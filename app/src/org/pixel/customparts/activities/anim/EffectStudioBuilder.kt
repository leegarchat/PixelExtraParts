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


@Composable
internal fun BuilderPreviewModalOverlay(
    open: Boolean,
    sourceRect: Rect,
    sourceBoundsProvider: () -> Rect,
    exitParams: AnimParams,
    enterParams: AnimParams,
    clockState: ConstructorPreviewClockState,
    compileState: AnimationCompileState?,
    onBuildTheme: (String) -> Unit,
    onCancelCompile: () -> Unit,
    onDismissCompile: () -> Unit,
    onClose: () -> Unit,
    scrollBlur: GraphicsLayerRecordingState,
    onProgress: (Float) -> Unit,
    topInsetPx: Float = 0f,
    closeSignal: Int = 0,
    isDualPreview: Boolean = false,
    step2ExitParams: AnimParams = exitParams,
    step2EnterParams: AnimParams = enterParams,
    firstPreviewLabel: String? = null,
    secondPreviewLabel: String? = null,
    xmlNamedParams: List<Pair<String, AnimParams>>? = null
) {
    if (!open && compileState == null) return
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val clipboard = LocalClipboard.current
    val modalScrollState = rememberScrollState()
    val secondaryClockState = rememberConstructorPreviewClockState()

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val flightAnim = remember { Animatable(0f) }
    var closing by remember { mutableStateOf(false) }
    var closingFromCompile by remember { mutableStateOf(false) }
    var showXml by remember { mutableStateOf(false) }
    val cardCoordsRef = remember { StudioCoordsRef() }
    val flightCoordsRef = remember { StudioCoordsRef() }

    val canDismiss = compileState == null ||
        compileState.phase == AnimationCompilePhase.SUCCESS ||
        compileState.phase == AnimationCompilePhase.ERROR ||
        compileState.phase == AnimationCompilePhase.CANCELLED

    LaunchedEffect(open, compileState != null) {
        if (open || compileState != null) {
            closing = false
            closingFromCompile = false
            withFrameNanos { }
            haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
            flightAnim.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    stiffness = 100f,
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    visibilityThreshold = 0.002f
                )
            )
        }
    }

    fun closeAnimated(fromCompile: Boolean = compileState != null, afterClose: (() -> Unit)? = null) {
        if (closing) return
        closing = true
        closingFromCompile = fromCompile
        showXml = false
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
        scope.launch {
            flightAnim.stop()
            try {
                flightAnim.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(
                        stiffness = 150f,
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        visibilityThreshold = 0.05f
                    )
                )
            } finally {
                onProgress(0f)
                closingFromCompile = false
                afterClose?.invoke()
                onClose()
            }
        }
    }

    BackHandler(enabled = !closing) {
        if (compileState != null) {
            if (canDismiss) {
                closeAnimated(fromCompile = true) { onDismissCompile() }
            } else {
                onCancelCompile()
            }
        } else {
            closeAnimated(fromCompile = false)
        }
    }

    val initialCloseSignal = remember(open, compileState != null) { closeSignal }
    LaunchedEffect(closeSignal) {
        if (closeSignal > initialCloseSignal && !closing) {
            if (compileState != null) {
                if (canDismiss) closeAnimated(fromCompile = true) { onDismissCompile() }
            } else {
                closeAnimated(fromCompile = false)
            }
        }
    }

    val rawT = flightAnim.value.coerceIn(0f, 1f)
    val morphT = sstepE(rawT, 0f, 1f)
    DisposableEffect(morphT) {
        onProgress(morphT)
        onDispose { }
    }

    val containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val scrimAlpha = 0.42f * sstepE(morphT, 0.45f, 0.92f)
    val cardBgAlpha = sstepE(morphT, 0.0f, 0.35f)
    val blurReveal = sstepE(morphT, 0.55f, 0.97f)
    val cornerProgress = sstepE(morphT, 0.40f, 0.88f)
    val modalCorner = lerp(24.dp, 26.dp, cornerProgress)
    val modalShape = RoundedCornerShape(modalCorner)
    val cardBorderColor = containerColor.edgeOutline()

    val isCompileExit = closingFromCompile || compileState != null
    val flightProgress = if (isCompileExit) 1f else sstepE(morphT, 0.0f, 0.96f)
    val layoutMorph = if (isCompileExit) 1f else sstepE(morphT, 0.04f, 0.86f)
    val contentReveal = sstepE(morphT, 0.22f, 0.88f)

    val topInsetDp = with(density) { topInsetPx.coerceAtLeast(0f).toDp() }
    val navBottomDp = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val imeBottomDp = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
    val effectiveBottomDp = maxOf(navBottomDp, imeBottomDp)
    val effectiveBottomPx = with(density) { effectiveBottomDp.toPx() }

    LaunchedEffect(imeBottomDp > 40.dp, modalScrollState.maxValue) {
        if (imeBottomDp > 40.dp && modalScrollState.maxValue > 0) {
            modalScrollState.animateScrollTo(modalScrollState.maxValue)
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(1f)
    ) {
        val maxW = maxWidth
        val maxH = maxHeight
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
        }.coerceIn(0.dp, maxWidth)

        val cardWidth: Dp = if (isCompileExit) {
            endW
        } else {
            lerp(startW, endW, sstepE(morphT, 0f, 0.90f))
        }
        val visibleMaxCardH = (maxH - topInsetDp - effectiveBottomDp - 24.dp).coerceAtLeast(180.dp)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = scrimAlpha))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        if (!closing && canDismiss) {
                            if (compileState != null) {
                                closeAnimated(fromCompile = true) { onDismissCompile() }
                            } else {
                                closeAnimated(fromCompile = false)
                            }
                        }
                    }
                )
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = topInsetDp, bottom = effectiveBottomDp),
            contentAlignment = Alignment.Center
        ) {
            val targetHost = Rect(
                0f,
                topInsetPx.coerceAtMost(fallbackHpx),
                fallbackWpx,
                (fallbackHpx - effectiveBottomPx).coerceAtLeast(topInsetPx + 1f)
            )
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
                    .heightIn(max = visibleMaxCardH)
                    .then(
                        if (closingFromCompile) {
                            Modifier
                                .unfoldVerticalSlot(morphT)
                                .alpha(sstepE(morphT, 0.04f, 0.75f))
                        } else {
                            Modifier
                        }
                    )
                    .onPlaced { flightCoordsRef.value = it }
                    .graphicsLayer {
                        translationX = dx
                        translationY = dy
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
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
                            .background(containerColor.copy(alpha = cardBgAlpha.coerceAtLeast(0.88f)))
                    )
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .graphicsLayer {
                                    clip = true
                                    alpha = blurReveal.coerceAtLeast(0.85f)
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
                                        alpha = lerpFloat(0.64f, 0.66f, sstepE(morphT, 0.45f, 0.92f))
                                    )
                                )
                        )
                    }

                    AnimatedContent(
                        targetState = compileState,
                        transitionSpec = {
                            (fadeIn(tween(220)) + scaleIn(initialScale = 0.94f, animationSpec = tween(220))) togetherWith
                                fadeOut(tween(150))
                        },
                        label = "BuilderModalMode"
                    ) { cState ->
                        if (cState != null) {
                            val terminal = cState.phase == AnimationCompilePhase.SUCCESS ||
                                cState.phase == AnimationCompilePhase.ERROR ||
                                cState.phase == AnimationCompilePhase.CANCELLED
                            val animatedProgress by animateFloatAsState(
                                targetValue = cState.progress.coerceIn(0f, 1f),
                                animationSpec = tween(250, easing = FastOutSlowInEasing),
                                label = "compile_morph_progress"
                            )

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .verticalScroll(rememberScrollState())
                                    .padding(horizontal = 24.dp, vertical = 24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                AnimatedContent(
                                    targetState = terminal,
                                    transitionSpec = {
                                        (fadeIn(tween(220)) + scaleIn(animationSpec = tween(220))) togetherWith
                                            fadeOut(tween(140))
                                    },
                                    label = "compile_status_icon"
                                ) { isTerm ->
                                    if (isTerm) {
                                        when (cState.phase) {
                                            AnimationCompilePhase.SUCCESS -> Icon(
                                                Icons.Rounded.CheckCircle,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(56.dp)
                                            )
                                            AnimationCompilePhase.ERROR -> Icon(
                                                Icons.Rounded.Error,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(56.dp)
                                            )
                                            else -> Icon(
                                                Icons.Rounded.Cancel,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(56.dp)
                                            )
                                        }
                                    } else {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Icon(
                                                Icons.Rounded.Build,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier
                                                    .size(54.dp)
                                                    .scale(1f + animatedProgress * 0.06f)
                                            )
                                            Spacer(Modifier.height(16.dp))
                                            LinearProgressIndicator(
                                                progress = { animatedProgress },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(8.dp)
                                                    .clip(CircleShape)
                                            )
                                        }
                                    }
                                }

                                Spacer(Modifier.height(16.dp))
                                Text(
                                    text = when (cState.phase) {
                                        AnimationCompilePhase.COMPILING -> stringResource(R.string.anim_compile_dialog_compiling)
                                        AnimationCompilePhase.INSTALLING -> stringResource(R.string.anim_compile_dialog_installing)
                                        AnimationCompilePhase.SUCCESS -> stringResource(R.string.anim_compile_dialog_success)
                                        AnimationCompilePhase.ERROR -> stringResource(R.string.anim_compile_dialog_error)
                                        AnimationCompilePhase.CANCELLED -> stringResource(R.string.anim_compile_dialog_cancelled)
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )

                                if (!terminal) {
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        text = stringResource(
                                            R.string.anim_compile_dialog_progress,
                                            (animatedProgress * 100).toInt()
                                        ),
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

                                if (cState.detail.isNotBlank()) {
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        text = cState.detail,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Spacer(Modifier.height(20.dp))

                                val actionBg = if (terminal) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.errorContainer
                                }
                                val actionContent = if (terminal) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onErrorContainer
                                }
                                val btnShape = RoundedCornerShape(28.dp)

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(46.dp)
                                        .clip(btnShape)
                                        .background(actionBg)
                                        .border(1.dp, actionBg.edgeOutline(), btnShape)
                                        .clickable {
                                            haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                                            if (terminal) {
                                                closeAnimated { onDismissCompile() }
                                            } else {
                                                onCancelCompile()
                                            }
                                        },
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (terminal) {
                                            stringResource(R.string.anim_compile_dialog_ok)
                                        } else {
                                            stringResource(R.string.anim_compile_dialog_cancel)
                                        },
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = actionContent
                                    )
                                }
                            }
                        } else {
                            // ── 1:1 Geometric Morph from FloatBar to Modal ──
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .verticalScroll(modalScrollState, enabled = morphT > 0.7f)
                            ) {
                                val stageH = lerp(92.dp, 336.dp, layoutMorph)
                                val titleX = lerp(78.dp, 20.dp, layoutMorph)
                                val titleMaxW = (cardWidth - titleX - 66.dp).coerceAtLeast(60.dp)
                                val subtitleAlpha = 1f - sstepE(layoutMorph, 0f, 0.35f)

                                val arrowX = (cardWidth - lerp(16.dp, 20.dp, layoutMorph) - 38.dp)
                                    .coerceAtLeast(0.dp)
                                val arrowY = lerp(27.dp, 16.dp, layoutMorph)
                                val arrowRotation = lerpFloat(0f, 180f, layoutMorph)

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(stageH)
                                ) {
                                    if (!isDualPreview) {
                                        val previewW = lerp(46.dp, 156.dp, layoutMorph)
                                        val previewH = lerp(72.dp, 256.dp, layoutMorph)
                                        val previewCorner = lerp(10.dp, 14.dp, layoutMorph)
                                        val previewLabelSp = lerpFloat(16f, 28f, layoutMorph)
                                        val previewX = lerp(
                                            16.dp,
                                            ((cardWidth - previewW) / 2f).coerceAtLeast(16.dp),
                                            layoutMorph
                                        )
                                        val previewY = lerp(10.dp, 66.dp, layoutMorph)

                                        Box(
                                            modifier = Modifier.offset {
                                                IntOffset(
                                                    x = with(density) { previewX.roundToPx() },
                                                    y = with(density) { previewY.roundToPx() }
                                                )
                                            }
                                        ) {
                                            StudioConstructorLoopPreview(
                                                exitParams = exitParams,
                                                enterParams = enterParams,
                                                clockState = clockState,
                                                width = previewW,
                                                height = previewH,
                                                cornerRadius = previewCorner,
                                                labelSizeSp = previewLabelSp
                                            )
                                        }
                                    } else {
                                        val targetSingleW = ((cardWidth - 52.dp) / 2f).coerceIn(108.dp, 138.dp)
                                        val targetSingleH = 226.dp
                                        val previewW = lerp(46.dp, targetSingleW, layoutMorph)
                                        val previewH = lerp(72.dp, targetSingleH, layoutMorph)
                                        val previewCorner = lerp(10.dp, 14.dp, layoutMorph)
                                        val previewLabelSp = lerpFloat(16f, 26f, layoutMorph)

                                        val gapDp = 16.dp
                                        val pairTotalW = targetSingleW * 2 + gapDp
                                        val pairStartX = ((cardWidth - pairTotalW) / 2f).coerceAtLeast(16.dp)

                                        val card1X = lerp(16.dp, pairStartX, layoutMorph)
                                        val card1Y = lerp(10.dp, 86.dp, layoutMorph)

                                        val splitT = sstepE(layoutMorph, 0.14f, 1f)
                                        val card2X = lerp(card1X, pairStartX + targetSingleW + gapDp, splitT)
                                        val card2Alpha = sstepE(splitT, 0.04f, 0.45f)
                                        val labelAlpha = sstepE(splitT, 0.40f, 0.92f)

                                        // 2nd Card (Close / Выход) slides out to the right from Card 1
                                        if (splitT > 0.01f) {
                                            if (secondPreviewLabel != null) {
                                                Box(
                                                    modifier = Modifier
                                                        .offset {
                                                            IntOffset(
                                                                x = with(density) { card2X.roundToPx() },
                                                                y = with(density) { (card1Y - 22.dp).roundToPx() }
                                                            )
                                                        }
                                                        .width(previewW)
                                                        .alpha(labelAlpha),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = secondPreviewLabel,
                                                        style = MaterialTheme.typography.labelMedium,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        maxLines = 1
                                                    )
                                                }
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .offset {
                                                        IntOffset(
                                                            x = with(density) { card2X.roundToPx() },
                                                            y = with(density) { card1Y.roundToPx() }
                                                        )
                                                    }
                                                    .alpha(card2Alpha)
                                            ) {
                                                StudioConstructorLoopPreview(
                                                    exitParams = exitParams,
                                                    enterParams = enterParams,
                                                    step2ExitParams = step2ExitParams,
                                                    step2EnterParams = step2EnterParams,
                                                    directionMode = LoopDirectionMode.STEP2_ONLY,
                                                    clockState = secondaryClockState,
                                                    width = previewW,
                                                    height = previewH,
                                                    cornerRadius = previewCorner,
                                                    labelSizeSp = previewLabelSp
                                                )
                                            }
                                        }

                                        // 1st Card (Open / Вход)
                                        if (firstPreviewLabel != null && splitT > 0.01f) {
                                            Box(
                                                modifier = Modifier
                                                    .offset {
                                                        IntOffset(
                                                            x = with(density) { card1X.roundToPx() },
                                                            y = with(density) { (card1Y - 22.dp).roundToPx() }
                                                        )
                                                    }
                                                    .width(previewW)
                                                    .alpha(labelAlpha),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = firstPreviewLabel,
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                        Box(
                                            modifier = Modifier.offset {
                                                IntOffset(
                                                    x = with(density) { card1X.roundToPx() },
                                                    y = with(density) { card1Y.roundToPx() }
                                                )
                                            }
                                        ) {
                                            StudioConstructorLoopPreview(
                                                exitParams = exitParams,
                                                enterParams = enterParams,
                                                step2ExitParams = step2ExitParams,
                                                step2EnterParams = step2EnterParams,
                                                directionMode = if (splitT > 0.1f) {
                                                    LoopDirectionMode.STEP1_ONLY
                                                } else {
                                                    LoopDirectionMode.ALTERNATE
                                                },
                                                clockState = clockState,
                                                width = previewW,
                                                height = previewH,
                                                cornerRadius = previewCorner,
                                                labelSizeSp = previewLabelSp
                                            )
                                        }
                                    }

                                    var fullTitleBlockHeightPx by remember { mutableIntStateOf(0) }
                                    Column(
                                        modifier = Modifier
                                            .layout { measurable, constraints ->
                                                val p = measurable.measure(constraints)
                                                if (subtitleAlpha > 0.95f || fullTitleBlockHeightPx == 0) {
                                                    fullTitleBlockHeightPx = p.height
                                                }
                                                val baseH = if (fullTitleBlockHeightPx > 0) fullTitleBlockHeightPx else p.height
                                                val startY = ((92.dp.roundToPx() - baseH) / 2).coerceAtLeast(0)
                                                val endY = 18.dp.roundToPx()
                                                val yPx = lerpFloat(startY.toFloat(), endY.toFloat(), layoutMorph).roundToInt()
                                                val xPx = titleX.roundToPx()
                                                layout(p.width, p.height) {
                                                    p.placeRelative(xPx, yPx)
                                                }
                                            }
                                            .width(titleMaxW)
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null,
                                                enabled = !closing && morphT > 0.5f,
                                                onClick = { closeAnimated(fromCompile = false) }
                                            )
                                    ) {
                                        SliderTitleText(
                                            title = stringResource(R.string.rotation_anim_constructor_preview),
                                            growProgress = sstepE(morphT, 0.08f, 0.78f),
                                            maxGrow = 1.65f,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        if (subtitleAlpha > 0.01f) {
                                            Text(
                                                text = stringResource(R.string.rotation_anim_constructor_title),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.alpha(subtitleAlpha)
                                            )
                                        }
                                    }

                                    Box(
                                        modifier = Modifier
                                            .offset {
                                                IntOffset(
                                                    x = with(density) { arrowX.roundToPx() },
                                                    y = with(density) { arrowY.roundToPx() }
                                                )
                                            }
                                            .size(38.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f))
                                            .border(
                                                1.dp,
                                                MaterialTheme.colorScheme.primaryContainer.edgeOutline(),
                                                CircleShape
                                            )
                                            .clickable(enabled = !closing) {
                                                haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                                                closeAnimated()
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.KeyboardArrowUp,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier
                                                .size(22.dp)
                                                .rotate(arrowRotation)
                                        )
                                    }
                                }

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .unfoldVerticalSlot(contentReveal)
                                        .alpha(contentReveal)
                                        .padding(start = 20.dp, end = 20.dp, bottom = 18.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    MorphingNameCompileBar(
                                        buttonLabel = stringResource(R.string.rotation_anim_constructor_build),
                                        placeholderLabel = stringResource(R.string.rotation_anim_constructor_style_name),
                                        enabled = !closing && morphT > 0.6f,
                                        onCompile = onBuildTheme
                                    )

                                    val xmlCorner by animateFloatAsState(
                                        targetValue = if (showXml) 28f else 12f,
                                        animationSpec = spring(
                                            stiffness = Spring.StiffnessMedium,
                                            dampingRatio = Spring.DampingRatioNoBouncy
                                        ),
                                        label = "XmlBtnCorner"
                                    )
                                    val xmlBtnBg = if (showXml) {
                                        MaterialTheme.colorScheme.secondaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerHighest
                                    }
                                    val xmlBtnContent = if (showXml) {
                                        MaterialTheme.colorScheme.onSecondaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                    val xmlBtnShape = RoundedCornerShape(xmlCorner.dp)

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(46.dp)
                                            .clip(xmlBtnShape)
                                            .background(xmlBtnBg)
                                            .border(1.dp, xmlBtnBg.edgeOutline(), xmlBtnShape)
                                            .clickable(enabled = !closing && morphT > 0.6f) {
                                                haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                                                showXml = !showXml
                                            }
                                            .padding(horizontal = 14.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Code,
                                            contentDescription = null,
                                            tint = xmlBtnContent,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = if (showXml) {
                                                stringResource(R.string.rotation_anim_constructor_hide_xml)
                                            } else {
                                                stringResource(R.string.rotation_anim_constructor_show_xml)
                                            },
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = xmlBtnContent
                                        )
                                    }

                                    val xmlItems = xmlNamedParams ?: listOf(
                                        "custom_rotate_exit.xml" to exitParams,
                                        "custom_rotate_enter.xml" to enterParams
                                    )

                                    AnimatedVisibility(
                                        visible = showXml,
                                        enter = expandVertically() + fadeIn(),
                                        exit = shrinkVertically() + fadeOut()
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 6.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            xmlItems.forEach { (name, p) ->
                                                val xmlText = remember(p) { animParamsToXml(p) }
                                                val codeBg = MaterialTheme.colorScheme.surfaceContainerHighest
                                                Card(
                                                    shape = RoundedCornerShape(16.dp),
                                                    colors = CardDefaults.cardColors(containerColor = codeBg),
                                                    border = BorderStroke(1.dp, codeBg.edgeOutline()),
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Column(modifier = Modifier.padding(12.dp)) {
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.SpaceBetween
                                                        ) {
                                                            Text(
                                                                text = name,
                                                                style = MaterialTheme.typography.labelMedium,
                                                                fontWeight = FontWeight.Bold,
                                                                color = MaterialTheme.colorScheme.primary,
                                                                fontFamily = FontFamily.Monospace
                                                            )
                                                            IconButton(
                                                                onClick = {
                                                                    haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                                                                    scope.launch {
                                                                        clipboard.setClipEntry(
                                                                            ClipEntry(ClipData.newPlainText("xml", xmlText))
                                                                        )
                                                                    }
                                                                },
                                                                modifier = Modifier.size(28.dp)
                                                            ) {
                                                                Icon(
                                                                    imageVector = Icons.Rounded.ContentCopy,
                                                                    contentDescription = stringResource(R.string.anim_cd_copy),
                                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                                    modifier = Modifier.size(16.dp)
                                                                )
                                                            }
                                                        }
                                                        Spacer(Modifier.height(4.dp))
                                                        Text(
                                                            text = xmlText,
                                                            style = MaterialTheme.typography.bodySmall.copy(
                                                                fontFamily = FontFamily.Monospace,
                                                                fontSize = 10.sp,
                                                                lineHeight = 14.sp
                                                            ),
                                                            color = MaterialTheme.colorScheme.onSurface
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
    }
}

@Composable
internal fun StudioConstructorCard(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    exitParams: AnimParams,
    enterParams: AnimParams,
    onExitChange: (AnimParams) -> Unit,
    onEnterChange: (AnimParams) -> Unit,
    isInterpolatorMorphOpen: Boolean,
    onOpenInterpolatorMorph: (InterpolatorMorphRequest) -> Unit
) {
    val animatedTabPos by animateFloatAsState(
        targetValue = selectedTab.toFloat(),
        animationSpec = spring(
            stiffness = Spring.StiffnessMediumLow,
            dampingRatio = Spring.DampingRatioNoBouncy
        ),
        label = "ConstructorTabPos"
    )

    SettingsGroupCard(title = stringResource(R.string.rotation_anim_constructor_title)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 8.dp)
        ) {
            StudioTabs(
                titles = listOf(
                    stringResource(R.string.rotation_anim_constructor_exit),
                    stringResource(R.string.rotation_anim_constructor_enter)
                ),
                position = animatedTabPos,
                onTabClick = onTabSelected
            )

            Spacer(Modifier.height(12.dp))

            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    val dir = if (targetState > initialState) 1 else -1
                    (slideInHorizontally(
                        animationSpec = spring(
                            stiffness = Spring.StiffnessMediumLow,
                            dampingRatio = Spring.DampingRatioNoBouncy
                        )
                    ) { fullWidth -> dir * fullWidth } + fadeIn(tween(180))) togetherWith
                        (slideOutHorizontally(
                            animationSpec = spring(
                                stiffness = Spring.StiffnessMediumLow,
                                dampingRatio = Spring.DampingRatioNoBouncy
                            )
                        ) { fullWidth -> -dir * fullWidth } + fadeOut(tween(140)))
                },
                label = "ConstructorSlidersSlide"
            ) { tab ->
                val currentParams = if (tab == 0) exitParams else enterParams
                val onParamsChange: (AnimParams) -> Unit = if (tab == 0) onExitChange else onEnterChange

                StudioConstructorParamSliders(
                    currentParams = currentParams,
                    onParamsChange = onParamsChange,
                    isInterpolatorMorphOpen = isInterpolatorMorphOpen,
                    onOpenInterpolatorMorph = onOpenInterpolatorMorph
                )
            }
        }
    }
}

@Composable
internal fun StudioTransitionConstructorCard(
    directionTab: Int,
    onDirectionTabSelected: (Int) -> Unit,
    phaseTab: Int,
    onPhaseTabSelected: (Int) -> Unit,
    openEnterParams: AnimParams,
    openExitParams: AnimParams,
    closeEnterParams: AnimParams,
    closeExitParams: AnimParams,
    onOpenEnterChange: (AnimParams) -> Unit,
    onOpenExitChange: (AnimParams) -> Unit,
    onCloseEnterChange: (AnimParams) -> Unit,
    onCloseExitChange: (AnimParams) -> Unit,
    isInterpolatorMorphOpen: Boolean,
    onOpenInterpolatorMorph: (InterpolatorMorphRequest) -> Unit,
    directionTitles: List<String> = listOf(stringResource(R.string.anim_direction_enter), stringResource(R.string.anim_direction_close)),
    phaseTitles: List<String> = listOf(stringResource(R.string.anim_phase_start), stringResource(R.string.anim_phase_end))
) {
    val animatedDirPos by animateFloatAsState(
        targetValue = directionTab.toFloat(),
        animationSpec = spring(
            stiffness = Spring.StiffnessMediumLow,
            dampingRatio = Spring.DampingRatioNoBouncy
        ),
        label = "TransitionDirTabPos"
    )
    val animatedPhasePos by animateFloatAsState(
        targetValue = phaseTab.toFloat(),
        animationSpec = spring(
            stiffness = Spring.StiffnessMediumLow,
            dampingRatio = Spring.DampingRatioNoBouncy
        ),
        label = "TransitionPhaseTabPos"
    )

    val combinedSlot = directionTab.coerceIn(0, 1) * 2 + phaseTab.coerceIn(0, 1)

    SettingsGroupCard(title = stringResource(R.string.rotation_anim_constructor_title)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 8.dp)
        ) {
            // Row 1: Enter | Close
            StudioTabs(
                titles = directionTitles,
                position = animatedDirPos,
                onTabClick = onDirectionTabSelected
            )

            Spacer(Modifier.height(8.dp))

            // Row 2: Start | End
            StudioTabs(
                titles = phaseTitles,
                position = animatedPhasePos,
                onTabClick = onPhaseTabSelected
            )

            Spacer(Modifier.height(12.dp))

            AnimatedContent(
                targetState = combinedSlot,
                transitionSpec = {
                    val dir = if (targetState > initialState) 1 else -1
                    (slideInHorizontally(
                        animationSpec = spring(
                            stiffness = Spring.StiffnessMediumLow,
                            dampingRatio = Spring.DampingRatioNoBouncy
                        )
                    ) { fullWidth -> dir * fullWidth } + fadeIn(tween(180))) togetherWith
                        (slideOutHorizontally(
                            animationSpec = spring(
                                stiffness = Spring.StiffnessMediumLow,
                                dampingRatio = Spring.DampingRatioNoBouncy
                            )
                        ) { fullWidth -> -dir * fullWidth } + fadeOut(tween(140)))
                },
                label = "TransitionConstructorSlidersSlide"
            ) { slot ->
                val currentParams = when (slot) {
                    0 -> openEnterParams
                    1 -> openExitParams
                    2 -> closeEnterParams
                    else -> closeExitParams
                }
                val onParamsChange: (AnimParams) -> Unit = when (slot) {
                    0 -> onOpenEnterChange
                    1 -> onOpenExitChange
                    2 -> onCloseEnterChange
                    else -> onCloseExitChange
                }

                StudioConstructorParamSliders(
                    currentParams = currentParams,
                    onParamsChange = onParamsChange,
                    isInterpolatorMorphOpen = isInterpolatorMorphOpen,
                    onOpenInterpolatorMorph = onOpenInterpolatorMorph
                )
            }
        }
    }
}

@Composable
internal fun StudioConstructorParamSliders(
    currentParams: AnimParams,
    onParamsChange: (AnimParams) -> Unit,
    isInterpolatorMorphOpen: Boolean,
    onOpenInterpolatorMorph: (InterpolatorMorphRequest) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        val haptics = LocalHapticFeedback.current
        var interpBounds by remember { mutableStateOf(Rect.Zero) }
        val interpBg = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.68f)
        val interpContent = MaterialTheme.colorScheme.onPrimaryContainer
        val interpShape = RoundedCornerShape(28.dp)
        val interpName = INTERPOLATOR_NAMES.getOrElse(currentParams.interpolatorIndex) { "Linear" }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .height(46.dp)
                .onPlaced {
                    val r = it.unclippedBoundsInRoot()
                    if (r.width > 1f && r.height > 1f) interpBounds = r
                }
                .onGloballyPositioned {
                    val r = it.unclippedBoundsInRoot()
                    if (r.width > 1f && r.height > 1f) interpBounds = r
                }
                .alpha(if (isInterpolatorMorphOpen) 0f else 1f)
                .clip(interpShape)
                .background(interpBg)
                .border(1.dp, interpBg.edgeOutline(), interpShape)
                .clickable(enabled = !isInterpolatorMorphOpen) {
                    haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                    onOpenInterpolatorMorph(
                        InterpolatorMorphRequest(
                            selectedIndex = currentParams.interpolatorIndex,
                            sourceRect = interpBounds,
                            sourceBoundsProvider = { interpBounds },
                            onSelect = { newIdx ->
                                onParamsChange(currentParams.copy(interpolatorIndex = newIdx))
                            }
                        )
                    )
                }
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(R.string.anim_param_interpolator),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                color = interpContent.copy(alpha = 0.85f),
                maxLines = 1
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = interpName,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = interpContent,
                    maxLines = 1
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = null,
                    tint = interpContent,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 20.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )

        StudioConstructorSectionHeader(stringResource(R.string.anim_param_translate))
        SliderSettingFloat(
            title = "fromX",
            value = currentParams.translateFromX,
            range = -100f..100f,
            unit = "%",
            enabled = true,
            stepSize = 1f,
            defaultValue = 0f,
            defaultValueText = "0%",
            valueText = String.format(Locale.US, "%.0f%%", currentParams.translateFromX),
            onValueChange = { onParamsChange(currentParams.copy(translateFromX = it)) },
            onDefault = { onParamsChange(currentParams.copy(translateFromX = 0f)) }
        )
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 20.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
        )
        SliderSettingFloat(
            title = "toX",
            value = currentParams.translateToX,
            range = -100f..100f,
            unit = "%",
            enabled = true,
            stepSize = 1f,
            defaultValue = 0f,
            defaultValueText = "0%",
            valueText = String.format(Locale.US, "%.0f%%", currentParams.translateToX),
            onValueChange = { onParamsChange(currentParams.copy(translateToX = it)) },
            onDefault = { onParamsChange(currentParams.copy(translateToX = 0f)) }
        )
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 20.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
        )
        SliderSettingFloat(
            title = "fromY",
            value = currentParams.translateFromY,
            range = -100f..100f,
            unit = "%",
            enabled = true,
            stepSize = 1f,
            defaultValue = 0f,
            defaultValueText = "0%",
            valueText = String.format(Locale.US, "%.0f%%", currentParams.translateFromY),
            onValueChange = { onParamsChange(currentParams.copy(translateFromY = it)) },
            onDefault = { onParamsChange(currentParams.copy(translateFromY = 0f)) }
        )
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 20.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
        )
        SliderSettingFloat(
            title = "toY",
            value = currentParams.translateToY,
            range = -100f..100f,
            unit = "%",
            enabled = true,
            stepSize = 1f,
            defaultValue = 0f,
            defaultValueText = "0%",
            valueText = String.format(Locale.US, "%.0f%%", currentParams.translateToY),
            onValueChange = { onParamsChange(currentParams.copy(translateToY = it)) },
            onDefault = { onParamsChange(currentParams.copy(translateToY = 0f)) }
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        StudioConstructorSectionHeader(stringResource(R.string.anim_param_scale))
        SliderSettingFloat(
            title = "fromX",
            value = currentParams.scaleFromX,
            range = 0f..3f,
            unit = "x",
            enabled = true,
            stepSize = 0.05f,
            defaultValue = 1f,
            defaultValueText = "1.00x",
            valueText = String.format(Locale.US, "%.2fx", currentParams.scaleFromX),
            onValueChange = { onParamsChange(currentParams.copy(scaleFromX = it)) },
            onDefault = { onParamsChange(currentParams.copy(scaleFromX = 1f)) }
        )
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 20.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
        )
        SliderSettingFloat(
            title = "toX",
            value = currentParams.scaleToX,
            range = 0f..3f,
            unit = "x",
            enabled = true,
            stepSize = 0.05f,
            defaultValue = 1f,
            defaultValueText = "1.00x",
            valueText = String.format(Locale.US, "%.2fx", currentParams.scaleToX),
            onValueChange = { onParamsChange(currentParams.copy(scaleToX = it)) },
            onDefault = { onParamsChange(currentParams.copy(scaleToX = 1f)) }
        )
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 20.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
        )
        SliderSettingFloat(
            title = "fromY",
            value = currentParams.scaleFromY,
            range = 0f..3f,
            unit = "x",
            enabled = true,
            stepSize = 0.05f,
            defaultValue = 1f,
            defaultValueText = "1.00x",
            valueText = String.format(Locale.US, "%.2fx", currentParams.scaleFromY),
            onValueChange = { onParamsChange(currentParams.copy(scaleFromY = it)) },
            onDefault = { onParamsChange(currentParams.copy(scaleFromY = 1f)) }
        )
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 20.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
        )
        SliderSettingFloat(
            title = "toY",
            value = currentParams.scaleToY,
            range = 0f..3f,
            unit = "x",
            enabled = true,
            stepSize = 0.05f,
            defaultValue = 1f,
            defaultValueText = "1.00x",
            valueText = String.format(Locale.US, "%.2fx", currentParams.scaleToY),
            onValueChange = { onParamsChange(currentParams.copy(scaleToY = it)) },
            onDefault = { onParamsChange(currentParams.copy(scaleToY = 1f)) }
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        StudioConstructorSectionHeader(stringResource(R.string.anim_param_alpha))
        SliderSettingFloat(
            title = "from",
            value = currentParams.alphaFrom,
            range = 0f..1f,
            unit = "",
            enabled = true,
            stepSize = 0.05f,
            defaultValue = 1f,
            defaultValueText = "1.00",
            valueText = String.format(Locale.US, "%.2f", currentParams.alphaFrom),
            onValueChange = { onParamsChange(currentParams.copy(alphaFrom = it)) },
            onDefault = { onParamsChange(currentParams.copy(alphaFrom = 1f)) }
        )
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 20.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
        )
        SliderSettingFloat(
            title = "to",
            value = currentParams.alphaTo,
            range = 0f..1f,
            unit = "",
            enabled = true,
            stepSize = 0.05f,
            defaultValue = 1f,
            defaultValueText = "1.00",
            valueText = String.format(Locale.US, "%.2f", currentParams.alphaTo),
            onValueChange = { onParamsChange(currentParams.copy(alphaTo = it)) },
            onDefault = { onParamsChange(currentParams.copy(alphaTo = 1f)) }
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        StudioConstructorSectionHeader(stringResource(R.string.anim_param_rotate))
        SliderSettingFloat(
            title = "from",
            value = currentParams.rotateFrom,
            range = -360f..360f,
            unit = "°",
            enabled = true,
            stepSize = 1f,
            defaultValue = 0f,
            defaultValueText = "0°",
            valueText = String.format(Locale.US, "%.0f°", currentParams.rotateFrom),
            onValueChange = { onParamsChange(currentParams.copy(rotateFrom = it)) },
            onDefault = { onParamsChange(currentParams.copy(rotateFrom = 0f)) }
        )
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 20.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
        )
        SliderSettingFloat(
            title = "to",
            value = currentParams.rotateTo,
            range = -360f..360f,
            unit = "°",
            enabled = true,
            stepSize = 1f,
            defaultValue = 0f,
            defaultValueText = "0°",
            valueText = String.format(Locale.US, "%.0f°", currentParams.rotateTo),
            onValueChange = { onParamsChange(currentParams.copy(rotateTo = it)) },
            onDefault = { onParamsChange(currentParams.copy(rotateTo = 0f)) }
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        StudioConstructorSectionHeader(stringResource(R.string.anim_param_pivot))
        SliderSettingFloat(
            title = "pivotX",
            value = currentParams.pivotX,
            range = 0f..100f,
            unit = "%",
            enabled = true,
            stepSize = 1f,
            defaultValue = 50f,
            defaultValueText = "50%",
            valueText = String.format(Locale.US, "%.0f%%", currentParams.pivotX),
            onValueChange = { onParamsChange(currentParams.copy(pivotX = it)) },
            onDefault = { onParamsChange(currentParams.copy(pivotX = 50f)) }
        )
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 20.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
        )
        SliderSettingFloat(
            title = "pivotY",
            value = currentParams.pivotY,
            range = 0f..100f,
            unit = "%",
            enabled = true,
            stepSize = 1f,
            defaultValue = 50f,
            defaultValueText = "50%",
            valueText = String.format(Locale.US, "%.0f%%", currentParams.pivotY),
            onValueChange = { onParamsChange(currentParams.copy(pivotY = it)) },
            onDefault = { onParamsChange(currentParams.copy(pivotY = 50f)) }
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        StudioConstructorSectionHeader(stringResource(R.string.anim_param_timing))
        SliderSetting(
            title = "duration",
            value = currentParams.duration.toInt(),
            range = 50..2000,
            unit = "ms",
            enabled = true,
            defaultValue = 350,
            defaultValueText = "350 ms",
            valueText = "${currentParams.duration} ms",
            onValueChange = { onParamsChange(currentParams.copy(duration = it.toLong())) },
            onDefault = { onParamsChange(currentParams.copy(duration = 350L)) }
        )
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 20.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
        )
        SliderSetting(
            title = "startOffset",
            value = currentParams.startOffset.toInt(),
            range = 0..1000,
            unit = "ms",
            enabled = true,
            defaultValue = 0,
            defaultValueText = "0 ms",
            valueText = "${currentParams.startOffset} ms",
            onValueChange = { onParamsChange(currentParams.copy(startOffset = it.toLong())) },
            onDefault = { onParamsChange(currentParams.copy(startOffset = 0L)) }
        )
    }
}

@Composable
private fun StudioConstructorSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 2.dp)
    )
}