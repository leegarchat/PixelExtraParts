package org.pixel.customparts.activities

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.ui.draw.rotate
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.util.lerp as lerpFloat
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.launch
import org.pixel.customparts.R
import org.pixel.customparts.icons.AppIconCandidate
import org.pixel.customparts.icons.AppIconShapeOverrides
import org.pixel.customparts.icons.IconApplyProgress
import org.pixel.customparts.icons.IconApplyResult
import org.pixel.customparts.icons.IconPackApplyStatus
import org.pixel.customparts.icons.IconPackEntry
import org.pixel.customparts.icons.IconPackManager
import org.pixel.customparts.icons.IconPackPreviewItem
import org.pixel.customparts.icons.IconShapeAreaConfig
import org.pixel.customparts.ui.ColorWheel
import org.pixel.customparts.ui.GenericSwitchRow
import org.pixel.customparts.ui.GraphicsLayerRecordingState
import org.pixel.customparts.ui.SliderTitleText
import org.pixel.customparts.ui.edgeOutline
import kotlin.math.roundToInt

// ── Segmented Tabs for AppIcons (No Checkmarks) ─────────────────────────

@Composable
internal fun AppIconsTabs(
    titles: List<String>,
    position: Float,
    onTabClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    pagerState: StudioPagerState? = null
) {
    if (titles.isEmpty()) return
    val haptics = LocalHapticFeedback.current
    val clampedPos = position.coerceIn(0f, (titles.size - 1).toFloat())

    val isDirectJump = pagerState != null && pagerState.isDirectJumpActive
    val jumpFrom = pagerState?.jumpFromPage ?: 0
    val jumpTo = pagerState?.jumpToPage ?: 0
    val jumpP = pagerState?.jumpFraction ?: 1f

    val selectedBg = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.68f)
    val idleBg = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.52f)
    val selectedContent = MaterialTheme.colorScheme.onPrimaryContainer
    val idleContent = MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        titles.forEachIndexed { index, label ->
            val selFraction = if (isDirectJump) {
                when (index) {
                    jumpFrom -> (1f - jumpP).coerceIn(0f, 1f)
                    jumpTo -> jumpP.coerceIn(0f, 1f)
                    else -> 0f
                }
            } else {
                (1f - kotlin.math.abs(clampedPos - index)).coerceIn(0f, 1f)
            }

            val cornerRadius = lerp(12.dp, 28.dp, selFraction)
            val bgColor = lerpColor(idleBg, selectedBg, selFraction)
            val contentColor = lerpColor(idleContent, selectedContent, selFraction)
            val shape = RoundedCornerShape(cornerRadius)
            val isMostlySelected = selFraction >= 0.5f

            Row(
                modifier = Modifier
                    .weight(1f + 0.15f * selFraction)
                    .height(46.dp)
                    .clip(shape)
                    .background(bgColor)
                    .border(1.dp, bgColor.edgeOutline(alpha = 0.38f), shape)
                    .clickable {
                        haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                        onTabClick(index)
                    }
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (isMostlySelected) FontWeight.Bold else FontWeight.Medium,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

// ── Per-App Shape Settings Editor (with AppIconsTabs & SliderMorph) ──────


@Composable
internal fun PerAppShapeSettingsCard(
    overrides: AppIconShapeOverrides,
    onOverridesChange: (AppIconShapeOverrides) -> Unit,
    onSave: () -> Unit,
    onReset: () -> Unit
) {
    var areaTab by rememberSaveable { mutableIntStateOf(0) }
    val animatedAreaPos by animateFloatAsState(
        targetValue = areaTab.toFloat(),
        animationSpec = spring(
            stiffness = Spring.StiffnessMediumLow,
            dampingRatio = Spring.DampingRatioNoBouncy
        ),
        label = "PerAppAreaTabPos"
    )

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        AppIconsTabs(
            titles = listOf(
                stringResource(R.string.app_icons_per_app_system_area),
                stringResource(R.string.app_icons_per_app_notification_area),
                stringResource(R.string.app_icons_per_app_launcher_area)
            ),
            position = animatedAreaPos,
            onTabClick = { areaTab = it }
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            AnimatedContent(
                targetState = areaTab,
                transitionSpec = {
                    val dir = if (targetState > initialState) 1 else -1
                    (slideInHorizontally(
                        animationSpec = spring(
                            stiffness = Spring.StiffnessMediumLow,
                            dampingRatio = Spring.DampingRatioNoBouncy
                        )
                    ) { fullW -> dir * fullW } + fadeIn(tween(180))) togetherWith
                        (slideOutHorizontally(
                            animationSpec = spring(
                                stiffness = Spring.StiffnessMediumLow,
                                dampingRatio = Spring.DampingRatioNoBouncy
                            )
                        ) { fullW -> -dir * fullW } + fadeOut(tween(140)))
                },
                label = "PerAppShapeAreaSlide"
            ) { tab ->
                when (tab) {
                    0 -> PerAppShapeAreaEditor(
                        config = overrides.system ?: IconShapeAreaConfig(),
                        onConfigChange = { onOverridesChange(overrides.copy(system = it)) }
                    )
                    1 -> PerAppShapeAreaEditor(
                        config = overrides.notification ?: IconShapeAreaConfig(),
                        onConfigChange = { onOverridesChange(overrides.copy(notification = it)) }
                    )
                    else -> PerAppShapeAreaEditor(
                        config = overrides.launcher ?: IconShapeAreaConfig(),
                        onConfigChange = { onOverridesChange(overrides.copy(launcher = it)) }
                    )
                }
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        ) {
            SecondaryActionPill(
                icon = Icons.Rounded.Refresh,
                label = stringResource(R.string.app_icons_reset_individual_settings),
                enabled = true,
                onClick = onReset,
                modifier = Modifier.weight(1f)
            )
            SecondaryActionPill(
                icon = Icons.Rounded.Check,
                label = stringResource(R.string.app_icons_save_individual_settings),
                enabled = true,
                onClick = onSave,
                modifier = Modifier.weight(1f),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun PerAppShapeAreaEditor(
    config: IconShapeAreaConfig,
    onConfigChange: (IconShapeAreaConfig) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        GenericSwitchRow(
            title = stringResource(R.string.app_icons_per_app_stretch_shape),
            summary = stringResource(R.string.app_icons_per_app_stretch_shape_summary),
            checked = config.stretchShape,
            enabled = true,
            onCheckedChange = { checked ->
                onConfigChange(
                    config.copy(
                        stretchShape = checked,
                        removeShape = if (checked) false else config.removeShape
                    )
                )
            }
        )
        HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))
        AppIconsScaleSliderRow(
            title = stringResource(R.string.app_icons_per_app_shape_scale),
            value = config.scalePercent,
            enabled = config.stretchShape && !config.removeShape,
            onValueChange = { value -> onConfigChange(config.copy(scalePercent = value)) }
        )
        HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))
        GenericSwitchRow(
            title = stringResource(R.string.app_icons_per_app_remove_shape),
            summary = stringResource(R.string.app_icons_per_app_remove_shape_summary),
            checked = config.removeShape,
            enabled = true,
            onCheckedChange = { checked ->
                onConfigChange(
                    config.copy(
                        removeShape = checked,
                        stretchShape = if (checked) false else config.stretchShape
                    )
                )
            }
        )
    }
}

// ── Frosted Color Picker Pseudo-Modal (1:1 Flight from Tint Row) ─────────

@Stable
internal data class ColorPickerFlightRequest(
    val title: String,
    val initialColor: Int,
    val showAlpha: Boolean = true,
    val sourceRect: Rect = Rect.Zero,
    val sourceBoundsProvider: () -> Rect = { sourceRect },
    val onColorSelected: (Int) -> Unit,
    val sessionId: Long = System.nanoTime()
)

@Composable
internal fun ColorPickerFlightOverlay(
    request: ColorPickerFlightRequest?,
    onClose: () -> Unit,
    scrollBlur: GraphicsLayerRecordingState,
    onProgress: (Float) -> Unit = {},
    topInsetPx: Float = 0f,
    closeSignal: Int = 0
) {
    val req = request ?: return
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val modalScrollState = rememberScrollState()

    val flightAnim = remember(req.sessionId) { Animatable(0f) }
    var closing by remember(req.sessionId) { mutableStateOf(false) }
    val hostCoordsRef = remember(req.sessionId) { StudioCoordsRef() }
    val cardCoordsRef = remember(req.sessionId) { StudioCoordsRef() }
    val flightCoordsRef = remember(req.sessionId) { StudioCoordsRef() }

    var alpha by remember(req.sessionId) {
        mutableFloatStateOf(AndroidColor.alpha(req.initialColor) / 255f)
    }
    val hsv = remember(req.sessionId) {
        val arr = FloatArray(3)
        AndroidColor.colorToHSV(req.initialColor, arr)
        mutableStateListOf(arr[0], arr[1], arr[2])
    }
    var currentColor by remember(req.sessionId) {
        mutableStateOf(Color(req.initialColor))
    }

    LaunchedEffect(req.sessionId) {
        withFrameNanos { }
        if (!closing) {
            haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
            flightAnim.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    stiffness = 110f,
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    visibilityThreshold = 0.002f
                )
            )
        }
    }

    fun closeAnimated(afterClose: (() -> Unit)? = null) {
        if (closing) return
        closing = true
        scope.launch {
            flightAnim.stop()
            try {
                flightAnim.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(
                        stiffness = 160f,
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        visibilityThreshold = 0.05f
                    )
                )
            } finally {
                onProgress(0f)
                afterClose?.invoke()
                onClose()
            }
        }
    }

    BackHandler(enabled = !closing) {
        closeAnimated()
    }

    val initialCloseSignal = remember(req.sessionId) { closeSignal }
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

    val startBg = MaterialTheme.colorScheme.surfaceContainerHigh
    val endBg = MaterialTheme.colorScheme.surfaceContainerHigh
    val containerColor = lerpColor(startBg, endBg, sstepE(morphT, 0.1f, 0.7f))

    val scrimAlpha = 0.42f * sstepE(morphT, 0.45f, 0.92f)
    val blurReveal = sstepE(morphT, 0.45f, 0.96f)
    val cornerProgress = sstepE(morphT, 0.25f, 0.85f)
    val modalCorner = lerp(16.dp, 26.dp, cornerProgress)
    val modalShape = RoundedCornerShape(modalCorner)
    val cardBorderColor = containerColor.edgeOutline()

    val flightProgress = sstepE(morphT, 0.0f, 0.96f)
    val bodyUnfold = sstepE(morphT, 0.18f, 0.88f)

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

        val liveSource = req.sourceBoundsProvider().let {
            if (it.width > 1f && it.height > 1f) it else req.sourceRect
        }
        val endW = (maxW - 32.dp).coerceAtLeast(0.dp)
        val startW = if (liveSource.width > 1f) {
            with(density) { liveSource.width.roundToInt().toDp() }
        } else {
            endW
        }.coerceIn(0.dp, maxW)

        val cardWidth: Dp = lerp(startW, endW, sstepE(morphT, 0f, 0.90f))
        val visibleMaxCardH = (maxH - topInsetDp - navBottomDp - 24.dp).coerceAtLeast(180.dp)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = scrimAlpha))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = !closing,
                    onClick = { closeAnimated() }
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
            val cardHpx = flightCoordsRef.value?.takeIf { it.isAttached }?.size?.height
                ?.takeIf { it > 0 }
                ?: src.height.roundToInt().coerceAtLeast(1)
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

            Box(
                modifier = Modifier
                    .width(cardWidth)
                    .heightIn(max = visibleMaxCardH)
                    .onPlaced { flightCoordsRef.value = it }
                    .graphicsLayer {
                        translationX = dx
                        translationY = dy
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
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

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(modalScrollState, enabled = morphT > 0.7f)
                            .padding(
                                horizontal = lerp(14.dp, 22.dp, cornerProgress),
                                vertical = lerp(10.dp, 20.dp, cornerProgress)
                            ),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Top Row: Swatch + Title + Hex
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(lerp(32.dp, 40.dp, cornerProgress))
                                    .clip(CircleShape)
                                    .background(currentColor)
                                    .border(1.dp, currentColor.edgeOutline(alpha = 0.75f), CircleShape)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                SliderTitleText(
                                    title = req.title,
                                    growProgress = sstepE(morphT, 0.08f, 0.78f),
                                    maxGrow = 1.45f,
                                    baseStyle = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Text(
                                    text = String.format("#%08X", currentColor.toArgb()),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        // Unfolding ColorWheel + Brightness/Alpha Sliders + Actions
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .unfoldVerticalSlot(bodyUnfold)
                                .alpha(bodyUnfold),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Spacer(Modifier.height(18.dp))

                            ColorWheel(
                                hue = hsv[0],
                                saturation = hsv[1],
                                value = hsv[2],
                                onColorChange = { h, s, v ->
                                    hsv[0] = h
                                    hsv[1] = s
                                    hsv[2] = v
                                    currentColor = Color.hsv(h, s, v, alpha)
                                }
                            )

                            Spacer(Modifier.height(18.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = stringResource(R.string.icon_shape_brightness),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "${(hsv[2] * 100).roundToInt()}%",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Slider(
                                value = hsv[2],
                                onValueChange = {
                                    hsv[2] = it
                                    currentColor = Color.hsv(hsv[0], hsv[1], it, alpha)
                                }
                            )

                            if (req.showAlpha) {
                                Spacer(Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = stringResource(R.string.anim_param_alpha),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "${(alpha * 100).roundToInt()}%",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Slider(
                                    value = alpha,
                                    onValueChange = {
                                        alpha = it
                                        currentColor = Color.hsv(hsv[0], hsv[1], hsv[2], alpha)
                                    }
                                )
                            }

                            Spacer(Modifier.height(16.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val cancelBg = MaterialTheme.colorScheme.surfaceContainerHighest
                                val cancelShape = RoundedCornerShape(14.dp)
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(46.dp)
                                        .clip(cancelShape)
                                        .background(cancelBg)
                                        .border(1.dp, cancelBg.edgeOutline(), cancelShape)
                                        .clickable(enabled = !closing && morphT > 0.5f) {
                                            haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                                            closeAnimated()
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = stringResource(R.string.btn_cancel),
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                StudioTabStyledApplyButton(
                                    isActive = false,
                                    applyLabel = stringResource(R.string.btn_apply),
                                    activeLabel = stringResource(R.string.btn_apply),
                                    enabled = !closing && morphT > 0.5f,
                                    onClick = {
                                        val picked = currentColor.toArgb()
                                        closeAnimated {
                                            req.onColorSelected(picked)
                                        }
                                    },
                                    modifier = Modifier.weight(1.3f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Common List, Grid & Dialog Components ────────────────────────────────

@Composable
internal fun SelectableIconEntryRow(
    entry: IconPackEntry,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val bgColor = if (checked) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
    } else {
        MaterialTheme.colorScheme.surface
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        border = BorderStroke(1.dp, bgColor.edgeOutline())
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PreviewIcon(entry.iconPackPackage, entry.drawableName, 42.dp)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = entry.label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = entry.appPackageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Checkbox(
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = Modifier.padding(start = 12.dp)
            )
        }
    }
}

@Composable
internal fun AppIconCandidateRow(
    candidate: AppIconCandidate,
    applied: Boolean = false,
    busy: Boolean = false,
    onApply: () -> Unit
) {
    val bgColor = MaterialTheme.colorScheme.surface
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        border = BorderStroke(1.dp, bgColor.edgeOutline())
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PreviewIcon(candidate.iconPackPackage, candidate.drawableName, 42.dp)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = candidate.iconPackLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = candidate.drawableName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            StudioTabStyledApplyButton(
                isActive = applied,
                applyLabel = stringResource(R.string.btn_apply),
                activeLabel = stringResource(R.string.app_icons_applied_button),
                enabled = !applied && !busy,
                onClick = onApply
            )
        }
    }
}

@Composable
internal fun IconPreviewGrid(items: List<IconPackPreviewItem>, modifier: Modifier = Modifier) {
    val rows = remember(items) { items.chunked(4) }
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(rows) { rowItems ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowItems.forEach { item ->
                    IconPreviewCell(item = item, modifier = Modifier.weight(1f))
                }
                repeat(4 - rowItems.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun IconPreviewCell(item: IconPackPreviewItem, modifier: Modifier = Modifier) {
    val bgColor = MaterialTheme.colorScheme.surface
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(bgColor)
            .border(1.dp, bgColor.edgeOutline(), shape)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        PreviewIcon(item.iconPackPackage, item.drawableName, 44.dp)
        Spacer(Modifier.height(6.dp))
        Text(
            text = item.label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
internal fun AppIconsDialog(
    title: String,
    subtitle: String?,
    onDismiss: () -> Unit,
    footer: @Composable RowScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val bgColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val shape = RoundedCornerShape(24.dp)
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f),
            shape = shape,
            color = bgColor,
            border = BorderStroke(1.dp, bgColor.edgeOutline()),
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (!subtitle.isNullOrBlank()) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, stringResource(R.string.btn_close))
                    }
                }
                Spacer(Modifier.height(12.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    content()
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                    content = footer
                )
            }
        }
    }
}

@Composable
internal fun AppIconsSearchField(
    query: String,
    hint: String,
    onQueryChange: (String) -> Unit
) {
    val bgColor = MaterialTheme.colorScheme.surface
    val shape = RoundedCornerShape(18.dp)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        color = bgColor,
        border = BorderStroke(1.dp, bgColor.edgeOutline())
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (query.isEmpty()) {
                    Text(
                        text = hint,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (query.isNotEmpty()) {
                IconButton(
                    onClick = { onQueryChange("") },
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.btn_close),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
internal fun LoadingDialogContent() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 140.dp)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
internal fun EmptyStateText(text: String) {
    val containerColor = MaterialTheme.colorScheme.surface
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, containerColor.edgeOutline())
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(20.dp)
        )
    }
}

@Composable
internal fun PreviewIcon(iconPackPackage: String, drawableName: String, size: Dp) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val sizePx = with(density) { size.roundToPx() }
    var bitmap by remember(iconPackPackage, drawableName, sizePx) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(iconPackPackage, drawableName, sizePx) {
        bitmap = IconPackManager.loadIconBitmap(context, iconPackPackage, drawableName, sizePx)
    }

    BitmapIcon(bitmap = bitmap, size = size, fallback = Icons.Rounded.Apps)
}

@Composable
internal fun DrawableIcon(drawable: Drawable?, size: Dp, fallback: ImageVector) {
    val density = LocalDensity.current
    val sizePx = with(density) { size.roundToPx() }
    val bitmap = remember(drawable, sizePx) {
        runCatching {
            drawable?.toBitmap(
                width = sizePx.coerceAtLeast(1),
                height = sizePx.coerceAtLeast(1),
                config = Bitmap.Config.ARGB_8888
            )
        }.getOrNull()
    }
    BitmapIcon(bitmap = bitmap, size = size, fallback = fallback)
}

@Composable
internal fun BitmapIcon(bitmap: Bitmap?, size: Dp, fallback: ImageVector) {
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.size(size)
        )
    } else {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = fallback,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(size * 0.52f)
            )
        }
    }
}

@Composable
internal fun iconPackStatusText(status: IconPackApplyStatus): String {
    return when (status) {
        IconPackApplyStatus.NOT_APPLIED -> stringResource(R.string.app_icons_status_not_applied)
        IconPackApplyStatus.APPLIED_FULL -> stringResource(R.string.app_icons_status_full)
        IconPackApplyStatus.APPLIED_PARTIAL -> stringResource(R.string.app_icons_status_partial)
    }
}