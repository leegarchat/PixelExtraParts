package org.pixel.customparts.ui.addons

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.os.PersistableBundle
import android.provider.Settings
import android.telephony.CarrierConfigManager
import android.telephony.SubscriptionManager
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipFile
import java.net.URL
import java.util.Locale
import org.pixel.customparts.R
import org.pixel.customparts.dynamicDarkColorScheme
import org.pixel.customparts.dynamicLightColorScheme
import org.pixel.customparts.activities.AddonPageActivity
import org.pixel.customparts.core.ApkModulesGate
import org.pixel.customparts.ui.ColorPickerDialog
import org.pixel.customparts.ui.ExpandableWarningCard
import org.pixel.customparts.utils.HookKeyCompat
import org.pixel.customparts.utils.PixelPartsTileRefresher
import org.pixel.customparts.utils.TileUtils
import org.pixel.customparts.utils.runShellCommandForResult

private const val TAG = "AddonManagerUI"
private const val ADDON_DIR = "/data/pixelparts/addons"
private const val SYSTEM_ADDON_DIR = "/system_ext/etc/pixelparts/addons"
private const val ADDON_PREFIX = "pixel_addon_"
private const val INJECT_PREFIX = "pixel_extra_parts_inject_package_"
private const val SAFE_MODE_SETTING = "pixel_addon_safe_mode"
private const val IGNORED_PACKAGE = "android" // system_server — can't hook reliably
private const val DYNAMIC_ADDON_TILE_COUNT = 40

// =====================================================================
// Data model
// =====================================================================

internal fun scanAddons(context: Context): List<AddonUiModel> {
    val result = mutableListOf<AddonUiModel>()

    for (candidate in scanPreferredAddonJars(context)) {
        val file = candidate.file
        val json = candidate.descriptor
        val id = candidate.id
        val isSystemDir = candidate.isSystem
        try {
            val entryClassStr = json.optString("entryClass", "")
            val name = json.optString("name", id)
            val author = json.optString("author", context.getString(R.string.addon_author_unknown))
            val description = json.optString("description", "")
            val version = json.optString("version", "1.0")
            val defaultEnabled = json.optBoolean("enabled", true)

            val defaultTargets = mutableSetOf<String>()
            val arr = json.optJSONArray("targetPackages")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val pkg = arr.optString(i)
                    if (pkg.isNotEmpty()) defaultTargets.add(pkg)
                }
            }

            // Extract icon and background from JAR
            val iconPath = json.optString("icon", "")
            val bgPath = json.optString("background", "")
            val bgMode = json.optString("backgroundMode", "gradient")
            val bgAlpha = json.optInt("backgroundAlpha", 50).coerceIn(0, 100)
            val bgGradientStepsArr = json.optJSONArray("backgroundGradientSteps")
            val bgGradientSteps = if (bgGradientStepsArr != null && bgGradientStepsArr.length() >= 2) {
                (0 until bgGradientStepsArr.length()).map { bgGradientStepsArr.optInt(it, 0).coerceIn(0, 100) }
            } else listOf(0, 100)
            val bgBlur = json.optBoolean("backgroundBlur", false)
            val bgBlurRadius = json.optInt("backgroundBlurRadius", 25).coerceIn(0, 100)
            val cardColorStr = json.optString("cardColor", "")
            val bgScope = json.optString("backgroundScope", "full")
            val accentColorStr = json.optString("accent", json.optString("accentColor", ""))
            val updateUrl = json.optString("updateUrl", json.optString("otaUrl", ""))
            val iconBitmap = extractBitmapFromJar(file, iconPath)
            val bgBitmap = extractBitmapFromJar(file, bgPath)

            // Parse main[] entries for this addon
            val mainFlat = parseMainEntries(json, id, file.absolutePath, isSystemDir, file)
            val mainTopLevel = buildMainEntryTree(mainFlat.filter { it.targetActivity.isBlank() })
                .sortedWith(compareByDescending<AddonMainEntry> { it.priority }.thenBy { it.title })
            val modelIsSystem = isSystemDir || candidate.systemJarPath.isNotBlank()

            result += AddonUiModel(
                id = id, entryClass = entryClassStr, name = name, author = author, description = description,
                version = version, jarPath = file.absolutePath,
                defaultTargets = defaultTargets,
                enabled = readAddonEnabled(context, id, defaultEnabled),
                scopeMode = readScopeMode(context, id),
                customTargets = readCustomTargets(context, id),
                settings = parseSettings(json),
                isSystem = modelIsSystem,
                settingsOnly = entryClassStr.isEmpty(),
                iconBitmap = iconBitmap,
                backgroundBitmap = bgBitmap,
                backgroundMode = bgMode,
                backgroundAlpha = bgAlpha,
                backgroundGradientSteps = bgGradientSteps,
                backgroundBlur = bgBlur,
                backgroundBlurRadius = bgBlurRadius,
                cardColor = cardColorStr,
                backgroundScope = bgScope,
                accentColor = accentColorStr,
                updateUrl = updateUrl,
                hasDataOverride = candidate.hasDataOverride,
                systemJarPath = candidate.systemJarPath,
                dataOverrideJarPath = candidate.dataOverrideJarPath,
                mainEntries = mainTopLevel
            )
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to read addon: ${file.name}", t)
        }
    }
    return result
}

internal fun readDescriptor(jarFile: File): org.json.JSONObject? = readDescriptor(jarFile, null)

internal fun readDescriptor(jarFile: File, context: Context?): org.json.JSONObject? {
    val base = readBaseDescriptor(jarFile) ?: return null
    val language = context?.resources?.configuration?.locales?.get(0)?.language
        ?: Locale.getDefault().language
    if (language.isBlank() || language == "en") return base

    val localized = readInlineLocalizedDescriptor(base, language)
        ?: readLocalizedDescriptor(jarFile, language)
        ?: return base
    return mergeLocalizedDescriptor(base, localized)
}

private fun readInlineLocalizedDescriptor(base: JSONObject, language: String): JSONObject? {
    val locales = base.optJSONObject("locales")
        ?: base.optJSONObject("i18n")
        ?: base.optJSONObject("translations")
        ?: return null
    val safeLanguage = language.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9_-]"), "")
    if (safeLanguage.isBlank()) return null
    val languagePrefix = safeLanguage.substringBefore('_').substringBefore('-')
    return locales.optJSONObject(safeLanguage) ?: locales.optJSONObject(languagePrefix)
}

private fun readBaseDescriptor(jarFile: File): org.json.JSONObject? {
    val ext = File(jarFile.absolutePath + ".json")
    if (ext.exists()) {
        try { return org.json.JSONObject(ext.readText(Charsets.UTF_8)) } catch (_: Throwable) {}
    }
    try {
        java.util.zip.ZipFile(jarFile).use { zip ->
            val entry = zip.getEntry("META-INF/addon.json") ?: return null
            val text = zip.getInputStream(entry).bufferedReader(Charsets.UTF_8).readText()
            return org.json.JSONObject(text)
        }
    } catch (_: Throwable) { return null }
}

private fun readLocalizedDescriptor(jarFile: File, language: String): JSONObject? {
    val safeLanguage = language.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9_-]"), "")
    if (safeLanguage.isBlank()) return null

    val externalCandidates = listOf(
        File(jarFile.parentFile, "addon_$safeLanguage.json"),
        File(jarFile.absolutePath.removeSuffix(".jar") + "_$safeLanguage.json")
    )
    externalCandidates.firstOrNull { it.exists() }?.let { file ->
        runCatching { return JSONObject(file.readText(Charsets.UTF_8)) }
    }

    return try {
        java.util.zip.ZipFile(jarFile).use { zip ->
            val entry = zip.getEntry("META-INF/addon_$safeLanguage.json") ?: return null
            val text = zip.getInputStream(entry).bufferedReader(Charsets.UTF_8).readText()
            JSONObject(text)
        }
    } catch (_: Throwable) { null }
}

private fun mergeLocalizedDescriptor(base: JSONObject, localized: JSONObject): JSONObject {
    val merged = JSONObject(base.toString())
    listOf("name", "author", "description").forEach { key ->
        if (localized.has(key)) merged.put(key, localized.optString(key, merged.optString(key)))
    }
    val baseSettings = merged.optJSONArray("settings")
    val localizedSettings = localized.optJSONArray("settings")
    if (baseSettings != null && localizedSettings != null) {
        mergeLocalizedSettings(baseSettings, localizedSettings)
    }
    val baseMain = merged.optJSONArray("main")
    val localizedMain = localized.optJSONArray("main")
    if (baseMain != null && localizedMain != null) {
        mergeLocalizedMainEntries(baseMain, localizedMain)
    }
    return merged
}

private fun mergeLocalizedMainEntries(baseMain: JSONArray, localizedMain: JSONArray) {
    val localizedById = mutableMapOf<String, JSONObject>()
    for (index in 0 until localizedMain.length()) {
        val localized = localizedMain.optJSONObject(index) ?: continue
        val id = localized.optString("id", "")
        if (id.isNotBlank()) localizedById[id] = localized
    }
    for (index in 0 until baseMain.length()) {
        val base = baseMain.optJSONObject(index) ?: continue
        val localized = localizedById[base.optString("id", "")] ?: continue
        mergeLocalizedMainEntryText(base, localized)
        val baseSettings = base.optJSONArray("settings") ?: base.optJSONArray("children")
        val localizedSettings = localized.optJSONArray("settings") ?: localized.optJSONArray("children")
        if (baseSettings != null && localizedSettings != null) {
            mergeLocalizedSettings(baseSettings, localizedSettings)
        }
    }
}

private fun mergeLocalizedMainEntryText(base: JSONObject, localized: JSONObject) {
    listOf("title", "group").forEach { field ->
        if (localized.has(field)) base.put(field, localized.optString(field, base.optString(field)))
    }

    when {
        localized.has("subtitle") -> base.put("subtitle", localized.optString("subtitle", base.optString("subtitle")))
        localized.has("description") -> base.put("subtitle", localized.optString("description", base.optString("subtitle")))
        localized.has("title") -> base.put("subtitle", "")
    }

    when {
        localized.has("description") -> base.put("description", localized.optString("description", base.optString("description")))
        localized.has("subtitle") -> base.put("description", localized.optString("subtitle", base.optString("description")))
        localized.has("title") -> base.put("description", "")
    }
}

private fun mergeLocalizedSettings(baseSettings: JSONArray, localizedSettings: JSONArray) {
    val localizedByKey = mutableMapOf<String, JSONObject>()
    collectLocalizedSettingsByKey(localizedSettings, localizedByKey)
    for (index in 0 until baseSettings.length()) {
        val base = baseSettings.optJSONObject(index) ?: continue
        val localized = localizedByKey[base.optString("key", "")] ?: continue
        if (localized.has("title")) {
            base.put("title", localized.optString("title", base.optString("title")))
        }
        when {
            localized.has("description") -> base.put("description", localized.optString("description", base.optString("description")))
            localized.has("title") -> base.put("description", "")
        }
        mergeLocalizedAliasText(base, localized, "categoryTitle", "categoryTitle", "category_title", "sectionTitle", "section_title")
        mergeLocalizedAliasText(base, localized, "categoryDescription", "categoryDescription", "category_description", "sectionDescription", "section_description")
        mergeLocalizedOptions(base.optJSONArray("options"), localized.optJSONArray("options"))
        mergeLocalizedOptions(base.optJSONArray("activities") ?: base.optJSONArray("longPressActivities") ?: base.optJSONArray("pages"),
            localized.optJSONArray("activities") ?: localized.optJSONArray("longPressActivities") ?: localized.optJSONArray("pages"))
        mergeLocalizedTileTargets(base.optJSONArray("targets") ?: base.optJSONArray("tileTargets") ?: base.optJSONArray("keys"),
            localized.optJSONArray("targets") ?: localized.optJSONArray("tileTargets") ?: localized.optJSONArray("keys"))
        val baseChildren = base.optJSONArray("settings") ?: base.optJSONArray("children")
        val localizedChildren = localized.optJSONArray("settings") ?: localized.optJSONArray("children")
        if (baseChildren != null && localizedChildren != null) {
            mergeLocalizedSettings(baseChildren, localizedChildren)
        }
    }
}

private fun collectLocalizedSettingsByKey(settings: JSONArray, out: MutableMap<String, JSONObject>) {
    for (index in 0 until settings.length()) {
        val localized = settings.optJSONObject(index) ?: continue
        val key = localized.optString("key", "")
        if (key.isNotBlank() && !out.containsKey(key)) out[key] = localized
        val children = localized.optJSONArray("settings") ?: localized.optJSONArray("children")
        if (children != null) collectLocalizedSettingsByKey(children, out)
    }
}

private fun mergeLocalizedAliasText(base: JSONObject, localized: JSONObject, targetField: String, vararg aliases: String) {
    val localizedField = aliases.firstOrNull { localized.has(it) } ?: return
    base.put(targetField, localized.optString(localizedField, base.optString(targetField)))
}

internal data class AddonSettingCategory(
    val key: String,
    val title: String,
    val description: String,
    val settings: List<AddonSettingDef>
)

private data class AddonSettingCategoryBuilder(
    val key: String,
    var title: String,
    var description: String,
    val settings: MutableList<AddonSettingDef> = mutableListOf()
)

internal fun buildAddonSettingCategories(settings: List<AddonSettingDef>): List<AddonSettingCategory> {
    val hasCategories = settings.any {
        it.category.isNotBlank() || it.categoryTitle.isNotBlank() || it.categoryDescription.isNotBlank()
    }
    if (!hasCategories) {
        return listOf(AddonSettingCategory(key = "", title = "", description = "", settings = settings))
    }

    // Sequential runs in strict manifest order: settings are grouped only while
    // the category key stays the same. Merging by key would drag an uncategorized
    // trailing setting (e.g. a final "button" with no category) into the first
    // "__default" bucket at the top; separate runs keep every block in place.
    val runs = mutableListOf<AddonSettingCategoryBuilder>()
    settings.forEach { setting ->
        val rawCategory = setting.category.trim()
        val key = rawCategory.ifBlank { "__default" }
        val current = runs.lastOrNull()
        if (current != null && current.key == key) {
            if (current.title.isBlank() && setting.categoryTitle.isNotBlank()) current.title = setting.categoryTitle
            if (current.description.isBlank() && setting.categoryDescription.isNotBlank()) current.description = setting.categoryDescription
            current.settings += setting
        } else {
            runs += AddonSettingCategoryBuilder(
                key = key,
                title = setting.categoryTitle.ifBlank { rawCategory.toAddonCategoryTitle() },
                description = setting.categoryDescription
            ).also { it.settings += setting }
        }
    }

    return runs.map { category ->
        AddonSettingCategory(
            key = category.key,
            title = category.title,
            description = category.description,
            settings = category.settings.toList()
        )
    }
}

private fun String.toAddonCategoryTitle(): String {
    val words = replace('_', ' ').replace('-', ' ').trim()
    if (words.isEmpty()) return ""
    return words.split(Regex("\\s+")).joinToString(" ") { word ->
        if (word.length <= 3) word.uppercase(Locale.ROOT) else word.replaceFirstChar { it.uppercase() }
    }
}

private fun mergeLocalizedTileTargets(baseTargets: JSONArray?, localizedTargets: JSONArray?) {
    if (baseTargets == null || localizedTargets == null) return
    val localizedByKey = mutableMapOf<String, JSONObject>()
    for (index in 0 until localizedTargets.length()) {
        val target = localizedTargets.optJSONObject(index) ?: continue
        val key = target.optString("key", target.optString("value", target.optString("setting", "")))
        if (key.isNotBlank()) localizedByKey[key] = target
    }
    for (index in 0 until baseTargets.length()) {
        val base = baseTargets.optJSONObject(index) ?: continue
        val localized = localizedByKey[base.optString("key", base.optString("value", base.optString("setting", "")))] ?: continue
        listOf("label", "title", "name").forEach { field ->
            if (localized.has(field)) base.put(field, localized.optString(field, base.optString(field)))
        }
        if (localized.has("labels")) base.put("labels", localized.optJSONArray("labels") ?: localized.opt("labels"))
    }
}

private fun mergeLocalizedOptions(baseOptions: JSONArray?, localizedOptions: JSONArray?) {
    if (baseOptions == null || localizedOptions == null) return
    val labelsByValue = mutableMapOf<String, String>()
    for (index in 0 until localizedOptions.length()) {
        val option = localizedOptions.optJSONObject(index) ?: continue
        val value = option.optString("value", "")
        val label = option.optString("label", "")
        if (value.isNotBlank() && label.isNotBlank()) labelsByValue[value] = label
    }
    for (index in 0 until baseOptions.length()) {
        val option = baseOptions.optJSONObject(index) ?: continue
        val label = labelsByValue[option.optString("value", "")] ?: continue
        option.put("label", label)
    }
}

/** Extract a bitmap image from inside a JAR file at the given entry path */
internal fun extractBitmapFromJar(jarFile: File, entryPath: String): Bitmap? {
    val normalizedPath = normalizeJarEntryPath(entryPath)
    if (normalizedPath.isEmpty()) return null
    return try {
        java.util.zip.ZipFile(jarFile).use { zip ->
            for (candidate in bitmapPathCandidates(normalizedPath)) {
                zip.getEntry(candidate)?.let { entry ->
                    decodeBitmapFromZipEntry(zip, entry)?.let { return it }
                }
            }

            val fileName = normalizedPath.substringAfterLast('/')
            val fallback = zip.entries().asSequence().firstOrNull { entry ->
                !entry.isDirectory &&
                        hasBitmapFileExtension(entry.name) &&
                        (entry.name == normalizedPath || entry.name.endsWith("/$normalizedPath") || entry.name.substringAfterLast('/') == fileName)
            } ?: return null
            decodeBitmapFromZipEntry(zip, fallback)
        }
    } catch (_: Throwable) { null }
}

private fun normalizeJarEntryPath(path: String): String {
    return path.trim()
        .removePrefix("file://")
        .replace('\\', '/')
        .trimStart('/')
}

private fun bitmapPathCandidates(path: String): List<String> {
    val normalizedPath = normalizeJarEntryPath(path)
    if (normalizedPath.isEmpty()) return emptyList()
    val fileName = normalizedPath.substringAfterLast('/')
    val candidates = linkedSetOf(normalizedPath)
    if (!normalizedPath.startsWith("META-INF/")) {
        candidates.add("META-INF/$normalizedPath")
        candidates.add("META-INF/icons/$fileName")
    }
    if (!normalizedPath.startsWith("assets/")) {
        candidates.add("assets/$normalizedPath")
        candidates.add("assets/icons/$fileName")
    }
    return candidates.toList()
}

private fun decodeBitmapFromZipEntry(zip: java.util.zip.ZipFile, entry: java.util.zip.ZipEntry): Bitmap? {
    return zip.getInputStream(entry).use { stream -> BitmapFactory.decodeStream(stream) }
}

internal fun looksLikeBitmapPath(value: String): Boolean {
    val lower = normalizeJarEntryPath(value).lowercase()
    return lower.contains('/') || hasBitmapFileExtension(lower)
}

private fun hasBitmapFileExtension(value: String): Boolean {
    val lower = normalizeJarEntryPath(value).lowercase()
    return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") ||
            lower.endsWith(".webp") || lower.endsWith(".bmp") || lower.endsWith(".gif")
}

// =====================================================================
// Whitelist sync
// =====================================================================

internal fun syncWhitelist(context: Context, addons: List<AddonUiModel>) {
    for (addon in addons) {
        if (!addon.enabled) continue
        for (pkg in getEffectiveTargets(addon)) {
            if (pkg !in BUILTIN_WHITELIST) addToWhitelist(context, pkg)
        }
    }
    // Modern Xposed modules: their scopes must be injected too, otherwise
    // targets never load the module (module shows "disabled").
    try {
        for (mod in scanInstalledModules(context)) {
            if (!readAddonEnabled(context, mod.keyId, false)) continue
            val mode = readScopeMode(context, mod.keyId)
            val targets = when (mode) {
                1 -> readCustomTargets(context, mod.keyId)
                2 -> mod.scopes + readCustomTargets(context, mod.keyId)
                else -> mod.scopes
            }
            for (pkg in targets) {
                if (pkg == "android" || pkg == "*" || pkg.isBlank()) continue
                if (pkg !in BUILTIN_WHITELIST) addToWhitelist(context, pkg)
            }
        }
    } catch (_: Throwable) {}
}

internal fun getEffectiveTargets(addon: AddonUiModel): Set<String> {
    if (addon.settingsOnly) return emptySet()

    val raw = when (addon.scopeMode) {
        0 -> addon.defaultTargets
        1 -> addon.customTargets
        2 -> addon.defaultTargets + addon.customTargets
        else -> addon.defaultTargets
    }
    return raw - IGNORED_PACKAGE
}

/** Build a map of packageName → list of enabled addons targeting that package */
internal fun buildActiveAppsMap(addons: List<AddonUiModel>): Map<String, List<AddonUiModel>> {
    val map = mutableMapOf<String, MutableList<AddonUiModel>>()
    for (addon in addons) {
        if (!addon.enabled) continue
        for (pkg in getEffectiveTargets(addon)) {
            map.getOrPut(pkg) { mutableListOf() }.add(addon)
        }
    }
    return map
}

// =====================================================================
// Import / Delete
// =====================================================================

internal fun importAddonJar(context: Context, uri: Uri): Boolean {
    return try {
        val dir = File(ADDON_DIR)

        // --- Step 1: ensure directory exists ---
        if (!dir.exists() && !dir.mkdirs()) {
            Log.e(TAG, "Cannot create addon dir: ${dir.absolutePath}")
            return false
        }

        // --- Step 2: copy to a temp file so we can read addon.json before naming it ---
        val tmp = File(dir, "import_tmp_${System.currentTimeMillis()}.jar")
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(tmp).use { output ->
                input.copyTo(output)
            }
        }
        if (!tmp.exists() || tmp.length() == 0L) {
            tmp.delete()
            Log.e(TAG, "Copy failed: file empty or missing")
            return false
        }

        // --- Step 3: validate addon.json and extract id ---
        val desc = readDescriptor(tmp)
        if (desc == null || (!desc.has("entryClass") && !desc.has("id"))) {
            tmp.delete()
            Log.e(TAG, "Imported JAR has no valid addon.json")
            return false
        }

        val addonId = desc.optString("id").trim()
        if (addonId.isEmpty()) {
            tmp.delete()
            Log.e(TAG, "addon.json is missing required 'id' field")
            return false
        }

        // --- Step 4: rename temp file to {id}.jar, replacing any existing version ---
        val target = File(dir, "${addonId}.jar")
        if (target.exists()) target.delete()
        if (!tmp.renameTo(target)) {
            // renameTo can fail across filesystems — fall back to copy + delete
            tmp.copyTo(target, overwrite = true)
            tmp.delete()
        }
        if (!target.exists() || target.length() == 0L) {
            Log.e(TAG, "Rename/copy to final path failed: ${target.absolutePath}")
            return false
        }
        target.setReadable(true, false)

        // --- Step 5: register targets in whitelist ---
        val arr = desc.optJSONArray("targetPackages")
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val pkg = arr.optString(i)
                if (pkg.isNotEmpty() && pkg !in BUILTIN_WHITELIST) {
                    addToWhitelist(context, pkg)
                }
            }
        }

        Log.d(TAG, "Imported addon '$addonId' -> ${target.absolutePath}")
        true
    } catch (t: Throwable) {
        Log.e(TAG, "Import failed", t)
        false
    }
}

internal fun deleteAddon(context: Context, addon: AddonUiModel, allAddons: List<AddonUiModel>) {
    try {
        val jarFile = File(addon.jarPath)
        val descFile = File(addon.jarPath + ".json")

        if (addon.isSystem && addon.hasDataOverride) {
            val dataOverrideJar = addon.dataOverrideJarPath
                .takeIf { it.isNotBlank() }
                ?.let(::File)
                ?: jarFile.takeIf { it.absolutePath.startsWith(ADDON_DIR) }
                ?: return
            val dataOverrideDesc = File(dataOverrideJar.absolutePath + ".json")
            if (dataOverrideJar.exists()) dataOverrideJar.delete()
            if (dataOverrideDesc.exists()) dataOverrideDesc.delete()
            val dataOverrideDir = File(dataOverrideJar.parentFile ?: File(ADDON_DIR), dataOverrideJar.nameWithoutExtension + "_data")
            if (dataOverrideDir.exists()) dataOverrideDir.deleteRecursively()
            Log.d(TAG, "Deleted data override for system addon: ${addon.id}")
            return
        }

        if (addon.isSystem) {
            Log.d(TAG, "Skip deleting read-only system addon: ${addon.id}")
            return
        }

        if (jarFile.exists()) jarFile.delete()
        if (descFile.exists()) descFile.delete()

        // Also clean addon data directory
        val dataDir = if (addon.isSystem) {
            File("/data/pixelparts/system_addons_data", addon.id)
        } else {
            File(addon.jarPath.removeSuffix(".jar") + "_data")
        }
        if (dataDir.exists()) dataDir.deleteRecursively()

        // Clean whitelist — only remove packages not needed by other addons
        val otherAddonsTargets = allAddons
            .filter { it.id != addon.id && it.enabled }
            .flatMap { getEffectiveTargets(it) }
            .toSet()
        for (pkg in getEffectiveTargets(addon)) {
            if (pkg !in BUILTIN_WHITELIST && pkg !in otherAddonsTargets) {
                removeFromWhitelist(context, pkg)
            }
        }

        deleteAddonSetting(context, "${ADDON_PREFIX}${addon.id}_enabled")
        deleteAddonSetting(context, "${ADDON_PREFIX}${addon.id}_packages")
        deleteAddonSetting(context, "${ADDON_PREFIX}${addon.id}_scope_mode")

        Log.d(TAG, "Deleted addon: ${addon.id}")
    } catch (t: Throwable) {
        Log.e(TAG, "Delete failed", t)
    }
}

private fun deleteAddonSetting(context: Context, key: String) {
    try {
        context.contentResolver.delete(Settings.Global.getUriFor(key), null, null)
    } catch (_: Throwable) {}
}

// =====================================================================
// Load installed apps for package picker
// =====================================================================

internal fun loadInstalledApps(pm: PackageManager): List<AppInfoItem> {
    val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val launchablePackages = pm.queryIntentActivities(launcherIntent, 0)
        .mapNotNull { it.activityInfo?.packageName }
        .toSet()
    val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
    return apps.filter { it.packageName != IGNORED_PACKAGE }.map { info ->
        AppInfoItem(
            packageName = info.packageName,
            label = try { pm.getApplicationLabel(info).toString() } catch (_: Throwable) { info.packageName },
            icon = try { pm.getApplicationIcon(info) } catch (_: Throwable) { null },
            isSystem = (info.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0,
            isLaunchable = info.packageName in launchablePackages
        )
    }.sortedBy { it.label.lowercase() }
}

// =====================================================================
// Composable: Full Addon Manager Section
// =====================================================================
