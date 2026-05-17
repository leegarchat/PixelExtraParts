package org.pixel.customparts.utils

import android.content.Context
import org.pixel.customparts.SettingsKeys
import org.pixel.customparts.activities.DoubleTapManager
import org.pixel.customparts.activities.OverscrollManager
import org.pixel.customparts.services.ActivityTransitionTileService
import org.pixel.customparts.services.AutoHbmTileService
import org.pixel.customparts.services.ChargingInfoTileService
import org.pixel.customparts.services.Dt2sTileService
import org.pixel.customparts.services.Dt2wTileService
import org.pixel.customparts.services.GestureBarTileService
import org.pixel.customparts.services.MagnifierTileService
import org.pixel.customparts.services.MainActivityTileService
import org.pixel.customparts.services.OverscrollTileService
import org.pixel.customparts.services.PixelPartsLogTileService
import org.pixel.customparts.services.SaturationTileService
import org.pixel.customparts.services.ThermalManagerTileService

object PixelPartsTileRefresher {
    private const val PINE_SUFFIX = "_pine"
    private const val XPOSED_SUFFIX = "_xposed"

    private val allTileServices = listOf(
        MainActivityTileService::class.java,
        SaturationTileService::class.java,
        AutoHbmTileService::class.java,
        Dt2wTileService::class.java,
        Dt2sTileService::class.java,
        OverscrollTileService::class.java,
        GestureBarTileService::class.java,
        ChargingInfoTileService::class.java,
        MagnifierTileService::class.java,
        ActivityTransitionTileService::class.java,
        ThermalManagerTileService::class.java,
        PixelPartsLogTileService::class.java
    )

    private val settingTileServices by lazy {
        mapOf(
            normalizeKey(SettingsKeys.SATURATION_ENABLED) to listOf(SaturationTileService::class.java),
            normalizeKey(SettingsKeys.AUTO_HBM_ENABLED) to listOf(AutoHbmTileService::class.java),
            normalizeKey(SettingsKeys.AUTO_HBM_ACTIVE) to listOf(AutoHbmTileService::class.java),
            normalizeKey(DoubleTapManager.KEY_DT2W_ENABLE) to listOf(Dt2wTileService::class.java),
            normalizeKey(DoubleTapManager.KEY_DT2S_ENABLE) to listOf(Dt2sTileService::class.java),
            normalizeKey(OverscrollManager.KEY_ENABLED) to listOf(OverscrollTileService::class.java),
            normalizeKey(SettingsKeys.GESTURE_BAR_ENABLED) to listOf(GestureBarTileService::class.java),
            normalizeKey(SettingsKeys.BATTERY_INFO_ENABLE) to listOf(ChargingInfoTileService::class.java),
            normalizeKey(SettingsKeys.MAGNIFIER_CUSTOM_ENABLED) to listOf(MagnifierTileService::class.java),
            normalizeKey(SettingsKeys.ACTIVITY_OPEN_TRANSITION) to listOf(ActivityTransitionTileService::class.java),
            normalizeKey(SettingsKeys.ACTIVITY_CLOSE_TRANSITION) to listOf(ActivityTransitionTileService::class.java),
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

    fun requestForSetting(context: Context, key: String) {
        val appContext = context.applicationContext ?: context
        settingTileServices[normalizeKey(key)]?.forEach { tileServiceClass ->
            TileUtils.requestTileRefresh(appContext, tileServiceClass)
        }
    }

    private fun normalizeKey(key: String): String {
        return key.removeSuffix(PINE_SUFFIX).removeSuffix(XPOSED_SUFFIX)
    }
}