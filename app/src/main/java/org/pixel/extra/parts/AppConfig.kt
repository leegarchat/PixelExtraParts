package org.pixel.extra.parts

import org.pixel.extra.parts.utils.HiddenApi


object AppConfig {
    val ENABLE_THERMALS: Boolean
        get() = HiddenApi.getSystemPropertyBoolean("persist.sys.pixelparts.thermal_available", false)

    // Legacy flag, always false: the project is LSPlant-only, there is no Xposed build.
    // Kept for source compatibility (external addons may reference it).
    const val IS_XPOSED = false
    const val NEEDS_ROOT_ACCESS = false
}