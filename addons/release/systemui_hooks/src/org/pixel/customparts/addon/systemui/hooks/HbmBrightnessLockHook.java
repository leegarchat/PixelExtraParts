package org.pixel.customparts.addon.systemui.hooks;

import android.content.Context;
import android.provider.Settings;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collections;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * HBM brightness-lock hook (HOOK replacement for the SystemUI source patch).
 *
 * Wraps ScreenBrightnessDisplayManagerRepository.isBrightnessOverriddenByWindow
 * in a live StateFlow proxy: original value OR the PixelParts HBM lock
 * (Settings.Global pixelparts_auto_hbm_active + pixelparts_auto_hbm_brightness_lock).
 * The proxy delegates everything to the original flow except getValue(),
 * which is re-evaluated on every read — no frozen state, no restart needed.
 */
public class HbmBrightnessLockHook extends BaseSystemUIHook {
    private static final String PEP_HBM_ACTIVE = "pixelparts_auto_hbm_active";
    private static final String PEP_HBM_BRIGHTNESS_LOCK = "pixelparts_auto_hbm_brightness_lock";

    private volatile Boolean sLockCache;

    @Override
    public String getHookId() {
        return "HbmBrightnessLockHook";
    }

    @Override
    public int getPriority() {
        return 50;
    }

    @Override
    protected void onInit(ClassLoader classLoader) {
        try {
            Class<?> repoClass = XposedHelpers.findClass(
                    "com.android.systemui.brightness.data.repository.ScreenBrightnessDisplayManagerRepository",
                    classLoader);
            XposedBridge.hookAllMethods(repoClass, "getIsBrightnessOverriddenByWindow",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            try {
                                Object orig = param.getResult();
                                if (orig == null) {
                                    return;
                                }
                                Object proxy = wrapFlow(param, orig);
                                if (proxy != null) {
                                    param.setResult(proxy);
                                }
                            } catch (Throwable t) {
                                logError("HBM lock wrap failed", t);
                            }
                        }
                    });
            log("isBrightnessOverriddenByWindow hooked");
        } catch (Throwable t) {
            logError("Unable to hook brightness repository", t);
        }
    }

    private Object wrapFlow(final XC_MethodHook.MethodHookParam param, final Object orig) {
        Class<?> stateFlowIface = null;
        for (Class<?> iface : orig.getClass().getInterfaces()) {
            if ("kotlinx.coroutines.flow.StateFlow".equals(iface.getName())) {
                stateFlowIface = iface;
                break;
            }
        }
        if (stateFlowIface == null) {
            for (Class<?> iface : orig.getClass().getInterfaces()) {
                if ("kotlinx.coroutines.flow.MutableStateFlow".equals(iface.getName())) {
                    stateFlowIface = iface;
                    break;
                }
            }
        }
        if (stateFlowIface == null) {
            return null;
        }
        final Method getValue;
        try {
            getValue = stateFlowIface.getMethod("getValue");
        } catch (Throwable t) {
            return null;
        }
        final Context[] ctxHolder = new Context[1];
        try {
            Object ctx = XposedHelpers.getObjectField(param.thisObject, "context");
            if (ctx instanceof Context) {
                ctxHolder[0] = (Context) ctx;
                observeExactSettingOnce((Context) ctx, PEP_HBM_ACTIVE, new Runnable() {
                    @Override
                    public void run() {
                        sLockCache = null;
                    }
                });
                observeExactSettingOnce((Context) ctx, PEP_HBM_BRIGHTNESS_LOCK, new Runnable() {
                    @Override
                    public void run() {
                        sLockCache = null;
                    }
                });
            }
        } catch (Throwable ignored) {
        }
        final ClassLoader hostLoader = orig.getClass().getClassLoader();
        return Proxy.newProxyInstance(hostLoader, new Class<?>[]{stateFlowIface},
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) {
                        try {
                            if ("getValue".equals(method.getName())
                                    && (args == null || args.length == 0)) {
                                Object origValue = getValue.invoke(orig);
                                if (Boolean.TRUE.equals(origValue)) {
                                    return Boolean.TRUE;
                                }
                                return isHbmLockActive(ctxHolder[0])
                                        ? Boolean.TRUE : Boolean.FALSE;
                            }
                            if ("getReplayCache".equals(method.getName())
                                    && (args == null || args.length == 0)) {
                                Object current = getValue.invoke(orig);
                                if (!Boolean.TRUE.equals(current)
                                        && isHbmLockActive(ctxHolder[0])) {
                                    current = Boolean.TRUE;
                                }
                                return Collections.singletonList(current);
                            }
                            return method.invoke(orig, args);
                        } catch (Throwable t) {
                            try {
                                return method.invoke(orig, args);
                            } catch (Throwable ignored) {
                                return null;
                            }
                        }
                    }
                });
    }

    private boolean isHbmLockActive(Context context) {
        Boolean cached = sLockCache;
        if (cached != null) {
            return cached.booleanValue();
        }
        boolean active = false;
        if (context != null) {
            try {
                active = Settings.Global.getInt(
                                context.getContentResolver(), PEP_HBM_ACTIVE, 0) != 0
                        && Settings.Global.getInt(
                                context.getContentResolver(), PEP_HBM_BRIGHTNESS_LOCK, 0) != 0;
            } catch (Throwable ignored) {
            }
        }
        sLockCache = active;
        return active;
    }
}
