package org.pixel.customparts.hooks.systemui

import android.content.Context
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import org.pixel.customparts.core.BaseHook

class DozeTapDozeHook : BaseHook() {
    override val hookId = "DozeTapDozeHook"
    override val priority = 70

    private var dozeTapReason = 9

    override fun isEnabled(context: Context?): Boolean = true

    override fun onInit(classLoader: ClassLoader) {
        resolveTapReason(classLoader)
        hookDozeTriggers(classLoader)
    }

    private fun resolveTapReason(classLoader: ClassLoader) {
        try {
            val dozeLogClass = XposedHelpers.findClass("com.android.systemui.doze.DozeLog", classLoader)
            dozeTapReason = XposedHelpers.getStaticIntField(dozeLogClass, "REASON_SENSOR_TAP")
        } catch (_: Throwable) {
            log("DozeTapDozeHook: Using default REASON_SENSOR_TAP = $dozeTapReason")
        }
    }

    private fun hookDozeTriggers(classLoader: ClassLoader) {
        try {
            val dozeTriggersClass = XposedHelpers.findClass(
                "com.android.systemui.doze.DozeTriggers",
                classLoader
            )

            XposedBridge.hookAllMethods(dozeTriggersClass, "onSensor", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    try {
                        val pulseReason = param.args.firstOrNull { it is Int } as? Int ?: return
                        if (pulseReason != dozeTapReason) return

                        val dozeTriggers = param.thisObject
                        val context = XposedHelpers.getObjectField(dozeTriggers, "mContext") as? Context ?: return
                        if (!isSettingEnabled(context, DozeTapKeys.KEY_HOOK)) return

                        val floatArgs = param.args.filterIsInstance<Float>()
                        val screenX = floatArgs.getOrNull(0) ?: return
                        val screenY = floatArgs.getOrNull(1) ?: return

                        val timeout = getIntSetting(context, DozeTapKeys.KEY_TIMEOUT, DozeTapKeys.DEFAULT_TIMEOUT)
                        val consumed = DozeTapManager.processTap(
                            context,
                            screenX,
                            screenY,
                            true,
                            timeout
                        ) {
                            reregisterTapSensor(dozeTriggers)
                        }

                        if (consumed) {
                            param.result = null
                        }
                    } catch (t: Throwable) {
                        log("DozeTapDozeHook: Error in onSensor: ${t.message}")
                    }
                }
            })

            log("DozeTapDozeHook: Hook applied successfully")
        } catch (e: Throwable) {
            log("DozeTapDozeHook: Failed to apply hook: ${e.message}")
        }
    }

    private fun reregisterTapSensor(dozeTriggers: Any) {
        val dozeSensors = XposedHelpers.getObjectField(dozeTriggers, "mDozeSensors") ?: return
        val triggerSensors = XposedHelpers.getObjectField(dozeSensors, "mTriggerSensors") ?: return

        if (triggerSensors is Array<*>) {
            for (sensor in triggerSensors) {
                if (sensor == null) continue
                checkAndResetSensor(sensor)
            }
        } else if (triggerSensors is Collection<*>) {
            for (sensor in triggerSensors) {
                if (sensor == null) continue
                checkAndResetSensor(sensor)
            }
        }
    }

    private fun checkAndResetSensor(sensor: Any) {
        try {
            val reason = XposedHelpers.getIntField(sensor, "mPulseReason")
            if (reason == dozeTapReason) {
                val method = sensor.javaClass.getDeclaredMethod("setListening", Boolean::class.javaPrimitiveType)
                method.isAccessible = true
                method.invoke(sensor, false)
                method.invoke(sensor, true)
                log("DozeTapDozeHook: Sensor re-registered OK")
            }
        } catch (e: Throwable) {
            log("DozeTapDozeHook: checkAndResetSensor failed: ${e.message}")
            try {
                XposedHelpers.setBooleanField(sensor, "mRequested", false)
                XposedHelpers.callMethod(sensor, "updateListening")
                XposedHelpers.setBooleanField(sensor, "mRequested", true)
                XposedHelpers.callMethod(sensor, "updateListening")
                log("DozeTapDozeHook: Sensor re-registered via fallback OK")
            } catch (e2: Throwable) {
                log("DozeTapDozeHook: Fallback re-register also failed: ${e2.message}")
            }
        }
    }
}
