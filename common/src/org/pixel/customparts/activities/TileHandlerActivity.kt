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
import org.pixel.customparts.services.AutoHbmTileService
import org.pixel.customparts.services.MainActivityTileService
import org.pixel.customparts.services.OverscrollTileService
import org.pixel.customparts.services.PixelPartsLogTileService
import org.pixel.customparts.services.SaturationTileService
import org.pixel.customparts.services.ThermalManagerTileService

class TileHandlerActivity : Activity() {
    private val dynamicAddonTileRange = 1..40

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

        // Handle dynamic addon tiles (DynamicAddonTile01..40)
        val className = tile?.className ?: ""
        if (className.startsWith("org.pixel.customparts.services.DynamicAddonTile")) {
            val slotStr = className.removePrefix("org.pixel.customparts.services.DynamicAddonTile")
            val slot = slotStr.toIntOrNull() ?: 0
            if (slot in dynamicAddonTileRange) {
                val prefix = "pixel_addon_tile_${slot}_"
                val addonId = Settings.Global.getString(contentResolver, "${prefix}addon_id") ?: ""
                val pageId = Settings.Global.getString(contentResolver, "${prefix}page_id") ?: ""
                if (addonId.isNotBlank()) {
                    return Intent(this, AddonPageActivity::class.java).apply {
                        putExtra(AddonPageActivity.EXTRA_ADDON_ID, addonId)
                        if (pageId.isNotBlank()) putExtra(AddonPageActivity.EXTRA_PAGE_ID, pageId)
                        putExtra(AddonPageActivity.EXTRA_USE_SYSTEM_ACTIVITY_ANIMATION, true)
                        putExtra(AddonPageActivity.EXTRA_INCLUDE_TARGET_ACTIVITY_ENTRIES, true)
                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                }
            }
        }

        val target = when (className) {
            SaturationTileService::class.java.name -> Intent(this, SaturationActivity::class.java)
            AutoHbmTileService::class.java.name -> Intent(this, AutoHbmActivity::class.java)
            OverscrollTileService::class.java.name -> Intent(this, OverscrollActivity::class.java)
            ThermalManagerTileService::class.java.name -> Intent(this, ThermalConfigManagerActivity::class.java)
            MainActivityTileService::class.java.name -> Intent(this, MainActivity::class.java)
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