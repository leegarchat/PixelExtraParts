# Manifest Reference

`META-INF/addon.json` is the source of truth for addon identity, runtime target packages, generated UI, navigation pages, and dynamic tiles.

## Minimal Runtime Manifest

```json
{
  "id": "my_runtime_addon",
  "entryClass": "com.example.addon.MyRuntimeAddon",
  "name": "My Runtime Addon",
  "author": "Example Author",
  "description": "Installs a runtime hook into Settings.",
  "version": "1.0.0",
  "targetPackages": ["com.android.settings"],
  "enabled": true
}
```

## Minimal Settings-Only Manifest

```json
{
  "id": "my_settings_page",
  "name": "My Settings Page",
  "author": "Example Author",
  "description": "Generated settings only.",
  "version": "1.0.0",
  "enabled": true,
  "settings": [
    {
      "key": "my_settings_page_enabled",
      "title": "Enable feature",
      "type": "switch",
      "provider": "global",
      "default": false
    }
  ]
}
```

## Root Fields

| Field | Type | Purpose |
| --- | --- | --- |
| `id` | string | Stable addon identifier. Used for settings, scope overrides, and data folders. |
| `entryClass` | string | Fully qualified Java class implementing `IAddonHook`. Omit for settings-only addons. |
| `name` | string | User-facing addon name. Defaults to `id`. |
| `author` | string | User-facing author. |
| `description` | string | User-facing addon description. |
| `version` | string | User-facing version. Defaults to `1.0`. Also used for duplicate system/data addon selection. |
| `targetPackages` | string array | Default target packages. Empty means the addon can apply broadly. |
| `enabled` | boolean | Default enabled state before the user changes it. |
| `settings` | array | Inline generated settings shown in the addon card. |
| `main` | array | Generated main menu entries and nested pages. |
| `icon` | string | Bitmap path inside the JAR, such as `META-INF/icon.png`. |
| `background` | string | Bitmap path inside the JAR for addon card background. |
| `backgroundMode` | string | `gradient` or `cover`. |
| `backgroundAlpha` | int | Background image intensity from `0` to `100`. |
| `backgroundGradientSteps` | int array | Overlay opacity stops from `0` to `100`, minimum two values. |
| `backgroundBlur` | boolean | Enables blur over background image. |
| `backgroundBlurRadius` | int | Blur radius in dp, clamped to `0..100`. |
| `backgroundScope` | string | `full` or `header`. |
| `cardColor` | string | Custom card color as hex. |
| `accent` or `accentColor` | string | Default accent color for generated controls. |
| `updateUrl` or `otaUrl` | string | Optional JSON endpoint for update checks. |

## Card Styling Example

```json
{
  "id": "visual_addon",
  "entryClass": "com.example.VisualAddon",
  "name": "Visual Addon",
  "version": "1.0.0",
  "targetPackages": ["com.android.systemui"],
  "icon": "META-INF/icon.png",
  "background": "META-INF/header.webp",
  "backgroundMode": "gradient",
  "backgroundAlpha": 64,
  "backgroundGradientSteps": [0, 42, 82, 100],
  "backgroundBlur": true,
  "backgroundBlurRadius": 14,
  "backgroundScope": "header",
  "accent": "#8BDDED"
}
```

## Main Entry Fields

`main[]` creates navigation entries and generated pages.

| Field | Type | Purpose |
| --- | --- | --- |
| `id` | string | Path-like ID. `main/root/child` creates a child page under `root`. |
| `title` | string | Row or page title. |
| `subtitle` or `description` | string | Row or page subtitle. |
| `title_size`, `titleSize`, `title_seize` | number/string | Optional title text size in `sp`. |
| `description_size`, `descriptionSize`, `description_seize`, `subtitle_size` | number/string | Optional subtitle/description text size in `sp`. |
| `icon` | string | Material icon name or bitmap path. |
| `iconType` | string | `app` for Material icons, `file` for JAR images. |
| `iconShape` | string | `circle`, `rounded`, or `none`. |
| `iconSize` | int | Icon size in dp. |
| `iconColor` or `iconTint` | string | Icon tint as hex. |
| `iconBackground` or `iconBackgroundColor` | string | Icon container color as hex. |
| `group` | string | Main menu group. Known values are `launcher`, `system`, `network`, `gesture`; custom group names are also accepted. |
| `priority` | int | Higher priority entries sort first. |
| `targetActivity` | string | Injects the entry into an existing activity page. |
| `targetSlot` | string | Optional slot name used by the target activity. |
| `settings` | array | Settings displayed on this page. |

When the same addon `id` exists in `/system_ext/etc/pixelparts/addons` and `/data/pixelparts/addons`, the active copy is chosen by `version`: higher version wins, equal version prefers `/data`. Keep system addon versions increasing across OTA releases so stale user overrides do not mask newer built-in code.

## Localization

The base descriptor is always `META-INF/addon.json`. Localized descriptors can be packed next to it:

```text
META-INF/addon.json
META-INF/addon_ru.json
META-INF/addon_fr.json
```

External overrides are also supported next to the JAR:

```text
my_addon.jar
my_addon.jar.json
addon_ru.json
my_addon_ru.json
```

`my_addon.jar.json` is a full base descriptor override. `addon_ru.json` and `my_addon_ru.json` are localized descriptor overlays.

The localized descriptor merges display fields only:

- Root `name`, `author`, `description`.
- Setting `title`, `description`.
- Option `label`, matched by `value`.
- Nested settings, matched by `key`.
- Main entry `title`, `subtitle`, `description`, matched by `id`.
- Dynamic tile target `label`, matched by `key`/`value`.

Example:

```json
{
  "name": "Localized Name",
  "description": "Localized addon description.",
  "settings": [
    {
      "key": "feature_enabled",
      "title": "Localized title",
      "description": "Localized setting description."
    }
  ]
}
```
