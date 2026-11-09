package org.pixel.customparts.activities

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import org.pixel.customparts.R
import org.pixel.customparts.dynamicDarkColorScheme
import org.pixel.customparts.dynamicLightColorScheme
import org.pixel.customparts.services.ThermalManagerTileService
import org.pixel.customparts.ui.REBOOT_BUBBLE_CONTENT_BOTTOM_PADDING
import org.pixel.customparts.ui.RebootBubble
import org.pixel.customparts.ui.RebootBubbleMenuAction
import org.pixel.customparts.ui.SettingsGroupCard
import org.pixel.customparts.ui.TopBarBlurOverlay
import org.pixel.customparts.ui.TopStatusVeil
import org.pixel.customparts.ui.recordLayer
import org.pixel.customparts.ui.rememberGraphicsLayerRecordingState
import org.pixel.customparts.utils.RemoteStringsManager
import org.pixel.customparts.utils.ThermalConfigChoice
import org.pixel.customparts.utils.ThermalProfileController
import org.pixel.customparts.utils.ThermalProfileMap
import org.pixel.customparts.utils.TileUtils
import org.pixel.customparts.utils.dynamicStringResource
import java.io.File
import java.text.DecimalFormat
import java.text.Normalizer
import java.util.Locale

private const val VENDOR_THERMAL_CONFIG = "/vendor/etc/thermal_info_config.json"
private const val STOCK_THERMAL_CONFIG_FILE_NAME = "thermal_info_config.json"
private const val RESERVED_CUSTOM_SUFFIX = "_custom"
private const val EXTRA_OPEN_CREATE_PROFILE = "org.pixel.customparts.extra.OPEN_THERMAL_PROFILE_CREATOR"

private val FILE_NAME_TRANSLIT = mapOf(
    'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d", 'е' to "e",
    'ё' to "e", 'ж' to "zh", 'з' to "z", 'и' to "i", 'й' to "y", 'к' to "k",
    'л' to "l", 'м' to "m", 'н' to "n", 'о' to "o", 'п' to "p", 'р' to "r",
    'с' to "s", 'т' to "t", 'у' to "u", 'ф' to "f", 'х' to "h", 'ц' to "c",
    'ч' to "ch", 'ш' to "sh", 'щ' to "sch", 'ъ' to "", 'ы' to "y", 'ь' to "",
    'э' to "e", 'ю' to "yu", 'я' to "ya", 'і' to "i", 'ї' to "yi", 'є' to "ye",
    'ґ' to "g"
)

class ThermalConfigManagerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { ThermalConfigManagerApp { finish() } }
    }
}

class ThermalConfigEditorActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val openCreateProfile = intent.getBooleanExtra(EXTRA_OPEN_CREATE_PROFILE, false)
        setContent { ThermalConfigEditorApp(openCreateProfile = openCreateProfile) { finish() } }
    }
}

@Composable
private fun ThermalConfigManagerApp(onBack: () -> Unit) {
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
            ThermalConfigManagerScreen(onBack = onBack)
        }
    }
}

@Composable
private fun ThermalConfigEditorApp(openCreateProfile: Boolean, onBack: () -> Unit) {
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
            ThermalConfigEditorScreen(openCreateProfile = openCreateProfile, onBack = onBack)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThermalConfigManagerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { ThermalConfigStore(context) }
    val blurState = rememberGraphicsLayerRecordingState()
    val listState = rememberLazyListState()

    var savedConfigs by remember { mutableStateOf(emptyList<SavedThermalConfig>()) }
    var profileMap by remember { mutableStateOf(ThermalProfileMap()) }
    var tileProfileQueue by remember { mutableStateOf(ThermalProfileController.readTileProfileQueue(context)) }
    var managedApps by remember { mutableStateOf(emptyList<ThermalManagedApp>()) }
    var appsLoading by remember { mutableStateOf(true) }
    var showSystemApps by rememberSaveable { mutableStateOf(true) }
    var appSearchQuery by rememberSaveable { mutableStateOf("") }
    var pickerTarget by remember { mutableStateOf<ProfilePickerTarget?>(null) }

    fun refreshThermalState() {
        scope.launch {
            withContext(Dispatchers.IO) { ThermalProfileController.seedVendorConfigs() }
            savedConfigs = withContext(Dispatchers.IO) { store.listSavedConfigs() }
            profileMap = withContext(Dispatchers.IO) { ThermalProfileController.readProfileMap() }
            tileProfileQueue = withContext(Dispatchers.IO) { ThermalProfileController.readTileProfileQueue(context) }
        }
    }

    val editorLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        refreshThermalState()
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { ThermalProfileController.seedVendorConfigs() }
        savedConfigs = withContext(Dispatchers.IO) { store.listSavedConfigs() }
        profileMap = withContext(Dispatchers.IO) { ThermalProfileController.readProfileMap() }
        tileProfileQueue = withContext(Dispatchers.IO) { ThermalProfileController.readTileProfileQueue(context) }
        managedApps = withContext(Dispatchers.IO) { loadThermalManagedApps(context) }
        appsLoading = false
    }

    val globalChoices = remember(savedConfigs) { ThermalProfileController.listConfigChoices(includeFollowGlobal = false) }
    val appChoices = remember(savedConfigs) { ThermalProfileController.listConfigChoices(includeFollowGlobal = true) }
    val choicesById = remember(globalChoices, appChoices) { (globalChoices + appChoices).associateBy { it.id } }

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

    pickerTarget?.let { target ->
        ConfigPickerDialog(
            title = when (target) {
                ProfilePickerTarget.Global -> dynamicStringResource(R.string.thermal_manager_global_profile)
                ProfilePickerTarget.TileQueue -> dynamicStringResource(R.string.thermal_manager_tile_queue_add)
                is ProfilePickerTarget.Package -> target.app.label
            },
            selectedConfig = when (target) {
                ProfilePickerTarget.Global -> profileMap.globalConfig.ifBlank { ThermalProfileController.STOCK_CONFIG_ID }
                ProfilePickerTarget.TileQueue -> profileMap.globalConfig.ifBlank { ThermalProfileController.STOCK_CONFIG_ID }
                is ProfilePickerTarget.Package -> profileMap.packageConfigs[target.app.packageName].orEmpty()
            },
            includeFollowGlobal = target is ProfilePickerTarget.Package,
            choices = if (target is ProfilePickerTarget.Package) appChoices else globalChoices,
            onDismiss = { pickerTarget = null },
            onSelect = { choice ->
                when (target) {
                    ProfilePickerTarget.Global -> {
                        ThermalProfileController.updateGlobalConfig(context, choice.id)
                    }
                    ProfilePickerTarget.TileQueue -> {
                        tileProfileQueue = ThermalProfileController.addTileProfileToQueue(context, choice.id)
                    }
                    is ProfilePickerTarget.Package -> {
                        ThermalProfileController.updatePackageConfig(context, target.app.packageName, choice.id)
                    }
                }
                profileMap = ThermalProfileController.readProfileMap()
                pickerTarget = null
            }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentWindowInsets = WindowInsets.navigationBars,
        floatingActionButton = {
            RebootBubble(
                extraActions = listOf(
                    RebootBubbleMenuAction(
                        icon = Icons.Rounded.Add,
                        label = dynamicStringResource(R.string.thermal_manager_add_tile),
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        onClick = {
                            TileUtils.requestAddTileService(
                                context,
                                ThermalManagerTileService::class.java,
                                R.string.thermal_manager_title,
                                R.drawable.ic_thermal_tile
                            )
                        }
                    )
                )
            )
        },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = dynamicStringResource(R.string.thermal_manager_title),
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, dynamicStringResource(R.string.nav_back))
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
                    GlobalProfileCard(
                        selectedChoice = choiceForConfig(
                            profileMap.globalConfig.ifBlank { ThermalProfileController.STOCK_CONFIG_ID },
                            choicesById,
                            includeFollowGlobal = false
                        ),
                        onClick = { pickerTarget = ProfilePickerTarget.Global },
                        onCreateProfile = {
                            editorLauncher.launch(
                                Intent(context, ThermalConfigEditorActivity::class.java)
                                    .putExtra(EXTRA_OPEN_CREATE_PROFILE, true)
                            )
                        }
                    )
                }

                item {
                    ThermalTileQueueCard(
                        queue = tileProfileQueue,
                        choicesById = choicesById,
                        onAdd = { pickerTarget = ProfilePickerTarget.TileQueue },
                        onMoveUp = { index ->
                            tileProfileQueue = ThermalProfileController.moveTileProfileQueueItem(context, index, index - 1)
                        },
                        onMoveDown = { index ->
                            tileProfileQueue = ThermalProfileController.moveTileProfileQueueItem(context, index, index + 1)
                        },
                        onRemove = { index ->
                            tileProfileQueue = ThermalProfileController.removeTileProfileFromQueue(context, index)
                        }
                    )
                }

                item {
                    ThermalSystemAppsFilterRow(
                        checked = showSystemApps,
                        onCheckedChange = { showSystemApps = it }
                    )
                }

                item {
                    ThermalCompactSearchField(
                        query = appSearchQuery,
                        onQueryChange = { appSearchQuery = it },
                        hint = dynamicStringResource(R.string.thermal_manager_app_search_hint)
                    )
                }

                when {
                    appsLoading -> item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = dynamicStringResource(R.string.thermal_manager_apps_loading),
                                modifier = Modifier.padding(16.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    visibleApps.isEmpty() -> item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = dynamicStringResource(R.string.thermal_manager_apps_empty),
                                modifier = Modifier.padding(16.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    else -> {
                        item {
                            Text(
                                text = dynamicStringResource(R.string.thermal_manager_apps_title),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                            )
                        }

                        items(visibleApps, key = { it.packageName }) { app ->
                            ThermalManagedAppRow(
                                app = app,
                                selectedConfig = profileMap.packageConfigs[app.packageName].orEmpty(),
                                globalConfig = profileMap.globalConfig,
                                choicesById = choicesById,
                                onClick = { pickerTarget = ProfilePickerTarget.Package(app) }
                            )
                        }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThermalConfigEditorScreen(openCreateProfile: Boolean, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { ThermalConfigStore(context) }
    val blurState = rememberGraphicsLayerRecordingState()
    val listState = rememberLazyListState()
    val defaultEditorSource = dynamicStringResource(R.string.thermal_manager_source_vendor)

    var savedConfigs by remember { mutableStateOf(emptyList<SavedThermalConfig>()) }
    var userConfigsExpanded by rememberSaveable { mutableStateOf(false) }
    var systemConfigsExpanded by rememberSaveable { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var editorSource by remember { mutableStateOf(defaultEditorSource) }
    val defaultProfileName = dynamicStringResource(R.string.thermal_manager_default_profile_name)
    var profileName by remember(defaultProfileName) { mutableStateOf(defaultProfileName) }
    var fileNameInput by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var baseConfigText by remember { mutableStateOf<String?>(null) }
    var editableParams by remember { mutableStateOf(emptyList<ThermalConfigParam>()) }
    var infoParam by remember { mutableStateOf<ThermalConfigParam?>(null) }
    var editingParam by remember { mutableStateOf<ThermalConfigParam?>(null) }
    var pendingExportText by remember { mutableStateOf<String?>(null) }
    var editorFocusRequest by remember { mutableStateOf(0) }
    var expandedTreeNodes by rememberSaveable { mutableStateOf(emptySet<String>()) }
    var selectedTabName by rememberSaveable { mutableStateOf(ThermalEditorTab.ALL.name) }
    val selectedTab = runCatching { ThermalEditorTab.valueOf(selectedTabName) }.getOrDefault(ThermalEditorTab.ALL)
    val userConfigs = remember(savedConfigs) { savedConfigs.filterNot { it.locked } }
    val systemConfigs = remember(savedConfigs) { savedConfigs.filter { it.locked } }

    fun refreshSavedConfigs() {
        scope.launch {
            withContext(Dispatchers.IO) { ThermalProfileController.seedVendorConfigs() }
            savedConfigs = withContext(Dispatchers.IO) { store.listSavedConfigs() }
        }
    }

    fun showToast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    fun openEditorFromText(sourceName: String, rawJson: String, suggestedProfileName: String, suggestedFileName: String? = null) {
        try {
            val root = parseThermalJsonObject(context, rawJson)
            val params = buildEditableParams(context, root)
            baseConfigText = root.toString()
            editableParams = params
            profileName = suggestedProfileName.removeSuffix(".json").takeIf { it.isNotBlank() } ?: defaultProfileName
            fileNameInput = suggestedFileName?.removeSuffix(".json").orEmpty()
            editorSource = sourceName
            searchQuery = ""
            expandedTreeNodes = defaultExpandedTreeNodeIds(params)
            selectedTabName = ThermalEditorTab.ALL.name
            editorFocusRequest++
        } catch (throwable: Throwable) {
            showToast(context.getString(R.string.thermal_manager_error_bad_json, throwable.message ?: context.getString(R.string.thermal_manager_json_label)))
        }
    }

    fun openBaseEditor() {
        scope.launch {
            isLoading = true
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    Triple(
                        context.getString(R.string.thermal_manager_source_vendor),
                        store.readVendorConfig(),
                        context.getString(R.string.thermal_manager_default_profile_name)
                    )
                }
            }
            isLoading = false
            result.onSuccess { (source, json, suggestedName) ->
                openEditorFromText(source, json, suggestedName)
            }.onFailure { throwable ->
                showToast(context.getString(R.string.thermal_manager_error_vendor_missing, throwable.message ?: VENDOR_THERMAL_CONFIG))
            }
        }
    }

    fun openSavedEditor(config: SavedThermalConfig) {
        if (config.locked) {
            showToast(context.getString(R.string.thermal_manager_vendor_read_only))
            return
        }

        scope.launch {
            isLoading = true
            val result = withContext(Dispatchers.IO) { runCatching { config.file.readText() } }
            isLoading = false
            result.onSuccess { json ->
                openEditorFromText(config.displayName, json, config.displayName, config.name)
            }.onFailure { throwable ->
                showToast(context.getString(R.string.thermal_manager_error_read_file, throwable.message ?: config.name))
            }
        }
    }

    fun openSavedClone(config: SavedThermalConfig) {
        scope.launch {
            isLoading = true
            val result = withContext(Dispatchers.IO) { runCatching { config.file.readText() } }
            isLoading = false
            result.onSuccess { json ->
                openEditorFromText(
                    context.getString(R.string.thermal_manager_source_copy, config.displayName),
                    json,
                    config.displayName + "_copy",
                    config.name.removeSuffix(".json") + "_copy"
                )
            }.onFailure { throwable ->
                showToast(context.getString(R.string.thermal_manager_error_read_file, throwable.message ?: config.name))
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
            isLoading = true
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val root = parseThermalJsonObject(context, sourceText)
                    editableParams.forEach { param ->
                        setJsonValue(root, param.path, parseEditedValue(context, param))
                    }
                    store.saveConfig(safeFileName, root.toString(4), displayName)
                }
            }
            isLoading = false
            result.onSuccess { file ->
                refreshSavedConfigs()
                showToast(context.getString(R.string.thermal_manager_saved, file.name))
                if (applyAfterSave) {
                    if (ThermalProfileController.updateGlobalConfig(context, file.name)) {
                        showToast(context.getString(R.string.thermal_manager_applied, file.name))
                    } else {
                        showToast(context.getString(R.string.thermal_manager_apply_failed))
                    }
                }
            }.onFailure { throwable ->
                showToast(context.getString(R.string.thermal_manager_error_save, throwable.message ?: safeFileName))
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val text = pendingExportText
        pendingExportText = null
        if (uri != null && text != null) {
            scope.launch {
                val result = withContext(Dispatchers.IO) { runCatching { store.writeUri(uri, text) } }
                result.onSuccess { showToast(context.getString(R.string.thermal_manager_exported)) }
                    .onFailure { showToast(context.getString(R.string.thermal_manager_export_failed, it.message ?: context.getString(R.string.thermal_manager_export_label))) }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            isLoading = true
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val importedText = store.readUri(uri)
                    val imported = parseThermalJsonObject(context, importedText)
                    val reference = parseThermalJsonObject(context, store.readVendorConfig())
                    val validation = validateImportedConfig(context, reference, imported)
                    if (validation.isNotEmpty()) {
                        error(validation.take(8).joinToString("\n"))
                    }
                    val displayName = store.displayName(uri) ?: context.getString(R.string.thermal_manager_imported_profile_file_name)
                    val profileDisplayName = displayName.removeSuffix(".json")
                    val safeName = resolveConfigFileName(profileDisplayName, "")
                        ?: error(context.getString(R.string.thermal_manager_name_invalid))
                    val formattedJson = imported.toString(4)
                    val file = store.saveConfig(safeName, formattedJson, profileDisplayName)
                    ImportedThermalConfig(file, profileDisplayName, formattedJson)
                }
            }
            isLoading = false
            result.onSuccess { imported ->
                refreshSavedConfigs()
                openEditorFromText(
                    context.getString(R.string.thermal_manager_source_copy, imported.displayName),
                    imported.json,
                    imported.displayName,
                    imported.file.name
                )
                showToast(context.getString(R.string.thermal_manager_imported, imported.file.name))
            }.onFailure { throwable ->
                showToast(context.getString(R.string.thermal_manager_import_failed, throwable.message ?: context.getString(R.string.thermal_manager_import_label)))
            }
        }
    }

    LaunchedEffect(editorFocusRequest) {
        if (editorFocusRequest > 0) {
            listState.animateScrollToItem(2)
        }
    }

    LaunchedEffect(openCreateProfile) {
        withContext(Dispatchers.IO) { ThermalProfileController.seedVendorConfigs() }
        savedConfigs = withContext(Dispatchers.IO) { store.listSavedConfigs() }
        if (openCreateProfile) {
            openBaseEditor()
        }
    }

    val tabParams = remember(editableParams, selectedTab) {
        when (selectedTab) {
            ThermalEditorTab.SOC -> editableParams.filter { it.matchesThermalTargets(THERMAL_EDITOR_SOC_TARGETS) }
            ThermalEditorTab.BATTERY -> editableParams.filter { it.matchesThermalTargets(THERMAL_EDITOR_BATTERY_TARGETS) }
            ThermalEditorTab.ALL -> editableParams
        }
    }
    val normalizedQuery = searchQuery.trim().lowercase(Locale.ROOT)
    val filteredParams = remember(tabParams, normalizedQuery) {
        if (normalizedQuery.isBlank()) {
            tabParams
        } else {
            val tokens = normalizedQuery.split(Regex("\\s+")).filter { it.isNotBlank() }
            tabParams.filter { param -> tokens.all { token -> param.searchText.contains(token) } }
        }
    }
    val treeBranches = remember(context, filteredParams) { buildThermalParamTree(context, filteredParams) }
    val forceExpandTree = normalizedQuery.isNotBlank()

    infoParam?.let { param ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { infoParam = null },
            icon = { Icon(Icons.Rounded.Info, contentDescription = null) },
            title = { Text(param.key, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(param.help)
                    Text(
                        text = param.displayPath,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { infoParam = null }) {
                    Text(dynamicStringResource(R.string.btn_ok))
                }
            }
        )
    }

    editingParam?.let { param ->
        ThermalParamEditDialog(
            param = param,
            onDismiss = { editingParam = null },
            onSave = { newValue ->
                val updated = param.copy(value = newValue)
                runCatching { parseEditedValue(context, updated) }
                    .onSuccess {
                        editableParams = editableParams.map {
                            if (it.id == param.id) updated else it
                        }
                        editingParam = null
                    }
                    .onFailure { throwable ->
                        showToast(throwable.message ?: context.getString(R.string.thermal_manager_error_bad_json, param.key))
                    }
            }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentWindowInsets = WindowInsets.navigationBars,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = dynamicStringResource(R.string.thermal_manager_editor_title),
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, dynamicStringResource(R.string.nav_back))
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
                    ProfileConfigSectionCard(
                        title = dynamicStringResource(R.string.thermal_manager_user_configs),
                        configs = userConfigs,
                        expanded = userConfigsExpanded,
                        onExpandedChange = { userConfigsExpanded = it },
                        onImport = { importLauncher.launch(arrayOf("application/json", "text/json", "text/*", "*/*")) },
                        onCreate = ::openBaseEditor,
                        emptyText = dynamicStringResource(R.string.thermal_manager_user_configs_empty),
                        onEdit = ::openSavedEditor,
                        onClone = ::openSavedClone,
                        onExport = { config ->
                            scope.launch {
                                val result = withContext(Dispatchers.IO) { runCatching { config.file.readText() } }
                                result.onSuccess { text ->
                                    pendingExportText = text
                                    exportLauncher.launch(config.name)
                                }.onFailure { throwable ->
                                    showToast(context.getString(R.string.thermal_manager_export_failed, throwable.message ?: config.name))
                                }
                            }
                        },
                        onDelete = { config ->
                            if (config.locked) {
                                showToast(context.getString(R.string.thermal_manager_vendor_locked))
                            } else {
                                scope.launch {
                                    val deleted = withContext(Dispatchers.IO) { config.file.delete() }
                                    if (deleted) {
                                        withContext(Dispatchers.IO) { ThermalProfileController.removeProfileMetadata(config.name) }
                                        refreshSavedConfigs()
                                        showToast(context.getString(R.string.thermal_manager_deleted, config.name))
                                    } else {
                                        showToast(context.getString(R.string.thermal_manager_delete_failed, config.name))
                                    }
                                }
                            }
                        }
                    )
                }

                item {
                    ProfileConfigSectionCard(
                        title = dynamicStringResource(R.string.thermal_manager_system_configs),
                        configs = systemConfigs,
                        expanded = systemConfigsExpanded,
                        onExpandedChange = { systemConfigsExpanded = it },
                        onImport = null,
                        onCreate = null,
                        emptyText = dynamicStringResource(R.string.thermal_manager_system_configs_empty),
                        onEdit = ::openSavedEditor,
                        onClone = ::openSavedClone,
                        onExport = { config ->
                            scope.launch {
                                val result = withContext(Dispatchers.IO) { runCatching { config.file.readText() } }
                                result.onSuccess { text ->
                                    pendingExportText = text
                                    exportLauncher.launch(config.name)
                                }.onFailure { throwable ->
                                    showToast(context.getString(R.string.thermal_manager_export_failed, throwable.message ?: config.name))
                                }
                            }
                        },
                        onDelete = { config ->
                            showToast(context.getString(R.string.thermal_manager_vendor_locked))
                        }
                    )
                }

                if (isLoading) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = dynamicStringResource(R.string.thermal_manager_loading),
                                modifier = Modifier.padding(16.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (baseConfigText != null) {
                    item {
                        EditorHeaderCard(
                            source = editorSource,
                            profileName = profileName,
                            onProfileNameChange = { profileName = it },
                            fileNameInput = fileNameInput,
                            onFileNameInputChange = { fileNameInput = it },
                            selectedTab = selectedTab,
                            onTabSelected = { selectedTabName = it.name },
                            onSave = { saveEditedConfig(false) },
                            onSaveAndApply = { saveEditedConfig(true) }
                        )
                    }

                    item {
                        ThermalParamTreeCard(
                            branches = treeBranches,
                            searchQuery = searchQuery,
                            onSearchQueryChange = { searchQuery = it },
                            visibleCount = filteredParams.size,
                            totalCount = tabParams.size,
                            expandedNodeIds = expandedTreeNodes,
                            forceExpanded = forceExpandTree,
                            onToggleNode = { nodeId ->
                                expandedTreeNodes = if (nodeId in expandedTreeNodes) {
                                    expandedTreeNodes - nodeId
                                } else {
                                    expandedTreeNodes + nodeId
                                }
                            },
                            onParamClick = { editingParam = it },
                            onInfoClick = { infoParam = it }
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
