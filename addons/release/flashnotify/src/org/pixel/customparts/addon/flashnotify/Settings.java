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
    public static final String KEY_SIM_BRIGHTNESS = "flashnotify_sim_brightness";
    public static final String KEY_VOIP_BRIGHTNESS = "flashnotify_voip_brightness";

    /** Per-category push toggles (call is excluded: always on the call branch). */
    public static final String KEY_CAT_NONE = "flashnotify_cat_none";
    public static final String KEY_CAT_MSG = "flashnotify_cat_msg";
    public static final String KEY_CAT_EMAIL = "flashnotify_cat_email";
    public static final String KEY_CAT_ALARM = "flashnotify_cat_alarm";
    public static final String KEY_CAT_REMINDER = "flashnotify_cat_reminder";
    public static final String KEY_CAT_EVENT = "flashnotify_cat_event";
    public static final String KEY_CAT_PROMO = "flashnotify_cat_promo";
    public static final String KEY_CAT_SOCIAL = "flashnotify_cat_social";
    public static final String KEY_CAT_PROGRESS = "flashnotify_cat_progress";
    public static final String KEY_CAT_TRANSPORT = "flashnotify_cat_transport";
    public static final String KEY_CAT_NAVIGATION = "flashnotify_cat_navigation";
    public static final String KEY_CAT_SERVICE = "flashnotify_cat_service";
    public static final String KEY_CAT_SYSTEM = "flashnotify_cat_system";
    public static final String KEY_CAT_STATUS = "flashnotify_cat_status";
    public static final String KEY_CAT_ERROR = "flashnotify_cat_error";
    public static final String KEY_CAT_RECOMMENDATION = "flashnotify_cat_recommendation";
    public static final String KEY_CAT_WORKOUT = "flashnotify_cat_workout";
    public static final String KEY_CAT_LOCATION = "flashnotify_cat_location";

    /**
     * Canonical ROM-published unified call state (no suffix).
     * Written by PixelPartsCallState in system_server:
     * 0 = idle, 1 = ringing, 2 = active. Absent on ROMs without the weave.
     */
    public static final String KEY_ROM_STATE = "flashnotify_call_state";

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

    /** Whether a plain (non-call) notification of this category may blink.
     * Unknown/future categories fail open (true = current behavior). */
    public static boolean isCategoryAllowed(Context c, String category) {
        if (category == null) return getBoolean(c, KEY_CAT_NONE, true);
        switch (category) {
            case "msg": return getBoolean(c, KEY_CAT_MSG, true);
            case "email": return getBoolean(c, KEY_CAT_EMAIL, true);
            case "alarm": return getBoolean(c, KEY_CAT_ALARM, true);
            case "reminder": return getBoolean(c, KEY_CAT_REMINDER, true);
            case "event": return getBoolean(c, KEY_CAT_EVENT, true);
            case "promo": return getBoolean(c, KEY_CAT_PROMO, true);
            case "social": return getBoolean(c, KEY_CAT_SOCIAL, true);
            case "progress": return getBoolean(c, KEY_CAT_PROGRESS, true);
            case "transport": return getBoolean(c, KEY_CAT_TRANSPORT, true);
            case "navigation": return getBoolean(c, KEY_CAT_NAVIGATION, true);
            case "service": return getBoolean(c, KEY_CAT_SERVICE, true);
            case "system": return getBoolean(c, KEY_CAT_SYSTEM, true);
            case "status": return getBoolean(c, KEY_CAT_STATUS, true);
            case "err": return getBoolean(c, KEY_CAT_ERROR, true);
            case "recommendation": return getBoolean(c, KEY_CAT_RECOMMENDATION, true);
            case "workout": return getBoolean(c, KEY_CAT_WORKOUT, true);
            case "location_sharing": return getBoolean(c, KEY_CAT_LOCATION, true);
            default: return true;
        }
    }

    public static int getSimBrightness(Context c) {
        return getInt(c, KEY_SIM_BRIGHTNESS, DEFAULT_STRENGTH);
    }

    public static int getVoipBrightness(Context c) {
        return getInt(c, KEY_VOIP_BRIGHTNESS, DEFAULT_STRENGTH);
    }

    /** ROM-published unified call state, 0 when the ROM has no publisher. */
    public static int getRomState(Context c) {
        return getInt(c, KEY_ROM_STATE, 0);
    }

    /** True once system_server has published the unified state at least once. */
    public static boolean hasRomState(Context c) {
        try {
            return android.provider.Settings.Global.getString(
                    c.getContentResolver(), KEY_ROM_STATE) != null;
        } catch (Throwable ignored) {
            return false;
        }
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
