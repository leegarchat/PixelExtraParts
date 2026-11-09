# PixelExtraParts: архитектура

**Русский** | [English](ARCHITECTURE.md)

Как части связаны между собой в новой раскладке. Подключение — в
[README_RU.md](../README_RU.md); SDK аддонов — в [addons/](../addons/README.md).

## 0. Как всё связано

```
┌─ Приложение org.pixel.customparts (system_ext priv-app, platform-подпись)
│   пишет ТОЛЬКО Settings (канонический суффикс хуков `_lsplant`,
│   legacy `_pine`/`_xposed` — только чтение) ───────────────────┐
├─ LsplantInject.jar (system/framework)                         │
│   инжектится через woven ActivityThread.handleBindApplication │
│   в systemui / лаунчеры (+whitelist)                          │
│   ModEntry → HookEntry → хуки читают те же ключи ─────────────┘
├─ Аддон *.jar (system_ext/etc/pixelparts/addons + /data/...)
│   грузится AddonLoader внутри целевых процессов
├─ Weave: стоковые деревья остаются ванилью; Soong компилирует
│   woven-копии из soong/weave_gen/ (спеки в soong/weave/specs/*.patch)
└─ Thermal: приложение → persist.sys.pixelparts.* → init.rc
    → vendor.pixelparts.thermal.config → пропатченный форк .pep HAL
```

Главная идея: **приложение никогда не лезет в чужие процессы напрямую**.
Оно пишет настройки, а код внутри SystemUI/лаунчера (woven-код framework и
LSPlant-хуки/аддоны) их читает. Без weave (инжекции + точек хуков) приложение
собирается, но ничего не меняет.

## 1. Ключи настроек

Состояние — в Android Settings (`app/src/org/pixel/customparts/`):

- **`utils/HookKeyCompat.kt`**: `ACTIVE_SUFFIX = "_lsplant"`,
  `LEGACY_ENGINE_SUFFIX = "_pine"`, `XPOSED_SUFFIX = "_xposed"`.
- **`utils/SettingsCompat.kt`**: новые записи — только вариант `_lsplant`;
  legacy-значения `_pine`/`_xposed` читаются через fallback, никогда не пишутся.
- **Сторона хуков** (`lsplant_engine/.../manager/lsplant/LsplantEnvironment.java`):
  тот же порядок резолва.

Правило: определяй ключи через `SettingsKeys`/`SettingsCompat`, не хардкодь
строковые литералы `Settings.Global.put*`.

## 2. Приложение (`app/`)

Единая цепочка (бывшие рудименты `common/` + `system/` слиты сюда):
`AndroidManifest.xml`, `src/` (activities, services/receivers, tiles, utils,
icons), `res/`, `lib/` (включая `arm64/libaapt2.so`), `lang/`, `proguard.flags`,
`privapp-permissions-pixelparts.xml`, `donate_page.json`.

Soong-цель `PixelCustomPartsSystem` (`android_app`, platform-сертификат,
privileged, `system_ext`): `app/src/**/*.kt|java`, `app/res`, static-библиотеки
(androidx/compose/material/SettingsLib), `LsplantInject` — отдельная
`java_library` (см. §5).

## 3. Weave (`soong/`)

Чекиненных патчей на дерево нет, каталога `patches/` нет. Апстрим-деревья
(`frameworks/base`, `hardware/google/pixel/thermal`) остаются ванилью; PEP
везёт диффы и накатывает их во время сборки:

1. `device.mk` запускает `soong/weave/weave_apply.py --manifest soong/weave/manifest.json`
   **до** разбора file-листов Soong. Аппликатор читает живую ваниль, накатывает
   `soong/weave/specs/*.patch` (unified diffs), пишет результат в
   `soong/weave_gen/` (в git-игноре, 39 записей манифеста).
2. Soong-плагин (`soong/weave_mutator.go`, `bootstrap_go_package
   soong-pixelparts-plugin`) цепляет целевые модули по **имени** и подменяет исходники:
   - `framework-minus-apex` (+ `-headers`) ← `:pixelparts_weave_framework` + `:pixelparts_framework_helpers`
   - `SettingsLib` ← `:pixelparts_weave_settingslib`
   - `services.core.unboosted` ← `:pixelparts_weave_services_core` + `:pixelparts_services_helpers`
   - `services.impl` ← `:pixelparts_weave_services_impl`
3. `filegroup` объявлены в корневом `Android.bp`. Они лежат в корне, потому что
   их `srcs` тянут файлы из нескольких соседних деревьев, а Soong запрещает `../`
   из подпапок.
4. `soong/platform/` — обычные хелперы, компилируемые **в составе** целевых
   модулей: `framework_helpers/` (`CustomTransitionHelper`, `PixelPartsIconHelper`)
   и `services_helpers/` (`DtwSystemService`, `PixelPartsKeepScreenOn`,
   `PixelPartsCallState`, `PixelPartsHardwareInput`).

Поведение при дрейфе: спека, чей контекст больше не совпадает с живой ванилью,
роняет сборку громко (`PEP_WEAVE_ERROR`, ненулевой exit) — никогда молча.
Спеку надо ребейзить (переснять diff против новой ванили), а не форсировать.

### Новый weave-спек

1. Сними vanilla-файл и свою модифицированную копию, сохрани unified diff как
   `soong/weave/specs/<тот-же-относительный-путь>.patch`
   (например `soong/weave/specs/frameworks/base/core/java/android/app/Activity.java.patch`).
2. Добавь запись в `soong/weave/manifest.json`:
   `rel` — путь в живом дереве (`frameworks/base/...`),
   `spec` — путь патча, `out` — выход под `soong/weave_gen/...`.
3. Добавь `out`-путь в matching `filegroup` корневого `Android.bp`
   (`pixelparts_weave_framework`, `pixelparts_weave_settingslib`,
   `pixelparts_weave_services_core`, `pixelparts_weave_services_impl`
   или `pixelparts_weave_thermal`).

## 4. Thermal (`thermal_hal/`)

Самодостаточный форк: `cc_binary android.hardware.thermal-service.pixel.pep` с
`overrides: [android.hardware.thermal-service.pixel]` и **без** `stem:`
(Soong строит рёбра для каждого модуля в скоупе, общий output path — фатальная
ошибка "multiple rules"). `device.mk` вырезает стоковый модуль из
`PRODUCT_PACKAGES`. Свои `.rc`/`.xml`, свой `soong_namespace`. Исходники форка —
из `:pixelparts_weave_thermal` (`soong/weave_gen/`) плюс `include_dirs`-зеркало,
чтобы корневые кавычки резолвились exactly как в `hardware/google/pixel/thermal`.

Генератор конфигов (`thermal_hal/config/generate_thermal_configs.py`, вызывается
из `device.mk`): находит `thermal_info_config.json` по `VENDOR_PATH` (или
`THERMAL_CUSTOM_JSON_PATH`), патчит `HotThreshold` внутри объектов
`TARGETS_SOC` / `TARGETS_BATTERY` со смещениями `OFFSETS = {stock:0, soft:5,
medium:9, hard:15, off:90}`, выдаёт SOC×battery варианты в `configs/` +
`ThermalConfigCopyRules.mk`, а init-switch фрагмент — в
`gen/init.thermal-switch.rc`. Все три выхода в git-игноре; не коммитить.

Рантайм-цепочка: приложение пишет `persist.sys.pixelparts.thermal_config{,_request}` →
init-фрагмент копирует выбранный JSON в
`/data/vendor/pixelparts/ThermalConfigs/active.json` → выставляет мод-канал
`vendor.pixelparts.thermal.config` (`<request>,<path>`); сток-безопасный дефолт
оставляет `vendor.thermal.config = thermal_info_config.json`, чтобы
непропатченный HAL никогда не видел мод-пейлоад. Форкнутый HAL следит за
мод-каналом и перезагружает thermal helper.

## 5. LSPlant-рантайм (`lsplant_engine/`)

- `src/` — исходники `LsplantInject.jar` (`core/**` интерфейсы, включая
  `IAddonHook`, `manager/lsplant/**`, `lsplantinject/**` с `ModEntry`,
  `de/robv/android/xposed/**` compat-шим, современный libxposed API, shaded
  commons-lang). Сборка: корневой `Android.bp` → `java_library "LsplantInject"`.
- `libs/` — пребилты в `/system/lib64` как `cc_prebuilt_library_shared`
  (`liblsplant`, `libdobby`, `libc++_shared`, `liblspbridge`).
- `lsplant-native/` — наш JNI-glue (`bridge/`) + мастер-копии заголовков/бинарей
  движков (`third_party/`, provenance-маркеры). Собирается только NDK-fallback
  скриптом в стейджинг `out/`; Soong берёт пребилт из `libs/`.

Цепочка загрузки: woven `ActivityThread.handleBindApplication` (whitelist или
`pixel_extra_parts_inject_package_<pkg> == 1`, изолированные процессы
пропускаются, `system_server` не хукается никогда) → `ModEntry`
(`System.loadLibrary("lspbridge")`, биндится к уже загруженному чужому движку
или к нашим `/system`-пребилтам) → `HookEntry` (глобальные хуки + `AddonLoader`,
который грузит addon-JAR из `/system_ext/etc/pixelparts/addons` и
`/data/pixelparts/addons`, версионно с приоритетом `/data` при равенстве) →
аддонный `handleLoadPackage()`.

## 6. Sepolicy (`policy/sepolicy/`)

Подключается из `device.mk` через `BOARD_VENDOR_SEPOLICY_DIRS` (`vendor/`) и
`SYSTEM_EXT_{PRIVATE}_SEPOLICY_DIRS` (`system_ext/private/`). Каталога
`system_ext/public/` в проекте нет — public include dir из `device.mk` на этой
базе резолвится пустым. Исключения `domain.te`/`coredomain.te` (`-system_app`)
живут в апстриме как ручной патч базы, не в PEP.

## 7. Сборка (`Android.bp`, `device.mk`, init)

- Корневой `Android.bp` агрегирует всё: genrule `pep_init_rc`
  (`cat init.pixelextraparts.rc.in` + `:pep_thermal_switch_frag` из
  `thermal_hal/config/gen/`), `prebuilt_etc init.pixelextraparts.rc`,
  приложение, `LsplantInject`, четыре `cc_prebuilt_library_shared`, 11 addon
  `prebuilt_etc` jar, `privapp_whitelist`, `apksig-jar` import, все weave
  `filegroup`.
- `init.pixelextraparts.rc.in` — статический шаблон (`post-fs-data` каталоги,
  права на backlight sysfs); thermal-switch блок дописывается во время сборки
  genrule. Рабочее дерево не мутирует.
- Прошивка собирается только владельцем через `make.sh`. Агент сборки, `adb` и
  `repo sync` не запускает.

## 8. Что выкинуто из старого дерева (и почему)

- `patches/`, `sync_tree.py`, `bakFiles`, `command.txt`, `test.files/` —
  снапшот-патчер заменён build-time weave (§3).
- Сплит `common/` + `system/`, имя `example.addon.hook/`, пути `ThermalConfigs/`
  и `native/lsplant` — слиты/переименованы в `app/`, `addons/`,
  `thermal_hal/config/`, `lsplant_engine/`.
- Тест-ветки, заметки zigta, побранчевые доки (`test_rework_*`, старый `docs/`,
  `README_old.md`) — заменены этим документом и `addons/docs/`.
- Старые цели `Android.bp`, OTA-хелперы (`OTA/`, `pep_update.sh`) — не часть
  этого дерева; прошивка едет через `make.sh`.
