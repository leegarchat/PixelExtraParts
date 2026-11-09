package org.pixel.customparts.addon.diag;

import android.content.Context;

import org.pixel.customparts.core.IAddonHook;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.pixel.customparts.addon.diag.hooks.DiagBatteryHooks;

/**
 * Diagnostic addon: proves which read path the OLD firmware build actually
 * uses for battery values — BatteryManager API or sysfs fallback.
 * Runs inside com.android.systemui (keyguard widget) and
 * org.pixel.customparts (AutoHbmController). Logs to tag PepDiagBatt.
 */
public final class DiagBatteryPathHook implements IAddonHook {
    private static final String SYSTEMUI = "com.android.systemui";
    private static final String PEP_APP = "org.pixel.customparts";

    @Override public String getId() { return "diag_battery_path"; }
    @Override public String getName() { return "Diag Battery Path"; }
    @Override public String getAuthor() { return "PixelExtraParts"; }
    @Override public String getDescription() { return "Logs battery read path (API vs sysfs). Tag: PepDiagBatt."; }
    @Override public String getVersion() { return "1.0.0"; }
    @Override public Set<String> getTargetPackages() {
        return new HashSet<>(Arrays.asList(SYSTEMUI, PEP_APP));
    }
    @Override public int getPriority() { return 100; }

    @Override
    public void handleLoadPackage(Context context, ClassLoader classLoader, String packageName) {
        Context app = context != null && context.getApplicationContext() != null
                ? context.getApplicationContext() : context;
        if (SYSTEMUI.equals(packageName)) {
            DiagBatteryHooks.installSystemUi(app, classLoader);
        } else if (PEP_APP.equals(packageName)) {
            DiagBatteryHooks.installPepApp(app, classLoader);
        }
    }
}
