package org.pixel.extra.parts.addons

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.pixel.extra.parts.R
import org.pixel.extra.parts.ui.GroupCard
import org.pixel.extra.parts.ui.SettingSwitchRow
import org.pixel.extra.parts.ui.SettingSwitchRowVariant
import org.pixel.extra.parts.ui.edgeOutline
import org.pixel.extra.parts.ui.sectionContentPadding

private const val ROUTE_SEPARATOR = "/"

// Addons section: hub of declarative addon packs plus one settings page per
// pack page. Navigation is a light string route ("addonId/pageId") over
// AnimatedContent, mirroring the SectionRoutes approach: no nav library.
@Composable
fun AddonsScreen(modifier: Modifier = Modifier) {
    var route by rememberSaveable { mutableStateOf<String?>(null) }
    BackHandler(enabled = route != null) {
        route = null
    }
    AnimatedContent(
        targetState = route,
        transitionSpec = {
            val forward = targetState != null
            val direction = if (forward) 1 else -1
            (slideInHorizontally(
                animationSpec = tween(320, easing = FastOutSlowInEasing),
                initialOffsetX = { fullWidth -> (fullWidth * 0.18f * direction).toInt() },
            ) + fadeIn(
                animationSpec = tween(260),
            ) + scaleIn(
                initialScale = 0.96f,
                animationSpec = tween(320, easing = FastOutSlowInEasing),
            )).togetherWith(
                slideOutHorizontally(
                    animationSpec = tween(260, easing = FastOutSlowInEasing),
                    targetOffsetX = { fullWidth -> (-fullWidth * 0.18f * direction).toInt() },
                ) + fadeOut(
                    animationSpec = tween(200),
                ) + scaleOut(
                    targetScale = 0.96f,
                    animationSpec = tween(260, easing = FastOutSlowInEasing),
                ),
            ).using(SizeTransform(clip = false))
        },
        label = "AddonRouteTransition",
        modifier = modifier,
    ) { current ->
        val resolved = current?.let { AddonsRegistry.findPage(it) }
        if (resolved == null) {
            AddonHubList(
                onOpenPage = { addon, page ->
                    route = addon.id + ROUTE_SEPARATOR + page.id
                },
            )
        } else {
            AddonPageBody(
                page = resolved.second,
                onBack = { route = null },
            )
        }
    }
}

@Composable
private fun AddonHubList(
    onOpenPage: (AddonDescriptor, AddonPage) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = sectionContentPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "__addons_header__") {
            Column(
                modifier = Modifier.padding(horizontal = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = stringResource(R.string.tab_addons),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.addons_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        AddonsRegistry.addons.forEach { addon ->
            item(key = "addon_" + addon.id) {
                AddonHubCard(addon = addon, onOpenPage = onOpenPage)
            }
        }
    }
}

@Composable
private fun AddonHubCard(
    addon: AddonDescriptor,
    onOpenPage: (AddonDescriptor, AddonPage) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val enabledKey = remember(addon.id) { AddonKeys.enabledKey(addon.id) }
    var enabled by remember(enabledKey) {
        mutableStateOf(AddonStore.isEnabled(context, enabledKey, true))
    }
    GroupCard(
        title = stringResource(addon.nameRes),
        subtitle = stringResource(addon.descriptionRes),
        modifier = modifier,
    ) {
        SettingSwitchRow(
            title = stringResource(R.string.addon_enable_title),
            summary = stringResource(R.string.addon_enable_desc),
            checked = enabled,
            onCheckedChange = { value ->
                enabled = value
                AddonStore.setEnabled(context, enabledKey, value)
            },
            variant = SettingSwitchRowVariant.Plain,
        )
        addon.pages.forEach { page ->
            AddonPageRow(
                title = stringResource(page.titleRes),
                subtitle = stringResource(page.descriptionRes),
                onClick = { onOpenPage(addon, page) },
            )
        }
    }
}

@Composable
private fun AddonPageRow(
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val shape = RoundedCornerShape(16.dp)
    val container = MaterialTheme.colorScheme.surfaceContainer
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .border(width = 1.dp, color = container.edgeOutline(), shape = shape)
            .clickable {
                haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                onClick()
            }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (!subtitle.isNullOrBlank()) {
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
        )
    }
}

@Composable
private fun AddonPageBody(
    page: AddonPage,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = sectionContentPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "__addon_page_back__") {
            AddonBackRow(onBack = onBack)
        }
        item(key = "__addon_page_header__") {
            Column(
                modifier = Modifier.padding(horizontal = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = stringResource(page.titleRes),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(page.descriptionRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item(key = "__addon_page_nodes__") {
            AddonNodesColumn(nodes = page.nodes)
        }
    }
}

@Composable
private fun AddonBackRow(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onBack()
            }
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_arrow_back),
            contentDescription = stringResource(R.string.addon_back),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = stringResource(R.string.addon_back),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
