package org.pixel.customparts.addon.launcher.hooks;

import android.content.Context;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class LauncherHookRegistry {
    private static final String TAG = "LauncherHookRegistry";
    public static final String PACKAGE_NEXUS_LAUNCHER = "com.google.android.apps.nexuslauncher";

    private LauncherHookRegistry() {
    }

    public static void initAll(Context context, ClassLoader classLoader, String packageName) {
        if (!PACKAGE_NEXUS_LAUNCHER.equals(packageName)) {
            Log.d(TAG, "Skipping non-Nexus launcher package: " + packageName);
            return;
        }

        List<BaseLauncherHook> hooks = new ArrayList<>();
        hooks.add(new LauncherIconOverrideHookAddon());
        hooks.add(new GridSizeAppMenuHookAddon());
        hooks.add(new GestureBarHookAddon());
        hooks.add(new UnifiedLauncherHookAddon());
        hooks.add(new RecentsUnifiedHookAddon());

        Collections.sort(hooks, new Comparator<BaseLauncherHook>() {
            @Override
            public int compare(BaseLauncherHook left, BaseLauncherHook right) {
                return Integer.compare(right.getPriority(), left.getPriority());
            }
        });

        int applied = 0;
        for (BaseLauncherHook hook : hooks) {
            try {
                if (!hook.isEnabled(context)) {
                    Log.d(TAG, hook.getHookId() + " skipped: disabled");
                    continue;
                }
                hook.init(classLoader);
                applied++;
                Log.d(TAG, hook.getHookId() + " applied");
            } catch (Throwable throwable) {
                Log.e(TAG, "Failed to init " + hook.getHookId(), throwable);
            }
        }
        Log.d(TAG, "Nexus launcher addon hooks active: " + applied);
    }
}