package org.pixel.customparts.hooks.recents

import android.animation.ValueAnimator
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import org.pixel.customparts.core.BaseHook
import android.view.ViewTreeObserver

class RecentsLifecycleHook : BaseHook() {
    override val hookId = "RecentsLifecycleHook"
    override val priority = 60

    private val ENTER_ANIMATION_DURATION = 250L
    private val KEY_MODIFY_ENABLE = "launcher_recents_modify_enable"

    override fun isEnabled(context: android.content.Context?): Boolean {
        return isSettingEnabled(context, KEY_MODIFY_ENABLE)
    }

    override fun onInit(classLoader: ClassLoader) {
        try {
            val recentsViewClass = XposedHelpers.findClass(RecentsState.CLASS_RECENTS_VIEW, classLoader)

            
            
            hookTaskViewTranslation(classLoader)

            
            hookPreDrawCleanup(recentsViewClass)

            XposedHelpers.findAndHookMethod(recentsViewClass, "setVisibility", Int::class.java, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val visibility = param.args[0] as Int
                    if (visibility != View.VISIBLE) {
                        resetState()
                    }
                }
            })

            XposedHelpers.findAndHookMethod(recentsViewClass, "onDetachedFromWindow", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    resetState()
                }
            })

            XposedBridge.hookAllMethods(recentsViewClass, "onGestureAnimationStart", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    RecentsState.isGestureInProgress = true
                    val view = param.thisObject as? ViewGroup ?: return
                    if (!isSettingEnabled(view.context, KEY_MODIFY_ENABLE)) return
                    startEntryAnimation(view)
                }
            })

            XposedBridge.hookAllMethods(recentsViewClass, "onGestureAnimationEnd", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val view = param.thisObject as? ViewGroup ?: return
                    try {
                        val endTarget = XposedHelpers.getObjectField(view, "mCurrentGestureEndTarget")
                        view.setTag(RecentsState.TAG_PENDING_END_TARGET, endTarget?.toString() ?: "")
                    } catch (_: Throwable) {
                        view.setTag(RecentsState.TAG_PENDING_END_TARGET, "")
                    }
                }

                override fun afterHookedMethod(param: MethodHookParam) {
                    val view = param.thisObject as? ViewGroup ?: return
                    val targetName = (view.getTag(RecentsState.TAG_PENDING_END_TARGET) as? String) ?: ""
                    view.setTag(RecentsState.TAG_PENDING_END_TARGET, null)
                    RecentsState.isGestureInProgress = false
                    if (!isSettingEnabled(view.context, KEY_MODIFY_ENABLE)) return

                    if (targetName.contains("RECENTS")) {
                        
                        RecentsState.isInRecentsMode = true
                        RecentsState.enteringRecentsUntil = 0L 
                    } else {
                        
                    }
                }
            })
            try {
                XposedHelpers.findAndHookMethod(recentsViewClass, "setEnableDrawingLiveTile",
                    Boolean::class.java, object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            val enable = param.args[0] as Boolean
                            val view = param.thisObject as? ViewGroup ?: return
                            if (!isSettingEnabled(view.context, KEY_MODIFY_ENABLE)) return
                            if (enable && !RecentsState.isInRecentsMode && view.visibility == View.VISIBLE) {
                                startEntryAnimation(view)
                            }
                        }
                    })
            } catch (_: Throwable) {}
            XposedHelpers.findAndHookMethod(recentsViewClass, "onLayout",
                Boolean::class.java, Int::class.java, Int::class.java, Int::class.java, Int::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val view = param.thisObject as? ViewGroup ?: return
                        if (view.visibility != View.VISIBLE) return
                        if (view.childCount == 0) return
                        if (RecentsState.isGestureInProgress) return
                        if (!isSettingEnabled(view.context, KEY_MODIFY_ENABLE)) return

                        
                        if (!RecentsState.isInRecentsMode && RecentsState.carouselIntensity < 0.5f) {
                            startEntryAnimation(view)
                        }
                    }
                })
            val launchMethods = arrayOf("launchTask", "startHome", "confirmTask")
            for (methodName in launchMethods) {
                try {
                    XposedBridge.hookAllMethods(recentsViewClass, methodName, object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            RecentsState.isAnimatingExit = true
                            RecentsState.isInRecentsMode = false
                        }
                    })
                } catch (_: Throwable) {}
            }
            XposedBridge.hookAllMethods(recentsViewClass, "onTaskLaunchAnimationEnd", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    resetState()
                }
            })

        } catch (e: Throwable) {
            logError("RecentsLifecycle hook failed", e)
        }
    }

    





    private fun hookTaskViewTranslation(classLoader: ClassLoader) {
        try {
            val taskViewClass = XposedHelpers.findClass(RecentsState.CLASS_TASK_VIEW, classLoader)
            XposedBridge.hookAllMethods(View::class.java, "setTranslationX", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    
                    if (RecentsState.applyingCarousel.get() == true) return
                    val view = param.thisObject as? View ?: return
                    if (!taskViewClass.isInstance(view)) return
                    
                    view.setTag(RecentsState.TAG_SYS_TRANS_X, param.args[0] as Float)
                }
            })
        } catch (e: Throwable) {
            logError("Failed to hook TaskView.setTranslationX", e)
        }
    }

    




    private fun hookPreDrawCleanup(recentsViewClass: Class<*>) {
        XposedHelpers.findAndHookMethod(recentsViewClass, "onAttachedToWindow", object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val view = param.thisObject as? ViewGroup ?: return
                val installed = view.getTag(RecentsState.TAG_CAROUSEL_PREDRAW_INSTALLED) as? Boolean ?: false
                if (installed) return

                val listener = ViewTreeObserver.OnPreDrawListener {
                    if (!isSettingEnabled(view.context, KEY_MODIFY_ENABLE)) {
                        resetEffectsOnChildren(view)
                        return@OnPreDrawListener true
                    }
                    if (!RecentsState.isInRecentsMode && !RecentsState.isAnimatingExit
                        && !RecentsState.isGestureInProgress) {
                        resetEffectsOnChildren(view)
                    }
                    true
                }
                view.viewTreeObserver.addOnPreDrawListener(listener)
                view.setTag(RecentsState.TAG_CAROUSEL_PREDRAW_INSTALLED, true)
                view.setTag(RecentsState.TAG_CAROUSEL_PREDRAW_LISTENER, listener)

                view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                    override fun onViewAttachedToWindow(v: View) {}
                    override fun onViewDetachedFromWindow(v: View) {
                        val l = v.getTag(RecentsState.TAG_CAROUSEL_PREDRAW_LISTENER) as? ViewTreeObserver.OnPreDrawListener
                        if (l != null) v.viewTreeObserver.removeOnPreDrawListener(l)
                        v.setTag(RecentsState.TAG_CAROUSEL_PREDRAW_INSTALLED, false)
                        v.setTag(RecentsState.TAG_CAROUSEL_PREDRAW_LISTENER, null)
                    }
                })
            }
        })
    }

    



    private fun resetEffectsOnChildren(recentsView: ViewGroup) {
        val childCount = recentsView.childCount
        if (childCount == 0) return

        RecentsState.applyingCarousel.set(true)
        try {
            for (i in 0 until childCount) {
                val child = recentsView.getChildAt(i) ?: continue

                
                val hadTrans = child.getTag(RecentsState.TAG_OFFSET_TRANS) != null
                if (hadTrans) {
                    val sysTrans = (child.getTag(RecentsState.TAG_SYS_TRANS_X) as? Float) ?: 0f
                    child.translationX = sysTrans
                    child.setTag(RecentsState.TAG_OFFSET_TRANS, null)
                }

                
                if (child.scaleX != 1.0f || child.scaleY != 1.0f) {
                    child.scaleX = 1.0f
                    child.scaleY = 1.0f
                }

                
                child.setTag(RecentsState.TAG_OFFSET_ALPHA, null)
                val baseAlpha = child.getTag(RecentsState.TAG_SYS_STABLE_ALPHA) as? Float
                if (baseAlpha != null) {
                    try {
                        XposedHelpers.callMethod(child, "setStableAlpha", baseAlpha)
                    } catch (_: Throwable) {}
                }

                
                child.setTag(RecentsState.TAG_OFFSET_SCALE, null)
                val baseScale = child.getTag(RecentsState.TAG_SYS_NON_GRID_SCALE) as? Float
                if (baseScale != null) {
                    try {
                        XposedHelpers.callMethod(child, "setNonGridScale", baseScale)
                    } catch (_: Throwable) {}
                }

                
                if (android.os.Build.VERSION.SDK_INT >= 31) {
                    child.setTag(RecentsState.TAG_BLUR_EFFECT, null)
                    child.setTag(RecentsState.TAG_TINT_EFFECT, null)
                    try {
                        XposedHelpers.callMethod(child, "setRenderEffect", null as Any?)
                    } catch (_: Throwable) {}
                    
                    if (child is ViewGroup) {
                        resetThumbnailEffects(child, 0)
                    }
                }

                if (child is ViewGroup) {
                    resetIconOffsets(child)
                }
            }
        } finally {
            RecentsState.applyingCarousel.set(false)
        }
    }

    private fun resetIconOffsets(taskView: ViewGroup) {
        val icons = mutableListOf<View>()
        scanForIcons(taskView, icons, 0)
        for (icon in icons) {
            icon.translationX = 0f
            icon.translationY = 0f
        }

        val origDelegate = taskView.getTag(RecentsState.TAG_ICON_ORIG_DELEGATE) as? android.view.TouchDelegate
        if (origDelegate != null) {
            taskView.touchDelegate = origDelegate
            taskView.setTag(RecentsState.TAG_ICON_ORIG_DELEGATE, null)
        }
    }

    private fun scanForIcons(root: ViewGroup, out: MutableList<View>, depth: Int) {
        if (depth > 6) return
        for (i in 0 until root.childCount) {
            val v = root.getChildAt(i) ?: continue
            val name = v.javaClass.simpleName
            val fullName = v.javaClass.name

            var isMatch = false
            if (name.contains("Icon") || name.contains("Chip") ||
                fullName.contains("IconView") || fullName.contains("iconView") ||
                fullName.contains("AppChip")) {
                out.add(v)
                isMatch = true
            }

            if (!isMatch && v is ViewGroup) {
                scanForIcons(v, out, depth + 1)
            }
        }
    }

    private fun resetThumbnailEffects(root: ViewGroup, depth: Int) {
        if (depth > 3) return
        for (i in 0 until root.childCount) {
            val v = root.getChildAt(i) ?: continue
            val name = v.javaClass.simpleName
            if (name.contains("ThumbnailView") || name.contains("Snapshot")) {
                try { XposedHelpers.callMethod(v, "setRenderEffect", null as Any?) } catch (_: Throwable) {}
            }
            if (v is ViewGroup) resetThumbnailEffects(v, depth + 1)
        }
    }

    private fun startEntryAnimation(view: View) {
        if (RecentsState.isInRecentsMode && RecentsState.carouselAnimator?.isRunning == true) return
        if (!isSettingEnabled(view.context, KEY_MODIFY_ENABLE)) return
        RecentsState.isInRecentsMode = true
        RecentsState.enteringRecentsUntil = SystemClock.uptimeMillis() + ENTER_ANIMATION_DURATION
        RecentsState.carouselAnimator?.cancel()
        val anim = ValueAnimator.ofFloat(0f, 1f)
        anim.duration = ENTER_ANIMATION_DURATION
        anim.interpolator = DecelerateInterpolator()
        anim.addUpdateListener { 
            RecentsState.carouselIntensity = it.animatedValue as Float
            view.invalidate()
        }
        anim.start()
        RecentsState.carouselAnimator = anim
    }

    private fun resetState() {
        RecentsState.carouselAnimator?.cancel()
        RecentsState.carouselAnimator = null
        RecentsState.carouselIntensity = 0f
        RecentsState.isInRecentsMode = false
        RecentsState.isGestureInProgress = false
        RecentsState.enteringRecentsUntil = 0L
        RecentsState.isAnimatingExit = false
    }
}