package org.pixel.customparts.activities

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AccelerateInterpolator
import android.view.animation.AnticipateInterpolator
import android.view.animation.AnticipateOvershootInterpolator
import android.view.animation.BounceInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.Interpolator
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
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
import org.pixel.customparts.utils.PixelPartsTileRefresher
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

class ActivityTransitionActivity : ComponentActivity() {
    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        setContent {
            val darkTheme = isSystemInDarkTheme()
            val context = LocalContext.current
            val colorScheme = if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
            MaterialTheme(colorScheme = colorScheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ActivityTransitionScreen(onBack = { finish() })
                }
            }
        }
    }
}

// ── Main Screen ─────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityTransitionScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    // Experimental flags (res/values/experimental_flags.xml): restart the app to pick up changes.
    val expTransitionSpeedSliders = remember {
        context.resources.getBoolean(R.bool.exp_transition_speed_sliders)
    }

    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE ||
        configuration.screenWidthDp >= 600

    var currentOpenMode by remember {
        mutableIntStateOf(
            try {
                Settings.Global.getInt(context.contentResolver, SettingsKeys.ACTIVITY_OPEN_TRANSITION)
            } catch (_: Exception) {
                MODE_DISABLED
            }
        )
    }
    var currentCloseMode by remember {
        mutableIntStateOf(
            try {
                Settings.Global.getInt(context.contentResolver, SettingsKeys.ACTIVITY_CLOSE_TRANSITION)
            } catch (_: Exception) {
                MODE_DISABLED
            }
        )
    }

    var customOpenPackage by remember {
        mutableStateOf(
            try {
                Settings.Global.getString(context.contentResolver, SettingsKeys.ACTIVITY_OPEN_CUSTOM_PACKAGE)
                    ?: Settings.Global.getString(context.contentResolver, SettingsKeys.ACTIVITY_TRANSITION_CUSTOM_PACKAGE)
                    ?: ""
            } catch (_: Exception) {
                ""
            }
        )
    }
    var customClosePackage by remember {
        mutableStateOf(
            try {
                Settings.Global.getString(context.contentResolver, SettingsKeys.ACTIVITY_CLOSE_CUSTOM_PACKAGE)
                    ?: Settings.Global.getString(context.contentResolver, SettingsKeys.ACTIVITY_TRANSITION_CUSTOM_PACKAGE)
                    ?: ""
            } catch (_: Exception) {
                ""
            }
        )
    }

    var predictiveBackDisabled by remember {
        mutableStateOf(
            try {
                Settings.Global.getInt(context.contentResolver, SettingsKeys.DISABLE_PREDICTIVE_BACK_ANIM) != 0
            } catch (_: Exception) {
                false
            }
        )
    }

    var installedThemes by remember { mutableStateOf(scanInstalledTransitionThemes(context)) }
    var openSpeed by remember {
        mutableFloatStateOf(readTransitionSpeed(context, SettingsKeys.ACTIVITY_OPEN_TRANSITION_SPEED))
    }
    var closeSpeed by remember {
        mutableFloatStateOf(readTransitionSpeed(context, SettingsKeys.ACTIVITY_CLOSE_TRANSITION_SPEED))
    }

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
        installedThemes = scanInstalledTransitionThemes(context)
    }

    fun applyOpen(mode: Int) {
        Settings.Global.putInt(context.contentResolver, SettingsKeys.ACTIVITY_OPEN_TRANSITION, mode)
        PixelPartsTileRefresher.requestForSetting(context, SettingsKeys.ACTIVITY_OPEN_TRANSITION)
        currentOpenMode = mode
    }

    fun applyClose(mode: Int) {
        Settings.Global.putInt(context.contentResolver, SettingsKeys.ACTIVITY_CLOSE_TRANSITION, mode)
        PixelPartsTileRefresher.requestForSetting(context, SettingsKeys.ACTIVITY_CLOSE_TRANSITION)
        currentCloseMode = mode
        if (mode != MODE_DISABLED) {
            predictiveBackDisabled = true
            Settings.Global.putInt(context.contentResolver, SettingsKeys.DISABLE_PREDICTIVE_BACK_ANIM, 1)
        }
    }

    fun applyBothBuiltin(mode: Int) {
        applyOpen(mode)
        applyClose(mode)
    }

    fun applyCustomOpen(packageName: String) {
        Settings.Global.putString(context.contentResolver, SettingsKeys.ACTIVITY_OPEN_CUSTOM_PACKAGE, packageName)
        customOpenPackage = packageName
        applyOpen(MODE_CUSTOM)
    }

    fun applyCustomClose(packageName: String) {
        Settings.Global.putString(context.contentResolver, SettingsKeys.ACTIVITY_CLOSE_CUSTOM_PACKAGE, packageName)
        customClosePackage = packageName
        applyClose(MODE_CUSTOM)
    }

    fun applyCustomBoth(packageName: String) {
        Settings.Global.putString(context.contentResolver, SettingsKeys.ACTIVITY_TRANSITION_CUSTOM_PACKAGE, packageName)
        Settings.Global.putString(context.contentResolver, SettingsKeys.ACTIVITY_OPEN_CUSTOM_PACKAGE, packageName)
        Settings.Global.putString(context.contentResolver, SettingsKeys.ACTIVITY_CLOSE_CUSTOM_PACKAGE, packageName)
        customOpenPackage = packageName
        customClosePackage = packageName
        applyOpen(MODE_CUSTOM)
        applyClose(MODE_CUSTOM)
    }

    fun updateOpenSpeed(value: Float) {
        val clamped = value.coerceIn(0.1f, 10f)
        openSpeed = clamped
        try {
            Settings.Global.putFloat(
                context.contentResolver,
                SettingsKeys.ACTIVITY_OPEN_TRANSITION_SPEED,
                clamped
            )
        } catch (_: Exception) {
        }
    }

    fun updateCloseSpeed(value: Float) {
        val clamped = value.coerceIn(0.1f, 10f)
        closeSpeed = clamped
        try {
            Settings.Global.putFloat(
                context.contentResolver,
                SettingsKeys.ACTIVITY_CLOSE_TRANSITION_SPEED,
                clamped
            )
        } catch (_: Exception) {
        }
    }

    fun uninstallTheme(packageName: String) {
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                AnimThemeCompiler.uninstall(context, packageName)
            }
            if (ok) {
                listOf(
                    SettingsKeys.ACTIVITY_TRANSITION_CUSTOM_PACKAGE,
                    SettingsKeys.ACTIVITY_OPEN_CUSTOM_PACKAGE,
                    SettingsKeys.ACTIVITY_CLOSE_CUSTOM_PACKAGE
                ).forEach { key ->
                    try {
                        val current = Settings.Global.getString(context.contentResolver, key)
                        if (packageName == current) {
                            Settings.Global.putString(context.contentResolver, key, null)
                        }
                    } catch (_: Exception) {
                    }
                }
                if (customOpenPackage == packageName) {
                    customOpenPackage = ""
                    if (currentOpenMode == MODE_CUSTOM) applyOpen(MODE_DISABLED)
                }
                if (customClosePackage == packageName) {
                    customClosePackage = ""
                    if (currentCloseMode == MODE_CUSTOM) applyClose(MODE_DISABLED)
                }
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

    var openEnterUri by remember { mutableStateOf<Uri?>(null) }
    var openExitUri by remember { mutableStateOf<Uri?>(null) }
    var closeEnterUri by remember { mutableStateOf<Uri?>(null) }
    var closeExitUri by remember { mutableStateOf<Uri?>(null) }

    var compileLog by remember { mutableStateOf<List<String>>(emptyList()) }
    var isCompiling by remember { mutableStateOf(false) }
    var compileError by remember { mutableStateOf<String?>(null) }
    var compileDialogState by remember { mutableStateOf<AnimationCompileState?>(null) }
    var compileJob by remember { mutableStateOf<Job?>(null) }
    var compileCancellation by remember { mutableStateOf<AtomicBoolean?>(null) }
    var compileOperationId by remember { mutableIntStateOf(0) }
    var compiledPackageName by remember { mutableStateOf<String?>(null) }

    // 2 rows of constructor tabs:
    // Row 1: Enter (0) | Close (1)
    // Row 2: Start (0) | End (1)
    var constructorDirectionTab by remember { mutableIntStateOf(0) }
    var constructorPhaseTab by remember { mutableIntStateOf(0) }

    var cOpenEnter by remember {
        mutableStateOf(AnimParams(translateFromX = 100f, translateToX = 0f, duration = 350))
    }
    var cOpenExit by remember {
        mutableStateOf(AnimParams(alphaFrom = 1f, alphaTo = 0.7f, duration = 350))
    }
    var cCloseEnter by remember {
        mutableStateOf(AnimParams(alphaFrom = 0.7f, alphaTo = 1f, duration = 350))
    }
    var cCloseExit by remember {
        mutableStateOf(AnimParams(translateFromX = 0f, translateToX = 100f, duration = 350))
    }

    val openEnterPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) {
        if (it != null) openEnterUri = it
    }
    val openExitPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) {
        if (it != null) openExitUri = it
    }
    val closeEnterPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) {
        if (it != null) closeEnterUri = it
    }
    val closeExitPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) {
        if (it != null) closeExitUri = it
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
        if (name.isBlank() || openEnterUri == null || openExitUri == null) return
        startCompile { cancellation, logCallback ->
            AnimThemeCompiler.compile(
                context = context,
                styleName = name,
                openEnterUri = openEnterUri!!,
                openExitUri = openExitUri!!,
                closeEnterUri = closeEnterUri,
                closeExitUri = closeExitUri,
                logCallback = AnimThemeCompiler.LogCallback(logCallback),
                isCancelled = cancellation::get
            )
        }
    }

    fun doCompileConstructor(name: String) {
        if (name.isBlank()) return
        startCompile { cancellation, logCallback ->
            AnimThemeCompiler.compileFromXml(
                context = context,
                styleName = name,
                openEnterXml = animParamsToXml(cOpenEnter),
                openExitXml = animParamsToXml(cOpenExit),
                closeEnterXml = animParamsToXml(cCloseEnter),
                closeExitXml = animParamsToXml(cCloseExit),
                logCallback = AnimThemeCompiler.LogCallback(logCallback),
                isCancelled = cancellation::get
            )
        }
    }

    // 3-pass non-cyclic blur states:
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

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
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
                if (flight != null ||
                    builderPreviewOpen ||
                    compileDialogState != null ||
                    interpolatorRequest != null ||
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

    val builderPageFraction = sstepE(pagerState.position, 1.05f, 1.95f)
    val switcherPortraitVisibility = if (isLandscape) 1f else (1f - builderPageFraction)

    val visibleCollapsiblePx = (collapsibleMaxHeightPx + collapseOffsetPx).coerceAtLeast(0f)
    val effectiveHeaderHeightPx = statusPlusTopBarPx + visibleCollapsiblePx
    val effectiveHeaderHeightDp = with(density) { effectiveHeaderHeightPx.toDp() }
    val listTopPaddingDp = with(density) { (statusPlusTopBarPx + collapsibleMaxHeightPx).toDp() }

    val openLabelText = stringResource(R.string.anim_preview_open)
    val closeLabelText = stringResource(R.string.anim_preview_close)

    fun directionBadgeSubtitle(isOpen: Boolean, isClose: Boolean): String? = when {
        isOpen && isClose -> "$openLabelText + $closeLabelText"
        isOpen -> openLabelText
        isClose -> closeLabelText
        else -> null
    }

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
                                    val title = stringResource(R.string.anim_builtin_disabled)
                                    val isOpen = currentOpenMode == MODE_DISABLED
                                    val isClose = currentCloseMode == MODE_DISABLED
                                    val active = isOpen && isClose
                                    val subtitle = directionBadgeSubtitle(isOpen, isClose)
                                    val cardKey = "pep_default"
                                    val spec = remember {
                                        EffectLoopSpec.forTransitionRes(
                                            openEnterRes = R.anim.stock_activity_open_enter,
                                            openExitRes = R.anim.stock_activity_open_exit,
                                            closeEnterRes = R.anim.stock_activity_close_enter,
                                            closeExitRes = R.anim.stock_activity_close_exit
                                        )
                                    }
                                    LoopEffectCard(
                                        title = title,
                                        subtitle = subtitle,
                                        isActive = active,
                                        isHiddenForFlight = flight?.key == cardKey,
                                        onExpand = { bounds, provider ->
                                            flight = StudioCard.Builtin(
                                                key = cardKey,
                                                title = title,
                                                subtitle = subtitle,
                                                source = bounds,
                                                sourceBoundsProvider = provider,
                                                styleId = MODE_DISABLED,
                                                exitAnim = R.anim.stock_activity_open_exit,
                                                enterAnim = R.anim.stock_activity_open_enter,
                                                loopSpec = spec
                                            )
                                        },
                                        applyLabel = stringResource(R.string.rotation_anim_apply),
                                        activeLabel = stringResource(R.string.rotation_anim_active),
                                        onApply = { applyBothBuiltin(MODE_DISABLED) },
                                        preview = {
                                            StudioLoopPreview(
                                                spec = spec,
                                                speedMultiplier = 1f,
                                                step2SpeedMultiplier = 1f
                                            )
                                        }
                                    )
                                }

                                item(key = "pep_no_animation") {
                                    val title = stringResource(R.string.anim_no_animation_title)
                                    val isOpen = currentOpenMode == MODE_NO_ANIMATION
                                    val isClose = currentCloseMode == MODE_NO_ANIMATION
                                    val active = isOpen && isClose
                                    val subtitle = directionBadgeSubtitle(isOpen, isClose)
                                    val cardKey = "pep_no_animation"
                                    val spec = remember {
                                        EffectLoopSpec.forTransitionRes(
                                            openEnterRes = R.anim.no_anim,
                                            openExitRes = R.anim.no_anim,
                                            closeEnterRes = R.anim.no_anim,
                                            closeExitRes = R.anim.no_anim
                                        )
                                    }
                                    LoopEffectCard(
                                        title = title,
                                        subtitle = subtitle,
                                        isActive = active,
                                        isHiddenForFlight = flight?.key == cardKey,
                                        onExpand = { bounds, provider ->
                                            flight = StudioCard.Builtin(
                                                key = cardKey,
                                                title = title,
                                                subtitle = subtitle,
                                                source = bounds,
                                                sourceBoundsProvider = provider,
                                                styleId = MODE_NO_ANIMATION,
                                                exitAnim = R.anim.no_anim,
                                                enterAnim = R.anim.no_anim,
                                                loopSpec = spec
                                            )
                                        },
                                        applyLabel = stringResource(R.string.rotation_anim_apply),
                                        activeLabel = stringResource(R.string.rotation_anim_active),
                                        onApply = { applyBothBuiltin(MODE_NO_ANIMATION) },
                                        preview = {
                                            StudioLoopPreview(
                                                spec = spec,
                                                speedMultiplier = openSpeed,
                                                step2SpeedMultiplier = closeSpeed
                                            )
                                        }
                                    )
                                }

                                items(BUILTIN_MODES, key = { it.modeId }) { mode ->
                                    val groupName = stringResource(mode.groupNameRes)
                                    val dirName = mode.dirNameRes?.let { stringResource(it) }
                                    val title = if (dirName != null) "$groupName — $dirName" else groupName
                                    val isOpen = currentOpenMode == mode.modeId
                                    val isClose = currentCloseMode == mode.modeId
                                    val active = isOpen && isClose
                                    val subtitle = directionBadgeSubtitle(isOpen, isClose)
                                    val cardKey = "builtin_${mode.modeId}"
                                    val spec = remember(mode.modeId) {
                                        EffectLoopSpec.forTransitionRes(
                                            openEnterRes = mode.openEnterAnim,
                                            openExitRes = mode.openExitAnim,
                                            closeEnterRes = mode.closeEnterAnim,
                                            closeExitRes = mode.closeExitAnim
                                        )
                                    }
                                    LoopEffectCard(
                                        title = title,
                                        subtitle = subtitle,
                                        isActive = active,
                                        isHiddenForFlight = flight?.key == cardKey,
                                        onExpand = { bounds, provider ->
                                            flight = StudioCard.Builtin(
                                                key = cardKey,
                                                title = title,
                                                subtitle = subtitle,
                                                source = bounds,
                                                sourceBoundsProvider = provider,
                                                styleId = mode.modeId,
                                                exitAnim = mode.openExitAnim,
                                                enterAnim = mode.openEnterAnim,
                                                loopSpec = spec
                                            )
                                        },
                                        applyLabel = stringResource(R.string.rotation_anim_apply),
                                        activeLabel = stringResource(R.string.rotation_anim_active),
                                        onApply = { applyBothBuiltin(mode.modeId) },
                                        preview = {
                                            StudioLoopPreview(
                                                spec = spec,
                                                speedMultiplier = openSpeed,
                                                step2SpeedMultiplier = closeSpeed
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
                                            val now = SystemClock.uptimeMillis()
                                            val remaining = (armedTimestamp + totalDurationMs) - now
                                            if (remaining <= 0L) break
                                            delay(remaining.coerceAtMost(80L))
                                        }
                                        while (flight?.key == cardKey && !flightClosing && flightT < 0.99f) {
                                            delay(25L)
                                        }
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

                                    val isOpen = currentOpenMode == MODE_CUSTOM && packageName == customOpenPackage
                                    val isClose = currentCloseMode == MODE_CUSTOM && packageName == customClosePackage
                                    val active = isOpen && isClose
                                    val displayTitle = packageName.removePrefix(THEME_PACKAGE_PREFIX)
                                    val subtitle = directionBadgeSubtitle(isOpen, isClose)
                                    val spec = remember(packageName) {
                                        EffectLoopSpec.forTransitionTheme(packageName)
                                    }

                                    LoopEffectCard(
                                        title = displayTitle,
                                        subtitle = subtitle,
                                        packageName = packageName,
                                        isActive = active,
                                        isHiddenForFlight = flight?.key == cardKey,
                                        onExpand = { bounds, provider ->
                                            val curArmed = armedThemes[packageName]
                                            if (curArmed != null) {
                                                val now = SystemClock.uptimeMillis()
                                                val rem = (curArmed + 3000L) - now
                                                if (rem in 1L..450L) {
                                                    armedThemes[packageName] = now - 3000L + 500L
                                                }
                                            }
                                            flight = StudioCard.Theme(
                                                key = cardKey,
                                                title = displayTitle,
                                                subtitle = subtitle,
                                                source = bounds,
                                                sourceBoundsProvider = provider,
                                                packageName = packageName,
                                                isTrashArmedProvider = { armedThemes.containsKey(packageName) },
                                                loopSpec = spec
                                            )
                                        },
                                        applyLabel = stringResource(R.string.rotation_anim_apply),
                                        activeLabel = stringResource(R.string.rotation_anim_active),
                                        onApply = { applyCustomBoth(packageName) },
                                        showTrash = true,
                                        trashArmed = confirmDelete,
                                        onTrash = {
                                            if (confirmDelete) {
                                                armedThemes.remove(packageName)
                                                uninstallTheme(packageName)
                                            } else {
                                                armedThemes[packageName] = SystemClock.uptimeMillis()
                                            }
                                        },
                                        preview = {
                                            StudioLoopPreview(
                                                spec = spec,
                                                speedMultiplier = openSpeed,
                                                step2SpeedMultiplier = closeSpeed
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
                                        StudioTransitionImportCard(
                                            openEnterUri = openEnterUri,
                                            openExitUri = openExitUri,
                                            closeEnterUri = closeEnterUri,
                                            closeExitUri = closeExitUri,
                                            isCompiling = isCompiling,
                                            compileError = compileError,
                                            onPickOpenEnter = { openEnterPicker.launch("text/xml") },
                                            onPickOpenExit = { openExitPicker.launch("text/xml") },
                                            onPickCloseEnter = { closeEnterPicker.launch("text/xml") },
                                            onPickCloseExit = { closeExitPicker.launch("text/xml") },
                                            onCompile = ::doCompileImported
                                        )
                                    }

                                    item(key = "builder_constructor") {
                                        StudioTransitionConstructorCard(
                                            directionTab = constructorDirectionTab,
                                            onDirectionTabSelected = { constructorDirectionTab = it },
                                            phaseTab = constructorPhaseTab,
                                            onPhaseTabSelected = { constructorPhaseTab = it },
                                            openEnterParams = cOpenEnter,
                                            openExitParams = cOpenExit,
                                            closeEnterParams = cCloseEnter,
                                            closeExitParams = cCloseExit,
                                            onOpenEnterChange = { cOpenEnter = it },
                                            onOpenExitChange = { cOpenExit = it },
                                            onCloseEnterChange = { cCloseEnter = it },
                                            onCloseExitChange = { cCloseExit = it },
                                            isInterpolatorMorphOpen = interpolatorRequest != null,
                                            onOpenInterpolatorMorph = { req ->
                                                interpolatorRequest = req
                                            },
                                            directionTitles = listOf(stringResource(R.string.anim_direction_enter), stringResource(R.string.anim_direction_close)),
                                            phaseTitles = listOf(stringResource(R.string.anim_phase_start), stringResource(R.string.anim_phase_end))
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

                // Level-1 Modal A: Card Flight Overlay with Dual Slide-Out Preview,
                // Two Speed Sliders (Open & Close), and 3 Apply Buttons (Both / Open / Close)
                CardFlightOverlay(
                    target = flight,
                    onClose = {
                        flightClosing = false
                        flight = null
                    },
                    applyLabel = stringResource(R.string.rotation_anim_apply),
                    expandedApplyLabel = stringResource(R.string.anim_apply_both),
                    activeLabel = stringResource(R.string.rotation_anim_active),
                    applyOpenLabel = stringResource(R.string.anim_apply_open),
                    applyCloseLabel = stringResource(R.string.anim_apply_close),
                    isActive = { card ->
                        when (card) {
                            is StudioCard.Builtin -> currentOpenMode == card.styleId &&
                                currentCloseMode == card.styleId
                            is StudioCard.Theme -> currentOpenMode == MODE_CUSTOM &&
                                currentCloseMode == MODE_CUSTOM &&
                                card.packageName == customOpenPackage &&
                                card.packageName == customClosePackage
                        }
                    },
                    isOpenActive = { card ->
                        when (card) {
                            is StudioCard.Builtin -> currentOpenMode == card.styleId
                            is StudioCard.Theme -> currentOpenMode == MODE_CUSTOM &&
                                card.packageName == customOpenPackage
                        }
                    },
                    isCloseActive = { card ->
                        when (card) {
                            is StudioCard.Builtin -> currentCloseMode == card.styleId
                            is StudioCard.Theme -> currentCloseMode == MODE_CUSTOM &&
                                card.packageName == customClosePackage
                        }
                    },
                    subtitleProvider = { card ->
                        when (card) {
                            is StudioCard.Builtin -> directionBadgeSubtitle(
                                isOpen = currentOpenMode == card.styleId,
                                isClose = currentCloseMode == card.styleId
                            )
                            is StudioCard.Theme -> directionBadgeSubtitle(
                                isOpen = currentOpenMode == MODE_CUSTOM && card.packageName == customOpenPackage,
                                isClose = currentCloseMode == MODE_CUSTOM && card.packageName == customClosePackage
                            )
                        }
                    },
                    onApplyCard = { card ->
                        when (card) {
                            is StudioCard.Builtin -> applyBothBuiltin(card.styleId)
                            is StudioCard.Theme -> applyCustomBoth(card.packageName)
                        }
                    },
                    onApplyOpenCard = { card ->
                        when (card) {
                            is StudioCard.Builtin -> applyOpen(card.styleId)
                            is StudioCard.Theme -> applyCustomOpen(card.packageName)
                        }
                    },
                    onApplyCloseCard = { card ->
                        when (card) {
                            is StudioCard.Builtin -> applyClose(card.styleId)
                            is StudioCard.Theme -> applyCustomClose(card.packageName)
                        }
                    },
                    onDeleteCard = { card ->
                        if (card is StudioCard.Theme) {
                            armedThemes.remove(card.packageName)
                            uninstallTheme(card.packageName)
                        }
                    },
                    speed = openSpeed,
                    onSpeedChange = ::updateOpenSpeed,
                    speedTitle = "${stringResource(R.string.rotation_anim_speed)} ($openLabelText)",
                    secondarySpeed = closeSpeed,
                    onSecondarySpeedChange = ::updateCloseSpeed,
                    secondarySpeedTitle = "${stringResource(R.string.rotation_anim_speed)} ($closeLabelText)",
                    showSpeedSliderFor = { card ->
                        expTransitionSpeedSliders &&
                            !(card is StudioCard.Builtin && card.styleId == MODE_DISABLED)
                    },
                    scrollBlur = scrollBlur,
                    onProgress = { flightT = it },
                    firstPreviewLabel = openLabelText,
                    secondPreviewLabel = closeLabelText,
                    previewContent = { card ->
                        StudioLoopPreview(
                            spec = card.loopSpec.onlyStep1(),
                            width = 110.dp,
                            height = 190.dp,
                            speedMultiplier = if (card is StudioCard.Builtin &&
                                card.styleId == MODE_DISABLED
                            ) 1f else openSpeed
                        )
                    },
                    secondaryPreviewContent = { card ->
                        StudioLoopPreview(
                            spec = card.loopSpec.onlyStep2(),
                            width = 110.dp,
                            height = 190.dp,
                            speedMultiplier = if (card is StudioCard.Builtin &&
                                card.styleId == MODE_DISABLED
                            ) 1f else closeSpeed
                        )
                    },
                    topInsetPx = effectiveHeaderHeightPx,
                    closeSignal = level1CloseSignal,
                    onClosingChanged = { isClosing ->
                        flightClosing = isClosing
                        if (isClosing) {
                            val pkg = (flight as? StudioCard.Theme)?.packageName
                            val curArmed = if (pkg != null) armedThemes[pkg] else null
                            if (pkg != null && curArmed != null) {
                                val now = SystemClock.uptimeMillis()
                                val rem = (curArmed + 3000L) - now
                                if (rem in 1L..450L) {
                                    armedThemes[pkg] = now - 3000L + 450L
                                }
                            }
                        }
                    }
                )

                // Level-1 Modal B: Builder Preview Modal with Dual Slide-Out Cards (Open & Close)
                BuilderPreviewModalOverlay(
                    open = builderPreviewOpen,
                    sourceRect = builderPreviewSource,
                    sourceBoundsProvider = builderPreviewBoundsProvider,
                    exitParams = cOpenExit,
                    enterParams = cOpenEnter,
                    step2ExitParams = cCloseExit,
                    step2EnterParams = cCloseEnter,
                    isDualPreview = true,
                    firstPreviewLabel = openLabelText,
                    secondPreviewLabel = closeLabelText,
                    xmlNamedParams = listOf(
                        "custom_open_enter.xml" to cOpenEnter,
                        "custom_open_exit.xml" to cOpenExit,
                        "custom_close_enter.xml" to cCloseEnter,
                        "custom_close_exit.xml" to cCloseExit
                    ),
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

        // Floating Frosted Builder Preview Bar at the bottom of Screen 3
        val floatBarPageVisibility = sstepE(pagerState.position, 1.15f, 1.95f)
        val floatBarCompileFade = if (compileDialogState != null) {
            (1f - builderModalT).coerceIn(0f, 1f)
        } else {
            1f
        }
        BuilderPreviewFloatBar(
            exitParams = cOpenExit,
            enterParams = cOpenEnter,
            step2ExitParams = cCloseExit,
            step2EnterParams = cCloseEnter,
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

        // Common frost mask over Pinned Header zone (zIndex = 2f)
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

        // Status bar edge veil (zIndex = 3f)
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
            // 1. Pinned TopAppBar Row
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
                    text = stringResource(R.string.anim_transition_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // 2. Clipped Collapsible Container for Predictive Back Switcher + Tabs
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

                if (isLandscape) {
                    // Landscape / Tablet: single line [Switcher | Tabs]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            GenericSwitchRow(
                                title = stringResource(R.string.anim_predictive_back_title),
                                summary = stringResource(R.string.anim_predictive_back_desc),
                                checked = predictiveBackDisabled,
                                onCheckedChange = {
                                    predictiveBackDisabled = it
                                    Settings.Global.putInt(
                                        context.contentResolver,
                                        SettingsKeys.DISABLE_PREDICTIVE_BACK_ANIM,
                                        if (it) 1 else 0
                                    )
                                }
                            )
                        }
                        Box(modifier = Modifier.weight(1.15f)) {
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
                        }
                    }
                } else {
                    // Portrait: Switcher (collapses smoothly on Builder tab) \n Tabs
                    Column(modifier = Modifier.fillMaxWidth()) {
                        if (switcherPortraitVisibility > 0.005f) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .unfoldVerticalSlot(switcherPortraitVisibility)
                                    .alpha(switcherPortraitVisibility)
                            ) {
                                GenericSwitchRow(
                                    title = stringResource(R.string.anim_predictive_back_title),
                                    summary = stringResource(R.string.anim_predictive_back_desc),
                                    checked = predictiveBackDisabled,
                                    onCheckedChange = {
                                        predictiveBackDisabled = it
                                        Settings.Global.putInt(
                                            context.contentResolver,
                                            SettingsKeys.DISABLE_PREDICTIVE_BACK_ANIM,
                                            if (it) 1 else 0
                                        )
                                    }
                                )
                            }
                        }
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