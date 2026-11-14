package org.pixel.customparts.manager.lsplant;

import android.content.Context;
import android.util.Log;
import org.pixel.customparts.core.ApkModulesGate;
import org.pixel.customparts.core.IHookEnvironment;

import java.util.HashSet;
import java.util.Set;

public class HookEntry {
    private static final String TAG = "HookEntry";

    /** Packages whose phase-2 (late) dispatch already ran. */
    private static final Set<String> initializedPackages = new HashSet<>();
    /** Packages whose phase-1 (early, pre-Application) dispatch already ran. */
    private static final Set<String> earlyPackages = new HashSet<>();
    private static final IHookEnvironment environment = new LsplantEnvironment();

    public static void init(Context context, ClassLoader classLoader, String packageName) {
        if (initializedPackages.contains(packageName)) {
            return;
        }

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
        // Modules: early first (no-op when phase 1 already ran it),
        // then the late onPackageReady fan-out.
        initModulesEarly(context, classLoader, packageName);
        initModulesLate(context, classLoader, packageName);
        initializedPackages.add(packageName);
    }

    /**
     * Phase 1 — called from ActivityThread BEFORE Application creation
     * (ModEntry.initEarly). Fires onModuleLoaded/onPackageLoaded (modern)
     * and initZygote/handleLoadPackage (legacy/Yuki) so lifecycle hooks
     * (e.g. Yuki's Application.attach) land in time. Idempotent per package.
     */
    public static void initModulesEarly(Context context, ClassLoader classLoader,
            String packageName) {
        // TEMPORARY: APK-module (Xposed/LSPosed/Vector) support disabled
        // via ApkModulesGate — dispatches bypassed, code kept.
        // (Gate first, so a live-enabled flag still takes effect in this
        // process on the phase-2 pass.)
        if (!ApkModulesGate.isEnabled(context)) {
            return;
        }
        if (!earlyPackages.add(packageName)) {
            return;
        }
        // A foreign engine (Vector/LSPosed) routes APK modules itself —
        // running our APK hosts on top would double-dispatch every module.
        // JAR addons (phase 2) keep working through the shared bridge.
        if (LsplantBridge.isForeignEngine()) {
            Log.i(TAG, "Foreign engine active in " + packageName
                    + " — skipping internal APK module host");
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
            XposedModuleHost.dispatchPackageEarly(context, classLoader, packageName);
        } catch (Throwable t) {
            environment.logError(TAG, "Module early dispatch failed for " + packageName, t);
        }
        try {
            LegacyModuleHost.dispatchPackage(context, classLoader, packageName);
        } catch (Throwable t) {
            environment.logError(TAG, "Legacy dispatch failed for " + packageName, t);
        }
    }

    /**
     * Phase 2 — called from ActivityThread AFTER Application creation
     * (ModEntry.init path). Runs JAR addons; module callbacks already
     * completed in phase 1 (Vector/LSPosed parity: all modern callbacks
     * fire pre-Application so provider-attach guards can be hooked).
     * Idempotent per package.
     */
    public static void initModulesLate(Context context, ClassLoader classLoader,
            String packageName) {
        if (initializedPackages.contains(packageName)) {
            return;
        }
        if (!ApkModulesGate.isEnabled(context)) {
            return;
        }
        if (XposedModuleHost.SYSTEM_PACKAGE.equals(packageName)) {
            return;
        }
        // Foreign engine (Vector/LSPosed) owns APK dispatch, including the
        // module service bind — our late pass stands down with it.
        if (LsplantBridge.isForeignEngine()) {
            return;
        }
        try {
            XposedModuleHost.dispatchPackageLate(context, classLoader, packageName);
        } catch (Throwable t) {
            environment.logError(TAG, "Module late dispatch failed for " + packageName, t);
        }
    }
}
