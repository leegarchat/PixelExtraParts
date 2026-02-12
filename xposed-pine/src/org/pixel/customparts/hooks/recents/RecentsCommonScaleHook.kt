package org.pixel.customparts.hooks.recents

import android.content.Context
import android.graphics.Rect
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import org.pixel.customparts.core.BaseHook

class RecentsCommonScaleHook : BaseHook() {
    override val hookId = "RecentsCommonScaleHook"
    override val priority = 40

    companion object {
        private const val KEY_MODIFY_ENABLE = "launcher_recents_modify_enable"
        private const val KEY_SCALE_ENABLE = "launcher_recents_scale_enable"
        private const val KEY_SCALE_PERCENT = "launcher_recents_scale_percent"
    }

    override fun isEnabled(context: Context?): Boolean {
        return isSettingEnabled(context, KEY_MODIFY_ENABLE) &&
            isSettingEnabled(context, KEY_SCALE_ENABLE) &&
            getIntSetting(context, KEY_SCALE_PERCENT, 100) != 100
    }

    override fun onInit(classLoader: ClassLoader) {
        try {
            val baseContainerClass = XposedHelpers.findClass(RecentsState.CLASS_BASE_CONTAINER, classLoader)
            val methods = baseContainerClass.declaredMethods.filter { it.name == "calculateTaskSize" }
            for (method in methods) {
                if (method.parameterTypes.any { it == Rect::class.java }) {
                    XposedBridge.hookMethod(method, object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            val context = param.args[0] as Context
                            if (!isSettingEnabled(context, KEY_MODIFY_ENABLE)) return
                            if (!isSettingEnabled(context, KEY_SCALE_ENABLE)) return
                            val scalePercent = getIntSetting(context, KEY_SCALE_PERCENT, 100)
                            if (scalePercent == 100) return
                            val rect = param.args.find { it is Rect } as? Rect ?: return
                            applyScale(rect, scalePercent)
                        }
                    })
                }
            }
        } catch (e: Throwable) {
            logError("Failed to hook calculateTaskSize", e)
        }
    }

    private fun applyScale(rect: Rect, scalePercent: Int) {
        if (rect.isEmpty) return
        val scale = scalePercent / 100f
        val width = rect.width()
        val height = rect.height()
        val cx = rect.centerX()
        val cy = rect.centerY()
        val newW = (width * scale).toInt()
        val newH = (height * scale).toInt()
        rect.set(cx - newW / 2, cy - newH / 2, cx + newW / 2, cy + newH / 2)
    }
}