# PixelExtraParts (PEP)

**English** | [Русский](README_RU.md)

Privileged settings app + runtime hooks + thermal profiles for an Evolution X
firmware tree. Lives at `packages/apps/PixelExtraParts`.

> Internals (architecture, weave pipeline, thermal, sepolicy): [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).
> Addon SDK: [addons/README.md](addons/README.md).

## Layout

| Path | What it is |
|---|---|
| `app/` | The app itself, single chain: `AndroidManifest.xml`, `src/`, `res/`, `lib/`, `lang/`, `proguard.flags`, `privapp-permissions-pixelparts.xml`, `donate_page.json` |
| `addons/` | Addons: `release/` (11 prod addons, `out/*.jar` checked in), `tests/` (experimental), `prebuild/` (shared build deps: `android.jar`, `d8`, `IAddonHook.java`, LSPlant/Xposed jars); built with `addons/build.sh <name>` |
| `soong/` | H1-weave pipeline: `weave_mutator.go` + `Android.bp` (plugin `soong-pixelparts-plugin`, swaps stock sources for woven ones by module name); `weave/` (specs `specs/*.patch` + `manifest.json` + strict applicator `weave_apply.py`); `weave_gen/` (generated, git-ignored); `platform/` (helpers compiled into framework/services: `framework_helpers/`, `services_helpers/`) |
| `thermal_hal/` | Self-contained HAL fork `android.hardware.thermal-service.pixel.pep` (`overrides:`, no `stem:`), own `.rc`/`.xml`; sources come from `:pixelparts_weave_thermal` (`soong/weave_gen/`) + `include_dirs` mirror. `config/` holds the thermal generator + generated `configs/`, `ThermalConfigCopyRules.mk`, `gen/` fragment |
| `lsplant_engine/` | Injector runtime: `src/` (`LsplantInject` java library), `libs/` (prebuilt `liblsplant`/`libdobby`/`libc++_shared`/`liblspbridge` `.so`), `lsplant-native/` (`bridge/` JNI glue, NDK fallback script builds the prebuilt, Soong consumes only the prebuilt) |
| `policy/sepolicy/` | All PEP sepolicy: `system_ext/private/`, `vendor/` |
| `init.pixelextraparts.rc.in` | Static init template. The real `init.pixelextraparts.rc` is assembled by genrule `pep_init_rc` (`cat` template + generated thermal-switch fragment from `thermal_hal/config/gen/`); the tree is never mutated |
| `Android.bp` / `device.mk` | Build aggregators (see below) |
| `STRUCTURE.md` | Path/module-level map of the tree |

`app_test/` is a parked Compose experiment (git-ignored, not built).

## Firmware integration

In the device/product makefile, **before** `inherit-product`:

```makefile
DEVICE_CODENAME := shiba
VENDOR_PATH := vendor/google/shiba
# optional, if thermal_info_config.json is not auto-detected:
# THERMAL_CUSTOM_JSON_PATH := vendor/.../thermal_info_config.json

$(call inherit-product, packages/apps/PixelExtraParts/device.mk)
```

| Variable | Required | Purpose |
|---|---|---|
| `DEVICE_CODENAME` | Yes | Suffix for generated thermal config names |
| `VENDOR_PATH` | Yes | Locates `thermal_info_config.json` and copy rules; empty value is a build error |
| `THERMAL_CUSTOM_JSON_PATH` | No | Explicit path to `thermal_info_config.json` if autodetection fails |

What `device.mk` does on its own:

- adds `PRODUCT_PACKAGES`: `PixelCustomPartsSystem` app, `init.pixelextraparts.rc`, fork HAL `.pep`, 11 addon jars, `LsplantInject`, `liblsplant`/`libdobby`/`libc++_shared`/`liblspbridge`;
- adds `PRODUCT_SOONG_NAMESPACES` (root + `thermal_hal`, which has its own `soong_namespace`);
- regenerates `soong/weave_gen/` from live vanilla + `soong/weave/specs/` **before** Soong parses file lists; spec drift fails the build loudly (`PEP_WEAVE_ERROR`), never silently;
- runs the thermal config generator and includes `ThermalConfigCopyRules.mk`;
- includes sepolicy dirs (`vendor`, `system_ext/private`), and filters out the stock `android.hardware.thermal-service.pixel` (the `.pep` fork replaces it via `overrides:`).

Firmware is assembled only by the owner's `make.sh`. The agent does not run builds, `adb`, or `repo sync`.

## Addon build

```bash
cd packages/apps/PixelExtraParts/addons
./build.sh <name>              # looks up <name> in release/, then tests/
./build.sh my_hook ./path/to/project   # explicit project dir
```

Output: `<project>/out/<name>.jar`. Java 11+ required; `prebuild/` jars are used
first, local SDK as fallback (`ANDROID_JAR`/`D8_JAR` overrides supported).
Settings-only addons (no `src/`, no `entryClass`) package `META-INF/` without DEX.

## Adding a weave spec (short version)

1. Diff vanilla vs modified file, save as `soong/weave/specs/<same-rel-path>.patch`.
2. Append one entry to `soong/weave/manifest.json`: `rel` (live tree path),
   `spec` (patch path), `out` (path under `soong/weave_gen/`).
3. Add the `out` path to the matching `filegroup` in the root `Android.bp`
   (`pixelparts_weave_framework` / `_settingslib` / `_services_core` / `_services_impl` / `_thermal`).

Details: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md). Drift of the base
against a spec context fails the build — rebase the spec, do not force it.

## License

Project code plus several Android/LSPlant integration artifacts — check upstream
files and imported prebuilts before redistributing binaries outside your ROM workflow.
