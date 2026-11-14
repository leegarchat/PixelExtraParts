package org.pixel.customparts.activities

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.pixel.customparts.R
import org.pixel.customparts.icons.IconApplyProgress
import androidx.compose.ui.unit.Dp
import org.pixel.customparts.icons.IconPackApplyStatus
import org.pixel.customparts.icons.IconPackInfo
import org.pixel.customparts.icons.IconPackManager
import org.pixel.customparts.icons.InstalledIconApp
import org.pixel.customparts.ui.GenericSwitchRow
import org.pixel.customparts.ui.GraphicsLayerRecordingState
import org.pixel.customparts.ui.RadioSelectionGroup
import org.pixel.customparts.ui.SettingsGroupCard
import org.pixel.customparts.ui.SliderSettingFloat
import org.pixel.customparts.ui.addons.AddonMainEntry
import org.pixel.customparts.ui.addons.AddonMainEntryRow
import org.pixel.customparts.ui.edgeOutline

internal data class AppIconModuleFlags(
    val moduleEnabled: Boolean,
    val launcherOnly: Boolean
)

internal data class AppIconSystemShapeFlags(
    val stretchShape: Boolean,
    val removeShape: Boolean,
    val scalePercent: Float
)

internal data class AppIconNotificationShapeFlags(
    val stretchShape: Boolean,
    val removeShape: Boolean,
    val scalePercent: Float
)

internal data class AppIconLauncherShapeFlags(
    val stretchShape: Boolean,
    val removeShape: Boolean,
    val scalePercent: Float
)

internal data class AppIconShapeTintFlags(
    val backgroundTintMode: Int,
    val backgroundTintColor: Int,
    val foregroundTintMode: Int,
    val foregroundTintColor: Int
)

internal data class AppIconChooserItem(
    val iconPackPackage: String,
    val iconPackLabel: String,
    val drawableName: String,
    val label: String,
    val detected: Boolean,
    val appPackageName: String?
)

@Stable
internal data class IconPackFlightTarget(
    val key: String,
    val pack: IconPackInfo,
    val initialTab: Int = 0,
    val source: Rect = Rect.Zero,
    val sourceBoundsProvider: () -> Rect = { source },
    val isTrashArmedProvider: () -> Boolean = { false },
    val sessionId: Long = System.nanoTime()
)

@Stable
internal data class AppDetailFlightTarget(
    val app: InstalledIconApp,
    val source: Rect = Rect.Zero,
    val sourceBoundsProvider: () -> Rect = { source },
    val sessionId: Long = System.nanoTime()
)

@Stable
internal data class ColorPickerFlightTarget(
    val key: String,
    val title: String,
    val initialColor: Int,
    val source: Rect = Rect.Zero,
    val sourceBoundsProvider: () -> Rect = { source },
    val onColorSelected: (Int) -> Unit,
    val sessionId: Long = System.nanoTime()
)

private const val APP_ICON_TINT_MODE_OFF = 0
private const val APP_ICON_TINT_MODE_CUSTOM = 1
private const val APP_ICON_TINT_MODE_AUTO = 2

// ── Fixed-Geometry Shimmer Skeletons (Zero Layout Jumps on Initial Load) ──

@Composable
internal fun LoadingCard() {
    SkeletonPackPlaceholderCard()
}

@Composable
internal fun SkeletonPackPlaceholderCard() {
    val pulse = rememberInfiniteTransition(label = "SkeletonPackPulse")
    val alpha by pulse.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(
            animation = tween(700),
            repeatMode = RepeatMode.Reverse
        ),
        label = "SkeletonAlpha"
    )
    val bgColor = MaterialTheme.colorScheme.surface
    val blockColor = MaterialTheme.colorScheme.surfaceContainerHighest
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(216.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        border = BorderStroke(1.dp, bgColor.edgeOutline())
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .alpha(alpha),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(blockColor)
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.55f)
                            .height(16.dp)
                            .clip(CircleShape)
                            .background(blockColor)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.38f)
                            .height(12.dp)
                            .clip(CircleShape)
                            .background(blockColor)
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(blockColor)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(blockColor)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(blockColor)
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(blockColor)
                )
            }
        }
    }
}

@Composable
internal fun SkeletonAppStreamCard() {
    val pulse = rememberInfiniteTransition(label = "SkeletonStreamPulse")
    val alpha by pulse.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(
            animation = tween(700),
            repeatMode = RepeatMode.Reverse
        ),
        label = "SkeletonStreamAlpha"
    )
    val bgColor = MaterialTheme.colorScheme.surface
    val blockColor = MaterialTheme.colorScheme.surfaceContainerHighest
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        border = BorderStroke(1.dp, bgColor.edgeOutline())
    ) {
        Column(modifier = Modifier.alpha(alpha)) {
            repeat(6) { idx ->
                if (idx > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 20.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp)
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(blockColor)
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.48f)
                                .height(15.dp)
                                .clip(CircleShape)
                                .background(blockColor)
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.32f)
                                .height(11.dp)
                                .clip(CircleShape)
                                .background(blockColor)
                        )
                    }
                }
            }
        }
    }
}

// ── Tab 2: Seamless Unified App Stream Row (1:1 Flight Ready) ─────────────

@Composable
internal fun InstalledAppStreamRow(
    app: InstalledIconApp,
    sectionTitle: String?,
    isFirst: Boolean,
    isLast: Boolean,
    hasShapeOverride: Boolean,
    cachedCustomBitmap: Bitmap?,
    onCacheBitmap: (Bitmap?) -> Unit,
    isHiddenForFlight: Boolean,
    onOpenFlight: (Rect, () -> Rect) -> Unit
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val iconSizePx = with(density) { 48.dp.roundToPx() }

    var rowBounds by remember { mutableStateOf(Rect.Zero) }

    LaunchedEffect(app.packageName, app.appliedIconFileName) {
        if (app.appliedIconFileName != null && cachedCustomBitmap == null) {
            val loaded = IconPackManager.loadAppliedIconBitmap(context, app.packageName, iconSizePx)
            if (loaded != null) onCacheBitmap(loaded)
        }
    }

    val shape = when {
        isFirst && isLast -> RoundedCornerShape(24.dp)
        isFirst -> RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        isLast -> RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)
        else -> RoundedCornerShape(0.dp)
    }

    val bgColor = MaterialTheme.colorScheme.surface
    val outlineColor = bgColor.edgeOutline()
    val strokePx = with(density) { 1.dp.toPx() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(bgColor)
            .then(
                if (isFirst || isLast) {
                    Modifier.border(1.dp, outlineColor, shape)
                } else {
                    Modifier.drawWithContent {
                        drawContent()
                        drawLine(
                            color = outlineColor,
                            start = Offset(0f, 0f),
                            end = Offset(0f, size.height),
                            strokeWidth = strokePx
                        )
                        drawLine(
                            color = outlineColor,
                            start = Offset(size.width, 0f),
                            end = Offset(size.width, size.height),
                            strokeWidth = strokePx
                        )
                    }
                }
            )
    ) {
        if (isFirst && !sectionTitle.isNullOrBlank()) {
            Text(
                text = sectionTitle,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 6.dp)
            )
        }

        if (!isFirst) {
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                modifier = Modifier.padding(horizontal = 20.dp)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .onPlaced {
                    val r = it.unclippedBoundsInRoot()
                    if (r.width > 1f && r.height > 1f) rowBounds = r
                }
                .onGloballyPositioned {
                    val r = it.unclippedBoundsInRoot()
                    if (r.width > 1f && r.height > 1f) rowBounds = r
                }
                .alpha(if (isHiddenForFlight) 0f else 1f)
                .clickable(enabled = !isHiddenForFlight) {
                    haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                    onOpenFlight(rowBounds) { rowBounds }
                }
                .padding(
                    start = 20.dp,
                    end = 20.dp,
                    top = 13.dp,
                    bottom = if (isLast) 16.dp else 13.dp
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val activeBitmap = if (app.appliedIconFileName != null) cachedCustomBitmap else null
            if (activeBitmap != null) {
                BitmapIcon(bitmap = activeBitmap, size = 44.dp, fallback = Icons.Rounded.Apps)
            } else {
                DrawableIcon(drawable = app.icon, size = 44.dp, fallback = Icons.Rounded.Apps)
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 16.dp, end = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = app.label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = app.appliedIconPackLabel?.let {
                        stringResource(R.string.app_icons_applied_from_format, it)
                    } ?: app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (app.appliedIconPackLabel != null) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (hasShapeOverride) {
                val shapeBadgeBg = MaterialTheme.colorScheme.tertiaryContainer
                Box(
                    modifier = Modifier
                        .padding(end = 6.dp)
                        .clip(CircleShape)
                        .background(shapeBadgeBg)
                        .border(1.dp, shapeBadgeBg.edgeOutline(), CircleShape)
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "S",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }

            Box(
                modifier = Modifier.size(28.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

// ── Tab 1: Clean Icon Pack Card with 1:1 Flight Trigger ───────────────────

@Composable
internal fun IconPackSummaryBlock(
    pack: IconPackInfo,
    modifier: Modifier = Modifier
) {
    val coverageFraction by animateFloatAsState(
        targetValue = if (pack.supportedPackageCount > 0) {
            (pack.appliedPackageCount.toFloat() / pack.supportedPackageCount.toFloat()).coerceIn(0f, 1f)
        } else 0f,
        animationSpec = spring(
            stiffness = Spring.StiffnessMediumLow,
            dampingRatio = Spring.DampingRatioNoBouncy
        ),
        label = "PackCoverageFraction"
    )

    val badgeBg by animateColorAsState(
        targetValue = when {
            !pack.installed -> MaterialTheme.colorScheme.errorContainer
            pack.status == IconPackApplyStatus.APPLIED_FULL -> MaterialTheme.colorScheme.primaryContainer
            pack.status == IconPackApplyStatus.APPLIED_PARTIAL -> MaterialTheme.colorScheme.secondaryContainer
            else -> MaterialTheme.colorScheme.surfaceContainerHighest
        },
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "PackStatusBadgeBg"
    )
    val badgeFg by animateColorAsState(
        targetValue = when {
            !pack.installed -> MaterialTheme.colorScheme.onErrorContainer
            pack.status == IconPackApplyStatus.APPLIED_FULL -> MaterialTheme.colorScheme.onPrimaryContainer
            pack.status == IconPackApplyStatus.APPLIED_PARTIAL -> MaterialTheme.colorScheme.onSecondaryContainer
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "PackStatusBadgeFg"
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = stringResource(
                R.string.app_icons_supported_count_format,
                pack.supportedPackageCount,
                pack.supportedIconCount,
                pack.appliedPackageCount
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        LinearProgressIndicator(
            progress = { coverageFraction },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(CircleShape)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(badgeBg)
                    .border(1.dp, badgeBg.edgeOutline(), CircleShape)
                    .padding(horizontal = 12.dp, vertical = 5.dp)
            ) {
                Text(
                    text = if (pack.installed) {
                        iconPackStatusText(pack.status)
                    } else {
                        stringResource(R.string.app_icons_pack_missing_status)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = badgeFg,
                    maxLines = 1
                )
            }

            if (pack.installed && pack.requiresUpdate) {
                val updBg = MaterialTheme.colorScheme.tertiaryContainer
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(updBg)
                        .border(1.dp, updBg.edgeOutline(), CircleShape)
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = stringResource(R.string.app_icons_requires_update),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
internal fun IconPackCard(
    pack: IconPackInfo,
    busy: Boolean,
    trashArmed: Boolean,
    isHiddenForFlight: Boolean = false,
    onExpand: (Int, Rect, () -> Rect) -> Unit,
    onApplyUpdate: () -> Unit,
    onTrashClick: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    var cardBounds by remember { mutableStateOf(Rect.Zero) }

    val isAppliedAny = pack.appliedPackageCount > 0
    val cardBg by animateColorAsState(
        targetValue = if (isAppliedAny) {
            MaterialTheme.colorScheme.surfaceContainerHigh
        } else {
            MaterialTheme.colorScheme.surface
        },
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "PackCardBg"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .onPlaced {
                val r = it.unclippedBoundsInRoot()
                if (r.width > 1f && r.height > 1f) cardBounds = r
            }
            .onGloballyPositioned {
                val r = it.unclippedBoundsInRoot()
                if (r.width > 1f && r.height > 1f) cardBounds = r
            }
            .alpha(if (isHiddenForFlight) 0f else 1f)
            .clip(RoundedCornerShape(24.dp))
            .clickable(enabled = !isHiddenForFlight && !busy) {
                haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                onExpand(0, cardBounds) { cardBounds }
            },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = BorderStroke(1.dp, cardBg.edgeOutline())
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DrawableIcon(drawable = pack.icon, size = 52.dp, fallback = Icons.Rounded.Apps)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = pack.label,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = pack.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Box(
                    modifier = Modifier.size(36.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            IconPackSummaryBlock(pack = pack)
        }
    }
}

// ── Tab 3: Structured Settings Content (Flight Color Picker Ready) ───────

@Composable
internal fun AppIconsAddonInjectedSettings(
    entries: List<AddonMainEntry>,
    onIconShapeClick: () -> Unit
) {
    val context = LocalContext.current
    SettingsGroupCard(title = stringResource(R.string.app_icons_advanced_settings_title)) {
        AppIconsNavigationRow(
            title = stringResource(R.string.icon_shape_title),
            summary = stringResource(R.string.icon_shape_summary),
            onClick = onIconShapeClick
        )
        if (entries.isNotEmpty()) HorizontalDivider()
        entries.forEachIndexed { index, entry ->
            if (index > 0) HorizontalDivider()
            key(entry.rawId) {
                AddonMainEntryRow(
                    entry = entry,
                    iconContainerSize = 44.dp,
                    onClick = {
                        AddonPageActivity.start(
                            context = context,
                            addonId = entry.addonId,
                            pageId = entry.leafId,
                            title = entry.title,
                            includeTargetActivityEntries = true
                        )
                    }
                )
            }
        }
    }
}

@Composable
internal fun AppIconsSettingsTabContent(
    checked: Boolean,
    launcherOnlyChecked: Boolean,
    selectedAreaTab: Int,
    onAreaTabSelected: (Int) -> Unit,
    systemStretchShapeChecked: Boolean,
    systemRemoveShapeChecked: Boolean,
    notificationStretchShapeChecked: Boolean,
    notificationRemoveShapeChecked: Boolean,
    launcherStretchShapeChecked: Boolean,
    launcherRemoveShapeChecked: Boolean,
    systemShapeScalePercent: Float,
    notificationShapeScalePercent: Float,
    launcherShapeScalePercent: Float,
    backgroundTintMode: Int,
    backgroundTintColor: Int,
    foregroundTintMode: Int,
    foregroundTintColor: Int,
    showLauncherControls: Boolean,
    activeColorFlightKey: String?,
    onIconShapeClick: () -> Unit,
    onCheckedChange: (Boolean) -> Unit,
    onLauncherOnlyChange: (Boolean) -> Unit,
    onSystemStretchShapeChange: (Boolean) -> Unit,
    onSystemRemoveShapeChange: (Boolean) -> Unit,
    onNotificationStretchShapeChange: (Boolean) -> Unit,
    onNotificationRemoveShapeChange: (Boolean) -> Unit,
    onLauncherStretchShapeChange: (Boolean) -> Unit,
    onLauncherRemoveShapeChange: (Boolean) -> Unit,
    onSystemShapeScaleChange: (Float) -> Unit,
    onNotificationShapeScaleChange: (Float) -> Unit,
    onLauncherShapeScaleChange: (Float) -> Unit,
    onBackgroundTintModeChange: (Int) -> Unit,
    onOpenBackgroundColorFlight: (Rect, () -> Rect) -> Unit,
    onForegroundTintModeChange: (Int) -> Unit,
    onOpenForegroundColorFlight: (Rect, () -> Rect) -> Unit
) {
    val systemOptionsEnabled = checked && !launcherOnlyChecked
    val launcherOptionsEnabled = checked

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SettingsGroupCard(title = stringResource(R.string.app_icons_title)) {
            GenericSwitchRow(
                title = stringResource(R.string.app_icons_module_enabled_title),
                summary = stringResource(R.string.app_icons_module_enabled_summary),
                checked = checked,
                enabled = true,
                onCheckedChange = onCheckedChange
            )
            if (showLauncherControls) {
                HorizontalDivider()
                GenericSwitchRow(
                    title = stringResource(R.string.app_icons_launcher_only_title),
                    summary = stringResource(R.string.app_icons_launcher_only_summary),
                    checked = checked && launcherOnlyChecked,
                    enabled = checked,
                    onCheckedChange = onLauncherOnlyChange
                )
            }
            HorizontalDivider()
            AppIconsNavigationRow(
                title = stringResource(R.string.icon_shape_title),
                summary = stringResource(R.string.icon_shape_summary),
                onClick = onIconShapeClick
            )
        }

        val areaTitles = buildList {
            add(stringResource(R.string.app_icons_per_app_system_area))
            add(stringResource(R.string.app_icons_per_app_notification_area))
            if (showLauncherControls) {
                add(stringResource(R.string.app_icons_per_app_launcher_area))
            }
        }
        val clampedAreaTab = selectedAreaTab.coerceIn(0, (areaTitles.size - 1).coerceAtLeast(0))
        val animatedAreaPos by animateFloatAsState(
            targetValue = clampedAreaTab.toFloat(),
            animationSpec = spring(
                stiffness = Spring.StiffnessMediumLow,
                dampingRatio = Spring.DampingRatioNoBouncy
            ),
            label = "GlobalShapeAreaTabPos"
        )

        SettingsGroupCard(
            title = stringResource(R.string.app_icons_per_app_shape_title),
            enabled = checked
        ) {
            AppIconsTabs(
                titles = areaTitles,
                position = animatedAreaPos,
                onTabClick = onAreaTabSelected
            )

            Spacer(Modifier.height(8.dp))

            AnimatedContent(
                targetState = clampedAreaTab,
                transitionSpec = {
                    val dir = if (targetState > initialState) 1 else -1
                    (slideInHorizontally(
                        animationSpec = spring(
                            stiffness = Spring.StiffnessMediumLow,
                            dampingRatio = Spring.DampingRatioNoBouncy
                        )
                    ) { fullW -> dir * fullW } + fadeIn(tween(180))) togetherWith
                        (slideOutHorizontally(
                            animationSpec = spring(
                                stiffness = Spring.StiffnessMediumLow,
                                dampingRatio = Spring.DampingRatioNoBouncy
                            )
                        ) { fullW -> -dir * fullW } + fadeOut(tween(140)))
                },
                label = "GlobalShapeAreaSlide"
            ) { tab ->
                Column(modifier = Modifier.fillMaxWidth()) {
                    when (tab) {
                        0 -> {
                            GenericSwitchRow(
                                title = stringResource(R.string.app_icons_system_stretch_shape_title),
                                summary = stringResource(R.string.app_icons_system_stretch_shape_summary),
                                checked = systemOptionsEnabled && systemStretchShapeChecked,
                                enabled = systemOptionsEnabled,
                                onCheckedChange = onSystemStretchShapeChange
                            )
                            HorizontalDivider()
                            AppIconsScaleSliderRow(
                                title = stringResource(R.string.app_icons_system_shape_scale_title),
                                value = systemShapeScalePercent,
                                enabled = systemOptionsEnabled && systemStretchShapeChecked && !systemRemoveShapeChecked,
                                onValueChange = onSystemShapeScaleChange
                            )
                            HorizontalDivider()
                            GenericSwitchRow(
                                title = stringResource(R.string.app_icons_system_remove_shape_title),
                                summary = stringResource(R.string.app_icons_system_remove_shape_summary),
                                checked = systemOptionsEnabled && systemRemoveShapeChecked,
                                enabled = systemOptionsEnabled,
                                onCheckedChange = onSystemRemoveShapeChange
                            )
                        }
                        1 -> {
                            GenericSwitchRow(
                                title = stringResource(R.string.app_icons_notification_stretch_shape_title),
                                summary = stringResource(R.string.app_icons_notification_stretch_shape_summary),
                                checked = systemOptionsEnabled && notificationStretchShapeChecked,
                                enabled = systemOptionsEnabled,
                                onCheckedChange = onNotificationStretchShapeChange
                            )
                            HorizontalDivider()
                            AppIconsScaleSliderRow(
                                title = stringResource(R.string.app_icons_notification_shape_scale_title),
                                value = notificationShapeScalePercent,
                                enabled = systemOptionsEnabled && notificationStretchShapeChecked && !notificationRemoveShapeChecked,
                                onValueChange = onNotificationShapeScaleChange
                            )
                            HorizontalDivider()
                            GenericSwitchRow(
                                title = stringResource(R.string.app_icons_notification_remove_shape_title),
                                summary = stringResource(R.string.app_icons_notification_remove_shape_summary),
                                checked = systemOptionsEnabled && notificationRemoveShapeChecked,
                                enabled = systemOptionsEnabled,
                                onCheckedChange = onNotificationRemoveShapeChange
                            )
                        }
                        else -> {
                            GenericSwitchRow(
                                title = stringResource(R.string.app_icons_launcher_stretch_shape_title),
                                summary = stringResource(R.string.app_icons_launcher_stretch_shape_summary),
                                checked = launcherOptionsEnabled && launcherStretchShapeChecked,
                                enabled = launcherOptionsEnabled,
                                onCheckedChange = onLauncherStretchShapeChange
                            )
                            HorizontalDivider()
                            AppIconsScaleSliderRow(
                                title = stringResource(R.string.app_icons_launcher_shape_scale_title),
                                value = launcherShapeScalePercent,
                                enabled = launcherOptionsEnabled && launcherStretchShapeChecked && !launcherRemoveShapeChecked,
                                onValueChange = onLauncherShapeScaleChange
                            )
                            HorizontalDivider()
                            GenericSwitchRow(
                                title = stringResource(R.string.app_icons_launcher_remove_shape_title),
                                summary = stringResource(R.string.app_icons_launcher_remove_shape_summary),
                                checked = launcherOptionsEnabled && launcherRemoveShapeChecked,
                                enabled = launcherOptionsEnabled,
                                onCheckedChange = onLauncherRemoveShapeChange
                            )
                        }
                    }
                }
            }
        }

        SettingsGroupCard(
            title = stringResource(R.string.app_icons_shape_background_tint_title),
            enabled = checked
        ) {
            AppIconsTintSection(
                title = stringResource(R.string.app_icons_shape_background_tint_title),
                summary = stringResource(R.string.app_icons_shape_background_tint_summary),
                mode = backgroundTintMode,
                color = backgroundTintColor,
                enabled = checked,
                isHiddenForFlight = activeColorFlightKey == "bg_tint",
                onModeChange = onBackgroundTintModeChange,
                onOpenColorFlight = onOpenBackgroundColorFlight
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            AppIconsTintSection(
                title = stringResource(R.string.app_icons_shape_foreground_tint_title),
                summary = stringResource(R.string.app_icons_shape_foreground_tint_summary),
                mode = foregroundTintMode,
                color = foregroundTintColor,
                enabled = checked,
                isHiddenForFlight = activeColorFlightKey == "fg_tint",
                onModeChange = onForegroundTintModeChange,
                onOpenColorFlight = onOpenForegroundColorFlight
            )
        }
    }
}

@Composable
internal fun AppIconsSwitchRow(
    title: String,
    summary: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    GenericSwitchRow(
        title = title,
        summary = summary,
        checked = checked,
        enabled = enabled,
        onCheckedChange = onCheckedChange
    )
}

@Composable
private fun AppIconsNavigationRow(
    title: String,
    summary: String,
    onClick: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                onClick()
            }
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val iconBg = MaterialTheme.colorScheme.primaryContainer
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(iconBg)
                .border(1.dp, iconBg.edgeOutline(), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Rounded.Apps,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AppIconsTintSection(
    title: String,
    summary: String,
    mode: Int,
    color: Int,
    enabled: Boolean,
    isHiddenForFlight: Boolean,
    onModeChange: (Int) -> Unit,
    onOpenColorFlight: (Rect, () -> Rect) -> Unit
) {
    val haptics = LocalHapticFeedback.current
    var swatchBounds by remember { mutableStateOf(Rect.Zero) }

    val options = listOf(
        stringResource(R.string.app_icons_tint_mode_off),
        stringResource(R.string.app_icons_tint_mode_auto),
        stringResource(R.string.app_icons_tint_mode_custom)
    )
    val selectedIdx = when (mode) {
        APP_ICON_TINT_MODE_AUTO -> 1
        APP_ICON_TINT_MODE_CUSTOM -> 2
        else -> 0
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        RadioSelectionGroup(
            title = title,
            options = options,
            selectedIndex = selectedIdx,
            enabled = enabled,
            onSelect = { index ->
                val newMode = when (index) {
                    1 -> APP_ICON_TINT_MODE_AUTO
                    2 -> APP_ICON_TINT_MODE_CUSTOM
                    else -> APP_ICON_TINT_MODE_OFF
                }
                onModeChange(newMode)
            }
        )

        Text(
            text = summary,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.4f),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp)
        )

        val rowBg = MaterialTheme.colorScheme.surfaceContainerHigh
        val rowShape = RoundedCornerShape(16.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .onPlaced {
                    val r = it.unclippedBoundsInRoot()
                    if (r.width > 1f && r.height > 1f) swatchBounds = r
                }
                .onGloballyPositioned {
                    val r = it.unclippedBoundsInRoot()
                    if (r.width > 1f && r.height > 1f) swatchBounds = r
                }
                .alpha(if (isHiddenForFlight) 0f else if (enabled) 1f else 0.4f)
                .clip(rowShape)
                .background(rowBg)
                .border(1.dp, rowBg.edgeOutline(), rowShape)
                .clickable(enabled = enabled && !isHiddenForFlight) {
                    haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                    onOpenColorFlight(swatchBounds) { swatchBounds }
                }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val swatchColor = Color(color)
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(swatchColor)
                    .border(1.dp, swatchColor.edgeOutline(alpha = 0.7f), CircleShape)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = String.format("#%08X", color),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.Rounded.Palette,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
internal fun AppIconsScaleSliderRow(
    title: String,
    value: Float,
    enabled: Boolean,
    onValueChange: (Float) -> Unit
) {
    val defaultScale = IconPackManager.DEFAULT_SHAPE_SCALE_PERCENT
    SliderSettingFloat(
        title = title,
        value = value,
        range = IconPackManager.MIN_SHAPE_SCALE_PERCENT..IconPackManager.MAX_SHAPE_SCALE_PERCENT,
        unit = "%",
        enabled = enabled,
        stepSize = 1f,
        defaultValue = defaultScale,
        defaultValueText = stringResource(R.string.app_icons_shape_scale_value_format, defaultScale),
        onValueChange = { onValueChange(normalizeShapeScale(it)) },
        onDefault = { onValueChange(defaultScale) },
        valueText = stringResource(R.string.app_icons_shape_scale_value_format, value),
        showDefaultButton = true
    )
}

@Composable
internal fun SecondaryActionPill(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    val haptics = LocalHapticFeedback.current
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = modifier
            .height(40.dp)
            .alpha(if (enabled) 1f else 0.42f)
            .clip(shape)
            .background(containerColor)
            .border(1.dp, containerColor.edgeOutline(), shape)
            .clickable(enabled = enabled) {
                haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                onClick()
            }
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
internal fun AppIconsFilterPillsRow(
    showSystemApps: Boolean,
    showHiddenApps: Boolean,
    onToggleSystem: () -> Unit,
    onToggleHidden: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ExpressiveFilterPill(
            label = stringResource(R.string.app_icons_show_system_title),
            selected = showSystemApps,
            onClick = onToggleSystem,
            modifier = Modifier.weight(1f)
        )
        ExpressiveFilterPill(
            label = stringResource(R.string.app_icons_show_hidden_title),
            selected = showHiddenApps,
            onClick = onToggleHidden,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ExpressiveFilterPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    val selFraction by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = spring(
            stiffness = Spring.StiffnessMediumLow,
            dampingRatio = Spring.DampingRatioNoBouncy
        ),
        label = "FilterPillFraction"
    )
    val corner by animateDpAsState(
        targetValue = if (selected) 24.dp else 14.dp,
        animationSpec = spring(
            stiffness = Spring.StiffnessMediumLow,
            dampingRatio = Spring.DampingRatioNoBouncy
        ),
        label = "FilterPillCorner"
    )
    val bgColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        animationSpec = spring(
            stiffness = Spring.StiffnessMediumLow,
            dampingRatio = Spring.DampingRatioNoBouncy
        ),
        label = "FilterPillBg"
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = spring(
            stiffness = Spring.StiffnessMediumLow,
            dampingRatio = Spring.DampingRatioNoBouncy
        ),
        label = "FilterPillFg"
    )
    val shape = RoundedCornerShape(corner)

    Row(
        modifier = modifier
            .height(42.dp)
            .graphicsLayer {
                val scale = 0.97f + 0.03f * selFraction
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(bgColor)
            .border(1.dp, bgColor.edgeOutline(), shape)
            .clickable {
                haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                onClick()
            }
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selFraction >= 0.5f) FontWeight.Bold else FontWeight.Medium,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
internal fun ExpandableSectionHeader(
    title: String,
    subtitle: String,
    icon: ImageVector,
    expanded: Boolean,
    onExpandChange: (Boolean) -> Unit
) {
    val bgColor = MaterialTheme.colorScheme.surface
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        border = BorderStroke(1.dp, bgColor.edgeOutline())
    ) {
        ExpandableSectionHeaderContent(
            title = title,
            subtitle = subtitle,
            icon = icon,
            expanded = expanded,
            onExpandChange = onExpandChange
        )
    }
}

@Composable
internal fun ExpandableSectionHeaderContent(
    title: String,
    subtitle: String,
    icon: ImageVector,
    expanded: Boolean,
    onExpandChange: (Boolean) -> Unit
) {
    val rotation by animateFloatAsState(targetValue = if (expanded) 180f else 0f, label = "section_rotation")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onExpandChange(!expanded) }
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.padding(10.dp))
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 14.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            imageVector = Icons.Rounded.ExpandMore,
            contentDescription = null,
            modifier = Modifier.rotate(rotation),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}