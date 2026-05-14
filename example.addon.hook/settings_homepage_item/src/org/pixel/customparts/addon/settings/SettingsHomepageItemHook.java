package org.pixel.customparts.addon.settings;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
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
    private static final String TOP_LEVEL_SETTINGS_CLASS = "com.android.settings.homepage.TopLevelSettings";
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
    private static final String TITLE_RES_NAME = "settings_homepage_pixel_parts_title";
    private static final String SUMMARY_RES_NAME = "settings_homepage_pixel_parts_summary";
    private static final String ACCENT_ICON_RES_NAME = "ic_homepage_pixel_extra_parts";
    private static final int DEFAULT_CATEGORY_ORDER = -139;
    private static final int DEFAULT_PREFERENCE_ORDER = -160;

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
                    new DashboardResumeHook(classLoader)
            );
            Log.d(TAG, "DashboardFragment.onResume hook installed");
        } catch (Throwable throwable) {
            Log.e(TAG, "Failed to hook DashboardFragment.onResume", throwable);
        }
    }

    private static final class DashboardResumeHook extends XC_MethodHook {
        private final ClassLoader classLoader;

        private DashboardResumeHook(ClassLoader classLoader) {
            this.classLoader = classLoader;
        }

        @Override
        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
            Object fragment = param.thisObject;
            if (!TOP_LEVEL_SETTINGS_CLASS.equals(fragment.getClass().getName())) {
                return;
            }
            injectPixelPartsItem(fragment, classLoader);
        }
    }

    private static void injectPixelPartsItem(Object fragment, ClassLoader classLoader) throws Throwable {
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

        Drawable icon = isColoredIconEnabled(context) ? loadPixelPartsIcon(context) : loadPixelPartsAccentIcon(context);
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
        Drawable fallback = loadPixelPartsIcon(context);
        if (fallback != null) {
            fallback = fallback.mutate();
            fallback.setTint((int) 0xFF0B57D0);
        }
        return fallback;
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
