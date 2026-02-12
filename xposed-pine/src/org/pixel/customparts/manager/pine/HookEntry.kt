package org.pixel.customparts.manager.pine

import android.content.Context
import android.util.Log
import org.pixel.customparts.core.BaseHook
import org.pixel.customparts.core.IHookEnvironment
import org.pixel.customparts.hooks.*
import org.pixel.customparts.hooks.recents.*
import org.pixel.customparts.hooks.systemui.DozeTapDozeHook
import org.pixel.customparts.hooks.systemui.DozeTapShadeHook

object HookEntry {
    private const val TAG = "HookEntry"
    
    
    private val LAUNCHER_PACKAGES = setOf(
        "com.google.android.apps.nexuslauncher",
        "com.google.android.apps.pixel.launcher",
        "com.android.launcher3",
    )

    private const val PACKAGE_SYSTEMUI = "com.android.systemui"

    private val initializedPackages = mutableSetOf<String>()

    private val environment: IHookEnvironment = PineEnvironment()

    fun init(context: Context, classLoader: ClassLoader, packageName: String) {
        if (!initializedPackages.add(packageName)) return

        
        initGlobalHooks(context, classLoader)

        
        if (LAUNCHER_PACKAGES.contains(packageName)) {
            environment.log(TAG, "MATCHED LAUNCHER PACKAGE: $packageName")
            initLauncherHooks(context, classLoader)
        }

        
        if (packageName == PACKAGE_SYSTEMUI) {
            environment.log(TAG, "MATCHED SYSTEMUI PACKAGE")
            initSystemUIHooks(context, classLoader)
        }
    }

    


    private fun initGlobalHooks(context: Context, classLoader: ClassLoader) {
        val hooks = listOf<BaseHook>(
            EdgeEffectHookWrapper().apply {
                keySuffix = "_pine"
                useGlobalSettings = true
            }
        )
        applyHooks(hooks, context, classLoader, "global")
    }

    


    private fun initLauncherHooks(context: Context, classLoader: ClassLoader) {
        val hooks = listOf<BaseHook>(
            DT2SHook(),
            DisableGoogleFeedHook(),
            ClearAllHook(),
            TopWidgetHook(),
            GridSizeHomePageHook(),
            GridSizeAppMenuHook(),
            DockScaleHook(),
            DockSearchWidgetHook(),
            HomepageSizeHook(),
            DotsHomepageHook(),
            RecentsLifecycleHook(),
            RecentsCommonScaleHook(),
            RecentsEdgeCardsScaleHook(),
            RecentsEdgeCardsAlphaHook(),
            RecentsEdgeCardsBlurHook(),
            RecentsEdgeCardsTintHook(),
            RecentsEdgeCardsSpacingHook(),
            RecentsTextIconOffsetHook(),
            RecentsDisableLiveTileHook()
        )
        applyHooks(hooks, context, classLoader, "launcher")
    }

    


    private fun initSystemUIHooks(context: Context, classLoader: ClassLoader) {
        val hooks = listOf<BaseHook>(
            DozeTapDozeHook(),
            DozeTapShadeHook()
        )
        applyHooks(hooks, context, classLoader, "systemui")
    }

    private fun applyHooks(hooks: List<BaseHook>, context: Context, classLoader: ClassLoader, group: String) {
        val sortedHooks = hooks.sortedByDescending { it.priority }
        var successCount = 0

        for (hook in sortedHooks) {
            try {
                hook.setup(environment)
                if (hook.isEnabled(context)) {
                    hook.init(classLoader)
                    successCount++
                    environment.log(TAG, "Hook ${hook.hookId} applied ($group)")
                }
            } catch (t: Throwable) {
                environment.logError(TAG, "Failed to init ${hook.hookId} ($group)", t)
            }
        }

        if (successCount > 0) {
            environment.log(TAG, "Init complete for $group: $successCount hooks active")
        }
    }
}