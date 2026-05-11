package org.pixel.customparts.hooks;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageItemInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.os.StrictMode;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.LruCache;
import android.view.View;

import org.json.JSONException;
import org.json.JSONObject;
import org.pixel.customparts.core.BaseHook;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Calendar;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;

public class LauncherIconOverrideHook extends BaseHook {
    private static final String KEY_APP_ICONS_ENABLED = "pixelparts_app_icons_enabled";
    private static final String KEY_APP_ICONS_LAUNCHER_ENABLED =
            "pixelparts_app_icons_launcher_enabled";
    private static final String KEY_APP_ICONS_LAUNCHER_STRETCH_SHAPE =
            "pixelparts_app_icons_launcher_stretch_shape";
    private static final String KEY_APP_ICONS_LAUNCHER_REMOVE_SHAPE =
            "pixelparts_app_icons_launcher_remove_shape";
    private static final String KEY_APP_ICONS_LAUNCHER_SHAPE_SCALE =
            "pixelparts_app_icons_launcher_shape_scale";
    private static final String KEY_APP_ICONS_SHAPE_BACKGROUND_TINT_MODE =
            "pixelparts_app_icons_shape_background_tint_mode";
    private static final String KEY_APP_ICONS_SHAPE_BACKGROUND_TINT_COLOR =
            "pixelparts_app_icons_shape_background_tint_color";
    private static final String KEY_APP_ICONS_SHAPE_FOREGROUND_TINT_MODE =
            "pixelparts_app_icons_shape_foreground_tint_mode";
    private static final String KEY_APP_ICONS_SHAPE_FOREGROUND_TINT_COLOR =
            "pixelparts_app_icons_shape_foreground_tint_color";
    private static final String KEY_ICON_SHAPE_WORKSPACE_MATCH_ALL_APPS =
            "pixelparts_icon_shape_workspace_match_all_apps";
    private static final String KEY_ICON_SHAPE_IGNORE_CUSTOM_SETTINGS =
            "pixelparts_icon_shape_ignore_custom_settings";
    private static final String KEY_ICON_SHAPE_ALL_APPS_FOLLOW_WORKSPACE =
            "pixelparts_icon_shape_all_apps_follow_workspace";
    private static final String KEY_ICON_SHAPE_ALL_APPS_THEMED_ICONS =
            "pixelparts_icon_shape_all_apps_themed_icons";
        private static final String KEY_ICON_SHAPE_ALL_APPS_SUGGESTIONS_THEMED_ICONS =
            "pixelparts_icon_shape_all_apps_suggestions_themed_icons";
        private static final String KEY_ICON_SHAPE_SEARCH_THEMED_ICONS =
            "pixelparts_icon_shape_search_themed_icons";
    private static final String ICON_RELOAD_ACTION = "com.pixelparts.intent.action.RELOAD_ICONS";
    private static final int ICON_SHAPE_DEFAULT = 0;
    private static final int ICON_SHAPE_STRETCH = 1;
    private static final int ICON_SHAPE_REMOVE = 2;
    private static final int DISPLAY_ALL_APPS = 1;
    private static final int DISPLAY_SEARCH_RESULT_TALL = 6;
    private static final int DISPLAY_SEARCH_RESULT_SMALL = 7;
    private static final int DISPLAY_PREDICTION_ROW = 8;
    private static final int DISPLAY_SEARCH_RESULT_APP_ROW = 9;
    private static final int ICON_TINT_OFF = 0;
    private static final int ICON_TINT_CUSTOM = 1;
    private static final int ICON_TINT_AUTO = 2;
    private static final String DYNAMIC_ICON_CALENDAR = "calendar";
    private static final String DYNAMIC_ICON_CLOCK = "clock";
    private static final float DEFAULT_SHAPE_SCALE_PERCENT = 72f;
    private static final float MIN_SHAPE_SCALE_PERCENT = 0f;
    private static final float MAX_SHAPE_SCALE_PERCENT = 200f;
    private static final File ICON_ROOT = new File("/data/pixelparts/IconsManager");
    private static final File ICON_MAP_FILE = new File(ICON_ROOT, "icon_map.json");
    private static final String[] ICON_DENSITY_FOLDERS = {
            "mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi"
    };
    private static final ConcurrentHashMap<String, String> iconMap = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, IconSource> iconSources = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, ShapeOverride> shapeOverrides = new ConcurrentHashMap<>();
    private static final LruCache<String, Bitmap> iconCache = new LruCache<>(160);
    private static final Object iconLock = new Object();
    private static volatile boolean iconMapLoaded;
    private static volatile boolean reloadReceiverRegistered;
    private static volatile long iconMapLastModified = Long.MIN_VALUE;
    private Class<?> clockDrawableWrapperClass;
    private Class<?> clockAnimationInfoClass;

    @Override
    public String getHookId() {
        return "LauncherIconOverrideHook";
    }

    @Override
    public int getPriority() {
        return 65;
    }

    @Override
    public boolean isEnabled(Context context) {
        return isLauncherIconsEnabled(context);
    }

    @Override
    protected void onInit(ClassLoader classLoader) {
        initDynamicClockClasses(classLoader);
        hookProviderClass(classLoader, "com.android.launcher3.icons.IconProvider");
        hookProviderClass(classLoader, "com.android.launcher3.icons.LauncherIconProviderImpl");
        hookIconProviderGetIcon(classLoader);
        hookBaseIconFactory(classLoader);
        hookIconState(classLoader);
        hookFloatingIconView(classLoader);
        hookBubbleTextViewThemeMode(classLoader);
    }

    private void initDynamicClockClasses(ClassLoader classLoader) {
        try {
            clockDrawableWrapperClass = XposedHelpers.findClass(
                    "com.android.launcher3.icons.ClockDrawableWrapper", classLoader);
            clockAnimationInfoClass = XposedHelpers.findClass(
                    "com.android.launcher3.icons.ClockDrawableWrapper$ClockAnimationInfo", classLoader);
        } catch (Throwable ignored) {
            clockDrawableWrapperClass = null;
            clockAnimationInfoClass = null;
        }
    }

    private void hookBaseIconFactory(ClassLoader classLoader) {
        try {
            Class<?> factoryClass = XposedHelpers.findClass("com.android.launcher3.icons.BaseIconFactory", classLoader);
            Class<?> optionsClass = XposedHelpers.findClass("com.android.launcher3.icons.BaseIconFactory$IconOptions", classLoader);
            XposedHelpers.findAndHookMethod(
                    factoryClass,
                    "createBadgedIconBitmap",
                    Drawable.class,
                    optionsClass,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            if (!(param.args[0] instanceof Drawable)) {
                                return;
                            }

                            Context context = getObjectContext(param.thisObject);
                            Drawable originalDrawable = (Drawable) param.args[0];
                            boolean isPixelPartsIcon = originalDrawable instanceof PixelPartsBitmapDrawable;
                            if (!isPixelPartsIcon && !isWorkspaceMatchAllAppsEnabled(context)) {
                                return;
                            }
                            String packageName = isPixelPartsIcon
                                    ? ((PixelPartsBitmapDrawable) originalDrawable).packageName
                                    : getIconOptionsPackageName(param.args[1]);
                            ShapeConfig shapeConfig = getLauncherIconShapeConfig(context, packageName);
                            if (shapeConfig.mode == ICON_SHAPE_REMOVE) {
                                applyNoWrapIconOptions(param.args[1]);
                            } else if (shapeConfig.mode == ICON_SHAPE_STRETCH) {
                                Drawable foreground = originalDrawable;
                                Drawable background = resolveBackgroundDrawable(
                                        foreground,
                                        foreground,
                                        shapeConfig,
                                        getIconOptionsWrapperBackgroundColor(param.args[1]));
                                int backgroundTint = dominantSafeBackgroundColor(background);
                                param.args[0] = new AdaptiveIconDrawable(
                                        background,
                                        new ScaledForegroundDrawable(
                                                foreground,
                                                shapeConfig.scale,
                                                resolveForegroundTint(foreground, shapeConfig, backgroundTint)));
                                applyNoWrapIconOptions(param.args[1]);
                            }
                        }
                    });
            log("Hooked BaseIconFactory.createBadgedIconBitmap shape controls");
        } catch (Throwable t) {
            logError("Unable to hook BaseIconFactory.createBadgedIconBitmap", t);
        }
    }

    private void hookProviderClass(ClassLoader classLoader, String className) {
        try {
            Class<?> providerClass = XposedHelpers.findClass(className, classLoader);
            XposedHelpers.findAndHookMethod(
                    providerClass,
                    "loadPackageIcon",
                    PackageItemInfo.class,
                    ApplicationInfo.class,
                    int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            Drawable override = loadOverrideDrawable(
                                    param.thisObject,
                                    (PackageItemInfo) param.args[0],
                                    (ApplicationInfo) param.args[1],
                                    (Integer) param.args[2]);
                            if (override != null) {
                                param.setResult(override);
                            }
                        }
                    });
            log("Hooked " + className + ".loadPackageIcon");
        } catch (Throwable t) {
            logError("Unable to hook " + className + ".loadPackageIcon", t);
        }
    }

    private void hookIconProviderGetIcon(ClassLoader classLoader) {
        try {
            Class<?> providerClass = XposedHelpers.findClass("com.android.launcher3.icons.IconProvider", classLoader);
            XposedHelpers.findAndHookMethod(
                    providerClass,
                    "getIcon",
                    PackageItemInfo.class,
                    ApplicationInfo.class,
                    int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            Drawable override = loadOverrideDrawable(
                                    param.thisObject,
                                    (PackageItemInfo) param.args[0],
                                    (ApplicationInfo) param.args[1],
                                    (Integer) param.args[2]);
                            if (override != null) {
                                param.setResult(override);
                            }
                        }
                    });
            log("Hooked IconProvider.getIcon PixelParts override");
        } catch (Throwable t) {
            logError("Unable to hook IconProvider.getIcon", t);
        }
    }

    private void hookFloatingIconView(ClassLoader classLoader) {
        try {
            Class<?> floatingIconViewClass = XposedHelpers.findClass(
                    "com.android.launcher3.views.FloatingIconView", classLoader);
            XposedHelpers.findAndHookMethod(
                    floatingIconViewClass,
                    "setIcon",
                    Drawable.class,
                    Drawable.class,
                    Supplier.class,
                    int.class,
                    boolean.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            Context context = getFloatingIconContext(param.thisObject);
                            String packageName = getFloatingIconPackageName(param.thisObject);
                            ShapeConfig shapeConfig = getLauncherIconShapeConfig(context, packageName);
                            if (shapeConfig.mode == ICON_SHAPE_DEFAULT
                                    || !isFloatingIconPixelPartsIcon(context, packageName)) {
                                return;
                            }

                            Drawable drawable = (Drawable) param.args[0];
                            Drawable singleLayer = resolveFloatingSingleLayerDrawable(
                                    context,
                                    drawable,
                                    param.args[2],
                                    shapeConfig.mode == ICON_SHAPE_REMOVE);
                            if (singleLayer != null) {
                                param.args[0] = singleLayer;
                                param.args[2] = null;
                                param.args[4] = false;
                            }
                        }
                    });
            log("Hooked FloatingIconView.setIcon shape controls");
        } catch (Throwable t) {
            logError("Unable to hook FloatingIconView.setIcon", t);
        }
    }

    private void hookBubbleTextViewThemeMode(ClassLoader classLoader) {
        try {
            Class<?> bubbleTextViewClass = XposedHelpers.findClass(
                    "com.android.launcher3.BubbleTextView", classLoader);
            XposedHelpers.findAndHookMethod(
                    bubbleTextViewClass,
                    "shouldUseTheme",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            if (!(param.thisObject instanceof View)) {
                                return;
                            }
                            Context context = ((View) param.thisObject).getContext();
                            if (shouldForceThemedIcon(context, param.thisObject)) {
                                param.setResult(true);
                            }
                        }
                    });
            log("Hooked BubbleTextView.shouldUseTheme All Apps controls");
        } catch (Throwable t) {
            logError("Unable to hook BubbleTextView.shouldUseTheme", t);
        }
    }

    private void hookIconState(ClassLoader classLoader) {
        try {
            Class<?> providerClass = XposedHelpers.findClass("com.android.launcher3.icons.IconProvider", classLoader);
            XposedHelpers.findAndHookMethod(
                    providerClass,
                    "getStateForApp",
                    ApplicationInfo.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            String baseState = String.valueOf(param.getResult());
                            Context context = getProviderContext(param.thisObject);
                            maybeRegisterIconReloadReceiver(context);
                            ApplicationInfo appInfo = (ApplicationInfo) param.args[0];
                            int enabled = isLauncherIconsEnabled(context) ? 1 : 0;
                            long mapVersion = getFileLastModified(ICON_MAP_FILE);
                            String packageName = appInfo != null ? appInfo.packageName : "";
                            ShapeConfig shapeConfig = getLauncherIconShapeConfig(context, packageName);
                            param.setResult(baseState + " pixelparts-icons=" + enabled + ":"
                                    + mapVersion + ":" + shapeConfig.mode + ":"
                                    + Math.round(shapeConfig.scale * 10000f) + ":"
                                    + shapeConfig.backgroundTintMode + ":"
                                    + shapeConfig.backgroundTintColor + ":"
                                    + shapeConfig.foregroundTintMode + ":"
                                    + shapeConfig.foregroundTintColor + ":" + packageName
                                    + ":" + dynamicStateForPackage(packageName)
                                    + ":" + launcherShapeFlagsState(context));
                        }
                    });
            log("Hooked IconProvider.getStateForApp freshness");
        } catch (Throwable t) {
            logError("Unable to hook IconProvider.getStateForApp", t);
        }
    }

    private Drawable loadOverrideDrawable(
            Object provider,
            PackageItemInfo itemInfo,
            ApplicationInfo appInfo,
            int densityDpi) {
        Context context = getProviderContext(provider);
        if (context == null || !isLauncherIconsEnabled(context)) {
            return null;
        }
        maybeRegisterIconReloadReceiver(context);

        String packageName = resolvePackageName(itemInfo, appInfo);
        if (TextUtils.isEmpty(packageName)) {
            return null;
        }

        IconSource source = getIconSource(packageName);
        Drawable dynamicDrawable = loadDynamicOverrideDrawable(context, source, packageName, densityDpi);
        if (dynamicDrawable != null) {
            return dynamicDrawable;
        }

        String iconFileName = getIconFileName(packageName);
        if (TextUtils.isEmpty(iconFileName)) {
            return null;
        }

        File iconFile = findIconFile(iconFileName, densityDpi);
        if (iconFile == null) {
            return null;
        }

        String cacheKey = packageName + "|" + densityDpi + "|"
            + iconFile.getAbsolutePath() + "|" + getFileLastModified(iconFile);
        Bitmap cachedBitmap;
        synchronized (iconCache) {
            cachedBitmap = iconCache.get(cacheKey);
        }
        if (cachedBitmap != null) {
            return new PixelPartsBitmapDrawable(context.getResources(), cachedBitmap, packageName);
        }

        StrictMode.ThreadPolicy oldPolicy = StrictMode.allowThreadDiskReads();
        try (InputStream input = new FileInputStream(iconFile)) {
            Bitmap bitmap = BitmapFactory.decodeStream(input);
            if (bitmap == null) {
                return null;
            }

            synchronized (iconCache) {
                iconCache.put(cacheKey, bitmap);
            }
            return new PixelPartsBitmapDrawable(context.getResources(), bitmap, packageName);
        } catch (IOException e) {
            logError("Unable to load PixelParts icon override for " + packageName, e);
            return null;
        } finally {
            StrictMode.setThreadPolicy(oldPolicy);
        }
    }

    private Context getProviderContext(Object provider) {
        return getObjectContext(provider);
    }

    private static Context getObjectContext(Object object) {
        if (object == null) {
            return null;
        }
        Context context = getContextField(object, "mContext");
        return context != null ? context : getContextField(object, "context");
    }

    private static Context getContextField(Object object, String fieldName) {
        if (object == null) {
            return null;
        }
        try {
            Object value = XposedHelpers.getObjectField(object, fieldName);
            return value instanceof Context ? (Context) value : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Context getFloatingIconContext(Object object) {
        if (object instanceof View) {
            return ((View) object).getContext();
        }
        return getObjectContext(object);
    }

    private static boolean isLauncherIconsEnabled(Context context) {
        if (context == null) {
            return true;
        }
        try {
            int fallback = Settings.Global.getInt(
                    context.getContentResolver(), KEY_APP_ICONS_ENABLED, 1);
            return Settings.Global.getInt(
                    context.getContentResolver(), KEY_APP_ICONS_LAUNCHER_ENABLED, fallback) != 0;
        } catch (Throwable ignored) {
            return true;
        }
    }

    private static ShapeConfig getLauncherIconShapeConfig(Context context, String packageName) {
        TintConfig tintConfig = getGlobalTintConfig(context);
        boolean ignoreCustomShape = isIgnoreCustomSettingsShapeEnabled(context);
        ShapeOverride override = ignoreCustomShape ? null : getShapeOverride(packageName);
        if (override != null) {
            return new ShapeConfig(override.mode, scalePercentToFloat(override.scalePercent), tintConfig);
        }
        ShapeConfig globalConfig = ignoreCustomShape
                ? new ShapeConfig(ICON_SHAPE_DEFAULT, 1f, tintConfig)
                : getGlobalLauncherIconShapeConfig(context, tintConfig);
        if (globalConfig.mode != ICON_SHAPE_DEFAULT) {
            return globalConfig;
        }
        if (isWorkspaceMatchAllAppsEnabled(context)) {
            return new ShapeConfig(
                    ICON_SHAPE_STRETCH,
                    scalePercentToFloat(readLauncherShapeScalePercent(context)),
                    tintConfig);
        }
        return globalConfig;
    }

    private static ShapeConfig getGlobalLauncherIconShapeConfig(Context context, TintConfig tintConfig) {
        if (context == null) {
            return new ShapeConfig(ICON_SHAPE_DEFAULT, 1f, tintConfig);
        }
        try {
            float scalePercent = readLauncherShapeScalePercent(context);
            if (Settings.Global.getInt(
                    context.getContentResolver(), KEY_APP_ICONS_LAUNCHER_REMOVE_SHAPE, 0) != 0) {
                return new ShapeConfig(ICON_SHAPE_REMOVE, scalePercentToFloat(scalePercent), tintConfig);
            }
            if (Settings.Global.getInt(
                    context.getContentResolver(), KEY_APP_ICONS_LAUNCHER_STRETCH_SHAPE, 0) != 0) {
                return new ShapeConfig(ICON_SHAPE_STRETCH, scalePercentToFloat(scalePercent), tintConfig);
            }
        } catch (Throwable ignored) {
        }
        return new ShapeConfig(ICON_SHAPE_DEFAULT, 1f, tintConfig);
    }

    private static float readLauncherShapeScalePercent(Context context) {
        if (context == null) {
            return DEFAULT_SHAPE_SCALE_PERCENT;
        }
        try {
            return Settings.Global.getFloat(
                    context.getContentResolver(),
                    KEY_APP_ICONS_LAUNCHER_SHAPE_SCALE,
                    DEFAULT_SHAPE_SCALE_PERCENT);
        } catch (Throwable ignored) {
            return DEFAULT_SHAPE_SCALE_PERCENT;
        }
    }

    private static boolean isWorkspaceMatchAllAppsEnabled(Context context) {
        return isGlobalSettingEnabled(context, KEY_ICON_SHAPE_WORKSPACE_MATCH_ALL_APPS, false);
    }

    private static boolean isIgnoreCustomSettingsShapeEnabled(Context context) {
        return isGlobalSettingEnabled(context, KEY_ICON_SHAPE_IGNORE_CUSTOM_SETTINGS, false);
    }

    private static boolean isAllAppsFollowWorkspaceEnabled(Context context) {
        return isGlobalSettingEnabled(context, KEY_ICON_SHAPE_ALL_APPS_FOLLOW_WORKSPACE, false);
    }

    private static boolean isAllAppsThemedIconsEnabled(Context context) {
        return isGlobalSettingEnabled(context, KEY_ICON_SHAPE_ALL_APPS_THEMED_ICONS, false);
    }

    private static boolean isAllAppsSuggestionsThemedIconsEnabled(Context context) {
        return isGlobalSettingEnabled(context, KEY_ICON_SHAPE_ALL_APPS_SUGGESTIONS_THEMED_ICONS, false);
    }

    private static boolean isSearchThemedIconsEnabled(Context context) {
        return isGlobalSettingEnabled(context, KEY_ICON_SHAPE_SEARCH_THEMED_ICONS, false);
    }

    private static boolean isGlobalSettingEnabled(Context context, String key, boolean defaultValue) {
        if (context == null) {
            return defaultValue;
        }
        try {
            return Settings.Global.getInt(context.getContentResolver(), key, defaultValue ? 1 : 0) != 0;
        } catch (Throwable ignored) {
            return defaultValue;
        }
    }

    private static String launcherShapeFlagsState(Context context) {
        return (isWorkspaceMatchAllAppsEnabled(context) ? "1" : "0")
                + (isIgnoreCustomSettingsShapeEnabled(context) ? "1" : "0")
                + (isAllAppsFollowWorkspaceEnabled(context) ? "1" : "0")
                + (isAllAppsThemedIconsEnabled(context) ? "1" : "0")
                + (isAllAppsSuggestionsThemedIconsEnabled(context) ? "1" : "0")
                + (isSearchThemedIconsEnabled(context) ? "1" : "0");
    }

    private static boolean shouldForceThemedIcon(Context context, Object view) {
        int display = getBubbleDisplay(view);
        if (display == DISPLAY_ALL_APPS) {
            return isAllAppsFollowWorkspaceEnabled(context) || isAllAppsThemedIconsEnabled(context);
        }
        if (display == DISPLAY_PREDICTION_ROW) {
            return isAllAppsSuggestionsThemedIconsEnabled(context);
        }
        if (display == DISPLAY_SEARCH_RESULT_TALL
                || display == DISPLAY_SEARCH_RESULT_SMALL
                || display == DISPLAY_SEARCH_RESULT_APP_ROW
                || view.getClass().getName().endsWith("SearchResultIcon")) {
            return isSearchThemedIconsEnabled(context);
        }
        return false;
    }

    private static int getBubbleDisplay(Object view) {
        try {
            return XposedHelpers.getIntField(view, "mDisplay");
        } catch (Throwable ignored) {
            return -1;
        }
    }

    private static String getIconOptionsPackageName(Object options) {
        Object sourceHint = getObjectFieldQuietly(options, "sourceHint");
        Object componentKey = getObjectFieldQuietly(sourceHint, "key");
        Object componentName = getObjectFieldQuietly(componentKey, "componentName");
        if (componentName instanceof ComponentName) {
            return ((ComponentName) componentName).getPackageName();
        }
        return null;
    }

    private static TintConfig getGlobalTintConfig(Context context) {
        if (context == null) {
            return TintConfig.DEFAULT;
        }
        try {
            return new TintConfig(
                    normalizeTintMode(Settings.Global.getInt(
                            context.getContentResolver(),
                            KEY_APP_ICONS_SHAPE_BACKGROUND_TINT_MODE,
                            ICON_TINT_OFF)),
                    Settings.Global.getInt(
                            context.getContentResolver(),
                            KEY_APP_ICONS_SHAPE_BACKGROUND_TINT_COLOR,
                            Color.TRANSPARENT),
                    normalizeTintMode(Settings.Global.getInt(
                            context.getContentResolver(),
                            KEY_APP_ICONS_SHAPE_FOREGROUND_TINT_MODE,
                            ICON_TINT_OFF)),
                    Settings.Global.getInt(
                            context.getContentResolver(),
                            KEY_APP_ICONS_SHAPE_FOREGROUND_TINT_COLOR,
                            Color.WHITE));
        } catch (Throwable ignored) {
            return TintConfig.DEFAULT;
        }
    }

    private static float scalePercentToFloat(float scalePercent) {
        float clamped = Math.max(MIN_SHAPE_SCALE_PERCENT, Math.min(MAX_SHAPE_SCALE_PERCENT, scalePercent));
        return clamped / 100f;
    }

    private static int normalizeTintMode(int mode) {
        return mode == ICON_TINT_CUSTOM || mode == ICON_TINT_AUTO ? mode : ICON_TINT_OFF;
    }

    private static int resolveBackgroundTint(Drawable drawable, ShapeConfig shapeConfig, int fallbackColor) {
        if (shapeConfig.backgroundTintMode == ICON_TINT_CUSTOM) {
            return shapeConfig.backgroundTintColor;
        }
        if (shapeConfig.backgroundTintMode == ICON_TINT_AUTO) {
            int dominant = dominantIconColor(drawable);
            return Color.alpha(dominant) > 0 ? dominant : fallbackColor;
        }
        return fallbackColor;
    }

    private static Drawable resolveBackgroundDrawable(
            Drawable original,
            Drawable foreground,
            ShapeConfig shapeConfig,
            int fallbackColor) {
        if (shapeConfig.backgroundTintMode == ICON_TINT_OFF && original instanceof AdaptiveIconDrawable) {
            Drawable background = ((AdaptiveIconDrawable) original).getBackground();
            if (background != null) {
                return copyDrawable(background);
            }
        }
        return new ColorDrawable(resolveBackgroundTint(foreground, shapeConfig, fallbackColor));
    }

    private static Drawable copyDrawable(Drawable drawable) {
        if (drawable == null) {
            return new ColorDrawable(Color.TRANSPARENT);
        }
        Drawable.ConstantState constantState = drawable.getConstantState();
        if (constantState != null) {
            return constantState.newDrawable().mutate();
        }
        return drawable.mutate();
    }

    private static Drawable resolveFloatingSingleLayerDrawable(
            Context context,
            Drawable drawable,
            Object supplierObject,
            boolean removeShape) {
        Drawable supplied = getSupplierDrawable(supplierObject);
        if (supplied != null) {
            return copyDrawable(supplied);
        }
        if (removeShape) {
            Drawable foreground = getAdaptiveIconForeground(drawable);
            if (foreground != null) {
                return copyDrawable(foreground);
            }
        }
        return flattenDrawableForFloating(context, drawable);
    }

    private static Drawable getSupplierDrawable(Object supplierObject) {
        if (!(supplierObject instanceof Supplier)) {
            return null;
        }
        try {
            Object supplied = ((Supplier<?>) supplierObject).get();
            return supplied instanceof Drawable ? (Drawable) supplied : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Drawable flattenDrawableForFloating(Context context, Drawable drawable) {
        if (drawable == null || context == null) {
            return null;
        }
        try {
            int targetPx = Math.max(1, Math.round(96f * context.getResources().getDisplayMetrics().density));
            Bitmap bitmap = Bitmap.createBitmap(targetPx, targetPx, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            Rect oldBounds = new Rect(drawable.getBounds());
            drawable.setBounds(0, 0, targetPx, targetPx);
            drawable.draw(canvas);
            drawable.setBounds(oldBounds);
            PixelPartsBitmapDrawable bitmapDrawable = new PixelPartsBitmapDrawable(context.getResources(), bitmap, "");
            bitmapDrawable.setTargetDensity(context.getResources().getDisplayMetrics());
            return bitmapDrawable;
        } catch (Throwable ignored) {
            return copyDrawable(drawable);
        }
    }

    private static int dominantSafeBackgroundColor(Drawable drawable) {
        int color = dominantIconColor(drawable);
        return Color.alpha(color) > 0 ? color : Color.WHITE;
    }

    private static int getIconOptionsWrapperBackgroundColor(Object options) {
        if (options == null) {
            return Color.WHITE;
        }
        try {
            return XposedHelpers.getIntField(options, "wrapperBackgroundColor");
        } catch (Throwable ignored) {
            return Color.WHITE;
        }
    }

    private static Integer resolveForegroundTint(
            Drawable drawable, ShapeConfig shapeConfig, int backgroundTint) {
        if (shapeConfig.foregroundTintMode == ICON_TINT_CUSTOM) {
            return shapeConfig.foregroundTintColor;
        }
        if (shapeConfig.foregroundTintMode == ICON_TINT_AUTO) {
            int baseColor = Color.alpha(backgroundTint) > 0 ? backgroundTint : dominantIconColor(drawable);
            return contrastColor(baseColor);
        }
        return null;
    }

    private static int dominantIconColor(Drawable drawable) {
        if (drawable == null) {
            return Color.TRANSPARENT;
        }
        try {
            int width = Math.max(1, drawable.getIntrinsicWidth() > 0 ? drawable.getIntrinsicWidth() : 48);
            int height = Math.max(1, drawable.getIntrinsicHeight() > 0 ? drawable.getIntrinsicHeight() : 48);
            width = Math.min(width, 64);
            height = Math.min(height, 64);
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            int oldLeft = drawable.getBounds().left;
            int oldTop = drawable.getBounds().top;
            int oldRight = drawable.getBounds().right;
            int oldBottom = drawable.getBounds().bottom;
            drawable.setBounds(0, 0, width, height);
            drawable.draw(canvas);
            drawable.setBounds(oldLeft, oldTop, oldRight, oldBottom);

            long red = 0L;
            long green = 0L;
            long blue = 0L;
            long weight = 0L;
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int color = bitmap.getPixel(x, y);
                    int alpha = Color.alpha(color);
                    if (alpha < 32) {
                        continue;
                    }
                    red += (long) Color.red(color) * alpha;
                    green += (long) Color.green(color) * alpha;
                    blue += (long) Color.blue(color) * alpha;
                    weight += alpha;
                }
            }
            bitmap.recycle();
            if (weight <= 0L) {
                return Color.TRANSPARENT;
            }
            return Color.rgb((int) (red / weight), (int) (green / weight), (int) (blue / weight));
        } catch (Throwable ignored) {
            return Color.TRANSPARENT;
        }
    }

    private static int contrastColor(int color) {
        double luminance = (0.299d * Color.red(color)
                + 0.587d * Color.green(color)
                + 0.114d * Color.blue(color)) / 255d;
        return luminance > 0.55d ? Color.BLACK : Color.WHITE;
    }

    private static void applyNoWrapIconOptions(Object options) {
        if (options == null) {
            return;
        }
        callMethodIfPresent(options, "setWrapNonAdaptiveIcon", false);
        setBooleanFieldIfPresent(options, "wrapNonAdaptiveIcon", false);
        callMethodIfPresent(options, "setIconScale", 1f);
        setFloatFieldIfPresent(options, "iconScale", 1f);
        callMethodIfPresent(options, "setDrawFullBleed", false);
        setBooleanFieldIfPresent(options, "isFullBleed", false);
        setObjectFieldIfPresent(options, "drawFullBleed", Boolean.FALSE);
    }

    private static void callMethodIfPresent(Object object, String methodName, Object... args) {
        try {
            XposedHelpers.callMethod(object, methodName, args);
        } catch (Throwable ignored) {
        }
    }

    private static void setBooleanFieldIfPresent(Object object, String fieldName, boolean value) {
        try {
            XposedHelpers.setBooleanField(object, fieldName, value);
        } catch (Throwable ignored) {
        }
    }

    private static void setFloatFieldIfPresent(Object object, String fieldName, float value) {
        try {
            XposedHelpers.setFloatField(object, fieldName, value);
        } catch (Throwable ignored) {
        }
    }

    private static void setObjectFieldIfPresent(Object object, String fieldName, Object value) {
        try {
            XposedHelpers.setObjectField(object, fieldName, value);
        } catch (Throwable ignored) {
        }
    }

    private static String resolvePackageName(PackageItemInfo itemInfo, ApplicationInfo appInfo) {
        if (itemInfo != null && !TextUtils.isEmpty(itemInfo.packageName)) {
            return itemInfo.packageName;
        }
        return appInfo != null ? appInfo.packageName : null;
    }

    private static String getIconFileName(String packageName) {
        synchronized (iconLock) {
            refreshIconMapLocked();
            return iconMap.get(packageName);
        }
    }

    private static IconSource getIconSource(String packageName) {
        if (TextUtils.isEmpty(packageName)) {
            return null;
        }
        synchronized (iconLock) {
            refreshIconMapLocked();
            return iconSources.get(packageName);
        }
    }

    private static void refreshIconMapLocked() {
        StrictMode.ThreadPolicy oldPolicy = StrictMode.allowThreadDiskReads();
        try {
            long lastModified = ICON_MAP_FILE.isFile() ? ICON_MAP_FILE.lastModified() : 0L;
            if (iconMapLoaded && iconMapLastModified == lastModified) {
                return;
            }

            iconMapLoaded = true;
            iconMapLastModified = lastModified;
            iconMap.clear();
            iconSources.clear();
            shapeOverrides.clear();
            synchronized (iconCache) {
                iconCache.evictAll();
            }
            if (lastModified == 0L) {
                return;
            }

            try (InputStream input = new FileInputStream(ICON_MAP_FILE)) {
                JSONObject root = new JSONObject(new String(input.readAllBytes(), StandardCharsets.UTF_8));
                JSONObject icons = root.optJSONObject("icons") != null ? root.optJSONObject("icons") : root;
                Iterator<String> packages = icons.keys();
                while (packages.hasNext()) {
                    String packageName = packages.next();
                    Object value = icons.opt(packageName);
                    if (!(value instanceof String)) {
                        continue;
                    }

                    String fileName = normalizeIconFileName((String) value);
                    if (!TextUtils.isEmpty(packageName) && fileName != null) {
                        iconMap.put(packageName, fileName);
                    }
                }
                JSONObject sources = root.optJSONObject("sources");
                if (sources != null) {
                    Iterator<String> sourcePackages = sources.keys();
                    while (sourcePackages.hasNext()) {
                        String packageName = sourcePackages.next();
                        JSONObject sourceObject = sources.optJSONObject(packageName);
                        IconSource source = readIconSource(sourceObject);
                        if (!TextUtils.isEmpty(packageName) && source != null) {
                            iconSources.put(packageName, source);
                        }
                    }
                }
                JSONObject overrides = root.optJSONObject("shapeOverrides");
                if (overrides != null) {
                    Iterator<String> overridePackages = overrides.keys();
                    while (overridePackages.hasNext()) {
                        String packageName = overridePackages.next();
                        JSONObject appObject = overrides.optJSONObject(packageName);
                        ShapeOverride override = readShapeOverride(appObject, "launcher");
                        if (!TextUtils.isEmpty(packageName) && override != null) {
                            shapeOverrides.put(packageName, override);
                        }
                    }
                }
            } catch (IOException | JSONException ignored) {
                iconMap.clear();
                iconSources.clear();
                shapeOverrides.clear();
            }
        } finally {
            StrictMode.setThreadPolicy(oldPolicy);
        }
    }

    private static ShapeOverride getShapeOverride(String packageName) {
        if (TextUtils.isEmpty(packageName)) {
            return null;
        }
        synchronized (iconLock) {
            refreshIconMapLocked();
            return shapeOverrides.get(packageName);
        }
    }

    private static ShapeOverride readShapeOverride(JSONObject appObject, String areaName) {
        if (appObject == null) {
            return null;
        }
        JSONObject area = appObject.optJSONObject(areaName);
        if (area == null) {
            return null;
        }
        boolean removeShape = area.optBoolean("removeShape", false);
        boolean stretchShape = area.optBoolean("stretchShape", false) && !removeShape;
        int mode = removeShape ? ICON_SHAPE_REMOVE : (stretchShape ? ICON_SHAPE_STRETCH : ICON_SHAPE_DEFAULT);
        float scalePercent = (float) area.optDouble("scalePercent", DEFAULT_SHAPE_SCALE_PERCENT);
        return new ShapeOverride(mode,
                Math.max(MIN_SHAPE_SCALE_PERCENT, Math.min(MAX_SHAPE_SCALE_PERCENT, scalePercent)));
    }

    private static IconSource readIconSource(JSONObject sourceObject) {
        if (sourceObject == null) {
            return null;
        }
        String iconPackPackage = sourceObject.optString("iconPackPackage").trim();
        String drawableName = sourceObject.optString("drawableName").trim();
        if (TextUtils.isEmpty(iconPackPackage) || TextUtils.isEmpty(drawableName)) {
            return null;
        }
        JSONObject dynamicObject = sourceObject.optJSONObject("dynamicIcon");
        String dynamicType = dynamicObject != null
                ? dynamicObject.optString("type").trim()
                : sourceObject.optString("dynamicType").trim();
        ClockConfig clockConfig = null;
        JSONObject clockObject = dynamicObject != null
                ? dynamicObject.optJSONObject("clockConfig")
                : sourceObject.optJSONObject("clockConfig");
        if (clockObject != null) {
            clockConfig = new ClockConfig(
                    clockObject.optInt("hourLayerIndex", -1),
                    clockObject.optInt("minuteLayerIndex", -1),
                    clockObject.optInt("secondLayerIndex", -1),
                    clockObject.optInt("defaultHour", 0),
                    clockObject.optInt("defaultMinute", 0),
                    clockObject.optInt("defaultSecond", 0));
        }
        return new IconSource(
                iconPackPackage,
                drawableName,
                dynamicType,
                optString(dynamicObject, sourceObject, "calendarPrefix"),
                optString(dynamicObject, sourceObject, "calendarFallbackDrawable"),
                clockConfig);
    }

    private static String optString(JSONObject primary, JSONObject fallback, String key) {
        String value = primary != null ? primary.optString(key).trim() : "";
        if (TextUtils.isEmpty(value) && fallback != null) {
            value = fallback.optString(key).trim();
        }
        return TextUtils.isEmpty(value) ? null : value;
    }

    private static String normalizeIconFileName(String value) {
        if (value == null) {
            return null;
        }

        String fileName = value.trim();
        if (TextUtils.isEmpty(fileName) || fileName.contains("/") || fileName.contains("\\")
                || fileName.contains("..")) {
            return null;
        }

        if (!fileName.endsWith(".png")) {
            fileName += ".png";
        }
        return fileName.length() > ".png".length() ? fileName : null;
    }

    private static File findIconFile(String fileName, int densityDpi) {
        StrictMode.ThreadPolicy oldPolicy = StrictMode.allowThreadDiskReads();
        try {
            String densityFolder = iconDensityFolder(densityDpi);
            File currentDensityFile = new File(new File(ICON_ROOT, densityFolder), fileName);
            if (currentDensityFile.isFile()) {
                return currentDensityFile;
            }

            for (String fallbackDensity : ICON_DENSITY_FOLDERS) {
                if (fallbackDensity.equals(densityFolder)) {
                    continue;
                }
                File fallbackFile = new File(new File(ICON_ROOT, fallbackDensity), fileName);
                if (fallbackFile.isFile()) {
                    return fallbackFile;
                }
            }

            return null;
        } finally {
            StrictMode.setThreadPolicy(oldPolicy);
        }
    }

    private static boolean isFloatingIconPixelPartsIcon(Context context, String packageName) {
        if (context == null || TextUtils.isEmpty(packageName) || !isLauncherIconsEnabled(context)) {
            return false;
        }
        maybeRegisterIconReloadReceiver(context);
        String iconFileName = getIconFileName(packageName);
        return !TextUtils.isEmpty(iconFileName)
                && findIconFile(iconFileName, context.getResources().getConfiguration().densityDpi) != null;
    }

    private static String getFloatingIconPackageName(Object floatingIconView) {
        Object iconLoadResult = getObjectFieldQuietly(floatingIconView, "mIconLoadResult");
        Object itemInfo = getObjectFieldQuietly(iconLoadResult, "itemInfo");
        return getItemInfoPackageName(itemInfo);
    }

    private static String getItemInfoPackageName(Object itemInfo) {
        if (itemInfo == null) {
            return null;
        }

        ComponentName componentName = callComponentNameMethod(itemInfo, "getTargetComponent");
        if (componentName == null) {
            componentName = getComponentNameField(itemInfo, "targetComponent");
        }
        if (componentName == null) {
            componentName = getComponentNameField(itemInfo, "componentName");
        }
        if (componentName != null) {
            return componentName.getPackageName();
        }

        Intent intent = callIntentMethod(itemInfo, "getIntent");
        if (intent == null) {
            Object value = getObjectFieldQuietly(itemInfo, "intent");
            intent = value instanceof Intent ? (Intent) value : null;
        }
        if (intent == null) {
            return null;
        }

        componentName = intent.getComponent();
        if (componentName != null) {
            return componentName.getPackageName();
        }
        return intent.getPackage();
    }

    private static ComponentName callComponentNameMethod(Object object, String methodName) {
        try {
            Object result = XposedHelpers.callMethod(object, methodName);
            return result instanceof ComponentName ? (ComponentName) result : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Intent callIntentMethod(Object object, String methodName) {
        try {
            Object result = XposedHelpers.callMethod(object, methodName);
            return result instanceof Intent ? (Intent) result : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static ComponentName getComponentNameField(Object object, String fieldName) {
        Object value = getObjectFieldQuietly(object, fieldName);
        return value instanceof ComponentName ? (ComponentName) value : null;
    }

    private static Object getObjectFieldQuietly(Object object, String fieldName) {
        if (object == null) {
            return null;
        }
        try {
            return XposedHelpers.getObjectField(object, fieldName);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Drawable getAdaptiveIconForeground(Drawable drawable) {
        if (!(drawable instanceof AdaptiveIconDrawable)) {
            return null;
        }
        Drawable foreground = ((AdaptiveIconDrawable) drawable).getForeground();
        return foreground != null ? foreground : new ColorDrawable(Color.TRANSPARENT);
    }

    private Drawable loadDynamicOverrideDrawable(
            Context context,
            IconSource source,
            String packageName,
            int densityDpi) {
        if (context == null || source == null || TextUtils.isEmpty(source.dynamicType)) {
            return null;
        }

        StrictMode.ThreadPolicy oldPolicy = StrictMode.allowThreadDiskReads();
        try {
            PackageManager packageManager = context.getPackageManager();
            ApplicationInfo packInfo = packageManager.getApplicationInfo(source.iconPackPackage, 0);
            Resources packResources = packageManager.getResourcesForApplication(packInfo);
            if (DYNAMIC_ICON_CALENDAR.equals(source.dynamicType)) {
                String drawableName = resolveCalendarDrawableName(packResources, source);
                Drawable drawable = loadIconPackDrawable(packResources, source.iconPackPackage, drawableName, densityDpi);
                return drawable != null
                        ? renderAsPixelPartsDrawable(context, drawable, packageName, densityDpi)
                        : null;
            }
            if (DYNAMIC_ICON_CLOCK.equals(source.dynamicType)) {
                Drawable drawable = loadIconPackDrawable(packResources, source.iconPackPackage, source.drawableName, densityDpi);
                Drawable clockDrawable = buildClockDrawable(drawable, source);
                if (clockDrawable != null) {
                    return clockDrawable;
                }
                return drawable != null
                        ? renderAsPixelPartsDrawable(context, drawable, packageName, densityDpi)
                        : null;
            }
        } catch (PackageManager.NameNotFoundException | Resources.NotFoundException ignored) {
            return null;
        } catch (Throwable t) {
            logError("Unable to load dynamic PixelParts icon for " + packageName, t);
            return null;
        } finally {
            StrictMode.setThreadPolicy(oldPolicy);
        }
        return null;
    }

    private static Drawable loadIconPackDrawable(
            Resources resources,
            String packageName,
            String drawableName,
            int densityDpi) throws Resources.NotFoundException {
        if (resources == null || TextUtils.isEmpty(packageName) || TextUtils.isEmpty(drawableName)) {
            return null;
        }
        String cleanName = drawableName.substring(0, drawableName.lastIndexOf('.') > 0
                ? drawableName.lastIndexOf('.') : drawableName.length()).trim();
        if (TextUtils.isEmpty(cleanName)) {
            return null;
        }
        int resId = resources.getIdentifier(cleanName, "drawable", packageName);
        if (resId == 0) {
            resId = resources.getIdentifier(cleanName, "mipmap", packageName);
        }
        return resId != 0 ? resources.getDrawableForDensity(resId, densityDpi) : null;
    }

    private static String resolveCalendarDrawableName(Resources resources, IconSource source) {
        Calendar calendar = Calendar.getInstance();
        int day = calendar.get(Calendar.DAY_OF_MONTH);
        String dayText = String.valueOf(day);
        String dayPadded = day < 10 ? "0" + day : dayText;
        String[] candidates = new String[] {
                source.calendarPrefix != null ? source.calendarPrefix + dayText : null,
                source.calendarPrefix != null ? source.calendarPrefix + dayPadded : null,
                source.calendarPrefix != null ? source.calendarPrefix + "_" + dayText : null,
                source.calendarPrefix != null ? source.calendarPrefix + "_" + dayPadded : null,
            replaceCalendarDay(source.calendarFallbackDrawable, dayText, dayPadded),
            replaceTrailingCalendarNumber(source.drawableName, dayText, dayPadded),
            replaceTrailingCalendarNumber(source.calendarFallbackDrawable, dayText, dayPadded),
            source.calendarFallbackDrawable,
                source.drawableName
        };
        for (String candidate : candidates) {
            if (!TextUtils.isEmpty(candidate)
                    && (resources.getIdentifier(candidate, "drawable", source.iconPackPackage) != 0
                    || resources.getIdentifier(candidate, "mipmap", source.iconPackPackage) != 0)) {
                return candidate;
            }
        }
        return source.drawableName;
    }

    private static String replaceCalendarDay(String value, String dayText, String dayPadded) {
        if (TextUtils.isEmpty(value)) {
            return null;
        }
        return value.replace("%02d", dayPadded).replace("%d", dayText);
    }

    private static String replaceTrailingCalendarNumber(String value, String dayText, String dayPadded) {
        if (TextUtils.isEmpty(value)) {
            return null;
        }
        int extensionStart = value.lastIndexOf('.');
        int numberEnd = extensionStart > 0 ? extensionStart : value.length();
        int numberStart = numberEnd;
        while (numberStart > 0 && Character.isDigit(value.charAt(numberStart - 1))) {
            numberStart--;
        }
        if (numberStart == numberEnd) {
            return null;
        }
        String replacement = numberEnd - numberStart >= 2 ? dayPadded : dayText;
        return value.substring(0, numberStart) + replacement + value.substring(numberEnd);
    }

    private static PixelPartsBitmapDrawable renderAsPixelPartsDrawable(
            Context context,
            Drawable drawable,
            String packageName,
            int densityDpi) {
        int effectiveDensity = densityDpi > 0
                ? densityDpi
                : context.getResources().getConfiguration().densityDpi;
        int targetPx = Math.max(1, Math.round(48f * effectiveDensity / 160f));
        Bitmap bitmap = Bitmap.createBitmap(targetPx, targetPx, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Rect oldBounds = new Rect(drawable.getBounds());
        drawable.setBounds(0, 0, targetPx, targetPx);
        drawable.draw(canvas);
        drawable.setBounds(oldBounds);
        return new PixelPartsBitmapDrawable(context.getResources(), bitmap, packageName);
    }

    private Drawable buildClockDrawable(Drawable drawable, IconSource source) {
        if (!(drawable instanceof AdaptiveIconDrawable)
                || source.clockConfig == null
                || clockDrawableWrapperClass == null
                || clockAnimationInfoClass == null) {
            return null;
        }
        try {
            AdaptiveIconDrawable adaptiveIcon = (AdaptiveIconDrawable) drawable.mutate();
            Drawable foreground = adaptiveIcon.getForeground();
            if (!(foreground instanceof LayerDrawable)) {
                return null;
            }
            LayerDrawable layerDrawable = (LayerDrawable) foreground;
            ClockConfig config = source.clockConfig.normalized(layerDrawable.getNumberOfLayers());
            if (!config.hasAnimatedLayer()) {
                return null;
            }
            Drawable.ConstantState constantState = adaptiveIcon.getConstantState();
            if (constantState == null) {
                return null;
            }
            Object wrapper = XposedHelpers.newInstance(
                    clockDrawableWrapperClass,
                    adaptiveIcon.getBackground(),
                    adaptiveIcon.getForeground());
            Object animationInfo;
            try {
                animationInfo = XposedHelpers.newInstance(
                        clockAnimationInfoClass,
                        config.hourLayerIndex,
                        config.minuteLayerIndex,
                        config.secondLayerIndex,
                        config.defaultHour,
                        config.defaultMinute,
                        config.defaultSecond,
                        constantState,
                        0,
                        (Shader) null);
            } catch (Throwable firstFailure) {
                animationInfo = XposedHelpers.newInstance(
                        clockAnimationInfoClass,
                        wrapper,
                        config.hourLayerIndex,
                        config.minuteLayerIndex,
                        config.secondLayerIndex,
                        config.defaultHour,
                        config.defaultMinute,
                        config.defaultSecond,
                        constantState,
                        0,
                        (Shader) null);
            }
            try {
                XposedHelpers.callMethod(animationInfo, "applyTime", Calendar.getInstance(), layerDrawable);
            } catch (Throwable ignored) {
            }
            XposedHelpers.setObjectField(wrapper, "animationInfo", animationInfo);
            return wrapper instanceof Drawable ? (Drawable) wrapper : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String dynamicStateForPackage(String packageName) {
        IconSource source = getIconSource(packageName);
        if (source == null || TextUtils.isEmpty(source.dynamicType)) {
            return "static";
        }
        if (DYNAMIC_ICON_CALENDAR.equals(source.dynamicType)) {
            return "calendar-" + Calendar.getInstance().get(Calendar.DAY_OF_MONTH);
        }
        if (DYNAMIC_ICON_CLOCK.equals(source.dynamicType)) {
            return "clock-" + (source.clockConfig != null ? source.clockConfig.hashCode() : 0);
        }
        return source.dynamicType;
    }

    private static long getFileLastModified(File file) {
        StrictMode.ThreadPolicy oldPolicy = StrictMode.allowThreadDiskReads();
        try {
            return file.lastModified();
        } finally {
            StrictMode.setThreadPolicy(oldPolicy);
        }
    }

    private static String iconDensityFolder(int densityDpi) {
        if (densityDpi <= DisplayMetrics.DENSITY_MEDIUM) {
            return "mdpi";
        } else if (densityDpi <= DisplayMetrics.DENSITY_HIGH) {
            return "hdpi";
        } else if (densityDpi <= DisplayMetrics.DENSITY_XHIGH) {
            return "xhdpi";
        } else if (densityDpi <= DisplayMetrics.DENSITY_XXHIGH) {
            return "xxhdpi";
        }
        return "xxxhdpi";
    }

    private static void maybeRegisterIconReloadReceiver(Context context) {
        if (context == null) {
            return;
        }
        synchronized (iconLock) {
            if (reloadReceiverRegistered) {
                return;
            }
            reloadReceiverRegistered = true;
        }

        Context receiverContext = context.getApplicationContext() != null
                ? context.getApplicationContext() : context;
        try {
            receiverContext.registerReceiver(
                    new BroadcastReceiver() {
                        @Override
                        public void onReceive(Context context, Intent intent) {
                            if (intent != null && ICON_RELOAD_ACTION.equals(intent.getAction())) {
                                synchronized (iconLock) {
                                    iconMapLoaded = false;
                                    iconMapLastModified = Long.MIN_VALUE;
                                    iconMap.clear();
                                    iconSources.clear();
                                    shapeOverrides.clear();
                                }
                                synchronized (iconCache) {
                                    iconCache.evictAll();
                                }
                            }
                        }
                    },
                    new IntentFilter(ICON_RELOAD_ACTION),
                    Context.RECEIVER_EXPORTED);
        } catch (Throwable ignored) {
            synchronized (iconLock) {
                reloadReceiverRegistered = false;
            }
        }
    }

    private static final class PixelPartsBitmapDrawable extends BitmapDrawable {
        final String packageName;

        PixelPartsBitmapDrawable(Resources resources, Bitmap bitmap, String packageName) {
            super(resources, bitmap);
            this.packageName = packageName;
            if (resources != null) {
                setTargetDensity(resources.getDisplayMetrics());
            }
            setAntiAlias(true);
            setFilterBitmap(true);
            setDither(true);
        }
    }

    private static final class ShapeConfig {
        final int mode;
        final float scale;
        final int backgroundTintMode;
        final int backgroundTintColor;
        final int foregroundTintMode;
        final int foregroundTintColor;

        ShapeConfig(int mode, float scale, TintConfig tintConfig) {
            this.mode = mode;
            this.scale = Math.max(0f, Math.min(2f, scale));
            this.backgroundTintMode = tintConfig.backgroundTintMode;
            this.backgroundTintColor = tintConfig.backgroundTintColor;
            this.foregroundTintMode = tintConfig.foregroundTintMode;
            this.foregroundTintColor = tintConfig.foregroundTintColor;
        }
    }

    private static final class TintConfig {
        static final TintConfig DEFAULT = new TintConfig(
                ICON_TINT_OFF, Color.TRANSPARENT, ICON_TINT_OFF, Color.WHITE);

        final int backgroundTintMode;
        final int backgroundTintColor;
        final int foregroundTintMode;
        final int foregroundTintColor;

        TintConfig(
                int backgroundTintMode,
                int backgroundTintColor,
                int foregroundTintMode,
                int foregroundTintColor) {
            this.backgroundTintMode = normalizeTintMode(backgroundTintMode);
            this.backgroundTintColor = backgroundTintColor;
            this.foregroundTintMode = normalizeTintMode(foregroundTintMode);
            this.foregroundTintColor = foregroundTintColor;
        }
    }

    private static final class ShapeOverride {
        final int mode;
        final float scalePercent;

        ShapeOverride(int mode, float scalePercent) {
            this.mode = mode;
            this.scalePercent = scalePercent;
        }
    }

    private static final class IconSource {
        final String iconPackPackage;
        final String drawableName;
        final String dynamicType;
        final String calendarPrefix;
        final String calendarFallbackDrawable;
        final ClockConfig clockConfig;

        IconSource(
                String iconPackPackage,
                String drawableName,
                String dynamicType,
                String calendarPrefix,
                String calendarFallbackDrawable,
                ClockConfig clockConfig) {
            this.iconPackPackage = iconPackPackage;
            this.drawableName = drawableName;
            this.dynamicType = dynamicType;
            this.calendarPrefix = calendarPrefix;
            this.calendarFallbackDrawable = calendarFallbackDrawable;
            this.clockConfig = clockConfig;
        }
    }

    private static final class ClockConfig {
        final int hourLayerIndex;
        final int minuteLayerIndex;
        final int secondLayerIndex;
        final int defaultHour;
        final int defaultMinute;
        final int defaultSecond;

        ClockConfig(
                int hourLayerIndex,
                int minuteLayerIndex,
                int secondLayerIndex,
                int defaultHour,
                int defaultMinute,
                int defaultSecond) {
            this.hourLayerIndex = hourLayerIndex;
            this.minuteLayerIndex = minuteLayerIndex;
            this.secondLayerIndex = secondLayerIndex;
            this.defaultHour = defaultHour;
            this.defaultMinute = defaultMinute;
            this.defaultSecond = defaultSecond;
        }

        ClockConfig normalized(int layerCount) {
            return new ClockConfig(
                    validLayerIndex(hourLayerIndex, layerCount),
                    validLayerIndex(minuteLayerIndex, layerCount),
                    validLayerIndex(secondLayerIndex, layerCount),
                    defaultHour,
                    defaultMinute,
                    defaultSecond);
        }

        boolean hasAnimatedLayer() {
            return hourLayerIndex >= 0 || minuteLayerIndex >= 0 || secondLayerIndex >= 0;
        }

        @Override
        public int hashCode() {
            int result = hourLayerIndex;
            result = 31 * result + minuteLayerIndex;
            result = 31 * result + secondLayerIndex;
            result = 31 * result + defaultHour;
            result = 31 * result + defaultMinute;
            result = 31 * result + defaultSecond;
            return result;
        }

        private static int validLayerIndex(int index, int layerCount) {
            return index >= 0 && index < layerCount ? index : -1;
        }
    }

    private static final class ScaledForegroundDrawable extends Drawable {
        private final Drawable drawable;
        private final float scale;
        private final ColorFilter tintFilter;
        private int alpha = 255;
        private ColorFilter colorFilter;

        ScaledForegroundDrawable(Drawable drawable, float scale, Integer tintColor) {
            this.drawable = (drawable != null ? drawable : new ColorDrawable(Color.TRANSPARENT)).mutate();
            this.scale = Math.max(0f, Math.min(2f, scale));
            this.tintFilter = tintColor != null
                    ? new PorterDuffColorFilter(tintColor, PorterDuff.Mode.SRC_IN)
                    : null;
            if (this.drawable instanceof BitmapDrawable) {
                BitmapDrawable bitmapDrawable = (BitmapDrawable) this.drawable;
                bitmapDrawable.setAntiAlias(true);
                bitmapDrawable.setFilterBitmap(true);
                bitmapDrawable.setDither(true);
            }
        }

        @Override
        public void draw(Canvas canvas) {
            if (scale <= 0f) {
                return;
            }
            Rect bounds = getBounds();
            int save = canvas.saveLayerAlpha(
                    bounds.left,
                    bounds.top,
                    bounds.right,
                    bounds.bottom,
                    alpha);
            canvas.clipRect(bounds);
            canvas.scale(scale, scale, bounds.exactCenterX(), bounds.exactCenterY());
            drawable.setBounds(bounds);
            ColorFilter previousColorFilter = drawable.getColorFilter();
            ColorFilter activeColorFilter = colorFilter != null ? colorFilter : tintFilter;
            if (activeColorFilter != null) {
                drawable.setColorFilter(activeColorFilter);
            }
            drawable.draw(canvas);
            if (activeColorFilter != null) {
                drawable.setColorFilter(previousColorFilter);
            }
            canvas.restoreToCount(save);
        }

        @Override
        public void setAlpha(int alpha) {
            this.alpha = Math.max(0, Math.min(255, alpha));
            invalidateSelf();
        }

        @Override
        public int getAlpha() {
            return alpha;
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
            this.colorFilter = colorFilter;
            drawable.setColorFilter(colorFilter);
            invalidateSelf();
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }

        @Override
        public int getIntrinsicWidth() {
            return drawable.getIntrinsicWidth();
        }

        @Override
        public int getIntrinsicHeight() {
            return drawable.getIntrinsicHeight();
        }

        @Override
        public boolean isStateful() {
            return drawable.isStateful();
        }

        @Override
        protected boolean onStateChange(int[] state) {
            return drawable.setState(state);
        }

        @Override
        protected boolean onLevelChange(int level) {
            return drawable.setLevel(level);
        }

        @Override
        public boolean setVisible(boolean visible, boolean restart) {
            return drawable.setVisible(visible, restart) || super.setVisible(visible, restart);
        }
    }
}
