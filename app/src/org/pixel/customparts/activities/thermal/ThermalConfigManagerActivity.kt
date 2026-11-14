package org.pixel.customparts.activities

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Add
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
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
import org.pixel.customparts.dynamicDarkColorScheme
import org.pixel.customparts.dynamicLightColorScheme
import org.pixel.customparts.services.ThermalManagerTileService
import org.pixel.customparts.ui.SliderMorphRoot
import org.pixel.customparts.ui.TopBarBlurOverlay
import org.pixel.customparts.ui.TopStatusVeil
import org.pixel.customparts.ui.recordLayer
import org.pixel.customparts.ui.rememberGraphicsLayerRecordingState
import org.pixel.customparts.ui.rememberSliderMorphHostState
import org.pixel.customparts.utils.AutoHbmController
import org.pixel.customparts.utils.ThermalProfileController
import org.pixel.customparts.utils.ThermalProfileMap
import org.pixel.customparts.utils.TileUtils
import java.util.Locale
import kotlin.math.roundToInt

private const val VENDOR_THERMAL_CONFIG = "/vendor/etc/thermal_info_config.json"
private const val EXTRA_OPEN_CREATE_PROFILE = "org.pixel.customparts.extra.OPEN_THERMAL_PROFILE_CREATOR"

private const val TAB_THERMAL_OVERVIEW = 0
private const val TAB_THERMAL_APPS = 1
private const val TAB_THERMAL_STUDIO = 2

class ThermalConfigManagerActivity : ComponentActivity() {
    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        setContent {
            ThermalUnifiedSurface(
                initialTab = TAB_THERMAL_OVERVIEW,
                openCreateProfile = false,
                onBack = { finish() }
            )
        }
    }
}

class ThermalConfigEditorActivity : ComponentActivity() {
    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        val openCreateProfile = intent.getBooleanExtra(EXTRA_OPEN_CREATE_PROFILE, false)
        setContent {
            ThermalUnifiedSurface(
                initialTab = TAB_THERMAL_STUDIO,
                openCreateProfile = openCreateProfile,
                onBack = { finish() }
            )
        }
    }
}

@Composable
private fun ThermalUnifiedSurface(
    initialTab: Int,
    openCreateProfile: Boolean,
    onBack: () -> Unit
) {
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
            ThermalUnifiedStudioScreen(
                initialTab = initialTab,
                openCreateProfile = openCreateProfile,
                onBack = onBack
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThermalUnifiedStudioScreen(
    initialTab: Int,
    openCreateProfile: Boolean,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val store = remember { ThermalConfigStore(context) }

    // 3-Pass Non-Cyclic Blur Stack
    val scrollBlur = rememberGraphicsLayerRecordingState()
    val cardModalBlur = rememberGraphicsLayerRecordingState()
    val sliderModalBlur = rememberGraphicsLayerRecordingState()
    val sliderMorphHost = rememberSliderMorphHostState()

    val pagerState = rememberStudioPagerState(3)
    val overviewListState = rememberLazyListState()
    val appsListState = rememberLazyListState()
    val studioListState = rememberLazyListState()

    var savedConfigs by remember { mutableStateOf(emptyList<SavedThermalConfig>()) }
    var profileMap by remember { mutableStateOf(ThermalProfileMap()) }
    var tileProfileQueue by remember { mutableStateOf(ThermalProfileController.readTileProfileQueue(context)) }
    var managedApps by remember { mutableStateOf(emptyList<ThermalManagedApp>()) }
    var appsLoading by remember { mutableStateOf(true) }
    var isSilentRefreshing by remember { mutableStateOf(false) }

    // Live SoC Telemetry
    val socModel = remember { AutoHbmController.getSocModel() }
    var socTempC by remember { mutableStateOf(AutoHbmController.readSocTemperatureC()) }

    // Tab 2 (Apps) Filter & Search
    var showSystemApps by rememberSaveable { mutableStateOf(true) }
    var appSearchQuery by rememberSaveable { mutableStateOf("") }

    // Tab 3 (Studio / Editor) State
    val defaultEditorSource = stringResource(R.string.thermal_manager_source_vendor)
    val defaultProfileName = stringResource(R.string.thermal_manager_default_profile_name)
    var userConfigsExpanded by rememberSaveable { mutableStateOf(true) }
    var systemConfigsExpanded by rememberSaveable { mutableStateOf(false) }
    var isEditorLoading by remember { mutableStateOf(false) }
    var editorSource by remember { mutableStateOf(defaultEditorSource) }
    var profileName by remember(defaultProfileName) { mutableStateOf(defaultProfileName) }
    var fileNameInput by remember { mutableStateOf("") }
    var treeSearchQuery by rememberSaveable { mutableStateOf("") }
    var baseConfigText by remember { mutableStateOf<String?>(null) }
    var editableParams by remember { mutableStateOf(emptyList<ThermalConfigParam>()) }
    var expandedTreeNodes by rememberSaveable { mutableStateOf(emptySet<String>()) }
    var selectedEditorTabName by rememberSaveable { mutableStateOf(ThermalEditorTab.ALL.name) }
    val selectedEditorTab = runCatching {
        ThermalEditorTab.valueOf(selectedEditorTabName)
    }.getOrDefault(ThermalEditorTab.ALL)
    var pendingExportText by remember { mutableStateOf<String?>(null) }

    // Level-1 Flight Pseudo-Modals (Zero Dialogs!)
    var pickerFlight by remember { mutableStateOf<ThermalPickerFlightRequest?>(null) }
    var pickerFlightT by remember { mutableFloatStateOf(0f) }

    var paramFlight by remember { mutableStateOf<ThermalParamFlightRequest?>(null) }
    var paramFlightT by remember { mutableFloatStateOf(0f) }

    var level1CloseSignal by remember { mutableIntStateOf(0) }

    // Collapsible Frosted Header state
    val statusBarTopDp = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottomDp = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val topBarRowHeightDp = 56.dp
    val statusPlusTopBarPx = with(density) { (statusBarTopDp + topBarRowHeightDp).toPx() }

    var collapsibleMaxHeightPx by remember { mutableFloatStateOf(with(density) { 64.dp.toPx() }) }
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

    fun showToast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    suspend fun refreshThermalDataSilent(reloadApps: Boolean = false) {
        withContext(Dispatchers.IO) { ThermalProfileController.seedVendorConfigs() }
        savedConfigs = withContext(Dispatchers.IO) { store.listSavedConfigs() }
        profileMap = withContext(Dispatchers.IO) { ThermalProfileController.readProfileMap() }
        tileProfileQueue = withContext(Dispatchers.IO) { ThermalProfileController.readTileProfileQueue(context) }
        if (reloadApps || managedApps.isEmpty()) {
            managedApps = withContext(Dispatchers.IO) { loadThermalManagedApps(context) }
            appsLoading = false
        }
    }

    fun triggerManualSilentRefresh() {
        if (isSilentRefreshing) return
        revealHeaderSmoothly()
        scope.launch {
            when (pagerState.anchor) {
                TAB_THERMAL_OVERVIEW -> if (overviewListState.firstVisibleItemIndex > 0 || overviewListState.firstVisibleItemScrollOffset > 0) {
                    overviewListState.animateScrollToItem(0)
                }
                TAB_THERMAL_APPS -> if (appsListState.firstVisibleItemIndex > 0 || appsListState.firstVisibleItemScrollOffset > 0) {
                    appsListState.animateScrollToItem(0)
                }
                else -> if (studioListState.firstVisibleItemIndex > 0 || studioListState.firstVisibleItemScrollOffset > 0) {
                    studioListState.animateScrollToItem(0)
                }
            }
        }
        scope.launch {
            isSilentRefreshing = true
            refreshThermalDataSilent(reloadApps = true)
            isSilentRefreshing = false
        }
    }

    fun openEditorFromText(
        sourceName: String,
        rawJson: String,
        suggestedProfileName: String,
        suggestedFileName: String? = null
    ) {
        try {
            val root = parseThermalJsonObject(context, rawJson)
            val params = buildEditableParams(context, root)
            baseConfigText = root.toString()
            editableParams = params
            profileName = suggestedProfileName.removeSuffix(".json").takeIf { it.isNotBlank() } ?: defaultProfileName
            fileNameInput = suggestedFileName?.removeSuffix(".json").orEmpty()
            editorSource = sourceName
            treeSearchQuery = ""
            expandedTreeNodes = defaultExpandedTreeNodeIds(params)
            selectedEditorTabName = ThermalEditorTab.ALL.name
            scope.launch {
                if (pagerState.anchor != TAB_THERMAL_STUDIO) {
                    pagerState.animateScrollToPage(TAB_THERMAL_STUDIO, isDirectTap = true)
                }
                delay(120)
                studioListState.animateScrollToItem(2)
            }
        } catch (throwable: Throwable) {
            showToast(
                context.getString(
                    R.string.thermal_manager_error_bad_json,
                    throwable.message ?: context.getString(R.string.thermal_manager_json_label)
                )
            )
        }
    }

    fun openBaseEditor() {
        scope.launch {
            isEditorLoading = true
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    Triple(
                        context.getString(R.string.thermal_manager_source_vendor),
                        store.readVendorConfig(),
                        context.getString(R.string.thermal_manager_default_profile_name)
                    )
                }
            }
            isEditorLoading = false
            result.onSuccess { (source, json, suggestedName) ->
                openEditorFromText(source, json, suggestedName)
            }.onFailure { throwable ->
                showToast(
                    context.getString(
                        R.string.thermal_manager_error_vendor_missing,
                        throwable.message ?: VENDOR_THERMAL_CONFIG
                    )
                )
            }
        }
    }

    fun openSavedEditor(config: SavedThermalConfig) {
        if (config.locked) {
            showToast(context.getString(R.string.thermal_manager_vendor_read_only))
            return
        }
        scope.launch {
            isEditorLoading = true
            val result = withContext(Dispatchers.IO) { runCatching { config.file.readText() } }
            isEditorLoading = false
            result.onSuccess { json ->
                openEditorFromText(config.displayName, json, config.displayName, config.name)
            }.onFailure { throwable ->
                showToast(
                    context.getString(
                        R.string.thermal_manager_error_read_file,
                        throwable.message ?: config.name
                    )
                )
            }
        }
    }

    fun openSavedClone(config: SavedThermalConfig) {
        scope.launch {
            isEditorLoading = true
            val result = withContext(Dispatchers.IO) { runCatching { config.file.readText() } }
            isEditorLoading = false
            result.onSuccess { json ->
                openEditorFromText(
                    context.getString(R.string.thermal_manager_source_copy, config.displayName),
                    json,
                    config.displayName + "_copy",
                    config.name.removeSuffix(".json") + "_copy"
                )
            }.onFailure { throwable ->
                showToast(
                    context.getString(
                        R.string.thermal_manager_error_read_file,
                        throwable.message ?: config.name
                    )
                )
            }
        }
    }

    fun saveEditedConfig(applyAfterSave: Boolean) {
        val sourceText = baseConfigText ?: return
        val displayName = profileName.trim().takeIf { it.isNotBlank() } ?: defaultProfileName
        val safeFileName = resolveConfigFileName(displayName, fileNameInput)
        if (safeFileName == null) {
            showToast(context.getString(R.string.thermal_manager_name_invalid))
            return
        }

        scope.launch {
            isEditorLoading = true
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val root = parseThermalJsonObject(context, sourceText)
                    editableParams.forEach { param ->
                        setJsonValue(root, param.path, parseEditedValue(context, param))
                    }
                    store.saveConfig(safeFileName, root.toString(4), displayName)
                }
            }
            isEditorLoading = false
            result.onSuccess { file ->
                refreshThermalDataSilent(reloadApps = false)
                showToast(context.getString(R.string.thermal_manager_saved, file.name))
                if (applyAfterSave) {
                    if (ThermalProfileController.updateGlobalConfig(context, file.name)) {
                        profileMap = ThermalProfileController.readProfileMap()
                        showToast(context.getString(R.string.thermal_manager_applied, file.name))
                    } else {
                        showToast(context.getString(R.string.thermal_manager_apply_failed))
                    }
                }
            }.onFailure { throwable ->
                showToast(
                    context.getString(
                        R.string.thermal_manager_error_save,
                        throwable.message ?: safeFileName
                    )
                )
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        val text = pendingExportText
        pendingExportText = null
        if (uri != null && text != null) {
            scope.launch {
                val result = withContext(Dispatchers.IO) { runCatching { store.writeUri(uri, text) } }
                result.onSuccess { showToast(context.getString(R.string.thermal_manager_exported)) }
                    .onFailure {
                        showToast(
                            context.getString(
                                R.string.thermal_manager_export_failed,
                                it.message ?: context.getString(R.string.thermal_manager_export_label)
                            )
                        )
                    }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            isEditorLoading = true
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val importedText = store.readUri(uri)
                    val imported = parseThermalJsonObject(context, importedText)
                    val reference = parseThermalJsonObject(context, store.readVendorConfig())
                    val validation = validateImportedConfig(context, reference, imported)
                    if (validation.isNotEmpty()) {
                        error(validation.take(8).joinToString("\n"))
                    }
                    val displayName = store.displayName(uri)
                        ?: context.getString(R.string.thermal_manager_imported_profile_file_name)
                    val profileDisplayName = displayName.removeSuffix(".json")
                    val safeName = resolveConfigFileName(profileDisplayName, "")
                        ?: error(context.getString(R.string.thermal_manager_name_invalid))
                    val formattedJson = imported.toString(4)
                    val file = store.saveConfig(safeName, formattedJson, profileDisplayName)
                    ImportedThermalConfig(file, profileDisplayName, formattedJson)
                }
            }
            isEditorLoading = false
            result.onSuccess { imported ->
                refreshThermalDataSilent(reloadApps = false)
                openEditorFromText(
                    context.getString(R.string.thermal_manager_source_copy, imported.displayName),
                    imported.json,
                    imported.displayName,
                    imported.file.name
                )
                showToast(context.getString(R.string.thermal_manager_imported, imported.file.name))
            }.onFailure { throwable ->
                showToast(
                    context.getString(
                        R.string.thermal_manager_import_failed,
                        throwable.message ?: context.getString(R.string.thermal_manager_import_label)
                    )
                )
            }
        }
    }

    LaunchedEffect(Unit) {
        if (initialTab != 0) {
            pagerState.anchor = initialTab.coerceIn(0, 2)
        }
        refreshThermalDataSilent(reloadApps = true)
        if (openCreateProfile) {
            openBaseEditor()
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            socTempC = withContext(Dispatchers.IO) { AutoHbmController.readSocTemperatureC() }
            delay(1500L)
        }
    }

    val globalChoices = remember(savedConfigs) {
        ThermalProfileController.listConfigChoices(includeFollowGlobal = false)
    }
    val appChoices = remember(savedConfigs) {
        ThermalProfileController.listConfigChoices(includeFollowGlobal = true)
    }
    val choicesById = remember(globalChoices, appChoices) {
        (globalChoices + appChoices).associateBy { it.id }
    }

    val assignedPackagesCount = remember(profileMap) {
        profileMap.packageConfigs.count { it.value.isNotBlank() }
    }

    val visibleApps = remember(managedApps, showSystemApps, appSearchQuery, profileMap) {
        val query = appSearchQuery.trim().lowercase(Locale.getDefault())
        val assignedPackages = profileMap.packageConfigs
            .filterValues { it.isNotBlank() }
            .keys
        val filteredApps = if (showSystemApps) managedApps else managedApps.filterNot { it.isSystem }
        val matchedApps = if (query.isBlank()) {
            filteredApps
        } else {
            filteredApps.filter { app ->
                app.label.lowercase(Locale.getDefault()).contains(query) ||
                    app.packageName.lowercase(Locale.US).contains(query)
            }
        }
        matchedApps.sortedWith(
            compareByDescending<ThermalManagedApp> { it.packageName in assignedPackages }
                .thenBy { it.label.lowercase(Locale.getDefault()) }
                .thenBy { it.packageName }
        )
    }

    val userConfigs = remember(savedConfigs) { savedConfigs.filterNot { it.locked } }
    val systemConfigs = remember(savedConfigs) { savedConfigs.filter { it.locked } }

    val tabParams = remember(editableParams, selectedEditorTab) {
        when (selectedEditorTab) {
            ThermalEditorTab.SOC -> editableParams.filter { it.matchesThermalTargets(THERMAL_EDITOR_SOC_TARGETS) }
            ThermalEditorTab.BATTERY -> editableParams.filter { it.matchesThermalTargets(THERMAL_EDITOR_BATTERY_TARGETS) }
            ThermalEditorTab.ALL -> editableParams
        }
    }
    val normalizedTreeQuery = treeSearchQuery.trim().lowercase(Locale.ROOT)
    val filteredTreeParams = remember(tabParams, normalizedTreeQuery) {
        if (normalizedTreeQuery.isBlank()) {
            tabParams
        } else {
            val tokens = normalizedTreeQuery.split(Regex("\\s+")).filter { it.isNotBlank() }
            tabParams.filter { param -> tokens.all { token -> param.searchText.contains(token) } }
        }
    }
    val treeBranches = remember(context, filteredTreeParams) {
        buildThermalParamTree(context, filteredTreeParams)
    }

    val anyLevel1ModalOpen = pickerFlight != null || paramFlight != null
    val nestedMorphOpen = sliderMorphHost.request != null
    val anyModalOpen = anyLevel1ModalOpen || nestedMorphOpen
    val activeModalProgress = maxOf(pickerFlightT, paramFlightT)

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (pickerFlight != null || paramFlight != null || sliderMorphHost.request != null) {
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
                if (pickerFlight != null || paramFlight != null || sliderMorphHost.request != null) {
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

    val visibleCollapsiblePx = (collapsibleMaxHeightPx + collapseOffsetPx).coerceAtLeast(0f)
    val effectiveHeaderHeightPx = statusPlusTopBarPx + visibleCollapsiblePx
    val effectiveHeaderHeightDp = with(density) { effectiveHeaderHeightPx.toDp() }
    val listTopPaddingDp = with(density) { (statusPlusTopBarPx + collapsibleMaxHeightPx).toDp() }

    val activeGlobalChoice = remember(profileMap.globalConfig, choicesById) {
        choiceForConfig(
            profileMap.globalConfig.ifBlank { ThermalProfileController.STOCK_CONFIG_ID },
            choicesById,
            includeFollowGlobal = false
        )
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
                        userScrollEnabled = !anyModalOpen,
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
                            TAB_THERMAL_OVERVIEW -> {
                                LazyColumn(
                                    state = overviewListState,
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = pagePadding,
                                    verticalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    item(key = "thermal_live_telemetry") {
                                        ThermalLiveTelemetryCard(
                                            socModel = socModel,
                                            temperatureC = socTempC,
                                            activeGlobalChoice = activeGlobalChoice,
                                            assignedAppsCount = assignedPackagesCount
                                        )
                                    }

                                    item(key = "thermal_global_profile") {
                                        val isGlobalHidden = pickerFlight?.target == ProfilePickerTarget.Global
                                        GlobalProfileCard(
                                            selectedChoice = activeGlobalChoice,
                                            isHiddenForFlight = isGlobalHidden,
                                            onOpenFlight = { bounds, provider ->
                                                pickerFlight = ThermalPickerFlightRequest(
                                                    target = ProfilePickerTarget.Global,
                                                    title = context.getString(R.string.thermal_manager_global_profile),
                                                    subtitle = activeGlobalChoice.fileName,
                                                    selectedConfig = profileMap.globalConfig.ifBlank {
                                                        ThermalProfileController.STOCK_CONFIG_ID
                                                    },
                                                    includeFollowGlobal = false,
                                                    choices = globalChoices,
                                                    sourceRect = bounds,
                                                    sourceBoundsProvider = provider
                                                )
                                            },
                                            onCreateProfile = {
                                                openBaseEditor()
                                            }
                                        )
                                    }

                                    item(key = "thermal_tile_queue") {
                                        val isTileAddHidden = pickerFlight?.target == ProfilePickerTarget.TileQueue
                                        ThermalTileQueueCard(
                                            queue = tileProfileQueue,
                                            choicesById = choicesById,
                                            isAddHiddenForFlight = isTileAddHidden,
                                            onOpenAddFlight = { bounds, provider ->
                                                pickerFlight = ThermalPickerFlightRequest(
                                                    target = ProfilePickerTarget.TileQueue,
                                                    title = context.getString(R.string.thermal_manager_tile_queue_add),
                                                    subtitle = context.getString(R.string.thermal_manager_tile_queue),
                                                    selectedConfig = profileMap.globalConfig.ifBlank {
                                                        ThermalProfileController.STOCK_CONFIG_ID
                                                    },
                                                    includeFollowGlobal = false,
                                                    choices = globalChoices,
                                                    sourceRect = bounds,
                                                    sourceBoundsProvider = provider
                                                )
                                            },
                                            onMoveUp = { index ->
                                                tileProfileQueue = ThermalProfileController.moveTileProfileQueueItem(
                                                    context,
                                                    index,
                                                    index - 1
                                                )
                                            },
                                            onMoveDown = { index ->
                                                tileProfileQueue = ThermalProfileController.moveTileProfileQueueItem(
                                                    context,
                                                    index,
                                                    index + 1
                                                )
                                            },
                                            onRemove = { index ->
                                                tileProfileQueue = ThermalProfileController.removeTileProfileFromQueue(
                                                    context,
                                                    index
                                                )
                                            }
                                        )
                                    }
                                }
                            }

                            TAB_THERMAL_APPS -> {
                                LazyColumn(
                                    state = appsListState,
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = pagePadding
                                ) {
                                    item(key = "thermal_apps_search_and_filter") {
                                        Column(
                                            modifier = Modifier.padding(bottom = 14.dp),
                                            verticalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            AppIconsSearchField(
                                                query = appSearchQuery,
                                                hint = stringResource(R.string.thermal_manager_app_search_hint),
                                                onQueryChange = { appSearchQuery = it }
                                            )
                                            ThermalSystemAppsFilterRow(
                                                checked = showSystemApps,
                                                onCheckedChange = { showSystemApps = it }
                                            )
                                        }
                                    }

                                    when {
                                        appsLoading -> {
                                            item(key = "thermal_apps_skeleton") {
                                                SkeletonAppStreamCard()
                                            }
                                        }
                                        visibleApps.isEmpty() -> {
                                            item(key = "thermal_apps_empty") {
                                                EmptyStateText(
                                                    stringResource(R.string.thermal_manager_apps_empty)
                                                )
                                            }
                                        }
                                        else -> {
                                            itemsIndexed(
                                                items = visibleApps,
                                                key = { _, app -> app.packageName }
                                            ) { index, app ->
                                                val isFirst = index == 0
                                                val isLast = index == visibleApps.lastIndex
                                                val pkgTarget = ProfilePickerTarget.Package(app)
                                                val isRowHidden =
                                                    (pickerFlight?.target as? ProfilePickerTarget.Package)
                                                        ?.app?.packageName == app.packageName

                                                ThermalManagedAppStreamRow(
                                                    app = app,
                                                    sectionTitle = if (isFirst) {
                                                        "${stringResource(R.string.thermal_manager_apps_title)} (${visibleApps.size})"
                                                    } else null,
                                                    isFirst = isFirst,
                                                    isLast = isLast,
                                                    selectedConfig = profileMap.packageConfigs[app.packageName].orEmpty(),
                                                    globalConfig = profileMap.globalConfig,
                                                    choicesById = choicesById,
                                                    isHiddenForFlight = isRowHidden,
                                                    onOpenFlight = { bounds, provider ->
                                                        pickerFlight = ThermalPickerFlightRequest(
                                                            target = pkgTarget,
                                                            title = app.label,
                                                            subtitle = app.packageName,
                                                            appIcon = app.icon,
                                                            isFirstInStream = isFirst,
                                                            isLastInStream = isLast,
                                                            selectedConfig = profileMap.packageConfigs[app.packageName].orEmpty(),
                                                            globalConfig = profileMap.globalConfig,
                                                            includeFollowGlobal = true,
                                                            choices = appChoices,
                                                            sourceRect = bounds,
                                                            sourceBoundsProvider = provider
                                                        )
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            else -> {
                                LazyColumn(
                                    state = studioListState,
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = pagePadding,
                                    verticalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    item(key = "thermal_user_configs") {
                                        ProfileConfigSectionCard(
                                            title = stringResource(R.string.thermal_manager_user_configs),
                                            configs = userConfigs,
                                            expanded = userConfigsExpanded,
                                            onExpandedChange = { userConfigsExpanded = it },
                                            onImport = {
                                                importLauncher.launch(
                                                    arrayOf("application/json", "text/json", "text/*", "*/*")
                                                )
                                            },
                                            onCreate = ::openBaseEditor,
                                            emptyText = stringResource(R.string.thermal_manager_user_configs_empty),
                                            onEdit = ::openSavedEditor,
                                            onClone = ::openSavedClone,
                                            onExport = { config ->
                                                scope.launch {
                                                    val result = withContext(Dispatchers.IO) {
                                                        runCatching { config.file.readText() }
                                                    }
                                                    result.onSuccess { text ->
                                                        pendingExportText = text
                                                        exportLauncher.launch(config.name)
                                                    }.onFailure { throwable ->
                                                        showToast(
                                                            context.getString(
                                                                R.string.thermal_manager_export_failed,
                                                                throwable.message ?: config.name
                                                            )
                                                        )
                                                    }
                                                }
                                            },
                                            onDelete = { config ->
                                                if (config.locked) {
                                                    showToast(context.getString(R.string.thermal_manager_vendor_locked))
                                                } else {
                                                    scope.launch {
                                                        val deleted = withContext(Dispatchers.IO) {
                                                            config.file.delete()
                                                        }
                                                        if (deleted) {
                                                            withContext(Dispatchers.IO) {
                                                                ThermalProfileController.removeProfileMetadata(config.name)
                                                            }
                                                            refreshThermalDataSilent(reloadApps = false)
                                                            showToast(
                                                                context.getString(
                                                                    R.string.thermal_manager_deleted,
                                                                    config.name
                                                                )
                                                            )
                                                        } else {
                                                            showToast(
                                                                context.getString(
                                                                    R.string.thermal_manager_delete_failed,
                                                                    config.name
                                                                )
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        )
                                    }

                                    item(key = "thermal_system_configs") {
                                        ProfileConfigSectionCard(
                                            title = stringResource(R.string.thermal_manager_system_configs),
                                            configs = systemConfigs,
                                            expanded = systemConfigsExpanded,
                                            onExpandedChange = { systemConfigsExpanded = it },
                                            onImport = null,
                                            onCreate = null,
                                            emptyText = stringResource(R.string.thermal_manager_system_configs_empty),
                                            onEdit = ::openSavedEditor,
                                            onClone = ::openSavedClone,
                                            onExport = { config ->
                                                scope.launch {
                                                    val result = withContext(Dispatchers.IO) {
                                                        runCatching { config.file.readText() }
                                                    }
                                                    result.onSuccess { text ->
                                                        pendingExportText = text
                                                        exportLauncher.launch(config.name)
                                                    }.onFailure { throwable ->
                                                        showToast(
                                                            context.getString(
                                                                R.string.thermal_manager_export_failed,
                                                                throwable.message ?: config.name
                                                            )
                                                        )
                                                    }
                                                }
                                            },
                                            onDelete = {
                                                showToast(context.getString(R.string.thermal_manager_vendor_locked))
                                            }
                                        )
                                    }

                                    if (isEditorLoading) {
                                        item(key = "thermal_editor_loading") {
                                            LoadingDialogContent()
                                        }
                                    }

                                    if (baseConfigText != null) {
                                        item(key = "thermal_editor_header") {
                                            EditorHeaderCard(
                                                source = editorSource,
                                                profileName = profileName,
                                                onProfileNameChange = { profileName = it },
                                                fileNameInput = fileNameInput,
                                                onFileNameInputChange = { fileNameInput = it },
                                                selectedTab = selectedEditorTab,
                                                onTabSelected = { selectedEditorTabName = it.name },
                                                onSave = { saveEditedConfig(false) },
                                                onSaveAndApply = { saveEditedConfig(true) }
                                            )
                                        }

                                        item(key = "thermal_editor_tree") {
                                            ThermalParamTreeCard(
                                                branches = treeBranches,
                                                searchQuery = treeSearchQuery,
                                                onSearchQueryChange = { treeSearchQuery = it },
                                                visibleCount = filteredTreeParams.size,
                                                totalCount = tabParams.size,
                                                expandedNodeIds = expandedTreeNodes,
                                                forceExpanded = normalizedTreeQuery.isNotBlank(),
                                                activeFlightParamId = paramFlight?.param?.id,
                                                onToggleNode = { nodeId ->
                                                    expandedTreeNodes = if (nodeId in expandedTreeNodes) {
                                                        expandedTreeNodes - nodeId
                                                    } else {
                                                        expandedTreeNodes + nodeId
                                                    }
                                                },
                                                onOpenParamFlight = { param, depth, bounds, provider ->
                                                    paramFlight = ThermalParamFlightRequest(
                                                        param = param,
                                                        depth = depth,
                                                        sourceRect = bounds,
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
                }

                // Level-1 Pseudo-Modal A: Config Picker Flight Overlay (Global / TileQueue / App)
                ThermalConfigPickerFlightOverlay(
                    request = pickerFlight,
                    scrollBlur = scrollBlur,
                    topInsetPx = effectiveHeaderHeightPx,
                    closeSignal = level1CloseSignal,
                    onProgress = { pickerFlightT = it },
                    onClose = { pickerFlight = null },
                    onSelect = { target, choice ->
                        when (target) {
                            ProfilePickerTarget.Global -> {
                                ThermalProfileController.updateGlobalConfig(context, choice.id)
                            }
                            ProfilePickerTarget.TileQueue -> {
                                tileProfileQueue = ThermalProfileController.addTileProfileToQueue(
                                    context,
                                    choice.id
                                )
                            }
                            is ProfilePickerTarget.Package -> {
                                ThermalProfileController.updatePackageConfig(
                                    context,
                                    target.app.packageName,
                                    choice.id
                                )
                            }
                        }
                        profileMap = ThermalProfileController.readProfileMap()
                    }
                )

                // Level-1 Pseudo-Modal B: Tree Parameter Editor & Info Flight Overlay
                ThermalParamFlightOverlay(
                    request = paramFlight,
                    scrollBlur = scrollBlur,
                    topInsetPx = effectiveHeaderHeightPx,
                    closeSignal = level1CloseSignal,
                    onProgress = { paramFlightT = it },
                    onClose = { paramFlight = null },
                    onSave = { param, newValue ->
                        val updated = param.copy(value = newValue)
                        runCatching { parseEditedValue(context, updated) }
                            .onSuccess {
                                editableParams = editableParams.map {
                                    if (it.id == param.id) updated else it
                                }
                            }
                            .onFailure { throwable ->
                                showToast(
                                    throwable.message ?: context.getString(
                                        R.string.thermal_manager_error_bad_json,
                                        param.key
                                    )
                                )
                            }
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
            val spinTransition = rememberInfiniteTransition(label = "ThermalRefreshSpin")
            val spinAngle by spinTransition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 750, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "ThermalRefreshAngle"
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
                    text = stringResource(R.string.thermal_manager_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                        TileUtils.requestAddTileService(
                            context,
                            ThermalManagerTileService::class.java,
                            R.string.thermal_manager_title,
                            R.drawable.ic_thermal_tile
                        )
                    }
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = stringResource(R.string.thermal_manager_add_tile),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                IconButton(
                    onClick = { triggerManualSilentRefresh() },
                    enabled = !isSilentRefreshing
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

            // Clipped Collapsible Container for AppIconsTabs
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
                Column(modifier = Modifier.fillMaxWidth()) {
                    Spacer(Modifier.height(4.dp))
                    AppIconsTabs(
                        titles = listOf(
                            stringResource(R.string.thermal_manager_global_profile),
                            stringResource(R.string.thermal_manager_apps_title),
                            stringResource(R.string.thermal_manager_editor_title)
                        ),
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