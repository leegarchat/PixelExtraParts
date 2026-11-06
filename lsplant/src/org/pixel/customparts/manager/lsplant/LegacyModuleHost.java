package org.pixel.customparts.manager.lsplant;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.util.Log;

import dalvik.system.DexClassLoader;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import android.provider.Settings;

/**
 * Host for legacy (API ≤90) Xposed modules (APK format).
 * Discovery: {@code assets/xposed_init} class list. Instances live
 * per process; dispatch is a direct {@code handleLoadPackage} call
 * (our XposedBridge already routes hooks into LSPlant).
 */
public class LegacyModuleHost {
    private static final String TAG = "LegacyModuleHost";

    static class LegacyRecord {
        String id;
        String apkPath;
        final List<String> classNames = new ArrayList<>();
        final List<IXposedHookLoadPackage> instances = new ArrayList<>();
        boolean loaded = false;
    }

    private static final Map<String, LegacyRecord> modules = new HashMap<>();
    private static boolean scanned = false;

    public static synchronized void ensureScanned(Context context) {
        if (scanned) return;
        scanned = true;
        scanDir(new File("/system_ext/etc/pixelparts/addons"));
        scanDir(new File("/data/pixelparts/addons"));
        Log.d(TAG, "Legacy scan complete: " + modules.size() + " module(s).");
    }

    private static void scanDir(File dir) {
        if (dir == null || !dir.isDirectory()) return;
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (!f.getName().endsWith(".apk") || !f.canRead()) continue;
            try {
                scanApk(f);
            } catch (Throwable t) {
                Log.e(TAG, "Failed to scan legacy module: " + f.getName(), t);
            }
        }
    }

    private static void scanApk(File apk) throws Exception {
        ZipFile zip = new ZipFile(apk);
        try {
            if (zip.getEntry("META-INF/xposed/java_init.list") != null) {
                return; // modern — XposedModuleHost owns it
            }
            ZipEntry init = zip.getEntry("assets/xposed_init");
            if (init == null) return;
            LegacyRecord rec = new LegacyRecord();
            rec.id = apk.getName().replaceAll("\\.apk$", "");
            rec.apkPath = apk.getAbsolutePath();
            BufferedReader br = null;
            try {
                br = new BufferedReader(
                        new InputStreamReader(zip.getInputStream(init), "UTF-8"));
                String line;
                while ((line = br.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) continue;
                    rec.classNames.add(line);
                }
            } finally {
                if (br != null) {
                    try {
                        br.close();
                    } catch (Throwable ignored) {
                    }
                }
            }
            if (rec.classNames.isEmpty()) return;
            if (modules.containsKey(rec.id)) {
                Log.w(TAG, "Duplicate legacy module id " + rec.id + " — overwriting.");
            }
            modules.put(rec.id, rec);
            Log.d(TAG, "Indexed legacy module: " + rec.id
                    + " classes=" + rec.classNames.size());
        } finally {
            try {
                zip.close();
            } catch (Throwable ignored) {
            }
        }
    }

    private static String keyBase(Context context, LegacyRecord rec) {
        try {
            android.content.pm.PackageManager pm = context.getPackageManager();
            android.content.pm.PackageInfo pi =
                    pm.getPackageArchiveInfo(rec.apkPath, 0);
            if (pi != null) return pi.packageName;
        } catch (Throwable ignored) {
        }
        return rec.id;
    }

    private static boolean isEnabled(Context context, LegacyRecord rec) {
        try {
            return android.provider.Settings.Global.getInt(context.getContentResolver(),
                    "pixel_addon_" + keyBase(context, rec) + "_enabled", 0) != 0;
        } catch (Throwable t) {
            return false;
        }
    }

    private static synchronized void ensureLoaded(Context context, LegacyRecord rec) {
        if (rec.loaded) return;
        rec.loaded = true;
        try {
            File odexDir = context.getDir("xposed_legacy", Context.MODE_PRIVATE);
            DexClassLoader loader = new DexClassLoader(rec.apkPath,
                    odexDir.getAbsolutePath(), null, LegacyModuleHost.class.getClassLoader());
            for (String name : rec.classNames) {
                try {
                    Class<?> clazz = Class.forName(name, false, loader);
                    Object o = clazz.getDeclaredConstructor().newInstance();
                    if (o instanceof IXposedHookLoadPackage) {
                        rec.instances.add((IXposedHookLoadPackage) o);
                    } else {
                        Log.w(TAG, "Class " + name + " is not an IXposedHookLoadPackage");
                    }
                } catch (Throwable t) {
                    Log.e(TAG, "Failed to instantiate " + name + " from " + rec.id, t);
                }
            }
        } catch (Throwable t) {
            Log.e(TAG, "Failed to load legacy module " + rec.id, t);
        }
    }

    /** Called from HookEntry for every injected app process. */
    public static void dispatchPackage(Context context, ClassLoader appClassLoader,
            String packageName) {
        if (XposedModuleHost.SYSTEM_PACKAGE.equals(packageName)) return;
        ensureScanned(context);
        ApplicationInfo ai = null;
        try {
            ai = context.getPackageManager().getApplicationInfo(packageName, 0);
        } catch (Throwable ignored) {
        }
        for (LegacyRecord rec : modules.values()) {
            if (!isEnabled(context, rec)) continue;
            ensureLoaded(context, rec);
            if (rec.instances.isEmpty()) continue;
            XC_LoadPackage.LoadPackageParam lp =
                    new XC_LoadPackage.LoadPackageParam(
                            new XposedBridge.CopyOnWriteSortedSet<XC_LoadPackage>());
            lp.packageName = packageName;
            lp.processName = packageName;
            lp.classLoader = appClassLoader;
            lp.appInfo = ai;
            lp.isFirstApplication = true;
            for (IXposedHookLoadPackage inst : rec.instances) {
                try {
                    inst.handleLoadPackage(lp);
                } catch (Throwable t) {
                    Log.e(TAG, "handleLoadPackage failed for " + rec.id, t);
                }
            }
        }
    }

    /** Debug/diagnostics: indexed legacy module ids. */
    public static synchronized Set<String> getModuleIds() {
        return new HashSet<>(modules.keySet());
    }
}
