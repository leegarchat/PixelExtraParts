package org.pixel.customparts.services

import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import org.pixel.customparts.utils.PixelPartsTileRefresher

/**
 * Base class for dynamic addon tiles (slots 1-40).
 * Configuration is stored in Settings.Global:
 *   pixel_addon_tile_{slot}_enabled   — 1 if tile is bound to an addon
 *   pixel_addon_tile_{slot}_key       — Settings.Global key to toggle/cycle
 *   pixel_addon_tile_{slot}_mode      — "toggle" (0/1) or "carousel" (cycle through values)
 *   pixel_addon_tile_{slot}_values    — comma-separated carousel values (for carousel mode)
 *   pixel_addon_tile_{slot}_labels    — comma-separated labels for each carousel value
 *   pixel_addon_tile_{slot}_label     — tile label text
 *   pixel_addon_tile_{slot}_addon_id  — owning addon id
 *   pixel_addon_tile_{slot}_page_id   — page to open on long-press (optional)
 *   pixel_addon_tile_{slot}_summary_on  — subtitle when active/on
 *   pixel_addon_tile_{slot}_summary_off — subtitle when inactive/off
 */
abstract class DynamicAddonTileService : TileService() {

    protected abstract val slotIndex: Int
    @Volatile private var toggleInProgress = false

    private val prefix: String get() = "pixel_addon_tile_${slotIndex}_"

    private data class TileConfig(
        val isBound: Boolean,
        val settingKey: String,
        val mode: String,
        val label: String,
        val summaryOn: String,
        val summaryOff: String,
        val carouselValues: List<String>,
        val carouselLabels: List<String>
    )

    private fun readConfig(): TileConfig {
        val cr = contentResolver
        fun readString(suffix: String, default: String = ""): String {
            return Settings.Global.getString(cr, prefix + suffix) ?: default
        }
        fun readInt(suffix: String, default: Int = 0): Int {
            return Settings.Global.getInt(cr, prefix + suffix, default)
        }
        return TileConfig(
            isBound = readInt("enabled") == 1,
            settingKey = readString("key"),
            mode = readString("mode", "toggle"),
            label = readString("label", "Tile $slotIndex"),
            summaryOn = readString("summary_on", "On"),
            summaryOff = readString("summary_off", "Off"),
            carouselValues = readString("values").split(",").filter { it.isNotBlank() },
            carouselLabels = readString("labels").split(",").filter { it.isNotBlank() }
        )
    }

    override fun onStartListening() {
        super.onStartListening()
        updateTile(readConfig())
    }

    override fun onTileAdded() {
        super.onTileAdded()
        updateTile(readConfig())
    }

    override fun onClick() {
        super.onClick()
        val config = readConfig()
        if (!config.isBound || config.settingKey.isBlank()) {
            updateTile(config)
            return
        }
        if (toggleInProgress) return
        toggleInProgress = true

        Thread({
            var nextValue: String? = null
            runCatching {
                nextValue = when (config.mode) {
                    "carousel" -> cycleCarousel(config)
                    else -> toggleKey(config)
                }
                PixelPartsTileRefresher.requestForSetting(this, config.settingKey)
            }.onFailure { e ->
                Log.e("DynamicTile$slotIndex", "onClick failed", e)
            }

            mainExecutor.execute {
                toggleInProgress = false
                updateTile(readConfig(), nextValue)
            }
        }, "PixelParts-DynamicTile${slotIndex}Toggle").start()
    }

    private fun toggleKey(config: TileConfig): String {
        val current = Settings.Global.getInt(contentResolver, config.settingKey, 0)
        val next = if (current == 0) 1 else 0
        Settings.Global.putInt(contentResolver, config.settingKey, next)
        return next.toString()
    }

    private fun cycleCarousel(config: TileConfig): String {
        val values = config.carouselValues
        if (values.isEmpty()) return toggleKey(config)
        val current = Settings.Global.getString(contentResolver, config.settingKey)
        val currentIndex = current?.let { values.indexOf(it) } ?: -1
        val nextIndex = if (currentIndex < 0 || currentIndex >= values.size - 1) 0 else currentIndex + 1
        val next = values[nextIndex]
        Settings.Global.putString(contentResolver, config.settingKey, next)
        return next
    }

    private fun updateTile(config: TileConfig, currentValue: String? = null) {
        val tile = qsTile ?: return

        if (!config.isBound) {
            tile.label = "Slot $slotIndex"
            tile.subtitle = "Not bound"
            tile.state = Tile.STATE_UNAVAILABLE
            tile.updateTile()
            return
        }

        tile.label = config.label

        when (config.mode) {
            "carousel" -> {
                val values = config.carouselValues
                val labels = config.carouselLabels
                val current = currentValue ?: Settings.Global.getString(contentResolver, config.settingKey) ?: ""
                val idx = values.indexOf(current)
                tile.subtitle = if (idx >= 0 && idx < labels.size) labels[idx]
                    else if (idx >= 0) values[idx]
                    else config.summaryOff
                tile.state = if (current.isNotBlank() && current != "0" && current != "off") Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            }
            else -> {
                val enabled = currentValue?.let { it == "1" }
                    ?: (Settings.Global.getInt(contentResolver, config.settingKey, 0) == 1)
                tile.subtitle = if (enabled) config.summaryOn else config.summaryOff
                tile.state = if (enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            }
        }
        tile.updateTile()
    }
}
