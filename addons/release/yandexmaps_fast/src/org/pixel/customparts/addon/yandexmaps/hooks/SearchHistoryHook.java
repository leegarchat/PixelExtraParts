package org.pixel.customparts.addon.yandexmaps.hooks;

import android.content.Context;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.pixel.customparts.addon.yandexmaps.Settings;

import java.lang.ref.WeakReference;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

/**
 * Selects the History tab by default when search opens.
 *
 * Mechanism: SuggestController.Z0 arms a one-shot; the first tab-strip
 * composition with mode CATEGORIES is rewritten to HISTORY (avoids the
 * Categories flash) and a silent synthetic tap on the History segment
 * (callOnClick, no click sound) drives the genuine selection flow so the
 * body follows the strip. Manual tab switches afterwards pass through
 * untouched; the next search open re-arms.
 */
public final class SearchHistoryHook {
    private static final String TAG = "PixelPartsYandexFast";
    // Suggest screen controller: Z0(View, Bundle) runs when the search UI is (re)created.
    private static final String SUGGEST_CONTROLLER =
            "ru.yandex.yandexmaps.search.internal.suggest.SuggestController";
    // Tab strip state composer: dp71 a(SuggestSegment), index = position of mode.
    private static final String SEGMENT_COMPOSER = "x0f1";

    private static volatile boolean sArmed = true;
    private static volatile WeakReference<View> sRoot;

    private SearchHistoryHook() {}

    public static void install(final Context context, final ClassLoader cl) {
        hookOpen(context, cl);
        hookComposer(context, cl);
    }

    private static void hookOpen(final Context context, final ClassLoader cl) {
        try {
            final Class<?> ctl = Class.forName(SUGGEST_CONTROLLER, false, cl);
            boolean hooked = false;
            for (final java.lang.reflect.Method m : ctl.getDeclaredMethods()) {
                if (!"Z0".equals(m.getName())) continue;
                if (m.getParameterTypes().length != 2) continue;
                m.setAccessible(true);
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam param) {
                        if (!Settings.get(context, Settings.KEY_SEARCH_HISTORY, false)) return;
                        sArmed = true;
                        try {
                            if (param.args[0] instanceof View) {
                                sRoot = new WeakReference<View>((View) param.args[0]);
                            }
                        } catch (Throwable ignored) {}
                        Log.i(TAG, "search shown, history default armed");
                    }
                });
                Log.i(TAG, "hooked search open " + m);
                hooked = true;
            }
            if (!hooked) Log.w(TAG, SUGGEST_CONTROLLER + ": Z0 not found");
        } catch (ClassNotFoundException e) {
            Log.w(TAG, SUGGEST_CONTROLLER + " not in this process, skip");
        } catch (Throwable t) {
            Log.e(TAG, "search open hook failed", t);
        }
    }

    private static void hookComposer(final Context context, final ClassLoader cl) {
        try {
            final Class<?> composer = Class.forName(SEGMENT_COMPOSER, false, cl);
            boolean hooked = false;
            for (final java.lang.reflect.Method m : composer.getDeclaredMethods()) {
                if (!"a".equals(m.getName())) continue;
                if (m.getParameterTypes().length != 1) continue;
                if (!m.getParameterTypes()[0].isEnum()) continue;
                m.setAccessible(true);
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam param) {
                        if (!Settings.get(context, Settings.KEY_SEARCH_HISTORY, false)) return;
                        if (!sArmed) return;
                        try {
                            Object mode = param.args[0];
                            if (mode == null) return;
                            String name = ((Enum<?>) mode).name();
                            if (!"CATEGORIES".equals(name)) return;
                            Object history = Enum.valueOf((Class<Enum>) mode.getClass(), "HISTORY");
                            param.args[0] = history;
                            sArmed = false;
                            Log.i(TAG, "search default tab CATEGORIES->HISTORY");
                            WeakReference<View> ref = sRoot;
                            final View root = ref != null ? ref.get() : null;
                            if (root != null) {
                                root.post(new Runnable() {
                                    @Override public void run() {
                                        tapHistory(root);
                                    }
                                });
                            }
                        } catch (Throwable t) {
                            Log.w(TAG, "search tab rewrite failed: " + t);
                        }
                    }
                });
                Log.i(TAG, "hooked search tab composer " + m);
                hooked = true;
            }
            if (!hooked) Log.w(TAG, SEGMENT_COMPOSER + ": composer a() not found");
        } catch (ClassNotFoundException e) {
            Log.w(TAG, SEGMENT_COMPOSER + " not in this process, skip");
        } catch (Throwable t) {
            Log.e(TAG, "search composer hook failed", t);
        }
    }

    /** Silent synthetic tap on the History segment: drives the genuine
     * selection flow (strip + body), without click sound. */
    private static void tapHistory(View root) {
        try {
            View segmented = findByClassSuffix(root, "SegmentedItemView");
            if (!(segmented instanceof ViewGroup)) return;
            ViewGroup group = (ViewGroup) segmented;
            View fallback = null;
            for (int i = 0; i < group.getChildCount(); i++) {
                View child = group.getChildAt(i);
                if (isHistorySegment(child)) {
                    child.callOnClick();
                    Log.i(TAG, "search history tapped silently");
                    return;
                }
                fallback = child;
            }
            // Fallback: last segment child (child 0 is the selector thumb).
            if (fallback != null) {
                fallback.callOnClick();
                Log.i(TAG, "search history tapped (fallback)");
            }
        } catch (Throwable t) {
            Log.w(TAG, "history tap failed: " + t);
        }
    }

    private static boolean isHistorySegment(View v) {
        try {
            if (v instanceof TextView) {
                CharSequence t = ((TextView) v).getText();
                if (t != null && ("История".contentEquals(t) || "History".contentEquals(t))) return true;
            }
            if (v instanceof ViewGroup) {
                ViewGroup g = (ViewGroup) v;
                for (int i = 0; i < g.getChildCount(); i++) {
                    if (isHistorySegment(g.getChildAt(i))) return true;
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private static View findByClassSuffix(View root, String suffix) {
        try {
            if (root.getClass().getName().endsWith(suffix)) return root;
            if (root instanceof ViewGroup) {
                ViewGroup g = (ViewGroup) root;
                for (int i = 0; i < g.getChildCount(); i++) {
                    View found = findByClassSuffix(g.getChildAt(i), suffix);
                    if (found != null) return found;
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }
}
