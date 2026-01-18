package org.pixel.customparts.xposed

import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import android.util.Log
import org.pixel.customparts.xposed.hooks.DT2SHook
import org.pixel.customparts.xposed.hooks.DT2WHook
import org.pixel.customparts.xposed.hooks.ClearAllHook
import org.pixel.customparts.xposed.hooks.SearchWidgetHook
import org.pixel.customparts.xposed.hooks.EdgeEffectHook
import org.pixel.customparts.xposed.hooks.DisableGoogleFeedHook
import org.pixel.customparts.xposed.hooks.GridSizeHook
import de.robv.android.xposed.XposedBridge

class XposedInit : IXposedHookLoadPackage {

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {

        if (lpparam.packageName != null) {
            EdgeEffectHook.init(lpparam)
        }

        if (lpparam.packageName == "com.android.systemui") {
            DT2WHook.init(lpparam)
        }

        if (lpparam.packageName == "com.google.android.apps.nexuslauncher") {
            DT2SHook.init(lpparam)
            ClearAllHook.init(lpparam)
            SearchWidgetHook.init(lpparam)
            GridSizeHook.init(lpparam)
            DisableGoogleFeedHook.init(lpparam)
        }

        if (lpparam.packageName == "org.pixel.customparts.xposed") { 
            try {
                val targetClass = "org.pixel.customparts.ui.ModuleStatus"
                
                XposedHelpers.findAndHookMethod(
                    targetClass,
                    lpparam.classLoader,
                    "isModuleActive",
                    object : XC_MethodReplacement() {
                        override fun replaceHookedMethod(param: MethodHookParam): Any {
                            return true
                        }
                    }
                )
            } catch (e: Throwable) {
                XposedBridge.log("PixelParts: Failed to hook ModuleStatus self-check: $e")
            }
        }
    }
}