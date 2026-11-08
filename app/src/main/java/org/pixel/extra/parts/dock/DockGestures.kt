package org.pixel.extra.parts.dock

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.abs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

// Collapsed-bar detector: tap picks a tab, horizontal scrub walks the tabs,
// the burger opens the overflow list. Kept as a modifier so the main dock
// composable stays below the project file-size limit.
internal fun Modifier.collapsedDockGesture(
    enabled: Boolean,
    resolveKeyAtX: (Float) -> String?,
    selectedKey: () -> String,
    onBurger: () -> Unit,
    onSelect: (String) -> Unit,
    onPressChange: (Boolean) -> Unit,
    onPressedKeyChange: (String?) -> Unit,
    onHaptic: () -> Unit,
): Modifier = composed {
    val latestResolve by rememberUpdatedState(resolveKeyAtX)
    val latestSelected by rememberUpdatedState(selectedKey)
    val latestBurger by rememberUpdatedState(onBurger)
    val latestSelect by rememberUpdatedState(onSelect)
    val latestPress by rememberUpdatedState(onPressChange)
    val latestPressedKey by rememberUpdatedState(onPressedKeyChange)
    val latestHaptic by rememberUpdatedState(onHaptic)

    pointerInput(enabled) {
        if (!enabled) return@pointerInput
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            latestPress(true)
            val initialHit = latestResolve(down.position.x)
            latestPressedKey(initialHit)

            if (initialHit == BurgerKey) {
                latestHaptic()
                latestBurger()
                try {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.changes.none { it.pressed }) break
                        event.changes.forEach { it.consume() }
                    }
                } finally {
                    latestPress(false)
                    latestPressedKey(null)
                }
                return@awaitEachGesture
            } else if (initialHit != null) {
                latestHaptic()
                if (initialHit != latestSelected()) latestSelect(initialHit)
            }

            var anchorX = down.position.x
            try {
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id }
                        ?: event.changes.firstOrNull()
                        ?: break
                    if (!change.pressed) break

                    val x = change.position.x
                    val fingerMoved = abs(x - anchorX) > 0.5f
                    val hit = latestResolve(x)
                    if (hit == BurgerKey) {
                        if (fingerMoved) {
                            latestHaptic()
                            latestBurger()
                            break
                        }
                    } else if (hit != null && hit != latestSelected() && fingerMoved) {
                        latestHaptic()
                        latestSelect(hit)
                        anchorX = x
                    }
                    latestPressedKey(hit)
                    change.consume()
                }
            } finally {
                latestPress(false)
                latestPressedKey(null)
            }
        }
    }
}

// Everything the editor-mode detector needs from the dock. The dock builds a
// fresh spec on every composition; the modifier forwards the latest instance
// into the long-running pointer handler so state never goes stale.
internal class EditorGestureSpec(
    val isActive: () -> Boolean,
    val touchSlopPx: Float,
    val gripZonePx: Float,
    val longPressMs: Long,
    val viewportTopPx: Float,
    val viewportBottomPx: Float,
    val slotAtDown: (y: Float, x: Float) -> Int?,
    val rowRightAt: (slot: Int) -> Float?,
    val parkSettle: () -> Unit,
    val pickUp: (slot: Int) -> Unit,
    val dragBy: (dy: Float) -> Unit,
    val drop: () -> Unit,
    val cancel: () -> Unit,
    val scrollBy: (dy: Float) -> Unit,
    val onPickupHaptic: () -> Unit,
)

// Editor-mode detector: immediate pickup on the right-hand grip, long-press
// pickup anywhere else on a row, vertical drag before the long-press timeout
// turns into a list scroll, and a drag loop that follows the finger.
internal fun Modifier.editorDockGestures(spec: EditorGestureSpec): Modifier = composed {
    val latest by rememberUpdatedState(spec)

    pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val s = latest
            if (!s.isActive()) return@awaitEachGesture
            if (down.position.y < s.viewportTopPx ||
                down.position.y > s.viewportBottomPx
            ) {
                return@awaitEachGesture
            }

            s.parkSettle()
            val slot = s.slotAtDown(down.position.y, down.position.x)

            // Right-edge grip: instant pickup on DOWN, no long-press delay.
            val gripSlot = slot?.takeIf { pressedSlot ->
                s.rowRightAt(pressedSlot)
                    ?.let { right -> down.position.x >= right - s.gripZonePx } == true
            }
            if (gripSlot != null) {
                s.onPickupHaptic()
                s.pickUp(gripSlot)
                runEditorDrag(s, down.id)
                return@awaitEachGesture
            }

            var released = false
            var scrollMode = false
            var firstScrollDy = 0f
            withTimeoutOrNull(s.longPressMs) {
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id }
                        ?: event.changes.firstOrNull()
                        ?: break
                    if (!change.pressed) {
                        released = true
                        break
                    }
                    val dy = change.position.y - down.position.y
                    val dx = change.position.x - down.position.x
                    if (abs(dy) > s.touchSlopPx && abs(dy) >= abs(dx)) {
                        scrollMode = true
                        firstScrollDy = change.position.y - change.previousPosition.y
                        change.consume()
                        break
                    }
                    // Horizontal drift in editor mode: swallow the tap but keep
                    // the long-press window alive (no scrub in this mode).
                    if ((change.position - down.position).getDistance() > s.touchSlopPx * 2f) {
                        change.consume()
                    }
                }
            }
            if (released) return@awaitEachGesture

            if (scrollMode) {
                if (firstScrollDy != 0f) s.scrollBy(firstScrollDy)
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id }
                        ?: event.changes.firstOrNull()
                        ?: break
                    if (!change.pressed) {
                        change.consume()
                        break
                    }
                    val dy = change.position.y - change.previousPosition.y
                    if (dy != 0f) s.scrollBy(dy)
                    change.consume()
                }
                return@awaitEachGesture
            }

            // Long press fired with a stationary finger.
            if (slot == null) return@awaitEachGesture
            s.onPickupHaptic()
            s.pickUp(slot)
            runEditorDrag(s, down.id)
        }
    }
}

private suspend fun AwaitPointerEventScope.runEditorDrag(
    spec: EditorGestureSpec,
    pointerId: PointerId,
) {
    try {
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == pointerId }
                ?: event.changes.firstOrNull()
                ?: break
            if (!change.pressed) {
                change.consume()
                break
            }
            val dy = change.position.y - change.previousPosition.y
            if (dy != 0f) spec.dragBy(dy)
            change.consume()
        }
        spec.drop()
    } catch (e: CancellationException) {
        spec.cancel()
        throw e
    } catch (e: Exception) {
        spec.cancel()
    }
}
