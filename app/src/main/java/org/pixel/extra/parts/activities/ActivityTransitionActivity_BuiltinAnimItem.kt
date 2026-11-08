package org.pixel.extra.parts.activities

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
import org.pixel.extra.parts.R
import org.pixel.extra.parts.SettingsKeys
import org.pixel.extra.parts.dynamicDarkColorScheme
import org.pixel.extra.parts.dynamicLightColorScheme
import org.pixel.extra.parts.ui.legacy.REBOOT_BUBBLE_CONTENT_BOTTOM_PADDING
import org.pixel.extra.parts.ui.legacy.SettingsGroupCard
import org.pixel.extra.parts.ui.TopBarBlurOverlay
import org.pixel.extra.parts.ui.recordLayer
import org.pixel.extra.parts.ui.rememberGraphicsLayerRecordingState
import org.pixel.extra.parts.utils.AnimThemeCompiler
import org.pixel.extra.parts.utils.PixelPartsTileRefresher
import org.pixel.extra.parts.utils.filterThemeNameInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.res.stringResource


// ═══════════════════════════════════════════════════════════════════
// Components
// ═══════════════════════════════════════════════════════════════════

// ── Built-in Animation Item ────────────────────────────────────────

@Composable
internal fun BuiltinAnimItem(
    mode: AnimMode,
    isActiveOpen: Boolean,
    isActiveClose: Boolean,
    onApplyBoth: () -> Unit,
    onApplyOpen: () -> Unit,
    onApplyClose: () -> Unit
) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }

    val displayName = remember(mode) {
        val group = context.getString(mode.groupNameRes)
        if (mode.dirNameRes != null) "$group — ${context.getString(mode.dirNameRes)}" else group
    }

    val isAnyActive = isActiveOpen || isActiveClose

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isAnyActive)
                MaterialTheme.colorScheme.surfaceContainerHigh
            else
                MaterialTheme.colorScheme.surface
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (isActiveOpen && isActiveClose) {
                        Text(stringResource(R.string.anim_active_open_close), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary, fontSize = 11.sp)
                    } else if (isActiveOpen) {
                        Text(stringResource(R.string.anim_preview_open), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary, fontSize = 11.sp)
                    } else if (isActiveClose) {
                        Text(stringResource(R.string.anim_preview_close), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary, fontSize = 11.sp)
                    }
                }
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        imageVector = if (expanded) Icons.Rounded.ExpandLess
                        else Icons.Rounded.ExpandMore,
                        contentDescription = stringResource(R.string.anim_constructor_preview)
                    )
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 20.dp, bottom = 16.dp)
                ) {
                    HorizontalDivider(
                        modifier = Modifier.padding(bottom = 12.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )

                    // Two preview cards side-by-side
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        LoopingAnimPreview(
                            label = stringResource(R.string.anim_preview_open),
                            enterAnimRes = mode.openEnterAnim,
                            exitAnimRes = mode.openExitAnim,
                            isOpen = true
                        )
                        LoopingAnimPreview(
                            label = stringResource(R.string.anim_preview_close),
                            enterAnimRes = mode.closeEnterAnim,
                            exitAnimRes = mode.closeExitAnim,
                            isOpen = false
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    // Apply buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalButton(
                            onClick = onApplyBoth,
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
                            onClick = onApplyOpen,
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
                            onClick = onApplyClose,
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
    }
}

// ── Looping Animation Preview (built-in) ───────────────────────────

@Composable
internal fun LoopingAnimPreview(
    label: String,
    enterAnimRes: Int,
    exitAnimRes: Int,
    isOpen: Boolean
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        Card(
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ),
            modifier = Modifier.size(width = 110.dp, height = 190.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
            ) {
                AndroidView(
                    factory = { ctx ->
                        createLoopingBuiltinPreview(ctx, enterAnimRes, exitAnimRes, isOpen)
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

// ── Theme Item (Custom Addons) ──────────────────────────────────────

@Composable
internal fun ThemeItem(
    theme: InstalledTheme,
    isHighlighted: Boolean,
    onApplyOpen: () -> Unit,
    onApplyClose: () -> Unit,
    onApplyBoth: () -> Unit,
    onUninstall: () -> Unit
) {
    var showConfirmDelete by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    val isActive = theme.isActiveOpen || theme.isActiveClose
    val highlightTransition = rememberInfiniteTransition(label = "theme_highlight")
    val highlightAlpha by highlightTransition.animateFloat(
        initialValue = 0f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(300),
            repeatMode = RepeatMode.Reverse
        ),
        label = "theme_highlight_alpha"
    )

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isActive)
                MaterialTheme.colorScheme.surfaceContainerHigh
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = theme.styleName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = theme.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp
                    )
                    if (theme.isActiveOpen && theme.isActiveClose) {
                        Text(stringResource(R.string.anim_active_open_close), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary, fontSize = 11.sp)
                    } else if (theme.isActiveOpen) {
                        Text(stringResource(R.string.anim_preview_open), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary, fontSize = 11.sp)
                    } else if (theme.isActiveClose) {
                        Text(stringResource(R.string.anim_preview_close), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary, fontSize = 11.sp)
                    }
                }

                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        imageVector = if (expanded) Icons.Rounded.ExpandLess
                        else Icons.Rounded.ExpandMore,
                        contentDescription = stringResource(R.string.anim_constructor_preview)
                    )
                }

                if (!showConfirmDelete) {
                    IconButton(onClick = { showConfirmDelete = true }) {
                        Icon(Icons.Rounded.Delete, stringResource(R.string.anim_cd_delete),
                            tint = MaterialTheme.colorScheme.error)
                    }
                } else {
                    Row {
                        IconButton(onClick = {
                            showConfirmDelete = false; onUninstall()
                        }) {
                            Icon(Icons.Rounded.Check, stringResource(R.string.anim_cd_confirm_delete),
                                tint = MaterialTheme.colorScheme.error)
                        }
                        IconButton(onClick = { showConfirmDelete = false }) {
                            Icon(Icons.Rounded.Close, stringResource(R.string.anim_dialog_cancel),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            // Expandable animation preview
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                ThemePreviewPanel(
                    packageName = theme.packageName,
                    onApplyBoth = onApplyBoth,
                    onApplyOpen = onApplyOpen,
                    onApplyClose = onApplyClose
                )
            }
        }
        if (isHighlighted) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color.White.copy(alpha = highlightAlpha))
            )
        }
    }
}
}

// ── Theme Preview Panel ─────────────────────────────────────────────

@Composable
internal fun ThemePreviewPanel(
    packageName: String,
    onApplyBoth: () -> Unit,
    onApplyOpen: () -> Unit,
    onApplyClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, bottom = 16.dp)
    ) {
        HorizontalDivider(
            modifier = Modifier.padding(bottom = 12.dp),
            color = MaterialTheme.colorScheme.outlineVariant
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            LoopingThemePreview(
                label = stringResource(R.string.anim_preview_open),
                packageName = packageName,
                enterAnimName = "custom_open_enter",
                exitAnimName = "custom_open_exit",
                isOpen = true
            )
            LoopingThemePreview(
                label = stringResource(R.string.anim_preview_close),
                packageName = packageName,
                enterAnimName = "custom_close_enter",
                exitAnimName = "custom_close_exit",
                isOpen = false
            )
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilledTonalButton(
                onClick = onApplyBoth,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(stringResource(R.string.anim_apply_both),
                    style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
            OutlinedButton(
                onClick = onApplyOpen,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(stringResource(R.string.anim_apply_open),
                    style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
            OutlinedButton(
                onClick = onApplyClose,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(stringResource(R.string.anim_apply_close),
                    style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
        }
    }
}

// ── Looping Theme Preview (custom APK) ──────────────────────────────

@Composable
internal fun LoopingThemePreview(
    label: String,
    packageName: String,
    enterAnimName: String,
    exitAnimName: String,
    isOpen: Boolean
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        Card(
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ),
            modifier = Modifier.size(width = 110.dp, height = 190.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
            ) {
                AndroidView(
                    factory = { ctx ->
                        createLoopingThemePreview(
                            ctx, packageName, enterAnimName, exitAnimName, isOpen
                        )
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

// ── File Picker Row ─────────────────────────────────────────────────

@Composable
internal fun FilePickerRow(
    label: String,
    uri: Uri?,
    required: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (uri != null) Icons.Rounded.CheckCircle else Icons.Rounded.FileOpen,
            contentDescription = null,
            tint = if (uri != null) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row {
                Text(text = label, style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium)
                if (required) {
                    Text(stringResource(R.string.form_required_mark), color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (uri != null) {
                Text(
                    text = uri.lastPathSegment ?: uri.toString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            } else {
                Text(
                    text = stringResource(R.string.anim_theme_tap_to_select),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ── XML Input Field ─────────────────────────────────────────────────

@Composable
internal fun XmlInputField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    required: Boolean
) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row {
            Text(text = label, style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium)
            if (required) {
                Text(stringResource(R.string.form_required_mark), color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium)
            }
        }
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(stringResource(R.string.anim_theme_xml_hint),
                style = MaterialTheme.typography.bodySmall) },
            textStyle = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                lineHeight = 14.sp
            ),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 80.dp, max = 160.dp),
            maxLines = 12
        )
    }
}

// ═══════════════════════════════════════════════════════════════════
// Preview engine: View-layer helpers
// ═══════════════════════════════════════════════════════════════════

internal val handler = Handler(Looper.getMainLooper())

internal fun createPreviewScreen(
    ctx: Context,
    label: String,
    bgColor: Int,
    fgColor: Int
): LinearLayout {
    val dp = ctx.resources.displayMetrics.density
    return LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(bgColor)
        layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
        // Fake top bar
        addView(View(ctx).apply {
            setBackgroundColor(fgColor and 0x00FFFFFF or (0x33 shl 24))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (18 * dp).toInt()
            )
        })
        addView(View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
        })
        addView(TextView(ctx).apply {
            text = label; textSize = 28f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(fgColor); gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        })
        addView(View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 0.6f
            )
        })
        repeat(2) { i ->
            addView(View(ctx).apply {
                setBackgroundColor(fgColor and 0x00FFFFFF or (0x22 shl 24))
                val m = (10 * dp).toInt()
                val w = if (i == 1) (36 * dp).toInt() else LinearLayout.LayoutParams.MATCH_PARENT
                layoutParams = LinearLayout.LayoutParams(w, (4 * dp).toInt()).apply {
                    setMargins(m, (3 * dp).toInt(), m, 0)
                }
            })
        }
        addView(View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (8 * dp).toInt()
            )
        })
    }
}

/**
 * Create a looping preview for a built-in animation.
 * Uses the app's own R.anim resources.
 */
internal fun createLoopingBuiltinPreview(
    viewCtx: Context,
    enterAnimRes: Int,
    exitAnimRes: Int,
    isOpen: Boolean
): FrameLayout {
    val frame = FrameLayout(viewCtx).apply {
        clipChildren = true; clipToPadding = true
    }
    val screenA = createPreviewScreen(viewCtx, "A", 0xFFF3EDF7.toInt(), 0xFF49454F.toInt())
    val screenB = createPreviewScreen(viewCtx, "B", 0xFF6750A4.toInt(), 0xFFFFFFFF.toInt())
    frame.addView(screenA)
    frame.addView(screenB)

    if (isOpen) {
        screenB.visibility = View.INVISIBLE
    } else {
        screenA.visibility = View.INVISIBLE
    }

    frame.post {
        loopBuiltinAnim(viewCtx, frame, screenA, screenB, enterAnimRes, exitAnimRes, isOpen)
    }
    return frame
}

internal fun loopBuiltinAnim(
    ctx: Context,
    frame: FrameLayout,
    screenA: View,
    screenB: View,
    enterRes: Int,
    exitRes: Int,
    isOpen: Boolean
) {
    if (!frame.isAttachedToWindow) return

    // Reset to initial state
    screenA.clearAnimation()
    screenB.clearAnimation()
    screenA.alpha = 1f; screenA.translationX = 0f; screenA.translationY = 0f
    screenA.scaleX = 1f; screenA.scaleY = 1f; screenA.rotation = 0f
    screenB.alpha = 1f; screenB.translationX = 0f; screenB.translationY = 0f
    screenB.scaleX = 1f; screenB.scaleY = 1f; screenB.rotation = 0f

    if (isOpen) {
        screenA.visibility = View.VISIBLE
        screenB.visibility = View.INVISIBLE
    } else {
        screenA.visibility = View.INVISIBLE
        screenB.visibility = View.VISIBLE
    }

    handler.postDelayed({
        if (!frame.isAttachedToWindow) return@postDelayed

        var duration = 350L
        if (isOpen) {
            screenB.visibility = View.VISIBLE
            if (enterRes != 0) {
                val anim = AnimationUtils.loadAnimation(ctx, enterRes).apply { fillAfter = true }
                duration = maxOf(duration, anim.duration + anim.startOffset)
                screenB.startAnimation(anim)
            }
            if (exitRes != 0) {
                val anim = AnimationUtils.loadAnimation(ctx, exitRes).apply { fillAfter = true }
                duration = maxOf(duration, anim.duration + anim.startOffset)
                screenA.startAnimation(anim)
            }
        } else {
            screenA.visibility = View.VISIBLE
            if (enterRes != 0) {
                val anim = AnimationUtils.loadAnimation(ctx, enterRes).apply { fillAfter = true }
                duration = maxOf(duration, anim.duration + anim.startOffset)
                screenA.startAnimation(anim)
            }
            if (exitRes != 0) {
                val anim = AnimationUtils.loadAnimation(ctx, exitRes).apply { fillAfter = true }
                duration = maxOf(duration, anim.duration + anim.startOffset)
                screenB.startAnimation(anim)
            }
        }

        handler.postDelayed({
            loopBuiltinAnim(ctx, frame, screenA, screenB, enterRes, exitRes, isOpen)
        }, duration + 1200)
    }, 500)
}

/**
 * Create a looping preview for a custom theme APK animation.
 * Loads animations from the theme package via createPackageContext.
 */
internal fun createLoopingThemePreview(
    viewCtx: Context,
    packageName: String,
    enterAnimName: String,
    exitAnimName: String,
    isOpen: Boolean
): FrameLayout {
    val frame = FrameLayout(viewCtx).apply {
        clipChildren = true; clipToPadding = true
    }
    val screenA = createPreviewScreen(viewCtx, "A", 0xFFF3EDF7.toInt(), 0xFF49454F.toInt())
    val screenB = createPreviewScreen(viewCtx, "B", 0xFF6750A4.toInt(), 0xFFFFFFFF.toInt())
    frame.addView(screenA)
    frame.addView(screenB)

    if (isOpen) {
        screenB.visibility = View.INVISIBLE
    } else {
        screenA.visibility = View.INVISIBLE
    }

    frame.post {
        try {
            val themeCtx = viewCtx.createPackageContext(packageName, Context.CONTEXT_IGNORE_SECURITY)
            val res = themeCtx.resources
            val enterId = res.getIdentifier(enterAnimName, "anim", packageName)
            val exitId = res.getIdentifier(exitAnimName, "anim", packageName)
            loopThemeAnim(themeCtx, frame, screenA, screenB, enterId, exitId, isOpen)
        } catch (_: Exception) {
            screenB.visibility = View.VISIBLE
        }
    }
    return frame
}

internal fun loopThemeAnim(
    themeCtx: Context,
    frame: FrameLayout,
    screenA: View,
    screenB: View,
    enterRes: Int,
    exitRes: Int,
    isOpen: Boolean
) {
    if (!frame.isAttachedToWindow) return

    screenA.clearAnimation()
    screenB.clearAnimation()
    screenA.alpha = 1f; screenA.translationX = 0f; screenA.translationY = 0f
    screenA.scaleX = 1f; screenA.scaleY = 1f; screenA.rotation = 0f
    screenB.alpha = 1f; screenB.translationX = 0f; screenB.translationY = 0f
    screenB.scaleX = 1f; screenB.scaleY = 1f; screenB.rotation = 0f

    if (isOpen) {
        screenA.visibility = View.VISIBLE
        screenB.visibility = View.INVISIBLE
    } else {
        screenA.visibility = View.INVISIBLE
        screenB.visibility = View.VISIBLE
    }

    handler.postDelayed({
        if (!frame.isAttachedToWindow) return@postDelayed

        var duration = 350L
        if (isOpen) {
            screenB.visibility = View.VISIBLE
            if (enterRes != 0) {
                val anim = AnimationUtils.loadAnimation(themeCtx, enterRes).apply { fillAfter = true }
                duration = maxOf(duration, anim.duration + anim.startOffset)
                screenB.startAnimation(anim)
            }
            if (exitRes != 0) {
                val anim = AnimationUtils.loadAnimation(themeCtx, exitRes).apply { fillAfter = true }
                duration = maxOf(duration, anim.duration + anim.startOffset)
                screenA.startAnimation(anim)
            }
        } else {
            screenA.visibility = View.VISIBLE
            if (enterRes != 0) {
                val anim = AnimationUtils.loadAnimation(themeCtx, enterRes).apply { fillAfter = true }
                duration = maxOf(duration, anim.duration + anim.startOffset)
                screenA.startAnimation(anim)
            }
            if (exitRes != 0) {
                val anim = AnimationUtils.loadAnimation(themeCtx, exitRes).apply { fillAfter = true }
                duration = maxOf(duration, anim.duration + anim.startOffset)
                screenB.startAnimation(anim)
            }
        }

        handler.postDelayed({
            loopThemeAnim(themeCtx, frame, screenA, screenB, enterRes, exitRes, isOpen)
        }, duration + 1200)
    }, 500)
}

// ═══════════════════════════════════════════════════════════════════
// Animation Constructor
// ═══════════════════════════════════════════════════════════════════

internal val CONSTRUCTOR_TAB_LABELS = listOf(R.string.anim_theme_open_enter, R.string.anim_theme_open_exit, R.string.anim_theme_close_enter, R.string.anim_theme_close_exit)

@Composable
internal fun AnimConstructorBlock(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    openEnter: AnimParams,
    openExit: AnimParams,
    closeEnter: AnimParams,
    closeExit: AnimParams,
    onOpenEnterChange: (AnimParams) -> Unit,
    onOpenExitChange: (AnimParams) -> Unit,
    onCloseEnterChange: (AnimParams) -> Unit,
    onCloseExitChange: (AnimParams) -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Tab row
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                edgePadding = 0.dp,
                containerColor = Color.Transparent,
                divider = {}
            ) {
                CONSTRUCTOR_TAB_LABELS.forEachIndexed { i, labelRes ->
                    Tab(
                        selected = selectedTab == i,
                        onClick = { onTabSelected(i) },
                        text = { Text(stringResource(labelRes), style = MaterialTheme.typography.labelMedium, maxLines = 1) }
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Selected tab params editor
            val currentParams = when (selectedTab) {
                0 -> openEnter; 1 -> openExit; 2 -> closeEnter; else -> closeExit
            }
            val onParamsChange: (AnimParams) -> Unit = when (selectedTab) {
                0 -> onOpenEnterChange; 1 -> onOpenExitChange
                2 -> onCloseEnterChange; else -> onCloseExitChange
            }

            ParamEditor(params = currentParams, onParamsChange = onParamsChange)
        }
    }
}