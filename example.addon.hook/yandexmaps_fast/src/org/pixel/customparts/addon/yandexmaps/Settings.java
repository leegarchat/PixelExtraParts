package org.pixel.customparts.addon.yandexmaps;

import android.content.Context;

/** Shared Settings.Global keys for the Yandex Maps addon. */
public final class Settings {
    public static final String KEY_VULKAN = "yandexmaps_vulkan_enabled";
    public static final String KEY_NO_TAXI = "yandexmaps_no_taxi";
    public static final String KEY_FEED_COLLAPSED = "yandexmaps_feed_collapsed";
    public static final String KEY_SEARCH_HISTORY = "yandexmaps_search_history";

    /** Grace window (sec) after process start for startup-only tweaks. */
    public static final int GRACE_SEC = 25;

    private static volatile long sProcessStart;

    private Settings() {}

    public static void markProcessStart() {
        if (sProcessStart == 0) {
            sProcessStart = android.os.SystemClock.elapsedRealtime();
        }
    }

    public static long ageSec() {
        if (sProcessStart == 0) return Long.MAX_VALUE;
        return (android.os.SystemClock.elapsedRealtime() - sProcessStart) / 1000;
    }

    public static boolean inGrace() {
        return ageSec() < GRACE_SEC;
    }

    public static boolean get(Context c, String key, boolean def) {
        try {
            return android.provider.Settings.Global.getInt(c.getContentResolver(), key, def ? 1 : 0) != 0;
        } catch (Throwable ignored) {
            return def;
        }
    }
}
