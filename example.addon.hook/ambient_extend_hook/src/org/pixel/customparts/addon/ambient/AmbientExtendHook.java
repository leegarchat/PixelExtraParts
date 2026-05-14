package org.pixel.customparts.addon.ambient;

import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;

import org.pixel.customparts.core.IAddonHook;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class AmbientExtendHook implements IAddonHook {
    private static final String TAG = "AmbientExtendHook";
    private static final String PACKAGE_SYSTEMUI = "com.android.systemui";
    private static final String ID = "ambient_extend_hook";
    private static final String KEY_PREFIX = ID + "_";

    private static final String KEY_TIMEOUT_ENABLED = KEY_PREFIX + "timeout_enabled";
    private static final String KEY_TIMEOUT_SECONDS = KEY_PREFIX + "timeout_seconds";
    private static final String KEY_TIMEOUT_FADE_MS = KEY_PREFIX + "timeout_fade_ms";
    private static final String KEY_SMART_PIXELS_AOD_ENABLED = KEY_PREFIX + "smart_pixels_aod_enabled";
    private static final String KEY_SMART_PIXELS_AOD_PERCENT = KEY_PREFIX + "smart_pixels_aod_percent";
    private static final String KEY_SMART_PIXELS_SHIFT_ENABLED = KEY_PREFIX + "smart_pixels_shift_enabled";
    private static final String KEY_DIM_ENABLED = KEY_PREFIX + "dim_enabled";
    private static final String KEY_DIM_PERCENT = KEY_PREFIX + "dim_percent";

    private static final int MODE_NONE = 0;
    private static final int MODE_AOD = 1;
    private static final int MODE_DOZE = 2;
    private static final int TYPE_SECURE_SYSTEM_OVERLAY = 2015;
    private static final int PRIVATE_FLAG_TRUSTED_OVERLAY = 1 << 29;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Object stateLock = new Object();

    private Context appContext;
    private WindowManager windowManager;
    private AmbientOverlayView overlayView;
    private String currentStateName = "UNINITIALIZED";
    private Runnable timeoutRunnable;
    private int timeoutGeneration;
    private boolean timeoutBlackoutActive;
    private boolean settingsObserverRegistered;
    private boolean hookInstalled;

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getName() {
        return "Ambient Extend";
    }

    @Override
    public String getAuthor() {
        return "LeeGarBook";
    }

    @Override
    public String getDescription() {
        return "AOD timeout, ambient Smart Pixels, and extra Doze dimming for SystemUI.";
    }

    @Override
    public String getVersion() {
        return "1.0";
    }

    @Override
    public Set<String> getTargetPackages() {
        return Collections.singleton(PACKAGE_SYSTEMUI);
    }

    @Override
    public int getPriority() {
        return 950;
    }

    @Override
    public boolean isEnabled(Context context) {
        return true;
    }

    @Override
    public void handleLoadPackage(Context context, ClassLoader classLoader, String packageName) {
        if (!PACKAGE_SYSTEMUI.equals(packageName)) {
            return;
        }
        try {
            appContext = context != null && context.getApplicationContext() != null
                    ? context.getApplicationContext() : context;
            if (appContext != null) {
                windowManager = (WindowManager) appContext.getSystemService(Context.WINDOW_SERVICE);
                registerSettingsObserver(appContext);
            }
            hookDozeMachine(classLoader);
        } catch (Throwable throwable) {
            logError("Unable to initialize ambient hook", throwable);
        }
    }

    private void hookDozeMachine(ClassLoader classLoader) {
        if (hookInstalled) {
            return;
        }
        try {
            final Class<?> machineClass = XposedHelpers.findClass(
                    "com.android.systemui.doze.DozeMachine", classLoader);
                Class<?> stateEnumClass = XposedHelpers.findClass(
                    "com.android.systemui.doze.DozeMachine$State", classLoader);

            Method transitionMethod = machineClass.getDeclaredMethod(
                    "performTransitionOnComponents", stateEnumClass, stateEnumClass);
            transitionMethod.setAccessible(true);
            XposedBridge.hookMethod(transitionMethod, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    Object newState = param.args != null && param.args.length > 1 ? param.args[1] : null;
                    onDozeStateChanged(stateName(newState));
                }
            });

            hookInstalled = true;
            log("DozeMachine transition hook installed");
        } catch (Throwable primaryError) {
            logError("DozeMachine transition hook failed, installing requestState fallback", primaryError);
            hookDozeMachineFallback(classLoader);
        }
    }

    private void hookDozeMachineFallback(ClassLoader classLoader) {
        try {
            final Class<?> machineClass = XposedHelpers.findClass(
                    "com.android.systemui.doze.DozeMachine", classLoader);
            XposedBridge.hookAllMethods(machineClass, "requestState", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    syncStateFromMachine(param.thisObject);
                }
            });
            hookInstalled = true;
            log("DozeMachine requestState fallback hook installed");
        } catch (Throwable throwable) {
            logError("Unable to hook DozeMachine", throwable);
        }
    }

    private void syncStateFromMachine(Object machine) {
        if (machine == null) {
            return;
        }
        try {
            Object state = XposedHelpers.callMethod(machine, "getState");
            if (state == null) {
                state = XposedHelpers.getObjectField(machine, "mState");
            }
            onDozeStateChanged(stateName(state));
        } catch (Throwable throwable) {
            logError("Unable to read DozeMachine state", throwable);
        }
    }

    private void onDozeStateChanged(final String stateName) {
        if (stateName == null) {
            return;
        }
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                synchronized (stateLock) {
                    currentStateName = stateName;
                }
                applyCurrentState();
            }
        });
    }

    private void applyCurrentState() {
        Context context = appContext;
        if (context == null) {
            return;
        }
        String stateName;
        synchronized (stateLock) {
            stateName = currentStateName;
        }

        Config config = readConfig(context);
        int mode = modeForState(stateName);
        if (!config.timeoutEnabled || !isVisibleAodState(stateName)) {
            timeoutBlackoutActive = false;
        }
        updateOverlay(context, mode, config);
        updateTimeout(stateName, config);
    }

    private void updateTimeout(String stateName, Config config) {
        cancelTimeout();
        if (!config.timeoutEnabled || timeoutBlackoutActive || !isVisibleAodState(stateName)) {
            return;
        }
        final int generation = ++timeoutGeneration;
        timeoutRunnable = new Runnable() {
            @Override
            public void run() {
                if (generation != timeoutGeneration) {
                    return;
                }
                String latestState;
                synchronized (stateLock) {
                    latestState = currentStateName;
                }
                if (!isVisibleAodState(latestState)) {
                    return;
                }
                timeoutBlackoutActive = true;
                updateOverlay(appContext, modeForState(latestState), readConfig(appContext));
                log("AOD timeout switched to full black overlay");
            }
        };
        mainHandler.postDelayed(timeoutRunnable, config.timeoutSeconds * 1000L);
        log("AOD timeout scheduled: " + config.timeoutSeconds + "s for " + stateName);
    }

    private void cancelTimeout() {
        timeoutGeneration++;
        if (timeoutRunnable != null) {
            mainHandler.removeCallbacks(timeoutRunnable);
            timeoutRunnable = null;
        }
    }

    private void updateOverlay(Context context, int mode, Config config) {
        if (context == null) {
            return;
        }
        boolean inDoze = mode == MODE_AOD || mode == MODE_DOZE;
        boolean smartEnabled = inDoze && config.smartPixelsAodEnabled;
        int smartPercent = smartEnabled ? config.smartPixelsAodPercent : 0;
        int dimPercent = inDoze && config.dimEnabled ? config.dimPercent : 0;
        boolean animateDim = false;
        if (inDoze && timeoutBlackoutActive) {
            dimPercent = 100;
            animateDim = true;
        }
        boolean shouldShow = inDoze && ((smartEnabled && smartPercent > 0) || dimPercent > 0);
        if (!shouldShow) {
            removeOverlay();
            return;
        }
        AmbientOverlayView view = ensureOverlay(context);
        if (view != null) {
            view.setOverlayConfig(smartEnabled, smartPercent, config.smartPixelsShiftEnabled,
                    dimPercent, animateDim, config.timeoutFadeMs);
        }
    }

    private AmbientOverlayView ensureOverlay(Context context) {
        if (overlayView != null && overlayView.getParent() != null) {
            return overlayView;
        }
        if (windowManager == null) {
            windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        }
        if (windowManager == null) {
            return null;
        }

        AmbientOverlayView view = new AmbientOverlayView(context, mainHandler);
        WindowManager.LayoutParams params = createOverlayParams(TYPE_SECURE_SYSTEM_OVERLAY);
        try {
            windowManager.addView(view, params);
            overlayView = view;
            log("Ambient overlay added");
            return overlayView;
        } catch (Throwable secureOverlayError) {
            logError("Secure overlay add failed, trying application overlay", secureOverlayError);
            try {
                params = createOverlayParams(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
                windowManager.addView(view, params);
                overlayView = view;
                log("Ambient overlay added as application overlay");
                return overlayView;
            } catch (Throwable fallbackError) {
                logError("Unable to add ambient overlay", fallbackError);
                return null;
            }
        }
    }

    private WindowManager.LayoutParams createOverlayParams(int type) {
        int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
                | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED;
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                type,
                flags,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.setTitle("AmbientExtendHookOverlay");
        params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        addPrivateFlag(params, PRIVATE_FLAG_TRUSTED_OVERLAY);
        return params;
    }

    private void removeOverlay() {
        AmbientOverlayView view = overlayView;
        if (view == null) {
            return;
        }
        overlayView = null;
        try {
            if (windowManager != null && view.getParent() != null) {
                windowManager.removeViewImmediate(view);
            }
        } catch (Throwable throwable) {
            logError("Unable to remove ambient overlay", throwable);
        }
    }

    private void registerSettingsObserver(Context context) {
        if (settingsObserverRegistered || context == null) {
            return;
        }
        settingsObserverRegistered = true;
        ContentObserver observer = new ContentObserver(mainHandler) {
            @Override
            public void onChange(boolean selfChange) {
                applyCurrentState();
            }
        };
        String[] keys = new String[] {
            KEY_TIMEOUT_ENABLED, KEY_TIMEOUT_SECONDS, KEY_TIMEOUT_FADE_MS,
                KEY_SMART_PIXELS_AOD_ENABLED, KEY_SMART_PIXELS_AOD_PERCENT,
                KEY_SMART_PIXELS_SHIFT_ENABLED,
                KEY_DIM_ENABLED, KEY_DIM_PERCENT
        };
        ContentResolver resolver = context.getContentResolver();
        for (String key : keys) {
            try {
                resolver.registerContentObserver(Settings.Global.getUriFor(key), false, observer);
                resolver.registerContentObserver(Settings.Global.getUriFor(key + "_pine"), false, observer);
            } catch (Throwable ignored) {
            }
        }
    }

    private Config readConfig(Context context) {
        Config config = new Config();
        config.timeoutEnabled = getBooleanSetting(context, KEY_TIMEOUT_ENABLED, false);
        config.timeoutSeconds = clamp(getIntSetting(context, KEY_TIMEOUT_SECONDS, 60), 1, 600);
        config.timeoutFadeMs = clamp(getIntSetting(context, KEY_TIMEOUT_FADE_MS, 1200), 0, 10000);
        config.smartPixelsAodEnabled = getBooleanSetting(context, KEY_SMART_PIXELS_AOD_ENABLED, false);
        config.smartPixelsAodPercent = clamp(getIntSetting(context, KEY_SMART_PIXELS_AOD_PERCENT, 25), 0, 100);
        config.smartPixelsShiftEnabled = getBooleanSetting(context, KEY_SMART_PIXELS_SHIFT_ENABLED, false);
        config.dimEnabled = getBooleanSetting(context, KEY_DIM_ENABLED, false);
        config.dimPercent = clamp(getIntSetting(context, KEY_DIM_PERCENT, 30), 0, 100);
        return config;
    }

    private int modeForState(String stateName) {
        if (stateName == null) {
            return MODE_NONE;
        }
        if (stateName.equals("DOZE_AOD")
                || stateName.equals("DOZE_AOD_DOCKED")
                || stateName.equals("DOZE_AOD_MINMODE")
                || stateName.equals("DOZE_AOD_PAUSING")
                || stateName.equals("DOZE_AOD_PAUSED")) {
            return MODE_AOD;
        }
        if (stateName.equals("DOZE")
                || stateName.equals("DOZE_SUSPEND_TRIGGERS")
                || stateName.equals("DOZE_REQUEST_PULSE")
                || stateName.equals("DOZE_PULSING")
                || stateName.equals("DOZE_PULSING_BRIGHT")
                || stateName.equals("DOZE_PULSING_WITHOUT_UI")
                || stateName.equals("DOZE_PULSING_AUTH_UI")
                || stateName.equals("DOZE_PULSE_DONE")) {
            return MODE_DOZE;
        }
        return MODE_NONE;
    }

    private boolean isVisibleAodState(String stateName) {
        return "DOZE_AOD".equals(stateName)
                || "DOZE_AOD_DOCKED".equals(stateName)
                || "DOZE_AOD_MINMODE".equals(stateName);
    }

    private String stateName(Object state) {
        return state != null ? String.valueOf(state) : null;
    }

    private boolean getBooleanSetting(Context context, String key, boolean defaultValue) {
        String value = getSettingString(context, key);
        if (value == null) {
            return defaultValue;
        }
        return "1".equals(value) || "true".equalsIgnoreCase(value) || "on".equalsIgnoreCase(value);
    }

    private int getIntSetting(Context context, String key, int defaultValue) {
        String value = getSettingString(context, key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (Throwable ignored) {
            return defaultValue;
        }
    }

    private String getSettingString(Context context, String key) {
        if (context == null || key == null) {
            return null;
        }
        try {
            String value = Settings.Global.getString(context.getContentResolver(), key);
            if (value == null) {
                value = Settings.Global.getString(context.getContentResolver(), key + "_pine");
            }
            return value;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void addPrivateFlag(WindowManager.LayoutParams params, int flag) {
        try {
            Field field = WindowManager.LayoutParams.class.getDeclaredField("privateFlags");
            field.setAccessible(true);
            field.setInt(params, field.getInt(params) | flag);
        } catch (Throwable ignored) {
        }
    }

    private int clamp(int value, int min, int max) {
        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return value;
    }

    private void log(String message) {
        Log.i(TAG, message);
    }

    private void logError(String message, Throwable throwable) {
        Log.e(TAG, message, throwable);
    }

    private static final class Config {
        boolean timeoutEnabled;
        int timeoutSeconds;
        int timeoutFadeMs;
        boolean smartPixelsAodEnabled;
        int smartPixelsAodPercent;
        boolean smartPixelsShiftEnabled;
        boolean dimEnabled;
        int dimPercent;
    }

    private static final class AmbientOverlayView extends View {
        private static final int TILE_SIZE = 8;
        private static final int TOTAL_PIXELS = TILE_SIZE * TILE_SIZE;
        private static final long SHIFT_INTERVAL_MS = 60000L;
        private static final int[] BAYER_MATRIX = new int[] {
                 0, 32,  8, 40,  2, 34, 10, 42,
                48, 16, 56, 24, 50, 18, 58, 26,
                12, 44,  4, 36, 14, 46,  6, 38,
                60, 28, 52, 20, 62, 30, 54, 22,
                 3, 35, 11, 43,  1, 33,  9, 41,
                51, 19, 59, 27, 49, 17, 57, 25,
                15, 47,  7, 39, 13, 45,  5, 37,
                63, 31, 55, 23, 61, 29, 53, 21
        };

        private final Handler handler;
        private final Paint pixelPaint = new Paint();
        private final Paint dimPaint = new Paint();
        private final Runnable shiftRunnable;
        private final Runnable dimAnimationRunnable;

        private Bitmap patternBitmap;
        private boolean smartPixelsEnabled;
        private boolean shiftEnabled;
        private int smartPixelsPercent;
        private int dimAlpha;
        private int dimAnimationStartAlpha;
        private int dimAnimationTargetAlpha;
        private long dimAnimationStartTime;
        private int dimAnimationDurationMs;
        private int shiftStep;

        AmbientOverlayView(Context context, Handler handler) {
            super(context);
            this.handler = handler;
            setWillNotDraw(false);
            setLayerType(View.LAYER_TYPE_HARDWARE, null);
            setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            pixelPaint.setAntiAlias(false);
            pixelPaint.setDither(false);
            pixelPaint.setFilterBitmap(false);
            shiftRunnable = new Runnable() {
                @Override
                public void run() {
                    if (!smartPixelsEnabled || !shiftEnabled) {
                        return;
                    }
                    shiftStep++;
                    rebuildPattern();
                    invalidate();
                    AmbientOverlayView.this.handler.postDelayed(this, SHIFT_INTERVAL_MS);
                }
            };
            dimAnimationRunnable = new Runnable() {
                @Override
                public void run() {
                    long elapsed = SystemClock.uptimeMillis() - dimAnimationStartTime;
                    float fraction = dimAnimationDurationMs <= 0
                            ? 1f : Math.min(1f, elapsed / (float) dimAnimationDurationMs);
                    float eased = 1f - ((1f - fraction) * (1f - fraction));
                    dimAlpha = dimAnimationStartAlpha
                            + Math.round((dimAnimationTargetAlpha - dimAnimationStartAlpha) * eased);
                    invalidate();
                    if (fraction < 1f) {
                        AmbientOverlayView.this.handler.postDelayed(this, 16L);
                    }
                }
            };
        }

        void setOverlayConfig(boolean smartEnabled, int percent, boolean shift, int dimPercent,
                boolean animateDim, int dimAnimationMs) {
            int clampedPercent = clampStatic(percent, 0, 100);
            int nextDimAlpha = Math.round(clampStatic(dimPercent, 0, 100) * 255f / 100f);
            boolean oldShiftEnabled = shiftEnabled;
            boolean rebuildPattern = patternBitmap == null
                    || smartPixelsPercent != clampedPercent
                    || oldShiftEnabled != shift
                    || (patternBitmap.getWidth() != getWidth() || patternBitmap.getHeight() != getHeight());

            smartPixelsEnabled = smartEnabled && clampedPercent > 0;
            smartPixelsPercent = clampedPercent;
            shiftEnabled = shift;
            if (!shiftEnabled) {
                shiftStep = 0;
            }
            updateDimAlpha(nextDimAlpha, animateDim, dimAnimationMs);

            if (smartPixelsEnabled && rebuildPattern) {
                rebuildPattern();
            } else if (!smartPixelsEnabled) {
                clearPattern();
            }
            updateShiftScheduling();
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (dimAlpha > 0) {
                dimPaint.setColor(Color.argb(dimAlpha, 0, 0, 0));
                canvas.drawRect(0f, 0f, getWidth(), getHeight(), dimPaint);
            }
            if (smartPixelsEnabled && patternBitmap != null) {
                canvas.drawBitmap(patternBitmap, 0f, 0f, pixelPaint);
            }
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            if (smartPixelsEnabled) {
                rebuildPattern();
                invalidate();
            }
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            updateShiftScheduling();
        }

        @Override
        protected void onDetachedFromWindow() {
            handler.removeCallbacks(shiftRunnable);
            handler.removeCallbacks(dimAnimationRunnable);
            clearPattern();
            super.onDetachedFromWindow();
        }

        private void updateDimAlpha(int targetAlpha, boolean animate, int durationMs) {
            handler.removeCallbacks(dimAnimationRunnable);
            if (animate && targetAlpha > dimAlpha && durationMs > 0) {
                dimAnimationStartAlpha = dimAlpha;
                dimAnimationTargetAlpha = targetAlpha;
                dimAnimationStartTime = SystemClock.uptimeMillis();
                dimAnimationDurationMs = durationMs;
                handler.post(dimAnimationRunnable);
                return;
            }
            dimAlpha = targetAlpha;
        }

        private void rebuildPattern() {
            clearPattern();
            if (!smartPixelsEnabled || getWidth() <= 0 || getHeight() <= 0) {
                return;
            }
            int threshold = Math.round(TOTAL_PIXELS * smartPixelsPercent / 100f);
            threshold = clampStatic(threshold, 0, TOTAL_PIXELS);
            int width = getWidth();
            int height = getHeight();
            int offsetX = shiftEnabled ? shiftStep % TILE_SIZE : 0;
            int offsetY = shiftEnabled ? (shiftStep * 3) % TILE_SIZE : 0;
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            int[] row = new int[width];
            for (int y = 0; y < height; y++) {
                int matrixY = (y + offsetY) & (TILE_SIZE - 1);
                for (int x = 0; x < width; x++) {
                    int matrixX = (x + offsetX) & (TILE_SIZE - 1);
                    row[x] = BAYER_MATRIX[matrixY * TILE_SIZE + matrixX] < threshold
                            ? Color.BLACK : Color.TRANSPARENT;
                }
                bitmap.setPixels(row, 0, width, 0, y, width, 1);
            }
            patternBitmap = bitmap;
        }

        private void clearPattern() {
            if (patternBitmap != null) {
                patternBitmap.recycle();
                patternBitmap = null;
            }
        }

        private void updateShiftScheduling() {
            handler.removeCallbacks(shiftRunnable);
            if (isAttachedToWindow() && smartPixelsEnabled && shiftEnabled) {
                handler.postDelayed(shiftRunnable, SHIFT_INTERVAL_MS);
            }
        }

        private static int clampStatic(int value, int min, int max) {
            if (value < min) {
                return min;
            }
            if (value > max) {
                return max;
            }
            return value;
        }
    }
}