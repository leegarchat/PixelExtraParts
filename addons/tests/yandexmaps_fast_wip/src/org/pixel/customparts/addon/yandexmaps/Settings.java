package org.pixel.customparts.addon.yandexmaps;

import android.content.Context;

/** Shared Settings.Global keys for the Yandex Maps addon. */
public final class Settings {
    public static final String KEY_VULKAN = "yandexmaps_vulkan_enabled";
    public static final String KEY_NO_TAXI = "yandexmaps_no_taxi";
    public static final String KEY_FEED_COLLAPSED = "yandexmaps_feed_collapsed";
    /** Feed mode: "off" | "var1" (ours, anchor downgrade) | "var2" (author-style, content suppression). */
    public static final String KEY_FEED_MODE = "yandexmaps_feed_mode";
    public static final String FEED_OFF = "off";
    public static final String FEED_VAR1 = "var1";
    public static final String FEED_VAR2 = "var2";

    // Ported Discovery keys (see DiscoveryHooks).
    public static final String KEY_DISC_TAB = "yandexmaps_disc_tab";
    public static final String KEY_DISC_OBJECTS = "yandexmaps_disc_objects";
    public static final String KEY_DISC_BUTTON = "yandexmaps_disc_button";
    // Ported extra-taxi keys (see TaxiExtraHooks).
    public static final String KEY_TAXI_BANNERS = "yandexmaps_taxi_banners";
    public static final String KEY_TAXI_ROUTE = "yandexmaps_taxi_route";
    public static final String KEY_TAXI_CAR = "yandexmaps_taxi_car";
    // Ported tab/search keys (see TabsHooks).
    public static final String KEY_TAB_TAXI = "yandexmaps_tab_taxi";
    public static final String KEY_TAB_REFUEL = "yandexmaps_tab_refuel";
    public static final String KEY_TAB_SERVICES = "yandexmaps_tab_services";
    public static final String KEY_SEARCH_LINE = "yandexmaps_search_line";
    public static final String KEY_SEARCH_HISTORY = "yandexmaps_search_history";
    public static final String KEY_SEARCH_RUBRICS = "yandexmaps_search_rubrics";
    // Ported profile/music/navi/misc keys (see MiscHooks).
    public static final String KEY_PROF_PLUS = "yandexmaps_prof_plus";
    public static final String KEY_PROF_ADVERTISER = "yandexmaps_prof_advertiser";
    public static final String KEY_PROF_COMPANY = "yandexmaps_prof_company";
    public static final String KEY_PROF_PROMO = "yandexmaps_prof_promo";
    public static final String KEY_MUS_AUTH = "yandexmaps_mus_auth";
    public static final String KEY_NAVI_GUIDANCE_AD = "yandexmaps_navi_guidance_ad";
    public static final String KEY_NAVI_CAR_BANNER = "yandexmaps_navi_car_banner";
    public static final String KEY_NAVI_PED_BANNER = "yandexmaps_navi_ped_banner";
    public static final String KEY_NAVI_ROUTES = "yandexmaps_navi_routes";
    public static final String KEY_NAVI_REFUEL_TOOLBAR = "yandexmaps_navi_refuel_toolbar";
    public static final String KEY_NAVI_REFUEL_SETTINGS = "yandexmaps_navi_refuel_settings";
    public static final String KEY_MISC_NOTIF_CACHE = "yandexmaps_misc_notif_cache";
    public static final String KEY_MISC_TRANSPORT = "yandexmaps_misc_transport";
    // Ported placecard-ads keys (see PlacecardAdsHooks).
    public static final String KEY_AD_PROMO = "yandexmaps_ad_promo";
    public static final String KEY_AD_TEXT = "yandexmaps_ad_text";
    public static final String KEY_AD_ADVERTISER = "yandexmaps_ad_advertiser";
    public static final String KEY_AD_PLUS = "yandexmaps_ad_plus";
    public static final String KEY_AD_OFFERS = "yandexmaps_ad_offers";
    public static final String KEY_AD_SEARCH = "yandexmaps_ad_search";

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

    public static String getStr(Context c, String key, String def) {
        try {
            String v = android.provider.Settings.Global.getString(c.getContentResolver(), key);
            return v != null ? v : def;
        } catch (Throwable ignored) {
            return def;
        }
    }

    /** Feed mode with backward compat: old boolean switch ON means var1. */
    public static String feedMode(Context c) {
        String v = getStr(c, KEY_FEED_MODE, null);
        if (v == null) {
            return get(c, KEY_FEED_COLLAPSED, true) ? FEED_VAR1 : FEED_OFF;
        }
        return v;
    }
}
