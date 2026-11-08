package org.pixel.extra.parts.thermal

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.pixel.extra.parts.R
import org.pixel.extra.parts.ui.edgeOutline
import org.pixel.extra.parts.ui.sectionContentPadding

// Thermals manager: current profile status, per-axis preset picker and the
// QS tile profile queue with ordering. The screen only reads and writes the
// legacy keys in ThermalKeys; the HAL and config files stay untouched.
@Composable
fun ThermalScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    var snapshot by remember { mutableStateOf<ThermalSnapshot?>(null) }

    fun refresh() {
        scope.launch {
            snapshot = withContext(Dispatchers.IO) { ThermalRepository.load(context) }
        }
    }

    LaunchedEffect(Unit) { refresh() }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = sectionContentPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "__thermal_header__") {
            Text(
                text = stringResource(R.string.tab_thermal),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        val current = snapshot
        when {
            current == null -> {
                item(key = "__thermal_loading__") {
                    ThermalMessageCard(text = stringResource(R.string.thermal_loading))
                }
            }

            !current.available -> {
                item(key = "__thermal_unavailable__") {
                    ThermalUnavailableCard()
                }
            }

            else -> {
                item(key = "__thermal_status__") {
                    ThermalStatusCard(
                        active = current.active,
                        configValue = current.configValue,
                    )
                }
                item(key = "__thermal_battery__") {
                    ThermalAxisCard(
                        axis = ThermalAxis.BATTERY,
                        selected = current.active.battery,
                        onSelect = { level ->
                            haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                            val next = current.active.copy(battery = level)
                            scope.launch(Dispatchers.IO) {
                                ThermalRepository.applyPreset(context, next)
                                snapshot = ThermalRepository.load(context)
                            }
                        },
                    )
                }
                item(key = "__thermal_soc__") {
                    ThermalAxisCard(
                        axis = ThermalAxis.SOC,
                        selected = current.active.soc,
                        onSelect = { level ->
                            haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                            val next = current.active.copy(soc = level)
                            scope.launch(Dispatchers.IO) {
                                ThermalRepository.applyPreset(context, next)
                                snapshot = ThermalRepository.load(context)
                            }
                        },
                    )
                }
                item(key = "__thermal_queue__") {
                    ThermalQueueCard(
                        queue = current.queue,
                        queueIndex = current.queueIndex,
                        activeId = current.active.id,
                        onApply = { presetId, index ->
                            haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                            scope.launch(Dispatchers.IO) {
                                ThermalRepository.applyQueued(context, presetId, index)
                                snapshot = ThermalRepository.load(context)
                            }
                        },
                        onAddActive = {
                            haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                            scope.launch(Dispatchers.IO) {
                                ThermalRepository.addToQueue(context, current.active.id)
                                snapshot = ThermalRepository.load(context)
                            }
                        },
                        onRemove = { index ->
                            haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                            scope.launch(Dispatchers.IO) {
                                ThermalRepository.removeAt(context, index)
                                snapshot = ThermalRepository.load(context)
                            }
                        },
                        onMove = { fromIndex, toIndex ->
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            scope.launch(Dispatchers.IO) {
                                ThermalRepository.move(context, fromIndex, toIndex)
                                snapshot = ThermalRepository.load(context)
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun levelLabel(level: ThermalLevel): String {
    val name = stringResource(level.labelRes)
    return if (level.offsetC > 0) {
        stringResource(R.string.thermal_level_with_offset, name, level.offsetC)
    } else {
        name
    }
}

@Composable
private fun axisLevelLabel(axis: ThermalAxis, level: ThermalLevel): String {
    return stringResource(
        R.string.thermal_axis_level_format,
        stringResource(axis.labelRes),
        levelLabel(level),
    )
}

@Composable
private fun ThermalMessageCard(text: String, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(20.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.surfaceContainerLow.edgeOutline(),
                shape = RoundedCornerShape(20.dp),
            ),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
        )
    }
}

@Composable
private fun ThermalUnavailableCard(modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(24.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.surfaceContainerLow.edgeOutline(),
                shape = RoundedCornerShape(24.dp),
            ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(R.drawable.ic_info),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.thermal_unavailable_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(R.string.thermal_unavailable_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ThermalStatusCard(
    active: ThermalPreset,
    configValue: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(24.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.surfaceContainerLow.edgeOutline(),
                shape = RoundedCornerShape(24.dp),
            ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(R.drawable.ic_share),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.thermal_status_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = axisLevelLabel(ThermalAxis.BATTERY, active.battery),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = axisLevelLabel(ThermalAxis.SOC, active.soc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = configValue,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (active.isRisky) {
                    Text(
                        text = stringResource(R.string.thermal_risky_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Text(
                    text = stringResource(R.string.thermal_reboot_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ThermalAxisCard(
    axis: ThermalAxis,
    selected: ThermalLevel,
    onSelect: (ThermalLevel) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(20.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.surfaceContainerLow.edgeOutline(),
                shape = RoundedCornerShape(20.dp),
            ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(axis.labelRes),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 2.dp),
            )
            ThermalLevel.entries.forEach { level ->
                ThermalLevelRow(
                    level = level,
                    isSelected = level == selected,
                    onClick = { onSelect(level) },
                )
            }
        }
    }
}

@Composable
private fun ThermalLevelRow(
    level: ThermalLevel,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        animationSpec = spring(
            stiffness = Spring.StiffnessMedium,
            dampingRatio = Spring.DampingRatioNoBouncy,
        ),
        label = "ThermalLevelBg",
    )
    val contentColor = if (isSelected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Surface(
        color = container,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = container.edgeOutline(),
                shape = RoundedCornerShape(12.dp),
            )
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        Text(
            text = levelLabel(level),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = contentColor,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun ThermalQueueCard(
    queue: List<String>,
    queueIndex: Int,
    activeId: String,
    onApply: (String, Int) -> Unit,
    onAddActive: () -> Unit,
    onRemove: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val canAdd = activeId !in queue && queue.size < ThermalKeys.MAX_TILE_QUEUE_SIZE
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(20.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.surfaceContainerLow.edgeOutline(),
                shape = RoundedCornerShape(20.dp),
            ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.thermal_queue_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(
                        R.string.thermal_queue_count_format,
                        queue.size,
                        ThermalKeys.MAX_TILE_QUEUE_SIZE,
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.primaryContainer.edgeOutline(),
                            shape = RoundedCornerShape(10.dp),
                        )
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
            if (queue.isEmpty()) {
                Text(
                    text = stringResource(R.string.thermal_queue_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 2.dp),
                )
            }
            queue.forEachIndexed { index, presetId ->
                ThermalQueueRow(
                    preset = ThermalPreset.fromId(presetId),
                    isMarked = index == queueIndex,
                    onApply = { onApply(presetId, index) },
                    onMoveUp = { onMove(index, index - 1) },
                    onMoveDown = { onMove(index, index + 1) },
                    canMoveUp = index > 0,
                    canMoveDown = index < queue.lastIndex,
                    onRemove = { onRemove(index) },
                )
            }
            FilledTonalButton(
                onClick = onAddActive,
                enabled = canAdd,
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.secondaryContainer.edgeOutline(),
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = stringResource(R.string.thermal_queue_add_current))
            }
        }
    }
}

@Composable
private fun ThermalQueueRow(
    preset: ThermalPreset,
    isMarked: Boolean,
    onApply: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = if (isMarked) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }
    val contentColor = if (isMarked) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Surface(
        color = container,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = container.edgeOutline(),
                shape = RoundedCornerShape(12.dp),
            )
            .clickable(role = Role.Button, onClick = onApply),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = axisLevelLabel(ThermalAxis.BATTERY, preset.battery),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = contentColor,
                    )
                    Text(
                        text = axisLevelLabel(ThermalAxis.SOC, preset.soc),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isMarked) {
                            contentColor
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                IconButton(onClick = onRemove) {
                    Icon(
                        painter = painterResource(R.drawable.ic_delete),
                        contentDescription = stringResource(R.string.thermal_queue_remove),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                TextButton(onClick = onMoveUp, enabled = canMoveUp) {
                    Text(text = stringResource(R.string.thermal_queue_move_up))
                }
                TextButton(onClick = onMoveDown, enabled = canMoveDown) {
                    Text(text = stringResource(R.string.thermal_queue_move_down))
                }
            }
        }
    }
}
