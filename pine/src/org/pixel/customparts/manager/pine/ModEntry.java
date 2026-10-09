package org.pixel.customparts.pineinject;

import android.app.ActivityThread;
import android.app.Application;
import android.util.Log;
import org.pixel.customparts.manager.lsplant.LsplantBridge;
import org.pixel.customparts.manager.pine.HookEntry;

public class ModEntry {
    private static final String TAG = "PineInject";

    public static void init() {
        // LSPlant native backend (Dobby + ART symbol resolver inside liblsplant.so).
        // The Xposed-compat shim (de.robv.android.xposed.*) talks to it through
        // LsplantBridge; hook files are unchanged.
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
