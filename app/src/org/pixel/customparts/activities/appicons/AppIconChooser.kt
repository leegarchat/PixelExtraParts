package org.pixel.customparts.activities

import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.util.lerp as lerpFloat
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch
import org.pixel.customparts.R
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import org.pixel.customparts.icons.AppIconCandidate
import org.pixel.customparts.icons.AppIconShapeOverrides
import org.pixel.customparts.icons.IconApplyMode
import org.pixel.customparts.icons.IconMapSnapshot
import org.pixel.customparts.icons.IconPackApplyStatus
import org.pixel.customparts.icons.IconPackEntry
import org.pixel.customparts.icons.IconPackInfo
import org.pixel.customparts.icons.IconPackManager
import org.pixel.customparts.icons.IconPackPreviewItem
import org.pixel.customparts.icons.InstalledIconApp
import org.pixel.customparts.ui.GraphicsLayerRecordingState
import org.pixel.customparts.ui.SliderTitleText
import org.pixel.customparts.ui.edgeOutline
import java.util.Locale
import kotlin.math.roundToInt

// ── 1. Icon Pack Flight Pseudo-Modal Overlay (Mode | Select | Preview) ──

@Composable
internal fun IconPackFlightOverlay(
    target: IconPackFlightTarget?,
    iconMap: IconMapSnapshot,
    busy: Boolean,
    scrollBlur: GraphicsLayerRecordingState,
    topInsetPx: Float,
    closeSignal: Int,
    onProgress: (Float) -> Unit,
    onClosingChanged: (Boolean) -> Unit,
    onClose: () -> Unit,
    onOptimisticPackUpdate: (IconPackInfo) -> Unit = {},
    onApplyMode: (IconPackInfo, IconApplyMode) -> Unit,
    onApplyPartial: (IconPackInfo, Set<String>) -> Unit,
    onResetPack: (IconPackInfo) -> Unit
) {
    val flight = target ?: return
    var displayPack by remember(flight.sessionId) { mutableStateOf(flight.pack) }
    LaunchedEffect(flight.pack) {
        displayPack = flight.pack
    }
    val pack = displayPack

    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    val flightAnim = remember(flight.sessionId) { Animatable(0f) }
    var closing by remember(flight.sessionId) { mutableStateOf(false) }
    var activeTab by remember(flight.sessionId) { mutableIntStateOf(flight.initialTab.coerceIn(0, 2)) }

    val hostCoordsRef = remember(flight.sessionId) { StudioCoordsRef() }
    val cardCoordsRef = remember(flight.sessionId) { StudioCoordsRef() }
    val flightCoordsRef = remember(flight.sessionId) { StudioCoordsRef() }

    // Async state for Tab 1 (Partial Selection) & Tab 2 (Preview Grid)
    var entriesLoading by remember(flight.sessionId) { mutableStateOf(false) }
    var entriesLoaded by remember(flight.sessionId) { mutableStateOf(false) }
    var entries by remember(flight.sessionId) { mutableStateOf<List<IconPackEntry>>(emptyList()) }
    var partialQuery by rememberSaveable(flight.sessionId) { mutableStateOf("") }
    var selectedPackages by remember(flight.sessionId, iconMap) {
        mutableStateOf(
            iconMap.sources
                .filterValues { it.iconPackPackage == pack.packageName }
                .keys
                .toSet()
        )
    }

    var previewLoading by remember(flight.sessionId) { mutableStateOf(false) }
    var previewLoaded by remember(flight.sessionId) { mutableStateOf(false) }
    var previewItems by remember(flight.sessionId) { mutableStateOf<List<IconPackPreviewItem>>(emptyList()) }
    var previewQuery by rememberSaveable(flight.sessionId) { mutableStateOf("") }

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

    LaunchedEffect(activeTab, pack.packageName) {
        if (activeTab == 1 && !entriesLoaded && !entriesLoading) {
            entriesLoading = true
            entries = IconPackManager.loadIconPackSelectionEntries(context, pack.packageName)
            entriesLoaded = true
            entriesLoading = false
        } else if (activeTab == 2 && !previewLoaded && !previewLoading && pack.installed) {
            previewLoading = true
            previewItems = IconPackManager.loadPreviewItems(context, pack.packageName)
            previewLoaded = true
            previewLoading = false
        }
    }

    fun applyOptimisticAndClose(
        optimisticPack: IconPackInfo,
        immediateAction: () -> Unit
    ) {
        if (closing) return
        displayPack = optimisticPack
        onOptimisticPackUpdate(optimisticPack)
        immediateAction()
        closing = true
        onClosingChanged(true)
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
                        visibilityThreshold = 0.002f
                    )
                )
            } finally {
                onClosingChanged(false)
                onProgress(0f)
                onClose()
            }
        }
    }

    fun closeAnimated(afterClose: (() -> Unit)? = null) {
        if (closing) return
        closing = true
        onClosingChanged(true)
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
                        visibilityThreshold = 0.05f
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

    val isAppliedAny = pack.appliedPackageCount > 0
    val startCardColor = if (isAppliedAny) {
        MaterialTheme.colorScheme.surfaceContainerHigh
    } else {
        MaterialTheme.colorScheme.surface
    }
    val endCardColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val containerColor = lerpColor(startCardColor, endCardColor, sstepE(morphT, 0.15f, 0.78f))
    val scrimAlpha = 0.42f * sstepE(morphT, 0.45f, 0.92f)
    val blurReveal = sstepE(morphT, 0.50f, 0.96f)
    val cornerProgress = sstepE(morphT, 0.35f, 0.88f)
    val modalCorner = lerp(24.dp, 26.dp, cornerProgress)
    val modalShape = RoundedCornerShape(modalCorner)
    val cardBorderColor = containerColor.edgeOutline()

    val flightProgress = sstepE(morphT, 0.0f, 0.96f)
    val contentReveal = sstepE(morphT, 0.16f, 0.88f)
    val summaryVisibility = 1f - sstepE(morphT, 0.12f, 0.68f)

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

        val liveSource = flight.sourceBoundsProvider().let {
            if (it.width > 1f && it.height > 1f) it else flight.source
        }
        val endW = (maxW - 16.dp).coerceAtLeast(0.dp)
        val startW = if (liveSource.width > 1f) {
            with(density) { liveSource.width.roundToInt().toDp() }
        } else {
            endW
        }.coerceIn(0.dp, maxW)

        val endH = (maxH - topInsetDp - effectiveBottomDp - 8.dp).coerceAtLeast(200.dp)
        val startH = if (liveSource.height > 1f) {
            with(density) { liveSource.height.roundToInt().toDp() }
        } else {
            endH
        }.coerceIn(80.dp, maxH)

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
                        // 1. 1:1 Morphed Header matching IconPackCard
                        Column(
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
                                .padding(horizontal = 20.dp, vertical = 16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                DrawableIcon(
                                    drawable = pack.icon,
                                    size = lerp(52.dp, 48.dp, cornerProgress),
                                    fallback = Icons.Rounded.Apps
                                )
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 14.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    SliderTitleText(
                                        title = pack.label,
                                        growProgress = sstepE(morphT, 0.14f, 0.80f),
                                        maxGrow = 1.25f,
                                        baseStyle = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Bold
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Text(
                                        text = pack.packageName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                            enabled = !closing,
                                            onClick = {
                                                haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                                                closeAnimated()
                                            }
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.KeyboardArrowDown,
                                        contentDescription = stringResource(R.string.btn_close),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier
                                            .size(22.dp)
                                            .rotate(lerpFloat(0f, 180f, cornerProgress))
                                    )
                                }
                            }

                            if (summaryVisibility > 0.005f) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .unfoldVerticalSlot(summaryVisibility)
                                        .alpha(summaryVisibility)
                                ) {
                                    Spacer(Modifier.height(12.dp))
                                    IconPackSummaryBlock(pack = pack)
                                }
                            }
                        }

                        // 2. Unfolding Segmented Tabs + Full-Height Stage
                        if (contentReveal > 0.005f) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .alpha(contentReveal)
                                    .padding(bottom = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                val tabList = if (pack.installed) {
                                    listOf(
                                        stringResource(R.string.app_icons_apply_all),
                                        stringResource(R.string.app_icons_apply_partial),
                                        stringResource(R.string.app_icons_view_pack)
                                    )
                                } else {
                                    listOf(
                                        stringResource(R.string.app_icons_cancel_all),
                                        stringResource(R.string.app_icons_apply_partial)
                                    )
                                }
                                val clampedTab = activeTab.coerceIn(0, tabList.lastIndex)
                                val animatedTabPos by animateFloatAsState(
                                    targetValue = clampedTab.toFloat(),
                                    animationSpec = spring(
                                        stiffness = Spring.StiffnessMediumLow,
                                        dampingRatio = Spring.DampingRatioNoBouncy
                                    ),
                                    label = "PackFlightTabPos"
                                )

                                AppIconsTabs(
                                    titles = tabList,
                                    position = animatedTabPos,
                                    onTabClick = { activeTab = it }
                                )

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                        .padding(horizontal = 20.dp)
                                ) {
                                    AnimatedContent(
                                        targetState = clampedTab,
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
                                        label = "PackModalSubTabSlide"
                                    ) { tabIndex ->
                                        when (tabIndex) {
                                            0 -> {
                                                Column(
                                                    modifier = Modifier.fillMaxSize(),
                                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                                ) {
                                                    if (pack.installed) {
                                                        Text(
                                                            text = stringResource(R.string.app_icons_apply_mode_title),
                                                            style = MaterialTheme.typography.labelLarge,
                                                            fontWeight = FontWeight.Bold,
                                                            color = MaterialTheme.colorScheme.primary
                                                        )
                                                        val modes = listOf(
                                                            IconApplyMode.INSTALLED_ONLY to stringResource(R.string.app_icons_apply_mode_installed_only),
                                                            IconApplyMode.ALL_PACK to stringResource(R.string.app_icons_apply_mode_all_pack),
                                                            IconApplyMode.USER_ONLY to stringResource(R.string.app_icons_apply_mode_user_only),
                                                            IconApplyMode.SYSTEM_ONLY to stringResource(R.string.app_icons_apply_mode_system_only)
                                                        )
                                                        modes.forEach { (mode, label) ->
                                                            StudioTabStyledApplyButton(
                                                                isActive = false,
                                                                applyLabel = label,
                                                                activeLabel = label,
                                                                enabled = !closing && !busy,
                                                                onClick = {
                                                                    val totalCount = pack.supportedPackageCount
                                                                    val nextApplied = when (mode) {
                                                                        IconApplyMode.ALL_PACK,
                                                                        IconApplyMode.INSTALLED_ONLY -> totalCount
                                                                        IconApplyMode.USER_ONLY -> if (entriesLoaded) {
                                                                            entries.count { it.installed && !it.isSystem }
                                                                        } else pack.appliedPackageCount.coerceAtLeast(1)
                                                                        IconApplyMode.SYSTEM_ONLY -> if (entriesLoaded) {
                                                                            entries.count { it.installed && it.isSystem }
                                                                        } else pack.appliedPackageCount.coerceAtLeast(1)
                                                                    }
                                                                    val nextStatus = when {
                                                                        totalCount <= 0 || nextApplied <= 0 -> IconPackApplyStatus.NOT_APPLIED
                                                                        nextApplied >= totalCount -> IconPackApplyStatus.APPLIED_FULL
                                                                        else -> IconPackApplyStatus.APPLIED_PARTIAL
                                                                    }
                                                                    val optimistic = pack.copy(
                                                                        appliedPackageCount = nextApplied,
                                                                        status = nextStatus,
                                                                        requiresUpdate = false
                                                                    )
                                                                    applyOptimisticAndClose(optimistic) {
                                                                        onApplyMode(pack, mode)
                                                                    }
                                                                },
                                                                modifier = Modifier.fillMaxWidth()
                                                            )
                                                        }
                                                    }
                                                    Spacer(Modifier.weight(1f))
                                                    if (pack.appliedPackageCount > 0) {
                                                        SecondaryActionPill(
                                                            icon = Icons.Rounded.Delete,
                                                            label = stringResource(R.string.app_icons_cancel_all),
                                                            enabled = !closing && !busy,
                                                            onClick = {
                                                                val optimistic = pack.copy(
                                                                    appliedPackageCount = 0,
                                                                    status = IconPackApplyStatus.NOT_APPLIED,
                                                                    requiresUpdate = false
                                                                )
                                                                applyOptimisticAndClose(optimistic) {
                                                                    onResetPack(pack)
                                                                }
                                                            },
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .height(46.dp),
                                                            containerColor = MaterialTheme.colorScheme.errorContainer,
                                                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                                                        )
                                                    }
                                                }
                                            }

                                            1 -> {
                                                val visibleEntries = remember(entries, partialQuery) {
                                                    val needle = partialQuery.trim().lowercase(Locale.getDefault())
                                                    if (needle.isEmpty()) entries
                                                    else entries.filter { entry ->
                                                        entry.label.lowercase(Locale.getDefault()).contains(needle) ||
                                                            entry.appPackageName.lowercase(Locale.US).contains(needle) ||
                                                            entry.drawableName.lowercase(Locale.US).contains(needle)
                                                    }
                                                }
                                                Column(
                                                    modifier = Modifier.fillMaxSize(),
                                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                                ) {
                                                    AppIconsSearchField(
                                                        query = partialQuery,
                                                        hint = stringResource(R.string.app_icons_dialog_search_hint),
                                                        onQueryChange = { partialQuery = it }
                                                    )
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .weight(1f),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        when {
                                                            entriesLoading || !entriesLoaded -> LoadingDialogContent()
                                                            visibleEntries.isEmpty() -> EmptyStateText(
                                                                stringResource(R.string.app_icons_no_supported_apps)
                                                            )
                                                            else -> LazyColumn(
                                                                modifier = Modifier.fillMaxSize(),
                                                                verticalArrangement = Arrangement.spacedBy(8.dp)
                                                            ) {
                                                                items(
                                                                    visibleEntries,
                                                                    key = { it.appPackageName + it.drawableName }
                                                                ) { entry ->
                                                                    val checked = selectedPackages.contains(entry.appPackageName)
                                                                    SelectableIconEntryRow(
                                                                        entry = entry,
                                                                        checked = checked,
                                                                        onCheckedChange = { next ->
                                                                            selectedPackages = if (next) {
                                                                                selectedPackages + entry.appPackageName
                                                                            } else {
                                                                                selectedPackages - entry.appPackageName
                                                                            }
                                                                        }
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    }
                                                    StudioTabStyledApplyButton(
                                                        isActive = false,
                                                        applyLabel = "${stringResource(R.string.app_icons_apply_selected)} (${selectedPackages.size})",
                                                        activeLabel = stringResource(R.string.app_icons_apply_selected),
                                                        enabled = !closing && !busy && entriesLoaded,
                                                        onClick = {
                                                            val snapshot = selectedPackages
                                                            val nextCount = snapshot.size
                                                            val nextStatus = when {
                                                                nextCount <= 0 -> IconPackApplyStatus.NOT_APPLIED
                                                                pack.supportedPackageCount > 0 &&
                                                                    nextCount >= pack.supportedPackageCount -> IconPackApplyStatus.APPLIED_FULL
                                                                else -> IconPackApplyStatus.APPLIED_PARTIAL
                                                            }
                                                            val optimistic = pack.copy(
                                                                appliedPackageCount = nextCount,
                                                                status = nextStatus
                                                            )
                                                            applyOptimisticAndClose(optimistic) {
                                                                onApplyPartial(pack, snapshot)
                                                            }
                                                        },
                                                        modifier = Modifier.fillMaxWidth()
                                                    )
                                                }
                                            }

                                            else -> {
                                                val visiblePreview = remember(previewItems, previewQuery) {
                                                    val needle = previewQuery.trim().lowercase(Locale.getDefault())
                                                    if (needle.isEmpty()) previewItems
                                                    else previewItems.filter { item ->
                                                        item.label.lowercase(Locale.getDefault()).contains(needle) ||
                                                            item.drawableName.lowercase(Locale.US).contains(needle) ||
                                                            item.appPackageName.orEmpty().lowercase(Locale.US).contains(needle)
                                                    }.sortedWith(
                                                        compareBy<IconPackPreviewItem> { previewSearchRank(it, needle) }
                                                            .thenBy { it.drawableName.lowercase(Locale.US) }
                                                            .thenBy { it.label.lowercase(Locale.getDefault()) }
                                                    )
                                                }
                                                Column(
                                                    modifier = Modifier.fillMaxSize(),
                                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                                ) {
                                                    AppIconsSearchField(
                                                        query = previewQuery,
                                                        hint = stringResource(R.string.app_icons_dialog_search_hint),
                                                        onQueryChange = { previewQuery = it }
                                                    )
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .weight(1f),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        when {
                                                            previewLoading || !previewLoaded -> LoadingDialogContent()
                                                            visiblePreview.isEmpty() -> EmptyStateText(
                                                                stringResource(R.string.app_icons_no_pack_icons)
                                                            )
                                                            else -> IconPreviewGrid(
                                                                items = visiblePreview,
                                                                modifier = Modifier.fillMaxSize()
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
                    }
                }
            }
        }
    }
}

// ── 2. App Detail Flight Pseudo-Modal Overlay (Icon | Library | Shape) ──

@Composable
internal fun AppDetailFlightOverlay(
    target: AppDetailFlightTarget?,
    iconMap: IconMapSnapshot,
    iconPacks: List<IconPackInfo>,
    cachedCustomBitmap: Bitmap?,
    busy: Boolean,
    scrollBlur: GraphicsLayerRecordingState,
    topInsetPx: Float,
    closeSignal: Int,
    onProgress: (Float) -> Unit,
    onClose: () -> Unit,
    onRestore: (InstalledIconApp) -> Unit,
    onApplyCandidate: (InstalledIconApp, AppIconCandidate) -> Unit,
    onImportCustomIcon: (InstalledIconApp, Uri) -> Unit,
    onSaveShapeOverrides: (InstalledIconApp, AppIconShapeOverrides?) -> Unit
) {
    val flight = target ?: return
    val app = flight.app
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val previewSizePx = with(density) { 56.dp.roundToPx() }

    val flightAnim = remember(flight.sessionId) { Animatable(0f) }
    var closing by remember(flight.sessionId) { mutableStateOf(false) }
    var activeTab by rememberSaveable(flight.sessionId) { mutableIntStateOf(0) }

    val hostCoordsRef = remember(flight.sessionId) { StudioCoordsRef() }
    val cardCoordsRef = remember(flight.sessionId) { StudioCoordsRef() }
    val flightCoordsRef = remember(flight.sessionId) { StudioCoordsRef() }

    var candidatesLoading by remember(flight.sessionId) { mutableStateOf(true) }
    var candidates by remember(flight.sessionId) { mutableStateOf<List<AppIconCandidate>>(emptyList()) }
    var appliedBitmap by remember(flight.sessionId, cachedCustomBitmap) { mutableStateOf(cachedCustomBitmap) }

    var allPackIconsLoading by remember(flight.sessionId) { mutableStateOf(false) }
    var allPackIconsLoaded by remember(flight.sessionId) { mutableStateOf(false) }
    var allPackItems by remember(flight.sessionId) { mutableStateOf<List<AppIconChooserItem>>(emptyList()) }
    var libraryQuery by rememberSaveable(flight.sessionId) { mutableStateOf("") }

    var shapeOverrides by remember(flight.sessionId, iconMap) {
        mutableStateOf(
            iconMap.shapeOverrides[app.packageName] ?: app.shapeOverrides ?: AppIconShapeOverrides()
        )
    }
    val hasShapeOverride = shapeOverrides.isEmpty() == false

    LaunchedEffect(flight.sessionId) {
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

    LaunchedEffect(flight.sessionId, app.packageName, iconMap) {
        candidatesLoading = true
        if (app.appliedIconFileName != null && appliedBitmap == null) {
            appliedBitmap = IconPackManager.loadAppliedIconBitmap(context, app.packageName, previewSizePx)
        } else if (app.appliedIconFileName == null) {
            appliedBitmap = null
        }
        candidates = IconPackManager.loadCandidatesForApp(context, app.packageName)
        candidatesLoading = false
    }

    LaunchedEffect(activeTab, app.packageName, iconPacks) {
        if (activeTab == 1 && !allPackIconsLoaded && !allPackIconsLoading) {
            allPackIconsLoading = true
            val installedPacks = iconPacks.filter { it.installed }
            val packLabels = installedPacks.associate { it.packageName to it.label }
            val detected = candidates.map { c ->
                AppIconChooserItem(
                    iconPackPackage = c.iconPackPackage,
                    iconPackLabel = c.iconPackLabel,
                    drawableName = c.drawableName,
                    label = c.drawableName,
                    detected = true,
                    appPackageName = app.packageName
                )
            }
            val detectedSet = detected.map { it.iconPackPackage to it.drawableName }.toSet()
            val rest = installedPacks.flatMap { pack ->
                IconPackManager.loadPreviewItems(context, pack.packageName).map { item ->
                    AppIconChooserItem(
                        iconPackPackage = item.iconPackPackage,
                        iconPackLabel = packLabels[item.iconPackPackage] ?: pack.label,
                        drawableName = item.drawableName,
                        label = item.label,
                        detected = false,
                        appPackageName = item.appPackageName
                    )
                }
            }.filterNot { (it.iconPackPackage to it.drawableName) in detectedSet }
            allPackItems = detected + rest
            allPackIconsLoaded = true
            allPackIconsLoading = false
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

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            closeAnimated { onImportCustomIcon(app, uri) }
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

        val liveSource = flight.sourceBoundsProvider().let {
            if (it.width > 1f && it.height > 1f) it else flight.source
        }
        val endW = (maxW - 16.dp).coerceAtLeast(0.dp)
        val startW = if (liveSource.width > 1f) {
            with(density) { liveSource.width.roundToInt().toDp() }
        } else {
            endW
        }.coerceIn(0.dp, maxW)

        val endH = (maxH - topInsetDp - effectiveBottomDp - 8.dp).coerceAtLeast(200.dp)
        val startH = if (liveSource.height > 1f) {
            with(density) { liveSource.height.roundToInt().toDp() }
        } else {
            endH
        }.coerceIn(64.dp, maxH)

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
                        // 1. 1:1 Morphed Header matching InstalledAppStreamRow
                        val iconDp = lerp(44.dp, 50.dp, cornerProgress)

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
                                    horizontal = 20.dp,
                                    vertical = lerp(13.dp, 16.dp, cornerProgress)
                                ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (appliedBitmap != null) {
                                BitmapIcon(bitmap = appliedBitmap, size = iconDp, fallback = Icons.Rounded.Apps)
                            } else {
                                DrawableIcon(drawable = app.icon, size = iconDp, fallback = Icons.Rounded.Apps)
                            }

                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 16.dp, end = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                SliderTitleText(
                                    title = app.label,
                                    growProgress = sstepE(morphT, 0.14f, 0.80f),
                                    maxGrow = 1.25f,
                                    baseStyle = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Medium
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Text(
                                    text = app.appliedIconPackLabel?.let {
                                        stringResource(R.string.app_icons_applied_from_format, it)
                                    } ?: app.packageName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (app.appliedIconPackLabel != null) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            if (hasShapeOverride) {
                                val shapeBadgeBg = MaterialTheme.colorScheme.tertiaryContainer
                                Box(
                                    modifier = Modifier
                                        .padding(end = 6.dp)
                                        .clip(CircleShape)
                                        .background(shapeBadgeBg)
                                        .border(1.dp, shapeBadgeBg.edgeOutline(), CircleShape)
                                        .padding(horizontal = 7.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "S",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        enabled = !closing,
                                        onClick = {
                                            haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                                            closeAnimated()
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.ChevronRight,
                                    contentDescription = stringResource(R.string.btn_close),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .size(22.dp)
                                        .rotate(lerpFloat(0f, 90f, cornerProgress))
                                )
                            }
                        }

                        // 2. Unfolding Body: Segmented AppIconsTabs + Full-Height Stage
                        if (contentReveal > 0.005f) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .alpha(contentReveal)
                                    .padding(bottom = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                val subTabs = listOf(
                                    stringResource(R.string.app_icons_available_icons_title),
                                    stringResource(R.string.app_icons_all_pack_icons_title),
                                    stringResource(R.string.app_icons_per_app_shape_title)
                                )
                                val clampedSubTab = activeTab.coerceIn(0, 2)
                                val animatedSubTabPos by animateFloatAsState(
                                    targetValue = clampedSubTab.toFloat(),
                                    animationSpec = spring(
                                        stiffness = Spring.StiffnessMediumLow,
                                        dampingRatio = Spring.DampingRatioNoBouncy
                                    ),
                                    label = "AppFlightSubTabPos"
                                )

                                AppIconsTabs(
                                    titles = subTabs,
                                    position = animatedSubTabPos,
                                    onTabClick = { activeTab = it }
                                )

                                val appliedSource = iconMap.sources[app.packageName]

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                ) {
                                AnimatedContent(
                                    targetState = clampedSubTab,
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
                                    label = "AppFlightSubTabSlide"
                                ) { tabIdx ->
                                    when (tabIdx) {
                                        0 -> {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .padding(horizontal = 20.dp),
                                                verticalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .weight(1f),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    when {
                                                        candidatesLoading -> LoadingDialogContent()
                                                        candidates.isEmpty() -> EmptyStateText(
                                                            stringResource(R.string.app_icons_no_app_candidates)
                                                        )
                                                        else -> LazyColumn(
                                                            modifier = Modifier.fillMaxSize(),
                                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                                        ) {
                                                            items(
                                                                candidates,
                                                                key = { it.iconPackPackage + ":" + it.drawableName }
                                                            ) { candidate ->
                                                                val isApplied =
                                                                    appliedSource?.iconPackPackage == candidate.iconPackPackage &&
                                                                        appliedSource.drawableName == candidate.drawableName
                                                                AppIconCandidateRow(
                                                                    candidate = candidate,
                                                                    applied = isApplied,
                                                                    busy = busy,
                                                                    onApply = {
                                                                        closeAnimated {
                                                                            onApplyCandidate(app, candidate)
                                                                        }
                                                                    }
                                                                )
                                                            }
                                                        }
                                                    }
                                                }

                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    SecondaryActionPill(
                                                        icon = Icons.Rounded.FileOpen,
                                                        label = stringResource(R.string.app_icons_import_custom_icon),
                                                        enabled = !closing && !busy,
                                                        onClick = {
                                                            importLauncher.launch(
                                                                arrayOf("image/*", "application/xml", "text/xml")
                                                            )
                                                        },
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                    if (app.appliedIconFileName != null) {
                                                        SecondaryActionPill(
                                                            icon = Icons.Rounded.Refresh,
                                                            label = stringResource(R.string.app_icons_restore_default),
                                                            enabled = !closing && !busy,
                                                            onClick = {
                                                                closeAnimated { onRestore(app) }
                                                            },
                                                            modifier = Modifier.weight(1f),
                                                            containerColor = MaterialTheme.colorScheme.errorContainer,
                                                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        1 -> {
                                            val filteredLibrary = remember(allPackItems, libraryQuery) {
                                                filterChooserItems(allPackItems, libraryQuery)
                                            }
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .padding(horizontal = 20.dp),
                                                verticalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                AppIconsSearchField(
                                                    query = libraryQuery,
                                                    hint = stringResource(R.string.app_icons_dialog_search_hint),
                                                    onQueryChange = { libraryQuery = it }
                                                )
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .weight(1f),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    when {
                                                        allPackIconsLoading || !allPackIconsLoaded -> LoadingDialogContent()
                                                        filteredLibrary.isEmpty() -> EmptyStateText(
                                                            stringResource(R.string.app_icons_no_pack_icons)
                                                        )
                                                        else -> LazyColumn(
                                                            modifier = Modifier.fillMaxSize(),
                                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                                        ) {
                                                            items(
                                                                filteredLibrary,
                                                                key = { "${it.detected}:${it.iconPackPackage}:${it.drawableName}" }
                                                            ) { item ->
                                                                val isApplied =
                                                                    appliedSource?.iconPackPackage == item.iconPackPackage &&
                                                                        appliedSource.drawableName == item.drawableName
                                                                AppIconChooserRow(
                                                                    item = item,
                                                                    applied = isApplied,
                                                                    onApply = {
                                                                        closeAnimated {
                                                                            onApplyCandidate(
                                                                                app,
                                                                                AppIconCandidate(
                                                                                    item.iconPackPackage,
                                                                                    item.iconPackLabel,
                                                                                    item.drawableName
                                                                                )
                                                                            )
                                                                        }
                                                                    }
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        else -> {
                                            PerAppShapeSettingsCard(
                                                overrides = shapeOverrides,
                                                onOverridesChange = { shapeOverrides = it },
                                                onSave = {
                                                    onSaveShapeOverrides(
                                                        app,
                                                        shapeOverrides.normalizedOrNull()
                                                    )
                                                    closeAnimated()
                                                },
                                                onReset = {
                                                    shapeOverrides = AppIconShapeOverrides()
                                                    onSaveShapeOverrides(app, null)
                                                    closeAnimated()
                                                }
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
    }
}
}

