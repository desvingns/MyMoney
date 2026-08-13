# Аналитика: AnalyticsGateway и реализация на Firebase Analytics
Epic: support-hub-tip
Order: 06 of 08
Status: done
Depends-on: —
Date: 2026-08-12

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: У проекта впервые появляется аналитика. В `:core:domain` вводится интерфейс `AnalyticsGateway` с типизированными событиями (никаких сырых строк на местах вызова), в data-слое — реализация на Firebase Analytics, включающаяся только при `BuildConfig.HAS_FIREBASE`; иначе подставляется no-op. Событий в этом эпике ровно три: открытие раздела поддержки, начало покупки, завершение покупки (с исходом). Инфраструктура Firebase в проекте уже готова — CI материализует `google-services.json` из секрета и передаёт `-Pfirebase.enabled=true`; не хватает только артефакта `firebase-analytics` и самой абстракции.
LAYERS: domain, data, build
CHANGED_HINT:
  - core/domain/src/main/java/com/kshavrin/mymoney/core/domain/analytics/AnalyticsGateway.kt — `fun log(event: AnalyticsEvent)`; `sealed interface AnalyticsEvent` с `SupportOpened`, `SupportPurchaseStarted(productId)`, `SupportPurchaseCompleted(productId, outcome)` (G19 — абстракции нет, вводится с нуля)
  - gradle/libs.versions.toml:39,135 — добавить `firebase-analytics` рядом с существующим `firebase-config-ktx`; BOM уже есть (G15)
  - core/sync/build.gradle.kts:18-27 — зависимость от `firebase-analytics` под тем же условием, что и Remote Config (G16)
  - core/sync/src/main/java/com/kshavrin/mymoney/core/sync/analytics/FirebaseAnalyticsGateway.kt — реализация; ленивое получение SDK по образцу `RemoteConfigRepositoryImpl.kt:18-56` (G17)
  - core/sync/src/main/java/com/kshavrin/mymoney/core/sync/analytics/NoOpAnalyticsGateway.kt + di-модуль, выбирающий реализацию по `BuildConfig.HAS_FIREBASE` (G16, G17)
  - core/testing/src/main/kotlin/com/kshavrin/mymoney/core/testing/fake/FakeAnalyticsGateway.kt — фейк, копящий события списком (T1)
TEST_TYPES: unit
CONSTRAINTS:
  - **Домен не знает про Google** — то же правило, что для биллинга (SPEC-02): ни одного импорта `com.google.firebase.*` в `:core:domain`. Проверяется тестом границы, а не ревью.
  - При `HAS_FIREBASE == false` не должно происходить ни одного обращения к Firebase-классам — иначе локальная сборка и JVM-прогон упадут на отсутствующем `google-services.json` (G16, G17).
  - **`google-services.json` в `.gitignore:29` — не коммитить его.** CI материализует файл из секрета `GOOGLE_SERVICES_JSON` и только тогда включает `-Pfirebase.enabled=true` (`.github/workflows/ci.yml:61-72, 201-212`). Пока секрет не задан (OQ-9), аналитика молча не работает при зелёной сборке — это ожидаемое поведение, а не регресс.
  - **Побочный эффект, который нельзя замолчать:** включение Firebase ради Analytics одновременно включает Remote Config — оба сидят на одном `HAS_FIREBASE`. Именно поэтому SPEC-08 переписывает строку политики про Firebase, а не просто добавляет абзац про покупки.
  - В события не класть ни сумм, ни валют, ни идентификаторов пользователя — только идентификатор товара и исход. Финансовые данные в аналитику не уходят вообще (это же утверждается в тексте политики из SPEC-08).
  - `gradle/libs.versions.toml` и `app/build.gradle.kts` делятся со SPEC-03 (раньше) и SPEC-07 (позже) — параллельно не редактировать.
=== END SPEC ===

## Acceptance (Gherkin)
```gherkin
Feature: Аналитика раздела поддержки

  Scenario: Домен не знает про Firebase
    Given исходники пакета core/domain/.../analytics
    When тест границы сканирует их импорты
    Then ни одного импорта com.google.firebase не найдено

  Scenario: Без Firebase аналитика молчит
    Given сборку с выключенным Firebase
    When логируется событие открытия раздела поддержки
    Then обращения к Firebase SDK не происходит
    And вызов завершается без ошибки

  Scenario: Три события эпика логируются
    Given фейковую аналитику
    When пользователь открывает раздел, начинает и завершает покупку
    Then записано событие открытия раздела
    And записано событие начала покупки с идентификатором товара
    And записано событие завершения покупки с исходом

  Scenario: Финансовые данные в события не попадают
    Given записанные события покупки
    When проверяются их параметры
    Then среди параметров нет сумм, валют и идентификатора пользователя
```

## Gap / context
Аналитической абстракции в проекте нет вообще — из телеметрии только Sentry (G19), а
`firebase-analytics` отсутствует в каталоге версий, хотя BOM и Remote Config уже подключены
(G15). Абстракция вводится раньше экрана, чтобы SPEC-07 логировал события через доменный
интерфейс и тестировался фейком, а не Firebase-эмулятором.

## Implementation links
- commit: 24e17bac, 3ced475e
- files: core/domain/src/main/kotlin/com/kshavrin/mymoney/core/domain/analytics/AnalyticsGateway.kt; core/sync/build.gradle.kts; core/sync/src/main/java/com/kshavrin/mymoney/core/sync/analytics/FirebaseAnalyticsGateway.kt; core/sync/src/main/java/com/kshavrin/mymoney/core/sync/analytics/NoOpAnalyticsGateway.kt; core/sync/src/main/java/com/kshavrin/mymoney/core/sync/di/AnalyticsModule.kt; core/testing/src/main/kotlin/com/kshavrin/mymoney/core/testing/fake/FakeAnalyticsGateway.kt; core/testing/src/test/kotlin/com/kshavrin/mymoney/core/testing/fake/FakeAnalyticsGatewayTest.kt; gradle/libs.versions.toml
