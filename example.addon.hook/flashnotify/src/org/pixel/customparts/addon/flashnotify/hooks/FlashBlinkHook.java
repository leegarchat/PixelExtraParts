package org.pixel.customparts.addon.flashnotify.hooks;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.telephony.TelephonyCallback;
import android.telephony.TelephonyManager;
import android.util.Log;

import org.pixel.customparts.addon.flashnotify.Settings;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;

/**
 * Blinks the torch on posted notifications with user count/total time.
 *
 * Why here and not on FlashNotificationsController: that controller lives
 * in system_server ("android"), which the injector never hooks. SystemUI
 * sees every posted notification and already holds the CAMERA permission,
 * so the blink is driven from this process via CameraManager instead.
 * Turn the system Camera-flash toggle off to avoid double blinking.
 */
public final class FlashBlinkHook {
    private static final String TAG = "PixelPartsFlashNotify";
    private static final String LISTENER = "com.android.systemui.statusbar.NotificationListener";

    /** Shortest usable torch phase; total may grow past the setting then. */
    private static final int MIN_PHASE_MS = 50;

    private static final Object LOCK = new Object();
    private static Thread sBlinkThread;
    private static String sCameraId;
    private static boolean sCameraResolved;
    private static int sMaxStrength = 1;
    private static volatile boolean sCallBlink;
    private static String sVoipKey;
    private static volatile boolean sAudioBlink;
    private static volatile boolean sRadioRinging;
    private static java.lang.reflect.Method sIsRingingMethod;
    private static boolean sTestReceiverRegistered;

    /** Fired by the "Test blink" addon button (am broadcast). */
    public static final String ACTION_TEST =
            "org.pixel.customparts.addon.flashnotify.TEST";

    private FlashBlinkHook() {}

    /** Injector may hand a null context; fall back to the running app. */
    private static Context resolveContext(Context context) {
        if (context != null) {
            Context app = context.getApplicationContext();
            return app != null ? app : context;
        }
        try {
            Class<?> at = Class.forName("android.app.ActivityThread");
            Object app = at.getMethod("currentApplication").invoke(null);
            if (app instanceof Context) {
                Log.i(TAG, "resolveContext: used ActivityThread.currentApplication");
                return (Context) app;
            }
        } catch (Throwable t) {
            Log.w(TAG, "resolveContext fallback failed: " + t);
        }
        Log.w(TAG, "resolveContext: context is null");
        return null;
    }

    public static void install(final Context context, final ClassLoader cl) {
        Log.i(TAG, "install: context=" + context + " loader=" + cl);
        final Context app = resolveContext(context);
        Log.i(TAG, "install: appContext=" + app);
        try {
            final Class<?> listener = XposedHelpers.findClass(LISTENER, cl);
            XposedHelpers.findAndHookMethod(listener, "onNotificationPosted",
                    StatusBarNotification.class,
                    NotificationListenerService.RankingMap.class,
                    new XC_MethodHook() {
                        @Override protected void afterHookedMethod(MethodHookParam param) {
                            if (param.args == null || param.args.length < 1
                                    || !(param.args[0] instanceof StatusBarNotification)) return;
                            onPosted(app, cl, (StatusBarNotification) param.args[0],
                                    param.args.length > 1 ? param.args[1] : null);
                        }
                    });
            Log.i(TAG, "hooked " + LISTENER + "#onNotificationPosted");
            XposedHelpers.findAndHookMethod(listener, "onNotificationRemoved",
                    StatusBarNotification.class,
                    NotificationListenerService.RankingMap.class,
                    int.class, new XC_MethodHook() {
                        @Override protected void afterHookedMethod(MethodHookParam param) {
                            if (param.args != null && param.args.length > 0) {
                                onRemoved(param.args[0]);
                            }
                        }
                    });
            Log.i(TAG, "hooked " + LISTENER + "#onNotificationRemoved");
        } catch (Throwable t) {
            Log.e(TAG, "flash blink hook failed", t);
        }
        registerTestReceiver(app);
        registerCallListener(app);
        startAudioWatcher(app);
    }

    /**
     * Silent self-managed VoIP detector (OneMe & co: no notification, no
     * radio change). Telecom puts audio mode to RINGTONE for any ringing
     * call, so listen for mode changes (public API, no permission) and
     * confirm via TelecomManager.isRinging() (@SystemApi, reachable from
     * SystemUI which holds MODIFY_PHONE_STATE; precedent: NavigationBar).
     * Skipped while the SIM path owns ringing.
     */
    private static void startAudioWatcher(final Context context) {
        if (context == null) return;
        try {
            final android.media.AudioManager am = (android.media.AudioManager)
                    context.getSystemService(Context.AUDIO_SERVICE);
            if (am == null) {
                Log.w(TAG, "no AudioManager, self-managed calls unavailable");
                return;
            }
            am.addOnModeChangedListener(context.getMainExecutor(),
                    new android.media.AudioManager.OnModeChangedListener() {
                        @Override public void onModeChanged(int mode) {
                            Log.i(TAG, "audio mode=" + mode);
                            if (mode == android.media.AudioManager.MODE_RINGTONE) {
                                onAudioRinging(context);
                            } else {
                                onAudioIdle();
                            }
                        }
                    });
            Log.i(TAG, "audio watcher started");
        } catch (Throwable t) {
            Log.e(TAG, "audio watcher failed", t);
        }
    }

    /** True if Telecom reports any (managed or self-managed) ringing call. */
    private static boolean telecomRinging(Context context) {
        try {
            android.telecom.TelecomManager tm = (android.telecom.TelecomManager)
                    context.getSystemService(Context.TELECOM_SERVICE);
            if (tm == null) return false;
            java.lang.reflect.Method m = sIsRingingMethod;
            if (m == null) {
                m = tm.getClass().getMethod("isRinging");
                sIsRingingMethod = m;
            }
            Object r = m.invoke(tm);
            return Boolean.TRUE.equals(r);
        } catch (Throwable t) {
            Log.w(TAG, "isRinging failed: " + t);
            return false;
        }
    }

    private static void onAudioRinging(Context context) {
        if (sRadioRinging || sCallBlink) {
            Log.i(TAG, "audio ring: SIM path owns it, skip");
            return;
        }
        if (!telecomRinging(context)) {
            Log.i(TAG, "audio ring: telecom idle, skip");
            return;
        }
        if (!Settings.getVoipEnabled(context)) {
            Log.i(TAG, "audio ring: voip toggle off, skip");
            return;
        }
        int count = clamp(Settings.getVoipCount(context), 1, 50);
        int blink = clamp(Settings.getVoipBlinkMs(context), 50, 2000);
        int pause = clamp(Settings.getVoipPauseMs(context), 0, 10000);
        int strength = clamp(Settings.getStrength(context), 1, 45);
        Log.i(TAG, "audio ring: self-managed go blink=" + blink + " count=" + count
                + " pause=" + pause + " strength=" + strength);
        sAudioBlink = true;
        startBurst(context, blink, count, pause, strength);
    }

    private static void onAudioIdle() {
        if (!sAudioBlink) return;
        sAudioBlink = false;
        synchronized (LOCK) {
            Log.i(TAG, "audio idle: halt blink");
            if (sBlinkThread != null) sBlinkThread.interrupt();
        }
    }

    /** Repeats the pattern while the phone rings; stops on answer/hangup. */
    private static final class CallListener extends TelephonyCallback
            implements TelephonyCallback.CallStateListener {
        private final Context mContext;

        CallListener(Context context) {
            mContext = context.getApplicationContext() != null
                    ? context.getApplicationContext() : context;
        }

        @Override public void onCallStateChanged(int state) {
            Log.i(TAG, "call state=" + state);
            if (state == TelephonyManager.CALL_STATE_RINGING) {
                sRadioRinging = true;
                if (!Settings.getCallsEnabled(mContext)) {
                    Log.i(TAG, "call blink disabled, skip");
                    return;
                }
                int count = clamp(Settings.getSimCount(mContext), 1, 50);
                int blink = clamp(Settings.getSimBlinkMs(mContext), 50, 2000);
                int pause = clamp(Settings.getSimPauseMs(mContext), 0, 10000);
                int strength = clamp(Settings.getStrength(mContext), 1, 45);
                Log.i(TAG, "call: go blink=" + blink + " count=" + count
                        + " pause=" + pause + " strength=" + strength);
                sCallBlink = true;
                sAudioBlink = false;
                synchronized (LOCK) {
                    sVoipKey = null;
                }
                startBurst(mContext, blink, count, pause, strength);
            } else {
                sRadioRinging = false;
                if (sCallBlink) {
                    Log.i(TAG, "call ended, stop blink");
                    sCallBlink = false;
                    synchronized (LOCK) {
                        if (sBlinkThread != null) sBlinkThread.interrupt();
                    }
                }
            }
        }
    }

    private static void registerCallListener(Context context) {
        if (context == null) return;
        try {
            TelephonyManager tm =
                    (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            if (tm == null) {
                Log.w(TAG, "no TelephonyManager, call blink unavailable");
                return;
            }
            tm.registerTelephonyCallback(context.getMainExecutor(),
                    new CallListener(context));
            Log.i(TAG, "call listener registered");
        } catch (Throwable t) {
            Log.e(TAG, "call listener failed", t);
        }
    }

    private static void registerTestReceiver(final Context context) {
        Log.i(TAG, "registerTestReceiver: context=" + context);
        if (context == null) {
            Log.w(TAG, "registerTestReceiver: null context, skip");
            return;
        }
        synchronized (LOCK) {
            if (sTestReceiverRegistered) return;
            sTestReceiverRegistered = true;
        }
        try {
            // NB: 2-arg registerReceiver throws SecurityException on targetSdk 34+
            // (RECEIVER_EXPORTED/NOT_EXPORTED required). Exported: the test
            // blink is harmless and must be reachable from shell/am.
            IntentFilter filter = new IntentFilter(ACTION_TEST);
            context.registerReceiver(new BroadcastReceiver() {
                @Override public void onReceive(Context c, Intent intent) {
                    if (!ACTION_TEST.equals(intent.getAction())) return;
                    Context app = c != null ? c : context;
                    Log.i(TAG, "test blink requested");
                    int count = clamp(Settings.getCount(app), 1, 50);
                    int total = clamp(Settings.getTotalMs(app), 100, 10000);
                    int strength = clamp(Settings.getStrength(app), 1, 45);
                    startBlink(app, count, total, strength, false);
                }
            }, filter, Context.RECEIVER_EXPORTED);
            Log.i(TAG, "test receiver registered");
        } catch (Throwable t) {
            Log.e(TAG, "test receiver failed", t);
        }
    }

    private static void onPosted(Context context, ClassLoader cl,
            StatusBarNotification sbn, Object rankingMap) {
        String key;
        try {
            key = sbn.getKey();
        } catch (Throwable t) {
            key = "?";
        }
        Log.i(TAG, "posted: key=" + key);
        if (context == null) {
            Log.w(TAG, "posted: null context, skip");
            return;
        }
        boolean enabled = Settings.getEnabled(context);
        Log.i(TAG, "posted: enabled=" + enabled);
        if (!enabled) return;
        Notification n;
        try {
            n = sbn.getNotification();
        } catch (Throwable t) {
            Log.w(TAG, "posted: getNotification failed: " + t);
            return;
        }
        if (n == null) {
            Log.w(TAG, "posted: null notification, skip");
            return;
        }
        // Ongoing (player, download, call) never flashes — except VoIP
        // calls, tracked separately by their call-style notification.
        if ((n.flags & Notification.FLAG_ONGOING_EVENT) != 0) {
            onOngoing(context, sbn, n);
            return;
        }
        // Silent (below DEFAULT importance) never flashes either. Fail-open:
        // if ranking is unreadable, blink anyway.
        if (rankingMap instanceof NotificationListenerService.RankingMap
                && isSilent(sbn, (NotificationListenerService.RankingMap) rankingMap)) {
            Log.i(TAG, "posted: silent, skip");
            return;
        }

        int count = clamp(Settings.getCount(context), 1, 50);
        int total = clamp(Settings.getTotalMs(context), 100, 10000);
        int strength = clamp(Settings.getStrength(context), 1, 45);
        Log.i(TAG, "posted: go count=" + count + " total=" + total
                + " strength=" + strength);
        startBlink(context, count, total, strength, false);
    }

    /** Messenger (VoIP) calls: repeat while the call-style notification lives. */
    private static void onOngoing(Context context, StatusBarNotification sbn, Notification n) {
        if (!Notification.CATEGORY_CALL.equals(n.category)) {
            Log.i(TAG, "posted: ongoing, skip");
            return;
        }
        if (!Settings.getVoipEnabled(context)) {
            Log.i(TAG, "posted: voip call, toggle off, skip");
            return;
        }
        String key;
        try {
            key = sbn.getKey();
        } catch (Throwable t) {
            return;
        }
        int count = clamp(Settings.getVoipCount(context), 1, 50);
        int blink = clamp(Settings.getVoipBlinkMs(context), 50, 2000);
        int pause = clamp(Settings.getVoipPauseMs(context), 0, 10000);
        int strength = clamp(Settings.getStrength(context), 1, 45);
        Log.i(TAG, "posted: voip call go blink=" + blink + " count=" + count
                + " pause=" + pause + " strength=" + strength + " key=" + key);
        synchronized (LOCK) {
            sVoipKey = key;
        }
        startBurst(context, blink, count, pause, strength);
    }

    private static void onRemoved(Object sbnObj) {
        if (!(sbnObj instanceof StatusBarNotification)) return;
        String key;
        try {
            key = ((StatusBarNotification) sbnObj).getKey();
        } catch (Throwable t) {
            return;
        }
        synchronized (LOCK) {
            if (key == null || !key.equals(sVoipKey)) return;
            sVoipKey = null;
            Log.i(TAG, "voip call gone, stop blink");
            if (sBlinkThread != null) sBlinkThread.interrupt();
        }
    }

    private static boolean isSilent(StatusBarNotification sbn,
            NotificationListenerService.RankingMap map) {
        try {
            NotificationListenerService.Ranking ranking =
                    new NotificationListenerService.Ranking();
            if (!map.getRanking(sbn.getKey(), ranking)) {
                Log.i(TAG, "ranking: no entry, fail-open");
                return false;
            }
            int importance = ranking.getImportance();
            Log.i(TAG, "ranking: importance=" + importance);
            return importance < NotificationManager.IMPORTANCE_DEFAULT;
        } catch (Throwable t) {
            Log.w(TAG, "ranking read failed, fail-open: " + t);
            return false;
        }
    }

    private static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : Math.min(v, hi);
    }

    /** Call/VoIP burst loop: N blinks, pause, repeat until interrupted. */
    private static void runBurst(Context context, int blinkMs, int count,
            int pauseMs, int strength) {
        int phase = clamp(blinkMs, 50, 2000);
        int n = clamp(count, 1, 50);
        int pause = clamp(pauseMs, 0, 10000);
        int level = clamp(strength, 1, 45);
        Log.i(TAG, "burst: blink=" + phase + " count=" + n + " pause=" + pause
                + " strength=" + level);
        String cameraId = resolveCameraId(context);
        if (cameraId == null) {
            Log.w(TAG, "no flash camera, skip burst");
            return;
        }
        CameraManager cm;
        try {
            cm = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        } catch (Throwable t) {
            Log.w(TAG, "no CameraManager: " + t);
            return;
        }
        if (cm == null) return;
        try {
            int cycle = 0;
            while (true) {
                cycle++;
                for (int i = 0; i < n; i++) {
                    torchOn(cm, cameraId, level);
                    Thread.sleep(phase);
                    setTorchQuiet(cm, cameraId, false);
                    if (i + 1 < n) Thread.sleep(phase);
                }
                Log.i(TAG, "burst cycle " + cycle + " done, pause=" + pause);
                if (pause > 0) Thread.sleep(pause);
            }
        } catch (InterruptedException e) {
            setTorchQuiet(cm, cameraId, false);
            Log.i(TAG, "burst stopped");
            Thread.currentThread().interrupt();
        } catch (Throwable t) {
            Log.w(TAG, "burst aborted: " + t);
            setTorchQuiet(cm, cameraId, false);
        } finally {
            synchronized (LOCK) {
                if (Thread.currentThread() == sBlinkThread) sBlinkThread = null;
            }
        }
    }

    private static void startBurst(final Context context, final int blinkMs,
            final int count, final int pauseMs, final int strength) {
        synchronized (LOCK) {
            if (sBlinkThread != null) sBlinkThread.interrupt();
            sBlinkThread = new Thread(new Runnable() {
                @Override public void run() {
                    runBurst(context, blinkMs, count, pauseMs, strength);
                }
            }, TAG);
            sBlinkThread.setDaemon(true);
            sBlinkThread.start();
        }
    }

    private static void startBlink(final Context context, final int count,
            final int totalMs, final int strength, final boolean repeat) {
        int unit = totalMs / (2 * count);
        if (unit < MIN_PHASE_MS) unit = MIN_PHASE_MS;
        final int phase = unit;
        final int level = strength;
        final int budget = totalMs;
        final boolean loop = repeat;
        Log.i(TAG, "blink: count=" + count + " total=" + totalMs
                + " strength=" + level + " phase=" + phase + " repeat=" + loop);
        synchronized (LOCK) {
            // A one-shot blink supersedes any tracked VoIP/audio loop.
            if (!loop) {
                sVoipKey = null;
                sAudioBlink = false;
            }
            if (sBlinkThread != null) sBlinkThread.interrupt();
            sBlinkThread = new Thread(new Runnable() {
                @Override public void run() {
                    runBlink(context, count, phase, level, budget, loop);
                }
            }, TAG);
            sBlinkThread.setDaemon(true);
            sBlinkThread.start();
        }
    }

    /**
     * Torch is driven via CameraManager directly: the SystemUI on this ROM
     * (SystemUIGoogle) no longer ships the old FlashlightController
     * interface, and SystemUI already holds the CAMERA permission.
     */
    private static void runBlink(Context context, int count, int phaseMs,
            int strength, int totalMs, boolean repeat) {
        String cameraId = resolveCameraId(context);
        if (cameraId == null) {
            Log.w(TAG, "no flash camera, skip blink");
            return;
        }
        CameraManager cm;
        try {
            cm = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        } catch (Throwable t) {
            Log.w(TAG, "no CameraManager: " + t);
            return;
        }
        if (cm == null) return;
        long onCost = 0;
        long offCost = 0;
        int did = 0;
        final long start = android.os.SystemClock.elapsedRealtime();
        try {
            do {
                for (int i = 0; i < count; i++) {
                    // Time budget wins over count (one-shot mode): never start
                    // a blink that would overrun the total. Repeat mode
                    // (calls) ignores the budget and runs until interrupted.
                    // Torch always ends OFF.
                    if (!repeat && android.os.SystemClock.elapsedRealtime()
                            - start + phaseMs > totalMs) {
                        Log.i(TAG, "budget hit at " + (i + 1) + "/" + count);
                        break;
                    }
                Log.i(TAG, "phase " + (i + 1) + "/" + count + " ON");
                long t0 = android.os.SystemClock.elapsedRealtime();
                torchOn(cm, cameraId, strength);
                onCost += android.os.SystemClock.elapsedRealtime() - t0;
                Thread.sleep(phaseMs);
                Log.i(TAG, "phase " + (i + 1) + "/" + count + " OFF");
                t0 = android.os.SystemClock.elapsedRealtime();
                setTorchQuiet(cm, cameraId, false);
                offCost += android.os.SystemClock.elapsedRealtime() - t0;
                if (i + 1 < count) Thread.sleep(phaseMs);
                did++;
            }
            } while (repeat);
            Log.i(TAG, "blink done: did=" + did + "/" + count
                    + " torchOnAvg=" + (did == 0 ? 0 : onCost / did)
                    + "ms torchOffAvg=" + (did == 0 ? 0 : offCost / did) + "ms");
        } catch (InterruptedException e) {
            setTorchQuiet(cm, cameraId, false);
            Thread.currentThread().interrupt();
        } catch (Throwable t) {
            Log.w(TAG, "blink aborted: " + t);
            setTorchQuiet(cm, cameraId, false);
        } finally {
            synchronized (LOCK) {
                if (Thread.currentThread() == sBlinkThread) sBlinkThread = null;
            }
        }
    }

    private static synchronized String resolveCameraId(Context context) {
        if (sCameraResolved) return sCameraId;
        sCameraResolved = true;
        try {
            CameraManager cm =
                    (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
            if (cm == null) return null;
            for (String id : cm.getCameraIdList()) {
                CameraCharacteristics c;
                try {
                    c = cm.getCameraCharacteristics(id);
                } catch (Throwable ignored) {
                    continue;
                }
                Integer facing = c.get(CameraCharacteristics.LENS_FACING);
                Boolean flash = c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK
                        && Boolean.TRUE.equals(flash)) {
                    sCameraId = id;
                    try {
                        Integer max = c.get(
                                CameraCharacteristics.FLASH_INFO_STRENGTH_MAXIMUM_LEVEL);
                        if (max != null && max > 1) sMaxStrength = max;
                    } catch (Throwable ignored) {
                    }
                    Log.i(TAG, "flash camera: " + id + " maxStrength=" + sMaxStrength);
                    break;
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "camera resolve failed: " + t);
        }
        return sCameraId;
    }

    /** Torch ON at the given strength; falls back to plain torch if unsupported. */
    private static void torchOn(CameraManager cm, String id, int strength) throws Exception {
        int max;
        synchronized (FlashBlinkHook.class) {
            max = sMaxStrength;
        }
        int level = Math.max(1, Math.min(strength, Math.max(1, max)));
        if (max > 1) {
            cm.turnOnTorchWithStrengthLevel(id, level);
        } else {
            cm.setTorchMode(id, true);
        }
    }

    private static void setTorch(CameraManager cm, String id, boolean on) throws Exception {
        cm.setTorchMode(id, on);
    }

    private static void setTorchQuiet(CameraManager cm, String id, boolean on) {
        try {
            cm.setTorchMode(id, on);
        } catch (Throwable ignored) {
        }
    }
}
