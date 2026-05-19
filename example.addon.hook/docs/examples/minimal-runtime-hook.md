# Example: Minimal Runtime Hook

## `META-INF/addon.json`

```json
{
  "id": "minimal_settings_logger",
  "entryClass": "com.example.addon.MinimalSettingsLogger",
  "name": "Minimal Settings Logger",
  "author": "Example",
  "description": "Logs when Android Settings resumes.",
  "version": "1.0.0",
  "targetPackages": ["com.android.settings"],
  "enabled": true,
  "settings": [
    {
      "key": "minimal_settings_logger_enabled",
      "title": "Enable logger",
      "type": "switch",
      "provider": "global",
      "default": true
    }
  ]
}
```

## `src/com/example/addon/MinimalSettingsLogger.java`

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

public final class MinimalSettingsLogger implements IAddonHook {
    private static final String TAG = "MinimalSettingsLogger";
    private static final String TARGET_PACKAGE = "com.android.settings";
    private static final String KEY_ENABLED = "minimal_settings_logger_enabled";

    @Override public String getId() { return "minimal_settings_logger"; }
    @Override public String getName() { return "Minimal Settings Logger"; }
    @Override public String getAuthor() { return "Example"; }
    @Override public String getDescription() { return "Logs Settings resume events."; }
    @Override public String getVersion() { return "1.0.0"; }
    @Override public Set<String> getTargetPackages() { return Collections.singleton(TARGET_PACKAGE); }

    @Override
    public boolean isEnabled(Context context) {
        return Settings.Global.getInt(context.getContentResolver(), KEY_ENABLED, 1) != 0;
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
                            Log.d(TAG, "SettingsActivity.onResume");
                        }
                    }
            );
        } catch (Throwable throwable) {
            Log.e(TAG, "Unable to install hook", throwable);
        }
    }
}
```
