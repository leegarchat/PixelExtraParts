package org.pixel.customparts.utils

import android.content.Context
import android.os.PersistableBundle
import android.provider.Settings
import android.telephony.CarrierConfigManager
import android.telephony.SubscriptionManager
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale
import java.util.zip.ZipFile

/**
 * Re-applies binder actions (carrier_config overrides, etc.) for addon settings
 * that are currently enabled. Called on boot to restore transient overrides
 * that are lost when telephony/system restarts.
 */
object AddonBinderReapply {
    private const val TAG = "AddonBinderReapply"
    private const val ADDON_DIR = "/data/pixelparts/addons"
    private const val SYSTEM_ADDON_DIR = "/system_ext/etc/pixelparts/addons"
    private const val ADDON_PREFIX = "pixel_addon_"

    fun reapply(context: Context) {
        try {
            val descriptors = scanDescriptors()
            var appliedCount = 0

            descriptors.forEach { (id, entry) ->
                if (!isAddonEnabled(context, id, entry.descriptor)) return@forEach
                appliedCount += reapplyAddonBinders(context, id, entry)
            }

            Log.d(TAG, "Boot binder reapply: scanned ${descriptors.size} addon(s), applied $appliedCount action(s)")
        } catch (t: Throwable) {
            Log.e(TAG, "Boot binder reapply failed", t)
        }
    }

    private fun reapplyAddonBinders(context: Context, addonId: String, entry: DescriptorEntry): Int {
        var count = 0
        val descriptor = entry.descriptor

        val mainArray = descriptor.optJSONArray("main") ?: return 0
        for (i in 0 until mainArray.length()) {
            val mainEntry = mainArray.optJSONObject(i) ?: continue
            val settings = mainEntry.optJSONArray("settings") ?: continue
            count += processSettings(context, addonId, entry, settings)
        }

        val topSettings = descriptor.optJSONArray("settings")
        if (topSettings != null && topSettings.length() > 0) {
            count += processSettings(context, addonId, entry, topSettings)
        }

        return count
    }

    private fun processSettings(
        context: Context,
        addonId: String,
        entry: DescriptorEntry,
        settings: JSONArray
    ): Int {
        var count = 0
        for (i in 0 until settings.length()) {
            val settingObj = settings.optJSONObject(i) ?: continue
            count += processSettingNode(context, addonId, entry, settingObj)
        }
        return count
    }

    private fun processSettingNode(
        context: Context,
        addonId: String,
        entry: DescriptorEntry,
        settingObj: JSONObject
    ): Int {
        var count = 0

        // Process children (group settings)
        val children = settingObj.optJSONArray("children") ?: settingObj.optJSONArray("settings")
        if (children != null) {
            count += processSettings(context, addonId, entry, children)
        }

        val type = optStringTrim(settingObj, "type").lowercase(Locale.ROOT)
        if (type !in setOf("switch", "toggle", "checkbox")) return count

        val binderOn = parseBinderActions(settingObj, "binderOn", "binder_on", "apiOn", "api_on", "carrierConfigOn", "carrier_config_on")
        if (binderOn.isEmpty()) return count

        val key = optStringTrim(settingObj, "key")
        if (key.isBlank()) return count

        val isEnabled = readSwitchState(context, addonId, entry, settingObj, key)
        if (isEnabled) {
            applyBinderActions(context, binderOn)
            count++
            Log.d(TAG, "Re-applied binderOn for addon=$addonId key=$key")
        }

        return count
    }

    private fun readSwitchState(
        context: Context,
        addonId: String,
        entry: DescriptorEntry,
        settingObj: JSONObject,
        key: String
    ): Boolean {
        val storage = optStringTrim(settingObj, "storage").lowercase(Locale.ROOT)
        val provider = optStringTrim(settingObj, "provider").lowercase(Locale.ROOT)
        val defaultValue = settingObj.optBoolean("default", false)

        return when (storage) {
            "addon_file" -> readAddonFileSwitch(addonId, entry, key, defaultValue)
            "internal_file", "external_file" -> readAddonFileSwitch(addonId, entry, key, defaultValue)
            else -> {
                // SettingStorage.SETTINGS — read from Settings provider
                val intValue = readSettingInt(context, provider, key)
                if (intValue == null) defaultValue else intValue != 0
            }
        }
    }

    private fun readAddonFileSwitch(addonId: String, entry: DescriptorEntry, key: String, default: Boolean): Boolean {
        return try {
            val baseDir = addonDataDir(addonId, entry.jarPath, entry.isSystem)
            val safeKey = key.trim().replace(Regex("[^A-Za-z0-9._/-]"), "_").ifEmpty { "setting" }
            val segments = safeKey.split('/').filter { it.isNotEmpty() }
            val safeSegments = segments.map { it.replace(Regex("[^A-Za-z0-9._-]"), "_") }
            val parent = safeSegments.dropLast(1).fold(baseDir) { dir, seg -> File(dir, seg) }
            val file = File(parent, safeSegments.last() + ".json")
            if (!file.exists()) return default
            val json = JSONObject(file.readText(Charsets.UTF_8))
            val value = json.optString("value", "")
            when {
                value.equals("true", ignoreCase = true) || value == "1" -> true
                value.equals("false", ignoreCase = true) || value == "0" -> false
                else -> value.toIntOrNull()?.let { it != 0 } ?: default
            }
        } catch (t: Throwable) {
            Log.e(TAG, "readAddonFileSwitch($addonId/$key) failed", t)
            default
        }
    }

    private fun readSettingInt(context: Context, provider: String, key: String): Int? {
        return try {
            val raw = when (provider) {
                "secure" -> Settings.Secure.getString(context.contentResolver, key)
                "system" -> Settings.System.getString(context.contentResolver, key)
                else -> Settings.Global.getString(context.contentResolver, key)
            }
            raw?.toIntOrNull()
        } catch (_: Throwable) { null }
    }

    // =====================================================================
    // Binder action execution (carrier_config)
    // =====================================================================

    private fun applyBinderActions(context: Context, actions: List<BinderAction>) {
        val manager = context.getSystemService(CarrierConfigManager::class.java) ?: run {
            Log.w(TAG, "CarrierConfigManager not available")
            return
        }
        actions.forEach { action ->
            if (action.type != "carrier_config") return@forEach
            val subIds = resolveSubIds(context, action)
            if (subIds.isEmpty()) {
                Log.w(TAG, "No subscription IDs resolved for carrier_config reapply")
                return@forEach
            }
            val bundle = if (action.clear) null else buildBundle(action.values)
            if (!action.clear && (bundle == null || bundle.isEmpty)) return@forEach

            subIds.forEach { subId ->
                try {
                    manager.overrideConfig(subId, bundle)
                    Log.d(TAG, "carrier_config override applied for subId $subId (${action.values.size} values)")
                } catch (t: Throwable) {
                    Log.e(TAG, "carrier_config override failed for subId $subId", t)
                }
            }
        }
    }

    private fun resolveSubIds(context: Context, action: BinderAction): List<Int> {
        if (action.targetSubIds.isNotEmpty()) return action.targetSubIds.distinct()
        val active = if (action.useActiveSubscriptions) getActiveSubIds(context) else emptyList()
        return (active.ifEmpty { action.fallbackSubIds }).distinct()
    }

    private fun getActiveSubIds(context: Context): List<Int> {
        return try {
            val mgr = context.getSystemService(SubscriptionManager::class.java) ?: return emptyList()
            mgr.activeSubscriptionInfoList.orEmpty().map { it.subscriptionId }
        } catch (t: Throwable) {
            Log.e(TAG, "active subscription lookup failed", t)
            emptyList()
        }
    }

    private fun buildBundle(values: List<BinderValue>): PersistableBundle {
        val bundle = PersistableBundle()
        values.forEach { v ->
            when (v.valueType) {
                ValueType.STRING -> bundle.putString(v.key, v.value?.toString().orEmpty())
                ValueType.BOOL -> bundle.putBoolean(v.key, toBool(v.value))
                ValueType.INT -> bundle.putInt(v.key, toInt(v.value))
                ValueType.LONG -> bundle.putLong(v.key, toLong(v.value))
                ValueType.DOUBLE -> bundle.putDouble(v.key, toDouble(v.value))
                ValueType.INT_ARRAY -> bundle.putIntArray(v.key, toIntArray(v.value))
                ValueType.LONG_ARRAY -> bundle.putLongArray(v.key, toLongArray(v.value))
                ValueType.STRING_ARRAY -> bundle.putStringArray(v.key, toStringArray(v.value))
                ValueType.BOOL_ARRAY -> bundle.putBooleanArray(v.key, toBoolArray(v.value))
            }
        }
        return bundle
    }

    // =====================================================================
    // JSON parsing
    // =====================================================================

    private fun parseBinderActions(obj: JSONObject, vararg names: String): List<BinderAction> {
        for (name in names) {
            if (!obj.has(name)) continue
            return when (val value = obj.opt(name)) {
                is JSONArray -> (0 until value.length()).mapNotNull { value.optJSONObject(it)?.let { o -> parseBinderAction(o) } }
                is JSONObject -> listOfNotNull(parseBinderAction(value))
                else -> emptyList()
            }
        }
        return emptyList()
    }

    private fun parseBinderAction(obj: JSONObject): BinderAction? {
        val type = optStringTrim(obj, "type", "action", "service", "method").ifBlank { "carrier_config" }
        val normalized = type.trim().lowercase(Locale.ROOT).replace('-', '_')
        if (normalized !in setOf("carrier_config", "carrier_config_override", "override_carrier_config")) return null
        return BinderAction(
            type = "carrier_config",
            targetSubIds = parseIntList(obj.opt("subIds") ?: obj.opt("sub_ids") ?: obj.opt("subscriptions") ?: obj.opt("subscriptionIds")),
            useActiveSubscriptions = parseActiveFlag(obj),
            fallbackSubIds = parseIntList(obj.opt("fallbackSubIds") ?: obj.opt("fallback_sub_ids")),
            clear = obj.optBoolean("clear", obj.optBoolean("clearOverride", obj.optBoolean("clear_override", false))),
            values = parseBinderValues(obj.opt("values") ?: obj.opt("params") ?: obj.opt("parameters") ?: obj.opt("bundle"))
        )
    }

    private fun parseActiveFlag(obj: JSONObject): Boolean {
        val target = listOf("subIds", "sub_ids", "subscriptions", "subscriptionIds").firstNotNullOfOrNull { key ->
            obj.opt(key)?.takeIf { it is String }
        } as? String
        return when (target?.trim()?.lowercase(Locale.ROOT)) {
            "active", "active_subscriptions", "active-subscriptions" -> true
            "none", "false" -> false
            else -> obj.optBoolean("activeSubscriptions", obj.optBoolean("active_subscriptions", true))
        }
    }

    private fun parseBinderValues(raw: Any?): List<BinderValue> {
        return when (raw) {
            is JSONArray -> (0 until raw.length()).mapNotNull { (raw.optJSONObject(it))?.let { o -> parseBinderValueItem(o) } }
            is JSONObject -> {
                val singleKey = raw.optString("key", raw.optString("name", "")).trim()
                if (singleKey.isNotEmpty()) listOfNotNull(parseBinderValueItem(raw))
                else raw.keys().asSequence().map { key -> binderValueFromRaw(key, raw.opt(key), "") }.toList()
            }
            else -> emptyList()
        }
    }

    private fun parseBinderValueItem(obj: JSONObject): BinderValue? {
        val key = obj.optString("key", obj.optString("name", "")).trim()
        if (key.isEmpty()) return null
        val value = obj.opt("value") ?: obj.opt("set") ?: obj.opt("to") ?: true
        return binderValueFromRaw(key, value, obj.optString("type", obj.optString("valueType", obj.optString("value_type", ""))))
    }

    private fun binderValueFromRaw(key: String, rawValue: Any?, forcedType: String): BinderValue {
        val type = when (forcedType.trim().lowercase(Locale.ROOT)) {
            "string", "str" -> ValueType.STRING
            "bool", "boolean" -> ValueType.BOOL
            "int", "integer" -> ValueType.INT
            "long" -> ValueType.LONG
            "double", "float" -> ValueType.DOUBLE
            "int_array", "intarray", "integer_array", "integerarray", "ints" -> ValueType.INT_ARRAY
            "long_array", "longarray", "longs" -> ValueType.LONG_ARRAY
            "string_array", "stringarray", "strings" -> ValueType.STRING_ARRAY
            "bool_array", "boolean_array", "boolarray", "booleanarray", "booleans" -> ValueType.BOOL_ARRAY
            else -> inferType(rawValue)
        }
        return BinderValue(key = key.trim(), value = rawValue, valueType = type)
    }

    private fun inferType(rawValue: Any?): ValueType {
        return when (rawValue) {
            is Boolean -> ValueType.BOOL
            is Int -> ValueType.INT
            is Long -> ValueType.LONG
            is Float, is Double -> ValueType.DOUBLE
            is JSONArray -> when ((0 until rawValue.length()).firstNotNullOfOrNull { rawValue.opt(it) }) {
                is Boolean -> ValueType.BOOL_ARRAY
                is Number -> ValueType.INT_ARRAY
                else -> ValueType.STRING_ARRAY
            }
            else -> ValueType.STRING
        }
    }

    // =====================================================================
    // Descriptor scanning (mirrors AddonBootSync logic)
    // =====================================================================

    private data class DescriptorEntry(
        val descriptor: JSONObject,
        val jarPath: String,
        val isSystem: Boolean,
        val version: String
    )

    private fun scanDescriptors(): Map<String, DescriptorEntry> {
        val result = linkedMapOf<String, DescriptorEntry>()
        listOf(SYSTEM_ADDON_DIR, ADDON_DIR).forEach { dirPath ->
            val isSystemDir = dirPath == SYSTEM_ADDON_DIR
            val dir = File(dirPath)
            if (!dir.isDirectory) return@forEach
            dir.listFiles { file -> file.isFile && file.name.endsWith(".jar") }?.forEach { jar ->
                val descriptor = readDescriptor(jar) ?: return@forEach
                val id = descriptor.optString("id", descriptor.optString("entryClass", jar.nameWithoutExtension))
                if (id.isBlank()) return@forEach

                val candidate = DescriptorEntry(
                    descriptor = descriptor,
                    jarPath = jar.absolutePath,
                    isSystem = isSystemDir,
                    version = descriptor.optString("version", "1.0")
                )
                val existing = result[id]
                if (existing == null || shouldPrefer(candidate, existing)) {
                    result[id] = candidate
                }
            }
        }
        return result
    }

    private fun shouldPrefer(candidate: DescriptorEntry, existing: DescriptorEntry): Boolean {
        val cmp = compareVersions(candidate.version, existing.version)
        if (cmp != 0) return cmp > 0
        return !candidate.isSystem && existing.isSystem
    }

    private fun compareVersions(left: String, right: String): Int {
        val lp = left.split('.', '-', '_').map { it.toIntOrNull() ?: 0 }
        val rp = right.split('.', '-', '_').map { it.toIntOrNull() ?: 0 }
        val count = maxOf(lp.size, rp.size)
        for (i in 0 until count) {
            val l = lp.getOrElse(i) { 0 }
            val r = rp.getOrElse(i) { 0 }
            if (l != r) return l.compareTo(r)
        }
        return 0
    }

    private fun readDescriptor(jarFile: File): JSONObject? {
        val external = File(jarFile.absolutePath + ".json")
        if (external.exists()) {
            runCatching { return JSONObject(external.readText(Charsets.UTF_8)) }
        }
        return runCatching {
            ZipFile(jarFile).use { zip ->
                val entry = zip.getEntry("META-INF/addon.json") ?: return null
                JSONObject(zip.getInputStream(entry).bufferedReader(Charsets.UTF_8).use { it.readText() })
            }
        }.getOrNull()
    }

    private fun isAddonEnabled(context: Context, id: String, descriptor: JSONObject): Boolean {
        val key = ADDON_PREFIX + id + "_enabled"
        val raw = Settings.Global.getString(context.contentResolver, key)
        if (raw == null) return descriptor.optBoolean("enabled", true)
        return raw.toIntOrNull()?.let { it != 0 } ?: descriptor.optBoolean("enabled", true)
    }

    private fun addonDataDir(addonId: String, jarPath: String, isSystem: Boolean): File {
        return if (isSystem) {
            File("/data/pixelparts/system_addons_data", sanitize(addonId))
        } else {
            val jarFile = File(jarPath)
            File(jarFile.parentFile ?: File(ADDON_DIR), jarFile.nameWithoutExtension + "_data")
        }
    }

    private fun sanitize(value: String): String {
        return value.trim().replace(Regex("[^A-Za-z0-9._-]"), "_").ifEmpty { "setting" }
    }

    // =====================================================================
    // Value conversion helpers
    // =====================================================================

    private fun toBool(v: Any?): Boolean = when (v) {
        is Boolean -> v
        is Number -> v.toInt() != 0
        is String -> v.trim().let { it.equals("true", ignoreCase = true) || it == "1" }
        else -> false
    }

    private fun toInt(v: Any?): Int = when (v) {
        is Number -> v.toInt()
        is String -> v.trim().toIntOrNull() ?: 0
        is Boolean -> if (v) 1 else 0
        else -> 0
    }

    private fun toLong(v: Any?): Long = when (v) {
        is Number -> v.toLong()
        is String -> v.trim().toLongOrNull() ?: 0L
        is Boolean -> if (v) 1L else 0L
        else -> 0L
    }

    private fun toDouble(v: Any?): Double = when (v) {
        is Number -> v.toDouble()
        is String -> v.trim().toDoubleOrNull() ?: 0.0
        is Boolean -> if (v) 1.0 else 0.0
        else -> 0.0
    }

    private fun toIntArray(v: Any?): IntArray = toList(v).map { toInt(it) }.toIntArray()
    private fun toLongArray(v: Any?): LongArray = toList(v).map { toLong(it) }.toLongArray()
    private fun toStringArray(v: Any?): Array<String> = toList(v).map { it?.toString().orEmpty() }.toTypedArray()
    private fun toBoolArray(v: Any?): BooleanArray = toList(v).map { toBool(it) }.toBooleanArray()

    private fun toList(v: Any?): List<Any?> = when (v) {
        is JSONArray -> (0 until v.length()).map { v.opt(it) }
        is String -> v.split(',').map { it.trim() }
        null -> emptyList()
        else -> listOf(v)
    }

    private fun parseIntList(raw: Any?): List<Int> = when (raw) {
        is JSONArray -> (0 until raw.length()).mapNotNull { raw.opt(it)?.let { v -> toInt(v) } }
        is Number -> listOf(raw.toInt())
        is String -> raw.split(',').mapNotNull { it.trim().toIntOrNull() }
        else -> emptyList()
    }

    // =====================================================================
    // Internal models
    // =====================================================================

    private data class BinderAction(
        val type: String,
        val targetSubIds: List<Int> = emptyList(),
        val useActiveSubscriptions: Boolean = true,
        val fallbackSubIds: List<Int> = emptyList(),
        val clear: Boolean = false,
        val values: List<BinderValue> = emptyList()
    )

    private data class BinderValue(
        val key: String,
        val value: Any?,
        val valueType: ValueType = ValueType.BOOL
    )

    private enum class ValueType { STRING, BOOL, INT, LONG, DOUBLE, INT_ARRAY, LONG_ARRAY, STRING_ARRAY, BOOL_ARRAY }

    // =====================================================================
    // Utility
    // =====================================================================

    private fun optStringTrim(obj: JSONObject, vararg keys: String): String {
        for (key in keys) {
            val value = obj.optString(key, "").trim()
            if (value.isNotEmpty()) return value
        }
        return ""
    }
}
