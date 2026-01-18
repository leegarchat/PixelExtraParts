package com.android.internal.customNativeExtractParts;

import android.app.Activity;
import android.content.Context;
import android.graphics.Rect;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * Native implementation for Custom Grid Size customization.
 * Logic ported from GridSizeNativeEmulationHook.kt (OPTIMIZED & LOGIC FIXED version).
 * * Logic Summary:
 * 1. DeviceProfile is configured with 'globalCols' (Search Priority) to ensure Header/Search looks correct.
 * 2. Cell Width is recalculated based on 'listCols' (Menu Priority) to ensure Grid fills the screen width.
 * 3. RecyclerView SpanCount is forced to 'listCols'.
 */
public class LauncherGridSize {

    private static final String TAG = "LauncherGridSize";

    // Keys Home
    private static final String KEY_HOME_ENABLE = "launcher_homepage_sizer";
    private static final String KEY_HOME_COLS = "launcher_homepage_h";
    private static final String KEY_HOME_ROWS = "launcher_homepage_v";
    private static final String KEY_HOME_HIDE_TEXT = "launcher_homepage_hide_text";

    // Keys Menu
    private static final String KEY_MENU_ENABLE = "launcher_menupage_sizer";
    private static final String KEY_MENU_COLS = "launcher_menupage_h";
    private static final String KEY_MENU_SEARCH_COLS = "launcher_menupage_search_h";
    private static final String KEY_MENU_HIDE_TEXT = "launcher_menupage_hide_text";
    private static final String KEY_MENU_ROW_HEIGHT = "launcher_menupage_row_height";

    private static int lastAppliedConfigHash = 0;

    // --- Cache ---
    private static final Map<String, Field> sFieldCache = new HashMap<>();
    private static final Map<String, Method> sMethodCache = new HashMap<>();
    
    // Settings cache (Simplified for native: reload if attach called)
    // In a real native service, you might want to observe changes, but for this hook structure,
    // we read on every attach/layout pass, so caching values is good.
    private static boolean sSettingsLoaded = false;
    private static boolean sIsMenuEnabled;
    private static boolean sIsHomeEnabled;
    private static int sMenuCols;
    private static int sSearchCols;
    private static int sRowHeightRaw;
    private static boolean sHideMenuText;
    private static int sHomeCols;
    private static int sHomeRows;
    private static boolean sHideHomeText;

    public static void attach(final Activity activity) {
        if (activity == null) return;
        View contentView = activity.findViewById(android.R.id.content);
        if (contentView != null) {
            contentView.post(() -> applyGridSettings(activity));
        } else {
            applyGridSettings(activity);
        }
    }

    private static void loadSettings(Context context) {
        try {
            sIsMenuEnabled = isEnabled(context, KEY_MENU_ENABLE);
            sIsHomeEnabled = isEnabled(context, KEY_HOME_ENABLE);
            
            // Always load all if either is enabled, or just return to be safe
            if (sIsMenuEnabled || sIsHomeEnabled) {
                sMenuCols = getInt(context, KEY_MENU_COLS, 0);
                sSearchCols = getInt(context, KEY_MENU_SEARCH_COLS, 0);
                sRowHeightRaw = getInt(context, KEY_MENU_ROW_HEIGHT, 100);
                sHideMenuText = isEnabled(context, KEY_MENU_HIDE_TEXT);
                
                sHomeCols = getInt(context, KEY_HOME_COLS, 0);
                sHomeRows = getInt(context, KEY_HOME_ROWS, 0);
                sHideHomeText = isEnabled(context, KEY_HOME_HIDE_TEXT);
            }
            sSettingsLoaded = true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to load settings", e);
        }
    }

    private static void applyGridSettings(Activity activity) {
        try {
            // 1. Load Settings
            if (!sSettingsLoaded) {
                loadSettings(activity);
            }

            if (!sIsMenuEnabled && !sIsHomeEnabled) return;

            // Generate Hash
            int currentConfigHash = (sMenuCols + "|" + sSearchCols + "|" + sRowHeightRaw + "|" + sHideMenuText + "|" 
                                + sHomeCols + "|" + sHomeRows + "|" + sHideHomeText).hashCode();
            
            if (lastAppliedConfigHash == currentConfigHash) return;

            // 2. Reflection Access (Cached)
            Object deviceProfile = getField(activity, "mDeviceProfile");
            if (deviceProfile == null) return;

            Object invariantDeviceProfile = getField(deviceProfile, "inv");
            Object allAppsProfile = getField(deviceProfile, "mAllAppsProfile");

            boolean dpChanged = false;

            // --- LOGIC CORRECTION (Hybrid Approach) ---
            // Global DP priority: Search > Menu > 4. 
            // This ensures Header/Search gets the correct number of columns.
            int globalCols = (sSearchCols > 0) ? sSearchCols : ((sMenuCols > 0) ? sMenuCols : 4);
            
            // List priority: Menu > Global.
            // This is used for RecyclerView SpanCount AND for cell width calculation.
            int listCols = (sMenuCols > 0) ? sMenuCols : globalCols;

            // --- HOMEPAGE LOGIC ---
            if (sIsHomeEnabled) {
                if (invariantDeviceProfile != null) {
                    if (sHomeCols > 0) {
                        setIntFieldSilently(invariantDeviceProfile, "numColumns", sHomeCols);
                        setIntFieldSilently(invariantDeviceProfile, "numShownHotseatIcons", sHomeCols);
                        dpChanged = true;
                    }
                    if (sHomeRows > 0) {
                        setIntFieldSilently(invariantDeviceProfile, "numRows", sHomeRows);
                        dpChanged = true;
                    }
                }
                
                if (sHideHomeText) {
                    setIntFieldSilently(deviceProfile, "iconTextSizePx", 0);
                    dpChanged = true;
                }
            }

            // --- MENU LOGIC ---
            if (sIsMenuEnabled) {
                if (globalCols > 0) {
                    // 1. Set DP columns to GLOBAL (Search) count.
                    // This makes the Search Header layout with 'globalCols' columns.
                    applyColumnsToDp(invariantDeviceProfile, deviceProfile, allAppsProfile, globalCols);
                    
                    // 2. Set DP cell width based on LIST (Menu) count.
                    // This is the hybrid fix: DP thinks it has 5 cols (for Search), 
                    // but each cell is wide enough for 4 cols (for List).
                    // This prevents the list from "flying left".
                    recalculateCellWidth(deviceProfile, allAppsProfile, listCols);
                    
                    dpChanged = true;
                }

                float rowHeightScale = (sRowHeightRaw <= 0) ? 1.0f : sRowHeightRaw / 100f;
                if (rowHeightScale != 1.0f) {
                    applyHeightScale(deviceProfile, "allAppsCellHeightPx", rowHeightScale);
                    setIntFieldSilently(deviceProfile, "allAppsIconDrawablePaddingPx", 0);
                    if (allAppsProfile != null) {
                        applyHeightScale(allAppsProfile, "cellHeightPx", rowHeightScale);
                        setIntFieldSilently(allAppsProfile, "iconDrawablePaddingPx", 0);
                    }
                    dpChanged = true;
                }

                if (sHideMenuText) {
                    setIntFieldSilently(deviceProfile, "allAppsIconTextSizePx", 0);
                    setIntFieldSilently(deviceProfile, "allAppsIconDrawablePaddingPx", 0);
                    if (allAppsProfile != null) {
                        setFloatFieldSilently(allAppsProfile, "iconTextSizePx", 0f);
                        setIntFieldSilently(allAppsProfile, "iconDrawablePaddingPx", 0);
                    }
                    dpChanged = true;
                }
            }

            // --- Trigger Update ---
            if (dpChanged) {
                lastAppliedConfigHash = currentConfigHash;
                
                View appsView = getAppsView(activity);
                
                if (appsView != null) {
                    // 1. Trigger Global Update (Updates Search Header + List with hybrid config)
                    triggerNativeOnDpChanged(appsView, deviceProfile);
                    
                    // 2. Force List to use listCols (if different from global/search)
                    // This fixes the RecyclerView grid span count mismatch.
                    if (listCols != globalCols) {
                        manualForceUpdateList(activity, appsView, listCols);
                    }
                    
                    // 3. Fix Search Bar Width
                    appsView.post(() -> fixSearchBar(appsView));
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "Failed to apply grid settings", e);
        }
    }

    // --- Helpers ---

    private static void applyColumnsToDp(Object invDp, Object dp, Object allAppsProfile, int cols) {
        if (invDp != null) {
            setIntFieldSilently(invDp, "numAllAppsColumns", cols);
            setIntFieldSilently(invDp, "numShownAllAppsColumns", cols);
            setIntFieldSilently(invDp, "numDatabaseAllAppsColumns", cols);
        }
        setIntFieldSilently(dp, "numAllAppsColumns", cols);
        setIntFieldSilently(dp, "numShownAllAppsColumns", cols);
        if (allAppsProfile != null) {
            setIntFieldSilently(allAppsProfile, "numShownAllAppsColumns", cols);
        }
    }

    private static View getAppsView(Activity activity) {
        try {
            Method getAppsViewMethod = findMethod(activity.getClass(), "getAppsView");
            if (getAppsViewMethod == null) return null;
            getAppsViewMethod.setAccessible(true);
            return (View) getAppsViewMethod.invoke(activity);
        } catch (Exception e) {
            return null;
        }
    }

    private static void triggerNativeOnDpChanged(View appsView, Object deviceProfile) {
        try {
            Method onDpChangedMethod = findMethod(appsView.getClass(), "onDeviceProfileChanged", deviceProfile.getClass());
            if (onDpChangedMethod != null) {
                onDpChangedMethod.setAccessible(true);
                onDpChangedMethod.invoke(appsView, deviceProfile);
                Log.d(TAG, "Called native onDeviceProfileChanged success");
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to trigger native update", e);
        }
    }

    private static void manualForceUpdateList(Activity activity, View appsView, int cols) {
        try {
            updateAlphabeticalAppsList(appsView, cols);

            int listId = activity.getResources().getIdentifier("apps_list_view", "id", activity.getPackageName());
            View recyclerView = (listId != 0) ? appsView.findViewById(listId) : null;

            if (recyclerView != null) {
                // Set Span Count
                Method getLayoutManager = findMethod(recyclerView.getClass(), "getLayoutManager");
                Object layoutManager = (getLayoutManager != null) ? getLayoutManager.invoke(recyclerView) : null;
                
                if (layoutManager != null) {
                    Method setSpanCount = findMethod(layoutManager.getClass(), "setSpanCount", int.class);
                    if (setSpanCount != null) setSpanCount.invoke(layoutManager, cols);

                    Method getSpanSizeLookup = findMethod(layoutManager.getClass(), "getSpanSizeLookup");
                    Object spanSizeLookup = (getSpanSizeLookup != null) ? getSpanSizeLookup.invoke(layoutManager) : null;
                    
                    if (spanSizeLookup != null) {
                        Method invalidateCache = findMethod(spanSizeLookup.getClass(), "invalidateSpanIndexCache");
                        if (invalidateCache != null) invalidateCache.invoke(spanSizeLookup);
                    }
                }

                // Notify Adapter
                Method getAdapter = findMethod(recyclerView.getClass(), "getAdapter");
                Object adapter = (getAdapter != null) ? getAdapter.invoke(recyclerView) : null;
                if (adapter != null) {
                    Method notifyDataSetChanged = findMethod(adapter.getClass(), "notifyDataSetChanged");
                    if (notifyDataSetChanged != null) notifyDataSetChanged.invoke(adapter);
                }

                // Invalidate Decors
                Method invalidateDecors = findMethod(recyclerView.getClass(), "invalidateItemDecorations");
                if (invalidateDecors != null) invalidateDecors.invoke(recyclerView);
            }
        } catch (Exception e) {
            Log.e(TAG, "Manual list update failed", e);
        }
    }

    private static void updateAlphabeticalAppsList(View appsView, int cols) {
        try {
            Object alphaAppsList = getField(appsView, "mApps");
            if (alphaAppsList == null) return;

            setIntFieldSilently(alphaAppsList, "mNumAppsPerRow", cols);
            
            Method updateItemsMethod = findMethod(alphaAppsList.getClass(), "updateAdapterItems");
            if (updateItemsMethod != null) {
                updateItemsMethod.setAccessible(true);
                updateItemsMethod.invoke(alphaAppsList);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to update AlphabeticalAppsList", e);
        }
    }

    private static void fixSearchBar(View appsView) {
        try {
            View searchContainer = null;

            // 1. Try finding mSearchContainer via reflection (Cached access via getField)
            Object containerObj = getField(appsView, "mSearchContainer");
            if (containerObj instanceof View) {
                searchContainer = (View) containerObj;
            }

            // 2. Fallback to ID lookup
            if (searchContainer == null) {
                int searchId = appsView.getResources().getIdentifier("search_container_all_apps", "id", appsView.getContext().getPackageName());
                if (searchId != 0) {
                    searchContainer = appsView.findViewById(searchId);
                }
            }

            if (searchContainer != null) {
                searchContainer.setPadding(0, searchContainer.getPaddingTop(), 0, searchContainer.getPaddingBottom());

                ViewGroup.LayoutParams params = searchContainer.getLayoutParams();
                
                if (params.width != ViewGroup.LayoutParams.MATCH_PARENT) {
                    params.width = ViewGroup.LayoutParams.MATCH_PARENT;
                }
                
                if (params instanceof ViewGroup.MarginLayoutParams) {
                    ViewGroup.MarginLayoutParams marginParams = (ViewGroup.MarginLayoutParams) params;
                    marginParams.leftMargin = 0;
                    marginParams.rightMargin = 0;
                }
                
                searchContainer.setLayoutParams(params);
                searchContainer.requestLayout();
            }
        } catch (Exception ignored) {
            Log.w(TAG, "Failed to fix search bar layout: " + ignored);
        }
    }

    private static void recalculateCellWidth(Object dp, Object allAppsProfile, int cols) {
        try {
            int widthPx = getIntField(dp, "availableWidthPx");
            if (widthPx <= 0) widthPx = getIntField(dp, "widthPx");
            if (widthPx <= 0) return;

            int totalPadding = 0;
            Object paddingObj = getField(dp, "allAppsPadding");
            if (paddingObj instanceof Rect) {
                Rect r = (Rect) paddingObj;
                totalPadding = r.left + r.right;
            }

            int borderSpaceW = 0;
            Object borderSpaceObj = null;
            if (allAppsProfile != null) borderSpaceObj = getField(allAppsProfile, "borderSpacePx");
            if (borderSpaceObj == null) borderSpaceObj = getField(dp, "allAppsBorderSpacePx");

            if (borderSpaceObj != null) {
                Object value = getField(borderSpaceObj, "x");
                if (value instanceof Integer) borderSpaceW = (Integer) value;
                else if (value instanceof Float) borderSpaceW = ((Float) value).intValue();
            }

            int totalSpace = (cols > 1) ? (cols - 1) * borderSpaceW : 0;
            int availableForCells = widthPx - totalPadding - totalSpace;

            if (availableForCells > 0) {
                int newCellWidth = availableForCells / cols;
                setIntFieldSilently(dp, "allAppsCellWidthPx", newCellWidth);
                if (allAppsProfile != null) {
                    setIntFieldSilently(allAppsProfile, "cellWidthPx", newCellWidth);
                }
            }
        } catch (Exception ignored) {}
    }

    private static void applyHeightScale(Object obj, String fieldName, float scale) {
        try {
            int current = getIntField(obj, fieldName);
            if (current > 0) {
                setIntFieldSilently(obj, fieldName, Math.round(current * scale));
            }
        } catch (Exception ignored) {}
    }

    // --- Cached Reflection Utils ---

    private static boolean isEnabled(Context context, String key) {
        return Settings.Secure.getInt(context.getContentResolver(), key, 0) == 1;
    }

    private static int getInt(Context context, String key, int def) {
        return Settings.Secure.getInt(context.getContentResolver(), key, def);
    }

    private static void setIntFieldSilently(Object obj, String fieldName, int value) {
        if (obj == null) return;
        try {
            Field field = getFieldObject(obj.getClass(), fieldName);
            if (field != null) {
                field.setInt(obj, value);
            }
        } catch (Exception ignored) {}
    }

    private static void setFloatFieldSilently(Object obj, String fieldName, float value) {
        if (obj == null) return;
        try {
            Field field = getFieldObject(obj.getClass(), fieldName);
            if (field != null) {
                field.setFloat(obj, value);
            }
        } catch (Exception ignored) {}
    }

    private static int getIntField(Object obj, String fieldName) {
        if (obj == null) return 0;
        try {
            Field field = getFieldObject(obj.getClass(), fieldName);
            if (field != null) {
                return field.getInt(obj);
            }
        } catch (Exception ignored) {}
        return 0;
    }

    private static Object getField(Object obj, String fieldName) {
        if (obj == null) return null;
        try {
            Field field = getFieldObject(obj.getClass(), fieldName);
            if (field != null) {
                return field.get(obj);
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static Field getFieldObject(Class<?> clazz, String fieldName) {
        String key = clazz.getName() + "." + fieldName;
        synchronized (sFieldCache) {
            if (sFieldCache.containsKey(key)) {
                return sFieldCache.get(key);
            }
        }

        Class<?> current = clazz;
        while (current != null) {
            try {
                Field field = current.getDeclaredField(fieldName);
                field.setAccessible(true);
                synchronized (sFieldCache) {
                    sFieldCache.put(key, field);
                }
                return field;
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            }
        }
        
        synchronized (sFieldCache) {
            sFieldCache.put(key, null);
        }
        return null;
    }

    private static Method findMethod(Class<?> clazz, String methodName, Class<?>... parameterTypes) {
        StringBuilder keyBuilder = new StringBuilder(clazz.getName()).append(".").append(methodName);
        for (Class<?> p : parameterTypes) {
            keyBuilder.append("-").append(p.getName());
        }
        String key = keyBuilder.toString();

        synchronized (sMethodCache) {
            if (sMethodCache.containsKey(key)) {
                return sMethodCache.get(key);
            }
        }

        Class<?> current = clazz;
        while (current != null) {
            try {
                Method method = current.getDeclaredMethod(methodName, parameterTypes);
                method.setAccessible(true);
                synchronized (sMethodCache) {
                    sMethodCache.put(key, method);
                }
                return method;
            } catch (NoSuchMethodException e) {
                current = current.getSuperclass();
            }
        }
        
        synchronized (sMethodCache) {
            sMethodCache.put(key, null);
        }
        return null;
    }
}