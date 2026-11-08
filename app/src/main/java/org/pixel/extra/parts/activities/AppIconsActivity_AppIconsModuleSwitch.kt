package org.pixel.extra.parts.activities

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
import androidx.compose.runtime.derivedStateOf
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
import org.pixel.extra.parts.R
import org.pixel.extra.parts.SettingsKeys
import org.pixel.extra.parts.dynamicDarkColorScheme
import org.pixel.extra.parts.dynamicLightColorScheme
import org.pixel.extra.parts.icons.AppIconCandidate
import org.pixel.extra.parts.icons.AppIconShapeOverrides
import org.pixel.extra.parts.icons.IconApplyMode
import org.pixel.extra.parts.icons.IconApplyProgress
import org.pixel.extra.parts.icons.IconApplyResult
import org.pixel.extra.parts.icons.IconDashboardState
import org.pixel.extra.parts.icons.IconMapSnapshot
import org.pixel.extra.parts.icons.IconPackApplyStatus
import org.pixel.extra.parts.icons.IconPackEntry
import org.pixel.extra.parts.icons.IconPackInfo
import org.pixel.extra.parts.icons.IconPackManager
import org.pixel.extra.parts.icons.IconPackPreviewItem
import org.pixel.extra.parts.icons.IconShapeAreaConfig
import org.pixel.extra.parts.icons.InstalledIconApp
import org.pixel.extra.parts.ui.legacy.REBOOT_BUBBLE_CONTENT_BOTTOM_PADDING
import org.pixel.extra.parts.ui.legacy.ColorPickerDialog
import org.pixel.extra.parts.ui.legacy.RebootBubble
import org.pixel.extra.parts.ui.legacy.RebootBubbleMenuAction
import org.pixel.extra.parts.ui.legacy.SettingsGroupCard
import org.pixel.extra.parts.ui.legacy.SliderSettingFloat
import org.pixel.extra.parts.ui.TopBarBlurOverlay
import org.pixel.extra.parts.ui.legacy.performRebootSystem
import org.pixel.extra.parts.ui.recordLayer
import org.pixel.extra.parts.ui.rememberGraphicsLayerRecordingState
import org.pixel.extra.parts.utils.SettingsCompat
import java.util.Locale
import androidx.compose.ui.res.stringResource


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
                        AppIconsScaleSliderRow(
                            title = stringResource(R.string.app_icons_system_shape_scale_title),
                            value = systemShapeScalePercent,
                            enabled = systemOptionsEnabled,
                            onValueChange = onSystemShapeScaleChange
                        )
                    }
                    AppIconsSwitchRow(
                        title = stringResource(R.string.app_icons_system_remove_shape_title),
                        summary = stringResource(R.string.app_icons_system_remove_shape_summary),
                        checked = systemOptionsEnabled && systemRemoveShapeChecked,
                        enabled = systemOptionsEnabled,
                        onCheckedChange = onSystemRemoveShapeChange
                    )
                    AppIconsSwitchRow(
                        title = stringResource(R.string.app_icons_notification_stretch_shape_title),
                        summary = stringResource(R.string.app_icons_notification_stretch_shape_summary),
                        checked = systemOptionsEnabled && notificationStretchShapeChecked,
                        enabled = systemOptionsEnabled,
                        onCheckedChange = onNotificationStretchShapeChange
                    )
                    if (notificationStretchShapeChecked) {
                        AppIconsScaleSliderRow(
                            title = stringResource(R.string.app_icons_notification_shape_scale_title),
                            value = notificationShapeScalePercent,
                            enabled = systemOptionsEnabled,
                            onValueChange = onNotificationShapeScaleChange
                        )
                    }
                    AppIconsSwitchRow(
                        title = stringResource(R.string.app_icons_notification_remove_shape_title),
                        summary = stringResource(R.string.app_icons_notification_remove_shape_summary),
                        checked = systemOptionsEnabled && notificationRemoveShapeChecked,
                        enabled = systemOptionsEnabled,
                        onCheckedChange = onNotificationRemoveShapeChange
                    )
                    if (showLauncherControls) {
                        AppIconsSwitchRow(
                            title = stringResource(R.string.app_icons_launcher_stretch_shape_title),
                            summary = stringResource(R.string.app_icons_launcher_stretch_shape_summary),
                            checked = launcherOptionsEnabled && launcherStretchShapeChecked,
                            enabled = launcherOptionsEnabled,
                            onCheckedChange = onLauncherStretchShapeChange
                        )
                        if (launcherStretchShapeChecked) {
                            AppIconsScaleSliderRow(
                                title = stringResource(R.string.app_icons_launcher_shape_scale_title),
                                value = launcherShapeScalePercent,
                                enabled = launcherOptionsEnabled,
                                onValueChange = onLauncherShapeScaleChange
                            )
                        }
                        AppIconsSwitchRow(
                            title = stringResource(R.string.app_icons_launcher_remove_shape_title),
                            summary = stringResource(R.string.app_icons_launcher_remove_shape_summary),
                            checked = launcherOptionsEnabled && launcherRemoveShapeChecked,
                            enabled = launcherOptionsEnabled,
                            onCheckedChange = onLauncherRemoveShapeChange
                        )
                    }
                    AppIconsTintControlRow(
                        title = stringResource(R.string.app_icons_shape_background_tint_title),
                        summary = stringResource(R.string.app_icons_shape_background_tint_summary),
                        mode = backgroundTintMode,
                        color = backgroundTintColor,
                        enabled = checked,
                        onModeChange = onBackgroundTintModeChange,
                        onColorChange = onBackgroundTintColorChange
                    )
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
            .padding(horizontal = 16.dp, vertical = 10.dp),
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
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
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
internal fun AppIconsNavigationRow(
    title: String,
    summary: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
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
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null)
    }
}

@Composable
internal fun AppIconsTintControlRow(
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
            .padding(horizontal = 16.dp, vertical = 10.dp)
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
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
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
internal fun AppIconsTintModeButton(
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
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
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
                .padding(16.dp),
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
                progress = progressValue,
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
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
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
                    AppIconsEmptyStateText(stringResource(R.string.app_icons_no_icon_packs))
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
            .padding(horizontal = 16.dp, vertical = 14.dp),
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
internal fun IconPackCard(
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
                .padding(14.dp),
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
                .padding(horizontal = 14.dp, vertical = 10.dp),
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