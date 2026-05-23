# Example: Settings Gallery

Use this as a compact reference for different setting controls.

```json
{
  "settings": [
    {
      "key": "gallery_switch",
      "title": "Switch",
      "type": "switch",
      "provider": "global",
      "default": true
    },
    {
      "key": "gallery_checkbox",
      "title": "Checkbox",
      "type": "checkbox",
      "provider": "global",
      "default": false
    },
    {
      "key": "gallery_int",
      "title": "Integer slider",
      "type": "int",
      "provider": "global",
      "default": 40,
      "min": 0,
      "max": 100,
      "step": 10,
      "unit": "%"
    },
    {
      "key": "gallery_float",
      "title": "Float slider",
      "type": "float",
      "provider": "global",
      "default": 1.0,
      "min": 0.0,
      "max": 2.0,
      "step": 0.1,
      "unit": "x"
    },
    {
      "key": "gallery_string",
      "title": "String",
      "type": "string",
      "provider": "global",
      "default": "default text"
    },
    {
      "key": "gallery_select",
      "title": "Dropdown",
      "type": "select",
      "provider": "global",
      "default": "normal",
      "options": [
        { "value": "compact", "label": "Compact" },
        { "value": "normal", "label": "Normal" },
        { "value": "wide", "label": "Wide" }
      ]
    },
    {
      "key": "gallery_buttons",
      "title": "Button select",
      "type": "select_button",
      "provider": "global",
      "default": "balanced",
      "options": [
        { "value": "battery", "label": "Battery" },
        { "value": "balanced", "label": "Balanced" },
        { "value": "performance", "label": "Performance" }
      ]
    },
    {
      "key": "gallery_color",
      "title": "Color",
      "type": "color",
      "provider": "global",
      "default": "#8BDDED",
      "format": "hex"
    },
    {
      "key": "gallery_apps",
      "title": "App list",
      "type": "app_list",
      "storage": "addon_file",
      "showSelected": true
    },
    {
      "key": "gallery_multi_write",
      "title": "Multi-write switch",
      "type": "switch",
      "storage": "addon_file",
      "settingsOn": [
        { "provider": "secure", "key": "gallery_secure_flag", "type": "int", "value": 1 }
      ],
      "settingsOff": [
        { "provider": "secure", "key": "gallery_secure_flag", "type": "int", "value": 0 }
      ]
    },
    {
      "key": "gallery_carrier_action",
      "title": "Carrier action switch",
      "type": "switch",
      "storage": "addon_file",
      "binderOn": [
        { "type": "carrier_config", "subIds": "active", "values": [ { "key": "carrier_volte_available_bool", "type": "bool", "value": true } ] }
      ],
      "binderOff": [
        { "type": "carrier_config", "subIds": "active", "values": [ { "key": "carrier_volte_available_bool", "type": "bool", "value": false } ] }
      ]
    }
  ]
}
```
