package org.pixel.extra.parts.systemui

import android.content.Context
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import org.pixel.extra.parts.R
import org.pixel.extra.parts.ui.edgeOutline

// Expandable built-in mode row: header with open/close badges, expanded live
// open+close previews and Both/Open/Close apply buttons.
@Composable
internal fun TransitionBuiltinRow(
    mode: TransitionAnimMode,
    isActiveOpen: Boolean,
    isActiveClose: Boolean,
    onApplyBoth: () -> Unit,
    onApplyOpen: () -> Unit,
    onApplyClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    var expanded by remember { mutableStateOf(false) }
    val isAnyActive = isActiveOpen || isActiveClose
    val container = if (isAnyActive) {
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
                if (isAnyActive) {
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = transitionRowTitle(mode),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (isActiveOpen && isActiveClose) {
                        Text(
                            text = stringResource(R.string.sysui_trans_active_open_close),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 11.sp,
                        )
                    } else if (isActiveOpen) {
                        Text(
                            text = stringResource(R.string.sysui_trans_preview_open),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 11.sp,
                        )
                    } else if (isActiveClose) {
                        Text(
                            text = stringResource(R.string.sysui_trans_preview_close),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 11.sp,
                        )
                    }
                }
                Icon(
                    painter = painterResource(R.drawable.ic_chevron_down),
                    contentDescription = stringResource(R.string.sysui_trans_preview),
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
                ) {
                    HorizontalDivider(
                        modifier = Modifier.padding(bottom = 12.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        TransitionLoopingPreview(
                            label = stringResource(R.string.sysui_trans_preview_open),
                            enterAnimRes = mode.openEnterAnim,
                            exitAnimRes = mode.openExitAnim,
                            isOpen = true,
                        )
                        TransitionLoopingPreview(
                            label = stringResource(R.string.sysui_trans_preview_close),
                            enterAnimRes = mode.closeEnterAnim,
                            exitAnimRes = mode.closeExitAnim,
                            isOpen = false,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilledTonalButton(
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                                onApplyBoth()
                            },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.sysui_trans_apply_both),
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                                onApplyOpen()
                            },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.sysui_trans_apply_open),
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                                onApplyClose()
                            },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.sysui_trans_apply_close),
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun transitionRowTitle(mode: TransitionAnimMode): String {
    val group = stringResource(mode.groupNameRes)
    val dir = mode.dirNameRes?.let { stringResource(it) }
    return if (dir != null) "$group — $dir" else group
}

// Expandable custom theme row: name, package, active badges, live previews of
// the theme APK anims, open/close/both apply and two-tap uninstall.
@Composable
internal fun TransitionThemeRow(
    theme: InstalledTransitionTheme,
    onApplyOpen: () -> Unit,
    onApplyClose: () -> Unit,
    onApplyBoth: () -> Unit,
    onUninstall: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    var expanded by remember { mutableStateOf(false) }
    var showConfirmDelete by remember { mutableStateOf(false) }
    val isActive = theme.isActiveOpen || theme.isActiveClose
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = theme.styleName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = theme.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp,
                    )
                    if (theme.isActiveOpen && theme.isActiveClose) {
                        Text(
                            text = stringResource(R.string.sysui_trans_active_open_close),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    } else if (isActive) {
                        Text(
                            text = stringResource(R.string.sysui_trans_active),
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
                            contentDescription = stringResource(R.string.sysui_trans_uninstall),
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
                            contentDescription = stringResource(R.string.sysui_trans_uninstall),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                    IconButton(onClick = { showConfirmDelete = false }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_close),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
                ) {
                    HorizontalDivider(
                        modifier = Modifier.padding(bottom = 12.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        TransitionThemePreview(
                            packageName = theme.packageName,
                            label = stringResource(R.string.sysui_trans_preview_open),
                            enterName = "custom_open_enter",
                            exitName = "custom_open_exit",
                            isOpen = true,
                        )
                        TransitionThemePreview(
                            packageName = theme.packageName,
                            label = stringResource(R.string.sysui_trans_preview_close),
                            enterName = "custom_close_enter",
                            exitName = "custom_close_exit",
                            isOpen = false,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilledTonalButton(
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                                onApplyBoth()
                            },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.sysui_trans_apply_both),
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                                onApplyOpen()
                            },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.sysui_trans_apply_open),
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                                onApplyClose()
                            },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.sysui_trans_apply_close),
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TransitionLoopingPreview(
    label: String,
    enterAnimRes: Int,
    exitAnimRes: Int,
    isOpen: Boolean,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Card(
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            modifier = Modifier.size(width = 110.dp, height = 190.dp),
        ) {
            Box(modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp))) {
                AndroidView(
                    factory = { ctx -> createLoopingBuiltinPreview(ctx, enterAnimRes, exitAnimRes, isOpen) },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun TransitionThemePreview(
    packageName: String,
    label: String,
    enterName: String,
    exitName: String,
    isOpen: Boolean,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Card(
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            modifier = Modifier.size(width = 110.dp, height = 190.dp),
        ) {
            Box(modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp))) {
                AndroidView(
                    factory = { ctx ->
                        createLoopingThemePreview(ctx, packageName, enterName, exitName, isOpen)
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

// Looping preview engine: two fake screens cross-play the enter/exit anims.
// Runs entirely in the app process on the bundled R.anim resources.
private val transitionPreviewHandler = Handler(Looper.getMainLooper())

private fun createPreviewScreen(ctx: Context, label: String, bgColor: Int, fgColor: Int): LinearLayout {
    val dp = ctx.resources.displayMetrics.density
    return LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(bgColor)
        layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT,
        )
        addView(View(ctx).apply {
            setBackgroundColor(fgColor and 0x00FFFFFF or (0x33 shl 24))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (18 * dp).toInt(),
            )
        })
        addView(View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f,
            )
        })
        addView(TextView(ctx).apply {
            text = label
            textSize = 28f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(fgColor)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
        })
        addView(View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 0.6f,
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
                LinearLayout.LayoutParams.MATCH_PARENT, (8 * dp).toInt(),
            )
        })
    }
}

private fun createLoopingBuiltinPreview(
    viewCtx: Context,
    enterAnimRes: Int,
    exitAnimRes: Int,
    isOpen: Boolean,
): FrameLayout {
    val frame = FrameLayout(viewCtx).apply {
        clipChildren = true
        clipToPadding = true
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
        loopBuiltinTransitionAnim(viewCtx, frame, screenA, screenB, enterAnimRes, exitAnimRes, isOpen)
    }
    return frame
}

private fun loopBuiltinTransitionAnim(
    ctx: Context,
    frame: FrameLayout,
    screenA: View,
    screenB: View,
    enterRes: Int,
    exitRes: Int,
    isOpen: Boolean,
) {
    if (!frame.isAttachedToWindow) return
    resetTransitionPreviewView(screenA)
    resetTransitionPreviewView(screenB)
    if (isOpen) {
        screenA.visibility = View.VISIBLE
        screenB.visibility = View.INVISIBLE
    } else {
        screenA.visibility = View.INVISIBLE
        screenB.visibility = View.VISIBLE
    }
    transitionPreviewHandler.postDelayed({
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
        transitionPreviewHandler.postDelayed({
            loopBuiltinTransitionAnim(ctx, frame, screenA, screenB, enterRes, exitRes, isOpen)
        }, duration + 1200)
    }, 500)
}

private fun createLoopingThemePreview(
    viewCtx: Context,
    packageName: String,
    enterAnimName: String,
    exitAnimName: String,
    isOpen: Boolean,
): FrameLayout {
    val frame = FrameLayout(viewCtx).apply {
        clipChildren = true
        clipToPadding = true
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
            loopThemeTransitionAnim(themeCtx, frame, screenA, screenB, enterId, exitId, isOpen)
        } catch (_: Exception) {
            screenB.visibility = View.VISIBLE
        }
    }
    return frame
}

private fun loopThemeTransitionAnim(
    themeCtx: Context,
    frame: FrameLayout,
    screenA: View,
    screenB: View,
    enterRes: Int,
    exitRes: Int,
    isOpen: Boolean,
) {
    if (!frame.isAttachedToWindow) return
    resetTransitionPreviewView(screenA)
    resetTransitionPreviewView(screenB)
    if (isOpen) {
        screenA.visibility = View.VISIBLE
        screenB.visibility = View.INVISIBLE
    } else {
        screenA.visibility = View.INVISIBLE
        screenB.visibility = View.VISIBLE
    }
    transitionPreviewHandler.postDelayed({
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
        transitionPreviewHandler.postDelayed({
            loopThemeTransitionAnim(themeCtx, frame, screenA, screenB, enterRes, exitRes, isOpen)
        }, duration + 1200)
    }, 500)
}

private fun resetTransitionPreviewView(view: View) {
    view.clearAnimation()
    view.alpha = 1f
    view.translationX = 0f
    view.translationY = 0f
    view.scaleX = 1f
    view.scaleY = 1f
    view.rotation = 0f
}
