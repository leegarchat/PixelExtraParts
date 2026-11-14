package org.pixel.customparts.activities

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.pixel.customparts.R
import org.pixel.customparts.SettingsKeys
import org.pixel.customparts.dynamicDarkColorScheme
import org.pixel.customparts.dynamicLightColorScheme
import org.pixel.customparts.icons.IconApplyMode
import org.pixel.customparts.icons.IconApplyResult
import org.pixel.customparts.icons.IconPackManager
import org.pixel.customparts.ui.GenericSwitchRow
import org.pixel.customparts.ui.REBOOT_BUBBLE_CONTENT_BOTTOM_PADDING
import org.pixel.customparts.ui.RebootBubble
import org.pixel.customparts.ui.RebootBubbleMenuAction
import org.pixel.customparts.ui.SliderMorphRoot
import org.pixel.customparts.ui.TopBarBlurOverlay
import org.pixel.customparts.ui.TopStatusVeil
import org.pixel.customparts.ui.addons.AddonMainEntry
import org.pixel.customparts.ui.addons.flattenSettings
import org.pixel.customparts.ui.addons.scanAddonActivityEntries
import org.pixel.customparts.ui.performRebootSystem
import org.pixel.customparts.ui.recordLayer
import org.pixel.customparts.ui.rememberGraphicsLayerRecordingState
import org.pixel.customparts.ui.rememberSliderMorphHostState
import org.pixel.customparts.utils.SettingsCompat
import java.util.Locale
import kotlin.math.roundToInt

private const val APP_ICON_TINT_MODE_OFF = 0
private const val APP_ICON_TINT_MODE_CUSTOM = 1
private const val APP_ICON_TINT_MODE_AUTO = 2
private const val DEFAULT_SHAPE_BACKGROUND_TINT_COLOR = 0x00000000
private const val DEFAULT_SHAPE_FOREGROUND_TINT_COLOR = -0x1

private const val TAB_ICON_PACKS = 0
private const val TAB_INSTALLED_APPS = 1
private const val TAB_ICON_SETTINGS = 2

private fun readAppIconModuleFlags(context: Context): AppIconModuleFlags {
    val frameworkEnabled = SettingsCompat.isEnabled(context, SettingsKeys.APP_ICONS_ENABLED, false)
    val launcherHookEnabled = SettingsCompat.isEnabled(
        context,
        SettingsKeys.APP_ICONS_LAUNCHER_ENABLED,
        frameworkEnabled
    )
    return AppIconModuleFlags(
        moduleEnabled = frameworkEnabled || launcherHookEnabled,
        launcherOnly = launcherHookEnabled && !frameworkEnabled
    )
}

private fun writeAppIconModuleFlags(
    context: Context,
    moduleEnabled: Boolean,
    launcherOnly: Boolean
) {
    val frameworkEnabled = moduleEnabled && !launcherOnly
    val launcherHookEnabled = moduleEnabled
    SettingsCompat.putInt(context, SettingsKeys.APP_ICONS_ENABLED, if (frameworkEnabled) 1 else 0)
    SettingsCompat.putInt(context, SettingsKeys.APP_ICONS_LAUNCHER_ENABLED, if (launcherHookEnabled) 1 else 0)
    IconPackManager.requestIconReload(context)
}

private fun readAppIconSystemShapeFlags(context: Context): AppIconSystemShapeFlags {
    return AppIconSystemShapeFlags(
        stretchShape = SettingsCompat.isEnabled(context, SettingsKeys.APP_ICONS_SYSTEM_STRETCH_SHAPE, false),
        removeShape = SettingsCompat.isEnabled(context, SettingsKeys.APP_ICONS_SYSTEM_REMOVE_SHAPE, false),
        scalePercent = readShapeScale(context, SettingsKeys.APP_ICONS_SYSTEM_SHAPE_SCALE)
    )
}

private fun writeAppIconSystemShapeFlags(
    context: Context,
    stretchShape: Boolean,
    removeShape: Boolean,
    scalePercent: Float
) {
    SettingsCompat.putInt(context, SettingsKeys.APP_ICONS_SYSTEM_STRETCH_SHAPE, if (stretchShape) 1 else 0)
    SettingsCompat.putInt(context, SettingsKeys.APP_ICONS_SYSTEM_REMOVE_SHAPE, if (removeShape) 1 else 0)
    SettingsCompat.putFloat(context, SettingsKeys.APP_ICONS_SYSTEM_SHAPE_SCALE, normalizeShapeScale(scalePercent))
    IconPackManager.requestIconReload(context)
}

private fun readAppIconNotificationShapeFlags(context: Context): AppIconNotificationShapeFlags {
    return AppIconNotificationShapeFlags(
        stretchShape = SettingsCompat.isEnabled(context, SettingsKeys.APP_ICONS_NOTIFICATION_STRETCH_SHAPE, false),
        removeShape = SettingsCompat.isEnabled(context, SettingsKeys.APP_ICONS_NOTIFICATION_REMOVE_SHAPE, false),
        scalePercent = readShapeScale(context, SettingsKeys.APP_ICONS_NOTIFICATION_SHAPE_SCALE)
    )
}

private fun writeAppIconNotificationShapeFlags(
    context: Context,
    stretchShape: Boolean,
    removeShape: Boolean,
    scalePercent: Float
) {
    SettingsCompat.putInt(context, SettingsKeys.APP_ICONS_NOTIFICATION_STRETCH_SHAPE, if (stretchShape) 1 else 0)
    SettingsCompat.putInt(context, SettingsKeys.APP_ICONS_NOTIFICATION_REMOVE_SHAPE, if (removeShape) 1 else 0)
    SettingsCompat.putFloat(context, SettingsKeys.APP_ICONS_NOTIFICATION_SHAPE_SCALE, normalizeShapeScale(scalePercent))
    IconPackManager.requestIconReload(context)
}

private fun readAppIconLauncherShapeFlags(context: Context): AppIconLauncherShapeFlags {
    return AppIconLauncherShapeFlags(
        stretchShape = SettingsCompat.isEnabled(context, SettingsKeys.APP_ICONS_LAUNCHER_STRETCH_SHAPE, false),
        removeShape = SettingsCompat.isEnabled(context, SettingsKeys.APP_ICONS_LAUNCHER_REMOVE_SHAPE, false),
        scalePercent = readShapeScale(context, SettingsKeys.APP_ICONS_LAUNCHER_SHAPE_SCALE)
    )
}

private fun writeAppIconLauncherShapeFlags(
    context: Context,
    stretchShape: Boolean,
    removeShape: Boolean,
    scalePercent: Float
) {
    SettingsCompat.putInt(context, SettingsKeys.APP_ICONS_LAUNCHER_STRETCH_SHAPE, if (stretchShape) 1 else 0)
    SettingsCompat.putInt(context, SettingsKeys.APP_ICONS_LAUNCHER_REMOVE_SHAPE, if (removeShape) 1 else 0)
    SettingsCompat.putFloat(context, SettingsKeys.APP_ICONS_LAUNCHER_SHAPE_SCALE, normalizeShapeScale(scalePercent))
    IconPackManager.requestIconReload(context)
}

private fun readAppIconShapeTintFlags(context: Context): AppIconShapeTintFlags {
    return AppIconShapeTintFlags(
        backgroundTintMode = normalizeTintMode(
            SettingsCompat.getInt(context, SettingsKeys.APP_ICONS_SHAPE_BACKGROUND_TINT_MODE, APP_ICON_TINT_MODE_OFF)
        ),
        backgroundTintColor = SettingsCompat.getInt(
            context,
            SettingsKeys.APP_ICONS_SHAPE_BACKGROUND_TINT_COLOR,
            DEFAULT_SHAPE_BACKGROUND_TINT_COLOR
        ),
        foregroundTintMode = normalizeTintMode(
            SettingsCompat.getInt(context, SettingsKeys.APP_ICONS_SHAPE_FOREGROUND_TINT_MODE, APP_ICON_TINT_MODE_OFF)
        ),
        foregroundTintColor = SettingsCompat.getInt(
            context,
            SettingsKeys.APP_ICONS_SHAPE_FOREGROUND_TINT_COLOR,
            DEFAULT_SHAPE_FOREGROUND_TINT_COLOR
        )
    )
}

private fun writeAppIconShapeTintFlags(context: Context, flags: AppIconShapeTintFlags) {
    SettingsCompat.putInt(
        context,
        SettingsKeys.APP_ICONS_SHAPE_BACKGROUND_TINT_MODE,
        normalizeTintMode(flags.backgroundTintMode)
    )
    SettingsCompat.putInt(context, SettingsKeys.APP_ICONS_SHAPE_BACKGROUND_TINT_COLOR, flags.backgroundTintColor)
    SettingsCompat.putInt(
        context,
        SettingsKeys.APP_ICONS_SHAPE_FOREGROUND_TINT_MODE,
        normalizeTintMode(flags.foregroundTintMode)
    )
    SettingsCompat.putInt(context, SettingsKeys.APP_ICONS_SHAPE_FOREGROUND_TINT_COLOR, flags.foregroundTintColor)
    IconPackManager.requestIconReload(context)
}

private fun normalizeTintMode(mode: Int): Int {
    return if (mode == APP_ICON_TINT_MODE_CUSTOM || mode == APP_ICON_TINT_MODE_AUTO) {
        mode
    } else {
        APP_ICON_TINT_MODE_OFF
    }
}

private fun readShapeScale(context: Context, key: String): Float {
    return normalizeShapeScale(SettingsCompat.getFloat(context, key, IconPackManager.DEFAULT_SHAPE_SCALE_PERCENT))
}

internal fun normalizeShapeScale(value: Float): Float {
    return value.coerceIn(IconPackManager.MIN_SHAPE_SCALE_PERCENT, IconPackManager.MAX_SHAPE_SCALE_PERCENT)
}

class AppIconsActivity : ComponentActivity() {
    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
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
                    AppIconsScreen(onBack = { finish() })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppIconsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
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
    val packsListState = rememberLazyListState()
    val appsListState = rememberLazyListState()
    val settingsListState = rememberLazyListState()

    var dashboard by remember { mutableStateOf(IconPackManager.getCachedDashboardState()) }
    var isInitialLoading by remember { mutableStateOf(dashboard == null) }
    var isSilentRefreshing by remember { mutableStateOf(false) }
    var loadFailed by remember { mutableStateOf(false) }

    // Cached custom app icon bitmaps so the unified stream never re-decodes or jumps on scroll
    val appliedIconCache = remember { mutableStateMapOf<String, Bitmap?>() }

    var showSystemApps by rememberSaveable { mutableStateOf(true) }
    var showHiddenApps by rememberSaveable { mutableStateOf(false) }
    var appSearchQuery by rememberSaveable { mutableStateOf("") }

    // Level-1 Flight Pseudo-Modal states (ZERO Dialogs!)
    var packFlight by remember { mutableStateOf<IconPackFlightTarget?>(null) }
    var packFlightT by remember { mutableFloatStateOf(0f) }
    var packFlightClosing by remember { mutableStateOf(false) }
    val armedResetPacks = remember { mutableStateMapOf<String, Long>() }

    var appFlight by remember { mutableStateOf<AppDetailFlightTarget?>(null) }
    var appFlightT by remember { mutableFloatStateOf(0f) }

    var colorFlight by remember { mutableStateOf<ColorPickerFlightTarget?>(null) }
    var colorFlightT by remember { mutableFloatStateOf(0f) }

    var taskModalOpen by remember { mutableStateOf(false) }
    var taskModalConfirmClearAll by remember { mutableStateOf(false) }
    var taskModalSource by remember { mutableStateOf(Rect.Zero) }
    var taskModalBoundsProvider by remember { mutableStateOf<() -> Rect>({ taskModalSource }) }
    var taskModalT by remember { mutableFloatStateOf(0f) }

    var activeProgress by remember { mutableStateOf(IconPackManager.getActiveProgress()) }
    var completionResult by remember { mutableStateOf<IconApplyResult?>(null) }
    var handledProgressTaskId by rememberSaveable { mutableStateOf<String?>(null) }
    var injectedAddonEntries by remember { mutableStateOf<List<AddonMainEntry>>(emptyList()) }
    var level1CloseSignal by remember { mutableIntStateOf(0) }

    val initialModuleFlags = remember { readAppIconModuleFlags(context) }
    val initialSystemShapeFlags = remember { readAppIconSystemShapeFlags(context) }
    val initialNotificationShapeFlags = remember { readAppIconNotificationShapeFlags(context) }
    val initialLauncherShapeFlags = remember { readAppIconLauncherShapeFlags(context) }
    val initialShapeTintFlags = remember { readAppIconShapeTintFlags(context) }

    var moduleEnabled by remember { mutableStateOf(initialModuleFlags.moduleEnabled) }
    var launcherOnlyEnabled by remember { mutableStateOf(initialModuleFlags.launcherOnly) }
    var systemStretchShapeEnabled by remember { mutableStateOf(initialSystemShapeFlags.stretchShape) }
    var systemRemoveShapeEnabled by remember { mutableStateOf(initialSystemShapeFlags.removeShape) }
    var systemShapeScalePercent by remember { mutableFloatStateOf(initialSystemShapeFlags.scalePercent) }
    var notificationStretchShapeEnabled by remember { mutableStateOf(initialNotificationShapeFlags.stretchShape) }
    var notificationRemoveShapeEnabled by remember { mutableStateOf(initialNotificationShapeFlags.removeShape) }
    var notificationShapeScalePercent by remember { mutableFloatStateOf(initialNotificationShapeFlags.scalePercent) }
    var launcherStretchShapeEnabled by remember { mutableStateOf(initialLauncherShapeFlags.stretchShape) }
    var launcherRemoveShapeEnabled by remember { mutableStateOf(initialLauncherShapeFlags.removeShape) }
    var launcherShapeScalePercent by remember { mutableFloatStateOf(initialLauncherShapeFlags.scalePercent) }
    var shapeTintFlags by remember { mutableStateOf(initialShapeTintFlags) }
    var shapeAreaTab by rememberSaveable { mutableIntStateOf(0) }

    val injectedAddonSettings = remember(injectedAddonEntries) {
        injectedAddonEntries.flatMap { flattenSettings(it.settings) }
    }
    val injectedAddonKeys = remember(injectedAddonSettings) {
        injectedAddonSettings.map { it.key }.toSet()
    }
    val showHardcodedIconControls = injectedAddonEntries.isEmpty()
    val showHardcodedLauncherIconControls = showHardcodedIconControls &&
        SettingsKeys.APP_ICONS_LAUNCHER_ENABLED !in injectedAddonKeys &&
        SettingsKeys.APP_ICONS_LAUNCHER_STRETCH_SHAPE !in injectedAddonKeys &&
        SettingsKeys.APP_ICONS_LAUNCHER_REMOVE_SHAPE !in injectedAddonKeys &&
        SettingsKeys.APP_ICONS_LAUNCHER_SHAPE_SCALE !in injectedAddonKeys

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

    suspend fun loadDashboardSilent(forceRefresh: Boolean = false) {
        if (dashboard == null) {
            isInitialLoading = true
        } else {
            isSilentRefreshing = true
        }
        runCatching { IconPackManager.loadDashboardState(context, forceRefresh) }
            .onSuccess { newState ->
                dashboard = newState
                loadFailed = false
                // Synchronize flight targets if open so live counts/overrides update in-place
                packFlight?.let { current ->
                    val updatedPack = newState.iconPacks.find { it.packageName == current.pack.packageName }
                    if (updatedPack != null) {
                        packFlight = current.copy(pack = updatedPack)
                    }
                }
                appFlight?.let { current ->
                    val updatedApp = newState.installedApps.find { it.packageName == current.app.packageName }
                    if (updatedApp != null) {
                        appFlight = current.copy(app = updatedApp)
                    }
                }
            }
            .onFailure {
                if (dashboard == null) loadFailed = true
            }
        isInitialLoading = false
        isSilentRefreshing = false
    }

    fun triggerManualSilentRefresh() {
        if (isSilentRefreshing) return
        // 1. Fix / uncollapse header tabs smoothly
        revealHeaderSmoothly()
        // 2. Smoothly scroll active list back to top while background refresh runs
        scope.launch {
            when (pagerState.anchor) {
                TAB_ICON_PACKS -> if (packsListState.firstVisibleItemIndex > 0 || packsListState.firstVisibleItemScrollOffset > 0) {
                    packsListState.animateScrollToItem(0)
                }
                TAB_INSTALLED_APPS -> if (appsListState.firstVisibleItemIndex > 0 || appsListState.firstVisibleItemScrollOffset > 0) {
                    appsListState.animateScrollToItem(0)
                }
                else -> if (settingsListState.firstVisibleItemIndex > 0 || settingsListState.firstVisibleItemScrollOffset > 0) {
                    settingsListState.animateScrollToItem(0)
                }
            }
        }
        // 3. Silently regenerate dashboard in background without blanking the UI
        scope.launch {
            loadDashboardSilent(forceRefresh = true)
        }
    }

    fun startIconOperation(start: () -> Boolean) {
        if (!start()) {
            Toast.makeText(
                context,
                context.getString(R.string.app_icons_operation_running),
                Toast.LENGTH_SHORT
            ).show()
        } else {
            activeProgress = IconPackManager.getActiveProgress()
        }
    }

    LaunchedEffect(Unit) {
        loadDashboardSilent(forceRefresh = false)
    }

    LaunchedEffect(Unit) {
        injectedAddonEntries = withContext(Dispatchers.IO) {
            scanAddonActivityEntries(context, "AppIconsActivity", "advanced")
        }
    }

    var wasRunningTask by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            val progress = IconPackManager.getActiveProgress()
            val prev = activeProgress
            activeProgress = progress
            if (progress != null && !progress.completed) {
                wasRunningTask = true
            } else if (progress == null && wasRunningTask) {
                // Задача была прервана пользователем — тихо синхронизируем актуальное состояние
                wasRunningTask = false
                appliedIconCache.clear()
                loadDashboardSilent(forceRefresh = true)
            }
            if (progress?.completed == true && handledProgressTaskId != progress.taskId) {
                wasRunningTask = false
                handledProgressTaskId = progress.taskId
                completionResult = progress.result
                taskModalConfirmClearAll = false
                taskModalOpen = true
                appliedIconCache.clear()
                loadDashboardSilent(forceRefresh = true)
            }
            delay(50)
        }
    }

    val visibleApps = remember(dashboard?.installedApps, showSystemApps, showHiddenApps, appSearchQuery) {
        val apps = dashboard?.installedApps.orEmpty()
        val query = appSearchQuery.trim().lowercase(Locale.getDefault())
        val filteredApps = apps.filter { app ->
            (showSystemApps || !app.isSystem) && (showHiddenApps || app.isLauncherVisible)
        }
        if (query.isEmpty()) {
            filteredApps
        } else {
            filteredApps.filter { app ->
                app.label.lowercase(Locale.getDefault()).contains(query) ||
                    app.packageName.lowercase(Locale.US).contains(query) ||
                    app.appliedIconPackLabel.orEmpty().lowercase(Locale.getDefault()).contains(query)
            }
        }
    }

    val anyLevel1ModalOpen = packFlight != null ||
        appFlight != null ||
        colorFlight != null ||
        taskModalOpen
    val nestedMorphOpen = sliderMorphHost.request != null
    val anyModalOpen = anyLevel1ModalOpen || nestedMorphOpen
    val activeModalProgress = maxOf(packFlightT, appFlightT, colorFlightT, taskModalT)

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (packFlight != null ||
                    appFlight != null ||
                    colorFlight != null ||
                    taskModalOpen ||
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
                if (packFlight != null ||
                    appFlight != null ||
                    colorFlight != null ||
                    taskModalOpen ||
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

    val settingsPageFraction = sstepE(pagerState.position, 1.05f, 1.95f)
    val switcherPortraitVisibility = if (!showHardcodedIconControls) {
        0f
    } else if (isLandscape) {
        1f
    } else {
        1f - settingsPageFraction
    }

    val visibleCollapsiblePx = (collapsibleMaxHeightPx + collapseOffsetPx).coerceAtLeast(0f)
    val effectiveHeaderHeightPx = statusPlusTopBarPx + visibleCollapsiblePx
    val effectiveHeaderHeightDp = with(density) { effectiveHeaderHeightPx.toDp() }
    val listTopPaddingDp = with(density) { (statusPlusTopBarPx + collapsibleMaxHeightPx).toDp() }
    val isBusy = activeProgress?.completed == false

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
                // Pass 1: Pager content recorded into scrollBlur (origin = 0,0)
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
                        userScrollEnabled = !anyModalOpen,
                        onHorizontalDragDetected = { revealHeaderSmoothly() },
                        modifier = Modifier.fillMaxSize()
                    ) { page ->
                        val pagePadding = PaddingValues(
                            start = 16.dp,
                            top = listTopPaddingDp + 12.dp,
                            end = 16.dp,
                            bottom = navBottomDp + REBOOT_BUBBLE_CONTENT_BOTTOM_PADDING + 24.dp
                        )

                        when (page) {
                            TAB_ICON_PACKS -> {
                                LazyColumn(
                                    state = packsListState,
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = pagePadding,
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    when {
                                        isInitialLoading && dashboard == null -> {
                                            items(3, key = { "skeleton_pack_$it" }) {
                                                SkeletonPackPlaceholderCard()
                                            }
                                        }
                                        loadFailed && dashboard == null -> {
                                            item(key = "packs_failed") {
                                                EmptyStateText(stringResource(R.string.app_icons_load_failed))
                                            }
                                        }
                                        else -> {
                                            val packs = dashboard?.iconPacks.orEmpty()
                                            if (packs.isEmpty()) {
                                                item(key = "packs_empty") {
                                                    EmptyStateText(stringResource(R.string.app_icons_no_icon_packs))
                                                }
                                            } else {
                                                items(packs, key = { it.packageName }) { pack ->
                                                    val cardKey = "pack_${pack.packageName}"
                                                    val armedTime = armedResetPacks[pack.packageName] ?: 0L
                                                    val isTrashArmed = armedTime > 0L

                                                    LaunchedEffect(armedTime) {
                                                        if (armedTime <= 0L) return@LaunchedEffect
                                                        val totalDurationMs = 3000L
                                                        while (true) {
                                                            val now = SystemClock.uptimeMillis()
                                                            val remaining = (armedTime + totalDurationMs) - now
                                                            if (remaining <= 0L) break
                                                            delay(remaining.coerceAtMost(80L))
                                                        }
                                                        while (packFlight?.pack?.packageName == pack.packageName &&
                                                            !packFlightClosing &&
                                                            packFlightT < 0.99f
                                                        ) {
                                                            delay(25L)
                                                        }
                                                        if (packFlight?.pack?.packageName == pack.packageName &&
                                                            packFlightClosing
                                                        ) {
                                                            while (packFlight?.pack?.packageName == pack.packageName) {
                                                                delay(25L)
                                                            }
                                                            delay(90L)
                                                        }
                                                        if (armedResetPacks[pack.packageName] == armedTime) {
                                                            armedResetPacks.remove(pack.packageName)
                                                        }
                                                    }

                                                    IconPackCard(
                                                        pack = pack,
                                                        busy = isBusy,
                                                        trashArmed = isTrashArmed,
                                                        isHiddenForFlight = packFlight?.key == cardKey,
                                                        onExpand = { initialTab, bounds, provider ->
                                                            val curArmed = armedResetPacks[pack.packageName]
                                                            if (curArmed != null) {
                                                                val now = SystemClock.uptimeMillis()
                                                                val rem = (curArmed + 3000L) - now
                                                                if (rem in 1L..450L) {
                                                                    armedResetPacks[pack.packageName] =
                                                                        now - 3000L + 500L
                                                                }
                                                            }
                                                            packFlight = IconPackFlightTarget(
                                                                key = cardKey,
                                                                pack = pack,
                                                                initialTab = initialTab,
                                                                source = bounds,
                                                                sourceBoundsProvider = provider,
                                                                isTrashArmedProvider = {
                                                                    armedResetPacks.containsKey(pack.packageName)
                                                                }
                                                            )
                                                        },
                                                        onApplyUpdate = {
                                                            startIconOperation {
                                                                IconPackManager.startApplyAll(
                                                                    context,
                                                                    pack.packageName,
                                                                    IconApplyMode.INSTALLED_ONLY
                                                                )
                                                            }
                                                        },
                                                        onTrashClick = {
                                                            if (isTrashArmed) {
                                                                armedResetPacks.remove(pack.packageName)
                                                                startIconOperation {
                                                                    IconPackManager.startRemoveIconPack(
                                                                        context,
                                                                        pack.packageName
                                                                    )
                                                                }
                                                            } else {
                                                                armedResetPacks[pack.packageName] =
                                                                    SystemClock.uptimeMillis()
                                                            }
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            TAB_INSTALLED_APPS -> {
                                LazyColumn(
                                    state = appsListState,
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = pagePadding
                                ) {
                                    item(key = "apps_search_and_filters") {
                                        Column(
                                            modifier = Modifier.padding(bottom = 14.dp),
                                            verticalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            AppIconsSearchField(
                                                query = appSearchQuery,
                                                hint = stringResource(R.string.launcher_hidden_apps_search_hint),
                                                onQueryChange = { appSearchQuery = it }
                                            )
                                            AppIconsFilterPillsRow(
                                                showSystemApps = showSystemApps,
                                                showHiddenApps = showHiddenApps,
                                                onToggleSystem = { showSystemApps = !showSystemApps },
                                                onToggleHidden = { showHiddenApps = !showHiddenApps }
                                            )
                                        }
                                    }

                                    when {
                                        isInitialLoading && dashboard == null -> {
                                            item(key = "apps_skeleton") {
                                                SkeletonAppStreamCard()
                                            }
                                        }
                                        loadFailed && dashboard == null -> {
                                            item(key = "apps_failed") {
                                                EmptyStateText(stringResource(R.string.app_icons_load_failed))
                                            }
                                        }
                                        visibleApps.isEmpty() -> {
                                            item(key = "apps_empty") {
                                                EmptyStateText(stringResource(R.string.app_icons_no_apps))
                                            }
                                        }
                                        else -> {
                                            val state = dashboard
                                            if (state != null) {
                                                // Unified continuous stream (single card geometry with lazy virtualization)
                                                itemsIndexed(
                                                    items = visibleApps,
                                                    key = { _, app -> app.packageName }
                                                ) { index, app ->
                                                    val isFirst = index == 0
                                                    val isLast = index == visibleApps.lastIndex
                                                    val hasShapeOverride =
                                                        (state.iconMap.shapeOverrides[app.packageName]
                                                            ?: app.shapeOverrides)?.isEmpty() == false

                                                    InstalledAppStreamRow(
                                                        app = app,
                                                        sectionTitle = if (isFirst) {
                                                            stringResource(
                                                                R.string.app_icons_app_count_format,
                                                                visibleApps.size
                                                            )
                                                        } else null,
                                                        isFirst = isFirst,
                                                        isLast = isLast,
                                                        hasShapeOverride = hasShapeOverride,
                                                        cachedCustomBitmap = appliedIconCache[app.packageName],
                                                        onCacheBitmap = { bmp ->
                                                            appliedIconCache[app.packageName] = bmp
                                                        },
                                                        isHiddenForFlight = appFlight?.app?.packageName == app.packageName,
                                                        onOpenFlight = { bounds, provider ->
                                                            appFlight = AppDetailFlightTarget(
                                                                app = app,
                                                                source = bounds,
                                                                sourceBoundsProvider = provider
                                                            )
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            else -> {
                                LazyColumn(
                                    state = settingsListState,
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = pagePadding,
                                    verticalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    if (showHardcodedIconControls) {
                                        item(key = "settings_cards") {
                                            val bgTitle = stringResource(R.string.app_icons_shape_background_tint_title)
                                            val fgTitle = stringResource(R.string.app_icons_shape_foreground_tint_title)

                                            AppIconsSettingsTabContent(
                                                checked = moduleEnabled,
                                                launcherOnlyChecked = launcherOnlyEnabled,
                                                selectedAreaTab = shapeAreaTab,
                                                onAreaTabSelected = { shapeAreaTab = it },
                                                systemStretchShapeChecked = systemStretchShapeEnabled,
                                                systemRemoveShapeChecked = systemRemoveShapeEnabled,
                                                notificationStretchShapeChecked = notificationStretchShapeEnabled,
                                                notificationRemoveShapeChecked = notificationRemoveShapeEnabled,
                                                launcherStretchShapeChecked = launcherStretchShapeEnabled,
                                                launcherRemoveShapeChecked = launcherRemoveShapeEnabled,
                                                systemShapeScalePercent = systemShapeScalePercent,
                                                notificationShapeScalePercent = notificationShapeScalePercent,
                                                launcherShapeScalePercent = launcherShapeScalePercent,
                                                backgroundTintMode = shapeTintFlags.backgroundTintMode,
                                                backgroundTintColor = shapeTintFlags.backgroundTintColor,
                                                foregroundTintMode = shapeTintFlags.foregroundTintMode,
                                                foregroundTintColor = shapeTintFlags.foregroundTintColor,
                                                showLauncherControls = showHardcodedLauncherIconControls,
                                                activeColorFlightKey = colorFlight?.key,
                                                onIconShapeClick = {
                                                    context.startActivity(
                                                        Intent(context, IconShapeActivity::class.java)
                                                    )
                                                },
                                                onCheckedChange = { enabled ->
                                                    moduleEnabled = enabled
                                                    launcherOnlyEnabled = false
                                                    writeAppIconModuleFlags(
                                                        context = context,
                                                        moduleEnabled = enabled,
                                                        launcherOnly = false
                                                    )
                                                },
                                                onLauncherOnlyChange = { enabled ->
                                                    if (moduleEnabled) {
                                                        launcherOnlyEnabled = enabled
                                                        writeAppIconModuleFlags(
                                                            context = context,
                                                            moduleEnabled = true,
                                                            launcherOnly = enabled
                                                        )
                                                    }
                                                },
                                                onSystemStretchShapeChange = { enabled ->
                                                    systemStretchShapeEnabled = enabled
                                                    if (enabled) systemRemoveShapeEnabled = false
                                                    writeAppIconSystemShapeFlags(
                                                        context = context,
                                                        stretchShape = enabled,
                                                        removeShape = if (enabled) false else systemRemoveShapeEnabled,
                                                        scalePercent = systemShapeScalePercent
                                                    )
                                                },
                                                onSystemRemoveShapeChange = { enabled ->
                                                    systemRemoveShapeEnabled = enabled
                                                    if (enabled) systemStretchShapeEnabled = false
                                                    writeAppIconSystemShapeFlags(
                                                        context = context,
                                                        stretchShape = if (enabled) false else systemStretchShapeEnabled,
                                                        removeShape = enabled,
                                                        scalePercent = systemShapeScalePercent
                                                    )
                                                },
                                                onNotificationStretchShapeChange = { enabled ->
                                                    notificationStretchShapeEnabled = enabled
                                                    if (enabled) notificationRemoveShapeEnabled = false
                                                    writeAppIconNotificationShapeFlags(
                                                        context = context,
                                                        stretchShape = enabled,
                                                        removeShape = if (enabled) false else notificationRemoveShapeEnabled,
                                                        scalePercent = notificationShapeScalePercent
                                                    )
                                                },
                                                onNotificationRemoveShapeChange = { enabled ->
                                                    notificationRemoveShapeEnabled = enabled
                                                    if (enabled) notificationStretchShapeEnabled = false
                                                    writeAppIconNotificationShapeFlags(
                                                        context = context,
                                                        stretchShape = if (enabled) false else notificationStretchShapeEnabled,
                                                        removeShape = enabled,
                                                        scalePercent = notificationShapeScalePercent
                                                    )
                                                },
                                                onLauncherStretchShapeChange = { enabled ->
                                                    launcherStretchShapeEnabled = enabled
                                                    if (enabled) launcherRemoveShapeEnabled = false
                                                    writeAppIconLauncherShapeFlags(
                                                        context = context,
                                                        stretchShape = enabled,
                                                        removeShape = if (enabled) false else launcherRemoveShapeEnabled,
                                                        scalePercent = launcherShapeScalePercent
                                                    )
                                                },
                                                onLauncherRemoveShapeChange = { enabled ->
                                                    launcherRemoveShapeEnabled = enabled
                                                    if (enabled) launcherStretchShapeEnabled = false
                                                    writeAppIconLauncherShapeFlags(
                                                        context = context,
                                                        stretchShape = if (enabled) false else launcherStretchShapeEnabled,
                                                        removeShape = enabled,
                                                        scalePercent = launcherShapeScalePercent
                                                    )
                                                },
                                                onSystemShapeScaleChange = { value ->
                                                    systemShapeScalePercent = value
                                                    writeAppIconSystemShapeFlags(
                                                        context = context,
                                                        stretchShape = systemStretchShapeEnabled,
                                                        removeShape = systemRemoveShapeEnabled,
                                                        scalePercent = value
                                                    )
                                                },
                                                onNotificationShapeScaleChange = { value ->
                                                    notificationShapeScalePercent = value
                                                    writeAppIconNotificationShapeFlags(
                                                        context = context,
                                                        stretchShape = notificationStretchShapeEnabled,
                                                        removeShape = notificationRemoveShapeEnabled,
                                                        scalePercent = value
                                                    )
                                                },
                                                onLauncherShapeScaleChange = { value ->
                                                    launcherShapeScalePercent = value
                                                    writeAppIconLauncherShapeFlags(
                                                        context = context,
                                                        stretchShape = launcherStretchShapeEnabled,
                                                        removeShape = launcherRemoveShapeEnabled,
                                                        scalePercent = value
                                                    )
                                                },
                                                onBackgroundTintModeChange = { mode ->
                                                    val updated = shapeTintFlags.copy(
                                                        backgroundTintMode = normalizeTintMode(mode)
                                                    )
                                                    shapeTintFlags = updated
                                                    writeAppIconShapeTintFlags(context, updated)
                                                },
                                                onOpenBackgroundColorFlight = { bounds, provider ->
                                                    colorFlight = ColorPickerFlightTarget(
                                                        key = "bg_tint",
                                                        title = bgTitle,
                                                        initialColor = shapeTintFlags.backgroundTintColor,
                                                        source = bounds,
                                                        sourceBoundsProvider = provider,
                                                        onColorSelected = { picked ->
                                                            val updated = shapeTintFlags.copy(
                                                                backgroundTintMode = APP_ICON_TINT_MODE_CUSTOM,
                                                                backgroundTintColor = picked
                                                            )
                                                            shapeTintFlags = updated
                                                            writeAppIconShapeTintFlags(context, updated)
                                                        }
                                                    )
                                                },
                                                onForegroundTintModeChange = { mode ->
                                                    val updated = shapeTintFlags.copy(
                                                        foregroundTintMode = normalizeTintMode(mode)
                                                    )
                                                    shapeTintFlags = updated
                                                    writeAppIconShapeTintFlags(context, updated)
                                                },
                                                onOpenForegroundColorFlight = { bounds, provider ->
                                                    colorFlight = ColorPickerFlightTarget(
                                                        key = "fg_tint",
                                                        title = fgTitle,
                                                        initialColor = shapeTintFlags.foregroundTintColor,
                                                        source = bounds,
                                                        sourceBoundsProvider = provider,
                                                        onColorSelected = { picked ->
                                                            val updated = shapeTintFlags.copy(
                                                                foregroundTintMode = APP_ICON_TINT_MODE_CUSTOM,
                                                                foregroundTintColor = picked
                                                            )
                                                            shapeTintFlags = updated
                                                            writeAppIconShapeTintFlags(context, updated)
                                                        }
                                                    )
                                                }
                                            )
                                        }
                                    }

                                    if (injectedAddonEntries.isNotEmpty()) {
                                        item(key = "app_icons_addon_injected") {
                                            AppIconsAddonInjectedSettings(
                                                entries = injectedAddonEntries,
                                                onIconShapeClick = {
                                                    context.startActivity(
                                                        Intent(context, IconShapeActivity::class.java)
                                                    )
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Level-1 Pseudo-Modal A: Icon Pack Flight Overlay (Mode / Partial / Preview)
                val currentDashboard = dashboard
                if (currentDashboard != null) {
                    fun updatePackOptimistically(updatedPack: org.pixel.customparts.icons.IconPackInfo) {
                        packFlight = packFlight?.copy(pack = updatedPack)
                        dashboard = dashboard?.let { state ->
                            state.copy(
                                iconPacks = state.iconPacks.map {
                                    if (it.packageName == updatedPack.packageName) updatedPack else it
                                }
                            )
                        }
                    }

                    IconPackFlightOverlay(
                        target = packFlight,
                        iconMap = currentDashboard.iconMap,
                        busy = isBusy,
                        scrollBlur = scrollBlur,
                        topInsetPx = effectiveHeaderHeightPx,
                        closeSignal = level1CloseSignal,
                        onProgress = { packFlightT = it },
                        onClosingChanged = { isClosing ->
                            packFlightClosing = isClosing
                            if (isClosing) {
                                val pkg = packFlight?.pack?.packageName
                                val curArmed = if (pkg != null) armedResetPacks[pkg] else null
                                if (pkg != null && curArmed != null) {
                                    val now = SystemClock.uptimeMillis()
                                    val rem = (curArmed + 3000L) - now
                                    if (rem in 1L..450L) {
                                        armedResetPacks[pkg] = now - 3000L + 450L
                                    }
                                }
                            }
                        },
                        onClose = {
                            packFlightClosing = false
                            packFlight = null
                        },
                        onOptimisticPackUpdate = ::updatePackOptimistically,
                        onApplyMode = { pack, mode ->
                            startIconOperation {
                                IconPackManager.startApplyAll(context, pack.packageName, mode)
                            }
                        },
                        onApplyPartial = { pack, selectedPackages ->
                            startIconOperation {
                                IconPackManager.startApplyPartial(
                                    context,
                                    pack.packageName,
                                    selectedPackages
                                )
                            }
                        },
                        onResetPack = { pack ->
                            armedResetPacks.remove(pack.packageName)
                            startIconOperation {
                                IconPackManager.startRemoveIconPack(context, pack.packageName)
                            }
                        }
                    )

                    // Level-1 Pseudo-Modal B: App Detail Flight Overlay (Candidates / Library / Shape)
                    AppDetailFlightOverlay(
                        target = appFlight,
                        iconMap = currentDashboard.iconMap,
                        iconPacks = currentDashboard.iconPacks,
                        cachedCustomBitmap = appFlight?.let { appliedIconCache[it.app.packageName] },
                        busy = isBusy,
                        scrollBlur = scrollBlur,
                        topInsetPx = effectiveHeaderHeightPx,
                        closeSignal = level1CloseSignal,
                        onProgress = { appFlightT = it },
                        onClose = { appFlight = null },
                        onRestore = { app ->
                            appliedIconCache.remove(app.packageName)
                            startIconOperation {
                                IconPackManager.startRemovePackageIcon(context, app.packageName)
                            }
                        },
                        onApplyCandidate = { app, candidate ->
                            appliedIconCache.remove(app.packageName)
                            startIconOperation {
                                IconPackManager.startApplySingle(
                                    context,
                                    candidate.iconPackPackage,
                                    app.packageName,
                                    candidate.drawableName
                                )
                            }
                        },
                        onImportCustomIcon = { app, uri ->
                            appliedIconCache.remove(app.packageName)
                            startIconOperation {
                                IconPackManager.startImportPackageIcon(context, app.packageName, uri)
                            }
                        },
                        onSaveShapeOverrides = { app, overrides ->
                            IconPackManager.savePackageShapeOverrides(
                                context,
                                app.packageName,
                                overrides
                            )
                            scope.launch {
                                loadDashboardSilent(forceRefresh = true)
                            }
                        }
                    )
                }

                // Level-1 Pseudo-Modal C: Frosted Color Picker Flight Overlay
                val colorRequest = remember(colorFlight) {
                    colorFlight?.let { t ->
                        ColorPickerFlightRequest(
                            title = t.title,
                            initialColor = t.initialColor,
                            showAlpha = true,
                            sourceRect = t.source,
                            sourceBoundsProvider = t.sourceBoundsProvider,
                            onColorSelected = t.onColorSelected
                        )
                    }
                }
                ColorPickerFlightOverlay(
                    request = colorRequest,
                    onClose = { colorFlight = null },
                    scrollBlur = scrollBlur,
                    onProgress = { colorFlightT = it },
                    topInsetPx = effectiveHeaderHeightPx,
                    closeSignal = level1CloseSignal
                )

                // Level-1 Pseudo-Modal D: Frosted Task Progress / Completion / Clear-All Overlay
                IconTaskFlightOverlay(
                    open = taskModalOpen,
                    sourceRect = taskModalSource,
                    sourceBoundsProvider = taskModalBoundsProvider,
                    progress = activeProgress,
                    completionResult = completionResult,
                    confirmClearAll = taskModalConfirmClearAll,
                    onClose = {
                        taskModalOpen = false
                        taskModalConfirmClearAll = false
                    },
                    onCancelTask = {
                        IconPackManager.cancelActiveOperation()
                    },
                    onDismissCompletion = {
                        taskModalOpen = false
                        completionResult = null
                        IconPackManager.clearCompletedProgress()
                        activeProgress = IconPackManager.getActiveProgress()
                    },
                    onRebootSystem = {
                        taskModalOpen = false
                        completionResult = null
                        IconPackManager.clearCompletedProgress()
                        activeProgress = IconPackManager.getActiveProgress()
                        scope.launch(Dispatchers.IO) { performRebootSystem(context) }
                    },
                    onConfirmClearAll = {
                        taskModalConfirmClearAll = false
                        startIconOperation { IconPackManager.startClearAllIcons(context) }
                    },
                    onDismissClearAll = {
                        taskModalOpen = false
                        taskModalConfirmClearAll = false
                    },
                    scrollBlur = scrollBlur,
                    onProgress = { taskModalT = it },
                    topInsetPx = effectiveHeaderHeightPx,
                    closeSignal = level1CloseSignal
                )
            }
        } // end SliderMorphRoot

        // Floating Frosted Task Progress Bar at the bottom (outside SliderMorphRoot)
        IconTaskFloatBar(
            progress = activeProgress,
            blurState = scrollBlur,
            morphBlurState = if (nestedMorphOpen) sliderModalBlur else cardModalBlur,
            morphActive = nestedMorphOpen,
            tintBoost = 0.25f * sliderMorphHost.morphProgress,
            isHiddenForFlight = taskModalOpen && !taskModalConfirmClearAll,
            onBoundsChanged = { bounds, provider ->
                if (!taskModalConfirmClearAll) {
                    taskModalSource = bounds
                    taskModalBoundsProvider = provider
                }
            },
            onCancel = { IconPackManager.cancelActiveOperation() },
            onExpand = { bounds, provider ->
                if (nestedMorphOpen) {
                    sliderMorphHost.requestAnimatedClose()
                } else {
                    taskModalSource = bounds
                    taskModalBoundsProvider = provider
                    taskModalConfirmClearAll = false
                    taskModalOpen = true
                }
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .zIndex(if (anyLevel1ModalOpen) 0.5f else 3f)
        )

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
            // 1. Pinned TopAppBar Row with Silent Spinning Refresh Button
            val spinTransition = rememberInfiniteTransition(label = "SilentRefreshSpin")
            val spinAngle by spinTransition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 750, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "SilentRefreshAngle"
            )

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
                    text = stringResource(R.string.app_icons_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = { triggerManualSilentRefresh() },
                    enabled = !isInitialLoading && !isBusy
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = stringResource(R.string.app_icons_refresh),
                        tint = if (isSilentRefreshing) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        modifier = Modifier.rotate(if (isSilentRefreshing) spinAngle else 0f)
                    )
                }
            }

            // 2. Clipped Collapsible Container for Master Switch + StudioTabs
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
                    stringResource(R.string.app_icons_packs_section),
                    stringResource(R.string.app_icons_all_apps_section),
                    stringResource(R.string.app_icons_advanced_settings_title)
                )

                if (isLandscape && showHardcodedIconControls) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            GenericSwitchRow(
                                title = stringResource(R.string.app_icons_module_enabled_title),
                                summary = stringResource(R.string.app_icons_module_enabled_summary),
                                checked = moduleEnabled,
                                onCheckedChange = { enabled ->
                                    moduleEnabled = enabled
                                    launcherOnlyEnabled = false
                                    writeAppIconModuleFlags(
                                        context = context,
                                        moduleEnabled = enabled,
                                        launcherOnly = false
                                    )
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
                                    title = stringResource(R.string.app_icons_module_enabled_title),
                                    summary = stringResource(R.string.app_icons_module_enabled_summary),
                                    checked = moduleEnabled,
                                    onCheckedChange = { enabled ->
                                        moduleEnabled = enabled
                                        launcherOnlyEnabled = false
                                        writeAppIconModuleFlags(
                                            context = context,
                                            moduleEnabled = enabled,
                                            launcherOnly = false
                                        )
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
}