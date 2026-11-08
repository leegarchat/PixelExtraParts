package org.pixel.customparts.activities

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.pixel.customparts.R
import org.pixel.customparts.dynamicDarkColorScheme
import org.pixel.customparts.dynamicLightColorScheme
import org.pixel.customparts.ui.REBOOT_BUBBLE_CONTENT_BOTTOM_PADDING
import org.pixel.customparts.ui.SettingsGroupCard
import org.pixel.customparts.ui.TopBarBlurOverlay
import org.pixel.customparts.ui.recordLayer
import org.pixel.customparts.ui.rememberGraphicsLayerRecordingState
import org.pixel.customparts.utils.AnimThemeCompiler
import org.pixel.customparts.utils.dynamicStringResource
import org.pixel.customparts.utils.filterThemeNameInput
import java.util.concurrent.atomic.AtomicBoolean

private const val ROTATION_STYLE_DEFAULT = 0
private const val ROTATION_STYLE_CUSTOM_APK = -1
private const val ROTATION_STYLE_WINDOWS_PHONE = 1
private const val ROTATION_STYLE_WIN10_11 = 2
private const val ROTATION_STYLE_CUBE = 3
private const val ROTATION_STYLE_ZOOM = 4
private const val ROTATION_STYLE_SLIDE = 5
private const val ROTATION_STYLE_FLIP = 6
private const val ROTATION_STYLE_FADE = 7
private const val ROTATION_STYLE_BOUNCE = 8

private const val KEY_ROTATION_STYLE = "rotation_animation_style"
private const val KEY_ROTATION_CUSTOM_PACKAGE = "rotation_animation_custom_package"
private const val ROTATION_THEME_PACKAGE_PREFIX = "org.pixel.customparts.rotation."

private data class RotationStyle(
    val styleId: Int,
    val nameRes: Int,
    val exitAnimRes: Int,
    val enterAnimRes: Int,
    val alphaAnimRes: Int
)

private val BUILTIN_STYLES: List<RotationStyle> by lazy {
    listOf(
        RotationStyle(ROTATION_STYLE_WINDOWS_PHONE, R.string.rotation_style_windows_phone,
            R.anim.wp_exit, R.anim.wp_enter, R.anim.wp_alpha),
        RotationStyle(ROTATION_STYLE_WIN10_11, R.string.rotation_style_win10_11,
            R.anim.win10_11_exit, R.anim.win10_11_enter, R.anim.win10_11_alpha),
        RotationStyle(ROTATION_STYLE_CUBE, R.string.rotation_style_cube,
            R.anim.cube_exit, R.anim.cube_enter, R.anim.cube_alpha),
        RotationStyle(ROTATION_STYLE_ZOOM, R.string.rotation_style_zoom,
            R.anim.zoom_rotate_exit, R.anim.zoom_rotate_enter, R.anim.zoom_rotate_alpha),
        RotationStyle(ROTATION_STYLE_SLIDE, R.string.rotation_style_slide,
            R.anim.slide_rotate_exit, R.anim.slide_rotate_enter, R.anim.slide_rotate_alpha),
        RotationStyle(ROTATION_STYLE_FLIP, R.string.rotation_style_flip,
            R.anim.flip_exit, R.anim.flip_enter, R.anim.flip_alpha),
        RotationStyle(ROTATION_STYLE_FADE, R.string.rotation_style_fade,
            R.anim.fade_rotate_exit, R.anim.fade_rotate_enter, R.anim.fade_rotate_alpha),
        RotationStyle(ROTATION_STYLE_BOUNCE, R.string.rotation_style_bounce,
            R.anim.bounce_exit, R.anim.bounce_enter, R.anim.bounce_alpha)
    )
}

class RotationAnimationActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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
    val blurState = rememberGraphicsLayerRecordingState()
    val lazyListState = rememberLazyListState()
    val isScrolled by remember { derivedStateOf { lazyListState.canScrollBackward } }

    var currentStyle by remember {
        mutableIntStateOf(try {
            Settings.Global.getInt(context.contentResolver, KEY_ROTATION_STYLE)
        } catch (_: Exception) { ROTATION_STYLE_DEFAULT })
    }
    var customPackage by remember {
        mutableStateOf(try {
            Settings.Global.getString(context.contentResolver, KEY_ROTATION_CUSTOM_PACKAGE) ?: ""
        } catch (_: Exception) { "" })
    }
    var installedThemes by remember { mutableStateOf<List<String>>(emptyList()) }

    fun refreshThemes() {
        installedThemes = try {
            context.packageManager.getInstalledPackages(0)
                .filter { it.packageName.startsWith(ROTATION_THEME_PACKAGE_PREFIX) }
                .map { it.packageName }
                .sorted()
        } catch (_: Exception) { emptyList() }
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

    LaunchedEffect(Unit) { refreshThemes() }

    var styleName by remember { mutableStateOf("") }
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
    var highlightedThemePackage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(highlightedThemePackage) {
        if (highlightedThemePackage != null) {
            delay(3500)
            highlightedThemePackage = null
        }
    }

    var constructorTab by remember { mutableIntStateOf(0) }
    var constructorExit by remember {
        mutableStateOf(AnimParams(
            alphaFrom = 1f, alphaTo = 0f, rotateFrom = 0f, rotateTo = -90f, duration = 320
        ))
    }
    var constructorEnter by remember {
        mutableStateOf(AnimParams(
            alphaFrom = 0f, alphaTo = 1f, rotateFrom = 90f, rotateTo = 0f,
            duration = 320, startOffset = 80
        ))
    }
    var constructorPreviewKey by remember { mutableIntStateOf(0) }

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
                        compileLog = compileLog + "Installed successfully"
                        refreshThemes()
                        compiledPackageName = result.packageName
                        compileDialogState = AnimationCompileState(
                            AnimationCompilePhase.SUCCESS, 1f, result.packageName
                        )
                    } else {
                        compileError = context.getString(R.string.anim_install_failed)
                        compileLog = compileLog + context.getString(R.string.anim_install_failed_log)
                        compileDialogState = AnimationCompileState(
                            AnimationCompilePhase.ERROR, 0.98f, compileError.orEmpty()
                        )
                    }
                } else {
                    compileError = result.error ?: context.getString(R.string.anim_compile_failed)
                    compileDialogState = AnimationCompileState(
                        AnimationCompilePhase.ERROR, 0f, compileError.orEmpty()
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
                    AnimationCompilePhase.ERROR, 0f, compileError.orEmpty()
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
                val index = installedThemes.indexOf(packageName)
                if (index >= 0) {
                    lazyListState.animateScrollToItem(3 + BUILTIN_STYLES.size + index)
                }
            }
        }
    }

    fun doCompile() {
        if (styleName.isBlank() || exitUri == null || enterUri == null) return
        startCompile { cancellation, logCallback ->
            AnimThemeCompiler.compileRotation(
                context = context,
                styleName = styleName,
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

    val canCompile = !isCompiling && styleName.isNotBlank() && exitUri != null && enterUri != null

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            TopAppBar(
                title = { Text(dynamicStringResource(R.string.rotation_anim_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, dynamicStringResource(R.string.nav_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent
                )
            )
        }
    ) { innerPadding ->
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier
                    .fillMaxSize()
                    .recordLayer(blurState)
                    .background(MaterialTheme.colorScheme.surfaceContainer),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    top = 16.dp + innerPadding.calculateTopPadding(),
                    end = 16.dp,
                    bottom = REBOOT_BUBBLE_CONTENT_BOTTOM_PADDING + innerPadding.calculateBottomPadding()
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(20.dp)) {
                            Text(
                                String.format(
                                    dynamicStringResource(R.string.rotation_anim_current),
                                    styleDisplayName(context, currentStyle)
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            if (currentStyle != ROTATION_STYLE_DEFAULT) {
                                Spacer(Modifier.height(8.dp))
                                Button(
                                    onClick = { applyStyle(ROTATION_STYLE_DEFAULT) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error,
                                        contentColor = MaterialTheme.colorScheme.onError
                                    ),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
                                ) {
                                    Text(dynamicStringResource(R.string.rotation_anim_reset))
                                }
                            }
                        }
                    }
                }

                item {
                    Text(
                        dynamicStringResource(R.string.rotation_anim_builtin_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                    )
                }

                items(BUILTIN_STYLES, key = { it.styleId }) { style ->
                    RotationStyleItem(
                        style = style,
                        isActive = style.styleId == currentStyle,
                        onApply = { applyStyle(style.styleId) }
                    )
                }

                item {
                    Text(
                        dynamicStringResource(R.string.rotation_anim_custom_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                }

                if (installedThemes.isEmpty()) {
                    item {
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Text(
                                dynamicStringResource(R.string.rotation_anim_no_themes),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
                            )
                        }
                    }
                }

                items(installedThemes, key = { it }) { packageName ->
                    CustomThemeItem(
                        packageName = packageName,
                        isHighlighted = highlightedThemePackage == packageName,
                        isActive = currentStyle == ROTATION_STYLE_CUSTOM_APK && packageName == customPackage,
                        onApply = { applyCustomTheme(packageName) },
                        onUninstall = {
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    AnimThemeCompiler.uninstall(context, packageName)
                                }
                                refreshThemes()
                            }
                        }
                    )
                }

                item {
                    SettingsGroupCard(title = dynamicStringResource(R.string.rotation_anim_import_title)) {
                        Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                            OutlinedTextField(
                                value = styleName,
                                onValueChange = { styleName = filterThemeNameInput(it) },
                                label = { Text(dynamicStringResource(R.string.rotation_anim_style_name)) },
                                placeholder = { Text("e.g. windows phone") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Ascii,
                                    imeAction = ImeAction.Done
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(12.dp))
                            FilePickerRow(
                                label = dynamicStringResource(R.string.rotation_anim_exit_file),
                                uri = exitUri,
                                required = true,
                                onClick = { exitPicker.launch("text/xml") }
                            )
                            FilePickerRow(
                                label = dynamicStringResource(R.string.rotation_anim_enter_file),
                                uri = enterUri,
                                required = true,
                                onClick = { enterPicker.launch("text/xml") }
                            )
                            Spacer(Modifier.height(12.dp))
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
                                Text(dynamicStringResource(R.string.rotation_anim_compile_btn))
                            }
                            if (compileError != null) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    compileError!!,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }

                if (compileLog.isNotEmpty()) {
                    item {
                        SettingsGroupCard(title = dynamicStringResource(R.string.rotation_anim_log_title)) {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 8.dp)
                            ) {
                                Column(Modifier.padding(12.dp).heightIn(max = 200.dp)) {
                                    compileLog.forEach { line ->
                                        Text(
                                            line,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp,
                                            lineHeight = 15.sp,
                                            color = when {
                                                line.startsWith("ERROR") || line.startsWith("✗") ->
                                                    MaterialTheme.colorScheme.error
                                                line.startsWith("✓") -> MaterialTheme.colorScheme.primary
                                                else -> MaterialTheme.colorScheme.onSurface
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Text(
                        dynamicStringResource(R.string.rotation_anim_constructor_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                }
                item {
                    RotationConstructorBlock(
                        selectedTab = constructorTab,
                        onTabSelected = { constructorTab = it },
                        exit = constructorExit,
                        enter = constructorEnter,
                        onExitChange = { constructorExit = it; constructorPreviewKey++ },
                        onEnterChange = { constructorEnter = it; constructorPreviewKey++ }
                    )
                }
                item {
                    RotationConstructorPreview(
                        exit = constructorExit,
                        enter = constructorEnter,
                        previewKey = constructorPreviewKey
                    )
                }
                item {
                    RotationConstructorExportButton(onBuildTheme = ::doCompileConstructor)
                }
            }

            TopBarBlurOverlay(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter),
                blurState = blurState,
                topBarHeight = innerPadding.calculateTopPadding(),
                isScrolled = isScrolled
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

@Composable
private fun RotationStyleItem(
    style: RotationStyle,
    isActive: Boolean,
    onApply: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) MaterialTheme.colorScheme.surfaceContainerHigh
            else MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        dynamicStringResource(style.nameRes),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (isActive) {
                        Text(
                            dynamicStringResource(R.string.rotation_anim_active),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        dynamicStringResource(R.string.rotation_anim_preview)
                    )
                }
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 20.dp, bottom = 16.dp)
                ) {
                    HorizontalDivider(Modifier.padding(bottom = 12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        RotationAnimPreview(style.exitAnimRes, style.enterAnimRes)
                    }
                    Spacer(Modifier.height(12.dp))
                    FilledTonalButton(
                        onClick = onApply,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isActive
                    ) {
                        if (isActive) {
                            Icon(Icons.Rounded.Check, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(
                            dynamicStringResource(
                                if (isActive) R.string.rotation_anim_active
                                else R.string.rotation_anim_apply
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomThemeItem(
    packageName: String,
    isHighlighted: Boolean,
    isActive: Boolean,
    onApply: () -> Unit,
    onUninstall: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var showConfirmDelete by remember { mutableStateOf(false) }
    val styleName = packageName.removePrefix(ROTATION_THEME_PACKAGE_PREFIX)
    val pulse = rememberInfiniteTransition(label = "rotation_theme_highlight")
    val pulseAlpha by pulse.animateFloat(
        initialValue = 0f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(300),
            repeatMode = RepeatMode.Reverse
        ),
        label = "rotation_theme_highlight_alpha"
    )

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) MaterialTheme.colorScheme.surfaceContainerHigh
            else MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
        border = if (isHighlighted) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.65f))
        } else null
    ) {
        Box {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expanded = !expanded }
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(styleName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(
                            packageName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp
                        )
                        if (isActive) {
                            Text(
                                dynamicStringResource(R.string.rotation_anim_active),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    IconButton(onClick = { expanded = !expanded }) {
                        Icon(
                            if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                            dynamicStringResource(R.string.rotation_anim_preview)
                        )
                    }
                    if (!showConfirmDelete) {
                        IconButton(onClick = { showConfirmDelete = true }) {
                            Icon(
                                Icons.Rounded.Delete,
                                dynamicStringResource(R.string.rotation_anim_delete),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    } else {
                        IconButton(onClick = { showConfirmDelete = false; onUninstall() }) {
                            Icon(
                                Icons.Rounded.Check,
                                dynamicStringResource(R.string.rotation_anim_delete_confirm),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                        IconButton(onClick = { showConfirmDelete = false }) {
                            Icon(Icons.Rounded.Close, dynamicStringResource(R.string.rotation_anim_cancel))
                        }
                    }
                }
                AnimatedVisibility(
                    visible = expanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 20.dp, end = 20.dp, bottom = 16.dp)
                    ) {
                        HorizontalDivider(Modifier.padding(bottom = 12.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                            RotationCustomAnimPreview(packageName)
                        }
                        Spacer(Modifier.height(12.dp))
                        FilledTonalButton(
                            onClick = onApply,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isActive
                        ) {
                            if (isActive) {
                                Icon(Icons.Rounded.Check, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(
                                dynamicStringResource(
                                    if (isActive) R.string.rotation_anim_active
                                    else R.string.rotation_anim_apply
                                )
                            )
                        }
                    }
                }
            }
            if (isHighlighted) {
                Box(
                    Modifier
                        .matchParentSize()
                        .background(Color.White.copy(alpha = pulseAlpha))
                )
            }
        }
    }
}

@Composable
private fun FilePickerRow(
    label: String,
    uri: Uri?,
    required: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.FileOpen, null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                label + if (required) " *" else "",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                uri?.lastPathSegment ?: dynamicStringResource(R.string.rotation_anim_no_file),
                style = MaterialTheme.typography.bodySmall,
                color = if (uri != null) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private val ROTATION_CONSTRUCTOR_TAB_LABELS = listOf(
    R.string.rotation_anim_constructor_exit,
    R.string.rotation_anim_constructor_enter
)

@Composable
private fun RotationConstructorBlock(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    exit: AnimParams,
    enter: AnimParams,
    onExitChange: (AnimParams) -> Unit,
    onEnterChange: (AnimParams) -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp)) {
            TabRow(selectedTabIndex = selectedTab, containerColor = Color.Transparent) {
                ROTATION_CONSTRUCTOR_TAB_LABELS.forEachIndexed { index, labelRes ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { onTabSelected(index) },
                        text = { Text(dynamicStringResource(labelRes)) }
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            if (selectedTab == 0) {
                ParamEditor(params = exit, onParamsChange = onExitChange)
            } else {
                ParamEditor(params = enter, onParamsChange = onEnterChange)
            }
        }
    }
}

@Composable
private fun RotationConstructorPreview(
    exit: AnimParams,
    enter: AnimParams,
    previewKey: Int
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                dynamicStringResource(R.string.rotation_anim_constructor_preview),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(12.dp))
            Card(
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                modifier = Modifier.size(width = 150.dp, height = 250.dp)
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(12.dp))
                ) {
                    key(previewKey) {
                        AndroidView(
                            factory = { ctx -> createConstructorPreview(ctx, enter, exit, true) },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RotationConstructorExportButton(onBuildTheme: (String) -> Unit) {
    var showXml by remember { mutableStateOf(false) }
    var showBuildInput by remember { mutableStateOf(false) }
    var buildThemeName by remember { mutableStateOf("") }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp)) {
            AnimatedVisibility(
                visible = showBuildInput,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(Modifier.padding(bottom = 8.dp)) {
                    OutlinedTextField(
                        value = buildThemeName,
                        onValueChange = { buildThemeName = filterThemeNameInput(it) },
                        label = { Text(dynamicStringResource(R.string.rotation_anim_constructor_style_name)) },
                        placeholder = { Text(dynamicStringResource(R.string.rotation_anim_constructor_style_hint)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Ascii,
                            imeAction = ImeAction.Done
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            if (buildThemeName.isNotBlank()) {
                                onBuildTheme(buildThemeName)
                                showBuildInput = false
                            }
                        },
                        enabled = buildThemeName.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.Build, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(dynamicStringResource(R.string.rotation_anim_constructor_build))
                    }
                }
            }
            FilledTonalButton(
                onClick = { showBuildInput = !showBuildInput },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Rounded.Build, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    if (showBuildInput) dynamicStringResource(R.string.rotation_anim_constructor_hide)
                    else dynamicStringResource(R.string.rotation_anim_constructor_build)
                )
            }
            Spacer(Modifier.height(8.dp))
            FilledTonalButton(
                onClick = { showXml = !showXml },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Rounded.Code, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    if (showXml) dynamicStringResource(R.string.rotation_anim_constructor_hide_xml)
                    else dynamicStringResource(R.string.rotation_anim_constructor_show_xml)
                )
            }
            if (showXml) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "Exit XML is generated from the selected parameters. Enter XML is generated separately.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private val rotationPreviewHandler = Handler(Looper.getMainLooper())
private const val ROTATION_PREVIEW_START_DELAY = 450L
private const val ROTATION_PREVIEW_HOLD_DELAY = 700L

private data class RotationPreviewViews(
    val frame: FrameLayout,
    val purpleScreen: View,
    val orangeScreen: View
)

@Composable
private fun RotationAnimPreview(exitAnimRes: Int, enterAnimRes: Int) {
    RotationPreviewCard { ctx -> createRotationPreview(ctx, exitAnimRes, enterAnimRes) }
}

@Composable
private fun RotationCustomAnimPreview(packageName: String) {
    RotationPreviewCard { ctx -> createRotationThemePreview(ctx, packageName) }
}

@Composable
private fun RotationPreviewCard(factory: (Context) -> FrameLayout) {
    Card(
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        modifier = Modifier.size(width = 110.dp, height = 190.dp)
    ) {
        Box(Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp))) {
            AndroidView(factory = factory, modifier = Modifier.fillMaxSize())
        }
    }
}

private fun createRotationPreview(
    viewCtx: Context,
    exitAnimRes: Int,
    enterAnimRes: Int
): FrameLayout {
    val preview = createRotationPreviewViews(viewCtx)
    preview.frame.post {
        loopRotationAnim(viewCtx, preview, exitAnimRes, enterAnimRes, showPurple = true)
    }
    return preview.frame
}

private fun createRotationThemePreview(viewCtx: Context, packageName: String): FrameLayout {
    val preview = createRotationPreviewViews(viewCtx)
    preview.frame.post {
        try {
            val themeContext = viewCtx.createPackageContext(
                packageName,
                Context.CONTEXT_IGNORE_SECURITY
            )
            val exitAnimRes = themeContext.resources.getIdentifier(
                "custom_rotate_exit", "anim", packageName
            )
            val enterAnimRes = themeContext.resources.getIdentifier(
                "custom_rotate_enter", "anim", packageName
            )
            loopRotationAnim(themeContext, preview, exitAnimRes, enterAnimRes, showPurple = true)
        } catch (_: Exception) {
            preview.purpleScreen.visibility = View.INVISIBLE
            preview.orangeScreen.visibility = View.VISIBLE
        }
    }
    return preview.frame
}

private fun createRotationPreviewViews(viewCtx: Context): RotationPreviewViews {
    val frame = FrameLayout(viewCtx).apply {
        clipChildren = true
        clipToPadding = true
    }
    val purpleScreen = TextView(viewCtx).apply {
        text = "A"
        textSize = 32f
        gravity = Gravity.CENTER
        setBackgroundColor(0xFF6750A4.toInt())
        setTextColor(0xFFFFFFFF.toInt())
    }
    val orangeScreen = TextView(viewCtx).apply {
        text = "B"
        textSize = 32f
        gravity = Gravity.CENTER
        setBackgroundColor(0xFFE8874F.toInt())
        setTextColor(0xFFFFFFFF.toInt())
        visibility = View.INVISIBLE
    }
    frame.addView(purpleScreen, FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.MATCH_PARENT,
        FrameLayout.LayoutParams.MATCH_PARENT
    ))
    frame.addView(orangeScreen, FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.MATCH_PARENT,
        FrameLayout.LayoutParams.MATCH_PARENT
    ))
    return RotationPreviewViews(frame, purpleScreen, orangeScreen)
}

private fun loopRotationAnim(
    ctx: Context,
    preview: RotationPreviewViews,
    exitAnimRes: Int,
    enterAnimRes: Int,
    showPurple: Boolean
) {
    val frame = preview.frame
    if (!frame.isAttachedToWindow) return
    val outgoing = if (showPurple) preview.purpleScreen else preview.orangeScreen
    val incoming = if (showPurple) preview.orangeScreen else preview.purpleScreen
    resetRotationPreviewView(outgoing)
    resetRotationPreviewView(incoming)
    outgoing.visibility = View.VISIBLE
    incoming.visibility = View.INVISIBLE

    rotationPreviewHandler.postDelayed({
        if (!frame.isAttachedToWindow) return@postDelayed
        var duration = 350L
        incoming.visibility = View.VISIBLE
        duration = maxOf(duration, playRotationPreviewAnimation(ctx, outgoing, exitAnimRes))
        duration = maxOf(duration, playRotationPreviewAnimation(ctx, incoming, enterAnimRes))
        rotationPreviewHandler.postDelayed({
            if (!frame.isAttachedToWindow) return@postDelayed
            resetRotationPreviewView(outgoing)
            outgoing.visibility = View.INVISIBLE
            resetRotationPreviewView(incoming)
            incoming.visibility = View.VISIBLE
            rotationPreviewHandler.postDelayed({
                loopRotationAnim(ctx, preview, exitAnimRes, enterAnimRes, !showPurple)
            }, ROTATION_PREVIEW_HOLD_DELAY)
        }, duration + 120L)
    }, ROTATION_PREVIEW_START_DELAY)
}

private fun playRotationPreviewAnimation(ctx: Context, view: View, animationRes: Int): Long {
    if (animationRes == 0) return 350L
    return try {
        val animation = AnimationUtils.loadAnimation(ctx, animationRes).apply { fillAfter = true }
        view.startAnimation(animation)
        animation.duration + animation.startOffset
    } catch (_: Exception) { 350L }
}

private fun resetRotationPreviewView(view: View) {
    view.clearAnimation()
    view.alpha = 1f
    view.scaleX = 1f
    view.scaleY = 1f
    view.translationX = 0f
    view.translationY = 0f
    view.rotation = 0f
}

private fun styleDisplayName(context: Context, styleId: Int): String {
    if (styleId == ROTATION_STYLE_DEFAULT) return context.getString(R.string.rotation_anim_default)
    if (styleId == ROTATION_STYLE_CUSTOM_APK) return context.getString(R.string.rotation_anim_custom)
    val style = BUILTIN_STYLES.find { it.styleId == styleId }
    return if (style != null) context.getString(style.nameRes) else "Style $styleId"
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
        animationSpec = tween(250),
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
                        (fadeIn(tween(220)) + scaleIn(animationSpec = tween(220))) togetherWith
                            fadeOut(tween(140))
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
                                progress = animatedProgress,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
                Spacer(Modifier.size(if (terminal) 16.dp else 8.dp))
                Text(
                    text = when (state.phase) {
                        AnimationCompilePhase.COMPILING -> dynamicStringResource(labels.compiling)
                        AnimationCompilePhase.INSTALLING -> dynamicStringResource(labels.installing)
                        AnimationCompilePhase.SUCCESS -> dynamicStringResource(labels.success)
                        AnimationCompilePhase.ERROR -> dynamicStringResource(labels.error)
                        AnimationCompilePhase.CANCELLED -> dynamicStringResource(labels.cancelled)
                    },
                    style = MaterialTheme.typography.titleMedium
                )
                if (!terminal) {
                    Spacer(Modifier.size(8.dp))
                    Text(
                        dynamicStringResource(labels.progress, (animatedProgress * 100).toInt()),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (state.detail.isNotBlank()) {
                    Spacer(Modifier.size(8.dp))
                    Text(
                        state.detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2
                    )
                }
                Spacer(Modifier.size(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    if (!terminal) {
                        TextButton(onClick = onCancel) {
                            Text(dynamicStringResource(labels.cancel))
                        }
                    } else {
                        Button(onClick = onDismiss) {
                            Text(dynamicStringResource(labels.ok))
                        }
                    }
                }
            }
        }
    }
}
