package org.pixel.customparts.addon.systemui.hooks;

import android.content.Context;
import android.graphics.Color;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Log;

public abstract class BaseSystemUIHook {
    private static final String PINE_SUFFIX = "_pine";
    private static final String XPOSED_SUFFIX = "_xposed";
    protected static final int USER_CURRENT = -2;

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
            return Settings.Global.getInt(context.getContentResolver(), resolveKey(key), defaultValue ? 1 : 0) != 0;
        } catch (Throwable ignored) {
            return defaultValue;
        }
    }

    protected int getIntSetting(Context context, String key, int defaultValue) {
        if (context == null) {
            return defaultValue;
        }
        String resolvedKey = resolveKey(key);
        try {
            return Settings.Global.getInt(context.getContentResolver(), resolvedKey, defaultValue);
        } catch (Throwable ignored) {
            try {
                return parseIntOrColor(Settings.Global.getString(context.getContentResolver(), resolvedKey), defaultValue);
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
            return Settings.Global.getFloat(context.getContentResolver(), resolveKey(key), defaultValue);
        } catch (Throwable ignored) {
            return defaultValue;
        }
    }

    protected String getStringSetting(Context context, String key, String defaultValue) {
        if (context == null) {
            return defaultValue;
        }
        try {
            String value = Settings.Global.getString(context.getContentResolver(), resolveKey(key));
            return value != null ? value : defaultValue;
        } catch (Throwable ignored) {
            return defaultValue;
        }
    }

    protected String resolveKey(String key) {
        if (key == null || key.isEmpty()) {
            return key;
        }
        if (key.endsWith(PINE_SUFFIX)) {
            return key;
        }
        if (key.endsWith(XPOSED_SUFFIX)) {
            return key.substring(0, key.length() - XPOSED_SUFFIX.length()) + PINE_SUFFIX;
        }
        return key + PINE_SUFFIX;
    }

    protected static int userHandleIdentifier(UserHandle userHandle) {
        if (userHandle == null) {
            return 0;
        }
        try {
            Object value = UserHandle.class.getMethod("getIdentifier").invoke(userHandle);
            return value instanceof Integer ? (Integer) value : 0;
        } catch (Throwable ignored) {
            return 0;
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
                int red = parts.length > 0 ? Integer.parseInt(parts[0].trim()) : 0;
                int green = parts.length > 1 ? Integer.parseInt(parts[1].trim()) : 0;
                int blue = parts.length > 2 ? Integer.parseInt(parts[2].trim()) : 0;
                int alpha = parts.length > 3 ? Integer.parseInt(parts[3].trim()) : 255;
                return Color.argb(alpha, red, green, blue);
            }
            return Integer.decode(trimmed);
        } catch (Throwable ignored) {
            return defaultValue;
        }
    }
}
