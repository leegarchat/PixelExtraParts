package org.pixel.customparts.services

import org.pixel.customparts.R
import org.pixel.customparts.activities.DoubleTapManager

class Dt2sTileService : SettingsToggleTileService() {
    override val labelResId: Int
        get() = R.string.dt2s_title
    override val settingKey: String
        get() = DoubleTapManager.KEY_DT2S_ENABLE
}