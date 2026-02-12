package org.pixel.customparts.hooks

import android.app.Activity
import android.content.Context
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import org.pixel.customparts.core.BaseHook

class HomepageSizeHook : BaseHook() {

    override val hookId = "HomepageSizeHook"
    override val priority = 70

    companion object {
        private const val KEY_DOCK_ENABLE = "launcher_dock_enable"
        private const val KEY_PADDING_HOMEPAGE = "launcher_padding_homepage"
        private const val SETTINGS_DEFAULT = -45
        private const val LAUNCHER_ORIGINAL_BOTTOM_DP = 165
    }

    private var lastAppliedHash: Int = 0
    override fun isEnabled(context: Context?): Boolean = true

    override fun onInit(classLoader: ClassLoader) {
        try {
            XposedHelpers.findAndHookMethod(
                "com.android.launcher3.Launcher",
                classLoader,
                "onResume",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val activity = param.thisObject as Activity
                        if (getIntSetting(activity, KEY_DOCK_ENABLE, 0) != 1) return
                        val rootView = activity.findViewById<View>(android.R.id.content)
                        rootView?.post {
                            applyHomepagePadding(activity)
                        } ?: applyHomepagePadding(activity)
                    }
                }
            )
            log("HomepageSizeHook lifecycle listener installed")
        } catch (t: Throwable) {
            logError("Failed to hook Launcher.onResume", t)
        }
    }

    private fun applyHomepagePadding(activity: Activity) {
        try {
            if (getIntSetting(activity, KEY_DOCK_ENABLE, 0) != 1) return
            val paddingSetting = getIntSetting(activity, KEY_PADDING_HOMEPAGE, SETTINGS_DEFAULT)
            if (lastAppliedHash == paddingSetting) return
            val deviceProfile = try {
                XposedHelpers.getObjectField(activity, "mDeviceProfile")
            } catch (e: Throwable) { null }

            val workspace = try {
                XposedHelpers.getObjectField(activity, "mWorkspace") as? View
            } catch (e: Throwable) { null }

            val hotseat = try {
                XposedHelpers.getObjectField(activity, "mHotseat") as? ViewGroup
            } catch (e: Throwable) { null }

            if (deviceProfile == null || workspace == null) {
                log("mDeviceProfile or mWorkspace not found")
                return
            }
            var paddingObj: Rect? = null
            try {
                paddingObj = XposedHelpers.getObjectField(deviceProfile, "workspacePadding") as? Rect
            } catch (e: Throwable) { }
            if (paddingObj == null) {
                try {
                    val workspaceProfile = XposedHelpers.getObjectField(deviceProfile, "mWorkspaceProfile")
                    if (workspaceProfile != null) {
                        paddingObj = (try { XposedHelpers.getObjectField(workspaceProfile, "workspacePadding") } catch(e:Throwable){null}) as? Rect
                            ?: (try { XposedHelpers.getObjectField(workspaceProfile, "mWorkspacePadding") } catch(e:Throwable){null}) as? Rect
                            ?: (try { XposedHelpers.getObjectField(workspaceProfile, "padding") } catch(e:Throwable){null}) as? Rect
                    }
                } catch (e: Throwable) { }
            }
            if (paddingObj != null) {
                val desiredBottomPx = if (paddingSetting == SETTINGS_DEFAULT) {
                    if (lastAppliedHash == 0) paddingObj.bottom else toPx(activity, LAUNCHER_ORIGINAL_BOTTOM_DP)
                } else {
                    toPx(activity, paddingSetting + 20)
                }

                if (paddingObj.bottom != desiredBottomPx) {
                    log("Updating workspacePadding.bottom: ${paddingObj.bottom} -> $desiredBottomPx")
                    paddingObj.bottom = desiredBottomPx
                    triggerNativeUpdate(workspace, deviceProfile)
                    if (hotseat != null) {
                        triggerNativeUpdate(hotseat, deviceProfile)
                    }
                }
                lastAppliedHash = paddingSetting
            } else {
                log("workspacePadding Rect not found in DeviceProfile")
            }

        } catch (e: Exception) {
            logError("Failed to apply homepage padding", e)
        }
    }

    private fun triggerNativeUpdate(view: View, deviceProfile: Any) {
        try {
            val method = try {
                XposedHelpers.findMethodExact(view.javaClass, "onDeviceProfileChanged", deviceProfile.javaClass)
            } catch (e: Throwable) {
                view.javaClass.methods.find { it.name == "onDeviceProfileChanged" && it.parameterCount == 1 }
            }
            
            if (method != null) {
                method.isAccessible = true
                method.invoke(view, deviceProfile)
            }
        } catch (e: Throwable) {
            log("triggerNativeUpdate failed for ${view.javaClass.simpleName}: ${e.message}")
        }
    }

    private fun toPx(context: Context, dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }
}