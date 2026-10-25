package expo.modules.focusenforcement

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.PowerManager
import android.util.Log

/**
 * Foreground service that runs only while a session is active.
 *
 * Type specialUse with PROPERTY_SPECIAL_USE_FGS_SUBTYPE in the module manifest:
 * https://developer.android.com/develop/background-work/services/fgs/service-types#special-use
 *
 * Detection: polls UsageStatsManager events every 500 ms while the screen is interactive.
 * play: polling is primary (accessibility, if enabled, also reports).
 * hardline: accessibility is primary; polling runs only while the accessibility service is
 * not connected.
 */
class EnforcementService : Service() {
  private lateinit var thread: HandlerThread
  private lateinit var handler: Handler
  private var lastQueryEnd = 0L
  private var lastNotifiedMinute = -1L
  private var currentForeground: String? = null
  private var lastRecheck = 0L

  private val loop = object : Runnable {
    override fun run() {
      try {
        Enforcer.tick(this@EnforcementService)
        val state = SessionStore.get(this@EnforcementService).load()
        if (state == null) {
          stopSelf()
          return
        }
        updateNotification(state)
        if (shouldPoll()) poll()
      } catch (e: Exception) {
        Log.e(TAG, "tick failed", e)
      }
      handler.postDelayed(this, POLL_MS)
    }
  }

  override fun onCreate() {
    super.onCreate()
    thread = HandlerThread("lockin-enforcement").apply { start() }
    handler = Handler(thread.looper)
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    val state = SessionStore.get(this).load()
    if (state == null) {
      stopSelf()
      return START_NOT_STICKY
    }
    // startForeground must be called promptly after startForegroundService().
    val notification = buildNotification(state)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
      startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
    } else {
      startForeground(NOTIFICATION_ID, notification)
    }
    lastQueryEnd = System.currentTimeMillis() - 1000
    handler.removeCallbacks(loop)
    handler.post(loop)
    // Sticky: if the process is killed mid-session the system recreates the service.
    return START_STICKY
  }

  override fun onDestroy() {
    handler.removeCallbacksAndMessages(null)
    thread.quitSafely()
    super.onDestroy()
  }

  override fun onBind(intent: Intent?): IBinder? = null

  private fun shouldPoll(): Boolean {
    val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
    if (!pm.isInteractive) return false
    if (!Permissions.hasUsageAccess(this)) return false
    return !(Variant.isHardline(this) && LockinAccessibilityService.connected)
  }

  /**
   * Reports the most recent ACTIVITY_RESUMED since the last poll. With no new event, the last
   * known foreground app is re-checked every RECHECK_MS so a blocker launch the system
   * dropped (or a dismissed blocker) is retried while the blocked app stays in front.
   */
  private fun poll() {
    val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    val end = System.currentTimeMillis()
    val events = usm.queryEvents(lastQueryEnd, end) ?: return
    lastQueryEnd = end
    val e = UsageEvents.Event()
    var latest: String? = null
    while (events.hasNextEvent()) {
      events.getNextEvent(e)
      // ACTIVITY_RESUMED (API 29) has the same value as the older MOVE_TO_FOREGROUND.
      // https://developer.android.com/reference/android/app/usage/UsageEvents.Event#ACTIVITY_RESUMED
      if (e.eventType == UsageEvents.Event.ACTIVITY_RESUMED) latest = e.packageName
    }
    if (latest != null) currentForeground = latest
    val fg = currentForeground ?: return
    if (latest != null || end - lastRecheck >= RECHECK_MS) {
      lastRecheck = end
      Enforcer.onForeground(this, fg, "usage")
    }
  }

  private fun updateNotification(state: SessionState) {
    val minute = (state.endsAt - System.currentTimeMillis()) / 60_000
    if (minute == lastNotifiedMinute) return
    lastNotifiedMinute = minute
    val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    nm.notify(NOTIFICATION_ID, buildNotification(state))
  }

  private fun buildNotification(state: SessionState): Notification {
    val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      nm.createNotificationChannel(
        NotificationChannel(CHANNEL_ID, "Focus session", NotificationManager.IMPORTANCE_LOW)
      )
    }
    val now = System.currentTimeMillis()
    val minutesLeft = ((state.endsAt - now).coerceAtLeast(0) + 59_999) / 60_000
    val text = when {
      state.emergencyUnlockAt > now ->
        "Emergency unlock in ${((state.emergencyUnlockAt - now) + 59_999) / 60_000} min"
      state.isBreakActive(now) -> "Break: ${((state.breakUntil - now) + 59_999) / 60_000} min left"
      else -> "$minutesLeft min left"
    }
    val open = packageManager.getLaunchIntentForPackage(packageName)
    val content = open?.let {
      PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }
    @Suppress("DEPRECATION")
    val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      Notification.Builder(this, CHANNEL_ID)
    } else {
      Notification.Builder(this)
    }
    return builder
      .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
      .setContentTitle(state.task)
      .setContentText(text)
      .setOngoing(true)
      .setOnlyAlertOnce(true)
      .setShowWhen(false)
      .setContentIntent(content)
      .build()
  }

  companion object {
    private const val TAG = "LockinService"
    private const val CHANNEL_ID = "lockin_session"
    private const val NOTIFICATION_ID = 4711
    private const val POLL_MS = 500L
    private const val RECHECK_MS = 1500L

    fun start(context: Context) {
      val intent = Intent(context, EnforcementService::class.java)
      try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
          context.startForegroundService(intent)
        } else {
          context.startService(intent)
        }
      } catch (e: Exception) {
        // ForegroundServiceStartNotAllowedException (API 31+) when no background-start
        // exemption applies. Accessibility detection keeps working; the next app open,
        // boot, or accessibility reconnect retries.
        Log.w(TAG, "could not start service", e)
      }
    }

    fun stop(context: Context) {
      context.stopService(Intent(context, EnforcementService::class.java))
    }
  }
}
