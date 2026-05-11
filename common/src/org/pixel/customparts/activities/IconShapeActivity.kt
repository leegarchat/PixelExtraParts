package org.pixel.customparts.activities

import android.content.Context
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.util.PathParser
import android.view.View
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.pixel.customparts.R
import org.pixel.customparts.SettingsKeys
import org.pixel.customparts.dynamicDarkColorScheme
import org.pixel.customparts.dynamicLightColorScheme
import org.pixel.customparts.icons.IconPackManager
import org.pixel.customparts.icons.IconShapeOverlayManager
import org.pixel.customparts.icons.IconShapeOverlayManager.ShapeOption
import org.pixel.customparts.icons.IconShapeOverlayManager.ShapeSource
import org.pixel.customparts.ui.REBOOT_BUBBLE_CONTENT_BOTTOM_PADDING
import org.pixel.customparts.ui.GenericSwitchRow
import org.pixel.customparts.ui.InfoDialog
import org.pixel.customparts.ui.RebootBubble
import org.pixel.customparts.ui.SettingsGroupCard
import org.pixel.customparts.ui.TopBarBlurOverlay
import org.pixel.customparts.ui.recordLayer
import org.pixel.customparts.ui.rememberGraphicsLayerRecordingState
import org.pixel.customparts.utils.SettingsCompat
import org.pixel.customparts.utils.dynamicStringResource
import java.util.Locale

class IconShapeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            val darkTheme = isSystemInDarkTheme()
            val colorScheme = if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            MaterialTheme(colorScheme = colorScheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    IconShapeScreen(onBack = { finish() })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IconShapeScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val blurState = rememberGraphicsLayerRecordingState()
    val listState = rememberLazyListState()
    val isScrolled by remember { derivedStateOf { listState.canScrollBackward } }

    var options by remember { mutableStateOf<List<ShapeOption>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isBusy by remember { mutableStateOf(false) }
    val savedCustomName = remember {
        SettingsCompat.getString(context, SettingsKeys.ICON_SHAPE_CUSTOM_NAME, "PixelParts custom") ?: "PixelParts custom"
    }
    val savedCustomPath = remember {
        SettingsCompat.getString(context, SettingsKeys.ICON_SHAPE_CUSTOM_PATH, "") ?: ""
    }
    var customName by rememberSaveable { mutableStateOf(savedCustomName) }
    var mode by rememberSaveable { mutableIntStateOf(IconShapeOverlayManager.MODE_SUPERELLIPSE) }
    var sides by rememberSaveable { mutableIntStateOf(6) }
    var roundness by rememberSaveable { mutableStateOf(4.5f) }
    var inset by rememberSaveable { mutableStateOf(0f) }
    var wave by rememberSaveable { mutableStateOf(0.12f) }
    var rotation by rememberSaveable { mutableStateOf(0f) }
    var scaleX by rememberSaveable { mutableStateOf(1f) }
    var scaleY by rememberSaveable { mutableStateOf(1f) }
    var offsetX by rememberSaveable { mutableStateOf(0f) }
    var offsetY by rememberSaveable { mutableStateOf(0f) }
    var importedPath by rememberSaveable { mutableStateOf(savedCustomPath) }
    var resultLog by remember { mutableStateOf<List<String>>(emptyList()) }
    var workspaceMatchAllApps by remember {
        mutableStateOf(SettingsCompat.isEnabled(context, SettingsKeys.ICON_SHAPE_WORKSPACE_MATCH_ALL_APPS, false))
    }
    var ignoreCustomSettingsShape by remember {
        mutableStateOf(SettingsCompat.isEnabled(context, SettingsKeys.ICON_SHAPE_IGNORE_CUSTOM_SETTINGS, false))
    }
    var allAppsFollowWorkspace by remember {
        mutableStateOf(SettingsCompat.isEnabled(context, SettingsKeys.ICON_SHAPE_ALL_APPS_FOLLOW_WORKSPACE, false))
    }
    var allAppsThemedIcons by remember {
        mutableStateOf(SettingsCompat.isEnabled(context, SettingsKeys.ICON_SHAPE_ALL_APPS_THEMED_ICONS, false))
    }
    var allAppsSuggestionsThemedIcons by remember {
        mutableStateOf(SettingsCompat.isEnabled(context, SettingsKeys.ICON_SHAPE_ALL_APPS_SUGGESTIONS_THEMED_ICONS, false))
    }
    var searchThemedIcons by remember {
        mutableStateOf(SettingsCompat.isEnabled(context, SettingsKeys.ICON_SHAPE_SEARCH_THEMED_ICONS, false))
    }
    var systemThemedIcons by remember {
        mutableStateOf(SettingsCompat.isEnabled(context, SettingsKeys.ICON_SHAPE_SYSTEM_THEMED_ICONS, false))
    }
    var infoDialogTitle by remember { mutableStateOf<String?>(null) }
    var infoDialogText by remember { mutableStateOf<String?>(null) }
    var infoDialogVideo by remember { mutableStateOf<String?>(null) }

    val params = IconShapeOverlayManager.CustomParams(
        mode = mode,
        sides = sides,
        roundness = roundness,
        inset = inset,
        wave = wave,
        rotation = rotation,
        scaleX = scaleX,
        scaleY = scaleY,
        offsetX = offsetX,
        offsetY = offsetY
    )
    val generatedPath = remember(params) { IconShapeOverlayManager.generatePath(params) }

    suspend fun refreshOptions() {
        isLoading = true
        options = withContext(Dispatchers.IO) { IconShapeOverlayManager.loadOptions(context) }
        isLoading = false
    }

    fun showApplyResult(success: Boolean) {
        Toast.makeText(
            context,
            context.getString(if (success) R.string.icon_shape_apply_success else R.string.icon_shape_apply_failed),
            Toast.LENGTH_SHORT
        ).show()
    }

    fun applyOption(option: ShapeOption) {
        if (isBusy) return
        scope.launch {
            isBusy = true
            val success = withContext(Dispatchers.IO) { IconShapeOverlayManager.applyOption(context, option) }
            showApplyResult(success)
            refreshOptions()
            isBusy = false
        }
    }

    fun applyCustom(pathData: String) {
        if (isBusy || pathData.isBlank()) return
        scope.launch {
            isBusy = true
            resultLog = emptyList()
            val result = withContext(Dispatchers.IO) {
                IconShapeOverlayManager.compileAndApplyCustom(context, customName, pathData)
            }
            resultLog = result.log + listOfNotNull(result.error)
            if (result.success) {
                SettingsCompat.putString(context, SettingsKeys.ICON_SHAPE_CUSTOM_NAME, customName)
                SettingsCompat.putString(context, SettingsKeys.ICON_SHAPE_CUSTOM_PATH, pathData)
            }
            showApplyResult(result.success)
            refreshOptions()
            isBusy = false
        }
    }

    fun deleteOption(option: ShapeOption) {
        if (isBusy) return
        scope.launch {
            isBusy = true
            val success = withContext(Dispatchers.IO) { IconShapeOverlayManager.deleteCustomOverlay(context, option) }
            Toast.makeText(
                context,
                context.getString(if (success) R.string.icon_shape_delete_success else R.string.icon_shape_delete_failed),
                Toast.LENGTH_SHORT
            ).show()
            refreshOptions()
            isBusy = false
        }
    }

    fun setLauncherFlag(key: String, enabled: Boolean) {
        SettingsCompat.putInt(context, key, if (enabled) 1 else 0)
        IconPackManager.requestIconReload(context)
    }

    val showInfoDialog: (String, String, String?) -> Unit = { title, text, video ->
        infoDialogTitle = title
        infoDialogText = text
        infoDialogVideo = video
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                importedPath = withContext(Dispatchers.IO) { IconShapeOverlayManager.readCustomPath(context, uri) }
                if (!IconShapeOverlayManager.isValidPath(importedPath)) {
                    Toast.makeText(context, context.getString(R.string.icon_shape_custom_invalid), Toast.LENGTH_SHORT).show()
                } else {
                    SettingsCompat.putString(context, SettingsKeys.ICON_SHAPE_CUSTOM_PATH, importedPath)
                    Toast.makeText(context, context.getString(R.string.icon_shape_import_success), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    LaunchedEffect(Unit) { refreshOptions() }

    if (infoDialogTitle != null && infoDialogText != null) {
        InfoDialog(
            title = infoDialogTitle.orEmpty(),
            text = infoDialogText.orEmpty(),
            videoResName = infoDialogVideo,
            onDismiss = {
                infoDialogTitle = null
                infoDialogText = null
                infoDialogVideo = null
            }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets.navigationBars,
        floatingActionButton = { RebootBubble() },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        dynamicStringResource(R.string.icon_shape_title),
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
                    IconButton(onClick = { scope.launch { refreshOptions() } }, enabled = !isBusy) {
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
                item {
                    SettingsGroupCard(title = dynamicStringResource(R.string.icon_shape_custom_section)) {
                        CustomShapeBuilder(
                            mode = mode,
                            sides = sides,
                            roundness = roundness,
                            inset = inset,
                            wave = wave,
                            rotation = rotation,
                            scaleX = scaleX,
                            scaleY = scaleY,
                            offsetX = offsetX,
                            offsetY = offsetY,
                            pathData = generatedPath,
                            customName = customName,
                            importedPath = importedPath,
                            isBusy = isBusy,
                            resultLog = resultLog,
                            onModeChange = { mode = it },
                            onSidesChange = { sides = it },
                            onRoundnessChange = { roundness = it },
                            onInsetChange = { inset = it },
                            onWaveChange = { wave = it },
                            onRotationChange = { rotation = it },
                            onScaleXChange = { scaleX = it },
                            onScaleYChange = { scaleY = it },
                            onOffsetXChange = { offsetX = it },
                            onOffsetYChange = { offsetY = it },
                            onNameChange = { customName = it },
                            onApplyGenerated = { applyCustom(generatedPath) },
                            onPickFile = { importLauncher.launch("*/*") },
                            onApplyImported = { applyCustom(importedPath) }
                        )
                    }
                }

                item {
                    SettingsGroupCard(title = dynamicStringResource(R.string.icon_shape_launcher_section)) {
                        GenericSwitchRow(
                            title = dynamicStringResource(R.string.icon_shape_workspace_match_all_apps_title),
                            checked = workspaceMatchAllApps,
                            onCheckedChange = { checked ->
                                workspaceMatchAllApps = checked
                                setLauncherFlag(SettingsKeys.ICON_SHAPE_WORKSPACE_MATCH_ALL_APPS, checked)
                                if (checked && allAppsFollowWorkspace) {
                                    allAppsFollowWorkspace = false
                                    setLauncherFlag(SettingsKeys.ICON_SHAPE_ALL_APPS_FOLLOW_WORKSPACE, false)
                                }
                            },
                            summary = dynamicStringResource(R.string.icon_shape_workspace_match_all_apps_summary),
                            infoText = dynamicStringResource(R.string.icon_shape_workspace_match_all_apps_info),
                            enabled = !isBusy,
                            onInfoClick = showInfoDialog
                        )
                        GenericSwitchRow(
                            title = dynamicStringResource(R.string.icon_shape_ignore_custom_settings_title),
                            checked = ignoreCustomSettingsShape,
                            onCheckedChange = { checked ->
                                ignoreCustomSettingsShape = checked
                                setLauncherFlag(SettingsKeys.ICON_SHAPE_IGNORE_CUSTOM_SETTINGS, checked)
                            },
                            summary = dynamicStringResource(R.string.icon_shape_ignore_custom_settings_summary),
                            infoText = dynamicStringResource(R.string.icon_shape_ignore_custom_settings_info),
                            enabled = !isBusy,
                            onInfoClick = showInfoDialog
                        )
                        GenericSwitchRow(
                            title = dynamicStringResource(R.string.icon_shape_all_apps_follow_workspace_title),
                            checked = allAppsFollowWorkspace,
                            onCheckedChange = { checked ->
                                allAppsFollowWorkspace = checked
                                setLauncherFlag(SettingsKeys.ICON_SHAPE_ALL_APPS_FOLLOW_WORKSPACE, checked)
                                if (checked && workspaceMatchAllApps) {
                                    workspaceMatchAllApps = false
                                    setLauncherFlag(SettingsKeys.ICON_SHAPE_WORKSPACE_MATCH_ALL_APPS, false)
                                }
                            },
                            summary = dynamicStringResource(R.string.icon_shape_all_apps_follow_workspace_summary),
                            infoText = dynamicStringResource(R.string.icon_shape_all_apps_follow_workspace_info),
                            enabled = !isBusy,
                            onInfoClick = showInfoDialog
                        )
                        GenericSwitchRow(
                            title = dynamicStringResource(R.string.icon_shape_all_apps_themed_icons_title),
                            checked = allAppsThemedIcons,
                            onCheckedChange = { checked ->
                                allAppsThemedIcons = checked
                                setLauncherFlag(SettingsKeys.ICON_SHAPE_ALL_APPS_THEMED_ICONS, checked)
                            },
                            summary = dynamicStringResource(R.string.icon_shape_all_apps_themed_icons_summary),
                            infoText = dynamicStringResource(R.string.icon_shape_all_apps_themed_icons_info),
                            enabled = !isBusy,
                            onInfoClick = showInfoDialog
                        )
                        GenericSwitchRow(
                            title = dynamicStringResource(R.string.icon_shape_all_apps_suggestions_themed_icons_title),
                            checked = allAppsSuggestionsThemedIcons,
                            onCheckedChange = { checked ->
                                allAppsSuggestionsThemedIcons = checked
                                setLauncherFlag(SettingsKeys.ICON_SHAPE_ALL_APPS_SUGGESTIONS_THEMED_ICONS, checked)
                            },
                            summary = dynamicStringResource(R.string.icon_shape_all_apps_suggestions_themed_icons_summary),
                            infoText = dynamicStringResource(R.string.icon_shape_all_apps_suggestions_themed_icons_info),
                            enabled = !isBusy,
                            onInfoClick = showInfoDialog
                        )
                        GenericSwitchRow(
                            title = dynamicStringResource(R.string.icon_shape_search_themed_icons_title),
                            checked = searchThemedIcons,
                            onCheckedChange = { checked ->
                                searchThemedIcons = checked
                                setLauncherFlag(SettingsKeys.ICON_SHAPE_SEARCH_THEMED_ICONS, checked)
                            },
                            summary = dynamicStringResource(R.string.icon_shape_search_themed_icons_summary),
                            infoText = dynamicStringResource(R.string.icon_shape_search_themed_icons_info),
                            enabled = !isBusy,
                            onInfoClick = showInfoDialog
                        )
                        GenericSwitchRow(
                            title = dynamicStringResource(R.string.icon_shape_system_themed_icons_title),
                            checked = systemThemedIcons,
                            onCheckedChange = { checked ->
                                systemThemedIcons = checked
                                setLauncherFlag(SettingsKeys.ICON_SHAPE_SYSTEM_THEMED_ICONS, checked)
                            },
                            summary = dynamicStringResource(R.string.icon_shape_system_themed_icons_summary),
                            infoText = dynamicStringResource(R.string.icon_shape_system_themed_icons_info),
                            enabled = !isBusy,
                            onInfoClick = showInfoDialog
                        )
                    }
                }

                item {
                    SettingsGroupCard(title = dynamicStringResource(R.string.icon_shape_presets_section)) {
                        if (isLoading) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(120.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                    }
                }

                if (!isLoading) {
                    items(options, key = { it.id }) { option ->
                        ShapeOptionCard(
                            option = option,
                            isBusy = isBusy,
                            onApply = { applyOption(option) },
                            onDelete = if (option.source == ShapeSource.CUSTOM_OVERLAY) {
                                { deleteOption(option) }
                            } else null
                        )
                    }
                }
            }

            TopBarBlurOverlay(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter),
                blurState = blurState,
                topBarHeight = innerPadding.calculateTopPadding(),
                isScrolled = isScrolled
            )
        }
    }
}

@Composable
private fun CustomShapeBuilder(
    mode: Int,
    sides: Int,
    roundness: Float,
    inset: Float,
    wave: Float,
    rotation: Float,
    scaleX: Float,
    scaleY: Float,
    offsetX: Float,
    offsetY: Float,
    pathData: String,
    customName: String,
    importedPath: String,
    isBusy: Boolean,
    resultLog: List<String>,
    onModeChange: (Int) -> Unit,
    onSidesChange: (Int) -> Unit,
    onRoundnessChange: (Float) -> Unit,
    onInsetChange: (Float) -> Unit,
    onWaveChange: (Float) -> Unit,
    onRotationChange: (Float) -> Unit,
    onScaleXChange: (Float) -> Unit,
    onScaleYChange: (Float) -> Unit,
    onOffsetXChange: (Float) -> Unit,
    onOffsetYChange: (Float) -> Unit,
    onNameChange: (String) -> Unit,
    onApplyGenerated: () -> Unit,
    onPickFile: () -> Unit,
    onApplyImported: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconShapePreview(pathData = pathData, modifier = Modifier.size(88.dp), active = true)
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = customName,
                    onValueChange = onNameChange,
                    singleLine = true,
                    label = { Text(dynamicStringResource(R.string.icon_shape_custom_name)) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth()
                )
                Button(onClick = onApplyGenerated, enabled = !isBusy) {
                    Text(dynamicStringResource(R.string.icon_shape_custom_apply))
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ShapeModeChip(
                selected = mode == IconShapeOverlayManager.MODE_SUPERELLIPSE,
                label = dynamicStringResource(R.string.icon_shape_mode_superellipse),
                onClick = { onModeChange(IconShapeOverlayManager.MODE_SUPERELLIPSE) }
            )
            ShapeModeChip(
                selected = mode == IconShapeOverlayManager.MODE_POLYGON,
                label = dynamicStringResource(R.string.icon_shape_mode_polygon),
                onClick = { onModeChange(IconShapeOverlayManager.MODE_POLYGON) }
            )
            ShapeModeChip(
                selected = mode == IconShapeOverlayManager.MODE_FLOWER,
                label = dynamicStringResource(R.string.icon_shape_mode_flower),
                onClick = { onModeChange(IconShapeOverlayManager.MODE_FLOWER) }
            )
        }

        if (mode == IconShapeOverlayManager.MODE_SUPERELLIPSE) {
            ShapeSlider(
                title = dynamicStringResource(R.string.icon_shape_roundness),
                value = roundness,
                valueRange = 1.5f..8f,
                onValueChange = onRoundnessChange
            )
        } else {
            ShapeSlider(
                title = dynamicStringResource(R.string.icon_shape_sides),
                value = sides.toFloat(),
                valueRange = 3f..12f,
                steps = 8,
                onValueChange = { onSidesChange(it.toInt().coerceIn(3, 12)) }
            )
        }
        ShapeSlider(
            title = dynamicStringResource(R.string.icon_shape_inset),
            value = inset,
            valueRange = 0f..24f,
            onValueChange = onInsetChange
        )
        if (mode == IconShapeOverlayManager.MODE_FLOWER) {
            ShapeSlider(
                title = dynamicStringResource(R.string.icon_shape_wave),
                value = wave,
                valueRange = 0f..0.35f,
                onValueChange = onWaveChange
            )
        }
        ShapeSlider(
            title = dynamicStringResource(R.string.icon_shape_rotation),
            value = rotation,
            valueRange = -180f..180f,
            onValueChange = onRotationChange
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ShapeSlider(
                title = dynamicStringResource(R.string.icon_shape_scale_x),
                value = scaleX,
                valueRange = 0.5f..1.5f,
                onValueChange = onScaleXChange,
                modifier = Modifier.weight(1f)
            )
            ShapeSlider(
                title = dynamicStringResource(R.string.icon_shape_scale_y),
                value = scaleY,
                valueRange = 0.5f..1.5f,
                onValueChange = onScaleYChange,
                modifier = Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ShapeSlider(
                title = dynamicStringResource(R.string.icon_shape_offset_x),
                value = offsetX,
                valueRange = -25f..25f,
                onValueChange = onOffsetXChange,
                modifier = Modifier.weight(1f)
            )
            ShapeSlider(
                title = dynamicStringResource(R.string.icon_shape_offset_y),
                value = offsetY,
                valueRange = -25f..25f,
                onValueChange = onOffsetYChange,
                modifier = Modifier.weight(1f)
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onPickFile, enabled = !isBusy) {
                Icon(Icons.Rounded.FileOpen, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(dynamicStringResource(R.string.icon_shape_import))
            }
            Spacer(Modifier.width(8.dp))
            TextButton(
                onClick = onApplyImported,
                enabled = !isBusy && IconShapeOverlayManager.isValidPath(importedPath)
            ) {
                Text(dynamicStringResource(R.string.icon_shape_import_apply))
            }
        }
        AnimatedVisibility(
            visible = importedPath.isNotBlank() && IconShapeOverlayManager.isValidPath(importedPath),
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            IconShapePreview(pathData = importedPath, modifier = Modifier.size(72.dp), active = false)
        }
        AnimatedVisibility(visible = resultLog.isNotEmpty()) {
            Text(
                text = resultLog.takeLast(4).joinToString("\n"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShapeModeChip(selected: Boolean, label: String, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label, maxLines = 1) })
}

@Composable
private fun ShapeSlider(
    title: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    modifier: Modifier = Modifier,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = String.format(Locale.US, "%.2f", value),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Slider(value = value, onValueChange = onValueChange, valueRange = valueRange, steps = steps)
    }
}

@Composable
private fun ShapeOptionCard(
    option: ShapeOption,
    isBusy: Boolean,
    onApply: () -> Unit,
    onDelete: (() -> Unit)? = null
) {
    var showConfirmDelete by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = !isBusy && !option.active) { onApply() }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconShapePreview(pathData = option.pathData, modifier = Modifier.size(56.dp), active = option.active)
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = option.label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = shapeSourceLabel(option.source),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (option.active) {
                    Icon(Icons.Rounded.Check, dynamicStringResource(R.string.icon_shape_active))
                } else {
                    TextButton(onClick = onApply, enabled = !isBusy) {
                        Text(dynamicStringResource(R.string.icon_shape_apply))
                    }
                }
                if (onDelete != null) {
                    if (!showConfirmDelete) {
                        IconButton(onClick = { showConfirmDelete = true }, enabled = !isBusy) {
                            Icon(
                                Icons.Rounded.Delete,
                                dynamicStringResource(R.string.icon_shape_delete),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    } else {
                        IconButton(onClick = {
                            showConfirmDelete = false
                            onDelete()
                        }, enabled = !isBusy) {
                            Icon(
                                Icons.Rounded.Check,
                                dynamicStringResource(R.string.icon_shape_delete_confirm),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                        IconButton(onClick = { showConfirmDelete = false }, enabled = !isBusy) {
                            Icon(
                                Icons.Rounded.Close,
                                dynamicStringResource(R.string.anim_dialog_cancel),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun shapeSourceLabel(source: ShapeSource): String {
    return when (source) {
        ShapeSource.DEFAULT -> dynamicStringResource(R.string.icon_shape_source_default)
        ShapeSource.INSTALLED_OVERLAY -> dynamicStringResource(R.string.icon_shape_source_installed)
        ShapeSource.BUILTIN_PRESET -> dynamicStringResource(R.string.icon_shape_source_builtin)
        ShapeSource.CUSTOM_OVERLAY -> dynamicStringResource(R.string.icon_shape_source_custom)
    }
}

@Composable
private fun IconShapePreview(pathData: String, modifier: Modifier = Modifier, active: Boolean) {
    val fillColor = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer
    val strokeColor = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.outline
    AndroidView(
        modifier = modifier,
        factory = { IconShapePreviewView(it) },
        update = { view ->
            view.setShape(pathData, fillColor.toArgb(), strokeColor.toArgb())
        }
    )
}

private class IconShapePreviewView(context: Context) : View(context) {
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * resources.displayMetrics.density
    }
    private val bounds = RectF()
    private var pathData: String = ""

    fun setShape(pathData: String, fillColor: Int, strokeColor: Int) {
        this.pathData = pathData
        fillPaint.color = fillColor
        strokePaint.color = strokeColor
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val size = minOf(width, height).toFloat().coerceAtLeast(1f)
        val left = (width - size) / 2f
        val top = (height - size) / 2f
        bounds.set(left, top, left + size, top + size)
        canvas.drawRoundRect(bounds, size * 0.18f, size * 0.18f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = fillPaint.color and 0x33ffffff
        })
        try {
            val path = PathParser.createPathFromPathData(pathData)
            val matrix = Matrix().apply {
                setScale(size / 100f, size / 100f)
                postTranslate(left, top)
            }
            path.transform(matrix)
            canvas.drawPath(path, fillPaint)
            canvas.drawPath(path, strokePaint)
        } catch (_: Throwable) {
            canvas.drawOval(bounds, fillPaint)
            canvas.drawOval(bounds, strokePaint)
        }
    }
}