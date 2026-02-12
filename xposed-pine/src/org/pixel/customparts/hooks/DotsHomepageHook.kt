package org.pixel.customparts.hooks

import android.app.Activity
import android.content.Context
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import org.pixel.customparts.core.BaseHook

class DotsHomepageHook : BaseHook() {

    override val hookId = "DotsHomepageHook"
    override val priority = 70

    companion object {
        private const val KEY_DOCK_ENABLE = "launcher_dock_enable"
        private const val KEY_PADDING_DOTS = "launcher_padding_dots"
        private const val DEFAULT_PADDING_DOTS = 0
        private const val KEY_PADDING_DOTS_X = "launcher_padding_dots_x"
        private const val DEFAULT_PADDING_DOTS_X = 0
    }

    override fun isEnabled(context: Context?): Boolean {
        return isDotsOffsetEnabled(context)
    }

    override fun onInit(classLoader: ClassLoader) {
        try {
            val workspaceClass = XposedHelpers.findClass(
                "com.android.launcher3.Workspace",
                classLoader
            )
            XposedHelpers.findAndHookMethod(
                workspaceClass,
                "setInsets",
                Rect::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val workspace = param.thisObject as View
                        val context = workspace.context
                        if (!isDotsOffsetEnabled(context)) return
                        if (getIntSetting(context, KEY_DOCK_ENABLE, 0) != 1) return
                        val paddingDots = getIntSetting(context, KEY_PADDING_DOTS, DEFAULT_PADDING_DOTS)
                        val diffDp = paddingDots - DEFAULT_PADDING_DOTS
                        val diffPx = toPx(context, diffDp)
                        val paddingDotsX = getIntSetting(context, KEY_PADDING_DOTS_X, DEFAULT_PADDING_DOTS_X)
                        val diffPxX = toPx(context, paddingDotsX - DEFAULT_PADDING_DOTS_X)
                        val pageIndicator = try {
                            XposedHelpers.getObjectField(workspace, "mPageIndicator") as? View
                        } catch (e: Exception) { null } ?: return
                        val parent = pageIndicator.parent as? View ?: return
                        val params = parent.layoutParams as? ViewGroup.MarginLayoutParams ?: return
                        params.bottomMargin += diffPx
                        parent.layoutParams = params
                        if (parent.translationX != diffPxX.toFloat()) {
                            parent.translationX = diffPxX.toFloat()
                        }
                    }
                }
            )

            hookPageIndicatorDots(classLoader)

            log("DotsHomepageHook installed")
        } catch (e: Throwable) {
            logError("Failed to hook Workspace for Dots", e)
        }
    }

    private fun hookPageIndicatorDots(classLoader: ClassLoader) {
        try {
            val pageIndicatorDotsClass = XposedHelpers.findClass(
                "com.android.launcher3.pageindicators.PageIndicatorDots",
                classLoader
            )
            
            XposedHelpers.findAndHookMethod(
                pageIndicatorDotsClass,
                "onMeasure",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val view = param.thisObject as View
                        if (isDotsOffsetEnabled(view.context) && getIntSetting(view.context, KEY_DOCK_ENABLE, 0) == 1) {
                            if (view.visibility != View.VISIBLE) view.visibility = View.VISIBLE
                            if (view.alpha != 1f) view.alpha = 1f
                        }
                    }
                }
            )

            XposedHelpers.findAndHookMethod(
                pageIndicatorDotsClass,
                "onDraw",
                android.graphics.Canvas::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val view = param.thisObject as View
                        if (isDotsOffsetEnabled(view.context) && getIntSetting(view.context, KEY_DOCK_ENABLE, 0) == 1) {
                            if (view.alpha != 1f) view.alpha = 1f
                        }
                    }
                }
            )
        } catch (e: Throwable) {
            logError("Failed to hook PageIndicatorDots", e)
        }
    }

    override fun onActivityResumed(activity: Activity) {
        val rootView = activity.findViewById<View>(android.R.id.content)
        rootView?.post {
            forceUpdateDots(activity)
        }
    }

    private fun forceUpdateDots(activity: Activity) {
        try {
            if (!isEnabled(activity)) return
            if (getIntSetting(activity, KEY_DOCK_ENABLE, 0) != 1) return
            
            val resId = activity.resources.getIdentifier("page_indicator", "id", activity.packageName)
            if (resId != 0) {
                val pageIndicator = activity.findViewById<View>(resId)
                if (pageIndicator != null) {
                    pageIndicator.requestLayout()
                    if (pageIndicator.parent is View) {
                        (pageIndicator.parent as View).requestLayout()
                    }
                }
            }
        } catch (e: Exception) {
        }
    }
    
    private fun isDotsOffsetEnabled(context: Context?): Boolean {
        if (getIntSetting(context, KEY_DOCK_ENABLE, 0) != 1) return false
        val paddingDots = getIntSetting(context, KEY_PADDING_DOTS, DEFAULT_PADDING_DOTS)
        val paddingDotsX = getIntSetting(context, KEY_PADDING_DOTS_X, DEFAULT_PADDING_DOTS_X)
        return paddingDots != DEFAULT_PADDING_DOTS || paddingDotsX != DEFAULT_PADDING_DOTS_X
    }

    private fun toPx(context: Context, dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }
}