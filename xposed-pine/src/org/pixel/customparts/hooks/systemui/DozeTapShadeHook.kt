package org.pixel.customparts.hooks.systemui

import android.content.Context
import android.view.MotionEvent
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import org.pixel.customparts.core.BaseHook

class DozeTapShadeHook : BaseHook() {
    override val hookId = "DozeTapShadeHook"
    override val priority = 70

    private var loggedHook = false
    private var tapLogCount = 0

    override fun isEnabled(context: Context?): Boolean = true

    override fun onInit(classLoader: ClassLoader) {
        hookPulsingGestureListener(classLoader)
    }

    private fun hookPulsingGestureListener(classLoader: ClassLoader) {
        try {
            val listenerClass = XposedHelpers.findClass(
                "com.android.systemui.shade.PulsingGestureListener",
                classLoader
            )
            XposedBridge.hookAllMethods(listenerClass, "onSingleTapUp", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    try {
                        if (!loggedHook) {
                            loggedHook = true
                            log("DozeTapShadeHook: onSingleTapUp hooked (all overloads)")
                        }
                        if (tapLogCount < 10) {
                            tapLogCount += 1
                            log("DozeTapShadeHook: onSingleTapUp invoked (#$tapLogCount)")
                        }
                        val controller = XposedHelpers.getObjectField(param.thisObject, "statusBarStateController")
                        val isDozing = XposedHelpers.callMethod(controller, "isDozing") as? Boolean ?: return
                        if (tapLogCount <= 10) {
                            log("DozeTapShadeHook: isDozing=$isDozing")
                        }
                        if (!isDozing) {
                            if (tapLogCount <= 10) {
                                log("DozeTapShadeHook: isDozing=false, skipping")
                            }
                            return
                        }

                        val context = resolveAppContext(param.thisObject.javaClass.classLoader)
                        if (context == null) {
                            if (tapLogCount <= 10) {
                                log("DozeTapShadeHook: app context not available")
                            }
                            return
                        }

                        val enabled = isSettingEnabled(context, DozeTapKeys.KEY_HOOK)
                        if (tapLogCount <= 10) {
                            log("DozeTapShadeHook: enabled=$enabled")
                        }
                        if (!enabled) {
                            if (tapLogCount <= 10) {
                                log("DozeTapShadeHook: setting disabled, skipping")
                            }
                            return
                        }

                        val timeout = getIntSetting(context, DozeTapKeys.KEY_TIMEOUT, DozeTapKeys.DEFAULT_TIMEOUT)
                        val consumed = when {
                            param.args.size == 1 && param.args[0] is MotionEvent -> {
                                val event = param.args[0] as MotionEvent
                                DozeTapManager.processTap(
                                    context,
                                    event.x,
                                    event.y,
                                    true,
                                    timeout,
                                    null
                                )
                            }
                            param.args.size >= 2 && param.args[0] is Float && param.args[1] is Float -> {
                                DozeTapManager.processTap(
                                    context,
                                    param.args[0] as Float,
                                    param.args[1] as Float,
                                    true,
                                    timeout,
                                    null
                                )
                            }
                            else -> {
                                if (tapLogCount <= 10) {
                                    log("DozeTapShadeHook: unsupported args size=${param.args.size}")
                                }
                                return
                            }
                        }

                        if (tapLogCount <= 10) {
                            log("DozeTapShadeHook: consumed=$consumed")
                        }
                        if (consumed) {
                            log("DozeTapShadeHook: Pulsing tap consumed")
                            param.result = true
                        }
                    } catch (t: Throwable) {
                        log("DozeTapShadeHook: Error in PulsingGestureListener: ${t.message}")
                    }
                }
            })

            log("DozeTapShadeHook: Hook applied successfully")
        } catch (e: Throwable) {
            log("DozeTapShadeHook: Failed to apply hook: ${e.message}")
        }
    }

    private fun resolveAppContext(classLoader: ClassLoader?): Context? {
        return try {
            val activityThreadClass = XposedHelpers.findClass("android.app.ActivityThread", classLoader)
            XposedHelpers.callStaticMethod(activityThreadClass, "currentApplication") as? Context
        } catch (_: Throwable) {
            null
        }
    }
}
