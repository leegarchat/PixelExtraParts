/*
 * Copyright (C) 2026 The Evolution X Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.server;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.TriggerEvent;
import android.hardware.TriggerEventListener;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.PowerManager;
import android.os.Process;
import android.os.SystemClock;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Slog;

import java.util.List;

/** System-side double-tap-to-wake listener. */
public final class DtwSystemService extends SystemService implements SensorEventListener {
    private static final String TAG = "DtwSystemService";

    private static final String DTW_ENABLED = "pixelparts_dtw_enabled";
    private static final String DTW_TAP_COUNT = "pixelparts_dtw_tap_count";
    private static final String DTW_TAP_TIMEOUT_MS = "pixelparts_dtw_tap_timeout_ms";
    private static final String DTW_COORDINATE_CHECK = "pixelparts_dtw_coordinate_check";
    private static final String DTW_MAX_DISTANCE_DP = "pixelparts_dtw_max_distance_dp";

    private static final String DOZE_TAP_GESTURE = "doze_tap_gesture";
    private static final String DOZE_PULSE_ON_SINGLE_TAP = "doze_pulse_on_single_tap";

    private static final String SINGLE_TAP_SENSOR_TYPE = "com.google.sensor.single_touch";
    private static final String SINGLE_TAP_SENSOR_NAME_KEYWORD = "single_touch";
    private static final int DEFAULT_TAP_COUNT = 2;
    private static final int DEFAULT_TIMEOUT_MS = 300;
    private static final int DEFAULT_MAX_DISTANCE_DP = 50;

    private final HandlerThread mHandlerThread;
    private final Handler mHandler;

    private SensorManager mSensorManager;
    private PowerManager mPowerManager;
    private Sensor mSingleTapSensor;
    private boolean mSingleTapUsesTrigger;
    private boolean mSensorRegistered;
    private boolean mEnabled;

    private int mTapCount;
    private long mLastTapTime;
    private float mLastTapX = -1f;
    private float mLastTapY = -1f;
    private Runnable mTapTimeoutRunnable;

    private final ContentObserver mSettingsObserver;

    private final BroadcastReceiver mScreenReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                updateSensorRegistration();
            } else if (Intent.ACTION_SCREEN_ON.equals(action)
                    || Intent.ACTION_USER_PRESENT.equals(action)) {
                unregisterSingleTapSensor();
            }
        }
    };

    private final TriggerEventListener mSingleTapTriggerListener =
            new TriggerEventListener() {
                @Override
                public void onTrigger(TriggerEvent event) {
                    final float[] values = event.values.clone();
                    mHandler.post(() -> {
                        if (!mEnabled) {
                            return;
                        }

                        mSensorRegistered = false;
                        if (!mPowerManager.isInteractive()) {
                            registerSingleTapSensor();
                        }
                        // Do not discard a valid trigger only because the display state changed
                        // while the callback was waiting for the system-service handler.
                        processSingleTap(values);
                    });
                }
            };

    public DtwSystemService(Context context) {
        super(context);
        mHandlerThread = new HandlerThread(TAG, Process.THREAD_PRIORITY_BACKGROUND);
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper());
        mSettingsObserver = new ContentObserver(mHandler) {
            @Override
            public void onChange(boolean selfChange) {
                updateFromSettings();
            }
        };
    }

    @Override
    public void onStart() {
        Slog.i(TAG, "Starting DTW system service");
    }

    @Override
    public void onBootPhase(int phase) {
        if (phase != PHASE_SYSTEM_SERVICES_READY) {
            return;
        }

        mSensorManager = getContext().getSystemService(SensorManager.class);
        mPowerManager = getContext().getSystemService(PowerManager.class);

        final ContentResolver resolver = getContext().getContentResolver();
        resolver.registerContentObserver(
                Settings.Global.getUriFor(DTW_ENABLED), false, mSettingsObserver);
        resolver.registerContentObserver(
                Settings.Secure.getUriFor(DOZE_TAP_GESTURE), false, mSettingsObserver);
        resolver.registerContentObserver(
                Settings.Secure.getUriFor(DOZE_PULSE_ON_SINGLE_TAP), false, mSettingsObserver);

        final IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        getContext().registerReceiverForAllUsers(mScreenReceiver, filter, null, mHandler);

        mHandler.post(this::updateFromSettings);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (!mSingleTapUsesTrigger && isSingleTapSensor(event.sensor)) {
            processSingleTap(event.values);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    private void updateFromSettings() {
        final boolean enabled = Settings.Global.getInt(
                getContext().getContentResolver(), DTW_ENABLED, 0) != 0;
        if (enabled != mEnabled) {
            mEnabled = enabled;
            if (enabled) {
                disableSystemDozeGestures();
            }
        } else if (enabled) {
            disableSystemDozeGestures();
        }
        updateSensorRegistration();
    }

    private void updateSensorRegistration() {
        if (mEnabled && !mPowerManager.isInteractive()) {
            registerSingleTapSensor();
        } else {
            unregisterSingleTapSensor();
        }
    }

    private void registerSingleTapSensor() {
        if (mSensorRegistered || mSensorManager == null || mPowerManager.isInteractive()) {
            return;
        }

        if (mSingleTapSensor == null) {
            mSingleTapSensor = findSingleTapSensor();
        }
        if (mSingleTapSensor == null) {
            Slog.e(TAG, "Single-tap sensor not found");
            return;
        }

        mSingleTapUsesTrigger =
                mSingleTapSensor.getReportingMode() == Sensor.REPORTING_MODE_ONE_SHOT;
        final boolean registered;
        if (mSingleTapUsesTrigger) {
            registered = mSensorManager.requestTriggerSensor(
                    mSingleTapTriggerListener, mSingleTapSensor);
        } else {
            registered = mSensorManager.registerListener(
                    this,
                    mSingleTapSensor,
                    SensorManager.SENSOR_DELAY_FASTEST,
                    mHandler);
        }
        mSensorRegistered = registered;
        Slog.i(TAG, "Single-tap sensor registered=" + registered
                + ", name=" + mSingleTapSensor.getName()
                + ", type=" + mSingleTapSensor.getStringType()
                + ", wakeUp=" + mSingleTapSensor.isWakeUpSensor()
                + ", trigger=" + mSingleTapUsesTrigger);
    }

    private void unregisterSingleTapSensor() {
        if (!mSensorRegistered || mSensorManager == null || mSingleTapSensor == null) {
            resetTapState();
            return;
        }

        if (mSingleTapUsesTrigger) {
            mSensorManager.cancelTriggerSensor(mSingleTapTriggerListener, mSingleTapSensor);
        } else {
            mSensorManager.unregisterListener(this, mSingleTapSensor);
        }
        mSensorRegistered = false;
        resetTapState();
    }

    private Sensor findSingleTapSensor() {
        final List<Sensor> sensors = mSensorManager.getSensorList(Sensor.TYPE_ALL);
        Sensor namedFallback = null;
        Sensor exactFallback = null;
        for (Sensor sensor : sensors) {
            if (SINGLE_TAP_SENSOR_TYPE.equals(sensor.getStringType())) {
                if (sensor.isWakeUpSensor()) {
                    return sensor;
                }
                exactFallback = sensor;
            } else if (sensor.getName() != null
                    && sensor.getName().contains(SINGLE_TAP_SENSOR_NAME_KEYWORD)) {
                if (sensor.isWakeUpSensor()) {
                    namedFallback = sensor;
                } else if (namedFallback == null) {
                    namedFallback = sensor;
                }
            }
        }
        return exactFallback != null ? exactFallback : namedFallback;
    }

    private boolean isSingleTapSensor(Sensor sensor) {
        return SINGLE_TAP_SENSOR_TYPE.equals(sensor.getStringType())
                || (sensor.getName() != null
                && sensor.getName().contains(SINGLE_TAP_SENSOR_NAME_KEYWORD));
    }

    private void processSingleTap(float[] values) {
        final float x = values.length > 0 ? values[0] : 0f;
        final float y = values.length > 1 ? values[1] : 0f;
        final long now = SystemClock.uptimeMillis();
        final int requiredTaps = Math.max(1, Math.min(3, Settings.Global.getInt(
                getContext().getContentResolver(), DTW_TAP_COUNT, DEFAULT_TAP_COUNT)));
        final int timeoutMs = Math.max(100, Math.min(2000, Settings.Global.getInt(
                getContext().getContentResolver(), DTW_TAP_TIMEOUT_MS, DEFAULT_TIMEOUT_MS)));
        final boolean checkCoords = Settings.Global.getInt(
                getContext().getContentResolver(), DTW_COORDINATE_CHECK, 0) != 0;
        final int maxDistanceDp = Math.max(10, Math.min(500, Settings.Global.getInt(
                getContext().getContentResolver(), DTW_MAX_DISTANCE_DP,
                DEFAULT_MAX_DISTANCE_DP)));

        if (mTapCount > 0 && now - mLastTapTime > timeoutMs) {
            resetTapState();
        }

        if (checkCoords && mTapCount > 0) {
            final double dx = x - mLastTapX;
            final double dy = y - mLastTapY;
            final float maxDistancePx = maxDistanceDp
                    * getContext().getResources().getDisplayMetrics().density;
            if (dx * dx + dy * dy > maxDistancePx * maxDistancePx) {
                resetTapState();
            }
        }

        final int processedTapCount = ++mTapCount;
        mLastTapTime = now;
        mLastTapX = x;
        mLastTapY = y;

        if (mTapCount >= requiredTaps) {
            resetTapState();
            wakeUp();
        } else {
            if (mTapTimeoutRunnable != null) {
                mHandler.removeCallbacks(mTapTimeoutRunnable);
            }
            mTapTimeoutRunnable = this::resetTapState;
            mHandler.postDelayed(mTapTimeoutRunnable, timeoutMs);
        }
        Slog.d(TAG, "Processed tap: count=" + processedTapCount + "/" + requiredTaps
                + ", x=" + x + ", y=" + y);
    }

    private void resetTapState() {
        mTapCount = 0;
        mLastTapTime = 0L;
        mLastTapX = -1f;
        mLastTapY = -1f;
        if (mTapTimeoutRunnable != null) {
            mHandler.removeCallbacks(mTapTimeoutRunnable);
            mTapTimeoutRunnable = null;
        }
    }

    private void wakeUp() {
        mPowerManager.wakeUp(
                SystemClock.uptimeMillis(), PowerManager.WAKE_REASON_GESTURE,
                "PixelParts:dtw");
        Slog.i(TAG, "Wake requested by DTW");
    }

    private void disableSystemDozeGestures() {
        final ContentResolver resolver = getContext().getContentResolver();
        if (Settings.Secure.getIntForUser(
                resolver, DOZE_TAP_GESTURE, 0, UserHandle.USER_CURRENT) != 0) {
            Settings.Secure.putIntForUser(
                    resolver, DOZE_TAP_GESTURE, 0, UserHandle.USER_CURRENT);
        }
        if (Settings.Secure.getIntForUser(
                resolver, DOZE_PULSE_ON_SINGLE_TAP, 0, UserHandle.USER_CURRENT) != 0) {
            Settings.Secure.putIntForUser(
                    resolver, DOZE_PULSE_ON_SINGLE_TAP, 0, UserHandle.USER_CURRENT);
        }
    }
}
