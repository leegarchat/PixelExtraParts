package org.pixel.extra.parts.navigation

// Route keys for the top-level sections. Navigation is dock-driven
// (collapsed bar + overflow list); no navigation library is involved.
// ORDERED defines the AnimatedContent slide direction between sections.
object SectionRoutes {
    const val HOME = "home"
    const val DISPLAY = "display"
    const val SYSTEM_UI = "systemui"
    const val ADDONS = "addons"
    const val OVERSCROLL = "overscroll"
    const val THERMAL = "thermal"
    const val ICONS = "icons"
    const val HIDDEN_APPS = "hidden_apps"
    const val SETTINGS = "settings"

    // Display hub subpages: opened from the display hub rows, back returns
    // to the hub through the shared tab history (no nested navigation).
    const val DISPLAY_SATURATION = "display_saturation"
    const val DISPLAY_AUTO_HBM = "display_auto_hbm"
    const val DISPLAY_DOUBLE_TAP = "display_double_tap"
    const val DISPLAY_AUTO_LOCK = "display_auto_lock"

    val ORDERED: List<String> = listOf(
        HOME,
        DISPLAY,
        DISPLAY_SATURATION,
        DISPLAY_AUTO_HBM,
        DISPLAY_DOUBLE_TAP,
        DISPLAY_AUTO_LOCK,
        SYSTEM_UI,
        ADDONS,
        OVERSCROLL,
        THERMAL,
        ICONS,
        HIDDEN_APPS,
        SETTINGS,
    )
}
