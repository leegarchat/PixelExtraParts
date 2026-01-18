package org.pixel.customparts.xposed.hooks

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.ViewParent
import android.widget.TextView
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.lang.reflect.Field

object LauncherDebugHook {

    private const val TAG = "PixelParts_Debugger"
    private const val KEY_DEBUG_ENABLED = "launcher_debug_xposed"
    private const val LAUNCHER_CLASS = "com.android.launcher3.Launcher"
    private var listenerInfoField: Field? = null
    private var onClickListenerField: Field? = null
    private var onLongClickListenerField: Field? = null
    private var onTouchListenerField: Field? = null

    fun init(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != "com.google.android.apps.nexuslauncher") return

        try {
            prepareReflection()

            val launcherClass = XposedHelpers.findClass(LAUNCHER_CLASS, lpparam.classLoader)
            XposedHelpers.findAndHookMethod(
                launcherClass,
                "onResume",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val activity = param.thisObject as Activity
                        val context = activity.applicationContext
                        val isDebug = Settings.Secure.getInt(
                            context.contentResolver, KEY_DEBUG_ENABLED, 0
                        ) == 1

                        if (isDebug) {
                            Handler(Looper.getMainLooper()).postDelayed({
                                runFullDiagnostics(activity)
                            }, 5000)
                        }
                    }
                }
            )

        } catch (e: Throwable) {
            XposedBridge.log("$TAG: Failed to init debugger: $e")
        }
    }

    private fun runFullDiagnostics(activity: Activity) {
        Log.i(TAG, "===============================================================")
        Log.i(TAG, "STARTING UI DUMP FOR: ${activity.localClassName}")
        Log.i(TAG, "===============================================================")

        try {
            val rootView = activity.window.decorView
            dumpViewHierarchy(rootView, 0)
        } catch (e: Exception) {
            Log.e(TAG, "Error during dump", e)
        }

        Log.i(TAG, "===============================================================")
        Log.i(TAG, "END UI DUMP")
        Log.i(TAG, "===============================================================")
    }

    private fun dumpViewHierarchy(view: View, depth: Int) {
        val indent = "  ".repeat(depth)
        val info = extractViewInfo(view)

        // Логируем текущий View
        Log.d(TAG, "$indent$info")

        // Рекурсия для детей
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                dumpViewHierarchy(view.getChildAt(i), depth + 1)
            }
        }
    }

    private fun extractViewInfo(view: View): String {
        val sb = StringBuilder()
        sb.append("[${view.javaClass.simpleName}] ")
        val resName = getResourceName(view)
        if (resName.isNotEmpty()) {
            sb.append("id=@$resName ")
        }
        val vis = when (view.visibility) {
            View.VISIBLE -> "VISIBLE"
            View.INVISIBLE -> "INVISIBLE"
            View.GONE -> "GONE"
            else -> "UNKNOWN"
        }
        sb.append("Vis:$vis ")
        if (!view.isEnabled) sb.append("DISABLED ")
        if (view.isSelected) sb.append("SELECTED ")
        if (view.alpha < 1.0f) sb.append("Alpha:${String.format("%.2f", view.alpha)} ")
        sb.append("Geom:(${view.left},${view.top}-${view.right},${view.bottom}) ${view.width}x${view.height} ")

        if (view is TextView) {
            if (view.text.isNotEmpty()) {
                sb.append("Text:\"${view.text}\" ")
            }
            sb.append("Color:${String.format("#%06X", (0xFFFFFF and view.currentTextColor))} ")
            sb.append("Size:${view.textSize}px ")
        }

        val lp = view.layoutParams
        if (lp is ViewGroup.MarginLayoutParams) {
            sb.append("Margins:[L:${lp.leftMargin},T:${lp.topMargin},R:${lp.rightMargin},B:${lp.bottomMargin}] ")
        }
        if (lp != null) {
            sb.append("LP_W:${layoutSizeToString(lp.width)} LP_H:${layoutSizeToString(lp.height)} ")
        }

        if (view.background != null) {
            sb.append("Bg:${view.background.javaClass.simpleName} ")
        }

        val listeners = getListenersInfo(view)
        if (listeners.isNotEmpty()) {
            sb.append("\n${"  ".repeat(10)}>> LISTENERS: $listeners")
        }

        return sb.toString()
    }

    private fun getResourceName(view: View): String {
        if (view.id == View.NO_ID) return ""
        return try {
            view.resources.getResourceEntryName(view.id)
        } catch (e: Exception) {
            "0x${Integer.toHexString(view.id)}"
        }
    }

    private fun layoutSizeToString(size: Int): String {
        return when (size) {
            ViewGroup.LayoutParams.MATCH_PARENT -> "MATCH"
            ViewGroup.LayoutParams.WRAP_CONTENT -> "WRAP"
            else -> size.toString()
        }
    }

    private fun getListenersInfo(view: View): String {
        val result = ArrayList<String>()
        try {
            val listenerInfo = listenerInfoField?.get(view) ?: return ""

            val onClick = onClickListenerField?.get(listenerInfo)
            if (onClick != null) {
                result.add("OnClick: ${onClick.javaClass.name}")
            }

            val onLong = onLongClickListenerField?.get(listenerInfo)
            if (onLong != null) {
                result.add("OnLongClick: ${onLong.javaClass.name}")
            }

            val onTouch = onTouchListenerField?.get(listenerInfo)
            if (onTouch != null) {
                result.add("OnTouch: ${onTouch.javaClass.name}")
            }

        } catch (e: Exception) { /* Ignored */ }
        return result.joinToString(", ")
    }

    private fun prepareReflection() {
        try {
            val viewClass = View::class.java
            listenerInfoField = viewClass.getDeclaredField("mListenerInfo").apply { isAccessible = true }
            
            val liClass = Class.forName("android.view.View\$ListenerInfo")
            onClickListenerField = liClass.getDeclaredField("mOnClickListener").apply { isAccessible = true }
            onLongClickListenerField = liClass.getDeclaredField("mOnLongClickListener").apply { isAccessible = true }
            onTouchListenerField = liClass.getDeclaredField("mOnTouchListener").apply { isAccessible = true }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to prepare reflection for listeners", e)
        }
    }
}