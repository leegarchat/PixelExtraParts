package org.pixel.customparts.activities

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.pixel.customparts.R
import org.pixel.customparts.SettingsKeys
import org.pixel.customparts.dynamicDarkColorScheme
import org.pixel.customparts.dynamicLightColorScheme
import org.pixel.customparts.ui.GenericSwitchRow
import org.pixel.customparts.ui.RebootBubble
import org.pixel.customparts.ui.REBOOT_BUBBLE_CONTENT_BOTTOM_PADDING
import org.pixel.customparts.ui.SettingsGroupCard
import org.pixel.customparts.ui.TopBarBlurOverlay
import org.pixel.customparts.ui.recordLayer
import org.pixel.customparts.ui.rememberGraphicsLayerRecordingState
import org.pixel.customparts.utils.SettingsCompat
import org.pixel.customparts.utils.dynamicStringResource

class SystemUISettingsActivity : ComponentActivity() {
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
                    SystemUISettingsScreen(onBack = { finish() })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemUISettingsScreen(onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val blurState = rememberGraphicsLayerRecordingState()
    val lazyListState = androidx.compose.foundation.lazy.rememberLazyListState()
    val isScrolled by remember { derivedStateOf { lazyListState.canScrollBackward } }
    val legacyAodAppIconsEnabled = SettingsCompat.isEnabled(
        context,
        SettingsKeys.AOD_FULL_COLOR_NOTIFICATION_ICONS,
        false
    )
    var statusBarUseAppIcons by remember {
        mutableStateOf(SettingsCompat.isEnabled(context, SettingsKeys.STATUS_BAR_USE_APP_ICONS, false))
    }
    var statusBarMonochromeIcons by remember {
        mutableStateOf(SettingsCompat.isEnabled(context, SettingsKeys.STATUS_BAR_MONOCHROME_NOTIFICATION_ICONS, false))
    }
    var aodUseAppIcons by remember {
        mutableStateOf(SettingsCompat.isEnabled(context, SettingsKeys.AOD_USE_APP_ICONS, legacyAodAppIconsEnabled))
    }
    var aodMonochromeIcons by remember {
        mutableStateOf(SettingsCompat.isEnabled(context, SettingsKeys.AOD_MONOCHROME_NOTIFICATION_ICONS, false))
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        floatingActionButton = { RebootBubble() },
        topBar = {
            TopAppBar(
                title = {
                    Text(dynamicStringResource(R.string.sysui_settings_title), fontWeight = FontWeight.Bold)
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
                item {
                    SettingsGroupCard(title = dynamicStringResource(R.string.sysui_settings_title)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    context.startActivity(
                                        Intent(
                                            context,
                                            LockscreenSettingsActivity::class.java
                                        )
                                    )
                                }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = dynamicStringResource(R.string.sysui_lockscreen_title),
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = dynamicStringResource(R.string.sysui_lockscreen_subtitle),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(Icons.Filled.ChevronRight, null)
                        }

                        HorizontalDivider()

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    context.startActivity(
                                        Intent(
                                            context,
                                            ShadeSettingsActivity::class.java
                                        )
                                    )
                                }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = dynamicStringResource(R.string.sysui_shade_title),
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = dynamicStringResource(R.string.sysui_shade_subtitle),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(Icons.Filled.ChevronRight, null)
                        }

                        HorizontalDivider()

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    context.startActivity(
                                        Intent(
                                            context,
                                            GestureBarSettingsActivity::class.java
                                        )
                                    )
                                }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = dynamicStringResource(R.string.sysui_gesture_bar_title),
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = dynamicStringResource(R.string.sysui_gesture_bar_subtitle),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(Icons.Filled.ChevronRight, null)
                        }
                    }
                }

                item {
                    SettingsGroupCard(title = dynamicStringResource(R.string.notification_icon_overrides_title)) {
                        GenericSwitchRow(
                            title = dynamicStringResource(R.string.status_bar_app_icons_title),
                            summary = dynamicStringResource(R.string.status_bar_app_icons_summary),
                            checked = statusBarUseAppIcons,
                            onCheckedChange = { checked ->
                                statusBarUseAppIcons = checked
                                SettingsCompat.putInt(context, SettingsKeys.STATUS_BAR_USE_APP_ICONS, if (checked) 1 else 0)
                                if (!checked) {
                                    statusBarMonochromeIcons = false
                                    SettingsCompat.putInt(context, SettingsKeys.STATUS_BAR_MONOCHROME_NOTIFICATION_ICONS, 0)
                                }
                            }
                        )

                        HorizontalDivider()

                        GenericSwitchRow(
                            title = dynamicStringResource(R.string.status_bar_monochrome_icons_title),
                            summary = dynamicStringResource(R.string.status_bar_monochrome_icons_summary),
                            checked = statusBarMonochromeIcons,
                            enabled = statusBarUseAppIcons,
                            onCheckedChange = { checked ->
                                statusBarMonochromeIcons = checked
                                SettingsCompat.putInt(context, SettingsKeys.STATUS_BAR_MONOCHROME_NOTIFICATION_ICONS, if (checked) 1 else 0)
                            }
                        )

                        HorizontalDivider()

                        GenericSwitchRow(
                            title = dynamicStringResource(R.string.aod_app_icons_title),
                            summary = dynamicStringResource(R.string.aod_app_icons_summary),
                            checked = aodUseAppIcons,
                            onCheckedChange = { checked ->
                                aodUseAppIcons = checked
                                SettingsCompat.putInt(context, SettingsKeys.AOD_USE_APP_ICONS, if (checked) 1 else 0)
                                if (!checked) {
                                    aodMonochromeIcons = false
                                    SettingsCompat.putInt(context, SettingsKeys.AOD_MONOCHROME_NOTIFICATION_ICONS, 0)
                                }
                            }
                        )

                        HorizontalDivider()

                        GenericSwitchRow(
                            title = dynamicStringResource(R.string.aod_monochrome_icons_title),
                            summary = dynamicStringResource(R.string.aod_monochrome_icons_summary),
                            checked = aodMonochromeIcons,
                            enabled = aodUseAppIcons,
                            onCheckedChange = { checked ->
                                aodMonochromeIcons = checked
                                SettingsCompat.putInt(context, SettingsKeys.AOD_MONOCHROME_NOTIFICATION_ICONS, if (checked) 1 else 0)
                            }
                        )
                    }
                }

                item {
                    SettingsGroupCard(title = dynamicStringResource(R.string.magnifier_section_title)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    context.startActivity(
                                        Intent(
                                            context,
                                            MagnifierSettingsActivity::class.java
                                        )
                                    )
                                }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = dynamicStringResource(R.string.magnifier_section_title),
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = dynamicStringResource(R.string.magnifier_enable_summary),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(Icons.Filled.ChevronRight, null)
                        }
                    }
                }

                item {
                    SettingsGroupCard(title = dynamicStringResource(R.string.anim_transition_title)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    context.startActivity(
                                        Intent(
                                            context,
                                            ActivityTransitionActivity::class.java
                                        )
                                    )
                                }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = dynamicStringResource(R.string.anim_transition_title),
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = dynamicStringResource(R.string.anim_transition_subtitle),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(Icons.Filled.ChevronRight, null)
                        }
                    }
                }

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
