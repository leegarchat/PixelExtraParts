package org.pixel.customparts.xposed.hooks

import android.app.Activity
import android.content.Context
import android.graphics.Rect
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.ViewGroup
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import java.lang.reflect.Field
import java.lang.reflect.Method
import kotlin.math.roundToInt

object GridSizeHook {

    private const val TAG = "GridSizeHook"
    private const val KEY_HOME_ENABLE = "launcher_homepage_sizer_xposed"
    private const val KEY_HOME_COLS = "launcher_homepage_h"
    private const val KEY_HOME_ROWS = "launcher_homepage_v"
    private const val KEY_HOME_HIDE_TEXT = "launcher_homepage_hide_text"
    private const val KEY_MENU_ENABLE = "launcher_menupage_sizer_xposed"
    private const val KEY_MENU_COLS = "launcher_menupage_h"
    private const val KEY_MENU_SEARCH_COLS = "launcher_menupage_search_h"
    private const val KEY_MENU_HIDE_TEXT = "launcher_menupage_hide_text"
    private const val KEY_MENU_ROW_HEIGHT = "launcher_menupage_row_height"
    private var lastAppliedConfigHash: Int = 0

    fun init(lpparam: LoadPackageParam) {
        if (lpparam.packageName != "com.google.android.apps.nexuslauncher") return

        try {
            val launcherClass = "com.android.launcher3.Launcher"
            
            XposedHelpers.findAndHookMethod(
                launcherClass,
                lpparam.classLoader,
                "onResume",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val activity = param.thisObject as Activity
                        val rootView = activity.findViewById<View>(android.R.id.content)
                        rootView?.post {
                            applyGridSettings(activity)
                        } ?: applyGridSettings(activity)
                    }
                }
            )
        } catch (t: Throwable) {
            XposedBridge.log("$TAG: Init error: $t")
        }
    }

    private fun applyGridSettings(activity: Activity) {
        try {
            val isMenuEnabled = isEnabled(activity, KEY_MENU_ENABLE)
            val isHomeEnabled = isEnabled(activity, KEY_HOME_ENABLE)

            if (!isMenuEnabled && !isHomeEnabled) return
            val menuCols = getInt(activity, KEY_MENU_COLS, 0)
            val searchCols = getInt(activity, KEY_MENU_SEARCH_COLS, 0) 
            val rowHeightRaw = getInt(activity, KEY_MENU_ROW_HEIGHT, 100)
            val hideMenuText = isEnabled(activity, KEY_MENU_HIDE_TEXT)
            val homeCols = getInt(activity, KEY_HOME_COLS, 0)
            val homeRows = getInt(activity, KEY_HOME_ROWS, 0)
            val hideHomeText = isEnabled(activity, KEY_HOME_HIDE_TEXT)
            val currentConfigHash = "$menuCols|$searchCols|$rowHeightRaw|$hideMenuText|$homeCols|$homeRows|$hideHomeText".hashCode()
            if (lastAppliedConfigHash == currentConfigHash) return 

            val deviceProfile = getField(activity, "mDeviceProfile") ?: return
            val invariantDeviceProfile = getField(deviceProfile, "inv")
            val allAppsProfile = getField(deviceProfile, "mAllAppsProfile")
            var dpChanged = false
            val targetDpCols = if (searchCols > 0) searchCols else menuCols
            val targetListCols = if (menuCols > 0) menuCols else targetDpCols
            if (isHomeEnabled) {
                if (invariantDeviceProfile != null) {
                    if (homeCols > 0) {
                        setIntFieldSilently(invariantDeviceProfile, "numColumns", homeCols)
                        setIntFieldSilently(invariantDeviceProfile, "numShownHotseatIcons", homeCols)
                        dpChanged = true
                    }
                    if (homeRows > 0) {
                        setIntFieldSilently(invariantDeviceProfile, "numRows", homeRows)
                        dpChanged = true
                    }
                }
                
                if (hideHomeText) {
                    setIntFieldSilently(deviceProfile, "iconTextSizePx", 0)
                    dpChanged = true
                }
            }
            if (isMenuEnabled) {
                if (targetDpCols > 0) {
                    if (invariantDeviceProfile != null) {
                        setIntFieldSilently(invariantDeviceProfile, "numAllAppsColumns", targetDpCols)
                        setIntFieldSilently(invariantDeviceProfile, "numShownAllAppsColumns", targetDpCols)
                        setIntFieldSilently(invariantDeviceProfile, "numDatabaseAllAppsColumns", targetDpCols)
                    }
                    setIntFieldSilently(deviceProfile, "numAllAppsColumns", targetDpCols)
                    setIntFieldSilently(deviceProfile, "numShownAllAppsColumns", targetDpCols)
                    
                    if (allAppsProfile != null) {
                        setIntFieldSilently(allAppsProfile, "numShownAllAppsColumns", targetDpCols)
                    }
                    recalculateCellWidth(deviceProfile, allAppsProfile, targetDpCols)
                    dpChanged = true
                }

                val rowHeightScale = if (rowHeightRaw <= 0) 1.0f else rowHeightRaw / 100f
                if (rowHeightScale != 1.0f) {
                    applyHeightScale(deviceProfile, "allAppsCellHeightPx", rowHeightScale)
                    setIntFieldSilently(deviceProfile, "allAppsIconDrawablePaddingPx", 0)
                    if (allAppsProfile != null) {
                        applyHeightScale(allAppsProfile, "cellHeightPx", rowHeightScale)
                        setIntFieldSilently(allAppsProfile, "iconDrawablePaddingPx", 0)
                    }
                    dpChanged = true
                }

                if (hideMenuText) {
                    setIntFieldSilently(deviceProfile, "allAppsIconTextSizePx", 0)
                    setIntFieldSilently(deviceProfile, "allAppsIconDrawablePaddingPx", 0)
                    if (allAppsProfile != null) {
                        setFloatFieldSilently(allAppsProfile, "iconTextSizePx", 0f)
                        setIntFieldSilently(allAppsProfile, "iconDrawablePaddingPx", 0)
                    }
                    dpChanged = true
                }
            }

            if (dpChanged) {
                lastAppliedConfigHash = currentConfigHash
                triggerNativeUIUpdate(activity, deviceProfile, targetListCols)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply grid settings", e)
        }
    }

    private fun triggerNativeUIUpdate(activity: Activity, deviceProfile: Any, listCols: Int) {
        try {
            val getAppsViewMethod = findMethod(activity.javaClass, "getAppsView") ?: return
            getAppsViewMethod.isAccessible = true
            val appsView = getAppsViewMethod.invoke(activity) as? View ?: return
            val onDpChangedMethod = findMethod(appsView.javaClass, "onDeviceProfileChanged", deviceProfile.javaClass)
            
            var nativeCalled = false
            if (onDpChangedMethod != null) {
                onDpChangedMethod.isAccessible = true
                onDpChangedMethod.invoke(appsView, deviceProfile)
                nativeCalled = true
                Log.d(TAG, "Called native onDeviceProfileChanged success")
            }
            val currentDpCols = getIntField(deviceProfile, "numShownAllAppsColumns")
            
            if (!nativeCalled || (listCols > 0 && listCols != currentDpCols)) {
                manualForceUpdate(activity, appsView, deviceProfile, listCols)
            }
            appsView.post {
                fixSearchBar(appsView)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to trigger native update", e)
        }
    }
    
    private fun fixSearchBar(appsView: View) {
        try {
            var searchContainer: View? = null
            
            val mSearchContainerField = findField(appsView.javaClass, "mSearchContainer")
            if (mSearchContainerField != null) {
                mSearchContainerField.isAccessible = true
                searchContainer = mSearchContainerField.get(appsView) as? View
            }

            if (searchContainer == null) {
                val res = appsView.resources
                val pkg = appsView.context.packageName
                val searchId = res.getIdentifier("search_container_all_apps", "id", pkg)
                if (searchId != 0) {
                    searchContainer = appsView.findViewById(searchId)
                }
            }

            if (searchContainer != null) {
                searchContainer.setPadding(0, searchContainer.paddingTop, 0, searchContainer.paddingBottom)

                val params = searchContainer.layoutParams
                params.width = ViewGroup.LayoutParams.MATCH_PARENT
                
                if (params is ViewGroup.MarginLayoutParams) {
                    params.leftMargin = 0
                    params.rightMargin = 0
                }
                searchContainer.layoutParams = params
                searchContainer.requestLayout()
            }
            
        } catch (ignored: Exception) {
            Log.w(TAG, "Failed to fix search bar layout: $ignored")
        }
    }

    private fun manualForceUpdate(activity: Activity, appsView: View, deviceProfile: Any, overrideCols: Int) {
        try {
            val cols = if (overrideCols > 0) overrideCols else getIntField(deviceProfile, "numShownAllAppsColumns")
            if (cols <= 0) return
            updateAlphabeticalAppsList(appsView, cols)
            val listId = activity.resources.getIdentifier("apps_list_view", "id", activity.packageName)
            val recyclerView = if (listId != 0) appsView.findViewById<View>(listId) else null
            
            if (recyclerView != null) {
                val getLayoutManager = findMethod(recyclerView.javaClass, "getLayoutManager")
                val layoutManager = getLayoutManager?.invoke(recyclerView)
                if (layoutManager != null) {
                    val setSpanCount = findMethod(layoutManager.javaClass, "setSpanCount", Int::class.javaPrimitiveType!!)
                    setSpanCount?.invoke(layoutManager, cols)

                    val getSpanSizeLookup = findMethod(layoutManager.javaClass, "getSpanSizeLookup")
                    val spanSizeLookup = getSpanSizeLookup?.invoke(layoutManager)
                    if (spanSizeLookup != null) {
                        val invalidateCache = findMethod(spanSizeLookup.javaClass, "invalidateSpanIndexCache")
                        invalidateCache?.invoke(spanSizeLookup)
                    }
                }

                val getAdapter = findMethod(recyclerView.javaClass, "getAdapter")
                val setAdapter = findMethod(recyclerView.javaClass, "setAdapter", findClass("androidx.recyclerview.widget.RecyclerView\$Adapter", activity.classLoader))
                val adapter = getAdapter?.invoke(recyclerView)

                if (adapter != null && setAdapter != null) {
                    val notifyDataSetChanged = findMethod(adapter.javaClass, "notifyDataSetChanged")
                    notifyDataSetChanged?.invoke(adapter)
                    
                    setAdapter.invoke(recyclerView, null)
                    setAdapter.invoke(recyclerView, adapter)
                }
                
                val invalidateDecors = findMethod(recyclerView.javaClass, "invalidateItemDecorations")
                invalidateDecors?.invoke(recyclerView)
            }
            
            appsView.requestLayout()

        } catch (e: Exception) {
            Log.e(TAG, "Manual fallback failed", e)
        }
    }

    private fun updateAlphabeticalAppsList(appsView: View, cols: Int) {
        try {
            val mAppsField = findField(appsView.javaClass, "mApps") ?: return
            mAppsField.isAccessible = true
            val alphaAppsList = mAppsField.get(appsView) ?: return

            setIntFieldSilently(alphaAppsList, "mNumAppsPerRow", cols)
            
            val updateItemsMethod = findMethod(alphaAppsList.javaClass, "updateAdapterItems")
            updateItemsMethod?.isAccessible = true
            updateItemsMethod?.invoke(alphaAppsList)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update AlphabeticalAppsList", e)
        }
    }

    private fun recalculateCellWidth(dp: Any, allAppsProfile: Any?, cols: Int) {
        try {
            var widthPx = getIntField(dp, "availableWidthPx")
            if (widthPx <= 0) widthPx = getIntField(dp, "widthPx")
            if (widthPx <= 0) return

            var totalPadding = 0
            val paddingObj = getField(dp, "allAppsPadding")
            if (paddingObj is Rect) {
                totalPadding = paddingObj.left + paddingObj.right
            }

            var borderSpaceW = 0
            var borderSpaceObj: Any? = null
            if (allAppsProfile != null) borderSpaceObj = getField(allAppsProfile, "borderSpacePx")
            if (borderSpaceObj == null) borderSpaceObj = getField(dp, "allAppsBorderSpacePx")

            if (borderSpaceObj != null) {
                val xField = findField(borderSpaceObj.javaClass, "x")
                if (xField != null) {
                    xField.isAccessible = true
                    val value = xField.get(borderSpaceObj)
                    if (value is Int) borderSpaceW = value
                    else if (value is Float) borderSpaceW = value.toInt()
                }
            }

            val totalSpace = if (cols > 1) (cols - 1) * borderSpaceW else 0
            val availableForCells = widthPx - totalPadding - totalSpace
            
            if (availableForCells > 0) {
                val newCellWidth = availableForCells / cols
                setIntFieldSilently(dp, "allAppsCellWidthPx", newCellWidth)
                if (allAppsProfile != null) {
                    setIntFieldSilently(allAppsProfile, "cellWidthPx", newCellWidth)
                }
            }
        } catch (ignored: Exception) {}
    }

    private fun applyHeightScale(obj: Any, fieldName: String, scale: Float) {
        try {
            val field = findField(obj.javaClass, fieldName)
            if (field != null) {
                field.isAccessible = true
                if (field.type == Int::class.javaPrimitiveType) {
                    val current = field.getInt(obj)
                    if (current > 0) {
                        field.setInt(obj, (current * scale).roundToInt())
                    }
                }
            }
        } catch (ignored: Exception) {}
    }
    
    private fun findClass(className: String, classLoader: ClassLoader): Class<*>? {
        return try {
            Class.forName(className, false, classLoader)
        } catch (e: ClassNotFoundException) { null }
    }

    private fun isEnabled(context: Context, key: String): Boolean = 
        Settings.Secure.getInt(context.contentResolver, key, 0) == 1

    private fun getInt(context: Context, key: String, def: Int): Int = 
        Settings.Secure.getInt(context.contentResolver, key, def)

    private fun setIntFieldSilently(obj: Any?, fieldName: String, value: Int) {
        if (obj == null) return
        try {
            val field = findField(obj.javaClass, fieldName)
            field?.isAccessible = true
            field?.setInt(obj, value)
        } catch (ignored: Exception) {}
    }
    
    private fun setFloatFieldSilently(obj: Any?, fieldName: String, value: Float) {
        if (obj == null) return
        try {
            val field = findField(obj.javaClass, fieldName)
            field?.isAccessible = true
            field?.setFloat(obj, value)
        } catch (ignored: Exception) {}
    }

    private fun getIntField(obj: Any?, fieldName: String): Int {
        if (obj == null) return 0
        try {
            val field = findField(obj.javaClass, fieldName)
            field?.isAccessible = true
            return field?.getInt(obj) ?: 0
        } catch (ignored: Exception) {}
        return 0
    }

    private fun getField(obj: Any?, fieldName: String): Any? {
        if (obj == null) return null
        try {
            val field = findField(obj.javaClass, fieldName)
            field?.isAccessible = true
            return field?.get(obj)
        } catch (ignored: Exception) {}
        return null
    }

    private fun findField(clazz: Class<*>, fieldName: String): Field? {
        var current: Class<*>? = clazz
        while (current != null) {
            try {
                return current.getDeclaredField(fieldName)
            } catch (e: NoSuchFieldException) {
                current = current.superclass
            }
        }
        return null
    }
    
    private fun findMethod(clazz: Class<*>, methodName: String, vararg parameterTypes: Class<*>?): Method? {
        var current: Class<*>? = clazz
        while (current != null) {
            try {
                if (parameterTypes.isEmpty() || parameterTypes.any { it == null }) {
                    val methods = current.declaredMethods
                    val found = methods.find { it.name == methodName }
                    if (found != null) return found
                }
                @Suppress("UNCHECKED_CAST")
                val nonNullTypes = parameterTypes as Array<Class<*>>
                return current.getDeclaredMethod(methodName, *nonNullTypes)
            } catch (e: NoSuchMethodException) {
                current = current.superclass
            }
        }
        return null
    }
}