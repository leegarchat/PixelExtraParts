package org.pixel.customparts.addon.yandexmaps.hooks;

import android.content.Context;
import android.util.Log;

import org.pixel.customparts.addon.yandexmaps.Settings;

import java.util.Collections;
import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

/**
 * Keeps the discovery bottom sheet collapsed at all times.
 *
 * The sheet is driven by PartialHeaderLayoutManager. Code moves it to the
 * half position (Middle/MiddleEmpty) through E2(anchor, animate) for instant
 * jumps (e.g. address/profile enter) and M2(anchor). Both are downgraded to
 * the stashed Collapsed anchor. Scoping is by layout-manager instance
 * collected from the discovery shutter itself, so search and placecard
 * sheets (different LM instances, SUMMARY/MINI/EXPAND anchors) are untouched.
 * Programmatic adapter-api Middle requests are rewritten too. Drags to
 * Expanded keep working; settles at Middle snap down.
 */
public final class FeedHook {
    private static final String TAG = "PixelPartsYandexFast";
    private static final String SHUTTER_V1 =
            "ru.yandex.yandexmaps.tabnavigation.api.discovery.TabNavigationDiscoveryControllerShutterV1";
    private static final String ADAPTER_IMPL =
            "ru.yandex.yandexmaps.tabnavigation.internal.shutter.e";
    private static final String LM =
            "ru.yandex.maps.uikit.layoutmanagers.header.decomposition.PartialHeaderLayoutManager";

    private static volatile Object sCollapsedAnchor;
    private static volatile Context sAppCtx;
    private static final Set<Object> sDiscoveryLms =
            Collections.newSetFromMap(new java.util.IdentityHashMap<Object, Boolean>());
    private static volatile boolean sMoveHooked;

    private FeedHook() {}

    public static void install(final Context context, final ClassLoader cl) {
        sAppCtx = context.getApplicationContext();
        hookAttach(context, cl);
        hookAdapterApi(context, cl);
        hookMove(cl);
    }

    private static boolean feedOn() {
        Context c = sAppCtx;
        if (c == null) return true;
        return Settings.get(c, Settings.KEY_FEED_COLLAPSED, false);
    }

    private static void hookAttach(final Context context, final ClassLoader cl) {
        try {
            final Class<?> v1 = Class.forName(SHUTTER_V1, false, cl);
            for (final java.lang.reflect.Method m : v1.getDeclaredMethods()) {
                if (!"onAttach".equals(m.getName())) continue;
                if (m.getParameterTypes().length != 1) continue;
                m.setAccessible(true);
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam param) {
                        if (!feedOn()) return;
                        final Object ctrl = param.thisObject;
                        android.view.View view = (android.view.View) param.args[0];
                        rememberLm(view);
                        android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
                        for (long delay : new long[]{0, 1000, 2500, 4000}) {
                            h.postDelayed(new Runnable() {
                                @Override public void run() {
                                    collapseToBottom(ctrl);
                                }
                            }, delay);
                        }
                        view.post(new Runnable() {
                            @Override public void run() {
                                collapseToBottom(ctrl);
                            }
                        });
                    }
                });
                Log.i(TAG, "hooked shutter attach " + m);
                return;
            }
            Log.w(TAG, SHUTTER_V1 + ": onAttach not found");
        } catch (ClassNotFoundException e) {
            Log.w(TAG, SHUTTER_V1 + " not in this process, skip");
        } catch (Throwable t) {
            Log.e(TAG, "feed attach hook failed", t);
        }
    }

    private static void rememberLm(android.view.View shutterView) {
        try {
            java.lang.reflect.Method getLm =
                    shutterView.getClass().getMethod("getLayoutManager");
            Object lm = getLm.invoke(shutterView);
            if (lm != null) sDiscoveryLms.add(lm);
        } catch (Throwable ignored) {}
    }

    private static void collapseToBottom(Object ctrl) {
        try {
            Object anchors = null;
            Object shutter = null;
            for (java.lang.reflect.Field f : ctrl.getClass().getDeclaredFields()) {
                Class<?> t = f.getType();
                if (isAnchorProvider(t)) {
                    f.setAccessible(true);
                    anchors = f.get(ctrl);
                } else if (t.getName().contains("ShutterView")) {
                    f.setAccessible(true);
                    shutter = f.get(ctrl);
                }
            }
            if (anchors == null || shutter == null) {
                Log.w(TAG, "collapse: anchors=" + (anchors != null) + " shutter=" + (shutter != null));
                return;
            }
            rememberLm((android.view.View) shutter);
            java.lang.reflect.Method getLm = shutter.getClass().getMethod("getLayoutManager");
            Object lm = getLm.invoke(shutter);
            java.lang.reflect.Method mM2 = null;
            for (java.lang.reflect.Method m : lm.getClass().getMethods()) {
                if ("M2".equals(m.getName()) && m.getParameterTypes().length == 1
                        && m.getParameterTypes()[0].getName().contains("Anchor")) {
                    mM2 = m;
                    break;
                }
            }
            if (mM2 == null) {
                Log.w(TAG, "collapse: M2 not found");
                return;
            }
            Object best = null;
            for (String getter : new String[]{"a", "b", "d", "e"}) {
                try {
                    java.lang.reflect.Method g = anchors.getClass().getMethod(getter);
                    Object anchor = g.invoke(anchors);
                    if (anchor == null) continue;
                    String an;
                    try { an = String.valueOf(anchor.getClass().getMethod("getName").invoke(anchor)); }
                    catch (Throwable ignored) { an = "?"; }
                    if (best == null && isCollapsedName(an)) best = anchor;
                } catch (Throwable ignored) {}
            }
            if (best != null) {
                sCollapsedAnchor = best;
                mM2.invoke(lm, best);
                Log.i(TAG, "shutter collapsed to bottom");
            } else {
                Log.w(TAG, "collapse: Collapsed anchor not found");
            }
        } catch (Throwable t) {
            Log.w(TAG, "collapse failed: " + t);
        }
    }

    /**
     * Downgrades every half-position move (E2 instant jumps, M2 moves) on the
     * discovery shutter's own layout manager to the bottom anchor.
     */
    private static void hookMove(final ClassLoader cl) {
        synchronized (FeedHook.class) {
            if (sMoveHooked) return;
            sMoveHooked = true;
        }
        try {
            final Class<?> lm = Class.forName(LM, false, cl);
            for (final java.lang.reflect.Method m : lm.getDeclaredMethods()) {
                String n = m.getName();
                Class<?>[] p = m.getParameterTypes();
                boolean target = false;
                if ("M2".equals(n) && p.length == 1 && p[0].getName().contains("Anchor")) target = true;
                if ("E2".equals(n) && p.length == 2 && p[0].getName().contains("Anchor")) target = true;
                if (!target) continue;
                final boolean isE2 = "E2".equals(n);
                m.setAccessible(true);
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam param) {
                        try {
                            if (!feedOn()) return;
                            if (sCollapsedAnchor == null) return;
                            if (!sDiscoveryLms.contains(param.thisObject)) return;
                            // E2(anchor, animate): only downgrade instant programmatic
                            // jumps (animate=false). Animated moves (user drags
                            // settling, smooth expands) are left alone.
                            if (isE2 && !Boolean.FALSE.equals(param.args[1])) return;
                            Object anchor = param.args[0];
                            if (anchor == null) return;
                            String an;
                            try {
                                an = String.valueOf(
                                        anchor.getClass().getMethod("getName").invoke(anchor));
                            } catch (Throwable ignored) {
                                return;
                            }
                            if (isHalfName(an)) {
                                param.args[0] = sCollapsedAnchor;
                                Log.i(TAG, "shutter half->bottom (" + m.getName() + ":" + an + ")");
                            }
                        } catch (Throwable t) {
                            Log.w(TAG, "half downgrade failed: " + t);
                        }
                    }
                });
                Log.i(TAG, "hooked shutter move " + m);
            }
        } catch (Throwable t) {
            Log.e(TAG, "move hook failed", t);
        }
    }

    /**
     * Downgrades programmatic adapter-api requests (e.g. Middle on returns).
     */
    private static void hookAdapterApi(final Context context, final ClassLoader cl) {
        try {
            final Class<?> impl = Class.forName(ADAPTER_IMPL, false, cl);
            boolean hooked = false;
            for (final java.lang.reflect.Method m : impl.getDeclaredMethods()) {
                if (!"e".equals(m.getName())) continue;
                Class<?>[] p = m.getParameterTypes();
                if (p.length != 2) continue;
                if (!p[0].isEnum()) continue;
                m.setAccessible(true);
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam param) {
                        if (!feedOn()) return;
                        try {
                            Object anchor = param.args[0];
                            if (anchor == null) return;
                            String name = ((Enum<?>) anchor).name();
                            if (!"Middle".equals(name)) return;
                            Object collapsed = Enum.valueOf((Class<Enum>) anchor.getClass(), "Collapsed");
                            param.args[0] = collapsed;
                            Log.i(TAG, "shutter anchor Middle->Collapsed");
                        } catch (Throwable t) {
                            Log.w(TAG, "shutter anchor downgrade failed: " + t);
                        }
                    }
                });
                Log.i(TAG, "hooked shutter api " + m);
                hooked = true;
            }
            if (!hooked) Log.w(TAG, ADAPTER_IMPL + ": anchor setter not found");
        } catch (ClassNotFoundException e) {
            Log.w(TAG, ADAPTER_IMPL + " not in this process, skip");
        } catch (Throwable t) {
            Log.e(TAG, "feed api hook failed", t);
        }
    }

    private static boolean isCollapsedName(String an) {
        if (an == null) return false;
        String u = an.toUpperCase();
        return u.contains("COLLAPSED") || u.contains("HIDDEN") || u.contains("BOTTOM") || u.contains("CLOSED");
    }

    private static boolean isHalfName(String an) {
        if (an == null) return false;
        if (isCollapsedName(an)) return false;
        String u = an.toUpperCase();
        return u.contains("MIDDLE") || u.contains("HALF") || u.contains("PEEK") || u.contains("EMPTY");
    }

    private static boolean isAnchorProvider(Class<?> t) {
        try {
            int hits = 0;
            for (java.lang.reflect.Method m : t.getMethods()) {
                String n = m.getName();
                if ((n.equals("a") || n.equals("b") || n.equals("d") || n.equals("e"))
                        && m.getParameterTypes().length == 0
                        && m.getReturnType().getName().contains("Anchor")) {
                    hits++;
                }
            }
            return hits >= 3;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
