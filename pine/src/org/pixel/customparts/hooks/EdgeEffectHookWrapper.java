package org.pixel.customparts.hooks;

import android.content.Context;
import org.pixel.customparts.core.BaseHook;

public class EdgeEffectHookWrapper extends BaseHook {

    private static final String KEY_ENABLED = "overscroll_enabled";

    public String keySuffix = "_xposed";
    public boolean useGlobalSettings = true;

    @Override
    public String getHookId() {
        return "EdgeEffectHook";
    }

    @Override
    public int getPriority() {
        return 10;
    }

    public void setKeySuffix(String keySuffix) {
        this.keySuffix = keySuffix;
    }

    public void setUseGlobalSettings(boolean useGlobalSettings) {
        this.useGlobalSettings = useGlobalSettings;
    }

    @Override
    public boolean isEnabled(Context context) {
        if (context == null) return true;
        return isSettingEnabled(context, KEY_ENABLED, true);
    }

    @Override
    protected void onInit(ClassLoader classLoader) {
        try {
            EdgeEffectHook.configure(useGlobalSettings, keySuffix);
            EdgeEffectHook.initWithClassLoader(classLoader);
            
            log("EdgeEffect hook initialized (suffix=" + keySuffix + ", global=" + useGlobalSettings + ")");
        } catch (Throwable e) {
            logError("Failed to initialize EdgeEffect hook", e);
        }
    }
}