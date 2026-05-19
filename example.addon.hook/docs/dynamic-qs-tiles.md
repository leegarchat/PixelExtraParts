# Dynamic QS Tiles

Dynamic QS tiles let an addon bind one of the shared `DynamicAddonTile01..40` services to a setting from JSON.

## Basic Tile

```json
{
  "key": "feature_tile",
  "settingKey": "feature_enabled",
  "type": "tile",
  "title": "Feature tile",
  "description": "Toggles the feature.",
  "default": "toggle",
  "unit": "On",
  "pageId": "main/my-addon/general"
}
```

The tile UI stores a slot configuration in `Settings.Global`:

```text
pixel_addon_tile_{slot}_enabled
pixel_addon_tile_{slot}_tile_id
pixel_addon_tile_{slot}_key
pixel_addon_tile_{slot}_mode
pixel_addon_tile_{slot}_label
pixel_addon_tile_{slot}_addon_id
pixel_addon_tile_{slot}_page_id
pixel_addon_tile_{slot}_summary_on
pixel_addon_tile_{slot}_summary_off
pixel_addon_tile_{slot}_values
pixel_addon_tile_{slot}_labels
```

## Configurable Tile

Use a configurable tile when users should choose what the tile controls.

```json
{
  "key": "custom_tile",
  "type": "tile",
  "title": "Custom tile",
  "description": "Choose label, target setting, and long-press page.",
  "tileConfigurable": true,
  "targets": [
    {
      "key": "feature_enabled",
      "label": "Feature enabled",
      "mode": "toggle",
      "pageId": "main/my-addon/general"
    },
    {
      "key": "feature_mode",
      "label": "Feature mode",
      "mode": "carousel",
      "values": ["off", "balanced", "fast"],
      "labels": ["Off", "Balanced", "Fast"],
      "pageId": "main/my-addon/general"
    }
  ]
}
```

## Toggle Mode

Toggle mode reads the target setting as an integer. `0` means inactive, non-zero means active. On click, it writes `1` or `0`.

```json
{
  "key": "feature_enabled",
  "label": "Feature",
  "mode": "toggle"
}
```

## Carousel Mode

Carousel mode cycles through string values.

```json
{
  "key": "quality_mode",
  "label": "Quality",
  "mode": "carousel",
  "values": ["low", "balanced", "high"],
  "labels": ["Low", "Balanced", "High"]
}
```

If the current value is absent or unknown, the first click selects the first `values[]` item.

## Long Press Routing

`pageId` controls the generated page opened from QS tile preferences.

```json
{
  "key": "feature_enabled",
  "label": "Feature",
  "mode": "toggle",
  "pageId": "main/my-addon/general"
}
```

The tile handler opens `AddonPageActivity` with `includeTargetActivityEntries = true`, so target-activity pages can still be resolved.

## Responsiveness Notes

Dynamic tile clicks run setting changes on a background thread and use a `toggleInProgress` guard, matching the pattern used by the built-in Auto HBM and Saturation tiles. After writing the value, the tile asks `PixelPartsTileRefresher` to refresh every tile bound to the same setting.

## Design Tips

- Keep tile labels short.
- Use `summary_on` and `summary_off` through the generated UI defaults when possible.
- Prefer `toggle` for boolean settings and `carousel` only for small option sets.
- Give every target a `pageId` so long press lands near the setting it controls.
