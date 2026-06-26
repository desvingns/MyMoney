# Удаление полноэкранного окна всех операций
Epic: dashboard-operations-summary-drawer
Order: 04 of 04
Status: done
Depends-on: 03
Date: 2026-06-25

## SPEC
=== SPEC ===
TASK: refactor
PLATFORM: android
WHAT: Убрать вход и навигацию из dashboard к полноэкранному окну всех операций (после того как SPEC 03 перевёл точки входа на сводку). Удалить ставшие неиспользуемыми `Navigate*`-Action'ы dashboard и их обработку в NavHost. Маршрут `TRANSACTIONS_LIST` и экран `TransactionsListScreen` — **архивировать целиком ТОЛЬКО если других вызовов нет**; если есть (поиск/счета/др.), оставить маршрут и убрать лишь dashboard-проводку.
LAYERS: presentation
CHANGED_HINT:
  - feature/dashboard/.../DashboardAction.kt:43-61 — удалить `NavigateTransactionsByAccount`, `NavigateTransactionsByCurrency`, `NavigateTransactionsByCategory`, если они больше не эмитятся после SPEC 03. (G3, G14)
  - app/src/main/java/com/kshavrin/mymoney/navigation/MyMoneyNavHost.kt:94-104 — убрать обработку этих dashboard-action'ов (навигацию к `TRANSACTIONS_LIST`). (G14)
  - app/src/main/java/com/kshavrin/mymoney/navigation/MyMoneyNavHost.kt:128-159 — `composable(TRANSACTIONS_LIST)` + `TransactionsListScreen`: если grep по `TRANSACTIONS_LIST`/`navigate(...)` подтверждает отсутствие других вызовов — **архивировать** маршрут и весь `:feature:transactionslist` экран в `archive/` (не удалять); иначе оставить. (H4)
  - удалить мёртвые ссылки/импорты `TransactionsListScreen` из dashboard/nav после переключения.
TEST_TYPES: unit
CONSTRAINTS:
  - Правило проекта: **файлы не удаляются — переносятся в `archive/`** (git-ignored, repo root), затем сообщить пользователю пути для ручного удаления (CLAUDE.md «archive, never delete»).
  - Перед архивированием маршрута/экрана — grep на ВСЕ вызовы `TRANSACTIONS_LIST` и навигацию в `TransactionsListScreen` (поиск, экран счетов и т.д., H4). Архивировать только при нуле внешних вызовов.
  - Общий файл `DashboardAction.kt` с SPEC 03 → выполнять строго после 03 (G14).
  - После чистки — сборка `:app:assembleDebug` должна проходить (нет висячих ссылок).
=== END SPEC ===

## Acceptance
```gherkin
Feature: Чистка старого окна операций

  Scenario: Нет навигации к полноэкранному окну с dashboard
    Given реализованы SPEC 01–03
    When пользователь тапает балансу/плашке/категории
    Then открывается сводка (шторка), а не полноэкранное окно

  Scenario: Архивирование при отсутствии других вызовов
    Given grep не находит других вызовов TRANSACTIONS_LIST
    When выполняется чистка
    Then маршрут и экран перенесены в archive/ (не удалены)
    And :app:assembleDebug собирается

  Scenario: Сохранение при наличии других вызовов
    Given TRANSACTIONS_LIST вызывается из другого экрана
    When выполняется чистка
    Then маршрут остаётся, убрана только dashboard-навигация
```

## Gap / context
Старое полноэкранное окно становится мёртвым после перехода на сводку; этот SPEC безопасно убирает его проводку и архивирует код при отсутствии других потребителей.

## Implementation links
- commit: 588ce074 (prod), ab116d34 (test reconcile)
- files:
  - feature/dashboard/.../DashboardAction.kt (removed 3 Navigate* actions)
  - app/.../navigation/MyMoneyNavHost.kt (removed 3 nav branches + composable(TRANSACTIONS_LIST))
  - feature/transactionslist/detekt-baseline.xml
  - feature/dashboard/.../DashboardViewModelTest.kt (none{} guards -> isEmpty())
  - ARCHIVED to archive/: feature/transactionslist list/ package + its unit tests + app androidTests (SwipeToDeleteUiTest, TransactionsListContentUiTest)
  - KEPT Destinations.TRANSACTIONS_LIST const (DestinationsTest still asserts it)
