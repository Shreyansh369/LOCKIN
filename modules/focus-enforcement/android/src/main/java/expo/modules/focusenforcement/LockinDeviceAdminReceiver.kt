package expo.modules.focusenforcement

import android.app.admin.DeviceAdminReceiver

/**
 * Declared in the manifest only for the hardline variant (plugins/withLockin.js).
 * Being an active admin blocks one-tap uninstall; it is also the component passed to
 * `adb shell dpm set-device-owner` for the optional Device Owner mode.
 * https://developer.android.com/reference/android/app/admin/DeviceAdminReceiver
 */
class LockinDeviceAdminReceiver : DeviceAdminReceiver()
