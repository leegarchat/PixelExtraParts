package org.pixel.customparts.activities

import android.content.ClipData
import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationSet
import android.view.animation.AnimationUtils
import android.view.animation.Transformation
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.ui.draw.rotate
import org.pixel.customparts.ui.SliderTitleText
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.ui.graphics.toArgb
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.FileOpen
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
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp as lerpFloat
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch
import org.pixel.customparts.R
import org.pixel.customparts.ui.GraphicsLayerRecordingState
import org.pixel.customparts.ui.REBOOT_BUBBLE_CONTENT_BOTTOM_PADDING
import org.pixel.customparts.ui.SettingsGroupCard
import org.pixel.customparts.ui.SliderSetting
import org.pixel.customparts.ui.SliderSettingFloat
import org.pixel.customparts.ui.edgeOutline
import org.pixel.customparts.utils.filterThemeNameInput
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.roundToLong

// ── Card Flight Pseudo-Modal Overlay (Visible-Area Centered & Scrollable) ─

@Composable
fun CardFlightOverlay(
    target: StudioCard?,
    onClose: () -> Unit,
    applyLabel: String,
    activeLabel: String,
    isActive: (StudioCard) -> Boolean,
    onApplyCard: (StudioCard) -> Unit,
    onDeleteCard: ((StudioCard) -> Unit)?,
    speed: Float,
    onSpeedChange: (Float) -> Unit,
    scrollBlur: GraphicsLayerRecordingState,
    onProgress: (Float) -> Unit,
    previewContent: @Composable (StudioCard) -> Unit,
    topInsetPx: Float = 0f,
    closeSignal: Int = 0,
    onClosingChanged: (Boolean) -> Unit = {},
    secondaryPreviewContent: (@Composable (StudioCard) -> Unit)? = null,
    firstPreviewLabel: String? = null,
    secondPreviewLabel: String? = null,
    speedTitle: String? = null,
    secondarySpeed: Float? = null,
    onSecondarySpeedChange: ((Float) -> Unit)? = null,
    secondarySpeedTitle: String? = null,
    expandedApplyLabel: String = applyLabel,
    applyOpenLabel: String? = null,
    applyCloseLabel: String? = null,
    isOpenActive: ((StudioCard) -> Boolean)? = null,
    isCloseActive: ((StudioCard) -> Boolean)? = null,
    onApplyOpenCard: ((StudioCard) -> Unit)? = null,
    onApplyCloseCard: ((StudioCard) -> Unit)? = null,
    subtitleProvider: (StudioCard) -> String? = { it.subtitle },
    showSpeedSliderFor: (StudioCard) -> Boolean = { true }
) {
    val card = target ?: return
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val modalScrollState = rememberScrollState()

    val flightAnim = remember(card.key) { Animatable(0f) }
    var closing by remember(card.key) { mutableStateOf(false) }
    val hostCoordsRef = remember(card.key) { StudioCoordsRef() }
    val cardCoordsRef = remember(card.key) { StudioCoordsRef() }
    val flightCoordsRef = remember(card.key) { StudioCoordsRef() }

    LaunchedEffect(card.key) {
        onClosingChanged(false)
        withFrameNanos { }
        if (!closing) {
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

    fun closeAnimated(afterClose: (() -> Unit)? = null) {
        if (closing) return
        closing = true
        onClosingChanged(true)
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
                onClosingChanged(false)
                onProgress(0f)
                afterClose?.invoke()
                onClose()
            }
        }
    }

    BackHandler(enabled = !closing) {
        closeAnimated()
    }

    val initialCloseSignal = remember(card.key) { closeSignal }
    LaunchedEffect(closeSignal) {
        if (closeSignal > initialCloseSignal && !closing) {
            closeAnimated()
        }
    }

    val rawT = flightAnim.value.coerceIn(0f, 1f)
    val morphT = sstepE(rawT, 0f, 1f)
    DisposableEffect(morphT) {
        onProgress(morphT)
        onDispose { }
    }

    val active = isActive(card)
    val startCardColor = if (active) {
        MaterialTheme.colorScheme.surfaceContainerHigh
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }
    val endCardColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val containerColor = lerpColor(startCardColor, endCardColor, sstepE(morphT, 0.15f, 0.78f))
    val scrimAlpha = 0.42f * sstepE(morphT, 0.45f, 0.92f)
    val blurReveal = sstepE(morphT, 0.55f, 0.97f)
    val cornerProgress = sstepE(morphT, 0.40f, 0.88f)
    val modalCorner = lerp(20.dp, 24.dp, cornerProgress)
    val modalShape = RoundedCornerShape(modalCorner)
    val cardBorderColor = containerColor.edgeOutline()

    val flightProgress = sstepE(morphT, 0.0f, 0.96f)
    val headAlpha = 1f - sstepE(morphT, 0.0f, 0.25f)
    val previewGrow = sstepE(morphT, 0.05f, 0.78f)
    val sliderUnfold = sstepE(morphT, 0.22f, 0.88f)
    val sliderAlpha = sstepE(morphT, 0.32f, 0.88f)
    val trashMorph = sstepE(morphT, 0.18f, 0.90f)

    val topInsetDp = with(density) { topInsetPx.coerceAtLeast(0f).toDp() }
    val navBottomDp = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val navBottomPx = with(density) { navBottomDp.toPx() }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(1f)
    ) {
        val maxW = maxWidth
        val maxH = maxHeight
        val fallbackWpx = with(density) { maxWidth.toPx() }
        val fallbackHpx = with(density) { maxHeight.toPx() }

        val liveSource = card.sourceBoundsProvider().let {
            if (it.width > 1f && it.height > 1f) it else card.source
        }
        val endW = (maxW - 32.dp).coerceAtLeast(0.dp)
        val startW = if (liveSource.width > 1f) {
            with(density) { liveSource.width.roundToInt().toDp() }
        } else {
            endW
        }.coerceIn(0.dp, maxW)

        val cardWidth: Dp = lerp(startW, endW, sstepE(morphT, 0f, 0.90f))
        val visibleMaxCardH = (maxH - topInsetDp - navBottomDp - 24.dp).coerceAtLeast(180.dp)
        val startCardH = if (liveSource.height > 1f) {
            with(density) { liveSource.height.toDp() }.coerceAtLeast(visibleMaxCardH)
        } else {
            maxH
        }
        val currentMaxCardH = lerp(startCardH, visibleMaxCardH, sstepE(morphT, 0.18f, 0.88f))

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = scrimAlpha))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { if (!closing) closeAnimated() }
                )
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = topInsetDp, bottom = navBottomDp)
                .imePadding()
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

            val clipViewport = scrollBlur.contentBounds
                .takeIf { it.width > 1f && it.height > 1f }
                ?: Rect(0f, 0f, fallbackWpx, fallbackHpx)

            Box(
                modifier = Modifier
                    .width(cardWidth)
                    .heightIn(max = currentMaxCardH)
                    .onPlaced { flightCoordsRef.value = it }
                    .graphicsLayer {
                        translationX = dx
                        translationY = dy
                    }
                    .drawWithContent {
                        if (clipViewport.width > 1f && flightProgress < 0.999f) {
                            val w = size.width
                            val h = size.height
                            val rL = src.center.x - w / 2f
                            val rT = src.center.y - h / 2f
                            val keep = 1f - flightProgress
                            clipRect(
                                left = (clipViewport.left - rL).coerceIn(0f, w) * keep,
                                top = (clipViewport.top - rT).coerceIn(0f, h) * keep,
                                right = w - (w - (clipViewport.right - rL).coerceIn(0f, w)) * keep,
                                bottom = h - (h - (clipViewport.bottom - rT).coerceIn(0f, h)) * keep
                            ) {
                                this@drawWithContent.drawContent()
                            }
                        } else {
                            drawContent()
                        }
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
                            .background(containerColor)
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
                                        alpha = lerpFloat(1f, 0.66f, blurReveal)
                                    )
                                )
                        )
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(modalScrollState, enabled = morphT > 0.7f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Slot 1: Header (Dynamic 2-line sizer grows with SliderTitleText, never clips!)
                        val headerPadStart = lerp(16.dp, 20.dp, cornerProgress)
                        val headerPadEnd = lerp(12.dp, 20.dp, cornerProgress)
                        val headerPadTop = lerp(12.dp, 18.dp, cornerProgress)
                        val titleGrow = sstepE(morphT, 0.05f, 0.75f)
                        val liveSubtitle = subtitleProvider(card)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    start = headerPadStart,
                                    end = headerPadEnd,
                                    top = headerPadTop,
                                    bottom = 4.dp
                                ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier.weight(1f),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                // Невидимый 2-строчный эталон, растущий синхронно с titleGrow
                                Column(
                                    modifier = Modifier.alpha(0f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    SliderTitleText(
                                        title = "Ag",
                                        growProgress = titleGrow,
                                        maxGrow = 2f,
                                        baseStyle = MaterialTheme.typography.titleSmall.copy(
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    )
                                    Text(
                                        text = "Ag",
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1
                                    )
                                }

                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    SliderTitleText(
                                        title = card.title,
                                        growProgress = titleGrow,
                                        maxGrow = 2f,
                                        baseStyle = MaterialTheme.typography.titleSmall.copy(
                                            fontWeight = FontWeight.SemiBold
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    if (!liveSubtitle.isNullOrEmpty()) {
                                        Text(
                                            text = liveSubtitle,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                            if (headAlpha > 0.01f) {
                                Spacer(Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Rounded.KeyboardArrowDown,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .size(22.dp)
                                        .alpha(headAlpha)
                                )
                            }
                        }

                        // Slot 2: Animation Preview Stage (Single or Dual Slide-Out)
                        if (secondaryPreviewContent == null) {
                            val previewScale = lerpFloat(1f, 1.45f, previewGrow)
                            val stageHeight = lerp(206.dp, 296.dp, previewGrow)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(stageHeight),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier.graphicsLayer {
                                        scaleX = previewScale
                                        scaleY = previewScale
                                    }
                                ) {
                                    previewContent(card)
                                }
                            }
                        } else {
                            val splitT = sstepE(morphT, 0.10f, 0.88f)
                            val maxDualScale = ((cardWidth - 48.dp).value / (2f * 110f)).coerceIn(1f, 1.18f)
                            val dualScale = lerpFloat(1f, maxDualScale, previewGrow)
                            val stageHeight = lerp(206.dp, 276.dp, previewGrow)
                            val halfOffsetDp = (110.dp * dualScale + 16.dp) / 2f
                            val halfOffsetPx = with(density) { halfOffsetDp.toPx() } * splitT
                            val labelAlpha = sstepE(splitT, 0.35f, 0.90f)
                            val labelOffsetY = with(density) { (102.dp * dualScale + 14.dp).toPx() }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(stageHeight),
                                contentAlignment = Alignment.Center
                            ) {
                                // 2nd Card (Close / Выход) slides out to the right from behind the 1st card
                                if (splitT > 0.01f) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.graphicsLayer {
                                            translationX = halfOffsetPx
                                            alpha = sstepE(splitT, 0.02f, 0.42f)
                                        }
                                    ) {
                                        if (secondPreviewLabel != null) {
                                            Text(
                                                text = secondPreviewLabel,
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.graphicsLayer {
                                                    translationY = -labelOffsetY
                                                    alpha = labelAlpha
                                                }
                                            )
                                        }
                                        Box(
                                            modifier = Modifier.graphicsLayer {
                                                scaleX = dualScale
                                                scaleY = dualScale
                                            }
                                        ) {
                                            secondaryPreviewContent(card)
                                        }
                                    }
                                }

                                // 1st Card (Open / Вход) moves left as Card 2 slides out right
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.graphicsLayer {
                                        translationX = -halfOffsetPx
                                    }
                                ) {
                                    if (firstPreviewLabel != null && splitT > 0.01f) {
                                        Text(
                                            text = firstPreviewLabel,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.graphicsLayer {
                                                translationY = -labelOffsetY
                                                alpha = labelAlpha
                                            }
                                        )
                                    }
                                    Box(
                                        modifier = Modifier.graphicsLayer {
                                            scaleX = dualScale
                                            scaleY = dualScale
                                        }
                                    ) {
                                        previewContent(card)
                                    }
                                }
                            }
                        }

                        // Slot 3: Speed Slider(s) — hidden for stock/passthrough cards
                        if (showSpeedSliderFor(card)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .unfoldVerticalSlot(sliderUnfold)
                                .graphicsLayer {
                                    alpha = sliderAlpha
                                    translationY = (1f - sliderUnfold) * with(density) { 16.dp.toPx() }
                                }
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                SliderSettingFloat(
                                    title = speedTitle ?: stringResource(R.string.rotation_anim_speed),
                                    value = speed,
                                    range = 0.1f..10f,
                                    unit = "x",
                                    enabled = !closing && morphT > 0.6f,
                                    onValueChange = onSpeedChange,
                                    onDefault = { onSpeedChange(1f) },
                                    valueText = String.format(Locale.US, "%.1fx", speed),
                                    stepSize = 0.1f,
                                    defaultValue = 1f,
                                    defaultValueText = "1.0x",
                                    showDefaultButton = true
                                )
                                if (secondarySpeed != null && onSecondarySpeedChange != null) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(horizontal = 20.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                                    )
                                    SliderSettingFloat(
                                        title = secondarySpeedTitle ?: stringResource(R.string.rotation_anim_speed),
                                        value = secondarySpeed,
                                        range = 0.1f..10f,
                                        unit = "x",
                                        enabled = !closing && morphT > 0.6f,
                                        onValueChange = onSecondarySpeedChange,
                                        onDefault = { onSecondarySpeedChange(1f) },
                                        valueText = String.format(Locale.US, "%.1fx", secondarySpeed),
                                        stepSize = 0.1f,
                                        defaultValue = 1f,
                                        defaultValueText = "1.0x",
                                        showDefaultButton = true
                                    )
                                }
                            }
                        }
                        }

                        val sidePad = lerp(12.dp, 20.dp, cornerProgress)
                        val bottomPad = lerp(12.dp, 20.dp, cornerProgress)

                        // Slot 3.5: Separate Package Name item right before Apply & Delete buttons
                        if (card is StudioCard.Theme && card.packageName.isNotEmpty()) {
                            StudioPackageNameItem(
                                packageName = card.packageName,
                                horizontalPadding = sidePad
                            )
                        }

                        // Slot 4: Apply Button(s) + Morphing Trash -> "Delete Theme" Button
                        val isThemeCard = card is StudioCard.Theme && onDeleteCard != null
                        val hasSplitApply = onApplyOpenCard != null &&
                            onApplyCloseCard != null &&
                            applyOpenLabel != null &&
                            applyCloseLabel != null

                        BoxWithConstraints(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    start = sidePad,
                                    end = sidePad,
                                    top = lerp(2.dp, 6.dp, cornerProgress),
                                    bottom = bottomPad
                                )
                        ) {
                            val totalW = maxWidth
                            val gap = 6.dp
                            val rowGap = 10.dp
                            val btnH = 46.dp

                            val extraRowsCount = (if (hasSplitApply) 2 else 0) + (if (isThemeCard) 1 else 0)
                            val targetContainerH = btnH + (btnH + rowGap) * extraRowsCount
                            val containerH = if (extraRowsCount > 0) {
                                lerp(btnH, targetContainerH, trashMorph)
                            } else {
                                btnH
                            }

                            val applyW = if (isThemeCard) {
                                val startApplyW = (totalW - btnH - gap).coerceAtLeast(48.dp)
                                lerp(startApplyW, totalW, sstepE(trashMorph, 0f, 0.55f))
                            } else {
                                totalW
                            }

                            val primaryApplyText = if (morphT > 0.35f) expandedApplyLabel else applyLabel

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(containerH)
                            ) {
                                StudioTabStyledApplyButton(
                                    isActive = active,
                                    applyLabel = primaryApplyText,
                                    activeLabel = activeLabel,
                                    enabled = !closing && !active && morphT > 0.5f,
                                    onClick = {
                                        onApplyCard(card)
                                        closeAnimated()
                                    },
                                    modifier = Modifier
                                        .width(applyW)
                                        .align(Alignment.TopStart)
                                )

                                if (hasSplitApply) {
                                    val splitBtnAlpha = sstepE(trashMorph, 0.25f, 0.85f)
                                    val openRowY = lerp(0.dp, btnH + rowGap, sstepE(trashMorph, 0f, 0.75f))
                                    val closeRowY = lerp(0.dp, (btnH + rowGap) * 2, sstepE(trashMorph, 0.08f, 0.85f))
                                    val openAct = isOpenActive?.invoke(card) == true
                                    val closeAct = isCloseActive?.invoke(card) == true

                                    if (splitBtnAlpha > 0.01f) {
                                        StudioTabStyledApplyButton(
                                            isActive = openAct,
                                            applyLabel = applyOpenLabel!!,
                                            activeLabel = "$applyOpenLabel • $activeLabel",
                                            enabled = !closing && !openAct && morphT > 0.6f,
                                            onClick = {
                                                onApplyOpenCard?.invoke(card)
                                                closeAnimated()
                                            },
                                            modifier = Modifier
                                                .offset {
                                                    IntOffset(
                                                        x = 0,
                                                        y = with(density) { openRowY.roundToPx() }
                                                    )
                                                }
                                                .fillMaxWidth()
                                                .alpha(splitBtnAlpha)
                                        )

                                        StudioTabStyledApplyButton(
                                            isActive = closeAct,
                                            applyLabel = applyCloseLabel!!,
                                            activeLabel = "$applyCloseLabel • $activeLabel",
                                            enabled = !closing && !closeAct && morphT > 0.6f,
                                            onClick = {
                                                onApplyCloseCard?.invoke(card)
                                                closeAnimated()
                                            },
                                            modifier = Modifier
                                                .offset {
                                                    IntOffset(
                                                        x = 0,
                                                        y = with(density) { closeRowY.roundToPx() }
                                                    )
                                                }
                                                .fillMaxWidth()
                                                .alpha(splitBtnAlpha)
                                        )
                                    }
                                }

                                if (isThemeCard) {
                                    val trashArmedOnCard = (card as? StudioCard.Theme)
                                        ?.isTrashArmedProvider?.invoke() == true
                                    val dockedTrashCorner by animateFloatAsState(
                                        targetValue = if (trashArmedOnCard) 28f else 12f,
                                        animationSpec = spring(
                                            stiffness = Spring.StiffnessMedium,
                                            dampingRatio = Spring.DampingRatioNoBouncy
                                        ),
                                        label = "ModalDockedTrashCorner"
                                    )
                                    val trashBtnShape = RoundedCornerShape(
                                        lerp(dockedTrashCorner.dp, 28.dp, trashMorph)
                                    )
                                    val trashRowSlot = if (hasSplitApply) 3 else 1
                                    val trashY = lerp(
                                        0.dp,
                                        (btnH + rowGap) * trashRowSlot,
                                        sstepE(trashMorph, 0f, 0.72f)
                                    )
                                    val trashW = lerp(btnH, totalW, sstepE(trashMorph, 0.25f, 1f))
                                    val trashX = (totalW - trashW).coerceAtLeast(0.dp)
                                    val iconAlpha = 1f - sstepE(trashMorph, 0.15f, 0.50f)
                                    val textAlpha = sstepE(trashMorph, 0.45f, 0.90f)
                                    val trashBg = MaterialTheme.colorScheme.errorContainer
                                    val trashContent = MaterialTheme.colorScheme.onErrorContainer

                                    Box(
                                        modifier = Modifier
                                            .offset {
                                                IntOffset(
                                                    x = with(density) { trashX.roundToPx() },
                                                    y = with(density) { trashY.roundToPx() }
                                                )
                                            }
                                            .width(trashW)
                                            .height(btnH)
                                            .clip(trashBtnShape)
                                            .background(trashBg)
                                            .border(1.dp, trashBg.edgeOutline(), trashBtnShape)
                                            .clickable(enabled = !closing && morphT > 0.6f) {
                                                haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                                                closeAnimated {
                                                    onDeleteCard?.invoke(card)
                                                }
                                            }
                                            .padding(horizontal = 12.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (iconAlpha > 0.01f) {
                                            Icon(
                                                imageVector = if (trashArmedOnCard) {
                                                    Icons.Rounded.Check
                                                } else {
                                                    Icons.Rounded.Delete
                                                },
                                                contentDescription = null,
                                                tint = trashContent,
                                                modifier = Modifier
                                                    .size(20.dp)
                                                    .alpha(iconAlpha)
                                            )
                                        }
                                        if (textAlpha > 0.01f) {
                                            Text(
                                                text = stringResource(R.string.rotation_anim_delete_theme),
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.Bold,
                                                color = trashContent,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                textAlign = TextAlign.Center,
                                                modifier = Modifier.alpha(textAlpha)
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

// ── Interpolator Morphing Pseudo-Modal Overlay ──────────────────────────

@Stable
data class InterpolatorMorphRequest(
    val selectedIndex: Int,
    val sourceRect: Rect,
    val sourceBoundsProvider: () -> Rect,
    val onSelect: (Int) -> Unit,
    val sessionId: Long = System.nanoTime()
)

@Composable
fun InterpolatorMorphOverlay(
    request: InterpolatorMorphRequest?,
    onClose: () -> Unit,
    scrollBlur: GraphicsLayerRecordingState,
    onProgress: (Float) -> Unit,
    topInsetPx: Float = 0f,
    closeSignal: Int = 0
) {
    val req = request ?: return
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val listScrollState = rememberScrollState()

    val flightAnim = remember(req.sessionId) { Animatable(0f) }
    var closing by remember(req.sessionId) { mutableStateOf(false) }
    // Keeps the newly chosen index during the exit flight so it never snaps back to the old value
    var activeSelectedIndex by remember(req.sessionId) { mutableIntStateOf(req.selectedIndex) }
    val hostCoordsRef = remember(req.sessionId) { StudioCoordsRef() }
    val cardCoordsRef = remember(req.sessionId) { StudioCoordsRef() }
    val flightCoordsRef = remember(req.sessionId) { StudioCoordsRef() }

    LaunchedEffect(req.sessionId) {
        withFrameNanos { }
        if (!closing) {
            haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
            flightAnim.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    stiffness = 120f,
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    visibilityThreshold = 0.002f
                )
            )
        }
    }

    fun closeAnimated(afterClose: (() -> Unit)? = null) {
        if (closing) return
        closing = true
        scope.launch {
            flightAnim.stop()
            try {
                flightAnim.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(
                        stiffness = 160f,
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        visibilityThreshold = 0.05f
                    )
                )
            } finally {
                onProgress(0f)
                afterClose?.invoke()
                onClose()
            }
        }
    }

    BackHandler(enabled = !closing) {
        closeAnimated()
    }

    val initialCloseSignal = remember(req.sessionId) { closeSignal }
    LaunchedEffect(closeSignal) {
        if (closeSignal > initialCloseSignal && !closing) {
            closeAnimated()
        }
    }

    val rawT = flightAnim.value.coerceIn(0f, 1f)
    val morphT = sstepE(rawT, 0f, 1f)
    DisposableEffect(morphT) {
        onProgress(morphT)
        onDispose { }
    }

    val buttonBg = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.68f)
    val modalSurface = MaterialTheme.colorScheme.surfaceContainerHigh
    val surfaceTransition = sstepE(morphT, 0.08f, 0.65f)
    val containerColor = lerpColor(buttonBg, modalSurface, surfaceTransition)

    val scrimAlpha = 0.42f * sstepE(morphT, 0.45f, 0.92f)
    val blurReveal = sstepE(morphT, 0.45f, 0.95f)
    val cornerProgress = sstepE(morphT, 0.25f, 0.85f)
    val modalCorner = lerp(28.dp, 24.dp, cornerProgress)
    val modalShape = RoundedCornerShape(modalCorner)
    val cardBorderColor = containerColor.edgeOutline()

    val flightProgress = sstepE(morphT, 0.0f, 0.96f)
    val listUnfold = sstepE(morphT, 0.14f, 0.88f)

    val topInsetDp = with(density) { topInsetPx.coerceAtLeast(0f).toDp() }
    val navBottomDp = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val navBottomPx = with(density) { navBottomDp.toPx() }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(1f)
    ) {
        val maxW = maxWidth
        val maxH = maxHeight
        val fallbackWpx = with(density) { maxWidth.toPx() }
        val fallbackHpx = with(density) { maxHeight.toPx() }

        val liveSource = req.sourceBoundsProvider().let {
            if (it.width > 1f && it.height > 1f) it else req.sourceRect
        }
        val endW = (maxW - 32.dp).coerceAtLeast(0.dp)
        val startW = if (liveSource.width > 1f) {
            with(density) { liveSource.width.roundToInt().toDp() }
        } else {
            endW
        }.coerceIn(0.dp, maxWidth)

        val cardWidth: Dp = lerp(startW, endW, sstepE(morphT, 0f, 0.90f))
        val visibleMaxCardH = (maxH - topInsetDp - navBottomDp - 24.dp).coerceAtLeast(180.dp)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = scrimAlpha))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = !closing,
                    onClick = { closeAnimated() }
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
                    .heightIn(max = visibleMaxCardH)
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
                            .background(containerColor)
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
                                    modalSurface.copy(
                                        alpha = 0.66f * blurReveal
                                    )
                                )
                        )
                    }

                    val headerColorLeft = lerpColor(
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                        MaterialTheme.colorScheme.primary,
                        surfaceTransition
                    )
                    val headerColorRight = lerpColor(
                        MaterialTheme.colorScheme.onPrimaryContainer,
                        MaterialTheme.colorScheme.onSurfaceVariant,
                        surfaceTransition
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(listScrollState, enabled = morphT > 0.7f)
                    ) {
                        // Top Row: at morphT=0 matches the 46.dp button 1:1!
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(lerp(46.dp, 54.dp, cornerProgress))
                                .padding(horizontal = lerp(16.dp, 20.dp, cornerProgress)),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = stringResource(R.string.anim_param_interpolator),
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontSize = lerp(
                                        MaterialTheme.typography.labelLarge.fontSize,
                                        MaterialTheme.typography.titleMedium.fontSize,
                                        cornerProgress
                                    ),
                                    fontWeight = if (cornerProgress > 0.4f) FontWeight.Bold else FontWeight.Medium
                                ),
                                color = headerColorLeft,
                                maxLines = 1
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = INTERPOLATOR_NAMES.getOrElse(activeSelectedIndex) { "Linear" },
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = headerColorRight,
                                    maxLines = 1
                                )
                                Spacer(Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Rounded.KeyboardArrowDown,
                                    contentDescription = null,
                                    tint = headerColorRight,
                                    modifier = Modifier
                                        .size(20.dp)
                                        .rotate(180f * listUnfold)
                                )
                            }
                        }

                        // Unfolding options list
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .unfoldVerticalSlot(listUnfold)
                                .alpha(listUnfold)
                                .padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            INTERPOLATOR_NAMES.forEachIndexed { index, name ->
                                val isSelected = index == activeSelectedIndex
                                val itemBg = if (isSelected) {
                                    MaterialTheme.colorScheme.secondaryContainer
                                } else {
                                    Color.Transparent
                                }
                                val itemShape = RoundedCornerShape(14.dp)

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(itemShape)
                                        .background(itemBg)
                                        .then(
                                            if (isSelected) {
                                                Modifier.border(1.dp, itemBg.edgeOutline(), itemShape)
                                            } else {
                                                Modifier
                                            }
                                        )
                                        .clickable(enabled = !closing && morphT > 0.5f) {
                                            haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                                            activeSelectedIndex = index
                                            req.onSelect(index)
                                            closeAnimated()
                                        }
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) {
                                            MaterialTheme.colorScheme.onSecondaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        }
                                    )
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Rounded.CheckCircle,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
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
