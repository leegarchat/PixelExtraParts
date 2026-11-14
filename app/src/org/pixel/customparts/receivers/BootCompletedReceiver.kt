package org.pixel.customparts.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.pixel.customparts.R
import org.pixel.customparts.activities.ThermalManager
import org.pixel.customparts.utils.AddonBinderReapply
import org.pixel.customparts.utils.AddonBootSync
import org.pixel.customparts.utils.AutoHbmController
import org.pixel.customparts.utils.AutoLockController
import org.pixel.customparts.utils.PixelPartsLogController
import org.pixel.customparts.utils.PixelPartsTileRefresher
import org.pixel.customparts.utils.SaturationController
import org.pixel.customparts.utils.ThermalProfileController

class BootCompletedReceiver : BroadcastReceiver() {
    companion object {
        /**
         * Publishes experimental XML flags to Settings.Global so the engine
         * (ApkModulesGate) and the framework weave patch can gate on them.
         * The XML default wins on every boot; tests override live via
         * `settings put global` (reset on next boot).
         */
        fun publishExperimentalFlags(context: Context) {
            try {
                val enabled = context.resources.getBoolean(R.bool.exp_apk_modules_manager)
                Settings.Global.putInt(
                    context.contentResolver,
                    "pixel_addon_apk_modules_enabled",
                    if (enabled) 1 else 0
                )
            } catch (e: Exception) {
                Log.e("PixelParts", "Failed to publish experimental flags", e)
            }
            try {
                // RemotePrefs root for Xposed module shared preferences
                // (cross-process; see RemotePrefs). system_app can create it;
                // the rprefs type transition + appdomain rules cover the rest.
                java.io.File("/data/pixelparts/rprefs").mkdirs()
            } catch (e: Exception) {
                Log.w("PixelParts", "Failed to create rprefs dir", e)
            }
            try {
                // Manager (system_app) pre-creates each enabled module's
                // prefs file — injected (untrusted) processes may not
                // create files outside their sandbox (core neverallow).
                if (org.pixel.customparts.core.ApkModulesGate.isEnabled(context)) {
                    for (mod in org.pixel.customparts.ui.addons.scanInstalledModules(context)) {
                        if (mod.enabled) {
                            org.pixel.customparts.manager.lsplant.RemotePrefs
                                .ensureModulePrefsFile(mod.packageName)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("PixelParts", "Failed to ensure module prefs files", e)
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d("PixelParts", "Boot/wake event received. Action: $action")

        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (action == Intent.ACTION_USER_PRESENT || action == Intent.ACTION_SCREEN_ON) {
                    PixelPartsTileRefresher.requestAll(context)
                    return@launch
                }
                ThermalManager.onBoot(context)
                publishExperimentalFlags(context)
                ThermalProfileController.syncService(context)
                SaturationController.applyEffectiveSaturation(context)
                AutoHbmController.syncService(context)
                AutoLockController.syncService(context)
                PixelPartsLogController.syncService(context)
                AddonBootSync.sync(context)
                AddonBinderReapply.reapply(context)
                PixelPartsTileRefresher.requestAll(context)
            } catch (e: Exception) {
                Log.e("PixelParts", "Error during boot initialization", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
