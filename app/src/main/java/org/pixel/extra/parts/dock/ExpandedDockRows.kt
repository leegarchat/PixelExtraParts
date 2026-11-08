package org.pixel.extra.parts.dock

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.zIndex
import kotlin.math.abs
import kotlin.math.roundToInt
import org.pixel.extra.parts.motion.MotionSettings

// Absolute overflow rows for the expand animation and editor mode. The
// settled open state uses the unified scroll list instead, so the caller
// skips these duplicates there.
@Composable
internal fun ExpandedOverflowRows(
    chrome: DockChrome,
    density: Density,
    motion: MotionSettings,
    effOverflow: List<DockEntry>,
    selectedKey: String,
    rowsEnabled: Boolean,
    editorMode: Boolean,
    drag: DockDragSnapshot,
    onSettleDone: () -> Unit,
    jigglePhase: Float,
    wobbleStrength: Float,
    rowWidthPx: Float,
    reportRowBounds: (String, Rect) -> Unit,
    onPickEntry: (String) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    with(density) {
        val pillHPx = chrome.pillHPx
        val rowGapPx = chrome.rowGapPx
        val expandedBarHPx = chrome.expandedBarHPx
        effOverflow.forEachIndexed { idx, item ->
            // Stable identity per entry: rows are passive visuals, the
            // list-level detector owns the gesture, so key() only
            // preserves the lifted/follow treatment mid-drag.
            key(item.key) {
                val fromBottom = effOverflow.size - 1 - idx
                val itemBottomFromBottomPx =
                    chrome.overflowBottomFromBottomPx + fromBottom * (pillHPx + rowGapPx)
                val itemMidFromBottomPx = itemBottomFromBottomPx + pillHPx * 0.5f
                val itemTopFromBottomPx = itemBottomFromBottomPx + pillHPx

                val revealStartH = itemMidFromBottomPx
                val revealEndH = minOf(
                    itemTopFromBottomPx + pillHPx * motion.revealOvershoot,
                    expandedBarHPx,
                )
                val revealProgress = sstep(chrome.barHPx, revealStartH, revealEndH)

                if (revealProgress > 0.001f) {
                    val rowTopY = expandedBarHPx - itemTopFromBottomPx
                    // Neighbor glide toward the current preview slot. This state
                    // lives inside the stable key(item.key) above, so hover
                    // crossings animate instead of teleporting. The held row
                    // bypasses it and follows the finger directly.
                    val glideTopY by animateFloatAsState(
                        targetValue = rowTopY,
                        animationSpec = spring(stiffness = 380f, dampingRatio = 0.85f),
                        label = "RowGlide",
                    )
                    val slideUpOffsetPx = (1f - revealProgress) * motion.riseOffsetDp.dp.toPx()

                    val slot = idx
                    val isDragged = editorMode && drag.dragKey == item.key
                    val isSettling = editorMode && drag.settleKey == item.key
                    // Neighbors already sit in the preview arrangement
                    // (eff lists equal the commit result), so no extra shift.
                    val shiftTarget = 0f
                    // Settle residual: starts at the release offset, springs to the
                    // committed-slot delta. Scoped to the stable key(item.key).
                    // Folded into the layout offset below (not the graphics
                    // layer) so onPlaced bounds track the animation frame by
                    // frame and a re-grab mid-settle reads the on-screen top.
                    var settleExtraYPx = 0f
                    if (isSettling) {
                        val settleAnim = remember(drag.settleToken) {
                            Animatable(drag.settleStartDeltaPx)
                        }
                        LaunchedEffect(drag.settleToken) {
                            settleAnim.animateTo(
                                drag.settleTargetDeltaPx,
                                animationSpec = spring(stiffness = 380f, dampingRatio = 0.85f),
                            )
                            onSettleDone()
                        }
                        settleExtraYPx = settleAnim.value
                    }

                    // The held row lives in screen space: it follows the finger and
                    // is clamped to the visible viewport so it never disappears under
                    // the bar clip. The list scrolls underneath it via auto-scroll.
                    val draggedTopY = (drag.dragStartTopPx + drag.dragOffsetYPx)
                        .coerceIn(
                            chrome.expandedPadTopPx,
                            (chrome.expandedBarHPx - chrome.footerHPx - pillHPx)
                                .coerceAtLeast(chrome.expandedPadTopPx),
                        )
                    val rowTopNow = when {
                        isDragged -> draggedTopY
                        isSettling -> drag.settleBaseTopPx + settleExtraYPx
                        else -> glideTopY + slideUpOffsetPx + chrome.editorScrollPx
                    }

                    OverflowEntryRow(
                        entry = item,
                        motion = motion,
                        selected = item.key == selectedKey,
                        revealProgress = revealProgress,
                        enabled = rowsEnabled,
                        editorMode = editorMode,
                        isDragged = isDragged,
                        jigglePhase = jigglePhase + slot * 1.15f,
                        wobbleStrength = wobbleStrength,
                        shiftTargetPx = shiftTarget,
                        // Residual already folded into rowTopNow above.
                        followOffsetYPx = null,
                        onPick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onPickEntry(item.key)
                        },
                        modifier = Modifier
                            .zIndex(if (isDragged) 10f else 0f)
                            .offset {
                                IntOffset(
                                    x = chrome.expandedPadHPx.roundToInt(),
                                    y = rowTopNow.roundToInt(),
                                )
                            }
                            .then(
                                if (editorMode && !isDragged && !isSettling) {
                                    Modifier.clipToVerticalBand(
                                        topPx = chrome.expandedPadTopPx,
                                        bottomPx = chrome.expandedBarHPx - chrome.footerHPx,
                                        itemTopPx = rowTopNow,
                                    )
                                } else {
                                    Modifier
                                },
                            )
                            .width(rowWidthPx.toDp())
                            .height(PillH)
                            .onPlaced { coords ->
                                reportRowBounds(item.key, coords.boundsInParent())
                            },
                    )
                }
            }
        }
    }
}

// Absolute pinned-tab flight rows for the expand animation and editor mode;
// the settled open state uses the unified scroll list above.
@Composable
internal fun PinnedFlightRows(
    chrome: DockChrome,
    density: Density,
    motion: MotionSettings,
    effTabs: List<DockEntry>,
    tabLabels: List<String>,
    tabSelectedFractions: List<Float>,
    collapsedTabWidthsPx: List<Float>,
    collapsedSlotRanges: Map<String, ClosedFloatingPointRange<Float>>,
    collapsedBarLeftScreenX: Float,
    barLeftScreenXPx: Float,
    maxCollapsedPillWPx: Float,
    overCount: Int,
    tabCount: Int,
    selectedKey: String,
    pressedKey: String?,
    isDockPressed: Boolean,
    expanded: Boolean,
    editorMode: Boolean,
    drag: DockDragSnapshot,
    onSettleDone: () -> Unit,
    jigglePhase: Float,
    wobbleStrength: Float,
    reportRowBounds: (String, Rect) -> Unit,
    onPickEntry: (String) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    with(density) {
        val pillHPx = chrome.pillHPx
        val rowGapPx = chrome.rowGapPx
        val barPadPx = chrome.barPadPx
        val expandedBarHPx = chrome.expandedBarHPx
        val p = chrome.progress
        effTabs.forEachIndexed { tabIndex, tab ->
            // Stable identity per entry: passive visual, same as overflow rows.
            key(tab.key) {
                val gapBefore = (0 until tabIndex).sumOf { k ->
                    motion.flightGaps.getOrElse(k) {
                        motion.flightGaps.lastOrNull() ?: 0.14f
                    }.toDouble()
                }.toFloat()
                val startWindow = gapBefore.coerceAtMost(0.5f)
                val durationWindow = (1f - startWindow).coerceAtMost(motion.flightSpan)
                val rawFlight = ((p - startWindow) / durationWindow).coerceIn(0f, 1f)
                val flightT = FastOutSlowInEasing.transform(rawFlight)

                val slotRange = collapsedSlotRanges[tab.key]
                val startLocalX = slotRange?.start ?: barPadPx
                val startScreenX = collapsedBarLeftScreenX + startLocalX
                val startTopY = expandedBarHPx - barPadPx - pillHPx

                val stepsAboveDivider = (tabCount - 1 - tabIndex).coerceAtLeast(0)
                val targetTopY = expandedBarHPx -
                    chrome.footerHPx -
                    pillHPx -
                    stepsAboveDivider * (pillHPx + rowGapPx)
                val targetScreenX = chrome.expandedPadHPx

                val arcBulgePx = (motion.arcBulgeBaseDp.dp + motion.arcBulgeStepDp.dp * stepsAboveDivider).toPx()
                val p0 = Offset(startScreenX, startTopY)
                val p2 = Offset(targetScreenX, targetTopY)
                val p1 = Offset(
                    x = (minOf(startScreenX, targetScreenX) - arcBulgePx)
                        .coerceAtLeast(barLeftScreenXPx + barPadPx),
                    y = lerp(startTopY, targetTopY, 0.20f),
                )

                val curScreenPos = bez(flightT, p0, p1, p2)
                val curLocalX = (curScreenPos.x - barLeftScreenXPx)
                    .coerceIn(barPadPx, (chrome.barWPx - barPadPx - chrome.unselectedPillWPx).coerceAtLeast(barPadPx))
                val curTopY = curScreenPos.y

                val w0 = collapsedTabWidthsPx[tabIndex]
                val widthMorph = sstep(rawFlight, motion.widthMorphStart, 1.0f)
                val desiredW = lerp(w0, chrome.expandedRowWPx, widthMorph)
                val maxAvailableW = (chrome.barWPx - curLocalX - lerp(barPadPx, chrome.expandedPadHPx, flightT))
                    .coerceAtLeast(chrome.unselectedPillWPx)
                val curW = minOf(desiredW, maxAvailableW)
                // Visual cap matching the analytic clamp above; eases to full
                // row width as the pill morphs to the expanded list.
                val pillWidthCapNowPx = lerp(maxCollapsedPillWPx, chrome.expandedRowWPx, widthMorph)

                val selFrac = tabSelectedFractions[tabIndex]
                val labelReveal = lerp(selFrac, 1f, sstep(rawFlight, motion.labelRevealStart, motion.labelRevealEnd))

                val slot = overCount + tabIndex
                val isDragged = editorMode && drag.dragKey == tab.key
                val isSettling = editorMode && drag.settleKey == tab.key
                // See overflow section: preview lists already match the commit layout.
                val shiftTarget = 0f
                // Neighbor glide toward the current preview slot. This state lives
                // inside the stable key(tab.key) above, so hover crossings animate
                // instead of teleporting. While the expand flight runs the bezier
                // owns Y; once settled the spring owns it. The held row bypasses
                // it and follows the finger directly. It must stay ABOVE the
                // conditional settle block: otherwise toggling isSettling reuses
                // this slot with the wrong type and crashes in animateValueAsState.
                val glidePinnedY by animateFloatAsState(
                    targetValue = targetTopY,
                    animationSpec = spring(stiffness = 380f, dampingRatio = 0.85f),
                    label = "PinnedGlide",
                )
                // Same settle residual as overflow rows: release offset springs to
                // the committed-slot delta. Folded into the layout offset below
                // so onPlaced bounds track the animation and re-grab reads the
                // on-screen top. Scoped to key(tab.key).
                var settleExtraYPx = 0f
                if (isSettling) {
                    val settleAnim = remember(drag.settleToken) {
                        Animatable(drag.settleStartDeltaPx)
                    }
                    LaunchedEffect(drag.settleToken) {
                        settleAnim.animateTo(
                            drag.settleTargetDeltaPx,
                            animationSpec = spring(stiffness = 380f, dampingRatio = 0.85f),
                        )
                        onSettleDone()
                    }
                    settleExtraYPx = settleAnim.value
                }
                val settledTopY = if (p > 0.999f) glidePinnedY else curTopY
                // Same screen-space rule as overflow rows: the held row follows the
                // finger and stays clamped inside the visible viewport.
                val draggedTopY = (drag.dragStartTopPx + drag.dragOffsetYPx)
                    .coerceIn(
                        chrome.expandedPadTopPx,
                        (chrome.expandedBarHPx - chrome.footerHPx - pillHPx)
                            .coerceAtLeast(chrome.expandedPadTopPx),
                    )
                val rowTopNow = when {
                    isDragged -> draggedTopY
                    isSettling -> drag.settleBaseTopPx + settleExtraYPx
                    else -> settledTopY + chrome.editorScrollPx
                }

                UnifiedDockTabItem(
                    entry = tab,
                    motion = motion,
                    label = tabLabels[tabIndex],
                    selected = tab.key == selectedKey,
                    isFingerHere = pressedKey == tab.key && isDockPressed && !expanded,
                    dockPressed = isDockPressed && !expanded,
                    flightProgress = flightT,
                    labelReveal = labelReveal,
                    expandedClickable = p > 0.55f,
                    editorMode = editorMode,
                    isDragged = isDragged,
                    jigglePhase = jigglePhase + slot * 1.15f,
                    wobbleStrength = wobbleStrength,
                    shiftTargetPx = shiftTarget,
                    // Residual already folded into rowTopNow above.
                    followOffsetYPx = null,
                    onExpandedClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onPickEntry(tab.key)
                    },
                    modifier = Modifier
                        .zIndex(if (isDragged) 10f else 0f)
                        .offset {
                            IntOffset(
                                x = curLocalX.roundToInt(),
                                y = rowTopNow.roundToInt(),
                            )
                        }
                        .then(
                            if (editorMode && !isDragged && !isSettling) {
                                Modifier.clipToVerticalBand(
                                    topPx = chrome.expandedPadTopPx,
                                    bottomPx = chrome.expandedBarHPx - chrome.footerHPx,
                                    itemTopPx = rowTopNow,
                                )
                            } else {
                                Modifier
                            },
                        )
                        .width(curW.toDp())
                        .widthIn(max = pillWidthCapNowPx.toDp())
                        .height(PillH)
                        .onPlaced { coords ->
                            reportRowBounds(tab.key, coords.boundsInParent())
                        },
                )
            }
        }
    }
}

// Settled open state (fully expanded, not editing): one scroll region above
// the fixed footer holding the main header, all overflow rows, the favorites
// header and all pinned rows. Same top-down geometry as the absolute rows,
// so the switch at the end of the expand flight is seamless; when the bar is
// capped (landscape / tall lists) this list scrolls instead of pushing items
// off the bar. Animation and editor mode keep the absolute rows.
@Composable
internal fun SettledDockList(
    listState: LazyListState,
    density: Density,
    motion: MotionSettings,
    effOverflow: List<DockEntry>,
    effTabs: List<DockEntry>,
    tabLabels: List<String>,
    selectedKey: String,
    jigglePhase: Float,
    rowWidthPx: Float,
    listHPx: Float,
    offsetXPx: Float,
    offsetYPx: Float,
    mainSectionTitle: String,
    favoritesSectionTitle: String,
    onPickEntry: (String) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    with(density) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .offset {
                    IntOffset(
                        offsetXPx.roundToInt(),
                        offsetYPx.roundToInt(),
                    )
                }
                .width(rowWidthPx.toDp())
                .height(listHPx.toDp()),
            verticalArrangement = Arrangement.spacedBy(RowGap, Alignment.Bottom),
            userScrollEnabled = true,
        ) {
            item(key = "main-header") {
                SectionHeaderDivider(
                    title = mainSectionTitle,
                    revealProgress = 1f,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(SectionHeaderH),
                )
            }
            itemsIndexed(effOverflow, key = { _, item -> item.key }) { _, item ->
                OverflowEntryRow(
                    entry = item,
                    motion = motion,
                    selected = item.key == selectedKey,
                    revealProgress = 1f,
                    enabled = true,
                    editorMode = false,
                    isDragged = false,
                    jigglePhase = jigglePhase,
                    wobbleStrength = 0f,
                    shiftTargetPx = 0f,
                    followOffsetYPx = null,
                    onPick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onPickEntry(item.key)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(PillH),
                )
            }
            item(key = "fav-header") {
                SectionHeaderDivider(
                    title = favoritesSectionTitle,
                    revealProgress = 1f,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(SectionHeaderH),
                )
            }
            itemsIndexed(effTabs, key = { _, tab -> tab.key }) { tabIndex, tab ->
                UnifiedDockTabItem(
                    entry = tab,
                    motion = motion,
                    label = tabLabels[tabIndex],
                    selected = tab.key == selectedKey,
                    isFingerHere = false,
                    dockPressed = false,
                    flightProgress = 1f,
                    labelReveal = 1f,
                    expandedClickable = true,
                    editorMode = false,
                    isDragged = false,
                    jigglePhase = jigglePhase,
                    wobbleStrength = 0f,
                    shiftTargetPx = 0f,
                    followOffsetYPx = null,
                    onExpandedClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onPickEntry(tab.key)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(PillH),
                )
            }
        }
    }
}

// "Main" section divider. Glides with a spring like rows so count changes
// move it smoothly. Absolute header only while animating/editing; the
// settled scroll list renders its own copy.
@Composable
internal fun MainSectionHeader(
    chrome: DockChrome,
    density: Density,
    motion: MotionSettings,
    topFromBottomPx: Float,
    bottomFromBottomPx: Float,
    rowWidthPx: Float,
    settledList: Boolean,
    title: String,
) {
    key("main-header") {
        val targetY = chrome.expandedBarHPx - topFromBottomPx
        val glideY by animateFloatAsState(
            targetValue = targetY,
            animationSpec = spring(stiffness = 380f, dampingRatio = 0.85f),
            label = "MainHeaderGlide",
        )
        with(density) {
            val mid = bottomFromBottomPx + chrome.sectionHeaderHPx * 0.5f
            val revealEnd = minOf(
                topFromBottomPx + chrome.sectionHeaderHPx * motion.revealOvershoot,
                chrome.expandedBarHPx,
            )
            val reveal = sstep(chrome.barHPx, mid, revealEnd)
            if (!settledList && reveal > 0.001f) {
                val slideUpPx = (1f - reveal) * motion.riseOffsetDp.dp.toPx()
                val headerTopPx = glideY + slideUpPx + chrome.editorScrollPx
                SectionHeaderDivider(
                    title = title,
                    revealProgress = reveal,
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                x = chrome.expandedPadHPx.roundToInt(),
                                y = headerTopPx.roundToInt(),
                            )
                        }
                        .clipToVerticalBand(
                            topPx = chrome.expandedPadTopPx,
                            bottomPx = chrome.expandedBarHPx - chrome.footerHPx,
                            itemTopPx = headerTopPx,
                        )
                        .width(rowWidthPx.toDp())
                        .height(SectionHeaderH),
                )
            }
        }
    }
}

// "Favorites" section divider. Same spring glide as rows so promoting a row
// glides the divider. Same settled-list hiding as the main header above.
@Composable
internal fun FavoritesSectionHeader(
    chrome: DockChrome,
    density: Density,
    motion: MotionSettings,
    topFromBottomPx: Float,
    bottomFromBottomPx: Float,
    rowWidthPx: Float,
    settledList: Boolean,
    title: String,
) {
    key("fav-header") {
        val targetY = chrome.expandedBarHPx - topFromBottomPx
        val glideY by animateFloatAsState(
            targetValue = targetY,
            animationSpec = spring(stiffness = 380f, dampingRatio = 0.85f),
            label = "FavHeaderGlide",
        )
        with(density) {
            val mid = bottomFromBottomPx + chrome.sectionHeaderHPx * 0.5f
            val revealEnd = minOf(
                topFromBottomPx + chrome.sectionHeaderHPx * motion.revealOvershoot,
                chrome.expandedBarHPx,
            )
            val reveal = sstep(chrome.barHPx, mid, revealEnd)
            if (!settledList && reveal > 0.001f) {
                val slideUpPx = (1f - reveal) * motion.riseOffsetDp.dp.toPx()
                val headerTopPx = glideY + slideUpPx + chrome.editorScrollPx
                SectionHeaderDivider(
                    title = title,
                    revealProgress = reveal,
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                x = chrome.expandedPadHPx.roundToInt(),
                                y = headerTopPx.roundToInt(),
                            )
                        }
                        .clipToVerticalBand(
                            topPx = chrome.expandedPadTopPx,
                            bottomPx = chrome.expandedBarHPx - chrome.footerHPx,
                            itemTopPx = headerTopPx,
                        )
                        .width(rowWidthPx.toDp())
                        .height(SectionHeaderH),
                )
            }
        }
    }
}

// Bottom divider above the footer buttons.
@Composable
internal fun FooterDivider(
    chrome: DockChrome,
    motion: MotionSettings,
) {
    val dividerAlpha = sstep(chrome.progress, motion.dividerFadeStart, motion.dividerFadeEnd)
    val dividerY = chrome.expandedBarHPx - chrome.expandedPadBottomPx - chrome.pillHPx - chrome.dividerAreaHPx * 0.5f
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f * dividerAlpha),
        modifier = Modifier
            .padding(horizontal = ExpandedPadH)
            .fillMaxWidth()
            .offset { IntOffset(0, dividerY.roundToInt()) },
    )
}
