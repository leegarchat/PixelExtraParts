package org.pixel.extra.parts.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

// Full-width strip around the dock: a clean tap-release in the strip is
// swallowed here so it never reaches the list under the overlay, while an
// ongoing scroll keeps its events (isConsumed passthrough, no fighting).
@Composable
fun DockStrip(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id }
                            ?: event.changes.firstOrNull()
                            ?: break
                        if (change.isConsumed) break // scroll claimed elsewhere: yield
                        if (!change.pressed) {
                            change.consume() // stray tap in the strip: swallow
                            break
                        }
                    }
                }
            },
        contentAlignment = Alignment.BottomCenter,
    ) {
        content()
    }
}
