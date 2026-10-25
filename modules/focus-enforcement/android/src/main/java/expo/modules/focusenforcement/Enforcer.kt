package expo.modules.focusenforcement

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log

/**
 * Session lifecycle and the "foreground app changed" entry point shared by both detectors.
 */
object Enforcer {
  private const val TAG = "LockinEnforcer"
  private const val DEBOUNCE_MS = 800L

  @Volatile private var lastBlockedPkg: String? = null
  @Volatile private var lastBlockedAt = 0L

  /**
   * Called by UsageEvents polling and by the AccessibilityService. Returns true if a block
   * was triggered. Cheap enough to call on every event.
   */
  fun onForeground(context: Context, pkg: String, source: String): Boolean {
    val store = SessionStore.get(context)
    val state = store.load() ?: return false
    val now = System.currentTimeMillis()
    if (now >= state.endsAt) {
      endSession(context, "expired", null)
      return false
    }
    if (Policy.isAllowed(context, pkg, state, now)) return false
    block(context, pkg, "app", source)
    return true
  }

  /** reason: "app" (not on allowlist) or "protected" (hardline Settings guard). */
  fun block(context: Context, pkg: String, reason: String, source: String) {
    val t = SystemClock.elapsedRealtime()
    if (pkg == lastBlockedPkg && t - lastBlockedAt < DEBOUNCE_MS) return
    lastBlockedPkg = pkg
    lastBlockedAt = t
    BlockerActivity.launch(context, pkg, reason)
    val store = SessionStore.get(context)
    store.incrementBlocks()
    val ts = System.currentTimeMillis()
    store.enqueueEvent("block", mapOf("pkg" to pkg, "reason" to reason, "source" to source))
    EventBus.blocked(pkg, ts)
  }

  fun startSession(context: Context, state: SessionState) {
    val store = SessionStore.get(context)
    store.start(state)
    store.enqueueEvent(
      "session_start",
      mapOf(
        "task" to state.task,
        "startedAt" to state.startedAt,
        "endsAt" to state.endsAt,
        "mode" to state.mode,
        "allowlist" to state.allowlist.joinToString(",")
      )
    )
    DeviceOwnerControls.applySession(context, state)
    EnforcementService.start(context)
  }

  /** Ends the session and reverts every restriction. type: expired | user | emergency. */
  fun endSession(context: Context, type: String, reason: String?) {
    val store = SessionStore.get(context)
    if (store.load() == null) return
    // Revert first: if anything below throws, the device must not stay locked down.
    try {
      DeviceOwnerControls.revertAll(context)
    } catch (e: Exception) {
      Log.e(TAG, "revertAll failed", e)
    }
    store.enqueueEvent("session_end", mapOf("endType" to type, "reason" to reason))
    store.clear()
    EnforcementService.stop(context)
    EventBus.sessionEnded(type, System.currentTimeMillis())
  }

  fun requestBreak(context: Context, minutes: Int, packages: Set<String>) {
    val store = SessionStore.get(context)
    val state = store.load() ?: throw IllegalStateException("No active session")
    val now = System.currentTimeMillis()
    require(state.mode != "strict") { "Breaks are disabled in strict mode" }
    require(state.breaksUsed < SessionState.MAX_BREAKS) { "No breaks left this session" }
    require(!state.isBreakActive(now)) { "A break is already running" }
    require(minutes in 1..SessionState.MAX_BREAK_MINUTES) { "Break must be 1-${SessionState.MAX_BREAK_MINUTES} minutes" }
    val until = (now + minutes * 60_000L).coerceAtMost(state.endsAt)
    store.startBreak(until, packages)
    store.enqueueEvent("break_start", mapOf("minutes" to minutes, "packages" to packages.joinToString(",")))
    DeviceOwnerControls.onBreakStart(context, packages)
  }

  /** Called from the service tick. */
  fun endBreakIfDue(context: Context, now: Long) {
    val store = SessionStore.get(context)
    val state = store.load() ?: return
    if (state.breakUntil in 1..now) {
      store.clearBreak()
      store.enqueueEvent("break_end")
      DeviceOwnerControls.onBreakEnd(context, state)
    }
  }

  fun startEmergencyUnlock(context: Context): Long {
    val store = SessionStore.get(context)
    val state = store.load() ?: throw IllegalStateException("No active session")
    if (state.emergencyUnlockAt > 0) return state.emergencyUnlockAt
    val at = System.currentTimeMillis() + SessionState.EMERGENCY_UNLOCK_MS
    store.setEmergencyUnlockAt(at)
    store.enqueueEvent("emergency_start", mapOf("unlockAt" to at))
    return at
  }

  /** Service tick: expiry, emergency completion, break relock. */
  fun tick(context: Context) {
    val state = SessionStore.get(context).load() ?: return
    val now = System.currentTimeMillis()
    when {
      now >= state.endsAt -> endSession(context, "expired", null)
      state.emergencyUnlockAt in 1..now -> endSession(context, "emergency", null)
      else -> endBreakIfDue(context, now)
    }
  }

  fun homeIntent(): Intent =
    Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
