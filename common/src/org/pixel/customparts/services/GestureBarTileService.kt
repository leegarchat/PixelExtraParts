package org.pixel.customparts.services

import org.pixel.customparts.R
import org.pixel.customparts.SettingsKeys

class GestureBarTileService : SettingsToggleTileService() {
    override val labelResId: Int
        get() = R.string.sysui_gesture_bar_title
    override val settingKey: String
        get() = SettingsKeys.GESTURE_BAR_ENABLED
}