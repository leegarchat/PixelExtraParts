package org.pixel.customparts.addon.systemui.hooks;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.view.View;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;


/**
 * Forces the shade to be visually single-layer by disabling the notification scrim layer.
 *
 * On Android 16 QPR1+ (and some ROM variants), QS/behind scrim and notification scrim can be
 * rendered with different tint/alpha, creating a split appearance. The most robust fix is to
 * remove the notifications scrim at the final rendering point.
 */
public class ShadeUnifiedSurfaceHook extends BaseSystemUIHook {

    private static final String KEY_SHADE_BLUR_INTENSITY = "shade_blur_intensity";
    private static final String KEY_SHADE_ZOOM_INTENSITY = "shade_zoom_intensity";
    // Master on/off for the zoom path. Replaces the old
    // "shade_disable_scale_threshold" int slider (removed): OFF forces the
    // blur scale to 1.0 (no zoom, sharp surface), ON applies zoom intensity.
    private static final String KEY_SHADE_ZOOM_ENABLED = "shade_zoom_enabled";
    // Stabilizer window in ms (slider, 0 = off = direct target tracking).
    private static final String KEY_SHADE_ZOOM_SMOOTH_MS = "shade_zoom_smooth_ms";

    // New keys
    private static final String KEY_SHADE_NOTIF_SCRIM_ALPHA = "shade_notif_scrim_alpha";
    private static final String KEY_SHADE_NOTIF_SCRIM_TINT = "shade_notif_scrim_tint";
    private static final String KEY_SHADE_NOTIF_SCRIM_TINT_ENABLED = "shade_notif_scrim_tint_enabled";
    private static final String KEY_SHADE_MAIN_SCRIM_ALPHA = "shade_main_scrim_alpha";
    private static final String KEY_SHADE_MAIN_SCRIM_TINT = "shade_main_scrim_tint";
    private static final String KEY_SHADE_MAIN_SCRIM_TINT_ENABLED = "shade_main_scrim_tint_enabled";

    private static final int DEFAULT_SHADE_BLUR_INTENSITY_PERCENT = 100;
    private static final int DEFAULT_SHADE_ZOOM_INTENSITY_PERCENT = 0;
    private static final int DEFAULT_SHADE_ZOOM_SMOOTH_MS = 300;
    private static final int MAIN_SCRIM_MAX_PERCENT = 138;
    private static final int NOTIF_SCRIM_MAX_PERCENT = 201;
    
    // Default values for new keys (-1 implies "use system default")
    private static final int DEFAULT_SCRIM_ALPHA = -1;
    private static final int DEFAULT_SCRIM_TINT = 0; // 0 usually means no tint override or transparent

    private static final String SCRIM_VIEW_CLASS =
        "com.android.systemui.scrim.ScrimView";

    // Live config model: settings are cached, invalidated via ContentObserver
    // on any shade key change, and re-read on the next frame — no SystemUI
    // restart needed. (Stock EvoX shade janks even unhooked, so the observer
    // path is not the cause; hot paths only pay volatile reads when idle.)
    private static volatile boolean sConfigLoaded;
    private static volatile int sCfgBlurIntensity = DEFAULT_SHADE_BLUR_INTENSITY_PERCENT;
    private static volatile int sCfgZoomIntensity = DEFAULT_SHADE_ZOOM_INTENSITY_PERCENT;
    private static volatile boolean sCfgZoomEnabled = true;
    private static volatile int sCfgSmoothMs = DEFAULT_SHADE_ZOOM_SMOOTH_MS;
    private static volatile int sCfgNotifScrimAlpha = DEFAULT_SCRIM_ALPHA;
    private static volatile int sCfgNotifScrimTint = DEFAULT_SCRIM_TINT;
    private static volatile int sCfgMainScrimAlpha = DEFAULT_SCRIM_ALPHA;
    private static volatile int sCfgMainScrimTint = DEFAULT_SCRIM_TINT;

    // Fast-path flags (avoid any extra math/branching in default mode).
    private static volatile boolean sBlurRadiusScalingActive;
    private static volatile boolean sZoomForceOffActive;
    private static volatile boolean sZoomScalingActive;
    private static volatile boolean sNotifAlphaOverrideActive;
    private static volatile boolean sMainAlphaOverrideActive;
    private static volatile boolean sNotifTintOverrideActive;
    private static volatile boolean sMainTintOverrideActive;

    // Lock-screen state: skip tint on KEYGUARD / BOUNCER / AOD
    private static volatile boolean sIsKeyguardState;

    // Reflection caches for hot paths
    private static volatile Method sGetContextMethod;
    private static volatile Field sScrimNameField;
    private static volatile Field sGroupHeaderField;
    private static volatile Field sTintColorField;
    private static volatile boolean sColorFieldResolved;
    private static volatile Field sDrawableField;
    private static volatile boolean sDrawableFieldResolved;

    // applyBlur() scale arg index cache: -2 unknown, -1 absent, >=0 actual index
    private static volatile int sApplyBlurScaleArgIndex = -2;

    // Per-window smoother states: every blur client window (shade, keyguard,
    // dreams...) runs its own chase from its own current position, so
    // concurrent streams never fight over one shared value. Switching
    // windows interrupts nothing: the idle stream's state just waits.
    // Weak keys — dead windows are GC'd, no manual eviction needed.
    private static final java.util.WeakHashMap<Object, SmoothState> sSmoothStates =
            new java.util.WeakHashMap<>();

    private static final class SmoothState {
        volatile float output = -1f; // -1 = never engaged: anchor only
        volatile float vel;          // satellite velocity (inertia)
        volatile long lastMs;
    }

    // Gravity follower: the smoothed scale is a satellite attracted to the
    // moving target. Pull grows as it closes in (like real gravity),
    // velocity gives inertia, damping lands it dead on target with no orbit
    // wobble. The slider sets gravity strength (0 = off = direct tracking);
    // reverse works identically (symmetric by construction), including the
    // glide back to 0 zoom.
    private static final float G_REF = 0.028f;     // far pull at slider 1000
    private static final float G_SOFT = 0.015f;    // softening (no singularity)
    private static final float G_DAMP = 0.86f;     // velocity retention
    private static final float G_PULL_MAX = 0.09f; // single-frame move bound
    private static final float G_EPS = 0.0015f;    // arrival snap band
    // Fallback window key when applyBlur gets a null root.
    private static final Object SMOOTH_FALLBACK_WINDOW = new Object();

    private static SmoothState smoothStateFor(Object window) {
        synchronized (sSmoothStates) {
            SmoothState st = sSmoothStates.get(window);
            if (st == null) {
                st = new SmoothState();
                sSmoothStates.put(window, st);
            }
            return st;
        }
    }

    private static float smoothCurrent(Object window) {
        synchronized (sSmoothStates) {
            SmoothState st = sSmoothStates.get(window);
            return (st != null) ? st.output : -1f;
        }
    }

    private static Object smoothWindowKey(Object[] args) {
        if (args != null && args.length > 0 && args[0] != null) return args[0];
        return SMOOTH_FALLBACK_WINDOW;
    }

    @Override
    public String getHookId() {
        return "ShadeUnifiedSurfaceHook";
    }

    @Override
    public int getPriority() {
        return 65;
    }

    @Override
    public boolean isEnabled(Context context) {
        if (context == null) return false;
        return true; 
    }

    @Override
    protected void onInit(ClassLoader classLoader) {
        ensureConfigLoaded(getCurrentApplication());
        hookScrimControllerState(classLoader);
        hookScrimViewNotificationAlpha(classLoader);
        hookScrimViewTint(classLoader);
        hookBackgroundBlurRadiusIfPresent(classLoader);
        hookBlurUtils(classLoader);
        hookNotificationGroupHeaderBackground(classLoader);
    }

    private void hookBlurUtils(ClassLoader classLoader) {
        try {
            Class<?> blurUtilsClass = XposedHelpers.findClass("com.android.systemui.statusbar.BlurUtils", classLoader);
            
            XposedBridge.hookAllMethods(blurUtilsClass, "applyBlur", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    try {
                        if (param.args == null || param.args.length < 3) return;

                        int scaleIndex = resolveApplyBlurScaleArgIndex(param.args);
                        if (scaleIndex == -1) return;
                        // Per-window stabilizer: concurrent blur clients never
                        // share one chase value.
                        Object window = smoothWindowKey(param.args);

                        Context app = getCurrentApplication();
                        ensureConfigLoaded(app);
                        if (!sConfigLoaded) return;
                        if (!isShadeTweaksEnabled(app)) return;

                        float originalScale0 = resolveFloatArg(param.args, scaleIndex);
                        if (originalScale0 < 0f) return;
                        // applyBlur radius (args[1] in every overload), -1 if unknown.
                        int radiusEff = -1;
                        if (param.args.length >= 2 && param.args[1] instanceof Integer) {
                            radiusEff = (Integer) param.args[1];
                        }

                        if (sZoomForceOffActive) {
                            param.args[scaleIndex] = smoothScale(1.0f, originalScale0, radiusEff, window);
                            return;
                        }

                        float originalScale = originalScale0;

                        if (!sZoomScalingActive) {
                            // Zoom just disengaged mid-glide: ease back to stock
                            // instead of snapping. Pure-stock frames (never
                            // engaged) pass through untouched.
                            if (smoothCurrent(window) < 0f
                                    || smoothCurrent(window) == originalScale) return;
                            param.args[scaleIndex] = smoothScale(originalScale, originalScale, radiusEff, window);
                            return;
                        }

                        // Keep zoom alive at blur 0: without a blur layer the
                        // scale has nothing to apply to. Force a minimal radius
                        // while the shade is actually open (orig < 1); a closed
                        // shade keeps radius 0 (no cost, nothing to show).
                        // applyBlur radius is args[1] in every overload.
                        if (originalScale < 1.0f && radiusEff == 0) {
                            param.args[1] = 1;
                            radiusEff = 1;
                        }

                        int zoomIntensity = sCfgZoomIntensity;
                        float target = applyZoomCurve(originalScale, zoomIntensity);
                        param.args[scaleIndex] = smoothScale(target, originalScale, radiusEff, window);
                    } catch (Throwable t) {
                        logError("Failed in BlurUtils#applyBlur hook", t);
                    }
                }
            });
            
            log("BlurUtils#applyBlur hook installed");
        } catch (Throwable t) {
            logError("Failed to hook BlurUtils", t);
        }
    }

    /**
     * Progress-relative zoom around STOCK. UI range is -1000..1000,
     * 0 means stock (identity passthrough).
     *
     * Stock shrinks the background as the shade opens (scale 1.0 gliding
     * down by the pushback factor). The zoom NEVER re-anchors the scale: it
     * only scales the live stock deviation d = 1 - orig, so with the shade
     * closed (d = 0) output is exactly stock — no entry jerk. The effect
     * grows with shade progress from 0 to the set boundary:
     * <ul>
     *   <li>zoom &gt; 0: deepen the shrinking,
     *   newScale = orig - PLUS_GAIN * v * d (+1000 lands on the 1/16 floor
     *   at full open).</li>
     *   <li>zoom &lt; 0: reverse toward enlarge,
     *   newScale = orig + MINUS_GAIN * |v| * d (-1000 lands on ENLARGE_CAP
     *   at full open; stock headroom alone is only ~5% pushback, hence the
     *   gain instead of a remap).</li>
     * </ul>
     * Output clamped to [1/16, ENLARGE_CAP]; an exact 0.0 scale downsamples
     * to a 0px surface and kills SystemUI natively (no Java trace).
     */
    private static final float ENLARGE_CAP = 2.0f;
    // Full-setting extra at full open: 1000 * 0.05 (max stock pushback).
    private static final float PLUS_GAIN = 18f;   // ~0.9 extra shrink
    private static final float MINUS_GAIN = 20f;  // ~1.0 extra enlarge

    private float resolveFloatArg(Object[] args, int index) {
        if (args == null || index < 0 || index >= args.length) return -1f;
        Object v = args[index];
        return (v instanceof Float) ? (Float) v : -1f;
    }

    /**
     * Gravity step toward the target. Unset state, or a closed shade
     * (radius 0 = no blur layer on screen), anchors to the live stock value
     * with dead stop so there is never an entry jump and a half-finished
     * glide can never poison the next open. Retargeting just redirects the
     * one satellite — there are no parallel flows to interrupt. Returns the
     * scale to apply and advances the stored state.
     */
    private float smoothScale(float target, float original, int radius, Object window) {
        long now = android.os.SystemClock.uptimeMillis();
        SmoothState st = smoothStateFor(window);
        float x = st.output;
        if (x < 0f || radius == 0) {
            st.output = original;
            st.vel = 0f;
            st.lastMs = now;
            return original;
        }
        int s = sCfgSmoothMs;
        if (s <= 0) {
            st.output = target;
            st.vel = 0f;
            st.lastMs = now;
            return target;
        }
        float r = target - x;
        float dist = Math.abs(r);
        if (dist < G_EPS) {
            st.output = target;
            st.vel = 0f;
            st.lastMs = now;
            return target;
        }
        long dt = now - st.lastMs;
        if (dt < 0) dt = 0;
        // Frame-rate compensation, bounded: bursts advance a little, long
        // gaps don't explode the integrator.
        float kdt = Math.max(0.25f, Math.min(2f, dt / 16.7f));
        float pull = (s / 1000f) * G_REF / (dist * dist + G_SOFT);
        if (pull > G_PULL_MAX) pull = G_PULL_MAX;
        pull *= kdt;
        float v = (st.vel + Math.signum(r) * pull) * G_DAMP;
        float nx = x + v;
        // Absorb overshoot dead: no orbit wobble on a blur scale.
        if ((target - nx) * r < 0) {
            nx = target;
            v = 0f;
        }
        st.output = nx;
        st.vel = v;
        st.lastMs = now;
        return nx;
    }

    private float applyZoomCurve(float originalScale, int zoomIntensity) {
        if (zoomIntensity == 0) return originalScale;
        float d = 1.0f - originalScale;
        if (d < 0f) d = 0f; // closed shade or overshoot: no deviation, stock
        float newScale;
        if (zoomIntensity > 0) {
            newScale = originalScale - PLUS_GAIN * (zoomIntensity / 1000f) * d;
            if (newScale < 0.0625f) newScale = 0.0625f;
        } else {
            newScale = originalScale + MINUS_GAIN * (-zoomIntensity / 1000f) * d;
            if (newScale > ENLARGE_CAP) newScale = ENLARGE_CAP;
        }
        return newScale;
    }

    private void hookScrimControllerState(ClassLoader classLoader) {
        try {
            Class<?> scrimCtrl = XposedHelpers.findClass(
                    "com.android.systemui.statusbar.phone.ScrimController", classLoader);

            XposedBridge.hookAllMethods(scrimCtrl, "transitionTo", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    try {
                        if (param.args != null && param.args.length > 0 && param.args[0] != null) {
                            String stateName = param.args[0].toString();
                            sIsKeyguardState = stateName.contains("KEYGUARD")
                                    || stateName.contains("BOUNCER")
                                    || stateName.contains("PULSING")
                                    || stateName.contains("DREAMING")
                                    || stateName.contains("AOD");
                        }
                    } catch (Throwable t) {
                        logError("Failed in ScrimController state track", t);
                    }
                }
            });

            log("ScrimController#transitionTo state tracker installed");
        } catch (Throwable t) {
            logError("Failed to hook ScrimController#transitionTo", t);
        }
    }

    private void hookScrimViewNotificationAlpha(ClassLoader classLoader) {
        try {
            Class<?> scrimViewClass = XposedHelpers.findClass(SCRIM_VIEW_CLASS, classLoader);

            Set<XC_MethodHook.Unhook> unhooks = XposedBridge.hookAllMethods(
                    scrimViewClass,
                    "setViewAlpha",
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            try {
                                Context hookCtx = getContextFromHookObject(param.thisObject);
                                if (!sConfigLoaded) ensureConfigLoaded(hookCtx);
                                if (!sConfigLoaded) return;
                                if (!isShadeTweaksEnabled(hookCtx)) return;
                                if (!sNotifAlphaOverrideActive && !sMainAlphaOverrideActive) return;

                                String name = getScrimName(param.thisObject);
                                if ("notifications_scrim".equals(name) && sNotifAlphaOverrideActive) {
                                    float alphaVal = sCfgNotifScrimAlpha;
                                    if (alphaVal >= 0 && param.args[0] instanceof Float) {
                                        float original = (Float) param.args[0];
                                        float scalar = toEffectiveScalar((int) alphaVal, NOTIF_SCRIM_MAX_PERCENT);
                                        float newAlpha = original * scalar;
                                        if (newAlpha > 1.0f) newAlpha = 1.0f;
                                        param.args[0] = newAlpha;
                                    }
                                } else if (name != null && (name.contains("behind"))) {
                                    if (sMainAlphaOverrideActive) {
                                        float alphaVal = sCfgMainScrimAlpha;
                                        if (alphaVal >= 0 && param.args[0] instanceof Float) {
                                            float original = (Float) param.args[0];
                                            float scalar = toEffectiveScalar((int) alphaVal, MAIN_SCRIM_MAX_PERCENT);
                                            float newAlpha = original * scalar;
                                            if (newAlpha > 1.0f) newAlpha = 1.0f;
                                            param.args[0] = newAlpha;
                                        }
                                    }
                                }
                            } catch (Throwable t) {
                                logError("Failed in ScrimView#setViewAlpha before-hook", t);
                            }
                        }

                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            // After system updates internals, forcefully apply tint overlay to Drawable
                            applyTintEnforcement(param.thisObject);
                        }
                    }
            );

            log("ScrimView#setViewAlpha hook installed (" + unhooks.size() + " methods hooked)");
        } catch (Throwable t) {
            logError("Failed to hook ScrimView#setViewAlpha", t);
        }
    }

    private void hookScrimViewTint(ClassLoader classLoader) {
        XC_MethodHook recolorCallback = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                try {
                    Context hookCtx = getContextFromHookObject(param.thisObject);
                    if (!sConfigLoaded) ensureConfigLoaded(hookCtx);
                    if (!sConfigLoaded || sIsKeyguardState) return;
                    if (!isShadeTweaksEnabled(hookCtx)) return;
                    if (!sNotifTintOverrideActive && !sMainTintOverrideActive) return;

                    int colorArgIndex = -1;
                    if (param.args != null) {
                        for (int i = 0; i < param.args.length; i++) {
                            if (param.args[i] instanceof Integer) { colorArgIndex = i; break; }
                        }
                    }
                    if (colorArgIndex < 0) return;

                    int original = (Integer) param.args[colorArgIndex];
                    int sysAlpha = (original >>> 24) & 0xFF;
                    String name = getScrimName(param.thisObject);

                    if ("notifications_scrim".equals(name) && sNotifTintOverrideActive) {
                        int newColor = (sysAlpha << 24) | (sCfgNotifScrimTint & 0x00FFFFFF);
                        param.args[colorArgIndex] = newColor;
                    } else if (name != null && (name.contains("behind")) && sMainTintOverrideActive) {
                        int newColor = (sysAlpha << 24) | (sCfgMainScrimTint & 0x00FFFFFF);
                        param.args[colorArgIndex] = newColor;
                    }
                } catch (Throwable t) {
                    logError("Failed in ScrimView setTint recolor", t);
                }
            }

            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                applyTintEnforcement(param.thisObject);
            }
        };

        XC_MethodHook forceEnforcementHook = new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                applyTintEnforcement(param.thisObject);
            }
        };

        try {
            Class<?> scrimViewClass = XposedHelpers.findClass(SCRIM_VIEW_CLASS, classLoader);

            // NOTE: ScrimView has no setScrimColor() method (only setTint/setViewAlpha),
            // so hooking it by name always threw and skipped the enforcement hooks below.
            // Dead hook removed; setTint + update-method enforcement cover recoloring.
            Set<XC_MethodHook.Unhook> u1 = XposedBridge.hookAllMethods(scrimViewClass, "setTint", recolorCallback);

            // Hook any other update methods to ensure our tint survives internal re-draw triggers
            String[] extraMethods = {"setColors", "updateColorWithTint", "updateColors"};
            for (String method : extraMethods) {
                try {
                    XposedBridge.hookAllMethods(scrimViewClass, method, forceEnforcementHook);
                } catch (Throwable ignored) { }
            }

            log("ScrimView colour hooks total: setTint=" + u1.size());
        } catch (Throwable t) {
            logError("Failed to hook ScrimView colour", t);
        }
    }

    private void hookBackgroundBlurRadiusIfPresent(ClassLoader classLoader) {
        // SurfaceControl.Transaction#setBackgroundBlurRadius — primary blur radius path on Android 14+
        try {
            Class<?> txn = XposedHelpers.findClassIfExists("android.view.SurfaceControl$Transaction", null);
            if (txn != null) {
                Set<XC_MethodHook.Unhook> unhooks = XposedBridge.hookAllMethods(
                        txn, "setBackgroundBlurRadius", new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) {
                                try {
                                    if (param.args == null || param.args.length < 2) return;
                                    Object radiusObj = param.args[param.args.length - 1];
                                    if (!(radiusObj instanceof Integer)) return;

                                    Context app = getCurrentApplication();
                                    ensureConfigLoaded(app);
                                    if (!sConfigLoaded) return;
                                    if (!isShadeTweaksEnabled(app)) return;
                                    if (!sBlurRadiusScalingActive) return;

                                    int intensityPercent = sCfgBlurIntensity;

                                    int base = (Integer) radiusObj;
                                    int scaled;
                                    if (intensityPercent <= 0) {
                                        scaled = 0;
                                    } else {
                                        scaled = Math.round(base * (intensityPercent / 100f));
                                        if (scaled < 0) scaled = 0;
                                    }
                                    // Keep zoom alive at blur 0: radius 0 drops
                                    // the blur layer and the scale with it.
                                    // base == 0 means closed shade: keep 0
                                    // (no cost, nothing to show).
                                    if (scaled == 0 && base != 0 && sZoomScalingActive) {
                                        scaled = 1;
                                    }

                                    param.args[param.args.length - 1] = scaled;
                                } catch (Throwable t) {
                                    logError("Failed in SurfaceControl.Transaction#setBackgroundBlurRadius override", t);
                                }
                            }
                        }
                );
                log("SurfaceControl.Transaction#setBackgroundBlurRadius scaler installed (" + unhooks.size() + " methods hooked)");
            }
        } catch (Throwable t) {
            logError("Failed to hook SurfaceControl blur radius", t);
        }
    }

    private void ensureConfigLoaded(Context context) {
        if (sConfigLoaded) return;
        synchronized (ShadeUnifiedSurfaceHook.class) {
            if (sConfigLoaded) return;
            Context ctx = (context != null) ? context : getCurrentApplication();
            if (ctx == null) return;

            int blur = getIntSetting(ctx, KEY_SHADE_BLUR_INTENSITY, DEFAULT_SHADE_BLUR_INTENSITY_PERCENT);
            int zoom = getIntSetting(ctx, KEY_SHADE_ZOOM_INTENSITY, DEFAULT_SHADE_ZOOM_INTENSITY_PERCENT);
            // New boolean switch; legacy int threshold key (if still stored)
            // maps to OFF only when it explicitly disabled zoom before.
            boolean zoomEnabled = isSettingEnabled(ctx, KEY_SHADE_ZOOM_ENABLED, true);
            int smoothMs = getIntSetting(ctx, KEY_SHADE_ZOOM_SMOOTH_MS, DEFAULT_SHADE_ZOOM_SMOOTH_MS);

            int notifAlpha = getIntSetting(ctx, KEY_SHADE_NOTIF_SCRIM_ALPHA, DEFAULT_SCRIM_ALPHA);
            int notifTint = getIntSetting(ctx, KEY_SHADE_NOTIF_SCRIM_TINT, DEFAULT_SCRIM_TINT);
            boolean notifTintEnabled = isSettingEnabled(ctx, KEY_SHADE_NOTIF_SCRIM_TINT_ENABLED, false);
            int mainAlpha = getIntSetting(ctx, KEY_SHADE_MAIN_SCRIM_ALPHA, DEFAULT_SCRIM_ALPHA);
            int mainTint = getIntSetting(ctx, KEY_SHADE_MAIN_SCRIM_TINT, DEFAULT_SCRIM_TINT);
            boolean mainTintEnabled = isSettingEnabled(ctx, KEY_SHADE_MAIN_SCRIM_TINT_ENABLED, false);

            sCfgBlurIntensity = blur;
            sCfgZoomIntensity = zoom;
            sCfgZoomEnabled = zoomEnabled;
            sCfgSmoothMs = smoothMs;
            sCfgNotifScrimAlpha = notifAlpha;
            sCfgNotifScrimTint = notifTint;
            sCfgMainScrimAlpha = mainAlpha;
            sCfgMainScrimTint = mainTint;

            sBlurRadiusScalingActive = (blur != DEFAULT_SHADE_BLUR_INTENSITY_PERCENT);
            sZoomForceOffActive = (blur > 0 && !zoomEnabled);
            // NOTE: no blur gate — zoom stays alive at blur 0 via the
            // minimal-radius floor below.
            sZoomScalingActive = (zoomEnabled && zoom != DEFAULT_SHADE_ZOOM_INTENSITY_PERCENT);
            sNotifAlphaOverrideActive = (notifAlpha >= 0);
            sMainAlphaOverrideActive = (mainAlpha >= 0);
            sNotifTintOverrideActive = notifTintEnabled;
            sMainTintOverrideActive = mainTintEnabled;

            sConfigLoaded = true;
            observeShadeKeysOnce(ctx);
            log("Shade config loaded: blur=" + blur + "% zoom=" + zoom
                    + "% zoomEnabled=" + zoomEnabled + " smoothMs=" + smoothMs
                    + " notifAlpha=" + notifAlpha + " mainAlpha=" + mainAlpha
                    + " notifTint=" + notifTint + "(en=" + notifTintEnabled + ")"
                    + " mainTint=" + mainTint + "(en=" + mainTintEnabled + ")");
        }
    }

    // Live updates without SystemUI restart: any shade key change drops the
    // cached config so the next frame reloads it.
    private void observeShadeKeysOnce(Context context) {
        String[] keys = {
                KEY_SHADE_BLUR_INTENSITY,
                KEY_SHADE_ZOOM_INTENSITY,
                KEY_SHADE_ZOOM_ENABLED,
                KEY_SHADE_ZOOM_SMOOTH_MS,
                KEY_SHADE_NOTIF_SCRIM_ALPHA,
                KEY_SHADE_NOTIF_SCRIM_TINT,
                KEY_SHADE_NOTIF_SCRIM_TINT_ENABLED,
                KEY_SHADE_MAIN_SCRIM_ALPHA,
                KEY_SHADE_MAIN_SCRIM_TINT,
                KEY_SHADE_MAIN_SCRIM_TINT_ENABLED,
        };
        for (String key : keys) {
            final String k = key;
            observeSettingOnce(context, k, new Runnable() {
                @Override
                public void run() {
                    sConfigLoaded = false;
                }
            });
        }
    }

    private Context getContextFromHookObject(Object obj) {
        if (obj == null) return null;
        try {
            Method method = sGetContextMethod;
            if (method == null) {
                method = obj.getClass().getMethod("getContext");
                method.setAccessible(true);
                sGetContextMethod = method;
            }
            Object ctx = method.invoke(obj);
            return (ctx instanceof Context) ? (Context) ctx : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private String getScrimName(Object obj) {
        if (obj == null) return "";
        try {
            Field field = sScrimNameField;
            if (field == null) {
                field = obj.getClass().getDeclaredField("mScrimName");
                field.setAccessible(true);
                sScrimNameField = field;
            }
            Object value = field.get(obj);
            return (value instanceof String) ? (String) value : "";
        } catch (Throwable ignored) {
            return "";
        }
    }

    private int resolveApplyBlurScaleArgIndex(Object[] args) {
        int cached = sApplyBlurScaleArgIndex;
        if (cached >= 0) {
            if (args.length > cached && args[cached] instanceof Float) return cached;
        } else if (cached == -1) {
            return -1;
        }

        int resolved = -1;
        if (args.length >= 4 && args[3] instanceof Float) {
            resolved = 3;
        } else if (args.length >= 3 && args[2] instanceof Float) {
            resolved = 2;
        }
        sApplyBlurScaleArgIndex = resolved;
        return resolved;
    }

    private void applyTintEnforcement(Object scrimView) {
        try {
            if (!sConfigLoaded || sIsKeyguardState) return;
            Context hookCtx = (scrimView instanceof View) ? ((View) scrimView).getContext() : null;
            if (!isShadeTweaksEnabled(hookCtx)) return;
            boolean anyTint = sNotifTintOverrideActive || sMainTintOverrideActive;
            if (!anyTint) return;

            String name = getScrimName(scrimView);
            if ("notifications_scrim".equals(name) && sNotifTintOverrideActive) {
                forceScrimBaseColor(scrimView, sCfgNotifScrimTint);
            } else if (name != null && (name.contains("behind")) && sMainTintOverrideActive) {
                forceScrimBaseColor(scrimView, sCfgMainScrimTint);
            }
        } catch (Throwable t) {
            logError("Failed in applyTintEnforcement", t);
        }
    }

    /**
     * Bypasses internal blending properties and applies the RGB tint directly to the 
     * final underlying drawables. Preserves existing alpha transparency from the system view.
     */
    private void forceScrimBaseColor(Object scrimView, int desiredColor) {
        try {
            View view = (View) scrimView;

            if (!sColorFieldResolved) {
                synchronized (ShadeUnifiedSurfaceHook.class) {
                    if (!sColorFieldResolved) {
                        Class<?> clz = scrimView.getClass();
                        String[] candidates = {"mTintColor", "mScrimColor", "mMainColor",
                                               "mCurrentColor", "mColor", "mTint"};
                        for (String name : candidates) {
                            try {
                                Field f = clz.getDeclaredField(name);
                                f.setAccessible(true);
                                sTintColorField = f;
                                break;
                            } catch (NoSuchFieldException ignored) { }
                        }
                        sColorFieldResolved = true;
                    }
                }
            }

            int sysAlpha = 255;
            if (sTintColorField != null) {
                int current = sTintColorField.getInt(scrimView);
                sysAlpha = (current >>> 24) & 0xFF;
                if (sysAlpha == 0) {
                    sysAlpha = Math.round(view.getAlpha() * 255f);
                }
                // Update variable so getters recognize our override
                int targetColorWithAlpha = (sysAlpha << 24) | (desiredColor & 0x00FFFFFF);
                sTintColorField.setInt(scrimView, targetColorWithAlpha);
            } else {
                sysAlpha = Math.round(view.getAlpha() * 255f);
            }

            if (!sDrawableFieldResolved) {
                synchronized (ShadeUnifiedSurfaceHook.class) {
                    if (!sDrawableFieldResolved) {
                        try {
                            Field f = scrimView.getClass().getDeclaredField("mDrawable");
                            f.setAccessible(true);
                            sDrawableField = f;
                        } catch (Throwable ignored) { }
                        sDrawableFieldResolved = true;
                    }
                }
            }

            // A fully opaque version of the tint. By passing it to SRC_IN, the PorterDuff 
            // logic will naturally inherit the existing alpha layout output by the ScrimView paint.
            int opaqueTint = 0xFF000000 | (desiredColor & 0x00FFFFFF);
            int targetColorWithAlpha = (sysAlpha << 24) | (desiredColor & 0x00FFFFFF);

            boolean appliedToDrawable = false;
            if (sDrawableField != null) {
                Drawable d = (Drawable) sDrawableField.get(scrimView);
                if (d != null) {
                    if (d instanceof ColorDrawable) {
                        ((ColorDrawable) d).setColor(targetColorWithAlpha);
                    } else {
                        d.setColorFilter(new PorterDuffColorFilter(opaqueTint, PorterDuff.Mode.SRC_IN));
                    }
                    appliedToDrawable = true;
                }
            }

            if (!appliedToDrawable) {
                Drawable bg = view.getBackground();
                if (bg instanceof ColorDrawable) {
                    ((ColorDrawable) bg).setColor(targetColorWithAlpha);
                } else if (bg != null) {
                    bg.setColorFilter(new PorterDuffColorFilter(opaqueTint, PorterDuff.Mode.SRC_IN));
                }
            }

            view.invalidate();
        } catch (Throwable t) {
            logError("forceScrimBaseColor failed", t);
        }
    }

    private int clamp(int value, int min, int max) {
        if (value < min) return min;
        if (value > max) return max;
        return value;
    }

    private float toEffectiveScalar(int userPercent, int maxPercent) {
        int clamped = clamp(userPercent, 0, 100);
        return (clamped / 100f) * (maxPercent / 100f);
    }

    private Context getCurrentApplication() {
        try {
            Class<?> atClass = XposedHelpers.findClass("android.app.ActivityThread", null);
            Object app = XposedHelpers.callStaticMethod(atClass, "currentApplication");
            return (app instanceof Context) ? (Context) app : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void hookNotificationGroupHeaderBackground(ClassLoader classLoader) {
        try {
            Class<?> containerClass = XposedHelpers.findClassIfExists(
                    "com.android.systemui.statusbar.notification.stack.NotificationChildrenContainer", 
                    classLoader
            );
            if (containerClass == null) return;

            Set<XC_MethodHook.Unhook> hooks = XposedBridge.hookAllMethods(
                    containerClass, 
                    "recreateNotificationHeader", 
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(XC_MethodHook.MethodHookParam param) {
                            try {
                                Context hookCtx = getContextFromHookObject(param.thisObject);
                                if (!sConfigLoaded) ensureConfigLoaded(hookCtx);
                                if (!sConfigLoaded || !sNotifAlphaOverrideActive) return;
                                if (!isShadeTweaksEnabled(hookCtx)) return;
                                int alphaVal = sCfgNotifScrimAlpha;
                                
                                if (alphaVal >= 0) {
                                    float scalar = toEffectiveScalar(alphaVal, NOTIF_SCRIM_MAX_PERCENT);
                                    if (scalar < 1.0f) {
                                        Object headerView = getGroupHeader(param.thisObject);
                                        if (headerView != null) {
                                            XposedHelpers.callMethod(headerView, "setBackground", (Object) null);
                                        }
                                    }
                                }
                            } catch (Throwable t) {
                                logError("Failed in recreateNotificationHeader hook", t);
                            }
                        }
                    }
            );
            
            // Only log if actually hooked; method removed in Android 16+
            if (hooks.size() > 0) {
                log("NotificationChildrenContainer#recreateNotificationHeader hook installed");
            }
        } catch (Throwable ignored) {
        }
    }

    private Object getGroupHeader(Object obj) {
        if (obj == null) return null;
        try {
            Field field = sGroupHeaderField;
            if (field == null) {
                field = obj.getClass().getDeclaredField("mGroupHeader");
                field.setAccessible(true);
                sGroupHeaderField = field;
            }
            return field.get(obj);
        } catch (Throwable ignored) {
            return null;
        }
    }
}