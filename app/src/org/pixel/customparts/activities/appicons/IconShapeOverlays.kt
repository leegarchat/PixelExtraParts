package org.pixel.customparts.activities

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Matrix
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.MediaStore
import android.provider.Settings
import android.provider.Telephony
import android.util.PathParser
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp as lerpFloat
import androidx.compose.ui.zIndex
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.pixel.customparts.R
import org.pixel.customparts.dynamicDarkColorScheme
import org.pixel.customparts.dynamicLightColorScheme
import org.pixel.customparts.icons.IconPackManager
import org.pixel.customparts.icons.IconShapeOverlayManager
import org.pixel.customparts.icons.IconShapeOverlayManager.ShapeOption
import org.pixel.customparts.icons.IconShapeOverlayManager.ShapeSource
import org.pixel.customparts.ui.GraphicsLayerRecordingState
import org.pixel.customparts.ui.REBOOT_BUBBLE_CONTENT_BOTTOM_PADDING
import org.pixel.customparts.ui.RebootBubble
import org.pixel.customparts.ui.SettingsGroupCard
import org.pixel.customparts.ui.SliderMorphRoot
import org.pixel.customparts.ui.SliderSetting
import org.pixel.customparts.ui.SliderSettingFloat
import org.pixel.customparts.ui.SliderTitleText
import org.pixel.customparts.ui.TopBarBlurOverlay
import org.pixel.customparts.ui.TopStatusVeil
import org.pixel.customparts.ui.edgeOutline
import org.pixel.customparts.ui.recordLayer
import org.pixel.customparts.ui.rememberGraphicsLayerRecordingState
import org.pixel.customparts.ui.rememberSliderMorphHostState
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

// ── Level-1 Flight Pseudo-Modal A: Shape Card Inspector ──────────────────

@Composable
internal fun ShapeCardFlightOverlay(
    target: ShapeFlightTarget?,
    busy: Boolean,
    scrollBlur: GraphicsLayerRecordingState,
    topInsetPx: Float,
    closeSignal: Int,
    onProgress: (Float) -> Unit,
    onClosingChanged: (Boolean) -> Unit,
    onClose: () -> Unit,
    onApply: (ShapeOption) -> Unit,
    onDelete: (ShapeOption) -> Unit
) {
    val flight = target ?: return
    val option = flight.option
    val haptics = LocalHapticFeedback.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    val flightAnim = remember(flight.sessionId) { Animatable(0f) }
    var closing by remember(flight.sessionId) { mutableStateOf(false) }

    val hostCoordsRef = remember(flight.sessionId) { StudioCoordsRef() }
    val cardCoordsRef = remember(flight.sessionId) { StudioCoordsRef() }
    val flightCoordsRef = remember(flight.sessionId) { StudioCoordsRef() }

    LaunchedEffect(flight.sessionId) {
        onClosingChanged(false)
        withFrameNanos { }
        if (!closing) {
            haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
            flightAnim.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    stiffness = 105f,
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    visibilityThreshold = 0.002f
                )
            )
        }
    }

    fun closeAnimated(afterClose: (() -> Unit)? = null) {
        if (closing) return
        closing = true
        onClosingChanged(true)
        scope.launch {
            flightAnim.stop()
            try {
                flightAnim.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(
                        stiffness = 155f,
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        visibilityThreshold = 0.005f
                    )
                )
            } finally {
                onClosingChanged(false)
                onProgress(0f)
                afterClose?.invoke()
                onClose()
            }
        }
    }

    BackHandler(enabled = !closing) {
        closeAnimated()
    }

    val initialCloseSignal = remember(flight.sessionId) { closeSignal }
    LaunchedEffect(closeSignal) {
        if (closeSignal > initialCloseSignal && !closing) {
            closeAnimated()
        }
    }

    val rawT = flightAnim.value.coerceIn(0f, 1f)
    val morphT = sstepE(rawT, 0f, 1f)
    DisposableEffect(morphT) {
        onProgress(morphT)
        onDispose { }
    }

    val startCardColor = if (option.active) {
        MaterialTheme.colorScheme.surfaceContainerHigh
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }
    val endCardColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val containerColor = lerpColor(startCardColor, endCardColor, sstepE(morphT, 0.15f, 0.78f))
    val scrimAlpha = 0.42f * sstepE(morphT, 0.45f, 0.92f)
    val blurReveal = sstepE(morphT, 0.50f, 0.96f)
    val cornerProgress = sstepE(morphT, 0.35f, 0.88f)
    val modalCorner = lerp(20.dp, 26.dp, cornerProgress)
    val modalShape = RoundedCornerShape(modalCorner)
    val cardBorderColor = containerColor.edgeOutline()

    val flightProgress = sstepE(morphT, 0.0f, 0.96f)
    val contentReveal = sstepE(morphT, 0.20f, 0.88f)

    val topInsetDp = with(density) { topInsetPx.coerceAtLeast(0f).toDp() }
    val navBottomDp = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val navBottomPx = with(density) { navBottomDp.toPx() }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(1f)
    ) {
        val maxW = maxWidth
        val maxH = maxHeight
        val fallbackWpx = with(density) { maxWidth.toPx() }
        val fallbackHpx = with(density) { maxHeight.toPx() }

        val liveSource = flight.sourceBoundsProvider().let {
            if (it.width > 1f && it.height > 1f) it else flight.source
        }
        val endW = (maxW - 16.dp).coerceAtLeast(0.dp)
        val startW = if (liveSource.width > 1f) {
            with(density) { liveSource.width.roundToInt().toDp() }
        } else {
            endW
        }.coerceIn(0.dp, maxW)

        val endH = (maxH - topInsetDp - navBottomDp - 8.dp).coerceAtLeast(220.dp)
        val startH = if (liveSource.height > 1f) {
            with(density) { liveSource.height.roundToInt().toDp() }
        } else {
            endH
        }.coerceIn(120.dp, maxH)

        val cardWidth: Dp = lerp(startW, endW, sstepE(morphT, 0f, 0.90f))
        val cardHeight: Dp = lerp(startH, endH, sstepE(morphT, 0f, 0.92f))

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = scrimAlpha))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { if (!closing) closeAnimated() }
                )
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = topInsetDp, bottom = navBottomDp)
                .onPlaced { hostCoordsRef.value = it }
                .onGloballyPositioned { hostCoordsRef.value = it },
            contentAlignment = Alignment.Center
        ) {
            val hostBounds = hostCoordsRef.value?.boundsInRoot()
            val targetHost = when {
                hostBounds != null && hostBounds.width > 1f && hostBounds.height > 1f -> hostBounds
                else -> Rect(
                    0f,
                    topInsetPx.coerceAtMost(fallbackHpx),
                    fallbackWpx,
                    (fallbackHpx - navBottomPx).coerceAtLeast(topInsetPx + 1f)
                )
            }
            val src = liveSource
            val dockW = 1f - flightProgress
            val cardWpx = with(density) { cardWidth.toPx() }.roundToInt()
            val cardHpx = with(density) { cardHeight.toPx() }.roundToInt().coerceAtLeast(1)
            val hostWpx = targetHost.width.roundToInt()
            val hostHpx = targetHost.height.roundToInt()
            val e0x = (hostWpx - cardWpx) / 2 + cardWpx / 2f - targetHost.width / 2f
            val e0y = (hostHpx - cardHpx) / 2 + cardHpx / 2f - targetHost.height / 2f

            val dx = if (targetHost.width > 1f && src.width > 1f) {
                (src.center.x - targetHost.center.x - e0x) * dockW
            } else 0f
            val dy = if (targetHost.height > 1f && src.height > 1f) {
                (src.center.y - targetHost.center.y - e0y) * dockW
            } else 0f

            val clipViewport = scrollBlur.contentBounds
                .takeIf { it.width > 1f && it.height > 1f }
                ?: Rect(0f, 0f, fallbackWpx, fallbackHpx)

            Box(
                modifier = Modifier
                    .width(cardWidth)
                    .height(cardHeight)
                    .onPlaced { flightCoordsRef.value = it }
                    .graphicsLayer {
                        translationX = dx
                        translationY = dy
                    }
                    .drawWithContent {
                        if (clipViewport.width > 1f && flightProgress < 0.999f) {
                            val w = size.width
                            val h = size.height
                            val rL = src.center.x - w / 2f
                            val rT = src.center.y - h / 2f
                            val keep = 1f - flightProgress
                            clipRect(
                                left = (clipViewport.left - rL).coerceIn(0f, w) * keep,
                                top = (clipViewport.top - rT).coerceIn(0f, h) * keep,
                                right = w - (w - (clipViewport.right - rL).coerceIn(0f, w)) * keep,
                                bottom = h - (h - (clipViewport.bottom - rT).coerceIn(0f, h)) * keep
                            ) {
                                this@drawWithContent.drawContent()
                            }
                        } else {
                            drawContent()
                        }
                    },
                contentAlignment = Alignment.TopCenter
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(modalShape)
                        .border(1.dp, cardBorderColor, modalShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            enabled = !closing,
                            onClick = {}
                        )
                ) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(containerColor)
                    )
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .graphicsLayer {
                                    clip = true
                                    alpha = blurReveal
                                }
                                .onPlaced { cardCoordsRef.value = it }
                                .onGloballyPositioned { cardCoordsRef.value = it }
                                .cardFlightBlurMask(
                                    state = scrollBlur,
                                    coordsProvider = { cardCoordsRef.value }
                                )
                        )
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .background(
                                    containerColor.copy(
                                        alpha = lerpFloat(1f, 0.66f, blurReveal)
                                    )
                                )
                        )
                    }

                    Column(modifier = Modifier.fillMaxSize()) {
                        // 1. 1:1 Morphed Header matching ShapeStudioGridCard
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    enabled = !closing && morphT > 0.5f,
                                    onClick = {
                                        haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                                        closeAnimated()
                                    }
                                )
                                .padding(
                                    start = lerp(16.dp, 20.dp, cornerProgress),
                                    end = lerp(12.dp, 20.dp, cornerProgress),
                                    top = lerp(12.dp, 18.dp, cornerProgress),
                                    bottom = 4.dp
                                ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                SliderTitleText(
                                    title = option.label,
                                    growProgress = sstepE(morphT, 0.08f, 0.78f),
                                    maxGrow = 1.6f,
                                    baseStyle = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Text(
                                    text = shapeSourceLabel(option.source),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (option.active) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Rounded.KeyboardArrowDown,
                                contentDescription = stringResource(R.string.btn_close),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .size(22.dp)
                                    .rotate(lerpFloat(0f, 180f, cornerProgress))
                            )
                        }

                        // 2. Center Blueprint Stage (grows smoothly from 112.dp -> 176.dp)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(lerp(148.dp, 210.dp, cornerProgress)),
                            contentAlignment = Alignment.Center
                        ) {
                            ShapeBlueprintPreview(
                                pathData = option.pathData,
                                active = option.active,
                                modifier = Modifier.size(lerp(112.dp, 172.dp, cornerProgress))
                            )
                        }

                        // 3. Unfolding Showcase Dock & SVG Path Inspector
                        if (contentReveal > 0.005f) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .alpha(contentReveal)
                                    .padding(horizontal = 20.dp)
                                    .verticalScroll(rememberScrollState(), enabled = morphT > 0.7f),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                ShapeMiniDockShowcase(
                                    pathData = option.pathData,
                                    active = option.active
                                )

                                val codeBg = MaterialTheme.colorScheme.surfaceContainerHighest
                                Card(
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = codeBg),
                                    border = BorderStroke(1.dp, codeBg.edgeOutline()),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "config_icon_mask",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontFamily = FontFamily.Monospace
                                            )
                                            IconButton(
                                                onClick = {
                                                    haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                                                    scope.launch {
                                                        clipboard.setClipEntry(
                                                            ClipEntry(
                                                                ClipData.newPlainText(
                                                                    "config_icon_mask",
                                                                    option.pathData
                                                                )
                                                            )
                                                        )
                                                    }
                                                },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.ContentCopy,
                                                    contentDescription = stringResource(R.string.anim_cd_copy),
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = option.pathData,
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 10.sp,
                                                lineHeight = 14.sp
                                            ),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 4,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }

                        // 4. Bottom Action Row (1:1 with card bottom row at morphT = 0f)
                        val isCustomOverlay = option.source == ShapeSource.CUSTOM_OVERLAY
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    start = lerp(12.dp, 20.dp, cornerProgress),
                                    end = lerp(12.dp, 20.dp, cornerProgress),
                                    top = lerp(2.dp, 8.dp, cornerProgress),
                                    bottom = lerp(12.dp, 18.dp, cornerProgress)
                                ),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            StudioTabStyledApplyButton(
                                isActive = option.active,
                                applyLabel = stringResource(R.string.icon_shape_apply),
                                activeLabel = stringResource(R.string.icon_shape_active),
                                enabled = !closing && !busy && !option.active,
                                onClick = {
                                    onApply(option)
                                    closeAnimated()
                                },
                                modifier = Modifier.weight(1f)
                            )

                            if (isCustomOverlay) {
                                val trashArmedOnCard = flight.isTrashArmedProvider()
                                val trashCorner = lerp(
                                    if (trashArmedOnCard) 28.dp else 12.dp,
                                    28.dp,
                                    cornerProgress
                                )
                                val trashBg = MaterialTheme.colorScheme.errorContainer
                                val trashContent = MaterialTheme.colorScheme.onErrorContainer
                                val trashShape = RoundedCornerShape(trashCorner)

                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(trashShape)
                                        .background(trashBg)
                                        .border(1.dp, trashBg.edgeOutline(), trashShape)
                                        .clickable(enabled = !closing && !busy && morphT > 0.5f) {
                                            haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                                            closeAnimated { onDelete(option) }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Delete,
                                        contentDescription = stringResource(R.string.icon_shape_delete),
                                        tint = trashContent,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Tab 2: Import Card & Parametric Constructor with SliderMorph ─────────

@Composable
internal fun ShapeImportCard(
    importedUri: android.net.Uri?,
    importedPath: String,
    isBusy: Boolean,
    onPickFile: () -> Unit,
    onCompileImported: (String) -> Unit
) {
    val isValid = importedPath.isNotBlank() && IconShapeOverlayManager.isValidPath(importedPath)

    SettingsGroupCard(title = stringResource(R.string.icon_shape_import)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StudioXmlFileChipRow(
                label = stringResource(R.string.icon_shape_import),
                uri = importedUri,
                required = true,
                onClick = onPickFile
            )

            AnimatedVisibility(
                visible = isValid,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    ShapeBlueprintPreview(
                        pathData = importedPath,
                        active = true,
                        modifier = Modifier.size(64.dp)
                    )
                    Text(
                        text = importedPath,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            MorphingNameCompileBar(
                buttonLabel = stringResource(R.string.icon_shape_import_apply),
                placeholderLabel = stringResource(R.string.icon_shape_custom_name),
                enabled = isValid && !isBusy,
                onCompile = onCompileImported
            )
        }
    }
}

