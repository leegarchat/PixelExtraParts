package org.pixel.customparts.addon.diag.hooks;

import android.content.Context;
import android.util.Log;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;

/**
 * Diagnostic hooks for the OLD firmware build. Logs every battery read
 * with its source path so we can prove whether the sysfs fallback is
 * load-bearing or dead. Tag: PepDiagBatt. No throttling by design.
 */
public final class DiagBatteryHooks {
    private static final String TAG = "PepDiagBatt";

    private static final String KEYGUARD_HOOK =
            "org.pixel.customparts.addon.systemui.hooks.KeyguardBatteryPowerHook";
    private static final String AUTOHBM =
            "org.pixel.customparts.utils.AutoHbmController";

    private DiagBatteryHooks() {}

    public static void installSystemUi(final Context context, final ClassLoader cl) {
        try {
            final Class<?> hook = XposedHelpers.findClass(KEYGUARD_HOOK, cl);
            XposedHelpers.findAndHookMethod(hook, "readLongFromFile",
                    String.class, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    Log.d(TAG, "sysfs READ path=" + param.args[0]);
                }
            });
            Log.i(TAG, "hooked " + KEYGUARD_HOOK + "#readLongFromFile");
        } catch (Throwable t) {
            Log.e(TAG, "systemui hook failed", t);
        }
        try {
            final Class<?> hook = XposedHelpers.findClass(KEYGUARD_HOOK, cl);
            XposedHelpers.findAndHookMethod(hook, "readChargeSnapshotFromSys",
                    Context.class, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    Log.d(TAG, "snapshot result=" + (param.getResult() != null ? "OK" : "NULL"));
                }
            });
            Log.i(TAG, "hooked " + KEYGUARD_HOOK + "#readChargeSnapshotFromSys");
        } catch (Throwable t) {
            Log.e(TAG, "systemui snapshot hook failed", t);
        }
    }

    public static void installPepApp(final Context context, final ClassLoader cl) {
        try {
            final Class<?> ctrl = XposedHelpers.findClass(AUTOHBM, cl);
            XposedHelpers.findAndHookMethod(ctrl, "readSocTemperatureC",
                    new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    Log.d(TAG, "soc_therm result=" + param.getResult());
                }
            });
            Log.i(TAG, "hooked " + AUTOHBM + "#readSocTemperatureC");
        } catch (Throwable t) {
            Log.e(TAG, "pepapp hook failed", t);
        }
    }
}
