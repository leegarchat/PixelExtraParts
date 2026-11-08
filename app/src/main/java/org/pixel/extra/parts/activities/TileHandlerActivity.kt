package org.pixel.extra.parts.activities

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.service.quicksettings.TileService
import android.util.Log
import org.pixel.extra.parts.MainActivity

// Transparent dispatcher for QS tile long-press preferences.
// Single-activity world: every tile preference opens the home catalog for now;
// per-tile deep links into the back stack are a TODO (see report).
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
        val tile = source.getParcelableExtra(Intent.EXTRA_COMPONENT_NAME, ComponentName::class.java)

        val className = tile?.className.orEmpty()
        val route = when {
            className.endsWith("SaturationTileService") -> "saturation"
            className.endsWith("AutoHbmTileService") -> "autohbm"
            className.endsWith("OverscrollTileService") -> "overscroll"
            className.endsWith("ThermalManagerTileService") -> "thermalConfigs"
            className.endsWith("MainActivityTileService") -> null
            className.endsWith("PixelPartsLogTileService") -> "systemui"
            else -> null
        }

        return Intent(this, MainActivity::class.java).apply {
            // TODO: pass the route as an extra and push it onto the back stack on launch.
            if (route != null) putExtra(EXTRA_LEGACY_ROUTE, route)
            addFlags(
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TASK or
                    Intent.FLAG_ACTIVITY_NEW_TASK
            )
        }
    }

    companion object {
        const val EXTRA_LEGACY_ROUTE = "org.pixel.extra.parts.extra.LEGACY_ROUTE"
    }
}
