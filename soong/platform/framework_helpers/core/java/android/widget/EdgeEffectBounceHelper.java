package android.widget;

import android.content.Context;
import android.graphics.RenderNode;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;

/**
 * Bounce overscroll engine for {@link EdgeEffect}: Settings keys, cached settings,
 * caller detection, delta normalization inputs and pure math helpers.
 *
 * <p>The per-instance physics state (spring, smoothing, normalization window)
 * stays on {@code EdgeEffect} itself; this class owns everything that does not
 * need {@code EdgeEffect} internals, so the weave spec keeps only guards,
 * injection points and thin state wrappers.
 *
 * @hide
 */
public final class EdgeEffectBounceHelper {
    private EdgeEffectBounceHelper() {
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Custom bounce: Settings keys
    // ═══════════════════════════════════════════════════════════════════

    private static final String KEY_ENABLED = "overscroll_enabled";
    private static final String KEY_PACKAGES_CONFIG = "overscroll_packages_config";
    private static final String KEY_PULL_COEFF = "overscroll_pull";
    private static final String KEY_STIFFNESS = "overscroll_stiffness";
    private static final String KEY_DAMPING = "overscroll_damping";
    private static final String KEY_FLING = "overscroll_fling";
    private static final String KEY_PHYSICS_MIN_VEL = "overscroll_physics_min_vel";
    private static final String KEY_PHYSICS_MIN_VAL = "overscroll_physics_min_val";
    private static final String KEY_INPUT_SMOOTH_FACTOR = "overscroll_input_smooth";
    private static final String KEY_ANIMATION_SPEED = "overscroll_anim_speed";
    private static final String KEY_RESISTANCE_EXPONENT = "overscroll_res_exponent";
    private static final String KEY_LERP_MAIN_IDLE = "overscroll_lerp_main_idle";
    private static final String KEY_LERP_MAIN_RUN = "overscroll_lerp_main_run";
    private static final String KEY_DISABLE_ARBITRARY_RENDERING = "overscroll_disable_arbitrary_rendering";
    private static final String KEY_SCALE_MODE = "overscroll_scale_mode";
    private static final String KEY_SCALE_INTENSITY = "overscroll_scale_intensity";
    private static final String KEY_SCALE_LIMIT_MIN = "overscroll_scale_limit_min";
    private static final String KEY_ZOOM_MODE = "overscroll_zoom_mode";
    private static final String KEY_ZOOM_INTENSITY = "overscroll_zoom_intensity";
    private static final String KEY_ZOOM_LIMIT_MIN = "overscroll_zoom_limit_min";
    private static final String KEY_ZOOM_ANCHOR_X = "overscroll_zoom_anchor_x";
    private static final String KEY_ZOOM_ANCHOR_Y = "overscroll_zoom_anchor_y";
    private static final String KEY_H_SCALE_MODE = "overscroll_h_scale_mode";
    private static final String KEY_H_SCALE_INTENSITY = "overscroll_h_scale_intensity";
    private static final String KEY_H_SCALE_LIMIT_MIN = "overscroll_h_scale_limit_min";
    private static final String KEY_SCALE_ANCHOR_Y = "overscroll_scale_anchor_y";
    private static final String KEY_H_SCALE_ANCHOR_X = "overscroll_h_scale_anchor_x";
    private static final String KEY_SCALE_ANCHOR_X_HORIZ = "overscroll_scale_anchor_x_horiz";
    private static final String KEY_H_SCALE_ANCHOR_Y_HORIZ = "overscroll_h_scale_anchor_y_horiz";
    private static final String KEY_ZOOM_ANCHOR_X_HORIZ = "overscroll_zoom_anchor_x_horiz";
    private static final String KEY_ZOOM_ANCHOR_Y_HORIZ = "overscroll_zoom_anchor_y_horiz";
    private static final String KEY_SCALE_INTENSITY_HORIZ = "overscroll_scale_intensity_horiz";
    private static final String KEY_ZOOM_INTENSITY_HORIZ = "overscroll_zoom_intensity_horiz";
    private static final String KEY_H_SCALE_INTENSITY_HORIZ = "overscroll_h_scale_intensity_horiz";
    private static final String KEY_INVERT_ANCHOR = "overscroll_invert_anchor";
    private static final String KEY_RECORD_PATTERNS = "record_patterns_edge_effect";

    // ═══════════════════════════════════════════════════════════════════
    //  Ключи Settings.Global: интеллектуальная нормализация дельт
    //  (Phase 2 — автоматическое подавление усиленных Compose-дельт)
    // ═══════════════════════════════════════════════════════════════════

    // Главный выключатель нормализации: 0 = выкл (дефолт), 1 = вкл
    private static final String KEY_NORM_ENABLED = "overscroll_norm_enabled";

    // Эталонная «нормальная» дельта View-приложения (доля от effectiveSize).
    private static final String KEY_NORM_REF_DELTA = "overscroll_norm_ref_delta";

    // Множитель порога обнаружения. Активация: running_mean > ref × это.
    private static final String KEY_NORM_DETECT_MUL = "overscroll_norm_detect_mul";

    // Фактор нормализации — на сколько домножить усиленную дельту.
    private static final String KEY_NORM_FACTOR = "overscroll_norm_factor";

    // Размер скользящего окна — сколько последних |delta| анализировать.
    private static final String KEY_NORM_WINDOW = "overscroll_norm_window";

    // Плавность перехода — за сколько событий перейти от 1.0 к factor.
    private static final String KEY_NORM_RAMP = "overscroll_norm_ramp";

    // Режим определения caller'а: 0 = только поведение (окно дельт),
    // 1 = стек-трейс + поведение, 2 = только стек-трейс.
    private static final String KEY_NORM_DETECT_MODE = "overscroll_norm_detect_mode";

    // ═══════════════════════════════════════════════════════════════════
    //  Custom bounce: constants
    // ═══════════════════════════════════════════════════════════════════

    /** @hide */
    public static final float FILTER_THRESHOLD = 0.08f;
    /** @hide */
    public static final float MICRO_DELTA_EPS = 0.00035f;
    /** @hide */
    public static final float DIRECTION_FLIP_DAMPING = 0.2f;
    /** @hide */
    public static final float NORMAL_FLIP_DAMPING = 0.65f;
    private static final long SETTINGS_CACHE_TTL_MS = 120L;
    private static final String RECORD_TAG = "EdgePatternRec";

    /** @hide */
    public static boolean isBounceEnabled(Context context, boolean cfgIgnore) {
        if (context == null) {
            return false;
        }
        try {
            if (getIntSetting(context, KEY_ENABLED, 0) != 1) {
                return false;
            }
            if (cfgIgnore) {
                return false;
            }
            return true;
        } catch (Exception ignored) {
            return true;
        }
    }

    /**
     * Refreshes (or reuses, within {@code SETTINGS_CACHE_TTL_MS}) the cached
     * bounce settings. Pure w.r.t. {@code EdgeEffect}: the caller owns storing
     * the returned instance.
     *
     * @hide
     */
    public static SettingsCache refreshSettings(Context context, SettingsCache prev,
            boolean force, long now) {
        SettingsCache cache = prev;
        if (cache == null) {
            cache = new SettingsCache();
            force = true;
        }

        if (!force && (now - cache.updatedAt) < SETTINGS_CACHE_TTL_MS) {
            return cache;
        }

        cache.pullCoeff = getFloatSetting(context, KEY_PULL_COEFF, 1.5141f);
        cache.stiffness = getFloatSetting(context, KEY_STIFFNESS, 148.6191f);
        cache.damping = getFloatSetting(context, KEY_DAMPING, 0.9976f);
        cache.fling = getFloatSetting(context, KEY_FLING, 1.3679f);
        cache.minVel = getFloatSetting(context, KEY_PHYSICS_MIN_VEL, 8.0f);
        cache.minVal = getFloatSetting(context, KEY_PHYSICS_MIN_VAL, 0.6f);
        cache.inputSmooth = getFloatSetting(context, KEY_INPUT_SMOOTH_FACTOR, 0.5f);
        cache.animationSpeedPercent = getFloatSetting(context, KEY_ANIMATION_SPEED, 168.5232f);
        if (cache.animationSpeedPercent < 1.0f) {
            cache.animationSpeedPercent = 1.0f;
        }
        if (cache.animationSpeedPercent > 300.0f) {
            cache.animationSpeedPercent = 300.0f;
        }
        cache.animationSpeedMul = cache.animationSpeedPercent / 100.0f;
        cache.resExponent = getFloatSetting(context, KEY_RESISTANCE_EXPONENT, 4.0f);
        cache.lerpMainIdle = getFloatSetting(context, KEY_LERP_MAIN_IDLE, 0.4f);
        cache.lerpMainRun = getFloatSetting(context, KEY_LERP_MAIN_RUN, 0.6999f);
        cache.disableArbitraryRendering = getIntSetting(context,
                KEY_DISABLE_ARBITRARY_RENDERING, 0) == 1;

        cache.scaleMode = getIntSetting(context, KEY_SCALE_MODE, 0);
        cache.scaleIntensity = getFloatSetting(context, KEY_SCALE_INTENSITY, 0.31f);
        cache.scaleIntensityHoriz = getFloatSetting(context, KEY_SCALE_INTENSITY_HORIZ, 0.3786f);
        cache.scaleLimitMin = getFloatSetting(context, KEY_SCALE_LIMIT_MIN, 0.1f);

        cache.zoomMode = getIntSetting(context, KEY_ZOOM_MODE, 0);
        cache.zoomIntensity = getFloatSetting(context, KEY_ZOOM_INTENSITY, 0.2f);
        cache.zoomIntensityHoriz = getFloatSetting(context, KEY_ZOOM_INTENSITY_HORIZ, 0.2f);
        cache.zoomLimitMin = getFloatSetting(context, KEY_ZOOM_LIMIT_MIN, 0.1f);
        cache.zoomAnchorX = getFloatSetting(context, KEY_ZOOM_ANCHOR_X, 0.5f);
        cache.zoomAnchorY = getFloatSetting(context, KEY_ZOOM_ANCHOR_Y, 0.5f);
        cache.zoomAnchorXHoriz = getFloatSetting(context, KEY_ZOOM_ANCHOR_X_HORIZ, 0.5f);
        cache.zoomAnchorYHoriz = getFloatSetting(context, KEY_ZOOM_ANCHOR_Y_HORIZ, 0.5f);

        cache.hScaleMode = getIntSetting(context, KEY_H_SCALE_MODE, 0);
        cache.hScaleIntensity = getFloatSetting(context, KEY_H_SCALE_INTENSITY, 0.2f);
        cache.hScaleIntensityHoriz = getFloatSetting(context, KEY_H_SCALE_INTENSITY_HORIZ, 0.0f);
        cache.hScaleLimitMin = getFloatSetting(context, KEY_H_SCALE_LIMIT_MIN, 0.1f);

        cache.scaleAnchorY = getFloatSetting(context, KEY_SCALE_ANCHOR_Y, 0.5f);
        cache.hScaleAnchorX = getFloatSetting(context, KEY_H_SCALE_ANCHOR_X, 0.5f);
        cache.scaleAnchorXHoriz = getFloatSetting(context, KEY_SCALE_ANCHOR_X_HORIZ, 0.5f);
        cache.hScaleAnchorYHoriz = getFloatSetting(context, KEY_H_SCALE_ANCHOR_Y_HORIZ, 0.5f);
        cache.invertAnchor = getIntSetting(context, KEY_INVERT_ANCHOR, 1) == 1;
        cache.recordPatterns = getIntSetting(context, KEY_RECORD_PATTERNS, 0) == 1;

        // ── Delta normalization keys ──
        cache.normEnabled = getIntSetting(context, KEY_NORM_ENABLED, 1) == 1;
        cache.normRefDelta = getFloatSetting(context, KEY_NORM_REF_DELTA, 9.9999f);
        cache.normDetectMul = getFloatSetting(context, KEY_NORM_DETECT_MUL, 0f);
        cache.normFactor = getFloatSetting(context, KEY_NORM_FACTOR, 0.33f);
        cache.normWindow = (int) getFloatSetting(context, KEY_NORM_WINDOW, 2f);
        cache.normRamp = (int) getFloatSetting(context, KEY_NORM_RAMP, 1f);
        cache.normDetectMode = getIntSetting(context, KEY_NORM_DETECT_MODE, 1);
        if (cache.normWindow < 2) {
            cache.normWindow = 2;
        }
        if (cache.normWindow > 64) {
            cache.normWindow = 64;
        }
        if (cache.normRamp < 1) {
            cache.normRamp = 1;
        }
        if (cache.normDetectMode < 0 || cache.normDetectMode > 2) {
            cache.normDetectMode = 1;
        }

        cache.updatedAt = now;
        return cache;
    }

    /**
     * Parses the per-package config entry ({@code pattern:filter:scale[:ignore]})
     * for the calling package. Never throws: returns defaults on any failure.
     *
     * @hide
     */
    public static BounceConfig parsePackageConfig(Context context) {
        float scale = 1.0f;
        boolean filter = false;
        boolean ignore = false;
        try {
            String pkgName = context != null ? context.getPackageName() : null;
            String configString = getStringSetting(context, KEY_PACKAGES_CONFIG);
            if (!TextUtils.isEmpty(configString) && pkgName != null) {
                String[] apps = configString.split(" ");
                for (String appConfig : apps) {
                    String[] parts = appConfig.split(":");
                    if (parts.length >= 3 && matchesWildcard(parts[0], pkgName)) {
                        filter = Integer.parseInt(parts[1]) == 1;
                        scale = Float.parseFloat(parts[2]);
                        if (parts.length >= 4) {
                            ignore = parts[3].equals("1");
                        }
                        break;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return new BounceConfig(scale, filter, ignore);
    }

    /**
     * Returns {@code {screenHeight, screenWidth}} in px, with safe defaults.
     * Never throws.
     *
     * @hide
     */
    public static float[] getScreenSize(Context context) {
        float screenHeight = 2200f;
        float screenWidth = 1080f;
        try {
            WindowManager wm = context != null
                    ? (WindowManager) context.getSystemService(Context.WINDOW_SERVICE) : null;
            if (wm != null) {
                DisplayMetrics dm = new DisplayMetrics();
                wm.getDefaultDisplay().getMetrics(dm);
                screenHeight = dm.heightPixels;
                screenWidth = dm.widthPixels;
            }
        } catch (Exception ignored) {
        }
        return new float[]{screenHeight, screenWidth};
    }

    /**
     * Анализирует стек-трейс при создании EdgeEffect, чтобы определить,
     * кто инициировал создание — Compose или классический View.
     *
     * @return строка вида "compose:ClassName", "view:ClassName" или "unknown"
     * @hide
     */
    public static String detectCaller() {
        // Compose-характерные фрагменты в имени класса
        final String[] COMPOSE_SIGS = {
            "AndroidEdgeEffectOverscrollEffect",
            "OverscrollKt",
            "androidx.compose.foundation",
            "EdgeEffectCompat"                     // иногда Compose идёт через EdgeEffectCompat
        };
        // Классические View-caller'ы
        final String[] VIEW_SIGS = {
            "RecyclerView",
            "ScrollView",
            "AbsListView",
            "NestedScrollView",
            "ListView",
            "HorizontalScrollView",
            "ViewPager"
        };

        try {
            StackTraceElement[] stack = Thread.currentThread().getStackTrace();
            // Пропускаем первые фреймы (getStackTrace, detectCaller, initCustomInstance, <init>)
            int startIdx = Math.min(4, stack.length);

            // Сначала ищем Compose (приоритет — он ближе к вызову)
            for (int i = startIdx; i < stack.length; i++) {
                String cls = stack[i].getClassName();
                for (String sig : COMPOSE_SIGS) {
                    if (cls.contains(sig)) {
                        String shortName = cls.substring(cls.lastIndexOf('.') + 1);
                        return "compose:" + shortName;
                    }
                }
            }
            // Если Compose не найден, ищем View
            for (int i = startIdx; i < stack.length; i++) {
                String cls = stack[i].getClassName();
                for (String sig : VIEW_SIGS) {
                    if (cls.contains(sig)) {
                        String shortName = cls.substring(cls.lastIndexOf('.') + 1);
                        return "view:" + shortName;
                    }
                }
            }
        } catch (Throwable ignored) {
            // Стек-трейс может не быть доступен в некоторых окружениях
        }
        return "unknown";
    }

    /**
     * Logs one onPull event via logcat with tag RECORD_TAG.
     * Active when global setting record_patterns_edge_effect = 1.
     * Format: elapsed_ns\tpkg\tgesture_id\tseq\tdt_us\traw\tcorrected\tfiltered\tdisp\tspring\tsize\tcaller
     * Use `adb logcat -s EdgePatternRec:D` to capture on PC.
     *
     * @hide
     */
    public static void recordPattern(String callerInfo, long elapsedNs, String pkg, int gestureId,
            int seq, long dtUs, float raw, float corrected, float filtered, float disp,
            float springVal, float effSize) {
        String info = (callerInfo != null) ? callerInfo : "?";
        Log.d(RECORD_TAG, elapsedNs + "\t" + pkg + "\t" + gestureId + "\t" + seq + "\t" + dtUs
                + "\t" + raw + "\t" + corrected + "\t" + filtered
                + "\t" + disp + "\t" + springVal + "\t" + effSize + "\t" + info);
    }

    /** @hide */
    public static float calcScale(int mode, float intensity, float limit, float ratio) {
        if (mode == 0 || intensity <= 0) {
            return 1.0f;
        }
        if (mode == 1) {
            return Math.max(1.0f - (ratio * intensity), limit);
        }
        if (mode == 2) {
            return 1.0f + (ratio * intensity);
        }
        return 1.0f;
    }

    /** @hide */
    public static float lerp(float start, float end, float factor) {
        return start + (end - start) * factor;
    }

    /** @hide */
    public static boolean matchesWildcard(String pattern, String text) {
        if (pattern.equals("*")) {
            return true;
        }
        if (!pattern.contains("*")) {
            return pattern.equals(text);
        }
        // Split by '*' and match segments in order
        String[] segments = pattern.split("\\*", -1);
        int textIdx = 0;
        for (int i = 0; i < segments.length; i++) {
            String seg = segments[i];
            if (seg.isEmpty()) {
                continue;
            }
            int found = text.indexOf(seg, textIdx);
            if (found < 0) {
                return false;
            }
            // First segment must match at start if pattern doesn't start with '*'
            if (i == 0 && !pattern.startsWith("*") && found != 0) {
                return false;
            }
            textIdx = found + seg.length();
        }
        // Last segment must match at end if pattern doesn't end with '*'
        if (!pattern.endsWith("*")) {
            String lastSeg = segments[segments.length - 1];
            if (!lastSeg.isEmpty() && !text.endsWith(lastSeg)) {
                return false;
            }
        }
        return true;
    }

    /** @hide */
    public static float getFloatSetting(Context context, String key, float def) {
        if (context == null) {
            return def;
        }
        try {
            return Settings.Global.getFloat(context.getContentResolver(), key, def);
        } catch (Exception ignored) {
            return def;
        }
    }

    /** @hide */
    public static int getIntSetting(Context context, String key, int def) {
        if (context == null) {
            return def;
        }
        try {
            return Settings.Global.getInt(context.getContentResolver(), key, def);
        } catch (Exception ignored) {
            return def;
        }
    }

    /** @hide */
    public static String getStringSetting(Context context, String key) {
        if (context == null) {
            return null;
        }
        try {
            return Settings.Global.getString(context.getContentResolver(), key);
        } catch (Exception ignored) {
            return null;
        }
    }

    /** @hide */
    public static void safeResetRenderNode(RenderNode renderNode,
            float viewWidth, float viewHeight) {
        if (renderNode == null) {
            return;
        }
        try {
            renderNode.setTranslationX(0f);
            renderNode.setTranslationY(0f);
            renderNode.setScaleX(1f);
            renderNode.setScaleY(1f);
            // [FIX] Pass actual W/H to stretch() — stretch(0,0,0,0) may not properly clear
            float sw = viewWidth > 0 ? viewWidth : 1f;
            float sh = viewHeight > 0 ? viewHeight : 1f;
            renderNode.stretch(0f, 0f, sw, sh);
        } catch (Throwable ignored) {
        }
    }

    /** @hide */
    public static final class SettingsCache {
        /** @hide */ public long updatedAt;
        /** @hide */ public float pullCoeff;
        /** @hide */ public float stiffness;
        /** @hide */ public float damping;
        /** @hide */ public float fling;
        /** @hide */ public float minVel;
        /** @hide */ public float minVal;
        /** @hide */ public float inputSmooth;
        /** @hide */ public float animationSpeedPercent;
        /** @hide */ public float animationSpeedMul;
        /** @hide */ public float resExponent;
        /** @hide */ public float lerpMainIdle;
        /** @hide */ public float lerpMainRun;
        /** @hide */ public boolean disableArbitraryRendering;
        /** @hide */ public int scaleMode;
        /** @hide */ public float scaleIntensity;
        /** @hide */ public float scaleIntensityHoriz;
        /** @hide */ public float scaleLimitMin;
        /** @hide */ public int zoomMode;
        /** @hide */ public float zoomIntensity;
        /** @hide */ public float zoomIntensityHoriz;
        /** @hide */ public float zoomLimitMin;
        /** @hide */ public float zoomAnchorX;
        /** @hide */ public float zoomAnchorY;
        /** @hide */ public float zoomAnchorXHoriz;
        /** @hide */ public float zoomAnchorYHoriz;
        /** @hide */ public int hScaleMode;
        /** @hide */ public float hScaleIntensity;
        /** @hide */ public float hScaleIntensityHoriz;
        /** @hide */ public float hScaleLimitMin;
        /** @hide */ public float scaleAnchorY;
        /** @hide */ public float hScaleAnchorX;
        /** @hide */ public float scaleAnchorXHoriz;
        /** @hide */ public float hScaleAnchorYHoriz;
        /** @hide */ public boolean invertAnchor;
        /** @hide */ public boolean recordPatterns;
        // ── Delta normalization ──
        /** @hide */ public boolean normEnabled;
        /** @hide */ public float normRefDelta;
        /** @hide */ public float normDetectMul;
        /** @hide */ public float normFactor;
        /** @hide */ public int normWindow;
        /** @hide */ public int normRamp;
        /** @hide */ public int normDetectMode;  // 0=behavior, 1=hybrid, 2=stacktrace-only
    }

    /** Per-package bounce config parsed from {@code overscroll_packages_config}. @hide */
    public static final class BounceConfig {
        /** @hide */ public final float scale;
        /** @hide */ public final boolean filter;
        /** @hide */ public final boolean ignore;

        BounceConfig(float scale, boolean filter, boolean ignore) {
            this.scale = scale;
            this.filter = filter;
            this.ignore = ignore;
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Custom bounce: SpringDynamics
    // ═══════════════════════════════════════════════════════════════════

    /**
     * @hide
     */
    public static class SpringDynamics {
        private float mStiffness = 450.0f;
        private float mDampingRatio = 0.7f;
        private float mMinVel = 1.0f;
        private float mMinVal = 0.5f;
        private float mSpeedMultiplier = 1.0f;

        /** @hide */ public float mValue;
        /** @hide */ public float mVelocity;
        /** @hide */ public float mTargetValue = 0f;
        private boolean mIsRunning = false;
        private long mLastFrameTimeNanos = 0;

        /** @hide */
        public void setParams(float stiffness, float damping, float minVel, float minVal) {
            mStiffness = stiffness > 0 ? stiffness : 0.1f;
            mDampingRatio = damping >= 0 ? damping : 0;
            mMinVel = minVel;
            mMinVal = minVal;
        }

        /** @hide */
        public void setSpeedMultiplier(float speedMultiplier) {
            if (speedMultiplier < 0.01f) {
                speedMultiplier = 0.01f;
            }
            mSpeedMultiplier = speedMultiplier;
        }

        /** @hide */
        public void setTargetValue(float targetValue) {
            mTargetValue = targetValue;
        }

        /** @hide */
        public void setVelocity(float velocity) {
            mVelocity = velocity;
        }

        /** @hide */
        public boolean isRunning() {
            return mIsRunning;
        }

        /** @hide */
        public void cancel() {
            mIsRunning = false;
        }

        /** @hide */
        public void start() {
            if (mIsRunning) {
                return;
            }
            mIsRunning = true;
            mLastFrameTimeNanos = System.nanoTime();
        }

        /** @hide */
        public void doFrame(long frameTimeNanos) {
            if (!mIsRunning) {
                return;
            }
            long deltaTimeNanos = frameTimeNanos - mLastFrameTimeNanos;
            if (deltaTimeNanos > 100_000_000) {
                deltaTimeNanos = 16_000_000;
            }
            mLastFrameTimeNanos = frameTimeNanos;
            float dt = (deltaTimeNanos / 1_000_000_000.0f) * mSpeedMultiplier;

            float displacement = mValue - mTargetValue;
            float dampingCoefficient = 2 * mDampingRatio * (float) Math.sqrt(mStiffness);
            float force = -mStiffness * displacement - dampingCoefficient * mVelocity;

            if (Float.isNaN(force) || Float.isInfinite(force)) {
                force = 0;
            }

            mVelocity += force * dt;
            mValue += mVelocity * dt;

            if (Float.isNaN(mValue) || Float.isInfinite(mValue)) {
                mValue = mTargetValue;
                mVelocity = 0;
                cancel();
                return;
            }

            if (Math.abs(mVelocity) < mMinVel && Math.abs(mValue - mTargetValue) < mMinVal) {
                mValue = mTargetValue;
                mVelocity = 0;
                cancel();
            }
        }
    }
}
