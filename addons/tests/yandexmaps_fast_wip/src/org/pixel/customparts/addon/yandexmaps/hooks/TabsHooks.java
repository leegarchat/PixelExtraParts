package org.pixel.customparts.addon.yandexmaps.hooks;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.util.Log;
import android.view.View;

import org.pixel.customparts.addon.yandexmaps.Settings;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

/**
 * Bottom-tab and main-screen tab hooks ported from the LSPosed patcher
 * (hook_tabs / hook_taxi_tab / hook_refuel_tab), verified against
 * ru.yandex.yandexmaps 31.1.3 decompile.
 *
 * Patcher behavior (gated by its navigation_tabs set of
 * NAVI/REFUEL/TAXI/MT):
 * - C5317/C4921(9): TabBarNewDesignConfig.&lt;init&gt; before-hook, args[0]
 *   (tabs list) filtered down to the enabled set.
 * - C5235: TabBarNewDesignState.&lt;init&gt; before-hook, args[1]
 *   (isTaxiTabForbidden) forced FALSE.
 * - C4210: TabBarOldDesignState.&lt;init&gt; before-hook, args[0]
 *   (taxiTabOnMainScreen) forced TRUE.
 * - C4788/C1724(26): refuel predicate forced to REFUEL_TAB membership.
 * Each hook below is gated by its own boolean key instead of the set.
 */
public final class TabsHooks {
    private static final String TAG = "PixelPartsYandexFast";

    /** Show the taxi tab (new + old design). */
    public static final String KEY_TAXI_TAB = "yandexmaps_tab_taxi";
    /** Show the refuel tab (new + old design). */
    public static final String KEY_REFUEL_TAB = "yandexmaps_tab_refuel";
    /** Filter the bottom tab-bar config list (BottomTabServices). */
    public static final String KEY_SERVICES = "yandexmaps_tab_services";
    /** Long-press on the menu/search icons opens the addon settings host. */
    public static final String KEY_SEARCH_LINE = "yandexmaps_search_line";
    /** Start-screen search history (SearchStartScreenNewHistoryMode). */
    public static final String KEY_SEARCH_HISTORY = "yandexmaps_search_history";
    /** Premium ("luxury") rubric cards on the search start screen. */
    public static final String KEY_SEARCH_RUBRICS = "yandexmaps_search_rubrics";

    // ru.yandex.yandexmaps.tabnavigation.internal.redux.TabBarNewDesignState
    // Primary ctor: (boolean isRefuelTabForbidden, boolean isTaxiTabForbidden,
    // boolean isUserLoggedIn, csf1 customIcon, TabBarNewDesignConfig config).
    // g() adds the Taxi tab iff !isTaxiTabForbidden and the Refuel tab iff
    // !isRefuelTabForbidden. copy() delegates to this ctor, so one hook covers it.
    private static final String NEW_STATE =
            "ru.yandex.yandexmaps.tabnavigation.internal.redux.TabBarNewDesignState";

    // ru.yandex.yandexmaps.tabnavigation.internal.redux.TabBarOldDesignState
    // Ctor: (boolean taxiTabOnMainScreen, boolean refuelTabOnMainScreen,
    // DiscoveryTabPosition, EditMapProjectInfo, boolean businessTabOnMainScreen).
    private static final String OLD_STATE =
            "ru.yandex.yandexmaps.tabnavigation.internal.redux.TabBarOldDesignState";

    // ru.yandex.yandexmaps.tabnavigation.api.TabBarNewDesignConfig
    // Ctor: (ArrayList<Tab> tabs, boolean hideTitles, boolean dontMove...).
    // Tab enum names: NAVI, REFUEL, TAXI, MT, BOOKMARKS, ORDERS_HISTORY, MISSIONS, CUSTOM.
    private static final String NEW_CONFIG =
            "ru.yandex.yandexmaps.tabnavigation.api.TabBarNewDesignConfig";

    // --- Search start-screen UI (same file: main-screen UI layer) ---

    // ru.yandex.yandexmaps.search.api.controller.SearchFeatureConfig
    // History mode getter F1() returns SearchStartScreenNewHistoryMode
    // (SHORT_ABOVE_RUBRICS / SHORT_BELOW_RUBRICS / FULL_BELOW_RUBRICS / DISABLED).
    // Patcher: hook_history_design (C5788/C4963/C2523(21)), after-hook replaces
    // the result with the enum named by pref history_screen_design.
    private static final String SEARCH_FEATURE_CONFIG =
            "ru.yandex.yandexmaps.search.api.controller.SearchFeatureConfig";
    private static final String HISTORY_MODE_GETTER = "F1";
    private static final String HISTORY_MODE_TYPE =
            "ru.yandex.yandexmaps.search.api.controller.SearchStartScreenNewHistoryMode";

    // Luxury rubrics flag getter v0() returns boolean luxuryRubrics.
    // Patcher: hook_luxury_rubrics (C2881/C1733/C2523(17)), before-hook forces
    // FALSE on a boolean method of the class carrying "luxuryRubrics".
    private static final String LUXURY_GETTER = "v0";

    // ru.yandex.yandexmaps.controls.search.SearchLineView binds the
    // search_line_menu_button_or_search_icon ImageView in its ctors;
    // ru.yandex.yandexmaps.controls.profile.ControlProfile binds
    // control_profile_menu_icon in its ctors.
    // Patcher: hook_module (C0462/C4950/C4596(24)), before-hook on a View-taking
    // method attaches a long-click listener opening the patcher settings.
    private static final String SEARCH_LINE_VIEW =
            "ru.yandex.yandexmaps.controls.search.SearchLineView";
    private static final String CONTROL_PROFILE =
            "ru.yandex.yandexmaps.controls.profile.ControlProfile";
    private static final String ICON_SEARCH_RES = "search_line_menu_button_or_search_icon";
    private static final String ICON_MENU_RES = "control_profile_menu_icon";
    private static final String HOST_PACKAGE = "org.pixel.customparts";
    private static final int LONG_CLICK_TAG = 0x7E1A0001;

    private TabsHooks() {}

    public static void install(final Context context, final ClassLoader cl) {
        hookNewDesignState(context, cl);
        hookOldDesignState(context, cl);
        hookNewDesignConfig(context, cl);
    }

    /** Search start-screen hooks: icon shortcut, history mode, luxury rubrics. */
    public static void installSearch(final Context context, final ClassLoader cl) {
        hookSearchIcons(context, cl);
        hookHistoryMode(context, cl);
        hookLuxuryRubrics(context, cl);
    }

    private static void hookNewDesignState(final Context context, final ClassLoader cl) {
        try {
            final Class<?> cls = Class.forName(NEW_STATE, false, cl);
            boolean hooked = false;
            for (final Constructor<?> c : cls.getDeclaredConstructors()) {
                final Class<?>[] p = c.getParameterTypes();
                if (p.length != 5) continue;
                if (!isBool(p[0]) || !isBool(p[1]) || !isBool(p[2])) continue;
                c.setAccessible(true);
                XposedBridge.hookMethod(c, new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam param) {
                        param.args[0] = Settings.get(context, KEY_REFUEL_TAB, true)
                                ? Boolean.FALSE : Boolean.TRUE;
                        param.args[1] = Settings.get(context, KEY_TAXI_TAB, true)
                                ? Boolean.FALSE : Boolean.TRUE;
                    }
                });
                Log.i(TAG, "hooked new tab state " + c);
                hooked = true;
            }
            if (!hooked) Log.w(TAG, NEW_STATE + ": 5-arg ctor not found");
        } catch (ClassNotFoundException e) {
            Log.w(TAG, NEW_STATE + " not in this process, skip");
        } catch (Throwable t) {
            Log.e(TAG, "new tab state hook failed", t);
        }
    }

    private static void hookOldDesignState(final Context context, final ClassLoader cl) {
        try {
            final Class<?> cls = Class.forName(OLD_STATE, false, cl);
            boolean hooked = false;
            for (final Constructor<?> c : cls.getDeclaredConstructors()) {
                final Class<?>[] p = c.getParameterTypes();
                if (p.length != 5) continue;
                if (!isBool(p[0]) || !isBool(p[1]) || !isBool(p[4])) continue;
                c.setAccessible(true);
                XposedBridge.hookMethod(c, new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam param) {
                        param.args[0] = Settings.get(context, KEY_TAXI_TAB, true)
                                ? Boolean.TRUE : Boolean.FALSE;
                        param.args[1] = Settings.get(context, KEY_REFUEL_TAB, true)
                                ? Boolean.TRUE : Boolean.FALSE;
                    }
                });
                Log.i(TAG, "hooked old tab state " + c);
                hooked = true;
            }
            if (!hooked) Log.w(TAG, OLD_STATE + ": 5-arg ctor not found");
        } catch (ClassNotFoundException e) {
            Log.w(TAG, OLD_STATE + " not in this process, skip");
        } catch (Throwable t) {
            Log.e(TAG, "old tab state hook failed", t);
        }
    }

    private static void hookNewDesignConfig(final Context context, final ClassLoader cl) {
        try {
            final Class<?> cls = Class.forName(NEW_CONFIG, false, cl);
            boolean hooked = false;
            for (final Constructor<?> c : cls.getDeclaredConstructors()) {
                final Class<?>[] p = c.getParameterTypes();
                if (p.length != 3) continue;
                if (!List.class.isAssignableFrom(p[0])) continue;
                if (!isBool(p[1]) || !isBool(p[2])) continue;
                c.setAccessible(true);
                XposedBridge.hookMethod(c, new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam param) {
                        if (!Settings.get(context, KEY_SERVICES, true)) return;
                        try {
                            Object tabs = param.args[0];
                            if (!(tabs instanceof List)) return;
                            List<?> src = (List<?>) tabs;
                            boolean showTaxi = Settings.get(context, KEY_TAXI_TAB, true);
                            boolean showRefuel = Settings.get(context, KEY_REFUEL_TAB, true);
                            if (showTaxi && showRefuel) return;
                            List<Object> kept = new ArrayList<Object>(src.size());
                            for (Object tab : src) {
                                if (!showTaxi && isTab(tab, "TAXI")) continue;
                                if (!showRefuel && isTab(tab, "REFUEL")) continue;
                                kept.add(tab);
                            }
                            param.args[0] = kept;
                            Log.i(TAG, "tab bar filtered to " + kept.size() + "/" + src.size());
                        } catch (Throwable t) {
                            Log.w(TAG, "tab bar filter failed: " + t);
                        }
                    }
                });
                Log.i(TAG, "hooked tab bar config " + c);
                hooked = true;
            }
            if (!hooked) Log.w(TAG, NEW_CONFIG + ": 3-arg ctor not found");
        } catch (ClassNotFoundException e) {
            Log.w(TAG, NEW_CONFIG + " not in this process, skip");
        } catch (Throwable t) {
            Log.e(TAG, "tab bar config hook failed", t);
        }
    }

    private static boolean isBool(Class<?> c) {
        return c == boolean.class || c == Boolean.class;
    }

    private static boolean isTab(Object tab, String name) {
        return tab instanceof Enum && name.equals(((Enum<?>) tab).name());
    }

    private static void hookSearchIcons(final Context context, final ClassLoader cl) {
        hookIconHost(context, cl, SEARCH_LINE_VIEW, ICON_SEARCH_RES);
        hookIconHost(context, cl, CONTROL_PROFILE, ICON_MENU_RES);
    }

    private static void hookIconHost(final Context context, final ClassLoader cl,
            final String className, final String iconRes) {
        try {
            final Class<?> cls = Class.forName(className, false, cl);
            boolean hooked = false;
            for (final Constructor<?> c : cls.getDeclaredConstructors()) {
                c.setAccessible(true);
                XposedBridge.hookMethod(c, new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam param) {
                        if (!Settings.get(context, KEY_SEARCH_LINE, true)) return;
                        try {
                            Object self = param.thisObject;
                            if (!(self instanceof View)) return;
                            final View root = (View) self;
                            if (root.getTag(LONG_CLICK_TAG) != null) return;
                            int id = root.getResources().getIdentifier(
                                    iconRes, "id", "ru.yandex.yandexmaps");
                            if (id == 0) return;
                            View icon = root.findViewById(id);
                            if (icon == null) return;
                            icon.setOnLongClickListener(new View.OnLongClickListener() {
                                @Override public boolean onLongClick(View v) {
                                    try {
                                        PackageManager pm = v.getContext().getPackageManager();
                                        Intent launch = pm.getLaunchIntentForPackage(HOST_PACKAGE);
                                        if (launch == null) return false;
                                        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                        v.getContext().startActivity(launch);
                                        Log.i(TAG, "search icon opens " + HOST_PACKAGE);
                                        return true;
                                    } catch (Throwable t) {
                                        Log.w(TAG, "search icon shortcut failed: " + t);
                                        return false;
                                    }
                                }
                            });
                            root.setTag(LONG_CLICK_TAG, Boolean.TRUE);
                        } catch (Throwable t) {
                            Log.w(TAG, "search icon hook failed: " + t);
                        }
                    }
                });
                hooked = true;
            }
            if (hooked) Log.i(TAG, "hooked search icons in " + className);
            else Log.w(TAG, className + ": no ctors");
        } catch (ClassNotFoundException e) {
            Log.w(TAG, className + " not in this process, skip");
        } catch (Throwable t) {
            Log.e(TAG, "search icon hook failed", t);
        }
    }

    private static void hookHistoryMode(final Context context, final ClassLoader cl) {
        try {
            final Class<?> cls = Class.forName(SEARCH_FEATURE_CONFIG, false, cl);
            final Class<?> modeType = Class.forName(HISTORY_MODE_TYPE, false, cl);
            final Object disabled;
            try {
                disabled = Enum.valueOf(modeType.asSubclass(Enum.class), "DISABLED");
            } catch (Throwable t) {
                Log.w(TAG, HISTORY_MODE_TYPE + ": DISABLED unresolved, skip");
                return;
            }
            boolean hooked = false;
            for (final Method m : cls.getDeclaredMethods()) {
                if (!HISTORY_MODE_GETTER.equals(m.getName())) continue;
                if (m.getParameterTypes().length != 0) continue;
                if (!m.getReturnType().equals(modeType)) continue;
                m.setAccessible(true);
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam param) {
                        if (!Settings.get(context, KEY_SEARCH_HISTORY, true)) {
                            param.setResult(disabled);
                        }
                    }
                });
                Log.i(TAG, "hooked search history mode " + m);
                hooked = true;
            }
            if (!hooked) Log.w(TAG, SEARCH_FEATURE_CONFIG + ": F1() not found");
        } catch (ClassNotFoundException e) {
            Log.w(TAG, SEARCH_FEATURE_CONFIG + " not in this process, skip");
        } catch (Throwable t) {
            Log.e(TAG, "search history hook failed", t);
        }
    }

    private static void hookLuxuryRubrics(final Context context, final ClassLoader cl) {
        try {
            final Class<?> cls = Class.forName(SEARCH_FEATURE_CONFIG, false, cl);
            boolean hooked = false;
            for (final Method m : cls.getDeclaredMethods()) {
                if (!LUXURY_GETTER.equals(m.getName())) continue;
                if (m.getParameterTypes().length != 0) continue;
                if (m.getReturnType() != boolean.class && m.getReturnType() != Boolean.class) continue;
                m.setAccessible(true);
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam param) {
                        if (!Settings.get(context, KEY_SEARCH_RUBRICS, true)) {
                            param.setResult(Boolean.FALSE);
                        }
                    }
                });
                Log.i(TAG, "hooked luxury rubrics flag " + m);
                hooked = true;
            }
            if (!hooked) Log.w(TAG, SEARCH_FEATURE_CONFIG + ": v0() not found");
        } catch (ClassNotFoundException e) {
            Log.w(TAG, SEARCH_FEATURE_CONFIG + " not in this process, skip");
        } catch (Throwable t) {
            Log.e(TAG, "luxury rubrics hook failed", t);
        }
    }
}
