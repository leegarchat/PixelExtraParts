package org.pixel.customparts.activities

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import org.pixel.customparts.R
import org.pixel.customparts.dynamicDarkColorScheme
import org.pixel.customparts.dynamicLightColorScheme
import org.pixel.customparts.ui.GenericSwitchRow
import org.pixel.customparts.ui.REBOOT_BUBBLE_CONTENT_BOTTOM_PADDING
import org.pixel.customparts.ui.RebootBubble
import org.pixel.customparts.ui.SettingsGroupCard
import org.pixel.customparts.ui.SliderSetting
import org.pixel.customparts.ui.TopBarBlurOverlay
import org.pixel.customparts.ui.recordLayer
import org.pixel.customparts.ui.rememberGraphicsLayerRecordingState
import org.pixel.customparts.utils.AutoLockController
import org.pixel.customparts.utils.dynamicStringResource

class AutoLockSettingsActivity : ComponentActivity() {
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
                    AutoLockScreen(onBack = { finish() })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AutoLockScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val blurState = rememberGraphicsLayerRecordingState()
    val listState = rememberLazyListState()
    val isScrolled by remember { derivedStateOf { listState.canScrollBackward } }

    var enabled by remember { mutableStateOf(AutoLockController.isEnabled(context)) }
    var blockerOnly by remember {
        mutableStateOf(AutoLockController.getMode(context) == AutoLockController.MODE_BLOCKER_ONLY)
    }
    var pauseMedia by remember { mutableStateOf(AutoLockController.isPauseMediaEnabled(context)) }
    var timeoutSeconds by remember { mutableIntStateOf(AutoLockController.getTimeoutSeconds(context)) }
    var remainingSeconds by remember { mutableIntStateOf(-1) }

    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                enabled = intent.getBooleanExtra(AutoLockController.EXTRA_ENABLED, enabled)
                timeoutSeconds = intent.getIntExtra(AutoLockController.EXTRA_TIMEOUT, timeoutSeconds)
                blockerOnly = AutoLockController.getMode(context) == AutoLockController.MODE_BLOCKER_ONLY
                remainingSeconds = intent.getIntExtra(AutoLockController.EXTRA_REMAINING, remainingSeconds)
            }
        }
        ContextCompat.registerReceiver(
            context, receiver,
            IntentFilter(AutoLockController.ACTION_STATE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentWindowInsets = WindowInsets.navigationBars,
        floatingActionButton = { RebootBubble() },
        topBar = {
            TopAppBar(
                title = { Text(dynamicStringResource(R.string.auto_lock_title), fontWeight = FontWeight.Bold) },
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
                    SettingsGroupCard(title = dynamicStringResource(R.string.auto_lock_title)) {
                        GenericSwitchRow(
                            title = dynamicStringResource(R.string.auto_lock_enable_title),
                            summary = dynamicStringResource(R.string.auto_lock_enable_summary),
                            checked = enabled,
                            onCheckedChange = {
                                enabled = it
                                AutoLockController.setMode(
                                    context,
                                    if (it) {
                                        if (blockerOnly) AutoLockController.MODE_BLOCKER_ONLY
                                        else AutoLockController.MODE_ALWAYS
                                    } else {
                                        AutoLockController.MODE_OFF
                                    }
                                )
                            }
                        )

                        GenericSwitchRow(
                            title = dynamicStringResource(R.string.auto_lock_blocker_only_title),
                            summary = dynamicStringResource(R.string.auto_lock_blocker_only_summary),
                            checked = blockerOnly,
                            enabled = enabled,
                            onCheckedChange = {
                                blockerOnly = it
                                AutoLockController.setMode(
                                    context,
                                    if (!enabled) AutoLockController.MODE_OFF
                                    else if (it) AutoLockController.MODE_BLOCKER_ONLY
                                    else AutoLockController.MODE_ALWAYS
                                )
                            }
                        )

                        GenericSwitchRow(
                            title = dynamicStringResource(R.string.auto_lock_pause_media_title),
                            summary = dynamicStringResource(R.string.auto_lock_pause_media_summary),
                            checked = pauseMedia,
                            enabled = enabled,
                            onCheckedChange = {
                                pauseMedia = it
                                AutoLockController.setPauseMediaEnabled(context, it)
                            }
                        )
                    }
                }

                item {
                    SettingsGroupCard(
                        title = dynamicStringResource(R.string.auto_lock_timer_section),
                        enabled = enabled
                    ) {
                        SliderSetting(
                            title = dynamicStringResource(R.string.auto_lock_timeout_title),
                            value = timeoutSeconds,
                            range = AutoLockController.MIN_TIMEOUT_SECONDS..AutoLockController.MAX_TIMEOUT_SECONDS,
                            unit = "",
                            enabled = enabled,
                            valueText = AutoLockController.formatTimeout(context, timeoutSeconds),
                            showValueBelow = true,
                            onValueChange = {
                                timeoutSeconds = it
                                AutoLockController.setTimeoutSeconds(context, timeoutSeconds)
                                AutoLockController.publishState(context)
                            },
                            onDefault = {
                                timeoutSeconds = AutoLockController.DEFAULT_TIMEOUT_SECONDS
                                AutoLockController.setTimeoutSeconds(context, timeoutSeconds)
                                AutoLockController.publishState(context)
                            }
                        )

                        Text(
                            text = dynamicStringResource(R.string.auto_lock_timeout_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
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
