package org.pixel.customparts.addon.yandexmaps.hooks;

import android.content.Context;
import android.util.Log;

import org.pixel.customparts.addon.yandexmaps.Settings;

import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

/**
 * Forces the Vulkan render path on GPUs blocklisted by the native checker
 * (e.g. Mali-G715). PlatformViewFactory falls back to GL automatically if
 * Vulkan view creation throws, so forcing is safe to try.
 */
public final class VulkanHook {
    private static final String TAG = "PixelPartsYandexFast";
    private static final String VULKAN_LAUNCHER = "com.yandex.runtime.vulkan_launcher.VulkanLauncher";

    private VulkanHook() {}

    public static void install(final Context context, final ClassLoader cl) {
        try {
            final Class<?> vk = Class.forName(VULKAN_LAUNCHER, false, cl);
            for (final Method m : vk.getDeclaredMethods()) {
                if (!"isVulkanEnabled".equals(m.getName())) continue;
                m.setAccessible(true);
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam param) {
                        if (Settings.get(context, Settings.KEY_VULKAN, true)) {
                            param.setResult(Boolean.TRUE);
                            Log.i(TAG, "forced Vulkan enabled (was native decision)");
                        }
                    }
                });
                Log.i(TAG, "hooked vulkan " + m);
                return;
            }
            Log.w(TAG, VULKAN_LAUNCHER + " has no isVulkanEnabled()");
        } catch (ClassNotFoundException e) {
            Log.w(TAG, VULKAN_LAUNCHER + " not in this process, skip");
        } catch (Throwable t) {
            Log.e(TAG, "vulkan hook failed", t);
        }
    }
}
