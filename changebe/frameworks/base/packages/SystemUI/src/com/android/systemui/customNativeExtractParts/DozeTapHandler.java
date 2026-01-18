package com.android.systemui.customNativeExtractParts;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.ViewConfiguration;

public class DozeTapHandler {
    
    private static final String KEY_HOOK = "doze_double_tap_hook";
    private static final String KEY_TIMEOUT = "doze_double_tap_timeout";

    private final Context mContext;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    
    // State
    private boolean mDoubleTapPending = false;
    private float mLastTapX = -1;
    private float mLastTapY = -1;
    private int mDoubleTapSlop = -1;

    private final Runnable mTimeoutRunnable = () -> {
        resetState();
    };

    public DozeTapHandler(Context context) {
        mContext = context;
    }

    private void resetState() {
        mDoubleTapPending = false;
        mLastTapX = -1;
        mLastTapY = -1;
    }

    /**
     * @param resetSensorAction Runnable, который перезапускает сенсор (reregisterTapSensor).
     * @return true, если событие обработано (надо проигнорировать оригинальный тап).
     */
    public boolean processTap(float x, float y, Runnable resetSensorAction) {
        boolean isEnabled = Settings.Secure.getInt(mContext.getContentResolver(), KEY_HOOK, 0) == 1;
        if (!isEnabled) return false;

        if (mDoubleTapSlop < 0) {
            mDoubleTapSlop = ViewConfiguration.get(mContext).getScaledDoubleTapSlop();
        }

        int timeout = Settings.Secure.getInt(mContext.getContentResolver(), KEY_TIMEOUT, 200);

        if (!mDoubleTapPending) {
            // --- TAP 1 ---
            mDoubleTapPending = true;
            mLastTapX = x;
            mLastTapY = y;

            mHandler.removeCallbacks(mTimeoutRunnable);
            mHandler.postDelayed(mTimeoutRunnable, timeout);

            // Мгновенный сброс сенсора, чтобы поймать второй тап
            if (resetSensorAction != null) {
                resetSensorAction.run();
            }
            return true; // Съедаем событие
        } else {
            // --- TAP 2 ---
            float dx = Math.abs(x - mLastTapX);
            float dy = Math.abs(y - mLastTapY);
            
            // Pixel fix: иногда координаты 0 или -1
            boolean invalidCoords = (x <= 0 && mLastTapX <= 0);
            boolean isClose = invalidCoords || (dx < mDoubleTapSlop && dy < mDoubleTapSlop);

            if (isClose) {
                // SUCCESS
                resetState();
                mHandler.removeCallbacks(mTimeoutRunnable);
                return false; // Пропускаем событие дальше -> WakeUp
            } else {
                // FAIL -> Новый TAP 1
                mLastTapX = x;
                mLastTapY = y;
                
                mHandler.removeCallbacks(mTimeoutRunnable);
                mHandler.postDelayed(mTimeoutRunnable, timeout);
                
                if (resetSensorAction != null) {
                    resetSensorAction.run();
                }
                return true; // Съедаем
            }
        }
    }
}