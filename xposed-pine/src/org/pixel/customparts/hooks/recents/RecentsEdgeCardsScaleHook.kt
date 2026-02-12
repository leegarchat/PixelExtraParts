package org.pixel.customparts.hooks.recents

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import org.pixel.customparts.core.BaseHook

class RecentsEdgeCardsScaleHook : BaseHook() {
    override val hookId = "RecentsEdgeCardsScaleHook"
    override val priority = 50

    companion object {
        private const val KEY_MODIFY_ENABLE = "launcher_recents_modify_enable"
        private const val KEY_SCALE = "launcher_recents_carousel_scale"
        private const val DEFAULT_SCALE = 1.0f
    }

    override fun isEnabled(context: Context?): Boolean {
        if (!isSettingEnabled(context, KEY_MODIFY_ENABLE)) return false
        val scale = getFloatSetting(context, KEY_SCALE, DEFAULT_SCALE)
        return scale < 0.99f || scale > 1.01f
    }

    override fun onInit(classLoader: ClassLoader) {
        val recentsViewClass = XposedHelpers.findClass(RecentsState.CLASS_RECENTS_VIEW, classLoader)
        XposedHelpers.findAndHookMethod(recentsViewClass, "onAttachedToWindow", object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val view = param.thisObject as ViewGroup
                
                val oldListener = view.getTag(RecentsState.TAG_SCALE_PREDRAW)
                        as? ViewTreeObserver.OnPreDrawListener
                if (oldListener != null) {
                    view.viewTreeObserver.removeOnPreDrawListener(oldListener)
                }
                val listener = object : ViewTreeObserver.OnPreDrawListener {
                    override fun onPreDraw(): Boolean {
                        applyEffect(view)
                        return true
                    }
                }
                view.viewTreeObserver.addOnPreDrawListener(listener)
                view.setTag(RecentsState.TAG_SCALE_PREDRAW, listener)
            }
        })

        val taskViewClass = XposedHelpers.findClass(RecentsState.CLASS_TASK_VIEW, classLoader)
        XposedHelpers.findAndHookMethod(taskViewClass, "setNonGridScale", Float::class.javaPrimitiveType, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val view = param.thisObject as? View ?: return
                if (RecentsState.applyingCarousel.get() == true) return
                val baseScale = param.args[0] as? Float ?: return
                view.setTag(RecentsState.TAG_SYS_NON_GRID_SCALE, baseScale)
                if (!RecentsState.isInRecentsMode && !RecentsState.isAnimatingExit) return
                if (!isSettingEnabled(view.context, KEY_MODIFY_ENABLE)) return

                val scaleFactor = (view.getTag(RecentsState.TAG_OFFSET_SCALE) as? Float) ?: 1.0f
                if (scaleFactor < 0.99f || scaleFactor > 1.01f) {
                    param.args[0] = baseScale * scaleFactor
                }
            }
        })
    }

    private fun applyEffect(recentsView: ViewGroup) {
        if (!isSettingEnabled(recentsView.context, KEY_MODIFY_ENABLE)) return
        if (!RecentsState.isInRecentsMode && !RecentsState.isAnimatingExit) return
        val intensity = RecentsState.carouselIntensity
        if (intensity <= 0f) return
        val minScale = getFloatSetting(recentsView.context, KEY_SCALE, DEFAULT_SCALE)
        val effectiveScale = 1.0f + ((minScale - 1.0f) * intensity)
        val scaleRange = 1.0f - effectiveScale

        RecentsState.applyingCarousel.set(true)
        try {
            for (i in 0 until recentsView.childCount) {
                val child = recentsView.getChildAt(i)
                if (child.javaClass.simpleName.contains("ClearAll")) continue
                val factor = RecentsState.getTaskViewFactor(child, recentsView)
                val scaleFactor = 1.0f - (scaleRange * factor)
                child.setTag(RecentsState.TAG_OFFSET_SCALE, scaleFactor)

                val baseScale = (child.getTag(RecentsState.TAG_SYS_NON_GRID_SCALE) as? Float) ?: 1.0f
                try {
                    XposedHelpers.callMethod(child, "setNonGridScale", baseScale * scaleFactor)
                } catch (_: Throwable) {
                }
            }
        } finally {
            RecentsState.applyingCarousel.set(false)
        }
    }
}