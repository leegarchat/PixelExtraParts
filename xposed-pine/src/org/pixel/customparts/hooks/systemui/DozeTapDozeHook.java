package org.pixel.customparts.hooks.systemui;

import android.content.Context;
import android.os.PowerManager;
import android.os.SystemClock;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import org.pixel.customparts.core.BaseHook;

import java.lang.reflect.Method;
import java.util.Collection;

public class DozeTapDozeHook extends BaseHook {
    
    private static final int WAKE_REASON_TAP = 15;

    private int dozeTapReason = 9; // Default fallback

    @Override
    public String getHookId() {
        return "DozeTapDozeHook";
    }

    @Override
    public int getPriority() {
        return 70;
    }

    @Override
    public boolean isEnabled(Context context) {
        return true;
    }

    @Override
    protected void onInit(ClassLoader classLoader) {
        resolveTapReason(classLoader);
        hookDozeTriggers(classLoader);
    }

    private void resolveTapReason(ClassLoader classLoader) {
        try {
            Class<?> dozeLogClass = XposedHelpers.findClass("com.android.systemui.doze.DozeLog", classLoader);
            dozeTapReason = XposedHelpers.getStaticIntField(dozeLogClass, "REASON_SENSOR_TAP");
        } catch (Throwable t) {
            log("DozeTapDozeHook: Using default REASON_SENSOR_TAP = " + dozeTapReason);
        }
    }

    private void hookDozeTriggers(ClassLoader classLoader) {
        try {
            Class<?> dozeTriggersClass = XposedHelpers.findClass(
                "com.android.systemui.doze.DozeTriggers",
                classLoader
            );

            XposedBridge.hookAllMethods(dozeTriggersClass, "onSensor", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    try {
                        // Ищем первый аргумент типа Int (pulseReason)
                        int pulseReason = -1;
                        for (Object arg : param.args) {
                            if (arg instanceof Integer) {
                                pulseReason = (Integer) arg;
                                break;
                            }
                        }

                        if (pulseReason != dozeTapReason) return;

                        Object dozeTriggers = param.thisObject;
                        Context context = (Context) XposedHelpers.getObjectField(dozeTriggers, "mContext");
                        if (context == null) return;
                        
                        if (!isSettingEnabled(context, DozeTapManager.KEY_HOOK)) return;

                        // Ищем float аргументы (x, y)
                        float screenX = -1f;
                        float screenY = -1f;
                        int floatCount = 0;
                        
                        for (Object arg : param.args) {
                            if (arg instanceof Float) {
                                if (floatCount == 0) screenX = (Float) arg;
                                else if (floatCount == 1) screenY = (Float) arg;
                                floatCount++;
                            }
                        }

                        if (floatCount < 2) return;

                        int timeout = getIntSetting(context, DozeTapManager.KEY_TIMEOUT, DozeTapManager.DEFAULT_TIMEOUT);
                        final int tapReason = pulseReason;
                        final float tapX = screenX;
                        final float tapY = screenY;
                        
                        DozeTapManager.TapResult tapResult = DozeTapManager.processTap(
                            context,
                            tapX,
                            tapY,
                            true,
                            timeout,
                            new Runnable() {
                                @Override
                                public void run() {
                                    prepareTapSensorForNextTap(dozeTriggers);
                                }
                            },
                            new Runnable() {
                                @Override
                                public void run() {
                                    wakeFromSystemDoze(dozeTriggers, tapReason, tapX, tapY);
                                }
                            }
                        );

                        if (tapResult != DozeTapManager.TapResult.IGNORED) {
                            param.setResult(null); // Блокируем выполнение оригинального метода
                        }
                    } catch (Throwable t) {
                        logError("DozeTapDozeHook: Error in onSensor", t);
                    }
                }
            });

            log("DozeTapDozeHook: Hook applied successfully");
        } catch (Throwable e) {
            logError("DozeTapDozeHook: Failed to apply hook", e);
        }
    }

    private void prepareTapSensorForNextTap(Object dozeTriggers) {
        enableNativeTapSensorReregister(dozeTriggers);
        reregisterTapSensor(dozeTriggers);
    }

    private boolean enableNativeTapSensorReregister(Object dozeTriggers) {
        try {
            Object dozeSensors = XposedHelpers.getObjectField(dozeTriggers, "mDozeSensors");
            if (dozeSensors == null) return false;

            Object triggerSensors = XposedHelpers.getObjectField(dozeSensors, "mTriggerSensors");
            if (triggerSensors == null) return false;

            boolean updated = false;
            if (triggerSensors instanceof Object[]) {
                for (Object sensor : (Object[]) triggerSensors) {
                    updated |= enableNativeTapSensorReregisterForSensor(sensor);
                }
            } else if (triggerSensors instanceof Collection) {
                for (Object sensor : (Collection<?>) triggerSensors) {
                    updated |= enableNativeTapSensorReregisterForSensor(sensor);
                }
            }
            return updated;
        } catch (Throwable t) {
            logError("DozeTapDozeHook: enableNativeTapSensorReregister failed", t);
            return false;
        }
    }

    private boolean enableNativeTapSensorReregisterForSensor(Object sensor) {
        if (sensor == null) return false;
        try {
            int reason = XposedHelpers.getIntField(sensor, "mPulseReason");
            if (reason != dozeTapReason) return false;
            XposedHelpers.setBooleanField(sensor, "mImmediatelyReRegister", true);
            log("DozeTapDozeHook: native tap sensor re-register enabled");
            return true;
        } catch (Throwable t) {
            log("DozeTapDozeHook: native re-register enable failed: " + t.getMessage());
            return false;
        }
    }

    private void wakeFromSystemDoze(Object dozeTriggers, int reason, float screenX, float screenY) {
        try {
            Object host = XposedHelpers.getObjectField(dozeTriggers, "mDozeHost");
            if (host != null) {
                XposedHelpers.callMethod(host, "onSlpiTap", screenX, screenY);
            }
        } catch (Throwable ignored) {
        }

        try {
            Object machine = XposedHelpers.getObjectField(dozeTriggers, "mMachine");
            if (machine != null) {
                XposedHelpers.callMethod(machine, "wakeUp", reason);
                log("DozeTapDozeHook: double tap woke via DozeMachine");
                return;
            }
        } catch (Throwable t) {
            log("DozeTapDozeHook: DozeMachine wake failed: " + t.getMessage());
        }

        try {
            Context context = (Context) XposedHelpers.getObjectField(dozeTriggers, "mContext");
            if (context == null) return;
            PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            if (powerManager != null) {
                Method wakeUp = PowerManager.class.getMethod(
                    "wakeUp", long.class, int.class, String.class);
                wakeUp.invoke(powerManager, SystemClock.uptimeMillis(),
                    WAKE_REASON_TAP, "PixelPartsDozeDoubleTap");
                log("DozeTapDozeHook: double tap woke via PowerManager fallback");
            }
        } catch (Throwable t) {
            logError("DozeTapDozeHook: PowerManager wake fallback failed", t);
        }
    }

    private void reregisterTapSensor(Object dozeTriggers) {
        try {
            Object dozeSensors = XposedHelpers.getObjectField(dozeTriggers, "mDozeSensors");
            if (dozeSensors == null) return;
            
            Object triggerSensors = XposedHelpers.getObjectField(dozeSensors, "mTriggerSensors");
            if (triggerSensors == null) return;

            if (triggerSensors instanceof Object[]) {
                for (Object sensor : (Object[]) triggerSensors) {
                    if (sensor != null) checkAndResetSensor(sensor);
                }
            } else if (triggerSensors instanceof Collection) {
                for (Object sensor : (Collection<?>) triggerSensors) {
                    if (sensor != null) checkAndResetSensor(sensor);
                }
            }
        } catch (Throwable t) {
             logError("DozeTapDozeHook: reregisterTapSensor failed", t);
        }
    }

    private void checkAndResetSensor(Object sensor) {
        try {
            int reason = XposedHelpers.getIntField(sensor, "mPulseReason");
            if (reason == dozeTapReason) {
                Method method = sensor.getClass().getDeclaredMethod("setListening", boolean.class);
                method.setAccessible(true);
                method.invoke(sensor, false);
                method.invoke(sensor, true);
                log("DozeTapDozeHook: Sensor re-registered OK");
            }
        } catch (Throwable e) {
            log("DozeTapDozeHook: checkAndResetSensor failed: " + e.getMessage());
            try {
                // Fallback method
                XposedHelpers.setBooleanField(sensor, "mRequested", false);
                XposedHelpers.callMethod(sensor, "updateListening");
                XposedHelpers.setBooleanField(sensor, "mRequested", true);
                XposedHelpers.callMethod(sensor, "updateListening");
                log("DozeTapDozeHook: Sensor re-registered via fallback OK");
            } catch (Throwable e2) {
                log("DozeTapDozeHook: Fallback re-register also failed: " + e2.getMessage());
            }
        }
    }
}