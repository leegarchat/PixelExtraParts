package org.pixel.customparts.services

import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import org.pixel.customparts.R

abstract class SettingsToggleTileService : TileService() {
    @Volatile private var toggleInProgress = false

    protected abstract val labelResId: Int
    protected abstract val settingKey: String
    protected open val activeSubtitleResId: Int = R.string.os_status_active
    protected open val inactiveSubtitleResId: Int = R.string.os_status_disabled
    protected open val defaultEnabled: Boolean = false

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onTileAdded() {
        super.onTileAdded()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        if (toggleInProgress) return
        toggleInProgress = true

        Thread({
            runCatching {
                setEnabled(!isEnabled())
            }.onFailure {
                Log.e(javaClass.simpleName, "Failed to toggle tile", it)
            }

            mainExecutor.execute {
                toggleInProgress = false
                updateTile()
            }
        }, "PixelParts-${javaClass.simpleName}Toggle").start()
    }

    protected open fun isEnabled(): Boolean {
        return Settings.Global.getInt(contentResolver, settingKey, if (defaultEnabled) 1 else 0) == 1
    }

    protected open fun setEnabled(enabled: Boolean) {
        Settings.Global.putInt(contentResolver, settingKey, if (enabled) 1 else 0)
    }

    protected fun updateTile() {
        val tile = qsTile ?: return
        val enabled = isEnabled()

        tile.label = getString(labelResId)
        tile.subtitle = getString(if (enabled) activeSubtitleResId else inactiveSubtitleResId)
        tile.state = if (enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.updateTile()
    }
}