package org.pixel.customparts.activities

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AccelerateInterpolator
import android.view.animation.AnimationUtils
import android.view.animation.AnticipateInterpolator
import android.view.animation.AnticipateOvershootInterpolator
import android.view.animation.BounceInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import android.view.animation.Interpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import org.pixel.customparts.R
import org.pixel.customparts.SettingsKeys
import org.pixel.customparts.dynamicDarkColorScheme
import org.pixel.customparts.dynamicLightColorScheme
import org.pixel.customparts.ui.REBOOT_BUBBLE_CONTENT_BOTTOM_PADDING
import org.pixel.customparts.ui.SettingsGroupCard
import org.pixel.customparts.ui.TopBarBlurOverlay
import org.pixel.customparts.ui.TopStatusVeil
import org.pixel.customparts.ui.recordLayer
import org.pixel.customparts.ui.rememberGraphicsLayerRecordingState
import org.pixel.customparts.utils.AnimThemeCompiler
import org.pixel.customparts.utils.PixelPartsTileRefresher
import org.pixel.customparts.utils.filterThemeNameInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.res.stringResource

// ── Constants ───────────────────────────────────────────────────────

internal const val THEME_PACKAGE_PREFIX = "org.pixel.customparts.anim."

internal const val MODE_DISABLED = 0
internal const val MODE_CUSTOM = -1
internal const val MODE_NO_ANIMATION = -2

// ── Built-in animation catalog ──────────────────────────────────────

internal data class AnimMode(
    val modeId: Int,
    val groupNameRes: Int,
    val dirNameRes: Int? = null,
    val openEnterAnim: Int,
    val openExitAnim: Int,
    val closeEnterAnim: Int,
    val closeExitAnim: Int
)

internal val BUILTIN_MODES: List<AnimMode> by lazy {
    listOf(
        // Slide
        AnimMode(10, R.string.anim_group_slide, R.string.anim_dir_right,
            R.anim.slide_in_right, R.anim.no_anim, R.anim.no_anim, R.anim.slide_out_right),
        AnimMode(11, R.string.anim_group_slide, R.string.anim_dir_left,
            R.anim.slide_in_left, R.anim.no_anim, R.anim.no_anim, R.anim.train_exit),
        AnimMode(12, R.string.anim_group_slide, R.string.anim_dir_top,
            R.anim.slide_in_top, R.anim.no_anim, R.anim.no_anim, R.anim.slide_out_top),
        AnimMode(13, R.string.anim_group_slide, R.string.anim_dir_bottom,
            R.anim.slide_up_enter, R.anim.no_anim, R.anim.no_anim, R.anim.slide_down_exit),
        // Card Stack
        AnimMode(20, R.string.anim_group_card_stack, R.string.anim_dir_right,
            R.anim.slide_in_right, R.anim.card_stack_exit, R.anim.card_stack_enter, R.anim.slide_out_right),
        AnimMode(21, R.string.anim_group_card_stack, R.string.anim_dir_left,
            R.anim.slide_in_left, R.anim.card_stack_exit_left, R.anim.card_stack_enter_left, R.anim.train_exit),
        AnimMode(22, R.string.anim_group_card_stack, R.string.anim_dir_top,
            R.anim.slide_in_top, R.anim.card_stack_exit_top, R.anim.card_stack_enter_top, R.anim.slide_out_top),
        AnimMode(23, R.string.anim_group_card_stack, R.string.anim_dir_bottom,
            R.anim.slide_up_enter, R.anim.card_stack_exit_bottom, R.anim.card_stack_enter_bottom, R.anim.slide_down_exit),
        // Train
        AnimMode(30, R.string.anim_group_train, R.string.anim_dir_right,
            R.anim.slide_in_right, R.anim.train_exit, R.anim.slide_in_left, R.anim.slide_out_right),
        AnimMode(31, R.string.anim_group_train, R.string.anim_dir_left,
            R.anim.slide_in_left, R.anim.slide_out_right, R.anim.slide_in_right, R.anim.train_exit),
        AnimMode(32, R.string.anim_group_train, R.string.anim_dir_top,
            R.anim.slide_in_top, R.anim.slide_down_exit, R.anim.slide_up_enter, R.anim.slide_out_top),
        AnimMode(33, R.string.anim_group_train, R.string.anim_dir_bottom,
            R.anim.slide_up_enter, R.anim.slide_out_top, R.anim.slide_in_top, R.anim.slide_down_exit),
        // iOS Parallax
        AnimMode(40, R.string.anim_group_ios, R.string.anim_dir_right,
            R.anim.slide_in_right, R.anim.ios_open_exit, R.anim.ios_close_enter, R.anim.slide_out_right),
        AnimMode(41, R.string.anim_group_ios, R.string.anim_dir_left,
            R.anim.slide_in_left, R.anim.ios_open_exit_left, R.anim.ios_close_enter_left, R.anim.train_exit),
        AnimMode(42, R.string.anim_group_ios, R.string.anim_dir_top,
            R.anim.slide_in_top, R.anim.ios_open_exit_top, R.anim.ios_close_enter_top, R.anim.slide_out_top),
        AnimMode(43, R.string.anim_group_ios, R.string.anim_dir_bottom,
            R.anim.slide_up_enter, R.anim.ios_open_exit_bottom, R.anim.ios_close_enter_bottom, R.anim.slide_down_exit),
        // Fade
        AnimMode(50, R.string.anim_group_fade, null,
            R.anim.fade_in, R.anim.fade_out, R.anim.fade_in, R.anim.fade_out),
        // Zoom
        AnimMode(60, R.string.anim_group_zoom, null,
            R.anim.zoom_enter, R.anim.zoom_exit, R.anim.zoom_close_enter, R.anim.zoom_close_exit),
        // Modal
        AnimMode(70, R.string.anim_group_modal, R.string.anim_dir_right,
            R.anim.slide_in_right, R.anim.slide_up_exit_right, R.anim.slide_up_close_enter_right, R.anim.slide_out_right),
        AnimMode(71, R.string.anim_group_modal, R.string.anim_dir_left,
            R.anim.slide_in_left, R.anim.slide_up_exit_left, R.anim.slide_up_close_enter_left, R.anim.train_exit),
        AnimMode(72, R.string.anim_group_modal, R.string.anim_dir_top,
            R.anim.slide_in_top, R.anim.slide_up_exit_top, R.anim.slide_up_close_enter_top, R.anim.slide_out_top),
        AnimMode(73, R.string.anim_group_modal, R.string.anim_dir_bottom,
            R.anim.slide_up_enter, R.anim.slide_up_exit, R.anim.slide_up_close_enter, R.anim.slide_down_exit),
        // Depth
        AnimMode(80, R.string.anim_group_depth, R.string.anim_dir_right,
            R.anim.slide_in_right, R.anim.depth_exit, R.anim.depth_close_enter, R.anim.slide_out_right),
        AnimMode(81, R.string.anim_group_depth, R.string.anim_dir_left,
            R.anim.slide_in_left, R.anim.depth_exit, R.anim.depth_close_enter, R.anim.train_exit),
        AnimMode(82, R.string.anim_group_depth, R.string.anim_dir_top,
            R.anim.slide_in_top, R.anim.depth_exit, R.anim.depth_close_enter, R.anim.slide_out_top),
        AnimMode(83, R.string.anim_group_depth, R.string.anim_dir_bottom,
            R.anim.slide_up_enter, R.anim.depth_exit, R.anim.depth_close_enter, R.anim.slide_down_exit),
        // Pivot
        AnimMode(90, R.string.anim_group_pivot, R.string.anim_dir_right,
            R.anim.pivot_enter, R.anim.pivot_exit, R.anim.pivot_close_enter, R.anim.pivot_close_exit),
        AnimMode(91, R.string.anim_group_pivot, R.string.anim_dir_left,
            R.anim.pivot_enter_left, R.anim.pivot_exit_left, R.anim.pivot_close_enter_left, R.anim.pivot_close_exit_left),
        AnimMode(92, R.string.anim_group_pivot, R.string.anim_dir_top,
            R.anim.pivot_enter_top, R.anim.pivot_exit_top, R.anim.pivot_close_enter_top, R.anim.pivot_close_exit_top),
        AnimMode(93, R.string.anim_group_pivot, R.string.anim_dir_bottom,
            R.anim.pivot_enter_bottom, R.anim.pivot_exit_bottom, R.anim.pivot_close_enter_bottom, R.anim.pivot_close_exit_bottom)
    )
}

internal fun modeDisplayName(context: Context, modeId: Int): String {
    if (modeId == MODE_DISABLED) return context.getString(R.string.anim_builtin_disabled)
    if (modeId == MODE_CUSTOM) return context.getString(R.string.anim_builtin_custom)
    if (modeId == MODE_NO_ANIMATION) return context.getString(R.string.anim_no_animation_title)
    val mode = BUILTIN_MODES.find { it.modeId == modeId } ?: return "Mode $modeId"
    val group = context.getString(mode.groupNameRes)
    return if (mode.dirNameRes != null) "$group — ${context.getString(mode.dirNameRes)}" else group
}

// ── Data classes ────────────────────────────────────────────────────

internal data class InstalledTheme(
    val packageName: String,
    val styleName: String,
    val isActiveOpen: Boolean,
    val isActiveClose: Boolean
)

// ── Animation Constructor data ──────────────────────────────────────

internal data class AnimParams(
    val translateFromX: Float = 0f,   // % parent width
    val translateToX: Float = 0f,
    val translateFromY: Float = 0f,   // % parent height
    val translateToY: Float = 0f,
    val scaleFromX: Float = 1f,
    val scaleToX: Float = 1f,
    val scaleFromY: Float = 1f,
    val scaleToY: Float = 1f,
    val alphaFrom: Float = 1f,
    val alphaTo: Float = 1f,
    val rotateFrom: Float = 0f,
    val rotateTo: Float = 0f,
    val pivotX: Float = 50f,          // % of view
    val pivotY: Float = 50f,
    val duration: Long = 350,
    val startOffset: Long = 0,
    val interpolatorIndex: Int = 0
)

internal val INTERPOLATOR_NAMES = listOf(
    "Linear", "Accelerate", "Decelerate", "AccelerateDecelerate",
    "Overshoot", "Bounce", "Anticipate", "AnticipateOvershoot"
)

internal fun resolveInterpolator(index: Int): Interpolator = when (index) {
    0 -> LinearInterpolator()
    1 -> AccelerateInterpolator()
    2 -> DecelerateInterpolator()
    3 -> AccelerateDecelerateInterpolator()
    4 -> OvershootInterpolator()
    5 -> BounceInterpolator()
    6 -> AnticipateInterpolator()
    7 -> AnticipateOvershootInterpolator()
    else -> LinearInterpolator()
}

internal fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

// ── Activity ────────────────────────────────────────────────────────

class ActivityTransitionActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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
    val blurState = rememberGraphicsLayerRecordingState()
    val lazyListState = rememberLazyListState()

    // ── settings state ──
    var currentOpenMode by remember {
        mutableIntStateOf(
            try { Settings.Global.getInt(context.contentResolver, SettingsKeys.ACTIVITY_OPEN_TRANSITION) }
            catch (_: Exception) { 0 }
        )
    }
    var currentCloseMode by remember {
        mutableIntStateOf(
            try { Settings.Global.getInt(context.contentResolver, SettingsKeys.ACTIVITY_CLOSE_TRANSITION) }
            catch (_: Exception) { 0 }
        )
    }

    // ── predictive back ──
    var predictiveBackDisabled by remember {
        mutableStateOf(
            try { Settings.Global.getInt(context.contentResolver, SettingsKeys.DISABLE_PREDICTIVE_BACK_ANIM) != 0 }
            catch (_: Exception) { false }
        )
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
        // Auto-enable predictive back disable when close animation is set
        if (mode != MODE_DISABLED) {
            predictiveBackDisabled = true
            Settings.Global.putInt(context.contentResolver, SettingsKeys.DISABLE_PREDICTIVE_BACK_ANIM, 1)
        }
    }

    // ── theme creator state ──
    var styleName by remember { mutableStateOf("") }
    var useXmlInput by remember { mutableStateOf(false) }
    var openEnterUri by remember { mutableStateOf<Uri?>(null) }
    var openExitUri by remember { mutableStateOf<Uri?>(null) }
    var closeEnterUri by remember { mutableStateOf<Uri?>(null) }
    var closeExitUri by remember { mutableStateOf<Uri?>(null) }
    var openEnterXml by remember { mutableStateOf("") }
    var openExitXml by remember { mutableStateOf("") }
    var closeEnterXml by remember { mutableStateOf("") }
    var closeExitXml by remember { mutableStateOf("") }
    var compileLog by remember { mutableStateOf<List<String>>(emptyList()) }
    var isCompiling by remember { mutableStateOf(false) }
    var compileError by remember { mutableStateOf<String?>(null) }
    var compileDialogState by remember { mutableStateOf<AnimationCompileState?>(null) }
    var compileJob by remember { mutableStateOf<Job?>(null) }
    var compileCancellation by remember { mutableStateOf<AtomicBoolean?>(null) }
    var compileOperationId by remember { mutableIntStateOf(0) }
    var compiledPackageName by remember { mutableStateOf<String?>(null) }
    var highlightedThemePackage by remember { mutableStateOf<String?>(null) }

    // ── constructor state ──
    var constructorTab by remember { mutableIntStateOf(0) } // 0=OpenEnter,1=OpenExit,2=CloseEnter,3=CloseExit
    var cOpenEnter by remember { mutableStateOf(AnimParams(translateFromX = 100f, translateToX = 0f)) }
    var cOpenExit by remember { mutableStateOf(AnimParams(alphaFrom = 1f, alphaTo = 0.7f)) }
    var cCloseEnter by remember { mutableStateOf(AnimParams(alphaFrom = 0.7f, alphaTo = 1f)) }
    var cCloseExit by remember { mutableStateOf(AnimParams(translateFromX = 0f, translateToX = 100f)) }
    var constructorPreviewKey by remember { mutableIntStateOf(0) }

    // ── installed themes ──
    var installedThemes by remember { mutableStateOf<List<InstalledTheme>>(emptyList()) }

    fun refreshThemes() {
        val activeOpenPkg = try {
            Settings.Global.getString(context.contentResolver, SettingsKeys.ACTIVITY_OPEN_CUSTOM_PACKAGE)
                ?: Settings.Global.getString(context.contentResolver, SettingsKeys.ACTIVITY_TRANSITION_CUSTOM_PACKAGE)
        } catch (_: Exception) { null }
        val activeClosePkg = try {
            Settings.Global.getString(context.contentResolver, SettingsKeys.ACTIVITY_CLOSE_CUSTOM_PACKAGE)
                ?: Settings.Global.getString(context.contentResolver, SettingsKeys.ACTIVITY_TRANSITION_CUSTOM_PACKAGE)
        } catch (_: Exception) { null }

        installedThemes = context.packageManager.getInstalledPackages(0)
            .filter { it.packageName.startsWith(THEME_PACKAGE_PREFIX) }
            .map { pi ->
                InstalledTheme(
                    packageName = pi.packageName,
                    styleName = pi.packageName.removePrefix(THEME_PACKAGE_PREFIX),
                    isActiveOpen = currentOpenMode == MODE_CUSTOM && pi.packageName == activeOpenPkg,
                    isActiveClose = currentCloseMode == MODE_CUSTOM && pi.packageName == activeClosePkg
                )
            }
            .sortedBy { it.styleName }
    }

    LaunchedEffect(Unit) { refreshThemes() }
    LaunchedEffect(currentOpenMode, currentCloseMode) { refreshThemes() }
    LaunchedEffect(highlightedThemePackage) {
        if (highlightedThemePackage != null) {
            delay(3500)
            highlightedThemePackage = null
        }
    }

    // ── file pickers ──
    val openEnterPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> if (uri != null) openEnterUri = uri }
    val openExitPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> if (uri != null) openExitUri = uri }
    val closeEnterPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> if (uri != null) closeEnterUri = uri }
    val closeExitPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> if (uri != null) closeExitUri = uri }

    fun progressForLog(
        phase: AnimationCompilePhase,
        line: String,
        current: Float
    ): Float {
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
        if (packageName != null) {
            highlightedThemePackage = packageName
            scope.launch {
                delay(100)
                val themeIndex = installedThemes.indexOfFirst { it.packageName == packageName }
                if (themeIndex >= 0) {
                    lazyListState.animateScrollToItem(5 + BUILTIN_MODES.size + themeIndex)
                }
            }
        }
    }

    // ── compile ──
    fun doCompile() {
        if (styleName.isBlank()) return
        if (!useXmlInput && (openEnterUri == null || openExitUri == null)) return
        if (useXmlInput && (openEnterXml.isBlank() || openExitXml.isBlank())) return
        startCompile { cancellation, logCallback ->
            if (useXmlInput) {
                AnimThemeCompiler.compileFromXml(
                    context = context,
                    styleName = styleName,
                    openEnterXml = openEnterXml,
                    openExitXml = openExitXml,
                    closeEnterXml = closeEnterXml.ifBlank { null },
                    closeExitXml = closeExitXml.ifBlank { null },
                    logCallback = AnimThemeCompiler.LogCallback(logCallback),
                    isCancelled = cancellation::get
                )
            } else {
                AnimThemeCompiler.compile(
                    context = context,
                    styleName = styleName,
                    openEnterUri = openEnterUri!!,
                    openExitUri = openExitUri!!,
                    closeEnterUri = closeEnterUri,
                    closeExitUri = closeExitUri,
                    logCallback = AnimThemeCompiler.LogCallback(logCallback),
                    isCancelled = cancellation::get
                )
            }
        }
    }

    fun activateTheme(packageName: String) {
        // Legacy key for backward compatibility
        Settings.Global.putString(context.contentResolver, SettingsKeys.ACTIVITY_TRANSITION_CUSTOM_PACKAGE, packageName)
        Settings.Global.putString(context.contentResolver, SettingsKeys.ACTIVITY_OPEN_CUSTOM_PACKAGE, packageName)
        Settings.Global.putString(context.contentResolver, SettingsKeys.ACTIVITY_CLOSE_CUSTOM_PACKAGE, packageName)
        refreshThemes()
    }

    fun activateOpenTheme(packageName: String) {
        Settings.Global.putString(context.contentResolver, SettingsKeys.ACTIVITY_OPEN_CUSTOM_PACKAGE, packageName)
        refreshThemes()
    }

    fun activateCloseTheme(packageName: String) {
        Settings.Global.putString(context.contentResolver, SettingsKeys.ACTIVITY_CLOSE_CUSTOM_PACKAGE, packageName)
        refreshThemes()
    }

    fun uninstallTheme(packageName: String) {
        scope.launch {
            val ok = withContext(Dispatchers.IO) { AnimThemeCompiler.uninstall(context, packageName) }
            if (ok) {
                // Clear all package references if this theme was active
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
                    } catch (_: Exception) {}
                }
                refreshThemes()
            }
        }
    }

    val canCompile = !isCompiling && styleName.isNotBlank() && if (useXmlInput) {
        openEnterXml.isNotBlank() && openExitXml.isNotBlank()
    } else {
        openEnterUri != null && openExitUri != null
    }

    // ── UI ──
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.anim_transition_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.nav_back)) }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent
                )
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier
                    .fillMaxSize()
                    .recordLayer(state = blurState, backgroundColor = MaterialTheme.colorScheme.surfaceContainer)
                    .background(MaterialTheme.colorScheme.surfaceContainer),
                contentPadding = PaddingValues(
                    start = 16.dp, top = 16.dp + innerPadding.calculateTopPadding(),
                    end = 16.dp,
                    bottom = REBOOT_BUBBLE_CONTENT_BOTTOM_PADDING + innerPadding.calculateBottomPadding()
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // ═══════════════════════════════════════════════════════
                // Section: Predictive Back toggle
                // ═══════════════════════════════════════════════════════
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.anim_predictive_back_title),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = stringResource(R.string.anim_predictive_back_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
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
                }

                // ═══════════════════════════════════════════════════════
                // Section: Current status
                // ═══════════════════════════════════════════════════════
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text(
                                text = String.format(
                                    stringResource(R.string.anim_current_open),
                                    modeDisplayName(context, currentOpenMode)
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = String.format(
                                    stringResource(R.string.anim_current_close),
                                    modeDisplayName(context, currentCloseMode)
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            if (currentOpenMode != MODE_DISABLED || currentCloseMode != MODE_DISABLED) {
                                Spacer(Modifier.height(8.dp))
                                Button(
                                    onClick = {
                                        applyOpen(MODE_DISABLED)
                                        applyClose(MODE_DISABLED)
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error,
                                        contentColor = MaterialTheme.colorScheme.onError
                                    ),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
                                ) {
                                    Text(stringResource(R.string.anim_builtin_disabled))
                                }
                            }
                        }
                    }
                }

                // ═══════════════════════════════════════════════════════
                // Section: Built-in Animations
                // ═══════════════════════════════════════════════════════
                item {
                    Text(
                        text = stringResource(R.string.anim_builtin_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                    )
                }

                // No-animation mode
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (currentOpenMode == MODE_NO_ANIMATION || currentCloseMode == MODE_NO_ANIMATION)
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            else MaterialTheme.colorScheme.surface
                        ),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.anim_no_animation_title),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = stringResource(R.string.anim_no_animation_desc),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 11.sp
                                    )
                                    val noAnimLabels = buildList {
                                        if (currentOpenMode == MODE_NO_ANIMATION) add(stringResource(R.string.anim_preview_open))
                                        if (currentCloseMode == MODE_NO_ANIMATION) add(stringResource(R.string.anim_preview_close))
                                    }
                                    if (noAnimLabels.isNotEmpty()) {
                                        Text(
                                            noAnimLabels.joinToString(" + "),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilledTonalButton(
                                    onClick = { applyOpen(MODE_NO_ANIMATION); applyClose(MODE_NO_ANIMATION) },
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        stringResource(R.string.anim_apply_both),
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1
                                    )
                                }
                                OutlinedButton(
                                    onClick = { applyOpen(MODE_NO_ANIMATION) },
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        stringResource(R.string.anim_apply_open),
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1
                                    )
                                }
                                OutlinedButton(
                                    onClick = { applyClose(MODE_NO_ANIMATION) },
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        stringResource(R.string.anim_apply_close),
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }

                items(BUILTIN_MODES, key = { it.modeId }) { mode ->
                    BuiltinAnimItem(
                        mode = mode,
                        isActiveOpen = mode.modeId == currentOpenMode,
                        isActiveClose = mode.modeId == currentCloseMode,
                        onApplyBoth = { applyOpen(mode.modeId); applyClose(mode.modeId) },
                        onApplyOpen = { applyOpen(mode.modeId) },
                        onApplyClose = { applyClose(mode.modeId) }
                    )
                }

                // ═══════════════════════════════════════════════════════
                // Section: Custom Themes (Addons)
                // ═══════════════════════════════════════════════════════
                item {
                    Text(
                        text = stringResource(R.string.anim_theme_installed_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                }

                if (installedThemes.isEmpty()) {
                    item {
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Text(
                                text = stringResource(R.string.anim_theme_none_installed),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
                            )
                        }
                    }
                }

                items(installedThemes, key = { it.packageName }) { theme ->
                        ThemeItem(
                            theme = theme,
                            isHighlighted = highlightedThemePackage == theme.packageName,
                            onApplyOpen = {
                            activateOpenTheme(theme.packageName)
                            applyOpen(MODE_CUSTOM)
                        },
                        onApplyClose = {
                            activateCloseTheme(theme.packageName)
                            applyClose(MODE_CUSTOM)
                        },
                        onApplyBoth = {
                            activateTheme(theme.packageName)
                            applyOpen(MODE_CUSTOM)
                            applyClose(MODE_CUSTOM)
                        },
                        onUninstall = { uninstallTheme(theme.packageName) }
                    )
                }

                // ═══════════════════════════════════════════════════════
                // Section: Create Theme
                // ═══════════════════════════════════════════════════════
                item {
                    SettingsGroupCard(title = stringResource(R.string.anim_theme_create_title)) {
                        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                            // Style name
                            OutlinedTextField(
                                value = styleName,
                                onValueChange = { styleName = filterThemeNameInput(it) },
                                label = { Text(stringResource(R.string.anim_theme_style_name)) },
                                placeholder = { Text("e.g. bouncy slide") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Ascii,
                                    imeAction = ImeAction.Done
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(Modifier.height(12.dp))

                            // Input mode toggle
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                FilterChip(
                                    selected = !useXmlInput,
                                    onClick = { useXmlInput = false },
                                    label = { Text(stringResource(R.string.anim_theme_input_mode_file)) }
                                )
                                Spacer(Modifier.width(8.dp))
                                FilterChip(
                                    selected = useXmlInput,
                                    onClick = { useXmlInput = true },
                                    label = { Text(stringResource(R.string.anim_theme_input_mode_xml)) }
                                )
                            }

                            Spacer(Modifier.height(8.dp))

                            if (useXmlInput) {
                                // XML text inputs
                                XmlInputField(
                                    label = stringResource(R.string.anim_theme_open_enter),
                                    value = openEnterXml,
                                    onValueChange = { openEnterXml = it },
                                    required = true
                                )
                                XmlInputField(
                                    label = stringResource(R.string.anim_theme_open_exit),
                                    value = openExitXml,
                                    onValueChange = { openExitXml = it },
                                    required = true
                                )
                                XmlInputField(
                                    label = stringResource(R.string.anim_theme_close_enter),
                                    value = closeEnterXml,
                                    onValueChange = { closeEnterXml = it },
                                    required = false
                                )
                                XmlInputField(
                                    label = stringResource(R.string.anim_theme_close_exit),
                                    value = closeExitXml,
                                    onValueChange = { closeExitXml = it },
                                    required = false
                                )
                            } else {
                                // File pickers
                                FilePickerRow(
                                    label = stringResource(R.string.anim_theme_open_enter),
                                    uri = openEnterUri, required = true,
                                    onClick = { openEnterPicker.launch("text/xml") }
                                )
                                FilePickerRow(
                                    label = stringResource(R.string.anim_theme_open_exit),
                                    uri = openExitUri, required = true,
                                    onClick = { openExitPicker.launch("text/xml") }
                                )
                                FilePickerRow(
                                    label = stringResource(R.string.anim_theme_close_enter),
                                    uri = closeEnterUri, required = false,
                                    onClick = { closeEnterPicker.launch("text/xml") }
                                )
                                FilePickerRow(
                                    label = stringResource(R.string.anim_theme_close_exit),
                                    uri = closeExitUri, required = false,
                                    onClick = { closeExitPicker.launch("text/xml") }
                                )
                            }

                            Spacer(Modifier.height(12.dp))

                            // Compile button
                            Button(
                                onClick = { doCompile() },
                                enabled = canCompile,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (isCompiling) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                    Spacer(Modifier.width(8.dp))
                                }
                                Icon(Icons.Rounded.Build, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.anim_theme_compile_btn))
                            }

                            if (compileError != null) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = compileError!!,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }

                // ── Compile Log ──
                if (compileLog.isNotEmpty()) {
                    item {
                        SettingsGroupCard(title = stringResource(R.string.anim_theme_log_title)) {
                            Column(
                                modifier = Modifier
                                    .padding(horizontal = 20.dp, vertical = 8.dp)
                                    .fillMaxWidth()
                            ) {
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .padding(12.dp)
                                            .heightIn(max = 200.dp)
                                    ) {
                                        compileLog.forEach { line ->
                                            Text(
                                                text = line,
                                                style = MaterialTheme.typography.bodySmall,
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 11.sp, lineHeight = 15.sp,
                                                color = when {
                                                    line.startsWith("ERROR") || line.startsWith("✗") ->
                                                        MaterialTheme.colorScheme.error
                                                    line.startsWith("✓") ->
                                                        MaterialTheme.colorScheme.primary
                                                    else ->
                                                        MaterialTheme.colorScheme.onSurface
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // ═══════════════════════════════════════════════════════
                // Section: Animation Constructor
                // ═══════════════════════════════════════════════════════
                item {
                    Text(
                        text = stringResource(R.string.anim_constructor_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                }

                item {
                    AnimConstructorBlock(
                        selectedTab = constructorTab,
                        onTabSelected = { constructorTab = it },
                        openEnter = cOpenEnter,
                        openExit = cOpenExit,
                        closeEnter = cCloseEnter,
                        closeExit = cCloseExit,
                        onOpenEnterChange = { cOpenEnter = it; constructorPreviewKey++ },
                        onOpenExitChange = { cOpenExit = it; constructorPreviewKey++ },
                        onCloseEnterChange = { cCloseEnter = it; constructorPreviewKey++ },
                        onCloseExitChange = { cCloseExit = it; constructorPreviewKey++ }
                    )
                }

                item {
                    ConstructorPreview(
                        openEnter = cOpenEnter,
                        openExit = cOpenExit,
                        closeEnter = cCloseEnter,
                        closeExit = cCloseExit,
                        previewKey = constructorPreviewKey
                    )
                }

                item {
                    ConstructorExportButton(
                        openEnter = cOpenEnter,
                        openExit = cOpenExit,
                        closeEnter = cCloseEnter,
                        closeExit = cCloseExit,
                        onBuildTheme = { name ->
                            styleName = name
                            useXmlInput = true
                            openEnterXml = animParamsToXml(cOpenEnter)
                            openExitXml = animParamsToXml(cOpenExit)
                            closeEnterXml = animParamsToXml(cCloseEnter)
                            closeExitXml = animParamsToXml(cCloseExit)
                            doCompile()
                        }
                    )
                }
            }

            TopBarBlurOverlay(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter),
                topBarHeight = innerPadding.calculateTopPadding(),
                blurState = blurState
            )
            TopStatusVeil(
                backgroundColor = MaterialTheme.colorScheme.surfaceContainer,
                veilHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 14.dp,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    compileDialogState?.let { state ->
        AnimationCompileDialog(
            state = state,
            onCancel = ::cancelCompile,
            onDismiss = ::dismissCompileDialog
        )
    }
}

// ═══════════════════════════════════════════════════════════════════
// Components
// ═══════════════════════════════════════════════════════════════════

// ── Built-in Animation Item ────────────────────────────────────────
