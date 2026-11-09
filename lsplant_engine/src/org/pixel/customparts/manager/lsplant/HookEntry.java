package org.pixel.customparts.manager.lsplant;

import android.content.Context;
import org.pixel.customparts.core.ApkModulesGate;
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
        // (see patches/files); this entry point only runs addon JARs
        // and modern Xposed modules (APK).
        if (hasAddons) {
            try {
                AddonLoader.loadAndRunAddons(context, classLoader, packageName);
            } catch (Throwable t) {
                environment.logError(TAG, "Addon loading failed for " + packageName, t);
            }
        }
        // TEMPORARY: APK-module (Xposed/LSPosed/Vector) support disabled
        // via ApkModulesGate — dispatches bypassed, code kept.
        if (!ApkModulesGate.ENABLED) {
            return;
        }
        if (XposedModuleHost.SYSTEM_PACKAGE.equals(packageName)) {
            // system_server: only modern modules with system scope.
            // (Requires ActivityThread to inject the "android" package;
            // see framework patch — no globals here, ever.)
            try {
                XposedModuleHost.dispatchSystem(context, classLoader);
            } catch (Throwable t) {
                environment.logError(TAG, "Module system dispatch failed", t);
            }
            return;
        }
        try {
            XposedModuleHost.dispatchPackage(context, classLoader, packageName);
        } catch (Throwable t) {
            environment.logError(TAG, "Module dispatch failed for " + packageName, t);
        }
        try {
            LegacyModuleHost.dispatchPackage(context, classLoader, packageName);
        } catch (Throwable t) {
            environment.logError(TAG, "Legacy dispatch failed for " + packageName, t);
        }
    }

}