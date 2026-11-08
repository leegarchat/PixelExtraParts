package org.pixel.customparts

import android.os.SystemProperties

object AppConfig {
    val ENABLE_THERMALS: Boolean
        get() = SystemProperties.getBoolean("persist.sys.pixelparts.thermal_available", false)
}