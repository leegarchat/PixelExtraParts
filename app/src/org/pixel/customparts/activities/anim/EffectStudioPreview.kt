package org.pixel.customparts.activities

import android.content.Context
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationSet
import android.view.animation.AnimationUtils
import android.view.animation.Transformation
import android.widget.FrameLayout
import android.widget.TextView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.pixel.customparts.ui.edgeOutline
import kotlin.math.roundToLong

// ── Reusable Looped Effect Preview Player (Real-Time Speed Scaling) ──────

private const val STUDIO_PREVIEW_START_DELAY = 320.0
private const val STUDIO_PREVIEW_HOLD_DELAY = 580.0

/**
 * Wraps an Android XML [Animation] so that changing [speedProvider] mid-flight
 * immediately scales the rate of virtual time progression on the very next frame
 * without restarting or waiting for the next loop cycle.
 */
private class RealtimeSpeedAnimation(
    private val delegate: Animation,
    private val speedProvider: () -> Float
) : Animation() {
    private var lastRealTimeMs: Long = -1L
    var virtualTimeMs: Double = 0.0
        private set
    val baseTotalDurationMs: Long = computeMaxAnimDuration(delegate).coerceAtLeast(50L)
    var isFinished: Boolean = false
        private set

    init {
        fillAfter = true
        fillBefore = true
    }

    override fun initialize(width: Int, height: Int, parentWidth: Int, parentHeight: Int) {
        super.initialize(width, height, parentWidth, parentHeight)
        delegate.fillAfter = true
        delegate.fillBefore = true
        delegate.initialize(width, height, parentWidth, parentHeight)
        delegate.startTime = 0L
        lastRealTimeMs = -1L
        virtualTimeMs = 0.0
        isFinished = false
    }

    private fun advanceVirtualTime(currentTime: Long): Long {
        if (lastRealTimeMs < 0L) {
            lastRealTimeMs = currentTime
        } else {
            val dt = (currentTime - lastRealTimeMs).coerceIn(0L, 100L)
            lastRealTimeMs = currentTime
            val factor = speedProvider().coerceIn(0.1f, 10f).toDouble()
            virtualTimeMs += dt.toDouble() * factor
        }
        return virtualTimeMs.roundToLong()
    }

    override fun getTransformation(currentTime: Long, outTransformation: Transformation): Boolean {
        val vTime = advanceVirtualTime(currentTime)
        val more = delegate.getTransformation(vTime, outTransformation)
        val active = more && vTime < baseTotalDurationMs
        if (!active) isFinished = true
        return active
    }

    override fun getTransformation(
        currentTime: Long,
        outTransformation: Transformation,
        scale: Float
    ): Boolean {
        val vTime = advanceVirtualTime(currentTime)
        val more = delegate.getTransformation(vTime, outTransformation, scale)
        val active = more && vTime < baseTotalDurationMs
        if (!active) isFinished = true
        return active
    }
}

private enum class PreviewLoopPhase {
    START_WAIT,
    PLAYING,
    HOLD_WAIT
}

private data class StudioPreviewViews(
    val frame: FrameLayout,
    val screenA: TextView,
    val screenB: TextView,
    @Volatile var speedMultiplier: Float = 1f,
    @Volatile var step2SpeedMultiplier: Float = 1f,
    var attachedToken: Int = 0
)

@Composable
fun StudioLoopPreview(
    spec: EffectLoopSpec,
    modifier: Modifier = Modifier,
    width: Dp = 110.dp,
    height: Dp = 190.dp,
    cornerRadius: Dp = 12.dp,
    speedMultiplier: Float = 1f,
    step2SpeedMultiplier: Float = speedMultiplier
) {
    val shape = RoundedCornerShape(cornerRadius)
    val bgColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val screenABg = MaterialTheme.colorScheme.primary.toArgb()
    val screenAFg = MaterialTheme.colorScheme.onPrimary.toArgb()
    val screenBBg = MaterialTheme.colorScheme.tertiaryContainer.toArgb()
    val screenBFg = MaterialTheme.colorScheme.onTertiaryContainer.toArgb()

    Card(
        shape = shape,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        border = BorderStroke(1.dp, bgColor.edgeOutline()),
        modifier = modifier.size(width = width, height = height)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(shape)
        ) {
            AndroidView(
                factory = { ctx ->
                    createStudioLoopPreviewFrame(
                        ctx, spec, speedMultiplier, step2SpeedMultiplier,
                        screenABg, screenAFg, screenBBg, screenBFg
                    )
                },
                update = { frame ->
                    val holder = frame.tag as? StudioPreviewViews
                    if (holder != null) {
                        holder.speedMultiplier = speedMultiplier.coerceIn(0.1f, 10f)
                        holder.step2SpeedMultiplier = step2SpeedMultiplier.coerceIn(0.1f, 10f)
                        holder.screenA.setBackgroundColor(screenABg)
                        holder.screenA.setTextColor(screenAFg)
                        holder.screenB.setBackgroundColor(screenBBg)
                        holder.screenB.setTextColor(screenBFg)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

private fun createStudioLoopPreviewFrame(
    viewCtx: Context,
    spec: EffectLoopSpec,
    initialSpeed: Float,
    initialStep2Speed: Float,
    screenABg: Int,
    screenAFg: Int,
    screenBBg: Int,
    screenBFg: Int
): FrameLayout {
    val holder = createStudioPreviewViews(
        viewCtx, initialSpeed, initialStep2Speed,
        screenABg, screenAFg, screenBBg, screenBFg
    )
    holder.frame.tag = holder

    holder.frame.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) {
            holder.attachedToken++
            val token = holder.attachedToken
            v.post {
                startStudioPreviewRealtimeTicker(viewCtx, holder, spec, token)
            }
        }

        override fun onViewDetachedFromWindow(v: View) {
            holder.attachedToken++
            holder.screenA.clearAnimation()
            holder.screenB.clearAnimation()
        }
    })

    holder.frame.post {
        if (holder.frame.isAttachedToWindow) {
            holder.attachedToken++
            startStudioPreviewRealtimeTicker(viewCtx, holder, spec, holder.attachedToken)
        }
    }
    return holder.frame
}

private fun createStudioPreviewViews(
    viewCtx: Context,
    initialSpeed: Float,
    initialStep2Speed: Float,
    screenABg: Int,
    screenAFg: Int,
    screenBBg: Int,
    screenBFg: Int
): StudioPreviewViews {
    val frame = FrameLayout(viewCtx).apply {
        clipChildren = true
        clipToPadding = true
    }
    val screenA = TextView(viewCtx).apply {
        text = "A"
        textSize = 26f
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        setBackgroundColor(screenABg)
        setTextColor(screenAFg)
    }
    val screenB = TextView(viewCtx).apply {
        text = "B"
        textSize = 26f
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        setBackgroundColor(screenBBg)
        setTextColor(screenBFg)
        visibility = View.INVISIBLE
    }
    frame.addView(
        screenA,
        FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
    )
    frame.addView(
        screenB,
        FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
    )
    return StudioPreviewViews(
        frame = frame,
        screenA = screenA,
        screenB = screenB,
        speedMultiplier = initialSpeed.coerceIn(0.1f, 10f),
        step2SpeedMultiplier = initialStep2Speed.coerceIn(0.1f, 10f)
    )
}

private fun startStudioPreviewRealtimeTicker(
    viewCtx: Context,
    holder: StudioPreviewViews,
    spec: EffectLoopSpec,
    token: Int
) {
    val resolvedCtx: Context
    val s1Exit: Int
    val s1Enter: Int
    val s2Exit: Int
    val s2Enter: Int

    val pkg = spec.themePackage
    if (pkg != null) {
        try {
            val themeCtx = viewCtx.createPackageContext(pkg, Context.CONTEXT_IGNORE_SECURITY)
            val res = themeCtx.resources
            val e1 = res.getIdentifier(spec.step1ExitName, "anim", pkg)
            val n1 = res.getIdentifier(spec.step1EnterName, "anim", pkg)
            val e2 = res.getIdentifier(spec.step2ExitName, "anim", pkg).takeIf { it != 0 } ?: e1
            val n2 = res.getIdentifier(spec.step2EnterName, "anim", pkg).takeIf { it != 0 } ?: n1
            resolvedCtx = themeCtx
            s1Exit = e1
            s1Enter = n1
            s2Exit = e2
            s2Enter = n2
        } catch (_: Exception) {
            holder.screenA.visibility = View.VISIBLE
            holder.screenB.visibility = View.INVISIBLE
            return
        }
    } else {
        resolvedCtx = viewCtx
        s1Exit = spec.step1ExitRes
        s1Enter = spec.step1EnterRes
        s2Exit = spec.step2ExitRes
        s2Enter = spec.step2EnterRes
    }

    var phaseAtoB = spec.directionMode != LoopDirectionMode.STEP2_ONLY
    var loopPhase = PreviewLoopPhase.START_WAIT
    var phaseVirtualElapsed = 0.0
    var lastTickMs = SystemClock.uptimeMillis()
    var outAnim: RealtimeSpeedAnimation? = null
    var inAnim: RealtimeSpeedAnimation? = null

    fun currentSpeed(): Float =
        if (phaseAtoB) holder.speedMultiplier else holder.step2SpeedMultiplier

    fun prepareWaitPhase() {
        val outgoing = if (phaseAtoB) holder.screenA else holder.screenB
        val incoming = if (phaseAtoB) holder.screenB else holder.screenA
        resetStudioPreviewView(outgoing)
        resetStudioPreviewView(incoming)
        outgoing.visibility = View.VISIBLE
        incoming.visibility = View.INVISIBLE
        outAnim = null
        inAnim = null
        phaseVirtualElapsed = 0.0
        loopPhase = PreviewLoopPhase.START_WAIT
    }

    prepareWaitPhase()

    val runnable = object : Runnable {
        override fun run() {
            val frame = holder.frame
            if (!frame.isAttachedToWindow || holder.attachedToken != token) return

            val now = SystemClock.uptimeMillis()
            val dt = (now - lastTickMs).coerceIn(0L, 64L).toDouble()
            lastTickMs = now
            val speedFactor = currentSpeed().coerceIn(0.1f, 10f).toDouble()

            when (loopPhase) {
                PreviewLoopPhase.START_WAIT -> {
                    phaseVirtualElapsed += dt
                    if (phaseVirtualElapsed >= STUDIO_PREVIEW_START_DELAY) {
                        val outgoing = if (phaseAtoB) holder.screenA else holder.screenB
                        val incoming = if (phaseAtoB) holder.screenB else holder.screenA
                        val exitRes = if (phaseAtoB) s1Exit else s2Exit
                        val enterRes = if (phaseAtoB) s1Enter else s2Enter

                        incoming.visibility = View.VISIBLE
                        outAnim = startRealtimePreviewAnim(resolvedCtx, outgoing, exitRes, ::currentSpeed)
                        inAnim = startRealtimePreviewAnim(resolvedCtx, incoming, enterRes, ::currentSpeed)
                        phaseVirtualElapsed = 0.0
                        loopPhase = PreviewLoopPhase.PLAYING
                    }
                }

                PreviewLoopPhase.PLAYING -> {
                    val outgoing = if (phaseAtoB) holder.screenA else holder.screenB
                    val incoming = if (phaseAtoB) holder.screenB else holder.screenA
                    outgoing.invalidate()
                    incoming.invalidate()
                    frame.invalidate()

                    phaseVirtualElapsed += dt * speedFactor
                    val oDone = outAnim?.let { it.isFinished || phaseVirtualElapsed >= it.baseTotalDurationMs } ?: true
                    val iDone = inAnim?.let { it.isFinished || phaseVirtualElapsed >= it.baseTotalDurationMs } ?: true

                    if (oDone && iDone) {
                        resetStudioPreviewView(outgoing)
                        outgoing.visibility = View.INVISIBLE
                        resetStudioPreviewView(incoming)
                        incoming.visibility = View.VISIBLE
                        phaseVirtualElapsed = 0.0
                        loopPhase = PreviewLoopPhase.HOLD_WAIT
                    }
                }

                PreviewLoopPhase.HOLD_WAIT -> {
                    phaseVirtualElapsed += dt
                    if (phaseVirtualElapsed >= STUDIO_PREVIEW_HOLD_DELAY) {
                        phaseAtoB = when (spec.directionMode) {
                            LoopDirectionMode.ALTERNATE -> !phaseAtoB
                            LoopDirectionMode.STEP1_ONLY -> true
                            LoopDirectionMode.STEP2_ONLY -> false
                        }
                        prepareWaitPhase()
                    }
                }
            }

            frame.postOnAnimation(this)
        }
    }

    holder.frame.postOnAnimation(runnable)
}

private fun startRealtimePreviewAnim(
    ctx: Context,
    view: View,
    animRes: Int,
    speedProvider: () -> Float
): RealtimeSpeedAnimation? {
    if (animRes == 0) return null
    return try {
        val raw = AnimationUtils.loadAnimation(ctx, animRes)
        val wrapped = RealtimeSpeedAnimation(raw, speedProvider)
        view.startAnimation(wrapped)
        wrapped
    } catch (_: Exception) {
        null
    }
}

private fun computeMaxAnimDuration(anim: Animation): Long {
    if (anim is AnimationSet) {
        var maxChild = 0L
        val children = anim.animations
        for (i in 0 until children.size) {
            maxChild = maxOf(maxChild, computeMaxAnimDuration(children[i]))
        }
        return (anim.startOffset + maxChild).coerceAtLeast(180L)
    }
    return (anim.startOffset + anim.duration).coerceAtLeast(180L)
}

private fun resetStudioPreviewView(view: View) {
    view.clearAnimation()
    view.alpha = 1f
    view.scaleX = 1f
    view.scaleY = 1f
    view.translationX = 0f
    view.translationY = 0f
    view.rotation = 0f
}

// ── Live Constructor Loop Preview (Real-Time Param Updates & Shared Clock) ──

@Stable
class ConstructorPreviewClockState {
    var phaseAtoB: Boolean = true
    var loopPhaseOrdinal: Int = PreviewLoopPhase.START_WAIT.ordinal
    var elapsedMs: Long = 0L
}

@Composable
fun rememberConstructorPreviewClockState(): ConstructorPreviewClockState =
    remember { ConstructorPreviewClockState() }

private class ConstructorLoopHolder(
    val frame: FrameLayout,
    val screenA: TextView,
    val screenB: TextView,
    @Volatile var exitParams: AnimParams,
    @Volatile var enterParams: AnimParams,
    @Volatile var step2ExitParams: AnimParams,
    @Volatile var step2EnterParams: AnimParams,
    @Volatile var directionMode: LoopDirectionMode,
    val clockState: ConstructorPreviewClockState,
    var attachedToken: Int = 0
)

@Composable
internal fun StudioConstructorLoopPreview(
    exitParams: AnimParams,
    enterParams: AnimParams,
    modifier: Modifier = Modifier,
    width: Dp = 150.dp,
    height: Dp = 250.dp,
    cornerRadius: Dp = 12.dp,
    labelSizeSp: Float = 26f,
    clockState: ConstructorPreviewClockState = rememberConstructorPreviewClockState(),
    step2ExitParams: AnimParams = exitParams,
    step2EnterParams: AnimParams = enterParams,
    directionMode: LoopDirectionMode = LoopDirectionMode.ALTERNATE
) {
    val shape = RoundedCornerShape(cornerRadius)
    val bgColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val screenABg = MaterialTheme.colorScheme.primary.toArgb()
    val screenAFg = MaterialTheme.colorScheme.onPrimary.toArgb()
    val screenBBg = MaterialTheme.colorScheme.tertiaryContainer.toArgb()
    val screenBFg = MaterialTheme.colorScheme.onTertiaryContainer.toArgb()

    Card(
        shape = shape,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        border = BorderStroke(1.dp, bgColor.edgeOutline()),
        modifier = modifier.size(width = width, height = height)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(shape)
        ) {
            AndroidView(
                factory = { ctx ->
                    createLiveConstructorPreviewFrame(
                        ctx, exitParams, enterParams, step2ExitParams, step2EnterParams,
                        directionMode, labelSizeSp, clockState,
                        screenABg, screenAFg, screenBBg, screenBFg
                    )
                },
                update = { frame ->
                    val holder = frame.tag as? ConstructorLoopHolder
                    if (holder != null) {
                        holder.exitParams = exitParams
                        holder.enterParams = enterParams
                        holder.step2ExitParams = step2ExitParams
                        holder.step2EnterParams = step2EnterParams
                        holder.directionMode = directionMode
                        holder.screenA.textSize = labelSizeSp
                        holder.screenB.textSize = labelSizeSp
                        holder.screenA.setBackgroundColor(screenABg)
                        holder.screenA.setTextColor(screenAFg)
                        holder.screenB.setBackgroundColor(screenBBg)
                        holder.screenB.setTextColor(screenBFg)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

private fun createLiveConstructorPreviewFrame(
    ctx: Context,
    initialExit: AnimParams,
    initialEnter: AnimParams,
    initialStep2Exit: AnimParams,
    initialStep2Enter: AnimParams,
    initialDirectionMode: LoopDirectionMode,
    labelSizeSp: Float,
    clockState: ConstructorPreviewClockState,
    screenABg: Int,
    screenAFg: Int,
    screenBBg: Int,
    screenBFg: Int
): FrameLayout {
    val frame = FrameLayout(ctx).apply {
        clipChildren = true
        clipToPadding = true
    }
    val screenA = TextView(ctx).apply {
        text = "A"
        textSize = labelSizeSp
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        setBackgroundColor(screenABg)
        setTextColor(screenAFg)
    }
    val screenB = TextView(ctx).apply {
        text = "B"
        textSize = labelSizeSp
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        setBackgroundColor(screenBBg)
        setTextColor(screenBFg)
        visibility = View.INVISIBLE
    }
    frame.addView(
        screenA,
        FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
    )
    frame.addView(
        screenB,
        FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
    )

    val holder = ConstructorLoopHolder(
        frame = frame,
        screenA = screenA,
        screenB = screenB,
        exitParams = initialExit,
        enterParams = initialEnter,
        step2ExitParams = initialStep2Exit,
        step2EnterParams = initialStep2Enter,
        directionMode = initialDirectionMode,
        clockState = clockState
    )
    frame.tag = holder

    frame.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) {
            holder.attachedToken++
            val token = holder.attachedToken
            v.post { startConstructorRealtimeTicker(holder, token) }
        }

        override fun onViewDetachedFromWindow(v: View) {
            holder.attachedToken++
        }
    })

    frame.post {
        if (frame.isAttachedToWindow) {
            holder.attachedToken++
            startConstructorRealtimeTicker(holder, holder.attachedToken)
        }
    }
    return frame
}

private fun startConstructorRealtimeTicker(holder: ConstructorLoopHolder, token: Int) {
    val clock = holder.clockState
    var lastTickMs = SystemClock.uptimeMillis()

    fun resolvePhaseAtoB(): Boolean = when (holder.directionMode) {
        LoopDirectionMode.ALTERNATE -> clock.phaseAtoB
        LoopDirectionMode.STEP1_ONLY -> true
        LoopDirectionMode.STEP2_ONLY -> false
    }

    fun applyTransformAt(view: View, params: AnimParams, timeMs: Long) {
        val pw = holder.frame.width.toFloat().coerceAtLeast(1f)
        val ph = holder.frame.height.toFloat().coerceAtLeast(1f)
        val rawT = if (timeMs <= params.startOffset) {
            0f
        } else {
            ((timeMs - params.startOffset).toFloat() / params.duration.coerceAtLeast(1L).toFloat())
                .coerceIn(0f, 1f)
        }
        val t = resolveInterpolator(params.interpolatorIndex).getInterpolation(rawT)
        view.translationX = lerp(params.translateFromX / 100f * pw, params.translateToX / 100f * pw, t)
        view.translationY = lerp(params.translateFromY / 100f * ph, params.translateToY / 100f * ph, t)
        view.scaleX = lerp(params.scaleFromX, params.scaleToX, t)
        view.scaleY = lerp(params.scaleFromY, params.scaleToY, t)
        view.alpha = lerp(params.alphaFrom, params.alphaTo, t)
        view.rotation = lerp(params.rotateFrom, params.rotateTo, t)
        view.pivotX = params.pivotX / 100f * view.width.coerceAtLeast(1)
        view.pivotY = params.pivotY / 100f * view.height.coerceAtLeast(1)
    }

    val initAtoB = resolvePhaseAtoB()
    val initOutgoing = if (initAtoB) holder.screenA else holder.screenB
    val initIncoming = if (initAtoB) holder.screenB else holder.screenA
    val initExitP = if (initAtoB) holder.exitParams else holder.step2ExitParams
    val initEnterP = if (initAtoB) holder.enterParams else holder.step2EnterParams

    when (PreviewLoopPhase.entries.getOrElse(clock.loopPhaseOrdinal) { PreviewLoopPhase.START_WAIT }) {
        PreviewLoopPhase.START_WAIT -> {
            resetStudioPreviewView(initOutgoing)
            resetStudioPreviewView(initIncoming)
            initOutgoing.visibility = View.VISIBLE
            initIncoming.visibility = View.INVISIBLE
        }
        PreviewLoopPhase.PLAYING -> {
            initOutgoing.visibility = View.VISIBLE
            initIncoming.visibility = View.VISIBLE
            applyTransformAt(initOutgoing, initExitP, clock.elapsedMs)
            applyTransformAt(initIncoming, initEnterP, clock.elapsedMs)
        }
        PreviewLoopPhase.HOLD_WAIT -> {
            resetStudioPreviewView(initOutgoing)
            initOutgoing.visibility = View.INVISIBLE
            resetStudioPreviewView(initIncoming)
            initIncoming.visibility = View.VISIBLE
        }
    }

    val runnable = object : Runnable {
        override fun run() {
            val frame = holder.frame
            if (!frame.isAttachedToWindow || holder.attachedToken != token) return

            val now = SystemClock.uptimeMillis()
            val dt = (now - lastTickMs).coerceIn(0L, 64L)
            lastTickMs = now
            clock.elapsedMs += dt

            val useAtoB = resolvePhaseAtoB()
            val outgoing = if (useAtoB) holder.screenA else holder.screenB
            val incoming = if (useAtoB) holder.screenB else holder.screenA
            val exitP = if (useAtoB) holder.exitParams else holder.step2ExitParams
            val enterP = if (useAtoB) holder.enterParams else holder.step2EnterParams
            val currentPhase = PreviewLoopPhase.entries.getOrElse(clock.loopPhaseOrdinal) {
                PreviewLoopPhase.START_WAIT
            }

            when (currentPhase) {
                PreviewLoopPhase.START_WAIT -> {
                    if (clock.elapsedMs >= 320L) {
                        resetStudioPreviewView(outgoing)
                        resetStudioPreviewView(incoming)
                        outgoing.visibility = View.VISIBLE
                        incoming.visibility = View.VISIBLE
                        clock.elapsedMs = 0L
                        clock.loopPhaseOrdinal = PreviewLoopPhase.PLAYING.ordinal
                    }
                }

                PreviewLoopPhase.PLAYING -> {
                    val exitTotal = (exitP.duration + exitP.startOffset).coerceAtLeast(100L)
                    val enterTotal = (enterP.duration + enterP.startOffset).coerceAtLeast(100L)
                    val maxTotal = maxOf(exitTotal, enterTotal, 220L)

                    outgoing.visibility = View.VISIBLE
                    incoming.visibility = View.VISIBLE
                    applyTransformAt(outgoing, exitP, clock.elapsedMs)
                    applyTransformAt(incoming, enterP, clock.elapsedMs)

                    if (clock.elapsedMs >= maxTotal) {
                        resetStudioPreviewView(outgoing)
                        outgoing.visibility = View.INVISIBLE
                        resetStudioPreviewView(incoming)
                        incoming.visibility = View.VISIBLE
                        clock.elapsedMs = 0L
                        clock.loopPhaseOrdinal = PreviewLoopPhase.HOLD_WAIT.ordinal
                    }
                }

                PreviewLoopPhase.HOLD_WAIT -> {
                    if (clock.elapsedMs >= 620L) {
                        clock.phaseAtoB = when (holder.directionMode) {
                            LoopDirectionMode.ALTERNATE -> !clock.phaseAtoB
                            LoopDirectionMode.STEP1_ONLY -> true
                            LoopDirectionMode.STEP2_ONLY -> false
                        }
                        clock.elapsedMs = 0L
                        clock.loopPhaseOrdinal = PreviewLoopPhase.START_WAIT.ordinal
                    }
                }
            }

            frame.postOnAnimation(this)
        }
    }

    holder.frame.postOnAnimation(runnable)
}