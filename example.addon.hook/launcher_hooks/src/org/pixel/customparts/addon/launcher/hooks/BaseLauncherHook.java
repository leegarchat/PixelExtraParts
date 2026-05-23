package org.pixel.customparts.addon.launcher.hooks;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.provider.Settings;
import android.util.Log;

public abstract class BaseLauncherHook {
    protected ClassLoader hostClassLoader;

    public abstract String getHookId();

    public int getPriority() {
        return 0;
    }

    public boolean isEnabled(Context context) {
        return true;
    }

    public final void init(ClassLoader classLoader) {
        hostClassLoader = classLoader;
        try {
            onInit(classLoader);
        } catch (Throwable throwable) {
            logError("Error during initialization", throwable);
        }
    }

    protected void onInit(ClassLoader classLoader) {
    }

    public void onActivityCreated(Activity activity) {
    }

    public void onActivityResumed(Activity activity) {
    }

    public void onActivityPaused(Activity activity) {
    }

    public void onActivityDestroyed(Activity activity) {
    }

    protected void log(String message) {
        Log.d(getHookId(), message);
    }

    protected void logError(String message, Throwable throwable) {
        Log.e(getHookId(), message, throwable);
    }

    protected boolean isSettingEnabled(Context context, String key) {
        return isSettingEnabled(context, key, false);
    }

    protected boolean isSettingEnabled(Context context, String key, boolean defaultValue) {
        if (context == null) {
            return defaultValue;
        }
        try {
            return Settings.Global.getInt(context.getContentResolver(), key, defaultValue ? 1 : 0) != 0;
        } catch (Throwable ignored) {
            return defaultValue;
        }
    }

    protected int getIntSetting(Context context, String key, int defaultValue) {
        if (context == null) {
            return defaultValue;
        }
        try {
            return Settings.Global.getInt(context.getContentResolver(), key, defaultValue);
        } catch (Throwable ignored) {
            try {
                return parseIntOrColor(Settings.Global.getString(context.getContentResolver(), key), defaultValue);
            } catch (Throwable ignoredAgain) {
                return defaultValue;
            }
        }
    }

    protected float getFloatSetting(Context context, String key, float defaultValue) {
        if (context == null) {
            return defaultValue;
        }
        try {
            return Settings.Global.getFloat(context.getContentResolver(), key, defaultValue);
        } catch (Throwable ignored) {
            return defaultValue;
        }
    }

    protected String getStringSetting(Context context, String key, String defaultValue) {
        if (context == null) {
            return defaultValue;
        }
        try {
            String value = Settings.Global.getString(context.getContentResolver(), key);
            return value != null ? value : defaultValue;
        } catch (Throwable ignored) {
            return defaultValue;
        }
    }

    private static int parseIntOrColor(String value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return defaultValue;
        }
        try {
            if (trimmed.charAt(0) == '#') {
                return Color.parseColor(trimmed);
            }
            if (trimmed.indexOf(',') >= 0) {
                String[] parts = trimmed.split(",");
                int r = parts.length > 0 ? Integer.parseInt(parts[0].trim()) : 0;
                int g = parts.length > 1 ? Integer.parseInt(parts[1].trim()) : 0;
                int b = parts.length > 2 ? Integer.parseInt(parts[2].trim()) : 0;
                int a = parts.length > 3 ? Integer.parseInt(parts[3].trim()) : 255;
                return Color.argb(a, r, g, b);
            }
            return Integer.decode(trimmed);
        } catch (Throwable ignored) {
            return defaultValue;
        }
    }
}