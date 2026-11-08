package org.pixel.extra.parts.display

import android.hardware.Sensor
import android.hardware.SensorManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.pixel.extra.parts.R
import org.pixel.extra.parts.ui.ExpressiveSegmentedRow
import org.pixel.extra.parts.ui.GroupCard
import org.pixel.extra.parts.ui.SettingSlider
import org.pixel.extra.parts.ui.SettingSwitchRow
import org.pixel.extra.parts.ui.SettingSwitchRowVariant

// Double-tap card: enable switch, segmented tap count (1-3) and a timeout
// slider. The legacy coordinate check, canvas preview and live radius overlay
// are dropped; only the wake knobs are kept.
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
    val controlsEnabled = enabled && supported

    GroupCard(
        title = stringResource(R.string.dtw_title),
        subtitle = stringResource(R.string.dtw_summary),
        modifier = modifier,
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
    }
}

private fun isDoubleTapSupported(context: android.content.Context): Boolean {
    val sensorManager = context.getSystemService(SensorManager::class.java) ?: return false
    return sensorManager.getSensorList(Sensor.TYPE_ALL).any { sensor ->
        sensor.stringType == "com.google.sensor.single_touch" ||
            (sensor.name?.contains("single_touch", ignoreCase = true) == true)
    }
}
