package org.pixel.customparts.hooks.systemui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.widget.ImageView;

import org.pixel.customparts.core.BaseHook;

import java.util.ArrayList;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;

public class AodNotificationIconColorHook extends BaseHook {
    private static final String KEY_AOD_FULL_COLOR_ICONS = "aod_full_color_notification_icons";
    private static final float AOD_DOZE_AMOUNT = 0.99f;

    @Override
    public String getHookId() {
        return "AodNotificationIconColorHook";
    }

    @Override
    public int getPriority() {
        return 63;
    }

    @Override
    protected void onInit(ClassLoader classLoader) {
        try {
            Class<?> statusBarIconViewClass = XposedHelpers.findClass(
                    "com.android.systemui.statusbar.StatusBarIconView",
                    classLoader);

            XposedHelpers.findAndHookMethod(
                    statusBarIconViewClass,
                    "updateIconColor",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            clearAodTintIfNeeded(param.thisObject);
                        }
                    });

            XposedHelpers.findAndHookMethod(
                    statusBarIconViewClass,
                    "setStaticDrawableColor",
                    int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            clearAodTintIfNeeded(param.thisObject);
                        }
                    });

            XposedHelpers.findAndHookMethod(
                    statusBarIconViewClass,
                    "setTintAlpha",
                    float.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            clearAodTintIfNeeded(param.thisObject);
                        }
                    });

            XposedHelpers.findAndHookMethod(
                    statusBarIconViewClass,
                    "onDarkChanged",
                    ArrayList.class,
                    float.class,
                    int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            clearAodTintIfNeeded(param.thisObject);
                        }
                    });

            Class<?> notificationDozeHelperClass = XposedHelpers.findClass(
                    "com.android.systemui.statusbar.notification.NotificationDozeHelper",
                    classLoader);
            XposedHelpers.findAndHookMethod(
                    notificationDozeHelperClass,
                    "updateGrayscale",
                    ImageView.class,
                    float.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            ImageView target = (ImageView) param.args[0];
                            float darkAmount = (Float) param.args[1];
                            if (shouldKeepFullColor(target, darkAmount)) {
                                clearIconTint(target);
                                param.setResult(null);
                            }
                        }
                    });

            log("Hooked full color AOD notification icons");
        } catch (Throwable t) {
            logError("Unable to hook AOD notification icon colors", t);
        }
    }

    private void clearAodTintIfNeeded(Object iconView) {
        if (!(iconView instanceof ImageView) || !isNotificationIcon(iconView) || getDozeAmount(iconView) < AOD_DOZE_AMOUNT) {
            return;
        }
        Context context = ((ImageView) iconView).getContext();
        if (!isSettingEnabled(context, KEY_AOD_FULL_COLOR_ICONS, false)) {
            return;
        }
        clearIconTint((ImageView) iconView);
    }

    private static void clearIconTint(ImageView imageView) {
        imageView.setColorFilter(null);
        imageView.setImageTintList((ColorStateList) null);
        imageView.setImageTintMode(null);
        Drawable drawable = imageView.getDrawable();
        if (drawable != null) {
            drawable.clearColorFilter();
            drawable.setTintList(null);
        }
        imageView.invalidate();
    }

    private boolean shouldKeepFullColor(ImageView target, float darkAmount) {
        if (target == null || darkAmount <= 0f || !isNotificationIcon(target)) {
            return false;
        }
        return isSettingEnabled(target.getContext(), KEY_AOD_FULL_COLOR_ICONS, false);
    }

    private static boolean isNotificationIcon(Object iconView) {
        try {
            return XposedHelpers.getObjectField(iconView, "mNotification") != null;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static float getDozeAmount(Object iconView) {
        try {
            return XposedHelpers.getFloatField(iconView, "mDozeAmount");
        } catch (Throwable ignored) {
            return 0f;
        }
    }
}