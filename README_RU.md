# PixelExtraParts (PEP)

**Русский** | [English](README.md)

Системный пакет кастомизации для Android ROM, собираемых из исходников: привилегированное приложение настроек (`org.pixel.customparts`), runtime-хуки через Pine-инжекцию, термопрофили, снапшот-патчи исходников и SDK внешних аддонов.

> Как это устроено внутри (архитектура, хуки, настройки, патчер, thermal, sepolicy) — отдельный документ: **[docs/PROJECT_RU.md](docs/PROJECT_RU.md)**. Здесь — только подключение и сопровождение.

---

<a id="must-read"></a>
## ⚠️ Сначала прочитай: обязательные шаги

Если пропустить хотя бы один пункт — получишь «собралось, но ничего не работает» или ошибку сборки.

| # | Шаг | Что будет, если пропустить |
|---|-----|----------------------------|
| 1 | **Примени патчи исходников** ([как](#patches)) | Все опции на месте, но ничего не меняют (особенно лаунчер). Сборка успешная — это и сбивает с толку |
| 2 | **Вырежь стоковый thermal-HAL из vendor-образа** ([как](#thermal)) | Конфликт HAL → возможен бутлуп: стоковый HAL не читает кастомный конфиг, fallback'а нет |
| 3 | **Проверь sepolicy под свой base** ([как](#sepolicy)) | `neverallow ... violated by allow ...` в середине сборки |
| 4 | **Задай `VENDOR_PATH` и `DEVICE_CODENAME` до `inherit-product device.mk`** ([как](#connect)) | `PixelExtraParts requires VENDOR_PATH...` |
| 5 | **Запускай `sync_tree.py` только из корня дерева** ([как](#sync)) | Сломаные снапшоты, мусор в `bakFiles` |

Быстрая самопроверка:

```bash
# из корня Android source tree:
python3 packages/apps/PixelExtraParts/patches/apply_patches.py --check   # патчи на месте?
grep -rn "android.hardware.thermal-service.pixel" device/<vendor>/<codename>/  # свой HAL есть, стоковый вырезан?
```

---

<a id="connect"></a>
## 1. Подключение

```bash
# Клон СТРОГО в этот путь:
git clone <repo> packages/apps/PixelExtraParts
```

В device/product makefile, **до** `inherit-product`:

```makefile
DEVICE_CODENAME := shiba
VENDOR_PATH := vendor/google/shiba
# опционально, если thermal_info_config.json лежит нестандартно:
# THERMAL_CUSTOM_JSON_PATH := vendor/.../thermal_info_config.json

$(call inherit-product, packages/apps/PixelExtraParts/device.mk)
```

| Переменная | Обязательна | Зачем |
|---|---|---|
| `DEVICE_CODENAME` | Да | Суффикс имён генерируемых термоконфигов |
| `VENDOR_PATH` | Да | Поиск `thermal_info_config.json` и copy-rules; без неё — ошибка сборки |
| `THERMAL_CUSTOM_JSON_PATH` | Нет | Явный путь к `thermal_info_config.json`, если автоопределение не нашло |

Что `device.mk` делает сам:

- добавляет в `PRODUCT_PACKAGES`: приложение `PixelCustomPartsSystem`, `init.pixelextraparts.rc`, `PineInject`, `libpine` и 8 аддонов;
- разрешает пути артефактов (`system_ext/etc/pixelparts/addons/*.jar`, `system/framework/PineInject.jar`, `system/lib64/libpine.so` и др.);
- запускает генератор термоконфигов и подключает `ThermalConfigCopyRules.mk`;
- подключает `sepolicy/vendor` и `sepolicy/system_ext/{public,private}`.

---

<a id="patches"></a>
## 2. Патчи исходников

Приложение — лишь пульт: хуки живут в пропатченных `frameworks/base`, `Settings`, `hardware/google/pixel/thermal`, `system/sepolicy`, `system/memory`. Всего ~27 файлов + 1 новый (`DtwSystemService.java`). **Все команды — из корня дерева.**

```bash
python3 packages/apps/PixelExtraParts/patches/apply_patches.py --list    # что есть
python3 packages/apps/PixelExtraParts/patches/apply_patches.py --check   # что уже применено (dry-run)
python3 packages/apps/PixelExtraParts/patches/apply_patches.py --apply   # применить всё
python3 packages/apps/PixelExtraParts/patches/apply_patches.py --apply --only frameworks/base/core/java/android/app/Activity.java
```

Правила:

- Новые файлы из `patches/files/new/` создаются автоматически.
- Если дерево уплыло от снапшота — патчер **остановится с подсказкой** `Manual action required: ...`, а не будет гадать. Портируй вручную (`files/modified/` vs `files/original/`, либо `patch -p1 < files/patches/...`) и перезапусти. Тексты подсказок на английском; если не уверен — покажи оба файла AI.
- Отдельный патч можно исключить: `--configure-bypass <patch-id> on|off` (ID видно в `--list`), применить вопреки исключению — `--apply-bypassed`.
- После своих правок + нового снапшота `sync_tree.py -s` обнови checked-in файлы:

```bash
python3 packages/apps/PixelExtraParts/patches/sync_from_bakfiles.py --snapshot latest --prune
```

Как устроен движок патчера и полный список целей — [docs/PROJECT_RU.md](docs/PROJECT_RU.md#patch-system).

---

<a id="sync"></a>
## 3. Безопасный `repo sync` (sync_tree.py)

Скрипт сохраняет локальные изменения в `bakFiles/snapshots/`, синкает дерево и возвращает изменения обратно. **Запуск только из корня дерева** (пути резолвятся от CWD).

| Команда | Что делает |
|---|---|
| `sync_tree.py -s -j4` | Глубокий скан + снапшот + `repo sync` + возврат патчей |
| `sync_tree.py -s -d` | Только снапшот, без sync |
| `sync_tree.py -c` | Просмотр/применение патчей из снапшотов |
| `sync_tree.py -f` | Force sync: снапшот + откат, патчи обратно НЕ накатываются |
| `sync_tree.py -r` | Только откат к HEAD со снапшотом, без sync |
| `sync_tree.py -e` | Исключения сканера |
| `-y` | Неинтерактивный режим |

Перед первым `-s` добавь в исключения (`-e`) свой device-tree, vendor-tree и приватные каталоги — иначе сканер подхватит чужие изменения. Пример для Pixel 8 series: `device/google/shiba`, `vendor/google/shiba` (`out` и сам проект исключены по умолчанию).

---

<a id="thermal"></a>
## 4. Thermal: два требования

1. **Убери стоковый thermal из vendor-образа** в своём device-tree и собери HAL из исходников (`hardware/google/pixel/thermal`):

```makefile
PRODUCT_PACKAGES += android.hardware.thermal-service.pixel thermal_symlinks
PRODUCT_SOONG_NAMESPACES += hardware/google/pixel/thermal
```

Из vendor-образа убрать: `android.hardware.thermal-service.pixel{.rc,.xml,}`, `pixel-thermal-symlinks.rc`, `thermal-budget-interface-ndk`, `thermal_symlinks`.

> Почему так строго: стоковый HAL падает с ошибкой преобразования пути на кастомном конфиге, fallback'а нет — после перезагрузки система может не запуститься. Если чинить device-tree некогда — временно вырежь thermal-куски из `common/`, `device.mk` и `init.pixelextraparts.rc`.

2. Генератор (`ThermalConfigs/generate_thermal_configs.py`) вызывается из `device.mk` автоматически: ищет `thermal_info_config.json` по `VENDOR_PATH` (или `THERMAL_CUSTOM_JSON_PATH`), выдаёт варианты `stock/soft/medium/hard/off` для SOC и батареи в `configs/` + `ThermalConfigCopyRules.mk`. Сгенерированное **не коммитить**.

Как thermal работает внутри (проперти, `init.rc`, per-app профили) — [docs/PROJECT_RU.md](docs/PROJECT_RU.md#thermal-internals).

---

<a id="sepolicy"></a>
## 5. Sepolicy

- `device.mk` подключает `sepolicy/{vendor,system_ext/*}` сам.
- Патчи добавляют исключения в `system/sepolicy/private/{domain,coredomain}.te` (например, `-system_app` для `sysfs_batteryinfo` и `sysfs_leds`) и `device/lineage/sepolicy`.

Если сборка падает с `neverallow ... violated by allow ...` — **сначала** проверь `--check` патчей PEP: нужное исключение, скорее всего, уже есть в патч-сете. Ручные правки под свой base держи в своём device-tree, не в PEP. Полный разбор политик — [docs/PROJECT_RU.md](docs/PROJECT_RU.md#sepolicy-internals).

---

## 6. Сборка и обновление

```bash
lunch <target>
m PixelCustomPartsSystem PineInject libpine
```

- Ручное обновление собранного APK на устройстве: `./pep_update.sh install` / `./pep_update.sh uninstall` (нужен `adb root`, userdebug).
- OTA-метаданные своих сборок: `OTA/gen.sh <путь_к_zip>`, сводка релизов — `OTA/update_ota_json.py`.
- Цели `Android.bp`, манифест, разрешения — [docs/PROJECT_RU.md](docs/PROJECT_RU.md#build-internals).

---

## 7. FAQ

<details>
<summary><b>Собралось успешно, но опции ничего не меняют (иконки, лаунчер...)</b></summary>

Патчи не применены. Выполни [раздел 2](#patches): `--check`, затем `--apply`. Критично для лаунчера (`launcher_hooks` + `frameworks/base`).

</details>

<details>
<summary><b>`neverallow ... violated by allow ...` в середине сборки</b></summary>

Сначала `--check` патчей PEP — исключение уже может быть в патч-сете. Если base отличается — портируй в своём device-tree (см. [раздел 5](#sepolicy)).

</details>

<details>
<summary><b>Патчер пишет `Manual action required`</b></summary>

Дерево уплыло от снапшота. Внеси изменения вручную по паре `files/original/` → `files/modified/` (или `files/patches/....patch`), перезапусти `--apply`.

</details>

<details>
<summary><b>После смены термопрофиля — проблемы с загрузкой</b></summary>

Стоковый thermal-HAL в образе. См. [раздел 4](#thermal).

</details>

<details>
<summary><b>`repo sync` затёр мои правки</b></summary>

Синкай только через `sync_tree.py -s` из корня дерева (см. [раздел 3](#sync)). Откат без sync — `-r`.

</details>

---

## Для разработчиков

Коротко; детали — [docs/PROJECT_RU.md](docs/PROJECT_RU.md):

- Новые настройки — только через `SettingsKeys`/`SettingsCompat` (`Settings.Global`, суффикс `_pine`; legacy `_xposed` только читается). Не хардкодь ключи.
- Видимый текст — через ресурсы `common/res/` (+ переводы в `lang/`).
- Не коммить сгенерированные термоконфиги и локальные файлы.
- Меняешь патчер/thermal-скрипты — валидируй JSON/XML/Python.
- `command.txt` и `test.files/` — dev-мусор, не для продакшена.

## License

Project code plus several Android/Pine integration artifacts — check upstream files and imported prebuilts before redistributing binaries outside your ROM workflow.
