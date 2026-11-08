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
    private static final int HOST_API = 102;

    static class ModuleRecord {
        String id;
        String apkPath;
        /** Own package, resolved lazily (null = unresolved/fallback to id). */
        String pkgName;
        final List<String> classNames = new ArrayList<>();
        final Set<String> scopes = new HashSet<>();
        final List<XposedModuleInterface> instances = new ArrayList<>();
        boolean loaded = false;
        boolean moduleLoadedCalled = false;
    }

    private static final Map<String, ModuleRecord> modules = new HashMap<>();
    private static boolean scanned = false;

    /** System package name as seen by ActivityThread in system_server. */
    public static final String SYSTEM_PACKAGE = "android";

    /** Settings flag mirrored for the framework ss-inject gate. */
    static final String SYSTEM_MODULES_KEY = "pixelparts_xposed_has_system";

    /** Param impls (interfaces are implemented by the host, read by modules). */
    static final class LoadedParamImpl implements XposedModuleInterface.ModuleLoadedParam {
        private final ClassLoader cl;
        private final String processName;

        LoadedParamImpl(ClassLoader cl, String processName) {
            this.cl = cl;
            this.processName = processName;
        }

        @Override
        public ClassLoader getClassLoader() {
            return cl;
        }

        @Override
        public String getProcessName() {
            return processName;
        }
    }

    static final class PkgLoadedParamImpl implements XposedModuleInterface.PackageLoadedParam {
        private final String pkg;
        private final ClassLoader cl;
        private final boolean first;

        PkgLoadedParamImpl(String pkg, ClassLoader cl, boolean first) {
            this.pkg = pkg;
            this.cl = cl;
            this.first = first;
        }

        @Override
        public String getPackageName() {
            return pkg;
        }

        @Override
        public ClassLoader getClassLoader() {
            return cl;
        }

        @Override
        public boolean isFirstPackage() {
            return first;
        }
    }

    static final class PkgReadyParamImpl implements XposedModuleInterface.PackageReadyParam {
        private final String pkg;
        private final ClassLoader cl;
        private final boolean first;
        private final android.content.pm.ApplicationInfo appInfo;

        PkgReadyParamImpl(String pkg, ClassLoader cl, boolean first,
                android.content.pm.ApplicationInfo appInfo) {
            this.pkg = pkg;
            this.cl = cl;
            this.first = first;
            this.appInfo = appInfo;
        }

        @Override
        public String getPackageName() {
            return pkg;
        }

        @Override
        public ClassLoader getClassLoader() {
            return cl;
        }

        @Override
        public boolean isFirstPackage() {
            return first;
        }

        @Override
        public android.content.pm.ApplicationInfo getApplicationInfo() {
            return appInfo;
        }
    }

    static final class SystemStartingParamImpl
            implements XposedModuleInterface.SystemServerStartingParam {
        private final ClassLoader cl;

        SystemStartingParamImpl(ClassLoader cl) {
            this.cl = cl;
        }

        @Override
        public ClassLoader getClassLoader() {
            return cl;
        }
    }

    /** Settings list of manager-registered module APK paths (comma-separated). */
    static final String MODULE_LIST_KEY = "pixelparts_xposed_modules";

    public static synchronized void ensureScanned(Context context) {
        if (scanned) return;
        scanned = true;
        scanDir(new File("/system_ext/etc/pixelparts/addons"));
        scanDir(new File("/data/pixelparts/addons"));
        scanRegisteredList(context);
        Log.d(TAG, "Module scan complete: " + modules.size() + " modern module(s).");
        syncSystemFlag(context);
    }

    /**
     * Modules registered by the manager UI (installed APKs whose META-INF
     * was already verified there). Modern modules carry no manifest
     * markers, so per-process zip-walking all installed apps is skipped —
     * the manager owns discovery, the engine only loads listed paths.
     */
    private static void scanRegisteredList(Context context) {
        String list = null;
        try {
            list = Settings.Global.getString(context.getContentResolver(), MODULE_LIST_KEY);
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
                Log.e(TAG, "Failed to load registered module: " + path, t);
            }
        }
    }

    /**
     * Own package of an installed module APK (no install required).
     * Used for self-targeting: an enabled module always receives its own
     * package dispatch so it can report "activated".
     * NOTE: the manager UI must also set
     * {@code pixel_extra_parts_inject_package_<module-pkg>=1} on enable,
     * otherwise the module process is never injected to begin with.
     */
    static String getOwnPackage(Context context, String apkPath) {
        try {
            android.content.pm.PackageManager pm = context.getPackageManager();
            android.content.pm.PackageInfo pi = pm.getPackageArchiveInfo(apkPath,
                    android.content.pm.PackageManager.GET_META_DATA);
            if (pi != null) return pi.packageName;
        } catch (Throwable ignored) {
        }
        return null;
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
            ZipEntry prop = zip.getEntry("META-INF/xposed/module.prop");
            int minApi = 0;
            if (prop != null) {
                BufferedReader pr = null;
                try {
                    pr = new BufferedReader(
                            new InputStreamReader(zip.getInputStream(prop), "UTF-8"));
                    String pl;
                    while ((pl = pr.readLine()) != null) {
                        pl = pl.trim();
                        if (pl.startsWith("minApiVersion=")) {
                            try {
                                minApi = Integer.parseInt(pl.substring("minApiVersion=".length()));
                            } catch (Throwable ignored) {
                            }
                        }
                    }
                } finally {
                    if (pr != null) {
                        try {
                            pr.close();
                        } catch (Throwable ignored) {
                        }
                    }
                }
            }
            if (minApi > HOST_API) {
                Log.w(TAG, "Module " + rec.id + " needs API " + minApi + ", host is "
                        + HOST_API + " — skipped");
                return;
            }
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
                Log.w(TAG, "Duplicate module id " + rec.id + " — overwriting "
                        + modules.get(rec.id).apkPath);
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

    /**
     * Canonical settings key base: own package when known (matches the
     * manager UI, which keys everything by package), else the file id.
     */
    private static String keyBase(Context context, ModuleRecord rec) {
        if (rec.pkgName == null) {
            try {
                rec.pkgName = getOwnPackage(context, rec.apkPath);
            } catch (Throwable ignored) {
            }
        }
        return rec.pkgName != null ? rec.pkgName : rec.id;
    }

    private static boolean isModuleEnabled(Context context, ModuleRecord rec) {
        // Secure default: unknown APKs stay OFF until explicitly enabled.
        // (Manual enable: settings put global pixel_addon_<package>_enabled 1.)
        try {
            return Settings.Global.getInt(context.getContentResolver(),
                    "pixel_addon_" + keyBase(context, rec) + "_enabled", 0) != 0;
        } catch (Throwable t) {
            return false;
        }
    }

    private static synchronized void ensureLoaded(Context context, ModuleRecord rec,
            String processName) {
        if (rec.loaded) return;
        rec.loaded = true;
        String ownPkg = null;
        try {
            ownPkg = getOwnPackage(context, rec.apkPath);
        } catch (Throwable ignored) {
        }
        try {
            File odexDir = context.getDir("xposed_modules", Context.MODE_PRIVATE);
            DexClassLoader loader = new DexClassLoader(rec.apkPath,
                    odexDir.getAbsolutePath(), null, XposedModuleHost.class.getClassLoader());
            for (String name : rec.classNames) {
                try {
                    Class<?> clazz = Class.forName(name, false, loader);
                    Object o = clazz.getDeclaredConstructor().newInstance();
                    if (o instanceof XposedModuleInterface) {
                        if (o instanceof io.github.libxposed.api.XposedModule) {
                            try {
                                ((io.github.libxposed.api.XposedModule) o)
                                        .attachHost(context, ownPkg);
                            } catch (Throwable ignored) {
                            }
                        }
                        rec.instances.add((XposedModuleInterface) o);
                    } else {
                        Log.w(TAG, "Module class " + name + " is not an XposedModuleInterface");
                    }
                } catch (Throwable t) {
                    Log.e(TAG, "Failed to instantiate " + name + " from " + rec.id, t);
                }
            }
            XposedModuleInterface.ModuleLoadedParam mp =
                    new LoadedParamImpl(loader, processName);
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

    private static Set<String> getEffectiveScopes(Context context, ModuleRecord rec) {
        // Mirror of AddonLoader scope_mode: 0 = manifest scope.list,
        // 1 = custom packages only, 2 = both. Default 0.
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

    private static boolean targetsPackage(Set<String> scopes, String packageName) {
        if (scopes.isEmpty()) return true; // wildcard
        if (scopes.contains("*")) return true; // explicit wildcard
        if (scopes.contains(packageName)) return true;
        return false;
    }

    /** Called from HookEntry for every injected app process. */
    public static void dispatchPackage(Context context, ClassLoader appClassLoader,
            String packageName) {
        if (SYSTEM_PACKAGE.equals(packageName)) return;
        ensureScanned(context);
        for (ModuleRecord rec : modules.values()) {
            if (!isModuleEnabled(context, rec)) continue;
            // Self-targeting: enabled module always sees its own package
            // (Vector parity — otherwise "activated" status never shows).
            boolean self = false;
            try {
                String own = getOwnPackage(context, rec.apkPath);
                self = packageName.equals(own);
            } catch (Throwable ignored) {
            }
            if (!self && !targetsPackage(getEffectiveScopes(context, rec), packageName)) {
                continue;
            }
            ensureLoaded(context, rec, packageName);
            if (rec.instances.isEmpty()) continue;
            android.content.pm.ApplicationInfo ai = null;
            try {
                ai = context.getPackageManager().getApplicationInfo(packageName, 0);
            } catch (Throwable ignored) {
            }
            XposedModuleInterface.PackageLoadedParam lp =
                    new PkgLoadedParamImpl(packageName, appClassLoader, true);
            XposedModuleInterface.PackageReadyParam rp =
                    new PkgReadyParamImpl(packageName, appClassLoader, true, ai);
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
            ensureLoaded(context, rec, SYSTEM_PACKAGE);
            if (rec.instances.isEmpty()) continue;
            XposedModuleInterface.SystemServerStartingParam sp =
                    new SystemStartingParamImpl(ssClassLoader);
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

    /** Mirror system-module presence for the framework ss-inject gate. */
    public static void syncSystemFlag(Context context) {
        boolean has = false;
        try {
            has = hasSystemModules(context);
        } catch (Throwable ignored) {
        }
        try {
            Settings.Global.putInt(context.getContentResolver(), SYSTEM_MODULES_KEY,
                    has ? 1 : 0);
        } catch (Throwable ignored) {
        }
    }

    /** All effective scope targets of enabled modules (for whitelist sync). */
    public static synchronized Set<String> getAllModuleTargets(Context context) {
        ensureScanned(context);
        Set<String> out = new HashSet<>();
        for (ModuleRecord rec : modules.values()) {
            if (!isModuleEnabled(context, rec)) continue;
            out.addAll(getEffectiveScopes(context, rec));
        }
        out.remove(SYSTEM_PACKAGE);
        out.remove("*");
        return out;
    }
}
