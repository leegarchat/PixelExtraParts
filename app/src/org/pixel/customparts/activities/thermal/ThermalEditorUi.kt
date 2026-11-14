package org.pixel.customparts.activities

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.util.lerp as lerpFloat
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch
import org.pixel.customparts.R
import androidx.compose.material.icons.rounded.Code
import org.json.JSONArray
import org.pixel.customparts.ui.ExpressiveStepButton
import org.pixel.customparts.ui.GraphicsLayerRecordingState
import org.pixel.customparts.ui.SettingsGroupCard
import org.pixel.customparts.ui.SliderTitleText
import org.pixel.customparts.ui.edgeOutline
import java.util.Locale
import kotlin.math.roundToInt

@Stable
internal data class ThermalParamFlightRequest(
    val param: ThermalConfigParam,
    val depth: Int = 2,
    val sourceRect: Rect = Rect.Zero,
    val sourceBoundsProvider: () -> Rect = { sourceRect },
    val sessionId: Long = System.nanoTime()
)

@Composable
internal fun EditorHeaderCard(
    source: String,
    profileName: String,
    onProfileNameChange: (String) -> Unit,
    fileNameInput: String,
    onFileNameInputChange: (String) -> Unit,
    selectedTab: ThermalEditorTab,
    onTabSelected: (ThermalEditorTab) -> Unit,
    onSave: () -> Unit,
    onSaveAndApply: () -> Unit
) {
    val tabIndex = ThermalEditorTab.entries.indexOf(selectedTab).coerceAtLeast(0)
    val animatedTabPos by animateFloatAsState(
        targetValue = tabIndex.toFloat(),
        animationSpec = spring(
            stiffness = Spring.StiffnessMediumLow,
            dampingRatio = Spring.DampingRatioNoBouncy
        ),
        label = "ThermalEditorTabPos"
    )

    SettingsGroupCard(title = stringResource(R.string.thermal_manager_editor_title)) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.thermal_manager_source, source),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = profileName,
                onValueChange = onProfileNameChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                label = { Text(stringResource(R.string.thermal_manager_profile_name)) }
            )
            OutlinedTextField(
                value = fileNameInput,
                onValueChange = onFileNameInputChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                label = { Text(stringResource(R.string.thermal_manager_file_name_optional)) },
                supportingText = { Text(stringResource(R.string.thermal_manager_file_name_help)) }
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryActionPill(
                    icon = Icons.Rounded.Save,
                    label = stringResource(R.string.thermal_manager_save),
                    enabled = true,
                    onClick = onSave,
                    modifier = Modifier.weight(1f)
                )
                SecondaryActionPill(
                    icon = Icons.Rounded.CheckCircle,
                    label = stringResource(R.string.thermal_manager_save_apply),
                    enabled = true,
                    onClick = onSaveAndApply,
                    modifier = Modifier.weight(1.2f),
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        AppIconsTabs(
            titles = ThermalEditorTab.entries.map { stringResource(it.labelRes) },
            position = animatedTabPos,
            onTabClick = { idx ->
                ThermalEditorTab.entries.getOrNull(idx)?.let(onTabSelected)
            }
        )
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
internal fun ThermalParamTreeCard(
    branches: List<ThermalParamTreeNode>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    visibleCount: Int,
    totalCount: Int,
    expandedNodeIds: Set<String>,
    forceExpanded: Boolean,
    activeFlightParamId: String? = null,
    onToggleNode: (String) -> Unit,
    onOpenParamFlight: (ThermalConfigParam, Int, Rect, () -> Rect) -> Unit
) {
    SettingsGroupCard(title = stringResource(R.string.thermal_manager_editor_tree_title)) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ThermalCompactSearchField(
                query = searchQuery,
                onQueryChange = onSearchQueryChange,
                hint = stringResource(R.string.thermal_manager_search_hint)
            )
            Text(
                text = stringResource(R.string.thermal_manager_search_count, visibleCount, totalCount),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (branches.isEmpty()) {
            Text(
                text = stringResource(R.string.thermal_manager_editor_tree_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
            )
        } else {
            Column(modifier = Modifier.padding(bottom = 6.dp)) {
                branches.forEach { node ->
                    ThermalTreeNodeRow(
                        node = node,
                        depth = 0,
                        expandedNodeIds = expandedNodeIds,
                        forceExpanded = forceExpanded,
                        activeFlightParamId = activeFlightParamId,
                        onToggleNode = onToggleNode,
                        onOpenParamFlight = onOpenParamFlight
                    )
                }
            }
        }
    }
}

@Composable
private fun ThermalTreeNodeRow(
    node: ThermalParamTreeNode,
    depth: Int,
    expandedNodeIds: Set<String>,
    forceExpanded: Boolean,
    activeFlightParamId: String?,
    onToggleNode: (String) -> Unit,
    onOpenParamFlight: (ThermalConfigParam, Int, Rect, () -> Rect) -> Unit
) {
    val expanded = forceExpanded || node.id in expandedNodeIds
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggleNode(node.id) }
                .padding(start = (20 + depth * 14).dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = node.title,
                style = if (depth == 0) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
                color = if (depth == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = node.leafCount.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(
                animationSpec = spring(
                    stiffness = Spring.StiffnessMediumLow,
                    dampingRatio = Spring.DampingRatioNoBouncy
                )
            ) + fadeIn(),
            exit = shrinkVertically(
                animationSpec = spring(
                    stiffness = Spring.StiffnessMediumLow,
                    dampingRatio = Spring.DampingRatioNoBouncy
                )
            ) + fadeOut()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                node.children.forEach { child ->
                    ThermalTreeNodeRow(
                        node = child,
                        depth = depth + 1,
                        expandedNodeIds = expandedNodeIds,
                        forceExpanded = forceExpanded,
                        activeFlightParamId = activeFlightParamId,
                        onToggleNode = onToggleNode,
                        onOpenParamFlight = onOpenParamFlight
                    )
                }
                node.params.forEach { param ->
                    val paramDepth = depth + 1
                    ThermalTreeParamRow(
                        param = param,
                        depth = paramDepth,
                        isHiddenForFlight = activeFlightParamId == param.id,
                        onOpenFlight = { bounds, provider ->
                            onOpenParamFlight(param, paramDepth, bounds, provider)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ThermalTreeParamRow(
    param: ThermalConfigParam,
    depth: Int,
    isHiddenForFlight: Boolean,
    onOpenFlight: (Rect, () -> Rect) -> Unit
) {
    val haptics = LocalHapticFeedback.current
    var rowBounds by remember { mutableStateOf(Rect.Zero) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .onPlaced {
                val r = it.unclippedBoundsInRoot()
                if (r.width > 1f && r.height > 1f) rowBounds = r
            }
            .onGloballyPositioned {
                val r = it.unclippedBoundsInRoot()
                if (r.width > 1f && r.height > 1f) rowBounds = r
            }
            .alpha(if (isHiddenForFlight) 0f else 1f)
            .clickable(enabled = !isHiddenForFlight) {
                haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                onOpenFlight(rowBounds) { rowBounds }
            }
            .padding(start = (20 + depth * 14).dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(end = 6.dp)
                .size(16.dp)
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = param.key,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(6.dp))
                FieldBadge(param)
            }
            Text(
                text = param.value.previewValue(),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(6.dp))
        IconButton(
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                onOpenFlight(rowBounds) { rowBounds }
            },
            modifier = Modifier.size(34.dp)
        ) {
            Icon(
                Icons.Rounded.Info,
                contentDescription = stringResource(R.string.thermal_manager_info),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

// ── 1:1 Full-Height Flight Pseudo-Modal: Tree Parameter Editor ───────────

@Composable
internal fun ThermalParamFlightOverlay(
    request: ThermalParamFlightRequest?,
    scrollBlur: GraphicsLayerRecordingState,
    topInsetPx: Float,
    closeSignal: Int,
    onProgress: (Float) -> Unit,
    onClose: () -> Unit,
    onSave: (ThermalConfigParam, String) -> Unit
) {
    val req = request ?: return
    val param = req.param
    val haptics = LocalHapticFeedback.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    val flightAnim = remember(req.sessionId) { Animatable(0f) }
    var closing by remember(req.sessionId) { mutableStateOf(false) }
    var draftValue by remember(req.sessionId, param.value) { mutableStateOf(param.value) }

    val hostCoordsRef = remember(req.sessionId) { StudioCoordsRef() }
    val cardCoordsRef = remember(req.sessionId) { StudioCoordsRef() }
    val flightCoordsRef = remember(req.sessionId) { StudioCoordsRef() }

    LaunchedEffect(req.sessionId) {
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
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
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

    val startCardColor = MaterialTheme.colorScheme.surface
    val endCardColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val containerColor = lerpColor(startCardColor, endCardColor, sstepE(morphT, 0.12f, 0.75f))
    val scrimAlpha = 0.42f * sstepE(morphT, 0.45f, 0.92f)
    val blurReveal = sstepE(morphT, 0.50f, 0.96f)
    val cornerProgress = sstepE(morphT, 0.15f, 0.85f)
    val modalCorner = lerp(12.dp, 26.dp, cornerProgress)
    val modalShape = RoundedCornerShape(modalCorner)
    val cardBorderColor = containerColor.edgeOutline()

    val flightProgress = sstepE(morphT, 0.0f, 0.96f)
    val contentReveal = sstepE(morphT, 0.18f, 0.88f)

    val topInsetDp = with(density) { topInsetPx.coerceAtLeast(0f).toDp() }
    val navBottomDp = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val imeBottomDp = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
    val effectiveBottomDp = maxOf(navBottomDp, imeBottomDp)
    val effectiveBottomPx = with(density) { effectiveBottomDp.toPx() }

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
        val endW = (maxW - 16.dp).coerceAtLeast(0.dp)
        val startW = if (liveSource.width > 1f) {
            with(density) { liveSource.width.roundToInt().toDp() }
        } else {
            endW
        }.coerceIn(0.dp, maxW)

        val endH = (maxH - topInsetDp - effectiveBottomDp - 8.dp).coerceAtLeast(220.dp)
        val startH = if (liveSource.height > 1f) {
            with(density) { liveSource.height.roundToInt().toDp() }
        } else {
            54.dp
        }.coerceIn(44.dp, maxH)

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
                .padding(top = topInsetDp, bottom = effectiveBottomDp)
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
                    (fallbackHpx - effectiveBottomPx).coerceAtLeast(topInsetPx + 1f)
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
                        // 1. 1:1 Morphed Header matching ThermalTreeParamRow at morphT = 0f
                        val startIndentDp = (20 + req.depth * 14).dp
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
                                    start = lerp(startIndentDp, 20.dp, cornerProgress),
                                    end = lerp(12.dp, 20.dp, cornerProgress),
                                    top = lerp(8.dp, 16.dp, cornerProgress),
                                    bottom = lerp(8.dp, 14.dp, cornerProgress)
                                ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .padding(end = 6.dp)
                                    .size(16.dp)
                                    .rotate(lerpFloat(0f, 90f, cornerProgress))
                            )
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    SliderTitleText(
                                        title = param.key,
                                        growProgress = sstepE(morphT, 0.14f, 0.80f),
                                        maxGrow = 1.25f,
                                        baseStyle = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.SemiBold
                                        ),
                                        modifier = Modifier.weight(1f)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    FieldBadge(param)
                                }
                                Text(
                                    text = draftValue.previewValue(),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(Modifier.width(6.dp))
                            Box(
                                modifier = Modifier.size(34.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (cornerProgress < 0.5f) {
                                    Icon(
                                        imageVector = Icons.Rounded.Info,
                                        contentDescription = stringResource(R.string.thermal_manager_info),
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .size(18.dp)
                                            .alpha(1f - cornerProgress * 2f)
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Rounded.KeyboardArrowUp,
                                        contentDescription = stringResource(R.string.btn_close),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier
                                            .size(20.dp)
                                            .alpha((cornerProgress - 0.5f) * 2f)
                                    )
                                }
                            }
                        }

                        // 2. Unfolding Description, Value Editor & Save Action
                        if (contentReveal > 0.005f) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .alpha(contentReveal)
                                    .padding(horizontal = 20.dp, vertical = 12.dp)
                                    .verticalScroll(rememberScrollState(), enabled = morphT > 0.7f),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                val infoBg = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.65f)
                                Card(
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = infoBg),
                                    border = BorderStroke(1.dp, infoBg.edgeOutline()),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = param.displayPath,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = param.help,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                when (param.kind) {
                                    ThermalValueKind.BOOLEAN -> {
                                        val boolBg = MaterialTheme.colorScheme.surface
                                        Card(
                                            shape = RoundedCornerShape(16.dp),
                                            colors = CardDefaults.cardColors(containerColor = boolBg),
                                            border = BorderStroke(1.dp, boolBg.edgeOutline()),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = draftValue,
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontFamily = FontFamily.Monospace,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Switch(
                                                    checked = draftValue.equals("true", ignoreCase = true),
                                                    onCheckedChange = { draftValue = it.toString() }
                                                )
                                            }
                                        }
                                    }

                                    ThermalValueKind.NUMBER -> {
                                        val isDecimal = draftValue.contains('.')
                                        val step = if (isDecimal) 0.5f else 1f
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            ExpressiveStepButton(
                                                symbol = "−",
                                                enabled = !closing,
                                                size = 52.dp,
                                                corner = 16.dp,
                                                onClick = {
                                                    val cur = draftValue.trim().toDoubleOrNull() ?: 0.0
                                                    val next = cur - step
                                                    draftValue = if (isDecimal) {
                                                        String.format(Locale.US, "%.2f", next).trimEnd('0').trimEnd('.')
                                                    } else {
                                                        next.roundToInt().toString()
                                                    }
                                                }
                                            )
                                            OutlinedTextField(
                                                value = draftValue,
                                                onValueChange = { draftValue = it },
                                                modifier = Modifier.weight(1f),
                                                shape = RoundedCornerShape(16.dp),
                                                singleLine = true,
                                                label = { Text(param.valueLabel) }
                                            )
                                            ExpressiveStepButton(
                                                symbol = "+",
                                                enabled = !closing,
                                                size = 52.dp,
                                                corner = 16.dp,
                                                onClick = {
                                                    val cur = draftValue.trim().toDoubleOrNull() ?: 0.0
                                                    val next = cur + step
                                                    draftValue = if (isDecimal) {
                                                        String.format(Locale.US, "%.2f", next).trimEnd('0').trimEnd('.')
                                                    } else {
                                                        next.roundToInt().toString()
                                                    }
                                                }
                                            )
                                        }
                                    }

                                    ThermalValueKind.ARRAY -> {
                                        var rawJsonMode by remember(req.sessionId) { mutableStateOf(false) }
                                        val parsedArray = remember(draftValue) {
                                            runCatching { parseThermalJsonValue(draftValue) as? JSONArray }.getOrNull()
                                        }
                                        val isNumericOrNullArray = remember(parsedArray) {
                                            if (parsedArray == null || parsedArray.length() == 0) false
                                            else (0 until parsedArray.length()).all { idx ->
                                                val v = parsedArray.opt(idx)
                                                v == null || v == org.json.JSONObject.NULL || v is Number ||
                                                    (v is String && (v.equals("NaN", true) || v.toDoubleOrNull() != null))
                                            }
                                        }

                                        if (parsedArray != null && isNumericOrNullArray && !rawJsonMode) {
                                            val severityNames = listOf(
                                                "0 • NONE",
                                                "1 • LIGHT",
                                                "2 • MODERATE",
                                                "3 • SEVERE",
                                                "4 • CRITICAL",
                                                "5 • EMERGENCY",
                                                "6 • SHUTDOWN"
                                            )
                                            Column(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                for (idx in 0 until parsedArray.length()) {
                                                    val itemRaw = parsedArray.opt(idx)
                                                    val numVal = when (itemRaw) {
                                                        is Number -> itemRaw.toDouble()
                                                        is String -> itemRaw.toDoubleOrNull()
                                                        else -> null
                                                    }?.takeIf { !it.isNaN() }

                                                    val levelLabel = if (parsedArray.length() == 7) {
                                                        severityNames[idx]
                                                    } else {
                                                        "Index [$idx]"
                                                    }
                                                    val rowBg = MaterialTheme.colorScheme.surface
                                                    Card(
                                                        shape = RoundedCornerShape(14.dp),
                                                        colors = CardDefaults.cardColors(containerColor = rowBg),
                                                        border = BorderStroke(1.dp, rowBg.edgeOutline()),
                                                        modifier = Modifier.fillMaxWidth()
                                                    ) {
                                                        Row(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .padding(horizontal = 14.dp, vertical = 8.dp),
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                        ) {
                                                            Column(modifier = Modifier.weight(1f)) {
                                                                Text(
                                                                    text = levelLabel,
                                                                    style = MaterialTheme.typography.labelMedium,
                                                                    fontWeight = FontWeight.Bold,
                                                                    color = if (idx >= 5 && parsedArray.length() == 7) {
                                                                        MaterialTheme.colorScheme.error
                                                                    } else {
                                                                        MaterialTheme.colorScheme.primary
                                                                    }
                                                                )
                                                                Text(
                                                                    text = numVal?.let {
                                                                        if (it % 1.0 == 0.0) "${it.toLong()}" else "$it"
                                                                    } ?: "NaN / null",
                                                                    style = MaterialTheme.typography.titleMedium,
                                                                    fontFamily = FontFamily.Monospace,
                                                                    fontWeight = FontWeight.Bold,
                                                                    color = MaterialTheme.colorScheme.onSurface
                                                                )
                                                            }
                                                            ExpressiveStepButton(
                                                                symbol = "−",
                                                                enabled = !closing && numVal != null,
                                                                size = 38.dp,
                                                                corner = 12.dp,
                                                                onClick = {
                                                                    val cur = numVal ?: return@ExpressiveStepButton
                                                                    val copy = JSONArray(parsedArray.toString())
                                                                    val next = cur - 1.0
                                                                    if (cur % 1.0 == 0.0) copy.put(idx, next.toLong()) else copy.put(idx, next)
                                                                    draftValue = copy.toString()
                                                                }
                                                            )
                                                            ExpressiveStepButton(
                                                                symbol = "+",
                                                                enabled = !closing && numVal != null,
                                                                size = 38.dp,
                                                                corner = 12.dp,
                                                                onClick = {
                                                                    val cur = numVal ?: return@ExpressiveStepButton
                                                                    val copy = JSONArray(parsedArray.toString())
                                                                    val next = cur + 1.0
                                                                    if (cur % 1.0 == 0.0) copy.put(idx, next.toLong()) else copy.put(idx, next)
                                                                    draftValue = copy.toString()
                                                                }
                                                            )
                                                        }
                                                    }
                                                }
                                                SecondaryActionPill(
                                                    icon = Icons.Rounded.Code,
                                                    label = "Raw JSON",
                                                    enabled = !closing,
                                                    onClick = { rawJsonMode = true },
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                            }
                                        } else {
                                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                OutlinedTextField(
                                                    value = draftValue,
                                                    onValueChange = { draftValue = it },
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .heightIn(min = 160.dp),
                                                    shape = RoundedCornerShape(16.dp),
                                                    singleLine = false,
                                                    minLines = 6,
                                                    maxLines = 14,
                                                    label = { Text(param.valueLabel) }
                                                )
                                                if (parsedArray != null && isNumericOrNullArray) {
                                                    SecondaryActionPill(
                                                        icon = Icons.Rounded.Code,
                                                        label = "Visual Stepper",
                                                        enabled = !closing,
                                                        onClick = { rawJsonMode = false },
                                                        modifier = Modifier.fillMaxWidth()
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    else -> {
                                        OutlinedTextField(
                                            value = draftValue,
                                            onValueChange = { draftValue = it },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .heightIn(min = 56.dp),
                                            shape = RoundedCornerShape(16.dp),
                                            singleLine = true,
                                            label = { Text(param.valueLabel) }
                                        )
                                    }
                                }

                                Spacer(Modifier.weight(1f))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    SecondaryActionPill(
                                        icon = Icons.Rounded.Close,
                                        label = stringResource(R.string.btn_cancel),
                                        enabled = !closing,
                                        onClick = { closeAnimated() },
                                        modifier = Modifier.weight(1f)
                                    )
                                    StudioTabStyledApplyButton(
                                        isActive = false,
                                        applyLabel = stringResource(R.string.btn_save),
                                        activeLabel = stringResource(R.string.btn_save),
                                        enabled = !closing,
                                        onClick = {
                                            val committed = draftValue
                                            onSave(param, committed)
                                            closeAnimated()
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
}

@Composable
private fun FieldBadge(param: ThermalConfigParam) {
    val color = when {
        param.throttlingField -> MaterialTheme.colorScheme.errorContainer
        param.knownField -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val textColor = when {
        param.throttlingField -> MaterialTheme.colorScheme.onErrorContainer
        param.knownField -> MaterialTheme.colorScheme.onTertiaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        color = color,
        contentColor = textColor,
        shape = CircleShape,
        border = BorderStroke(1.dp, color.edgeOutline())
    ) {
        Text(
            text = if (param.throttlingField) {
                stringResource(R.string.thermal_manager_badge_throttle)
            } else if (param.knownField) {
                stringResource(R.string.thermal_manager_badge_known)
            } else {
                stringResource(R.string.thermal_manager_badge_unknown)
            },
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}