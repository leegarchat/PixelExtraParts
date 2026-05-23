package org.pixel.customparts.services

import org.pixel.customparts.R
import org.pixel.customparts.activities.OverscrollManager

class OverscrollTileService : SettingsToggleTileService() {
    override val labelResId: Int
        get() = R.string.os_title_activity
    override val settingKey: String
        get() = OverscrollManager.KEY_ENABLED
    override val defaultEnabled: Boolean
        get() = true

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        OverscrollManager.clearActiveProfile(this)
    }
}