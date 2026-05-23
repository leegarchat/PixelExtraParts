package org.pixel.customparts.addon.systemuishade;

import android.content.Context;
import android.graphics.Rect;
import android.provider.Settings;
import android.util.Log;
import android.view.View;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

final class NotificationFirstShadeHook {
    private static final String TAG = "NotifFirstShadeHook";
    private static final String KEY_NOTIFICATION_FIRST_SHADE = "systemui_notification_first_shade";
    private static final String PINE_SUFFIX = "_pine";

    // Стабильный класс из Compose-шторки Android 16 QPR1+
    private static final String QS_FRAGMENT_COMPOSE = "com.android.systemui.qs.composefragment.QSFragmentCompose";
    private static final String SINGLE_SHADE_MEASURE_POLICY = "com.android.systemui.shade.ui.composable.SingleShadeMeasurePolicy";
    
    // Legacy классы для старой шторки
    private static final String QUICK_STATUS_BAR_HEADER = "com.android.systemui.qs.QuickStatusBarHeader";

    private static volatile Context sContext;
    private static volatile float sQsExpansionFraction = 0f;

    private NotificationFirstShadeHook() {
    }

    static void init(Context context, ClassLoader classLoader) {
        sContext = context;

        hookQSFragmentCompose(classLoader);
        hookComposeRenderPipeline(classLoader);
        hookNotificationsTop(classLoader);
        hookLegacyShade(classLoader);
        Log.d(TAG, "NotificationFirstShadeHook initialized: QS disabled in notification shade");
    }

    /**
     * Контролируем видимость QS в Compose-режиме (Android 16 QPR1+)
     */
    private static void hookQSFragmentCompose(ClassLoader classLoader) {
        try {
            Class<?> qsFragmentComposeClass = XposedHelpers.findClassIfExists(QS_FRAGMENT_COMPOSE, classLoader);
            if (qsFragmentComposeClass == null) {
                Log.w(TAG, "QSFragmentCompose not found!");
                return;
            }

            // 1. Устанавливаем минимальную высоту QS (в свернутом виде) строго в 0
            XposedBridge.hookAllMethods(qsFragmentComposeClass, "getQsMinHeight", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (isEnabledNow()) {
                        param.setResult(0); // Сплющиваем QQS, чтобы он не занимал места
                    }
                }
            });

            // 2. Отслеживаем раскрытие и физически обрезаем View, если шторка свернута (скрывает остаточные отступы)
            XposedBridge.hookAllMethods(qsFragmentComposeClass, "setQsExpansion", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (param.args != null && param.args.length > 0 && param.args[0] instanceof Float) {
                        sQsExpansionFraction = (Float) param.args[0];
                    }

                    if (!isEnabledNow()) return;

                    try {
                        View view = (View) XposedHelpers.callMethod(param.thisObject, "getView");
                        if (view != null) {
                            if (sQsExpansionFraction <= 0.01f) { 
                                // Шторка свернута: полностью обрезаем отрисовку
                                view.setClipBounds(new Rect(0, 0, Integer.MAX_VALUE, 0));
                            } else {
                                // Шторка разворачивается: восстанавливаем область
                                view.setClipBounds(null);
                            }
                        }
                    } catch (Throwable ignored) {}
                }
            });
            Log.d(TAG, "Hooked QSFragmentCompose successfully");
        } catch (Throwable t) {
            Log.e(TAG, "Failed to hook QSFragmentCompose", t);
        }
    }

    /**
     * Прямой перехват Compose-компонентов отрисовки плиток.
     * Заменяем список плиток на пустой, чтобы Compose нативно сжал высоту рендер-области до нуля,
     * убирая "зарезервированное" пустое пространство.
     */
    private static void hookComposeRenderPipeline(ClassLoader classLoader) {
        String[] composeClasses = {
            "com.android.systemui.qs.panels.ui.compose.QuickQuickSettingsKt",
            "com.android.systemui.qs.panels.ui.compose.TileGridKt",
            "com.android.systemui.qs.panels.ui.compose.GridLayoutKt",
            "com.android.systemui.qs.panels.ui.compose.DefaultGridLayoutKt",
            "com.android.systemui.qs.panels.ui.compose.PartitionedGridLayoutKt"
        };

        for (String className : composeClasses) {
            try {
                Class<?> clazz = XposedHelpers.findClassIfExists(className, classLoader);
                if (clazz == null) continue;

                for (final Method method : clazz.getDeclaredMethods()) {
                    if (method == null) continue;

                    try {
                        XposedBridge.hookMethod(method, new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) {
                                if (!isEnabledNow() || param.args == null) return;

                                // Когда шторка свернута (QQS), полностью очищаем список плиток.
                                // Без param.setResult(null), функция выполнится и сохранит якоря для анимации,
                                // но отрисует 0 элементов, физически схлопывая высоту.
                                if (sQsExpansionFraction <= 0.01f) {
                                    for (int i = 0; i < param.args.length; i++) {
                                        Object arg = param.args[i];
                                        if (arg instanceof List) {
                                            List<?> list = (List<?>) arg;
                                            if (isTileList(list)) {
                                                param.args[i] = Collections.emptyList();
                                            }
                                        }
                                    }
                                }
                            }
                        });
                    } catch (Throwable ignored) {}
                }
                Log.d(TAG, "Compose render interceptors applied to " + className);
            } catch (Throwable t) {
                Log.e(TAG, "Failed to hook Compose class " + className, t);
            }
        }
    }

    private static boolean isTileList(List<?> list) {
        if (list == null || list.isEmpty()) return false;
        Object first = list.get(0);
        if (first == null) return false;
        String name = first.getClass().getName();
        return name.contains("Tile") 
                || name.contains("ComponentState") 
                || name.contains("TileSpec") 
                || name.contains("TileUiState")
                || name.contains("QSTile");
    }

    /**
     * Подтягиваем уведомления к самому верху, скрывая пустую зону
     */
    private static void hookNotificationsTop(ClassLoader classLoader) {
        try {
            Class<?> measurePolicyClass = XposedHelpers.findClassIfExists(SINGLE_SHADE_MEASURE_POLICY, classLoader);
            if (measurePolicyClass == null) {
                measurePolicyClass = XposedHelpers.findClassIfExists("com.android.systemui.shade.ui.composable.SingleShadeKt$SingleShadeMeasurePolicy", classLoader);
            }
            if (measurePolicyClass == null) {
                measurePolicyClass = XposedHelpers.findClassIfExists("com.android.systemui.shade.ui.composable.SingleShadeKt", classLoader);
            }

            if (measurePolicyClass == null) return;

            XposedBridge.hookAllMethods(measurePolicyClass, "calculateNotificationsTop", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (!isEnabledNow() || param.args == null || param.args.length < 3) return;
                    
                    int headerHeight = getPlaceableHeight(param.args[0]);
                    int insetsTop = param.args[2] instanceof Integer ? (Integer) param.args[2] : 0;
                    
                    // Жестко притягиваем уведомления к статус-бару
                    param.setResult(insetsTop + headerHeight);
                }
            });
            Log.d(TAG, "SingleShadeMeasurePolicy hook installed");
        } catch (Throwable t) {
            Log.e(TAG, "Failed to hook scene notifications top", t);
        }
    }

    /**
     * Отключаем панель в Legacy-шторке
     */
    private static void hookLegacyShade(ClassLoader classLoader) {
        try {
            Class<?> headerClass = XposedHelpers.findClassIfExists(QUICK_STATUS_BAR_HEADER, classLoader);
            if (headerClass == null) return;

            XC_MethodHook visibilityHook = new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    applyLegacyQuickQsVisibility(param.thisObject);
                }
            };

            XposedBridge.hookAllMethods(headerClass, "onFinishInflate", visibilityHook);
            XposedBridge.hookAllMethods(headerClass, "updateResources", visibilityHook);
            XposedBridge.hookAllMethods(headerClass, "setExpanded", visibilityHook);

            Log.d(TAG, "QuickStatusBarHeader legacy hooks installed");
        } catch (Throwable t) {
            Log.e(TAG, "Failed to hook legacy QuickStatusBarHeader", t);
        }
    }

    private static void applyLegacyQuickQsVisibility(Object header) {
        if (header == null) return;
        try {
            boolean expanded = isLegacyHeaderExpanded(header);
            Object panel = XposedHelpers.getObjectField(header, "mHeaderQsPanel");
            if (panel instanceof View) {
                ((View) panel).setVisibility(isEnabledNow() && !expanded ? View.GONE : View.VISIBLE);
            }
        } catch (Throwable ignored) {}
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
        if (context == null) return false;
        try {
            return Settings.Global.getInt(
                    context.getContentResolver(),
                    resolveKey(KEY_NOTIFICATION_FIRST_SHADE),
                    0) != 0;
        } catch (Throwable ignored) {
            return false;
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