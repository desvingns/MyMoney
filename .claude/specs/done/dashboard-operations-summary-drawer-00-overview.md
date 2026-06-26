# Подробная сводка операций (нижняя шторка) — epic overview
Epic: dashboard-operations-summary-drawer
Order: 00 of 04
Status: done
Depends-on: —
Date: 2026-06-25

## Goal
Переносим «окно подробных операций» с dashboard в формат нижней шторки (раскрытой до верхнего тулбара), показывающей **единый хронологический список** всех операций — доходов, расходов и переводов — за текущий период топбара и текущий выбор счёта/валюты. Точки входа: тап по плашке «Аврора» (график+остаток) и по балансу → сводка **без фильтра**; тап по плитке категории → та же сводка **с фильтром по этой категории** (инлайн-аккордеон убирается). Старое полноэкранное окно всех операций удаляется; настройки графика остаются доступны через правое ⋮ меню. Вне зоны: сам график, экран редактирования транзакции, новые типы данных/миграции Room.

## Locked decisions
- Контейнер сводки — нижняя `ModalBottomSheet`, раскрытая до верхнего тулбара (топбар периода + ⋮ остаются видимы). [confirmed]
- Содержимое — единый хронологический список доходов+расходов+переводов, сортировка по дате (НЕ вкладки). [confirmed]
- Тап «Аврора» и тап по балансу → сводка без фильтра по категории. [confirmed]
- Тап по плитке категории → сводка с фильтром по категории; прежний инлайн-аккордеон убирается. [confirmed]
- При фильтре по категории переводы исключаются (у них нет категории). [confirmed]
- Настройки графика остаются доступны через правое ⋮ меню (`ChartSettingsClicked`); убирается только их открытие по тапу на плашку. [confirmed]
- Старое полноэкранное окно всех операций (тап по балансу → `TransactionsListScreen`) — вход и навигация из dashboard убираются; файлы архивируются, не удаляются. [confirmed]
- Охват — по текущему выбору dashboard (счёт / «Все счета» с конвертацией в целевую валюту) за период топбара. [confirmed]
- Маршрут `TRANSACTIONS_LIST`/экран архивируются полностью только если нет других вызовов; иначе остаётся, убирается лишь dashboard-навигация. (assumption — проверить в SPEC 04)
- Тап по строке → редактирование `TRANSACTION_DETAIL/{id}`, шторка закрывается. (assumption)
- Нет операций за период → пустой плейсхолдер в шторке. (assumption)

## SPECs (run via `/mp --feature --next` in Order)
| Order | File | Depends-on | Layers | Summary |
|---|---|---|---|---|
| 01 | `dashboard-operations-summary-drawer-01-summary-usecase.md` | — | domain | Use-case: единый отсортированный по времени список доходов+расходов+переводов; фильтр по категории ⇒ переводы исключаются |
| 02 | `dashboard-operations-summary-drawer-02-summary-sheet-ui.md` | 01 | presentation | Stateless UI шторки: `ModalBottomSheet` до тулбара, хронологический список, строки операции/перевода, пустое состояние |
| 03 | `dashboard-operations-summary-drawer-03-dashboard-wiring.md` | 01, 02 | presentation | Проводка VM/State/Screen: репурпоз тапов, загрузка через 01, хостинг шторки, настройки графика только из ⋮ |
| 04 | `dashboard-operations-summary-drawer-04-remove-fullscreen-window.md` | 03 | presentation | Убрать вход/навигацию к полноэкранному окну; архив маршрута/экрана если нет других вызовов; чистка Navigate*-Action'ов |

## Why this ordering
Foundation-first: чистая доменная логика (01) → stateless UI (02, опирается на модель из 01) → интеграция в dashboard (03) → чистка старого окна (04). SPEC 03 и 04 оба правят `DashboardAction.kt`, поэтому строго последовательны — 03 добавляет открытие сводки, 04 удаляет ставшие ненужными `Navigate*`-Action'ы. Параллельная правка этого файла запрещена.

## Key facts (verified)
- G1: тап «Аврора» → `DashboardEvent.ChartTapped` → `chartSettingsSheetOpen=true` — `feature/dashboard/.../DashboardScreen.kt:388`, `DashboardViewModel.kt:1087-1088`.
- G2: тап плитки → `SliceClicked(categoryId)` → `toggleExpandedCategory` (инлайн-аккордеон) — `DashboardScreen.kt:418`, `DashboardViewModel.kt:1082,1226-1280`.
- G3: тап по балансу → `BalanceCardClicked` → `NavigateTransactionsByAccount`/`NavigateTransactionsByCurrency` → `TransactionsListScreen` — `DashboardViewModel.kt:1066-1081`.
- G4: настройки графика также из ⋮ — `ChartSettingsClicked` (с `closeDrawers()`) — `DashboardViewModel.kt:1089-1092`.
- G5: тап строки → `RecordRowClicked` → `NavigateToTransactionDetail` → `TRANSACTION_DETAIL/{id}` — `DashboardViewModel.kt:1083-1084`.
- G6: `ChartSettingsSheet` — Material3 `ModalBottomSheet` — `ChartSettingsSheet.kt:52-64`, `DashboardScreen.kt:262-268`.
- G8: `DashboardViewModel` — `StateFlow<DashboardState>` + `SharedFlow<DashboardAction>` replay=0 — `DashboardViewModel.kt:74-82`.
- G9: state-поля `chartSettingsSheetOpen` (:49), `expandedCategoryId/expandedRecords/expandedRecordsLoading` (:42-44) — `DashboardState.kt`.
- G10: `TransactionsListScreen` — 2 вкладки Operations/Transfers — `TransactionsListScreen.kt:211-218,249-340`.
- G13: `TransactionsListViewModel` — отдельные потоки: category-records (фильтр по категории) и transfer-records (без фильтра) — `TransactionsListViewModel.kt:36-37,164-172`.
- G14: маршрут `TRANSACTIONS_LIST` полноэкранный — `MyMoneyNavHost.kt:128-159`; action `NavigateTransactionsByCategory` — `DashboardAction.kt:55-61`.
- G15: `GetCategoryRecordsUseCase.invoke(accountId, period, categoryId: Long?=null): List<CategoryRecordGroup>` + `.forAccounts(accounts, currency, period, categoryId)` — `GetCategoryRecordsUseCase.kt:26-51`.
- G16: `GetTransferRecordsUseCase.invoke(accountId: Long?, period): List<TransferRecord>` — `GetTransferRecordsUseCase.kt:20-40`.
- G17: `Period` sealed → epoch-millis через `PeriodArithmetic.toEpochMillisRange` — `Period.kt:7-48`, `PeriodArithmetic.kt:9-33`.
- G18-G22: конвенции (BigDecimal/Double, LocalDate/Long, stringResource), `:feature:*`↛`:feature:*`, тест-gotchas (chart UI = :app androidTest не Robolectric; пустой список → assertExists; SharedFlow replay=0 не pre-consume; новый метод интерфейса ломает fakes; ktlintFormat перед коммитом).

## Implementation links
- Epic COMPLETE 2026-06-25. All 4 SPECs in done/.
- 01 3f18fbc0/efe39d61 - use-case; 02 66154d5f.. - sheet UI; 03 ab183973+9fc10498/efaf4fe0 - wiring; 04 588ce074+ab116d34 - remove full-screen window.
