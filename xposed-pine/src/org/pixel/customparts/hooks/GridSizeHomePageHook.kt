package org.pixel.customparts.hooks

import android.content.Context
import android.text.TextUtils
import android.widget.TextView
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import org.pixel.customparts.core.BaseHook

class GridSizeHomePageHook : BaseHook() {

    override val hookId = "GridSizeHomePageHook"
    override val priority = 80

    companion object {
        private const val CLASS_INVARIANT_DEVICE_PROFILE = "com.android.launcher3.InvariantDeviceProfile"
        private const val CLASS_BUBBLE_TEXT_VIEW = "com.android.launcher3.BubbleTextView"
        private const val KEY_HOME_ENABLE = "launcher_homepage_sizer"
        private const val KEY_HOME_COLS = "launcher_homepage_h"
        private const val KEY_HOME_ROWS = "launcher_homepage_v"
        private const val KEY_HOME_ICON_SIZE = "launcher_homepage_icon_size"
        private const val KEY_HOME_TEXT_MODE = "launcher_homepage_text_mode"
        private const val DISPLAY_WORKSPACE = 0
        private const val DISPLAY_FOLDER = 2
        private const val CONTAINER_HOTSEAT = -101
        private const val CONTAINER_HOTSEAT_PREDICTION = -103
    }

    private val layoutListeners = java.util.WeakHashMap<android.view.View, android.view.View.OnLayoutChangeListener>()

    override fun isEnabled(context: Context?): Boolean {
        
        return isSettingEnabled(context, KEY_HOME_ENABLE)
    }

    override fun onInit(classLoader: ClassLoader) {
        try {
            hookInvariantDeviceProfile(classLoader)
            hookBubbleTextView(classLoader)
            hookFloatingIconView(classLoader)
            log("HomePage grid hooks installed")
        } catch (e: Throwable) {
            logError("Failed to initialize HomePage grid hooks", e)
        }
    }

    private fun hookFloatingIconView(classLoader: ClassLoader) {
        try {
            val helperClass = XposedHelpers.findClass("com.android.quickstep.util.FloatingIconViewHelper", classLoader)
            val launcherClass = XposedHelpers.findClass("com.android.launcher3.uioverrides.QuickstepLauncher", classLoader)
            val asyncViewClass = XposedHelpers.findClass("com.android.launcher3.util.AsyncView", classLoader)
            val clipIconViewClass = XposedHelpers.findClass("com.android.launcher3.views.ClipIconView", classLoader)
            
            XposedHelpers.findAndHookMethod(
                helperClass,
                "getFloatingIconView",
                launcherClass,
                android.view.View::class.java,
                asyncViewClass,
                asyncViewClass,
                Boolean::class.javaPrimitiveType,
                android.graphics.RectF::class.java,
                Boolean::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            val originalView = param.args[1] as? android.view.View ?: return
                            val targetView = resolveBubbleTextView(originalView) ?: originalView
                            if (!isWorkspaceView(targetView)) return

                            val context = targetView.context ?: return
                            if (!isSettingEnabled(context, KEY_HOME_ENABLE)) return
                            val sizePercent = getIntSetting(context, KEY_HOME_ICON_SIZE, 100)
                            if (sizePercent <= 0 || sizePercent == 100) return
                            val scale = sizePercent / 100f

                            val positionOut = param.args[5] as? android.graphics.RectF ?: return
                            val cx = positionOut.centerX()
                            val cy = positionOut.centerY()
                            val newW = positionOut.width() * scale
                            val newH = positionOut.height() * scale
                            positionOut.set(cx - newW / 2, cy - newH / 2, cx + newW / 2, cy + newH / 2)

                            log("FloatingIconViewHelper(Home): applied target scale $scale")
                        } catch (e: Exception) {
                            logError("Error in FloatingIconViewHelper (Home)", e)
                        }
                    }
                }
            )

            
            XposedBridge.hookAllMethods(clipIconViewClass, "update", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    try {
                        val floatingView = param.args[5] as? android.view.View ?: return
                        val originalIcon = XposedHelpers.getObjectField(floatingView, "mOriginalIcon") as? android.view.View ?: return
                        val targetView = resolveBubbleTextView(originalIcon) ?: originalIcon
                        if (!isWorkspaceView(targetView)) return

                        val context = targetView.context ?: return
                        if (!isSettingEnabled(context, KEY_HOME_ENABLE)) return
                        val sizePercent = getIntSetting(context, KEY_HOME_ICON_SIZE, 100)
                        if (sizePercent <= 0 || sizePercent == 100) return

                        
                        
                        
                        
                        
                        
                        val rectF = param.args[0] as? android.graphics.RectF ?: return
                        val lp = floatingView.layoutParams as? android.view.ViewGroup.MarginLayoutParams ?: return
                        val fMin = Math.min(lp.width, lp.height).toFloat()
                        
                        if (fMin > 0) {
                            
                            val intendedScale = rectF.width() / fMin
                            floatingView.scaleX = intendedScale
                            floatingView.scaleY = intendedScale

                            
                            
                            
                            val outline = XposedHelpers.getObjectField(param.thisObject, "mOutline") as? android.graphics.Rect
                            val finalBounds = XposedHelpers.getObjectField(param.thisObject, "mFinalDrawableBounds") as? android.graphics.Rect
                            
                            if (outline != null) {
                                outline.set(0, 0, lp.width, lp.height)
                            }
                            
                            if (finalBounds != null) {
                                val bg = XposedHelpers.getObjectField(param.thisObject, "mBackground") as? android.graphics.drawable.Drawable
                                val fg = XposedHelpers.getObjectField(param.thisObject, "mForeground") as? android.graphics.drawable.Drawable
                                bg?.bounds = finalBounds
                                fg?.bounds = finalBounds
                            }
                        }

                        
                        val clipView = param.thisObject
                        val isAdaptive = XposedHelpers.getBooleanField(clipView, "mIsAdaptiveIcon")
                        if (isAdaptive) {
                            val foreground = XposedHelpers.getObjectField(clipView, "mForeground") as? android.graphics.drawable.Drawable
                            val background = XposedHelpers.getObjectField(clipView, "mBackground") as? android.graphics.drawable.Drawable
                            if (foreground != null && background != null) {
                                foreground.bounds = background.bounds
                            }
                        }
                    } catch (e: Exception) {}
                }
            })
            log("FloatingIconView (Helper) hook installed (Home)")
        } catch (e: Throwable) {
            logError("Failed to hook FloatingIconViewHelper (Home)", e)
        }
    }

    private fun hookInvariantDeviceProfile(classLoader: ClassLoader) {
        try {
            val idpClass = XposedHelpers.findClass(CLASS_INVARIANT_DEVICE_PROFILE, classLoader)
            XposedHelpers.findAndHookMethod(
                idpClass,
                "initGrid",
                String::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val idp = param.thisObject
                        val context = getContextFromIDP(idp) ?: return
                        applyHomepageGridSettings(idp, context)
                    }
                }
            )
        } catch (e: Throwable) {
            logError("Failed to hook InvariantDeviceProfile", e)
        }
    }

    private fun hookBubbleTextView(classLoader: ClassLoader) {
        try {
            val bubbleTextViewClass = XposedHelpers.findClass(CLASS_BUBBLE_TEXT_VIEW, classLoader)
            val folderIconClass = try { XposedHelpers.findClass("com.android.launcher3.folder.FolderIcon", classLoader) } catch (e: Throwable) { null }

            
            for (method in bubbleTextViewClass.declaredMethods) {
                if (method.name == "applyIconAndLabel") {
                    XposedBridge.hookMethod(method, object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            val view = param.thisObject as? TextView ?: return
                            val info = param.args.firstOrNull()
                            handleWorkspaceView(view, info)
                        }
                    })
                }
            }

            
            if (folderIconClass != null) {
                XposedBridge.hookAllMethods(folderIconClass, "setFolder", object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val view = param.thisObject as? android.view.View ?: return
                        if (isWorkspaceView(view)) {
                            applyIconSize(view, view.context)
                        }
                    }
                })
            }
            
            
            val scaleHook = object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val view = param.thisObject as? android.view.View ?: return
                    
                    if (!bubbleTextViewClass.isInstance(view) && (folderIconClass == null || !folderIconClass.isInstance(view))) return

                    if (!isWorkspaceView(view)) return

                    val context = view.context ?: return
                    if (!isSettingEnabled(context, KEY_HOME_ENABLE)) return
                    val sizePercent = getIntSetting(context, KEY_HOME_ICON_SIZE, 100)
                    if (sizePercent <= 0 || sizePercent == 100) return

                    val myScale = sizePercent / 100f
                    val incomingFn = param.args[0] as Float
                    param.args[0] = incomingFn * myScale
                }
            }
            val viewClass = android.view.View::class.java
            XposedHelpers.findAndHookMethod(viewClass, "setScaleX", Float::class.javaPrimitiveType, scaleHook)
            XposedHelpers.findAndHookMethod(viewClass, "setScaleY", Float::class.javaPrimitiveType, scaleHook)
            
            
            XposedHelpers.findAndHookMethod(viewClass, "setVisibility", Int::class.javaPrimitiveType, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val view = param.thisObject as? android.view.View ?: return
                    if (!bubbleTextViewClass.isInstance(view) && (folderIconClass == null || !folderIconClass.isInstance(view))) return
                    
                    val visibility = param.args[0] as Int
                    if (visibility == 0) { 
                        val context = view.context ?: return
                        if (!isSettingEnabled(context, KEY_HOME_ENABLE)) return
                        applyIconSize(view, context)
                    }
                }
            })
        } catch (e: Throwable) {
            logError("Failed to hook BubbleTextView or FolderIcon", e)
        }
    }

    private fun isWorkspaceView(view: android.view.View): Boolean {
        
        val p = view.parent
        if (p != null) {
            var curr = p
            while (curr != null) {
                val name = curr.javaClass.name
                if (name.contains("Hotseat")) return false 
                if (name.contains("Workspace")) return true 
                if (name.contains("Folder")) return true 
                curr = curr.parent
            }
            
            
            return false
        }

        
        val tag = view.tag ?: return false
        return try {
            val container = XposedHelpers.getIntField(tag, "container")
            
            
            container != CONTAINER_HOTSEAT && 
            container != CONTAINER_HOTSEAT_PREDICTION &&
            container != -104 
        } catch (e: Throwable) {
            false
        }
    }

    private fun resolveBubbleTextView(view: android.view.View): TextView? {
        if (view is TextView && view.javaClass.name.contains("BubbleTextView")) return view

        if (view.javaClass.name.contains("BubbleTextHolder")) {
            return try {
                XposedHelpers.callMethod(view, "getBubbleText") as? TextView
            } catch (e: Throwable) {
                null
            }
        }

        if (view is android.view.ViewGroup) {
            for (i in 0 until view.childCount) {
                val child = view.getChildAt(i)
                if (child is TextView && child.javaClass.name.contains("BubbleTextView")) {
                    return child
                }
            }
        }

        return null
    }

    private fun applyHomepageGridSettings(idp: Any, context: Context) {
        if (!isSettingEnabled(context, KEY_HOME_ENABLE)) return
        val homeCols = getIntSetting(context, KEY_HOME_COLS, 0)
        val homeRows = getIntSetting(context, KEY_HOME_ROWS, 0)

        if (homeCols > 0) {
            XposedHelpers.setIntField(idp, "numColumns", homeCols)
            
            
            
            val safeHotseat = if (homeCols < 4) 4 else homeCols
            XposedHelpers.setIntField(idp, "numShownHotseatIcons", safeHotseat)
            
            
            try {
                XposedHelpers.setIntField(idp, "minColumns", Math.min(homeCols, 4))
            } catch (e: Throwable) {}

            log("Homepage columns set to $homeCols (Hotseat: $safeHotseat)")
        }

        if (homeRows > 0) {
            XposedHelpers.setIntField(idp, "numRows", homeRows)
            try {
                XposedHelpers.setIntField(idp, "minRows", Math.min(homeRows, 4))
            } catch (e: Throwable) {}
            log("Homepage rows set to $homeRows")
        }
    }

    


    private fun handleWorkspaceView(view: TextView, itemInfo: Any?) {
        try {
            val context = view.context ?: return
            if (!isSettingEnabled(context, KEY_HOME_ENABLE)) return
            
            val mDisplay = XposedHelpers.getIntField(view, "mDisplay")
            if (mDisplay != DISPLAY_WORKSPACE && mDisplay != DISPLAY_FOLDER) return

            
            if (itemInfo != null) {
                val container = XposedHelpers.getIntField(itemInfo, "container")
                
                if (container == -101 || container == -103 || container == -104) return
            }

            
            applyIconSize(view, context)

            
            applyTextMode(view, context)
        } catch (e: Exception) {
            
        }
    }

    private fun applyIconSize(view: android.view.View, context: Context) {
        if (!isSettingEnabled(context, KEY_HOME_ENABLE)) return
        val sizePercent = getIntSetting(context, KEY_HOME_ICON_SIZE, 100)
        if (sizePercent <= 0) return
        
        
        view.scaleX = 1f
        view.scaleY = 1f

        if (!layoutListeners.containsKey(view)) {
            val listener = android.view.View.OnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
                updatePivot(v)
            }
            view.addOnLayoutChangeListener(listener)
            layoutListeners[view] = listener
        }

        view.post {
            updatePivot(view)
        }
    }

    private fun updatePivot(view: android.view.View) {
        try {
            if (view.width <= 0) return

            if (view is TextView && view.javaClass.name.contains("BubbleTextView")) {
                val iconBounds = android.graphics.Rect()
                XposedHelpers.callMethod(view, "getIconBounds", iconBounds)
                if (!iconBounds.isEmpty) {
                    view.pivotX = view.width / 2f
                    view.pivotY = iconBounds.centerY().toFloat()
                }
            } else if (view.javaClass.name.contains("FolderIcon")) {
                view.pivotX = view.width / 2f
                val previewBounds = android.graphics.Rect()
                XposedHelpers.callMethod(view, "getPreviewBounds", previewBounds)
                if (!previewBounds.isEmpty) {
                    view.pivotY = previewBounds.centerY().toFloat()
                }
            }
        } catch (e: Throwable) {}
    }

    private fun applyTextMode(view: TextView, context: Context) {
        if (!isSettingEnabled(context, KEY_HOME_ENABLE)) return
        val mode = getIntSetting(context, KEY_HOME_TEXT_MODE, 0)
        
        if (view.text.isNullOrEmpty() && mode != 3) return

        when (mode) {
            0 -> {  }
            1 -> {
                view.maxLines = 2
                view.ellipsize = TextUtils.TruncateAt.END
            }
            2 -> {
                view.setSingleLine(true)
                view.ellipsize = TextUtils.TruncateAt.MARQUEE
                view.marqueeRepeatLimit = -1
                view.post { 
                    try { view.isSelected = true } catch (e: Exception) {} 
                }
            }
            3 -> {
                view.text = ""
            }
        }
    }

    private fun getContextFromIDP(idp: Any): Context? {
        return try {
            val displayController = XposedHelpers.getObjectField(idp, "mDisplayController")
            if (displayController != null) {
                val appContext = XposedHelpers.getObjectField(displayController, "mAppContext")
                if (appContext is Context) return appContext
            }
            android.app.ActivityThread.currentApplication()
        } catch (e: Exception) {
            android.app.ActivityThread.currentApplication()
        }
    }
}