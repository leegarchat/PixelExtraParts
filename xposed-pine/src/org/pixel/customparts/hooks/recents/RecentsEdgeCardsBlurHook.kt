package org.pixel.customparts.hooks.recents

import android.content.Context
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import org.pixel.customparts.core.BaseHook

class RecentsEdgeCardsBlurHook : BaseHook() {
    override val hookId = "RecentsEdgeCardsBlurHook"
    override val priority = 50

    companion object {
        private const val KEY_MODIFY_ENABLE = "launcher_recents_modify_enable"
        private const val KEY_BLUR = "launcher_recents_carousel_blur_radius"
        private const val KEY_OVERFLOW = "launcher_recents_carousel_blur_overflow"
        private const val BLUR_STEP = 2f
    }

    override fun isEnabled(context: Context?): Boolean {
        if (Build.VERSION.SDK_INT < 31) return false
        return isSettingEnabled(context, KEY_MODIFY_ENABLE) && getIntSetting(context, KEY_BLUR, 0) > 0
    }

    override fun onInit(classLoader: ClassLoader) {
        try {
            val recentsViewClass = XposedHelpers.findClass(RecentsState.CLASS_RECENTS_VIEW, classLoader)
            XposedHelpers.findAndHookMethod(recentsViewClass, "onAttachedToWindow", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val view = param.thisObject as ViewGroup
                    
                    val oldListener = view.getTag(RecentsState.TAG_BLUR_PREDRAW)
                            as? ViewTreeObserver.OnPreDrawListener
                    if (oldListener != null) {
                        view.viewTreeObserver.removeOnPreDrawListener(oldListener)
                    }
                    val listener = ViewTreeObserver.OnPreDrawListener {
                        applyEffect(view)
                        true
                    }
                    view.viewTreeObserver.addOnPreDrawListener(listener)
                    view.setTag(RecentsState.TAG_BLUR_PREDRAW, listener)
                }
            })
        } catch (e: Throwable) {
            logError("Init failed", e)
        }
    }

    private fun applyEffect(recentsView: ViewGroup) {
        if (!isSettingEnabled(recentsView.context, KEY_MODIFY_ENABLE)) return
        if (!RecentsState.isInRecentsMode) return
        val intensity = RecentsState.carouselIntensity
        if (intensity <= 0f) return

        val maxBlur = getIntSetting(recentsView.context, KEY_BLUR, 0).toFloat()
        val overflow = isSettingEnabled(recentsView.context, KEY_OVERFLOW, false)

        for (i in 0 until recentsView.childCount) {
            val child = recentsView.getChildAt(i)
            if (child.javaClass.simpleName.contains("ClearAll")) continue
            val factor = RecentsState.getTaskViewFactor(child, recentsView)
            val rawRadius = maxBlur * intensity * factor
            val radius = if (rawRadius < 2f) 0f else (Math.floor(rawRadius / BLUR_STEP.toDouble()) * BLUR_STEP).toFloat()
            val thumbnailView = if (!overflow) findThumbnailView(child) else null
            if (radius > 0) {
                val tileMode = if (overflow) Shader.TileMode.DECAL else Shader.TileMode.CLAMP
                val effect = RenderEffect.createBlurEffect(radius, radius, tileMode)
                if (overflow) {
                    
                    child.setTag(RecentsState.TAG_BLUR_EFFECT, effect)
                    RecentsState.composeAndApplyRenderEffects(child)
                    child.clipToOutline = false
                    if (child is ViewGroup) {
                        child.clipChildren = false
                        child.clipToPadding = false
                    }
                    if (thumbnailView != null) setRenderEffectSafely(thumbnailView, null)
                } else {
                    
                    child.setTag(RecentsState.TAG_BLUR_EFFECT, null)
                    RecentsState.composeAndApplyRenderEffects(child)
                    child.clipToOutline = true
                    if (thumbnailView != null) {
                        setRenderEffectSafely(thumbnailView, effect)
                        thumbnailView.clipToOutline = true
                    } else {
                        
                        child.setTag(RecentsState.TAG_BLUR_EFFECT, effect)
                        RecentsState.composeAndApplyRenderEffects(child)
                    }
                }
            } else {
                
                val hadBlur = child.getTag(RecentsState.TAG_BLUR_EFFECT) != null
                if (hadBlur) {
                    child.setTag(RecentsState.TAG_BLUR_EFFECT, null)
                    RecentsState.composeAndApplyRenderEffects(child)
                }
                if (!overflow) {
                    val thumb = findThumbnailView(child)
                    if (thumb != null) setRenderEffectSafely(thumb, null)
                }
            }
        }
    }

    private fun setRenderEffectSafely(view: View, effect: RenderEffect?) {
        try {
            XposedHelpers.callMethod(view, "setRenderEffect", effect)
        } catch (e: Throwable) { }
    }

    private fun findThumbnailView(root: View): View? {
        if (root !is ViewGroup) return null
        val queue = ArrayDeque<View>()
        queue.add(root)
        var depth = 0
        while (!queue.isEmpty() && depth < 3) {
            val size = queue.size
            for (i in 0 until size) {
                val curr = queue.removeFirst()
                val name = curr.javaClass.simpleName
                if (name.contains("TaskThumbnailView") || name.contains("Snapshot")) {
                    return curr
                }
                if (curr is ViewGroup) {
                    for (k in 0 until curr.childCount) {
                        queue.add(curr.getChildAt(k))
                    }
                }
            }
            depth++
        }
        return null
    }
}