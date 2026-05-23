package org.pixel.customparts

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.pixel.customparts.activities.*
import org.pixel.customparts.services.MainActivityTileService
import org.pixel.customparts.ui.RebootBubbleMenuAction
import org.pixel.customparts.ui.TopBarBlurOverlay
import org.pixel.customparts.ui.recordLayer
import org.pixel.customparts.ui.rememberGraphicsLayerRecordingState
import org.pixel.customparts.ui.RebootBubble
import org.pixel.customparts.ui.REBOOT_BUBBLE_CONTENT_BOTTOM_PADDING
import org.pixel.customparts.ui.SettingsGroupCard
import org.pixel.customparts.ui.addons.AddonMainEntry
import org.pixel.customparts.ui.addons.AddonMainEntryRow
import org.pixel.customparts.ui.addons.AddonSettingDef
import org.pixel.customparts.ui.addons.SettingType
import org.pixel.customparts.ui.addons.scanAddonMainEntries
import org.pixel.customparts.utils.RootUtils
import org.pixel.customparts.utils.RemoteStringsManager
import org.pixel.customparts.utils.TileUtils
import org.pixel.customparts.utils.dynamicStringResource
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val darkTheme = isSystemInDarkTheme()
            val context = LocalContext.current

            LaunchedEffect(Unit) {
                try {
                    RemoteStringsManager.initialize(context)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            var rootState by remember { mutableStateOf(if (AppConfig.NEEDS_ROOT_ACCESS) 0 else 1) }

            LaunchedEffect(Unit) {
                if (AppConfig.NEEDS_ROOT_ACCESS) {
                    withContext(Dispatchers.IO) {
                        try {
                            if (RootUtils.hasRootAccess()) {
                                RootUtils.grantPermissions(context)
                                rootState = 1
                            } else {
                                rootState = 2
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            rootState = 2
                        }
                    }
                }
            }

            val colorScheme = if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

            MaterialTheme(colorScheme = colorScheme) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    when (rootState) {
                        0 -> LoadingScreen()
                        1 -> MainDashboard()
                        2 -> NoRootDialog { finishAffinity() }
                    }
                }
            }
        }
    }
}

@Composable
fun LoadingScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
fun NoRootDialog(onExit: () -> Unit) {
    AlertDialog(
        onDismissRequest = { },
        icon = { Icon(Icons.Rounded.Security, contentDescription = null) },
        title = { Text(text = dynamicStringResource(R.string.main_root_title)) },
        text = { Text(dynamicStringResource(R.string.main_root_desc)) },
        confirmButton = {
            TextButton(onClick = onExit) { Text(dynamicStringResource(R.string.btn_exit)) }
        },
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainDashboard() {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val blurState = rememberGraphicsLayerRecordingState()
    val lazyListState = rememberLazyListState()
    val isScrolled by remember { derivedStateOf { lazyListState.canScrollBackward } }

    // Load addon main-menu entries grouped by their "group" field
    var addonGestureEntries by remember { mutableStateOf<List<AddonMainEntry>>(emptyList()) }
    var addonSystemEntries by remember { mutableStateOf<List<AddonMainEntry>>(emptyList()) }
    var addonNetworkEntries by remember { mutableStateOf<List<AddonMainEntry>>(emptyList()) }
    var addonLauncherEntries by remember { mutableStateOf<List<AddonMainEntry>>(emptyList()) }
    var addonCustomGroups by remember { mutableStateOf<List<Pair<String, List<AddonMainEntry>>>>(emptyList()) }
    var addonSearchRootEntries by remember { mutableStateOf<List<AddonMainEntry>>(emptyList()) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var searchFieldFocused by remember { mutableStateOf(false) }
    val hideSearchKeyboard = {
        focusManager.clearFocus(force = true)
        searchFieldFocused = false
    }
    val exitSearch = {
        searchQuery = ""
        hideSearchKeyboard()
    }

    androidx.activity.compose.BackHandler(enabled = searchQuery.isNotBlank() || searchFieldFocused) {
        if (searchFieldFocused) {
            hideSearchKeyboard()
        } else {
            exitSearch()
        }
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val model = scanAddonMainEntries(context)
                val searchModel = scanAddonMainEntries(context, includeTargetActivityEntries = true)
                val entries = model.entries
                addonSearchRootEntries = searchModel.entries
                addonGestureEntries = entries.filterAddonGroup("gesture")
                addonSystemEntries = entries.filterAddonGroup("system")
                addonNetworkEntries = entries.filterAddonGroup("network")
                addonLauncherEntries = entries.filterAddonGroup("launcher")
                addonCustomGroups = entries
                    .groupBy { it.normalizedAddonGroup() }
                    .filterKeys { it !in MAIN_DASHBOARD_KNOWN_ADDON_GROUPS }
                    .map { (group, groupEntries) -> group to groupEntries.sortedForMainDashboard() }
                    .sortedWith(
                        compareByDescending<Pair<String, List<AddonMainEntry>>> { (_, groupEntries) ->
                            groupEntries.maxOfOrNull { it.priority } ?: 0
                        }.thenBy { (group, _) -> addonGroupTitle(context, group) }
                    )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    val showTestThings = remember {
        try {
            Settings.Global.getInt(context.contentResolver, "pixelparts_test_things", 0) == 1
        } catch (_: Throwable) { false }
    }

    val addonSearchEntries = addonSearchRootEntries
        .flatMap { it.flattenAddonTree() }
        .distinctBy { it.addonId + ":" + it.rawId }
    val addonSearchSettingEntries = addonSearchEntries
        .flatMap { it.flattenAddonSettingSearchEntries() }
        .distinctBy { it.uniqueKey }
    val internalStringIndex = remember(context) { collectMainSearchResourceStrings(context) }

    val dashboardSearchItems = buildList {
        add(
            MainDashboardSearchItem(
                title = dynamicStringResource(R.string.donate_title),
                subtitle = dynamicStringResource(R.string.donate_desc_short),
                section = dynamicStringResource(R.string.donate_title),
                keywords = "donate support contribution",
                icon = Icons.Rounded.Favorite,
                iconContainerColor = MaterialTheme.colorScheme.primary,
                iconContentColor = MaterialTheme.colorScheme.onPrimary,
                onClick = { context.startActivity(Intent(context, DonateActivity::class.java)) }
            )
        )
        add(
            MainDashboardSearchItem(
                title = dynamicStringResource(R.string.os_title_activity),
                subtitle = dynamicStringResource(R.string.os_desc_activity),
                section = dynamicStringResource(R.string.main_header_system),
                keywords = "overscroll scroll animation bounce edge " + internalStringIndex.categoryText("overscroll"),
                icon = Icons.Rounded.Animation,
                iconContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                iconContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                onClick = { context.startActivity(Intent(context, OverscrollActivity::class.java)) }
            )
        )
        if (!AppConfig.IS_XPOSED) {
            add(
                MainDashboardSearchItem(
                    title = dynamicStringResource(R.string.display_title),
                    subtitle = dynamicStringResource(R.string.display_desc),
                    section = dynamicStringResource(R.string.main_header_system),
                    keywords = "display color theme palette screen " + internalStringIndex.categoryText("display"),
                    icon = Icons.Rounded.Palette,
                    iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    iconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    onClick = { context.startActivity(Intent(context, DisplaySettingsActivity::class.java)) }
                )
            )
            add(
                MainDashboardSearchItem(
                    title = dynamicStringResource(R.string.app_icons_title),
                    subtitle = dynamicStringResource(R.string.app_icons_summary),
                    section = dynamicStringResource(R.string.main_header_system),
                    keywords = "icons icon manager shapes tint apps " + internalStringIndex.categoryText("app_icons"),
                    icon = Icons.Rounded.Apps,
                    iconContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    iconContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    onClick = { context.startActivity(Intent(context, AppIconsActivity::class.java)) }
                )
            )
        }
        add(
            MainDashboardSearchItem(
                title = dynamicStringResource(R.string.sysui_settings_title),
                subtitle = "Configure SystemUI components",
                section = dynamicStringResource(R.string.main_header_system),
                keywords = "systemui status bar quick settings qs shade lockscreen " + internalStringIndex.categoryText("systemui"),
                icon = Icons.Rounded.SettingsSystemDaydream,
                iconContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                iconContentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                onClick = { context.startActivity(Intent(context, SystemUISettingsActivity::class.java)) }
            )
        )
        add(
            MainDashboardSearchItem(
                title = dynamicStringResource(R.string.launcher_hidden_apps_title),
                subtitle = dynamicStringResource(R.string.launcher_hidden_apps_subtitle),
                section = dynamicStringResource(R.string.launcher_settings_title),
                keywords = "launcher hidden apps hide home nexus " + internalStringIndex.categoryText("launcher"),
                icon = Icons.Rounded.Apps,
                iconContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                iconContentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                onClick = { context.startActivity(Intent(context, HiddenLauncherAppsActivity::class.java)) }
            )
        )
        if (AppConfig.ENABLE_THERMALS) {
            add(
                MainDashboardSearchItem(
                    title = dynamicStringResource(R.string.thermal_manager_title),
                    subtitle = dynamicStringResource(R.string.thermal_manager_desc_activity),
                    section = dynamicStringResource(R.string.main_header_system),
                    keywords = "thermal temperature performance profiles " + internalStringIndex.categoryText("thermal"),
                    icon = Icons.Rounded.Tune,
                    iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    iconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    onClick = { context.startActivity(Intent(context, ThermalConfigManagerActivity::class.java)) }
                )
            )
        }
        if (!AppConfig.IS_XPOSED || showTestThings) {
            add(
                MainDashboardSearchItem(
                    title = dynamicStringResource(R.string.addon_title),
                    subtitle = dynamicStringResource(R.string.addon_desc),
                    section = dynamicStringResource(R.string.main_header_system),
                    keywords = "addons modules extensions hooks " + internalStringIndex.categoryText("addons"),
                    icon = Icons.Rounded.Extension,
                    iconContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    iconContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    onClick = { context.startActivity(Intent(context, AddonManagerActivity::class.java)) }
                )
            )
        }
        if (showTestThings) {
            add(
                MainDashboardSearchItem(
                    title = dynamicStringResource(R.string.test_things_title),
                    subtitle = dynamicStringResource(R.string.test_things_desc),
                    section = dynamicStringResource(R.string.test_things_title),
                    keywords = "test debug experimental " + internalStringIndex.categoryText("test"),
                    icon = Icons.Rounded.Science,
                    iconContainerColor = MaterialTheme.colorScheme.errorContainer,
                    iconContentColor = MaterialTheme.colorScheme.onErrorContainer,
                    onClick = { context.startActivity(Intent(context, TestActivity::class.java)) }
                )
            )
        }
        addonSearchEntries.forEach { entry ->
            add(
                MainDashboardSearchItem(
                    title = entry.title,
                    subtitle = entry.subtitle,
                    section = addonGroupTitle(context, entry.normalizedAddonGroup()),
                    keywords = entry.addonSearchText(),
                    icon = Icons.Rounded.Extension,
                    iconContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    iconContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    addonEntry = entry,
                    onClick = { openAddonSearchEntry(context, entry) }
                )
            )
        }
        addonSearchSettingEntries.forEach { settingEntry ->
            val setting = settingEntry.setting
            add(
                MainDashboardSearchItem(
                    title = setting.title.ifBlank { setting.key },
                    subtitle = listOf(setting.description, settingEntry.entry.title)
                        .filter { it.isNotBlank() }
                        .joinToString(" - "),
                    section = addonGroupTitle(context, settingEntry.entry.normalizedAddonGroup()),
                    keywords = settingEntry.searchText(),
                    icon = Icons.Rounded.Tune,
                    iconContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    iconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    addonEntry = settingEntry.entry,
                    onClick = { openAddonSearchEntry(context, settingEntry.entry) }
                )
            )
        }
    }
    val searchResults = searchDashboardItems(searchQuery, dashboardSearchItems)

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentWindowInsets = WindowInsets.navigationBars, // Only respect nav bars for Scaffold layout, let content handle status bar
        floatingActionButton = {
            RebootBubble(
                extraActions = listOf(
                    RebootBubbleMenuAction(
                        icon = Icons.Rounded.Add,
                        label = dynamicStringResource(R.string.main_add_tile),
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        onClick = {
                            TileUtils.requestAddTileService(
                                context,
                                MainActivityTileService::class.java,
                                R.string.main_title,
                                R.drawable.ic_homepage_pixel_extra_parts
                            )
                        }
                    )
                )
            )
        },
        topBar = {
            LargeTopAppBar(
                scrollBehavior = scrollBehavior,
                title = {
                    Column {
                        Text(dynamicStringResource(R.string.main_title), fontWeight = FontWeight.Bold)

                        Text(
                            dynamicStringResource(R.string.main_desc),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (searchQuery.isNotBlank()) {
                            exitSearch()
                        } else {
                            activity?.finish()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, dynamicStringResource(R.string.btn_exit))
                    }
                },
                actions = {
                    IconButton(onClick = {
                        scope.launch {
                            val success = RemoteStringsManager.forceRefresh(context)

                            val message = if (success) RemoteStringsManager.getString(context, R.string.refresh_strings) else RemoteStringsManager.getString(context, R.string.error_network)
                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()

                            if (success) {
                                activity?.recreate()
                            }
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = dynamicStringResource(R.string.menu_refresh)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent
                ),
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = lazyListState,
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
                    MainDashboardSearchBar(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        onFocusChanged = { searchFieldFocused = it }
                    )
                }

                if (searchQuery.isNotBlank()) {
                    item {
                        MainDashboardSearchResults(
                            query = searchQuery,
                            results = searchResults
                        )
                    }
                    return@LazyColumn
                }

                item {
                    SettingsGroupCard(title = dynamicStringResource(R.string.donate_title)) {
                        MainMenuNavigationRow(
                            title = dynamicStringResource(R.string.donate_title),
                            subtitle = dynamicStringResource(R.string.donate_desc_short),
                            icon = Icons.Rounded.Favorite,
                            iconContainerColor = MaterialTheme.colorScheme.primary,
                            iconContentColor = MaterialTheme.colorScheme.onPrimary,
                            onClick = { context.startActivity(Intent(context, DonateActivity::class.java)) }
                        )
                    }
                }

                if (addonGestureEntries.isNotEmpty()) {
                    item {
                        SettingsGroupCard(title = dynamicStringResource(R.string.main_header_gesture)) {
                            addonGestureEntries.forEachIndexed { index, entry ->
                                if (index > 0) HorizontalDivider()
                                AddonMainEntryRow(
                                    entry = entry,
                                    onClick = {
                                        AddonPageActivity.start(context, addonId = entry.addonId, pageId = entry.leafId, title = entry.title)
                                    }
                                )
                            }
                        }
                    }
                }

                item {
                    SettingsGroupCard(title = dynamicStringResource(R.string.main_header_system)) {
                        if (!AppConfig.IS_XPOSED) {
                            MainMenuNavigationRow(
                                title = dynamicStringResource(R.string.display_title),
                                subtitle = dynamicStringResource(R.string.display_desc),
                                icon = Icons.Rounded.Palette,
                                iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                iconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                onClick = { context.startActivity(Intent(context, DisplaySettingsActivity::class.java)) }
                            )

                            HorizontalDivider()

                            MainMenuNavigationRow(
                                title = dynamicStringResource(R.string.app_icons_title),
                                subtitle = dynamicStringResource(R.string.app_icons_summary),
                                icon = Icons.Rounded.Apps,
                                iconContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                iconContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                onClick = { context.startActivity(Intent(context, AppIconsActivity::class.java)) }
                            )

                            HorizontalDivider()
                        }

                        MainMenuNavigationRow(
                            title = dynamicStringResource(R.string.sysui_settings_title),
                            subtitle = "Configure SystemUI components",
                            icon = Icons.Rounded.SettingsSystemDaydream,
                            iconContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            iconContentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                            onClick = { context.startActivity(Intent(context, SystemUISettingsActivity::class.java)) }
                        )

                        HorizontalDivider()

                        MainMenuNavigationRow(
                            title = dynamicStringResource(R.string.os_title_activity),
                            subtitle = dynamicStringResource(R.string.os_desc_activity),
                            icon = Icons.Rounded.Animation,
                            iconContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                            iconContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            onClick = { context.startActivity(Intent(context, OverscrollActivity::class.java)) }
                        )

                        if (AppConfig.ENABLE_THERMALS) {
                            HorizontalDivider()

                            MainMenuNavigationRow(
                                title = dynamicStringResource(R.string.thermal_manager_title),
                                subtitle = dynamicStringResource(R.string.thermal_manager_desc_activity),
                                icon = Icons.Rounded.Tune,
                                iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                iconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                onClick = { context.startActivity(Intent(context, ThermalConfigManagerActivity::class.java)) }
                            )
                        }

                        if (!AppConfig.IS_XPOSED || showTestThings) {
                            HorizontalDivider()

                            MainMenuNavigationRow(
                                title = dynamicStringResource(R.string.addon_title),
                                subtitle = dynamicStringResource(R.string.addon_desc),
                                icon = Icons.Rounded.Extension,
                                iconContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                iconContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                onClick = { context.startActivity(Intent(context, AddonManagerActivity::class.java)) }
                            )
                        }

                        // Addon entries for "system" group
                        addonSystemEntries.forEach { entry ->
                            HorizontalDivider()
                            AddonMainEntryRow(
                                entry = entry,
                                onClick = {
                                    AddonPageActivity.start(context, addonId = entry.addonId, pageId = entry.leafId, title = entry.title)
                                }
                            )
                        }
                    }
                }

                if (addonNetworkEntries.isNotEmpty()) {
                    item {
                        SettingsGroupCard(title = dynamicStringResource(R.string.main_header_network)) {
                            addonNetworkEntries.forEachIndexed { index, entry ->
                                if (index > 0) HorizontalDivider()
                                AddonMainEntryRow(
                                    entry = entry,
                                    onClick = {
                                        AddonPageActivity.start(context, addonId = entry.addonId, pageId = entry.leafId, title = entry.title)
                                    }
                                )
                            }
                        }
                    }
                }

                // Pixel Launcher addon settings plus the remaining system-only launcher entry.
                item {
                    SettingsGroupCard(title = dynamicStringResource(R.string.launcher_settings_title)) {
                        MainMenuNavigationRow(
                            title = dynamicStringResource(R.string.launcher_hidden_apps_title),
                            subtitle = dynamicStringResource(R.string.launcher_hidden_apps_subtitle),
                            icon = Icons.Rounded.Apps,
                            iconContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            iconContentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                            onClick = { context.startActivity(Intent(context, HiddenLauncherAppsActivity::class.java)) }
                        )

                        // Addon entries for "launcher" group
                        addonLauncherEntries.forEach { entry ->
                            HorizontalDivider()
                            AddonMainEntryRow(
                                entry = entry,
                                onClick = {
                                    AddonPageActivity.start(context, addonId = entry.addonId, pageId = entry.leafId, title = entry.title)
                                }
                            )
                        }
                    }
                }

                addonCustomGroups.forEach { (group, entries) ->
                    item(key = "addon_custom_group_$group") {
                        SettingsGroupCard(title = addonGroupTitle(context, group)) {
                            entries.forEachIndexed { index, entry ->
                                if (index > 0) HorizontalDivider()
                                AddonMainEntryRow(
                                    entry = entry,
                                    onClick = {
                                        AddonPageActivity.start(context, addonId = entry.addonId, pageId = entry.leafId, title = entry.title)
                                    }
                                )
                            }
                        }
                    }
                }

                // Test Things — visible only when pixelparts_test_things == 1
                if (showTestThings) {
                    item {
                        SettingsGroupCard(title = dynamicStringResource(R.string.test_things_title)) {
                            MainMenuNavigationRow(
                                title = dynamicStringResource(R.string.test_things_title),
                                subtitle = dynamicStringResource(R.string.test_things_desc),
                                icon = Icons.Rounded.Science,
                                iconContainerColor = MaterialTheme.colorScheme.errorContainer,
                                iconContentColor = MaterialTheme.colorScheme.onErrorContainer,
                                onClick = { context.startActivity(Intent(context, TestActivity::class.java)) }
                            )
                        }
                    }
                }
            }

            // Fixed at collapsed top-bar height (64dp + status bar).
            // When scrolled the large bar is already collapsed to this size;
            // when at top the overlay is invisible (isScrolled = false, alpha = 0).
            TopBarBlurOverlay(
                modifier = Modifier.fillMaxWidth(),
                topBarHeight = 64.dp + WindowInsets.statusBars
                    .asPaddingValues().calculateTopPadding(),
                blurState = blurState,
                isScrolled = isScrolled
            )
        }
    }
}

private val MAIN_DASHBOARD_KNOWN_ADDON_GROUPS = setOf("gesture", "system", "network", "launcher")

private fun AddonMainEntry.normalizedAddonGroup(): String = group.ifBlank { "system" }

private fun List<AddonMainEntry>.filterAddonGroup(group: String): List<AddonMainEntry> {
    return filter { it.normalizedAddonGroup() == group }.sortedForMainDashboard()
}

private fun List<AddonMainEntry>.sortedForMainDashboard(): List<AddonMainEntry> {
    return sortedWith(compareByDescending<AddonMainEntry> { it.priority }.thenBy { it.title })
}

private fun addonGroupTitle(context: Context, group: String): String {
    return when (group.trim().lowercase(Locale.ROOT)) {
        "gesture" -> context.getString(R.string.main_header_gesture)
        "system" -> context.getString(R.string.main_header_system)
        "network" -> context.getString(R.string.main_header_network)
        "launcher" -> context.getString(R.string.main_header_launcher)
        "systemui", "system_ui" -> context.getString(R.string.main_header_systemui)
        "camera" -> context.getString(R.string.main_header_camera)
        else -> addonCustomGroupTitle(group)
    }
}

private fun addonCustomGroupTitle(group: String): String {
    val words = group.replace('_', ' ').replace('-', ' ').trim()
    if (words.isEmpty()) return "Addons"
    return words.split(Regex("\\s+")).joinToString(" ") { word ->
        word.replaceFirstChar { it.uppercase() }
    }
}

private data class MainDashboardSearchItem(
    val title: String,
    val subtitle: String,
    val section: String,
    val keywords: String,
    val icon: ImageVector,
    val iconContainerColor: Color,
    val iconContentColor: Color,
    val addonEntry: AddonMainEntry? = null,
    val onClick: () -> Unit
) {
    val searchableText: String
        get() = listOf(title, subtitle, section, keywords).joinToString(" ")
}

private data class MainDashboardSearchResult(
    val item: MainDashboardSearchItem,
    val score: Int
)

private data class AddonSettingSearchEntry(
    val entry: AddonMainEntry,
    val setting: AddonSettingDef,
    val path: String
) {
    val uniqueKey: String
        get() = listOf(entry.addonId, entry.rawId, setting.key, path).joinToString(":")
}

@Composable
private fun MainDashboardSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onFocusChanged: (Boolean) -> Unit
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { onFocusChanged(it.isFocused) },
        singleLine = true,
        shape = MaterialTheme.shapes.extraLarge,
        leadingIcon = {
            Icon(Icons.Rounded.Search, contentDescription = null)
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Rounded.Close, contentDescription = null)
                }
            }
        },
        placeholder = {
            Text(dynamicStringResource(R.string.main_search_hint))
        }
    )
}

@Composable
private fun MainDashboardSearchResults(
    query: String,
    results: List<MainDashboardSearchResult>
) {
    SettingsGroupCard(title = dynamicStringResource(R.string.main_search_results_title)) {
        if (results.isEmpty()) {
            Text(
                text = dynamicStringResource(R.string.main_search_no_results, query.trim()),
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            results.forEachIndexed { index, result ->
                if (index > 0) HorizontalDivider()
                val item = result.item
                val addonEntry = item.addonEntry
                if (addonEntry != null) {
                    AddonMainEntryRow(
                        entry = addonEntry,
                        onClick = item.onClick
                    )
                } else {
                    MainMenuNavigationRow(
                        title = item.title,
                        subtitle = item.subtitle,
                        icon = item.icon,
                        iconContainerColor = item.iconContainerColor,
                        iconContentColor = item.iconContentColor,
                        onClick = item.onClick
                    )
                }
            }
        }
    }
}

private fun searchDashboardItems(query: String, items: List<MainDashboardSearchItem>): List<MainDashboardSearchResult> {
    val normalizedQuery = normalizeSearchText(query)
    if (normalizedQuery.isBlank()) return emptyList()
    return items.mapNotNull { item ->
        val score = scoreSearchMatch(normalizedQuery, item.searchableText)
        if (score > 0) MainDashboardSearchResult(item, score) else null
    }.sortedWith(compareByDescending<MainDashboardSearchResult> { it.score }.thenBy { it.item.title })
}

private fun scoreSearchMatch(normalizedQuery: String, text: String): Int {
    val normalizedText = normalizeSearchText(text)
    if (normalizedText.isBlank()) return 0
    val queryTokens = normalizedQuery.split(SEARCH_TOKEN_SPLIT).filter { it.isNotBlank() }
    if (queryTokens.isEmpty()) return 0
    val textTokens = normalizedText.split(SEARCH_TOKEN_SPLIT).filter { it.isNotBlank() }
    var score = 0
    if (normalizedText == normalizedQuery) score += 500
    if (normalizedText.contains(normalizedQuery)) score += 180
    for (token in queryTokens) {
        val tokenScore = when {
            textTokens.any { it == token } -> 90
            textTokens.any { it.startsWith(token) } -> 60
            normalizedText.contains(token) -> 25
            else -> return 0
        }
        score += tokenScore
    }
    return score
}

private fun normalizeSearchText(value: String): String {
    return value
        .lowercase(Locale.ROOT)
        .replace(SEARCH_NORMALIZE_REGEX, " ")
        .trim()
}

private val SEARCH_NORMALIZE_REGEX = Regex("[^\\p{L}\\p{Nd}]+")
private val SEARCH_TOKEN_SPLIT = Regex("\\s+")

private fun AddonMainEntry.flattenAddonTree(): List<AddonMainEntry> {
    return listOf(this) + children.flatMap { it.flattenAddonTree() }
}

private fun AddonMainEntry.addonSearchText(): String {
    return listOf(
        title,
        subtitle,
        group,
        rawId,
        leafId,
        addonId,
        pathSegments.joinToString(" "),
        settings.joinToString(" ") { it.searchText() }
    ).joinToString(" ")
}

private fun AddonMainEntry.flattenAddonSettingSearchEntries(): List<AddonSettingSearchEntry> {
    return settings.flatMap { setting -> setting.flattenAddonSettingSearchEntries(this, emptyList()) }
}

private fun AddonSettingDef.flattenAddonSettingSearchEntries(
    entry: AddonMainEntry,
    parentTitles: List<String>
): List<AddonSettingSearchEntry> {
    val currentPath = (parentTitles + title).filter { it.isNotBlank() }
    return buildList {
        if (type.isSearchableAddonParameter() && (title.isNotBlank() || description.isNotBlank() || key.isNotBlank())) {
            add(AddonSettingSearchEntry(entry = entry, setting = this@flattenAddonSettingSearchEntries, path = currentPath.joinToString(" / ")))
        }
        children.forEach { child ->
            addAll(child.flattenAddonSettingSearchEntries(entry, currentPath))
        }
    }
}

private fun SettingType.isSearchableAddonParameter(): Boolean {
    return this != SettingType.VISUAL
}

private fun AddonSettingSearchEntry.searchText(): String {
    return listOf(
        entry.title,
        entry.subtitle,
        entry.group,
        entry.rawId,
        entry.leafId,
        path,
        setting.searchText()
    ).joinToString(" ")
}

private fun AddonSettingDef.searchText(): String {
    return listOf(
        key,
        title,
        description,
        unit,
        options.joinToString(" ") { option -> option.value + " " + option.label },
        tileTargets.joinToString(" ") { target -> target.key + " " + target.label + " " + target.values.joinToString(" ") + " " + target.labels.joinToString(" ") },
        tileActivities.joinToString(" ") { option -> option.value + " " + option.label },
        children.joinToString(" ") { it.searchText() }
    ).joinToString(" ")
}

private fun openAddonSearchEntry(context: Context, entry: AddonMainEntry) {
    val targetIntent = intentForTargetActivity(context, entry.targetActivity)
    if (targetIntent != null) {
        context.startActivity(targetIntent)
    } else {
        AddonPageActivity.start(context, addonId = entry.addonId, pageId = entry.leafId, title = entry.title)
    }
}

private fun intentForTargetActivity(context: Context, targetActivity: String): Intent? {
    if (targetActivity.isBlank()) return null
    return when (targetActivity.substringAfterLast('.').removeSuffix("Activity").lowercase(Locale.ROOT)) {
        "appicons" -> Intent(context, AppIconsActivity::class.java)
        "addonmanager" -> Intent(context, AddonManagerActivity::class.java)
        "displaysettings", "display" -> Intent(context, DisplaySettingsActivity::class.java)
        "hiddenlauncherapps", "launcher" -> Intent(context, HiddenLauncherAppsActivity::class.java)
        "systemuisettings", "systemui" -> Intent(context, SystemUISettingsActivity::class.java)
        "thermalconfigmanager", "thermal" -> Intent(context, ThermalConfigManagerActivity::class.java)
        else -> null
    }
}

private fun Map<String, String>.categoryText(category: String): String = get(category).orEmpty()

private fun collectMainSearchResourceStrings(context: Context): Map<String, String> {
    val categories = mapOf(
        "double_tap" to listOf("dt_", "dt2w_", "dt2s_", "doze_"),
        "overscroll" to listOf("os_", "overscroll_"),
        "display" to listOf("display_", "saturation_", "auto_hbm_"),
        "app_icons" to listOf("app_icons_", "icon_", "icon_shape_"),
        "systemui" to listOf("sysui_", "qs_", "shade_", "lockscreen_", "battery_", "charging_", "magnifier_", "activity_transition_", "log_"),
        "launcher" to listOf("launcher_", "recents_", "search_widget_", "grid_", "clear_all_", "gesture_bar_"),
        "thermal" to listOf("thermal_"),
        "addons" to listOf("addon_"),
        "test" to listOf("test_")
    )
    val result = categories.keys.associateWith { StringBuilder() }.toMutableMap()
    runCatching {
        R.string::class.java.fields.forEach { field ->
            val name = field.name
            val matchedCategories = categories.filterValues { prefixes -> prefixes.any { name.startsWith(it) } }.keys
            if (matchedCategories.isEmpty()) return@forEach
            val value = runCatching { context.getString(field.getInt(null)) }.getOrNull().orEmpty()
            if (value.isBlank()) return@forEach
            matchedCategories.forEach { category ->
                result[category]?.append(' ')?.append(name)?.append(' ')?.append(value)
            }
        }
    }
    return result.mapValues { (_, value) -> value.toString() }
}

@Composable
fun MainMenuNavigationRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconContainerColor: Color,
    iconContentColor: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(iconContainerColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = iconContentColor
            )
        }

        Column(
            modifier = Modifier
                .padding(start = 16.dp)
                .weight(1f)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }

        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}