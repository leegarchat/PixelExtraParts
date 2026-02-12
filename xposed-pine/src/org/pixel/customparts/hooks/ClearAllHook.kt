package org.pixel.customparts.hooks

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import org.pixel.customparts.core.BaseHook
import java.lang.ref.WeakReference

class ClearAllHook : BaseHook() {

    override val hookId = "ClearAllHook"
    override val priority = 90

    companion object {
        private const val CLASS_RECENTS_VIEW = "com.android.quickstep.views.RecentsView"
        private const val CLASS_OVERVIEW_ACTIONS_VIEW = "com.android.quickstep.views.OverviewActionsView"
        private const val KEY_ENABLED = "launcher_clear_all"
        private const val KEY_MODE = "launcher_replace_on_clear"
        private const val KEY_MARGIN_BOTTOM = "launcher_clear_all_bottom_margin"
        private const val MODE_ADD_BOTTOM = 0
        private const val MODE_REPLACE_SCREENSHOT = 1
        private const val MODE_REPLACE_SELECT = 2
        private const val TAG_CLEAR_ALL = "custom_clear_all_btn"
        private const val TAG_TRANSFORMED = "transformed_clear_all"
        private var recentsViewRef: WeakReference<Any>? = null
    }

    override fun isEnabled(context: Context?): Boolean {
        return isSettingEnabled(context, KEY_ENABLED)
    }

    override fun onInit(classLoader: ClassLoader) {
        try {
            val recentsViewClass = XposedHelpers.findClass(CLASS_RECENTS_VIEW, classLoader)
            XposedBridge.hookAllConstructors(recentsViewClass, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    recentsViewRef = WeakReference(param.thisObject)
                }
            })

            val overviewActionsClass = XposedHelpers.findClass(CLASS_OVERVIEW_ACTIONS_VIEW, classLoader)
            XposedHelpers.findAndHookMethod(
                overviewActionsClass,
                "onFinishInflate",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val view = param.thisObject as ViewGroup
                        val context = view.context

                        if (isEnabled(context)) {
                            val mode = getIntSetting(context, KEY_MODE, MODE_ADD_BOTTOM)
                            view.post {
                                updateOverviewActions(view, context, mode)
                            }
                        }
                    }
                }
            )

            log("Hooks installed successfully")
        } catch (e: Throwable) {
            logError("Failed to initialize hooks", e)
        }
    }

    @SuppressLint("DiscouragedApi")
    private fun updateOverviewActions(parent: ViewGroup, context: Context, mode: Int) {
        try {
            val res = context.resources
            val packageName = context.packageName

            val containerId = res.getIdentifier("action_buttons", "id", packageName)
            val buttonContainer = if (containerId != 0) {
                parent.findViewById<ViewGroup>(containerId)
            } else {
                (0 until parent.childCount)
                    .map { parent.getChildAt(it) }
                    .filterIsInstance<LinearLayout>()
                    .firstOrNull()
            } ?: return

            buttonContainer.findViewWithTag<View>(TAG_CLEAR_ALL)?.let {
                buttonContainer.removeView(it)
            }
            parent.findViewWithTag<View>(TAG_CLEAR_ALL)?.let {
                parent.removeView(it)
            }

            val screenshotId = res.getIdentifier("action_screenshot", "id", packageName)
            val selectId = res.getIdentifier("action_select", "id", packageName)
            val screenshotBtn = if (screenshotId != 0) buttonContainer.findViewById<Button>(screenshotId) else null
            val selectBtn = if (selectId != 0) buttonContainer.findViewById<Button>(selectId) else null

            when (mode) {
                MODE_REPLACE_SCREENSHOT -> {
                    if (screenshotBtn != null) {
                        transformButtonToClearAll(context, screenshotBtn)
                    } else {
                        addClearAllButton(context, buttonContainer, selectBtn, TAG_CLEAR_ALL, 0)
                    }
                }

                MODE_REPLACE_SELECT -> {
                    if (selectBtn != null) {
                        transformButtonToClearAll(context, selectBtn)
                    } else {
                        addClearAllButton(context, buttonContainer, screenshotBtn, TAG_CLEAR_ALL, -1)
                    }
                }

                else -> {
                    addClearAllButtonBottom(context, parent, buttonContainer, selectBtn ?: screenshotBtn)
                }
            }

            log("Button added/updated (mode=$mode)")
        } catch (e: Throwable) {
            logError("Failed to add ClearAll button", e)
        }
    }

    @SuppressLint("DiscouragedApi")
    private fun transformButtonToClearAll(context: Context, button: Button) {
        val res = context.resources
        val packageName = context.packageName

        var stringId = res.getIdentifier("recents_clear_all", "string", packageName)
        if (stringId == 0) stringId = res.getIdentifier("clear_all", "string", "android")
        val clearAllText = if (stringId != 0) res.getString(stringId) else "Clear All"

        button.text = clearAllText
        button.setCompoundDrawablesWithIntrinsicBounds(null, null, null, null)
        button.tag = TAG_TRANSFORMED

        button.setOnClickListener { view ->
            performClearAll(context, view)
        }
    }

    @SuppressLint("DiscouragedApi")
    private fun addClearAllButton(
        context: Context,
        container: ViewGroup,
        styleSource: Button?,
        tag: String,
        position: Int
    ) {
        val button = createClearAllButton(context, tag, styleSource)
        syncStateWithView(button, container)

        val insertIndex = when {
            position == 0 -> 0
            position < 0 -> container.childCount
            else -> position.coerceAtMost(container.childCount)
        }

        container.addView(button, insertIndex)
    }

    @SuppressLint("DiscouragedApi")
    private fun addClearAllButtonBottom(
        context: Context,
        parent: ViewGroup,
        buttonContainer: ViewGroup,
        styleSource: Button?
    ) {
        val button = createClearAllButton(context, TAG_CLEAR_ALL, styleSource)
        syncStateWithView(button, buttonContainer)

        var marginFactor = getFloatSetting(context, KEY_MARGIN_BOTTOM, 3.0f)
        if (marginFactor <= 0) marginFactor = 3.0f

        val bottomParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            bottomMargin = (getButtonSpacing(context) * marginFactor).toInt()
        }

        button.layoutParams = bottomParams
        button.setPadding(
            button.paddingLeft,
            button.paddingTop + 10,
            button.paddingRight,
            button.paddingBottom + 10
        )

        button.clearAnimation()
        parent.addView(button)
    }

    @SuppressLint("DiscouragedApi")
    private fun createClearAllButton(context: Context, tag: String, sourceBtn: Button?): Button {
        val res = context.resources
        val packageName = context.packageName

        val contextToUse = if (sourceBtn != null) {
            sourceBtn.context
        } else {
            val themeStyleId = res.getIdentifier("ThemeControlHighlightWorkspaceColor", "style", packageName)
            if (themeStyleId != 0) ContextThemeWrapper(context, themeStyleId) else context
        }

        val styleId = res.getIdentifier("OverviewActionButton", "style", packageName)

        return Button(contextToUse, null, 0, styleId).apply {
            this.tag = tag
            id = View.generateViewId()

            var stringId = res.getIdentifier("recents_clear_all", "string", packageName)
            if (stringId == 0) stringId = res.getIdentifier("clear_all", "string", "android")
            text = if (stringId != 0) res.getString(stringId) else "Clear All"

            setCompoundDrawablesWithIntrinsicBounds(null, null, null, null)

            if (sourceBtn != null) {
                setTextColor(sourceBtn.textColors)
                sourceBtn.background?.constantState?.newDrawable()?.let { background = it }
                typeface = sourceBtn.typeface
                sourceBtn.stateListAnimator?.clone()?.let { stateListAnimator = it }
                setPadding(
                    sourceBtn.paddingLeft,
                    sourceBtn.paddingTop,
                    sourceBtn.paddingRight,
                    sourceBtn.paddingBottom
                )
            } else {
                val spacing = getButtonSpacing(context)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { marginStart = spacing }
            }

            setOnClickListener { view ->
                performClearAll(context, view)
            }
        }
    }

    private fun performClearAll(context: Context, triggerView: View?) {
        val recentsView = recentsViewRef?.get() ?: getRecentsViewFromContext(context)
        if (recentsView == null) {
            logError("RecentsView not found")
            return
        }
        if (tryClickNativeClearAll(recentsView)) {
            return
        }

        try {
            XposedHelpers.callMethod(recentsView, "dismissAllTasks", triggerView)
            log("Cleared tasks via dismissAllTasks(View)")
            return
        } catch (e: Throwable) {
            try {
                XposedHelpers.callMethod(recentsView, "dismissAllTasks")
                log("Cleared tasks via dismissAllTasks()")
            } catch (e2: Throwable) {
                logError("Failed to clear tasks", e2)
            }
        }
    }

    private fun tryClickNativeClearAll(recentsView: Any): Boolean {
        return try {
            val clearAllBtn = XposedHelpers.getObjectField(recentsView, "mClearAllButton") as? View
            if (clearAllBtn != null) {
                clearAllBtn.performClick()
                log("Clicked native ClearAll button")
                true
            } else false
        } catch (e: Throwable) {
            false
        }
    }

    private fun getRecentsViewFromContext(context: Context): Any? {
        return try {
            val activity = context as? Activity
            if (activity != null) {
                XposedHelpers.callMethod(activity, "getOverviewPanel")
            } else null
        } catch (e: Throwable) {
            null
        }
    }

    private fun syncStateWithView(target: View, source: View) {
        target.alpha = source.alpha
        target.translationY = source.translationY
        target.translationX = source.translationX

        if (source.visibility == View.VISIBLE) {
            target.visibility = View.VISIBLE
        }

        val listener = ViewTreeObserver.OnPreDrawListener {
            if (target.alpha != source.alpha) target.alpha = source.alpha
            if (source.visibility != target.visibility) {
                if (source is ViewGroup) target.visibility = source.visibility
            }
            if (target.translationY != source.translationY) target.translationY = source.translationY
            if (target.translationX != source.translationX) target.translationX = source.translationX
            true
        }

        source.viewTreeObserver.addOnPreDrawListener(listener)

        target.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {}
            override fun onViewDetachedFromWindow(v: View) {
                if (source.viewTreeObserver.isAlive) {
                    source.viewTreeObserver.removeOnPreDrawListener(listener)
                }
            }
        })
    }

    @SuppressLint("DiscouragedApi")
    private fun getButtonSpacing(context: Context): Int {
        val res = context.resources
        val spacingId = res.getIdentifier("overview_actions_button_spacing", "dimen", context.packageName)
        return if (spacingId != 0) res.getDimensionPixelSize(spacingId) else 24
    }
}