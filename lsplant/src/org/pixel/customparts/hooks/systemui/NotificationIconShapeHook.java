package org.pixel.customparts.hooks.systemui;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.StrictMode;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.TextUtils;

import org.json.JSONException;
import org.json.JSONObject;
import org.pixel.customparts.core.BaseHook;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;

public class NotificationIconShapeHook extends BaseHook {
    private static final String KEY_APP_ICONS_ENABLED = "pixelparts_app_icons_enabled";
    private static final String KEY_NOTIFICATION_STRETCH_SHAPE =
            "pixelparts_app_icons_notification_stretch_shape";
    private static final String KEY_NOTIFICATION_REMOVE_SHAPE =
            "pixelparts_app_icons_notification_remove_shape";
        private static final String KEY_NOTIFICATION_SHAPE_SCALE =
            "pixelparts_app_icons_notification_shape_scale";
        private static final String KEY_APP_ICONS_SHAPE_BACKGROUND_TINT_MODE =
            "pixelparts_app_icons_shape_background_tint_mode";
        private static final String KEY_APP_ICONS_SHAPE_BACKGROUND_TINT_COLOR =
            "pixelparts_app_icons_shape_background_tint_color";
        private static final String KEY_APP_ICONS_SHAPE_FOREGROUND_TINT_MODE =
            "pixelparts_app_icons_shape_foreground_tint_mode";
        private static final String KEY_APP_ICONS_SHAPE_FOREGROUND_TINT_COLOR =
            "pixelparts_app_icons_shape_foreground_tint_color";
    private static final String ICON_RELOAD_ACTION = "com.pixelparts.intent.action.RELOAD_ICONS";
    private static final int ICON_SHAPE_DEFAULT = 0;
    private static final int ICON_SHAPE_STRETCH = 1;
    private static final int ICON_SHAPE_REMOVE = 2;
        private static final int ICON_TINT_OFF = 0;
        private static final int ICON_TINT_CUSTOM = 1;
        private static final int ICON_TINT_AUTO = 2;
        private static final float DEFAULT_SHAPE_SCALE_PERCENT = 72f;
        private static final float MIN_SHAPE_SCALE_PERCENT = 0f;
        private static final float MAX_SHAPE_SCALE_PERCENT = 200f;
    private static final int BASE_ICON_FACTORY_MODE_HARDWARE = 2;
        private static final File ICON_MAP_FILE =
            new File("/data/pixelparts/IconsManager/icon_map.json");

    private static volatile boolean reloadReceiverRegistered;
    private static WeakReference<Object> providerRef = new WeakReference<>(null);
        private static final Object shapeOverrideLock = new Object();
        private static final ConcurrentHashMap<String, ShapeOverride> shapeOverrides =
            new ConcurrentHashMap<>();
        private static volatile long shapeOverridesLastModified = Long.MIN_VALUE;

    @Override
    public String getHookId() {
        return "NotificationIconShapeHook";
    }

    @Override
    public int getPriority() {
        return 64;
    }

    @Override
    public boolean isEnabled(Context context) {
        return true;
    }

    @Override
    protected void onInit(ClassLoader classLoader) {
        try {
            Class<?> providerClass = XposedHelpers.findClass(
                    "com.android.systemui.statusbar.notification.row.icon.AppIconProviderImpl",
                    classLoader);
            Class<?> iconFactoryClass = XposedHelpers.findClass(
                    "com.android.launcher3.icons.BaseIconFactory",
                    classLoader);

            hookProviderCacheRegistration(providerClass);
            hookFetchAppIconBitmapInfo(classLoader, providerClass, iconFactoryClass);
            log("Hooked notification app icon shape controls");
        } catch (Throwable t) {
            logError("Unable to hook notification app icons", t);
        }
    }

    private void hookProviderCacheRegistration(Class<?> providerClass) {
        try {
            XposedHelpers.findAndHookMethod(
                    providerClass,
                    "getOrFetchAppIcon",
                    String.class,
                    UserHandle.class,
                    String.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            rememberProvider(param.thisObject);
                        }
                    });
        } catch (Throwable t) {
            logError("Unable to hook AppIconProviderImpl.getOrFetchAppIcon", t);
        }

        try {
            XposedHelpers.findAndHookMethod(
                    providerClass,
                    "getOrFetchSkeletonAppIcon",
                    String.class,
                    UserHandle.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            rememberProvider(param.thisObject);
                        }
                    });
        } catch (Throwable t) {
            logError("Unable to hook AppIconProviderImpl.getOrFetchSkeletonAppIcon", t);
        }
    }

    private void hookFetchAppIconBitmapInfo(
            ClassLoader classLoader, Class<?> providerClass, Class<?> iconFactoryClass) {
        XposedHelpers.findAndHookMethod(
                providerClass,
                "fetchAppIconBitmapInfo",
                iconFactoryClass,
                String.class,
                UserHandle.class,
                boolean.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        Context context = getProviderContext(param.thisObject);
                        rememberProvider(param.thisObject);
                        String packageName = (String) param.args[1];
                        ShapeConfig shapeConfig = getNotificationIconShapeConfig(context, packageName);
                        if (context == null || shapeConfig.mode == ICON_SHAPE_DEFAULT) {
                            return;
                        }

                        UserHandle userHandle = (UserHandle) param.args[2];
                        boolean allowProfileBadge = (Boolean) param.args[3];
                        if (TextUtils.isEmpty(packageName)
                                || !hasPixelPartsIconOverride(context, packageName)) {
                            return;
                        }

                        try {
                            ApplicationInfo appInfo = getApplicationInfoAsUser(
                                    context, packageName, userHandle);
                            Drawable icon = appInfo.loadUnbadgedIcon(context.getPackageManager());
                            Object options = createIconOptions(
                                    classLoader, context, userHandle, allowProfileBadge);

                            if (shapeConfig.mode == ICON_SHAPE_REMOVE) {
                                applyNoWrapIconOptions(options);
                            } else if (shapeConfig.mode == ICON_SHAPE_STRETCH) {
                                int backgroundTint = resolveBackgroundTint(icon, shapeConfig);
                                icon = new AdaptiveIconDrawable(
                                        new ColorDrawable(backgroundTint),
                                        new ScaledForegroundDrawable(
                                                icon,
                                                shapeConfig.scale,
                                                resolveForegroundTint(icon, shapeConfig, backgroundTint)));
                                applyNoWrapIconOptions(options);
                            }

                            Object bitmapInfo = XposedHelpers.callMethod(
                                    param.args[0], "createBadgedIconBitmap", icon, options);
                            param.setResult(bitmapInfo);
                        } catch (Throwable t) {
                            logError("Unable to apply notification icon shape for " + packageName, t);
                        }
                    }
                });
    }

    private static Object createIconOptions(
            ClassLoader classLoader,
            Context context,
            UserHandle userHandle,
            boolean allowProfileBadge) throws Throwable {
        Class<?> optionsClass = XposedHelpers.findClass(
                "com.android.launcher3.icons.BaseIconFactory$IconOptions", classLoader);
        Object options = XposedHelpers.newInstance(optionsClass);

        Object userIconInfo = allowProfileBadge
                ? fetchUserIconInfo(classLoader, context, userHandle)
                : newMainUserIconInfo(classLoader, userHandle);
        callMethodIfPresent(options, "setUser", userIconInfo);
        setObjectFieldIfPresent(options, "userIconInfo", userIconInfo);

        callMethodIfPresent(options, "setBitmapGenerationMode", BASE_ICON_FACTORY_MODE_HARDWARE);
        setBooleanFieldIfPresent(options, "useHardware", true);
        setBooleanFieldIfPresent(options, "addShadows", false);

        callMethodIfPresent(options, "setExtractedColor", Color.BLUE);
        setObjectFieldIfPresent(options, "extractedColor", Integer.valueOf(Color.BLUE));
        return options;
    }

    private static Object fetchUserIconInfo(
            ClassLoader classLoader, Context context, UserHandle userHandle) {
        Class<?> utilsClass = XposedHelpers.findClass(
                "com.android.settingslib.Utils", classLoader);
        return XposedHelpers.callStaticMethod(utilsClass, "fetchUserIconInfo", context, userHandle);
    }

    private static Object newMainUserIconInfo(ClassLoader classLoader, UserHandle userHandle) {
        Class<?> userIconInfoClass = XposedHelpers.findClass(
                "com.android.launcher3.util.UserIconInfo", classLoader);
        return XposedHelpers.newInstance(userIconInfoClass, userHandle, 0);
    }

    private static ApplicationInfo getApplicationInfoAsUser(
            Context context, String packageName, UserHandle userHandle) {
        return (ApplicationInfo) XposedHelpers.callMethod(
                context.getPackageManager(),
                "getApplicationInfoAsUser",
                packageName,
                PackageManager.MATCH_UNINSTALLED_PACKAGES,
                userHandle.getIdentifier());
    }

    private void rememberProvider(Object provider) {
        if (provider == null) {
            return;
        }
        providerRef = new WeakReference<>(provider);
        maybeRegisterReloadReceiver(getProviderContext(provider));
    }

    private void maybeRegisterReloadReceiver(Context context) {
        if (context == null) {
            return;
        }
        synchronized (NotificationIconShapeHook.class) {
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
                                resetShapeOverrideCache();
                                purgeProviderCache();
                            }
                        }
                    },
                    new IntentFilter(ICON_RELOAD_ACTION),
                    Context.RECEIVER_EXPORTED);
        } catch (Throwable t) {
            synchronized (NotificationIconShapeHook.class) {
                reloadReceiverRegistered = false;
            }
            logError("Unable to register notification icon reload receiver", t);
        }
    }

    private void purgeProviderCache() {
        Object provider = providerRef.get();
        if (provider == null) {
            return;
        }
        try {
            clearAppIconCache(XposedHelpers.getObjectField(provider, "standardCache"));
            clearAppIconCache(XposedHelpers.getObjectField(provider, "skeletonCache"));
        } catch (Throwable t) {
            logError("Unable to purge notification app icon cache", t);
        }
    }

    private static void clearAppIconCache(Object appIconCache) {
        if (appIconCache == null) {
            return;
        }
        clearNotifCollectionCache(XposedHelpers.getObjectField(appIconCache, "bitmapInfoCache"));
        clearNotifCollectionCache(XposedHelpers.getObjectField(appIconCache, "drawableCache"));
    }

    private static void clearNotifCollectionCache(Object cache) {
        if (cache != null) {
            XposedHelpers.callMethod(cache, "clear");
        }
    }

    private static Context getProviderContext(Object provider) {
        Context context = getContextField(provider, "sysuiContext");
        if (context != null) {
            return context;
        }
        context = getContextField(provider, "mContext");
        return context != null ? context : getContextField(provider, "context");
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

    private static ShapeConfig getNotificationIconShapeConfig(Context context, String packageName) {
        TintConfig tintConfig = getGlobalTintConfig(context);
        if (context == null) {
            return new ShapeConfig(ICON_SHAPE_DEFAULT, 1f, tintConfig);
        }
        try {
            if (Settings.Global.getInt(context.getContentResolver(), KEY_APP_ICONS_ENABLED, 1) == 0) {
                return new ShapeConfig(ICON_SHAPE_DEFAULT, 1f, tintConfig);
            }
            ShapeOverride override = getShapeOverride(packageName);
            if (override != null) {
                return new ShapeConfig(override.mode, scalePercentToFloat(override.scalePercent), tintConfig);
            }
            float scalePercent = Settings.Global.getFloat(
                    context.getContentResolver(),
                    KEY_NOTIFICATION_SHAPE_SCALE,
                    DEFAULT_SHAPE_SCALE_PERCENT);
            if (Settings.Global.getInt(
                    context.getContentResolver(), KEY_NOTIFICATION_REMOVE_SHAPE, 0) != 0) {
                return new ShapeConfig(ICON_SHAPE_REMOVE, scalePercentToFloat(scalePercent), tintConfig);
            }
            if (Settings.Global.getInt(
                    context.getContentResolver(), KEY_NOTIFICATION_STRETCH_SHAPE, 0) != 0) {
                return new ShapeConfig(ICON_SHAPE_STRETCH, scalePercentToFloat(scalePercent), tintConfig);
            }
        } catch (Throwable ignored) {
        }
        return new ShapeConfig(ICON_SHAPE_DEFAULT, 1f, tintConfig);
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

    private static int resolveBackgroundTint(Drawable drawable, ShapeConfig shapeConfig) {
        if (shapeConfig.backgroundTintMode == ICON_TINT_CUSTOM) {
            return shapeConfig.backgroundTintColor;
        }
        if (shapeConfig.backgroundTintMode == ICON_TINT_AUTO) {
            return dominantIconColor(drawable);
        }
        return Color.TRANSPARENT;
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

    private static boolean hasPixelPartsIconOverride(Context context, String packageName) {
        try {
            Class<?> appPmClass = Class.forName("android.app.ApplicationPackageManager");
            Object result = appPmClass
                    .getMethod("hasPixelPartsIconOverride", Context.class, String.class, int.class)
                    .invoke(
                            null,
                            context,
                            packageName,
                            context.getResources().getConfiguration().densityDpi);
            return result instanceof Boolean && (Boolean) result;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static ShapeOverride getShapeOverride(String packageName) {
        if (TextUtils.isEmpty(packageName)) {
            return null;
        }
        synchronized (shapeOverrideLock) {
            refreshShapeOverridesLocked();
            return shapeOverrides.get(packageName);
        }
    }

    private static void refreshShapeOverridesLocked() {
        StrictMode.ThreadPolicy oldPolicy = StrictMode.allowThreadDiskReads();
        try {
            long lastModified = ICON_MAP_FILE.isFile() ? ICON_MAP_FILE.lastModified() : 0L;
            if (shapeOverridesLastModified == lastModified) {
                return;
            }
            shapeOverridesLastModified = lastModified;
            shapeOverrides.clear();
            if (lastModified == 0L) {
                return;
            }
            try (InputStream input = new FileInputStream(ICON_MAP_FILE)) {
                JSONObject root = new JSONObject(new String(input.readAllBytes(), StandardCharsets.UTF_8));
                JSONObject overrides = root.optJSONObject("shapeOverrides");
                if (overrides == null) {
                    return;
                }
                Iterator<String> packages = overrides.keys();
                while (packages.hasNext()) {
                    String packageName = packages.next();
                    ShapeOverride override = readShapeOverride(
                            overrides.optJSONObject(packageName), "notification");
                    if (!TextUtils.isEmpty(packageName) && override != null) {
                        shapeOverrides.put(packageName, override);
                    }
                }
            } catch (IOException | JSONException ignored) {
                shapeOverrides.clear();
            }
        } finally {
            StrictMode.setThreadPolicy(oldPolicy);
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

    private static void resetShapeOverrideCache() {
        synchronized (shapeOverrideLock) {
            shapeOverridesLastModified = Long.MIN_VALUE;
            shapeOverrides.clear();
        }
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
        setObjectFieldIfPresent(options, "drawFullBleed", Boolean.FALSE);
        setBooleanFieldIfPresent(options, "isFullBleed", false);
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