package expo.modules.focusenforcement

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Resumes enforcement after reboot or app update if a session is still running.
 * BOOT_COMPLETED is an exemption for starting a foreground service from the background, and
 * specialUse is not among the types Android 15 forbids from BOOT_COMPLETED receivers.
 * https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start#background-start-restriction-exemptions
 * https://developer.android.com/about/versions/15/behavior-changes-15#fgs-boot-completed
 */
class BootReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    when (intent.action) {
      Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
        val state = SessionStore.get(context).load() ?: return
        if (System.currentTimeMillis() >= state.endsAt) {
          Enforcer.endSession(context, "expired", null)
        } else {
          EnforcementService.start(context)
        }
      }
    }
  }
}
