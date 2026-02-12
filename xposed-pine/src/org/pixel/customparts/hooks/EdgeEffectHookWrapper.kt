package org.pixel.customparts.hooks

import android.content.Context
import org.pixel.customparts.core.BaseHook












class EdgeEffectHookWrapper : BaseHook() {
    override val hookId = "EdgeEffectHook"
    override val priority = 10  

    




    var keySuffix: String = "_xposed"

    



    var useGlobalSettings: Boolean = true

    companion object {
        private const val KEY_ENABLED = "overscroll_enabled"
    }

    override fun isEnabled(context: Context?): Boolean {
        if (context == null) return true  
        return isSettingEnabled(context, KEY_ENABLED, true)
    }

    override fun onInit(classLoader: ClassLoader) {
        try {
            
            EdgeEffectHook.configure(useGlobalSettings, keySuffix)
            EdgeEffectHook.initWithClassLoader(classLoader)
            log("EdgeEffect hook initialized (suffix=$keySuffix, global=$useGlobalSettings)")
        } catch (e: Throwable) {
            logError("Failed to initialize EdgeEffect hook", e)
        }
    }
}
