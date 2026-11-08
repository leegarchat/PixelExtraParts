package org.pixel.extra.parts.display

// Canonical Settings keys for the Display section.
// Names are byte-identical to the legacy project on purpose: firmware-side
// services keep reading the same values. A dedicated pass renames them later,
// so no key is renamed here. Telemetry-only keys (last lux, active-since,
// original brightness snapshots) are intentionally excluded: this UI only
// reads and writes tuning knobs.
object DisplayKeys {
    // Saturation.
    const val SATURATION_ENABLED = "pixelparts_saturation_enabled"
    const val SATURATION_PERCENT = "pixelparts_saturation_percent"

    // AutoHBM tuning knobs.
    const val AUTO_HBM_ENABLED = "pixelparts_auto_hbm_enabled"
    const val AUTO_HBM_THRESHOLD = "pixelparts_auto_hbm_threshold"
    const val AUTO_HBM_ENABLE_TIME = "pixelparts_auto_hbm_enable_time"
    const val AUTO_HBM_DISABLE_TIME = "pixelparts_auto_hbm_disable_time"
    const val AUTO_HBM_SMOOTH_RAMP_ENABLED = "pixelparts_auto_hbm_smooth_ramp_enabled"
    const val AUTO_HBM_RAMP_TIME_MS = "pixelparts_auto_hbm_ramp_time_ms"
    const val AUTO_HBM_MAX_ACTIVE_TIME = "pixelparts_auto_hbm_max_active_time"
    const val AUTO_HBM_COOLDOWN_TIME = "pixelparts_auto_hbm_cooldown_time"
    const val AUTO_HBM_CHECK_INTERVAL_MS = "pixelparts_auto_hbm_check_interval_ms"
    const val AUTO_HBM_TEMPERATURE_LIMIT = "pixelparts_auto_hbm_temperature_limit"
    const val AUTO_HBM_MODE = "pixelparts_auto_hbm_mode"
    const val AUTO_HBM_BRIGHTNESS_LOCK = "pixelparts_auto_hbm_brightness_lock"

    // Double tap to wake.
    const val DTW_ENABLED = "pixelparts_dtw_enabled"
    const val DTW_TAP_COUNT = "pixelparts_dtw_tap_count"
    const val DTW_TAP_TIMEOUT_MS = "pixelparts_dtw_tap_timeout_ms"
    const val DTW_COORDINATE_CHECK = "pixelparts_dtw_coordinate_check"
    const val DTW_MAX_DISTANCE_DP = "pixelparts_dtw_max_distance_dp"

    // Auto lock.
    const val AUTO_LOCK_ENABLED = "pixelparts_auto_lock_enabled"
    const val AUTO_LOCK_MODE = "pixelparts_auto_lock_mode"
    const val AUTO_LOCK_TIMEOUT_SECONDS = "pixelparts_auto_lock_timeout_seconds"
    const val AUTO_LOCK_PAUSE_MEDIA = "pixelparts_auto_lock_pause_media"

    // HBM mode values.
    const val HBM_MODE_AUTO = 0
    const val HBM_MODE_PERMANENT = 1

    // Auto-lock mode values.
    const val AUTO_LOCK_MODE_OFF = 0
    const val AUTO_LOCK_MODE_ALWAYS = 1
    const val AUTO_LOCK_MODE_BLOCKER_ONLY = 2

    // Saturation slider bounds.
    const val SATURATION_MIN = 0
    const val SATURATION_DEFAULT = 100
    const val SATURATION_MAX = 200

    // AutoHBM slider bounds.
    const val HBM_THRESHOLD_DEFAULT = 20000
    const val HBM_THRESHOLD_MIN = 100
    const val HBM_THRESHOLD_MAX = 60000
    const val HBM_DELAY_DEFAULT_ON = 0
    const val HBM_DELAY_DEFAULT_OFF = 1
    const val HBM_DELAY_MIN = 0
    const val HBM_DELAY_MAX = 10
    const val HBM_RAMP_DEFAULT_MS = 800
    const val HBM_RAMP_MIN_MS = 100
    const val HBM_RAMP_MAX_MS = 5000
    const val HBM_MAX_ACTIVE_DEFAULT_S = 120
    const val HBM_COOLDOWN_DEFAULT_S = 60
    const val HBM_TIMEOUT_MIN_S = 10
    const val HBM_TIMEOUT_MAX_S = 2000
    const val HBM_CHECK_INTERVAL_DEFAULT_MS = 250
    const val HBM_CHECK_INTERVAL_MIN_MS = 30
    const val HBM_CHECK_INTERVAL_MAX_MS = 10000
    const val HBM_TEMP_DEFAULT_C = 50
    const val HBM_TEMP_MIN_C = 30
    const val HBM_TEMP_MAX_C = 80

    // Double-tap slider bounds.
    const val DTW_TAP_COUNT_DEFAULT = 2
    const val DTW_TAP_COUNT_MIN = 1
    const val DTW_TAP_COUNT_MAX = 3
    const val DTW_TIMEOUT_DEFAULT_MS = 300
    const val DTW_TIMEOUT_MIN_MS = 100
    const val DTW_TIMEOUT_MAX_MS = 2000
    const val DTW_MAX_DISTANCE_DEFAULT_DP = 50
    const val DTW_MAX_DISTANCE_MIN_DP = 10
    const val DTW_MAX_DISTANCE_MAX_DP = 500

    // Auto-lock slider bounds.
    const val AUTO_LOCK_TIMEOUT_DEFAULT_S = 300
    const val AUTO_LOCK_TIMEOUT_MIN_S = 10
    const val AUTO_LOCK_TIMEOUT_MAX_S = 3600
}
