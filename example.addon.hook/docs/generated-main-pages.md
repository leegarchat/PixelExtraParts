# Generated Main Pages

`main[]` builds navigation rows and generated activity pages. It is the preferred way to keep complex addon UI out of one huge settings card.

## Single Page

```json
{
  "main": [
    {
      "id": "main/my-addon/display",
      "title": "Display",
      "subtitle": "Brightness, tint, and animation.",
      "title_size": 18,
      "description_size": 13,
      "icon": "Tune",
      "iconType": "app",
      "iconShape": "circle",
      "iconColor": "#8BDDED",
      "iconBackground": "#263A40",
      "group": "system",
      "priority": 100,
      "settings": [
        {
          "key": "display_enabled",
          "title": "Enable display hook",
          "type": "switch",
          "default": false
        }
      ]
    }
  ]
}
```

## Nested Pages

Path segments create a tree. The `main/` prefix is ignored by the parser, but it makes JSON easier to read.

```json
{
  "main": [
    {
      "id": "main/my-addon",
      "title": "My Addon",
      "subtitle": "All pages for this addon.",
      "icon": "Settings",
      "iconType": "app",
      "priority": 100
    },
    {
      "id": "main/my-addon/general",
      "title": "General",
      "subtitle": "Common controls.",
      "icon": "Tune",
      "iconType": "app",
      "priority": 90,
      "settings": []
    },
    {
      "id": "main/my-addon/advanced",
      "title": "Advanced",
      "subtitle": "Riskier controls.",
      "icon": "Security",
      "iconType": "app",
      "priority": 80,
      "settings": []
    }
  ]
}
```

If a parent is missing, the entry is promoted to the nearest existing ancestor or to the top level.

## Target Activities

`targetActivity` injects an entry into an existing Pixel Extra Parts activity instead of only showing it in the addon manager.

```json
{
  "id": "main/icon-manager/advanced-settings",
  "title": "Advanced Icon Settings",
  "subtitle": "System, notification, launcher, and tint controls.",
  "icon": "Apps",
  "iconType": "app",
  "iconShape": "circle",
  "iconColor": "#D7B8FF",
  "iconBackground": "#332A45",
  "targetActivity": "AppIconsActivity",
  "targetSlot": "advanced",
  "priority": 1000,
  "settings": []
}
```

Target matching accepts either full activity class names or short names without the `Activity` suffix. For example, `SystemUISettingsActivity`, `SystemUISettings`, and a matching fully qualified class can all resolve to the same activity.

## Page Transitions

Generated pages use an internal pseudo-activity stack. Forward navigation and back navigation follow the built-in Activity Transition open/close modes when they are configured.

Built-in modes reuse the same app animation resources as normal Activity transitions. When a custom transition APK is active, generated addon pages load these animation resources from that package:

```text
custom_open_enter
custom_open_exit
custom_close_enter
custom_close_exit
```

Forward navigation uses the custom open pair. Back navigation uses the custom close pair. If any resource or package lookup fails, the page stack falls back to the normal generated slide/fade animation.

## Icon Fields

Material icon example:

```json
{
  "icon": "Rounded.Settings",
  "iconType": "app",
  "iconShape": "circle",
  "iconSize": 20,
  "iconColor": "#A9C7FF",
  "iconBackground": "#243047"
}
```

Bitmap icon example:

```json
{
  "icon": "META-INF/page_icon.png",
  "iconType": "file",
  "iconShape": "rounded",
  "iconSize": 22,
  "iconBackground": "#202124"
}
```

If `icon` is absent or cannot resolve, the row falls back to the extension icon.

## Typography Fields

`main[]` entries accept `title_size` and `description_size` in `sp`. The aliases `titleSize`, `descriptionSize`, `title_seize`, `description_seize`, `subtitle_size`, and `subtitleSize` are also accepted. These values apply to addon manager page buttons, generated activity navigation rows, and generated page headers/subtitles.

## Sorting

Entries sort by `priority` descending and then by `title`. Use larger priorities for important pages. Known top-level group values (`launcher`, `gesture`, `system`, `network`) keep the app-defined order. Custom group names are accepted and sorted after known groups by the highest priority entry inside each group.
