package org.pixel.customparts.utils

import android.content.Context
import android.provider.Settings
import org.pixel.customparts.SettingsKeys
import org.pixel.customparts.services.AutoHbmTileService
import org.pixel.customparts.services.MainActivityTileService
import org.pixel.customparts.services.PixelPartsLogTileService
import org.pixel.customparts.services.SaturationTileService

object PixelPartsTileRefresher {
    private const val PINE_SUFFIX = "_pine"
    private const val XPOSED_SUFFIX = "_xposed"
    private const val DYNAMIC_ADDON_TILE_COUNT = 40

    private val allTileServices = listOf(
        MainActivityTileService::class.java,
        SaturationTileService::class.java,
        AutoHbmTileService::class.java,
        PixelPartsLogTileService::class.java
    )

    private val settingTileServices by lazy {
        mapOf(
            normalizeKey(SettingsKeys.SATURATION_ENABLED) to listOf(SaturationTileService::class.java),
            normalizeKey(SettingsKeys.AUTO_HBM_ENABLED) to listOf(AutoHbmTileService::class.java),
            normalizeKey(SettingsKeys.AUTO_HBM_ACTIVE) to listOf(AutoHbmTileService::class.java),
            normalizeKey(SettingsKeys.LOG_SERVICE_ENABLED) to listOf(PixelPartsLogTileService::class.java)
        )
    }

    fun requestAll(context: Context) {
        val appContext = context.applicationContext ?: context
        allTileServices.forEach { tileServiceClass ->
            TileUtils.requestTileRefresh(appContext, tileServiceClass)
        }
        boundDynamicTileServices(appContext).forEach { tileServiceClass ->
            TileUtils.requestTileRefresh(appContext, tileServiceClass)
        }
    }

    fun requestForSetting(context: Context, key: String) {
        val appContext = context.applicationContext ?: context
        settingTileServices[normalizeKey(key)]?.forEach { tileServiceClass ->
            TileUtils.requestTileRefresh(appContext, tileServiceClass)
        }
        requestDynamicForSetting(appContext, key)
    }

    private fun requestDynamicForSetting(context: Context, key: String) {
        val normalized = normalizeKey(key)
        for (slot in 1..DYNAMIC_ADDON_TILE_COUNT) {
            val prefix = "pixel_addon_tile_${slot}_"
            val enabled = Settings.Global.getInt(context.contentResolver, "${prefix}enabled", 0) == 1
            if (!enabled) continue
            val boundKey = Settings.Global.getString(context.contentResolver, "${prefix}key") ?: continue
            if (normalizeKey(boundKey) == normalized) {
                dynamicTileServiceClass(slot)?.let { TileUtils.requestTileRefresh(context, it) }
            }
        }
    }

    private fun boundDynamicTileServices(context: Context): List<Class<*>> {
        return (1..DYNAMIC_ADDON_TILE_COUNT).mapNotNull { slot ->
            val enabled = Settings.Global.getInt(context.contentResolver, "pixel_addon_tile_${slot}_enabled", 0) == 1
            if (enabled) dynamicTileServiceClass(slot) else null
        }
    }

    private fun dynamicTileServiceClass(slot: Int): Class<*>? {
        val num = slot.toString().padStart(2, '0')
        return try { Class.forName("org.pixel.customparts.services.DynamicAddonTile$num") } catch (_: Throwable) { null }
    }

    private fun normalizeKey(key: String): String {
        return key.removeSuffix(PINE_SUFFIX).removeSuffix(XPOSED_SUFFIX)
    }
}