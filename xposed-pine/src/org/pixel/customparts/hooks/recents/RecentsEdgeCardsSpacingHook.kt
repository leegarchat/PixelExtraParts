package org.pixel.customparts.hooks.recents

import android.content.Context
import android.view.ViewGroup
import android.view.ViewTreeObserver
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import org.pixel.customparts.core.BaseHook
import kotlin.math.abs

class RecentsEdgeCardsSpacingHook : BaseHook() {
    override val hookId = "RecentsEdgeCardsSpacingHook"
    override val priority = 55

    companion object {
        private const val KEY_MODIFY_ENABLE = "launcher_recents_modify_enable"
        private const val KEY_SPACING = "launcher_recents_carousel_spacing"
    }

    override fun isEnabled(context: Context?): Boolean {
        return isSettingEnabled(context, KEY_MODIFY_ENABLE) &&
            getIntSetting(context, KEY_SPACING, 0) != 0
    }

    override fun onInit(classLoader: ClassLoader) {
        val recentsViewClass = XposedHelpers.findClass(RecentsState.CLASS_RECENTS_VIEW, classLoader)
        
        XposedHelpers.findAndHookMethod(recentsViewClass, "onAttachedToWindow", object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val view = param.thisObject as ViewGroup
                
                val oldListener = view.getTag(RecentsState.TAG_SPACING_PREDRAW)
                        as? ViewTreeObserver.OnPreDrawListener
                if (oldListener != null) {
                    view.viewTreeObserver.removeOnPreDrawListener(oldListener)
                }
                val listener = ViewTreeObserver.OnPreDrawListener {
                    applyEffect(view)
                    true
                }
                view.viewTreeObserver.addOnPreDrawListener(listener)
                view.setTag(RecentsState.TAG_SPACING_PREDRAW, listener)
            }
        })
    }

    private fun applyEffect(recentsView: ViewGroup) {
        if (!isSettingEnabled(recentsView.context, KEY_MODIFY_ENABLE)) return
        if (!RecentsState.isInRecentsMode) return
        val intensity = RecentsState.carouselIntensity
        if (intensity <= 0f) return
        val spacingOffsetSetting = getIntSetting(recentsView.context, KEY_SPACING, 0)
        val spacingOffset = (spacingOffsetSetting * intensity).toInt()
        val screenCenter = recentsView.scrollX + recentsView.width / 2f

        for (i in 0 until recentsView.childCount) {
            val child = recentsView.getChildAt(i)
            if (child.javaClass.simpleName.contains("ClearAll")) continue
            val factor = RecentsState.getTaskViewFactor(child, recentsView)
            val childCenter = (child.left + child.right) / 2f + (child.getTag(RecentsState.TAG_SYS_TRANS_X) as? Float ?: 0f)
            
            var extraTransX = 0f
            if (abs(screenCenter - childCenter) > 50) { 
                val direction = if (childCenter > screenCenter) 1 else -1
                val spacingFactor = factor.coerceAtMost(1.0f)
                extraTransX = (direction * spacingOffset * spacingFactor).toFloat()
            }

            RecentsState.applyingCarousel.set(true)
            try {
                val sysTrans = child.getTag(RecentsState.TAG_SYS_TRANS_X) as? Float ?: child.translationX
                child.translationX = sysTrans + extraTransX
                child.setTag(RecentsState.TAG_OFFSET_TRANS, extraTransX)
            } finally {
                RecentsState.applyingCarousel.set(false)
            }
        }
    }
}