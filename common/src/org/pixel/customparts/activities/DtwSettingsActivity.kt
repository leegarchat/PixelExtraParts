package org.pixel.customparts.activities

import android.content.Context
import android.os.Bundle
import android.hardware.Sensor
import android.hardware.SensorManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import org.pixel.customparts.R
import org.pixel.customparts.SettingsKeys
import org.pixel.customparts.dynamicDarkColorScheme
import org.pixel.customparts.dynamicLightColorScheme
import org.pixel.customparts.ui.GenericSwitchRow
import org.pixel.customparts.ui.REBOOT_BUBBLE_CONTENT_BOTTOM_PADDING
import org.pixel.customparts.ui.RebootBubble
import org.pixel.customparts.ui.SettingsGroupCard
import org.pixel.customparts.ui.SliderSetting
import org.pixel.customparts.ui.TopBarBlurOverlay
import org.pixel.customparts.utils.dynamicStringResource
import org.pixel.customparts.ui.recordLayer
import org.pixel.customparts.ui.rememberGraphicsLayerRecordingState
import org.pixel.customparts.utils.SettingsCompat
import java.util.Locale

class DtwSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val context = androidx.compose.ui.platform.LocalContext.current
            val colorScheme = if (isSystemInDarkTheme()) {
                dynamicDarkColorScheme(context)
            } else {
                dynamicLightColorScheme(context)
            }

            MaterialTheme(colorScheme = colorScheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    DtwSettingsScreen(onBack = { finish() })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DtwSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val blurState = rememberGraphicsLayerRecordingState()
    val listState = rememberLazyListState()
    val supported = remember { isDt2wSupported(context) }

    var enabled by remember { mutableStateOf(SettingsCompat.isEnabled(context, SettingsKeys.DTW_ENABLED, false)) }
    var tapCount by remember { mutableIntStateOf(SettingsCompat.getInt(context, SettingsKeys.DTW_TAP_COUNT, 2)) }
    var timeoutMs by remember { mutableIntStateOf(SettingsCompat.getInt(context, SettingsKeys.DTW_TAP_TIMEOUT_MS, 300)) }
    var coordinateCheck by remember { mutableStateOf(SettingsCompat.isEnabled(context, SettingsKeys.DTW_COORDINATE_CHECK, false)) }
    var maxDistance by remember {
        mutableIntStateOf(
            SettingsCompat.getInt(context, SettingsKeys.DTW_MAX_DISTANCE_DP, 50)
                .coerceIn(10, 500)
        )
    }
    var isMaxDistanceAdjusting by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentWindowInsets = WindowInsets.navigationBars,
        topBar = {
            TopAppBar(
                title = { Text(dynamicStringResource(R.string.dtw_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, dynamicStringResource(R.string.nav_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent
                )
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .recordLayer(blurState)
                    .background(MaterialTheme.colorScheme.surfaceContainer),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    top = innerPadding.calculateTopPadding() + 16.dp,
                    end = 16.dp,
                    bottom = innerPadding.calculateBottomPadding() + REBOOT_BUBBLE_CONTENT_BOTTOM_PADDING
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    SettingsGroupCard(title = dynamicStringResource(R.string.dtw_title), enabled = supported) {
                        GenericSwitchRow(
                            title = dynamicStringResource(R.string.dtw_enable_title),
                            summary = dynamicStringResource(R.string.dtw_summary),
                            checked = enabled,
                            enabled = supported,
                            onCheckedChange = {
                                enabled = it
                                SettingsCompat.putInt(context, SettingsKeys.DTW_ENABLED, if (it) 1 else 0)
                            }
                        )

                        SliderSetting(
                            title = dynamicStringResource(R.string.dtw_tap_count_title),
                            value = tapCount,
                            range = 1..3,
                            unit = "",
                            enabled = enabled && supported,
                            valueText = tapCount.toString(),
                            onValueChange = {
                                tapCount = it
                                SettingsCompat.putInt(context, SettingsKeys.DTW_TAP_COUNT, it)
                            },
                            onDefault = {
                                tapCount = 2
                                SettingsCompat.putInt(context, SettingsKeys.DTW_TAP_COUNT, tapCount)
                            }
                        )

                        SliderSetting(
                            title = dynamicStringResource(R.string.dtw_timeout_title),
                            value = timeoutMs,
                            range = 100..2000,
                            unit = "ms",
                            enabled = enabled && supported,
                            valueText = "$timeoutMs ms",
                            onValueChange = {
                                timeoutMs = it
                                SettingsCompat.putInt(context, SettingsKeys.DTW_TAP_TIMEOUT_MS, it)
                            },
                            onDefault = {
                                timeoutMs = 300
                                SettingsCompat.putInt(context, SettingsKeys.DTW_TAP_TIMEOUT_MS, timeoutMs)
                            }
                        )

                        GenericSwitchRow(
                            title = dynamicStringResource(R.string.dtw_coordinate_check_title),
                            summary = dynamicStringResource(R.string.dtw_coordinate_check_summary),
                            checked = coordinateCheck,
                            enabled = enabled && supported,
                            onCheckedChange = {
                                coordinateCheck = it
                                SettingsCompat.putInt(context, SettingsKeys.DTW_COORDINATE_CHECK, if (it) 1 else 0)
                            }
                        )

                        if (coordinateCheck) {
                            DtwCoordinatePreview(
                                maxDistance = maxDistance
                            )

                            SliderSetting(
                                title = dynamicStringResource(R.string.dtw_max_distance_title),
                                value = maxDistance,
                                range = 10..500,
                                unit = "dp",
                                enabled = enabled && supported,
                                valueText = "$maxDistance dp",
                                onValueChange = {
                                    isMaxDistanceAdjusting = true
                                    maxDistance = it
                                    SettingsCompat.putInt(context, SettingsKeys.DTW_MAX_DISTANCE_DP, it)
                                },
                                onValueChangeFinished = { isMaxDistanceAdjusting = false },
                                onDefault = {
                                    isMaxDistanceAdjusting = false
                                    maxDistance = 50
                                    SettingsCompat.putInt(context, SettingsKeys.DTW_MAX_DISTANCE_DP, maxDistance)
                                }
                            )
                        }
                    }
                }
            }

            TopBarBlurOverlay(
                modifier = Modifier.fillMaxWidth(),
                topBarHeight = 64.dp + WindowInsets.statusBars.asPaddingValues().calculateTopPadding(),
                blurState = blurState,
                isScrolled = listState.canScrollBackward
            )

            if (isMaxDistanceAdjusting) {
                DtwLiveRadiusOverlay(maxDistance = maxDistance)
            }
        }
    }
}

private fun isDt2wSupported(context: Context): Boolean {
    val sensorManager = context.getSystemService(SensorManager::class.java) ?: return false
    return sensorManager.getSensorList(Sensor.TYPE_ALL).any { sensor ->
        sensor.stringType == "com.google.sensor.single_touch" ||
            sensor.name?.contains("single_touch", ignoreCase = true) == true
    }
}

@Composable
private fun DtwCoordinatePreview(maxDistance: Int) {
    val primary = MaterialTheme.colorScheme.primary
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val previewSurface = MaterialTheme.colorScheme.surfaceContainerHighest
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val displayWidthDp = configuration.screenWidthDp.coerceAtLeast(1)
    val displayHeightDp = configuration.screenHeightDp.coerceAtLeast(1)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = dynamicStringResource(R.string.dtw_coordinate_preview_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = dynamicStringResource(R.string.dtw_coordinate_preview_summary),
                style = MaterialTheme.typography.bodySmall,
                color = onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(480.dp)
                    .padding(top = 12.dp)
            ) {
                val horizontalPadding = 16.dp.toPx()
                val verticalPadding = 12.dp.toPx()
                val maxPreviewWidth = (size.width - horizontalPadding * 2f).coerceAtLeast(1f)
                val maxPreviewHeight = (size.height - verticalPadding * 2f).coerceAtLeast(1f)
                val displayWidthPx = with(density) { displayWidthDp.dp.toPx() }
                val displayHeightPx = with(density) { displayHeightDp.dp.toPx() }
                val previewScale = minOf(
                    maxPreviewWidth / displayWidthPx,
                    maxPreviewHeight / displayHeightPx
                )
                val displaySize = Size(
                    width = displayWidthPx * previewScale,
                    height = displayHeightPx * previewScale
                )
                val distancePx = with(density) { maxDistance.dp.toPx() }
                val allowedRadius = distancePx * previewScale
                val center = Offset(size.width / 2f, size.height / 2f)
                val displayTopLeft = Offset(
                    center.x - displaySize.width / 2f,
                    center.y - displaySize.height / 2f
                )
                val cornerRadius = minOf(18.dp.toPx(), displaySize.width / 2f)

                drawRoundRect(
                    color = previewSurface.copy(alpha = 0.16f),
                    topLeft = displayTopLeft,
                    size = displaySize,
                    cornerRadius = CornerRadius(cornerRadius, cornerRadius)
                )
                drawRoundRect(
                    color = primary.copy(alpha = 0.44f),
                    topLeft = displayTopLeft,
                    size = displaySize,
                    cornerRadius = CornerRadius(cornerRadius, cornerRadius),
                    style = Stroke(width = 1.dp.toPx())
                )
                clipRect(
                    left = displayTopLeft.x,
                    top = displayTopLeft.y,
                    right = displayTopLeft.x + displaySize.width,
                    bottom = displayTopLeft.y + displaySize.height
                ) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                primary.copy(alpha = 0.16f),
                                primary.copy(alpha = 0.04f)
                            ),
                            center = center,
                            radius = allowedRadius.coerceAtLeast(1f)
                        ),
                        radius = allowedRadius.coerceAtLeast(1f),
                        center = center
                    )
                    drawCircle(
                        color = primary.copy(alpha = 0.58f),
                        radius = allowedRadius,
                        center = center,
                        style = Stroke(width = 2.dp.toPx())
                    )
                }

                drawCircle(
                    color = primary,
                    radius = 6.dp.toPx(),
                    center = center
                )
                drawCircle(
                    color = previewSurface,
                    radius = 2.5.dp.toPx(),
                    center = center
                )
                drawLine(
                    color = primary.copy(alpha = 0.42f),
                    start = center,
                    end = Offset(
                        center.x,
                        center.y - allowedRadius
                    ),
                    strokeWidth = 1.5.dp.toPx()
                )
                drawCircle(
                    color = primary.copy(alpha = 0.92f),
                    radius = 5.dp.toPx(),
                    center = Offset(
                        center.x,
                        center.y - allowedRadius
                    )
                )
            }

            Text(
                text = dynamicStringResource(R.string.dtw_coordinate_preview_value, maxDistance),
                style = MaterialTheme.typography.labelLarge,
                color = primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}

@Composable
private fun DtwLiveRadiusOverlay(maxDistance: Int) {
    val primary = MaterialTheme.colorScheme.primary
    val surface = MaterialTheme.colorScheme.surface
    val density = LocalDensity.current
    val radius = with(density) { maxDistance.dp.toPx() }

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(1f)
    ) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val visibleRadius = radius.coerceAtLeast(1f)

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    primary.copy(alpha = 0.14f),
                    primary.copy(alpha = 0.02f)
                ),
                center = center,
                radius = visibleRadius
            ),
            radius = visibleRadius,
            center = center
        )
        drawCircle(
            color = primary.copy(alpha = 0.72f),
            radius = visibleRadius,
            center = center,
            style = Stroke(width = 2.dp.toPx())
        )
        drawLine(
            color = primary.copy(alpha = 0.42f),
            start = center,
            end = Offset(center.x, center.y - visibleRadius),
            strokeWidth = 1.5.dp.toPx()
        )
        drawCircle(
            color = primary.copy(alpha = 0.92f),
            radius = 5.dp.toPx(),
            center = Offset(center.x, center.y - visibleRadius)
        )
        drawCircle(
            color = primary,
            radius = 6.dp.toPx(),
            center = center
        )
        drawCircle(
            color = surface,
            radius = 2.5.dp.toPx(),
            center = center
        )
    }
}
