package org.pixel.customparts.activities

import android.content.Intent
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.pixel.customparts.R
import org.pixel.customparts.SettingsKeys
import org.pixel.customparts.dynamicDarkColorScheme
import org.pixel.customparts.dynamicLightColorScheme
import org.pixel.customparts.services.DtwSensorService
import org.pixel.customparts.ui.GenericSwitchRow
import org.pixel.customparts.ui.REBOOT_BUBBLE_CONTENT_BOTTOM_PADDING
import org.pixel.customparts.ui.RebootBubble
import org.pixel.customparts.ui.SettingsGroupCard
import org.pixel.customparts.ui.SliderSetting
import org.pixel.customparts.ui.TopBarBlurOverlay
import org.pixel.customparts.ui.dynamicStringResource
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
    val supported = remember { DtwSensorService.isDt2wSupported(context) }

    var enabled by remember { mutableStateOf(SettingsCompat.isEnabled(context, SettingsKeys.DTW_ENABLED, false)) }
    var tapCount by remember { mutableIntStateOf(SettingsCompat.getInt(context, SettingsKeys.DTW_TAP_COUNT, 1)) }
    var timeoutMs by remember { mutableIntStateOf(SettingsCompat.getInt(context, SettingsKeys.DTW_TAP_TIMEOUT_MS, 300)) }
    var proximityCheck by remember { mutableStateOf(SettingsCompat.isEnabled(context, SettingsKeys.DTW_PROXIMITY_CHECK, false)) }
    var coordinateCheck by remember { mutableStateOf(SettingsCompat.isEnabled(context, SettingsKeys.DTW_COORDINATE_CHECK, false)) }
    var maxDistance by remember { mutableIntStateOf(SettingsCompat.getInt(context, SettingsKeys.DTW_MAX_DISTANCE_DP, 50)) }

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
                                if (it) {
                                    context.startService(Intent(context, DtwSensorService::class.java))
                                } else {
                                    context.stopService(Intent(context, DtwSensorService::class.java))
                                }
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
                            title = dynamicStringResource(R.string.dtw_proximity_check_title),
                            summary = dynamicStringResource(R.string.dtw_proximity_check_summary),
                            checked = proximityCheck,
                            enabled = enabled && supported,
                            onCheckedChange = {
                                proximityCheck = it
                                SettingsCompat.putInt(context, SettingsKeys.DTW_PROXIMITY_CHECK, if (it) 1 else 0)
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
                            SliderSetting(
                                title = dynamicStringResource(R.string.dtw_max_distance_title),
                                value = maxDistance,
                                range = 10..1000,
                                unit = "dp",
                                enabled = enabled && supported,
                                valueText = "$maxDistance dp",
                                onValueChange = {
                                    maxDistance = it
                                    SettingsCompat.putInt(context, SettingsKeys.DTW_MAX_DISTANCE_DP, it)
                                },
                                onDefault = {
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
        }
    }
}
