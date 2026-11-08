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


@Composable
internal fun RotationConstructorBlock(
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
                        text = { Text(stringResource(labelRes)) }
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
internal fun RotationConstructorPreview(
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
                stringResource(R.string.rotation_anim_constructor_preview),
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
internal fun RotationConstructorExportButton(onBuildTheme: (String) -> Unit) {
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
                        label = { Text(stringResource(R.string.rotation_anim_constructor_style_name)) },
                        placeholder = { Text(stringResource(R.string.rotation_anim_constructor_style_hint)) },
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
                        Text(stringResource(R.string.rotation_anim_constructor_build))
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
                    if (showBuildInput) stringResource(R.string.rotation_anim_constructor_hide)
                    else stringResource(R.string.rotation_anim_constructor_build)
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
                    if (showXml) stringResource(R.string.rotation_anim_constructor_hide_xml)
                    else stringResource(R.string.rotation_anim_constructor_show_xml)
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

internal val rotationPreviewHandler = Handler(Looper.getMainLooper())
internal const val ROTATION_PREVIEW_START_DELAY = 450L
internal const val ROTATION_PREVIEW_HOLD_DELAY = 700L

internal data class RotationPreviewViews(
    val frame: FrameLayout,
    val purpleScreen: View,
    val orangeScreen: View
)

@Composable
internal fun RotationAnimPreview(exitAnimRes: Int, enterAnimRes: Int) {
    RotationPreviewCard { ctx -> createRotationPreview(ctx, exitAnimRes, enterAnimRes) }
}

@Composable
internal fun RotationCustomAnimPreview(packageName: String) {
    RotationPreviewCard { ctx -> createRotationThemePreview(ctx, packageName) }
}

@Composable
internal fun RotationPreviewCard(factory: (Context) -> FrameLayout) {
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

internal fun createRotationPreview(
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

internal fun createRotationThemePreview(viewCtx: Context, packageName: String): FrameLayout {
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

internal fun createRotationPreviewViews(viewCtx: Context): RotationPreviewViews {
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

internal fun loopRotationAnim(
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

internal fun playRotationPreviewAnimation(ctx: Context, view: View, animationRes: Int): Long {
    if (animationRes == 0) return 350L
    return try {
        val animation = AnimationUtils.loadAnimation(ctx, animationRes).apply { fillAfter = true }
        view.startAnimation(animation)
        animation.duration + animation.startOffset
    } catch (_: Exception) { 350L }
}

internal fun resetRotationPreviewView(view: View) {
    view.clearAnimation()
    view.alpha = 1f
    view.scaleX = 1f
    view.scaleY = 1f
    view.translationX = 0f
    view.translationY = 0f
    view.rotation = 0f
}

internal fun styleDisplayName(context: Context, styleId: Int): String {
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
