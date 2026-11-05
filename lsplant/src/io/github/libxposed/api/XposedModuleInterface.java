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
    class ModuleLoadedParam {
        private final ClassLoader classLoader;

        public ModuleLoadedParam(ClassLoader classLoader) {
            this.classLoader = classLoader;
        }

        public ClassLoader getClassLoader() {
            return classLoader;
        }
    }

    /** Params for {@link #onPackageLoaded}. */
    class PackageLoadedParam {
        private final String packageName;
        private final ClassLoader classLoader;
        private final boolean firstPackage;

        public PackageLoadedParam(String packageName, ClassLoader classLoader,
                boolean firstPackage) {
            this.packageName = packageName;
            this.classLoader = classLoader;
            this.firstPackage = firstPackage;
        }

        public String getPackageName() {
            return packageName;
        }

        public ClassLoader getClassLoader() {
            return classLoader;
        }

        public boolean isFirstPackage() {
            return firstPackage;
        }
    }

    /** Params for {@link #onPackageReady}. */
    class PackageReadyParam {
        private final String packageName;
        private final ClassLoader classLoader;
        private final boolean firstPackage;

        public PackageReadyParam(String packageName, ClassLoader classLoader,
                boolean firstPackage) {
            this.packageName = packageName;
            this.classLoader = classLoader;
            this.firstPackage = firstPackage;
        }

        public String getPackageName() {
            return packageName;
        }

        public ClassLoader getClassLoader() {
            return classLoader;
        }

        public boolean isFirstPackage() {
            return firstPackage;
        }
    }

    /** Params for {@link #onSystemServerStarting}. */
    class SystemServerStartingParam {
        private final ClassLoader classLoader;

        public SystemServerStartingParam(ClassLoader classLoader) {
            this.classLoader = classLoader;
        }

        public ClassLoader getClassLoader() {
            return classLoader;
        }
    }
}
