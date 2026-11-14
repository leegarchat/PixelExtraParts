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

import org.pixel.customparts.core.ApkModulesGate;

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
        private final boolean systemServer;

        LoadedParamImpl(ClassLoader cl, String processName, boolean systemServer) {
            this.cl = cl;
            this.processName = processName;
            this.systemServer = systemServer;
        }

        @Override
        public ClassLoader getClassLoader() {
            return cl;
        }

        @Override
        public String getProcessName() {
            return processName;
        }

        @Override
        public boolean isSystemServer() {
            return systemServer;
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
        // TEMPORARY: APK modules disabled via ApkModulesGate (code kept).
        // Return without marking scanned so re-enabling takes effect live.
        if (!ApkModulesGate.isEnabled(context)) return;
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
            rec.id = moduleIdFor(apk);
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
            String packageName, String processName, ClassLoader appClassLoader) {
        if (rec.loaded) return;
        rec.loaded = true;
        String ownPkg = null;
        try {
            ownPkg = getOwnPackage(context, rec.apkPath);
        } catch (Throwable ignored) {
        }
        try {
            File odexDir = context.getDir("xposed_modules", Context.MODE_PRIVATE);
            // Module native libs (e.g. DexKit's libdexkit.so) live extracted
            // next to base.apk (<dir>/lib/<arch>/); derive the dir from the
            // APK path (no package visibility needed) so System.loadLibrary
            // from module code resolves.
            String libPath = nativeLibDirFor(rec.apkPath, odexDir);
            DexClassLoader loader = new DexClassLoader(rec.apkPath,
                    odexDir.getAbsolutePath(), libPath, appClassLoader);
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
                    new LoadedParamImpl(loader, processName,
                            SYSTEM_PACKAGE.equals(packageName));
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

    /**
     * Native library dir for an installed module APK, derived from the APK
     * path itself ({@code <dir>/base.apk} → {@code <dir>/lib/<arch>/}).
     * Returns null when nothing usable exists (pure-Java modules).
     */
    static String nativeLibDirFor(String apkPath) {
        return nativeLibDirFor(apkPath, null);
    }

    /**
     * Native library search path for a module APK. Prefers the installed
     * {@code lib/<arch>/} dir; when the installer left it empty
     * (extractNativeLibs=false style — all BlueCat patchers), extracts the
     * bundled {@code lib/<abi>/*.so} into {@code odexDir} so that
     * {@code System.loadLibrary} from module code resolves. Returns null
     * when the module ships no native libs at all.
     */
    static String nativeLibDirFor(String apkPath, File odexDir) {
        String installed = installedLibDirFor(apkPath);
        if (installed != null) return installed;
        return extractNativeLibs(apkPath, odexDir);
    }

    private static String installedLibDirFor(String apkPath) {
        if (apkPath == null) return null;
        try {
            File apk = new File(apkPath);
            File parent = apk.getParentFile();
            if (parent == null) return null;
            for (String abi : candidateAbis()) {
                String arch = abi;
                int dash = abi.indexOf('-');
                // Installed layout uses the short arch (lib/arm64, lib/arm, …).
                if (dash > 0 && (abi.startsWith("arm") || abi.startsWith("x86"))) {
                    if (abi.startsWith("armeabi")) arch = "arm";
                    else if (abi.startsWith("arm64")) arch = "arm64";
                    else arch = abi.substring(0, dash);
                }
                File dir = new File(parent, "lib" + File.separator + arch);
                if (isNonEmptyDir(dir)) return dir.getAbsolutePath();
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static String[] candidateAbis() {
        try {
            String[] abis = android.os.Build.SUPPORTED_ABIS;
            if (abis != null && abis.length > 0) return abis;
        } catch (Throwable ignored) {
        }
        return new String[]{"arm64-v8a"};
    }

    private static boolean isNonEmptyDir(File dir) {
        try {
            if (dir != null && dir.isDirectory()) {
                String[] kids = dir.list();
                if (kids != null && kids.length > 0) return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    /** Stable per-APK id (parent dir + file name — plain base.apk collides). */
    static String moduleIdFor(File apk) {
        String name = apk.getName().replaceAll("\\.apk$", "");
        try {
            File parent = apk.getParentFile();
            if (parent != null && !parent.getName().isEmpty()) {
                return parent.getName() + "-" + name;
            }
        } catch (Throwable ignored) {
        }
        return name;
    }

    /**
     * Extracts bundled {@code lib/<abi>/*.so} from the module APK into
     * {@code <odexDir>/lib-<id>/} (writable + executable by the host UID).
     * Skips work when the target dir already holds .so files.
     */
    private static String extractNativeLibs(String apkPath, File odexDir) {
        if (apkPath == null || odexDir == null) return null;
        File outDir = null;
        try {
            outDir = new File(odexDir, "lib-"
                    + moduleIdFor(new File(apkPath)).replaceAll("[^A-Za-z0-9_.-]", "_"));
            if (isNonEmptyDir(outDir)) return outDir.getAbsolutePath();
            outDir.mkdirs();
            if (!outDir.isDirectory()) return null;
            java.util.zip.ZipFile zip = new java.util.zip.ZipFile(apkPath);
            try {
                java.util.Enumeration<? extends java.util.zip.ZipEntry> entries =
                        zip.entries();
                String[] abis = candidateAbis();
                int count = 0;
                while (entries.hasMoreElements()) {
                    java.util.zip.ZipEntry e = entries.nextElement();
                    String n = e.getName();
                    if (e.isDirectory() || !n.endsWith(".so")) continue;
                    boolean wanted = false;
                    for (String abi : abis) {
                        if (n.startsWith("lib/" + abi + "/")) {
                            wanted = true;
                            break;
                        }
                    }
                    if (!wanted) continue;
                    String base = n.substring(n.lastIndexOf('/') + 1);
                    File out = new File(outDir, base);
                    if (out.exists() && out.length() == e.getSize()) {
                        count++;
                        continue;
                    }
                    java.io.InputStream in = null;
                    java.io.FileOutputStream fos = null;
                    try {
                        in = zip.getInputStream(e);
                        fos = new java.io.FileOutputStream(out);
                        byte[] buf = new byte[65536];
                        int r;
                        while ((r = in.read(buf)) > 0) fos.write(buf, 0, r);
                        fos.getFD().sync();
                    } finally {
                        if (fos != null) {
                            try {
                                fos.close();
                            } catch (Throwable ignored) {
                            }
                        }
                        if (in != null) {
                            try {
                                in.close();
                            } catch (Throwable ignored) {
                            }
                        }
                    }
                    out.setReadable(true, false);
                    out.setExecutable(true, false);
                    count++;
                }
                if (count > 0) {
                    Log.d(TAG, "Extracted " + count + " native lib(s) for " + apkPath);
                    return outDir.getAbsolutePath();
                }
            } finally {
                try {
                    zip.close();
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "Native lib extract failed for " + apkPath, t);
        }
        return null;
    }

    /** Real process name; falls back to the package when unavailable. */
    static String currentProcessName(String fallback) {
        try {
            String proc = android.app.Application.getProcessName();
            if (proc != null && !proc.isEmpty()) return proc;
        } catch (Throwable ignored) {
        }
        return fallback;
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

    /** Phase 1 (pre-Application): scan, load, onModuleLoaded + onPackageLoaded. */
    public static void dispatchPackageEarly(Context context, ClassLoader appClassLoader,
            String packageName) {
        if (!ApkModulesGate.isEnabled(context)) return; // TEMPORARY kill-switch
        if (SYSTEM_PACKAGE.equals(packageName)) return;
        ensureScanned(context);
        String processName = currentProcessName(packageName);
        android.content.pm.ApplicationInfo ai = null;
        try {
            ai = context.getPackageManager().getApplicationInfo(packageName, 0);
        } catch (Throwable ignored) {
        }
        for (ModuleRecord rec : modules.values()) {
            if (!isModuleEnabled(context, rec)) continue;
            // Self-targeting: enabled module always sees its own package
            // (Vector parity — otherwise "activated" status never shows).
            boolean self = false;
            String ownPkg = null;
            try {
                ownPkg = getOwnPackage(context, rec.apkPath);
                self = packageName.equals(ownPkg);
            } catch (Throwable ignored) {
            }
            if (!self && !targetsPackage(getEffectiveScopes(context, rec), packageName)) {
                continue;
            }
            if (self) {
                // Own process: mirror the module's private prefs into the
                // shared store so targets (and status checks) see them.
                // Runs even when instantiation below fails.
                try {
                    PrefsMirror.syncIfSelf(context, packageName, ownPkg);
                } catch (Throwable ignored) {
                }
            }
            ensureLoaded(context, rec, packageName, processName, appClassLoader);
            if (rec.instances.isEmpty()) continue;
            XposedModuleInterface.PackageLoadedParam lp =
                    new PkgLoadedParamImpl(packageName, appClassLoader, true);
            for (XposedModuleInterface inst : rec.instances) {
                try {
                    inst.onPackageLoaded(lp);
                } catch (Throwable t) {
                    Log.e(TAG, "onPackageLoaded failed for " + rec.id, t);
                }
            }
            // onPackageReady fires here too (same early dispatch), NOT in
            // phase 2: Vector/LSPosed parity. Modules that defeat
            // provider-attach guards (e.g. bank patchers) must have their
            // hooks installed BEFORE installContentProviders — a post-
            // provider onPackageReady would arrive after the app is dead.
            XposedModuleInterface.PackageReadyParam rp =
                    new PkgReadyParamImpl(packageName, appClassLoader, true, ai);
            for (XposedModuleInterface inst : rec.instances) {
                try {
                    inst.onPackageReady(rp);
                } catch (Throwable t) {
                    Log.e(TAG, "onPackageReady failed for " + rec.id, t);
                }
            }
        }
    }

    /**
     * Phase 2 (post-Application, pre-onCreate): module service bind.
     *
     * <p>Modern modules declare {@code XposedProvider} in their manifest.
     * On LSPosed, system_server calls its {@code call("SendBinder", ...)}
     * to hand the module an {@code IXposedService} Binder, which fires the
     * module's {@code OnServiceListener} (the "module activated" status).
     * Our host has no system_server side, so the self-process replays this
     * handshake locally in phase 2 (providers are installed by now).
     * No-op for non-self packages.
     */
    public static void dispatchPackageLate(Context context, ClassLoader appClassLoader,
            String packageName) {
        if (!ApkModulesGate.isEnabled(context)) return;
        if (SYSTEM_PACKAGE.equals(packageName)) return;
        ensureScanned(context);
        for (ModuleRecord rec : modules.values()) {
            if (!isModuleEnabled(context, rec)) continue;
            String own = null;
            try {
                own = getOwnPackage(context, rec.apkPath);
            } catch (Throwable ignored) {
            }
            if (own == null || !packageName.equals(own)) continue;
            try {
                notifyModuleServiceBind(context, appClassLoader, own);
            } catch (Throwable t) {
                Log.w(TAG, "notifyModuleServiceBind failed for " + rec.id, t);
            }
        }
    }

    /**
     * Replays LSPosed's system_server → XposedProvider "SendBinder"
     * handshake inside the module's own process: builds a local
     * {@code IXposedService} proxy and delivers it via
     * {@code ContentResolver.call("content://<pkg>.XposedService",
     * "SendBinder", ...)}. The provider then fires the module's
     * {@code OnServiceListener} ("module activated").
     */
    private static void notifyModuleServiceBind(Context context, ClassLoader appCl,
            String ownPkg) {
        final String descriptor = "io.github.libxposed.service.IXposedService";
        Class<?> iFace;
        try {
            // In the self process the app loader sees the module's own APK,
            // so this is the same Class object the provider uses.
            iFace = Class.forName(descriptor, false, appCl);
        } catch (Throwable t) {
            Log.d(TAG, "No IXposedService in " + ownPkg + ", skipping bind");
            return;
        }
        final Class<?> iface = iFace;
        Object proxy;
        try {
            proxy = java.lang.reflect.Proxy.newProxyInstance(
                    appCl, new Class<?>[]{iface},
                    new java.lang.reflect.InvocationHandler() {
                        @Override
                        public Object invoke(Object p, java.lang.reflect.Method method,
                                Object[] args) {
                            String m = method.getName();
                            if ("asBinder".equals(m)) return null;
                            if ("getAPIVersion".equals(m)) return 102;
                            if ("getFrameworkName".equals(m)) return "PixelParts";
                            if ("getFrameworkVersion".equals(m)) return "1.0";
                            if ("getFrameworkVersionCode".equals(m)) return 102L;
                            if ("getFrameworkPrivilege".equals(m)) return 0;
                            if ("getScope".equals(m)) return new java.util.ArrayList<String>();
                            Class<?> rt = method.getReturnType();
                            if (rt == boolean.class) return false;
                            if (rt == int.class) return 0;
                            if (rt == long.class) return 0L;
                            return null;
                        }
                    });
        } catch (Throwable t) {
            Log.w(TAG, "IXposedService proxy failed for " + ownPkg, t);
            return;
        }
        final Object binderProxy = proxy;
        android.os.Binder binder = new android.os.Binder() {
            @Override
            public android.os.IInterface queryLocalInterface(String desc) {
                if (descriptor.equals(desc)
                        && binderProxy instanceof android.os.IInterface) {
                    return (android.os.IInterface) binderProxy;
                }
                return null;
            }
        };
        try {
            android.os.Bundle extras = new android.os.Bundle();
            extras.putBinder("binder", binder);
            // NOTE: method name is "SendBinder" (capital S) — verified
            // against the module-bundled XposedProvider.call bytecode.
            android.os.Bundle res = context.getContentResolver().call(
                    android.net.Uri.parse("content://" + ownPkg + ".XposedService"),
                    "SendBinder", null, extras);
            Log.d(TAG, "sendBinder delivered for " + ownPkg + " -> " + res);
        } catch (Throwable t) {
            Log.w(TAG, "sendBinder call failed for " + ownPkg, t);
        }
    }

    /** Called from HookEntry for every injected app process. */
    public static void dispatchPackage(Context context, ClassLoader appClassLoader,
            String packageName) {
        dispatchPackageEarly(context, appClassLoader, packageName);
        dispatchPackageLate(context, appClassLoader, packageName);
    }

    /** Called once in system_server when a system-scope module is installed. */
    public static void dispatchSystem(Context context, ClassLoader ssClassLoader) {
        if (!ApkModulesGate.isEnabled(context)) return; // TEMPORARY kill-switch
        ensureScanned(context);
        for (ModuleRecord rec : modules.values()) {
            if (!isModuleEnabled(context, rec)) continue;
            if (!rec.scopes.contains("system")) continue;
            ensureLoaded(context, rec, SYSTEM_PACKAGE,
                    currentProcessName(SYSTEM_PACKAGE), ssClassLoader);
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
