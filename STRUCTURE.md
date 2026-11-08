# Структура PixelExtraParts

Корень держит только агрегаторы сборки:

- `Android.bp` — все Soong-модули: `init.pixelextraparts.rc` (из `config/`),
  стоковая аппка `PixelCustomPartsSystem` (`common/` + `system/` + `lang/`,
  platform-подпись, system_ext priv-app) + `apksig`/`aapt2`/`privapp_whitelist`,
  пребилты `liblsplant/dobby/c++_shared/lspbridge` + `LsplantInject`
  (из `native/lsplant/`), пребилты аддонов (`addons/release/*/out/*.jar`),
  filegroups weave (из `weave_gen/`) и хелперы (из `platform/`).
  Filegroups лежат здесь, т.к. их `srcs` тянут файлы из нескольких соседних
  деревьев, а Soong не разрешает `../` из подпапок.
- `device.mk` — точка входа для продукта: `PRODUCT_PACKAGES`,
  `PRODUCT_SOONG_NAMESPACES` (корень + `native/thermal_hal` — у него свой
  `soong_namespace`), weave-regen (`build` до Soong), sepolicy-директории,
  thermal-генератор, `filter-out` стокового thermal-HAL.
- `.gitignore` — игноры generated: `weave_gen/`, `config/thermal/configs`,
  `ThermalConfigCopyRules.mk`, NDK-артефакты lsplant.

Домены:

- `soong/` — Go-плагин `soong-pixelparts-plugin` (`weave_mutator.go`).
  Логика оперирует только ИМЕНАМИ модулей (`framework-minus-apex`,
  `services.core.unboosted`, `services.impl`, `SettingsLib`) и ИМЕНАМИ
  filegroup (`:pixelparts_*`). Физических путей не содержит — переезд
  каталогов его не затрагивает.
- `weave/` — build-time weave: `specs/*.patch` (диффы ваниль→мод),
  `manifest.json` (`rel` = живой файл, `spec` = дифф, `out` = `weave_gen/`),
  `weave_apply.py` (строгий аппликатор, дрейф = `PEP_WEAVE_ERROR`).
  `weave_gen/` — generated, в git нет. НЕ ПЕРЕЕЗЖАЛ: на него завязаны
  37 записей манифеста + `include_dirs` thermal + `.gitignore`.
- `platform/framework_helpers/` — Java-хелперы framework
  (`CustomTransitionHelper`, `PixelPartsIconHelper`), компилируются В СОСТАВЕ
  `framework-minus-apex` через `:pixelparts_framework_helpers`.
- `platform/services_helpers/` — хелперы сервисов (`DtwSystemService`,
  `PixelPartsHardwareInput`, `PixelPartsKeepScreenOn`), компилируются В СОСТАВЕ
  `services.core.unboosted` через `:pixelparts_services_helpers`.
- `native/lsplant/` — рантайм инжектора: `src/` (исходники `LsplantInject`),
  `libs/` (пребилты `liblsplant/dobby/c++_shared.so`), `lsplant-native/`
  (`bridge/` — наш JNI-glue, собирается NDK-скриптом в пребилт
  `libs/.../liblspbridge.so`); `third_party/` — только мастер-копии
  движков + заголовки + provenance-маркеры, IDE/CI-мусор вычищен).
  Java-пакеты (`org.pixel...manager.lsplant`) — это ПАКЕТЫ, а не пути:
  при переезде каталогов их не переименовывать.
- `native/thermal_hal/` — форк HAL `.pep` (`overrides:`, без `stem:`),
  свой `.rc`/`.xml`. Исходники — через `:pixelparts_weave_thermal`
  из `weave_gen/` + `include_dirs` на зеркало `weave_gen/.../thermal`.
- `policy/sepolicy/` — весь sepolicy PEP (`system_ext/private`,
  `system_ext/public`, `vendor/`). Подключен тремя `*_SEPOLICY_DIRS`
  в `device.mk`. `domain.te`-исключение живёт в апстриме отдельно (ручной патч).
- `config/thermal/` — `generate_thermal_configs.py` (пути вычисляет от
  собственного `SCRIPT_DIR`, переездобезопасен) + `configs/` (generated,
  в git нет) + `ThermalConfigCopyRules.mk` (generated, в git нет).
- `config/init.pixelextraparts.rc` — init-скрипт (модуль `prebuilt_etc`).
  Перезаписывается генератором thermal (safe-split) при каждой сборке.
- `app/` — стоковая аппка единой цепочкой (как в проде, без изменений):
  `AndroidManifest.xml`, `src/`, `res/`, `lib/`, `lang/`, `proguard.flags`,
  `privapp-permissions-pixelparts.xml`, `donate_page.json`
  (бывшие рудименты веток `common/` + `system/` слиты сюда).
- `addons/` — аддоны: `release/` — продовые (11 шт, `out/*.jar` зачекинены),
  `tests/` — экспериментальные; `prebuild/` — общие зависимости сборки;
  `build.sh` (`build.bat/ps1`) собирает по имени из `release/` и `tests/`.
- `app_test/` — запаркованная Compose-работа (игнор в git, в сборку не входит).
