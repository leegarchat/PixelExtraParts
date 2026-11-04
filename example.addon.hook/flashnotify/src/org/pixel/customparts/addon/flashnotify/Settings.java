package org.pixel.customparts.addon.flashnotify;

import android.content.Context;

/** Shared Settings.Global keys for the Flash Notify addon. */
public final class Settings {
    public static final String KEY_ENABLED = "flashnotify_enabled";
    public static final String KEY_CALLS_ENABLED = "flashnotify_calls_enabled";
    public static final String KEY_VOIP_ENABLED = "flashnotify_voip_enabled";
    public static final String KEY_COUNT = "flashnotify_count";
    public static final String KEY_TOTAL_MS = "flashnotify_total_ms";
    public static final String KEY_STRENGTH = "flashnotify_strength";
    public static final String KEY_SIM_BLINK_MS = "flashnotify_sim_blink_ms";
    public static final String KEY_SIM_COUNT = "flashnotify_sim_count";
    public static final String KEY_SIM_PAUSE_MS = "flashnotify_sim_pause_ms";
    public static final String KEY_VOIP_BLINK_MS = "flashnotify_voip_blink_ms";
    public static final String KEY_VOIP_COUNT = "flashnotify_voip_count";
    public static final String KEY_VOIP_PAUSE_MS = "flashnotify_voip_pause_ms";

    public static final boolean DEFAULT_ENABLED = true;
    public static final int DEFAULT_COUNT = 2;
    public static final int DEFAULT_TOTAL_MS = 1200;
    public static final int DEFAULT_STRENGTH = 45;

    private Settings() {}

    public static boolean getEnabled(Context c) {
        return getBoolean(c, KEY_ENABLED, DEFAULT_ENABLED);
    }

    public static boolean getCallsEnabled(Context c) {
        return getBoolean(c, KEY_CALLS_ENABLED, false);
    }

    public static boolean getVoipEnabled(Context c) {
        return getBoolean(c, KEY_VOIP_ENABLED, false);
    }

    public static int getSimBlinkMs(Context c) {
        return getInt(c, KEY_SIM_BLINK_MS, 300);
    }

    public static int getSimCount(Context c) {
        return getInt(c, KEY_SIM_COUNT, 3);
    }

    public static int getSimPauseMs(Context c) {
        return getInt(c, KEY_SIM_PAUSE_MS, 1000);
    }

    public static int getVoipBlinkMs(Context c) {
        return getInt(c, KEY_VOIP_BLINK_MS, 300);
    }

    public static int getVoipCount(Context c) {
        return getInt(c, KEY_VOIP_COUNT, 3);
    }

    public static int getVoipPauseMs(Context c) {
        return getInt(c, KEY_VOIP_PAUSE_MS, 1000);
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
