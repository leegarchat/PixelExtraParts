# app/ — слот под будущий АПК PixelCustomParts

Сюда вернётся АПК (бывшие `common/`, `system/`, `lang/` + `AndroidManifest.xml`):

- `app/common/` — общие ресурсы/исходники (`res/`, `src/`, `lib/`)
- `app/system/` — `AndroidManifest.xml` + системные исходники
- `app/lang/` — строки локализации

Правила:
- Модуль `PixelCustomPartsSystem` (`android_app`) описывать в `app/app.bp`,
  пути `srcs`/`manifest` — относительно `app/`.
- В `device.mk` добавить его в `PRODUCT_PACKAGES` отдельным блоком `# APK`.
- Приватные разрешения (`privapp-permissions`) — тоже сюда.
