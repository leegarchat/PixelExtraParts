package org.pixel.customparts.utils

import android.content.Context
import android.content.Intent
import android.util.Log
import org.pixel.customparts.SettingsKeys
import org.pixel.customparts.services.AutoHbmService
import java.io.File

object AutoHbmController {
    const val DEFAULT_THRESHOLD_LUX = 20000
    const val MIN_THRESHOLD_LUX = 2000
    const val MAX_THRESHOLD_LUX = 60000
    const val DEFAULT_ENABLE_TIME_SECONDS = 0
    const val DEFAULT_DISABLE_TIME_SECONDS = 1
    const val MIN_TIME_SECONDS = 0
    const val MAX_TIME_SECONDS = 10

    const val BRIGHTNESS_PATH = "/sys/class/backlight/panel0-backlight/brightness"
    const val MAX_BRIGHTNESS_PATH = "/sys/class/backlight/panel0-backlight/max_brightness"

    const val ACTION_STATE_CHANGED = "org.pixel.customparts.action.AUTO_HBM_STATE_CHANGED"
    const val EXTRA_LUX = "lux"
    const val EXTRA_ACTIVE = "active"
    const val EXTRA_BRIGHTNESS = "brightness"

    private const val TAG = "AutoHbmController"
    private const val NO_ORIGINAL_BRIGHTNESS = -1

    fun isSupported(): Boolean {
        return File(BRIGHTNESS_PATH).exists() && File(MAX_BRIGHTNESS_PATH).exists()
    }

    fun isEnabled(context: Context): Boolean {
        return SettingsCompat.isEnabled(context, SettingsKeys.AUTO_HBM_ENABLED, false)
    }

    fun isHbmActive(context: Context): Boolean {
        return SettingsCompat.isEnabled(context, SettingsKeys.AUTO_HBM_ACTIVE, false)
    }

    fun getThreshold(context: Context): Int {
        return SettingsCompat.getInt(
            context,
            SettingsKeys.AUTO_HBM_THRESHOLD,
            DEFAULT_THRESHOLD_LUX
        ).coerceIn(MIN_THRESHOLD_LUX, MAX_THRESHOLD_LUX)
    }

    fun setThreshold(context: Context, value: Int) {
        SettingsCompat.putInt(
            context,
            SettingsKeys.AUTO_HBM_THRESHOLD,
            value.coerceIn(MIN_THRESHOLD_LUX, MAX_THRESHOLD_LUX)
        )
    }

    fun getEnableTime(context: Context): Int {
        return SettingsCompat.getInt(
            context,
            SettingsKeys.AUTO_HBM_ENABLE_TIME,
            DEFAULT_ENABLE_TIME_SECONDS
        ).coerceIn(MIN_TIME_SECONDS, MAX_TIME_SECONDS)
    }

    fun setEnableTime(context: Context, value: Int) {
        SettingsCompat.putInt(
            context,
            SettingsKeys.AUTO_HBM_ENABLE_TIME,
            value.coerceIn(MIN_TIME_SECONDS, MAX_TIME_SECONDS)
        )
    }

    fun getDisableTime(context: Context): Int {
        return SettingsCompat.getInt(
            context,
            SettingsKeys.AUTO_HBM_DISABLE_TIME,
            DEFAULT_DISABLE_TIME_SECONDS
        ).coerceIn(MIN_TIME_SECONDS, MAX_TIME_SECONDS)
    }

    fun setDisableTime(context: Context, value: Int) {
        SettingsCompat.putInt(
            context,
            SettingsKeys.AUTO_HBM_DISABLE_TIME,
            value.coerceIn(MIN_TIME_SECONDS, MAX_TIME_SECONDS)
        )
    }

    fun getLastLux(context: Context): Float {
        return SettingsCompat.getFloat(context, SettingsKeys.AUTO_HBM_LAST_LUX, 0f)
    }

    fun getLastBrightness(context: Context): Int {
        return SettingsCompat.getInt(context, SettingsKeys.AUTO_HBM_LAST_BRIGHTNESS, readBrightness() ?: 0)
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        SettingsCompat.putInt(context, SettingsKeys.AUTO_HBM_ENABLED, if (enabled) 1 else 0)
        syncService(context)
    }

    fun syncService(context: Context) {
        if (isEnabled(context) && isSupported()) {
            context.startService(Intent(context, AutoHbmService::class.java))
        } else {
            restoreOriginalBrightness(context)
            context.stopService(Intent(context, AutoHbmService::class.java))
        }
    }

    fun activateHighBrightness(context: Context): Boolean {
        val maxBrightness = readMaxBrightness() ?: return false
        val currentBrightness = readBrightness() ?: return false

        if (!isHbmActive(context)) {
            SettingsCompat.putInt(context, SettingsKeys.AUTO_HBM_ORIGINAL_BRIGHTNESS, currentBrightness)
        }

        val success = writeBrightness(maxBrightness)
        if (success) {
            SettingsCompat.putInt(context, SettingsKeys.AUTO_HBM_ACTIVE, 1)
            SettingsCompat.putInt(context, SettingsKeys.AUTO_HBM_LAST_BRIGHTNESS, maxBrightness)
        }
        return success
    }

    fun restoreOriginalBrightness(context: Context): Boolean {
        val originalBrightness = SettingsCompat.getInt(
            context,
            SettingsKeys.AUTO_HBM_ORIGINAL_BRIGHTNESS,
            NO_ORIGINAL_BRIGHTNESS
        )

        val success = if (originalBrightness >= 0) {
            writeBrightness(originalBrightness)
        } else {
            true
        }

        if (success) {
            SettingsCompat.putInt(context, SettingsKeys.AUTO_HBM_ACTIVE, 0)
            SettingsCompat.putInt(context, SettingsKeys.AUTO_HBM_ORIGINAL_BRIGHTNESS, NO_ORIGINAL_BRIGHTNESS)
            readBrightness()?.let { SettingsCompat.putInt(context, SettingsKeys.AUTO_HBM_LAST_BRIGHTNESS, it) }
        }
        return success
    }

    fun publishState(context: Context, lux: Float) {
        SettingsCompat.putFloat(context, SettingsKeys.AUTO_HBM_LAST_LUX, lux)
        readBrightness()?.let { SettingsCompat.putInt(context, SettingsKeys.AUTO_HBM_LAST_BRIGHTNESS, it) }

        context.sendBroadcast(
            Intent(ACTION_STATE_CHANGED).setPackage(context.packageName)
                .putExtra(EXTRA_LUX, lux)
                .putExtra(EXTRA_ACTIVE, isHbmActive(context))
                .putExtra(EXTRA_BRIGHTNESS, getLastBrightness(context))
        )
    }

    fun readBrightness(): Int? = readIntFile(BRIGHTNESS_PATH)

    fun readMaxBrightness(): Int? = readIntFile(MAX_BRIGHTNESS_PATH)

    private fun readIntFile(path: String): Int? {
        return runCatching { File(path).readText().trim().toInt() }
            .onFailure { Log.w(TAG, "Unable to read $path", it) }
            .getOrNull()
    }

    private fun writeBrightness(value: Int): Boolean {
        val directWrite = runCatching { File(BRIGHTNESS_PATH).writeText(value.toString()) }.isSuccess
        if (!directWrite) {
            runRootCommand("printf '%s' '$value' > '$BRIGHTNESS_PATH'")
        }
        return readBrightness() == value
    }
}