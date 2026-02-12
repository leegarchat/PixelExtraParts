package org.pixel.customparts.hooks.recents

import android.content.Context
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RenderEffect
import android.os.Build
import android.view.ViewGroup
import android.view.ViewTreeObserver
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import org.pixel.customparts.core.BaseHook

class RecentsEdgeCardsTintHook : BaseHook() {
    override val hookId = "RecentsEdgeCardsTintHook"
    override val priority = 49

    companion object {
        private const val KEY_MODIFY_ENABLE = "launcher_recents_modify_enable"
        private const val KEY_COLOR = "launcher_recents_carousel_tint_color"
        private const val KEY_INTENSITY = "launcher_recents_carousel_tint_intensity"
    }

    override fun isEnabled(context: Context?): Boolean {
        if (Build.VERSION.SDK_INT < 31) return false
        val intensity = getIntSetting(context, KEY_INTENSITY, 0)
        return isSettingEnabled(context, KEY_MODIFY_ENABLE) && intensity > 0
    }

    override fun onInit(classLoader: ClassLoader) {
        try {
            val recentsViewClass = XposedHelpers.findClass(RecentsState.CLASS_RECENTS_VIEW, classLoader)
            
            XposedHelpers.findAndHookMethod(recentsViewClass, "onAttachedToWindow", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val view = param.thisObject as ViewGroup
                    
                    val oldListener = view.getTag(RecentsState.TAG_TINT_PREDRAW)
                            as? ViewTreeObserver.OnPreDrawListener
                    if (oldListener != null) {
                        view.viewTreeObserver.removeOnPreDrawListener(oldListener)
                    }
                    val listener = ViewTreeObserver.OnPreDrawListener {
                        applyEffect(view)
                        true
                    }
                    view.viewTreeObserver.addOnPreDrawListener(listener)
                    view.setTag(RecentsState.TAG_TINT_PREDRAW, listener)
                }
            })
        } catch (e: Throwable) {
            logError("Failed to init RecentsEdgeCardsTintHook", e)
        }
    }

    private fun applyEffect(recentsView: ViewGroup) {
        if (!isSettingEnabled(recentsView.context, KEY_MODIFY_ENABLE)) return
        if (!RecentsState.isInRecentsMode) return
        val intensity = RecentsState.carouselIntensity
        if (intensity <= 0f) return

        val color = getIntSetting(recentsView.context, KEY_COLOR, Color.BLACK)
        val maxIntensity = getIntSetting(recentsView.context, KEY_INTENSITY, 0) / 100f

        for (i in 0 until recentsView.childCount) {
            val child = recentsView.getChildAt(i)
            if (child.javaClass.simpleName.contains("ClearAll")) continue
            val factor = RecentsState.getTaskViewFactor(child, recentsView)
            val alpha = (255 * maxIntensity * intensity * factor).toInt().coerceIn(0, 255)
            
            if (alpha > 5) {
                val colorWithAlpha = (color and 0x00FFFFFF) or (alpha shl 24)
                val filter = PorterDuffColorFilter(colorWithAlpha, PorterDuff.Mode.SRC_ATOP)
                val effect = RenderEffect.createColorFilterEffect(filter)
                
                
                child.setTag(RecentsState.TAG_TINT_EFFECT, effect)
                RecentsState.composeAndApplyRenderEffects(child)
            } else {
                val hadEffect = child.getTag(RecentsState.TAG_TINT_EFFECT) != null
                if (hadEffect) {
                    child.setTag(RecentsState.TAG_TINT_EFFECT, null)
                    RecentsState.composeAndApplyRenderEffects(child)
                }
            }
        }
    }
}