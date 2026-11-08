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
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.shadow
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
// shape segmented row, vertical-offset slider, editable sample text and a
// live lens preview whose geometry mirrors the framework (100x48 base,
// square/circle normalized to the longest side, default center -42dp).
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
    val defaultSampleText = stringResource(R.string.sysui_mag_sample)
    var sampleText by remember(defaultSampleText) { mutableStateOf(defaultSampleText) }

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
            OutlinedTextField(
                value = sampleText,
                onValueChange = { sampleText = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Box(modifier = Modifier.alpha(if (enabled) 1f else 0.42f)) {
                MagnifierLensPreview(
                    sampleText = sampleText,
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

// Faithful lens preview: the framework base is 100x48dp scaled by
// sizeScale (square/circle normalize to the longest side), the default lens
// center sits 42dp above the text cursor plus the custom offset. The column
// always spans both the lens and the source line, so large offsets never
// clip; positions are animated like the loupe itself.
@Composable
private fun MagnifierLensPreview(
    sampleText: String,
    zoom: Float,
    sizeScale: Float,
    shape: Int,
    offsetY: Int,
    modifier: Modifier = Modifier,
) {
    val animatedZoom by animateFloatAsState(targetValue = zoom, label = "SystemUiMagZoom")
    val animatedSize by animateFloatAsState(targetValue = sizeScale, label = "SystemUiMagSize")
    val baseW = 100f
    val baseH = 48f
    var magWDp = baseW * animatedSize
    var magHDp = baseH * animatedSize
    if (shape == SHAPE_SQUARE || shape == SHAPE_CIRCLE) {
        val side = maxOf(magWDp, magHDp)
        magWDp = side
        magHDp = side
    }
    val lensCenterRelative = -42f + offsetY
    val animatedLensCenter by animateFloatAsState(
        targetValue = lensCenterRelative,
        label = "SystemUiMagCenter",
    )
    val textLineH = 20f
    val lensHalfH = magHDp / 2f
    val textHalfH = textLineH / 2f
    val lensTop = animatedLensCenter - lensHalfH
    val lensBottom = animatedLensCenter + lensHalfH
    val topEdge = minOf(lensTop, -textHalfH)
    val bottomEdge = maxOf(lensBottom, textHalfH)
    val boxHeightDp = (bottomEdge - topEdge).coerceAtLeast(1f)
    val textTopOffset = -textHalfH - topEdge
    val lensTopOffset = lensTop - topEdge
    val magShape = if (shape == SHAPE_CIRCLE) {
        CircleShape
    } else {
        RoundedCornerShape(50)
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
                    .height(boxHeightDp.dp),
                contentAlignment = Alignment.TopCenter,
            ) {
                Text(
                    text = sampleText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = textTopOffset.dp),
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = lensTopOffset.dp)
                        .size(magWDp.dp, magHDp.dp)
                        .shadow(elevation = 6.dp, shape = magShape)
                        .clip(magShape)
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, MaterialTheme.colorScheme.surface.edgeOutline(), magShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = sampleText,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        softWrap = false,
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
