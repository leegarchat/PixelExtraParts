package org.pixel.customparts.activities

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.service.quicksettings.TileService
import android.util.Log
import org.pixel.customparts.services.SaturationTileService

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
            SaturationTileService::class.java.name -> Intent(this, SaturationTileDialogActivity::class.java)
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