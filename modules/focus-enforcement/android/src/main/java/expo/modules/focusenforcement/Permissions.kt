package expo.modules.focusenforcement

import android.app.Activity
import android.app.AppOpsManager
import android.app.NotificationManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings

/**
 * Permission status + the Settings screens that grant each one. Nothing here grants anything
 * by itself; every special permission is a user toggle in Settings.
 */
object Permissions {
  // Stores whose installs are not subject to Android 13+ "restricted settings".
  private val STORE_INSTALLERS = setOf("com.android.vending")

  fun hasUsageAccess(context: Context): Boolean {
    val ops = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
    } else {
      @Suppress("DEPRECATION")
      ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
    }
    return mode == AppOpsManager.MODE_ALLOWED
  }

  fun hasOverlay(context: Context) = Settings.canDrawOverlays(context)

  fun notificationsEnabled(context: Context) =
    (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).areNotificationsEnabled()

  fun accessibilityEnabled(context: Context): Boolean {
    val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
      ?: return false
    val me = ComponentName(context, LockinAccessibilityService::class.java)
    return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
  }

  fun ignoringBatteryOptimizations(context: Context) =
    (context.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(context.packageName)

  fun installer(context: Context): String? = try {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName
    } else {
      @Suppress("DEPRECATION")
      context.packageManager.getInstallerPackageName(context.packageName)
    }
  } catch (e: PackageManager.NameNotFoundException) {
    null
  }

  /**
   * Android 13+ blocks accessibility services of apps installed outside an app store until the
   * user taps ⋮ → "Allow restricted settings" in App info. There is no public API to read that
   * flag, so this is a heuristic: API 33+, not installed by Play, service not yet enabled.
   * The UI shows the steps and re-checks on resume.
   */
  fun restrictedSettingsLikely(context: Context): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
      installer(context) !in STORE_INSTALLERS &&
      !accessibilityEnabled(context)

  fun status(context: Context): Map<String, Any?> = mapOf(
    "variant" to Variant.get(context),
    "sdkInt" to Build.VERSION.SDK_INT,
    "manufacturer" to Build.MANUFACTURER,
    "installer" to installer(context),
    "usageAccess" to hasUsageAccess(context),
    "overlay" to hasOverlay(context),
    "notifications" to notificationsEnabled(context),
    "accessibility" to accessibilityEnabled(context),
    "accessibilityConnected" to LockinAccessibilityService.connected,
    "restrictedSettingsLikely" to restrictedSettingsLikely(context),
    "batteryUnrestricted" to ignoringBatteryOptimizations(context),
    "deviceAdmin" to (Variant.isHardline(context) && DeviceOwnerControls.isAdminActive(context)),
    "deviceOwner" to (Variant.isHardline(context) && DeviceOwnerControls.isDeviceOwner(context))
  )

  /** Opens the Settings screen for [kind]. Returns false if the kind is unknown or not allowed for this variant. */
  fun request(activity: Activity?, context: Context, kind: String): Boolean {
    val pkgUri = Uri.parse("package:${context.packageName}")
    val hardline = Variant.isHardline(context)
    val intent = when (kind) {
      "usageAccess" -> Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
      "overlay" -> Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, pkgUri)
      "notifications" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
      } else {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkgUri)
      }
      "accessibility" -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
      // Restricted-settings flow and OEM autostart steps both start at our App info page.
      "appInfo" -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkgUri)
      // https://developer.android.com/training/monitoring-device-state/doze-standby#support_for_other_use_cases
      "battery" -> if (hardline) {
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkgUri)
      } else {
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
      }
      "deviceAdmin" -> if (hardline) {
        Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
          .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, DeviceOwnerControls.admin(context))
          .putExtra(
            DevicePolicyManager.EXTRA_ADD_EXPLANATION,
            "Prevents one-tap uninstall of LOCKIN HL. Deactivate it in Device admin settings outside a session."
          )
      } else null
      else -> null
    } ?: return false
    return try {
      if (activity != null) {
        activity.startActivity(intent)
      } else {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
      }
      true
    } catch (e: Exception) {
      // Some OEM builds lack a screen (e.g. usage access); fall back to App info.
      context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkgUri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
      true
    }
  }
}
