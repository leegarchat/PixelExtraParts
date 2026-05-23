package org.pixel.customparts.ui.addons

import android.content.Context
import android.provider.Settings
import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.AnimationSet
import android.view.animation.AnimationUtils
import android.view.animation.DecelerateInterpolator
import android.view.animation.TranslateAnimation
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.pixel.customparts.SettingsKeys
import org.pixel.customparts.activities.BUILTIN_MODES
import org.pixel.customparts.activities.MODE_CUSTOM
import org.pixel.customparts.activities.MODE_DISABLED
import org.pixel.customparts.activities.MODE_NO_ANIMATION

private const val DEFAULT_PAGE_ANIM_DURATION_MS = 300L
private const val CUSTOM_OPEN_ENTER = "custom_open_enter"
private const val CUSTOM_OPEN_EXIT = "custom_open_exit"
private const val CUSTOM_CLOSE_ENTER = "custom_close_enter"
private const val CUSTOM_CLOSE_EXIT = "custom_close_exit"

private data class AddonPageTransitionSpec(
    val open: AddonPageTransitionDirectionSpec?,
    val close: AddonPageTransitionDirectionSpec?
)

private data class AddonPageTransitionDirectionSpec(
    val themeContext: Context,
    val enterAnim: Int,
    val exitAnim: Int,
    val noAnimation: Boolean = false
)

private data class AddonPageViewAnimations(
    val enter: Animation?,
    val exit: Animation?
)

@Composable
internal fun <T : Any> AddonPageTransitionHost(
    targetState: T,
    modifier: Modifier = Modifier,
    isForward: (initialState: T, targetState: T) -> Boolean,
    content: @Composable (T) -> Unit
) {
    val context = LocalContext.current
    val parentCompositionContext = rememberCompositionContext()
    val latestContent by rememberUpdatedState(content)
    val customTransitionSpec by produceState<AddonPageTransitionSpec?>(initialValue = null, context) {
        value = withContext(Dispatchers.IO) { loadAddonPageTransitionSpec(context) }
    }

    val spec = customTransitionSpec
    if (spec == null) {
        AnimatedContent(
            targetState = targetState,
            transitionSpec = {
                val forward = isForward(initialState, targetState)
                if (forward) {
                    (slideInHorizontally { it / 3 } + fadeIn()) togetherWith
                            (slideOutHorizontally { -it / 3 } + fadeOut())
                } else {
                    (slideInHorizontally { -it / 3 } + fadeIn()) togetherWith
                            (slideOutHorizontally { it / 3 } + fadeOut())
                }
            },
            label = "pageAnim"
        ) { page ->
            latestContent(page)
        }
    } else {
        AndroidView(
            modifier = modifier,
            factory = { viewContext ->
                FrameLayout(viewContext).apply {
                    clipChildren = true
                    clipToPadding = true
                    val initialView = newComposePageView(viewContext)
                    addView(initialView)
                    tag = AddonPageTransitionViewHolder(this, initialView)
                }
            },
            update = { frame ->
                @Suppress("UNCHECKED_CAST")
                val holder = frame.tag as AddonPageTransitionViewHolder
                holder.update(
                    targetState = targetState,
                    transitionSpec = spec,
                    parentCompositionContext = parentCompositionContext,
                    isForward = isForward,
                    content = latestContent
                )
            }
        )
    }
}

private class AddonPageTransitionViewHolder(
    private val frame: FrameLayout,
    private val firstView: ComposeView
) {
    private var currentState: Any? = null
    private var activeView: ComposeView = firstView
    private var inactiveView: ComposeView? = null
    private var animationSerial = 0

    fun <T : Any> update(
        targetState: T,
        transitionSpec: AddonPageTransitionSpec,
        parentCompositionContext: CompositionContext,
        isForward: (initialState: T, targetState: T) -> Boolean,
        content: @Composable (T) -> Unit
    ) {
        val previousState = currentState
        if (previousState == null) {
            currentState = targetState
            setPageContent(activeView, parentCompositionContext, targetState, content)
            activeView.visibility = View.VISIBLE
            return
        }

        if (previousState == targetState) return

        animationSerial++
        hideInactiveChildren()

        val outgoingView = activeView
        val incomingView = obtainInactiveView(parentCompositionContext)
        setPageContent(incomingView, parentCompositionContext, targetState, content)

        @Suppress("UNCHECKED_CAST")
        val forward = runCatching { isForward(previousState as T, targetState) }.getOrDefault(true)
        val animations = createAnimations(transitionSpec, forward)

        resetAnimatedView(outgoingView)
        resetAnimatedView(incomingView)
        outgoingView.visibility = View.VISIBLE
        incomingView.visibility = View.VISIBLE
        if (forward || animations.exit == null) {
            frame.bringChildToFront(incomingView)
        } else {
            frame.bringChildToFront(outgoingView)
        }

        activeView = incomingView
        currentState = targetState
        startAnimations(outgoingView, incomingView, animations)
    }

    private fun <T : Any> setPageContent(
        view: ComposeView,
        parentCompositionContext: CompositionContext,
        state: T,
        content: @Composable (T) -> Unit
    ) {
        view.setParentCompositionContext(parentCompositionContext)
        view.setContent { content(state) }
    }

    private fun obtainInactiveView(parentCompositionContext: CompositionContext): ComposeView {
        val view = inactiveView ?: newComposePageView(frame.context).also(frame::addView)
        inactiveView = null
        view.setParentCompositionContext(parentCompositionContext)
        return view
    }

    private fun hideInactiveChildren() {
        for (index in 0 until frame.childCount) {
            val child = frame.getChildAt(index)
            if (child !== activeView) {
                child.clearAnimation()
                child.visibility = View.GONE
                if (child is ComposeView) inactiveView = child
            }
        }
    }

    private fun createAnimations(
        transitionSpec: AddonPageTransitionSpec,
        forward: Boolean
    ): AddonPageViewAnimations {
        val directionSpec = if (forward) transitionSpec.open else transitionSpec.close
        return directionSpec?.loadAnimations() ?: createDefaultPageAnimations(frame, forward)
    }

    private fun startAnimations(
        outgoingView: ComposeView,
        incomingView: ComposeView,
        animations: AddonPageViewAnimations
    ) {
        val serial = animationSerial
        val running = listOfNotNull(animations.enter, animations.exit)
        if (running.isEmpty()) {
            finishTransition(serial, outgoingView, incomingView)
            return
        }

        var remaining = running.size
        var finished = false
        fun onFinished() {
            remaining--
            if (remaining <= 0 && !finished) {
                finished = true
                finishTransition(serial, outgoingView, incomingView)
            }
        }

        animations.enter?.apply { setEndAction(::onFinished) }?.let(incomingView::startAnimation)
        animations.exit?.apply { setEndAction(::onFinished) }?.let(outgoingView::startAnimation)

        val maxDuration = running.maxOf { it.duration + it.startOffset }.coerceAtLeast(DEFAULT_PAGE_ANIM_DURATION_MS)
        frame.postDelayed({
            if (!finished) {
                finished = true
                finishTransition(serial, outgoingView, incomingView)
            }
        }, maxDuration + 80L)
    }

    private fun finishTransition(
        serial: Int,
        outgoingView: ComposeView,
        incomingView: ComposeView
    ) {
        if (serial != animationSerial) return
        outgoingView.clearAnimation()
        incomingView.clearAnimation()
        resetAnimatedView(outgoingView)
        resetAnimatedView(incomingView)
        outgoingView.visibility = View.GONE
        incomingView.visibility = View.VISIBLE
        frame.bringChildToFront(incomingView)
        inactiveView = outgoingView
    }
}

private fun newComposePageView(context: Context): ComposeView {
    return ComposeView(context).apply {
        layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
    }
}

private fun AddonPageTransitionDirectionSpec.loadAnimations(): AddonPageViewAnimations? {
    if (noAnimation) return AddonPageViewAnimations(null, null)
    return runCatching {
        val enter = enterAnim.takeIf { it != 0 }?.let { AnimationUtils.loadAnimation(themeContext, it).apply { fillAfter = true } }
        val exit = exitAnim.takeIf { it != 0 }?.let { AnimationUtils.loadAnimation(themeContext, it).apply { fillAfter = true } }
        if (enter == null && exit == null) null else AddonPageViewAnimations(enter, exit)
    }.getOrNull()
}

private fun createDefaultPageAnimations(frame: FrameLayout, forward: Boolean): AddonPageViewAnimations {
    val width = frame.width.takeIf { it > 0 } ?: frame.resources.displayMetrics.widthPixels
    val offset = width / 3f
    val enterFromX = if (forward) offset else -offset
    val exitToX = if (forward) -offset else offset
    return AddonPageViewAnimations(
        enter = createTranslateFadeAnimation(enterFromX, 0f, 0f, 1f),
        exit = createTranslateFadeAnimation(0f, exitToX, 1f, 0f)
    )
}

private fun createTranslateFadeAnimation(
    fromX: Float,
    toX: Float,
    fromAlpha: Float,
    toAlpha: Float
): Animation {
    return AnimationSet(true).apply {
        duration = DEFAULT_PAGE_ANIM_DURATION_MS
        interpolator = DecelerateInterpolator()
        fillAfter = true
        addAnimation(TranslateAnimation(fromX, toX, 0f, 0f).apply { duration = DEFAULT_PAGE_ANIM_DURATION_MS })
        addAnimation(AlphaAnimation(fromAlpha, toAlpha).apply { duration = DEFAULT_PAGE_ANIM_DURATION_MS })
    }
}

private fun Animation.setEndAction(onEnd: () -> Unit) {
    setAnimationListener(object : Animation.AnimationListener {
        override fun onAnimationStart(animation: Animation?) = Unit
        override fun onAnimationRepeat(animation: Animation?) = Unit
        override fun onAnimationEnd(animation: Animation?) = onEnd()
    })
}

private fun resetAnimatedView(view: View) {
    view.alpha = 1f
    view.translationX = 0f
    view.translationY = 0f
    view.scaleX = 1f
    view.scaleY = 1f
    view.rotation = 0f
    view.pivotX = view.width / 2f
    view.pivotY = view.height / 2f
}

private fun loadAddonPageTransitionSpec(context: Context): AddonPageTransitionSpec? {
    val resolver = context.contentResolver
    val openMode = Settings.Global.getInt(resolver, SettingsKeys.ACTIVITY_OPEN_TRANSITION, 0)
    val closeMode = Settings.Global.getInt(resolver, SettingsKeys.ACTIVITY_CLOSE_TRANSITION, 0)
    val open = resolveModeSpec(
        context = context,
        mode = openMode,
        customPackageKey = SettingsKeys.ACTIVITY_OPEN_CUSTOM_PACKAGE,
        customEnterName = CUSTOM_OPEN_ENTER,
        customExitName = CUSTOM_OPEN_EXIT,
        openDirection = true
    )
    val close = resolveModeSpec(
        context = context,
        mode = closeMode,
        customPackageKey = SettingsKeys.ACTIVITY_CLOSE_CUSTOM_PACKAGE,
        customEnterName = CUSTOM_CLOSE_ENTER,
        customExitName = CUSTOM_CLOSE_EXIT,
        openDirection = false
    )
    return if (open != null || close != null) AddonPageTransitionSpec(open, close) else null
}

private fun resolveModeSpec(
    context: Context,
    mode: Int,
    customPackageKey: String,
    customEnterName: String,
    customExitName: String,
    openDirection: Boolean
): AddonPageTransitionDirectionSpec? {
    return when (mode) {
        MODE_DISABLED -> null
        MODE_NO_ANIMATION -> AddonPageTransitionDirectionSpec(context, 0, 0, noAnimation = true)
        MODE_CUSTOM -> resolveDirectionSpec(
            context = context,
            packageName = readCustomPackage(context, customPackageKey),
            enterName = customEnterName,
            exitName = customExitName
        )
        else -> resolveBuiltInDirectionSpec(context, mode, openDirection)
    }
}

private fun resolveBuiltInDirectionSpec(
    context: Context,
    mode: Int,
    openDirection: Boolean
): AddonPageTransitionDirectionSpec? {
    val modeDef = BUILTIN_MODES.firstOrNull { it.modeId == mode } ?: return null
    val enter = if (openDirection) modeDef.openEnterAnim else modeDef.closeEnterAnim
    val exit = if (openDirection) modeDef.openExitAnim else modeDef.closeExitAnim
    return if (enter == 0 && exit == 0) null else AddonPageTransitionDirectionSpec(context, enter, exit)
}

private fun readCustomPackage(context: Context, directionKey: String): String? {
    val resolver = context.contentResolver
    return Settings.Global.getString(resolver, directionKey)?.takeIf { it.isNotBlank() }
        ?: Settings.Global.getString(resolver, SettingsKeys.ACTIVITY_TRANSITION_CUSTOM_PACKAGE)?.takeIf { it.isNotBlank() }
}

private fun resolveDirectionSpec(
    context: Context,
    packageName: String?,
    enterName: String,
    exitName: String
): AddonPageTransitionDirectionSpec? {
    if (packageName.isNullOrBlank()) return null
    return runCatching {
        val themeContext = context.createPackageContext(packageName, Context.CONTEXT_IGNORE_SECURITY)
        val resources = themeContext.resources
        val enter = resources.getIdentifier(enterName, "anim", packageName)
        val exit = resources.getIdentifier(exitName, "anim", packageName)
        if (enter == 0 && exit == 0) null else AddonPageTransitionDirectionSpec(themeContext, enter, exit)
    }.getOrNull()
}