package org.pixel.customparts.hooks

import android.content.Context
import android.widget.TextView
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import org.pixel.customparts.core.BaseHook

class DockScaleHook : BaseHook() {

    override val hookId = "DockScaleHook"
    override val priority = 80

    companion object {
        private const val CLASS_INVARIANT_DEVICE_PROFILE = "com.android.launcher3.InvariantDeviceProfile"
        private const val CLASS_DEVICE_PROFILE_BUILDER = "com.android.launcher3.DeviceProfile\$Builder"
        private const val CLASS_BUBBLE_TEXT_VIEW = "com.android.launcher3.BubbleTextView"
        private const val CLASS_FAST_BITMAP_DRAWABLE = "com.android.launcher3.icons.FastBitmapDrawable"
        private const val CONTAINER_HOTSEAT = -101
        private const val CONTAINER_HOTSEAT_PREDICTION = -103

        
        private const val KEY_HOTSEAT_ICONS = "launcher_hotseat_icons"
        private const val KEY_HOTSEAT_ICON_SIZE = "launcher_hotseat_icon_size"
    }

    override fun isEnabled(context: Context?): Boolean {
        val customIcons = getIntSetting(context, KEY_HOTSEAT_ICONS, 0)
        val iconSize = getIntSetting(context, KEY_HOTSEAT_ICON_SIZE, 100)
        return customIcons > 0 || iconSize != 100
    }

    override fun onInit(classLoader: ClassLoader) {
        try {
            hookInvariantDeviceProfile(classLoader)
            hookDeviceProfileBuilder(classLoader)
            hookGridSizeMigration(classLoader)
            hookDeviceGridState(classLoader)
            hookBubbleTextView(classLoader)
            hookFloatingIconView(classLoader)
            log("DockScaleHook installed successfully")
        } catch (e: Throwable) {
            logError("Failed to initialize DockScaleHook", e)
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
                            if (!isHotseatView(targetView)) return

                            val context = targetView.context ?: return
                            val sizePercent = getIntSetting(context, KEY_HOTSEAT_ICON_SIZE, 100)
                            if (sizePercent <= 0 || sizePercent == 100) return
                            val scale = sizePercent / 100f

                            
                            val positionOut = param.args[5] as? android.graphics.RectF ?: return
                            val cx = positionOut.centerX()
                            val cy = positionOut.centerY()
                            val newW = positionOut.width() * scale
                            val newH = positionOut.height() * scale
                            positionOut.set(cx - newW / 2, cy - newH / 2, cx + newW / 2, cy + newH / 2)
                            
                            log("FloatingIconViewHelper(Dock): scaled target to $scale")
                        } catch (e: Exception) {
                            logError("Error in FloatingIconViewHelper hook (Dock)", e)
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
                        if (!isHotseatView(targetView)) return

                        val context = targetView.context ?: return
                        val sizePercent = getIntSetting(context, KEY_HOTSEAT_ICON_SIZE, 100)
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
        } catch (e: Throwable) {
            logError("Failed to hook FloatingIconView logic in DockScaleHook", e)
        }
    }

    private fun hookGridSizeMigration(classLoader: ClassLoader) {
        try {
            val logicClass = XposedHelpers.findClass("com.android.launcher3.model.GridSizeMigrationLogic", classLoader)
            val dbReaderClass = XposedHelpers.findClass("com.android.launcher3.model.DbReader", classLoader)
            val dbHelperClass = XposedHelpers.findClass("com.android.launcher3.model.DatabaseHelper", classLoader)
            XposedHelpers.findAndHookMethod(
                logicClass,
                "migrateHotseat",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                dbReaderClass,
                dbReaderClass,
                dbHelperClass,
                List::class.java,
                object : XC_MethodReplacement() {
                    override fun replaceHookedMethod(param: MethodHookParam): Any? {
                        val context = android.app.ActivityThread.currentApplication()
                        val customIcons = getIntSetting(context, KEY_HOTSEAT_ICONS, 0)
                        return if (customIcons > 0) {
                            
                            null
                        } else {
                            XposedBridge.invokeOriginalMethod(param.method, param.thisObject, param.args)
                        }
                    }
                }
            )
        } catch (e: Throwable) {
            logError("Failed to hook GridSizeMigrationLogic.migrateHotseat", e)
        }
    }

    
    
    

    private fun hookInvariantDeviceProfile(classLoader: ClassLoader) {
        val idpClass = XposedHelpers.findClass(CLASS_INVARIANT_DEVICE_PROFILE, classLoader)
        
        
        
        XposedBridge.hookAllMethods(idpClass, "initGrid", object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val idp = param.thisObject
                val context = param.args.filterIsInstance<Context>().firstOrNull()
                    ?: getContextFromIDP(idp)
                    ?: return
                applyHotseatGridSettings(idp, context)
            }
        })
    }

    private fun applyHotseatGridSettings(idp: Any, context: Context) {
        try {
            val numIcons = getIntSetting(context, KEY_HOTSEAT_ICONS, 0)
            if (numIcons <= 0) return

            XposedHelpers.setIntField(idp, "numShownHotseatIcons", numIcons)
            XposedHelpers.setIntField(idp, "numDatabaseHotseatIcons", numIcons)
            updateWindowProfiles(idp, numIcons)
            log("Applied hotseat icons count: $numIcons")
        } catch (e: Exception) {
            logError("Error applying IDP settings", e)
        }
    }

    private fun hookDeviceGridState(classLoader: ClassLoader) {
        try {
            val gridStateClass = XposedHelpers.findClass("com.android.launcher3.model.DeviceGridState", classLoader)
            XposedBridge.hookAllConstructors(gridStateClass, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val context = android.app.ActivityThread.currentApplication() ?: return
                    val numIcons = getIntSetting(context, KEY_HOTSEAT_ICONS, 0)
                    if (numIcons <= 0) return
                    try {
                        XposedHelpers.setIntField(param.thisObject, "mNumHotseat", numIcons)
                    } catch (_: Throwable) {}
                }
            })
        } catch (e: Throwable) {
            logError("Failed to hook DeviceGridState", e)
        }
    }

    private fun hookDeviceProfileBuilder(classLoader: ClassLoader) {
        try {
            val builderClass = XposedHelpers.findClass(CLASS_DEVICE_PROFILE_BUILDER, classLoader)
            XposedHelpers.findAndHookMethod(builderClass, "build", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val deviceProfile = param.result ?: return
                    val context = android.app.ActivityThread.currentApplication() ?: return
                    applyHotseatDeviceProfileSettings(deviceProfile, context)
                }
            })
        } catch (e: Throwable) {
            logError("Failed to hook DeviceProfile.Builder", e)
        }
    }

    private fun applyHotseatDeviceProfileSettings(deviceProfile: Any, context: Context) {
        try {
            val numIcons = getIntSetting(context, KEY_HOTSEAT_ICONS, 0)
            if (numIcons <= 0) return

            XposedHelpers.setIntField(deviceProfile, "numShownHotseatIcons", numIcons)
            
            XposedHelpers.callMethod(deviceProfile, "updateHotseatWidthAndBorderSpace", numIcons)
            log("Applied hotseat icons count to DeviceProfile: $numIcons")
        } catch (e: Throwable) {
            logError("Failed to apply DeviceProfile hotseat settings", e)
        }
    }

    private fun updateWindowProfiles(idp: Any, numIcons: Int) {
        try {
            val supportedProfiles = XposedHelpers.getObjectField(idp, "supportedProfiles") as? List<*>
            supportedProfiles?.forEach { dp ->
                if (dp != null) {
                    try {
                        XposedHelpers.setIntField(dp, "numShownHotseatIcons", numIcons)
                    } catch (_: Throwable) {}
                }
            }
        } catch (e: Throwable) {
            logError("Failed to update window profiles for hotseat icons", e)
        }
    }

    
    
    

    private fun hookBubbleTextView(classLoader: ClassLoader) {
        val bubbleTextViewClass = XposedHelpers.findClass(CLASS_BUBBLE_TEXT_VIEW, classLoader)
        val fastBitmapDrawableClass = XposedHelpers.findClass(CLASS_FAST_BITMAP_DRAWABLE, classLoader)

        
        for (method in bubbleTextViewClass.declaredMethods) {
            if (method.name == "applyIconAndLabel") {
                XposedBridge.hookMethod(method, object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val view = param.thisObject as? TextView ?: return
                        val info = param.args.firstOrNull() ?: return
                        handleHotseatView(view, info)
                    }
                })
            }
        }

        
        XposedHelpers.findAndHookMethod(
            bubbleTextViewClass,
            "setIcon",
            fastBitmapDrawableClass,
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val view = param.thisObject as? TextView ?: return
                    val itemInfo = getItemInfoFromTag(view) ?: return
                    handleHotseatView(view, itemInfo)
                }
            }
        )

        
        
        
        val viewClass = android.view.View::class.java
        val scaleHook = object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                
                if (!bubbleTextViewClass.isInstance(param.thisObject)) return

                val view = param.thisObject as? TextView ?: return
                
                if (!isHotseatView(view)) return

                val context = view.context ?: return
                val sizePercent = getIntSetting(context, KEY_HOTSEAT_ICON_SIZE, 100)
                if (sizePercent <= 0 || sizePercent == 100) return

                val myScale = sizePercent / 100f
                val incomingFn = param.args[0] as Float
                
                param.args[0] = incomingFn * myScale
            }
        }

        XposedHelpers.findAndHookMethod(viewClass, "setScaleX", Float::class.javaPrimitiveType, scaleHook)
        XposedHelpers.findAndHookMethod(viewClass, "setScaleY", Float::class.javaPrimitiveType, scaleHook)
        
        
        XposedHelpers.findAndHookMethod(viewClass, "setVisibility", Int::class.javaPrimitiveType, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                if (!bubbleTextViewClass.isInstance(param.thisObject)) return
                val view = param.thisObject as? TextView ?: return
                val visibility = param.args[0] as Int
                
                if (visibility == 0) { 
                    val context = view.context ?: return
                    
                    
                    
                     applyIconSize(view, context)
                }
            }
        })
    }

    private fun isHotseatView(view: android.view.View): Boolean {
        
        val p = view.parent
        if (p != null) {
            var curr = p
            while (curr != null) {
                if (curr.javaClass.name.contains("Hotseat")) return true
                curr = curr.parent
            }
            
            
            return false
        }

        
        val tag = view.tag ?: return false
        return try {
            val container = XposedHelpers.getIntField(tag, "container")
            container == CONTAINER_HOTSEAT || container == CONTAINER_HOTSEAT_PREDICTION
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

    private fun handleHotseatView(view: TextView, itemInfo: Any) {
        try {
            
            
            if (!isHotseatView(view)) return

            val context = view.context ?: return

            
            applyIconSize(view, context)

            
        } catch (e: Exception) {
            logError("handleHotseatView failed", e)
        }
    }

    private fun applyIconSize(view: TextView, context: Context) {
        val sizePercent = getIntSetting(context, KEY_HOTSEAT_ICON_SIZE, 100)
        
        
        
        if (sizePercent <= 0) return
        
        
        
        
        
        view.scaleX = 1f
        view.scaleY = 1f
    }

    private fun getItemInfoFromTag(view: TextView): Any? {
        val parent = view.parent
        return if (parent is android.view.View && parent.javaClass.name.contains("FolderIcon")) {
            parent.tag
        } else {
            view.tag
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
        } catch (e: Throwable) {
            android.app.ActivityThread.currentApplication()
        }
    }
}