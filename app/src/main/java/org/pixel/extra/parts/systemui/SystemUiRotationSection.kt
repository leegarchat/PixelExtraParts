package org.pixel.extra.parts.systemui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.FrameLayout
import android.widget.TextView
import androidx.annotation.AnimRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.pixel.extra.parts.R
import org.pixel.extra.parts.ui.ExpandableGroupCard
import org.pixel.extra.parts.ui.GroupCard
import org.pixel.extra.parts.ui.edgeOutline

// Rotation animation style IDs. The framework reads the raw int, so IDs are
// frozen; the curated skeleton only exposed a subset, the full legacy
// catalog (default + 8 styles + custom APK) is restored here.
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
private const val ROTATION_THEME_PACKAGE_PREFIX = "org.pixel.customparts.rotation."

private data class RotationStyle(
    val styleId: Int,
    @StringRes val nameRes: Int,
    @AnimRes val exitAnimRes: Int,
    @AnimRes val enterAnimRes: Int,
    @AnimRes val alphaAnimRes: Int,
)

private val ROTATION_BUILTIN_STYLES: List<RotationStyle> by lazy {
    listOf(
        RotationStyle(ROTATION_STYLE_WINDOWS_PHONE, R.string.sysui_rot_winphone,
            R.anim.wp_exit, R.anim.wp_enter, R.anim.wp_alpha),
        RotationStyle(ROTATION_STYLE_WIN10_11, R.string.sysui_rot_win10,
            R.anim.win10_11_exit, R.anim.win10_11_enter, R.anim.win10_11_alpha),
        RotationStyle(ROTATION_STYLE_CUBE, R.string.sysui_rot_cube,
            R.anim.cube_exit, R.anim.cube_enter, R.anim.cube_alpha),
        RotationStyle(ROTATION_STYLE_ZOOM, R.string.sysui_rot_zoom,
            R.anim.zoom_rotate_exit, R.anim.zoom_rotate_enter, R.anim.zoom_rotate_alpha),
        RotationStyle(ROTATION_STYLE_SLIDE, R.string.sysui_rot_slide,
            R.anim.slide_rotate_exit, R.anim.slide_rotate_enter, R.anim.slide_rotate_alpha),
        RotationStyle(ROTATION_STYLE_FLIP, R.string.sysui_rot_flip,
            R.anim.flip_exit, R.anim.flip_enter, R.anim.flip_alpha),
        RotationStyle(ROTATION_STYLE_FADE, R.string.sysui_rot_fade,
            R.anim.fade_rotate_exit, R.anim.fade_rotate_enter, R.anim.fade_rotate_alpha),
        RotationStyle(ROTATION_STYLE_BOUNCE, R.string.sysui_rot_bounce,
            R.anim.bounce_exit, R.anim.bounce_enter, R.anim.bounce_alpha),
    )
}

// Hub group for the rotation animation: current-status card with reset, the
// full built-in style catalog with live looping previews, and installed
// custom themes (apply, two-tap uninstall). Importing/compiling new theme
// APKs and the parameter constructor need the firmware-side
// AnimThemeCompiler backend, which this tree does not ship.
@Composable
internal fun RotationGroup(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var style by remember {
        mutableIntStateOf(
            runCatching {
                Settings.Global.getInt(context.contentResolver, SystemUiKeys.ROTATION_STYLE)
            }.getOrDefault(ROTATION_STYLE_DEFAULT),
        )
    }
    var customPackage by remember {
        mutableStateOf(
            runCatching {
                Settings.Global.getString(context.contentResolver, SystemUiKeys.ROTATION_CUSTOM_PACKAGE)
            }.getOrNull().orEmpty(),
        )
    }
    var installedThemes by remember { mutableStateOf<List<String>>(emptyList()) }

    fun refreshThemes() {
        scope.launch(Dispatchers.IO) {
            installedThemes = runCatching {
                context.packageManager.getInstalledPackages(0)
                    .filter { it.packageName.startsWith(ROTATION_THEME_PACKAGE_PREFIX) }
                    .map { it.packageName }
                    .sorted()
            }.getOrDefault(emptyList())
        }
    }

    LaunchedEffect(Unit) { refreshThemes() }

    fun applyStyle(styleId: Int) {
        runCatching {
            Settings.Global.putInt(context.contentResolver, SystemUiKeys.ROTATION_STYLE, styleId)
        }
        style = styleId
    }

    fun applyCustomTheme(packageName: String) {
        runCatching {
            Settings.Global.putString(context.contentResolver, SystemUiKeys.ROTATION_CUSTOM_PACKAGE, packageName)
            Settings.Global.putInt(context.contentResolver, SystemUiKeys.ROTATION_STYLE, ROTATION_STYLE_CUSTOM_APK)
        }
        customPackage = packageName
        style = ROTATION_STYLE_CUSTOM_APK
    }

    val subtitle = stringResource(R.string.sysui_rot_status, stringResource(rotationStyleLabel(style)))

    ExpandableGroupCard(
        title = stringResource(R.string.sysui_rot_title),
        chevronPainter = painterResource(R.drawable.ic_chevron_down),
        subtitle = subtitle,
        keyTag = "systemui_rotation",
        modifier = modifier,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            GroupCard(title = stringResource(R.string.sysui_rot_title)) {
                Text(
                    text = stringResource(R.string.sysui_rot_current, rotationStyleName(context, style)),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                if (style != ROTATION_STYLE_DEFAULT) {
                    Button(
                        onClick = { applyStyle(ROTATION_STYLE_DEFAULT) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    ) {
                        Text(stringResource(R.string.sysui_rot_reset))
                    }
                }
            }
            Text(
                text = stringResource(R.string.sysui_rot_builtin_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 4.dp),
            )
            ROTATION_BUILTIN_STYLES.forEach { item ->
                RotationStyleRow(
                    style = item,
                    isActive = item.styleId == style,
                    onApply = { applyStyle(item.styleId) },
                )
            }
            Text(
                text = stringResource(R.string.sysui_rot_custom_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 4.dp),
            )
            if (installedThemes.isEmpty()) {
                Text(
                    text = stringResource(R.string.sysui_rot_no_themes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                installedThemes.forEach { packageName ->
                    RotationCustomThemeRow(
                        packageName = packageName,
                        isActive = style == ROTATION_STYLE_CUSTOM_APK && packageName == customPackage,
                        onApply = { applyCustomTheme(packageName) },
                        onUninstall = {
                            scope.launch(Dispatchers.IO) {
                                runCatching {
                                    val delete = Intent(Intent.ACTION_DELETE)
                                    delete.data = Uri.parse("package:$packageName")
                                    delete.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    context.startActivity(delete)
                                }
                                refreshThemes()
                            }
                        },
                    )
                }
            }
            Text(
                text = if (customPackage.isBlank()) {
                    stringResource(R.string.sysui_pkg_none)
                } else {
                    stringResource(R.string.sysui_pkg_current, customPackage)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun rotationStyleLabel(style: Int): Int = when (style) {
    ROTATION_STYLE_ZOOM -> R.string.sysui_rot_zoom
    ROTATION_STYLE_SLIDE -> R.string.sysui_rot_slide
    ROTATION_STYLE_FADE -> R.string.sysui_rot_fade
    ROTATION_STYLE_WINDOWS_PHONE -> R.string.sysui_rot_winphone
    ROTATION_STYLE_WIN10_11 -> R.string.sysui_rot_win10
    ROTATION_STYLE_CUBE -> R.string.sysui_rot_cube
    ROTATION_STYLE_FLIP -> R.string.sysui_rot_flip
    ROTATION_STYLE_BOUNCE -> R.string.sysui_rot_bounce
    ROTATION_STYLE_CUSTOM_APK -> R.string.sysui_rot_custom
    else -> R.string.sysui_rot_default
}

private fun rotationStyleName(context: Context, styleId: Int): String {
    if (styleId == ROTATION_STYLE_DEFAULT) return context.getString(R.string.sysui_rot_default)
    if (styleId == ROTATION_STYLE_CUSTOM_APK) return context.getString(R.string.sysui_rot_custom)
    val item = ROTATION_BUILTIN_STYLES.find { it.styleId == styleId }
    return if (item != null) context.getString(item.nameRes) else "Style $styleId"
}

@Composable
private fun RotationStyleRow(
    style: RotationStyle,
    isActive: Boolean,
    onApply: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    var expanded by remember { mutableStateOf(false) }
    val container = if (isActive) {
        MaterialTheme.colorScheme.surfaceContainerHigh
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }
    val shape = RoundedCornerShape(20.dp)
    Surface(
        color = container,
        shape = shape,
        modifier = modifier
            .fillMaxWidth()
            .border(
                1.dp,
                if (isActive) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                } else {
                    container.edgeOutline()
                },
                shape,
            ),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                        expanded = !expanded
                    }
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(style.nameRes),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (isActive) {
                        Text(
                            text = stringResource(R.string.sysui_rot_active),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Icon(
                    painter = painterResource(R.drawable.ic_chevron_down),
                    contentDescription = stringResource(R.string.sysui_rot_preview),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.rotate(if (expanded) 180f else 0f),
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
                ) {
                    HorizontalDivider(Modifier.padding(bottom = 12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        RotationAnimPreview(style.exitAnimRes, style.enterAnimRes)
                    }
                    Spacer(Modifier.height(12.dp))
                    FilledTonalButton(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                            onApply()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isActive,
                    ) {
                        if (isActive) {
                            Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(8.dp))
                        }
                        Text(
                            stringResource(
                                if (isActive) R.string.sysui_rot_active else R.string.sysui_rot_apply,
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RotationCustomThemeRow(
    packageName: String,
    isActive: Boolean,
    onApply: () -> Unit,
    onUninstall: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    var expanded by remember { mutableStateOf(false) }
    var showConfirmDelete by remember { mutableStateOf(false) }
    val container = if (isActive) {
        MaterialTheme.colorScheme.surfaceContainerHigh
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }
    val shape = RoundedCornerShape(20.dp)
    Surface(
        color = container,
        shape = shape,
        modifier = modifier
            .fillMaxWidth()
            .border(
                1.dp,
                if (isActive) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                } else {
                    container.edgeOutline()
                },
                shape,
            ),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                        expanded = !expanded
                    }
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        packageName.removePrefix(ROTATION_THEME_PACKAGE_PREFIX),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (isActive) {
                        Text(
                            text = stringResource(R.string.sysui_rot_active),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                if (!showConfirmDelete) {
                    IconButton(onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                        showConfirmDelete = true
                    }) {
                        Icon(
                            Icons.Rounded.Delete,
                            contentDescription = stringResource(R.string.sysui_rot_uninstall),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                } else {
                    IconButton(onClick = {
                        showConfirmDelete = false
                        onUninstall()
                    }) {
                        Icon(
                            Icons.Rounded.Check,
                            contentDescription = stringResource(R.string.sysui_rot_uninstall),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                    IconButton(onClick = { showConfirmDelete = false }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_close),
                            contentDescription = null,
                        )
                    }
                }
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
                ) {
                    HorizontalDivider(Modifier.padding(bottom = 12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        RotationCustomAnimPreview(packageName)
                    }
                    Spacer(Modifier.height(12.dp))
                    FilledTonalButton(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                            onApply()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isActive,
                    ) {
                        if (isActive) {
                            Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(8.dp))
                        }
                        Text(
                            stringResource(
                                if (isActive) R.string.sysui_rot_active else R.string.sysui_rot_apply,
                            ),
                        )
                    }
                }
            }
        }
    }
}

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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.size(width = 110.dp, height = 190.dp),
    ) {
        Box(Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp))) {
            AndroidView(factory = factory, modifier = Modifier.fillMaxSize())
        }
    }
}

private val rotationPreviewHandler = Handler(Looper.getMainLooper())
private const val ROTATION_PREVIEW_START_DELAY = 450L
private const val ROTATION_PREVIEW_HOLD_DELAY = 700L

private data class RotationPreviewViews(
    val frame: FrameLayout,
    val purpleScreen: View,
    val orangeScreen: View,
)

private fun createRotationPreview(viewCtx: Context, exitAnimRes: Int, enterAnimRes: Int): FrameLayout {
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
            val themeContext = viewCtx.createPackageContext(packageName, Context.CONTEXT_IGNORE_SECURITY)
            val exitAnimRes = themeContext.resources.getIdentifier("custom_rotate_exit", "anim", packageName)
            val enterAnimRes = themeContext.resources.getIdentifier("custom_rotate_enter", "anim", packageName)
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
    frame.addView(
        purpleScreen,
        FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT,
        ),
    )
    frame.addView(
        orangeScreen,
        FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT,
        ),
    )
    return RotationPreviewViews(frame, purpleScreen, orangeScreen)
}

private fun loopRotationAnim(
    ctx: Context,
    preview: RotationPreviewViews,
    exitAnimRes: Int,
    enterAnimRes: Int,
    showPurple: Boolean,
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
        outgoing.visibility = View.VISIBLE
        incoming.visibility = View.VISIBLE
        val exitDuration = playRotationPreviewAnimation(ctx, outgoing, exitAnimRes)
        rotationPreviewHandler.postDelayed({
            if (!frame.isAttachedToWindow) return@postDelayed
            outgoing.visibility = View.INVISIBLE
            playRotationPreviewAnimation(ctx, incoming, enterAnimRes)
            rotationPreviewHandler.postDelayed({
                loopRotationAnim(ctx, preview, exitAnimRes, enterAnimRes, !showPurple)
            }, ROTATION_PREVIEW_HOLD_DELAY)
        }, exitDuration)
    }, ROTATION_PREVIEW_START_DELAY)
}

private fun playRotationPreviewAnimation(ctx: Context, view: View, animationRes: Int): Long {
    if (animationRes == 0) return 350L
    return try {
        val animation = AnimationUtils.loadAnimation(ctx, animationRes).apply { fillAfter = true }
        view.startAnimation(animation)
        animation.duration + animation.startOffset
    } catch (_: Exception) {
        350L
    }
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
