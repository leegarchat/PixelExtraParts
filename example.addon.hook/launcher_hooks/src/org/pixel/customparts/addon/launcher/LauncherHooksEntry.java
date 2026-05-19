package org.pixel.customparts.addon.launcher;

import android.content.Context;

import org.pixel.customparts.addon.launcher.hooks.LauncherHookRegistry;
import org.pixel.customparts.core.IAddonHook;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class LauncherHooksEntry implements IAddonHook {
    private static final String ADDON_ID = "launcher_hooks";
    private static final Set<String> TARGETS;

    static {
        Set<String> targets = new HashSet<>();
        targets.add(LauncherHookRegistry.PACKAGE_NEXUS_LAUNCHER);
        TARGETS = Collections.unmodifiableSet(targets);
    }

    @Override
    public String getId() {
        return ADDON_ID;
    }

    @Override
    public String getName() {
        return "Pixel Launcher Settings";
    }

    @Override
    public String getAuthor() {
        return "PixelExtraParts";
    }

    @Override
    public String getDescription() {
        return "Nexus Launcher layout, drawer, icons and recents customization.";
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
        LauncherHookRegistry.initAll(appContext, classLoader, packageName);
    }
}