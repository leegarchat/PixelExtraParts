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

// ── Compose Vector Blueprint & Adaptive Icon Shape Preview ───────────────

@Composable
internal fun ShapeBlueprintPreview(
    pathData: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    showBlueprintGrid: Boolean = true,
    showInnerGlyph: Boolean = true
) {
    val fillColor by animateColorAsState(
        targetValue = if (active) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.secondaryContainer
        },
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "ShapeFillColor"
    )
    val glyphColor by animateColorAsState(
        targetValue = if (active) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSecondaryContainer
        },
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "ShapeGlyphColor"
    )
    val strokeColor = if (active) {
        MaterialTheme.colorScheme.primary.edgeOutline(alpha = 0.85f)
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
    }
    val gridColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.10f)
    val stageBg = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f)

    Canvas(modifier = modifier) {
        val side = minOf(size.width, size.height).coerceAtLeast(1f)
        val left = (size.width - side) / 2f
        val top = (size.height - side) / 2f

        if (showBlueprintGrid) {
            val pad = side * 0.06f
            val boxSide = side - pad * 2f
            val boxLeft = left + pad
            val boxTop = top + pad
            val corner = boxSide * 0.22f
            drawRoundRect(
                color = stageBg,
                topLeft = Offset(boxLeft, boxTop),
                size = Size(boxSide, boxSide),
                cornerRadius = CornerRadius(corner, corner)
            )
            // Subtle center hairlines
            drawLine(
                color = gridColor,
                start = Offset(boxLeft + boxSide / 2f, boxTop + 6f),
                end = Offset(boxLeft + boxSide / 2f, boxTop + boxSide - 6f),
                strokeWidth = 1.dp.toPx()
            )
            drawLine(
                color = gridColor,
                start = Offset(boxLeft + 6f, boxTop + boxSide / 2f),
                end = Offset(boxLeft + boxSide - 6f, boxTop + boxSide / 2f),
                strokeWidth = 1.dp.toPx()
            )
        }

        val inset = if (showBlueprintGrid) side * 0.16f else side * 0.06f
        val maskSize = (side - inset * 2f).coerceAtLeast(1f)
        val maskLeft = left + inset
        val maskTop = top + inset

        val composePath: Path = runCatching {
            val androidPath = PathParser.createPathFromPathData(pathData)
            val matrix = Matrix().apply {
                setScale(maskSize / 100f, maskSize / 100f)
                postTranslate(maskLeft, maskTop)
            }
            androidPath.transform(matrix)
            androidPath.asComposePath()
        }.getOrElse {
            Path().apply {
                addOval(Rect(maskLeft, maskTop, maskLeft + maskSize, maskTop + maskSize))
            }
        }

        // Draw adaptive icon background + centered iris with asymmetric eye pupil
        clipPath(composePath) {
            drawPath(path = composePath, color = fillColor)
            if (showInnerGlyph) {
                val cx = maskLeft + maskSize * 0.5f
                val cy = maskTop + maskSize * 0.5f
                // 1. Внешний круг (радужка) — строго по центру
                drawCircle(
                    color = glyphColor.copy(alpha = 0.22f),
                    radius = maskSize * 0.31f,
                    center = Offset(cx, cy)
                )
                drawCircle(
                    color = glyphColor.copy(alpha = 0.38f),
                    radius = maskSize * 0.31f,
                    center = Offset(cx, cy),
                    style = Stroke(width = 1.25.dp.toPx())
                )
                // 2. Внутренний кружок (зрачок) — асимметрично со смещением («глазик»)
                val pupilCenter = Offset(cx + maskSize * 0.065f, cy - maskSize * 0.055f)
                drawCircle(
                    color = glyphColor.copy(alpha = 0.92f),
                    radius = maskSize * 0.135f,
                    center = pupilCenter
                )
                // 3. Микро-блик внутри глазика
                drawCircle(
                    color = fillColor.copy(alpha = 0.90f),
                    radius = maskSize * 0.036f,
                    center = Offset(
                        pupilCenter.x + maskSize * 0.045f,
                        pupilCenter.y - maskSize * 0.045f
                    )
                )
            }
        }

        drawPath(
            path = composePath,
            color = strokeColor,
            style = Stroke(width = 1.75.dp.toPx())
        )
    }
}

internal val cachedShowcaseBitmapsLock = Any()
internal var cachedShowcaseBitmaps: List<ImageBitmap>? = null

internal fun loadSystemShowcaseIcons(context: Context, sizePx: Int): List<ImageBitmap> {
    synchronized(cachedShowcaseBitmapsLock) {
        cachedShowcaseBitmaps?.let { if (it.size == 4) return it }
    }
    val pm = context.packageManager

    fun resolveCategoryAppInfo(intents: List<Intent>, fallbackPackages: List<String>): ApplicationInfo? {
        for (intent in intents) {
            val activities = runCatching {
                pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            }.getOrDefault(emptyList())
            val resolved = activities.firstOrNull {
                val pkg = it.activityInfo?.packageName
                !pkg.isNullOrBlank() && pkg != "android"
            }?.activityInfo?.applicationInfo
            if (resolved != null) return resolved

            val allActivities = runCatching {
                pm.queryIntentActivities(intent, 0)
            }.getOrDefault(emptyList())
            val anyResolved = allActivities.firstOrNull {
                val pkg = it.activityInfo?.packageName
                !pkg.isNullOrBlank() && pkg != "android"
            }?.activityInfo?.applicationInfo
            if (anyResolved != null) return anyResolved
        }
        for (pkg in fallbackPackages) {
            val info = runCatching { pm.getApplicationInfo(pkg, 0) }.getOrNull()
            if (info != null) return info
        }
        return null
    }

    fun renderFullBleedDrawable(drawable: Drawable, targetPx: Int): ImageBitmap {
        val bmp = Bitmap.createBitmap(targetPx, targetPx, Bitmap.Config.ARGB_8888)
        val canvas = AndroidCanvas(bmp)
        if (drawable is AdaptiveIconDrawable) {
            // Draw unmasked adaptive layers with standard 18% bleed so custom shape clips cleanly
            val bleed = (targetPx * 0.18f).roundToInt()
            val bg = drawable.background
            val fg = drawable.foreground
            if (bg != null || fg != null) {
                bg?.setBounds(-bleed, -bleed, targetPx + bleed, targetPx + bleed)
                bg?.draw(canvas)
                fg?.setBounds(-bleed, -bleed, targetPx + bleed, targetPx + bleed)
                fg?.draw(canvas)
                return bmp.asImageBitmap()
            }
        }
        drawable.setBounds(0, 0, targetPx, targetPx)
        drawable.draw(canvas)
        return bmp.asImageBitmap()
    }

    val defaultSmsPkg = runCatching { Telephony.Sms.getDefaultSmsPackage(context) }.getOrNull()

    val phoneInfo = resolveCategoryAppInfo(
        intents = listOf(
            Intent(Intent.ACTION_DIAL),
            Intent(Intent.ACTION_CALL_BUTTON),
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CONTACTS)
        ),
        fallbackPackages = listOf(
            "com.google.android.dialer",
            "com.android.dialer",
            "org.fossify.phone",
            "com.simplemobiletools.dialer",
            "com.truecaller"
        )
    )

    val messagesInfo = resolveCategoryAppInfo(
        intents = listOfNotNull(
            defaultSmsPkg?.let { Intent(Intent.ACTION_MAIN).setPackage(it) },
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MESSAGING),
            Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:"))
        ),
        fallbackPackages = listOfNotNull(
            defaultSmsPkg,
            "com.google.android.apps.messaging",
            "com.android.messaging",
            "com.android.mms",
            "org.lineageos.etar",
            "org.fossify.messages",
            "com.simplemobiletools.smsmessenger",
            "org.thoughtcrime.securesms"
        )
    )

    val cameraInfo = resolveCategoryAppInfo(
        intents = listOf(
            Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA),
            Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        ),
        fallbackPackages = listOf(
            "com.google.android.GoogleCamera",
            "org.lineageos.aperture",
            "app.grapheneos.camera",
            "com.android.camera2",
            "com.android.camera",
            "org.fossify.camera",
            "net.sourceforge.opencamera"
        )
    )

    val settingsInfo = resolveCategoryAppInfo(
        intents = listOf(Intent(Settings.ACTION_SETTINGS)),
        fallbackPackages = listOf("com.android.settings")
    )

    val result = listOf(phoneInfo, messagesInfo, cameraInfo, settingsInfo).mapNotNull { info ->
        runCatching {
            val drawable = if (info != null) {
                pm.getApplicationIcon(info)
            } else {
                pm.defaultActivityIcon
            }
            renderFullBleedDrawable(drawable, sizePx)
        }.getOrNull()
    }

    synchronized(cachedShowcaseBitmapsLock) {
        if (result.size == 4) cachedShowcaseBitmaps = result
    }
    return result
}

@Composable
internal fun ShapeMiniDockShowcase(
    pathData: String,
    active: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val iconSizeDp = 50.dp
    val iconSizePx = with(density) { iconSizeDp.roundToPx().coerceAtLeast(96) }

    var appBitmaps by remember { mutableStateOf<List<ImageBitmap>>(cachedShowcaseBitmaps.orEmpty()) }
    LaunchedEffect(iconSizePx) {
        if (appBitmaps.size < 4) {
            appBitmaps = withContext(Dispatchers.IO) {
                loadSystemShowcaseIcons(context, iconSizePx)
            }
        }
    }

    val dockBg = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.72f)
    val dockShape = RoundedCornerShape(24.dp)
    val strokeColor = if (active) {
        MaterialTheme.colorScheme.primary.edgeOutline(alpha = 0.65f)
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
    }

    Row(
        modifier = modifier
            .clip(dockShape)
            .background(dockBg)
            .border(1.dp, dockBg.edgeOutline(), dockShape)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (appBitmaps.isNotEmpty()) {
            appBitmaps.forEach { bmp ->
                Canvas(modifier = Modifier.size(iconSizeDp)) {
                    val side = minOf(size.width, size.height).coerceAtLeast(1f)
                    val left = (size.width - side) / 2f
                    val top = (size.height - side) / 2f
                    val composePath: Path = runCatching {
                        val androidPath = PathParser.createPathFromPathData(pathData)
                        val matrix = Matrix().apply {
                            setScale(side / 100f, side / 100f)
                            postTranslate(left, top)
                        }
                        androidPath.transform(matrix)
                        androidPath.asComposePath()
                    }.getOrElse {
                        Path().apply {
                            addOval(Rect(left, top, left + side, top + side))
                        }
                    }

                    clipPath(composePath) {
                        drawImage(
                            image = bmp,
                            dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
                            dstSize = IntSize(side.roundToInt(), side.roundToInt())
                        )
                    }
                    drawPath(
                        path = composePath,
                        color = strokeColor,
                        style = Stroke(width = 1.25.dp.toPx())
                    )
                }
            }
        } else {
            repeat(4) { idx ->
                ShapeBlueprintPreview(
                    pathData = pathData,
                    active = active || idx == 0,
                    showBlueprintGrid = false,
                    showInnerGlyph = true,
                    modifier = Modifier.size(iconSizeDp)
                )
            }
        }
    }
}

// ── Tab 1: Grid Cards & Shimmer Skeleton ─────────────────────────────────

@Composable
internal fun ShapeStudioAddCard(onClick: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val bgColor = MaterialTheme.colorScheme.surfaceContainerLow
    val shape = RoundedCornerShape(20.dp)
    val invisible = Color.Transparent

    Card(
        colors = CardDefaults.cardColors(containerColor = bgColor),
        shape = shape,
        border = BorderStroke(1.dp, bgColor.edgeOutline()),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                    onClick()
                }
        ) {
            // 1. Невидимая шапка 1:1 с ShapeStudioGridCard (titleSmall + labelSmall)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = " ",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = invisible,
                        maxLines = 1
                    )
                    Text(
                        text = " ",
                        style = MaterialTheme.typography.labelSmall,
                        color = invisible,
                        maxLines = 1
                    )
                }
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = null,
                    tint = invisible,
                    modifier = Modifier.size(22.dp)
                )
            }

            // 2. Центральная сцена 148.dp (1:1 с ShapeStudioGridCard)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(148.dp)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(28.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.primaryContainer.edgeOutline(),
                                RoundedCornerShape(28.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Text(
                        text = stringResource(R.string.rotation_anim_add_custom),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // 3. Нижний слот 46.dp с теми же отступами, что и ряд кнопки Apply
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, top = 2.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                )
            }
        }
    }
}

@Composable
internal fun SkeletonShapeGridCard() {
    val pulse = rememberInfiniteTransition(label = "ShapeGridSkeleton")
    val alpha by pulse.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(
            animation = tween(700),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ShapeGridSkeletonAlpha"
    )
    val bgColor = MaterialTheme.colorScheme.surfaceContainerLow
    val blockColor = MaterialTheme.colorScheme.surfaceContainerHighest

    Card(
        colors = CardDefaults.cardColors(containerColor = bgColor),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, bgColor.edgeOutline()),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(alpha)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.65f)
                        .height(14.dp)
                        .clip(CircleShape)
                        .background(blockColor)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.45f)
                        .height(10.dp)
                        .clip(CircleShape)
                        .background(blockColor)
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(148.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(blockColor)
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp)
                    .height(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(blockColor)
            )
        }
    }
}

@Composable
internal fun ShapeStudioGridCard(
    option: ShapeOption,
    busy: Boolean,
    trashArmed: Boolean,
    isHiddenForFlight: Boolean,
    onExpand: (Rect, () -> Rect) -> Unit,
    onApply: () -> Unit,
    onTrashClick: (() -> Unit)?
) {
    val haptics = LocalHapticFeedback.current
    var cardBounds by remember { mutableStateOf(Rect.Zero) }
    val cardBg by animateColorAsState(
        targetValue = if (option.active) {
            MaterialTheme.colorScheme.surfaceContainerHigh
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "ShapeGridCardBg"
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = cardBg),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, cardBg.edgeOutline()),
        modifier = Modifier
            .fillMaxWidth()
            .onPlaced {
                val r = it.unclippedBoundsInRoot()
                if (r.width > 1f && r.height > 1f) cardBounds = r
            }
            .onGloballyPositioned {
                val r = it.unclippedBoundsInRoot()
                if (r.width > 1f && r.height > 1f) cardBounds = r
            }
            .alpha(if (isHiddenForFlight) 0f else 1f)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 1. Header: Title + Source subtitle + Down-Arrow affordance
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !isHiddenForFlight) {
                        haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                        onExpand(cardBounds) { cardBounds }
                    }
                    .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = option.label,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = shapeSourceLabel(option.source),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (option.active) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }

            // 2. Center Stage: Vector Shape Blueprint Preview
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(148.dp)
                    .clickable(enabled = !isHiddenForFlight) {
                        haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                        onExpand(cardBounds) { cardBounds }
                    },
                contentAlignment = Alignment.Center
            ) {
                ShapeBlueprintPreview(
                    pathData = option.pathData,
                    active = option.active,
                    modifier = Modifier.size(112.dp)
                )
            }

            // 3. Bottom Action Row: StudioTabStyledApplyButton + optional 2-tap Trash
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, top = 2.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StudioTabStyledApplyButton(
                    isActive = option.active,
                    applyLabel = stringResource(R.string.icon_shape_apply),
                    activeLabel = stringResource(R.string.icon_shape_active),
                    enabled = !isHiddenForFlight && !busy && !option.active,
                    onClick = onApply,
                    modifier = Modifier.weight(1f)
                )

                if (onTrashClick != null) {
                    val trashCorner by animateFloatAsState(
                        targetValue = if (trashArmed) 28f else 12f,
                        animationSpec = spring(
                            stiffness = Spring.StiffnessMedium,
                            dampingRatio = Spring.DampingRatioNoBouncy
                        ),
                        label = "ShapeTrashCorner"
                    )
                    val trashBg = MaterialTheme.colorScheme.errorContainer
                    val trashContent = MaterialTheme.colorScheme.onErrorContainer
                    val trashShape = RoundedCornerShape(trashCorner.dp)

                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(trashShape)
                            .background(trashBg)
                            .border(1.dp, trashBg.edgeOutline(), trashShape)
                            .clickable(enabled = !isHiddenForFlight && !busy) {
                                haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                                onTrashClick()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (trashArmed) Icons.Rounded.Check else Icons.Rounded.Delete,
                            contentDescription = stringResource(R.string.icon_shape_delete),
                            tint = trashContent,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

