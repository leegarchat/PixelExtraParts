package org.pixel.extra.parts.display

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

// Icon-box palette for the display hub, mirroring the original
// DisplaySettingsActivity row container hues.
private enum class DisplayHubPalette {
    PRIMARY,
    SECONDARY,
    TERTIARY,
    ERROR,
}

@Composable
private fun DisplayHubPalette.container(): Color = when (this) {
    DisplayHubPalette.PRIMARY -> MaterialTheme.colorScheme.primaryContainer
    DisplayHubPalette.SECONDARY -> MaterialTheme.colorScheme.secondaryContainer
    DisplayHubPalette.TERTIARY -> MaterialTheme.colorScheme.tertiaryContainer
    DisplayHubPalette.ERROR -> MaterialTheme.colorScheme.errorContainer
}

@Composable
private fun DisplayHubPalette.content(): Color = when (this) {
    DisplayHubPalette.PRIMARY -> MaterialTheme.colorScheme.onPrimaryContainer
    DisplayHubPalette.SECONDARY -> MaterialTheme.colorScheme.onSecondaryContainer
    DisplayHubPalette.TERTIARY -> MaterialTheme.colorScheme.onTertiaryContainer
    DisplayHubPalette.ERROR -> MaterialTheme.colorScheme.onErrorContainer
}

// One hub row: routes to a display subsection page through the shared
// selected-key state, so back walks the tab history back to this hub.
private data class DisplayHubEntry(
    val routeKey: String,
    @StringRes val titleRes: Int,
    @StringRes val subtitleRes: Int,
    @DrawableRes val iconRes: Int,
    val palette: DisplayHubPalette,
)

// Same four subsections as the original DisplaySettingsActivity group card:
// Saturation (primary), AutoHBM (secondary), double-tap (tertiary) and
// auto-lock (error container).
private val DisplayHubEntries = listOf(
    DisplayHubEntry(
        routeKey = SectionRoutes.DISPLAY_SATURATION,
        titleRes = R.string.saturation_title,
        subtitleRes = R.string.saturation_summary,
        iconRes = R.drawable.ic_saturation_tile,
        palette = DisplayHubPalette.PRIMARY,
    ),
    DisplayHubEntry(
        routeKey = SectionRoutes.DISPLAY_AUTO_HBM,
        titleRes = R.string.auto_hbm_title,
        subtitleRes = R.string.auto_hbm_summary,
        iconRes = R.drawable.ic_auto_hbm_tile,
        palette = DisplayHubPalette.SECONDARY,
    ),
    DisplayHubEntry(
        routeKey = SectionRoutes.DISPLAY_DOUBLE_TAP,
        titleRes = R.string.dtw_title,
        subtitleRes = R.string.dtw_summary,
        iconRes = R.drawable.ic_dt2w_tile,
        palette = DisplayHubPalette.TERTIARY,
    ),
    DisplayHubEntry(
        routeKey = SectionRoutes.DISPLAY_AUTO_LOCK,
        titleRes = R.string.auto_lock_title,
        subtitleRes = R.string.auto_lock_summary,
        iconRes = R.drawable.ic_auto_lock_tile,
        palette = DisplayHubPalette.ERROR,
    ),
)

// Display hub: header plus one grouped card with a row per subsection.
// The list is edge-to-edge and scrolls under the root top bar and dock;
// the root owns blur and navigation.
@Composable
fun DisplayScreen(
    onOpenSubsection: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = sectionContentPadding(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "__display_header__") {
            Column(
                modifier = Modifier.padding(horizontal = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = stringResource(R.string.tab_display),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.display_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item(key = "__display_hub_card__") {
            SectionGroupCard(title = stringResource(R.string.display_group_screen)) {
                DisplayHubEntries.forEachIndexed { index, entry ->
                    if (index > 0) HorizontalDivider()
                    SectionNavRow(
                        title = stringResource(entry.titleRes),
                        subtitle = stringResource(entry.subtitleRes),
                        iconRes = entry.iconRes,
                        iconContainerColor = entry.palette.container(),
                        iconContentColor = entry.palette.content(),
                        onClick = { onOpenSubsection(entry.routeKey) },
                    )
                }
            }
        }
    }
}
