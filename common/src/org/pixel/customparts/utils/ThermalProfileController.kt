package org.pixel.customparts.utils

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import org.json.JSONArray
import org.json.JSONObject
import org.pixel.customparts.SettingsKeys
import org.pixel.customparts.services.ThermalProfileService
import java.io.File
import java.util.Locale

object ThermalProfileController {
    const val CONFIG_DIR = "/data/pixelparts/ThermalConfigs"
    const val MAP_FILE_NAME = "map.json"
    const val PROFILE_METADATA_FILE_NAME = "profiles.json"
    const val STOCK_CONFIG_ID = "__stock__"
    const val FOLLOW_GLOBAL_ID = ""
    const val MAX_TILE_QUEUE_SIZE = 5
    const val PROFILE_SOURCE_SYSTEM = "system"
    const val PROFILE_SOURCE_USER = "user"
    private const val VENDOR_CONFIG_DIR = "/vendor/etc"
    private const val VENDOR_MIRROR_CONFIG_DIR = "/data/vendor/pixelparts/ThermalConfigs"
    private const val VENDOR_THERMAL_PREFIX = "thermal_info_config_"
    private const val STOCK_CONFIG_VALUE = "thermal_info_config.json"
    private const val PROP_CONFIG_CURRENT = "persist.sys.pixelparts.thermal_config"
    private const val PROP_CONFIG_REQUEST = "persist.sys.pixelparts.thermal_config_request"
    private const val PROP_VENDOR_THERMAL_CONFIG = "vendor.thermal.config"
    private const val MIRROR_CLEANUP_DELAY_MS = 5000L
    @Volatile private var lastTriggerSerial = 0L

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
                file.name != MAP_FILE_NAME && file.name != PROFILE_METADATA_FILE_NAME &&
                file.name != STOCK_CONFIG_VALUE
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
        removeSeededSystemPresets(targetDir, metadata, sources.keys)
        val copiedNames = mutableSetOf<String>()

        sources.forEach { (targetName, source) ->
            val target = File(targetDir, targetName)
            runCatching {
                copyConfigAtomically(source, target)
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
        writeTextAtomically(file, root.toString(4))
        file.setReadable(true, false)
        file.setWritable(true, true)
    }

    fun updateGlobalConfig(context: Context, configId: String): Boolean {
        val profileMap = readProfileMap().copy(globalConfig = normalizeGlobalConfigId(configId))
        writeProfileMap(profileMap)
        val applied = applyConfig(context, profileMap.globalConfig)
        syncService(context)
        return applied
    }

    fun readTileProfileQueue(context: Context): List<String> {
        val raw = Settings.Global.getString(context.contentResolver, SettingsKeys.THERMAL_TILE_PROFILE_QUEUE).orEmpty()
        val availableIds = availableTileProfileQueueIds()
        return decodeTileProfileQueue(raw)
            .map(::normalizeTileProfileQueueId)
            .filter { it in availableIds }
            .distinct()
            .take(MAX_TILE_QUEUE_SIZE)
    }

    fun writeTileProfileQueue(context: Context, queue: List<String>): List<String> {
        val availableIds = availableTileProfileQueueIds()
        val sanitized = queue
            .map(::normalizeTileProfileQueueId)
            .filter { it in availableIds }
            .distinct()
            .take(MAX_TILE_QUEUE_SIZE)
        val encoded = JSONArray().apply { sanitized.forEach(::put) }.toString()
        Settings.Global.putString(context.contentResolver, SettingsKeys.THERMAL_TILE_PROFILE_QUEUE, encoded)
        PixelPartsTileRefresher.requestForSetting(context, SettingsKeys.THERMAL_TILE_PROFILE_QUEUE)

        val currentIndex = Settings.Global.getInt(context.contentResolver, SettingsKeys.THERMAL_TILE_PROFILE_QUEUE_INDEX, -1)
        if (currentIndex >= sanitized.size) {
            Settings.Global.putInt(context.contentResolver, SettingsKeys.THERMAL_TILE_PROFILE_QUEUE_INDEX, -1)
            PixelPartsTileRefresher.requestForSetting(context, SettingsKeys.THERMAL_TILE_PROFILE_QUEUE_INDEX)
        }
        return sanitized
    }

    fun addTileProfileToQueue(context: Context, configId: String): List<String> {
        val queue = readTileProfileQueue(context)
        val normalized = normalizeTileProfileQueueId(configId)
        return if (normalized in queue || queue.size >= MAX_TILE_QUEUE_SIZE) {
            queue
        } else {
            writeTileProfileQueue(context, queue + normalized)
        }
    }

    fun removeTileProfileFromQueue(context: Context, index: Int): List<String> {
        val queue = readTileProfileQueue(context)
        if (index !in queue.indices) return queue
        return writeTileProfileQueue(context, queue.filterIndexed { itemIndex, _ -> itemIndex != index })
    }

    fun moveTileProfileQueueItem(context: Context, fromIndex: Int, toIndex: Int): List<String> {
        val queue = readTileProfileQueue(context)
        if (fromIndex !in queue.indices || toIndex !in queue.indices || fromIndex == toIndex) return queue
        val updated = queue.toMutableList()
        val item = updated.removeAt(fromIndex)
        updated.add(toIndex, item)
        return writeTileProfileQueue(context, updated)
    }

    fun cycleTileProfileQueue(context: Context): String? {
        val queue = readTileProfileQueue(context)
        if (queue.isEmpty()) return null

        val currentGlobal = normalizeTileProfileQueueId(readProfileMap().globalConfig)
        val currentIndex = queue.indexOf(currentGlobal)
        val lastIndex = Settings.Global.getInt(context.contentResolver, SettingsKeys.THERMAL_TILE_PROFILE_QUEUE_INDEX, -1)
        val nextIndex = if (currentIndex >= 0) {
            (currentIndex + 1) % queue.size
        } else {
            (lastIndex + 1).floorMod(queue.size)
        }
        val nextConfig = queue[nextIndex]
        Settings.Global.putInt(context.contentResolver, SettingsKeys.THERMAL_TILE_PROFILE_QUEUE_INDEX, nextIndex)
        PixelPartsTileRefresher.requestForSetting(context, SettingsKeys.THERMAL_TILE_PROFILE_QUEUE_INDEX)
        updateGlobalConfig(context, nextConfig)
        return nextConfig
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
        return configFileFor(normalized).isFile
    }

    fun configStateToken(configId: String): String {
        val normalized = normalizeConfigId(configId)
        if (normalized.isBlank() || normalized == STOCK_CONFIG_ID) return STOCK_CONFIG_VALUE

        val file = configFileFor(normalized)
        return if (file.isFile) {
            "${file.absolutePath}:${file.length()}:${file.lastModified()}"
        } else {
            "missing:${if (normalized.startsWith('/')) normalized else file.absolutePath}"
        }
    }

    fun applyConfig(context: Context, configId: String): Boolean {
        seedVendorConfigs()
        val normalized = normalizeConfigId(configId)
        if (normalized.isNotBlank() && normalized != STOCK_CONFIG_ID && !isConfigAvailable(normalized)) {
            return false
        }

        val propertyValue = resolvePropertyValue(configId)
        val sourceFile = propertyValue.takeIf { it.startsWith('/') }?.let(::File)
        if (sourceFile != null && (!sourceFile.isFile || sourceFile.length() <= 0L)) return false

        val requestName = sourceFile?.name?.takeIf { it.isNotBlank() } ?: STOCK_CONFIG_VALUE
        val applied = setAndVerifySystemProperty(PROP_CONFIG_CURRENT, requestName) &&
            setSystemProperty(PROP_CONFIG_REQUEST, nextTriggerSerial())
        if (applied) {
            scheduleMirroredConfigCleanup("active.json")
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

    private fun availableTileProfileQueueIds(): Set<String> {
        return listConfigChoices(includeFollowGlobal = false)
            .map { normalizeTileProfileQueueId(it.id) }
            .toSet()
    }

    private fun normalizeTileProfileQueueId(configId: String): String {
        val normalized = normalizeConfigId(configId)
        return if (normalized.isBlank()) STOCK_CONFIG_ID else normalized.substringAfterLast('/')
    }

    private fun decodeTileProfileQueue(raw: String): List<String> {
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            List(array.length()) { index -> array.optString(index) }
        }.getOrElse {
            raw.split(',')
        }
    }

    private fun Int.floorMod(other: Int): Int = ((this % other) + other) % other

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
        writeTextAtomically(file, root.toString(4))
        file.setReadable(true, false)
        file.setWritable(true, true)
    }

    private fun removeSeededSystemPresets(
        targetDir: File,
        metadata: MutableMap<String, ThermalProfileMetadata>,
        currentPresetNames: Set<String>
    ) {
        val systemNames = metadata.values
            .filter { it.source == PROFILE_SOURCE_SYSTEM }
            .map { it.id }
            .toMutableSet()

        targetDir.listFiles { file -> file.isFile && isGeneratedPresetName(file.name) }
            ?.forEach { systemNames += it.name }

        systemNames
            .filterNot { it in currentPresetNames }
            .forEach { name ->
                File(targetDir, name).delete()
                metadata.remove(name)
            }
    }

    private fun writeTextAtomically(file: File, text: String) {
        val temp = File(file.parentFile, ".${file.name}.${SystemClock.elapsedRealtimeNanos()}.tmp")
        temp.writeText(text)
        temp.setReadable(true, false)
        temp.setWritable(true, true)
        if (!temp.renameTo(file)) {
            temp.delete()
            error("Unable to replace ${file.absolutePath}")
        }
    }

    private fun copyConfigAtomically(source: File, target: File) {
        if (target.isFile && filesHaveSameBytes(source, target)) {
            target.setReadable(true, false)
            target.setWritable(true, true)
            return
        }

        val temp = File(target.parentFile, ".${target.name}.${SystemClock.elapsedRealtimeNanos()}.tmp")
        source.copyTo(temp, overwrite = true)
        temp.setReadable(true, false)
        temp.setWritable(true, true)
        if (!temp.renameTo(target)) {
            temp.delete()
            error("Unable to replace ${target.absolutePath}")
        }
        target.setReadable(true, false)
        target.setWritable(true, true)
    }

    private fun filesHaveSameBytes(first: File, second: File): Boolean {
        if (!first.isFile || !second.isFile || first.length() != second.length()) return false

        first.inputStream().use { firstInput ->
            second.inputStream().use { secondInput ->
                val firstBuffer = ByteArray(DEFAULT_BUFFER_SIZE)
                val secondBuffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val firstRead = firstInput.read(firstBuffer)
                    val secondRead = secondInput.read(secondBuffer)
                    if (firstRead != secondRead) return false
                    if (firstRead < 0) return true
                    for (index in 0 until firstRead) {
                        if (firstBuffer[index] != secondBuffer[index]) return false
                    }
                }
            }
        }
    }

    private fun configFileFor(normalizedConfigId: String): File {
        return if (normalizedConfigId.startsWith('/')) {
            File(normalizedConfigId)
        } else {
            File(ensureConfigDir(), normalizedConfigId)
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
        val configPath = value.substringAfter(',', value).trim()
        return if (configPath.startsWith("$VENDOR_MIRROR_CONFIG_DIR/")) {
            File(configPath).name.takeIf { it.isNotBlank() }
        } else {
            null
        }
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

    private fun setAndVerifySystemProperty(key: String, value: String): Boolean {
        if (!setSystemProperty(key, value)) return false
        repeat(5) {
            if (getSystemProperty(key) == value) return true
            SystemClock.sleep(10L)
        }
        return getSystemProperty(key) == value
    }

    @Synchronized
    private fun nextTriggerSerial(): String {
        val now = System.currentTimeMillis()
        val serial = if (now <= lastTriggerSerial) lastTriggerSerial + 1L else now
        lastTriggerSerial = serial
        return serial.toString()
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