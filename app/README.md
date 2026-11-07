# app/ — приложение Pixel Extra Parts (`org.pixel.extra.parts`)

Пустой каркас по дизайн-гайдлайну: `BottomBlurBar` + `ExpressiveFloatingDock`
(тап и свайп-скраб), `TopBarBlurOverlay` + `TopStatusVeil`, две вкладки-заглушки.

## Две сборки, один код

- **Debug (быстрая проверка UI):** system Gradle из корня проекта:
  `gradle :app:assembleDebug`. Манифест `src/main/AndroidManifest.xml`
  (без `sharedUserId` — ставится на обычное устройство).
- **Прошивка:** Soong-модуль `PixelExtraParts` в корневом `Android.bp`
  (зеркало Gradle по §13.4 гайдлайна): `package_name` обязан совпадать
  с `applicationId`, версии держать синхронно в трёх местах —
  `app/build.gradle.kts` + `src/firmware/AndroidManifest.xml`.
  Подпись `platform`, `system_ext` priv-app, `PRODUCT_PACKAGES` в `device.mk`.

## Раскладка

- `src/main/AndroidManifest.xml` — debug-манифест (LAUNCHER, минимум прав).
- `src/firmware/AndroidManifest.xml` — прошивочный манифест: полный carte-blanche
  из прошлого проекта (187 разрешений) + `sharedUserId="android.uid.system"`.
- `src/main/java/org/pixel/extra/parts/` — `MainActivity` (корень) + `Root`;
  `ui/` — UI-кит (`Theme`, `BlurComponents`, `ExpressiveFloatingDock`,
  `PlaceholderCard`); `home/`, `settings/` — экраны-заглушки.
- `src/main/res/` — строки строго через ресурсы, все 8 локалей сразу
  (`values`, `de/es/fr/pt/ru/uk/zh-rCN`); иконки — векторы в `drawable/`,
  адаптивный лаунчер в `mipmap-anydpi-v26/`.
- `privapp-permissions-extra-parts.xml` — whitelist привилегированных прав
  (сгенерирован из списка манифеста, пакет `org.pixel.extra.parts`).

Правило: `package_name` / `applicationId` / `app_name` менять только после
подтверждения (ворота идентичности §4 agent-instructions).
