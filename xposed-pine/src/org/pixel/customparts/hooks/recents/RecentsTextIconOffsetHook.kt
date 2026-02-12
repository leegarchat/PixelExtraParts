package org.pixel.customparts.hooks.recents

import android.content.Context
import android.view.TouchDelegate
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import org.pixel.customparts.core.BaseHook

class RecentsTextIconOffsetHook : BaseHook() {
    override val hookId = "RecentsTextIconOffsetHook"
    override val priority = 50

    companion object {
        private const val KEY_MODIFY_ENABLE = "launcher_recents_modify_enable"
        private const val KEY_OFFSET_X = "launcher_recents_carousel_icon_offset_x"
        private const val KEY_OFFSET_Y = "launcher_recents_carousel_icon_offset_y"
    }

    override fun isEnabled(context: Context?): Boolean {
        val x = getIntSetting(context, KEY_OFFSET_X, 0)
        val y = getIntSetting(context, KEY_OFFSET_Y, 0)
        return isSettingEnabled(context, KEY_MODIFY_ENABLE) && (x != 0 || y != 0)
    }

    override fun onInit(classLoader: ClassLoader) {
        val recentsViewClass = XposedHelpers.findClass(RecentsState.CLASS_RECENTS_VIEW, classLoader)
        
        XposedHelpers.findAndHookMethod(recentsViewClass, "onAttachedToWindow", object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val view = param.thisObject as ViewGroup
                val oldListener = view.getTag(RecentsState.TAG_ICON_PREDRAW) as? ViewTreeObserver.OnPreDrawListener
                if (oldListener != null) {
                    view.viewTreeObserver.removeOnPreDrawListener(oldListener)
                }
                val listener = ViewTreeObserver.OnPreDrawListener {
                    applyEffect(view)
                    true
                }
                view.viewTreeObserver.addOnPreDrawListener(listener)
                view.setTag(RecentsState.TAG_ICON_PREDRAW, listener)
            }
        })
    }

    private fun applyEffect(recentsView: ViewGroup) {
        if (!isSettingEnabled(recentsView.context, KEY_MODIFY_ENABLE)) return
        if (!RecentsState.isInRecentsMode) return
        val intensity = RecentsState.carouselIntensity

        val offsetX = getIntSetting(recentsView.context, KEY_OFFSET_X, 0) * intensity
        val offsetY = getIntSetting(recentsView.context, KEY_OFFSET_Y, 0) * intensity

        for (i in 0 until recentsView.childCount) {
            val child = recentsView.getChildAt(i)
            if (child !is ViewGroup) continue
            val className = child.javaClass.simpleName

            
            if (className.contains("ClearAll")) continue

            
            if (className.contains("Grouped")) continue

            applyToTaskView(child, offsetX, offsetY)
        }
    }

    private fun applyToTaskView(taskView: ViewGroup, x: Float, y: Float) {
        val icons = mutableListOf<View>()
        scanForIcons(taskView, icons)

        for (icon in icons) {
            icon.translationX = x
            icon.translationY = y
        }

        
        
        if (x != 0f || y != 0f) {
            taskView.clipChildren = false
            taskView.clipToPadding = false
            (taskView.parent as? ViewGroup)?.let {
                it.clipChildren = false
            }

            
            if (taskView.touchDelegate != null && taskView.getTag(RecentsState.TAG_ICON_ORIG_DELEGATE) == null) {
                taskView.setTag(RecentsState.TAG_ICON_ORIG_DELEGATE, taskView.touchDelegate)
                taskView.touchDelegate = null
            }
        } else {
            
            val origDelegate = taskView.getTag(RecentsState.TAG_ICON_ORIG_DELEGATE) as? TouchDelegate
            if (origDelegate != null) {
                taskView.touchDelegate = origDelegate
                taskView.setTag(RecentsState.TAG_ICON_ORIG_DELEGATE, null)
            }
        }
    }

    private fun scanForIcons(root: ViewGroup, out: MutableList<View>) {
        for (i in 0 until root.childCount) {
            val v = root.getChildAt(i) ?: continue
            val name = v.javaClass.simpleName
            val fullName = v.javaClass.name

            var isMatch = false
            if (name.contains("Icon") || name.contains("Chip") ||
                fullName.contains("IconView") || fullName.contains("iconView") ||
                fullName.contains("AppChip")) {
                out.add(v)
                isMatch = true
            }

            if (!isMatch && v is ViewGroup) {
                scanForIcons(v, out)
            }
        }
    }
}