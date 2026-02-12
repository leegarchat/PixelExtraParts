package org.pixel.customparts.core

import android.app.Activity
import android.content.Context





abstract class BaseHook {

    



    lateinit var env: IHookEnvironment
    
    


    protected var hostClassLoader: ClassLoader? = null

    


    abstract val hookId: String

    


    open val priority: Int = 0

    



    fun setup(environment: IHookEnvironment) {
        this.env = environment
    }

    




    fun init(classLoader: ClassLoader) {
        this.hostClassLoader = classLoader
        
        try {
            onInit(classLoader)
        } catch (t: Throwable) {
            logError("Error during initialization", t)
        }
    }

    


    protected open fun onInit(classLoader: ClassLoader) {}

    




    open fun isEnabled(context: Context?): Boolean = true

    

    open fun onActivityCreated(activity: Activity) {}
    open fun onActivityResumed(activity: Activity) {}
    open fun onActivityPaused(activity: Activity) {}
    open fun onActivityDestroyed(activity: Activity) {}

    

    protected fun log(message: String) {
        if (::env.isInitialized) {
            env.log(hookId, message)
        }
    }

    protected fun logError(message: String, t: Throwable? = null) {
        if (::env.isInitialized) {
            env.logError(hookId, message, t)
        }
    }

    protected fun isSettingEnabled(context: Context?, key: String, default: Boolean = false): Boolean {
        return env.isEnabled(context, key, default)
    }

    protected fun getIntSetting(context: Context?, key: String, default: Int): Int {
        return env.getInt(context, key, default)
    }

    protected fun getFloatSetting(context: Context?, key: String, default: Float): Float {
        return env.getFloat(context, key, default)
    }
}