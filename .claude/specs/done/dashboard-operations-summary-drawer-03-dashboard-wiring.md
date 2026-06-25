# Проводка сводки в dashboard (репурпоз тапов + хостинг шторки)
Epic: dashboard-operations-summary-drawer
Order: 03 of 04
Status: done
Depends-on: 01, 02
Date: 2026-06-25

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: Подключить сводку к dashboard. Тап по плашке «Аврора» (`ChartTapped`) и тап по балансу (`BalanceCardClicked`) открывают сводку **без фильтра**; тап по плитке категории (`SliceClicked`) открывает сводку **с фильтром по этой категории** вместо инлайн-аккордеона. Данные грузятся через `GetOperationsSummaryUseCase` (SPEC 01) по текущему `dashboardSelection` за период топбара. Шторка `OperationsSummarySheet` (SPEC 02) хостится в `DashboardScreen`. Настройки графика открываются ТОЛЬКО из правого ⋮ меню (`ChartSettingsClicked`). Тап по строке → `TRANSACTION_DETAIL/{id}`, шторка закрывается.
LAYERS: presentation
CHANGED_HINT:
  - feature/dashboard/.../DashboardState.kt — добавить состояние сводки (открыта/закрыта, `categoryFilter: Long?`, `records: List<SummaryRecord>`, `loading`, `title`); убрать инлайн-аккордеон поля `expandedCategoryId/expandedRecords/expandedRecordsLoading` (:42-44) или пометить неиспользуемыми. Сохранить `chartSettingsSheetOpen` (:49) для ⋮. (G9)
  - feature/dashboard/.../DashboardViewModel.kt:1087-1088 — `ChartTapped` → открыть сводку без фильтра (вместо `chartSettingsSheetOpen=true`). (G1)
  - feature/dashboard/.../DashboardViewModel.kt:1066-1081 — `BalanceCardClicked` → открыть сводку без фильтра; убрать `emit(NavigateTransactionsByAccount/ByCurrency)`. (G3, H1)
  - feature/dashboard/.../DashboardViewModel.kt:1082 — `SliceClicked(categoryId)` → открыть сводку с `categoryFilter = categoryId` (вместо `toggleExpandedCategory`, :1226-1280). (G2)
  - feature/dashboard/.../DashboardViewModel.kt — при открытии сводки грузить данные: `SpecificAccount` → `GetOperationsSummaryUseCase.invoke(accountId, period, categoryId)`; `AllAccounts` ConvertTo → `.forAccounts(...)` (D8); инжект use-case через Hilt. `RecordRowClicked` (:1083-1084) уже навигирует в детали (G5) — закрывать шторку при навигации.
  - feature/dashboard/.../DashboardScreen.kt:262-268 — хостить `OperationsSummarySheet` при открытой сводке; `chartSettingsSheetOpen` оставить для ⋮.
  - feature/dashboard/.../DashboardScreen.kt:413-421 — убрать рендер инлайн-аккордеона (`CategoryRecordsInlineList`); `onChartClick`/`SliceClicked` теперь ведут в сводку.
  - feature/dashboard/.../DashboardAction.kt — при необходимости новый `OpenSummary`/нет (открытие — через state, не Action). Файл общий с SPEC 04 (см. CONSTRAINTS).
TEST_TYPES: unit, compose-ui
CONSTRAINTS:
  - Общий файл `DashboardAction.kt` с SPEC 04 → строго последовательно (03 раньше 04), без параллельной правки (G14).
  - `SharedFlow` actions replay=0: в тестах НЕ делать pre-consume `awaitItem()` перед нужным action (G21).
  - Убедиться, что в правом ⋮ меню есть видимый пункт «Настройки графика» (`ChartSettingsClicked`, G4) — раз тап-вход удалён (H2).
  - Compose UI-тесты dashboard — `:app` androidTest, НЕ Robolectric (G20).
  - Открытие/закрытие сводки — через `DashboardState`, не одноразовый Action (чтобы переживать рекомпозицию).
=== END SPEC ===

## Acceptance
```gherkin
Feature: Открытие сводки с dashboard

  Scenario: Тап по плашке «Аврора»
    Given dashboard с выбранным счётом и периодом
    When пользователь тапает по плашке «Аврора»
    Then открывается сводка без фильтра по категории
    And настройки графика НЕ открываются

  Scenario: Тап по плитке категории
    When пользователь тапает плитку категории A
    Then открывается сводка с фильтром по категории A
    And инлайн-аккордеон под плиткой не появляется

  Scenario: Настройки графика из меню
    When пользователь открывает правое ⋮ меню и выбирает «Настройки графика»
    Then открывается лист настроек графика

  Scenario: Тап по строке в сводке
    Given открыта сводка
    When пользователь тапает строку операции id=42
    Then происходит переход в TRANSACTION_DETAIL/42
    And шторка закрывается
```

## Gap / context
Точки входа dashboard ведут в старое окно/настройки/аккордеон; этот SPEC переключает их на единую сводку и подключает загрузку данных.

## Implementation links
- commit: ab183973 (prod wiring) + 9fc10498 / efaf4fe0 (tests)
- files:
  - feature/dashboard/.../DashboardState.kt (operationsSummary state; accordion fields removed)
  - feature/dashboard/.../DashboardViewModel.kt (ChartTapped/BalanceCardClicked/SliceClicked → openOperationsSummary; getOperationsSummary injected; RecordRowClicked closes sheet; Navigate* removed)
  - feature/dashboard/.../DashboardScreen.kt (hosts OperationsSummarySheet conditionally; inline accordion removed; onChartClick→ChartTapped, onTileClick→SliceClicked)
  - feature/dashboard/.../components/CategoryTilesList.kt (accordion params dropped → expenseTiles + onTileClick)
  - tests: DashboardViewModelTest.kt, app androidTest DashboardContentUiTest.kt / CategoryTilesListUiTest.kt / ImportFocusColdStartRegressionTest.kt (stale reconciled)
- notes: chart settings now only via ⋮ (RightDrawerContent→ChartSettingsClicked). BalanceCardClicked handler kept + unit-tested but has no separate UI surface (balance lives inside the Aurora card → ChartTapped). Pre-existing red androidTest `ImportFocusColdStartRegressionTest.importedRowsSurviveColdStartAndShowOnDashboard` confirmed failing at baseline fc0710a4 — NOT a SPEC 03 regression (follow-up needed).
