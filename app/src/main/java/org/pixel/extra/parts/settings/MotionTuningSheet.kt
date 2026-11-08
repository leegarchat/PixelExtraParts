package org.pixel.extra.parts.settings

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.pixel.extra.parts.R
import org.pixel.extra.parts.dock.DockEntry
import org.pixel.extra.parts.motion.MotionSettings
import org.pixel.extra.parts.ui.SettingSlider
import org.pixel.extra.parts.ui.edgeOutline
import kotlinx.coroutines.launch

// Custom bottom sheet with the full motion-tuning panel: every phase of the
// float-bar transformation is adjustable live, with per-slider reset.
//
// Architecture (replaces ModalBottomSheet):
// - The sheet container is FIXED: it never moves with content. Only the inner
//   LazyColumn scrolls, so scrolling sliders cannot move or dismiss the sheet.
// - Max height = screen height - status bar - 64.dp top bar - 16.dp margin, so
//   the sheet top edge always stays below the top bar. Anchored bottom.
// - Dismiss ONLY via the header drag-handle (drag down past threshold/fling)
//   or the system back button (BackHandler). Scrim taps are eaten but never
//   dismiss.
// - Spring slide-from-bottom for the sheet + fade for the scrim, both ways:
//   enter runs on launch, exit runs inside requestDismiss() before onDismiss().
@Composable
fun MotionTuningSheet(
    motion: MotionSettings,
    tabs: List<DockEntry>,
    onUpdate: (MotionSettings) -> Unit,
    onDismiss: () -> Unit,
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val dismissThresholdPx = with(density) { 120.dp.toPx() }
    val flingThresholdPxPerSec = 900f

    // Slide offset of the sheet in px (0 = fully shown) + scrim alpha.
    val slidePx = remember { Animatable(1600f) }
    val scrimAlpha = remember { Animatable(0f) }
    // Header-only drag offset in px (downwards only, 0 = resting).
    val dragPx = remember { Animatable(0f) }
    var sheetHpx by remember { mutableIntStateOf(0) }
    var dismissing by remember { mutableStateOf(false) }

    fun requestDismiss() {
        if (dismissing) return
        dismissing = true
        scope.launch {
            // Fold the current header drag into the slide so there is no jump,
            // then spring the whole sheet below the screen while fading scrim.
            val startOffset = slidePx.value + dragPx.value
            slidePx.snapTo(startOffset)
            dragPx.snapTo(0f)
            val target = sheetHpx.toFloat().coerceAtLeast(startOffset).coerceAtLeast(800f)
            val slide = launch {
                slidePx.animateTo(
                    target,
                    animationSpec = spring(
                        stiffness = 380f,
                        dampingRatio = 0.85f,
                    ),
                )
            }
            val scrim = launch { scrimAlpha.animateTo(0f, animationSpec = tween(220)) }
            slide.join()
            scrim.join()
            onDismiss()
        }
    }

    BackHandler(onBack = ::requestDismiss)

    LaunchedEffect(Unit) {
        launch {
            slidePx.animateTo(
                0f,
                animationSpec = spring(
                    stiffness = 380f,
                    dampingRatio = 0.85f,
                ),
            )
        }
        launch { scrimAlpha.animateTo(0.45f, animationSpec = tween(240)) }
    }

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val sheetMaxH = (maxHeight - statusTop - 64.dp - 16.dp).coerceAtLeast(240.dp)
        val sheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        val sheetColor = MaterialTheme.colorScheme.surfaceContainerHigh
        val scrimColor = MaterialTheme.colorScheme.scrim

        // Scrim: eats taps (blocks the content behind) but never dismisses.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(scrimColor.copy(alpha = scrimAlpha.value))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = sheetMaxH)
                .onPlaced { sheetHpx = it.size.height }
                .graphicsLayer { translationY = slidePx.value + dragPx.value }
                .clip(sheetShape)
                .background(sheetColor)
                .border(1.dp, sheetColor.edgeOutline(), sheetShape),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = sheetMaxH),
            ) {
                SheetHeader(
                    onDismissDrag = { dragAmount ->
                        scope.launch {
                            dragPx.snapTo((dragPx.value + dragAmount).coerceAtLeast(0f))
                        }
                    },
                    onDragEnd = { velocity ->
                        scope.launch {
                            if (dragPx.value > dismissThresholdPx ||
                                (dragPx.value > dismissThresholdPx * 0.4f &&
                                    velocity > flingThresholdPxPerSec)
                            ) {
                                requestDismiss()
                            } else {
                                dragPx.animateTo(
                                    0f,
                                    animationSpec = spring(
                                        stiffness = Spring.StiffnessMedium,
                                        dampingRatio = 0.75f,
                                    ),
                                )
                            }
                        }
                    },
                    onDragCancel = {
                        scope.launch {
                            dragPx.animateTo(
                                0f,
                                animationSpec = spring(
                                    stiffness = Spring.StiffnessMedium,
                                    dampingRatio = 0.75f,
                                ),
                            )
                        }
                    },
                    onResetAll = { onUpdate(MotionSettings()) },
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 64.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    tuningGroup(R.string.g_timing) {
                        tuningSlider(
                            titleRes = R.string.s_expand_ms,
                            value = motion.expandMs.toFloat(),
                            range = 0f..10000f,
                            step = 10f,
                            default = 450f,
                            label = { v -> stringResource(R.string.fmt_ms, v.toInt()) },
                            onChange = { onUpdate(motion.copy(expandMs = it.toInt())) },
                        )
                        tuningSlider(
                            titleRes = R.string.s_collapse_ms,
                            value = motion.collapseMs.toFloat(),
                            range = 0f..10000f,
                            step = 10f,
                            default = 380f,
                            label = { v -> stringResource(R.string.fmt_ms, v.toInt()) },
                            onChange = { onUpdate(motion.copy(collapseMs = it.toInt())) },
                        )
                    }

                    tuningGroup(R.string.g_capsule) {
                        tuningSlider(
                            titleRes = R.string.s_width_end,
                            value = motion.widthPhaseEnd * 100f,
                            range = 0f..100f,
                            step = 1f,
                            default = 38f,
                            label = { v -> stringResource(R.string.fmt_pct, v.toInt()) },
                            onChange = { onUpdate(motion.copy(widthPhaseEnd = it / 100f)) },
                        )
                        tuningSlider(
                            titleRes = R.string.s_height_start,
                            value = motion.heightPhaseStart * 100f,
                            range = 0f..100f,
                            step = 1f,
                            default = 3f,
                            label = { v -> stringResource(R.string.fmt_pct, v.toInt()) },
                            onChange = { onUpdate(motion.copy(heightPhaseStart = it / 100f)) },
                        )
                        tuningSlider(
                            titleRes = R.string.s_pulse,
                            value = motion.expansionPulsePct,
                            range = 0f..20f,
                            step = 0.25f,
                            default = 1.5f,
                            label = { v -> stringResource(R.string.fmt_pct2, v) },
                            onChange = { onUpdate(motion.copy(expansionPulsePct = it)) },
                        )
                        tuningSlider(
                            titleRes = R.string.s_press,
                            value = motion.pressScalePct,
                            range = 100f..150f,
                            step = 0.5f,
                            default = 103f,
                            label = { v -> stringResource(R.string.fmt_pct2, v) },
                            onChange = { onUpdate(motion.copy(pressScalePct = it)) },
                        )
                        tuningSlider(
                            titleRes = R.string.s_bottom_lift,
                            value = motion.bottomLiftDp,
                            range = 0f..300f,
                            step = 1f,
                            default = 0f,
                            label = { v -> stringResource(R.string.fmt_dp, v.toInt()) },
                            onChange = { onUpdate(motion.copy(bottomLiftDp = it)) },
                        )
                        tuningSlider(
                            titleRes = R.string.s_compact_scale,
                            value = motion.compactScalePct,
                            range = 50f..150f,
                            step = 1f,
                            default = 100f,
                            label = { v -> stringResource(R.string.fmt_pct, v.toInt()) },
                            onChange = { onUpdate(motion.copy(compactScalePct = it)) },
                        )
                        tuningSlider(
                            titleRes = R.string.s_blur,
                            value = motion.blurRadiusPx,
                            range = 0f..80f,
                            step = 1f,
                            default = 40f,
                            label = { v -> stringResource(R.string.fmt_px, v.toInt()) },
                            onChange = { onUpdate(motion.copy(blurRadiusPx = it)) },
                        )
                        TapEaterSwitchRow(
                            checked = motion.stripTapEater,
                            onCheckedChange = { onUpdate(motion.copy(stripTapEater = it)) },
                        )
                    }

                    tuningGroup(R.string.g_flight) {
                        // Queue of delays: one gap slider per element after the first.
                        tabs.drop(1).forEachIndexed { k, tab ->
                            val gap = motion.flightGaps.getOrElse(k) {
                                motion.flightGaps.lastOrNull() ?: 0.14f
                            }
                            SettingSlider(
                                title = stringResource(R.string.fmt_gap, stringResource(tab.labelRes)),
                                value = gap * 100f,
                                onValueChange = { v ->
                                    val updated = motion.flightGaps.toMutableList()
                                    while (updated.size <= k) updated.add(updated.lastOrNull() ?: 0.14f)
                                    updated[k] = v / 100f
                                    onUpdate(motion.copy(flightGaps = updated))
                                },
                                valueRange = 0f..100f,
                                step = 1f,
                                defaultValue = 14f,
                                resetLabel = stringResource(R.string.motion_reset),
                                valueLabel = { v -> stringResource(R.string.fmt_pct, v.toInt()) },
                            )
                        }
                        tuningSlider(
                            titleRes = R.string.s_span,
                            value = motion.flightSpan * 100f,
                            range = 1f..100f,
                            step = 1f,
                            default = 74f,
                            label = { v -> stringResource(R.string.fmt_pct, v.toInt()) },
                            onChange = { onUpdate(motion.copy(flightSpan = it / 100f)) },
                        )
                        tuningSlider(
                            titleRes = R.string.s_bulge_base,
                            value = motion.arcBulgeBaseDp,
                            range = 0f..300f,
                            step = 1f,
                            default = 26f,
                            label = { v -> stringResource(R.string.fmt_dp, v.toInt()) },
                            onChange = { onUpdate(motion.copy(arcBulgeBaseDp = it)) },
                        )
                        tuningSlider(
                            titleRes = R.string.s_bulge_step,
                            value = motion.arcBulgeStepDp,
                            range = 0f..200f,
                            step = 1f,
                            default = 12f,
                            label = { v -> stringResource(R.string.fmt_dp, v.toInt()) },
                            onChange = { onUpdate(motion.copy(arcBulgeStepDp = it)) },
                        )
                        tuningSlider(
                            titleRes = R.string.s_width_start,
                            value = motion.widthMorphStart * 100f,
                            range = 0f..100f,
                            step = 1f,
                            default = 10f,
                            label = { v -> stringResource(R.string.fmt_pct, v.toInt()) },
                            onChange = { onUpdate(motion.copy(widthMorphStart = it / 100f)) },
                        )
                        tuningSlider(
                            titleRes = R.string.s_label_start,
                            value = motion.labelRevealStart * 100f,
                            range = 0f..100f,
                            step = 1f,
                            default = 12f,
                            label = { v -> stringResource(R.string.fmt_pct, v.toInt()) },
                            onChange = { onUpdate(motion.copy(labelRevealStart = it / 100f)) },
                        )
                        tuningSlider(
                            titleRes = R.string.s_label_end,
                            value = motion.labelRevealEnd * 100f,
                            range = 0f..100f,
                            step = 1f,
                            default = 78f,
                            label = { v -> stringResource(R.string.fmt_pct, v.toInt()) },
                            onChange = { onUpdate(motion.copy(labelRevealEnd = it / 100f)) },
                        )
                        tuningSlider(
                            titleRes = R.string.s_row_corner,
                            value = motion.rowCornerDp,
                            range = 0f..64f,
                            step = 1f,
                            default = 16f,
                            label = { v -> stringResource(R.string.fmt_dp, v.toInt()) },
                            onChange = { onUpdate(motion.copy(rowCornerDp = it)) },
                        )
                    }

                    tuningGroup(R.string.g_toggle) {
                        tuningSlider(
                            titleRes = R.string.s_text_start,
                            value = motion.collapseTextStart * 100f,
                            range = 0f..100f,
                            step = 1f,
                            default = 14f,
                            label = { v -> stringResource(R.string.fmt_pct, v.toInt()) },
                            onChange = { onUpdate(motion.copy(collapseTextStart = it / 100f)) },
                        )
                        tuningSlider(
                            titleRes = R.string.s_text_end,
                            value = motion.collapseTextEnd * 100f,
                            range = 0f..100f,
                            step = 1f,
                            default = 76f,
                            label = { v -> stringResource(R.string.fmt_pct, v.toInt()) },
                            onChange = { onUpdate(motion.copy(collapseTextEnd = it / 100f)) },
                        )
                        tuningSlider(
                            titleRes = R.string.s_cap_start,
                            value = motion.capsuleFadeStart * 100f,
                            range = 0f..100f,
                            step = 1f,
                            default = 8f,
                            label = { v -> stringResource(R.string.fmt_pct, v.toInt()) },
                            onChange = { onUpdate(motion.copy(capsuleFadeStart = it / 100f)) },
                        )
                        tuningSlider(
                            titleRes = R.string.s_cap_end,
                            value = motion.capsuleFadeEnd * 100f,
                            range = 0f..100f,
                            step = 1f,
                            default = 45f,
                            label = { v -> stringResource(R.string.fmt_pct, v.toInt()) },
                            onChange = { onUpdate(motion.copy(capsuleFadeEnd = it / 100f)) },
                        )
                        tuningSlider(
                            titleRes = R.string.s_morph_start,
                            value = motion.iconMorphStart * 100f,
                            range = 0f..100f,
                            step = 1f,
                            default = 4f,
                            label = { v -> stringResource(R.string.fmt_pct, v.toInt()) },
                            onChange = { onUpdate(motion.copy(iconMorphStart = it / 100f)) },
                        )
                        tuningSlider(
                            titleRes = R.string.s_morph_end,
                            value = motion.iconMorphEnd * 100f,
                            range = 0f..100f,
                            step = 1f,
                            default = 64f,
                            label = { v -> stringResource(R.string.fmt_pct, v.toInt()) },
                            onChange = { onUpdate(motion.copy(iconMorphEnd = it / 100f)) },
                        )
                    }

                    tuningGroup(R.string.g_reveal) {
                        tuningSlider(
                            titleRes = R.string.s_overshoot,
                            value = motion.revealOvershoot,
                            range = 0f..3f,
                            step = 0.05f,
                            default = 0.85f,
                            label = { v -> stringResource(R.string.fmt_coef, v) },
                            onChange = { onUpdate(motion.copy(revealOvershoot = it)) },
                        )
                        tuningSlider(
                            titleRes = R.string.s_slide,
                            value = motion.riseOffsetDp,
                            range = 0f..100f,
                            step = 1f,
                            default = 10f,
                            label = { v -> stringResource(R.string.fmt_dp, v.toInt()) },
                            onChange = { onUpdate(motion.copy(riseOffsetDp = it)) },
                        )
                        tuningSlider(
                            titleRes = R.string.s_div_start,
                            value = motion.dividerFadeStart * 100f,
                            range = 0f..100f,
                            step = 1f,
                            default = 35f,
                            label = { v -> stringResource(R.string.fmt_pct, v.toInt()) },
                            onChange = { onUpdate(motion.copy(dividerFadeStart = it / 100f)) },
                        )
                        tuningSlider(
                            titleRes = R.string.s_div_end,
                            value = motion.dividerFadeEnd * 100f,
                            range = 0f..100f,
                            step = 1f,
                            default = 68f,
                            label = { v -> stringResource(R.string.fmt_pct, v.toInt()) },
                            onChange = { onUpdate(motion.copy(dividerFadeEnd = it / 100f)) },
                        )
                    }
                }
            }
        }
    }
}

// Fixed header: drag handle + title + reset-all. This is the ONLY region whose
// vertical drag moves/dismisses the sheet; the LazyColumn below scrolls
// independently and never affects the sheet offset.
@Composable
private fun SheetHeader(
    onDismissDrag: (dragAmount: Float) -> Unit,
    onDragEnd: (velocityPxPerSec: Float) -> Unit,
    onDragCancel: () -> Unit,
    onResetAll: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                val tracker = VelocityTracker()
                detectVerticalDragGestures(
                    onDragStart = { tracker.resetTracking() },
                    onVerticalDrag = { change, dragAmount ->
                        tracker.addPosition(change.uptimeMillis, change.position)
                        onDismissDrag(dragAmount)
                        change.consume()
                    },
                    onDragEnd = {
                        onDragEnd(tracker.calculateVelocity().y)
                    },
                    onDragCancel = { onDragCancel() },
                )
            }
            .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 4.dp),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .width(40.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.motion_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            FilledTonalButton(
                onClick = onResetAll,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.secondaryContainer.edgeOutline(),
                ),
            ) {
                Text(text = stringResource(R.string.motion_reset_all))
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
    }
}

// Simple M3 Switch row for the strip tap-catcher toggle, with haptic on flip.
// Rendered on a level-2 nested card so the control reads as its own surface
// inside the group card.
@Composable
private fun TapEaterSwitchRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.surfaceContainer.edgeOutline(),
                shape = shape,
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.s_tap_eater),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onCheckedChange(it)
            },
        )
    }
}

// One group = one level-1 card: rounded container, adaptive 1.dp outline,
// internal 14.dp padding, 12.dp between controls and a primary group title.
private fun LazyListScope.tuningGroup(
    @StringRes titleRes: Int,
    content: @Composable ColumnScope.() -> Unit,
) {
    item(key = "group_$titleRes") {
        val shape = RoundedCornerShape(20.dp)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.surfaceContainerLow.edgeOutline(),
                    shape = shape,
                )
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(titleRes),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            content()
        }
    }
}

@Composable
private fun tuningSlider(
    @StringRes titleRes: Int,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    step: Float,
    default: Float,
    label: @Composable (Float) -> String,
    onChange: (Float) -> Unit,
) {
    SettingSlider(
        title = stringResource(titleRes),
        value = value,
        onValueChange = onChange,
        valueRange = range,
        step = step,
        defaultValue = default,
        resetLabel = stringResource(R.string.motion_reset),
        valueLabel = label,
    )
}
