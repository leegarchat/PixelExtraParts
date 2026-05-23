package org.pixel.customparts.activities

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.rounded.WbSunny
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import org.pixel.customparts.R
import org.pixel.customparts.dynamicDarkColorScheme
import org.pixel.customparts.dynamicLightColorScheme
import org.pixel.customparts.services.AutoHbmTileService
import org.pixel.customparts.ui.ExpandableWarningCard
import org.pixel.customparts.ui.GenericSwitchRow
import org.pixel.customparts.ui.REBOOT_BUBBLE_CONTENT_BOTTOM_PADDING
import org.pixel.customparts.ui.RebootBubble
import org.pixel.customparts.ui.SettingsGroupCard
import org.pixel.customparts.ui.SliderSetting
import org.pixel.customparts.ui.TopBarBlurOverlay
import org.pixel.customparts.ui.recordLayer
import org.pixel.customparts.ui.rememberGraphicsLayerRecordingState
import org.pixel.customparts.utils.AutoHbmController
import org.pixel.customparts.utils.TileUtils
import org.pixel.customparts.utils.dynamicStringResource
import java.util.Locale

class AutoHbmActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
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
                    AutoHbmScreen(onBack = { finish() })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AutoHbmScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val blurState = rememberGraphicsLayerRecordingState()
    val listState = rememberLazyListState()
    val isScrolled by remember { derivedStateOf { listState.canScrollBackward } }

    var enabled by remember { mutableStateOf(AutoHbmController.isEnabled(context)) }
    var threshold by remember { mutableIntStateOf(AutoHbmController.getThreshold(context)) }
    var enableTime by remember { mutableIntStateOf(AutoHbmController.getEnableTime(context)) }
    var disableTime by remember { mutableIntStateOf(AutoHbmController.getDisableTime(context)) }
    var smoothRampEnabled by remember { mutableStateOf(AutoHbmController.isSmoothRampEnabled(context)) }
    var rampTimeMs by remember { mutableIntStateOf(AutoHbmController.getRampTimeMs(context)) }
    var maxActiveTime by remember { mutableIntStateOf(AutoHbmController.getMaxActiveTime(context)) }
    var cooldownTime by remember { mutableIntStateOf(AutoHbmController.getCooldownTime(context)) }
    var checkIntervalMs by remember { mutableIntStateOf(AutoHbmController.getCheckIntervalMs(context)) }
    var temperatureLimit by remember { mutableIntStateOf(AutoHbmController.getTemperatureLimit(context)) }
    var currentLux by remember { mutableFloatStateOf(AutoHbmController.getLastLux(context)) }
    var currentTemperature by remember {
        mutableStateOf(AutoHbmController.getLastTemperature(context) ?: AutoHbmController.readSocTemperatureC())
    }
    var hbmActive by remember { mutableStateOf(AutoHbmController.isHbmActive(context)) }
    var brightness by remember { mutableIntStateOf(AutoHbmController.getLastBrightness(context)) }
    var maxBrightness by remember { mutableIntStateOf(AutoHbmController.readMaxBrightness() ?: 0) }
    val socModel = remember { AutoHbmController.getSocModel() }
    val supported = remember { AutoHbmController.isSupported() }

    DisposableEffect(Unit) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val lightSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_LIGHT)
        // When service is running, broadcast is the authoritative lux source.
        // Local sensor is only used when service is not broadcasting (AutoHBM disabled).
        var lastBroadcastTime = 0L

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (event.sensor.type == Sensor.TYPE_LIGHT) {
                    // Only update from local sensor if no recent broadcast (service not active)
                    val now = android.os.SystemClock.uptimeMillis()
                    if (now - lastBroadcastTime > 1000L) {
                        currentLux = event.values.firstOrNull() ?: currentLux
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        if (supported && lightSensor != null) {
            sensorManager.registerListener(listener, lightSensor, SensorManager.SENSOR_DELAY_NORMAL)
        }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                lastBroadcastTime = android.os.SystemClock.uptimeMillis()
                currentLux = intent.getFloatExtra(AutoHbmController.EXTRA_LUX, currentLux)
                hbmActive = intent.getBooleanExtra(AutoHbmController.EXTRA_ACTIVE, AutoHbmController.isHbmActive(context))
                brightness = intent.getIntExtra(AutoHbmController.EXTRA_BRIGHTNESS, brightness)
                maxBrightness = intent.getIntExtra(AutoHbmController.EXTRA_MAX_BRIGHTNESS, maxBrightness)
                if (intent.hasExtra(AutoHbmController.EXTRA_TEMPERATURE)) {
                    currentTemperature = intent.getFloatExtra(AutoHbmController.EXTRA_TEMPERATURE, currentTemperature ?: 0f)
                }
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(AutoHbmController.ACTION_STATE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        onDispose {
            sensorManager?.unregisterListener(listener)
            runCatching { context.unregisterReceiver(receiver) }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentWindowInsets = WindowInsets.navigationBars,
        floatingActionButton = { RebootBubble() },
        topBar = {
            TopAppBar(
                title = { Text(dynamicStringResource(R.string.auto_hbm_title), fontWeight = FontWeight.Bold) },
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
                if (!supported) {
                    item {
                        ExpandableWarningCard(
                            title = dynamicStringResource(R.string.auto_hbm_unsupported_title),
                            text = dynamicStringResource(R.string.auto_hbm_unsupported_summary)
                        )
                    }
                }

                item {
                    AutoHbmLuxCard(
                        currentLux = currentLux,
                        threshold = threshold,
                        hbmActive = hbmActive,
                        brightness = brightness,
                        maxBrightness = maxBrightness,
                        socModel = socModel,
                        currentTemperature = currentTemperature
                    )
                }

                item {
                    SettingsGroupCard(title = dynamicStringResource(R.string.auto_hbm_title), enabled = supported) {
                        GenericSwitchRow(
                            title = dynamicStringResource(R.string.auto_hbm_enable_title),
                            summary = dynamicStringResource(R.string.auto_hbm_summary),
                            checked = enabled,
                            enabled = supported,
                            onCheckedChange = {
                                enabled = it
                                AutoHbmController.setEnabled(context, it)
                                hbmActive = AutoHbmController.isHbmActive(context)
                                brightness = AutoHbmController.getLastBrightness(context)
                                maxBrightness = AutoHbmController.readMaxBrightness() ?: maxBrightness
                            }
                        )

                        SliderSetting(
                            title = dynamicStringResource(R.string.auto_hbm_threshold_title),
                            value = threshold,
                            range = AutoHbmController.MIN_THRESHOLD_LUX..AutoHbmController.MAX_THRESHOLD_LUX,
                            unit = "lx",
                            enabled = enabled && supported,
                            valueText = "$threshold lx",
                            onValueChange = {
                                threshold = it
                                AutoHbmController.setThreshold(context, it)
                            },
                            onDefault = {
                                threshold = AutoHbmController.DEFAULT_THRESHOLD_LUX
                                AutoHbmController.setThreshold(context, threshold)
                            }
                        )

                        SliderSetting(
                            title = dynamicStringResource(R.string.auto_hbm_enable_time_title),
                            value = enableTime,
                            range = AutoHbmController.MIN_TIME_SECONDS..AutoHbmController.MAX_TIME_SECONDS,
                            unit = "s",
                            enabled = enabled && supported,
                            valueText = "$enableTime s",
                            onValueChange = {
                                enableTime = it
                                AutoHbmController.setEnableTime(context, it)
                            },
                            onDefault = {
                                enableTime = AutoHbmController.DEFAULT_ENABLE_TIME_SECONDS
                                AutoHbmController.setEnableTime(context, enableTime)
                            }
                        )

                        SliderSetting(
                            title = dynamicStringResource(R.string.auto_hbm_disable_time_title),
                            value = disableTime,
                            range = AutoHbmController.MIN_TIME_SECONDS..AutoHbmController.MAX_TIME_SECONDS,
                            unit = "s",
                            enabled = enabled && supported,
                            valueText = "$disableTime s",
                            onValueChange = {
                                disableTime = it
                                AutoHbmController.setDisableTime(context, it)
                            },
                            onDefault = {
                                disableTime = AutoHbmController.DEFAULT_DISABLE_TIME_SECONDS
                                AutoHbmController.setDisableTime(context, disableTime)
                            }
                        )

                        GenericSwitchRow(
                            title = dynamicStringResource(R.string.auto_hbm_smooth_ramp_title),
                            summary = dynamicStringResource(R.string.auto_hbm_smooth_ramp_summary),
                            checked = smoothRampEnabled,
                            enabled = enabled && supported,
                            onCheckedChange = {
                                smoothRampEnabled = it
                                AutoHbmController.setSmoothRampEnabled(context, it)
                            }
                        )

                        if (smoothRampEnabled) {
                            SliderSetting(
                                title = dynamicStringResource(R.string.auto_hbm_ramp_time_title),
                                value = rampTimeMs,
                                range = AutoHbmController.MIN_RAMP_TIME_MS..AutoHbmController.MAX_RAMP_TIME_MS,
                                unit = "ms",
                                enabled = enabled && supported,
                                valueText = "$rampTimeMs ms",
                                onValueChange = {
                                    rampTimeMs = it
                                    AutoHbmController.setRampTimeMs(context, it)
                                },
                                onDefault = {
                                    rampTimeMs = AutoHbmController.DEFAULT_RAMP_TIME_MS
                                    AutoHbmController.setRampTimeMs(context, rampTimeMs)
                                }
                            )
                        }

                        SliderSetting(
                            title = dynamicStringResource(R.string.auto_hbm_max_active_time_title),
                            value = maxActiveTime,
                            range = AutoHbmController.MIN_TIMEOUT_SECONDS..AutoHbmController.MAX_TIMEOUT_SECONDS,
                            unit = "s",
                            enabled = enabled && supported,
                            valueText = "$maxActiveTime s",
                            onValueChange = {
                                maxActiveTime = it
                                AutoHbmController.setMaxActiveTime(context, it)
                            },
                            onDefault = {
                                maxActiveTime = AutoHbmController.DEFAULT_MAX_ACTIVE_TIME_SECONDS
                                AutoHbmController.setMaxActiveTime(context, maxActiveTime)
                            }
                        )

                        SliderSetting(
                            title = dynamicStringResource(R.string.auto_hbm_cooldown_time_title),
                            value = cooldownTime,
                            range = AutoHbmController.MIN_TIMEOUT_SECONDS..AutoHbmController.MAX_TIMEOUT_SECONDS,
                            unit = "s",
                            enabled = enabled && supported,
                            valueText = "$cooldownTime s",
                            onValueChange = {
                                cooldownTime = it
                                AutoHbmController.setCooldownTime(context, it)
                            },
                            onDefault = {
                                cooldownTime = AutoHbmController.DEFAULT_COOLDOWN_TIME_SECONDS
                                AutoHbmController.setCooldownTime(context, cooldownTime)
                            }
                        )

                        SliderSetting(
                            title = dynamicStringResource(R.string.auto_hbm_check_interval_title),
                            value = checkIntervalMs,
                            range = AutoHbmController.MIN_CHECK_INTERVAL_MS..AutoHbmController.MAX_CHECK_INTERVAL_MS,
                            unit = "ms",
                            enabled = enabled && supported,
                            valueText = "$checkIntervalMs ms",
                            onValueChange = {
                                checkIntervalMs = it
                                AutoHbmController.setCheckIntervalMs(context, it)
                            },
                            onDefault = {
                                checkIntervalMs = AutoHbmController.DEFAULT_CHECK_INTERVAL_MS
                                AutoHbmController.setCheckIntervalMs(context, checkIntervalMs)
                            }
                        )

                        SliderSetting(
                            title = dynamicStringResource(R.string.auto_hbm_temperature_limit_title),
                            value = temperatureLimit,
                            range = AutoHbmController.MIN_TEMPERATURE_LIMIT_C..AutoHbmController.MAX_TEMPERATURE_LIMIT_C,
                            unit = "°C",
                            enabled = enabled && supported,
                            valueText = "$temperatureLimit°C",
                            onValueChange = {
                                temperatureLimit = it
                                AutoHbmController.setTemperatureLimit(context, it)
                            },
                            onDefault = {
                                temperatureLimit = AutoHbmController.DEFAULT_TEMPERATURE_LIMIT_C
                                AutoHbmController.setTemperatureLimit(context, temperatureLimit)
                            }
                        )
                    }
                }

                item {
                    Button(
                        onClick = {
                            TileUtils.requestAddTileService(
                                context,
                                AutoHbmTileService::class.java,
                                R.string.auto_hbm_title,
                                R.drawable.ic_auto_hbm_tile
                            )
                        },
                        enabled = supported,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(Icons.Rounded.Add, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(8.dp))
                        Text(dynamicStringResource(R.string.auto_hbm_add_tile))
                    }
                }
            }

            TopBarBlurOverlay(
                modifier = Modifier.fillMaxWidth(),
                topBarHeight = 64.dp + WindowInsets.statusBars.asPaddingValues().calculateTopPadding(),
                blurState = blurState,
                isScrolled = isScrolled
            )
        }
    }
}

@Composable
private fun AutoHbmLuxCard(
    currentLux: Float,
    threshold: Int,
    hbmActive: Boolean,
    brightness: Int,
    maxBrightness: Int,
    socModel: String,
    currentTemperature: Float?
) {
    val progress = (currentLux / threshold.coerceAtLeast(1)).coerceIn(0f, 1f)
    val temperatureText = currentTemperature?.let { String.format(Locale.US, "%.1f°C", it) }
        ?: dynamicStringResource(R.string.auto_hbm_temperature_unknown)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.WbSunny,
                    contentDescription = null,
                    tint = if (hbmActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(28.dp)
                )
                Column(modifier = Modifier.padding(start = 16.dp).weight(1f)) {
                    Text(
                        text = dynamicStringResource(R.string.auto_hbm_current_lux_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = dynamicStringResource(
                            if (hbmActive) R.string.auto_hbm_status_active else R.string.auto_hbm_status_monitoring
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = currentLux.toInt().toString(),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }

            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                text = dynamicStringResource(
                    R.string.auto_hbm_status_format,
                    threshold,
                    brightness,
                    maxBrightness,
                    socModel,
                    temperatureText
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}