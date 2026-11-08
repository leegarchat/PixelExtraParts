/*
 * Copyright (C) 2026 The Evolution X Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.server;

import android.app.Notification;
import android.content.Context;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.util.Slog;

import java.util.HashSet;

/**
 * PixelExtraParts unified call-state publisher (H1-weave DELEGATE target).
 *
 * Called from TelephonyRegistry (SIM funnel) and NotificationManagerService
 * (messenger funnel) via one-line hooks; all logic lives here so the
 * framework diff stays minimal. Publishes a single canonical Settings.Global
 * key (no suffix) consumed by the FlashNotify addon as its primary source:
 *
 *   flashnotify_call_state: 0 = idle, 1 = ringing, 2 = active.
 *
 * Plain framework code only: no LSPlant/dobby dependency. system_server
 * already holds WRITE_SECURE_SETTINGS, and Settings.Global reads from
 * SystemUI need no extra permission, so no sepolicy change is required
 * (TODO: re-verify on device with enforcing SELinux).
 */
public final class PixelPartsCallState {
    private PixelPartsCallState() {}

    /** Canonical Settings.Global key, no suffix. */
    public static final String KEY_CALL_STATE = "flashnotify_call_state";

    public static final int CALL_IDLE = 0;
    public static final int CALL_RINGING = 1;
    public static final int CALL_ACTIVE = 2;

    private static final String TAG = "PixelPartsCallState";

    private static final Object sLock = new Object();
    private static int sSimState = CALL_IDLE;
    private static final HashSet<String> sVoipKeys = new HashSet<>();

    /** Called from TelephonyRegistry.broadcastCallStateChanged (SIM funnel). */
    public static void onTelephonyCallState(Context context, int state) {
        final int mapped;
        if (state == TelephonyManager.CALL_STATE_RINGING) {
            mapped = CALL_RINGING;
        } else if (state == TelephonyManager.CALL_STATE_OFFHOOK) {
            mapped = CALL_ACTIVE;
        } else {
            mapped = CALL_IDLE;
        }
        synchronized (sLock) {
            if (sSimState == mapped) {
                return;
            }
            sSimState = mapped;
            publishLocked(context);
        }
    }

    /** Called from NotificationManagerService.addNotification (messenger funnel). */
    public static void onNotificationPosted(Context context, String key, Notification n) {
        if (key == null || n == null) {
            return;
        }
        if (!isCallNotification(n)) {
            return;
        }
        synchronized (sLock) {
            if (!sVoipKeys.add(key)) {
                return;
            }
            publishLocked(context);
        }
    }

    /** Called from NotificationManagerService.cancelNotificationLocked. */
    public static void onNotificationRemoved(Context context, String key) {
        if (key == null) {
            return;
        }
        synchronized (sLock) {
            if (!sVoipKeys.remove(key)) {
                return;
            }
            publishLocked(context);
        }
    }

    /**
     * Messenger calls that bypass Telecom (WhatsApp/Telegram in general do not
     * register a ConnectionService/PhoneAccount) surface as high-priority
     * notifications with a fullScreenIntent and/or CATEGORY_CALL/CallStyle.
     */
    private static boolean isCallNotification(Notification n) {
        if (Notification.CATEGORY_CALL.equals(n.category)) {
            return true;
        }
        if (n.fullScreenIntent != null) {
            return true;
        }
        try {
            if (n.isStyle(Notification.CallStyle.class)) {
                return true;
            }
        } catch (Throwable ignored) {
            // Conservatively ignore style inspection failures.
        }
        return false;
    }

    private static void publishLocked(Context context) {
        int state = Math.max(sSimState, sVoipKeys.isEmpty() ? CALL_IDLE : CALL_RINGING);
        if (context == null) {
            return;
        }
        try {
            Settings.Global.putInt(context.getContentResolver(), KEY_CALL_STATE, state);
        } catch (Throwable t) {
            Slog.w(TAG, "publish failed: " + t);
        }
    }
}
