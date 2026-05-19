package org.pixel.customparts.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.pixel.customparts.activities.ImsManager
import org.pixel.customparts.activities.ThermalManager
import org.pixel.customparts.utils.AddonBootSync
import org.pixel.customparts.utils.AutoHbmController
import org.pixel.customparts.utils.PixelPartsLogController
import org.pixel.customparts.utils.PixelPartsTileRefresher
import org.pixel.customparts.utils.SaturationController
import org.pixel.customparts.utils.ThermalProfileController

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d("PixelParts", "Boot/wake event received. Action: $action")

        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (action == Intent.ACTION_USER_PRESENT || action == Intent.ACTION_SCREEN_ON) {
                    PixelPartsTileRefresher.requestAll(context)
                    return@launch
                }
                ImsManager.onBoot(context)
                ThermalManager.onBoot(context)
                ThermalProfileController.syncService(context)
                SaturationController.applyEffectiveSaturation(context)
                AutoHbmController.syncService(context)
                PixelPartsLogController.syncService(context)
                AddonBootSync.sync(context)
                PixelPartsTileRefresher.requestAll(context)
            } catch (e: Exception) {
                Log.e("PixelParts", "Error during boot initialization", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}