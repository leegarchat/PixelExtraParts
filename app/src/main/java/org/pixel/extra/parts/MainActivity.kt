package org.pixel.extra.parts

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.pixel.extra.parts.addons.AddonsScreen
import org.pixel.extra.parts.display.DisplayScreen
import org.pixel.extra.parts.dock.DockEntry
import org.pixel.extra.parts.dock.ExpressiveFloatingDockWithOverflow
import org.pixel.extra.parts.hiddenapps.HiddenAppsScreen
import org.pixel.extra.parts.home.HomeScreen
import org.pixel.extra.parts.icons.IconsScreen
import org.pixel.extra.parts.motion.rememberFloatBarMotion
import org.pixel.extra.parts.navigation.SectionRoutes
import org.pixel.extra.parts.overscroll.OverscrollScreen
import org.pixel.extra.parts.settings.MotionTuningSheet
import org.pixel.extra.parts.settings.SettingsScreen
import org.pixel.extra.parts.systemui.SystemUiScreen
import org.pixel.extra.parts.thermal.ThermalScreen
import org.pixel.extra.parts.ui.AppExpressiveTheme
import org.pixel.extra.parts.ui.DockStrip
import org.pixel.extra.parts.ui.TopBarBlurOverlay
import org.pixel.extra.parts.ui.TopStatusVeil
import org.pixel.extra.parts.ui.recordLayer
import org.pixel.extra.parts.ui.rememberGraphicsLayerRecordingState

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Edge-to-edge via WindowCompat; icon contrast is synced in AppExpressiveTheme.
        WindowCompat.enableEdgeToEdge(window)
        setContent {
            AppExpressiveTheme {
                PepRoot()
            }
        }
    }
}

// Collapsed bar pins: Home + Settings only. The future sections live in the
// overflow list below as clean slots; the editor can promote them to pins.
private val PinnedTabs = listOf(
    DockEntry(key = SectionRoutes.HOME, labelRes = R.string.tab_home, iconRes = R.drawable.ic_home),
    DockEntry(key = SectionRoutes.SETTINGS, labelRes = R.string.tab_settings, iconRes = R.drawable.ic_tune),
)

// Overflow slots for the migration stages: title-only screens, no logic yet.
private val SectionEntries = listOf(
    DockEntry(key = SectionRoutes.DISPLAY, labelRes = R.string.tab_display, iconRes = R.drawable.ic_search),
    DockEntry(key = SectionRoutes.SYSTEM_UI, labelRes = R.string.tab_systemui, iconRes = R.drawable.ic_info),
    DockEntry(key = SectionRoutes.ADDONS, labelRes = R.string.tab_addons, iconRes = R.drawable.ic_extension),
    DockEntry(key = SectionRoutes.OVERSCROLL, labelRes = R.string.tab_overscroll, iconRes = R.drawable.ic_more),
    DockEntry(key = SectionRoutes.THERMAL, labelRes = R.string.tab_thermal, iconRes = R.drawable.ic_share),
    DockEntry(key = SectionRoutes.ICONS, labelRes = R.string.tab_icons, iconRes = R.drawable.ic_favorite),
    DockEntry(key = SectionRoutes.HIDDEN_APPS, labelRes = R.string.tab_hidden_apps, iconRes = R.drawable.ic_archive),
)

private const val TOP_BAR_HEIGHT = 64 // dp

@Composable
private fun PepRoot() {
    var selectedKey by rememberSaveable { mutableStateOf(SectionRoutes.HOME) }
    var overflowExpanded by rememberSaveable { mutableStateOf(false) }
    var editorMode by rememberSaveable { mutableStateOf(false) }
    // Tuning state owned by the dock module: one holder shared by the dock
    // and the tuning sheet (loaded from disk once, saved on every change).
    val motionHolder = rememberFloatBarMotion()
    val motion = motionHolder.settings
    var showTuning by rememberSaveable { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    val blurState = rememberGraphicsLayerRecordingState()
    val blurRadius = motion.blurRadiusPx
    val rootBg = MaterialTheme.colorScheme.surfaceContainer

    BackHandler(enabled = editorMode) {
        editorMode = false
    }
    BackHandler(enabled = !editorMode && overflowExpanded) {
        overflowExpanded = false
    }

    val allEntries = remember { PinnedTabs + SectionEntries }
    val sectionOrder = remember {
        SectionRoutes.ORDERED.withIndex().associate { it.value to it.index }
    }
    val current = allEntries.firstOrNull { it.key == selectedKey } ?: PinnedTabs.first()
    val sectionLabel = stringResource(current.labelRes)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(rootBg)
    ) {
        // Main scrollable content: recorded once per frame for the shared blur.
        // Each screen is edge-to-edge (own contentPadding), so the list runs
        // under the top bar and the dock instead of being squeezed between them.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .recordLayer(state = blurState, backgroundColor = rootBg, blurRadius = blurRadius)
        ) {
            AnimatedContent(
                targetState = selectedKey,
                transitionSpec = {
                    val orderOf: (String) -> Int = { sectionOrder[it] ?: Int.MAX_VALUE }
                    val direction = if (orderOf(targetState) >= orderOf(initialState)) 1 else -1
                    (slideInHorizontally(
                        animationSpec = tween(320, easing = FastOutSlowInEasing),
                        initialOffsetX = { fullWidth -> (fullWidth * 0.18f * direction).toInt() }
                    ) + fadeIn(
                        animationSpec = tween(260)
                    ) + scaleIn(
                        initialScale = 0.96f,
                        animationSpec = tween(320, easing = FastOutSlowInEasing)
                    )).togetherWith(
                        slideOutHorizontally(
                            animationSpec = tween(260, easing = FastOutSlowInEasing),
                            targetOffsetX = { fullWidth -> (-fullWidth * 0.18f * direction).toInt() }
                        ) + fadeOut(
                            animationSpec = tween(200)
                        ) + scaleOut(
                            targetScale = 0.96f,
                            animationSpec = tween(260, easing = FastOutSlowInEasing)
                        )
                    ).using(SizeTransform(clip = false))
                },
                label = "ScreenTransition",
                modifier = Modifier.fillMaxSize(),
            ) { key ->
                when (key) {
                    SectionRoutes.DISPLAY -> DisplayScreen()
                    SectionRoutes.SYSTEM_UI -> SystemUiScreen()
                    SectionRoutes.ADDONS -> AddonsScreen()
                    SectionRoutes.OVERSCROLL -> OverscrollScreen()
                    SectionRoutes.THERMAL -> ThermalScreen()
                    SectionRoutes.ICONS -> IconsScreen()
                    SectionRoutes.HIDDEN_APPS -> HiddenAppsScreen()
                    SectionRoutes.SETTINGS -> SettingsScreen(
                        onOpenTuning = { showTuning = true },
                        onOpenEditor = {
                            overflowExpanded = true
                            editorMode = true
                        },
                    )
                    else -> HomeScreen(onOpenSection = { selectedKey = it })
                }
            }
        }

        // Floating overlays: siblings OUTSIDE recordLayer (no render loop).
        TopStatusVeil(
            backgroundColor = rootBg,
            veilHeight = 32.dp,
            modifier = Modifier.align(Alignment.TopCenter),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .height(TOP_BAR_HEIGHT.dp),
            contentAlignment = Alignment.Center,
        ) {
            TopBarBlurOverlay(
                modifier = Modifier.fillMaxSize(),
                topBarHeight = TOP_BAR_HEIGHT.dp,
                blurState = blurState,
                blurRadius = blurRadius,
            )
            Text(
                text = sectionLabel,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            IconButton(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    showTuning = true
                },
                modifier = Modifier.align(Alignment.CenterEnd),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_tune),
                    contentDescription = stringResource(R.string.motion_open_desc),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        DockStrip(
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            ExpressiveFloatingDockWithOverflow(
                tabs = PinnedTabs,
                overflowItems = SectionEntries,
                selectedKey = selectedKey,
                onSelect = { selectedKey = it },
                expanded = overflowExpanded,
                onExpandedChange = {
                    overflowExpanded = it
                    if (!it) editorMode = false
                },
                blurState = blurState,
                blurRadius = blurRadius,
                motion = motion,
                stripTapEaterEnabled = motion.stripTapEater,
                editorMode = editorMode,
                onEditorModeChange = { editorMode = it },
                onOpenTuning = { showTuning = true },
                mainSectionTitle = stringResource(R.string.overflow_title),
                favoritesSectionTitle = stringResource(R.string.tab_favorites),
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(bottom = 24.dp, start = 16.dp, end = 16.dp),
            )
        }

        if (showTuning) {
            MotionTuningSheet(
                motion = motion,
                tabs = PinnedTabs,
                onUpdate = { motionHolder.update(it) },
                onDismiss = { showTuning = false },
            )
        }
    }
}
