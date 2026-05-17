package org.pixel.customparts.utils

import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context.ACTIVITY_SERVICE
import android.content.Context
import android.content.Intent
import org.pixel.customparts.SettingsKeys
import org.pixel.customparts.services.PixelPartsLogService

object PixelPartsLogController {
    const val LOG_ROOT_PATH = "/sdcard/PixelExtraPartsLogs"

    fun isServiceEnabled(context: Context): Boolean {
        return SettingsCompat.isEnabled(context, SettingsKeys.LOG_SERVICE_ENABLED, false)
    }

    fun isLogcatEnabled(context: Context): Boolean {
        return SettingsCompat.isEnabled(context, SettingsKeys.LOG_SERVICE_LOGCAT_ENABLED, false)
    }

    fun isDmesgEnabled(context: Context): Boolean {
        return SettingsCompat.isEnabled(context, SettingsKeys.LOG_SERVICE_DMESG_ENABLED, false)
    }

    fun isCrashesEnabled(context: Context): Boolean {
        return SettingsCompat.isEnabled(context, SettingsKeys.LOG_SERVICE_CRASHES_ENABLED, false)
    }

    fun setServiceEnabled(context: Context, enabled: Boolean) {
        SettingsCompat.putInt(context, SettingsKeys.LOG_SERVICE_ENABLED, if (enabled) 1 else 0)
        syncService(context)
    }

    fun startLogging(context: Context) {
        setServiceEnabled(context, true)
    }

    fun stopLogging(context: Context) {
        setServiceEnabled(context, false)
    }

    fun isServiceRunning(context: Context): Boolean {
        if (PixelPartsLogService.isRunning) {
            return true
        }

        val manager = context.getSystemService(ACTIVITY_SERVICE) as? ActivityManager ?: return false
        val serviceComponent = ComponentName(context, PixelPartsLogService::class.java)
        @Suppress("DEPRECATION")
        return manager.getRunningServices(Int.MAX_VALUE).any { runningService ->
            runningService.service == serviceComponent
        }
    }

    fun setLogcatEnabled(context: Context, enabled: Boolean) {
        SettingsCompat.putInt(context, SettingsKeys.LOG_SERVICE_LOGCAT_ENABLED, if (enabled) 1 else 0)
        syncService(context)
    }

    fun setDmesgEnabled(context: Context, enabled: Boolean) {
        SettingsCompat.putInt(context, SettingsKeys.LOG_SERVICE_DMESG_ENABLED, if (enabled) 1 else 0)
        syncService(context)
    }

    fun setCrashesEnabled(context: Context, enabled: Boolean) {
        SettingsCompat.putInt(context, SettingsKeys.LOG_SERVICE_CRASHES_ENABLED, if (enabled) 1 else 0)
        syncService(context)
    }

    fun syncService(context: Context) {
        val intent = Intent(context, PixelPartsLogService::class.java)
        if (isServiceEnabled(context)) {
            context.startService(intent)
        } else {
            context.stopService(intent)
        }
    }
}