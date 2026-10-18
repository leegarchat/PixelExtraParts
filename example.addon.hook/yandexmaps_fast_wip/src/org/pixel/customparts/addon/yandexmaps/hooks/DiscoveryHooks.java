package org.pixel.customparts.addon.yandexmaps.hooks;

import android.content.Context;
import android.util.Log;

import org.pixel.customparts.addon.yandexmaps.Settings;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

/**
 * Author-style Discovery declutter, ported from the LSPosed reference patcher
 * (ru.yandex.yandexmaps 31.1.3, verified against decompiled classes).
 *
 * <p>Each install method is gated by its own Settings.Global key (default ON)
 * and degrades to skip-with-log when the target class/method is absent, so a
 * future app update cannot bootloop the process.
 *
 * <p>Ported hooks (target + behavior):
 * <ul>
 *   <li>disc_tab: {@code ...discoveryflow.internal.c#b()} (boolean gate that
 *       reports whether the Discovery tab entry point is available for the
 *       current region) is forced to {@code false}, hiding the Discovery tab
 *       on the main screen. Patcher triple
 *       DISCOVERY_MAIN_TAB/discovery_tab/DiscoveryTabOnMainScreen, DexKit
 *       class key {@code tab_entry_point_regions}.
 *   <li>disc_objects: every no-arg {@code List}-returning method of
 *       {@code nvq} (the {@code Content(items=..., filters=...,
 *       shutterInitialOpenCounter=...)} shutter view-state) — i.e. items and
 *       filters — is replaced with an empty list. Mirrors the patcher's
 *       multi-method match (return type {@code List} in a class using
 *       {@code shutterInitialOpenCounter} + {@code Content}) whose handler
 *       replaces the result with the shared empty-list constant.
 *   <li>disc_button: every no-arg method returning {@code qvq}
 *       ({@code DiscoveryShutterViewState$Data$MapButtonState}, a nullable
 *       Kotlin type) on the shutter content states {@code nvq}/{@code ovq}/
 *       {@code rvq} — i.e. {@code O()} — is replaced with {@code null},
 *       dropping the map button from the Discovery shutter. Mirrors the
 *       patcher's return-type match whose handler replaces the result with
 *       {@code null}.
 * </ul>
 */
public final class DiscoveryHooks {
    private static final String TAG = "PixelPartsYandexFast";

    /** Settings.Global keys (defaults live in settings.json; all default ON). */
    public static final String KEY_DISC_TAB = "yandexmaps_disc_tab";
    public static final String KEY_DISC_OBJECTS = "yandexmaps_disc_objects";
    public static final String KEY_DISC_BUTTON = "yandexmaps_disc_button";

    /** Discovery tab-entry manager (name verified in 31.1.3 dex descriptors). */
    private static final String TAB_MANAGER =
            "ru.yandex.yandexmaps.multiplatform.discoveryflow.internal.c";
    /**
     * Shutter content view-states. NOTE: runtime binary names live in the
     * default package (jadx renders them as {@code defpackage.nvq} etc.);
     * verified via dex descriptors, same as {@code zbx0} in TaxiButtonHook.
     */
    private static final String CONTENT = "nvq";
    private static final String ERROR_STATE = "ovq";
    private static final String NOT_SUPPORTED_STATE = "rvq";
    /** Obfuscated runtime name of DiscoveryShutterViewState$Data$MapButtonState. */
    private static final String MAP_BUTTON_STATE = "qvq";

    private DiscoveryHooks() {}

    public static void installAll(final Context context, final ClassLoader cl) {
        installTab(context, cl);
        installObjects(context, cl);
        installButton(context, cl);
    }

    /** Hides the Discovery tab entry on the main screen (gate forced false). */
    public static void installTab(final Context context, final ClassLoader cl) {
        try {
            final Class<?> manager = Class.forName(TAB_MANAGER, false, cl);
            boolean hooked = false;
            for (final Method m : manager.getDeclaredMethods()) {
                if (m.getParameterTypes().length != 0) continue;
                if (m.getReturnType() != boolean.class
                        && m.getReturnType() != Boolean.class) continue;
                m.setAccessible(true);
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam param) {
                        if (!Settings.get(context, KEY_DISC_TAB, true)) return;
                        try {
                            param.setResult(Boolean.FALSE);
                            Log.i(TAG, "discovery tab entry suppressed (" + m.getName() + "->false)");
                        } catch (Throwable t) {
                            Log.w(TAG, "disc tab suppress failed: " + t);
                        }
                    }
                });
                Log.i(TAG, "hooked discovery tab gate " + m);
                hooked = true;
            }
            if (!hooked) Log.w(TAG, TAB_MANAGER + ": boolean gate not found");
        } catch (ClassNotFoundException e) {
            Log.w(TAG, TAB_MANAGER + " not in this process, skip");
        } catch (Throwable t) {
            Log.e(TAG, "disc tab hook failed", t);
        }
    }

    /** Empties the Discovery shutter items/filters lists. */
    public static void installObjects(final Context context, final ClassLoader cl) {
        try {
            final Class<?> content = Class.forName(CONTENT, false, cl);
            boolean hooked = false;
            for (final Method m : content.getDeclaredMethods()) {
                if (m.getParameterTypes().length != 0) continue;
                if (!List.class.isAssignableFrom(m.getReturnType())) continue;
                m.setAccessible(true);
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam param) {
                        if (!Settings.get(context, KEY_DISC_OBJECTS, true)) return;
                        try {
                            Object r = param.getResult();
                            Log.i(TAG, "disc dbg objects: " + m.getName() + " -> " + (r instanceof List ? "list(" + ((List<?>) r).size() + ")" : String.valueOf(r)));
                            param.setResult(Collections.emptyList());
                            Log.i(TAG, "discovery objects emptied (" + m.getName() + "->[])");
                        } catch (Throwable t) {
                            Log.w(TAG, "disc objects suppress failed: " + t);
                        }
                    }
                });
                Log.i(TAG, "hooked discovery objects " + m);
                hooked = true;
            }
            if (!hooked) Log.w(TAG, CONTENT + ": List getters not found");
        } catch (ClassNotFoundException e) {
            Log.w(TAG, CONTENT + " not in this process, skip");
        } catch (Throwable t) {
            Log.e(TAG, "disc objects hook failed", t);
        }
    }

    /** Drops the map button from the Discovery shutter content states. */
    public static void installButton(final Context context, final ClassLoader cl) {
        for (final String cls : new String[]{CONTENT, ERROR_STATE, NOT_SUPPORTED_STATE}) {
            try {
                final Class<?> state = Class.forName(cls, false, cl);
                boolean hooked = false;
                for (final Method m : state.getDeclaredMethods()) {
                    if (m.getParameterTypes().length != 0) continue;
                    if (!MAP_BUTTON_STATE.equals(m.getReturnType().getName())) continue;
                    m.setAccessible(true);
                    XposedBridge.hookMethod(m, new XC_MethodHook() {
                        @Override protected void afterHookedMethod(MethodHookParam param) {
                            if (!Settings.get(context, KEY_DISC_BUTTON, true)) return;
                            try {
                                param.setResult(null);
                                Log.i(TAG, "discovery button dropped (" + cls + "." + m.getName() + "->null)");
                            } catch (Throwable t) {
                                Log.w(TAG, "disc button suppress failed: " + t);
                            }
                        }
                    });
                    Log.i(TAG, "hooked discovery button " + m);
                    hooked = true;
                }
                if (!hooked) Log.w(TAG, cls + ": MapButtonState getter not found");
            } catch (ClassNotFoundException e) {
                Log.w(TAG, cls + " not in this process, skip");
            } catch (Throwable t) {
                Log.e(TAG, "disc button hook failed", t);
            }
        }
    }
}
