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
internal fun AddonSettingControl(
    setting: AddonSettingDef,
    addon: AddonUiModel,
    modifier: Modifier = Modifier,
    allSettings: List<AddonSettingDef> = emptyList(),
    inheritedEnabled: Boolean = true,
    dependencyRevision: Int = 0,
    onSettingChanged: (AddonSettingDef, Boolean?) -> Unit = { _, _ -> },
    highlightKey: String? = null
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
    // Search highlight: flash the matched setting and scroll it into view (one-shot per key).
    val isHighlightTarget = !highlightKey.isNullOrBlank() && setting.key == highlightKey
    val bringIntoViewRequester = remember(setting.key, highlightKey) { BringIntoViewRequester() }
    var highlightConsumed by rememberSaveable(setting.key, highlightKey) { mutableStateOf(false) }
    var highlightFaded by rememberSaveable(setting.key, highlightKey) { mutableStateOf(false) }
    val highlightColor by animateColorAsState(
        targetValue = if (isHighlightTarget && !highlightFaded) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        } else {
            Color.Transparent
        },
        animationSpec = tween(durationMillis = 600),
        label = "searchHighlight"
    )
    if (isHighlightTarget && !highlightConsumed) {
        LaunchedEffect(setting.key, highlightKey) {
            highlightConsumed = true
            // Let navigation/initial composition settle before scrolling.
            delay(350)
            runCatching { bringIntoViewRequester.bringIntoView() }
            delay(2600)
            highlightFaded = true
        }
    }
    val effectiveModifier = if (setting.isImmersiveGroup()) modifier.fillMaxWidth() else modifier
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = effectiveModifier
            .bringIntoViewRequester(bringIntoViewRequester)
            .background(highlightColor, RoundedCornerShape(12.dp))
    ) {
        val contentModifier = Modifier.alpha(if (enabled) 1f else 0.45f)
        if (setting.icon.isNotEmpty() && setting.type != SettingType.VISUAL && setting.type != SettingType.GROUP) {
            Row(modifier = contentModifier, verticalAlignment = Alignment.CenterVertically) {
                SettingIcon(setting, addon, Modifier.padding(end = 12.dp))
                val innerMod = Modifier.weight(1f)
                AddonSettingControlInner(setting, addon, innerMod, allSettings, enabled, dependencyRevision, onSettingChanged, highlightKey)
            }
        } else {
            AddonSettingControlInner(setting, addon, contentModifier, allSettings, enabled, dependencyRevision, onSettingChanged, highlightKey)
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
    onSettingChanged: (AddonSettingDef, Boolean?) -> Unit = { _, _ -> },
    highlightKey: String? = null
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
        SettingType.GROUP -> GroupSettingControl(setting, addon, modifier, allSettings, inheritedEnabled, dependencyRevision, onSettingChanged, highlightKey)
        SettingType.VISUAL -> VisualSettingControl(setting, addon, modifier)
        SettingType.COMMAND_BUTTON -> CommandButtonSettingControl(setting, addon, modifier)
        SettingType.TOGGLE, SettingType.SWITCH -> SwitchSettingControl(setting, addon, modifier, dependencyRevision, onSettingChanged)
        SettingType.CHECKBOX -> CheckboxSettingControl(setting, addon, modifier, dependencyRevision, onSettingChanged)
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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val accent = settingAccent(setting, addon)
    val command = setting.command.ifBlank { setting.commandOn }
    val binderActions = setting.binderOn
    var running by remember { mutableStateOf(false) }
    var output by remember { mutableStateOf("") }

    Column(modifier = modifier.fillMaxWidth()) {
        if (setting.description.isNotEmpty()) {
            SettingDescriptionText(setting, modifier = Modifier.padding(bottom = 8.dp))
        }
        Button(
            onClick = {
                if ((command.isBlank() && binderActions.isEmpty()) || running) return@Button
                running = true
                if (setting.showOutput) output = ""
                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        val lines = mutableListOf<String>()
                        val binderOutput = applyBinderActions(context, binderActions)
                        if (binderOutput.isNotBlank()) lines += binderOutput
                        if (command.isNotBlank()) lines += runShellCommandForResult(command).combinedOutput()
                        lines.filter { it.isNotBlank() }.joinToString("\n")
                    }
                    if (setting.showOutput) output = result
                    running = false
                }
            },
            enabled = (command.isNotBlank() || binderActions.isNotEmpty()) && !running,
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
    dependencyRevision: Int,
    onSettingChanged: (AddonSettingDef, Boolean?) -> Unit
) {
    val context = LocalContext.current
    val accent = settingAccent(setting, addon)
    val defaultVal = if (setting.defaultBool) 1 else setting.defaultInt
    var checked by remember(setting.key, dependencyRevision) {
        mutableStateOf(readStoredInt(context, setting, addon.id, addon.jarPath, addon.isSystem, defaultVal) != 0)
    }
    val scope = rememberCoroutineScope()
    var commandRunning by remember { mutableStateOf(false) }
    var commandOutput by remember { mutableStateOf("") }

    fun updateChecked(newChecked: Boolean) {
        if (commandRunning) return
        checked = newChecked
        writeStoredInt(context, setting, addon.id, addon.jarPath, addon.isSystem, if (newChecked) 1 else 0)
        applySettingsAssignments(context, if (newChecked) setting.settingsOn else setting.settingsOff)
        onSettingChanged(setting, newChecked)
        val command = if (newChecked) setting.commandOn else setting.commandOff
        val binderActions = if (newChecked) setting.binderOn else setting.binderOff
        if (command.isBlank() && binderActions.isEmpty()) return
        commandRunning = true
        if (setting.showOutput) commandOutput = ""
        scope.launch {
            val output = withContext(Dispatchers.IO) {
                val lines = mutableListOf<String>()
                val binderOutput = applyBinderActions(context, binderActions)
                if (binderOutput.isNotBlank()) lines += binderOutput
                if (command.isNotBlank()) lines += runShellCommandForResult(command).combinedOutput()
                lines.filter { it.isNotBlank() }.joinToString("\n")
            }
            if (setting.showOutput) commandOutput = output
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
        if (setting.showOutput && (setting.commandOn.isNotBlank() || setting.commandOff.isNotBlank() || setting.binderOn.isNotEmpty() || setting.binderOff.isNotEmpty())) {
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
    dependencyRevision: Int,
    onSettingChanged: (AddonSettingDef, Boolean?) -> Unit
) {
    val context = LocalContext.current
    val accent = settingAccent(setting, addon)
    val defaultVal = if (setting.defaultBool) 1 else setting.defaultInt
    var checked by remember(setting.key, dependencyRevision) {
        mutableStateOf(readStoredInt(context, setting, addon.id, addon.jarPath, addon.isSystem, defaultVal) != 0)
    }

    fun updateChecked(newChecked: Boolean) {
        checked = newChecked
        writeStoredInt(context, setting, addon.id, addon.jarPath, addon.isSystem, if (newChecked) 1 else 0)
        applySettingsAssignments(context, if (newChecked) setting.settingsOn else setting.settingsOff)
        onSettingChanged(setting, newChecked)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { updateChecked(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = { updateChecked(it) },
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
                    if (isBound) stringResource(R.string.addon_tile_slot, boundSlot) else if (setting.tileConfigurable) stringResource(R.string.addon_tile_configurable) else stringResource(R.string.addon_tile_not_added),
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
                    label = { Text(stringResource(R.string.addon_tile_name)) },
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
                        label = { Text(stringResource(R.string.addon_tile_setting)) },
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
                            label = { Text(stringResource(R.string.addon_tile_long_press)) },
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
                    Text(stringResource(R.string.addon_tile_add))
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
                Text(stringResource(R.string.addon_tile_bind))
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

internal fun TextStyle.withAddonTextSize(sizeSp: Float): TextStyle {
    return if (sizeSp > 0f) copy(fontSize = sizeSp.sp) else this
}

@Composable
internal fun SettingTitleText(
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
internal fun SettingDescriptionText(
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
                Icon(Icons.Rounded.Refresh, stringResource(R.string.btn_default), Modifier.size(16.dp), tint = accent)
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
                Icon(Icons.Rounded.Refresh, stringResource(R.string.btn_default), Modifier.size(16.dp), tint = accent)
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
