# Generated Settings UI

Generated settings are declared in `settings[]` arrays. A root `settings[]` array appears inside the addon card. A `main[]` entry can also contain its own `settings[]`, which appear inside that generated page.

## Common Fields

| Field | Type | Purpose |
| --- | --- | --- |
| `key` | string | Stable setting key. Required for non-visual settings. |
| `title` | string | User-facing label. |
| `description` | string | Optional supporting text. |
| `title_size`, `titleSize`, `title_seize` | number/string | Optional title text size in `sp`, clamped to `6..64`. |
| `description_size`, `descriptionSize`, `description_seize` | number/string | Optional description text size in `sp`, clamped to `6..64`. |
| `type` | string | Control type. See below. |
| `provider` | string | `global`, `system`, or `secure`. Defaults to `global`. |
| `default` | mixed | Default int, float, string, or boolean value. |
| `storage` or `store` | string | `settings`, `addon_file`, `internal`, or `external`. Defaults to settings provider. |
| `enabledIfAll` | string array | Enable only when all listed settings are active. |
| `enabledIfAny` | string array | Enable when any listed setting is active. |
| `disabledIfAll` | string array | Disable when all listed settings are active. |
| `disabledIfAny` | string array | Disable when any listed setting is active. |
| `forceValueWhenDisabled` | string | Force a value when dependencies disable this setting. |
| `exclusiveGroup` | string | Makes related booleans mutually exclusive by group name. |
| `exclusiveWith` | string array | Turns listed boolean settings off when this setting becomes true. |
| `accent` or `accentColor` | string | Per-setting accent color. |
| `icon` | string | Material icon name or JAR image path. |
| `iconType` | string | `app` or `file`. |
| `iconShape` | string | `none`, `circle`, or `rounded`. |
| `iconSize` | int | Icon size in dp, clamped to `8..64`. |

## Supported Types

| Type values | Generated control |
| --- | --- |
| `switch` | Material switch row. |
| `toggle`, `bool`, `boolean` | Toggle row. |
| `checkbox`, `check` | Checkbox row. |
| `int` | Integer slider. |
| `float` | Float slider. |
| `string`, `str` | Text input. |
| `select`, `arr` | Dropdown select. |
| `select_button`, `button_select`, `radio_button` | Segmented/button select. |
| `cmd_button`, `command_button`, `shell_button`, `cmd`, `command` | Shell command button. |
| `color`, `colour` | Color picker. |
| `file` | File picker. |
| `apps`, `app_list`, `package_list`, `packages` | Package picker. |
| `group`, `subgroup`, `section` | Nested group. |
| `tile`, `qs_tile`, `quick_tile` | Dynamic QS tile binding UI. |
| `text`, `info`, `description` | Visual text block. |
| `image` | Visual image from the JAR. |
| `spacer`, `space` | Spacer. |
| `divider`, `line` | Divider. |
| `dashed`, `dashed_line` | Dashed divider. |
| `warning` | Warning visual block. |

## Numeric Controls

```json
{
  "key": "sample_intensity",
  "title": "Intensity",
  "description": "Discrete integer value.",
  "type": "int",
  "provider": "global",
  "default": 50,
  "min": 0,
  "max": 100,
  "step": 5,
  "unit": "%",
  "icon": "Rounded.Tune",
  "iconType": "app",
  "iconShape": "circle",
  "iconSize": 20
}
```

```json
{
  "key": "sample_scale",
  "title": "Scale",
  "type": "float",
  "provider": "global",
  "default": 1.0,
  "min": 0.5,
  "max": 2.0,
  "step": 0.05,
  "unit": "x"
}
```

## Select Controls

```json
{
  "key": "sample_mode",
  "title": "Mode",
  "type": "select",
  "provider": "global",
  "default": "balanced",
  "options": [
    { "value": "off", "label": "Off" },
    { "value": "balanced", "label": "Balanced" },
    { "value": "fast", "label": "Fast" }
  ]
}
```

```json
{
  "key": "sample_button_mode",
  "title": "Button mode",
  "type": "select_button",
  "provider": "global",
  "default": "compact",
  "options": [
    { "value": "compact", "label": "Compact" },
    { "value": "normal", "label": "Normal" },
    { "value": "wide", "label": "Wide" }
  ]
}
```

## Color Controls

`format` can be `hex`, `hex_argb`, `rgb`, `rgba`, or `int`. Set `alpha` to `true` when alpha editing is required.

```json
{
  "key": "sample_tint",
  "title": "Tint color",
  "type": "color",
  "provider": "global",
  "default": "#8BDDED",
  "format": "hex",
  "alpha": false
}
```

```json
{
  "key": "sample_scrim_tint",
  "title": "Scrim tint",
  "type": "color",
  "provider": "global",
  "default": "0",
  "format": "int",
  "alpha": true
}
```

## App List Control

```json
{
  "key": "sample_target_apps",
  "title": "Target apps",
  "description": "Pick packages controlled by this addon.",
  "type": "app_list",
  "storage": "addon_file",
  "showSelected": true,
  "appPickerMode": "modal"
}
```

Use `appPickerMode: "activity"` for the full activity picker.

## Command Controls

A command button runs `cmd`/`command` through the app shell helper. When `showOutput` is true, stdout, stderr, exit code, or timeout details expand under the row.

```json
{
  "key": "dump_launcher_config",
  "title": "Dump launcher config",
  "description": "Runs a diagnostic command and displays the result.",
  "type": "cmd_button",
  "cmd": "cmd device_config list launcher",
  "showOutput": true
}
```

Switches and toggles can run exact commands for each state:

```json
{
  "key": "demo_flag_enabled",
  "title": "Demo flag",
  "type": "switch",
  "default": false,
  "cmdOn": "cmd device_config put launcher demo_flag true",
  "cmdOff": "cmd device_config put launcher demo_flag false",
  "showOutput": true
}
```

Use `showOutput: false` for silent actions. Accepted command aliases include `cmd`, `command`, `shell`, `cmdOn`, `commandOn`, `onCommand`, `cmdOff`, `commandOff`, and `offCommand`.

## Dependencies

```json
{
  "key": "feature_enabled",
  "title": "Enable feature",
  "type": "switch",
  "default": false
},
{
  "key": "feature_strength",
  "title": "Strength",
  "type": "int",
  "default": 50,
  "min": 0,
  "max": 100,
  "enabledIfAll": ["feature_enabled"]
}
```

Dependency expressions can also compare values:

```json
{
  "key": "advanced_value",
  "title": "Advanced value",
  "type": "int",
  "default": 10,
  "enabledIfAll": ["feature_mode=advanced"]
}
```

Prefix a dependency with `!` to negate it:

```json
"enabledIfAll": ["!feature_locked"]
```

## Groups

```json
{
  "key": "display_group",
  "type": "group",
  "title": "Display",
  "description": "Display-related options.",
  "mode": "inline",
  "icon": "Rounded.Tune",
  "iconType": "app",
  "iconShape": "circle",
  "settings": [
    {
      "key": "display_feature_enabled",
      "title": "Enable display feature",
      "type": "switch",
      "default": false
    }
  ]
}
```

Group modes:

- `inline`, `flat`, `embedded`: render title and children in place.
- `expand`, `expanded`, `expandable`: expandable row.
- `fullscreen`, `full_screen`, `full`: opens a full-screen group dialog.
- `modal`, `card`, `floating`, `floating_card`: opens a dialog/card group.
- `immersive_expand`, `inline_expand`: richer expandable surface.

Group titles, descriptions, inline visual text, warning blocks, group dialogs, and normal setting rows support `title_size`/`description_size` and their camel-case or `*_seize` aliases.
