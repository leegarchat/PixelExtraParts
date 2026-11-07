package org.pixel.extra.parts.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

// Floating dock with swipe scrub: tap is a zero-length swipe, selection
// already happens on awaitFirstDown, no slop threshold.
// Haptics follow guideline table 8: ContextClick for taps,
// TextHandleMove for swipe tab switches, Reject for locked attempts.
@Composable
fun <T> ExpressiveFloatingDock(
    tabs: List<T>,
    selectedTab: T,
    onSelectTab: (T) -> Unit,
    iconProvider: @Composable (T) -> Painter,
    labelProvider: @Composable (T) -> String,
    blurState: GraphicsLayerRecordingState,
    blurRadius: Float,
    isNavigationLocked: Boolean = false,
    onLockedAttempt: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val dockHaptics = LocalHapticFeedback.current
    var isDockPressed by remember { mutableStateOf(false) }
    var pressedTab by remember { mutableStateOf<T?>(null) }
    val tabBounds = remember { mutableStateMapOf<T, ClosedFloatingPointRange<Float>>() }

    // Fresh reads inside the long-running gesture without restarting it.
    val latestSelected by rememberUpdatedState(selectedTab)
    val latestSelect by rememberUpdatedState(onSelectTab)
    val latestLocked by rememberUpdatedState(isNavigationLocked)
    val latestLockedAttempt by rememberUpdatedState(onLockedAttempt)

    val dockScale by animateFloatAsState(
        targetValue = if (isDockPressed) 1.032f else 1f,
        animationSpec = spring(stiffness = 260f, dampingRatio = 0.78f),
        label = "DockBounceScale",
    )

    fun resolveTabAtX(x: Float): T? {
        // Exact hit first; otherwise nearest center so gaps never go dead.
        tabBounds.entries.firstOrNull { x in it.value }?.let { return it.key }
        return tabBounds.entries.minByOrNull { entry ->
            val center = (entry.value.start + entry.value.endInclusive) * 0.5f
            kotlin.math.abs(x - center)
        }?.key
    }

    fun selectTabFromGesture(targetTab: T) {
        if (targetTab == latestSelected) return
        if (latestLocked) {
            dockHaptics.performHapticFeedback(HapticFeedbackType.Reject)
            latestLockedAttempt()
        } else {
            dockHaptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            latestSelect(targetTab)
        }
    }

    BottomBlurBar(
        modifier = modifier
            .padding(bottom = 24.dp)
            // Empty tap eater so stray taps never fall through to the list underneath.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {}),
        blurState = blurState,
        blurRadius = blurRadius,
        scale = dockScale,
        shape = CircleShape,
    ) {
        Row(
            modifier = Modifier
                .padding(6.dp)
                // KEY: keyed only on the lock flag, NEVER on selection —
                // restarting mid-scrub would cancel awaitEachGesture.
                .pointerInput(latestLocked) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        isDockPressed = true
                        val initialHit = resolveTabAtX(down.position.x)
                        pressedTab = initialHit
                        if (initialHit != null && initialHit != latestSelected) {
                            selectTabFromGesture(initialHit)
                        } else {
                            dockHaptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                        }

                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id }
                                    ?: event.changes.firstOrNull()
                                if (change == null || !change.pressed) break
                                val hit = resolveTabAtX(change.position.x)
                                pressedTab = hit
                                if (hit != null && hit != latestSelected) {
                                    selectTabFromGesture(hit)
                                }
                                change.consume()
                            }
                        } finally {
                            isDockPressed = false
                            pressedTab = null
                        }
                    }
                },
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEach { t ->
                val selected = t == selectedTab
                val isFingerHere = pressedTab == t && isDockPressed

                val itemScale by animateFloatAsState(
                    targetValue = when {
                        isFingerHere && selected -> 0.96f
                        isFingerHere -> 1.05f
                        selected && isDockPressed -> 1.02f
                        else -> 1f
                    },
                    animationSpec = spring(stiffness = 280f, dampingRatio = 0.78f),
                    label = "TabItemBounce",
                )

                val pillBg by animateColorAsState(
                    targetValue = when {
                        // 0.85f, not opaque: avoids a harsh flash over the blur.
                        selected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
                        isFingerHere -> MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.75f)
                        else -> Color.Transparent
                    },
                    animationSpec = tween(220, easing = FastOutSlowInEasing),
                    label = "TabBgColor",
                )
                val contentTint by animateColorAsState(
                    targetValue = if (selected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    animationSpec = tween(180),
                    label = "TabContentTint",
                )
                val horizontalPad by animateDpAsState(
                    targetValue = if (selected) 16.dp else 14.dp,
                    animationSpec = spring(stiffness = 300f, dampingRatio = 0.85f),
                    label = "TabHorizontalPadding",
                )

                Row(
                    modifier = Modifier
                        .height(46.dp)
                        .onPlaced { coords ->
                            val x = coords.positionInParent().x
                            tabBounds[t] = x..(x + coords.size.width)
                        }
                        .graphicsLayer {
                            scaleX = itemScale
                            scaleY = itemScale
                        }
                        .clip(CircleShape)
                        .background(pillBg)
                        .then(
                            if (selected) {
                                Modifier.border(1.dp, pillBg.edgeOutline(), CircleShape)
                            } else {
                                Modifier
                            }
                        )
                        .padding(horizontal = horizontalPad),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        painter = iconProvider(t),
                        contentDescription = labelProvider(t),
                        tint = contentTint,
                        modifier = Modifier.size(20.dp),
                    )
                    AnimatedVisibility(
                        visible = selected,
                        enter = fadeIn(tween(220)) + expandHorizontally(
                            animationSpec = spring(stiffness = 320f, dampingRatio = 0.85f),
                            expandFrom = Alignment.Start,
                        ),
                        exit = fadeOut(tween(160)) + shrinkHorizontally(
                            animationSpec = spring(
                                stiffness = 320f,
                                dampingRatio = Spring.DampingRatioNoBouncy,
                            ),
                            shrinkTowards = Alignment.Start,
                        ),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = labelProvider(t),
                                color = contentTint,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

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
