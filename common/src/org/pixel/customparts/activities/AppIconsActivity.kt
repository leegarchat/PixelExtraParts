package org.pixel.customparts.activities

import android.content.Context
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
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Close
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
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
import org.pixel.customparts.ui.SliderSettingFloat
import org.pixel.customparts.ui.TopBarBlurOverlay
import org.pixel.customparts.ui.performRebootSystem
import org.pixel.customparts.ui.recordLayer
import org.pixel.customparts.ui.rememberGraphicsLayerRecordingState
import org.pixel.customparts.utils.SettingsCompat
import org.pixel.customparts.utils.dynamicStringResource
import java.util.Locale

private data class AppIconModuleFlags(
    val moduleEnabled: Boolean,
    val launcherOnly: Boolean
)

private data class AppIconSystemShapeFlags(
    val stretchShape: Boolean,
    val removeShape: Boolean,
    val scalePercent: Float
)

private data class AppIconNotificationShapeFlags(
    val stretchShape: Boolean,
    val removeShape: Boolean,
    val scalePercent: Float
)

private data class AppIconLauncherShapeFlags(
    val stretchShape: Boolean,
    val removeShape: Boolean,
    val scalePercent: Float
)

private data class AppIconShapeTintFlags(
    val backgroundTintMode: Int,
    val backgroundTintColor: Int,
    val foregroundTintMode: Int,
    val foregroundTintColor: Int
)

private data class AppIconChooserItem(
    val iconPackPackage: String,
    val iconPackLabel: String,
    val drawableName: String,
    val label: String,
    val detected: Boolean,
    val appPackageName: String?
)

private const val APP_ICON_TINT_MODE_OFF = 0
private const val APP_ICON_TINT_MODE_CUSTOM = 1
private const val APP_ICON_TINT_MODE_AUTO = 2
private const val DEFAULT_SHAPE_BACKGROUND_TINT_COLOR = 0x00000000
private const val DEFAULT_SHAPE_FOREGROUND_TINT_COLOR = -0x1

private fun readAppIconModuleFlags(context: Context): AppIconModuleFlags {
    val frameworkEnabled = SettingsCompat.isEnabled(context, SettingsKeys.APP_ICONS_ENABLED, true)
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

private fun normalizeShapeScale(value: Float): Float {
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
    val listState = rememberLazyListState()
    val isScrolled by remember { derivedStateOf { listState.canScrollBackward } }

    var dashboard by remember { mutableStateOf<IconDashboardState?>(null) }
    var isLoading by remember { mutableStateOf(true) }
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
    var activeProgress by remember { mutableStateOf(IconPackManager.getActiveProgress()) }
    var completionResult by remember { mutableStateOf<IconApplyResult?>(null) }
    var handledProgressTaskId by rememberSaveable { mutableStateOf<String?>(null) }
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

    suspend fun loadDashboard(showSpinner: Boolean = true) {
        if (showSpinner) isLoading = true
        loadFailed = false
        runCatching { IconPackManager.loadDashboardState(context) }
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
        loadDashboard()
    }

    LaunchedEffect(Unit) {
        while (true) {
            val progress = IconPackManager.getActiveProgress()
            activeProgress = progress
            if (progress?.completed == true && handledProgressTaskId != progress.taskId) {
                handledProgressTaskId = progress.taskId
                completionResult = progress.result
                loadDashboard(showSpinner = false)
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
        floatingActionButton = { RebootBubble() },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        dynamicStringResource(R.string.app_icons_title),
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, dynamicStringResource(R.string.nav_back))
                    }
                },
                actions = {
                    IconButton(
                        onClick = { scope.launch { loadDashboard() } },
                        enabled = !isLoading && activeProgress?.completed != false
                    ) {
                        Icon(Icons.Rounded.Refresh, dynamicStringResource(R.string.app_icons_refresh))
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
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when {
                    isLoading -> item { LoadingCard() }
                    loadFailed -> item { EmptyStateText(dynamicStringResource(R.string.app_icons_load_failed)) }
                    else -> {
                        val state = dashboard
                        if (state != null) {
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
                                    title = dynamicStringResource(R.string.app_icons_all_apps_section),
                                    subtitle = dynamicStringResource(R.string.app_icons_app_count_format, visibleApps.size),
                                    icon = Icons.Rounded.Apps,
                                    expanded = appsExpanded,
                                    onExpandChange = { appsExpanded = it }
                                )
                            }

                            if (appsExpanded) {
                                item(key = "app_icons_filters") {
                                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        AppIconsFilterSwitchRow(
                                            title = dynamicStringResource(R.string.app_icons_show_system_title),
                                            summary = dynamicStringResource(R.string.app_icons_show_system_summary),
                                            checked = showSystemApps,
                                            onCheckedChange = { showSystemApps = it }
                                        )
                                        AppIconsFilterSwitchRow(
                                            title = dynamicStringResource(R.string.app_icons_show_hidden_title),
                                            summary = dynamicStringResource(R.string.app_icons_show_hidden_summary),
                                            checked = showHiddenApps,
                                            onCheckedChange = { showHiddenApps = it }
                                        )
                                        AppIconsSearchField(
                                            query = appSearchQuery,
                                            hint = dynamicStringResource(R.string.launcher_hidden_apps_search_hint),
                                            onQueryChange = { appSearchQuery = it }
                                        )
                                    }
                                }
                                if (visibleApps.isEmpty()) {
                                    item(key = "app_icons_empty") {
                                        EmptyStateText(dynamicStringResource(R.string.app_icons_no_apps))
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
                                                scope.launch { loadDashboard(showSpinner = false) }
                                            }
                                        )
                                    }
                                }
                            }
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

@Composable
private fun LoadingCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    }
}

@Composable
private fun AppIconsModuleSwitch(
    checked: Boolean,
    launcherOnlyChecked: Boolean,
    systemStretchShapeChecked: Boolean,
    systemRemoveShapeChecked: Boolean,
    notificationStretchShapeChecked: Boolean,
    notificationRemoveShapeChecked: Boolean,
    launcherStretchShapeChecked: Boolean,
    launcherRemoveShapeChecked: Boolean,
    systemShapeScalePercent: Float,
    notificationShapeScalePercent: Float,
    launcherShapeScalePercent: Float,
    backgroundTintMode: Int,
    backgroundTintColor: Int,
    foregroundTintMode: Int,
    foregroundTintColor: Int,
    advancedExpanded: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onLauncherOnlyChange: (Boolean) -> Unit,
    onSystemStretchShapeChange: (Boolean) -> Unit,
    onSystemRemoveShapeChange: (Boolean) -> Unit,
    onNotificationStretchShapeChange: (Boolean) -> Unit,
    onNotificationRemoveShapeChange: (Boolean) -> Unit,
    onLauncherStretchShapeChange: (Boolean) -> Unit,
    onLauncherRemoveShapeChange: (Boolean) -> Unit,
    onSystemShapeScaleChange: (Float) -> Unit,
    onNotificationShapeScaleChange: (Float) -> Unit,
    onLauncherShapeScaleChange: (Float) -> Unit,
    onBackgroundTintModeChange: (Int) -> Unit,
    onBackgroundTintColorChange: (Int) -> Unit,
    onForegroundTintModeChange: (Int) -> Unit,
    onForegroundTintColorChange: (Int) -> Unit,
    onAdvancedExpandedChange: (Boolean) -> Unit
) {
    val systemOptionsEnabled = checked && !launcherOnlyChecked
    val launcherOptionsEnabled = checked
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
        ) {
            AppIconsSwitchRow(
                title = dynamicStringResource(R.string.app_icons_module_enabled_title),
                summary = dynamicStringResource(R.string.app_icons_module_enabled_summary),
                checked = checked,
                enabled = true,
                onCheckedChange = onCheckedChange
            )
            AppIconsSwitchRow(
                title = dynamicStringResource(R.string.app_icons_launcher_only_title),
                summary = dynamicStringResource(R.string.app_icons_launcher_only_summary),
                checked = checked && launcherOnlyChecked,
                enabled = checked,
                onCheckedChange = onLauncherOnlyChange
            )
            ExpandableSectionHeaderContent(
                title = dynamicStringResource(R.string.app_icons_advanced_settings_title),
                subtitle = dynamicStringResource(R.string.app_icons_advanced_settings_summary),
                icon = Icons.Rounded.Apps,
                expanded = advancedExpanded,
                onExpandChange = onAdvancedExpandedChange
            )
            AnimatedVisibility(
                visible = advancedExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    AppIconsSwitchRow(
                        title = dynamicStringResource(R.string.app_icons_system_stretch_shape_title),
                        summary = dynamicStringResource(R.string.app_icons_system_stretch_shape_summary),
                        checked = systemOptionsEnabled && systemStretchShapeChecked,
                        enabled = systemOptionsEnabled,
                        onCheckedChange = onSystemStretchShapeChange
                    )
                    if (systemStretchShapeChecked) {
                        AppIconsScaleSliderRow(
                            title = dynamicStringResource(R.string.app_icons_system_shape_scale_title),
                            value = systemShapeScalePercent,
                            enabled = systemOptionsEnabled,
                            onValueChange = onSystemShapeScaleChange
                        )
                    }
                    AppIconsSwitchRow(
                        title = dynamicStringResource(R.string.app_icons_system_remove_shape_title),
                        summary = dynamicStringResource(R.string.app_icons_system_remove_shape_summary),
                        checked = systemOptionsEnabled && systemRemoveShapeChecked,
                        enabled = systemOptionsEnabled,
                        onCheckedChange = onSystemRemoveShapeChange
                    )
                    AppIconsSwitchRow(
                        title = dynamicStringResource(R.string.app_icons_notification_stretch_shape_title),
                        summary = dynamicStringResource(R.string.app_icons_notification_stretch_shape_summary),
                        checked = systemOptionsEnabled && notificationStretchShapeChecked,
                        enabled = systemOptionsEnabled,
                        onCheckedChange = onNotificationStretchShapeChange
                    )
                    if (notificationStretchShapeChecked) {
                        AppIconsScaleSliderRow(
                            title = dynamicStringResource(R.string.app_icons_notification_shape_scale_title),
                            value = notificationShapeScalePercent,
                            enabled = systemOptionsEnabled,
                            onValueChange = onNotificationShapeScaleChange
                        )
                    }
                    AppIconsSwitchRow(
                        title = dynamicStringResource(R.string.app_icons_notification_remove_shape_title),
                        summary = dynamicStringResource(R.string.app_icons_notification_remove_shape_summary),
                        checked = systemOptionsEnabled && notificationRemoveShapeChecked,
                        enabled = systemOptionsEnabled,
                        onCheckedChange = onNotificationRemoveShapeChange
                    )
                    AppIconsSwitchRow(
                        title = dynamicStringResource(R.string.app_icons_launcher_stretch_shape_title),
                        summary = dynamicStringResource(R.string.app_icons_launcher_stretch_shape_summary),
                        checked = launcherOptionsEnabled && launcherStretchShapeChecked,
                        enabled = launcherOptionsEnabled,
                        onCheckedChange = onLauncherStretchShapeChange
                    )
                    if (launcherStretchShapeChecked) {
                        AppIconsScaleSliderRow(
                            title = dynamicStringResource(R.string.app_icons_launcher_shape_scale_title),
                            value = launcherShapeScalePercent,
                            enabled = launcherOptionsEnabled,
                            onValueChange = onLauncherShapeScaleChange
                        )
                    }
                    AppIconsSwitchRow(
                        title = dynamicStringResource(R.string.app_icons_launcher_remove_shape_title),
                        summary = dynamicStringResource(R.string.app_icons_launcher_remove_shape_summary),
                        checked = launcherOptionsEnabled && launcherRemoveShapeChecked,
                        enabled = launcherOptionsEnabled,
                        onCheckedChange = onLauncherRemoveShapeChange
                    )
                    AppIconsTintControlRow(
                        title = dynamicStringResource(R.string.app_icons_shape_background_tint_title),
                        summary = dynamicStringResource(R.string.app_icons_shape_background_tint_summary),
                        mode = backgroundTintMode,
                        color = backgroundTintColor,
                        enabled = checked,
                        onModeChange = onBackgroundTintModeChange,
                        onColorChange = onBackgroundTintColorChange
                    )
                    AppIconsTintControlRow(
                        title = dynamicStringResource(R.string.app_icons_shape_foreground_tint_title),
                        summary = dynamicStringResource(R.string.app_icons_shape_foreground_tint_summary),
                        mode = foregroundTintMode,
                        color = foregroundTintColor,
                        enabled = checked,
                        onModeChange = onForegroundTintModeChange,
                        onColorChange = onForegroundTintColorChange
                    )
                }
            }
        }
    }
}

@Composable
private fun AppIconsSwitchRow(
    title: String,
    summary: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.48f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.56f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            modifier = Modifier.padding(start = 12.dp)
        )
    }
}

@Composable
private fun AppIconsTintControlRow(
    title: String,
    summary: String,
    mode: Int,
    color: Int,
    enabled: Boolean,
    onModeChange: (Int) -> Unit,
    onColorChange: (Int) -> Unit
) {
    var showColorPicker by remember { mutableStateOf(false) }
    val swatchBorderColor = if (isSystemInDarkTheme()) Color.White else Color.Black
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.48f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.56f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Box(
                modifier = Modifier
                    .padding(start = 12.dp)
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color(color))
                        .border(1.dp, swatchBorderColor, CircleShape)
                    .clickable(enabled = enabled) { showColorPicker = true }
            )
        }
        Row(
            modifier = Modifier.padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AppIconsTintModeButton(
                text = dynamicStringResource(R.string.app_icons_tint_mode_off),
                selected = mode == APP_ICON_TINT_MODE_OFF,
                enabled = enabled,
                onClick = { onModeChange(APP_ICON_TINT_MODE_OFF) }
            )
            AppIconsTintModeButton(
                text = dynamicStringResource(R.string.app_icons_tint_mode_auto),
                selected = mode == APP_ICON_TINT_MODE_AUTO,
                enabled = enabled,
                onClick = { onModeChange(APP_ICON_TINT_MODE_AUTO) }
            )
            AppIconsTintModeButton(
                text = dynamicStringResource(R.string.app_icons_tint_mode_custom),
                selected = mode == APP_ICON_TINT_MODE_CUSTOM,
                enabled = enabled,
                onClick = { onModeChange(APP_ICON_TINT_MODE_CUSTOM) }
            )
        }
    }

    if (showColorPicker) {
        ColorPickerDialog(
            initialColor = color,
            onColorSelected = { selectedColor ->
                showColorPicker = false
                onColorChange(selectedColor)
            },
            onDismissRequest = { showColorPicker = false }
        )
    }
}

@Composable
private fun AppIconsTintModeButton(
    text: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    if (selected) {
        Button(
            onClick = onClick,
            enabled = enabled,
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Text(text = text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Text(text = text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun AppIconsScaleSliderRow(
    title: String,
    value: Float,
    enabled: Boolean,
    onValueChange: (Float) -> Unit
) {
    SliderSettingFloat(
        title = title,
        value = value,
        range = IconPackManager.MIN_SHAPE_SCALE_PERCENT..IconPackManager.MAX_SHAPE_SCALE_PERCENT,
        unit = "%",
        enabled = enabled,
        onValueChange = { onValueChange(normalizeShapeScale(it)) },
        onDefault = { onValueChange(IconPackManager.DEFAULT_SHAPE_SCALE_PERCENT) },
        valueText = dynamicStringResource(R.string.app_icons_shape_scale_value_format, value),
        showDefaultButton = true
    )
    Text(
        text = dynamicStringResource(R.string.app_icons_shape_scale_summary),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.56f),
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(horizontal = 20.dp)
    )
}

@Composable
private fun ApplyProgressCard(progress: IconApplyProgress) {
    val progressValue = (progress.percent / 100f).coerceIn(0f, 1f)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!progress.completed) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                }
                Text(
                    text = if (progress.completed) {
                        dynamicStringResource(R.string.app_icons_progress_completed)
                    } else {
                        progress.label
                    },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = dynamicStringResource(R.string.app_icons_progress_percent_format, progress.percent),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            LinearProgressIndicator(
                progress = progressValue,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = dynamicStringResource(
                    R.string.app_icons_progress_counts_format,
                    progress.processed,
                    progress.total,
                    progress.applied,
                    progress.removed,
                    progress.skipped,
                    progress.failed
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun IconPacksBlock(
    packs: List<IconPackInfo>,
    expanded: Boolean,
    busy: Boolean,
    onExpandChange: (Boolean) -> Unit,
    onApplyAll: (IconPackInfo) -> Unit,
    onApplyPartial: (IconPackInfo) -> Unit,
    onViewPack: (IconPackInfo) -> Unit,
    onApplyUpdate: (IconPackInfo) -> Unit,
    onCancelAll: (IconPackInfo) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ExpandableSectionHeader(
            title = dynamicStringResource(R.string.app_icons_packs_section),
            subtitle = dynamicStringResource(R.string.app_icons_pack_count_format, packs.size),
            icon = Icons.Rounded.Apps,
            expanded = expanded,
            onExpandChange = onExpandChange
        )

        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (packs.isEmpty()) {
                    EmptyStateText(dynamicStringResource(R.string.app_icons_no_icon_packs))
                } else {
                    packs.forEach { pack ->
                        IconPackCard(
                            pack = pack,
                            busy = busy,
                            onApplyAll = { onApplyAll(pack) },
                            onApplyPartial = { onApplyPartial(pack) },
                            onViewPack = { onViewPack(pack) },
                            onApplyUpdate = { onApplyUpdate(pack) },
                            onCancelAll = { onCancelAll(pack) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpandableSectionHeader(
    title: String,
    subtitle: String,
    icon: ImageVector,
    expanded: Boolean,
    onExpandChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        ExpandableSectionHeaderContent(
            title = title,
            subtitle = subtitle,
            icon = icon,
            expanded = expanded,
            onExpandChange = onExpandChange
        )
    }
}

@Composable
private fun ExpandableSectionHeaderContent(
    title: String,
    subtitle: String,
    icon: ImageVector,
    expanded: Boolean,
    onExpandChange: (Boolean) -> Unit
) {
    val rotation by animateFloatAsState(targetValue = if (expanded) 180f else 0f, label = "section_rotation")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onExpandChange(!expanded) }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.padding(10.dp))
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 14.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            imageVector = Icons.Rounded.ExpandMore,
            contentDescription = null,
            modifier = Modifier.rotate(rotation),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun IconPackCard(
    pack: IconPackInfo,
    busy: Boolean,
    onApplyAll: () -> Unit,
    onApplyPartial: () -> Unit,
    onViewPack: () -> Unit,
    onApplyUpdate: () -> Unit,
    onCancelAll: () -> Unit
) {
    var actionsExpanded by rememberSaveable(pack.packageName) { mutableStateOf(false) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable { actionsExpanded = !actionsExpanded },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DrawableIcon(drawable = pack.icon, size = 48.dp, fallback = Icons.Rounded.Apps)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp)
                ) {
                    Text(
                        text = pack.label,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = pack.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = iconPackStatusText(pack.status),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                Icon(
                    imageVector = Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.rotate(if (actionsExpanded) 180f else 0f),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (busy) {
                    CircularProgressIndicator(modifier = Modifier.size(26.dp), strokeWidth = 2.dp)
                }
            }

            Text(
                text = dynamicStringResource(
                    R.string.app_icons_supported_count_format,
                    pack.supportedPackageCount,
                    pack.supportedIconCount,
                    pack.appliedPackageCount
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (pack.requiresUpdate) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.28f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = dynamicStringResource(R.string.app_icons_requires_update),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onApplyUpdate, enabled = !busy) {
                        Text(dynamicStringResource(R.string.app_icons_apply_update), maxLines = 1)
                    }
                }
            }

            AnimatedVisibility(
                visible = actionsExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onApplyAll,
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.Apps, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(dynamicStringResource(R.string.app_icons_apply_all), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    OutlinedButton(
                        onClick = onApplyPartial,
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.Search, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(dynamicStringResource(R.string.app_icons_apply_partial), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    OutlinedButton(
                        onClick = onViewPack,
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.Apps, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(dynamicStringResource(R.string.app_icons_view_pack), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    OutlinedButton(
                        onClick = onCancelAll,
                        enabled = !busy && pack.appliedPackageCount > 0,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.Refresh, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(dynamicStringResource(R.string.app_icons_cancel_all), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun AppIconsFilterSwitchRow(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = summary,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = Modifier.padding(start = 12.dp)
            )
        }
    }
}

@Composable
private fun InstalledAppCard(
    app: InstalledIconApp,
    iconMap: IconMapSnapshot,
    iconPacks: List<IconPackInfo>,
    expanded: Boolean,
    busy: Boolean,
    onExpandChange: (Boolean) -> Unit,
    onRestore: () -> Unit,
    onApply: (AppIconCandidate) -> Unit,
    onImportCustomIcon: (Uri) -> Unit,
    onSaveShapeOverrides: (AppIconShapeOverrides?) -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val previewSizePx = with(density) { 56.dp.roundToPx() }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onImportCustomIcon(uri)
    }
    var loading by remember(app.packageName, iconMap) { mutableStateOf(false) }
    var candidates by remember(app.packageName, iconMap) { mutableStateOf<List<AppIconCandidate>>(emptyList()) }
    var appliedBitmap by remember(app.packageName, iconMap) { mutableStateOf<Bitmap?>(null) }
    var shapeOverrides by remember(app.packageName, iconMap) {
        mutableStateOf(iconMap.shapeOverrides[app.packageName] ?: app.shapeOverrides ?: AppIconShapeOverrides())
    }
    var chooserOpen by rememberSaveable(app.packageName) { mutableStateOf(false) }

    LaunchedEffect(expanded, app.packageName, iconMap) {
        if (!expanded) return@LaunchedEffect
        loading = true
        candidates = IconPackManager.loadCandidatesForApp(context, app.packageName)
        appliedBitmap = IconPackManager.loadAppliedIconBitmap(context, app.packageName, previewSizePx)
        loading = false
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable { onExpandChange(!expanded) },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DrawableIcon(drawable = app.icon, size = 42.dp, fallback = Icons.Rounded.Apps)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp)
                ) {
                    Text(
                        text = app.label,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = app.appliedIconPackLabel?.let {
                            dynamicStringResource(R.string.app_icons_applied_from_format, it)
                        } ?: dynamicStringResource(R.string.app_icons_not_applied_to_app),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (app.appliedIconPackLabel != null) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                Icon(
                    imageVector = Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.rotate(if (expanded) 180f else 0f),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AppIconCurrentIconCard(app = app, appliedBitmap = appliedBitmap)

                    if (app.appliedIconFileName != null) {
                        OutlinedButton(
                            onClick = onRestore,
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Rounded.Refresh, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(dynamicStringResource(R.string.app_icons_restore_default), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }

                    OutlinedButton(
                        onClick = { importLauncher.launch(arrayOf("image/*", "application/xml", "text/xml")) },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.Apps, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(dynamicStringResource(R.string.app_icons_import_custom_icon), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }

                    OutlinedButton(
                        onClick = { chooserOpen = true },
                        enabled = !busy && iconPacks.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.Search, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(dynamicStringResource(R.string.app_icons_choose_from_pack), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }

                    PerAppShapeSettingsCard(
                        overrides = shapeOverrides,
                        onOverridesChange = { shapeOverrides = it },
                        onSave = { onSaveShapeOverrides(shapeOverrides.normalizedOrNull()) },
                        onReset = {
                            shapeOverrides = AppIconShapeOverrides()
                            onSaveShapeOverrides(null)
                        }
                    )

                    Text(
                        text = dynamicStringResource(R.string.app_icons_available_icons_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )

                    when {
                        loading -> LoadingDialogContent()
                        candidates.isEmpty() -> EmptyStateText(dynamicStringResource(R.string.app_icons_no_app_candidates))
                        else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            val appliedSource = iconMap.sources[app.packageName]
                            candidates.forEach { candidate ->
                                val applied = appliedSource?.iconPackPackage == candidate.iconPackPackage &&
                                    appliedSource.drawableName == candidate.drawableName
                                AppIconCandidateRow(
                                    candidate = candidate,
                                    applied = applied,
                                    busy = busy,
                                    onApply = { onApply(candidate) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (chooserOpen) {
        AppIconChooserDialog(
            app = app,
            iconMap = iconMap,
            iconPacks = iconPacks,
            onDismiss = { chooserOpen = false },
            onApply = { item ->
                chooserOpen = false
                onApply(AppIconCandidate(item.iconPackPackage, item.iconPackLabel, item.drawableName))
            }
        )
    }
}

@Composable
private fun AppIconCurrentIconCard(app: InstalledIconApp, appliedBitmap: Bitmap?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (appliedBitmap != null) {
                BitmapIcon(bitmap = appliedBitmap, size = 56.dp, fallback = Icons.Rounded.Apps)
            } else {
                DrawableIcon(drawable = app.icon, size = 56.dp, fallback = Icons.Rounded.Apps)
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 14.dp)
            ) {
                Text(
                    text = dynamicStringResource(R.string.app_icons_current_icon_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = app.appliedIconPackLabel?.let {
                        dynamicStringResource(R.string.app_icons_applied_from_format, it)
                    } ?: dynamicStringResource(R.string.app_icons_not_applied_to_app),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun AppIconChooserDialog(
    app: InstalledIconApp,
    iconMap: IconMapSnapshot,
    iconPacks: List<IconPackInfo>,
    onDismiss: () -> Unit,
    onApply: (AppIconChooserItem) -> Unit
) {
    val context = LocalContext.current
    var loading by remember(app.packageName, iconPacks) { mutableStateOf(true) }
    var detectedItems by remember(app.packageName, iconPacks) { mutableStateOf<List<AppIconChooserItem>>(emptyList()) }
    var allItems by remember(app.packageName, iconPacks) { mutableStateOf<List<AppIconChooserItem>>(emptyList()) }
    var query by rememberSaveable(app.packageName) { mutableStateOf("") }

    LaunchedEffect(app.packageName, iconPacks) {
        loading = true
        val packLabels = iconPacks.associate { it.packageName to it.label }
        val detected = IconPackManager.loadCandidatesForApp(context, app.packageName)
            .map { candidate ->
                AppIconChooserItem(
                    iconPackPackage = candidate.iconPackPackage,
                    iconPackLabel = candidate.iconPackLabel,
                    drawableName = candidate.drawableName,
                    label = candidate.drawableName,
                    detected = true,
                    appPackageName = app.packageName
                )
            }
        val detectedIds = detected.map { it.iconPackPackage to it.drawableName }.toSet()
        val allPackItems = iconPacks.flatMap { pack ->
            IconPackManager.loadPreviewItems(context, pack.packageName).map { item ->
                AppIconChooserItem(
                    iconPackPackage = item.iconPackPackage,
                    iconPackLabel = packLabels[item.iconPackPackage] ?: pack.label,
                    drawableName = item.drawableName,
                    label = item.label,
                    detected = false,
                    appPackageName = item.appPackageName
                )
            }
        }.filterNot { it.iconPackPackage to it.drawableName in detectedIds }
        detectedItems = detected
        allItems = allPackItems
        loading = false
    }

    val filteredDetected = remember(detectedItems, query) {
        filterChooserItems(detectedItems, query)
    }
    val filteredAll = remember(allItems, query) {
        filterChooserItems(allItems, query)
    }
    val appliedSource = iconMap.sources[app.packageName]

    AppIconsDialog(
        title = dynamicStringResource(R.string.app_icons_choose_from_pack),
        subtitle = app.label,
        onDismiss = onDismiss,
        footer = { TextButton(onClick = onDismiss) { Text(dynamicStringResource(R.string.btn_close)) } }
    ) {
        AppIconsSearchField(
            query = query,
            hint = dynamicStringResource(R.string.app_icons_dialog_search_hint),
            onQueryChange = { query = it }
        )
        Spacer(Modifier.height(12.dp))
        when {
            loading -> LoadingDialogContent()
            filteredDetected.isEmpty() && filteredAll.isEmpty() -> EmptyStateText(dynamicStringResource(R.string.app_icons_no_pack_icons))
            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (filteredDetected.isNotEmpty()) {
                    item(key = "detected_header") {
                        AppIconChooserSectionTitle(dynamicStringResource(R.string.app_icons_detected_alternatives_title))
                    }
                    items(filteredDetected, key = { "detected:${it.iconPackPackage}:${it.drawableName}" }) { item ->
                        AppIconChooserRow(
                            item = item,
                            applied = appliedSource?.iconPackPackage == item.iconPackPackage &&
                                appliedSource.drawableName == item.drawableName,
                            onApply = { onApply(item) }
                        )
                    }
                }
                if (filteredAll.isNotEmpty()) {
                    item(key = "all_header") {
                        AppIconChooserSectionTitle(dynamicStringResource(R.string.app_icons_all_pack_icons_title))
                    }
                    items(filteredAll, key = { "all:${it.iconPackPackage}:${it.drawableName}" }) { item ->
                        AppIconChooserRow(
                            item = item,
                            applied = appliedSource?.iconPackPackage == item.iconPackPackage &&
                                appliedSource.drawableName == item.drawableName,
                            onApply = { onApply(item) }
                        )
                    }
                }
            }
        }
    }
}

private fun filterChooserItems(items: List<AppIconChooserItem>, query: String): List<AppIconChooserItem> {
    val needle = query.trim().lowercase(Locale.getDefault())
    if (needle.isEmpty()) return items
    return items.filter { item ->
        item.label.lowercase(Locale.getDefault()).contains(needle) ||
            item.iconPackLabel.lowercase(Locale.getDefault()).contains(needle) ||
            item.drawableName.lowercase(Locale.US).contains(needle) ||
            item.appPackageName.orEmpty().lowercase(Locale.US).contains(needle)
    }.sortedWith(
        compareBy<AppIconChooserItem> { chooserSearchRank(it, needle) }
            .thenByDescending { it.detected }
            .thenBy { it.drawableName.lowercase(Locale.US) }
            .thenBy { it.iconPackLabel.lowercase(Locale.getDefault()) }
    )
}

private fun chooserSearchRank(item: AppIconChooserItem, needle: String): Int {
    return listOf(
        searchableRank(item.drawableName, needle),
        searchableRank(item.label, needle) + 4,
        searchableRank(item.appPackageName.orEmpty(), needle) + 8,
        searchableRank(item.iconPackLabel, needle) + 12
    ).minOrNull() ?: Int.MAX_VALUE
}

private fun previewSearchRank(item: IconPackPreviewItem, needle: String): Int {
    return listOf(
        searchableRank(item.drawableName, needle),
        searchableRank(item.label, needle) + 4,
        searchableRank(item.appPackageName.orEmpty(), needle) + 8
    ).minOrNull() ?: Int.MAX_VALUE
}

private fun searchableRank(value: String, needle: String): Int {
    if (value.isBlank()) return Int.MAX_VALUE
    val lower = value.lowercase(Locale.getDefault())
    val tokenIndex = lower
        .replace('.', '_')
        .replace('-', '_')
        .split('_')
        .indexOfFirst { it.startsWith(needle) }
    return when {
        lower == needle -> 0
        lower.startsWith(needle) -> 1
        tokenIndex >= 0 -> 10 + tokenIndex
        lower.contains(needle) -> 100 + lower.indexOf(needle)
        else -> Int.MAX_VALUE
    }
}

@Composable
private fun AppIconChooserSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 4.dp)
    )
}

@Composable
private fun AppIconChooserRow(
    item: AppIconChooserItem,
    applied: Boolean,
    onApply: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PreviewIcon(item.iconPackPackage, item.drawableName, 42.dp)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp)
            ) {
                Text(
                    text = item.label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = item.iconPackLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Button(
                onClick = onApply,
                enabled = !applied,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(
                    text = if (applied) {
                        dynamicStringResource(R.string.app_icons_applied_button)
                    } else {
                        dynamicStringResource(R.string.btn_apply)
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun PartialApplyDialog(
    pack: IconPackInfo,
    iconMap: IconMapSnapshot,
    onDismiss: () -> Unit,
    onApply: (Set<String>) -> Unit
) {
    val context = LocalContext.current
    var loading by remember(pack.packageName) { mutableStateOf(true) }
    var entries by remember(pack.packageName) { mutableStateOf<List<IconPackEntry>>(emptyList()) }
    var query by rememberSaveable(pack.packageName) { mutableStateOf("") }
    var selectedPackages by remember(pack.packageName, iconMap) {
        mutableStateOf(
            iconMap.sources
                .filterValues { it.iconPackPackage == pack.packageName }
                .keys
                .toSet()
        )
    }

    LaunchedEffect(pack.packageName) {
        entries = IconPackManager.loadIconPackEntries(context, pack.packageName)
        loading = false
    }

    val visibleEntries = remember(entries, query) {
        val needle = query.trim().lowercase(Locale.getDefault())
        if (needle.isEmpty()) {
            entries
        } else {
            entries.filter { entry ->
                entry.label.lowercase(Locale.getDefault()).contains(needle) ||
                    entry.appPackageName.lowercase(Locale.US).contains(needle) ||
                    entry.drawableName.lowercase(Locale.US).contains(needle)
            }
        }
    }

    AppIconsDialog(
        title = dynamicStringResource(R.string.app_icons_supported_apps_title),
        subtitle = pack.label,
        onDismiss = onDismiss,
        footer = {
            TextButton(onClick = onDismiss) { Text(dynamicStringResource(R.string.btn_cancel)) }
            Button(onClick = { onApply(selectedPackages) }) {
                Text(dynamicStringResource(R.string.app_icons_apply_selected), maxLines = 1)
            }
        }
    ) {
        AppIconsSearchField(
            query = query,
            hint = dynamicStringResource(R.string.app_icons_dialog_search_hint),
            onQueryChange = { query = it }
        )
        Spacer(Modifier.height(12.dp))
        when {
            loading -> LoadingDialogContent()
            visibleEntries.isEmpty() -> EmptyStateText(dynamicStringResource(R.string.app_icons_no_supported_apps))
            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(visibleEntries, key = { it.appPackageName + it.drawableName }) { entry ->
                    val checked = selectedPackages.contains(entry.appPackageName)
                    SelectableIconEntryRow(
                        entry = entry,
                        checked = checked,
                        onCheckedChange = { nextChecked ->
                            selectedPackages = if (nextChecked) {
                                selectedPackages + entry.appPackageName
                            } else {
                                selectedPackages - entry.appPackageName
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ApplyModeDialog(
    pack: IconPackInfo,
    onDismiss: () -> Unit,
    onApply: (IconApplyMode) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(dynamicStringResource(R.string.app_icons_apply_mode_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = pack.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                ApplyModeButton(
                    text = dynamicStringResource(R.string.app_icons_apply_mode_all_pack),
                    onClick = { onApply(IconApplyMode.ALL_PACK) }
                )
                ApplyModeButton(
                    text = dynamicStringResource(R.string.app_icons_apply_mode_system_only),
                    onClick = { onApply(IconApplyMode.SYSTEM_ONLY) }
                )
                ApplyModeButton(
                    text = dynamicStringResource(R.string.app_icons_apply_mode_user_only),
                    onClick = { onApply(IconApplyMode.USER_ONLY) }
                )
                ApplyModeButton(
                    text = dynamicStringResource(R.string.app_icons_apply_mode_installed_only),
                    onClick = { onApply(IconApplyMode.INSTALLED_ONLY) }
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(dynamicStringResource(R.string.btn_cancel)) } }
    )
}

@Composable
private fun ApplyModeButton(text: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(text, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun IconApplyCompletedDialog(
    result: IconApplyResult,
    onDismiss: () -> Unit,
    onRebootSystem: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(dynamicStringResource(R.string.app_icons_apply_finished_title)) },
        text = {
            Text(
                text = dynamicStringResource(
                    R.string.app_icons_apply_finished_message_format,
                    result.applied,
                    result.removed,
                    result.skipped,
                    result.failed
                )
            )
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text(dynamicStringResource(R.string.btn_ok))
            }
        },
        dismissButton = {
            Button(
                onClick = onRebootSystem,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Text(dynamicStringResource(R.string.app_icons_reboot_system))
            }
        }
    )
}

@Composable
private fun PackPreviewDialog(pack: IconPackInfo, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var loading by remember(pack.packageName) { mutableStateOf(true) }
    var items by remember(pack.packageName) { mutableStateOf<List<IconPackPreviewItem>>(emptyList()) }
    var query by rememberSaveable(pack.packageName) { mutableStateOf("") }

    LaunchedEffect(pack.packageName) {
        items = IconPackManager.loadPreviewItems(context, pack.packageName)
        loading = false
    }

    val visibleItems = remember(items, query) {
        val needle = query.trim().lowercase(Locale.getDefault())
        if (needle.isEmpty()) {
            items
        } else {
            items.filter { item ->
                item.label.lowercase(Locale.getDefault()).contains(needle) ||
                    item.drawableName.lowercase(Locale.US).contains(needle) ||
                    item.appPackageName.orEmpty().lowercase(Locale.US).contains(needle)
            }.sortedWith(
                compareBy<IconPackPreviewItem> { previewSearchRank(it, needle) }
                    .thenBy { it.drawableName.lowercase(Locale.US) }
                    .thenBy { it.label.lowercase(Locale.getDefault()) }
            )
        }
    }

    AppIconsDialog(
        title = dynamicStringResource(R.string.app_icons_pack_contents_title),
        subtitle = pack.label,
        onDismiss = onDismiss,
        footer = { TextButton(onClick = onDismiss) { Text(dynamicStringResource(R.string.btn_close)) } }
    ) {
        AppIconsSearchField(
            query = query,
            hint = dynamicStringResource(R.string.app_icons_dialog_search_hint),
            onQueryChange = { query = it }
        )
        Spacer(Modifier.height(12.dp))
        when {
            loading -> LoadingDialogContent()
            visibleItems.isEmpty() -> EmptyStateText(dynamicStringResource(R.string.app_icons_no_pack_icons))
            else -> IconPreviewGrid(items = visibleItems, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun AppIconDialog(
    app: InstalledIconApp,
    iconMap: IconMapSnapshot,
    onDismiss: () -> Unit,
    onRestore: () -> Unit,
    onApply: (AppIconCandidate) -> Unit,
    onImportCustomIcon: (Uri) -> Unit,
    onSaveShapeOverrides: (AppIconShapeOverrides?) -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val previewSizePx = with(density) { 56.dp.roundToPx() }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onImportCustomIcon(uri)
    }
    var loading by remember(app.packageName) { mutableStateOf(true) }
    var candidates by remember(app.packageName) { mutableStateOf<List<AppIconCandidate>>(emptyList()) }
    var appliedBitmap by remember(app.packageName, iconMap) { mutableStateOf<Bitmap?>(null) }
    var shapeOverrides by remember(app.packageName, iconMap) {
        mutableStateOf(iconMap.shapeOverrides[app.packageName] ?: app.shapeOverrides ?: AppIconShapeOverrides())
    }

    LaunchedEffect(app.packageName, iconMap) {
        candidates = IconPackManager.loadCandidatesForApp(context, app.packageName)
        appliedBitmap = IconPackManager.loadAppliedIconBitmap(context, app.packageName, previewSizePx)
        loading = false
    }

    AppIconsDialog(
        title = app.label,
        subtitle = app.packageName,
        onDismiss = onDismiss,
        footer = { TextButton(onClick = onDismiss) { Text(dynamicStringResource(R.string.btn_close)) } }
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (appliedBitmap != null) {
                    BitmapIcon(bitmap = appliedBitmap, size = 56.dp, fallback = Icons.Rounded.Apps)
                } else {
                    DrawableIcon(drawable = app.icon, size = 56.dp, fallback = Icons.Rounded.Apps)
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 14.dp)
                ) {
                    Text(
                        text = dynamicStringResource(R.string.app_icons_current_icon_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = app.appliedIconPackLabel?.let {
                            dynamicStringResource(R.string.app_icons_applied_from_format, it)
                        } ?: dynamicStringResource(R.string.app_icons_not_applied_to_app),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        if (app.appliedIconFileName != null) {
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = onRestore,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Rounded.Refresh, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(dynamicStringResource(R.string.app_icons_restore_default), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }

        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = { importLauncher.launch(arrayOf("image/*", "application/xml", "text/xml")) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Rounded.Apps, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(dynamicStringResource(R.string.app_icons_import_custom_icon), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }

        Spacer(Modifier.height(12.dp))
        PerAppShapeSettingsCard(
            overrides = shapeOverrides,
            onOverridesChange = { shapeOverrides = it },
            onSave = { onSaveShapeOverrides(shapeOverrides.normalizedOrNull()) },
            onReset = {
                shapeOverrides = AppIconShapeOverrides()
                onSaveShapeOverrides(null)
            }
        )

        Spacer(Modifier.height(12.dp))
        Text(
            text = dynamicStringResource(R.string.app_icons_available_icons_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))

        when {
            loading -> LoadingDialogContent()
            candidates.isEmpty() -> EmptyStateText(dynamicStringResource(R.string.app_icons_no_app_candidates))
            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(candidates, key = { it.iconPackPackage + it.drawableName }) { candidate ->
                    val appliedSource = iconMap.sources[app.packageName]
                    val applied = appliedSource?.iconPackPackage == candidate.iconPackPackage &&
                        appliedSource.drawableName == candidate.drawableName
                    AppIconCandidateRow(
                        candidate = candidate,
                        applied = applied,
                        busy = false,
                        onApply = { onApply(candidate) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PerAppShapeSettingsCard(
    overrides: AppIconShapeOverrides,
    onOverridesChange: (AppIconShapeOverrides) -> Unit,
    onSave: () -> Unit,
    onReset: () -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(true) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ExpandableSectionHeaderContent(
                title = dynamicStringResource(R.string.app_icons_per_app_shape_title),
                subtitle = dynamicStringResource(R.string.app_icons_per_app_shape_summary),
                icon = Icons.Rounded.Apps,
                expanded = expanded,
                onExpandChange = { expanded = it }
            )
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    PerAppShapeAreaEditor(
                        title = dynamicStringResource(R.string.app_icons_per_app_system_area),
                        config = overrides.system ?: IconShapeAreaConfig(),
                        onConfigChange = { onOverridesChange(overrides.copy(system = it)) }
                    )
                    PerAppShapeAreaEditor(
                        title = dynamicStringResource(R.string.app_icons_per_app_notification_area),
                        config = overrides.notification ?: IconShapeAreaConfig(),
                        onConfigChange = { onOverridesChange(overrides.copy(notification = it)) }
                    )
                    PerAppShapeAreaEditor(
                        title = dynamicStringResource(R.string.app_icons_per_app_launcher_area),
                        config = overrides.launcher ?: IconShapeAreaConfig(),
                        onConfigChange = { onOverridesChange(overrides.copy(launcher = it)) }
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = onReset, modifier = Modifier.weight(1f)) {
                            Text(dynamicStringResource(R.string.app_icons_reset_individual_settings), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Button(onClick = onSave, modifier = Modifier.weight(1f)) {
                            Text(dynamicStringResource(R.string.app_icons_save_individual_settings), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PerAppShapeAreaEditor(
    title: String,
    config: IconShapeAreaConfig,
    onConfigChange: (IconShapeAreaConfig) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f))
            .padding(vertical = 6.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        AppIconsSwitchRow(
            title = dynamicStringResource(R.string.app_icons_per_app_stretch_shape),
            summary = dynamicStringResource(R.string.app_icons_per_app_stretch_shape_summary),
            checked = config.stretchShape,
            enabled = true,
            onCheckedChange = { checked -> onConfigChange(config.copy(stretchShape = checked, removeShape = if (checked) false else config.removeShape)) }
        )
        AppIconsSwitchRow(
            title = dynamicStringResource(R.string.app_icons_per_app_remove_shape),
            summary = dynamicStringResource(R.string.app_icons_per_app_remove_shape_summary),
            checked = config.removeShape,
            enabled = true,
            onCheckedChange = { checked -> onConfigChange(config.copy(removeShape = checked, stretchShape = if (checked) false else config.stretchShape)) }
        )
        AppIconsScaleSliderRow(
            title = dynamicStringResource(R.string.app_icons_per_app_shape_scale),
            value = config.scalePercent,
            enabled = config.stretchShape && !config.removeShape,
            onValueChange = { value -> onConfigChange(config.copy(scalePercent = value)) }
        )
    }
}

@Composable
private fun SelectableIconEntryRow(
    entry: IconPackEntry,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (checked) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.52f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PreviewIcon(entry.iconPackPackage, entry.drawableName, 42.dp)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp)
            ) {
                Text(
                    text = entry.label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = entry.appPackageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
private fun AppIconCandidateRow(
    candidate: AppIconCandidate,
    applied: Boolean = false,
    busy: Boolean = false,
    onApply: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PreviewIcon(candidate.iconPackPackage, candidate.drawableName, 42.dp)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp)
            ) {
                Text(
                    text = candidate.iconPackLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = candidate.drawableName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Button(
                onClick = onApply,
                enabled = !applied && !busy,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(
                    text = if (applied) {
                        dynamicStringResource(R.string.app_icons_applied_button)
                    } else {
                        dynamicStringResource(R.string.btn_apply)
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun IconPreviewGrid(items: List<IconPackPreviewItem>, modifier: Modifier = Modifier) {
    val rows = remember(items) { items.chunked(4) }
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(rows) { rowItems ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowItems.forEach { item ->
                    IconPreviewCell(item = item, modifier = Modifier.weight(1f))
                }
                repeat(4 - rowItems.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun IconPreviewCell(item: IconPackPreviewItem, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        PreviewIcon(item.iconPackPackage, item.drawableName, 44.dp)
        Spacer(Modifier.height(6.dp))
        Text(
            text = item.label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun AppIconsDialog(
    title: String,
    subtitle: String?,
    onDismiss: () -> Unit,
    footer: @Composable RowScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (!subtitle.isNullOrBlank()) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, dynamicStringResource(R.string.btn_close))
                    }
                }
                Spacer(Modifier.height(12.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    content()
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                    content = footer
                )
            }
        }
    }
}

@Composable
private fun AppIconsSearchField(
    query: String,
    hint: String,
    onQueryChange: (String) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (query.isEmpty()) {
                    Text(
                        text = hint,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (query.isNotEmpty()) {
                IconButton(
                    onClick = { onQueryChange("") },
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = dynamicStringResource(R.string.btn_close),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun LoadingDialogContent() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 180.dp)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun EmptyStateText(text: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(20.dp)
        )
    }
}

@Composable
private fun PreviewIcon(iconPackPackage: String, drawableName: String, size: Dp) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val sizePx = with(density) { size.roundToPx() }
    var bitmap by remember(iconPackPackage, drawableName, sizePx) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(iconPackPackage, drawableName, sizePx) {
        bitmap = IconPackManager.loadIconBitmap(context, iconPackPackage, drawableName, sizePx)
    }

    BitmapIcon(bitmap = bitmap, size = size, fallback = Icons.Rounded.Apps)
}

@Composable
private fun DrawableIcon(drawable: Drawable?, size: Dp, fallback: ImageVector) {
    val density = LocalDensity.current
    val sizePx = with(density) { size.roundToPx() }
    val bitmap = remember(drawable, sizePx) {
        runCatching {
            drawable?.toBitmap(
                width = sizePx.coerceAtLeast(1),
                height = sizePx.coerceAtLeast(1),
                config = Bitmap.Config.ARGB_8888
            )
        }.getOrNull()
    }
    BitmapIcon(bitmap = bitmap, size = size, fallback = fallback)
}

@Composable
private fun BitmapIcon(bitmap: Bitmap?, size: Dp, fallback: ImageVector) {
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.size(size)
        )
    } else {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = fallback,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(size * 0.52f)
            )
        }
    }
}

@Composable
private fun iconPackStatusText(status: IconPackApplyStatus): String {
    return when (status) {
        IconPackApplyStatus.NOT_APPLIED -> dynamicStringResource(R.string.app_icons_status_not_applied)
        IconPackApplyStatus.APPLIED_FULL -> dynamicStringResource(R.string.app_icons_status_full)
        IconPackApplyStatus.APPLIED_PARTIAL -> dynamicStringResource(R.string.app_icons_status_partial)
    }
}
