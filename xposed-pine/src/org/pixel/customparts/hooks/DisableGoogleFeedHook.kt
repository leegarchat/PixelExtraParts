package org.pixel.customparts.hooks

import android.app.Activity
import android.content.Context
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import org.pixel.customparts.core.BaseHook







class DisableGoogleFeedHook : BaseHook() {

    override val hookId = "DisableGoogleFeedHook"
    override val priority = 60

    companion object {
        private const val CLASS_LAUNCHER = "com.android.launcher3.Launcher"
        private const val KEY_DISABLE_FEED = "launcher_disable_google_feed"
    }

    override fun isEnabled(context: Context?): Boolean {
        return isSettingEnabled(context, KEY_DISABLE_FEED)
    }

    override fun onInit(classLoader: ClassLoader) {
        try {
            
            XposedHelpers.findAndHookMethod(
                CLASS_LAUNCHER,
                classLoader,
                "onResume",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val activity = param.thisObject as Activity
                        
                        if (isEnabled(activity)) {
                            disableFeedOverlay(activity)
                        }
                    }
                }
            )

            
            try {
                val overlayProxyClass = XposedHelpers.findClass(
                    "com.android.systemui.plugins.shared.LauncherOverlayManager\$LauncherOverlayTouchProxy",
                    classLoader
                )
                XposedHelpers.findAndHookMethod(
                    CLASS_LAUNCHER,
                    classLoader,
                    "setLauncherOverlay",
                    overlayProxyClass,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            val context = android.app.ActivityThread.currentApplication()
                            if (isSettingEnabled(context, KEY_DISABLE_FEED)) {
                                param.args[0] = null
                            }
                        }
                    }
                )
                log("Launcher.setLauncherOverlay hook installed")
            } catch (e: Throwable) {
                logError("Failed to hook setLauncherOverlay", e)
            }

            log("DisableGoogleFeed hook installed successfully")
        } catch (e: Throwable) {
            logError("Failed to initialize DisableGoogleFeed hook", e)
        }
    }

    private fun disableFeedOverlay(launcherActivity: Any) {
        try {
            XposedHelpers.callMethod(
                launcherActivity,
                "setLauncherOverlay",
                *arrayOfNulls<Any>(1)
            )
            log("Launcher overlay disabled via setLauncherOverlay(null)")
        } catch (e: Throwable) {
            logError("Failed to disable overlay", e)
        }
    }
}