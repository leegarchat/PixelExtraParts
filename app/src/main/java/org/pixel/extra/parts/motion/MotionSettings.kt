package org.pixel.extra.parts.motion

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

// Every tunable knob of the float-bar transformation, in one parcelized snapshot.
// Main timing is real milliseconds (toolbar-driven tween); phase windows are
// 0..1 fractions of the unified progress; sizes are dp.
@Parcelize
data class MotionSettings(
    // Timing.
    val expandMs: Int = 450,
    val collapseMs: Int = 380,
    // Capsule.
    val widthPhaseEnd: Float = 0.38f,
    val heightPhaseStart: Float = 0.03f,
    val expansionPulsePct: Float = 1.5f,
    val pressScalePct: Float = 103f,
    // Flight of pinned tabs along arcs (queue of per-element delays).
    val flightGaps: List<Float> = listOf(0.14f, 0.14f, 0.14f),
    val flightSpan: Float = 0.74f,
    val arcBulgeBaseDp: Float = 26f,
    val arcBulgeStepDp: Float = 12f,
    val widthMorphStart: Float = 0.10f,
    val labelRevealStart: Float = 0.12f,
    val labelRevealEnd: Float = 0.78f,
    val rowCornerDp: Float = 16f,
    // Burger -> collapse capsule.
    val collapseTextStart: Float = 0.14f,
    val collapseTextEnd: Float = 0.76f,
    val capsuleFadeStart: Float = 0.08f,
    val capsuleFadeEnd: Float = 0.45f,
    val iconMorphStart: Float = 0.04f,
    val iconMorphEnd: Float = 0.64f,
    // Overflow list reveal.
    val revealOvershoot: Float = 0.85f,
    val riseOffsetDp: Float = 10f,
    val dividerFadeStart: Float = 0.35f,
    val dividerFadeEnd: Float = 0.68f,
    // Placement of the whole dock above the navigation bar.
    val bottomLiftDp: Float = 0f,
    // Compact physical scale of the unexpanded bar, in percent (100 = identity).
    val compactScalePct: Float = 100f,
    // Shared blur radius for every frosted surface, in px.
    val blurRadiusPx: Float = 40f,
    // Outer strip tap-catcher around/below the bar eats taps (true) or lets
    // them fall through to the list behind (false).
    val stripTapEater: Boolean = true,
) : Parcelable
