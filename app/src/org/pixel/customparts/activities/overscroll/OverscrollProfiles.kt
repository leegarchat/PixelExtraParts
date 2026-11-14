package org.pixel.customparts.activities

import android.content.Context
import android.os.Build
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.util.lerp as lerpFloat
import androidx.compose.ui.zIndex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.pixel.customparts.R
import org.pixel.customparts.ui.GraphicsLayerRecordingState
import org.pixel.customparts.ui.SettingsGroupCard
import org.pixel.customparts.ui.SliderTitleText
import org.pixel.customparts.ui.edgeOutline
import kotlin.math.roundToInt

@Composable
fun ProfilesSection(
    context: Context,
    scope: CoroutineScope,
    profiles: List<SavedProfile>,
    enabled: Boolean,
    isNetworkFlightOpen: Boolean,
    onProfilesChanged: (List<SavedProfile>) -> Unit,
    onProfileLoaded: () -> Unit,
    onOpenNetworkFlight: (Rect, () -> Rect) -> Unit,
    exportLauncher: ManagedActivityResultLauncher<String, android.net.Uri?>,
    importLauncher: ManagedActivityResultLauncher<Array<String>, android.net.Uri?>,
    refreshKey: Int
) {
    val haptics = LocalHapticFeedback.current
    var activeProfileName by remember { mutableStateOf<String?>(null) }
    var networkBtnBounds by remember { mutableStateOf(Rect.Zero) }
    val armedProfiles = remember { mutableStateMapOf<String, Long>() }

    LaunchedEffect(refreshKey, profiles) {
        val savedName = withContext(Dispatchers.IO) {
            OverscrollManager.getActiveProfileName(context)
        }
        activeProfileName = savedName
    }

    SettingsGroupCard(
        title = stringResource(R.string.os_group_profiles),
        enabled = enabled
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 1. Inline Morphing Save Profile Bar (Zero AlertDialogs!)
            MorphingNameCompileBar(
                buttonLabel = stringResource(R.string.os_prof_btn_save),
                placeholderLabel = stringResource(R.string.os_prof_dialog_hint),
                enabled = enabled,
                onCompile = { profileName ->
                    scope.launch {
                        OverscrollManager.saveProfile(context, profileName)
                        onProfilesChanged(OverscrollManager.getSavedProfiles(context))
                        activeProfileName = profileName
                    }
                }
            )

            // 2. Import / Export / Online Presets Pills
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SecondaryActionPill(
                    icon = Icons.Outlined.FileDownload,
                    label = stringResource(R.string.os_prof_btn_import),
                    enabled = enabled,
                    onClick = { importLauncher.launch(arrayOf("application/json")) },
                    modifier = Modifier.weight(1f)
                )
                SecondaryActionPill(
                    icon = Icons.Outlined.FileUpload,
                    label = stringResource(R.string.os_prof_btn_export),
                    enabled = enabled,
                    onClick = { exportLauncher.launch("overscroll_settings.json") },
                    modifier = Modifier.weight(1f)
                )
            }

            // 3. Online Cloud Configs Trigger (1:1 Flight Source)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .onPlaced {
                        val r = it.unclippedBoundsInRoot()
                        if (r.width > 1f && r.height > 1f) networkBtnBounds = r
                    }
                    .onGloballyPositioned {
                        val r = it.unclippedBoundsInRoot()
                        if (r.width > 1f && r.height > 1f) networkBtnBounds = r
                    }
                    .alpha(if (isNetworkFlightOpen) 0f else 1f)
            ) {
                SecondaryActionPill(
                    icon = Icons.Default.CloudDownload,
                    label = stringResource(R.string.os_prof_btn_network),
                    enabled = enabled && !isNetworkFlightOpen,
                    onClick = { onOpenNetworkFlight(networkBtnBounds) { networkBtnBounds } },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }

            // 4. Saved Profiles List with StudioTabStyledApplyButton & 2-Tap Trash
            if (profiles.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.os_prof_header_list),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    profiles.forEach { profile ->
                        val isActive = profile.name == activeProfileName
                        val armedTime = armedProfiles[profile.name] ?: 0L
                        val isTrashArmed = armedTime > 0L

                        LaunchedEffect(armedTime) {
                            if (armedTime <= 0L) return@LaunchedEffect
                            delay(3000L)
                            if (armedProfiles[profile.name] == armedTime) {
                                armedProfiles.remove(profile.name)
                            }
                        }

                        val cardBg by animateColorAsState(
                            targetValue = if (isActive) {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerLow
                            },
                            label = "SavedProfileCardBg"
                        )

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = cardBg),
                            border = BorderStroke(1.dp, cardBg.edgeOutline())
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = profile.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = if (isActive) {
                                            stringResource(R.string.os_prof_item_active)
                                        } else {
                                            stringResource(R.string.os_prof_item_tap)
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isActive) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        maxLines = 1
                                    )
                                }

                                StudioTabStyledApplyButton(
                                    isActive = isActive,
                                    applyLabel = stringResource(R.string.btn_apply),
                                    activeLabel = stringResource(R.string.os_status_active),
                                    enabled = enabled && !isActive,
                                    onClick = {
                                        activeProfileName = profile.name
                                        scope.launch {
                                            OverscrollManager.loadProfile(context, profile)
                                            onProfileLoaded()
                                        }
                                    }
                                )

                                val trashCorner by animateFloatAsState(
                                    targetValue = if (isTrashArmed) 24f else 12f,
                                    animationSpec = spring(
                                        stiffness = Spring.StiffnessMedium,
                                        dampingRatio = Spring.DampingRatioNoBouncy
                                    ),
                                    label = "ProfileTrashCorner"
                                )
                                val trashBg = MaterialTheme.colorScheme.errorContainer
                                val trashShape = RoundedCornerShape(trashCorner.dp)

                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(trashShape)
                                        .background(trashBg)
                                        .border(1.dp, trashBg.edgeOutline(), trashShape)
                                        .clickable(enabled = enabled) {
                                            haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                                            if (isTrashArmed) {
                                                armedProfiles.remove(profile.name)
                                                scope.launch {
                                                    OverscrollManager.deleteProfile(context, profile)
                                                    onProfilesChanged(OverscrollManager.getSavedProfiles(context))
                                                }
                                            } else {
                                                armedProfiles[profile.name] = SystemClock.uptimeMillis()
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (isTrashArmed) Icons.Rounded.Check else Icons.Rounded.Delete,
                                        contentDescription = stringResource(R.string.os_prof_delete_desc),
                                        tint = MaterialTheme.colorScheme.onErrorContainer,
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

// ── 1:1 Full-Height Flight Pseudo-Modal: Online Network Configs ──────────

@Composable
internal fun NetworkConfigsFlightOverlay(
    open: Boolean,
    sourceRect: Rect,
    sourceBoundsProvider: () -> Rect,
    scrollBlur: GraphicsLayerRecordingState,
    topInsetPx: Float,
    closeSignal: Int,
    onProgress: (Float) -> Unit,
    onClose: () -> Unit,
    onConfigApplied: (String) -> Unit
) {
    val flightAnim = remember { Animatable(0f) }
    var closing by remember { mutableStateOf(false) }
    val isVisible = open || closing || flightAnim.value > 0.001f
    if (!isVisible) return

    val context = androidx.compose.ui.platform.LocalContext.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    val hostCoordsRef = remember { StudioCoordsRef() }
    val cardCoordsRef = remember { StudioCoordsRef() }
    val flightCoordsRef = remember { StudioCoordsRef() }

    var configs by remember(open) { mutableStateOf<List<OverscrollManager.NetworkConfig>>(emptyList()) }
    var isLoading by remember(open) { mutableStateOf(true) }
    var isError by remember(open) { mutableStateOf(false) }
    var applyingConfig by remember(open) { mutableStateOf<String?>(null) }

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
        if (open) {
            isLoading = true
            isError = false
            val result = OverscrollManager.fetchNetworkConfigs(context)
            result.fold(
                onSuccess = {
                    configs = it
                    isLoading = false
                },
                onFailure = {
                    isError = true
                    isLoading = false
                }
            )
        }
    }

    fun closeAnimated(afterClose: (() -> Unit)? = null) {
        if (closing || applyingConfig != null) return
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

        val liveSource = sourceBoundsProvider().let {
            if (it.width > 1f && it.height > 1f) it else sourceRect
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
            44.dp
        }.coerceIn(40.dp, maxH)

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
                    // Header Row
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
                                horizontal = lerp(12.dp, 20.dp, cornerProgress),
                                vertical = lerp(10.dp, 16.dp, cornerProgress)
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudDownload,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(lerp(16.dp, 24.dp, cornerProgress))
                        )
                        Spacer(Modifier.width(12.dp))
                        SliderTitleText(
                            title = stringResource(R.string.os_net_dialog_title),
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
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .alpha(contentReveal)
                                .padding(horizontal = 20.dp, vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            when {
                                isLoading -> LoadingDialogContent()
                                isError -> EmptyStateText(stringResource(R.string.os_net_error))
                                configs.isEmpty() -> EmptyStateText(stringResource(R.string.os_net_empty))
                                else -> LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(configs, key = { it.name }) { config ->
                                        val isApplying = applyingConfig == config.name
                                        val rowBg = MaterialTheme.colorScheme.surface
                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(16.dp),
                                            colors = CardDefaults.cardColors(containerColor = rowBg),
                                            border = BorderStroke(1.dp, rowBg.edgeOutline())
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = config.name,
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.SemiBold,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                if (isApplying) {
                                                    CircularProgressIndicator(
                                                        modifier = Modifier.size(22.dp),
                                                        strokeWidth = 2.dp
                                                    )
                                                } else {
                                                    StudioTabStyledApplyButton(
                                                        isActive = false,
                                                        applyLabel = stringResource(R.string.btn_apply),
                                                        activeLabel = stringResource(R.string.btn_apply),
                                                        enabled = applyingConfig == null && !closing,
                                                        onClick = {
                                                            applyingConfig = config.name
                                                            scope.launch {
                                                                val ok = OverscrollManager.applyNetworkConfig(
                                                                    context,
                                                                    config
                                                                )
                                                                applyingConfig = null
                                                                if (ok) {
                                                                    closeAnimated {
                                                                        onConfigApplied(config.name)
                                                                    }
                                                                }
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
}