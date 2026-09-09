# PixelExtraParts: internals

**English** | [Русский](PROJECT_RU.md)

A detailed walkthrough of the codebase: how the parts connect, where things live, and how control flows. Integration is covered in [README.md](../README.md).

- [0. Architecture: how it all connects](#0-architecture-how-it-all-connects)
- [1. Settings: `Settings.Global` + the `_pine` suffix](#1-settings-settingsglobal--the-_pine-suffix)
- [2. The app (`common/`, `system/`)](#2-the-app-common-system)
- [3. Pine runtime (`pine/`)](#3-pine-runtime-pine)
- [4. Addons (`example.addon.hook/`)](#4-addons-exampleaddonhook)
- [5. Patch system (`patches/`)](#patch-system)
- [6. Thermal (`ThermalConfigs/` + runtime)](#thermal-internals)
- [7. Sepolicy (`sepolicy/` + patches)](#sepolicy-internals)
- [8. Build (`Android.bp`, `device.mk`, manifest, `init.rc`)](#build-internals)
- [9. Misc: OTA, languages, overscroll presets, `pep_update.sh`](#9-misc)

---

## 0. Architecture: how it all connects

```
┌─ App org.pixel.customparts (system_ext, platform signature)
│   writes ONLY Settings.Global: <base>_pine ────────────────┐
├─ PineInject.jar (system/framework)                         │
│   injected by the ActivityThread.handleBindApplication     │
│   patch into: systemui, nexus/pixel/launcher3 (+whitelist) │
│   ModEntry → HookEntry → hooks read the same keys ─────────┘
├─ Addon *.jar (system_ext/etc/pixelparts/addons + /data/...)
│   loaded by AddonLoader inside target processes
├─ Patches patches/files/* — without them nothing above
│   exists in the tree (injection, DTW service, thermal HAL...)
└─ Thermal: app → persist.sys.pixelparts.* → init.rc
    → vendor.thermal.config → patched pixel thermal HAL
```

The core idea: **the app never touches foreign processes directly**. It writes settings to `Settings.Global`, and the code living inside SystemUI/launcher (Pine hooks and addons) reads them from there. That's why without source patches (injection + hook points) the app builds fine but changes nothing.

---

## 1. Settings: `Settings.Global` + the `_pine` suffix

All state lives in `Settings.Global` (`common/src/org/pixel/customparts/`).

- **`SettingsKeys.kt`** (`object SettingsKeys`): some keys are bare `const val` (`pixelparts_saturation_*`, `AUTO_HBM_*` (~20), `pixelparts_dtw_*`, `APP_ICONS_*`, `ICON_SHAPE_*`, `LAUNCHER_DT2S_TIMEOUT/SLOP`, `LOG_SERVICE_*`, `THERMAL_TILE_PROFILE_QUEUE/_INDEX`); hook keys are `val ... get() = "base" + suffix` with `suffix = "_pine"` (groups `LAUNCHER_*`, `QS_*`, `AOD_*/STATUS_BAR_*`, `GESTURE_BAR_*` (~18), `SHADE_*`, `MAGNIFIER_CUSTOM_*`, `TWO_SHADE_HOOK`, `ACTIVITY_OPEN/CLOSE_TRANSITION`, `DISABLE_PREDICTIVE_BACK_ANIM`, `BATTERY_INFO_*`).
- **`utils/SettingsCompat.kt`**: `PINE_INJECT_SUFFIX = "_pine"`, `XPOSED_SUFFIX = "_xposed"`, a `SUFFIXED_KEY_BASES` whitelist (~60 bases). `key(base)` normalizes any input to the `_pine` variant; legacy `_xposed` is **only stripped on read, never written**. All put/get helpers are `@JvmStatic` over `Settings.Global`, and every write triggers `PixelPartsTileRefresher.requestForSetting()` (QS tile refresh). No custom `ContentObserver`s.
- **Hook side** (`pine/.../manager/pine/PineEnvironment.java`, `SUFFIX = "_pine"`): `resolveKey(k)` — `_pine` as is, `_xposed` → replaced, otherwise `_pine` appended. Reads also come from `Settings.Global` (bool = `getInt != 0`, float with `getInt/100f` fallback).
- Overscroll keeps its own suffix duplicate in `activities/OverscrollManager.kt` (`overscroll_*_pine`, `stripSuffix()` for portable JSON profiles).
- Exceptions: `AutoHbmController` also reads/writes `Settings.System.SCREEN_BRIGHTNESS/_MODE`; `AddonBinderReapply` supports `Secure/System/Global` via the addon's manifest `provider` field.

Developer rule: define keys only through `SettingsKeys`/`SettingsCompat`, never hardcode `Settings.Global.put*` string literals.

---

## 2. The app (`common/`, `system/`)

### Entry and dashboard

`MainActivity : ComponentActivity()`: `onCreate` → `RemoteStringsManager.initialize()` → `AppConfig.NEEDS_ROOT_ACCESS` check (false in system builds; the `RootUtils` path is unused) → `MainDashboard()` (`Scaffold` + `LargeTopAppBar` + `LazyColumn` + `RebootBubble`).

Screens are hardcoded statically (`MainMenuNavigationRow` → `startActivity(Intent(...))`), no router. Groups: `donate`, `main_header_system` (Display, AppIcons, SystemUI, Overscroll, Thermal — the latter only if `AppConfig.ENABLE_THERMALS`, Addons), `launcher_settings_title` (HiddenLauncherApps), plus dynamic addon groups `gesture/system/network/launcher/custom` via `scanAddonMainEntries()` sorted by `priority`+`title`.

Search: a `dashboardSearchItems` index (static + `flattenAddonTree()` + `flattenAddonSettingSearchEntries()` + `R.string` strings by `dt_/os_/display_/app_icons_/sysui_/launcher_/thermal_/addon_` prefixes), scoring exact=500 / contains=180 / tokens 90–25 with AND. Tapping an addon result maps to a native screen via `intentForTargetActivity()` or opens `AddonPageActivity`.

### Screens (`activities/`)

| Screen | What it configures |
|---|---|
| `DisplaySettingsActivity` | Hub: Saturation, AutoHBM, DTW, AutoLock |
| `SaturationActivity` | Screen saturation (`SATURATION_ENABLED/PERCENT`) |
| `AutoHbmActivity` | Lux threshold, timings, ramp, temperature limit |
| `DtwSettingsActivity` | Double-tap-to-wake (`DTW_*`) |
| `AutoLockSettingsActivity` | Auto-lock mode/timeout |
| `AppIconsActivity` / `IconShapeActivity` | Icon pack manager / icon shape + preview |
| `HiddenLauncherAppsActivity` | Hiding apps from the launcher |
| `SystemUISettingsActivity` | Hub: magnifier, transitions, rotation, log service |
| `MagnifierSettingsActivity` | Magnifier (`MAGNIFIER_CUSTOM_ENABLED/ZOOM/SIZE/SHAPE/OFFSET_Y`) |
| `ActivityTransitionActivity` | Open/close animations, custom APK themes (`AnimThemeCompiler/Signer`, `ApkCompiler/Installer`) |
| `RotationAnimationActivity` | Rotation styles (WindowsPhone/Win10/Cube/Zoom/Slide/Flip/Fade) |
| `OverscrollActivity` (+`OverscrollManager/Profiles/AppConfig`) | Overscroll physics, per-app rules, import/export |
| `ThermalActivity` (+`object ThermalManager`) | Legacy profile screen; `onBoot()` from the boot receiver |
| `ThermalConfigManagerActivity` (+`ThermalConfigEditorActivity`) | JSON manager/editor in `/data/pixelparts/ThermalConfigs`, per-app `map.json` |
| `AddonManagerActivity` / `AddonPageActivity` | Pine addon hosts |
| `TileHandlerActivity` | Long-press-on-QS-tile router → the right screen |
| `DonateActivity` | Donations/links |

`DoubleTapManager` and `LauncherManager` are `object` singletons (DT2S and all `LAUNCHER_*` getters/setters via `SettingsCompat.key()`).

### Services, receivers, tiles (`services/`)

- `BootCompletedReceiver` (in the `receivers` package): on boot — `ThermalManager.onBoot()`, controller `syncService()` calls (Thermal, Saturation, AutoHBM, AutoLock, Log), `AddonBootSync.sync()`, `AddonBinderReapply.reapply()`, `PixelPartsTileRefresher.requestAll()`; on `USER_PRESENT/SCREEN_ON` — tile refresh only.
- Watchers: `ThermalProfileService` (foreground, polls the top app every second → per-app profile), `AutoHbmService` (light sensor → `AutoHbmController`), `AutoLockService` (timer → `goToSleep`), `PixelPartsLogService` (logcat/dmesg/crashes).
- QS tiles: `Saturation`, `AutoHbm` + `PermanentHbm`, `AutoLock`, `ThermalManager` (cycles a queue of up to 5), `Overscroll` (based on `SettingsToggleTileService`), `PixelPartsLog`, `MainActivity`, and **40 dynamic** `DynamicAddonTile01..40` (config in `Settings.Global pixel_addon_tile_{slot}_*`, `enabled=false` in the manifest, enabled in code).

### Controllers (`utils/`, `icons/`)

- `AutoHbmController`: sysfs `/sys/class/backlight/panel0-backlight/brightness`, `DisplayManager.setBrightness`, temperature from `/sys/class/thermal/thermal_zone*/soc_therm` (battery fallback).
- `SaturationController`: `SurfaceFlinger` transaction `transact(1022, float)` (`percent/100`).
- `icons/IconPackManager`: parses `appfilter.xml` (`adw/teslacoilsw/novalauncher` themes), exports PNGs to `/data/pixelparts/IconsManager/*` + `icon_map.json`, notifies hooks via the `RELOAD_ICONS` broadcast.
- `OverscrollManager`: all physics in `Settings.Global` (`pull/stiffness/damping/fling`, scale/zoom, `packages_config`, `saved_profiles` JSONArray), network source — `api.github.com/.../overscroll.configs`.
- `AddonBootSync.sync()`: scans `*.jar` in `/system_ext/...` + `/data/...`, defaults for `pixel_addon_{id}_enabled/scope_mode/packages`, sets `pixel_extra_parts_inject_package_{pkg}=1`.
- `AddonBinderReapply.reapply()`: re-applies `binderOn` (carrier-config override for active subIds).
- Restarts (`SystemUIRestartUtils`, `RebootBubble`): SystemUI via `IStatusBarService.restartSystemUI()` with the `pixel_addon_manual_restart` flag; launcher via `forceStopPackage()` (system privilege, no root); icons without reboot via the `RELOAD_ICONS` broadcast.

---

## 3. Pine runtime (`pine/`)

Sources of `PineInject.jar` + `libpine.so` (`pine/libs/pine/`: `pine-core.jar`, `pine-xposed.jar`, `arm64-v8a/armeabi-v7a/libpine*.so*`). Build: `Android.bp` → `java_library "PineInject"` (`core/**`, `hooks/**`, `manager/pine/**` + `pine-core-jar`, `pine-xposed-jar`, kotlin-stdlib).

### Load chain

1. Patched `ActivityThread.handleBindApplication` (patch `frameworks/base/.../app/ActivityThread.java`, markers `// --- [PixelParts] INJECTION START/END`): if the package is in `PIXEL_PARTS_DEFAULT_WHITELIST` (`systemui`, `nexuslauncher`, `pixel.launcher`, `launcher3`) **or** `Settings.Global pixel_extra_parts_inject_package_<pkg> == 1` (0 = deny; synced by `AddonLoader.syncWhitelist()`), and `/system/framework/PineInject.jar` exists — `addDexPath` + `loadClass("org.pixel.customparts.pineinject.ModEntry").init()`. Isolated processes are skipped. `system_server` (`"android"`) is **never** hooked (`IGNORED_PACKAGE`).
2. `ModEntry.init()`: `PineConfig.debug=false`, `System.load("/system/lib64/libpine.so")` (fallback `/system/lib/...`), `ActivityThread.currentApplication()` → `HookEntry.init(app, cl, pkg)`.
3. `HookEntry.init()`: once-guard per package; **always** `initGlobalHooks()` — 4 hooks in every injected process (`EdgeEffectHookWrapper` with `useGlobalSettings`, `MagnifierHook`, `ActivityTransitionHook`, `PredictiveBackDisableHook`, sorted by `getPriority()` desc); launcher — log + `initLauncherHooks()` (currently a **no-op**, everything moved to the `launcher_hooks` addon); SystemUI — **no** built-in hooks, all in the `systemui_hooks` addon; other packages — addons only; finally `AddonLoader.loadAndRunAddons()` if any.
4. `AddonLoader` (867 lines): phase 1 — metadata without DEX (`<jar>.json` override, then `META-INF/addon.json`); versioning — segment-wise compare, `/data` wins ties; phase 2 — lazy `DexClassLoader` into `addons-dex` only for `getApplicableAddons(pkg)`; sort by `getPriority()` desc → `handleLoadPackage()`. Scope (`pixel_addon_<id>_scope_mode` 0=default/1=custom/2=merge), `targetPackages` (empty = wildcard `"*"`). Boot guard for SystemUI only (3 crashes in 180s → safe mode), manual-restart flag `pixel_addon_manual_restart`. Data: system addons — `/data/pixelparts/system_addons_data/<id>/`, user addons — `<jar>_data/`.
5. Interfaces (`core/`): `IHookEnvironment` (isEnabled/getInt/getFloat/getString/log), `BaseHook` (priority, setup/init, settings-read helpers), `IAddonHook` (`getId`, `getTargetPackages` — null/empty = all packages, `handleLoadPackage`).

### Hook table (`hooks/`, effective key = base + `_pine`)

| Hook (`getHookId`, prio) | Target in Android | Keys (bases) |
|---|---|---|
| `ActivityTransitionHook` (30) | `Activity.startActivity*/finish*` (override via `ActivityClient`) | `activity_open/close_transition` (def 10/0), `*_custom_package` |
| `EdgeEffectHookWrapper` (10) → static `EdgeEffectHook` | `widget.EdgeEffect` (ctors, `onPull*`, `onAbsorb`, `draw`...) | `overscroll_enabled` (def true), `overscroll_pull/stiffness/damping/fling/...`, scale/zoom/h-scale groups, `packages_config`, norm-* |
| `MagnifierHook` (0) | `widget.Magnifier[.Builder]` (`mZoom`, `setZoom`) | `magnifier_custom_enabled/zoom(1.25)/size/shape/offset_y` |
| `PredictiveBackDisableHook` (25) | `WindowOnBackInvokedDispatcher.setTopOnBackInvokedCallback` | `disable_predictive_back_anim` |
| `GestureBarHook` (66) | Launcher: `StashedHandleView*`, `Taskbar*Controller`, `RotationTouchHelper` | `gesture_bar_enabled/width_percent/height_dp/offset_*/reserved_area/gesture_area/hide_*/alpha/fade_*/tint_*` (18) |
| `GridSizeAppMenuHook` (80) | `InvariantDeviceProfile`, `AlphabeticalAppsList`, `BubbleTextView`, `PredictionRowView` | `launcher_menupage_sizer/h/row_height/icon_size/text_mode`, suggestion/search |
| `LauncherIconOverrideHook` (65) | `IconProvider*`, `BaseIconFactory`, `FloatingIconView`; `icon_map.json` | `pixelparts_app_icons_enabled/launcher_*/shape_*_tint_*`, `pixelparts_icon_shape_*` (14) |
| `UnifiedLauncherHook` (50) | `Launcher`, `Workspace`, `PageIndicatorDots`, `GridOccupancy`, grid migrations | `launcher_homepage_sizer/h/v/icon_size/text_mode`, `launcher_dock_enable`, `hotseat_*`, `padding_*`, `disable_google_feed/top_widget`, `dt2s_*` |
| `RecentsUnifiedHook` (50) | `RecentsView`, `TaskView`, `OverviewActionsView` | `launcher_recents_modify_enable/carousel_*/scale/alpha/blur_*/tint_*/offsets/disable_livetile`, `launcher_clear_all/hide_actions_row/replace_on_clear/bottom_margin` |
| `AodNotificationIconColorHook` (63) | `StatusBarIconView`, `IconManager`, `StatusBarIcon` | `aod_full_color_notification_icons`, `status_bar_use/monochrome_*`, `aod_use/monochrome_*` |
| `KeyguardBatteryPowerHook` (65) | `KeyguardIndicationController.computePowerIndication`; sysfs `current_now/voltage_now/temp` | `pixelparts_battery_info_enable/show_wattage/voltage/current/temp/percent/standard_string/custom_symbol/refresh_interval_ms/average_mode` |
| `NotificationIconShapeHook` (64) | `AppIconProviderImpl`, `BaseIconFactory`; `icon_map.json` | `pixelparts_app_icons_enabled`, `notification_stretch/remove_shape/scale`, shape tint |
| `ShadeCompactMediaHook` (67) | `MediaHost*`, `MediaViewController`, `TransitionLayout*` | `qs_compact_player/mode`, `qs_player_hide_expand/notify/lockscreen/alpha` |
| `ShadeUnifiedSurfaceHook` (65) | `BlurUtils.applyBlur`, `ScrimController`, `ScrimView` | `shade_blur/zoom_intensity`, `notif/main_scrim_alpha/tint*` |
| `SystemUIRestartHook` | `SystemUIApplication` + `RESTART_SYSTEMUI` receiver → kill+exit; systemui only | no key |
| `TwoShadeHook` | `NotificationsQuickSettingsContainer`, `QuickSettingsControllerImpl`, `NotificationStackScrollLayout` | `two_shade_hook` |

---

## 4. Addons (`example.addon.hook/`)

8 built-ins (already in `device.mk`), plus the SDK for your own.

| Directory | Entry / targets |
|---|---|
| `ambient_extend_hook` | `AmbientExtendHook` → `com.android.systemui` (ambient/battery UI) |
| `gcam_photo_torch` | `GcamPhotoTorchHook` → `com.google.android.GoogleCamera` |
| `icon_manager_settings` | settings-only (no Java): `main/icon-manager/advanced-settings` |
| `ims_carrier_config` | settings-only: `ims-carrier-config` |
| `launcher_hooks` (v2.2.1) | `LauncherHooksEntry` → nexuslauncher: 5 hooks (`LauncherIconOverride`, `GridSizeAppMenu`, `GestureBar`, `UnifiedLauncher`, `RecentsUnified`), prio 100 |
| `settings_homepage_item` | `SettingsHomepageItemHook` → `com.android.settings` |
| `settings_icon_style_override` | `SettingsIconStyleOverrideHook` → `com.android.settings` |
| `systemui_hooks` (v2.1.0) | `SystemUIHooksEntry` → systemui: 6 hooks (`KeyguardBatteryPower`, `ShadeDateCalendar`, `ShadeUnifiedSurface`, `ShadeCompactMedia`, `NotificationIconShape`, `AodNotificationIconColor`) |

Addon-only extras (not in `pine/`): `ShadeDateCalendarHook` (`shade_date_opens_calendar`), `NativeSearchRedirectView`, base `BaseLauncherHook`/`BaseSystemUIHook`. The `launcher_hooks` manifest exposes 74 keys, `systemui_hooks` — 47 (runtime keys with `_pine`).

Package format: DEX-JAR (`classes.dex` + `META-INF/addon.json`); settings-only — `META-INF/` alone, no dex. `addon.json`: `id*`, `entryClass` (omit for settings-only), `name/author/description/version` (version picks the active copy), `targetPackages[]` (empty = all), `enabled`, `settings[]` (types `switch/toggle/checkbox/int/float/string/text/select/color/file/app_list/group/visual/tile/cmd_button/...`; `provider` global/system/secure; `storage` settings/addon_file/internal/external; `enabledIf/disabledIf`, `exclusiveGroup`, `binderOn/binderOff` for carrier-config, `icon*`), `main[]` pages (`id/title/group/priority/targetActivity/targetSlot`), locales inline + `META-INF/addon_<lang>.json`, external `<jar>.json` override. Build: `./build_addon.sh <name>` (Java 11+, D8 from `prebuild/`, output `out/*.jar`). Load paths: `/system_ext/etc/pixelparts/addons` + `/data/pixelparts/addons`. Full schema — `example.addon.hook/README.md` + `docs/`.

---

<a id="patch-system"></a>
## 5. Patch system (`patches/`)

Checked-in snapshots (`patches/files/`): `modified/<rel>` (modified), `original/<rel>` (pristine HEAD), `new/<rel>` (new files), `patches/<rel>.patch` (unified diff for manual apply), `source_snapshot.{json,txt}` (provenance). `config.json` is currently empty (`version: 1, bypass_paths: [], patches: {}`) — IDs are generated dynamically (`snap-frameworks-base-...`, `new-...`).

`apply_patches.py`: `--root` (default — tree root), `--check` (dry-run, default), `--apply`, `--apply-bypassed`, `--list`, `--configure-bypass <id> on|off`, `--only <substr>` (repeatable), `--verbose`.

Engine (`patchlib.py`): **structural port first** — `difflib.SequenceMatcher.get_grouped_opcodes(5)` between `original` and `modified`: a hunk applies if the new block is already present (skip) or the old block is found **exactly once** (replace); 0/2+ matches = conflict → **unified-patch fallback `patch -p0 -N -s -l --fuzz=3`** (with a reverse `-R` dry-run first: already applied → `applied`); `.rej/.orig` junk is removed. On failure — `failed` with a `manual_hint`, no guessing. `NewFilePatch`: byte-wise compare → `applied`/`failed`/`created`. Bypass: `--apply-bypassed` > config `mode` > `bypass_paths` prefixes > `bypass_by_default`.

Targets (27 modified + 1 new):

- **`frameworks/base`** (17+1): `Activity`, **`ActivityThread`** (injection), `ApplicationPackageManager`, `LauncherActivityInfo`, `EdgeEffect`, `Magnifier`, `WindowOnBackInvokedDispatcher`, `ScreenRotationAnimation`, SettingsLib `Utils`, SystemUI `ScreenBrightnessDisplayManagerRepository` + `AodBurnInLayer`, `LightsService`, `PackageInstaller{,Service}`, `PhoneWindowManager`, `DisplayContent`, `SystemServer` (starts `DtwSystemService`); new: `DtwSystemService.java` (system-side DTW, `pixelparts_dtw_*` keys).
- **`hardware/google/pixel/thermal`** (5): `Thermal.{h,cpp}`, `thermal-helper.{h,cpp}`, `powerhal_helper.h` (`vendor.thermal.config` watcher, helper reload).
- **`system/sepolicy`** (2): `private/{domain,coredomain}.te` — `-system_app` exceptions (see §7).
- **`system/memory`** (2): `libdmabufheap/.../BufferAllocator.{h,cpp}` (out-of-line destructor for GRF prebuilts).
- **`device/lineage/sepolicy`** (1): `common/sepolicy.mk` (`akita` added to the filter).

Representative insertions: in `ActivityThread` — `PIXEL_PARTS_DEFAULT_WHITELIST` + `PIXEL_PARTS_INJECT_PREFIX` constants and the `INJECTION START/END` block before `callApplicationOnCreate` (whitelist OR `...inject_package_<pkg>==1`, `Process.isIsolated()` skipped); in `SystemServer` — `startService(DtwSystemService.class)`; in sepolicy — `-system_app` in two `neverallow`s.

Refreshing checked-in files after your own edits: `sync_from_bakfiles.py --list` / `--snapshot latest --prune` / `--snapshot <ts> --regen-patches --prune`. Short format reference — `patches/README.md`.

---

<a id="thermal-internals"></a>
## 6. Thermal

### Generator (`ThermalConfigs/generate_thermal_configs.py`)

Invoked from `device.mk` on every build (`--quiet --vendor-path $(VENDOR_PATH) --device-codename $(DEVICE_CODENAME) --init-rc ... [+ --thermal-json]`). `thermal_info_config.json` lookup: vendor copy-rule regex over `*.mk` → `rglob` (prefer rule match, then `proprietary/vendor/etc/`, `vendor/etc/`) → otherwise `PixelExtraPartsThermal=disabled` (the `init.rc` block is commented out, `thermal_available=false`).

Patches only `HotThreshold` inside objects whose `Name` is in `TARGETS_SOC` (9 `VIRTUAL-SKIN*`) / `TARGETS_BATTERY` (4 `VIRTUAL-SKIN-CHARGE*`), offsets `OFFSETS = {stock:0, soft:5, medium:9, hard:15, off:90}`. Output: `configs/thermal_info_config[_soc_<lvl>][_battery_<lvl>]_<codename>.json`, 5×5−1 = 24 names (72 files for akita/husky/shiba) + a rewritten `ThermalConfigCopyRules.mk` (`PRODUCT_COPY_FILES → $(TARGET_COPY_OUT_VENDOR)/etc/...`). Then `strip_old_thermal_block()` cuts the old block from `init.pixelextraparts.rc` and appends the new one. Generated files are git-ignored.

### Runtime

Switch chain (`utils/ThermalProfileController` + `services/ThermalProfileService`):

1. `seedVendorConfigs()`: `/vendor/etc/thermal_info_config_*.json` → `/data/pixelparts/*.json` (+ `profiles.json`).
2. `applyConfig()`: `thermal_config` = file name (stock → `thermal_info_config.json`) + `thermal_config_request` = millis serial, both via `SystemProperties` (reflection, 5 tries × 10ms).
3. `init.rc`: `on property:thermal_config_request=* && thermal_config=*` → copy to `/data/vendor/pixelparts/ThermalConfigs/active.json` (`chown/chmod/restorecon`) → `setprop vendor.thermal.config <serial>,<active.json>`; stock case — `vendor.thermal.config=thermal_info_config.json` with no copy.
4. Patched HAL (`Thermal.cpp`): `configWatcherLoop` polls `vendor.thermal.config` every 200ms, `reloadThermalHelper()`, 5s retry.
5. Per-app: `map.json` `{globalConfig, packages:{pkg:configId}}`, the service checks the top package every second → `packageConfigs[pkg] ?: globalConfig ?: __stock__`; global-only without rules — `stopSelf()`. Tile queue — `Settings.Global THERMAL_TILE_PROFILE_QUEUE` (max 5).

`init.pixelextraparts.rc` (30 lines): `post-fs-data` — `mkdir/restorecon` for `/data/pixelparts{,/ThermalConfigs,/addons,/addons-dex,/system_addons_data}`, `/data/vendor/pixelparts/ThermalConfigs`; `boot` — backlight permissions for AutoHBM; `init` — `thermal_available=true`; the two `on property` cases above.

---

<a id="sepolicy-internals"></a>
## 7. Sepolicy

Own files (included from `device.mk`: `BOARD_VENDOR_SEPOLICY_DIRS`, `SYSTEM_EXT_{PUBLIC,PRIVATE}_SEPOLICY_DIRS`):

| File | Rule |
|---|---|
| `system_ext/private/file.te` + `file_contexts` | Types `pixelparts_data_file` (`/data/pixelparts`), `pixelparts_system_file` (`/system_ext/etc/pixelparts/addons`) |
| `system_ext/private/pixelparts.te` | `system_app`: set/get `system_pixelparts_prop`, create data; init/appdomain/system_server/shell — read/create data; all appdomains — read system_file; `system_app` read `sysfs_leds/thermal/batteryinfo`, rw `sysfs_pixelparts_leds`; `system_server` rw leds |
| `system_ext/private/property.{te,_contexts}` | 5 keys `persist.sys.pixelparts.{soc,battery,thermal_config,thermal_config_request,thermal_available}` → `system_pixelparts_prop` |
| `system_ext/public/file.te` | Type `sysfs_pixelparts_leds` (sysfs_type) |
| `vendor/file{,_contexts}.te` | `pixelparts_vendor_data_file` (`/data/vendor/pixelparts`) |
| `vendor/genfs_contexts` | 3 backlight sysfs paths → `sysfs_pixelparts_leds` |
| `vendor/hal_sensors_default.te` | `hal_sensors_default` reads the leds paths |
| `vendor/pixelparts_thermal.te` | `init` creates vendor data; `hal_thermal_default` reads |

Base patches (`patches/files/...`): `system/sepolicy/private/domain.te` — `-system_app` in `neverallow {coredomain ...} sysfs_batteryinfo:file {open read}`; `coredomain.te` — `-system_app` in `neverallow sysfs_leds:file *`. These are the exceptions most often missing from a foreign base, producing `neverallow ... violated by allow system_app ...` — run the patch `--check` first, then port manually into your device tree.

---

<a id="build-internals"></a>
## 8. Build

`Android.bp`: `PixelCustomPartsSystem` (`android_app` from `common/src/**` + `system/src/**`, `common/res`, platform cert/privileged), `PineInject` (`java_library`: `core/**`, `hooks/**`, `manager/pine/**` + `pine-core-jar`, `pine-xposed-jar`, kotlin-stdlib), `libpine` (`cc_prebuilt_library_shared`, arm64 only), `aapt2_pixelparts`/`libaapt2_pixelparts` (prebuilt `common/lib/arm64/libaapt2.so` — **arm64 only**), 8 `prebuilt_etc *_addon` → `system_ext/etc/pixelparts/addons/*.jar`, `privapp_whitelist`, `init.pixelextraparts.rc`, `java_import` (`apksig-jar`, `pine-core-jar`, `pine-xposed-jar`).

Manifest (`system/AndroidManifest.xml`, `sharedUserId=android.uid.system`, `directBootAware`): ~150 `uses-permission`, key ones — `WRITE_SECURE_SETTINGS`, `DEVICE_POWER`, `STATUS_BAR(_SERVICE)`, `CONTROL_DISPLAY_BRIGHTNESS`, `FOREGROUND_SERVICE_SPECIAL_USE`, `REBOOT`, `INSTALL/DELETE_PACKAGES`, `READ_LOGS`, `DUMP`; `privapp-permissions-pixelparts.xml` — 175 `<permission>` entries (including `INTERACT_ACROSS_USERS`, `MANAGE_USERS`, `PACKAGE_USAGE_STATS`, `CHANGE_OVERLAY_PACKAGES`). `AppConfig`: `ENABLE_THERMALS` ← `persist.sys.pixelparts.thermal_available`, `IS_XPOSED=false` (Pine-only), `NEEDS_ROOT_ACCESS=false`.

---

## 9. Misc

- **OTA/**: `gen.sh <zip>` — JSON from the file name (`cut -d- -f4/-f5`: codename/version) + `size/md5/sha256/timestamp`, `download=INSERT_DOWNLOAD_LINK_HERE`; `update_ota_json.py <date|release-dir> [--devices ...] [--dry-run]` — `builds/{shiba,husky,akita}.json` summary (SourceForge + changelogs).
- **Languages** (`lang/`): `strings_{en,ru,de,fr,in,uk}.json` (~542–557 keys; en/ru complete).
- **Overscroll presets** (`overscroll.configs/`): 10 JSON files (iOS Rubber Band, Elastic Band, Snap Back, Wave Deform, Zoom Pulse, Bouncy Ball, Samsung Galaxy, Ghost Whisper, Heavy Weight, Jelly Physics).
- **`pep_update.sh install|uninstall`**: installs/removes the built APK as an update (`adb root`, userdebug); uninstall wipes the `/data/app` copy and keeps the system package.
- **`command.txt`, `test.files/`, `VideoSample/`**: dev notes, clutter, and demo videos — not part of the deliverable.
