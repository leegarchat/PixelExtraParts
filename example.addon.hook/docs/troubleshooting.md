# Troubleshooting

## The Addon Does Not Appear

Check the JAR contents:

```bash
jar tf my_addon/out/my_addon.jar
```

Required:

- `META-INF/addon.json`
- `classes.dex` for runtime addons

Validate JSON:

```bash
unzip -p my_addon/out/my_addon.jar META-INF/addon.json | python3 -m json.tool >/dev/null
```

## The Addon Appears But The Hook Does Not Run

Check:

- `entryClass` exactly matches the Java package and class name.
- `targetPackages` contains the process package actually being loaded.
- `enabled` is true, or the user has enabled the addon in the manager.
- `isEnabled(context)` does not return false.
- The hook is not failing during class lookup.

## Settings UI Appears But The Hook Ignores Values

Check:

- The Java setting key matches `addon.json` exactly.
- The provider matches: `global`, `system`, or `secure`.
- The value type matches the reader: int for switches, string for select/carousel, float for float sliders.
- Dependencies are not forcing a setting value when disabled.

## The Wrong Copy Loads After OTA Or Manual Install

When both `/system_ext/etc/pixelparts/addons` and `/data/pixelparts/addons` contain the same addon `id`, the active copy is selected by descriptor `version`.

Check:

- The system addon version is higher than the stale `/data` copy when an OTA should take over.
- Equal versions intentionally keep the `/data` copy active as a user override.
- The manager shows and removes the stale data override for an OTA-newer system addon.
- The updated JAR really contains the new `META-INF/addon.json` version.

## A Generated Page Does Not Show In A Target Activity

Check:

- The `main[]` entry has `targetActivity` set.
- The short target name matches the host activity name without the `Activity` suffix.
- `targetSlot` matches the host slot if the host filters by slot.
- The addon JAR currently installed contains the updated descriptor.

## Dynamic Tile Is Bound But Does Not Update

Check:

- The tile slot is enabled in `pixel_addon_tile_{slot}_enabled`.
- `pixel_addon_tile_{slot}_key` points to the expected setting key.
- Toggle targets use integer `0` and `1` values.
- Carousel targets include `values[]` and optional `labels[]`.
- The tile service component is enabled by the generated tile binding UI.

## Build Fails With Missing D8 Or Android Jar

Either keep the bundled prebuilds or pass explicit paths:

```bash
ANDROID_JAR=/path/to/android.jar D8_JAR=/path/to/d8.jar ./build_addon.sh my_addon
```

## Java Compilation Fails

Check imports. Runtime addons can compile against:

- `android.jar`
- `prebuild/IAddonHook.java`
- `prebuild/pine/pine-xposed.jar`
- `prebuild/pine/pine-core.jar`
- `prebuild/xposed/api-82.jar`

Do not import app-only classes from Pixel Extra Parts unless they are intentionally present in the target process. The stable addon interface is `IAddonHook` plus Android and Pine/Xposed APIs.
