package org.pixel.customparts.activities

import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.SolidColor
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.util.lerp as lerpFloat
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch
import org.pixel.customparts.R
import org.pixel.customparts.ui.GraphicsLayerRecordingState
import org.pixel.customparts.ui.SliderTitleText
import org.pixel.customparts.ui.edgeOutline
import org.pixel.customparts.utils.ThermalConfigChoice
import org.pixel.customparts.utils.ThermalProfileController
import java.util.Locale
import kotlin.math.roundToInt

@Stable
internal data class ThermalPickerFlightRequest(
    val target: ProfilePickerTarget,
    val title: String,
    val subtitle: String? = null,
    val appIcon: Drawable? = null,
    val isFirstInStream: Boolean = false,
    val isLastInStream: Boolean = false,
    val selectedConfig: String,
    val globalConfig: String = "",
    val includeFollowGlobal: Boolean,
    val choices: List<ThermalConfigChoice>,
    val sourceRect: Rect = Rect.Zero,
    val sourceBoundsProvider: () -> Rect = { sourceRect },
    val sessionId: Long = System.nanoTime()
)

@Composable
internal fun ThermalSystemAppsFilterRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val haptics = LocalHapticFeedback.current
    val selFraction by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = spring(
            stiffness = Spring.StiffnessMediumLow,
            dampingRatio = Spring.DampingRatioNoBouncy
        ),
        label = "ThermalFilterPillFraction"
    )
    val corner by animateDpAsState(
        targetValue = if (checked) 24.dp else 14.dp,
        animationSpec = spring(
            stiffness = Spring.StiffnessMediumLow,
            dampingRatio = Spring.DampingRatioNoBouncy
        ),
        label = "ThermalFilterPillCorner"
    )
    val bgColor by animateColorAsState(
        targetValue = if (checked) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        animationSpec = spring(
            stiffness = Spring.StiffnessMediumLow,
            dampingRatio = Spring.DampingRatioNoBouncy
        ),
        label = "ThermalFilterPillBg"
    )
    val contentColor by animateColorAsState(
        targetValue = if (checked) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = spring(
            stiffness = Spring.StiffnessMediumLow,
            dampingRatio = Spring.DampingRatioNoBouncy
        ),
        label = "ThermalFilterPillFg"
    )
    val shape = RoundedCornerShape(corner)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp)
            .graphicsLayer {
                val scale = 0.97f + 0.03f * selFraction
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(bgColor)
            .border(1.dp, bgColor.edgeOutline(), shape)
            .clickable {
                haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                onCheckedChange(!checked)
            }
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.thermal_manager_show_system_title),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selFraction >= 0.5f) FontWeight.Bold else FontWeight.Medium,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
internal fun ThermalCompactSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    hint: String
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

// ── Tab 2: Seamless Unified App Stream Row (1:1 Flight Ready) ─────────────

@Composable
internal fun ThermalManagedAppStreamRow(
    app: ThermalManagedApp,
    sectionTitle: String?,
    isFirst: Boolean,
    isLast: Boolean,
    selectedConfig: String,
    globalConfig: String,
    choicesById: Map<String, ThermalConfigChoice>,
    isHiddenForFlight: Boolean,
    onOpenFlight: (Rect, () -> Rect) -> Unit
) {
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    var rowBounds by remember { mutableStateOf(Rect.Zero) }

    val globalChoice = choiceForConfig(
        globalConfig.ifBlank { ThermalProfileController.STOCK_CONFIG_ID },
        choicesById,
        includeFollowGlobal = false
    )
    val selectedChoice = if (selectedConfig.isBlank()) {
        globalChoice
    } else {
        choiceForConfig(selectedConfig, choicesById, includeFollowGlobal = true)
    }
    val isCustomAssigned = selectedConfig.isNotBlank()

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
                .clickable(enabled = !isHiddenForFlight) {
                    haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                    onOpenFlight(rowBounds) { rowBounds }
                }
                .padding(
                    start = 20.dp,
                    end = 20.dp,
                    top = 13.dp,
                    bottom = if (isLast) 16.dp else 13.dp
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DrawableIcon(drawable = app.icon, size = 44.dp, fallback = Icons.Rounded.Apps)

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 16.dp, end = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = app.label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (isCustomAssigned) {
                        profileChoiceTitle(selectedChoice)
                    } else {
                        app.packageName
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isCustomAssigned) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (app.isSystem) {
                val sysBadgeBg = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)
                Box(
                    modifier = Modifier
                        .padding(end = 6.dp)
                        .clip(CircleShape)
                        .background(sysBadgeBg)
                        .border(1.dp, sysBadgeBg.edgeOutline(), CircleShape)
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = stringResource(R.string.launcher_hidden_apps_system_badge),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            if (isCustomAssigned) {
                val badgeBg = MaterialTheme.colorScheme.primaryContainer
                Box(
                    modifier = Modifier
                        .padding(end = 6.dp)
                        .clip(CircleShape)
                        .background(badgeBg)
                        .border(1.dp, badgeBg.edgeOutline(), CircleShape)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = stringResource(R.string.thermal_manager_selection_selected),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = 1
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

// ── 1:1 Full-Height Flight Pseudo-Modal: Profile Picker ──────────────────

@Composable
internal fun ThermalConfigPickerFlightOverlay(
    request: ThermalPickerFlightRequest?,
    scrollBlur: GraphicsLayerRecordingState,
    topInsetPx: Float,
    closeSignal: Int,
    onProgress: (Float) -> Unit,
    onClose: () -> Unit,
    onSelect: (ProfilePickerTarget, ThermalConfigChoice) -> Unit
) {
    val req = request ?: return
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    val flightAnim = remember(req.sessionId) { Animatable(0f) }
    var closing by remember(req.sessionId) { mutableStateOf(false) }
    var searchQuery by rememberSaveable(req.sessionId) { mutableStateOf("") }
    var activeChoiceId by remember(req.sessionId) {
        mutableStateOf(selectedProfileChoiceId(req.selectedConfig, req.includeFollowGlobal))
    }

    val choicesById = remember(req.choices) { req.choices.associateBy { it.id } }
    val globalChoice = remember(req.globalConfig, choicesById) {
        choiceForConfig(
            req.globalConfig.ifBlank { ThermalProfileController.STOCK_CONFIG_ID },
            choicesById,
            includeFollowGlobal = false
        )
    }
    val activeChoice = remember(activeChoiceId, choicesById, globalChoice, req.includeFollowGlobal) {
        if (req.includeFollowGlobal && activeChoiceId == ThermalProfileController.FOLLOW_GLOBAL_ID) {
            globalChoice
        } else {
            choiceForConfig(activeChoiceId, choicesById, includeFollowGlobal = req.includeFollowGlobal)
        }
    }

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

    val isGlobalTarget = req.target == ProfilePickerTarget.Global
    val isTileQueueTarget = req.target == ProfilePickerTarget.TileQueue
    val pkgApp = (req.target as? ProfilePickerTarget.Package)?.app

    val startCardColor = when {
        isGlobalTarget -> MaterialTheme.colorScheme.surfaceContainerHigh
        isTileQueueTarget -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surface
    }
    val endCardColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val containerColor = lerpColor(startCardColor, endCardColor, sstepE(morphT, 0.12f, 0.75f))
    val scrimAlpha = 0.42f * sstepE(morphT, 0.45f, 0.92f)
    val blurReveal = sstepE(morphT, 0.50f, 0.96f)
    val cornerProgress = sstepE(morphT, 0.15f, 0.85f)

    val modalShape = when {
        isGlobalTarget -> RoundedCornerShape(lerp(18.dp, 26.dp, cornerProgress))
        isTileQueueTarget -> RoundedCornerShape(lerp(14.dp, 26.dp, cornerProgress))
        else -> {
            val startTop = if (req.isFirstInStream) 24.dp else 0.dp
            val startBottom = if (req.isLastInStream) 24.dp else 0.dp
            RoundedCornerShape(
                topStart = lerp(startTop, 26.dp, cornerProgress),
                topEnd = lerp(startTop, 26.dp, cornerProgress),
                bottomStart = lerp(startBottom, 26.dp, cornerProgress),
                bottomEnd = lerp(startBottom, 26.dp, cornerProgress)
            )
        }
    }
    val cardBorderColor = containerColor.edgeOutline()

    val flightProgress = sstepE(morphT, 0.0f, 0.96f)
    val contentReveal = sstepE(morphT, 0.18f, 0.88f)

    val topInsetDp = with(density) { topInsetPx.coerceAtLeast(0f).toDp() }
    val navBottomDp = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val imeBottomDp = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
    val effectiveBottomDp = maxOf(navBottomDp, imeBottomDp)
    val effectiveBottomPx = with(density) { effectiveBottomDp.toPx() }

    val orderedChoices = remember(req.choices, req.selectedConfig, req.includeFollowGlobal) {
        orderedProfileChoices(req.choices, req.selectedConfig, req.includeFollowGlobal)
    }
    val filteredChoices = remember(orderedChoices, searchQuery, context) {
        val tokens = searchQuery.trim().lowercase(Locale.ROOT)
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
        if (tokens.isEmpty()) {
            orderedChoices
        } else {
            orderedChoices.filter { choice ->
                val haystack = profileChoiceSearchText(context, choice)
                tokens.all { token -> haystack.contains(token) }
            }
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
            if (isTileQueueTarget) 40.dp else 64.dp
        }.coerceIn(36.dp, maxH)

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
                        // 1. 1:1 Target-Specific Morphed Header
                        when {
                            isGlobalTarget -> {
                                // 1:1 with GlobalProfileCard inner selector box
                                val pathText = activeChoice.propertyValue?.takeIf { it.startsWith('/') }
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
                                            horizontal = lerp(16.dp, 20.dp, cornerProgress),
                                            vertical = lerp(14.dp, 16.dp, cornerProgress)
                                        ),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(end = 12.dp)
                                    ) {
                                        SliderTitleText(
                                            title = profileChoiceTitle(activeChoice),
                                            growProgress = sstepE(morphT, 0.14f, 0.80f),
                                            maxGrow = 1.2f,
                                            baseStyle = MaterialTheme.typography.titleMedium.copy(
                                                fontWeight = FontWeight.Bold
                                            ),
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                            Row(
                                                modifier = Modifier.padding(top = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                ProfileTypeBadge(activeChoice)
                                                activeChoice.fileName?.takeIf { it.isNotBlank() }?.let { fileName ->
                                                    Spacer(Modifier.width(6.dp))
                                                    Text(
                                                        text = stringResource(
                                                            R.string.thermal_manager_profile_file,
                                                            fileName
                                                        ),
                                                        style = MaterialTheme.typography.labelMedium,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                            if (pathText != null) {
                                                Text(
                                                    text = stringResource(
                                                        R.string.thermal_manager_profile_path,
                                                        pathText
                                                    ),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                    Icon(
                                        imageVector = Icons.Rounded.KeyboardArrowDown,
                                        contentDescription = stringResource(R.string.btn_close),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.rotate(lerpFloat(0f, 180f, cornerProgress))
                                    )
                                }
                            }

                            isTileQueueTarget -> {
                                // 1:1 with SecondaryActionPill ("+ Добавить")
                                val pillContentColor = lerpColor(
                                    MaterialTheme.colorScheme.onPrimaryContainer,
                                    MaterialTheme.colorScheme.onSurface,
                                    sstepE(morphT, 0.12f, 0.70f)
                                )
                                val pillIconColor = lerpColor(
                                    MaterialTheme.colorScheme.onPrimaryContainer,
                                    MaterialTheme.colorScheme.primary,
                                    sstepE(morphT, 0.12f, 0.70f)
                                )
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(lerp(40.dp, 58.dp, cornerProgress))
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                            enabled = !closing && morphT > 0.5f,
                                            onClick = {
                                                haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                                                closeAnimated()
                                            }
                                        )
                                        .padding(horizontal = lerp(12.dp, 20.dp, cornerProgress)),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Add,
                                        contentDescription = null,
                                        tint = pillIconColor,
                                        modifier = Modifier.size(lerp(16.dp, 22.dp, cornerProgress))
                                    )
                                    Spacer(Modifier.width(lerp(6.dp, 12.dp, cornerProgress)))
                                    Text(
                                        text = req.title,
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontSize = lerp(
                                                MaterialTheme.typography.labelMedium.fontSize,
                                                MaterialTheme.typography.titleMedium.fontSize,
                                                cornerProgress
                                            ),
                                            fontWeight = if (cornerProgress > 0.4f) {
                                                FontWeight.Bold
                                            } else {
                                                FontWeight.SemiBold
                                            }
                                        ),
                                        color = pillContentColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (cornerProgress > 0.05f) {
                                        Box(
                                            modifier = Modifier
                                                .size(28.dp * cornerProgress)
                                                .alpha(cornerProgress),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.KeyboardArrowUp,
                                                contentDescription = stringResource(R.string.btn_close),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(20.dp * cornerProgress)
                                            )
                                        }
                                    }
                                }
                            }

                            else -> {
                                // 1:1 with ThermalManagedAppStreamRow (including conditional badges & subtitle color)
                                val isCustomAssigned =
                                    activeChoiceId != ThermalProfileController.FOLLOW_GLOBAL_ID
                                val assignedBadgeReveal by animateFloatAsState(
                                    targetValue = if (isCustomAssigned) 1f else 0f,
                                    animationSpec = spring(
                                        stiffness = Spring.StiffnessMediumLow,
                                        dampingRatio = Spring.DampingRatioNoBouncy
                                    ),
                                    label = "ThermalAppAssignedBadgeReveal"
                                )
                                val subtitleText = if (isCustomAssigned) {
                                    profileChoiceTitle(activeChoice)
                                } else {
                                    pkgApp?.packageName ?: req.subtitle.orEmpty()
                                }
                                val subtitleColor by animateColorAsState(
                                    targetValue = if (isCustomAssigned) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                                    label = "ThermalAppSubtitleColor"
                                )

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
                                            start = 20.dp,
                                            end = 20.dp,
                                            top = lerp(13.dp, 16.dp, cornerProgress),
                                            bottom = lerp(
                                                if (req.isLastInStream) 16.dp else 13.dp,
                                                16.dp,
                                                cornerProgress
                                            )
                                        ),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    DrawableIcon(
                                        drawable = req.appIcon,
                                        size = lerp(44.dp, 48.dp, cornerProgress),
                                        fallback = Icons.Rounded.Apps
                                    )

                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(start = 16.dp, end = 8.dp),
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        SliderTitleText(
                                            title = req.title,
                                            growProgress = sstepE(morphT, 0.14f, 0.80f),
                                            maxGrow = 1.2f,
                                            baseStyle = MaterialTheme.typography.titleMedium.copy(
                                                fontWeight = FontWeight.Medium
                                            ),
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        Text(
                                            text = subtitleText,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = subtitleColor,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    if (pkgApp?.isSystem == true) {
                                        val sysBadgeBg =
                                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)
                                        Box(
                                            modifier = Modifier
                                                .padding(end = 6.dp)
                                                .clip(CircleShape)
                                                .background(sysBadgeBg)
                                                .border(1.dp, sysBadgeBg.edgeOutline(), CircleShape)
                                                .padding(horizontal = 7.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = stringResource(
                                                    R.string.launcher_hidden_apps_system_badge
                                                ),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onErrorContainer
                                            )
                                        }
                                    }

                                    if (assignedBadgeReveal > 0.005f) {
                                        val badgeBg = MaterialTheme.colorScheme.primaryContainer
                                        Box(
                                            modifier = Modifier
                                                .padding(end = (6f * assignedBadgeReveal).dp)
                                                .graphicsLayer {
                                                    alpha = assignedBadgeReveal
                                                    scaleX = 0.8f + 0.2f * assignedBadgeReveal
                                                    scaleY = 0.8f + 0.2f * assignedBadgeReveal
                                                }
                                                .clip(CircleShape)
                                                .background(badgeBg)
                                                .border(1.dp, badgeBg.edgeOutline(), CircleShape)
                                                .padding(
                                                    horizontal = (8f * assignedBadgeReveal).dp,
                                                    vertical = 2.dp
                                                )
                                        ) {
                                            Text(
                                                text = stringResource(
                                                    R.string.thermal_manager_selection_selected
                                                ),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                maxLines = 1
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
                            }
                        }

                        // 2. Unfolding Search & Profile Choices List
                        if (contentReveal > 0.005f) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .alpha(contentReveal)
                                    .padding(horizontal = 20.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                ThermalCompactSearchField(
                                    query = searchQuery,
                                    onQueryChange = { searchQuery = it },
                                    hint = stringResource(R.string.thermal_manager_profile_search_hint)
                                )

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                ) {
                                    if (filteredChoices.isEmpty()) {
                                        EmptyStateText(
                                            stringResource(R.string.thermal_manager_profile_search_empty)
                                        )
                                    } else {
                                        LazyColumn(
                                            modifier = Modifier.fillMaxSize(),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            items(filteredChoices, key = { it.id }) { choice ->
                                                val isSelected = choice.id == activeChoiceId
                                                ProfilePickerChoiceRow(
                                                    choice = choice,
                                                    selected = isSelected,
                                                    onClick = {
                                                        if (!closing) {
                                                            activeChoiceId = choice.id
                                                            onSelect(req.target, choice)
                                                            closeAnimated()
                                                        }
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

@Composable
private fun ProfilePickerChoiceRow(
    choice: ThermalConfigChoice,
    selected: Boolean,
    onClick: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    val targetContainerColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
    } else {
        MaterialTheme.colorScheme.surface
    }
    val containerColor by animateColorAsState(
        targetValue = targetContainerColor,
        animationSpec = spring(
            stiffness = Spring.StiffnessMediumLow,
            dampingRatio = Spring.DampingRatioNoBouncy
        ),
        label = "pickerRowBg"
    )
    val shape = RoundedCornerShape(16.dp)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, containerColor.edgeOutline(), shape)
            .clickable {
                haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                onClick()
            },
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = profileChoiceTitle(choice),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProfileTypeBadge(choice)
                    choice.fileName?.takeIf { it.isNotBlank() }?.let { fileName ->
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = fileName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            if (selected) {
                Spacer(Modifier.width(12.dp))
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

private fun selectedProfileChoiceId(selectedConfig: String, includeFollowGlobal: Boolean): String {
    val normalized = ThermalProfileController.normalizeConfigId(selectedConfig)
    return when {
        includeFollowGlobal && normalized.isBlank() -> ThermalProfileController.FOLLOW_GLOBAL_ID
        normalized.isBlank() -> ThermalProfileController.STOCK_CONFIG_ID
        else -> normalized.substringAfterLast('/')
    }
}

private fun orderedProfileChoices(
    choices: List<ThermalConfigChoice>,
    selectedConfig: String,
    includeFollowGlobal: Boolean
): List<ThermalConfigChoice> {
    val selectedId = selectedProfileChoiceId(selectedConfig, includeFollowGlobal)
    val usedIds = mutableSetOf<String>()
    val ordered = mutableListOf<ThermalConfigChoice>()

    fun addChoice(choice: ThermalConfigChoice?) {
        if (choice != null && usedIds.add(choice.id)) {
            ordered += choice
        }
    }

    addChoice(choices.firstOrNull { it.id == selectedId })

    if (includeFollowGlobal) {
        addChoice(choices.firstOrNull { it.id == ThermalProfileController.FOLLOW_GLOBAL_ID })
    }

    addChoice(choices.firstOrNull { it.id == ThermalProfileController.STOCK_CONFIG_ID })

    choices
        .filterNot { it.id in usedIds }
        .filterNot { isSystemProfileChoice(it) }
        .sortedWith(compareBy<ThermalConfigChoice> { it.label.lowercase(Locale.ROOT) }.thenBy { it.fileName.orEmpty() })
        .forEach(::addChoice)

    choices
        .filterNot { it.id in usedIds }
        .filter { isSystemProfileChoice(it) }
        .sortedWith(compareBy<ThermalConfigChoice> { it.label.lowercase(Locale.ROOT) }.thenBy { it.fileName.orEmpty() })
        .forEach(::addChoice)

    return ordered
}

private fun isSystemProfileChoice(choice: ThermalConfigChoice): Boolean {
    return choice.builtIn || choice.source == ThermalProfileController.PROFILE_SOURCE_SYSTEM
}

private fun profileChoiceSearchText(context: Context, choice: ThermalConfigChoice): String {
    val title = when (choice.id) {
        ThermalProfileController.FOLLOW_GLOBAL_ID -> context.getString(
            R.string.thermal_manager_follow_global
        )
        ThermalProfileController.STOCK_CONFIG_ID -> context.getString(
            R.string.thermal_manager_stock_profile
        )
        else -> choice.label
    }
    val source = if (isSystemProfileChoice(choice)) {
        context.getString(R.string.thermal_manager_profile_system)
    } else {
        context.getString(R.string.thermal_manager_profile_user)
    }
    return listOfNotNull(
        choice.id,
        title,
        choice.label,
        choice.fileName,
        choice.propertyValue,
        source,
        if (isSystemProfileChoice(choice)) "system" else "user"
    ).joinToString(" ").lowercase(Locale.ROOT)
}

@Composable
internal fun profileChoiceTitle(choice: ThermalConfigChoice): String {
    return when (choice.id) {
        ThermalProfileController.FOLLOW_GLOBAL_ID -> stringResource(R.string.thermal_manager_follow_global)
        ThermalProfileController.STOCK_CONFIG_ID -> stringResource(R.string.thermal_manager_stock_profile)
        else -> choice.label
    }
}

internal fun choiceForConfig(
    configId: String,
    choicesById: Map<String, ThermalConfigChoice>,
    includeFollowGlobal: Boolean
): ThermalConfigChoice {
    val normalized = ThermalProfileController.normalizeConfigId(configId)
    val id = when {
        includeFollowGlobal && normalized.isBlank() -> ThermalProfileController.FOLLOW_GLOBAL_ID
        normalized.isBlank() -> ThermalProfileController.STOCK_CONFIG_ID
        else -> normalized.substringAfterLast('/')
    }
    choicesById[id]?.let { return it }

    return when {
        id == ThermalProfileController.FOLLOW_GLOBAL_ID -> ThermalConfigChoice(
            id = id,
            label = id,
            fileName = null,
            propertyValue = null,
            builtIn = true,
            source = ThermalProfileController.PROFILE_SOURCE_SYSTEM
        )
        id == ThermalProfileController.STOCK_CONFIG_ID -> ThermalConfigChoice(
            id = id,
            label = id,
            fileName = "thermal_info_config.json",
            propertyValue = "thermal_info_config.json",
            builtIn = true,
            source = ThermalProfileController.PROFILE_SOURCE_SYSTEM
        )
        else -> ThermalConfigChoice(
            id = id,
            label = ThermalProfileController.displayName(id),
            fileName = id,
            propertyValue = null,
            builtIn = false,
            source = ThermalProfileController.PROFILE_SOURCE_USER
        )
    }
}