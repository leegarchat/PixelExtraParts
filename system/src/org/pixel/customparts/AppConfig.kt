package org.pixel.customparts

import android.os.SystemProperties

object AppConfig {
    val ENABLE_THERMALS: Boolean
        get() = SystemProperties.getBoolean("persist.sys.pixelparts.thermal_available", false)

    // Legacy flag, always false: the project is Pine-only, there is no Xposed build.
    // Kept for source compatibility (external addons may reference it).
    const val IS_XPOSED = false
    const val NEEDS_ROOT_ACCESS = false
}