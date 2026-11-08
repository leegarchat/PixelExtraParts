package org.pixel.customparts.manager.lsplant;

import android.content.Context;
import android.provider.Settings;
import android.util.Log;

import org.pixel.customparts.core.IHookEnvironment;

public class LsplantEnvironment implements IHookEnvironment {

    private static final String TAG_PREFIX = "LsplantInject";

    @Override
    public boolean isEnabled(Context context, String key, boolean def) {
        if (context == null) return def;
        try {
            int defaultValue = def ? 1 : 0;
            return Settings.Global.getInt(context.getContentResolver(), key, defaultValue) != 0;
        } catch (Throwable t) {
            logError("Env", "Failed to read boolean setting " + key, t);
            return def;
        }
    }

    @Override
    public int getInt(Context context, String key, int def) {
        if (context == null) return def;
        try {
            String raw = Settings.Global.getString(context.getContentResolver(), key);
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
            String raw = Settings.Global.getString(context.getContentResolver(), key);
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
            String val = Settings.Global.getString(context.getContentResolver(), key);
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
