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

@Composable
internal fun ParamEditor(
    params: AnimParams,
    onParamsChange: (AnimParams) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        // ── Translate ──
        Text(stringResource(R.string.anim_param_translate), style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        ParamSlider("fromX %", params.translateFromX, -100f, 100f, 0f) {
            onParamsChange(params.copy(translateFromX = it))
        }
        ParamSlider("toX %", params.translateToX, -100f, 100f, 0f) {
            onParamsChange(params.copy(translateToX = it))
        }
        ParamSlider("fromY %", params.translateFromY, -100f, 100f, 0f) {
            onParamsChange(params.copy(translateFromY = it))
        }
        ParamSlider("toY %", params.translateToY, -100f, 100f, 0f) {
            onParamsChange(params.copy(translateToY = it))
        }

        Spacer(Modifier.height(4.dp))

        // ── Scale ──
        Text(stringResource(R.string.anim_param_scale), style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        ParamSlider("fromX", params.scaleFromX, 0f, 3f, 1f) {
            onParamsChange(params.copy(scaleFromX = it))
        }
        ParamSlider("toX", params.scaleToX, 0f, 3f, 1f) {
            onParamsChange(params.copy(scaleToX = it))
        }
        ParamSlider("fromY", params.scaleFromY, 0f, 3f, 1f) {
            onParamsChange(params.copy(scaleFromY = it))
        }
        ParamSlider("toY", params.scaleToY, 0f, 3f, 1f) {
            onParamsChange(params.copy(scaleToY = it))
        }

        Spacer(Modifier.height(4.dp))

        // ── Alpha ──
        Text(stringResource(R.string.anim_param_alpha), style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        ParamSlider("from", params.alphaFrom, 0f, 1f, 1f) {
            onParamsChange(params.copy(alphaFrom = it))
        }
        ParamSlider("to", params.alphaTo, 0f, 1f, 1f) {
            onParamsChange(params.copy(alphaTo = it))
        }

        Spacer(Modifier.height(4.dp))

        // ── Rotate ──
        Text(stringResource(R.string.anim_param_rotate), style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        ParamSlider("from °", params.rotateFrom, -360f, 360f, 0f) {
            onParamsChange(params.copy(rotateFrom = it))
        }
        ParamSlider("to °", params.rotateTo, -360f, 360f, 0f) {
            onParamsChange(params.copy(rotateTo = it))
        }

        Spacer(Modifier.height(4.dp))

        // ── Pivot ──
        Text(stringResource(R.string.anim_param_pivot), style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        ParamSlider("X %", params.pivotX, 0f, 100f, 50f) {
            onParamsChange(params.copy(pivotX = it))
        }
        ParamSlider("Y %", params.pivotY, 0f, 100f, 50f) {
            onParamsChange(params.copy(pivotY = it))
        }

        Spacer(Modifier.height(4.dp))

        // ── Timing ──
        Text(stringResource(R.string.anim_param_timing), style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        ParamSlider("duration ms", params.duration.toFloat(), 50f, 2000f, 350f) {
            onParamsChange(params.copy(duration = it.toLong()))
        }
        ParamSlider("offset ms", params.startOffset.toFloat(), 0f, 1000f, 0f) {
            onParamsChange(params.copy(startOffset = it.toLong()))
        }

        Spacer(Modifier.height(4.dp))

        // ── Interpolator ──
        Text(stringResource(R.string.anim_param_interpolator), style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        InterpolatorSelector(
            selectedIndex = params.interpolatorIndex,
            onSelected = { onParamsChange(params.copy(interpolatorIndex = it)) }
        )
    }
}

@Composable
private fun ParamSlider(
    label: String,
    value: Float,
    min: Float,
    max: Float,
    defaultValue: Float = 0f,
    onValueChange: (Float) -> Unit
) {
    var showInputDialog by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .width(72.dp)
                .clickable { showInputDialog = true },
            maxLines = 1
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = min..max,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = if (max <= 3f) String.format("%.2f", value)
                   else String.format("%.0f", value),
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(48.dp),
            maxLines = 1
        )
        if (value != defaultValue) {
            IconButton(
                onClick = { onValueChange(defaultValue) },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    Icons.Rounded.Refresh, stringResource(R.string.anim_cd_reset),
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Spacer(Modifier.width(28.dp))
        }
    }

    if (showInputDialog) {
        var inputText by remember {
            mutableStateOf(
                if (max <= 3f) String.format("%.2f", value) else String.format("%.0f", value)
            )
        }
        AlertDialog(
            onDismissRequest = { showInputDialog = false },
            title = { Text(label) },
            text = {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    inputText.replace(',', '.').toFloatOrNull()?.let { v ->
                        onValueChange(v.coerceIn(min, max))
                    }
                    showInputDialog = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showInputDialog = false }) { Text(stringResource(R.string.anim_dialog_cancel)) }
            }
        )
    }
}

@Composable
private fun InterpolatorSelector(
    selectedIndex: Int,
    onSelected: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) {
            Text(INTERPOLATOR_NAMES.getOrElse(selectedIndex) { "Linear" },
                style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Rounded.ArrowDropDown, null, modifier = Modifier.size(18.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            INTERPOLATOR_NAMES.forEachIndexed { i, name ->
                DropdownMenuItem(
                    text = { Text(name, style = MaterialTheme.typography.bodyMedium) },
                    onClick = { onSelected(i); expanded = false },
                    leadingIcon = {
                        if (i == selectedIndex) Icon(Icons.Rounded.Check, null,
                            modifier = Modifier.size(18.dp))
                    }
                )
            }
        }
    }
}

// ── Constructor Preview ─────────────────────────────────────────────

@Composable
internal fun ConstructorPreview(
    openEnter: AnimParams,
    openExit: AnimParams,
    closeEnter: AnimParams,
    closeExit: AnimParams,
    previewKey: Int
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.anim_constructor_preview),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Open preview
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.anim_preview_open), style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        modifier = Modifier.size(width = 110.dp, height = 190.dp)
                    ) {
                        Box(modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp))) {
                            key(previewKey) {
                                AndroidView(
                                    factory = { ctx ->
                                        createConstructorPreview(ctx, openEnter, openExit, true)
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }

                // Close preview
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.anim_preview_close), style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        modifier = Modifier.size(width = 110.dp, height = 190.dp)
                    ) {
                        Box(modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp))) {
                            key(previewKey) {
                                AndroidView(
                                    factory = { ctx ->
                                        createConstructorPreview(ctx, closeEnter, closeExit, false)
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Export XML button ───────────────────────────────────────────────

internal fun animParamsToXml(params: AnimParams): String {
    val sb = StringBuilder()
    sb.appendLine("""<?xml version="1.0" encoding="utf-8"?>""")
    sb.appendLine("""<set xmlns:android="http://schemas.android.com/apk/res/android">""")

    // Translate
    if (params.translateFromX != 0f || params.translateToX != 0f ||
        params.translateFromY != 0f || params.translateToY != 0f) {
        sb.appendLine("    <translate")
        sb.appendLine("        android:fromXDelta=\"${params.translateFromX}%p\"")
        sb.appendLine("        android:toXDelta=\"${params.translateToX}%p\"")
        sb.appendLine("        android:fromYDelta=\"${params.translateFromY}%p\"")
        sb.appendLine("        android:toYDelta=\"${params.translateToY}%p\"")
        sb.appendLine("        android:duration=\"${params.duration}\"")
        sb.appendLine("        android:startOffset=\"${params.startOffset}\"")
        sb.appendLine("        android:interpolator=\"@android:anim/${interpolatorXmlName(params.interpolatorIndex)}\"")
        sb.appendLine("        android:fillAfter=\"true\" />")
    }
    // Scale
    if (params.scaleFromX != 1f || params.scaleToX != 1f ||
        params.scaleFromY != 1f || params.scaleToY != 1f) {
        sb.appendLine("    <scale")
        sb.appendLine("        android:fromXScale=\"${params.scaleFromX}\"")
        sb.appendLine("        android:toXScale=\"${params.scaleToX}\"")
        sb.appendLine("        android:fromYScale=\"${params.scaleFromY}\"")
        sb.appendLine("        android:toYScale=\"${params.scaleToY}\"")
        sb.appendLine("        android:pivotX=\"${params.pivotX}%\"")
        sb.appendLine("        android:pivotY=\"${params.pivotY}%\"")
        sb.appendLine("        android:duration=\"${params.duration}\"")
        sb.appendLine("        android:startOffset=\"${params.startOffset}\"")
        sb.appendLine("        android:interpolator=\"@android:anim/${interpolatorXmlName(params.interpolatorIndex)}\"")
        sb.appendLine("        android:fillAfter=\"true\" />")
    }
    // Alpha
    if (params.alphaFrom != 1f || params.alphaTo != 1f) {
        sb.appendLine("    <alpha")
        sb.appendLine("        android:fromAlpha=\"${params.alphaFrom}\"")
        sb.appendLine("        android:toAlpha=\"${params.alphaTo}\"")
        sb.appendLine("        android:duration=\"${params.duration}\"")
        sb.appendLine("        android:startOffset=\"${params.startOffset}\"")
        sb.appendLine("        android:interpolator=\"@android:anim/${interpolatorXmlName(params.interpolatorIndex)}\"")
        sb.appendLine("        android:fillAfter=\"true\" />")
    }
    // Rotate
    if (params.rotateFrom != 0f || params.rotateTo != 0f) {
        sb.appendLine("    <rotate")
        sb.appendLine("        android:fromDegrees=\"${params.rotateFrom}\"")
        sb.appendLine("        android:toDegrees=\"${params.rotateTo}\"")
        sb.appendLine("        android:pivotX=\"${params.pivotX}%\"")
        sb.appendLine("        android:pivotY=\"${params.pivotY}%\"")
        sb.appendLine("        android:duration=\"${params.duration}\"")
        sb.appendLine("        android:startOffset=\"${params.startOffset}\"")
        sb.appendLine("        android:interpolator=\"@android:anim/${interpolatorXmlName(params.interpolatorIndex)}\"")
        sb.appendLine("        android:fillAfter=\"true\" />")
    }

    sb.appendLine("</set>")
    return sb.toString()
}

private fun interpolatorXmlName(index: Int): String = when (index) {
    0 -> "linear_interpolator"
    1 -> "accelerate_interpolator"
    2 -> "decelerate_interpolator"
    3 -> "accelerate_decelerate_interpolator"
    4 -> "overshoot_interpolator"
    5 -> "bounce_interpolator"
    6 -> "anticipate_interpolator"
    7 -> "anticipate_overshoot_interpolator"
    else -> "linear_interpolator"
}
