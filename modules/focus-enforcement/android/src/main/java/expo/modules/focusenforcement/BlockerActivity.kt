package expo.modules.focusenforcement

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import org.json.JSONArray

/**
 * Full-screen native cover shown over a blocked app. Native so it appears within ~300 ms even
 * when the JS runtime is not running.
 *
 * Background-activity-launch: launches come from the foreground service or the accessibility
 * service, i.e. from the background. Valid exemptions we rely on
 * (https://developer.android.com/guide/components/activities/background-starts):
 *   - "The app has the SYSTEM_ALERT_WINDOW permission granted by the user" — required for
 *     both variants (onboarding asks for "Display over other apps"); covers UsageEvents polling.
 *   - accessibility services are bound with BIND_ALLOW_BACKGROUND_ACTIVITY_STARTS, covering
 *     launches from LockinAccessibilityService.
 * No PendingIntent is involved, so the Android 14/15 PendingIntent BAL opt-ins don't apply.
 *
 * Back never returns to the blocked app: both back paths send the user to the launcher.
 */
class BlockerActivity : Activity() {
  private val handler = Handler(Looper.getMainLooper())
  private lateinit var timeLeft: TextView
  private var backCallback: Any? = null

  private val ticker = object : Runnable {
    override fun run() {
      val state = SessionStore.get(this@BlockerActivity).load()
      if (state == null) {
        // Session ended (expiry / emergency / exit): nothing to cover any more.
        finish()
        return
      }
      timeLeft.text = formatLeft(state.endsAt - System.currentTimeMillis())
      handler.postDelayed(this, 1000)
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val state = SessionStore.get(this).load()
    if (state == null) {
      finish()
      return
    }
    setContentView(buildUi(state, intent.getStringExtra(EXTRA_REASON) ?: "app"))
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      val cb = OnBackInvokedCallback { goHome() }
      onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, cb)
      backCallback = cb
    }
  }

  override fun onResume() {
    super.onResume()
    handler.post(ticker)
  }

  override fun onPause() {
    handler.removeCallbacks(ticker)
    super.onPause()
  }

  override fun onDestroy() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      (backCallback as? OnBackInvokedCallback)?.let { onBackInvokedDispatcher.unregisterOnBackInvokedCallback(it) }
    }
    super.onDestroy()
  }

  // The app opts out of predictive back (app.config.ts predictiveBackGestureEnabled=false ->
  // android:enableOnBackInvokedCallback="false"), so onBackPressed is still delivered on
  // Android 16 with targetSdk 36. The OnBackInvokedCallback above covers the opted-in case.
  // https://developer.android.com/about/versions/16/behavior-changes-16#predictive-back
  @Deprecated("Deprecated in Android API 33")
  override fun onBackPressed() {
    goHome()
  }

  private fun goHome() {
    startActivity(Enforcer.homeIntent())
    finish()
  }

  private fun openExitRitual() {
    val uri = Uri.parse("${Variant.scheme(this)}://exit")
    val i = Intent(Intent.ACTION_VIEW, uri).setPackage(packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    startActivity(i)
    finish()
  }

  private fun buildUi(state: SessionState, reason: String): View {
    val pad = dp(24)
    // Edge-to-edge is enforced when targeting API 35+; the outer frame absorbs system-bar insets.
    // https://developer.android.com/about/versions/15/behavior-changes-15#edge-to-edge
    val frame = FrameLayout(this).apply {
      setBackgroundColor(Color.BLACK)
      fitsSystemWindows = true
    }
    val root = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      gravity = Gravity.CENTER_VERTICAL
      setPadding(pad, pad, pad, pad)
    }
    frame.addView(root, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    root.addView(text(if (reason == "protected") "PROTECTED DURING SESSION" else "NOT NOW", 14f, GREY, bold = true))
    root.addView(space(12))
    root.addView(text("You committed to:", 16f, GREY))
    root.addView(text(state.task, 26f, Color.WHITE, bold = true))
    root.addView(space(16))
    timeLeft = text(formatLeft(state.endsAt - System.currentTimeMillis()), 20f, Color.WHITE)
    root.addView(timeLeft)
    root.addView(space(32))
    pickQuote()?.let { root.addView(text("“$it”", 20f, Color.WHITE)) }
    root.addView(space(48))
    root.addView(button("Back to work", primary = true) { goHome() })
    root.addView(space(12))
    root.addView(button("Exit ritual", primary = false) { openExitRitual() })
    return frame
  }

  private fun pickQuote(): String? = try {
    val raw = assets.open("quotes.json").bufferedReader().use { it.readText() }
    val arr = JSONArray(raw)
    val allowProfanity = SessionStore.get(this).profanityEnabled
    val pool = (0 until arr.length()).map { arr.getJSONObject(it) }
      .filter { allowProfanity || !it.optBoolean("profanity", false) }
      .map { it.getString("text") }
    pool.randomOrNull()
  } catch (e: Exception) {
    null
  }

  private fun text(s: String, sp: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
    text = s
    setTextColor(color)
    setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
    if (bold) typeface = Typeface.DEFAULT_BOLD
  }

  private fun button(label: String, primary: Boolean, onClick: () -> Unit) = Button(this).apply {
    text = label
    isAllCaps = false
    setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
    setTextColor(if (primary) Color.BLACK else Color.WHITE)
    setBackgroundColor(if (primary) Color.WHITE else DARK)
    minHeight = dp(56)
    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    setOnClickListener { onClick() }
  }

  private fun space(h: Int) = View(this).apply {
    layoutParams = LinearLayout.LayoutParams(1, dp(h))
  }

  private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

  private fun formatLeft(ms: Long): String {
    val s = (ms.coerceAtLeast(0) / 1000)
    return "%d:%02d:%02d left".format(s / 3600, (s % 3600) / 60, s % 60)
  }

  companion object {
    private const val EXTRA_PKG = "lockin.blockedPkg"
    private const val EXTRA_REASON = "lockin.reason"
    private const val GREY = 0xFFAAAAAA.toInt()
    private const val DARK = 0xFF222222.toInt()

    fun launch(context: Context, pkg: String, reason: String) {
      val i = Intent(context, BlockerActivity::class.java)
        .putExtra(EXTRA_PKG, pkg)
        .putExtra(EXTRA_REASON, reason)
        .addFlags(
          Intent.FLAG_ACTIVITY_NEW_TASK or
            Intent.FLAG_ACTIVITY_CLEAR_TASK or
            Intent.FLAG_ACTIVITY_NO_ANIMATION or
            Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
        )
      context.startActivity(i)
    }
  }
}
