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

    public XposedModule() {
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
        };
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
