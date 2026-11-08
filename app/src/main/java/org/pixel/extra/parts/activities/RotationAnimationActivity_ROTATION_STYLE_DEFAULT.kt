package org.pixel.extra.parts.activities

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
import org.pixel.extra.parts.R
import org.pixel.extra.parts.dynamicDarkColorScheme
import org.pixel.extra.parts.dynamicLightColorScheme
import org.pixel.extra.parts.ui.legacy.REBOOT_BUBBLE_CONTENT_BOTTOM_PADDING
import org.pixel.extra.parts.ui.legacy.SettingsGroupCard
import org.pixel.extra.parts.ui.TopBarBlurOverlay
import org.pixel.extra.parts.ui.recordLayer
import org.pixel.extra.parts.ui.rememberGraphicsLayerRecordingState
import org.pixel.extra.parts.utils.AnimThemeCompiler
import org.pixel.extra.parts.utils.filterThemeNameInput
import java.util.concurrent.atomic.AtomicBoolean
import androidx.compose.ui.res.stringResource


internal const val ROTATION_STYLE_DEFAULT = 0
internal const val ROTATION_STYLE_CUSTOM_APK = -1
internal const val ROTATION_STYLE_WINDOWS_PHONE = 1
internal const val ROTATION_STYLE_WIN10_11 = 2
internal const val ROTATION_STYLE_CUBE = 3
internal const val ROTATION_STYLE_ZOOM = 4
internal const val ROTATION_STYLE_SLIDE = 5
internal const val ROTATION_STYLE_FLIP = 6
internal const val ROTATION_STYLE_FADE = 7
internal const val ROTATION_STYLE_BOUNCE = 8

internal const val KEY_ROTATION_STYLE = "rotation_animation_style"
internal const val KEY_ROTATION_CUSTOM_PACKAGE = "rotation_animation_custom_package"
internal const val ROTATION_THEME_PACKAGE_PREFIX = "org.pixel.extra.parts.rotation."

internal data class RotationStyle(
    val styleId: Int,
    val nameRes: Int,
    val exitAnimRes: Int,
    val enterAnimRes: Int,
    val alphaAnimRes: Int
)

internal val BUILTIN_STYLES: List<RotationStyle> by lazy {
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


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RotationAnimationScreen(onBack: () -> Unit, onNavigate: (String) -> Unit = {}) {
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
                title = { Text(stringResource(R.string.rotation_anim_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.nav_back))
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
                    .recordLayer(state = blurState, backgroundColor = MaterialTheme.colorScheme.surfaceContainer)
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
                                    stringResource(R.string.rotation_anim_current),
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
                                    Text(stringResource(R.string.rotation_anim_reset))
                                }
                            }
                        }
                    }
                }

                item {
                    Text(
                        stringResource(R.string.rotation_anim_builtin_title),
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
                        stringResource(R.string.rotation_anim_custom_title),
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
                                stringResource(R.string.rotation_anim_no_themes),
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
                    SettingsGroupCard(title = stringResource(R.string.rotation_anim_import_title)) {
                        Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                            OutlinedTextField(
                                value = styleName,
                                onValueChange = { styleName = filterThemeNameInput(it) },
                                label = { Text(stringResource(R.string.rotation_anim_style_name)) },
                                placeholder = { Text(stringResource(R.string.rotation_example_hint)) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Ascii,
                                    imeAction = ImeAction.Done
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(12.dp))
                            RotationFilePickerRow(
                                label = stringResource(R.string.rotation_anim_exit_file),
                                uri = exitUri,
                                required = true,
                                onClick = { exitPicker.launch("text/xml") }
                            )
                            RotationFilePickerRow(
                                label = stringResource(R.string.rotation_anim_enter_file),
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
                                Text(stringResource(R.string.rotation_anim_compile_btn))
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
                        SettingsGroupCard(title = stringResource(R.string.rotation_anim_log_title)) {
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
                        stringResource(R.string.rotation_anim_constructor_title),
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
                blurRadius = org.pixel.extra.parts.ui.legacy.LegacyBlurRadius
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
internal fun RotationStyleItem(
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
                        stringResource(style.nameRes),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (isActive) {
                        Text(
                            stringResource(R.string.rotation_anim_active),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        stringResource(R.string.rotation_anim_preview)
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
                            stringResource(
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
internal fun CustomThemeItem(
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
                                stringResource(R.string.rotation_anim_active),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    IconButton(onClick = { expanded = !expanded }) {
                        Icon(
                            if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                            stringResource(R.string.rotation_anim_preview)
                        )
                    }
                    if (!showConfirmDelete) {
                        IconButton(onClick = { showConfirmDelete = true }) {
                            Icon(
                                Icons.Rounded.Delete,
                                stringResource(R.string.rotation_anim_delete),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    } else {
                        IconButton(onClick = { showConfirmDelete = false; onUninstall() }) {
                            Icon(
                                Icons.Rounded.Check,
                                stringResource(R.string.rotation_anim_delete_confirm),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                        IconButton(onClick = { showConfirmDelete = false }) {
                            Icon(Icons.Rounded.Close, stringResource(R.string.rotation_anim_cancel))
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
                                stringResource(
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
internal fun RotationFilePickerRow(
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
                uri?.lastPathSegment ?: stringResource(R.string.rotation_anim_no_file),
                style = MaterialTheme.typography.bodySmall,
                color = if (uri != null) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

internal val ROTATION_CONSTRUCTOR_TAB_LABELS = listOf(
    R.string.rotation_anim_constructor_exit,
    R.string.rotation_anim_constructor_enter
)