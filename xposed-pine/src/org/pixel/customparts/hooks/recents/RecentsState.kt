package org.pixel.customparts.hooks.recents

import android.animation.ValueAnimator
import android.graphics.RenderEffect
import android.view.View
import android.view.ViewGroup
import de.robv.android.xposed.XposedHelpers
import kotlin.math.abs

object RecentsState {
    const val CLASS_RECENTS_VIEW = "com.android.quickstep.views.RecentsView"
    const val CLASS_TASK_VIEW = "com.android.quickstep.views.TaskView"
    const val CLASS_BASE_CONTAINER = "com.android.quickstep.BaseContainerInterface"
    const val TAG_CAROUSEL_PREDRAW_INSTALLED = 0x7F0B0002
    const val TAG_CAROUSEL_PREDRAW_LISTENER = 0x7F0B0003
    const val TAG_PENDING_END_TARGET = 0x7F0B0004
    const val TAG_SYS_TRANS_X = 0x7F0B0013
    const val TAG_SYS_ALPHA = 0x7F0B0012
    const val TAG_SYS_STABLE_ALPHA = 0x7F0B0014
    const val TAG_SYS_NON_GRID_SCALE = 0x7F0B0015
    const val TAG_OFFSET_TRANS = 0x7F0B0021
    const val TAG_OFFSET_ALPHA = 0x7F0B0022
    const val TAG_OFFSET_SCALE = 0x7F0B0023

    
    const val TAG_SCALE_PREDRAW = 0x7F0B0030
    const val TAG_TINT_PREDRAW = 0x7F0B0031
    const val TAG_SPACING_PREDRAW = 0x7F0B0032
    const val TAG_BLUR_PREDRAW = 0x7F0B0033
    const val TAG_ALPHA_PREDRAW = 0x7F0B0034
    const val TAG_ICON_PREDRAW = 0x7F0B0035

    
    const val TAG_BLUR_EFFECT = 0x7F0B0040
    const val TAG_TINT_EFFECT = 0x7F0B0041
    const val TAG_ICON_ORIG_DELEGATE = 0x7F0B0042

    val applyingCarousel = object : ThreadLocal<Boolean>() {
        override fun initialValue() = false
    }
    
    @Volatile var carouselIntensity = 0f
    @Volatile var carouselAnimator: ValueAnimator? = null
    @Volatile var isInRecentsMode = false
    @Volatile var isGestureInProgress = false
    @Volatile var enteringRecentsUntil = 0L
    @Volatile var isAnimatingExit = false

    



    fun composeAndApplyRenderEffects(child: View) {
        if (android.os.Build.VERSION.SDK_INT < 31) return
        val blur = child.getTag(TAG_BLUR_EFFECT) as? RenderEffect
        val tint = child.getTag(TAG_TINT_EFFECT) as? RenderEffect
        val result = when {
            blur != null && tint != null -> RenderEffect.createChainEffect(tint, blur)
            blur != null -> blur
            tint != null -> tint
            else -> null
        }
        try {
            XposedHelpers.callMethod(child, "setRenderEffect", result)
        } catch (_: Throwable) {}
    }

    fun getTaskViewFactor(child: View, parent: ViewGroup): Float {
        val scrollX = parent.scrollX
        val viewWidth = parent.width
        if (viewWidth <= 0) return 0f
        val screenCenter = scrollX + viewWidth / 2f
        val influenceDistance = viewWidth * 0.55f
        val lastAppliedOffset = (child.getTag(TAG_OFFSET_TRANS) as? Float) ?: 0f
        val currentTrans = child.translationX
        val taggedSys = (child.getTag(TAG_SYS_TRANS_X) as? Float)
        val systemTrans = taggedSys ?: (currentTrans - lastAppliedOffset)
        if (taggedSys == null) {
            child.setTag(TAG_SYS_TRANS_X, systemTrans)
        }

        val childCenter = ((child.left + child.right) / 2f) + systemTrans
        val distanceFromCenter = abs(screenCenter - childCenter)
        return if (influenceDistance <= 0f) 0f
        else if (distanceFromCenter > influenceDistance) 1.0f
        else (distanceFromCenter / influenceDistance)
    }
}