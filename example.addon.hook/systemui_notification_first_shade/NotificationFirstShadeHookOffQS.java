package org.pixel.customparts.addon.systemuishade;

import android.content.Context;
import android.provider.Settings;
import android.util.Log;
import android.view.View;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

final class NotificationFirstShadeHook {
    private static final String TAG = "NotifFirstShadeHook";
    private static final String KEY_NOTIFICATION_FIRST_SHADE = "systemui_notification_first_shade";
    private static final String PINE_SUFFIX = "_pine";

        private static final String QUICK_QUICK_SETTINGS_COMPOSABLE =
            "com.android.systemui.qs.panels.ui.compose.QuickQuickSettingsKt";
        private static final String SINGLE_SHADE_MEASURE_POLICY =
            "com.android.systemui.shade.ui.composable.SingleShadeMeasurePolicy";
        private static final String QUICK_STATUS_BAR_HEADER =
            "com.android.systemui.qs.QuickStatusBarHeader";

    private static volatile Context sContext;
    private static volatile boolean sEnabledAtInit;

    private NotificationFirstShadeHook() {
    }

    static void init(Context context, ClassLoader classLoader) {
        sContext = context;
        sEnabledAtInit = isEnabledNow();
        if (!sEnabledAtInit) {
            Log.d(TAG, "skipped: disabled");
            return;
        }

        hookSceneContainerShade(classLoader);
        hookLegacyShade(classLoader);
        Log.d(TAG, "notification-first shade hooks installed");
    }

    private static void hookSceneContainerShade(ClassLoader classLoader) {
        try {
            Class<?> quickQuickSettingsClass = XposedHelpers.findClassIfExists(
                    QUICK_QUICK_SETTINGS_COMPOSABLE,
                    classLoader);
            if (quickQuickSettingsClass != null) {
                XposedBridge.hookAllMethods(quickQuickSettingsClass, "QuickQuickSettings", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (isEnabledNow()) {
                            param.setResult(null);
                        }
                    }
                });
                Log.d(TAG, "QuickQuickSettings composable hooks installed");
            }
        } catch (Throwable t) {
            Log.e(TAG, "Failed to hook scene QQS composable", t);
        }

        try {
            Class<?> measurePolicyClass = XposedHelpers.findClassIfExists(
                    SINGLE_SHADE_MEASURE_POLICY,
                    classLoader);
            if (measurePolicyClass == null) return;
            XposedBridge.hookAllMethods(measurePolicyClass, "calculateNotificationsTop", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (!isEnabledNow() || param.args == null || param.args.length < 3) return;
                    int headerHeight = getPlaceableHeight(param.args[0]);
                    int mediaAndQqsHeight = getPlaceableHeight(param.args[1]);
                    int insetsTop = param.args[2] instanceof Integer ? (Integer) param.args[2] : 0;
                    int emptyQqsThreshold = Math.max(headerHeight, 96);
                    int top = insetsTop + headerHeight;
                    if (mediaAndQqsHeight > emptyQqsThreshold) {
                        top += mediaAndQqsHeight;
                    }
                    param.setResult(top);
                }
            });
            Log.d(TAG, "SingleShadeMeasurePolicy hooks installed");
        } catch (Throwable t) {
            Log.e(TAG, "Failed to hook scene notifications top", t);
        }
    }

    private static void hookLegacyShade(ClassLoader classLoader) {
        try {
            Class<?> headerClass = XposedHelpers.findClassIfExists(QUICK_STATUS_BAR_HEADER, classLoader);
            if (headerClass == null) return;

            XposedBridge.hookAllMethods(headerClass, "onFinishInflate", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    applyLegacyQuickQsVisibility(param.thisObject, false);
                }
            });

            XposedBridge.hookAllMethods(headerClass, "updateResources", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    applyLegacyQuickQsVisibility(param.thisObject, isLegacyHeaderExpanded(param.thisObject));
                }
            });

            XposedBridge.hookAllMethods(headerClass, "setExpanded", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    boolean expanded = param.args != null
                            && param.args.length > 0
                            && param.args[0] instanceof Boolean
                            && (Boolean) param.args[0];
                    applyLegacyQuickQsVisibility(param.thisObject, expanded);
                }
            });

            Log.d(TAG, "QuickStatusBarHeader hooks installed");
        } catch (Throwable t) {
            Log.e(TAG, "Failed to hook legacy QuickStatusBarHeader", t);
        }
    }

    private static void applyLegacyQuickQsVisibility(Object header, boolean expanded) {
        if (header == null) return;
        try {
            Object panel = XposedHelpers.getObjectField(header, "mHeaderQsPanel");
            if (panel instanceof View) {
                ((View) panel).setVisibility(isEnabledNow() && !expanded ? View.GONE : View.VISIBLE);
            }
        } catch (Throwable ignored) {
        }
    }

    private static boolean isLegacyHeaderExpanded(Object header) {
        try {
            return XposedHelpers.getBooleanField(header, "mExpanded");
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static int getPlaceableHeight(Object placeable) {
        if (placeable == null) return 0;
        try {
            Object height = XposedHelpers.callMethod(placeable, "getHeight");
            return height instanceof Integer ? (Integer) height : 0;
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static boolean isEnabledNow() {
        Context context = currentContext();
        if (context == null) return sEnabledAtInit;
        try {
            return Settings.Global.getInt(
                    context.getContentResolver(),
                    resolveKey(KEY_NOTIFICATION_FIRST_SHADE),
                    sEnabledAtInit ? 1 : 0) != 0;
        } catch (Throwable ignored) {
            return sEnabledAtInit;
        }
    }

    private static String resolveKey(String key) {
        if (key == null || key.endsWith(PINE_SUFFIX)) return key;
        return key + PINE_SUFFIX;
    }

    private static Context currentContext() {
        try {
            Class<?> activityThreadClass = XposedHelpers.findClass("android.app.ActivityThread", null);
            Object app = XposedHelpers.callStaticMethod(activityThreadClass, "currentApplication");
            if (app instanceof Context) return (Context) app;
        } catch (Throwable ignored) {
        }
        return sContext;
    }
}