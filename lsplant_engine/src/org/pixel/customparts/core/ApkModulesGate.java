package org.pixel.customparts.core;

import android.content.Context;
import android.content.res.Resources;
import android.provider.Settings;

/**
 * TEMPORARY kill-switch for Xposed/LSPosed/Vector APK-module support.
 *
 * Single source of truth: every APK-module entry point checks
 * {@link #isEnabled(Context)} and bails out early when it is false.
 * Nothing is deleted — all module code stays in the tree, only bypassed.
 *
 * The flag is published to Settings.Global
 * ({@code pixel_addon_apk_modules_enabled}) by the app boot receiver from
 * {@code res/values/experimental_flags.xml} ({@code exp_apk_modules_manager}),
 * so tests can flip it live via {@code settings put global} (the XML
 * default wins again on next boot). When the Settings key is absent
 * (e.g. engine runs before first boot sync), the XML value is read
 * directly as a fallback. Any failure degrades to {@code false}.
 *
 * Gated entry points:
 *  1. UI — AddonManagerScreen "APK modules" tab (scan + TabRow + content).
 *  2. Engine — HookEntry.init module dispatch (Xposed + Legacy hosts).
 *  3. Engine init — XposedModuleHost / LegacyModuleHost scans dispatches.
 *  4. Threads — ActivityThread weave patch, system_server branch
 *     (reads the same Settings key at runtime; framework cannot
 *     reference this class).
 */
public final class ApkModulesGate {
    private ApkModulesGate() {
    }

    /** Settings.Global key published by the app boot receiver. */
    public static final String ENABLED_SETTING = "pixel_addon_apk_modules_enabled";

    /**
     * Reads the Settings key first (live test toggling), falls back to the
     * XML default when the key was never published.
     */
    public static boolean isEnabled(Context context) {
        try {
            int v = Settings.Global.getInt(
                    context.getContentResolver(), ENABLED_SETTING, -1);
            if (v != -1) return v != 0;
        } catch (Throwable ignored) {
        }
        return readXmlFlag(context);
    }

    private static boolean readXmlFlag(Context context) {
        try {
            Context moduleCtx = context;
            try {
                moduleCtx = context.createPackageContext(
                        "org.pixel.customparts", Context.CONTEXT_IGNORE_SECURITY);
            } catch (Throwable ignored) {
            }
            if (moduleCtx == null) moduleCtx = context;
            Resources res = moduleCtx.getResources();
            int id = res.getIdentifier("exp_apk_modules_manager", "bool",
                    "org.pixel.customparts");
            if (id != 0) return res.getBoolean(id);
        } catch (Throwable ignored) {
        }
        return false;
    }
}
