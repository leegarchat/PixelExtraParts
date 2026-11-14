package org.pixel.customparts.activities

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.pixel.customparts.R
import org.pixel.customparts.SettingsKeys
import org.pixel.customparts.dynamicDarkColorScheme
import org.pixel.customparts.dynamicLightColorScheme
import org.pixel.customparts.ui.GenericSwitchRow
import org.pixel.customparts.ui.REBOOT_BUBBLE_CONTENT_BOTTOM_PADDING
import org.pixel.customparts.ui.SliderMorphRoot
import org.pixel.customparts.ui.TopBarBlurOverlay
import org.pixel.customparts.ui.TopStatusVeil
import org.pixel.customparts.ui.recordLayer
import org.pixel.customparts.ui.rememberGraphicsLayerRecordingState
import org.pixel.customparts.ui.rememberSliderMorphHostState
import org.pixel.customparts.utils.AnimThemeCompiler
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

private const val ROTATION_STYLE_DEFAULT = 0
private const val ROTATION_STYLE_CUSTOM_APK = -1
private const val ROTATION_STYLE_DISABLED = -2

private const val KEY_ROTATION_STYLE = "rotation_animation_style"
private const val KEY_ROTATION_CUSTOM_PACKAGE = "rotation_animation_custom_package"
private const val KEY_ROTATION_SPEED = "rotation_animation_speed"
private const val ROTATION_THEME_PACKAGE_PREFIX = "org.pixel.customparts.rotation."

private fun readRotationSpeed(context: Context): Float {
    return try {
        val v = Settings.Global.getFloat(context.contentResolver, KEY_ROTATION_SPEED, 1f)
        v.coerceIn(0.1f, 10f)
    } catch (_: Exception) {
        1f
    }
}

private fun scanInstalledRotationThemes(context: Context): List<String> {
    val result = linkedSetOf<String>()
    runCatching {
        context.packageManager.getInstalledPackages(0).forEach { pi ->
            if (pi.packageName.startsWith(ROTATION_THEME_PACKAGE_PREFIX)) {
                result.add(pi.packageName)
            }
        }
    }
    runCatching {
        context.packageManager.getInstalledApplications(0).forEach { ai ->
            if (ai.packageName.startsWith(ROTATION_THEME_PACKAGE_PREFIX)) {
                result.add(ai.packageName)
            }
        }
    }
    return result.sorted()
}

private const val ROTATION_STYLE_WINDOWS_PHONE = 1
private const val ROTATION_STYLE_WIN10_11 = 2
private const val ROTATION_STYLE_CUBE = 3
private const val ROTATION_STYLE_ZOOM = 4
private const val ROTATION_STYLE_SLIDE = 5
private const val ROTATION_STYLE_FLIP = 6
private const val ROTATION_STYLE_FADE = 7
private const val ROTATION_STYLE_BOUNCE = 8

private data class RotationStyle(
    val styleId: Int,
    val nameRes: Int,
    val exitAnimRes: Int,
    val enterAnimRes: Int,
    val alphaAnimRes: Int
)

private val BUILTIN_STYLES: List<RotationStyle> by lazy {
    listOf(
        RotationStyle(
            ROTATION_STYLE_WINDOWS_PHONE,
            R.string.rotation_style_windows_phone,
            R.anim.wp_exit,
            R.anim.wp_enter,
            R.anim.wp_alpha
        ),
        RotationStyle(
            ROTATION_STYLE_WIN10_11,
            R.string.rotation_style_win10_11,
            R.anim.win10_11_exit,
            R.anim.win10_11_enter,
            R.anim.win10_11_alpha
        ),
        RotationStyle(
            ROTATION_STYLE_CUBE,
            R.string.rotation_style_cube,
            R.anim.cube_exit,
            R.anim.cube_enter,
            R.anim.cube_alpha
        ),
        RotationStyle(
            ROTATION_STYLE_ZOOM,
            R.string.rotation_style_zoom,
            R.anim.zoom_rotate_exit,
            R.anim.zoom_rotate_enter,
            R.anim.zoom_rotate_alpha
        ),
        RotationStyle(
            ROTATION_STYLE_SLIDE,
            R.string.rotation_style_slide,
            R.anim.slide_rotate_exit,
            R.anim.slide_rotate_enter,
            R.anim.slide_rotate_alpha
        ),
        RotationStyle(
            ROTATION_STYLE_FLIP,
            R.string.rotation_style_flip,
            R.anim.flip_exit,
            R.anim.flip_enter,
            R.anim.flip_alpha
        ),
        RotationStyle(
            ROTATION_STYLE_FADE,
            R.string.rotation_style_fade,
            R.anim.fade_rotate_exit,
            R.anim.fade_rotate_enter,
            R.anim.fade_rotate_alpha
        ),
        RotationStyle(
            ROTATION_STYLE_BOUNCE,
            R.string.rotation_style_bounce,
            R.anim.bounce_exit,
            R.anim.bounce_enter,
            R.anim.bounce_alpha
        )
    )
}

class RotationAnimationActivity : ComponentActivity() {
    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        setContent {
            val darkTheme = isSystemInDarkTheme()
            val context = LocalContext.current
            val colors = if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
            MaterialTheme(colorScheme = colors) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    RotationAnimationScreen(onBack = { finish() })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RotationAnimationScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current

    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE ||
        configuration.screenWidthDp >= 600

    var currentStyle by remember {
        mutableIntStateOf(
            try {
                Settings.Global.getInt(context.contentResolver, KEY_ROTATION_STYLE)
            } catch (_: Exception) {
                ROTATION_STYLE_DEFAULT
            }
        )
    }
    var customPackage by remember {
        mutableStateOf(
            try {
                Settings.Global.getString(context.contentResolver, KEY_ROTATION_CUSTOM_PACKAGE) ?: ""
            } catch (_: Exception) {
                ""
            }
        )
    }
    var installedThemes by remember { mutableStateOf(scanInstalledRotationThemes(context)) }
    var speed by remember { mutableFloatStateOf(readRotationSpeed(context)) }
    val pagerState = rememberStudioPagerState(3)

    var flight by remember { mutableStateOf<StudioCard?>(null) }
    var flightT by remember { mutableFloatStateOf(0f) }
    var flightClosing by remember { mutableStateOf(false) }
    val armedThemes = remember { mutableStateMapOf<String, Long>() }

    var builderPreviewOpen by remember { mutableStateOf(false) }
    var builderPreviewSource by remember { mutableStateOf(Rect.Zero) }
    var builderPreviewBoundsProvider by remember { mutableStateOf<() -> Rect>({ builderPreviewSource }) }
    var builderModalT by remember { mutableFloatStateOf(0f) }

    var interpolatorRequest by remember { mutableStateOf<InterpolatorMorphRequest?>(null) }
    var interpolatorModalT by remember { mutableFloatStateOf(0f) }

    val builtinGridState = rememberLazyGridState()
    val collectedGridState = rememberLazyGridState()
    val builderListState = rememberLazyListState()

    fun refreshThemes() {
        installedThemes = scanInstalledRotationThemes(context)
    }

    fun applyStyle(styleId: Int) {
        Settings.Global.putInt(context.contentResolver, KEY_ROTATION_STYLE, styleId)
        currentStyle = styleId
    }

    fun applyCustomTheme(packageName: String) {
        Settings.Global.putString(context.contentResolver, KEY_ROTATION_CUSTOM_PACKAGE, packageName)
        Settings.Global.putInt(context.contentResolver, KEY_ROTATION_STYLE, ROTATION_STYLE_CUSTOM_APK)
        customPackage = packageName
        currentStyle = ROTATION_STYLE_CUSTOM_APK
    }

    fun updateSpeed(value: Float) {
        val clamped = value.coerceIn(0.1f, 10f)
        speed = clamped
        try {
            Settings.Global.putFloat(context.contentResolver, KEY_ROTATION_SPEED, clamped)
        } catch (_: Exception) {
        }
    }

    fun uninstallTheme(packageName: String) {
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                AnimThemeCompiler.uninstall(context, packageName)
            }
            if (ok && customPackage == packageName) {
                Settings.Global.putString(context.contentResolver, KEY_ROTATION_CUSTOM_PACKAGE, null)
                if (currentStyle == ROTATION_STYLE_CUSTOM_APK) {
                    Settings.Global.putInt(context.contentResolver, KEY_ROTATION_STYLE, ROTATION_STYLE_DEFAULT)
                    currentStyle = ROTATION_STYLE_DEFAULT
                }
                customPackage = ""
            }
            refreshThemes()
        }
    }

    LaunchedEffect(Unit) {
        refreshThemes()
    }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                refreshThemes()
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        context.registerReceiver(receiver, filter)
        onDispose {
            runCatching { context.unregisterReceiver(receiver) }
        }
    }

    var exitUri by remember { mutableStateOf<Uri?>(null) }
    var enterUri by remember { mutableStateOf<Uri?>(null) }
    var compileLog by remember { mutableStateOf<List<String>>(emptyList()) }
    var isCompiling by remember { mutableStateOf(false) }
    var compileError by remember { mutableStateOf<String?>(null) }
    var compileDialogState by remember { mutableStateOf<AnimationCompileState?>(null) }
    var compileJob by remember { mutableStateOf<Job?>(null) }
    var compileCancellation by remember { mutableStateOf<AtomicBoolean?>(null) }
    var compileOperationId by remember { mutableIntStateOf(0) }
    var compiledPackageName by remember { mutableStateOf<String?>(null) }

    var constructorTab by remember { mutableIntStateOf(0) }
    var constructorExit by remember {
        mutableStateOf(
            AnimParams(
                alphaFrom = 1f,
                alphaTo = 0f,
                rotateFrom = 0f,
                rotateTo = -90f,
                duration = 320
            )
        )
    }
    var constructorEnter by remember {
        mutableStateOf(
            AnimParams(
                alphaFrom = 0f,
                alphaTo = 1f,
                rotateFrom = 90f,
                rotateTo = 0f,
                duration = 320,
                startOffset = 80
            )
        )
    }

    val exitPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) {
        if (it != null) exitUri = it
    }
    val enterPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) {
        if (it != null) enterUri = it
    }

    fun progressForLog(phase: AnimationCompilePhase, line: String, current: Float): Float {
        if (phase == AnimationCompilePhase.INSTALLING) {
            return when {
                line.contains("Installing via") -> 0.94f
                line.contains("successful", ignoreCase = true) -> 1f
                else -> maxOf(current, 0.92f)
            }
        }
        return when {
            line.startsWith("Package:") -> 0.08f
            line.contains("files written", ignoreCase = true) -> 0.18f
            line.contains("AndroidManifest", ignoreCase = true) -> 0.25f
            line.contains("aapt2 binary", ignoreCase = true) -> 0.32f
            line.contains("compile output", ignoreCase = true) -> 0.58f
            line.contains("Signing APK", ignoreCase = true) -> 0.78f
            line.contains("Signed APK", ignoreCase = true) -> 0.9f
            else -> current
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
            val effectivePhase = if (
                state.phase == AnimationCompilePhase.INSTALLING &&
                phase == AnimationCompilePhase.COMPILING
            ) AnimationCompilePhase.INSTALLING else phase
            compileDialogState = state.copy(
                phase = effectivePhase,
                progress = progressForLog(effectivePhase, line, state.progress),
                detail = line
            )
        }
    }

    fun startCompile(
        build: (AtomicBoolean, (String) -> Unit) -> AnimThemeCompiler.CompileResult
    ) {
        if (isCompiling) return
        val operationId = compileOperationId + 1
        compileOperationId = operationId
        val cancellation = AtomicBoolean(false)
        compileCancellation = cancellation
        isCompiling = true
        compileError = null
        compileLog = emptyList()
        compileDialogState = AnimationCompileState(AnimationCompilePhase.COMPILING, 0.02f)

        val job = scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    build(cancellation) { line ->
                        postCompileLog(operationId, AnimationCompilePhase.COMPILING, line)
                    }
                }
                if (cancellation.get()) throw CancellationException()

                if (result.success && result.apkPath != null && result.packageName != null) {
                    compileDialogState = AnimationCompileState(
                        AnimationCompilePhase.INSTALLING,
                        0.92f,
                        context.getString(R.string.anim_compile_dialog_installing)
                    )
                    compileLog = compileLog + context.getString(R.string.anim_compile_dialog_installing)
                    val installed = withContext(Dispatchers.IO) {
                        AnimThemeCompiler.install(
                            context = context,
                            apkPath = result.apkPath,
                            packageName = result.packageName,
                            logCallback = { line ->
                                postCompileLog(operationId, AnimationCompilePhase.INSTALLING, line)
                            },
                            isCancelled = cancellation::get
                        )
                    }
                    if (cancellation.get()) throw CancellationException()

                    if (installed) {
                        compileLog = compileLog + "✓ Installed successfully"
                        refreshThemes()
                        compiledPackageName = result.packageName
                        compileDialogState = AnimationCompileState(
                            AnimationCompilePhase.SUCCESS,
                            1f,
                            result.packageName
                        )
                    } else {
                        compileError = context.getString(R.string.anim_install_failed)
                        compileLog = compileLog + context.getString(R.string.anim_install_failed_log)
                        compileDialogState = AnimationCompileState(
                            AnimationCompilePhase.ERROR,
                            0.98f,
                            compileError.orEmpty()
                        )
                    }
                } else {
                    compileError = result.error ?: context.getString(R.string.anim_compile_failed)
                    compileDialogState = AnimationCompileState(
                        AnimationCompilePhase.ERROR,
                        0f,
                        compileError.orEmpty()
                    )
                }
            } catch (_: CancellationException) {
                compileDialogState = AnimationCompileState(
                    AnimationCompilePhase.CANCELLED,
                    compileDialogState?.progress ?: 0f
                )
            } catch (t: Throwable) {
                compileError = t.message ?: context.getString(R.string.anim_compile_failed)
                compileDialogState = AnimationCompileState(
                    AnimationCompilePhase.ERROR,
                    0f,
                    compileError.orEmpty()
                )
            } finally {
                isCompiling = false
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

    fun dismissCompileDialog() {
        val packageName = compiledPackageName
        compiledPackageName = null
        compileDialogState = null
        builderPreviewOpen = false
        if (packageName != null) {
            refreshThemes()
            scope.launch {
                delay(80)
                pagerState.animateScrollToPage(STUDIO_TAB_COLLECTED, isDirectTap = true)
                val index = installedThemes.indexOf(packageName)
                if (index >= 0) {
                    collectedGridState.animateScrollToItem(index)
                }
            }
        }
    }

    fun doCompileImported(name: String) {
        if (name.isBlank() || exitUri == null || enterUri == null) return
        startCompile { cancellation, logCallback ->
            AnimThemeCompiler.compileRotation(
                context = context,
                styleName = name,
                exitUri = exitUri!!,
                enterUri = enterUri!!,
                logCallback = AnimThemeCompiler.LogCallback(logCallback),
                isCancelled = cancellation::get
            )
        }
    }

    fun doCompileConstructor(name: String) {
        if (name.isBlank()) return
        startCompile { cancellation, logCallback ->
            AnimThemeCompiler.compileRotationFromXml(
                context = context,
                styleName = name,
                exitXml = animParamsToXml(constructorExit),
                enterXml = animParamsToXml(constructorEnter),
                logCallback = AnimThemeCompiler.LogCallback(logCallback),
                isCancelled = cancellation::get
            )
        }
    }

    // 3-pass non-cyclic blur states:
    // 1. scrollBlur: records pager content + floating builder preview bar
    // 2. cardModalBlur: records [Pass 1 + Level-1 Modal] when any Level-1 modal is open
    // 3. sliderModalBlur: records [Pass 2 + SliderMorphOverlay] when Level-2 slider modal is open
    val scrollBlur = rememberGraphicsLayerRecordingState()
    val cardModalBlur = rememberGraphicsLayerRecordingState()
    val sliderModalBlur = rememberGraphicsLayerRecordingState()
    val sliderMorphHost = rememberSliderMorphHostState()

    val constructorClockState = rememberConstructorPreviewClockState()
    var level1CloseSignal by remember { mutableIntStateOf(0) }

    val anyLevel1ModalOpen = flight != null ||
        builderPreviewOpen ||
        compileDialogState != null ||
        interpolatorRequest != null
    val nestedMorphOpen = sliderMorphHost.request != null
    val anyModalOpen = anyLevel1ModalOpen || nestedMorphOpen
    val activeModalProgress = maxOf(flightT, builderModalT, interpolatorModalT)

    // Scroll-driven collapsible Switcher + Tabs logic (clipped strictly at the
    // bottom edge of the TopAppBar so it never overlaps the transparent title bar)
    val statusBarTopDp = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val topBarRowHeightDp = 56.dp
    val statusPlusTopBarPx = with(density) { (statusBarTopDp + topBarRowHeightDp).toPx() }

    var collapsibleMaxHeightPx by remember { mutableFloatStateOf(with(density) { 128.dp.toPx() }) }
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

    // Header follows REAL list scroll, not the swipe gesture itself: collapse runs on
    // post-scroll (consumed delta), so a short list that cannot scroll (e.g. Collected
    // with a single theme) never hides the Switcher + Tabs. Reveal stays on pre-scroll
    // so pull-down at the top edge still brings the header back immediately.
    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // Block header motion when scrolling inside any open pseudo-modal
                if (flight != null ||
                    builderPreviewOpen ||
                    compileDialogState != null ||
                    interpolatorRequest != null ||
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
                // Same modal guard as pre-scroll
                if (flight != null ||
                    builderPreviewOpen ||
                    compileDialogState != null ||
                    interpolatorRequest != null ||
                    sliderMorphHost.request != null
                ) {
                    return Offset.Zero
                }
                // Collapse only by the distance the list really scrolled
                if (consumed.y < -0.5f && collapsibleMaxHeightPx > 1f) {
                    headerRevealJob?.cancel()
                    collapseOffsetPx = (collapseOffsetPx + consumed.y)
                        .coerceIn(-collapsibleMaxHeightPx, 0f)
                }
                return Offset.Zero
            }
        }
    }

    // Smoothly reveal header when user switches pages
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
                // Pass 1: Full-screen scroll content recorded into scrollBlur (origin = 0,0)
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
                            STUDIO_TAB_BUILTIN -> StudioGrid(
                                gridState = builtinGridState,
                                topPadding = listTopPaddingDp + 12.dp
                            ) {
                                item(key = "pep_default") {
                                    val title = stringResource(R.string.rotation_anim_default)
                                    val active = currentStyle == ROTATION_STYLE_DEFAULT
                                    val cardKey = "pep_default"
                                    LoopEffectCard(
                                        title = title,
                                        isActive = active,
                                        isHiddenForFlight = flight?.key == cardKey,
                                        onExpand = { bounds, provider ->
                                            flight = StudioCard.Builtin(
                                                key = cardKey,
                                                title = title,
                                                source = bounds,
                                                sourceBoundsProvider = provider,
                                                styleId = ROTATION_STYLE_DEFAULT,
                                                exitAnim = R.anim.stock_rotate_exit,
                                                enterAnim = R.anim.stock_rotate_enter
                                            )
                                        },
                                        applyLabel = stringResource(R.string.rotation_anim_apply),
                                        activeLabel = stringResource(R.string.rotation_anim_active),
                                        onApply = { applyStyle(ROTATION_STYLE_DEFAULT) },
                                        preview = {
                                            RotationAnimPreview(
                                                exitAnimRes = R.anim.stock_rotate_exit,
                                                enterAnimRes = R.anim.stock_rotate_enter,
                                                speedMultiplier = 1f
                                            )
                                        }
                                    )
                                }
                                item(key = "pep_disabled") {
                                    val title = stringResource(R.string.rotation_anim_disabled)
                                    val active = currentStyle == ROTATION_STYLE_DISABLED
                                    val cardKey = "pep_disabled"
                                    LoopEffectCard(
                                        title = title,
                                        isActive = active,
                                        isHiddenForFlight = flight?.key == cardKey,
                                        onExpand = { bounds, provider ->
                                            flight = StudioCard.Builtin(
                                                key = cardKey,
                                                title = title,
                                                source = bounds,
                                                sourceBoundsProvider = provider,
                                                styleId = ROTATION_STYLE_DISABLED,
                                                exitAnim = R.anim.no_anim,
                                                enterAnim = R.anim.no_anim
                                            )
                                        },
                                        applyLabel = stringResource(R.string.rotation_anim_apply),
                                        activeLabel = stringResource(R.string.rotation_anim_active),
                                        onApply = { applyStyle(ROTATION_STYLE_DISABLED) },
                                        preview = {
                                            RotationAnimPreview(
                                                exitAnimRes = R.anim.no_anim,
                                                enterAnimRes = R.anim.no_anim,
                                                speedMultiplier = speed
                                            )
                                        }
                                    )
                                }
                                items(BUILTIN_STYLES, key = { it.styleId }) { style ->
                                    val title = stringResource(style.nameRes)
                                    val active = style.styleId == currentStyle
                                    val cardKey = "builtin_${style.styleId}"
                                    LoopEffectCard(
                                        title = title,
                                        isActive = active,
                                        isHiddenForFlight = flight?.key == cardKey,
                                        onExpand = { bounds, provider ->
                                            flight = StudioCard.Builtin(
                                                key = cardKey,
                                                title = title,
                                                source = bounds,
                                                sourceBoundsProvider = provider,
                                                styleId = style.styleId,
                                                exitAnim = style.exitAnimRes,
                                                enterAnim = style.enterAnimRes
                                            )
                                        },
                                        applyLabel = stringResource(R.string.rotation_anim_apply),
                                        activeLabel = stringResource(R.string.rotation_anim_active),
                                        onApply = { applyStyle(style.styleId) },
                                        preview = {
                                            RotationAnimPreview(
                                                exitAnimRes = style.exitAnimRes,
                                                enterAnimRes = style.enterAnimRes,
                                                speedMultiplier = speed
                                            )
                                        }
                                    )
                                }
                            }

                            STUDIO_TAB_COLLECTED -> StudioGrid(
                                gridState = collectedGridState,
                                topPadding = listTopPaddingDp + 12.dp
                            ) {
                                items(installedThemes, key = { it }) { packageName ->
                                    val cardKey = "theme_$packageName"
                                    val armedTimestamp = armedThemes[packageName] ?: 0L
                                    val confirmDelete = armedTimestamp > 0L

                                    LaunchedEffect(armedTimestamp) {
                                        if (armedTimestamp <= 0L) return@LaunchedEffect
                                        val totalDurationMs = 3000L
                                        while (true) {
                                            val now = android.os.SystemClock.uptimeMillis()
                                            val remaining = (armedTimestamp + totalDurationMs) - now
                                            if (remaining <= 0L) break
                                            delay(remaining.coerceAtMost(80L))
                                        }
                                        // Boundary / mid-flight guard:
                                        // 1) If the modal is currently opening, wait until it finishes opening
                                        //    (at full open the button is a full-width capsule anyway).
                                        while (flight?.key == cardKey && !flightClosing && flightT < 0.99f) {
                                            delay(25L)
                                        }
                                        // 2) If the modal is closing (or started closing right at the boundary),
                                        //    keep it as a circle until the close flight completely finishes,
                                        //    and only then morph from circle to square on the card!
                                        if (flight?.key == cardKey && flightClosing) {
                                            while (flight?.key == cardKey) {
                                                delay(25L)
                                            }
                                            delay(90L)
                                        }
                                        if (armedThemes[packageName] == armedTimestamp) {
                                            armedThemes.remove(packageName)
                                        }
                                    }

                                    val active = currentStyle == ROTATION_STYLE_CUSTOM_APK &&
                                        packageName == customPackage
                                    val displayTitle = packageName.removePrefix(ROTATION_THEME_PACKAGE_PREFIX)
                                    LoopEffectCard(
                                        title = displayTitle,
                                        subtitle = null,
                                        packageName = packageName,
                                        isActive = active,
                                        isHiddenForFlight = flight?.key == cardKey,
                                        onExpand = { bounds, provider ->
                                            // If the timer is in the boundary window (< 450ms left) when opening,
                                            // extend it slightly so it doesn't flip mid-open flight.
                                            val curArmed = armedThemes[packageName]
                                            if (curArmed != null) {
                                                val now = android.os.SystemClock.uptimeMillis()
                                                val rem = (curArmed + 3000L) - now
                                                if (rem in 1L..450L) {
                                                    armedThemes[packageName] = now - 3000L + 500L
                                                }
                                            }
                                            flight = StudioCard.Theme(
                                                key = cardKey,
                                                title = displayTitle,
                                                subtitle = null,
                                                source = bounds,
                                                sourceBoundsProvider = provider,
                                                packageName = packageName,
                                                isTrashArmedProvider = { armedThemes.containsKey(packageName) }
                                            )
                                        },
                                        applyLabel = stringResource(R.string.rotation_anim_apply),
                                        activeLabel = stringResource(R.string.rotation_anim_active),
                                        onApply = { applyCustomTheme(packageName) },
                                        showTrash = true,
                                        trashArmed = confirmDelete,
                                        onTrash = {
                                            if (confirmDelete) {
                                                armedThemes.remove(packageName)
                                                uninstallTheme(packageName)
                                            } else {
                                                armedThemes[packageName] = android.os.SystemClock.uptimeMillis()
                                            }
                                        },
                                        preview = {
                                            RotationCustomAnimPreview(
                                                packageName = packageName,
                                                speedMultiplier = speed
                                            )
                                        }
                                    )
                                }
                                item(key = "pep_add") {
                                    StudioAddCard {
                                        scope.launch {
                                            pagerState.animateScrollToPage(STUDIO_TAB_BUILDER, isDirectTap = true)
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
                                        bottom = REBOOT_BUBBLE_CONTENT_BOTTOM_PADDING + 120.dp
                                    ),
                                    verticalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    item(key = "builder_import") {
                                        StudioImportCard(
                                            exitUri = exitUri,
                                            enterUri = enterUri,
                                            isCompiling = isCompiling,
                                            compileError = compileError,
                                            onPickExit = { exitPicker.launch("text/xml") },
                                            onPickEnter = { enterPicker.launch("text/xml") },
                                            onCompile = ::doCompileImported
                                        )
                                    }

                                    item(key = "builder_constructor") {
                                        StudioConstructorCard(
                                            selectedTab = constructorTab,
                                            onTabSelected = { constructorTab = it },
                                            exitParams = constructorExit,
                                            enterParams = constructorEnter,
                                            onExitChange = { constructorExit = it },
                                            onEnterChange = { constructorEnter = it },
                                            isInterpolatorMorphOpen = interpolatorRequest != null,
                                            onOpenInterpolatorMorph = { req ->
                                                interpolatorRequest = req
                                            }
                                        )
                                    }

                                    if (compileLog.isNotEmpty()) {
                                        item(key = "builder_compile_log") {
                                            StudioCompileLogCard(compileLog = compileLog)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // (BuilderPreviewFloatBar lives outside SliderMorphRoot so sliders morph underneath it)

                // Level-1 Modal A: Card Flight Overlay (Builtin / Collected cards)
                CardFlightOverlay(
                    target = flight,
                    onClose = {
                        flightClosing = false
                        flight = null
                    },
                    applyLabel = stringResource(R.string.rotation_anim_apply),
                    activeLabel = stringResource(R.string.rotation_anim_active),
                    isActive = { card ->
                        when (card) {
                            is StudioCard.Builtin -> card.styleId == currentStyle
                            is StudioCard.Theme -> currentStyle == ROTATION_STYLE_CUSTOM_APK &&
                                card.packageName == customPackage
                        }
                    },
                    onApplyCard = { card ->
                        when (card) {
                            is StudioCard.Builtin -> applyStyle(card.styleId)
                            is StudioCard.Theme -> applyCustomTheme(card.packageName)
                        }
                    },
                    onDeleteCard = { card ->
                        if (card is StudioCard.Theme) {
                            armedThemes.remove(card.packageName)
                            uninstallTheme(card.packageName)
                        }
                    },
                    speed = speed,
                    onSpeedChange = ::updateSpeed,
                    showSpeedSliderFor = { card ->
                        !(card is StudioCard.Builtin && card.styleId == ROTATION_STYLE_DEFAULT)
                    },
                    scrollBlur = scrollBlur,
                    onProgress = { flightT = it },
                    previewContent = { card ->
                        StudioLoopPreview(
                            spec = card.loopSpec,
                            width = 110.dp,
                            height = 190.dp,
                            speedMultiplier = if (card is StudioCard.Builtin &&
                                card.styleId == ROTATION_STYLE_DEFAULT
                            ) 1f else speed
                        )
                    },
                    topInsetPx = effectiveHeaderHeightPx,
                    closeSignal = level1CloseSignal,
                    onClosingChanged = { isClosing ->
                        flightClosing = isClosing
                        if (isClosing) {
                            // If closing starts when the trash button is in the boundary window
                            // (< 450ms left), hold the circle state until close finishes!
                            val pkg = (flight as? StudioCard.Theme)?.packageName
                            val curArmed = if (pkg != null) armedThemes[pkg] else null
                            if (pkg != null && curArmed != null) {
                                val now = android.os.SystemClock.uptimeMillis()
                                val rem = (curArmed + 3000L) - now
                                if (rem in 1L..450L) {
                                    armedThemes[pkg] = now - 3000L + 450L
                                }
                            }
                        }
                    }
                )

                // Level-1 Modal B: Builder Preview & In-Place Morphed Compile Progress Modal
                BuilderPreviewModalOverlay(
                    open = builderPreviewOpen,
                    sourceRect = builderPreviewSource,
                    sourceBoundsProvider = builderPreviewBoundsProvider,
                    exitParams = constructorExit,
                    enterParams = constructorEnter,
                    compileState = compileDialogState,
                    clockState = constructorClockState,
                    onBuildTheme = ::doCompileConstructor,
                    onCancelCompile = ::cancelCompile,
                    onDismissCompile = ::dismissCompileDialog,
                    onClose = { builderPreviewOpen = false },
                    scrollBlur = scrollBlur,
                    onProgress = { builderModalT = it },
                    topInsetPx = effectiveHeaderHeightPx,
                    closeSignal = level1CloseSignal
                )

                // Level-1 Modal C: Interpolator Selection Morph Modal
                InterpolatorMorphOverlay(
                    request = interpolatorRequest,
                    onClose = { interpolatorRequest = null },
                    scrollBlur = scrollBlur,
                    onProgress = { interpolatorModalT = it },
                    topInsetPx = effectiveHeaderHeightPx,
                    closeSignal = level1CloseSignal
                )
            }
        } // end SliderMorphRoot

        // Floating Frosted Builder Preview Bar at the bottom of Screen 3:
        // Placed OUTSIDE SliderMorphRoot (zIndex = 2f when idle / slider open) so
        // constructor sliders morph from UNDERNEATH it with real-time 2-pass blur!
        // When compile info modal closes, smoothly fades in via (1f - builderModalT).
        val floatBarPageVisibility = sstepE(pagerState.position, 1.15f, 1.95f)
        val floatBarCompileFade = if (compileDialogState != null) {
            (1f - builderModalT).coerceIn(0f, 1f)
        } else {
            1f
        }
        BuilderPreviewFloatBar(
            exitParams = constructorExit,
            enterParams = constructorEnter,
            clockState = constructorClockState,
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
                .zIndex(if (anyLevel1ModalOpen) 0.5f else 2f)
        )

        // Common frost mask over Pinned Header zone (zIndex = 2f inside TopBarBlurOverlay)
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

        // Status bar edge veil (zIndex = 3f inside TopStatusVeil)
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
            // 1. Pinned TopAppBar Row (always visible; collapsible area clips strictly below it)
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
                    text = stringResource(R.string.rotation_anim_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // 2. Clipped Collapsible Container for Switcher + Tabs:
            // Clips strictly at its top edge (y = 0, right under TopAppBar),
            // so scrolling hides Switcher + Tabs into an invisible clipped region
            // without ever intersecting or passing behind the transparent TopAppBar!
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
                val tabTitles = listOf(
                    stringResource(R.string.rotation_anim_tab_builtin),
                    stringResource(R.string.rotation_anim_tab_collected),
                    stringResource(R.string.rotation_anim_tab_builder)
                )

                Column(modifier = Modifier.fillMaxWidth()) {
                    Spacer(Modifier.height(4.dp))
                    StudioTabs(
                        titles = tabTitles,
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

        // Full-header tap/swipe blocker when any pseudo-modal is open:
        // intercepts touches on TopAppBar, Switcher, and Tabs and exits the active modal!
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

internal enum class AnimationCompilePhase {
    COMPILING,
    INSTALLING,
    SUCCESS,
    ERROR,
    CANCELLED
}

internal data class AnimationCompileState(
    val phase: AnimationCompilePhase,
    val progress: Float,
    val detail: String = ""
)

internal data class CompileDialogLabels(
    val compiling: Int,
    val installing: Int,
    val success: Int,
    val error: Int,
    val cancelled: Int,
    val progress: Int,
    val cancel: Int,
    val ok: Int
)

@Composable
internal fun AnimationCompileDialog(
    state: AnimationCompileState,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
    labels: CompileDialogLabels = CompileDialogLabels(
        compiling = R.string.anim_compile_dialog_compiling,
        installing = R.string.anim_compile_dialog_installing,
        success = R.string.anim_compile_dialog_success,
        error = R.string.anim_compile_dialog_error,
        cancelled = R.string.anim_compile_dialog_cancelled,
        progress = R.string.anim_compile_dialog_progress,
        cancel = R.string.anim_compile_dialog_cancel,
        ok = R.string.anim_compile_dialog_ok
    )
) {
    val terminal = state.phase == AnimationCompilePhase.SUCCESS ||
        state.phase == AnimationCompilePhase.ERROR ||
        state.phase == AnimationCompilePhase.CANCELLED
    val animatedProgress by animateFloatAsState(
        targetValue = state.progress.coerceIn(0f, 1f),
        animationSpec = tween(250, easing = FastOutSlowInEasing),
        label = "compile_progress"
    )
    Dialog(
        onDismissRequest = { if (terminal) onDismiss() },
        properties = DialogProperties(
            dismissOnBackPress = terminal,
            dismissOnClickOutside = terminal
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            )
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AnimatedContent(
                    targetState = terminal,
                    transitionSpec = {
                        (fadeIn(tween(220, easing = FastOutSlowInEasing)) +
                            scaleIn(animationSpec = tween(220, easing = FastOutSlowInEasing))) togetherWith
                            fadeOut(tween(140, easing = FastOutSlowInEasing))
                    },
                    label = "compile_status"
                ) { isTerminal ->
                    if (isTerminal) {
                        when (state.phase) {
                            AnimationCompilePhase.SUCCESS -> Icon(
                                Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(58.dp)
                            )
                            AnimationCompilePhase.ERROR -> Icon(
                                Icons.Rounded.Error,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(58.dp)
                            )
                            else -> Icon(
                                Icons.Rounded.Cancel,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(58.dp)
                            )
                        }
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Rounded.Build,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .size(58.dp)
                                    .scale(1f + animatedProgress * 0.04f)
                            )
                            Spacer(Modifier.size(16.dp))
                            LinearProgressIndicator(
                                progress = { animatedProgress },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
                Spacer(Modifier.size(if (terminal) 16.dp else 8.dp))
                Text(
                    text = when (state.phase) {
                        AnimationCompilePhase.COMPILING -> stringResource(labels.compiling)
                        AnimationCompilePhase.INSTALLING -> stringResource(labels.installing)
                        AnimationCompilePhase.SUCCESS -> stringResource(labels.success)
                        AnimationCompilePhase.ERROR -> stringResource(labels.error)
                        AnimationCompilePhase.CANCELLED -> stringResource(labels.cancelled)
                    },
                    style = MaterialTheme.typography.titleMedium
                )
                if (!terminal) {
                    Spacer(Modifier.size(8.dp))
                    Text(
                        stringResource(labels.progress, (animatedProgress * 100).toInt()),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (state.detail.isNotBlank()) {
                    Spacer(Modifier.size(8.dp))
                    Text(
                        state.detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.size(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    if (!terminal) {
                        TextButton(onClick = onCancel) {
                            Text(stringResource(labels.cancel))
                        }
                    } else {
                        Button(onClick = onDismiss) {
                            Text(stringResource(labels.ok))
                        }
                    }
                }
            }
        }
    }
}