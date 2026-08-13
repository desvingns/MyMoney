# Состояние поддержки: бейдж и счётчик
Epic: support-hub-tip
Order: 04 of 08
Status: done
Depends-on: 02
Date: 2026-08-12

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: Появляется `SupporterRepository` — единственный владелец ответа на вопросы «есть ли бейдж» и «сколько раз поддержал». Локальное хранилище — два новых поля `AppSettings`: `supporterBadgeEarned: Boolean` и `supportPurchaseCount: Int`. Успешная покупка (`PurchaseOutcome.Purchased`) инкрементирует счётчик и навсегда взводит бейдж; `Pending` не делает ни того, ни другого. Бейдж не снимается никогда — ни при выходе из аккаунта, ни при отсутствии сети, ни при недоступности биллинга. Слияние с сервером живёт в SPEC-05, здесь описывается только правило слияния, чтобы оно было одно и в одном месте.
LAYERS: domain, data
CHANGED_HINT:
  - core/domain/src/main/java/com/kshavrin/mymoney/core/domain/supporter/SupporterRepository.kt — новый интерфейс: `state(): Flow<SupporterState>`, `recordPurchase(outcome: PurchaseOutcome.Purchased): Result<Unit>`, `mergeRemote(remoteCount: Int, remoteBadge: Boolean): Result<Unit>` (assumption: имена; конвенция размещения — G11)
  - core/domain/src/main/java/com/kshavrin/mymoney/core/domain/supporter/SupporterState.kt — `data class SupporterState(val badgeEarned: Boolean, val purchaseCount: Int)`
  - core/datastore/src/main/java/com/kshavrin/mymoney/core/datastore/model/AppSettings.kt:3-38 — два поля с дефолтами `false` / `0` (G26)
  - core/datastore/src/main/java/com/kshavrin/mymoney/core/datastore/AppSettingsKeys.kt:8-41 — `booleanPreferencesKey` + `intPreferencesKey` (G26)
  - core/datastore/src/main/java/com/kshavrin/mymoney/core/datastore/AppSettingsRepositoryImpl.kt:60-100 — маппинг в `toAppSettings()` и writer (G26)
  - core/datastore/src/main/java/com/kshavrin/mymoney/core/datastore/supporter/SupporterRepositoryImpl.kt — реализация поверх `AppSettingsRepository`
  - core/testing/src/main/kotlin/com/kshavrin/mymoney/core/testing/fake/FakeSupporterRepository.kt — фейк по образцу `FakeCurrencyRepository.kt:9-45` (T1)
TEST_TYPES: unit
CONSTRAINTS:
  - **Правило слияния — `max`, а не «сервер побеждает».** `mergeRemote` берёт `max(local, remote)` по счётчику и `local || remote` по бейджу. Иначе сценарий «купил офлайн, потом вошёл в аккаунт со свежей серверной строкой» обнулит только что совершённую покупку.
  - Бейдж монотонен: ни один путь кода не имеет права записать `badgeEarned = false` поверх `true`. Покрыть отдельным тестом, а не комментарием.
  - `Pending`-покупка счётчик не двигает и бейдж не выдаёт (ADR-0010 D4 + SPEC-03): деньги ещё не списаны.
  - Дефолты новых полей обязаны быть `false`/`0` — существующие установки читают DataStore без этих ключей и не должны падать или внезапно получить бейдж (G26).
  - `:core:domain` и `:core:testing` правятся также в SPEC-02 (раньше) и SPEC-06; `SupporterRepository` делится с SPEC-05 — параллельно не редактировать.
  - Отдельного счётчика в EncryptedSharedPreferences не заводить: данные не секретные, `SecureStorage` (`core/datastore/.../SecureStorage.kt:6-29`) предназначен для токенов и PIN.
=== END SPEC ===

## Acceptance (Gherkin)
```gherkin
Feature: Бейдж и счётчик поддержки

  Scenario: Первая покупка выдаёт бейдж
    Given пользователя без бейджа и со счётчиком 0
    When успешная покупка записана
    Then бейдж взведён
    And счётчик равен 1

  Scenario: Повторные покупки наращивают счётчик
    Given пользователя с бейджем и счётчиком 2
    When записана ещё одна успешная покупка
    Then счётчик равен 3
    And бейдж остаётся взведённым

  Scenario: Покупка в pending ничего не меняет
    Given пользователя без бейджа
    When покупка приходит в состоянии pending
    Then бейдж не взведён
    And счётчик равен 0

  Scenario: Слияние с сервером не теряет локальную покупку
    Given локальный счётчик 3 и взведённый бейдж
    When приходит серверное состояние со счётчиком 1
    Then счётчик равен 3
    And бейдж остаётся взведённым

  Scenario: Слияние поднимает локальное состояние до серверного
    Given чистую установку без бейджа и со счётчиком 0
    When приходит серверное состояние со счётчиком 4
    Then счётчик равен 4
    And бейдж взведён
```

## Gap / context
Ни бейджа, ни счётчика в проекте нет. Раз консьюмнутая покупка из `queryPurchases` не
возвращается (D2), локальное хранилище становится единственным источником истины для
неавторизованного пользователя и первым слагаемым для авторизованного. Правило слияния описано
здесь один раз, чтобы SPEC-05 его использовал, а не изобрёл второе.

## Implementation links
- commit: 4f10deee (production) + 30e265cb (tests and SPEC close-out)
- files: core/datastore/src/main/java/com/kshavrin/mymoney/core/datastore/AppSettingsKeys.kt, core/datastore/src/main/java/com/kshavrin/mymoney/core/datastore/AppSettingsRepositoryImpl.kt, core/datastore/src/main/java/com/kshavrin/mymoney/core/datastore/di/DataStoreModule.kt, core/datastore/src/main/java/com/kshavrin/mymoney/core/datastore/model/AppSettings.kt, core/datastore/src/main/java/com/kshavrin/mymoney/core/datastore/supporter/SupporterRepositoryImpl.kt, core/domain/src/main/kotlin/com/kshavrin/mymoney/core/domain/supporter/SupporterRepository.kt, core/domain/src/main/kotlin/com/kshavrin/mymoney/core/domain/supporter/SupporterState.kt, core/testing/src/main/kotlin/com/kshavrin/mymoney/core/testing/fake/FakeSupporterRepository.kt, core/datastore/src/test/java/com/kshavrin/mymoney/core/datastore/AppSettingsRepositoryTest.kt, core/datastore/src/test/kotlin/com/kshavrin/mymoney/core/datastore/supporter/SupporterRepositoryImplTest.kt
