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

internal fun isSafeModeActive(context: Context): Boolean {
    return try {
        Settings.Global.getInt(context.contentResolver, SAFE_MODE_SETTING, 0) == 1
    } catch (_: Throwable) { false }
}

internal fun exitAddonSafeMode(context: Context) {
    try {
        Settings.Global.putInt(context.contentResolver, SAFE_MODE_SETTING, 0)
        // Also delete the crash counter file
        val guardFile = File("/data/pixelparts/.boot_guard")
        if (guardFile.exists()) guardFile.delete()
    } catch (t: Throwable) {
        Log.e(TAG, "Failed to exit safe mode", t)
    }
}

internal fun readAddonEnabled(context: Context, id: String, defaultEnabled: Boolean = true): Boolean {
    return try {
        Settings.Global.getInt(context.contentResolver, "${ADDON_PREFIX}${id}_enabled", if (defaultEnabled) 1 else 0) != 0
    } catch (_: Throwable) { defaultEnabled }
}

internal fun writeAddonEnabled(context: Context, id: String, enabled: Boolean) {
    Settings.Global.putInt(context.contentResolver, "${ADDON_PREFIX}${id}_enabled", if (enabled) 1 else 0)
}

internal fun readScopeMode(context: Context, id: String): Int {
    return try {
        Settings.Global.getInt(context.contentResolver, "${ADDON_PREFIX}${id}_scope_mode", 0)
    } catch (_: Throwable) { 0 }
}

internal fun writeScopeMode(context: Context, id: String, mode: Int) {
    Settings.Global.putInt(context.contentResolver, "${ADDON_PREFIX}${id}_scope_mode", mode)
}

internal fun readCustomTargets(context: Context, id: String): Set<String> {
    val raw = Settings.Global.getString(context.contentResolver, "${ADDON_PREFIX}${id}_packages")
    if (raw.isNullOrBlank()) return emptySet()
    return raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
}

internal fun writeCustomTargets(context: Context, id: String, targets: Set<String>) {
    Settings.Global.putString(context.contentResolver, "${ADDON_PREFIX}${id}_packages", targets.joinToString(","))
}

internal fun addToWhitelist(context: Context, pkg: String) {
    Settings.Global.putInt(context.contentResolver, "${INJECT_PREFIX}${pkg}", 1)
}

internal fun removeFromWhitelist(context: Context, pkg: String) {
    Settings.Global.putInt(context.contentResolver, "${INJECT_PREFIX}${pkg}", 0)
}

internal val BUILTIN_WHITELIST = setOf(
    "com.google.android.apps.nexuslauncher",
    "com.google.android.apps.pixel.launcher",
    "com.android.launcher3",
    "com.android.systemui"
)

// =====================================================================
// Settings provider read/write helpers
// =====================================================================

// =====================================================================
// Engine-rename key compat: canonical "_lsplant" suffix for writes, with
// read-through fallback to legacy "_pine"/"_xposed" values so user settings
// survive the update. Applies to GLOBAL hook keys only; other providers
// and unsuffixed keys pass through untouched.
//
// Suffix vocabulary + strip live in HookKeyCompat (shared with
// SettingsCompat/PixelPartsTileRefresher). The write/read gating below
// stays local on purpose: bare addon.json keys must pass through verbatim,
// only suffixed keys are rewritten — see HookKeyCompat KDoc.
private fun stripHookSuffix(key: String): String = HookKeyCompat.stripHookSuffix(key)

private fun hookSuffixes(): List<String> =
    listOf(HookKeyCompat.ACTIVE_SUFFIX, HookKeyCompat.LEGACY_ENGINE_SUFFIX, HookKeyCompat.XPOSED_SUFFIX)

private fun writeKeyForHookSettings(provider: SettingProvider, key: String): String {
    if (provider != SettingProvider.GLOBAL) return key
    for (s in hookSuffixes()) {
        if (key.endsWith(s)) return stripHookSuffix(key) + HookKeyCompat.ACTIVE_SUFFIX
    }
    return key
}

private fun readKeyCandidates(provider: SettingProvider, key: String): List<String> {
    if (provider != SettingProvider.GLOBAL) return listOf(key)
    val stripped = stripHookSuffix(key)
    var hasSuffix = false
    for (s in hookSuffixes()) {
        if (key.endsWith(s)) {
            hasSuffix = true
            break
        }
    }
    if (!hasSuffix) return listOf(key)
    return hookSuffixes().map { stripped + it }
}

private fun readGlobalStringAny(context: Context, key: String): String? {
    for (candidate in readKeyCandidates(SettingProvider.GLOBAL, key)) {
        try {
            val value = Settings.Global.getString(context.contentResolver, candidate)
            if (value != null) return value
        } catch (_: Throwable) { }
    }
    return null
}

private fun readSettingString(context: Context, provider: SettingProvider, key: String): String? {
    if (provider == SettingProvider.GLOBAL) return readGlobalStringAny(context, key)
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
            SettingProvider.GLOBAL -> Settings.Global.putString(context.contentResolver, writeKeyForHookSettings(provider, key), value)
            SettingProvider.SYSTEM -> Settings.System.putString(context.contentResolver, key, value)
            SettingProvider.SECURE -> Settings.Secure.putString(context.contentResolver, key, value)
        }
        if (provider == SettingProvider.GLOBAL) PixelPartsTileRefresher.requestForSetting(context, key)
    } catch (t: Throwable) { Log.e(TAG, "writeSettingString($key) failed", t) }
}

private fun readSettingInt(context: Context, provider: SettingProvider, key: String, default: Int): Int {
    if (provider == SettingProvider.GLOBAL) {
        return readGlobalStringAny(context, key)?.toIntOrNull() ?: default
    }
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
            SettingProvider.GLOBAL -> Settings.Global.putInt(context.contentResolver, writeKeyForHookSettings(provider, key), value)
            SettingProvider.SYSTEM -> Settings.System.putInt(context.contentResolver, key, value)
            SettingProvider.SECURE -> Settings.Secure.putInt(context.contentResolver, key, value)
        }
        if (provider == SettingProvider.GLOBAL) PixelPartsTileRefresher.requestForSetting(context, key)
    } catch (t: Throwable) { Log.e(TAG, "writeSettingInt($key) failed", t) }
}

private fun readSettingFloat(context: Context, provider: SettingProvider, key: String, default: Float): Float {
    if (provider == SettingProvider.GLOBAL) {
        return readGlobalStringAny(context, key)?.toFloatOrNull() ?: default
    }
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
            SettingProvider.GLOBAL -> Settings.Global.putFloat(context.contentResolver, writeKeyForHookSettings(provider, key), value)
            SettingProvider.SYSTEM -> Settings.System.putFloat(context.contentResolver, key, value)
            SettingProvider.SECURE -> Settings.Secure.putFloat(context.contentResolver, key, value)
        }
        if (provider == SettingProvider.GLOBAL) PixelPartsTileRefresher.requestForSetting(context, key)
    } catch (t: Throwable) { Log.e(TAG, "writeSettingFloat($key) failed", t) }
}

internal fun applySettingsAssignments(context: Context, assignments: List<SettingsAssignmentDef>) {
    assignments.forEach { assignment ->
        when (assignment.valueType) {
            AssignmentValueType.BOOL, AssignmentValueType.INT -> {
                val intValue = assignment.value.toBooleanStrictOrNull()?.let { if (it) 1 else 0 }
                    ?: assignment.value.toIntOrNull()
                    ?: if (assignment.value.equals("true", ignoreCase = true)) 1 else 0
                writeSettingInt(context, assignment.provider, assignment.key, intValue)
            }
            AssignmentValueType.FLOAT -> writeSettingFloat(context, assignment.provider, assignment.key, assignment.value.toFloatOrNull() ?: 0f)
            AssignmentValueType.STRING -> writeSettingString(context, assignment.provider, assignment.key, assignment.value)
        }
    }
}

internal fun applyBinderActions(context: Context, actions: List<BinderActionDef>): String {
    if (actions.isEmpty()) return ""
    val output = mutableListOf<String>()
    actions.forEach { action ->
        when (action.type) {
            "carrier_config" -> output += applyCarrierConfigOverride(context, action)
            else -> output += "Unsupported binder action: ${action.type}"
        }
    }
    return output.filter { it.isNotBlank() }.joinToString("\n")
}

private fun applyCarrierConfigOverride(context: Context, action: BinderActionDef): String {
    val manager = context.getSystemService(CarrierConfigManager::class.java)
        ?: return "CarrierConfigManager is not available"
    val subIds = resolveCarrierConfigSubIds(context, action)
    if (subIds.isEmpty()) return "No subscription IDs resolved for carrier_config action"
    val bundle = if (action.clear) null else buildCarrierConfigBundle(action.values)
    if (!action.clear && (bundle == null || bundle.isEmpty)) return "carrier_config action has no values"
    val results = mutableListOf<String>()
    subIds.forEach { subId ->
        try {
            manager.overrideConfig(subId, bundle)
            results += if (action.clear) {
                "Cleared carrier config override for subId $subId"
            } else {
                "Applied carrier config override for subId $subId (${action.values.size} values)"
            }
        } catch (t: Throwable) {
            Log.e(TAG, "carrier_config override failed for subId $subId", t)
            results += "carrier_config override failed for subId $subId: ${t.message ?: t.javaClass.simpleName}"
        }
    }
    return results.joinToString("\n")
}

private fun resolveCarrierConfigSubIds(context: Context, action: BinderActionDef): List<Int> {
    if (action.targetSubIds.isNotEmpty()) return action.targetSubIds.distinct()
    val activeSubIds = if (action.useActiveSubscriptions) getActiveSubscriptionIds(context) else emptyList()
    return (activeSubIds.ifEmpty { action.fallbackSubIds }).distinct()
}

private fun getActiveSubscriptionIds(context: Context): List<Int> {
    return try {
        val subscriptionManager = context.getSystemService(SubscriptionManager::class.java) ?: return emptyList()
        subscriptionManager.activeSubscriptionInfoList.orEmpty().map { it.subscriptionId }
    } catch (t: Throwable) {
        Log.e(TAG, "active subscription lookup failed", t)
        emptyList()
    }
}

private fun buildCarrierConfigBundle(values: List<BinderValueDef>): PersistableBundle {
    val bundle = PersistableBundle()
    values.forEach { value ->
        when (value.valueType) {
            BinderValueType.STRING -> bundle.putString(value.key, value.value?.toString().orEmpty())
            BinderValueType.BOOL -> bundle.putBoolean(value.key, value.value.toBooleanValue())
            BinderValueType.INT -> bundle.putInt(value.key, value.value.toIntValue())
            BinderValueType.LONG -> bundle.putLong(value.key, value.value.toLongValue())
            BinderValueType.DOUBLE -> bundle.putDouble(value.key, value.value.toDoubleValue())
            BinderValueType.INT_ARRAY -> bundle.putIntArray(value.key, value.value.toIntArrayValue())
            BinderValueType.LONG_ARRAY -> bundle.putLongArray(value.key, value.value.toLongArrayValue())
            BinderValueType.STRING_ARRAY -> bundle.putStringArray(value.key, value.value.toStringArrayValue())
            BinderValueType.BOOL_ARRAY -> bundle.putBooleanArray(value.key, value.value.toBooleanArrayValue())
        }
    }
    return bundle
}

private fun Any?.toBooleanValue(): Boolean {
    return when (this) {
        is Boolean -> this
        is Number -> toInt() != 0
        is String -> trim().let { it.equals("true", ignoreCase = true) || it == "1" }
        else -> false
    }
}

private fun Any?.toIntValue(): Int {
    return when (this) {
        is Number -> toInt()
        is String -> trim().toIntOrNull() ?: 0
        is Boolean -> if (this) 1 else 0
        else -> 0
    }
}

private fun Any?.toLongValue(): Long {
    return when (this) {
        is Number -> toLong()
        is String -> trim().toLongOrNull() ?: 0L
        is Boolean -> if (this) 1L else 0L
        else -> 0L
    }
}

private fun Any?.toDoubleValue(): Double {
    return when (this) {
        is Number -> toDouble()
        is String -> trim().toDoubleOrNull() ?: 0.0
        is Boolean -> if (this) 1.0 else 0.0
        else -> 0.0
    }
}

private fun Any?.toIntArrayValue(): IntArray {
    return toJsonArrayList().map { it.toIntValue() }.toIntArray()
}

private fun Any?.toLongArrayValue(): LongArray {
    return toJsonArrayList().map { it.toLongValue() }.toLongArray()
}

private fun Any?.toStringArrayValue(): Array<String> {
    return toJsonArrayList().map { it?.toString().orEmpty() }.toTypedArray()
}

private fun Any?.toBooleanArrayValue(): BooleanArray {
    return toJsonArrayList().map { it.toBooleanValue() }.toBooleanArray()
}

private fun Any?.toJsonArrayList(): List<Any?> {
    return when (this) {
        is JSONArray -> (0 until length()).map { index -> opt(index) }
        is String -> parseStringList(this)
        null -> emptyList()
        else -> listOf(this)
    }
}

internal fun addonDataDir(addonId: String, addonJarPath: String, isSystemAddon: Boolean): File {
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

internal fun sanitizeFileSegment(value: String): String {
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

internal fun readStoredString(
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

internal fun writeStoredString(
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

internal fun readStoredInt(context: Context, setting: AddonSettingDef, addonId: String, addonJarPath: String, isSystemAddon: Boolean, default: Int): Int {
    if (setting.storage == SettingStorage.SETTINGS) return readSettingInt(context, setting.provider, setting.key, default)
    return readStoredString(context, setting, addonId, addonJarPath, isSystemAddon)?.toIntOrNull() ?: default
}

internal fun writeStoredInt(context: Context, setting: AddonSettingDef, addonId: String, addonJarPath: String, isSystemAddon: Boolean, value: Int) {
    if (setting.storage == SettingStorage.SETTINGS) writeSettingInt(context, setting.provider, setting.key, value)
    else writeStoredString(context, setting, addonId, addonJarPath, isSystemAddon, value.toString())
}

internal fun readStoredFloat(context: Context, setting: AddonSettingDef, addonId: String, addonJarPath: String, isSystemAddon: Boolean, default: Float): Float {
    if (setting.storage == SettingStorage.SETTINGS) return readSettingFloat(context, setting.provider, setting.key, default)
    return readStoredString(context, setting, addonId, addonJarPath, isSystemAddon)?.toFloatOrNull() ?: default
}

internal fun writeStoredFloat(context: Context, setting: AddonSettingDef, addonId: String, addonJarPath: String, isSystemAddon: Boolean, value: Float) {
    if (setting.storage == SettingStorage.SETTINGS) writeSettingFloat(context, setting.provider, setting.key, value)
    else writeStoredString(context, setting, addonId, addonJarPath, isSystemAddon, value.toString())
}

internal fun readStoredArray(
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

internal fun writeStoredArray(
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

internal fun AddonSettingDef.isImmersiveGroup(): Boolean {
    return type == SettingType.GROUP && groupMode == GroupMode.IMMERSIVE_EXPAND
}

internal data class SettingDependencyState(
    val enabled: Boolean,
    val forceValue: String = ""
)

internal fun resolveSettingDependencyState(
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

internal fun writeForcedSettingValue(
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
