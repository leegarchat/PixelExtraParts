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
     * <p>HOST NOTE: {@code isFirstPackage} is currently always {@code true}
     * (multi-package processes are not distinguished), and this callback
     * fires in {@code handleBindApplication} <em>before</em>
     * {@code Application.onCreate} — not when the app is "ready", unlike
     * stock LSPosed where ready means ready. Modules must not assume an
     * initialized Application here.
     */
    interface PackageReadyParam {
        String getPackageName();

        ClassLoader getClassLoader();

        boolean isFirstPackage();
    }

    /** Params for {@link #onSystemServerStarting}. */
    interface SystemServerStartingParam {
        ClassLoader getClassLoader();
    }
}
