package org.pixel.extra.parts.services

import org.pixel.extra.parts.R
import org.pixel.extra.parts.activities.OverscrollManager

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