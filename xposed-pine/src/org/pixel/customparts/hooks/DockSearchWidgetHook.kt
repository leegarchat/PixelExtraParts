package org.pixel.customparts.hooks

import android.app.Activity
import android.content.Context
import android.graphics.RectF
import android.view.View
import android.view.ViewGroup
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import org.pixel.customparts.core.BaseHook

class DockSearchWidgetHook : BaseHook() {

    override val hookId = "DockSearchWidgetHook"
    override val priority = 70

    companion object {
        private const val KEY_ENABLE = "launcher_dock_enable"
        private const val KEY_HIDE_SEARCH = "launcher_hidden_search"
        private const val KEY_HIDE_DOCK = "launcher_hidden_dock"
        private const val KEY_PADDING_DOCK = "launcher_padding_dock"
        private const val KEY_PADDING_SEARCH = "launcher_padding_search"

        private const val TAG_VIEW_LISTENER = 0x7f010002
    }

    private var lastAppliedHash: Int = 0

    override fun isEnabled(context: Context?): Boolean {
        return isSettingEnabled(context, KEY_ENABLE)
    }

    override fun onInit(classLoader: ClassLoader) {
        try {
            
            XposedHelpers.findAndHookMethod(
                "com.android.launcher3.Launcher",
                classLoader,
                "onResume",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val activity = param.thisObject as Activity
                        if (!isEnabled(activity)) return
                        val rootView = activity.findViewById<View>(android.R.id.content)
                        rootView?.post {
                            applyDockSettings(activity)
                        } ?: applyDockSettings(activity)
                    }
                }
            )
            log("DockSearchWidgetHook lifecycle listener installed")
        } catch (t: Throwable) {
            logError("Failed to hook Launcher.onResume", t)
        }

        try {
            val launcherClass = XposedHelpers.findClass("com.android.launcher3.Launcher", classLoader)
            XposedHelpers.findAndHookMethod(
                "com.android.launcher3.views.FloatingIconView",
                classLoader,
                "getLocationBoundsForView",
                launcherClass,
                View::class.java,
                Boolean::class.javaPrimitiveType,
                RectF::class.java,
                android.graphics.Rect::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val launcher = param.args[0] as? Activity ?: return
                        if (!isEnabled(launcher)) return
                        val view = param.args[1] as? View ?: return
                        val isOpening = param.args[2] as? Boolean ?: return
                        if (isOpening) return
                        val paddingDock = getIntSetting(launcher, KEY_PADDING_DOCK, 0)
                        if (paddingDock == 0) return

                        val hotseat = findHotseat(launcher) ?: return
                        if (!isDescendantOf(view, hotseat)) return

                        val dockIconsView = findHotseatCellLayout(hotseat) ?: return
                        val offsetY = dockIconsView.translationY
                        if (offsetY == 0f) return

                        val rectF = param.args[3] as? RectF ?: return
                        rectF.offset(0f, offsetY)
                    }
                }
            )
            log("DockSearchWidgetHook icon bounds hook installed")
        } catch (t: Throwable) {
            logError("Failed to hook FloatingIconView.getLocationBoundsForView", t)
        }

        try {
            val stableViewInfoClass = XposedHelpers.findClass(
                "com.android.launcher3.util.StableViewInfo",
                classLoader
            )
            XposedHelpers.findAndHookMethod(
                "com.android.launcher3.Launcher",
                classLoader,
                "getFirstHomeElementForAppClose",
                stableViewInfoClass,
                String::class.java,
                android.os.UserHandle::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val activity = param.thisObject as? Activity ?: return
                        if (!isEnabled(activity)) return
                        if (!isSettingEnabled(activity, KEY_HIDE_DOCK)) return

                        val resultView = param.result as? View ?: return
                        val hotseat = findHotseat(activity) ?: return
                        if (isDescendantOf(resultView, hotseat)) {
                            param.result = null
                        }
                    }
                }
            )
            log("DockSearchWidgetHook app close target hook installed")
        } catch (t: Throwable) {
            logError("Failed to hook getFirstHomeElementForAppClose", t)
        }
    }

    private fun applyDockSettings(activity: Activity) {
        try {
            if (!isEnabled(activity)) return

            val hideSearch = isSettingEnabled(activity, KEY_HIDE_SEARCH)
            val hideDock = isSettingEnabled(activity, KEY_HIDE_DOCK)
            val paddingDock = getIntSetting(activity, KEY_PADDING_DOCK, 0)
            val paddingSearch = getIntSetting(activity, KEY_PADDING_SEARCH, 0)

            val currentHash = "$hideSearch|$hideDock|$paddingDock|$paddingSearch".hashCode()
            
            if (lastAppliedHash == currentHash) return

            log("Applying settings: hideSearch=$hideSearch, hideDock=$hideDock, paddingDock=$paddingDock, paddingSearch=$paddingSearch")

            val hotseat = findHotseat(activity)
            if (hotseat == null) {
                log("mHotseat not found in Activity")
                return
            }

            val qsbView = findQsbView(hotseat, activity)
            val dockIconsView = findHotseatCellLayout(hotseat)

            if (qsbView != null) {
                val searchTranslationY = if (paddingSearch != 0) -1f * toPx(activity, paddingSearch).toFloat() else 0f
                val searchVisibility = if (hideSearch) View.GONE else View.VISIBLE
                
                enforceViewProperties(qsbView, searchVisibility, searchTranslationY, forceHeightZero = hideSearch)
            } else {
                log("QSB view not found")
            }

            if (dockIconsView != null) {
                val dockTranslationY = if (paddingDock != 0) -1f * toPx(activity, paddingDock).toFloat() else 0f
                val dockVisibility = if (hideDock) View.GONE else View.VISIBLE
                
                enforceViewProperties(dockIconsView, dockVisibility, dockTranslationY, forceHeightZero = hideDock)
            } else {
                log("Dock icons view not found")
            }

            lastAppliedHash = currentHash

        } catch (e: Exception) {
            logError("Failed to apply dock settings", e)
        }
    }

    private fun enforceViewProperties(view: View, visibility: Int, translationY: Float, forceHeightZero: Boolean) {
        applyViewProps(view, visibility, translationY, forceHeightZero)
        val oldListener = view.getTag(TAG_VIEW_LISTENER) as? View.OnLayoutChangeListener
        if (oldListener != null) {
            view.removeOnLayoutChangeListener(oldListener)
        }
        val newListener = View.OnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            applyViewProps(v, visibility, translationY, forceHeightZero)
        }
        view.addOnLayoutChangeListener(newListener)
        view.setTag(TAG_VIEW_LISTENER, newListener)
    }

    private fun applyViewProps(view: View, visibility: Int, translationY: Float, forceHeightZero: Boolean) {
        if (view.visibility != visibility) {
            view.visibility = visibility
        }
        if (view.translationY != translationY) {
            view.translationY = translationY
        }

        val params = view.layoutParams
        if (params != null) {
            if (forceHeightZero) {
                if (params.height != 0) {
                    params.height = 0
                    view.layoutParams = params
                }
            } else if (visibility == View.VISIBLE) {
                if (params.height == 0) {
                    params.height = ViewGroup.LayoutParams.WRAP_CONTENT
                    view.layoutParams = params
                }
            }
        }
    }

    private fun findHotseat(activity: Activity): ViewGroup? {
        return try {
            XposedHelpers.getObjectField(activity, "mHotseat") as? ViewGroup
        } catch (e: Throwable) {
            null
        }
    }

    private fun findQsbView(hotseat: ViewGroup, context: Context): View? {
        try {
            val qsb = XposedHelpers.getObjectField(hotseat, "mQsb") as? View
            if (qsb != null) return qsb
        } catch (e: Throwable) { 
            log("mQsb field not found in Hotseat, trying alternative methods")
        }

        val ids = listOf("search_container_hotseat", "qsb_container")
        for (name in ids) {
            val resId = context.resources.getIdentifier(name, "id", context.packageName)
            if (resId != 0) {
                val v = hotseat.findViewById<View>(resId)
                if (v != null) return v
            }
        }
        return null
    }

    private fun findHotseatCellLayout(hotseat: ViewGroup): View? {
        val resId = hotseat.context.resources.getIdentifier("layout", "id", hotseat.context.packageName)
        if (resId != 0) {
            val v = hotseat.findViewById<View>(resId)
            if (v != null) return v
        }

        for (i in 0 until hotseat.childCount) {
            val child = hotseat.getChildAt(i)
            if (child is ViewGroup && child.javaClass.name.contains("CellLayout")) {
                return child
            }
        }
        
        for (i in 0 until hotseat.childCount) {
            val child = hotseat.getChildAt(i)
            if (child is ViewGroup) return child
        }
        return null
    }

    private fun toPx(context: Context, dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }

    private fun isDescendantOf(child: View, parent: View): Boolean {
        var current = child.parent
        while (current is View) {
            if (current == parent) return true
            current = current.parent
        }
        return false
    }
}