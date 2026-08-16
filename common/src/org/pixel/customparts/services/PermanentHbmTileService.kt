package org.pixel.customparts.services

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean
import org.pixel.customparts.R
import org.pixel.customparts.SettingsKeys
import org.pixel.customparts.utils.AutoHbmController

class PermanentHbmTileService : TileService() {
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

                val currentlyActive = AutoHbmController.isPermanentMode(this)
                if (currentlyActive) {
                    // Disable permanent mode, also disable HBM entirely
                    AutoHbmController.setEnabled(this, false)
                    AutoHbmController.setHbmMode(this, SettingsKeys.HBM_MODE_AUTO)
                } else {
                    // Enable permanent mode
                    AutoHbmController.setEnabled(this, true)
                    AutoHbmController.setHbmMode(this, SettingsKeys.HBM_MODE_PERMANENT)
                }
            }.onFailure {
                Log.e(TAG, "Failed to toggle Permanent HBM tile", it)
            }

            mainExecutor.execute {
                toggleInProgress.set(false)
                updateTile()
            }
        }, "PixelParts-PermanentHbmTileToggle").start()
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        val supported = AutoHbmController.isSupported()
        val active = AutoHbmController.isPermanentMode(this)

        tile.label = getString(R.string.permanent_hbm_title)
        tile.subtitle = getString(
            when {
                !supported -> R.string.permanent_hbm_tile_unsupported
                active -> R.string.permanent_hbm_tile_on
                else -> R.string.permanent_hbm_tile_off
            }
        )
        tile.state = if (!supported) Tile.STATE_UNAVAILABLE else if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.updateTile()
    }

    companion object {
        private const val TAG = "PermanentHbmTileService"
    }
}
