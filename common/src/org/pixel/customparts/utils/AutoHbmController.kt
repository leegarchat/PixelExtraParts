package org.pixel.customparts.utils

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import org.pixel.customparts.SettingsKeys
import org.pixel.customparts.services.AutoHbmService
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

object AutoHbmController {
    const val DEFAULT_THRESHOLD_LUX = 20000
    const val MIN_THRESHOLD_LUX = 2000
    const val MAX_THRESHOLD_LUX = 60000
    const val DEFAULT_ENABLE_TIME_SECONDS = 0
    const val DEFAULT_DISABLE_TIME_SECONDS = 1
    const val MIN_TIME_SECONDS = 0
    const val MAX_TIME_SECONDS = 10
    const val DEFAULT_RAMP_TIME_MS = 800
    const val MIN_RAMP_TIME_MS = 100
    const val MAX_RAMP_TIME_MS = 5000
    const val DEFAULT_MAX_ACTIVE_TIME_SECONDS = 120
    const val DEFAULT_COOLDOWN_TIME_SECONDS = 60
    const val MIN_TIMEOUT_SECONDS = 10
    const val MAX_TIMEOUT_SECONDS = 2000
    const val DEFAULT_CHECK_INTERVAL_MS = 250
    const val MIN_CHECK_INTERVAL_MS = 30
    const val MAX_CHECK_INTERVAL_MS = 10000
    const val DEFAULT_TEMPERATURE_LIMIT_C = 50
    const val MIN_TEMPERATURE_LIMIT_C = 30
    const val MAX_TEMPERATURE_LIMIT_C = 80

    const val BRIGHTNESS_PATH = "/sys/class/backlight/panel0-backlight/brightness"
    const val MAX_BRIGHTNESS_PATH = "/sys/class/backlight/panel0-backlight/max_brightness"

    const val ACTION_STATE_CHANGED = "org.pixel.customparts.action.AUTO_HBM_STATE_CHANGED"
    const val EXTRA_LUX = "lux"
    const val EXTRA_ACTIVE = "active"
    const val EXTRA_BRIGHTNESS = "brightness"
    const val EXTRA_MAX_BRIGHTNESS = "max_brightness"
    const val EXTRA_TEMPERATURE = "temperature"

    private const val TAG = "AutoHbmController"
    private const val NO_ORIGINAL_BRIGHTNESS = -1
    private const val NO_AUTO_BRIGHTNESS_STATE = -1
    private const val NO_TEMPERATURE = -1f
    private const val THERMAL_ROOT = "/sys/class/thermal"
    private const val BATTERY_TEMP_PATH = "/sys/class/power_supply/battery/temp"
    private val SOC_THERMAL_KEYWORDS = listOf(
        "soc",
        "cpu",
        "gpu",
        "tpu",
        "tensor",
        "big",
        "little",
        "mid",
        "silver",
        "gold",
        "prime"
    )

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

    fun isSmoothRampEnabled(context: Context): Boolean {
        return SettingsCompat.isEnabled(context, SettingsKeys.AUTO_HBM_SMOOTH_RAMP_ENABLED, true)
    }

    fun setSmoothRampEnabled(context: Context, enabled: Boolean) {
        SettingsCompat.putInt(context, SettingsKeys.AUTO_HBM_SMOOTH_RAMP_ENABLED, if (enabled) 1 else 0)
    }

    fun getRampTimeMs(context: Context): Int {
        return SettingsCompat.getInt(
            context,
            SettingsKeys.AUTO_HBM_RAMP_TIME_MS,
            DEFAULT_RAMP_TIME_MS
        ).coerceIn(MIN_RAMP_TIME_MS, MAX_RAMP_TIME_MS)
    }

    fun setRampTimeMs(context: Context, value: Int) {
        SettingsCompat.putInt(
            context,
            SettingsKeys.AUTO_HBM_RAMP_TIME_MS,
            value.coerceIn(MIN_RAMP_TIME_MS, MAX_RAMP_TIME_MS)
        )
    }

    fun getMaxActiveTime(context: Context): Int {
        return SettingsCompat.getInt(
            context,
            SettingsKeys.AUTO_HBM_MAX_ACTIVE_TIME,
            DEFAULT_MAX_ACTIVE_TIME_SECONDS
        ).coerceIn(MIN_TIMEOUT_SECONDS, MAX_TIMEOUT_SECONDS)
    }

    fun setMaxActiveTime(context: Context, value: Int) {
        SettingsCompat.putInt(
            context,
            SettingsKeys.AUTO_HBM_MAX_ACTIVE_TIME,
            value.coerceIn(MIN_TIMEOUT_SECONDS, MAX_TIMEOUT_SECONDS)
        )
    }

    fun getCooldownTime(context: Context): Int {
        return SettingsCompat.getInt(
            context,
            SettingsKeys.AUTO_HBM_COOLDOWN_TIME,
            DEFAULT_COOLDOWN_TIME_SECONDS
        ).coerceIn(MIN_TIMEOUT_SECONDS, MAX_TIMEOUT_SECONDS)
    }

    fun setCooldownTime(context: Context, value: Int) {
        SettingsCompat.putInt(
            context,
            SettingsKeys.AUTO_HBM_COOLDOWN_TIME,
            value.coerceIn(MIN_TIMEOUT_SECONDS, MAX_TIMEOUT_SECONDS)
        )
    }

    fun getCheckIntervalMs(context: Context): Int {
        return SettingsCompat.getInt(
            context,
            SettingsKeys.AUTO_HBM_CHECK_INTERVAL_MS,
            DEFAULT_CHECK_INTERVAL_MS
        ).coerceIn(MIN_CHECK_INTERVAL_MS, MAX_CHECK_INTERVAL_MS)
    }

    fun setCheckIntervalMs(context: Context, value: Int) {
        SettingsCompat.putInt(
            context,
            SettingsKeys.AUTO_HBM_CHECK_INTERVAL_MS,
            value.coerceIn(MIN_CHECK_INTERVAL_MS, MAX_CHECK_INTERVAL_MS)
        )
    }

    fun getTemperatureLimit(context: Context): Int {
        return SettingsCompat.getInt(
            context,
            SettingsKeys.AUTO_HBM_TEMPERATURE_LIMIT,
            DEFAULT_TEMPERATURE_LIMIT_C
        ).coerceIn(MIN_TEMPERATURE_LIMIT_C, MAX_TEMPERATURE_LIMIT_C)
    }

    fun setTemperatureLimit(context: Context, value: Int) {
        SettingsCompat.putInt(
            context,
            SettingsKeys.AUTO_HBM_TEMPERATURE_LIMIT,
            value.coerceIn(MIN_TEMPERATURE_LIMIT_C, MAX_TEMPERATURE_LIMIT_C)
        )
    }

    fun getLastLux(context: Context): Float {
        return SettingsCompat.getFloat(context, SettingsKeys.AUTO_HBM_LAST_LUX, 0f)
    }

    fun getLastTemperature(context: Context): Float? {
        val value = SettingsCompat.getFloat(context, SettingsKeys.AUTO_HBM_LAST_TEMPERATURE, NO_TEMPERATURE)
        return value.takeIf { it >= 0f }
    }

    fun getLastBrightness(context: Context): Int {
        return SettingsCompat.getInt(context, SettingsKeys.AUTO_HBM_LAST_BRIGHTNESS, readBrightness() ?: 0)
    }

    fun getSocModel(): String {
        return readSystemProperty("ro.soc.model") ?: "SoC"
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
            restoreAutoBrightnessIfNeeded(context)
            context.stopService(Intent(context, AutoHbmService::class.java))
        }
    }

    fun activateHighBrightness(
        context: Context,
        smoothRamp: Boolean = isSmoothRampEnabled(context),
        rampTimeMs: Int = getRampTimeMs(context),
        shouldContinue: () -> Boolean = { true }
    ): Boolean {
        val maxBrightness = readMaxBrightness() ?: return false
        val currentBrightness = readBrightness() ?: return false

        if (!isHbmActive(context)) {
            SettingsCompat.putInt(context, SettingsKeys.AUTO_HBM_ORIGINAL_BRIGHTNESS, currentBrightness)
        }

        val success = if (smoothRamp && rampTimeMs > 0 && currentBrightness != maxBrightness) {
            rampBrightness(currentBrightness, maxBrightness, rampTimeMs, shouldContinue)
        } else {
            writeBrightness(maxBrightness)
        }
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

    fun disableAutoBrightnessIfNeeded(context: Context): Boolean {
        val currentMode = getScreenBrightnessMode(context) ?: return false
        val storedState = SettingsCompat.getInt(
            context,
            SettingsKeys.AUTO_HBM_AUTO_BRIGHTNESS_WAS_ENABLED,
            NO_AUTO_BRIGHTNESS_STATE
        )

        if (storedState == NO_AUTO_BRIGHTNESS_STATE) {
            SettingsCompat.putInt(
                context,
                SettingsKeys.AUTO_HBM_AUTO_BRIGHTNESS_WAS_ENABLED,
                if (currentMode == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC) 1 else 0
            )
        }

        return if (currentMode == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC) {
            setScreenBrightnessMode(context, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
        } else {
            true
        }
    }

    fun restoreAutoBrightnessIfNeeded(context: Context): Boolean {
        val wasAutoBrightnessEnabled = SettingsCompat.getInt(
            context,
            SettingsKeys.AUTO_HBM_AUTO_BRIGHTNESS_WAS_ENABLED,
            NO_AUTO_BRIGHTNESS_STATE
        )

        val success = if (wasAutoBrightnessEnabled == 1) {
            setScreenBrightnessMode(context, Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC)
        } else {
            true
        }

        if (success) {
            SettingsCompat.putInt(
                context,
                SettingsKeys.AUTO_HBM_AUTO_BRIGHTNESS_WAS_ENABLED,
                NO_AUTO_BRIGHTNESS_STATE
            )
        }
        return success
    }

    fun publishState(context: Context, lux: Float, temperatureCelsius: Float? = readSocTemperatureC()) {
        SettingsCompat.putFloat(context, SettingsKeys.AUTO_HBM_LAST_LUX, lux)
        if (temperatureCelsius != null) {
            SettingsCompat.putFloat(context, SettingsKeys.AUTO_HBM_LAST_TEMPERATURE, temperatureCelsius)
        }
        readBrightness()?.let { SettingsCompat.putInt(context, SettingsKeys.AUTO_HBM_LAST_BRIGHTNESS, it) }

        val intent = Intent(ACTION_STATE_CHANGED).setPackage(context.packageName)
            .putExtra(EXTRA_LUX, lux)
            .putExtra(EXTRA_ACTIVE, isHbmActive(context))
            .putExtra(EXTRA_BRIGHTNESS, getLastBrightness(context))
            .putExtra(EXTRA_MAX_BRIGHTNESS, readMaxBrightness() ?: 0)
        if (temperatureCelsius != null) {
            intent.putExtra(EXTRA_TEMPERATURE, temperatureCelsius)
        }

        context.sendBroadcast(intent)
    }

    fun readBrightness(): Int? = readIntFile(BRIGHTNESS_PATH)

    fun readMaxBrightness(): Int? = readIntFile(MAX_BRIGHTNESS_PATH)

    fun readSocTemperatureC(): Float? {
        val thermalKeywords = getSocThermalKeywords()
        val thermalTemps = runCatching {
            File(THERMAL_ROOT).listFiles { file -> file.name.startsWith("thermal_zone") }
                ?.mapNotNull { zone ->
                    val type = File(zone, "type").readTextOrNull()?.trim()?.lowercase() ?: return@mapNotNull null
                    if (thermalKeywords.none { type.contains(it) }) return@mapNotNull null
                    File(zone, "temp").readTextOrNull()?.trim()?.toLongOrNull()?.let(::normalizeTemperature)
                }
                ?.filter { it in 0f..150f }
                ?.maxOrNull()
        }
            .onFailure { Log.w(TAG, "Unable to read SoC thermal zones", it) }
            .getOrNull()

        return thermalTemps ?: File(BATTERY_TEMP_PATH).readTextOrNull()
            ?.trim()
            ?.toLongOrNull()
            ?.let(::normalizeTemperature)
            ?.takeIf { it in 0f..150f }
    }

    private fun readIntFile(path: String): Int? {
        return runCatching { File(path).readText().trim().toInt() }
            .onFailure { Log.w(TAG, "Unable to read $path", it) }
            .getOrNull()
    }

    private fun File.readTextOrNull(): String? {
        return runCatching { readText() }.getOrNull()
    }

    private fun writeBrightness(value: Int): Boolean {
        return runCatching { File(BRIGHTNESS_PATH).writeText(value.toString()) }
            .onFailure { Log.e(TAG, "Unable to write $BRIGHTNESS_PATH", it) }
            .isSuccess && readBrightness() == value
    }

    private fun rampBrightness(
        from: Int,
        to: Int,
        durationMs: Int,
        shouldContinue: () -> Boolean
    ): Boolean {
        val clampedDuration = durationMs.coerceIn(MIN_RAMP_TIME_MS, MAX_RAMP_TIME_MS)
        val steps = (clampedDuration / 50).coerceIn(1, 100)
        val stepDelayMs = max(1, clampedDuration / steps).toLong()
        val upperBound = max(from, to).coerceAtLeast(1)

        for (step in 1..steps) {
            if (!shouldContinue()) return false
            val progress = step.toFloat() / steps.toFloat()
            val brightness = (from + ((to - from) * progress)).roundToInt().coerceIn(0, upperBound)
            if (!writeBrightness(brightness)) return false
            if (step < steps) {
                runCatching { Thread.sleep(stepDelayMs) }
            }
        }
        return writeBrightness(to)
    }

    private fun getScreenBrightnessMode(context: Context): Int? {
        return runCatching {
            Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE)
        }
            .onFailure { Log.w(TAG, "Unable to read screen brightness mode", it) }
            .getOrNull()
    }

    private fun setScreenBrightnessMode(context: Context, mode: Int): Boolean {
        return runCatching {
            Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, mode)
        }
            .onFailure { Log.w(TAG, "Unable to write screen brightness mode", it) }
            .getOrDefault(false)
    }

    private fun getSocThermalKeywords(): List<String> {
        val model = readSystemProperty("ro.soc.model")?.lowercase().orEmpty()
        if (model.isBlank()) return SOC_THERMAL_KEYWORDS

        val modelTokens = model.split(Regex("[^a-z0-9]+"))
            .filter { it.length >= 2 }
        val compactModel = model.filter { it.isLetterOrDigit() }

        return (SOC_THERMAL_KEYWORDS + modelTokens + compactModel)
            .filter { it.isNotBlank() }
            .distinct()
    }

    private fun readSystemProperty(name: String): String? {
        return runCatching {
            val clazz = Class.forName("android.os.SystemProperties")
            val method = clazz.getMethod("get", String::class.java, String::class.java)
            method.invoke(null, name, "") as? String
        }
            .getOrNull()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    private fun normalizeTemperature(rawValue: Long): Float {
        val absValue = kotlin.math.abs(rawValue).toFloat()
        return when {
            absValue > 1000f -> absValue / 1000f
            absValue > 100f -> absValue / 10f
            else -> absValue
        }
    }
}
