package org.pixel.customparts.services

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import org.pixel.customparts.R
import org.pixel.customparts.utils.AutoHbmController

class AutoHbmTileService : TileService() {
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
        AutoHbmController.setEnabled(this, !AutoHbmController.isEnabled(this))
        updateTile()
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        val supported = AutoHbmController.isSupported()
        val enabled = AutoHbmController.isEnabled(this)

        tile.label = getString(R.string.auto_hbm_title)
        tile.subtitle = getString(
            when {
                !supported -> R.string.auto_hbm_tile_unsupported
                AutoHbmController.isHbmActive(this) -> R.string.auto_hbm_tile_active
                enabled -> R.string.auto_hbm_tile_monitoring
                else -> R.string.auto_hbm_tile_off
            }
        )
        tile.state = when {
            !supported -> Tile.STATE_UNAVAILABLE
            enabled -> Tile.STATE_ACTIVE
            else -> Tile.STATE_INACTIVE
        }
        tile.updateTile()
    }
}