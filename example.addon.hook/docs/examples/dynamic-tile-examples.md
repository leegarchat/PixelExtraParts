# Example: Dynamic Tile Configurations

## One Fixed Toggle Tile

```json
{
  "key": "sleep_tile",
  "settingKey": "launcher_dt2s_enabled",
  "type": "tile",
  "title": "Sleep gesture tile",
  "description": "Toggles double-tap-to-sleep on the launcher.",
  "default": "toggle",
  "unit": "Enabled",
  "pageId": "main/pixel-launcher/home-screen"
}
```

## User-Configurable Tile

```json
{
  "key": "launcher_custom_tile",
  "type": "tile",
  "title": "Custom launcher tile",
  "description": "Choose a target and long-press page.",
  "tileConfigurable": true,
  "targets": [
    {
      "key": "launcher_dt2s_enabled",
      "label": "Double tap sleep",
      "mode": "toggle",
      "pageId": "main/pixel-launcher/home-screen"
    },
    {
      "key": "launcher_replace_on_clear",
      "label": "Clear All mode",
      "mode": "carousel",
      "values": ["0", "1", "2"],
      "labels": ["Bottom", "Screenshot", "Select"],
      "pageId": "main/pixel-launcher/recents/clear-all"
    }
  ]
}
```

## Long Press Pages List

Some UIs expose page choices separately:

```json
{
  "key": "custom_tile_with_pages",
  "type": "tile",
  "title": "Custom tile",
  "tileConfigurable": true,
  "targets": [
    { "key": "feature_enabled", "label": "Feature", "mode": "toggle" }
  ],
  "pages": [
    { "value": "main/my-addon/general", "label": "General" },
    { "value": "main/my-addon/advanced", "label": "Advanced" }
  ]
}
```
