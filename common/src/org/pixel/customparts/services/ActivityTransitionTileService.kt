package org.pixel.customparts.services

import android.provider.Settings
import org.pixel.customparts.R
import org.pixel.customparts.SettingsKeys

class ActivityTransitionTileService : SettingsToggleTileService() {
    override val labelResId: Int
        get() = R.string.anim_transition_title
    override val settingKey: String
        get() = SettingsKeys.ACTIVITY_OPEN_TRANSITION

    override fun isEnabled(): Boolean {
        return getMode(SettingsKeys.ACTIVITY_OPEN_TRANSITION) != MODE_DISABLED ||
            getMode(SettingsKeys.ACTIVITY_CLOSE_TRANSITION) != MODE_DISABLED
    }

    override fun setEnabled(enabled: Boolean) {
        if (enabled) {
            val lastOpen = getMode(SettingsKeys.ACTIVITY_OPEN_TRANSITION_LAST)
            val lastClose = getMode(SettingsKeys.ACTIVITY_CLOSE_TRANSITION_LAST)
            val hasLastMode = lastOpen != MODE_DISABLED || lastClose != MODE_DISABLED
            putMode(SettingsKeys.ACTIVITY_OPEN_TRANSITION, if (hasLastMode) lastOpen else DEFAULT_TRANSITION_MODE)
            putMode(SettingsKeys.ACTIVITY_CLOSE_TRANSITION, if (hasLastMode) lastClose else DEFAULT_TRANSITION_MODE)
        } else {
            val openMode = getMode(SettingsKeys.ACTIVITY_OPEN_TRANSITION)
            val closeMode = getMode(SettingsKeys.ACTIVITY_CLOSE_TRANSITION)
            if (openMode != MODE_DISABLED) putMode(SettingsKeys.ACTIVITY_OPEN_TRANSITION_LAST, openMode)
            if (closeMode != MODE_DISABLED) putMode(SettingsKeys.ACTIVITY_CLOSE_TRANSITION_LAST, closeMode)
            putMode(SettingsKeys.ACTIVITY_OPEN_TRANSITION, MODE_DISABLED)
            putMode(SettingsKeys.ACTIVITY_CLOSE_TRANSITION, MODE_DISABLED)
        }
    }

    private fun getMode(key: String): Int {
        return Settings.Global.getInt(contentResolver, key, MODE_DISABLED)
    }

    private fun putMode(key: String, mode: Int) {
        Settings.Global.putInt(contentResolver, key, mode)
    }

    private companion object {
        const val MODE_DISABLED = 0
        const val DEFAULT_TRANSITION_MODE = 50
    }
}