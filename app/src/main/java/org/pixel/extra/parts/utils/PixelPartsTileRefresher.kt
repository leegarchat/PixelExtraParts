package org.pixel.extra.parts.utils

import android.content.Context
import android.provider.Settings
import org.pixel.extra.parts.SettingsKeys
import org.pixel.extra.parts.activities.OverscrollManager
import org.pixel.extra.parts.services.AutoHbmTileService
import org.pixel.extra.parts.services.MainActivityTileService
import org.pixel.extra.parts.services.OverscrollTileService
import org.pixel.extra.parts.services.PermanentHbmTileService
import org.pixel.extra.parts.services.PixelPartsLogTileService
import org.pixel.extra.parts.services.SaturationTileService
import org.pixel.extra.parts.services.ThermalManagerTileService

object PixelPartsTileRefresher {
    private const val LEGACY_ENGINE_SUFFIX = "_pine"
    private const val ACTIVE_SUFFIX = "_lsplant"
    private const val XPOSED_SUFFIX = "_xposed"
    private val allTileServices = listOf(
        MainActivityTileService::class.java,
        SaturationTileService::class.java,
        AutoHbmTileService::class.java,
        PermanentHbmTileService::class.java,
        OverscrollTileService::class.java,
        ThermalManagerTileService::class.java,
        PixelPartsLogTileService::class.java
    )

    private val hbmTileServices = listOf(
        AutoHbmTileService::class.java,
        PermanentHbmTileService::class.java
    )

    private val settingTileServices by lazy {
        mapOf(
            normalizeKey(SettingsKeys.SATURATION_ENABLED) to listOf(SaturationTileService::class.java),
            normalizeKey(SettingsKeys.AUTO_HBM_ENABLED) to listOf(
                AutoHbmTileService::class.java,
                PermanentHbmTileService::class.java
            ),
            normalizeKey(SettingsKeys.AUTO_HBM_MODE) to listOf(
                AutoHbmTileService::class.java,
                PermanentHbmTileService::class.java
            ),
            normalizeKey(SettingsKeys.AUTO_HBM_ACTIVE) to listOf(AutoHbmTileService::class.java),
            normalizeKey(OverscrollManager.KEY_ENABLED) to listOf(OverscrollTileService::class.java),
            normalizeKey(SettingsKeys.THERMAL_TILE_PROFILE_QUEUE) to listOf(ThermalManagerTileService::class.java),
            normalizeKey(SettingsKeys.THERMAL_TILE_PROFILE_QUEUE_INDEX) to listOf(ThermalManagerTileService::class.java),
            normalizeKey(SettingsKeys.LOG_SERVICE_ENABLED) to listOf(PixelPartsLogTileService::class.java)
        )
    }

    fun requestAll(context: Context) {
        val appContext = context.applicationContext ?: context
        allTileServices.forEach { tileServiceClass ->
            TileUtils.requestTileRefresh(appContext, tileServiceClass)
        }
    }

    fun refreshHbmTiles(context: Context) {
        val appContext = context.applicationContext ?: context
        hbmTileServices.forEach { tileServiceClass ->
            TileUtils.requestTileRefresh(appContext, tileServiceClass)
        }
    }

    fun requestForSetting(context: Context, key: String) {
        val appContext = context.applicationContext ?: context
        settingTileServices[normalizeKey(key)]?.forEach { tileServiceClass ->
            TileUtils.requestTileRefresh(appContext, tileServiceClass)
        }
    }

    private fun normalizeKey(key: String): String {
        return key.removeSuffix(ACTIVE_SUFFIX).removeSuffix(LEGACY_ENGINE_SUFFIX).removeSuffix(XPOSED_SUFFIX)
    }
}
