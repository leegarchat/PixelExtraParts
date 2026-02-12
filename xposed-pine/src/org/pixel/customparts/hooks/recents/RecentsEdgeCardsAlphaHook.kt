package org.pixel.customparts.hooks.recents

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import org.pixel.customparts.core.BaseHook

class RecentsEdgeCardsAlphaHook : BaseHook() {
    override val hookId = "RecentsEdgeCardsAlphaHook"
    override val priority = 50

    companion object {
        private const val KEY_MODIFY_ENABLE = "launcher_recents_modify_enable"
        private const val KEY_ALPHA = "launcher_recents_carousel_alpha"
        private const val DEFAULT_ALPHA = 1.0f
    }

    override fun isEnabled(context: Context?): Boolean {
        if (!isSettingEnabled(context, KEY_MODIFY_ENABLE)) return false
        val alpha = getFloatSetting(context, KEY_ALPHA, DEFAULT_ALPHA)
        return alpha < 0.99f
    }

    override fun onInit(classLoader: ClassLoader) {
        try {
            val recentsViewClass = XposedHelpers.findClass(RecentsState.CLASS_RECENTS_VIEW, classLoader)
            XposedHelpers.findAndHookMethod(recentsViewClass, "onAttachedToWindow", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val view = param.thisObject as ViewGroup
                    val oldListener = view.getTag(RecentsState.TAG_ALPHA_PREDRAW) as? ViewTreeObserver.OnPreDrawListener
                    if (oldListener != null) {
                        view.viewTreeObserver.removeOnPreDrawListener(oldListener)
                    }
                    val listener = ViewTreeObserver.OnPreDrawListener {
                        applyEffect(view)
                        true
                    }
                    view.viewTreeObserver.addOnPreDrawListener(listener)
                    view.setTag(RecentsState.TAG_ALPHA_PREDRAW, listener)
                }
            })

            val taskViewClass = XposedHelpers.findClass(RecentsState.CLASS_TASK_VIEW, classLoader)
            XposedHelpers.findAndHookMethod(taskViewClass, "setStableAlpha", Float::class.javaPrimitiveType, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val view = param.thisObject as? View ?: return
                    if (RecentsState.applyingCarousel.get() == true) return
                    val baseAlpha = param.args[0] as? Float ?: return
                    view.setTag(RecentsState.TAG_SYS_STABLE_ALPHA, baseAlpha)
                    if (!RecentsState.isInRecentsMode) return
                    if (!isSettingEnabled(view.context, KEY_MODIFY_ENABLE)) return

                    val carouselAlpha = (view.getTag(RecentsState.TAG_OFFSET_ALPHA) as? Float) ?: 1.0f
                    if (carouselAlpha < 0.99f) {
                        param.args[0] = baseAlpha * carouselAlpha
                    }
                }
            })
        } catch (e: Throwable) {
            logError("Failed to init RecentsEdgeCardsAlphaHook", e)
        }
    }

    private fun applyEffect(recentsView: ViewGroup) {
        if (!isSettingEnabled(recentsView.context, KEY_MODIFY_ENABLE)) return
        if (!RecentsState.isInRecentsMode) return
        val intensity = RecentsState.carouselIntensity
        if (intensity <= 0f) return

        val minAlpha = getFloatSetting(recentsView.context, KEY_ALPHA, DEFAULT_ALPHA)
        if (minAlpha >= 0.99f) return

        val effectiveMinAlpha = 1.0f - ((1.0f - minAlpha) * intensity)
        val alphaRange = 1.0f - effectiveMinAlpha

        RecentsState.applyingCarousel.set(true)
        try {
            for (i in 0 until recentsView.childCount) {
                val child = recentsView.getChildAt(i)
                if (child.javaClass.simpleName.contains("ClearAll")) continue
                val factor = RecentsState.getTaskViewFactor(child, recentsView)
                val alphaFactor = (1.0f - (alphaRange * factor)).coerceIn(0f, 1f)
                child.setTag(RecentsState.TAG_OFFSET_ALPHA, alphaFactor)

                val baseAlpha = (child.getTag(RecentsState.TAG_SYS_STABLE_ALPHA) as? Float) ?: 1.0f
                try {
                    XposedHelpers.callMethod(child, "setStableAlpha", baseAlpha * alphaFactor)
                } catch (_: Throwable) {
                }
            }
        } finally {
            RecentsState.applyingCarousel.set(false)
        }
    }
}
