package org.pixel.customparts.utils

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import org.pixel.customparts.services.ThermalProfileService
import java.io.File
import java.util.Locale

object ThermalProfileController {
    const val CONFIG_DIR = "/data/pixelparts/ThermalConfigs"
    const val MAP_FILE_NAME = "map.json"
    const val PROFILE_METADATA_FILE_NAME = "profiles.json"
    const val STOCK_CONFIG_ID = "__stock__"
    const val FOLLOW_GLOBAL_ID = ""
    const val PROFILE_SOURCE_SYSTEM = "system"
    const val PROFILE_SOURCE_USER = "user"
    private const val VENDOR_CONFIG_DIR = "/vendor/etc"
    private const val VENDOR_MIRROR_CONFIG_DIR = "/data/vendor/pixelparts/ThermalConfigs"
    private const val VENDOR_THERMAL_PREFIX = "thermal_info_config_"
    private const val STOCK_CONFIG_VALUE = "thermal_info_config.json"
    private const val PROP_CONFIG_REQUEST = "sys.pixelparts.thermal_config_request"
    private const val PROP_CONFIG_FILE_REQUEST = "sys.pixelparts.thermal_config_file_request"
    private const val PROP_VENDOR_THERMAL_CONFIG = "vendor.thermal.config"
    private const val MIRROR_CLEANUP_DELAY_MS = 5000L

    fun listConfigChoices(includeFollowGlobal: Boolean = false): List<ThermalConfigChoice> {
        seedVendorConfigs()
        val metadata = readProfileMetadataMap()
        val choices = mutableListOf<ThermalConfigChoice>()
        if (includeFollowGlobal) {
            choices += ThermalConfigChoice(
                id = FOLLOW_GLOBAL_ID,
                label = "Follow global",
                fileName = null,
                propertyValue = null,
                builtIn = true,
                source = PROFILE_SOURCE_SYSTEM
            )
        }
        choices += ThermalConfigChoice(
            id = STOCK_CONFIG_ID,
            label = "Stock",
            fileName = STOCK_CONFIG_VALUE,
            propertyValue = STOCK_CONFIG_VALUE,
            builtIn = true,
            source = PROFILE_SOURCE_SYSTEM
        )
        val vendorNames = vendorPresetNames()
        choices += ensureConfigDir().listFiles { file ->
            file.isFile && file.name.endsWith(".json", ignoreCase = true) &&
                file.name != MAP_FILE_NAME && file.name != PROFILE_METADATA_FILE_NAME
        }?.sortedBy { it.name.lowercase() }?.map { file ->
            val itemMetadata = metadata[file.name]
            val source = itemMetadata?.source ?: if (vendorNames.contains(file.name)) PROFILE_SOURCE_SYSTEM else PROFILE_SOURCE_USER
            ThermalConfigChoice(
                id = file.name,
                label = itemMetadata?.displayName?.takeIf { it.isNotBlank() } ?: file.name.removeSuffix(".json"),
                fileName = file.name,
                propertyValue = file.absolutePath,
                builtIn = source == PROFILE_SOURCE_SYSTEM,
                source = source
            )
        }.orEmpty()
        return choices.sortedWith(
            compareBy<ThermalConfigChoice> { !it.builtIn }
                .thenBy { it.label.lowercase(Locale.US) }
                .thenBy { it.fileName.orEmpty().lowercase(Locale.US) }
        )
    }

    fun seedVendorConfigs(): Set<String> {
        val targetDir = ensureConfigDir()
        val sources = vendorPresetSources()
        if (sources.isEmpty()) return emptySet()

        val metadata = readProfileMetadataMap().toMutableMap()
        removeSeededSystemPresets(targetDir, metadata)
        val copiedNames = mutableSetOf<String>()

        sources.forEach { (targetName, source) ->
            val target = File(targetDir, targetName)
            runCatching {
                source.copyTo(target, overwrite = true)
                target.setReadable(true, false)
                target.setWritable(true, true)
                metadata[targetName] = ThermalProfileMetadata(
                    id = targetName,
                    displayName = vendorPresetDisplayName(source.name),
                    source = PROFILE_SOURCE_SYSTEM,
                    fileName = targetName,
                    path = target.absolutePath
                )
                copiedNames += targetName
            }
        }
        writeProfileMetadataMap(metadata)
        return copiedNames
    }

    fun isVendorPreset(configId: String): Boolean {
        val normalized = normalizeConfigId(configId).substringAfterLast('/')
        return readProfileMetadataMap()[normalized]?.source == PROFILE_SOURCE_SYSTEM || vendorPresetNames().contains(normalized)
    }

    fun profileMetadata(configId: String): ThermalProfileMetadata? {
        val normalized = normalizeConfigId(configId).substringAfterLast('/')
        if (normalized.isBlank() || normalized == STOCK_CONFIG_ID) return null
        return readProfileMetadataMap()[normalized]
    }

    fun writeUserProfileMetadata(fileName: String, displayName: String, path: String) {
        val normalizedName = fileName.substringAfterLast('/')
        if (normalizedName.isBlank() || normalizedName == MAP_FILE_NAME || normalizedName == PROFILE_METADATA_FILE_NAME) return

        val metadata = readProfileMetadataMap().toMutableMap()
        metadata[normalizedName] = ThermalProfileMetadata(
            id = normalizedName,
            displayName = displayName.trim().takeIf { it.isNotBlank() } ?: normalizedName.removeSuffix(".json"),
            source = PROFILE_SOURCE_USER,
            fileName = normalizedName,
            path = path
        )
        writeProfileMetadataMap(metadata)
    }

    fun removeProfileMetadata(configId: String) {
        val normalized = normalizeConfigId(configId).substringAfterLast('/')
        if (normalized.isBlank()) return

        val metadata = readProfileMetadataMap().toMutableMap()
        if (metadata.remove(normalized) != null) {
            writeProfileMetadataMap(metadata)
        }
    }

    fun readProfileMap(): ThermalProfileMap {
        val file = mapFile()
        if (!file.exists()) return ThermalProfileMap()

        return runCatching {
            val root = JSONObject(file.readText())
            val packages = root.optJSONObject("packages") ?: JSONObject()
            val packageMap = mutableMapOf<String, String>()
            val keys = packages.keys()
            while (keys.hasNext()) {
                val packageName = keys.next()
                val config = packages.optString(packageName).takeIf { it.isNotBlank() } ?: continue
                packageMap[packageName] = config
            }

            ThermalProfileMap(
                globalConfig = normalizeConfigId(root.optString("globalConfig")),
                packageConfigs = packageMap
            )
        }.getOrElse { ThermalProfileMap() }
    }

    fun hasProfileMap(): Boolean = mapFile().exists()

    fun writeProfileMap(profileMap: ThermalProfileMap) {
        val packages = JSONObject()
        profileMap.packageConfigs.toSortedMap().forEach { (packageName, config) ->
            val normalized = normalizeConfigId(config)
            if (normalized.isNotBlank()) {
                packages.put(packageName, normalized)
            }
        }

        val root = JSONObject()
            .put("globalConfig", normalizeConfigId(profileMap.globalConfig).takeUnless { it == STOCK_CONFIG_ID }.orEmpty())
            .put("packages", packages)

        val file = mapFile()
        file.writeText(root.toString(4))
        file.setReadable(true, false)
        file.setWritable(true, true)
    }

    fun updateGlobalConfig(context: Context, configId: String) {
        val profileMap = readProfileMap().copy(globalConfig = normalizeGlobalConfigId(configId))
        writeProfileMap(profileMap)
        applyConfig(context, profileMap.globalConfig)
        syncService(context)
    }

    fun updatePackageConfig(context: Context, packageName: String, configId: String) {
        val normalized = normalizeConfigId(configId)
        val profileMap = readProfileMap()
        val updatedPackages = profileMap.packageConfigs.toMutableMap()
        if (normalized.isBlank()) {
            updatedPackages.remove(packageName)
        } else {
            updatedPackages[packageName] = normalized
        }
        writeProfileMap(profileMap.copy(packageConfigs = updatedPackages))
        syncService(context)
    }

    fun effectiveConfigForPackage(packageName: String): String {
        val profileMap = readProfileMap()
        return profileMap.packageConfigs[packageName]?.takeIf { it.isNotBlank() }
            ?: profileMap.globalConfig.takeIf { it.isNotBlank() }
            ?: STOCK_CONFIG_ID
    }

    fun resolvePropertyValue(configId: String): String {
        val normalized = normalizeConfigId(configId)
        if (normalized.isBlank() || normalized == STOCK_CONFIG_ID) return STOCK_CONFIG_VALUE

        val file = if (normalized.startsWith('/')) {
            File(normalized)
        } else {
            File(ensureConfigDir(), normalized)
        }
        return if (file.exists()) file.absolutePath else STOCK_CONFIG_VALUE
    }

    fun isConfigAvailable(configId: String): Boolean {
        val normalized = normalizeConfigId(configId)
        if (normalized.isBlank() || normalized == STOCK_CONFIG_ID) return true
        return File(ensureConfigDir(), normalized).exists()
    }

    fun applyConfig(context: Context, configId: String): Boolean {
        val propertyValue = resolvePropertyValue(configId)
        val activeMirrorName: String?
        val applied = if (propertyValue.startsWith('/')) {
            val fileName = File(propertyValue).name.takeIf { it.isNotBlank() } ?: return false
            activeMirrorName = fileName
            setSystemProperty(PROP_CONFIG_FILE_REQUEST, fileName)
        } else {
            activeMirrorName = null
            setSystemProperty(PROP_CONFIG_REQUEST, propertyValue)
        }
        if (applied) {
            scheduleMirroredConfigCleanup(activeMirrorName)
        }
        return applied
    }

    fun syncService(context: Context) {
        seedVendorConfigs()
        val mapExists = hasProfileMap()
        val profileMap = readProfileMap()
        val hasPackageRules = profileMap.packageConfigs.isNotEmpty()
        if (!mapExists && !hasPackageRules && profileMap.globalConfig.isBlank()) {
            return
        }

        val globalNeedsRetry = profileMap.globalConfig.isNotBlank() && !isConfigAvailable(profileMap.globalConfig)
        if (hasPackageRules || globalNeedsRetry) {
            context.startService(Intent(context, ThermalProfileService::class.java))
        } else {
            context.stopService(Intent(context, ThermalProfileService::class.java))
            applyConfig(context, profileMap.globalConfig)
        }
    }

    fun displayName(configId: String, includeFollowGlobal: Boolean = false): String {
        val normalized = normalizeConfigId(configId)
        return when {
            includeFollowGlobal && normalized.isBlank() -> "Follow global"
            normalized.isBlank() || normalized == STOCK_CONFIG_ID -> "Stock"
            else -> readProfileMetadataMap()[normalized.substringAfterLast('/')]?.displayName
                ?: normalized.substringAfterLast('/').removeSuffix(".json")
        }
    }

    fun normalizeConfigId(configId: String): String {
        val trimmed = configId.trim()
        return when (trimmed) {
            STOCK_CONFIG_VALUE -> STOCK_CONFIG_ID
            else -> trimmed
        }
    }

    private fun normalizeGlobalConfigId(configId: String): String {
        val normalized = normalizeConfigId(configId)
        return if (normalized == STOCK_CONFIG_ID) FOLLOW_GLOBAL_ID else normalized
    }

    private fun ensureConfigDir(): File {
        val dir = File(CONFIG_DIR)
        if (!dir.exists() && !dir.mkdirs()) {
            error("Unable to create $CONFIG_DIR")
        }
        dir.setReadable(true, false)
        dir.setExecutable(true, false)
        return dir
    }

    private fun mapFile(): File = File(ensureConfigDir(), MAP_FILE_NAME)

    private fun metadataFile(): File = File(ensureConfigDir(), PROFILE_METADATA_FILE_NAME)

    private fun readProfileMetadataMap(): Map<String, ThermalProfileMetadata> {
        val file = metadataFile()
        if (!file.exists()) return emptyMap()

        return runCatching {
            val root = JSONObject(file.readText())
            val profiles = root.optJSONObject("profiles") ?: root
            val result = mutableMapOf<String, ThermalProfileMetadata>()
            val keys = profiles.keys()
            while (keys.hasNext()) {
                val id = keys.next()
                val item = profiles.optJSONObject(id) ?: continue
                val fileName = item.optString("fileName").takeIf { it.isNotBlank() } ?: id
                result[id] = ThermalProfileMetadata(
                    id = id,
                    displayName = item.optString("displayName").takeIf { it.isNotBlank() }
                        ?: item.optString("name").takeIf { it.isNotBlank() }
                        ?: fileName.removeSuffix(".json"),
                    source = item.optString("source").takeIf { it == PROFILE_SOURCE_SYSTEM || it == PROFILE_SOURCE_USER }
                        ?: PROFILE_SOURCE_USER,
                    fileName = fileName,
                    path = item.optString("path").takeIf { it.isNotBlank() }
                        ?: File(ensureConfigDir(), fileName).absolutePath
                )
            }
            result
        }.getOrElse { emptyMap() }
    }

    private fun writeProfileMetadataMap(metadata: Map<String, ThermalProfileMetadata>) {
        val profiles = JSONObject()
        metadata.toSortedMap().forEach { (id, item) ->
            profiles.put(
                id,
                JSONObject()
                    .put("displayName", item.displayName)
                    .put("source", item.source)
                    .put("fileName", item.fileName)
                    .put("path", item.path)
            )
        }

        val root = JSONObject()
            .put("version", 1)
            .put("profiles", profiles)

        val file = metadataFile()
        file.writeText(root.toString(4))
        file.setReadable(true, false)
        file.setWritable(true, true)
    }

    private fun removeSeededSystemPresets(targetDir: File, metadata: MutableMap<String, ThermalProfileMetadata>) {
        val systemNames = metadata.values
            .filter { it.source == PROFILE_SOURCE_SYSTEM }
            .map { it.id }
            .toMutableSet()

        targetDir.listFiles { file -> file.isFile && isGeneratedPresetName(file.name) }
            ?.forEach { systemNames += it.name }

        systemNames.forEach { name ->
            File(targetDir, name).delete()
            metadata.remove(name)
        }
    }

    private fun vendorPresetNames(): Set<String> {
        return vendorPresetSources().keys
    }

    private fun vendorPresetSources(): Map<String, File> {
        val vendorDir = File(VENDOR_CONFIG_DIR)
        return vendorDir.listFiles { file ->
            file.isFile &&
                file.name.startsWith(VENDOR_THERMAL_PREFIX) &&
                file.name.endsWith(".json", ignoreCase = true)
        }.orEmpty().mapNotNull { source ->
            val targetName = vendorPresetName(source.name) ?: return@mapNotNull null
            targetName to source
        }.toMap()
    }

    private fun vendorPresetName(sourceName: String): String? {
        val suffix = sourceName
            .removePrefix(VENDOR_THERMAL_PREFIX)
            .removeSuffix(".json")
            .lowercase(Locale.US)
        if (suffix.isBlank() || suffix == "config") return null

        val battery = Regex("(?:^|_)battery_(soft|medium|hard|off)(?:_|$)")
            .find(suffix)?.groupValues?.getOrNull(1)
        val soc = Regex("(?:^|_)soc_(soft|medium|hard|off)(?:_|$)")
            .find(suffix)?.groupValues?.getOrNull(1)
        val parts = mutableListOf<String>()
        if (battery != null) parts += "battery_$battery"
        if (soc != null) parts += "soc_$soc"
        if (parts.isEmpty()) return null
        return parts.joinToString("_") + ".json"
    }

    private fun vendorPresetDisplayName(sourceName: String): String {
        val suffix = sourceName
            .removePrefix(VENDOR_THERMAL_PREFIX)
            .removeSuffix(".json")
            .lowercase(Locale.US)
        val battery = Regex("(?:^|_)battery_(soft|medium|hard|off)(?:_|$)")
            .find(suffix)?.groupValues?.getOrNull(1)
        val soc = Regex("(?:^|_)soc_(soft|medium|hard|off)(?:_|$)")
            .find(suffix)?.groupValues?.getOrNull(1)
        val parts = mutableListOf<String>()
        battery?.let { parts += "Battery +${thermalOffsetLabel(it)}c" }
        soc?.let { parts += "Soc +${thermalOffsetLabel(it)}c" }
        return parts.takeIf { it.isNotEmpty() }?.joinToString("; ") ?: sourceName.removeSuffix(".json")
    }

    private fun thermalOffsetLabel(level: String): Int = when (level) {
        "soft" -> 5
        "medium" -> 9
        "hard" -> 15
        "off" -> 90
        else -> 0
    }

    private fun isGeneratedPresetName(fileName: String): Boolean {
        val name = fileName.removeSuffix(".json")
        if (name == fileName) return false
        val parts = name.split('_')
        if (parts.size % 2 != 0) return false
        var index = 0
        var matched = false
        while (index < parts.size) {
            val category = parts[index]
            val level = parts.getOrNull(index + 1) ?: return false
            if (category != "battery" && category != "soc") return false
            if (level !in setOf("soft", "medium", "hard", "off")) return false
            matched = true
            index += 2
        }
        return matched
    }

    private fun scheduleMirroredConfigCleanup(activeFileName: String?) {
        runCatching {
            Handler(Looper.getMainLooper()).postDelayed(
                {
                    val currentMirrorName = currentMirroredConfigName()
                    if (activeFileName == null || currentMirrorName != null) {
                        cleanupMirroredConfigs(currentMirrorName)
                    }
                },
                MIRROR_CLEANUP_DELAY_MS
            )
        }
    }

    private fun currentMirroredConfigName(): String? {
        val value = getSystemProperty(PROP_VENDOR_THERMAL_CONFIG).takeIf { it.isNotBlank() } ?: return null
        return if (value.startsWith("$VENDOR_MIRROR_CONFIG_DIR/")) File(value).name.takeIf { it.isNotBlank() } else null
    }

    private fun cleanupMirroredConfigs(activeFileName: String?) {
        val dir = File(VENDOR_MIRROR_CONFIG_DIR)
        if (!dir.exists() || !dir.isDirectory) return

        dir.listFiles { file ->
            file.isFile &&
                file.name.endsWith(".json", ignoreCase = true) &&
                (activeFileName == null || file.name != activeFileName)
        }?.forEach { file ->
            runCatching { file.delete() }
        }
    }

    private fun setSystemProperty(key: String, value: String): Boolean = try {
        val systemProperties = Class.forName("android.os.SystemProperties")
        systemProperties.getMethod("set", String::class.java, String::class.java).invoke(null, key, value)
        true
    } catch (_: Throwable) {
        false
    }

    private fun getSystemProperty(key: String): String = try {
        val systemProperties = Class.forName("android.os.SystemProperties")
        systemProperties.getMethod("get", String::class.java, String::class.java).invoke(null, key, "") as? String ?: ""
    } catch (_: Throwable) {
        ""
    }
}

data class ThermalProfileMap(
    val globalConfig: String = ThermalProfileController.FOLLOW_GLOBAL_ID,
    val packageConfigs: Map<String, String> = emptyMap()
)

data class ThermalConfigChoice(
    val id: String,
    val label: String,
    val fileName: String?,
    val propertyValue: String?,
    val builtIn: Boolean,
    val source: String
)

data class ThermalProfileMetadata(
    val id: String,
    val displayName: String,
    val source: String,
    val fileName: String,
    val path: String
)