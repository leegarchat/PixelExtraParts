package org.pixel.customparts.xposed.hooks

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.lang.reflect.Field
import java.util.WeakHashMap

object DT2SHook {

    private const val TAG = "PixelParts_DT2S"
    private const val CLASS_WORKSPACE = "com.android.launcher3.Workspace"
    private const val KEY_ENABLED = "launcher_dt2s_enabled_xposed"
    private const val KEY_TIMEOUT = "launcher_dt2s_timeout"
    private val detectors = WeakHashMap<View, GestureDetector>()
    private var doubleTapTimeoutField: Field? = null

    fun init(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != "com.google.android.apps.nexuslauncher") return
        try {
            doubleTapTimeoutField = GestureDetector::class.java.getDeclaredField("mDoubleTapTimeout")
            doubleTapTimeoutField?.isAccessible = true
        } catch (e: Exception) {
            XposedBridge.log("$TAG: Failed to get mDoubleTapTimeout field: $e")
        }

        try {
            val workspaceClass = XposedHelpers.findClass(CLASS_WORKSPACE, lpparam.classLoader)

            XposedHelpers.findAndHookMethod(
                workspaceClass,
                "onTouchEvent",
                MotionEvent::class.java,
                object : XC_MethodHook() {
                    @SuppressLint("ClickableViewAccessibility")
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val view = param.thisObject as View
                        val event = param.args[0] as MotionEvent
                        val context = view.context

                        var detector = detectors[view]
                        if (detector == null) {
                            detector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
                                override fun onDoubleTap(e: MotionEvent): Boolean {
                                    val isEnabled = Settings.Secure.getInt(
                                        context.contentResolver, KEY_ENABLED, 1
                                    ) == 1
                                    
                                    if (isEnabled) {
                                        XposedBridge.log("$TAG: Double Tap detected")
                                        performSleep(context)
                                        return true
                                    }
                                    return false
                                }
                            })
                            detector.setIsLongpressEnabled(false)
                            detectors[view] = detector
                        }
                        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                            updateDetectorTimeout(context, detector!!)
                        }

                        if (detector!!.onTouchEvent(event)) {
                            param.result = true
                        }
                    }
                }
            )
        } catch (e: Throwable) {
            XposedBridge.log("$TAG: Failed to hook Workspace: $e")
        }
    }

    private fun updateDetectorTimeout(context: Context, detector: GestureDetector) {
        try {
            val customTimeout = Settings.Secure.getInt(
                context.contentResolver, KEY_TIMEOUT, 250
            )
            doubleTapTimeoutField?.setInt(detector, customTimeout)
        } catch (e: Exception) { /* Ignored */ }
    }

    private fun performSleep(context: Context) {
        try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            XposedHelpers.callMethod(pm, "goToSleep", SystemClock.uptimeMillis())
        } catch (e: Throwable) {
            try {
                val intent = Intent("org.pixel.customparts.ACTION_SLEEP")
                intent.setPackage("com.android.systemui")
                context.sendBroadcast(intent)
            } catch (e2: Throwable) {
                XposedBridge.log("$TAG: Failed to sleep: $e")
            }
        }
    }
}