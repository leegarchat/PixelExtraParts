package org.pixel.customparts.services

import org.pixel.customparts.R
import org.pixel.customparts.SettingsKeys

class MagnifierTileService : SettingsToggleTileService() {
    override val labelResId: Int
        get() = R.string.magnifier_section_title
    override val settingKey: String
        get() = SettingsKeys.MAGNIFIER_CUSTOM_ENABLED
}