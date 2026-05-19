# Java Hook Development

Runtime addons implement `org.pixel.customparts.core.IAddonHook`.

## Interface

```java
public interface IAddonHook {
    String getId();
    default String getName() { return getId(); }
    default String getAuthor() { return "Unknown"; }
    default String getDescription() { return ""; }
    default String getVersion() { return "1.0"; }
    Set<String> getTargetPackages();
    void handleLoadPackage(Context context, ClassLoader classLoader, String packageName);
    default int getPriority() { return 0; }
    default boolean isEnabled(Context context) { return true; }
}
```

## Lifecycle

1. The manager scans addon JARs from system and user addon directories.
2. It reads `META-INF/addon.json` before loading code.
3. It checks addon enable state and package scope.
4. It loads `entryClass` from the JAR.
5. It calls `handleLoadPackage(context, classLoader, packageName)` inside the target process.

## Minimal Hook Skeleton

```java
package com.example.addon;

import android.content.Context;
import android.provider.Settings;
import android.util.Log;
import org.pixel.customparts.core.IAddonHook;
import java.util.Collections;
import java.util.Set;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;

public final class MySettingsHook implements IAddonHook {
    private static final String TAG = "MySettingsHook";
    private static final String TARGET_PACKAGE = "com.android.settings";
    private static final String SETTING_ENABLED = "my_settings_hook_enabled";

    @Override public String getId() { return "my_settings_hook"; }
    @Override public String getName() { return "My Settings Hook"; }
    @Override public String getAuthor() { return "Example"; }
    @Override public String getDescription() { return "Hooks Android Settings."; }
    @Override public String getVersion() { return "1.0.0"; }
    @Override public Set<String> getTargetPackages() { return Collections.singleton(TARGET_PACKAGE); }
    @Override public int getPriority() { return 500; }

    @Override
    public boolean isEnabled(Context context) {
        return Settings.Global.getInt(context.getContentResolver(), SETTING_ENABLED, 1) != 0;
    }

    @Override
    public void handleLoadPackage(Context context, ClassLoader classLoader, String packageName) {
        if (!TARGET_PACKAGE.equals(packageName) || !isEnabled(context)) return;

        try {
            XposedHelpers.findAndHookMethod(
                    "com.android.settings.SettingsActivity",
                    classLoader,
                    "onResume",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            Log.d(TAG, "SettingsActivity resumed");
                        }
                    }
            );
        } catch (Throwable throwable) {
            Log.e(TAG, "Failed to install hook", throwable);
        }
    }
}
```

## Reading Settings

Generated UI writes to `Settings.Global`, `Settings.System`, or `Settings.Secure` depending on the manifest `provider`. Hooks usually read from the same provider.

```java
private static boolean getBoolean(Context context, String key, boolean defaultValue) {
    return Settings.Global.getInt(
            context.getContentResolver(),
            key,
            defaultValue ? 1 : 0
    ) != 0;
}
```

## Hook Safety Pattern

Use small helpers and fail closed.

```java
private static Class<?> findClassOrNull(String name, ClassLoader classLoader) {
    try {
        return XposedHelpers.findClass(name, classLoader);
    } catch (Throwable ignored) {
        return null;
    }
}
```

## Content Observer Pattern

For live settings, register an observer in the target process.

```java
context.getContentResolver().registerContentObserver(
        Settings.Global.getUriFor("my_feature_enabled"),
        false,
        new android.database.ContentObserver(new android.os.Handler(android.os.Looper.getMainLooper())) {
            @Override
            public void onChange(boolean selfChange) {
                // Re-read Settings.Global and apply the new value.
            }
        }
);
```

## Priority

Higher priority addons run earlier.

```java
@Override
public int getPriority() {
    return 900;
}
```

Use priority only when order matters. Most addons should stay at the default or use a modest explicit value.

## Common Hooking APIs

```java
XposedHelpers.findAndHookMethod("pkg.Class", classLoader, "methodName", ArgType.class, new XC_MethodHook() { ... });
XposedBridge.hookMethod(method, new XC_MethodHook() { ... });
XposedBridge.hookAllMethods(targetClass, "methodName", new XC_MethodHook() { ... });
XposedHelpers.callMethod(instance, "methodName", args);
XposedHelpers.getObjectField(instance, "fieldName");
XposedHelpers.setObjectField(instance, "fieldName", value);
```

## Practical Rules

- Return immediately when `packageName` is not a target.
- Check `isEnabled(context)` before installing expensive hooks.
- Cache resolved classes and methods only when safe for the target process.
- Avoid blocking target app main threads.
- Use `try/catch` around every reflective hook installation.
- Log enough to diagnose missing classes and renamed methods.
- Keep setting keys in Java exactly aligned with `addon.json`.
