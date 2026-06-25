# Use-case: единый хронологический список операций и переводов
Epic: dashboard-operations-summary-drawer
Order: 01 of 04
Status: done
Depends-on: —
Date: 2026-06-25

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: Новый доменный use-case `GetOperationsSummaryUseCase`, возвращающий плоский, отсортированный по времени (по убыванию) список записей — доходы, расходы и переводы — за период по текущему выбору. Без `categoryId` → доходы+расходы+переводы; с `categoryId != null` → только операции этой категории, переводы исключаются (у переводов нет категории). Введите модель `SummaryRecord` (sealed): `Operation(transaction…)` и `Transfer(transfer…)` с общим полем времени для сортировки.
LAYERS: domain
CHANGED_HINT:
  - core/domain/src/main/kotlin/com/kshavrin/mymoney/core/domain/model/SummaryRecord.kt — новый sealed-тип: `Operation` (обёртка над Transaction: amount BigDecimal, kind, categoryId, note, timestamp) и `Transfer` (обёртка над TransferRecord: from/to account, amount, note, timestamp). (assumption — форма зеркалит существующие модели)
  - core/domain/src/main/kotlin/com/kshavrin/mymoney/core/domain/usecase/GetOperationsSummaryUseCase.kt — новый use-case. `invoke(accountId: Long, period: Period, categoryId: Long? = null): List<SummaryRecord>` и `forAccounts(accounts, currency, period, categoryId: Long? = null): List<SummaryRecord>`; делегирует в `GetCategoryRecordsUseCase` (G15) для операций и в `GetTransferRecordsUseCase` (G16) для переводов; разворачивает `CategoryRecordGroup.transactions` в `Operation`-записи, `TransferRecord` в `Transfer`-записи, мёржит и сортирует по времени desc. (G15, G16)
  - при `categoryId != null` — переводы НЕ запрашиваются/не добавляются (G13: у переводов нет категории).
TEST_TYPES: unit
CONSTRAINTS:
  - Переиспользовать существующие `GetCategoryRecordsUseCase` (G15) и `GetTransferRecordsUseCase` (G16) — НЕ добавлять новый репозиторий/метод интерфейса (иначе сломаются все module-local fakes, G22). Никаких новых таблиц/миграций Room.
  - Деньги — `BigDecimal` в домене (G18); в тестах сравнивать через `compareTo`, не `equals` (G8); `BigDecimal.valueOf`, не `BigDecimal(double)`.
  - Сортировка стабильная по времени desc; при равном времени — детерминированный вторичный ключ (например id) для предсказуемости тестов.
  - Охват выбора (D8): `invoke` — для конкретного счёта; `forAccounts` — для режима «Все счета» с конвертацией в целевую валюту (мёрж как в `GetCategoryRecordsUseCase.forAccounts`, G15).
=== END SPEC ===

## Acceptance
```gherkin
Feature: Единый список операций для сводки

  Scenario: Без фильтра — доходы, расходы и переводы вместе
    Given за период есть расход, доход и перевод
    When вызывается GetOperationsSummaryUseCase.invoke(accountId, period, categoryId = null)
    Then результат содержит все три записи
    And записи отсортированы по времени по убыванию

  Scenario: С фильтром по категории — переводы исключены
    Given за период есть операции категории A, операции категории B и перевод
    When вызывается invoke(accountId, period, categoryId = A)
    Then результат содержит только операции категории A
    And ни одной записи-перевода в результате нет

  Scenario: Пустой период
    Given за период нет ни операций, ни переводов
    When вызывается invoke(accountId, period, categoryId = null)
    Then результат — пустой список
```

## Gap / context
Существующие use-case'ы дают группировку по категориям и отдельный список переводов; для хронологической шторки нужен единый плоский отсортированный по времени список — этот use-case его собирает без новых репозиториев.

## Implementation links
- commit: 3f18fbc0, efe39d61
- files:
  - core/domain/src/main/kotlin/com/kshavrin/mymoney/core/domain/model/SummaryRecord.kt
  - core/domain/src/main/kotlin/com/kshavrin/mymoney/core/domain/usecase/GetOperationsSummaryUseCase.kt
  - core/domain/src/test/kotlin/com/kshavrin/mymoney/core/domain/fake/FakeRepositories.kt
  - core/domain/src/test/kotlin/com/kshavrin/mymoney/core/domain/usecase/GetOperationsSummaryUseCaseTest.kt
