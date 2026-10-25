package expo.modules.focusenforcement

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.telecom.TelecomManager

/**
 * The single decision point: is this foreground package allowed right now?
 * Detection (UsageEvents polling, AccessibilityService) only ever calls isAllowed().
 */
object Policy {
  // Packages that are never blocked, in addition to the dynamically resolved ones below.
  // "android" hosts the system resolver/chooser and some system dialogs; the permission
  // controllers host runtime-permission prompts; com.android.phone hosts EmergencyDialer;
  // com.android.server.telecom hosts the in-call routing UI.
  private val STATIC_ALWAYS_ALLOWED = setOf(
    "android",
    "com.android.systemui",
    "com.android.phone",
    "com.android.server.telecom",
    "com.android.emergency",
    "com.android.permissioncontroller",
    "com.google.android.permissioncontroller"
  )

  private const val REFRESH_MS = 60_000L
  @Volatile private var cache: Set<String> = emptySet()
  @Volatile private var cacheAt = 0L
  @Volatile private var settingsCache: Set<String> = emptySet()

  fun isAllowed(context: Context, pkg: String, state: SessionState, now: Long = System.currentTimeMillis()): Boolean {
    if (pkg in alwaysAllowed(context)) return true
    if (pkg in state.allowlist) return true
    if (state.isBreakActive(now) && (state.breakPackages.isEmpty() || pkg in state.breakPackages)) return true
    return false
  }

  /**
   * Own package, default + system dialer, emergency dialer, system UI, current launcher,
   * current IME, Android Settings. Settings is allowed in both variants; hardline additionally
   * covers specific protected Settings screens via SettingsGuard.
   */
  fun alwaysAllowed(context: Context): Set<String> {
    val now = System.currentTimeMillis()
    if (now - cacheAt < REFRESH_MS && cache.isNotEmpty()) return cache
    val out = HashSet<String>(STATIC_ALWAYS_ALLOWED)
    out += context.packageName
    // https://developer.android.com/reference/android/telecom/TelecomManager#getDefaultDialerPackage()
    val telecom = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
    telecom?.defaultDialerPackage?.let { out += it }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) telecom?.systemDialerPackage?.let { out += it }
    resolvePackage(context, Intent(Intent.ACTION_DIAL))?.let { out += it }
    homePackage(context)?.let { out += it }
    imePackage(context)?.let { out += it }
    out += settingsPackages(context)
    cache = out
    cacheAt = now
    return out
  }

  fun homePackage(context: Context): String? =
    resolvePackage(context, Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))

  /** Settings.Secure.DEFAULT_INPUT_METHOD is "pkg/.Service". */
  private fun imePackage(context: Context): String? =
    Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
      ?.substringBefore('/')?.takeIf { it.isNotEmpty() }

  /**
   * Packages that host Settings, our App-info page, Accessibility settings and the uninstall
   * confirmation. Resolved from intents instead of hard-coding OEM names, so e.g. MIUI's
   * security center is picked up if it handles App info on that device. Visibility for these
   * intents is declared in <queries> by plugins/withLockin.js.
   */
  fun settingsPackages(context: Context): Set<String> {
    if (settingsCache.isNotEmpty()) return settingsCache
    val pkgUri = Uri.parse("package:${context.packageName}")
    val out = HashSet<String>()
    out += "com.android.settings"
    listOf(
      Intent(Settings.ACTION_SETTINGS),
      Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkgUri),
      Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
    ).forEach { i -> resolvePackage(context, i)?.let { out += it } }
    settingsCache = out
    return out
  }

  fun uninstallerPackages(context: Context): Set<String> {
    val out = HashSet<String>()
    @Suppress("DEPRECATION")
    resolvePackage(context, Intent(Intent.ACTION_DELETE, Uri.parse("package:${context.packageName}")))?.let { out += it }
    out += "com.android.packageinstaller"
    out += "com.google.android.packageinstaller"
    return out
  }

  private fun resolvePackage(context: Context, intent: Intent): String? {
    val info = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
    return info?.activityInfo?.packageName
  }
}
