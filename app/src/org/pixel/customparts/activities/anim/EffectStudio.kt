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

// ── Effect Studio shared generator ──────────────────────────────────────
// Reusable UI & preview engine for Rotation Animation and Activity Transitions.

const val STUDIO_TAB_BUILTIN = 0
const val STUDIO_TAB_COLLECTED = 1
const val STUDIO_TAB_BUILDER = 2

enum class LoopDirectionMode {
    ALTERNATE,
    STEP1_ONLY,
    STEP2_ONLY
}

@Stable
data class EffectLoopSpec(
    val step1ExitRes: Int = 0,
    val step1EnterRes: Int = 0,
    val step2ExitRes: Int = step1ExitRes,
    val step2EnterRes: Int = step1EnterRes,
    val themePackage: String? = null,
    val step1ExitName: String = "custom_rotate_exit",
    val step1EnterName: String = "custom_rotate_enter",
    val step2ExitName: String = step1ExitName,
    val step2EnterName: String = step1EnterName,
    val directionMode: LoopDirectionMode = LoopDirectionMode.ALTERNATE
) {
    fun onlyStep1(): EffectLoopSpec = copy(directionMode = LoopDirectionMode.STEP1_ONLY)
    fun onlyStep2(): EffectLoopSpec = copy(directionMode = LoopDirectionMode.STEP2_ONLY)
    companion object {
        fun forRotationRes(exitAnimRes: Int, enterAnimRes: Int): EffectLoopSpec =
            EffectLoopSpec(
                step1ExitRes = exitAnimRes,
                step1EnterRes = enterAnimRes,
                step2ExitRes = exitAnimRes,
                step2EnterRes = enterAnimRes
            )

        fun forRotationTheme(packageName: String): EffectLoopSpec =
            EffectLoopSpec(
                themePackage = packageName,
                step1ExitName = "custom_rotate_exit",
                step1EnterName = "custom_rotate_enter",
                step2ExitName = "custom_rotate_exit",
                step2EnterName = "custom_rotate_enter"
            )

        fun forTransitionRes(
            openEnterRes: Int,
            openExitRes: Int,
            closeEnterRes: Int,
            closeExitRes: Int
        ): EffectLoopSpec = EffectLoopSpec(
            step1ExitRes = openExitRes,
            step1EnterRes = openEnterRes,
            step2ExitRes = closeExitRes,
            step2EnterRes = closeEnterRes
        )

        fun forTransitionTheme(packageName: String): EffectLoopSpec =
            EffectLoopSpec(
                themePackage = packageName,
                step1ExitName = "custom_open_exit",
                step1EnterName = "custom_open_enter",
                step2ExitName = "custom_close_exit",
                step2EnterName = "custom_close_enter"
            )
    }
}

sealed interface StudioCard {
    val key: String
    val title: String
    val subtitle: String?
    val badgeSubtitle: String?
    val source: Rect
    val sourceBoundsProvider: () -> Rect
    val loopSpec: EffectLoopSpec

    data class Builtin(
        override val key: String,
        override val title: String,
        override val subtitle: String? = null,
        override val badgeSubtitle: String? = null,
        override val source: Rect = Rect.Zero,
        override val sourceBoundsProvider: () -> Rect = { source },
        val styleId: Int,
        val exitAnim: Int,
        val enterAnim: Int,
        override val loopSpec: EffectLoopSpec = EffectLoopSpec.forRotationRes(exitAnim, enterAnim)
    ) : StudioCard

    data class Theme(
        override val key: String,
        override val title: String,
        override val subtitle: String?,
        override val badgeSubtitle: String? = null,
        override val source: Rect = Rect.Zero,
        override val sourceBoundsProvider: () -> Rect = { source },
        val packageName: String,
        val isTrashArmedProvider: () -> Boolean = { false },
        override val loopSpec: EffectLoopSpec = EffectLoopSpec.forRotationTheme(packageName)
    ) : StudioCard
}


@Composable
fun StudioTabs(
    titles: List<String>,
    position: Float,
    onTabClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    pagerState: StudioPagerState? = null
) {
    if (titles.isEmpty()) return
    val haptics = LocalHapticFeedback.current
    val clampedPos = position.coerceIn(0f, (titles.size - 1).toFloat())

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
    ) {
        val density = LocalDensity.current
        val textMeasurer = rememberTextMeasurer()
        val selectedStyle = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
        val idleStyle = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium)
        val segmentGapPx = with(density) { 6.dp.toPx() }
        val segmentSidePaddingPx = with(density) { 10.dp.toPx() * 2f }
        val selectedExtraPx = with(density) { 18.dp.toPx() }
        val availablePx = with(density) { maxWidth.toPx() }

        val allWeights = remember(
            textMeasurer, titles, selectedStyle, idleStyle,
            availablePx, segmentGapPx, segmentSidePaddingPx, selectedExtraPx
        ) {
            titles.indices.map { idx ->
                studioTabTargetWeights(
                    textMeasurer = textMeasurer,
                    options = titles,
                    selectedIndex = idx,
                    selectedStyle = selectedStyle,
                    idleStyle = idleStyle,
                    segmentGapPx = segmentGapPx,
                    segmentSidePaddingPx = segmentSidePaddingPx,
                    selectedExtraPx = selectedExtraPx,
                    availablePx = availablePx
                )
            }
        }

        val isDirectJump = pagerState != null && pagerState.isDirectJumpActive
        val jumpFrom = pagerState?.jumpFromPage ?: 0
        val jumpTo = pagerState?.jumpToPage ?: 0
        val jumpP = pagerState?.jumpFraction ?: 1f

        val lowIdx = floor(clampedPos).toInt().coerceIn(0, titles.lastIndex)
        val highIdx = (lowIdx + 1).coerceAtMost(titles.lastIndex)
        val pageFrac = (clampedPos - lowIdx).coerceIn(0f, 1f)

        // Subtle translucency so the frosted blur layer underneath blends through
        val selectedBg = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.68f)
        val idleBg = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.52f)
        val selectedContent = MaterialTheme.colorScheme.onPrimaryContainer
        val idleContent = MaterialTheme.colorScheme.onSurfaceVariant

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            titles.forEachIndexed { index, label ->
                val selFraction = if (isDirectJump) {
                    when (index) {
                        jumpFrom -> (1f - jumpP).coerceIn(0f, 1f)
                        jumpTo -> jumpP.coerceIn(0f, 1f)
                        else -> 0f
                    }
                } else {
                    (1f - abs(clampedPos - index)).coerceIn(0f, 1f)
                }

                val liveWeight = if (isDirectJump) {
                    val wFrom = allWeights.getOrNull(jumpFrom)?.getOrElse(index) { 1f } ?: 1f
                    val wTo = allWeights.getOrNull(jumpTo)?.getOrElse(index) { 1f } ?: 1f
                    lerpFloat(wFrom, wTo, jumpP).coerceAtLeast(0.05f)
                } else {
                    val wLow = allWeights.getOrNull(lowIdx)?.getOrElse(index) { 1f } ?: 1f
                    val wHigh = allWeights.getOrNull(highIdx)?.getOrElse(index) { 1f } ?: 1f
                    lerpFloat(wLow, wHigh, pageFrac).coerceAtLeast(0.05f)
                }

                val cornerRadius = lerp(12.dp, 28.dp, selFraction)
                val bgColor = lerpColor(idleBg, selectedBg, selFraction)
                val contentColor = lerpColor(idleContent, selectedContent, selFraction)
                val shape = RoundedCornerShape(cornerRadius)
                val isMostlySelected = selFraction >= 0.5f

                Row(
                    modifier = Modifier
                        .weight(liveWeight)
                        .height(46.dp)
                        .clip(shape)
                        .background(bgColor)
                        .border(1.dp, bgColor.edgeOutline(alpha = 0.38f), shape)
                        .clickable {
                            haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                            onTabClick(index)
                        }
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (isMostlySelected) FontWeight.Bold else FontWeight.Medium,
                        color = contentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

// ── Live Horizontal Swipe Pager (Launcher Home Screen Mechanics) ─────────

@Stable
class StudioPagerState(val pageCount: Int) {
    var anchor by mutableIntStateOf(0)
    var dragPx by mutableFloatStateOf(0f)
    var pageWidthPx by mutableFloatStateOf(1f)
    internal val settleAnim = Animatable(0f)

    // Direct jump tracking so switching 0 <-> 2 bypasses tab 1's intermediate morph
    var isDirectJumpActive by mutableStateOf(false)
        internal set
    var jumpFromPage by mutableIntStateOf(0)
        internal set
    var jumpToPage by mutableIntStateOf(0)
        internal set
    var jumpFraction by mutableFloatStateOf(1f)
        internal set

    val position: Float
        get() = (anchor - dragPx / pageWidthPx.coerceAtLeast(1f))
            .coerceIn(0f, (pageCount - 1).coerceAtLeast(0).toFloat())

    suspend fun animateScrollToPage(page: Int, isDirectTap: Boolean = true) {
        val target = page.coerceIn(0, pageCount - 1)
        val width = pageWidthPx.coerceAtLeast(1f)
        val startPos = position
        val startNearest = startPos.roundToInt().coerceIn(0, pageCount - 1)
        val multiPageJump = isDirectTap && abs(target - startNearest) > 1

        if (multiPageJump) {
            jumpFromPage = startNearest
            jumpToPage = target
            jumpFraction = 0f
            isDirectJumpActive = true
        } else {
            isDirectJumpActive = false
        }

        anchor = target
        val initialDrag = (target - startPos) * width
        dragPx = initialDrag
        settleAnim.stop()
        settleAnim.snapTo(initialDrag)
        try {
            settleAnim.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    stiffness = Spring.StiffnessMediumLow,
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    visibilityThreshold = 0.5f
                )
            ) {
                dragPx = value
                if (multiPageJump && abs(initialDrag) > 1f) {
                    jumpFraction = (1f - (value / initialDrag)).coerceIn(0f, 1f)
                }
            }
        } finally {
            dragPx = 0f
            jumpFraction = 1f
            isDirectJumpActive = false
        }
    }
}

@Composable
fun rememberStudioPagerState(pageCount: Int): StudioPagerState {
    return remember(pageCount) { StudioPagerState(pageCount) }
}

@Composable
fun EffectStudioPager(
    state: StudioPagerState,
    modifier: Modifier = Modifier,
    userScrollEnabled: Boolean = true,
    onHorizontalDragDetected: (() -> Unit)? = null,
    pageContent: @Composable (Int) -> Unit
) {
    val scope = rememberCoroutineScope()
    val pageCount = state.pageCount

    val flingBehavior = remember(state, pageCount) {
        object : FlingBehavior {
            override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
                val cur = state.position
                val target = when {
                    initialVelocity < -350f -> floor(cur).toInt() + 1
                    initialVelocity > 350f -> floor(cur).toInt()
                    else -> cur.roundToInt()
                }.coerceIn(0, pageCount - 1)
                scope.launch {
                    state.animateScrollToPage(target, isDirectTap = false)
                }
                return initialVelocity
            }
        }
    }

    val scrollableState = rememberScrollableState { delta ->
        state.isDirectJumpActive = false
        if (abs(delta) > 1.5f) {
            onHorizontalDragDetected?.invoke()
        }
        val width = state.pageWidthPx.coerceAtLeast(1f)
        val nextPos = (state.anchor - (state.dragPx + delta) / width)
            .coerceIn(0f, (pageCount - 1).toFloat())
        val nextDrag = (state.anchor - nextPos) * width
        val consumed = nextDrag - state.dragPx
        state.dragPx = nextDrag
        consumed
    }

    Layout(
        content = {
            for (page in 0 until pageCount) {
                Box(modifier = Modifier.fillMaxSize()) {
                    pageContent(page)
                }
            }
        },
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .scrollable(
                orientation = Orientation.Horizontal,
                state = scrollableState,
                enabled = userScrollEnabled,
                flingBehavior = flingBehavior
            )
    ) { measurables, constraints ->
        val w = constraints.maxWidth.coerceAtLeast(1)
        val h = constraints.maxHeight.coerceAtLeast(1)
        state.pageWidthPx = w.toFloat()
        val pageConstraints = constraints.copy(
            minWidth = w,
            maxWidth = w,
            minHeight = h,
            maxHeight = h
        )
        val placeables = measurables.map { it.measure(pageConstraints) }
        val pos = state.position
        layout(w, h) {
            placeables.forEachIndexed { page, placeable ->
                val xOffset = ((page - pos) * w).roundToInt()
                placeable.placeRelative(xOffset, 0)
            }
        }
    }
}

// ── Loop Effect Card (Open Grid Card) ───────────────────────────────────

@Composable
internal fun StudioPackageNameItem(
    packageName: String,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 12.dp,
    isPlaceholder: Boolean = false
) {
    val pillShape = RoundedCornerShape(10.dp)
    val pillBg = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = horizontalPadding, end = horizontalPadding, top = 2.dp, bottom = 6.dp)
            .alpha(if (isPlaceholder) 0f else 1f)
            .clip(pillShape)
            .background(pillBg)
            .border(1.dp, pillBg.edgeOutline(alpha = 0.35f), pillShape)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = packageName.ifEmpty { "package" },
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun LoopEffectCard(
    title: String,
    subtitle: String? = null,
    packageName: String? = null,
    isActive: Boolean,
    isHiddenForFlight: Boolean = false,
    onExpand: (Rect, () -> Rect) -> Unit,
    applyLabel: String,
    activeLabel: String,
    onApply: () -> Unit,
    showTrash: Boolean = false,
    trashArmed: Boolean = false,
    onTrash: (() -> Unit)? = null,
    preview: @Composable () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    var cardBounds by remember { mutableStateOf(Rect.Zero) }
    val cardContainerColor = if (isActive) {
        MaterialTheme.colorScheme.surfaceContainerHigh
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = cardContainerColor),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, cardContainerColor.edgeOutline()),
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
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 1. Header: Fixed 2-line height via invisible sizer (never jumps and never clips)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !isHiddenForFlight) {
                        haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                        onExpand(cardBounds) { cardBounds }
                    }
                    .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart
                ) {
                    // Невидимый эталон на 2 строки — держит строго фиксированную высоту шапки
                    Column(
                        modifier = Modifier.alpha(0f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "Ag",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
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
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (!subtitle.isNullOrEmpty()) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }

            // 2. Center: Looped Animation Preview
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(206.dp)
                    .clickable(enabled = !isHiddenForFlight) {
                        haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                        onExpand(cardBounds) { cardBounds }
                    },
                contentAlignment = Alignment.Center
            ) {
                preview()
            }

            // 3. Separate Package Name item (before Apply & Delete buttons)
            if (!packageName.isNullOrEmpty()) {
                StudioPackageNameItem(
                    packageName = packageName,
                    horizontalPadding = 12.dp,
                    modifier = Modifier.clickable(enabled = !isHiddenForFlight) {
                        haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                        onExpand(cardBounds) { cardBounds }
                    }
                )
            }

            // 4. Bottom Action Row: Tab-styled Apply button + optional Red Trash button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, top = 2.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StudioTabStyledApplyButton(
                    isActive = isActive,
                    applyLabel = applyLabel,
                    activeLabel = activeLabel,
                    enabled = !isHiddenForFlight && !isActive,
                    onClick = onApply,
                    modifier = Modifier.weight(1f)
                )

                if (showTrash && onTrash != null) {
                    val trashCorner by animateFloatAsState(
                        targetValue = if (trashArmed) 28f else 12f,
                        animationSpec = spring(
                            stiffness = Spring.StiffnessMedium,
                            dampingRatio = Spring.DampingRatioNoBouncy
                        ),
                        label = "TrashCorner"
                    )
                    val trashBg = MaterialTheme.colorScheme.errorContainer
                    val trashContent = MaterialTheme.colorScheme.onErrorContainer
                    val trashShape = RoundedCornerShape(trashCorner.dp)

                    Box(
                        modifier = Modifier
                            .size(width = 46.dp, height = 46.dp)
                            .clip(trashShape)
                            .background(trashBg)
                            .border(1.dp, trashBg.edgeOutline(), trashShape)
                            .clickable(enabled = !isHiddenForFlight) {
                                haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                                onTrash()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (trashArmed) Icons.Rounded.Check else Icons.Rounded.Delete,
                            contentDescription = null,
                            tint = trashContent,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}


@Composable
fun StudioTabStyledApplyButton(
    isActive: Boolean,
    applyLabel: String,
    activeLabel: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    val selFraction by animateFloatAsState(
        targetValue = if (isActive) 1f else 0f,
        animationSpec = spring(
            stiffness = Spring.StiffnessMedium,
            dampingRatio = Spring.DampingRatioNoBouncy
        ),
        label = "ApplyTabMorph"
    )
    val cornerRadius = lerp(12.dp, 28.dp, selFraction)
    val shape = RoundedCornerShape(cornerRadius)
    val idleBg = MaterialTheme.colorScheme.surfaceContainerHighest
    val activeBg = MaterialTheme.colorScheme.primaryContainer
    val bgColor = lerpColor(idleBg, activeBg, selFraction)
    val contentColor = lerpColor(
        MaterialTheme.colorScheme.onSurfaceVariant,
        MaterialTheme.colorScheme.onPrimaryContainer,
        selFraction
    )

    Row(
        modifier = modifier
            .height(46.dp)
            .clip(shape)
            .background(bgColor)
            .border(1.dp, bgColor.edgeOutline(), shape)
            .clickable(enabled = enabled) {
                haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                onClick()
            }
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        AnimatedContent(
            targetState = if (isActive) activeLabel else applyLabel,
            transitionSpec = {
                (fadeIn(tween(180)) + scaleIn(initialScale = 0.92f, animationSpec = tween(180))) togetherWith
                    (fadeOut(tween(130)) + scaleOut(targetScale = 0.92f, animationSpec = tween(130))) using
                    SizeTransform(clip = false)
            },
            label = "ApplyLabelMorph"
        ) { targetText ->
            Text(
                text = targetText,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selFraction >= 0.5f) FontWeight.Bold else FontWeight.Medium,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

// ── Add Custom Theme Card (+ Card on Collected Tab) ─────────────────────

@Composable
fun StudioAddCard(onClick: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val bgColor = MaterialTheme.colorScheme.surfaceContainerLow
    val shape = RoundedCornerShape(20.dp)

    Card(
        colors = CardDefaults.cardColors(containerColor = bgColor),
        shape = shape,
        border = BorderStroke(1.dp, bgColor.edgeOutline()),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                    onClick()
                }
        ) {
            // 1. Header placeholder matching LoopEffectCard 2-line header 1:1
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .alpha(0f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "Ag",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                    Text(
                        text = "Ag",
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1
                    )
                }
                Spacer(Modifier.width(4.dp))
                Spacer(Modifier.size(22.dp))
            }

            // 2. Preview-height stage with the centered add affordance
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(206.dp)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(28.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.primaryContainer.edgeOutline(),
                                RoundedCornerShape(28.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Text(
                        text = stringResource(R.string.rotation_anim_add_custom),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // 3. Package name item placeholder so + card matches Theme cards height 1:1
            StudioPackageNameItem(
                packageName = "package",
                horizontalPadding = 12.dp,
                isPlaceholder = true
            )

            // 4. Action placeholder mirroring the 46.dp apply row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, top = 2.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                )
            }
        }
    }
}


// ── Morphing Name Input + Check Button (For Import & Builder Preview) ────

@Composable
fun MorphingNameCompileBar(
    buttonLabel: String,
    placeholderLabel: String,
    enabled: Boolean,
    onCompile: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }

    var inputMode by remember { mutableStateOf(false) }
    var themeName by remember { mutableStateOf("") }
    var fieldFocused by remember { mutableStateOf(false) }

    LaunchedEffect(enabled) {
        if (!enabled && inputMode) {
            inputMode = false
        }
    }

    val morphP by animateFloatAsState(
        targetValue = if (inputMode) 1f else 0f,
        animationSpec = spring(
            stiffness = Spring.StiffnessMediumLow,
            dampingRatio = Spring.DampingRatioNoBouncy
        ),
        label = "MorphCompileBtn"
    )

    LaunchedEffect(inputMode) {
        if (inputMode) {
            kotlinx.coroutines.delay(90)
            runCatching { focusRequester.requestFocus() }
            keyboardController?.show()
        } else {
            focusManager.clearFocus()
            keyboardController?.hide()
        }
    }

    fun submitIfValid() {
        val clean = filterThemeNameInput(themeName).trim()
        if (clean.isNotBlank() && enabled) {
            haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
            focusManager.clearFocus()
            keyboardController?.hide()
            inputMode = false
            onCompile(clean)
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
    ) {
        val totalW = maxWidth
        val btnH = 46.dp
        val gap = 8.dp
        val fieldTargetW = (totalW - btnH - gap).coerceAtLeast(0.dp)
        val fieldW = lerp(0.dp, fieldTargetW, sstepE(morphP, 0.08f, 0.96f))
        val btnW = lerp(totalW, btnH, sstepE(morphP, 0f, 0.88f))

        // Left unfolding theme name field
        if (morphP > 0.01f) {
            val fieldBg = MaterialTheme.colorScheme.surfaceContainerHighest
            val fieldShape = RoundedCornerShape(24.dp)
            val borderColor = if (fieldFocused) {
                MaterialTheme.colorScheme.primary
            } else {
                fieldBg.edgeOutline()
            }

            Box(
                modifier = Modifier
                    .width(fieldW)
                    .height(btnH)
                    .align(Alignment.CenterStart)
                    .graphicsLayer {
                        alpha = sstepE(morphP, 0.12f, 0.65f)
                    }
                    .clip(fieldShape)
                    .background(fieldBg)
                    .border(1.dp, borderColor, fieldShape),
                contentAlignment = Alignment.CenterStart
            ) {
            Row(
                modifier = Modifier
                    .requiredWidth(fieldTargetW)
                    .height(btnH)
                    .padding(start = 16.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (themeName.isEmpty()) {
                        Text(
                            text = placeholderLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    BasicTextField(
                        value = themeName,
                        onValueChange = { themeName = filterThemeNameInput(it) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Ascii,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = { submitIfValid() }),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                            .onFocusChanged { fieldFocused = it.isFocused }
                    )
                }
                IconButton(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                        inputMode = false
                    },
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            }
        }

        // Right morphing button: Full-width text button -> 46.dp Check icon capsule
        val canAct = if (inputMode) {
            enabled && themeName.trim().isNotBlank()
        } else {
            enabled
        }
        val btnBg = MaterialTheme.colorScheme.primaryContainer
        val btnContent = MaterialTheme.colorScheme.onPrimaryContainer
        val btnShape = RoundedCornerShape(28.dp)
        val textAlpha = 1f - sstepE(morphP, 0f, 0.38f)
        val checkAlpha = sstepE(morphP, 0.35f, 0.88f)

        Box(
            modifier = Modifier
                .width(btnW)
                .height(btnH)
                .align(Alignment.CenterEnd)
                .alpha(if (canAct) 1f else 0.42f)
                .clip(btnShape)
                .background(btnBg)
                .border(1.dp, btnBg.edgeOutline(), btnShape)
                .clickable(enabled = canAct) {
                    haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                    if (!inputMode) {
                        inputMode = true
                    } else {
                        submitIfValid()
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            if (textAlpha > 0.01f) {
                Row(
                    modifier = Modifier
                        .alpha(textAlpha)
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Build,
                        contentDescription = null,
                        tint = btnContent,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = buttonLabel,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = btnContent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (checkAlpha > 0.01f) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = btnContent,
                    modifier = Modifier
                        .size(22.dp)
                        .alpha(checkAlpha)
                )
            }
        }
    }
}

// ── Refreshed Import XML Card & Constructor Card (Screen 3) ─────────────

@Composable
fun StudioXmlFileChipRow(
    label: String,
    uri: android.net.Uri?,
    required: Boolean,
    onClick: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    val selected = uri != null
    val bgColor = if (selected) {
        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val shape = RoundedCornerShape(16.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(bgColor)
            .border(1.dp, bgColor.edgeOutline(), shape)
            .clickable {
                haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                onClick()
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(
                    if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceContainerLow
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (selected) Icons.Rounded.Check else Icons.Rounded.FileOpen,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (required) {
                    Text(
                        text = " *",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            Text(
                text = uri?.lastPathSegment ?: uri?.toString()
                    ?: stringResource(R.string.anim_theme_tap_to_select),
                style = MaterialTheme.typography.bodySmall,
                color = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun StudioTransitionImportCard(
    openEnterUri: android.net.Uri?,
    openExitUri: android.net.Uri?,
    closeEnterUri: android.net.Uri?,
    closeExitUri: android.net.Uri?,
    isCompiling: Boolean,
    compileError: String?,
    onPickOpenEnter: () -> Unit,
    onPickOpenExit: () -> Unit,
    onPickCloseEnter: () -> Unit,
    onPickCloseExit: () -> Unit,
    onCompile: (String) -> Unit
) {
    val requiredFilesSelected = openEnterUri != null && openExitUri != null
    SettingsGroupCard(title = stringResource(R.string.rotation_anim_import_title)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StudioXmlFileChipRow(
                label = stringResource(R.string.anim_theme_open_enter),
                uri = openEnterUri,
                required = true,
                onClick = onPickOpenEnter
            )
            StudioXmlFileChipRow(
                label = stringResource(R.string.anim_theme_open_exit),
                uri = openExitUri,
                required = true,
                onClick = onPickOpenExit
            )
            StudioXmlFileChipRow(
                label = stringResource(R.string.anim_theme_close_enter),
                uri = closeEnterUri,
                required = false,
                onClick = onPickCloseEnter
            )
            StudioXmlFileChipRow(
                label = stringResource(R.string.anim_theme_close_exit),
                uri = closeExitUri,
                required = false,
                onClick = onPickCloseExit
            )
            Spacer(Modifier.height(2.dp))
            MorphingNameCompileBar(
                buttonLabel = stringResource(R.string.rotation_anim_compile_btn),
                placeholderLabel = stringResource(R.string.rotation_anim_style_name),
                enabled = requiredFilesSelected && !isCompiling,
                onCompile = onCompile
            )
            if (!compileError.isNullOrBlank()) {
                Text(
                    text = compileError,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}
@Composable
fun StudioImportCard(
    exitUri: android.net.Uri?,
    enterUri: android.net.Uri?,
    isCompiling: Boolean,
    compileError: String?,
    onPickExit: () -> Unit,
    onPickEnter: () -> Unit,
    onCompile: (String) -> Unit
) {
    val bothFilesSelected = exitUri != null && enterUri != null
    SettingsGroupCard(title = stringResource(R.string.rotation_anim_import_title)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StudioXmlFileChipRow(
                label = stringResource(R.string.rotation_anim_exit_file),
                uri = exitUri,
                required = true,
                onClick = onPickExit
            )
            StudioXmlFileChipRow(
                label = stringResource(R.string.rotation_anim_enter_file),
                uri = enterUri,
                required = true,
                onClick = onPickEnter
            )
            Spacer(Modifier.height(2.dp))
            MorphingNameCompileBar(
                buttonLabel = stringResource(R.string.rotation_anim_compile_btn),
                placeholderLabel = stringResource(R.string.rotation_anim_style_name),
                enabled = bothFilesSelected && !isCompiling,
                onCompile = onCompile
            )
            if (!compileError.isNullOrBlank()) {
                Text(
                    text = compileError,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
fun StudioCompileLogCard(compileLog: List<String>) {
    if (compileLog.isEmpty()) return
    SettingsGroupCard(title = stringResource(R.string.rotation_anim_log_title)) {
        val logBg = MaterialTheme.colorScheme.surfaceContainerHighest
        Card(
            colors = CardDefaults.cardColors(containerColor = logBg),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, logBg.edgeOutline()),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 220.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp)
            ) {
                compileLog.forEach { line ->
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        color = when {
                            line.startsWith("ERROR") || line.startsWith("✗") ->
                                MaterialTheme.colorScheme.error
                            line.startsWith("✓") ->
                                MaterialTheme.colorScheme.primary
                            else ->
                                MaterialTheme.colorScheme.onSurface
                        }
                    )
                }
            }
        }
    }
}

// ── Studio Grid Helper ──────────────────────────────────────────────────

@Composable
fun StudioGrid(
    gridState: LazyGridState = rememberLazyGridState(),
    topPadding: Dp = 12.dp,
    content: LazyGridScope.() -> Unit
) {
    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = topPadding,
            bottom = 16.dp + REBOOT_BUBBLE_CONTENT_BOTTOM_PADDING
        ),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content
    )
}