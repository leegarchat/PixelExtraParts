package org.pixel.extra.parts.systemui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.pixel.extra.parts.R
import org.pixel.extra.parts.ui.ExpandableGroupCard
import org.pixel.extra.parts.ui.ExpressiveSegmentedRow
import org.pixel.extra.parts.ui.SettingSlider
import org.pixel.extra.parts.ui.SettingSwitchRow
import org.pixel.extra.parts.ui.SettingSwitchRowVariant
import org.pixel.extra.parts.ui.edgeOutline

// Magnifier shape values mirror the legacy activity (0 default, 1 square, 2 circle).
private const val SHAPE_DEFAULT = 0
private const val SHAPE_SQUARE = 1
private const val SHAPE_CIRCLE = 2

private const val DEFAULT_ZOOM = 1.25f
private const val DEFAULT_SIZE = 1.0f
private const val DEFAULT_OFFSET_Y = 0

// Hub group for the text loupe: enable switch plus zoom/size sliders,
// shape segmented row, vertical-offset slider and a live lens preview.
// State is read once from Settings.Global and written back on every change.
@Composable
internal fun MagnifierGroup(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var enabled by remember {
        mutableStateOf(readGlobalInt(context, SystemUiKeys.MAGNIFIER_ENABLED, 0) == 1)
    }
    var zoom by remember {
        mutableFloatStateOf(readGlobalFloat(context, SystemUiKeys.MAGNIFIER_ZOOM, DEFAULT_ZOOM))
    }
    var sizeScale by remember {
        mutableFloatStateOf(readGlobalFloat(context, SystemUiKeys.MAGNIFIER_SIZE, DEFAULT_SIZE))
    }
    var shape by remember {
        mutableIntStateOf(readGlobalInt(context, SystemUiKeys.MAGNIFIER_SHAPE, SHAPE_DEFAULT))
    }
    var offsetY by remember {
        mutableIntStateOf(readGlobalInt(context, SystemUiKeys.MAGNIFIER_OFFSET_Y, DEFAULT_OFFSET_Y))
    }

    val subtitle = stringResource(
        R.string.sysui_mag_status,
        stringResource(R.string.sysui_fmt_zoom, zoom),
        stringResource(R.string.sysui_fmt_zoom, sizeScale),
        stringResource(magnifierShapeLabel(shape)),
    )

    val shapeOptions = listOf(
        SegmentOption(SHAPE_DEFAULT, R.string.sysui_mag_shape_default),
        SegmentOption(SHAPE_SQUARE, R.string.sysui_mag_shape_square),
        SegmentOption(SHAPE_CIRCLE, R.string.sysui_mag_shape_circle),
    )

    ExpandableGroupCard(
        title = stringResource(R.string.sysui_magnifier_title),
        chevronPainter = painterResource(R.drawable.ic_chevron_down),
        subtitle = subtitle,
        keyTag = "systemui_magnifier",
        modifier = modifier,
    ) {
        SettingSwitchRow(
            title = stringResource(R.string.sysui_mag_enabled_title),
            summary = stringResource(R.string.sysui_mag_enabled_desc),
            checked = enabled,
            onCheckedChange = {
                enabled = it
                writeGlobalInt(context, SystemUiKeys.MAGNIFIER_ENABLED, if (it) 1 else 0)
            },
            variant = SettingSwitchRowVariant.Card,
        )
        // Disabled tunables keep their layout and only lose input through the
        // shared slider eater; the lens preview stays visible, dimmed.
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SettingSlider(
                title = stringResource(R.string.sysui_mag_zoom_title),
                value = zoom,
                onValueChange = {
                    zoom = it
                    writeGlobalFloat(context, SystemUiKeys.MAGNIFIER_ZOOM, it)
                },
                valueRange = 0.5f..4.0f,
                step = 0.05f,
                defaultValue = DEFAULT_ZOOM,
                resetLabel = stringResource(R.string.motion_reset),
                valueLabel = { v -> stringResource(R.string.sysui_fmt_zoom, v) },
                enabled = enabled,
            )
            SettingSlider(
                title = stringResource(R.string.sysui_mag_size_title),
                value = sizeScale,
                onValueChange = {
                    sizeScale = it
                    writeGlobalFloat(context, SystemUiKeys.MAGNIFIER_SIZE, it)
                },
                valueRange = 0.5f..3.0f,
                step = 0.05f,
                defaultValue = DEFAULT_SIZE,
                resetLabel = stringResource(R.string.motion_reset),
                valueLabel = { v -> stringResource(R.string.sysui_fmt_zoom, v) },
                enabled = enabled,
            )
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(R.string.sysui_mag_shape_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                ExpressiveSegmentedRow(
                    items = shapeOptions,
                    selectedItem = shapeOptions.firstOrNull { it.value == shape }
                        ?: shapeOptions.first(),
                    onSelect = {
                        if (enabled) {
                            shape = it.value
                            writeGlobalInt(context, SystemUiKeys.MAGNIFIER_SHAPE, it.value)
                        }
                    },
                    labelProvider = { stringResource(it.labelRes) },
                )
            }
            SettingSlider(
                title = stringResource(R.string.sysui_mag_offset_title),
                value = offsetY.toFloat(),
                onValueChange = {
                    offsetY = it.toInt()
                    writeGlobalInt(context, SystemUiKeys.MAGNIFIER_OFFSET_Y, offsetY)
                },
                valueRange = -200f..200f,
                step = 1f,
                defaultValue = DEFAULT_OFFSET_Y.toFloat(),
                resetLabel = stringResource(R.string.motion_reset),
                valueLabel = { v -> stringResource(R.string.sysui_fmt_dp, v.toInt()) },
                enabled = enabled,
            )
            Box(modifier = Modifier.alpha(if (enabled) 1f else 0.42f)) {
                MagnifierLensPreview(
                    zoom = zoom,
                    sizeScale = sizeScale,
                    shape = shape,
                    offsetY = offsetY,
                )
            }
        }
    }
}

private fun magnifierShapeLabel(shape: Int): Int = when (shape) {
    SHAPE_SQUARE -> R.string.sysui_mag_shape_square
    SHAPE_CIRCLE -> R.string.sysui_mag_shape_circle
    else -> R.string.sysui_mag_shape_default
}

// Simplified lens preview: a lens box with the scaled sample over a dimmed
// source line, shifted by the vertical offset. Geometry is indicative only;
// exact numbers live in the status subtitle above.
@Composable
private fun MagnifierLensPreview(
    zoom: Float,
    sizeScale: Float,
    shape: Int,
    offsetY: Int,
    modifier: Modifier = Modifier,
) {
    val sample = stringResource(R.string.sysui_mag_sample)
    val animatedZoom by animateFloatAsState(
        targetValue = zoom,
        label = "SystemUiMagZoom",
    )
    val animatedSize by animateFloatAsState(
        targetValue = sizeScale,
        label = "SystemUiMagSize",
    )
    val lensSide = (96f * animatedSize).coerceIn(48f, 200f).dp
    val lensShape = if (shape == SHAPE_CIRCLE) {
        CircleShape
    } else {
        RoundedCornerShape(18.dp)
    }
    val container = MaterialTheme.colorScheme.surfaceContainerHigh
    val containerShape = RoundedCornerShape(16.dp)
    Surface(
        color = container,
        shape = containerShape,
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, container.edgeOutline(), containerShape),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.sysui_mag_preview_label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = sample,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                    textAlign = TextAlign.Center,
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset(y = offsetY.dp)
                        .size(lensSide)
                        .clip(lensShape)
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, MaterialTheme.colorScheme.surface.edgeOutline(), lensShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = sample,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        modifier = Modifier.graphicsLayer {
                            scaleX = animatedZoom
                            scaleY = animatedZoom
                        },
                    )
                }
            }
        }
    }
}
