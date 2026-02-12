package org.pixel.customparts.hooks.recents

import android.content.Context
import android.view.ViewGroup
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import org.pixel.customparts.core.BaseHook

class RecentsDisableLiveTileHook : BaseHook() {
    override val hookId = "RecentsDisableLiveTileHook"
    override val priority = 40

    companion object {
        private const val KEY_MODIFY_ENABLE = "launcher_recents_modify_enable"
        private const val KEY_DISABLE = "launcher_recents_disable_livetile"
    }

    override fun isEnabled(context: Context?): Boolean {
        return isSettingEnabled(context, KEY_MODIFY_ENABLE) &&
            isSettingEnabled(context, KEY_DISABLE, false)
    }

    override fun onInit(classLoader: ClassLoader) {
        val recentsViewClass = XposedHelpers.findClass(RecentsState.CLASS_RECENTS_VIEW, classLoader)

        XposedHelpers.findAndHookMethod(recentsViewClass, "onGestureAnimationEnd", object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val view = param.thisObject as ViewGroup
                if (!isSettingEnabled(view.context, KEY_MODIFY_ENABLE)) return
                if (!isSettingEnabled(view.context, KEY_DISABLE, false)) return

                try {
                    val cleanupRunnable = Runnable {
                        try {
                            XposedHelpers.callMethod(view, "finishRecentsAnimation", false, false, null)
                            XposedHelpers.callMethod(view, "setEnableDrawingLiveTile", false)
                        } catch (_: Exception) {}
                    }

                    val method = XposedHelpers.findMethodBestMatch(recentsViewClass, "switchToScreenshot", Runnable::class.java)
                    if (method != null) {
                        method.invoke(view, cleanupRunnable)
                    } else {
                        XposedHelpers.callMethod(view, "switchToScreenshot", cleanupRunnable)
                    }
                } catch (e: Exception) {
                    logError("Failed to disable Live Tile", e)
                }
            }
        })
    }
}