package org.pixel.extra.parts.activities

import android.content.Intent
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.pixel.extra.parts.R
import org.pixel.extra.parts.home.MainMenuNavigationRow
import org.pixel.extra.parts.dynamicDarkColorScheme
import org.pixel.extra.parts.dynamicLightColorScheme
import org.pixel.extra.parts.ui.legacy.REBOOT_BUBBLE_CONTENT_BOTTOM_PADDING
import org.pixel.extra.parts.ui.legacy.RebootBubble
import org.pixel.extra.parts.ui.legacy.SettingsGroupCard
import org.pixel.extra.parts.ui.TopBarBlurOverlay
import org.pixel.extra.parts.ui.recordLayer
import org.pixel.extra.parts.ui.rememberGraphicsLayerRecordingState
import androidx.compose.ui.res.stringResource


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DisplaySettingsScreen(onBack: () -> Unit, onNavigate: (String) -> Unit = {}) {
    val context = LocalContext.current
    val blurState = rememberGraphicsLayerRecordingState()
    val listState = rememberLazyListState()
    val isScrolled by androidx.compose.runtime.remember { derivedStateOf { listState.canScrollBackward } }

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
                        SettingsGroupCard(title = stringResource(R.string.display_section_screen)) {
                            MainMenuNavigationRow(
                                title = stringResource(R.string.saturation_title),
                                subtitle = stringResource(R.string.saturation_summary),
                                icon = Icons.Rounded.Palette,
                                iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                iconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                onClick = { onNavigate("saturation") }
                            )

                            HorizontalDivider()

                            MainMenuNavigationRow(
                                title = stringResource(R.string.auto_hbm_title),
                                subtitle = stringResource(R.string.auto_hbm_summary),
                                icon = Icons.Rounded.WbSunny,
                                iconContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                iconContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                onClick = { onNavigate("autohbm") }
                            )

                            HorizontalDivider()

                            MainMenuNavigationRow(
                                title = stringResource(R.string.dtw_title),
                                subtitle = stringResource(R.string.dtw_summary),
                                icon = Icons.Rounded.TouchApp,
                                iconContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                iconContentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                onClick = { onNavigate("dtw") }
                            )

                            HorizontalDivider()

                            MainMenuNavigationRow(
                                title = stringResource(R.string.auto_lock_title),
                                subtitle = stringResource(R.string.auto_lock_summary),
                                icon = Icons.Rounded.Timer,
                                iconContainerColor = MaterialTheme.colorScheme.errorContainer,
                                iconContentColor = MaterialTheme.colorScheme.onErrorContainer,
                                onClick = { onNavigate("autolock") }
                            )
                        }
                    }
            }

            TopBarBlurOverlay(
                modifier = Modifier.fillMaxWidth(),
                topBarHeight = 64.dp + WindowInsets.statusBars.asPaddingValues().calculateTopPadding(),
                blurState = blurState,
                blurRadius = org.pixel.extra.parts.ui.legacy.LegacyBlurRadius
            )
        }
    }
}