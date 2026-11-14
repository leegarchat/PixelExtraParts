package org.pixel.customparts.manager.lsplant;

import android.content.Context;
import android.os.FileObserver;
import android.util.Log;

import java.io.File;

/**
 * One-way prefs bridge: module UI writes feature flags into its own
 * private {@code shared_prefs/*.xml}; target processes cannot read them
 * (SELinux app_data isolation, no package visibility). The only place
 * whose UID may read the module's files is the module's own (self)
 * process — this helper mirrors them into the shared
 * {@code /data/pixelparts/rprefs/<pkg>.xml} groups there, where every
 * injected process (and {@link XSharedPreferences}) can read them.
 *
 * <p>Sync points: once per self-process dispatch, plus a live
 * {@link FileObserver} on the module's {@code shared_prefs} dir so UI
 * edits propagate without restarts.
 */
public final class PrefsMirror {
    private static final String TAG = "PrefsMirror";

    private static final Object LOCK = new Object();
    private static String observedPkg = null;
    private static FileObserver observer = null;

    private PrefsMirror() {
    }

    /**
     * Mirrors the module's own shared_prefs into shared groups when this
     * process IS the module (self-targeting). No-op elsewhere.
     */
    public static void syncIfSelf(Context context, String packageName, String ownPkg) {
        if (ownPkg == null || !ownPkg.equals(packageName)) return;
        try {
            syncModulePrefs(context, ownPkg);
        } catch (Throwable t) {
            Log.w(TAG, "Self prefs sync failed for " + ownPkg, t);
        }
        try {
            startWatching(context, ownPkg);
        } catch (Throwable t) {
            Log.w(TAG, "Prefs watch failed for " + ownPkg, t);
        }
    }

    private static void syncModulePrefs(Context context, String ownPkg) {
        // In the module's own process its dataDir is directly correct —
        // no user-id guessing needed. Fallback covers exotic layouts.
        File prefsDir = null;
        try {
            String dataDir = context.getApplicationInfo().dataDir;
            if (dataDir != null) {
                File cand = new File(dataDir, "shared_prefs");
                if (!cand.exists()) {
                    // Own process: creating our own shared_prefs is allowed.
                    // Needed when the mirror runs in phase 1 before the app
                    // created the dir itself — and before watching it.
                    try {
                        cand.mkdirs();
                    } catch (Throwable ignored) {
                    }
                }
                if (cand.isDirectory()) prefsDir = cand;
            }
        } catch (Throwable ignored) {
        }
        if (prefsDir == null) {
            File cand = new File("/data/user/0/" + ownPkg + "/shared_prefs");
            if (cand.isDirectory()) prefsDir = cand;
        }
        if (prefsDir == null) return;
        File[] files = prefsDir.listFiles();
        if (files == null) return;
        int mirrored = 0;
        for (File f : files) {
            String name = f.getName();
            if (!name.endsWith(".xml")) continue;
            String group = name.substring(0, name.length() - 4);
            try {
                if (RemotePrefs.importFile(ownPkg, group, f)) mirrored++;
            } catch (Throwable ignored) {
            }
        }
        if (mirrored > 0) {
            Log.d(TAG, "Mirrored " + mirrored + " prefs group(s) for " + ownPkg);
        }
    }

    private static void startWatching(final Context context, final String ownPkg) {
        synchronized (LOCK) {
            if (ownPkg.equals(observedPkg) && observer != null) return;
            try {
                if (observer != null) observer.stopWatching();
            } catch (Throwable ignored) {
            }
            observedPkg = null;
            observer = null;
            File prefsDir = null;
            try {
                String dataDir = context.getApplicationInfo().dataDir;
                if (dataDir != null) {
                    File cand = new File(dataDir, "shared_prefs");
                    if (!cand.exists()) {
                        try {
                            cand.mkdirs();
                        } catch (Throwable ignored) {
                        }
                    }
                    if (cand.isDirectory()) prefsDir = cand;
                }
            } catch (Throwable ignored) {
            }
            if (prefsDir == null) return;
            final Context appCtx = context.getApplicationContext() != null
                    ? context.getApplicationContext() : context;
            // NOTE: no CREATE — SharedPreferencesImpl fires CREATE when the
            // file is still empty (O_CREAT|O_TRUNC), before CLOSE_WRITE.
            // Syncing on CREATE would mirror (and publish) a blank map.
            FileObserver o = new FileObserver(prefsDir.getAbsolutePath(),
                    FileObserver.CLOSE_WRITE | FileObserver.MOVED_TO
                            | FileObserver.DELETE) {
                @Override
                public void onEvent(int event, String path) {
                    if (path == null || !path.endsWith(".xml")) return;
                    try {
                        syncModulePrefs(appCtx, ownPkg);
                    } catch (Throwable ignored) {
                    }
                }
            };
            o.startWatching();
            observer = o;
            observedPkg = ownPkg;
            Log.d(TAG, "Watching prefs dir for " + ownPkg);
        }
    }
}
