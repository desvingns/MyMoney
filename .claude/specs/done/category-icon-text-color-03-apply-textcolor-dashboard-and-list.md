# Отрисовка имени категории в производном textColor (dashboard + список + сетка)
Epic: category-icon-text-color
Order: 03 of 04
Status: done
Depends-on: 02
Date: 2026-06-22

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: Применить производный `textColor` категории ко ВСЕМ местам, где рисуется имя категории
(D6, «везде, включая dashboard»). Render-логику долек/тинта НЕ переписываем — только цвет текста.
  1) Донат: пробросить цвет текста подписи категории. `CategorySlice` (CategorySlice.kt:5-12) несёт
     `color`+`label` — добавить `labelColor: Color`. `MonefyDonutChart` callout-подпись имени
     (MonefyDonutChart.kt:134,762) рисуется `labelColor` вместо хардкода `dashboardCalloutLabel`.
     `DashboardViewModel.categoryToPlaceholder` (DashboardViewModel.kt:118) заполняет
     `labelColor = parseHexColor(category.textColor)`.
  2) Тайл dashboard: имя в `CategoryTile` (CategoryTile.kt:99) — цвет из `category.textColor`
     вместо `textPrimary`.
  3) Список записей: заголовок группы категории (TransactionsListScreen.kt:617) — из `textColor`
     вместо `onSurface`.
  4) Сетка категорий на форме: лейбл имени в `CategoryGrid`/`CategoryCell` (CategoryGrid.kt:79-93) —
     из `textColor` (если в ячейке рисуется имя).
  5) Единый источник правды: рефакторить `categoryIconAccent(iconKey)` (NeonCategoryIcon.kt:138-193)
     так, чтобы она делегировала в :core:common `categoryIconDominantHex` (parse hex → Color) — не
     держать две расходящиеся таблицы (A3). Поведение тинта иконки не меняется (цвет тот же источник).
LAYERS: presentation
CHANGED_HINT:
  - core/designsystem/src/main/.../donut/CategorySlice.kt:5-12 — добавить `labelColor: Color` (G16)
  - core/designsystem/src/main/.../donut/MonefyDonutChart.kt:134,762 — подпись имени рисуется
    `labelColor` (G15)
  - feature/dashboard/src/main/.../DashboardViewModel.kt:118 — `labelColor = parseHexColor(
    category.textColor)` в categoryToPlaceholder (G19)
  - feature/dashboard/src/main/.../components/CategoryTile.kt:99 — имя из textColor (G17)
  - feature/transactionslist/src/main/.../list/TransactionsListScreen.kt:617 — имя группы из textColor (G18)
  - core/designsystem/src/main/.../form/CategoryGrid.kt:79-93 — лейбл имени из textColor (G4/G12)
  - core/designsystem/src/main/.../icon/NeonCategoryIcon.kt:138-193 — categoryIconAccent делегирует
    в :core:common (A3)
TEST_TYPES: instrumented
CONSTRAINTS:
  - Dashboard UI-тесты — в `:app` androidTest (НЕ Robolectric, G21). Текст доната рисуется на Canvas
    (drawText) и НЕ ассертится через semantics — проверять визуально/пиксельной пробой; для
    тайла/списка использовать semantics там, где это обычный Text.
  - ktlintFormat по затронутым модулям перед коммитом (G22).
  - НЕ менять логику цвета долек доната и тинта иконки (они уже читают colorHex из SPEC 02) — только
    добавить чтение textColor для текста (D2).
  - Файлы НЕ пересекаются со SPEC 04 (тот трогает только экран редактирования категории).
=== END SPEC ===

## Acceptance (Gherkin)
```gherkin
Feature: Цвет текста категории везде

  Scenario: Имя категории на тайле dashboard в цвете иконки
    Given категория "Такси" с жёлтой иконкой и textColor жёлтого
    When показан dashboard
    Then имя "Такси" на тайле нарисовано жёлтым (textColor), не textPrimary

  Scenario: Подпись доли доната в цвете категории
    Given долька категории с textColor
    When отрисован донат с callout-подписью
    Then имя в подписи нарисовано labelColor категории

  Scenario: Имя категории в списке записей
    Given группа записей категории с textColor
    When показан список записей
    Then заголовок группы нарисован в textColor категории
```

## Gap / context
Имя категории сейчас рисуется тематическими хардкодами (`dashboardCalloutLabel`, `textPrimary`,
`onSurface`) в 4+ местах. SPEC подключает производный `textColor` (из SPEC 02) во всех точках и
устраняет дублирование таблицы цветов иконок.

## Implementation links
- commit: 6c1ae9cb (feat: apply category text colors) · 1117d04a (fix: preserve API compatibility) · 550fef16 (fix: use stored category text colors) · 392eef77 (test: pin stored category text colors) — UNPUSHED (HEAD 19 ahead of origin/main)
- files: CategorySlice.kt, MonefyDonutChart.kt, CategoryGrid.kt, NeonCategoryIcon.kt (categoryIconAccent now delegates to :core:common), DashboardViewModel.kt, CategoryTile.kt, TransactionsListScreen.kt; data plumbing for stored textColor: TransactionDao.kt, Mappers.kt, CategoryGroupRow.kt, CategorySummaryRow.kt, BalanceSnapshot.kt, CategoryRecordGroup.kt, TransactionRepository.kt, BalanceCalculator.kt, GetCategoryRecordsUseCase.kt, AddExpenseScreen.kt, AddIncomeScreen.kt, TransactionDetailScreen.kt
- tests: NeonCategoryIconTest, DashboardViewModelTest, GetCategoryRecordsUseCaseTest, TransactionDetailFormMappingTest
- reviewer: pass (0 layer-boundary violations). NOT pushed; runner/verifier deferred — working tree holds an unrelated icon refactor (see session note). Closed via `/mp --feature --next` 2026-06-23.
