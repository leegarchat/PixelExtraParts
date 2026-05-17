package org.pixel.customparts.services

import org.pixel.customparts.R
import org.pixel.customparts.SettingsKeys

class ChargingInfoTileService : SettingsToggleTileService() {
    override val labelResId: Int
        get() = R.string.sysui_charging_info_title
    override val settingKey: String
        get() = SettingsKeys.BATTERY_INFO_ENABLE
}