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

// ── Constants ───────────────────────────────────────────────────────

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

private fun loopBuiltinAnim(
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

private fun loopThemeAnim(
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
