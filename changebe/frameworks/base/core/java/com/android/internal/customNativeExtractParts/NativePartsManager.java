package com.android.internal.customNativeExtractParts;

import android.app.Application;
import android.content.Context;
import android.graphics.Canvas;
import android.provider.Settings;
import android.util.Log;
import android.widget.EdgeEffect;

/**
 * Главный хаб (Facade).
 * Управляет инициализацией нативных хуков.
 * Путь: frameworks/base/core/java/com/android/internal/customNativeExtractParts/NativePartsManager.java
 */
public class NativePartsManager {

    private static final String TAG = "CustomNativeParts";
    private static final String PKG_LAUNCHER = "com.google.android.apps.nexuslauncher";
    private static final String KEY_NATIVE_HOOK_DISABLE = "native_hook_disable";

    public static void init(Application app) {
        String pkg = app.getPackageName();
        if (PKG_LAUNCHER.equals(pkg)) {
            if (isNativeHookDisabled(app)) {
                Log.w(TAG, "Native hooks for Launcher are DISABLED via Settings (" + KEY_NATIVE_HOOK_DISABLE + "=1)");
                return;
            }

            Log.i(TAG, "Init: Launcher Module");
            LauncherHooks.init(app);
        }
    }

    private static boolean isNativeHookDisabled(Context context) {
        try {
            return Settings.Secure.getInt(context.getContentResolver(), KEY_NATIVE_HOOK_DISABLE, 0) == 1;
        } catch (Exception e) {
            Log.e(TAG, "Failed to check native_hook_disable", e);
            return false;
        }
    }
}