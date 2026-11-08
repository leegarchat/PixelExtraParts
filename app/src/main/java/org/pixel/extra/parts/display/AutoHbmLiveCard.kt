package org.pixel.extra.parts.display

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import java.util.Locale
import org.pixel.extra.parts.R
import org.pixel.extra.parts.ui.GroupCard
import org.pixel.extra.parts.ui.SettingSlider

// Live ambient-light monitor. While automatic HBM is off the card samples the
// light sensor directly; while it runs, lux/brightness/temperature arrive
// from the firmware service broadcast. No persisted keys are involved.
@Composable
internal fun AutoHbmLiveCard(
    enabled: Boolean,
    threshold: Int,
    supported: Boolean,
    checkIntervalMs: Int,
    onCheckIntervalChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var currentLux by remember { mutableFloatStateOf(0f) }
    var hbmActive by remember { mutableStateOf(false) }
    var brightness by remember { mutableIntStateOf(DisplayStore.readBrightness() ?: 0) }
    var maxBrightness by remember { mutableIntStateOf(DisplayStore.readMaxBrightness() ?: 0) }
    var currentTemperature by remember { mutableStateOf<Float?>(DisplayStore.readSocTemperatureC()) }
    val socModel = remember { DisplayStore.readSocModel() }

    DisposableEffect(supported, checkIntervalMs, enabled) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val lightSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_LIGHT)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (event.sensor.type == Sensor.TYPE_LIGHT && !enabled) {
                    val lux = event.values.firstOrNull() ?: return
                    if (lux.isFinite() && lux >= 0f) currentLux = lux
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        if (supported && lightSensor != null && !enabled) {
            sensorManager.registerListener(listener, lightSensor, checkIntervalMs * 1000, 0)
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                currentLux = intent.getFloatExtra(DisplayStore.AUTO_HBM_EXTRA_LUX, currentLux)
                hbmActive = intent.getBooleanExtra(DisplayStore.AUTO_HBM_EXTRA_ACTIVE, hbmActive)
                brightness = intent.getIntExtra(DisplayStore.AUTO_HBM_EXTRA_BRIGHTNESS, brightness)
                maxBrightness = intent.getIntExtra(DisplayStore.AUTO_HBM_EXTRA_MAX_BRIGHTNESS, maxBrightness)
                if (intent.hasExtra(DisplayStore.AUTO_HBM_EXTRA_TEMPERATURE)) {
                    currentTemperature = intent.getFloatExtra(
                        DisplayStore.AUTO_HBM_EXTRA_TEMPERATURE,
                        currentTemperature ?: 0f,
                    )
                }
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(DisplayStore.AUTO_HBM_ACTION_STATE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onDispose {
            sensorManager?.unregisterListener(listener)
            runCatching { context.unregisterReceiver(receiver) }
        }
    }

    val temperatureText = currentTemperature?.let { String.format(Locale.US, "%.1f°C", it) }
        ?: stringResource(R.string.auto_hbm_temperature_unknown)

    GroupCard(
        title = stringResource(R.string.auto_hbm_current_lux_title),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.alpha(if (supported) 1f else 0.45f),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.WbSunny,
                    contentDescription = null,
                    tint = if (hbmActive) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(28.dp),
                )
                Column(modifier = Modifier.padding(start = 16.dp).weight(1f)) {
                    Text(
                        text = stringResource(R.string.auto_hbm_current_lux_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(
                            if (hbmActive) {
                                R.string.auto_hbm_status_active
                            } else {
                                R.string.auto_hbm_status_monitoring
                            },
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = currentLux.toInt().toString(),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
            }
            LuxProgress(progress = (currentLux / threshold.coerceAtLeast(1)).coerceIn(0f, 1f))
            Text(
                text = stringResource(
                    R.string.auto_hbm_status_format,
                    threshold,
                    brightness,
                    maxBrightness,
                    socModel,
                    temperatureText,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        SettingSlider(
            title = stringResource(R.string.auto_hbm_check_interval_title),
            value = checkIntervalMs.toFloat(),
            onValueChange = { onCheckIntervalChange(it.toInt()) },
            enabled = supported,
            valueRange = DisplayKeys.HBM_CHECK_INTERVAL_MIN_MS.toFloat()..
                DisplayKeys.HBM_CHECK_INTERVAL_MAX_MS.toFloat(),
            step = 10f,
            defaultValue = DisplayKeys.HBM_CHECK_INTERVAL_DEFAULT_MS.toFloat(),
            resetLabel = stringResource(R.string.motion_reset),
            valueLabel = { v -> stringResource(R.string.fmt_ms, v.toInt()) },
        )
    }
}

// Local capsule progress (ui/ has no shared linear progress component):
// highest-surface track with a primary-to-tertiary gradient fill.
@Composable
private fun LuxProgress(progress: Float, modifier: Modifier = Modifier) {
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0.03f, 1f),
        label = "HbmLuxProgress",
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(2.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(animated)
                .fillMaxHeight()
                .clip(CircleShape)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.tertiary,
                        ),
                    ),
                ),
        )
    }
}
