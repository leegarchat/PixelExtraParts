package org.pixel.customparts.activities

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.pixel.customparts.R
import org.pixel.customparts.SettingsKeys
import org.pixel.customparts.dynamicDarkColorScheme
import org.pixel.customparts.dynamicLightColorScheme
import org.pixel.customparts.icons.AppIconCandidate
import org.pixel.customparts.icons.AppIconShapeOverrides
import org.pixel.customparts.icons.IconApplyMode
import org.pixel.customparts.icons.IconApplyProgress
import org.pixel.customparts.icons.IconApplyResult
import org.pixel.customparts.icons.IconDashboardState
import org.pixel.customparts.icons.IconMapSnapshot
import org.pixel.customparts.icons.IconPackApplyStatus
import org.pixel.customparts.icons.IconPackEntry
import org.pixel.customparts.icons.IconPackInfo
import org.pixel.customparts.icons.IconPackManager
import org.pixel.customparts.icons.IconPackPreviewItem
import org.pixel.customparts.icons.IconShapeAreaConfig
import org.pixel.customparts.icons.InstalledIconApp
import org.pixel.customparts.ui.REBOOT_BUBBLE_CONTENT_BOTTOM_PADDING
import org.pixel.customparts.ui.ColorPickerDialog
import org.pixel.customparts.ui.RebootBubble
import org.pixel.customparts.ui.RebootBubbleMenuAction
import org.pixel.customparts.ui.SettingsGroupCard
import org.pixel.customparts.ui.SliderSettingFloat
import org.pixel.customparts.ui.TopBarBlurOverlay
import org.pixel.customparts.ui.TopStatusVeil
import org.pixel.customparts.ui.addons.AddonMainEntry
import org.pixel.customparts.ui.addons.AddonMainEntryRow
import org.pixel.customparts.ui.addons.flattenSettings
import org.pixel.customparts.ui.addons.scanAddonActivityEntries
import org.pixel.customparts.ui.performRebootSystem
import org.pixel.customparts.ui.recordLayer
import org.pixel.customparts.ui.rememberGraphicsLayerRecordingState
import org.pixel.customparts.ui.SliderMorphRoot
import org.pixel.customparts.ui.rememberSliderMorphHostState
import org.pixel.customparts.utils.SettingsCompat
import java.util.Locale
import androidx.compose.ui.res.stringResource







private const val APP_ICON_TINT_MODE_OFF = 0
private const val APP_ICON_TINT_MODE_CUSTOM = 1
private const val APP_ICON_TINT_MODE_AUTO = 2
private const val DEFAULT_SHAPE_BACKGROUND_TINT_COLOR = 0x00000000
private const val DEFAULT_SHAPE_FOREGROUND_TINT_COLOR = -0x1

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
    val blurState = rememberGraphicsLayerRecordingState()
    // Morph-time header blur (scroll+modal) + hoisted morph host so the header
    // tint boost runs at exactly the modal expand speed.
    val morphBlurState = rememberGraphicsLayerRecordingState()
    val morphHost = rememberSliderMorphHostState()
    val listState = rememberLazyListState()

    var dashboard by remember { mutableStateOf(IconPackManager.getCachedDashboardState()) }
    var isLoading by remember { mutableStateOf(dashboard == null) }
    var loadFailed by remember { mutableStateOf(false) }
    var packsExpanded by rememberSaveable { mutableStateOf(true) }
    var advancedSettingsExpanded by rememberSaveable { mutableStateOf(true) }
    var appsExpanded by rememberSaveable { mutableStateOf(true) }
    var showSystemApps by rememberSaveable { mutableStateOf(true) }
    var showHiddenApps by rememberSaveable { mutableStateOf(false) }
    var appSearchQuery by rememberSaveable { mutableStateOf("") }
    var partialPack by remember { mutableStateOf<IconPackInfo?>(null) }
    var previewPack by remember { mutableStateOf<IconPackInfo?>(null) }
    var expandedAppPackage by rememberSaveable { mutableStateOf<String?>(null) }
    var applyModePack by remember { mutableStateOf<IconPackInfo?>(null) }
    var confirmClearAllIcons by remember { mutableStateOf(false) }
    var activeProgress by remember { mutableStateOf(IconPackManager.getActiveProgress()) }
    var completionResult by remember { mutableStateOf<IconApplyResult?>(null) }
    var handledProgressTaskId by rememberSaveable { mutableStateOf<String?>(null) }
    var injectedAddonEntries by remember { mutableStateOf<List<AddonMainEntry>>(emptyList()) }
    val initialModuleFlags = remember { readAppIconModuleFlags(context) }
    val initialSystemShapeFlags = remember { readAppIconSystemShapeFlags(context) }
    val initialNotificationShapeFlags = remember { readAppIconNotificationShapeFlags(context) }
    val initialLauncherShapeFlags = remember { readAppIconLauncherShapeFlags(context) }
    val initialShapeTintFlags = remember { readAppIconShapeTintFlags(context) }
    var moduleEnabled by remember { mutableStateOf(initialModuleFlags.moduleEnabled) }
    var launcherOnlyEnabled by remember { mutableStateOf(initialModuleFlags.launcherOnly) }
    var systemStretchShapeEnabled by remember { mutableStateOf(initialSystemShapeFlags.stretchShape) }
    var systemRemoveShapeEnabled by remember { mutableStateOf(initialSystemShapeFlags.removeShape) }
    var systemShapeScalePercent by remember { mutableStateOf(initialSystemShapeFlags.scalePercent) }
    var notificationStretchShapeEnabled by remember { mutableStateOf(initialNotificationShapeFlags.stretchShape) }
    var notificationRemoveShapeEnabled by remember { mutableStateOf(initialNotificationShapeFlags.removeShape) }
    var notificationShapeScalePercent by remember { mutableStateOf(initialNotificationShapeFlags.scalePercent) }
    var launcherStretchShapeEnabled by remember { mutableStateOf(initialLauncherShapeFlags.stretchShape) }
    var launcherRemoveShapeEnabled by remember { mutableStateOf(initialLauncherShapeFlags.removeShape) }
    var launcherShapeScalePercent by remember { mutableStateOf(initialLauncherShapeFlags.scalePercent) }
    var shapeTintFlags by remember { mutableStateOf(initialShapeTintFlags) }
    val injectedAddonSettings = remember(injectedAddonEntries) { injectedAddonEntries.flatMap { flattenSettings(it.settings) } }
    val injectedAddonKeys = remember(injectedAddonSettings) { injectedAddonSettings.map { it.key }.toSet() }
    val showHardcodedIconControls = injectedAddonEntries.isEmpty()
    val showHardcodedLauncherIconControls = showHardcodedIconControls &&
        SettingsKeys.APP_ICONS_LAUNCHER_ENABLED !in injectedAddonKeys &&
        SettingsKeys.APP_ICONS_LAUNCHER_STRETCH_SHAPE !in injectedAddonKeys &&
        SettingsKeys.APP_ICONS_LAUNCHER_REMOVE_SHAPE !in injectedAddonKeys &&
        SettingsKeys.APP_ICONS_LAUNCHER_SHAPE_SCALE !in injectedAddonKeys

    suspend fun loadDashboard(showSpinner: Boolean = true, forceRefresh: Boolean = false) {
        if (showSpinner) isLoading = true
        loadFailed = false
        runCatching { IconPackManager.loadDashboardState(context, forceRefresh) }
            .onSuccess { dashboard = it }
            .onFailure { loadFailed = true }
        isLoading = false
    }

    fun startIconOperation(start: () -> Boolean) {
        if (!start()) {
            Toast.makeText(context, context.getString(R.string.app_icons_operation_running), Toast.LENGTH_SHORT).show()
        } else {
            activeProgress = IconPackManager.getActiveProgress()
        }
    }

    LaunchedEffect(Unit) {
        loadDashboard(showSpinner = dashboard == null)
    }

    LaunchedEffect(Unit) {
        injectedAddonEntries = withContext(Dispatchers.IO) {
            scanAddonActivityEntries(context, "AppIconsActivity", "advanced")
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            val progress = IconPackManager.getActiveProgress()
            activeProgress = progress
            if (progress?.completed == true && handledProgressTaskId != progress.taskId) {
                handledProgressTaskId = progress.taskId
                completionResult = progress.result
                loadDashboard(showSpinner = false, forceRefresh = true)
            }
            delay(500)
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

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentWindowInsets = WindowInsets.navigationBars,
        floatingActionButton = {
            RebootBubble(
                extraActions = listOf(
                    RebootBubbleMenuAction(
                        icon = Icons.Rounded.Delete,
                        label = stringResource(R.string.app_icons_clear_all),
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        onClick = { confirmClearAllIcons = true }
                    )
                )
            )
        },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.app_icons_title),
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.nav_back))
                    }
                },
                actions = {
                    IconButton(
                        onClick = { scope.launch { loadDashboard(forceRefresh = true) } },
                        enabled = !isLoading && activeProgress?.completed != false
                    ) {
                        Icon(Icons.Rounded.Refresh, stringResource(R.string.app_icons_refresh))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent
                )
            )
        }
    ) { innerPadding ->
        // Parent for the header overlays: they must stay OUTSIDE SliderMorphRoot.
        // The morph-time header blur is baked from the Root's subtree — a header
        // reading it from inside would reference itself (RenderNode cycle = SIGSEGV).
        Box(modifier = Modifier.fillMaxSize()) {
        SliderMorphRoot(
            host = morphHost,
            blurState = blurState,
            morphBlurState = morphBlurState,
            morphBackgroundColor = MaterialTheme.colorScheme.surfaceContainer
        ) {
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
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when {
                    isLoading -> item { LoadingCard() }
                    loadFailed -> item { EmptyStateText(stringResource(R.string.app_icons_load_failed)) }
                    else -> {
                        val state = dashboard
                        if (state != null) {
                            if (showHardcodedIconControls) {
                                item {
                                    AppIconsModuleSwitch(
                                        checked = moduleEnabled,
                                        launcherOnlyChecked = launcherOnlyEnabled,
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
                                        advancedExpanded = advancedSettingsExpanded,
                                        showHardcodedControls = showHardcodedIconControls,
                                        showLauncherControls = showHardcodedLauncherIconControls,
                                        onIconShapeClick = {
                                            context.startActivity(Intent(context, IconShapeActivity::class.java))
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
                                            if (enabled) {
                                                systemRemoveShapeEnabled = false
                                            }
                                            writeAppIconSystemShapeFlags(
                                                context = context,
                                                stretchShape = enabled,
                                                removeShape = if (enabled) false else systemRemoveShapeEnabled,
                                                scalePercent = systemShapeScalePercent
                                            )
                                        },
                                        onSystemRemoveShapeChange = { enabled ->
                                            systemRemoveShapeEnabled = enabled
                                            if (enabled) {
                                                systemStretchShapeEnabled = false
                                            }
                                            writeAppIconSystemShapeFlags(
                                                context = context,
                                                stretchShape = if (enabled) false else systemStretchShapeEnabled,
                                                removeShape = enabled,
                                                scalePercent = systemShapeScalePercent
                                            )
                                        },
                                        onNotificationStretchShapeChange = { enabled ->
                                            notificationStretchShapeEnabled = enabled
                                            if (enabled) {
                                                notificationRemoveShapeEnabled = false
                                            }
                                            writeAppIconNotificationShapeFlags(
                                                context = context,
                                                stretchShape = enabled,
                                                removeShape = if (enabled) false else notificationRemoveShapeEnabled,
                                                scalePercent = notificationShapeScalePercent
                                            )
                                        },
                                        onNotificationRemoveShapeChange = { enabled ->
                                            notificationRemoveShapeEnabled = enabled
                                            if (enabled) {
                                                notificationStretchShapeEnabled = false
                                            }
                                            writeAppIconNotificationShapeFlags(
                                                context = context,
                                                stretchShape = if (enabled) false else notificationStretchShapeEnabled,
                                                removeShape = enabled,
                                                scalePercent = notificationShapeScalePercent
                                            )
                                        },
                                        onLauncherStretchShapeChange = { enabled ->
                                            launcherStretchShapeEnabled = enabled
                                            if (enabled) {
                                                launcherRemoveShapeEnabled = false
                                            }
                                            writeAppIconLauncherShapeFlags(
                                                context = context,
                                                stretchShape = enabled,
                                                removeShape = if (enabled) false else launcherRemoveShapeEnabled,
                                                scalePercent = launcherShapeScalePercent
                                            )
                                        },
                                        onLauncherRemoveShapeChange = { enabled ->
                                            launcherRemoveShapeEnabled = enabled
                                            if (enabled) {
                                                launcherStretchShapeEnabled = false
                                            }
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
                                            val updated = shapeTintFlags.copy(backgroundTintMode = normalizeTintMode(mode))
                                            shapeTintFlags = updated
                                            writeAppIconShapeTintFlags(context, updated)
                                        },
                                        onBackgroundTintColorChange = { color ->
                                            val updated = shapeTintFlags.copy(
                                                backgroundTintMode = APP_ICON_TINT_MODE_CUSTOM,
                                                backgroundTintColor = color
                                            )
                                            shapeTintFlags = updated
                                            writeAppIconShapeTintFlags(context, updated)
                                        },
                                        onForegroundTintModeChange = { mode ->
                                            val updated = shapeTintFlags.copy(foregroundTintMode = normalizeTintMode(mode))
                                            shapeTintFlags = updated
                                            writeAppIconShapeTintFlags(context, updated)
                                        },
                                        onForegroundTintColorChange = { color ->
                                            val updated = shapeTintFlags.copy(
                                                foregroundTintMode = APP_ICON_TINT_MODE_CUSTOM,
                                                foregroundTintColor = color
                                            )
                                            shapeTintFlags = updated
                                            writeAppIconShapeTintFlags(context, updated)
                                        },
                                        onAdvancedExpandedChange = { advancedSettingsExpanded = it }
                                    )
                                }
                            }

                            if (injectedAddonEntries.isNotEmpty()) {
                                item(key = "app_icons_addon_injected") {
                                    AppIconsAddonInjectedSettings(
                                        entries = injectedAddonEntries,
                                        onIconShapeClick = {
                                            context.startActivity(Intent(context, IconShapeActivity::class.java))
                                        }
                                    )
                                }
                            }

                            if (activeProgress != null) {
                                item {
                                    ApplyProgressCard(progress = activeProgress!!)
                                }
                            }

                            item {
                                IconPacksBlock(
                                    packs = state.iconPacks,
                                    expanded = packsExpanded,
                                    busy = activeProgress?.completed == false,
                                    onExpandChange = { packsExpanded = it },
                                    onApplyAll = { pack -> applyModePack = pack },
                                    onApplyPartial = { partialPack = it },
                                    onViewPack = { previewPack = it },
                                    onApplyUpdate = { pack ->
                                        startIconOperation {
                                            IconPackManager.startApplyAll(context, pack.packageName, IconApplyMode.INSTALLED_ONLY)
                                        }
                                    },
                                    onCancelAll = { pack ->
                                        startIconOperation { IconPackManager.startRemoveIconPack(context, pack.packageName) }
                                    }
                                )
                            }

                            item {
                                ExpandableSectionHeader(
                                    title = stringResource(R.string.app_icons_all_apps_section),
                                    subtitle = stringResource(R.string.app_icons_app_count_format, visibleApps.size),
                                    icon = Icons.Rounded.Apps,
                                    expanded = appsExpanded,
                                    onExpandChange = { appsExpanded = it }
                                )
                            }

                            if (appsExpanded) {
                                item(key = "app_icons_filters") {
                                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        AppIconsFilterSwitchRow(
                                            title = stringResource(R.string.app_icons_show_system_title),
                                            summary = stringResource(R.string.app_icons_show_system_summary),
                                            checked = showSystemApps,
                                            onCheckedChange = { showSystemApps = it }
                                        )
                                        AppIconsFilterSwitchRow(
                                            title = stringResource(R.string.app_icons_show_hidden_title),
                                            summary = stringResource(R.string.app_icons_show_hidden_summary),
                                            checked = showHiddenApps,
                                            onCheckedChange = { showHiddenApps = it }
                                        )
                                        AppIconsSearchField(
                                            query = appSearchQuery,
                                            hint = stringResource(R.string.launcher_hidden_apps_search_hint),
                                            onQueryChange = { appSearchQuery = it }
                                        )
                                    }
                                }
                                if (visibleApps.isEmpty()) {
                                    item(key = "app_icons_empty") {
                                        EmptyStateText(stringResource(R.string.app_icons_no_apps))
                                    }
                                } else {
                                    items(visibleApps, key = { it.packageName }) { app ->
                                        InstalledAppCard(
                                            app = app,
                                            iconMap = state.iconMap,
                                            iconPacks = state.iconPacks,
                                            expanded = expandedAppPackage == app.packageName,
                                            busy = activeProgress?.completed == false,
                                            onExpandChange = { expanded ->
                                                expandedAppPackage = if (expanded) app.packageName else null
                                            },
                                            onRestore = {
                                                expandedAppPackage = null
                                                startIconOperation {
                                                    IconPackManager.startRemovePackageIcon(context, app.packageName)
                                                }
                                            },
                                            onApply = { candidate ->
                                                expandedAppPackage = null
                                                startIconOperation {
                                                    IconPackManager.startApplySingle(
                                                        context,
                                                        candidate.iconPackPackage,
                                                        app.packageName,
                                                        candidate.drawableName
                                                    )
                                                }
                                            },
                                            onImportCustomIcon = { uri ->
                                                expandedAppPackage = null
                                                startIconOperation {
                                                    IconPackManager.startImportPackageIcon(context, app.packageName, uri)
                                                }
                                            },
                                            onSaveShapeOverrides = { overrides ->
                                                IconPackManager.savePackageShapeOverrides(context, app.packageName, overrides)
                                                scope.launch { loadDashboard(showSpinner = false, forceRefresh = true) }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

        } // end SliderMorphRoot content — header below stays outside the recorded subtree

        TopBarBlurOverlay(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter),
            topBarHeight = innerPadding.calculateTopPadding(),
            blurState = blurState,
            morphBlurState = morphBlurState,
            morphActive = morphHost.request != null,
            tintBoost = 0.25f * morphHost.morphProgress
        )
        TopStatusVeil(
            backgroundColor = MaterialTheme.colorScheme.surfaceContainer,
            veilHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 14.dp,
            modifier = Modifier.fillMaxWidth()
        )
        }
    }

    val state = dashboard
    if (partialPack != null && state != null) {
        PartialApplyDialog(
            pack = partialPack!!,
            iconMap = state.iconMap,
            onDismiss = { partialPack = null },
            onApply = { selectedPackages ->
                val pack = partialPack ?: return@PartialApplyDialog
                partialPack = null
                startIconOperation { IconPackManager.startApplyPartial(context, pack.packageName, selectedPackages) }
            }
        )
    }

    if (applyModePack != null) {
        ApplyModeDialog(
            pack = applyModePack!!,
            onDismiss = { applyModePack = null },
            onApply = { mode ->
                val pack = applyModePack ?: return@ApplyModeDialog
                applyModePack = null
                startIconOperation { IconPackManager.startApplyAll(context, pack.packageName, mode) }
            }
        )
    }

    if (previewPack != null) {
        PackPreviewDialog(
            pack = previewPack!!,
            onDismiss = { previewPack = null }
        )
    }

    if (confirmClearAllIcons) {
        AlertDialog(
            onDismissRequest = { confirmClearAllIcons = false },
            icon = {
                Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error)
            },
            title = { Text(stringResource(R.string.app_icons_clear_all_confirm_title)) },
            text = { Text(stringResource(R.string.app_icons_clear_all_confirm_message)) },
            confirmButton = {
                Button(
                    onClick = {
                        confirmClearAllIcons = false
                        startIconOperation { IconPackManager.startClearAllIcons(context) }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text(stringResource(R.string.app_icons_clear_all), maxLines = 1)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearAllIcons = false }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }

    if (completionResult != null) {
        IconApplyCompletedDialog(
            result = completionResult!!,
            onDismiss = {
                completionResult = null
                IconPackManager.clearCompletedProgress()
                activeProgress = IconPackManager.getActiveProgress()
            },
            onRebootSystem = {
                completionResult = null
                IconPackManager.clearCompletedProgress()
                activeProgress = IconPackManager.getActiveProgress()
                scope.launch(Dispatchers.IO) { performRebootSystem(context) }
            }
        )
    }
}
