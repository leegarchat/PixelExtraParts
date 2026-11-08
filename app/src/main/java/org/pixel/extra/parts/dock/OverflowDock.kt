package org.pixel.extra.parts.dock

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.util.lerp
import kotlin.math.PI
import kotlin.math.abs
import kotlinx.coroutines.delay
import org.pixel.extra.parts.R
import org.pixel.extra.parts.editor.DockEditor
import org.pixel.extra.parts.motion.MotionPrefs
import org.pixel.extra.parts.motion.MotionSettings
import org.pixel.extra.parts.motion.rememberFloatBarMotion
import org.pixel.extra.parts.ui.BottomBlurBar
import org.pixel.extra.parts.ui.GraphicsLayerRecordingState

@Composable
fun ExpressiveFloatingDockWithOverflow(
    tabs: List<DockEntry>,
    overflowItems: List<DockEntry>,
    selectedKey: String,
    onSelect: (String) -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    blurState: GraphicsLayerRecordingState,
    blurRadius: Float,
    motion: MotionSettings? = null,
    stripTapEaterEnabled: Boolean = true,
    modifier: Modifier = Modifier,
    editorMode: Boolean = false,
    onEditorModeChange: (Boolean) -> Unit = {},
    onOpenTuning: () -> Unit = {},
    mainSectionTitle: String = "Main",
    favoritesSectionTitle: String = "Favorites",
) {
    val haptics = LocalHapticFeedback.current
    val dockView = LocalView.current
    val density = LocalDensity.current
    val touchSlopPx = LocalViewConfiguration.current.touchSlop
    val labelStyle = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)

    val internalMotion = rememberFloatBarMotion()
    val mt = motion ?: internalMotion.settings

    var isDockPressed by remember { mutableStateOf(false) }
    var pressedKey by remember { mutableStateOf<String?>(null) }

    val appContext = LocalContext.current.applicationContext
    val defaultPinnedKeys = remember(tabs) { tabs.map { it.key } }
    val defaultOverflowKeys = remember(overflowItems) { overflowItems.map { it.key } }
    val initialOrder = remember(appContext) {
        MotionPrefs.loadDockOrder(appContext, defaultPinnedKeys, defaultOverflowKeys)
    }
    var pinnedKeys by remember { mutableStateOf(initialOrder.first) }
    var overflowKeys by remember { mutableStateOf(initialOrder.second) }

    var dragKey by remember { mutableStateOf<String?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var dragStartSlot by remember { mutableIntStateOf(-1) }
    var dragHoverSlot by remember { mutableIntStateOf(-1) }
    var dragStartTopPx by remember { mutableFloatStateOf(0f) }
    // Editor-mode vertical list scroll. 0 is the bottom-anchored rest position;
    // growing values slide the whole absolute-row stack down to reveal earlier
    // rows. Reset on every editor exit/collapse.
    var editorScrollPx by remember { mutableFloatStateOf(0f) }
    // Settle phase: after drop the released row keeps rendering on a frozen base
    // (settleBaseTopPx) plus a decaying residual (Animatable from the release
    // offset to the committed-slot delta), so off-center releases glide instead
    // of snapping. Neighbors are untouched: preview == commit, only the held row
    // animates its residual.
    var settleKey by remember { mutableStateOf<String?>(null) }
    var settleBaseTopPx by remember { mutableFloatStateOf(0f) }
    var settleStartDeltaPx by remember { mutableFloatStateOf(0f) }
    var settleTargetDeltaPx by remember { mutableFloatStateOf(0f) }
    var settleToken by remember { mutableIntStateOf(0) }

    // Live full-row rects in detector-Box (single bar-local space) coordinates,
    // refreshed by each row's onPlaced on every placement/size change. Pickup
    // hit-testing resolves against these, never against startup-captured values.
    // Written only from layout callbacks and read only from input handlers, so
    // it never subscribes composition (pure input side-channel, no loops).
    var rowBoundsPx by remember { mutableStateOf<Map<String, Rect>>(emptyMap()) }

    // Scroll state for the expanded overflow list (non-editor mode only).
    val overflowListState = rememberLazyListState()

    fun resetDrag() {
        dragKey = null
        dragOffsetY = 0f
        dragStartSlot = -1
        dragHoverSlot = -1
        dragStartTopPx = 0f
        // Cancels any in-flight settle (new pickup, mode exit, collapse).
        settleKey = null
    }

    LaunchedEffect(editorMode, expanded) {
        if (!editorMode || !expanded) {
            resetDrag()
            editorScrollPx = 0f
        }
    }

    fun commitDrop(key: String, hover: Int): DockEditor.DropResult? {
        val res = DockEditor.applyDrop(pinnedKeys, overflowKeys, key, hover)
        if (res == null) {
            // Invalid drop: no state change, single Reject buzz (LongPress fallback below API 34).
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                dockView.performHapticFeedback(HapticFeedbackConstants.REJECT)
            } else {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            }
            return null
        }
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        pinnedKeys = res.pinned
        overflowKeys = res.overflow
        MotionPrefs.saveDockOrder(appContext, res.pinned, res.overflow)
        return res
    }

    // Single arrangement source: during an editor drag the preview lists are
    // exactly DockEditor.applyDrop(pinnedKeys, overflowKeys, dragKey,
    // dragHoverSlot), the same pure call commitDrop uses. Rendering derives
    // from them, so on drop only drag state clears and neighbors do not move.
    val previewDrop = remember(pinnedKeys, overflowKeys, dragKey, dragHoverSlot, editorMode, expanded) {
        val key = dragKey
        if (!editorMode || !expanded || key == null || dragHoverSlot < 0) null
        else DockEditor.applyDrop(pinnedKeys, overflowKeys, key, dragHoverSlot)
    }
    val previewPinnedKeys = previewDrop?.pinned ?: pinnedKeys
    val previewOverflowKeys = previewDrop?.overflow ?: overflowKeys

    // Drop bounds of rows that left the lists (reorder/promote/demote churn).
    val allRowKeys = remember(previewPinnedKeys, previewOverflowKeys) {
        (previewPinnedKeys + previewOverflowKeys).toSet()
    }
    LaunchedEffect(allRowKeys) {
        if (rowBoundsPx.keys.any { it !in allRowKeys }) {
            rowBoundsPx = rowBoundsPx.filterKeys { it in allRowKeys }
        }
    }

    // Dynamic favorite set: the pinned section renders previewPinnedKeys in order and the
    // overflow section renders previewOverflowKeys in order. Entries unknown to both lists
    // (new keys) append deterministically to overflow.
    val effTabs = remember(tabs, overflowItems, previewPinnedKeys) {
        val byKey = (tabs + overflowItems).associateBy { it.key }
        previewPinnedKeys.mapNotNull { byKey[it] }
    }
    val effOverflow = remember(tabs, overflowItems, previewPinnedKeys, previewOverflowKeys) {
        val byKey = (tabs + overflowItems).associateBy { it.key }
        val pinnedSet = previewPinnedKeys.toSet()
        val overflowSet = previewOverflowKeys.toSet()
        val ordered = previewOverflowKeys.filterNot { it in pinnedSet }.mapNotNull { byKey[it] }
        ordered + (tabs + overflowItems).filter { it.key !in pinnedSet && it.key !in overflowSet }
    }

    // The expanded list is anchored to the bottom: on every expand, request the
    // last item before the settled list first measures. requestScrollToItem is
    // synchronous (unlike scrollToItem, which waits for the first layout), so
    // the first composed frame is already the bottom slice, no top flash.
    val settledItemCount = 2 + effOverflow.size + effTabs.size
    LaunchedEffect(expanded, settledItemCount) {
        if (expanded && settledItemCount > 0) {
            overflowListState.requestScrollToItem(settledItemCount - 1)
        }
    }

    // Continuous jiggle phase for editor mode.
    val infiniteTransition = rememberInfiniteTransition(label = "EditorJiggle")
    val jigglePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 520, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "JigglePhase",
    )
    val editorWobbleStrength by animateFloatAsState(
        targetValue = if (editorMode && expanded) 1f else 0f,
        animationSpec = spring(stiffness = 300f, dampingRatio = 0.8f),
        label = "EditorWobbleStrength",
    )

    val latestSelected by rememberUpdatedState(selectedKey)
    val latestSelect by rememberUpdatedState(onSelect)
    val latestExpanded by rememberUpdatedState(expanded)
    val latestToggleExpand by rememberUpdatedState(onExpandedChange)

    val expandProgress by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (expanded) mt.expandMs else mt.collapseMs,
            easing = FastOutSlowInEasing,
        ),
        label = "ExpandProgress",
    )
    val p = expandProgress.coerceIn(0f, 1f)

    val pressScale by animateFloatAsState(
        targetValue = if (isDockPressed && !expanded) mt.pressScalePct / 100f else 1f,
        animationSpec = spring(stiffness = 280f, dampingRatio = 0.78f),
        label = "DockPressScale",
    )
    val barScale = pressScale * (
        1f + (mt.expansionPulsePct / 100f) * sstep(p, 0f, 0.35f) * (1f - sstep(p, 0.35f, 1f))
        )
    val dockCorner = lerp(29.dp, 28.dp, p)

    val compactK = (mt.compactScalePct / 100f).coerceIn(0.5f, 1.5f)
    val densityKeff = lerp(compactK, 1f, p)
    val scaledDensity = remember(density, densityKeff) {
        Density(density.density * densityKeff, density.fontScale * densityKeff)
    }

    CompositionLocalProvider(LocalDensity provides scaledDensity) {
        val textMeasurer = rememberTextMeasurer()
        val tabLabels = effTabs.map {
            if (it.labelArg != null) stringResource(it.labelRes, it.labelArg)
            else stringResource(it.labelRes)
        }
        // Measured with Bold to match the rendered selected collapsed label
        // (UnifiedDockTabItem draws the selected label Bold, not SemiBold).
        val selectedLabelStyle = remember(labelStyle) {
            labelStyle.copy(fontWeight = FontWeight.Bold)
        }
        val tabTextWidthsPx = remember(tabLabels, selectedLabelStyle, scaledDensity) {
            tabLabels.map { text ->
                textMeasurer.measure(text = text, style = selectedLabelStyle, maxLines = 1).size.width.toFloat()
            }
        }
        val collapseLabel = stringResource(R.string.dock_more_expanded)
        val editLabel = stringResource(R.string.dock_edit_behavior)
        val settingsLabel = stringResource(R.string.dock_settings_short)
        val exitEditorLabel = stringResource(R.string.dock_exit_editor)
        val collapseTextWidthPx = remember(collapseLabel, labelStyle, scaledDensity) {
            textMeasurer.measure(text = collapseLabel, style = labelStyle, maxLines = 1).size.width.toFloat()
        }
        val settingsTextWidthPx = remember(settingsLabel, labelStyle, scaledDensity) {
            textMeasurer.measure(text = settingsLabel, style = labelStyle, maxLines = 1).size.width.toFloat()
        }
        val editTextWidthPx = remember(editLabel, labelStyle, scaledDensity) {
            textMeasurer.measure(text = editLabel, style = labelStyle, maxLines = 1).size.width.toFloat()
        }
        val exitTextWidthPx = remember(exitEditorLabel, labelStyle, scaledDensity) {
            textMeasurer.measure(text = exitEditorLabel, style = labelStyle, maxLines = 1).size.width.toFloat()
        }

        // Keyed by entry so selection widths track items (not positions) across reorders.
        val tabSelectedFractions = effTabs.map { tab ->
            key(tab.key) {
                val frac by animateFloatAsState(
                    targetValue = if (tab.key == selectedKey) 1f else 0f,
                    animationSpec = spring(stiffness = 320f, dampingRatio = 0.85f),
                    label = "TabSel_${tab.key}",
                )
                frac
            }
        }

        val pw = sstep(p, 0f, mt.widthPhaseEnd)
        val ph = sstep(p, mt.heightPhaseStart, 1.0f)

        BoxWithConstraints(
            modifier = modifier
                .fillMaxWidth()
                .padding(bottom = mt.bottomLiftDp.dp)
                .pointerInput(stripTapEaterEnabled) {
                    // Outer strip tap-catcher: eats taps around/below the bar so they
                    // do not fall through to the list behind. Gated by
                    // stripTapEaterEnabled — when OFF this block consumes nothing.
                    if (!stripTapEaterEnabled) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id }
                                ?: event.changes.firstOrNull()
                                ?: break
                            if (change.isConsumed) break
                            if (!change.pressed) {
                                change.consume()
                                break
                            }
                        }
                    }
                },
            contentAlignment = Alignment.BottomCenter,
        ) {
            val wFull = maxWidth
            val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
            // Cap the bar to the actually available space: full height minus the
            // status bar, the 64.dp top bar, the dock bottom chrome (navigation-bar
            // inset + outer margins + tuning lift) and a small safety margin.
            val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            val dockChromeBottom = navBottom + 24.dp + mt.bottomLiftDp.dp
            val maxAllowedBarH =
                (maxHeight - statusTop - 64.dp - dockChromeBottom - 8.dp).coerceAtLeast(CollapsedBarH)

            with(scaledDensity) {
                val wFullPx = wFull.toPx()
                val pillHPx = PillH.toPx()
                val barPadPx = BarPad.toPx()
                val collapsedBarHPx = CollapsedBarH.toPx()
                val expandedPadHPx = ExpandedPadH.toPx()
                val expandedPadTopPx = ExpandedPadTop.toPx()
                val expandedPadBottomPx = ExpandedPadBottom.toPx()
                val rowGapPx = RowGap.toPx()
                val collapsedGapPx = CollapsedGap.toPx()
                val dividerAreaHPx = DividerAreaH.toPx()
                val sectionHeaderHPx = SectionHeaderH.toPx()

                // Single-count overhead, mirrors UnifiedDockTabItem collapsed layout.
                // Unselected icon pill: 14.dp start pad + 20.dp icon + 14.dp end pad.
                val unselectedPillWPx = (14.dp + 20.dp + 14.dp).toPx()
                // Selected labeled pill: 16.dp start pad + 20.dp icon + 8.dp spacer + 16.dp end pad.
                val selectedBaseWPx = (16.dp + 20.dp + 8.dp + 16.dp).toPx()
                val burgerCollapsedWPx = 48.dp.toPx()

                // Dynamic cap for a selected (labeled) collapsed pill so the whole
                // collapsed bar always fits the measured bar width. Free budget =
                // full width minus bar padding, inter-pill gaps (incl. gap to the
                // burger), the burger slot and the fixed icon pills; coerced to one
                // icon pill so any tab count / selection stays valid.
                val pillCapSafetyPx = 2.dp.toPx()
                val maxCollapsedPillWPx = (
                    wFullPx - barPadPx * 2f - effTabs.size * collapsedGapPx -
                        burgerCollapsedWPx - (effTabs.size - 1).coerceAtLeast(0) * unselectedPillWPx -
                        pillCapSafetyPx
                    ).coerceAtLeast(unselectedPillWPx)

                val collapsedTabWidthsPx = effTabs.indices.map { i ->
                    lerp(unselectedPillWPx, selectedBaseWPx + tabTextWidthsPx[i], tabSelectedFractions[i])
                        .coerceAtMost(maxCollapsedPillWPx)
                }
                val collapsedBarWPx = barPadPx * 2f +
                    collapsedTabWidthsPx.sum() +
                    effTabs.size * collapsedGapPx +
                    burgerCollapsedWPx

                // effTabs is a remember key on purpose: after a reorder the widths can
                // be identical (e.g. all unselected pills), and without it the map would
                // keep associating startup order with slots, so taps/scrub resolve stale.
                val collapsedSlotRanges = remember(collapsedTabWidthsPx, collapsedBarWPx, effTabs) {
                    val map = LinkedHashMap<String, ClosedFloatingPointRange<Float>>()
                    var cursorX = barPadPx
                    effTabs.forEachIndexed { idx, tab ->
                        val w = collapsedTabWidthsPx[idx]
                        map[tab.key] = cursorX..(cursorX + w)
                        cursorX += w + collapsedGapPx
                    }
                    map[BurgerKey] = cursorX..(cursorX + burgerCollapsedWPx)
                    map
                }

                val latestCollapsedRanges by rememberUpdatedState(collapsedSlotRanges)

                fun resolveKeyAtCollapsedX(x: Float): String? {
                    val ranges = latestCollapsedRanges
                    ranges.entries.firstOrNull { x in it.value }?.let { return it.key }
                    return ranges.entries.minByOrNull { entry ->
                        val center = (entry.value.start + entry.value.endInclusive) * 0.5f
                        abs(x - center)
                    }?.key
                }

                val overCount = effOverflow.size
                val tabCount = effTabs.size
                val totalRows = overCount + tabCount

                // Dragged-row follow uses the pickup snapshot (dragStartTopPx) as its base,
                // not the live preview slot, so the held row's layout base never moves
                // mid-drag and finger tracking stays continuous across slot crossings.

                // Bottom-up geometry with the two dividers ("Main" and "Favorites"):
                val footerHPx = expandedPadBottomPx + pillHPx + dividerAreaHPx
                val pinnedTopFromBottomPx =
                    footerHPx + tabCount * pillHPx + (tabCount - 1).coerceAtLeast(0) * rowGapPx
                val favHeaderBottomFromBottomPx = pinnedTopFromBottomPx + rowGapPx
                val favHeaderTopFromBottomPx = favHeaderBottomFromBottomPx + sectionHeaderHPx
                val overflowBottomFromBottomPx = favHeaderTopFromBottomPx + rowGapPx
                val overflowTopFromBottomPx =
                    overflowBottomFromBottomPx + overCount * pillHPx + (overCount - 1).coerceAtLeast(0) * rowGapPx
                val mainHeaderBottomFromBottomPx = overflowTopFromBottomPx + rowGapPx
                val mainHeaderTopFromBottomPx = mainHeaderBottomFromBottomPx + sectionHeaderHPx

                val expandedBarHPx = (expandedPadTopPx + mainHeaderTopFromBottomPx)
                    .coerceAtMost(maxAllowedBarH.toPx())
                val maxAllowedBarHPx = maxAllowedBarH.toPx()

                // Editor scroll viewport: rows live between the top padding and
                // the fixed footer. The content height comes from the same
                // bottom-up slot geometry, so maxScroll is exact for both the
                // fully-fitted and the capped (landscape) bar.
                val editorViewportTopPx = expandedPadTopPx
                val editorViewportBottomPx = expandedBarHPx - footerHPx
                val editorContentTopPx = expandedBarHPx - mainHeaderTopFromBottomPx
                val editorViewportHPx =
                    (editorViewportBottomPx - editorViewportTopPx).coerceAtLeast(0f)
                val editorContentHPx =
                    (editorViewportBottomPx - editorContentTopPx).coerceAtLeast(0f)
                val maxEditorScrollPx =
                    (editorContentHPx - editorViewportHPx).coerceAtLeast(0f)

                val curBarWPx = lerp(collapsedBarWPx, wFullPx, pw)
                val curBarHPx = lerp(collapsedBarHPx, expandedBarHPx, ph)

                val curBarLeftScreenX = (wFullPx - curBarWPx) * 0.5f
                val collapsedBarLeftScreenX = (wFullPx - collapsedBarWPx) * 0.5f
                val expandedRowWPx = (wFullPx - expandedPadHPx * 2f).coerceAtLeast(unselectedPillWPx)

                val chrome = DockChrome(
                    barWPx = curBarWPx,
                    barHPx = curBarHPx,
                    expandedBarHPx = expandedBarHPx,
                    expandedPadHPx = expandedPadHPx,
                    barPadPx = barPadPx,
                    pillHPx = pillHPx,
                    rowGapPx = rowGapPx,
                    expandedPadTopPx = expandedPadTopPx,
                    expandedPadBottomPx = expandedPadBottomPx,
                    footerHPx = footerHPx,
                    dividerAreaHPx = dividerAreaHPx,
                    sectionHeaderHPx = sectionHeaderHPx,
                    unselectedPillWPx = unselectedPillWPx,
                    expandedRowWPx = expandedRowWPx,
                    overflowBottomFromBottomPx = overflowBottomFromBottomPx,
                    progress = p,
                    widthPhase = pw,
                    heightPhase = ph,
                    editorScrollPx = editorScrollPx,
                )
                val dragSnapshot = DockDragSnapshot(
                    dragKey = dragKey,
                    dragOffsetYPx = dragOffsetY,
                    dragStartTopPx = dragStartTopPx,
                    settleKey = settleKey,
                    settleBaseTopPx = settleBaseTopPx,
                    settleToken = settleToken,
                    settleStartDeltaPx = settleStartDeltaPx,
                    settleTargetDeltaPx = settleTargetDeltaPx,
                )

                // Keep the editor scroll inside the computed range when the
                // arrangement (row counts / bar cap) changes.
                LaunchedEffect(maxEditorScrollPx) {
                    editorScrollPx = editorScrollPx.coerceIn(0f, maxEditorScrollPx)
                }

                val edgeZonePx = 40.dp.toPx()
                val autoScrollStepPx = 6.dp.toPx()

                fun clampedDragTop(originTop: Float, offsetY: Float): Float {
                    val minTop = editorViewportTopPx
                    val maxTop = (editorViewportBottomPx - pillHPx).coerceAtLeast(minTop)
                    return (originTop + offsetY).coerceIn(minTop, maxTop)
                }

                fun editorSlotTop(slot: Int): Float =
                    chrome.slotTopY(slot, overCount, tabCount) + editorScrollPx

                // Preview neighbors need no extra shift: the eff lists above already
                // equal the commit arrangement (see previewDrop).
                fun computeHoverSlot(startSlot: Int, offsetY: Float, isFromPinned: Boolean): Int {
                    // True finger Y: origin top captured at pick-up plus finger travel.
                    // Candidates are the current preview slots; the resulting hover int
                    // feeds the same applyDrop call for preview and for commit, so both
                    // arrangements stay identical. The held row is clamped to the
                    // viewport because that is where it is actually drawn.
                    val originTop = if (dragStartTopPx > 0f) dragStartTopPx else editorSlotTop(startSlot)
                    val draggedTopY = clampedDragTop(originTop, offsetY)
                    // Full 0..totalRows-1 range on purpose: invalid targets (e.g. demoting
                    // the last favorite) stay previewable and are rejected in commitDrop
                    // with a Reject haptic and no state change.
                    val maxAllowedSlot = (totalRows - 1).coerceAtLeast(0)
                    val nearest = (0..maxAllowedSlot).minByOrNull { candidate ->
                        abs(draggedTopY - editorSlotTop(candidate))
                    } ?: startSlot
                    // Hysteresis at slot boundaries: keep the current hover until the
                    // finger is clearly closer to the new slot, so 1-2px jitter exactly
                    // on a midpoint cannot flap the preview (and its haptics) back and
                    // forth while the finger is steady.
                    val current = dragHoverSlot
                    if (nearest != current && current in 0..maxAllowedSlot) {
                        val dNew = abs(draggedTopY - editorSlotTop(nearest))
                        val dCur = abs(draggedTopY - editorSlotTop(current))
                        if (dNew + touchSlopPx * 0.35f >= dCur) return current
                    }
                    return nearest
                }

                // Single list-level editor drag: hit-test + lifecycle live here so a
                // held row survives zone switches (no per-row gesture to dispose).
                // Rows are passive visuals (lift + follow); this detector owns
                // pickup, move, drop and cancel and reuses slotTopY/computeHoverSlot.
                fun reportRowBounds(key: String, rect: Rect) {
                    val cur = rowBoundsPx[key]
                    if (cur != rect) rowBoundsPx = rowBoundsPx + (key to rect)
                }

                // Pickup resolution: measured full-row rects first (whole row area,
                // full width including padding, full row height, plus small slop),
                // so any press inside the row starts a potential pickup. Analytic
                // geometry below is only a fallback before first placement.
                // Park a previous gesture's settle so the next pickup never shares
                // settle state with it. Called at DOWN (before hit-testing,
                // leaving the long-press window for bounds to refresh) and again
                // in pickUpAt (in case DOWN raced a drop commit). The settle
                // residual renders in layout space, so the cached rect already
                // tracks the animation: no snap is needed or applied.
                fun parkSettleForPickup() {
                    if (settleKey == null) return
                    settleKey = null
                    settleBaseTopPx = 0f
                    settleStartDeltaPx = 0f
                    settleTargetDeltaPx = 0f
                }

                fun slotAtDown(y: Float, x: Float): Int? {
                    if (rowBoundsPx.isNotEmpty()) {
                        val order = effOverflow.map { it.key } + effTabs.map { it.key }
                        val pad = touchSlopPx * 0.5f
                        // Settle renders in layout space, so even a mid-settle rect
                        // matches the on-screen row: every key hit-tests truthfully.
                        order.forEachIndexed { slot, key ->
                            val r = rowBoundsPx[key] ?: return@forEachIndexed
                            if (x >= r.left - pad && x <= r.right + pad &&
                                y >= r.top - pad && y <= r.bottom + pad
                            ) {
                                return slot
                            }
                        }
                    }
                    if (x < expandedPadHPx - touchSlopPx ||
                        x > curBarWPx - expandedPadHPx + touchSlopPx
                    ) {
                        return null
                    }
                    for (slot in 0 until totalRows) {
                        val top = editorSlotTop(slot)
                        if (y >= top && y <= top + pillHPx) return slot
                    }
                    return null
                }

                fun rowRightAtSlot(slot: Int): Float? {
                    val key = if (slot < overCount) {
                        effOverflow.getOrNull(slot)?.key
                    } else {
                        effTabs.getOrNull(slot - overCount)?.key
                    } ?: return null
                    return rowBoundsPx[key]?.right
                        ?: (chrome.expandedPadHPx + chrome.expandedRowWPx)
                }

                fun pickUpAt(slot: Int) {
                    // Each gesture is isolated: park any in-flight settle first so no
                    // shared settle state survives into the new drag, then resolve
                    // the key from the committed lists (dragKey == null here, so the
                    // preview lists equal the committed arrangement).
                    parkSettleForPickup()
                    val keyAtSlot = if (slot < overCount) {
                        effOverflow.getOrNull(slot)?.key
                    } else {
                        effTabs.getOrNull(slot - overCount)?.key
                    } ?: return
                    dragKey = keyAtSlot
                    dragOffsetY = 0f
                    dragStartSlot = slot
                    dragHoverSlot = slot
                    // Base the finger follow on the actually placed row top so the
                    // held row does not jump when analytic and placed Y diverge.
                    // Settle renders in layout space, so even a mid-settle rect is
                    // the on-screen top; the analytic path is only a pre-placement
                    // fallback and matches static geometry exactly (same scroll).
                    dragStartTopPx = rowBoundsPx[keyAtSlot]?.top ?: editorSlotTop(slot)
                }

                fun dragBy(dy: Float) {
                    dragOffsetY += dy
                    val nextHover = computeHoverSlot(
                        startSlot = dragStartSlot,
                        offsetY = dragOffsetY,
                        isFromPinned = false,
                    )
                    if (nextHover != dragHoverSlot) {
                        dragHoverSlot = nextHover
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                }

                // Scroll the editor list by finger delta (positive = content moves
                // down / earlier rows appear). While a row is held, the list slides
                // under a stationary finger, so hover slots must be re-resolved.
                fun scrollEditorBy(delta: Float) {
                    val next = (editorScrollPx + delta).coerceIn(0f, maxEditorScrollPx)
                    if (next == editorScrollPx) return
                    editorScrollPx = next
                    if (dragKey != null && dragStartSlot >= 0) {
                        val hover = computeHoverSlot(
                            startSlot = dragStartSlot,
                            offsetY = dragOffsetY,
                            isFromPinned = false,
                        )
                        if (hover != dragHoverSlot) {
                            dragHoverSlot = hover
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                    }
                }

                // Auto-scroll driver, ticked from a LaunchedEffect while a drag is
                // active. Speed ramps with how deep the held row is inside the
                // 40.dp edge zone, so a stationary finger still pulls the list.
                fun autoScrollTick() {
                    if (dragKey == null) return
                    val heldTop = clampedDragTop(dragStartTopPx, dragOffsetY)
                    val topDepth = ((editorViewportTopPx + edgeZonePx) - heldTop)
                        .coerceIn(0f, edgeZonePx)
                    val bottomDepth = ((heldTop + pillHPx) - (editorViewportBottomPx - edgeZonePx))
                        .coerceIn(0f, edgeZonePx)
                    val step = when {
                        topDepth > 0f -> autoScrollStepPx * (topDepth / edgeZonePx)
                        bottomDepth > 0f -> -autoScrollStepPx * (bottomDepth / edgeZonePx)
                        else -> 0f
                    }
                    if (step != 0f) scrollEditorBy(step)
                }

                fun dropNow() {
                    val key = dragKey
                    if (key == null) {
                        resetDrag()
                        return
                    }
                    // Snapshot the clamped visual release point before commit clears
                    // drag state: the settle animation must start exactly where the
                    // held row is drawn, even when the finger pushed past the viewport.
                    val releaseTopPx = clampedDragTop(dragStartTopPx, dragOffsetY)
                    val hover = dragHoverSlot
                    // Single source of truth: commit with the live hover, then resolve
                    // the settle target from the arrangement commitDrop swapped in
                    // (valid drop) or the unchanged lists (invalid drop). Never reuse
                    // the last-composed preview lists here: on a fast release the
                    // finger can lift before a recomposition consumes the final
                    // hover, and the composed preview would still show an older
                    // arrangement (up to the pre-drag origin), driving the row back
                    // to the origin instead of the committed slot.
                    val res = commitDrop(key, hover)
                    resetDrag()
                    // Settle phase: glide the residual delta to the committed slot instead
                    // of snapping. Invalid drops spring back to origin the same way.
                    val postPinned = res?.pinned ?: pinnedKeys
                    val postOverflow = res?.overflow ?: overflowKeys
                    val finalSlot = (postOverflow + postPinned).indexOf(key)
                    if (finalSlot < 0) return
                    val finalTopPx = chrome.committedSlotTopY(
                        finalSlot,
                        postOverflow.size,
                        postPinned.size,
                        maxAllowedBarHPx,
                        editorScrollPx,
                    )
                    settleBaseTopPx = releaseTopPx
                    settleStartDeltaPx = 0f
                    settleTargetDeltaPx = finalTopPx - releaseTopPx
                    settleToken++
                    settleKey = key
                }

                fun cancelNow() {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    resetDrag()
                }

                fun clearSettle() {
                    settleKey = null
                }

                fun pickEntry(entryKey: String) {
                    latestSelect(entryKey)
                    latestToggleExpand(false)
                }

                val latestAutoScrollTick by rememberUpdatedState(::autoScrollTick)
                // Stationary-finger auto-scroll: ticks every frame while a row is
                // held, and stops as soon as the drag session ends.
                LaunchedEffect(dragKey) {
                    if (dragKey == null) return@LaunchedEffect
                    while (true) {
                        delay(16L)
                        latestAutoScrollTick()
                    }
                }

                val latestSlotAtDown by rememberUpdatedState(::slotAtDown)
                val latestRowRightAtSlot by rememberUpdatedState(::rowRightAtSlot)
                val latestParkSettleForPickup by rememberUpdatedState(::parkSettleForPickup)
                val latestPickUpAt by rememberUpdatedState(::pickUpAt)
                val latestDragBy by rememberUpdatedState(::dragBy)
                val latestDropNow by rememberUpdatedState(::dropNow)
                val latestCancelNow by rememberUpdatedState(::cancelNow)
                val latestScrollEditorBy by rememberUpdatedState(::scrollEditorBy)
                val latestEditorActive by rememberUpdatedState(editorMode)
                val latestExpandedState by rememberUpdatedState(expanded)
                val latestHaptics by rememberUpdatedState(haptics)

                BottomBlurBar(
                    modifier = Modifier
                        .width(curBarWPx.toDp())
                        .height(curBarHPx.toDp())
                        .collapsedDockGesture(
                            enabled = !expanded,
                            resolveKeyAtX = ::resolveKeyAtCollapsedX,
                            selectedKey = { latestSelected },
                            onBurger = { latestToggleExpand(true) },
                            onSelect = { latestSelect(it) },
                            onPressChange = { isDockPressed = it },
                            onPressedKeyChange = { pressedKey = it },
                            onHaptic = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            },
                        ),
                    blurState = blurState,
                    blurRadius = blurRadius,
                    scale = barScale,
                    shape = RoundedCornerShape(dockCorner),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .wrapContentHeight(align = Alignment.Bottom, unbounded = true)
                            .height(expandedBarHPx.toDp())
                            .editorDockGestures(
                                EditorGestureSpec(
                                    isActive = { latestEditorActive && latestExpandedState },
                                    touchSlopPx = touchSlopPx,
                                    gripZonePx = 48.dp.toPx(),
                                    longPressMs = 220L,
                                    viewportTopPx = editorViewportTopPx,
                                    viewportBottomPx = editorViewportBottomPx,
                                    slotAtDown = { y, x -> latestSlotAtDown(y, x) },
                                    rowRightAt = { slot -> latestRowRightAtSlot(slot) },
                                    parkSettle = { latestParkSettleForPickup() },
                                    pickUp = { slot -> latestPickUpAt(slot) },
                                    dragBy = { dy -> latestDragBy(dy) },
                                    drop = { latestDropNow() },
                                    cancel = { latestCancelNow() },
                                    scrollBy = { dy -> latestScrollEditorBy(dy) },
                                    onPickupHaptic = {
                                        latestHaptics.performHapticFeedback(
                                            HapticFeedbackType.LongPress,
                                        )
                                    },
                                ),
                            ),
                    ) {
                        // Settled open state (fully expanded, not editing): one scroll
                        // region above the fixed footer holding the main header, all
                        // overflow rows, the favorites header and all pinned rows.
                        // Same top-down geometry as the absolute rows, so the switch at
                        // the end of the expand flight is seamless; when the bar is
                        // capped (landscape / tall lists) this list scrolls instead of
                        // pushing items off the bar. Animation and editor mode keep the
                        // absolute rows (flight, reveal, drag hit-testing).
                        val settledList = !editorMode && p > 0.999f
                        val settledListHPx =
                            (expandedBarHPx - footerHPx - expandedPadTopPx).coerceAtLeast(0f)
                        if (p > 0.01f) {
                            val rowWidthPx = (curBarWPx - expandedPadHPx * 2f)
                                .coerceAtLeast(unselectedPillWPx)

                            if (settledList && settledListHPx > 1f) {
                                SettledDockList(
                                    listState = overflowListState,
                                    density = scaledDensity,
                                    motion = mt,
                                    effOverflow = effOverflow,
                                    effTabs = effTabs,
                                    tabLabels = tabLabels,
                                    selectedKey = latestSelected,
                                    jigglePhase = jigglePhase,
                                    rowWidthPx = rowWidthPx,
                                    listHPx = settledListHPx,
                                    offsetXPx = expandedPadHPx,
                                    offsetYPx = expandedPadTopPx,
                                    mainSectionTitle = mainSectionTitle,
                                    favoritesSectionTitle = favoritesSectionTitle,
                                    onPickEntry = ::pickEntry,
                                )
                            }

                            // --- "Main" DIVIDER ---
                            // Glide the divider with a spring like rows so count changes move it smoothly.
                            MainSectionHeader(
                                chrome = chrome,
                                density = scaledDensity,
                                motion = mt,
                                topFromBottomPx = mainHeaderTopFromBottomPx,
                                bottomFromBottomPx = mainHeaderBottomFromBottomPx,
                                rowWidthPx = rowWidthPx,
                                settledList = settledList,
                                title = mainSectionTitle,
                            )

                            // --- 1. "MAIN" SECTION (overflow items) ---
                            // Absolute rows for the expand animation and editor mode (the
                            // list-level drag detector hit-tests against these bar-local
                            // rects). The settled open state uses the unified scroll list
                            // above, so skip the duplicates there.
                            if (!settledList) {
                                ExpandedOverflowRows(
                                    chrome = chrome,
                                    density = scaledDensity,
                                    motion = mt,
                                    effOverflow = effOverflow,
                                    selectedKey = latestSelected,
                                    rowsEnabled = p > 0.6f,
                                    editorMode = editorMode,
                                    drag = dragSnapshot,
                                    onSettleDone = ::clearSettle,
                                    jigglePhase = jigglePhase,
                                    wobbleStrength = editorWobbleStrength,
                                    rowWidthPx = rowWidthPx,
                                    reportRowBounds = ::reportRowBounds,
                                    onPickEntry = ::pickEntry,
                                )
                            }

                            // --- "Favorites" DIVIDER ---
                            // Same spring glide as rows so promoting a row glides the divider.
                            FavoritesSectionHeader(
                                chrome = chrome,
                                density = scaledDensity,
                                motion = mt,
                                topFromBottomPx = favHeaderTopFromBottomPx,
                                bottomFromBottomPx = favHeaderBottomFromBottomPx,
                                rowWidthPx = rowWidthPx,
                                settledList = settledList,
                                title = favoritesSectionTitle,
                            )

                            // Bottom divider above the footer buttons.
                            FooterDivider(chrome = chrome, motion = mt)
                        }

                        // --- 2. "FAVORITES" SECTION (pinned tabs) ---
                        // Absolute flight rows for the expand animation and editor mode;
                        // the settled open state uses the unified scroll list above.
                        // Collapsed state renders these too: at p == 0 the flight bezier
                        // is exactly the collapsed slot geometry, so the tabs stay visible.
                        if (!settledList) {
                            PinnedFlightRows(
                                chrome = chrome,
                                density = scaledDensity,
                                motion = mt,
                                effTabs = effTabs,
                                tabLabels = tabLabels,
                                tabSelectedFractions = tabSelectedFractions,
                                collapsedTabWidthsPx = collapsedTabWidthsPx,
                                collapsedSlotRanges = collapsedSlotRanges,
                                collapsedBarLeftScreenX = collapsedBarLeftScreenX,
                                barLeftScreenXPx = curBarLeftScreenX,
                                maxCollapsedPillWPx = maxCollapsedPillWPx,
                                overCount = overCount,
                                tabCount = tabCount,
                                selectedKey = latestSelected,
                                pressedKey = pressedKey,
                                isDockPressed = isDockPressed,
                                expanded = expanded,
                                editorMode = editorMode,
                                drag = dragSnapshot,
                                onSettleDone = ::clearSettle,
                                jigglePhase = jigglePhase,
                                wobbleStrength = editorWobbleStrength,
                                reportRowBounds = ::reportRowBounds,
                                onPickEntry = ::pickEntry,
                            )
                        }

                        // --- 3. FOOTER BUTTONS (edit / collapse / settings) ---
                        // Always composed: at p == 0 all expanded pieces have zero
                        // alpha and only the burger stays visible, so the collapsed
                        // bar still has its overflow entry point.
                        DockFooter(
                            chrome = chrome,
                            density = scaledDensity,
                            motion = mt,
                            editorMode = editorMode,
                            collapseLabel = collapseLabel,
                            collapseTextWidthPx = collapseTextWidthPx,
                            settingsLabel = settingsLabel,
                            settingsTextWidthPx = settingsTextWidthPx,
                            editLabel = editLabel,
                            editTextWidthPx = editTextWidthPx,
                            exitEditorLabel = exitEditorLabel,
                            exitTextWidthPx = exitTextWidthPx,
                            pressedKey = pressedKey,
                            isDockPressed = isDockPressed,
                            expanded = expanded,
                            burgerCollapsedWPx = burgerCollapsedWPx,
                            onCollapse = { latestToggleExpand(false) },
                            onToggleEditor = { onEditorModeChange(!editorMode) },
                            onOpenTuning = onOpenTuning,
                        )
                    }
                }
            }
        }
    }
}
