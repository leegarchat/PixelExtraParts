# PixelExtraParts: architecture

**English** | [Русский](ARCHITECTURE_RU.md)

How the parts connect under the new layout. Integration is covered in
[README.md](../README.md); the addon SDK lives in [addons/](../addons/README.md).

## 0. How it all connects

```
┌─ App org.pixel.customparts (system_ext priv-app, platform signature)
│   writes ONLY Settings (canonical hook suffix `_lsplant`,
│   legacy `_pine`/`_xposed` read-only fallback) ──────────────┐
├─ LsplantInject.jar (system/framework)                       │
│   injected via woven ActivityThread.handleBindApplication    │
│   into systemui / launchers (+whitelist)                     │
│   ModEntry → HookEntry → hooks read the same keys ──────────┘
├─ Addon *.jar (system_ext/etc/pixelparts/addons + /data/...)
│   loaded by AddonLoader inside target processes
├─ Weave: stock trees stay vanilla; Soong compiles woven copies
│   from soong/weave_gen/ (specs in soong/weave/specs/*.patch)
└─ Thermal: app → persist.sys.pixelparts.* → init.rc
    → vendor.pixelparts.thermal.config → patched .pep HAL fork
```

Core idea: **the app never touches foreign processes directly**. It writes
settings, and the code living inside SystemUI/launcher (woven framework code
and LSPlant hooks/addons) reads them. Without the weave (injection + hook
points) the app builds fine but changes nothing.

## 1. Settings keys

State lives in Android Settings (`app/src/org/pixel/customparts/`):

- **`utils/HookKeyCompat.kt`**: `ACTIVE_SUFFIX = "_lsplant"`,
  `LEGACY_ENGINE_SUFFIX = "_pine"`, `XPOSED_SUFFIX = "_xposed"`.
- **`utils/SettingsCompat.kt`**: new writes use the `_lsplant` variant;
  legacy `_pine`/`_xposed` values are read via fallback, never written.
- **Hook side** (`lsplant_engine/.../manager/lsplant/LsplantEnvironment.java`):
  same resolution order.

Rule: define keys through `SettingsKeys`/`SettingsCompat`, never hardcode
`Settings.Global.put*` string literals.

## 2. The app (`app/`)

Single chain (former `common/` + `system/` rudiments merged here):
`AndroidManifest.xml`, `src/` (activities, services/receivers, tiles, utils,
icons), `res/`, `lib/` (incl. `arm64/libaapt2.so`), `lang/`, `proguard.flags`,
`privapp-permissions-pixelparts.xml`, `donate_page.json`.

Soong target `PixelCustomPartsSystem` (`android_app`, platform cert, privileged,
`system_ext`): `app/src/**/*.kt|java`, `app/res`, static libs
(androidx/compose/material/SettingsLib), `LsplantInject` is a separate
`java_library` (see §5).

## 3. Weave (`soong/`)

No checked-in patches against the tree, no `patches/` directory. Upstream trees
(`frameworks/base`, `hardware/google/pixel/thermal`) stay vanilla; PEP ships
diffs and applies them at build time:

1. `device.mk` runs `soong/weave/weave_apply.py --manifest soong/weave/manifest.json
   --rom <rom> --sdk <sdk>` **before** Soong parses file lists. The applicator
   reads live vanilla files, applies `soong/weave/specs/*.patch` (unified diffs),
   writes results into `soong/weave_gen/` (git-ignored, 39 manifest entries).
   The target ROM is detected from vendor make variables
   (`EVO_BUILD_TYPE`→evo, `CR_VERSION`→crdroid, `RISING_VERSION`→rising,
   `LINEAGE_VERSION`→lineage, else aosp; `PEP_TARGET_ROM=<rom>` overrides).
   Files whose context drifts per vendor tree carry variants under
   `soong/weave/specs/<rom>/<rel>.patch` (manifest `"fallbacks"`, optional);
   `--rom` tries the whole chain strictly — ROM variant, then the shared aosp
   variant, then the default (evo) spec — unknown ROMs walk `[<rom>, aosp]`.
   Any total miss (or a non-A17 tree: specs are SDK-37-anchored) fails loudly
   with the tried variants, the first missing context line, and a regen hint.
2. The Soong plugin (`soong/weave_mutator.go`, `bootstrap_go_package
   soong-pixelparts-plugin`) hooks target modules by **name** and swaps sources:
   - `framework-minus-apex` (+ `-headers`) ← `:pixelparts_weave_framework` + `:pixelparts_framework_helpers`
   - `SettingsLib` ← `:pixelparts_weave_settingslib`
   - `services.core.unboosted` ← `:pixelparts_weave_services_core` + `:pixelparts_services_helpers`
   - `services.impl` ← `:pixelparts_weave_services_impl`
3. The root `Android.bp` declares the `filegroup`s. They live at the root
   because their `srcs` span several sibling trees and Soong forbids `../`
   from subdirectories.
4. `soong/platform/` holds plain helper sources compiled **inside** the target
   modules: `framework_helpers/` (`CustomTransitionHelper`, `PixelPartsIconHelper`)
   and `services_helpers/` (`DtwSystemService`, `PixelPartsKeepScreenOn`,
   `PixelPartsCallState`, `PixelPartsHardwareInput`).

Drift behavior: a spec whose context no longer matches live vanilla fails the
build loudly (`PEP_WEAVE_ERROR`, nonzero exit) — never silently. Rebase the
spec (regenerate the diff against the new vanilla), do not force it.

### Adding a weave spec

1. Take the vanilla file and your modified copy, save the unified diff as
   `soong/weave/specs/<same-relative-path>.patch`
   (e.g. `soong/weave/specs/frameworks/base/core/java/android/app/Activity.java.patch`).
2. Append an entry to `soong/weave/manifest.json`:
   `rel` = live tree path (`frameworks/base/...`),
   `spec` = patch path, `out` = output under `soong/weave_gen/...`.
3. Add the `out` path to the matching `filegroup` in the root `Android.bp`
   (`pixelparts_weave_framework`, `pixelparts_weave_settingslib`,
   `pixelparts_weave_services_core`, `pixelparts_weave_services_impl`,
   or `pixelparts_weave_thermal`).

If the file's context differs per vendor ROM (the applicator rejects it on a
non-EvoX tree), add a re-anchored variant with identical `+` lines as
`soong/weave/specs/<rom>/<same-relative-path>.patch` and register it in the
entry's `"fallbacks": {"<rom>": "<variant path>"}` (the shared AOSP-context
variant also serves as `"aosp"` for unknown ROMs). Verify with
`weave_apply.py --rom <rom>` against that tree: expect `PEP_WEAVE_OK`.

## 4. Thermal (`thermal_hal/`)

Self-contained fork: `cc_binary android.hardware.thermal-service.pixel.pep`
with `overrides: [android.hardware.thermal-service.pixel]` and **no** `stem:`
(Soong emits edges for every in-scope module, so a shared output path is a
fatal "multiple rules" error). `device.mk` filters the stock module out of
`PRODUCT_PACKAGES`. Own `.rc`/`.xml`, own `soong_namespace`. Fork sources come
from `:pixelparts_weave_thermal` (`soong/weave_gen/`) plus an `include_dirs`
mirror so root-relative quotes resolve exactly as in
`hardware/google/pixel/thermal`.

Config generator (`thermal_hal/config/generate_thermal_configs.py`, invoked from
`device.mk`): finds `thermal_info_config.json` via `VENDOR_PATH` (or
`THERMAL_CUSTOM_JSON_PATH`), patches `HotThreshold` inside `TARGETS_SOC` /
`TARGETS_BATTERY` objects with `OFFSETS = {stock:0, soft:5, medium:9, hard:15,
off:90}`, emits SOC×battery variants into `configs/` +
`ThermalConfigCopyRules.mk`, and the init switch fragment into
`gen/init.thermal-switch.rc`. All three outputs are git-ignored; never commit them.

Runtime chain: app writes `persist.sys.pixelparts.thermal_config{,_request}` →
init fragment copies the chosen JSON to
`/data/vendor/pixelparts/ThermalConfigs/active.json` → sets the mod channel
`vendor.pixelparts.thermal.config` (`<request>,<path>`); stock-safe default
keeps `vendor.thermal.config = thermal_info_config.json` so an unpatched HAL
never sees the mod payload. The forked HAL watches the mod channel and reloads
the thermal helper.

## 5. LSPlant runtime (`lsplant_engine/`)

- `src/` — sources of `LsplantInject.jar` (`core/**` interfaces incl.
  `IAddonHook`, `manager/lsplant/**`, `lsplantinject/**` with `ModEntry`,
  `de/robv/android/xposed/**` compat shim, modern libxposed API, shaded
  commons-lang). Build: root `Android.bp` → `java_library "LsplantInject"`.
- `libs/` — prebuilts shipped to `/system/lib64` as `cc_prebuilt_library_shared`
  (`liblsplant`, `libdobby`, `libc++_shared`, `liblspbridge`).
- `lsplant-native/` — our JNI glue (`bridge/`) + master copies of engine
  headers/binaries (`third_party/`, provenance markers). Built only by the NDK
  fallback script into a staging `out/` dir; Soong consumes the `libs/` prebuilt.

Load chain: woven `ActivityThread.handleBindApplication` (whitelist or
`pixel_extra_parts_inject_package_<pkg> == 1`, isolated processes skipped,
`system_server` never hooked) → `ModEntry` (`System.loadLibrary("lspbridge")`,
binds an already-loaded foreign engine or our own `/system` prebuilts) →
`HookEntry` (global hooks + `AddonLoader`, which loads addon JARs from
`/system_ext/etc/pixelparts/addons` and `/data/pixelparts/addons`, version-aware
with `/data`-wins-ties) → addon `handleLoadPackage()`.

## 6. Sepolicy (`policy/sepolicy/`)

Included from `device.mk` via `BOARD_VENDOR_SEPOLICY_DIRS` (`vendor/`) and
`SYSTEM_EXT_{PRIVATE}_SEPOLICY_DIRS` (`system_ext/private/`). There is no
`system_ext/public/` tree in this project — the public include dir referenced
by `device.mk` resolves empty on this base. The `domain.te`/`coredomain.te`
`-system_app` exceptions live upstream as a manual base patch, not in PEP.

## 7. Build (`Android.bp`, `device.mk`, init)

- Root `Android.bp` aggregates everything: genrule `pep_init_rc`
  (`cat init.pixelextraparts.rc.in` + `:pep_thermal_switch_frag` from
  `thermal_hal/config/gen/`), `prebuilt_etc init.pixelextraparts.rc`, the app,
  `LsplantInject`, the four `cc_prebuilt_library_shared` libs, 11 addon
  `prebuilt_etc` jars, `privapp_whitelist`, `apksig-jar` import, all weave
  `filegroup`s.
- `init.pixelextraparts.rc.in` is a static template (`post-fs-data` dirs,
  backlight sysfs perms); the thermal-switch block is appended at build time by
  the genrule. The working tree is never mutated.
- Firmware is assembled only by the owner's `make.sh`. The agent does not run
  builds, `adb`, or `repo sync`.

## 8. What was dropped from the old tree (and why)

- `patches/`, `sync_tree.py`, `bakFiles`, `command.txt`, `test.files/` —
  snapshot-patcher workflow replaced by the build-time weave (§3).
- `common/` + `system/` split, `example.addon.hook/` name, `ThermalConfigs/`
  and `native/lsplant` paths — merged/renamed into `app/`, `addons/`,
  `thermal_hal/config/`, `lsplant_engine/`.
- Test branches, zigta notes, per-branch docs (`test_rework_*`, old `docs/`,
  `README_old.md`) — superseded by this document and `addons/docs/`.
- Old `Android.bp` targets (`aapt2` prebuilt wiring aside), OTA helpers
  (`OTA/`, `pep_update.sh`) — not part of this tree; firmware ships via `make.sh`.
