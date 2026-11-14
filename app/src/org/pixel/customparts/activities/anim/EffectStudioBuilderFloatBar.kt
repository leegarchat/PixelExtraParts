package org.pixel.customparts.activities

import android.content.ClipData
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui. draw.scale
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp as lerpFloat
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch
import org.pixel.customparts.R
import org.pixel.customparts.ui.GraphicsLayerRecordingState
import org.pixel.customparts.ui.SettingsGroupCard
import org.pixel.customparts.ui.SliderSetting
import org.pixel.customparts.ui.SliderSettingFloat
import org.pixel.customparts.ui.SliderTitleText
import org.pixel.customparts.ui.edgeOutline
import java.util.Locale
import kotlin.math.roundToInt

// ── Floating Builder Preview Bar (Bottom Frosted Component) ──────────────

@Composable
internal fun BuilderPreviewFloatBar(
    exitParams: AnimParams,
    enterParams: AnimParams,
    clockState: ConstructorPreviewClockState,
    blurState: GraphicsLayerRecordingState,
    morphBlurState: GraphicsLayerRecordingState? = null,
    morphActive: Boolean = false,
    tintBoost: Float = 0f,
    visibleFraction: Float,
    isHiddenForFlight: Boolean,
    onExpand: (Rect, () -> Rect) -> Unit,
    modifier: Modifier = Modifier,
    step2ExitParams: AnimParams = exitParams,
    step2EnterParams: AnimParams = enterParams
) {
    if (visibleFraction <= 0.005f) return
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    var barBounds by remember { mutableStateOf(Rect.Zero) }
    val barCoordsRef = remember { StudioCoordsRef() }

    val shape = RoundedCornerShape(24.dp)
    val bgColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val slideOffsetPx = with(density) { 48.dp.toPx() } * (1f - visibleFraction)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = 16.dp,
                end = 16.dp,
                bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 12.dp
            )
            .graphicsLayer {
                translationY = slideOffsetPx
                alpha = if (isHiddenForFlight) 0f else visibleFraction
            }
            .onPlaced {
                barCoordsRef.value = it
                val r = it.unclippedBoundsInRoot()
                if (r.width > 1f && r.height > 1f) barBounds = r
            }
            .onGloballyPositioned {
                barCoordsRef.value = it
                val r = it.unclippedBoundsInRoot()
                if (r.width > 1f && r.height > 1f) barBounds = r
            }
            .clip(shape)
            .border(1.dp, bgColor.edgeOutline(), shape)
            .clickable(enabled = !isHiddenForFlight && visibleFraction > 0.5f) {
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

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(92.dp)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StudioConstructorLoopPreview(
                exitParams = exitParams,
                enterParams = enterParams,
                step2ExitParams = step2ExitParams,
                step2EnterParams = step2EnterParams,
                directionMode = LoopDirectionMode.ALTERNATE,
                clockState = clockState,
                width = 46.dp,
                height = 72.dp,
                cornerRadius = 10.dp,
                labelSizeSp = 16f
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.rotation_anim_constructor_preview),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(R.string.rotation_anim_constructor_title),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f))
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.primaryContainer.edgeOutline(),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowUp,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

