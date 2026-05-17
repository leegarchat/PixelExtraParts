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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
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
import org.pixel.customparts.ui.RebootBubble
import org.pixel.customparts.ui.REBOOT_BUBBLE_CONTENT_BOTTOM_PADDING
import org.pixel.customparts.ui.RebootBubbleMenuAction
import org.pixel.customparts.ui.SettingsGroupCard
import org.pixel.customparts.ui.TopBarBlurOverlay
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
    val isScrolled by remember { derivedStateOf { listState.canScrollBackward } }

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
                    .recordLayer(blurState)
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
                blurState = blurState,
                isScrolled = isScrolled
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
    val isScrolled by remember { derivedStateOf { listState.canScrollBackward } }
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
                    if (ThermalProfileController.applyConfig(context, file.name)) {
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
                    .recordLayer(blurState)
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
                blurState = blurState,
                isScrolled = isScrolled
            )
        }
    }
}

@Composable
private fun ProfileConfigSectionCard(
    title: String,
    configs: List<SavedThermalConfig>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onImport: (() -> Unit)?,
    onCreate: (() -> Unit)?,
    emptyText: String,
    onEdit: (SavedThermalConfig) -> Unit,
    onClone: (SavedThermalConfig) -> Unit,
    onExport: (SavedThermalConfig) -> Unit,
    onDelete: (SavedThermalConfig) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onExpandedChange(!expanded) }
                .padding(start = 16.dp, top = 10.dp, end = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = if (expanded) {
                        dynamicStringResource(R.string.thermal_manager_saved_count, configs.size)
                    } else {
                        dynamicStringResource(R.string.thermal_manager_profiles_collapsed)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            onImport?.let { importAction ->
                IconButton(onClick = importAction) {
                    Icon(
                        Icons.Rounded.UploadFile,
                        contentDescription = dynamicStringResource(R.string.thermal_manager_import),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            IconButton(onClick = { onExpandedChange(!expanded) }) {
                Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, contentDescription = null)
            }
        }

        AnimatedVisibility(visible = expanded) {
            Column {
                if (configs.isEmpty()) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = emptyText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        onCreate?.let { createAction ->
                            TextButton(onClick = createAction) {
                                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(dynamicStringResource(R.string.thermal_manager_create_config))
                            }
                        }
                    }
                } else {
                    configs.forEachIndexed { index, config ->
                        SavedConfigRow(
                            config = config,
                            onEdit = { onEdit(config) },
                            onClone = { onClone(config) },
                            onExport = { onExport(config) },
                            onDelete = { onDelete(config) }
                        )
                        if (index != configs.lastIndex) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SavedConfigRow(
    config: SavedThermalConfig,
    onEdit: () -> Unit,
    onClone: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = config.displayName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                modifier = Modifier.padding(top = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = config.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = config.sizeText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1
                )
            }
        }
        Box {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(Icons.Rounded.MoreVert, dynamicStringResource(R.string.thermal_manager_actions))
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
                shape = RoundedCornerShape(18.dp),
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 4.dp,
                shadowElevation = 8.dp
            ) {
                DropdownMenuItem(
                    text = { Text(dynamicStringResource(R.string.thermal_manager_create_from)) },
                    leadingIcon = { Icon(Icons.Rounded.ContentCopy, contentDescription = null) },
                    onClick = {
                        menuExpanded = false
                        onClone()
                    }
                )
                DropdownMenuItem(
                    text = { Text(dynamicStringResource(R.string.thermal_manager_export)) },
                    leadingIcon = { Icon(Icons.Rounded.Download, contentDescription = null) },
                    onClick = {
                        menuExpanded = false
                        onExport()
                    }
                )
                DropdownMenuItem(
                    text = { Text(dynamicStringResource(R.string.thermal_manager_edit)) },
                    leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                    enabled = !config.locked,
                    onClick = {
                        menuExpanded = false
                        onEdit()
                    }
                )
                DropdownMenuItem(
                    text = { Text(dynamicStringResource(R.string.thermal_manager_delete)) },
                    leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
                    enabled = !config.locked,
                    onClick = {
                        menuExpanded = false
                        onDelete()
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GlobalProfileCard(
    selectedChoice: ThermalConfigChoice,
    onClick: () -> Unit,
    onCreateProfile: () -> Unit
) {
    val context = LocalContext.current
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, top = 12.dp, end = 10.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = dynamicStringResource(R.string.thermal_manager_global_profile),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Surface(
                modifier = Modifier
                    .size(40.dp)
                    .combinedClickable(
                        onClick = onCreateProfile,
                        onLongClick = {
                            Toast.makeText(
                                context,
                                context.getString(R.string.thermal_manager_create_profile_hint),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    ),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = dynamicStringResource(R.string.thermal_manager_create_profile_short),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 10.dp)
            ) {
                Text(
                    text = profileChoiceTitle(selectedChoice),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                ProfileChoiceDetails(choice = selectedChoice, showPath = true)
            }
            Icon(
                Icons.Rounded.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

@Composable
private fun ThermalTileQueueCard(
    queue: List<String>,
    choicesById: Map<String, ThermalConfigChoice>,
    onAdd: () -> Unit,
    onMoveUp: (Int) -> Unit,
    onMoveDown: (Int) -> Unit,
    onRemove: (Int) -> Unit
) {
    SettingsGroupCard(title = dynamicStringResource(R.string.thermal_manager_tile_queue)) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = dynamicStringResource(
                        R.string.thermal_manager_tile_queue_count,
                        queue.size,
                        ThermalProfileController.MAX_TILE_QUEUE_SIZE
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = onAdd,
                    enabled = queue.size < ThermalProfileController.MAX_TILE_QUEUE_SIZE,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = dynamicStringResource(R.string.thermal_manager_tile_queue_add),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (queue.isEmpty()) {
                Text(
                    text = dynamicStringResource(R.string.thermal_manager_tile_queue_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
                )
            } else {
                queue.forEachIndexed { index, configId ->
                    ThermalTileQueueRow(
                        index = index,
                        choice = choiceForConfig(configId, choicesById, includeFollowGlobal = false),
                        isFirst = index == 0,
                        isLast = index == queue.lastIndex,
                        onMoveUp = { onMoveUp(index) },
                        onMoveDown = { onMoveDown(index) },
                        onRemove = { onRemove(index) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ThermalTileQueueRow(
    index: Int,
    choice: ThermalConfigChoice,
    isFirst: Boolean,
    isLast: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, top = 10.dp, end = 6.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = (index + 1).toString(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(28.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = profileChoiceTitle(choice),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                ProfileChoiceDetails(choice = choice, showPath = false)
            }
            IconButton(
                onClick = onMoveUp,
                enabled = !isFirst,
                modifier = Modifier.size(34.dp)
            ) {
                Icon(
                    Icons.Rounded.ExpandLess,
                    contentDescription = dynamicStringResource(R.string.thermal_manager_tile_queue_move_up),
                    modifier = Modifier.size(20.dp)
                )
            }
            IconButton(
                onClick = onMoveDown,
                enabled = !isLast,
                modifier = Modifier.size(34.dp)
            ) {
                Icon(
                    Icons.Rounded.ExpandMore,
                    contentDescription = dynamicStringResource(R.string.thermal_manager_tile_queue_move_down),
                    modifier = Modifier.size(20.dp)
                )
            }
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(34.dp)
            ) {
                Icon(
                    Icons.Rounded.Delete,
                    contentDescription = dynamicStringResource(R.string.thermal_manager_tile_queue_remove),
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(19.dp)
                )
            }
        }
    }
}

@Composable
private fun ProfileChoiceDetails(
    choice: ThermalConfigChoice,
    showPath: Boolean,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(
            modifier = Modifier.padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ProfileTypeBadge(choice)
            choice.fileName?.takeIf { it.isNotBlank() }?.let { fileName ->
                Spacer(Modifier.width(6.dp))
                Text(
                    text = dynamicStringResource(R.string.thermal_manager_profile_file, fileName),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (showPath) {
            choice.propertyValue?.takeIf { it.startsWith('/') }?.let { path ->
                Text(
                    text = dynamicStringResource(R.string.thermal_manager_profile_path, path),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ProfileTypeBadge(choice: ThermalConfigChoice) {
    val isSystem = choice.builtIn || choice.source == ThermalProfileController.PROFILE_SOURCE_SYSTEM
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(
                if (isSystem) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer
            )
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = if (isSystem) {
                dynamicStringResource(R.string.thermal_manager_profile_system)
            } else {
                dynamicStringResource(R.string.thermal_manager_profile_user)
            },
            style = MaterialTheme.typography.labelSmall,
            color = if (isSystem) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer,
            maxLines = 1
        )
    }
}

@Composable
private fun ThermalSystemAppsFilterRow(
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
                    text = dynamicStringResource(R.string.thermal_manager_show_system_title),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = dynamicStringResource(R.string.thermal_manager_show_system_summary),
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
private fun ThermalCompactSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    hint: String
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
private fun ThermalManagedAppRow(
    app: ThermalManagedApp,
    selectedConfig: String,
    globalConfig: String,
    choicesById: Map<String, ThermalConfigChoice>,
    onClick: () -> Unit
) {
    val globalChoice = choiceForConfig(
        globalConfig.ifBlank { ThermalProfileController.STOCK_CONFIG_ID },
        choicesById,
        includeFollowGlobal = false
    )
    val selectedChoice = if (selectedConfig.isBlank()) {
        globalChoice
    } else {
        choiceForConfig(selectedConfig, choicesById, includeFollowGlobal = true)
    }
    val isGlobalSelection = selectedConfig.isBlank()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selectedConfig.isNotBlank()) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
            } else if (app.isSystem) {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.07f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    modifier = Modifier.padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (app.icon != null) {
                        Image(
                            bitmap = app.icon.toBitmap().asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape)
                        )
                    }

                    Spacer(Modifier.width(8.dp))

                    if (app.isSystem) {
                        ThermalSystemAppBadge()
                        Spacer(Modifier.width(6.dp))
                    }

                    Text(
                        text = app.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                }
                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ProfileTypeBadge(selectedChoice)
                    Spacer(Modifier.width(6.dp))
                    SelectionTypeBadge(isGlobalSelection = isGlobalSelection)
                }
                Text(
                    text = profileChoiceTitle(selectedChoice),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 6.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Icon(
                imageVector = Icons.Rounded.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 12.dp)
            )
        }
    }
}

@Composable
private fun ThermalSystemAppBadge() {
    Box(
        modifier = Modifier
            .size(18.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.38f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = dynamicStringResource(R.string.launcher_hidden_apps_system_badge),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

@Composable
private fun SelectionTypeBadge(isGlobalSelection: Boolean) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(
                if (isGlobalSelection) MaterialTheme.colorScheme.tertiaryContainer
                else MaterialTheme.colorScheme.primaryContainer
            )
            .padding(horizontal = 8.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (isGlobalSelection) {
                dynamicStringResource(R.string.thermal_manager_selection_global)
            } else {
                dynamicStringResource(R.string.thermal_manager_selection_selected)
            },
            style = MaterialTheme.typography.labelSmall,
            color = if (isGlobalSelection) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onPrimaryContainer,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfigPickerDialog(
    title: String,
    selectedConfig: String,
    includeFollowGlobal: Boolean,
    choices: List<ThermalConfigChoice>,
    onDismiss: () -> Unit,
    onSelect: (ThermalConfigChoice) -> Unit
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val context = LocalContext.current
    val selectedChoiceId = selectedProfileChoiceId(selectedConfig, includeFollowGlobal)
    val orderedChoices = remember(choices, selectedConfig, includeFollowGlobal) {
        orderedProfileChoices(choices, selectedConfig, includeFollowGlobal)
    }
    val filteredChoices = remember(orderedChoices, searchQuery, context) {
        val tokens = searchQuery.trim().lowercase(Locale.ROOT).split(Regex("\\s+")).filter { it.isNotBlank() }
        if (tokens.isEmpty()) {
            orderedChoices
        } else {
            orderedChoices.filter { choice ->
                val haystack = profileChoiceSearchText(context, choice)
                tokens.all { token -> haystack.contains(token) }
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surfaceContainer
        ) {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                contentWindowInsets = WindowInsets.navigationBars,
                topBar = {
                    TopAppBar(
                        title = {
                            Text(
                                text = title,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Rounded.Close, dynamicStringResource(R.string.btn_close))
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            scrolledContainerColor = MaterialTheme.colorScheme.surface
                        )
                    )
                }
            ) { innerPadding ->
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceContainer),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        top = innerPadding.calculateTopPadding() + 12.dp,
                        end = 16.dp,
                        bottom = innerPadding.calculateBottomPadding() + REBOOT_BUBBLE_CONTENT_BOTTOM_PADDING
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        ThermalCompactSearchField(
                            query = searchQuery,
                            onQueryChange = { searchQuery = it },
                            hint = dynamicStringResource(R.string.thermal_manager_profile_search_hint)
                        )
                    }

                    if (filteredChoices.isEmpty()) {
                        item {
                            Text(
                                text = dynamicStringResource(R.string.thermal_manager_profile_search_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp)
                            )
                        }
                    } else {
                        items(filteredChoices, key = { it.id }) { choice ->
                            ProfilePickerChoiceRow(
                                choice = choice,
                                selected = choice.id == selectedChoiceId,
                                onClick = { onSelect(choice) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfilePickerChoiceRow(
    choice: ThermalConfigChoice,
    selected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.62f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                if (selected) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    text = profileChoiceTitle(choice),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                ProfileTypeBadge(choice)
                choice.fileName?.takeIf { it.isNotBlank() }?.let { fileName ->
                    Text(
                        text = fileName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

private fun selectedProfileChoiceId(selectedConfig: String, includeFollowGlobal: Boolean): String {
    val normalized = ThermalProfileController.normalizeConfigId(selectedConfig)
    return when {
        includeFollowGlobal && normalized.isBlank() -> ThermalProfileController.FOLLOW_GLOBAL_ID
        normalized.isBlank() -> ThermalProfileController.STOCK_CONFIG_ID
        else -> normalized.substringAfterLast('/')
    }
}

private fun orderedProfileChoices(
    choices: List<ThermalConfigChoice>,
    selectedConfig: String,
    includeFollowGlobal: Boolean
): List<ThermalConfigChoice> {
    val selectedId = selectedProfileChoiceId(selectedConfig, includeFollowGlobal)
    val usedIds = mutableSetOf<String>()
    val ordered = mutableListOf<ThermalConfigChoice>()

    fun addChoice(choice: ThermalConfigChoice?) {
        if (choice != null && usedIds.add(choice.id)) {
            ordered += choice
        }
    }

    addChoice(choices.firstOrNull { it.id == selectedId })

    if (includeFollowGlobal) {
        addChoice(choices.firstOrNull { it.id == ThermalProfileController.FOLLOW_GLOBAL_ID })
    }

    addChoice(choices.firstOrNull { it.id == ThermalProfileController.STOCK_CONFIG_ID })

    choices
        .filterNot { it.id in usedIds }
        .filterNot { isSystemProfileChoice(it) }
        .sortedWith(compareBy<ThermalConfigChoice> { it.label.lowercase(Locale.ROOT) }.thenBy { it.fileName.orEmpty() })
        .forEach(::addChoice)

    choices
        .filterNot { it.id in usedIds }
        .filter { isSystemProfileChoice(it) }
        .sortedWith(compareBy<ThermalConfigChoice> { it.label.lowercase(Locale.ROOT) }.thenBy { it.fileName.orEmpty() })
        .forEach(::addChoice)

    return ordered
}

private fun isSystemProfileChoice(choice: ThermalConfigChoice): Boolean {
    return choice.builtIn || choice.source == ThermalProfileController.PROFILE_SOURCE_SYSTEM
}

private fun profileChoiceSearchText(context: Context, choice: ThermalConfigChoice): String {
    val title = when (choice.id) {
        ThermalProfileController.FOLLOW_GLOBAL_ID -> RemoteStringsManager.getString(
            context,
            R.string.thermal_manager_follow_global
        )
        ThermalProfileController.STOCK_CONFIG_ID -> RemoteStringsManager.getString(
            context,
            R.string.thermal_manager_stock_profile
        )
        else -> choice.label
    }
    val source = if (isSystemProfileChoice(choice)) {
        RemoteStringsManager.getString(context, R.string.thermal_manager_profile_system)
    } else {
        RemoteStringsManager.getString(context, R.string.thermal_manager_profile_user)
    }
    return listOfNotNull(
        choice.id,
        title,
        choice.label,
        choice.fileName,
        choice.propertyValue,
        source,
        if (isSystemProfileChoice(choice)) "system" else "user"
    ).joinToString(" ").lowercase(Locale.ROOT)
}

@Composable
private fun profileChoiceTitle(choice: ThermalConfigChoice): String {
    return when (choice.id) {
        ThermalProfileController.FOLLOW_GLOBAL_ID -> dynamicStringResource(R.string.thermal_manager_follow_global)
        ThermalProfileController.STOCK_CONFIG_ID -> dynamicStringResource(R.string.thermal_manager_stock_profile)
        else -> choice.label
    }
}

private fun choiceForConfig(
    configId: String,
    choicesById: Map<String, ThermalConfigChoice>,
    includeFollowGlobal: Boolean
): ThermalConfigChoice {
    val normalized = ThermalProfileController.normalizeConfigId(configId)
    val id = when {
        includeFollowGlobal && normalized.isBlank() -> ThermalProfileController.FOLLOW_GLOBAL_ID
        normalized.isBlank() -> ThermalProfileController.STOCK_CONFIG_ID
        else -> normalized.substringAfterLast('/')
    }
    choicesById[id]?.let { return it }

    return when {
        id == ThermalProfileController.FOLLOW_GLOBAL_ID -> ThermalConfigChoice(
            id = id,
            label = id,
            fileName = null,
            propertyValue = null,
            builtIn = true,
            source = ThermalProfileController.PROFILE_SOURCE_SYSTEM
        )
        id == ThermalProfileController.STOCK_CONFIG_ID -> ThermalConfigChoice(
            id = id,
            label = id,
            fileName = "thermal_info_config.json",
            propertyValue = "thermal_info_config.json",
            builtIn = true,
            source = ThermalProfileController.PROFILE_SOURCE_SYSTEM
        )
        else -> ThermalConfigChoice(
            id = id,
            label = ThermalProfileController.displayName(id),
            fileName = id,
            propertyValue = null,
            builtIn = false,
            source = ThermalProfileController.PROFILE_SOURCE_USER
        )
    }
}

@Composable
private fun EditorHeaderCard(
    source: String,
    profileName: String,
    onProfileNameChange: (String) -> Unit,
    fileNameInput: String,
    onFileNameInputChange: (String) -> Unit,
    selectedTab: ThermalEditorTab,
    onTabSelected: (ThermalEditorTab) -> Unit,
    onSave: () -> Unit,
    onSaveAndApply: () -> Unit
) {
    SettingsGroupCard(title = dynamicStringResource(R.string.thermal_manager_editor_title)) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = dynamicStringResource(R.string.thermal_manager_source, source),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = profileName,
                onValueChange = onProfileNameChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(dynamicStringResource(R.string.thermal_manager_profile_name)) }
            )
            OutlinedTextField(
                value = fileNameInput,
                onValueChange = onFileNameInputChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(dynamicStringResource(R.string.thermal_manager_file_name_optional)) },
                supportingText = { Text(dynamicStringResource(R.string.thermal_manager_file_name_help)) }
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ThermalEditorTab.values().forEach { tab ->
                    val selected = tab == selectedTab
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onTabSelected(tab) },
                        shape = RoundedCornerShape(8.dp),
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = dynamicStringResource(tab.labelRes),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onSave,
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp)
                ) {
                    Icon(Icons.Rounded.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = dynamicStringResource(R.string.thermal_manager_save),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Button(
                    onClick = onSaveAndApply,
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp)
                ) {
                    Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = dynamicStringResource(R.string.thermal_manager_save_apply),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun ThermalParamTreeCard(
    branches: List<ThermalParamTreeNode>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    visibleCount: Int,
    totalCount: Int,
    expandedNodeIds: Set<String>,
    forceExpanded: Boolean,
    onToggleNode: (String) -> Unit,
    onParamClick: (ThermalConfigParam) -> Unit,
    onInfoClick: (ThermalConfigParam) -> Unit
) {
    SettingsGroupCard(title = dynamicStringResource(R.string.thermal_manager_editor_tree_title)) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ThermalCompactSearchField(
                query = searchQuery,
                onQueryChange = onSearchQueryChange,
                hint = dynamicStringResource(R.string.thermal_manager_search_hint)
            )
            Text(
                text = dynamicStringResource(R.string.thermal_manager_search_count, visibleCount, totalCount),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }

        if (branches.isEmpty()) {
            Text(
                text = dynamicStringResource(R.string.thermal_manager_editor_tree_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
        } else {
            Column(modifier = Modifier.padding(bottom = 6.dp)) {
                branches.forEach { node ->
                    ThermalTreeNodeRow(
                        node = node,
                        depth = 0,
                        expandedNodeIds = expandedNodeIds,
                        forceExpanded = forceExpanded,
                        onToggleNode = onToggleNode,
                        onParamClick = onParamClick,
                        onInfoClick = onInfoClick
                    )
                }
            }
        }
    }
}

@Composable
private fun ThermalTreeNodeRow(
    node: ThermalParamTreeNode,
    depth: Int,
    expandedNodeIds: Set<String>,
    forceExpanded: Boolean,
    onToggleNode: (String) -> Unit,
    onParamClick: (ThermalConfigParam) -> Unit,
    onInfoClick: (ThermalConfigParam) -> Unit
) {
    val expanded = forceExpanded || node.id in expandedNodeIds
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggleNode(node.id) }
                .padding(start = (12 + depth * 14).dp, end = 10.dp, top = 7.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = node.title,
                style = if (depth == 0) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
                color = if (depth == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = node.leafCount.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.fillMaxWidth()) {
                node.children.forEach { child ->
                    ThermalTreeNodeRow(
                        node = child,
                        depth = depth + 1,
                        expandedNodeIds = expandedNodeIds,
                        forceExpanded = forceExpanded,
                        onToggleNode = onToggleNode,
                        onParamClick = onParamClick,
                        onInfoClick = onInfoClick
                    )
                }
                node.params.forEach { param ->
                    ThermalTreeParamRow(
                        param = param,
                        depth = depth + 1,
                        onClick = { onParamClick(param) },
                        onInfoClick = { onInfoClick(param) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ThermalTreeParamRow(
    param: ThermalConfigParam,
    depth: Int,
    onClick: () -> Unit,
    onInfoClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = (20 + depth * 14).dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(end = 6.dp)
                .size(16.dp)
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = param.key,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(6.dp))
                FieldBadge(param)
            }
            Text(
                text = param.value.previewValue(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onInfoClick, modifier = Modifier.size(36.dp)) {
            Icon(
                Icons.Rounded.Info,
                contentDescription = dynamicStringResource(R.string.thermal_manager_info),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun ThermalParamEditDialog(
    param: ThermalConfigParam,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var draftValue by remember(param.id, param.value) { mutableStateOf(param.value) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
        title = {
            Text(
                text = param.key,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = param.help,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = param.displayPath,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FieldBadge(param)
                if (param.kind == ThermalValueKind.BOOLEAN) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = draftValue,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Switch(
                            checked = draftValue.equals("true", ignoreCase = true),
                            onCheckedChange = { draftValue = it.toString() }
                        )
                    }
                } else {
                    OutlinedTextField(
                        value = draftValue,
                        onValueChange = { draftValue = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = if (param.kind == ThermalValueKind.ARRAY) 132.dp else 56.dp),
                        singleLine = param.kind != ThermalValueKind.ARRAY,
                        minLines = if (param.kind == ThermalValueKind.ARRAY) 5 else 1,
                        maxLines = if (param.kind == ThermalValueKind.ARRAY) 12 else 1,
                        label = { Text(param.valueLabel) }
                    )
                }
            }
        },
        confirmButton = {
            IconButton(onClick = { onSave(draftValue) }) {
                Icon(Icons.Rounded.Check, contentDescription = dynamicStringResource(R.string.btn_save))
            }
        },
        dismissButton = {
            IconButton(onClick = onDismiss) {
                Icon(Icons.Rounded.Close, contentDescription = dynamicStringResource(R.string.btn_cancel))
            }
        }
    )
}

@Composable
private fun FieldBadge(param: ThermalConfigParam) {
    val color = when {
        param.throttlingField -> MaterialTheme.colorScheme.errorContainer
        param.knownField -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val textColor = when {
        param.throttlingField -> MaterialTheme.colorScheme.onErrorContainer
        param.knownField -> MaterialTheme.colorScheme.onTertiaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        color = color,
        contentColor = textColor,
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            text = if (param.throttlingField) {
                dynamicStringResource(R.string.thermal_manager_badge_throttle)
            } else if (param.knownField) {
                dynamicStringResource(R.string.thermal_manager_badge_known)
            } else {
                dynamicStringResource(R.string.thermal_manager_badge_unknown)
            },
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
        )
    }
}

private class ThermalConfigStore(private val context: Context) {
    fun readVendorConfig(): String = loadConfigWithIncludes(File(VENDOR_THERMAL_CONFIG), mutableSetOf()).toString(4)

    fun listSavedConfigs(): List<SavedThermalConfig> {
        val dir = ensureConfigDir()
        return dir.listFiles { file -> file.isFile && file.name.endsWith(".json", ignoreCase = true) }
            ?.filterNot { it.name == ThermalProfileController.MAP_FILE_NAME || it.name == ThermalProfileController.PROFILE_METADATA_FILE_NAME }
            ?.sortedByDescending { it.lastModified() }
            ?.map { file ->
                val metadata = ThermalProfileController.profileMetadata(file.name)
                val source = metadata?.source ?: if (ThermalProfileController.isVendorPreset(file.name)) {
                    ThermalProfileController.PROFILE_SOURCE_SYSTEM
                } else {
                    ThermalProfileController.PROFILE_SOURCE_USER
                }
                SavedThermalConfig(
                    name = file.name,
                    displayName = metadata?.displayName?.takeIf { it.isNotBlank() } ?: file.name.removeSuffix(".json"),
                    source = source,
                    file = file,
                    sizeText = formatBytes(file.length()),
                    locked = source == ThermalProfileController.PROFILE_SOURCE_SYSTEM
                )
            }
            .orEmpty()
    }

    fun saveConfig(fileName: String, json: String, displayName: String): File {
        if (ThermalProfileController.isVendorPreset(fileName)) {
            error(context.getString(R.string.thermal_manager_vendor_read_only))
        }

        val dir = ensureConfigDir()
        val file = File(dir, fileName)
        file.writeText(json)
        file.setReadable(true, false)
        file.setWritable(true, true)
        ThermalProfileController.writeUserProfileMetadata(file.name, displayName, file.absolutePath)
        return file
    }

    fun readUri(uri: Uri): String = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        ?: error(context.getString(R.string.thermal_manager_error_read_selected_file))

    fun writeUri(uri: Uri, text: String) {
        context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(text) }
            ?: error(context.getString(R.string.thermal_manager_error_open_export_target))
    }

    fun displayName(uri: Uri): String? {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) return cursor.getString(index)
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/')
    }

    private fun ensureConfigDir(): File {
        val dir = File(ThermalProfileController.CONFIG_DIR)
        if (!dir.exists() && !dir.mkdirs()) {
            error(context.getString(R.string.thermal_manager_error_create_dir, ThermalProfileController.CONFIG_DIR))
        }
        dir.setReadable(true, false)
        dir.setExecutable(true, false)
        return dir
    }

    private fun loadConfigWithIncludes(file: File, loadedPaths: MutableSet<String>): JSONObject {
        val normalizedPath = file.absolutePath
        if (!loadedPaths.add(normalizedPath)) {
            error(context.getString(R.string.thermal_manager_error_circular_include, normalizedPath))
        }

        val root = parseThermalJsonObject(context, file.readText())
        val includes = root.optJSONArray("Include") ?: return root
        for (index in 0 until includes.length()) {
            val includeName = includes.optString(index).takeIf { it.isNotBlank() } ?: continue
            val includeFile = if (includeName.startsWith('/')) {
                File(includeName)
            } else {
                File(File(VENDOR_THERMAL_CONFIG).parentFile, includeName)
            }
            val included = loadConfigWithIncludes(includeFile, loadedPaths)
            mergeArrayByName(root, included, "Sensors")
            mergeArrayByName(root, included, "CoolingDevices")
            mergeArrayByName(root, included, "PowerRails")
            mergeStats(root, included)
            mergeLogInfo(root, included)
        }
        return root
    }
}

private fun parseThermalJsonObject(context: Context, text: String): JSONObject {
    val parsed = parseThermalJsonValue(text)
    return parsed as? JSONObject ?: error(context.getString(R.string.thermal_manager_error_root_json_object))
}

private fun parseThermalJsonValue(text: String): Any {
    return JSONTokener(cleanThermalJson(text)).nextValue()
}

private fun cleanThermalJson(text: String): String {
    return removeTrailingJsonCommas(insertMissingJsonArrayCommas(removeJsonComments(text)))
}

private fun removeJsonComments(text: String): String {
    val output = StringBuilder(text.length)
    var index = 0
    var inString = false
    var escaping = false

    while (index < text.length) {
        val char = text[index]
        if (inString) {
            output.append(char)
            if (escaping) {
                escaping = false
            } else if (char == '\\') {
                escaping = true
            } else if (char == '"') {
                inString = false
            }
            index++
            continue
        }

        if (char == '"') {
            inString = true
            output.append(char)
            index++
            continue
        }

        if (char == '/' && index + 1 < text.length) {
            val next = text[index + 1]
            if (next == '/') {
                index += 2
                while (index < text.length && text[index] != '\n' && text[index] != '\r') {
                    index++
                }
                continue
            }
            if (next == '*') {
                index += 2
                while (index + 1 < text.length && !(text[index] == '*' && text[index + 1] == '/')) {
                    if (text[index] == '\n' || text[index] == '\r') output.append(text[index])
                    index++
                }
                index = (index + 2).coerceAtMost(text.length)
                continue
            }
        }

        output.append(char)
        index++
    }

    return output.toString()
}

private fun removeTrailingJsonCommas(text: String): String {
    val output = StringBuilder(text.length)
    var index = 0
    var inString = false
    var escaping = false

    while (index < text.length) {
        val char = text[index]
        if (inString) {
            output.append(char)
            if (escaping) {
                escaping = false
            } else if (char == '\\') {
                escaping = true
            } else if (char == '"') {
                inString = false
            }
            index++
            continue
        }

        if (char == '"') {
            inString = true
            output.append(char)
            index++
            continue
        }

        if (char == ',') {
            var lookahead = index + 1
            while (lookahead < text.length && text[lookahead].isWhitespace()) {
                lookahead++
            }
            if (lookahead < text.length && (text[lookahead] == '}' || text[lookahead] == ']')) {
                index++
                continue
            }
        }

        output.append(char)
        index++
    }

    return output.toString()
}

private fun insertMissingJsonArrayCommas(text: String): String {
    val output = StringBuilder(text.length)
    val containerStack = mutableListOf<Char>()
    var index = 0
    var inString = false
    var escaping = false

    while (index < text.length) {
        val char = text[index]
        if (inString) {
            output.append(char)
            if (escaping) {
                escaping = false
            } else if (char == '\\') {
                escaping = true
            } else if (char == '"') {
                inString = false
            }
            index++
            continue
        }

        if (char == '"') {
            inString = true
            output.append(char)
            index++
            continue
        }

        when (char) {
            '[', '{' -> containerStack.add(char)
            ']' -> if (containerStack.lastOrNull() == '[') containerStack.removeAt(containerStack.lastIndex)
            '}' -> if (containerStack.lastOrNull() == '{') containerStack.removeAt(containerStack.lastIndex)
        }

        if (char.isWhitespace() && containerStack.lastOrNull() == '[' && shouldInsertArrayComma(text, index)) {
            output.append(',')
        }

        output.append(char)
        index++
    }

    return output.toString()
}

private fun shouldInsertArrayComma(text: String, whitespaceIndex: Int): Boolean {
    var previousIndex = whitespaceIndex - 1
    while (previousIndex >= 0 && text[previousIndex].isWhitespace()) previousIndex--
    if (previousIndex < 0 || !isJsonValueEnd(text[previousIndex])) return false

    var nextIndex = whitespaceIndex + 1
    while (nextIndex < text.length && text[nextIndex].isWhitespace()) nextIndex++
    if (nextIndex >= text.length || !isJsonValueStart(text[nextIndex])) return false

    return true
}

private fun isJsonValueStart(char: Char): Boolean {
    return char == '"' || char == '{' || char == '[' || char == '-' || char.isDigit() ||
        char == 't' || char == 'f' || char == 'n'
}

private fun isJsonValueEnd(char: Char): Boolean {
    return char == '"' || char == '}' || char == ']' || char.isDigit() ||
        char == 'e' || char == 'E' || char == 'l'
}

private fun mergeArrayByName(target: JSONObject, source: JSONObject, key: String) {
    val sourceArray = source.optJSONArray(key) ?: return
    val targetArray = target.optJSONArray(key) ?: JSONArray().also { target.put(key, it) }
    val names = mutableSetOf<String>()
    for (index in 0 until targetArray.length()) {
        targetArray.optJSONObject(index)?.optString("Name")?.takeIf { it.isNotBlank() }?.let { names += it }
    }
    for (index in 0 until sourceArray.length()) {
        val item = sourceArray.optJSONObject(index) ?: continue
        val name = item.optString("Name")
        if (name.isBlank() || name !in names) {
            targetArray.put(JSONObject(item.toString()))
            if (name.isNotBlank()) names += name
        }
    }
}

private fun mergeStats(target: JSONObject, source: JSONObject) {
    val sourceStats = source.optJSONObject("Stats") ?: return
    val targetStats = target.optJSONObject("Stats") ?: JSONObject(sourceStats.toString()).also {
        target.put("Stats", it)
        return
    }
    val targetSensors = targetStats.optJSONObject("Sensors") ?: JSONObject().also { targetStats.put("Sensors", it) }
    val sourceSensors = sourceStats.optJSONObject("Sensors") ?: return
    mergeArrayByName(targetSensors, sourceSensors, "RecordWithThreshold")
}

private fun mergeLogInfo(target: JSONObject, source: JSONObject) {
    val sourceLogInfo = source.optJSONObject("LogInfo") ?: return
    val targetLogInfo = target.optJSONObject("LogInfo") ?: JSONObject(sourceLogInfo.toString()).also {
        target.put("LogInfo", it)
        return
    }
    mergeArrayByName(targetLogInfo, sourceLogInfo, "ExcludedPowerRailsLog")
}

private data class SavedThermalConfig(
    val name: String,
    val displayName: String,
    val source: String,
    val file: File,
    val sizeText: String,
    val locked: Boolean
)

private data class ImportedThermalConfig(
    val file: File,
    val displayName: String,
    val json: String
)

private data class ThermalManagedApp(
    val label: String,
    val packageName: String,
    val icon: Drawable?,
    val isSystem: Boolean
)

private data class ThermalParamTreeNode(
    val id: String,
    val title: String,
    val children: List<ThermalParamTreeNode> = emptyList(),
    val params: List<ThermalConfigParam> = emptyList()
) {
    val leafCount: Int
        get() = params.size + children.sumOf { it.leafCount }
}

private sealed interface ProfilePickerTarget {
    object Global : ProfilePickerTarget
    object TileQueue : ProfilePickerTarget
    data class Package(val app: ThermalManagedApp) : ProfilePickerTarget
}

private suspend fun loadThermalManagedApps(context: Context): List<ThermalManagedApp> =
    withContext(Dispatchers.IO) {
        val packageManager = context.packageManager
        val assignedPackages = ThermalProfileController.readProfileMap().packageConfigs.keys
        val installedPackages = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
            .map { it.packageName }
        val assignedSet = assignedPackages.toSet()

        (installedPackages + assignedPackages).distinct().mapNotNull { packageName ->
            runCatching {
                val info = packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
                val label = info.loadLabel(packageManager)?.toString()?.takeIf { it.isNotBlank() }
                    ?: packageName
                ThermalManagedApp(
                    label = label,
                    packageName = packageName,
                    icon = info.loadIcon(packageManager),
                    isSystem = info.isSystemApplication()
                )
            }.getOrNull()
        }.sortedWith(
            compareByDescending<ThermalManagedApp> { assignedSet.contains(it.packageName) }
                .thenBy { it.label.lowercase(Locale.getDefault()) }
                .thenBy { it.packageName }
        )
    }

private fun ApplicationInfo.isSystemApplication(): Boolean {
    return (flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
        (flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
}

private data class ThermalConfigParam(
    val id: String,
    val key: String,
    val section: String,
    val owner: String,
    val targetName: String?,
    val displayPath: String,
    val path: List<JsonPathToken>,
    val kind: ThermalValueKind,
    val value: String,
    val valueLabel: String,
    val help: String,
    val knownField: Boolean,
    val throttlingField: Boolean,
    val searchText: String
)

private enum class ThermalValueKind {
    STRING,
    NUMBER,
    BOOLEAN,
    ARRAY,
    NULL
}

private enum class ThermalEditorTab(val labelRes: Int) {
    SOC(R.string.thermal_manager_editor_tab_soc),
    BATTERY(R.string.thermal_manager_editor_tab_battery),
    ALL(R.string.thermal_manager_editor_tab_all)
}

private val THERMAL_EDITOR_SOC_TARGETS = setOf(
    "VIRTUAL-SKIN",
    "VIRTUAL-SKIN-HINT",
    "VIRTUAL-SKIN-CPU-LIGHT-ODPM",
    "VIRTUAL-SKIN-CPU-MID",
    "VIRTUAL-SKIN-CPU-HIGH",
    "VIRTUAL-SKIN-CPU-GPU",
    "VIRTUAL-SKIN-SOC",
    "VIRTUAL-SKIN-GPU",
    "VIRTUAL-SKIN-SPEAKER"
)

private val THERMAL_EDITOR_BATTERY_TARGETS = setOf(
    "VIRTUAL-SKIN-CHARGE-PERSIST",
    "VIRTUAL-SKIN-CHARGE-WIRED",
    "VIRTUAL-SKIN-CHARGE",
    "VIRTUAL-SKIN-CHARGE-WLC"
)

private val ThermalConfigParam.treeOwner: String
    get() = targetName ?: owner

private fun ThermalConfigParam.matchesThermalTargets(targets: Set<String>): Boolean {
    val target = targetName?.uppercase(Locale.US) ?: return false
    return target in targets
}

private sealed interface JsonPathToken {
    data class Key(val value: String) : JsonPathToken
    data class Index(val value: Int) : JsonPathToken
}

private fun buildEditableParams(context: Context, root: JSONObject): List<ThermalConfigParam> {
    val params = mutableListOf<ThermalConfigParam>()
    collectParams(context, root, emptyList(), null, null, null, params)
    return params.sortedWith(compareBy<ThermalConfigParam> { sectionRank(it.section) }.thenBy { it.owner }.thenBy { it.displayPath })
}

private fun buildThermalParamTree(context: Context, params: List<ThermalConfigParam>): List<ThermalParamTreeNode> {
    return params
        .groupBy { it.section }
        .toList()
        .sortedWith(compareBy<Pair<String, List<ThermalConfigParam>>> { sectionRank(it.first) }.thenBy { it.first })
        .map { (section, sectionParams) ->
            val owners = sectionParams
                .groupBy { it.treeOwner }
                .toList()
                .sortedBy { it.first.lowercase(Locale.ROOT) }
                .map { (owner, ownerParams) ->
                    ThermalParamTreeNode(
                        id = "section:$section/owner:$owner",
                        title = owner,
                        params = ownerParams.sortedBy { it.key.lowercase(Locale.ROOT) }
                    )
                }
            ThermalParamTreeNode(
                id = "section:$section",
                title = section.toSectionLabel(context),
                children = owners
            )
        }
}

private fun defaultExpandedTreeNodeIds(params: List<ThermalConfigParam>): Set<String> {
    return params.map { "section:${it.section}" }.toSet()
}

private fun collectParams(
    context: Context,
    value: Any?,
    path: List<JsonPathToken>,
    sectionHint: String?,
    ownerHint: String?,
    targetHint: String?,
    out: MutableList<ThermalConfigParam>
) {
    when (value) {
        is JSONObject -> {
            val section = sectionHint ?: path.firstKeyOrNull() ?: "Root"
            val objectName = value.optString("Name").takeIf { it.isNotBlank() }
            val owner = objectName
                ?: value.optString("CdevRequest").takeIf { it.isNotBlank() }
                ?: value.optString("Mode").takeIf { it.isNotBlank() }
                ?: ownerHint
            val targetName = objectName ?: targetHint
            value.keyList().forEach { key ->
                collectParams(context, value.opt(key), path + JsonPathToken.Key(key), section, owner, targetName, out)
            }
        }

        is JSONArray -> {
            if (value.isPrimitiveArray()) {
                addParam(context, value, path, sectionHint, ownerHint, targetHint, out)
            } else {
                for (index in 0 until value.length()) {
                    collectParams(context, value.opt(index), path + JsonPathToken.Index(index), sectionHint, ownerHint, targetHint, out)
                }
            }
        }

        else -> addParam(context, value, path, sectionHint, ownerHint, targetHint, out)
    }
}

private fun addParam(
    context: Context,
    value: Any?,
    path: List<JsonPathToken>,
    sectionHint: String?,
    ownerHint: String?,
    targetHint: String?,
    out: MutableList<ThermalConfigParam>
) {
    val key = path.lastKeyOrNull() ?: return
    if (key == "Name" && path.size > 2) return

    val section = sectionHint ?: path.firstKeyOrNull() ?: "Root"
    val owner = ownerHint ?: section
    val kind = value.toThermalValueKind()
    val displayPath = path.toDisplayPath()
    val help = helpForField(context, key, displayPath)
    val known = isKnownThermalField(key, displayPath)
    val throttling = isThrottlingField(key, displayPath)
    val textValue = value.toEditorText()
    val valueLabel = when (kind) {
        ThermalValueKind.ARRAY -> context.getString(R.string.thermal_manager_value_json_array)
        ThermalValueKind.BOOLEAN -> context.getString(R.string.thermal_manager_value_boolean)
        ThermalValueKind.NUMBER -> context.getString(R.string.thermal_manager_value_number)
        ThermalValueKind.NULL -> context.getString(R.string.thermal_manager_value_null)
        ThermalValueKind.STRING -> context.getString(R.string.thermal_manager_value_text)
    }
    out += ThermalConfigParam(
        id = displayPath,
        key = key,
        section = section,
        owner = owner,
        targetName = targetHint,
        displayPath = displayPath,
        path = path,
        kind = kind,
        value = textValue,
        valueLabel = valueLabel,
        help = help,
        knownField = known,
        throttlingField = throttling,
        searchText = listOfNotNull(section, section.toSectionLabel(context), owner, targetHint, key, displayPath, textValue, help)
            .joinToString(" ")
            .lowercase(Locale.ROOT)
    )
}

private fun resolveConfigFileName(displayName: String, fileNameInput: String): String? {
    val source = fileNameInput.trim().ifBlank { displayName.trim() }
    return sanitizeConfigName(transliterateForFileName(source))
}

private fun sanitizeConfigName(rawName: String): String? {
    val withoutJson = rawName.trim().removeSuffix(".json")
    if (withoutJson.isBlank() || withoutJson == "." || withoutJson == "..") return null

    val cleaned = buildString(withoutJson.length) {
        withoutJson.forEach { char ->
            when {
                char in 'a'..'z' || char in '0'..'9' -> append(char)
                char in 'A'..'Z' -> append(char.lowercaseChar())
                char == '.' || char == '-' -> append(char)
                char == '_' || char.isWhitespace() -> append('_')
            }
        }
    }
        .replace(Regex("_+"), "_")
        .trim('_', '.', '-')

    if (cleaned.isBlank() || cleaned == "." || cleaned == "..") return null
    return "$cleaned.json"
}

private fun transliterateForFileName(rawName: String): String {
    val normalized = Normalizer.normalize(rawName, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
    return buildString(normalized.length) {
        normalized.forEach { char ->
            append(FILE_NAME_TRANSLIT[char.lowercaseChar()] ?: char)
        }
    }
}

private fun String.previewValue(): String {
    val compact = replace('\n', ' ').replace(Regex("\\s+"), " ").trim()
    return if (compact.length <= 96) compact else compact.take(93) + "..."
}

private fun parseEditedValue(context: Context, param: ThermalConfigParam): Any? {
    val text = param.value.trim()
    return when (param.kind) {
        ThermalValueKind.BOOLEAN -> when (text.lowercase(Locale.ROOT)) {
            "true" -> true
            "false" -> false
            else -> error(context.getString(R.string.thermal_manager_error_expected_bool, param.displayPath))
        }

        ThermalValueKind.NUMBER -> {
            if (text.isBlank()) error(context.getString(R.string.thermal_manager_error_empty_number, param.displayPath))
            if (text.contains('.') || text.contains('e', ignoreCase = true)) text.toDouble() else text.toLong()
        }

        ThermalValueKind.ARRAY -> {
            val parsed = parseThermalJsonValue(text)
            if (parsed !is JSONArray) error(context.getString(R.string.thermal_manager_error_expected_json_array, param.displayPath))
            if (requiresSeverityArray(param.key) && parsed.length() != 7) {
                error(context.getString(R.string.thermal_manager_error_severity_array_size, param.displayPath))
            }
            parsed
        }

        ThermalValueKind.NULL -> if (text.equals("null", ignoreCase = true)) JSONObject.NULL else text
        ThermalValueKind.STRING -> text
    }
}

private fun setJsonValue(root: JSONObject, path: List<JsonPathToken>, value: Any?) {
    var cursor: Any = root
    path.dropLast(1).forEach { token ->
        cursor = when (token) {
            is JsonPathToken.Key -> (cursor as JSONObject).get(token.value)
            is JsonPathToken.Index -> (cursor as JSONArray).get(token.value)
        }
    }
    when (val token = path.last()) {
        is JsonPathToken.Key -> (cursor as JSONObject).put(token.value, value)
        is JsonPathToken.Index -> (cursor as JSONArray).put(token.value, value)
    }
}

private fun validateImportedConfig(context: Context, reference: JSONObject, imported: JSONObject): List<String> {
    val errors = mutableListOf<String>()
    validateObjectKeys(context, reference, imported, context.getString(R.string.thermal_manager_json_root), errors)
    return errors
}

private fun validateObjectKeys(context: Context, reference: JSONObject, imported: JSONObject, prefix: String, errors: MutableList<String>) {
    reference.keyList().forEach { key ->
        if (!imported.has(key)) {
            errors += context.getString(R.string.thermal_manager_import_missing_key, "$prefix.$key")
            return@forEach
        }
        val referenceValue = reference.opt(key)
        val importedValue = imported.opt(key)
        when {
            referenceValue is JSONObject && importedValue is JSONObject -> {
                validateObjectKeys(context, referenceValue, importedValue, "$prefix.$key", errors)
            }

            referenceValue is JSONArray && importedValue is JSONArray -> {
                validateArrayKeys(context, referenceValue, importedValue, "$prefix.$key", errors)
            }
        }
    }
}

private fun validateArrayKeys(context: Context, reference: JSONArray, imported: JSONArray, prefix: String, errors: MutableList<String>) {
    val identityKey = reference.firstIdentityKey()
    if (identityKey != null) {
        for (index in 0 until reference.length()) {
            val referenceObject = reference.optJSONObject(index) ?: continue
            val identity = referenceObject.optString(identityKey).takeIf { it.isNotBlank() } ?: continue
            val importedObject = imported.findObjectBy(identityKey, identity)
            if (importedObject == null) {
                errors += context.getString(R.string.thermal_manager_import_missing_entry, "$prefix[$identityKey=$identity]")
            } else {
                validateObjectKeys(context, referenceObject, importedObject, "$prefix[$identity]", errors)
            }
        }
    } else {
        val referenceObject = reference.firstObjectOrNull() ?: return
        val importedObject = imported.firstObjectOrNull()
        if (importedObject == null) {
            errors += context.getString(R.string.thermal_manager_import_missing_object_schema, "$prefix[]")
        } else {
            validateObjectKeys(context, referenceObject, importedObject, "$prefix[]", errors)
        }
    }
}

private fun helpForField(context: Context, key: String, path: String): String {
    return FIELD_HELP_RES[key]?.let(context::getString)
        ?: when {
            path.contains("PIDInfo") -> context.getString(R.string.thermal_manager_field_help_pidinfo_fallback)
            path.contains("BindedCdevInfo") -> context.getString(R.string.thermal_manager_field_help_binded_cdev_fallback)
            path.contains("Profile") -> context.getString(R.string.thermal_manager_field_help_profile_fallback)
            path.contains("Stats") -> context.getString(R.string.thermal_manager_field_help_stats_fallback)
            else -> context.getString(R.string.thermal_manager_field_help_unknown)
        }
}

private fun isKnownThermalField(key: String, path: String): Boolean {
    return FIELD_HELP_RES.containsKey(key) || path.contains("PIDInfo") || path.contains("BindedCdevInfo") || path.contains("Profile") || path.contains("Stats")
}

private fun isThrottlingField(key: String, path: String): Boolean {
    return key in THROTTLING_FIELDS || key.startsWith("K_") || path.contains("PIDInfo") || path.contains("BindedCdevInfo")
}

private fun requiresSeverityArray(key: String): Boolean {
    return key in setOf(
        "HotThreshold",
        "HotHysteresis",
        "ColdThreshold",
        "ColdHysteresis",
        "LimitInfo",
        "LimitInfoFrequency",
        "CdevCeiling",
        "CdevCeilingFrequency",
        "CdevWeightForPID",
        "K_Po",
        "K_Pu",
        "K_I",
        "K_Io",
        "K_Iu",
        "K_D",
        "I_Max",
        "S_Power",
        "MinAllocPower",
        "MaxAllocPower",
        "I_Cutoff"
    )
}

private fun Any?.toThermalValueKind(): ThermalValueKind = when (this) {
    is JSONArray -> ThermalValueKind.ARRAY
    is Boolean -> ThermalValueKind.BOOLEAN
    is Number -> ThermalValueKind.NUMBER
    null, JSONObject.NULL -> ThermalValueKind.NULL
    else -> ThermalValueKind.STRING
}

private fun Any?.toEditorText(): String = when (this) {
    is JSONArray -> this.toString()
    JSONObject.NULL, null -> "null"
    else -> toString()
}

private fun JSONArray.isPrimitiveArray(): Boolean {
    for (index in 0 until length()) {
        if (opt(index) is JSONObject || opt(index) is JSONArray) return false
    }
    return true
}

private fun JSONArray.firstIdentityKey(): String? {
    val first = firstObjectOrNull() ?: return null
    return listOf("Name", "CdevRequest", "Mode", "LoggingName").firstOrNull { first.optString(it).isNotBlank() }
}

private fun JSONArray.findObjectBy(key: String, value: String): JSONObject? {
    for (index in 0 until length()) {
        val candidate = optJSONObject(index) ?: continue
        if (candidate.optString(key) == value) return candidate
    }
    return null
}

private fun JSONArray.firstObjectOrNull(): JSONObject? {
    for (index in 0 until length()) {
        val candidate = optJSONObject(index)
        if (candidate != null) return candidate
    }
    return null
}

private fun JSONObject.keyList(): List<String> {
    val keys = mutableListOf<String>()
    val iterator = keys()
    while (iterator.hasNext()) keys += iterator.next()
    return keys
}

private fun List<JsonPathToken>.firstKeyOrNull(): String? = firstOrNull { it is JsonPathToken.Key }?.let { (it as JsonPathToken.Key).value }

private fun List<JsonPathToken>.lastKeyOrNull(): String? = lastOrNull { it is JsonPathToken.Key }?.let { (it as JsonPathToken.Key).value }

private fun List<JsonPathToken>.toDisplayPath(): String = buildString {
    this@toDisplayPath.forEach { token ->
        when (token) {
            is JsonPathToken.Key -> {
                if (isNotEmpty()) append('.')
                append(token.value)
            }

            is JsonPathToken.Index -> append('[').append(token.value).append(']')
        }
    }
}

private fun String.toSectionLabel(context: Context): String = when (this) {
    "Root" -> context.getString(R.string.thermal_manager_section_root)
    "Sensors" -> context.getString(R.string.thermal_manager_section_sensors)
    "CoolingDevices" -> context.getString(R.string.thermal_manager_section_cooling_devices)
    "PowerRails" -> context.getString(R.string.thermal_manager_section_power_rails)
    "Stats" -> context.getString(R.string.thermal_manager_section_stats)
    "LogInfo" -> context.getString(R.string.thermal_manager_section_log_info)
    "Include" -> context.getString(R.string.thermal_manager_section_includes)
    else -> this
}

private fun sectionRank(section: String): Int = when (section) {
    "Sensors" -> 0
    "CoolingDevices" -> 1
    "PowerRails" -> 2
    "Stats" -> 3
    "LogInfo" -> 4
    "Include" -> 5
    else -> 10
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val formatter = DecimalFormat("0.#")
    val kib = bytes / 1024.0
    if (kib < 1024) return "${formatter.format(kib)} KiB"
    return "${formatter.format(kib / 1024.0)} MiB"
}

private val THROTTLING_FIELDS = setOf(
    "HotThreshold",
    "HotHysteresis",
    "ColdThreshold",
    "ColdHysteresis",
    "PollingDelay",
    "PassiveDelay",
    "SendCallback",
    "SendPowerHint",
    "PIDInfo",
    "BindedCdevInfo",
    "CdevRequest",
    "LimitInfo",
    "LimitInfoFrequency",
    "CdevCeiling",
    "CdevCeilingFrequency",
    "CdevWeightForPID",
    "MaxReleaseStep",
    "MaxThrottleStep",
    "BindedPowerRail",
    "PowerThreshold",
    "ReleaseLogic",
    "Disabled"
)

private val FIELD_HELP_RES = mapOf(
    "Type" to R.string.thermal_manager_field_help_type,
    "HotThreshold" to R.string.thermal_manager_field_help_hot_threshold,
    "HotHysteresis" to R.string.thermal_manager_field_help_hot_hysteresis,
    "ColdThreshold" to R.string.thermal_manager_field_help_cold_threshold,
    "ColdHysteresis" to R.string.thermal_manager_field_help_cold_hysteresis,
    "Multiplier" to R.string.thermal_manager_field_help_multiplier,
    "PollingDelay" to R.string.thermal_manager_field_help_polling_delay,
    "PassiveDelay" to R.string.thermal_manager_field_help_passive_delay,
    "VirtualSensor" to R.string.thermal_manager_field_help_virtual_sensor,
    "Formula" to R.string.thermal_manager_field_help_formula,
    "Combination" to R.string.thermal_manager_field_help_combination,
    "Coefficient" to R.string.thermal_manager_field_help_coefficient,
    "CoefficientType" to R.string.thermal_manager_field_help_coefficient_type,
    "CombinationType" to R.string.thermal_manager_field_help_combination_type,
    "Offset" to R.string.thermal_manager_field_help_offset,
    "TriggerSensor" to R.string.thermal_manager_field_help_trigger_sensor,
    "SendCallback" to R.string.thermal_manager_field_help_send_callback,
    "SendPowerHint" to R.string.thermal_manager_field_help_send_power_hint,
    "Hidden" to R.string.thermal_manager_field_help_hidden,
    "Version" to R.string.thermal_manager_field_help_version,
    "Profile" to R.string.thermal_manager_field_help_profile,
    "Mode" to R.string.thermal_manager_field_help_mode,
    "PIDInfo" to R.string.thermal_manager_field_help_pid_info,
    "K_Po" to R.string.thermal_manager_field_help_k_po,
    "K_Pu" to R.string.thermal_manager_field_help_k_pu,
    "K_I" to R.string.thermal_manager_field_help_k_i,
    "K_Io" to R.string.thermal_manager_field_help_k_io,
    "K_Iu" to R.string.thermal_manager_field_help_k_iu,
    "K_D" to R.string.thermal_manager_field_help_k_d,
    "I_Max" to R.string.thermal_manager_field_help_i_max,
    "S_Power" to R.string.thermal_manager_field_help_s_power,
    "MinAllocPower" to R.string.thermal_manager_field_help_min_alloc_power,
    "MaxAllocPower" to R.string.thermal_manager_field_help_max_alloc_power,
    "I_Cutoff" to R.string.thermal_manager_field_help_i_cutoff,
    "I_Default" to R.string.thermal_manager_field_help_i_default,
    "I_Default_Pct" to R.string.thermal_manager_field_help_i_default_pct,
    "I_Trend" to R.string.thermal_manager_field_help_i_trend,
    "TranCycle" to R.string.thermal_manager_field_help_tran_cycle,
    "BindedCdevInfo" to R.string.thermal_manager_field_help_binded_cdev_info,
    "CdevRequest" to R.string.thermal_manager_field_help_cdev_request,
    "LimitInfo" to R.string.thermal_manager_field_help_limit_info,
    "LimitInfoFrequency" to R.string.thermal_manager_field_help_limit_info_frequency,
    "CdevCeiling" to R.string.thermal_manager_field_help_cdev_ceiling,
    "CdevCeilingFrequency" to R.string.thermal_manager_field_help_cdev_ceiling_frequency,
    "CdevWeightForPID" to R.string.thermal_manager_field_help_cdev_weight_for_pid,
    "MaxReleaseStep" to R.string.thermal_manager_field_help_max_release_step,
    "MaxThrottleStep" to R.string.thermal_manager_field_help_max_throttle_step,
    "BindedPowerRail" to R.string.thermal_manager_field_help_binded_power_rail,
    "PowerThreshold" to R.string.thermal_manager_field_help_power_threshold,
    "ReleaseLogic" to R.string.thermal_manager_field_help_release_logic,
    "Disabled" to R.string.thermal_manager_field_help_disabled,
    "WritePath" to R.string.thermal_manager_field_help_write_path,
    "State2Power" to R.string.thermal_manager_field_help_state2_power,
    "PowerRail" to R.string.thermal_manager_field_help_power_rail,
    "PowerSampleDelay" to R.string.thermal_manager_field_help_power_sample_delay,
    "PowerSampleCount" to R.string.thermal_manager_field_help_power_sample_count,
    "VirtualRails" to R.string.thermal_manager_field_help_virtual_rails,
    "RecordWithDefaultThreshold" to R.string.thermal_manager_field_help_record_with_default_threshold,
    "RecordWithThreshold" to R.string.thermal_manager_field_help_record_with_threshold,
    "DefaultThresholdEnableAll" to R.string.thermal_manager_field_help_default_threshold_enable_all,
    "Thresholds" to R.string.thermal_manager_field_help_thresholds,
    "LoggingName" to R.string.thermal_manager_field_help_logging_name,
    "Abnormality" to R.string.thermal_manager_field_help_abnormality,
    "Outlier" to R.string.thermal_manager_field_help_outlier,
    "Stuck" to R.string.thermal_manager_field_help_stuck,
    "Monitor" to R.string.thermal_manager_field_help_monitor,
    "TempRange" to R.string.thermal_manager_field_help_temp_range,
    "TempStuck" to R.string.thermal_manager_field_help_temp_stuck,
    "MinPollingCount" to R.string.thermal_manager_field_help_min_polling_count,
    "MinStuckDuration" to R.string.thermal_manager_field_help_min_stuck_duration,
    "RecordVotePerSensor" to R.string.thermal_manager_field_help_record_vote_per_sensor,
    "ExcludedPowerInfo" to R.string.thermal_manager_field_help_excluded_power_info,
    "PredictorInfo" to R.string.thermal_manager_field_help_predictor_info,
    "PredictionWeight" to R.string.thermal_manager_field_help_prediction_weight,
    "Include" to R.string.thermal_manager_field_help_include
)
