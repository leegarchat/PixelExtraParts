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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
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
import org.pixel.extra.parts.activities.ActivityTransitionScreen
import org.pixel.extra.parts.activities.AppIconsScreen
import org.pixel.extra.parts.activities.AutoHbmScreen
import org.pixel.extra.parts.activities.AutoLockScreen
import org.pixel.extra.parts.activities.DisplaySettingsScreen
import org.pixel.extra.parts.activities.DonateScreen
import org.pixel.extra.parts.activities.DtwSettingsScreen
import org.pixel.extra.parts.activities.HiddenLauncherAppsScreen
import org.pixel.extra.parts.activities.IconShapeScreen
import org.pixel.extra.parts.activities.MagnifierSettingsScreen
import org.pixel.extra.parts.activities.OverscrollScreen
import org.pixel.extra.parts.activities.RotationAnimationScreen
import org.pixel.extra.parts.activities.SaturationScreen
import org.pixel.extra.parts.activities.SystemUISettingsScreen
import org.pixel.extra.parts.activities.ThermalConfigEditorApp
import org.pixel.extra.parts.activities.ThermalConfigManagerApp
import org.pixel.extra.parts.activities.ThermalScreen
import org.pixel.extra.parts.dock.DockEntry
import org.pixel.extra.parts.dock.ExpressiveFloatingDockWithOverflow
import org.pixel.extra.parts.home.CatalogScreen
import org.pixel.extra.parts.home.HomeScreen
import org.pixel.extra.parts.motion.rememberFloatBarMotion
import org.pixel.extra.parts.settings.MotionTuningSheet
import org.pixel.extra.parts.settings.SettingsScreen
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

private val PinnedTabs = listOf(
    DockEntry(key = "home", labelRes = R.string.tab_home, iconRes = R.drawable.ic_home),
    DockEntry(key = "settings", labelRes = R.string.tab_settings, iconRes = R.drawable.ic_tune),
)

private val OverflowEntries: List<DockEntry> = run {
    // Icons cycle through already existing drawables (no new files).
    val extraIcons = listOf(
        R.drawable.ic_home,
        R.drawable.ic_favorite,
        R.drawable.ic_search,
        R.drawable.ic_music,
        R.drawable.ic_archive,
        R.drawable.ic_download,
        R.drawable.ic_share,
        R.drawable.ic_delete,
        R.drawable.ic_info,
        R.drawable.ic_tune,
        R.drawable.ic_more,
        R.drawable.ic_edit,
    )
    listOf(
        DockEntry(key = "archive", labelRes = R.string.overflow_item_archive, iconRes = R.drawable.ic_archive),
        DockEntry(key = "downloads", labelRes = R.string.overflow_item_downloads, iconRes = R.drawable.ic_download),
        DockEntry(key = "shared", labelRes = R.string.overflow_item_shared, iconRes = R.drawable.ic_share),
        DockEntry(key = "trash", labelRes = R.string.overflow_item_trash, iconRes = R.drawable.ic_delete),
        DockEntry(key = "about", labelRes = R.string.overflow_item_about, iconRes = R.drawable.ic_info),
    ) + (1..12).map { n ->
        DockEntry(
            key = "extra_$n",
            labelRes = R.string.overflow_extra_title,
            iconRes = extraIcons[(n - 1) % extraIcons.size],
            labelArg = n,
        )
    }
}

private const val TOP_BAR_HEIGHT = 64 // dp

@Composable
private fun PepRoot() {
    var selectedKey by rememberSaveable { mutableStateOf("home") }
    var overflowExpanded by rememberSaveable { mutableStateOf(false) }
    var editorMode by rememberSaveable { mutableStateOf(false) }
    // Hand-rolled back stack of ported section routes (no Navigation-Compose dependency).
    var backstack by rememberSaveable { mutableStateOf(listOf<String>()) }
    // Tuning state owned by the dock module: one holder shared by the dock
    // and the tuning sheet (loaded from disk once, saved on every change).
    val motionHolder = rememberFloatBarMotion()
    val motion = motionHolder.settings
    var showTuning by rememberSaveable { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    val blurState = rememberGraphicsLayerRecordingState()
    val blurRadius = motion.blurRadiusPx
    val rootBg = MaterialTheme.colorScheme.surfaceContainer

    val push: (String) -> Unit = { route -> backstack = backstack + route }
    val pop: () -> Unit = { if (backstack.isNotEmpty()) backstack = backstack.dropLast(1) }

    BackHandler(enabled = backstack.isNotEmpty()) { pop() }
    BackHandler(enabled = backstack.isEmpty() && editorMode) {
        editorMode = false
    }
    BackHandler(enabled = backstack.isEmpty() && !editorMode && overflowExpanded) {
        overflowExpanded = false
    }

    val allEntries = remember { PinnedTabs + OverflowEntries }
    val sectionOrder = remember(allEntries) {
        allEntries.mapIndexed { index, entry -> entry.key to index }.toMap()
    }
    val current = allEntries.firstOrNull { it.key == selectedKey } ?: PinnedTabs.first()
    val sectionLabel = current.labelArg?.let { stringResource(current.labelRes, it) }
        ?: stringResource(current.labelRes)
    val legacyOpen = backstack.isNotEmpty()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(rootBg)
    ) {
        // Main scrollable content: recorded once per frame for the shared blur.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .recordLayer(state = blurState, backgroundColor = rootBg, blurRadius = blurRadius)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                if (!legacyOpen) {
                    Spacer(modifier = Modifier.height(TOP_BAR_HEIGHT.dp + 8.dp))
                }
                AnimatedContent(
                    targetState = if (legacyOpen) "legacy:${backstack.last()}" else selectedKey,
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
                    modifier = Modifier
                        .weight(1f)
                        .then(if (legacyOpen) Modifier else Modifier.padding(horizontal = 16.dp)),
                ) { key ->
                    if (legacyOpen) {
                        LegacyHost(route = backstack.last(), onBack = pop, onPush = push)
                    } else if (key == "settings") {
                        SettingsScreen(
                            onOpenTuning = { showTuning = true },
                            onOpenEditor = {
                                overflowExpanded = true
                                editorMode = true
                            },
                        )
                    } else if (key == "home") {
                        CatalogScreen(onNavigate = push)
                    } else {
                        val entry = allEntries.firstOrNull { it.key == key } ?: PinnedTabs.first()
                        val entryLabel = entry.labelArg?.let { stringResource(entry.labelRes, it) }
                            ?: stringResource(entry.labelRes)
                        HomeScreen(
                            sectionLabel = entryLabel,
                            iconRes = entry.iconRes,
                        )
                    }
                }
                // Clearance above the floating dock (guideline 128-140.dp zone).
                if (!legacyOpen) {
                    Spacer(modifier = Modifier.height(132.dp))
                }
            }
        }

        if (!legacyOpen) {
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
        }
        DockStrip(
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            ExpressiveFloatingDockWithOverflow(
                tabs = PinnedTabs,
                overflowItems = OverflowEntries,
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

// Every ported legacy screen behind a back-stack route. Deep links from tile
// preferences land here in the future (TODO); for now everything opens home.
@Composable
private fun LegacyHost(
    route: String,
    onBack: () -> Unit,
    onPush: (String) -> Unit,
) {
    when (route) {
        "display" -> DisplaySettingsScreen(onBack = onBack, onNavigate = onPush)
        "appicons" -> AppIconsScreen(onBack = onBack, onNavigate = onPush)
        "shape" -> IconShapeScreen(onBack = onBack, onNavigate = onPush)
        "saturation" -> SaturationScreen(onBack = onBack, onNavigate = onPush)
        "autohbm" -> AutoHbmScreen(onBack = onBack, onNavigate = onPush)
        "dtw" -> DtwSettingsScreen(onBack = onBack, onNavigate = onPush)
        "magnifier" -> MagnifierSettingsScreen(onBack = onBack, onNavigate = onPush)
        "thermal" -> ThermalConfigManagerApp(onBack = onBack, onNavigate = onPush)
        "thermalStatus" -> ThermalScreen(onBack = onBack, onNavigate = onPush)
        "thermalEditor" -> ThermalConfigEditorApp(openCreateProfile = false, onBack = onBack, onNavigate = onPush)
        "thermalEditorCreate" -> ThermalConfigEditorApp(openCreateProfile = true, onBack = onBack, onNavigate = onPush)
        "rotation" -> RotationAnimationScreen(onBack = onBack, onNavigate = onPush)
        "transitions" -> ActivityTransitionScreen(onBack = onBack, onNavigate = onPush)
        "overscroll" -> OverscrollScreen(onBack = onBack, onNavigate = onPush)
        "autolock" -> AutoLockScreen(onBack = onBack, onNavigate = onPush)
        "systemui" -> SystemUISettingsScreen(onBack = onBack, onNavigate = onPush)
        "hidden" -> HiddenLauncherAppsScreen(onBack = onBack, onNavigate = onPush)
        "donate" -> DonateScreen(onBack = onBack, onNavigate = onPush)
        else -> DisplaySettingsScreen(onBack = onBack, onNavigate = onPush)
    }
}
