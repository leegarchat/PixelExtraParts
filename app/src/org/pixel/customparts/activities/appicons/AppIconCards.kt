package org.pixel.customparts.activities

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.pixel.customparts.R
import org.pixel.customparts.SettingsKeys
import org.pixel.customparts.dynamicDarkColorScheme
import org.pixel.customparts.dynamicLightColorScheme
import org.pixel.customparts.icons.AppIconCandidate
import org.pixel.customparts.icons.AppIconShapeOverrides
import org.pixel.customparts.icons.IconApplyMode
import org.pixel.customparts.icons.IconApplyProgress
import org.pixel.customparts.icons.IconApplyResult
import org.pixel.customparts.icons.IconDashboardState
import org.pixel.customparts.icons.IconMapSnapshot
import org.pixel.customparts.icons.IconPackApplyStatus
import org.pixel.customparts.icons.IconPackEntry
import org.pixel.customparts.icons.IconPackInfo
import org.pixel.customparts.icons.IconPackManager
import org.pixel.customparts.icons.IconPackPreviewItem
import org.pixel.customparts.icons.IconShapeAreaConfig
import org.pixel.customparts.icons.InstalledIconApp
import org.pixel.customparts.ui.REBOOT_BUBBLE_CONTENT_BOTTOM_PADDING
import org.pixel.customparts.ui.ColorPickerDialog
import org.pixel.customparts.ui.RebootBubble
import org.pixel.customparts.ui.RebootBubbleMenuAction
import org.pixel.customparts.ui.SettingsGroupCard
import org.pixel.customparts.ui.SliderSettingFloat
import org.pixel.customparts.ui.TopBarBlurOverlay
import org.pixel.customparts.ui.TopStatusVeil
import org.pixel.customparts.ui.addons.AddonMainEntry
import org.pixel.customparts.ui.addons.AddonMainEntryRow
import org.pixel.customparts.ui.addons.flattenSettings
import org.pixel.customparts.ui.addons.scanAddonActivityEntries
import org.pixel.customparts.ui.performRebootSystem
import org.pixel.customparts.ui.recordLayer
import org.pixel.customparts.ui.rememberGraphicsLayerRecordingState
import org.pixel.customparts.utils.SettingsCompat
import java.util.Locale
import androidx.compose.ui.res.stringResource

internal data class AppIconModuleFlags(
    val moduleEnabled: Boolean,
    val launcherOnly: Boolean
)

internal data class AppIconSystemShapeFlags(
    val stretchShape: Boolean,
    val removeShape: Boolean,
    val scalePercent: Float
)

internal data class AppIconNotificationShapeFlags(
    val stretchShape: Boolean,
    val removeShape: Boolean,
    val scalePercent: Float
)

internal data class AppIconLauncherShapeFlags(
    val stretchShape: Boolean,
    val removeShape: Boolean,
    val scalePercent: Float
)

internal data class AppIconShapeTintFlags(
    val backgroundTintMode: Int,
    val backgroundTintColor: Int,
    val foregroundTintMode: Int,
    val foregroundTintColor: Int
)

internal data class AppIconChooserItem(
    val iconPackPackage: String,
    val iconPackLabel: String,
    val drawableName: String,
    val label: String,
    val detected: Boolean,
    val appPackageName: String?
)

private const val APP_ICON_TINT_MODE_OFF = 0
private const val APP_ICON_TINT_MODE_CUSTOM = 1
private const val APP_ICON_TINT_MODE_AUTO = 2
private const val DEFAULT_SHAPE_BACKGROUND_TINT_COLOR = 0x00000000
private const val DEFAULT_SHAPE_FOREGROUND_TINT_COLOR = -0x1

@Composable
internal fun LoadingCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    }
}

@Composable
internal fun AppIconsAddonInjectedSettings(
    entries: List<AddonMainEntry>,
    onIconShapeClick: () -> Unit
) {
    val context = LocalContext.current
    SettingsGroupCard(title = stringResource(R.string.app_icons_advanced_settings_title)) {
        AppIconsNavigationRow(
            title = stringResource(R.string.icon_shape_title),
            summary = stringResource(R.string.icon_shape_summary),
            onClick = onIconShapeClick
        )
        if (entries.isNotEmpty()) HorizontalDivider()
        entries.forEachIndexed { index, entry ->
            if (index > 0) HorizontalDivider()
            key(entry.rawId) {
                AddonMainEntryRow(
                    entry = entry,
                    iconContainerSize = 44.dp,
                    onClick = {
                        AddonPageActivity.start(
                            context = context,
                            addonId = entry.addonId,
                            pageId = entry.leafId,
                            title = entry.title,
                            includeTargetActivityEntries = true
                        )
                    }
                )
            }
        }
    }
}

@Composable
internal fun AppIconsModuleSwitch(
    checked: Boolean,
    launcherOnlyChecked: Boolean,
    systemStretchShapeChecked: Boolean,
    systemRemoveShapeChecked: Boolean,
    notificationStretchShapeChecked: Boolean,
    notificationRemoveShapeChecked: Boolean,
    launcherStretchShapeChecked: Boolean,
    launcherRemoveShapeChecked: Boolean,
    systemShapeScalePercent: Float,
    notificationShapeScalePercent: Float,
    launcherShapeScalePercent: Float,
    backgroundTintMode: Int,
    backgroundTintColor: Int,
    foregroundTintMode: Int,
    foregroundTintColor: Int,
    advancedExpanded: Boolean,
    showHardcodedControls: Boolean,
    showLauncherControls: Boolean,
    onIconShapeClick: () -> Unit,
    onCheckedChange: (Boolean) -> Unit,
    onLauncherOnlyChange: (Boolean) -> Unit,
    onSystemStretchShapeChange: (Boolean) -> Unit,
    onSystemRemoveShapeChange: (Boolean) -> Unit,
    onNotificationStretchShapeChange: (Boolean) -> Unit,
    onNotificationRemoveShapeChange: (Boolean) -> Unit,
    onLauncherStretchShapeChange: (Boolean) -> Unit,
    onLauncherRemoveShapeChange: (Boolean) -> Unit,
    onSystemShapeScaleChange: (Float) -> Unit,
    onNotificationShapeScaleChange: (Float) -> Unit,
    onLauncherShapeScaleChange: (Float) -> Unit,
    onBackgroundTintModeChange: (Int) -> Unit,
    onBackgroundTintColorChange: (Int) -> Unit,
    onForegroundTintModeChange: (Int) -> Unit,
    onForegroundTintColorChange: (Int) -> Unit,
    onAdvancedExpandedChange: (Boolean) -> Unit
) {
    val systemOptionsEnabled = checked && !launcherOnlyChecked
    val launcherOptionsEnabled = checked
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
        ) {
            if (showHardcodedControls) {
                AppIconsSwitchRow(
                    title = stringResource(R.string.app_icons_module_enabled_title),
                    summary = stringResource(R.string.app_icons_module_enabled_summary),
                    checked = checked,
                    enabled = true,
                    onCheckedChange = onCheckedChange
                )
            }
            if (showHardcodedControls && showLauncherControls) {
                HorizontalDivider()
                AppIconsSwitchRow(
                    title = stringResource(R.string.app_icons_launcher_only_title),
                    summary = stringResource(R.string.app_icons_launcher_only_summary),
                    checked = checked && launcherOnlyChecked,
                    enabled = checked,
                    onCheckedChange = onLauncherOnlyChange
                )
            }
            if (showHardcodedControls) {
                ExpandableSectionHeaderContent(
                    title = stringResource(R.string.app_icons_advanced_settings_title),
                    subtitle = stringResource(R.string.app_icons_advanced_settings_summary),
                    icon = Icons.Rounded.Apps,
                    expanded = advancedExpanded,
                    onExpandChange = onAdvancedExpandedChange
                )
                AnimatedVisibility(
                    visible = advancedExpanded,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                    AppIconsNavigationRow(
                        title = stringResource(R.string.icon_shape_title),
                        summary = stringResource(R.string.icon_shape_summary),
                        onClick = onIconShapeClick
                    )
                    HorizontalDivider()
                    AppIconsSwitchRow(
                        title = stringResource(R.string.app_icons_system_stretch_shape_title),
                        summary = stringResource(R.string.app_icons_system_stretch_shape_summary),
                        checked = systemOptionsEnabled && systemStretchShapeChecked,
                        enabled = systemOptionsEnabled,
                        onCheckedChange = onSystemStretchShapeChange
                    )
                    if (systemStretchShapeChecked) {
                        HorizontalDivider()
                        AppIconsScaleSliderRow(
                            title = stringResource(R.string.app_icons_system_shape_scale_title),
                            value = systemShapeScalePercent,
                            enabled = systemOptionsEnabled,
                            onValueChange = onSystemShapeScaleChange
                        )
                    }
                    HorizontalDivider()
                    AppIconsSwitchRow(
                        title = stringResource(R.string.app_icons_system_remove_shape_title),
                        summary = stringResource(R.string.app_icons_system_remove_shape_summary),
                        checked = systemOptionsEnabled && systemRemoveShapeChecked,
                        enabled = systemOptionsEnabled,
                        onCheckedChange = onSystemRemoveShapeChange
                    )
                    HorizontalDivider()
                    AppIconsSwitchRow(
                        title = stringResource(R.string.app_icons_notification_stretch_shape_title),
                        summary = stringResource(R.string.app_icons_notification_stretch_shape_summary),
                        checked = systemOptionsEnabled && notificationStretchShapeChecked,
                        enabled = systemOptionsEnabled,
                        onCheckedChange = onNotificationStretchShapeChange
                    )
                    if (notificationStretchShapeChecked) {
                        HorizontalDivider()
                        AppIconsScaleSliderRow(
                            title = stringResource(R.string.app_icons_notification_shape_scale_title),
                            value = notificationShapeScalePercent,
                            enabled = systemOptionsEnabled,
                            onValueChange = onNotificationShapeScaleChange
                        )
                    }
                    HorizontalDivider()
                    AppIconsSwitchRow(
                        title = stringResource(R.string.app_icons_notification_remove_shape_title),
                        summary = stringResource(R.string.app_icons_notification_remove_shape_summary),
                        checked = systemOptionsEnabled && notificationRemoveShapeChecked,
                        enabled = systemOptionsEnabled,
                        onCheckedChange = onNotificationRemoveShapeChange
                    )
                    if (showLauncherControls) {
                        HorizontalDivider()
                        AppIconsSwitchRow(
                            title = stringResource(R.string.app_icons_launcher_stretch_shape_title),
                            summary = stringResource(R.string.app_icons_launcher_stretch_shape_summary),
                            checked = launcherOptionsEnabled && launcherStretchShapeChecked,
                            enabled = launcherOptionsEnabled,
                            onCheckedChange = onLauncherStretchShapeChange
                        )
                        if (launcherStretchShapeChecked) {
                            HorizontalDivider()
                            AppIconsScaleSliderRow(
                                title = stringResource(R.string.app_icons_launcher_shape_scale_title),
                                value = launcherShapeScalePercent,
                                enabled = launcherOptionsEnabled,
                                onValueChange = onLauncherShapeScaleChange
                            )
                        }
                        HorizontalDivider()
                        AppIconsSwitchRow(
                            title = stringResource(R.string.app_icons_launcher_remove_shape_title),
                            summary = stringResource(R.string.app_icons_launcher_remove_shape_summary),
                            checked = launcherOptionsEnabled && launcherRemoveShapeChecked,
                            enabled = launcherOptionsEnabled,
                            onCheckedChange = onLauncherRemoveShapeChange
                        )
                    }
                    HorizontalDivider()
                    AppIconsTintControlRow(
                        title = stringResource(R.string.app_icons_shape_background_tint_title),
                        summary = stringResource(R.string.app_icons_shape_background_tint_summary),
                        mode = backgroundTintMode,
                        color = backgroundTintColor,
                        enabled = checked,
                        onModeChange = onBackgroundTintModeChange,
                        onColorChange = onBackgroundTintColorChange
                    )
                    HorizontalDivider()
                    AppIconsTintControlRow(
                        title = stringResource(R.string.app_icons_shape_foreground_tint_title),
                        summary = stringResource(R.string.app_icons_shape_foreground_tint_summary),
                        mode = foregroundTintMode,
                        color = foregroundTintColor,
                        enabled = checked,
                        onModeChange = onForegroundTintModeChange,
                        onColorChange = onForegroundTintColorChange
                    )
                }
            }
            }
        }
    }
}

@Composable
internal fun AppIconsSwitchRow(
    title: String,
    summary: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.48f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.56f),
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            modifier = Modifier.padding(start = 12.dp)
        )
    }
}

@Composable
private fun AppIconsNavigationRow(
    title: String,
    summary: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ) {
            Icon(
                Icons.Rounded.Apps,
                contentDescription = null,
                modifier = Modifier.padding(10.dp)
            )
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null)
    }
}

@Composable
private fun AppIconsTintControlRow(
    title: String,
    summary: String,
    mode: Int,
    color: Int,
    enabled: Boolean,
    onModeChange: (Int) -> Unit,
    onColorChange: (Int) -> Unit
) {
    var showColorPicker by remember { mutableStateOf(false) }
    val swatchBorderColor = if (isSystemInDarkTheme()) Color.White else Color.Black
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.48f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.56f),
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Box(
                modifier = Modifier
                    .padding(start = 12.dp)
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color(color))
                        .border(1.dp, swatchBorderColor, CircleShape)
                    .clickable(enabled = enabled) { showColorPicker = true }
            )
        }
        Row(
            modifier = Modifier.padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AppIconsTintModeButton(
                text = stringResource(R.string.app_icons_tint_mode_off),
                selected = mode == APP_ICON_TINT_MODE_OFF,
                enabled = enabled,
                onClick = { onModeChange(APP_ICON_TINT_MODE_OFF) }
            )
            AppIconsTintModeButton(
                text = stringResource(R.string.app_icons_tint_mode_auto),
                selected = mode == APP_ICON_TINT_MODE_AUTO,
                enabled = enabled,
                onClick = { onModeChange(APP_ICON_TINT_MODE_AUTO) }
            )
            AppIconsTintModeButton(
                text = stringResource(R.string.app_icons_tint_mode_custom),
                selected = mode == APP_ICON_TINT_MODE_CUSTOM,
                enabled = enabled,
                onClick = { onModeChange(APP_ICON_TINT_MODE_CUSTOM) }
            )
        }
    }

    if (showColorPicker) {
        ColorPickerDialog(
            initialColor = color,
            onColorSelected = { selectedColor ->
                showColorPicker = false
                onColorChange(selectedColor)
            },
            onDismissRequest = { showColorPicker = false }
        )
    }
}

@Composable
private fun AppIconsTintModeButton(
    text: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    if (selected) {
        Button(
            onClick = onClick,
            enabled = enabled,
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Text(text = text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Text(text = text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
internal fun AppIconsScaleSliderRow(
    title: String,
    value: Float,
    enabled: Boolean,
    onValueChange: (Float) -> Unit
) {
    SliderSettingFloat(
        title = title,
        value = value,
        range = IconPackManager.MIN_SHAPE_SCALE_PERCENT..IconPackManager.MAX_SHAPE_SCALE_PERCENT,
        unit = "%",
        enabled = enabled,
        onValueChange = { onValueChange(normalizeShapeScale(it)) },
        onDefault = { onValueChange(IconPackManager.DEFAULT_SHAPE_SCALE_PERCENT) },
        valueText = stringResource(R.string.app_icons_shape_scale_value_format, value),
        showDefaultButton = true
    )
    Text(
        text = stringResource(R.string.app_icons_shape_scale_summary),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.56f),
        modifier = Modifier.padding(horizontal = 20.dp)
    )
}

@Composable
internal fun ApplyProgressCard(progress: IconApplyProgress) {
    val progressValue = (progress.percent / 100f).coerceIn(0f, 1f)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!progress.completed) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                }
                Text(
                    text = if (progress.completed) {
                        stringResource(R.string.app_icons_progress_completed)
                    } else {
                        progress.label
                    },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(R.string.app_icons_progress_percent_format, progress.percent),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            LinearProgressIndicator(
                progress = { progressValue },
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = stringResource(
                    R.string.app_icons_progress_counts_format,
                    progress.processed,
                    progress.total,
                    progress.applied,
                    progress.removed,
                    progress.skipped,
                    progress.failed
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
internal fun IconPacksBlock(
    packs: List<IconPackInfo>,
    expanded: Boolean,
    busy: Boolean,
    onExpandChange: (Boolean) -> Unit,
    onApplyAll: (IconPackInfo) -> Unit,
    onApplyPartial: (IconPackInfo) -> Unit,
    onViewPack: (IconPackInfo) -> Unit,
    onApplyUpdate: (IconPackInfo) -> Unit,
    onCancelAll: (IconPackInfo) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ExpandableSectionHeader(
            title = stringResource(R.string.app_icons_packs_section),
            subtitle = stringResource(R.string.app_icons_pack_count_format, packs.size),
            icon = Icons.Rounded.Apps,
            expanded = expanded,
            onExpandChange = onExpandChange
        )

        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (packs.isEmpty()) {
                    EmptyStateText(stringResource(R.string.app_icons_no_icon_packs))
                } else {
                    packs.forEach { pack ->
                        IconPackCard(
                            pack = pack,
                            busy = busy,
                            onApplyAll = { onApplyAll(pack) },
                            onApplyPartial = { onApplyPartial(pack) },
                            onViewPack = { onViewPack(pack) },
                            onApplyUpdate = { onApplyUpdate(pack) },
                            onCancelAll = { onCancelAll(pack) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun ExpandableSectionHeader(
    title: String,
    subtitle: String,
    icon: ImageVector,
    expanded: Boolean,
    onExpandChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        ExpandableSectionHeaderContent(
            title = title,
            subtitle = subtitle,
            icon = icon,
            expanded = expanded,
            onExpandChange = onExpandChange
        )
    }
}

@Composable
internal fun ExpandableSectionHeaderContent(
    title: String,
    subtitle: String,
    icon: ImageVector,
    expanded: Boolean,
    onExpandChange: (Boolean) -> Unit
) {
    val rotation by animateFloatAsState(targetValue = if (expanded) 180f else 0f, label = "section_rotation")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onExpandChange(!expanded) }
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.padding(10.dp))
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 14.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            imageVector = Icons.Rounded.ExpandMore,
            contentDescription = null,
            modifier = Modifier.rotate(rotation),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun IconPackCard(
    pack: IconPackInfo,
    busy: Boolean,
    onApplyAll: () -> Unit,
    onApplyPartial: () -> Unit,
    onViewPack: () -> Unit,
    onApplyUpdate: () -> Unit,
    onCancelAll: () -> Unit
) {
    var actionsExpanded by rememberSaveable(pack.packageName) { mutableStateOf(false) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable { actionsExpanded = !actionsExpanded },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DrawableIcon(drawable = pack.icon, size = 48.dp, fallback = Icons.Rounded.Apps)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp)
                ) {
                    Text(
                        text = pack.label,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = pack.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (pack.installed) {
                            iconPackStatusText(pack.status)
                        } else {
                            stringResource(R.string.app_icons_pack_missing_status)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (pack.installed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                Icon(
                    imageVector = Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.rotate(if (actionsExpanded) 180f else 0f),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (busy) {
                    CircularProgressIndicator(modifier = Modifier.size(26.dp), strokeWidth = 2.dp)
                }
            }

            Text(
                text = stringResource(
                    R.string.app_icons_supported_count_format,
                    pack.supportedPackageCount,
                    pack.supportedIconCount,
                    pack.appliedPackageCount
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (pack.requiresUpdate) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.28f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.app_icons_requires_update),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onApplyUpdate, enabled = !busy) {
                        Text(stringResource(R.string.app_icons_apply_update), maxLines = 1)
                    }
                }
            }

            AnimatedVisibility(
                visible = actionsExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!pack.installed) {
                        Text(
                            text = stringResource(R.string.app_icons_pack_missing_message),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.28f))
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                    if (pack.installed) {
                        Button(
                            onClick = onApplyAll,
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Rounded.Apps, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.app_icons_apply_all), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    OutlinedButton(
                        onClick = onApplyPartial,
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.Search, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.app_icons_apply_partial), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (pack.installed) {
                        OutlinedButton(
                            onClick = onViewPack,
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Rounded.Apps, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.app_icons_view_pack), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    OutlinedButton(
                        onClick = onCancelAll,
                        enabled = !busy && pack.appliedPackageCount > 0,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.Refresh, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.app_icons_cancel_all), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
internal fun AppIconsFilterSwitchRow(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = summary,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = Modifier.padding(start = 12.dp)
            )
        }
    }
}

@Composable
internal fun InstalledAppCard(
    app: InstalledIconApp,
    iconMap: IconMapSnapshot,
    iconPacks: List<IconPackInfo>,
    expanded: Boolean,
    busy: Boolean,
    onExpandChange: (Boolean) -> Unit,
    onRestore: () -> Unit,
    onApply: (AppIconCandidate) -> Unit,
    onImportCustomIcon: (Uri) -> Unit,
    onSaveShapeOverrides: (AppIconShapeOverrides?) -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val previewSizePx = with(density) { 56.dp.roundToPx() }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onImportCustomIcon(uri)
    }
    var loading by remember(app.packageName, iconMap) { mutableStateOf(false) }
    var candidates by remember(app.packageName, iconMap) { mutableStateOf<List<AppIconCandidate>>(emptyList()) }
    var appliedBitmap by remember(app.packageName, iconMap) { mutableStateOf<Bitmap?>(null) }
    var shapeOverrides by remember(app.packageName, iconMap) {
        mutableStateOf(iconMap.shapeOverrides[app.packageName] ?: app.shapeOverrides ?: AppIconShapeOverrides())
    }
    var chooserOpen by rememberSaveable(app.packageName) { mutableStateOf(false) }

    LaunchedEffect(expanded, app.packageName, iconMap) {
        if (!expanded) return@LaunchedEffect
        loading = true
        candidates = IconPackManager.loadCandidatesForApp(context, app.packageName)
        appliedBitmap = IconPackManager.loadAppliedIconBitmap(context, app.packageName, previewSizePx)
        loading = false
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable { onExpandChange(!expanded) },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DrawableIcon(drawable = app.icon, size = 42.dp, fallback = Icons.Rounded.Apps)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp)
                ) {
                    Text(
                        text = app.label,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = app.appliedIconPackLabel?.let {
                            stringResource(R.string.app_icons_applied_from_format, it)
                        } ?: stringResource(R.string.app_icons_not_applied_to_app),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (app.appliedIconPackLabel != null) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                Icon(
                    imageVector = Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.rotate(if (expanded) 180f else 0f),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AppIconCurrentIconCard(app = app, appliedBitmap = appliedBitmap)

                    if (app.appliedIconFileName != null) {
                        OutlinedButton(
                            onClick = onRestore,
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Rounded.Refresh, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.app_icons_restore_default), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }

                    OutlinedButton(
                        onClick = { importLauncher.launch(arrayOf("image/*", "application/xml", "text/xml")) },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.Apps, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.app_icons_import_custom_icon), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }

                    OutlinedButton(
                        onClick = { chooserOpen = true },
                        enabled = !busy && iconPacks.any { it.installed },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.Search, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.app_icons_choose_from_pack), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }

                    PerAppShapeSettingsCard(
                        overrides = shapeOverrides,
                        onOverridesChange = { shapeOverrides = it },
                        onSave = { onSaveShapeOverrides(shapeOverrides.normalizedOrNull()) },
                        onReset = {
                            shapeOverrides = AppIconShapeOverrides()
                            onSaveShapeOverrides(null)
                        }
                    )

                    Text(
                        text = stringResource(R.string.app_icons_available_icons_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )

                    when {
                        loading -> LoadingDialogContent()
                        candidates.isEmpty() -> EmptyStateText(stringResource(R.string.app_icons_no_app_candidates))
                        else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            val appliedSource = iconMap.sources[app.packageName]
                            candidates.forEach { candidate ->
                                val applied = appliedSource?.iconPackPackage == candidate.iconPackPackage &&
                                    appliedSource.drawableName == candidate.drawableName
                                AppIconCandidateRow(
                                    candidate = candidate,
                                    applied = applied,
                                    busy = busy,
                                    onApply = { onApply(candidate) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (chooserOpen) {
        AppIconChooserDialog(
            app = app,
            iconMap = iconMap,
            iconPacks = iconPacks.filter { it.installed },
            onDismiss = { chooserOpen = false },
            onApply = { item ->
                chooserOpen = false
                onApply(AppIconCandidate(item.iconPackPackage, item.iconPackLabel, item.drawableName))
            }
        )
    }
}
