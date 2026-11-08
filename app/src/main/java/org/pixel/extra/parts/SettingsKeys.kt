package org.pixel.extra.parts

object SettingsKeys {

    const val SATURATION_ENABLED = "pixelparts_saturation_enabled"
    const val SATURATION_PERCENT = "pixelparts_saturation_percent"

    const val AUTO_HBM_ENABLED = "pixelparts_auto_hbm_enabled"
    const val AUTO_HBM_THRESHOLD = "pixelparts_auto_hbm_threshold"
    const val AUTO_HBM_ENABLE_TIME = "pixelparts_auto_hbm_enable_time"
    const val AUTO_HBM_DISABLE_TIME = "pixelparts_auto_hbm_disable_time"
    const val AUTO_HBM_ORIGINAL_BRIGHTNESS = "pixelparts_auto_hbm_original_brightness"
    const val AUTO_HBM_ACTIVE = "pixelparts_auto_hbm_active"
    const val AUTO_HBM_ACTIVE_SINCE = "pixelparts_auto_hbm_active_since"
    const val AUTO_HBM_ORIGINAL_SYSTEM_BRIGHTNESS = "pixelparts_auto_hbm_original_system_brightness"
    const val AUTO_HBM_LAST_SYSTEM_BRIGHTNESS = "pixelparts_auto_hbm_last_system_brightness"
    const val AUTO_HBM_LAST_LUX = "pixelparts_auto_hbm_last_lux"
    const val AUTO_HBM_LAST_BRIGHTNESS = "pixelparts_auto_hbm_last_brightness"
    const val AUTO_HBM_SMOOTH_RAMP_ENABLED = "pixelparts_auto_hbm_smooth_ramp_enabled"
    const val AUTO_HBM_RAMP_TIME_MS = "pixelparts_auto_hbm_ramp_time_ms"
    const val AUTO_HBM_MAX_ACTIVE_TIME = "pixelparts_auto_hbm_max_active_time"
    const val AUTO_HBM_COOLDOWN_TIME = "pixelparts_auto_hbm_cooldown_time"
    const val AUTO_HBM_CHECK_INTERVAL_MS = "pixelparts_auto_hbm_check_interval_ms"
    const val AUTO_HBM_TEMPERATURE_LIMIT = "pixelparts_auto_hbm_temperature_limit"
    const val AUTO_HBM_AUTO_BRIGHTNESS_WAS_ENABLED = "pixelparts_auto_hbm_auto_brightness_was_enabled"
    const val AUTO_HBM_LAST_TEMPERATURE = "pixelparts_auto_hbm_last_temperature"
    const val AUTO_HBM_MODE = "pixelparts_auto_hbm_mode"
    const val AUTO_HBM_PERMANENT = "pixelparts_auto_hbm_permanent"
    const val AUTO_HBM_BRIGHTNESS_LOCK = "pixelparts_auto_hbm_brightness_lock"

    const val AUTO_LOCK_ENABLED = "pixelparts_auto_lock_enabled"
    const val AUTO_LOCK_TIMEOUT_SECONDS = "pixelparts_auto_lock_timeout_seconds"
    const val AUTO_LOCK_MODE = "pixelparts_auto_lock_mode"
    const val AUTO_LOCK_PAUSE_MEDIA = "pixelparts_auto_lock_pause_media"

    const val HBM_MODE_AUTO = 0
    const val HBM_MODE_PERMANENT = 1

    const val THERMAL_TILE_PROFILE_QUEUE = "pixelparts_thermal_tile_profile_queue"
    const val THERMAL_TILE_PROFILE_QUEUE_INDEX = "pixelparts_thermal_tile_profile_queue_index"

    const val LOG_SERVICE_ENABLED = "pixelparts_log_service_enabled"
    const val LOG_SERVICE_LOGCAT_ENABLED = "pixelparts_log_service_logcat_enabled"
    const val LOG_SERVICE_DMESG_ENABLED = "pixelparts_log_service_dmesg_enabled"
    const val LOG_SERVICE_CRASHES_ENABLED = "pixelparts_log_service_crashes_enabled"

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
    const val ICON_SHAPE_ALL_APPS_SUGGESTIONS_THEMED_ICONS = "pixelparts_icon_shape_all_apps_suggestions_themed_icons"


    // Engine suffixes (_pine/_xposed/_lsplant) were removed from all hook keys.
    // Reads still fall back to legacy suffixed keys (see SettingsCompat).
    // Reads still fall back to legacy suffixed keys (see SettingsCompat).
    
    const val BATTERY_INFO_ENABLE = "pixelparts_battery_info_enable"
    const val SHOW_WATTAGE = "pixelparts_battery_info_show_wattage"
    const val SHOW_VOLTAGE = "pixelparts_battery_info_show_voltage"
    const val SHOW_CURRENT = "pixelparts_battery_info_show_current"
    const val SHOW_TEMP = "pixelparts_battery_info_show_temp"
    const val SHOW_PERCENT = "pixelparts_battery_info_show_percent"
    const val SHOW_STANDARD_STRING = "pixelparts_battery_info_show_standard_string"
    const val SHOW_CUSTOM_SYMBOL = "pixelparts_battery_info_show_custom_symbol"
    const val CUSTOM_SYMBOL = "pixelparts_battery_info_custom_symbol"
    const val BATTERY_INFO_REFRESH_INTERVAL_MS = "pixelparts_battery_info_refresh_interval_ms"
    const val BATTERY_INFO_AVERAGE_MODE = "pixelparts_battery_info_average_mode"

    const val LAUNCHER_CLEAR_ALL_ENABLED = "launcher_clear_all"

    const val LAUNCHER_CLEAR_ALL_HIDE_ACTIONS_ROW = "launcher_clear_all_hide_actions_row"

    const val LAUNCHER_CLEAR_ALL_MODE = "launcher_replace_on_clear"

    const val LAUNCHER_CLEAR_ALL_MARGIN = "launcher_clear_all_bottom_margin"

    const val LAUNCHER_HOMEPAGE_SIZER = "launcher_homepage_sizer"

    const val LAUNCHER_HOMEPAGE_COLS = "launcher_homepage_h"

    const val LAUNCHER_HOMEPAGE_ROWS = "launcher_homepage_v"

    const val LAUNCHER_HOMEPAGE_HIDE_TEXT = "launcher_homepage_hide_text"

    const val LAUNCHER_HOMEPAGE_ICON_SIZE = "launcher_homepage_icon_size"

    const val LAUNCHER_HOMEPAGE_TEXT_MODE = "launcher_homepage_text_mode"

    const val LAUNCHER_MENUPAGE_SIZER = "launcher_menupage_sizer"

    const val LAUNCHER_MENUPAGE_COLS = "launcher_menupage_h"

    const val LAUNCHER_MENUPAGE_ROW_HEIGHT = "launcher_menupage_row_height"

    const val LAUNCHER_MENUPAGE_HIDE_TEXT = "launcher_menupage_hide_text"

    const val LAUNCHER_MENUPAGE_ICON_SIZE = "launcher_menupage_icon_size"

    const val LAUNCHER_MENUPAGE_TEXT_MODE = "launcher_menupage_text_mode"
    
    const val LAUNCHER_DOCK_ENABLE = "launcher_dock_enable"

    const val LAUNCHER_HIDE_SEARCH = "launcher_hidden_search"

    const val LAUNCHER_HIDE_DOCK = "launcher_hidden_dock"

    const val LAUNCHER_DOCK_PADDING = "launcher_dock_padding"

    const val LAUNCHER_HOTSEAT_ICONS = "launcher_hotseat_icons"

    const val LAUNCHER_HOTSEAT_HIDE_TEXT = "launcher_hotseat_hide_text"

    const val LAUNCHER_HOTSEAT_ICON_SIZE = "launcher_hotseat_icon_size"

    const val LAUNCHER_HOTSEAT_TEXT_MODE = "launcher_hotseat_text_mode"

    const val LAUNCHER_DISABLE_GOOGLE_FEED = "launcher_disable_google_feed"

    const val LAUNCHER_DISABLE_TOP_WIDGET = "launcher_disable_top_widget"

    
    const val LAUNCHER_DEBUG_ENABLE = "launcher_debug_enable"

    const val LAUNCHER_RECENTS_MODIFY_ENABLE = "launcher_recents_modify_enable"

    const val LAUNCHER_RECENTS_DISABLE_LIVETILE = "launcher_recents_disable_livetile"

    const val LAUNCHER_RECENTS_SCALE_ENABLE = "launcher_recents_scale_enable"

    const val LAUNCHER_RECENTS_SCALE_PERCENT = "launcher_recents_scale_percent"

    const val LAUNCHER_RECENTS_CAROUSEL_SCALE = "launcher_recents_carousel_scale"

    const val LAUNCHER_RECENTS_CAROUSEL_SPACING = "launcher_recents_carousel_spacing"

    const val LAUNCHER_RECENTS_CAROUSEL_ALPHA = "launcher_recents_carousel_alpha"

    const val LAUNCHER_RECENTS_CAROUSEL_BLUR_RADIUS = "launcher_recents_carousel_blur_radius"

    const val LAUNCHER_RECENTS_CAROUSEL_BLUR_OVERFLOW = "launcher_recents_carousel_blur_overflow"

    const val LAUNCHER_RECENTS_CAROUSEL_TINT_COLOR = "launcher_recents_carousel_tint_color"

    const val LAUNCHER_RECENTS_CAROUSEL_TINT_INTENSITY = "launcher_recents_carousel_tint_intensity"

    const val LAUNCHER_RECENTS_CAROUSEL_ICON_OFFSET_X = "launcher_recents_carousel_icon_offset_x"

    const val LAUNCHER_RECENTS_CAROUSEL_ICON_OFFSET_Y = "launcher_recents_carousel_icon_offset_y"

    const val LAUNCHER_PADDING_HOMEPAGE = "launcher_padding_homepage"

    const val LAUNCHER_PADDING_DOCK = "launcher_padding_dock"

    const val LAUNCHER_PADDING_SEARCH = "launcher_padding_search"

    const val LAUNCHER_PADDING_DOTS = "launcher_padding_dots"

    const val LAUNCHER_PADDING_DOTS_X = "launcher_padding_dots_x"


    const val LAUNCHER_DT2S_ENABLED = "launcher_dt2s_enabled"

    // SystemUI / Shade
    const val QS_COMPACT_PLAYER = "qs_compact_player"
    const val QS_PLAYER_HIDE_EXPAND = "qs_player_hide_expand"
    const val QS_PLAYER_HIDE_NOTIFY = "qs_player_hide_notify"
    const val QS_PLAYER_HIDE_LOCKSCREEN = "qs_player_hide_lockscreen"
    const val QS_PLAYER_ALPHA = "qs_player_alpha"

    const val AOD_FULL_COLOR_NOTIFICATION_ICONS = "aod_full_color_notification_icons"

    const val STATUS_BAR_USE_APP_ICONS = "status_bar_use_app_icons"
    const val STATUS_BAR_MONOCHROME_NOTIFICATION_ICONS = "status_bar_monochrome_notification_icons"
    const val AOD_USE_APP_ICONS = "aod_use_app_icons"
    const val AOD_MONOCHROME_NOTIFICATION_ICONS = "aod_monochrome_notification_icons"

    const val GESTURE_BAR_ENABLED = "gesture_bar_enabled"
    const val GESTURE_BAR_WIDTH_PERCENT = "gesture_bar_width_percent"
    const val GESTURE_BAR_HEIGHT_DP = "gesture_bar_height_dp"
    const val GESTURE_BAR_OFFSET_X_DP = "gesture_bar_offset_x_dp"
    const val GESTURE_BAR_OFFSET_Y_DP = "gesture_bar_offset_y_dp"
    const val GESTURE_BAR_RESERVED_AREA_DP = "gesture_bar_reserved_area_dp"
    const val GESTURE_BAR_GESTURE_AREA_DP = "gesture_bar_gesture_area_dp"
    const val GESTURE_BAR_HIDE_ON_LAUNCHER = "gesture_bar_hide_on_launcher"
    const val GESTURE_BAR_HIDE_IN_APPS = "gesture_bar_hide_in_apps"
    const val GESTURE_BAR_LAUNCHER_HIDE_TIMEOUT_MS = "gesture_bar_launcher_hide_timeout_ms"
    const val GESTURE_BAR_APP_HIDE_TIMEOUT_MS = "gesture_bar_app_hide_timeout_ms"
    const val GESTURE_BAR_HIDE_ON_LOCKSCREEN = "gesture_bar_hide_on_lockscreen"
    const val GESTURE_BAR_REMOVE_RESERVED_AREA = "gesture_bar_remove_reserved_area"
    const val GESTURE_BAR_ALPHA_PERCENT = "gesture_bar_alpha_percent"
    const val GESTURE_BAR_TINT_ENABLED = "gesture_bar_tint_enabled"
    const val GESTURE_BAR_TINT_COLOR = "gesture_bar_tint_color"
    const val GESTURE_BAR_FADE_IN_MS = "gesture_bar_fade_in_ms"
    const val GESTURE_BAR_FADE_OUT_MS = "gesture_bar_fade_out_ms"

    /** Shade background/window blur intensity in percent (100 = stock). */
    const val SHADE_BLUR_INTENSITY = "shade_blur_intensity"

    /** Shade zoom intensity in percent (100 = stock). */
    const val SHADE_ZOOM_INTENSITY = "shade_zoom_intensity"

    /** Shade zoom master switch (default true; OFF forces sharp surface). */
    const val SHADE_ZOOM_ENABLED = "shade_zoom_enabled"

    /** Notification Scrim Alpha (0-100, -1 = default). */
    const val SHADE_NOTIF_SCRIM_ALPHA = "shade_notif_scrim_alpha"

    /** Notification Scrim Tint (Color int, 0 = default). */
    const val SHADE_NOTIF_SCRIM_TINT = "shade_notif_scrim_tint"

    /** Notification Scrim Tint enabled (0/1). */
    const val SHADE_NOTIF_SCRIM_TINT_ENABLED = "shade_notif_scrim_tint_enabled"

    /** Main Scrim Alpha (0-100, -1 = default). */
    const val SHADE_MAIN_SCRIM_ALPHA = "shade_main_scrim_alpha"

    /** Main Scrim Tint (Color int, 0 = default). */
    const val SHADE_MAIN_SCRIM_TINT = "shade_main_scrim_tint"

    /** Main Scrim Tint enabled (0/1). */
    const val SHADE_MAIN_SCRIM_TINT_ENABLED = "shade_main_scrim_tint_enabled"

    /** Minimum non-zero blur radius in px to avoid awkward tiny-blur transitions (0 = off). */
    const val SHADE_BLUR_MIN_RADIUS_PX = "shade_blur_min_radius_px"

    const val LAUNCHER_DT2S_TIMEOUT = "launcher_dt2s_timeout"
    const val LAUNCHER_DT2S_SLOP = "launcher_dt2s_slop"

    const val LAUNCHER_CURRENT_ICON_PACK = "launcher_current_icon_pack"

    
    const val PIXEL_LAUNCHER_NATIVE_SEARCH = "pixel_launcher_native_search"

    // Magnifier (text loupe)
    const val MAGNIFIER_CUSTOM_ENABLED = "magnifier_custom_enabled"
    const val MAGNIFIER_CUSTOM_ZOOM = "magnifier_custom_zoom"
    const val MAGNIFIER_CUSTOM_SIZE = "magnifier_custom_size"
    const val MAGNIFIER_CUSTOM_SHAPE = "magnifier_custom_shape"
    const val MAGNIFIER_CUSTOM_OFFSET_Y = "magnifier_custom_offset_y"

    // Two-Shade (horizontal QS paging)
    /** Enable two-shade mode: QS becomes a separate horizontal page in shade. */
    const val TWO_SHADE_HOOK = "two_shade_hook"

    // Activity transition animations
    /** Open transition mode ID (0=disabled, -1=custom, 10-93=built-in). */
    const val ACTIVITY_OPEN_TRANSITION = "activity_open_transition"

    /** Close transition mode ID (0=disabled, -1=custom, 10-93=built-in). */
    const val ACTIVITY_CLOSE_TRANSITION = "activity_close_transition"

    /** Last non-disabled open transition mode used by the QS tile. */
    const val ACTIVITY_OPEN_TRANSITION_LAST = "activity_open_transition_last"

    /** Last non-disabled close transition mode used by the QS tile. */
    const val ACTIVITY_CLOSE_TRANSITION_LAST = "activity_close_transition_last"

    /** Package name of the custom animation theme APK. */
    const val ACTIVITY_TRANSITION_CUSTOM_PACKAGE = "activity_transition_custom_package"

    /** Package name of the custom animation theme APK for open transitions. */
    const val ACTIVITY_OPEN_CUSTOM_PACKAGE = "activity_open_custom_package"

    /** Package name of the custom animation theme APK for close transitions. */
    const val ACTIVITY_CLOSE_CUSTOM_PACKAGE = "activity_close_custom_package"

    /** Disable predictive back gesture animation (0 = default, 1 = disable). */
    const val DISABLE_PREDICTIVE_BACK_ANIM = "disable_predictive_back_anim"

    // Double Tap to Wake (DTW) — framework-backed sensor implementation
    const val DTW_ENABLED = "pixelparts_dtw_enabled"
    const val DTW_TAP_COUNT = "pixelparts_dtw_tap_count"          // 1..3
    const val DTW_TAP_TIMEOUT_MS = "pixelparts_dtw_tap_timeout_ms"  // 100..2000
    const val DTW_COORDINATE_CHECK = "pixelparts_dtw_coordinate_check" // bool
    const val DTW_MAX_DISTANCE_DP = "pixelparts_dtw_max_distance_dp" // 10..500
}
