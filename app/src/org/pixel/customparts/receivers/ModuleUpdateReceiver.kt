package org.pixel.customparts.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log
import java.util.zip.ZipFile

/**
 * Keeps the APK-module registry ([pixelparts_xposed_modules], a list of
 * module APK paths) fresh across reinstalls/updates: an update changes
 * the randomized /data/app/~~.../ sourceDir, so the stored path would
 * silently stop resolving. On PACKAGE_ADDED/REPLACED the stored entry
 * whose owning package matches is rewritten to the new sourceDir.
 * Whitelist/enabled keys are per package name and survive updates.
 */
class ModuleUpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_PACKAGE_ADDED &&
            action != Intent.ACTION_PACKAGE_REPLACED) return
        if (intent.getBooleanExtra(Intent.EXTRA_REPLACING, false) &&
            action == Intent.ACTION_PACKAGE_ADDED) return
        val pkg = intent.data?.schemeSpecificPart ?: return
        try {
            val pm = context.packageManager
            val newSource: String
            try {
                newSource = pm.getApplicationInfo(pkg, 0).sourceDir
            } catch (_: Throwable) { return }
            if (!looksLikeModule(newSource)) return
            val key = "pixelparts_xposed_modules"
            val raw = try {
                Settings.Global.getString(context.contentResolver, key)
            } catch (_: Throwable) { return } ?: return
            val paths = raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            if (paths.isEmpty()) return
            var changed = false
            val updated = paths.map { p ->
                val own = try {
                    pm.getPackageArchiveInfo(p, PackageManager.GET_META_DATA)?.packageName
                } catch (_: Throwable) { null }
                if (own == pkg && p != newSource) {
                    changed = true
                    newSource
                } else p
            }
            if (changed) {
                try {
                    Settings.Global.putString(
                        context.contentResolver, key, updated.joinToString(","))
                    Log.d("PixelParts", "Module registry refreshed for $pkg")
                } catch (t: Throwable) {
                    Log.w("PixelParts", "Failed to refresh module registry", t)
                }
            }
        } catch (t: Throwable) {
            Log.w("PixelParts", "ModuleUpdateReceiver failed for $pkg", t)
        }
    }

    private fun looksLikeModule(sourceDir: String): Boolean {
        return try {
            ZipFile(sourceDir).use { zip ->
                zip.getEntry("META-INF/xposed/java_init.list") != null ||
                    zip.getEntry("assets/xposed_init") != null ||
                    zip.getEntry("META-INF/yukihookapi_init") != null
            }
        } catch (_: Throwable) { false }
    }
}
