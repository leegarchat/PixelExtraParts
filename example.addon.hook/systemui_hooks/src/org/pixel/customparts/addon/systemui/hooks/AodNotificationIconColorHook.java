package org.pixel.customparts.addon.systemui.hooks;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.drawable.Drawable;
import android.service.notification.StatusBarNotification;
import android.widget.ImageView;


import java.util.ArrayList;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class AodNotificationIconColorHook extends BaseSystemUIHook {
    private static final String KEY_AOD_FULL_COLOR_ICONS = "aod_full_color_notification_icons";
    private static final String KEY_STATUS_BAR_USE_APP_ICONS = "status_bar_use_app_icons";
    private static final String KEY_STATUS_BAR_MONOCHROME_ICONS = "status_bar_monochrome_notification_icons";
    private static final String KEY_AOD_USE_APP_ICONS = "aod_use_app_icons";
    private static final String KEY_AOD_MONOCHROME_ICONS = "aod_monochrome_notification_icons";
    private static final String EXTRA_IS_NOTIFICATION_ICON = "pixelparts_aod_is_notification_icon";
    private static final String EXTRA_ICON_MODE_APPLIED = "pixelparts_notification_icon_mode_applied";
    private static final String EXTRA_RESTORING_ICON_MODE = "pixelparts_notification_icon_mode_restoring";

    // Every notification icon view ever seen (weak: views die with their notifications).
    // Lets setting flips re-apply to existing icons at runtime, no restart needed.
    private static final Map<Object, Boolean> sSeenIcons =
            Collections.synchronizedMap(new WeakHashMap<Object, Boolean>());

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

            int iconStyleHooks = hookAodIconStyleMethods(classLoader, statusBarIconViewClass);
            int drawableHooks = hookAodDrawableLoaders(classLoader, statusBarIconViewClass);
            int bindHooks = hookNotificationBindPath(classLoader, statusBarIconViewClass);
            int updateColorHooks = hookStatusBarIconViewUpdateIconColor(statusBarIconViewClass);
            int darkChangedHooks = hookStatusBarIconViewOnDarkChanged(statusBarIconViewClass);

            log("Hooked notification app icon colors: iconStyleHooks=" + iconStyleHooks
                    + " drawableHooks=" + drawableHooks
                    + " bindHooks=" + bindHooks
                    + " updateColorHooks=" + updateColorHooks
                    + " darkChangedHooks=" + darkChangedHooks);
        } catch (Throwable t) {
            logHookWarning("Unable to hook notification app icon colors", t);
        }
    }

    private int hookAodIconStyleMethods(ClassLoader classLoader, Class<?> statusBarIconViewClass) {
        int count = 0;
        count += hookStatusBarIconViewSetIconStyle(statusBarIconViewClass);
        count += hookIconManagerSetIcon(classLoader, statusBarIconViewClass);
        return count;
    }

    private int hookStatusBarIconViewSetIconStyle(Class<?> statusBarIconViewClass) {
        try {
            Method method = statusBarIconViewClass.getDeclaredMethod("setIconStyle", Boolean.TYPE);
            return hookMethod(method, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (shouldUseAppIcon(param.thisObject)) {
                        param.args[0] = Boolean.TRUE;
                        markIconModeApplied(param.thisObject);
                    }
                }
            });
        } catch (NoSuchMethodException ignored) {
            // setIconStyle(boolean) removed in Android 16+; icon mode is handled via IconManager hooks
            return 0;
        } catch (Throwable t) {
            logHookWarning("StatusBarIconView.setIconStyle hook unavailable", t);
            return 0;
        }
    }

    private int hookIconManagerSetIcon(ClassLoader classLoader, Class<?> statusBarIconViewClass) {
        // Current tree (A16): setIcon(entry, descriptor, view), overloads for
        // NotificationEntry and BundleEntry. The old 6-arg
        // (entry, descriptor, view, boolean, boolean, boolean) overload this
        // scan was written for is gone, so match the live signature instead.
        int count = 0;
        try {
            Class<?> iconManagerClass = XposedHelpers.findClass(
                    "com.android.systemui.statusbar.notification.icon.IconManager",
                    classLoader);
            Class<?> statusBarIconClass;
            try {
                statusBarIconClass = XposedHelpers.findClass(
                        "com.android.internal.statusbar.StatusBarIcon", classLoader);
            } catch (Throwable t) {
                logHookWarning("StatusBarIcon class unavailable for IconManager hook", t);
                return 0;
            }
            for (Method method : iconManagerClass.getDeclaredMethods()) {
                Class<?>[] parameterTypes = method.getParameterTypes();
                if (!Void.TYPE.equals(method.getReturnType())
                        || parameterTypes.length != 3
                        || !statusBarIconClass.isAssignableFrom(parameterTypes[1])
                        || !statusBarIconViewClass.isAssignableFrom(parameterTypes[2])) {
                    continue;
                }
                count += hookMethod(method, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        Object iconView = param.args[2];
                        if (shouldUseAppIcon(iconView)) {
                            markIconModeApplied(iconView);
                            forceNotificationIconMode(iconView, true);
                        }
                    }
                });
            }
            if (count == 0) {
                log("IconManager.setIcon: no 3-arg overload matched");
            }
            return count;
        } catch (Throwable t) {
            logHookWarning("IconManager notification setIcon hook unavailable", t);
            return 0;
        }
    }

    /**
     * Deterministic bind path (current tree): IconManager.setIcon() ->
     * StatusBarIconView.set() -> updateDrawable(false) -> getIcon().
     * Hooking set() covers the initial bind, so app icons apply immediately
     * instead of only after a later updateIconColor/onDarkChanged.
     */
    private int hookNotificationBindPath(ClassLoader classLoader, Class<?> statusBarIconViewClass) {
        int count = 0;
        Class<?> statusBarIconClass;
        try {
            statusBarIconClass = XposedHelpers.findClass(
                    "com.android.internal.statusbar.StatusBarIcon", classLoader);
        } catch (Throwable t) {
            logHookWarning("StatusBarIcon class unavailable for bind hook", t);
            return 0;
        }
        try {
            Method setMethod = statusBarIconViewClass.getDeclaredMethod("set", statusBarIconClass);
            count += hookMethod(setMethod, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    Object iconView = param.thisObject;
                    if (shouldUseAppIcon(iconView)) {
                        markIconModeApplied(iconView);
                        forceNotificationIconMode(iconView, true);
                    }
                }
            });
        } catch (Throwable t) {
            logHookWarning("StatusBarIconView.set hook unavailable", t);
        }
        return count;
    }

    private int hookAodDrawableLoaders(ClassLoader classLoader, Class<?> statusBarIconViewClass) {
        // Funnel for every bind: StatusBarIconView.set() -> updateDrawable()
        // -> getIcon(StatusBarIcon) [-> getIcon(Context, Context, StatusBarIcon)].
        // Swapping the drawable at the source: no monochrome flicker, immediate.
        int count = 0;
        try {
            java.util.Set<XC_MethodHook.Unhook> unhooks = XposedBridge.hookAllMethods(
                    statusBarIconViewClass, "getIcon", new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            try {
                                if (!shouldUseAppIcon(param.thisObject)) {
                                    return;
                                }
                                Context context = null;
                                Object statusBarIcon = null;
                                if (param.args != null) {
                                    for (Object arg : param.args) {
                                        if (context == null && arg instanceof Context) {
                                            context = (Context) arg;
                                        } else if (statusBarIcon == null
                                                && arg != null
                                                && "com.android.internal.statusbar.StatusBarIcon"
                                                        .equals(arg.getClass().getName())) {
                                            statusBarIcon = arg;
                                        }
                                    }
                                }
                                if (context == null) {
                                    context = ((ImageView) param.thisObject).getContext();
                                }
                                Drawable appIcon = loadApplicationIcon(context, statusBarIcon);
                                if (appIcon != null) {
                                    markIconModeApplied(param.thisObject);
                                    param.setResult(appIcon);
                                }
                            } catch (Throwable t) {
                                logHookWarning("getIcon app-icon swap failed", t);
                            }
                        }
                    });
            count += (unhooks == null ? 0 : unhooks.size());
        } catch (Throwable t) {
            logHookWarning("StatusBarIconView.getIcon hook unavailable", t);
        }
        return count;
    }

    private int hookStatusBarIconViewUpdateIconColor(Class<?> statusBarIconViewClass) {
        try {
            Method method = statusBarIconViewClass.getDeclaredMethod("updateIconColor");
            return hookMethod(method, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (!isRestoringIconMode(param.thisObject)) {
                        forceNotificationIconMode(param.thisObject, true);
                    }
                }
            });
        } catch (Throwable t) {
            log("StatusBarIconView.updateIconColor not available");
            return 0;
        }
    }

    private int hookStatusBarIconViewOnDarkChanged(Class<?> statusBarIconViewClass) {
        try {
            Method method = statusBarIconViewClass.getDeclaredMethod(
                    "onDarkChanged", ArrayList.class, Float.TYPE, Integer.TYPE);
            return hookMethod(method, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (!isRestoringIconMode(param.thisObject)) {
                        forceNotificationIconMode(param.thisObject, true);
                    }
                }
            });
        } catch (Throwable t) {
            log("StatusBarIconView.onDarkChanged not available");
            return 0;
        }
    }

    private int hookMethod(Method method, XC_MethodHook hook) {
        try {
            XposedBridge.hookMethod(method, hook);
            return 1;
        } catch (Throwable t) {
            logHookWarning("Unable to hook method by signature: " + method, t);
            return 0;
        }
    }

    private void logHookWarning(String message, Throwable throwable) {
        log(message + ": " + throwableSummary(throwable));
    }

    private static String throwableSummary(Throwable throwable) {
        if (throwable == null) {
            return "unknown";
        }
        String detail = throwable.getMessage();
        String name = throwable.getClass().getSimpleName();
        return detail == null || detail.isEmpty() ? name : name + ": " + detail;
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

    private void applyIconColorMode(ImageView iconView, boolean useGrayscale) {
        if (useGrayscale) {
            applyGrayscaleIconTint(iconView);
        } else {
            clearIconTint(iconView);
        }
    }

    private static void applyGrayscaleIconTint(ImageView imageView) {
        ColorMatrix matrix = new ColorMatrix();
        matrix.setSaturation(0f);
        ColorMatrixColorFilter filter = new ColorMatrixColorFilter(matrix);
        imageView.setImageTintList((ColorStateList) null);
        imageView.setImageTintMode(null);
        imageView.setColorFilter(filter);
        Drawable drawable = imageView.getDrawable();
        if (drawable != null) {
            drawable.setTintList(null);
            drawable.setColorFilter(filter);
        }
        imageView.invalidate();
    }

    private boolean shouldHandleNotificationIcon(Object iconView, boolean colorUpdateEvidence) {
        if (!(iconView instanceof ImageView)) {
            return false;
        }
        if (!isNotificationIcon(iconView)) {
            return false;
        }
        boolean aodIcon = isAodIcon(iconView);
        return aodIcon || colorUpdateEvidence;
    }

    private boolean shouldUseAppIcon(Object iconView) {
        if (!(iconView instanceof ImageView) || !isNotificationIcon(iconView)) {
            return false;
        }
        trackIconView(iconView);
        Context context = ((ImageView) iconView).getContext();
        observeIconKeysOnce(context);
        return shouldUseAppIconSetting(context, shouldUseAodSettings(iconView));
    }

    private void trackIconView(Object iconView) {
        if (iconView == null) {
            return;
        }
        try {
            sSeenIcons.put(iconView, Boolean.TRUE);
        } catch (Throwable ignored) {
        }
    }

    // Runtime updates without SystemUI restart: any icon-mode key change
    // re-applies (or restores) the mode on all live notification icons.
    private void observeIconKeysOnce(Context context) {
        String[] keys = {
                KEY_AOD_FULL_COLOR_ICONS,
                KEY_AOD_USE_APP_ICONS,
                KEY_AOD_MONOCHROME_ICONS,
                KEY_STATUS_BAR_USE_APP_ICONS,
                KEY_STATUS_BAR_MONOCHROME_ICONS,
        };
        for (String key : keys) {
            observeSettingOnce(context, key, new Runnable() {
                @Override
                public void run() {
                    reapplyAllIconModes();
                }
            });
        }
    }

    private void reapplyAllIconModes() {
        java.util.List<Object> snapshot;
        synchronized (sSeenIcons) {
            snapshot = new ArrayList<Object>(sSeenIcons.keySet());
        }
        for (Object iconView : snapshot) {
            if (iconView == null) {
                continue;
            }
            try {
                forceNotificationIconMode(iconView, true);
            } catch (Throwable t) {
                logHookWarning("re-apply icon mode failed", t);
            }
        }
    }

    private boolean shouldUseAppIconSetting(Context context, boolean useAodSettings) {
        boolean legacyDefault = useAodSettings
                && isSettingEnabled(context, KEY_AOD_FULL_COLOR_ICONS, false);
        return isSettingEnabled(context,
                useAodSettings ? KEY_AOD_USE_APP_ICONS : KEY_STATUS_BAR_USE_APP_ICONS,
                legacyDefault);
    }

    private boolean shouldUseGrayscaleSetting(Context context, boolean useAodSettings) {
        return isSettingEnabled(context,
                useAodSettings ? KEY_AOD_MONOCHROME_ICONS : KEY_STATUS_BAR_MONOCHROME_ICONS,
                false);
    }

    private void forceNotificationIconMode(Object iconView, boolean colorUpdateEvidence) {
        if (!shouldHandleNotificationIcon(iconView, colorUpdateEvidence)) {
            return;
        }
        ImageView imageView = (ImageView) iconView;
        Context context = imageView.getContext();
        boolean useAodSettings = shouldUseAodSettings(iconView);
        boolean useAppIcon = shouldUseAppIconSetting(context, useAodSettings);
        if (!useAppIcon) {
            restoreSystemIconModeIfNeeded(imageView);
            return;
        }
        boolean useGrayscale = shouldUseGrayscaleSetting(context, useAodSettings);
        callIgnored(iconView, "setIconStyle", Boolean.TRUE);
        callIgnored(iconView, "updateDrawable");
        applyApplicationIcon(imageView);
        markIconModeApplied(imageView);
        applyIconColorMode(imageView, useGrayscale);
    }

    private void restoreSystemIconModeIfNeeded(ImageView iconView) {
        if (!Boolean.TRUE.equals(XposedHelpers.getAdditionalInstanceField(iconView, EXTRA_ICON_MODE_APPLIED))) {
            return;
        }
        XposedHelpers.setAdditionalInstanceField(iconView, EXTRA_RESTORING_ICON_MODE, Boolean.TRUE);
        try {
            callIgnored(iconView, "setIconStyle", Boolean.FALSE);
            callIgnored(iconView, "updateDrawable");
            callIgnored(iconView, "updateIconColor");
        } finally {
            XposedHelpers.setAdditionalInstanceField(iconView, EXTRA_ICON_MODE_APPLIED, null);
            XposedHelpers.setAdditionalInstanceField(iconView, EXTRA_RESTORING_ICON_MODE, null);
        }
    }

    private void markIconModeApplied(Object iconView) {
        if (iconView instanceof ImageView) {
            XposedHelpers.setAdditionalInstanceField(iconView, EXTRA_ICON_MODE_APPLIED, Boolean.TRUE);
        }
    }

    private boolean isRestoringIconMode(Object iconView) {
        return Boolean.TRUE.equals(XposedHelpers.getAdditionalInstanceField(iconView, EXTRA_RESTORING_ICON_MODE));
    }

    private void callIgnored(Object object, String methodName, Object... args) {
        try {
            XposedHelpers.callMethod(object, methodName, args);
        } catch (Throwable ignored) {
        }
    }

    private boolean shouldUseAodSettings(Object iconView) {
        return isAodIcon(iconView);
    }

    private void applyApplicationIcon(ImageView iconView) {
        Drawable appIcon = loadApplicationIcon(iconView.getContext(), findStatusBarIcon(iconView));
        if (appIcon != null) {
            iconView.setImageDrawable(appIcon);
        }
    }

    private static boolean isNotificationIcon(Object iconView) {
        Object tagged = XposedHelpers.getAdditionalInstanceField(iconView, EXTRA_IS_NOTIFICATION_ICON);
        if (Boolean.TRUE.equals(tagged)) {
            return true;
        }
        if (hasStatusBarNotificationField(iconView) || isNotificationStatusBarIcon(findStatusBarIcon(iconView))) {
            XposedHelpers.setAdditionalInstanceField(iconView, EXTRA_IS_NOTIFICATION_ICON, Boolean.TRUE);
            return true;
        }
        return false;
    }

    private static boolean isAodIcon(Object iconView) {
        return isIncreasedSizeAodIcon(iconView);
    }

    private static boolean isIncreasedSizeAodIcon(Object iconView) {
        if (!(iconView instanceof ImageView)) {
            return false;
        }
        try {
            return XposedHelpers.getBooleanField(iconView, "mIncreasedSize");
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static Drawable loadApplicationIcon(Context context, Object statusBarIcon) {
        if (context == null || statusBarIcon == null) {
            return null;
        }
        try {
            Object packageNameValue = XposedHelpers.getObjectField(statusBarIcon, "pkg");
            if (!(packageNameValue instanceof String)) {
                return null;
            }
            String packageName = (String) packageNameValue;
            if (packageName.contains("systemui")) {
                return null;
            }
            Drawable drawable = context.getPackageManager().getApplicationIcon(packageName);
            if (drawable == null) {
                return null;
            }
            drawable = drawable.mutate();
            drawable.clearColorFilter();
            drawable.setTintList(null);
            return drawable;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object findStatusBarIcon(Object iconView) {
        if (iconView == null) {
            return null;
        }
        try {
            Object statusBarIcon = XposedHelpers.callMethod(iconView, "getStatusBarIcon");
            if (statusBarIcon != null) {
                return statusBarIcon;
            }
        } catch (Throwable ignored) {
        }
        Class<?> currentClass = iconView.getClass();
        while (currentClass != null && currentClass != Object.class) {
            for (Field field : currentClass.getDeclaredFields()) {
                if (!"com.android.internal.statusbar.StatusBarIcon".equals(field.getType().getName())) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    Object statusBarIcon = field.get(iconView);
                    if (statusBarIcon != null) {
                        return statusBarIcon;
                    }
                } catch (Throwable ignored) {
                }
            }
            currentClass = currentClass.getSuperclass();
        }
        return null;
    }

    private static boolean hasStatusBarNotificationField(Object iconView) {
        Class<?> currentClass = iconView != null ? iconView.getClass() : null;
        while (currentClass != null && currentClass != Object.class) {
            for (Field field : currentClass.getDeclaredFields()) {
                try {
                    field.setAccessible(true);
                    Object value = field.get(iconView);
                    if (value instanceof StatusBarNotification || isNotificationBackedEntry(value)) {
                        return true;
                    }
                } catch (Throwable ignored) {
                }
            }
            currentClass = currentClass.getSuperclass();
        }
        return false;
    }

    private static boolean isNotificationBackedEntry(Object value) {
        if (value == null) {
            return false;
        }
        String className = value.getClass().getName();
        return className.endsWith(".NotificationEntry") || className.endsWith(".BundleEntry");
    }

    private static boolean isNotificationStatusBarIcon(Object statusBarIcon) {
        if (statusBarIcon == null) {
            return false;
        }
        try {
            Object packageNameValue = XposedHelpers.getObjectField(statusBarIcon, "pkg");
            return packageNameValue instanceof String && !((String) packageNameValue).contains("systemui");
        } catch (Throwable ignored) {
            return false;
        }
    }

}
