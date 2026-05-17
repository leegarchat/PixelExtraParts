package org.pixel.customparts.services

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import org.pixel.customparts.R
import org.pixel.customparts.utils.PixelPartsLogController

class PixelPartsLogTileService : TileService() {
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
                if (PixelPartsLogController.isServiceRunning(this)) {
                    PixelPartsLogController.stopLogging(this)
                } else {
                    PixelPartsLogController.startLogging(this)
                }
            }.onFailure {
                Log.e(TAG, "Failed to toggle PixelParts log tile", it)
            }

            mainExecutor.execute {
                toggleInProgress = false
                updateTile()
            }
        }, "PixelParts-LogTileToggle").start()
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        val running = PixelPartsLogController.isServiceRunning(this)

        tile.label = getString(R.string.sysui_log_service_title)
        tile.subtitle = getString(
            if (running) R.string.sysui_log_service_status_running else R.string.sysui_log_service_status_stopped
        )
        tile.state = if (running) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.updateTile()
    }

    companion object {
        private const val TAG = "PixelPartsLogTileService"
    }
}