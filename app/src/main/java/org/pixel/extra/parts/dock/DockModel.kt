package org.pixel.extra.parts.dock

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class DockEntry(
    val key: String,
    @StringRes val labelRes: Int,
    @DrawableRes val iconRes: Int,
    // Optional slot index for future numbered section entries.
    val labelArg: Int? = null,
)

internal fun sstep(x: Float, a: Float, b: Float): Float {
    if (b <= a) return if (x >= b) 1f else 0f
    val t = ((x - a) / (b - a)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

internal fun bez(t: Float, p0: Offset, p1: Offset, p2: Offset): Offset {
    val u = 1f - t
    return Offset(
        x = u * u * p0.x + 2f * u * t * p1.x + t * t * p2.x,
        y = u * u * p0.y + 2f * u * t * p1.y + t * t * p2.y,
    )
}

internal val PillH: Dp = 46.dp
internal val BarPad: Dp = 6.dp
internal val CollapsedBarH: Dp = PillH + BarPad * 2 // 58.dp
internal val ExpandedPadH: Dp = 10.dp
internal val ExpandedPadTop: Dp = 10.dp
internal val ExpandedPadBottom: Dp = 6.dp
internal val RowGap: Dp = 2.dp
internal val CollapsedGap: Dp = 4.dp
internal val DividerAreaH: Dp = 13.dp
internal val SectionHeaderH: Dp = 26.dp
internal val RowPadH: Dp = 14.dp
internal const val BurgerKey = "__burger__"

// Shared per-frame geometry of the expanded dock, computed once in the main
// composable and passed down to the extracted row/footer children so the
// 1000-line file limit holds without duplicating the layout math.
internal data class DockChrome(
    val barWPx: Float,
    val barHPx: Float,
    val expandedBarHPx: Float,
    val expandedPadHPx: Float,
    val barPadPx: Float,
    val pillHPx: Float,
    val rowGapPx: Float,
    val expandedPadTopPx: Float,
    val expandedPadBottomPx: Float,
    val footerHPx: Float,
    val dividerAreaHPx: Float,
    val sectionHeaderHPx: Float,
    val unselectedPillWPx: Float,
    val expandedRowWPx: Float,
    val overflowBottomFromBottomPx: Float,
    val progress: Float,
    val widthPhase: Float,
    val heightPhase: Float,
    // Editor-mode list scroll: 0 = bottom-anchored, grows upward to reveal
    // earlier rows. Rendering adds it to every row/header offset directly.
    val editorScrollPx: Float,
)

// Live drag/settle snapshot shared by the row children. Rows are passive
// visuals: the list-level detector owns pickup/move/drop, rows only read this.
internal data class DockDragSnapshot(
    val dragKey: String?,
    val dragOffsetYPx: Float,
    val dragStartTopPx: Float,
    val settleKey: String?,
    val settleBaseTopPx: Float,
    val settleToken: Int,
    val settleStartDeltaPx: Float,
    val settleTargetDeltaPx: Float,
)

// Exact Y of each slot top (0..totalRows-1) in the expanded state.
internal fun DockChrome.slotTopY(slot: Int, overCount: Int, tabCount: Int): Float {
    return if (slot < overCount) {
        val fromBottom = overCount - 1 - slot
        val itemTopFromBottom =
            overflowBottomFromBottomPx + fromBottom * (pillHPx + rowGapPx) + pillHPx
        expandedBarHPx - itemTopFromBottom
    } else {
        val tabIdx = slot - overCount
        val stepsAboveDivider = (tabCount - 1 - tabIdx).coerceAtLeast(0)
        expandedBarHPx - footerHPx - pillHPx - stepsAboveDivider * (pillHPx + rowGapPx)
    }
}

// Post-commit slot geometry: the same math as slotTopY above, but
// parameterized by explicit list sizes instead of the composed
// overCount/tabCount, so dropNow can resolve the settle target from
// the arrangement commitDrop actually swapped in. Term-by-term it
// equals the static row's rest position post-commit (same space,
// same slotTopY math); it must stay in sync with slotTopY.
// scrollPx is the editor-mode list scroll added on top of the base.
internal fun DockChrome.committedSlotTopY(
    slot: Int,
    over: Int,
    tabs: Int,
    maxAllowedBarHPx: Float,
    scrollPx: Float,
): Float {
    val pinnedTopFromBottom =
        footerHPx + tabs * pillHPx + (tabs - 1).coerceAtLeast(0) * rowGapPx
    val favTopFromBottom = pinnedTopFromBottom + rowGapPx + sectionHeaderHPx
    val overBottomFromBottom = favTopFromBottom + rowGapPx
    val overTopFromBottom =
        overBottomFromBottom + over * pillHPx + (over - 1).coerceAtLeast(0) * rowGapPx
    val mainTopFromBottom = overTopFromBottom + rowGapPx + sectionHeaderHPx
    val barHPx = (expandedPadTopPx + mainTopFromBottom)
        .coerceAtMost(maxAllowedBarHPx)
    val baseTop = if (slot < over) {
        val fromBottom = over - 1 - slot
        val itemTopFromBottom =
            overBottomFromBottom + fromBottom * (pillHPx + rowGapPx) + pillHPx
        barHPx - itemTopFromBottom
    } else {
        val tabIdx = slot - over
        val stepsAboveDivider = (tabs - 1 - tabIdx).coerceAtLeast(0)
        barHPx - footerHPx - pillHPx - stepsAboveDivider * (pillHPx + rowGapPx)
    }
    return baseTop + scrollPx
}
