package org.pixel.customparts.addon.systemui.hooks;

import android.content.Context;
import android.database.ContentObserver;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Log;

public abstract class BaseSystemUIHook {
    private static final String ACTIVE_SUFFIX = "_lsplant";
    private static final String LEGACY_PINE_SUFFIX = "_pine";
    private static final String XPOSED_SUFFIX = "_xposed";

    /**
     * Master switch for all shade/QS tweaks (settings key {@code shade_tweaks_enabled}).
     * Hooks install unconditionally and gate on this at runtime, so no SystemUI
     * restart is needed to (de)activate. Cached + ContentObserver-invalidated:
     * hot paths pay no binder IPC after the first read.
     */
    protected static final String KEY_SHADE_TWEAKS_ENABLED = "shade_tweaks_enabled";
    private static volatile Boolean sShadeTweaksEnabledCache;
    private static final java.util.Set<String> sObservedSettingKeys =
            java.util.Collections.synchronizedSet(new java.util.HashSet<String>());
    protected static final int USER_CURRENT = -2;

    protected ClassLoader hostClassLoader;

    public abstract String getHookId();

    public int getPriority() {
        return 0;
    }

    public boolean isEnabled(Context context) {
        return true;
    }

    public final void init(ClassLoader classLoader) {
        hostClassLoader = classLoader;
        try {
            onInit(classLoader);
        } catch (Throwable throwable) {
            logError("Error during initialization", throwable);
        }
    }

    protected void onInit(ClassLoader classLoader) {
    }

    protected void log(String message) {
        Log.d(getHookId(), message);
    }

    protected void logError(String message, Throwable throwable) {
        Log.e(getHookId(), message, throwable);
    }

    protected boolean isSettingEnabled(Context context, String key) {
        return isSettingEnabled(context, key, false);
    }

    protected boolean isSettingEnabled(Context context, String key, boolean defaultValue) {
        if (context == null) {
            return defaultValue;
        }
        try {
            return Settings.Global.getInt(context.getContentResolver(), resolveKeyForRead(context, key), defaultValue ? 1 : 0) != 0;
        } catch (Throwable ignored) {
            return defaultValue;
        }
    }

    protected int getIntSetting(Context context, String key, int defaultValue) {
        if (context == null) {
            return defaultValue;
        }
        String resolvedKey = resolveKeyForRead(context, key);
        try {
            return Settings.Global.getInt(context.getContentResolver(), resolvedKey, defaultValue);
        } catch (Throwable ignored) {
            try {
                return parseIntOrColor(Settings.Global.getString(context.getContentResolver(), resolvedKey), defaultValue);
            } catch (Throwable ignoredAgain) {
                return defaultValue;
            }
        }
    }

    protected float getFloatSetting(Context context, String key, float defaultValue) {
        if (context == null) {
            return defaultValue;
        }
        try {
            return Settings.Global.getFloat(context.getContentResolver(), resolveKeyForRead(context, key), defaultValue);
        } catch (Throwable ignored) {
            return defaultValue;
        }
    }

    protected String getStringSetting(Context context, String key, String defaultValue) {
        if (context == null) {
            return defaultValue;
        }
        try {
            String value = Settings.Global.getString(context.getContentResolver(), resolveKeyForRead(context, key));
            return value != null ? value : defaultValue;
        } catch (Throwable ignored) {
            return defaultValue;
        }
    }

    private static String stripSuffix(String key) {
        for (String s : new String[]{ACTIVE_SUFFIX, LEGACY_PINE_SUFFIX, XPOSED_SUFFIX}) {
            if (key.endsWith(s)) {
                return key.substring(0, key.length() - s.length());
            }
        }
        return key;
    }

    protected String resolveKey(String key) {
        // Canonical (write) form; reads go through resolveKeyForRead() fallback.
        if (key == null || key.isEmpty()) {
            return key;
        }
        return stripSuffix(key) + ACTIVE_SUFFIX;
    }

    /**
     * Read-through fallback: new key first, then legacy keys, so settings
     * stored under "_pine" keep working after the engine rename.
     */
    protected String resolveKeyForRead(Context context, String key) {
        if (key == null || key.isEmpty() || context == null) {
            return resolveKey(key);
        }
        String base = stripSuffix(key);
        String[] candidates = new String[]{
                base + ACTIVE_SUFFIX, base + LEGACY_PINE_SUFFIX, base + XPOSED_SUFFIX};
        for (String candidate : candidates) {
            try {
                if (Settings.Global.getString(context.getContentResolver(), candidate) != null) {
                    return candidate;
                }
            } catch (Throwable ignored) {
            }
        }
        return base + ACTIVE_SUFFIX;
    }

    /**
     * Master switch for shade/QS tweaks. Cached after the first read, invalidated
     * via ContentObserver, so hot paths stay IPC-free. Unknown context falls back
     * to enabled (never brick the UI when the context is unavailable).
     */
    protected boolean isShadeTweaksEnabled(Context context) {
        Boolean cached = sShadeTweaksEnabledCache;
        if (cached != null) {
            return cached.booleanValue();
        }
        boolean enabled = isSettingEnabled(context, KEY_SHADE_TWEAKS_ENABLED, true);
        sShadeTweaksEnabledCache = enabled;
        observeSettingOnce(context, KEY_SHADE_TWEAKS_ENABLED, new Runnable() {
            @Override
            public void run() {
                sShadeTweaksEnabledCache = null;
            }
        });
        return enabled;
    }

    /**
     * Registers a ContentObserver for a Global setting exactly once per process.
     * Lets hooks react to setting changes at runtime without polling and without
     * restarting SystemUI.
     */
    protected void observeSettingOnce(Context context, String baseKey, final Runnable onChange) {
        if (context == null || baseKey == null || onChange == null) {
            return;
        }
        String resolvedKey;
        try {
            resolvedKey = resolveKey(baseKey);
        } catch (Throwable ignored) {
            return;
        }
        observeExactSettingOnce(context, resolvedKey, onChange);
    }

    /**
     * Same as {@link #observeSettingOnce}, but the key is used verbatim (no
     * {@code _pine} suffix handling) for hooks that read raw Global keys.
     */
    protected void observeExactSettingOnce(
            Context context, String fullKey, final Runnable onChange) {
        if (context == null || fullKey == null || fullKey.isEmpty() || onChange == null) {
            return;
        }
        synchronized (sObservedSettingKeys) {
            if (!sObservedSettingKeys.add(fullKey)) {
                return;
            }
        }
        try {
            Context app = context.getApplicationContext();
            final Context observerContext = (app != null) ? app : context;
            observerContext.getContentResolver().registerContentObserver(
                    Settings.Global.getUriFor(fullKey),
                    false,
                    new ContentObserver(new Handler(Looper.getMainLooper())) {
                        @Override
                        public void onChange(boolean selfChange) {
                            try {
                                onChange.run();
                            } catch (Throwable ignored) {
                            }
                        }
                    });
        } catch (Throwable ignored) {
            synchronized (sObservedSettingKeys) {
                sObservedSettingKeys.remove(fullKey);
            }
        }
    }

    protected static int userHandleIdentifier(UserHandle userHandle) {
        if (userHandle == null) {
            return 0;
        }
        try {
            Object value = UserHandle.class.getMethod("getIdentifier").invoke(userHandle);
            return value instanceof Integer ? (Integer) value : 0;
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static int parseIntOrColor(String value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return defaultValue;
        }
        try {
            if (trimmed.charAt(0) == '#') {
                return Color.parseColor(trimmed);
            }
            if (trimmed.indexOf(',') >= 0) {
                String[] parts = trimmed.split(",");
                int red = parts.length > 0 ? Integer.parseInt(parts[0].trim()) : 0;
                int green = parts.length > 1 ? Integer.parseInt(parts[1].trim()) : 0;
                int blue = parts.length > 2 ? Integer.parseInt(parts[2].trim()) : 0;
                int alpha = parts.length > 3 ? Integer.parseInt(parts[3].trim()) : 255;
                return Color.argb(alpha, red, green, blue);
            }
            return Integer.decode(trimmed);
        } catch (Throwable ignored) {
            return defaultValue;
        }
    }
}
