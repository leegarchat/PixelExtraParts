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
internal fun ConstructorExportButton(
    openEnter: AnimParams,
    openExit: AnimParams,
    closeEnter: AnimParams,
    closeExit: AnimParams,
    onBuildTheme: (String) -> Unit
) {
    var showXml by remember { mutableStateOf(false) }
    var showBuildInput by remember { mutableStateOf(false) }
    var buildThemeName by remember { mutableStateOf("") }
    val clipboardManager = LocalClipboardManager.current

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // ── Build theme ──
            AnimatedVisibility(
                visible = showBuildInput,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(modifier = Modifier.padding(bottom = 8.dp)) {
                    OutlinedTextField(
                        value = buildThemeName,
                        onValueChange = { buildThemeName = filterThemeNameInput(it) },
                        label = { Text(stringResource(R.string.anim_constructor_style_name)) },
                        placeholder = { Text("e.g. my animation") },
                        singleLine = true,
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
                        Text(stringResource(R.string.anim_constructor_build_theme))
                    }
                }
            }

            FilledTonalButton(
                onClick = { showBuildInput = !showBuildInput },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Rounded.Build, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (showBuildInput) stringResource(R.string.anim_constructor_hide) else stringResource(R.string.anim_constructor_build_theme))
            }

            Spacer(Modifier.height(8.dp))

            // ── Show XML ──
            FilledTonalButton(
                onClick = { showXml = !showXml },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Rounded.Code, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (showXml) stringResource(R.string.anim_constructor_hide_xml) else stringResource(R.string.anim_constructor_show_xml))
            }

            AnimatedVisibility(
                visible = showXml,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    listOf(
                        "open_enter" to openEnter,
                        "open_exit" to openExit,
                        "close_enter" to closeEnter,
                        "close_exit" to closeExit
                    ).forEach { (name, p) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("<!-- $name -->",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontFamily = FontFamily.Monospace)
                            IconButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(animParamsToXml(p)))
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.ContentCopy, stringResource(R.string.anim_cd_copy),
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Card(
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                        ) {
                            Text(
                                text = animParamsToXml(p),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 9.sp,
                                    lineHeight = 12.sp
                                ),
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// Custom preview engine (ValueAnimator-based, no XML resources)
// ═══════════════════════════════════════════════════════════════════

/**
 * Create a looping preview that animates using raw AnimParams
 * instead of loading anim XML resources.
 */
internal fun createConstructorPreview(
    ctx: Context,
    enterParams: AnimParams,
    exitParams: AnimParams,
    isOpen: Boolean
): FrameLayout {
    val frame = FrameLayout(ctx).apply {
        clipChildren = true; clipToPadding = true
    }
    val screenA = createPreviewScreen(ctx, "A", 0xFFF3EDF7.toInt(), 0xFF49454F.toInt())
    val screenB = createPreviewScreen(ctx, "B", 0xFF6750A4.toInt(), 0xFFFFFFFF.toInt())
    frame.addView(screenA)
    frame.addView(screenB)

    if (isOpen) screenB.visibility = View.INVISIBLE
    else screenA.visibility = View.INVISIBLE

    frame.post {
        loopConstructorAnim(ctx, frame, screenA, screenB, enterParams, exitParams, isOpen)
    }
    return frame
}

private fun loopConstructorAnim(
    ctx: Context,
    frame: FrameLayout,
    screenA: View,
    screenB: View,
    enterParams: AnimParams,
    exitParams: AnimParams,
    isOpen: Boolean
) {
    if (!frame.isAttachedToWindow) return

    // Reset
    resetViewTransform(screenA)
    resetViewTransform(screenB)
    if (isOpen) {
        screenA.visibility = View.VISIBLE; screenB.visibility = View.INVISIBLE
    } else {
        screenA.visibility = View.INVISIBLE; screenB.visibility = View.VISIBLE
    }

    handler.postDelayed({
        if (!frame.isAttachedToWindow) return@postDelayed

        val enterDur = enterParams.duration + enterParams.startOffset
        val exitDur = exitParams.duration + exitParams.startOffset
        val maxDur = maxOf(enterDur, exitDur, 350L)

        if (isOpen) {
            // Open: B enters, A exits
            screenB.visibility = View.VISIBLE
            driveAnimParams(screenB, enterParams)
            driveAnimParams(screenA, exitParams)
        } else {
            // Close: A enters, B exits
            screenA.visibility = View.VISIBLE
            driveAnimParams(screenA, enterParams)
            driveAnimParams(screenB, exitParams)
        }

        handler.postDelayed({
            loopConstructorAnim(ctx, frame, screenA, screenB, enterParams, exitParams, isOpen)
        }, maxDur + 1200)
    }, 500)
}

private fun driveAnimParams(view: View, params: AnimParams) {
    val interpolator = resolveInterpolator(params.interpolatorIndex)
    ValueAnimator.ofFloat(0f, 1f).apply {
        duration = params.duration
        startDelay = params.startOffset
        this.interpolator = interpolator
        addUpdateListener { anim ->
            val t = anim.animatedValue as Float
            val parent = view.parent as? View ?: return@addUpdateListener
            val pw = parent.width.toFloat()
            val ph = parent.height.toFloat()

            view.translationX = lerp(params.translateFromX / 100f * pw, params.translateToX / 100f * pw, t)
            view.translationY = lerp(params.translateFromY / 100f * ph, params.translateToY / 100f * ph, t)
            view.scaleX = lerp(params.scaleFromX, params.scaleToX, t)
            view.scaleY = lerp(params.scaleFromY, params.scaleToY, t)
            view.alpha = lerp(params.alphaFrom, params.alphaTo, t)
            view.rotation = lerp(params.rotateFrom, params.rotateTo, t)
            view.pivotX = params.pivotX / 100f * view.width
            view.pivotY = params.pivotY / 100f * view.height
        }
        start()
    }
}

private fun resetViewTransform(v: View) {
    v.clearAnimation()
    v.translationX = 0f; v.translationY = 0f
    v.scaleX = 1f; v.scaleY = 1f
    v.alpha = 1f; v.rotation = 0f
}