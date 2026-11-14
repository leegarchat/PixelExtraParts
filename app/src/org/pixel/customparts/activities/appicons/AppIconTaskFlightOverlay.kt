package org.pixel.customparts.activities

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.ui.draw.rotate
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.util.lerp as lerpFloat
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.launch
import org.pixel.customparts.R
import org.pixel.customparts.icons.AppIconCandidate
import org.pixel.customparts.icons.AppIconShapeOverrides
import org.pixel.customparts.icons.IconApplyProgress
import org.pixel.customparts.icons.IconApplyResult
import org.pixel.customparts.icons.IconPackApplyStatus
import org.pixel.customparts.icons.IconPackEntry
import org.pixel.customparts.icons.IconPackManager
import org.pixel.customparts.icons.IconPackPreviewItem
import org.pixel.customparts.icons.IconShapeAreaConfig
import org.pixel.customparts.ui.ColorWheel
import org.pixel.customparts.ui.GenericSwitchRow
import org.pixel.customparts.ui.GraphicsLayerRecordingState
import org.pixel.customparts.ui.SliderTitleText
import org.pixel.customparts.ui.edgeOutline
import kotlin.math.roundToInt

// ── Frosted Task Progress & Clear-All Confirmation Pseudo-Modal ──────────

@Composable
internal fun IconTaskFlightOverlay(
    open: Boolean,
    sourceRect: Rect = Rect.Zero,
    sourceBoundsProvider: () -> Rect = { sourceRect },
    progress: IconApplyProgress?,
    completionResult: IconApplyResult?,
    confirmClearAll: Boolean = false,
    onClose: () -> Unit = {},
    onCancelTask: () -> Unit = {},
    onDismissCompletion: () -> Unit,
    onRebootSystem: () -> Unit,
    onConfirmClearAll: () -> Unit = {},
    onDismissClearAll: () -> Unit = {},
    scrollBlur: GraphicsLayerRecordingState,
    onProgress: (Float) -> Unit = {},
    topInsetPx: Float = 0f,
    closeSignal: Int = 0
) {
    // Keep last known progress snapshot so cancelling never kills the overlay mid-flight
    var cachedProgress by remember { mutableStateOf<IconApplyProgress?>(null) }
    var cachedResult by remember { mutableStateOf<IconApplyResult?>(null) }
    if (progress != null) cachedProgress = progress
    if (completionResult != null) cachedResult = completionResult

    val modalAnim = remember { Animatable(0f) }
    var closing by remember { mutableStateOf(false) }

    val effectiveProgress = progress ?: cachedProgress
    val effectiveResult = completionResult ?: cachedResult
    val hasPayload = confirmClearAll || effectiveProgress != null || effectiveResult != null
    val isVisible = (open || closing || modalAnim.value > 0.001f) && hasPayload
    if (!isVisible) return

    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    val hostCoordsRef = remember { StudioCoordsRef() }
    val cardCoordsRef = remember { StudioCoordsRef() }
    val flightCoordsRef = remember { StudioCoordsRef() }

    val isCompleted = !confirmClearAll && (effectiveResult != null || effectiveProgress?.completed == true)
    val isRunning = !confirmClearAll && !isCompleted

    LaunchedEffect(open) {
        if (open) {
            closing = false
            withFrameNanos { }
            haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
            modalAnim.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    stiffness = 115f,
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    visibilityThreshold = 0.002f
                )
            )
        }
    }

    fun closeAnimated(afterClose: () -> Unit) {
        if (closing) return
        closing = true
        scope.launch {
            modalAnim.stop()
            try {
                modalAnim.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(
                        stiffness = 165f,
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        visibilityThreshold = 0.005f
                    )
                )
            } finally {
                onProgress(0f)
                closing = false
                afterClose()
            }
        }
    }

    fun dismissOrMinimize() {
        when {
            confirmClearAll -> closeAnimated(onDismissClearAll)
            isRunning -> closeAnimated(onClose)
            else -> closeAnimated(onDismissCompletion)
        }
    }

    BackHandler(enabled = !closing) {
        dismissOrMinimize()
    }

    val initialCloseSignal = remember(open) { closeSignal }
    LaunchedEffect(closeSignal) {
        if (closeSignal > initialCloseSignal && !closing) {
            dismissOrMinimize()
        }
    }

    val rawT = modalAnim.value.coerceIn(0f, 1f)
    val morphT = sstepE(rawT, 0f, 1f)
    DisposableEffect(morphT) {
        onProgress(morphT)
        onDispose { }
    }

    val containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val scrimAlpha = 0.42f * sstepE(morphT, 0.35f, 0.90f)
    // Float bar is already frosted at morphT = 0f; keep frost continuous (1f) for task modes
    val blurReveal = if (confirmClearAll) sstepE(morphT, 0.35f, 0.95f) else 1f
    val cornerProgress = sstepE(morphT, 0.20f, 0.85f)
    val modalShape = RoundedCornerShape(lerp(24.dp, 26.dp, cornerProgress))
    val cardBorderColor = containerColor.edgeOutline()
    val flightProgress = sstepE(morphT, 0.0f, 0.96f)
    val bodyUnfold = sstepE(morphT, 0.16f, 0.88f)

    // Smoothly morph the 24.dp Close button in the top row:
    // At morphT = 0f (docked to running float bar): 1f (matches IconTaskFloatBar 1:1).
    // As modal expands (morphT -> 1f) or when completed: smoothly collapses to 0f.
    val baseCancelChip by animateFloatAsState(
        targetValue = if (!isCompleted) 1f else 0f,
        animationSpec = spring(
            stiffness = Spring.StiffnessMediumLow,
            dampingRatio = Spring.DampingRatioNoBouncy
        ),
        label = "ModalBaseCancelChip"
    )
    val barCancelChipReveal = (baseCancelChip * (1f - sstepE(morphT, 0.08f, 0.55f))).coerceIn(0f, 1f)

    val topInsetDp = with(density) { topInsetPx.coerceAtLeast(0f).toDp() }
    val navBottomDp = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val navBottomPx = with(density) { navBottomDp.toPx() }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(1f)
    ) {
        val maxW = maxWidth
        val fallbackWpx = with(density) { maxWidth.toPx() }
        val fallbackHpx = with(density) { maxHeight.toPx() }

        val liveSource = sourceBoundsProvider().let {
            if (it.width > 1f && it.height > 1f) it else sourceRect
        }
        val endW = (maxW - 32.dp).coerceAtLeast(0.dp)
        val startW = if (liveSource.width > 1f) {
            with(density) { liveSource.width.roundToInt().toDp() }
        } else {
            endW
        }.coerceIn(0.dp, maxW)

        val cardWidth = lerp(startW, endW, sstepE(morphT, 0f, 0.90f))

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = scrimAlpha))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        if (!closing) dismissOrMinimize()
                    }
                )
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = topInsetDp, bottom = navBottomDp)
                .onPlaced { hostCoordsRef.value = it }
                .onGloballyPositioned { hostCoordsRef.value = it },
            contentAlignment = Alignment.Center
        ) {
            val hostBounds = hostCoordsRef.value?.boundsInRoot()
            val targetHost = when {
                hostBounds != null && hostBounds.width > 1f && hostBounds.height > 1f -> hostBounds
                else -> Rect(
                    0f,
                    topInsetPx.coerceAtMost(fallbackHpx),
                    fallbackWpx,
                    (fallbackHpx - navBottomPx).coerceAtLeast(topInsetPx + 1f)
                )
            }
            val src = liveSource
            val dockW = 1f - flightProgress
            val cardWpx = with(density) { cardWidth.toPx() }.roundToInt()
            val cardHpx = flightCoordsRef.value?.takeIf { it.isAttached }?.size?.height
                ?.takeIf { it > 0 }
                ?: src.height.roundToInt().coerceAtLeast(1)
            val hostWpx = targetHost.width.roundToInt()
            val hostHpx = targetHost.height.roundToInt()
            val e0x = (hostWpx - cardWpx) / 2 + cardWpx / 2f - targetHost.width / 2f
            val e0y = (hostHpx - cardHpx) / 2 + cardHpx / 2f - targetHost.height / 2f

            val dx = if (targetHost.width > 1f && src.width > 1f) {
                (src.center.x - targetHost.center.x - e0x) * dockW
            } else 0f
            val dy = if (targetHost.height > 1f && src.height > 1f) {
                (src.center.y - targetHost.center.y - e0y) * dockW
            } else 0f

            Box(
                modifier = Modifier
                    .width(cardWidth)
                    .onPlaced { flightCoordsRef.value = it }
                    .graphicsLayer {
                        translationX = dx
                        translationY = dy
                    }
                    .clip(modalShape)
                    .border(1.dp, cardBorderColor, modalShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        enabled = !closing,
                        onClick = {}
                    )
            ) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(containerColor.copy(alpha = 0.88f))
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .graphicsLayer {
                                clip = true
                                alpha = blurReveal
                            }
                            .onPlaced { cardCoordsRef.value = it }
                            .onGloballyPositioned { cardCoordsRef.value = it }
                            .cardFlightBlurMask(
                                state = scrollBlur,
                                coordsProvider = { cardCoordsRef.value }
                            )
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(
                                containerColor.copy(
                                    alpha = lerpFloat(0.64f, 0.66f, cornerProgress)
                                )
                            )
                    )
                }

                if (confirmClearAll) {
                    // Clear-All Confirmation (flies from RebootBubble)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = lerp(16.dp, 24.dp, cornerProgress),
                                vertical = lerp(12.dp, 24.dp, cornerProgress)
                            ),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(lerp(36.dp, 54.dp, cornerProgress))
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.errorContainer)
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.errorContainer.edgeOutline(),
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(lerp(18.dp, 26.dp, cornerProgress))
                            )
                        }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .unfoldVerticalSlot(bodyUnfold)
                                .alpha(bodyUnfold),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Spacer(Modifier.height(16.dp))
                            Text(
                                text = stringResource(R.string.app_icons_clear_all_confirm_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.app_icons_clear_all_confirm_message),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(20.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                SecondaryActionPill(
                                    icon = Icons.Rounded.Close,
                                    label = stringResource(R.string.btn_cancel),
                                    enabled = !closing,
                                    onClick = { closeAnimated(onDismissClearAll) },
                                    modifier = Modifier.weight(1f)
                                )
                                SecondaryActionPill(
                                    icon = Icons.Rounded.Delete,
                                    label = stringResource(R.string.app_icons_clear_all),
                                    enabled = !closing,
                                    onClick = { onConfirmClearAll() },
                                    modifier = Modifier.weight(1.2f),
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                } else {
                    // Unified 1:1 Morph Layout for Running & Completed States
                    val p = effectiveProgress
                    val res = effectiveResult ?: p?.result ?: IconApplyResult(0, 0, 0)
                    val pct = if (isCompleted) 100 else (p?.percent ?: 0).coerceIn(0, 100)
                    val animatedPct by animateFloatAsState(
                        targetValue = pct / 100f,
                        animationSpec = spring(
                            stiffness = Spring.StiffnessMediumLow,
                            dampingRatio = Spring.DampingRatioNoBouncy
                        ),
                        label = "IconTaskUnifiedPct"
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = lerp(16.dp, 24.dp, cornerProgress),
                                vertical = lerp(12.dp, 22.dp, cornerProgress)
                            )
                    ) {
                        // 1. Header Row: at morphT = 0f matches IconTaskFloatBar 100% pixel-for-pixel
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(lerp(24.dp, 28.dp, cornerProgress))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    enabled = !closing && morphT > 0.5f,
                                    onClick = { dismissOrMinimize() }
                                ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier.size(lerp(18.dp, 24.dp, cornerProgress)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isCompleted) {
                                    Icon(
                                        imageVector = Icons.Rounded.CheckCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(lerp(18.dp, 24.dp, cornerProgress))
                                    )
                                } else {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(lerp(16.dp, 22.dp, cornerProgress)),
                                        strokeWidth = lerp(2.dp, 2.5.dp, cornerProgress)
                                    )
                                }
                            }
                            Spacer(Modifier.width(lerp(10.dp, 12.dp, cornerProgress)))
                            SliderTitleText(
                                title = if (isCompleted) {
                                    stringResource(R.string.app_icons_apply_finished_title)
                                } else {
                                    p?.label.orEmpty()
                                },
                                growProgress = sstepE(morphT, 0.12f, 0.82f),
                                maxGrow = 1.2f,
                                baseStyle = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.SemiBold
                                ),
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.app_icons_progress_percent_format, pct),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (barCancelChipReveal > 0.005f) {
                                Row(
                                    modifier = Modifier
                                        .width(30.dp * barCancelChipReveal)
                                        .height(24.dp)
                                        .graphicsLayer {
                                            alpha = barCancelChipReveal
                                            scaleX = barCancelChipReveal
                                            scaleY = barCancelChipReveal
                                        },
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    Spacer(Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.errorContainer),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Close,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onErrorContainer,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Rounded.KeyboardArrowUp,
                                contentDescription = stringResource(R.string.btn_close),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .size(18.dp)
                                    .rotate(lerpFloat(0f, 180f, cornerProgress))
                            )
                        }

                        // 2. Exact Spacing + Continuous Progress Bar
                        Spacer(Modifier.height(lerp(6.dp, 12.dp, cornerProgress)))
                        LinearProgressIndicator(
                            progress = { animatedPct },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(lerp(5.dp, 8.dp, cornerProgress))
                                .clip(CircleShape)
                        )

                        // 3. Unfolding Details & Action Buttons ( strictly 0.dp height at morphT = 0f )
                        if (bodyUnfold > 0.005f) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .unfoldVerticalSlot(bodyUnfold)
                                    .alpha(bodyUnfold),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Spacer(Modifier.height(14.dp))
                                AnimatedContent(
                                    targetState = isCompleted,
                                    transitionSpec = {
                                        fadeIn(tween(180)) togetherWith fadeOut(tween(120))
                                    },
                                    label = "IconTaskBodyState"
                                ) { completedState ->
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        if (!completedState) {
                                            if (p != null) {
                                                Text(
                                                    text = stringResource(
                                                        R.string.app_icons_progress_counts_format,
                                                        p.processed,
                                                        p.total,
                                                        p.applied,
                                                        p.removed,
                                                        p.skipped,
                                                        p.failed
                                                    ),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    textAlign = TextAlign.Center
                                                )
                                                Spacer(Modifier.height(16.dp))
                                            }
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                SecondaryActionPill(
                                                    icon = Icons.Rounded.Close,
                                                    label = stringResource(R.string.btn_close),
                                                    enabled = !closing,
                                                    onClick = { closeAnimated(onClose) },
                                                    modifier = Modifier.weight(1f)
                                                )
                                                SecondaryActionPill(
                                                    icon = Icons.Rounded.Delete,
                                                    label = stringResource(R.string.btn_cancel),
                                                    enabled = !closing,
                                                    onClick = {
                                                        onCancelTask()
                                                        closeAnimated(onClose)
                                                    },
                                                    modifier = Modifier.weight(1f),
                                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                                                )
                                            }
                                        } else {
                                            Text(
                                                text = stringResource(
                                                    R.string.app_icons_apply_finished_message_format,
                                                    res.applied,
                                                    res.removed,
                                                    res.skipped,
                                                    res.failed
                                                ),
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                textAlign = TextAlign.Center
                                            )
                                            Spacer(Modifier.height(16.dp))
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                SecondaryActionPill(
                                                    icon = Icons.Rounded.Refresh,
                                                    label = stringResource(R.string.app_icons_reboot_system),
                                                    enabled = !closing,
                                                    onClick = { closeAnimated(onRebootSystem) },
                                                    modifier = Modifier.weight(1.15f)
                                                )
                                                StudioTabStyledApplyButton(
                                                    isActive = false,
                                                    applyLabel = stringResource(R.string.btn_ok),
                                                    activeLabel = stringResource(R.string.btn_ok),
                                                    enabled = !closing,
                                                    onClick = { closeAnimated(onDismissCompletion) },
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

