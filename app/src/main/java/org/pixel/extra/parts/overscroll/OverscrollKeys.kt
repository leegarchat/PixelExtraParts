package org.pixel.extra.parts.overscroll

// Single source of truth for every Settings.Global key of the overscroll
// engine. Key names are direct: the framework hooks read these exact names.
object OverscrollKeys {
    // Master + diagnostics.
    const val ENABLED = "overscroll_enabled"
    const val LOGGING = "overscroll_logging"
    const val PACKAGES_CONFIG = "overscroll_packages_config"

    // Spring core.
    const val PULL = "overscroll_pull"
    const val STIFFNESS = "overscroll_stiffness"
    const val DAMPING = "overscroll_damping"
    const val FLING = "overscroll_fling"
    const val RESISTANCE_EXPONENT = "overscroll_res_exponent"

    // Vertical scale visuals.
    const val SCALE_MODE = "overscroll_scale_mode"
    const val SCALE_INTENSITY = "overscroll_scale_intensity"
    const val SCALE_INTENSITY_HORIZ = "overscroll_scale_intensity_horiz"
    const val SCALE_LIMIT_MIN = "overscroll_scale_limit_min"
    const val SCALE_ANCHOR_X = "overscroll_scale_anchor_x"
    const val SCALE_ANCHOR_Y = "overscroll_scale_anchor_y"
    const val SCALE_ANCHOR_X_HORIZ = "overscroll_scale_anchor_x_horiz"
    const val SCALE_ANCHOR_Y_HORIZ = "overscroll_scale_anchor_y_horiz"

    // Zoom visuals.
    const val ZOOM_MODE = "overscroll_zoom_mode"
    const val ZOOM_INTENSITY = "overscroll_zoom_intensity"
    const val ZOOM_INTENSITY_HORIZ = "overscroll_zoom_intensity_horiz"
    const val ZOOM_LIMIT_MIN = "overscroll_zoom_limit_min"
    const val ZOOM_ANCHOR_X = "overscroll_zoom_anchor_x"
    const val ZOOM_ANCHOR_Y = "overscroll_zoom_anchor_y"
    const val ZOOM_ANCHOR_X_HORIZ = "overscroll_zoom_anchor_x_horiz"
    const val ZOOM_ANCHOR_Y_HORIZ = "overscroll_zoom_anchor_y_horiz"

    // Horizontal scale visuals.
    const val H_SCALE_MODE = "overscroll_h_scale_mode"
    const val H_SCALE_INTENSITY = "overscroll_h_scale_intensity"
    const val H_SCALE_INTENSITY_HORIZ = "overscroll_h_scale_intensity_horiz"
    const val H_SCALE_LIMIT_MIN = "overscroll_h_scale_limit_min"
    const val H_SCALE_ANCHOR_X = "overscroll_h_scale_anchor_x"
    const val H_SCALE_ANCHOR_Y = "overscroll_h_scale_anchor_y"
    const val H_SCALE_ANCHOR_X_HORIZ = "overscroll_h_scale_anchor_x_horiz"
    const val H_SCALE_ANCHOR_Y_HORIZ = "overscroll_h_scale_anchor_y_horiz"

    // Advanced pipeline.
    const val INPUT_SMOOTH = "overscroll_input_smooth"
    const val PHYSICS_MIN_VEL = "overscroll_physics_min_vel"
    const val PHYSICS_MIN_VAL = "overscroll_physics_min_val"
    const val ANIM_SPEED = "overscroll_anim_speed"
    const val LERP_MAIN_IDLE = "overscroll_lerp_main_idle"
    const val LERP_MAIN_RUN = "overscroll_lerp_main_run"
    const val COMPOSE_SCALE = "overscroll_compose_scale"
    const val INVERT_ANCHOR = "overscroll_invert_anchor"

    // Delta normalization (Compose intelligent smoothing).
    const val NORM_ENABLED = "overscroll_norm_enabled"
    const val NORM_REF_DELTA = "overscroll_norm_ref_delta"
    const val NORM_DETECT_MUL = "overscroll_norm_detect_mul"
    const val NORM_FACTOR = "overscroll_norm_factor"
    const val NORM_WINDOW = "overscroll_norm_window"
    const val NORM_RAMP = "overscroll_norm_ramp"
    const val NORM_DETECT_MODE = "overscroll_norm_detect_mode"

    // Plain keys: stored profiles and active profile name.
    const val SAVED_PROFILES = "overscroll_saved_profiles"
    const val ACTIVE_PROFILE_NAME = "overscroll_active_profile_name"

    // Factory defaults, matching the legacy resetAll() values.
    object Defaults {
        const val ENABLED = true
        const val LOGGING = false
        const val PULL = 1.5141f
        const val STIFFNESS = 148.6191f
        const val DAMPING = 0.9976f
        const val FLING = 1.3679f
        const val RESISTANCE_EXPONENT = 4.0f
        const val SCALE_MODE = 0
        const val SCALE_INTENSITY = 0.31f
        const val SCALE_INTENSITY_HORIZ = 0.3786f
        const val SCALE_LIMIT_MIN = 0.1f
        const val ANCHOR = 0.5f
        const val ZOOM_MODE = 0
        const val ZOOM_INTENSITY = 0.2f
        const val ZOOM_INTENSITY_HORIZ = 0.2f
        const val ZOOM_LIMIT_MIN = 0.1f
        const val H_SCALE_MODE = 0
        const val H_SCALE_INTENSITY = 0.2f
        const val H_SCALE_INTENSITY_HORIZ = 0.0f
        const val H_SCALE_LIMIT_MIN = 0.1f
        const val INPUT_SMOOTH = 0.5f
        const val PHYSICS_MIN_VEL = 8.0f
        const val PHYSICS_MIN_VAL = 0.6f
        const val ANIM_SPEED = 168.5232f
        const val LERP_MAIN_IDLE = 0.4f
        const val LERP_MAIN_RUN = 0.6999f
        const val COMPOSE_SCALE = 3.3299f
        const val INVERT_ANCHOR = true
        const val NORM_ENABLED = true
        const val NORM_REF_DELTA = 9.9999f
        const val NORM_DETECT_MUL = 0.0f
        const val NORM_FACTOR = 0.33f
        const val NORM_WINDOW = 2.0f
        const val NORM_RAMP = 1.0f
        const val NORM_DETECT_MODE = 1
    }
}
