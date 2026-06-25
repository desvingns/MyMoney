# UI шторки сводки операций (stateless)
Epic: dashboard-operations-summary-drawer
Order: 02 of 04
Status: done
Depends-on: 01
Date: 2026-06-25

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: Stateless UI нижней шторки сводки в `:feature:dashboard`. `ModalBottomSheet`, раскрытый до верхнего тулбара (топбар периода + ⋮ остаются видимы над ней). Внутри — единый хронологический список `SummaryRecord` (доходы/расходы/переводы вперемешку по времени). Строка операции: иконка категории, название/заметка, сумма с цветом по kind, дата. Строка перевода: «счёт → счёт», заметка, сумма, дата. Заголовок отражает режим: «Все операции» либо имя категории при фильтре. Loading-индикатор и пустое состояние («нет операций за период»).
LAYERS: presentation
CHANGED_HINT:
  - feature/dashboard/src/main/java/com/kshavrin/mymoney/feature/dashboard/components/OperationsSummarySheet.kt — новый: `OperationsSummarySheet(records, loading, title, onRowClick: (Long) -> Unit, onDismiss)`; `ModalBottomSheet` по образцу `ChartSettingsSheet` (G6), `sheetState` с раскрытием до тулбара; testTag для шторки и для пустого состояния. (G6)
  - feature/dashboard/src/main/java/com/kshavrin/mymoney/feature/dashboard/components/SummaryRecordRow.kt — новый: рендер `SummaryRecord.Operation` / `SummaryRecord.Transfer`; визуал на основе существующих строк аккордеона `CategoryRecordsInlineList.kt:45-97` (стиль строки записи) и `TransferRow` (`TransactionsListScreen.kt:343-404`) — НЕ импортируя их из `:feature:transactionslist`. (G2, G12)
  - все подписи — через `stringResource` (values/values-ru), без хардкода (G18).
TEST_TYPES: compose-ui
CONSTRAINTS:
  - `:feature:dashboard` НЕ может зависеть от `:feature:transactionslist` (G19) — строки реализуются в dashboard (либо, при переиспользовании в нескольких feature, выносятся в `:core:designsystem`); по умолчанию — локально в dashboard.
  - Compose UI-тесты — `:app` androidTest, НЕ Robolectric (G20); один `setContent` на `@Test`; пустой список → tagged Column нулевого размера → `.assertExists()`, не `.assertIsDisplayed()` (G20); off-screen строки → `.performScrollTo()` перед кликом.
  - Композиция полностью stateless: данные и колбэки приходят сверху, никакой загрузки/VM внутри.
  - Высота — до верхнего тулбара (не перекрывать топбар периода и ⋮).
=== END SPEC ===

## Acceptance
```gherkin
Feature: Шторка сводки операций

  Scenario: Список с доходами, расходами и переводами
    Given переданы записи операций и переводов
    When открыта OperationsSummarySheet без фильтра
    Then видны строки операций и переводов в порядке по времени
    And заголовок — «Все операции»

  Scenario: Режим фильтра по категории
    Given переданы только операции одной категории, title = имя категории
    When открыта шторка
    Then заголовок показывает имя категории
    And строк-переводов в списке нет

  Scenario: Пустое состояние
    Given передан пустой список и loading = false
    When открыта шторка
    Then показан плейсхолдер «нет операций за период» (assertExists)

  Scenario: Тап по строке
    Given открыта шторка со строкой операции id=42
    When пользователь тапает строку
    Then вызывается onRowClick(42)
```

## Gap / context
Нет UI-компонента шторки со смешанным хронологическим списком операций и переводов — этот SPEC добавляет stateless-композиции, которые SPEC 03 подключит к данным.

## Implementation links
- commit: 66154d5f, fd8b4d86, d97a43d5, 173bfad9, 836bf865
- files:  feature/dashboard/src/main/java/com/kshavrin/mymoney/feature/dashboard/components/OperationsSummarySheet.kt; feature/dashboard/src/main/java/com/kshavrin/mymoney/feature/dashboard/components/SummaryRecordRow.kt; feature/dashboard/src/main/res/values/strings.xml; feature/dashboard/src/main/res/values-ru/strings.xml; app/src/androidTest/java/com/kshavrin/mymoney/feature/dashboard/components/OperationsSummarySheetUiTest.kt
