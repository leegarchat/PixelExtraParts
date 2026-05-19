# Example: Groups And Visual Layout

## Inline Group

```json
{
  "key": "layout_group",
  "type": "group",
  "title": "Layout",
  "description": "Compact inline section.",
  "title_size": 16,
  "description_size": 13,
  "mode": "inline",
  "icon": "Rounded.ViewModule",
  "iconType": "app",
  "iconShape": "circle",
  "iconSize": 20,
  "settings": [
    {
      "key": "layout_enabled",
      "title": "Enable layout override",
      "type": "switch",
      "default": false
    }
  ]
}
```

## Expandable Group

```json
{
  "key": "advanced_group",
  "type": "group",
  "title": "Advanced",
  "description": "Hidden until expanded.",
  "mode": "expandable",
  "defaultExpanded": false,
  "color": "#202A34",
  "surfaceAlpha": 0.72,
  "settings": [
    {
      "key": "advanced_value",
      "title": "Advanced value",
      "type": "int",
      "default": 10,
      "min": 0,
      "max": 100
    }
  ]
}
```

## Visual Elements

```json
{
  "key": "section_note",
  "type": "info",
  "title": "Note",
  "description": "Use visual text sparingly. Prefer clear setting labels."
}
```

```json
{
  "key": "soft_divider",
  "type": "divider",
  "height": 8,
  "thickness": 1,
  "color": "#33445566"
}
```

```json
{
  "key": "warning_block",
  "type": "warning",
  "title": "Requires restart",
  "description": "This change applies after the target process restarts.",
  "title_size": 15,
  "description_size": 12,
  "color": "#FFD18B"
}
```

## Typography Overrides

Generated rows, groups, visual text, and warning blocks accept `title_size` and `description_size` in `sp`. The parser also accepts camel-case aliases such as `titleSize` and legacy typo aliases such as `title_seize` / `description_seize`.
