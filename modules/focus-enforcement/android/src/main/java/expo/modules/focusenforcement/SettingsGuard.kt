package expo.modules.focusenforcement

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

/**
 * HARDLINE ONLY. Must never run in the play variant (LockinAccessibilityService gates it on
 * Variant.isHardline, and check() re-checks).
 *
 * During a session, covers the Settings screens that would let the user switch LOCKIN off:
 * our App info page (and with it Force stop / Uninstall), Accessibility settings, Device admin
 * settings, and the uninstall confirmation dialog.
 *
 * Detection heuristics (documented in docs/DEVICE_TEST.md, fragile by nature):
 *  1. Package: one of Policy.settingsPackages() (resolved from ACTION_SETTINGS,
 *     ACTION_APPLICATION_DETAILS_SETTINGS for our package, ACTION_ACCESSIBILITY_SETTINGS)
 *     or Policy.uninstallerPackages().
 *  2. Window class name contains a protected marker (AOSP class names such as
 *     ...InstalledAppDetails, ...AccessibilitySettings, ...DeviceAdminSettings). Many OEM
 *     builds route everything through a generic SubSettings activity, so this alone misses.
 *  3. Window title (event.text) contains a protected keyword. English only.
 *  4. Our app label appears anywhere in the active window (App info header, our row in the
 *     Accessibility / Device admin lists, the uninstall dialog). Locale-independent, but it
 *     also covers harmless pages that list every app (e.g. the Apps list) during a session.
 */
object SettingsGuard {
  private val CLASS_MARKERS = listOf(
    "installedappdetails", "appinfo", "appdetails",
    "accessibility",
    "deviceadmin",
    "uninstall"
  )
  private val TITLE_KEYWORDS = listOf(
    "accessibility", "device admin", "admin apps", "force stop", "uninstall"
  )

  /** Returns true if the event was handled (screen covered). */
  fun check(service: AccessibilityService, event: AccessibilityEvent): Boolean {
    if (!Variant.isHardline(service)) return false
    if (SessionStore.get(service).load() == null) return false
    val pkg = event.packageName?.toString() ?: return false
    val inSettings = pkg in Policy.settingsPackages(service)
    val inUninstaller = pkg in Policy.uninstallerPackages(service)
    if (!inSettings && !inUninstaller) return false

    val cls = event.className?.toString()?.lowercase().orEmpty()
    val title = event.text.joinToString(" ").lowercase()
    val label = service.applicationInfo.loadLabel(service.packageManager).toString()

    val protected = (inSettings && CLASS_MARKERS.any { it in cls }) ||
      (inSettings && TITLE_KEYWORDS.any { it in title }) ||
      label.lowercase() in title ||
      windowMentions(service, label)

    if (!protected) return false
    service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
    Enforcer.block(service, pkg, "protected", "accessibility")
    return true
  }

  private fun windowMentions(service: AccessibilityService, label: String): Boolean {
    val root = service.rootInActiveWindow ?: return false
    return root.findAccessibilityNodeInfosByText(label).isNotEmpty()
  }
}
