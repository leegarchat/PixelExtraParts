package org.pixel.extra.parts.icons

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.pixel.extra.parts.R
import org.pixel.extra.parts.ui.edgeOutline

// Icon-packs block: collapsible header with the pack count and one card per
// pack with status, update banner and the apply/view/undo actions. The apply
// mode, partial selection and contents dialogs live in IconsPackDialogs.
@Composable
internal fun IconsPacksSection(
    packs: List<IconPackInfo>,
    expanded: Boolean,
    busy: Boolean,
    onExpandChange: (Boolean) -> Unit,
    onApplyAll: (IconPackInfo) -> Unit,
    onApplyPartial: (IconPackInfo) -> Unit,
    onViewPack: (IconPackInfo) -> Unit,
    onApplyUpdate: (IconPackInfo) -> Unit,
    onCancelAll: (IconPackInfo) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        IconsExpandableHeader(
            title = stringResource(R.string.icons_packs_section),
            subtitle = stringResource(R.string.icons_packs_count, packs.size),
            icon = Icons.Rounded.Apps,
            expanded = expanded,
            onExpandChange = onExpandChange,
        )
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (packs.isEmpty()) {
                    IconsEmptyState(stringResource(R.string.icons_packs_empty))
                } else {
                    packs.forEach { pack ->
                        IconPackCard(
                            pack = pack,
                            busy = busy,
                            onApplyAll = { onApplyAll(pack) },
                            onApplyPartial = { onApplyPartial(pack) },
                            onViewPack = { onViewPack(pack) },
                            onApplyUpdate = { onApplyUpdate(pack) },
                            onCancelAll = { onCancelAll(pack) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun IconPackCard(
    pack: IconPackInfo,
    busy: Boolean,
    onApplyAll: () -> Unit,
    onApplyPartial: () -> Unit,
    onViewPack: () -> Unit,
    onApplyUpdate: () -> Unit,
    onCancelAll: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    var actionsExpanded by rememberSaveable(pack.packageName) { mutableStateOf(false) }
    val shape = RoundedCornerShape(18.dp)
    val container = MaterialTheme.colorScheme.surfaceContainerHigh
    Surface(
        color = container,
        shape = shape,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, container.edgeOutline(), shape)
            .clip(shape)
            .clickable {
                haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                actionsExpanded = !actionsExpanded
            },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PackDrawableIcon(drawable = pack.icon, size = 48.dp)
                Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(
                        text = pack.label,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = pack.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = if (pack.installed) {
                            stringResource(iconPackStatusText(pack.status))
                        } else {
                            stringResource(R.string.icons_pack_missing_status)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (pack.installed) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                Icon(
                    painter = painterResource(R.drawable.ic_chevron_down),
                    contentDescription = null,
                    modifier = Modifier.rotate(if (actionsExpanded) 180f else 0f),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (busy) {
                    CircularProgressIndicator(modifier = Modifier.size(26.dp), strokeWidth = 2.dp)
                }
            }
            Text(
                text = stringResource(
                    R.string.icons_pack_counts,
                    pack.supportedPackageCount,
                    pack.supportedIconCount,
                    pack.appliedPackageCount,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (pack.requiresUpdate) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.28f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.icons_pack_requires_update),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onApplyUpdate, enabled = !busy) {
                        Text(stringResource(R.string.icons_pack_apply_update), maxLines = 1)
                    }
                }
            }
            AnimatedVisibility(
                visible = actionsExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!pack.installed) {
                        Text(
                            text = stringResource(R.string.icons_pack_missing_msg),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.28f))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                    if (pack.installed) {
                        Button(onClick = onApplyAll, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Rounded.Apps, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.icons_pack_apply_all), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    OutlinedButton(onClick = onApplyPartial, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.icons_pack_apply_partial), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (pack.installed) {
                        OutlinedButton(onClick = onViewPack, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Rounded.Apps, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.icons_pack_view), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    OutlinedButton(
                        onClick = onCancelAll,
                        enabled = !busy && pack.appliedPackageCount > 0,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.icons_pack_cancel_all), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}
