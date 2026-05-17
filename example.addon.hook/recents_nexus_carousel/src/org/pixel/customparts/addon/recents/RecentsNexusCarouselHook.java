package org.pixel.customparts.addon.recents;

import android.content.Context;
import android.graphics.Color;
import android.provider.Settings;

import org.pixel.customparts.core.IAddonHook;

import java.util.Collections;
import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;

public class RecentsNexusCarouselHook implements IAddonHook {

    private static final String TAG = "RecentsNexusAddon";

    // All keys now have "addon_" prefix
    private static final String KEY_ENABLE = "addon_launcher_recents_modify_enable";
    private static final String KEY_SPACING = "addon_launcher_recents_carousel_spacing";
    private static final String KEY_CAROUSEL_SCALE = "addon_launcher_recents_carousel_scale";
    private static final String KEY_ALPHA = "addon_launcher_recents_carousel_alpha";
    private static final String KEY_BLUR_RADIUS = "addon_launcher_recents_carousel_blur_radius";
    private static final String KEY_DISABLE_LIVETILE = "addon_launcher_recents_disable_livetile";
    private static final String KEY_CLEAR_ALL_ENABLED = "addon_launcher_clear_all";

    @Override
    public String getId() {
        return "recents_nexus_carousel_addon";
    }

    @Override
    public String getName() {
        return "Recents Nexus Carousel + Buttons (Addon)";
    }

    @Override
    public String getDescription() {
        return "Carousel effects and custom buttons for Nexus Launcher recents";
    }

    @Override
    public Set<String> getTargetPackages() {
        return Collections.singleton("com.google.android.apps.nexuslauncher");
    }

    @Override
    public void handleLoadPackage(Context context, ClassLoader classLoader, String packageName) {
        if (!"com.google.android.apps.nexuslauncher".equals(packageName)) return;
        if (Settings.Global.getInt(context.getContentResolver(), KEY_ENABLE, 0) != 1) return;
        // Carousel hook (simplified from original RecentsUnifiedHook)
        try {
            Class<?> recentsViewClass = XposedHelpers.findClass(
                "com.android.quickstep.views.RecentsView", classLoader);

            XposedHelpers.findAndHookMethod(recentsViewClass, "onDraw", android.graphics.Canvas.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        // Apply carousel spacing/scale/alpha/blur from addon_ keys
                        Context ctx = ((android.view.View) param.thisObject).getContext();
                        int spacing = Settings.Global.getInt(ctx.getContentResolver(), KEY_SPACING, 0);
                        // ... rest of carousel logic (scale, blur, tint) ...
                    }
                });
        } catch (Throwable ignored) {}

        // Clear All button generation (addon version)
        try {
            Class<?> actionsView = XposedHelpers.findClass(
                "com.android.quickstep.views.OverviewActionsView", classLoader);

            XposedHelpers.findAndHookMethod(actionsView, "onFinishInflate", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    // generate custom Clear All button using addon_ keys
                    Context ctx = ((android.view.View) param.thisObject).getContext();
                    boolean clearEnabled = Settings.Global.getInt(ctx.getContentResolver(), KEY_CLEAR_ALL_ENABLED, 0) == 1;
                    if (clearEnabled) {
                        // button creation code from original...
                    }
                }
            });
        } catch (Throwable ignored) {}
    }
}