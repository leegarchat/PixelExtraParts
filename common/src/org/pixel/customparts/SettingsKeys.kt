package org.pixel.customparts

import org.pixel.customparts.AppConfig.IS_XPOSED

object SettingsKeys {
    
    val LAUNCHER_CLEAR_ALL_ENABLED: String
        get() = if (IS_XPOSED) "launcher_clear_all_xposed" else "launcher_clear_all_pine"

    val LAUNCHER_CLEAR_ALL_MODE: String
        get() = if (IS_XPOSED) "launcher_replace_on_clear_xposed" else "launcher_replace_on_clear_pine"

    val LAUNCHER_CLEAR_ALL_MARGIN: String
        get() = if (IS_XPOSED) "launcher_clear_all_bottom_margin_xposed" else "launcher_clear_all_bottom_margin_pine"

    val LAUNCHER_HOMEPAGE_SIZER: String
        get() = if (IS_XPOSED) "launcher_homepage_sizer_xposed" else "launcher_homepage_sizer_pine"

    val LAUNCHER_HOMEPAGE_COLS: String
        get() = if (IS_XPOSED) "launcher_homepage_h_xposed" else "launcher_homepage_h_pine"

    val LAUNCHER_HOMEPAGE_ROWS: String
        get() = if (IS_XPOSED) "launcher_homepage_v_xposed" else "launcher_homepage_v_pine"

    val LAUNCHER_HOMEPAGE_HIDE_TEXT: String
        get() = if (IS_XPOSED) "launcher_homepage_hide_text_xposed" else "launcher_homepage_hide_text_pine"

    val LAUNCHER_HOMEPAGE_ICON_SIZE: String
        get() = if (IS_XPOSED) "launcher_homepage_icon_size_xposed" else "launcher_homepage_icon_size_pine"

    val LAUNCHER_HOMEPAGE_TEXT_MODE: String
        get() = if (IS_XPOSED) "launcher_homepage_text_mode_xposed" else "launcher_homepage_text_mode_pine"

    val LAUNCHER_MENUPAGE_SIZER: String
        get() = if (IS_XPOSED) "launcher_menupage_sizer_xposed" else "launcher_menupage_sizer_pine"

    val LAUNCHER_MENUPAGE_COLS: String
        get() = if (IS_XPOSED) "launcher_menupage_h_xposed" else "launcher_menupage_h_pine"

    val LAUNCHER_MENUPAGE_ROW_HEIGHT: String
        get() = if (IS_XPOSED) "launcher_menupage_row_height_xposed" else "launcher_menupage_row_height_pine"

    val LAUNCHER_MENUPAGE_HIDE_TEXT: String
        get() = if (IS_XPOSED) "launcher_menupage_hide_text_xposed" else "launcher_menupage_hide_text_pine"

    val LAUNCHER_MENUPAGE_ICON_SIZE: String
        get() = if (IS_XPOSED) "launcher_menupage_icon_size_xposed" else "launcher_menupage_icon_size_pine"

    val LAUNCHER_MENUPAGE_TEXT_MODE: String
        get() = if (IS_XPOSED) "launcher_menupage_text_mode_xposed" else "launcher_menupage_text_mode_pine"
    
    val LAUNCHER_DOCK_ENABLE: String
        get() = if (IS_XPOSED) "launcher_dock_enable_xposed" else "launcher_dock_enable_pine"

    val LAUNCHER_HIDE_SEARCH: String
        get() = if (IS_XPOSED) "launcher_hidden_search_xposed" else "launcher_hidden_search_pine"

    val LAUNCHER_HIDE_DOCK: String
        get() = if (IS_XPOSED) "launcher_hidden_dock_xposed" else "launcher_hidden_dock_pine"

    val LAUNCHER_DOCK_PADDING: String
        get() = if (IS_XPOSED) "launcher_dock_padding_xposed" else "launcher_dock_padding_pine"

    val LAUNCHER_HOTSEAT_ICONS: String
        get() = if (IS_XPOSED) "launcher_hotseat_icons_xposed" else "launcher_hotseat_icons_pine"

    val LAUNCHER_HOTSEAT_HIDE_TEXT: String
        get() = if (IS_XPOSED) "launcher_hotseat_hide_text_xposed" else "launcher_hotseat_hide_text_pine"

    val LAUNCHER_HOTSEAT_ICON_SIZE: String
        get() = if (IS_XPOSED) "launcher_hotseat_icon_size_xposed" else "launcher_hotseat_icon_size_pine"

    val LAUNCHER_HOTSEAT_TEXT_MODE: String
        get() = if (IS_XPOSED) "launcher_hotseat_text_mode_xposed" else "launcher_hotseat_text_mode_pine"

    val LAUNCHER_DISABLE_GOOGLE_FEED: String
        get() = if (IS_XPOSED) "launcher_disable_google_feed_xposed" else "launcher_disable_google_feed_pine"

    val LAUNCHER_DISABLE_TOP_WIDGET: String
        get() = if (IS_XPOSED) "launcher_disable_top_widget_xposed" else "launcher_disable_top_widget_pine"

    
    val LAUNCHER_DEBUG_ENABLE: String
        get() = if (IS_XPOSED) "launcher_debug_enable_xposed" else "launcher_debug_enable_pine"

    val LAUNCHER_RECENTS_MODIFY_ENABLE: String
        get() = if (IS_XPOSED) "launcher_recents_modify_enable_xposed" else "launcher_recents_modify_enable_pine"

    val LAUNCHER_RECENTS_DISABLE_LIVETILE: String
        get() = if (IS_XPOSED) "launcher_recents_disable_livetile_xposed" else "launcher_recents_disable_livetile_pine"

    val LAUNCHER_RECENTS_SCALE_ENABLE: String
        get() = if (IS_XPOSED) "launcher_recents_scale_enable_xposed" else "launcher_recents_scale_enable_pine"

    val LAUNCHER_RECENTS_SCALE_PERCENT: String
        get() = if (IS_XPOSED) "launcher_recents_scale_percent_xposed" else "launcher_recents_scale_percent_pine"

    val LAUNCHER_RECENTS_CAROUSEL_SCALE: String
        get() = if (IS_XPOSED) "launcher_recents_carousel_scale_xposed" else "launcher_recents_carousel_scale_pine"

    val LAUNCHER_RECENTS_CAROUSEL_SPACING: String
        get() = if (IS_XPOSED) "launcher_recents_carousel_spacing_xposed" else "launcher_recents_carousel_spacing_pine"

    val LAUNCHER_RECENTS_CAROUSEL_ALPHA: String
        get() = if (IS_XPOSED) "launcher_recents_carousel_alpha_xposed" else "launcher_recents_carousel_alpha_pine"

    val LAUNCHER_RECENTS_CAROUSEL_BLUR_RADIUS: String
        get() = if (IS_XPOSED) "launcher_recents_carousel_blur_radius_xposed" else "launcher_recents_carousel_blur_radius_pine"

    val LAUNCHER_RECENTS_CAROUSEL_BLUR_OVERFLOW: String
        get() = if (IS_XPOSED) "launcher_recents_carousel_blur_overflow_xposed" else "launcher_recents_carousel_blur_overflow_pine"

    val LAUNCHER_RECENTS_CAROUSEL_TINT_COLOR: String
        get() = if (IS_XPOSED) "launcher_recents_carousel_tint_color_xposed" else "launcher_recents_carousel_tint_color_pine"

    val LAUNCHER_RECENTS_CAROUSEL_TINT_INTENSITY: String
        get() = if (IS_XPOSED) "launcher_recents_carousel_tint_intensity_xposed" else "launcher_recents_carousel_tint_intensity_pine"

    val LAUNCHER_RECENTS_CAROUSEL_ICON_OFFSET_X: String
        get() = if (IS_XPOSED) "launcher_recents_carousel_icon_offset_x_xposed" else "launcher_recents_carousel_icon_offset_x_pine"

    val LAUNCHER_RECENTS_CAROUSEL_ICON_OFFSET_Y: String
        get() = if (IS_XPOSED) "launcher_recents_carousel_icon_offset_y_xposed" else "launcher_recents_carousel_icon_offset_y_pine"

    val LAUNCHER_PADDING_HOMEPAGE: String
        get() = if (IS_XPOSED) "launcher_padding_homepage_xposed" else "launcher_padding_homepage_pine"

    val LAUNCHER_PADDING_DOCK: String
        get() = if (IS_XPOSED) "launcher_padding_dock_xposed" else "launcher_padding_dock_pine"

    val LAUNCHER_PADDING_SEARCH: String
        get() = if (IS_XPOSED) "launcher_padding_search_xposed" else "launcher_padding_search_pine"

    val LAUNCHER_PADDING_DOTS: String
        get() = if (IS_XPOSED) "launcher_padding_dots_xposed" else "launcher_padding_dots_pine"

    val LAUNCHER_PADDING_DOTS_X: String
        get() = if (IS_XPOSED) "launcher_padding_dots_x_xposed" else "launcher_padding_dots_x_pine"


    val DOZE_DOUBLE_TAP_HOOK: String
        get() = if (IS_XPOSED) "doze_double_tap_hook_xposed" else "doze_double_tap_hook_pine"

    val LAUNCHER_DT2S_ENABLED: String
        get() = if (IS_XPOSED) "launcher_dt2s_enabled_xposed" else "launcher_dt2s_enabled_pine"

    const val DOZE_DOUBLE_TAP_TIMEOUT = "doze_double_tap_timeout"
    const val LAUNCHER_DT2S_TIMEOUT = "launcher_dt2s_timeout"
    const val LAUNCHER_DT2S_SLOP = "launcher_dt2s_slop"

    const val LAUNCHER_CURRENT_ICON_PACK = "launcher_current_icon_pack"

    
    const val PIXEL_LAUNCHER_NATIVE_SEARCH = "pixel_launcher_native_search"
}