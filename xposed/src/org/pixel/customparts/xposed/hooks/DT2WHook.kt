package org.pixel.customparts.xposed.hooks

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.MotionEvent
import android.view.ViewConfiguration
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import kotlin.math.abs

object DT2WHook {
    private const val TAG = "PixelPartsDT2W"
    private const val KEY_DOZE_DOUBLE_TAP_HOOK = "doze_double_tap_hook_xposed"
    private const val KEY_DOZE_DOUBLE_TAP_TIMEOUT = "doze_double_tap_timeout"
    private var doubleTapPending = false
    private var lastTapX = -1f
    private var lastTapY = -1f
    private var doubleTapSlop = -1
    
    private var cachedTapSensor: Any? = null
    private var dozeLogTapReason = 2 

    private val handler = Handler(Looper.getMainLooper())
    private val resetRunnable = Runnable {
        doubleTapPending = false
        lastTapX = -1f
        lastTapY = -1f
    }
    
    private var isNativePatchDetected = false

    fun init(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != "com.android.systemui") return
        if (checkNativePatchExistence(lpparam.classLoader)) {
            Log.i(TAG, "Native DT2W patch detected inside SystemUI. Xposed hook disabled to prevent conflicts.")
            return
        }

        hookDozeTriggers(lpparam)
        hookPulsingGestureListener(lpparam)
    }

    private fun checkNativePatchExistence(classLoader: ClassLoader): Boolean {
        return try {
            val dozeSensorsClass = XposedHelpers.findClass("com.android.systemui.doze.DozeSensors", classLoader)
            val methods = dozeSensorsClass.declaredMethods
            methods.any { it.name == "reregisterTapSensor" }
        } catch (e: Throwable) {
            false
        }
    }

    private fun hookDozeTriggers(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            try {
                val dozeLogClass = XposedHelpers.findClass("com.android.systemui.doze.DozeLog", lpparam.classLoader)
                dozeLogTapReason = XposedHelpers.getStaticIntField(dozeLogClass, "REASON_SENSOR_TAP")
            } catch (e: Throwable) { /* Ignored */ }

            val dozeTriggersClass = XposedHelpers.findClass("com.android.systemui.doze.DozeTriggers", lpparam.classLoader)

            XposedHelpers.findAndHookMethod(
                dozeTriggersClass,
                "onSensor",
                Int::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType,
                Float::class.javaPrimitiveType,
                Float::class.javaPrimitiveType,
                FloatArray::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (isNativePatchDetected) return

                        val pulseReason = param.args[0] as Int
                        val screenX = param.args[2] as Float
                        val screenY = param.args[3] as Float
                        
                        if (pulseReason == dozeLogTapReason) {
                            val context = XposedHelpers.getObjectField(param.thisObject, "mContext") as Context
                            
                            val isHookEnabled = Settings.Secure.getInt(
                                context.contentResolver, KEY_DOZE_DOUBLE_TAP_HOOK, 0
                            ) == 1

                            if (isHookEnabled) {
                                handleHardwareTap(param, context, screenX, screenY)
                            }
                        }
                    }
                }
            )
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to hook DozeTriggers", e)
        }
    }

    private fun hookPulsingGestureListener(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val listenerClass = XposedHelpers.findClass("com.android.systemui.shade.PulsingGestureListener", lpparam.classLoader)
            
            XposedHelpers.findAndHookMethod(
                listenerClass,
                "onSingleTapUp",
                MotionEvent::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (isNativePatchDetected) return

                        val event = param.args[0] as MotionEvent
                        val view = XposedHelpers.getObjectField(param.thisObject, "notificationShadeWindowView")
                        val context = XposedHelpers.callMethod(view, "getContext") as Context
                        val isHookEnabled = Settings.Secure.getInt(
                            context.contentResolver, KEY_DOZE_DOUBLE_TAP_HOOK, 0
                        ) == 1
                        
                        if (!isHookEnabled) return

                        val statusBarStateController = XposedHelpers.getObjectField(param.thisObject, "statusBarStateController")
                        val isDozing = XposedHelpers.callMethod(statusBarStateController, "isDozing") as Boolean

                        if (isDozing) {
                            val handled = handleSoftwareTap(context, event.x, event.y)
                            if (handled) {
                                param.result = true
                            }
                        }
                    }
                }
            )
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to hook PulsingGestureListener", e)
        }
    }

    private fun handleHardwareTap(param: XC_MethodHook.MethodHookParam, context: Context, x: Float, y: Float) {
        if (doubleTapSlop < 0) {
            doubleTapSlop = ViewConfiguration.get(context).scaledDoubleTapSlop
        }

        val timeout = Settings.Secure.getInt(
            context.contentResolver, KEY_DOZE_DOUBLE_TAP_TIMEOUT, 200
        ).toLong()

        if (!doubleTapPending) {
            doubleTapPending = true
            lastTapX = x
            lastTapY = y

            handler.removeCallbacks(resetRunnable)
            handler.postDelayed(resetRunnable, timeout)

            reRegisterTapSensor(param.thisObject)
            
            param.result = null 
        } else {

            val dx = abs(x - lastTapX)
            val dy = abs(y - lastTapY)
            
            val isCoordsValid = x > 0 && lastTapX > 0

            val isCloseEnough = (x <= 0 && lastTapX <= 0) || (dx < doubleTapSlop && dy < doubleTapSlop)

            if (isCloseEnough) {
                doubleTapPending = false
                handler.removeCallbacks(resetRunnable)
            } else {
                lastTapX = x
                lastTapY = y
                
                handler.removeCallbacks(resetRunnable)
                handler.postDelayed(resetRunnable, timeout)
                
                reRegisterTapSensor(param.thisObject)
                param.result = null
            }
        }
    }

    private fun handleSoftwareTap(context: Context, x: Float, y: Float): Boolean {
        if (doubleTapSlop < 0) {
            doubleTapSlop = ViewConfiguration.get(context).scaledDoubleTapSlop
        }
        val timeout = Settings.Secure.getInt(
            context.contentResolver, KEY_DOZE_DOUBLE_TAP_TIMEOUT, 200
        ).toLong()

        if (!doubleTapPending) {
            doubleTapPending = true
            lastTapX = x
            lastTapY = y
            handler.removeCallbacks(resetRunnable)
            handler.postDelayed(resetRunnable, timeout)
            return true
        } else {
            val dx = abs(x - lastTapX)
            val dy = abs(y - lastTapY)
            if (dx < doubleTapSlop && dy < doubleTapSlop) {
                doubleTapPending = false
                handler.removeCallbacks(resetRunnable)
                return false
            } else {
                lastTapX = x
                lastTapY = y
                handler.removeCallbacks(resetRunnable)
                handler.postDelayed(resetRunnable, timeout)
                return true
            }
        }
    }

    private fun reRegisterTapSensor(dozeTriggers: Any) {
        try {
            if (cachedTapSensor != null) {
                toggleSensor(cachedTapSensor!!)
                return
            }

            val dozeSensors = XposedHelpers.getObjectField(dozeTriggers, "mDozeSensors")

            var sensorsArray: Array<*>? = null
            try {
                sensorsArray = XposedHelpers.getObjectField(dozeSensors, "mTriggerSensors") as Array<*>
            } catch (e: NoSuchFieldError) {
                try {
                    sensorsArray = XposedHelpers.getObjectField(dozeSensors, "mSensors") as Array<*>
                } catch (ignored: Throwable) {}
            }

            if (sensorsArray == null) {
                Log.e(TAG, "Could not find mTriggerSensors or mSensors in DozeSensors")
                return
            }

            for (sensor in sensorsArray) {
                if (sensor == null) continue
                val reason = XposedHelpers.getIntField(sensor, "mPulseReason")
                if (reason == dozeLogTapReason) {
                    cachedTapSensor = sensor
                    toggleSensor(sensor)
                    break
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to reregister tap sensor", e)
        }
    }

    private fun toggleSensor(sensor: Any) {
        XposedHelpers.callMethod(sensor, "setListening", false)
        XposedHelpers.callMethod(sensor, "setListening", true)
    }
}