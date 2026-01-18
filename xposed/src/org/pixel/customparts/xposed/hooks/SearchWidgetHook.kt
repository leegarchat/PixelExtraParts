package org.pixel.customparts.xposed.hooks

import android.app.Activity
import android.content.Context
import android.graphics.Rect
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.ViewGroup
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import java.lang.reflect.Field
import java.lang.reflect.Method
import kotlin.math.roundToInt

object SearchWidgetHook {

    private const val TAG = "SearchWidgetNativeEmu"
    private const val KEY_DOCK_ENABLE = "launcher_dock_enable_xposed"
    private const val KEY_HIDE_SEARCH = "launcher_hidden_search_xposed"
    private const val KEY_HIDE_DOCK = "launcher_hidden_dock_xposed"
    private const val KEY_PADDING_HOMEPAGE = "launcher_padding_homepage"
    private const val KEY_PADDING_DOCK = "launcher_padding_dock"
    private const val KEY_PADDING_SEARCH = "launcher_padding_search"
    private const val KEY_PADDING_DOTS = "launcher_padding_dots"
    private var lastAppliedHash: Int = 0

    fun init(lpparam: LoadPackageParam) {
        if (lpparam.packageName != "com.google.android.apps.nexuslauncher") return

        try {
            val launcherClass = "com.android.launcher3.Launcher"

            XposedHelpers.findAndHookMethod(
                launcherClass,
                lpparam.classLoader,
                "onResume",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val activity = param.thisObject as Activity
                        val rootView = activity.findViewById<View>(android.R.id.content)
                        rootView?.post {
                            applySettings(activity)
                        } ?: applySettings(activity)
                    }
                }
            )
        } catch (t: Throwable) {
            XposedBridge.log("$TAG: Init error: $t")
        }
    }

    private fun applySettings(activity: Activity) {
        try {
            if (!isEnabled(activity, KEY_DOCK_ENABLE)) return
            val hideSearch = isEnabled(activity, KEY_HIDE_SEARCH)
            val hideDock = isEnabled(activity, KEY_HIDE_DOCK)
            val paddingHomepage = getInt(activity, KEY_PADDING_HOMEPAGE, -45)
            val paddingDock = getInt(activity, KEY_PADDING_DOCK, 0)   // 0 - default offset
            val paddingSearch = getInt(activity, KEY_PADDING_SEARCH, 0) // 0 - default offset
            val paddingDots = getInt(activity, KEY_PADDING_DOTS, -30)
            val currentHash = "$hideSearch|$hideDock|$paddingHomepage|$paddingDock|$paddingSearch|$paddingDots".hashCode()
            val deviceProfile = getField(activity, "mDeviceProfile") ?: return
            val workspace = getField(activity, "mWorkspace") as? View ?: return
            val hotseat = getField(activity, "mHotseat") as? ViewGroup ?: return
            if (lastAppliedHash != currentHash) {
                var dpChanged = false
                val paddingObj = getField(deviceProfile, "workspacePadding")
                if (paddingObj is Rect) {
                    val desiredBottom = if (paddingHomepage == -45) {
                        paddingObj.bottom
                    } else {
                        toPx(activity, paddingHomepage + 20)
                    }

                    if (paddingObj.bottom != desiredBottom) {
                        paddingObj.bottom = desiredBottom
                        dpChanged = true
                    }
                }

                if (dpChanged) {
                    triggerNativeUpdate(workspace, deviceProfile)
                    triggerNativeUpdate(hotseat, deviceProfile)
                }
                lastAppliedHash = currentHash
            }
            val qsbView = findQsbView(hotseat, activity)
            val dockIconsView = findHotseatCellLayout(hotseat)
            val pageIndicator = findPageIndicator(activity)
            if (qsbView != null) {
                val searchTranslationY = if (paddingSearch != 0) -1f * toPx(activity, paddingSearch).toFloat() else 0f
                val searchVisibility = if (hideSearch) View.GONE else View.VISIBLE
                enforceViewProperties(qsbView, searchVisibility, searchTranslationY, forceHeightZero = hideSearch)
            }
            if (dockIconsView != null) {
                val dockTranslationY = if (paddingDock != 0) -1f * toPx(activity, paddingDock).toFloat() else 0f
                val dockVisibility = if (hideDock) View.GONE else View.VISIBLE
                enforceViewProperties(dockIconsView, dockVisibility, dockTranslationY, forceHeightZero = hideDock)
            }
            if (pageIndicator != null) {
                applyDotsMargin(pageIndicator, activity, paddingDots)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply settings", e)
        }
    }
    private fun applyDotsMargin(view: View, context: Context, paddingDots: Int) {
        val params = view.layoutParams
        if (params !is ViewGroup.MarginLayoutParams) return
        val originalMarginTag = 0x7f010003
        var originalBottomMargin = view.getTag(originalMarginTag) as? Int
        if (originalBottomMargin == null) {
            originalBottomMargin = params.bottomMargin
            view.setTag(originalMarginTag, originalBottomMargin)
        }
        val diffDp = paddingDots - (-30)
        val diffPx = toPx(context, diffDp)
        val newBottomMargin = originalBottomMargin + diffPx
        if (params.bottomMargin != newBottomMargin) {
            params.bottomMargin = newBottomMargin
            view.layoutParams = params
        }
        val listenerTag = 0x7f010004
        if (view.getTag(listenerTag) == null) {
            val listener = View.OnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
                val p = v.layoutParams as? ViewGroup.MarginLayoutParams ?: return@OnLayoutChangeListener
                if (p.bottomMargin != newBottomMargin) {
                    p.bottomMargin = newBottomMargin
                    v.layoutParams = p
                }
            }
            view.addOnLayoutChangeListener(listener)
            view.setTag(listenerTag, listener)
        }
        if (view.translationY != 0f && paddingDots == -30) { 
        }
    }

    private fun enforceViewProperties(view: View, visibility: Int, translationY: Float, forceHeightZero: Boolean) {
        applyProps(view, visibility, translationY, forceHeightZero)
        val listenerTag = 0x7f010002
        var listener = view.getTag(listenerTag) as? View.OnLayoutChangeListener
        if (listener == null) {
            listener = View.OnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
                applyProps(v, visibility, translationY, forceHeightZero)
            }
            view.addOnLayoutChangeListener(listener)
            view.setTag(listenerTag, listener)
        } else {
            view.removeOnLayoutChangeListener(listener)
            val newListener = View.OnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
                applyProps(v, visibility, translationY, forceHeightZero)
            }
            view.addOnLayoutChangeListener(newListener)
            view.setTag(listenerTag, newListener)
        }
    }

    private fun applyProps(view: View, visibility: Int, translationY: Float, forceHeightZero: Boolean) {
        if (view.visibility != visibility) {
            view.visibility = visibility
        }
        if (view.translationY != translationY) {
            view.translationY = translationY
        }
        
        if (forceHeightZero) {
            val params = view.layoutParams
            if (params != null && params.height != 0) {
                params.height = 0
                view.layoutParams = params
            }
        } else if (visibility == View.VISIBLE) {
            val params = view.layoutParams
            if (params != null && params.height == 0) {
                params.height = ViewGroup.LayoutParams.WRAP_CONTENT
                view.layoutParams = params
            }
        }
    }

    private fun findQsbView(hotseat: ViewGroup, context: Context): View? {
        val qsb = getField(hotseat, "mQsb") as? View
        if (qsb != null) return qsb
        
        val resId1 = context.resources.getIdentifier("search_container_hotseat", "id", context.packageName)
        if (resId1 != 0) {
            val v = hotseat.findViewById<View>(resId1)
            if (v != null) return v
        }
        
        val resId2 = context.resources.getIdentifier("qsb_container", "id", context.packageName)
        if (resId2 != 0) {
            val v = hotseat.findViewById<View>(resId2)
            if (v != null) return v
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
            if (child is ViewGroup) {
                if (child.javaClass.name.contains("CellLayout")) {
                    return child
                }
            }
        }
        for (i in 0 until hotseat.childCount) {
            val child = hotseat.getChildAt(i)
            if (child is ViewGroup) return child
        }
        return null
    }

    private fun findPageIndicator(activity: Activity): View? {
        val resId = activity.resources.getIdentifier("page_indicator", "id", activity.packageName)
        if (resId != 0) {
            return activity.findViewById(resId)
        }
        return null
    }

    private fun triggerNativeUpdate(view: View, deviceProfile: Any) {
        try {
            val onDpChanged = findMethod(view.javaClass, "onDeviceProfileChanged", deviceProfile.javaClass)
            onDpChanged?.isAccessible = true
            onDpChanged?.invoke(view, deviceProfile)
        } catch (ignored: Exception) {}
    }

    private fun isEnabled(context: Context, key: String): Boolean {
        return Settings.Secure.getInt(context.contentResolver, key, 0) == 1
    }

    private fun getInt(context: Context, key: String, def: Int): Int {
        return Settings.Secure.getInt(context.contentResolver, key, def)
    }

    private fun toPx(context: Context, dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }

    private fun getField(obj: Any?, fieldName: String): Any? {
        if (obj == null) return null
        try {
            val field = findField(obj.javaClass, fieldName)
            if (field != null) {
                field.isAccessible = true
                return field.get(obj)
            }
        } catch (ignored: Exception) {}
        return null
    }

    private fun findField(clazz: Class<*>, fieldName: String): Field? {
        var current: Class<*>? = clazz
        while (current != null) {
            try {
                return current.getDeclaredField(fieldName)
            } catch (e: NoSuchFieldException) {
                current = current.superclass
            }
        }
        return null
    }
    
    private fun findMethod(clazz: Class<*>, methodName: String, vararg parameterTypes: Class<*>?): Method? {
        var current: Class<*>? = clazz
        while (current != null) {
            try {
                if (parameterTypes.isEmpty() || parameterTypes.any { it == null }) {
                    val methods = current.declaredMethods
                    val found = methods.find { it.name == methodName }
                    if (found != null) return found
                }
                @Suppress("UNCHECKED_CAST")
                val nonNullTypes = parameterTypes as Array<Class<*>>
                return current.getDeclaredMethod(methodName, *nonNullTypes)
            } catch (e: NoSuchMethodException) {
                current = current.superclass
            }
        }
        return null
    }
}