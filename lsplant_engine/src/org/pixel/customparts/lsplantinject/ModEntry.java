package org.pixel.customparts.lsplantinject;

import android.app.ActivityThread;
import android.app.Application;
import android.util.Log;
import org.pixel.customparts.manager.lsplant.LsplantBridge;
import org.pixel.customparts.manager.lsplant.HookEntry;

public class ModEntry {
    private static final String TAG = "LsplantInject";

    /**
     * Phase 1 — called from ActivityThread BEFORE Application creation.
     * Loads the native glue, binds the LSPlant backend and runs the
     * module early dispatch (no JAR addons here — those stay in phase 2).
     */
    public static void initEarly(android.content.Context context,
            ClassLoader classLoader, String packageName) {
        try {
            System.loadLibrary("lspbridge");
        } catch (Throwable t) {
            Log.e(TAG, "Failed to load lspbridge glue", t);
            return;
        }
        if (!LsplantBridge.init()) {
            Log.e(TAG, "LSPlant backend unavailable, hooks disabled");
            return;
        }
        try {
            HookEntry.initModulesEarly(context, classLoader, packageName);
        } catch (Throwable t) {
            Log.e(TAG, "HookEntry.initModulesEarly failed", t);
        }
    }

    public static void init() {
        // LSPlant backend binds at runtime: if Vector/LSPosed already injected
        // its engine into this process, our glue reuses it (native probe
        // inside LsplantBridge) and our own prebuilts are never loaded —
        // two cores in one process is the native conflict. Standalone
        // processes load the /system prebuilts as before.
        // Load order matters: glue first (no DT_NEEDED on engines anymore),
        // engine libs only when no foreign engine is present.
        try {
            System.loadLibrary("lspbridge");
        } catch (Throwable t) {
            Log.e(TAG, "Failed to load lspbridge glue", t);
            return;
        }
        if (!LsplantBridge.init()) {
            Log.e(TAG, "LSPlant backend unavailable, hooks disabled");
            return;
        }

        Application app = ActivityThread.currentApplication();
        if (app == null) {
            // system_server has no Application: use the system context and
            // this loader, package "android" (module system dispatch only).
            try {
                ActivityThread at = ActivityThread.currentActivityThread();
                android.content.Context sysCtx = at.getSystemContext();
                if (sysCtx == null) return;
                HookEntry.init(sysCtx, ModEntry.class.getClassLoader(), "android");
            } catch (Throwable t) {
                Log.e(TAG, "HookEntry.init(system) failed", t);
            }
            return;
        }

        ClassLoader classLoader = app.getClassLoader();
        String packageName = app.getPackageName();

        try {
            HookEntry.init(app, classLoader, packageName);
        } catch (Throwable t) {
            Log.e(TAG, "HookEntry.init failed", t);
        }
    }
}
