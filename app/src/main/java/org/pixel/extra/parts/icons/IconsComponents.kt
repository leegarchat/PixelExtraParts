package org.pixel.extra.parts.icons

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.pixel.extra.parts.R
import org.pixel.extra.parts.ui.ExpressiveSegmentedRow
import org.pixel.extra.parts.ui.SettingSlider
import org.pixel.extra.parts.ui.SettingSwitchRow
import org.pixel.extra.parts.ui.SettingSwitchRowVariant
import org.pixel.extra.parts.ui.edgeOutline

// Per-area stretch/remove/scale block inside the appearance card.
@Composable
internal fun IconsAreaBlock(
    areaTitle: String,
    stretch: Boolean,
    remove: Boolean,
    scale: Float,
    onStretchChange: (Boolean) -> Unit,
    onRemoveChange: (Boolean) -> Unit,
    onScaleChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = areaTitle,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
        SettingSwitchRow(
            title = stringResource(R.string.icons_stretch_title),
            summary = stringResource(R.string.icons_stretch_sub),
            checked = stretch,
            onCheckedChange = onStretchChange,
            variant = SettingSwitchRowVariant.Card,
        )
        SettingSwitchRow(
            title = stringResource(R.string.icons_remove_title),
            summary = stringResource(R.string.icons_remove_sub),
            checked = remove,
            onCheckedChange = onRemoveChange,
            variant = SettingSwitchRowVariant.Card,
        )
        SettingSlider(
            title = stringResource(R.string.icons_scale_title),
            value = scale,
            onValueChange = onScaleChange,
            valueRange = IconsStore.SCALE_MIN..IconsStore.SCALE_MAX,
            step = 1f,
            defaultValue = IconsStore.SCALE_DEFAULT,
            resetLabel = stringResource(R.string.icons_reset),
            valueLabel = { value ->
                stringResource(R.string.icons_scale_value, value)
            },
        )
    }
}

// Tint mode block: segmented Off/Custom/Auto plus a swatch grid in custom mode.
@Composable
internal fun IconsTintBlock(
    title: String,
    summary: String,
    mode: Int,
    selectedColor: Int,
    onModeChange: (Int) -> Unit,
    onColorChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = summary,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ExpressiveSegmentedRow(
            items = listOf(
                IconsStore.TINT_MODE_OFF,
                IconsStore.TINT_MODE_CUSTOM,
                IconsStore.TINT_MODE_AUTO,
            ),
            selectedItem = mode,
            onSelect = onModeChange,
            labelProvider = { item ->
                when (item) {
                    IconsStore.TINT_MODE_CUSTOM -> stringResource(R.string.icons_tint_custom)
                    IconsStore.TINT_MODE_AUTO -> stringResource(R.string.icons_tint_auto)
                    else -> stringResource(R.string.icons_tint_off)
                }
            },
        )
        AnimatedVisibility(
            visible = mode == IconsStore.TINT_MODE_CUSTOM,
            enter = fadeIn(tween(180)) + expandVertically(tween(220)),
            exit = fadeOut(tween(140)) + shrinkVertically(tween(200)),
        ) {
            IconsSwatchGrid(
                selectedColor = selectedColor,
                onSelect = onColorChange,
            )
        }
    }
}


// Fixed palette grid: tap writes the color and forces Custom mode upstream.
@Composable
internal fun IconsSwatchGrid(
    selectedColor: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        IconsStore.TINT_SWATCHES.chunked(5).forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                row.forEach { color ->
                    val isSelected = color == selectedColor
                    val shape = CircleShape
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(shape)
                            .background(androidx.compose.ui.graphics.Color(color))
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                },
                                shape = shape,
                            )
                            .clickable(
                                role = Role.RadioButton,
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                                    onSelect(color)
                                },
                            ),
                    )
                }
            }
        }
    }
}

// Shape preset row with a live path preview and an Active pill on selection.
@Composable
internal fun IconsPresetRow(
    label: String,
    pathData: String,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val shape = RoundedCornerShape(16.dp)
    val bg = MaterialTheme.colorScheme.surfaceContainer
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(bg)
            .border(1.dp, bg.edgeOutline(), shape)
            .clickable(
                role = Role.RadioButton,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                    onSelect()
                },
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (pathData.isBlank()) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                )
            }
        } else {
            IconsShapePreview(
                pathData = pathData,
                active = selected,
                fillColor = (if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.secondaryContainer
                }).toArgb(),
                strokeColor = (if (selected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.outline
                }).toArgb(),
                modifier = Modifier.size(44.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (selected) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = CircleShape,
            ) {
                Text(
                    text = stringResource(R.string.icons_shape_active),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
    }
}

// Icon-pack picker row: label, package summary and an Active pill on selection.
@Composable
internal fun IconsPackRow(
    label: String,
    summary: String,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val shape = RoundedCornerShape(16.dp)
    val bg = MaterialTheme.colorScheme.surfaceContainer
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(bg)
            .border(1.dp, bg.edgeOutline(), shape)
            .clickable(
                role = Role.RadioButton,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                    onSelect()
                },
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(12.dp))
        if (selected) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = CircleShape,
            ) {
                Text(
                    text = stringResource(R.string.icons_shape_active),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
    }
}
