package org.pixel.extra.parts.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SettingsSystemDaydream
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.activity.compose.BackHandler
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.pixel.extra.parts.AppConfig
import org.pixel.extra.parts.R
import org.pixel.extra.parts.services.MainActivityTileService
import org.pixel.extra.parts.ui.legacy.REBOOT_BUBBLE_CONTENT_BOTTOM_PADDING
import org.pixel.extra.parts.ui.legacy.RebootBubble
import org.pixel.extra.parts.ui.legacy.RebootBubbleMenuAction
import org.pixel.extra.parts.ui.legacy.SettingsGroupCard
import org.pixel.extra.parts.utils.TileUtils
import java.util.Locale

// Home tab: grouped catalog of the ported sections. Each row pushes a route
// onto the root hand-rolled back stack (single MainActivity, no Navigation-Compose).
@Composable
fun CatalogScreen(
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var searchFieldFocused by remember { mutableStateOf(false) }

    BackHandler(enabled = searchQuery.isNotBlank() || searchFieldFocused) {
        if (searchFieldFocused) {
            focusManager.clearFocus(force = true)
            searchFieldFocused = false
        } else {
            searchQuery = ""
        }
    }

    val dashboardSearchItems = remember { buildCatalogSearchItems() }
    val internalStringIndex = remember(context) { collectMainSearchResourceStrings(context) }
    val searchResults = remember(searchQuery, dashboardSearchItems, internalStringIndex) {
        searchDashboardItems(context, searchQuery, dashboardSearchItems, internalStringIndex)
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = rememberLazyListState(),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                top = 8.dp,
                end = 16.dp,
                bottom = REBOOT_BUBBLE_CONTENT_BOTTOM_PADDING
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                CatalogSearchBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    onFocusChanged = { searchFieldFocused = it }
                )
            }

            if (searchQuery.isNotBlank()) {
                item {
                    CatalogSearchResults(
                        query = searchQuery,
                        results = searchResults,
                        onNavigate = onNavigate
                    )
                }
                return@LazyColumn
            }

            item {
                SettingsGroupCard(title = stringResource(R.string.donate_title)) {
                    MainMenuNavigationRow(
                        title = stringResource(R.string.donate_title),
                        subtitle = stringResource(R.string.donate_desc_short),
                        icon = Icons.Rounded.Favorite,
                        iconContainerColor = MaterialTheme.colorScheme.primary,
                        iconContentColor = MaterialTheme.colorScheme.onPrimary,
                        onClick = { onNavigate("donate") }
                    )
                }
            }

            item {
                SettingsGroupCard(title = stringResource(R.string.main_header_system)) {
                    MainMenuNavigationRow(
                        title = stringResource(R.string.display_title),
                        subtitle = stringResource(R.string.display_desc),
                        icon = Icons.Rounded.Palette,
                        iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        iconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        onClick = { onNavigate("display") }
                    )

                    HorizontalDivider()

                    MainMenuNavigationRow(
                        title = stringResource(R.string.app_icons_title),
                        subtitle = stringResource(R.string.app_icons_summary),
                        icon = Icons.Rounded.Apps,
                        iconContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        iconContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        onClick = { onNavigate("appicons") }
                    )

                    HorizontalDivider()

                    MainMenuNavigationRow(
                        title = stringResource(R.string.sysui_settings_title),
                        subtitle = stringResource(R.string.sysui_settings_summary),
                        icon = Icons.Rounded.SettingsSystemDaydream,
                        iconContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        iconContentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        onClick = { onNavigate("systemui") }
                    )

                    HorizontalDivider()

                    MainMenuNavigationRow(
                        title = stringResource(R.string.os_title_activity),
                        subtitle = stringResource(R.string.os_desc_activity),
                        icon = Icons.Rounded.Animation,
                        iconContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        iconContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        onClick = { onNavigate("overscroll") }
                    )

                    if (AppConfig.ENABLE_THERMALS) {
                        HorizontalDivider()

                        MainMenuNavigationRow(
                            title = stringResource(R.string.thermal_manager_title),
                            subtitle = stringResource(R.string.thermal_manager_desc_activity),
                            icon = Icons.Rounded.Tune,
                            iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            iconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            onClick = { onNavigate("thermalConfigs") }
                        )
                    }
                }
            }

            item {
                SettingsGroupCard(title = stringResource(R.string.launcher_settings_title)) {
                    MainMenuNavigationRow(
                        title = stringResource(R.string.launcher_hidden_apps_title),
                        subtitle = stringResource(R.string.launcher_hidden_apps_subtitle),
                        icon = Icons.Rounded.Apps,
                        iconContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        iconContentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        onClick = { onNavigate("hidden") }
                    )
                }
            }
        }

        RebootBubble(
            extraActions = listOf(
                RebootBubbleMenuAction(
                    icon = Icons.Rounded.Add,
                    label = stringResource(R.string.main_add_tile),
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
            ),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 16.dp)
        )
    }
}

private fun buildCatalogSearchItems(): List<CatalogSearchItem> {
    return buildList {
        add(
            CatalogSearchItem(
                titleRes = R.string.donate_title,
                subtitleRes = R.string.donate_desc_short,
                sectionRes = R.string.donate_title,
                keywords = "donate support contribution",
                icon = Icons.Rounded.Favorite,
                category = "donate",
                route = "donate"
            )
        )
        add(
            CatalogSearchItem(
                titleRes = R.string.os_title_activity,
                subtitleRes = R.string.os_desc_activity,
                sectionRes = R.string.main_header_system,
                keywords = "overscroll scroll animation bounce edge ",
                icon = Icons.Rounded.Animation,
                category = "overscroll",
                route = "overscroll"
            )
        )
        add(
            CatalogSearchItem(
                titleRes = R.string.display_title,
                subtitleRes = R.string.display_desc,
                sectionRes = R.string.main_header_system,
                keywords = "display color theme palette screen ",
                icon = Icons.Rounded.Palette,
                category = "display",
                route = "display"
            )
        )
        add(
            CatalogSearchItem(
                titleRes = R.string.app_icons_title,
                subtitleRes = R.string.app_icons_summary,
                sectionRes = R.string.main_header_system,
                keywords = "icons icon manager shapes tint apps ",
                icon = Icons.Rounded.Apps,
                category = "app_icons",
                route = "appicons"
            )
        )
        add(
            CatalogSearchItem(
                titleRes = R.string.sysui_settings_title,
                subtitleRes = R.string.sysui_settings_summary,
                sectionRes = R.string.main_header_system,
                keywords = "systemui status bar quick settings qs shade lockscreen ",
                icon = Icons.Rounded.SettingsSystemDaydream,
                category = "systemui",
                route = "systemui"
            )
        )
        add(
            CatalogSearchItem(
                titleRes = R.string.launcher_hidden_apps_title,
                subtitleRes = R.string.launcher_hidden_apps_subtitle,
                sectionRes = R.string.launcher_settings_title,
                keywords = "launcher hidden apps hide home nexus ",
                icon = Icons.Rounded.Apps,
                category = "launcher",
                route = "hidden"
            )
        )
        if (AppConfig.ENABLE_THERMALS) {
            add(
                CatalogSearchItem(
                    titleRes = R.string.thermal_manager_title,
                    subtitleRes = R.string.thermal_manager_desc_activity,
                    sectionRes = R.string.main_header_system,
                    keywords = "thermal temperature performance profiles ",
                    icon = Icons.Rounded.Tune,
                    category = "thermal",
                route = "thermal"
                )
            )
        }
    }
}

private data class CatalogSearchItem(
    val titleRes: Int,
    val subtitleRes: Int,
    val sectionRes: Int,
    val keywords: String,
    val category: String,
    val icon: ImageVector,
    val route: String
)

private data class CatalogSearchResult(
    val item: CatalogSearchItem,
    val score: Int
)

@Composable
private fun CatalogSearchBar(
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
            Text(stringResource(R.string.main_search_hint))
        }
    )
}

@Composable
private fun CatalogSearchResults(
    query: String,
    results: List<CatalogSearchResult>,
    onNavigate: (String) -> Unit
) {
    val context = LocalContext.current
    SettingsGroupCard(title = stringResource(R.string.main_search_results_title)) {
        if (results.isEmpty()) {
            Text(
                text = stringResource(R.string.main_search_no_results, query.trim()),
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            results.forEachIndexed { index, result ->
                if (index > 0) HorizontalDivider()
                val item = result.item
                MainMenuNavigationRow(
                    title = context.getString(item.titleRes),
                    subtitle = context.getString(item.subtitleRes),
                    icon = item.icon,
                    iconContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    iconContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    onClick = { onNavigate(item.route) }
                )
            }
        }
    }
}

private fun searchDashboardItems(
    context: android.content.Context,
    query: String,
    items: List<CatalogSearchItem>,
    index: Map<String, String>
): List<CatalogSearchResult> {
    val normalizedQuery = normalizeSearchText(query)
    if (normalizedQuery.isBlank()) return emptyList()
    return items.mapNotNull { item ->
        val title = context.getString(item.titleRes)
        val subtitle = context.getString(item.subtitleRes)
        val section = context.getString(item.sectionRes)
        val text = listOf(title, subtitle, section, item.keywords, index.categoryText(item.category)).joinToString(" ")
        val score = scoreSearchMatch(normalizedQuery, text)
        if (score > 0) CatalogSearchResult(item, score) else null
    }.sortedWith(compareByDescending<CatalogSearchResult> { it.score }.thenBy { context.getString(it.item.titleRes) })
}

private fun Map<String, String>.categoryText(category: String): String = get(category).orEmpty()

private fun collectMainSearchResourceStrings(context: android.content.Context): Map<String, String> {
    val categories = mapOf(
        "donate" to listOf("donate_"),
        "overscroll" to listOf("os_", "overscroll_"),
        "display" to listOf("display_", "saturation_", "auto_hbm_"),
        "app_icons" to listOf("app_icons_", "icon_", "icon_shape_"),
        "systemui" to listOf("sysui_", "qs_", "shade_", "lockscreen_", "battery_", "charging_", "magnifier_", "activity_transition_", "log_"),
        "launcher" to listOf("launcher_", "recents_", "search_widget_", "grid_", "clear_all_", "gesture_bar_"),
        "thermal" to listOf("thermal_")
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
