package org.pixel.customparts.hooks.systemui;

import android.content.Context;
import android.os.PowerManager;
import android.os.SystemClock;
import android.view.MotionEvent;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import org.pixel.customparts.core.BaseHook;

import java.lang.reflect.Method;

public class DozeTapShadeHook extends BaseHook {

    private static final int WAKE_REASON_TAP = 15;

    private boolean loggedHook = false;
    private int tapLogCount = 0;

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
                        if (!loggedHook) {
                            loggedHook = true;
                            log("DozeTapShadeHook: onSingleTapUp hooked (all overloads)");
                        }
                        
                        if (tapLogCount < 10) {
                            tapLogCount++;
                            log("DozeTapShadeHook: onSingleTapUp invoked (#" + tapLogCount + ")");
                        }

                        Object controller = XposedHelpers.getObjectField(param.thisObject, "statusBarStateController");
                        Boolean isDozingObj = (Boolean) XposedHelpers.callMethod(controller, "isDozing");
                        boolean isDozing = isDozingObj != null && isDozingObj;

                        if (tapLogCount <= 10) {
                            log("DozeTapShadeHook: isDozing=" + isDozing);
                        }

                        if (!isDozing) {
                            return;
                        }

                        Context context = resolveAppContext(param.thisObject.getClass().getClassLoader());
                        if (context == null) {
                            if (tapLogCount <= 10) {
                                log("DozeTapShadeHook: app context not available");
                            }
                            return;
                        }

                        boolean enabled = isSettingEnabled(context, DozeTapManager.KEY_HOOK);
                        if (tapLogCount <= 10) {
                            log("DozeTapShadeHook: enabled=" + enabled);
                        }
                        if (!enabled) {
                            return;
                        }

                        int timeout = getIntSetting(context, DozeTapManager.KEY_TIMEOUT, DozeTapManager.DEFAULT_TIMEOUT);
                        DozeTapManager.TapResult tapResult = DozeTapManager.TapResult.IGNORED;
                        Runnable wakeAction = new Runnable() {
                            @Override
                            public void run() {
                                wakeFromSystemUi(context);
                            }
                        };

                        if (param.args.length == 1 && param.args[0] instanceof MotionEvent) {
                            MotionEvent event = (MotionEvent) param.args[0];
                            tapResult = DozeTapManager.processTap(
                                context,
                                event.getX(),
                                event.getY(),
                                true,
                                timeout,
                                null,
                                wakeAction
                            );
                        } else if (param.args.length >= 2 && param.args[0] instanceof Float && param.args[1] instanceof Float) {
                            tapResult = DozeTapManager.processTap(
                                context,
                                (Float) param.args[0],
                                (Float) param.args[1],
                                true,
                                timeout,
                                null,
                                wakeAction
                            );
                        } else {
                            if (tapLogCount <= 10) {
                                log("DozeTapShadeHook: unsupported args size=" + param.args.length);
                            }
                            return;
                        }

                        if (tapLogCount <= 10) {
                            log("DozeTapShadeHook: tapResult=" + tapResult);
                        }
                        
                        if (tapResult != DozeTapManager.TapResult.IGNORED) {
                            log("DozeTapShadeHook: Pulsing tap consumed");
                            param.setResult(true);
                        }

                    } catch (Throwable t) {
                        log("DozeTapShadeHook: Error in PulsingGestureListener: " + t.getMessage());
                    }
                }
            });

            log("DozeTapShadeHook: Hook applied successfully");
        } catch (Throwable e) {
            log("DozeTapShadeHook: Failed to apply hook: " + e.getMessage());
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