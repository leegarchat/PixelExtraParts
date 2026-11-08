package org.pixel.customparts.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log

class SleepReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if ("org.pixel.customparts.ACTION_SLEEP" == intent.action) {
            Log.d("CustomParts", "Sleep requested via Broadcast")
            try {
                val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                pm?.goToSleep(SystemClock.uptimeMillis())
            } catch (e: Exception) {
                Log.e("CustomParts", "Failed to sleep", e)
            }
        }
    }
}