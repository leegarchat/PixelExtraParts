package org.pixel.customparts.addon.yandexmaps.hooks;

import android.content.Context;
import android.util.Log;

import org.pixel.customparts.addon.yandexmaps.Settings;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

/**
 * Misc hooks ported from the LSPosed patcher to the addon pattern:
 * Class.forName with the app classLoader + XposedBridge.hookMethod +
 * per-key gate via Settings.get(context, KEY, true) + TAG.
 *
 * Every target below was verified against ru.yandex.yandexmaps 31.1.3
 * decompile; see report.txt for ported vs skipped hooks and the exact
 * evidence for each target.
 *
 * NOTE: the KEY_* constants below must be mirrored into
 * org.pixel.customparts.addon.yandexmaps.Settings and addon.json
 * (see settings.json next to this file). They are kept local so this
 * file compiles against the project unmodified.
 */
public final class MiscHooks {
    private static final String TAG = "PixelPartsYandexFast";

    // Profile.
    private static final String KEY_PROF_PLUS = "yandexmaps_prof_plus";
    private static final String KEY_PROF_ADVERTISER = "yandexmaps_prof_advertiser";
    private static final String KEY_PROF_COMPANY = "yandexmaps_prof_company";
    private static final String KEY_PROF_PROMO = "yandexmaps_prof_promo";
    // Music.
    private static final String KEY_MUS_AUTH = "yandexmaps_mus_auth";
    // Navigation.
    private static final String KEY_NAVI_GUIDANCE_AD = "yandexmaps_navi_guidance_ad";
    private static final String KEY_NAVI_CAR_BANNER = "yandexmaps_navi_car_banner";
    private static final String KEY_NAVI_PED_BANNER = "yandexmaps_navi_ped_banner";
    private static final String KEY_NAVI_ROUTES = "yandexmaps_navi_routes";
    private static final String KEY_NAVI_REFUEL_TOOLBAR = "yandexmaps_navi_refuel_toolbar";
    private static final String KEY_NAVI_REFUEL_SETTINGS = "yandexmaps_navi_refuel_settings";
    // Notifications/intro/transport.
    private static final String KEY_MISC_NOTIF_CACHE = "yandexmaps_misc_notif_cache";
    private static final String KEY_MISC_TRANSPORT = "yandexmaps_misc_transport";

    // Runtime filter markers (see report.txt for origin).
    private static final String PLUS_MARKER = "plusovik";
    private static final String REFUEL_ITEM_MARKER = "openRefuels";
    private static final String ROUTE_PLACE_PREFIX = "Place(";
    private static final String SETTINGS_REFUEL_ID = "Refuel";

    // Verified targets (binary names in 31.1.3).
    private static final String CLS_CAROUSEL_STATE = "fz";
    private static final String CLS_PROFILE_STATE =
            "ru.yandex.yandexmaps.profile.internal.redux.ProfileState";
    private static final String CLS_MAIN_TAB_TOOLTIPS =
            "ru.yandex.yandexmaps.map.tabs.q0";
    private static final String CLS_MUSIC_USER =
            "com.yandex.music.sdk.authorizer.data.User";
    private static final String CLS_NAVI_AD_TRACKER = "i01";
    private static final String CLS_GUIDANCE_BANNER_AD =
            "ru.yandex.yandexmaps.multiplatform.guidance.banner.ads.api.GuidanceBannerAdController";
    private static final String CLS_ECO_GUIDANCE_NEW =
            "ru.yandex.yandexmaps.ecoguidance.api.NewEcoGuidanceController";
    private static final String CLS_ADS_BANNER_CONTAINER =
            "ru.yandex.yandexmaps.ecoguidance.internal.view.AdsBannerContainer";
    private static final String CLS_NAVI_SERVICE =
            "ru.yandex.yandexmaps.services.navi.NaviServiceController";
    private static final String CLS_GRID_RUBRIC_BLOCK = "gni0";
    private static final String CLS_SETTINGS_SOURCE =
            "ru.yandex.yandexmaps.multiplatform.settings.ui.internal.screenstatesource.d";
    private static final String CLS_CONFIG_CACHE_READER =
            "ru.yandex.yandexmaps.multiplatform.config.cache.impl.ConfigCacheServiceImpl$readFromCacheAsync$2";
    private static final String CLS_TRANSPORT_FLAGS = "ifk1";

    private MiscHooks() {}

    public static void install(final Context context, final ClassLoader cl) {
        installProfilePlus(context, cl);
        installProfileAdvertiser(context, cl);
        installProfileCompany(context, cl);
        installProfilePromo(context, cl);
        installMusicAuth(context, cl);
        installNaviGuidanceAd(context, cl);
        installNaviCarBanner(context, cl);
        installNaviPedBanner(context, cl);
        installNaviRoutes(context, cl);
        installNaviRefuelToolbar(context, cl);
        installNaviRefuelSettings(context, cl);
        installNotifCache(context, cl);
        installTransport(context, cl);
    }

    // ---- profile: plus -----------------------------------------------
    // Patcher (hook_profile_plus): after-hook on the List-returning
    // carousel-state method drops items whose toString contains
    // "plusovik". Ported as an after-hook on fz.f() (the
    // profileMenuIcons list of ActionsCarouselItem). Additionally the
    // plus row composer (gz.e) explicitly hides the row when
    // yandexPlusState == HIDDEN, so the state getter is forced to
    // HIDDEN as a second, independently verified mechanism.
    private static void installProfilePlus(final Context context, final ClassLoader cl) {
        try {
            final Class<?> state = Class.forName(CLS_CAROUSEL_STATE, false, cl);
            boolean hooked = false;
            for (final Method m : state.getDeclaredMethods()) {
                if (!"f".equals(m.getName())) continue;
                if (m.getParameterTypes().length != 0) continue;
                if (!List.class.isAssignableFrom(m.getReturnType())) continue;
                m.setAccessible(true);
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam param) {
                        if (!Settings.get(context, KEY_PROF_PLUS, true)) return;
                        try {
                            Object result = param.getResult();
                            if (!(result instanceof List)) return;
                            List<?> src = (List<?>) result;
                            List<Object> kept = new ArrayList<Object>(src.size());
                            int dropped = 0;
                            for (Object item : src) {
                                if (item != null
                                        && String.valueOf(item).contains(PLUS_MARKER)) {
                                    dropped++;
                                    continue;
                                }
                                kept.add(item);
                            }
                            if (dropped > 0) {
                                param.setResult(kept);
                                Log.i(TAG, "profile plus icons removed (" + dropped + ")");
                            }
                        } catch (Throwable t) {
                            Log.w(TAG, "profile plus filter failed: " + t);
                        }
                    }
                });
                Log.i(TAG, "hooked profile plus icons " + m);
                hooked = true;
            }
            if (!hooked) Log.w(TAG, CLS_CAROUSEL_STATE + ": f() not found");
        } catch (ClassNotFoundException e) {
            Log.w(TAG, CLS_CAROUSEL_STATE + " not in this process, skip");
        } catch (Throwable t) {
            Log.e(TAG, "profile plus hook failed", t);
        }
        try {
            final Class<?> state = Class.forName(CLS_PROFILE_STATE, false, cl);
            boolean hooked = false;
            for (final Method m : state.getDeclaredMethods()) {
                if (!"getYandexPlusState".equals(m.getName())) continue;
                if (m.getParameterTypes().length != 0) continue;
                m.setAccessible(true);
                final Class<?> hiddenOwner = m.getReturnType();
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam param) {
                        if (!Settings.get(context, KEY_PROF_PLUS, true)) return;
                        try {
                            Object hidden = Enum.valueOf(
                                    (Class<Enum>) hiddenOwner, "HIDDEN");
                            if (hidden != null && !hidden.equals(param.getResult())) {
                                param.setResult(hidden);
                                Log.i(TAG, "profile plus row hidden");
                            }
                        } catch (Throwable t) {
                            Log.w(TAG, "profile plus row hide failed: " + t);
                        }
                    }
                });
                Log.i(TAG, "hooked profile plus row " + m);
                hooked = true;
            }
            if (!hooked) Log.w(TAG, CLS_PROFILE_STATE + ": getYandexPlusState not found");
        } catch (ClassNotFoundException e) {
            Log.w(TAG, CLS_PROFILE_STATE + " not in this process, skip");
        } catch (Throwable t) {
            Log.e(TAG, "profile plus row hook failed", t);
        }
    }

    // ---- profile: advertiser ------------------------------------------
    // Patcher (hook_profile_advertiser): before-hook on the
    // ActionsCarouselItem constructor forces arg[4] (showPlaceAd) to
    // false. Verified: fz.<init> 5th boolean is showPlaceAd and the
    // composer (gz.c) returns null when it is false.
    private static void installProfileAdvertiser(final Context context, final ClassLoader cl) {
        try {
            final Class<?> state = Class.forName(CLS_CAROUSEL_STATE, false, cl);
            boolean hooked = false;
            for (final Constructor<?> c : state.getDeclaredConstructors()) {
                Class<?>[] p = c.getParameterTypes();
                if (p.length != 19) continue;
                if (p[4] != boolean.class) continue;
                c.setAccessible(true);
                XposedBridge.hookMethod(c, new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam param) {
                        if (!Settings.get(context, KEY_PROF_ADVERTISER, true)) return;
                        param.args[4] = Boolean.FALSE;
                    }
                });
                Log.i(TAG, "hooked profile advertiser " + c);
                hooked = true;
            }
            if (!hooked) Log.w(TAG, CLS_CAROUSEL_STATE + ": 19-arg ctor not found");
        } catch (ClassNotFoundException e) {
            Log.w(TAG, CLS_CAROUSEL_STATE + " not in this process, skip");
        } catch (Throwable t) {
            Log.e(TAG, "profile advertiser hook failed", t);
        }
    }

    // ---- profile: company ----------------------------------------------
    // Patcher (hook_profile_company): skips the method that builds the
    // "is this your company?" tooltip (the only user of the
    // potential_company_question_your_company_text string):
    // map.tabs.q0.x(Context, int, ViewGroup, PotentialCompany, a1).
    // The method only adds a tooltip view, so skipping is UI-only.
    private static void installProfileCompany(final Context context, final ClassLoader cl) {
        try {
            final Class<?> tips = Class.forName(CLS_MAIN_TAB_TOOLTIPS, false, cl);
            boolean hooked = false;
            for (final Method m : tips.getDeclaredMethods()) {
                if (!"x".equals(m.getName())) continue;
                Class<?>[] p = m.getParameterTypes();
                if (p.length != 5) continue;
                if (!p[3].getName().contains("PotentialCompany")) continue;
                if (m.getReturnType() != void.class) continue;
                m.setAccessible(true);
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam param) {
                        if (!Settings.get(context, KEY_PROF_COMPANY, true)) return;
                        param.setResult(null);
                        Log.i(TAG, "profile company tooltip skipped");
                    }
                });
                Log.i(TAG, "hooked profile company " + m);
                hooked = true;
            }
            if (!hooked) Log.w(TAG, CLS_MAIN_TAB_TOOLTIPS + ": x(5) not found");
        } catch (ClassNotFoundException e) {
            Log.w(TAG, CLS_MAIN_TAB_TOOLTIPS + " not in this process, skip");
        } catch (Throwable t) {
            Log.e(TAG, "profile company hook failed", t);
        }
    }

    // ---- profile: promo -------------------------------------------------
    // Patcher (hook_profile_promo): nulls the promo (gameBanner) of the
    // ActionsCarouselItem. Both read sites (carousel composers gz.e and
    // e.b) are explicitly null-safe, and ProfileState.getGameBanner is
    // declared @Nullable, so both levels are nulled.
    private static void installProfilePromo(final Context context, final ClassLoader cl) {
        try {
            final Class<?> state = Class.forName(CLS_CAROUSEL_STATE, false, cl);
            boolean hooked = false;
            for (final Method m : state.getDeclaredMethods()) {
                if (!"b".equals(m.getName())) continue;
                if (m.getParameterTypes().length != 0) continue;
                if (!m.getReturnType().getName().contains("ProfileGameBanner")) continue;
                m.setAccessible(true);
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam param) {
                        if (!Settings.get(context, KEY_PROF_PROMO, true)) return;
                        if (param.getResult() != null) {
                            param.setResult(null);
                            Log.i(TAG, "profile promo banner removed");
                        }
                    }
                });
                Log.i(TAG, "hooked profile promo " + m);
                hooked = true;
            }
            if (!hooked) Log.w(TAG, CLS_CAROUSEL_STATE + ": b() not found");
        } catch (ClassNotFoundException e) {
            Log.w(TAG, CLS_CAROUSEL_STATE + " not in this process, skip");
        } catch (Throwable t) {
            Log.e(TAG, "profile promo hook failed", t);
        }
        try {
            final Class<?> state = Class.forName(CLS_PROFILE_STATE, false, cl);
            boolean hooked = false;
            for (final Method m : state.getDeclaredMethods()) {
                if (!"getGameBanner".equals(m.getName())) continue;
                if (m.getParameterTypes().length != 0) continue;
                m.setAccessible(true);
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam param) {
                        if (!Settings.get(context, KEY_PROF_PROMO, true)) return;
                        if (param.getResult() != null) {
                            param.setResult(null);
                            Log.i(TAG, "profile promo banner removed (state)");
                        }
                    }
                });
                Log.i(TAG, "hooked profile promo state " + m);
                hooked = true;
            }
            if (!hooked) Log.w(TAG, CLS_PROFILE_STATE + ": getGameBanner not found");
        } catch (ClassNotFoundException e) {
            Log.w(TAG, CLS_PROFILE_STATE + " not in this process, skip");
        } catch (Throwable t) {
            Log.e(TAG, "profile promo state hook failed", t);
        }
    }

    // ---- music: auth ------------------------------------------------------
    // Patcher (hook_music_auth): before-hook on the music SDK User
    // constructor forces the hasSubscription flag (ctor arg[2]) to true.
    // Verified: User(uid, login, hasSubscription, geoRegion, child);
    // f() returns the hasSubscription field.
    private static void installMusicAuth(final Context context, final ClassLoader cl) {
        try {
            final Class<?> user = Class.forName(CLS_MUSIC_USER, false, cl);
            boolean hooked = false;
            for (final Constructor<?> c : user.getDeclaredConstructors()) {
                Class<?>[] p = c.getParameterTypes();
                if (p.length != 5) continue;
                if (p[2] != boolean.class) continue;
                c.setAccessible(true);
                XposedBridge.hookMethod(c, new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam param) {
                        if (!Settings.get(context, KEY_MUS_AUTH, true)) return;
                        param.args[2] = Boolean.TRUE;
                        Log.i(TAG, "music auth subscription forced");
                    }
                });
                Log.i(TAG, "hooked music auth " + c);
                hooked = true;
            }
            if (!hooked) Log.w(TAG, CLS_MUSIC_USER + ": 5-arg ctor not found");
        } catch (ClassNotFoundException e) {
            Log.w(TAG, CLS_MUSIC_USER + " not in this process, skip");
        } catch (Throwable t) {
            Log.e(TAG, "music auth hook failed", t);
        }
    }

    // ---- navigation: guidance advert ---------------------------------------
    // Patcher (hook_ad_guidance_navi): skips i01.i(PolylinePosition),
    // the route-position feed of the car-guidance billboards (adverts)
    // controller pt00. Navigation itself is untouched.
    private static void installNaviGuidanceAd(final Context context, final ClassLoader cl) {
        try {
            final Class<?> tracker = Class.forName(CLS_NAVI_AD_TRACKER, false, cl);
            boolean hooked = false;
            for (final Method m : tracker.getDeclaredMethods()) {
                if (!"i".equals(m.getName())) continue;
                Class<?>[] p = m.getParameterTypes();
                if (p.length != 1) continue;
                if (!p[0].getName().contains("PolylinePosition")) continue;
                if (m.getReturnType() != void.class) continue;
                m.setAccessible(true);
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam param) {
                        if (!Settings.get(context, KEY_NAVI_GUIDANCE_AD, true)) return;
                        param.setResult(null);
                        Log.i(TAG, "navi guidance advert position feed skipped");
                    }
                });
                Log.i(TAG, "hooked navi guidance advert " + m);
                hooked = true;
            }
            if (!hooked) Log.w(TAG, CLS_NAVI_AD_TRACKER + ": i(PolylinePosition) not found");
        } catch (ClassNotFoundException e) {
            Log.w(TAG, CLS_NAVI_AD_TRACKER + " not in this process, skip");
        } catch (Throwable t) {
            Log.e(TAG, "navi guidance advert hook failed", t);
        }
    }

    // ---- navigation: car guidance banner ------------------------------------
    // Patcher (hook_ad_guidance_car): before-hook on
    // GuidanceBannerAdController.e1(boolean) forces arg[0] to false, so
    // the ad is never allowed by the view. e1 is the only boolean-param
    // method of that controller.
    private static void installNaviCarBanner(final Context context, final ClassLoader cl) {
        try {
            final Class<?> ctrl = Class.forName(CLS_GUIDANCE_BANNER_AD, false, cl);
            boolean hooked = false;
            for (final Method m : ctrl.getDeclaredMethods()) {
                if (!"e1".equals(m.getName())) continue;
                Class<?>[] p = m.getParameterTypes();
                if (p.length != 1) continue;
                if (p[0] != boolean.class) continue;
                m.setAccessible(true);
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam param) {
                        if (!Settings.get(context, KEY_NAVI_CAR_BANNER, true)) return;
                        param.args[0] = Boolean.FALSE;
                    }
                });
                Log.i(TAG, "hooked car guidance banner " + m);
                hooked = true;
            }
            if (!hooked) Log.w(TAG, CLS_GUIDANCE_BANNER_AD + ": e1(boolean) not found");
        } catch (ClassNotFoundException e) {
            Log.w(TAG, CLS_GUIDANCE_BANNER_AD + " not in this process, skip");
        } catch (Throwable t) {
            Log.e(TAG, "car guidance banner hook failed", t);
        }
    }

    // ---- navigation: pedestrian guidance banner ------------------------------
    // Patcher (hook_ad_guidance_pedestrian): suppresses the eco/pedestrian
    // guidance ads banner. Ported at two verified points: D1(wyj0) of
    // NewEcoGuidanceController only touches the AdsBannerContainer
    // (visibility + ad bind + visibility signal), and
    // AdsBannerContainer.b(ewu) binds the ad payload into the container.
    // Skipping both keeps the banner unpopulated; the finish-route info
    // text path is left intact.
    private static void installNaviPedBanner(final Context context, final ClassLoader cl) {
        try {
            final Class<?> ctrl = Class.forName(CLS_ECO_GUIDANCE_NEW, false, cl);
            boolean hooked = false;
            for (final Method m : ctrl.getDeclaredMethods()) {
                if (!"D1".equals(m.getName())) continue;
                if (m.getParameterTypes().length != 1) continue;
                if (m.getReturnType() != void.class) continue;
                m.setAccessible(true);
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam param) {
                        if (!Settings.get(context, KEY_NAVI_PED_BANNER, true)) return;
                        param.setResult(null);
                        Log.i(TAG, "pedestrian guidance banner update skipped");
                    }
                });
                Log.i(TAG, "hooked pedestrian guidance banner " + m);
                hooked = true;
            }
            if (!hooked) Log.w(TAG, CLS_ECO_GUIDANCE_NEW + ": D1 not found");
        } catch (ClassNotFoundException e) {
            Log.w(TAG, CLS_ECO_GUIDANCE_NEW + " not in this process, skip");
        } catch (Throwable t) {
            Log.e(TAG, "pedestrian guidance banner hook failed", t);
        }
        try {
            final Class<?> container = Class.forName(CLS_ADS_BANNER_CONTAINER, false, cl);
            boolean hooked = false;
            for (final Method m : container.getDeclaredMethods()) {
                if (!"b".equals(m.getName())) continue;
                if (m.getParameterTypes().length != 1) continue;
                if (m.getReturnType() != void.class) continue;
                m.setAccessible(true);
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam param) {
                        if (!Settings.get(context, KEY_NAVI_PED_BANNER, true)) return;
                        param.setResult(null);
                        Log.i(TAG, "pedestrian guidance banner bind skipped");
                    }
                });
                Log.i(TAG, "hooked pedestrian banner bind " + m);
                hooked = true;
            }
            if (!hooked) Log.w(TAG, CLS_ADS_BANNER_CONTAINER + ": b() not found");
        } catch (ClassNotFoundException e) {
            Log.w(TAG, CLS_ADS_BANNER_CONTAINER + " not in this process, skip");
        } catch (Throwable t) {
            Log.e(TAG, "pedestrian banner bind hook failed", t);
        }
    }

    // ---- navigation: routes ---------------------------------------------------
    // Patcher (hook_routes_navi): before-hook on
    // NaviServiceController.j2(controller, list) replaces the suggest
    // list with a copy dropping items whose toString starts with
    // "Place(" (FloatingSuggestItem places with route estimates).
    private static void installNaviRoutes(final Context context, final ClassLoader cl) {
        try {
            final Class<?> svc = Class.forName(CLS_NAVI_SERVICE, false, cl);
            boolean hooked = false;
            for (final Method m : svc.getDeclaredMethods()) {
                if (!"j2".equals(m.getName())) continue;
                Class<?>[] p = m.getParameterTypes();
                if (p.length != 2) continue;
                if (!List.class.isAssignableFrom(p[1])) continue;
                m.setAccessible(true);
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam param) {
                        if (!Settings.get(context, KEY_NAVI_ROUTES, true)) return;
                        try {
                            Object arg = param.args[1];
                            if (!(arg instanceof List)) return;
                            List<?> src = (List<?>) arg;
                            List<Object> kept = new ArrayList<Object>(src.size());
                            int dropped = 0;
                            for (Object item : src) {
                                if (item != null
                                        && String.valueOf(item).startsWith(ROUTE_PLACE_PREFIX)) {
                                    dropped++;
                                    continue;
                                }
                                kept.add(item);
                            }
                            if (dropped > 0) {
                                param.args[1] = kept;
                                Log.i(TAG, "navi routes places removed (" + dropped + ")");
                            }
                        } catch (Throwable t) {
                            Log.w(TAG, "navi routes filter failed: " + t);
                        }
                    }
                });
                Log.i(TAG, "hooked navi routes " + m);
                hooked = true;
            }
            if (!hooked) Log.w(TAG, CLS_NAVI_SERVICE + ": j2 not found");
        } catch (ClassNotFoundException e) {
            Log.w(TAG, CLS_NAVI_SERVICE + " not in this process, skip");
        } catch (Throwable t) {
            Log.e(TAG, "navi routes hook failed", t);
        }
    }

    // ---- navigation: refuel toolbar --------------------------------------------
    // Patcher (hook_refuel_toolbar): after-hook on gni0.a() (the
    // GridRubricBlock items list of the navi shutter) drops items whose
    // toString contains "openRefuels". A fresh list is returned; the
    // original is never mutated.
    private static void installNaviRefuelToolbar(final Context context, final ClassLoader cl) {
        try {
            final Class<?> block = Class.forName(CLS_GRID_RUBRIC_BLOCK, false, cl);
            boolean hooked = false;
            for (final Method m : block.getDeclaredMethods()) {
                if (!"a".equals(m.getName())) continue;
                if (m.getParameterTypes().length != 0) continue;
                if (!List.class.isAssignableFrom(m.getReturnType())) continue;
                m.setAccessible(true);
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam param) {
                        if (!Settings.get(context, KEY_NAVI_REFUEL_TOOLBAR, true)) return;
                        try {
                            Object result = param.getResult();
                            if (!(result instanceof List)) return;
                            List<?> src = (List<?>) result;
                            List<Object> kept = new ArrayList<Object>(src.size());
                            int dropped = 0;
                            for (Object item : src) {
                                if (item != null
                                        && String.valueOf(item).contains(REFUEL_ITEM_MARKER)) {
                                    dropped++;
                                    continue;
                                }
                                kept.add(item);
                            }
                            if (dropped > 0) {
                                param.setResult(kept);
                                Log.i(TAG, "refuel toolbar items removed (" + dropped + ")");
                            }
                        } catch (Throwable t) {
                            Log.w(TAG, "refuel toolbar filter failed: " + t);
                        }
                    }
                });
                Log.i(TAG, "hooked refuel toolbar " + m);
                hooked = true;
            }
            if (!hooked) Log.w(TAG, CLS_GRID_RUBRIC_BLOCK + ": a() not found");
        } catch (ClassNotFoundException e) {
            Log.w(TAG, CLS_GRID_RUBRIC_BLOCK + " not in this process, skip");
        } catch (Throwable t) {
            Log.e(TAG, "refuel toolbar hook failed", t);
        }
    }

    // ---- navigation: refuel settings --------------------------------------------
    // Patcher (hook_refuel_settings): after-hook on the settings screen
    // state source list drops the item whose id equals "Refuel".
    // The match is exact (fail-open: unknown items are kept).
    private static void installNaviRefuelSettings(final Context context, final ClassLoader cl) {
        try {
            final Class<?> source = Class.forName(CLS_SETTINGS_SOURCE, false, cl);
            boolean hooked = false;
            for (final Method m : source.getDeclaredMethods()) {
                if (!"c".equals(m.getName())) continue;
                if (m.getParameterTypes().length != 0) continue;
                if (!List.class.isAssignableFrom(m.getReturnType())) continue;
                m.setAccessible(true);
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam param) {
                        if (!Settings.get(context, KEY_NAVI_REFUEL_SETTINGS, true)) return;
                        try {
                            Object result = param.getResult();
                            if (!(result instanceof List)) return;
                            List<?> src = (List<?>) result;
                            List<Object> kept = new ArrayList<Object>(src.size());
                            int dropped = 0;
                            for (Object item : src) {
                                if (item != null && hasExactId(item, SETTINGS_REFUEL_ID)) {
                                    dropped++;
                                    continue;
                                }
                                kept.add(item);
                            }
                            if (dropped > 0) {
                                param.setResult(kept);
                                Log.i(TAG, "refuel settings row removed (" + dropped + ")");
                            }
                        } catch (Throwable t) {
                            Log.w(TAG, "refuel settings filter failed: " + t);
                        }
                    }
                });
                Log.i(TAG, "hooked refuel settings " + m);
                hooked = true;
            }
            if (!hooked) Log.w(TAG, CLS_SETTINGS_SOURCE + ": c() not found");
        } catch (ClassNotFoundException e) {
            Log.w(TAG, CLS_SETTINGS_SOURCE + " not in this process, skip");
        } catch (Throwable t) {
            Log.e(TAG, "refuel settings hook failed", t);
        }
    }

    // ---- notifications: cache ------------------------------------------------------
    // Patcher (hook_notif_cache): the config-cache suspend read
    // (ConfigCacheServiceImpl$readFromCacheAsync$2.invokeSuspend)
    // completes with null, i.e. a cache miss, so no stale cached
    // payload (notifications/intro) is served.
    private static void installNotifCache(final Context context, final ClassLoader cl) {
        try {
            final Class<?> reader = Class.forName(CLS_CONFIG_CACHE_READER, false, cl);
            boolean hooked = false;
            for (final Method m : reader.getDeclaredMethods()) {
                if (!"invokeSuspend".equals(m.getName())) continue;
                if (m.getParameterTypes().length != 1) continue;
                m.setAccessible(true);
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam param) {
                        // Default OFF: generic config-cache path, enable only when field-tested.
                        if (!Settings.get(context, KEY_MISC_NOTIF_CACHE, false)) return;
                        param.setResult(null);
                        Log.i(TAG, "config cache read nulled");
                    }
                });
                Log.i(TAG, "hooked notif cache " + m);
                hooked = true;
            }
            if (!hooked) Log.w(TAG, CLS_CONFIG_CACHE_READER + ": invokeSuspend not found");
        } catch (ClassNotFoundException e) {
            Log.w(TAG, CLS_CONFIG_CACHE_READER + " not in this process, skip");
        } catch (Throwable t) {
            Log.e(TAG, "notif cache hook failed", t);
        }
    }

    // ---- transport ---------------------------------------------------------------
    // Patcher (hook_transport_screen): forces the TransportCardConfig
    // experiment flags (ifk1.a/b/c/d) to false. The wrapper itself
    // defaults to all-false when no config is present, so false is the
    // stock-off behavior.
    private static void installTransport(final Context context, final ClassLoader cl) {
        try {
            final Class<?> flags = Class.forName(CLS_TRANSPORT_FLAGS, false, cl);
            boolean hooked = false;
            for (final Method m : flags.getDeclaredMethods()) {
                String n = m.getName();
                if (n.length() != 1) continue;
                if ("abcd".indexOf(n.charAt(0)) < 0) continue;
                if (m.getParameterTypes().length != 0) continue;
                if (m.getReturnType() != boolean.class) continue;
                m.setAccessible(true);
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam param) {
                        if (!Settings.get(context, KEY_MISC_TRANSPORT, true)) return;
                        if (!Boolean.FALSE.equals(param.getResult())) {
                            param.setResult(Boolean.FALSE);
                            Log.i(TAG, "transport flag disabled (" + m.getName() + ")");
                        }
                    }
                });
                Log.i(TAG, "hooked transport flag " + m);
                hooked = true;
            }
            if (!hooked) Log.w(TAG, CLS_TRANSPORT_FLAGS + ": flag getters not found");
        } catch (ClassNotFoundException e) {
            Log.w(TAG, CLS_TRANSPORT_FLAGS + " not in this process, skip");
        } catch (Throwable t) {
            Log.e(TAG, "transport hook failed", t);
        }
    }

    // Exact-match id probe used by the refuel-settings filter: String
    // fields, zero-arg String getters and enum names equal to the id.
    private static boolean hasExactId(Object item, String id) {
        try {
            for (Field f : item.getClass().getDeclaredFields()) {
                if (f.getType() != String.class) continue;
                f.setAccessible(true);
                Object v = f.get(item);
                if (id.equals(v)) return true;
            }
        } catch (Throwable ignored) {}
        try {
            for (Method m : item.getClass().getMethods()) {
                if (m.getParameterTypes().length != 0) continue;
                if (m.getReturnType() != String.class) continue;
                String n = m.getName();
                if (n.equals("toString") || n.equals("hashCode")) continue;
                try {
                    Object v = m.invoke(item);
                    if (id.equals(v)) return true;
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
        try {
            if (item instanceof Enum && id.equals(((Enum<?>) item).name())) return true;
        } catch (Throwable ignored) {}
        return false;
    }
}
