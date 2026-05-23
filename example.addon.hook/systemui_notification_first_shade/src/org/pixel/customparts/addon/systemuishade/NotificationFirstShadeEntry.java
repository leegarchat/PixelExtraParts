package org.pixel.customparts.addon.systemuishade;

import android.content.Context;

import org.pixel.customparts.core.IAddonHook;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class NotificationFirstShadeEntry implements IAddonHook {
    private static final String ADDON_ID = "systemui_notification_first_shade";
    private static final String PACKAGE_SYSTEMUI = "com.android.systemui";
    private static final Set<String> TARGETS;

    static {
        Set<String> targets = new HashSet<>();
        targets.add(PACKAGE_SYSTEMUI);
        TARGETS = Collections.unmodifiableSet(targets);
    }

    @Override
    public String getId() {
        return ADDON_ID;
    }

    @Override
    public String getName() {
        return "Notification-first shade";
    }

    @Override
    public String getAuthor() {
        return "PixelExtraParts";
    }

    @Override
    public String getDescription() {
        return "Controls collapsed shade QQS row count while keeping expanded QS stock.";
    }

    @Override
    public String getVersion() {
        return "1.1.0";
    }

    @Override
    public Set<String> getTargetPackages() {
        return TARGETS;
    }

    @Override
    public int getPriority() {
        return 120;
    }

    @Override
    public void handleLoadPackage(Context context, ClassLoader classLoader, String packageName) {
        if (!PACKAGE_SYSTEMUI.equals(packageName)) {
            return;
        }
        Context appContext = context != null && context.getApplicationContext() != null
                ? context.getApplicationContext()
                : context;
        NotificationFirstShadeHook.init(appContext, classLoader);
    }
}