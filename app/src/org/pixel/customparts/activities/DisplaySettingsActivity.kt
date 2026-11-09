package org.pixel.customparts.activities

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.pixel.customparts.MainMenuNavigationRow
import org.pixel.customparts.R
import org.pixel.customparts.dynamicDarkColorScheme
import org.pixel.customparts.dynamicLightColorScheme
import org.pixel.customparts.ui.REBOOT_BUBBLE_CONTENT_BOTTOM_PADDING
import org.pixel.customparts.ui.RebootBubble
import org.pixel.customparts.ui.SettingsGroupCard
import org.pixel.customparts.ui.TopBarBlurOverlay
import org.pixel.customparts.ui.TopStatusVeil
import org.pixel.customparts.ui.recordLayer
import org.pixel.customparts.ui.rememberGraphicsLayerRecordingState
import androidx.compose.ui.res.stringResource

class DisplaySettingsActivity : ComponentActivity() {
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
                    DisplaySettingsScreen(onBack = { finish() })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DisplaySettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val blurState = rememberGraphicsLayerRecordingState()
    val listState = rememberLazyListState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentWindowInsets = WindowInsets.navigationBars,
        floatingActionButton = { RebootBubble() },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.display_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.nav_back))
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
                    .recordLayer(state = blurState, backgroundColor = MaterialTheme.colorScheme.surfaceContainer)
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
                        SettingsGroupCard(
                            title = stringResource(R.string.display_section_screen),
                        ) {
                            MainMenuNavigationRow(
                                title = stringResource(R.string.saturation_title),
                                subtitle = stringResource(R.string.saturation_summary),
                                icon = Icons.Rounded.Palette,
                                iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                iconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                                    context.startActivity(Intent(context, SaturationActivity::class.java))
                                }
                            )

                            HorizontalDivider()

                            MainMenuNavigationRow(
                                title = stringResource(R.string.auto_hbm_title),
                                subtitle = stringResource(R.string.auto_hbm_summary),
                                icon = Icons.Rounded.WbSunny,
                                iconContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                iconContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                                    context.startActivity(Intent(context, AutoHbmActivity::class.java))
                                }
                            )

                            HorizontalDivider()

                            MainMenuNavigationRow(
                                title = stringResource(R.string.dtw_title),
                                subtitle = stringResource(R.string.dtw_summary),
                                icon = Icons.Rounded.TouchApp,
                                iconContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                iconContentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                                    context.startActivity(Intent(context, DtwSettingsActivity::class.java))
                                }
                            )

                            HorizontalDivider()

                            MainMenuNavigationRow(
                                title = stringResource(R.string.auto_lock_title),
                                subtitle = stringResource(R.string.auto_lock_summary),
                                icon = Icons.Rounded.Timer,
                                iconContainerColor = MaterialTheme.colorScheme.errorContainer,
                                iconContentColor = MaterialTheme.colorScheme.onErrorContainer,
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                                    context.startActivity(Intent(context, AutoLockSettingsActivity::class.java))
                                }
                            )
                        }
                    }
            }

            TopBarBlurOverlay(
                modifier = Modifier.fillMaxWidth(),
                topBarHeight = 64.dp + WindowInsets.statusBars.asPaddingValues().calculateTopPadding(),
                blurState = blurState
            )
            TopStatusVeil(
                backgroundColor = MaterialTheme.colorScheme.surfaceContainer,
                veilHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 14.dp,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}