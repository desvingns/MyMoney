# Модуль :core:billing — реализация на Google Play Billing
Epic: support-hub-tip
Order: 03 of 08
Status: done
Depends-on: 02
Date: 2026-08-12

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: Появляется модуль `:core:billing` с единственной реализацией `BillingGateway` поверх Google Play Billing: подключение клиента, `queryProductDetails` для `coffee_small`/`coffee_large`, запуск покупки, `acknowledge` + немедленный `consume` (ADR-0010 D4 — товары повторяемы), обработка `PENDING`, и `queryPurchases` **исключительно** для дозакрытия незавершённых покупок при старте. Биллинг выключается в debug-сборке флагом `BuildConfig.BILLING_ENABLED` (Gradle-property `billing.enabled`, default `false`) — при выключенном флаге реализация не трогает Play SDK вообще и отдаёт `BillingAvailability.DisabledInBuild`, чтобы Roborazzi-эталоны и JVM-тесты не зависели от наличия Play Services.
LAYERS: data, build
CHANGED_HINT:
  - settings.gradle.kts:34-52 — `include(":core:billing")` (G10, G12)
  - gradle/libs.versions.toml — `billing-ktx` в `[versions]`+`[libraries]`; сейчас его нет (G15)
  - core/billing/build.gradle.kts — новый модуль по образцу core-модуля; `buildConfigField("boolean", "BILLING_ENABLED", …)` из property `billing.enabled` по образцу `core/sync/build.gradle.kts:18-27` (G16, D7)
  - app/build.gradle.kts:319-326 — тот же `buildConfigField` в `defaultConfig` по образцу `PLAY_INTERNAL_SYNC_ENABLED` (`app/build.gradle.kts:69-73`) (G9b, D7)
  - app/build.gradle.kts:400-406 — `implementation(project(":core:billing"))` (G12)
  - core/billing/src/main/java/com/kshavrin/mymoney/core/billing/PlayBillingGateway.kt — реализация `BillingGateway` (SPEC-02)
  - core/billing/src/main/java/com/kshavrin/mymoney/core/billing/di/BillingModule.kt — `@Module @InstallIn(SingletonComponent::class)`, `@Singleton` биндинг SDK-обёртки, `@Named` диспетчер вместо прямого `Dispatchers.IO` (`AGENTS.md`, раздел «Hilt DI conventions»)
  - .github/workflows/ci.yml:76, 214 — добавить `-Pbilling.enabled=true` в release-сборку рядом с `$FIREBASE_ARGS` (assumption: точная строка задачи уточняется по месту)
TEST_TYPES: unit
CONSTRAINTS:
  - **`queryPurchases` НЕ является источником бейджа.** Консьюмнутый consumable Play в него не возвращает, поэтому единственное его назначение здесь — найти незавершённые/`PENDING` покупки и довести их до `acknowledge`+`consume`. Любая логика вида «нашли покупку в queryPurchases → выдали бейдж» противоречит D2 и будет работать только до первого консьюма.
  - Консьюмить **сразу после** acknowledge, иначе повторная покупка того же SKU вернёт `ITEM_ALREADY_OWNED` и кнопка перестанет работать (ADR-0010 D4).
  - Различать `BILLING_UNAVAILABLE`/`SERVICE_UNAVAILABLE`/недоступность в регионе и мапить их в разные `BillingAvailability`; в частности недоступность в регионе для пользователей из России — **постоянное состояние**, а не редкая ошибка (ADR-0010:132-144), и мапиться в `Unavailable`-исключение оно не должно.
  - `USER_CANCELED` — это `PurchaseOutcome.Cancelled`, не ошибка: в Sentry не отправлять, баннер ошибки не показывать.
  - Модуль **не добавлять** в `ConnectedModulesCiContractTest.kt:22-28` — инструментальных тестов у него нет (T7). Добавление туда без реального connected-набора сломает CI.
  - `gradle/libs.versions.toml` и `app/build.gradle.kts` правятся также в SPEC-06 и SPEC-07 — этот первый, остальные идут строго после.
  - Дефолт `billing.enabled=false` означает, что локальная сборка и весь JVM-прогон видят `DisabledInBuild`. Тесты не должны требовать `true`.
=== END SPEC ===

## Acceptance (Gherkin)
```gherkin
Feature: Реализация покупок на Google Play Billing

  Scenario: Биллинг выключен в сборке
    Given сборка собрана без -Pbilling.enabled=true
    When экран запрашивает доступность биллинга
    Then возвращается DisabledInBuild
    And Play Billing SDK не инициализируется

  Scenario: Покупка консьюмится и может быть повторена
    Given успешно завершённую покупку coffee_small
    When обработка покупки завершена
    Then покупка подтверждена и потреблена
    And повторный запуск покупки того же товара снова доступен

  Scenario: Незавершённая покупка дозакрывается при старте
    Given покупку, оставшуюся неподтверждённой после прошлого запуска
    When приложение стартует и разрешает незавершённые покупки
    Then покупка подтверждается и потребляется
    And результат отдаётся как Purchased

  Scenario: Покупка в состоянии pending
    Given покупку, которую Play вернул как PENDING
    When обрабатывается результат
    Then отдаётся Pending
    And бейдж не выдаётся до подтверждения

  Scenario: Отмена пользователем не является ошибкой
    Given пользователя, закрывшего диалог оплаты
    When обрабатывается результат
    Then отдаётся Cancelled
    And событие не уходит в Sentry
```

## Gap / context
`:core:billing` описан в AGENTS.md и ADR-0010 D7, но на диске отсутствует (G10), а `billing-ktx`
нет в каталоге версий (G15). Это единственное место в эпике, где живёт Google Play SDK: всё
остальное — экран, состояние, тесты — работает через доменный контракт из SPEC-02.

## Implementation links
- commits: `45cc52e3`, `06842106`, `06feceb7`, `50f2ec44`, `d0c344f2`
- files: `.github/workflows/ci.yml`, `app/build.gradle.kts`, `core/billing/`, `core/domain/src/main/kotlin/com/kshavrin/mymoney/core/domain/billing/BillingAvailability.kt`, `gradle/libs.versions.toml`, `settings.gradle.kts`, and the related CI/unit contract tests
- verification: reviewer `0` violations; semantic reviewer and independent critic passed; full runner `2034 passed / 0 failed / 0 skipped`, detekt/lint `ok`; full verifier passed
- scope note: startup reconciliation remains an explicit gateway API for SPEC-04/05, where the reward consumer and durable supporter state exist; SPEC-03 does not silently consume and discard `Purchased` outcomes
- external prerequisite: a real Play Internal test with `billing.enabled=true` is still required before enabling production SKUs
