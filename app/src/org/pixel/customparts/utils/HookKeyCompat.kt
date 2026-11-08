package org.pixel.customparts.utils

/**
 * Single home for engine-rename key compatibility.
 *
 * Storage reality (do NOT change without a data migration + hook-side audit):
 * - new writes use [ACTIVE_SUFFIX] (`_lsplant`);
 * - hooks still read `_pine` (verbatim in Ambient/KeyguardBatteryPower hooks,
 *   via read-through fallback in BaseSystemUIHook), so `_pine` values must
 *   keep resolving;
 * - ancient `_xposed` values resolve through the same fallback.
 *
 * What is shared here is ONLY the suffix vocabulary + the strip helper.
 * Read/write gating intentionally stays at each call site:
 * [SettingsCompat] normalizes through its key-base allowlist,
 * `AddonManagerScreen` passes bare addon.json keys through verbatim and
 * rewrites only suffixed ones, `PixelPartsTileRefresher` only compares
 * normalized keys. Merging those policies would change lookup behavior.
 */
object HookKeyCompat {
    /** Canonical suffix for new writes. */
    const val ACTIVE_SUFFIX = "_lsplant"

    /** Previous engine suffix; still read (and still written by legacy paths). */
    const val LEGACY_ENGINE_SUFFIX = "_pine"

    /** Ancient module-era suffix; read-only compatibility. */
    const val XPOSED_SUFFIX = "_xposed"

    /**
     * Strips one trailing engine suffix, if present.
     * Chained (not first-match): inputs never carry stacked suffixes in
     * practice, so this matches the previous per-file implementations.
     */
    fun stripHookSuffix(key: String): String =
        key.removeSuffix(ACTIVE_SUFFIX)
            .removeSuffix(LEGACY_ENGINE_SUFFIX)
            .removeSuffix(XPOSED_SUFFIX)
}
