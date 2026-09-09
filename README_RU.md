# PixelExtraParts

PixelExtraParts — системный пакет кастомизации для Android ROM-сборок. Проект объединяет privileged-приложение настроек, runtime-хуки для Launcher/SystemUI/framework, Pine-инъекцию ART-хуков, генератор thermal-профилей и SDK для внешних addon-хуков.

English documentation: [README.md](README.md)

## Для кого этот проект

Проект ориентирован на Android ROM и device maintainers, которые собирают систему из исходников и хотят добавить Pixel-style настройки прямо в системный образ. Это не обычное приложение из Play Store: основной system target использует platform APIs, platform certificate, privileged permissions и интеграцию в `system_ext`.

## Что входит в проект

PixelExtraParts состоит из нескольких связанных частей:

| Область | Назначение |
| --- | --- |
| Privileged system app | UI настроек на Compose/Material3 с package `org.pixel.customparts`. |
| Pine injection JAR | `PineInject.jar` с runtime-хуками для source-built ROM интеграции. |
| Built-in hooks | Настройки launcher grid/recents, overscroll physics, magnifier, activity transitions, predictive back, SystemUI/doze/shade tweaks. |
| Thermal tooling | Build-time генерация thermal profile JSON из vendor `thermal_info_config.json`. |
| Source patch tooling | Python launcher для framework и Settings snapshots из `patches/files/`. |
| Addon SDK | Примеры внешних hook-модулей, которые собираются как addon JAR и загружаются динамически. |
| OTA helpers | Вспомогательные скрипты и JSON metadata для OTA в `OTA/`. |

## Структура репозитория

| Путь | Описание |
| --- | --- |
| [Android.bp](Android.bp) | Soong-модули, prebuilt libraries, APK targets и `PineInject`. |
| [device.mk](device.mk) | Product include file для ROM/device trees. |
| [sync_tree.py](sync_tree.py) | Хелпер безопасного `repo sync`: сохраняет локальные изменения, синкает, возвращает их. Запускать из корня дерева. |
| [common/](common/) | Общий app-код, Compose UI, ресурсы, менеджеры и утилиты. |
| [system/](system/) | Manifest privileged system APK и system-build `AppConfig`. |
| [pine/](pine/) | Pine runtime sources, hook core, managers, built-in hooks и prebuilt Pine jars. |
| [patches/files/](patches/files/) | Snapshots (`modified/`, `original/`, `new/`) и unified diffs (`patches/`) для framework и Settings patches. |
| [patches/](patches/) | Python patch launcher для применения или проверки snapshots из `patches/files/`. |
| [ThermalConfigs/](ThermalConfigs/) | Генератор thermal profiles и integration copy rules. |
| [example.addon.hook/](example.addon.hook/) | Примеры внешних addon-хуков и build scripts. |
| [sepolicy/](sepolicy/) | SELinux policy snippets, подключаемые через `device.mk`. |
| [OTA/](OTA/) | OTA JSON metadata и helper scripts. |
| [overscroll.configs/](overscroll.configs/) | Пользовательские JSON presets для overscroll. |

## Build targets

Основные Soong targets описаны в [Android.bp](Android.bp):

| Target | Тип | Назначение |
| --- | --- | --- |
| `PixelCustomPartsSystem` | `android_app` | Privileged `system_ext` app из `common/` + `system/`, platform APIs, platform certificate. |
| `PineInject` | `java_library` | Hook payload, устанавливаемый как `system/framework/PineInject.jar`. |
| `libpine` | prebuilt shared library | Native runtime dependency для Pine. |
| `aapt2_pixelparts` / `libaapt2_pixelparts` | prebuilts | Runtime/build helper binaries для приложения и tooling. |

## Git clone в Android source tree

Клонируйте репозиторий в путь, который ожидает [device.mk](device.mk):

```bash
cd $ANDROID_BUILD_TOP
git clone https://github.com/leegarchat/PixelExtraParts packages/apps/PixelExtraParts
```

Product include рассчитывает на этот путь:

```makefile
PIXEL_EXTRA_PARTS_PATH := packages/apps/PixelExtraParts
```

Если репозиторий расположен в другом месте, поправьте локальные product makefiles.

## Подключение в device product

Добавьте PixelExtraParts из device или product makefile:

```makefile
$(call inherit-product, packages/apps/PixelExtraParts/device.mk)
```

[device.mk](device.mk) добавляет:

```makefile
PRODUCT_PACKAGES += \
    PixelCustomPartsSystem \
    init.pixelextraparts.rc \
    PineInject \
    libpine
```

Также он добавляет artifact allow-list для Pine/aapt2 prebuilts и подключает SELinux policy из [sepolicy/system_ext/private](sepolicy/system_ext/private/).

### Обязательные product variables

`device.mk` ожидает стандартные Android build variables и путь к vendor tree для генерации thermal configs:

```makefile
DEVICE_CODENAME := shiba
VENDOR_PATH := vendor/google/shiba
```

`VENDOR_PATH` должен указывать на vendor tree, где есть `thermal_info_config.json` или vendor makefiles, из которых можно вывести путь копирования. Генератор thermal profiles использует этот файл для создания вариантов конфигов.

Опциональный override:

```makefile
THERMAL_CUSTOM_JSON_PATH := vendor/google/shiba/proprietary/vendor/etc/thermal_info_config.json
```

## Сборка

Из настроенного Android build окружения:

```bash
lunch <your_target>
m PixelCustomPartsSystem PineInject libpine
```

При полной ROM-сборке system target подтянется автоматически после подключения [device.mk](device.mk).

## Как это работает

### System app

`PixelCustomPartsSystem` — ROM-интегрированное приложение. Оно собирается из [common/](common/) и [system/](system/), работает как package `org.pixel.customparts`, подписывается platform certificate, является privileged и устанавливается в `system_ext`. UI сделан на Compose/Material3, а состояние фич в основном хранится через `Settings.Global` helpers в `SettingsKeys` и `SettingsCompat`.

System manifest запрашивает privileged Android permissions для записи настроек, package visibility, restart actions для SystemUI/launcher, telephony controls и package management. Соответствующий privapp allow-list находится в [privapp-permissions-pixelparts.xml](privapp-permissions-pixelparts.xml).

### Pine runtime

`PineInject` упаковывает hook core, built-in hooks и Pine manager code в `PineInject.jar`. Source-tree patches могут инжектить этот JAR в выбранные app processes. Во время runtime [HookEntry.java](pine/src/org/pixel/customparts/manager/pine/HookEntry.java) применяет built-in hooks к launcher packages и `com.android.systemui`, затем загружает addon hooks, если для package есть подходящие addon metadata.

Текущий built-in launcher scope:

```text
com.google.android.apps.nexuslauncher
com.google.android.apps.pixel.launcher
com.android.launcher3
```

SystemUI hooks применяются к:

```text
com.android.systemui
```

[init.pixelextraparts.rc](init.pixelextraparts.rc) создаёт `/data/pixelparts` директории для addons и runtime data.

### Runtime settings suffixes

Настройки, зависящие от runtime, используют суффикс `_pine` через project settings helpers (legacy-суффикс `_xposed` по-прежнему распознаётся при чтении сохранённых ключей для обратной совместимости). При добавлении новых settings или hooks используйте `SettingsKeys` и `SettingsCompat`, а не дублируйте `Settings.Global` keys вручную.

## Source patches

PixelExtraParts корректно работает только с накатанными source-tree патчами: хуки из [pine/](pine/) рассчитаны на matching-изменения в Android framework, Settings, thermal HAL, sepolicy и исходниках Updater. Без них приложение соберётся, но runtime-фичи (overscroll, magnifier, launcher, thermal и т.д.) молча не будут работать.

Накатывайте snapshots из [patches/files/](patches/files/) через patch launcher из [patches/](patches/). Все команды ниже выполняются из корня Android source tree:

```bash
cd $ANDROID_BUILD_TOP
python3 packages/apps/PixelExtraParts/patches/apply_patches.py --list
python3 packages/apps/PixelExtraParts/patches/apply_patches.py --check
python3 packages/apps/PixelExtraParts/patches/apply_patches.py --apply
```

Отдельные патчи можно пропускать по ID (список ID — в `--list`):

```bash
python3 packages/apps/PixelExtraParts/patches/apply_patches.py --configure-bypass <patch-id> on
python3 packages/apps/PixelExtraParts/patches/apply_patches.py --apply
```

Ручной вариант: если launcher сообщает о drift для файла, накатите matching unified diff вручную, тоже из корня дерева:

```bash
patch -p1 < packages/apps/PixelExtraParts/patches/files/patches/frameworks/base/core/java/android/widget/EdgeEffect.java.patch
```

Новые файлы из `patches/files/new/` создаются автоматически через `--apply`; при ручном накатывании скопируйте их руками. Если upstream-файл слишком сильно ушёл от snapshot, patcher остановится и напечатает manual porting hint вместо рискованного угадывания.

### Безопасный repo sync с локальными изменениями (sync_tree.py)

[sync_tree.py](sync_tree.py) — копия утилиты синхронизации дерева. Она сохраняет ваши локальные изменения framework/Settings/thermal в `bakFiles/snapshots/`, выполняет `repo sync`, затем возвращает изменения поверх обновлённого дерева:

```bash
cd $ANDROID_BUILD_TOP
python3 packages/apps/PixelExtraParts/sync_tree.py -s -j 4
```

Важно: скрипт резолвит все пути (`bakFiles/`, git-проекты, вызовы `repo`) относительно текущей рабочей директории — сам корень дерева он не ищет (в коде `ROOT_DIR = Path(".").resolve()`, привязки к расположению `.py`-файла нет). Всегда запускайте его из корня Android source tree, а не из каталога проекта. Другие полезные режимы: `-s -d` — только слепок без sync, `-c` — просмотр/применение снимков, `-f` — force-sync без повторного наката патчей, `-y` — неинтерактивный режим. После нового `-s`-снимка обновите checked-in файлы через `patches/sync_from_bakfiles.py --snapshot latest --prune`.

Перед первым запуском `-s` добавьте в исключения (`-e`) ваш device tree, vendor tree и прочие приватные каталоги — иначе сканер подхватит чужие изменения. Пути указываются от корня дерева:

```bash
cd $ANDROID_BUILD_TOP
python3 packages/apps/PixelExtraParts/sync_tree.py -e
```

Рабочий пример (Pixel 8 series + локальные деревья):

| Игнорируемый путь (от корня дерева) |
| --- |
| `device/google/akita` |
| `device/google/build` |
| `device/google/gs-common` |
| `device/google/husky` |
| `device/google/pixel-kernels` |
| `device/google/shiba` |
| `device/google/shusky` |
| `device/google/zuma` |
| `out` (`out` исключён по умолчанию, указан для наглядности) |
| `packages/apps/PixelExtraParts` |
| `vendor/JamesDSP` |
| `vendor/google/akita` |
| `vendor/google/faceunlock` |
| `vendor/google/husky` |
| `vendor/google/shiba` |

Без этих исключений сканер будет цеплять посторонние device/vendor-модификации — наснимает мусор или будет конфликтовать с вашим device tree на каждом sync.

## Thermal profiles

[ThermalConfigs/generate_thermal_configs.py](ThermalConfigs/generate_thermal_configs.py) генерирует variants из исходного `thermal_info_config.json`. Скрипт меняет настроенные SOC и battery thermal sensor thresholds и пишет product copy rules в `ThermalConfigs/ThermalConfigCopyRules.mk`.

Generated files специально игнорируются git:

```text
ThermalConfigs/ThermalConfigCopyRules.mk
ThermalConfigs/configs/
```

На boot `init.pixelextraparts.rc` выставляет:

```text
persist.sys.pixelparts.thermal_available=true
```

Когда пользователь выбирает profile, приложение обновляет `persist.sys.pixelparts.thermal_config`; init переносит значение в `vendor.thermal.config` и перезапускает `vendor.thermal-hal`.

> [!WARNING]
> Обязательно для работы кастомного thermal: стоковый AOSP/vendor thermal-компонент нужно исключить из vendor-сборки, иначе он конфликтует с Pixel thermal HAL, который патчит PixelExtraParts (`hardware/google/pixel/thermal/`). Вынесите эти пребилды из vendor-образа в ваше древо устройства:
>
> - `vendor/etc/init/android.hardware.thermal-service.pixel.rc`
> - `vendor/etc/init/pixel-thermal-symlinks.rc`
> - `thermal-budget-interface-ndk`
> - `android.hardware.thermal-service.pixel.xml`
> - `android.hardware.thermal-service.pixel`
> - `thermal_symlinks`
>
> Пример для Pixel 8 series (zuma-древо, например `common.mk`):
>
> ```makefile
> # Thermal
> PRODUCT_PACKAGES += \
>     android.hardware.thermal-service.pixel \
>     thermal_symlinks
> ```
>
> И в этом же файле объявите Soong-неймспейс, который их собирает:
>
> ```makefile
> PRODUCT_SOONG_NAMESPACES += \
>     hardware/google/pixel/thermal
> ```
>
> Без этого на устройстве останется стоковый vendor thermal-сервис и кастомные профили не заработают. Хуже того, стоковый HAL падает с ошибкой преобразования пути на кастомном конфиге: после перезагрузки система просто не запустится, так как HAL не найдёт файл конфига, а fallback для стокового HAL программой не предусмотрен. В этом случае самостоятельно выпилите thermal-секцию и логику init-файлов из PEP (thermal UI/сервисы в `common/`, подключение `ThermalConfigs/` в `device.mk` и обработку `persist.sys.pixelparts.thermal_*` в `init.pixelextraparts.rc`), пока древо устройства не исправлено как описано выше.

## Addons

Внешние хуки можно собирать как addon JAR. Формат addon, build scripts, `META-INF/addon.json`, entry class contract и metadata для settings UI описаны в [example.addon.hook/README.md](example.addon.hook/README.md).

Addon payloads хранятся в `/data/pixelparts/addons` и загружаются Pine manager, когда scope addon совпадает с package.

## Ручные deployment notes

[command.txt](command.txt) содержит ручные `adb`, mount, install, push и reboot snippets для разработки. Считайте их локальными maintenance notes; не запускайте их вслепую на production device.

## Development guidelines

- Держите изменения в рамках нужного runtime: system app, Pine injection, patches, thermal tooling или addon SDK.
- Используйте существующие helpers для `Settings.Global`, restart actions, package queries и hook setup.
- Видимый UI-текст добавляйте через ресурсы в [common/res](common/res/).
- Не коммитьте generated thermal configs и локальные MemPalace files.
- Валидируйте JSON/XML/Python tooling при изменении metadata, resources, patcher code или thermal scripts.
- Android builds и deployment на устройство требуют настроенного ROM build tree и явного разрешения maintainer.

## License

Репозиторий содержит project code и несколько Android/Pine integration artifacts. Перед распространением бинарников вне вашего ROM workflow проверьте upstream files и imported prebuilts.