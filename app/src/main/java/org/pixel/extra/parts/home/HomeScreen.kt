package org.pixel.extra.parts.home

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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.pixel.extra.parts.R
import org.pixel.extra.parts.navigation.SectionRoutes
import org.pixel.extra.parts.ui.sectionContentPadding

// One row of the home hub. subtitleRes is null when the section exposes no
// short description string; the row then renders title-only.
private data class HomeSectionEntry(
    val routeKey: String,
    @StringRes val titleRes: Int,
    @StringRes val subtitleRes: Int?,
    @DrawableRes val iconRes: Int,
)

// Same sections as the dock pins and overflow slots, minus Home/Settings:
// tap routes through the shared selected-key state, so the transition matches
// a dock tab switch exactly.
private val HomeSections = listOf(
    HomeSectionEntry(
        routeKey = SectionRoutes.DISPLAY,
        titleRes = R.string.tab_display,
        subtitleRes = R.string.display_desc,
        iconRes = R.drawable.ic_search,
    ),
    HomeSectionEntry(
        routeKey = SectionRoutes.SYSTEM_UI,
        titleRes = R.string.tab_systemui,
        subtitleRes = R.string.sysui_section_desc,
        iconRes = R.drawable.ic_info,
    ),
    HomeSectionEntry(
        routeKey = SectionRoutes.OVERSCROLL,
        titleRes = R.string.tab_overscroll,
        subtitleRes = R.string.os_header_desc,
        iconRes = R.drawable.ic_more,
    ),
    HomeSectionEntry(
        routeKey = SectionRoutes.THERMAL,
        titleRes = R.string.tab_thermal,
        subtitleRes = null,
        iconRes = R.drawable.ic_share,
    ),
    HomeSectionEntry(
        routeKey = SectionRoutes.ICONS,
        titleRes = R.string.tab_icons,
        subtitleRes = R.string.icons_header_sub,
        iconRes = R.drawable.ic_favorite,
    ),
    HomeSectionEntry(
        routeKey = SectionRoutes.HIDDEN_APPS,
        titleRes = R.string.tab_hidden_apps,
        subtitleRes = R.string.hidden_apps_subtitle,
        iconRes = R.drawable.ic_archive,
    ),
    HomeSectionEntry(
        routeKey = SectionRoutes.ADDONS,
        titleRes = R.string.tab_addons,
        subtitleRes = R.string.addons_desc,
        iconRes = R.drawable.ic_extension,
    ),
)

// Home hub: a flat edge-to-edge list of every top-level section. Rows are
// plain full-width surfaces (no background, no border, no capsules): text,
// an optional 24.dp box-free icon, a 20.dp chevron and a divider.
@Composable
fun HomeScreen(
    onOpenSection: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = sectionContentPadding(),
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
        items(
            items = HomeSections,
            key = { it.routeKey },
        ) { entry ->
            HomeSectionRow(
                title = stringResource(entry.titleRes),
                subtitle = entry.subtitleRes?.let { stringResource(it) },
                iconRes = entry.iconRes,
                showDivider = entry != HomeSections.last(),
                onClick = { onOpenSection(entry.routeKey) },
            )
        }
    }
}

// Plain section row per guideline §6.11: full-width, min-height 56.dp,
// no background or border, divider below (except after the last row).
@Composable
private fun HomeSectionRow(
    title: String,
    subtitle: String?,
    @DrawableRes iconRes: Int,
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
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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
