package com.android.internal.customNativeExtractParts;

import android.app.Activity;
import android.content.Context;
import android.provider.Settings;
import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class LauncherFeedDisabler {

    private static final String TAG = "LauncherFeedDisabler";
    private static final String KEY_DISABLE_FEED = "launcher_disable_google_feed";

    private static boolean sSettingsLoaded = false;
    private static boolean sIsFeedDisabled = false;

    private static boolean sReflectionInitialized = false;
    private static Field sWorkspaceField;
    private static Method sSetOverlayMethod;

    public static void checkAndDisableFeed(final Activity launcherActivity) {
        if (launcherActivity == null) return;

        try {
            if (!sSettingsLoaded) {
                loadSettings(launcherActivity);
            }

            if (sIsFeedDisabled) {
                forceDisconnectOverlay(launcherActivity);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to disable feed", e);
        }
    }

    private static void loadSettings(Context context) {
        try {
            sIsFeedDisabled = Settings.Secure.getInt(context.getContentResolver(), KEY_DISABLE_FEED, 0) == 1;
            sSettingsLoaded = true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to load settings", e);
        }
    }

    private static void forceDisconnectOverlay(Activity launcherActivity) {
        try {
            if (!sReflectionInitialized) {
                initReflection(launcherActivity);
            }

            if (sWorkspaceField == null) return;
            
            Object workspace = sWorkspaceField.get(launcherActivity);
            if (workspace == null) return;

            if (sSetOverlayMethod != null) {
                sSetOverlayMethod.invoke(workspace, (Object) null);
            } else {
                if (resolveMethod(workspace)) {
                    sSetOverlayMethod.invoke(workspace, (Object) null);
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "Failed to disconnect overlay", e);
        }
    }

    private static void initReflection(Activity launcherActivity) {
        try {
            sWorkspaceField = findField(launcherActivity.getClass(), "mWorkspace");
            if (sWorkspaceField != null) {
                sWorkspaceField.setAccessible(true);
                Object workspace = sWorkspaceField.get(launcherActivity);
                if (workspace != null) {
                    resolveMethod(workspace);
                }
            }
            sReflectionInitialized = true;
        } catch (Exception e) {
            Log.e(TAG, "Reflection init failed", e);
        }
    }

    private static boolean resolveMethod(Object workspace) {
        try {
            sSetOverlayMethod = findMethodByNameAndParamCount(workspace.getClass(), "setLauncherOverlay", 1);
            if (sSetOverlayMethod != null) {
                sSetOverlayMethod.setAccessible(true);
                return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    private static Field findField(Class<?> clazz, String fieldName) {
        Class<?> current = clazz;
        while (current != null) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    private static Method findMethodByNameAndParamCount(Class<?> clazz, String methodName, int paramCount) {
        Class<?> current = clazz;
        while (current != null) {
            try {
                for (Method method : current.getDeclaredMethods()) {
                    if (method.getName().equals(methodName) && method.getParameterTypes().length == paramCount) {
                        return method;
                    }
                }
            } catch (Exception e) {
                // Continue searching in superclass
            }
            current = current.getSuperclass();
        }
        return null;
    }
}