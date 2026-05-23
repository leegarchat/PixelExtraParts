package org.pixel.customparts.addon.systemuishade;

import android.content.Context;
import android.provider.Settings;
import android.util.Log;
import android.view.View;

import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

final class NotificationFirstShadeHook {
    private static final String TAG = "NotifFirstShadeHook";
    private static final String KEY_NOTIFICATION_FIRST_SHADE = "systemui_notification_first_shade";
    private static final String PINE_SUFFIX = "_pine";

    private static final String SHADE_SCENE_CONTENT_VM =
            "com.android.systemui.shade.ui.viewmodel.ShadeSceneContentViewModel";
        private static final String SINGLE_SHADE_MEASURE_POLICY =
            "com.android.systemui.shade.ui.composable.SingleShadeMeasurePolicy";
    private static final String QUICK_SETTINGS_CONTROLLER =
            "com.android.systemui.shade.QuickSettingsControllerImpl";
    private static final String QS_FRAGMENT_LEGACY =
            "com.android.systemui.qs.QSFragmentLegacy";
    private static final String QS_FRAGMENT_COMPOSE =
            "com.android.systemui.qs.composefragment.QSFragmentCompose";

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
        hookQsFragments(classLoader);
        Log.d(TAG, "notification-first shade hooks installed");
    }

    private static void hookSceneContainerShade(ClassLoader classLoader) {
        try {
            Class<?> viewModelClass = XposedHelpers.findClassIfExists(SHADE_SCENE_CONTENT_VM, classLoader);
            if (viewModelClass == null) return;
            Set<XC_MethodHook.Unhook> hooks = XposedBridge.hookAllMethods(
                    viewModelClass,
                    "isQsEnabled",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            if (isEnabledNow() && isSingleShadeMode(param.thisObject)) {
                                param.setResult(false);
                            }
                        }
                    });
            Log.d(TAG, "ShadeSceneContentViewModel.isQsEnabled hooks="
                    + (hooks == null ? 0 : hooks.size()));
        } catch (Throwable t) {
            Log.e(TAG, "Failed to hook scene shade QQS", t);
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
                    int insetsTop = param.args[2] instanceof Integer ? (Integer) param.args[2] : 0;
                    param.setResult(insetsTop + headerHeight);
                }
            });
            Log.d(TAG, "SingleShadeMeasurePolicy hooks installed");
        } catch (Throwable t) {
            Log.e(TAG, "Failed to hook scene notifications top", t);
        }
    }

    private static void hookLegacyShade(ClassLoader classLoader) {
        try {
            Class<?> controllerClass = XposedHelpers.findClassIfExists(QUICK_SETTINGS_CONTROLLER, classLoader);
            if (controllerClass == null) return;

            XposedBridge.hookAllMethods(controllerClass, "updateMinHeight", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (shouldUseLegacyNotificationFirst(param.thisObject)) {
                        setIntFieldIfPresent(param.thisObject, "mMinExpansionHeight", 0);
                        clampExpansionToZeroWhenCollapsed(param.thisObject);
                    }
                }
            });

            XposedBridge.hookAllMethods(controllerClass, "getMinExpansionHeight", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (shouldUseLegacyNotificationFirst(param.thisObject)) {
                        param.setResult(0);
                    }
                }
            });

            XposedBridge.hookAllMethods(controllerClass, "applyClippingImmediately", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (param.args == null || param.args.length < 5) return;
                    if (!(param.args[4] instanceof Boolean)) return;
                    if ((Boolean) param.args[4] && shouldSuppressLegacyQqs(param.thisObject)) {
                        param.args[4] = false;
                    }
                }
            });

            Log.d(TAG, "QuickSettingsControllerImpl hooks installed");
        } catch (Throwable t) {
            Log.e(TAG, "Failed to hook legacy QuickSettingsController", t);
        }
    }

    private static void hookQsFragments(ClassLoader classLoader) {
        hookQsFragment(classLoader, QS_FRAGMENT_LEGACY);
        hookQsFragment(classLoader, QS_FRAGMENT_COMPOSE);
    }

    private static void hookQsFragment(ClassLoader classLoader, String className) {
        try {
            Class<?> qsClass = XposedHelpers.findClassIfExists(className, classLoader);
            if (qsClass == null) return;

            XposedBridge.hookAllMethods(qsClass, "setQsVisible", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (param.args == null || param.args.length < 1) return;
                    if (!(param.args[0] instanceof Boolean)) return;
                    if ((Boolean) param.args[0] && shouldSuppressFragmentQqs(param.thisObject)) {
                        param.args[0] = false;
                    }
                }

                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (param.args == null || param.args.length < 1) return;
                    if (!(param.args[0] instanceof Boolean)) return;
                    if ((Boolean) param.args[0] && !shouldSuppressFragmentQqs(param.thisObject)) {
                        showHeaderView(param.thisObject);
                    }
                }
            });

            XposedBridge.hookAllMethods(qsClass, "isHeaderShown", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (shouldSuppressFragmentQqs(param.thisObject)) {
                        param.setResult(false);
                    }
                }
            });

            XposedBridge.hookAllMethods(qsClass, "setQsExpansion", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (param.args == null || param.args.length < 1) return;
                    if (!(param.args[0] instanceof Float)) return;
                    float fraction = (Float) param.args[0];
                    if (fraction <= 0.01f && shouldSuppressFragmentQqs(param.thisObject)) {
                        hideHeaderView(param.thisObject);
                    } else if (fraction > 0.01f && isEnabledNow()) {
                        showHeaderView(param.thisObject);
                    }
                }
            });

            Log.d(TAG, className + " hooks installed");
        } catch (Throwable t) {
            Log.e(TAG, "Failed to hook " + className, t);
        }
    }

    private static boolean shouldUseLegacyNotificationFirst(Object controller) {
        if (!isEnabledNow() || controller == null) return false;
        try {
            return !XposedHelpers.getBooleanField(controller, "mSplitShadeEnabled");
        } catch (Throwable ignored) {
            return true;
        }
    }

    private static boolean shouldSuppressLegacyQqs(Object controller) {
        if (!shouldUseLegacyNotificationFirst(controller)) return false;
        try {
            Object fraction = XposedHelpers.callMethod(controller, "computeExpansionFraction");
            if (fraction instanceof Float && (Float) fraction > 0.01f) {
                return false;
            }
        } catch (Throwable ignored) {
        }
        try {
            return !XposedHelpers.getBooleanField(controller, "mFullyExpanded");
        } catch (Throwable ignored) {
            return true;
        }
    }

    private static boolean shouldSuppressFragmentQqs(Object qsFragment) {
        if (!isEnabledNow() || qsFragment == null) return false;
        try {
            Object collapsed = XposedHelpers.callMethod(qsFragment, "isFullyCollapsed");
            return !(collapsed instanceof Boolean) || (Boolean) collapsed;
        } catch (Throwable ignored) {
            return true;
        }
    }

    private static boolean isSingleShadeMode(Object viewModel) {
        if (viewModel == null) return true;
        try {
            Object shadeMode = XposedHelpers.callMethod(viewModel, "getShadeMode");
            String mode = String.valueOf(shadeMode);
            return mode.contains("Single");
        } catch (Throwable ignored) {
            return true;
        }
    }

    private static void hideHeaderView(Object qsFragment) {
        try {
            Object header = XposedHelpers.callMethod(qsFragment, "getHeader");
            if (header instanceof View) {
                View view = (View) header;
                view.setAlpha(0f);
                view.setVisibility(View.INVISIBLE);
            }
        } catch (Throwable ignored) {
        }
    }

    private static void showHeaderView(Object qsFragment) {
        try {
            Object header = XposedHelpers.callMethod(qsFragment, "getHeader");
            if (header instanceof View) {
                View view = (View) header;
                view.setVisibility(View.VISIBLE);
                view.setAlpha(1f);
            }
        } catch (Throwable ignored) {
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

    private static void clampExpansionToZeroWhenCollapsed(Object controller) {
        try {
            float expansionHeight = XposedHelpers.getFloatField(controller, "mExpansionHeight");
            if (expansionHeight <= 1f) {
                XposedHelpers.setFloatField(controller, "mExpansionHeight", 0f);
            }
        } catch (Throwable ignored) {
        }
    }

    private static void setIntFieldIfPresent(Object target, String fieldName, int value) {
        try {
            XposedHelpers.setIntField(target, fieldName, value);
        } catch (Throwable ignored) {
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