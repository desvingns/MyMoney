# Android 10/11 support — androidTest dexing with backtick method names
Epic: android-10-11-support
Order: 04 of 04
Status: done
Completed: 2026-09-20
Depends-on: android-10-11-support-01-sdk-floor-api29
Acceptance-matrix: module=app,core_database,core_designsystem,core_datastore,core_network,feature_lockscreen,core_testing; task=assembleDebugAndroidTest
Risk-signals: build-config, test-infrastructure
Date: 2026-09-20

## SPEC
=== SPEC ===
TASK: bugfix
PLATFORM: android
WHAT: После снижения minSdk до 29 (SPEC-01) сборка ВСЕХ инструментальных тестов падает в D8: «Space characters in SimpleName … are not allowed prior to DEX version 040» — тесты и контрактные классы с именами методов в обратных кавычках (`fun \`some name\``) требуют DEX 040 (API 30+). Восстановить сборку `assembleDebugAndroidTest`/`connectedDebugAndroidTest` для всех модулей, не поднимая minSdk приложения и библиотек выше 29.
LAYERS: platform | test
CHANGED_HINT:
  - build-logic/src/main/kotlin/com/kshavrin/mymoney/buildlogic/MymoneyConventionPlugins.kt (application, library и test-плагины) — ПРЕДПОЧТИТЕЛЬНЫЙ вариант A: поднять minSdk ТОЛЬКО для компонента androidTest до 30 через Variant API AGP 8.7.3 (`androidComponents.onVariants { … androidTest … }`), оставив `defaultConfig.minSdk = 29` для app/library; проверить эмпирически, что dexing зависимостей androidTest (в т.ч. `:core:testing`) тоже идёт с minSdk 30 (assumption: возможность зависит от API AGP 8.7.3 — спайк обязателен)
  - ВАРИАНТ B (если A недостижим в AGP 8.7.3): скриптом переименовать все методы в обратных кавычках в camelCase в `**/src/androidTest/**` (≈600 функций в app, core:database, core:designsystem, core:network, feature:lockscreen) и в `core/testing/src/main/kotlin/**` (контрактные классы, попадают в classpath androidTest); без потери семантики и без коллизий имён; JVM-тесты (`src/test`) переименовывать НЕ нужно
  - build-logic/src/test/kotlin/com/kshavrin/mymoney/buildlogic/MymoneyConventionPluginsTest.kt и MymoneyBuildConfigurationContractTest.kt — сохранить утверждения minSdk = 29 для app/library/test; добавить проверку выбранного решения (для A: androidTest-minSdk = 30 не дублируется в модулях; для B: в androidTest-исходниках нет методов в обратных кавычках)
  - docs/ANDROID_SUPPORT_MATRIX.md — одна строка: инструментальные тесты собираются/запускаются на API 30+ (устройство-шлюз Pixel 5/API 34), продуктовый minSdk остаётся 29
TEST_TYPES: contract | build | static-analysis
CONSTRAINTS:
  - `defaultConfig.minSdk` приложения и библиотек остаётся 29 (SPEC-01); модульных переопределений SDK не вводить — единый источник в convention plugins и version catalog.
  - Не удалять и не ослаблять тесты; не помечать `@Ignore`; коммитить только свои файлы (в дереве есть чужие незакоммиченные правки: core/designsystem form-файлы, Spacing.kt, docs/implementation_plan/*, feature/transaction AddExpenseScreen.kt; файл `core/designsystem/src/androidTest/.../form/TransactionFormContentUiTest.kt` помечен грязным — при варианте B переименовывать его только через явный `git add` своих hunk'ов не нужно: пропустить и вернуть в отчёте).
  - Критерий готовности: `./gradlew assembleDebugAndroidTest` зелёный для всех модулей; один существующий connected-тест (например `:core:designsystem` SpotlightOverlayUiTest) проходит на Pixel_5 API 34.
=== END SPEC ===

## Acceptance (Gherkin)
```gherkin
Feature: Инструментальные тесты собираются при minSdk 29

  Scenario: Сборка androidTest всех модулей
    Given minSdk продукта равен 29
    When выполняется assembleDebugAndroidTest
    Then сборка завершается без ошибок D8 про пробелы в именах

  Scenario: Продуктовый minSdk не изменился
    Given исправление применено
    Then приложение и библиотеки по-прежнему объявляют minSdk 29
    And compileSdk и targetSdk равны 36
```

## Gap / context
Обнаружено при реализации onboarding-spotlight-tour-01: SPEC-01 снизил minSdk до 29, а runner/lint эту ошибку не ловят (они не dex-ят androidTest). Проверено: `:core:database:assembleDebugAndroidTest` падает на `core/testing` контрактных классах.

## Implementation links
- commit: 645f223f
- files: 539 backtick androidTest methods renamed across 58 files + core/testing contracts, build-logic contract test, docs/ANDROID_SUPPORT_MATRIX.md
