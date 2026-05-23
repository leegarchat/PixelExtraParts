package org.pixel.customparts.addon.systemui.hooks;

import android.content.Context;
import android.os.PowerManager;
import android.os.SystemClock;
import android.view.MotionEvent;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

import java.lang.reflect.Method;

public class DozeTapShadeHook extends BaseSystemUIHook {

    private static final int WAKE_REASON_TAP = 15;

    @Override
    public String getHookId() {
        return "DozeTapShadeHook";
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
        hookPulsingGestureListener(classLoader);
    }

    private void hookPulsingGestureListener(ClassLoader classLoader) {
        try {
            Class<?> listenerClass = XposedHelpers.findClass(
                "com.android.systemui.shade.PulsingGestureListener",
                classLoader
            );
            
            XposedBridge.hookAllMethods(listenerClass, "onSingleTapUp", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    try {
                        Object controller = XposedHelpers.getObjectField(param.thisObject, "statusBarStateController");
                        Boolean isDozingObj = (Boolean) XposedHelpers.callMethod(controller, "isDozing");
                        boolean isDozing = isDozingObj != null && isDozingObj;

                        if (!isDozing) return;

                        Context context = resolveAppContext(param.thisObject.getClass().getClassLoader());
                        if (context == null) return;

                        if (!isSettingEnabled(context, DozeTapManager.KEY_HOOK)) return;

                        int timeout = getIntSetting(context, DozeTapManager.KEY_TIMEOUT, DozeTapManager.DEFAULT_TIMEOUT);
                        Runnable wakeAction = new Runnable() {
                            @Override
                            public void run() {
                                wakeFromSystemUi(context);
                            }
                        };

                        DozeTapManager.TapResult tapResult = DozeTapManager.TapResult.IGNORED;

                        if (param.args.length == 1 && param.args[0] instanceof MotionEvent) {
                            MotionEvent event = (MotionEvent) param.args[0];
                            tapResult = DozeTapManager.processTap(
                                context, event.getX(), event.getY(),
                                true, timeout, null, wakeAction
                            );
                        } else if (param.args.length >= 2 && param.args[0] instanceof Float && param.args[1] instanceof Float) {
                            tapResult = DozeTapManager.processTap(
                                context, (Float) param.args[0], (Float) param.args[1],
                                true, timeout, null, wakeAction
                            );
                        } else {
                            return;
                        }

                        if (tapResult != DozeTapManager.TapResult.IGNORED) {
                            param.setResult(true);
                        }
                    } catch (Throwable t) {
                        logError("Error in PulsingGestureListener hook", t);
                    }
                }
            });

            log("DozeTapShadeHook: Hook applied successfully");
        } catch (Throwable e) {
            logError("DozeTapShadeHook: Failed to apply hook", e);
        }
    }

    private void wakeFromSystemUi(Context context) {
        try {
            PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            if (powerManager == null) return;
            Method wakeUp = PowerManager.class.getMethod(
                "wakeUp", long.class, int.class, String.class);
            wakeUp.invoke(powerManager, SystemClock.uptimeMillis(),
                WAKE_REASON_TAP, "PixelPartsDozeDoubleTapShade");
            log("DozeTapShadeHook: double tap woke via PowerManager");
        } catch (Throwable t) {
            log("DozeTapShadeHook: wake failed: " + t.getMessage());
        }
    }

    private Context resolveAppContext(ClassLoader classLoader) {
        try {
            Class<?> activityThreadClass = XposedHelpers.findClass("android.app.ActivityThread", classLoader);
            return (Context) XposedHelpers.callStaticMethod(activityThreadClass, "currentApplication");
        } catch (Throwable t) {
            return null;
        }
    }
}