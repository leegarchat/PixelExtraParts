package org.pixel.extra.parts.display

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.pixel.extra.parts.R
import org.pixel.extra.parts.ui.ExpandableGroupCard
import org.pixel.extra.parts.ui.ExpressiveSegmentedRow
import org.pixel.extra.parts.ui.GroupCard
import org.pixel.extra.parts.ui.SettingSlider
import org.pixel.extra.parts.ui.SettingSwitchRow
import org.pixel.extra.parts.ui.SettingSwitchRowVariant
import java.io.File

// AutoHBM card: live lux monitor, enable switch, segmented auto/permanent
// mode, brightness lock, threshold and delay sliders, plus an expandable
// advanced group.
@Composable
fun AutoHbmCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val supported = remember { isAutoHbmSupported() }

    var enabled by remember {
        mutableStateOf(DisplayStore.isEnabled(context, DisplayKeys.AUTO_HBM_ENABLED))
    }
    var hbmMode by remember {
        mutableIntStateOf(
            DisplayStore.getInt(context, DisplayKeys.AUTO_HBM_MODE, DisplayKeys.HBM_MODE_AUTO)
                .coerceIn(DisplayKeys.HBM_MODE_AUTO, DisplayKeys.HBM_MODE_PERMANENT),
        )
    }
    var brightnessLock by remember {
        mutableStateOf(DisplayStore.isEnabled(context, DisplayKeys.AUTO_HBM_BRIGHTNESS_LOCK))
    }
    var threshold by remember {
        mutableIntStateOf(
            DisplayStore.getInt(context, DisplayKeys.AUTO_HBM_THRESHOLD, DisplayKeys.HBM_THRESHOLD_DEFAULT)
                .coerceIn(DisplayKeys.HBM_THRESHOLD_MIN, DisplayKeys.HBM_THRESHOLD_MAX),
        )
    }
    var enableTime by remember {
        mutableIntStateOf(
            DisplayStore.getInt(context, DisplayKeys.AUTO_HBM_ENABLE_TIME, DisplayKeys.HBM_DELAY_DEFAULT_ON)
                .coerceIn(DisplayKeys.HBM_DELAY_MIN, DisplayKeys.HBM_DELAY_MAX),
        )
    }
    var disableTime by remember {
        mutableIntStateOf(
            DisplayStore.getInt(context, DisplayKeys.AUTO_HBM_DISABLE_TIME, DisplayKeys.HBM_DELAY_DEFAULT_OFF)
                .coerceIn(DisplayKeys.HBM_DELAY_MIN, DisplayKeys.HBM_DELAY_MAX),
        )
    }
    var smoothRamp by remember {
        mutableStateOf(
            DisplayStore.isEnabled(context, DisplayKeys.AUTO_HBM_SMOOTH_RAMP_ENABLED, true),
        )
    }
    var rampTimeMs by remember {
        mutableIntStateOf(
            DisplayStore.getInt(context, DisplayKeys.AUTO_HBM_RAMP_TIME_MS, DisplayKeys.HBM_RAMP_DEFAULT_MS)
                .coerceIn(DisplayKeys.HBM_RAMP_MIN_MS, DisplayKeys.HBM_RAMP_MAX_MS),
        )
    }
    var maxActiveTime by remember {
        mutableIntStateOf(
            DisplayStore.getInt(context, DisplayKeys.AUTO_HBM_MAX_ACTIVE_TIME, DisplayKeys.HBM_MAX_ACTIVE_DEFAULT_S)
                .coerceIn(DisplayKeys.HBM_TIMEOUT_MIN_S, DisplayKeys.HBM_TIMEOUT_MAX_S),
        )
    }
    var cooldownTime by remember {
        mutableIntStateOf(
            DisplayStore.getInt(context, DisplayKeys.AUTO_HBM_COOLDOWN_TIME, DisplayKeys.HBM_COOLDOWN_DEFAULT_S)
                .coerceIn(DisplayKeys.HBM_TIMEOUT_MIN_S, DisplayKeys.HBM_TIMEOUT_MAX_S),
        )
    }
    var checkIntervalMs by remember {
        mutableIntStateOf(
            DisplayStore.getInt(
                context,
                DisplayKeys.AUTO_HBM_CHECK_INTERVAL_MS,
                DisplayKeys.HBM_CHECK_INTERVAL_DEFAULT_MS,
            ).coerceIn(DisplayKeys.HBM_CHECK_INTERVAL_MIN_MS, DisplayKeys.HBM_CHECK_INTERVAL_MAX_MS),
        )
    }
    var temperatureLimit by remember {
        mutableIntStateOf(
            DisplayStore.getInt(context, DisplayKeys.AUTO_HBM_TEMPERATURE_LIMIT, DisplayKeys.HBM_TEMP_DEFAULT_C)
                .coerceIn(DisplayKeys.HBM_TEMP_MIN_C, DisplayKeys.HBM_TEMP_MAX_C),
        )
    }

    val autoMode = hbmMode == DisplayKeys.HBM_MODE_AUTO
    val autoControlsEnabled = enabled && autoMode && supported
    val baseControlsEnabled = enabled && supported

    GroupCard(
        title = stringResource(R.string.auto_hbm_title),
        subtitle = stringResource(R.string.auto_hbm_summary),
        modifier = modifier,
    ) {
        AutoHbmLiveCard(
            enabled = enabled,
            threshold = threshold,
            supported = supported,
            checkIntervalMs = checkIntervalMs,
            onCheckIntervalChange = {
                checkIntervalMs = it.coerceIn(
                    DisplayKeys.HBM_CHECK_INTERVAL_MIN_MS,
                    DisplayKeys.HBM_CHECK_INTERVAL_MAX_MS,
                )
                DisplayStore.putInt(context, DisplayKeys.AUTO_HBM_CHECK_INTERVAL_MS, checkIntervalMs)
            },
        )
        if (!supported) {
            Column(modifier = Modifier.padding(horizontal = 4.dp)) {
                Text(
                    text = stringResource(R.string.auto_hbm_unsupported_title),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.error,
                )
                Text(
                    text = stringResource(R.string.auto_hbm_unsupported_summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        SettingSwitchRow(
            title = stringResource(R.string.auto_hbm_enable_title),
            summary = stringResource(R.string.auto_hbm_summary),
            checked = enabled,
            enabled = supported,
            onCheckedChange = { checked ->
                enabled = checked
                DisplayStore.setEnabled(context, DisplayKeys.AUTO_HBM_ENABLED, checked)
            },
            variant = SettingSwitchRowVariant.Plain,
            dimContentWhenDisabled = true,
            horizontalSpacing = 8.dp,
        )
        Text(
            text = stringResource(R.string.auto_hbm_mode_title),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Box(modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.alpha(if (baseControlsEnabled) 1f else 0.45f)) {
                ExpressiveSegmentedRow(
                    items = listOf(DisplayKeys.HBM_MODE_AUTO, DisplayKeys.HBM_MODE_PERMANENT),
                    selectedItem = hbmMode,
                    onSelect = { mode ->
                        hbmMode = mode
                        DisplayStore.putInt(context, DisplayKeys.AUTO_HBM_MODE, mode)
                    },
                    labelProvider = { mode ->
                        if (mode == DisplayKeys.HBM_MODE_AUTO) {
                            stringResource(R.string.auto_hbm_mode_auto)
                        } else {
                            stringResource(R.string.auto_hbm_mode_permanent)
                        }
                    },
                )
            }
            if (!baseControlsEnabled) {
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
        if (!autoMode) {
            Text(
                text = stringResource(R.string.auto_hbm_permanent_summary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
        SettingSwitchRow(
            title = stringResource(R.string.auto_hbm_brightness_lock_title),
            summary = stringResource(R.string.auto_hbm_brightness_lock_summary),
            checked = brightnessLock,
            enabled = baseControlsEnabled,
            onCheckedChange = { checked ->
                brightnessLock = checked
                DisplayStore.setEnabled(context, DisplayKeys.AUTO_HBM_BRIGHTNESS_LOCK, checked)
            },
            variant = SettingSwitchRowVariant.Plain,
            dimContentWhenDisabled = true,
            horizontalSpacing = 8.dp,
        )
        SettingSlider(
            title = stringResource(R.string.auto_hbm_threshold_title),
            value = threshold.toFloat(),
            onValueChange = { value ->
                threshold = value.toInt().coerceIn(DisplayKeys.HBM_THRESHOLD_MIN, DisplayKeys.HBM_THRESHOLD_MAX)
                DisplayStore.putInt(context, DisplayKeys.AUTO_HBM_THRESHOLD, threshold)
            },
            enabled = autoControlsEnabled,
            valueRange = DisplayKeys.HBM_THRESHOLD_MIN.toFloat()..DisplayKeys.HBM_THRESHOLD_MAX.toFloat(),
            step = 100f,
            defaultValue = DisplayKeys.HBM_THRESHOLD_DEFAULT.toFloat(),
            resetLabel = stringResource(R.string.motion_reset),
            valueLabel = { v -> stringResource(R.string.display_fmt_lux, v.toInt()) },
        )
        SettingSlider(
            title = stringResource(R.string.auto_hbm_enable_time_title),
            value = enableTime.toFloat(),
            onValueChange = { value ->
                enableTime = value.toInt().coerceIn(DisplayKeys.HBM_DELAY_MIN, DisplayKeys.HBM_DELAY_MAX)
                DisplayStore.putInt(context, DisplayKeys.AUTO_HBM_ENABLE_TIME, enableTime)
            },
            enabled = autoControlsEnabled,
            valueRange = DisplayKeys.HBM_DELAY_MIN.toFloat()..DisplayKeys.HBM_DELAY_MAX.toFloat(),
            step = 1f,
            defaultValue = DisplayKeys.HBM_DELAY_DEFAULT_ON.toFloat(),
            resetLabel = stringResource(R.string.motion_reset),
            valueLabel = { v -> stringResource(R.string.display_fmt_sec, v.toInt()) },
        )
        SettingSlider(
            title = stringResource(R.string.auto_hbm_disable_time_title),
            value = disableTime.toFloat(),
            onValueChange = { value ->
                disableTime = value.toInt().coerceIn(DisplayKeys.HBM_DELAY_MIN, DisplayKeys.HBM_DELAY_MAX)
                DisplayStore.putInt(context, DisplayKeys.AUTO_HBM_DISABLE_TIME, disableTime)
            },
            enabled = autoControlsEnabled,
            valueRange = DisplayKeys.HBM_DELAY_MIN.toFloat()..DisplayKeys.HBM_DELAY_MAX.toFloat(),
            step = 1f,
            defaultValue = DisplayKeys.HBM_DELAY_DEFAULT_OFF.toFloat(),
            resetLabel = stringResource(R.string.motion_reset),
            valueLabel = { v -> stringResource(R.string.display_fmt_sec, v.toInt()) },
        )
        SettingSwitchRow(
            title = stringResource(R.string.auto_hbm_smooth_ramp_title),
            summary = stringResource(R.string.auto_hbm_smooth_ramp_summary),
            checked = smoothRamp,
            enabled = baseControlsEnabled,
            onCheckedChange = { checked ->
                smoothRamp = checked
                DisplayStore.setEnabled(context, DisplayKeys.AUTO_HBM_SMOOTH_RAMP_ENABLED, checked)
            },
            variant = SettingSwitchRowVariant.Plain,
            dimContentWhenDisabled = true,
            horizontalSpacing = 8.dp,
        )
        if (smoothRamp) {
            SettingSlider(
                title = stringResource(R.string.auto_hbm_ramp_time_title),
                value = rampTimeMs.toFloat(),
                onValueChange = { value ->
                    rampTimeMs = value.toInt().coerceIn(DisplayKeys.HBM_RAMP_MIN_MS, DisplayKeys.HBM_RAMP_MAX_MS)
                    DisplayStore.putInt(context, DisplayKeys.AUTO_HBM_RAMP_TIME_MS, rampTimeMs)
                },
                enabled = baseControlsEnabled,
                valueRange = DisplayKeys.HBM_RAMP_MIN_MS.toFloat()..DisplayKeys.HBM_RAMP_MAX_MS.toFloat(),
                step = 50f,
                defaultValue = DisplayKeys.HBM_RAMP_DEFAULT_MS.toFloat(),
            resetLabel = stringResource(R.string.motion_reset),
                valueLabel = { v -> stringResource(R.string.fmt_ms, v.toInt()) },
            )
        }
        ExpandableGroupCard(
            title = stringResource(R.string.display_advanced_title),
            chevronPainter = painterResource(R.drawable.ic_chevron_down),
        ) {
            SettingSlider(
                title = stringResource(R.string.auto_hbm_max_active_time_title),
                value = maxActiveTime.toFloat(),
                onValueChange = { value ->
                    maxActiveTime = value.toInt()
                        .coerceIn(DisplayKeys.HBM_TIMEOUT_MIN_S, DisplayKeys.HBM_TIMEOUT_MAX_S)
                    DisplayStore.putInt(context, DisplayKeys.AUTO_HBM_MAX_ACTIVE_TIME, maxActiveTime)
                },
                enabled = baseControlsEnabled,
                valueRange = DisplayKeys.HBM_TIMEOUT_MIN_S.toFloat()..DisplayKeys.HBM_TIMEOUT_MAX_S.toFloat(),
                step = 10f,
                defaultValue = DisplayKeys.HBM_MAX_ACTIVE_DEFAULT_S.toFloat(),
                resetLabel = stringResource(R.string.motion_reset),
                valueLabel = { v -> stringResource(R.string.display_fmt_sec, v.toInt()) },
            )
            SettingSlider(
                title = stringResource(R.string.auto_hbm_cooldown_time_title),
                value = cooldownTime.toFloat(),
                onValueChange = { value ->
                    cooldownTime = value.toInt()
                        .coerceIn(DisplayKeys.HBM_TIMEOUT_MIN_S, DisplayKeys.HBM_TIMEOUT_MAX_S)
                    DisplayStore.putInt(context, DisplayKeys.AUTO_HBM_COOLDOWN_TIME, cooldownTime)
                },
                enabled = baseControlsEnabled,
                valueRange = DisplayKeys.HBM_TIMEOUT_MIN_S.toFloat()..DisplayKeys.HBM_TIMEOUT_MAX_S.toFloat(),
                step = 10f,
                defaultValue = DisplayKeys.HBM_COOLDOWN_DEFAULT_S.toFloat(),
                resetLabel = stringResource(R.string.motion_reset),
                valueLabel = { v -> stringResource(R.string.display_fmt_sec, v.toInt()) },
            )
            SettingSlider(
                title = stringResource(R.string.auto_hbm_temperature_limit_title),
                value = temperatureLimit.toFloat(),
                onValueChange = { value ->
                    temperatureLimit = value.toInt()
                        .coerceIn(DisplayKeys.HBM_TEMP_MIN_C, DisplayKeys.HBM_TEMP_MAX_C)
                    DisplayStore.putInt(context, DisplayKeys.AUTO_HBM_TEMPERATURE_LIMIT, temperatureLimit)
                },
                enabled = baseControlsEnabled,
                valueRange = DisplayKeys.HBM_TEMP_MIN_C.toFloat()..DisplayKeys.HBM_TEMP_MAX_C.toFloat(),
                step = 1f,
                defaultValue = DisplayKeys.HBM_TEMP_DEFAULT_C.toFloat(),
                resetLabel = stringResource(R.string.motion_reset),
                valueLabel = { v -> stringResource(R.string.display_fmt_celsius, v.toInt()) },
            )
        }
    }
}

private fun isAutoHbmSupported(): Boolean {
    return File("/sys/class/backlight/panel0-backlight/brightness").exists() &&
        File("/sys/class/backlight/panel0-backlight/max_brightness").exists()
}
