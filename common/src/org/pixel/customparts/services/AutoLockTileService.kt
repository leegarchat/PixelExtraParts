package org.pixel.customparts.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.concurrent.atomic.AtomicBoolean
import org.pixel.customparts.R
import org.pixel.customparts.utils.AutoLockController

class AutoLockTileService : TileService() {
    private val toggleInProgress = AtomicBoolean(false)
    private var stateReceiver: BroadcastReceiver? = null

    override fun onStartListening() {
        super.onStartListening()
        registerStateReceiver()
        updateTile()
    }

    override fun onStopListening() {
        unregisterStateReceiver()
        super.onStopListening()
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
                val nextMode = when (AutoLockController.getMode(this)) {
                    AutoLockController.MODE_OFF -> AutoLockController.MODE_ALWAYS
                    AutoLockController.MODE_ALWAYS -> AutoLockController.MODE_BLOCKER_ONLY
                    else -> AutoLockController.MODE_OFF
                }
                AutoLockController.setMode(this, nextMode)
            }.onFailure {
                Log.e(TAG, "Failed to toggle AutoLock tile", it)
            }

            mainExecutor.execute {
                toggleInProgress.set(false)
                updateTile()
            }
        }, "PixelParts-AutoLockTileToggle").start()
    }

    private fun registerStateReceiver() {
        if (stateReceiver != null) return
        stateReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                updateTile()
            }
        }
        ContextCompat.registerReceiver(
            this,
            stateReceiver,
            IntentFilter(AutoLockController.ACTION_STATE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    private fun unregisterStateReceiver() {
        stateReceiver?.let { runCatching { unregisterReceiver(it) } }
        stateReceiver = null
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        val mode = AutoLockController.getMode(this)
        val enabled = mode != AutoLockController.MODE_OFF
        val timeoutSeconds = AutoLockController.getTimeoutSeconds(this)

        tile.label = getString(R.string.auto_lock_title)
        tile.subtitle = when (mode) {
            AutoLockController.MODE_ALWAYS -> getString(
                R.string.auto_lock_tile_always,
                AutoLockController.formatTimeout(this, timeoutSeconds)
            )
            AutoLockController.MODE_BLOCKER_ONLY -> getString(
                R.string.auto_lock_tile_blocker_only,
                AutoLockController.formatTimeout(this, timeoutSeconds)
            )
            else -> getString(R.string.auto_lock_tile_off)
        }
        tile.state = if (enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.updateTile()
    }

    companion object {
        private const val TAG = "AutoLockTileService"
    }
}
