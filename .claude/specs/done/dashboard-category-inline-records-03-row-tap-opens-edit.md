# Тап по строке-операции открывает редактирование
Epic: dashboard-category-inline-records
Order: 03 of 03
Status: done
Depends-on: 02
Date: 2026-06-22

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: Тап по строке-операции в раскрытом списке открывает экран редактирования этой транзакции. Добавляется одноразовое действие `DashboardAction.NavigateToTransactionDetail(transactionId: Long)`; строка `CategoryRecordsInlineList` при клике пробрасывает `transaction.id` через `DashboardEvent` → `DashboardViewModel` эмитит это действие; `MyMoneyNavHost` обрабатывает его как `navController.navigate("${Destinations.TRANSACTION_DETAIL}/$id")` — тот же маршрут, что используют записи/поиск.
LAYERS: presentation
CHANGED_HINT:
  - feature/dashboard/.../DashboardAction.kt — добавить `data class NavigateToTransactionDetail(val transactionId: Long) : DashboardAction` (G14)
  - feature/dashboard/.../DashboardState.kt — добавить событие `data class RecordRowClicked(val transactionId: Long) : DashboardEvent` (рядом со `SliceClicked`, ср. `DashboardState.kt:203-205`)
  - feature/dashboard/.../DashboardViewModel.kt — ветка `is DashboardEvent.RecordRowClicked` → `emit(DashboardAction.NavigateToTransactionDetail(event.transactionId))` (рядом с обработкой `SliceClicked`, `DashboardViewModel.kt:814-845`)
  - feature/dashboard/.../components/CategoryRecordsInlineList.kt — строка получает `onRowClick: (Long) -> Unit`, по клику передаёт `transaction.id`; проброс из `CategoryTilesList`/`DashboardScreen.kt` до `onEvent(DashboardEvent.RecordRowClicked(id))`
  - app/.../navigation/MyMoneyNavHost.kt:78-110 — в `when` по `DashboardAction` добавить ветку `is …DashboardAction.NavigateToTransactionDetail -> navController.navigate("${Destinations.TRANSACTION_DETAIL}/${action.transactionId}")` (G14; до `else -> Unit`)
  - тест: DashboardViewModelTest — `RecordRowClicked(id)` эмитит `NavigateToTransactionDetail(id)`; (опц.) route-string контракт `TRANSACTION_DETAIL/$id`
TEST_TYPES: unit
CONSTRAINTS:
  - Переиспользовать существующий маршрут `Destinations.TRANSACTION_DETAIL` (G14) — НЕ заводить новый экран/route редактирования.
  - `DashboardState.kt` (правится в 01), `DashboardViewModel.kt` (01) и `DashboardScreen.kt` + `CategoryRecordsInlineList.kt` (02) правятся также в предыдущих SPEC-ах — этот ПОСЛЕДНИЙ (same-file clash, строгий порядок 01→02→03).
  - `else -> Unit` в блоке DashboardAction (`MyMoneyNavHost.kt:109`) сохранить — добавляем ветку перед ним.
=== END SPEC ===

## Acceptance (Gherkin)
```gherkin
Feature: Переход к редактированию операции из inline-списка

  Scenario: Тап по строке открывает редактирование
    Given под плиткой «Еда» раскрыт список операций
    When пользователь тапает строку конкретной операции
    Then открывается экран редактирования этой транзакции (TRANSACTION_DETAIL/$id)

  Scenario: Дашборд остаётся точкой возврата
    Given пользователь открыл редактирование операции из раскрытого списка
    When он возвращается назад
    Then он снова на дашборде
```

## Gap / context
После SPEC 02 строки видны, но некликабельны. Этот SPEC замыкает взаимодействие: тап по операции ведёт на её редактирование через уже существующий маршрут `TRANSACTION_DETAIL`.

## Implementation links
- commit: c3c8e451 (prod) + 5a5506be (test)
- files:
  - feature/dashboard/.../DashboardAction.kt (NavigateToTransactionDetail)
  - feature/dashboard/.../DashboardState.kt (RecordRowClicked event)
  - feature/dashboard/.../DashboardViewModel.kt (handler → emit action)
  - feature/dashboard/.../DashboardScreen.kt (onEvent wiring)
  - feature/dashboard/.../components/CategoryRecordsInlineList.kt (onRowClick)
  - feature/dashboard/.../components/CategoryTilesList.kt (pass-through)
  - app/.../navigation/MyMoneyNavHost.kt (DashboardAction branch → TRANSACTION_DETAIL/$id)
  - feature/dashboard/.../DashboardViewModelTest.kt (3 new tests, 101 total green)
