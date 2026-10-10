package org.pixel.customparts.pineinject;

import android.app.ActivityThread;
import android.app.Application;
import android.util.Log;
import org.pixel.customparts.manager.lsplant.LsplantBridge;
import org.pixel.customparts.manager.pine.HookEntry;

public class ModEntry {
    private static final String TAG = "PineInject";

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
