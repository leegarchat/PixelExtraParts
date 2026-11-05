package org.pixel.customparts.manager.lsplant;

import android.content.Context;
import org.pixel.customparts.core.BaseHook;
import org.pixel.customparts.core.IHookEnvironment;
import org.pixel.customparts.hooks.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class HookEntry {
    private static final String TAG = "HookEntry";

    private static final Set<String> initializedPackages = new HashSet<>();
    private static final IHookEnvironment environment = new LsplantEnvironment();

    public static void init(Context context, ClassLoader classLoader, String packageName) {
        if (initializedPackages.contains(packageName)) {
            return;
        }
        initializedPackages.add(packageName);

        boolean hasAddons = false;

        try {
            hasAddons = AddonLoader.hasAddonsForPackage(context, packageName);
        } catch (Throwable t) {
            environment.logError(TAG, "Failed to check addons for " + packageName, t);
        }

        // Built-in global hooks first, then addon hooks (if any).
        initGlobalHooks(context, classLoader);

        if (hasAddons) {
            try {
                AddonLoader.loadAndRunAddons(context, classLoader, packageName);
            } catch (Throwable t) {
                environment.logError(TAG, "Addon loading failed for " + packageName, t);
            }
        }
    }

    private static void initGlobalHooks(Context context, ClassLoader classLoader) {
        List<BaseHook> hooks = new ArrayList<>();
        
        EdgeEffectHookWrapper edgeHook = new EdgeEffectHookWrapper();
        // Настройка полей напрямую, так как apply в Java нет
        edgeHook.setKeySuffix("_lsplant"); 
        edgeHook.setUseGlobalSettings(true);
        hooks.add(edgeHook);

        hooks.add(new MagnifierHook());
        hooks.add(new ActivityTransitionHook());
        hooks.add(new PredictiveBackDisableHook());

        applyHooks(hooks, context, classLoader, "global");
    }

    private static void applyHooks(List<BaseHook> hooks, Context context, ClassLoader classLoader, String group) {
        // Сортировка по приоритету (по убыванию)
        Collections.sort(hooks, new Comparator<BaseHook>() {
            @Override
            public int compare(BaseHook o1, BaseHook o2) {
                // Descending order: 
                return Integer.compare(o2.getPriority(), o1.getPriority());
            }
        });

        int successCount = 0;

        for (BaseHook hook : hooks) {
            try {
                hook.setup(environment);
                // Проверяем включен ли хук (тумблер проверяется внутри)
                if (hook.isEnabled(context)) {
                    hook.init(classLoader);
                    successCount++;
                    environment.log(TAG, "Hook " + hook.getHookId() + " applied (" + group + ")");
                }
            } catch (Throwable t) {
                environment.logError(TAG, "Failed to init " + hook.getHookId() + " (" + group + ")", t);
            }
        }

        if (successCount > 0) {
            environment.log(TAG, "Init complete for " + group + ": " + successCount + " hooks active");
        }
    }
}