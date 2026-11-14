package io.github.libxposed.api;

import java.lang.reflect.Executable;
import java.lang.reflect.Member;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import org.pixel.customparts.manager.lsplant.LsplantBridge;

/**
 * Modern module base (API 101), empty default lifecycle.
 * Hooking is routed into our LSPlant backend via the legacy bridge.
 */
public abstract class XposedModule implements XposedModuleInterface {

    private android.content.Context hostContext;
    private String hostModulePackage;

    public XposedModule() {
    }

    /** Called by the host before any callback. */
    public void attachHost(android.content.Context context, String modulePackage) {
        android.content.Context appCtx = null;
        try {
            if (context != null) appCtx = context.getApplicationContext();
        } catch (Throwable ignored) {
        }
        // In phase 1 (pre-Application) getApplicationContext() returns null
        // instead of throwing — keep the valid ContextImpl, never null it.
        this.hostContext = appCtx != null ? appCtx : context;
        this.hostModulePackage = modulePackage;
    }

    @Override
    public void onModuleLoaded(XposedModuleInterface.ModuleLoadedParam param) throws Throwable {
    }

    @Override
    public void onPackageLoaded(XposedModuleInterface.PackageLoadedParam param) throws Throwable {
    }

    @Override
    public void onPackageReady(XposedModuleInterface.PackageReadyParam param) throws Throwable {
    }

    @Override
    public void onSystemServerStarting(XposedModuleInterface.SystemServerStartingParam param)
            throws Throwable {
    }

    public XposedInterface.HookBuilder hook(Executable executable) {
        final Member target = (Member) executable;
        return new XposedInterface.HookBuilder() {
            private volatile ExceptionMode mode = ExceptionMode.PASSTHROUGH;

            @Override
            public XposedInterface.HookHandle intercept(final XposedInterface.Hooker hooker) {
                final XC_MethodHook cb = new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        ChainImpl chain = new ChainImpl(target, param.thisObject,
                                param.args == null ? new Object[0] : param.args.clone());
                        // Modern semantics: the intercept return value IS the
                        // result (around-style). chain.proceed() runs the
                        // original and returns its value for observation;
                        // exceptions (from either) propagate to the caller.
                        Object result;
                        try {
                            result = hooker.intercept(chain);
                        } catch (Throwable t) {
                            param.setThrowable(t);
                            return;
                        }
                        param.setResult(result);
                    }
                };
                XposedBridge.hookMethod(target, cb);
                return new XposedInterface.HookHandle() {
                    @Override
                    public void unhook() {
                        XposedBridge.unhookMethod(target, cb);
                    }
                };
            }

            @Override
            public XposedInterface.HookBuilder setExceptionMode(ExceptionMode m) {
                if (m != null) mode = m; // passthrough is native behavior
                return this;
            }
        };
    }

    /**
     * Remote preferences of the module app, readable from a target process.
     * Backed by {@link org.pixel.customparts.manager.lsplant.RemotePrefs}
     * (shared files under /data/pixelparts/rprefs): the module's own
     * shared_prefs dir is unreadable from a target UID (SELinux), so a
     * plain cross-package read would silently return defaults there.
     * Both the module UI process and target processes see the same values.
     * Never returns null.
     */
    public android.content.SharedPreferences getRemotePreferences(String name) {
        try {
            if (hostModulePackage != null) {
                return org.pixel.customparts.manager.lsplant.RemotePrefs
                        .forModule(hostContext, hostModulePackage, name);
            }
        } catch (Throwable ignored) {
        }
        if (hostContext != null) {
            try {
                return org.pixel.customparts.manager.lsplant.RemotePrefs
                        .forModule(hostContext, hostContext.getPackageName(), name);
            } catch (Throwable ignored) {
            }
        }
        return emptyPreferences();
    }

    /** Empty read-only SharedPreferences stub (last-resort fallback). */
    static android.content.SharedPreferences emptyPreferences() {
        final android.content.SharedPreferences.Editor editProxy =
                (android.content.SharedPreferences.Editor) java.lang.reflect.Proxy
                        .newProxyInstance(XposedModule.class.getClassLoader(),
                                new Class<?>[] {
                                        android.content.SharedPreferences.Editor.class },
                                new java.lang.reflect.InvocationHandler() {
                                    @Override
                                    public Object invoke(Object proxy,
                                            java.lang.reflect.Method method,
                                            Object[] args) {
                                        Class<?> rt = method.getReturnType();
                                        if (rt == boolean.class) return true;
                                        if (rt == void.class) return null;
                                        return proxy;
                                    }
                                });
        return (android.content.SharedPreferences) java.lang.reflect.Proxy.newProxyInstance(
                XposedModule.class.getClassLoader(),
                new Class<?>[] { android.content.SharedPreferences.class },
                new java.lang.reflect.InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, java.lang.reflect.Method method,
                            Object[] args) {
                        String n = method.getName();
                        Class<?> rt = method.getReturnType();
                        if ("contains".equals(n)) return false;
                        if ("edit".equals(n)) return editProxy;
                        if ("getAll".equals(n))
                            return java.util.Collections.emptyMap();
                        if ("getStringSet".equals(n)) {
                            return (args != null && args.length > 1) ? args[1] : null;
                        }
                        if (args != null && args.length > 1) return args[1];
                        if (rt == boolean.class) return false;
                        if (rt == int.class) return 0;
                        if (rt == long.class) return 0L;
                        if (rt == float.class) return 0f;
                        return null;
                    }
                });
    }

    public void log(int priority, String tag, String message) {
        android.util.Log.println(priority, tag, message);
    }

    public void log(int priority, String tag, String message, Throwable throwable) {
        android.util.Log.println(priority, tag,
                message + "\n" + android.util.Log.getStackTraceString(throwable));
    }

    /** Adapts modern {@link XposedInterface.Chain} to a backup call. */
    static final class ChainImpl implements XposedInterface.Chain {
        final Member target;
        final Object thisObject;
        final Object[] args;
        boolean proceeded = false;
        Object proceedResult = null;

        ChainImpl(Member target, Object thisObject, Object[] args) {
            this.target = target;
            this.thisObject = thisObject;
            this.args = args;
        }

        @Override
        public Object getThisObject() {
            return thisObject;
        }

        @Override
        public java.util.List<Object> getArgs() {
            return java.util.Arrays.asList(args);
        }

        @Override
        public Object getArg(int index) {
            return args[index];
        }

        @Override
        public Object proceed() throws Throwable {
            return proceed(args);
        }

        @Override
        public Object proceed(Object[] callArgs) throws Throwable {
            proceeded = true;
            proceedResult = LsplantBridge.invokeBackup(target, thisObject, callArgs);
            return proceedResult;
        }
    }
}
