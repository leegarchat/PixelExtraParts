# PixelExtraParts: внутреннее устройство

**Русский** | [English](PROJECT.md)

Подробный разбор кодовой базы: как части проекта связаны, где что лежит и как течёт управление. Подключение — в [README_RU.md](../README_RU.md).

- [0. Архитектура: как всё связано](#0-архитектура-как-всё-связано)
- [1. Настройки: `Settings.Global` + суффикс `_pine`](#1-настройки-settingsglobal--суффикс-_pine)
- [2. Приложение (`common/`, `system/`)](#2-приложение-common-system)
- [3. Pine runtime (`pine/`)](#3-pine-runtime-pine)
- [4. Аддоны (`example.addon.hook/`)](#4-аддоны-exampleaddonhook)
- [5. Патч-система (`patches/`)](#patch-system)
- [6. Thermal (`ThermalConfigs/` + runtime)](#thermal-internals)
- [7. Sepolicy (`sepolicy/` + патчи)](#sepolicy-internals)
- [8. Сборка (`Android.bp`, `device.mk`, манифест, `init.rc`)](#build-internals)
- [9. Прочее: OTA, языки, overscroll-пресеты, `pep_update.sh`](#9-прочее)

---

## 0. Архитектура: как всё связано

```
┌─ Приложение org.pixel.customparts (system_ext, platform-подпись)
│   пишет ТОЛЬКО Settings.Global: <база>_pine ──────────────┐
├─ PineInject.jar (system/framework)                         │
│   инжектится патчем ActivityThread.handleBindApplication   │
│   в процессы: systemui, nexus/pixel/launcher3 (+whitelist) │
│   ModEntry → HookEntry → хуки читают те же ключи ──────────┘
├─ Аддоны *.jar (system_ext/etc/pixelparts/addons + /data/...)
│   грузятся AddonLoader'ом внутри целевых процессов
├─ Патчи patches/files/* — то, без чего вышеперечисленное
│   не существует в дереве (инжекция, DTW-сервис, thermal-HAL...)
└─ Thermal: приложение → persist.sys.pixelparts.* → init.rc
    → vendor.thermal.config → пропатченный pixel thermal-HAL
```

Ключевая идея: **приложение никогда не трогает чужие процессы напрямую**. Оно пишет настройки в `Settings.Global`, а код, живущий внутри SystemUI/лаунчера (Pine-хуки и аддоны), читает их оттуда. Поэтому без патчей исходников (инжекция + точки хуков) приложение собирается, но ничего не меняет.

---

## 1. Настройки: `Settings.Global` + суффикс `_pine`

Всё состояние — в `Settings.Global` (`common/src/org/pixel/customparts/`).

- **`SettingsKeys.kt`** (`object SettingsKeys`): часть ключей — голые `const val` (`pixelparts_saturation_*`, `AUTO_HBM_*` (~20 шт.), `pixelparts_dtw_*`, `APP_ICONS_*`, `ICON_SHAPE_*`, `LAUNCHER_DT2S_TIMEOUT/SLOP`, `LOG_SERVICE_*`, `THERMAL_TILE_PROFILE_QUEUE/_INDEX`); хуковые ключи — `val ... get() = "база" + suffix`, где `suffix = "_pine"` (группы `LAUNCHER_*`, `QS_*`, `AOD_*/STATUS_BAR_*`, `GESTURE_BAR_*` (~18), `SHADE_*`, `MAGNIFIER_CUSTOM_*`, `TWO_SHADE_HOOK`, `ACTIVITY_OPEN/CLOSE_TRANSITION`, `DISABLE_PREDICTIVE_BACK_ANIM`, `BATTERY_INFO_*`).
- **`utils/SettingsCompat.kt`**: `PINE_INJECT_SUFFIX = "_pine"`, `XPOSED_SUFFIX = "_xposed"`, whitelist `SUFFIXED_KEY_BASES` (~60 баз). `key(base)` нормализует любой вход к `_pine`-варианту; legacy `_xposed` **только срезается при чтении, никогда не пишется**. Все put/get — `@JvmStatic` через `Settings.Global`, на каждую запись — `PixelPartsTileRefresher.requestForSetting()` (обновление QS-тайлов). Своих `ContentObserver` нет.
- **Сторона хуков** (`pine/.../manager/pine/PineEnvironment.java`, `SUFFIX = "_pine"`): `resolveKey(k)` — `_pine` как есть, `_xposed` → заменить, иначе дописать `_pine`. Чтение тоже из `Settings.Global` (bool = `getInt != 0`, float с fallback `getInt/100f`).
- У overscroll свой дубль суффикса в `activities/OverscrollManager.kt` (`overscroll_*_pine`, `stripSuffix()` для переносимых JSON-профилей).
- Исключения: `AutoHbmController` также читает/пишет `Settings.System.SCREEN_BRIGHTNESS/_MODE`; `AddonBinderReapply` умеет `Secure/System/Global` по полю `provider` из addon-манифеста.

Новое правило для разработчиков: ключи — только через `SettingsKeys`/`SettingsCompat`, никакого хардкода `Settings.Global.put*` по строкам.

---

## 2. Приложение (`common/`, `system/`)

### Вход и дашборд

`MainActivity : ComponentActivity()`: `onCreate` → `RemoteStringsManager.initialize()` → проверка `AppConfig.NEEDS_ROOT_ACCESS` (в system-сборке `false`, root-путь через `RootUtils` не используется) → `MainDashboard()` (`Scaffold` + `LargeTopAppBar` + `LazyColumn` + `RebootBubble`).

Экраны зашиты статикой (`MainMenuNavigationRow` → `startActivity(Intent(...))`), роутера нет. Группы: `donate`, `main_header_system` (Display, AppIcons, SystemUI, Overscroll, Thermal — последний только если `AppConfig.ENABLE_THERMALS`, Addons), `launcher_settings_title` (HiddenLauncherApps), плюс динамические группы аддонов `gesture/system/network/launcher/custom` через `scanAddonMainEntries()` с сортировкой по `priority`+`title`.

Поиск: индекс `dashboardSearchItems` (статика + `flattenAddonTree()` + `flattenAddonSettingSearchEntries()` + строки `R.string` по префиксам `dt_/os_/display_/app_icons_/sysui_/launcher_/thermal_/addon_`), скоринг exact=500 / contains=180 / токены 90-25 с AND. Клик по результату аддона маппится на нативный экран через `intentForTargetActivity()` или открывает `AddonPageActivity`.

### Экраны (`activities/`)

| Экран | Что настраивает |
|---|---|
| `DisplaySettingsActivity` | Хаб: Saturation, AutoHBM, DTW, AutoLock |
| `SaturationActivity` | Насыщенность (`SATURATION_ENABLED/PERCENT`) |
| `AutoHbmActivity` | Порог люкс, тайминги, рампа, лимит температуры |
| `DtwSettingsActivity` | Double-tap-to-wake (`DTW_*`) |
| `AutoLockSettingsActivity` | Режим/таймаут автоблокировки |
| `AppIconsActivity` / `IconShapeActivity` | Менеджер иконпаков / форма иконок + превью |
| `HiddenLauncherAppsActivity` | Скрытие приложений из лаунчера |
| `SystemUISettingsActivity` | Хаб: лупа, переходы, ротация, лог-сервис |
| `MagnifierSettingsActivity` | Лупа (`MAGNIFIER_CUSTOM_ENABLED/ZOOM/SIZE/SHAPE/OFFSET_Y`) |
| `ActivityTransitionActivity` | Анимации open/close, кастомные APK-темы (`AnimThemeCompiler/Signer`, `ApkCompiler/Installer`) |
| `RotationAnimationActivity` | Стили ротации (WindowsPhone/Win10/Cube/Zoom/Slide/Flip/Fade) |
| `OverscrollActivity` (+`OverscrollManager/Profiles/AppConfig`) | Физика overscroll, per-app правила, импорт/экспорт |
| `ThermalActivity` (+`object ThermalManager`) | Legacy-экран профилей; `onBoot()` из boot-ресивера |
| `ThermalConfigManagerActivity` (+`ThermalConfigEditorActivity`) | Менеджер/редактор JSON в `/data/pixelparts/ThermalConfigs`, per-app `map.json` |
| `AddonManagerActivity` / `AddonPageActivity` | Хосты Pine-аддонов |
| `TileHandlerActivity` | Роутер долгого тапа по QS-тайлу → нужный экран |
| `DonateActivity` | Донаты/ссылки |

`DoubleTapManager` и `LauncherManager` — `object`-синглтоны (DT2S и все `LAUNCHER_*` геттеры/сеттеры через `SettingsCompat.key()`).

### Сервисы, ресиверы, тайлы (`services/`)

- `BootCompletedReceiver` (в пакете `receivers`): на boot — `ThermalManager.onBoot()`, `syncService()` контроллеров (Thermal, Saturation, AutoHBM, AutoLock, Log), `AddonBootSync.sync()`, `AddonBinderReapply.reapply()`, `PixelPartsTileRefresher.requestAll()`; на `USER_PRESENT/SCREEN_ON` — только refresh тайлов.
- Наблюдатели: `ThermalProfileService` (foreground, опрос top-app каждую секунду → per-app профиль), `AutoHbmService` (датчик света → `AutoHbmController`), `AutoLockService` (таймер → `goToSleep`), `PixelPartsLogService` (logcat/dmesg/краши).
- QS-тайлы: `Saturation`, `AutoHbm` + `PermanentHbm`, `AutoLock`, `ThermalManager` (циклит очередь до 5), `Overscroll` (база `SettingsToggleTileService`), `PixelPartsLog`, `MainActivity` и **40 динамических** `DynamicAddonTile01..40` (конфиг `Settings.Global pixel_addon_tile_{slot}_*`, в манифесте `enabled=false`, включаются кодом).

### Контроллеры (`utils/`, `icons/`)

- `AutoHbmController`: sysfs `/sys/class/backlight/panel0-backlight/brightness`, `DisplayManager.setBrightness`, температура из `/sys/class/thermal/thermal_zone*/soc_therm` (fallback — батарея).
- `SaturationController`: транзакция `SurfaceFlinger` `transact(1022, float)` (`percent/100`).
- `icons/IconPackManager`: парсинг `appfilter.xml` (`adw/teslacoilsw/novalauncher` темы), экспорт PNG в `/data/pixelparts/IconsManager/*` + `icon_map.json`, оповещение хуков бродкастом `RELOAD_ICONS`.
- `OverscrollManager`: вся физика в `Settings.Global` (`pull/stiffness/damping/fling`, scale/zoom, `packages_config`, `saved_profiles` JSONArray), сеть — `api.github.com/.../overscroll.configs`.
- `AddonBootSync.sync()`: скан `*.jar` в `/system_ext/...` + `/data/...`, дефолты `pixel_addon_{id}_enabled/scope_mode/packages`, выставляет `pixel_extra_parts_inject_package_{pkg}=1`.
- `AddonBinderReapply.reapply()`: повторное применение `binderOn` (carrier-config override по активным subId).
- Перезапуски (`SystemUIRestartUtils`, `RebootBubble`): SystemUI — через `IStatusBarService.restartSystemUI()` с флагом `pixel_addon_manual_restart`; лаунчер — `forceStopPackage()` (system-привилегия, без root); иконки без ребута — бродкаст `RELOAD_ICONS`.

---

## 3. Pine runtime (`pine/`)

Исходники `PineInject.jar` + `libpine.so` (`pine/libs/pine/`: `pine-core.jar`, `pine-xposed.jar`, `arm64-v8a/armeabi-v7a/libpine*.so*`). Сборка: `Android.bp` → `java_library "PineInject"` (`core/**`, `hooks/**`, `manager/pine/**` + `pine-core-jar`, `pine-xposed-jar`).

### Цепочка загрузки

1. Пропатченный `ActivityThread.handleBindApplication` (патч `frameworks/base/.../app/ActivityThread.java`, маркеры `// --- [PixelParts] INJECTION START/END`): если пакет в `PIXEL_PARTS_DEFAULT_WHITELIST` (`systemui`, `nexuslauncher`, `pixel.launcher`, `launcher3`) **или** `Settings.Global pixel_extra_parts_inject_package_<pkg> == 1` (0 — запрет; sync делает `AddonLoader.syncWhitelist()`), и существует `/system/framework/PineInject.jar` — `addDexPath` + `loadClass("org.pixel.customparts.pineinject.ModEntry").init()`. Изолированные процессы пропускаются. `system_server` (`"android"`) **никогда** не хукается (`IGNORED_PACKAGE`).
2. `ModEntry.init()`: `PineConfig.debug=false`, `System.load("/system/lib64/libpine.so")` (fallback `/system/lib/...`), `ActivityThread.currentApplication()` → `HookEntry.init(app, cl, pkg)`.
3. `HookEntry.init()`: once-guard на пакет; **всегда** `initGlobalHooks()` — 4 хука во всех инжектированных процессах (`EdgeEffectHookWrapper` с `useGlobalSettings`, `MagnifierHook`, `ActivityTransitionHook`, `PredictiveBackDisableHook`, сортировка по `getPriority()` desc); лаунчер — лог + `initLauncherHooks()` (сейчас **no-op**, всё отдано аддону `launcher_hooks`); SystemUI — встроенных хуков **нет**, всё в аддоне `systemui_hooks`; прочие пакеты — только аддоны; финал — `AddonLoader.loadAndRunAddons()` при наличии.
4. `AddonLoader` (867 строк): фаза 1 — метаданные без DEX (`<jar>.json`-override, затем `META-INF/addon.json`); версия — сравнение по сегментам, при равенстве побеждает `/data`; фаза 2 — lazy `DexClassLoader` в `addons-dex` только для `getApplicableAddons(pkg)`; сортировка по `getPriority()` desc → `handleLoadPackage()`. Scope (`pixel_addon_<id>_scope_mode` 0=default/1=custom/2=merge), `targetPackages` (пусто = wildcard `"*"`). Boot-guard только для SystemUI (3 падения за 180с → safe mode), флаг ручного рестарта `pixel_addon_manual_restart`. Данные: системные — `/data/pixelparts/system_addons_data/<id>/`, юзерские — `<jar>_data/`.
5. Интерфейсы (`core/`): `IHookEnvironment` (isEnabled/getInt/getFloat/getString/log), `BaseHook` (приоритет, setup/init, хелперы чтения настроек), `IAddonHook` (`getId`, `getTargetPackages` — null/empty = все пакеты, `handleLoadPackage`).

### Таблица хуков (`hooks/`, факт. ключ = база + `_pine`)

| Хук (`getHookId`, prio) | Цель в Android | Ключи (базы) |
|---|---|---|
| `ActivityTransitionHook` (30) | `Activity.startActivity*/finish*` (override через `ActivityClient`) | `activity_open/close_transition` (def 10/0), `*_custom_package` |
| `EdgeEffectHookWrapper` (10) → static `EdgeEffectHook` | `widget.EdgeEffect` (ctor, `onPull*`, `onAbsorb`, `draw`...) | `overscroll_enabled` (def true), `overscroll_pull/stiffness/damping/fling/...`, scale/zoom/h-scale группы, `packages_config`, norm-* |
| `MagnifierHook` (0) | `widget.Magnifier[.Builder]` (`mZoom`, `setZoom`) | `magnifier_custom_enabled/zoom(1.25)/size/shape/offset_y` |
| `PredictiveBackDisableHook` (25) | `WindowOnBackInvokedDispatcher.setTopOnBackInvokedCallback` | `disable_predictive_back_anim` |
| `GestureBarHook` (66) | Лаунчер: `StashedHandleView*`, `Taskbar*Controller`, `RotationTouchHelper` | `gesture_bar_enabled/width_percent/height_dp/offset_*/reserved_area/gesture_area/hide_*/alpha/fade_*/tint_*` (18) |
| `GridSizeAppMenuHook` (80) | `InvariantDeviceProfile`, `AlphabeticalAppsList`, `BubbleTextView`, `PredictionRowView` | `launcher_menupage_sizer/h/row_height/icon_size/text_mode`, suggestion/search |
| `LauncherIconOverrideHook` (65) | `IconProvider*`, `BaseIconFactory`, `FloatingIconView`; `icon_map.json` | `pixelparts_app_icons_enabled/launcher_*/shape_*_tint_*`, `pixelparts_icon_shape_*` (14) |
| `UnifiedLauncherHook` (50) | `Launcher`, `Workspace`, `PageIndicatorDots`, `GridOccupancy`, миграции сетки | `launcher_homepage_sizer/h/v/icon_size/text_mode`, `launcher_dock_enable`, `hotseat_*`, `padding_*`, `disable_google_feed/top_widget`, `dt2s_*` |
| `RecentsUnifiedHook` (50) | `RecentsView`, `TaskView`, `OverviewActionsView` | `launcher_recents_modify_enable/carousel_*/scale/alpha/blur_*/tint_*/offsets/disable_livetile`, `launcher_clear_all/hide_actions_row/replace_on_clear/bottom_margin` |
| `AodNotificationIconColorHook` (63) | `StatusBarIconView`, `IconManager`, `StatusBarIcon` | `aod_full_color_notification_icons`, `status_bar_use/monochrome_*`, `aod_use/monochrome_*` |
| `KeyguardBatteryPowerHook` (65) | `KeyguardIndicationController.computePowerIndication`; sysfs `current_now/voltage_now/temp` | `pixelparts_battery_info_enable/show_wattage/voltage/current/temp/percent/standard_string/custom_symbol/refresh_interval_ms/average_mode` |
| `NotificationIconShapeHook` (64) | `AppIconProviderImpl`, `BaseIconFactory`; `icon_map.json` | `pixelparts_app_icons_enabled`, `notification_stretch/remove_shape/scale`, shape tint |
| `ShadeCompactMediaHook` (67) | `MediaHost*`, `MediaViewController`, `TransitionLayout*` | `qs_compact_player/mode`, `qs_player_hide_expand/notify/lockscreen/alpha` |
| `ShadeUnifiedSurfaceHook` (65) | `BlurUtils.applyBlur`, `ScrimController`, `ScrimView` | `shade_blur/zoom_intensity`, `notif/main_scrim_alpha/tint*` |
| `SystemUIRestartHook` | `SystemUIApplication` + ресивер `RESTART_SYSTEMUI` → kill+exit; только systemui | без ключа |
| `TwoShadeHook` | `NotificationsQuickSettingsContainer`, `QuickSettingsControllerImpl`, `NotificationStackScrollLayout` | `two_shade_hook` |

---

## 4. Аддоны (`example.addon.hook/`)

8 встроенных (уже в `device.mk`), плюс SDK для своих.

| Каталог | Entry / цели |
|---|---|
| `ambient_extend_hook` | `AmbientExtendHook` → `com.android.systemui` (ambient/battery UI) |
| `gcam_photo_torch` | `GcamPhotoTorchHook` → `com.google.android.GoogleCamera` |
| `icon_manager_settings` | settings-only (без Java): `main/icon-manager/advanced-settings` |
| `ims_carrier_config` | settings-only: `ims-carrier-config` |
| `launcher_hooks` (v2.2.1) | `LauncherHooksEntry` → nexuslauncher: 5 хуков (`LauncherIconOverride`, `GridSizeAppMenu`, `GestureBar`, `UnifiedLauncher`, `RecentsUnified`), prio 100 |
| `settings_homepage_item` | `SettingsHomepageItemHook` → `com.android.settings` |
| `settings_icon_style_override` | `SettingsIconStyleOverrideHook` → `com.android.settings` |
| `systemui_hooks` (v2.1.0) | `SystemUIHooksEntry` → systemui: 6 хуков (`KeyguardBatteryPower`, `ShadeDateCalendar`, `ShadeUnifiedSurface`, `ShadeCompactMedia`, `NotificationIconShape`, `AodNotificationIconColor`) |

Эксклюзивы аддонов (нет в `pine/`): `ShadeDateCalendarHook` (`shade_date_opens_calendar`), `NativeSearchRedirectView`, базовые `BaseLauncherHook`/`BaseSystemUIHook`. Манифест `launcher_hooks` экспонирует 74 ключа, `systemui_hooks` — 47 (runtime-ключи с `_pine`).

Формат пакета: DEX-JAR (`classes.dex` + `META-INF/addon.json`); settings-only — только `META-INF/` без dex. `addon.json`: `id*`, `entryClass` (опустить для settings-only), `name/author/description/version` (версия выбирает активную копию), `targetPackages[]` (пусто = всем), `enabled`, `settings[]` (типы `switch/toggle/checkbox/int/float/string/text/select/color/file/app_list/group/visual/tile/cmd_button/...`; `provider` global/system/secure; `storage` settings/addon_file/internal/external; `enabledIf/disabledIf`, `exclusiveGroup`, `binderOn/binderOff` для carrier-config, `icon*`), `main[]` (страницы `id/title/group/priority/targetActivity/targetSlot`), локали inline + `META-INF/addon_<lang>.json`, external override `<jar>.json`. Сборка: `./build_addon.sh <name>` (Java 11+, D8 из `prebuild/`, выход `out/*.jar`). Загрузка: `/system_ext/etc/pixelparts/addons` + `/data/pixelparts/addons`. Полная схема — `example.addon.hook/README.md` + `docs/`.

---

<a id="patch-system"></a>
## 5. Патч-система (`patches/`)

Checked-in снапшоты (`patches/files/`): `modified/<rel>` (изменённые), `original/<rel>` (чистый HEAD), `new/<rel>` (новые файлы), `patches/<rel>.patch` (unified diff для ручного наката), `source_snapshot.{json,txt}` (провенанс). `config.json` сейчас пуст (`version: 1, bypass_paths: [], patches: {}`) — ID генерируются динамически (`snap-frameworks-base-...`, `new-...`).

`apply_patches.py`: `--root` (default — корень дерева), `--check` (dry-run, default), `--apply`, `--apply-bypassed`, `--list`, `--configure-bypass <id> on|off`, `--only <substr>` (repeatable), `--verbose`.

Движок (`patchlib.py`): **сначала структурный порт** — `difflib.SequenceMatcher.get_grouped_opcodes(5)` между `original` и `modified`: ханк применяется, если новый блок уже есть (skip) или старый блок найден **ровно 1 раз** (замена); 0/2+ совпадений — конфликт → **fallback unified patch `patch -p0 -N -s -l --fuzz=3`** (предварительно reverse dry-run `-R`: уже применён → `applied`); мусор `.rej/.orig` удаляется. Не легло — `failed` с `manual_hint`, без угадывания. `NewFilePatch`: побайтовое сравнение → `applied`/`failed`/`created`. Bypass: `--apply-bypassed` > `mode` в конфиге > префиксы `bypass_paths` > `bypass_by_default`.

Цели (27 modified + 1 new):

- **`frameworks/base`** (17+1): `Activity`, **`ActivityThread`** (инжекция), `ApplicationPackageManager`, `LauncherActivityInfo`, `EdgeEffect`, `Magnifier`, `WindowOnBackInvokedDispatcher`, `ScreenRotationAnimation`, SettingsLib `Utils`, SystemUI `ScreenBrightnessDisplayManagerRepository` + `AodBurnInLayer`, `LightsService`, `PackageInstaller{,Service}`, `PhoneWindowManager`, `DisplayContent`, `SystemServer` (старт `DtwSystemService`); new: `DtwSystemService.java` (system-side DTW, ключи `pixelparts_dtw_*`).
- **`hardware/google/pixel/thermal`** (5): `Thermal.{h,cpp}`, `thermal-helper.{h,cpp}`, `powerhal_helper.h` (watcher `vendor.thermal.config`, reload helper).
- **`system/sepolicy`** (2): `private/{domain,coredomain}.te` — исключения `-system_app` (см. §7).
- **`system/memory`** (2): `libdmabufheap/.../BufferAllocator.{h,cpp}` (деструктор out-of-line для GRF-пребилдов).
- **`device/lineage/sepolicy`** (1): `common/sepolicy.mk` (в filter добавлен `akita`).

Репрезентативные вставки: в `ActivityThread` — константы `PIXEL_PARTS_DEFAULT_WHITELIST` + `PIXEL_PARTS_INJECT_PREFIX` и блок `INJECTION START/END` перед `callApplicationOnCreate` (whitelist ИЛИ `...inject_package_<pkg>==1`, скип `Process.isIsolated()`); в `SystemServer` — `startService(DtwSystemService.class)`; в sepolicy — `-system_app` в двух `neverallow`.

Обновление checked-in файлов после своих правок: `sync_from_bakfiles.py --list` / `--snapshot latest --prune` / `--snapshot <ts> --regen-patches --prune`. Формат коротко — `patches/README.md`.

---

<a id="thermal-internals"></a>
## 6. Thermal

### Генератор (`ThermalConfigs/generate_thermal_configs.py`)

Вызывается из `device.mk` на каждый билд (`--quiet --vendor-path $(VENDOR_PATH) --device-codename $(DEVICE_CODENAME) --init-rc ... [+ --thermal-json]`). `thermal_info_config.json` ищется: regex vendor copy-rules по `*.mk` → `rglob` (предпочтение совпадению с rule, затем `proprietary/vendor/etc/`, `vendor/etc/`) → иначе `PixelExtraPartsThermal=disabled` (блок в `init.rc` комментируется, `thermal_available=false`).

Патчит только `HotThreshold` внутри объектов с `Name` из `TARGETS_SOC` (9 `VIRTUAL-SKIN*`) / `TARGETS_BATTERY` (4 `VIRTUAL-SKIN-CHARGE*`), сдвиги `OFFSETS = {stock:0, soft:5, medium:9, hard:15, off:90}`. Выход: `configs/thermal_info_config[_soc_<lvl>][_battery_<lvl>]_<codename>.json`, комбинаторика 5×5−1 = 24 имени (на akita/husky/shiba — 72 файла) + перезаписываемый `ThermalConfigCopyRules.mk` (`PRODUCT_COPY_FILES → $(TARGET_COPY_OUT_VENDOR)/etc/...`). Затем `strip_old_thermal_block()` вырезает старый блок из `init.pixelextraparts.rc` и дописывает новый. Сгенерированное в `.gitignore`.

### Runtime

Цепочка переключения (`utils/ThermalProfileController` + `services/ThermalProfileService`):

1. `seedVendorConfigs()`: `/vendor/etc/thermal_info_config_*.json` → `/data/pixelparts/*.json` (+ `profiles.json`).
2. `applyConfig()`: `thermal_config` = имя файла (stock → `thermal_info_config.json`) + `thermal_config_request` = millis-serial, обе через `SystemProperties` (рефлексия, 5 попыток × 10мс).
3. `init.rc`: `on property:thermal_config_request=* && thermal_config=*` → copy в `/data/vendor/pixelparts/ThermalConfigs/active.json` (`chown/chmod/restorecon`) → `setprop vendor.thermal.config <serial>,<active.json>`; stock-кейс — `vendor.thermal.config=thermal_info_config.json` без copy.
4. Пропатченный HAL (`Thermal.cpp`): `configWatcherLoop` опрашивает `vendor.thermal.config` каждые 200мс, `reloadThermalHelper()`, retry 5с.
5. Per-app: `map.json` `{globalConfig, packages:{pkg:configId}}`, сервис каждую секунду смотрит top-package → `packageConfigs[pkg] ?: globalConfig ?: __stock__`; только global без правил — `stopSelf()`. Очередь тайла — `Settings.Global THERMAL_TILE_PROFILE_QUEUE` (max 5).

`init.pixelextraparts.rc` (30 строк): `post-fs-data` — `mkdir/restorecon` `/data/pixelparts{,/ThermalConfigs,/addons,/addons-dex,/system_addons_data}`, `/data/vendor/pixelparts/ThermalConfigs`; `boot` — права backlight для AutoHBM; `init` — `thermal_available=true`; два `on property` кейса выше.

---

<a id="sepolicy-internals"></a>
## 7. Sepolicy

Собственные файлы (подключаются из `device.mk`: `BOARD_VENDOR_SEPOLICY_DIRS`, `SYSTEM_EXT_{PUBLIC,PRIVATE}_SEPOLICY_DIRS`):

| Файл | Правило |
|---|---|
| `system_ext/private/file.te` + `file_contexts` | Типы `pixelparts_data_file` (`/data/pixelparts`), `pixelparts_system_file` (`/system_ext/etc/pixelparts/addons`) |
| `system_ext/private/pixelparts.te` | `system_app`: set/get `system_pixelparts_prop`, create data; init/appdomain/system_server/shell — read/create data; все appdomain — read system_file; `system_app` read `sysfs_leds/thermal/batteryinfo`, rw `sysfs_pixelparts_leds`; `system_server` rw leds |
| `system_ext/private/property.{te,_contexts}` | 5 ключей `persist.sys.pixelparts.{soc,battery,thermal_config,thermal_config_request,thermal_available}` → `system_pixelparts_prop` |
| `system_ext/public/file.te` | Тип `sysfs_pixelparts_leds` (sysfs_type) |
| `vendor/file{,_contexts}.te` | `pixelparts_vendor_data_file` (`/data/vendor/pixelparts`) |
| `vendor/genfs_contexts` | 3 sysfs-пути подсветки → `sysfs_pixelparts_leds` |
| `vendor/hal_sensors_default.te` | `hal_sensors_default` read leds-пути |
| `vendor/pixelparts_thermal.te` | `init` create vendor-data; `hal_thermal_default` read |

Патчи base (`patches/files/...`): `system/sepolicy/private/domain.te` — `-system_app` в `neverallow {coredomain ...} sysfs_batteryinfo:file {open read}`; `coredomain.te` — `-system_app` в `neverallow sysfs_leds:file *`. Именно эти исключения чаще всего отсутствуют в чужом base и дают `neverallow ... violated by allow system_app ...` — сначала `--check` патчей, потом ручной порт в свой device-tree.

---

<a id="build-internals"></a>
## 8. Сборка

`Android.bp`: `PixelCustomPartsSystem` (`android_app` из `common/src/**` + `system/src/**`, `common/res`, platform cert/privileged), `PineInject` (`java_library`: `core/**`, `hooks/**`, `manager/pine/**` + `pine-core-jar`, `pine-xposed-jar`, kotlin-stdlib), `libpine` (`cc_prebuilt_library_shared`, только arm64), `aapt2_pixelparts`/`libaapt2_pixelparts` (prebuilt `common/lib/arm64/libaapt2.so` — **только arm64**), 8 `prebuilt_etc *_addon` → `system_ext/etc/pixelparts/addons/*.jar`, `privapp_whitelist`, `init.pixelextraparts.rc`, `java_import` (`apksig-jar`, `pine-core-jar`, `pine-xposed-jar`).

Манифест (`system/AndroidManifest.xml`, `sharedUserId=android.uid.system`, `directBootAware`): ~150 `uses-permission`, ключевые — `WRITE_SECURE_SETTINGS`, `DEVICE_POWER`, `STATUS_BAR(_SERVICE)`, `CONTROL_DISPLAY_BRIGHTNESS`, `FOREGROUND_SERVICE_SPECIAL_USE`, `REBOOT`, `INSTALL/DELETE_PACKAGES`, `READ_LOGS`, `DUMP`; `privapp-permissions-pixelparts.xml` — 175 `<permission>` (включая `INTERACT_ACROSS_USERS`, `MANAGE_USERS`, `PACKAGE_USAGE_STATS`, `CHANGE_OVERLAY_PACKAGES`). `AppConfig`: `ENABLE_THERMALS` ← `persist.sys.pixelparts.thermal_available`, `IS_XPOSED=false` (Pine-only), `NEEDS_ROOT_ACCESS=false`.

---

## 9. Прочее

- **OTA/**: `gen.sh <zip>` — JSON из имени (`cut -d- -f4/-f5`: codename/версия) + `size/md5/sha256/timestamp`, `download=INSERT_DOWNLOAD_LINK_HERE`; `update_ota_json.py <дата|release-дир> [--devices ...] [--dry-run]` — сводка `builds/{shiba,husky,akita}.json` (SourceForge + чейнджлоги).
- **Языки** (`lang/`): `strings_{en,ru,de,fr,in,uk}.json` (~542–557 ключей; полные — en/ru).
- **Overscroll-пресеты** (`overscroll.configs/`): 10 JSON (iOS Rubber Band, Elastic Band, Snap Back, Wave Deform, Zoom Pulse, Bouncy Ball, Samsung Galaxy, Ghost Whisper, Heavy Weight, Jelly Physics).
- **`pep_update.sh install|uninstall`**: ставит/сносит собранный APK как update (`adb root`, userdebug); uninstall чистит `/data/app`-копию, системный пакет сохраняет.
- **`command.txt`, `test.files/`, `VideoSample/`**: dev-заметки, мусор и демо-видео — не часть поставки.
