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

// ── Floating Frosted Task Progress Bar (Bottom Bar <-> Flight Modal) ─────

@Composable
internal fun IconTaskFloatBar(
    progress: IconApplyProgress?,
    blurState: GraphicsLayerRecordingState,
    morphBlurState: GraphicsLayerRecordingState? = null,
    morphActive: Boolean = false,
    tintBoost: Float = 0f,
    isHiddenForFlight: Boolean,
    onBoundsChanged: (Rect, () -> Rect) -> Unit = { _, _ -> },
    onCancel: () -> Unit = {},
    onExpand: (Rect, () -> Rect) -> Unit,
    modifier: Modifier = Modifier
) {
    var cachedProgress by remember { mutableStateOf<IconApplyProgress?>(null) }
    if (progress != null) {
        cachedProgress = progress
    }
    val visibleFraction by animateFloatAsState(
        targetValue = if (progress != null || isHiddenForFlight) 1f else 0f,
        animationSpec = spring(
            stiffness = Spring.StiffnessMediumLow,
            dampingRatio = Spring.DampingRatioNoBouncy
        ),
        label = "IconTaskFloatBarVis"
    )
    val displayProgress = progress ?: cachedProgress
    if (visibleFraction <= 0.005f || displayProgress == null) return

    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    var barBounds by remember { mutableStateOf(Rect.Zero) }
    val barCoordsRef = remember { StudioCoordsRef() }

    val shape = RoundedCornerShape(24.dp)
    val bgColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val slideOffsetPx = with(density) { 42.dp.toPx() } * (1f - visibleFraction)

    val cancelChipReveal by animateFloatAsState(
        targetValue = if (!displayProgress.completed) 1f else 0f,
        animationSpec = spring(
            stiffness = Spring.StiffnessMediumLow,
            dampingRatio = Spring.DampingRatioNoBouncy
        ),
        label = "FloatBarCancelChipReveal"
    )
    val progressFraction by animateFloatAsState(
        targetValue = if (displayProgress.completed) {
            1f
        } else {
            (displayProgress.percent / 100f).coerceIn(0f, 1f)
        },
        animationSpec = spring(
            stiffness = Spring.StiffnessMediumLow,
            dampingRatio = Spring.DampingRatioNoBouncy
        ),
        label = "FloatBarProgressPct"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = 16.dp,
                end = 16.dp,
                bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 16.dp
            )
            // Capture resting bounds BEFORE graphicsLayer so entrance slide never skews morph target
            .onPlaced {
                val r = it.unclippedBoundsInRoot()
                if (r.width > 1f && r.height > 1f) {
                    barBounds = r
                    onBoundsChanged(r) { barBounds }
                }
            }
            .onGloballyPositioned {
                val r = it.unclippedBoundsInRoot()
                if (r.width > 1f && r.height > 1f) {
                    barBounds = r
                    onBoundsChanged(r) { barBounds }
                }
            }
            .graphicsLayer {
                translationY = slideOffsetPx
                alpha = if (isHiddenForFlight) 0f else visibleFraction
            }
            .clip(shape)
            .border(1.dp, bgColor.edgeOutline(), shape)
            .clickable(enabled = !isHiddenForFlight && progress != null && visibleFraction > 0.5f) {
                haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                onExpand(barBounds) { barBounds }
            }
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(bgColor.copy(alpha = 0.88f))
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer { clip = true }
                    .onPlaced { barCoordsRef.value = it }
                    .onGloballyPositioned { barCoordsRef.value = it }
                    .cardFlightBlurMask(
                        state = blurState,
                        coordsProvider = { barCoordsRef.value }
                    )
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(bgColor.copy(alpha = 0.64f))
            )
            if (morphActive && morphBlurState != null) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .graphicsLayer { clip = true }
                        .cardFlightBlurMask(
                            state = morphBlurState,
                            coordsProvider = { barCoordsRef.value }
                        )
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(bgColor.copy(alpha = 0.64f))
                )
                if (tintBoost > 0.003f) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(
                                MaterialTheme.colorScheme.scrim.copy(
                                    alpha = tintBoost.coerceIn(0f, 1f)
                                )
                            )
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(18.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (displayProgress.completed) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    } else {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    text = if (displayProgress.completed) {
                        stringResource(R.string.app_icons_apply_finished_title)
                    } else {
                        displayProgress.label
                    },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(
                        R.string.app_icons_progress_percent_format,
                        if (displayProgress.completed) 100 else displayProgress.percent.coerceIn(0, 100)
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                if (cancelChipReveal > 0.005f) {
                    Row(
                        modifier = Modifier
                            .width(30.dp * cancelChipReveal)
                            .height(24.dp)
                            .graphicsLayer {
                                alpha = cancelChipReveal
                                scaleX = cancelChipReveal
                                scaleY = cancelChipReveal
                            },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End
                    ) {
                        Spacer(Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.errorContainer)
                                .clickable(enabled = progress != null && !displayProgress.completed && cancelChipReveal > 0.5f) {
                                    haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                                    onCancel()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = stringResource(R.string.btn_cancel),
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowUp,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { progressFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(CircleShape)
            )
        }
    }
}

