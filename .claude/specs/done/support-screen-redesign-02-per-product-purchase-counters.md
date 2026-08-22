# Раздельные счётчики покупок кофе (мл./бл.)
Epic: support-screen-redesign
Order: 02 of 07
Status: done
Depends-on: —
Date: 2026-08-20
Acceptance-matrix: purchase=small,large,unknown_product,duplicate_token; migration=fresh_install,existing_count
Risk-signals: data-migration, persistence, sync

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: `SupporterState` перестаёт знать только суммарное число покупок и начинает различать
покупки маленького и большого кофе. В `AppSettings` добавляются два Int-поля, `recordPurchase`
инкрементирует нужное по `PurchaseOutcome.Purchased.productId` (G34), а при первом чтении
настроек накопленный исторический `supportPurchaseCount` однократно бэкфиллится в счётчик
маленького кофе (D11), чтобы сумма раздельных счётчиков сходилась с итогом. Суммарный
`supportPurchaseCount` сохраняется как есть и продолжает синхронизироваться через `mergeRemote`;
раздельные счётчики остаются локальными (D12) — контракт `mergeRemote` и схема Supabase не
меняются. UI в этом SPEC не трогается.
LAYERS: domain, data
CHANGED_HINT:
  - core/domain/src/main/kotlin/com/kshavrin/mymoney/core/domain/supporter/SupporterState.kt:3-6 — добавить `smallCoffeeCount: Int` и `largeCoffeeCount: Int` рядом с `purchaseCount`; `badgeEarned` и `purchaseCount` не трогать (G33)
  - core/datastore/src/main/java/com/kshavrin/mymoney/core/datastore/model/AppSettings.kt:21-23 — два новых поля `supportPurchaseCountSmall: Int = 0`, `supportPurchaseCountLarge: Int = 0` + флаг завершённого бэкфилла `supportPurchaseSplitBackfilled: Boolean = false` (G35)
  - core/datastore/src/main/java/com/kshavrin/mymoney/core/datastore/AppSettingsRepositoryImpl.kt:54-56,63-65,93-95,137-139 — новые `AppSettingsKeys` + чтение/запись новых полей во ВСЕХ четырёх точках маппинга (пропуск любой из них даёт молча теряющиеся настройки, G35)
  - core/datastore/src/main/java/com/kshavrin/mymoney/core/datastore/supporter/SupporterRepositoryImpl.kt:28-40 — в `recordPurchase` внутри существующей проверки дедупликации по `purchaseToken` инкрементировать нужный счётчик по `outcome.productId`; маппинг id → счётчик; неизвестный productId увеличивает только общий `supportPurchaseCount` (G33, G34)
  - core/datastore/src/main/java/com/kshavrin/mymoney/core/datastore/supporter/SupporterRepositoryImpl.kt:19-26 — в `state()` отдавать новые поля; выполнить одноразовый бэкфилл (`supportPurchaseSplitBackfilled == false` → `small = max(small, supportPurchaseCount)`, флаг в true) в записи, а не в маппинге чтения (D11)
  - core/testing/src/main/kotlin/com/kshavrin/mymoney/core/testing/fake/FakeSupporterRepository.kt — обновить фейк под новый `SupporterState` (обязательно: он реализует изменившийся контракт)
  - core/datastore/src/test/kotlin/com/kshavrin/mymoney/core/datastore/supporter/SupporterRepositoryImplTest.kt — тесты на инкремент по productId, дедупликацию по токену, неизвестный productId и одноразовость бэкфилла
  - core/datastore/src/test/java/com/kshavrin/mymoney/core/datastore/AppSettingsRepositoryTest.kt — round-trip новых ключей
TEST_TYPES: unit
CONSTRAINTS:
  - Идентификаторы продуктов уже объявлены как `COFFEE_SMALL_PRODUCT_ID`/`COFFEE_LARGE_PRODUCT_ID`
    в `feature/support/.../SupportState.kt:47-48` и помечены `internal` — модуль данных их НЕ
    видит. Не дублировать строковые литералы в `core:datastore`: поднять константы в
    `:core:domain` (рядом с `SupportProduct`) и переиспользовать в обоих местах.
  - Бэкфилл выполняется РОВНО один раз (флаг `supportPurchaseSplitBackfilled`), иначе каждое
    чтение будет заново перезаписывать счётчик маленького кофе поверх реальных покупок.
  - Инвариант монотонности `supporterBadgeEarned` не нарушать: `AppSettingsRepositoryImpl.kt:42-43`
    бросает `IllegalStateException` на откат true→false (G35). Новые счётчики тоже не должны
    уменьшаться — тест на это обязателен.
  - `mergeRemote(remoteCount, remoteBadge)` НЕ трогать: подпись, поведение и схема Supabase
    остаются прежними (D12). Раздельные счётчики удалённо не сливаются.
  - Дедупликация покупок по `purchaseToken` сохраняется: повторный `recordPurchase` с тем же
    токеном не должен увеличивать ни один счётчик (G35).
  - Fakes, не MockK: `FakeSupporterRepository` — единственный тестовый дублёр.
  - Изменение `SupporterState` — публичный доменный контракт: пройтись по ВСЕМ его потребителям
    (`core/sync/.../SupporterSyncImpl.kt`, `SupportPurchaseReconciliationCoordinatorImpl.kt`,
    `feature/support`), убедиться, что новые поля не ломают компиляцию и существующие тесты.
=== END SPEC ===

## Acceptance (Gherkin)
```gherkin
Feature: Раздельный учёт покупок кофе

  Scenario: Покупка маленького кофе увеличивает счётчик маленького кофе
    Given пользователь не совершал покупок
    When покупка маленького кофе подтверждена
    Then счётчик маленького кофе равен одному
    And счётчик большого кофе равен нулю
    And суммарное число покупок равно одному

  Scenario: Покупка большого кофе увеличивает счётчик большого кофе
    Given пользователь купил один маленький кофе
    When покупка большого кофе подтверждена
    Then счётчик большого кофе равен одному
    And суммарное число покупок равно двум

  Scenario: Повторная обработка того же токена ничего не увеличивает
    Given покупка с данным токеном уже учтена
    When та же покупка обрабатывается повторно
    Then все три счётчика остаются прежними

  Scenario: Неизвестный продукт учитывается только в общем счётчике
    Given обработана покупка с неизвестным идентификатором продукта
    Then суммарное число покупок увеличивается
    And счётчики маленького и большого кофе не меняются

  Scenario: Исторические покупки однократно переносятся в маленький кофе
    Given у пользователя накоплено три покупки без разбивки по продуктам
    When настройки читаются впервые после обновления
    Then счётчик маленького кофе равен трём
    And при следующих чтениях счётчик маленького кофе больше не переписывается

  Scenario: Чистая установка не выполняет бэкфилл
    Given приложение установлено впервые
    Then все три счётчика равны нулю
```

## Gap / context
Карточка благодарности из макета показывает три счётчика («реклама», «мл. кофе», «бл. кофе»), но
данных под два последних нет: хранится только суммарный `supportPurchaseCount`, а исторические
покупки записаны токенами без `productId` (G35, H2). Этот SPEC закрывает разрыв в данных до того,
как SPEC-04 добавит счётчики в `SupportState`, а SPEC-07 — в вёрстку.

## Implementation links
- commit: 662b8230, c2187cac, d660c9b2, e8ae3dfe, dbcc2800
- files:
  - core/billing/src/main/java/com/kshavrin/mymoney/core/billing/PlayBillingGateway.kt
  - core/datastore/src/main/java/com/kshavrin/mymoney/core/datastore/AppSettingsKeys.kt
  - core/datastore/src/main/java/com/kshavrin/mymoney/core/datastore/AppSettingsRepositoryImpl.kt
  - core/datastore/src/main/java/com/kshavrin/mymoney/core/datastore/model/AppSettings.kt
  - core/datastore/src/main/java/com/kshavrin/mymoney/core/datastore/di/DataStoreModule.kt
  - core/datastore/src/main/java/com/kshavrin/mymoney/core/datastore/supporter/SupporterPurchaseStore.kt
  - core/datastore/src/main/java/com/kshavrin/mymoney/core/datastore/supporter/SupporterRepositoryImpl.kt
  - core/datastore/src/test/java/com/kshavrin/mymoney/core/datastore/AppSettingsRepositoryTest.kt
  - core/datastore/src/test/kotlin/com/kshavrin/mymoney/core/datastore/supporter/SupporterRepositoryImplTest.kt
  - core/domain/src/main/kotlin/com/kshavrin/mymoney/core/domain/billing/SupportProduct.kt
  - core/domain/src/main/kotlin/com/kshavrin/mymoney/core/domain/supporter/SupporterState.kt
  - core/domain/src/main/kotlin/com/kshavrin/mymoney/core/domain/supporter/SupporterRepository.kt
  - core/domain/src/main/kotlin/com/kshavrin/mymoney/core/domain/usecase/ObserveSupporterStateUseCase.kt
  - core/domain/src/test/kotlin/com/kshavrin/mymoney/core/domain/usecase/ObserveSupporterStateUseCaseTest.kt
  - core/testing/src/main/kotlin/com/kshavrin/mymoney/core/testing/fake/FakeSupporterRepository.kt
  - core/sync/src/main/java/com/kshavrin/mymoney/core/sync/supporter/SupportPurchaseReconciliationCoordinatorImpl.kt
  - core/sync/src/main/java/com/kshavrin/mymoney/core/sync/supporter/SupporterSyncImpl.kt
  - feature/support/src/main/java/com/kshavrin/mymoney/feature/support/SupportState.kt
  - feature/support/src/main/java/com/kshavrin/mymoney/feature/support/SupportViewModel.kt
  - core/billing/src/test/java/com/kshavrin/mymoney/core/billing/BillingWiringContractTest.kt
  - feature/support/src/test/java/com/kshavrin/mymoney/feature/support/SupportViewModelTest.kt
