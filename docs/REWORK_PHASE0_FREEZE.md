# REWORK Фаза 0 — заморозка (ветка test_rework_patches)

Дата среза: 2026-11-07. Сборку делает только владелец вручную.
Агент не запускает Soong-сборки. Планы: `test_rework_patches/` (игнор),
`test_rework_patches_gemini/gemiin.md`.

## 1. База

- PEP-ветка: `test_rework_patches` от `3938d84` (ветка `test-vector-module-compat`).
- Upstream-проекты в состоянии `repo status` на момент среза — см. раздел 3.
  Все правки upstream лежат в рабочих деревьях / detached-HEAD-коммитах,
  НЕ в ветках lineage. Откат — только после того, как замена приземлилась
  в PEP (H1-оверлей / хук / форк). Порядок откатов — раздел 4.

## 2. Грязные upstream-файлы на срез (repo status)

- `build/soong`: `scripts/check_boot_jars/package_allowed_list.txt` (+2 строки,
  разделитель; строк `org.pixel` НЕТ — правка не нужна, файл откачен к HEAD).
- `device/lineage/sepolicy`: `common/sepolicy.mk` (M) — вне скоупа PEP-триплетов,
  оставлен как есть, требует отдельного разбора владельцем.
- `frameworks/base` (M): `Activity.java`, `ActivityThread.java`,
  `ApplicationPackageManager.java`, `LauncherActivityInfo.java`,
  `EdgeEffect.java`, `Magnifier.java`, `WindowOnBackInvokedDispatcher.java`,
  `ScreenRotationAnimation.java`, `SettingsLib/Utils.java`,
  `ScreenBrightnessDisplayManagerRepository.kt`, `AodBurnInLayer.kt`,
  `PackageInstallerService.java`, `PackageInstallerSession.java`,
  `PhoneWindowManager.java`, `DisplayContent.java`, `SystemServer.java`.
  Плюс untracked `services/core/java/com/android/server/DtwSystemService.java`
  (новый файл PEP-логики; при любом checkout frameworks/base будет ПОТЕРЯН —
  до переноса в PEP не делать `checkout -- .` в этом проекте).
- `hardware/google/pixel`: `thermal/{Thermal.cpp,Thermal.h,thermal-helper.cpp,
  thermal-helper.h,utils/powerhal_helper.h}` (M) — откат только вместе с
  форком HAL через `overrides:` (Фаза 6).
- `packages/apps/Settings`: `Android.bp` (M) — вне триплетов, оставлен как есть.
- `system/sepolicy`: `private/domain.te` (M, KEEP — исключение `-system_app`,
  неснимаемо); `Android.bp` + `build/soong/compat_cil.go` — записи
  `pixelparts_app` были закоммичены в detached HEAD; откачены к ванили
  копией из `patches/files/original/` (Фаза 1, Вариант А).
- `vendor/gms`: `product/blobs/etc/sysconfig/google.xml` (M) — вне скоупа.
- `device/google/shusky`, `vendor/google/shiba`: ветка
  `lineage-24.0-leegar-evox-cp3a` — не трогать.

## 3. Триплеты PEP (patches/files)

Всего 24 `.patch` (17 frameworks/base + 5 thermal + 2 sepolicy).
Оригиналы `original/` — эталон ванили для отката.
`modified/` + `patches/*.patch` — применённое состояние.

Выведены из обращения (Фаза 1, `retired/`):
- `system/sepolicy/Android.bp` (orig `Android.bp.bak` + mod + `.patch`)
- `system/sepolicy/build/soong/compat_cil.go` (orig + mod + `.patch`)

Причина: домен `pixelparts_app` удалён (Вариант А) — allowlist-довески
больше не нужны. Оригиналы сохранены в `retired/` как слепок ванили.

## 4. Порядок откатов upstream (не делать раньше замены)

1. Уже откачено (Фаза 1): `system/sepolicy/Android.bp`, `compat_cil.go`,
   `build/soong/.../package_allowed_list.txt`.
2. После H1-пилота + зелёной сборки владельца: `WindowOnBackInvokedDispatcher`,
   `SettingsLib/Utils` (WEAVE) — откат + удаление триплетов.
3. После H1-расширения: `EdgeEffect`, `Magnifier` (WEAVE).
4. После делегатов: `Activity`, `APM`, `LauncherActivityInfo`,
   `PhoneWindowManager`, `DisplayContent` — ужатие до 1–3 строк вызова,
   триплеты обновить (не удалять).
5. После per-app хуков SystemUI: `ScreenRotationAnimation`, HBM-репозиторий,
   `AodBurnInLayer` — откат + удаление триплетов.
6. После форка HAL: 5 файлов thermal — откат + удаление триплетов.
7. Никогда (KEEP): `ActivityThread`, `SystemServer`, `PackageInstaller(+Session)`,
   `domain.te` (`-system_app`).

## 5. Вариант А (Фаза 1) — что сделано в этой ветке

- `sepolicy/system_ext/private/pixelparts.te`: блок `pixelparts_app`
  (type/app_domain/net_domain/hal_client/permissive) удалён; аппка падает
  на стоковый `system_app`. Строка `sysfs_leds:file`: `r_file_perms` →
  `rw_file_perms` (писатель — `AutoHbmController.writeText`; стоковый
  `neverallow coredomain.te:40-53` уже содержит `-system_app`).
  `dir` оставлен `r_dir_perms`, `lnk_file` — `read` (минимум для записи
  в существующий узел brightness).
- `sepolicy/system_ext/private/seapp_contexts` удалён (файл) — fallback:
  сток `system/sepolicy/private/seapp_contexts:171`
  (`user=system seinfo=platform → system_app`).
- Оставлено без изменений: `domain.te`-триплет, `property_contexts`
  (2 префикса), `get_prop(zygote/shell/system_app)`, `genfs_contexts`
  (1 class-строка), весь vendor thermal-блок, `init.pixelextraparts.rc`.

Проверка владельцем: user-сборка (гейты neverallow/allowlist молчат) →
`ps -Z` показывает `system_app` → HBM вкл/выкл + слайдер, `dmesg` без
`avc: denied`.

## 6. ФИНАЛ (все фазы в ветке, сборок не запускалось)

Коммиты (новые к старым): 5fbaa86 thermal-форк, 72dde46 хуки,
fb6f893 drift-repair, 5f25e59 делегаты, 67f7470 H1+WEAVE,
8d4023f freeze+SELinux, 3938d84 база.

- H1: soong/soong-pixelparts-plugin (framework-minus-apex, SettingsLib,
  services.core) + filegroups pixelparts_framework_weave/helpers,
  pixelparts_settingslib_weave, pixelparts_services_helpers.
- WEAVE: EdgeEffect/Magnifier/WindowOnBack/Utils — оверлеи в PEP,
  апстрим ваниль, триплеты retired.
- DELEGATE: Activity +29/-1, APM +5, Launcher +19, PhoneWM +1,
  DisplayContent +6; хелперы в framework_helpers/.
- DtwSystemService переехал в framework_helpers_services/
  (модуль services.core), untracked-файлов в frameworks/base нет.
- HOOK: 3 хука в systemui_hooks + регистрация; 3 source-файла ваниль.
- THERMAL: thermal_hal/ форк (stem+overrides), device.mk += pep;
  hardware/ чисто. sepolicy уже покрывал мод-канал.
- Остаток триплетов (10): 5 делегатов + ActivityThread +
  2 PackageInstaller + SystemServer + domain.te.
- ВНИМАНИЕ владельцу: локальный коммит 0cae3e97e в system/sepolicy СНЕСЁН
  (reset --hard до evo/cnb dd9d56abe, см. проверку «no local commits»).
  system/sepolicy чистый кроме domain.te (-system_app, uncommitted KEEP).
  frameworks/base HEAD новее triplet-базы в 4 файлах — originals/
  обновлены до HEAD (коммит fb6f893).
- packages/apps/Settings, device/lineage/sepolicy,
  vendor/gms/google.xml — чужая грязь, не тронута.

Чек-лист владельца (одна сборка): neverallow/allowlist/check_boot_jars/
api-stubs молчат → ps -Z system_app → HBM/thermal/поворот/AOD/переходы/
иконки/DTW → maps Telegram без lsplant → dmesg без avc.

## 7. BUILD-TIME WEAVE (замена статичным форкам, коммит 6f376dd)

Статичные копии (framework_overrides/, settingslib_overrides/,
vendored thermal-sources) УДАЛЕНЫ. Вместо них:
- `weave/specs/` — 18 unified-diff спек (13 java + 5 thermal).
- `weave/weave_apply.py` + `weave/manifest.json` (36 записей: 18 patch + 18 copy).
- `device.mk` запускает regen на make-парсе; дрейф = громкий стоп сборки.
- `weave_gen/` — git-ignored выхлоп; Go-плагин подсовывает его через
  Exclude_srcs + явные filegroups (framework/SettingsLib/services.core/
  services.impl + services_helpers + thermal).
- Исходники в полном ванильном состоянии: frameworks/base и hardware
  чистые, system/sepolicy — только domain.te. Триплеты мигрировавших
  файлов retired; legacy-патчер их пропускает (bypass_paths).
- domain.te оставлен tree-патчем (не Java, H1 неприменим).
