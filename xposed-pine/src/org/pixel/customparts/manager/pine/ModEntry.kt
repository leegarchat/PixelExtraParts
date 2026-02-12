package org.pixel.customparts.pineinject

import android.app.ActivityThread
import android.util.Log
import org.pixel.customparts.manager.pine.HookEntry




object ModEntry {
    private const val TAG = "PineInject"

    @JvmStatic
    fun init() {
        
        
        try {
            try {
                System.load("/system/lib64/libpine.so")
            } catch (_: Throwable) {
                System.load("/system/lib/libpine.so")
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to load libpine.so", t)
            return
        }

        
        val app = ActivityThread.currentApplication()
        if (app == null) {
            
            
            return
        }

        val classLoader = app.classLoader
        val packageName = app.packageName

        
        try {
            HookEntry.init(app, classLoader, packageName)
        } catch (t: Throwable) {
            Log.e(TAG, "HookEntry.init failed", t)
        }
    }
}