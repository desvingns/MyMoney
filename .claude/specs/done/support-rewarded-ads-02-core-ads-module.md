# Каркас :core:ads — зависимости AdMob/UMP, AD_ID, BuildConfig-флаги
Epic: support-rewarded-ads
Order: 02 of 06
Status: done
Depends-on: —
Date: 2026-08-12

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: В проекте появляется модуль :core:ads — пока пустой по логике, но полностью обвязанный: подключён в settings.gradle.kts, зависимости play-services-ads и user-messaging-platform заведены в каталоге версий, в манифест приходит com.google.android.gms.permission.AD_ID и meta-data APPLICATION_ID, а ad unit id и переключатель рекламы выдаются через BuildConfig так же, как :core:network выдаёт supabase-ключи. Debug-сборка получает тестовые ad unit id Google, release — боевые из gradle-свойства. Сборка проекта зелёная, поведение приложения не меняется.
LAYERS: build, data
CHANGED_HINT:
  - settings.gradle.kts:44-51 — `include(":core:ads")` в блок объявления модулей (G6)
  - gradle/libs.versions.toml — версии + библиотеки `play-services-ads` и `user-messaging-platform` в `[versions]`/`[libraries]`; литеральных версий в build-файлах быть не должно (G8)
  - core/ads/build.gradle.kts — НОВЫЙ; зеркалить `core/network/build.gradle.kts` (G41): `alias(libs.plugins.mymoney.android.library)` + ksp + hilt, `namespace = "com.kshavrin.mymoney.core.ads"`, `buildFeatures { buildConfig = true }`
  - core/ads/build.gradle.kts — `buildConfigField` из gradle-свойств с fallback на `local.properties`, по образцу supabase-ключей (G41): `ADS_ENABLED` (debug=false, release=true, свойство `ads.enabled`), `ADMOB_REWARDED_UNIT_ID` (в debug — тестовый id Google `ca-app-pub-3940256099942544/5224354917`, в release — из `admob.rewardedUnitId`, иначе плейсхолдер)
  - core/ads/src/main/AndroidManifest.xml — НОВЫЙ; `<uses-permission android:name="com.google.android.gms.permission.AD_ID" />` и `<meta-data android:name="com.google.android.gms.ads.APPLICATION_ID" android:value="${admobApplicationId}" />` (G29; assumption: имя плейсхолдера)
  - core/ads/build.gradle.kts — `manifestPlaceholders["admobApplicationId"]` из gradle-свойства `admob.applicationId`, в debug — тестовый app id Google (assumption O3: APPLICATION_ID не может быть BuildConfig-полем)
  - app/src/main/AndroidManifest.xml:5-9 — permission приезжает мержем манифестов; проверить итоговый merged manifest, НЕ дублировать объявление вручную (G9, G29)
  - gradle.properties — задокументированные пустые дефолты `admob.applicationId` / `admob.rewardedUnitId` / `ads.enabled` (assumption: место дефолтов, зеркалит sync-свойства `app/build.gradle.kts:69-73`)
  - app/src/test/java/com/kshavrin/mymoney/ConnectedModulesCiContractTest.kt:15-31 — обновить ТОЛЬКО если для `:core:ads` заводится connected-задача в CI (G34)
  - build.gradle.kts:22-33 — порог Kover для `:core:ads` заводить только если решено; в текущем списке нет `:core:network`, `:core:ui`, `:core:sync` (G42)
TEST_TYPES: unit
CONSTRAINTS:
  - Модуль **не** содержит логики показа рекламы — она в SPEC 03; здесь только обвязка. Это
    осознанно «пустой» SPEC, чтобы диффы 02 и 03 читались отдельно.
  - `core/ads/build.gradle.kts` правится также в SPEC 03 — этот SPEC первый, параллельно нельзя.
  - Никаких product flavours (ADR-0010 D6, G24) — только gradle-свойства + buildTypes.
  - SDK **не инициализируется** на старте приложения: ни в `Application.onCreate`, ни через
    `androidx.startup`. Инициализация ленивая, из SPEC 03 (D5: попытка только по открытию блока).
  - Боевые `applicationId` / ad unit id в репозиторий не коммитятся — только плейсхолдеры и
    `local.properties`-fallback, как у supabase-ключей (G41).
  - detekt `maxIssues: 0` (G36); `mp-runner` даёт ложные «ok» по detekt и не гоняет ktlint/kover —
    проверять полным прогоном (G37).
  - Обязательно приложить merged manifest release-варианта как доказательство, что `AD_ID`
    действительно приехал, а не только объявлен в модуле.
=== END SPEC ===

## Acceptance (Gherkin)
```gherkin
Feature: Модуль :core:ads подключён и настроен

  Scenario: Проект собирается с новым модулем
    Given модуль :core:ads объявлен в settings.gradle.kts
    When собирается debug и release
    Then обе сборки успешны и поведение приложения не изменилось

  Scenario: Разрешение AD_ID есть в итоговом манифесте
    Given release-вариант собран
    When проверяется merged manifest
    Then он содержит com.google.android.gms.permission.AD_ID
    And он содержит meta-data com.google.android.gms.ads.APPLICATION_ID

  Scenario: Debug использует тестовые идентификаторы
    Given debug-сборка без заданных gradle-свойств AdMob
    When читается BuildConfig модуля :core:ads
    Then ad unit id равен тестовому идентификатору Google
    And ADS_ENABLED равен false

  Scenario: Release берёт боевые идентификаторы из свойства
    Given release-сборка с заданным admob.rewardedUnitId
    When читается BuildConfig модуля :core:ads
    Then ad unit id равен значению из свойства

  Scenario: Реклама не инициализируется на старте
    Given приложение запускается
    When пользователь не открывал раздел поддержки
    Then AdMob SDK не инициализирован
```

## Gap / context
Модулей `:core:ads` и `:core:billing` в проекте нет, зависимостей AdMob в каталоге версий тоже нет
(G2). ADR-0010 D7 (G25) закрепляет состав `:core:ads`, а D6 (G24) — что переключение идёт флагом
BuildConfig без product flavours. Этот SPEC ставит каркас, чтобы SPEC 03 писал только логику.

## Implementation links
- commit: 1cc54df2, 0f39e8e3, baf72e5f
- files: app/build.gradle.kts; core/ads/build.gradle.kts; core/ads/src/main/AndroidManifest.xml; gradle.properties; gradle/libs.versions.toml; settings.gradle.kts; app/src/test/java/com/kshavrin/mymoney/CoreAdsWiringContractTest.kt; build-logic/src/test/kotlin/com/kshavrin/mymoney/buildlogic/MymoneyBuildConfigurationContractTest.kt
