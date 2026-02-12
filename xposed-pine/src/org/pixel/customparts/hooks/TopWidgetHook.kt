package org.pixel.customparts.hooks

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Point
import android.view.ViewGroup
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import org.json.JSONObject
import org.pixel.customparts.core.BaseHook
import java.util.ArrayList

class TopWidgetHook : BaseHook() {

    override val hookId = "TopWidgetHook"
    override val priority = 70

    companion object {
        private const val SETTING_KEY = "launcher_disable_top_widget"
        private const val PREFS_NAME = "top_row_keeper"
        private const val PREF_GRID_ROWS = "last_grid_rows"
        private const val PREF_GRID_COLS = "last_grid_cols"
        private const val PREF_SAVED_ITEMS = "saved_items_json"
    }

    override fun isEnabled(context: Context?): Boolean {
        return true
    }

    override fun onInit(classLoader: ClassLoader) {
        try {
            val workspaceClass = XposedHelpers.findClass("com.android.launcher3.Workspace", classLoader)
            XposedHelpers.findAndHookMethod(
                workspaceClass,
                "bindAndInitFirstWorkspaceScreen",
                object : XC_MethodReplacement() {
                    override fun replaceHookedMethod(param: MethodHookParam): Any? {
                        val workspace = param.thisObject as ViewGroup
                        val context = workspace.context
                        val settingValue = try {
                            getIntSetting(context, SETTING_KEY, 0)
                        } catch (e: Exception) {
                            0
                        }

                        if (settingValue == 1) {
                            try {
                                val childCount = workspace.childCount
                                XposedHelpers.callMethod(workspace, "insertNewWorkspaceScreen", 0, childCount)
                            } catch (e: Exception) {
                                log("Failed to manually insert workspace screen: " + e.message)
                                XposedBridge.log(e)
                            }
                            return null
                        } else {
                            return XposedBridge.invokeOriginalMethod(param.method, param.thisObject, param.args)
                        }
                    }
                }
            )
            try {
                val logicClass = XposedHelpers.findClass("com.android.launcher3.model.GridSizeMigrationLogic", classLoader)
                val itemsToPlaceClass = XposedHelpers.findClass("com.android.launcher3.model.GridSizeMigrationLogic\$WorkspaceItemsToPlace", classLoader)
                val occupancyClass = XposedHelpers.findClass("com.android.launcher3.util.GridOccupancy", classLoader)

                XposedHelpers.findAndHookMethod(
                    logicClass,
                    "solveGridPlacement",
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    List::class.java,
                    List::class.java,
                    object : XC_MethodReplacement() {
                        override fun replaceHookedMethod(param: MethodHookParam): Any {
                            val screenId = param.args[0] as Int
                            val trgX = param.args[1] as Int
                            val trgY = param.args[2] as Int
                            val remaining = param.args[3] as MutableList<Any>
                            val placed = param.args[4] as List<Any>?

                            var allowTopRow = false
                            try {
                                val context = android.app.ActivityThread.currentApplication()
                                if (context != null) {
                                    val setting = getIntSetting(context, SETTING_KEY, 0)
                                    allowTopRow = (setting == 1)
                                }
                            } catch (e: Throwable) {}

                            if (screenId == 0 && allowTopRow) {
                                val solutionConstructor = itemsToPlaceClass.getConstructor(List::class.java, List::class.java)
                                val placementSolution = ArrayList<Any>()
                                val result = solutionConstructor.newInstance(remaining, placementSolution)
                                val occupancyConstructor = occupancyClass.getConstructor(Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                                val gridOccupancy = occupancyConstructor.newInstance(trgX, trgY)
                                val nextEmptyCell = Point(0, 0)
                                
                                if (placed != null) {
                                    for (dbEntry in placed) {
                                        val cellX = XposedHelpers.getIntField(dbEntry, "cellX")
                                        val cellY = XposedHelpers.getIntField(dbEntry, "cellY")
                                        val spanX = XposedHelpers.getIntField(dbEntry, "spanX")
                                        val spanY = XposedHelpers.getIntField(dbEntry, "spanY")
                                        XposedHelpers.callMethod(gridOccupancy, "markCells", true, cellX, cellY, spanX, spanY)
                                    }
                                }
                                
                                val it = remaining.iterator()
                                while (it.hasNext()) {
                                    val dbEntry = it.next()
                                    val minSpanX = XposedHelpers.getIntField(dbEntry, "minSpanX")
                                    val minSpanY = XposedHelpers.getIntField(dbEntry, "minSpanY")
                                    
                                    if (minSpanX > trgX || minSpanY > trgY) {
                                        it.remove()
                                        continue
                                    }
                                    
                                    var foundCellX = -1
                                    var foundCellY = -1
                                    var y = nextEmptyCell.y
                                    while (y < trgY) {
                                        var x = if (y == nextEmptyCell.y) nextEmptyCell.x else 0
                                        while (x < trgX) {
                                            val fits = XposedHelpers.callMethod(gridOccupancy, "isRegionVacant", x, y, minSpanX, minSpanY) as Boolean
                                            if (fits) {
                                                foundCellX = x
                                                foundCellY = y
                                                break
                                            }
                                            x++
                                        }
                                        if (foundCellX != -1) break
                                        y++
                                    }
                                    
                                    if (foundCellX != -1) {
                                        XposedHelpers.setIntField(dbEntry, "screenId", screenId)
                                        XposedHelpers.setIntField(dbEntry, "cellX", foundCellX)
                                        XposedHelpers.setIntField(dbEntry, "cellY", foundCellY)
                                        XposedHelpers.setIntField(dbEntry, "spanX", minSpanX)
                                        XposedHelpers.setIntField(dbEntry, "spanY", minSpanY)
                                        XposedHelpers.callMethod(gridOccupancy, "markCells", true, foundCellX, foundCellY, minSpanX, minSpanY)
                                        nextEmptyCell.set(foundCellX + minSpanX, foundCellY)
                                        placementSolution.add(dbEntry)
                                        it.remove()
                                    }
                                }
                                return result
                            } else {
                                return XposedBridge.invokeOriginalMethod(param.method, null, param.args)
                            }
                        }
                    }
                )
            } catch (e: Throwable) {
                log("Error hooking GridSizeMigrationLogic: " + e.message)
            }

            try {
                hookPersistence(classLoader)
            } catch (e: Throwable) {
                log("Error hooking Persistence logic: " + e.message)
            }
            
            try {
                hookGridOccupancy(classLoader)
            } catch (e: Throwable) {
                log("Error hooking GridOccupancy: " + e.message)
            }
            
        } catch (e: Throwable) {
            log("Error hooking Workspace: " + e.message)
            XposedBridge.log(e)
        }
    }

    private fun hookPersistence(classLoader: ClassLoader) {
        val modelWriterClass = XposedHelpers.findClass("com.android.launcher3.model.ModelWriter", classLoader)
        val loaderCursorClass = XposedHelpers.findClass("com.android.launcher3.model.LoaderCursor", classLoader)
        val itemInfoClass = XposedHelpers.findClass("com.android.launcher3.model.data.ItemInfo", classLoader)
        val intSparseArrayMapClass = XposedHelpers.findClass("com.android.launcher3.util.IntSparseArrayMap", classLoader)
        val loaderMemoryLoggerClass = XposedHelpers.findClass("com.android.launcher3.model.LoaderMemoryLogger", classLoader)

        XposedHelpers.findAndHookMethod(loaderCursorClass, "checkAndAddItem",
            itemInfoClass, intSparseArrayMapClass, loaderMemoryLoggerClass,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    try {
                        val cursor = param.thisObject
                        val item = param.args[0]
                        val context = XposedHelpers.getObjectField(cursor, "mContext") as Context
                        
                        val settingValue = getIntSetting(context, SETTING_KEY, 0)
                        if (settingValue != 1) return
                        
                        val container = XposedHelpers.getIntField(item, "container")
                        if (container != -100) return
                        
                        val screenId = XposedHelpers.getIntField(item, "screenId")
                        if (screenId != 0) return
                        
                        val cellY = XposedHelpers.getIntField(item, "cellY")
                        if (cellY != 0) return
                        
                        val mOccupied = XposedHelpers.getObjectField(cursor, "mOccupied")
                        val gridOccupancy = XposedHelpers.callMethod(mOccupied, "get", screenId)
                        
                        if (gridOccupancy != null) {
                            val cellX = XposedHelpers.getIntField(item, "cellX")
                            val spanX = XposedHelpers.getIntField(item, "spanX")
                            val spanY = XposedHelpers.getIntField(item, "spanY")
                            
                            XposedHelpers.callMethod(gridOccupancy, "markCells", false, cellX, cellY, spanX, spanY)
                            
                            log("checkAndAddItem: Temporarily freed cells for item at ($cellX, $cellY) on screen $screenId")
                            
                            param.setObjectExtra("gridOccupancy", gridOccupancy)
                            param.setObjectExtra("cellX", cellX)
                            param.setObjectExtra("cellY", cellY)
                            param.setObjectExtra("spanX", spanX)
                            param.setObjectExtra("spanY", spanY)
                        }
                    } catch (e: Throwable) {
                        log("checkAndAddItem before error: " + e.message)
                    }
                }
                
                override fun afterHookedMethod(param: MethodHookParam) {
                    try {
                        val gridOccupancy = param.getObjectExtra("gridOccupancy")
                        if (gridOccupancy != null) {
                            val cellX = param.getObjectExtra("cellX") as Int
                            val cellY = param.getObjectExtra("cellY") as Int
                            val spanX = param.getObjectExtra("spanX") as Int
                            val spanY = param.getObjectExtra("spanY") as Int
                            
                            XposedHelpers.callMethod(gridOccupancy, "markCells", true, cellX, cellY, spanX, spanY)
                        }
                    } catch (e: Throwable) {
                    }
                }
            })
        
        log("Hooked LoaderCursor.checkAndAddItem")

        XposedHelpers.findAndHookMethod(modelWriterClass, "addItemToDatabase", 
            itemInfoClass, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val context = XposedHelpers.getObjectField(param.thisObject, "mContext") as Context
                    val item = param.args[0]
                    saveWorkspaceItem(context, item)
                }
            })

        XposedHelpers.findAndHookMethod(modelWriterClass, "modifyItemInDatabase", 
            itemInfoClass, 
            Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, 
            Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val context = XposedHelpers.getObjectField(param.thisObject, "mContext") as Context
                    val item = param.args[0]
                    saveWorkspaceItem(context, item)
                }
            })
            
        XposedHelpers.findAndHookMethod(modelWriterClass, "deleteItemsFromDatabase", 
            java.util.Collection::class.java, String::class.java,
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val context = XposedHelpers.getObjectField(param.thisObject, "mContext") as Context
                    val items = param.args[0] as Collection<*>
                    for (item in items) {
                        if (item != null) {
                            deleteItemFromBackup(context, item)
                        }
                    }
                }
            })

        XposedHelpers.findAndHookMethod(loaderCursorClass, "applyCommonProperties", 
            itemInfoClass,
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    try {
                        val cursor = param.thisObject
                        val item = param.args[0]
                        val context = XposedHelpers.getObjectField(cursor, "mContext") as Context
                        
                        val settingValue = getIntSetting(context, SETTING_KEY, 0)
                        if (settingValue != 1) return
                        
                        val itemId = XposedHelpers.getIntField(item, "id")
                        val container = XposedHelpers.getIntField(item, "container")
                        
                        if (container != -100) return
                        
                        val prefs = getPrefs(context)
                        val jsonStr = prefs.getString(PREF_SAVED_ITEMS, "{}")
                        if (jsonStr == "{}") return
                        
                        val json = JSONObject(jsonStr)
                        val idStr = itemId.toString()
                        
                        if (json.has(idStr)) {
                            val saved = json.getJSONObject(idStr)
                            val savedX = saved.getInt("cellX")
                            val savedY = saved.getInt("cellY")
                            val savedScreen = saved.getInt("screenId")
                            
                            val currentX = XposedHelpers.getIntField(item, "cellX")
                            val currentY = XposedHelpers.getIntField(item, "cellY")
                            val currentScreen = XposedHelpers.getIntField(item, "screenId")
                            
                            if (currentScreen != savedScreen || currentX != savedX || currentY != savedY) {
                                XposedHelpers.setIntField(item, "screenId", savedScreen)
                                XposedHelpers.setIntField(item, "cellX", savedX)
                                XposedHelpers.setIntField(item, "cellY", savedY)
                                XposedHelpers.setIntField(item, "spanX", saved.getInt("spanX"))
                                XposedHelpers.setIntField(item, "spanY", saved.getInt("spanY"))
                                log("Restored item $itemId position to ($savedScreen, $savedX, $savedY)")
                            }
                        }
                    } catch (e: Throwable) {
                    }
                }
            })

        val loaderTaskClass = XposedHelpers.findClass("com.android.launcher3.model.LoaderTask", classLoader)
        val methods = loaderTaskClass.methods.filter { it.name == "loadAllSurfacesOrdered" }
        if (methods.isNotEmpty()) {
            XposedBridge.hookMethod(methods[0], object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val task = param.thisObject
                    val context = XposedHelpers.getObjectField(task, "mContext") as Context
                    val idp = XposedHelpers.getObjectField(task, "mIDP")
                    val numRows = XposedHelpers.getIntField(idp, "numRows")
                    val numCols = XposedHelpers.getIntField(idp, "numColumns")
                    
                    checkGridAndResetIfNeeded(context, numRows, numCols)
                }
            })
        } else {
            log("Could not find loadAllSurfacesOrdered method")
        }
    }

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun hookGridOccupancy(classLoader: ClassLoader) {
        val gridOccupancyClass = XposedHelpers.findClass("com.android.launcher3.util.GridOccupancy", classLoader)
        
        XposedHelpers.findAndHookMethod(gridOccupancyClass, "isRegionVacant",
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val y = param.args[1] as Int
                    val spanY = param.args[3] as Int
                    if (y > 0 || (y + spanY) > 1) return
                    if (param.result != false) return
                    
                    try {
                        val context = android.app.ActivityThread.currentApplication() ?: return
                        val settingValue = getIntSetting(context, SETTING_KEY, 0)
                        if (settingValue != 1) return
                        val x = param.args[0] as Int
                        val spanX = param.args[2] as Int
                        val gridOccupancy = param.thisObject
                        val cells = XposedHelpers.getObjectField(gridOccupancy, "cells") as Array<BooleanArray>
                        val countX = XposedHelpers.getIntField(gridOccupancy, "mCountX")
                        val countY = XposedHelpers.getIntField(gridOccupancy, "mCountY")
                        val endX = x + spanX - 1
                        val endY = y + spanY - 1
                        if (x < 0 || y < 0 || endX >= countX || endY >= countY) return
                        
                        var wouldBeVacant = true
                        for (cx in x..endX) {
                            for (cy in y..endY) {
                                if (cy > 0 && cells[cx][cy]) {
                                    wouldBeVacant = false
                                    break
                                }
                            }
                            if (!wouldBeVacant) break
                        }
                        
                        if (wouldBeVacant) {
                            param.result = true
                            log("isRegionVacant: Forcing row 0 region ($x,$y,$spanX,$spanY) as vacant")
                        }
                    } catch (e: Throwable) {
                    }
                }
            })
        
        log("Hooked GridOccupancy.isRegionVacant")
    }

    private fun checkGridAndResetIfNeeded(context: Context, currentRows: Int, currentCols: Int) {
        val prefs = getPrefs(context)
        val lastRows = prefs.getInt(PREF_GRID_ROWS, -1)
        val lastCols = prefs.getInt(PREF_GRID_COLS, -1)
        
        if (lastRows != -1 && (lastRows != currentRows || lastCols != currentCols)) {
            log("Grid size changed ($lastRows x $lastCols -> $currentRows x $currentCols). Clearing backup.")
            prefs.edit().remove(PREF_SAVED_ITEMS).apply()
        }
        
        prefs.edit().putInt(PREF_GRID_ROWS, currentRows).putInt(PREF_GRID_COLS, currentCols).apply()
    }

    private fun saveWorkspaceItem(context: Context, item: Any) {
        try {
            val container = XposedHelpers.getIntField(item, "container")
            if (container == -100) {
                val prefs = getPrefs(context)
                val jsonStr = prefs.getString(PREF_SAVED_ITEMS, "{}")
                val json = JSONObject(jsonStr)
                
                val itemId = XposedHelpers.getIntField(item, "id").toString()
                val itemJson = serializeItem(item)
                
                json.put(itemId, itemJson)
                prefs.edit().putString(PREF_SAVED_ITEMS, json.toString()).apply()
                log("Tracked/Saved item $itemId state")
            } else {
                val itemId = XposedHelpers.getIntField(item, "id").toString()
                val prefs = getPrefs(context)
                val jsonStr = prefs.getString(PREF_SAVED_ITEMS, "{}")
                val json = JSONObject(jsonStr)
                if (json.has(itemId)) {
                    json.remove(itemId)
                    prefs.edit().putString(PREF_SAVED_ITEMS, json.toString()).apply()
                    log("Removed backup for moved item $itemId")
                }
            }
        } catch (e: Throwable) {
            log("Error saving item: " + e.message)
        }
    }

    private fun deleteItemFromBackup(context: Context, item: Any) {
        try {
            val itemId = XposedHelpers.getIntField(item, "id").toString()
            val prefs = getPrefs(context)
            val jsonStr = prefs.getString(PREF_SAVED_ITEMS, "{}")
            val json = JSONObject(jsonStr)
            
            if (json.has(itemId)) {
                json.remove(itemId)
                prefs.edit().putString(PREF_SAVED_ITEMS, json.toString()).apply()
                log("Deleted backup for item $itemId")
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun serializeItem(item: Any): JSONObject {
        val json = JSONObject()
        json.put("id", XposedHelpers.getIntField(item, "id"))
        json.put("itemType", XposedHelpers.getIntField(item, "itemType"))
        json.put("cellX", XposedHelpers.getIntField(item, "cellX"))
        json.put("cellY", XposedHelpers.getIntField(item, "cellY"))
        json.put("spanX", XposedHelpers.getIntField(item, "spanX"))
        json.put("spanY", XposedHelpers.getIntField(item, "spanY"))
        json.put("screenId", XposedHelpers.getIntField(item, "screenId"))
        
        try {
            val title = XposedHelpers.getObjectField(item, "title") as? CharSequence
            if (title != null) json.put("title", title.toString())
        } catch (e: Throwable) { }
        
        try {
            val itemType = XposedHelpers.getIntField(item, "itemType")
            if (itemType == 4) { 
                json.put("appWidgetId", XposedHelpers.getIntField(item, "appWidgetId"))
                val provider = XposedHelpers.getObjectField(item, "providerName") as? ComponentName
                if (provider != null) json.put("provider", provider.flattenToString())
            }
        } catch (e: Throwable) { }

        try {
            val intent = XposedHelpers.getObjectField(item, "intent") as? Intent
            if (intent != null) json.put("intent", intent.toUri(0))
        } catch (e: Throwable) { }
        
        return json
    }
}