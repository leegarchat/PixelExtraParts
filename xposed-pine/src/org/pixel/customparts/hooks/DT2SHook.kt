package org.pixel.customparts.hooks

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.os.SystemClock
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import org.pixel.customparts.core.BaseHook
import java.lang.ref.WeakReference
import java.lang.reflect.Field

class DT2SHook : BaseHook() {

    override val hookId = "DT2SHook"
    override val priority = 100

    companion object {
        private const val CLASS_WORKSPACE = "com.android.launcher3.Workspace"
        private const val KEY_ENABLED = "launcher_dt2s_enabled"
        private const val KEY_TIMEOUT = "launcher_dt2s_timeout"
        
        private val detectors = mutableMapOf<Int, WeakReference<GestureDetector>>()
        private var doubleTapTimeoutField: Field? = null
        
        init {
            try {
                doubleTapTimeoutField = GestureDetector::class.java.getDeclaredField("mDoubleTapTimeout")
                doubleTapTimeoutField?.isAccessible = true
            } catch (e: Exception) { }
        }
    }

    override fun isEnabled(context: Context?): Boolean {
        return isSettingEnabled(context, KEY_ENABLED)
    }

    override fun onInit(classLoader: ClassLoader) {
        try {
            val workspaceClass = XposedHelpers.findClass(CLASS_WORKSPACE, classLoader)

            XposedHelpers.findAndHookMethod(
                workspaceClass,
                "onTouchEvent",
                MotionEvent::class.java,
                object : XC_MethodHook() {
                    @SuppressLint("ClickableViewAccessibility")
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        handleTouchEvent(param)
                    }
                }
            )

            log("Workspace hook installed successfully")
        } catch (e: Throwable) {
            logError("Failed to hook Workspace", e)
        }
    }

    private fun handleTouchEvent(param: XC_MethodHook.MethodHookParam) {
        val view = param.thisObject as View
        val event = param.args[0] as MotionEvent
        val context = view.context

        if (!isSettingEnabled(context, KEY_ENABLED)) return

        val viewId = System.identityHashCode(view)
        var detector = detectors[viewId]?.get()

        if (detector == null) {
            detector = createGestureDetector(context)
            detectors[viewId] = WeakReference(detector)
        }

        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            updateDetectorTimeout(context, detector)
        }

        if (detector.onTouchEvent(event)) {
            param.result = true
        }
    }

    private fun createGestureDetector(context: Context): GestureDetector {
        return GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (isSettingEnabled(context, KEY_ENABLED)) {
                    log("Double Tap detected")
                    performSleep(context)
                    return true
                }
                return false
            }
        }).apply {
            setIsLongpressEnabled(false)
        }
    }

    private fun updateDetectorTimeout(context: Context, detector: GestureDetector) {
        try {
            val customTimeout = getIntSetting(context, KEY_TIMEOUT, 400)
            doubleTapTimeoutField?.setInt(detector, customTimeout)
        } catch (e: Exception) { }
    }

    private fun performSleep(context: Context) {
        try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            XposedHelpers.callMethod(pm, "goToSleep", SystemClock.uptimeMillis())
            log("Sleep via PowerManager successful (Direct)")
            return
        } catch (t: Throwable) {
            
            log("Direct PowerManager sleep failed, trying broadcast fallback...")
        }
        try {
            val intent = Intent("org.pixel.customparts.ACTION_SLEEP")
            intent.setClassName("org.pixel.customparts", "org.pixel.customparts.services.SleepReceiver")
            intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES) 
            
            context.sendBroadcast(intent)
            log("Sleep broadcast sent to org.pixel.customparts")
        } catch (e: Throwable) {
            logError("Failed to send Sleep broadcast", e)
        }
    }

    override fun onActivityDestroyed(activity: Activity) {
        detectors.entries.removeIf { it.value.get() == null }
    }
}