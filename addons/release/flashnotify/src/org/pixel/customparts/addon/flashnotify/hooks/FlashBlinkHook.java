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
    private static boolean sRomObserverRegistered;

    /** ROM-published unified call state mirror: 0 idle, 1 ringing, 2 active. */
    private static volatile int sRomCall;
    private static final int ROM_IDLE = 0;
    private static final int ROM_RINGING = 1;
    private static final int ROM_ACTIVE = 2;

    /** Fired by the "Test blink" addon button (am broadcast). */
    public static final String ACTION_TEST =
            "org.pixel.customparts.addon.flashnotify.TEST";
    /** Test-only: start a SIM-pattern burst for N seconds, then stop. */
    public static final String ACTION_TEST_CALL =
            "org.pixel.customparts.addon.flashnotify.TEST_CALL";
    public static final String ACTION_TEST_VOIP =
            "org.pixel.customparts.addon.flashnotify.TEST_VOIP";
    public static final String EXTRA_TEST_SECONDS = "seconds";
    private static int sTestGen;
    private static volatile boolean sTestBurst;
    private static volatile long sTestUntil;

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
        registerRomObserver(app);
    }

    /**
     * Primary source: unified call state published by system_server
     * (PixelPartsCallState via TelephonyRegistry + NotificationManagerService).
     * Covers SIM ringing/active authoritatively and messenger calls that post
     * call-style/fullScreenIntent notifications. The TelephonyCallback, audio
     * mode watcher and notification trackers below stay as fallback for ROMs
     * without the publisher and for self-managed calls with no notification.
     */
    private static void registerRomObserver(final Context context) {
        if (context == null) return;
        synchronized (LOCK) {
            if (sRomObserverRegistered) return;
            sRomObserverRegistered = true;
        }
        try {
            android.net.Uri uri = android.provider.Settings.Global.getUriFor(
                    Settings.KEY_ROM_STATE);
            context.getContentResolver().registerContentObserver(uri, false,
                    new android.database.ContentObserver(
                            new android.os.Handler(context.getMainLooper())) {
                        @Override public void onChange(boolean selfChange) {
                            onRomStateChanged(context,
                                    Settings.getRomState(context));
                        }
                    });
            Log.i(TAG, "rom observer registered");
            onRomStateChanged(context, Settings.getRomState(context));
        } catch (Throwable t) {
            Log.e(TAG, "rom observer failed", t);
        }
    }

    private static void onRomStateChanged(Context context, int state) {
        Log.i(TAG, "rom state=" + state);
        synchronized (LOCK) {
            // Any state move cancels a pending SIM hold below.
            sRomGen++;
        }
        boolean simOn = Settings.getCallsEnabled(context);
        boolean voipOn = Settings.getVoipEnabled(context);
        if (!simOn && !voipOn) {
            sRomCall = ROM_IDLE;
            return;
        }
        if (state == ROM_IDLE) {
            sRomCall = ROM_IDLE;
            stopIfAllIdle();
            return;
        }
        if (state == ROM_ACTIVE) {
            // Answered: blinking always stops (same as the TelephonyCallback
            // path). VoIP calls keep blinking via their ongoing notification.
            sRomCall = ROM_IDLE;
            stopIfAllIdle();
            return;
        }
        // state == ROM_RINGING from here on.
        if (!simOn) {
            // SIM blinks off: a ringing state alone is not VoIP evidence.
            // VoIP blinking requires its own trigger (call notification or
            // self-managed audio detection), so do not fall through to it.
            Log.i(TAG, "rom ring: SIM off, no blink (waiting for VoIP evidence)");
            sRomCall = ROM_IDLE;
            return;
        }
        // A live VoIP call notification proves a messenger call: the SIM
        // branch must not claim it (prevents SIM blink on Max/Telegram
        // while SIM is on).
        synchronized (LOCK) {
            if (sVoipKey != null) {
                Log.i(TAG, "rom ring: voip notification live, SIM stays out");
                sRomCall = ROM_IDLE;
                return;
            }
        }
        sRomCall = state;
        // Hold the SIM burst briefly: a messenger call notification that is
        // still in flight would otherwise lose the race (ROM observer fires
        // first) and flash the SIM pattern once before VoIP takes over.
        // If a VoIP key lands during the hold, SIM yields without a blink.
        final int gen;
        synchronized (LOCK) {
            gen = sRomGen;
        }
        final Context appContext = context;
        Log.i(TAG, "rom ring: hold SIM 350ms for voip key (gen=" + gen + ")");
        try {
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(
                    new Runnable() {
                        @Override public void run() {
                            startRomSimIfClear(appContext, gen);
                        }
                    }, ROM_SIM_HOLD_MS);
        } catch (Throwable t) {
            Log.w(TAG, "rom ring: no looper, SIM goes immediate: " + t);
            startRomSimBurst(context);
        }
    }

    /** Fires the held SIM burst only if no VoIP key arrived meanwhile. */
    private static void startRomSimIfClear(Context context, int gen) {
        synchronized (LOCK) {
            if (gen != sRomGen) {
                Log.i(TAG, "rom ring: hold gen stale, drop");
                return;
            }
            if (sVoipKey != null) {
                Log.i(TAG, "rom ring: voip key arrived during hold, SIM yields");
                sRomCall = ROM_IDLE;
                return;
            }
        }
        if (sRomCall != ROM_RINGING) {
            Log.i(TAG, "rom ring: state moved during hold, drop");
            return;
        }
        if (!Settings.getCallsEnabled(context)) {
            Log.i(TAG, "rom ring: SIM went off during hold, drop");
            sRomCall = ROM_IDLE;
            return;
        }
        startRomSimBurst(context);
    }

    /** Immediate SIM burst (held path above, or no-Looper fallback). */
    private static void startRomSimBurst(Context context) {
        int strength = clamp(Settings.getSimBrightness(context), 1, 45);
        int count = clamp(Settings.getSimCount(context), 1, 50);
        int blink = clamp(Settings.getSimBlinkMs(context), 50, 2000);
        int pause = clamp(Settings.getSimPauseMs(context), 0, 10000);
        Log.i(TAG, "rom call: go blink=" + blink + " count=" + count
                + " pause=" + pause + " strength=" + strength);
        sAudioBlink = false;
        cancelTestBurst("rom-sim");
        startBurst(context, blink, count, pause, strength);
    }

    /** Halts the blink thread only when no source claims a live call. */
    private static void stopIfAllIdle() {
        if (sCallBlink || sAudioBlink || sRomCall != ROM_IDLE) {
            return;
        }
        synchronized (LOCK) {
            if (sVoipKey == null && sBlinkThread != null) {
                Log.i(TAG, "all idle: halt blink");
                sBlinkThread.interrupt();
            }
        }
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
        if (sRadioRinging || sCallBlink || sRomCall != ROM_IDLE) {
            Log.i(TAG, "audio ring: SIM/ROM path owns it, skip");
            return;
        }
        if (!telecomRinging(context)) {
            Log.i(TAG, "audio ring: telecom idle, skip");
            return;
        }
        // Distinguish managed SIM ringing from self-managed VoIP: a ringing
        // radio means Telecom's ringing call is the SIM one, so the audio
        // path must stay out (SIM path owns it, or SIM is off entirely).
        try {
            android.telephony.TelephonyManager tm = (android.telephony.TelephonyManager)
                    context.getSystemService(Context.TELEPHONY_SERVICE);
            if (tm != null && tm.getCallState() != android.telephony.TelephonyManager.CALL_STATE_IDLE) {
                Log.i(TAG, "audio ring: radio busy (managed call), skip");
                return;
            }
        } catch (Throwable t) {
            Log.w(TAG, "audio ring: radio check failed, assume VoIP: " + t);
        }
        if (!Settings.getVoipEnabled(context)) {
            Log.i(TAG, "audio ring: voip toggle off, skip");
            return;
        }
        int count = clamp(Settings.getVoipCount(context), 1, 50);
        int blink = clamp(Settings.getVoipBlinkMs(context), 50, 2000);
        int pause = clamp(Settings.getVoipPauseMs(context), 0, 10000);
        int strength = clamp(Settings.getVoipBrightness(context), 1, 45);
        Log.i(TAG, "audio ring: self-managed go blink=" + blink + " count=" + count
                + " pause=" + pause + " strength=" + strength);
        sAudioBlink = true;
        cancelTestBurst("audio");
        startBurst(context, blink, count, pause, strength);
    }

    private static void onAudioIdle() {
        if (!sAudioBlink) return;
        sAudioBlink = false;
        stopIfAllIdle();
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
                int strength = clamp(Settings.getSimBrightness(mContext), 1, 45);
                Log.i(TAG, "call: go blink=" + blink + " count=" + count
                        + " pause=" + pause + " strength=" + strength);
                sCallBlink = true;
                sAudioBlink = false;
                synchronized (LOCK) {
                    sVoipKey = null;
                }
                cancelTestBurst("sim");
                startBurst(mContext, blink, count, pause, strength);
            } else {
                sRadioRinging = false;
                if (sCallBlink) {
                    Log.i(TAG, "call ended, stop blink");
                    sCallBlink = false;
                    stopIfAllIdle();
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
            Log.i(TAG, "call listener registered (TelephonyCallback)");
        } catch (Throwable t) {
            Log.e(TAG, "call listener failed (TelephonyCallback)", t);
        }
        // Legacy fallback: if the modern callback never delivers (seen on
        // this ROM: zero onCallStateChanged), the deprecated listener still
        // fires on the same radio events. Distinct tag prefix for diagnosis.
        try {
            TelephonyManager tm =
                    (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            if (tm == null) return;
            tm.listen(new android.telephony.PhoneStateListener() {
                @Override public void onCallStateChanged(int state, String phoneNumber) {
                    Log.i(TAG, "call[legacy] state=" + state);
                    if (state == TelephonyManager.CALL_STATE_RINGING) {
                        sRadioRinging = true;
                        // Mirror of CallListener: drives the SIM burst only if
                        // the modern callback did not already claim it.
                        if (!sCallBlink && Settings.getCallsEnabled(context)) {
                            int count = clamp(Settings.getSimCount(context), 1, 50);
                            int blink = clamp(Settings.getSimBlinkMs(context), 50, 2000);
                            int pause = clamp(Settings.getSimPauseMs(context), 0, 10000);
                            int strength = clamp(Settings.getSimBrightness(context), 1, 45);
                            Log.i(TAG, "call[legacy]: go blink=" + blink + " count=" + count
                                    + " pause=" + pause + " strength=" + strength);
                            sCallBlink = true;
                            sAudioBlink = false;
                            synchronized (LOCK) {
                                sVoipKey = null;
                            }
                            cancelTestBurst("sim-legacy");
                            startBurst(context, blink, count, pause, strength);
                        } else if (!Settings.getCallsEnabled(context)) {
                            Log.i(TAG, "call[legacy] blink disabled, skip");
                        }
                    } else {
                        sRadioRinging = false;
                        if (sCallBlink) {
                            Log.i(TAG, "call[legacy] ended, stop blink");
                            sCallBlink = false;
                            stopIfAllIdle();
                        }
                    }
                }
            }, android.telephony.PhoneStateListener.LISTEN_CALL_STATE);
            Log.i(TAG, "call listener registered (legacy PhoneStateListener)");
        } catch (Throwable t) {
            Log.e(TAG, "call listener failed (legacy)", t);
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
            filter.addAction(ACTION_TEST_CALL);
            filter.addAction(ACTION_TEST_VOIP);
            context.registerReceiver(new BroadcastReceiver() {
                @Override public void onReceive(Context c, Intent intent) {
                    String action = intent != null ? intent.getAction() : null;
                    if (ACTION_TEST_VOIP.equals(action)) {
                        onTestCall(c != null ? c : context, intent, true);
                        return;
                    }
                    if (ACTION_TEST_CALL.equals(action)) {
                        onTestCall(c != null ? c : context, intent, false);
                        return;
                    }
                    if (!ACTION_TEST.equals(action)) return;
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

    /**
     * Test-only simulated call: SIM-pattern burst for N seconds, then stop.
     * Trigger: am broadcast -a ...TEST_CALL --ei seconds N.
     * Skipped while a real call owns the blink (SIM/VoIP/ROM/audio).
     * A supervisor thread owns the torch for the whole window: it restarts
     * the burst if a notification blink kills it mid-test, so the test
     * always runs to its deadline instead of dying on the first repost.
     */
    private static void onTestCall(Context context, Intent intent, final boolean voip) {
        int seconds = 0;
        try {
            seconds = intent.getIntExtra(EXTRA_TEST_SECONDS, 0);
        } catch (Throwable ignored) {}
        if (seconds <= 0 || seconds > 30) seconds = 5;
        final Context app;
        {
            Context c = context != null ? context.getApplicationContext() : null;
            app = c != null ? c : context;
        }
        if (app == null) {
            Log.w(TAG, "test call: null context, skip");
            return;
        }
        final int count;
        final int blink;
        final int pause;
        final int strength;
        final String tag;
        if (voip) {
            count = clamp(Settings.getVoipCount(app), 1, 50);
            blink = clamp(Settings.getVoipBlinkMs(app), 50, 2000);
            pause = clamp(Settings.getVoipPauseMs(app), 0, 10000);
            strength = clamp(Settings.getVoipBrightness(app), 1, 45);
            tag = "voip-test";
        } else {
            count = clamp(Settings.getSimCount(app), 1, 50);
            blink = clamp(Settings.getSimBlinkMs(app), 50, 2000);
            pause = clamp(Settings.getSimPauseMs(app), 0, 10000);
            strength = clamp(Settings.getSimBrightness(app), 1, 45);
            tag = "sim-test";
        }
        final long deadline;
        final int gen;
        synchronized (LOCK) {
            if (sCallBlink || sAudioBlink || sRomCall != ROM_IDLE || sVoipKey != null) {
                Log.i(TAG, "test call: real call owns blink, skip");
                return;
            }
            sTestGen++;
            gen = sTestGen;
            deadline = android.os.SystemClock.elapsedRealtime() + seconds * 1000L;
            sTestUntil = deadline;
            sTestBurst = true;
        }
        Log.i(TAG, "test call [" + tag + "]: go seconds=" + seconds
                + " blink=" + blink + " count=" + count
                + " pause=" + pause + " strength=" + strength);
        Thread sup = new Thread(new Runnable() {
            @Override public void run() {
                while (true) {
                    synchronized (LOCK) {
                        if (gen != sTestGen) return;
                        if (android.os.SystemClock.elapsedRealtime() >= sTestUntil) {
                            sTestBurst = false;
                            break;
                        }
                        Thread t = sBlinkThread;
                        if (t == null || !t.isAlive()) {
                            Log.i(TAG, "test call [" + tag + "]: (re)start burst");
                            startBurst(app, blink, count, pause, strength);
                        }
                    }
                    try {
                        Thread.sleep(250);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
                Log.i(TAG, "test call: timeout, stop");
                stopIfAllIdle();
            }
        }, TAG + "-testcall");
        sup.setDaemon(true);
        sup.start();
    }

    /** Real calls preempt test mode: invalidate its generation. */
    private static void cancelTestBurst(String why) {
        synchronized (LOCK) {
            if (!sTestBurst) return;
            sTestBurst = false;
            sTestGen++;
            Log.i(TAG, "test call: preempted by " + why);
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
        // Diagnostic shape dump (first post per key): characterizes even
        // plain notifications (a messenger call may hide among them).
        synchronized (LOCK) {
            if (!key.equals(sLastShapeKey)) {
                sLastShapeKey = key;
                boolean fsi = false;
                try {
                    fsi = n.fullScreenIntent != null;
                } catch (Throwable ignored) {}
                String sound = "?";
                int usage = -1;
                try {
                    sound = String.valueOf(n.sound);
                    if (n.audioAttributes != null) {
                        usage = n.audioAttributes.getUsage();
                    }
                } catch (Throwable ignored) {}
                Log.i(TAG, "posted: shape key=" + key
                        + " cat=" + n.category
                        + " flags=" + n.flags
                        + " sound=" + sound
                        + " usage=" + usage
                        + " fsi=" + fsi);
            }
        }
        // Call-style notifications (messenger calls AND the stock dialer for
        // SIM) route by category, not by the ongoing flag: Max posts an
        // ongoing call notification, Telegram an FSI heads-up with
        // CATEGORY_CALL but no ongoing flag (flags=128 is just clickable).
        if (Notification.CATEGORY_CALL.equals(n.category)) {
            onCallNotification(context, sbn, n);
            return;
        }
        // Other ongoing (player, download) never flashes.
        if ((n.flags & Notification.FLAG_ONGOING_EVENT) != 0) {
            onOngoingSkip(context, sbn, n);
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

    private static String sVoipBurstSig;
    /** Last key already shape-dumped (reposts are frequent). Guarded by LOCK. */
    private static String sLastShapeKey;
    /** SIM hold generation: any ROM state move or new VoIP key bumps it,
     * cancelling a pending SIM hold. Guarded by LOCK. */
    private static int sRomGen;
    private static final int ROM_SIM_HOLD_MS = 350;
    private static volatile boolean sLegacyRinging;

    /** Packages that render the *SIM* call UI — their call notifications are
     * never VoIP, even with CATEGORY_CALL/fullScreenIntent. Resolved against
     * the system default dialer first, hardcoded list as fallback. */
    private static final String[] KNOWN_DIALER_PACKAGES = {
            "com.google.android.dialer",
            "com.android.dialer",
            "com.android.server.telecom",
            "com.android.phone",
            "com.android.incallui",
            "com.samsung.android.dialer",
            "com.samsung.android.incallui",
    };

    private static boolean isDialerPackage(Context context, String pkg) {
        if (pkg == null) return false;
        try {
            android.telecom.TelecomManager tm = (android.telecom.TelecomManager)
                    context.getSystemService(Context.TELECOM_SERVICE);
            if (tm != null) {
                String def = tm.getDefaultDialerPackage();
                if (pkg.equals(def)) return true;
            }
        } catch (Throwable ignored) {}
        for (String known : KNOWN_DIALER_PACKAGES) {
            if (pkg.equals(known)) return true;
        }
        return false;
    }

    /** Other ongoing notifications (player, download): never flash. */
    private static void onOngoingSkip(Context context, StatusBarNotification sbn, Notification n) {
        Log.i(TAG, "posted: ongoing, skip");
    }

    /** Messenger (VoIP) calls: repeat while the call-style notification lives.
     * Routed by CATEGORY_CALL (caller guarantees it); the ongoing flag is
     * irrelevant (Max sets it, Telegram's FSI heads-up does not). */
    private static void onCallNotification(Context context, StatusBarNotification sbn, Notification n) {
        String key;
        try {
            key = sbn.getKey();
        } catch (Throwable t) {
            return;
        }
        String pkg;
        try {
            pkg = sbn.getPackageName();
        } catch (Throwable t) {
            pkg = null;
        }
        // The stock dialer posts a call notification *for SIM calls*.
        // It is never a VoIP call — SIM ringing is owned by the SIM/ROM path.
        if (isDialerPackage(context, pkg)) {
            Log.i(TAG, "posted: voip call from dialer " + pkg + ", skip (SIM UI)");
            return;
        }
        // Record the live VoIP key BEFORE the toggle check: the SIM branch
        // yields to a live key, so the key must exist even with VoIP off.
        // Only the blinking itself respects the toggle.
        synchronized (LOCK) {
            if (!key.equals(sVoipKey)) {
                sVoipKey = key;
                sVoipBurstSig = null;
                sRomGen++; // cancel any pending SIM hold: messenger wins
                boolean fsi = false;
                try {
                    fsi = n.fullScreenIntent != null;
                } catch (Throwable ignored) {}
                boolean callStyle = false;
                try {
                    // isCallStyle() exists only on new platforms; resolve
                    // via reflection so the addon still compiles against
                    // the old android.jar from prebuild/.
                    Object r = n.getClass().getMethod("isCallStyle").invoke(n);
                    callStyle = Boolean.TRUE.equals(r);
                } catch (Throwable ignored) {}
                Log.i(TAG, "posted: voip NEW key=" + key
                        + " category=" + n.category
                        + " fsi=" + fsi
                        + " callStyle=" + callStyle
                        + " flags=" + n.flags);
            }
        }
        if (!Settings.getVoipEnabled(context)) {
            Log.i(TAG, "posted: voip call, toggle off, skip (key held for SIM yield)");
            return;
        }
        int count = clamp(Settings.getVoipCount(context), 1, 50);
        int blink = clamp(Settings.getVoipBlinkMs(context), 50, 2000);
        int pause = clamp(Settings.getVoipPauseMs(context), 0, 10000);
        int strength = clamp(Settings.getVoipBrightness(context), 1, 45);
        String sig = blink + "/" + count + "/" + pause + "/" + strength;
        synchronized (LOCK) {
            // Ongoing call notifications repost often (timer/state updates).
            // Restarting the burst on every repost would trap it in phase 1
            // forever, so keep the running burst when nothing changed.
            Thread t = sBlinkThread;
            if (key.equals(sVoipKey) && sig.equals(sVoipBurstSig)
                    && t != null && t.isAlive()) {
                Log.i(TAG, "posted: voip dup post, keep running key=" + key);
                return;
            }
            sVoipKey = key;
            sVoipBurstSig = sig;
        }
        Log.i(TAG, "posted: voip call go blink=" + blink + " count=" + count
                + " pause=" + pause + " strength=" + strength + " key=" + key);
        cancelTestBurst("voip");
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
            sVoipBurstSig = null;
            Log.i(TAG, "voip call gone");
        }
        stopIfAllIdle();
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
        // Burst-window semantics (same as notifications): blinkMs is the TOTAL
        // burst length, flashes share it evenly (unit = window / (2 * count)).
        // Floored at MIN_PHASE_MS: hardware cannot toggle faster anyway.
        // The window is a hard budget: a blink that would overrun it is
        // skipped, even if the count is not reached.
        int n = clamp(count, 1, 50);
        int budget = clamp(blinkMs, 100, 10000);
        int phase = budget / (2 * Math.max(n, 1));
        if (phase < MIN_PHASE_MS) phase = MIN_PHASE_MS;
        int pause = clamp(pauseMs, 0, 10000);
        int level = clamp(strength, 1, 45);
        Log.i(TAG, "burst: window=" + blinkMs + "ms unit=" + phase + "ms count=" + n
                + " pause=" + pause + " strength=" + level);
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
                // Time budget wins over count (same as notifications):
                // never start a blink that would overrun the window.
                // Reset per cycle since calls repeat until interrupted.
                final long start = android.os.SystemClock.elapsedRealtime();
                for (int i = 0; i < n; i++) {
                    if (android.os.SystemClock.elapsedRealtime() - start + phase > budget) {
                        Log.i(TAG, "budget hit at " + (i + 1) + "/" + n);
                        break;
                    }
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
        synchronized (LOCK) {
            // Test mode owns the torch for its window; notifications wait.
            if (sTestBurst
                    && android.os.SystemClock.elapsedRealtime() < sTestUntil) {
                Log.i(TAG, "test owns torch, skip notification blink");
                return;
            }
        }
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
