package expo.modules.focusenforcement

import android.content.Context
import android.content.pm.PackageManager

/**
 * Build variant injected by plugins/withLockin.js as
 * <meta-data android:name="lockin.variant" android:value="play|hardline"/>.
 * Unknown or missing values fall back to the stricter-policy "play" behaviour.
 */
object Variant {
  @Volatile private var cached: String? = null

  fun get(context: Context): String = cached ?: run {
    val value = try {
      @Suppress("DEPRECATION")
      context.packageManager
        .getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
        .metaData?.getString("lockin.variant")
    } catch (e: PackageManager.NameNotFoundException) {
      null
    }
    (if (value == "hardline") "hardline" else "play").also { cached = it }
  }

  fun isHardline(context: Context) = get(context) == "hardline"

  /** Must match `scheme` in app.config.ts. */
  fun scheme(context: Context) = if (isHardline(context)) "lockinhl" else "lockin"
}
