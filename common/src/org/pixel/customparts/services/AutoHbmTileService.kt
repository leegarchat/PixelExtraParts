package org.pixel.customparts.services

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean
import org.pixel.customparts.R
import org.pixel.customparts.SettingsKeys
import org.pixel.customparts.utils.AutoHbmController

class AutoHbmTileService : TileService() {
    private val toggleInProgress = AtomicBoolean(false)

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
        if (!toggleInProgress.compareAndSet(false, true)) return

        Thread({
            runCatching {
                if (!AutoHbmController.isSupported()) return@runCatching

                val currentlyEnabled = AutoHbmController.isAutoModeEnabled(this)
                if (currentlyEnabled) {
                    // Disabling auto HBM
                    AutoHbmController.setModeEnabled(this, SettingsKeys.HBM_MODE_AUTO, false)
                } else {
                    // Enabling auto HBM also selects it over permanent HBM.
                    AutoHbmController.setModeEnabled(this, SettingsKeys.HBM_MODE_AUTO, true)
                }
            }.onFailure {
                Log.e(TAG, "Failed to toggle Auto HBM tile", it)
            }

            mainExecutor.execute {
                toggleInProgress.set(false)
                updateTile()
            }
        }, "PixelParts-AutoHbmTileToggle").start()
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        val supported = AutoHbmController.isSupported()
        val enabled = AutoHbmController.isAutoModeEnabled(this)

        tile.label = getString(R.string.auto_hbm_title)
        tile.subtitle = getString(
            when {
                !supported -> R.string.auto_hbm_tile_unsupported
                enabled && AutoHbmController.isHbmActive(this) -> R.string.auto_hbm_tile_active
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

    companion object {
        private const val TAG = "AutoHbmTileService"
    }
}
