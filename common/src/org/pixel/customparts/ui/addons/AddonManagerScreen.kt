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
import android.provider.Settings
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
import java.net.URL
import java.util.Locale
import org.pixel.customparts.R
import org.pixel.customparts.dynamicDarkColorScheme
import org.pixel.customparts.dynamicLightColorScheme
import org.pixel.customparts.activities.AddonPageActivity
import org.pixel.customparts.ui.ColorPickerDialog
import org.pixel.customparts.ui.ExpandableWarningCard
import org.pixel.customparts.utils.PixelPartsTileRefresher
import org.pixel.customparts.utils.TileUtils
import org.pixel.customparts.utils.dynamicStringResource
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

data class AddonUiModel(
    val id: String,
    val entryClass: String,
    val name: String,
    val author: String,
    val description: String,
    val version: String,
    val jarPath: String,
    val defaultTargets: Set<String>,
    val enabled: Boolean,
    val scopeMode: Int,          // 0=default, 1=custom, 2=merge
    val customTargets: Set<String>,
    val settings: List<AddonSettingDef> = emptyList(),
    val isSystem: Boolean = false,
    /** True when addon has no entryClass — it only provides settings UI via main[]/settings[] */
    val settingsOnly: Boolean = false,
    val iconBitmap: Bitmap? = null,
    val backgroundBitmap: Bitmap? = null,
    val backgroundMode: String = "gradient",  // "cover", "gradient"
    val backgroundAlpha: Int = 50,             // 0–100, intensity of background image
    val backgroundGradientSteps: List<Int> = listOf(0, 100), // gradient opacity stops (0–100 each)
    val backgroundBlur: Boolean = false,       // enable blur on background
    val backgroundBlurRadius: Int = 25,        // blur radius in dp
    val cardColor: String = "",                // custom card background color (hex, e.g. "#FF5722")
    val backgroundScope: String = "full",      // "full" = background extends with settings, "header" = header only
    val accentColor: String = "",
    val updateUrl: String = "",
    val hasDataOverride: Boolean = false,
    val systemJarPath: String = "",
    /** Top-level main[] entries for this addon (tree already built). Empty if addon has no main[]. */
    val mainEntries: List<AddonMainEntry> = emptyList()
)

/** A single setting definition parsed from addon.json "settings" array */
data class AddonSettingDef(
    val key: String,
    val title: String,
    val description: String = "",
    val titleSizeSp: Float = 0f,
    val descriptionSizeSp: Float = 0f,
    val type: SettingType,
    val provider: SettingProvider = SettingProvider.GLOBAL,
    val defaultInt: Int = 0,
    val defaultFloat: Float = 0f,
    val defaultString: String = "",
    val defaultBool: Boolean = false,
    val min: Float = 0f,
    val max: Float = 100f,
    val step: Float = 1f,
    val unit: String = "",
    val options: List<SelectOption> = emptyList(),
    val mimeType: String = "*/*",
    val storage: SettingStorage = SettingStorage.SETTINGS,
    val children: List<AddonSettingDef> = emptyList(),
    val groupMode: GroupMode = GroupMode.INLINE,
    val defaultExpanded: Boolean = false,
    val closeButtonPosition: CloseButtonPosition = CloseButtonPosition.END,
    val visualType: VisualType = VisualType.TEXT,
    val imagePath: String = "",
    val sizeDp: Int = 12,
    val thicknessDp: Int = 1,
    val color: String = "",
    val surfaceAlpha: Float = -1f,
    val colorFormat: ColorOutputFormat = ColorOutputFormat.HEX_RGB,
    val allowAlpha: Boolean = false,
    val accentColor: String = "",
    val exclusiveGroup: String = "",
    val exclusiveWith: List<String> = emptyList(),
    val enabledIfAll: List<String> = emptyList(),
    val enabledIfAny: List<String> = emptyList(),
    val disabledIfAll: List<String> = emptyList(),
    val disabledIfAny: List<String> = emptyList(),
    val forceValueWhenDisabled: String = "",
    // Icon fields
    val icon: String = "",              // Material icon name (iconType=app) or JAR path (iconType=file)
    val iconType: String = "",          // "app" = Material3 icon, "file" = from JAR
    val iconShape: String = "none",     // "none", "circle", "rounded", "custom"
    val iconShapePath: String = "",     // path to custom shape svg/xml inside JAR (iconShape=custom)
    val iconSize: Int = 24,             // icon size inside shape in dp
    // App list fields
    val showSelected: Boolean = true,   // whether to show selected items chips below the button
    // Dynamic options from file
    val optionsSource: String = "",     // "file" = load options from external JSON
    val optionsPath: String = "",       // path relative to addon data dir (e.g. "dir/array.json")
    val appPickerMode: String = "modal", // "modal" or "activity" for APP_LIST settings
    // Dynamic QS tile fields
    val tileConfigurable: Boolean = false,
    val tileTargets: List<TileTargetOption> = emptyList(),
    val tileActivities: List<SelectOption> = emptyList(),
    val command: String = "",
    val commandOn: String = "",
    val commandOff: String = "",
    val showOutput: Boolean = true
)

enum class SettingType { INT, FLOAT, STRING, SELECT, SELECT_BUTTON, FILE, TOGGLE, SWITCH, CHECKBOX, APP_LIST, COLOR, GROUP, VISUAL, TILE, COMMAND_BUTTON }
enum class SettingProvider { GLOBAL, SYSTEM, SECURE }
enum class SettingStorage { SETTINGS, ADDON_FILE, INTERNAL_FILE, EXTERNAL_FILE }
enum class GroupMode { INLINE, EXPANDABLE, FULLSCREEN, CARD, IMMERSIVE_EXPAND }
enum class CloseButtonPosition { START, END }
enum class VisualType { TEXT, IMAGE, SPACER, DIVIDER, DASHED_DIVIDER, WARNING }
enum class ColorOutputFormat { HEX_RGB, HEX_ARGB, RGB_CSV, RGBA_CSV, INT }

data class SelectOption(val value: String, val label: String)

data class TileTargetOption(
    val key: String,
    val label: String,
    val mode: String = "toggle",
    val values: List<String> = emptyList(),
    val labels: List<String> = emptyList(),
    val pageId: String = ""
)

data class AddonUpdateInfo(
    val version: String,
    val downloadUrl: String,
    val changelog: String,
    val extraInfo: String
)

// =====================================================================
// Main-menu entry models (addon.json "main" array)
// =====================================================================

/**
 * A single entry in the addon's "main" array.
 * Represents one button that appears in the app's main menu (or nested inside another entry).
 *
 * addon.json schema:
 * {
 *   "main": [
 *     {
 *       "id": "my-settings",          // unique within this addon; used as path segment
 *       "title": "My Settings",
 *       "subtitle": "Short description shown under the button",
 *       "icon": "META-INF/icon.png",  // optional; path inside JAR
 *       "group": "system",            // main-menu group: "gesture"|"system"|"network" (default "system")
 *       "priority": 100,              // display order within the group (higher = first)
 *       "settings": [ ... ]           // same settings array as in the card
 *     },
 *     {
 *       "id": "main/my-settings/advanced",  // slash-path → nested under "my-settings"
 *       "title": "Advanced",
 *       "settings": [ ... ]
 *     }
 *   ]
 * }
 *
 * Path resolution rules:
 *  - "foo"            → top-level entry with id "foo"
 *  - "main/foo/bar"   → child of "foo" named "bar"
 *  - If the parent segment is not found the entry is promoted to the nearest found ancestor,
 *    or to the top level if no ancestor exists.
 */
data class AddonMainEntry(
    val addonId: String,          // owning addon id
    val addonJarPath: String,
    val isSystemAddon: Boolean,
    val rawId: String,            // full raw id string from json (may contain slashes)
    val pathSegments: List<String>, // parsed path segments (rawId split by '/')
    val title: String,
    val subtitle: String,
    val icon: String,
    val iconType: String,
    val iconBitmap: android.graphics.Bitmap?,
    val iconShape: String = "circle",
    val iconSize: Int = 20,
    val iconColor: String = "",
    val iconBackground: String = "",
    val titleSizeSp: Float = 0f,
    val descriptionSizeSp: Float = 0f,
    val group: String,            // "gesture" | "system" | "network"
    val priority: Int,
    val targetActivity: String = "",
    val targetSlot: String = "",
    val settings: List<AddonSettingDef>,
    val children: MutableList<AddonMainEntry> = mutableListOf()
) {
    /** The leaf segment used as the logical id of this entry */
    val leafId: String get() = pathSegments.lastOrNull() ?: rawId
}

/** Flat list of all top-level AddonMainEntry items across all addons, tree already built */
data class AddonMainMenuModel(
    val entries: List<AddonMainEntry>  // only top-level entries; children are nested inside
)

/** Info about an installed app for the package picker */
data class AppInfoItem(
    val packageName: String,
    val label: String,
    val icon: Drawable?,
    val isSystem: Boolean,
    val isLaunchable: Boolean
)

// =====================================================================
// Settings helpers
// =====================================================================

private fun isSafeModeActive(context: Context): Boolean {
    return try {
        Settings.Global.getInt(context.contentResolver, SAFE_MODE_SETTING, 0) == 1
    } catch (_: Throwable) { false }
}

private fun exitAddonSafeMode(context: Context) {
    try {
        Settings.Global.putInt(context.contentResolver, SAFE_MODE_SETTING, 0)
        // Also delete the crash counter file
        val guardFile = File("/data/pixelparts/.boot_guard")
        if (guardFile.exists()) guardFile.delete()
    } catch (t: Throwable) {
        Log.e(TAG, "Failed to exit safe mode", t)
    }
}

private fun readAddonEnabled(context: Context, id: String, defaultEnabled: Boolean = true): Boolean {
    return try {
        Settings.Global.getInt(context.contentResolver, "${ADDON_PREFIX}${id}_enabled", if (defaultEnabled) 1 else 0) != 0
    } catch (_: Throwable) { defaultEnabled }
}

private fun writeAddonEnabled(context: Context, id: String, enabled: Boolean) {
    Settings.Global.putInt(context.contentResolver, "${ADDON_PREFIX}${id}_enabled", if (enabled) 1 else 0)
}

private fun readScopeMode(context: Context, id: String): Int {
    return try {
        Settings.Global.getInt(context.contentResolver, "${ADDON_PREFIX}${id}_scope_mode", 0)
    } catch (_: Throwable) { 0 }
}

private fun writeScopeMode(context: Context, id: String, mode: Int) {
    Settings.Global.putInt(context.contentResolver, "${ADDON_PREFIX}${id}_scope_mode", mode)
}

private fun readCustomTargets(context: Context, id: String): Set<String> {
    val raw = Settings.Global.getString(context.contentResolver, "${ADDON_PREFIX}${id}_packages")
    if (raw.isNullOrBlank()) return emptySet()
    return raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
}

private fun writeCustomTargets(context: Context, id: String, targets: Set<String>) {
    Settings.Global.putString(context.contentResolver, "${ADDON_PREFIX}${id}_packages", targets.joinToString(","))
}

private fun addToWhitelist(context: Context, pkg: String) {
    Settings.Global.putInt(context.contentResolver, "${INJECT_PREFIX}${pkg}", 1)
}

private fun removeFromWhitelist(context: Context, pkg: String) {
    Settings.Global.putInt(context.contentResolver, "${INJECT_PREFIX}${pkg}", 0)
}

private val BUILTIN_WHITELIST = setOf(
    "com.google.android.apps.nexuslauncher",
    "com.google.android.apps.pixel.launcher",
    "com.android.launcher3",
    "com.android.systemui"
)

// =====================================================================
// Settings provider read/write helpers
// =====================================================================

private fun readSettingString(context: Context, provider: SettingProvider, key: String): String? {
    return try {
        when (provider) {
            SettingProvider.GLOBAL -> Settings.Global.getString(context.contentResolver, key)
            SettingProvider.SYSTEM -> Settings.System.getString(context.contentResolver, key)
            SettingProvider.SECURE -> Settings.Secure.getString(context.contentResolver, key)
        }
    } catch (_: Throwable) { null }
}

private fun writeSettingString(context: Context, provider: SettingProvider, key: String, value: String) {
    try {
        when (provider) {
            SettingProvider.GLOBAL -> Settings.Global.putString(context.contentResolver, key, value)
            SettingProvider.SYSTEM -> Settings.System.putString(context.contentResolver, key, value)
            SettingProvider.SECURE -> Settings.Secure.putString(context.contentResolver, key, value)
        }
        if (provider == SettingProvider.GLOBAL) PixelPartsTileRefresher.requestForSetting(context, key)
    } catch (t: Throwable) { Log.e(TAG, "writeSettingString($key) failed", t) }
}

private fun readSettingInt(context: Context, provider: SettingProvider, key: String, default: Int): Int {
    return try {
        when (provider) {
            SettingProvider.GLOBAL -> Settings.Global.getInt(context.contentResolver, key, default)
            SettingProvider.SYSTEM -> Settings.System.getInt(context.contentResolver, key, default)
            SettingProvider.SECURE -> Settings.Secure.getInt(context.contentResolver, key, default)
        }
    } catch (_: Throwable) { default }
}

private fun writeSettingInt(context: Context, provider: SettingProvider, key: String, value: Int) {
    try {
        when (provider) {
            SettingProvider.GLOBAL -> Settings.Global.putInt(context.contentResolver, key, value)
            SettingProvider.SYSTEM -> Settings.System.putInt(context.contentResolver, key, value)
            SettingProvider.SECURE -> Settings.Secure.putInt(context.contentResolver, key, value)
        }
        if (provider == SettingProvider.GLOBAL) PixelPartsTileRefresher.requestForSetting(context, key)
    } catch (t: Throwable) { Log.e(TAG, "writeSettingInt($key) failed", t) }
}

private fun readSettingFloat(context: Context, provider: SettingProvider, key: String, default: Float): Float {
    return try {
        when (provider) {
            SettingProvider.GLOBAL -> Settings.Global.getFloat(context.contentResolver, key, default)
            SettingProvider.SYSTEM -> Settings.System.getFloat(context.contentResolver, key, default)
            SettingProvider.SECURE -> Settings.Secure.getFloat(context.contentResolver, key, default)
        }
    } catch (_: Throwable) { default }
}

private fun writeSettingFloat(context: Context, provider: SettingProvider, key: String, value: Float) {
    try {
        when (provider) {
            SettingProvider.GLOBAL -> Settings.Global.putFloat(context.contentResolver, key, value)
            SettingProvider.SYSTEM -> Settings.System.putFloat(context.contentResolver, key, value)
            SettingProvider.SECURE -> Settings.Secure.putFloat(context.contentResolver, key, value)
        }
        if (provider == SettingProvider.GLOBAL) PixelPartsTileRefresher.requestForSetting(context, key)
    } catch (t: Throwable) { Log.e(TAG, "writeSettingFloat($key) failed", t) }
}

private fun addonDataDir(addonId: String, addonJarPath: String, isSystemAddon: Boolean): File {
    return if (isSystemAddon) {
        File("/data/pixelparts/system_addons_data", sanitizeFileSegment(addonId))
    } else {
        val jarFile = File(addonJarPath)
        File(jarFile.parentFile ?: File(ADDON_DIR), jarFile.nameWithoutExtension + "_data")
    }
}

private fun settingBaseDir(
    context: Context,
    setting: AddonSettingDef,
    addonId: String,
    addonJarPath: String,
    isSystemAddon: Boolean
): File {
    return when (setting.storage) {
        SettingStorage.ADDON_FILE -> addonDataDir(addonId, addonJarPath, isSystemAddon)
        SettingStorage.INTERNAL_FILE -> File(context.filesDir, "addon_settings/${sanitizeFileSegment(addonId)}")
        SettingStorage.EXTERNAL_FILE -> File(
            context.getExternalFilesDir("addon_settings") ?: context.filesDir,
            sanitizeFileSegment(addonId)
        )
        SettingStorage.SETTINGS -> addonDataDir(addonId, addonJarPath, isSystemAddon)
    }
}

private fun sanitizeFileSegment(value: String): String {
    val sanitized = value.trim().replace(Regex("[^A-Za-z0-9._-]"), "_")
    return sanitized.ifEmpty { "setting" }
}

private fun settingFile(baseDir: File, key: String, suffix: String = ".json"): File {
    val rawSegments = key.split('/').map { it.trim() }.filter { it.isNotEmpty() }
    val safeSegments = rawSegments.ifEmpty { listOf("setting") }.map { sanitizeFileSegment(it) }
    val parent = safeSegments.dropLast(1).fold(baseDir) { dir, segment -> File(dir, segment) }
    val fileName = safeSegments.last() + suffix
    return File(parent, fileName)
}

private fun arraySettingFile(baseDir: File, key: String): File = settingFile(baseDir, key, "_array.json")

private fun readStoredString(
    context: Context,
    setting: AddonSettingDef,
    addonId: String,
    addonJarPath: String,
    isSystemAddon: Boolean
): String? {
    if (setting.storage == SettingStorage.SETTINGS) {
        return readSettingString(context, setting.provider, setting.key)
    }
    return try {
        val file = settingFile(settingBaseDir(context, setting, addonId, addonJarPath, isSystemAddon), setting.key)
        if (!file.exists()) return null
        JSONObject(file.readText(Charsets.UTF_8)).optString("value", "")
    } catch (t: Throwable) {
        Log.e(TAG, "readStoredString(${setting.key}) failed", t)
        null
    }
}

private fun writeStoredString(
    context: Context,
    setting: AddonSettingDef,
    addonId: String,
    addonJarPath: String,
    isSystemAddon: Boolean,
    value: String
) {
    if (setting.storage == SettingStorage.SETTINGS) {
        writeSettingString(context, setting.provider, setting.key, value)
        return
    }
    try {
        val file = settingFile(settingBaseDir(context, setting, addonId, addonJarPath, isSystemAddon), setting.key)
        file.parentFile?.mkdirs()
        file.writeText(JSONObject().put("value", value).toString(2), Charsets.UTF_8)
        file.setReadable(true, false)
    } catch (t: Throwable) {
        Log.e(TAG, "writeStoredString(${setting.key}) failed", t)
    }
}

private fun readStoredInt(context: Context, setting: AddonSettingDef, addonId: String, addonJarPath: String, isSystemAddon: Boolean, default: Int): Int {
    if (setting.storage == SettingStorage.SETTINGS) return readSettingInt(context, setting.provider, setting.key, default)
    return readStoredString(context, setting, addonId, addonJarPath, isSystemAddon)?.toIntOrNull() ?: default
}

private fun writeStoredInt(context: Context, setting: AddonSettingDef, addonId: String, addonJarPath: String, isSystemAddon: Boolean, value: Int) {
    if (setting.storage == SettingStorage.SETTINGS) writeSettingInt(context, setting.provider, setting.key, value)
    else writeStoredString(context, setting, addonId, addonJarPath, isSystemAddon, value.toString())
}

private fun readStoredFloat(context: Context, setting: AddonSettingDef, addonId: String, addonJarPath: String, isSystemAddon: Boolean, default: Float): Float {
    if (setting.storage == SettingStorage.SETTINGS) return readSettingFloat(context, setting.provider, setting.key, default)
    return readStoredString(context, setting, addonId, addonJarPath, isSystemAddon)?.toFloatOrNull() ?: default
}

private fun writeStoredFloat(context: Context, setting: AddonSettingDef, addonId: String, addonJarPath: String, isSystemAddon: Boolean, value: Float) {
    if (setting.storage == SettingStorage.SETTINGS) writeSettingFloat(context, setting.provider, setting.key, value)
    else writeStoredString(context, setting, addonId, addonJarPath, isSystemAddon, value.toString())
}

private fun readStoredArray(
    context: Context,
    setting: AddonSettingDef,
    addonId: String,
    addonJarPath: String,
    isSystemAddon: Boolean
): List<String> {
    return try {
        val file = arraySettingFile(settingBaseDir(context, setting.copy(storage = arrayStorage(setting)), addonId, addonJarPath, isSystemAddon), setting.key)
        if (!file.exists()) return emptyList()
        val arr = JSONArray(file.readText(Charsets.UTF_8))
        (0 until arr.length()).mapNotNull { arr.optString(it).takeIf { value -> value.isNotEmpty() } }
    } catch (t: Throwable) {
        Log.e(TAG, "readStoredArray(${setting.key}) failed", t)
        emptyList()
    }
}

private fun writeStoredArray(
    context: Context,
    setting: AddonSettingDef,
    addonId: String,
    addonJarPath: String,
    isSystemAddon: Boolean,
    values: Collection<String>
) {
    try {
        val file = arraySettingFile(settingBaseDir(context, setting.copy(storage = arrayStorage(setting)), addonId, addonJarPath, isSystemAddon), setting.key)
        file.parentFile?.mkdirs()
        val arr = JSONArray()
        values.forEach { arr.put(it) }
        file.writeText(arr.toString(2), Charsets.UTF_8)
        file.setReadable(true, false)
    } catch (t: Throwable) {
        Log.e(TAG, "writeStoredArray(${setting.key}) failed", t)
    }
}

private fun arrayStorage(setting: AddonSettingDef): SettingStorage {
    return if (setting.storage == SettingStorage.SETTINGS) SettingStorage.ADDON_FILE else setting.storage
}

internal fun flattenSettings(settings: List<AddonSettingDef>): List<AddonSettingDef> {
    return settings.flatMap { setting ->
        if (setting.type == SettingType.GROUP) listOf(setting) + flattenSettings(setting.children) else listOf(setting)
    }
}

internal fun applyExclusiveSettingLogic(
    context: Context,
    addon: AddonUiModel,
    changed: AddonSettingDef,
    allSettings: List<AddonSettingDef>
): Boolean {
    val targetKeys = mutableSetOf<String>()
    targetKeys += changed.exclusiveWith
    if (changed.exclusiveGroup.isNotBlank()) {
        allSettings
            .filter { it.key != changed.key && it.exclusiveGroup == changed.exclusiveGroup && it.isBooleanSetting() }
            .mapTo(targetKeys) { it.key }
    }
    if (targetKeys.isEmpty()) return false

    allSettings
        .filter { it.key in targetKeys && it.isBooleanSetting() }
        .forEach { target ->
            writeStoredInt(context, target, addon.id, addon.jarPath, addon.isSystem, 0)
        }
    return true
}

private fun AddonSettingDef.isBooleanSetting(): Boolean {
    return type == SettingType.TOGGLE || type == SettingType.SWITCH || type == SettingType.CHECKBOX
}

private fun AddonSettingDef.isImmersiveGroup(): Boolean {
    return type == SettingType.GROUP && groupMode == GroupMode.IMMERSIVE_EXPAND
}

private data class SettingDependencyState(
    val enabled: Boolean,
    val forceValue: String = ""
)

private fun resolveSettingDependencyState(
    context: Context,
    addon: AddonUiModel,
    setting: AddonSettingDef,
    allSettings: List<AddonSettingDef>
): SettingDependencyState {
    val allByKey = allSettings.associateBy { it.key }
    val enabledByAll = setting.enabledIfAll.all { dependencyKeyActive(context, addon, allByKey, it) }
    val enabledByAny = setting.enabledIfAny.isEmpty() || setting.enabledIfAny.any { dependencyKeyActive(context, addon, allByKey, it) }
    val disabledByAll = setting.disabledIfAll.isNotEmpty() && setting.disabledIfAll.all { dependencyKeyActive(context, addon, allByKey, it) }
    val disabledByAny = setting.disabledIfAny.any { dependencyKeyActive(context, addon, allByKey, it) }
    val enabled = enabledByAll && enabledByAny && !disabledByAll && !disabledByAny
    return SettingDependencyState(
        enabled = enabled,
        forceValue = if (!enabled) setting.forceValueWhenDisabled else ""
    )
}

private fun dependencyKeyActive(
    context: Context,
    addon: AddonUiModel,
    allByKey: Map<String, AddonSettingDef>,
    rawKey: String
): Boolean {
    val trimmed = rawKey.trim()
    if (trimmed.isEmpty()) return false
    val negate = trimmed.startsWith("!")
    val expression = trimmed.removePrefix("!")
    val parts = expression.split('=', limit = 2)
    val key = parts[0].trim()
    val expectedValue = parts.getOrNull(1)?.trim()
    val setting = allByKey[key]
    val active = if (expectedValue != null) {
        val actual = if (setting != null) {
            settingValueString(context, addon, setting)
        } else {
            readSettingString(context, SettingProvider.GLOBAL, key)
                ?: readSettingInt(context, SettingProvider.GLOBAL, key, 0).toString()
        }
        actual == expectedValue
    } else if (setting != null) {
        settingValueActive(context, addon, setting)
    } else {
        readSettingString(context, SettingProvider.GLOBAL, key)
            ?.let { valueActive(it) }
            ?: (readSettingInt(context, SettingProvider.GLOBAL, key, 0) != 0)
    }
    return if (negate) !active else active
}

private fun settingValueActive(context: Context, addon: AddonUiModel, setting: AddonSettingDef): Boolean {
    return when (setting.type) {
        SettingType.TOGGLE, SettingType.SWITCH, SettingType.CHECKBOX -> {
            val defaultVal = if (setting.defaultBool) 1 else setting.defaultInt
            readStoredInt(context, setting, addon.id, addon.jarPath, addon.isSystem, defaultVal) != 0
        }
        SettingType.INT -> readStoredInt(context, setting, addon.id, addon.jarPath, addon.isSystem, setting.defaultInt) != 0
        SettingType.FLOAT -> readStoredFloat(context, setting, addon.id, addon.jarPath, addon.isSystem, setting.defaultFloat) != 0f
        SettingType.APP_LIST -> readStoredArray(context, setting, addon.id, addon.jarPath, addon.isSystem).isNotEmpty()
        else -> valueActive(readStoredString(context, setting, addon.id, addon.jarPath, addon.isSystem) ?: setting.defaultString)
    }
}

private fun settingValueString(context: Context, addon: AddonUiModel, setting: AddonSettingDef): String {
    return when (setting.type) {
        SettingType.TOGGLE, SettingType.SWITCH, SettingType.CHECKBOX, SettingType.INT -> {
            val defaultVal = if (setting.defaultBool) 1 else setting.defaultInt
            readStoredInt(context, setting, addon.id, addon.jarPath, addon.isSystem, defaultVal).toString()
        }
        SettingType.FLOAT -> readStoredFloat(context, setting, addon.id, addon.jarPath, addon.isSystem, setting.defaultFloat).toString()
        SettingType.APP_LIST -> readStoredArray(context, setting, addon.id, addon.jarPath, addon.isSystem).joinToString(",")
        else -> readStoredString(context, setting, addon.id, addon.jarPath, addon.isSystem) ?: setting.defaultString
    }
}

private fun valueActive(raw: String): Boolean {
    val value = raw.trim().lowercase(Locale.ROOT)
    return value.isNotEmpty() && value != "0" && value != "false" && value != "off" && value != "none" && value != "null"
}

private fun writeForcedSettingValue(
    context: Context,
    addon: AddonUiModel,
    setting: AddonSettingDef,
    rawValue: String
): Boolean {
    if (rawValue.isBlank()) return false
    val value = if (rawValue == "default" || rawValue == "${'$'}default") defaultValueString(setting) else rawValue
    return when (setting.type) {
        SettingType.TOGGLE, SettingType.SWITCH, SettingType.CHECKBOX, SettingType.INT -> {
            val intValue = value.toBooleanStrictOrNull()?.let { if (it) 1 else 0 } ?: value.toIntOrNull() ?: return false
            val current = readStoredInt(context, setting, addon.id, addon.jarPath, addon.isSystem, setting.defaultInt)
            if (current == intValue) false else {
                writeStoredInt(context, setting, addon.id, addon.jarPath, addon.isSystem, intValue)
                true
            }
        }
        SettingType.FLOAT -> {
            val floatValue = value.toFloatOrNull() ?: return false
            val current = readStoredFloat(context, setting, addon.id, addon.jarPath, addon.isSystem, setting.defaultFloat)
            if (current == floatValue) false else {
                writeStoredFloat(context, setting, addon.id, addon.jarPath, addon.isSystem, floatValue)
                true
            }
        }
        SettingType.APP_LIST -> false
        else -> {
            val current = readStoredString(context, setting, addon.id, addon.jarPath, addon.isSystem) ?: setting.defaultString
            if (current == value) false else {
                writeStoredString(context, setting, addon.id, addon.jarPath, addon.isSystem, value)
                true
            }
        }
    }
}

private fun defaultValueString(setting: AddonSettingDef): String {
    return when (setting.type) {
        SettingType.TOGGLE, SettingType.SWITCH, SettingType.CHECKBOX -> if (setting.defaultBool || setting.defaultInt != 0) "1" else "0"
        SettingType.INT -> setting.defaultInt.toString()
        SettingType.FLOAT -> setting.defaultFloat.toString()
        else -> setting.defaultString
    }
}

// =====================================================================
// Parse settings from addon.json
// =====================================================================

private fun parseSettings(json: JSONObject): List<AddonSettingDef> {
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
    val dirs = listOf(SYSTEM_ADDON_DIR, ADDON_DIR)

    // Track which addon IDs have a user override (data dir takes priority)
    val userOverrideIds = mutableSetOf<String>()
    val userDir = java.io.File(ADDON_DIR)
    if (userDir.exists() && userDir.isDirectory) {
        userDir.listFiles()?.forEach { file ->
            if (!file.name.endsWith(".jar")) return@forEach
            try {
                val json = readDescriptor(file, context) ?: return@forEach
                val entryClassStr = json.optString("entryClass", "")
                val id = json.optString("id", entryClassStr.ifEmpty { file.nameWithoutExtension })
                userOverrideIds.add(id)
            } catch (_: Throwable) {}
        }
    }

    for (dirPath in dirs) {
        val isSystemDir = dirPath == SYSTEM_ADDON_DIR
        val dir = java.io.File(dirPath)
        if (!dir.exists() || !dir.isDirectory) continue
        val files = dir.listFiles() ?: continue

        for (file in files) {
            if (!file.name.endsWith(".jar")) continue
            try {
                val json = readDescriptor(file, context) ?: continue
                val entryClassStr = json.optString("entryClass", "")
                val id = json.optString("id", entryClassStr.ifEmpty { file.nameWithoutExtension })

                // Skip system addon if user override exists
                if (isSystemDir && id in userOverrideIds) continue

                val defaultEnabled = json.optBoolean("enabled", true)
                if (!readAddonEnabled(context, id, defaultEnabled)) continue

                flat.addAll(
                    parseMainEntries(json, id, file.absolutePath, isSystemDir, file)
                        .filter { includeTargetActivityEntries || it.targetActivity.isBlank() }
                )
            } catch (t: Throwable) {
                Log.e(TAG, "scanAddonMainEntries: failed for ${file.name}", t)
            }
        }
    }

    val topLevel = buildMainEntryTree(flat)
    // Sort top-level entries by priority descending, then title
    val sorted = topLevel.sortedWith(compareByDescending<AddonMainEntry> { it.priority }.thenBy { it.title })
    return AddonMainMenuModel(entries = sorted)
}

fun scanAddonActivityEntries(context: Context, activityName: String, slot: String = ""): List<AddonMainEntry> {
    val entries = mutableListOf<AddonMainEntry>()
    val dirs = listOf(SYSTEM_ADDON_DIR, ADDON_DIR)
    val userOverrideIds = mutableSetOf<String>()
    val userDir = File(ADDON_DIR)
    if (userDir.exists() && userDir.isDirectory) {
        userDir.listFiles()?.forEach { file ->
            if (!file.name.endsWith(".jar")) return@forEach
            try {
                val json = readDescriptor(file, context) ?: return@forEach
                val entryClassStr = json.optString("entryClass", "")
                val id = json.optString("id", entryClassStr.ifEmpty { file.nameWithoutExtension })
                userOverrideIds.add(id)
            } catch (_: Throwable) {}
        }
    }

    for (dirPath in dirs) {
        val isSystemDir = dirPath == SYSTEM_ADDON_DIR
        val dir = File(dirPath)
        if (!dir.exists() || !dir.isDirectory) continue
        dir.listFiles()?.forEach { file ->
            if (!file.name.endsWith(".jar")) return@forEach
            try {
                val json = readDescriptor(file, context) ?: return@forEach
                val entryClassStr = json.optString("entryClass", "")
                val id = json.optString("id", entryClassStr.ifEmpty { file.nameWithoutExtension })
                if (isSystemDir && id in userOverrideIds) return@forEach
                if (!readAddonEnabled(context, id, json.optBoolean("enabled", true))) return@forEach
                entries += parseMainEntries(json, id, file.absolutePath, isSystemDir, file)
                    .filter { entry ->
                        targetActivityMatches(entry.targetActivity, activityName) &&
                                (slot.isBlank() || entry.targetSlot.isBlank() || entry.targetSlot == slot.lowercase(Locale.ROOT))
                    }
            } catch (t: Throwable) {
                Log.e(TAG, "scanAddonActivityEntries: failed for ${file.name}", t)
            }
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
            showOutput = obj.optBoolean("showOutput", obj.optBoolean("show_output", obj.optBoolean("logOutput", obj.optBoolean("showLog", obj.optBoolean("show_log", true)))))
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

private fun optStringList(array: JSONArray?): List<String> {
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

private fun parseStringList(raw: String): List<String> {
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

private fun scanAddons(context: Context): List<AddonUiModel> {
    val result = linkedMapOf<String, AddonUiModel>()
    val dirs = listOf(SYSTEM_ADDON_DIR, ADDON_DIR)

    val systemAddonPathsById = mutableMapOf<String, String>()
    val systemDir = File(SYSTEM_ADDON_DIR)
    if (systemDir.exists() && systemDir.isDirectory) {
        systemDir.listFiles()?.forEach { file ->
            if (!file.name.endsWith(".jar")) return@forEach
            try {
                val json = readDescriptor(file, context) ?: return@forEach
                val entryClassStr = json.optString("entryClass", "")
                val id = json.optString("id", entryClassStr.ifEmpty { file.nameWithoutExtension })
                systemAddonPathsById[id] = file.absolutePath
            } catch (_: Throwable) {}
        }
    }

    // Pre-scan user dir to know which IDs have overrides
    val userOverrideIds = mutableSetOf<String>()
    val userDir = File(ADDON_DIR)
    if (userDir.exists() && userDir.isDirectory) {
        userDir.listFiles()?.forEach { file ->
            if (!file.name.endsWith(".jar")) return@forEach
            try {
                val json = readDescriptor(file, context) ?: return@forEach
                val entryClassStr = json.optString("entryClass", "")
                val id = json.optString("id", entryClassStr.ifEmpty { file.nameWithoutExtension })
                userOverrideIds.add(id)
            } catch (_: Throwable) {}
        }
    }

    for (dirPath in dirs) {
        val isSystemDir = dirPath == SYSTEM_ADDON_DIR
        val dir = File(dirPath)
        if (!dir.exists() || !dir.isDirectory) continue
        val files = dir.listFiles() ?: continue

        for (file in files) {
            if (!file.name.endsWith(".jar")) continue
            try {
                val json = readDescriptor(file, context) ?: continue
                val entryClassStr = json.optString("entryClass", "")
                val id = json.optString("id", entryClassStr.ifEmpty { file.nameWithoutExtension })

                // Skip system addon entirely if user override exists — user version takes full priority
                if (isSystemDir && id in userOverrideIds) continue

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

                val model = AddonUiModel(
                    id = id, entryClass = entryClassStr, name = name, author = author, description = description,
                    version = version, jarPath = file.absolutePath,
                    defaultTargets = defaultTargets,
                    enabled = readAddonEnabled(context, id, defaultEnabled),
                    scopeMode = readScopeMode(context, id),
                    customTargets = readCustomTargets(context, id),
                    settings = parseSettings(json),
                    isSystem = isSystemDir,
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
                    mainEntries = mainTopLevel
                )
                val existing = result[id]
                val systemJarPath = systemAddonPathsById[id]
                result[id] = if (!isSystemDir && systemJarPath != null) {
                    model.copy(isSystem = true, hasDataOverride = true, systemJarPath = systemJarPath)
                } else if (!isSystemDir && existing?.isSystem == true) {
                    model.copy(isSystem = true, hasDataOverride = true, systemJarPath = existing.jarPath)
                } else {
                    model
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to read addon: ${file.name}", t)
            }
        }
    }
    return result.values.toList()
}

private fun readDescriptor(jarFile: File): org.json.JSONObject? = readDescriptor(jarFile, null)

private fun readDescriptor(jarFile: File, context: Context?): org.json.JSONObject? {
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
        listOf("title", "subtitle", "description", "group").forEach { field ->
            if (localized.has(field)) base.put(field, localized.optString(field, base.optString(field)))
        }
        val baseSettings = base.optJSONArray("settings") ?: base.optJSONArray("children")
        val localizedSettings = localized.optJSONArray("settings") ?: localized.optJSONArray("children")
        if (baseSettings != null && localizedSettings != null) {
            mergeLocalizedSettings(baseSettings, localizedSettings)
        }
    }
}

private fun mergeLocalizedSettings(baseSettings: JSONArray, localizedSettings: JSONArray) {
    val localizedByKey = mutableMapOf<String, JSONObject>()
    for (index in 0 until localizedSettings.length()) {
        val localized = localizedSettings.optJSONObject(index) ?: continue
        val key = localized.optString("key", "")
        if (key.isNotBlank()) localizedByKey[key] = localized
    }
    for (index in 0 until baseSettings.length()) {
        val base = baseSettings.optJSONObject(index) ?: continue
        val localized = localizedByKey[base.optString("key", "")] ?: continue
        listOf("title", "description").forEach { field ->
            if (localized.has(field)) base.put(field, localized.optString(field, base.optString(field)))
        }
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
private fun extractBitmapFromJar(jarFile: File, entryPath: String): Bitmap? {
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

private fun looksLikeBitmapPath(value: String): Boolean {
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

private fun syncWhitelist(context: Context, addons: List<AddonUiModel>) {
    for (addon in addons) {
        if (!addon.enabled) continue
        for (pkg in getEffectiveTargets(addon)) {
            if (pkg !in BUILTIN_WHITELIST) addToWhitelist(context, pkg)
        }
    }
}

private fun getEffectiveTargets(addon: AddonUiModel): Set<String> {
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
private fun buildActiveAppsMap(addons: List<AddonUiModel>): Map<String, List<AddonUiModel>> {
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

private fun importAddonJar(context: Context, uri: Uri): Boolean {
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

private fun deleteAddon(context: Context, addon: AddonUiModel, allAddons: List<AddonUiModel>) {
    try {
        val jarFile = File(addon.jarPath)
        val descFile = File(addon.jarPath + ".json")

        if (addon.isSystem && addon.hasDataOverride) {
            if (jarFile.exists()) jarFile.delete()
            if (descFile.exists()) descFile.delete()
            val dataOverrideDir = File(File(addon.jarPath).parentFile ?: File(ADDON_DIR), File(addon.jarPath).nameWithoutExtension + "_data")
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

private fun loadInstalledApps(pm: PackageManager): List<AppInfoItem> {
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AddonManagerSection(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var addons by remember { mutableStateOf<List<AddonUiModel>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedAddon by remember { mutableStateOf<AddonUiModel?>(null) }
    var showDeleteDialog by remember { mutableStateOf<AddonUiModel?>(null) }
    var safeModeActive by remember { mutableStateOf(isSafeModeActive(context)) }
    var highlightedAddonId by remember { mutableStateOf<String?>(null) }
    val bringIntoViewRequesters = remember { mutableMapOf<String, BringIntoViewRequester>() }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            addons = scanAddons(context)
            syncWhitelist(context, addons)
        }
        isLoading = false
    }

    fun refreshAddons() {
        scope.launch {
            isLoading = true
            withContext(Dispatchers.IO) {
                addons = scanAddons(context)
                syncWhitelist(context, addons)
            }
            isLoading = false
        }
    }

    val importSuccessMsg = dynamicStringResource(R.string.addon_import_success)
    val importFailedMsg = dynamicStringResource(R.string.addon_import_failed)

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val success = withContext(Dispatchers.IO) { importAddonJar(context, uri) }
                Toast.makeText(
                    context,
                    if (success) importSuccessMsg else importFailedMsg,
                    Toast.LENGTH_SHORT
                ).show()
                if (success) refreshAddons()
            }
        }
    }

    val systemAddons = addons.filter { it.isSystem }
    val userAddons = addons.filter { !it.isSystem }
    val activeCount = addons.count { it.enabled }

    Column(modifier = modifier.fillMaxWidth()) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    dynamicStringResource(R.string.addon_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    dynamicStringResource(R.string.addon_count, userAddons.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    dynamicStringResource(R.string.addon_active_count, activeCount, addons.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            IconButton(onClick = { refreshAddons() }) {
                Icon(Icons.Rounded.Refresh, dynamicStringResource(R.string.menu_refresh), tint = MaterialTheme.colorScheme.primary)
            }
        }

        // Import button card
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { filePickerLauncher.launch("application/java-archive") }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Rounded.Add, null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        dynamicStringResource(R.string.addon_import),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        dynamicStringResource(R.string.addon_import_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }
                Icon(
                    Icons.Rounded.FileOpen, null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f)
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // === Active apps section ===
        if (!isLoading) {
            val activeAppsMap = remember(addons) { buildActiveAppsMap(addons) }
            if (activeAppsMap.isNotEmpty()) {
                ActiveAppsSection(
                    activeAppsMap = activeAppsMap,
                    onAddonClick = { addonId ->
                        scope.launch {
                            highlightedAddonId = addonId
                            bringIntoViewRequesters[addonId]?.bringIntoView()
                            delay(1500)
                            highlightedAddonId = null
                        }
                    },
                    onDisableAddon = { addonId ->
                        val addon = addons.find { it.id == addonId } ?: return@ActiveAppsSection
                        writeAddonEnabled(context, addonId, false)
                        val updatedList = addons.map { if (it.id == addonId) it.copy(enabled = false) else it }
                        scope.launch(Dispatchers.IO) {
                            val otherTargets = updatedList
                                .filter { it.id != addonId && it.enabled }
                                .flatMap { getEffectiveTargets(it) }
                                .toSet()
                            for (pkg in getEffectiveTargets(addon)) {
                                if (pkg !in BUILTIN_WHITELIST && pkg !in otherTargets) {
                                    removeFromWhitelist(context, pkg)
                                }
                            }
                        }
                        addons = updatedList
                    }
                )
                Spacer(Modifier.height(12.dp))
            }
        }

        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(modifier = Modifier.size(32.dp))
            }
        }

        if (!isLoading) {
            // === Safe mode banner ===
            if (safeModeActive) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Rounded.Warning, null,
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                dynamicStringResource(R.string.addon_safe_mode_title),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            dynamicStringResource(R.string.addon_safe_mode_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = {
                                exitAddonSafeMode(context)
                                safeModeActive = false
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            )
                        ) {
                            Text(dynamicStringResource(R.string.addon_safe_mode_exit))
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            // === System addons section ===
            SectionHeader(dynamicStringResource(R.string.addon_section_system))

            if (systemAddons.isEmpty()) {
                EmptySectionCard(
                    title = dynamicStringResource(R.string.addon_system_empty_title),
                    description = dynamicStringResource(R.string.addon_system_empty_desc)
                )
            } else {
                for (addon in systemAddons) {
                    val requester = remember { BringIntoViewRequester() }
                    bringIntoViewRequesters[addon.id] = requester
                    AddonCard(
                        addon = addon,
                        isSystem = true,
                        isHighlighted = highlightedAddonId == addon.id,
                        bringIntoViewRequester = requester,
                        onToggle = { enabled ->
                            writeAddonEnabled(context, addon.id, enabled)
                            val updatedList = addons.map { if (it.id == addon.id) it.copy(enabled = enabled) else it }
                            scope.launch(Dispatchers.IO) {
                                if (enabled) {
                                    for (pkg in getEffectiveTargets(addon)) {
                                        if (pkg !in BUILTIN_WHITELIST) addToWhitelist(context, pkg)
                                    }
                                } else {
                                    val otherTargets = updatedList
                                        .filter { it.id != addon.id && it.enabled }
                                        .flatMap { getEffectiveTargets(it) }
                                        .toSet()
                                    for (pkg in getEffectiveTargets(addon)) {
                                        if (pkg !in BUILTIN_WHITELIST && pkg !in otherTargets) {
                                            removeFromWhitelist(context, pkg)
                                        }
                                    }
                                }
                            }
                            addons = updatedList
                        },
                        onClick = { selectedAddon = addon },
                        onDelete = { if (addon.hasDataOverride) showDeleteDialog = addon },
                        onRefresh = ::refreshAddons
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }

            Spacer(Modifier.height(16.dp))

            // === User addons section ===
            SectionHeader(dynamicStringResource(R.string.addon_section_user))

            if (userAddons.isEmpty()) {
                EmptySectionCard(
                    title = dynamicStringResource(R.string.addon_user_empty_title),
                    description = dynamicStringResource(R.string.addon_user_empty_desc)
                )
            } else {
                for (addon in userAddons) {
                    val requester = remember { BringIntoViewRequester() }
                    bringIntoViewRequesters[addon.id] = requester
                    AddonCard(
                        addon = addon,
                        isSystem = false,
                        isHighlighted = highlightedAddonId == addon.id,
                        bringIntoViewRequester = requester,
                        onToggle = { enabled ->
                            writeAddonEnabled(context, addon.id, enabled)
                            val updatedList = addons.map { if (it.id == addon.id) it.copy(enabled = enabled) else it }
                            scope.launch(Dispatchers.IO) {
                                if (enabled) {
                                    for (pkg in getEffectiveTargets(addon)) {
                                        if (pkg !in BUILTIN_WHITELIST) addToWhitelist(context, pkg)
                                    }
                                } else {
                                    val otherTargets = updatedList
                                        .filter { it.id != addon.id && it.enabled }
                                        .flatMap { getEffectiveTargets(it) }
                                        .toSet()
                                    for (pkg in getEffectiveTargets(addon)) {
                                        if (pkg !in BUILTIN_WHITELIST && pkg !in otherTargets) {
                                            removeFromWhitelist(context, pkg)
                                        }
                                    }
                                }
                            }
                            addons = updatedList
                        },
                        onClick = { selectedAddon = addon },
                        onDelete = { showDeleteDialog = addon },
                        onRefresh = ::refreshAddons
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }

    // Detail dialog
    selectedAddon?.let { addon ->
        AddonDetailDialog(
            addon = addon,
            onDismiss = { selectedAddon = null },
            onSave = { updatedAddon ->
                val oldTargets = getEffectiveTargets(addon)
                val newTargets = getEffectiveTargets(updatedAddon)

                writeAddonEnabled(context, updatedAddon.id, updatedAddon.enabled)
                writeScopeMode(context, updatedAddon.id, updatedAddon.scopeMode)
                writeCustomTargets(context, updatedAddon.id, updatedAddon.customTargets)

                val updatedAddons = addons.map { if (it.id == updatedAddon.id) updatedAddon else it }

                scope.launch(Dispatchers.IO) {
                    // Add new targets to whitelist
                    for (pkg in newTargets) {
                        if (pkg !in BUILTIN_WHITELIST) addToWhitelist(context, pkg)
                    }
                    // Remove targets that were dropped — but only if no other addon uses them
                    val removedTargets = oldTargets - newTargets
                    if (removedTargets.isNotEmpty()) {
                        val otherAddonsTargets = updatedAddons
                            .filter { it.id != updatedAddon.id && it.enabled }
                            .flatMap { getEffectiveTargets(it) }
                            .toSet()
                        for (pkg in removedTargets) {
                            if (pkg !in BUILTIN_WHITELIST && pkg !in otherAddonsTargets) {
                                removeFromWhitelist(context, pkg)
                            }
                        }
                    }
                }
                addons = updatedAddons
                selectedAddon = null
            }
        )
    }

    // Delete dialog
    showDeleteDialog?.let { addon ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            icon = { Icon(Icons.Rounded.DeleteForever, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(dynamicStringResource(R.string.addon_delete_title)) },
            text = {
                Text(
                    dynamicStringResource(
                        if (addon.hasDataOverride) R.string.addon_delete_data_update_confirm else R.string.addon_delete_confirm,
                        addon.name
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) { deleteAddon(context, addon, addons) }
                            showDeleteDialog = null
                            refreshAddons()
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text(dynamicStringResource(R.string.addon_btn_remove)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) { Text(dynamicStringResource(R.string.addon_btn_cancel)) }
            }
        )
    }
}

// =====================================================================
// Addon Card — with expandable settings panel
// =====================================================================

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AddonCard(
    addon: AddonUiModel,
    isSystem: Boolean = false,
    isHighlighted: Boolean = false,
    bringIntoViewRequester: BringIntoViewRequester? = null,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onRefresh: () -> Unit
) {
    val context = LocalContext.current
    val effectiveTargets = getEffectiveTargets(addon)
    val contentAlpha = if (addon.enabled) 1f else 0.5f
    var settingsExpanded by remember { mutableStateOf(false) }
    val onHeaderClick = {
        settingsExpanded = !settingsExpanded
    }

    // Pulsing white highlight like Android system settings
    var highlightVisible by remember { mutableStateOf(false) }
    LaunchedEffect(isHighlighted) {
        if (isHighlighted) {
            highlightVisible = true
        } else {
            highlightVisible = false
        }
    }
    val infiniteTransition = rememberInfiniteTransition(label = "highlightPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 300),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    // Parse custom card color
    val cardContainerColor = remember(addon.cardColor) {
        if (addon.cardColor.isNotEmpty()) {
            try { Color(android.graphics.Color.parseColor(addon.cardColor)) }
            catch (_: Throwable) { null }
        } else null
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = cardContainerColor ?: MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .then(if (bringIntoViewRequester != null) Modifier.bringIntoViewRequester(bringIntoViewRequester) else Modifier)
    ) {
        // Composable to render background layers
        @Composable
        fun BackgroundLayers(modifier: Modifier = Modifier) {
            if (addon.backgroundBitmap != null) {
                val bgImageBitmap = remember(addon.id) { addon.backgroundBitmap.asImageBitmap() }
                val bgAlphaFloat = (addon.backgroundAlpha / 100f).coerceIn(0f, 1f) *
                    if (addon.enabled) 1f else 0.4f
                Image(
                    bitmap = bgImageBitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = modifier
                        .alpha(bgAlphaFloat)
                        .then(
                            if (addon.backgroundBlur && addon.backgroundBlurRadius > 0)
                                Modifier.blur(addon.backgroundBlurRadius.dp)
                            else Modifier
                        )
                )
                if (addon.backgroundMode == "gradient" && addon.backgroundGradientSteps.size >= 2) {
                    val surfaceColor = cardContainerColor ?: MaterialTheme.colorScheme.surface
                    val gradientColors = addon.backgroundGradientSteps.map { opacity ->
                        surfaceColor.copy(alpha = (opacity / 100f).coerceIn(0f, 1f))
                    }
                    Box(modifier = modifier.background(Brush.verticalGradient(colors = gradientColors)))
                }
            }
        }

        if (addon.backgroundScope == "header") {
            // Background covers only the header, not the expandable settings
            Box {
                Column {
                    Box {
                        BackgroundLayers(Modifier.matchParentSize())
                        Column(modifier = Modifier.padding(16.dp)) {
                            AddonCardHeader(addon, contentAlpha, effectiveTargets, settingsExpanded,
                                onToggle, onClick, onDelete, isSystem, onHeaderClick)
                        }
                    }
                    // Settings panel outside background
                    AddonCardSettings(addon, settingsExpanded, onRefresh)
                }
                // Highlight overlay on top of everything
                if (highlightVisible) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(Color.White.copy(alpha = pulseAlpha))
                    )
                }
            }
        } else {
            // "full" — background extends to the entire card including settings
            Box {
                BackgroundLayers(Modifier.matchParentSize())
                Column(modifier = Modifier.padding(16.dp)) {
                    AddonCardHeader(addon, contentAlpha, effectiveTargets, settingsExpanded,
                        onToggle, onClick, onDelete, isSystem, onHeaderClick)
                    AddonCardSettings(addon, settingsExpanded, onRefresh)
                }
                // Highlight overlay on top of everything
                if (highlightVisible) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(Color.White.copy(alpha = pulseAlpha))
                    )
                }
            }
        }
    }
}

@Composable
private fun AddonCardHeader(
    addon: AddonUiModel,
    contentAlpha: Float,
    effectiveTargets: Set<String>,
    settingsExpanded: Boolean,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    isSystem: Boolean,
    onHeaderClick: () -> Unit = {}
) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onHeaderClick() }
            ) {
                // Custom icon or default Extension icon
                if (addon.iconBitmap != null) {
                    val iconImageBitmap = remember(addon.id) { addon.iconBitmap.asImageBitmap() }
                    Image(
                        bitmap = iconImageBitmap,
                        contentDescription = null,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )
                } else {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            if (addon.enabled) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceContainerHigh
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.Extension, null,
                        modifier = Modifier.size(20.dp),
                        tint = if (addon.enabled) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                }

                Spacer(Modifier.width(12.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .alpha(contentAlpha)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            addon.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "v${addon.version}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                    Text(
                        addon.author,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        addon.id,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Switch(
                    checked = addon.enabled,
                    onCheckedChange = onToggle,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .clickable(onClick = {}) // consume click to prevent header toggle
                )
            }

            if (addon.description.isNotEmpty()) {
                Text(
                    addon.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .alpha(contentAlpha)
                )
            }

            if (effectiveTargets.isNotEmpty() && !addon.settingsOnly) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .alpha(contentAlpha),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        Icons.Outlined.FilterAlt, null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                    Text(
                        effectiveTargets.joinToString(", ") { it.substringAfterLast(".") },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (addon.settingsOnly) {
                    // Settings-only addon: show info chip instead of scope
                    SuggestionChip(
                        onClick = onHeaderClick,
                        label = { Text("Settings UI", style = MaterialTheme.typography.labelSmall) },
                        icon = {
                            Icon(
                                if (settingsExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                null,
                                modifier = Modifier.size(14.dp)
                            )
                        },
                        modifier = Modifier.height(26.dp)
                    )
                } else {
                val scopeLabel = when (addon.scopeMode) {
                    0 -> dynamicStringResource(R.string.addon_scope_label_default)
                    1 -> dynamicStringResource(R.string.addon_scope_label_custom)
                    2 -> dynamicStringResource(R.string.addon_scope_label_merge)
                    else -> dynamicStringResource(R.string.addon_scope_label_default)
                }

                SuggestionChip(
                    onClick = onClick,
                    label = { Text(scopeLabel, style = MaterialTheme.typography.labelSmall) },
                    icon = { Icon(Icons.Outlined.Tune, null, modifier = Modifier.size(14.dp)) },
                    modifier = Modifier.height(26.dp)
                )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    if (!isSystem || addon.hasDataOverride) {
                        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                            Icon(
                                Icons.Outlined.Delete, dynamicStringResource(R.string.addon_btn_remove),
                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
}

@Composable
private fun AddonCardSettings(
    addon: AddonUiModel,
    settingsExpanded: Boolean,
    onRefresh: () -> Unit
) {
            val context = LocalContext.current
            val importFailedMsg = dynamicStringResource(R.string.addon_import_failed)
            var settingsRevision by remember(addon.id) { mutableIntStateOf(0) }
            val allSettings = remember(addon.settings) { flattenSettings(addon.settings) }
            fun onSettingChanged(setting: AddonSettingDef, booleanValue: Boolean?) {
                if (booleanValue == true) {
                    applyExclusiveSettingLogic(context, addon, setting, allSettings)
                }
                settingsRevision++
            }

            val settingsImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                if (uri != null) {
                    val ok = importAddonSettings(context, uri, addon)
                    if (!ok) Toast.makeText(context, importFailedMsg, Toast.LENGTH_SHORT).show()
                    settingsRevision++
                }
            }
            val settingsExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
                if (uri != null) exportAddonSettings(context, uri, addon)
            }

            val hasInlineSettings = addon.settings.isNotEmpty() || addon.updateUrl.isNotBlank()
            val hasMainEntries = addon.mainEntries.isNotEmpty()

            // ---- Panel: no inline settings but has main[] activity pages ----
            AnimatedVisibility(
                visible = settingsExpanded && !hasInlineSettings && hasMainEntries,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .padding(
                            top = 8.dp,
                            start = if (addon.backgroundScope == "header") 16.dp else 0.dp,
                            end = if (addon.backgroundScope == "header") 16.dp else 0.dp,
                            bottom = if (addon.backgroundScope == "header") 16.dp else 0.dp
                        )
                        .clickable(onClick = {})
                ) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    // Info message
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Outlined.Info,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            dynamicStringResource(R.string.addon_no_inline_settings_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                    // Link buttons for each top-level main entry
                    val accent = parseOptionalColor(addon.accentColor) ?: MaterialTheme.colorScheme.primary
                    addon.mainEntries.forEach { entry ->
                        OutlinedButton(
                            onClick = {
                                AddonPageActivity.start(
                                    context,
                                    addonId = addon.id,
                                    pageId = entry.leafId,
                                    title = entry.title,
                                    includeTargetActivityEntries = true
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 6.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                accent.copy(alpha = 0.5f)
                            )
                        ) {
                            AddonMainEntryIcon(
                                entry = entry,
                                containerSize = 28.dp,
                                fallbackTint = accent,
                                fallbackContainer = accent.copy(alpha = 0.12f)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                entry.title,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium.withAddonTextSize(entry.titleSizeSp),
                                color = accent
                            )
                            Icon(
                                Icons.Filled.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = accent.copy(alpha = 0.7f)
                            )
                        }
                    }

                    AddonSettingsActions(
                        addon = addon,
                        onImport = { settingsImportLauncher.launch(arrayOf("application/json", "text/*", "*/*")) },
                        onExport = { settingsExportLauncher.launch("${sanitizeFileSegment(addon.id)}_settings.json") },
                        onRefresh = onRefresh
                    )
                }
            }

            // ---- Panel: manifest-only card without runtime entry/settings ----
            AnimatedVisibility(
                visible = settingsExpanded && !hasInlineSettings && !hasMainEntries,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .padding(
                            top = 8.dp,
                            start = if (addon.backgroundScope == "header") 16.dp else 0.dp,
                            end = if (addon.backgroundScope == "header") 16.dp else 0.dp,
                            bottom = if (addon.backgroundScope == "header") 16.dp else 0.dp
                        )
                        .clickable(onClick = {})
                ) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Outlined.Info,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            dynamicStringResource(R.string.addon_no_inline_settings_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }

                    AddonSettingsActions(
                        addon = addon,
                        onImport = { settingsImportLauncher.launch(arrayOf("application/json", "text/*", "*/*")) },
                        onExport = { settingsExportLauncher.launch("${sanitizeFileSegment(addon.id)}_settings.json") },
                        onRefresh = onRefresh
                    )
                }
            }

            // ---- Expandable settings panel (normal inline settings) ----
            AnimatedVisibility(
                visible = settingsExpanded && hasInlineSettings,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(modifier = Modifier
                    .padding(top = 8.dp, start = if (addon.backgroundScope == "header") 16.dp else 0.dp,
                             end = if (addon.backgroundScope == "header") 16.dp else 0.dp,
                             bottom = if (addon.backgroundScope == "header") 16.dp else 0.dp)
                    .clickable(onClick = {}) // consume clicks — prevent Card onClick (detail dialog)
                ) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    Text(
                        dynamicStringResource(R.string.addon_settings_title),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = parseOptionalColor(addon.accentColor) ?: MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    for (setting in addon.settings) {
                        key(setting.key) {
                            val settingModifier = if (setting.type == SettingType.GROUP && setting.groupMode != GroupMode.INLINE) {
                                Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp)
                            } else {
                                Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                            }
                            AddonSettingControl(
                                setting = setting,
                                addon = addon,
                                modifier = settingModifier,
                                allSettings = allSettings,
                                dependencyRevision = settingsRevision,
                                onSettingChanged = ::onSettingChanged
                            )
                        }
                    }

                    AddonSettingsActions(
                        addon = addon,
                        onImport = { settingsImportLauncher.launch(arrayOf("application/json", "text/*", "*/*")) },
                        onExport = { settingsExportLauncher.launch("${sanitizeFileSegment(addon.id)}_settings.json") },
                        onRefresh = onRefresh
                    )
                }
            }
}

// =====================================================================
// Single setting control — dispatches by type
// =====================================================================

@Composable
internal fun AddonSettingControl(
    setting: AddonSettingDef,
    addon: AddonUiModel,
    modifier: Modifier = Modifier,
    allSettings: List<AddonSettingDef> = emptyList(),
    inheritedEnabled: Boolean = true,
    dependencyRevision: Int = 0,
    onSettingChanged: (AddonSettingDef, Boolean?) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val dependencyState = resolveSettingDependencyState(context, addon, setting, allSettings)
    val enabled = inheritedEnabled && dependencyState.enabled
    LaunchedEffect(setting.key, enabled, dependencyState.forceValue, dependencyRevision) {
        if (!enabled && dependencyState.forceValue.isNotBlank()) {
            if (writeForcedSettingValue(context, addon, setting, dependencyState.forceValue)) {
                onSettingChanged(setting, null)
            }
        }
    }
    val effectiveModifier = if (setting.isImmersiveGroup()) modifier.fillMaxWidth() else modifier
    val interactionSource = remember { MutableInteractionSource() }
    Box(modifier = effectiveModifier) {
        val contentModifier = Modifier.alpha(if (enabled) 1f else 0.45f)
        if (setting.icon.isNotEmpty() && setting.type != SettingType.VISUAL && setting.type != SettingType.GROUP) {
            Row(modifier = contentModifier, verticalAlignment = Alignment.CenterVertically) {
                SettingIcon(setting, addon, Modifier.padding(end = 12.dp))
                val innerMod = Modifier.weight(1f)
                AddonSettingControlInner(setting, addon, innerMod, allSettings, enabled, dependencyRevision, onSettingChanged)
            }
        } else {
            AddonSettingControlInner(setting, addon, contentModifier, allSettings, enabled, dependencyRevision, onSettingChanged)
        }
        if (!enabled) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = {}
                    )
            )
        }
    }
}

@Composable
private fun AddonSettingControlInner(
    setting: AddonSettingDef,
    addon: AddonUiModel,
    modifier: Modifier = Modifier,
    allSettings: List<AddonSettingDef> = emptyList(),
    inheritedEnabled: Boolean = true,
    dependencyRevision: Int = 0,
    onSettingChanged: (AddonSettingDef, Boolean?) -> Unit = { _, _ -> }
) {
    when (setting.type) {
        SettingType.INT -> IntSliderSettingControl(setting, addon, modifier, onSettingChanged)
        SettingType.FLOAT -> FloatSliderSettingControl(setting, addon, modifier, onSettingChanged)
        SettingType.STRING -> StringSettingControl(setting, addon, modifier, onSettingChanged)
        SettingType.SELECT -> SelectSettingControl(setting, addon, modifier, onSettingChanged)
        SettingType.SELECT_BUTTON -> SelectButtonSettingControl(setting, addon, modifier, onSettingChanged)
        SettingType.FILE -> FileSettingControl(setting, addon, modifier, onSettingChanged)
        SettingType.APP_LIST -> AppListSettingControl(setting, addon, modifier, onSettingChanged)
        SettingType.COLOR -> ColorSettingControl(setting, addon, modifier, onSettingChanged)
        SettingType.GROUP -> GroupSettingControl(setting, addon, modifier, allSettings, inheritedEnabled, dependencyRevision, onSettingChanged)
        SettingType.VISUAL -> VisualSettingControl(setting, addon, modifier)
        SettingType.COMMAND_BUTTON -> CommandButtonSettingControl(setting, addon, modifier)
        SettingType.TOGGLE, SettingType.SWITCH -> SwitchSettingControl(setting, addon, modifier, onSettingChanged)
        SettingType.CHECKBOX -> CheckboxSettingControl(setting, addon, modifier, onSettingChanged)
        SettingType.TILE -> TileBindingControl(setting, addon, modifier)
    }
}

// ---------- COMMAND BUTTON ----------

@Composable
private fun CommandButtonSettingControl(
    setting: AddonSettingDef,
    addon: AddonUiModel,
    modifier: Modifier
) {
    val scope = rememberCoroutineScope()
    val accent = settingAccent(setting, addon)
    val command = setting.command.ifBlank { setting.commandOn }
    var running by remember { mutableStateOf(false) }
    var output by remember { mutableStateOf("") }

    Column(modifier = modifier.fillMaxWidth()) {
        if (setting.description.isNotEmpty()) {
            SettingDescriptionText(setting, modifier = Modifier.padding(bottom = 8.dp))
        }
        Button(
            onClick = {
                if (command.isBlank() || running) return@Button
                running = true
                if (setting.showOutput) output = ""
                scope.launch {
                    val result = withContext(Dispatchers.IO) { runShellCommandForResult(command) }
                    if (setting.showOutput) output = result.combinedOutput()
                    running = false
                }
            },
            enabled = command.isNotBlank() && !running,
            colors = ButtonDefaults.buttonColors(containerColor = accent),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (running) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = setting.title,
                style = MaterialTheme.typography.labelLarge.withAddonTextSize(setting.titleSizeSp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (setting.showOutput) {
            CommandOutputPanel(output = output, running = running, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
private fun CommandOutputPanel(
    output: String,
    running: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(visible = running || output.isNotBlank()) {
        Surface(
            modifier = modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                if (running) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                if (output.isNotBlank()) {
                    Text(
                        text = output,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 180.dp)
                            .verticalScroll(rememberScrollState())
                    )
                }
            }
        }
    }
}

// ---------- TOGGLE / SWITCH ----------

@Composable
private fun SwitchSettingControl(
    setting: AddonSettingDef,
    addon: AddonUiModel,
    modifier: Modifier,
    onSettingChanged: (AddonSettingDef, Boolean?) -> Unit
) {
    val context = LocalContext.current
    val accent = settingAccent(setting, addon)
    val defaultVal = if (setting.defaultBool) 1 else setting.defaultInt
    var checked by remember {
        mutableStateOf(readStoredInt(context, setting, addon.id, addon.jarPath, addon.isSystem, defaultVal) != 0)
    }
    val scope = rememberCoroutineScope()
    var commandRunning by remember { mutableStateOf(false) }
    var commandOutput by remember { mutableStateOf("") }

    fun updateChecked(newChecked: Boolean) {
        if (commandRunning) return
        checked = newChecked
        writeStoredInt(context, setting, addon.id, addon.jarPath, addon.isSystem, if (newChecked) 1 else 0)
        onSettingChanged(setting, newChecked)
        val command = if (newChecked) setting.commandOn else setting.commandOff
        if (command.isBlank()) return
        commandRunning = true
        if (setting.showOutput) commandOutput = ""
        scope.launch {
            val result = withContext(Dispatchers.IO) { runShellCommandForResult(command) }
            if (setting.showOutput) commandOutput = result.combinedOutput()
            commandRunning = false
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = !commandRunning) { updateChecked(!checked) }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                SettingTitleText(setting)
                if (setting.description.isNotEmpty()) {
                    SettingDescriptionText(setting)
                }
            }
            Spacer(Modifier.width(12.dp))
            Switch(
                checked = checked,
                onCheckedChange = { updateChecked(it) },
                enabled = !commandRunning,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = accent,
                    checkedTrackColor = accent.copy(alpha = 0.45f)
                )
            )
        }
        if (setting.showOutput && (setting.commandOn.isNotBlank() || setting.commandOff.isNotBlank())) {
            CommandOutputPanel(output = commandOutput, running = commandRunning, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

// ---------- CHECKBOX ----------

@Composable
private fun CheckboxSettingControl(
    setting: AddonSettingDef,
    addon: AddonUiModel,
    modifier: Modifier,
    onSettingChanged: (AddonSettingDef, Boolean?) -> Unit
) {
    val context = LocalContext.current
    val accent = settingAccent(setting, addon)
    val defaultVal = if (setting.defaultBool) 1 else setting.defaultInt
    var checked by remember {
        mutableStateOf(readStoredInt(context, setting, addon.id, addon.jarPath, addon.isSystem, defaultVal) != 0)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable {
                checked = !checked
                writeStoredInt(context, setting, addon.id, addon.jarPath, addon.isSystem, if (checked) 1 else 0)
                onSettingChanged(setting, checked)
            }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = {
                checked = it
                writeStoredInt(context, setting, addon.id, addon.jarPath, addon.isSystem, if (it) 1 else 0)
                onSettingChanged(setting, it)
            },
            colors = CheckboxDefaults.colors(checkedColor = accent)
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            SettingTitleText(setting)
            if (setting.description.isNotEmpty()) {
                SettingDescriptionText(setting)
            }
        }
    }
}

// ---------- TILE BINDING ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TileBindingControl(
    setting: AddonSettingDef,
    addon: AddonUiModel,
    modifier: Modifier
) {
    val context = LocalContext.current
    val accent = settingAccent(setting, addon)
    val targets = remember(setting) {
        setting.tileTargets.ifEmpty {
            listOf(
                TileTargetOption(
                    key = setting.key,
                    label = setting.title,
                    mode = setting.defaultString.ifEmpty { "toggle" },
                    values = setting.options.map { it.value },
                    labels = setting.options.map { it.label },
                    pageId = setting.options.firstOrNull()?.value.orEmpty()
                )
            )
        }
    }
    val activityOptions = remember(setting.tileActivities) { setting.tileActivities }
    var configExpanded by rememberSaveable(setting.key) { mutableStateOf(false) }
    var targetMenuExpanded by remember { mutableStateOf(false) }
    var activityMenuExpanded by remember { mutableStateOf(false) }
    var selectedTargetKey by rememberSaveable(setting.key) { mutableStateOf(targets.firstOrNull()?.key.orEmpty()) }
    var selectedActivity by rememberSaveable(setting.key) { mutableStateOf(activityOptions.firstOrNull()?.value.orEmpty()) }
    var tileTitle by rememberSaveable(setting.key) { mutableStateOf(setting.title) }
    val selectedTarget = targets.find { it.key == selectedTargetKey } ?: targets.firstOrNull()

    // Find which slot this tile is bound to (if any)
    var boundSlot by remember(addon.id, setting.key) { mutableStateOf(findBoundTileSlot(context, addon.id, setting.key)) }
    val isBound = boundSlot > 0

    val tileMode = selectedTarget?.mode?.ifEmpty { setting.defaultString.ifEmpty { "toggle" } } ?: "toggle"
    val tileLabel = tileTitle.ifBlank { setting.title }
    val summaryOn = setting.unit.ifEmpty { "On" }
    val summaryOff = setting.description.ifEmpty { "Off" }
    val carouselValues = selectedTarget?.values?.joinToString(",").orEmpty()
    val carouselLabels = selectedTarget?.labels?.joinToString(",").orEmpty()
    val longPressPageId = selectedActivity.ifBlank { selectedTarget?.pageId.orEmpty() }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = setting.tileConfigurable && !isBound) { configExpanded = !configExpanded },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Rounded.Dashboard,
                null,
                modifier = Modifier.size(20.dp),
                tint = accent
            )
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    setting.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    if (isBound) "Slot $boundSlot" else if (setting.tileConfigurable) "Настраиваемая плитка" else "Не добавлена",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isBound) accent else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (setting.tileConfigurable && !isBound) {
                Icon(
                    if (configExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(8.dp))

        AnimatedVisibility(
            visible = setting.tileConfigurable && !isBound && configExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = tileTitle,
                    onValueChange = { tileTitle = it },
                    label = { Text("Название плитки") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                ExposedDropdownMenuBox(
                    expanded = targetMenuExpanded,
                    onExpandedChange = { targetMenuExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedTarget?.label.orEmpty(),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Настройка") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = targetMenuExpanded) },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = targetMenuExpanded,
                        onDismissRequest = { targetMenuExpanded = false }
                    ) {
                        targets.forEach { target ->
                            DropdownMenuItem(
                                text = { Text(target.label) },
                                onClick = {
                                    selectedTargetKey = target.key
                                    if (tileTitle.isBlank() || tileTitle == setting.title) tileTitle = target.label
                                    targetMenuExpanded = false
                                },
                                trailingIcon = {
                                    if (target.key == selectedTargetKey) Icon(Icons.Rounded.Check, null, Modifier.size(18.dp))
                                }
                            )
                        }
                    }
                }

                if (activityOptions.isNotEmpty()) {
                    val selectedActivityLabel = activityOptions.find { it.value == selectedActivity }?.label ?: selectedActivity
                    ExposedDropdownMenuBox(
                        expanded = activityMenuExpanded,
                        onExpandedChange = { activityMenuExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = selectedActivityLabel,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Долгое нажатие") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = activityMenuExpanded) },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = activityMenuExpanded,
                            onDismissRequest = { activityMenuExpanded = false }
                        ) {
                            activityOptions.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.label) },
                                    onClick = {
                                        selectedActivity = option.value
                                        activityMenuExpanded = false
                                    },
                                    trailingIcon = {
                                        if (option.value == selectedActivity) Icon(Icons.Rounded.Check, null, Modifier.size(18.dp))
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        if (isBound) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = {
                        unbindTileSlot(context, boundSlot)
                        boundSlot = 0
                        configExpanded = false
                    },
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                ) {
                    Icon(Icons.Rounded.Close, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                }
                FilledTonalButton(
                    onClick = { requestAddDynamicTile(context, boundSlot, readTileSlotLabel(context, boundSlot, tileLabel)) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Rounded.Add, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Добавить тайл")
                }
            }
        } else {
            FilledTonalButton(
                onClick = {
                    val slot = findFreeTileSlot(context)
                    val target = selectedTarget
                    if (slot > 0 && target != null) {
                        bindTileSlot(context, slot, addon.id, setting.key, target.key, tileMode, tileLabel, summaryOn, summaryOff, carouselValues, carouselLabels, longPressPageId)
                        boundSlot = slot
                        configExpanded = false
                    }
                },
                enabled = selectedTarget != null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Rounded.Link, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Привязать тайл")
            }
        }
    }
}

private fun readTileSlotLabel(context: Context, slot: Int, fallback: String): String {
    return Settings.Global.getString(context.contentResolver, "pixel_addon_tile_${slot}_label")
        ?.takeIf { it.isNotBlank() }
        ?: fallback
}

private fun findBoundTileSlot(context: Context, addonId: String, key: String): Int {
    for (i in 1..DYNAMIC_ADDON_TILE_COUNT) {
        val prefix = "pixel_addon_tile_${i}_"
        val enabled = Settings.Global.getInt(context.contentResolver, "${prefix}enabled", 0)
        if (enabled != 1) continue
        val boundAddon = Settings.Global.getString(context.contentResolver, "${prefix}addon_id") ?: ""
        val boundTileId = Settings.Global.getString(context.contentResolver, "${prefix}tile_id") ?: ""
        val boundKey = Settings.Global.getString(context.contentResolver, "${prefix}key") ?: ""
        if (boundAddon == addonId && (boundTileId == key || boundKey == key)) return i
    }
    return 0
}

private fun findFreeTileSlot(context: Context): Int {
    for (i in 1..DYNAMIC_ADDON_TILE_COUNT) {
        val enabled = Settings.Global.getInt(context.contentResolver, "pixel_addon_tile_${i}_enabled", 0)
        if (enabled != 1) return i
    }
    return 0
}

private fun bindTileSlot(
    context: Context, slot: Int, addonId: String, tileId: String, key: String,
    mode: String, label: String, summaryOn: String, summaryOff: String,
    values: String, labels: String, pageId: String
) {
    val prefix = "pixel_addon_tile_${slot}_"
    val cr = context.contentResolver
    Settings.Global.putInt(cr, "${prefix}enabled", 1)
    Settings.Global.putString(cr, "${prefix}tile_id", tileId)
    Settings.Global.putString(cr, "${prefix}key", key)
    Settings.Global.putString(cr, "${prefix}mode", mode)
    Settings.Global.putString(cr, "${prefix}label", label)
    Settings.Global.putString(cr, "${prefix}addon_id", addonId)
    Settings.Global.putString(cr, "${prefix}page_id", pageId)
    Settings.Global.putString(cr, "${prefix}summary_on", summaryOn)
    Settings.Global.putString(cr, "${prefix}summary_off", summaryOff)
    Settings.Global.putString(cr, "${prefix}values", values)
    Settings.Global.putString(cr, "${prefix}labels", labels)
    // Enable the tile component
    setTileComponentEnabled(context, slot, true)
    dynamicTileServiceClass(slot)?.let { TileUtils.requestTileRefresh(context, it) }
}

private fun unbindTileSlot(context: Context, slot: Int) {
    val prefix = "pixel_addon_tile_${slot}_"
    val cr = context.contentResolver
    Settings.Global.putInt(cr, "${prefix}enabled", 0)
    Settings.Global.putString(cr, "${prefix}tile_id", "")
    Settings.Global.putString(cr, "${prefix}key", "")
    Settings.Global.putString(cr, "${prefix}mode", "")
    Settings.Global.putString(cr, "${prefix}label", "")
    Settings.Global.putString(cr, "${prefix}addon_id", "")
    Settings.Global.putString(cr, "${prefix}page_id", "")
    Settings.Global.putString(cr, "${prefix}summary_on", "")
    Settings.Global.putString(cr, "${prefix}summary_off", "")
    Settings.Global.putString(cr, "${prefix}values", "")
    Settings.Global.putString(cr, "${prefix}labels", "")
    // Disable the tile component
    setTileComponentEnabled(context, slot, false)
}

private fun setTileComponentEnabled(context: Context, slot: Int, enabled: Boolean) {
    val num = slot.toString().padStart(2, '0')
    val componentName = android.content.ComponentName(
        context.packageName,
        "org.pixel.customparts.services.DynamicAddonTile$num"
    )
    val newState = if (enabled)
        android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED
    else
        android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED
    try {
        context.packageManager.setComponentEnabledSetting(componentName, newState, android.content.pm.PackageManager.DONT_KILL_APP)
    } catch (_: Throwable) {}
}

private fun requestAddDynamicTile(context: Context, slot: Int, label: String) {
    val tileClass = dynamicTileServiceClass(slot) ?: return
    TileUtils.requestAddTileService(context, tileClass, label, R.drawable.ic_addon_tile)
}

private fun dynamicTileServiceClass(slot: Int): Class<*>? {
    if (slot !in 1..DYNAMIC_ADDON_TILE_COUNT) return null
    val num = slot.toString().padStart(2, '0')
    return try { Class.forName("org.pixel.customparts.services.DynamicAddonTile$num") } catch (_: Throwable) { null }
}

private fun TextStyle.withAddonTextSize(sizeSp: Float): TextStyle {
    return if (sizeSp > 0f) copy(fontSize = sizeSp.sp) else this
}

@Composable
private fun SettingTitleText(
    setting: AddonSettingDef,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    fontWeight: FontWeight? = FontWeight.Medium,
    color: Color = Color.Unspecified,
    maxLines: Int = 1,
    overflow: TextOverflow = TextOverflow.Ellipsis,
    modifier: Modifier = Modifier
) {
    Text(
        setting.title,
        style = style.withAddonTextSize(setting.titleSizeSp),
        fontWeight = fontWeight,
        color = color,
        maxLines = maxLines,
        overflow = overflow,
        modifier = modifier
    )
}

@Composable
private fun SettingDescriptionText(
    setting: AddonSettingDef,
    style: TextStyle = MaterialTheme.typography.bodySmall,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    maxLines: Int = 2,
    overflow: TextOverflow = TextOverflow.Ellipsis,
    modifier: Modifier = Modifier
) {
    Text(
        setting.description,
        style = style.withAddonTextSize(setting.descriptionSizeSp),
        color = color,
        maxLines = maxLines,
        overflow = overflow,
        modifier = modifier
    )
}

// ---------- INT SLIDER ----------

@Composable
private fun IntSliderSettingControl(
    setting: AddonSettingDef,
    addon: AddonUiModel,
    modifier: Modifier,
    onSettingChanged: (AddonSettingDef, Boolean?) -> Unit
) {
    val context = LocalContext.current
    val accent = settingAccent(setting, addon)
    var value by remember {
        mutableIntStateOf(readStoredInt(context, setting, addon.id, addon.jarPath, addon.isSystem, setting.defaultInt))
    }
    var showManualInput by remember { mutableStateOf(false) }

    Column(modifier = modifier
        .fillMaxWidth()
        .clickable { showManualInput = true }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                SettingTitleText(setting)
                if (setting.description.isNotEmpty()) {
                    SettingDescriptionText(setting)
                }
            }
            TextButton(onClick = { showManualInput = true }) {
                Text(
                    "$value${if (setting.unit.isNotEmpty()) " ${setting.unit}" else ""}",
                    fontWeight = FontWeight.Bold,
                    color = accent
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = {
                    value = setting.defaultInt
                    writeStoredInt(context, setting, addon.id, addon.jarPath, addon.isSystem, value)
                    onSettingChanged(setting, null)
                },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(Icons.Rounded.Refresh, dynamicStringResource(R.string.btn_default), Modifier.size(16.dp), tint = accent)
            }
            Spacer(Modifier.width(4.dp))
            Slider(
                value = value.toFloat(),
                onValueChange = { value = it.toInt() },
                valueRange = setting.min..setting.max,
                steps = if (setting.step > 1f) ((setting.max - setting.min) / setting.step).toInt() - 1 else 0,
                onValueChangeFinished = {
                    writeStoredInt(context, setting, addon.id, addon.jarPath, addon.isSystem, value)
                    onSettingChanged(setting, null)
                },
                colors = SliderDefaults.colors(
                    thumbColor = accent,
                    activeTrackColor = accent
                ),
                modifier = Modifier.weight(1f)
            )
        }
    }

    if (showManualInput) {
        ManualIntInputDialog(
            title = setting.title,
            currentValue = value,
            min = setting.min.toInt(),
            max = setting.max.toInt(),
            unit = setting.unit,
            defaultValue = setting.defaultInt,
            onDismiss = { showManualInput = false },
            onConfirm = { newVal ->
                value = newVal
                writeStoredInt(context, setting, addon.id, addon.jarPath, addon.isSystem, newVal)
                onSettingChanged(setting, null)
                showManualInput = false
            }
        )
    }
}

// ---------- FLOAT SLIDER ----------

@Composable
private fun FloatSliderSettingControl(
    setting: AddonSettingDef,
    addon: AddonUiModel,
    modifier: Modifier,
    onSettingChanged: (AddonSettingDef, Boolean?) -> Unit
) {
    val context = LocalContext.current
    val accent = settingAccent(setting, addon)
    var value by remember {
        mutableFloatStateOf(readStoredFloat(context, setting, addon.id, addon.jarPath, addon.isSystem, setting.defaultFloat))
    }
    var showManualInput by remember { mutableStateOf(false) }

    Column(modifier = modifier
        .fillMaxWidth()
        .clickable { showManualInput = true }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                SettingTitleText(setting)
                if (setting.description.isNotEmpty()) {
                    SettingDescriptionText(setting)
                }
            }
            TextButton(onClick = { showManualInput = true }) {
                Text(
                    String.format("%.2f%s", value, if (setting.unit.isNotEmpty()) " ${setting.unit}" else ""),
                    fontWeight = FontWeight.Bold,
                    color = accent
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = {
                    value = setting.defaultFloat
                    writeStoredFloat(context, setting, addon.id, addon.jarPath, addon.isSystem, value)
                    onSettingChanged(setting, null)
                },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(Icons.Rounded.Refresh, dynamicStringResource(R.string.btn_default), Modifier.size(16.dp), tint = accent)
            }
            Spacer(Modifier.width(4.dp))
            Slider(
                value = value,
                onValueChange = { value = it },
                valueRange = setting.min..setting.max,
                onValueChangeFinished = {
                    writeStoredFloat(context, setting, addon.id, addon.jarPath, addon.isSystem, value)
                    onSettingChanged(setting, null)
                },
                colors = SliderDefaults.colors(
                    thumbColor = accent,
                    activeTrackColor = accent
                ),
                modifier = Modifier.weight(1f)
            )
        }
    }

    if (showManualInput) {
        ManualFloatInputDialog(
            title = setting.title,
            currentValue = value,
            min = setting.min,
            max = setting.max,
            unit = setting.unit,
            defaultValue = setting.defaultFloat,
            onDismiss = { showManualInput = false },
            onConfirm = { newVal ->
                value = newVal
                writeStoredFloat(context, setting, addon.id, addon.jarPath, addon.isSystem, newVal)
                onSettingChanged(setting, null)
                showManualInput = false
            }
        )
    }
}

// ---------- STRING INPUT ----------

@Composable
private fun StringSettingControl(
    setting: AddonSettingDef,
    addon: AddonUiModel,
    modifier: Modifier,
    onSettingChanged: (AddonSettingDef, Boolean?) -> Unit
) {
    val context = LocalContext.current
    var value by remember {
        mutableStateOf(readStoredString(context, setting, addon.id, addon.jarPath, addon.isSystem) ?: setting.defaultString)
    }

    Column(modifier = modifier.fillMaxWidth()) {
        SettingTitleText(setting)
        if (setting.description.isNotEmpty()) {
            SettingDescriptionText(setting)
        }
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = value,
            onValueChange = {
                value = it
                writeStoredString(context, setting, addon.id, addon.jarPath, addon.isSystem, it)
                onSettingChanged(setting, null)
            },
            singleLine = true,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodyMedium,
            trailingIcon = {
                if (value != setting.defaultString) {
                    IconButton(onClick = {
                        value = setting.defaultString
                        writeStoredString(context, setting, addon.id, addon.jarPath, addon.isSystem, setting.defaultString)
                        onSettingChanged(setting, null)
                    }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Rounded.Refresh, dynamicStringResource(R.string.btn_default), Modifier.size(16.dp))
                    }
                }
            }
        )
    }
}

// ---------- SELECT (dropdown) ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectSettingControl(
    setting: AddonSettingDef,
    addon: AddonUiModel,
    modifier: Modifier,
    onSettingChanged: (AddonSettingDef, Boolean?) -> Unit
) {
    val context = LocalContext.current
    val options = resolveSelectOptions(setting, addon)
    val currentValue = readStoredString(context, setting, addon.id, addon.jarPath, addon.isSystem) ?: setting.defaultString
    var expanded by remember { mutableStateOf(false) }
    var selectedValue by remember { mutableStateOf(currentValue) }
    val selectedLabel = options.find { it.value == selectedValue }?.label ?: selectedValue

    Column(modifier = modifier.fillMaxWidth()) {
        SettingTitleText(setting)
        if (setting.description.isNotEmpty()) {
            SettingDescriptionText(setting)
        }
        Spacer(Modifier.height(4.dp))

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it }
        ) {
            OutlinedTextField(
                value = selectedLabel,
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodyMedium
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label) },
                        onClick = {
                            selectedValue = option.value
                            writeStoredString(context, setting, addon.id, addon.jarPath, addon.isSystem, option.value)
                            onSettingChanged(setting, null)
                            expanded = false
                        },
                        trailingIcon = {
                            if (option.value == selectedValue) {
                                Icon(Icons.Rounded.Check, null, Modifier.size(18.dp))
                            }
                        }
                    )
                }
            }
        }
    }
}

// ---------- FILE PICKER ----------

// ---------- SELECT BUTTON (ThermalActivity-style) ----------

@Composable
private fun SelectButtonSettingControl(
    setting: AddonSettingDef,
    addon: AddonUiModel,
    modifier: Modifier,
    onSettingChanged: (AddonSettingDef, Boolean?) -> Unit
) {
    val context = LocalContext.current
    val options = resolveSelectOptions(setting, addon)
    val currentValue = readStoredString(context, setting, addon.id, addon.jarPath, addon.isSystem) ?: setting.defaultString
    var selectedValue by remember { mutableStateOf(currentValue) }
    val accent = settingAccent(setting, addon)

    Column(modifier = modifier.fillMaxWidth()) {
        SettingTitleText(setting)
        if (setting.description.isNotEmpty()) {
            SettingDescriptionText(setting)
        }
        Spacer(Modifier.height(8.dp))

        options.forEach { option ->
            val isSelected = option.value == selectedValue
            val rowBackgroundColor by animateColorAsState(
                targetValue = if (isSelected) accent.copy(alpha = 0.15f) else Color.Transparent,
                label = "selectBtnBg"
            )
            val weightVal by animateIntAsState(
                targetValue = if (isSelected) 700 else 400,
                animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
                label = "selectBtnWeight"
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(rowBackgroundColor)
                    .clickable {
                        selectedValue = option.value
                        writeStoredString(context, setting, addon.id, addon.jarPath, addon.isSystem, option.value)
                        onSettingChanged(setting, null)
                    }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = option.label,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight(weightVal)
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )

                AnimatedVisibility(
                    visible = isSelected,
                    enter = scaleIn() + fadeIn(),
                    exit = scaleOut() + fadeOut()
                ) {
                    Icon(
                        Icons.Rounded.CheckCircle,
                        null,
                        tint = accent
                    )
                }
            }
        }
    }
}

// ---------- FILE PICKER (original) ----------

@Composable
private fun FileSettingControl(
    setting: AddonSettingDef,
    addon: AddonUiModel,
    modifier: Modifier,
    onSettingChanged: (AddonSettingDef, Boolean?) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentPath = readStoredString(context, setting, addon.id, addon.jarPath, addon.isSystem) ?: ""
    var filePath by remember { mutableStateOf(currentPath) }
    val notSetLabel = dynamicStringResource(R.string.addon_file_not_set)
    val fileSavedMsg = dynamicStringResource(R.string.addon_file_saved)
    val fileCopyFailedMsg = dynamicStringResource(R.string.addon_file_copy_failed)
    val fileName = if (filePath.isNotEmpty()) File(filePath).name else notSetLabel

    // System addons: writable data in /data/pixelparts/system_addons_data/{id}/
    // User addons: data next to JAR  e.g. /data/pixelparts/addons/my_addon_data/
    val dataDir = addonDataDir(addon.id, addon.jarPath, addon.isSystem)

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                val destPath = withContext(Dispatchers.IO) {
                    copyFileToAddonData(context, uri, dataDir, setting.key)
                }
                if (destPath != null) {
                    filePath = destPath
                    writeStoredString(context, setting, addon.id, addon.jarPath, addon.isSystem, destPath)
                    onSettingChanged(setting, null)
                    Toast.makeText(context, fileSavedMsg, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, fileCopyFailedMsg, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        SettingTitleText(setting)
        if (setting.description.isNotEmpty()) {
            SettingDescriptionText(setting)
        }
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Rounded.InsertDriveFile, null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        fileName,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (filePath.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            FilledTonalIconButton(
                onClick = { filePicker.launch(setting.mimeType) },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(Icons.Rounded.FolderOpen, null, Modifier.size(20.dp))
            }
            if (filePath.isNotEmpty()) {
                Spacer(Modifier.width(4.dp))
                IconButton(
                    onClick = {
                        filePath = ""
                        writeStoredString(context, setting, addon.id, addon.jarPath, addon.isSystem, "")
                        onSettingChanged(setting, null)
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Rounded.Close, null, Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f))
                }
            }
        }
    }
}

// ---------- APP LIST ----------

private const val EXTRA_PICKER_ADDON_ID = "addon_id"
private const val EXTRA_PICKER_ADDON_JAR = "addon_jar"
private const val EXTRA_PICKER_IS_SYSTEM = "is_system"
private const val EXTRA_PICKER_KEY = "setting_key"
private const val EXTRA_PICKER_TITLE = "setting_title"
private const val EXTRA_PICKER_PROVIDER = "setting_provider"
private const val EXTRA_PICKER_STORAGE = "setting_storage"
private const val EXTRA_PICKER_LAUNCHABLE_ONLY = "launchable_only"

@Composable
private fun AppListSettingControl(
    setting: AddonSettingDef,
    addon: AddonUiModel,
    modifier: Modifier,
    onSettingChanged: (AddonSettingDef, Boolean?) -> Unit
) {
    val context = LocalContext.current
    var selected by remember {
        mutableStateOf(readStoredArray(context, setting, addon.id, addon.jarPath, addon.isSystem).toSet())
    }
    var showPicker by remember { mutableStateOf(false) }
    val activityPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        selected = readStoredArray(context, setting, addon.id, addon.jarPath, addon.isSystem).toSet()
        onSettingChanged(setting, null)
    }

    Column(modifier = modifier.fillMaxWidth()) {
        SettingTitleText(setting)
        if (setting.description.isNotEmpty()) {
            SettingDescriptionText(setting)
        }
        Spacer(Modifier.height(6.dp))
        FilledTonalButton(onClick = {
            if (setting.appPickerMode == "activity" || setting.appPickerMode == "screen" || setting.appPickerMode == "activity_launchable" || setting.appPickerMode == "screen_launchable") {
                activityPickerLauncher.launch(addonAppPickerIntent(context, addon, setting))
            } else {
                showPicker = true
            }
        }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Rounded.Apps, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(dynamicStringResource(R.string.addon_apps_selected, selected.size))
        }
        if (selected.isNotEmpty() && setting.showSelected) {
            Spacer(Modifier.height(8.dp))
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                selected.sorted().forEach { pkg ->
                    InputChip(
                        selected = true,
                        onClick = {
                            selected = selected - pkg
                            writeStoredArray(context, setting, addon.id, addon.jarPath, addon.isSystem, selected)
                            onSettingChanged(setting, null)
                        },
                        label = { Text(pkg.substringAfterLast('.'), style = MaterialTheme.typography.labelSmall, maxLines = 1) },
                        trailingIcon = { Icon(Icons.Rounded.Close, null, modifier = Modifier.size(14.dp)) },
                        modifier = Modifier.height(28.dp)
                    )
                }
            }
        }
    }

    if (showPicker) {
        AppPickerDialog(
            defaultTargets = emptySet(),
            selectedPackages = selected,
            onDismiss = { showPicker = false },
            onConfirm = { packages ->
                selected = packages
                writeStoredArray(context, setting, addon.id, addon.jarPath, addon.isSystem, packages)
                onSettingChanged(setting, null)
                showPicker = false
            },
            launchableOnlyDefault = setting.appPickerMode.endsWith("launchable")
        )
    }
}

private fun addonAppPickerIntent(context: Context, addon: AddonUiModel, setting: AddonSettingDef): Intent {
    return Intent(context, AddonAppPickerActivity::class.java)
        .putExtra(EXTRA_PICKER_ADDON_ID, addon.id)
        .putExtra(EXTRA_PICKER_ADDON_JAR, addon.jarPath)
        .putExtra(EXTRA_PICKER_IS_SYSTEM, addon.isSystem)
        .putExtra(EXTRA_PICKER_KEY, setting.key)
        .putExtra(EXTRA_PICKER_TITLE, setting.title)
        .putExtra(EXTRA_PICKER_PROVIDER, setting.provider.name)
        .putExtra(EXTRA_PICKER_STORAGE, setting.storage.name)
        .putExtra(EXTRA_PICKER_LAUNCHABLE_ONLY, setting.appPickerMode.endsWith("launchable"))
}

// ---------- COLOR ----------

@Composable
private fun ColorSettingControl(
    setting: AddonSettingDef,
    addon: AddonUiModel,
    modifier: Modifier,
    onSettingChanged: (AddonSettingDef, Boolean?) -> Unit
) {
    val context = LocalContext.current
    val stored = readStoredString(context, setting, addon.id, addon.jarPath, addon.isSystem) ?: setting.defaultString
    var colorInt by remember { mutableIntStateOf(parseColorValue(stored, setting.colorFormat, setting.allowAlpha)) }
    var showPicker by remember { mutableStateOf(false) }
    val formatted = remember(colorInt, setting.colorFormat) { formatColorValue(colorInt, setting.colorFormat) }

    Column(modifier = modifier.fillMaxWidth()) {
        SettingTitleText(setting)
        if (setting.description.isNotEmpty()) {
            SettingDescriptionText(setting)
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(colorInt))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                    .clickable { showPicker = true }
            )
            Spacer(Modifier.width(10.dp))
            Text(
                formatted,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            FilledTonalButton(onClick = { showPicker = true }) {
                Icon(Icons.Rounded.Palette, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(dynamicStringResource(R.string.addon_color_pick))
            }
        }
    }

    if (showPicker) {
        ColorPickerDialog(
            initialColor = colorInt,
            showAlpha = setting.allowAlpha,
            title = setting.title,
            onColorSelected = { picked ->
                colorInt = picked
                writeStoredString(context, setting, addon.id, addon.jarPath, addon.isSystem, formatColorValue(picked, setting.colorFormat))
                onSettingChanged(setting, null)
                showPicker = false
            },
            onDismissRequest = { showPicker = false }
        )
    }
}

// ---------- GROUP ----------

@Composable
private fun GroupSettingControl(
    setting: AddonSettingDef,
    addon: AddonUiModel,
    modifier: Modifier,
    allSettings: List<AddonSettingDef>,
    inheritedEnabled: Boolean,
    dependencyRevision: Int,
    onSettingChanged: (AddonSettingDef, Boolean?) -> Unit
) {
    // Inline mode: no expandable container, just title + children in place.
    if (setting.groupMode == GroupMode.INLINE) {
        Column(modifier = modifier.fillMaxWidth()) {
            if (setting.title.isNotEmpty()) {
                SettingTitleText(
                    setting,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = parseOptionalColor(setting.accentColor) ?: parseOptionalColor(addon.accentColor) ?: MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            if (setting.description.isNotEmpty()) {
                SettingDescriptionText(
                    setting,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            setting.children.forEach { child ->
                AddonSettingControl(child, addon, Modifier.padding(bottom = 8.dp), allSettings, inheritedEnabled, dependencyRevision, onSettingChanged)
            }
        }
        return
    }

    if (setting.groupMode == GroupMode.IMMERSIVE_EXPAND) {
        var expanded by rememberSaveable(setting.key) { mutableStateOf(setting.defaultExpanded) }
        val containerColor = groupContainerColor(
            setting = setting,
            fallback = MaterialTheme.colorScheme.surfaceContainerHigh,
            fallbackAlpha = 0.68f
        )

        Surface(
            color = containerColor,
            shape = RoundedCornerShape(16.dp),
            modifier = modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expanded = !expanded },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SettingIcon(setting, addon, Modifier.padding(end = 12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        SettingTitleText(
                            setting,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (setting.description.isNotEmpty()) {
                            SettingDescriptionText(setting)
                        }
                    }
                    Icon(
                        if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                AnimatedVisibility(
                    visible = expanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column(modifier = Modifier.padding(top = 12.dp)) {
                        setting.children.forEach { child ->
                            AddonSettingControl(child, addon, Modifier.padding(bottom = 8.dp), allSettings, inheritedEnabled, dependencyRevision, onSettingChanged)
                        }
                    }
                }
            }
        }
        return
    }

    var expanded by remember { mutableStateOf(false) }
    var showDialog by remember { mutableStateOf(false) }
    val openAction = {
        if (setting.groupMode == GroupMode.EXPANDABLE) expanded = !expanded else showDialog = true
    }

    val containerColor = groupContainerColor(
        setting = setting,
        fallback = MaterialTheme.colorScheme.surfaceContainerHigh,
        fallbackAlpha = 0.65f
    )

    Surface(
        color = containerColor,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { openAction() },
                verticalAlignment = Alignment.CenterVertically
            ) {
                SettingIcon(setting, addon, Modifier.padding(end = 8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    SettingTitleText(setting, fontWeight = FontWeight.SemiBold)
                    if (setting.description.isNotEmpty()) {
                        SettingDescriptionText(setting)
                    }
                }
                Icon(
                    if (setting.groupMode == GroupMode.EXPANDABLE && expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ChevronRight,
                    null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            AnimatedVisibility(
                visible = setting.groupMode == GroupMode.EXPANDABLE && expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    setting.children.forEach { child ->
                        AddonSettingControl(child, addon, Modifier.padding(bottom = 8.dp), allSettings, inheritedEnabled, dependencyRevision, onSettingChanged)
                    }
                }
            }
        }
    }

    if (showDialog) {
        GroupDialog(setting = setting, addon = addon, allSettings = allSettings, inheritedEnabled = inheritedEnabled, dependencyRevision = dependencyRevision, onDismiss = { showDialog = false }, onSettingChanged = onSettingChanged)
    }
}

@Composable
private fun GroupDialog(
    setting: AddonSettingDef,
    addon: AddonUiModel,
    allSettings: List<AddonSettingDef>,
    inheritedEnabled: Boolean,
    dependencyRevision: Int,
    onDismiss: () -> Unit,
    onSettingChanged: (AddonSettingDef, Boolean?) -> Unit
) {
    val fullScreen = setting.groupMode == GroupMode.FULLSCREEN
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = !fullScreen)) {
        Surface(
            shape = if (fullScreen) RoundedCornerShape(0.dp) else RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = if (fullScreen) Modifier.fillMaxSize() else Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (fullScreen) Modifier.fillMaxHeight() else Modifier)
                    .padding(20.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (setting.closeButtonPosition == CloseButtonPosition.START) {
                        IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, dynamicStringResource(R.string.btn_close)) }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        SettingTitleText(setting, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = Int.MAX_VALUE, overflow = TextOverflow.Clip)
                        if (setting.description.isNotEmpty()) {
                            SettingDescriptionText(setting, maxLines = Int.MAX_VALUE, overflow = TextOverflow.Clip)
                        }
                    }
                    if (setting.closeButtonPosition == CloseButtonPosition.END) {
                        IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, dynamicStringResource(R.string.btn_close)) }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Column(
                    modifier = Modifier
                        .weight(1f, fill = fullScreen)
                        .verticalScroll(rememberScrollState())
                ) {
                    setting.children.forEach { child ->
                        AddonSettingControl(child, addon, Modifier.padding(bottom = 8.dp), allSettings, inheritedEnabled, dependencyRevision, onSettingChanged)
                    }
                }
            }
        }
    }
}

// ---------- VISUAL ----------

@Composable
private fun VisualSettingControl(setting: AddonSettingDef, addon: AddonUiModel, modifier: Modifier) {
    when (setting.visualType) {
        VisualType.TEXT -> {
            Column(modifier = modifier.fillMaxWidth()) {
                if (setting.title.isNotEmpty()) {
                    SettingTitleText(setting, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = parseOptionalColor(setting.accentColor) ?: MaterialTheme.colorScheme.primary)
                }
                if (setting.description.isNotEmpty()) {
                    SettingDescriptionText(setting, maxLines = Int.MAX_VALUE, overflow = TextOverflow.Clip)
                }
            }
        }
        VisualType.IMAGE -> {
            val imageBitmap = remember(addon.jarPath, setting.imagePath) {
                extractBitmapFromJar(File(addon.jarPath), setting.imagePath.ifEmpty { setting.defaultString })?.asImageBitmap()
            }
            if (imageBitmap != null) {
                Image(
                    bitmap = imageBitmap,
                    contentDescription = setting.title.takeIf { it.isNotEmpty() },
                    contentScale = ContentScale.Crop,
                    modifier = modifier
                        .fillMaxWidth()
                        .height(setting.sizeDp.coerceAtLeast(48).dp)
                        .clip(RoundedCornerShape(12.dp))
                )
            }
        }
        VisualType.SPACER -> Spacer(modifier.height(setting.sizeDp.dp))
        VisualType.DIVIDER -> HorizontalDivider(
            modifier = modifier.padding(vertical = setting.sizeDp.dp / 2),
            thickness = setting.thicknessDp.dp,
            color = parseOptionalColor(setting.color) ?: MaterialTheme.colorScheme.outlineVariant
        )
        VisualType.DASHED_DIVIDER -> DashedDivider(setting = setting, modifier = modifier)
        VisualType.WARNING -> {
            val warningColor = parseOptionalColor(setting.color)
            val containerColor = warningColor?.copy(alpha = 0.15f)
                ?: MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f)
            val contentColor = warningColor ?: MaterialTheme.colorScheme.onErrorContainer
            ExpandableWarningCard(
                title = setting.title,
                text = setting.description,
                modifier = modifier,
                containerColor = containerColor,
                contentColor = contentColor,
                titleStyle = MaterialTheme.typography.titleMedium.withAddonTextSize(setting.titleSizeSp),
                textStyle = MaterialTheme.typography.bodyMedium.withAddonTextSize(setting.descriptionSizeSp)
            )
        }
    }
}

@Composable
private fun DashedDivider(setting: AddonSettingDef, modifier: Modifier) {
    val color = parseOptionalColor(setting.color) ?: MaterialTheme.colorScheme.outlineVariant
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height((setting.sizeDp + setting.thicknessDp).dp)
    ) {
        val stroke = setting.thicknessDp.dp.toPx()
        val dash = 10.dp.toPx()
        val gap = 6.dp.toPx()
        val y = size.height / 2f
        var start = 0f
        while (start < size.width) {
            drawLine(
                color = color,
                start = androidx.compose.ui.geometry.Offset(start, y),
                end = androidx.compose.ui.geometry.Offset((start + dash).coerceAtMost(size.width), y),
                strokeWidth = stroke
            )
            start += dash + gap
        }
    }
}

internal fun parseOptionalColor(value: String): Color? {
    if (value.isBlank()) return null
    return try { Color(android.graphics.Color.parseColor(value.trim())) } catch (_: Throwable) { null }
}

@Composable
private fun settingAccent(setting: AddonSettingDef, addon: AddonUiModel): Color {
    return parseOptionalColor(setting.accentColor)
        ?: parseOptionalColor(addon.accentColor)
        ?: MaterialTheme.colorScheme.primary
}

private fun groupContainerColor(setting: AddonSettingDef, fallback: Color, fallbackAlpha: Float): Color {
    val base = parseOptionalColor(setting.color) ?: fallback.copy(alpha = fallbackAlpha)
    return if (setting.surfaceAlpha >= 0f) base.copy(alpha = setting.surfaceAlpha) else base
}

/**
 * Renders an icon for a setting if configured.
 * Returns true if an icon was rendered, false otherwise.
 */
@Composable
private fun SettingIcon(setting: AddonSettingDef, addon: AddonUiModel, modifier: Modifier = Modifier): Boolean {
    val iconName = setting.icon.trim()
    if (iconName.isEmpty()) return false

    val iconSizeDp = setting.iconSize.dp
    val accent = settingAccent(setting, addon)
    val iconType = setting.iconType.trim().lowercase()

    val shapeModifier = when (setting.iconShape) {
        "circle" -> Modifier
            .size(iconSizeDp + 12.dp)
            .clip(CircleShape)
            .background(accent.copy(alpha = 0.12f))
        "rounded" -> Modifier
            .size(iconSizeDp + 12.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(accent.copy(alpha = 0.12f))
        else -> Modifier // "none" or unsupported
    }

    val hasShape = setting.iconShape != "none" && setting.iconShape.isNotEmpty()

    if (iconType == "file" || (iconType.isEmpty() && looksLikeBitmapPath(iconName))) {
        val iconBitmap = remember(addon.jarPath, iconName) {
            extractBitmapFromJar(java.io.File(addon.jarPath), iconName)?.asImageBitmap()
        }
        if (iconBitmap != null) {
            if (hasShape) {
                Box(modifier = modifier.then(shapeModifier), contentAlignment = Alignment.Center) {
                    Image(bitmap = iconBitmap, contentDescription = null, modifier = Modifier.size(iconSizeDp))
                }
            } else {
                Image(bitmap = iconBitmap, contentDescription = null, modifier = modifier.size(iconSizeDp))
            }
            return true
        }
    }

    if (iconType != "file") {
        val imageVector = rememberMaterialIcon(iconName)
        if (imageVector != null) {
            if (hasShape) {
                Box(modifier = modifier.then(shapeModifier), contentAlignment = Alignment.Center) {
                    Icon(imageVector = imageVector, contentDescription = null, modifier = Modifier.size(iconSizeDp), tint = accent)
                }
            } else {
                Icon(imageVector = imageVector, contentDescription = null, modifier = modifier.size(iconSizeDp), tint = accent)
            }
            return true
        }
    }
    return false
}

/**
 * Resolves a Material Icons name to an ImageVector.
 * Supports Rounded, Filled, Outlined variants via prefix (e.g. "Rounded.Settings", "Settings").
 * Falls back to Icons.Rounded, then Icons.Filled.
 */
@Composable
internal fun rememberMaterialIcon(name: String): androidx.compose.ui.graphics.vector.ImageVector? {
    return remember(name) {
        resolveMaterialIcon(name.trim())
    }
}

private data class MaterialIconVariant(
    val packageName: String,
    val receiver: Any
)

private fun resolveMaterialIcon(name: String): androidx.compose.ui.graphics.vector.ImageVector? {
    if (name.isBlank()) return null

    val parts = name.split('.').filter { it.isNotBlank() }
    val autoMirrored = parts.firstOrNull()?.equals("AutoMirrored", ignoreCase = true) == true
    val style = when {
        autoMirrored && parts.size >= 2 -> parts[1].lowercase()
        parts.size >= 2 -> parts[0].lowercase()
        else -> ""
    }
    val iconName = when {
        autoMirrored && parts.size >= 3 -> parts.drop(2).joinToString("")
        parts.size >= 2 -> parts.drop(1).joinToString("")
        else -> parts.firstOrNull().orEmpty()
    }
    if (iconName.isBlank()) return null

    for (variant in materialIconVariants(style, autoMirrored)) {
        val result = resolveIconFromKtClass(variant.packageName, iconName, variant.receiver)
        if (result != null) return result
    }

    return fallbackMaterialIcon(iconName)
}

private fun fallbackMaterialIcon(iconName: String): androidx.compose.ui.graphics.vector.ImageVector? {
    return when (iconName.trim().lowercase(Locale.ROOT)) {
        "apps", "circle", "dashboard", "gridview", "menu", "rocket", "viewcarousel", "viewmodule" -> Icons.Rounded.Apps
        "arrowdropdowncircle", "radiobuttonchecked", "linearscale", "speed", "tune" -> Icons.Rounded.Tune
        "batterychargingfull", "bluetooth", "brightnesshigh", "notifications", "powersettingsnew", "settings", "textfields", "toggleon" -> Icons.Rounded.Settings
        "checkbox", "check", "checkcircle", "looksone", "lookstwo" -> Icons.Rounded.CheckCircle
        "camera", "cameraalt", "flashon", "photocamera" -> Icons.Rounded.Tune
        "colorlens", "palette", "diamond" -> Icons.Rounded.Palette
        "delete", "deleteoutline" -> Icons.Rounded.Delete
        "eco", "favorite", "star" -> Icons.Rounded.Favorite
        "fileopen" -> Icons.Rounded.FileOpen
        "home" -> Icons.Rounded.Home
        "locationon", "wifi" -> Icons.Rounded.NetworkCell
        "search" -> Icons.Rounded.Search
        "security", "shield" -> Icons.Rounded.Security
        "volumeup" -> Icons.Rounded.Tune
        else -> Icons.Rounded.Extension
    }
}

private fun materialIconVariants(style: String, autoMirrored: Boolean): List<MaterialIconVariant> {
    val rounded = MaterialIconVariant("androidx.compose.material.icons.rounded", Icons.Rounded)
    val filled = MaterialIconVariant("androidx.compose.material.icons.filled", Icons.Filled)
    val outlined = MaterialIconVariant("androidx.compose.material.icons.outlined", Icons.Outlined)
    val autoRounded = MaterialIconVariant("androidx.compose.material.icons.automirrored.rounded", Icons.AutoMirrored.Rounded)
    val autoFilled = MaterialIconVariant("androidx.compose.material.icons.automirrored.filled", Icons.AutoMirrored.Filled)
    val autoOutlined = MaterialIconVariant("androidx.compose.material.icons.automirrored.outlined", Icons.AutoMirrored.Outlined)

    return when {
        autoMirrored && style == "filled" -> listOf(autoFilled, autoRounded, autoOutlined)
        autoMirrored && style == "outlined" -> listOf(autoOutlined, autoRounded, autoFilled)
        autoMirrored -> listOf(autoRounded, autoFilled, autoOutlined)
        style == "filled" -> listOf(filled, rounded, outlined, autoFilled, autoRounded, autoOutlined)
        style == "outlined" -> listOf(outlined, rounded, filled, autoOutlined, autoRounded, autoFilled)
        style == "rounded" -> listOf(rounded, filled, outlined, autoRounded, autoFilled, autoOutlined)
        else -> listOf(rounded, filled, outlined, autoRounded, autoFilled, autoOutlined)
    }
}

private fun resolveIconFromKtClass(packageName: String, iconName: String, receiver: Any): androidx.compose.ui.graphics.vector.ImageVector? {
    // Compose icons compile as: package.IconNameKt.getIconName(Icons.Rounded)
    val className = "$packageName.${iconName}Kt"
    return try {
        val cls = Class.forName(className)
        val method = cls.methods.firstOrNull { m ->
            m.name == "get$iconName" && m.parameterCount == 1
        }
        method?.invoke(null, receiver) as? androidx.compose.ui.graphics.vector.ImageVector
    } catch (_: Throwable) {
        // Some icons have different class names (e.g. _Settings or numbers)
        try {
            val altClassName = "$packageName._${iconName}Kt"
            val cls = Class.forName(altClassName)
            val method = cls.methods.firstOrNull { m ->
                m.name == "get$iconName" && m.parameterCount == 1
            }
            method?.invoke(null, receiver) as? androidx.compose.ui.graphics.vector.ImageVector
        } catch (_: Throwable) {
            null
        }
    }
}

private fun getIconField(cls: Class<*>, instance: Any, name: String): androidx.compose.ui.graphics.vector.ImageVector? {
    // Legacy fallback - try direct property access on companion
    return try {
        val method = cls.getDeclaredMethod("get${name}")
        method.isAccessible = true
        method.invoke(instance) as? androidx.compose.ui.graphics.vector.ImageVector
    } catch (_: Throwable) {
        null
    }
}

/**
 * Loads select options from an external JSON file in the addon data directory.
 * Expected JSON format: array of objects with "name"/"label" and "value" fields.
 * e.g. [{"name": "Option A", "value": "a"}, {"name": "Option B", "value": "b"}]
 */
private fun loadOptionsFromFile(addonId: String, path: String): List<SelectOption> {
    if (path.isBlank()) return emptyList()
    val file = java.io.File("$ADDON_DIR/$addonId/$path")
    if (!file.exists()) {
        // Also try in /data/pixelparts/addons/<path> directly
        val altFile = java.io.File("$ADDON_DIR/$path")
        if (!altFile.exists()) return emptyList()
        return parseOptionsJson(altFile)
    }
    return parseOptionsJson(file)
}

private fun parseOptionsJson(file: java.io.File): List<SelectOption> {
    return try {
        val text = file.readText(Charsets.UTF_8)
        val arr = org.json.JSONArray(text)
        val result = mutableListOf<SelectOption>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i)
            if (obj != null) {
                val value = obj.optString("value", obj.optString("id", ""))
                val label = obj.optString("name", obj.optString("label", obj.optString("title", value)))
                if (value.isNotEmpty()) result.add(SelectOption(value, label))
            } else {
                val s = arr.optString(i)
                if (s.isNotEmpty()) result.add(SelectOption(s, s))
            }
        }
        result
    } catch (_: Throwable) { emptyList() }
}

/**
 * Resolves options for a select setting: from inline options or from external file.
 */
@Composable
private fun resolveSelectOptions(setting: AddonSettingDef, addon: AddonUiModel): List<SelectOption> {
    return if (setting.optionsSource == "file" && setting.optionsPath.isNotEmpty()) {
        val fileOptions = remember(addon.id, setting.optionsPath) {
            loadOptionsFromFile(addon.id, setting.optionsPath)
        }
        if (fileOptions.isNotEmpty()) fileOptions else setting.options
    } else {
        setting.options
    }
}

private fun parseColorValue(value: String, format: ColorOutputFormat, allowAlpha: Boolean): Int {
    if (value.isBlank()) return if (allowAlpha) 0xFFFFFFFF.toInt() else 0xFF2196F3.toInt()
    return try {
        val trimmed = value.trim()
        if (format == ColorOutputFormat.INT) {
            trimmed.toIntOrNull() ?: trimmed.toLongOrNull()?.toInt() ?: 0
        } else if (trimmed.startsWith("#")) {
            android.graphics.Color.parseColor(trimmed)
        } else {
            val parts = value.split(',').mapNotNull { it.trim().toIntOrNull()?.coerceIn(0, 255) }
            val r = parts.getOrElse(0) { 33 }
            val g = parts.getOrElse(1) { 150 }
            val b = parts.getOrElse(2) { 243 }
            val a = if (format == ColorOutputFormat.RGBA_CSV || allowAlpha) parts.getOrElse(3) { 255 } else 255
            android.graphics.Color.argb(a, r, g, b)
        }
    } catch (_: Throwable) {
        if (allowAlpha) 0xFFFFFFFF.toInt() else 0xFF2196F3.toInt()
    }
}

private fun formatColorValue(color: Int, format: ColorOutputFormat): String {
    val a = android.graphics.Color.alpha(color)
    val r = android.graphics.Color.red(color)
    val g = android.graphics.Color.green(color)
    val b = android.graphics.Color.blue(color)
    return when (format) {
        ColorOutputFormat.HEX_ARGB -> String.format("#%02X%02X%02X%02X", a, r, g, b)
        ColorOutputFormat.RGB_CSV -> "$r,$g,$b"
        ColorOutputFormat.RGBA_CSV -> "$r,$g,$b,$a"
        ColorOutputFormat.INT -> color.toString()
        ColorOutputFormat.HEX_RGB -> String.format("#%02X%02X%02X", r, g, b)
    }
}

@Composable
private fun AddonSettingsActions(
    addon: AddonUiModel,
    onImport: () -> Unit,
    onExport: () -> Unit,
    onRefresh: () -> Unit
) {
    var showExportDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(onClick = onImport, modifier = Modifier.weight(1f)) {
                Icon(Icons.Rounded.FileOpen, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(dynamicStringResource(R.string.addon_settings_import))
            }
            OutlinedButton(onClick = { showExportDialog = true }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Rounded.SaveAlt, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(dynamicStringResource(R.string.addon_settings_export))
            }
        }
        if (addon.updateUrl.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            AddonUpdateButton(addon = addon, onRefresh = onRefresh)
        }
    }

    if (showExportDialog) {
        ExportChoiceDialog(
            onDismiss = { showExportDialog = false },
            onExportSettings = {
                showExportDialog = false
                onExport()
            },
            onExportModule = {
                showExportDialog = false
                // Copy JAR to shared location via SAF
                // For now, export settings (module export requires separate SAF launcher)
                onExport()
            }
        )
    }
}

@Composable
private fun ExportChoiceDialog(
    onDismiss: () -> Unit,
    onExportSettings: () -> Unit,
    onExportModule: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    dynamicStringResource(R.string.addon_settings_export),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(16.dp))

                // Export settings JSON
                OutlinedButton(
                    onClick = onExportSettings,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.SaveAlt, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(dynamicStringResource(R.string.addon_export_settings_title), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text(dynamicStringResource(R.string.addon_export_settings_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Export module JAR
                OutlinedButton(
                    onClick = onExportModule,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.Extension, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(dynamicStringResource(R.string.addon_export_module_title), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text(dynamicStringResource(R.string.addon_export_module_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Spacer(Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text(dynamicStringResource(R.string.addon_btn_cancel)) }
                }
            }
        }
    }
}

@Composable
private fun AddonUpdateButton(addon: AddonUiModel, onRefresh: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var updating by remember { mutableStateOf(false) }
    var updateInfo by remember { mutableStateOf<AddonUpdateInfo?>(null) }
    var statusText by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxWidth()) {
        FilledTonalButton(
            enabled = !checking && !updating,
            onClick = {
                val currentUpdate = updateInfo
                if (currentUpdate == null) {
                    checking = true
                    statusText = ""
                    scope.launch {
                        val info = withContext(Dispatchers.IO) { checkAddonUpdate(addon) }
                        updateInfo = info
                        statusText = if (info == null) context.getString(R.string.addon_update_none) else context.getString(R.string.addon_update_available, info.version)
                        checking = false
                    }
                } else {
                    updating = true
                    statusText = ""
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) { downloadAddonUpdate(context, addon, currentUpdate) }
                        statusText = context.getString(if (ok) R.string.addon_update_installed else R.string.addon_update_failed)
                        updating = false
                        if (ok) onRefresh()
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            if (checking || updating) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Icon(if (updateInfo == null) Icons.Rounded.Update else Icons.Rounded.Download, null, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text(
                when {
                    updating -> dynamicStringResource(R.string.addon_update_installing)
                    checking -> dynamicStringResource(R.string.addon_update_checking)
                    updateInfo != null -> dynamicStringResource(R.string.addon_update_install)
                    else -> dynamicStringResource(R.string.addon_update_check)
                }
            )
        }
        if (statusText.isNotEmpty()) {
            Text(statusText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
        }
        updateInfo?.let { info ->
            if (info.changelog.isNotBlank()) {
                Text(info.changelog, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            }
            if (info.extraInfo.isNotBlank()) {
                Text(info.extraInfo, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f), modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}

private fun exportAddonSettings(context: Context, uri: Uri, addon: AddonUiModel): Boolean {
    return try {
        val values = JSONObject()
        writeSettingsToJson(context, addon, addon.settings, values)
        val root = JSONObject()
            .put("id", addon.id)
            .put("version", addon.version)
            .put("values", values)
        context.contentResolver.openOutputStream(uri)?.use { output ->
            output.write(root.toString(2).toByteArray(Charsets.UTF_8))
        } ?: return false
        true
    } catch (t: Throwable) {
        Log.e(TAG, "exportAddonSettings failed", t)
        false
    }
}

private fun importAddonSettings(context: Context, uri: Uri, addon: AddonUiModel): Boolean {
    return try {
        val text = context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: return false
        val root = JSONObject(text)
        val values = root.optJSONObject("values") ?: root
        readSettingsFromJson(context, addon, addon.settings, values)
        true
    } catch (t: Throwable) {
        Log.e(TAG, "importAddonSettings failed", t)
        false
    }
}

private fun writeSettingsToJson(context: Context, addon: AddonUiModel, settings: List<AddonSettingDef>, values: JSONObject) {
    settings.forEach { setting ->
        when (setting.type) {
            SettingType.GROUP -> writeSettingsToJson(context, addon, setting.children, values)
            SettingType.VISUAL, SettingType.COMMAND_BUTTON -> Unit
            SettingType.APP_LIST -> {
                val arr = JSONArray()
                readStoredArray(context, setting, addon.id, addon.jarPath, addon.isSystem).forEach { arr.put(it) }
                values.put(setting.key, arr)
            }
            SettingType.INT, SettingType.TOGGLE, SettingType.SWITCH, SettingType.CHECKBOX -> values.put(setting.key, readStoredInt(context, setting, addon.id, addon.jarPath, addon.isSystem, setting.defaultInt))
            SettingType.FLOAT -> values.put(setting.key, readStoredFloat(context, setting, addon.id, addon.jarPath, addon.isSystem, setting.defaultFloat))
            else -> values.put(setting.key, readStoredString(context, setting, addon.id, addon.jarPath, addon.isSystem) ?: setting.defaultString)
        }
    }
}

private fun readSettingsFromJson(context: Context, addon: AddonUiModel, settings: List<AddonSettingDef>, values: JSONObject) {
    settings.forEach { setting ->
        when (setting.type) {
            SettingType.GROUP -> readSettingsFromJson(context, addon, setting.children, values)
            SettingType.VISUAL, SettingType.COMMAND_BUTTON -> Unit
            SettingType.APP_LIST -> {
                val arr = values.optJSONArray(setting.key) ?: return@forEach
                writeStoredArray(context, setting, addon.id, addon.jarPath, addon.isSystem, optStringList(arr))
            }
            SettingType.INT, SettingType.TOGGLE, SettingType.SWITCH, SettingType.CHECKBOX -> if (values.has(setting.key)) {
                writeStoredInt(context, setting, addon.id, addon.jarPath, addon.isSystem, values.optInt(setting.key, setting.defaultInt))
            }
            SettingType.FLOAT -> if (values.has(setting.key)) {
                writeStoredFloat(context, setting, addon.id, addon.jarPath, addon.isSystem, values.optDouble(setting.key, setting.defaultFloat.toDouble()).toFloat())
            }
            else -> if (values.has(setting.key)) {
                writeStoredString(context, setting, addon.id, addon.jarPath, addon.isSystem, values.optString(setting.key, setting.defaultString))
            }
        }
    }
}

private fun checkAddonUpdate(addon: AddonUiModel): AddonUpdateInfo? {
    return try {
        val text = URL(addon.updateUrl).openStream().bufferedReader(Charsets.UTF_8).use { it.readText() }
        val json = JSONObject(text)
        val version = json.optString("version", "")
        val downloadUrl = json.optString("downloadUrl", json.optString("url", ""))
        if (version.isBlank() || downloadUrl.isBlank()) return null
        if (compareVersions(version, addon.version) <= 0) return null
        AddonUpdateInfo(
            version = version,
            downloadUrl = downloadUrl,
            changelog = json.optString("changelog", ""),
            extraInfo = json.optString("info", json.optString("extra", ""))
        )
    } catch (t: Throwable) {
        Log.e(TAG, "checkAddonUpdate failed", t)
        null
    }
}

private fun downloadAddonUpdate(context: Context, addon: AddonUiModel, info: AddonUpdateInfo): Boolean {
    return try {
        val dir = File(ADDON_DIR)
        if (!dir.exists() && !dir.mkdirs()) return false
        val tmp = File(dir, "${sanitizeFileSegment(addon.id)}_update_tmp.jar")
        URL(info.downloadUrl).openStream().use { input ->
            FileOutputStream(tmp).use { output -> input.copyTo(output) }
        }
        val desc = readDescriptor(tmp) ?: run { tmp.delete(); return false }
        val downloadedId = desc.optString("id", desc.optString("entryClass", tmp.nameWithoutExtension))
        if (downloadedId != addon.id) {
            tmp.delete()
            return false
        }
        val target = File(dir, "${sanitizeFileSegment(addon.id)}.jar")
        if (target.exists()) target.delete()
        if (!tmp.renameTo(target)) {
            tmp.copyTo(target, overwrite = true)
            tmp.delete()
        }
        target.setReadable(true, false)
        true
    } catch (t: Throwable) {
        Log.e(TAG, "downloadAddonUpdate failed", t)
        false
    }
}

private fun compareVersions(left: String, right: String): Int {
    val leftParts = left.split('.', '-', '_').map { it.toIntOrNull() ?: 0 }
    val rightParts = right.split('.', '-', '_').map { it.toIntOrNull() ?: 0 }
    val count = maxOf(leftParts.size, rightParts.size)
    for (index in 0 until count) {
        val l = leftParts.getOrElse(index) { 0 }
        val r = rightParts.getOrElse(index) { 0 }
        if (l != r) return l.compareTo(r)
    }
    return 0
}

/** Copy a picker URI into the addon's data directory. Returns absolute path or null. */
private fun copyFileToAddonData(context: Context, uri: Uri, dataDir: File, settingKey: String): String? {
    return try {
        if (!dataDir.exists()) dataDir.mkdirs()

        // Determine file name
        var fileName = "${sanitizeFileSegment(settingKey)}_${System.currentTimeMillis()}"
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex >= 0) {
                fileName = sanitizeFileSegment(cursor.getString(nameIndex).orEmpty())
            }
        }

        val target = File(dataDir, fileName)

        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(target).use { output -> input.copyTo(output) }
        }
        if (!target.exists() || target.length() == 0L) {
            Log.e(TAG, "copyFileToAddonData: copy failed")
            return null
        }
        target.setReadable(true, false)

        target.absolutePath
    } catch (t: Throwable) {
        Log.e(TAG, "copyFileToAddonData failed", t)
        null
    }
}

// ---------- Manual input dialogs ----------

@Composable
private fun ManualIntInputDialog(
    title: String,
    currentValue: Int,
    min: Int,
    max: Int,
    unit: String,
    defaultValue: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var text by remember { mutableStateOf(currentValue.toString()) }
    var isError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(dynamicStringResource(R.string.addon_range_format, min.toString(), max.toString()), style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = {
                        text = it
                        val n = it.toIntOrNull()
                        isError = n == null || n < min || n > max
                    },
                    isError = isError,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    suffix = { if (unit.isNotEmpty()) Text(unit) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = {
                    onConfirm(defaultValue)
                }) { Text(dynamicStringResource(R.string.btn_default)) }
                Button(onClick = {
                    val n = text.toIntOrNull()
                    if (n != null && n in min..max) onConfirm(n)
                    else isError = true
                }) { Text(dynamicStringResource(R.string.btn_apply)) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(dynamicStringResource(R.string.addon_btn_cancel)) } },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    )
}

@Composable
private fun ManualFloatInputDialog(
    title: String,
    currentValue: Float,
    min: Float,
    max: Float,
    unit: String,
    defaultValue: Float,
    onDismiss: () -> Unit,
    onConfirm: (Float) -> Unit
) {
    var text by remember { mutableStateOf(currentValue.toString()) }
    var isError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(dynamicStringResource(R.string.addon_range_format, min.toString(), max.toString()), style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = {
                        text = it
                        val n = it.toFloatOrNull()
                        isError = n == null || n < min || n > max
                    },
                    isError = isError,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    suffix = { if (unit.isNotEmpty()) Text(unit) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = {
                    onConfirm(defaultValue)
                }) { Text(dynamicStringResource(R.string.btn_default)) }
                Button(onClick = {
                    val n = text.toFloatOrNull()
                    if (n != null && n >= min && n <= max) onConfirm(n)
                    else isError = true
                }) { Text(dynamicStringResource(R.string.btn_apply)) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(dynamicStringResource(R.string.addon_btn_cancel)) } },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    )
}

// =====================================================================
// Active Apps Section — expandable per-app module lists
// =====================================================================

@Composable
private fun ActiveAppsSection(
    activeAppsMap: Map<String, List<AddonUiModel>>,
    onAddonClick: (String) -> Unit,
    onDisableAddon: (String) -> Unit
) {
    val context = LocalContext.current
    val pm = context.packageManager
    var expanded by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Rounded.Apps, null,
                    modifier = Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        dynamicStringResource(R.string.addon_active_apps_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        dynamicStringResource(R.string.addon_active_apps_desc, activeAppsMap.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    for ((pkg, addons) in activeAppsMap.entries.sortedBy { it.key }) {
                        ActiveAppRow(
                            packageName = pkg,
                            pm = pm,
                            addons = addons,
                            onAddonClick = onAddonClick,
                            onDisableAddon = onDisableAddon
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActiveAppRow(
    packageName: String,
    pm: PackageManager,
    addons: List<AddonUiModel>,
    onAddonClick: (String) -> Unit,
    onDisableAddon: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    val appLabel = remember(packageName) {
        try {
            val ai = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(ai).toString()
        } catch (_: Throwable) { packageName }
    }
    val appIcon = remember(packageName) {
        try {
            pm.getApplicationIcon(packageName)
        } catch (_: Throwable) { null }
    }

    Column(modifier = Modifier.padding(vertical = 2.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .clickable { expanded = !expanded }
                .padding(vertical = 8.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // App icon
            if (appIcon != null) {
                val bitmap = remember(packageName) {
                    try { appIcon.toBitmap(width = 64, height = 64).asImageBitmap() }
                    catch (_: Throwable) { null }
                }
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = null,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                } else {
                    AppPlaceholderIcon()
                }
            } else {
                AppPlaceholderIcon()
            }

            Spacer(Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    appLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    dynamicStringResource(R.string.addon_active_apps_modules, addons.size),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Icon(
                if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(modifier = Modifier.padding(start = 38.dp, bottom = 4.dp)) {
                for (addon in addons) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onAddonClick(addon.id) }
                            .padding(vertical = 8.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Rounded.Extension, null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            addon.name,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            "v${addon.version}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                        Spacer(Modifier.width(6.dp))
                        IconButton(
                            onClick = { onDisableAddon(addon.id) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Close, null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppPlaceholderIcon() {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Rounded.Android, null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// =====================================================================
// Empty state
// =====================================================================

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
    )
}

@Composable
private fun EmptySectionCard(title: String, description: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Outlined.Extension, null,
                modifier = Modifier.size(36.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

// =====================================================================
// Detail Dialog — Addon settings + App Picker
// =====================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddonDetailDialog(
    addon: AddonUiModel,
    onDismiss: () -> Unit,
    onSave: (AddonUiModel) -> Unit
) {
    var enabled by remember { mutableStateOf(addon.enabled) }
    var scopeMode by remember { mutableIntStateOf(addon.scopeMode) }
    var customTargets by remember { mutableStateOf(addon.customTargets) }
    var showAppPicker by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = true)
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp)
            ) {
                // Header
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (addon.iconBitmap != null) {
                        val iconImageBitmap = remember(addon.id) { addon.iconBitmap.asImageBitmap() }
                        Image(
                            bitmap = iconImageBitmap,
                            contentDescription = null,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                        )
                    } else {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.Extension, null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            addon.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "${addon.author} • v${addon.version}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // ID and entryClass info
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "ID: ",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            addon.id,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (addon.entryClass.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Class: ",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                addon.entryClass,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                if (addon.description.isNotEmpty()) {
                    Text(
                        addon.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

                // Scope mode
                Text(
                    dynamicStringResource(R.string.addon_target_scope),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(8.dp))

                val scopeLabels = listOf(
                    dynamicStringResource(R.string.addon_scope_default),
                    dynamicStringResource(R.string.addon_scope_custom),
                    dynamicStringResource(R.string.addon_scope_merge)
                )
                scopeLabels.forEachIndexed { index, label ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { scopeMode = index }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = scopeMode == index,
                            onClick = { scopeMode = index }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(label, style = MaterialTheme.typography.bodyMedium)
                    }
                }

                // Default targets (read-only)
                if (addon.defaultTargets.isNotEmpty() && (scopeMode == 0 || scopeMode == 2)) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        dynamicStringResource(R.string.addon_targets_default),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    for (pkg in addon.defaultTargets) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Rounded.Apps, null,
                                Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                pkg,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.weight(1f))
                            if (pkg in BUILTIN_WHITELIST) {
                                Text(
                                    dynamicStringResource(R.string.addon_builtin_badge),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                // Custom targets — "Select apps" button + chips
                if (scopeMode == 1 || scopeMode == 2) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        dynamicStringResource(R.string.addon_targets_custom),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (customTargets.isNotEmpty()) {
                        Text(
                            dynamicStringResource(R.string.addon_apps_selected, customTargets.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    FilledTonalButton(
                        onClick = { showAppPicker = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Rounded.Checklist, null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(dynamicStringResource(R.string.addon_select_apps))
                    }

                    // Chips for selected custom targets
                    if (customTargets.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        @OptIn(ExperimentalLayoutApi::class)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            for (pkg in customTargets.sorted()) {
                                InputChip(
                                    selected = true,
                                    onClick = { customTargets = customTargets - pkg },
                                    label = {
                                        Text(
                                            pkg.substringAfterLast("."),
                                            style = MaterialTheme.typography.labelSmall,
                                            maxLines = 1
                                        )
                                    },
                                    trailingIcon = {
                                        Icon(
                                            Icons.Rounded.Close, null,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    },
                                    modifier = Modifier.height(28.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text(dynamicStringResource(R.string.addon_btn_cancel)) }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = {
                        onSave(
                            addon.copy(
                                enabled = enabled,
                                scopeMode = scopeMode,
                                customTargets = customTargets
                            )
                        )
                    }) { Text(dynamicStringResource(R.string.addon_btn_save)) }
                }
            }
        }
    }

    // Full-screen app picker dialog (LSPosed-style)
    if (showAppPicker) {
        AppPickerDialog(
            defaultTargets = addon.defaultTargets,
            selectedPackages = customTargets,
            onDismiss = { showAppPicker = false },
            onConfirm = { selected ->
                customTargets = selected
                showAppPicker = false
            }
        )
    }
}

// =====================================================================
// Full-screen App Picker Dialog (LSPosed-style)
// =====================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppPickerDialog(
    defaultTargets: Set<String>,
    selectedPackages: Set<String>,
    launchableOnlyDefault: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (Set<String>) -> Unit
) {
    val context = LocalContext.current

    var allApps by remember { mutableStateOf<List<AppInfoItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selected by remember { mutableStateOf(selectedPackages) }
    var searchQuery by remember { mutableStateOf("") }
    var showSystemApps by remember { mutableStateOf(false) }
    var launchableOnly by remember { mutableStateOf(launchableOnlyDefault) }
    val initiallyPinnedPackages = remember(selectedPackages, defaultTargets) { selectedPackages + defaultTargets }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            allApps = loadInstalledApps(context.packageManager)
        }
        isLoading = false
    }

    val filteredApps = remember(allApps, searchQuery, showSystemApps, launchableOnly, selected, defaultTargets, initiallyPinnedPackages) {
        val query = searchQuery.trim().lowercase()
        allApps
            .filter { app ->
                if (launchableOnly && !app.isLaunchable &&
                    app.packageName !in selected &&
                    app.packageName !in defaultTargets
                ) return@filter false
                if (!showSystemApps && app.isSystem &&
                    app.packageName !in selected &&
                    app.packageName !in defaultTargets
                ) return@filter false
                if (query.isNotEmpty()) {
                    val matchesLabel = app.label.lowercase().contains(query)
                    val matchesPackage = app.packageName.lowercase().contains(query)
                    return@filter matchesLabel || matchesPackage
                }
                true
            }
            .sortedWith(
                compareByDescending<AppInfoItem> {
                    it.packageName in initiallyPinnedPackages
                }.thenBy { it.label.lowercase() }
            )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            topBar = {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, dynamicStringResource(R.string.btn_close))
                        }
                    },
                    title = {
                        Column {
                            Text(
                                dynamicStringResource(R.string.addon_select_apps),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                dynamicStringResource(R.string.addon_selected_count, selected.size),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent
                    )
                )
            },
            bottomBar = {
                Surface(tonalElevation = 3.dp) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(onClick = onDismiss) { Text(dynamicStringResource(R.string.addon_btn_cancel)) }
                        Spacer(Modifier.width(12.dp))
                        Button(onClick = { onConfirm(selected) }) {
                            Text(dynamicStringResource(R.string.addon_confirm_count, selected.size))
                        }
                    }
                }
            }
        ) { padding ->
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // System apps filter
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showSystemApps = !showSystemApps },
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        dynamicStringResource(R.string.addon_show_system),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1
                                    )
                                    Text(
                                        dynamicStringResource(R.string.addon_show_system_desc),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                }
                                Switch(
                                    checked = showSystemApps,
                                    onCheckedChange = { showSystemApps = it },
                                    modifier = Modifier.padding(start = 12.dp)
                                )
                            }
                        }
                    }

                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { launchableOnly = !launchableOnly },
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        dynamicStringResource(R.string.addon_launchable_only),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1
                                    )
                                    Text(
                                        dynamicStringResource(R.string.addon_launchable_only_desc),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                }
                                Switch(
                                    checked = launchableOnly,
                                    onCheckedChange = { launchableOnly = it },
                                    modifier = Modifier.padding(start = 12.dp)
                                )
                            }
                        }
                    }

                    // Search field
                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .padding(horizontal = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Rounded.Search, null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 10.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    if (searchQuery.isEmpty()) {
                                        Text(
                                            dynamicStringResource(R.string.addon_search_apps),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1
                                        )
                                    }
                                    androidx.compose.foundation.text.BasicTextField(
                                        value = searchQuery,
                                        onValueChange = { searchQuery = it },
                                        singleLine = true,
                                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                                            color = MaterialTheme.colorScheme.onSurface
                                        ),
                                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(30.dp)) {
                                        Icon(Icons.Rounded.Close, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }

                    // App list
                    items(items = filteredApps, key = { it.packageName }) { app ->
                        val isDefault = app.packageName in defaultTargets
                        val isChecked = app.packageName in selected

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .clickable {
                                    if (!isDefault) {
                                        selected = if (isChecked) selected - app.packageName
                                        else selected + app.packageName
                                    }
                                },
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isChecked || isDefault) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                } else {
                                    MaterialTheme.colorScheme.surface
                                }
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (app.icon != null) {
                                    val bitmap = remember(app.packageName) {
                                        try { app.icon.toBitmap(width = 80, height = 80).asImageBitmap() }
                                        catch (_: Throwable) { null }
                                    }
                                    if (bitmap != null) {
                                        Image(bitmap = bitmap, contentDescription = null, modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)))
                                    } else {
                                        DefaultAppIcon()
                                    }
                                } else {
                                    DefaultAppIcon()
                                }

                                Spacer(Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            app.label,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        if (isDefault) {
                                            Spacer(Modifier.width(6.dp))
                                            Surface(
                                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    dynamicStringResource(R.string.addon_default_target_badge),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        if (app.isSystem) {
                                            Spacer(Modifier.width(6.dp))
                                            Surface(
                                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    dynamicStringResource(R.string.launcher_hidden_apps_system_badge),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        app.packageName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Checkbox(
                                    checked = isChecked || isDefault,
                                    onCheckedChange = null,
                                    enabled = !isDefault,
                                    modifier = Modifier.padding(start = 12.dp)
                                )
                            }
                        }
                    }

                    if (filteredApps.isEmpty() && !isLoading) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Text(
                                    if (searchQuery.isNotEmpty()) dynamicStringResource(R.string.addon_no_apps_match, searchQuery)
                                    else dynamicStringResource(R.string.addon_no_apps_available),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DefaultAppIcon() {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Rounded.Android, null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

class AddonAppPickerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val addonId = intent.getStringExtra(EXTRA_PICKER_ADDON_ID).orEmpty()
        val addonJar = intent.getStringExtra(EXTRA_PICKER_ADDON_JAR).orEmpty()
        val isSystemAddon = intent.getBooleanExtra(EXTRA_PICKER_IS_SYSTEM, false)
        val settingKey = intent.getStringExtra(EXTRA_PICKER_KEY).orEmpty()
        val settingTitle = intent.getStringExtra(EXTRA_PICKER_TITLE).orEmpty().ifBlank { settingKey }
        val provider = runCatching {
            SettingProvider.valueOf(intent.getStringExtra(EXTRA_PICKER_PROVIDER).orEmpty())
        }.getOrDefault(SettingProvider.GLOBAL)
        val storage = runCatching {
            SettingStorage.valueOf(intent.getStringExtra(EXTRA_PICKER_STORAGE).orEmpty())
        }.getOrDefault(SettingStorage.ADDON_FILE)
        val launchableOnly = intent.getBooleanExtra(EXTRA_PICKER_LAUNCHABLE_ONLY, false)
        val setting = AddonSettingDef(
            key = settingKey,
            title = settingTitle,
            type = SettingType.APP_LIST,
            provider = provider,
            storage = storage
        )
        val addon = AddonUiModel(
            id = addonId,
            entryClass = "",
            name = settingTitle,
            author = "",
            description = "",
            version = "",
            jarPath = addonJar,
            defaultTargets = emptySet(),
            enabled = true,
            scopeMode = 0,
            customTargets = emptySet(),
            isSystem = isSystemAddon
        )

        setContent {
            val context = LocalContext.current
            val colorScheme = if (isSystemInDarkTheme()) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            MaterialTheme(colorScheme = colorScheme) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainer) {
                    AppPickerDialog(
                        defaultTargets = emptySet(),
                        selectedPackages = readStoredArray(context, setting, addon.id, addon.jarPath, addon.isSystem).toSet(),
                        launchableOnlyDefault = launchableOnly,
                        onDismiss = { finish() },
                        onConfirm = { packages ->
                            writeStoredArray(context, setting, addon.id, addon.jarPath, addon.isSystem, packages)
                            setResult(RESULT_OK)
                            finish()
                        }
                    )
                }
            }
        }
    }
}
