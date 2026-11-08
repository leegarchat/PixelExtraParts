package org.pixel.extra.parts.dock

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.util.lerp
import kotlin.math.roundToInt
import kotlin.math.sin
import org.pixel.extra.parts.R
import org.pixel.extra.parts.motion.MotionSettings
import org.pixel.extra.parts.ui.edgeOutline

@Composable
internal fun SectionHeaderDivider(
    title: String,
    revealProgress: Float,
    modifier: Modifier = Modifier,
) {
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
    val lineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)

    Row(
        modifier = modifier
            .graphicsLayer {
                compositingStrategy = CompositingStrategy.Offscreen
            }
            .drawWithContent {
                drawContent()
                val r = revealProgress.coerceIn(0f, 1f)
                if (r < 0.999f) {
                    val h = size.height
                    val feather = (h * 0.95f).coerceAtLeast(1f)
                    val opaqueY = lerp(h + feather, 0f, r)
                    val transparentY = opaqueY - feather
                    drawRect(
                        brush = Brush.verticalGradient(
                            0f to Color.Transparent,
                            1f to Color.Black,
                            startY = transparentY,
                            endY = opaqueY,
                        ),
                        blendMode = BlendMode.DstIn,
                    )
                }
            }
            .padding(horizontal = RowPadH),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = labelColor,
            maxLines = 1,
        )
        Spacer(modifier = Modifier.width(10.dp))
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            thickness = 1.dp,
            color = lineColor,
        )
    }
}

@Composable
internal fun UnifiedDockTabItem(
    entry: DockEntry,
    motion: MotionSettings,
    label: String,
    selected: Boolean,
    isFingerHere: Boolean,
    dockPressed: Boolean,
    flightProgress: Float,
    labelReveal: Float,
    expandedClickable: Boolean,
    onExpandedClick: () -> Unit,
    modifier: Modifier = Modifier,
    editorMode: Boolean = false,
    isDragged: Boolean = false,
    jigglePhase: Float = 0f,
    wobbleStrength: Float = 0f,
    shiftTargetPx: Float = 0f,
    followOffsetYPx: Float? = null,
) {
    val dragShiftPx by animateFloatAsState(
        targetValue = shiftTargetPx,
        animationSpec = spring(stiffness = 380f, dampingRatio = 0.85f),
        label = "EditShift",
    )
    val dragScale by animateFloatAsState(
        targetValue = if (isDragged) 1.05f else 1f,
        animationSpec = spring(stiffness = 380f, dampingRatio = 0.85f),
        label = "EditDragScale",
    )
    val rowExtraYPx = followOffsetYPx ?: dragShiftPx
    val itemScale by animateFloatAsState(
        targetValue = when {
            flightProgress > 0.05f -> 1f
            isFingerHere && selected -> 0.96f
            isFingerHere -> 1.05f
            selected && dockPressed -> 1.02f
            else -> 1f
        },
        animationSpec = spring(stiffness = 280f, dampingRatio = 0.78f),
        label = "UnifiedTabBounce",
    )

    val pillBg by animateColorAsState(
        targetValue = when {
            isDragged -> MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.95f)
            selected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
            editorMode && flightProgress > 0.8f ->
                MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.42f)
            isFingerHere -> MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.75f)
            else -> Color.Transparent
        },
        animationSpec = tween(200),
        label = "UnifiedTabBg",
    )

    val contentTint by animateColorAsState(
        targetValue = if (selected || isDragged) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(180),
        label = "UnifiedTabTint",
    )

    val cornerRadius = lerp(23.dp, motion.rowCornerDp.dp, flightProgress)
    val shape = RoundedCornerShape(cornerRadius)
    val collapsedPad = if (selected) 16.dp else 14.dp
    val horizontalPad = lerp(collapsedPad, RowPadH, flightProgress)
    val iconSpacer = lerp(8.dp, 12.dp, flightProgress)

    val activeWobble = if (isDragged) 0f else wobbleStrength * flightProgress
    val wobbleRotZ = sin(jigglePhase) * 0.4f * activeWobble
    val wobbleTxDp = sin(jigglePhase * 1.3f + 0.8f) * 0.5f * activeWobble
    val wobbleTyDp = sin(jigglePhase * 0.9f + 1.7f) * 0.25f * activeWobble

    val borderModifier = when {
        isDragged -> Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, shape)
        selected -> Modifier.border(1.dp, pillBg.edgeOutline(), shape)
        editorMode && flightProgress > 0.8f -> Modifier.border(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f * activeWobble),
            shape,
        )
        else -> Modifier
    }

    Row(
        modifier = modifier
            .graphicsLayer {
                scaleX = itemScale * dragScale
                scaleY = itemScale * dragScale
                rotationZ = wobbleRotZ
                translationX = wobbleTxDp.dp.toPx()
                translationY = wobbleTyDp.dp.toPx()
            }
            .clip(shape)
            .background(pillBg)
            .then(borderModifier)
            .then(
                // In editor mode rows are drag-only: a plain tap must do nothing.
                if (expandedClickable && !editorMode) {
                    Modifier.clickable(onClick = onExpandedClick)
                } else {
                    Modifier
                }
            )
            .padding(horizontal = horizontalPad),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(entry.iconRes),
            contentDescription = label,
            tint = contentTint,
            modifier = Modifier.size(20.dp),
        )
        if (labelReveal > 0.01f) {
            Spacer(modifier = Modifier.width(iconSpacer * labelReveal))
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clipToBounds(),
            ) {
                Text(
                    text = label,
                    color = contentTint,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.graphicsLayer {
                        alpha = labelReveal
                        translationX = -(1f - labelReveal) * 10.dp.toPx()
                    },
                )
            }
            if (wobbleStrength > 0.01f) {
                DragHandleGrip(
                    tint = contentTint.copy(alpha = 0.65f * wobbleStrength * labelReveal),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
internal fun OverflowEntryRow(
    entry: DockEntry,
    motion: MotionSettings,
    selected: Boolean,
    revealProgress: Float,
    enabled: Boolean,
    onPick: () -> Unit,
    modifier: Modifier = Modifier,
    editorMode: Boolean = false,
    isDragged: Boolean = false,
    jigglePhase: Float = 0f,
    wobbleStrength: Float = 0f,
    shiftTargetPx: Float = 0f,
    followOffsetYPx: Float? = null,
) {
    val dragShiftPx by animateFloatAsState(
        targetValue = shiftTargetPx,
        animationSpec = spring(stiffness = 380f, dampingRatio = 0.85f),
        label = "OverflowEditShift",
    )
    val dragScale by animateFloatAsState(
        targetValue = if (isDragged) 1.05f else 1f,
        animationSpec = spring(stiffness = 380f, dampingRatio = 0.85f),
        label = "OverflowEditScale",
    )
    val rowExtraYPx = followOffsetYPx ?: dragShiftPx
    val rowBg by animateColorAsState(
        targetValue = when {
            isDragged -> MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.95f)
            selected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
            editorMode -> MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.42f)
            else -> Color.Transparent
        },
        animationSpec = tween(200),
        label = "OverflowRowBg",
    )
    val rowTint by animateColorAsState(
        targetValue = if (selected || isDragged) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(180),
        label = "OverflowRowTint",
    )

    val activeWobble = if (isDragged) 0f else wobbleStrength * revealProgress
    val wobbleRotZ = sin(jigglePhase) * 0.4f * activeWobble
    val wobbleTxDp = sin(jigglePhase * 1.3f + 0.8f) * 0.5f * activeWobble
    val wobbleTyDp = sin(jigglePhase * 0.9f + 1.7f) * 0.25f * activeWobble

    val rowShape = RoundedCornerShape(motion.rowCornerDp.dp)
    val borderModifier = when {
        isDragged -> Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, rowShape)
        selected -> Modifier.border(1.dp, rowBg.edgeOutline(), rowShape)
        editorMode -> Modifier.border(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f * activeWobble),
            rowShape,
        )
        else -> Modifier
    }

    Row(
        modifier = modifier
            .graphicsLayer {
                scaleX = dragScale
                scaleY = dragScale
                rotationZ = wobbleRotZ
                translationX = wobbleTxDp.dp.toPx()
                translationY = wobbleTyDp.dp.toPx()
                compositingStrategy = CompositingStrategy.Offscreen
            }
            .drawWithContent {
                drawContent()
                val r = revealProgress.coerceIn(0f, 1f)
                if (r < 0.999f) {
                    val h = size.height
                    val feather = (h * 0.95f).coerceAtLeast(1f)
                    val opaqueY = lerp(h + feather, 0f, r)
                    val transparentY = opaqueY - feather
                    drawRect(
                        brush = Brush.verticalGradient(
                            0f to Color.Transparent,
                            1f to Color.Black,
                            startY = transparentY,
                            endY = opaqueY,
                        ),
                        blendMode = BlendMode.DstIn,
                    )
                }
            }
            .clip(rowShape)
            .background(rowBg)
            .then(borderModifier)
            .then(
                // In editor mode rows are drag-only: a plain tap must do nothing.
                if (enabled && !editorMode) {
                    Modifier.clickable(onClick = onPick)
                } else {
                    Modifier
                }
            )
            .padding(horizontal = RowPadH),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(entry.iconRes),
            contentDescription = null,
            tint = rowTint,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = if (entry.labelArg != null) stringResource(entry.labelRes, entry.labelArg)
            else stringResource(entry.labelRes),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = rowTint,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Start,
            maxLines = 1,
        )
        if (wobbleStrength > 0.01f) {
            DragHandleGrip(
                tint = rowTint.copy(alpha = 0.65f * wobbleStrength),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
internal fun DragHandleGrip(
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val strokeW = 2.dp.toPx()
        val halfSpan = 5.5.dp.toPx()
        val gapY = 2.5.dp.toPx()
        val cx = size.width * 0.5f
        val cy = size.height * 0.5f

        drawLine(
            color = tint,
            start = Offset(cx - halfSpan, cy - gapY),
            end = Offset(cx + halfSpan, cy - gapY),
            strokeWidth = strokeW,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = tint,
            start = Offset(cx - halfSpan, cy + gapY),
            end = Offset(cx + halfSpan, cy + gapY),
            strokeWidth = strokeW,
            cap = StrokeCap.Round,
        )
    }
}
