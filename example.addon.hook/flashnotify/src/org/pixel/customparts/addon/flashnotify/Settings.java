package org.pixel.customparts.addon.flashnotify;

import android.content.Context;

/** Shared Settings.Global keys for the Flash Notify addon. */
public final class Settings {
    public static final String KEY_ENABLED = "flashnotify_enabled";
    public static final String KEY_COUNT = "flashnotify_count";
    public static final String KEY_TOTAL_MS = "flashnotify_total_ms";
    public static final String KEY_STRENGTH = "flashnotify_strength";

    public static final boolean DEFAULT_ENABLED = true;
    public static final int DEFAULT_COUNT = 2;
    public static final int DEFAULT_TOTAL_MS = 1200;
    public static final int DEFAULT_STRENGTH = 45;

    private Settings() {}

    public static boolean getEnabled(Context c) {
        return getBoolean(c, KEY_ENABLED, DEFAULT_ENABLED);
    }

    public static int getCount(Context c) {
        return getInt(c, KEY_COUNT, DEFAULT_COUNT);
    }

    public static int getTotalMs(Context c) {
        return getInt(c, KEY_TOTAL_MS, DEFAULT_TOTAL_MS);
    }

    public static int getStrength(Context c) {
        return getInt(c, KEY_STRENGTH, DEFAULT_STRENGTH);
    }

    public static boolean getBoolean(Context c, String key, boolean def) {
        try {
            return android.provider.Settings.Global.getInt(
                    c.getContentResolver(), key, def ? 1 : 0) != 0;
        } catch (Throwable ignored) {
            return def;
        }
    }

    public static int getInt(Context c, String key, int def) {
        try {
            return android.provider.Settings.Global.getInt(
                    c.getContentResolver(), key, def);
        } catch (Throwable ignored) {
            return def;
        }
    }
}
