package org.pixel.customparts.manager.lsplant;

import android.content.Context;
import android.util.Log;

import dalvik.system.DexClassLoader;
import io.github.libxposed.api.XposedModuleInterface;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import android.provider.Settings;

/**
 * Host for modern (API 101) Xposed modules (APK format).
 * Discovery: META-INF/xposed/{java_init.list,module.prop,scope.list}.
 * Instances live per process; loaded lazily on first dispatch.
 */
public class XposedModuleHost {
    private static final String TAG = "XposedModuleHost";
    private static final int HOST_API = 101;

    static class ModuleRecord {
        final String id;
        final String apkPath;
        final List<String> classNames = new ArrayList<>();
        final Set<String> scopes = new HashSet<>();
        boolean staticScope = false;
        final List<XposedModuleInterface> instances = new ArrayList<>();
        boolean loaded = false;
        boolean moduleLoadedCalled = false;
    }

    private static final Map<String, ModuleRecord> modules = new HashMap<>();
    private static boolean scanned = false;

    /** System package name as seen by ActivityThread in system_server. */
    public static final String SYSTEM_PACKAGE = "android";

    public static synchronized void ensureScanned(Context context) {
        if (scanned) return;
        scanned = true;
        scanDir(new File("/system_ext/etc/pixelparts/addons"));
        scanDir(new File(context.getFilesDir().getParent() + "/addons"));
        Log.d(TAG, "Module scan complete: " + modules.size() + " modern module(s).");
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
                Log.e(TAG, "Failed to scan module APK: " + f.getName(), t);
            }
        }
    }

    private static void scanApk(File apk) throws Exception {
        ZipFile zip = new ZipFile(apk);
        try {
            ZipEntry init = zip.getEntry("META-INF/xposed/java_init.list");
            if (init == null) return; // not a modern module (legacy path ignores APKs)
            ModuleRecord rec = new ModuleRecord();
            rec.id = apk.getName().replaceAll("\\.apk$", "");
            rec.apkPath = apk.getAbsolutePath();
            BufferedReader br = new BufferedReader(
                    new InputStreamReader(zip.getInputStream(init), "UTF-8"));
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                rec.classNames.add(line);
            }
            br.close();
            if (rec.classNames.isEmpty()) return;
            ZipEntry prop = zip.getEntry("META-INF/xposed/module.prop");
            int minApi = 0;
            if (prop != null) {
                BufferedReader pr = new BufferedReader(
                        new InputStreamReader(zip.getInputStream(prop), "UTF-8"));
                String pl;
                while ((pl = pr.readLine()) != null) {
                    pl = pl.trim();
                    if (pl.startsWith("minApiVersion=")) {
                        try {
                            minApi = Integer.parseInt(pl.substring("minApiVersion=".length()));
                        } catch (Throwable ignored) {
                        }
                    } else if (pl.startsWith("staticScope=")) {
                        rec.staticScope = pl.substring("staticScope=".length())
                                .equalsIgnoreCase("true");
                    }
                }
                pr.close();
            }
            if (minApi > HOST_API) {
                Log.w(TAG, "Module " + rec.id + " needs API " + minApi + ", host is "
                        + HOST_API + " — skipped");
                return;
            }
            ZipEntry scope = zip.getEntry("META-INF/xposed/scope.list");
            if (scope != null) {
                BufferedReader sr = new BufferedReader(
                        new InputStreamReader(zip.getInputStream(scope), "UTF-8"));
                String sl;
                while ((sl = sr.readLine()) != null) {
                    sl = sl.trim();
                    if (!sl.isEmpty() && !sl.startsWith("#")) rec.scopes.add(sl);
                }
                sr.close();
            }
            modules.put(rec.id, rec);
            Log.d(TAG, "Indexed modern module: " + rec.id + " classes=" + rec.classNames.size()
                    + " scopes=" + rec.scopes);
        } finally {
            try {
                zip.close();
            } catch (Throwable ignored) {
            }
        }
    }

    private static boolean isModuleEnabled(Context context, ModuleRecord rec) {
        try {
            return Settings.Global.getInt(context.getContentResolver(),
                    "pixel_addon_" + rec.id + "_enabled", 1) != 0;
        } catch (Throwable t) {
            return true;
        }
    }

    private static synchronized void ensureLoaded(Context context, ModuleRecord rec) {
        if (rec.loaded) return;
        rec.loaded = true;
        try {
            File odexDir = context.getDir("xposed_modules", Context.MODE_PRIVATE);
            DexClassLoader loader = new DexClassLoader(rec.apkPath,
                    odexDir.getAbsolutePath(), null, XposedModuleHost.class.getClassLoader());
            for (String name : rec.classNames) {
                try {
                    Class<?> clazz = Class.forName(name, false, loader);
                    Object o = clazz.newInstance();
                    if (o instanceof XposedModuleInterface) {
                        rec.instances.add((XposedModuleInterface) o);
                    } else {
                        Log.w(TAG, "Module class " + name + " is not an XposedModuleInterface");
                    }
                } catch (Throwable t) {
                    Log.e(TAG, "Failed to instantiate " + name + " from " + rec.id, t);
                }
            }
            XposedModuleInterface.ModuleLoadedParam mp =
                    new XposedModuleInterface.ModuleLoadedParam(loader);
            for (XposedModuleInterface inst : rec.instances) {
                try {
                    inst.onModuleLoaded(mp);
                } catch (Throwable t) {
                    Log.e(TAG, "onModuleLoaded failed for " + rec.id, t);
                }
            }
            rec.moduleLoadedCalled = true;
        } catch (Throwable t) {
            Log.e(TAG, "Failed to load module " + rec.id, t);
        }
    }

    private static boolean targetsPackage(ModuleRecord rec, String packageName) {
        if (rec.scopes.isEmpty()) return true; // wildcard
        if (rec.scopes.contains(packageName)) return true;
        return false;
    }

    /** Called from HookEntry for every injected app process. */
    public static void dispatchPackage(Context context, ClassLoader appClassLoader,
            String packageName) {
        if (SYSTEM_PACKAGE.equals(packageName)) return;
        ensureScanned(context);
        for (ModuleRecord rec : modules.values()) {
            if (!isModuleEnabled(context, rec)) continue;
            if (!targetsPackage(rec, packageName)) continue;
            ensureLoaded(context, rec);
            if (rec.instances.isEmpty()) continue;
            XposedModuleInterface.PackageLoadedParam lp =
                    new XposedModuleInterface.PackageLoadedParam(packageName, appClassLoader,
                            true);
            XposedModuleInterface.PackageReadyParam rp =
                    new XposedModuleInterface.PackageReadyParam(packageName, appClassLoader,
                            true);
            for (XposedModuleInterface inst : rec.instances) {
                try {
                    inst.onPackageLoaded(lp);
                } catch (Throwable t) {
                    Log.e(TAG, "onPackageLoaded failed for " + rec.id, t);
                }
                try {
                    inst.onPackageReady(rp);
                } catch (Throwable t) {
                    Log.e(TAG, "onPackageReady failed for " + rec.id, t);
                }
            }
        }
    }

    /** Called once in system_server when a system-scope module is installed. */
    public static void dispatchSystem(Context context, ClassLoader ssClassLoader) {
        ensureScanned(context);
        for (ModuleRecord rec : modules.values()) {
            if (!isModuleEnabled(context, rec)) continue;
            if (!rec.scopes.contains("system")) continue;
            ensureLoaded(context, rec);
            if (rec.instances.isEmpty()) continue;
            XposedModuleInterface.SystemServerStartingParam sp =
                    new XposedModuleInterface.SystemServerStartingParam(ssClassLoader);
            for (XposedModuleInterface inst : rec.instances) {
                try {
                    inst.onSystemServerStarting(sp);
                } catch (Throwable t) {
                    Log.e(TAG, "onSystemServerStarting failed for " + rec.id, t);
                }
            }
        }
    }

    /** True when at least one enabled module targets system_server. */
    public static boolean hasSystemModules(Context context) {
        ensureScanned(context);
        for (ModuleRecord rec : modules.values()) {
            if (!isModuleEnabled(context, rec)) continue;
            if (rec.scopes.contains("system")) return true;
        }
        return false;
    }

    /** Debug/diagnostics: indexed module ids. */
    public static synchronized Set<String> getModuleIds() {
        return new HashSet<>(modules.keySet());
    }

    /** Used by tests/tools to drop cached scan state. */
    public static synchronized void rescan(Context context) {
        modules.clear();
        scanned = false;
        ensureScanned(context);
    }
}
