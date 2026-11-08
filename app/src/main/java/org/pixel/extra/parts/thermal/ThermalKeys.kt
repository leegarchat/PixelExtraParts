package org.pixel.extra.parts.thermal

// Legacy key registry for the thermals section. Names are frozen: the HAL,
// the QS tile and already-shipped builds read these exact keys, so nothing
// here may be renamed. All thermals state flows through this one object.
object ThermalKeys {
    // Settings.Global queue cycled by the QS tile (JSON array of preset ids).
    const val TILE_PROFILE_QUEUE = "pixelparts_thermal_tile_profile_queue"

    // Settings.Global index of the last tile-applied queue entry.
    const val TILE_PROFILE_QUEUE_INDEX = "pixelparts_thermal_tile_profile_queue_index"

    // Upper bound for the tile queue, matching the legacy controller.
    const val MAX_TILE_QUEUE_SIZE = 5

    // System properties consumed by the thermal HAL wiring.
    const val PROP_CONFIG_CURRENT = "persist.sys.pixelparts.thermal_config"
    const val PROP_CONFIG_REQUEST = "persist.sys.pixelparts.thermal_config_request"
    const val PROP_BATTERY = "persist.sys.pixelparts.battery"
    const val PROP_SOC = "persist.sys.pixelparts.soc"

    // Read-only mirror published by the vendor thermal HAL (info only).
    const val PROP_VENDOR_THERMAL_CONFIG = "vendor.thermal.config"

    // Feature gate published by init.
    const val PROP_THERMAL_AVAILABLE = "persist.sys.pixelparts.thermal_available"

    // Built-in stock preset identifiers.
    const val STOCK_CONFIG_ID = "__stock__"
    const val STOCK_CONFIG_VALUE = "thermal_info_config.json"
}
