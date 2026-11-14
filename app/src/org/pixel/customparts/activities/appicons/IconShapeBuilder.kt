package org.pixel.customparts.activities

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Matrix
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.MediaStore
import android.provider.Settings
import android.provider.Telephony
import android.util.PathParser
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp as lerpFloat
import androidx.compose.ui.zIndex
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.pixel.customparts.R
import org.pixel.customparts.dynamicDarkColorScheme
import org.pixel.customparts.dynamicLightColorScheme
import org.pixel.customparts.icons.IconPackManager
import org.pixel.customparts.icons.IconShapeOverlayManager
import org.pixel.customparts.icons.IconShapeOverlayManager.ShapeOption
import org.pixel.customparts.icons.IconShapeOverlayManager.ShapeSource
import org.pixel.customparts.ui.GraphicsLayerRecordingState
import org.pixel.customparts.ui.REBOOT_BUBBLE_CONTENT_BOTTOM_PADDING
import org.pixel.customparts.ui.RebootBubble
import org.pixel.customparts.ui.SettingsGroupCard
import org.pixel.customparts.ui.SliderMorphRoot
import org.pixel.customparts.ui.SliderSetting
import org.pixel.customparts.ui.SliderSettingFloat
import org.pixel.customparts.ui.SliderTitleText
import org.pixel.customparts.ui.TopBarBlurOverlay
import org.pixel.customparts.ui.TopStatusVeil
import org.pixel.customparts.ui.edgeOutline
import org.pixel.customparts.ui.recordLayer
import org.pixel.customparts.ui.rememberGraphicsLayerRecordingState
import org.pixel.customparts.ui.rememberSliderMorphHostState
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

@Composable
internal fun shapeGeneratorModeLabel(mode: Int): String {
    return when (mode) {
        IconShapeOverlayManager.MODE_POLYGON -> stringResource(R.string.icon_shape_mode_polygon)
        IconShapeOverlayManager.MODE_FLOWER -> stringResource(R.string.icon_shape_mode_flower)
        IconShapeOverlayManager.MODE_STAR -> stringResource(R.string.icon_shape_mode_star)
        IconShapeOverlayManager.MODE_ASYM_RECT -> stringResource(R.string.icon_shape_mode_asymmetric)
        else -> stringResource(R.string.icon_shape_mode_superellipse)
    }
}

@Composable
internal fun ShapeParametricConstructorCard(
    params: IconShapeOverlayManager.CustomParams,
    onParamsChange: (IconShapeOverlayManager.CustomParams) -> Unit
) {
    val mode = params.mode.coerceIn(0, 4)
    val topRowIndex = if (mode <= 2) mode else -1
    val bottomRowIndex = if (mode >= 3) mode - 3 else -1

    val animatedTopPos by animateFloatAsState(
        targetValue = topRowIndex.coerceAtLeast(0).toFloat(),
        animationSpec = spring(
            stiffness = Spring.StiffnessMediumLow,
            dampingRatio = Spring.DampingRatioNoBouncy
        ),
        label = "ShapeModeTopTabPos"
    )
    val animatedBottomPos by animateFloatAsState(
        targetValue = bottomRowIndex.coerceAtLeast(0).toFloat(),
        animationSpec = spring(
            stiffness = Spring.StiffnessMediumLow,
            dampingRatio = Spring.DampingRatioNoBouncy
        ),
        label = "ShapeModeBottomTabPos"
    )

    SettingsGroupCard(title = stringResource(R.string.icon_shape_custom_section)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 8.dp)
        ) {
            // Ряд 1: Superellipse | Polygon | Flower
            AppIconsTabs(
                titles = listOf(
                    stringResource(R.string.icon_shape_mode_superellipse),
                    stringResource(R.string.icon_shape_mode_polygon),
                    stringResource(R.string.icon_shape_mode_flower)
                ),
                position = if (topRowIndex >= 0) animatedTopPos else -10f,
                onTabClick = { onParamsChange(params.copy(mode = it)) }
            )

            Spacer(Modifier.height(6.dp))

            // Ряд 2: Star / Gear | Asymmetric Corners
            AppIconsTabs(
                titles = listOf(stringResource(R.string.icon_shape_mode_star), stringResource(R.string.icon_shape_mode_asymmetric)),
                position = if (bottomRowIndex >= 0) animatedBottomPos else -10f,
                onTabClick = { onParamsChange(params.copy(mode = it + 3)) }
            )

            Spacer(Modifier.height(12.dp))

            // Специфичные настройки выбранного режима
            AnimatedContent(
                targetState = mode,
                transitionSpec = {
                    val dir = if (targetState > initialState) 1 else -1
                    (slideInHorizontally(
                        animationSpec = spring(
                            stiffness = Spring.StiffnessMediumLow,
                            dampingRatio = Spring.DampingRatioNoBouncy
                        )
                    ) { fullW -> dir * fullW } + fadeIn(tween(180))) togetherWith
                        (slideOutHorizontally(
                            animationSpec = spring(
                                stiffness = Spring.StiffnessMediumLow,
                                dampingRatio = Spring.DampingRatioNoBouncy
                            )
                        ) { fullW -> -dir * fullW } + fadeOut(tween(140)))
                },
                label = "ShapeModeSlidersSlide"
            ) { currentMode ->
                Column(modifier = Modifier.fillMaxWidth()) {
                    when (currentMode) {
                        IconShapeOverlayManager.MODE_SUPERELLIPSE -> {
                            SliderSettingFloat(
                                title = stringResource(R.string.icon_shape_roundness),
                                value = params.roundness,
                                range = 0.6f..12f,
                                unit = "",
                                enabled = true,
                                stepSize = 0.1f,
                                defaultValue = DEFAULT_CUSTOM_SHAPE_ROUNDNESS,
                                defaultValueText = String.format(Locale.US, "%.1f", DEFAULT_CUSTOM_SHAPE_ROUNDNESS),
                                valueText = String.format(Locale.US, "%.2f", params.roundness),
                                onValueChange = { onParamsChange(params.copy(roundness = it)) },
                                onDefault = { onParamsChange(params.copy(roundness = DEFAULT_CUSTOM_SHAPE_ROUNDNESS)) }
                            )
                        }

                        IconShapeOverlayManager.MODE_POLYGON -> {
                            SliderSetting(
                                title = stringResource(R.string.icon_shape_sides),
                                value = params.sides,
                                range = 3..16,
                                unit = "",
                                enabled = true,
                                defaultValue = DEFAULT_CUSTOM_SHAPE_SIDES,
                                defaultValueText = DEFAULT_CUSTOM_SHAPE_SIDES.toString(),
                                valueText = params.sides.toString(),
                                onValueChange = { onParamsChange(params.copy(sides = it.coerceIn(3, 16))) },
                                onDefault = { onParamsChange(params.copy(sides = DEFAULT_CUSTOM_SHAPE_SIDES)) }
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 20.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                            )
                            SliderSettingFloat(
                                title = stringResource(R.string.icon_shape_corner_smoothing),
                                value = params.cornerRadius,
                                range = 0f..45f,
                                unit = "%",
                                enabled = true,
                                stepSize = 1f,
                                defaultValue = 0f,
                                defaultValueText = "0%",
                                valueText = String.format(Locale.US, "%.0f%%", params.cornerRadius),
                                onValueChange = { onParamsChange(params.copy(cornerRadius = it)) },
                                onDefault = { onParamsChange(params.copy(cornerRadius = 0f)) }
                            )
                        }

                        IconShapeOverlayManager.MODE_FLOWER -> {
                            SliderSetting(
                                title = stringResource(R.string.icon_shape_sides),
                                value = params.sides,
                                range = 3..16,
                                unit = "",
                                enabled = true,
                                defaultValue = DEFAULT_CUSTOM_SHAPE_SIDES,
                                defaultValueText = DEFAULT_CUSTOM_SHAPE_SIDES.toString(),
                                valueText = params.sides.toString(),
                                onValueChange = { onParamsChange(params.copy(sides = it.coerceIn(3, 16))) },
                                onDefault = { onParamsChange(params.copy(sides = DEFAULT_CUSTOM_SHAPE_SIDES)) }
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 20.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                            )
                            SliderSettingFloat(
                                title = stringResource(R.string.icon_shape_wave),
                                value = params.wave,
                                range = 0f..0.45f,
                                unit = "",
                                enabled = true,
                                stepSize = 0.01f,
                                defaultValue = DEFAULT_CUSTOM_SHAPE_WAVE,
                                defaultValueText = String.format(Locale.US, "%.2f", DEFAULT_CUSTOM_SHAPE_WAVE),
                                valueText = String.format(Locale.US, "%.2f", params.wave),
                                onValueChange = { onParamsChange(params.copy(wave = it)) },
                                onDefault = { onParamsChange(params.copy(wave = DEFAULT_CUSTOM_SHAPE_WAVE)) }
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 20.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                            )
                            SliderSettingFloat(
                                title = stringResource(R.string.icon_shape_petal_sharpness),
                                value = params.innerRadius,
                                range = 0.25f..2.5f,
                                unit = "",
                                enabled = true,
                                stepSize = 0.05f,
                                defaultValue = 1.0f,
                                defaultValueText = "1.00",
                                valueText = String.format(Locale.US, "%.2f", params.innerRadius),
                                onValueChange = { onParamsChange(params.copy(innerRadius = it)) },
                                onDefault = { onParamsChange(params.copy(innerRadius = 1.0f)) }
                            )
                        }

                        IconShapeOverlayManager.MODE_STAR -> {
                            SliderSetting(
                                title = stringResource(R.string.icon_shape_sides),
                                value = params.sides,
                                range = 3..16,
                                unit = "",
                                enabled = true,
                                defaultValue = DEFAULT_CUSTOM_SHAPE_SIDES,
                                defaultValueText = DEFAULT_CUSTOM_SHAPE_SIDES.toString(),
                                valueText = params.sides.toString(),
                                onValueChange = { onParamsChange(params.copy(sides = it.coerceIn(3, 16))) },
                                onDefault = { onParamsChange(params.copy(sides = DEFAULT_CUSTOM_SHAPE_SIDES)) }
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 20.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                            )
                            SliderSettingFloat(
                                title = stringResource(R.string.icon_shape_inner_radius),
                                value = params.innerRadius,
                                range = 0.2f..0.95f,
                                unit = "",
                                enabled = true,
                                stepSize = 0.02f,
                                defaultValue = 0.55f,
                                defaultValueText = "0.55",
                                valueText = String.format(Locale.US, "%.2f", params.innerRadius),
                                onValueChange = { onParamsChange(params.copy(innerRadius = it)) },
                                onDefault = { onParamsChange(params.copy(innerRadius = 0.55f)) }
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 20.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                            )
                            SliderSettingFloat(
                                title = stringResource(R.string.icon_shape_tip_smoothing),
                                value = params.cornerRadius,
                                range = 0f..45f,
                                unit = "%",
                                enabled = true,
                                stepSize = 1f,
                                defaultValue = 0f,
                                defaultValueText = "0%",
                                valueText = String.format(Locale.US, "%.0f%%", params.cornerRadius),
                                onValueChange = { onParamsChange(params.copy(cornerRadius = it)) },
                                onDefault = { onParamsChange(params.copy(cornerRadius = 0f)) }
                            )
                        }

                        else -> {
                            SliderSettingFloat(
                                title = stringResource(R.string.icon_shape_corner_top_left),
                                value = params.cornerTL,
                                range = 0f..50f,
                                unit = "%",
                                enabled = true,
                                stepSize = 1f,
                                defaultValue = 24f,
                                defaultValueText = "24%",
                                valueText = String.format(Locale.US, "%.0f%%", params.cornerTL),
                                onValueChange = { onParamsChange(params.copy(cornerTL = it)) },
                                onDefault = { onParamsChange(params.copy(cornerTL = 24f)) }
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 20.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                            )
                            SliderSettingFloat(
                                title = stringResource(R.string.icon_shape_corner_top_right),
                                value = params.cornerTR,
                                range = 0f..50f,
                                unit = "%",
                                enabled = true,
                                stepSize = 1f,
                                defaultValue = 24f,
                                defaultValueText = "24%",
                                valueText = String.format(Locale.US, "%.0f%%", params.cornerTR),
                                onValueChange = { onParamsChange(params.copy(cornerTR = it)) },
                                onDefault = { onParamsChange(params.copy(cornerTR = 24f)) }
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 20.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                            )
                            SliderSettingFloat(
                                title = stringResource(R.string.icon_shape_corner_bottom_right),
                                value = params.cornerBR,
                                range = 0f..50f,
                                unit = "%",
                                enabled = true,
                                stepSize = 1f,
                                defaultValue = 24f,
                                defaultValueText = "24%",
                                valueText = String.format(Locale.US, "%.0f%%", params.cornerBR),
                                onValueChange = { onParamsChange(params.copy(cornerBR = it)) },
                                onDefault = { onParamsChange(params.copy(cornerBR = 24f)) }
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 20.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                            )
                            SliderSettingFloat(
                                title = stringResource(R.string.icon_shape_corner_bottom_left),
                                value = params.cornerBL,
                                range = 0f..50f,
                                unit = "%",
                                enabled = true,
                                stepSize = 1f,
                                defaultValue = 24f,
                                defaultValueText = "24%",
                                valueText = String.format(Locale.US, "%.0f%%", params.cornerBL),
                                onValueChange = { onParamsChange(params.copy(cornerBL = it)) },
                                onDefault = { onParamsChange(params.copy(cornerBL = 24f)) }
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 20.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                            )
                            SliderSettingFloat(
                                title = stringResource(R.string.icon_shape_roundness),
                                value = params.roundness,
                                range = 1.2f..8f,
                                unit = "",
                                enabled = true,
                                stepSize = 0.1f,
                                defaultValue = DEFAULT_CUSTOM_SHAPE_ROUNDNESS,
                                defaultValueText = String.format(Locale.US, "%.1f", DEFAULT_CUSTOM_SHAPE_ROUNDNESS),
                                valueText = String.format(Locale.US, "%.2f", params.roundness),
                                onValueChange = { onParamsChange(params.copy(roundness = it)) },
                                onDefault = { onParamsChange(params.copy(roundness = DEFAULT_CUSTOM_SHAPE_ROUNDNESS)) }
                            )
                        }
                    }
                }
            }

            // Глобальные модификаторы геометрии и деформации
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SliderSettingFloat(
                title = stringResource(R.string.icon_shape_inset),
                value = params.inset,
                range = 0f..24f,
                unit = "%",
                enabled = true,
                stepSize = 0.5f,
                defaultValue = DEFAULT_CUSTOM_SHAPE_INSET,
                defaultValueText = "0%",
                valueText = String.format(Locale.US, "%.1f%%", params.inset),
                onValueChange = { onParamsChange(params.copy(inset = it)) },
                onDefault = { onParamsChange(params.copy(inset = DEFAULT_CUSTOM_SHAPE_INSET)) }
            )

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 20.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            )
            SliderSettingFloat(
                title = stringResource(R.string.icon_shape_barrel_pinch),
                value = params.pinch,
                range = -0.45f..0.45f,
                unit = "",
                enabled = true,
                stepSize = 0.02f,
                defaultValue = 0f,
                defaultValueText = "0.00",
                valueText = String.format(Locale.US, "%.2f", params.pinch),
                onValueChange = { onParamsChange(params.copy(pinch = it)) },
                onDefault = { onParamsChange(params.copy(pinch = 0f)) }
            )

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 20.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            )
            SliderSettingFloat(
                title = stringResource(R.string.icon_shape_spiral_twist),
                value = params.twist,
                range = -120f..120f,
                unit = "°",
                enabled = true,
                stepSize = 1f,
                defaultValue = 0f,
                defaultValueText = "0°",
                valueText = String.format(Locale.US, "%.0f°", params.twist),
                onValueChange = { onParamsChange(params.copy(twist = it)) },
                onDefault = { onParamsChange(params.copy(twist = 0f)) }
            )

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 20.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            )
            SliderSettingFloat(
                title = stringResource(R.string.icon_shape_rotation),
                value = params.rotation,
                range = -180f..180f,
                unit = "°",
                enabled = true,
                stepSize = 1f,
                defaultValue = DEFAULT_CUSTOM_SHAPE_ROTATION,
                defaultValueText = "0°",
                valueText = String.format(Locale.US, "%.0f°", params.rotation),
                onValueChange = { onParamsChange(params.copy(rotation = it)) },
                onDefault = { onParamsChange(params.copy(rotation = DEFAULT_CUSTOM_SHAPE_ROTATION)) }
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SliderSettingFloat(
                title = stringResource(R.string.icon_shape_skew_x),
                value = params.skewX,
                range = -30f..30f,
                unit = "°",
                enabled = true,
                stepSize = 1f,
                defaultValue = 0f,
                defaultValueText = "0°",
                valueText = String.format(Locale.US, "%.0f°", params.skewX),
                onValueChange = { onParamsChange(params.copy(skewX = it)) },
                onDefault = { onParamsChange(params.copy(skewX = 0f)) }
            )
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 20.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            )
            SliderSettingFloat(
                title = stringResource(R.string.icon_shape_skew_y),
                value = params.skewY,
                range = -30f..30f,
                unit = "°",
                enabled = true,
                stepSize = 1f,
                defaultValue = 0f,
                defaultValueText = "0°",
                valueText = String.format(Locale.US, "%.0f°", params.skewY),
                onValueChange = { onParamsChange(params.copy(skewY = it)) },
                onDefault = { onParamsChange(params.copy(skewY = 0f)) }
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SliderSettingFloat(
                title = stringResource(R.string.icon_shape_scale_x),
                value = params.scaleX,
                range = 0.5f..1.5f,
                unit = "x",
                enabled = true,
                stepSize = 0.05f,
                defaultValue = DEFAULT_CUSTOM_SHAPE_SCALE,
                defaultValueText = "1.00x",
                valueText = String.format(Locale.US, "%.2fx", params.scaleX),
                onValueChange = { onParamsChange(params.copy(scaleX = it)) },
                onDefault = { onParamsChange(params.copy(scaleX = DEFAULT_CUSTOM_SHAPE_SCALE)) }
            )
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 20.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            )
            SliderSettingFloat(
                title = stringResource(R.string.icon_shape_scale_y),
                value = params.scaleY,
                range = 0.5f..1.5f,
                unit = "x",
                enabled = true,
                stepSize = 0.05f,
                defaultValue = DEFAULT_CUSTOM_SHAPE_SCALE,
                defaultValueText = "1.00x",
                valueText = String.format(Locale.US, "%.2fx", params.scaleY),
                onValueChange = { onParamsChange(params.copy(scaleY = it)) },
                onDefault = { onParamsChange(params.copy(scaleY = DEFAULT_CUSTOM_SHAPE_SCALE)) }
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SliderSettingFloat(
                title = stringResource(R.string.icon_shape_offset_x),
                value = params.offsetX,
                range = -25f..25f,
                unit = "%",
                enabled = true,
                stepSize = 1f,
                defaultValue = DEFAULT_CUSTOM_SHAPE_OFFSET,
                defaultValueText = "0%",
                valueText = String.format(Locale.US, "%.0f%%", params.offsetX),
                onValueChange = { onParamsChange(params.copy(offsetX = it)) },
                onDefault = { onParamsChange(params.copy(offsetX = DEFAULT_CUSTOM_SHAPE_OFFSET)) }
            )
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 20.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            )
            SliderSettingFloat(
                title = stringResource(R.string.icon_shape_offset_y),
                value = params.offsetY,
                range = -25f..25f,
                unit = "%",
                enabled = true,
                stepSize = 1f,
                defaultValue = DEFAULT_CUSTOM_SHAPE_OFFSET,
                defaultValueText = "0%",
                valueText = String.format(Locale.US, "%.0f%%", params.offsetY),
                onValueChange = { onParamsChange(params.copy(offsetY = it)) },
                onDefault = { onParamsChange(params.copy(offsetY = DEFAULT_CUSTOM_SHAPE_OFFSET)) }
            )
        }
    }
}

// ── Floating Frosted Shape Builder Preview Bar & 1:1 Flight Modal ────────

@Composable
internal fun ShapeBuilderFloatBar(
    pathData: String,
    mode: Int,
    blurState: GraphicsLayerRecordingState,
    morphBlurState: GraphicsLayerRecordingState? = null,
    morphActive: Boolean = false,
    tintBoost: Float = 0f,
    visibleFraction: Float,
    isHiddenForFlight: Boolean,
    onExpand: (Rect, () -> Rect) -> Unit,
    modifier: Modifier = Modifier
) {
    if (visibleFraction <= 0.005f) return
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    var barBounds by remember { mutableStateOf(Rect.Zero) }
    val barCoordsRef = remember { StudioCoordsRef() }

    val shape = RoundedCornerShape(24.dp)
    val bgColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val slideOffsetPx = with(density) { 48.dp.toPx() } * (1f - visibleFraction)

    val modeLabel = shapeGeneratorModeLabel(mode)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = 16.dp,
                end = 16.dp,
                bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 16.dp
            )
            .onPlaced {
                val r = it.unclippedBoundsInRoot()
                if (r.width > 1f && r.height > 1f) barBounds = r
            }
            .onGloballyPositioned {
                val r = it.unclippedBoundsInRoot()
                if (r.width > 1f && r.height > 1f) barBounds = r
            }
            .graphicsLayer {
                translationY = slideOffsetPx
                alpha = if (isHiddenForFlight) 0f else visibleFraction
            }
            .clip(shape)
            .border(1.dp, bgColor.edgeOutline(), shape)
            .clickable(enabled = !isHiddenForFlight && visibleFraction > 0.5f) {
                haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                onExpand(barBounds) { barBounds }
            }
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(bgColor.copy(alpha = 0.88f))
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer { clip = true }
                    .onPlaced { barCoordsRef.value = it }
                    .onGloballyPositioned { barCoordsRef.value = it }
                    .cardFlightBlurMask(
                        state = blurState,
                        coordsProvider = { barCoordsRef.value }
                    )
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(bgColor.copy(alpha = 0.64f))
            )
            if (morphActive && morphBlurState != null) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .graphicsLayer { clip = true }
                        .cardFlightBlurMask(
                            state = morphBlurState,
                            coordsProvider = { barCoordsRef.value }
                        )
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(bgColor.copy(alpha = 0.64f))
                )
                if (tintBoost > 0.003f) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(
                                MaterialTheme.colorScheme.scrim.copy(
                                    alpha = tintBoost.coerceIn(0f, 1f)
                                )
                            )
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(76.dp)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ShapeBlueprintPreview(
                pathData = pathData,
                active = true,
                modifier = Modifier.size(54.dp)
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.icon_shape_custom_section),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = modeLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f))
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.primaryContainer.edgeOutline(),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowUp,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
internal fun ShapeBuilderModalOverlay(
    open: Boolean,
    sourceRect: Rect,
    sourceBoundsProvider: () -> Rect,
    pathData: String,
    mode: Int,
    compileState: AnimationCompileState?,
    onBuildAndApply: (String) -> Unit,
    onCancelCompile: () -> Unit,
    onDismissCompile: () -> Unit,
    onClose: () -> Unit,
    scrollBlur: GraphicsLayerRecordingState,
    onProgress: (Float) -> Unit,
    topInsetPx: Float = 0f,
    closeSignal: Int = 0
) {
    if (!open && compileState == null) return
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val clipboard = LocalClipboard.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val flightAnim = remember { Animatable(0f) }
    var closing by remember { mutableStateOf(false) }
    var showPathCode by remember { mutableStateOf(false) }
    val hostCoordsRef = remember { StudioCoordsRef() }
    val cardCoordsRef = remember { StudioCoordsRef() }
    val flightCoordsRef = remember { StudioCoordsRef() }

    val canDismiss = compileState == null ||
        compileState.phase == AnimationCompilePhase.SUCCESS ||
        compileState.phase == AnimationCompilePhase.ERROR ||
        compileState.phase == AnimationCompilePhase.CANCELLED

    LaunchedEffect(open, compileState != null) {
        if (open || compileState != null) {
            closing = false
            withFrameNanos { }
            haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
            flightAnim.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    stiffness = 105f,
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    visibilityThreshold = 0.002f
                )
            )
        }
    }

    fun closeAnimated(afterClose: (() -> Unit)? = null) {
        if (closing) return
        closing = true
        showPathCode = false
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
        scope.launch {
            flightAnim.stop()
            try {
                flightAnim.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(
                        stiffness = 155f,
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        visibilityThreshold = 0.005f
                    )
                )
            } finally {
                onProgress(0f)
                closing = false
                afterClose?.invoke()
                onClose()
            }
        }
    }

    BackHandler(enabled = !closing) {
        if (compileState != null) {
            if (canDismiss) {
                closeAnimated { onDismissCompile() }
            } else {
                onCancelCompile()
            }
        } else {
            closeAnimated()
        }
    }

    val initialCloseSignal = remember(open, compileState != null) { closeSignal }
    LaunchedEffect(closeSignal) {
        if (closeSignal > initialCloseSignal && !closing) {
            if (compileState != null) {
                if (canDismiss) closeAnimated { onDismissCompile() }
            } else {
                closeAnimated()
            }
        }
    }

    val rawT = flightAnim.value.coerceIn(0f, 1f)
    val morphT = sstepE(rawT, 0f, 1f)
    DisposableEffect(morphT) {
        onProgress(morphT)
        onDispose { }
    }

    val containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val scrimAlpha = 0.42f * sstepE(morphT, 0.45f, 0.92f)
    val cornerProgress = sstepE(morphT, 0.30f, 0.88f)
    val modalCorner = lerp(24.dp, 26.dp, cornerProgress)
    val modalShape = RoundedCornerShape(modalCorner)
    val cardBorderColor = containerColor.edgeOutline()

    val flightProgress = sstepE(morphT, 0.0f, 0.96f)
    val contentReveal = sstepE(morphT, 0.18f, 0.88f)

    val topInsetDp = with(density) { topInsetPx.coerceAtLeast(0f).toDp() }
    val navBottomDp = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val imeBottomDp = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
    val effectiveBottomDp = maxOf(navBottomDp, imeBottomDp)
    val effectiveBottomPx = with(density) { effectiveBottomDp.toPx() }

    val modeLabel = shapeGeneratorModeLabel(mode)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(1f)
    ) {
        val maxW = maxWidth
        val maxH = maxHeight
        val fallbackWpx = with(density) { maxWidth.toPx() }
        val fallbackHpx = with(density) { maxHeight.toPx() }

        val liveSource = sourceBoundsProvider().let {
            if (it.width > 1f && it.height > 1f) it else sourceRect
        }
        val endW = (maxW - 16.dp).coerceAtLeast(0.dp)
        val startW = if (liveSource.width > 1f) {
            with(density) { liveSource.width.roundToInt().toDp() }
        } else {
            endW
        }.coerceIn(0.dp, maxW)

        val endH = (maxH - topInsetDp - effectiveBottomDp - 8.dp).coerceAtLeast(220.dp)
        val startH = if (liveSource.height > 1f) {
            with(density) { liveSource.height.roundToInt().toDp() }
        } else {
            76.dp
        }.coerceIn(64.dp, maxH)

        val cardWidth: Dp = lerp(startW, endW, sstepE(morphT, 0f, 0.90f))
        val cardHeight: Dp = lerp(startH, endH, sstepE(morphT, 0f, 0.92f))

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = scrimAlpha))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        if (!closing && canDismiss) {
                            if (compileState != null) {
                                closeAnimated { onDismissCompile() }
                            } else {
                                closeAnimated()
                            }
                        }
                    }
                )
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = topInsetDp, bottom = effectiveBottomDp)
                .onPlaced { hostCoordsRef.value = it }
                .onGloballyPositioned { hostCoordsRef.value = it },
            contentAlignment = Alignment.Center
        ) {
            val hostBounds = hostCoordsRef.value?.boundsInRoot()
            val targetHost = when {
                hostBounds != null && hostBounds.width > 1f && hostBounds.height > 1f -> hostBounds
                else -> Rect(
                    0f,
                    topInsetPx.coerceAtMost(fallbackHpx),
                    fallbackWpx,
                    (fallbackHpx - effectiveBottomPx).coerceAtLeast(topInsetPx + 1f)
                )
            }
            val src = liveSource
            val dockW = 1f - flightProgress
            val cardWpx = with(density) { cardWidth.toPx() }.roundToInt()
            val cardHpx = with(density) { cardHeight.toPx() }.roundToInt().coerceAtLeast(1)
            val hostWpx = targetHost.width.roundToInt()
            val hostHpx = targetHost.height.roundToInt()
            val e0x = (hostWpx - cardWpx) / 2 + cardWpx / 2f - targetHost.width / 2f
            val e0y = (hostHpx - cardHpx) / 2 + cardHpx / 2f - targetHost.height / 2f

            val dx = if (targetHost.width > 1f && src.width > 1f) {
                (src.center.x - targetHost.center.x - e0x) * dockW
            } else 0f
            val dy = if (targetHost.height > 1f && src.height > 1f) {
                (src.center.y - targetHost.center.y - e0y) * dockW
            } else 0f

            Box(
                modifier = Modifier
                    .width(cardWidth)
                    .height(cardHeight)
                    .onPlaced { flightCoordsRef.value = it }
                    .graphicsLayer {
                        translationX = dx
                        translationY = dy
                    }
                    .clip(modalShape)
                    .border(1.dp, cardBorderColor, modalShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        enabled = !closing,
                        onClick = {}
                    )
            ) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(containerColor.copy(alpha = 0.88f))
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .graphicsLayer { clip = true }
                            .onPlaced { cardCoordsRef.value = it }
                            .onGloballyPositioned { cardCoordsRef.value = it }
                            .cardFlightBlurMask(
                                state = scrollBlur,
                                coordsProvider = { cardCoordsRef.value }
                            )
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(
                                containerColor.copy(
                                    alpha = lerpFloat(0.64f, 0.66f, cornerProgress)
                                )
                            )
                    )
                }

                AnimatedContent(
                    targetState = compileState,
                    transitionSpec = {
                        (fadeIn(tween(220)) + scaleIn(initialScale = 0.94f, animationSpec = tween(220))) togetherWith
                            fadeOut(tween(150))
                    },
                    label = "ShapeBuilderModalMode"
                ) { cState ->
                    if (cState != null) {
                        val terminal = cState.phase == AnimationCompilePhase.SUCCESS ||
                            cState.phase == AnimationCompilePhase.ERROR ||
                            cState.phase == AnimationCompilePhase.CANCELLED
                        val animatedProgress by animateFloatAsState(
                            targetValue = cState.progress.coerceIn(0f, 1f),
                            animationSpec = tween(250, easing = FastOutSlowInEasing),
                            label = "ShapeCompileProgress"
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            when {
                                !terminal -> {
                                    Icon(
                                        Icons.Rounded.Build,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .size(56.dp)
                                            .scale(1f + animatedProgress * 0.06f)
                                    )
                                    Spacer(Modifier.height(16.dp))
                                    LinearProgressIndicator(
                                        progress = { animatedProgress },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(8.dp)
                                            .clip(CircleShape)
                                    )
                                }
                                cState.phase == AnimationCompilePhase.SUCCESS -> Icon(
                                    Icons.Rounded.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(58.dp)
                                )
                                cState.phase == AnimationCompilePhase.ERROR -> Icon(
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

                            Spacer(Modifier.height(16.dp))
                            Text(
                                text = when (cState.phase) {
                                    AnimationCompilePhase.COMPILING -> stringResource(R.string.icon_shape_compile_dialog_compiling)
                                    AnimationCompilePhase.INSTALLING -> stringResource(R.string.icon_shape_compile_dialog_installing)
                                    AnimationCompilePhase.SUCCESS -> stringResource(R.string.icon_shape_compile_dialog_success)
                                    AnimationCompilePhase.ERROR -> stringResource(R.string.icon_shape_compile_dialog_error)
                                    AnimationCompilePhase.CANCELLED -> stringResource(R.string.icon_shape_compile_dialog_cancelled)
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )

                            if (cState.detail.isNotBlank()) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = cState.detail,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }

                            Spacer(Modifier.height(24.dp))
                            StudioTabStyledApplyButton(
                                isActive = false,
                                applyLabel = if (terminal) {
                                    stringResource(R.string.icon_shape_compile_dialog_ok)
                                } else {
                                    stringResource(R.string.icon_shape_compile_dialog_cancel)
                                },
                                activeLabel = stringResource(R.string.icon_shape_compile_dialog_ok),
                                enabled = !closing,
                                onClick = {
                                    if (terminal) {
                                        closeAnimated { onDismissCompile() }
                                    } else {
                                        onCancelCompile()
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    } else {
                        Column(modifier = Modifier.fillMaxSize()) {
                            // 1. 1:1 Header Row matching ShapeBuilderFloatBar at morphT = 0f
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(76.dp)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        enabled = !closing && morphT > 0.5f,
                                        onClick = {
                                            haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                                            closeAnimated()
                                        }
                                    )
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                ShapeBlueprintPreview(
                                    pathData = pathData,
                                    active = true,
                                    modifier = Modifier.size(lerp(54.dp, 48.dp, cornerProgress))
                                )
                                Spacer(Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    SliderTitleText(
                                        title = stringResource(R.string.icon_shape_custom_section),
                                        growProgress = sstepE(morphT, 0.10f, 0.80f),
                                        maxGrow = 1.35f,
                                        baseStyle = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.SemiBold
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Text(
                                        text = modeLabel,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f))
                                        .border(
                                            1.dp,
                                            MaterialTheme.colorScheme.primaryContainer.edgeOutline(),
                                            CircleShape
                                        )
                                        .clickable(enabled = !closing) {
                                            haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                                            closeAnimated()
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.KeyboardArrowUp,
                                        contentDescription = stringResource(R.string.btn_close),
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier
                                            .size(20.dp)
                                            .rotate(lerpFloat(0f, 180f, cornerProgress))
                                    )
                                }
                            }

                            // 2. Unfolding Full-Height Interactive Stage + Compile Bar
                            if (contentReveal > 0.005f) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                        .alpha(contentReveal)
                                        .padding(horizontal = 20.dp, vertical = 12.dp)
                                        .verticalScroll(rememberScrollState(), enabled = morphT > 0.7f),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    ShapeBlueprintPreview(
                                        pathData = pathData,
                                        active = true,
                                        modifier = Modifier.size(188.dp)
                                    )

                                    ShapeMiniDockShowcase(
                                        pathData = pathData,
                                        active = true
                                    )

                                    Spacer(Modifier.weight(1f))

                                    MorphingNameCompileBar(
                                        buttonLabel = stringResource(R.string.icon_shape_custom_apply),
                                        placeholderLabel = stringResource(R.string.icon_shape_custom_name),
                                        enabled = !closing && morphT > 0.6f,
                                        onCompile = onBuildAndApply
                                    )

                                    SecondaryActionPill(
                                        icon = Icons.Rounded.Code,
                                        label = if (showPathCode) {
                                            stringResource(R.string.rotation_anim_constructor_hide_xml)
                                        } else {
                                            stringResource(R.string.rotation_anim_constructor_show_xml)
                                        },
                                        enabled = !closing && morphT > 0.6f,
                                        onClick = { showPathCode = !showPathCode },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(44.dp)
                                    )

                                    AnimatedVisibility(
                                        visible = showPathCode,
                                        enter = expandVertically() + fadeIn(),
                                        exit = shrinkVertically() + fadeOut()
                                    ) {
                                        val codeBg = MaterialTheme.colorScheme.surfaceContainerHighest
                                        Card(
                                            shape = RoundedCornerShape(16.dp),
                                            colors = CardDefaults.cardColors(containerColor = codeBg),
                                            border = BorderStroke(1.dp, codeBg.edgeOutline()),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(12.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text(
                                                        text = "config_icon_mask",
                                                        style = MaterialTheme.typography.labelMedium,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.primary,
                                                        fontFamily = FontFamily.Monospace
                                                    )
                                                    IconButton(
                                                        onClick = {
                                                            haptics.performHapticFeedback(
                                                                HapticFeedbackType.ContextClick
                                                            )
                                                            scope.launch {
                                                                clipboard.setClipEntry(
                                                                    ClipEntry(
                                                                        ClipData.newPlainText(
                                                                            "config_icon_mask",
                                                                            pathData
                                                                        )
                                                                    )
                                                                )
                                                            }
                                                        },
                                                        modifier = Modifier.size(28.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Rounded.ContentCopy,
                                                            contentDescription = stringResource(R.string.anim_cd_copy),
                                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    }
                                                }
                                                Spacer(Modifier.height(4.dp))
                                                Text(
                                                    text = pathData,
                                                    style = MaterialTheme.typography.bodySmall.copy(
                                                        fontFamily = FontFamily.Monospace,
                                                        fontSize = 10.sp,
                                                        lineHeight = 14.sp
                                                    ),
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun shapeSourceLabel(source: ShapeSource): String {
    return when (source) {
        ShapeSource.DEFAULT -> stringResource(R.string.icon_shape_source_default)
        ShapeSource.INSTALLED_OVERLAY -> stringResource(R.string.icon_shape_source_installed)
        ShapeSource.BUILTIN_PRESET -> stringResource(R.string.icon_shape_source_builtin)
        ShapeSource.CUSTOM_OVERLAY -> stringResource(R.string.icon_shape_source_custom)
    }
}
