package org.pixel.customparts.hooks

import android.content.Context
import android.text.TextUtils
import android.view.View
import android.widget.TextView
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import org.pixel.customparts.core.BaseHook
import java.util.Collections

class GridSizeAppMenuHook : BaseHook() {

    override val hookId = "GridSizeAppMenuHook"
    override val priority = 80

    companion object {
        private const val CLASS_INVARIANT_DEVICE_PROFILE = "com.android.launcher3.InvariantDeviceProfile"
        private const val CLASS_DEVICE_PROFILE_BUILDER = "com.android.launcher3.DeviceProfile\$Builder"
        private const val CLASS_BUBBLE_TEXT_VIEW = "com.android.launcher3.BubbleTextView"
        private const val CLASS_ALPHABETICAL_APPS_LIST = "com.android.launcher3.allapps.AlphabeticalAppsList"
        private const val CLASS_PREDICTION_ROW_VIEW = "com.android.launcher3.appprediction.PredictionRowView"

        private const val KEY_MENU_ENABLE = "launcher_menupage_sizer"
        private const val KEY_MENU_COLS = "launcher_menupage_h"
        private const val KEY_MENU_ROW_HEIGHT = "launcher_menupage_row_height"
        private const val KEY_MENU_ICON_SIZE = "launcher_menupage_icon_size"
        private const val KEY_MENU_TEXT_MODE = "launcher_menupage_text_mode"

        
        private const val KEY_SUGGESTION_ICON_SIZE = "launcher_suggestion_icon_size"
        private const val KEY_SUGGESTION_TEXT_MODE = "launcher_suggestion_text_mode"
        private const val KEY_SUGGESTION_DISABLE = "launcher_suggestion_disable"

        
        private const val KEY_SEARCH_ICON_SIZE = "launcher_search_icon_size"
        private const val KEY_SEARCH_TEXT_MODE = "launcher_search_text_mode"

        private const val DISPLAY_ALL_APPS = 1
        private const val DISPLAY_SEARCH_RESULT = 6 
        private const val DISPLAY_SEARCH_RESULT_SMALL = 7
        private const val DISPLAY_PREDICTION = 8
        private const val DISPLAY_SEARCH_RESULT_APP_ROW = 9

        private const val EXTRA_BASE_ICON_SIZE = "cpr_base_icon_size"
    }

    override fun isEnabled(context: Context?): Boolean {
        
        return isSettingEnabled(context, KEY_MENU_ENABLE)
    }

    override fun onInit(classLoader: ClassLoader) {
        try {
            hookInvariantDeviceProfile(classLoader)
            hookDeviceProfileBuilder(classLoader)
            hookAlphabeticalAppsList(classLoader)
            hookBubbleTextView(classLoader)
            hookPredictionRowView(classLoader)
            log("AppMenu grid hooks installed")
        } catch (e: Throwable) {
            logError("Failed to initialize AppMenu grid hooks", e)
        }
    }

    private fun hookInvariantDeviceProfile(classLoader: ClassLoader) {
        try {
            val idpClass = XposedHelpers.findClass(CLASS_INVARIANT_DEVICE_PROFILE, classLoader)
            
            
            XposedBridge.hookAllMethods(
                idpClass,
                "initGrid",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val idp = param.thisObject
                        val context = param.args.filterIsInstance<Context>().firstOrNull()
                            ?: getContextFromIDP(idp)
                            ?: return
                        applyMenuGridSettingsToIDP(idp, context)
                    }
                }
            )
        } catch (e: Throwable) {
            logError("Failed to hook InvariantDeviceProfile", e)
        }
    }

    private fun hookDeviceProfileBuilder(classLoader: ClassLoader) {
        try {
            val builderClass = XposedHelpers.findClass(CLASS_DEVICE_PROFILE_BUILDER, classLoader)
            XposedHelpers.findAndHookMethod(
                builderClass,
                "build",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val deviceProfile = param.result ?: return
                        val context = getContextFromDP(deviceProfile) ?: return
                        applyAllAppsSettings(deviceProfile, context)
                    }
                }
            )
        } catch (e: Throwable) {
            logError("Failed to hook DeviceProfile.Builder", e)
        }
    }

    private fun hookAlphabeticalAppsList(classLoader: ClassLoader) {
        try {
            val appsListClass = XposedHelpers.findClass(CLASS_ALPHABETICAL_APPS_LIST, classLoader)
            XposedBridge.hookAllConstructors(appsListClass, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val appsList = param.thisObject
                    val context = getContextFromAppsList(appsList) ?: return
                    if (!isSettingEnabled(context, KEY_MENU_ENABLE)) return
                    val cols = getIntSetting(context, KEY_MENU_COLS, 0)
                    if (cols > 0) {
                        XposedHelpers.setIntField(appsList, "mNumAppsPerRowAllApps", cols)
                        log("AlphabeticalAppsList columns set to $cols")
                    }
                }
            })
        } catch (e: Throwable) {
            logError("Failed to hook AlphabeticalAppsList", e)
        }
    }

    private fun hookBubbleTextView(classLoader: ClassLoader) {
        try {
            val bubbleTextViewClass = XposedHelpers.findClass(CLASS_BUBBLE_TEXT_VIEW, classLoader)

            for (method in bubbleTextViewClass.declaredMethods) {
                if (method.name == "applyIconAndLabel") {
                    XposedBridge.hookMethod(method, object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            val view = param.thisObject as? TextView ?: return
                            val context = view.context ?: return
                            handleAllAppsView(view, context)
                        }
                    })
                }
            }
            
            
            
        } catch (e: Throwable) {
            logError("Failed to hook BubbleTextView", e)
        }
    }

    private fun hookPredictionRowView(classLoader: ClassLoader) {
        try {
            val predictionRowClass = XposedHelpers.findClass(CLASS_PREDICTION_ROW_VIEW, classLoader)
            XposedHelpers.findAndHookMethod(
                predictionRowClass,
                "setPredictedApps",
                List::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val view = param.thisObject as? View ?: return
                        val context = view.context ?: return
                        if (!isSettingEnabled(context, KEY_MENU_ENABLE)) return
                        if (getIntSetting(context, KEY_SUGGESTION_DISABLE, 0) == 1) {
                            param.args[0] = Collections.emptyList<Any>()
                        }
                    }
                }
            )
        } catch (e: Throwable) {
            logError("Failed to hook PredictionRowView", e)
        }
    }

    private fun applyMenuGridSettingsToIDP(idp: Any, context: Context) {
        if (!isSettingEnabled(context, KEY_MENU_ENABLE)) return
        val menuCols = getIntSetting(context, KEY_MENU_COLS, 0)
        if (menuCols > 0) {
            XposedHelpers.setIntField(idp, "numAllAppsColumns", menuCols)
            XposedHelpers.setIntField(idp, "numDatabaseAllAppsColumns", menuCols)
            log("AllApps columns in IDP set to $menuCols")
        }
    }

    private fun applyAllAppsSettings(deviceProfile: Any, context: Context) {
        if (!isSettingEnabled(context, KEY_MENU_ENABLE)) return
        val menuCols = getIntSetting(context, KEY_MENU_COLS, 0)
        val rowHeightRaw = getIntSetting(context, KEY_MENU_ROW_HEIGHT, 100)
        if (menuCols > 0) {
            XposedHelpers.setIntField(deviceProfile, "numShownAllAppsColumns", menuCols)
        }
        if (rowHeightRaw != 100 && rowHeightRaw > 0) {
            val scale = rowHeightRaw / 100f
            val allAppsProfile = XposedHelpers.getObjectField(deviceProfile, "mAllAppsProfile")
            if (allAppsProfile != null) {
                val currentHeight = XposedHelpers.getIntField(allAppsProfile, "cellHeightPx")
                if (currentHeight > 0) {
                    val newHeight = (currentHeight * scale).toInt()
                    XposedHelpers.setIntField(allAppsProfile, "cellHeightPx", newHeight)
                    log("AllApps cell height scaled to $newHeight (${rowHeightRaw}%)")
                }
            }
        }
    }

    



    private fun handleAllAppsView(view: TextView, context: Context) {
        try {
            if (!isSettingEnabled(context, KEY_MENU_ENABLE)) return
            val mDisplay = XposedHelpers.getIntField(view, "mDisplay")
            
            
            var keyIconSize: String? = null
            var keyTextMode: String? = null
            
            when (mDisplay) {
                DISPLAY_ALL_APPS -> {
                    keyIconSize = KEY_MENU_ICON_SIZE
                    keyTextMode = KEY_MENU_TEXT_MODE
                }
                DISPLAY_PREDICTION -> {
                    keyIconSize = KEY_SUGGESTION_ICON_SIZE
                    keyTextMode = KEY_SUGGESTION_TEXT_MODE
                }
                DISPLAY_SEARCH_RESULT,
                DISPLAY_SEARCH_RESULT_SMALL,
                DISPLAY_SEARCH_RESULT_APP_ROW -> {
                    keyIconSize = KEY_SEARCH_ICON_SIZE
                    keyTextMode = KEY_SEARCH_TEXT_MODE
                }
                else -> return 
            }

            
            if (keyIconSize != null) {
                applyIconSize(view, context, keyIconSize)
            }

            
            if (keyTextMode != null) {
                applyTextMode(view, context, keyTextMode)
            }
        } catch (e: Exception) {}
    }

    private fun applyIconSize(view: TextView, context: Context, keySize: String) {
        try {
            val sizePercent = getIntSetting(context, keySize, 100)
            val currentSize = XposedHelpers.getIntField(view, "mIconSize")
            val storedBaseSize = XposedHelpers.getAdditionalInstanceField(view, EXTRA_BASE_ICON_SIZE) as? Int
            val baseSize = storedBaseSize ?: currentSize
            if (storedBaseSize == null) {
                XposedHelpers.setAdditionalInstanceField(view, EXTRA_BASE_ICON_SIZE, baseSize)
            }

            val newSize = if (sizePercent == 100 || sizePercent <= 0) {
                baseSize
            } else {
                (baseSize * sizePercent / 100f).toInt().coerceAtLeast(1)
            }

            XposedHelpers.setIntField(view, "mIconSize", newSize)

            val drawables = (view as TextView).compoundDrawables
            val topDrawable = drawables[1]
            if (topDrawable != null) {
                topDrawable.setBounds(0, 0, newSize, newSize)
                view.setCompoundDrawables(drawables[0], topDrawable, drawables[2], drawables[3])
            }
        } catch (e: Throwable) {}
    }

    private fun applyTextMode(view: TextView, context: Context, keyMode: String) {
        val mode = getIntSetting(context, keyMode, 0)
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
                    try { view.isSelected = true } catch (_: Throwable) {}
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

    private fun getContextFromDP(dp: Any): Context? {
        return try {
            val info = XposedHelpers.getObjectField(dp, "mInfo")
            if (info != null) {
                val context = XposedHelpers.getObjectField(info, "context")
                if (context is Context) return context
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    private fun getContextFromAppsList(appsList: Any): Context? {
        return try {
            val activityContext = XposedHelpers.getObjectField(appsList, "mActivityContext")
            if (activityContext != null) {
                val asContext = activityContext.javaClass.getMethod("asContext")
                return asContext.invoke(activityContext) as? Context
            }
            null
        } catch (e: Exception) {
            null
        }
    }
}