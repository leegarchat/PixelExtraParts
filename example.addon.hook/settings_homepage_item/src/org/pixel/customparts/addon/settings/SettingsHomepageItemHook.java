package org.pixel.customparts.addon.settings;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.PathShape;
import android.graphics.drawable.shapes.RectShape;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;

import org.pixel.customparts.core.IAddonHook;

import java.util.Collections;
import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;

public class SettingsHomepageItemHook implements IAddonHook {

    private static final String TAG = "PixelPartsSettingsItem";
    private static final String TARGET_PACKAGE = "com.android.settings";
    static final String TOP_LEVEL_SETTINGS_CLASS = "com.android.settings.homepage.TopLevelSettings";
    private static final String DASHBOARD_FRAGMENT_CLASS = "com.android.settings.dashboard.DashboardFragment";
    private static final String UNTITLED_CATEGORY_CLASS = "com.android.settingslib.widget.UntitledPreferenceCategory";
    private static final String HOMEPAGE_PREFERENCE_CLASS = "com.android.settings.widget.HomepagePreference";
    private static final String PREFERENCE_CATEGORY_CLASS = "androidx.preference.PreferenceCategory";
    private static final String PREFERENCE_CLASS = "androidx.preference.Preference";

    private static final String PIXEL_PARTS_PACKAGE = "org.pixel.customparts";
    private static final String PIXEL_PARTS_ACTIVITY = "org.pixel.customparts.MainActivity";
    private static final String OPEN_SETTINGS_ACTION = "org.pixel.customparts.action.OPEN_SETTINGS";
    private static final String CATEGORY_KEY = "top_level_pixel_parts_category";
    private static final String EVOLUTION_CATEGORY_KEY = "top_level_evolution_category";
    private static final String PREFERENCE_KEY = "top_level_pixel_parts";
    private static final String EXTRA_CREATED_BY_ADDON = "pixelparts_created_by_settings_homepage_item";
    private static final String SETTING_ENABLED = "settings_homepage_item_enabled";
    private static final String SETTING_CATEGORY_ORDER = "settings_homepage_item_category_order";
    private static final String SETTING_COLORED_ICON = "settings_homepage_item_colored_icon";
    private static final String SETTING_BACKGROUND_COLOR_MODE = "settings_homepage_item_background_color_mode";
    private static final String SETTING_ICON_COLOR_MODE = "settings_homepage_item_icon_color_mode";
    private static final String SETTING_BACKGROUND_CUSTOM_COLOR = "settings_homepage_item_background_custom_hex";
    private static final String SETTING_ICON_CUSTOM_COLOR = "settings_homepage_item_icon_custom_hex";
    private static final String TITLE_RES_NAME = "settings_homepage_pixel_parts_title";
    private static final String SUMMARY_RES_NAME = "settings_homepage_pixel_parts_summary";
    private static final String ACCENT_ICON_RES_NAME = "ic_homepage_pixel_extra_parts";
    private static final String COLOR_MODE_AUTO = "auto";
    private static final String COLOR_MODE_CUSTOM = "custom";
    private static final int DEFAULT_CATEGORY_ORDER = -139;
    private static final int DEFAULT_PREFERENCE_ORDER = -160;
    private static final int DEFAULT_ICON_SIZE_DP = 48;
    private static final int DEFAULT_ICON_INSET_DP = 12;
    private static final float SYSTEM_ICON_MASK_VIEWPORT = 100f;
    private static final int FALLBACK_BACKGROUND_COLOR = (int) 0xFF67DDF7;
    private static final int FALLBACK_FOREGROUND_COLOR = (int) 0xFF0B3D4F;

    @Override
    public String getId() {
        return "settings_homepage_item";
    }

    @Override
    public String getName() {
        return "Settings Homepage Item";
    }

    @Override
    public String getAuthor() {
        return "LeeGarChat";
    }

    @Override
    public String getDescription() {
        return "Injects Pixel Extra Parts into a separate top-level Settings homepage area.";
    }

    @Override
    public String getVersion() {
        return "1.0-test";
    }

    @Override
    public Set<String> getTargetPackages() {
        return Collections.singleton(TARGET_PACKAGE);
    }

    @Override
    public int getPriority() {
        return 900;
    }

    @Override
    public void handleLoadPackage(Context context, ClassLoader classLoader, String packageName) {
        if (!TARGET_PACKAGE.equals(packageName)) {
            return;
        }

        try {
            XposedHelpers.findAndHookMethod(
                    DASHBOARD_FRAGMENT_CLASS,
                    classLoader,
                    "onResume",
                    new SettingsHomepageDashboardResumeHook(classLoader)
            );
            Log.d(TAG, "DashboardFragment.onResume hook installed");
        } catch (Throwable throwable) {
            Log.e(TAG, "Failed to hook DashboardFragment.onResume", throwable);
        }
    }

    static void injectPixelPartsItem(Object fragment, ClassLoader classLoader) throws Throwable {
        Context context = (Context) XposedHelpers.callMethod(fragment, "getContext");
        if (context == null || !isPixelPartsInstalled(context)) {
            return;
        }

        Object screen = XposedHelpers.callMethod(fragment, "getPreferenceScreen");
        if (screen == null) {
            return;
        }

        if (!isFeatureEnabled(context)) {
            restoreDefaultPlacement(screen);
            return;
        }

        Object category = XposedHelpers.callMethod(screen, "findPreference", CATEGORY_KEY);
        int categoryOrder = getIntSetting(context, SETTING_CATEGORY_ORDER, DEFAULT_CATEGORY_ORDER);
        if (category == null) {
            category = createCategory(context, classLoader);
            XposedHelpers.callMethod(category, "setKey", CATEGORY_KEY);
            XposedHelpers.callMethod(category, "setOrder", categoryOrder);
            XposedHelpers.callMethod(category, "setSelectable", false);
            XposedHelpers.callMethod(screen, "addPreference", category);
        } else {
            XposedHelpers.callMethod(category, "setOrder", categoryOrder);
        }

        Object preference = XposedHelpers.callMethod(screen, "findPreference", PREFERENCE_KEY);
        if (preference == null) {
            preference = createHomepagePreference(context, classLoader);
            XposedHelpers.callMethod(preference, "setKey", PREFERENCE_KEY);
            markCreatedByAddon(preference);
        }

        configurePreference(context, preference);
        movePreferenceToCategory(preference, category);
    }

    private static Object createCategory(Context context, ClassLoader classLoader) {
        Class<?> categoryClass = findClassOrNull(UNTITLED_CATEGORY_CLASS, classLoader);
        if (categoryClass == null) {
            categoryClass = XposedHelpers.findClass(PREFERENCE_CATEGORY_CLASS, classLoader);
        }
        return XposedHelpers.newInstance(categoryClass, context);
    }

    private static Object createHomepagePreference(Context context, ClassLoader classLoader) {
        Class<?> preferenceClass = findClassOrNull(HOMEPAGE_PREFERENCE_CLASS, classLoader);
        if (preferenceClass == null) {
            preferenceClass = XposedHelpers.findClass(PREFERENCE_CLASS, classLoader);
        }
        return XposedHelpers.newInstance(preferenceClass, context);
    }

    private static void configurePreference(Context context, Object preference) {
        XposedHelpers.callMethod(preference, "setTitle", loadPixelPartsString(context, TITLE_RES_NAME, "Pixel Extra Parts"));
        XposedHelpers.callMethod(preference, "setSummary", loadPixelPartsString(context, SUMMARY_RES_NAME, "Additional improvements to your Pixel"));
        XposedHelpers.callMethod(preference, "setOrder", DEFAULT_PREFERENCE_ORDER);
        XposedHelpers.callMethod(preference, "setSelectable", true);
        XposedHelpers.callMethod(preference, "setIntent", createPixelPartsIntent());

        Drawable icon = isColoredIconEnabled(context) ? loadPixelPartsIcon(context) : createSettingsStyledIcon(context);
        if (icon == null) {
            icon = loadPixelPartsIcon(context);
        }
        if (icon != null) {
            XposedHelpers.callMethod(preference, "setIcon", icon);
        }

        Object helper = callMethodOrNull(preference, "getHelper");
        if (helper != null) {
            callMethodOrNull(helper, "setIconVisible", true);
        }
    }

    private static void movePreferenceToCategory(Object preference, Object category) {
        Object currentParent = callMethodOrNull(preference, "getParent");
        if (currentParent == category) {
            return;
        }

        if (currentParent != null) {
            XposedHelpers.callMethod(currentParent, "removePreference", preference);
        }
        XposedHelpers.callMethod(category, "addPreference", preference);
        Log.d(TAG, "Pixel Extra Parts homepage item injected into a separate category");
    }

    private static void restoreDefaultPlacement(Object screen) {
        Object category = XposedHelpers.callMethod(screen, "findPreference", CATEGORY_KEY);
        if (category == null) {
            return;
        }

        Object preference = XposedHelpers.callMethod(category, "findPreference", PREFERENCE_KEY);
        if (preference != null) {
            XposedHelpers.callMethod(category, "removePreference", preference);
            if (!wasCreatedByAddon(preference)) {
                Object evolutionCategory = XposedHelpers.callMethod(screen, "findPreference", EVOLUTION_CATEGORY_KEY);
                if (evolutionCategory != null) {
                    XposedHelpers.callMethod(evolutionCategory, "addPreference", preference);
                }
            }
        }

        int categorySize = getPreferenceCount(category);
        if (categorySize <= 0) {
            XposedHelpers.callMethod(screen, "removePreference", category);
        }
    }

    private static Intent createPixelPartsIntent() {
        Intent intent = new Intent(OPEN_SETTINGS_ACTION);
        intent.setPackage(PIXEL_PARTS_PACKAGE);
        intent.setClassName(PIXEL_PARTS_PACKAGE, PIXEL_PARTS_ACTIVITY);
        return intent;
    }

    private static Drawable loadPixelPartsIcon(Context context) {
        try {
            return context.getPackageManager().getApplicationIcon(PIXEL_PARTS_PACKAGE);
        } catch (PackageManager.NameNotFoundException ignored) {
            return null;
        }
    }

    private static Drawable loadPixelPartsAccentIcon(Context context) {
        Context pixelPartsContext = createPixelPartsContext(context);
        if (pixelPartsContext != null) {
            int iconResId = pixelPartsContext.getResources().getIdentifier(
                    ACCENT_ICON_RES_NAME,
                    "drawable",
                    PIXEL_PARTS_PACKAGE
            );
            if (iconResId != 0) {
                return pixelPartsContext.getDrawable(iconResId);
            }
        }
        return loadPixelPartsIcon(context);
    }

    private static Drawable createSettingsStyledIcon(Context context) {
        Drawable foreground = loadPixelPartsAccentIcon(context);
        if (foreground == null) {
            return null;
        }

        int backgroundColor = resolveHomepageBackgroundColor(context);
        int foregroundColor = resolveHomepageForegroundColor(context);
        Drawable background = createHomepageIconBackground(context, backgroundColor);
        foreground = foreground.mutate();
        foreground.setTint(foregroundColor);

        int iconSize = dp(context, DEFAULT_ICON_SIZE_DP);
        int inset = dp(context, DEFAULT_ICON_INSET_DP);
        LayerDrawable icon = new LayerDrawable(new Drawable[]{background, foreground});
        icon.setLayerInset(1, inset, inset, inset, inset);
        setLayerSize(icon, 0, iconSize, iconSize);
        setLayerSize(icon, 1, Math.max(1, iconSize - inset * 2), Math.max(1, iconSize - inset * 2));
        return icon;
    }

    private static Drawable createHomepageIconBackground(Context context, int backgroundColor) {
        int iconSize = dp(context, DEFAULT_ICON_SIZE_DP);
        ShapeDrawable background = new ShapeDrawable(resolveSystemIconMask(context));
        background.getPaint().setColor(backgroundColor);
        background.setIntrinsicWidth(iconSize);
        background.setIntrinsicHeight(iconSize);
        return background;
    }

    private static android.graphics.drawable.shapes.Shape resolveSystemIconMask(Context context) {
        int resourceId = context.getResources().getIdentifier("config_icon_mask", "string", "android");
        if (resourceId != 0) {
            try {
                Path path = (Path) Class.forName("android.util.PathParser")
                        .getMethod("createPathFromPathData", String.class)
                        .invoke(null, context.getResources().getString(resourceId));
                if (path != null) {
                    return new PathShape(path, SYSTEM_ICON_MASK_VIEWPORT, SYSTEM_ICON_MASK_VIEWPORT);
                }
            } catch (Throwable throwable) {
                Log.w(TAG, "Failed to load system icon mask", throwable);
            }
        }
        return new RectShape();
    }

    private static int resolveHomepageBackgroundColor(Context context) {
        int autoColor = resolveSettingsColor(context, "homepage_evolution_background", FALLBACK_BACKGROUND_COLOR);
        String mode = getStringSetting(context, SETTING_BACKGROUND_COLOR_MODE, COLOR_MODE_AUTO).trim();
        if (COLOR_MODE_CUSTOM.equals(mode)) {
            return parseHexColor(getStringSetting(context, SETTING_BACKGROUND_CUSTOM_COLOR, "#67DDF7"), autoColor);
        }
        if (COLOR_MODE_AUTO.equals(mode)) {
            return autoColor;
        }
        return parseHexColor(mode, autoColor);
    }

    private static int resolveHomepageForegroundColor(Context context) {
        int autoColor = resolveSettingsColor(context, "homepage_evolution_foreground", FALLBACK_FOREGROUND_COLOR);
        String mode = getStringSetting(context, SETTING_ICON_COLOR_MODE, COLOR_MODE_AUTO).trim();
        if (COLOR_MODE_CUSTOM.equals(mode)) {
            return parseHexColor(getStringSetting(context, SETTING_ICON_CUSTOM_COLOR, "#0B3D4F"), autoColor);
        }
        if (COLOR_MODE_AUTO.equals(mode)) {
            return autoColor;
        }
        return parseHexColor(mode, autoColor);
    }

    private static int resolveSettingsColor(Context context, String resourceName, int fallback) {
        int resourceId = context.getResources().getIdentifier(resourceName, "color", TARGET_PACKAGE);
        if (resourceId == 0) {
            return fallback;
        }
        try {
            return context.getColor(resourceId);
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private static int parseHexColor(String rawValue, int fallback) {
        if (rawValue == null) {
            return fallback;
        }
        String value = rawValue.trim();
        if (value.isEmpty()) {
            return fallback;
        }
        if (!value.startsWith("#")) {
            value = "#" + value;
        }
        try {
            return Color.parseColor(value);
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private static void setLayerSize(LayerDrawable drawable, int index, int width, int height) {
        try {
            drawable.setLayerSize(index, width, height);
        } catch (Throwable ignored) {
        }
    }

    private static String loadPixelPartsString(Context context, String resourceName, String fallback) {
        Context pixelPartsContext = createPixelPartsContext(context);
        if (pixelPartsContext == null) {
            return fallback;
        }
        int stringResId = pixelPartsContext.getResources().getIdentifier(
                resourceName,
                "string",
                PIXEL_PARTS_PACKAGE
        );
        if (stringResId == 0) {
            return fallback;
        }
        return pixelPartsContext.getString(stringResId);
    }

    private static Context createPixelPartsContext(Context context) {
        try {
            return context.createPackageContext(PIXEL_PARTS_PACKAGE, Context.CONTEXT_IGNORE_SECURITY);
        } catch (PackageManager.NameNotFoundException ignored) {
            return null;
        }
    }

    private static boolean isPixelPartsInstalled(Context context) {
        try {
            context.getPackageManager().getPackageInfo(PIXEL_PARTS_PACKAGE, 0);
            return true;
        } catch (PackageManager.NameNotFoundException ignored) {
            return false;
        }
    }

    private static boolean isFeatureEnabled(Context context) {
        return getIntSetting(context, SETTING_ENABLED, 1) != 0;
    }

    private static boolean isColoredIconEnabled(Context context) {
        return getIntSetting(context, SETTING_COLORED_ICON, 0) != 0;
    }

    private static int getIntSetting(Context context, String key, int defaultValue) {
        try {
            return Settings.Global.getInt(context.getContentResolver(), key, defaultValue);
        } catch (Throwable ignored) {
            return defaultValue;
        }
    }

    private static String getStringSetting(Context context, String key, String defaultValue) {
        try {
            String value = Settings.Global.getString(context.getContentResolver(), key);
            return value == null || value.trim().isEmpty() ? defaultValue : value;
        } catch (Throwable ignored) {
            return defaultValue;
        }
    }

    private static void markCreatedByAddon(Object preference) {
        Bundle extras = (Bundle) callMethodOrNull(preference, "getExtras");
        if (extras != null) {
            extras.putBoolean(EXTRA_CREATED_BY_ADDON, true);
        }
    }

    private static boolean wasCreatedByAddon(Object preference) {
        Bundle extras = (Bundle) callMethodOrNull(preference, "getExtras");
        return extras != null && extras.getBoolean(EXTRA_CREATED_BY_ADDON, false);
    }

    private static int getPreferenceCount(Object preferenceGroup) {
        Object count = callMethodOrNull(preferenceGroup, "getPreferenceCount");
        return count instanceof Integer ? (Integer) count : 0;
    }

    private static Class<?> findClassOrNull(String className, ClassLoader classLoader) {
        try {
            return XposedHelpers.findClass(className, classLoader);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object callMethodOrNull(Object target, String methodName, Object... args) {
        try {
            return XposedHelpers.callMethod(target, methodName, args);
        } catch (Throwable ignored) {
            return null;
        }
    }
}

final class SettingsHomepageDashboardResumeHook extends XC_MethodHook {
    private final ClassLoader classLoader;

    SettingsHomepageDashboardResumeHook(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    @Override
    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
        Object fragment = param.thisObject;
        if (!SettingsHomepageItemHook.TOP_LEVEL_SETTINGS_CLASS.equals(fragment.getClass().getName())) {
            return;
        }
        SettingsHomepageItemHook.injectPixelPartsItem(fragment, classLoader);
    }
}
