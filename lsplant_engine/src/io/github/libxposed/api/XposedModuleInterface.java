package io.github.libxposed.api;

/**
 * Modern module lifecycle (API 101). Implemented by modules, driven
 * by our host ({@code XposedModuleHost} in manager.lsplant).
 */
public interface XposedModuleInterface {

    void onModuleLoaded(ModuleLoadedParam param) throws Throwable;

    void onPackageLoaded(PackageLoadedParam param) throws Throwable;

    void onPackageReady(PackageReadyParam param) throws Throwable;

    void onSystemServerStarting(SystemServerStartingParam param) throws Throwable;

    /** Params for {@link #onModuleLoaded}. */
    interface ModuleLoadedParam {
        ClassLoader getClassLoader();

        String getProcessName();

        /** True when running inside system_server (package "android"). */
        boolean isSystemServer();
    }

    /** Params for {@link #onPackageLoaded}. */
    interface PackageLoadedParam {
        String getPackageName();

        ClassLoader getClassLoader();

        boolean isFirstPackage();
    }

    /**
     * Params for {@link #onPackageReady}.
     *
     * <p>HOST NOTE: fires in the phase-1 (pre-Application) dispatch,
     * alongside {@code onPackageLoaded} — Vector/LSPosed parity, so that
     * modules defeating provider-attach guards have their hooks installed
     * before {@code installContentProviders}. Modules must not assume an
     * initialized Application here.
     */
    interface PackageReadyParam {
        String getPackageName();

        ClassLoader getClassLoader();

        boolean isFirstPackage();

        android.content.pm.ApplicationInfo getApplicationInfo();
    }

    /** Params for {@link #onSystemServerStarting}. */
    interface SystemServerStartingParam {
        ClassLoader getClassLoader();
    }
}
