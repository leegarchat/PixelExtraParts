# PixelExtraParts (PEP)

**Русский** | [English](README.md)

Привилегированное приложение настроек + runtime-хуки + термопрофили для
дерева прошивки Evolution X. Лежит в `packages/apps/PixelExtraParts`.

> Устройство внутри (архитектура, weave-конвейер, thermal, sepolicy): [docs/ARCHITECTURE_RU.md](docs/ARCHITECTURE_RU.md).
> SDK аддонов: [addons/README.md](addons/README.md).

## Структура

| Путь | Что это |
|---|---|
| `app/` | Само приложение, единой цепочкой: `AndroidManifest.xml`, `src/`, `res/`, `lib/`, `lang/`, `proguard.flags`, `privapp-permissions-pixelparts.xml`, `donate_page.json` |
| `addons/` | Аддоны: `release/` (11 продовых, `out/*.jar` зачекинены), `tests/` (экспериментальные), `prebuild/` (общие зависимости сборки: `android.jar`, `d8`, `IAddonHook.java`, LSPlant/Xposed jars); сборка через `addons/build.sh <name>` |
| `soong/` | H1-weave конвейер: `weave_mutator.go` + `Android.bp` (плагин `soong-pixelparts-plugin`, подменяет стоковые исходники на woven по именам модулей); `weave/` (спеки `specs/*.patch` + `manifest.json` + строгий аппликатор `weave_apply.py`); `weave_gen/` (generated, в git нет); `platform/` (хелперы, компилируемые в составе framework/services: `framework_helpers/`, `services_helpers/`) |
| `thermal_hal/` | Самодостаточный форк HAL `android.hardware.thermal-service.pixel.pep` (`overrides:`, без `stem:`), свои `.rc`/`.xml`; исходники — из `:pixelparts_weave_thermal` (`soong/weave_gen/`) + `include_dirs`-зеркало. В `config/` — генератор термоконфигов + генерируемые `configs/`, `ThermalConfigCopyRules.mk`, `gen/`-фрагмент |
| `lsplant_engine/` | Рантайм инжектора: `src/` (java-библиотека `LsplantInject`), `libs/` (пребилты `liblsplant`/`libdobby`/`libc++_shared`/`liblspbridge` `.so`), `lsplant-native/` (`bridge/` JNI-glue, NDK-скрипт — fallback для ручной сборки пребилта, Soong берёт только пребилт) |
| `policy/sepolicy/` | Весь sepolicy PEP: `system_ext/private/`, `vendor/` |
| `init.pixelextraparts.rc.in` | Статический шаблон init-скрипта. Настоящий `init.pixelextraparts.rc` собирается genrule `pep_init_rc` (`cat` шаблона + генерируемого thermal-switch фрагмента из `thermal_hal/config/gen/`); дерево не мутирует |
| `Android.bp` / `device.mk` | Агрегаторы сборки (см. ниже) |
| `STRUCTURE.md` | Карта дерева на уровне путей и модулей |

`app_test/` — запаркованный Compose-эксперимент (в git-игноре, в сборку не входит).

## Подключение к прошивке

В device/product makefile — plain `-include` (НЕ `inherit-product`),
инлайном в глобальном скоупе сразу после стоковых записей:

```makefile
DEVICE_CODENAME := shiba
VENDOR_PATH := vendor/google/shiba
# опционально, если thermal_info_config.json не находится сам:
# THERMAL_CUSTOM_JSON_PATH := vendor/.../thermal_info_config.json

# Plain include, NOT inherit-product: файл должен выполниться немедленно
# в глобальном скоупе ПОСЛЕ стоковых записей выше, чтобы thermal filter-out
# в его конце реально их увидел. inherit-product откладывает его в
# node-local scope, где PRODUCT_PACKAGES пуст (filter-out — no-op).
-include packages/apps/PixelExtraParts/device.mk
```

| Переменная | Обязательна | Зачем |
|---|---|---|
| `DEVICE_CODENAME` | Да | Суффикс имён генерируемых термоконфигов |
| `VENDOR_PATH` | Да | Поиск `thermal_info_config.json` и copy rules; пустое значение — ошибка сборки |
| `THERMAL_CUSTOM_JSON_PATH` | Нет | Явный путь к `thermal_info_config.json`, если автоопределение не сработало |

Что `device.mk` делает сам:

- добавляет в `PRODUCT_PACKAGES`: приложение `PixelCustomPartsSystem`, `init.pixelextraparts.rc`, форк HAL `.pep`, 11 addon-jar, `LsplantInject`, `liblsplant`/`libdobby`/`libc++_shared`/`liblspbridge`;
- добавляет `PRODUCT_SOONG_NAMESPACES` (корень + `thermal_hal` с собственным `soong_namespace`);
- перегенерирует `soong/weave_gen/` из живой ванили + `soong/weave/specs/` **до** разбора file-листов Soong; дрейф спеки роняет сборку громко (`PEP_WEAVE_ERROR`), а не молча;
- запускает генератор термоконфигов и подключает `ThermalConfigCopyRules.mk`;
- подключает sepolicy-каталоги (`vendor`, `system_ext/private`) и вырезает стоковый `android.hardware.thermal-service.pixel` (форк `.pep` заменяет его через `overrides:`).

Прошивка собирается только владельцем через `make.sh`. Агент сборки, `adb` и `repo sync` не запускает.

## Сборка аддона

```bash
cd packages/apps/PixelExtraParts/addons
./build.sh <name>              # ищет <name> в release/, затем в tests/
./build.sh my_hook ./path/to/project   # явный каталог проекта
```

Результат: `<project>/out/<name>.jar`. Нужна Java 11+; сначала используются
jars из `prebuild/`, затем локальный SDK (переопределения `ANDROID_JAR`/`D8_JAR`
поддерживаются). Settings-only аддоны (без `src/`, без `entryClass`) пакуют
только `META-INF/`, без DEX.

## Новый weave-спек (кратко)

1. Сними diff ванили и модифицированного файла, положи в `soong/weave/specs/<тот-же-rel-путь>.patch`.
2. Добавь запись в `soong/weave/manifest.json`: `rel` (путь в живом дереве),
   `spec` (путь патча), `out` (путь под `soong/weave_gen/`).
3. Добавь `out`-путь в matching `filegroup` корневого `Android.bp`
   (`pixelparts_weave_framework` / `_settingslib` / `_services_core` / `_services_impl` / `_thermal`).

Детали: [docs/ARCHITECTURE_RU.md](docs/ARCHITECTURE_RU.md). Уплывшая база
против контекста спеки роняет сборку — спеку надо ребейзить, а не форсировать.

## License

Project code plus several Android/LSPlant integration artifacts — check upstream
files and imported prebuilts before redistributing binaries outside your ROM workflow.
