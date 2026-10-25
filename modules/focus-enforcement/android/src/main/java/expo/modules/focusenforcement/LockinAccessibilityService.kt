package expo.modules.focusenforcement

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

/**
 * Receives TYPE_WINDOW_STATE_CHANGED only (see res/xml/lockin_accessibility_service.xml).
 *
 * play: reads event.packageName and nothing else. Never calls performGlobalAction, never
 * clicks, never reads window content. Google Play AccessibilityService policy:
 * https://support.google.com/googleplay/android-developer/answer/10964491
 *
 * hardline: additionally runs SettingsGuard, which reads class name / window title and may
 * press HOME (performGlobalAction) before covering a protected Settings screen.
 *
 * Starting BlockerActivity from here relies on the system binding accessibility services with
 * Context.BIND_ALLOW_BACKGROUND_ACTIVITY_STARTS (AOSP AccessibilityServiceConnection), i.e. the
 * documented "bound by a service that has been granted permission to start background
 * activities" exception: https://developer.android.com/guide/components/activities/background-starts
 */
class LockinAccessibilityService : AccessibilityService() {
  override fun onServiceConnected() {
    super.onServiceConnected()
    connected = true
    // Process may have been recreated mid-session; make sure the service and notification exist.
    if (SessionStore.get(this).load() != null) EnforcementService.start(this)
  }

  override fun onAccessibilityEvent(event: AccessibilityEvent) {
    if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
    val pkg = event.packageName?.toString() ?: return
    if (pkg == packageName) return
    if (Variant.isHardline(this) && SettingsGuard.check(this, event)) return
    Enforcer.onForeground(this, pkg, "accessibility")
  }

  override fun onInterrupt() {}

  override fun onUnbind(intent: Intent?): Boolean {
    connected = false
    return super.onUnbind(intent)
  }

  override fun onDestroy() {
    connected = false
    super.onDestroy()
  }

  companion object {
    @Volatile var connected = false
  }
}
