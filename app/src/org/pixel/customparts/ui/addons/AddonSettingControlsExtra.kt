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
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
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
import org.pixel.customparts.ui.RadioSelectionGroup
import org.pixel.customparts.ui.edgeOutline
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
internal fun StringSettingControl(
    setting: AddonSettingDef,
    addon: AddonUiModel,
    modifier: Modifier,
    enabled: Boolean = true,
    onSettingChanged: (AddonSettingDef, Boolean?) -> Unit
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    var value by remember {
        mutableStateOf(readStoredString(context, setting, addon.id, addon.jarPath, addon.isSystem) ?: setting.defaultString)
    }

    Column(modifier = modifier.fillMaxWidth()) {
        SettingTitleText(setting)
        if (setting.description.isNotEmpty()) {
            SettingDescriptionText(setting, modifier = Modifier.padding(top = 2.dp))
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = value,
            onValueChange = {
                value = it
                writeStoredString(context, setting, addon.id, addon.jarPath, addon.isSystem, it)
                onSettingChanged(setting, null)
            },
            enabled = enabled,
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodyMedium,
            trailingIcon = {
                if (value != setting.defaultString) {
                    IconButton(onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        value = setting.defaultString
                        writeStoredString(context, setting, addon.id, addon.jarPath, addon.isSystem, setting.defaultString)
                        onSettingChanged(setting, null)
                    }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Rounded.Refresh, stringResource(R.string.btn_default), Modifier.size(16.dp))
                    }
                }
            }
        )
    }
}

// ---------- SELECT (dropdown) ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SelectSettingControl(
    setting: AddonSettingDef,
    addon: AddonUiModel,
    modifier: Modifier,
    enabled: Boolean = true,
    onSettingChanged: (AddonSettingDef, Boolean?) -> Unit
) {
    val context = LocalContext.current
    val options = resolveSelectOptions(setting, addon)
    val stored = readStoredString(context, setting, addon.id, addon.jarPath, addon.isSystem) ?: setting.defaultString
    var selectedValue by remember(setting.key) { mutableStateOf(stored) }
    val selectedIndex = options.indexOfFirst { it.value == selectedValue }

    // Shared segmented row (<=4 options) or its vertical radio-list variant.
    // Haptics and highlight handling come from the shared component; its own
    // disabled visuals (alpha + muted colors) match the main screens.
    Column(modifier = Modifier.fillMaxWidth()) {
        RadioSelectionGroup(
            title = setting.title,
            options = options.map { it.label },
            selectedIndex = selectedIndex,
            enabled = enabled,
            onSelect = { index ->
                selectedValue = options[index].value
                writeStoredString(context, setting, addon.id, addon.jarPath, addon.isSystem, options[index].value)
                onSettingChanged(setting, null)
            },
            vertical = options.size > 4
        )
        if (setting.description.isNotEmpty()) {
            SettingDescriptionText(
                setting,
                modifier = Modifier.padding(top = 2.dp).alpha(if (enabled) 1f else 0.4f)
            )
        }
    }
}

// ---------- FILE PICKER ----------

// ---------- SELECT BUTTON (ThermalActivity-style) ----------

@Composable
internal fun SelectButtonSettingControl(
    setting: AddonSettingDef,
    addon: AddonUiModel,
    modifier: Modifier,
    enabled: Boolean = true,
    onSettingChanged: (AddonSettingDef, Boolean?) -> Unit
) {
    val context = LocalContext.current
    val options = resolveSelectOptions(setting, addon)
    val stored = readStoredString(context, setting, addon.id, addon.jarPath, addon.isSystem) ?: setting.defaultString
    var selectedValue by remember(setting.key) { mutableStateOf(stored) }
    val selectedIndex = options.indexOfFirst { it.value == selectedValue }

    // Shared vertical radio list: secondaryContainer highlight, check icon,
    // spring reveal and haptics come from the common component.
    Column(modifier = Modifier.fillMaxWidth()) {
        RadioSelectionGroup(
            title = setting.title,
            options = options.map { it.label },
            selectedIndex = selectedIndex,
            enabled = enabled,
            onSelect = { index ->
                selectedValue = options[index].value
                writeStoredString(context, setting, addon.id, addon.jarPath, addon.isSystem, options[index].value)
                onSettingChanged(setting, null)
            },
            vertical = true
        )
        if (setting.description.isNotEmpty()) {
            SettingDescriptionText(
                setting,
                modifier = Modifier.padding(top = 2.dp).alpha(if (enabled) 1f else 0.4f)
            )
        }
    }
}

// ---------- FILE PICKER (original) ----------

@Composable
internal fun FileSettingControl(
    setting: AddonSettingDef,
    addon: AddonUiModel,
    modifier: Modifier,
    enabled: Boolean = true,
    onSettingChanged: (AddonSettingDef, Boolean?) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentPath = readStoredString(context, setting, addon.id, addon.jarPath, addon.isSystem) ?: ""
    var filePath by remember { mutableStateOf(currentPath) }
    val notSetLabel = stringResource(R.string.addon_file_not_set)
    val fileSavedMsg = stringResource(R.string.addon_file_saved)
    val fileCopyFailedMsg = stringResource(R.string.addon_file_copy_failed)
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
            SettingDescriptionText(setting, modifier = Modifier.padding(top = 2.dp))
        }
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceContainerHighest.edgeOutline()),
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
                        Icons.AutoMirrored.Rounded.InsertDriveFile, null,
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
            val fileHaptics = LocalHapticFeedback.current
            FilledTonalIconButton(
                onClick = {
                    fileHaptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                    filePicker.launch(setting.mimeType)
                },
                enabled = enabled,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(Icons.Rounded.FolderOpen, null, Modifier.size(20.dp))
            }
            if (filePath.isNotEmpty()) {
                Spacer(Modifier.width(4.dp))
                IconButton(
                    onClick = {
                        fileHaptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                        filePath = ""
                        writeStoredString(context, setting, addon.id, addon.jarPath, addon.isSystem, "")
                        onSettingChanged(setting, null)
                    },
                    enabled = enabled,
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

internal const val EXTRA_PICKER_ADDON_ID = "addon_id"
internal const val EXTRA_PICKER_ADDON_JAR = "addon_jar"
internal const val EXTRA_PICKER_IS_SYSTEM = "is_system"
internal const val EXTRA_PICKER_KEY = "setting_key"
internal const val EXTRA_PICKER_TITLE = "setting_title"
internal const val EXTRA_PICKER_PROVIDER = "setting_provider"
internal const val EXTRA_PICKER_STORAGE = "setting_storage"
internal const val EXTRA_PICKER_LAUNCHABLE_ONLY = "launchable_only"

@Composable
internal fun AppListSettingControl(
    setting: AddonSettingDef,
    addon: AddonUiModel,
    modifier: Modifier,
    enabled: Boolean = true,
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
            SettingDescriptionText(setting, modifier = Modifier.padding(top = 2.dp))
        }
        Spacer(Modifier.height(12.dp))
        val appListHaptics = LocalHapticFeedback.current
        FilledTonalButton(onClick = {
            appListHaptics.performHapticFeedback(HapticFeedbackType.ContextClick)
            if (setting.appPickerMode == "activity" || setting.appPickerMode == "screen" || setting.appPickerMode == "activity_launchable" || setting.appPickerMode == "screen_launchable") {
                activityPickerLauncher.launch(addonAppPickerIntent(context, addon, setting))
            } else {
                showPicker = true
            }
        },
        enabled = enabled,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondaryContainer.edgeOutline()),
        modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Rounded.Apps, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.addon_apps_selected, selected.size))
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
                            appListHaptics.performHapticFeedback(HapticFeedbackType.ContextClick)
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
internal fun ColorSettingControl(
    setting: AddonSettingDef,
    addon: AddonUiModel,
    modifier: Modifier,
    enabled: Boolean = true,
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
            SettingDescriptionText(setting, modifier = Modifier.padding(top = 2.dp))
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            val swatchColor = Color(colorInt)
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(swatchColor)
                    .border(1.dp, swatchColor.edgeOutline(), RoundedCornerShape(12.dp))
                    .clickable(enabled = enabled) { showPicker = true }
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
            val colorHaptics = LocalHapticFeedback.current
            FilledTonalButton(
                onClick = {
                    colorHaptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                    showPicker = true
                },
                enabled = enabled,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondaryContainer.edgeOutline())
            ) {
                Icon(Icons.Rounded.Palette, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.addon_color_pick))
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

/** True when this setting or any nested child carries [key]. Used to auto-reveal search highlights. */
internal fun AddonSettingDef.containsSettingKey(key: String?): Boolean {
    if (key.isNullOrBlank()) return false
    if (this.key == key) return true
    return children.any { it.containsSettingKey(key) }
}

@Composable
internal fun GroupSettingControl(
    setting: AddonSettingDef,
    addon: AddonUiModel,
    modifier: Modifier,
    allSettings: List<AddonSettingDef>,
    inheritedEnabled: Boolean,
    dependencyRevision: Int,
    onSettingChanged: (AddonSettingDef, Boolean?) -> Unit,
    highlightKey: String? = null
) {
    val containsHighlight = remember(setting, highlightKey) { setting.containsSettingKey(highlightKey) && setting.key != highlightKey }
    // Inline mode: no expandable container, just title + children in place.
    if (setting.groupMode == GroupMode.INLINE) {
        val inlineDim = if (inheritedEnabled) 1f else 0.4f
        Column(modifier = modifier.fillMaxWidth()) {
            if (setting.title.isNotEmpty()) {
                SettingTitleText(
                    setting,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = parseOptionalColor(setting.accentColor) ?: parseOptionalColor(addon.accentColor) ?: MaterialTheme.colorScheme.primary,
                    maxLines = Int.MAX_VALUE,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 2.dp).alpha(inlineDim)
                )
            }
            if (setting.description.isNotEmpty()) {
                SettingDescriptionText(
                    setting,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = Int.MAX_VALUE,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.padding(top = 2.dp, start = 20.dp, end = 20.dp, bottom = 12.dp).alpha(inlineDim)
                )
            }
            setting.children.forEachIndexed { index, child ->
                if (index > 0) HorizontalDivider()
                AddonSettingControl(child, addon, addonGroupChildModifier(child), allSettings, inheritedEnabled, dependencyRevision, onSettingChanged, highlightKey)
            }
        }
        return
    }

    if (setting.groupMode == GroupMode.IMMERSIVE_EXPAND) {
        var expanded by rememberSaveable(setting.key) { mutableStateOf(setting.defaultExpanded || containsHighlight) }
        val groupHaptics = LocalHapticFeedback.current
        // AddonExpandableGroupCard geometry (ExpandableGroupCard): 18.dp,
        // surface container and adaptive edgeOutline.
        val containerColor = groupContainerColor(
            setting = setting,
            fallback = MaterialTheme.colorScheme.surface,
            fallbackAlpha = 1f
        )

        Surface(
            color = containerColor,
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, containerColor.edgeOutline()),
            modifier = modifier.fillMaxWidth()
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            groupHaptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                            expanded = !expanded
                        }
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                        .alpha(if (inheritedEnabled) 1f else 0.4f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SettingIcon(setting, addon, Modifier.padding(end = 12.dp))
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        SettingTitleText(
                            setting,
                            style = MaterialTheme.typography.titleMedium,
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
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
                AnimatedVisibility(
                    visible = expanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column(modifier = Modifier.padding(bottom = 12.dp)) {
                        setting.children.forEachIndexed { index, child ->
                            if (index > 0) HorizontalDivider()
                            AddonSettingControl(child, addon, addonGroupChildModifier(child), allSettings, inheritedEnabled, dependencyRevision, onSettingChanged, highlightKey)
                        }
                    }
                }
            }
        }
        return
    }

    var expanded by remember(setting.key) { mutableStateOf(containsHighlight) }
    var showDialog by remember(setting.key) { mutableStateOf(containsHighlight && setting.groupMode != GroupMode.EXPANDABLE) }
    val groupHaptics = LocalHapticFeedback.current
    val openAction = {
        groupHaptics.performHapticFeedback(HapticFeedbackType.ContextClick)
        if (setting.groupMode == GroupMode.EXPANDABLE) expanded = !expanded else showDialog = true
    }

    // CARD follows the shared SectionGroupCard geometry (24.dp, surface,
    // edgeOutline); EXPANDABLE rows follow ExpandableGroupCard (18.dp).
    val containerColor = groupContainerColor(
        setting = setting,
        fallback = MaterialTheme.colorScheme.surface,
        fallbackAlpha = 1f
    )
    val groupShape = if (setting.groupMode == GroupMode.CARD) RoundedCornerShape(24.dp) else RoundedCornerShape(18.dp)

    Surface(
        color = containerColor,
        shape = groupShape,
        border = BorderStroke(1.dp, containerColor.edgeOutline()),
        modifier = modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { openAction() }
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .alpha(if (inheritedEnabled) 1f else 0.4f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SettingIcon(setting, addon, Modifier.padding(end = 12.dp))
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    SettingTitleText(
                        setting,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (setting.description.isNotEmpty()) {
                        SettingDescriptionText(setting)
                    }
                }
                Icon(
                    if (setting.groupMode == GroupMode.EXPANDABLE && expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ChevronRight,
                    null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            AnimatedVisibility(
                visible = setting.groupMode == GroupMode.EXPANDABLE && expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(modifier = Modifier.padding(bottom = 12.dp)) {
                    setting.children.forEachIndexed { index, child ->
                        if (index > 0) HorizontalDivider()
                        AddonSettingControl(child, addon, addonGroupChildModifier(child), allSettings, inheritedEnabled, dependencyRevision, onSettingChanged, highlightKey)
                    }
                }
            }
        }
    }

    if (showDialog) {
        GroupDialog(setting = setting, addon = addon, allSettings = allSettings, inheritedEnabled = inheritedEnabled, dependencyRevision = dependencyRevision, onDismiss = { showDialog = false }, onSettingChanged = onSettingChanged, highlightKey = highlightKey)
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
    onSettingChanged: (AddonSettingDef, Boolean?) -> Unit,
    highlightKey: String? = null
) {
    val fullScreen = setting.groupMode == GroupMode.FULLSCREEN
    val dialogHaptics = LocalHapticFeedback.current
    val dialogShape = if (fullScreen) RoundedCornerShape(0.dp) else RoundedCornerShape(24.dp)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = !fullScreen)) {
        Surface(
            shape = dialogShape,
            color = MaterialTheme.colorScheme.surface,
            border = if (fullScreen) null else BorderStroke(1.dp, MaterialTheme.colorScheme.surface.edgeOutline()),
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
                        IconButton(onClick = {
                            dialogHaptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                            onDismiss()
                        }) { Icon(Icons.Rounded.Close, stringResource(R.string.btn_close)) }
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        SettingTitleText(setting, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = Int.MAX_VALUE, overflow = TextOverflow.Clip)
                        if (setting.description.isNotEmpty()) {
                            SettingDescriptionText(setting, maxLines = Int.MAX_VALUE, overflow = TextOverflow.Clip)
                        }
                    }
                    if (setting.closeButtonPosition == CloseButtonPosition.END) {
                        IconButton(onClick = {
                            dialogHaptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                            onDismiss()
                        }) { Icon(Icons.Rounded.Close, stringResource(R.string.btn_close)) }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Column(
                    modifier = Modifier
                        .weight(1f, fill = fullScreen)
                        .verticalScroll(rememberScrollState())
                ) {
                    setting.children.forEach { child ->
                        AddonSettingControl(child, addon, Modifier.padding(bottom = 8.dp), allSettings, inheritedEnabled, dependencyRevision, onSettingChanged, highlightKey)
                    }
                }
            }
        }
    }
}

// ---------- VISUAL ----------

@Composable
internal fun VisualSettingControl(setting: AddonSettingDef, addon: AddonUiModel, modifier: Modifier) {
    when (setting.visualType) {
        VisualType.TEXT -> {
            Column(modifier = modifier.fillMaxWidth()) {
                if (setting.title.isNotEmpty()) {
                    SettingTitleText(
                        setting,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = parseOptionalColor(setting.accentColor) ?: MaterialTheme.colorScheme.primary,
                        maxLines = Int.MAX_VALUE,
                        overflow = TextOverflow.Clip,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }
                if (setting.description.isNotEmpty()) {
                    SettingDescriptionText(setting, style = MaterialTheme.typography.bodyMedium, maxLines = Int.MAX_VALUE, overflow = TextOverflow.Clip)
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
                        .clip(RoundedCornerShape(14.dp))
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
