package org.pixel.customparts.utils

import android.content.Context
import android.os.ServiceManager
import android.provider.Settings
import android.util.Log
import com.android.internal.statusbar.IStatusBarService

private const val TAG = "SystemUI_Restarter"
const val MANUAL_RESTART_FLAG = "pixel_addon_manual_restart"

fun restartSystemUI(context: Context) {
    markManualRestart(context)
    try {
        val statusBarService = IStatusBarService.Stub.asInterface(
            ServiceManager.getService(Context.STATUS_BAR_SERVICE)
        ) ?: error("statusbar service is unavailable")

        statusBarService.restartSystemUI()
        Log.d(TAG, "SystemUI restart requested through IStatusBarService")
    } catch (t: Throwable) {
        Log.e(TAG, "Failed to request SystemUI restart through IStatusBarService", t)
    }
}

private fun markManualRestart(context: Context) {
    try {
        Settings.Global.putInt(context.contentResolver, MANUAL_RESTART_FLAG, 1)
        Log.d(TAG, "Manual restart flag set")
    } catch (t: Throwable) {
        Log.e(TAG, "Failed to set manual restart flag", t)
    }
}
