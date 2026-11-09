# Структура PixelExtraParts

Корень держит только агрегаторы сборки:

- `Android.bp` — все Soong-модули: `init.pixelextraparts.rc` собирается
  `genrule pep_init_rc` (`cat` шаблона + thermal-фрагмента, см. ниже),
  стоковая аппка `PixelCustomPartsSystem` (из `app/`, platform-подпись,
  system_ext priv-app) + `apksig`/`aapt2`/`privapp_whitelist`,
  пребилты `liblsplant/dobby/c++_shared/lspbridge` + `LsplantInject`
  (из `lsplant_engine/`), пребилты аддонов (`addons/release/*/out/*.jar`),
  filegroups weave (из `soong/weave_gen/`) и хелперы (из `soong/platform/`).
  Filegroups лежат здесь, т.к. их `srcs` тянут файлы из нескольких соседних
  деревьев, а Soong не разрешает `../` из подпапок.
- `device.mk` — точка входа для продукта: `PRODUCT_PACKAGES`,
  `PRODUCT_SOONG_NAMESPACES` (корень + `thermal_hal` — у него свой
  `soong_namespace`), weave-regen (`build` до Soong), sepolicy-директории,
  thermal-генератор, `filter-out` стокового thermal-HAL.
- `.gitignore` — игноры generated: `soong/weave_gen/`,
  `thermal_hal/config/{configs,gen}`, `ThermalConfigCopyRules.mk`,
  NDK-артефакты lsplant, запаркованный `app_test/`.

Домены:

- `soong/` — конвейер build-time weave (всё в одном месте):
  `weave_mutator.go` + `Android.bp` — Go-плагин `soong-pixelparts-plugin`.
  Логика оперирует только ИМЕНАМИ модулей (`framework-minus-apex`,
  `services.core.unboosted`, `services.impl`, `SettingsLib`) и ИМЕНАМИ
  filegroup (`:pixelparts_*`). Физических путей не содержит.
- `soong/weave/` — спеки: `specs/*.patch` (диффы ваниль→мод),
  `manifest.json` (`rel` = живой файл, `spec` = дифф, `out` = `weave_gen/`),
  `weave_apply.py` (строгий аппликатор, дрейф = `PEP_WEAVE_ERROR`).
  `soong/weave_gen/` — generated, в git нет (39 записей манифеста +
  `include_dirs` thermal указывают сюда).
- `soong/platform/framework_helpers/` — Java-хелперы framework
  (`CustomTransitionHelper`, `PixelPartsIconHelper`), компилируются В СОСТАВЕ
  `framework-minus-apex` через `:pixelparts_framework_helpers`.
- `soong/platform/services_helpers/` — хелперы сервисов (`DtwSystemService`,
  `PixelPartsHardwareInput`, `PixelPartsKeepScreenOn`, `PixelPartsCallState`),
  компилируются В СОСТАВЕ `services.core.unboosted` через
  `:pixelparts_services_helpers` (плюс woven `services.impl`-файлы через
  `:pixelparts_weave_services_impl`).
- `lsplant_engine/` — рантайм инжектора отдельно от всего: `src/`
  (исходники `LsplantInject`), `libs/` (пребилты
  `liblsplant/dobby/c++_shared/lspbridge.so`), `lsplant-native/`
  (`bridge/` — наш JNI-glue, собирается NDK-скриптом в пребилт;
  `third_party/` — только мастер-копии движков + заголовки +
  provenance-маркеры, IDE/CI-мусор вычищен).
  Java-пакеты (`org.pixel...manager.lsplant`) — это ПАКЕТЫ, а не пути:
  при переезде каталогов их не переименовывать.
- `thermal_hal/` — самодостаточный домен форка HAL `.pep` (`overrides:`,
  без `stem:`), свой `.rc`/`.xml`. Исходники — через
  `:pixelparts_weave_thermal` из `soong/weave_gen/` + `include_dirs`
  на его зеркало. Внутри — `config/` (рантайм thermal-сборки):
  `generate_thermal_configs.py` (дерево НЕ трогает) + `configs/` +
  `ThermalConfigCopyRules.mk` (generated, в git нет) + `gen/` (thermal-блок
  `init.thermal-switch.rc`, generated, в git нет; решает enabled/disabled).
- `init.pixelextraparts.rc.in` — СТАТИЧЕСКИЙ шаблон init-скрипта всего модуля
  (app-директории, brightness; модуль `prebuilt_etc` берёт его НЕ напрямую,
  а через `genrule pep_init_rc`: `cat` шаблона + thermal-фрагмента).
  Дерево при сборке НЕ мутирует — замена прописана `cmd` в `Android.bp`.
- `policy/sepolicy/` — весь sepolicy PEP (`system_ext/private`,
  `vendor/`; каталога `system_ext/public/` в проекте нет — public-директория
  из `device.mk` на этой базе резолвится пустой). Подключен `*_SEPOLICY_DIRS`
  в `device.mk`. `domain.te`-исключение живёт в апстриме отдельно (ручной патч).
- `app/` — стоковая аппка единой цепочкой (как в проде, без изменений):
  `AndroidManifest.xml`, `src/`, `res/`, `lib/`, `lang/`, `proguard.flags`,
  `privapp-permissions-pixelparts.xml`, `donate_page.json`
  (бывшие рудименты веток `common/` + `system/` слиты сюда).
- `addons/` — аддоны: `release/` — продовые (11 шт, `out/*.jar` зачекинены),
  `tests/` — экспериментальные; `prebuild/` — общие зависимости сборки;
  `build.sh` (`build.bat/ps1`) собирает по имени из `release/` и `tests/`.
- `app_test/` — запаркованная Compose-работа (игнор в git, в сборку не входит).
