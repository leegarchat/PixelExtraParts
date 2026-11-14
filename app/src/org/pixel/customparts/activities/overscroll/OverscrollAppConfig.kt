package org.pixel.customparts.activities

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.geometry.Offset
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.util.lerp as lerpFloat
import androidx.compose.ui.zIndex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.pixel.customparts.R
import org.pixel.customparts.ui.GenericSwitchRow
import org.pixel.customparts.ui.GraphicsLayerRecordingState
import org.pixel.customparts.ui.SliderSettingFloat
import org.pixel.customparts.ui.SliderTitleText
import org.pixel.customparts.ui.edgeOutline
import java.util.Locale
import kotlin.math.roundToInt

data class AppItem(val name: String, val pkg: String, val icon: Drawable?)

@Stable
internal data class OverscrollAppFlightTarget(
    val item: AppConfigItem,
    val appName: String,
    val appIcon: Drawable?,
    val source: Rect = Rect.Zero,
    val sourceBoundsProvider: () -> Rect = { source },
    val sessionId: Long = System.nanoTime()
)

// ── Tab 3: Seamless Configured Apps Stream Page ──────────────────────────

@Composable
internal fun OverscrollAppsTabPage(
    context: Context,
    listState: LazyListState,
    pagePadding: PaddingValues,
    appConfigs: List<AppConfigItem>,
    enabled: Boolean,
    isPickerOpen: Boolean,
    activeEditorPkg: String?,
    onOpenPickerFlight: (Rect, () -> Rect) -> Unit,
    onOpenEditorFlight: (OverscrollAppFlightTarget) -> Unit
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var addBtnBounds by remember { mutableStateOf(Rect.Zero) }

    val filteredConfigs = remember(appConfigs, searchQuery) {
        val q = searchQuery.trim().lowercase(Locale.getDefault())
        if (q.isEmpty()) appConfigs
        else appConfigs.filter { it.pkg.lowercase(Locale.US).contains(q) }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .alpha(if (enabled) 1f else 0.45f),
        contentPadding = pagePadding
    ) {
        item(key = "os_apps_header_controls") {
            Column(
                modifier = Modifier.padding(bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AppIconsSearchField(
                    query = searchQuery,
                    hint = stringResource(R.string.os_app_search_hint),
                    onQueryChange = { searchQuery = it }
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onPlaced {
                            val r = it.unclippedBoundsInRoot()
                            if (r.width > 1f && r.height > 1f) addBtnBounds = r
                        }
                        .onGloballyPositioned {
                            val r = it.unclippedBoundsInRoot()
                            if (r.width > 1f && r.height > 1f) addBtnBounds = r
                        }
                        .alpha(if (isPickerOpen) 0f else 1f)
                ) {
                    SecondaryActionPill(
                        icon = Icons.Rounded.Add,
                        label = stringResource(R.string.os_btn_add_app),
                        enabled = enabled && !isPickerOpen,
                        onClick = { onOpenPickerFlight(addBtnBounds) { addBtnBounds } },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp),
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        if (filteredConfigs.isEmpty()) {
            item(key = "os_apps_empty") {
                EmptyStateText(
                    text = stringResource(R.string.os_group_apps) + " (0)"
                )
            }
        } else {
            itemsIndexed(
                items = filteredConfigs,
                key = { _, item -> item.pkg }
            ) { index, item ->
                val isFirst = index == 0
                val isLast = index == filteredConfigs.lastIndex

                OverscrollAppStreamRow(
                    context = context,
                    item = item,
                    sectionTitle = if (isFirst) {
                        "${stringResource(R.string.os_group_apps)} (${filteredConfigs.size})"
                    } else null,
                    isFirst = isFirst,
                    isLast = isLast,
                    enabled = enabled,
                    isHiddenForFlight = activeEditorPkg == item.pkg,
                    onOpenFlight = onOpenEditorFlight
                )
            }
        }
    }
}

@Composable
private fun OverscrollAppStreamRow(
    context: Context,
    item: AppConfigItem,
    sectionTitle: String?,
    isFirst: Boolean,
    isLast: Boolean,
    enabled: Boolean,
    isHiddenForFlight: Boolean,
    onOpenFlight: (OverscrollAppFlightTarget) -> Unit
) {
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    var rowBounds by remember { mutableStateOf(Rect.Zero) }
    var appName by remember(item.pkg) { mutableStateOf(item.pkg) }
    var appIcon by remember(item.pkg) { mutableStateOf<Drawable?>(null) }

    LaunchedEffect(item.pkg) {
        withContext(Dispatchers.IO) {
            runCatching {
                val pm = context.packageManager
                val info = pm.getApplicationInfo(item.pkg, 0)
                val label = pm.getApplicationLabel(info).toString()
                val icon = pm.getApplicationIcon(info)
                withContext(Dispatchers.Main) {
                    appName = label
                    appIcon = icon
                }
            }
        }
    }

    val shape = when {
        isFirst && isLast -> RoundedCornerShape(24.dp)
        isFirst -> RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        isLast -> RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)
        else -> RoundedCornerShape(0.dp)
    }

    val bgColor = MaterialTheme.colorScheme.surface
    val outlineColor = bgColor.edgeOutline()
    val strokePx = with(density) { 1.dp.toPx() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(bgColor)
            .then(
                if (isFirst || isLast) {
                    Modifier.border(1.dp, outlineColor, shape)
                } else {
                    Modifier.drawWithContent {
                        drawContent()
                        drawLine(
                            color = outlineColor,
                            start = Offset(0f, 0f),
                            end = Offset(0f, size.height),
                            strokeWidth = strokePx
                        )
                        drawLine(
                            color = outlineColor,
                            start = Offset(size.width, 0f),
                            end = Offset(size.width, size.height),
                            strokeWidth = strokePx
                        )
                    }
                }
            )
    ) {
        if (isFirst && !sectionTitle.isNullOrBlank()) {
            Text(
                text = sectionTitle,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 6.dp)
            )
        }

        if (!isFirst) {
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                modifier = Modifier.padding(horizontal = 20.dp)
            )
        }

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
                .clickable(enabled = enabled && !isHiddenForFlight) {
                    haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                    onOpenFlight(
                        OverscrollAppFlightTarget(
                            item = item,
                            appName = appName,
                            appIcon = appIcon,
                            source = rowBounds,
                            sourceBoundsProvider = { rowBounds }
                        )
                    )
                }
                .padding(
                    start = 20.dp,
                    end = 20.dp,
                    top = 13.dp,
                    bottom = if (isLast) 16.dp else 13.dp
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DrawableIcon(drawable = appIcon, size = 44.dp, fallback = Icons.Rounded.Apps)

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 16.dp, end = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = appName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = item.pkg,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (item.ignore) {
                val badgeBg = MaterialTheme.colorScheme.errorContainer
                Box(
                    modifier = Modifier
                        .padding(end = 6.dp)
                        .clip(CircleShape)
                        .background(badgeBg)
                        .border(1.dp, badgeBg.edgeOutline(), CircleShape)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = stringResource(R.string.os_app_ignore),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            } else {
                val scaleBg = MaterialTheme.colorScheme.secondaryContainer
                Box(
                    modifier = Modifier
                        .padding(end = 6.dp)
                        .clip(CircleShape)
                        .background(scaleBg)
                        .border(1.dp, scaleBg.edgeOutline(), CircleShape)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = String.format(Locale.US, "%.1fx", item.scale),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            Box(
                modifier = Modifier.size(28.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

// ── 1:1 Flight Pseudo-Modal B: App Picker ────────────────────────────────

@Composable
internal fun OverscrollAppPickerFlightOverlay(
    open: Boolean,
    sourceRect: Rect,
    sourceBoundsProvider: () -> Rect,
    existingPackages: Set<String>,
    scrollBlur: GraphicsLayerRecordingState,
    topInsetPx: Float,
    closeSignal: Int,
    onProgress: (Float) -> Unit,
    onClose: () -> Unit,
    onAppSelected: (String) -> Unit
) {
    val flightAnim = remember { Animatable(0f) }
    var closing by remember { mutableStateOf(false) }
    val isVisible = open || closing || flightAnim.value > 0.001f
    if (!isVisible) return

    val context = androidx.compose.ui.platform.LocalContext.current
    val haptics = LocalHapticFeedback.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    val hostCoordsRef = remember { StudioCoordsRef() }
    val cardCoordsRef = remember { StudioCoordsRef() }
    val flightCoordsRef = remember { StudioCoordsRef() }

    var searchQuery by rememberSaveable(open) { mutableStateOf("") }
    var appsList by remember { mutableStateOf<List<AppItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(open) {
        if (open) {
            closing = false
            withFrameNanos { }
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

    LaunchedEffect(open) {
        if (open && appsList.isEmpty()) {
            isLoading = true
            val loaded = withContext(Dispatchers.IO) {
                val pm = context.packageManager
                pm.getInstalledApplications(PackageManager.GET_META_DATA).map {
                    AppItem(
                        name = it.loadLabel(pm).toString(),
                        pkg = it.packageName,
                        icon = runCatching { it.loadIcon(pm) }.getOrNull()
                    )
                }.sortedBy { it.name.lowercase(Locale.getDefault()) }
            }
            appsList = loaded
            isLoading = false
        } else if (open) {
            isLoading = false
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
                closing = false
                afterClose?.invoke()
                onClose()
            }
        }
    }

    BackHandler(enabled = !closing) {
        closeAnimated()
    }

    val initialCloseSignal = remember(open) { closeSignal }
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

    val containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val scrimAlpha = 0.42f * sstepE(morphT, 0.35f, 0.90f)
    val blurReveal = sstepE(morphT, 0.40f, 0.95f)
    val cornerProgress = sstepE(morphT, 0.25f, 0.85f)
    val modalShape = RoundedCornerShape(lerp(14.dp, 26.dp, cornerProgress))
    val cardBorderColor = containerColor.edgeOutline()
    val flightProgress = sstepE(morphT, 0.0f, 0.96f)
    val contentReveal = sstepE(morphT, 0.18f, 0.88f)

    val topInsetDp = with(density) { topInsetPx.coerceAtLeast(0f).toDp() }
    val navBottomDp = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val imeBottomDp = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
    val effectiveBottomDp = maxOf(navBottomDp, imeBottomDp)
    val effectiveBottomPx = with(density) { effectiveBottomDp.toPx() }

    val filteredApps = remember(searchQuery, appsList, existingPackages) {
        val base = appsList.filterNot { it.pkg in existingPackages }
        if (searchQuery.isBlank()) base
        else base.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
                it.pkg.contains(searchQuery, ignoreCase = true)
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(1f)
    ) {
        val maxW = maxWidth
        val maxH = maxHeight
        val fallbackWpx = with(density) { maxWidth.toPx() }
        val fallbackHpx = with(density) { maxHeight.toPx() }

        val liveSource = sourceBoundsProvider().let {
            if (it.width > 1f && it.height > 1f) it else sourceRect
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
            46.dp
        }.coerceIn(42.dp, maxH)

        val cardWidth: Dp = lerp(startW, endW, sstepE(morphT, 0f, 0.90f))
        val cardHeight: Dp = lerp(startH, endH, sstepE(morphT, 0f, 0.92f))

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = scrimAlpha))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { closeAnimated() }
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

            Box(
                modifier = Modifier
                    .width(cardWidth)
                    .height(cardHeight)
                    .onPlaced { flightCoordsRef.value = it }
                    .graphicsLayer {
                        translationX = dx
                        translationY = dy
                    }
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
                                horizontal = lerp(14.dp, 20.dp, cornerProgress),
                                vertical = lerp(11.dp, 16.dp, cornerProgress)
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(lerp(18.dp, 24.dp, cornerProgress))
                        )
                        Spacer(Modifier.width(12.dp))
                        SliderTitleText(
                            title = stringResource(R.string.os_app_select_title),
                            growProgress = sstepE(morphT, 0.12f, 0.80f),
                            maxGrow = 1.3f,
                            baseStyle = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        Box(
                            modifier = Modifier
                                .size(lerp(24.dp, 36.dp, cornerProgress))
                                .clip(CircleShape)
                                .background(
                                    MaterialTheme.colorScheme.primaryContainer.copy(
                                        alpha = 0.72f * cornerProgress
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.KeyboardArrowUp,
                                contentDescription = stringResource(R.string.btn_close),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier
                                    .size(20.dp)
                                    .rotate(lerpFloat(0f, 180f, cornerProgress))
                            )
                        }
                    }

                    if (contentReveal > 0.005f) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .alpha(contentReveal)
                                .padding(horizontal = 20.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            AppIconsSearchField(
                                query = searchQuery,
                                hint = stringResource(R.string.os_app_search_hint),
                                onQueryChange = { searchQuery = it }
                            )

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isLoading) {
                                    LoadingDialogContent()
                                } else {
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        items(filteredApps, key = { it.pkg }) { app ->
                                            val rowBg = MaterialTheme.colorScheme.surface
                                            Card(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable(enabled = !closing) {
                                                        haptics.performHapticFeedback(
                                                            HapticFeedbackType.ContextClick
                                                        )
                                                        closeAnimated {
                                                            onAppSelected(app.pkg)
                                                        }
                                                    },
                                                shape = RoundedCornerShape(16.dp),
                                                colors = CardDefaults.cardColors(containerColor = rowBg),
                                                border = BorderStroke(1.dp, rowBg.edgeOutline())
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    DrawableIcon(
                                                        drawable = app.icon,
                                                        size = 42.dp,
                                                        fallback = Icons.Rounded.Apps
                                                    )
                                                    Column(
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .padding(start = 12.dp)
                                                    ) {
                                                        Text(
                                                            text = app.name,
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            fontWeight = FontWeight.SemiBold,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                        Text(
                                                            text = app.pkg,
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
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

// ── 1:1 Flight Pseudo-Modal C: Individual App Config Editor ──────────────

@Composable
internal fun OverscrollAppEditorFlightOverlay(
    target: OverscrollAppFlightTarget?,
    scrollBlur: GraphicsLayerRecordingState,
    topInsetPx: Float,
    closeSignal: Int,
    onProgress: (Float) -> Unit,
    onClose: () -> Unit,
    onUpdateItem: (AppConfigItem) -> Unit,
    onDeleteItem: (String) -> Unit
) {
    val flight = target ?: return
    var currentItem by remember(flight.sessionId) { mutableStateOf(flight.item) }
    LaunchedEffect(flight.item) {
        currentItem = flight.item
    }

    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    val flightAnim = remember(flight.sessionId) { Animatable(0f) }
    var closing by remember(flight.sessionId) { mutableStateOf(false) }

    val hostCoordsRef = remember(flight.sessionId) { StudioCoordsRef() }
    val cardCoordsRef = remember(flight.sessionId) { StudioCoordsRef() }
    val flightCoordsRef = remember(flight.sessionId) { StudioCoordsRef() }

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

    fun closeAnimated(afterClose: (() -> Unit)? = null) {
        if (closing) return
        closing = true
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
        }.coerceIn(64.dp, maxH)

        val cardWidth: Dp = lerp(startW, endW, sstepE(morphT, 0f, 0.90f))
        val cardHeight: Dp = lerp(startH, endH, sstepE(morphT, 0.0f, 0.92f))

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
                        // 1. 1:1 Morphed Header matching OverscrollAppStreamRow
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
                            DrawableIcon(
                                drawable = flight.appIcon,
                                size = iconDp,
                                fallback = Icons.Rounded.Apps
                            )

                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 16.dp, end = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                SliderTitleText(
                                    title = flight.appName,
                                    growProgress = sstepE(morphT, 0.14f, 0.80f),
                                    maxGrow = 1.25f,
                                    baseStyle = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Medium
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Text(
                                    text = currentItem.pkg,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            if (currentItem.ignore) {
                                val badgeBg = MaterialTheme.colorScheme.errorContainer
                                Box(
                                    modifier = Modifier
                                        .padding(end = 6.dp)
                                        .clip(CircleShape)
                                        .background(badgeBg)
                                        .border(1.dp, badgeBg.edgeOutline(), CircleShape)
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.os_app_ignore),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            } else {
                                val scaleBg = MaterialTheme.colorScheme.secondaryContainer
                                Box(
                                    modifier = Modifier
                                        .padding(end = 6.dp)
                                        .clip(CircleShape)
                                        .background(scaleBg)
                                        .border(1.dp, scaleBg.edgeOutline(), CircleShape)
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = String.format(Locale.US, "%.1fx", currentItem.scale),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier.size(28.dp),
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

                        // 2. Unfolding Body: Switches + Scale Slider (with SliderMorph!) + Delete
                        if (contentReveal > 0.005f) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .alpha(contentReveal)
                                    .padding(bottom = 18.dp)
                                    .verticalScroll(rememberScrollState(), enabled = morphT > 0.7f),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))

                                GenericSwitchRow(
                                    title = stringResource(R.string.os_app_filter),
                                    checked = currentItem.filter,
                                    enabled = !closing && !currentItem.ignore,
                                    onCheckedChange = { checked ->
                                        val updated = currentItem.copy(filter = checked)
                                        currentItem = updated
                                        onUpdateItem(updated)
                                    }
                                )

                                HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))

                                GenericSwitchRow(
                                    title = stringResource(R.string.os_app_ignore),
                                    checked = currentItem.ignore,
                                    enabled = !closing,
                                    onCheckedChange = { checked ->
                                        val updated = currentItem.copy(ignore = checked)
                                        currentItem = updated
                                        onUpdateItem(updated)
                                    }
                                )

                                HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))

                                SliderSettingFloat(
                                    title = stringResource(R.string.os_app_scale),
                                    value = currentItem.scale,
                                    range = 0f..6f,
                                    unit = "x",
                                    enabled = !closing && !currentItem.ignore,
                                    stepSize = 0.1f,
                                    defaultValue = 1.0f,
                                    defaultValueText = "1.0x",
                                    valueText = String.format(Locale.US, "%.1fx", currentItem.scale),
                                    onValueChange = { nextScale ->
                                        val updated = currentItem.copy(scale = nextScale)
                                        currentItem = updated
                                        onUpdateItem(updated)
                                    },
                                    onDefault = {
                                        val updated = currentItem.copy(scale = 1.0f)
                                        currentItem = updated
                                        onUpdateItem(updated)
                                    }
                                )

                                Spacer(Modifier.weight(1f))

                                Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                                    SecondaryActionPill(
                                        icon = Icons.Rounded.Delete,
                                        label = stringResource(R.string.os_prof_delete_desc),
                                        enabled = !closing,
                                        onClick = {
                                            val pkg = currentItem.pkg
                                            closeAnimated { onDeleteItem(pkg) }
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
                    }
                }
            }
        }
    }
}