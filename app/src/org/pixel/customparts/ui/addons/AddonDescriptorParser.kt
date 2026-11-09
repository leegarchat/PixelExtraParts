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

internal fun parseSettings(json: JSONObject): List<AddonSettingDef> {
    return parseSettingsArray(json.optJSONArray("settings") ?: return emptyList())
}

// =====================================================================
// Parse main[] entries from addon.json
// =====================================================================

/**
 * Parses the "main" array from an addon descriptor and returns a flat list of AddonMainEntry.
 * The caller is responsible for building the tree via [buildMainEntryTree].
 */
fun parseMainEntries(
    json: JSONObject,
    addonId: String,
    addonJarPath: String,
    isSystemAddon: Boolean,
    jarFile: java.io.File
): List<AddonMainEntry> {
    val arr = json.optJSONArray("main") ?: return emptyList()
    val result = mutableListOf<AddonMainEntry>()
    for (i in 0 until arr.length()) {
        val obj = arr.optJSONObject(i) ?: continue
        val rawId = obj.optString("id", "").trim()
        if (rawId.isEmpty()) continue

        // Split path by '/' — filter out "main" prefix if present
        val segments = rawId.split('/').map { it.trim() }.filter { it.isNotEmpty() && it != "main" }
        if (segments.isEmpty()) continue

        val iconPath = obj.optString("icon", "").trim()
        val iconType = obj.optString("iconType", obj.optString("icon_type", "")).trim().lowercase()
        val iconShape = obj.optString("iconShape", obj.optString("icon_shape", "circle")).trim().lowercase(Locale.ROOT)
        val iconSize = obj.optInt("iconSize", obj.optInt("icon_size", 20))
        val iconColor = obj.optString(
            "iconColor",
            obj.optString("iconTint", obj.optString("icon_color", obj.optString("icon_tint", "")))
        ).trim()
        val iconBackground = obj.optString(
            "iconBackground",
            obj.optString(
                "iconBackgroundColor",
                obj.optString("icon_background", obj.optString("icon_background_color", obj.optString("backgroundColor", "")))
            )
        ).trim()
        val iconBitmap = if (iconPath.isNotEmpty() && (iconType != "app" || looksLikeBitmapPath(iconPath))) {
            extractBitmapFromJar(jarFile, iconPath)
        } else null

        result.add(
            AddonMainEntry(
                addonId = addonId,
                addonJarPath = addonJarPath,
                isSystemAddon = isSystemAddon,
                rawId = rawId,
                pathSegments = segments,
                title = obj.optString("title", rawId),
                subtitle = obj.optString("subtitle", obj.optString("description", "")),
                icon = iconPath,
                iconType = iconType,
                iconBitmap = iconBitmap,
                iconShape = iconShape,
                iconSize = iconSize,
                iconColor = iconColor,
                iconBackground = iconBackground,
                titleSizeSp = optTextSizeSp(obj, "title_size", "titleSize", "title_seize", "titleSeize", "titleTextSize", "title_text_size", "titleSp", "title_sp"),
                descriptionSizeSp = optTextSizeSp(obj, "description_size", "descriptionSize", "description_seize", "descriptionSeize", "subtitle_size", "subtitleSize", "subtitle_seize", "subtitleSeize", "descriptionTextSize", "description_text_size", "subtitleTextSize", "subtitle_text_size", "descriptionSp", "description_sp", "subtitleSp", "subtitle_sp"),
                group = obj.optString("group", "system").trim().lowercase().ifBlank { "system" },
                priority = obj.optInt("priority", 0),
                targetActivity = obj.optString("targetActivity", obj.optString("activity", obj.optString("injectActivity", ""))).trim(),
                targetSlot = obj.optString("targetSlot", obj.optString("slot", "")).trim().lowercase(Locale.ROOT),
                settings = parseSettingsArray(obj.optJSONArray("settings") ?: JSONArray())
            )
        )
    }
    return result
}

/**
 * Builds a tree from a flat list of AddonMainEntry items.
 * Returns only the top-level entries; children are attached via [AddonMainEntry.children].
 *
 * Path resolution:
 *  - Single-segment id → top-level
 *  - Multi-segment path → walk the tree to find the parent; if not found, promote to nearest
 *    found ancestor or top level.
 */
fun buildMainEntryTree(flat: List<AddonMainEntry>): List<AddonMainEntry> {
    // Sort by path depth so parents are always processed before children
    val sorted = flat.sortedBy { it.pathSegments.size }
    // Map from (addonId, leafId) → entry for O(1) parent lookup
    val byLeafId = mutableMapOf<String, AddonMainEntry>()
    val topLevel = mutableListOf<AddonMainEntry>()

    for (entry in sorted) {
        if (entry.pathSegments.size <= 1) {
            // Top-level entry
            topLevel.add(entry)
            byLeafId[entry.addonId + "/" + entry.leafId] = entry
        } else {
            // Try to find parent by walking path segments from the end
            var placed = false
            for (depth in entry.pathSegments.size - 1 downTo 1) {
                val parentLeaf = entry.pathSegments[depth - 1]
                val parentKey = entry.addonId + "/" + parentLeaf
                val parent = byLeafId[parentKey]
                if (parent != null) {
                    parent.children.add(entry)
                    placed = true
                    break
                }
            }
            if (!placed) {
                // No parent found — promote to top level
                topLevel.add(entry)
            }
            byLeafId[entry.addonId + "/" + entry.leafId] = entry
        }
    }
    return topLevel
}

/**
 * Scans all addon JARs and returns a built AddonMainMenuModel with the full entry tree.
 * Only entries from enabled addons are included.
 */
fun scanAddonMainEntries(context: android.content.Context, includeTargetActivityEntries: Boolean = false): AddonMainMenuModel {
    val flat = mutableListOf<AddonMainEntry>()
    for (candidate in scanPreferredAddonJars(context)) {
        try {
            val defaultEnabled = candidate.descriptor.optBoolean("enabled", true)
            if (!readAddonEnabled(context, candidate.id, defaultEnabled)) continue

            flat.addAll(
                parseMainEntries(candidate.descriptor, candidate.id, candidate.file.absolutePath, candidate.isSystem, candidate.file)
                    .filter { includeTargetActivityEntries || it.targetActivity.isBlank() }
            )
        } catch (t: Throwable) {
            Log.e(TAG, "scanAddonMainEntries: failed for ${candidate.file.name}", t)
        }
    }

    val topLevel = buildMainEntryTree(flat)
    // Sort top-level entries by priority descending, then title
    val sorted = topLevel.sortedWith(compareByDescending<AddonMainEntry> { it.priority }.thenBy { it.title })
    return AddonMainMenuModel(entries = sorted)
}

fun scanAddonActivityEntries(context: Context, activityName: String, slot: String = ""): List<AddonMainEntry> {
    val entries = mutableListOf<AddonMainEntry>()
    for (candidate in scanPreferredAddonJars(context)) {
        try {
            if (!readAddonEnabled(context, candidate.id, candidate.descriptor.optBoolean("enabled", true))) continue
            entries += parseMainEntries(candidate.descriptor, candidate.id, candidate.file.absolutePath, candidate.isSystem, candidate.file)
                .filter { entry ->
                    targetActivityMatches(entry.targetActivity, activityName) &&
                            (slot.isBlank() || entry.targetSlot.isBlank() || entry.targetSlot == slot.lowercase(Locale.ROOT))
                }
        } catch (t: Throwable) {
            Log.e(TAG, "scanAddonActivityEntries: failed for ${candidate.file.name}", t)
        }
    }

    return entries.sortedWith(compareByDescending<AddonMainEntry> { it.priority }.thenBy { it.title })
}

private fun targetActivityMatches(target: String, activityName: String): Boolean {
    if (target.isBlank()) return false
    val normalizedTarget = target.substringAfterLast('.').removeSuffix("Activity").lowercase(Locale.ROOT)
    val normalizedActivity = activityName.substringAfterLast('.').removeSuffix("Activity").lowercase(Locale.ROOT)
    return target == activityName || normalizedTarget == normalizedActivity
}

internal data class AddonJarCandidate(
    val id: String,
    val file: File,
    val descriptor: JSONObject,
    val isSystem: Boolean,
    val version: String,
    val systemJarPath: String = "",
    val dataOverrideJarPath: String = ""
) {
    val hasDataOverride: Boolean get() = systemJarPath.isNotBlank() && dataOverrideJarPath.isNotBlank()
}

internal fun scanPreferredAddonJars(context: Context): List<AddonJarCandidate> {
    val preferred = linkedMapOf<String, AddonJarCandidate>()
    val systemPaths = mutableMapOf<String, String>()
    val dataPaths = mutableMapOf<String, String>()

    listOf(SYSTEM_ADDON_DIR, ADDON_DIR).forEach { dirPath ->
        val isSystemDir = dirPath == SYSTEM_ADDON_DIR
        val dir = File(dirPath)
        if (!dir.exists() || !dir.isDirectory) return@forEach
        dir.listFiles()?.forEach { file ->
            if (!file.isFile || !file.name.endsWith(".jar")) return@forEach
            try {
                val descriptor = readDescriptor(file, context) ?: return@forEach
                val entryClassStr = descriptor.optString("entryClass", "")
                val id = descriptor.optString("id", entryClassStr.ifEmpty { file.nameWithoutExtension })
                if (id.isBlank()) return@forEach

                val candidate = AddonJarCandidate(
                    id = id,
                    file = file,
                    descriptor = descriptor,
                    isSystem = isSystemDir,
                    version = descriptor.optString("version", "1.0")
                )
                if (isSystemDir) systemPaths[id] = file.absolutePath else dataPaths[id] = file.absolutePath

                val existing = preferred[id]
                if (existing == null || shouldPreferAddonCandidate(candidate, existing)) {
                    preferred[id] = candidate
                }
            } catch (t: Throwable) {
                Log.e(TAG, "scanPreferredAddonJars: failed for ${file.name}", t)
            }
        }
    }

    return preferred.values.map { candidate ->
        candidate.copy(
            systemJarPath = systemPaths[candidate.id].orEmpty(),
            dataOverrideJarPath = dataPaths[candidate.id].orEmpty()
        )
    }
}

private fun shouldPreferAddonCandidate(candidate: AddonJarCandidate, existing: AddonJarCandidate): Boolean {
    val versionCompare = compareVersions(candidate.version, existing.version)
    if (versionCompare != 0) return versionCompare > 0
    return !candidate.isSystem && existing.isSystem
}

private fun parseSettingsArray(arr: JSONArray): List<AddonSettingDef> {
    val result = mutableListOf<AddonSettingDef>()
    for (i in 0 until arr.length()) {
        val obj = arr.optJSONObject(i) ?: continue
        val typeStr = obj.optString("type", "").lowercase()
        val type = when (typeStr) {
            "int" -> SettingType.INT
            "float" -> SettingType.FLOAT
            "string", "str" -> SettingType.STRING
            "select", "arr" -> SettingType.SELECT
            "select_button", "button_select", "radio_button" -> SettingType.SELECT_BUTTON
            "cmd_button", "command_button", "shell_button", "button", "cmd", "command" -> SettingType.COMMAND_BUTTON
            "tile", "qs_tile", "quick_tile" -> SettingType.TILE
            "file" -> SettingType.FILE
            "apps", "app_list", "package_list", "packages" -> SettingType.APP_LIST
            "color", "colour" -> SettingType.COLOR
            "group", "subgroup", "section" -> SettingType.GROUP
            "text", "info", "description", "image", "spacer", "space", "divider", "line", "dashed", "dashed_line", "warning" -> SettingType.VISUAL
            "toggle", "bool", "boolean" -> SettingType.TOGGLE
            "switch" -> SettingType.SWITCH
            "checkbox", "check" -> SettingType.CHECKBOX
            else -> continue
        }
        val key = obj.optString("key", if (type == SettingType.VISUAL) "visual_$i" else if (type == SettingType.GROUP) "group_$i" else if (type == SettingType.TILE) "tile_$i" else if (type == SettingType.COMMAND_BUTTON) "cmd_$i" else "")
        if (key.isEmpty()) continue
        val providerStr = obj.optString("provider", "global").lowercase()
        val provider = when (providerStr) {
            "system" -> SettingProvider.SYSTEM
            "secure" -> SettingProvider.SECURE
            else -> SettingProvider.GLOBAL
        }
        val storageStr = obj.optString("storage", obj.optString("store", providerStr)).lowercase()
        val storage = when (storageStr) {
            "file", "addon_file", "data", "addon" -> SettingStorage.ADDON_FILE
            "internal", "internal_file", "internal_storage" -> SettingStorage.INTERNAL_FILE
            "external", "external_file", "external_storage" -> SettingStorage.EXTERNAL_FILE
            else -> SettingStorage.SETTINGS
        }
        val options = mutableListOf<SelectOption>()
        val optArr = obj.optJSONArray("options")
        if (optArr != null) {
            for (j in 0 until optArr.length()) {
                val optObj = optArr.optJSONObject(j)
                if (optObj != null) {
                    options.add(SelectOption(
                        value = optObj.optString("value", ""),
                        label = optObj.optString("label", optObj.optString("value", ""))
                    ))
                } else {
                    val s = optArr.optString(j)
                    if (s.isNotEmpty()) options.add(SelectOption(s, s))
                }
            }
        }
        val children = parseSettingsArray(obj.optJSONArray("settings") ?: obj.optJSONArray("children") ?: JSONArray())
        val groupModeValue = obj.optString("mode", obj.optString("presentation", ""))
            .trim()
            .lowercase()
            .replace('-', '_')
            .replace(' ', '_')
        val groupMode = when (groupModeValue) {
            "fullscreen", "full_screen", "full" -> GroupMode.FULLSCREEN
            "modal", "card", "floating", "floating_card" -> GroupMode.CARD
            "immersive_expand", "immersive_expanded", "imersive_expand", "imersive_expand", "inline_expand", "inline_expanded" -> GroupMode.IMMERSIVE_EXPAND
            "expand", "expand", "expanded", "expandable" -> GroupMode.EXPANDABLE
            "", "immersive", "inline", "flat", "embedded", "embed" -> GroupMode.INLINE
            else -> GroupMode.INLINE
        }
        val closePosition = when (obj.optString("closeButtonPosition", obj.optString("closePosition", "end")).lowercase()) {
            "start", "left" -> CloseButtonPosition.START
            else -> CloseButtonPosition.END
        }
        val visualType = when (typeStr) {
            "image" -> VisualType.IMAGE
            "spacer", "space" -> VisualType.SPACER
            "divider", "line" -> VisualType.DIVIDER
            "dashed", "dashed_line" -> VisualType.DASHED_DIVIDER
            "warning" -> VisualType.WARNING
            else -> when (obj.optString("visual", obj.optString("view", "text")).lowercase()) {
                "image" -> VisualType.IMAGE
                "spacer", "space" -> VisualType.SPACER
                "divider", "line" -> VisualType.DIVIDER
                "dashed", "dashed_line" -> VisualType.DASHED_DIVIDER
                "warning" -> VisualType.WARNING
                else -> VisualType.TEXT
            }
        }
        val colorFormat = when (obj.optString("format", obj.optString("colorFormat", "hex")).lowercase()) {
            "hex_argb", "argb", "ahex" -> ColorOutputFormat.HEX_ARGB
            "rgb", "csv", "comma", "rgb_csv" -> ColorOutputFormat.RGB_CSV
            "rgba", "rgba_csv" -> ColorOutputFormat.RGBA_CSV
            "int", "integer", "decimal", "argb_int" -> ColorOutputFormat.INT
            else -> ColorOutputFormat.HEX_RGB
        }
        result.add(AddonSettingDef(
            key = key,
            title = obj.optString("title", key),
            description = obj.optString("description", ""),
            category = optStringTrim(obj, "category", "categoryId", "category_id", "section", "sectionId", "section_id"),
            categoryTitle = optStringTrim(obj, "categoryTitle", "category_title", "sectionTitle", "section_title"),
            categoryDescription = optStringTrim(obj, "categoryDescription", "category_description", "sectionDescription", "section_description"),
            titleSizeSp = optTextSizeSp(obj, "title_size", "titleSize", "title_seize", "titleSeize", "titleTextSize", "title_text_size", "titleSp", "title_sp"),
            descriptionSizeSp = optTextSizeSp(obj, "description_size", "descriptionSize", "description_seize", "descriptionSeize", "descriptionTextSize", "description_text_size", "descriptionSp", "description_sp"),
            type = type,
            provider = provider,
            defaultInt = obj.optInt("default", 0),
            defaultFloat = obj.optDouble("default", 0.0).toFloat(),
            defaultString = obj.optString("default", ""),
            defaultBool = obj.optBoolean("default", false),
            min = obj.optDouble("min", 0.0).toFloat(),
            max = obj.optDouble("max", 100.0).toFloat(),
            step = obj.optDouble("step", 1.0).toFloat(),
            unit = obj.optString("unit", ""),
            options = options,
            mimeType = obj.optString("mimeType", "*/*"),
            storage = storage,
            children = children,
            groupMode = groupMode,
            defaultExpanded = obj.optBoolean("defaultExpanded", obj.optBoolean("expanded", obj.optBoolean("open", false))),
            closeButtonPosition = closePosition,
            visualType = visualType,
            imagePath = obj.optString("image", obj.optString("src", "")),
            sizeDp = obj.optInt("size", obj.optInt("height", 12)).coerceIn(0, 400),
            thicknessDp = obj.optInt("thickness", 1).coerceIn(1, 24),
            color = obj.optString("color", ""),
            surfaceAlpha = optAlphaFraction(obj, "surfaceAlpha", "containerAlpha", "backgroundAlpha", "surface_alpha", "container_alpha", "background_alpha"),
            colorFormat = colorFormat,
            allowAlpha = obj.optBoolean("alpha", obj.optBoolean("allowAlpha", colorFormat == ColorOutputFormat.HEX_ARGB || colorFormat == ColorOutputFormat.RGBA_CSV)),
            accentColor = obj.optString("accent", obj.optString("accentColor", "")),
            exclusiveGroup = obj.optString("exclusiveGroup", ""),
            exclusiveWith = optStringList(obj.optJSONArray("exclusiveWith") ?: obj.optJSONArray("forceFalseWhenTrue")),
                enabledIfAll = optFlexibleStringList(obj, "enabledIfAll", "enableIfAll", "requiresAll", "requiredAll", "andKeys", "and") +
                    optFlexibleStringList(obj.optJSONObject("dependencies"), "all", "and", "enabledAll"),
                enabledIfAny = optFlexibleStringList(obj, "enabledIfAny", "enableIfAny", "requiresAny", "requiredAny", "orKeys", "or") +
                    optFlexibleStringList(obj.optJSONObject("dependencies"), "any", "or", "enabledAny"),
                disabledIfAll = optFlexibleStringList(obj, "disabledIfAll", "disableIfAll", "blockedByAll") +
                    optFlexibleStringList(obj.optJSONObject("dependencies"), "disabledAll", "blockedAll"),
                disabledIfAny = optFlexibleStringList(obj, "disabledIfAny", "disableIfAny", "blockedByAny") +
                    optFlexibleStringList(obj.optJSONObject("dependencies"), "disabledAny", "blockedAny"),
                forceValueWhenDisabled = obj.optString("forceValueWhenDisabled", obj.optString("disabledValue", obj.optString("forceValue", ""))).trim(),
            icon = obj.optString("icon", "").trim(),
            iconType = obj.optString("iconType", obj.optString("icon_type", "")).trim().lowercase(),
            iconShape = obj.optString("iconShape", obj.optString("icon_shape", "none")).trim().lowercase(),
            iconShapePath = obj.optString("iconShapePath", obj.optString("icon_shape_path", "")).trim(),
            iconSize = obj.optInt("iconSize", obj.optInt("icon_size", 24)).coerceIn(8, 64),
            showSelected = obj.optBoolean("showSelected", obj.optBoolean("show_selected", true)),
            optionsSource = obj.optString("optionsSource", obj.optString("options_source", "")).lowercase(),
                optionsPath = obj.optString("optionsPath", obj.optString("options_path", "")),
                appPickerMode = obj.optString("appPickerMode", obj.optString("pickerMode", obj.optString("picker", "modal"))).trim().lowercase(Locale.ROOT),
            tileConfigurable = obj.optBoolean("tileConfigurable", obj.optBoolean("configurable", false)),
            tileTargets = parseTileTargets(obj),
            tileActivities = parseSelectOptions(obj.optJSONArray("activities") ?: obj.optJSONArray("longPressActivities") ?: obj.optJSONArray("pages")),
            command = optStringTrim(obj, "cmd", "command", "shell", "shellCommand", "shell_command"),
            commandOn = optStringTrim(obj, "cmdOn", "cmd_on", "commandOn", "command_on", "onCommand", "on_command", "shellOn", "shell_on"),
            commandOff = optStringTrim(obj, "cmdOff", "cmd_off", "commandOff", "command_off", "offCommand", "off_command", "shellOff", "shell_off"),
            showOutput = obj.optBoolean("showOutput", obj.optBoolean("show_output", obj.optBoolean("logOutput", obj.optBoolean("showLog", obj.optBoolean("show_log", true))))),
            settingsOn = parseSettingsAssignments(obj, provider, true, "settingsOn", "settings_on", "setOn", "set_on", "writeOn", "write_on", "trueSettings", "true_settings"),
            settingsOff = parseSettingsAssignments(obj, provider, false, "settingsOff", "settings_off", "setOff", "set_off", "writeOff", "write_off", "falseSettings", "false_settings"),
            binderOn = parseBinderActions(obj, "binderOn", "binder_on", "apiOn", "api_on", "carrierConfigOn", "carrier_config_on"),
            binderOff = parseBinderActions(obj, "binderOff", "binder_off", "apiOff", "api_off", "carrierConfigOff", "carrier_config_off")
        ))
    }
    return result
}

private fun parseSelectOptions(array: JSONArray?): List<SelectOption> {
    if (array == null) return emptyList()
    return (0 until array.length()).mapNotNull { index ->
        val obj = array.optJSONObject(index)
        if (obj != null) {
            val value = obj.optString("value", obj.optString("id", obj.optString("key", ""))).trim()
            val label = obj.optString("label", obj.optString("title", obj.optString("name", value))).trim()
            if (value.isNotEmpty()) SelectOption(value, label.ifEmpty { value }) else null
        } else {
            val value = array.optString(index).trim()
            if (value.isNotEmpty()) SelectOption(value, value) else null
        }
    }
}

private fun parseSettingsAssignments(
    obj: JSONObject,
    defaultProvider: SettingProvider,
    defaultOnValue: Boolean,
    vararg names: String
): List<SettingsAssignmentDef> {
    for (name in names) {
        if (!obj.has(name)) continue
        return when (val value = obj.opt(name)) {
            is JSONArray -> parseSettingsAssignmentArray(value, defaultProvider, defaultOnValue)
            is JSONObject -> parseSettingsAssignmentObject(value, defaultProvider)
            is String -> parseStringList(value).map { key ->
                SettingsAssignmentDef(key = key, provider = defaultProvider, value = if (defaultOnValue) "1" else "0", valueType = AssignmentValueType.INT)
            }
            else -> emptyList()
        }
    }
    return emptyList()
}

private fun parseSettingsAssignmentArray(
    array: JSONArray,
    defaultProvider: SettingProvider,
    defaultOnValue: Boolean
): List<SettingsAssignmentDef> {
    return (0 until array.length()).mapNotNull { index ->
        val item = array.opt(index)
        when (item) {
            is JSONObject -> parseSettingsAssignmentItem(item, defaultProvider)
            is String -> item.trim().takeIf { it.isNotEmpty() }?.let { key ->
                SettingsAssignmentDef(key = key, provider = defaultProvider, value = if (defaultOnValue) "1" else "0", valueType = AssignmentValueType.INT)
            }
            else -> null
        }
    }
}

private fun parseSettingsAssignmentObject(
    obj: JSONObject,
    defaultProvider: SettingProvider
): List<SettingsAssignmentDef> {
    val singleKey = obj.optString("key", obj.optString("name", "")).trim()
    if (singleKey.isNotEmpty()) return listOfNotNull(parseSettingsAssignmentItem(obj, defaultProvider))
    val assignments = mutableListOf<SettingsAssignmentDef>()
    obj.keys().forEach { key ->
        assignments += assignmentFromValue(key, obj.opt(key), defaultProvider)
    }
    return assignments
}

private fun parseSettingsAssignmentItem(obj: JSONObject, defaultProvider: SettingProvider): SettingsAssignmentDef? {
    val key = obj.optString("key", obj.optString("name", obj.optString("setting", ""))).trim()
    if (key.isEmpty()) return null
    val provider = parseSettingProvider(obj.optString("provider", obj.optString("namespace", "")), defaultProvider)
    val value = obj.opt("value") ?: obj.opt("set") ?: obj.opt("to") ?: true
    return assignmentFromValue(key, value, provider, obj.optString("type", obj.optString("valueType", obj.optString("value_type", ""))))
}

private fun assignmentFromValue(
    key: String,
    rawValue: Any?,
    provider: SettingProvider,
    forcedType: String = ""
): SettingsAssignmentDef {
    val type = when (forcedType.trim().lowercase(Locale.ROOT)) {
        "string", "str" -> AssignmentValueType.STRING
        "float", "double" -> AssignmentValueType.FLOAT
        "bool", "boolean" -> AssignmentValueType.BOOL
        "int", "integer" -> AssignmentValueType.INT
        else -> when (rawValue) {
            is Boolean -> AssignmentValueType.BOOL
            is Float, is Double -> AssignmentValueType.FLOAT
            is Number -> AssignmentValueType.INT
            else -> AssignmentValueType.STRING
        }
    }
    val value = when (rawValue) {
        is Boolean -> if (rawValue) "1" else "0"
        null -> ""
        else -> rawValue.toString()
    }
    return SettingsAssignmentDef(key = key.trim(), provider = provider, value = value, valueType = type)
}

private fun parseSettingProvider(value: String, defaultProvider: SettingProvider = SettingProvider.GLOBAL): SettingProvider {
    return when (value.trim().lowercase(Locale.ROOT)) {
        "system" -> SettingProvider.SYSTEM
        "secure" -> SettingProvider.SECURE
        "global" -> SettingProvider.GLOBAL
        else -> defaultProvider
    }
}

private fun parseBinderActions(obj: JSONObject, vararg names: String): List<BinderActionDef> {
    for (name in names) {
        if (!obj.has(name)) continue
        return when (val value = obj.opt(name)) {
            is JSONArray -> (0 until value.length()).mapNotNull { index ->
                value.optJSONObject(index)?.let { parseBinderAction(it) }
            }
            is JSONObject -> listOfNotNull(parseBinderAction(value))
            else -> emptyList()
        }
    }
    return emptyList()
}

private fun parseBinderAction(obj: JSONObject): BinderActionDef? {
    val type = optStringTrim(obj, "type", "action", "service", "method").ifBlank { "carrier_config" }
    val normalizedType = type.trim().lowercase(Locale.ROOT).replace('-', '_')
    if (normalizedType !in setOf("carrier_config", "carrier_config_override", "override_carrier_config")) return null
    return BinderActionDef(
        type = "carrier_config",
        targetSubIds = parseIntList(obj.opt("subIds") ?: obj.opt("sub_ids") ?: obj.opt("subscriptions") ?: obj.opt("subscriptionIds")),
        useActiveSubscriptions = parseActiveSubscriptionsFlag(obj),
        fallbackSubIds = parseIntList(obj.opt("fallbackSubIds") ?: obj.opt("fallback_sub_ids")),
        clear = obj.optBoolean("clear", obj.optBoolean("clearOverride", obj.optBoolean("clear_override", false))),
        values = parseBinderValues(obj.opt("values") ?: obj.opt("params") ?: obj.opt("parameters") ?: obj.opt("bundle"))
    )
}

private fun parseActiveSubscriptionsFlag(obj: JSONObject): Boolean {
    val target = listOf("subIds", "sub_ids", "subscriptions", "subscriptionIds").firstNotNullOfOrNull { key ->
        obj.opt(key)?.takeIf { it is String }
    } as? String
    return when (target?.trim()?.lowercase(Locale.ROOT)) {
        "active", "active_subscriptions", "active-subscriptions" -> true
        "none", "false" -> false
        else -> obj.optBoolean("activeSubscriptions", obj.optBoolean("active_subscriptions", true))
    }
}

private fun parseBinderValues(raw: Any?): List<BinderValueDef> {
    return when (raw) {
        is JSONArray -> (0 until raw.length()).mapNotNull { index ->
            when (val item = raw.opt(index)) {
                is JSONObject -> parseBinderValueItem(item)
                else -> null
            }
        }
        is JSONObject -> {
            val singleKey = raw.optString("key", raw.optString("name", "")).trim()
            if (singleKey.isNotEmpty()) {
                listOfNotNull(parseBinderValueItem(raw))
            } else {
                raw.keys().asSequence().map { key ->
                    binderValueFromRaw(key, raw.opt(key), "")
                }.toList()
            }
        }
        else -> emptyList()
    }
}

private fun parseBinderValueItem(obj: JSONObject): BinderValueDef? {
    val key = obj.optString("key", obj.optString("name", "")).trim()
    if (key.isEmpty()) return null
    val value = obj.opt("value") ?: obj.opt("set") ?: obj.opt("to") ?: true
    return binderValueFromRaw(key, value, obj.optString("type", obj.optString("valueType", obj.optString("value_type", ""))))
}

private fun binderValueFromRaw(key: String, rawValue: Any?, forcedType: String): BinderValueDef {
    val type = when (forcedType.trim().lowercase(Locale.ROOT)) {
        "string", "str" -> BinderValueType.STRING
        "bool", "boolean" -> BinderValueType.BOOL
        "int", "integer" -> BinderValueType.INT
        "long" -> BinderValueType.LONG
        "double", "float" -> BinderValueType.DOUBLE
        "int_array", "intarray", "integer_array", "integerarray", "ints" -> BinderValueType.INT_ARRAY
        "long_array", "longarray", "longs" -> BinderValueType.LONG_ARRAY
        "string_array", "stringarray", "strings" -> BinderValueType.STRING_ARRAY
        "bool_array", "boolean_array", "boolarray", "booleanarray", "booleans" -> BinderValueType.BOOL_ARRAY
        else -> inferBinderValueType(rawValue)
    }
    return BinderValueDef(key = key.trim(), value = rawValue, valueType = type)
}

private fun inferBinderValueType(rawValue: Any?): BinderValueType {
    return when (rawValue) {
        is Boolean -> BinderValueType.BOOL
        is Int -> BinderValueType.INT
        is Long -> BinderValueType.LONG
        is Float, is Double -> BinderValueType.DOUBLE
        is JSONArray -> when ((0 until rawValue.length()).firstNotNullOfOrNull { index -> rawValue.opt(index) }) {
            is Boolean -> BinderValueType.BOOL_ARRAY
            is Number -> BinderValueType.INT_ARRAY
            else -> BinderValueType.STRING_ARRAY
        }
        else -> BinderValueType.STRING
    }
}

private fun parseIntList(raw: Any?): List<Int> {
    return when (raw) {
        is JSONArray -> (0 until raw.length()).mapNotNull { raw.optIntOrNull(it) }
        is Number -> listOf(raw.toInt())
        is String -> parseStringList(raw).mapNotNull { it.toIntOrNull() }
        else -> emptyList()
    }
}

private fun JSONArray.optIntOrNull(index: Int): Int? {
    val value = opt(index)
    return when (value) {
        is Number -> value.toInt()
        is String -> value.trim().toIntOrNull()
        else -> null
    }
}

private fun parseTileTargets(obj: JSONObject): List<TileTargetOption> {
    val array = obj.optJSONArray("targets") ?: obj.optJSONArray("tileTargets") ?: obj.optJSONArray("keys")
    if (array == null) {
        val key = obj.optString("settingKey", obj.optString("targetKey", obj.optString("key", ""))).trim()
        if (key.isBlank()) return emptyList()
        return listOf(
            TileTargetOption(
                key = key,
                label = obj.optString("title", key),
                mode = obj.optString("mode", obj.optString("tileMode", obj.optString("default", "toggle"))).trim().lowercase(Locale.ROOT),
                values = optStringList(obj.optJSONArray("values")),
                labels = optStringList(obj.optJSONArray("labels")),
                pageId = obj.optString("pageId", obj.optString("activity", "")).trim()
            )
        )
    }
    return (0 until array.length()).mapNotNull { index ->
        val target = array.optJSONObject(index)
        if (target != null) {
            val key = target.optString("key", target.optString("value", target.optString("setting", ""))).trim()
            if (key.isBlank()) return@mapNotNull null
            val values = optStringList(target.optJSONArray("values"))
            val labels = optStringList(target.optJSONArray("labels"))
            TileTargetOption(
                key = key,
                label = target.optString("label", target.optString("title", key)).trim().ifEmpty { key },
                mode = target.optString("mode", if (values.isNotEmpty()) "carousel" else "toggle").trim().lowercase(Locale.ROOT),
                values = values,
                labels = labels.ifEmpty { values },
                pageId = target.optString("pageId", target.optString("activity", "")).trim()
            )
        } else {
            val value = array.optString(index).trim()
            if (value.isNotEmpty()) TileTargetOption(key = value, label = value) else null
        }
    }
}

internal fun optStringList(array: JSONArray?): List<String> {
    if (array == null) return emptyList()
    return (0 until array.length()).mapNotNull { index -> array.optString(index).takeIf { it.isNotBlank() } }
}

private fun optStringTrim(obj: JSONObject, vararg names: String): String {
    for (name in names) {
        if (!obj.has(name)) continue
        val value = obj.optString(name, "").trim()
        if (value.isNotEmpty()) return value
    }
    return ""
}

private fun optAlphaFraction(obj: JSONObject, vararg names: String): Float {
    for (name in names) {
        if (!obj.has(name)) continue
        val parsed = when (val value = obj.opt(name)) {
            is Number -> value.toDouble()
            is Boolean -> if (value) 1.0 else 0.0
            is String -> value.trim().removeSuffix("%").trim().toDoubleOrNull()
            else -> null
        } ?: continue
        val fraction = if (parsed > 1.0) parsed / 100.0 else parsed
        return fraction.toFloat().coerceIn(0f, 1f)
    }
    return -1f
}

private fun optTextSizeSp(obj: JSONObject, vararg names: String): Float {
    for (name in names) {
        if (!obj.has(name)) continue
        val parsed = when (val value = obj.opt(name)) {
            is Number -> value.toFloat()
            is String -> value.trim().removeSuffix("sp").trim().toFloatOrNull()
            else -> null
        } ?: continue
        if (parsed > 0f) return parsed.coerceIn(6f, 64f)
    }
    return 0f
}

private fun optFlexibleStringList(obj: JSONObject?, vararg names: String): List<String> {
    if (obj == null) return emptyList()
    for (name in names) {
        if (!obj.has(name)) continue
        return when (val value = obj.opt(name)) {
            is JSONArray -> optStringList(value)
            is String -> parseStringList(value)
            else -> emptyList()
        }
    }
    return emptyList()
}

internal fun parseStringList(raw: String): List<String> {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return emptyList()
    if (trimmed.startsWith("[")) {
        runCatching { return optStringList(JSONArray(trimmed)) }
    }
    return trimmed.split(',').map { it.trim() }.filter { it.isNotEmpty() }
}

// =====================================================================
// Scan addons
// =====================================================================
