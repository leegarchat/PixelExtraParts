# PixelExtraParts: подробный список изменений и функций

English version: [pixel_extra_parts_features_2026_05_10.en.md](pixel_extra_parts_features_2026_05_10.en.md)

## Коротко.

- Добавлен полноценный Icon Pack Manager для PixelExtraParts: применение icon pack'ов, выбор иконок по приложениям, импорт кастомной иконки из APK, очистка, восстановление, поддержка удалённых паков и отдельные настройки shape/tint/scale для Launcher, SystemUI notifications и framework-level иконок.
- Добавлен runtime-хук Pixel Launcher для замены иконок без пересборки лаунчера: работает с cache/factory путём лаунчера, динамическими Calendar/Clock иконками, floating icon при открытии/перетаскивании и shape-логикой Pixel Launcher.
- Добавлены настройки Display: Saturation и Auto HBM. Saturation управляет насыщенностью через SurfaceFlinger, Auto HBM включает high brightness mode по датчику освещённости с задержками, ramp, cooldown, лимитом температуры и статусом.
- Сильно расширены настройки Pixel Launcher: сетка рабочего стола, dock, drawer, search/suggestion размеры, скрытие search/dock/feed/top widget, native search fix, hidden apps в drawer и double-tap-to-sleep.
- Добавлен большой блок Recents customization: Clear All button в разных режимах, отключение live tile, масштаб карточек, spacing, alpha, blur, tint и смещение иконок.
- Расширен SystemUI: компактный QS media player, скрытие медиаплеера в отдельных местах, прозрачность player background, настройка shade blur/zoom/scrim alpha/scrim tint, расширенная charging info на lockscreen и shape/tint иконок уведомлений.
- Добавлены/доработаны gesture и visual hooks: wake on doze double tap, text magnifier customization, activity transition animations, predictive back control и продвинутый overscroll physics engine с профилями.
- Добавлены Tensor thermal profiles: отдельные режимы battery/SoC Stock, Soft, Medium, Hard и Off, генератор thermal JSON и интеграция через device build.
- Добавлен IMS/CarrierConfig manager: VoLTE, VoWiFi, VT, VoNR, Cross-SIM, UT, 5G availability и 5G thresholds с применением на активные SIM.
- Обновлена runtime-инфраструктура Pine/Xposed: встроенные хуки для Launcher/SystemUI, Pine override для настроек, Addon SDK/Manager и загрузка внешних DEX JAR аддонов.
- Добавлена device/build интеграция: Soong targets, device.mk hooks, sepolicy, source patcher, OTA metadata/generator и документация PixelExtraParts.

## App Icons и Icon Pack Manager

Это один из самых крупных блоков изменений. PixelExtraParts получил собственную систему управления иконками, ориентированную именно на Pixel Launcher и системные UI-места, где Android обычно использует стандартные adaptive/dynamic иконки.

### Что появилось

- Новый раздел App Icons в системной версии PixelExtraParts.
- Backend `IconPackManager`, который отвечает за поиск icon pack'ов, чтение их mapping'ов, экспорт выбранных иконок в `/data/pixelparts/IconsManager/` и ведение `icon_map.json`.
- Поддержка нескольких режимов применения:
  - применить весь icon pack сразу;
  - частично применить pack через список приложений;
  - выбрать иконку из pack'а только для одного приложения;
  - импортировать иконку из другого установленного APK;
  - восстановить стандартную иконку конкретного приложения;
  - удалить все иконки, связанные с конкретным icon pack;
  - полностью очистить все данные App Icons.
- Разделение источников иконок в `icon_map.json`: pack source, custom imported icon, динамическая иконка и metadata по применённым пакетам.
- Сохранение карточек применённых icon pack'ов даже после удаления самого APK icon pack'а.
- Возможность управлять иконками удалённого pack'а: пользователь видит, что pack удалён, но всё ещё может открыть частичный список/снять применённые иконки/очистить следы.
- В App Icons UI добавлен отдельный action в floating reboot bubble для полной очистки всех иконок.
- Добавлены предупреждения и статусы для pack'ов, которые требуют обновления или уже отсутствуют в системе.
- Добавлены progress/status состояния для долгих операций применения, отмены и очистки.

### Поиск и выбор иконок

- Для каждого установленного приложения UI теперь показывает текущую применённую иконку и кандидаты из icon pack'ов.
- Улучшен поиск доступных иконок для конкретного пакета.
- Частичный apply теперь использует сохранённые bindings из `icon_map.json`, поэтому список остаётся корректным даже когда icon pack уже удалён.
- Для приложений можно выбирать альтернативы из icon pack'а, а не только дефолтное совпадение из `appfilter`.
- Добавлена логика восстановления применённых selection entries для missing pack'ов.

### Shape, tint и scale

App Icons теперь не ограничены простой заменой PNG. Добавлен отдельный слой настроек формы:

- Global/framework режим:
  - включение App Icons на системном уровне;
  - stretch icon into shape;
  - remove shape wrapper;
  - shape scale.
- Pixel Launcher режим:
  - отдельный master switch для Launcher;
  - stretch/remove shape только для launcher icons;
  - scale shape wrapper;
  - отдельная логика для floating icon и Pixel Launcher icon cache.
- Notification/SystemUI режим:
  - stretch/remove shape для notification app icons;
  - scale shape;
  - применение shape override к иконкам уведомлений в SystemUI.
- Tint controls:
  - background tint mode: Off/Auto/Custom;
  - foreground tint mode: Off/Auto/Custom;
  - кастомные цвета через color picker;
  - отдельная логика, чтобы цветовой круг/preview не терялся на фоне такого же цвета.
- Per-app shape overrides:
  - можно тонко управлять shape-обработкой не только глобально, но и для конкретных приложений.

### Dynamic icons

Отдельно доработан кейс динамических иконок:

- Calendar и Clock больше не должны безусловно уходить в стандартный набор динамических иконок Pixel Launcher, если для них есть PixelParts override.
- Runtime-хук учитывает dynamic icon metadata из PixelParts.
- Launcher dynamic path перехватывается так, чтобы PixelParts-иконка имела приоритет над stock dynamic provider там, где это нужно.
- Исправлены случаи, когда календарь/часы визуально возвращались к дефолтным динамическим иконкам после reload/cache path.

### Runtime в Pixel Launcher

Новый `LauncherIconOverrideHook` делает замену иконок в самом Pixel Launcher:

- Перехватывается создание badged icon bitmap в `BaseIconFactory`.
- Перехватывается путь Pixel Launcher `IconProvider`, связанный с dynamic icon state.
- Читается `/data/pixelparts/IconsManager/icon_map.json`.
- Поддерживаются PNG-иконки, adaptive wrappers, shape scale/tint/remove/stretch режимы.
- Исправлен floating icon при открытии приложения из drawer и при перетаскивании.
- Для PixelParts floating icon используется safe single-layer path, чтобы не появлялись дубли phantom icon.
- Исправлен кейс, где Chrome/другие приложения могли давать две phantom-иконки во floating animation.
- Исправлен мелкий размер phantom icon: нормализация видимой области и альфы перенесена в правильный участок pipeline.
- При режиме без shape wrapper floating icon больше не должен внезапно рендериться внутри shape.

### SystemUI notification icons

Новый `NotificationIconShapeHook` подключает App Icons к SystemUI:

- Hook применяется к `AppIconProviderImpl.fetchAppIconBitmapInfo`.
- Shape/tint настройки App Icons используются для иконок приложений в уведомлениях.
- Учитываются настройки `APP_ICONS_NOTIFICATION_*`.
- Используются reload receiver и чтение `icon_map.json`, чтобы SystemUI мог обновляться после изменений.
- Цель: чтобы иконки в notification shade/lockscreen выглядели консистентно с выбранной политикой App Icons, а не жили отдельно от Launcher.

### Последние фиксы App Icons

- Исправлена альфа/прозрачность иконок: экспортированные иконки нормализуются по видимой области, чтобы прозрачные adaptive wrappers не давали слишком маленькую иконку.
- Исправлены проблемы с фантомной иконкой при animation/open/drag.
- Исправлен double-render floating icon для Chrome-подобных кейсов.
- Исправлена ситуация, когда удалённый icon pack исчезал из UI, но его применённые иконки оставались без понятного способа очистки.
- Добавлена кнопка полной очистки всех иконок.
- Добавлена реконструкция missing pack cards по `icon_map.json`.
- Добавлена защита от устаревших bindings при удалённом APK pack'а.
- Улучшена карточка pack'а: missing/removed статус, ограничения на apply/view, но сохранение действий cleanup.

## Display: Saturation и Auto HBM

Добавлен новый Display hub в PixelExtraParts system app. Он объединяет настройки насыщенности и автоматического high brightness mode.

### Display hub

- Новый `DisplaySettingsActivity`.
- В системной сборке PixelExtraParts раздел Display доступен с главного экрана.
- В `system/AndroidManifest.xml` добавлены Settings aliases/activities для интеграции в системные категории.
- Display-раздел не показывается в Xposed-only APK, потому что это именно privileged/system app функциональность.

### Saturation

Добавлена настройка насыщенности экрана:

- Master switch включения.
- Slider насыщенности в диапазоне 0-200%.
- Preview card, чтобы пользователь сразу видел ожидаемый эффект.
- Применение через SurfaceFlinger transaction `1022`.
- Добавлена QS tile integration.
- Long press по tile открывает Saturation settings.
- Исправлены Compose/annotation и insets мелочи вокруг display screen.

Практический смысл: можно сделать цвета спокойнее или насыщеннее без внешних модулей и без отдельной утилиты, прямо из PixelExtraParts.

### Auto HBM

Добавлен автоматический High Brightness Mode:

- Master switch включения Auto HBM.
- Статусная карточка с текущим состоянием:
  - текущая освещённость lux;
  - активен ли HBM;
  - текущая температура;
  - текущая brightness state.
- Порог освещённости:
  - диапазон 2000-60000 lux;
  - дефолт около 20000 lux.
- Задержка включения.
- Задержка выключения.
- Smooth ramp:
  - плавный переход brightness вместо резкого скачка;
  - ramp duration 100-5000 ms;
  - дефолт около 800 ms.
- Max active time:
  - ограничение максимальной длительности активного HBM.
- Cooldown time:
  - пауза после HBM, чтобы режим не включался/выключался слишком часто.
- Check interval:
  - настраиваемая частота опроса.
- Temperature limit:
  - диапазон 30-80C;
  - дефолт около 50C;
  - защита от включения/удержания HBM при перегреве.
- Сервис `AutoHbmService`, который читает light sensor и управляет panel/sysfs brightness nodes.
- Сохранение и восстановление исходной яркости.
- Учитывается, была ли включена auto brightness до активации HBM.
- Добавлена QS tile/status интеграция.

Практический смысл: на улице экран может автоматически уходить в повышенную яркость, но с задержками, температурной защитой и cooldown, чтобы не превращать функцию в постоянный нагрев.

## Pixel Launcher

Pixel Launcher получил самый широкий набор runtime-настроек. Важная часть: это не просто UI-переключатели, а Pine/Xposed hooks, которые меняют поведение реального Pixel Launcher.

### Launcher settings hub

- Раздел Launcher вынесен в отдельный hub.
- Добавлены отдельные экраны:
  - grid/dock/app drawer/search sizing;
  - search/feed settings;
  - recents settings;
  - hidden apps;
  - App Icons integration.
- Для настроек, требующих reload, UI показывает restart action для Pixel Launcher.
- Большая часть настроек хранится в `Settings.Global` с runtime suffix `_pine` или `_xposed`.

### Home screen, dock и app drawer grid

Добавлены настройки сетки и размеров:

- Dock:
  - количество иконок dock: 1-12;
  - размер иконок dock: 1-200%.
- Home screen:
  - включение кастомного home grid;
  - количество колонок;
  - количество строк;
  - размер иконок;
  - режим текста: default, two-line, marquee, hide.
- App drawer:
  - включение кастомного drawer grid;
  - количество колонок;
  - высота строки;
  - размер иконок;
  - режим текста: default, two-line, marquee, hide.
- Suggestions/Search results:
  - размер иконок suggestions;
  - режим текста suggestions;
  - отключение suggestions;
  - размер иконок search results;
  - режим текста search results.

### Search widget, dock и paddings

Добавлен отдельный блок настройки нижней области лаунчера:

- Включение dock/search customization.
- Скрытие bottom search widget.
- Скрытие dock.
- Padding homepage.
- Padding dock.
- Padding search.
- Padding page indicator dots по Y.
- Padding page indicator dots по X.

Это позволяет делать как минималистичный home screen без search bar/dock, так и просто подправлять отступы под конкретный layout.

### Native search, feed и top widget

Добавлены переключатели:

- Native search fix для Pixel Launcher.
- Disable Google Feed.
- Disable top widget.

Runtime-хук `UnifiedLauncherHook` дорабатывался под актуальные классы Pixel Launcher/Nexus Launcher. В changelog попали аудиты и стабилизация launcher hooks.

### Hidden Launcher Apps

Добавлен отдельный экран скрытия приложений из drawer:

- Список установленных приложений.
- Поиск по приложениям.
- Фильтр системных приложений.
- Checkbox rows для выбора скрываемых приложений.
- Предупреждение о необходимости перезапуска Pixel Launcher.
- Привязка к runtime-хуку лаунчера, чтобы drawer не показывал выбранные пакеты.

Это именно Pixel Launcher feature: скрытие приложений делается на стороне лаунчера, а не через отключение APK или сторонний launcher.

### Double Tap To Sleep на Launcher

- Добавлен DT2S section для Pixel Launcher.
- Настройка хранится в runtime-aware `Settings.Global` key.
- Хук лаунчера обрабатывает double tap на workspace.
- В UI есть предупреждение о необходимости активного модуля в Xposed mode.

### Стабилизация Launcher hooks

В последних изменениях launcher hooks были дополнительно вычищены и стабилизированы:

- Аудит Nexus/Pixel Launcher hooks.
- Улучшено package matching.
- Учтены launcher lifecycle и reload/restart сценарии.
- Добавлены caches для базовых размеров/отступов, чтобы изменения не накапливались повторно после reload.
- Исправлены кейсы с dots margin и row height base caching.
- Оптимизировано чтение настроек Pine injection.
- Учитывается Pine override для settings resolver.

## Recents

Recents customization вынесен в отдельный экран и hook `RecentsUnifiedHook`.

### General

- Master switch включения Recents modding.
- Отключение live tile.
- Перезапуск Pixel Launcher из UI после изменений.

### Clear All button

Добавлена гибкая настройка Clear All:

- Включение Clear All.
- Скрытие actions row.
- Режимы отображения:
  - floating bottom button;
  - replace Screenshot action;
  - replace Select action.
- Настройка bottom margin для floating режима.
- В UI режимы показаны как понятные варианты с иконками.

Это закрывает частый Pixel Launcher pain point: Clear All можно вернуть в удобное место без замены лаунчера.

### Static scale и carousel geometry

- Static scale:
  - включение;
  - масштаб 20-120%.
- Carousel:
  - минимальный scale карточек 0.2x-1.2x;
  - spacing -400..500 px;
  - минимальная alpha 0.0-1.0.

### Blur, tint и icon offset

- Carousel blur radius:
  - 0-300 dp;
  - доступно на Android 12+ render effects path.
- Blur overflow switch.
- Tint:
  - intensity 0-100%;
  - выбор tint color через color picker.
- Icon offset:
  - X offset -1500..2500 dp;
  - Y offset -1500..2500 dp.

### Runtime polish

- Render effects включаются только когда реально нужны.
- Добавлена cleanup-логика для attach listeners.
- Лишняя работа в default mode снижена.
- Recents hook адаптирован под актуальные классы Pixel Launcher.

## SystemUI

SystemUI-раздел теперь разбит на Lockscreen, Shade, Magnifier и Activity Transitions. Основные изменения касаются медиаплеера, shade surface, charging info и notification icon shape.

### Shade media player

Добавлены настройки QS/media player:

- Background alpha player'а:
  - 0-100%;
  - применяется к album art/background/scrim элементам media view.
- Compact mode:
  - Off;
  - Small;
  - Header;
  - Very small.
- Hide media player:
  - в expanded QS;
  - в notifications area;
  - на lockscreen.

Runtime-хук `ShadeCompactMediaHook` использует несколько путей, включая constraint set fallback, чтобы compact media работал даже на R8/обфусцированных сборках SystemUI.

### Shade surface, blur и scrims

Добавлен `ShadeUnifiedSurfaceHook` и UI-настройки:

- Shade blur intensity:
  - 0-400% в UI;
  - поддерживается расширенный input range.
- Shade zoom intensity:
  - -200..400% в UI;
  - позволяет уменьшать или усиливать zoom component blur transition.
- Disable scale threshold:
  - порог, ниже которого scale может принудительно отключаться, чтобы избежать странной микромасштабной анимации.
- Notification scrim alpha override:
  - off/default через `-1`;
  - 0-100% при включении.
- Main scrim alpha override:
  - off/default через `-1`;
  - 0-100% при включении.
- Notification scrim tint:
  - отдельный switch;
  - выбор цвета.
- Main scrim tint:
  - отдельный switch;
  - выбор цвета.

Цель этого блока: сделать shade визуально единым, без разделения QS/notification слоёв с разной альфой/тинтом, которое может проявляться на Android 16 QPR1+ и некоторых ROM variants.

### Lockscreen charging info

Добавлена расширенная charging info на lockscreen:

- Master switch включения.
- Refresh interval 100-5000 ms.
- Average mode.
- Возможность оставить стандартную charging string.
- Custom symbol перед показателями.
- Настраиваемый custom symbol.
- Отдельные показатели:
  - wattage;
  - voltage;
  - current;
  - temperature;
  - percent.
- Чтение battery sysfs paths:
  - current;
  - voltage;
  - temperature.
- Background sampler thread для усреднения.
- Обновление `KeyguardIndicationController.computePowerIndication` через hook.
- Поддержка `_pine`/`_xposed` suffix, включая override для Pine.

Практический результат: на экране блокировки можно видеть реальную мощность зарядки, ток, напряжение и температуру, а не только стандартное "Charging rapidly".

### Notification icon shape

SystemUI notification icons теперь связаны с App Icons:

- Хук читает `icon_map.json`.
- Применяет notification-specific shape/tint/scale настройки.
- Использует reload receiver.
- Цель: иконки уведомлений больше не обязаны жить в дефолтной adaptive shape, если пользователь настроил PixelParts App Icons.

### SystemUI restart UX

- В SystemUI-экранах используется `RebootBubble`.
- Для shade/lockscreen есть restart action, когда настройка требует перезапуска SystemUI.
- Bubble поддерживает дополнительные действия, что используется и в App Icons для clear all.

## Gestures, input и visual hooks

### Wake on doze double tap

- Добавлен/доработан wake on doze double tap hook.
- UI доступен из Gestures/Input и Lockscreen.
- Настраиваемый timeout 300-1000 ms.
- В Xposed mode UI проверяет активность модуля, если Pine override не включён.

### Text magnifier customization

Добавлен раздел настройки text loupe/magnifier:

- Master switch.
- Zoom 0.5x-4.0x.
- Size scale 0.5x-3.0x.
- Shape:
  - default;
  - square;
  - circle.
- Vertical offset -200..200 dp.
- Live preview card с редактируемым sample text.

### Activity transition animations

Добавлен большой раздел кастомизации activity transitions:

- Отдельные настройки open и close transition.
- Режимы:
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
- Добавлен skip для review intents, чтобы transition hook не ломал специальные system/review flows.

### Predictive back control

- В runtime присутствует hook отключения predictive back animation.
- Настройка хранится как runtime-aware `disable_predictive_back_anim`.
- Полезно для пользователей, которым не нравится новый predictive back visual behavior или у кого он конфликтует с кастомными transitions.

## Overscroll Physics

Overscroll остаётся одним из самых продвинутых visual modules PixelExtraParts. Изменения в этом диапазоне включают фиксы и интеграцию с Pine/Xposed runtime.

### Общая логика

- Master switch overscroll engine.
- Separate settings suffix для Pine/Xposed.
- Playground в UI для быстрой визуальной проверки поведения.
- Сохранение профилей в `Settings.Global`.
- Активный профиль хранится отдельно.
- Export/import JSON.
- Импорт/экспорт кросс-runtime: JSON хранит ключи без `_pine`/`_xposed`, а при применении suffix добавляется автоматически.
- Поддержка сетевых preset configs из `overscroll.configs`.
- В репозитории есть готовые пресеты:
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

Эти параметры управляют не только визуальным scale, но и тем, как overscroll набирается, сопротивляется и возвращается назад.

### Visual deformation

Есть три независимые группы визуальной деформации:

- Vertical scale.
- Zoom.
- Horizontal scale.

Для каждой группы доступны:

- Mode:
  - Off;
  - Shrink;
  - Grow.
- Intensity для вертикального gesture.
- Intensity для horizontal gesture.
- Minimum limit.
- Anchor X/Y.
- Отдельные anchors для horizontal path.

### Advanced

- Input smoothing.
- Minimum physics velocity.
- Minimum physics value.
- Lerp idle.
- Lerp run.
- Compose scale.
- Invert anchor.

### Delta normalization

Добавлен/доработан блок нормализации delta для Compose/intelligent smoothing:

- Master switch normalization.
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

- Исправлено сохранение signed overscroll pull distance: направление pull больше не теряется там, где знак важен для корректной физики.
- Hook обёрнут в `EdgeEffectHookWrapper` для единообразной инициализации через Pine/Xposed.

## Thermal Profiles для Tensor/Pixel

Добавлена device-specific thermal integration. Это не generic ROM setting, а PixelExtraParts build/runtime блок для Pixel/Tensor конфигов.

### UI

В PixelExtraParts появился Thermal screen:

- Отдельный выбор режима для Battery.
- Отдельный выбор режима для SoC.
- Режимы:
  - Stock;
  - Soft;
  - Medium;
  - Hard;
  - Off.
- Описание режимов разделено для battery и SoC.
- Настройки пишутся в persistent properties:
  - `persist.sys.pixelparts.battery`;
  - `persist.sys.pixelparts.soc`;
  - `persist.sys.pixelparts.thermal_config`.

### Смысл режимов

- Stock: стандартное поведение thermal config.
- Soft: более мягкое ограничение, примерно +5C к target threshold.
- Medium: примерно +9C.
- Hard: примерно +15C.
- Off: очень высокий threshold, фактически почти отключение thermal throttling policy для выбранного блока.

### Build integration

- Добавлен generator `ThermalConfigs/generate_thermal_configs.py`.
- Генератор берёт vendor thermal config и создаёт варианты JSON.
- Генерируются copy rules для device build.
- Генерируется init rc block для выбора config.
- `device.mk` подключает thermal generation и нужные артефакты.
- Добавлены готовые thermal config variants в `ThermalConfigs/configs/`.

### Важное предупреждение

Thermal Off/Hard режимы потенциально опаснее стандартного поведения: они могут повышать температуру устройства и батареи. Это power-user функция, а не рекомендация для постоянного использования.

## IMS и сеть

Добавлен IMS/CarrierConfig manager для PixelExtraParts.

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

- Настройки хранятся в `Settings.Secure`.
- При изменении переключателя вызывается `ImsManager.updateImsProfile`.
- На boot конфиг может быть применён заново.
- Для каждой активной SIM/subId собирается `PersistableBundle` CarrierConfig overrides.
- Если privileged API доступен, применяется через `CarrierConfigManager.overrideConfig`.
- Если API недоступен и есть root, используется fallback через `cmd phone cc set-value`/`clear-values`.
- Если активные subscription IDs не получены, root fallback пробует стандартные IDs `[1, 2]`.

### Что именно включает

VoLTE:

- carrier volte available;
- editable enhanced 4G LTE;
- enhanced 4G LTE on by default;
- не скрывать enhanced LTE toggle/icon.

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
- enable cross SIM calling on opportunistic data.

VoNR:

- VoNR enabled;
- VoNR setting visibility.

5G:

- NR availabilities;
- optional SSRSRP thresholds.

UT:

- supplementary services over UT.

### Ограничение

IMS toggles не гарантируют поддержку функции оператором. Они открывают/форсируют CarrierConfig flags на стороне устройства, но сеть и SIM всё равно должны поддерживать соответствующую возможность.

## Pine/Xposed runtime и Addons

### System app и Xposed app

PixelExtraParts теперь оформлен как несколько связанных runtime/build targets:

- `PixelCustomPartsSystem`:
  - privileged `system_ext` app;
  - package `org.pixel.customparts`;
  - platform APIs;
  - platform certificate;
  - основная системная UI/настройки часть.
- `PixelCustomPartsXposed`:
  - Xposed APK/test target;
  - package `org.pixel.customparts.xposed`;
  - Xposed API 82;
  - asset `xposed_init`.
- `PineInject`:
  - `java_library` для Pine injection jar;
  - entrypoint `ModEntry` загружает `libpine.so` и запускает `HookEntry.init`.
- Prebuilt/runtime dependencies:
  - `libpine`;
  - Pine/Xposed compatibility jars;
  - aapt/apksig tooling.

### Hook routing

`HookEntry` и `XposedInit` были обновлены под текущий список built-in hooks:

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

### Addon Manager и SDK

Добавлена/расширена система внешних аддонов:

- Аддоны загружаются как DEX JAR из `/data/pixelparts/addons/`.
- Каждый аддон содержит `META-INF/addon.json`.
- Entry class реализует `org.pixel.customparts.core.IAddonHook`.
- Аддон может указывать:
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
  - settings schema для auto-generated UI.
- Поддерживается target package scope: аддон может запускаться только в нужных пакетах.
- Поддерживается auto-generated settings UI по `settings` массиву в manifest.
- Поддерживаются типы настроек: int, float, string, select, file, toggle/switch/checkbox.
- В `example.addon.hook/` добавлены build scripts и prebuild SDK, чтобы собирать простые аддоны без полного Android Studio проекта.

Практический смысл: PixelExtraParts становится не только набором встроенных фич, но и runtime-платформой для дополнительных хуков.

## Build, device integration и OTA tooling

### Soong/device integration

В build layer обновлены/добавлены:

- `Android.bp` targets для system app, Xposed APK, PineInject и prebuilts.
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
- `privapp-permissions-pixelparts.xml` и sepolicy под системные возможности PixelExtraParts.

### Source patcher

Добавлен/обновлён patch infrastructure:

- `patches/apply_patches.py`.
- `patches/config.json`.
- `patches/patchlib.py`.
- Subpatches для framework/settings/launcher related changes.
- `changebe/` хранит reference/modified source snapshots.
- Добавлен source patch launcher.

Это нужно для тех частей, где runtime hook недостаточен или где удобнее поддерживать source-side интеграцию.

## UI/UX polish

### Общий UI стиль

- Экран PixelExtraParts стал более dashboard-like, но остаётся системным settings UI.
- Главный экран сгруппирован по смыслу:
  - Donate;
  - Gestures & Input;
  - System;
  - Network/IMS;
  - Test Things hidden section.
- Используется Jetpack Compose + Material3 + dynamic color.
- На экранах используется edge-to-edge и blur overlay для top bar.
- `RebootBubble` стал общим механизмом быстрых restart/actions.
- Для Xposed-only runtime добавлены предупреждения, если модуль не активен.

### Последние мелкие фиксы из диапазона

- Hidden launcher apps UI был доработан после первичного добавления.
- Display settings получили фиксы insets/imports.
- Saturation tile long press ведёт в settings.
- Auto HBM получил расширенный статус и дополнительные controls.
- App Icons получили серию minor fixes после добавления icon manager.
- Overscroll сохранил signed pull distance.
- Transition hooks пропускают review intents.
- Pine settings lookup стал быстрее.
- Settings resolver учитывает Pine override.
- Документация PixelExtraParts была обновлена под текущую архитектуру.

## Что важно знать пользователю

- Часть функций требует перезапуска Pixel Launcher или SystemUI. UI показывает restart action там, где это нужно.
- App Icons хранят сгенерированные данные в `/data/pixelparts/IconsManager/`; очистка из UI удаляет map/metadata/generated icons.
- Auto HBM и Thermal Profiles работают с низкоуровневыми brightness/thermal механизмами. Это power-user функции.
- IMS toggles зависят от оператора, SIM и сети. PixelExtraParts может выставить CarrierConfig flags, но не может заставить оператора поддерживать отсутствующую услугу.
- Pine/Xposed фичи зависят от активного runtime. В системной сборке основной путь рассчитан на Pine injection, в Xposed APK есть self-check и runtime-specific suffix.
- Addons являются мощным механизмом хуков. Непроверенные аддоны могут ломать target app или SystemUI, поэтому их стоит ставить только из доверенных источников.

## Что не относится к этому changelog

- Общие изменения Evolution X source tree.
- Generic Android security patch notes.
- Kernel/vendor changes, если они не связаны напрямую с PixelExtraParts thermal/build integration.
- Общие GApps/Launcher upstream изменения без PixelExtraParts-specific hook/UI части.
- Тестовые OTA commits, которые меняли только metadata/placeholders и не добавляли пользовательские PixelExtraParts функции.
