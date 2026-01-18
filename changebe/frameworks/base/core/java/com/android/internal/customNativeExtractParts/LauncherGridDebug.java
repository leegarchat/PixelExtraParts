package com.android.internal.customNativeExtractParts;

import android.app.Activity;
import android.util.Log;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class LauncherGridDebug {

    private static final String TAG = "GridDebug";

    public static void dump(Activity activity) {
        new Thread(() -> {
            try {
                Log.e(TAG, "================ START GRID DEBUG ================");
                
                Object deviceProfile = getField(activity, "mDeviceProfile");
                if (deviceProfile == null) {
                    Log.e(TAG, "DeviceProfile NOT FOUND in Activity!");
                } else {
                    Log.i(TAG, ">> DeviceProfile found: " + deviceProfile.getClass().getName());
                    inspectObject(deviceProfile, "DeviceProfile");
                    
                    Object inv = getField(deviceProfile, "inv");
                    if (inv != null) {
                        Log.i(TAG, ">> InvariantDeviceProfile found: " + inv.getClass().getName());
                        inspectObject(inv, "InvariantDeviceProfile");
                    } else {
                        Log.e(TAG, "InvariantDeviceProfile (inv) NOT FOUND in DeviceProfile");
                    }

                    Object allAppsProfile = getField(deviceProfile, "mAllAppsProfile");
                    if (allAppsProfile != null) {
                        Log.i(TAG, ">> AllAppsProfile found: " + allAppsProfile.getClass().getName());
                        inspectObject(allAppsProfile, "AllAppsProfile");
                    } else {
                        Log.w(TAG, "mAllAppsProfile NOT FOUND (может называться иначе или отсутствовать)");
                        scanForProfileFields(deviceProfile);
                    }
                }
                
                Log.e(TAG, "================ END GRID DEBUG ================");
            } catch (Exception e) {
                Log.e(TAG, "Critical error during debug", e);
            }
        }).start();
    }

    private static void inspectObject(Object obj, String title) {
        if (obj == null) return;
        Class<?> clazz = obj.getClass();
        
        List<String> foundFields = new ArrayList<>();

        while (clazz != null) {
            for (Field field : clazz.getDeclaredFields()) {
                field.setAccessible(true);
                String name = field.getName();
                String lowerName = name.toLowerCase();
                if (lowerName.contains("col") || 
                    lowerName.contains("row") || 
                    lowerName.contains("height") || 
                    lowerName.contains("size") || 
                    lowerName.contains("pad") ||
                    lowerName.contains("apps")) {
                    
                    try {
                        Object val = field.get(obj);
                        foundFields.add(String.format("%s (%s) = %s", name, field.getType().getSimpleName(), val));
                    } catch (Exception e) {
                        foundFields.add(name + " = [ERROR]");
                    }
                }
            }
            clazz = clazz.getSuperclass();
        }
        
        Collections.sort(foundFields);
        
        Log.d(TAG, "--- " + title + " FIELDS ---");
        for (String line : foundFields) {
            Log.d(TAG, line);
        }
    }

    private static void scanForProfileFields(Object deviceProfile) {
        Log.d(TAG, "--- Scanning DeviceProfile for other Profile objects ---");
        for (Field f : deviceProfile.getClass().getDeclaredFields()) {
            f.setAccessible(true);
            if (f.getType().getName().contains("Profile")) {
                try {
                    Log.d(TAG, "Potential Profile Field: " + f.getName() + " type: " + f.getType().getName());
                } catch (Exception e) {}
            }
        }
    }

    private static Object getField(Object obj, String fieldName) {
        if (obj == null) return null;
        Class<?> clazz = obj.getClass();
        while (clazz != null) {
            try {
                Field field = clazz.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(obj);
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }
}