package org.pixel.customparts.ui

import android.net.Uri
import android.widget.VideoView
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.*
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import org.pixel.customparts.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import java.net.URL
import androidx.compose.material.icons.rounded.Refresh

@Composable
fun ExpandableWarningCard(
    title: String,
    text: String,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f),
    contentColor: Color = MaterialTheme.colorScheme.onErrorContainer,
    dividerAlpha: Float = 0.2f,
    titleStyle: TextStyle = MaterialTheme.typography.titleMedium,
    textStyle: TextStyle = MaterialTheme.typography.bodyMedium
) {
    var expanded by remember { mutableStateOf(false) }

    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "rot",
        animationSpec = tween(300, easing = FastOutSlowInEasing)
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(24.dp),
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessHigh
                )
            )
    ) {
        Column(
            modifier = Modifier
                .clickable { expanded = !expanded }
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Warning, null, tint = contentColor)
                Text(
                    text = title,
                    style = titleStyle,
                    color = contentColor,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(horizontal = 16.dp)
                )
                Icon(Icons.Rounded.ExpandMore, null, modifier = Modifier.rotate(rotation), tint = contentColor)
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(animationSpec = tween(240, easing = FastOutSlowInEasing)) + fadeIn(tween(180)),
                exit = shrinkVertically(animationSpec = tween(200, easing = FastOutSlowInEasing)) + fadeOut(tween(140))
            ) {
                Column {
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(color = contentColor.copy(alpha = dividerAlpha), modifier = Modifier.padding(bottom = 12.dp))
                    Text(text = text, style = textStyle, color = contentColor)
                }
            }
        }
    }
}

@Composable
fun GenericSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    summary: String? = null,
    infoText: String? = null,
    videoResName: String? = null,
    enabled: Boolean = true,
    onInfoClick: ((String, String, String?) -> Unit)? = null
) {
    val contentAlpha = if (enabled) 1f else 0.4f
    val haptics = LocalHapticFeedback.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f).alpha(contentAlpha), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (summary != null) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (onInfoClick != null && infoText != null) {
            IconButton(
                onClick = { onInfoClick(title, infoText, videoResName) },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = "Info",
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = {
                haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                onCheckedChange(it)
            },
            modifier = Modifier.alpha(contentAlpha)
        )
    }
}

@Composable
fun SliderSetting(
    title: String,
    value: Int,
    range: IntRange,
    unit: String,
    enabled: Boolean,
    onValueChange: (Int) -> Unit,
    onDefault: () -> Unit,
    valueText: String? = null,
    infoText: String? = null,
    videoResName: String? = null,
    onInfoClick: ((String, String, String?) -> Unit)? = null,
    onValueChangeFinished: (() -> Unit)? = null,
    showDefaultButton: Boolean = true,
    inputRange: IntRange? = null,
    defaultValue: Int? = null,
    // Kept for source compatibility; the layout is now unified (value readout
    // is always below-right), so this flag no longer changes anything.
    showValueBelow: Boolean = false,
    // Pre-formatted default value shown in the reset capsule as
    // "Default (<defaultValueText>)". Uses the exact same formatting as
    // valueText of the readout; null keeps the plain "Default" label.
    defaultValueText: String? = null
) {
    SliderSettingFloat(
        title = title,
        value = value.toFloat(),
        range = range.first.toFloat()..range.last.toFloat(),
        unit = unit,
        enabled = enabled,
        onValueChange = { onValueChange(it.toInt()) },
        onDefault = onDefault,
        valueText = valueText,
        isInteger = true,
        infoText = infoText,
        videoResName = videoResName,
        onInfoClick = onInfoClick,
        onValueChangeFinished = onValueChangeFinished,
        showDefaultButton = showDefaultButton,
        inputRange = inputRange?.let { it.first.toFloat()..it.last.toFloat() },
        stepSize = 1f,
        defaultValue = defaultValue?.toFloat(),
        showValueBelow = showValueBelow,
        defaultValueText = defaultValueText
    )
}

@Composable
fun SliderSettingFloat(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    unit: String,
    enabled: Boolean,
    onValueChange: (Float) -> Unit,
    onDefault: () -> Unit,
    valueText: String? = null,
    isInteger: Boolean = false,
    infoText: String? = null,
    videoResName: String? = null,
    onInfoClick: ((String, String, String?) -> Unit)? = null,
    onValueChangeFinished: (() -> Unit)? = null,
    showDefaultButton: Boolean = true,
    inputRange: ClosedFloatingPointRange<Float>? = null,
    stepSize: Float = if (isInteger) 1f else 0.01f,
    defaultValue: Float? = null,
    // Kept for source compatibility; the layout is now unified (value readout
    // is always below-right), so this flag no longer changes anything.
    showValueBelow: Boolean = false,
    // Pre-formatted default value shown in the reset capsule as
    // "Default (<defaultValueText>)". Uses the exact same formatting as
    // valueText of the readout; null keeps the plain "Default" label.
    defaultValueText: String? = null
) {
    var showManualInput by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    // Disabled rows keep enabled colors, dim to 0.42 (DESIGN_GUIDELINES §6.5).
    val contentAlpha = if (enabled) 1f else 0.42f

    val formattedValue = valueText ?: if (isInteger) {
        "${value.toInt()} $unit"
    } else {
        String.format("%.2f %s", value, unit)
    }

    // Reset badge appears once the drift off default exceeds a small epsilon:
    // 0.5% of the range span, but at most half a step (so any real step always
    // counts) and never below float noise. Reset itself always lands exactly
    // on the default value.
    val resetThreshold = minOf(
        (range.endInclusive - range.start) * 0.005f,
        stepSize * 0.5f
    ).coerceAtLeast(1e-6f)
    val showResetBadge = showDefaultButton &&
        (defaultValue == null || kotlin.math.abs(value - defaultValue) >= resetThreshold)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { showManualInput = true }
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        // Row 1: title only — single line, shrink-to-fit, never above base size
        // (DESIGN_GUIDELINES §6.5).
        SliderTitleText(
            title = title,
            modifier = Modifier.fillMaxWidth().alpha(contentAlpha)
        )

        Spacer(Modifier.height(10.dp))

        // Row 2: slider with step buttons: [−] [slider] [+]
        Row(verticalAlignment = Alignment.CenterVertically) {
            ExpressiveStepButton(
                symbol = "−",
                enabled = enabled && value > range.start,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    val newValue = (value - stepSize).coerceIn(range.start, range.endInclusive)
                    onValueChange(newValue)
                    onValueChangeFinished?.invoke()
                },
                modifier = Modifier.alpha(contentAlpha)
            )

            Spacer(Modifier.width(10.dp))

            val sliderColors = SliderDefaults.colors(
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
                // Disabled state keeps enabled colors; the row dims via alpha instead.
                disabledThumbColor = MaterialTheme.colorScheme.primary,
                disabledActiveTrackColor = MaterialTheme.colorScheme.primary,
                disabledInactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                disabledActiveTickColor = Color.Transparent,
                disabledInactiveTickColor = Color.Transparent
            )
            Slider(
                value = value.coerceIn(range.start, range.endInclusive),
                onValueChange = onValueChange,
                valueRange = range,
                enabled = enabled,
                colors = sliderColors,
                modifier = Modifier.weight(1f).alpha(contentAlpha),
                onValueChangeFinished = onValueChangeFinished,
                thumb = {
                    Box(
                        modifier = Modifier
                            .width(8.dp)
                            .height(28.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
            )

            Spacer(Modifier.width(10.dp))

            ExpressiveStepButton(
                symbol = "+",
                enabled = enabled && value < range.endInclusive,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    val newValue = (value + stepSize).coerceIn(range.start, range.endInclusive)
                    onValueChange(newValue)
                    onValueChangeFinished?.invoke()
                },
                modifier = Modifier.alpha(contentAlpha)
            )
        }

        Spacer(Modifier.height(10.dp))

        // Row 3: (i) [reset] <spacer> <value> (DESIGN_GUIDELINES §6.5).
        // The value readout keeps the exact formatted string, rendered as plain text.
        // Fixed height: the reset capsule is taller than the plain readout, so the
        // row reserves its slot up front and never jumps when the badge appears.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().height(32.dp).alpha(contentAlpha)
        ) {
            if (onInfoClick != null && infoText != null) {
                IconButton(
                    onClick = { onInfoClick(title, infoText, videoResName) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = "Info",
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            if (showResetBadge) {
                val resetText = if (defaultValueText.isNullOrEmpty()) {
                    stringResource(R.string.btn_default)
                } else {
                    stringResource(R.string.btn_default_with_value, defaultValueText)
                }
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f))
                        .clickable(enabled = enabled) {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onDefault()
                            onValueChangeFinished?.invoke()
                        }
                ) {
                    Text(
                        text = resetText,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            Text(
                text = formattedValue,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                textAlign = TextAlign.End
            )
        }
    }

    if (showManualInput) {
        val dialogRange = inputRange ?: range
        FloatInputDialog(
            title = title,
            initialValue = value,
            rangeStart = dialogRange.start,
            rangeEnd = dialogRange.endInclusive,
            unit = unit,
            isInteger = isInteger,
            onDismiss = { showManualInput = false },
            onConfirm = { 
                onValueChange(it)
                onValueChangeFinished?.invoke()
                showManualInput = false 
            },
            onDefault = {
                onDefault()
                showManualInput = false
            }
        )
    }
}

/**
 * Single-line slider title with shrink-to-fit (DESIGN_GUIDELINES §6.5).
 * Manual onTextLayout shrink loop on purpose: the firmware Soong build links
 * platform Compose libs that predate the TextAutoSize API, so this works on
 * every Compose version. The base titleMedium size is the ceiling — the text
 * only shrinks (0.5sp per overflow pass, floor 70% of base, min 10sp) and
 * short titles stay exactly at base size. Never ellipsized, maxLines = 1.
 */
@Composable
private fun SliderTitleText(
    title: String,
    modifier: Modifier = Modifier
) {
    val baseStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
    val baseSizeSp = baseStyle.fontSize.value
    val minSizeSp = (baseSizeSp * 0.7f).coerceAtLeast(10f)
    var style by remember(title) { mutableStateOf(baseStyle) }
    Text(
        text = title,
        style = style,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Clip,
        onTextLayout = { result ->
            val currentSp = style.fontSize.value
            if (result.hasVisualOverflow && currentSp > minSizeSp) {
                style = style.copy(fontSize = (currentSp - 0.5f).coerceAtLeast(minSizeSp).sp)
            }
        },
        modifier = modifier
    )
}

@Composable
private fun ExpressiveStepButton(
    symbol: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.88f else 1f,
        animationSpec = spring(
            stiffness = Spring.StiffnessMedium,
            dampingRatio = Spring.DampingRatioMediumBouncy
        ),
        label = "StepBtnScale"
    )
    val bgColor by animateColorAsState(
        targetValue = if (isPressed) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
        label = "StepBtnBg"
    )

    Box(
        modifier = modifier
            .size(38.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = symbol,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * Target Row weights for the horizontal segmented group (DESIGN_GUIDELINES §6.1).
 *
 * Formula (all in px, from parent constraints + memoized text widths):
 * avail = total - gap * (n - 1); equal = avail / n.
 * If the selected need (Bold label + side padding) fits into equal,
 * every weight stays 1f, i.e. exactly the old equal layout.
 * Otherwise deficit = need - equal is borrowed from neighbors proportionally to
 * each neighbor shrinkable headroom (equal - min), where min is the measured
 * width of the first 4 chars + "…" in Medium plus side padding, never a fixed dp.
 * If the deficit exceeds the total headroom, neighbors clamp at their minimums
 * and the selected segment takes the remainder (its text still Ellipsizes).
 * Returned weights are normalized so the all-equal case is exactly 1f.
 */
private fun segmentTargetWeights(
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
        minFor(label, idleStyle, segmentSidePaddingPx, textMeasurer, 4)
    }
    val shrinkable = minWidths.map { (equal - it).coerceAtLeast(0f) }
    val totalShrinkable = shrinkable.sum()
    if (totalShrinkable <= 0f) return List(n) { 1f }
    var take = minOf(selectedNeed - equal, totalShrinkable)
    var finalPx = List(n) { i ->
        if (i == selectedIndex) equal + take
        else equal - take * (shrinkable[i] / totalShrinkable)
    }
    // Aggressive second tier: if the selected label still doesn't fit,
    // squeeze neighbours down to a 2-letter minimum before giving up.
    if (finalPx[selectedIndex] < selectedNeed) {
        val tightMins = options.map { label ->
            minFor(label, idleStyle, segmentSidePaddingPx, textMeasurer, 2)
        }
        val tightShrinkable = tightMins.map { (equal - it).coerceAtLeast(0f) }
        val tightTotal = tightShrinkable.sum()
        if (tightTotal > 0f) {
            take = minOf(selectedNeed - equal, tightTotal)
            finalPx = List(n) { i ->
                if (i == selectedIndex) equal + take
                else equal - take * (tightShrinkable[i] / tightTotal)
            }
        }
    }
    val sum = finalPx.sum().coerceAtLeast(1e-6f)
    return finalPx.map { it / sum * n }
}

private fun minFor(
    label: String,
    idleStyle: TextStyle,
    segmentSidePaddingPx: Float,
    textMeasurer: TextMeasurer,
    chars: Int
): Float {
    val probe = if (label.length > chars) label.take(chars) + "…" else label
    return textMeasurer.measure(
        text = AnnotatedString(probe),
        style = idleStyle
    ).size.width.toFloat() + segmentSidePaddingPx
}

@Composable
fun RadioSelectionGroup(
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    enabled: Boolean = true,
    infoText: String? = null,
    videoResName: String? = null,
    onInfoClick: ((String, String, String?) -> Unit)? = null,
    // Horizontal segmented mode (DESIGN_GUIDELINES §6.1) is the default.
    // Pass vertical = true for the legacy vertical radio list.
    vertical: Boolean = false
) {
    val contentAlpha = if (enabled) 1f else 0.4f
    val haptics = LocalHapticFeedback.current

    Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp)) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).alpha(contentAlpha)
            )

            if (onInfoClick != null && infoText != null) {
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = { onInfoClick(title, infoText, videoResName) },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = "Info",
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        if (!vertical) {
            // Dynamic segment resize (DESIGN_GUIDELINES §6.1): equal shares while
            // the selected label fits, otherwise the selected segment borrows width
            // from its neighbors down to their measured minimums.
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .alpha(contentAlpha)
            ) {
                val density = LocalDensity.current
                val textMeasurer = rememberTextMeasurer()
                val selectedStyle = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                val idleStyle = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium)
                val segmentGapPx = with(density) { 6.dp.toPx() }
                val segmentSidePaddingPx = with(density) { 8.dp.toPx() * 2f }
                // Fixed headroom over the measured width: TextMeasurer
                // systematically underestimates the real Text layout by a few
                // glyphs, which used to end in a pointless "…" for 1-2 missing
                // letters. The buffer makes borderline labels fit in full.
                val selectedExtraPx = with(density) { 12.dp.toPx() }
                val availablePx = with(density) { maxWidth.toPx() }

                // Pure function of parent constraints + memoized text widths, so there
                // is no onSizeChanged/onTextLayout feedback and no per-frame remeasure
                // while the weight springs below settle.
                val targetWeights = remember(
                    textMeasurer, options, selectedIndex, selectedStyle, idleStyle,
                    density, availablePx, segmentGapPx, segmentSidePaddingPx,
                    selectedExtraPx
                ) {
                    segmentTargetWeights(
                        textMeasurer = textMeasurer,
                        options = options,
                        selectedIndex = selectedIndex,
                        selectedStyle = selectedStyle,
                        idleStyle = idleStyle,
                        segmentGapPx = segmentGapPx,
                        segmentSidePaddingPx = segmentSidePaddingPx,
                        selectedExtraPx = selectedExtraPx,
                        availablePx = availablePx
                    )
                }
                val animatedWeights = targetWeights.map { target ->
                    animateFloatAsState(
                        targetValue = target,
                        animationSpec = spring(
                            stiffness = Spring.StiffnessMedium,
                            dampingRatio = Spring.DampingRatioNoBouncy
                        ),
                        label = "SegmentWeight"
                    ).value
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    options.forEachIndexed { index, label ->
                        val isSelected = index == selectedIndex
                        val cornerRadius by animateDpAsState(
                            targetValue = if (isSelected) 28.dp else 12.dp,
                            animationSpec = spring(
                                stiffness = Spring.StiffnessMedium,
                                dampingRatio = Spring.DampingRatioNoBouncy
                            ),
                            label = "SegmentCorner"
                        )
                        val bgColor by animateColorAsState(
                            targetValue = if (isSelected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHighest
                            },
                            animationSpec = tween(200),
                            label = "SegmentBg"
                        )
                        val contentColor by animateColorAsState(
                            targetValue = if (isSelected) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            animationSpec = tween(200),
                            label = "SegmentContent"
                        )

                        Row(
                            modifier = Modifier
                                .weight(animatedWeights.getOrElse(index) { 1f }.coerceAtLeast(0.01f))
                                .height(46.dp)
                                .clip(RoundedCornerShape(cornerRadius))
                                .background(bgColor)
                                .border(1.dp, bgColor.edgeOutline(), RoundedCornerShape(cornerRadius))
                                .clickable(enabled = enabled) {
                                    haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                                    onSelect(index)
                                }
                                .padding(horizontal = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = contentColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        } else {
            options.forEachIndexed { index, label ->
                val isSelected = index == selectedIndex
                val targetContainerColor = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
                val backgroundColor by animateColorAsState(
                    targetContainerColor,
                    animationSpec = tween(200, easing = FastOutSlowInEasing),
                    label = "bg"
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(backgroundColor)
                        .clickable(enabled = enabled) { onSelect(index) }
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .alpha(contentAlpha),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    AnimatedVisibility(
                        visible = isSelected,
                        modifier = Modifier.padding(start = 12.dp),
                        enter = scaleIn(animationSpec = tween(220, easing = FastOutSlowInEasing)) + fadeIn(tween(180)),
                        exit = scaleOut(animationSpec = tween(160, easing = FastOutSlowInEasing)) + fadeOut(tween(140))
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsGroupCard(
    title: String,
    enabled: Boolean = true,
    titleStyle: TextStyle = MaterialTheme.typography.titleMedium,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surface.edgeOutline()),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(vertical = 12.dp)) {
            if (title.isNotBlank()) {
                Text(
                    text = title,
                    style = titleStyle,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp).alpha(if (enabled) 1f else 0.5f)
                )
            }
            content()
        }
    }
}

@Composable
fun RestartLauncherButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        )
    ) {
        Icon(Icons.Rounded.Refresh, null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.btn_restart_launcher))
    }
}

@Composable
fun FloatInputDialog(
    title: String,
    initialValue: Float,
    rangeStart: Float,
    rangeEnd: Float,
    unit: String,
    isInteger: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Float) -> Unit,
    onDefault: (() -> Unit)? = null
) {
    val initText = if (isInteger) initialValue.toInt().toString() else initialValue.toString()
    var text by remember { mutableStateOf(initText) }
    var isError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.common_range_format, rangeStart.toInt(), rangeEnd.toInt()),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = {
                        text = it
                        val num = it.toFloatOrNull()
                        isError = num == null || num < rangeStart || num > rangeEnd
                    },
                    label = { Text(stringResource(R.string.common_input_label)) },
                    isError = isError,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    suffix = { Text(unit) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Row {
                if (onDefault != null) {
                    TextButton(onClick = onDefault) { Text(stringResource(R.string.btn_default)) }
                }
                Button(
                    onClick = {
                        val num = text.toFloatOrNull()
                        if (num != null && num >= rangeStart && num <= rangeEnd) {
                            onConfirm(num)
                        } else {
                            isError = true
                        }
                    }
                ) { Text(stringResource(R.string.btn_apply)) }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_cancel)) }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    )
}

@Composable
fun InfoDialog(
    title: String,
    text: String,
    videoResName: String? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 24.dp)
                .verticalScroll(scrollState)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss 
                ),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 6.dp,
                shadowElevation = 8.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clickable(enabled = false) {}
            ) {
                Column(
                    modifier = Modifier
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Icon(Icons.Outlined.Info, null, tint = MaterialTheme.colorScheme.secondary)
                        IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                            Icon(
                                Icons.Default.Close,
                                stringResource(R.string.btn_close),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))

                    if (videoResName != null) {
                        var videoRatio by remember { mutableFloatStateOf(16f / 9f) }
                        var currentUri by remember { mutableStateOf<Uri?>(null) }
                        var isLoading by remember { mutableStateOf(true) }
                        var isError by remember { mutableStateOf(false) }
                        
                        var reloadKey by remember { mutableIntStateOf(0) }
                        var isNetworkSource by remember { mutableStateOf(false) }

                        val rawBase = stringResource(R.string.url_github_raw_base)
                        val repo = stringResource(R.string.url_github_repo)
                        val branch = stringResource(R.string.url_github_branch)
                        val videoPrefix = stringResource(R.string.url_video_sample_path_prefix)
                        val videoSuffix = stringResource(R.string.url_video_sample_path_suffix)

                        LaunchedEffect(videoResName, reloadKey) {
                            isLoading = true
                            isError = false
                            
                            val webUrl = "$rawBase/$repo/refs/heads/$branch$videoPrefix$videoResName$videoSuffix"
                            
                            
                            val resId = context.resources.getIdentifier(videoResName, "raw", context.packageName)
                            if (resId != 0) {
                                currentUri = Uri.parse("android.resource://${context.packageName}/$resId")
                                isNetworkSource = false
                                isLoading = false
                                return@LaunchedEffect
                            }

                            isNetworkSource = true
                            
                            // System_ext app: cache videos on external cache when available.
                            val extCache = context.externalCacheDir
                            val targetDir = extCache ?: context.cacheDir
                            
                            val cacheFile = File(targetDir, "$videoResName.mp4")

                            try {
                                withContext(Dispatchers.IO) {
                                    val fileExists = cacheFile.exists()
                                    val fileLength = cacheFile.length()

                                    if (!fileExists || fileLength == 0L || reloadKey > 0) {
                                        if (fileExists) cacheFile.delete()

                                        val url = java.net.URL(webUrl)
                                        val connection = url.openConnection() as java.net.HttpURLConnection
                                        connection.connectTimeout = 15000
                                        connection.readTimeout = 15000
                                        connection.instanceFollowRedirects = true
                                        connection.connect()

                                        if (connection.responseCode != 200) {
                                            throw Exception("HTTP Failed: ${connection.responseCode}")
                                        }

                                        connection.inputStream.use { input ->
                                            cacheFile.outputStream().use { output ->
                                                input.copyTo(output)
                                            }
                                        }
                                        
                                        cacheFile.setReadable(true, false)
                                    }
                                    
                                    if (cacheFile.exists() && cacheFile.length() > 0) {
                                        currentUri = Uri.fromFile(cacheFile)
                                    } else {
                                        throw Exception("Verification failed")
                                    }
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                                isError = true 
                            } finally {
                                isLoading = false
                            }
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(videoRatio)
                                .clip(RoundedCornerShape(24.dp))
                                .background(if (isLoading || isError || currentUri == null) Color.Black else Color.Transparent),
                            contentAlignment = Alignment.Center
                        ) {
                            if (currentUri != null && !isError) {
                                AndroidView(
                                    factory = { ctx ->
                                        VideoView(ctx).apply {
                                            setBackgroundColor(android.graphics.Color.TRANSPARENT)
                                            clipToOutline = true
                                            outlineProvider = object : android.view.ViewOutlineProvider() {
                                                override fun getOutline(view: android.view.View, outline: android.graphics.Outline) {
                                                    outline.setRoundRect(0, 0, view.width, view.height, 24.dp.value * ctx.resources.displayMetrics.density)
                                                }
                                            }

                                            setOnPreparedListener { mp ->
                                                mp.isLooping = true
                                                mp.setVideoScalingMode(android.media.MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT)
                                                
                                                if (mp.videoWidth > 0 && mp.videoHeight > 0) {
                                                    videoRatio = mp.videoWidth.toFloat() / mp.videoHeight.toFloat()
                                                }
                                                start()
                                            }
                                            setOnErrorListener { _, _, _ ->
                                                isError = true
                                                true
                                            }
                                        }
                                    },
                                    update = { videoView ->
                                        if (currentUri != null && !videoView.isPlaying) {
                                                videoView.setVideoURI(currentUri)
                                        }
                                    },
                                    modifier = Modifier.matchParentSize()
                                )
                            }
                            
                            if (isError) {
                                Text(stringResource(R.string.status_error), color = Color.Red)
                            }

                            if (isLoading) {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            }

                            if (isNetworkSource && !isLoading) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(12.dp)
                                ) {
                                    Surface(
                                        onClick = { reloadKey++ },
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.7f),
                                        modifier = Modifier.size(44.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = androidx.compose.material.icons.Icons.Default.Refresh,
                                                contentDescription = "Refresh",
                                                tint = MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                    }

                    Text(
                        text = title, 
                        style = MaterialTheme.typography.titleLarge, 
                        fontSize = 18.sp, 
                        textAlign = TextAlign.Start, 
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = text, 
                        style = MaterialTheme.typography.bodyMedium, 
                        textAlign = TextAlign.Start, 
                        color = MaterialTheme.colorScheme.onSurfaceVariant, 
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
fun WeakDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        modifier = modifier.padding(horizontal = 20.dp)
    )
}

@Composable
fun StrongDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant,
        thickness = 1.dp,
        modifier = modifier.padding(horizontal = 20.dp, vertical = 8.dp)
    )
}

@Composable
fun ExpandableSettingsGroupCard(
    title: String,
    enabled: Boolean = true,
    expanded: Boolean = false,
    onExpandChange: (Boolean) -> Unit,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    content: @Composable ColumnScope.() -> Unit
) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "arrow_rotation",
        animationSpec = tween(300, easing = FastOutSlowInEasing)
    )

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) containerColor else containerColor.copy(alpha = 0.6f)
        ),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessHigh
                )
            )
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onExpandChange(!expanded) }
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (enabled) contentColor else contentColor.copy(alpha = 0.5f)
                )

                Icon(
                    imageVector = Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.padding(start = 8.dp).rotate(rotation),
                    tint = if (enabled) contentColor else contentColor.copy(alpha = 0.5f)
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(animationSpec = tween(240, easing = FastOutSlowInEasing)) + fadeIn(tween(180)),
                exit = shrinkVertically(animationSpec = tween(200, easing = FastOutSlowInEasing)) + fadeOut(tween(140))
            ) {
                Column(modifier = Modifier.padding(bottom = 16.dp)) {
                    content()
                }
            }
        }
    }
}



