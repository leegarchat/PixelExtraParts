package org.pixel.extra.parts.display

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.pixel.extra.parts.R
import org.pixel.extra.parts.navigation.SectionRoutes
import org.pixel.extra.parts.ui.sectionContentPadding

// Per-row icon tint, mirroring the old DisplaySettingsActivity row container
// hues (primary/secondary/tertiary/error) without any background box, so the
// flat §6.11 rows keep their color richness.
private enum class DisplayHubTint {
    PRIMARY,
    SECONDARY,
    TERTIARY,
    ERROR,
}

@Composable
private fun DisplayHubTint.color(): Color = when (this) {
    DisplayHubTint.PRIMARY -> MaterialTheme.colorScheme.onPrimaryContainer
    DisplayHubTint.SECONDARY -> MaterialTheme.colorScheme.onSecondaryContainer
    DisplayHubTint.TERTIARY -> MaterialTheme.colorScheme.onTertiaryContainer
    DisplayHubTint.ERROR -> MaterialTheme.colorScheme.onErrorContainer
}

// One hub row: routes to a display subsection page through the shared
// selected-key state, so back walks the tab history back to this hub.
private data class DisplayHubEntry(
    val routeKey: String,
    @StringRes val titleRes: Int,
    @StringRes val subtitleRes: Int,
    @DrawableRes val iconRes: Int,
    val tint: DisplayHubTint,
)

// Same four subsections as the old DisplaySettingsActivity group card:
// tap opens a dedicated subpage whose content is the existing section card.
private val DisplayHubEntries = listOf(
    DisplayHubEntry(
        routeKey = SectionRoutes.DISPLAY_SATURATION,
        titleRes = R.string.saturation_title,
        subtitleRes = R.string.saturation_summary,
        iconRes = R.drawable.ic_saturation_tile,
        tint = DisplayHubTint.PRIMARY,
    ),
    DisplayHubEntry(
        routeKey = SectionRoutes.DISPLAY_AUTO_HBM,
        titleRes = R.string.auto_hbm_title,
        subtitleRes = R.string.auto_hbm_summary,
        iconRes = R.drawable.ic_auto_hbm_tile,
        tint = DisplayHubTint.SECONDARY,
    ),
    DisplayHubEntry(
        routeKey = SectionRoutes.DISPLAY_DOUBLE_TAP,
        titleRes = R.string.dtw_title,
        subtitleRes = R.string.dtw_summary,
        iconRes = R.drawable.ic_dt2w_tile,
        tint = DisplayHubTint.TERTIARY,
    ),
    DisplayHubEntry(
        routeKey = SectionRoutes.DISPLAY_AUTO_LOCK,
        titleRes = R.string.auto_lock_title,
        subtitleRes = R.string.auto_lock_summary,
        iconRes = R.drawable.ic_auto_lock_tile,
        tint = DisplayHubTint.ERROR,
    ),
)

// Display hub: header plus one flat §6.11 row per subsection
// (icon + title + subtitle + chevron). The list is edge-to-edge and scrolls
// under the root top bar and dock; the root owns blur and navigation.
@Composable
fun DisplayScreen(
    onOpenSubsection: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = sectionContentPadding(),
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
        items(
            items = DisplayHubEntries,
            key = { it.routeKey },
        ) { entry ->
            DisplayHubRow(
                title = stringResource(entry.titleRes),
                subtitle = stringResource(entry.subtitleRes),
                iconRes = entry.iconRes,
                iconTint = entry.tint.color(),
                showDivider = entry != DisplayHubEntries.last(),
                onClick = { onOpenSubsection(entry.routeKey) },
            )
        }
    }
}

// Plain subsection row per guideline §6.11: full-width, min-height 56.dp,
// no background or border, divider below (except after the last row).
// Typography and single-line ellipsis mirror the old dashboard's
// MainMenuNavigationRow; the colored tile glyph carries its richness.
@Composable
private fun DisplayHubRow(
    title: String,
    subtitle: String,
    @DrawableRes iconRes: Int,
    iconTint: Color,
    showDivider: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clickable(onClickLabel = title) {
                    haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                    onClick()
                }
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(24.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                painter = painterResource(R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
        if (showDivider) {
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
            )
        }
    }
}
