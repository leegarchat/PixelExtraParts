# Example: Navigation Pages

This example creates a top-level entry with three child pages.

```json
{
  "main": [
    {
      "id": "main/display-tools",
      "title": "Display Tools",
      "subtitle": "Brightness, colors, and tiles.",
      "icon": "Settings",
      "iconType": "app",
      "iconShape": "circle",
      "iconColor": "#D7B8FF",
      "iconBackground": "#332A45",
      "group": "display-tools",
      "priority": 100
    },
    {
      "id": "main/display-tools/brightness",
      "title": "Brightness",
      "subtitle": "HBM and brightness behavior.",
      "icon": "BrightnessHigh",
      "iconType": "app",
      "priority": 90,
      "settings": [
        {
          "key": "display_tools_brightness_enabled",
          "title": "Enable brightness override",
          "type": "switch",
          "default": false
        }
      ]
    },
    {
      "id": "main/display-tools/color",
      "title": "Color",
      "subtitle": "Tint and saturation.",
      "icon": "Palette",
      "iconType": "app",
      "priority": 80,
      "settings": []
    },
    {
      "id": "main/display-tools/tiles",
      "title": "Tiles",
      "subtitle": "Quick Settings controls.",
      "icon": "Dashboard",
      "iconType": "app",
      "priority": 70,
      "settings": []
    }
  ]
}
```

Known group names keep the app-defined order. A custom group such as `display-tools` is accepted and appears after known groups, sorted by the highest priority entry inside the group.

To inject a page into a host activity, add `targetActivity`:

```json
{
  "id": "main/display-tools/color",
  "title": "Color",
  "subtitle": "Tint and saturation.",
  "icon": "Palette",
  "iconType": "app",
  "targetActivity": "DisplayActivity",
  "targetSlot": "advanced",
  "priority": 80,
  "settings": []
}
```

Internal navigation between generated pages follows the global Activity Transition setting. With a custom transition APK selected, the page stack uses that APK's `custom_open_*` resources for forward navigation and `custom_close_*` resources for back navigation.
