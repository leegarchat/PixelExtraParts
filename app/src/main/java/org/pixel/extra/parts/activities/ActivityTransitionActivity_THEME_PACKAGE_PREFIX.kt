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


// ── Constants ───────────────────────────────────────────────────────

internal const val THEME_PACKAGE_PREFIX = "org.pixel.extra.parts.anim."

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