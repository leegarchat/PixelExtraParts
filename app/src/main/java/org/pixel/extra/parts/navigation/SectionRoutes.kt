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

    val ORDERED: List<String> = listOf(
        HOME,
        DISPLAY,
        SYSTEM_UI,
        ADDONS,
        OVERSCROLL,
        THERMAL,
        ICONS,
        HIDDEN_APPS,
        SETTINGS,
    )
}
