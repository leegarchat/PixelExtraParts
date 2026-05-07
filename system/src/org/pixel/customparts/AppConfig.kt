package org.pixel.customparts

import android.os.SystemProperties

object AppConfig {
    val ENABLE_THERMALS: Boolean
        get() = SystemProperties.getBoolean("persist.sys.pixelparts.thermal_available", false)

    const val IS_XPOSED = false
    const val NEEDS_ROOT_ACCESS = false
}