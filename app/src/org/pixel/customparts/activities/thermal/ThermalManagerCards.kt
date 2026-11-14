package org.pixel.customparts.activities

import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.pixel.customparts.R
import org.pixel.customparts.ui.SettingsGroupCard
import org.pixel.customparts.ui.edgeOutline
import org.pixel.customparts.utils.ThermalConfigChoice
import org.pixel.customparts.utils.ThermalProfileController
import java.util.Locale

// ── Live SoC Telemetry Hero Card ─────────────────────────────────────────

@Composable
internal fun ThermalLiveTelemetryCard(
    socModel: String,
    temperatureC: Float?,
    activeGlobalChoice: ThermalConfigChoice,
    assignedAppsCount: Int
) {
    val tempVal = temperatureC ?: 36f
    val normalizedHeat by animateFloatAsState(
        targetValue = ((tempVal - 25f) / 45f).coerceIn(0.06f, 1f),
        animationSpec = spring(
            stiffness = Spring.StiffnessMediumLow,
            dampingRatio = Spring.DampingRatioNoBouncy
        ),
        label = "ThermalHeatGauge"
    )

    val tempAccent by animateColorAsState(
        targetValue = when {
            tempVal >= 52f -> MaterialTheme.colorScheme.error
            tempVal >= 43f -> MaterialTheme.colorScheme.tertiary
            else -> MaterialTheme.colorScheme.primary
        },
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "ThermalHeatColor"
    )

    val cardBg = MaterialTheme.colorScheme.surface
    Card(
        colors = CardDefaults.cardColors(containerColor = cardBg),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, cardBg.edgeOutline()),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(tempAccent.copy(alpha = 0.15f))
                        .border(1.dp, tempAccent.edgeOutline(alpha = 0.45f), RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Memory,
                        contentDescription = null,
                        tint = tempAccent,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = socModel,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = profileChoiceTitle(activeGlobalChoice),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Text(
                    text = temperatureC?.let { String.format(Locale.US, "%.1f°C", it) }
                        ?: stringResource(R.string.auto_hbm_temperature_unknown),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = tempAccent
                )
            }

            // Thermal Heat Spectrum Bar
            val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
            val coolColor = MaterialTheme.colorScheme.primary
            val warmColor = MaterialTheme.colorScheme.tertiary
            val hotColor = MaterialTheme.colorScheme.error

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CircleShape)
            ) {
                val r = size.height / 2f
                drawRoundRect(
                    color = trackColor,
                    size = size,
                    cornerRadius = CornerRadius(r, r)
                )
                val fillW = (size.width * normalizedHeat).coerceAtLeast(size.height)
                drawRoundRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(coolColor, warmColor, hotColor),
                        startX = 0f,
                        endX = size.width
                    ),
                    topLeft = Offset.Zero,
                    size = Size(fillW, size.height),
                    cornerRadius = CornerRadius(r, r)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                ProfileTypeBadge(activeGlobalChoice)
                Text(
                    text = "${stringResource(R.string.thermal_manager_apps_title)}: $assignedAppsCount",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

// ── Saved User & System Config Cards (Inline Actions + 2-Tap Trash) ──────

@Composable
internal fun ProfileConfigSectionCard(
    title: String,
    configs: List<SavedThermalConfig>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onImport: (() -> Unit)?,
    onCreate: (() -> Unit)?,
    emptyText: String,
    onEdit: (SavedThermalConfig) -> Unit,
    onClone: (SavedThermalConfig) -> Unit,
    onExport: (SavedThermalConfig) -> Unit,
    onDelete: (SavedThermalConfig) -> Unit
) {
    val haptics = LocalHapticFeedback.current
    val armedDeletes = remember { mutableStateMapOf<String, Long>() }
    val cardBg = MaterialTheme.colorScheme.surface

    Card(
        colors = CardDefaults.cardColors(containerColor = cardBg),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, cardBg.edgeOutline()),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                    onExpandedChange(!expanded)
                }
                .padding(start = 20.dp, top = 14.dp, end = 12.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = if (expanded) {
                        stringResource(R.string.thermal_manager_saved_count, configs.size)
                    } else {
                        stringResource(R.string.thermal_manager_profiles_collapsed)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (onImport != null) {
                IconButton(onClick = onImport) {
                    Icon(
                        Icons.Rounded.UploadFile,
                        contentDescription = stringResource(R.string.thermal_manager_import),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            if (onCreate != null) {
                IconButton(onClick = onCreate) {
                    Icon(
                        Icons.Rounded.Add,
                        contentDescription = stringResource(R.string.thermal_manager_create_config),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            IconButton(onClick = { onExpandedChange(!expanded) }) {
                Icon(
                    if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(
                animationSpec = spring(
                    stiffness = Spring.StiffnessMediumLow,
                    dampingRatio = Spring.DampingRatioNoBouncy
                )
            ) + fadeIn(),
            exit = shrinkVertically(
                animationSpec = spring(
                    stiffness = Spring.StiffnessMediumLow,
                    dampingRatio = Spring.DampingRatioNoBouncy
                )
            ) + fadeOut()
        ) {
            Column(modifier = Modifier.padding(bottom = 12.dp)) {
                if (configs.isEmpty()) {
                    Column(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = emptyText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (onCreate != null) {
                            SecondaryActionPill(
                                icon = Icons.Rounded.Add,
                                label = stringResource(R.string.thermal_manager_create_config),
                                enabled = true,
                                onClick = onCreate,
                                modifier = Modifier.fillMaxWidth(),
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                } else {
                    configs.forEachIndexed { index, config ->
                        if (index > 0) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 20.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                            )
                        }
                        val armedTime = armedDeletes[config.name] ?: 0L
                        val isArmed = armedTime > 0L

                        LaunchedEffect(armedTime) {
                            if (armedTime <= 0L) return@LaunchedEffect
                            delay(3000L)
                            if (armedDeletes[config.name] == armedTime) {
                                armedDeletes.remove(config.name)
                            }
                        }

                        SavedConfigInlineRow(
                            config = config,
                            trashArmed = isArmed,
                            onEdit = { onEdit(config) },
                            onClone = { onClone(config) },
                            onExport = { onExport(config) },
                            onTrashClick = {
                                if (isArmed) {
                                    armedDeletes.remove(config.name)
                                    onDelete(config)
                                } else {
                                    armedDeletes[config.name] = SystemClock.uptimeMillis()
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SavedConfigInlineRow(
    config: SavedThermalConfig,
    trashArmed: Boolean,
    onEdit: () -> Unit,
    onClone: () -> Unit,
    onExport: () -> Unit,
    onTrashClick: () -> Unit
) {
    val haptics = LocalHapticFeedback.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                if (config.locked) onClone() else onEdit()
            }
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = config.displayName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                modifier = Modifier.padding(top = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = config.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = config.sizeText,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1
                )
            }
        }

        Spacer(Modifier.width(8.dp))

        // Inline Action Chips (Clone, Export, Edit, 2-Tap Delete)
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SmallIconActionChip(
                icon = Icons.Rounded.ContentCopy,
                contentDescription = stringResource(R.string.thermal_manager_create_from),
                onClick = onClone
            )
            SmallIconActionChip(
                icon = Icons.Rounded.Download,
                contentDescription = stringResource(R.string.thermal_manager_export),
                onClick = onExport
            )
            if (!config.locked) {
                SmallIconActionChip(
                    icon = Icons.Rounded.Edit,
                    contentDescription = stringResource(R.string.thermal_manager_edit),
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    onClick = onEdit
                )

                val trashCorner by animateFloatAsState(
                    targetValue = if (trashArmed) 18f else 10f,
                    animationSpec = spring(
                        stiffness = Spring.StiffnessMedium,
                        dampingRatio = Spring.DampingRatioNoBouncy
                    ),
                    label = "ThermalConfigTrashCorner"
                )
                val trashBg = MaterialTheme.colorScheme.errorContainer
                val trashShape = RoundedCornerShape(trashCorner.dp)
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(trashShape)
                        .background(trashBg)
                        .border(1.dp, trashBg.edgeOutline(), trashShape)
                        .clickable {
                            haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                            onTrashClick()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (trashArmed) Icons.Rounded.Check else Icons.Rounded.Delete,
                        contentDescription = stringResource(R.string.thermal_manager_delete),
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SmallIconActionChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    containerColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    contentColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onClick: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(shape)
            .background(containerColor)
            .border(1.dp, containerColor.edgeOutline(), shape)
            .clickable {
                haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = contentColor,
            modifier = Modifier.size(17.dp)
        )
    }
}

// ── Global Profile & QS Tile Queue Cards (1:1 Flight Ready) ──────────────

@Composable
internal fun GlobalProfileCard(
    selectedChoice: ThermalConfigChoice,
    isHiddenForFlight: Boolean = false,
    onOpenFlight: (Rect, () -> Rect) -> Unit,
    onCreateProfile: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    var rowBounds by remember { mutableStateOf(Rect.Zero) }
    val cardBg = MaterialTheme.colorScheme.surface

    Card(
        colors = CardDefaults.cardColors(containerColor = cardBg),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, cardBg.edgeOutline()),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, top = 14.dp, end = 16.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.thermal_manager_global_profile),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            SecondaryActionPill(
                icon = Icons.Rounded.Add,
                label = stringResource(R.string.thermal_manager_create_profile_short),
                enabled = true,
                onClick = onCreateProfile,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }

        val selectorBg = MaterialTheme.colorScheme.surfaceContainerHigh
        val selectorShape = RoundedCornerShape(18.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 16.dp)
                .onPlaced {
                    val r = it.unclippedBoundsInRoot()
                    if (r.width > 1f && r.height > 1f) rowBounds = r
                }
                .onGloballyPositioned {
                    val r = it.unclippedBoundsInRoot()
                    if (r.width > 1f && r.height > 1f) rowBounds = r
                }
                .alpha(if (isHiddenForFlight) 0f else 1f)
                .clip(selectorShape)
                .background(selectorBg)
                .border(1.dp, selectorBg.edgeOutline(), selectorShape)
                .clickable(enabled = !isHiddenForFlight) {
                    haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                    onOpenFlight(rowBounds) { rowBounds }
                }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp)
            ) {
                Text(
                    text = profileChoiceTitle(selectedChoice),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                ProfileChoiceDetails(choice = selectedChoice, showPath = true)
            }
            Icon(
                Icons.Rounded.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
internal fun ThermalTileQueueCard(
    queue: List<String>,
    choicesById: Map<String, ThermalConfigChoice>,
    isAddHiddenForFlight: Boolean = false,
    onOpenAddFlight: (Rect, () -> Rect) -> Unit,
    onMoveUp: (Int) -> Unit,
    onMoveDown: (Int) -> Unit,
    onRemove: (Int) -> Unit
) {
    var addBtnBounds by remember { mutableStateOf(Rect.Zero) }

    SettingsGroupCard(title = stringResource(R.string.thermal_manager_tile_queue)) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(
                        R.string.thermal_manager_tile_queue_count,
                        queue.size,
                        ThermalProfileController.MAX_TILE_QUEUE_SIZE
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier
                        .onPlaced {
                            val r = it.unclippedBoundsInRoot()
                            if (r.width > 1f && r.height > 1f) addBtnBounds = r
                        }
                        .onGloballyPositioned {
                            val r = it.unclippedBoundsInRoot()
                            if (r.width > 1f && r.height > 1f) addBtnBounds = r
                        }
                        .alpha(if (isAddHiddenForFlight) 0f else 1f)
                ) {
                    SecondaryActionPill(
                        icon = Icons.Rounded.Add,
                        label = stringResource(R.string.thermal_manager_tile_queue_add),
                        enabled = !isAddHiddenForFlight && queue.size < ThermalProfileController.MAX_TILE_QUEUE_SIZE,
                        onClick = { onOpenAddFlight(addBtnBounds) { addBtnBounds } },
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            if (queue.isEmpty()) {
                Text(
                    text = stringResource(R.string.thermal_manager_tile_queue_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 6.dp)
                )
            } else {
                queue.forEachIndexed { index, configId ->
                    ThermalTileQueueRow(
                        index = index,
                        choice = choiceForConfig(configId, choicesById, includeFollowGlobal = false),
                        isFirst = index == 0,
                        isLast = index == queue.lastIndex,
                        onMoveUp = { onMoveUp(index) },
                        onMoveDown = { onMoveDown(index) },
                        onRemove = { onRemove(index) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ThermalTileQueueRow(
    index: Int,
    choice: ThermalConfigChoice,
    isFirst: Boolean,
    isLast: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit
) {
    val rowBg = MaterialTheme.colorScheme.surfaceContainerHigh
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, rowBg.edgeOutline(), RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        color = rowBg
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, top = 10.dp, end = 8.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = (index + 1).toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            ) {
                Text(
                    text = profileChoiceTitle(choice),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                ProfileChoiceDetails(choice = choice, showPath = false)
            }
            IconButton(
                onClick = onMoveUp,
                enabled = !isFirst,
                modifier = Modifier.size(34.dp)
            ) {
                Icon(
                    Icons.Rounded.ExpandLess,
                    contentDescription = stringResource(R.string.thermal_manager_tile_queue_move_up),
                    modifier = Modifier.size(20.dp)
                )
            }
            IconButton(
                onClick = onMoveDown,
                enabled = !isLast,
                modifier = Modifier.size(34.dp)
            ) {
                Icon(
                    Icons.Rounded.ExpandMore,
                    contentDescription = stringResource(R.string.thermal_manager_tile_queue_move_down),
                    modifier = Modifier.size(20.dp)
                )
            }
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(34.dp)
            ) {
                Icon(
                    Icons.Rounded.Delete,
                    contentDescription = stringResource(R.string.thermal_manager_tile_queue_remove),
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(19.dp)
                )
            }
        }
    }
}

@Composable
private fun ProfileChoiceDetails(
    choice: ThermalConfigChoice,
    showPath: Boolean,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(
            modifier = Modifier.padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ProfileTypeBadge(choice)
            choice.fileName?.takeIf { it.isNotBlank() }?.let { fileName ->
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.thermal_manager_profile_file, fileName),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (showPath) {
            choice.propertyValue?.takeIf { it.startsWith('/') }?.let { path ->
                Text(
                    text = stringResource(R.string.thermal_manager_profile_path, path),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
internal fun ProfileTypeBadge(choice: ThermalConfigChoice) {
    val isSystem = choice.builtIn || choice.source == ThermalProfileController.PROFILE_SOURCE_SYSTEM
    val bgColor = if (isSystem) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(bgColor)
            .border(1.dp, bgColor.edgeOutline(), CircleShape)
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(
            text = if (isSystem) {
                stringResource(R.string.thermal_manager_profile_system)
            } else {
                stringResource(R.string.thermal_manager_profile_user)
            },
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = if (isSystem) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSecondaryContainer
            },
            maxLines = 1
        )
    }
}