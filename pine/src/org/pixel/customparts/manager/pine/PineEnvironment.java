package org.pixel.customparts.manager.pine;

import android.content.Context;
import android.provider.Settings;
import android.util.Log;

import org.pixel.customparts.core.IHookEnvironment;

public class PineEnvironment implements IHookEnvironment {

    private static final String TAG_PREFIX = "PineInject";
    private static final String ACTIVE_SUFFIX = "_lsplant";
    private static final String LEGACY_PINE_SUFFIX = "_pine";
    private static final String XPOSED_SUFFIX = "_xposed";

    private String stripSuffix(String key) {
        for (String s : new String[]{ACTIVE_SUFFIX, LEGACY_PINE_SUFFIX, XPOSED_SUFFIX}) {
            if (key.endsWith(s)) {
                return key.substring(0, key.length() - s.length());
            }
        }
        return key;
    }

    private String resolveKey(String key) {
        // Canonical (write) form; reads go through readStringAny() fallback.
        return stripSuffix(key) + ACTIVE_SUFFIX;
    }

    /** Read-through: new key, then legacy keys. Null when nothing stored. */
    private String readStringAny(android.content.ContentResolver resolver, String key) {
        String base = stripSuffix(key);
        String[] candidates = new String[]{
                base + ACTIVE_SUFFIX, base + LEGACY_PINE_SUFFIX, base + XPOSED_SUFFIX};
        for (String candidate : candidates) {
            try {
                String value = android.provider.Settings.Global.getString(resolver, candidate);
                if (value != null) {
                    return value;
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    @Override
    public boolean isEnabled(Context context, String key, boolean def) {
        if (context == null) return def;
        String finalKey = resolveKey(key);
        try {
            int defaultValue = def ? 1 : 0;
            return Settings.Global.getInt(context.getContentResolver(), finalKey, defaultValue) != 0;
        } catch (Throwable t) {
            logError("Env", "Failed to read boolean setting " + finalKey, t);
            return def;
        }
    }

    @Override
    public int getInt(Context context, String key, int def) {
        if (context == null) return def;
        try {
            String raw = readStringAny(context.getContentResolver(), key);
            if (raw == null) return def;
            return Integer.parseInt(raw.trim());
        } catch (Throwable t) {
            logError("Env", "Failed to read int setting " + key, t);
            return def;
        }
    }

    @Override
    public float getFloat(Context context, String key, float def) {
        if (context == null) return def;
        try {
            String raw = readStringAny(context.getContentResolver(), key);
            if (raw == null) return def;
            try {
                return Float.parseFloat(raw.trim());
            } catch (NumberFormatException e) {
                // Фолбэк: int, поделённый на 100 (для совместимости со старыми твиками)
                return Integer.parseInt(raw.trim()) / 100f;
            }
        } catch (Throwable t) {
            logError("Env", "Failed to read float setting " + key, t);
            return def;
        }
    }

    @Override
    public String getString(Context context, String key, String def) {
        if (context == null) return def;
        try {
            String val = readStringAny(context.getContentResolver(), key);
            return val != null ? val : def;
        } catch (Throwable t) {
            logError("Env", "Failed to read string setting " + key, t);
            return def;
        }
    }

    @Override
    public void log(String tag, String message) {
        Log.d(TAG_PREFIX, "[" + tag + "] " + message);
    }

    @Override
    public void logError(String tag, String message, Throwable t) {
        Log.e(TAG_PREFIX, "[" + tag + "] ERROR: " + message, t);
    }
}