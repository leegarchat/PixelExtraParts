package org.pixel.customparts.services

import android.app.ActivityManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import org.pixel.customparts.utils.ThermalProfileController

class ThermalProfileService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var lastPackageName: String? = null
    private var lastAppliedConfig: String? = null

    private val updateRunnable = object : Runnable {
        override fun run() {
            updateActiveThermalConfig()
            handler.postDelayed(this, UPDATE_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        handler.post(updateRunnable)
    }

    override fun onDestroy() {
        handler.removeCallbacks(updateRunnable)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun updateActiveThermalConfig() {
        ThermalProfileController.seedVendorConfigs()

        if (!ThermalProfileController.hasProfileMap()) {
            stopSelf()
            return
        }

        val profileMap = ThermalProfileController.readProfileMap()
        val packageName = getTopPackageName()
        val configId = packageName
            ?.let { profileMap.packageConfigs[it] }
            ?.takeIf { it.isNotBlank() }
            ?: profileMap.globalConfig
            ?: ThermalProfileController.STOCK_CONFIG_ID
        val propertyValue = ThermalProfileController.resolvePropertyValue(configId)

        if (packageName == lastPackageName && propertyValue == lastAppliedConfig) {
            return
        }

        if (ThermalProfileController.applyConfig(this, configId)) {
            lastPackageName = packageName
            lastAppliedConfig = propertyValue

            if (profileMap.packageConfigs.isEmpty() && ThermalProfileController.isConfigAvailable(profileMap.globalConfig)) {
                stopSelf()
            }
        } else {
            Log.w(TAG, "Failed to apply thermal config $configId for ${packageName ?: "global"}")
        }
    }

    @Suppress("DEPRECATION")
    private fun getTopPackageName(): String? {
        return runCatching {
            val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            activityManager.getRunningTasks(1).firstOrNull()?.topActivity?.packageName
        }.getOrNull()
    }

    companion object {
        private const val TAG = "ThermalProfileService"
        private const val UPDATE_INTERVAL_MS = 1000L
    }
}