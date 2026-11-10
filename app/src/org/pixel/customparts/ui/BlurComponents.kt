package org.pixel.customparts.ui

import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CanvasHolder
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

/*
 * Single shared full-screen blur RenderNode: the RenderEffect is applied
 * exactly once per frame inside recordLayer, while overlays (header, bottom bar,
 * navigate button) draw the ready blurred node as a shifted mask.
 */

@Stable
class GraphicsLayerRecordingState(
    val graphicsLayer: androidx.compose.ui.graphics.layer.GraphicsLayer,
) {
    val sharedBlurNode: RenderNode? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) RenderNode("SharedFullScreenBlur") else null
    var lastAppliedBlurRadius: Float = -1f
}

@Composable
fun rememberGraphicsLayerRecordingState(): GraphicsLayerRecordingState {
    val graphicsLayer = rememberGraphicsLayer()
    return remember(graphicsLayer) {
        GraphicsLayerRecordingState(graphicsLayer = graphicsLayer)
    }
}

fun Modifier.recordLayer(
    state: GraphicsLayerRecordingState,
    backgroundColor: Color,
    blurRadius: Float = 40f,
    topEdgeFadeHeight: Dp = 28.dp,
    bottomEdgeFadeHeight: Dp = 20.dp,
): Modifier = composed {
    val canvasHolder = remember { CanvasHolder() }

    this.drawWithContent {
        val w = size.width.toInt()
        val h = size.height.toInt()
        if (w > 0 && h > 0) {
            val topFadePx = topEdgeFadeHeight.toPx().coerceAtMost(h * 0.25f)
            val bottomFadePx = bottomEdgeFadeHeight.toPx().coerceAtMost(h * 0.25f)

            try {
                // 1. Record the content tree exactly ONCE
                state.graphicsLayer.record(size = IntSize(w, h)) {
                    this@drawWithContent.drawContent()
                }

                // 2. Bake the shared blur node S from the recorded layer C
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && blurRadius > 0.5f) {
                    val blurNode = state.sharedBlurNode!!
                    blurNode.setPosition(0, 0, w, h)
                    if (kotlin.math.abs(state.lastAppliedBlurRadius - blurRadius) > 0.25f) {
                        blurNode.setRenderEffect(
                            RenderEffect.createBlurEffect(blurRadius, blurRadius, Shader.TileMode.CLAMP)
                        )
                        state.lastAppliedBlurRadius = blurRadius
                    }
                    val recCanvas = blurNode.beginRecording(w, h)
                    canvasHolder.drawInto(recCanvas) {
                        drawRect(color = backgroundColor)
                        val orig = drawContext.canvas
                        drawContext.canvas = this@drawInto
                        this@drawWithContent.drawLayer(state.graphicsLayer)
                        drawContext.canvas = orig

                        if (topFadePx > 0f) {
                            drawRect(
                                brush = Brush.verticalGradient(
                                    colors = listOf(backgroundColor, backgroundColor.copy(alpha = 0f)),
                                    startY = 0f,
                                    endY = topFadePx,
                                ),
                                topLeft = Offset.Zero,
                                size = Size(size.width, topFadePx),
                            )
                        }
                        if (bottomFadePx > 0f) {
                            val startY = size.height - bottomFadePx
                            drawRect(
                                brush = Brush.verticalGradient(
                                    colors = listOf(backgroundColor.copy(alpha = 0f), backgroundColor),
                                    startY = startY,
                                    endY = size.height,
                                ),
                                topLeft = Offset(0f, startY),
                                size = Size(size.width, bottomFadePx),
                            )
                        }
                    }
                    blurNode.endRecording()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            // 3. Draw the recorded layer C to the screen without a second Compose tree traversal
            drawLayer(state.graphicsLayer)
        } else {
            drawContent()
        }
    }
}

/**
 * Renders a cutout (mask) from the already prepared [state.sharedBlurNode].
 * When [scale] != 1 the outer capsule grows while the blur texture stays 1:1 with the screen:
 * an inverse 1/s matrix is applied around the element center.
 * Creates no new RenderNodes and duplicates no RenderEffects!
 */
@RequiresApi(Build.VERSION_CODES.S)
private fun Modifier.renderLayerAt(
    state: GraphicsLayerRecordingState,
    regionProvider: () -> Rect,
    scaleProvider: () -> Float = { 1f },
): Modifier = this.drawBehind {
    val blurNode = state.sharedBlurNode ?: return@drawBehind
    if (!blurNode.hasDisplayList()) return@drawBehind
    val region = regionProvider()
    val s = scaleProvider().coerceAtLeast(0.01f)

    drawIntoCanvas { canvas ->
        val native = canvas.nativeCanvas
        val saveCount = native.save()
        if (kotlin.math.abs(s - 1f) > 0.001f) {
            // Compensate the outer scale(s, s) around the element center:
            // the capsule mask expands by s while the blur texture stays 1:1 with the screen!
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

/**
 * Uniform frosted veil: keeps [minVeilAlpha] at high blur to remove blotchiness
 * and smoothly reaches 1.0f (opaque background) as the blur radius drops to 0.
 */
private fun frostedVeilAlpha(blurRadius: Float, minVeilAlpha: Float): Float {
    val t = ((20f - blurRadius) / 20f).coerceIn(0f, 1f)
    return minVeilAlpha + (1f - minVeilAlpha) * t
}

@Composable
fun TopBarBlurOverlay(
    modifier: Modifier = Modifier,
    topBarHeight: Dp,
    blurState: GraphicsLayerRecordingState,
    blurRadius: Float = 40f,
) {
    val surfaceColor = MaterialTheme.colorScheme.surfaceContainer
    val veilAlpha = frostedVeilAlpha(blurRadius = blurRadius, minVeilAlpha = 0.58f)
    var region by remember { mutableStateOf(Rect.Zero) }

    Box(
        modifier = modifier.height(topBarHeight).zIndex(2f)
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && blurRadius > 0.5f) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(surfaceColor)
            )

            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        clip = true
                    }
                    .onGloballyPositioned { coords ->
                        region = coords.boundsInRoot()
                    }
                    .renderLayerAt(
                        state = blurState,
                        regionProvider = { region },
                    )
            )

            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(surfaceColor.copy(alpha = veilAlpha))
            )
        } else {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(surfaceColor)
            )
        }
    }
}

@Composable
fun BottomBlurBar(
    modifier: Modifier = Modifier,
    blurState: GraphicsLayerRecordingState,
    blurRadius: Float = 40f,
    scale: Float = 1f,
    shape: Shape = CircleShape,
    content: @Composable () -> Unit,
) {
    val bgColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val veilColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
    val veilAlpha = frostedVeilAlpha(blurRadius = blurRadius, minVeilAlpha = 0.62f)
    var region by remember { mutableStateOf(Rect.Zero) }

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .border(width = 1.dp, color = borderColor, shape = shape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            ),
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && blurRadius > 0.5f) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(bgColor)
            )

            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        clip = true
                    }
                    .onPlaced { coords ->
                        region = coords.boundsInRoot()
                    }
                    .onGloballyPositioned { coords ->
                        region = coords.boundsInRoot()
                    }
                    .renderLayerAt(
                        state = blurState,
                        regionProvider = { region },
                        scaleProvider = { scale },
                    )
            )

            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(veilColor.copy(alpha = veilAlpha))
            )
        } else {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(bgColor)
            )
        }

        content()
    }
}

/**
 * Gradient dim seated directly BELOW the header, never over it.
 *
 * Smooths the seam between the clean frosted header and the morph-scrimmed list:
 * a scrim-token gradient, darkest at the header edge and fading to transparent
 * below. Peak alpha follows the morph modal: 0 when closed (the composable bails
 * out and adds no layer), up to [maxPeakAlpha] when the scrim is fully up, so the
 * modal scrim + this veil never stack into mud.
 * Only reads [LocalSliderMorphHost]: morph keys/recording/logic are untouched.
 * z = 1.5f: above the modal scrim (1f), below the header (2f) and TopStatusVeil
 * (3f). No input handling: taps fall through to the scrim catcher underneath.
 */
@Composable
fun TopBarUnderDim(
    topBarHeight: Dp,
    modifier: Modifier = Modifier,
    blendHeight: Dp = 56.dp,
    maxPeakAlpha: Float = 0.20f,
) {
    val morphOpen = LocalSliderMorphHost.current?.request != null
    val peak by animateFloatAsState(
        targetValue = if (morphOpen) maxPeakAlpha else 0f,
        animationSpec = tween(
            durationMillis = if (morphOpen) 240 else 220,
            easing = FastOutSlowInEasing,
        ),
        label = "TopBarUnderDim",
    )
    if (peak <= 0.003f) return
    val scrim = MaterialTheme.colorScheme.scrim
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = topBarHeight)
            .height(blendHeight)
            .zIndex(1.5f)
            .background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.0f to scrim.copy(alpha = peak),
                        0.55f to scrim.copy(alpha = peak * 0.45f),
                        1.0f to scrim.copy(alpha = 0f),
                    ),
                ),
            ),
    )
}

/**
 * Micro-gradient veil at the top edge (status-bar zone).
 * Hides blur softness at content edges where there is no sampling data.
 * Alpha bottom-up: 0 → 20 → 40 → 100. Color = content background (dark/light per theme).
 * Drawn as a recordLayer sibling, only over the header blur overlay.
 */
@Composable
fun TopStatusVeil(
    backgroundColor: Color,
    veilHeight: Dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(veilHeight)
            .zIndex(3f)
            .background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.0f to backgroundColor,
                        0.55f to backgroundColor.copy(alpha = 0.4f),
                        0.8f to backgroundColor.copy(alpha = 0.2f),
                        1.0f to backgroundColor.copy(alpha = 0f),
                    )
                )
            )
    )
}
