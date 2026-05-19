# Pixel Extra Parts Addon SDK

This directory is the complete addon development kit for Pixel Extra Parts. An addon is a DEX JAR with a `META-INF/addon.json` descriptor and, optionally, Java hook code. The same package can provide Pine/Xposed-compatible runtime hooks, generated settings UI, generated activity pages, dynamic Quick Settings tiles, target-activity page injection, Settings-homepage injection, update metadata, import/export support, localized text, or any combination of those pieces.

The old mixed guide was kept as `README_old.md` for reference. This README is the main entry point; focused deep dives live in smaller files under `docs/`.

## Addon Building Blocks

| Layer | Manifest area | What it does |
| --- | --- | --- |
| Identity | root fields | Stable `id`, display metadata, version, author, accent, background and update URL. |
| Runtime hook | `entryClass`, `targetPackages` | Loads Java code into selected target packages through the Pine/Xposed-compatible runtime. |
| Addon card | root `settings[]`, update/import/export | Shows the addon in the manager. Cards always expand, even without `entryClass` or settings. |
| Generated controls | `settings[]` | Writes values to `Settings.Global`, `Settings.System`, `Settings.Secure`, or addon files. |
| Main pages | `main[]` | Adds generated pages to Pixel Extra Parts navigation or to target activities. |
| Target injection | `targetActivity`, `targetSlot` | Injects addon pages into built-in screens such as launcher, SystemUI or icon manager pages. |
| Dynamic QS tiles | `type: "tile"` | Binds one of `DynamicAddonTile01..40` to addon settings. |
| Localization | `locales` or `addon_<lang>.json` | Overrides display text for the active system language. |

## Directory Layout

```text
example.addon.hook/
+-- README.md
+-- README_old.md
+-- build_addon.sh
+-- build_addon.ps1
+-- build_addon.bat
+-- prebuild/
|   +-- IAddonHook.java
|   +-- android.jar
|   +-- pine/
|   +-- sdk/
|   +-- xposed/
+-- ambient_extend_hook/
+-- gcam_photo_torch/
+-- icon_manager_settings/
+-- launcher_hooks/
+-- settings_homepage_item/
+-- systemui_hooks/
+-- docs/
```

Each addon project uses this shape:

```text
my_addon/
+-- META-INF/
|   +-- addon.json
+-- src/
|   +-- com/example/addon/MyAddonHook.java
+-- out/
    +-- my_addon.jar
```

For a settings-only addon, omit `src/` and omit `entryClass` from `addon.json`. The build script will package a manifest-only JAR.

## Quick Start

1. Create a project directory next to the examples.

```bash
mkdir -p my_addon/META-INF my_addon/src/com/example/addon
```

2. Add `META-INF/addon.json`.

```json
{
  "id": "my_addon",
  "entryClass": "com.example.addon.MyAddonHook",
  "name": "My Addon",
  "author": "Your Name",
  "description": "A small addon example.",
  "version": "1.0.0",
  "targetPackages": ["com.android.settings"],
  "enabled": true
}
```

3. Add `src/com/example/addon/MyAddonHook.java`.

```java
package com.example.addon;

import android.content.Context;
import org.pixel.customparts.core.IAddonHook;
import java.util.Collections;
import java.util.Set;

public final class MyAddonHook implements IAddonHook {
    @Override public String getId() { return "my_addon"; }
    @Override public String getName() { return "My Addon"; }
    @Override public String getAuthor() { return "Your Name"; }
    @Override public String getDescription() { return "A small addon example."; }
    @Override public String getVersion() { return "1.0.0"; }
    @Override public Set<String> getTargetPackages() { return Collections.singleton("com.android.settings"); }

    @Override
    public void handleLoadPackage(Context context, ClassLoader classLoader, String packageName) {
        if (!"com.android.settings".equals(packageName)) return;
        // Install hooks here.
    }
}
```

4. Build it.

```bash
./build_addon.sh my_addon
```

The output is `my_addon/out/my_addon.jar`.

## Runtime Load Locations

Pixel Extra Parts scans addon JARs from two locations:

```text
/system_ext/etc/pixelparts/addons
/data/pixelparts/addons
```

System addons live under `system_ext`. User or test builds can live under `/data/pixelparts/addons`. When both locations contain an addon with the same `id`, the user addon fully overrides the system addon.

## Manifest Root Fields

| Field | Type | Notes |
| --- | --- | --- |
| `id` | string | Required stable identifier. It is used for enable state, data overrides, settings export and dynamic tile ownership. |
| `entryClass` | string | Fully qualified Java class implementing `IAddonHook`. Omit it for settings-only addons. |
| `name`, `author`, `description`, `version` | string | Display metadata. The base manifest should be English. |
| `targetPackages` | array | Runtime package allow-list. Target only the packages the addon actually hooks. |
| `enabled` | boolean | Default enabled state on first install. |
| `updateUrl` or `otaUrl` | string | Optional update JSON endpoint shown in the expanded addon card. |
| `accent` | color | Accent color used by generated cards and controls. |
| `backgroundMode`, `backgroundAlpha`, `backgroundGradientSteps`, `backgroundScope` | mixed | Optional addon card background styling. |
| `settings` | array | Inline settings rendered inside the addon card. |
| `main` | array | Generated navigation entries and generated pages. |
| `locales` | object | Inline localized descriptor overlays keyed by language, for example `ru`, `de`, `fr`, `uk`. |

Addon cards always expand. The expanded state shows inline settings when present, generated page links when `main[]` exists without inline settings, and import/export actions for every addon. If `updateUrl` is present, the update button appears in the same expanded area.

## Localization

Keep the base `addon.json` in English. Localized text can be shipped inline:

```json
{
    "name": "Ambient Extend",
    "description": "AOD timeout and extra dimming.",
    "locales": {
        "ru": {
            "description": "Таймаут AOD и дополнительное затемнение.",
            "main": [
                {
                    "id": "ambient-extend",
                    "title": "Ambient Extend",
                    "settings": [
                        { "key": "ambient_timeout_group", "title": "AOD blackout" }
                    ]
                }
            ]
        }
    }
}
```

External overlays are also supported: `META-INF/addon_ru.json`, `META-INF/addon_de.json`, `META-INF/addon_fr.json`, `META-INF/addon_uk.json`, or external files next to an installed JAR. Localized overlays merge display fields only: root `name`, `author`, `description`, `main[]` title/subtitle/description/group, setting title/description, select option labels, tile target labels and tile activity labels.

## Generated Settings UI

Settings are declared in root `settings[]` or inside a `main[]` entry. Common fields:

| Field | Notes |
| --- | --- |
| `key` | Stable setting key. Required for non-visual controls. |
| `type` | Control type. Aliases are accepted for many types. |
| `title`, `description` | Display text. Use English in base manifest and overlays for localization. |
| `title_size`, `description_size` | Optional custom text size in `sp`; camel-case and `title_seize` / `description_seize` aliases are also accepted. |
| `provider` | `global`, `system`, or `secure` for Android Settings storage. |
| `default`, `min`, `max`, `step`, `unit` | Numeric and default value metadata. |
| `storage` | `settings`, `addon_file`, `internal`, or `external`. |
| `enabledIfAll`, `enabledIfAny`, `disabledIfAll`, `disabledIfAny` | Dependency gates for controls. |
| `exclusiveGroup`, `exclusiveWith` | Mutually exclusive boolean logic. |
| `icon`, `iconType`, `iconShape`, `iconSize` | Optional Material or file icons for rows and groups. |

Supported control types:

| Type | Purpose |
| --- | --- |
| `switch`, `toggle`, `checkbox` | Boolean controls. They can write normal settings or execute explicit shell commands for on/off states. |
| `int`, `float` | Slider-like numeric controls with min/max/step/unit. |
| `string`, `text` | Text input. |
| `select` | Dropdown selection. |
| `select_button` | Button/radio style selection. |
| `file` | Storage-backed file picker. |
| `app_list` | App picker, either modal or activity based. |
| `color` | Color picker with hex, ARGB, RGB CSV, RGBA CSV or integer output. |
| `group` | Inline, expandable, fullscreen, floating card or immersive group container. |
| `visual`, `text`, `warning`, `divider`, `dashed_line`, `spacer`, `image` | Non-setting layout elements. |
| `tile` | Dynamic QS tile binding UI. |
| `cmd_button`, `command_button`, `button` | Runs a shell command and can expand to show command output. |

## Command Controls

Command controls are intended for explicit addon maintenance actions, diagnostics and controlled system integration. They run through the app shell/root command pipeline used by Pixel Extra Parts utilities.

```json
{
    "key": "collect_launcher_log",
    "type": "cmd_button",
    "title": "Collect launcher log",
    "description": "Runs logcat for the launcher tag.",
    "cmd": "logcat -d -s NexusLauncher PixelLauncher",
    "showOutput": true
}
```

Boolean controls can define explicit on/off commands:

```json
{
    "key": "demo_native_flag",
    "type": "switch",
    "title": "Native flag",
    "provider": "global",
    "default": false,
    "cmdOn": "cmd device_config put launcher demo_flag true",
    "cmdOff": "cmd device_config put launcher demo_flag false",
    "showOutput": true
}
```

When `showOutput` is true, the row expands after execution and displays stdout/stderr. Use `showOutput: false` for silent actions. Command fields are aliases-aware: `cmd`, `command`, `shell`, `cmdOn`, `commandOn`, `onCommand`, `cmdOff`, `commandOff`, `offCommand`.

## Main Pages

`main[]` creates generated navigation rows and pages. Each entry has an `id`, `title`, optional `subtitle`, optional icon fields, optional `group`, optional `priority`, and optional `settings[]` for the page body.

Nested pages are represented with slash-separated IDs:

```json
{
    "main": [
        { "id": "pixel-launcher", "title": "Pixel Launcher", "settings": [] },
        { "id": "main/pixel-launcher/home-screen", "title": "Home screen", "settings": [] }
    ]
}
```

Entries sort by `priority` descending and then by `title`. `targetActivity` and `targetSlot` can inject pages into existing Pixel Extra Parts screens instead of only the addon manager.

## Dynamic QS Tiles

Dynamic tiles bind addon settings to shared tile services `DynamicAddonTile01` through `DynamicAddonTile40`. A tile can toggle a boolean setting or cycle through a carousel of values.

```json
{
    "key": "launcher_custom_tile",
    "type": "tile",
    "title": "Custom launcher tile",
    "tileConfigurable": true,
    "targets": [
        { "key": "launcher_dt2s_enabled", "label": "Double tap to sleep", "mode": "toggle" },
        {
            "key": "launcher_replace_on_clear",
            "label": "Clear All mode",
            "mode": "carousel",
            "values": ["0", "1", "2"],
            "labels": ["Bottom", "Screenshot", "Select"]
        }
    ],
    "pages": [
        { "id": "main/pixel-launcher/home-screen", "label": "Home screen" }
    ]
}
```

Tile click handling runs setting changes on a background thread and uses a guard against concurrent toggles. Long press opens the configured addon page through `TileHandlerActivity`.

## Import, Export And Updates

Every expanded addon card exposes settings import/export. Export writes the current setting values for that addon to JSON. Import reads a compatible JSON document and updates the same keys. If `updateUrl` is present, the card also exposes update checking and install flow.

Update metadata endpoint shape:

```json
{
    "version": "1.2.0",
    "downloadUrl": "https://example.com/my_addon.jar",
    "changelog": "Fixed hooks and added settings.",
    "extraInfo": "Optional text"
}
```

## Documentation Map

- [Addon Package And Build](docs/addon-package-and-build.md)
- [Manifest Reference](docs/manifest-reference.md)
- [Generated Settings UI](docs/generated-settings-ui.md)
- [Generated Main Pages](docs/generated-main-pages.md)
- [Dynamic QS Tiles](docs/dynamic-qs-tiles.md)
- [Java Hook Development](docs/java-hook-development.md)
- [Troubleshooting](docs/troubleshooting.md)

Example-focused documents:

- [Minimal Runtime Hook](docs/examples/minimal-runtime-hook.md)
- [Settings-Only Addon](docs/examples/settings-only-addon.md)
- [Settings Gallery](docs/examples/settings-gallery.md)
- [Groups And Visual Layout](docs/examples/groups-and-visual-layout.md)
- [Navigation Pages](docs/examples/navigation-pages.md)
- [Dynamic Tile Examples](docs/examples/dynamic-tile-examples.md)
- [Java Hook Recipes](docs/examples/java-hook-recipes.md)

## Current Example Addons

- `ambient_extend_hook`: SystemUI hook for AOD blackout, Smart Pixels and extra ambient dimming.
- `gcam_photo_torch`: Google Camera hook that adds persistent Torch behavior to the photo flash menu.
- `settings_homepage_item`: Settings hook that inserts Pixel Extra Parts into the Android Settings homepage.
- `icon_manager_settings`: settings-only generated UI injected into the Icon Manager screen.
- `launcher_hooks`: Pixel Launcher addon for home screen, dock/search, app drawer, recents, gesture bar and dynamic launcher tiles.
- `systemui_hooks`: SystemUI addon for lock screen, charging info, shade/media/scrim and notification icon controls.
- `demo_settings`: settings-only showcase for every generated UI type and layout pattern.

## Practical Rules

- `id` values must be stable. They are used for enable state, data overrides, and user settings.
- `entryClass` must match the compiled Java class exactly. Omit it only for settings-only addons.
- Keep setting keys stable. Hooks read the same keys that generated UI writes.
- Keep base manifest text in English and put translations in `locales` or `addon_<lang>.json` overlays.
- Use `targetPackages` defensively. Do not hook every process unless the addon is designed for that.
- Prefer generated settings over custom UI when the control is a normal setting.
- Use command controls only for explicit shell actions, keep command strings predictable, and avoid interactive commands.
- Use dynamic QS tiles for user-configurable tile behavior instead of declaring new tile services.
- Use background threads for slow hook work and avoid blocking main thread callbacks in target apps.
- Validate JSON before building.

## Build Requirements

- Java 11 or newer.
- `javac`, `jar`, `find`, `sort`, `tail`, `sed`, and `head` on Unix-like shells.
- The bundled `prebuild/android.jar` and `prebuild/sdk/d8.jar`, or SDK equivalents configured through `ANDROID_JAR` and `D8_JAR`.

The build script uses bundled prebuilds first, then searches the local Android SDK. It packages every file under `META-INF/`, so inline `locales` and external `addon_<lang>.json` overlays both survive packaging.
