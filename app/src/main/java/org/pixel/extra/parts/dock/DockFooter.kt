package org.pixel.extra.parts.dock

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.util.lerp
import kotlin.math.roundToInt
import org.pixel.extra.parts.R
import org.pixel.extra.parts.motion.MotionSettings
import org.pixel.extra.parts.ui.edgeOutline

// Footer buttons of the expanded dock: edit pencil on the left, collapse (or
// settings, in editor mode) capsule on the right. Rendered as absolutely
// positioned children of the bar content box, like the rows above.
@Composable
internal fun DockFooter(
    chrome: DockChrome,
    density: Density,
    motion: MotionSettings,
    editorMode: Boolean,
    collapseLabel: String,
    collapseTextWidthPx: Float,
    settingsLabel: String,
    settingsTextWidthPx: Float,
    editLabel: String,
    editTextWidthPx: Float,
    exitEditorLabel: String,
    exitTextWidthPx: Float,
    pressedKey: String?,
    isDockPressed: Boolean,
    expanded: Boolean,
    burgerCollapsedWPx: Float,
    onCollapse: () -> Unit,
    onToggleEditor: () -> Unit,
    onOpenTuning: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val textRevealT = sstep(chrome.progress, motion.collapseTextStart, motion.collapseTextEnd)
    val capsuleAppearT = sstep(chrome.progress, motion.capsuleFadeStart, motion.capsuleFadeEnd)
    val iconMorphT = sstep(chrome.progress, motion.iconMorphStart, motion.iconMorphEnd)

    val activeToggleTextWPx = if (editorMode) settingsTextWidthPx else collapseTextWidthPx
    val animToggleTextWPx by animateFloatAsState(
        targetValue = activeToggleTextWPx,
        animationSpec = tween(220),
        label = "ToggleTextW",
    )
    with(density) {
        val collapseExpandedWPx = 56.dp.toPx() + animToggleTextWPx
        val curCapsuleWPx = lerp(burgerCollapsedWPx, collapseExpandedWPx, textRevealT)
        val rightInsetPx = lerp(chrome.barPadPx, chrome.expandedPadHPx, chrome.widthPhase)
        val curToggleLocalX = (chrome.barWPx - rightInsetPx - curCapsuleWPx)
            .coerceAtLeast(chrome.barPadPx)
        val curToggleTopY = chrome.expandedBarHPx -
            lerp(chrome.barPadPx, chrome.expandedPadBottomPx, chrome.heightPhase) -
            chrome.pillHPx

        val editAlpha = sstep(chrome.progress, 0.70f, 0.95f)
        val animEditTextWPx by animateFloatAsState(
            targetValue = if (editorMode) exitTextWidthPx else editTextWidthPx,
            animationSpec = tween(220),
            label = "EditTextW",
        )
        val pencilContentWPx =
            20.dp.toPx() + 12.dp.toPx() + animEditTextWPx + RowPadH.toPx() * 2f
        val pencilAvailWPx =
            (curToggleLocalX - chrome.expandedPadHPx - 8.dp.toPx()).coerceAtLeast(0f)
        val pencilWPx = minOf(pencilContentWPx, pencilAvailWPx)

        if (editAlpha > 0.01f && pencilWPx > 1f) {
            val pencilSlidePx = -(1f - editAlpha) * 14.dp.toPx()
            EditorPencilButton(
                editLabel = editLabel,
                exitLabel = exitEditorLabel,
                editorMode = editorMode,
                alpha = editAlpha,
                enabled = editAlpha > 0.5f && chrome.progress > 0.6f,
                onToggleEditor = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onToggleEditor()
                },
                modifier = Modifier
                    .offset {
                        IntOffset(
                            x = (chrome.expandedPadHPx + pencilSlidePx).roundToInt(),
                            y = curToggleTopY.roundToInt(),
                        )
                    }
                    .width(pencilWPx.toDp())
                    .height(PillH),
            )
        }

        UnifiedBurgerCollapseButton(
            collapseLabel = collapseLabel,
            collapseTextWidthPx = collapseTextWidthPx,
            settingsLabel = settingsLabel,
            editorMode = editorMode,
            iconMorphProgress = iconMorphT,
            capsuleAlpha = capsuleAppearT,
            textExpandProgress = textRevealT,
            isFingerHere = pressedKey == BurgerKey && isDockPressed && !expanded,
            expandedClickable = chrome.progress > 0.15f,
            onCollapseClick = {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onCollapse()
            },
            onSettingsClick = {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onOpenTuning()
            },
            modifier = Modifier
                .offset {
                    IntOffset(
                        x = curToggleLocalX.roundToInt(),
                        y = curToggleTopY.roundToInt(),
                    )
                }
                .width(curCapsuleWPx.toDp())
                .height(PillH),
        )
    }
}

@Composable
internal fun UnifiedBurgerCollapseButton(
    collapseLabel: String,
    collapseTextWidthPx: Float,
    settingsLabel: String,
    editorMode: Boolean,
    iconMorphProgress: Float,
    capsuleAlpha: Float,
    textExpandProgress: Float,
    isFingerHere: Boolean,
    expandedClickable: Boolean,
    onCollapseClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val baseCapsuleColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val pressAlpha = if (isFingerHere) 0.75f else 0f
    val effectiveBgAlpha = maxOf(pressAlpha, 0.52f * capsuleAlpha)
    val bgColor = baseCapsuleColor.copy(alpha = effectiveBgAlpha)
    val borderColor = baseCapsuleColor.edgeOutline(alpha = 0.45f * capsuleAlpha)
    val iconTint = MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(bgColor)
            .then(
                if (capsuleAlpha > 0.01f) {
                    Modifier.border(1.dp, borderColor, CircleShape)
                } else {
                    Modifier
                }
            )
            .then(
                if (expandedClickable) {
                    Modifier.clickable(onClick = if (editorMode) onSettingsClick else onCollapseClick)
                } else {
                    Modifier
                }
            )
            .padding(horizontal = RowPadH),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Crossfade(targetState = editorMode, label = "ToggleIcon") { editing ->
            if (editing) {
                Icon(
                    painter = painterResource(R.drawable.ic_tune),
                    contentDescription = settingsLabel,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp),
                )
            } else {
                MorphingBurgerArrowIcon(
                    progress = iconMorphProgress,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        if (editorMode) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = settingsLabel,
                color = iconTint,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.graphicsLayer { alpha = capsuleAlpha },
            )
        } else if (textExpandProgress > 0.005f) {
            val extraWidthDp = with(density) {
                ((8.dp.toPx() + collapseTextWidthPx) * textExpandProgress).toDp()
            }
            Box(
                modifier = Modifier
                    .width(extraWidthDp)
                    .clipToBounds(),
                contentAlignment = Alignment.CenterStart,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.graphicsLayer {
                        alpha = sstep(textExpandProgress, 0.08f, 0.85f)
                        translationX = -(1f - textExpandProgress) * (collapseTextWidthPx * 0.65f)
                    },
                ) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = collapseLabel,
                        color = iconTint,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Visible,
                    )
                }
            }
        }
    }
}

@Composable
internal fun MorphingBurgerArrowIcon(
    progress: Float,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val t = progress.coerceIn(0f, 1f)
        val cx = size.width * 0.5f
        val cy = size.height * 0.5f
        val strokeW = 2.dp.toPx()
        val halfSpan = 7.dp.toPx()
        val barOffsetY = 5.dp.toPx()
        val wingTipX = halfSpan
        val wingBackX = 0.5.dp.toPx()
        val wingSpanY = 5.8.dp.toPx()

        // At t = 0 all three bars are horizontal (y = -offset, 0, +offset);
        // at t = 1 the top/bottom bars meet the shaft tip as an arrowhead.
        rotate(degrees = 180f * t, pivot = Offset(cx, cy)) {
            val shaftStart = Offset(cx - lerp(halfSpan, 6.5.dp.toPx(), t), cy)
            val shaftEnd = Offset(cx + lerp(halfSpan, 6.2.dp.toPx(), t), cy)
            drawLine(
                color = tint,
                start = shaftStart,
                end = shaftEnd,
                strokeWidth = strokeW,
                cap = StrokeCap.Round,
            )

            val topStart = Offset(
                x = cx + lerp(-halfSpan, wingBackX, t),
                y = cy + lerp(-barOffsetY, -wingSpanY, t),
            )
            val topEnd = Offset(
                x = cx + lerp(halfSpan, wingTipX, t),
                y = cy + lerp(-barOffsetY, 0f, t),
            )
            drawLine(
                color = tint,
                start = topStart,
                end = topEnd,
                strokeWidth = strokeW,
                cap = StrokeCap.Round,
            )

            val botStart = Offset(
                x = cx + lerp(-halfSpan, wingBackX, t),
                y = cy + lerp(barOffsetY, wingSpanY, t),
            )
            val botEnd = Offset(
                x = cx + lerp(halfSpan, wingTipX, t),
                y = cy + lerp(barOffsetY, 0f, t),
            )
            drawLine(
                color = tint,
                start = botStart,
                end = botEnd,
                strokeWidth = strokeW,
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
internal fun EditorPencilButton(
    editLabel: String,
    exitLabel: String,
    editorMode: Boolean,
    alpha: Float,
    enabled: Boolean,
    onToggleEditor: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val baseCapsuleColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val bgColor = baseCapsuleColor.copy(alpha = 0.52f * alpha)
    val borderColor = baseCapsuleColor.edgeOutline(alpha = 0.45f * alpha)
    val contentTint = MaterialTheme.colorScheme.onSurfaceVariant
    val activeLabel = if (editorMode) exitLabel else editLabel

    Row(
        modifier = modifier
            .graphicsLayer { this.alpha = alpha }
            .clip(CircleShape)
            .background(bgColor)
            .border(1.dp, borderColor, CircleShape)
            .then(
                if (enabled) {
                    Modifier.clickable(onClick = onToggleEditor)
                } else {
                    Modifier
                }
            )
            .padding(horizontal = RowPadH)
            .clipToBounds(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Crossfade(targetState = editorMode, label = "EditPencilIcon") { editing ->
            Icon(
                painter = painterResource(
                    if (editing) R.drawable.ic_close else R.drawable.ic_edit,
                ),
                contentDescription = activeLabel,
                tint = contentTint,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Box(
            modifier = Modifier.weight(1f, fill = false),
            contentAlignment = Alignment.CenterStart,
        ) {
            Crossfade(targetState = editorMode, label = "EditPencilLabel") { editing ->
                Text(
                    text = if (editing) exitLabel else editLabel,
                    color = contentTint,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
