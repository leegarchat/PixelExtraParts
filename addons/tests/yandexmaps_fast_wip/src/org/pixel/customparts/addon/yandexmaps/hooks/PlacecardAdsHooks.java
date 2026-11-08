package org.pixel.customparts.addon.yandexmaps.hooks;

import android.content.Context;
import android.util.Log;

import org.pixel.customparts.addon.yandexmaps.Settings;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

/**
 * Placecard body declutter, analogous to the reference patcher's
 * PLACECARD_* ad hooks.
 *
 * Single hook point: the geoobject card-list composer
 * {@code ...composers.cardlist.a.f()} builds the whole body as a list of
 * {@code Pair(item, PlacecardItemType)} (see the A..X block methods, each
 * tagged with its PlacecardItemType). Ad blocks are dropped by type after
 * composition, so neighbours re-layout cleanly and legit blocks sharing a
 * composer method (e.g. VERIFIED_OWNER next to BECOME_ADVERTISER) survive.
 * Unknown element shapes are kept (fail-open).
 */
public final class PlacecardAdsHooks {
    private static final String TAG = "PixelPartsYandexFast";
    private static final String COMPOSER =
            "ru.yandex.yandexmaps.placecard.controllers.geoobject.epics.loading.composing.composers.cardlist.a";

    public static final String KEY_PROMO = "yandexmaps_ad_promo";
    public static final String KEY_TEXT = "yandexmaps_ad_text";
    public static final String KEY_ADVERTISER = "yandexmaps_ad_advertiser";
    public static final String KEY_PLUS = "yandexmaps_ad_plus";
    public static final String KEY_OFFERS = "yandexmaps_ad_offers";
    public static final String KEY_SEARCH = "yandexmaps_ad_search";

    private PlacecardAdsHooks() {}

    public static void install(final Context context, final ClassLoader cl) {
        try {
            final Class<?> composer = Class.forName(COMPOSER, false, cl);
            boolean hooked = false;
            for (final Method m : composer.getDeclaredMethods()) {
                if (!"f".equals(m.getName())) continue;
                if (m.getParameterTypes().length != 0) continue;
                if (!List.class.isAssignableFrom(m.getReturnType())) continue;
                m.setAccessible(true);
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam param) {
                        try {
                            Object result = param.getResult();
                            if (!(result instanceof List)) return;
                            List<?> src = (List<?>) result;
                            Map<String, Boolean> on = new HashMap<String, Boolean>();
                            on.put(KEY_PROMO, Settings.get(context, KEY_PROMO, true));
                            on.put(KEY_TEXT, Settings.get(context, KEY_TEXT, true));
                            on.put(KEY_ADVERTISER, Settings.get(context, KEY_ADVERTISER, true));
                            on.put(KEY_PLUS, Settings.get(context, KEY_PLUS, true));
                            on.put(KEY_OFFERS, Settings.get(context, KEY_OFFERS, true));
                            on.put(KEY_SEARCH, Settings.get(context, KEY_SEARCH, true));
                            List<Object> kept = new ArrayList<Object>(src.size());
                            int dropped = 0;
                            StringBuilder dbg = new StringBuilder();
                            java.util.Set<String> seen = new java.util.HashSet<String>();
                            for (Object item : src) {
                                String t = itemTypeName(item);
                                if (t != null && seen.add(t)) dbg.append(t).append(',');
                            }
                            Log.i(TAG, "ads dbg cardlist: size=" + src.size() + " types=" + dbg + " classes=" + classesOf(src));
                            for (Object item : src) {
                                String key = adKeyOf(item);
                                if (key != null && Boolean.TRUE.equals(on.get(key))) {
                                    dropped++;
                                    continue;
                                }
                                kept.add(item);
                            }
                            if (dropped > 0) {
                                param.setResult(kept);
                                Log.i(TAG, "placecard ads removed (" + dropped + ")");
                            }
                        } catch (Throwable t) {
                            Log.w(TAG, "placecard ads filter failed: " + t);
                        }
                    }
                });
                Log.i(TAG, "hooked placecard composer " + m);
                hooked = true;
            }
            if (!hooked) Log.w(TAG, COMPOSER + ": composer f() not found");
        } catch (ClassNotFoundException e) {
            Log.w(TAG, COMPOSER + " not in this process, skip");
        } catch (Throwable t) {
            Log.e(TAG, "placecard ads hook failed", t);
        }
    }

    /** Debug: distinct element class names (first 6). */
    private static String classesOf(List<?> src) {
        StringBuilder sb = new StringBuilder();
        java.util.Set<String> seen = new java.util.HashSet<String>();
        int n = 0;
        for (Object item : src) {
            if (n >= 6) break;
            String c = item == null ? "null" : item.getClass().getName();
            if (seen.add(c)) {
                sb.append(c).append(',');
                n++;
            }
        }
        return sb.toString();
    }

    /** Maps a card-list element to its ad-group key, or null to keep. */
    private static String adKeyOf(Object item) {
        String type = itemTypeName(item);
        if (type == null) return null;
        if ("PROMOTIONS".equals(type) || "PROMO_CAMPAIGN_TITLE".equals(type)) return KEY_PROMO;
        if ("TEXT_ADVERTISEMENT_BOTTOM".equals(type)) return KEY_TEXT;
        if ("BECOME_ADVERTISER".equals(type)) return KEY_ADVERTISER;
        if ("PLUS_OFFERS".equals(type)) return KEY_PLUS;
        if ("ORG_OFFER_BANNER".equals(type) || "ORG_OFFER_BADGE".equals(type)
                || "DOUBLOONS_OR_ORG_OFFER_BANNER".equals(type)) return KEY_OFFERS;
        if ("SEARCH_BANNER".equals(type)) return KEY_SEARCH;
        return null;
    }

    /** Extracts the PlacecardItemType name from a Pair(item, type) element. */
    private static String itemTypeName(Object item) {
        if (item == null) return null;
        try {
            // kotlin.Pair: second() holds the PlacecardItemType.
            if ("kotlin.Pair".equals(item.getClass().getName())) {
                Object second;
                try {
                    second = item.getClass().getMethod("getSecond").invoke(item);
                } catch (NoSuchMethodException e) {
                    java.lang.reflect.Field f = item.getClass().getDeclaredField("second");
                    f.setAccessible(true);
                    second = f.get(item);
                }
                if (second instanceof Enum) return ((Enum<?>) second).name();
                return null;
            }
            Method getter = item.getClass().getMethod("getType");
            Object type = getter.invoke(item);
            if (type instanceof Enum) return ((Enum<?>) type).name();
        } catch (Throwable ignored) {}
        return null;
    }
}
