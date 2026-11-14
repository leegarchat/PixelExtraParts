package org.pixel.customparts.activities

import android.content.ClipData
import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationSet
import android.view.animation.AnimationUtils
import android.view.animation.Transformation
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.ui.draw.rotate
import org.pixel.customparts.ui.SliderTitleText
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.ui.graphics.toArgb
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp as lerpFloat
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch
import org.pixel.customparts.R
import org.pixel.customparts.ui.GraphicsLayerRecordingState
import org.pixel.customparts.ui.REBOOT_BUBBLE_CONTENT_BOTTOM_PADDING
import org.pixel.customparts.ui.SettingsGroupCard
import org.pixel.customparts.ui.SliderSetting
import org.pixel.customparts.ui.SliderSettingFloat
import org.pixel.customparts.ui.edgeOutline
import org.pixel.customparts.utils.filterThemeNameInput
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.roundToLong

internal class StudioCoordsRef(var value: LayoutCoordinates? = null)

internal fun LayoutCoordinates.unclippedBoundsInRoot(): Rect {
    val s = size
    return Rect(positionInRoot(), Size(s.width.toFloat(), s.height.toFloat()))
}

internal fun sstepE(x: Float, a: Float, b: Float): Float {
    if (b <= a) return if (x >= b) 1f else 0f
    val t = ((x - a) / (b - a)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

internal fun Modifier.unfoldVerticalSlot(progress: Float): Modifier =
    this.layout { measurable, constraints ->
        val placeable = measurable.measure(constraints.copy(minHeight = 0))
        val p = progress.coerceIn(0f, 1f)
        if (p <= 0.001f) {
            layout(placeable.width, 0) {}
        } else {
            val h = (placeable.height * p).roundToInt()
            layout(placeable.width, h) {
                placeable.placeRelative(0, (h - placeable.height) / 2)
            }
        }
    }

internal fun Modifier.cardFlightBlurMask(
    state: GraphicsLayerRecordingState,
    coordsProvider: () -> LayoutCoordinates?
): Modifier = this.drawBehind {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return@drawBehind
    val blurNode = state.sharedBlurNode ?: return@drawBehind
    if (!blurNode.hasDisplayList()) return@drawBehind
    val coords = coordsProvider() ?: return@drawBehind
    if (!coords.isAttached) return@drawBehind
    val region = coords.boundsInRoot()
    if (region.isEmpty) return@drawBehind
    drawIntoCanvas { canvas ->
        val native = canvas.nativeCanvas
        val saveCount = native.save()
        native.translate(-region.left, -region.top)
        native.drawRenderNode(blurNode)
        native.restoreToCount(saveCount)
    }
}

// ── Segmented Tabs with live swipe-bound morphing & direct jump bypass ───

internal fun studioTabTargetWeights(
    textMeasurer: TextMeasurer,
    options: List<String>,
    selectedIndex: Int,
    selectedStyle: TextStyle,
    idleStyle: TextStyle,
    segmentGapPx: Float,
    segmentSidePaddingPx: Float,
    selectedExtraPx: Float,
    availablePx: Float
): List<Float> {
    val n = options.size
    if (n == 0) return emptyList()
    if (selectedIndex !in options.indices) return List(n) { 1f }
    val forSegments = (availablePx - segmentGapPx * (n - 1)).coerceAtLeast(0f)
    if (forSegments <= 0f) return List(n) { 1f }
    val equal = forSegments / n
    val selectedNeed = textMeasurer.measure(
        text = AnnotatedString(options[selectedIndex]),
        style = selectedStyle
    ).size.width.toFloat() + segmentSidePaddingPx + selectedExtraPx
    if (selectedNeed <= equal) return List(n) { 1f }
    val minWidths = options.map { label ->
        val probe = if (label.length > 4) label.take(4) + "…" else label
        textMeasurer.measure(
            text = AnnotatedString(probe),
            style = idleStyle
        ).size.width.toFloat() + segmentSidePaddingPx
    }
    val shrinkable = minWidths.map { (equal - it).coerceAtLeast(0f) }
    val totalShrinkable = shrinkable.sum()
    if (totalShrinkable <= 0f) return List(n) { 1f }
    val take = minOf(selectedNeed - equal, totalShrinkable)
    val finalPx = List(n) { i ->
        if (i == selectedIndex) equal + take
        else equal - take * (shrinkable[i] / totalShrinkable)
    }
    val sum = finalPx.sum().coerceAtLeast(1e-6f)
    return finalPx.map { it / sum * n }
}

