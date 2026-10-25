package expo.modules.focusenforcement

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import java.io.File
import java.io.FileOutputStream

/**
 * Launchable apps via a MAIN/LAUNCHER query, which works without QUERY_ALL_PACKAGES thanks to
 * the matching <queries> entry: https://developer.android.com/training/package-visibility/declaring
 * Icons are rendered once into cacheDir and returned as file:// URIs (cheaper than base64 over
 * the bridge for ~100+ apps).
 */
object AppList {
  private const val ICON_PX = 96

  fun list(context: Context): List<Map<String, Any?>> {
    val pm = context.packageManager
    val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val dir = File(context.cacheDir, "app-icons").apply { mkdirs() }
    val always = Policy.alwaysAllowed(context)
    return pm.queryIntentActivities(launcher, 0)
      .distinctBy { it.activityInfo.packageName }
      .filter { it.activityInfo.packageName != context.packageName }
      .map { ri ->
        val pkg = ri.activityInfo.packageName
        val versionStamp = try {
          pm.getPackageInfo(pkg, 0).lastUpdateTime
        } catch (e: Exception) {
          0L
        }
        val file = File(dir, "$pkg-$versionStamp.png")
        if (!file.exists()) {
          try {
            val d = ri.loadIcon(pm)
            val bmp = Bitmap.createBitmap(ICON_PX, ICON_PX, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            d.setBounds(0, 0, ICON_PX, ICON_PX)
            d.draw(c)
            FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bmp.recycle()
          } catch (e: Exception) {
            // Icon is optional.
          }
        }
        mapOf(
          "packageName" to pkg,
          "label" to ri.loadLabel(pm).toString(),
          "icon" to if (file.exists()) "file://${file.absolutePath}" else null,
          "alwaysAllowed" to (pkg in always)
        )
      }
      .sortedBy { (it["label"] as String).lowercase() }
  }
}
