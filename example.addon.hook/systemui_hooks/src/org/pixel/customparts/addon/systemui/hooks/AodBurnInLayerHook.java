package org.pixel.customparts.addon.systemui.hooks;

import java.lang.reflect.Field;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * AOD burn-in hook (HOOK replacement for the SystemUI source patch).
 *
 * Before AodBurnInLayer.setRotation() runs, clears the internal
 * ConstraintLayout Layer.mViews cache so updatePostLayout() recomputes
 * referenced views — fixes AOD flicker on rotate/unfold transitions.
 */
public class AodBurnInLayerHook extends BaseSystemUIHook {
    @Override
    public String getHookId() {
        return "AodBurnInLayerHook";
    }

    @Override
    public int getPriority() {
        return 50;
    }

    @Override
    protected void onInit(ClassLoader classLoader) {
        try {
            Class<?> layerClass = XposedHelpers.findClass(
                    "com.android.systemui.keyguard.ui.view.layout.sections.AodBurnInLayer",
                    classLoader);
            XposedBridge.hookAllMethods(layerClass, "setRotation", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    invalidateViewsCache(param.thisObject);
                }
            });
            log("AodBurnInLayer.setRotation hooked");
        } catch (Throwable t) {
            logError("Unable to hook AodBurnInLayer", t);
        }
    }

    private void invalidateViewsCache(Object layer) {
        if (layer == null) {
            return;
        }
        try {
            Class<?> layerClass = layer.getClass();
            while (layerClass != null && layerClass != Object.class) {
                if ("androidx.constraintlayout.helper.widget.Layer"
                        .equals(layerClass.getName())) {
                    break;
                }
                layerClass = layerClass.getSuperclass();
            }
            if (layerClass == null) {
                return;
            }
            Field field = layerClass.getDeclaredField("mViews");
            field.setAccessible(true);
            field.set(layer, null);
        } catch (Throwable ignored) {
        }
    }
}
