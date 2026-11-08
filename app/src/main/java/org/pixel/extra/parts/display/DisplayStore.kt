package org.pixel.extra.parts.display

import android.content.Context
import android.os.IBinder
import android.os.Parcel
import android.provider.Settings
import android.util.Log

// Fail-safe bridge to Settings.Global for every Display knob, plus the
// best-effort SurfaceFlinger saturation transaction ported from the legacy
// controller. The persisted keys are the source of truth; firmware services
// re-apply them on boot, so the UI never needs to start or stop services.
object DisplayStore {
    private const val TAG = "DisplayStore"
    private const val SURFACE_FLINGER_SERVICE = "SurfaceFlinger"
    private const val SURFACE_COMPOSER_INTERFACE = "android.ui.ISurfaceComposer"
    private const val TRANSACTION_SET_SATURATION = 1022

    // Firmware broadcast contracts. Action strings stay verbatim so the
    // firmware-side services keep interop with this client.
    const val AUTO_HBM_ACTION_STATE_CHANGED = "org.pixel.customparts.action.AUTO_HBM_STATE_CHANGED"
    const val AUTO_HBM_EXTRA_LUX = "lux"
    const val AUTO_HBM_EXTRA_ACTIVE = "active"
    const val AUTO_HBM_EXTRA_BRIGHTNESS = "brightness"
    const val AUTO_HBM_EXTRA_MAX_BRIGHTNESS = "max_brightness"
    const val AUTO_HBM_EXTRA_TEMPERATURE = "temperature"

    const val AUTO_LOCK_ACTION_STATE_CHANGED = "org.pixel.customparts.action.AUTO_LOCK_STATE_CHANGED"
    const val AUTO_LOCK_EXTRA_ENABLED = "enabled"
    const val AUTO_LOCK_EXTRA_TIMEOUT = "timeout"
    const val AUTO_LOCK_EXTRA_REMAINING = "remaining"

    private const val BRIGHTNESS_PATH = "/sys/class/backlight/panel0-backlight/brightness"
    private const val MAX_BRIGHTNESS_PATH = "/sys/class/backlight/panel0-backlight/max_brightness"
    private const val THERMAL_ROOT = "/sys/class/thermal"
    private const val SOC_THERMAL_NAME = "soc_therm"
    private const val BATTERY_TEMP_PATH = "/sys/class/power_supply/battery/temp"

    fun getInt(context: Context, key: String, defaultValue: Int): Int {
        return try {
            Settings.Global.getInt(context.contentResolver, key, defaultValue)
        } catch (e: Exception) {
            Log.w(TAG, "Read failed for $key", e)
            defaultValue
        }
    }

    fun putInt(context: Context, key: String, value: Int): Boolean {
        return try {
            Settings.Global.putInt(context.contentResolver, key, value)
            true
        } catch (e: Exception) {
            Log.w(TAG, "Write failed for $key", e)
            false
        }
    }

    fun getFloat(context: Context, key: String, defaultValue: Float): Float {
        return try {
            Settings.Global.getFloat(context.contentResolver, key, defaultValue)
        } catch (e: Exception) {
            Log.w(TAG, "Read failed for $key", e)
            defaultValue
        }
    }

    // Best-effort state broadcast for firmware services that listen for
    // tuning changes. Sending needs no manifest entry.
    fun publishAutoLockState(context: Context, enabled: Boolean, timeoutSeconds: Int, remainingSeconds: Int = -1) {
        runCatching {
            context.sendBroadcast(
                android.content.Intent(AUTO_LOCK_ACTION_STATE_CHANGED).apply {
                    putExtra(AUTO_LOCK_EXTRA_ENABLED, enabled)
                    putExtra(AUTO_LOCK_EXTRA_TIMEOUT, timeoutSeconds)
                    putExtra(AUTO_LOCK_EXTRA_REMAINING, remainingSeconds)
                },
            )
        }.onFailure { Log.w(TAG, "AutoLock publish failed", it) }
    }

    // Panel brightness nodes shared by the AutoHBM live card.
    fun readBrightness(): Int? = readIntSysfs(BRIGHTNESS_PATH)

    fun readMaxBrightness(): Int? = readIntSysfs(MAX_BRIGHTNESS_PATH)

    fun readSocTemperatureC(): Float? {
        val soc = runCatching {
            java.io.File(THERMAL_ROOT).listFiles { file -> file.name.startsWith("thermal_zone") }
                ?.mapNotNull { zone ->
                    val type = runCatching { java.io.File(zone, "type").readText().trim().lowercase() }.getOrNull()
                        ?: return@mapNotNull null
                    if (type != SOC_THERMAL_NAME) return@mapNotNull null
                    runCatching { java.io.File(zone, "temp").readText().trim().toLong() }.getOrNull()
                        ?.let(::normalizeTemperature)
                        ?.takeIf { it in 0f..150f }
                }
                ?.firstOrNull()
        }.getOrNull()
        if (soc != null) return soc
        return runCatching { java.io.File(BATTERY_TEMP_PATH).readText().trim().toLong() }.getOrNull()
            ?.let(::normalizeTemperature)
            ?.takeIf { it in 0f..150f }
    }

    fun readSocModel(): String {
        return runCatching {
            val clazz = Class.forName("android.os.SystemProperties")
            val method = clazz.getMethod("get", String::class.java, String::class.java)
            (method.invoke(null, "ro.soc.model", "") as? String)?.trim()?.takeIf { it.isNotEmpty() }
        }.getOrNull() ?: "SoC"
    }

    private fun readIntSysfs(path: String): Int? {
        return runCatching { java.io.File(path).readText().trim().toInt() }.getOrNull()
    }

    private fun normalizeTemperature(rawValue: Long): Float {
        val absValue = kotlin.math.abs(rawValue).toFloat()
        return when {
            absValue > 1000f -> absValue / 1000f
            absValue > 100f -> absValue / 10f
            else -> absValue
        }
    }

    fun isEnabled(context: Context, key: String, defaultValue: Boolean = false): Boolean {
        return getInt(context, key, if (defaultValue) 1 else 0) != 0
    }

    fun setEnabled(context: Context, key: String, enabled: Boolean): Boolean {
        return putInt(context, key, if (enabled) 1 else 0)
    }

    fun writeSaturation(context: Context, enabled: Boolean, percent: Int): Boolean {
        val clamped = percent.coerceIn(DisplayKeys.SATURATION_MIN, DisplayKeys.SATURATION_MAX)
        if (!putInt(context, DisplayKeys.SATURATION_PERCENT, clamped)) return false
        if (!setEnabled(context, DisplayKeys.SATURATION_ENABLED, enabled)) return false
        val effective = if (enabled) clamped else DisplayKeys.SATURATION_DEFAULT
        return applySaturationPercent(effective)
    }

    // Immediate preview of the stored value. Uses reflection for
    // ServiceManager so the call compiles against the plain SDK.
    fun applySaturationPercent(percent: Int): Boolean {
        val clamped = percent.coerceIn(DisplayKeys.SATURATION_MIN, DisplayKeys.SATURATION_MAX)
        val scale = if (clamped == DisplayKeys.SATURATION_DEFAULT) 1.001f else clamped / 100f
        val binder: IBinder = try {
            val manager = Class.forName("android.os.ServiceManager")
            val getter = manager.getMethod("getService", String::class.java)
            getter.invoke(null, SURFACE_FLINGER_SERVICE) as? IBinder ?: return false
        } catch (e: Exception) {
            Log.w(TAG, "SurfaceFlinger lookup failed", e)
            return false
        }
        val data = Parcel.obtain()
        return try {
            data.writeInterfaceToken(SURFACE_COMPOSER_INTERFACE)
            data.writeFloat(scale)
            binder.transact(TRANSACTION_SET_SATURATION, data, null, 0)
        } catch (e: Exception) {
            Log.w(TAG, "Saturation transaction failed", e)
            false
        } finally {
            data.recycle()
        }
    }
}
