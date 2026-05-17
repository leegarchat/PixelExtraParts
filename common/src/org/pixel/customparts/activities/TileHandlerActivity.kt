package org.pixel.customparts.activities

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.service.quicksettings.TileService
import android.util.Log
import org.pixel.customparts.MainActivity
import org.pixel.customparts.services.ActivityTransitionTileService
import org.pixel.customparts.services.AutoHbmTileService
import org.pixel.customparts.services.ChargingInfoTileService
import org.pixel.customparts.services.Dt2sTileService
import org.pixel.customparts.services.Dt2wTileService
import org.pixel.customparts.services.GestureBarTileService
import org.pixel.customparts.services.MainActivityTileService
import org.pixel.customparts.services.MagnifierTileService
import org.pixel.customparts.services.OverscrollTileService
import org.pixel.customparts.services.PixelPartsLogTileService
import org.pixel.customparts.services.SaturationTileService
import org.pixel.customparts.services.ThermalManagerTileService

class TileHandlerActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            if (TileService.ACTION_QS_TILE_PREFERENCES == intent.action) {
                startActivity(resolvePreferencesIntent(intent))
            }
        } catch (e: Throwable) {
            Log.e("TileHandlerActivity", "Error handling QS tile preferences", e)
        } finally {
            finish()
        }
    }

    private fun resolvePreferencesIntent(source: Intent): Intent {
        val tile = source.getParcelableExtra(Intent.EXTRA_COMPONENT_NAME) as? ComponentName
        val target = when (tile?.className) {
            SaturationTileService::class.java.name -> Intent(this, SaturationActivity::class.java)
            AutoHbmTileService::class.java.name -> Intent(this, AutoHbmActivity::class.java)
            Dt2wTileService::class.java.name,
            Dt2sTileService::class.java.name -> Intent(this, DoubleTapActivity::class.java)
            OverscrollTileService::class.java.name -> Intent(this, OverscrollActivity::class.java)
            MainActivityTileService::class.java.name -> Intent(this, MainActivity::class.java)
            GestureBarTileService::class.java.name -> Intent(this, GestureBarSettingsActivity::class.java)
            ChargingInfoTileService::class.java.name -> Intent(this, LockscreenSettingsActivity::class.java)
            MagnifierTileService::class.java.name -> Intent(this, MagnifierSettingsActivity::class.java)
            ActivityTransitionTileService::class.java.name -> Intent(this, ActivityTransitionActivity::class.java)
            ThermalManagerTileService::class.java.name -> Intent(this, ThermalConfigManagerActivity::class.java)
            PixelPartsLogTileService::class.java.name -> Intent(this, SystemUISettingsActivity::class.java)
            else -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", tile?.packageName ?: packageName, null)
            }
        }

        return target.addFlags(
            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_CLEAR_TASK or
                Intent.FLAG_ACTIVITY_NEW_TASK
        )
    }
}