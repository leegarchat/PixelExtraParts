package org.pixel.customparts.activities

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.BatteryStd
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.util.lerp as lerpFloat
import androidx.compose.ui.zIndex
import androidx.preference.PreferenceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.pixel.customparts.AppConfig
import org.pixel.customparts.R
import org.pixel.customparts.dynamicDarkColorScheme
import org.pixel.customparts.dynamicLightColorScheme
import org.pixel.customparts.ui.ExpandableWarningCard
import org.pixel.customparts.ui.GraphicsLayerRecordingState
import org.pixel.customparts.ui.TopBarBlurOverlay
import org.pixel.customparts.ui.TopStatusVeil
import org.pixel.customparts.ui.edgeOutline
import org.pixel.customparts.ui.recordLayer
import org.pixel.customparts.ui.rememberGraphicsLayerRecordingState
import org.pixel.customparts.utils.AutoHbmController
import org.pixel.customparts.utils.ThermalProfileController
import kotlin.math.roundToInt

@Stable
private data class ThermalRebootFlightRequest(
    val sectionKey: String,
    val modeId: Int,
    val modeLabel: String,
    val isRiskyConfig: Boolean,
    val sourceRect: Rect = Rect.Zero,
    val sourceBoundsProvider: () -> Rect = { sourceRect },
    val sessionId: Long = System.nanoTime()
)

class ThermalActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!AppConfig.ENABLE_THERMALS) {
            finish()
            return
        }
        enableEdgeToEdge()
        setContent {
            val darkTheme = isSystemInDarkTheme()
            val context = LocalContext.current
            val colorScheme = if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

            MaterialTheme(colorScheme = colorScheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ThermalScreen(onBack = { finish() })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThermalScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    val scrollBlur = rememberGraphicsLayerRecordingState()
    val modalBlur = rememberGraphicsLayerRecordingState()
    val lazyListState = rememberLazyListState()

    var batteryProfile by remember { mutableIntStateOf(0) }
    var socProfile by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }

    var rebootFlight by remember { mutableStateOf<ThermalRebootFlightRequest?>(null) }
    var rebootFlightT by remember { mutableFloatStateOf(0f) }
    var closeSignal by remember { mutableIntStateOf(0) }

    val socModel = remember { AutoHbmController.getSocModel() }
    var socTempC by remember { mutableStateOf(AutoHbmController.readSocTemperatureC()) }

    LaunchedEffect(Unit) {
        val (batIndex, socIndex) = ThermalManager.getCurrentProfiles(context)
        batteryProfile = batIndex
        socProfile = socIndex
        isLoading = false
    }

    LaunchedEffect(Unit) {
        while (true) {
            socTempC = withContext(Dispatchers.IO) { AutoHbmController.readSocTemperatureC() }
            delay(1500L)
        }
    }

    val statusBarTopDp = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottomDp = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val topBarRowHeightDp = 56.dp
    val effectiveHeaderHeightDp = statusBarTopDp + topBarRowHeightDp
    val effectiveHeaderHeightPx = with(density) { effectiveHeaderHeightDp.toPx() }
    val anyModalOpen = rebootFlight != null

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (anyModalOpen) {
                        Modifier.recordLayer(
                            state = modalBlur,
                            backgroundColor = MaterialTheme.colorScheme.surfaceContainer
                        )
                    } else {
                        Modifier
                    }
                )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .recordLayer(
                        state = scrollBlur,
                        backgroundColor = MaterialTheme.colorScheme.surfaceContainer
                    )
            ) {
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        top = effectiveHeaderHeightDp + 12.dp,
                        end = 16.dp,
                        bottom = navBottomDp + 28.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item(key = "thermal_warning") {
                        ExpandableWarningCard(
                            title = stringResource(R.string.thermal_info_title),
                            text = stringResource(R.string.thermal_info_summary)
                        )
                    }

                    item(key = "thermal_battery_section") {
                        ThermalSection(
                            title = stringResource(R.string.thermal_sec_battery),
                            icon = Icons.Rounded.BatteryStd,
                            currentMode = batteryProfile,
                            modes = ThermalManager.getModes(),
                            isLoading = isLoading,
                            hiddenModeId = if (rebootFlight?.sectionKey == "battery") rebootFlight?.modeId else null,
                            onModeSelect = { newModeIndex, modeLabel, bounds, provider ->
                                batteryProfile = newModeIndex
                                scope.launch {
                                    ThermalManager.setBatteryProfile(context, newModeIndex)
                                }
                                rebootFlight = ThermalRebootFlightRequest(
                                    sectionKey = "battery",
                                    modeId = newModeIndex,
                                    modeLabel = modeLabel,
                                    isRiskyConfig = newModeIndex >= 3,
                                    sourceRect = bounds,
                                    sourceBoundsProvider = provider
                                )
                            },
                            getDescription = { mode -> ThermalManager.getBatteryDescription(mode) }
                        )
                    }

                    item(key = "thermal_soc_section") {
                        ThermalSection(
                            title = "$socModel • ${stringResource(R.string.thermal_sec_soc)}" +
                                (socTempC?.let { String.format(" (%.1f°C)", it) } ?: ""),
                            icon = Icons.Rounded.Memory,
                            currentMode = socProfile,
                            modes = ThermalManager.getModes(),
                            isLoading = isLoading,
                            hiddenModeId = if (rebootFlight?.sectionKey == "soc") rebootFlight?.modeId else null,
                            onModeSelect = { newModeIndex, modeLabel, bounds, provider ->
                                socProfile = newModeIndex
                                scope.launch {
                                    ThermalManager.setSocProfile(context, newModeIndex)
                                }
                                rebootFlight = ThermalRebootFlightRequest(
                                    sectionKey = "soc",
                                    modeId = newModeIndex,
                                    modeLabel = modeLabel,
                                    isRiskyConfig = newModeIndex >= 3,
                                    sourceRect = bounds,
                                    sourceBoundsProvider = provider
                                )
                            },
                            getDescription = { mode -> ThermalManager.getSocDescription(mode) }
                        )
                    }
                }
            }

            ThermalRebootFlightOverlay(
                request = rebootFlight,
                scrollBlur = scrollBlur,
                topInsetPx = effectiveHeaderHeightPx,
                closeSignal = closeSignal,
                onProgress = { rebootFlightT = it },
                onClose = { rebootFlight = null },
                onConfirmReboot = {
                    rebootFlight = null
                    rebootDevice(context)
                }
            )
        }

        TopBarBlurOverlay(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter),
            topBarHeight = effectiveHeaderHeightDp,
            blurState = scrollBlur,
            morphBlurState = modalBlur,
            morphActive = anyModalOpen,
            tintBoost = 0.25f * rebootFlightT
        )

        TopStatusVeil(
            backgroundColor = MaterialTheme.colorScheme.surfaceContainer,
            veilHeight = statusBarTopDp + 14.dp,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .zIndex(4f)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
                .padding(top = statusBarTopDp)
                .height(topBarRowHeightDp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.nav_back)
                )
            }
            Text(
                text = stringResource(R.string.thermal_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }

        if (anyModalOpen) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(effectiveHeaderHeightDp)
                    .align(Alignment.TopCenter)
                    .zIndex(5f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { closeSignal++ }
                    )
            )
        }
    }
}

@Composable
private fun ThermalRebootFlightOverlay(
    request: ThermalRebootFlightRequest?,
    scrollBlur: GraphicsLayerRecordingState,
    topInsetPx: Float,
    closeSignal: Int,
    onProgress: (Float) -> Unit,
    onClose: () -> Unit,
    onConfirmReboot: () -> Unit
) {
    val req = request ?: return
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    val flightAnim = remember(req.sessionId) { Animatable(0f) }
    var closing by remember(req.sessionId) { mutableStateOf(false) }

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
                    stiffness = 115f,
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
                        stiffness = 165f,
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

    val containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val scrimAlpha = 0.42f * sstepE(morphT, 0.35f, 0.90f)
    val blurReveal = sstepE(morphT, 0.35f, 0.95f)
    val cornerProgress = sstepE(morphT, 0.20f, 0.85f)
    val modalShape = RoundedCornerShape(lerp(14.dp, 26.dp, cornerProgress))
    val cardBorderColor = containerColor.edgeOutline()
    val flightProgress = sstepE(morphT, 0.0f, 0.96f)
    val bodyUnfold = sstepE(morphT, 0.16f, 0.88f)

    val topInsetDp = with(density) { topInsetPx.coerceAtLeast(0f).toDp() }
    val navBottomDp = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val navBottomPx = with(density) { navBottomDp.toPx() }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(1f)
    ) {
        val maxW = maxWidth
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
                        .background(containerColor.copy(alpha = 0.88f))
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
                            .background(containerColor.copy(alpha = 0.66f))
                    )
                }

                val msg = if (req.isRiskyConfig) {
                    stringResource(R.string.thermal_dialog_risk_msg)
                } else {
                    stringResource(R.string.thermal_dialog_safe_msg)
                }
                val accentTint = if (req.isRiskyConfig) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = lerp(14.dp, 22.dp, cornerProgress),
                            vertical = lerp(13.dp, 20.dp, cornerProgress)
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // 1:1 Header Row matching the selected mode row in ThermalSection at morphT = 0f
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = req.modeLabel,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(12.dp))
                        Icon(
                            imageVector = if (req.isRiskyConfig && cornerProgress > 0.5f) {
                                Icons.Rounded.Warning
                            } else {
                                Icons.Rounded.CheckCircle
                            },
                            contentDescription = null,
                            tint = accentTint
                        )
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .unfoldVerticalSlot(bodyUnfold)
                            .alpha(bodyUnfold),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(Modifier.height(14.dp))
                        Text(
                            text = stringResource(R.string.thermal_dialog_reboot_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(20.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            SecondaryActionPill(
                                icon = Icons.Rounded.Close,
                                label = stringResource(R.string.btn_ok),
                                enabled = !closing,
                                onClick = { closeAnimated() },
                                modifier = Modifier.weight(1f)
                            )
                            SecondaryActionPill(
                                icon = Icons.Rounded.Refresh,
                                label = stringResource(R.string.btn_reboot),
                                enabled = !closing,
                                onClick = {
                                    closeAnimated { onConfirmReboot() }
                                },
                                modifier = Modifier.weight(1.2f),
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

private fun rebootDevice(context: Context) {
    try {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        pm.reboot(null)
    } catch (e: Exception) {
        Log.e("ThermalActivity", "Failed to reboot via PowerManager", e)
        try {
            Runtime.getRuntime().exec(arrayOf("su", "-c", "svc power reboot"))
        } catch (ex: Exception) {
            Log.e("ThermalActivity", "Shell reboot failed", ex)
        }
    }
}

@Composable
fun ThermalSection(
    title: String,
    icon: ImageVector,
    currentMode: Int,
    modes: List<ThermalMode>,
    isLoading: Boolean,
    hiddenModeId: Int? = null,
    onModeSelect: (Int, String, Rect, () -> Rect) -> Unit,
    getDescription: @Composable (Int) -> String
) {
    val haptics = LocalHapticFeedback.current
    val isDangerous = currentMode >= 3
    val targetContainerColor = if (isDangerous) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.surface
    }

    val containerColor by animateColorAsState(
        targetContainerColor,
        animationSpec = spring(
            stiffness = Spring.StiffnessMediumLow,
            dampingRatio = Spring.DampingRatioNoBouncy
        ),
        label = "containerColor"
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, containerColor.edgeOutline()),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.padding(start = 6.dp, end = 6.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.65f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        icon,
                        null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            modes.forEach { mode ->
                val isSelected = mode.id == currentMode && !isLoading
                var rowBounds by remember { mutableStateOf(Rect.Zero) }

                val selectedColor = if (isDangerous) {
                    MaterialTheme.colorScheme.error.copy(alpha = 0.16f)
                } else {
                    MaterialTheme.colorScheme.secondaryContainer
                }

                val rowBackgroundColor by animateColorAsState(
                    targetValue = if (isSelected) selectedColor else Color.Transparent,
                    animationSpec = spring(
                        stiffness = Spring.StiffnessMediumLow,
                        dampingRatio = Spring.DampingRatioNoBouncy
                    ),
                    label = "rowBg"
                )

                val weightVal by animateIntAsState(
                    targetValue = if (isSelected) 700 else 500,
                    animationSpec = spring(
                        stiffness = Spring.StiffnessMedium,
                        dampingRatio = Spring.DampingRatioNoBouncy
                    ),
                    label = "weight"
                )

                val modeLabel = stringResource(mode.labelRes)
                val isHiddenForFlight = hiddenModeId == mode.id

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                        .onPlaced {
                            val r = it.unclippedBoundsInRoot()
                            if (r.width > 1f && r.height > 1f) rowBounds = r
                        }
                        .onGloballyPositioned {
                            val r = it.unclippedBoundsInRoot()
                            if (r.width > 1f && r.height > 1f) rowBounds = r
                        }
                        .alpha(if (isHiddenForFlight) 0f else 1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(rowBackgroundColor)
                        .then(
                            if (isSelected) {
                                Modifier.border(
                                    1.dp,
                                    rowBackgroundColor.edgeOutline(),
                                    RoundedCornerShape(14.dp)
                                )
                            } else {
                                Modifier
                            }
                        )
                        .clickable(enabled = !isHiddenForFlight) {
                            haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                            onModeSelect(mode.id, modeLabel, rowBounds) { rowBounds }
                        }
                        .padding(horizontal = 14.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(mode.labelRes),
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight(weightVal)
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    AnimatedVisibility(
                        visible = isSelected,
                        modifier = Modifier.padding(start = 12.dp),
                        enter = scaleIn(
                            animationSpec = spring(
                                stiffness = Spring.StiffnessMedium,
                                dampingRatio = Spring.DampingRatioMediumBouncy
                            )
                        ) + fadeIn(),
                        exit = scaleOut() + fadeOut()
                    ) {
                        Icon(
                            Icons.Rounded.CheckCircle,
                            null,
                            tint = if (isDangerous) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.35f),
                        RoundedCornerShape(14.dp)
                    )
                    .padding(10.dp)
            ) {
                Text(
                    text = getDescription(currentMode),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

data class ThermalMode(val id: Int, val labelRes: Int)

internal fun Color.thermalEdgeOutline(alpha: Float = 0.45f): Color = edgeOutline(alpha)

object ThermalManager {
    private const val TAG = "PixelPartsThermal"

    private const val KEY_THERMAL_BATTERY = "thermal_battery_mode"
    private const val KEY_THERMAL_SOC = "thermal_soc_mode"

    private const val PROP_BATTERY = "persist.sys.pixelparts.battery"
    private const val PROP_SOC = "persist.sys.pixelparts.soc"

    private const val MODE_STOCK = 0
    private const val MODE_SOFT = 1
    private const val MODE_MEDIUM = 2
    private const val MODE_HARD = 3
    private const val MODE_OFF = 4

    private val MODE_STRINGS = listOf("stock", "soft", "medium", "hard", "off")

    fun getModes(): List<ThermalMode> = listOf(
        ThermalMode(MODE_STOCK, R.string.thermal_mode_stock),
        ThermalMode(MODE_SOFT, R.string.thermal_mode_soft),
        ThermalMode(MODE_MEDIUM, R.string.thermal_mode_medium),
        ThermalMode(MODE_HARD, R.string.thermal_mode_hard),
        ThermalMode(MODE_OFF, R.string.thermal_mode_off)
    )

    @Composable
    fun getBatteryDescription(mode: Int): String {
        val resId = when (mode) {
            MODE_STOCK -> R.string.thermal_desc_bat_stock
            MODE_SOFT -> R.string.thermal_desc_bat_soft
            MODE_MEDIUM -> R.string.thermal_desc_bat_medium
            MODE_HARD -> R.string.thermal_desc_bat_hard
            MODE_OFF -> R.string.thermal_desc_bat_off
            else -> R.string.thermal_desc_bat_stock
        }
        return stringResource(resId)
    }

    @Composable
    fun getSocDescription(mode: Int): String {
        val resId = when (mode) {
            MODE_STOCK -> R.string.thermal_desc_soc_stock
            MODE_SOFT -> R.string.thermal_desc_soc_soft
            MODE_MEDIUM -> R.string.thermal_desc_soc_medium
            MODE_HARD -> R.string.thermal_desc_soc_hard
            MODE_OFF -> R.string.thermal_desc_soc_off
            else -> R.string.thermal_desc_soc_stock
        }
        return stringResource(resId)
    }

    fun onBoot(context: Context) {
        if (!AppConfig.ENABLE_THERMALS) {
            Log.d(TAG, "Thermal configs are unavailable on this target")
            return
        }
        Log.d(TAG, "onBoot: Re-applying Thermal config")
        updateThermalProps(context)
    }

    suspend fun getCurrentProfiles(context: Context): Pair<Int, Int> = withContext(Dispatchers.IO) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val batStr = prefs.getString(KEY_THERMAL_BATTERY, "stock") ?: "stock"
        val socStr = prefs.getString(KEY_THERMAL_SOC, "stock") ?: "stock"

        val batIndex = MODE_STRINGS.indexOf(batStr).takeIf { it >= 0 } ?: MODE_STOCK
        val socIndex = MODE_STRINGS.indexOf(socStr).takeIf { it >= 0 } ?: MODE_STOCK

        return@withContext Pair(batIndex, socIndex)
    }

    suspend fun setBatteryProfile(context: Context, modeIndex: Int) {
        val modeStr = MODE_STRINGS.getOrElse(modeIndex) { "stock" }
        withContext(Dispatchers.IO) {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            prefs.edit().putString(KEY_THERMAL_BATTERY, modeStr).apply()
            updateThermalProps(context)
        }
    }

    suspend fun setSocProfile(context: Context, modeIndex: Int) {
        val modeStr = MODE_STRINGS.getOrElse(modeIndex) { "stock" }
        withContext(Dispatchers.IO) {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            prefs.edit().putString(KEY_THERMAL_SOC, modeStr).apply()
            updateThermalProps(context)
        }
    }

    private fun updateThermalProps(context: Context) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val batteryMode = prefs.getString(KEY_THERMAL_BATTERY, "stock") ?: "stock"
        val socMode = prefs.getString(KEY_THERMAL_SOC, "stock") ?: "stock"

        Log.d(TAG, "Updating thermal props. Battery: $batteryMode, SoC:$socMode")

        setSystemProperty(PROP_BATTERY, batteryMode)
        setSystemProperty(PROP_SOC, socMode)

        val configFileName = buildConfigFileName(batteryMode, socMode)
        if (!ThermalProfileController.applyConfig(context, configFileName)) {
            Log.w(TAG, "Failed to apply thermal config $configFileName")
        }
    }

    private fun buildConfigFileName(batteryMode: String, socMode: String): String {
        val parts = mutableListOf<String>()
        if (batteryMode != "stock") parts += "battery_$batteryMode"
        if (socMode != "stock") parts += "soc_$socMode"
        return if (parts.isEmpty()) "thermal_info_config.json" else parts.joinToString("_") + ".json"
    }

    @SuppressLint("PrivateApi")
    private fun setSystemProperty(key: String, value: String): Boolean {
        try {
            val c = Class.forName("android.os.SystemProperties")
            val set = c.getMethod("set", String::class.java, String::class.java)
            set.invoke(null, key, value)
            return true
        } catch (e: Exception) {
            Log.w(TAG, "Reflection set failed ($key), trying root...", e)
        }

        try {
            return Runtime.getRuntime().exec(arrayOf("su", "-c", "svc power reboot")).waitFor() == 0
        } catch (e: Exception) {
            Log.e(TAG, "Root set failed for $key", e)
        }
        return false
    }
}