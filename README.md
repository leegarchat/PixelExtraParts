# PixelExtraParts

PixelExtraParts is a system-level customization package for Android ROM builds. It provides a privileged settings app, runtime hooks for Launcher/SystemUI/framework behavior, optional Xposed packaging, Pine-based ART hook injection, thermal profile tooling, and an addon SDK for external hook modules.

Russian documentation: [README_RU.md](README_RU.md)

## Audience

This project is intended for Android ROM and device maintainers who build from source and want Pixel-style customization controls inside the system image. It is not a regular Play Store application: the main system target uses platform APIs, platform signing, privileged permissions, and `system_ext` integration.

## What It Provides

PixelExtraParts combines several pieces:

| Area | Purpose |
| --- | --- |
| Privileged system app | Compose/Material3 settings UI under package `org.pixel.customparts`. |
| Xposed APK | Test/module APK under package `org.pixel.customparts.xposed`, using Xposed API 82. |
| Pine injection JAR | `PineInject.jar` runtime hook payload for source-built ROM integration. |
| Built-in hooks | Launcher grid/recents tweaks, overscroll physics, magnifier, activity transitions, predictive back controls, SystemUI/doze/shade tweaks. |
| Thermal tooling | Build-time generation of thermal profile JSON files from a vendor `thermal_info_config.json`. |
| Source patch tooling | Python patch launcher for framework and Settings snapshot changes stored in `changebe/`. |
| Addon SDK | Example external hook projects that can be packaged as addon JARs and loaded dynamically. |
| OTA helpers | Device OTA metadata helpers under `OTA/`. |

## Repository Layout

| Path | Description |
| --- | --- |
| [Android.bp](Android.bp) | Soong modules, prebuilt libraries, APK targets, and `PineInject`. |
| [device.mk](device.mk) | Product include file for ROM/device trees. |
| [common/](common/) | Shared app code, Compose UI, resources, managers, and utility classes. |
| [system/](system/) | Privileged system APK manifest and system-build `AppConfig`. |
| [xposed-pine/](xposed-pine/) | Xposed APK, Pine runtime sources, hook core, managers, built-in hooks, and prebuilt Pine/Xposed jars. |
| [changebe/](changebe/) | Source-tree snapshots for framework and Settings patches. |
| [patches/](patches/) | Python patch launcher that applies or checks `changebe/` snapshots. |
| [ThermalConfigs/](ThermalConfigs/) | Thermal profile generator and generated-copy-rule integration. |
| [example.addon.hook/](example.addon.hook/) | External addon hook examples and build scripts. |
| [sepolicy/](sepolicy/) | SELinux policy snippets required by `device.mk`. |
| [OTA/](OTA/) | OTA JSON metadata and helper scripts. |
| [overscroll.configs/](overscroll.configs/) | User-facing overscroll preset JSON files. |

## Build Targets

The main Soong targets are defined in [Android.bp](Android.bp):

| Target | Type | Purpose |
| --- | --- | --- |
| `PixelCustomPartsSystem` | `android_app` | Privileged `system_ext` app built from `common/` + `system/`, platform APIs, platform certificate. |
| `PixelCustomPartsXposed` | `android_test` | Xposed module APK built from `common/` + `xposed-pine/`, with `xposed_init` asset. |
| `PineInject` | `java_library` | Hook payload installed as `system/framework/PineInject.jar`. |
| `libpine` | prebuilt shared library | Pine native runtime dependency. |
| `aapt2_pixelparts` / `libaapt2_pixelparts` | prebuilts | Runtime/build helper binaries used by the app/tooling. |

## Clone Into an Android Source Tree

Clone the repository at the same path expected by [device.mk](device.mk):

```bash
cd $ANDROID_BUILD_TOP
git clone https://github.com/leegarchat/PixelExtraParts packages/apps/PixelExtraParts
```

The product include assumes:

```makefile
PIXEL_EXTRA_PARTS_PATH := packages/apps/PixelExtraParts
```

If you place the repository elsewhere, update your local product makefiles accordingly.

## Include in a Device Product

Add PixelExtraParts from your device or product makefile:

```makefile
$(call inherit-product, packages/apps/PixelExtraParts/device.mk)
```

[device.mk](device.mk) adds:

```makefile
PRODUCT_PACKAGES += \
	PixelCustomPartsSystem \
	init.pixelextraparts.rc \
	PineInject \
	libpine
```

It also adds artifact allow-list entries for Pine/aapt2 prebuilts and includes SELinux policy from [sepolicy/system_ext/private](sepolicy/system_ext/private/).

### Required Product Variables

`device.mk` expects the normal Android build variables plus a vendor tree path for thermal generation:

```makefile
DEVICE_CODENAME := shiba
VENDOR_PATH := vendor/google/shiba
```

`VENDOR_PATH` must point to a vendor tree that contains, or references through vendor makefiles, a `thermal_info_config.json`. The thermal generator uses it to infer copy destinations and generate profile variants.

Optional override:

```makefile
THERMAL_CUSTOM_JSON_PATH := vendor/google/shiba/proprietary/vendor/etc/thermal_info_config.json
```

## Build

From a configured Android build environment:

```bash
lunch <your_target>
m PixelCustomPartsSystem PineInject libpine
```

For the Xposed APK target:

```bash
m PixelCustomPartsXposed
```

Full ROM builds should pick up the system target automatically after including [device.mk](device.mk).

## How It Works

### System App

`PixelCustomPartsSystem` is the ROM-integrated app. It is built from [common/](common/) and [system/](system/), runs as package `org.pixel.customparts`, is platform-signed, privileged, and installed to `system_ext`. The app uses Compose/Material3 and stores feature state primarily through `Settings.Global` helpers in `SettingsKeys` and `SettingsCompat`.

The system manifest requests privileged Android permissions for settings writes, package visibility, SystemUI/launcher restart actions, telephony controls, and package management. The matching privapp allow-list lives in [privapp-permissions-pixelparts.xml](privapp-permissions-pixelparts.xml).

### Pine Runtime

`PineInject` packages the hook core, built-in hooks, and Pine manager code into `PineInject.jar`. Source-tree patches can inject this JAR into selected app processes. At runtime, [HookEntry.java](xposed-pine/src/org/pixel/customparts/manager/pine/HookEntry.java) applies built-in hooks to launcher packages and `com.android.systemui`, then loads addon hooks when matching addon metadata exists.

Built-in launcher packages currently include:

```text
com.google.android.apps.nexuslauncher
com.google.android.apps.pixel.launcher
com.android.launcher3
```

SystemUI hooks target:

```text
com.android.systemui
```

[init.pixelextraparts.rc](init.pixelextraparts.rc) creates `/data/pixelparts` directories used by addons and runtime data.

### Xposed Runtime

`PixelCustomPartsXposed` builds a module APK with package `org.pixel.customparts.xposed`. The Xposed entrypoint is declared in [xposed-pine/assets/xposed_init](xposed-pine/assets/xposed_init):

```text
org.pixel.customparts.manager.xposed.XposedInit
```

`XposedInit` applies global hooks, launcher hooks, and SystemUI hooks through the Xposed API. This target is useful for Xposed-style deployments and testing outside a fully source-integrated Pine injection flow.

### Runtime Settings Suffixes

Runtime-aware settings use suffixes such as `_pine` and `_xposed` through the project settings helpers. When adding new settings or hooks, reuse `SettingsKeys` and `SettingsCompat` instead of hardcoding duplicate `Settings.Global` keys.

## Source Patches

Some features require source-tree changes in Android framework or Settings. The checked-in snapshots live under [changebe/](changebe/) and are managed by the patch launcher in [patches/](patches/).

Common commands:

```bash
python3 packages/apps/PixelExtraParts/patches/apply_patches.py --list
python3 packages/apps/PixelExtraParts/patches/apply_patches.py --check
python3 packages/apps/PixelExtraParts/patches/apply_patches.py --apply
```

Settings resources are bypassed by default because they are ROM-specific. To include them in patch application:

```bash
python3 packages/apps/PixelExtraParts/patches/apply_patches.py --configure-bypass settings-res off
python3 packages/apps/PixelExtraParts/patches/apply_patches.py --apply
```

If an upstream file drifts too far from the snapshot, the patcher stops and prints a manual porting hint instead of guessing.

## Thermal Profiles

[ThermalConfigs/generate_thermal_configs.py](ThermalConfigs/generate_thermal_configs.py) generates profile variants from a source `thermal_info_config.json`. It adjusts configured SOC and battery thermal sensor thresholds and writes product copy rules to `ThermalConfigs/ThermalConfigCopyRules.mk`.

The generated files are intentionally ignored by git:

```text
ThermalConfigs/ThermalConfigCopyRules.mk
ThermalConfigs/configs/
```

During boot, `init.pixelextraparts.rc` sets:

```text
persist.sys.pixelparts.thermal_available=true
```

When the user selects a profile, the app updates `persist.sys.pixelparts.thermal_config`; init then forwards it to `vendor.thermal.config` and restarts `vendor.thermal-hal`.

## Addons

External hooks can be built as addon JARs. See [example.addon.hook/README.md](example.addon.hook/README.md) for the addon format, build scripts, `META-INF/addon.json`, entry class contract, and supported settings UI metadata.

Addon payloads are stored under `/data/pixelparts/addons` and loaded by the Pine manager when their scope matches a package.

## Manual Deployment Notes

[command.txt](command.txt) contains manual `adb`, mount, install, push, and reboot snippets used during development. Treat those commands as local maintenance notes; do not run them blindly on a production device.

## Development Guidelines

- Keep changes scoped to the relevant runtime: system app, Xposed APK, Pine injection, patches, thermal tooling, or addon SDK.
- Prefer existing helpers for `Settings.Global`, restart actions, package queries, and hook setup.
- Add visible UI text through resources in [common/res](common/res/).
- Do not commit generated thermal configs or local MemPalace files.
- Validate JSON/XML/Python tooling when editing metadata, resources, patcher code, or thermal scripts.
- Android builds and device deployment require a configured ROM build tree and maintainer approval.

## License

This repository contains project code and several Android/Pine/Xposed integration artifacts. Check upstream files and imported prebuilts before redistributing binaries outside your ROM workflow.