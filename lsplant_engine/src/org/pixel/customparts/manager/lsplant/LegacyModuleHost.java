package org.pixel.customparts.manager.lsplant;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.util.Log;

import dalvik.system.DexClassLoader;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
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

import org.pixel.customparts.core.ApkModulesGate;

/**
 * Host for legacy (API ≤90) Xposed modules (APK format).
 * Discovery: {@code assets/xposed_init} class list, plus optional
 * {@code META-INF/xposed/scope.list} and manifest {@code xposedscope}
 * (array resource or {@code ;}-separated string) for default scopes.
 * Instances live per process; dispatch is a direct
 * {@code handleLoadPackage} call (our XposedBridge already routes hooks
 * into LSPlant). Unlike the modern host, an empty effective scope set
 * means "nowhere" (never a wildcard) so an unconfigured legacy module
 * can never spray hooks into system processes.
 */
public class LegacyModuleHost {
    private static final String TAG = "LegacyModuleHost";

    static class LegacyRecord {
        String id;
        String apkPath;
        /** Own package, resolved lazily (null = unresolved/fallback to id). */
        String pkgName;
        final List<String> classNames = new ArrayList<>();
        final Set<String> scopes = new HashSet<>();
        final List<IXposedHookLoadPackage> instances = new ArrayList<>();
        boolean loaded = false;
    }

    private static final Map<String, LegacyRecord> modules = new HashMap<>();
    private static boolean scanned = false;

    public static synchronized void ensureScanned(Context context) {
        // TEMPORARY: APK modules disabled via ApkModulesGate (code kept).
        if (!ApkModulesGate.isEnabled(context)) return;
        if (scanned) return;
        scanned = true;
        scanDir(new File("/system_ext/etc/pixelparts/addons"));
        scanDir(new File("/data/pixelparts/addons"));
        scanRegisteredList(context);
        Log.d(TAG, "Legacy scan complete: " + modules.size() + " module(s).");
    }

    /** APK paths registered by the manager UI (installed modules). */
    private static void scanRegisteredList(Context context) {
        String list = null;
        try {
            list = android.provider.Settings.Global.getString(
                    context.getContentResolver(), XposedModuleHost.MODULE_LIST_KEY);
        } catch (Throwable ignored) {
        }
        if (list == null || list.isEmpty()) return;
        for (String p : list.split(",")) {
            String path = p.trim();
            if (path.isEmpty()) continue;
            File f = new File(path);
            if (!f.getName().endsWith(".apk") || !f.canRead()) continue;
            try {
                scanApk(f);
            } catch (Throwable t) {
                Log.e(TAG, "Failed to load registered legacy module: " + path, t);
            }
        }
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
            rec.id = XposedModuleHost.moduleIdFor(apk);
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
            // Default scopes: bundled scope.list (some hybrids ship one).
            // Manifest xposedscope is resolved lazily per process (needs the
            // module's resources) — see getManifestScopes.
            ZipEntry scope = zip.getEntry("META-INF/xposed/scope.list");
            if (scope != null) {
                BufferedReader sr = null;
                try {
                    sr = new BufferedReader(
                            new InputStreamReader(zip.getInputStream(scope), "UTF-8"));
                    String sl;
                    while ((sl = sr.readLine()) != null) {
                        sl = sl.trim();
                        if (!sl.isEmpty() && !sl.startsWith("#")) rec.scopes.add(sl);
                    }
                } finally {
                    if (sr != null) {
                        try {
                            sr.close();
                        } catch (Throwable ignored) {
                        }
                    }
                }
            }
            if (modules.containsKey(rec.id)) {
                Log.w(TAG, "Duplicate legacy module id " + rec.id + " — overwriting.");
            }
            modules.put(rec.id, rec);
            Log.d(TAG, "Indexed legacy module: " + rec.id
                    + " classes=" + rec.classNames.size()
                    + " scopes=" + rec.scopes.size());
        } finally {
            try {
                zip.close();
            } catch (Throwable ignored) {
            }
        }
    }

    private static String keyBase(Context context, LegacyRecord rec) {
        if (rec.pkgName == null) {
            try {
                android.content.pm.PackageManager pm = context.getPackageManager();
                android.content.pm.PackageInfo pi =
                        pm.getPackageArchiveInfo(rec.apkPath, 0);
                if (pi != null) rec.pkgName = pi.packageName;
            } catch (Throwable ignored) {
            }
        }
        return rec.pkgName != null ? rec.pkgName : rec.id;
    }

    /**
     * Manifest xposedscope: array resource id first, then inline
     * {@code ;}-separated string (LSPosed/Vector parity). Best-effort:
     * cross-package resource lookup can fail (visibility), then empty.
     */
    private static Set<String> getManifestScopes(Context context, LegacyRecord rec) {
        Set<String> out = new HashSet<>();
        try {
            String own = keyBase(context, rec);
            if (own == null) return out;
            android.content.pm.PackageManager pm = context.getPackageManager();
            android.content.pm.ApplicationInfo ai =
                    pm.getApplicationInfo(own, android.content.pm.PackageManager.GET_META_DATA);
            if (ai == null || ai.metaData == null) return out;
            int resId = ai.metaData.getInt("xposedscope", 0);
            if (resId != 0) {
                try {
                    android.content.res.Resources res =
                            pm.getResourcesForApplication(ai);
                    String[] arr = res.getStringArray(resId);
                    if (arr != null) {
                        for (String s : arr) {
                            if (s != null && !s.trim().isEmpty()) out.add(s.trim());
                        }
                    }
                    return out;
                } catch (Throwable ignored) {
                }
            }
            String raw = ai.metaData.getString("xposedscope");
            if (raw != null && !raw.isEmpty()) {
                for (String s : raw.split(";")) {
                    String t = s.trim();
                    if (!t.isEmpty()) out.add(t);
                }
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    private static boolean isEnabled(Context context, LegacyRecord rec) {
        try {
            return android.provider.Settings.Global.getInt(context.getContentResolver(),
                    "pixel_addon_" + keyBase(context, rec) + "_enabled", 0) != 0;
        } catch (Throwable t) {
            return false;
        }
    }

    private static Set<String> getEffectiveScopes(Context context, LegacyRecord rec) {
        // Mirror of the modern host scope_mode: 0 = manifest scope.list (+
        // manifest xposedscope), 1 = custom packages only, 2 = both.
        int mode = 0;
        String base = keyBase(context, rec);
        try {
            mode = Settings.Global.getInt(context.getContentResolver(),
                    "pixel_addon_" + base + "_scope_mode", 0);
        } catch (Throwable ignored) {
        }
        Set<String> out = new HashSet<>();
        if (mode == 0 || mode == 2) {
            out.addAll(rec.scopes);
            out.addAll(getManifestScopes(context, rec));
        }
        if (mode == 1 || mode == 2) {
            try {
                String custom = Settings.Global.getString(context.getContentResolver(),
                        "pixel_addon_" + base + "_packages");
                if (custom != null && !custom.isEmpty()) {
                    for (String pkg : custom.split(",")) {
                        String t = pkg.trim();
                        if (!t.isEmpty()) out.add(t);
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        return out;
    }

    private static synchronized void ensureLoaded(Context context, LegacyRecord rec,
            ClassLoader appClassLoader) {
        if (rec.loaded) return;
        rec.loaded = true;
        try {
            File odexDir = context.getDir("xposed_legacy", Context.MODE_PRIVATE);
            String libPath = XposedModuleHost.nativeLibDirFor(rec.apkPath, odexDir);
            DexClassLoader loader = new DexClassLoader(rec.apkPath,
                    odexDir.getAbsolutePath(), libPath, appClassLoader);
            for (String name : rec.classNames) {
                try {
                    Class<?> clazz = Class.forName(name, false, loader);
                    Object o = clazz.getDeclaredConstructor().newInstance();
                    // YukiHookAPI (and classic modules) implement
                    // IXposedHookZygoteInit on the same entry class: feed it
                    // the module path first, otherwise its internal state
                    // (moduleAppFilePath) stays empty and handleLoadPackage
                    // misbehaves.
                    if (o instanceof IXposedHookZygoteInit) {
                        try {
                            IXposedHookZygoteInit.StartupParam sp =
                                    new IXposedHookZygoteInit.StartupParam();
                            sp.modulePath = rec.apkPath;
                            sp.startsSystemServer = false;
                            ((IXposedHookZygoteInit) o).initZygote(sp);
                        } catch (Throwable t) {
                            Log.e(TAG, "initZygote failed for " + rec.id, t);
                        }
                    }
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

    /** Called from HookEntry for every injected app process (phase 1). */
    public static void dispatchPackage(Context context, ClassLoader appClassLoader,
            String packageName) {
        if (!ApkModulesGate.isEnabled(context)) return; // TEMPORARY kill-switch
        if (XposedModuleHost.SYSTEM_PACKAGE.equals(packageName)) return;
        ensureScanned(context);
        String processName = XposedModuleHost.currentProcessName(packageName);
        ApplicationInfo ai = null;
        try {
            ai = context.getPackageManager().getApplicationInfo(packageName, 0);
        } catch (Throwable ignored) {
        }
        for (LegacyRecord rec : modules.values()) {
            if (!isEnabled(context, rec)) continue;
            Set<String> scopes = getEffectiveScopes(context, rec);
            boolean self = false;
            try {
                self = packageName.equals(keyBase(context, rec));
            } catch (Throwable ignored) {
            }
            if (!self && (scopes.isEmpty() || !scopes.contains(packageName))) {
                // Empty scopes = nowhere (never a wildcard for legacy).
                continue;
            }
            if (self) {
                // Own process: mirror the module's private prefs into the
                // shared store so targets (and status checks) see them.
                // Runs even when instantiation below fails.
                try {
                    PrefsMirror.syncIfSelf(context, packageName,
                            keyBase(context, rec));
                } catch (Throwable ignored) {
                }
            }
            ensureLoaded(context, rec, appClassLoader);
            if (rec.instances.isEmpty()) continue;
            XC_LoadPackage.LoadPackageParam lp =
                    new XC_LoadPackage.LoadPackageParam(
                            new XposedBridge.CopyOnWriteSortedSet<XC_LoadPackage>());
            lp.packageName = packageName;
            lp.processName = processName;
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

    /** All effective scope targets of enabled legacy modules (for whitelist sync). */
    public static synchronized Set<String> getAllModuleTargets(Context context) {
        ensureScanned(context);
        Set<String> out = new HashSet<>();
        for (LegacyRecord rec : modules.values()) {
            try {
                if (!isEnabled(context, rec)) continue;
                out.addAll(getEffectiveScopes(context, rec));
            } catch (Throwable ignored) {
            }
        }
        out.remove(XposedModuleHost.SYSTEM_PACKAGE);
        out.remove("*");
        return out;
    }
}
