package org.pixel.extra.parts.display

import android.hardware.Sensor
import android.hardware.SensorManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import org.pixel.extra.parts.R
import org.pixel.extra.parts.ui.ExpressiveSegmentedRow
import org.pixel.extra.parts.ui.GroupCard
import org.pixel.extra.parts.ui.SettingSlider
import org.pixel.extra.parts.ui.SettingSwitchRow
import org.pixel.extra.parts.ui.SettingSwitchRowVariant

// Double-tap card: enable switch, segmented tap count (1-3), timeout slider,
// coordinate check with a scaled phone-mock preview and a live fullscreen
// radius overlay while the max-distance slider moves.
@Composable
fun DoubleTapCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val supported = remember { isDoubleTapSupported(context) }

    var enabled by remember {
        mutableStateOf(DisplayStore.isEnabled(context, DisplayKeys.DTW_ENABLED))
    }
    var tapCount by remember {
        mutableIntStateOf(
            DisplayStore.getInt(context, DisplayKeys.DTW_TAP_COUNT, DisplayKeys.DTW_TAP_COUNT_DEFAULT)
                .coerceIn(DisplayKeys.DTW_TAP_COUNT_MIN, DisplayKeys.DTW_TAP_COUNT_MAX),
        )
    }
    var timeoutMs by remember {
        mutableIntStateOf(
            DisplayStore.getInt(context, DisplayKeys.DTW_TAP_TIMEOUT_MS, DisplayKeys.DTW_TIMEOUT_DEFAULT_MS)
                .coerceIn(DisplayKeys.DTW_TIMEOUT_MIN_MS, DisplayKeys.DTW_TIMEOUT_MAX_MS),
        )
    }
    var coordinateCheck by remember {
        mutableStateOf(DisplayStore.isEnabled(context, DisplayKeys.DTW_COORDINATE_CHECK))
    }
    var maxDistance by remember {
        mutableIntStateOf(
            DisplayStore.getInt(
                context,
                DisplayKeys.DTW_MAX_DISTANCE_DP,
                DisplayKeys.DTW_MAX_DISTANCE_DEFAULT_DP,
            ).coerceIn(DisplayKeys.DTW_MAX_DISTANCE_MIN_DP, DisplayKeys.DTW_MAX_DISTANCE_MAX_DP),
        )
    }
    var isMaxDistanceAdjusting by remember { mutableStateOf(false) }
    val controlsEnabled = enabled && supported

    Box(modifier = modifier) {
        GroupCard(
            title = stringResource(R.string.dtw_title),
            subtitle = stringResource(R.string.dtw_summary),
        ) {
            if (!supported) {
                Text(
                    text = stringResource(R.string.dtw_unsupported),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
            SettingSwitchRow(
                title = stringResource(R.string.dtw_enable_title),
                summary = stringResource(R.string.dtw_enable_summary),
                checked = enabled,
                enabled = supported,
                onCheckedChange = { checked ->
                    enabled = checked
                    DisplayStore.setEnabled(context, DisplayKeys.DTW_ENABLED, checked)
                },
                variant = SettingSwitchRowVariant.Plain,
                dimContentWhenDisabled = true,
                horizontalSpacing = 8.dp,
            )
            Text(
                text = stringResource(R.string.dtw_tap_count_title),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            Box(modifier = Modifier.fillMaxWidth()) {
                Box(modifier = Modifier.alpha(if (controlsEnabled) 1f else 0.45f)) {
                    ExpressiveSegmentedRow(
                        items = listOf(
                            DisplayKeys.DTW_TAP_COUNT_MIN,
                            DisplayKeys.DTW_TAP_COUNT_MIN + 1,
                            DisplayKeys.DTW_TAP_COUNT_MAX,
                        ),
                        selectedItem = tapCount,
                        onSelect = { count ->
                            tapCount = count
                            DisplayStore.putInt(context, DisplayKeys.DTW_TAP_COUNT, count)
                        },
                        labelProvider = { count -> count.toString() },
                    )
                }
                if (!controlsEnabled) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {},
                            ),
                    )
                }
            }
            SettingSlider(
                title = stringResource(R.string.dtw_timeout_title),
                value = timeoutMs.toFloat(),
                onValueChange = { value ->
                    timeoutMs = value.toInt()
                        .coerceIn(DisplayKeys.DTW_TIMEOUT_MIN_MS, DisplayKeys.DTW_TIMEOUT_MAX_MS)
                    DisplayStore.putInt(context, DisplayKeys.DTW_TAP_TIMEOUT_MS, timeoutMs)
                },
                enabled = controlsEnabled,
                valueRange = DisplayKeys.DTW_TIMEOUT_MIN_MS.toFloat()..
                    DisplayKeys.DTW_TIMEOUT_MAX_MS.toFloat(),
                step = 50f,
                defaultValue = DisplayKeys.DTW_TIMEOUT_DEFAULT_MS.toFloat(),
                resetLabel = stringResource(R.string.motion_reset),
                valueLabel = { v -> stringResource(R.string.fmt_ms, v.toInt()) },
            )
            SettingSwitchRow(
                title = stringResource(R.string.dtw_coordinate_check_title),
                summary = stringResource(R.string.dtw_coordinate_check_summary),
                checked = coordinateCheck,
                enabled = controlsEnabled,
                onCheckedChange = { checked ->
                    coordinateCheck = checked
                    DisplayStore.setEnabled(context, DisplayKeys.DTW_COORDINATE_CHECK, checked)
                },
                variant = SettingSwitchRowVariant.Plain,
                dimContentWhenDisabled = true,
                horizontalSpacing = 8.dp,
            )
            if (coordinateCheck) {
                DtwCoordinatePreview(maxDistance = maxDistance)
                SettingSlider(
                    title = stringResource(R.string.dtw_max_distance_title),
                    value = maxDistance.toFloat(),
                    onValueChange = {
                        isMaxDistanceAdjusting = true
                        maxDistance = it.toInt().coerceIn(
                            DisplayKeys.DTW_MAX_DISTANCE_MIN_DP,
                            DisplayKeys.DTW_MAX_DISTANCE_MAX_DP,
                        )
                        DisplayStore.putInt(context, DisplayKeys.DTW_MAX_DISTANCE_DP, maxDistance)
                    },
                    enabled = controlsEnabled,
                    valueRange = DisplayKeys.DTW_MAX_DISTANCE_MIN_DP.toFloat()..
                        DisplayKeys.DTW_MAX_DISTANCE_MAX_DP.toFloat(),
                    step = 5f,
                    defaultValue = DisplayKeys.DTW_MAX_DISTANCE_DEFAULT_DP.toFloat(),
                    resetLabel = stringResource(R.string.motion_reset),
                    valueLabel = { v -> stringResource(R.string.fmt_dp, v.toInt()) },
                )
            }
        }
        if (isMaxDistanceAdjusting) {
            // The shared slider has no drag-end callback, so the overlay is
            // purely visual (never consumes input) and fades shortly after
            // the last value change, mirroring onValueChangeFinished.
            LaunchedEffect(maxDistance) {
                kotlinx.coroutines.delay(900)
                isMaxDistanceAdjusting = false
            }
            DtwLiveRadiusOverlay(maxDistance = maxDistance)
        }
    }
}

private fun isDoubleTapSupported(context: android.content.Context): Boolean {
    val sensorManager = context.getSystemService(SensorManager::class.java) ?: return false
    return sensorManager.getSensorList(Sensor.TYPE_ALL).any { sensor ->
        sensor.stringType == "com.google.sensor.single_touch" ||
            (sensor.name?.contains("single_touch", ignoreCase = true) == true)
    }
}

// Scaled phone mock showing how far the second tap may land from the first.
// Geometry mirrors the legacy preview: the display rect is fit into the
// canvas, the allowed radius is the dp value scaled by the same factor.
@Composable
private fun DtwCoordinatePreview(maxDistance: Int, modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val previewSurface = MaterialTheme.colorScheme.surfaceContainerHighest
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val displayWidthDp = configuration.screenWidthDp.coerceAtLeast(1)
    val displayHeightDp = configuration.screenHeightDp.coerceAtLeast(1)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.dtw_coordinate_preview_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.dtw_coordinate_preview_summary),
                style = MaterialTheme.typography.bodySmall,
                color = onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .padding(top = 12.dp),
            ) {
                val horizontalPadding = 16.dp.toPx()
                val verticalPadding = 12.dp.toPx()
                val maxPreviewWidth = (size.width - horizontalPadding * 2f).coerceAtLeast(1f)
                val maxPreviewHeight = (size.height - verticalPadding * 2f).coerceAtLeast(1f)
                val displayWidthPx = with(density) { displayWidthDp.dp.toPx() }
                val displayHeightPx = with(density) { displayHeightDp.dp.toPx() }
                val previewScale = minOf(
                    maxPreviewWidth / displayWidthPx,
                    maxPreviewHeight / displayHeightPx,
                )
                val displaySize = Size(
                    width = displayWidthPx * previewScale,
                    height = displayHeightPx * previewScale,
                )
                val distancePx = with(density) { maxDistance.dp.toPx() }
                val allowedRadius = distancePx * previewScale
                val center = Offset(size.width / 2f, size.height / 2f)
                val displayTopLeft = Offset(
                    center.x - displaySize.width / 2f,
                    center.y - displaySize.height / 2f,
                )
                val cornerRadius = minOf(18.dp.toPx(), displaySize.width / 2f)
                drawRoundRect(
                    color = previewSurface.copy(alpha = 0.16f),
                    topLeft = displayTopLeft,
                    size = displaySize,
                    cornerRadius = CornerRadius(cornerRadius, cornerRadius),
                )
                drawRoundRect(
                    color = primary.copy(alpha = 0.44f),
                    topLeft = displayTopLeft,
                    size = displaySize,
                    cornerRadius = CornerRadius(cornerRadius, cornerRadius),
                    style = Stroke(width = 1.dp.toPx()),
                )
                clipRect(
                    left = displayTopLeft.x,
                    top = displayTopLeft.y,
                    right = displayTopLeft.x + displaySize.width,
                    bottom = displayTopLeft.y + displaySize.height,
                ) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                primary.copy(alpha = 0.16f),
                                primary.copy(alpha = 0.04f),
                            ),
                            center = center,
                            radius = allowedRadius.coerceAtLeast(1f),
                        ),
                        radius = allowedRadius.coerceAtLeast(1f),
                        center = center,
                    )
                    drawCircle(
                        color = primary.copy(alpha = 0.58f),
                        radius = allowedRadius,
                        center = center,
                        style = Stroke(width = 2.dp.toPx()),
                    )
                }
                drawCircle(color = primary, radius = 6.dp.toPx(), center = center)
                drawCircle(color = previewSurface, radius = 2.5.dp.toPx(), center = center)
                drawLine(
                    color = primary.copy(alpha = 0.42f),
                    start = center,
                    end = Offset(center.x, center.y - allowedRadius),
                    strokeWidth = 1.5.dp.toPx(),
                )
                drawCircle(
                    color = primary.copy(alpha = 0.92f),
                    radius = 5.dp.toPx(),
                    center = Offset(center.x, center.y - allowedRadius),
                )
            }
            Text(
                text = stringResource(R.string.dtw_coordinate_preview_value, maxDistance),
                style = MaterialTheme.typography.labelLarge,
                color = primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

// Fullscreen 1:1 radius overlay shown while the distance slider moves.
// Purely visual: it never consumes input, so the drag underneath continues.
@Composable
private fun DtwLiveRadiusOverlay(maxDistance: Int) {
    val primary = MaterialTheme.colorScheme.primary
    val surface = MaterialTheme.colorScheme.surface
    val density = LocalDensity.current
    val radius = with(density) { maxDistance.dp.toPx() }

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(1f),
    ) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val visibleRadius = radius.coerceAtLeast(1f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(primary.copy(alpha = 0.14f), primary.copy(alpha = 0.02f)),
                center = center,
                radius = visibleRadius,
            ),
            radius = visibleRadius,
            center = center,
        )
        drawCircle(
            color = primary.copy(alpha = 0.72f),
            radius = visibleRadius,
            center = center,
            style = Stroke(width = 2.dp.toPx()),
        )
        drawLine(
            color = primary.copy(alpha = 0.42f),
            start = center,
            end = Offset(center.x, center.y - visibleRadius),
            strokeWidth = 1.5.dp.toPx(),
        )
        drawCircle(
            color = primary.copy(alpha = 0.92f),
            radius = 5.dp.toPx(),
            center = Offset(center.x, center.y - visibleRadius),
        )
        drawCircle(color = primary, radius = 6.dp.toPx(), center = center)
        drawCircle(color = surface, radius = 2.5.dp.toPx(), center = center)
    }
}
