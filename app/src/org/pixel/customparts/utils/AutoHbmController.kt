package org.pixel.customparts.utils

import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.Display
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.Interpolator
import org.pixel.customparts.SettingsKeys
import org.pixel.customparts.services.AutoHbmService
import java.io.File
import kotlin.math.roundToInt

object AutoHbmController {
    const val DEFAULT_THRESHOLD_LUX = 20000
    const val MIN_THRESHOLD_LUX = 100
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
    private const val NO_SYSTEM_BRIGHTNESS = -1
    private const val MAX_SYSTEM_BRIGHTNESS = 255
    private const val NO_AUTO_BRIGHTNESS_STATE = -1
    private const val NO_TEMPERATURE = -1f
    private const val FRAME_INTERVAL_MS = 16L
    private val rampInterpolator: Interpolator = AccelerateDecelerateInterpolator()
    private val hbmModeLock = Any()
    private const val THERMAL_ROOT = "/sys/class/thermal"
    private const val SOC_THERMAL_NAME = "soc_therm"
    private const val BATTERY_TEMP_PATH = "/sys/class/power_supply/battery/temp"

    fun isSupported(): Boolean {
        return File(BRIGHTNESS_PATH).exists() && File(MAX_BRIGHTNESS_PATH).exists()
    }

    fun isEnabled(context: Context): Boolean {
        return SettingsCompat.isEnabled(context, SettingsKeys.AUTO_HBM_ENABLED, false)
    }

    fun getHbmMode(context: Context): Int {
        return SettingsCompat.getInt(
            context,
            SettingsKeys.AUTO_HBM_MODE,
            SettingsKeys.HBM_MODE_AUTO
        ).coerceIn(SettingsKeys.HBM_MODE_AUTO, SettingsKeys.HBM_MODE_PERMANENT)
    }

    fun isAutoModeEnabled(context: Context): Boolean {
        return isEnabled(context) && getHbmMode(context) == SettingsKeys.HBM_MODE_AUTO
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

    fun getActiveSince(context: Context): Long {
        return SettingsCompat.getString(context, SettingsKeys.AUTO_HBM_ACTIVE_SINCE, null)
            ?.toLongOrNull()
            ?.takeIf { it > 0L }
            ?: 0L
    }

    fun getSocModel(): String {
        return readSystemProperty("ro.soc.model") ?: "SoC"
    }

    fun isPermanentMode(context: Context): Boolean {
        return isEnabled(context) && getHbmMode(context) == SettingsKeys.HBM_MODE_PERMANENT
    }

    fun setHbmMode(context: Context, mode: Int) {
        SettingsCompat.putInt(
            context,
            SettingsKeys.AUTO_HBM_MODE,
            mode.coerceIn(SettingsKeys.HBM_MODE_AUTO, SettingsKeys.HBM_MODE_PERMANENT)
        )
        syncService(context)
    }

    fun setModeEnabled(context: Context, mode: Int, enabled: Boolean) {
        val normalizedMode = mode.coerceIn(SettingsKeys.HBM_MODE_AUTO, SettingsKeys.HBM_MODE_PERMANENT)
        synchronized(hbmModeLock) {
            // Commit the mutually-exclusive mode pair before asking either tile to refresh.
            Settings.Global.putInt(context.contentResolver, SettingsKeys.AUTO_HBM_MODE, normalizedMode)
            Settings.Global.putInt(context.contentResolver, SettingsKeys.AUTO_HBM_ENABLED, if (enabled) 1 else 0)
            syncService(context)
            PixelPartsTileRefresher.refreshHbmTiles(context)
        }
    }

    fun isBrightnessLockEnabled(context: Context): Boolean {
        return SettingsCompat.isEnabled(context, SettingsKeys.AUTO_HBM_BRIGHTNESS_LOCK, false)
    }

    fun setBrightnessLock(context: Context, enabled: Boolean) {
        SettingsCompat.putInt(context, SettingsKeys.AUTO_HBM_BRIGHTNESS_LOCK, if (enabled) 1 else 0)
        syncService(context)
    }

    fun isBrightnessLocked(context: Context): Boolean {
        val maxBrightness = readMaxBrightness() ?: 0
        val currentBrightness = readBrightness() ?: 0
        return isBrightnessLockEnabled(context) && currentBrightness >= maxBrightness
    }

    fun forceMaxBrightness(context: Context): Boolean {
        val maxBrightness = readMaxBrightness() ?: return false
        if (!isHbmActive(context)) {
            captureOriginalSystemBrightness(context)
            captureOriginalPanelBrightness(context, maxBrightness)
        }
        val success = writeBrightness(maxBrightness)
        if (success) {
            setHbmActive(context, true)
            syncSystemBrightnessToMax(context)
            SettingsCompat.putInt(context, SettingsKeys.AUTO_HBM_LAST_BRIGHTNESS, maxBrightness)
        }
        return success
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        SettingsCompat.putInt(context, SettingsKeys.AUTO_HBM_ENABLED, if (enabled) 1 else 0)
        syncService(context)
    }

    fun syncService(context: Context) {
        if (isEnabled(context) && isSupported()) {
            context.startService(Intent(context, AutoHbmService::class.java))
        } else {
            restoreOriginalBrightnessImmediate(context)
            restoreAutoBrightnessIfNeeded(context)
            context.stopService(Intent(context, AutoHbmService::class.java))
        }
    }

    fun activateHighBrightnessAsync(
        context: Context,
        handler: Handler,
        smoothRamp: Boolean = isSmoothRampEnabled(context),
        rampTimeMs: Int = getRampTimeMs(context),
        shouldContinue: () -> Boolean = { true },
        onComplete: (success: Boolean) -> Unit
    ) {
        val maxBrightness = readMaxBrightness() ?: run { onComplete(false); return }

        if (!isHbmActive(context)) {
            captureOriginalSystemBrightness(context)
            captureOriginalPanelBrightness(context, maxBrightness)
        }

        disableAutoBrightnessIfNeeded(context)

        if (smoothRamp && rampTimeMs > 0) {
            val logicalBrightness = SettingsCompat.getInt(
                context,
                SettingsKeys.AUTO_HBM_ORIGINAL_SYSTEM_BRIGHTNESS,
                MAX_SYSTEM_BRIGHTNESS
            ).coerceIn(1, MAX_SYSTEM_BRIGHTNESS)
            rampDisplayBrightnessAsync(
                context = context,
                handler = handler,
                from = logicalBrightness.toFloat() / MAX_SYSTEM_BRIGHTNESS,
                to = 1f,
                durationMs = rampTimeMs,
                shouldContinue = shouldContinue
            ) { rampSuccess ->
                val success = rampSuccess && writeBrightness(maxBrightness)
                if (success) {
                    setHbmActive(context, true)
                    SettingsCompat.putInt(context, SettingsKeys.AUTO_HBM_LAST_BRIGHTNESS, maxBrightness)
                }
                onComplete(success)
            }
            return
        }

        // The raw node is written only for the panel's HBM peak. Normal brightness
        // transitions and restoration belong to DisplayManager/Settings.
        val success = writeBrightness(maxBrightness)
        if (success) {
            setHbmActive(context, true)
            SettingsCompat.putInt(context, SettingsKeys.AUTO_HBM_LAST_BRIGHTNESS, maxBrightness)
        }
        onComplete(success)
    }

    fun maintainHighBrightness(context: Context): Boolean {
        val maxBrightness = readMaxBrightness() ?: return false
        val currentBrightness = readBrightness() ?: return false
        if (currentBrightness == maxBrightness) {
            syncSystemBrightnessToMax(context)
            SettingsCompat.putInt(context, SettingsKeys.AUTO_HBM_LAST_BRIGHTNESS, maxBrightness)
            return true
        }

        val success = writeBrightness(maxBrightness)
        if (success) {
            setHbmActive(context, true)
            syncSystemBrightnessToMax(context)
            SettingsCompat.putInt(context, SettingsKeys.AUTO_HBM_LAST_BRIGHTNESS, maxBrightness)
        }
        return success
    }

    fun restoreOriginalBrightnessAsync(
        context: Context,
        handler: Handler,
        smoothRamp: Boolean = isSmoothRampEnabled(context),
        rampTimeMs: Int = getRampTimeMs(context),
        shouldContinue: () -> Boolean = { true },
        onComplete: (success: Boolean) -> Unit
    ) {
        setHbmActive(context, false)
        SettingsCompat.putInt(context, SettingsKeys.AUTO_HBM_ORIGINAL_BRIGHTNESS, NO_ORIGINAL_BRIGHTNESS)
        readBrightness()?.let { SettingsCompat.putInt(context, SettingsKeys.AUTO_HBM_LAST_BRIGHTNESS, it) }
        onComplete(true)
    }

    fun restoreOriginalBrightnessImmediate(context: Context): Boolean {
        setHbmActive(context, false)
        SettingsCompat.putInt(context, SettingsKeys.AUTO_HBM_ORIGINAL_BRIGHTNESS, NO_ORIGINAL_BRIGHTNESS)
        readBrightness()?.let { SettingsCompat.putInt(context, SettingsKeys.AUTO_HBM_LAST_BRIGHTNESS, it) }
        return true
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
        // High-frequency state publication must not trigger a QS refresh scan on every sample.
        Settings.Global.putFloat(context.contentResolver, SettingsKeys.AUTO_HBM_LAST_LUX, lux)
        if (temperatureCelsius != null) {
            Settings.Global.putFloat(context.contentResolver, SettingsKeys.AUTO_HBM_LAST_TEMPERATURE, temperatureCelsius)
        }
        readBrightness()?.let {
            Settings.Global.putInt(context.contentResolver, SettingsKeys.AUTO_HBM_LAST_BRIGHTNESS, it)
        }

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
        // Match the sensor selected by Pixel's thermal HAL. Do not use the
        // generic "soc" zone: it is a CPU hotspot and is not in the HAL config.
        val socTemperature = runCatching {
            File(THERMAL_ROOT).listFiles { file -> file.name.startsWith("thermal_zone") }
                ?.mapNotNull { zone ->
                    val type = File(zone, "type").readTextOrNull()?.trim()?.lowercase()
                        ?: return@mapNotNull null
                    if (type != SOC_THERMAL_NAME) return@mapNotNull null
                    File(zone, "temp").readTextOrNull()?.trim()?.toLongOrNull()
                        ?.let(::normalizeTemperature)
                        ?.takeIf { it in 0f..150f }
                }
                ?.firstOrNull()
        }
            .onFailure { Log.w(TAG, "Unable to read $SOC_THERMAL_NAME thermal zone", it) }
            .getOrNull()

        if (socTemperature != null) return socTemperature

        // Keep the battery value as a safe fallback on devices without soc_therm.
        return File(BATTERY_TEMP_PATH).readTextOrNull()
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
            .isSuccess
    }

    private fun setHbmActive(context: Context, active: Boolean) {
        val wasActive = isHbmActive(context)
        SettingsCompat.putInt(context, SettingsKeys.AUTO_HBM_ACTIVE, if (active) 1 else 0)
        if (active && !wasActive) {
            SettingsCompat.putString(context, SettingsKeys.AUTO_HBM_ACTIVE_SINCE, SystemClock.elapsedRealtime().toString())
            disableAutoBrightnessIfNeeded(context)
            setDisplayBrightness(context, 1f)
            Settings.System.putIntForUser(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                MAX_SYSTEM_BRIGHTNESS,
                android.os.UserHandle.USER_CURRENT
            )
        } else if (!active) {
            SettingsCompat.putString(context, SettingsKeys.AUTO_HBM_ACTIVE_SINCE, null)
            restoreOriginalSystemBrightness(context)
        }
    }

    fun rememberSystemBrightness(context: Context) {
        if (isHbmActive(context)) return
        val current = readSystemBrightness(context) ?: return
        if (current in 1..MAX_SYSTEM_BRIGHTNESS) {
            Settings.Global.putInt(
                context.contentResolver,
                SettingsKeys.AUTO_HBM_LAST_SYSTEM_BRIGHTNESS,
                current
            )
        }
    }

    private fun captureOriginalSystemBrightness(context: Context) {
        if (SettingsCompat.getInt(
                context,
                SettingsKeys.AUTO_HBM_ORIGINAL_SYSTEM_BRIGHTNESS,
                NO_SYSTEM_BRIGHTNESS
            ) in 1..MAX_SYSTEM_BRIGHTNESS
        ) {
            return
        }

        rememberSystemBrightness(context)
        val current = readSystemBrightness(context)
            ?: SettingsCompat.getInt(
                context,
                SettingsKeys.AUTO_HBM_LAST_SYSTEM_BRIGHTNESS,
                NO_SYSTEM_BRIGHTNESS
            )
        if (current in 1..MAX_SYSTEM_BRIGHTNESS) {
            SettingsCompat.putInt(context, SettingsKeys.AUTO_HBM_ORIGINAL_SYSTEM_BRIGHTNESS, current)
        }
    }

    private fun captureOriginalPanelBrightness(context: Context, maxBrightness: Int) {
        val logicalBrightness = SettingsCompat.getInt(
            context,
            SettingsKeys.AUTO_HBM_ORIGINAL_SYSTEM_BRIGHTNESS,
            NO_SYSTEM_BRIGHTNESS
        )
        if (logicalBrightness !in 1..MAX_SYSTEM_BRIGHTNESS) {
            SettingsCompat.putInt(context, SettingsKeys.AUTO_HBM_ORIGINAL_BRIGHTNESS, NO_ORIGINAL_BRIGHTNESS)
            return
        }

        val panelBrightness = (logicalBrightness.toFloat() / MAX_SYSTEM_BRIGHTNESS * maxBrightness)
            .roundToInt()
            .coerceIn(1, maxBrightness)
        SettingsCompat.putInt(context, SettingsKeys.AUTO_HBM_ORIGINAL_BRIGHTNESS, panelBrightness)
    }

    private fun restoreOriginalSystemBrightness(context: Context) {
        val originalBrightness = SettingsCompat.getInt(
            context,
            SettingsKeys.AUTO_HBM_ORIGINAL_SYSTEM_BRIGHTNESS,
            NO_SYSTEM_BRIGHTNESS
        )
        if (originalBrightness !in 1..MAX_SYSTEM_BRIGHTNESS) return

        val settingsUpdated = Settings.System.putIntForUser(
            context.contentResolver,
            Settings.System.SCREEN_BRIGHTNESS,
            originalBrightness,
            android.os.UserHandle.USER_CURRENT
        )
        setDisplayBrightness(context, originalBrightness.toFloat() / MAX_SYSTEM_BRIGHTNESS)
        if (settingsUpdated) {
            SettingsCompat.putInt(
                context,
                SettingsKeys.AUTO_HBM_ORIGINAL_SYSTEM_BRIGHTNESS,
                NO_SYSTEM_BRIGHTNESS
            )
        }
    }

    private fun readSystemBrightness(context: Context): Int? {
        return runCatching {
            Settings.System.getIntForUser(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                NO_SYSTEM_BRIGHTNESS,
                android.os.UserHandle.USER_CURRENT
            )
        }.getOrNull()?.takeIf { it in 1..MAX_SYSTEM_BRIGHTNESS }
    }

    private fun setDisplayBrightness(context: Context, brightness: Float): Boolean {
        val displayManager = context.getSystemService(DisplayManager::class.java) ?: return false
        return runCatching {
            displayManager.setBrightness(
                Display.DEFAULT_DISPLAY,
                brightness.coerceIn(0f, 1f)
            )
            true
        }.onFailure {
            Log.w(TAG, "Unable to update system brightness state", it)
        }.getOrDefault(false)
    }

    private fun syncSystemBrightnessToMax(context: Context): Boolean {
        val settingsUpdated = Settings.System.putIntForUser(
            context.contentResolver,
            Settings.System.SCREEN_BRIGHTNESS,
            MAX_SYSTEM_BRIGHTNESS,
            android.os.UserHandle.USER_CURRENT
        )
        val displayUpdated = setDisplayBrightness(context, 1f)
        if (!settingsUpdated || !displayUpdated) {
            Log.w(
                TAG,
                "Unable to synchronize system brightness slider to max " +
                    "(settings=$settingsUpdated, display=$displayUpdated)"
            )
        }
        return settingsUpdated && displayUpdated
    }

    private fun setTemporaryDisplayBrightness(context: Context, brightness: Float): Boolean {
        val displayManager = context.getSystemService(DisplayManager::class.java) ?: return false
        return runCatching {
            displayManager.setTemporaryBrightness(
                Display.DEFAULT_DISPLAY,
                brightness.coerceIn(0f, 1f)
            )
            true
        }.onFailure {
            Log.w(TAG, "Unable to update temporary system brightness", it)
        }.getOrDefault(false)
    }

    private fun rampDisplayBrightnessAsync(
        context: Context,
        handler: Handler,
        from: Float,
        to: Float,
        durationMs: Int,
        shouldContinue: () -> Boolean,
        onComplete: (success: Boolean) -> Unit
    ) {
        val clampedDuration = durationMs.coerceIn(MIN_RAMP_TIME_MS, MAX_RAMP_TIME_MS).toLong()
        val startTime = SystemClock.elapsedRealtime()

        val frameRunnable = object : Runnable {
            override fun run() {
                if (!shouldContinue()) {
                    onComplete(false)
                    return
                }

                val elapsed = SystemClock.elapsedRealtime() - startTime
                val fraction = (elapsed.toFloat() / clampedDuration).coerceIn(0f, 1f)
                val interpolatedFraction = rampInterpolator.getInterpolation(fraction)
                val currentBrightness = from + ((to - from) * interpolatedFraction)

                if (!setTemporaryDisplayBrightness(context, currentBrightness)) {
                    onComplete(false)
                    return
                }

                if (fraction < 1f) {
                    handler.postDelayed(this, FRAME_INTERVAL_MS)
                } else {
                    val finalSuccess = setDisplayBrightness(context, to)
                    onComplete(finalSuccess && shouldContinue())
                }
            }
        }
        handler.post(frameRunnable)
    }

    private fun getScreenBrightnessMode(context: Context): Int? {
        return runCatching {
            Settings.System.getIntForUser(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC,
                android.os.UserHandle.USER_CURRENT
            )
        }
            .onFailure { Log.w(TAG, "Unable to read screen brightness mode", it) }
            .getOrNull()
    }

    private fun setScreenBrightnessMode(context: Context, mode: Int): Boolean {
        return runCatching {
            Settings.System.putIntForUser(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                mode,
                android.os.UserHandle.USER_CURRENT
            )
        }
            .onFailure { Log.w(TAG, "Unable to write screen brightness mode", it) }
            .getOrDefault(false)
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
