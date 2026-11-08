package org.pixel.extra.parts.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log

class SleepReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if ("org.pixel.extra.parts.ACTION_SLEEP" == intent.action) {
            Log.d("CustomParts", "Sleep requested via Broadcast")
            try {
                val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                if (pm != null) org.pixel.extra.parts.utils.HiddenApi.goToSleep(pm, SystemClock.uptimeMillis())
            } catch (e: Exception) {
                Log.e("CustomParts", "Failed to sleep", e)
            }
        }
    }
}