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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.pixel.customparts.R
import org.pixel.customparts.dynamicDarkColorScheme
import org.pixel.customparts.dynamicLightColorScheme
import org.pixel.customparts.services.PixelPartsLogTileService
import org.pixel.customparts.ui.REBOOT_BUBBLE_CONTENT_BOTTOM_PADDING
import org.pixel.customparts.ui.GenericSwitchRow
import org.pixel.customparts.ui.RebootBubble
import org.pixel.customparts.ui.RebootBubbleMenuAction
import org.pixel.customparts.ui.SettingsGroupCard
import org.pixel.customparts.ui.TopBarBlurOverlay
import org.pixel.customparts.ui.addons.AddonMainEntry
import org.pixel.customparts.ui.addons.AddonMainEntryRow
import org.pixel.customparts.ui.addons.scanAddonActivityEntries
import org.pixel.customparts.ui.recordLayer
import org.pixel.customparts.ui.rememberGraphicsLayerRecordingState
import org.pixel.customparts.utils.PixelPartsLogController
import org.pixel.customparts.utils.TileUtils
import org.pixel.customparts.utils.dynamicStringResource

class SystemUISettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val darkTheme = isSystemInDarkTheme()
            val context = LocalContext.current
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
    val context = LocalContext.current
    val blurState = rememberGraphicsLayerRecordingState()
    val lazyListState = rememberLazyListState()
    val isScrolled by remember { derivedStateOf { lazyListState.canScrollBackward } }
    var injectedAddonEntries by remember { mutableStateOf<List<AddonMainEntry>>(emptyList()) }
    var logServiceRunning by remember { mutableStateOf(PixelPartsLogController.isServiceRunning(context)) }
    var logcatEnabled by remember { mutableStateOf(PixelPartsLogController.isLogcatEnabled(context)) }
    var dmesgEnabled by remember { mutableStateOf(PixelPartsLogController.isDmesgEnabled(context)) }
    var crashLogEnabled by remember { mutableStateOf(PixelPartsLogController.isCrashesEnabled(context)) }

    LaunchedEffect(Unit) {
        injectedAddonEntries = withContext(Dispatchers.IO) {
            scanAddonActivityEntries(context, "SystemUISettingsActivity")
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            logServiceRunning = PixelPartsLogController.isServiceRunning(context)
            delay(1000L)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        floatingActionButton = {
            RebootBubble(
                extraActions = listOf(
                    RebootBubbleMenuAction(
                        icon = Icons.Filled.Add,
                        label = dynamicStringResource(R.string.sysui_log_service_add_tile),
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        onClick = {
                            TileUtils.requestAddTileService(
                                context,
                                PixelPartsLogTileService::class.java,
                                R.string.sysui_log_service_title,
                                R.drawable.ic_log_tile
                            )
                        }
                    )
                )
            )
        },
        topBar = {
            TopAppBar(
                title = { Text(dynamicStringResource(R.string.sysui_settings_title), fontWeight = FontWeight.Bold) },
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
                if (injectedAddonEntries.isNotEmpty()) {
                    item(key = "systemui_addon_injected") {
                        SystemUIAddonInjectedSettings(
                            entries = injectedAddonEntries
                        )
                    }
                }

                item {
                    SettingsGroupCard(title = dynamicStringResource(R.string.sysui_log_service_title)) {
                        LogServiceControlRow(
                            running = logServiceRunning,
                            onClick = {
                                if (logServiceRunning) {
                                    PixelPartsLogController.stopLogging(context)
                                } else {
                                    PixelPartsLogController.startLogging(context)
                                }
                                logServiceRunning = PixelPartsLogController.isServiceRunning(context)
                            }
                        )

                        HorizontalDivider()

                        GenericSwitchRow(
                            title = dynamicStringResource(R.string.sysui_logcat_title),
                            summary = dynamicStringResource(R.string.sysui_logcat_summary),
                            checked = logcatEnabled,
                            onCheckedChange = { checked ->
                                logcatEnabled = checked
                                PixelPartsLogController.setLogcatEnabled(context, checked)
                            }
                        )

                        HorizontalDivider()

                        GenericSwitchRow(
                            title = dynamicStringResource(R.string.sysui_dmesg_title),
                            summary = dynamicStringResource(R.string.sysui_dmesg_summary),
                            checked = dmesgEnabled,
                            onCheckedChange = { checked ->
                                dmesgEnabled = checked
                                PixelPartsLogController.setDmesgEnabled(context, checked)
                            }
                        )

                        HorizontalDivider()

                        GenericSwitchRow(
                            title = dynamicStringResource(R.string.sysui_crash_log_title),
                            summary = dynamicStringResource(R.string.sysui_crash_log_summary),
                            checked = crashLogEnabled,
                            onCheckedChange = { checked ->
                                crashLogEnabled = checked
                                PixelPartsLogController.setCrashesEnabled(context, checked)
                            }
                        )
                    }
                }

                item {
                    SettingsGroupCard(title = dynamicStringResource(R.string.magnifier_section_title)) {
                        SystemUIStaticNavigationRow(
                            title = dynamicStringResource(R.string.magnifier_section_title),
                            subtitle = dynamicStringResource(R.string.magnifier_enable_summary),
                            onClick = {
                                context.startActivity(Intent(context, MagnifierSettingsActivity::class.java))
                            }
                        )
                    }
                }

                item {
                    SettingsGroupCard(title = dynamicStringResource(R.string.anim_transition_title)) {
                        SystemUIStaticNavigationRow(
                            title = dynamicStringResource(R.string.anim_transition_title),
                            subtitle = dynamicStringResource(R.string.anim_transition_subtitle),
                            onClick = {
                                context.startActivity(Intent(context, ActivityTransitionActivity::class.java))
                            }
                        )
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

@Composable
private fun SystemUIAddonInjectedSettings(
    entries: List<AddonMainEntry>
) {
    val context = LocalContext.current
    SettingsGroupCard(title = dynamicStringResource(R.string.addon_settings_title)) {
        entries.forEachIndexed { index, entry ->
            if (index > 0) HorizontalDivider()
            key(entry.rawId) {
                AddonMainEntryRow(
                    entry = entry,
                    onClick = {
                        AddonPageActivity.start(
                            context = context,
                            addonId = entry.addonId,
                            pageId = entry.leafId,
                            title = entry.title,
                            includeTargetActivityEntries = true
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun SystemUIStaticNavigationRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(12.dp))
        Icon(Icons.Filled.ChevronRight, null)
    }
}

@Composable
private fun LogServiceControlRow(
    running: Boolean,
    onClick: () -> Unit
) {
    val statusColor = if (running) Color(0xFF2E7D32) else Color(0xFFC62828)
    val statusIcon = if (running) Icons.Filled.CheckCircle else Icons.Filled.Cancel
    val statusText = dynamicStringResource(
        if (running) R.string.sysui_log_service_status_running else R.string.sysui_log_service_status_stopped
    )
    val buttonIcon = if (running) Icons.Filled.Stop else Icons.Filled.PlayArrow
    val buttonText = dynamicStringResource(
        if (running) R.string.sysui_log_service_stop else R.string.sysui_log_service_start
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = dynamicStringResource(R.string.sysui_log_service_enable_title),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = dynamicStringResource(R.string.sysui_log_service_enable_summary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(statusIcon, null, tint = statusColor, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelMedium,
                    color = statusColor
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        FilledTonalButton(onClick = onClick) {
            Icon(buttonIcon, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(buttonText)
        }
    }
}
