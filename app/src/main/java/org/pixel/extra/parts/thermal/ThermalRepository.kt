package org.pixel.extra.parts.thermal

import android.content.Context
import android.provider.Settings
import org.json.JSONArray
import java.util.concurrent.atomic.AtomicLong

// Immutable snapshot of everything the thermals UI shows. Loaded off the
// main thread; the HAL and ThermalConfigs directories are never touched,
// only Settings.Global keys and system properties flow through here.
data class ThermalSnapshot(
    val available: Boolean,
    val configValue: String,
    val vendorConfig: String,
    val active: ThermalPreset,
    val queue: List<String>,
    val queueIndex: Int,
)

// UI-only persistence for the thermals section: reads the current profile
// from system properties, writes profile switches back through the legacy
// thermal_config / thermal_config_request trigger pair, and owns the QS
// tile queue stored in Settings.Global. No services, no file management.
object ThermalRepository {
    private val triggerSerial = AtomicLong(0L)

    fun load(context: Context): ThermalSnapshot {
        val available = when (getProp(ThermalKeys.PROP_THERMAL_AVAILABLE).lowercase()) {
            "1", "true" -> true
            else -> false
        }
        val configValue = getProp(ThermalKeys.PROP_CONFIG_CURRENT)
            .takeIf { it.isNotBlank() } ?: ThermalKeys.STOCK_CONFIG_VALUE
        val batteryProp = getProp(ThermalKeys.PROP_BATTERY)
        val socProp = getProp(ThermalKeys.PROP_SOC)
        val fromProps = ThermalPreset(
            battery = ThermalLevel.fromKey(batteryProp.ifBlank { null }),
            soc = ThermalLevel.fromKey(socProp.ifBlank { null }),
        )
        val fromConfig = ThermalPreset.fromId(configValue)
        val active = if (batteryProp.isBlank() && socProp.isBlank()) {
            fromConfig
        } else {
            fromProps
        }
        return ThermalSnapshot(
            available = available,
            configValue = configValue,
            vendorConfig = getProp(ThermalKeys.PROP_VENDOR_THERMAL_CONFIG),
            active = active,
            queue = readQueue(context),
            queueIndex = readQueueIndex(context),
        )
    }

    fun applyPreset(context: Context, preset: ThermalPreset): Boolean {
        val fileName = preset.id.substringAfterLast('/')
        val applied = setProp(ThermalKeys.PROP_BATTERY, preset.battery.key) &&
            setProp(ThermalKeys.PROP_SOC, preset.soc.key) &&
            setProp(ThermalKeys.PROP_CONFIG_CURRENT, fileName) &&
            setProp(ThermalKeys.PROP_CONFIG_REQUEST, nextSerial())
        return applied
    }

    fun applyQueued(context: Context, presetId: String, index: Int): Boolean {
        val applied = applyPreset(context, ThermalPreset.fromId(presetId))
        if (applied) {
            writeQueueIndex(context, index)
        }
        return applied
    }

    fun readQueue(context: Context): List<String> {
        val raw = runCatching {
            Settings.Global.getString(context.contentResolver, ThermalKeys.TILE_PROFILE_QUEUE)
        }.getOrNull().orEmpty()
        return decodeQueue(raw)
    }

    fun writeQueue(context: Context, queue: List<String>): List<String> {
        val sanitized = queue
            .map { it.trim().substringAfterLast('/') }
            .filter { it.isNotBlank() }
            .distinct()
            .take(ThermalKeys.MAX_TILE_QUEUE_SIZE)
        runCatching {
            val encoded = JSONArray().apply { sanitized.forEach(::put) }.toString()
            Settings.Global.putString(
                context.contentResolver,
                ThermalKeys.TILE_PROFILE_QUEUE,
                encoded,
            )
        }
        val currentIndex = readQueueIndex(context)
        if (currentIndex >= sanitized.size) {
            writeQueueIndex(context, -1)
        }
        return sanitized
    }

    fun addToQueue(context: Context, presetId: String): List<String> {
        val normalized = presetId.trim().substringAfterLast('/')
        if (normalized.isBlank()) return readQueue(context)
        val queue = readQueue(context)
        if (normalized in queue || queue.size >= ThermalKeys.MAX_TILE_QUEUE_SIZE) return queue
        return writeQueue(context, queue + normalized)
    }

    fun removeAt(context: Context, index: Int): List<String> {
        val queue = readQueue(context)
        if (index !in queue.indices) return queue
        return writeQueue(context, queue.filterIndexed { itemIndex, _ -> itemIndex != index })
    }

    fun move(context: Context, fromIndex: Int, toIndex: Int): List<String> {
        val queue = readQueue(context)
        if (fromIndex !in queue.indices || toIndex !in queue.indices || fromIndex == toIndex) {
            return queue
        }
        val updated = queue.toMutableList()
        val item = updated.removeAt(fromIndex)
        updated.add(toIndex, item)
        return writeQueue(context, updated)
    }

    private fun readQueueIndex(context: Context): Int {
        return runCatching {
            Settings.Global.getInt(
                context.contentResolver,
                ThermalKeys.TILE_PROFILE_QUEUE_INDEX,
                -1,
            )
        }.getOrDefault(-1)
    }

    private fun writeQueueIndex(context: Context, index: Int) {
        runCatching {
            Settings.Global.putInt(
                context.contentResolver,
                ThermalKeys.TILE_PROFILE_QUEUE_INDEX,
                index,
            )
        }
    }

    private fun decodeQueue(raw: String): List<String> {
        if (raw.isBlank()) return emptyList()
        val decoded = runCatching {
            val array = JSONArray(raw)
            List(array.length()) { index -> array.optString(index) }
        }.getOrElse {
            raw.split(',')
        }
        return decoded
            .map { it.trim().substringAfterLast('/') }
            .filter { it.isNotBlank() }
            .distinct()
            .take(ThermalKeys.MAX_TILE_QUEUE_SIZE)
    }

    private fun nextSerial(): String {
        val now = System.currentTimeMillis()
        return triggerSerial.updateAndGet { previous ->
            if (now <= previous) previous + 1L else now
        }.toString()
    }

    private fun getProp(key: String): String {
        return runCatching {
            val cls = Class.forName("android.os.SystemProperties")
            cls.getMethod("get", String::class.java, String::class.java)
                .invoke(null, key, "") as? String
        }.getOrNull().orEmpty()
    }

    private fun setProp(key: String, value: String): Boolean {
        return runCatching {
            val cls = Class.forName("android.os.SystemProperties")
            cls.getMethod("set", String::class.java, String::class.java)
                .invoke(null, key, value)
            true
        }.getOrDefault(false)
    }
}
