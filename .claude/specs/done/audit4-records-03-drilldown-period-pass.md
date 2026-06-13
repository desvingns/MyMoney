# Drill-down с дашборда передаёт выбранный период
Epic: audit4-records
Order: 03 of 05
Status: done
Depends-on: —
Date: 2026-06-10

## SPEC
=== SPEC ===
TASK: bugfix
PLATFORM: android
WHAT: Открытие записей с дашборда (тап по слайсу/категории/балансу) должно показывать ТОТ ЖЕ период, что выбран на дашборде. Действия NavigateTransactionsBy* получают epoch-millis диапазон текущего периода; NavHost дописывает &from=&to= в маршрут; resolvePeriod на стороне списка уже умеет их читать.
LAYERS: presentation
CHANGED_HINT:
  - feature/dashboard/.../DashboardViewModel.kt — data-классы DashboardAction.NavigateTransactionsByAccount/ByCurrency/ByCategory получают fromMillis/toMillis из `PeriodArithmetic.toEpochMillisRange(state.period)` в момент эмиссии (G4)
  - app/src/main/java/com/kshavrin/mymoney/navigation/MyMoneyNavHost.kt:84-97 — append `&from=${action.fromMillis}&to=${action.toMillis}` во все три navigate-вызова (G3)
  - тесты: DashboardViewModelTest — действия несут диапазон выбранного периода; route-контракт в DestinationsTest-стиле — собранный маршрут парсится навигацией с дефолтами −1
TEST_TYPES: unit
CONSTRAINTS:
  - `resolvePeriod` (G4) НЕ менять — он уже корректно строит CustomRange из from/to.
  - Дефолт −1/−1 (прямой заход без аргументов) сохраняет текущее поведение «текущий месяц».
  - `DashboardViewModel.kt` правится также в SPEC 05 — этот первым.
=== END SPEC ===

## Acceptance (Gherkin)
```gherkin
Feature: Период переживает drill-down

  Scenario: Годовой период
    Given на дашборде выбран период «Год: 2026»
    When пользователь тапает слайс категории «Еда»
    Then список записей показывает операции за 2026 год
    And суммы списка согласуются с дашбордом

  Scenario: Произвольный диапазон
    Given на дашборде выбран диапазон 10–15 июня
    When пользователь открывает записи по счёту
    Then список ограничен 10–15 июня

  Scenario: Прямой заход без аргументов
    When экран записей открыт не с дашборда
    Then показывается текущий месяц (как раньше)
```

## Gap / context
Баг M2/M6 аудита: navigate-вызовы не передают from/to (G3, verified-main) → список всегда падает
в Period.Month(now) и расходится с дашбордом при «Год»/диапазоне.

## Implementation links
- commit: 5dac6fdf (feat), 72246319 (tests) — pushed to main 2026-06-13
- files:
  - feature/dashboard/.../DashboardAction.kt — NavigateTransactionsBy{Account,Currency,Category} gained fromMillis/toMillis
  - feature/dashboard/.../DashboardViewModel.kt — BalanceCardClicked/SliceClicked compute PeriodArithmetic.toEpochMillisRange(state.period) and embed it
  - app/.../navigation/MyMoneyNavHost.kt — append &from=&to= to all three transactions-list navigate calls
  - DashboardViewModelTest.kt + DestinationsTest.kt — range carry + route-string contract
