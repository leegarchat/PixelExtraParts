package org.pixel.customparts.xposed.hooks

import android.app.Activity
import android.content.Context
import android.provider.Settings
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import java.lang.reflect.Method

object DisableGoogleFeedHook {

    private const val TAG = "DisableGoogleFeedHook"
    private const val KEY_DISABLE_FEED = "launcher_disable_google_feed_xposed"

    fun init(lpparam: LoadPackageParam) {
        if (lpparam.packageName != "com.google.android.apps.nexuslauncher") return

        try {
            val launcherClass = "com.android.launcher3.Launcher"
            XposedHelpers.findAndHookMethod(
                launcherClass,
                lpparam.classLoader,
                "onResume",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val activity = param.thisObject as Activity
                        if (isFeedDisabled(activity)) {
                            forceDisconnectOverlay(activity)
                        }
                    }
                }
            )

        } catch (t: Throwable) {
            XposedBridge.log("$TAG: Init error: $t")
        }
    }

    private fun forceDisconnectOverlay(launcherActivity: Any) {
        try {
            val workspace = XposedHelpers.getObjectField(launcherActivity, "mWorkspace") ?: return
            val workspaceClass = workspace.javaClass
            val method = findMethodByName(workspaceClass, "setLauncherOverlay")
            
            if (method != null) {
                method.isAccessible = true
                method.invoke(workspace, null)
            } else {
                XposedBridge.log("$TAG: Method setLauncherOverlay not found")
            }

        } catch (e: Throwable) {
            XposedBridge.log("$TAG: Failed to disconnect overlay: $e")
        }
    }

    private fun findMethodByName(clazz: Class<*>, methodName: String): Method? {
        var current: Class<*>? = clazz
        while (current != null) {
            try {
                val methods = current.declaredMethods
                val found = methods.find { it.name == methodName && it.parameterTypes.size == 1 }
                if (found != null) return found
            } catch (e: Exception) {
            }
            current = current.superclass
        }
        return null
    }

    private fun isFeedDisabled(context: Context): Boolean {
        return Settings.Secure.getInt(context.contentResolver, KEY_DISABLE_FEED, 0) == 1
    }
}