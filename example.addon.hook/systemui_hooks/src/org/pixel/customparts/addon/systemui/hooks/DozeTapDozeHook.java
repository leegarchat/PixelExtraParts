package org.pixel.customparts.addon.systemui.hooks;

import android.content.Context;
import android.os.PowerManager;
import android.os.SystemClock;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

import java.lang.reflect.Method;
import java.util.Collection;

public class DozeTapDozeHook extends BaseSystemUIHook {
    
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
            return true;
        } catch (Throwable t) {
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

        // Try DozeMachine.wakeUp — signature varies across Android versions
        try {
            Object machine = XposedHelpers.getObjectField(dozeTriggers, "mMachine");
            if (machine != null) {
                boolean woke = false;
                // Android 16+: wakeUp() may take no args or different signature
                for (Method m : machine.getClass().getDeclaredMethods()) {
                    if (!"wakeUp".equals(m.getName())) continue;
                    m.setAccessible(true);
                    Class<?>[] params = m.getParameterTypes();
                    if (params.length == 0) {
                        m.invoke(machine);
                        woke = true;
                        break;
                    } else if (params.length == 1 && (params[0] == int.class || params[0] == Integer.class)) {
                        m.invoke(machine, reason);
                        woke = true;
                        break;
                    }
                }
                if (woke) return;
            }
        } catch (Throwable ignored) {
        }

        // Fallback: PowerManager.wakeUp
        try {
            Context context = (Context) XposedHelpers.getObjectField(dozeTriggers, "mContext");
            if (context == null) return;
            PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            if (powerManager != null) {
                Method wakeUp = PowerManager.class.getMethod(
                    "wakeUp", long.class, int.class, String.class);
                wakeUp.invoke(powerManager, SystemClock.uptimeMillis(),
                    WAKE_REASON_TAP, "PixelPartsDozeDoubleTap");
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
            if (reason != dozeTapReason) return;

            // Android 16+: setListening(boolean) was removed; use mRequested + updateListening
            try {
                XposedHelpers.setBooleanField(sensor, "mRequested", false);
                XposedHelpers.callMethod(sensor, "updateListening");
                XposedHelpers.setBooleanField(sensor, "mRequested", true);
                XposedHelpers.callMethod(sensor, "updateListening");
            } catch (Throwable e2) {
                // Last resort: try setListening if available on older API
                try {
                    Method method = sensor.getClass().getDeclaredMethod("setListening", boolean.class);
                    method.setAccessible(true);
                    method.invoke(sensor, false);
                    method.invoke(sensor, true);
                } catch (Throwable ignored) {}
            }
        } catch (Throwable e) {
            logError("DozeTapDozeHook: checkAndResetSensor failed", e);
        }
    }
}