# Example: Settings-Only Addon

This addon has no `src/` directory and no `entryClass`. It only contributes generated UI.

## Layout

```text
simple_settings/
+-- META-INF/
  +-- addon.json
```

## Manifest

```json
{
  "id": "simple_settings",
  "name": "Simple Settings",
  "author": "Example",
  "description": "A manifest-only generated settings page.",
  "version": "1.0.0",
  "enabled": true,
  "backgroundScope": "header",
  "accent": "#8BDDED",
  "settings": [
    {
      "key": "simple_settings_enabled",
      "title": "Enable feature",
      "description": "Stores 1 or 0 in Settings.Global.",
      "type": "switch",
      "provider": "global",
      "default": false,
      "icon": "Rounded.PowerSettingsNew",
      "iconType": "app",
      "iconShape": "circle",
      "iconSize": 20
    },
    {
      "key": "simple_settings_level",
      "title": "Level",
      "type": "int",
      "provider": "global",
      "default": 50,
      "min": 0,
      "max": 100,
      "step": 5,
      "unit": "%",
      "enabledIfAll": ["simple_settings_enabled"]
    }
  ]
}
```

## Build

```bash
./build_addon.sh simple_settings
```

The output JAR contains `META-INF/addon.json` and does not contain `classes.dex`.
