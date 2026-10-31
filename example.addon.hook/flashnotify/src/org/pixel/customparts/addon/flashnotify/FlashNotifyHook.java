package org.pixel.customparts.addon.flashnotify;

import android.content.Context;

import org.pixel.customparts.core.IAddonHook;

import java.util.Collections;
import java.util.Set;

import org.pixel.customparts.addon.flashnotify.hooks.FlashBlinkHook;

/**
 * Entry point: custom camera-flash blink on notifications.
 * Runs inside com.android.systemui (system_server is never hooked).
 */
public final class FlashNotifyHook implements IAddonHook {
    private static final String TARGET = "com.android.systemui";

    @Override public String getId() { return "flashnotify"; }
    @Override public String getName() { return "Flash Notify"; }
    @Override public String getAuthor() { return "PixelExtraParts"; }
    @Override public String getDescription() { return "Custom flash count and blink time on notifications."; }
    @Override public String getVersion() { return "1.0.0"; }
    @Override public Set<String> getTargetPackages() { return Collections.singleton(TARGET); }
    @Override public int getPriority() { return 100; }

    @Override
    public void handleLoadPackage(Context context, ClassLoader classLoader, String packageName) {
        if (!TARGET.equals(packageName)) return;
        Context app = context != null && context.getApplicationContext() != null
                ? context.getApplicationContext() : context;
        FlashBlinkHook.install(app, classLoader);
    }
}
