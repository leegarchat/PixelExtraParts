package org.pixel.customparts.addon.yandexmaps.hooks;

import android.content.Context;
import android.util.Log;

import org.pixel.customparts.addon.yandexmaps.Settings;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

/**
 * Extra taxi declutter hooks ported from the LSPosed patcher
 * (hook_ad_taxi / hook_taxi_route / hook_taxi_car), verified against
 * ru.yandex.yandexmaps 31.1.3 decompile.
 *
 * Does NOT duplicate TaxiButtonHook (placecard taxi capsule); every hook
 * below targets a different surface and is gated by its own settings key.
 */
public final class TaxiExtraHooks {
    private static final String TAG = "PixelPartsYandexFast";

    /** Hide the taxi banner on the transport main card. */
    public static final String KEY_BANNERS = "yandexmaps_taxi_banners";
    /** Taxi offered on the single-route screen (RouteTypesConfiguration). */
    public static final String KEY_ROUTE = "yandexmaps_taxi_route";
    /** Taxi snippet inside mixed route cards (route grouping). */
    public static final String KEY_CAR = "yandexmaps_taxi_car";

    // ru.yandex.yandexmaps.multiplatform.taxi.internal.redux.state.ExperimentsState
    // Boolean getter of isTaxiMainCardBannerEnabled (runtime name "c0").
    // Consumer: defpackage.cc6 (skips the TaxiMainCard banner epic when false).
    // Patcher: hook_ad_taxi (C5882/C1849), before-hook forces FALSE, gated by pref "taxi".
    private static final String EXPERIMENTS_STATE =
            "ru.yandex.yandexmaps.multiplatform.taxi.internal.redux.state.ExperimentsState";
    private static final String BANNER_GETTER = "c0";

    // ru.yandex.yandexmaps.multiplatform.select.route.common.api.RouteTypesConfiguration$SingleRouteType
    // Primary ctor: (RouteType routeType, boolean noTaxi, boolean showTaxiMultimodalRoutes).
    // toString "SingleRouteType(routeType=..., noTaxi=...)" carries both DexKit literals.
    // Patcher: hook_taxi_route (C3106/C4327), ctor before-hook forces args[1] FALSE,
    // gated by navigation_tabs containing TAXI_TAB.
    private static final String SINGLE_ROUTE_TYPE =
            "ru.yandex.yandexmaps.multiplatform.select.route.common.api.RouteTypesConfiguration$SingleRouteType";
    private static final String ROUTE_TYPE =
            "ru.yandex.yandexmaps.multiplatform.core.routes.RouteType";

    // Route card grouping (R8-renamed holders, build-pinned):
    // ru.yandex.yandexmaps.multiplatform.select.route.common.impl.internal.viewstate.common.grouping.a
    // static b(SuccessResultWithSelection, int, RouteType, TaxiOffer, TaxiRoutesRequest, boolean):
    // when the trailing flag is true a Taxi snippet is inserted into the card list.
    // Patcher: hook_taxi_car (C3956/C3599), before-hook forces the last arg FALSE,
    // gated by navigation_tabs containing TAXI_TAB. Matched by shape, not by name.
    private static final String MIXED_TAXI_GROUPING =
            "ru.yandex.yandexmaps.multiplatform.select.route.common.impl.internal.viewstate.common.grouping.a";

    private TaxiExtraHooks() {}

    public static void install(final Context context, final ClassLoader cl) {
        hookMainCardBanner(context, cl);
        hookSingleRouteType(context, cl);
        hookMixedTaxiGrouping(context, cl);
    }

    private static void hookMainCardBanner(final Context context, final ClassLoader cl) {
        try {
            final Class<?> state = Class.forName(EXPERIMENTS_STATE, false, cl);
            boolean hooked = false;
            for (final Method m : state.getDeclaredMethods()) {
                if (!BANNER_GETTER.equals(m.getName())) continue;
                if (m.getParameterTypes().length != 0) continue;
                if (m.getReturnType() != boolean.class && m.getReturnType() != Boolean.class) continue;
                m.setAccessible(true);
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam param) {
                        if (Settings.get(context, KEY_BANNERS, true)) {
                            param.setResult(Boolean.FALSE);
                        }
                    }
                });
                Log.i(TAG, "hooked taxi banner flag " + m);
                hooked = true;
            }
            if (!hooked) Log.w(TAG, EXPERIMENTS_STATE + ": c0() not found");
        } catch (ClassNotFoundException e) {
            Log.w(TAG, EXPERIMENTS_STATE + " not in this process, skip");
        } catch (Throwable t) {
            Log.e(TAG, "taxi banner hook failed", t);
        }
    }

    private static void hookSingleRouteType(final Context context, final ClassLoader cl) {
        try {
            final Class<?> cls = Class.forName(SINGLE_ROUTE_TYPE, false, cl);
            final Class<?> routeType = Class.forName(ROUTE_TYPE, false, cl);
            final Object taxi;
            try {
                taxi = Enum.valueOf(routeType.asSubclass(Enum.class), "TAXI");
            } catch (Throwable t) {
                Log.w(TAG, SINGLE_ROUTE_TYPE + ": RouteType.TAXI unresolved, skip");
                return;
            }
            boolean hooked = false;
            for (final Constructor<?> c : cls.getDeclaredConstructors()) {
                final Class<?>[] p = c.getParameterTypes();
                if (p.length != 3) continue;
                if (p[1] != boolean.class && p[1] != Boolean.class) continue;
                if (p[2] != boolean.class && p[2] != Boolean.class) continue;
                if (!p[0].getName().endsWith("RouteType")) continue;
                c.setAccessible(true);
                XposedBridge.hookMethod(c, new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam param) {
                        boolean show = Settings.get(context, KEY_ROUTE, true);
                        if (show) {
                            param.args[1] = Boolean.FALSE;
                        } else if (!taxi.equals(param.args[0])) {
                            // SingleRouteType(TAXI, noTaxi=true) violates the app
                            // invariant and throws, so a lone taxi route is kept.
                            param.args[1] = Boolean.TRUE;
                        } else {
                            Log.i(TAG, "taxi route kept (single TAXI route)");
                        }
                    }
                });
                Log.i(TAG, "hooked single route type " + c);
                hooked = true;
            }
            if (!hooked) Log.w(TAG, SINGLE_ROUTE_TYPE + ": 3-arg ctor not found");
        } catch (ClassNotFoundException e) {
            Log.w(TAG, SINGLE_ROUTE_TYPE + " not in this process, skip");
        } catch (Throwable t) {
            Log.e(TAG, "taxi route hook failed", t);
        }
    }

    private static void hookMixedTaxiGrouping(final Context context, final ClassLoader cl) {
        try {
            final Class<?> grouping = Class.forName(MIXED_TAXI_GROUPING, false, cl);
            boolean hooked = false;
            for (final Method m : grouping.getDeclaredMethods()) {
                final Class<?>[] p = m.getParameterTypes();
                // Static builder (..., int, RouteType, ..., boolean): the trailing
                // flag inserts the Taxi snippet into the mixed route card.
                if (p.length != 6) continue;
                if (p[1] != int.class && p[1] != Integer.class) continue;
                if (!p[2].getName().endsWith("RouteType")) continue;
                if (p[5] != boolean.class && p[5] != Boolean.class) continue;
                m.setAccessible(true);
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam param) {
                        param.args[5] = Settings.get(context, KEY_CAR, true)
                                ? Boolean.FALSE : Boolean.TRUE;
                    }
                });
                Log.i(TAG, "hooked mixed taxi grouping " + m);
                hooked = true;
            }
            if (!hooked) Log.w(TAG, MIXED_TAXI_GROUPING + ": 6-arg builder not found");
        } catch (ClassNotFoundException e) {
            Log.w(TAG, MIXED_TAXI_GROUPING + " not in this process, skip");
        } catch (Throwable t) {
            Log.e(TAG, "taxi grouping hook failed", t);
        }
    }
}
