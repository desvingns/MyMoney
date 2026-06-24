# Inline-раскрытие операций категории на дашборде — epic overview
Epic: dashboard-category-inline-records
Order: 00 of 03
Status: done
Depends-on: —
Date: 2026-06-22

## Goal
При тапе по плитке категории на дашборде (S01) пользователь **остаётся на дашборде**: прямо под нажатой плиткой аккордеоном раскрывается список подробных операций **только этой категории** за период, выбранный в топбаре. Тап заменяет прежний переход на отдельный экран записей по категории. Строка операции показывает заметку + сумму + дату; тап по строке открывает редактирование операции. Вне фичи: слайсы доната (доната нет — заменён графиком), категория «Прочее» (её больше нет), режим «Все счета → раздельно» (плиток категорий там нет), сам экран записей `TRANSACTIONS_LIST` (остаётся для входа по счёту/валюте/поиску).

## Locked decisions
- Триггер раскрытия — **только тап по плитке категории** (`CategoryTilesList`); донат отсутствует.
- Тап **заменяет** навигацию `NavigateTransactionsByCategory` на inline-раскрытие — с дашборда не уходим.
- Подача — **аккордеон под нажатой плиткой**; одновременно открыта одна категория; повторный тап сворачивает, тап по другой — переключает.
- Строка операции — **заметка/комментарий + сумма + дата**.
- Тап по строке-операции → **редактирование операции** (существующий маршрут `Destinations.TRANSACTION_DETAIL/$id`).
- «Прочее» больше нет → раскрытие только у реальных категорий; существующий `OTHER_CATEGORY_ID` early-return сохраняется как защита.
- Период — из топбара (`state.period`); список тянется через уже существующий `GetCategoryRecordsUseCase` (новый домен/данные не нужны).
- Работает в `SpecificAccount` (`invoke(accountId,…)`) и `AllAccounts.ConvertTo` (`forAccounts(accounts, target-currency,…)`).
- (assumption) Сортировка — новейшие сверху (порядок из use-case проверить); список встроен в общий скролл дашборда; загрузка ленивая, пусто-безопасный рендер.

## SPECs (run via `/mp --feature --next` in Order)
| Order | File | Depends-on | Layers | Summary |
|---|---|---|---|---|
| 01 | `dashboard-category-inline-records-01-vm-expand-state-and-fetch.md` | — | presentation | Состояние раскрытия + ленивая загрузка операций категории; `SliceClicked` тогглит вместо навигации |
| 02 | `dashboard-category-inline-records-02-inline-records-accordion-ui.md` | 01 | presentation | Аккордеон-список под плиткой + строка-операция (заметка/сумма/дата) |
| 03 | `dashboard-category-inline-records-03-row-tap-opens-edit.md` | 02 | presentation | Тап по строке → редактирование транзакции через NavHost |

## Why this ordering
Линейная цепочка 01→02→03. Чистого домена/данных нет (`GetCategoryRecordsUseCase` уже есть), поэтому «фундамент» — это VM-поведение (01), затем UI (02), затем навигация по строке (03). Линейность вынуждена same-file clash-ами: `DashboardViewModel.kt` правится в 01 и 03; `DashboardScreen.kt` — в 02 и 03. Параллельная правка этих файлов запрещена — отсюда строгий порядок.

## Key facts (verified)
- G1: плитки — `CategoryTilesList` `feature/dashboard/.../DashboardScreen.kt:278-296`; `onTileClick → DashboardEvent.SliceClicked(categoryId)` (`components/CategoryTile.kt:38-46,76`). Донат заменён графиком (`DashboardScreen.kt:257-261`, `onChartClick`).
- G2: `DashboardEvent.SliceClicked(categoryId: Long)` — `DashboardState.kt:203-205`.
- G3: handler `SliceClicked` — `DashboardViewModel.kt:814-845`: `OTHER_CATEGORY_ID` early-return (815); эмитит `NavigateTransactionsByCategory` в `SpecificAccount` (818-829) и `AllAccounts.ConvertTo` (830-842); в `AllAccounts.Separate` — ничего.
- G4: `GetCategoryRecordsUseCase.invoke(accountId, period, categoryId?=null): List<CategoryRecordGroup>` и `forAccounts(accounts, currency, period, categoryId?=null)` — `core/domain/.../usecase/GetCategoryRecordsUseCase.kt:26-51`. `CategoryRecordGroup{categoryId,name,iconKey,colorHex,kind,total: Money,count,transactions: List<Transaction>}` — `.../model/CategoryRecordGroup.kt:3-12`.
- G5: `DashboardState` — `DashboardState.kt:16-62` (нет полей раскрытия — добавляем).
- G6: `Transaction{id,amount: BigDecimal,note: String?,occurredAt: Instant,categoryId?,accountId,currencyId,kind,…}` — `core/domain/.../model/Transaction.kt:6-21`.
- G10: `:feature:*→:core:*` только → строку-операцию рендерить своим composable в `:feature:dashboard`.
- G14: маршрут редактирования — `navController.navigate("${Destinations.TRANSACTION_DETAIL}/$id")` — `app/.../navigation/MyMoneyNavHost.kt:117,166`; блок сбора DashboardAction `when{… else -> Unit}` — `MyMoneyNavHost.kt:78-110`.
- G-gotcha: UI-тесты дашборда — `:app` androidTest (`app/src/androidTest/.../feature/dashboard/`), не Robolectric; нужен `:app:ktlintFormat`; `assertExists`/`onAllNodes` — методы-члены; off-screen — `.performScrollTo()` перед кликом.

## Implementation links
- commit: (pending)
- files:  (pending)
