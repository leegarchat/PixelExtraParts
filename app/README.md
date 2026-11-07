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

## Версии toolchain / deps (Gradle-сторона, newest-first)

| Компонент | Версия | Примечание |
| :--- | :--- | :--- |
| Gradle | 9.7.1 | запуск напрямую, `gradle-9.7.1/bin/gradle` |
| JDK для запуска Gradle | 25 (`/home/leegarboot/jdk25`) | newest-first держится, fallback 21 не понадобился |
| Java source/target, KGP jvmTarget | 21 | `compileOptions` + `kotlin.compilerOptions` |
| AGP | 9.5.1 | newest stable; built-in Kotlin — `kotlin-android` удалён, KGP 2.4.21 через `buildscript` classpath |
| KGP / compose-compiler | 2.4.21 | newest stable |
| Compose BOM | 2026.09.00 | тянет compose 1.12.1, material3 1.4.0 |
| activity-compose | 1.14.0-alpha03 | newest ≥ floor 1.14.0-alpha01 |
| core-ktx | 1.19.1 | floor 1.20.0-alpha01 нет в public maven (404) — взят newest доступный |
| compileSdk / targetSdk | 37 | платформа `android-37.2` + симлинк `android-37` в `/opt/android-sdk/platforms` |
| minSdk | 26 | пол ROM, не понижать |

Soong-зеркало (`Android.bp`, `device.mk`) — за координатором; таблица выше —
исходник версий для него.

Правило: `package_name` / `applicationId` / `app_name` менять только после
подтверждения (ворота идентичности §4 agent-instructions).
