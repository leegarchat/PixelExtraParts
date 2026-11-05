package org.pixel.customparts.manager.lsplant;

import android.content.Context;
import org.pixel.customparts.core.IHookEnvironment;

import java.util.HashSet;
import java.util.Set;

public class HookEntry {
    private static final String TAG = "HookEntry";

    private static final Set<String> initializedPackages = new HashSet<>();
    private static final IHookEnvironment environment = new LsplantEnvironment();

    public static void init(Context context, ClassLoader classLoader, String packageName) {
        if (initializedPackages.contains(packageName)) {
            return;
        }
        initializedPackages.add(packageName);

        boolean hasAddons = false;

        try {
            hasAddons = AddonLoader.hasAddonsForPackage(context, packageName);
        } catch (Throwable t) {
            environment.logError(TAG, "Failed to check addons for " + packageName, t);
        }

        // Built-in hooks now live as native framework patches
        // (see patches/files); this entry point only runs addon JARs.
        if (hasAddons) {
            try {
                AddonLoader.loadAndRunAddons(context, classLoader, packageName);
            } catch (Throwable t) {
                environment.logError(TAG, "Addon loading failed for " + packageName, t);
            }
        }
    }

}