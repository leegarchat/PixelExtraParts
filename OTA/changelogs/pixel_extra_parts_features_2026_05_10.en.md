# PixelExtraParts: detailed list of changes and features

Russian version: [pixel_extra_parts_features_2026_05_10.md](pixel_extra_parts_features_2026_05_10.md)

## In Short.

- A full Icon Pack Manager has been added to PixelExtraParts: applying icon packs, selecting icons per app, importing a custom icon from an APK, cleanup, restore, support for removed packs, and separate shape/tint/scale settings for Launcher, SystemUI notifications, and framework-level icons.
- A runtime Pixel Launcher hook has been added for replacing icons without rebuilding the launcher: it works with the launcher's cache/factory path, dynamic Calendar/Clock icons, the floating icon shown during app launch/drag, and Pixel Launcher shape logic.
- Display settings have been added: Saturation and Auto HBM. Saturation controls color intensity through SurfaceFlinger, while Auto HBM enables high brightness mode based on the ambient light sensor with delays, ramping, cooldown, a temperature limit, and status reporting.
- Pixel Launcher settings were significantly expanded: home screen grid, dock, drawer, search/suggestion sizing, hiding search/dock/feed/top widget, native search fix, hidden apps in the drawer, and double-tap-to-sleep.
- A large Recents customization block was added: Clear All button in different modes, live tile disable, card scale, spacing, alpha, blur, tint, and icon offsets.
- SystemUI was expanded: compact QS media player, hiding the media player in specific places, player background transparency, shade blur/zoom/scrim alpha/scrim tint controls, extended charging info on the lockscreen, and notification icon shape/tint controls.
- Gesture and visual hooks were added or refined: wake on doze double tap, text magnifier customization, activity transition animations, predictive back control, and an advanced overscroll physics engine with profiles.
- Tensor thermal profiles were added: separate Battery/SoC modes for Stock, Soft, Medium, Hard, and Off, a thermal JSON generator, and device build integration.
- An IMS/CarrierConfig manager was added: VoLTE, VoWiFi, VT, VoNR, Cross-SIM, UT, 5G availability, and 5G thresholds with application to active SIMs.
- Pine/Xposed runtime infrastructure was updated: built-in hooks for Launcher/SystemUI, Pine settings override, Addon SDK/Manager, and loading external DEX JAR addons.
- Device/build integration was added: Soong targets, device.mk hooks, sepolicy, source patcher, OTA metadata/generator, and PixelExtraParts documentation.

## App Icons and Icon Pack Manager

This is one of the largest change areas. PixelExtraParts now has its own icon management system, built specifically for Pixel Launcher and system UI surfaces where Android normally uses standard adaptive/dynamic icons.

### What's new

- A new App Icons section was added to the system version of PixelExtraParts.
- A new `IconPackManager` backend handles icon pack discovery, mapping parsing, export of selected icons into `/data/pixelparts/IconsManager/`, and maintenance of `icon_map.json`.
- Support for multiple apply modes:
  - apply a whole icon pack at once;
  - partially apply a pack through the app list;
  - choose an icon from a pack for only one app;
  - import an icon from another installed APK;
  - restore the default icon for a specific app;
  - remove all icons associated with a specific icon pack;
  - fully clear all App Icons data.
- Icon sources are split inside `icon_map.json`: pack source, custom imported icon, dynamic icon, and metadata for applied packs.
- Applied icon pack cards are preserved even after the icon pack APK itself is removed.
- Removed packs can still be managed: the user sees that the pack is gone, but can still open the partial list, remove applied icons, and clean up leftovers.
- The App Icons UI now has a separate action in the floating reboot bubble for fully clearing all icons.
- Warnings and statuses were added for packs that require an update or are already missing from the system.
- Progress/status states were added for long apply, revert, and cleanup operations.

### Search and icon selection

- For each installed app, the UI now shows the currently applied icon and candidate icons from icon packs.
- Search for available icons for a specific package was improved.
- Partial apply now uses saved bindings from `icon_map.json`, so the list stays correct even when the icon pack has already been removed.
- Apps can use alternative icons from a pack, not just the default `appfilter` match.
- Logic was added to restore applied selection entries for missing packs.

### Shape, tint, and scale

App Icons are no longer limited to simple PNG replacement. A dedicated shape settings layer was added:

- Global/framework mode:
  - enable App Icons at the system level;
  - stretch icon into shape;
  - remove shape wrapper;
  - shape scale.
- Pixel Launcher mode:
  - a separate master switch for Launcher;
  - stretch/remove shape only for launcher icons;
  - scale shape wrapper;
  - separate logic for the floating icon and Pixel Launcher icon cache.
- Notification/SystemUI mode:
  - stretch/remove shape for notification app icons;
  - shape scale;
  - apply shape override to notification icons in SystemUI.
- Tint controls:
  - background tint mode: Off/Auto/Custom;
  - foreground tint mode: Off/Auto/Custom;
  - custom colors through a color picker;
  - separate logic so the color wheel/preview does not get lost against the same color background.
- Per-app shape overrides:
  - shape handling can be controlled not only globally, but also per app.

### Dynamic icons

The dynamic icon case was specifically refined:

- Calendar and Clock should no longer unconditionally fall back to the standard Pixel Launcher dynamic icon set if a PixelParts override exists for them.
- The runtime hook respects PixelParts dynamic icon metadata.
- The Launcher dynamic path is intercepted so the PixelParts icon takes priority over the stock dynamic provider where needed.
- Fixed cases where Calendar/Clock visually reverted to the default dynamic icons after reload/cache path handling.

### Runtime in Pixel Launcher

A new `LauncherIconOverrideHook` performs icon replacement inside Pixel Launcher itself:

- The `BaseIconFactory` badged icon bitmap creation path is hooked.
- The Pixel Launcher `IconProvider` path tied to dynamic icon state is hooked.
- `/data/pixelparts/IconsManager/icon_map.json` is read.
- PNG icons, adaptive wrappers, and shape scale/tint/remove/stretch modes are supported.
- The floating icon shown when opening an app from the drawer and during drag operations was fixed.
- For PixelParts, the floating icon uses a safe single-layer path so duplicate phantom icons do not appear.
- Fixed a case where Chrome and similar apps could produce two phantom icons during floating animation.
- Fixed tiny phantom icon size: visible-area and alpha normalization was moved to the correct part of the pipeline.
- When shape wrapper is disabled, the floating icon should no longer suddenly render inside a shape.

### SystemUI notification icons

A new `NotificationIconShapeHook` connects App Icons to SystemUI:

- The hook is applied to `AppIconProviderImpl.fetchAppIconBitmapInfo`.
- App Icons shape/tint settings are used for app icons in notifications.
- `APP_ICONS_NOTIFICATION_*` settings are respected.
- A reload receiver and `icon_map.json` reading are used so SystemUI can update after changes.
- Goal: notification shade/lockscreen icons should look consistent with the selected App Icons policy instead of behaving separately from Launcher.

### Latest App Icons fixes

- Fixed icon alpha/transparency: exported icons are normalized by visible bounds so transparent adaptive wrappers no longer produce icons that are too small.
- Fixed phantom icon issues during animation/open/drag.
- Fixed double-render floating icon for Chrome-like cases.
- Fixed the case where a removed icon pack disappeared from the UI while its applied icons still remained with no clear way to remove them.
- Added a full clear-all-icons button.
- Added reconstruction of missing pack cards from `icon_map.json`.
- Added protection against stale bindings when the pack APK is removed.
- Improved the pack card: missing/removed status, apply/view restrictions, while preserving cleanup actions.

## Display: Saturation and Auto HBM

A new Display hub was added to the PixelExtraParts system app. It combines saturation settings and automatic high brightness mode.

### Display hub

- New `DisplaySettingsActivity`.
- In the system build of PixelExtraParts, the Display section is available from the main screen.
- `system/AndroidManifest.xml` now includes Settings aliases/activities for integration into system categories.
- The Display section is not shown in the Xposed-only APK because this is privileged/system-app functionality.

### Saturation

A display saturation control was added:

- Master switch.
- Saturation slider in the 0-200% range.
- Preview card so the user can immediately see the expected effect.
- Applied through SurfaceFlinger transaction `1022`.
- QS tile integration was added.
- Long press on the tile opens Saturation settings.
- Minor Compose/annotation and inset issues around the display screen were fixed.

Practical point: colors can be made calmer or more saturated without external modules and without a separate utility, directly from PixelExtraParts.

### Auto HBM

Automatic High Brightness Mode was added:

- Master switch for Auto HBM.
- Status card with current state:
  - current ambient lux;
  - whether HBM is active;
  - current temperature;
  - current brightness state.
- Light threshold:
  - range 2000-60000 lux;
  - default around 20000 lux.
- Turn-on delay.
- Turn-off delay.
- Smooth ramp:
  - smooth brightness transition instead of a hard jump;
  - ramp duration 100-5000 ms;
  - default around 800 ms.
- Max active time:
  - limit for maximum HBM active duration.
- Cooldown time:
  - pause after HBM so the mode does not toggle too often.
- Check interval:
  - configurable polling frequency.
- Temperature limit:
  - range 30-80C;
  - default around 50C;
  - protection against enabling/keeping HBM under overheating.
- `AutoHbmService` reads the light sensor and controls panel/sysfs brightness nodes.
- Original brightness is saved and restored.
- The code keeps track of whether auto brightness was enabled before HBM activation.
- QS tile/status integration was added.

Practical point: outdoors the screen can automatically switch to higher brightness, but with delays, temperature protection, and cooldown so the feature does not turn into constant heating.

## Pixel Launcher

Pixel Launcher received the broadest set of runtime settings. Important point: these are not just UI toggles, but Pine/Xposed hooks that change the behavior of the real Pixel Launcher.

### Launcher settings hub

- The Launcher section was moved into a dedicated hub.
- Separate screens were added:
  - grid/dock/app drawer/search sizing;
  - search/feed settings;
  - recents settings;
  - hidden apps;
  - App Icons integration.
- For settings that require reload, the UI shows a restart action for Pixel Launcher.
- Most settings are stored in `Settings.Global` with runtime suffixes `_pine` or `_xposed`.

### Home screen, dock, and app drawer grid

Grid and sizing controls were added:

- Dock:
  - dock icon count: 1-12;
  - dock icon size: 1-200%.
- Home screen:
  - enable custom home grid;
  - column count;
  - row count;
  - icon size;
  - text mode: default, two-line, marquee, hide.
- App drawer:
  - enable custom drawer grid;
  - column count;
  - row height;
  - icon size;
  - text mode: default, two-line, marquee, hide.
- Suggestions/Search results:
  - suggestions icon size;
  - suggestions text mode;
  - disable suggestions;
  - search results icon size;
  - search results text mode.

### Search widget, dock, and paddings

A dedicated block was added for customizing the lower launcher area:

- Enable dock/search customization.
- Hide bottom search widget.
- Hide dock.
- Home page padding.
- Dock padding.
- Search padding.
- Page indicator dots Y padding.
- Page indicator dots X padding.

This makes it possible both to build a minimal home screen without a search bar/dock and to fine-tune spacing for a specific layout.

### Native search, feed, and top widget

Toggles were added for:

- Native search fix for Pixel Launcher.
- Disable Google Feed.
- Disable top widget.

The runtime hook `UnifiedLauncherHook` was refined for current Pixel Launcher/Nexus Launcher classes. Launcher hook audits and stabilization are included in this changelog.

### Hidden Launcher Apps

A dedicated screen was added for hiding apps from the drawer:

- List of installed apps.
- App search.
- System app filter.
- Checkbox rows for selecting hidden apps.
- Warning that Pixel Launcher must be restarted.
- Bound to the launcher runtime hook so the drawer does not show selected packages.

This is specifically a Pixel Launcher feature: app hiding is done on the launcher side, not by disabling APKs or using a third-party launcher.

### Double Tap To Sleep on Launcher

- A DT2S section was added for Pixel Launcher.
- The setting is stored in a runtime-aware `Settings.Global` key.
- The launcher hook handles double tap on the workspace.
- The UI warns that an active module is required in Xposed mode.

### Launcher hook stabilization

In the latest changes, launcher hooks were additionally cleaned up and stabilized:

- Nexus/Pixel Launcher hook audit.
- Improved package matching.
- Launcher lifecycle and reload/restart scenarios are handled.
- Caches were added for base sizes/offsets so changes do not stack repeatedly after reload.
- Fixed dots margin and row height base caching cases.
- Pine injection settings reading was optimized.
- Pine override is respected for the settings resolver.

## Recents

Recents customization was moved into a dedicated screen and hook `RecentsUnifiedHook`.

### General

- Master switch for enabling Recents modding.
- Disable live tile.
- Restart Pixel Launcher from the UI after changes.

### Clear All button

Flexible Clear All customization was added:

- Enable Clear All.
- Hide actions row.
- Display modes:
  - floating bottom button;
  - replace Screenshot action;
  - replace Select action.
- Bottom margin setting for floating mode.
- In the UI, the modes are shown as clear icon-based options.

This addresses a common Pixel Launcher pain point: Clear All can be brought back to a convenient place without replacing the launcher.

### Static scale and carousel geometry

- Static scale:
  - enable;
  - scale 20-120%.
- Carousel:
  - minimum card scale 0.2x-1.2x;
  - spacing -400..500 px;
  - minimum alpha 0.0-1.0.

### Blur, tint, and icon offset

- Carousel blur radius:
  - 0-300 dp;
  - available on the Android 12+ render effects path.
- Blur overflow switch.
- Tint:
  - intensity 0-100%;
  - tint color selection via color picker.
- Icon offset:
  - X offset -1500..2500 dp;
  - Y offset -1500..2500 dp.

### Runtime polish

- Render effects are enabled only when actually needed.
- Cleanup logic was added for attach listeners.
- Extra work in default mode was reduced.
- The Recents hook was adapted for current Pixel Launcher classes.

## SystemUI

The SystemUI section is now split into Lockscreen, Shade, Magnifier, and Activity Transitions. The main changes affect the media player, shade surface, charging info, and notification icon shape.

### Shade media player

QS/media player settings were added:

- Player background alpha:
  - 0-100%;
  - applied to album art/background/scrim elements of the media view.
- Compact mode:
  - Off;
  - Small;
  - Header;
  - Very small.
- Hide media player:
  - in expanded QS;
  - in the notifications area;
  - on the lockscreen.

The runtime hook `ShadeCompactMediaHook` uses multiple paths, including a constraint set fallback, so compact media works even on R8/obfuscated SystemUI builds.

### Shade surface, blur, and scrims

`ShadeUnifiedSurfaceHook` and matching UI settings were added:

- Shade blur intensity:
  - 0-400% in the UI;
  - extended input range is supported.
- Shade zoom intensity:
  - -200..400% in the UI;
  - allows reducing or increasing the zoom component of the blur transition.
- Disable scale threshold:
  - threshold below which scale can be forcibly disabled to avoid strange micro-scale animation.
- Notification scrim alpha override:
  - off/default through `-1`;
  - 0-100% when enabled.
- Main scrim alpha override:
  - off/default through `-1`;
  - 0-100% when enabled.
- Notification scrim tint:
  - separate switch;
  - color picker.
- Main scrim tint:
  - separate switch;
  - color picker.

The goal of this block is to make the shade visually unified, without splitting QS/notification layers into different alpha/tint behaviors that may show up on Android 16 QPR1+ and some ROM variants.

### Lockscreen charging info

Extended charging info on the lockscreen was added:

- Master switch.
- Refresh interval 100-5000 ms.
- Average mode.
- Option to keep the stock charging string.
- Custom symbol before metrics.
- Configurable custom symbol.
- Separate metrics:
  - wattage;
  - voltage;
  - current;
  - temperature;
  - percent.
- Reading battery sysfs paths:
  - current;
  - voltage;
  - temperature.
- Background sampler thread for averaging.
- Updating `KeyguardIndicationController.computePowerIndication` through a hook.
- Support for `_pine`/`_xposed` suffixes, including Pine override.

Practical result: on the lockscreen, the user can see real charging wattage, current, voltage, and temperature instead of only the standard "Charging rapidly".

### Notification icon shape

SystemUI notification icons are now tied to App Icons:

- The hook reads `icon_map.json`.
- It applies notification-specific shape/tint/scale settings.
- It uses a reload receiver.
- Goal: notification icons no longer have to stay in the default adaptive shape if the user configured PixelParts App Icons.

### SystemUI restart UX

- `RebootBubble` is used across SystemUI screens.
- Shade/lockscreen screens expose a restart action when a setting requires restarting SystemUI.
- The bubble supports extra actions, which is also used in App Icons for clear all.

## Gestures, input, and visual hooks

### Wake on doze double tap

- Wake on doze double tap hook was added/refined.
- The UI is available from Gestures/Input and Lockscreen.
- Configurable timeout 300-1000 ms.
- In Xposed mode, the UI checks whether the module is active if Pine override is not enabled.

### Text magnifier customization

A text loupe/magnifier settings section was added:

- Master switch.
- Zoom 0.5x-4.0x.
- Size scale 0.5x-3.0x.
- Shape:
  - default;
  - square;
  - circle.
- Vertical offset -200..200 dp.
- Live preview card with editable sample text.

### Activity transition animations

A large activity transition customization section was added:

- Separate settings for open and close transitions.
- Modes:
  - disabled;
  - no animation;
  - built-in presets;
  - custom theme package.
- Built-in animation catalog:
  - Slide: right/left/top/bottom;
  - Card Stack: right/left/top/bottom;
  - Train: right/left/top/bottom;
  - iOS Parallax: right/left/top/bottom;
  - Fade;
  - Zoom;
  - Modal: right/left/top/bottom;
  - Depth: right/left/top/bottom;
  - Pivot: right/left/top/bottom.
- Custom animation parameters:
  - translate X/Y from/to;
  - scale X/Y from/to;
  - alpha from/to;
  - rotation from/to;
  - pivot X/Y;
  - duration;
  - start offset;
  - interpolator.
- Interpolators:
  - Linear;
  - Accelerate;
  - Decelerate;
  - AccelerateDecelerate;
  - Overshoot;
  - Bounce;
  - Anticipate;
  - AnticipateOvershoot.
- A skip was added for review intents so the transition hook does not break special system/review flows.

### Predictive back control

- There is a runtime hook to disable predictive back animation.
- The setting is stored as runtime-aware `disable_predictive_back_anim`.
- Useful for users who do not like the new predictive back visual behavior or who have conflicts with custom transitions.

## Overscroll Physics

Overscroll remains one of the most advanced visual modules in PixelExtraParts. Changes in this area include fixes and integration with the Pine/Xposed runtime.

### General logic

- Master switch for the overscroll engine.
- Separate settings suffix for Pine/Xposed.
- Playground in the UI for quick visual behavior checks.
- Profiles are saved in `Settings.Global`.
- The active profile is stored separately.
- JSON export/import.
- Cross-runtime import/export: JSON stores keys without `_pine`/`_xposed`, and the suffix is added automatically when applying.
- Support for preset configs from `overscroll.configs`.
- The repository includes ready-made presets:
  - Bouncy Ball;
  - Elastic Band;
  - Ghost Whisper;
  - Heavy Weight;
  - iOS Rubber Band;
  - Jelly Physics;
  - Samsung Galaxy;
  - Snap Back;
  - Wave Deform;
  - Zoom Pulse.

### Physics sliders

- Pull coefficient.
- Stiffness.
- Damping.
- Fling.
- Resistance exponent.
- Animation speed.

These parameters control not just visual scale, but also how overscroll builds up, resists, and returns.

### Visual deformation

There are three independent visual deformation groups:

- Vertical scale.
- Zoom.
- Horizontal scale.

For each group, the following are available:

- Mode:
  - Off;
  - Shrink;
  - Grow.
- Intensity for vertical gesture.
- Intensity for horizontal gesture.
- Minimum limit.
- Anchor X/Y.
- Separate anchors for the horizontal path.

### Advanced

- Input smoothing.
- Minimum physics velocity.
- Minimum physics value.
- Lerp idle.
- Lerp run.
- Compose scale.
- Invert anchor.

### Delta normalization

A delta normalization block for Compose/intelligent smoothing was added/refined:

- Master switch for normalization.
- Detection mode:
  - behavior;
  - hybrid;
  - stacktrace.
- Reference delta.
- Detection multiplier.
- Normalization factor.
- Window.
- Ramp.

### Fixes

- Saving signed overscroll pull distance was fixed: pull direction is no longer lost where the sign matters for correct physics.
- The hook was wrapped in `EdgeEffectHookWrapper` for consistent initialization through Pine/Xposed.

## Thermal Profiles for Tensor/Pixel

Device-specific thermal integration was added. This is not a generic ROM setting, but a PixelExtraParts build/runtime block for Pixel/Tensor configs.

### UI

PixelExtraParts now includes a Thermal screen:

- Separate mode selection for Battery.
- Separate mode selection for SoC.
- Modes:
  - Stock;
  - Soft;
  - Medium;
  - Hard;
  - Off.
- Mode descriptions are separated for Battery and SoC.
- Settings are written to persistent properties:
  - `persist.sys.pixelparts.battery`;
  - `persist.sys.pixelparts.soc`;
  - `persist.sys.pixelparts.thermal_config`.

### Meaning of the modes

- Stock: standard thermal config behavior.
- Soft: softer limiting, roughly +5C to the target threshold.
- Medium: roughly +9C.
- Hard: roughly +15C.
- Off: very high threshold, effectively close to disabling the thermal throttling policy for the selected block.

### Build integration

- Generator `ThermalConfigs/generate_thermal_configs.py` was added.
- The generator takes vendor thermal config and creates JSON variants.
- Copy rules are generated for device build.
- An init rc block is generated for config selection.
- `device.mk` integrates thermal generation and the required artifacts.
- Ready-made thermal config variants were added in `ThermalConfigs/configs/`.

### Important warning

Thermal Off/Hard modes are potentially more dangerous than stock behavior: they can increase device and battery temperature. This is a power-user feature, not a recommendation for permanent use.

## IMS and network

An IMS/CarrierConfig manager was added to PixelExtraParts.

### UI toggles

Voice:

- VoLTE.
- VoWiFi.
- Video Calling / VT.
- Cross-SIM calling.

Network:

- VoNR.
- 5G availability.
- 5G thresholds.

Advanced:

- UT / supplementary services over UT.

### Runtime behavior

- Settings are stored in `Settings.Secure`.
- When a toggle changes, `ImsManager.updateImsProfile` is called.
- On boot, the config can be applied again.
- For each active SIM/subId, a `PersistableBundle` of CarrierConfig overrides is built.
- If the privileged API is available, it is applied through `CarrierConfigManager.overrideConfig`.
- If the API is unavailable and root is present, a fallback is used through `cmd phone cc set-value`/`clear-values`.
- If active subscription IDs are not obtained, the root fallback tries standard IDs `[1, 2]`.

### What exactly gets enabled

VoLTE:

- carrier volte available;
- editable enhanced 4G LTE;
- enhanced 4G LTE on by default;
- do not hide enhanced LTE toggle/icon.

VoWiFi:

- carrier WFC IMS available;
- Wi-Fi only support;
- editable WFC mode;
- editable WFC roaming mode;
- show Wi-Fi calling icon.

VT:

- carrier VT available.

Cross-SIM:

- carrier cross SIM IMS available;
- enable cross-SIM calling on opportunistic data.

VoNR:

- VoNR enabled;
- VoNR setting visibility.

5G:

- NR availabilities;
- optional SSRSRP thresholds.

UT:

- supplementary services over UT.

### Limitation

IMS toggles do not guarantee carrier support for the feature. They expose/force CarrierConfig flags on the device side, but the network and SIM still have to support the corresponding capability.

## Pine/Xposed runtime and Addons

### System app and Xposed app

PixelExtraParts is now organized as several connected runtime/build targets:

- `PixelCustomPartsSystem`:
  - privileged `system_ext` app;
  - package `org.pixel.customparts`;
  - platform APIs;
  - platform certificate;
  - main system UI/settings part.
- `PixelCustomPartsXposed`:
  - Xposed APK/test target;
  - package `org.pixel.customparts.xposed`;
  - Xposed API 82;
  - asset `xposed_init`.
- `PineInject`:
  - `java_library` for the Pine injection jar;
  - entrypoint `ModEntry` loads `libpine.so` and runs `HookEntry.init`.
- Prebuilt/runtime dependencies:
  - `libpine`;
  - Pine/Xposed compatibility jars;
  - aapt/apksig tooling.

### Hook routing

`HookEntry` and `XposedInit` were updated for the current built-in hook list:

Launcher side:

- `LauncherIconOverrideHook`.
- `GridSizeAppMenuHook`.
- `UnifiedLauncherHook`.
- `RecentsUnifiedHook`.

SystemUI side:

- Doze/double tap hook.
- Battery power/charging info hook.
- Shade surface hook.
- Compact media hook.
- Notification icon shape hook.
- SystemUI restart/reload helpers.

Global/Xposed side:

- Overscroll edge effect hook.
- Magnifier hook.
- Activity transition hook.
- Predictive back disable hook.

### Addon Manager and SDK

The external addon system was added/expanded:

- Addons are loaded as DEX JARs from `/data/pixelparts/addons/`.
- Each addon contains `META-INF/addon.json`.
- The entry class implements `org.pixel.customparts.core.IAddonHook`.
- An addon can specify:
  - id;
  - entryClass;
  - name;
  - author;
  - description;
  - version;
  - targetPackages;
  - enabled by default;
  - priority;
  - icon/background/card visual metadata;
  - settings schema for auto-generated UI.
- Target package scope is supported: an addon can run only in the packages it targets.
- Auto-generated settings UI based on the `settings` array in the manifest is supported.
- Supported setting types: int, float, string, select, file, toggle/switch/checkbox.
- `example.addon.hook/` now contains build scripts and a prebuilt SDK so simple addons can be built without a full Android Studio project.

Practical point: PixelExtraParts is becoming not just a set of built-in features, but also a runtime platform for additional hooks.

## Build, device integration, and OTA tooling

### Soong/device integration

The build layer was updated with:

- `Android.bp` targets for the system app, Xposed APK, PineInject, and prebuilts.
- `device.mk` integration:
  - system app;
  - PineInject;
  - libpine;
  - thermal configs;
  - sepolicy;
  - related runtime artifacts.
- `system/AndroidManifest.xml`:
  - privileged permissions;
  - shared UID `android.uid.system`;
  - exported/settings aliases;
  - registration of new activities.
- `privapp-permissions-pixelparts.xml` and sepolicy for PixelExtraParts system capabilities.

### Source patcher

Patch infrastructure was added/updated:

- `patches/apply_patches.py`.
- `patches/config.json`.
- `patches/patchlib.py`.
- Subpatches for framework/settings/launcher-related changes.
- `changebe/` stores reference/modified source snapshots.
- A source patch launcher was added.

This is needed for the parts where a runtime hook is not enough or where source-side integration is easier to maintain.

## UI/UX polish

### Overall UI style

- The PixelExtraParts screen became more dashboard-like, while still remaining a system settings UI.
- The main screen is grouped by purpose:
  - Donate;
  - Gestures & Input;
  - System;
  - Network/IMS;
  - Test Things hidden section.
- Jetpack Compose + Material3 + dynamic color are used.
- Screens use edge-to-edge and a blur overlay for the top bar.
- `RebootBubble` became the common mechanism for quick restart/actions.
- For Xposed-only runtime, warnings are shown when the module is not active.

### Latest small fixes in this range

- Hidden launcher apps UI was refined after the initial addition.
- Display settings received inset/import fixes.
- Long press on the Saturation tile opens settings.
- Auto HBM received expanded status and additional controls.
- App Icons received a series of minor fixes after adding the icon manager.
- Overscroll preserved signed pull distance.
- Transition hooks skip review intents.
- Pine settings lookup became faster.
- Settings resolver respects Pine override.
- PixelExtraParts documentation was updated for the current architecture.

## What users should know

- Some features require restarting Pixel Launcher or SystemUI. The UI shows a restart action where needed.
- App Icons store generated data in `/data/pixelparts/IconsManager/`; clearing from the UI removes map/metadata/generated icons.
- Auto HBM and Thermal Profiles work with low-level brightness/thermal mechanisms. These are power-user features.
- IMS toggles depend on the carrier, SIM, and network. PixelExtraParts can set CarrierConfig flags, but cannot force the carrier to support an unavailable service.
- Pine/Xposed features depend on the active runtime. In the system build, the main path is designed for Pine injection; in the Xposed APK there is self-check and runtime-specific suffix handling.
- Addons are a powerful hook mechanism. Untrusted addons can break a target app or SystemUI, so they should only be installed from trusted sources.

## What does not belong to this changelog

- General Evolution X source tree changes.
- Generic Android security patch notes.
- Kernel/vendor changes, if they are not directly related to PixelExtraParts thermal/build integration.
- General GApps/Launcher upstream changes without PixelExtraParts-specific hook/UI work.
- Test OTA commits that only changed metadata/placeholders and did not add user-facing PixelExtraParts features.