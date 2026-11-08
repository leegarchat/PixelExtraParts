package org.pixel.customparts.services

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import org.pixel.customparts.R
import org.pixel.customparts.utils.SaturationController

class SaturationTileService : TileService() {
    @Volatile private var toggleInProgress = false

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
                SaturationController.setEnabled(this, !SaturationController.isEnabled(this))
            }.onFailure {
                Log.e(TAG, "Failed to toggle saturation tile", it)
            }

            mainExecutor.execute {
                toggleInProgress = false
                updateTile()
            }
        }, "PixelParts-SaturationTileToggle").start()
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        val enabled = SaturationController.isEnabled(this)

        tile.label = getString(R.string.saturation_title)
        tile.subtitle = getString(if (enabled) R.string.saturation_tile_active else R.string.saturation_tile_inactive)
        tile.state = if (enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.updateTile()
    }

    companion object {
        private const val TAG = "SaturationTileService"
    }
}