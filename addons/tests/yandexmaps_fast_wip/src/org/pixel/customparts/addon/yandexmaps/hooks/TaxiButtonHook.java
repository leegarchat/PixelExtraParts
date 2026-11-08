package org.pixel.customparts.addon.yandexmaps.hooks;

import android.content.Context;
import android.util.Log;

import org.pixel.customparts.addon.yandexmaps.Settings;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

/**
 * Removes the "order taxi" capsule (e.g. "от 300 ₽") from the geoobject
 * placecard footer.
 *
 * Data-level hook with two composition paths (both verified in 31.1.3):
 * 1) actionbuttonslist.a.i(...) appends a TAXI button when taxi info loads —
 *    the button is dropped from the composed list.
 * 2) zbx0.a() builds the house/address card footer with taxi unconditionally —
 *    the Ready state is rebuilt without the TAXI button.
 * Siblings re-layout cleanly because the capsule never reaches the RecyclerView.
 */
public final class TaxiButtonHook {
    private static final String TAG = "PixelPartsYandexFast";
    private static final String COMPOSER =
            "ru.yandex.yandexmaps.placecard.controllers.geoobject.epics.loading.composing.composers.actionbuttonslist.a";
    // NOTE: runtime binary name lives in the default package ("zbx0");
    // jadx renders it as defpackage.zbx0. Verified via dex descriptors.
    private static final String HOUSE_COMPOSER = "zbx0";
    private static final String READY =
            "ru.yandex.yandexmaps.placecard.actionsblock.ActionsBlockState$Ready";

    private TaxiButtonHook() {}

    public static void install(final Context context, final ClassLoader cl) {
        hookListComposer(context, cl);
        hookHouseComposer(context, cl);
    }

    private static void hookListComposer(final Context context, final ClassLoader cl) {
        try {
            final Class<?> composer = Class.forName(COMPOSER, false, cl);
            boolean hooked = false;
            for (final Method m : composer.getDeclaredMethods()) {
                if (!"i".equals(m.getName())) continue;
                Class<?>[] p = m.getParameterTypes();
                if (p.length != 3) continue;
                if (p[2] != boolean.class && p[2] != Boolean.class) continue;
                if (!List.class.isAssignableFrom(m.getReturnType())) continue;
                m.setAccessible(true);
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam param) {
                        if (!Settings.get(context, Settings.KEY_NO_TAXI, true)) return;
                        try {
                            Object result = param.getResult();
                            if (!(result instanceof List)) return;
                            FilterResult r = withoutTaxi((List<?>) result);
                            if (r.dropped > 0) {
                                param.setResult(r.kept);
                                Log.i(TAG, "taxi capsule removed (" + r.dropped + ", list path)");
                            }
                        } catch (Throwable t) {
                            Log.w(TAG, "taxi filter failed: " + t);
                        }
                    }
                });
                Log.i(TAG, "hooked taxi composer " + m);
                hooked = true;
            }
            if (!hooked) Log.w(TAG, COMPOSER + ": composer i() not found");
        } catch (ClassNotFoundException e) {
            Log.w(TAG, COMPOSER + " not in this process, skip");
        } catch (Throwable t) {
            Log.e(TAG, "taxi hook failed", t);
        }
    }

    private static void hookHouseComposer(final Context context, final ClassLoader cl) {
        try {
            final Class<?> composer = Class.forName(HOUSE_COMPOSER, false, cl);
            boolean hooked = false;
            for (final Method m : composer.getDeclaredMethods()) {
                if (!"a".equals(m.getName())) continue;
                if (m.getParameterTypes().length != 0) continue;
                if (!m.getReturnType().getName().endsWith("ActionsBlockState$Ready")) continue;
                m.setAccessible(true);
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam param) {
                        if (!Settings.get(context, Settings.KEY_NO_TAXI, true)) return;
                        try {
                            Object ready = param.getResult();
                            if (ready == null) return;
                            Method getButtons = ready.getClass().getMethod("getButtons");
                            Object buttons = getButtons.invoke(ready);
                            if (!(buttons instanceof List)) return;
                            FilterResult r = withoutTaxi((List<?>) buttons);
                            if (r.dropped == 0) return;
                            Method getErr = ready.getClass().getMethod("getIsErrorOccurred");
                            Method getTaxiLoaded = ready.getClass().getMethod("getIsTaxiInfoLoaded");
                            boolean err = (Boolean) getErr.invoke(ready);
                            boolean loaded = (Boolean) getTaxiLoaded.invoke(ready);
                            Class<?> readyCls = Class.forName(READY, false, cl);
                            Constructor<?> ctor = readyCls.getConstructor(List.class, boolean.class, boolean.class);
                            param.setResult(ctor.newInstance(r.kept, err, loaded));
                            Log.i(TAG, "taxi capsule removed (" + r.dropped + ", house path)");
                        } catch (Throwable t) {
                            Log.w(TAG, "taxi house filter failed: " + t);
                        }
                    }
                });
                Log.i(TAG, "hooked taxi house composer " + m);
                hooked = true;
            }
            if (!hooked) Log.w(TAG, HOUSE_COMPOSER + ": composer a() not found");
        } catch (ClassNotFoundException e) {
            Log.w(TAG, HOUSE_COMPOSER + " not in this process, skip");
        } catch (Throwable t) {
            Log.e(TAG, "taxi house hook failed", t);
        }
    }

    private static final class FilterResult {
        final List<Object> kept;
        final int dropped;
        FilterResult(List<Object> kept, int dropped) {
            this.kept = kept;
            this.dropped = dropped;
        }
    }

    private static FilterResult withoutTaxi(List<?> src) {
        List<Object> kept = new ArrayList<Object>(src.size());
        int dropped = 0;
        for (Object item : src) {
            if (isTaxiButton(item)) {
                dropped++;
                continue;
            }
            kept.add(item);
        }
        return new FilterResult(kept, dropped);
    }

    private static String analyticsName(Object item) throws Exception {
        for (Method m : item.getClass().getMethods()) {
            if (m.getParameterTypes().length == 0
                    && m.getReturnType().getName().contains("ActionBarButtonAnalyticsId")) {
                Object id = m.invoke(item);
                return id == null ? "noid" : ((Enum<?>) id).name();
            }
        }
        throw new NoSuchMethodException("no analytics getter in " + item.getClass().getName());
    }

    private static boolean isTaxiButton(Object item) {
        if (item == null) return false;
        try {
            return "TAXI".equals(analyticsName(item));
        } catch (NoSuchMethodException e) {
            return false;
        } catch (Throwable t) {
            Log.w(TAG, "taxi id check failed: " + t);
            return false;
        }
    }
}
