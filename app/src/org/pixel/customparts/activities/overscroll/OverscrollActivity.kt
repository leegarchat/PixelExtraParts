package org.pixel.customparts.activities

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.pixel.customparts.R
import org.pixel.customparts.dynamicDarkColorScheme
import org.pixel.customparts.dynamicLightColorScheme
import org.pixel.customparts.services.OverscrollTileService
import org.pixel.customparts.ui.GenericSwitchRow
import org.pixel.customparts.ui.InfoDialog
import org.pixel.customparts.ui.RadioSelectionGroup
import org.pixel.customparts.ui.SettingsGroupCard
import org.pixel.customparts.ui.SliderMorphRoot
import org.pixel.customparts.ui.SliderSettingFloat
import org.pixel.customparts.ui.TopBarBlurOverlay
import org.pixel.customparts.ui.TopStatusVeil
import org.pixel.customparts.ui.edgeOutline
import org.pixel.customparts.ui.recordLayer
import org.pixel.customparts.ui.rememberGraphicsLayerRecordingState
import org.pixel.customparts.ui.rememberSliderMorphHostState
import org.pixel.customparts.utils.TileUtils
import java.util.Locale
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.roundToInt

private const val TAB_OVERSCROLL_PROFILES = 0
private const val TAB_OVERSCROLL_PHYSICS = 1
private const val TAB_OVERSCROLL_APPS = 2

class OverscrollActivity : ComponentActivity() {
    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        setContent {
            val darkTheme = isSystemInDarkTheme()
            val context = LocalContext.current
            val colorScheme = if (darkTheme) {
                dynamicDarkColorScheme(context)
            } else {
                dynamicLightColorScheme(context)
            }

            MaterialTheme(colorScheme = colorScheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    OverscrollScreen(onBack = { finish() })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OverscrollScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current

    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE ||
        configuration.screenWidthDp >= 600

    // 3-Pass Non-Cyclic Blur Stack:
    // Pass 1 (scrollBlur): records pager content
    // Pass 2 (cardModalBlur): records [Pass 1 + Level-1 Flight Pseudo-Modals]
    // Pass 3 (sliderModalBlur): records [Pass 2 + Level-2 SliderMorphOverlay]
    val scrollBlur = rememberGraphicsLayerRecordingState()
    val cardModalBlur = rememberGraphicsLayerRecordingState()
    val sliderModalBlur = rememberGraphicsLayerRecordingState()
    val sliderMorphHost = rememberSliderMorphHostState()

    val pagerState = rememberStudioPagerState(3)
    val profilesListState = rememberLazyListState()
    val physicsListState = rememberLazyListState()
    val appsListState = rememberLazyListState()

    var isMasterEnabled by remember { mutableStateOf(OverscrollManager.isMasterEnabled(context)) }
    var profiles by remember { mutableStateOf(OverscrollManager.getSavedProfiles(context)) }
    var appConfigs by remember { mutableStateOf(OverscrollManager.getAppConfigs(context)) }
    var refreshKey by remember { mutableIntStateOf(0) }

    var visualSubTab by rememberSaveable { mutableIntStateOf(0) }
    var engineSubTab by rememberSaveable { mutableIntStateOf(0) }

    var infoDialogTitle by remember { mutableStateOf<String?>(null) }
    var infoDialogText by remember { mutableStateOf<String?>(null) }
    var infoDialogVideo by remember { mutableStateOf<String?>(null) }

    // 2-tap armed reset state in top bar
    var resetArmedTime by remember { mutableStateOf(0L) }
    val isResetArmed = resetArmedTime > 0L
    LaunchedEffect(resetArmedTime) {
        if (resetArmedTime <= 0L) return@LaunchedEffect
        delay(3000L)
        resetArmedTime = 0L
    }

    // Level-1 Flight Pseudo-Modal states (ZERO AlertDialogs!)
    var networkFlightOpen by remember { mutableStateOf(false) }
    var networkFlightSource by remember { mutableStateOf(Rect.Zero) }
    var networkFlightBoundsProvider by remember { mutableStateOf<() -> Rect>({ networkFlightSource }) }
    var networkFlightT by remember { mutableFloatStateOf(0f) }

    var appPickerFlightOpen by remember { mutableStateOf(false) }
    var appPickerFlightSource by remember { mutableStateOf(Rect.Zero) }
    var appPickerFlightBoundsProvider by remember { mutableStateOf<() -> Rect>({ appPickerFlightSource }) }
    var appPickerFlightT by remember { mutableFloatStateOf(0f) }

    var appEditorFlight by remember { mutableStateOf<OverscrollAppFlightTarget?>(null) }
    var appEditorFlightT by remember { mutableFloatStateOf(0f) }

    var level1CloseSignal by remember { mutableIntStateOf(0) }

    val onSettingChanged: () -> Unit = {
        scope.launch(Dispatchers.IO) {
            OverscrollManager.clearActiveProfile(context)
        }
        refreshKey++
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) scope.launch { OverscrollManager.exportSettings(context, uri) }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                if (OverscrollManager.importSettings(context, uri)) {
                    isMasterEnabled = OverscrollManager.isMasterEnabled(context)
                    refreshKey++
                    appConfigs = OverscrollManager.getAppConfigs(context)
                    Toast.makeText(
                        context,
                        context.getString(R.string.os_msg_import_success),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    var isPlaygroundActive by remember { mutableStateOf(false) }
    val anyLevel1ModalOpen = networkFlightOpen || appPickerFlightOpen || appEditorFlight != null
    val nestedMorphOpen = sliderMorphHost.request != null
    val anyModalOpen = anyLevel1ModalOpen || nestedMorphOpen
    val activeModalProgress = maxOf(networkFlightT, appPickerFlightT, appEditorFlightT)

    // Collapsible Frosted Header state
    val statusBarTopDp = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottomDp = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val topBarRowHeightDp = 56.dp
    val statusPlusTopBarPx = with(density) { (statusBarTopDp + topBarRowHeightDp).toPx() }

    var collapsibleMaxHeightPx by remember { mutableFloatStateOf(with(density) { 128.dp.toPx() }) }
    var collapseOffsetPx by remember { mutableFloatStateOf(0f) }
    var headerRevealJob by remember { mutableStateOf<Job?>(null) }

    fun revealHeaderSmoothly() {
        if (collapseOffsetPx > -0.5f || headerRevealJob?.isActive == true) return
        headerRevealJob = scope.launch {
            val anim = Animatable(collapseOffsetPx)
            anim.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    stiffness = Spring.StiffnessMediumLow,
                    dampingRatio = Spring.DampingRatioNoBouncy
                )
            ) {
                collapseOffsetPx = value
            }
        }
    }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (networkFlightOpen ||
                    appPickerFlightOpen ||
                    appEditorFlight != null ||
                    sliderMorphHost.request != null
                ) {
                    return Offset.Zero
                }
                if (available.y > 0.5f) {
                    headerRevealJob?.cancel()
                    collapseOffsetPx = (collapseOffsetPx + available.y)
                        .coerceIn(-collapsibleMaxHeightPx, 0f)
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (networkFlightOpen ||
                    appPickerFlightOpen ||
                    appEditorFlight != null ||
                    sliderMorphHost.request != null
                ) {
                    return Offset.Zero
                }
                if (consumed.y < -0.5f && collapsibleMaxHeightPx > 1f) {
                    headerRevealJob?.cancel()
                    collapseOffsetPx = (collapseOffsetPx + consumed.y)
                        .coerceIn(-collapsibleMaxHeightPx, 0f)
                }
                return Offset.Zero
            }
        }
    }

    LaunchedEffect(pagerState.anchor) {
        revealHeaderSmoothly()
    }

    val appsPageFraction = sstepE(pagerState.position, 1.05f, 1.95f)
    val switcherPortraitVisibility = if (isLandscape) 1f else (1f - appsPageFraction)

    val visibleCollapsiblePx = (collapsibleMaxHeightPx + collapseOffsetPx).coerceAtLeast(0f)
    val effectiveHeaderHeightPx = statusPlusTopBarPx + visibleCollapsiblePx
    val effectiveHeaderHeightDp = with(density) { effectiveHeaderHeightPx.toDp() }
    val listTopPaddingDp = with(density) { (statusPlusTopBarPx + collapsibleMaxHeightPx).toDp() }

    val showInfo: (String, String, String?) -> Unit = { title, text, video ->
        infoDialogTitle = title
        infoDialogText = text
        infoDialogVideo = video
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .nestedScroll(nestedScrollConnection)
    ) {
        SliderMorphRoot(
            host = sliderMorphHost,
            blurState = cardModalBlur,
            morphBlurState = sliderModalBlur,
            morphBackgroundColor = MaterialTheme.colorScheme.surfaceContainer
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (anyLevel1ModalOpen || nestedMorphOpen) {
                            Modifier.recordLayer(
                                state = cardModalBlur,
                                backgroundColor = MaterialTheme.colorScheme.surfaceContainer
                            )
                        } else {
                            Modifier
                        }
                    )
            ) {
                // Pass 1: Pager content recorded into scrollBlur
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .recordLayer(
                            state = scrollBlur,
                            backgroundColor = MaterialTheme.colorScheme.surfaceContainer
                        )
                ) {
                    EffectStudioPager(
                        state = pagerState,
                        userScrollEnabled = !anyModalOpen && !isPlaygroundActive,
                        onHorizontalDragDetected = { revealHeaderSmoothly() },
                        modifier = Modifier.fillMaxSize()
                    ) { page ->
                        val pagePadding = PaddingValues(
                            start = 16.dp,
                            top = listTopPaddingDp + 12.dp,
                            end = 16.dp,
                            bottom = navBottomDp + 28.dp
                        )

                        when (page) {
                            TAB_OVERSCROLL_PROFILES -> {
                                LazyColumn(
                                    state = profilesListState,
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = pagePadding,
                                    verticalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    item(key = "os_live_playground") {
                                        OverscrollInteractivePlaygroundCard(
                                            context = context,
                                            refreshKey = refreshKey,
                                            onInteractionActiveChanged = { isPlaygroundActive = it }
                                        )
                                    }

                                    item(key = "os_profiles_studio") {
                                        ProfilesSection(
                                            context = context,
                                            scope = scope,
                                            profiles = profiles,
                                            enabled = isMasterEnabled,
                                            isNetworkFlightOpen = networkFlightOpen,
                                            onProfilesChanged = { profiles = it },
                                            onProfileLoaded = {
                                                isMasterEnabled = OverscrollManager.isMasterEnabled(context)
                                                appConfigs = OverscrollManager.getAppConfigs(context)
                                                refreshKey++
                                            },
                                            onOpenNetworkFlight = { bounds, provider ->
                                                networkFlightSource = bounds
                                                networkFlightBoundsProvider = provider
                                                networkFlightOpen = true
                                            },
                                            exportLauncher = exportLauncher,
                                            importLauncher = importLauncher,
                                            refreshKey = refreshKey
                                        )
                                    }
                                }
                            }

                            TAB_OVERSCROLL_PHYSICS -> {
                                LazyColumn(
                                    state = physicsListState,
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = pagePadding,
                                    verticalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    item(key = "os_core_physics_card") {
                                        SettingsGroupCard(
                                            title = stringResource(R.string.os_group_physics),
                                            enabled = isMasterEnabled
                                        ) {
                                            OverscrollFloatSlider(
                                                context = context,
                                                title = stringResource(R.string.os_lbl_physics_pull),
                                                key = OverscrollManager.KEY_PULL_COEFF,
                                                range = 0.1f..3.0f,
                                                defVal = 1.5141f,
                                                infoText = stringResource(R.string.os_desc_physics_pull),
                                                video = "overscroll_pull",
                                                onInfo = showInfo,
                                                refreshKey = refreshKey,
                                                enabled = isMasterEnabled,
                                                onChange = onSettingChanged
                                            )
                                            HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))
                                            OverscrollFloatSlider(
                                                context = context,
                                                title = stringResource(R.string.os_lbl_physics_stiffness),
                                                key = OverscrollManager.KEY_STIFFNESS,
                                                range = 10f..1000f,
                                                defVal = 148.6191f,
                                                stepSize = 1f,
                                                decimals = 1,
                                                infoText = stringResource(R.string.os_desc_physics_stiffness),
                                                video = "overscroll_stiffness",
                                                onInfo = showInfo,
                                                refreshKey = refreshKey,
                                                enabled = isMasterEnabled,
                                                onChange = onSettingChanged
                                            )
                                            HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))
                                            OverscrollFloatSlider(
                                                context = context,
                                                title = stringResource(R.string.os_lbl_physics_damping),
                                                key = OverscrollManager.KEY_DAMPING,
                                                range = 0.1f..2.0f,
                                                defVal = 0.9976f,
                                                infoText = stringResource(R.string.os_desc_physics_damping),
                                                video = "overscroll_damping",
                                                onInfo = showInfo,
                                                refreshKey = refreshKey,
                                                enabled = isMasterEnabled,
                                                onChange = onSettingChanged
                                            )
                                            HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))
                                            OverscrollFloatSlider(
                                                context = context,
                                                title = stringResource(R.string.os_lbl_physics_fling),
                                                key = OverscrollManager.KEY_FLING,
                                                range = 0.1f..3.0f,
                                                defVal = 1.3679f,
                                                infoText = stringResource(R.string.os_desc_physics_fling),
                                                video = "overscroll_fling",
                                                onInfo = showInfo,
                                                refreshKey = refreshKey,
                                                enabled = isMasterEnabled,
                                                onChange = onSettingChanged
                                            )
                                            HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))
                                            OverscrollFloatSlider(
                                                context = context,
                                                title = stringResource(R.string.os_lbl_physics_res_exp),
                                                key = OverscrollManager.KEY_RESISTANCE_EXPONENT,
                                                range = 1f..8f,
                                                defVal = 4.0f,
                                                infoText = stringResource(R.string.os_desc_physics_res_exp),
                                                video = "overscroll_res_exponent",
                                                onInfo = showInfo,
                                                refreshKey = refreshKey,
                                                enabled = isMasterEnabled,
                                                onChange = onSettingChanged
                                            )
                                            HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))
                                            OverscrollFloatSlider(
                                                context = context,
                                                title = stringResource(R.string.os_lbl_anim_speed),
                                                key = OverscrollManager.KEY_ANIMATION_SPEED,
                                                range = 1f..300f,
                                                defVal = 168.5232f,
                                                stepSize = 1f,
                                                decimals = 1,
                                                infoText = stringResource(R.string.os_desc_anim_speed),
                                                video = "overscroll_anim_speed",
                                                onInfo = showInfo,
                                                refreshKey = refreshKey,
                                                enabled = isMasterEnabled,
                                                onChange = onSettingChanged
                                            )
                                        }
                                    }

                                    item(key = "os_visual_scales_studio_card") {
                                        OverscrollVisualScaleStudioCard(
                                            context = context,
                                            selectedTab = visualSubTab,
                                            onTabSelected = { visualSubTab = it },
                                            onInfo = showInfo,
                                            refreshKey = refreshKey,
                                            isMasterEnabled = isMasterEnabled,
                                            onChange = onSettingChanged
                                        )
                                    }

                                    item(key = "os_engine_and_norm_studio_card") {
                                        OverscrollEngineAndNormStudioCard(
                                            context = context,
                                            selectedTab = engineSubTab,
                                            onTabSelected = { engineSubTab = it },
                                            onInfo = showInfo,
                                            refreshKey = refreshKey,
                                            isMasterEnabled = isMasterEnabled,
                                            onChange = onSettingChanged
                                        )
                                    }
                                }
                            }

                            else -> {
                                OverscrollAppsTabPage(
                                    context = context,
                                    listState = appsListState,
                                    pagePadding = pagePadding,
                                    appConfigs = appConfigs,
                                    enabled = isMasterEnabled,
                                    isPickerOpen = appPickerFlightOpen,
                                    activeEditorPkg = appEditorFlight?.item?.pkg,
                                    onOpenPickerFlight = { bounds, provider ->
                                        appPickerFlightSource = bounds
                                        appPickerFlightBoundsProvider = provider
                                        appPickerFlightOpen = true
                                    },
                                    onOpenEditorFlight = { target ->
                                        appEditorFlight = target
                                    }
                                )
                            }
                        }
                    }
                }

                // Level-1 Pseudo-Modal A: Online Network Configs Flight Overlay
                NetworkConfigsFlightOverlay(
                    open = networkFlightOpen,
                    sourceRect = networkFlightSource,
                    sourceBoundsProvider = networkFlightBoundsProvider,
                    scrollBlur = scrollBlur,
                    topInsetPx = effectiveHeaderHeightPx,
                    closeSignal = level1CloseSignal,
                    onProgress = { networkFlightT = it },
                    onClose = { networkFlightOpen = false },
                    onConfigApplied = { configName ->
                        profiles = OverscrollManager.getSavedProfiles(context)
                        isMasterEnabled = OverscrollManager.isMasterEnabled(context)
                        appConfigs = OverscrollManager.getAppConfigs(context)
                        refreshKey++
                        Toast.makeText(
                            context,
                            context.getString(R.string.os_net_applied, configName),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                )

                // Level-1 Pseudo-Modal B: App Picker Flight Overlay
                OverscrollAppPickerFlightOverlay(
                    open = appPickerFlightOpen,
                    sourceRect = appPickerFlightSource,
                    sourceBoundsProvider = appPickerFlightBoundsProvider,
                    existingPackages = remember(appConfigs) { appConfigs.map { it.pkg }.toSet() },
                    scrollBlur = scrollBlur,
                    topInsetPx = effectiveHeaderHeightPx,
                    closeSignal = level1CloseSignal,
                    onProgress = { appPickerFlightT = it },
                    onClose = { appPickerFlightOpen = false },
                    onAppSelected = { pkg ->
                        if (appConfigs.none { it.pkg == pkg }) {
                            val newList = appConfigs.toMutableList().apply {
                                add(AppConfigItem(pkg, false, 1.0f, false))
                            }
                            appConfigs = newList
                            scope.launch { OverscrollManager.saveAppConfig(context, newList) }
                        } else {
                            Toast.makeText(
                                context,
                                context.getString(R.string.os_msg_app_exists),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                )

                // Level-1 Pseudo-Modal C: Individual App Config Flight Overlay
                OverscrollAppEditorFlightOverlay(
                    target = appEditorFlight,
                    scrollBlur = scrollBlur,
                    topInsetPx = effectiveHeaderHeightPx,
                    closeSignal = level1CloseSignal,
                    onProgress = { appEditorFlightT = it },
                    onClose = { appEditorFlight = null },
                    onUpdateItem = { updated ->
                        appEditorFlight = appEditorFlight?.copy(item = updated)
                        val newList = appConfigs.map {
                            if (it.pkg == updated.pkg) updated else it
                        }
                        appConfigs = newList
                        scope.launch { OverscrollManager.saveAppConfig(context, newList) }
                    },
                    onDeleteItem = { pkg ->
                        val newList = appConfigs.filterNot { it.pkg == pkg }
                        appConfigs = newList
                        scope.launch { OverscrollManager.saveAppConfig(context, newList) }
                    }
                )
            }
        } // end SliderMorphRoot

        // Frosted Blur Overlay over Pinned Header
        TopBarBlurOverlay(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter),
            topBarHeight = effectiveHeaderHeightDp,
            blurState = scrollBlur,
            morphBlurState = if (nestedMorphOpen) sliderModalBlur else cardModalBlur,
            morphActive = anyLevel1ModalOpen || nestedMorphOpen,
            tintBoost = maxOf(0.25f * activeModalProgress, 0.25f * sliderMorphHost.morphProgress)
        )

        // Status bar veil
        TopStatusVeil(
            backgroundColor = MaterialTheme.colorScheme.surfaceContainer,
            veilHeight = statusBarTopDp + 14.dp,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
        )

        // Crisp Pinned Header UI above the frost overlay (zIndex = 4f)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .zIndex(4f)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
                .padding(top = statusBarTopDp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(topBarRowHeightDp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.nav_back)
                    )
                }
                Text(
                    text = stringResource(R.string.os_title_activity),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                // Quick Settings Tile Button
                IconButton(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                        TileUtils.requestAddTileService(
                            context,
                            OverscrollTileService::class.java,
                            R.string.os_title_activity,
                            R.drawable.ic_overscroll_tile
                        )
                    }
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = stringResource(R.string.overscroll_add_tile),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                // 2-Tap Reset All Button
                val resetBg by animateColorAsState(
                    targetValue = if (isResetArmed) {
                        MaterialTheme.colorScheme.errorContainer
                    } else {
                        Color.Transparent
                    },
                    label = "ResetTopBtnBg"
                )
                val resetTint by animateColorAsState(
                    targetValue = if (isResetArmed) {
                        MaterialTheme.colorScheme.onErrorContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    label = "ResetTopBtnTint"
                )
                Box(
                    modifier = Modifier
                        .padding(end = 6.dp)
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(resetBg)
                        .clickable {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            if (isResetArmed) {
                                resetArmedTime = 0L
                                scope.launch {
                                    OverscrollManager.resetAll(context)
                                    isMasterEnabled = OverscrollManager.isMasterEnabled(context)
                                    appConfigs = OverscrollManager.getAppConfigs(context)
                                    refreshKey++
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.os_msg_reset_done),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            } else {
                                resetArmedTime = SystemClock.uptimeMillis()
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isResetArmed) Icons.Rounded.Check else Icons.Rounded.Refresh,
                        contentDescription = stringResource(R.string.os_btn_reset),
                        tint = resetTint,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // Clipped Collapsible Container for Master Switch + AppIconsTabs
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clipToBounds()
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        val fullH = placeable.height.toFloat()
                        collapsibleMaxHeightPx = fullH
                        val visH = (fullH + collapseOffsetPx).roundToInt().coerceAtLeast(0)
                        val yOff = collapseOffsetPx.roundToInt().coerceIn(-placeable.height, 0)
                        layout(placeable.width, visH) {
                            placeable.placeRelative(0, yOff)
                        }
                    }
            ) {
                val tabTitles = listOf(
                    stringResource(R.string.os_group_profiles),
                    stringResource(R.string.os_group_physics),
                    stringResource(R.string.os_group_apps)
                )

                if (isLandscape) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            GenericSwitchRow(
                                title = stringResource(R.string.os_label_master_native),
                                summary = if (isMasterEnabled) {
                                    stringResource(R.string.os_status_active)
                                } else {
                                    stringResource(R.string.os_status_disabled)
                                },
                                checked = isMasterEnabled,
                                onCheckedChange = { checked ->
                                    isMasterEnabled = checked
                                    scope.launch { OverscrollManager.setMasterEnabled(context, checked) }
                                    onSettingChanged()
                                }
                            )
                        }
                        Box(modifier = Modifier.weight(1.15f)) {
                            AppIconsTabs(
                                titles = tabTitles,
                                position = pagerState.position,
                                pagerState = pagerState,
                                onTabClick = { index ->
                                    scope.launch {
                                        pagerState.animateScrollToPage(index, isDirectTap = true)
                                    }
                                }
                            )
                        }
                    }
                } else {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        if (switcherPortraitVisibility > 0.005f) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .unfoldVerticalSlot(switcherPortraitVisibility)
                                    .alpha(switcherPortraitVisibility)
                            ) {
                                GenericSwitchRow(
                                    title = stringResource(R.string.os_label_master_native),
                                    summary = if (isMasterEnabled) {
                                        stringResource(R.string.os_status_active)
                                    } else {
                                        stringResource(R.string.os_status_disabled)
                                    },
                                    checked = isMasterEnabled,
                                    infoText = stringResource(R.string.os_desc_master_native),
                                    videoResName = "overscroll_master",
                                    onInfoClick = showInfo,
                                    onCheckedChange = { checked ->
                                        isMasterEnabled = checked
                                        scope.launch { OverscrollManager.setMasterEnabled(context, checked) }
                                        onSettingChanged()
                                    }
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        AppIconsTabs(
                            titles = tabTitles,
                            position = pagerState.position,
                            pagerState = pagerState,
                            onTabClick = { index ->
                                scope.launch {
                                    pagerState.animateScrollToPage(index, isDirectTap = true)
                                }
                            }
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                }
            }
        }

        // Full-header tap/swipe blocker when any pseudo-modal is open
        if (anyModalOpen) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(effectiveHeaderHeightDp)
                    .align(Alignment.TopCenter)
                    .zIndex(5f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            if (nestedMorphOpen) {
                                sliderMorphHost.requestAnimatedClose()
                            } else {
                                level1CloseSignal++
                            }
                        }
                    )
            )
        }
    }

    if (infoDialogTitle != null && infoDialogText != null) {
        InfoDialog(
            title = infoDialogTitle!!,
            text = infoDialogText!!,
            videoResName = infoDialogVideo,
            onDismiss = {
                infoDialogTitle = null
                infoDialogText = null
                infoDialogVideo = null
            }
        )
    }
}

// ── Live Spring Telemetry & Interactive 2D Playground Card ───────────────

@Composable
private fun OverscrollInteractivePlaygroundCard(
    context: Context,
    refreshKey: Int,
    onInteractionActiveChanged: (Boolean) -> Unit = {}
) {
    val playgroundRowState = rememberLazyListState()
    var isTouchingStrip by remember { mutableStateOf(false) }

    LaunchedEffect(isTouchingStrip, playgroundRowState.isScrollInProgress) {
        onInteractionActiveChanged(isTouchingStrip || playgroundRowState.isScrollInProgress)
    }
    val cr = context.contentResolver
    val pull = remember(refreshKey) { Settings.Global.getFloat(cr, OverscrollManager.KEY_PULL_COEFF, 1.5141f) }
    val stiffness = remember(refreshKey) { Settings.Global.getFloat(cr, OverscrollManager.KEY_STIFFNESS, 148.6191f) }
    val damping = remember(refreshKey) { Settings.Global.getFloat(cr, OverscrollManager.KEY_DAMPING, 0.9976f) }
    val resExp = remember(refreshKey) { Settings.Global.getFloat(cr, OverscrollManager.KEY_RESISTANCE_EXPONENT, 4.0f) }

    val primaryColor = MaterialTheme.colorScheme.primary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
    val stageBg = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f)

    SettingsGroupCard(title = stringResource(R.string.os_title_playground)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Live Spring Response Curve Visualizer
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(92.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(stageBg)
                    .border(1.dp, stageBg.edgeOutline(), RoundedCornerShape(18.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val midY = h * 0.58f

                    drawLine(
                        color = gridColor,
                        start = Offset(0f, midY),
                        end = Offset(w, midY),
                        strokeWidth = 1.dp.toPx()
                    )

                    val path = Path()
                    val steps = 80
                    val omega = (stiffness / 150f).coerceIn(0.4f, 4.5f) * 7.5f
                    val zeta = damping.coerceIn(0.15f, 2.0f)
                    val amp = (pull / 2.2f).coerceIn(0.2f, 1.25f) * (h * 0.42f)

                    for (i in 0..steps) {
                        val t = i.toFloat() / steps
                        val x = t * w
                        val yOffset = if (t < 0.28f) {
                            // Pull phase with resistance exponent curve
                            val p = (t / 0.28f).toDouble()
                            val pullCurve = 1.0 - (1.0 - p).pow(resExp.coerceIn(1f, 8f).toDouble() * 0.45)
                            (-amp * pullCurve).toFloat()
                        } else {
                            // Spring release oscillation phase
                            val st = (t - 0.28f) / 0.72f
                            val env = exp((-zeta * omega * st * 0.55f).toDouble()).toFloat()
                            val osc = cos((omega * st * (1.35f - (zeta * 0.35f).coerceAtMost(0.85f))).toDouble()).toFloat()
                            -amp * env * osc
                        }
                        val py = (midY + yOffset).coerceIn(4f, h - 4f)
                        if (i == 0) path.moveTo(x, py) else path.lineTo(x, py)
                    }

                    drawPath(
                        path = path,
                        color = primaryColor,
                        style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                Row(
                    modifier = Modifier.align(Alignment.TopEnd),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = String.format(Locale.US, "K: %.0f  ζ: %.2f", stiffness, damping),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = tertiaryColor
                    )
                }
            }

            // Horizontal Overscroll Strip (isolated from parent EffectStudioPager)
            LazyRow(
                state = playgroundRowState,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 2.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(
                                requireUnconsumed = false,
                                pass = PointerEventPass.Initial
                            )
                            isTouchingStrip = true
                            onInteractionActiveChanged(true)
                            try {
                                waitForUpOrCancellation(pass = PointerEventPass.Initial)
                            } finally {
                                isTouchingStrip = false
                            }
                        }
                    }
            ) {
                items(6, key = { "playground_card_$it" }) { index ->
                    val cardBg = MaterialTheme.colorScheme.surfaceContainerHigh
                    Card(
                        colors = CardDefaults.cardColors(containerColor = cardBg),
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, cardBg.edgeOutline()),
                        modifier = Modifier
                            .width(136.dp)
                            .height(104.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${index + 1}",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            Text(
                                text = stringResource(R.string.os_card_example_text),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.SemiBold,
                                lineHeight = 13.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Tab 2: Unified Visual Scale & Engine Studio Cards ────────────────────

@Composable
private fun OverscrollVisualScaleStudioCard(
    context: Context,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    onInfo: (String, String, String?) -> Unit,
    refreshKey: Int,
    isMasterEnabled: Boolean,
    onChange: () -> Unit
) {
    val clampedTab = selectedTab.coerceIn(0, 2)
    val animatedTabPos by animateFloatAsState(
        targetValue = clampedTab.toFloat(),
        animationSpec = spring(
            stiffness = Spring.StiffnessMediumLow,
            dampingRatio = Spring.DampingRatioNoBouncy
        ),
        label = "OverscrollVisualTabPos"
    )

    SettingsGroupCard(
        title = stringResource(R.string.os_lbl_scale_mode),
        enabled = isMasterEnabled
    ) {
        AppIconsTabs(
            titles = listOf(
                stringResource(R.string.os_group_visual_vert),
                stringResource(R.string.os_group_visual_zoom),
                stringResource(R.string.os_group_visual_horz)
            ),
            position = animatedTabPos,
            onTabClick = onTabSelected
        )

        Spacer(Modifier.height(8.dp))

        AnimatedContent(
            targetState = clampedTab,
            transitionSpec = {
                val dir = if (targetState > initialState) 1 else -1
                (slideInHorizontally(
                    animationSpec = spring(
                        stiffness = Spring.StiffnessMediumLow,
                        dampingRatio = Spring.DampingRatioNoBouncy
                    )
                ) { fullW -> dir * fullW } + fadeIn(tween(180))) togetherWith
                    (slideOutHorizontally(
                        animationSpec = spring(
                            stiffness = Spring.StiffnessMediumLow,
                            dampingRatio = Spring.DampingRatioNoBouncy
                        )
                    ) { fullW -> -dir * fullW } + fadeOut(tween(140)))
            },
            label = "OverscrollVisualScaleSlide"
        ) { tab ->
            when (tab) {
                0 -> ScaleChannelEditor(
                    context = context,
                    prefix = "overscroll_scale",
                    desc = stringResource(R.string.os_desc_visual_vert),
                    onInfo = onInfo,
                    refreshKey = refreshKey,
                    isMasterEnabled = isMasterEnabled,
                    onChange = onChange,
                    defIntensity = 0.31f,
                    defIntensityHoriz = 0.3786f,
                    defLimitMin = 0.1f
                )
                1 -> ScaleChannelEditor(
                    context = context,
                    prefix = "overscroll_zoom",
                    desc = stringResource(R.string.os_desc_visual_zoom),
                    onInfo = onInfo,
                    refreshKey = refreshKey,
                    isMasterEnabled = isMasterEnabled,
                    onChange = onChange,
                    defIntensity = 0.2f,
                    defIntensityHoriz = 0.2f,
                    defLimitMin = 0.1f
                )
                else -> ScaleChannelEditor(
                    context = context,
                    prefix = "overscroll_h_scale",
                    desc = stringResource(R.string.os_desc_visual_horz),
                    onInfo = onInfo,
                    refreshKey = refreshKey,
                    isMasterEnabled = isMasterEnabled,
                    onChange = onChange,
                    defIntensity = 0.2f,
                    defIntensityHoriz = 0.0f,
                    defLimitMin = 0.1f
                )
            }
        }
    }
}

@Composable
private fun ScaleChannelEditor(
    context: Context,
    prefix: String,
    desc: String,
    onInfo: (String, String, String?) -> Unit,
    refreshKey: Int,
    isMasterEnabled: Boolean,
    onChange: () -> Unit,
    defIntensity: Float,
    defIntensityHoriz: Float,
    defLimitMin: Float
) {
    val scope = rememberCoroutineScope()
    val modeKey = "${prefix}_mode"
    var mode by remember(refreshKey, prefix) {
        mutableIntStateOf(Settings.Global.getInt(context.contentResolver, modeKey, 0))
    }
    val modes = listOf(
        stringResource(R.string.os_mode_off),
        stringResource(R.string.os_mode_shrink),
        stringResource(R.string.os_mode_grow)
    )
    val areSlidersActive = isMasterEnabled && (mode != 0)
    val anchorDesc = stringResource(R.string.os_desc_anchor)

    Column(modifier = Modifier.fillMaxWidth()) {
        RadioSelectionGroup(
            title = stringResource(R.string.os_lbl_scale_mode),
            options = modes,
            selectedIndex = mode,
            enabled = isMasterEnabled,
            onSelect = {
                mode = it
                scope.launch(Dispatchers.IO) {
                    Settings.Global.putInt(context.contentResolver, modeKey, it)
                    launch(Dispatchers.Main) { onChange() }
                }
            },
            infoText = desc,
            videoResName = "${prefix}_mode",
            onInfoClick = onInfo
        )

        HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))

        OverscrollFloatSlider(
            context = context,
            title = stringResource(R.string.os_lbl_scale_int),
            key = "${prefix}_intensity",
            range = 0f..10f,
            defVal = defIntensity,
            infoText = stringResource(R.string.os_desc_scale_intensity),
            video = "${prefix}_intensity",
            onInfo = onInfo,
            refreshKey = refreshKey,
            enabled = areSlidersActive,
            onChange = onChange
        )

        HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))

        OverscrollFloatSlider(
            context = context,
            title = stringResource(R.string.os_lbl_scale_int_horiz),
            key = "${prefix}_intensity_horiz",
            range = 0f..10f,
            defVal = defIntensityHoriz,
            infoText = stringResource(R.string.os_desc_scale_int_horiz),
            video = "${prefix}_intensity_horiz",
            onInfo = onInfo,
            refreshKey = refreshKey,
            enabled = areSlidersActive,
            onChange = onChange
        )

        HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))

        OverscrollFloatSlider(
            context = context,
            title = stringResource(R.string.os_lbl_scale_limit),
            key = "${prefix}_limit_min",
            range = 0.1f..10f,
            defVal = defLimitMin,
            infoText = stringResource(R.string.os_desc_scale_limit),
            video = "${prefix}_intensity_horiz",
            onInfo = onInfo,
            refreshKey = refreshKey,
            enabled = areSlidersActive,
            onChange = onChange
        )

        when (prefix) {
            "overscroll_scale" -> {
                HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))
                OverscrollFloatSlider(
                    context = context,
                    title = stringResource(R.string.os_lbl_anchor_y),
                    key = "overscroll_scale_anchor_y",
                    range = 0f..1f,
                    defVal = 0.5f,
                    infoText = anchorDesc,
                    video = "overscroll_scale_anchor_y",
                    onInfo = onInfo,
                    refreshKey = refreshKey,
                    enabled = areSlidersActive,
                    onChange = onChange
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))
                OverscrollFloatSlider(
                    context = context,
                    title = stringResource(R.string.os_lbl_anchor_x_horiz),
                    key = "overscroll_scale_anchor_x_horiz",
                    range = 0f..1f,
                    defVal = 0.5f,
                    infoText = stringResource(R.string.os_desc_anchor_x_horiz),
                    video = "overscroll_scale_anchor_x_horiz",
                    onInfo = onInfo,
                    refreshKey = refreshKey,
                    enabled = areSlidersActive,
                    onChange = onChange
                )
            }
            "overscroll_h_scale" -> {
                HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))
                OverscrollFloatSlider(
                    context = context,
                    title = stringResource(R.string.os_lbl_anchor_x),
                    key = "overscroll_h_scale_anchor_x",
                    range = 0f..1f,
                    defVal = 0.5f,
                    infoText = anchorDesc,
                    video = "overscroll_h_scale_anchor_x",
                    onInfo = onInfo,
                    refreshKey = refreshKey,
                    enabled = areSlidersActive,
                    onChange = onChange
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))
                OverscrollFloatSlider(
                    context = context,
                    title = stringResource(R.string.os_lbl_anchor_y_horiz),
                    key = "overscroll_h_scale_anchor_y_horiz",
                    range = 0f..1f,
                    defVal = 0.5f,
                    infoText = stringResource(R.string.os_desc_anchor_y_horiz),
                    video = "overscroll_h_scale_anchor_y_horiz",
                    onInfo = onInfo,
                    refreshKey = refreshKey,
                    enabled = areSlidersActive,
                    onChange = onChange
                )
            }
            "overscroll_zoom" -> {
                HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))
                OverscrollFloatSlider(
                    context = context,
                    title = stringResource(R.string.os_lbl_anchor_x),
                    key = "overscroll_zoom_anchor_x",
                    range = 0f..1f,
                    defVal = 0.5f,
                    infoText = anchorDesc,
                    video = "overscroll_zoom_anchor_x",
                    onInfo = onInfo,
                    refreshKey = refreshKey,
                    enabled = areSlidersActive,
                    onChange = onChange
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))
                OverscrollFloatSlider(
                    context = context,
                    title = stringResource(R.string.os_lbl_anchor_y),
                    key = "overscroll_zoom_anchor_y",
                    range = 0f..1f,
                    defVal = 0.5f,
                    infoText = anchorDesc,
                    video = "overscroll_zoom_anchor_y",
                    onInfo = onInfo,
                    refreshKey = refreshKey,
                    enabled = areSlidersActive,
                    onChange = onChange
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))
                OverscrollFloatSlider(
                    context = context,
                    title = stringResource(R.string.os_lbl_anchor_x_horiz),
                    key = "overscroll_zoom_anchor_x_horiz",
                    range = 0f..1f,
                    defVal = 0.5f,
                    infoText = stringResource(R.string.os_desc_anchor_x_horiz),
                    video = "overscroll_zoom_anchor_x_horiz",
                    onInfo = onInfo,
                    refreshKey = refreshKey,
                    enabled = areSlidersActive,
                    onChange = onChange
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))
                OverscrollFloatSlider(
                    context = context,
                    title = stringResource(R.string.os_lbl_anchor_y_horiz),
                    key = "overscroll_zoom_anchor_y_horiz",
                    range = 0f..1f,
                    defVal = 0.5f,
                    infoText = stringResource(R.string.os_desc_anchor_y_horiz),
                    video = "overscroll_zoom_anchor_y_horiz",
                    onInfo = onInfo,
                    refreshKey = refreshKey,
                    enabled = areSlidersActive,
                    onChange = onChange
                )
            }
        }
    }
}

@Composable
private fun OverscrollEngineAndNormStudioCard(
    context: Context,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    onInfo: (String, String, String?) -> Unit,
    refreshKey: Int,
    isMasterEnabled: Boolean,
    onChange: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val clampedTab = selectedTab.coerceIn(0, 1)
    val animatedTabPos by animateFloatAsState(
        targetValue = clampedTab.toFloat(),
        animationSpec = spring(
            stiffness = Spring.StiffnessMediumLow,
            dampingRatio = Spring.DampingRatioNoBouncy
        ),
        label = "OverscrollEngineTabPos"
    )

    SettingsGroupCard(
        title = stringResource(R.string.os_group_advanced),
        enabled = isMasterEnabled
    ) {
        AppIconsTabs(
            titles = listOf(
                stringResource(R.string.os_group_advanced),
                stringResource(R.string.os_group_norm)
            ),
            position = animatedTabPos,
            onTabClick = onTabSelected
        )

        Spacer(Modifier.height(8.dp))

        AnimatedContent(
            targetState = clampedTab,
            transitionSpec = {
                val dir = if (targetState > initialState) 1 else -1
                (slideInHorizontally(
                    animationSpec = spring(
                        stiffness = Spring.StiffnessMediumLow,
                        dampingRatio = Spring.DampingRatioNoBouncy
                    )
                ) { fullW -> dir * fullW } + fadeIn(tween(180))) togetherWith
                    (slideOutHorizontally(
                        animationSpec = spring(
                            stiffness = Spring.StiffnessMediumLow,
                            dampingRatio = Spring.DampingRatioNoBouncy
                        )
                    ) { fullW -> -dir * fullW } + fadeOut(tween(140)))
            },
            label = "OverscrollEngineSlide"
        ) { tab ->
            if (tab == 0) {
                val invertKey = OverscrollManager.KEY_INVERT_ANCHOR
                var invert by remember(refreshKey) {
                    mutableStateOf(Settings.Global.getInt(context.contentResolver, invertKey, 1) == 1)
                }

                Column(modifier = Modifier.fillMaxWidth()) {
                    OverscrollFloatSlider(
                        context = context,
                        title = stringResource(R.string.os_lbl_input_smooth),
                        key = OverscrollManager.KEY_INPUT_SMOOTH_FACTOR,
                        range = 0f..0.95f,
                        defVal = 0.5f,
                        infoText = stringResource(R.string.os_desc_input_smooth),
                        video = "overscroll_input_smooth",
                        onInfo = onInfo,
                        refreshKey = refreshKey,
                        enabled = isMasterEnabled,
                        onChange = onChange
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))
                    OverscrollFloatSlider(
                        context = context,
                        title = stringResource(R.string.os_lbl_min_vel),
                        key = OverscrollManager.KEY_PHYSICS_MIN_VEL,
                        range = 0f..400f,
                        defVal = 8.0f,
                        stepSize = 0.5f,
                        decimals = 1,
                        infoText = stringResource(R.string.os_desc_min_vel),
                        video = "overscroll_physics_min_vel",
                        onInfo = onInfo,
                        refreshKey = refreshKey,
                        enabled = isMasterEnabled,
                        onChange = onChange
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))
                    OverscrollFloatSlider(
                        context = context,
                        title = stringResource(R.string.os_lbl_min_val),
                        key = OverscrollManager.KEY_PHYSICS_MIN_VAL,
                        range = 0f..20f,
                        defVal = 0.6f,
                        infoText = stringResource(R.string.os_desc_min_val),
                        video = "overscroll_physics_min_val",
                        onInfo = onInfo,
                        refreshKey = refreshKey,
                        enabled = isMasterEnabled,
                        onChange = onChange
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))
                    OverscrollFloatSlider(
                        context = context,
                        title = stringResource(R.string.os_lbl_lerp_idle),
                        key = OverscrollManager.KEY_LERP_MAIN_IDLE,
                        range = 0f..1f,
                        defVal = 0.4f,
                        infoText = stringResource(R.string.os_desc_lerp_idle),
                        video = "overscroll_lerp_main_idle",
                        onInfo = onInfo,
                        refreshKey = refreshKey,
                        enabled = isMasterEnabled,
                        onChange = onChange
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))
                    OverscrollFloatSlider(
                        context = context,
                        title = stringResource(R.string.os_lbl_lerp_run),
                        key = OverscrollManager.KEY_LERP_MAIN_RUN,
                        range = 0f..1f,
                        defVal = 0.6999f,
                        infoText = stringResource(R.string.os_desc_lerp_run),
                        video = "overscroll_lerp_main_run",
                        onInfo = onInfo,
                        refreshKey = refreshKey,
                        enabled = isMasterEnabled,
                        onChange = onChange
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))
                    OverscrollFloatSlider(
                        context = context,
                        title = stringResource(R.string.os_lbl_compose_scale),
                        key = OverscrollManager.KEY_COMPOSE_SCALE,
                        range = 0.01f..10.0f,
                        defVal = 3.3299f,
                        infoText = stringResource(R.string.os_desc_compose_scale),
                        video = "overscroll_compose_scale",
                        onInfo = onInfo,
                        refreshKey = refreshKey,
                        enabled = isMasterEnabled,
                        onChange = onChange
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))
                    GenericSwitchRow(
                        title = stringResource(R.string.os_lbl_invert_anchor),
                        checked = invert,
                        enabled = isMasterEnabled,
                        infoText = stringResource(R.string.os_desc_invert_anchor),
                        videoResName = "overscroll_invert_anchor",
                        onInfoClick = onInfo,
                        onCheckedChange = {
                            invert = it
                            scope.launch(Dispatchers.IO) {
                                Settings.Global.putInt(context.contentResolver, invertKey, if (it) 1 else 0)
                                launch(Dispatchers.Main) { onChange() }
                            }
                        }
                    )
                }
            } else {
                val normEnabledKey = OverscrollManager.KEY_NORM_ENABLED
                var normEnabled by remember(refreshKey) {
                    mutableStateOf(Settings.Global.getInt(context.contentResolver, normEnabledKey, 0) == 1)
                }
                val detectModeKey = OverscrollManager.KEY_NORM_DETECT_MODE
                var detectMode by remember(refreshKey) {
                    mutableIntStateOf(Settings.Global.getInt(context.contentResolver, detectModeKey, 1))
                }
                val detectModes = listOf(
                    stringResource(R.string.os_norm_mode_behavior),
                    stringResource(R.string.os_norm_mode_hybrid),
                    stringResource(R.string.os_norm_mode_stacktrace)
                )
                val normSlidersEnabled = isMasterEnabled && normEnabled

                Column(modifier = Modifier.fillMaxWidth()) {
                    GenericSwitchRow(
                        title = stringResource(R.string.os_lbl_norm_enabled),
                        summary = if (normEnabled) {
                            stringResource(R.string.os_status_active)
                        } else {
                            stringResource(R.string.os_status_disabled)
                        },
                        checked = normEnabled,
                        enabled = isMasterEnabled,
                        infoText = stringResource(R.string.os_desc_norm_enabled),
                        onInfoClick = onInfo,
                        onCheckedChange = {
                            normEnabled = it
                            scope.launch(Dispatchers.IO) {
                                Settings.Global.putInt(context.contentResolver, normEnabledKey, if (it) 1 else 0)
                                launch(Dispatchers.Main) { onChange() }
                            }
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))
                    RadioSelectionGroup(
                        title = stringResource(R.string.os_lbl_norm_detect_mode),
                        options = detectModes,
                        selectedIndex = detectMode,
                        enabled = normSlidersEnabled,
                        onSelect = {
                            detectMode = it
                            scope.launch(Dispatchers.IO) {
                                Settings.Global.putInt(context.contentResolver, detectModeKey, it)
                                launch(Dispatchers.Main) { onChange() }
                            }
                        },
                        infoText = stringResource(R.string.os_desc_norm_detect_mode),
                        onInfoClick = onInfo
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))
                    OverscrollFloatSlider(
                        context = context,
                        title = stringResource(R.string.os_lbl_norm_ref_delta),
                        key = OverscrollManager.KEY_NORM_REF_DELTA,
                        range = 0.001f..1.0f,
                        defVal = 0.015f,
                        stepSize = 0.001f,
                        decimals = 3,
                        infoText = stringResource(R.string.os_desc_norm_ref_delta),
                        video = "overscroll_norm_ref_delta",
                        onInfo = onInfo,
                        refreshKey = refreshKey,
                        enabled = normSlidersEnabled,
                        onChange = onChange
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))
                    OverscrollFloatSlider(
                        context = context,
                        title = stringResource(R.string.os_lbl_norm_detect_mul),
                        key = OverscrollManager.KEY_NORM_DETECT_MUL,
                        range = 0f..10.0f,
                        defVal = 1.5f,
                        infoText = stringResource(R.string.os_desc_norm_detect_mul),
                        video = "overscroll_norm_detect_mul",
                        onInfo = onInfo,
                        refreshKey = refreshKey,
                        enabled = normSlidersEnabled,
                        onChange = onChange
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))
                    OverscrollFloatSlider(
                        context = context,
                        title = stringResource(R.string.os_lbl_norm_factor),
                        key = OverscrollManager.KEY_NORM_FACTOR,
                        range = 0.01f..1.0f,
                        defVal = 0.33f,
                        infoText = stringResource(R.string.os_desc_norm_factor),
                        video = "overscroll_norm_factor",
                        onInfo = onInfo,
                        refreshKey = refreshKey,
                        enabled = normSlidersEnabled,
                        onChange = onChange
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))
                    OverscrollFloatSlider(
                        context = context,
                        title = stringResource(R.string.os_lbl_norm_window),
                        key = OverscrollManager.KEY_NORM_WINDOW,
                        range = 2f..64f,
                        defVal = 2f,
                        stepSize = 1f,
                        decimals = 0,
                        infoText = stringResource(R.string.os_desc_norm_window),
                        video = "overscroll_norm_window",
                        onInfo = onInfo,
                        refreshKey = refreshKey,
                        enabled = normSlidersEnabled,
                        onChange = onChange
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))
                    OverscrollFloatSlider(
                        context = context,
                        title = stringResource(R.string.os_lbl_norm_ramp),
                        key = OverscrollManager.KEY_NORM_RAMP,
                        range = 1f..20f,
                        defVal = 1f,
                        stepSize = 1f,
                        decimals = 0,
                        infoText = stringResource(R.string.os_desc_norm_ramp),
                        video = "overscroll_norm_ramp",
                        onInfo = onInfo,
                        refreshKey = refreshKey,
                        enabled = normSlidersEnabled,
                        onChange = onChange
                    )
                }
            }
        }
    }
}

@Composable
private fun OverscrollFloatSlider(
    context: Context,
    title: String,
    key: String,
    range: ClosedFloatingPointRange<Float>,
    defVal: Float,
    infoText: String,
    video: String?,
    onInfo: (String, String, String?) -> Unit,
    refreshKey: Int,
    enabled: Boolean = true,
    stepSize: Float = 0.01f,
    decimals: Int = 2,
    onChange: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var value by remember(refreshKey, key) {
        mutableFloatStateOf(Settings.Global.getFloat(context.contentResolver, key, defVal))
    }
    val fmt = "%.${decimals}f"

    SliderSettingFloat(
        title = title,
        value = value,
        range = range,
        unit = "",
        enabled = enabled,
        stepSize = stepSize,
        defaultValue = defVal,
        defaultValueText = String.format(Locale.US, fmt, defVal),
        valueText = String.format(Locale.US, fmt, value),
        onValueChange = {
            value = it
            scope.launch(Dispatchers.IO) {
                Settings.Global.putFloat(context.contentResolver, key, it)
                launch(Dispatchers.Main) { onChange() }
            }
        },
        onDefault = {
            value = defVal
            scope.launch(Dispatchers.IO) {
                Settings.Global.putFloat(context.contentResolver, key, defVal)
                launch(Dispatchers.Main) { onChange() }
            }
        },
        infoText = infoText,
        videoResName = video,
        onInfoClick = onInfo
    )
}