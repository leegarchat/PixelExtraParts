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

internal const val DEFAULT_CUSTOM_SHAPE_SIDES = 6
internal const val DEFAULT_CUSTOM_SHAPE_ROUNDNESS = 4.5f
internal const val DEFAULT_CUSTOM_SHAPE_INSET = 0f
internal const val DEFAULT_CUSTOM_SHAPE_WAVE = 0.12f
internal const val DEFAULT_CUSTOM_SHAPE_ROTATION = 0f
internal const val DEFAULT_CUSTOM_SHAPE_SCALE = 1f
internal const val DEFAULT_CUSTOM_SHAPE_OFFSET = 0f

private const val TAB_SHAPE_PRESETS = 0
private const val TAB_SHAPE_BUILDER = 1

@Stable
internal data class ShapeFlightTarget(
    val key: String,
    val option: ShapeOption,
    val source: Rect = Rect.Zero,
    val sourceBoundsProvider: () -> Rect = { source },
    val isTrashArmedProvider: () -> Boolean = { false },
    val sessionId: Long = System.nanoTime()
)

class IconShapeActivity : ComponentActivity() {
    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        setContent {
            val context = LocalContext.current
            val darkTheme = isSystemInDarkTheme()
            val colorScheme = if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            MaterialTheme(colorScheme = colorScheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    IconShapeScreen(onBack = { finish() })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IconShapeScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    // 3-Pass Non-Cyclic Blur Stack:
    // Pass 1 (scrollBlur): records pager content
    // Pass 2 (cardModalBlur): records [Pass 1 + Level-1 Flight Pseudo-Modals]
    // Pass 3 (sliderModalBlur): records [Pass 2 + Level-2 SliderMorphOverlay]
    val scrollBlur = rememberGraphicsLayerRecordingState()
    val cardModalBlur = rememberGraphicsLayerRecordingState()
    val sliderModalBlur = rememberGraphicsLayerRecordingState()
    val sliderMorphHost = rememberSliderMorphHostState()

    val pagerState = rememberStudioPagerState(2)
    val presetsGridState = rememberLazyGridState()
    val builderListState = rememberLazyListState()

    var options by remember { mutableStateOf<List<ShapeOption>>(emptyList()) }
    var isInitialLoading by remember { mutableStateOf(true) }
    var isSilentRefreshing by remember { mutableStateOf(false) }
    var isBusy by remember { mutableStateOf(false) }

    // Builder parameters
    var mode by rememberSaveable { mutableIntStateOf(IconShapeOverlayManager.MODE_SUPERELLIPSE) }
    var sides by rememberSaveable { mutableIntStateOf(DEFAULT_CUSTOM_SHAPE_SIDES) }
    var roundness by rememberSaveable { mutableFloatStateOf(DEFAULT_CUSTOM_SHAPE_ROUNDNESS) }
    var inset by rememberSaveable { mutableFloatStateOf(DEFAULT_CUSTOM_SHAPE_INSET) }
    var wave by rememberSaveable { mutableFloatStateOf(DEFAULT_CUSTOM_SHAPE_WAVE) }
    var rotation by rememberSaveable { mutableFloatStateOf(DEFAULT_CUSTOM_SHAPE_ROTATION) }
    var scaleX by rememberSaveable { mutableFloatStateOf(DEFAULT_CUSTOM_SHAPE_SCALE) }
    var scaleY by rememberSaveable { mutableFloatStateOf(DEFAULT_CUSTOM_SHAPE_SCALE) }
    var offsetX by rememberSaveable { mutableFloatStateOf(DEFAULT_CUSTOM_SHAPE_OFFSET) }
    var offsetY by rememberSaveable { mutableFloatStateOf(DEFAULT_CUSTOM_SHAPE_OFFSET) }
    var cornerRadius by rememberSaveable { mutableFloatStateOf(0f) }
    var innerRadius by rememberSaveable { mutableFloatStateOf(0.55f) }
    var pinch by rememberSaveable { mutableFloatStateOf(0f) }
    var twist by rememberSaveable { mutableFloatStateOf(0f) }
    var skewX by rememberSaveable { mutableFloatStateOf(0f) }
    var skewY by rememberSaveable { mutableFloatStateOf(0f) }
    var cornerTL by rememberSaveable { mutableFloatStateOf(24f) }
    var cornerTR by rememberSaveable { mutableFloatStateOf(24f) }
    var cornerBR by rememberSaveable { mutableFloatStateOf(24f) }
    var cornerBL by rememberSaveable { mutableFloatStateOf(24f) }

    var importedUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var importedPath by rememberSaveable { mutableStateOf("") }

    // Compile & Flight Pseudo-Modal states (ZERO Dialogs!)
    var shapeFlight by remember { mutableStateOf<ShapeFlightTarget?>(null) }
    var shapeFlightT by remember { mutableFloatStateOf(0f) }
    var shapeFlightClosing by remember { mutableStateOf(false) }
    val armedCustomShapes = remember { mutableStateMapOf<String, Long>() }

    var builderPreviewOpen by remember { mutableStateOf(false) }
    var builderPreviewSource by remember { mutableStateOf(Rect.Zero) }
    var builderPreviewBoundsProvider by remember { mutableStateOf<() -> Rect>({ builderPreviewSource }) }
    var builderModalT by remember { mutableFloatStateOf(0f) }

    var compileLog by remember { mutableStateOf<List<String>>(emptyList()) }
    var isCompiling by remember { mutableStateOf(false) }
    var compileDialogState by remember { mutableStateOf<AnimationCompileState?>(null) }
    var compileJob by remember { mutableStateOf<Job?>(null) }
    var compileCancellation by remember { mutableStateOf<AtomicBoolean?>(null) }
    var compileOperationId by remember { mutableIntStateOf(0) }
    var compiledPackageName by remember { mutableStateOf<String?>(null) }
    var level1CloseSignal by remember { mutableIntStateOf(0) }

    val params = remember(
        mode, sides, roundness, inset, wave, rotation, scaleX, scaleY, offsetX, offsetY,
        cornerRadius, innerRadius, pinch, twist, skewX, skewY, cornerTL, cornerTR, cornerBR, cornerBL
    ) {
        IconShapeOverlayManager.CustomParams(
            mode = mode,
            sides = sides,
            roundness = roundness,
            inset = inset,
            wave = wave,
            rotation = rotation,
            scaleX = scaleX,
            scaleY = scaleY,
            offsetX = offsetX,
            offsetY = offsetY,
            cornerRadius = cornerRadius,
            innerRadius = innerRadius,
            pinch = pinch,
            twist = twist,
            skewX = skewX,
            skewY = skewY,
            cornerTL = cornerTL,
            cornerTR = cornerTR,
            cornerBR = cornerBR,
            cornerBL = cornerBL
        )
    }
    val generatedPath = remember(params) { IconShapeOverlayManager.generatePath(params) }

    // Collapsible Frosted Header state
    val statusBarTopDp = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottomDp = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val topBarRowHeightDp = 56.dp
    val statusPlusTopBarPx = with(density) { (statusBarTopDp + topBarRowHeightDp).toPx() }

    var collapsibleMaxHeightPx by remember { mutableFloatStateOf(with(density) { 64.dp.toPx() }) }
    var collapseOffsetPx by remember { mutableFloatStateOf(0f) }
    var headerRevealJob by remember { mutableStateOf<Job?>(null) }

    fun revealHeaderSmoothly() {
        if (collapseOffsetPx > -0.5f || headerRevealJob?.isActive == true) return
        headerRevealJob = scope.launch {
            val anim = Animatable(collapseOffsetPx)
            anim.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    stiffness = Spring.StiffnessMediumLow,
                    dampingRatio = Spring.DampingRatioNoBouncy
                )
            ) {
                collapseOffsetPx = value
            }
        }
    }

    suspend fun refreshOptionsSilent() {
        if (options.isEmpty()) {
            isInitialLoading = true
        } else {
            isSilentRefreshing = true
        }
        val loaded = withContext(Dispatchers.IO) { IconShapeOverlayManager.loadOptions(context) }
        options = loaded
        shapeFlight?.let { current ->
            val updated = loaded.find { it.id == current.option.id }
            if (updated != null) {
                shapeFlight = current.copy(option = updated)
            }
        }
        isInitialLoading = false
        isSilentRefreshing = false
    }

    fun triggerManualSilentRefresh() {
        if (isSilentRefreshing || isBusy) return
        revealHeaderSmoothly()
        scope.launch {
            if (pagerState.anchor == TAB_SHAPE_PRESETS) {
                if (presetsGridState.firstVisibleItemIndex > 0 || presetsGridState.firstVisibleItemScrollOffset > 0) {
                    presetsGridState.animateScrollToItem(0)
                }
            } else {
                if (builderListState.firstVisibleItemIndex > 0 || builderListState.firstVisibleItemScrollOffset > 0) {
                    builderListState.animateScrollToItem(0)
                }
            }
        }
        scope.launch { refreshOptionsSilent() }
    }

    fun applyOptionOptimistic(option: ShapeOption) {
        if (isBusy) return
        // Optimistic update so card & flight morph back in the new active state immediately
        options = options.map { it.copy(active = it.id == option.id) }
        shapeFlight?.let { current ->
            if (current.option.id == option.id) {
                shapeFlight = current.copy(option = option.copy(active = true))
            }
        }
        scope.launch {
            isBusy = true
            val success = withContext(Dispatchers.IO) {
                IconShapeOverlayManager.applyOption(context, option)
            }
            Toast.makeText(
                context,
                context.getString(
                    if (success) R.string.icon_shape_apply_success else R.string.icon_shape_apply_failed
                ),
                Toast.LENGTH_SHORT
            ).show()
            if (success) {
                IconPackManager.requestIconReload(context)
            }
            refreshOptionsSilent()
            isBusy = false
        }
    }

    fun deleteOption(option: ShapeOption) {
        if (isBusy) return
        armedCustomShapes.remove(option.id)
        scope.launch {
            isBusy = true
            val success = withContext(Dispatchers.IO) {
                IconShapeOverlayManager.deleteCustomOverlay(context, option)
            }
            Toast.makeText(
                context,
                context.getString(
                    if (success) R.string.icon_shape_delete_success else R.string.icon_shape_delete_failed
                ),
                Toast.LENGTH_SHORT
            ).show()
            if (success) {
                IconPackManager.requestIconReload(context)
            }
            refreshOptionsSilent()
            isBusy = false
        }
    }

    fun postCompileLog(operationId: Int, phase: AnimationCompilePhase, line: String) {
        scope.launch(Dispatchers.Main) {
            if (compileOperationId != operationId) return@launch
            compileLog = compileLog + line
            val state = compileDialogState ?: return@launch
            if (state.phase == AnimationCompilePhase.SUCCESS ||
                state.phase == AnimationCompilePhase.ERROR ||
                state.phase == AnimationCompilePhase.CANCELLED
            ) return@launch
            compileDialogState = state.copy(
                phase = phase,
                progress = if (phase == AnimationCompilePhase.INSTALLING) 0.85f else 0.45f,
                detail = line
            )
        }
    }

    fun startCompileAndApply(label: String, pathData: String) {
        if (isCompiling || isBusy || pathData.isBlank()) return
        val operationId = compileOperationId + 1
        compileOperationId = operationId
        val cancellation = AtomicBoolean(false)
        compileCancellation = cancellation
        isCompiling = true
        isBusy = true
        compileLog = emptyList()
        compileDialogState = AnimationCompileState(AnimationCompilePhase.COMPILING, 0.15f)
        builderPreviewOpen = true

        val job = scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    IconShapeOverlayManager.compileAndInstallCustom(
                        context = context,
                        label = label,
                        pathData = pathData,
                        logCallback = { line ->
                            postCompileLog(operationId, AnimationCompilePhase.COMPILING, line)
                        },
                        installLogCallback = { line ->
                            postCompileLog(operationId, AnimationCompilePhase.INSTALLING, line)
                        }
                    )
                }
                if (cancellation.get()) throw CancellationException()

                val pkg = result.packageName
                if (result.success && pkg != null) {
                    val tempOption = ShapeOption(
                        id = pkg,
                        label = label,
                        packageName = pkg,
                        pathData = pathData,
                        source = ShapeSource.CUSTOM_OVERLAY,
                        active = false
                    )
                    withContext(Dispatchers.IO) {
                        IconShapeOverlayManager.applyOption(context, tempOption)
                    }
                    IconPackManager.requestIconReload(context)
                    refreshOptionsSilent()
                    compiledPackageName = pkg
                    compileDialogState = AnimationCompileState(
                        AnimationCompilePhase.SUCCESS,
                        1f,
                        label
                    )
                } else {
                    compileDialogState = AnimationCompileState(
                        AnimationCompilePhase.ERROR,
                        0f,
                        result.error ?: context.getString(R.string.icon_shape_compile_dialog_error)
                    )
                }
            } catch (_: CancellationException) {
                compileDialogState = AnimationCompileState(
                    AnimationCompilePhase.CANCELLED,
                    compileDialogState?.progress ?: 0f
                )
            } catch (t: Throwable) {
                compileDialogState = AnimationCompileState(
                    AnimationCompilePhase.ERROR,
                    0f,
                    t.message ?: context.getString(R.string.icon_shape_compile_dialog_error)
                )
            } finally {
                isCompiling = false
                isBusy = false
                compileJob = null
                compileCancellation = null
            }
        }
        compileJob = job
    }

    fun cancelCompile() {
        compileCancellation?.set(true)
        compileJob?.cancel()
    }

    fun dismissCompileModal() {
        val pkg = compiledPackageName
        compiledPackageName = null
        compileDialogState = null
        builderPreviewOpen = false
        if (pkg != null) {
            scope.launch {
                delay(60)
                pagerState.animateScrollToPage(TAB_SHAPE_PRESETS, isDirectTap = true)
                val idx = options.indexOfFirst { it.packageName == pkg || it.id == pkg }
                if (idx >= 0) {
                    presetsGridState.animateScrollToItem(idx)
                }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                val parsed = withContext(Dispatchers.IO) {
                    IconShapeOverlayManager.readCustomPath(context, uri)
                }
                if (!IconShapeOverlayManager.isValidPath(parsed)) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.icon_shape_custom_invalid),
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    importedUri = uri
                    importedPath = parsed
                    Toast.makeText(
                        context,
                        context.getString(R.string.icon_shape_import_success),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshOptionsSilent()
    }

    val anyLevel1ModalOpen = shapeFlight != null || builderPreviewOpen || compileDialogState != null
    val nestedMorphOpen = sliderMorphHost.request != null
    val anyModalOpen = anyLevel1ModalOpen || nestedMorphOpen
    val activeModalProgress = maxOf(shapeFlightT, builderModalT)

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (shapeFlight != null ||
                    builderPreviewOpen ||
                    compileDialogState != null ||
                    sliderMorphHost.request != null
                ) {
                    return Offset.Zero
                }
                if (available.y > 0.5f) {
                    headerRevealJob?.cancel()
                    collapseOffsetPx = (collapseOffsetPx + available.y)
                        .coerceIn(-collapsibleMaxHeightPx, 0f)
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (shapeFlight != null ||
                    builderPreviewOpen ||
                    compileDialogState != null ||
                    sliderMorphHost.request != null
                ) {
                    return Offset.Zero
                }
                if (consumed.y < -0.5f && collapsibleMaxHeightPx > 1f) {
                    headerRevealJob?.cancel()
                    collapseOffsetPx = (collapseOffsetPx + consumed.y)
                        .coerceIn(-collapsibleMaxHeightPx, 0f)
                }
                return Offset.Zero
            }
        }
    }

    LaunchedEffect(pagerState.anchor) {
        revealHeaderSmoothly()
    }

    val visibleCollapsiblePx = (collapsibleMaxHeightPx + collapseOffsetPx).coerceAtLeast(0f)
    val effectiveHeaderHeightPx = statusPlusTopBarPx + visibleCollapsiblePx
    val effectiveHeaderHeightDp = with(density) { effectiveHeaderHeightPx.toDp() }
    val listTopPaddingDp = with(density) { (statusPlusTopBarPx + collapsibleMaxHeightPx).toDp() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .nestedScroll(nestedScrollConnection)
    ) {
        SliderMorphRoot(
            host = sliderMorphHost,
            blurState = cardModalBlur,
            morphBlurState = sliderModalBlur,
            morphBackgroundColor = MaterialTheme.colorScheme.surfaceContainer
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (anyLevel1ModalOpen || nestedMorphOpen) {
                            Modifier.recordLayer(
                                state = cardModalBlur,
                                backgroundColor = MaterialTheme.colorScheme.surfaceContainer
                            )
                        } else {
                            Modifier
                        }
                    )
            ) {
                // Pass 1: Pager content recorded into scrollBlur
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .recordLayer(
                            state = scrollBlur,
                            backgroundColor = MaterialTheme.colorScheme.surfaceContainer
                        )
                ) {
                    EffectStudioPager(
                        state = pagerState,
                        userScrollEnabled = !anyModalOpen,
                        onHorizontalDragDetected = { revealHeaderSmoothly() },
                        modifier = Modifier.fillMaxSize()
                    ) { page ->
                        when (page) {
                            TAB_SHAPE_PRESETS -> {
                                StudioGrid(
                                    gridState = presetsGridState,
                                    topPadding = listTopPaddingDp + 12.dp
                                ) {
                                    if (isInitialLoading && options.isEmpty()) {
                                        items(6, key = { "shape_skeleton_$it" }) {
                                            SkeletonShapeGridCard()
                                        }
                                    } else {
                                        items(options, key = { it.id }) { option ->
                                            val cardKey = "shape_${option.id}"
                                            val armedTime = armedCustomShapes[option.id] ?: 0L
                                            val isTrashArmed = armedTime > 0L

                                            LaunchedEffect(armedTime) {
                                                if (armedTime <= 0L) return@LaunchedEffect
                                                val totalDurationMs = 3000L
                                                while (true) {
                                                    val now = SystemClock.uptimeMillis()
                                                    val remaining = (armedTime + totalDurationMs) - now
                                                    if (remaining <= 0L) break
                                                    delay(remaining.coerceAtMost(80L))
                                                }
                                                while (shapeFlight?.key == cardKey &&
                                                    !shapeFlightClosing &&
                                                    shapeFlightT < 0.99f
                                                ) {
                                                    delay(25L)
                                                }
                                                if (shapeFlight?.key == cardKey && shapeFlightClosing) {
                                                    while (shapeFlight?.key == cardKey) {
                                                        delay(25L)
                                                    }
                                                    delay(90L)
                                                }
                                                if (armedCustomShapes[option.id] == armedTime) {
                                                    armedCustomShapes.remove(option.id)
                                                }
                                            }

                                            ShapeStudioGridCard(
                                                option = option,
                                                busy = isBusy,
                                                trashArmed = isTrashArmed,
                                                isHiddenForFlight = shapeFlight?.key == cardKey,
                                                onExpand = { bounds, provider ->
                                                    val curArmed = armedCustomShapes[option.id]
                                                    if (curArmed != null) {
                                                        val now = SystemClock.uptimeMillis()
                                                        val rem = (curArmed + 3000L) - now
                                                        if (rem in 1L..450L) {
                                                            armedCustomShapes[option.id] =
                                                                now - 3000L + 500L
                                                        }
                                                    }
                                                    shapeFlight = ShapeFlightTarget(
                                                        key = cardKey,
                                                        option = option,
                                                        source = bounds,
                                                        sourceBoundsProvider = provider,
                                                        isTrashArmedProvider = {
                                                            armedCustomShapes.containsKey(option.id)
                                                        }
                                                    )
                                                },
                                                onApply = { applyOptionOptimistic(option) },
                                                onTrashClick = if (option.source == ShapeSource.CUSTOM_OVERLAY) {
                                                    {
                                                        if (isTrashArmed) {
                                                            deleteOption(option)
                                                        } else {
                                                            armedCustomShapes[option.id] =
                                                                SystemClock.uptimeMillis()
                                                        }
                                                    }
                                                } else null
                                            )
                                        }

                                        item(key = "shape_add_custom_card") {
                                            ShapeStudioAddCard {
                                                scope.launch {
                                                    pagerState.animateScrollToPage(
                                                        TAB_SHAPE_BUILDER,
                                                        isDirectTap = true
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            else -> {
                                LazyColumn(
                                    state = builderListState,
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(
                                        start = 16.dp,
                                        top = listTopPaddingDp + 12.dp,
                                        end = 16.dp,
                                        bottom = navBottomDp + REBOOT_BUBBLE_CONTENT_BOTTOM_PADDING + 120.dp
                                    ),
                                    verticalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    item(key = "shape_builder_import") {
                                        ShapeImportCard(
                                            importedUri = importedUri,
                                            importedPath = importedPath,
                                            isBusy = isBusy,
                                            onPickFile = { importLauncher.launch("*/*") },
                                            onCompileImported = { customName ->
                                                startCompileAndApply(customName, importedPath)
                                            }
                                        )
                                    }

                                    item(key = "shape_builder_parametric") {
                                        ShapeParametricConstructorCard(
                                            params = params,
                                            onParamsChange = { next ->
                                                mode = next.mode
                                                sides = next.sides
                                                roundness = next.roundness
                                                inset = next.inset
                                                wave = next.wave
                                                rotation = next.rotation
                                                scaleX = next.scaleX
                                                scaleY = next.scaleY
                                                offsetX = next.offsetX
                                                offsetY = next.offsetY
                                                cornerRadius = next.cornerRadius
                                                innerRadius = next.innerRadius
                                                pinch = next.pinch
                                                twist = next.twist
                                                skewX = next.skewX
                                                skewY = next.skewY
                                                cornerTL = next.cornerTL
                                                cornerTR = next.cornerTR
                                                cornerBR = next.cornerBR
                                                cornerBL = next.cornerBL
                                            }
                                        )
                                    }

                                    if (compileLog.isNotEmpty()) {
                                        item(key = "shape_builder_compile_log") {
                                            StudioCompileLogCard(compileLog = compileLog)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Level-1 Pseudo-Modal A: Shape Card Flight Overlay
                ShapeCardFlightOverlay(
                    target = shapeFlight,
                    busy = isBusy,
                    scrollBlur = scrollBlur,
                    topInsetPx = effectiveHeaderHeightPx,
                    closeSignal = level1CloseSignal,
                    onProgress = { shapeFlightT = it },
                    onClosingChanged = { isClosing ->
                        shapeFlightClosing = isClosing
                        if (isClosing) {
                            val optId = shapeFlight?.option?.id
                            val curArmed = if (optId != null) armedCustomShapes[optId] else null
                            if (optId != null && curArmed != null) {
                                val now = SystemClock.uptimeMillis()
                                val rem = (curArmed + 3000L) - now
                                if (rem in 1L..450L) {
                                    armedCustomShapes[optId] = now - 3000L + 450L
                                }
                            }
                        }
                    },
                    onClose = {
                        shapeFlightClosing = false
                        shapeFlight = null
                    },
                    onApply = { option -> applyOptionOptimistic(option) },
                    onDelete = { option -> deleteOption(option) }
                )

                // Level-1 Pseudo-Modal B: Builder Preview & Compile Flight Overlay
                ShapeBuilderModalOverlay(
                    open = builderPreviewOpen,
                    sourceRect = builderPreviewSource,
                    sourceBoundsProvider = builderPreviewBoundsProvider,
                    pathData = generatedPath,
                    mode = mode,
                    compileState = compileDialogState,
                    onBuildAndApply = { customName ->
                        startCompileAndApply(customName, generatedPath)
                    },
                    onCancelCompile = ::cancelCompile,
                    onDismissCompile = ::dismissCompileModal,
                    onClose = { builderPreviewOpen = false },
                    scrollBlur = scrollBlur,
                    onProgress = { builderModalT = it },
                    topInsetPx = effectiveHeaderHeightPx,
                    closeSignal = level1CloseSignal
                )
            }
        } // end SliderMorphRoot

        // Floating Frosted Shape Builder Preview Bar at the bottom of Tab 2 (outside SliderMorphRoot)
        val floatBarPageVisibility = sstepE(pagerState.position, 0.15f, 0.95f)
        val floatBarCompileFade = if (compileDialogState != null) {
            (1f - builderModalT).coerceIn(0f, 1f)
        } else {
            1f
        }
        ShapeBuilderFloatBar(
            pathData = generatedPath,
            mode = mode,
            blurState = scrollBlur,
            morphBlurState = if (nestedMorphOpen) sliderModalBlur else cardModalBlur,
            morphActive = nestedMorphOpen,
            tintBoost = 0.25f * sliderMorphHost.morphProgress,
            visibleFraction = floatBarPageVisibility * floatBarCompileFade,
            isHiddenForFlight = builderPreviewOpen && compileDialogState == null,
            onExpand = { bounds, provider ->
                if (nestedMorphOpen) {
                    sliderMorphHost.requestAnimatedClose()
                } else if (!anyLevel1ModalOpen) {
                    builderPreviewSource = bounds
                    builderPreviewBoundsProvider = provider
                    builderPreviewOpen = true
                }
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .zIndex(if (anyLevel1ModalOpen) 0.5f else 3f)
        )


        // Frosted Blur Overlay over Pinned Header
        TopBarBlurOverlay(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter),
            topBarHeight = effectiveHeaderHeightDp,
            blurState = scrollBlur,
            morphBlurState = if (nestedMorphOpen) sliderModalBlur else cardModalBlur,
            morphActive = anyLevel1ModalOpen || nestedMorphOpen,
            tintBoost = maxOf(0.25f * activeModalProgress, 0.25f * sliderMorphHost.morphProgress)
        )

        // Status bar veil
        TopStatusVeil(
            backgroundColor = MaterialTheme.colorScheme.surfaceContainer,
            veilHeight = statusBarTopDp + 14.dp,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
        )

        // Crisp Pinned Header UI above the frost overlay (zIndex = 4f)
        Column(
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
        ) {
            val spinTransition = rememberInfiniteTransition(label = "ShapeSilentRefreshSpin")
            val spinAngle by spinTransition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 750, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "ShapeSilentRefreshAngle"
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
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
                    text = stringResource(R.string.icon_shape_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = { triggerManualSilentRefresh() },
                    enabled = !isInitialLoading && !isBusy
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = stringResource(R.string.app_icons_refresh),
                        tint = if (isSilentRefreshing) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        modifier = Modifier.rotate(if (isSilentRefreshing) spinAngle else 0f)
                    )
                }
            }

            // Clipped Collapsible Container for AppIconsTabs
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clipToBounds()
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        val fullH = placeable.height.toFloat()
                        collapsibleMaxHeightPx = fullH
                        val visH = (fullH + collapseOffsetPx).roundToInt().coerceAtLeast(0)
                        val yOff = collapseOffsetPx.roundToInt().coerceIn(-placeable.height, 0)
                        layout(placeable.width, visH) {
                            placeable.placeRelative(0, yOff)
                        }
                    }
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Spacer(Modifier.height(4.dp))
                    AppIconsTabs(
                        titles = listOf(
                            stringResource(R.string.icon_shape_presets_section),
                            stringResource(R.string.icon_shape_custom_section)
                        ),
                        position = pagerState.position,
                        pagerState = pagerState,
                        onTabClick = { index ->
                            scope.launch {
                                pagerState.animateScrollToPage(index, isDirectTap = true)
                            }
                        }
                    )
                    Spacer(Modifier.height(10.dp))
                }
            }
        }

        // Full-header tap/swipe blocker when any pseudo-modal is open
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
                        onClick = {
                            if (nestedMorphOpen) {
                                sliderMorphHost.requestAnimatedClose()
                            } else {
                                level1CloseSignal++
                            }
                        }
                    )
            )
        }
    }
}

