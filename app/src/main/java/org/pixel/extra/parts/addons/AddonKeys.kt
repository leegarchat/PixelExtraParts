package org.pixel.extra.parts.addons

// Canonical Settings.Global keys for addon UI state and the bundled
// systemui_hooks stubs. Names are plain identifiers with no engine suffix:
// the legacy _pine/_xposed/_lsplant compat mapping is gone, the unified
// scheme reads and writes one key per knob. Only new keys live here;
// knobs owned by built-in sections keep living in their feature Keys files.
object AddonKeys {
    // Runtime enable flag read by the lsplant host at process start.
    private const val ENABLED_PREFIX = "pixel_addon_"
    private const val ENABLED_SUFFIX = "_enabled"
    fun enabledKey(addonId: String): String = "$ENABLED_PREFIX$addonId$ENABLED_SUFFIX"

    // Rotation stub page (systemui_hooks/rotation).
    const val ROTATION_MODE = "systemui_hooks_rotation_mode"
    const val ROTATION_SMOOTH = "systemui_hooks_rotation_smooth"
    const val ROTATION_MODE_DEFAULT = 0

    // HBM stub page (systemui_hooks/hbm).
    const val HBM_LOCK = "systemui_hooks_hbm_lock"
    const val HBM_WINDOW_S = "systemui_hooks_hbm_window_s"
    const val HBM_WINDOW_MIN_S = 10
    const val HBM_WINDOW_MAX_S = 2000
    const val HBM_WINDOW_STEP_S = 10
    const val HBM_WINDOW_DEFAULT_S = 120

    // AOD stub page (systemui_hooks/aod).
    const val AOD_SHIFT = "systemui_hooks_aod_shift"
    const val AOD_FULL_COLOR = "systemui_hooks_aod_full_color"
}
