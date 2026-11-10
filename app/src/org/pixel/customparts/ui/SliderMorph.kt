package org.pixel.customparts.ui

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.launch
import org.pixel.customparts.R
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private class CoordsRef(var value: LayoutCoordinates? = null)

@Stable
class SliderMorphHostState {
    var request by mutableStateOf<SliderMorphRequest?>(null)
        private set

    fun open(request: SliderMorphRequest) {
        this.request = request
    }

    fun close(target: SliderMorphRequest? = null) {
        if (target == null || request?.sessionId == target.sessionId) {
            request = null
        }
    }

    fun closeById(sessionId: Long) {
        if (request?.sessionId == sessionId) {
            request = null
        }
    }
}

val LocalSliderMorphHost = compositionLocalOf<SliderMorphHostState?> { null }

@Composable
fun rememberSliderMorphHostState(): SliderMorphHostState =
    remember { SliderMorphHostState() }

data class SliderMorphRequest(
    val title: String,
    val initialValue: Float,
    val rangeStart: Float,
    val rangeEnd: Float,
    val unit: String,
    val isInteger: Boolean,
    val stepSize: Float,
    val formattedValue: String,
    val showResetBadge: Boolean,
    val resetText: String,
    val sourceInRoot: Rect,
    val valueFlyDyPx: Float,
    val onConfirm: (Float) -> Unit,
    val onDefault: () -> Unit,
    val onDismiss: () -> Unit,
    val valueFlyDxPx: Float = 0f,
    val visualRangeStart: Float = rangeStart,
    val visualRangeEnd: Float = rangeEnd,
    val sliderFraction: Float = if (rangeEnd > rangeStart) {
        ((initialValue - rangeStart) / (rangeEnd - rangeStart)).coerceIn(0f, 1f)
    } else {
        0f
    },
    val initialTrackHeightPx: Float = 0f,
    val defaultValue: Float? = null,
    val resetThreshold: Float = (stepSize * 0.5f).coerceAtLeast(1e-6f),
    val showDefaultButton: Boolean = true,
    val showInfoIcon: Boolean = false,
    val sourceBoundsProvider: () -> Rect = { sourceInRoot },
    val sessionId: Long = System.nanoTime(),
)

@Composable
fun SliderMorphRoot(
    blurState: GraphicsLayerRecordingState? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val host = rememberSliderMorphHostState()
    var rootRect by remember { mutableStateOf(Rect.Zero) }
    CompositionLocalProvider(LocalSliderMorphHost provides host) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onPlaced { rootRect = it.boundsInRoot() }
                .onGloballyPositioned { rootRect = it.boundsInRoot() }
        ) {
            content()
            SliderMorphOverlay(
                host = host,
                blurState = blurState,
                fallbackHostRect = rootRect
            )
        }
    }
}

/** Smoothstep window over a shared progress (float-bar reveal pattern). */
private fun sstep(x: Float, a: Float, b: Float): Float {
    if (b <= a) return if (x >= b) 1f else 0f
    val t = ((x - a) / (b - a)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

private val ReadoutLeadingNum = Regex("""-?\d+(?:[.,]\d+)?""")

/**
 * Builds the readout text for the exit flight after Apply: the leading numeric
 * token of the open-time [old] text is swapped for [confirmed], preserving the
 * surrounding custom suffix/prefix/spacing (e.g. "1.50x", "500 lx", "30%") so the
 * flying text matches what the updated row behind will show. Falls back to the
 * shared auto format only when [old] carries no number.
 */
private fun formatExitReadout(old: String, confirmed: Float, isInteger: Boolean, unit: String): String {
    if (isInteger) {
        val num = confirmed.toInt().toString()
        val m = ReadoutLeadingNum.find(old)
        return if (m != null) {
            old.replaceRange(m.range, num)
        } else {
            if (unit.isNotEmpty()) "$num $unit" else num
        }
    }
    val m = ReadoutLeadingNum.find(old)
    val newNum = formatFloatLike(oldToken = m?.value, value = confirmed)
    return if (m != null) {
        old.replaceRange(m.range, newNum)
    } else {
        if (unit.isNotEmpty()) "$newNum $unit" else newNum
    }
}

/**
 * Formats a confirmed float with the same fraction width as the open-time token
 * when present (keeps "%.2f"-style trailing zeros), extended to the natural
 * precision of [value] so narrower open-time tokens never truncate the new value.
 */
private fun formatFloatLike(oldToken: String?, value: Float): String {
    val oldFrac = oldToken?.let {
        val i = it.indexOfFirst { ch -> ch == '.' || ch == ',' }
        if (i >= 0) (it.length - i - 1).coerceIn(0, 6) else 0
    } ?: 2
    val plain = value.toString()
    val naturalFrac = plain.indexOf('.').let { i ->
        if (i < 0) {
            0
        } else {
            var e = plain.length - 1
            while (e > i && plain[e] == '0') e--
            (e - i).coerceIn(0, 6)
        }
    }
    val digits = maxOf(oldFrac, naturalFrac).coerceIn(0, 6)
    var out = String.format(Locale.US, "%.${digits}f", value)
    if (oldToken?.contains(',') == true) out = out.replace('.', ',')
    return out
}

@Composable
fun SliderMorphOverlay(
    host: SliderMorphHostState,
    blurState: GraphicsLayerRecordingState?,
    modifier: Modifier = Modifier,
    fallbackHostRect: Rect = Rect.Zero,
) {
    val req = host.request ?: return
    val haptics = LocalHapticFeedback.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    val containerColor = MaterialTheme.colorScheme.surface

    val morphAnim = remember(req.sessionId) { Animatable(0f) }
    var closing by remember(req.sessionId) { mutableStateOf(false) }
    val hostCoordsRef = remember(req.sessionId) { CoordsRef() }
    val cardCoordsRef = remember(req.sessionId) { CoordsRef() }
    // Layout size of the flight card wrapper (transform-independent IntSize).
    // Needed to cancel the integer Center-placement parity below.
    val flightCoordsRef = remember(req.sessionId) { CoordsRef() }

    LaunchedEffect(req.sessionId) {
        // Warm up Frame 0 at morphT = 0f (exact 1:1 visual match with the row)
        // so all subtrees are composed and measured before the spring starts.
        withFrameNanos { }
        if (!closing) {
            haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
            morphAnim.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    stiffness = 250f,
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    visibilityThreshold = 0.002f
                )
            )
        }
    }

    fun closeAnimated(commit: () -> Unit) {
        if (closing) return
        closing = true
        // Stop a still-running enter spring first: concurrent animateTo calls on
        // one Animatable serialize on its mutex and would stall the exit.
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
        scope.launch {
            morphAnim.stop()
            try {
                // Stiff close with a coarse threshold: the sstep easing flattens
                // the visual tail, so a fine threshold only adds ~200ms of dead
                // overlay time blocking list scroll after everything is in place.
                morphAnim.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(
                        stiffness = 500f,
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        visibilityThreshold = 0.01f
                    )
                )
            } finally {
                commit()
                host.close(req)
            }
        }
    }

    BackHandler(enabled = !closing) {
        closeAnimated(req.onDismiss)
    }

    // The raw spring is fastest at t=0; smooth both ends so detach and landing are soft.
    val rawT = morphAnim.value.coerceIn(0f, 1f)
    val morphT = sstep(rawT, 0f, 1f)

    // Dim, rounding, tint and outline switch on mid-flight, not at the start.
    val scrimAlpha = 0.42f * sstep(morphT, 0.45f, 0.92f)
    val cornerProgress = sstep(morphT, 0.40f, 0.88f)
    val modalCorner = lerp(48.dp, 22.dp, cornerProgress)
    val modalShape = RoundedCornerShape(modalCorner)
    val outlineAlpha = sstep(morphT, 0.65f, 0.96f)
    val cardBorderColor = containerColor.edgeOutline().let {
        it.copy(alpha = it.alpha * outlineAlpha)
    }

    // No pulse inflation: width strictly matches the card through the whole morph.
    val cardScale = 1f

    // While docked inside SettingsGroupCard, keep 1.dp outside (so the card's
    // own 1.dp side borders are not painted over). The row's single 20.dp side
    // inset is therefore split into outer (border gap) + inner paddings — but
    // two separately-rounded insets can sum to ±1px vs one rounded 20.dp
    // (e.g. on x.5 densities: round(1.dp)+round(19.dp) != round(20.dp)).
    // Derive inner from rounded ints so round(outerPx)+round(innerPx) is
    // exactly round(20.dp.px) at the docked/landed endpoints on any density.
    val outerInsetH = lerp(1.dp, 0.dp, cornerProgress)
    val innerPadH = with(density) {
        val totalPx = 20.dp.toPx()
        val outerPxInt = outerInsetH.toPx().roundToInt()
        (totalPx - outerPxInt).toDp()
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize().zIndex(1f)) {
        val maxW = maxWidth
        val fallbackWpx = with(density) { maxWidth.toPx() }
        val fallbackHpx = with(density) { maxHeight.toPx() }
        // Single snapshot per frame: card width and flight offset must share
        // it so attach (flight=0) and landing coincide pixel-exactly.
        // Same boundsInRoot space is used for source, host and card on every
        // frame, so scroll position cannot skew either endpoint.
        val liveSource = req.sourceBoundsProvider().let {
            if (it.width > 1f && it.height > 1f) it else req.sourceInRoot
        }
        // Snap to int px before the px->dp->px round-trip so the docked width
        // is exactly the measured source width (no float dust -> off-by-one).
        // With no valid measurement yet, start at the dialog width (as before).
        val endW = (maxW - 32.dp).coerceAtLeast(0.dp)
        val startW = if (liveSource.width > 1f) {
            with(density) { liveSource.width.roundToInt().toDp() }
        } else {
            endW
        }.coerceIn(0.dp, maxW)

        // Card width morphs from the MEASURED source width to the dialog width
        // (screen width minus dialog margins) — no fixed floors, so wide rows
        // start 1:1 instead of jumping from a hardcoded geometry.
        val cardWidth: Dp = lerp(startW, endW, sstep(morphT, 0f, 0.90f))

        // Light dim scrim outside the card.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = scrimAlpha))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = !closing,
                    onClick = { closeAnimated(req.onDismiss) }
                )
        )

        // Visible-area centering host.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .onPlaced { hostCoordsRef.value = it }
                .onGloballyPositioned { hostCoordsRef.value = it },
            contentAlignment = Alignment.Center
        ) {
            val flightProgress = sstep(morphT, 0.0f, 0.96f)

            // Float-precise flight vector in boundsInRoot px, resolved once per
            // frame from the same liveSource snapshot as the width above.
            // Applied via graphicsLayer (sub-pixel) instead of Modifier.offset
            // (IntOffset quantization = ±1px attach/detach jump).
            //
            // Surgical docking: Box(Alignment.Center) places the flight card at
            // INTEGER (host - card)/2, while the delta below is computed from
            // float bounds centers. The parity residual (0 or -0.5px whenever
            // host-card is odd) used to survive into translationX/Y, so the
            // docked card straddled pixels and its 1.dp edges fringed — most
            // visible on far-from-center rows near the screen edges. e0x/e0y
            // cancel exactly that residual, scaled by (1-flight), so the
            // free-flight path and the landed (flight=1, translation=0)
            // geometry stay byte-for-byte identical to before.
            val hostBounds = hostCoordsRef.value?.boundsInRoot()
            val targetHost = when {
                hostBounds != null && hostBounds.width > 1f && hostBounds.height > 1f -> hostBounds
                fallbackHostRect.width > 1f && fallbackHostRect.height > 1f -> fallbackHostRect
                else -> Rect(0f, 0f, fallbackWpx, fallbackHpx)
            }
            val src = liveSource
            val dockW = 1f - flightProgress
            val cardWpx = with(density) { cardWidth.toPx() }.roundToInt()
            // Layout height of the flight card (transform-independent). Before
            // the first placement it equals the source height: at morphT = 0
            // every slot/padding matches the row 1:1 (title, 12.dp pads,
            // 10.dp gaps, 32.dp bottom row, slider slot = measured track).
            val cardHpx = flightCoordsRef.value?.takeIf { it.isAttached }?.size?.height
                ?.takeIf { it > 0 }
                ?: src.height.roundToInt().coerceAtLeast(1)
            val hostWpx = targetHost.width.roundToInt()
            val hostHpx = targetHost.height.roundToInt()
            // Integer Center-placement vs float bounds-center residual.
            // Matches Box alignment op-for-op: (host - card)/2 is Int division.
            val e0x = (hostWpx - cardWpx) / 2 + cardWpx / 2f - targetHost.width / 2f
            val e0y = (hostHpx - cardHpx) / 2 + cardHpx / 2f - targetHost.height / 2f
            val dx = if (targetHost.width > 1f && src.width > 1f) {
                (src.center.x - targetHost.center.x - e0x) * dockW
            } else {
                0f
            }
            val dy = if (targetHost.height > 1f && src.height > 1f) {
                (src.center.y - targetHost.center.y - e0y) * dockW
            } else {
                0f
            }

            Box(
                modifier = Modifier
                    .width(cardWidth)
                    .onPlaced { flightCoordsRef.value = it }
                    .graphicsLayer {
                        translationX = dx
                        translationY = dy
                        scaleX = cardScale
                        scaleY = cardScale
                    },
                contentAlignment = Alignment.Center
            ) {
                // Morphed card container with 1.dp side-border compensation of SettingsGroupCard.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = outerInsetH)
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
                    if (blurState != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .graphicsLayer { clip = true }
                                .onPlaced { cardCoordsRef.value = it }
                                .onGloballyPositioned { cardCoordsRef.value = it }
                                .sliderMorphBlurMask(
                                    state = blurState,
                                    coordsProvider = { cardCoordsRef.value },
                                    scaleProvider = { cardScale }
                                )
                        )
                        // Tint (transparency under blur) switches on mid-flight (0.45..0.92).
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .background(
                                    containerColor.copy(
                                        alpha = lerp(1f, 0.62f, sstep(morphT, 0.45f, 0.92f))
                                    )
                                )
                        )
                    }

                    val padVert = lerp(12.dp, 18.dp, sstep(morphT, 0.18f, 0.88f))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = innerPadH, vertical = padVert)
                    ) {
                        SliderMorphEditorContent(
                            request = req,
                            morphT = morphT,
                            outlineAlpha = outlineAlpha,
                            closing = closing,
                            closeAnimated = ::closeAnimated
                        )
                    }
                }
            }
        }
    }
}

/**
 * Unfolds a pill element smoothly from left-to-right and vertically without popping/bouncing.
 */
private fun Modifier.unfoldPill(progress: Float): Modifier = this.layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
    val p = progress.coerceIn(0f, 1f)
    if (p <= 0.001f) {
        layout(0, 0) {}
    } else {
        val hFactor = sstep(p, 0f, 0.45f)
        val wFactor = sstep(p, 0.05f, 1f)
        val targetH = (placeable.height * hFactor).roundToInt().coerceAtLeast(1)
        val minPillW = minOf(placeable.width, targetH)
        val targetW = lerp(minPillW.toFloat(), placeable.width.toFloat(), wFactor)
            .roundToInt()
            .coerceIn(1, placeable.width)
        layout(targetW, targetH) {
            placeable.place(0, (targetH - placeable.height) / 2)
        }
    }
}

private fun Modifier.sliderMorphBlurMask(
    state: GraphicsLayerRecordingState,
    coordsProvider: () -> LayoutCoordinates?,
    scaleProvider: () -> Float = { 1f },
): Modifier = this.drawBehind {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return@drawBehind
    val blurNode = state.sharedBlurNode ?: return@drawBehind
    if (!blurNode.hasDisplayList()) return@drawBehind
    val coords = coordsProvider() ?: return@drawBehind
    if (!coords.isAttached) return@drawBehind
    val region = coords.boundsInRoot()
    if (region.isEmpty) return@drawBehind
    val s = scaleProvider().coerceAtLeast(0.01f)
    drawIntoCanvas { canvas ->
        val native = canvas.nativeCanvas
        val saveCount = native.save()
        if (abs(s - 1f) > 0.001f) {
            val centerInRoot = region.center
            native.translate(size.width * 0.5f, size.height * 0.5f)
            native.scale(1f / s, 1f / s)
            native.translate(-centerInRoot.x, -centerInRoot.y)
        } else {
            native.translate(-region.left, -region.top)
        }
        native.drawRenderNode(blurNode)
        native.restoreToCount(saveCount)
    }
}

@Composable
internal fun SliderMorphEditorContent(
    request: SliderMorphRequest,
    morphT: Float,
    outlineAlpha: Float = sstep(morphT, 0.72f, 0.98f),
    closing: Boolean,
    closeAnimated: (() -> Unit) -> Unit,
) {
    val req = request
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val dpPx = with(density) { 1.dp.toPx() }

    val initText = if (req.isInteger) req.initialValue.toInt().toString() else req.initialValue.toString()
    var text by remember(req.sessionId) { mutableStateOf(initText) }
    var isError by remember(req.sessionId) { mutableStateOf(false) }
    var isFieldFocused by remember(req.sessionId) { mutableStateOf(false) }

    fun parseCandidate(candidate: String): Float? =
        candidate.trim().replace(',', '.').toFloatOrNull()

    fun validate(candidate: String): Boolean {
        val num = parseCandidate(candidate)
        return num != null && num >= req.rangeStart && num <= req.rangeEnd
    }

    // Value the re-expanding track shows during the exit flight: the confirmed
    // draft after Apply (matches the updated row behind), the START value on
    // dismiss paths (the row behind is unchanged) — no snap at the end.
    var confirmedNum by remember(req.sessionId) { mutableStateOf<Float?>(null) }
    var exitSliderValue by remember(req.sessionId) { mutableStateOf<Float?>(null) }
    LaunchedEffect(closing) {
        if (closing && exitSliderValue == null) {
            exitSliderValue = confirmedNum ?: req.initialValue
        }
    }

    fun tryApply() {
        if (closing) return
        val num = parseCandidate(text)
        if (num != null && num >= req.rangeStart && num <= req.rangeEnd) {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            confirmedNum = num
            exitSliderValue = num
            closeAnimated { req.onConfirm(num) }
        } else {
            isError = true
        }
    }

    // Unified vertical slot expansion — top and bottom grow in sync without rocking.
    val expandW = sstep(morphT, 0.18f, 0.88f)
    val trackW = sstep(morphT, 0.08f, 0.54f)
    val fieldW = sstep(morphT, 0.22f, 0.86f)
    val flyW = sstep(morphT, 0.08f, 0.60f)
    val fieldTextW = sstep(morphT, 0.44f, 0.84f)
    val rangeW = sstep(morphT, 0.22f, 0.86f)
    val cancelW = sstep(morphT, 0.34f, 0.88f)
    val applyW = sstep(morphT, 0.34f, 0.92f)

    // Exact measured height of the source M3 Slider row at morphT = 0.
    // No fixed clamp: wide/short rows keep their own measured height.
    val initialRowHDp = remember(req.initialTrackHeightPx, density) {
        if (req.initialTrackHeightPx > 1f) {
            with(density) { req.initialTrackHeightPx.toDp() }
        } else {
            48.dp
        }
    }
    val slotHDp = lerp(initialRowHDp, 56.dp, expandW)
    val topGapSlotHDp = lerp(10.dp, 42.dp, expandW)
    val bottomRowHDp = lerp(32.dp, 38.dp, expandW)
    val applySlotHDp = lerp(0.dp, 62.dp, expandW)
    // Step buttons grow with the input field: 38.dp source size -> 56.dp field height.
    val stepBtnSize = lerp(38.dp, 56.dp, fieldW)
    val stepBtnCorner = lerp(12.dp, 16.dp, sstep(fieldW, 0.15f, 0.85f))

    val rangeBg = MaterialTheme.colorScheme.secondaryContainer
    val resetBg = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
    val fieldBg = MaterialTheme.colorScheme.surfaceContainerHighest
    val cancelBg = MaterialTheme.colorScheme.surfaceContainerHigh

    val draftNum = parseCandidate(text) ?: req.initialValue
    fun stepDraft(dir: Int) {
        if (closing) return
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        val stepped = (draftNum + dir * req.stepSize).coerceIn(req.rangeStart, req.rangeEnd)
        text = if (req.isInteger) stepped.toInt().toString() else stepped.toString()
        isError = !validate(text)
    }

    // Reset capsule visibility is purely draft-driven (no stale open-time snapshot):
    // with no known default it compares against the start value.
    val isDraftModified = if (req.defaultValue != null) {
        abs(draftNum - req.defaultValue) >= req.resetThreshold
    } else {
        abs(draftNum - req.initialValue) >= req.resetThreshold
    }
    val shouldShowReset = req.showDefaultButton && isDraftModified
    val resetReveal by animateFloatAsState(
        targetValue = if (shouldShowReset) 1f else 0f,
        animationSpec = spring(
            stiffness = 300f,
            dampingRatio = Spring.DampingRatioNoBouncy
        ),
        label = "ResetBadgeReveal"
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        // 1. Title stays Title.
        SliderTitleText(
            title = req.title,
            modifier = Modifier.fillMaxWidth()
        )

        // 2. Range capsule slot: left-aligned, reveals by unfolding (no popping from above).
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(topGapSlotHDp)
                .clipToBounds(),
            contentAlignment = Alignment.CenterStart
        ) {
            val rangeBorderColor = rangeBg.edgeOutline().let {
                it.copy(alpha = it.alpha * outlineAlpha)
            }
            Box(
                modifier = Modifier
                    .graphicsLayer {
                        alpha = sstep(rangeW, 0.02f, 0.32f)
                    }
                    .clip(CircleShape)
                    .background(rangeBg)
                    .border(1.dp, rangeBorderColor, CircleShape)
                    .unfoldPill(rangeW)
            ) {
                Text(
                    text = stringResource(
                        R.string.common_range_format,
                        req.rangeStart.toInt(),
                        req.rangeEnd.toInt()
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                )
            }
        }

        // 3. Middle row: [−] [Track compressing down -> Input Field expanding] [+]
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            ExpressiveStepButton(
                symbol = "−",
                enabled = !closing && draftNum > req.rangeStart,
                size = stepBtnSize,
                corner = stepBtnCorner,
                onClick = { stepDraft(-1) }
            )

            Spacer(Modifier.width(10.dp))

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(slotHDp),
                contentAlignment = Alignment.Center
            ) {
                // Input field metrics first: the collapsing track sinks toward the
                // growing field center using only measured slot/field heights.
                val fieldBoxH = lerp(14.dp, 56.dp, fieldW)
                val fieldInsetH = lerp(6.dp, 0.dp, fieldW)
                val collapseY = trackW *
                    (with(density) { slotHDp.toPx() - fieldBoxH.toPx() } / 2f).coerceAtLeast(0f)
                val collapseScaleY = (1f - 0.82f * trackW).coerceAtLeast(0.01f)
                val sliderColors = SliderDefaults.colors(
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTickColor = Color.Transparent,
                    inactiveTickColor = Color.Transparent,
                    disabledThumbColor = MaterialTheme.colorScheme.primary,
                    disabledActiveTrackColor = MaterialTheme.colorScheme.primary,
                    disabledInactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    disabledActiveTickColor = Color.Transparent,
                    disabledInactiveTickColor = Color.Transparent
                )

                Slider(
                    value = (exitSliderValue ?: draftNum).coerceIn(req.visualRangeStart, req.visualRangeEnd),
                    onValueChange = {},
                    valueRange = req.visualRangeStart..req.visualRangeEnd,
                    enabled = false,
                    colors = sliderColors,
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            translationY = collapseY
                            scaleY = collapseScaleY
                            alpha = 1f - trackW
                        },
                    thumb = {
                        Box(
                            modifier = Modifier
                                .width(8.dp)
                                .height(28.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                )

                // Field keeps growing out of the track width (inset 6.dp -> 0.dp).
                val fieldCorner = lerp(8.dp, 16.dp, sstep(fieldW, 0.15f, 0.85f))
                val fieldShape = RoundedCornerShape(fieldCorner)
                val baseFieldBorder = when {
                    isError -> MaterialTheme.colorScheme.error
                    isFieldFocused -> MaterialTheme.colorScheme.primary
                    else -> fieldBg.edgeOutline()
                }
                val fieldBorderAlpha = if (isError || isFieldFocused) 1f else outlineAlpha
                val fieldBorderColor = baseFieldBorder.copy(
                    alpha = baseFieldBorder.alpha * fieldBorderAlpha
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = fieldInsetH)
                        .height(fieldBoxH)
                        .graphicsLayer {
                            alpha = sstep(fieldW, 0.02f, 0.28f)
                        }
                        .clip(fieldShape)
                        .background(fieldBg)
                        .border(1.dp, fieldBorderColor, fieldShape)
                        .clipToBounds(),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .graphicsLayer {
                                alpha = fieldTextW
                            },
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.common_input_label),
                            style = MaterialTheme.typography.labelSmall,
                            color = when {
                                isError -> MaterialTheme.colorScheme.error
                                isFieldFocused -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            maxLines = 1
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            BasicTextField(
                                value = text,
                                onValueChange = {
                                    text = it
                                    isError = !validate(it)
                                },
                                readOnly = closing || morphT < 0.8f,
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = if (req.isInteger) {
                                        KeyboardType.Number
                                    } else {
                                        KeyboardType.Decimal
                                    },
                                    imeAction = ImeAction.Done
                                ),
                                keyboardActions = KeyboardActions(onDone = { tryApply() }),
                                textStyle = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                modifier = Modifier
                                    .weight(1f)
                                    .onFocusChanged { isFieldFocused = it.isFocused }
                            )

                            if (req.unit.isNotEmpty()) {
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = req.unit,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.width(10.dp))

            ExpressiveStepButton(
                symbol = "+",
                enabled = !closing && draftNum < req.rangeEnd,
                size = stepBtnSize,
                corner = stepBtnCorner,
                onClick = { stepDraft(1) }
            )
        }

        Spacer(Modifier.height(lerp(10.dp, 12.dp, expandW)))

        // 4. Bottom row: Reset capsule under −; readout flies into field; Cancel unfolds in its place.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(bottomRowHDp)
        ) {
            // Source row's (i): rides in at morphT = 0 for a 1:1 match, then slides
            // left + fades during enter and rides back on exit. The layout slot
            // collapses to zero once fully out so Cancel can dock right.
            if (req.showInfoIcon) {
                val infoOut = sstep(morphT, 0.05f, 0.50f)
                if (infoOut < 1f) {
                    val infoShiftPx = with(density) { 32.dp.toPx() }
                    Box(
                        modifier = Modifier
                            .width(lerp(32.dp, 0.dp, infoOut))
                            .height(32.dp)
                            .clipToBounds(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier
                                .size(20.dp)
                                .graphicsLayer {
                                    translationX = -infoShiftPx * infoOut
                                    alpha = 1f - sstep(infoOut, 0f, 0.7f)
                                }
                        )
                    }
                }
            }
            if (resetReveal > 0.001f) {
                Box(
                    modifier = Modifier
                        .graphicsLayer {
                            alpha = sstep(resetReveal, 0.02f, 0.35f)
                        }
                        .clip(CircleShape)
                        .background(resetBg)
                        .border(1.dp, resetBg.edgeOutline(), CircleShape)
                        .unfoldPill(resetReveal)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            enabled = !closing && shouldShowReset,
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                // Stay open: drop the default into the text field.
                                // Capsule visibility recomputes from the draft below.
                                val base = req.defaultValue ?: req.initialValue
                                text = if (req.isInteger) {
                                    base.toInt().toString()
                                } else {
                                    base.toString()
                                }
                                isError = !validate(text)
                            }
                        )
                ) {
                    // Morphs toward the Cancel button size across the flight
                    // instead of just fading in at the small capsule size.
                    val resetGrow = sstep(morphT, 0.30f, 0.90f)
                    Text(
                        text = req.resetText,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontSize = lerp(
                                MaterialTheme.typography.labelMedium.fontSize,
                                MaterialTheme.typography.labelLarge.fontSize,
                                resetGrow
                            ),
                            fontWeight = if (resetGrow > 0.5f) FontWeight.SemiBold else FontWeight.Medium
                        ),
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(
                            horizontal = lerp(12.dp, 16.dp, resetGrow),
                            vertical = lerp(5.dp, 7.dp, resetGrow)
                        )
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            Box(contentAlignment = Alignment.CenterEnd) {
                // Flying readout value: lands at the exact value position inside the input field.
                // Return text: after Apply fly the whole reverse with the CONFIRMED (new)
                // text so it docks 1:1 into the updated row (no end-of-flight pop);
                // on dismiss paths (confirmedNum == null) the start text returns as before.
                val exitReadoutText = remember(req.formattedValue, confirmedNum) {
                    val c = confirmedNum ?: return@remember req.formattedValue
                    formatExitReadout(req.formattedValue, c, req.isInteger, req.unit)
                }
                if (req.formattedValue.isNotEmpty()) {
                    Text(
                        text = exitReadoutText,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        textAlign = TextAlign.End,
                        modifier = Modifier.graphicsLayer {
                            translationX = req.valueFlyDxPx * flyW
                            translationY = -req.valueFlyDyPx * flyW
                            alpha = 1f - sstep(morphT, 0.30f, 0.56f)
                        }
                    )
                }

                // Cancel button unfolding in place of the readout.
                val cancelShape = RoundedCornerShape(14.dp)
                val cancelBorderColor = cancelBg.edgeOutline().let {
                    it.copy(alpha = it.alpha * outlineAlpha)
                }
                Box(
                    modifier = Modifier
                        .graphicsLayer {
                            alpha = sstep(cancelW, 0.05f, 0.45f)
                        }
                        .clip(cancelShape)
                        .background(cancelBg)
                        .border(1.dp, cancelBorderColor, cancelShape)
                        .unfoldPill(cancelW)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            enabled = !closing && morphT > 0.5f,
                            onClick = { closeAnimated(req.onDismiss) }
                        )
                        .padding(horizontal = 16.dp, vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.btn_cancel),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }

        // 5. Full-width Apply button below with continuous slot height and late 1.dp outline.
        val applyBorderColor = MaterialTheme.colorScheme.primary.edgeOutline().let {
            it.copy(alpha = it.alpha * outlineAlpha)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(applySlotHDp)
                .clipToBounds(),
            contentAlignment = Alignment.BottomCenter
        ) {
            Button(
                onClick = { tryApply() },
                enabled = !closing && morphT > 0.6f,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, applyBorderColor),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .graphicsLayer {
                        scaleY = 0.72f + 0.28f * applyW
                        alpha = sstep(applyW, 0.05f, 0.65f)
                        translationY = (1f - applyW) * 8f * dpPx
                    }
            ) {
                Text(
                    text = stringResource(R.string.btn_apply),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}