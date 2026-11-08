package org.pixel.extra.parts.icons

// Canonical Settings keys for the Icons section. Names are frozen to the
// legacy PixelParts values, so the framework and launcher hooks keep working
// without any migration step. All keys live in Settings.Global.
object IconsKeys {
    const val APP_ICONS_ENABLED = "pixelparts_app_icons_enabled"
    const val APP_ICONS_LAUNCHER_ENABLED = "pixelparts_app_icons_launcher_enabled"

    const val APP_ICONS_SYSTEM_STRETCH_SHAPE = "pixelparts_app_icons_system_stretch_shape"
    const val APP_ICONS_SYSTEM_REMOVE_SHAPE = "pixelparts_app_icons_system_remove_shape"
    const val APP_ICONS_SYSTEM_SHAPE_SCALE = "pixelparts_app_icons_system_shape_scale"

    const val APP_ICONS_NOTIFICATION_STRETCH_SHAPE = "pixelparts_app_icons_notification_stretch_shape"
    const val APP_ICONS_NOTIFICATION_REMOVE_SHAPE = "pixelparts_app_icons_notification_remove_shape"
    const val APP_ICONS_NOTIFICATION_SHAPE_SCALE = "pixelparts_app_icons_notification_shape_scale"

    const val APP_ICONS_LAUNCHER_STRETCH_SHAPE = "pixelparts_app_icons_launcher_stretch_shape"
    const val APP_ICONS_LAUNCHER_REMOVE_SHAPE = "pixelparts_app_icons_launcher_remove_shape"
    const val APP_ICONS_LAUNCHER_SHAPE_SCALE = "pixelparts_app_icons_launcher_shape_scale"

    const val APP_ICONS_SHAPE_BACKGROUND_TINT_MODE = "pixelparts_app_icons_shape_background_tint_mode"
    const val APP_ICONS_SHAPE_BACKGROUND_TINT_COLOR = "pixelparts_app_icons_shape_background_tint_color"
    const val APP_ICONS_SHAPE_FOREGROUND_TINT_MODE = "pixelparts_app_icons_shape_foreground_tint_mode"
    const val APP_ICONS_SHAPE_FOREGROUND_TINT_COLOR = "pixelparts_app_icons_shape_foreground_tint_color"

    const val ICON_SHAPE_CUSTOM_PATH = "pixelparts_icon_shape_custom_path"
    const val ICON_SHAPE_CUSTOM_NAME = "pixelparts_icon_shape_custom_name"
    const val ICON_SHAPE_WORKSPACE_MATCH_ALL_APPS = "pixelparts_icon_shape_workspace_match_all_apps"
    const val ICON_SHAPE_IGNORE_CUSTOM_SETTINGS = "pixelparts_icon_shape_ignore_custom_settings"
    const val ICON_SHAPE_ALL_APPS_FOLLOW_WORKSPACE = "pixelparts_icon_shape_all_apps_follow_workspace"
    const val ICON_SHAPE_ALL_APPS_THEMED_ICONS = "pixelparts_icon_shape_all_apps_themed_icons"
    const val ICON_SHAPE_SUGGESTIONS_THEMED_ICONS = "pixelparts_icon_shape_all_apps_suggestions_themed_icons"

    const val LAUNCHER_CURRENT_ICON_PACK = "launcher_current_icon_pack"
    const val PIXEL_LAUNCHER_NATIVE_SEARCH = "pixel_launcher_native_search"

    // Overlay category used by the future shape stage for
    // android.theme.customization.adaptive_icon_shape overlays.
    const val ADAPTIVE_ICON_SHAPE_CATEGORY = "android.theme.customization.adaptive_icon_shape"
}
