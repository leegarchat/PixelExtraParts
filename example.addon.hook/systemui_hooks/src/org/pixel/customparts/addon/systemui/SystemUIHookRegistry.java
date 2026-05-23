package org.pixel.customparts.addon.systemui;

import android.content.Context;
import android.util.Log;

import org.pixel.customparts.addon.systemui.hooks.AodNotificationIconColorHook;
import org.pixel.customparts.addon.systemui.hooks.BaseSystemUIHook;
import org.pixel.customparts.addon.systemui.hooks.DozeTapDozeHook;
import org.pixel.customparts.addon.systemui.hooks.DozeTapShadeHook;
import org.pixel.customparts.addon.systemui.hooks.KeyguardBatteryPowerHook;
import org.pixel.customparts.addon.systemui.hooks.NotificationIconShapeHook;
import org.pixel.customparts.addon.systemui.hooks.ShadeCompactMediaHook;
import org.pixel.customparts.addon.systemui.hooks.ShadeDateCalendarHook;
import org.pixel.customparts.addon.systemui.hooks.ShadeUnifiedSurfaceHook;
import org.pixel.customparts.addon.systemui.hooks.SystemUIRestartHook;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class SystemUIHookRegistry {
    private static final String TAG = "SystemUIHookRegistry";
    public static final String PACKAGE_SYSTEMUI = "com.android.systemui";

    private SystemUIHookRegistry() {
    }

    public static void initAll(Context context, ClassLoader hostClassLoader, String packageName) {
        if (!PACKAGE_SYSTEMUI.equals(packageName)) {
            Log.d(TAG, "Skipping non-SystemUI package: " + packageName);
            return;
        }

        List<BaseSystemUIHook> hooks = new ArrayList<>();
        hooks.add(new DozeTapDozeHook());
        hooks.add(new DozeTapShadeHook());
        hooks.add(new KeyguardBatteryPowerHook());
        hooks.add(new ShadeDateCalendarHook());
        hooks.add(new ShadeUnifiedSurfaceHook());
        hooks.add(new ShadeCompactMediaHook());
        hooks.add(new NotificationIconShapeHook());
        hooks.add(new AodNotificationIconColorHook());
        hooks.add(new SystemUIRestartHook());

        Collections.sort(hooks, new Comparator<BaseSystemUIHook>() {
            @Override
            public int compare(BaseSystemUIHook left, BaseSystemUIHook right) {
                return Integer.compare(right.getPriority(), left.getPriority());
            }
        });

        int applied = 0;
        for (BaseSystemUIHook hook : hooks) {
            try {
                if (!hook.isEnabled(context)) {
                    Log.d(TAG, hook.getHookId() + " skipped: disabled");
                    continue;
                }
                hook.init(hostClassLoader);
                applied++;
                Log.d(TAG, hook.getHookId() + " applied");
            } catch (Throwable throwable) {
                Log.e(TAG, "Failed to init " + hook.getHookId(), throwable);
            }
        }
        Log.d(TAG, "SystemUI addon hooks active: " + applied);
    }
}