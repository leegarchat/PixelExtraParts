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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.pixel.customparts.R
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
    val title: String
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
    onBack: () -> Unit
) {
    val context = LocalContext.current

    // Load the full main-menu model for this addon only
    var model by remember { mutableStateOf<AddonMainMenuModel?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(addonId) {
        withContext(Dispatchers.IO) {
            val full = scanAddonMainEntries(context)
            // Filter to entries belonging to this addon
            val filtered = full.entries.filter { it.addonId == addonId }
            model = AddonMainMenuModel(entries = filtered)
        }
        isLoading = false
    }

    // Back-stack: start at root
    val backStack = remember { mutableStateListOf(PageStackEntry(entry = null, title = addonId)) }
    val current = backStack.last()

    // If a specific pageId was requested, navigate to it once model is loaded
    LaunchedEffect(model, pageId) {
        val m = model ?: return@LaunchedEffect
        if (pageId != null && backStack.size == 1) {
            val target = findEntryByLeafId(m.entries, pageId)
            if (target != null) {
                backStack.add(PageStackEntry(entry = target, title = target.title))
            }
        }
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
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
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
                        Text(title, fontWeight = FontWeight.Bold)
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
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                AnimatedContent(
                    targetState = current,
                    transitionSpec = {
                        val forward = targetState.entry != null || initialState.entry != null
                        if (forward) {
                            (slideInHorizontally { it / 3 } + fadeIn()) togetherWith
                                    (slideOutHorizontally { -it / 3 } + fadeOut())
                        } else {
                            (slideInHorizontally { -it / 3 } + fadeIn()) togetherWith
                                    (slideOutHorizontally { it / 3 } + fadeOut())
                        }
                    },
                    label = "pageAnim"
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
                        innerPadding = innerPadding,
                        blurState = blurState,
                        onNavigate = { child ->
                            backStack.add(PageStackEntry(entry = child, title = child.title))
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
    innerPadding: PaddingValues,
    blurState: GraphicsLayerRecordingState,
    onNavigate: (AddonMainEntry) -> Unit
) {
    val context = LocalContext.current
    var settingsRevision by remember { mutableIntStateOf(0) }

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
        // Settings for the current page (if any)
        if (currentEntry != null && currentEntry.settings.isNotEmpty()) {
            item(key = "settings_card") {
                val allSettings = remember(currentEntry.settings) { flattenSettings(currentEntry.settings) }
                SettingsGroupCard(title = dynamicStringResource(R.string.addon_settings_title)) {
                    for (setting in currentEntry.settings) {
                        key(setting.key, settingsRevision) {
                            AddonSettingControl(
                                setting = setting,
                                addon = AddonUiModel(
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
                                ),
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                                onSettingChanged = { changedSetting, boolVal ->
                                    if (boolVal == true) {
                                        applyExclusiveSettingLogic(
                                            context,
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
                                            ),
                                            changedSetting,
                                            allSettings
                                        )
                                        settingsRevision++
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        // Child navigation entries
        if (entries.isNotEmpty()) {
            // Group children by their group field (only meaningful at root level;
            // nested pages typically have no group distinction)
            val grouped = entries.groupBy { it.group }
            val groupOrder = listOf("gesture", "system", "network")
            val sortedGroups = grouped.keys.sortedWith(
                compareBy { groupOrder.indexOf(it).takeIf { i -> i >= 0 } ?: Int.MAX_VALUE }
            )

            for (group in sortedGroups) {
                val groupEntries = grouped[group]
                    ?.sortedWith(compareByDescending<AddonMainEntry> { it.priority }.thenBy { it.title })
                    ?: continue

                item(key = "group_$group") {
                    val groupTitle = when (group) {
                        "gesture" -> dynamicStringResource(R.string.main_header_gesture)
                        "network" -> dynamicStringResource(R.string.main_header_network)
                        else -> dynamicStringResource(R.string.main_header_system)
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

// =====================================================================
// Single navigation row for an AddonMainEntry
// =====================================================================

@Composable
fun AddonMainEntryRow(
    entry: AddonMainEntry,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon: custom bitmap or default Extension icon
        if (entry.iconBitmap != null) {
            val bmp = remember(entry.addonId, entry.leafId) { entry.iconBitmap.asImageBitmap() }
            Image(
                bitmap = bmp,
                contentDescription = null,
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
            )
        } else {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Extension,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }

        Column(
            modifier = Modifier
                .padding(start = 16.dp)
                .weight(1f)
        ) {
            Text(
                text = entry.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            if (entry.subtitle.isNotEmpty()) {
                Text(
                    text = entry.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
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

// =====================================================================
// Helper: find entry by leafId in a tree
// =====================================================================

fun findEntryByLeafId(entries: List<AddonMainEntry>, leafId: String): AddonMainEntry? {
    for (entry in entries) {
        if (entry.leafId == leafId) return entry
        val found = findEntryByLeafId(entry.children, leafId)
        if (found != null) return found
    }
    return null
}
