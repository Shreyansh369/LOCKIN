package expo.modules.focusenforcement

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.UserManager
import android.util.Log

/**
 * Hardline + Device Owner only. Every call is a no-op unless both hold, so the play variant
 * can never reach these APIs even though the class is compiled into it.
 *
 * - setPackagesSuspended: https://developer.android.com/reference/android/app/admin/DevicePolicyManager#setPackagesSuspended(android.content.ComponentName,%20java.lang.String[],%20boolean)
 * - setUninstallBlocked:  https://developer.android.com/reference/android/app/admin/DevicePolicyManager#setUninstallBlocked(android.content.ComponentName,%20java.lang.String,%20boolean)
 * - DISALLOW_SAFE_BOOT:   https://developer.android.com/reference/android/os/UserManager#DISALLOW_SAFE_BOOT
 *
 * Everything applied here is reverted by revertAll(), which Enforcer.endSession() calls for
 * expiry, user exit and emergency unlock.
 */
object DeviceOwnerControls {
  private const val TAG = "LockinDeviceOwner"

  fun admin(context: Context) = ComponentName(context, LockinDeviceAdminReceiver::class.java)

  private fun dpm(context: Context) =
    context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

  fun isDeviceOwner(context: Context): Boolean = dpm(context).isDeviceOwnerApp(context.packageName)

  fun isAdminActive(context: Context): Boolean = dpm(context).isAdminActive(admin(context))

  private fun enabled(context: Context) = Variant.isHardline(context) && isDeviceOwner(context)

  fun applySession(context: Context, state: SessionState) {
    if (!enabled(context)) return
    val dpm = dpm(context)
    val admin = admin(context)
    try {
      suspend(context, blockedLaunchables(context, state))
      dpm.setUninstallBlocked(admin, context.packageName, true)
      dpm.addUserRestriction(admin, UserManager.DISALLOW_SAFE_BOOT)
    } catch (e: SecurityException) {
      Log.e(TAG, "applySession", e)
    }
  }

  fun onBreakStart(context: Context, packages: Set<String>) {
    if (!enabled(context)) return
    val current = SessionStore.get(context).suspendedPackages()
    val release = if (packages.isEmpty()) current else current.intersect(packages)
    setSuspended(context, release, false)
    SessionStore.get(context).setSuspendedPackages(current - release)
  }

  fun onBreakEnd(context: Context, state: SessionState) {
    if (!enabled(context)) return
    suspend(context, blockedLaunchables(context, state.copy(breakUntil = 0, breakPackages = emptySet())))
  }

  /** Idempotent. Safe to call when nothing was applied. */
  fun revertAll(context: Context) {
    if (!Variant.isHardline(context) || !isDeviceOwner(context)) return
    val dpm = dpm(context)
    val admin = admin(context)
    val store = SessionStore.get(context)
    setSuspended(context, store.suspendedPackages(), false)
    store.setSuspendedPackages(emptySet())
    dpm.setUninstallBlocked(admin, context.packageName, false)
    dpm.clearUserRestriction(admin, UserManager.DISALLOW_SAFE_BOOT)
  }

  /**
   * Rollback path for Device Owner / device admin, outside sessions only. `adb shell dpm
   * remove-active-admin` refuses non-test admins, so the app must release itself:
   * clearDeviceOwnerApp (deprecated in API 26 but still the DO's own exit) and
   * removeActiveAdmin. https://developer.android.com/reference/android/app/admin/DevicePolicyManager#clearDeviceOwnerApp(java.lang.String)
   */
  @Suppress("DEPRECATION")
  fun releaseAll(context: Context) {
    if (!Variant.isHardline(context)) return
    check(SessionStore.get(context).load() == null) { "End the session first" }
    revertAll(context)
    val dpm = dpm(context)
    if (isDeviceOwner(context)) dpm.clearDeviceOwnerApp(context.packageName)
    if (isAdminActive(context)) dpm.removeActiveAdmin(admin(context))
  }

  private fun suspend(context: Context, pkgs: Set<String>) {
    val store = SessionStore.get(context)
    val failed = setSuspended(context, pkgs, true)
    store.setSuspendedPackages(store.suspendedPackages() + (pkgs - failed))
  }

  /** Returns the packages the system refused to change. */
  private fun setSuspended(context: Context, pkgs: Set<String>, suspended: Boolean): Set<String> {
    if (pkgs.isEmpty()) return emptySet()
    return try {
      dpm(context).setPackagesSuspended(admin(context), pkgs.toTypedArray(), suspended).toSet()
    } catch (e: SecurityException) {
      Log.e(TAG, "setPackagesSuspended($suspended)", e)
      pkgs
    }
  }

  private fun blockedLaunchables(context: Context, state: SessionState): Set<String> {
    val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return context.packageManager.queryIntentActivities(launcher, 0)
      .map { it.activityInfo.packageName }
      .filterTo(HashSet()) { !Policy.isAllowed(context, it, state) }
  }
}
