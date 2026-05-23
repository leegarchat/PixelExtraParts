package org.pixel.customparts.addon.settings;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.ShapeDrawable;
import android.provider.Settings;
import android.util.Log;
import android.util.TypedValue;

import org.pixel.customparts.core.IAddonHook;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;

public class SettingsIconStyleOverrideHook implements IAddonHook {

    private static final String TAG = "PixelPartsSettingsIcon";
    private static final String TARGET_PACKAGE = "com.android.settings";
    private static final String ICON_TINTER_CLASS = "com.android.evolution.utils.IconTinterUtils";
    private static final String PREFERENCE_CLASS = "androidx.preference.Preference";
    private static final String PREFERENCE_SCREEN_CLASS = "androidx.preference.PreferenceScreen";
    private static final String SETTING_RESTORE_STOCK_ICONS = "pixelparts_settings_icons_ignore_external_shape";
    private static final String ADAPTIVE_ICON_SHAPE_DRAWABLE_CLASS = "com.android.settingslib.widget.AdaptiveIconShapeDrawable";
    private static final String APP_PREFERENCE_CLASS = "com.android.settingslib.widget.AppPreference";
    private static final String FAST_BITMAP_DRAWABLE_CLASS = "com.android.launcher3.icons.FastBitmapDrawable";
    private static final String ADAPTIVE_ICON_DRAWABLE_CLASS = "android.graphics.drawable.AdaptiveIconDrawable";
    private static final String ICON_STYLE = "settings_icon_style";
    private static final String ICON_RANDOM_COLORS = "settings_icon_random_colors";

    private static final int ICON_STYLE_MATERIAL_EXPRESSIVE_ICON = 0;
    private static final int ICON_STYLE_SOLID_BG_WHITE_ICON = 1;
    private static final int ICON_STYLE_GRADIENT_BG_WHITE_ICON = 2;
    private static final int ICON_STYLE_ACCENT_OUTLINE_ACCENT_ICON = 3;
    private static final int ICON_STYLE_SOLID_OUTLINE_SOLID_ICON = 4;
    private static final int ICON_STYLE_COLOR_ICON_NO_BG = 5;
    private static final int ICON_STYLE_ACCENT_ICON = 6;

    private static final int BG_PADDING_DP = 8;
    private static final int OUTLINE_WIDTH_DP = 2;
    private static final int ICON_LAYER_ID = 0x7F1C0001;
    private static final float SOLID_BG_SATURATION_BOOST = 0.5f;
    private static final float GRADIENT_COLOR_FACTOR = 0.5f;
    private static final float GRADIENT_SATURATION_FACTOR = 0.5f;
    private static final int USER_CURRENT = -2;

    private static final String[] MATERIAL_COLOR_BG_NAMES = {
            "m3_ref_palette_blue90",
            "m3_ref_palette_pink90",
            "m3_ref_palette_orange90",
            "m3_ref_palette_yellow90",
            "m3_ref_palette_blue_variant90",
            "m3_ref_palette_green90",
            "m3_ref_palette_grey90",
            "m3_ref_palette_cyan90",
            "m3_ref_palette_red90",
            "m3_ref_palette_purple90"
    };
    private static final String[] MATERIAL_COLOR_FG_NAMES = {
            "m3_ref_palette_blue30",
            "m3_ref_palette_pink30",
            "m3_ref_palette_orange30",
            "m3_ref_palette_yellow30",
            "m3_ref_palette_blue_variant30",
            "m3_ref_palette_green30",
            "m3_ref_palette_grey30",
            "m3_ref_palette_cyan30",
            "m3_ref_palette_red30",
            "m3_ref_palette_purple30"
    };
    private static final int[] FALLBACK_BG_COLORS = {
            0xFFBBDEFB, 0xFFF8BBD0, 0xFFFFE0B2, 0xFFFFF9C4, 0xFFC5CAE9,
            0xFFC8E6C9, 0xFFF5F5F5, 0xFFB2EBF2, 0xFFFFCDD2, 0xFFE1BEE7
    };
    private static final int[] FALLBACK_FG_COLORS = {
            0xFF1565C0, 0xFFC2185B, 0xFFE65100, 0xFFF9A825, 0xFF283593,
            0xFF2E7D32, 0xFF424242, 0xFF00838F, 0xFFC62828, 0xFF6A1B9A
    };
    private static final Map<String, IconColors> colorCache = new HashMap<>();
    private static final Random random = new Random();

    @Override
    public String getId() {
        return "settings_icon_style_override";
    }

    @Override
    public String getName() {
        return "Settings Icon Style Override";
    }

    @Override
    public String getAuthor() {
        return "PixelExtraParts";
    }

    @Override
    public String getDescription() {
        return "Restores the stock Settings icon pipeline and lets the global adaptive icon mask define shape.";
    }

    @Override
    public String getVersion() {
        return "1.0";
    }

    @Override
    public Set<String> getTargetPackages() {
        return Collections.singleton(TARGET_PACKAGE);
    }

    @Override
    public int getPriority() {
        return 950;
    }

    @Override
    public void handleLoadPackage(Context context, ClassLoader classLoader, String packageName) {
        if (!TARGET_PACKAGE.equals(packageName)) {
            return;
        }

        try {
            Class<?> preferenceScreenClass = XposedHelpers.findClass(PREFERENCE_SCREEN_CLASS, classLoader);
            Class<?> preferenceClass = XposedHelpers.findClass(PREFERENCE_CLASS, classLoader);
            XposedHelpers.findAndHookMethod(
                    ICON_TINTER_CLASS,
                    classLoader,
                    "tintIcons",
                    preferenceScreenClass,
                    Context.class,
                    new TintIconsHook(classLoader)
            );
            XposedHelpers.findAndHookMethod(
                    ICON_TINTER_CLASS,
                    classLoader,
                    "tintSinglePreferenceIcon",
                    preferenceClass,
                    Context.class,
                    new TintSinglePreferenceIconHook(classLoader)
                );
            Log.d(TAG, "Settings icon provider override installed");
        } catch (Throwable throwable) {
            Log.w(TAG, "Failed to hook Settings icon provider", throwable);
        }
    }

    private static final class TintIconsHook extends XC_MethodHook {
        private final ClassLoader classLoader;

        private TintIconsHook(ClassLoader classLoader) {
            this.classLoader = classLoader;
        }

        @Override
        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
            Context context = findContext(param.args);
            if (!isRestoreStockIconsEnabled(context)) {
                return;
            }
            param.setResult(null);

            IconStyleConfig config = IconStyleConfig.from(context);
            if (config.randomColors) {
                colorCache.clear();
            }
            tintPreferenceTree(param.args[0], context, config, classLoader);
        }
    }

    private static final class TintSinglePreferenceIconHook extends XC_MethodHook {
        private final ClassLoader classLoader;

        private TintSinglePreferenceIconHook(ClassLoader classLoader) {
            this.classLoader = classLoader;
        }

        @Override
        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
            Context context = findContext(param.args);
            if (!isRestoreStockIconsEnabled(context)) {
                return;
            }
            param.setResult(null);

            Object preference = param.args[0];
            if (getPreferenceIcon(preference) != null) {
                tintPreferenceIcon(preference, context, IconStyleConfig.from(context), classLoader);
            }
        }
    }

    private static final class IconStyleConfig {
        private final int iconStyle;
        private final boolean randomColors;

        private IconStyleConfig(int iconStyle, boolean randomColors) {
            this.iconStyle = iconStyle;
            this.randomColors = randomColors;
        }

        private static IconStyleConfig from(Context context) {
            int iconStyle = getSystemIntForCurrentUser(context, ICON_STYLE, ICON_STYLE_MATERIAL_EXPRESSIVE_ICON);
            boolean randomColors = getSystemIntForCurrentUser(context, ICON_RANDOM_COLORS, 0) == 1;
            return new IconStyleConfig(iconStyle, randomColors);
        }
    }

    private static final class IconColors {
        private final int bg;
        private final int fg;

        private IconColors(int bg, int fg) {
            this.bg = bg;
            this.fg = fg;
        }
    }

    private static void tintPreferenceTree(
            Object preference,
            Context context,
            IconStyleConfig config,
            ClassLoader classLoader
    ) {
        tintPreferenceIcon(preference, context, config, classLoader);
        int preferenceCount = getPreferenceCount(preference);
        if (preferenceCount <= 0) {
            return;
        }
        for (int index = 0; index < preferenceCount; index++) {
            Object child = callMethodOrNull(preference, "getPreference", index);
            if (child != null) {
                tintPreferenceTree(child, context, config, classLoader);
            }
        }
    }

    private static void tintPreferenceIcon(
            Object preference,
            Context context,
            IconStyleConfig config,
            ClassLoader classLoader
    ) {
        if (preference == null || context == null) {
            return;
        }
        Drawable rawIcon = getPreferenceIcon(preference);
        if (rawIcon == null) {
            return;
        }

        Drawable originalIcon = unwrapToOriginalIcon(rawIcon);
        if (isAppIconPreference(preference) || isAppIconDrawable(originalIcon)) {
            if (originalIcon != rawIcon) {
                setPreferenceIcon(preference, originalIcon);
            }
            return;
        }

        Drawable icon = copyDrawable(originalIcon, context).mutate();
        IconColors colors = config.randomColors
                ? getRandomColors(context)
                : getCachedColorsForPreference(getPreferenceKey(preference), context);

        switch (config.iconStyle) {
            case ICON_STYLE_MATERIAL_EXPRESSIVE_ICON:
                applyIconWithAdaptiveBackground(preference, icon, colors.bg, colors.fg, context, classLoader);
                break;
            case ICON_STYLE_SOLID_BG_WHITE_ICON:
                applyIconWithAdaptiveBackground(
                        preference,
                        icon,
                        increaseSaturation(colors.bg, SOLID_BG_SATURATION_BOOST),
                        Color.WHITE,
                        context,
                        classLoader
                );
                break;
            case ICON_STYLE_GRADIENT_BG_WHITE_ICON:
                applyIconWithAdaptiveGradient(preference, icon, colors.bg, context, classLoader);
                break;
            case ICON_STYLE_COLOR_ICON_NO_BG:
                applyTintOnly(preference, icon, colors.bg);
                break;
            case ICON_STYLE_ACCENT_OUTLINE_ACCENT_ICON:
                applyIconWithAdaptiveOutline(
                        preference,
                        icon,
                        resolveThemeColorAccent(context),
                        true,
                        context,
                        classLoader
                );
                break;
            case ICON_STYLE_SOLID_OUTLINE_SOLID_ICON:
                applyIconWithAdaptiveOutline(preference, icon, colors.bg, false, context, classLoader);
                break;
            case ICON_STYLE_ACCENT_ICON:
            default:
                applyTintOnly(preference, icon, resolveThemeColorAccent(context));
                break;
        }
    }

    private static void applyIconWithAdaptiveBackground(
            Object preference,
            Drawable icon,
            int bgColor,
            int iconColor,
            Context context,
            ClassLoader classLoader
    ) {
        ShapeDrawable bgDrawable = createAdaptiveShapeDrawable(context, classLoader);
        if (bgDrawable == null) {
            applyTintOnly(preference, icon, iconColor);
            return;
        }
        Paint paint = bgDrawable.getPaint();
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(bgColor);
        paint.setShader(null);
        applyIconLayer(preference, bgDrawable, icon, iconColor, context);
    }

    private static void applyIconWithAdaptiveGradient(
            Object preference,
            Drawable icon,
            int baseColor,
            Context context,
            ClassLoader classLoader
    ) {
        ShapeDrawable bgDrawable = createAdaptiveShapeDrawable(context, classLoader);
        if (bgDrawable == null) {
            applyTintOnly(preference, icon, Color.WHITE);
            return;
        }
        int startColor = adjustSaturation(lightenColor(baseColor, GRADIENT_COLOR_FACTOR), -GRADIENT_SATURATION_FACTOR);
        int endColor = adjustSaturation(darkenColor(baseColor, GRADIENT_SATURATION_FACTOR), GRADIENT_COLOR_FACTOR);
        final int[] gradientColors = { startColor, baseColor, endColor };
        Paint paint = bgDrawable.getPaint();
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(null);
        bgDrawable.setShaderFactory(new ShapeDrawable.ShaderFactory() {
            @Override
            public Shader resize(int width, int height) {
                return new LinearGradient(0, 0, width, height, gradientColors, null, Shader.TileMode.CLAMP);
            }
        });
        applyIconLayer(preference, bgDrawable, icon, Color.WHITE, context);
    }

    private static void applyIconWithAdaptiveOutline(
            Object preference,
            Drawable icon,
            int outlineColor,
            boolean useAccentForIcon,
            Context context,
            ClassLoader classLoader
    ) {
        ShapeDrawable outlineDrawable = createAdaptiveShapeDrawable(context, classLoader);
        if (outlineDrawable == null) {
            applyTintOnly(preference, icon, outlineColor);
            return;
        }
        Paint paint = outlineDrawable.getPaint();
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dpToPx(context, OUTLINE_WIDTH_DP));
        paint.setColor(outlineColor);
        paint.setShader(null);

        int iconColor = useAccentForIcon ? resolveThemeColorAccent(context) : outlineColor;
        applyIconLayer(preference, outlineDrawable, icon, iconColor, context);
    }

    private static void applyIconLayer(
            Object preference,
            Drawable background,
            Drawable icon,
            int iconColor,
            Context context
    ) {
        int width = Math.max(1, icon.getIntrinsicWidth());
        int height = Math.max(1, icon.getIntrinsicHeight());
        int padding = dpToPx(context, BG_PADDING_DP);

        icon.setBounds(0, 0, width, height);
        icon.setTint(iconColor);
        icon.setTintMode(PorterDuff.Mode.SRC_ATOP);

        LayerDrawable layerDrawable = new LayerDrawable(new Drawable[] { background, icon });
        layerDrawable.setId(1, ICON_LAYER_ID);
        layerDrawable.setLayerInset(1, padding, padding, padding, padding);
        setPreferenceIcon(preference, layerDrawable);
    }

    private static void applyTintOnly(Object preference, Drawable icon, int color) {
        icon.setTint(color);
        icon.setTintMode(PorterDuff.Mode.SRC_ATOP);
        setPreferenceIcon(preference, icon);
    }

    private static ShapeDrawable createAdaptiveShapeDrawable(Context context, ClassLoader classLoader) {
        try {
            Class<?> shapeClass = XposedHelpers.findClass(ADAPTIVE_ICON_SHAPE_DRAWABLE_CLASS, classLoader);
            Object drawable = XposedHelpers.newInstance(shapeClass, context.getResources());
            return drawable instanceof ShapeDrawable ? (ShapeDrawable) drawable : null;
        } catch (Throwable throwable) {
            Log.w(TAG, "Failed to create Settings adaptive icon shape", throwable);
            return null;
        }
    }

    private static Drawable unwrapToOriginalIcon(Drawable drawable) {
        if (drawable instanceof LayerDrawable) {
            Drawable inner = ((LayerDrawable) drawable).findDrawableByLayerId(ICON_LAYER_ID);
            if (inner != null) {
                return unwrapToOriginalIcon(inner);
            }
        }
        return drawable;
    }

    private static Drawable copyDrawable(Drawable drawable, Context context) {
        if (drawable == null) {
            return null;
        }
        try {
            Drawable.ConstantState state = drawable.getConstantState();
            if (state != null) {
                return state.newDrawable(context.getResources()).mutate();
            }
        } catch (Throwable ignored) {
        }
        return drawable.mutate();
    }

    private static boolean isAppIconPreference(Object preference) {
        Class<?> current = preference != null ? preference.getClass() : null;
        while (current != null) {
            if (APP_PREFERENCE_CLASS.equals(current.getName())) {
                return true;
            }
            current = current.getSuperclass();
        }
        return false;
    }

    private static boolean isAppIconDrawable(Drawable drawable) {
        if (drawable == null) {
            return false;
        }
        Class<?> current = drawable.getClass();
        while (current != null) {
            String className = current.getName();
            if (FAST_BITMAP_DRAWABLE_CLASS.equals(className)
                    || ADAPTIVE_ICON_DRAWABLE_CLASS.equals(className)) {
                return true;
            }
            current = current.getSuperclass();
        }
        return false;
    }

    private static Drawable getPreferenceIcon(Object preference) {
        Object icon = callMethodOrNull(preference, "getIcon");
        return icon instanceof Drawable ? (Drawable) icon : null;
    }

    private static void setPreferenceIcon(Object preference, Drawable icon) {
        try {
            XposedHelpers.callMethod(preference, "setIcon", icon);
        } catch (Throwable throwable) {
            Log.w(TAG, "Failed to set Settings preference icon", throwable);
        }
    }

    private static String getPreferenceKey(Object preference) {
        Object key = callMethodOrNull(preference, "getKey");
        return key instanceof String ? (String) key : "";
    }

    private static int getPreferenceCount(Object preference) {
        Object count = callMethodOrNull(preference, "getPreferenceCount");
        return count instanceof Integer ? (Integer) count : -1;
    }

    private static IconColors getCachedColorsForPreference(String key, Context context) {
        String safeKey = key == null ? "" : key;
        IconColors cached = colorCache.get(safeKey);
        if (cached != null) {
            return cached;
        }

        int colorIndex = (safeKey.hashCode() & 0x7fffffff) % MATERIAL_COLOR_BG_NAMES.length;
        IconColors colors = getColorsAt(context, colorIndex);
        colorCache.put(safeKey, colors);
        return colors;
    }

    private static IconColors getRandomColors(Context context) {
        return getColorsAt(context, random.nextInt(MATERIAL_COLOR_BG_NAMES.length));
    }

    private static IconColors getColorsAt(Context context, int index) {
        int bg = getColorByName(context, MATERIAL_COLOR_BG_NAMES[index], FALLBACK_BG_COLORS[index]);
        int fg = getColorByName(context, MATERIAL_COLOR_FG_NAMES[index], FALLBACK_FG_COLORS[index]);
        return new IconColors(bg, fg);
    }

    private static int getColorByName(Context context, String name, int fallback) {
        try {
            Resources resources = context.getResources();
            int resourceId = resources.getIdentifier(name, "color", context.getPackageName());
            if (resourceId == 0) {
                resourceId = resources.getIdentifier(name, "color", TARGET_PACKAGE);
            }
            return resourceId != 0 ? resources.getColor(resourceId, context.getTheme()) : fallback;
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private static int getSystemIntForCurrentUser(Context context, String key, int defaultValue) {
        try {
            Object value = XposedHelpers.callStaticMethod(
                    Settings.System.class,
                    "getIntForUser",
                    context.getContentResolver(),
                    key,
                    defaultValue,
                    USER_CURRENT
            );
            return value instanceof Integer ? (Integer) value : defaultValue;
        } catch (Throwable ignored) {
            return Settings.System.getInt(context.getContentResolver(), key, defaultValue);
        }
    }

    private static int resolveThemeColorAccent(Context context) {
        TypedValue typedValue = new TypedValue();
        context.getTheme().resolveAttribute(android.R.attr.colorAccent, typedValue, true);
        return typedValue.data;
    }

    private static int adjustSaturation(int color, float saturationDelta) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[1] = clamp(hsv[1] + saturationDelta, 0f, 1f);
        return Color.HSVToColor(hsv);
    }

    private static int increaseSaturation(int color, float saturationBoost) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[1] = Math.min(1f, hsv[1] + saturationBoost);
        return Color.HSVToColor(hsv);
    }

    private static int lightenColor(int color, float factor) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[2] = Math.min(1f, hsv[2] + factor);
        return Color.HSVToColor(hsv);
    }

    private static int darkenColor(int color, float factor) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[2] = Math.max(0f, hsv[2] - factor);
        return Color.HSVToColor(hsv);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int dpToPx(Context context, int dp) {
        return (int) (dp * context.getResources().getDisplayMetrics().density);
    }

    private static boolean isRestoreStockIconsEnabled(Context context) {
        if (context == null) {
            return false;
        }
        try {
            return Settings.Global.getInt(
                    context.getContentResolver(),
                    SETTING_RESTORE_STOCK_ICONS,
                    1
            ) != 0;
        } catch (Throwable ignored) {
            return true;
        }
    }

    private static Context findContext(Object[] args) {
        if (args == null) {
            return null;
        }
        for (Object arg : args) {
            Context context = asContext(arg);
            if (context != null) {
                return context;
            }
        }
        for (Object arg : args) {
            Context context = getPreferenceContext(arg);
            if (context != null) {
                return context;
            }
        }
        return null;
    }

    private static Context getPreferenceContext(Object preference) {
        Object context = callMethodOrNull(preference, "getContext");
        return asContext(context);
    }

    private static Context asContext(Object value) {
        return value instanceof Context ? (Context) value : null;
    }

    private static String asString(Object value) {
        return value instanceof String ? (String) value : null;
    }

    private static Object callMethodOrNull(Object target, String methodName, Object... args) {
        if (target == null) {
            return null;
        }
        try {
            return XposedHelpers.callMethod(target, methodName, args);
        } catch (Throwable ignored) {
            return null;
        }
    }
}