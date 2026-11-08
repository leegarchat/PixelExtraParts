package org.pixel.extra.parts.hiddenapps

import org.pixel.extra.parts.navigation.SectionRoutes

// Single naming point for the hidden-apps section. Hidden state itself lives in
// PackageManager (no Settings keys by design), so this object only pins the
// route, save-state names and platform command details for future renames.
object HiddenAppsKeys {
    const val ROUTE: String = SectionRoutes.HIDDEN_APPS

    const val SAVE_QUERY: String = "hidden_apps_query"
    const val SAVE_SHOW_SYSTEM: String = "hidden_apps_show_system"

    // Launcher package restarted after a hidden-state change.
    const val LAUNCHER_PACKAGE: String = "com.google.android.apps.nexuslauncher"

    // Root fallback verbs for `pm <verb> --user <id> <package>`.
    const val PM_HIDE: String = "hide"
    const val PM_UNHIDE: String = "unhide"
}
