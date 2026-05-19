# Addon Package And Build

An addon is a JAR that contains a descriptor and, optionally, compiled DEX code.

## Runtime Addon Package

```text
my_addon.jar
+-- classes.dex
+-- META-INF/
    +-- addon.json
```

Use this package type when the addon implements `org.pixel.customparts.core.IAddonHook` and needs runtime hook code.

## Settings-Only Package

```text
my_settings.jar
+-- META-INF/
    +-- addon.json
```

Use this package type when the addon only contributes generated UI. The project has no Java source files and `addon.json` has no `entryClass`.

## Project Layout

```text
my_addon/
+-- META-INF/
|   +-- addon.json
|   +-- addon_ru.json
+-- src/
|   +-- com/example/addon/MyAddonHook.java
+-- build/
+-- out/
```

`build/` is temporary and removed by the script. `out/` contains the final JAR.

## Runtime Load Locations

The manager scans addon JARs from:

```text
/system_ext/etc/pixelparts/addons
/data/pixelparts/addons
```

The system directory is for built-in or ROM-provided addons. The data directory is for user or test overrides. If both directories contain a JAR with the same `id`, the data version wins and the system version is skipped entirely.

An external base descriptor can also sit next to the JAR as `<jar-file>.json`. For example, `my_addon.jar.json` is read before the packed `META-INF/addon.json`.

## Build Script

```bash
./build_addon.sh my_addon
```

The first argument is the output addon name. If a directory with the same name exists next to the script, it is used as the project root.

You can also pass an explicit project path:

```bash
./build_addon.sh my_output_name ./path/to/project
```

## What The Script Does

For runtime addons:

1. Checks Java 11+ and required shell tools.
2. Compiles `prebuild/IAddonHook.java` as a local stub.
3. Compiles all Java files under `src/`.
4. Converts compiled classes to `classes.dex` through D8.
5. Packages `classes.dex` and `META-INF/` into `out/<name>.jar`.

For settings-only addons:

1. Checks the environment.
2. Packages `META-INF/` into `out/<name>.jar`.
3. Does not require D8 or `android.jar` for Java compilation.

## Optional Environment Overrides

```bash
ANDROID_JAR=/path/to/android.jar D8_JAR=/path/to/d8.jar ./build_addon.sh my_addon
```

The script prefers local prebuilds:

- `prebuild/android.jar`
- `prebuild/sdk/d8.jar`
- `prebuild/pine/pine-xposed.jar`
- `prebuild/pine/pine-core.jar`
- `prebuild/xposed/api-82.jar`

## JSON Validation

Validate the manifest before building:

```bash
python3 -m json.tool my_addon/META-INF/addon.json >/dev/null
```

## Recommended Build Checks

```bash
./build_addon.sh my_addon
jar tf my_addon/out/my_addon.jar
unzip -p my_addon/out/my_addon.jar META-INF/addon.json | python3 -m json.tool >/dev/null
```

For runtime addons, `jar tf` should show both `classes.dex` and `META-INF/addon.json`. For settings-only addons, `classes.dex` is intentionally absent.
