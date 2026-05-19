package org.pixel.customparts.addon.systemui;

import android.content.Context;

import org.pixel.customparts.core.IAddonHook;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class SystemUIHooksEntry implements IAddonHook {
    private static final String ADDON_ID = "systemui_hooks";
    private static final Set<String> TARGETS;

    static {
        Set<String> targets = new HashSet<>();
        targets.add(SystemUIHookRegistry.PACKAGE_SYSTEMUI);
        TARGETS = Collections.unmodifiableSet(targets);
    }

    @Override
    public String getId() {
        return ADDON_ID;
    }

    @Override
    public String getName() {
        return "SystemUI Hooks";
    }

    @Override
    public String getAuthor() {
        return "PixelExtraParts";
    }

    @Override
    public String getDescription() {
        return "SystemUI Pine hooks and settings.";
    }

    @Override
    public String getVersion() {
        return "1.0";
    }

    @Override
    public Set<String> getTargetPackages() {
        return TARGETS;
    }

    @Override
    public int getPriority() {
        return 100;
    }

    @Override
    public void handleLoadPackage(Context context, ClassLoader classLoader, String packageName) {
        Context appContext = context != null && context.getApplicationContext() != null
                ? context.getApplicationContext()
                : context;
        SystemUIHookRegistry.initAll(appContext, classLoader, packageName);
    }
}