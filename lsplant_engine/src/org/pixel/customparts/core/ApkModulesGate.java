package org.pixel.customparts.core;

/**
 * TEMPORARY kill-switch for Xposed/LSPosed/Vector APK-module support.
 *
 * Single source of truth: every APK-module entry point checks this flag
 * and bails out early when it is false. Nothing is deleted — all module
 * code stays in the tree, only bypassed.
 *
 * Gated entry points:
 *  1. UI — AddonManagerScreen "APK modules" tab (scan + TabRow + content).
 *  2. Engine — HookEntry.init module dispatch (Xposed + Legacy hosts).
 *  3. Engine init — XposedModuleHost / LegacyModuleHost scans dispatches.
 *  4. Threads — ActivityThread weave patch, system_server branch
 *     (mirrored there as PIXEL_PARTS_APK_MODULES_ENABLED, framework
 *     cannot reference this class).
 *
 * To re-enable: set ENABLED = true and flip the ActivityThread patch
 * constant back to true, then rebuild.
 */
public final class ApkModulesGate {
    private ApkModulesGate() {
    }

    public static final boolean ENABLED = false;
}
