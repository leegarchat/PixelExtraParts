package org.pixel.customparts.services

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import org.pixel.customparts.R
import org.pixel.customparts.utils.ThermalProfileController

class ThermalManagerTileService : TileService() {
    @Volatile private var toggleInProgress = false

    override fun onStartListening() {
        super.onStartListening()
        refreshTile()
    }

    override fun onTileAdded() {
        super.onTileAdded()
        refreshTile()
    }

    override fun onClick() {
        super.onClick()
        if (toggleInProgress) return
        toggleInProgress = true

        Thread({
            ThermalProfileController.cycleTileProfileQueue(this)
            val tileState = loadTileState()
            mainExecutor.execute {
                toggleInProgress = false
                applyTileState(tileState)
            }
        }, "PixelParts-ThermalTileToggle").start()
    }

    private fun refreshTile() {
        Thread({
            val tileState = loadTileState()
            mainExecutor.execute { applyTileState(tileState) }
        }, "PixelParts-ThermalTileRefresh").start()
    }

    private fun loadTileState(): TileState {
        val queue = ThermalProfileController.readTileProfileQueue(this)
        val currentConfig = ThermalProfileController.readProfileMap().globalConfig
            .ifBlank { ThermalProfileController.STOCK_CONFIG_ID }
        return TileState(
            hasQueue = queue.isNotEmpty(),
            subtitle = if (queue.isEmpty()) {
                getString(R.string.thermal_manager_tile_queue_empty)
            } else {
                thermalProfileLabel(currentConfig)
            }
        )
    }

    private fun applyTileState(tileState: TileState) {
        val tile = qsTile ?: return
        tile.label = getString(R.string.thermal_manager_title)
        tile.subtitle = tileState.subtitle
        tile.state = if (tileState.hasQueue) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.updateTile()
    }

    private fun thermalProfileLabel(configId: String): String {
        return if (ThermalProfileController.normalizeConfigId(configId) == ThermalProfileController.STOCK_CONFIG_ID) {
            getString(R.string.thermal_manager_stock_profile)
        } else {
            ThermalProfileController.displayName(configId)
        }
    }

    private data class TileState(
        val hasQueue: Boolean,
        val subtitle: String
    )
}