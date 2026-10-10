package org.pixel.customparts.manager.lsplant;

import android.util.Log;

import java.lang.reflect.Constructor;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import de.robv.android.xposed.XposedBridge;

/**
 * Java side of the LSPlant hook backend.
 *
 * <p>Replaces {@code top.canyie.pine.Pine}: one LSPlant native hook per
 * {@link Member}, dispatching to the Xposed callback chain held by
 * {@link XposedBridge}. Hook files are untouched — they keep talking
 * {@code de.robv.android.xposed.*}.
 *
 * <p>The matching native glue ({@code liblspbridge.so}) provides
 * {@code nativeDoHook}/{@code nativeUnHook}/{@code nativeDeoptimize} with
 * JNI names derived from this class. The LSPlant/Dobby engine behind the
 * glue is bound at runtime: an already-injected foreign engine
 * (Vector/LSPosed) is reused, otherwise our own {@code /system} prebuilts
 * are loaded — never both in one process.
 */
public final class LsplantBridge {
    private static final String TAG = "LsplantBridge";
    private static final String LIB_NAME = "lsplant";

    private static volatile boolean libraryLoaded;
    private static volatile boolean initOk;

    /** Member -> native backup (Method, or Constructor for <init> targets). */
    private static final Map<Member, Object> backups = new ConcurrentHashMap<>();

    private LsplantBridge() {
    }

    /**
     * Loads the native backend (once per process). Prefers an already-injected
     * foreign LSPlant engine (Vector/LSPosed): in that case our own
     * {@code liblsplant.so}/{@code libdobby.so} prebuilts are NOT loaded at
     * all. Returns true when the native backend is usable. Never throws.
     */
    public static synchronized boolean init() {
        if (initOk) {
            return true;
        }
        // Pine parity: Pine disabled the hidden-API blacklist process-wide.
        // Without this, hooks running with a modern targetSdk (e.g. GCam on
        // API 37) get "using reflection: denied" on platform hidden methods
        // (observed: CameraMetadataNative.set for the torch hook).
        exemptHiddenApi();
        if (!libraryLoaded) {
            boolean foreign = false;
            try {
                foreign = nativeForeignEnginePresent();
            } catch (Throwable t) {
                Log.w(TAG, "Foreign engine probe failed, using own prebuilts: " + t);
            }
            if (foreign) {
                Log.i(TAG, "Reusing foreign LSPlant engine, own prebuilts skipped");
                libraryLoaded = true;
            } else {
                try {
                    System.loadLibrary(LIB_NAME);
                    System.loadLibrary("dobby");
                    libraryLoaded = true;
                } catch (Throwable t) {
                    Log.e(TAG, "Failed to load own LSPlant prebuilts", t);
                    return false;
                }
            }
        }
        try {
            initOk = nativeInit();
        } catch (Throwable t) {
            Log.e(TAG, "LSPlant native init failed", t);
            initOk = false;
        }
        return initOk;
    }

    /**
     * Installs (or reuses) the LSPlant hook for {@code method} and records the
     * native backup for {@link #invokeBackup}. The per-method dispatcher is
     * created on first hook and shared by every {@code XC_MethodHook} that
     * {@link XposedBridge} chains onto the same member.
     *
     * @return true when the method is (now) hooked through LSPlant.
     */
    public static boolean hookMember(Member method,
            de.robv.android.xposed.XposedBridge.LSPlantDispatcher dispatcher) {
        if (!init()) {
            return false;
        }
        try {
            Object backup = nativeDoHook(method, dispatcher, dispatcher.getCallbackMethod());
            if (backup == null) {
                Log.e(TAG, "LSPlant hook failed for " + method);
                return false;
            }
            backups.putIfAbsent(method, backup);
            dispatcher.attachBackup(backups.get(method));
            return true;
        } catch (Throwable t) {
            Log.e(TAG, "LSPlant hook threw for " + method, t);
            return false;
        }
    }

    /**
     * Calls the original implementation of an LSPlant-hooked member, mirroring
     * {@code XposedBridge.invokeOriginalMethod} semantics.
     */
    public static Object invokeBackup(Member method, Object thisObject, Object[] args)
            throws Throwable {
        Object backup = backups.get(method);
        if (backup == null) {
            throw new IllegalStateException("No LSPlant backup for " + method);
        }
        try {
            if (backup instanceof Constructor) {
                return ((Constructor<?>) backup).newInstance(args);
            }
            return ((Method) backup).invoke(thisObject, args);
        } catch (java.lang.reflect.InvocationTargetException e) {
            throw e.getCause() != null ? e.getCause() : e;
        }
    }

    /** Best-effort deopt of a (usually caller) method; never throws. */
    public static void deoptimize(Member method) {
        if (method == null || !init()) {
            return;
        }
        try {
            nativeDeoptimize(method);
        } catch (Throwable t) {
            Log.w(TAG, "LSPlant deoptimize failed for " + method + ": " + t);
        }
    }

    /**
     * Process-wide hidden-API exemption ({@code "L"} = everything), mirroring
     * what Pine did at startup. Reflection-safe across releases.
     */
    private static void exemptHiddenApi() {
        try {
            Class<?> vmRuntime = Class.forName("dalvik.system.VMRuntime");
            Object runtime = vmRuntime.getDeclaredMethod("getRuntime").invoke(null);
            vmRuntime.getDeclaredMethod("setHiddenApiExemptions", String[].class)
                    .invoke(runtime, (Object) new String[]{"L"});
        } catch (Throwable t) {
            Log.w(TAG, "Hidden-API exemption failed: " + t);
        }
    }

    // ---- JNI (implemented in our liblspbridge.so glue; the LSPlant/Dobby
    // engine behind it is bound at runtime — foreign or own) ----

    /** Runs {@code lsplant::Init} state check; true when hooking is usable. */
    private static native boolean nativeInit();

    /**
     * True when an LSPlant engine not shipped by us is already loaded in this
     * process (Vector/LSPosed). No engine code is executed by the probe.
     */
    private static native boolean nativeForeignEnginePresent();

    /**
     * Mirrors {@code lsplant::Hook(env, target, hooker, callback)}.
     *
     * @return backup method object (invoke it to run the original), or null.
     */
    private static native Object nativeDoHook(Object target, Object hooker, Object callback);

    /** Mirrors {@code lsplant::UnHook}. */
    @SuppressWarnings("unused")
    private static native boolean nativeUnHook(Object target);

    /** Mirrors {@code lsplant::Deoptimize}. */
    private static native void nativeDeoptimize(Object method);
}
