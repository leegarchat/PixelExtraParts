package org.pixel.customparts.ui.addons

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.pixel.customparts.R
import org.pixel.customparts.SettingsKeys
import org.pixel.customparts.activities.LauncherManager
import org.pixel.customparts.icons.IconPackManager
import org.pixel.customparts.ui.GraphicsLayerRecordingState
import org.pixel.customparts.ui.RebootBubble
import org.pixel.customparts.ui.REBOOT_BUBBLE_CONTENT_BOTTOM_PADDING
import org.pixel.customparts.ui.SettingsGroupCard
import org.pixel.customparts.ui.TopBarBlurOverlay
import org.pixel.customparts.ui.recordLayer
import org.pixel.customparts.ui.rememberGraphicsLayerRecordingState
import org.pixel.customparts.utils.dynamicStringResource

// =====================================================================
// Navigation state for addon page stack
// =====================================================================

/**
 * Represents one level in the back-stack of an addon page navigation.
 * [entry] is null only for the root level (shows all top-level entries for the addon).
 */
private data class PageStackEntry(
    val entry: AddonMainEntry?,   // null = root list of top-level entries
    val title: String,
    val depth: Int
)

// =====================================================================
// AddonPageScreen — full-screen Activity UI for a single addon's main[] tree
// =====================================================================

/**
 * Full-screen composable that renders the main[] page tree for one addon.
 *
 * [addonId]   — the addon whose main[] entries to show
 * [pageId]    — optional: if set, navigate directly to this entry's leafId on launch
 * [onBack]    — called when the user presses back at the root level
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddonPageScreen(
    addonId: String,
    pageId: String? = null,
    includeTargetActivityEntries: Boolean = false,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    // Load the full main-menu model for this addon only
    var model by remember { mutableStateOf<AddonMainMenuModel?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(addonId, includeTargetActivityEntries) {
        withContext(Dispatchers.IO) {
            val full = scanAddonMainEntries(context, includeTargetActivityEntries = includeTargetActivityEntries)
            // Filter to entries belonging to this addon
            val filtered = full.entries.filter { it.addonId == addonId }
            model = AddonMainMenuModel(entries = filtered)
        }
        isLoading = false
    }

    // Back-stack starts at root, but direct page launches replace it with the real entry path.
    val backStack = remember(addonId, pageId, includeTargetActivityEntries) { mutableStateListOf(PageStackEntry(entry = null, title = addonId, depth = 0)) }
    val current = backStack.last()

    // If a specific pageId was requested, navigate to it once model is loaded (without animation)
    var initialNavigationDone by remember(addonId, pageId, includeTargetActivityEntries) { mutableStateOf(pageId == null) }
    LaunchedEffect(model, pageId, includeTargetActivityEntries) {
        val m = model ?: return@LaunchedEffect
        if (pageId != null && !initialNavigationDone) {
            val path = findEntryPathByLeafId(m.entries, pageId)
            if (path != null) {
                backStack.clear()
                backStack.addAll(path.mapIndexed { index, entry -> PageStackEntry(entry = entry, title = entry.title, depth = index + 1) })
            }
            initialNavigationDone = true
        }
    }

    // Handle system back gesture
    androidx.activity.compose.BackHandler(enabled = backStack.size > 1) {
        backStack.removeLast()
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val blurState = rememberGraphicsLayerRecordingState()
    val lazyListState = rememberLazyListState()
    val isScrolled by remember { derivedStateOf { lazyListState.canScrollBackward } }

    // Determine which entries to show at the current level
    val currentEntries: List<AddonMainEntry> = remember(current, model) {
        val m = model ?: return@remember emptyList()
        if (current.entry == null) m.entries else current.entry.children
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        floatingActionButton = { RebootBubble() },
        topBar = {
            LargeTopAppBar(
                scrollBehavior = scrollBehavior,
                title = {
                    AnimatedContent(
                        targetState = current.title,
                        transitionSpec = {
                            (slideInHorizontally { it / 4 } + fadeIn()) togetherWith
                                    (slideOutHorizontally { -it / 4 } + fadeOut())
                        },
                        label = "titleAnim"
                    ) { title ->
                        val titleSizeSp = current.entry?.titleSizeSp ?: 0f
                        Text(
                            title,
                            style = MaterialTheme.typography.headlineSmall.withAddonTextSize(titleSizeSp),
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (backStack.size > 1) backStack.removeLast() else onBack()
                    }) {
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
            if (isLoading || !initialNavigationDone) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                AddonPageTransitionHost(
                    targetState = current,
                    modifier = Modifier.fillMaxSize(),
                    isForward = { initialState, targetState -> targetState.depth > initialState.depth }
                ) { page ->
                    val entries = if (page.entry == null) {
                        model?.entries ?: emptyList()
                    } else {
                        page.entry.children
                    }

                    AddonPageContent(
                        entries = entries,
                        currentEntry = page.entry,
                        lazyListState = lazyListState,
                        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
                        innerPadding = innerPadding,
                        blurState = blurState,
                        onNavigate = { child ->
                            backStack.add(PageStackEntry(entry = child, title = child.title, depth = page.depth + 1))
                        }
                    )
                }
            }

            TopBarBlurOverlay(
                modifier = Modifier.fillMaxWidth(),
                topBarHeight = innerPadding.calculateTopPadding(),
                blurState = blurState,
                isScrolled = isScrolled
            )
        }
    }
}

// =====================================================================
// Page content — settings + child navigation rows
// =====================================================================

@Composable
private fun AddonPageContent(
    entries: List<AddonMainEntry>,
    currentEntry: AddonMainEntry?,
    lazyListState: androidx.compose.foundation.lazy.LazyListState,
    modifier: Modifier = Modifier,
    innerPadding: PaddingValues,
    blurState: GraphicsLayerRecordingState,
    onNavigate: (AddonMainEntry) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var settingsRevision by remember { mutableIntStateOf(0) }

    LazyColumn(
        state = lazyListState,
        modifier = modifier
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
        // Settings for the current page (if any)
        if (currentEntry != null && currentEntry.settings.isNotEmpty()) {
            item(key = "settings_card") {
                val allSettings = remember(currentEntry.settings) { flattenSettings(currentEntry.settings) }
                val categories = remember(currentEntry.settings) { buildAddonSettingCategories(currentEntry.settings) }
                val addonUiModel = remember(currentEntry) {
                    AddonUiModel(
                        id = currentEntry.addonId,
                        entryClass = "",
                        name = currentEntry.title,
                        author = "",
                        description = "",
                        version = "",
                        jarPath = currentEntry.addonJarPath,
                        defaultTargets = emptySet(),
                        enabled = true,
                        scopeMode = 0,
                        customTargets = emptySet(),
                        isSystem = currentEntry.isSystemAddon
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    categories.forEach { category ->
                        key("category_${category.key}") {
                            AddonPageSettingsCategoryCard(
                                category = category,
                                addon = addonUiModel,
                                allSettings = allSettings,
                                dependencyRevision = settingsRevision,
                                onSettingChanged = { changedSetting, boolVal ->
                                    if (boolVal == true) {
                                        applyExclusiveSettingLogic(context, addonUiModel, changedSetting, allSettings)
                                    }
                                    if (changedSetting.key.startsWith("pixelparts_app_icons_") || changedSetting.key.startsWith("pixelparts_icon_shape_")) {
                                        IconPackManager.requestIconReload(context)
                                    }
                                    if (changedSetting.key == SettingsKeys.PIXEL_LAUNCHER_NATIVE_SEARCH && boolVal != null) {
                                        scope.launch { LauncherManager.setNativeSearchEnabled(context, boolVal) }
                                    }
                                    settingsRevision++
                                }
                            )
                        }
                    }
                }
            }
        }

        // Child navigation entries
        if (entries.isNotEmpty()) {
            // Group children by their group field
            val grouped = entries.groupBy { it.group }
            // Known groups with fixed order; custom groups come after, sorted by max priority within group
            val knownGroupOrder = listOf("launcher", "gesture", "system", "systemui", "camera", "network")
            val sortedGroups = grouped.keys.sortedWith(
                compareBy<String> {
                    val knownIdx = knownGroupOrder.indexOf(it)
                    if (knownIdx >= 0) knownIdx else knownGroupOrder.size
                }.thenByDescending { groupKey ->
                    // Custom groups sorted by highest priority entry within
                    grouped[groupKey]?.maxOfOrNull { it.priority } ?: 0
                }
            )
            for (group in sortedGroups) {
                val groupEntries = grouped[group]
                    ?.sortedWith(compareByDescending<AddonMainEntry> { it.priority }.thenBy { it.title })
                    ?: continue

                item(key = "group_$group") {
                    val groupTitle = when (group) {
                        "launcher" -> dynamicStringResource(R.string.main_header_launcher)
                        "gesture" -> dynamicStringResource(R.string.main_header_gesture)
                        "network" -> dynamicStringResource(R.string.main_header_network)
                        "system" -> dynamicStringResource(R.string.main_header_system)
                        "systemui", "system_ui" -> dynamicStringResource(R.string.main_header_systemui)
                        "camera" -> dynamicStringResource(R.string.main_header_camera)
                        else -> group.replaceFirstChar { it.uppercase() }
                    }
                    SettingsGroupCard(title = groupTitle) {
                        groupEntries.forEachIndexed { index, entry ->
                            if (index > 0) HorizontalDivider()
                            AddonMainEntryRow(
                                entry = entry,
                                onClick = { onNavigate(entry) }
                            )
                        }
                    }
                }
            }
        }

        // Empty state when no settings and no children
        if (currentEntry != null && currentEntry.settings.isEmpty() && entries.isEmpty()) {
            item(key = "empty") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        dynamicStringResource(R.string.addon_settings_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun AddonPageSettingsCategoryCard(
    category: AddonSettingCategory,
    addon: AddonUiModel,
    allSettings: List<AddonSettingDef>,
    dependencyRevision: Int,
    onSettingChanged: (AddonSettingDef, Boolean?) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            if (category.title.isNotBlank()) {
                Text(
                    text = category.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
            }
            if (category.description.isNotBlank()) {
                Text(
                    text = category.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                )
            }
            category.settings.forEach { setting ->
                key(setting.key) {
                    val settingModifier = if (setting.type == SettingType.GROUP && setting.groupMode != GroupMode.INLINE) {
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    } else {
                        Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                    }
                    AddonSettingControl(
                        setting = setting,
                        addon = addon,
                        modifier = settingModifier,
                        allSettings = allSettings,
                        dependencyRevision = dependencyRevision,
                        onSettingChanged = onSettingChanged
                    )
                }
            }
        }
    }
}

// =====================================================================
// Single navigation row for an AddonMainEntry
// =====================================================================

@Composable
internal fun AddonMainEntryIcon(
    entry: AddonMainEntry,
    modifier: Modifier = Modifier,
    containerSize: Dp = 36.dp,
    fallbackTint: Color? = null,
    fallbackContainer: Color? = null
) {
    val materialIcon = if (entry.iconBitmap == null && entry.icon.isNotBlank() && entry.iconType != "file") {
        rememberMaterialIcon(entry.icon)
    } else null
    val shape = when (entry.iconShape.trim().lowercase()) {
        "rounded" -> RoundedCornerShape(10.dp)
        "none" -> null
        else -> CircleShape
    }
    val iconSize = (entry.iconSize.takeIf { it > 0 } ?: 20).dp
    val tint = parseOptionalColor(entry.iconColor)
        ?: fallbackTint
        ?: MaterialTheme.colorScheme.onSecondaryContainer
    val containerColor = parseOptionalColor(entry.iconBackground)
        ?: fallbackContainer
        ?: MaterialTheme.colorScheme.secondaryContainer

    if (entry.iconBitmap != null) {
        val bmp = remember(entry.addonId, entry.rawId) { entry.iconBitmap.asImageBitmap() }
        if (shape != null) {
            Box(
                modifier = modifier
                    .size(containerSize)
                    .clip(shape)
                    .background(containerColor),
                contentAlignment = Alignment.Center
            ) {
                Image(bitmap = bmp, contentDescription = null, modifier = Modifier.size(iconSize))
            }
        } else {
            Image(
                bitmap = bmp,
                contentDescription = null,
                modifier = modifier
                    .size(containerSize)
                    .clip(RoundedCornerShape(10.dp))
            )
        }
        return
    }

    val icon = materialIcon ?: Icons.Rounded.Extension
    if (shape != null) {
        Box(
            modifier = modifier
                .size(containerSize)
                .clip(shape)
                .background(containerColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(iconSize),
                tint = tint
            )
        }
    } else {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = modifier.size(iconSize),
            tint = tint
        )
    }
}

@Composable
fun AddonMainEntryRow(
    entry: AddonMainEntry,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconContainerSize: Dp = 36.dp
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AddonMainEntryIcon(entry = entry, containerSize = iconContainerSize)

        Column(
            modifier = Modifier
                .padding(start = 16.dp)
                .weight(1f)
        ) {
            Text(
                text = entry.title,
                style = MaterialTheme.typography.titleMedium.withAddonTextSize(entry.titleSizeSp),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (entry.subtitle.isNotEmpty()) {
                Text(
                    text = entry.subtitle,
                    style = MaterialTheme.typography.bodyMedium.withAddonTextSize(entry.descriptionSizeSp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun TextStyle.withAddonTextSize(sizeSp: Float): TextStyle {
    return if (sizeSp > 0f) copy(fontSize = sizeSp.sp) else this
}

// =====================================================================
// Helper: find entry by leafId in a tree
// =====================================================================

fun findEntryByLeafId(entries: List<AddonMainEntry>, leafId: String): AddonMainEntry? {
    return findEntryPathByPageId(entries, leafId)?.lastOrNull()
}

private fun findEntryPathByLeafId(entries: List<AddonMainEntry>, leafId: String): List<AddonMainEntry>? {
    return findEntryPathByPageId(entries, leafId)
}

private fun findEntryPathByPageId(entries: List<AddonMainEntry>, pageId: String): List<AddonMainEntry>? {
    val normalizedPageId = normalizeAddonPageId(pageId)
    for (entry in entries) {
        if (entry.matchesPageId(normalizedPageId)) return listOf(entry)
        val childPath = findEntryPathByPageId(entry.children, pageId)
        if (childPath != null) return listOf(entry) + childPath
    }
    return null
}

private fun AddonMainEntry.matchesPageId(normalizedPageId: String): Boolean {
    if (normalizedPageId.isBlank()) return false
    val normalizedRawId = normalizeAddonPageId(rawId)
    val normalizedPath = pathSegments.joinToString("/").lowercase()
    return normalizedPageId == leafId.lowercase() ||
            normalizedPageId == normalizedRawId ||
            normalizedPageId == normalizedPath
}

private fun normalizeAddonPageId(value: String): String {
    return value.trim()
        .trim('/')
        .split('/')
        .filter { it.isNotBlank() && it != "main" }
        .joinToString("/")
        .lowercase()
}
