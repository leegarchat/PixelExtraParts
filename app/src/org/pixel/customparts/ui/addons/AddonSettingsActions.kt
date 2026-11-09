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
import androidx.compose.ui.res.stringResource

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

@Composable
internal fun AddonSettingsActions(
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
                Text(stringResource(R.string.addon_settings_import))
            }
            OutlinedButton(onClick = { showExportDialog = true }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Rounded.SaveAlt, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.addon_settings_export))
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
                    stringResource(R.string.addon_settings_export),
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
                        Text(stringResource(R.string.addon_export_settings_title), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text(stringResource(R.string.addon_export_settings_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        Text(stringResource(R.string.addon_export_module_title), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text(stringResource(R.string.addon_export_module_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Spacer(Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.addon_btn_cancel)) }
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
                    updating -> stringResource(R.string.addon_update_installing)
                    checking -> stringResource(R.string.addon_update_checking)
                    updateInfo != null -> stringResource(R.string.addon_update_install)
                    else -> stringResource(R.string.addon_update_check)
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

internal fun exportAddonSettings(context: Context, uri: Uri, addon: AddonUiModel): Boolean {
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

internal fun importAddonSettings(context: Context, uri: Uri, addon: AddonUiModel): Boolean {
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

internal fun compareVersions(left: String, right: String): Int {
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
internal fun copyFileToAddonData(context: Context, uri: Uri, dataDir: File, settingKey: String): String? {
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
internal fun ManualIntInputDialog(
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
                Text(stringResource(R.string.addon_range_format, min.toString(), max.toString()), style = MaterialTheme.typography.bodySmall)
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
                }) { Text(stringResource(R.string.btn_default)) }
                Button(onClick = {
                    val n = text.toIntOrNull()
                    if (n != null && n in min..max) onConfirm(n)
                    else isError = true
                }) { Text(stringResource(R.string.btn_apply)) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.addon_btn_cancel)) } },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    )
}

@Composable
internal fun ManualFloatInputDialog(
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
                Text(stringResource(R.string.addon_range_format, min.toString(), max.toString()), style = MaterialTheme.typography.bodySmall)
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
                }) { Text(stringResource(R.string.btn_default)) }
                Button(onClick = {
                    val n = text.toFloatOrNull()
                    if (n != null && n >= min && n <= max) onConfirm(n)
                    else isError = true
                }) { Text(stringResource(R.string.btn_apply)) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.addon_btn_cancel)) } },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    )
}

// =====================================================================
// Active Apps Section — expandable per-app module lists
// =====================================================================
