package org.pixel.extra.parts.home

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.pixel.extra.parts.R
import org.pixel.extra.parts.navigation.SectionRoutes
import org.pixel.extra.parts.ui.SectionGroupCard
import org.pixel.extra.parts.ui.SectionNavRow
import org.pixel.extra.parts.ui.sectionContentPadding

// Icon-box palette for home rows, mirroring the original dashboard's
// per-section container colors (MainActivity system/launcher groups).
private enum class HomeRowPalette {
    PRIMARY,
    SECONDARY,
    TERTIARY,
}

@Composable
private fun HomeRowPalette.container(): Color = when (this) {
    HomeRowPalette.PRIMARY -> MaterialTheme.colorScheme.primaryContainer
    HomeRowPalette.SECONDARY -> MaterialTheme.colorScheme.secondaryContainer
    HomeRowPalette.TERTIARY -> MaterialTheme.colorScheme.tertiaryContainer
}

@Composable
private fun HomeRowPalette.content(): Color = when (this) {
    HomeRowPalette.PRIMARY -> MaterialTheme.colorScheme.onPrimaryContainer
    HomeRowPalette.SECONDARY -> MaterialTheme.colorScheme.onSecondaryContainer
    HomeRowPalette.TERTIARY -> MaterialTheme.colorScheme.onTertiaryContainer
}

// One row of the home hub. Subtitle is always present, matching the
// original MainMenuNavigationRow signature.
private data class HomeSectionEntry(
    val routeKey: String,
    @StringRes val titleRes: Int,
    @StringRes val subtitleRes: Int,
    @DrawableRes val iconRes: Int,
    val palette: HomeRowPalette,
)

// System group rows in the original dashboard order: Display/Thermals use
// the primary container, Icons/Overscroll/Addons the secondary one, and
// System UI the tertiary one.
private val HomeSystemSections = listOf(
    HomeSectionEntry(
        routeKey = SectionRoutes.DISPLAY,
        titleRes = R.string.tab_display,
        subtitleRes = R.string.display_desc,
        iconRes = R.drawable.ic_auto_hbm_tile,
        palette = HomeRowPalette.PRIMARY,
    ),
    HomeSectionEntry(
        routeKey = SectionRoutes.ICONS,
        titleRes = R.string.tab_icons,
        subtitleRes = R.string.icons_header_sub,
        iconRes = R.drawable.ic_saturation_tile,
        palette = HomeRowPalette.SECONDARY,
    ),
    HomeSectionEntry(
        routeKey = SectionRoutes.SYSTEM_UI,
        titleRes = R.string.tab_systemui,
        subtitleRes = R.string.sysui_section_desc,
        iconRes = R.drawable.ic_activity_transition_tile,
        palette = HomeRowPalette.TERTIARY,
    ),
    HomeSectionEntry(
        routeKey = SectionRoutes.OVERSCROLL,
        titleRes = R.string.tab_overscroll,
        subtitleRes = R.string.os_header_desc,
        iconRes = R.drawable.ic_overscroll_tile,
        palette = HomeRowPalette.SECONDARY,
    ),
    HomeSectionEntry(
        routeKey = SectionRoutes.THERMAL,
        titleRes = R.string.tab_thermal,
        subtitleRes = R.string.thermal_desc,
        iconRes = R.drawable.ic_thermal_tile,
        palette = HomeRowPalette.PRIMARY,
    ),
    HomeSectionEntry(
        routeKey = SectionRoutes.ADDONS,
        titleRes = R.string.tab_addons,
        subtitleRes = R.string.addons_desc,
        iconRes = R.drawable.ic_addon_tile,
        palette = HomeRowPalette.SECONDARY,
    ),
)

// Launcher group: hidden apps alone, with the tertiary container color as in
// the original launcher_settings_title card.
private val HomeLauncherSections = listOf(
    HomeSectionEntry(
        routeKey = SectionRoutes.HIDDEN_APPS,
        titleRes = R.string.tab_hidden_apps,
        subtitleRes = R.string.hidden_apps_subtitle,
        iconRes = R.drawable.ic_archive,
        palette = HomeRowPalette.TERTIARY,
    ),
)

// Home hub: header plus two grouped cards of section rows (system + misc).
// Taps route through the shared selected-key state, so the transition
// matches a dock tab switch exactly and back returns here via tab history.
@Composable
fun HomeScreen(
    onOpenSection: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = sectionContentPadding(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "__home_header__") {
            Column(
                modifier = Modifier.padding(horizontal = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = stringResource(R.string.tab_home),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.overflow_title),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item(key = "__home_system_card__") {
            SectionGroupCard(title = stringResource(R.string.home_group_system)) {
                HomeSystemSections.forEachIndexed { index, entry ->
                    if (index > 0) HorizontalDivider()
                    SectionNavRow(
                        title = stringResource(entry.titleRes),
                        subtitle = stringResource(entry.subtitleRes),
                        iconRes = entry.iconRes,
                        iconContainerColor = entry.palette.container(),
                        iconContentColor = entry.palette.content(),
                        onClick = { onOpenSection(entry.routeKey) },
                    )
                }
            }
        }
        item(key = "__home_launcher_card__") {
            SectionGroupCard(title = stringResource(R.string.home_group_misc)) {
                HomeLauncherSections.forEachIndexed { index, entry ->
                    if (index > 0) HorizontalDivider()
                    SectionNavRow(
                        title = stringResource(entry.titleRes),
                        subtitle = stringResource(entry.subtitleRes),
                        iconRes = entry.iconRes,
                        iconContainerColor = entry.palette.container(),
                        iconContentColor = entry.palette.content(),
                        onClick = { onOpenSection(entry.routeKey) },
                    )
                }
            }
        }
    }
}
