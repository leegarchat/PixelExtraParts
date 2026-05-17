package org.pixel.customparts.activities

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.pixel.customparts.R
import org.pixel.customparts.SettingsKeys
import org.pixel.customparts.dynamicDarkColorScheme
import org.pixel.customparts.dynamicLightColorScheme
import org.pixel.customparts.services.GestureBarTileService
import org.pixel.customparts.ui.ColorPickerDialog
import org.pixel.customparts.ui.GenericSwitchRow
import org.pixel.customparts.ui.REBOOT_BUBBLE_CONTENT_BOTTOM_PADDING
import org.pixel.customparts.ui.RebootBubble
import org.pixel.customparts.ui.RebootBubbleMenuAction
import org.pixel.customparts.ui.SettingsGroupCard
import org.pixel.customparts.ui.SliderSetting
import org.pixel.customparts.ui.StrongDivider
import org.pixel.customparts.ui.TopBarBlurOverlay
import org.pixel.customparts.ui.WeakDivider
import org.pixel.customparts.ui.recordLayer
import org.pixel.customparts.ui.rememberGraphicsLayerRecordingState
import org.pixel.customparts.utils.SettingsCompat
import org.pixel.customparts.utils.TileUtils
import org.pixel.customparts.utils.dynamicStringResource

private const val DEFAULT_WIDTH_PERCENT = 28
private const val DEFAULT_HEIGHT_DP = 4
private const val DEFAULT_RESERVED_AREA_DP = 24
private const val DEFAULT_GESTURE_AREA_DP = 24
private const val DEFAULT_ALPHA_PERCENT = 100
private const val DEFAULT_HIDE_TIMEOUT_MS = 3000
private const val DEFAULT_FADE_MS = 220
private const val DEFAULT_TINT_COLOR = 0xFFFFFFFF.toInt()

class GestureBarSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val darkTheme = isSystemInDarkTheme()
            val context = androidx.compose.ui.platform.LocalContext.current
            val colorScheme = if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

            MaterialTheme(colorScheme = colorScheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    GestureBarSettingsScreen(onBack = { finish() })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GestureBarSettingsScreen(onBack: () -> Unit) {
    val blurState = rememberGraphicsLayerRecordingState()
    val lazyListState = rememberLazyListState()
    val isScrolled by remember { derivedStateOf { lazyListState.canScrollBackward } }
    val context = androidx.compose.ui.platform.LocalContext.current

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        floatingActionButton = {
            RebootBubble(
                extraActions = listOf(
                    RebootBubbleMenuAction(
                        icon = Icons.Rounded.Add,
                        label = dynamicStringResource(R.string.gesture_bar_add_tile),
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        onClick = {
                            TileUtils.requestAddTileService(
                                context,
                                GestureBarTileService::class.java,
                                R.string.sysui_gesture_bar_title,
                                R.drawable.ic_gesture_bar_tile
                            )
                        }
                    )
                )
            )
        },
        topBar = {
            TopAppBar(
                title = {
                    Text(dynamicStringResource(R.string.gesture_bar_settings_title), fontWeight = FontWeight.Bold)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
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
                state = lazyListState,
                modifier = Modifier
                    .fillMaxSize()
                    .recordLayer(blurState)
                    .background(MaterialTheme.colorScheme.surfaceContainer),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    top = 16.dp + innerPadding.calculateTopPadding(),
                    end = 16.dp,
                    bottom = REBOOT_BUBBLE_CONTENT_BOTTOM_PADDING + innerPadding.calculateBottomPadding()
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item { GestureBarSettingsContent() }
            }

            TopBarBlurOverlay(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter),
                topBarHeight = innerPadding.calculateTopPadding(),
                blurState = blurState,
                isScrolled = isScrolled
            )
        }
    }
}

@Composable
private fun GestureBarSettingsContent() {
    val context = androidx.compose.ui.platform.LocalContext.current

    var enabled by remember { mutableStateOf(SettingsCompat.isEnabled(context, SettingsKeys.GESTURE_BAR_ENABLED)) }
    var widthPercent by remember { mutableIntStateOf(SettingsCompat.getInt(context, SettingsKeys.GESTURE_BAR_WIDTH_PERCENT, DEFAULT_WIDTH_PERCENT)) }
    var heightDp by remember { mutableIntStateOf(SettingsCompat.getInt(context, SettingsKeys.GESTURE_BAR_HEIGHT_DP, DEFAULT_HEIGHT_DP)) }
    var offsetX by remember { mutableIntStateOf(SettingsCompat.getInt(context, SettingsKeys.GESTURE_BAR_OFFSET_X_DP, 0)) }
    var offsetY by remember { mutableIntStateOf(SettingsCompat.getInt(context, SettingsKeys.GESTURE_BAR_OFFSET_Y_DP, 0)) }
    var reservedArea by remember { mutableIntStateOf(SettingsCompat.getInt(context, SettingsKeys.GESTURE_BAR_RESERVED_AREA_DP, DEFAULT_RESERVED_AREA_DP)) }
    var gestureArea by remember { mutableIntStateOf(SettingsCompat.getInt(context, SettingsKeys.GESTURE_BAR_GESTURE_AREA_DP, DEFAULT_GESTURE_AREA_DP)) }
    var hideOnLauncher by remember { mutableStateOf(SettingsCompat.isEnabled(context, SettingsKeys.GESTURE_BAR_HIDE_ON_LAUNCHER, false)) }
    var hideInApps by remember { mutableStateOf(SettingsCompat.isEnabled(context, SettingsKeys.GESTURE_BAR_HIDE_IN_APPS, false)) }
    var launcherHideTimeout by remember { mutableIntStateOf(SettingsCompat.getInt(context, SettingsKeys.GESTURE_BAR_LAUNCHER_HIDE_TIMEOUT_MS, DEFAULT_HIDE_TIMEOUT_MS)) }
    var appHideTimeout by remember { mutableIntStateOf(SettingsCompat.getInt(context, SettingsKeys.GESTURE_BAR_APP_HIDE_TIMEOUT_MS, DEFAULT_HIDE_TIMEOUT_MS)) }
    var hideOnLockscreen by remember { mutableStateOf(SettingsCompat.isEnabled(context, SettingsKeys.GESTURE_BAR_HIDE_ON_LOCKSCREEN, false)) }
    var removeReservedArea by remember { mutableStateOf(SettingsCompat.isEnabled(context, SettingsKeys.GESTURE_BAR_REMOVE_RESERVED_AREA, false)) }
    var alphaPercent by remember { mutableIntStateOf(SettingsCompat.getInt(context, SettingsKeys.GESTURE_BAR_ALPHA_PERCENT, DEFAULT_ALPHA_PERCENT)) }
    var fadeInMs by remember { mutableIntStateOf(SettingsCompat.getInt(context, SettingsKeys.GESTURE_BAR_FADE_IN_MS, DEFAULT_FADE_MS)) }
    var fadeOutMs by remember { mutableIntStateOf(SettingsCompat.getInt(context, SettingsKeys.GESTURE_BAR_FADE_OUT_MS, DEFAULT_FADE_MS)) }
    var tintEnabled by remember { mutableStateOf(SettingsCompat.isEnabled(context, SettingsKeys.GESTURE_BAR_TINT_ENABLED, false)) }
    var tintColor by remember { mutableIntStateOf(SettingsCompat.getInt(context, SettingsKeys.GESTURE_BAR_TINT_COLOR, DEFAULT_TINT_COLOR)) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SettingsGroupCard(title = dynamicStringResource(R.string.gesture_bar_behavior_title)) {
            GenericSwitchRow(
                title = dynamicStringResource(R.string.gesture_bar_enable_title),
                summary = dynamicStringResource(R.string.gesture_bar_enable_summary),
                checked = enabled,
                onCheckedChange = {
                    enabled = it
                    SettingsCompat.putInt(context, SettingsKeys.GESTURE_BAR_ENABLED, if (it) 1 else 0)
                }
            )
            WeakDivider()
            GenericSwitchRow(
                title = dynamicStringResource(R.string.gesture_bar_hide_launcher_title),
                summary = dynamicStringResource(R.string.gesture_bar_hide_launcher_summary),
                checked = hideOnLauncher,
                enabled = enabled,
                onCheckedChange = {
                    hideOnLauncher = it
                    SettingsCompat.putInt(context, SettingsKeys.GESTURE_BAR_HIDE_ON_LAUNCHER, if (it) 1 else 0)
                }
            )
            WeakDivider()
            SliderSetting(
                title = dynamicStringResource(R.string.gesture_bar_launcher_hide_timeout_title),
                value = launcherHideTimeout,
                range = 0..5000,
                unit = "ms",
                enabled = enabled && hideOnLauncher,
                onValueChange = {
                    launcherHideTimeout = it
                    SettingsCompat.putInt(context, SettingsKeys.GESTURE_BAR_LAUNCHER_HIDE_TIMEOUT_MS, it)
                },
                onDefault = {
                    launcherHideTimeout = DEFAULT_HIDE_TIMEOUT_MS
                    SettingsCompat.putInt(context, SettingsKeys.GESTURE_BAR_LAUNCHER_HIDE_TIMEOUT_MS, DEFAULT_HIDE_TIMEOUT_MS)
                }
            )
            WeakDivider()
            GenericSwitchRow(
                title = dynamicStringResource(R.string.gesture_bar_hide_apps_title),
                summary = dynamicStringResource(R.string.gesture_bar_hide_apps_summary),
                checked = hideInApps,
                enabled = enabled,
                onCheckedChange = {
                    hideInApps = it
                    SettingsCompat.putInt(context, SettingsKeys.GESTURE_BAR_HIDE_IN_APPS, if (it) 1 else 0)
                }
            )
            WeakDivider()
            SliderSetting(
                title = dynamicStringResource(R.string.gesture_bar_app_hide_timeout_title),
                value = appHideTimeout,
                range = 0..5000,
                unit = "ms",
                enabled = enabled && hideInApps,
                onValueChange = {
                    appHideTimeout = it
                    SettingsCompat.putInt(context, SettingsKeys.GESTURE_BAR_APP_HIDE_TIMEOUT_MS, it)
                },
                onDefault = {
                    appHideTimeout = DEFAULT_HIDE_TIMEOUT_MS
                    SettingsCompat.putInt(context, SettingsKeys.GESTURE_BAR_APP_HIDE_TIMEOUT_MS, DEFAULT_HIDE_TIMEOUT_MS)
                }
            )
            WeakDivider()
            GenericSwitchRow(
                title = dynamicStringResource(R.string.gesture_bar_hide_lockscreen_title),
                summary = dynamicStringResource(R.string.gesture_bar_hide_lockscreen_summary),
                checked = hideOnLockscreen,
                enabled = enabled,
                onCheckedChange = {
                    hideOnLockscreen = it
                    SettingsCompat.putInt(context, SettingsKeys.GESTURE_BAR_HIDE_ON_LOCKSCREEN, if (it) 1 else 0)
                }
            )
            WeakDivider()
            GenericSwitchRow(
                title = dynamicStringResource(R.string.gesture_bar_remove_reserved_title),
                summary = dynamicStringResource(R.string.gesture_bar_remove_reserved_summary),
                checked = removeReservedArea,
                enabled = enabled,
                onCheckedChange = {
                    removeReservedArea = it
                    SettingsCompat.putInt(context, SettingsKeys.GESTURE_BAR_REMOVE_RESERVED_AREA, if (it) 1 else 0)
                }
            )
        }

        SettingsGroupCard(title = dynamicStringResource(R.string.gesture_bar_geometry_title)) {
            SliderSetting(
                title = dynamicStringResource(R.string.gesture_bar_width_title),
                value = widthPercent,
                range = 0..100,
                unit = "%",
                enabled = enabled,
                onValueChange = {
                    widthPercent = it
                    SettingsCompat.putInt(context, SettingsKeys.GESTURE_BAR_WIDTH_PERCENT, it)
                },
                onDefault = {
                    widthPercent = DEFAULT_WIDTH_PERCENT
                    SettingsCompat.putInt(context, SettingsKeys.GESTURE_BAR_WIDTH_PERCENT, DEFAULT_WIDTH_PERCENT)
                }
            )
            WeakDivider()
            SliderSetting(
                title = dynamicStringResource(R.string.gesture_bar_height_title),
                value = heightDp,
                range = 0..64,
                unit = "dp",
                enabled = enabled,
                onValueChange = {
                    heightDp = it
                    SettingsCompat.putInt(context, SettingsKeys.GESTURE_BAR_HEIGHT_DP, it)
                },
                onDefault = {
                    heightDp = DEFAULT_HEIGHT_DP
                    SettingsCompat.putInt(context, SettingsKeys.GESTURE_BAR_HEIGHT_DP, DEFAULT_HEIGHT_DP)
                }
            )
            WeakDivider()
            SliderSetting(
                title = dynamicStringResource(R.string.gesture_bar_offset_x_title),
                value = offsetX,
                range = -200..200,
                unit = "dp",
                enabled = enabled,
                onValueChange = {
                    offsetX = it
                    SettingsCompat.putInt(context, SettingsKeys.GESTURE_BAR_OFFSET_X_DP, it)
                },
                onDefault = {
                    offsetX = 0
                    SettingsCompat.putInt(context, SettingsKeys.GESTURE_BAR_OFFSET_X_DP, 0)
                }
            )
            WeakDivider()
            SliderSetting(
                title = dynamicStringResource(R.string.gesture_bar_offset_y_title),
                value = offsetY,
                range = -80..80,
                unit = "dp",
                enabled = enabled,
                onValueChange = {
                    offsetY = it
                    SettingsCompat.putInt(context, SettingsKeys.GESTURE_BAR_OFFSET_Y_DP, it)
                },
                onDefault = {
                    offsetY = 0
                    SettingsCompat.putInt(context, SettingsKeys.GESTURE_BAR_OFFSET_Y_DP, 0)
                }
            )
            StrongDivider()
            SliderSetting(
                title = dynamicStringResource(R.string.gesture_bar_reserved_area_title),
                value = reservedArea,
                range = 0..160,
                unit = "dp",
                enabled = enabled && !removeReservedArea,
                onValueChange = {
                    reservedArea = it
                    SettingsCompat.putInt(context, SettingsKeys.GESTURE_BAR_RESERVED_AREA_DP, it)
                },
                onDefault = {
                    reservedArea = DEFAULT_RESERVED_AREA_DP
                    SettingsCompat.putInt(context, SettingsKeys.GESTURE_BAR_RESERVED_AREA_DP, DEFAULT_RESERVED_AREA_DP)
                }
            )
            WeakDivider()
            SliderSetting(
                title = dynamicStringResource(R.string.gesture_bar_area_title),
                value = gestureArea,
                range = 0..160,
                unit = "dp",
                enabled = enabled,
                onValueChange = {
                    gestureArea = it
                    SettingsCompat.putInt(context, SettingsKeys.GESTURE_BAR_GESTURE_AREA_DP, it)
                },
                onDefault = {
                    gestureArea = DEFAULT_GESTURE_AREA_DP
                    SettingsCompat.putInt(context, SettingsKeys.GESTURE_BAR_GESTURE_AREA_DP, DEFAULT_GESTURE_AREA_DP)
                }
            )
        }

        SettingsGroupCard(title = dynamicStringResource(R.string.gesture_bar_visibility_title)) {
            SliderSetting(
                title = dynamicStringResource(R.string.gesture_bar_alpha_title),
                value = alphaPercent,
                range = 0..100,
                unit = "%",
                enabled = enabled,
                onValueChange = {
                    alphaPercent = it
                    SettingsCompat.putInt(context, SettingsKeys.GESTURE_BAR_ALPHA_PERCENT, it)
                },
                onDefault = {
                    alphaPercent = DEFAULT_ALPHA_PERCENT
                    SettingsCompat.putInt(context, SettingsKeys.GESTURE_BAR_ALPHA_PERCENT, DEFAULT_ALPHA_PERCENT)
                }
            )
            WeakDivider()
            SliderSetting(
                title = dynamicStringResource(R.string.gesture_bar_fade_in_title),
                value = fadeInMs,
                range = 20..1500,
                unit = "ms",
                enabled = enabled,
                onValueChange = {
                    fadeInMs = it
                    SettingsCompat.putInt(context, SettingsKeys.GESTURE_BAR_FADE_IN_MS, it)
                },
                onDefault = {
                    fadeInMs = DEFAULT_FADE_MS
                    SettingsCompat.putInt(context, SettingsKeys.GESTURE_BAR_FADE_IN_MS, DEFAULT_FADE_MS)
                }
            )
            WeakDivider()
            SliderSetting(
                title = dynamicStringResource(R.string.gesture_bar_fade_out_title),
                value = fadeOutMs,
                range = 20..1500,
                unit = "ms",
                enabled = enabled,
                onValueChange = {
                    fadeOutMs = it
                    SettingsCompat.putInt(context, SettingsKeys.GESTURE_BAR_FADE_OUT_MS, it)
                },
                onDefault = {
                    fadeOutMs = DEFAULT_FADE_MS
                    SettingsCompat.putInt(context, SettingsKeys.GESTURE_BAR_FADE_OUT_MS, DEFAULT_FADE_MS)
                }
            )
        }

        SettingsGroupCard(title = dynamicStringResource(R.string.gesture_bar_appearance_title)) {
            GenericSwitchRow(
                title = dynamicStringResource(R.string.gesture_bar_tint_title),
                summary = dynamicStringResource(R.string.gesture_bar_tint_summary),
                checked = tintEnabled,
                enabled = enabled,
                onCheckedChange = {
                    tintEnabled = it
                    SettingsCompat.putInt(context, SettingsKeys.GESTURE_BAR_TINT_ENABLED, if (it) 1 else 0)
                }
            )
            GestureBarColorRow(
                color = tintColor,
                enabled = enabled && tintEnabled,
                onColorChange = {
                    tintColor = it
                    SettingsCompat.putInt(context, SettingsKeys.GESTURE_BAR_TINT_COLOR, it)
                }
            )
        }
    }
}

@Composable
private fun GestureBarColorRow(
    color: Int,
    enabled: Boolean,
    onColorChange: (Int) -> Unit
) {
    var showColorPicker by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = dynamicStringResource(R.string.gesture_bar_tint_color_title),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.4f)
            )
            Text(
                text = dynamicStringResource(R.string.gesture_bar_tint_color_summary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.4f)
            )
        }
        Box(
            modifier = Modifier
                .padding(start = 12.dp)
                .size(34.dp)
                .clip(CircleShape)
                .background(Color(color))
                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                .clickable(enabled = enabled) { showColorPicker = true }
        )
    }

    if (showColorPicker) {
        ColorPickerDialog(
            initialColor = color,
            onColorSelected = {
                showColorPicker = false
                onColorChange(it)
            },
            onDismissRequest = { showColorPicker = false }
        )
    }
}