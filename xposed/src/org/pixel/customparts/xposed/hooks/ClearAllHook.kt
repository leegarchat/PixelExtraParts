package org.pixel.customparts.xposed.hooks

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.provider.Settings
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
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.lang.ref.WeakReference

object ClearAllHook {

    private const val TAG = "PixelParts_ClearAll"
    
    private const val KEY_CLEAR_ALL_ENABLED = "launcher_clear_all_xposed"
    private const val KEY_REPLACE_MODE = "launcher_replace_on_clear"
    private const val KEY_BUTTON_MARGIN_BOTTOM = "launcher_clear_all_bottom_margin"

    private const val CLASS_RECENTS_VIEW = "com.android.quickstep.views.RecentsView"
    private const val CLASS_OVERVIEW_ACTIONS_VIEW = "com.android.quickstep.views.OverviewActionsView"

    private var recentsViewRef: WeakReference<Any>? = null

    fun init(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != "com.google.android.apps.nexuslauncher") return

        try {
            val recentsViewClass = XposedHelpers.findClass(CLASS_RECENTS_VIEW, lpparam.classLoader)
            XposedBridge.hookAllConstructors(recentsViewClass, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    recentsViewRef = WeakReference(param.thisObject)
                }
            })

            val overviewActionsClass = XposedHelpers.findClass(CLASS_OVERVIEW_ACTIONS_VIEW, lpparam.classLoader)
            XposedHelpers.findAndHookMethod(
                overviewActionsClass,
                "onFinishInflate",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val view = param.thisObject as ViewGroup
                        val context = view.context

                        val isEnabled = Settings.Secure.getInt(
                            context.contentResolver, KEY_CLEAR_ALL_ENABLED, 0
                        ) == 1

                        if (isEnabled) {
                            val mode = Settings.Secure.getInt(
                                context.contentResolver, KEY_REPLACE_MODE, 0
                            )
                            view.post {
                                updateOverviewActions(view, context, mode)
                            }
                        }
                    }
                }
            )

        } catch (e: Throwable) {
            XposedBridge.log("$TAG: Error initializing ClearAll hooks: $e")
        }
    }

    @SuppressLint("DiscouragedApi")
    private fun updateOverviewActions(parent: ViewGroup, context: Context, mode: Int) {
        try {
            val res = context.resources
            val myTag = "custom_clear_all_btn"

            val containerId = res.getIdentifier("action_buttons", "id", context.packageName)
            val buttonContainer = if (containerId != 0) {
                parent.findViewById<ViewGroup>(containerId)
            } else {
                (0 until parent.childCount)
                    .map { parent.getChildAt(it) }
                    .filterIsInstance<LinearLayout>()
                    .firstOrNull()
            } ?: return

            buttonContainer.findViewWithTag<View>(myTag)?.let { buttonContainer.removeView(it) }
            parent.findViewWithTag<View>(myTag)?.let { parent.removeView(it) }

            val screenshotId = res.getIdentifier("action_screenshot", "id", context.packageName)
            val selectId = res.getIdentifier("action_select", "id", context.packageName)
            val screenshotBtn = if (screenshotId != 0) buttonContainer.findViewById<Button>(screenshotId) else null
            val selectBtn = if (selectId != 0) buttonContainer.findViewById<Button>(selectId) else null

            when (mode) {
                1 -> {
                    if (screenshotBtn != null) {
                        transformButtonToClearAll(context, screenshotBtn)
                    } else {
                        val clearButton = createClearAllButton(context, myTag, selectBtn)
                        syncStateWithView(clearButton, buttonContainer)
                        buttonContainer.addView(clearButton, 0)
                    }
                }
                
                2 -> {
                    if (selectBtn != null) {
                        transformButtonToClearAll(context, selectBtn)
                    } else {
                        val clearButton = createClearAllButton(context, myTag, screenshotBtn)
                        syncStateWithView(clearButton, buttonContainer)
                        buttonContainer.addView(clearButton)
                    }
                }
                
                else -> {
                    val styleSource = selectBtn ?: screenshotBtn
                    val clearButton = createClearAllButton(context, myTag, styleSource)
                    
                    syncStateWithView(clearButton, buttonContainer)

                    var marginFactor = try {
                        Settings.Secure.getFloat(context.contentResolver, KEY_BUTTON_MARGIN_BOTTOM)
                    } catch (e: Settings.SettingNotFoundException) {
                        3.0f
                    }
                    if (marginFactor <= 0) marginFactor = 3.0f

                    val bottomParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply {
                        gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                        bottomMargin = (getButtonSpacing(context) * marginFactor).toInt()
                    }
                    clearButton.layoutParams = bottomParams
                    clearButton.setPadding(
                        clearButton.paddingLeft,
                        clearButton.paddingTop + 10,
                        clearButton.paddingRight,
                        clearButton.paddingBottom + 10
                    )
                    
                    clearButton.clearAnimation() 
                    parent.addView(clearButton)
                }
            }

        } catch (e: Throwable) {
            XposedBridge.log("$TAG: Failed to add ClearAll button: $e")
        }
    }

    @SuppressLint("DiscouragedApi")
    private fun transformButtonToClearAll(context: Context, button: Button) {
        val res = context.resources
        var stringId = res.getIdentifier("recents_clear_all", "string", context.packageName)
        if (stringId == 0) stringId = res.getIdentifier("clear_all", "string", "android")
        val newText = if (stringId != 0) res.getString(stringId) else "Clear All"
        
        button.text = newText
        button.setCompoundDrawablesWithIntrinsicBounds(null, null, null, null)

        button.setOnClickListener { view ->
            val recentsView = getRecentsView(context)
            if (recentsView != null) {
                if (tryClickNativeClearAll(recentsView)) {
                    return@setOnClickListener
                }
                try {
                    XposedHelpers.callMethod(recentsView, "dismissAllTasks", view)
                } catch (e: Throwable) {
                    try {
                        XposedHelpers.callMethod(recentsView, "dismissAllTasks")
                    } catch (e2: Throwable) {
                            XposedBridge.log("$TAG: Failed to clear tasks: $e2")
                    }
                }
            }
        }
        
        button.tag = "transformed_clear_all"
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
    private fun createClearAllButton(context: Context, tag: String, sourceBtn: Button?): Button {
        val res = context.resources
        
        val contextToUse = if (sourceBtn != null) {
            sourceBtn.context
        } else {
            val themeStyleId = res.getIdentifier("ThemeControlHighlightWorkspaceColor", "style", context.packageName)
            if (themeStyleId != 0) ContextThemeWrapper(context, themeStyleId) else context
        }

        val styleId = res.getIdentifier("OverviewActionButton", "style", context.packageName)

        return Button(contextToUse, null, 0, styleId).apply {
            this.tag = tag
            id = View.generateViewId()

            var stringId = res.getIdentifier("recents_clear_all", "string", context.packageName)
            if (stringId == 0) stringId = res.getIdentifier("clear_all", "string", "android")
            text = if (stringId != 0) res.getString(stringId) else "Clear All"

            setCompoundDrawablesWithIntrinsicBounds(null, null, null, null)

            if (sourceBtn != null) {
                setTextColor(sourceBtn.textColors)
                if (sourceBtn.background != null) {
                    background = sourceBtn.background.constantState?.newDrawable()
                }
                typeface = sourceBtn.typeface
                if (sourceBtn.stateListAnimator != null) {
                    stateListAnimator = sourceBtn.stateListAnimator.clone()
                }
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
                val recentsView = getRecentsView(context)
                if (recentsView != null) {
                    if (tryClickNativeClearAll(recentsView)) {
                        return@setOnClickListener
                    }

                    try {
                        XposedHelpers.callMethod(recentsView, "dismissAllTasks", view)
                    } catch (e: Throwable) {
                        try {
                            XposedHelpers.callMethod(recentsView, "dismissAllTasks")
                        } catch (e2: Throwable) {
                            XposedBridge.log("$TAG: Failed to clear tasks: $e2")
                        }
                    }
                }
            }
        }
    }

    private fun tryClickNativeClearAll(recentsView: Any): Boolean {
        try {
            val clearAllBtn = XposedHelpers.getObjectField(recentsView, "mClearAllButton") as? View
            if (clearAllBtn != null) {
                clearAllBtn.performClick()
                return true
            }
        } catch (e: Throwable) { /* Ignored */ }
        return false
    }

    private fun getRecentsView(context: Context): Any? {
        recentsViewRef?.get()?.let { return it }
        try {
            val activity = context as? Activity
            if (activity != null) {
                return XposedHelpers.callMethod(activity, "getOverviewPanel")
            }
        } catch (e: Throwable) { /* Ignored */ }
        return null
    }

    @SuppressLint("DiscouragedApi")
    private fun getButtonSpacing(context: Context): Int {
        val res = context.resources
        val spacingId = res.getIdentifier("overview_actions_button_spacing", "dimen", context.packageName)
        return if (spacingId != 0) res.getDimensionPixelSize(spacingId) else 24
    }
}