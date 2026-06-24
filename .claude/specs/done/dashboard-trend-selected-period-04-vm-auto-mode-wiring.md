# Проводка авто-режима в DashboardViewModel
Epic: dashboard-trend-selected-period
Order: 04 of 05
Status: done
Depends-on: 01, 02, 03
Date: 2026-06-22

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: Ввести флаг `chartAutoMode` (дефолт `true`) в `AppSettings` + `ChartConfig` и переключить вычисление графика. В АВТО-режиме: график (карточка «Аврора» И per-currency мини-графики «раздельно», D5) строится за **выбранный период** dashboard через `buildAutoWindow` + `buildAutoSeries` (SPEC 01/02); при `period is Period.Day` — через intra-day путь (SPEC 03) на сырых транзакциях; подписи точек (числа дней / сокращения месяцев / часы) передаются в `BalanceTrendChart(labels=…)`. В РУЧНОМ режиме (`!autoMode`) — текущий путь без изменений (`buildWindow` + `trendAnchorPeriod` + `pointCount`, G8/G9/G10). Авто-режим всегда якорится на текущем `state.period` (без независимого `trendAnchorPeriod`).
LAYERS: data, presentation
CHANGED_HINT:
  - core/datastore/.../model/AppSettings.kt — добавить `chartAutoMode: Boolean = true` (assumption: путь `:core:datastore`, сверить) — аддитивный preference, БЕЗ Room-миграции; запись через атомарный `appSettingsRepository.update { … }` (RMW-гонка закрыта audit2-03).
  - feature/dashboard/.../DashboardState.kt:78-97 — добавить `autoMode: Boolean` в `ChartConfig` (G11).
  - feature/dashboard/.../ChartConfigMapping.kt:83-93 — маппить `chartAutoMode` → `ChartConfig.autoMode` в `toChartConfig()` (G12).
  - feature/dashboard/.../DashboardViewModel.kt:382-396 (recomputeBalance) — если `chartConfig.autoMode`: `period is Period.Day` → intra-day (SPEC 03, через `findByPeriod` G6 по счетам выборки + merge); иначе `buildAutoSeries(buildAutoWindow(period, earliestDate, today), metric, provider)` (SPEC 01/02); иначе (ручной) — текущий `buildWindow`+`invoke` (G8).
  - feature/dashboard/.../DashboardViewModel.kt:604-650 (computeCurrencyCards) — та же авто/ручная развилка per-currency (D5, G10), снапшоты через `balanceCalculator.forAccounts(group,currency,bucket)`; для Day — intra-day на транзакциях группы.
  - feature/dashboard/.../DashboardViewModel.kt — источник `earliestDate` для All: лёгкий запрос min(occurredAt) (assumption O2: новый DAO/repo-метод или derive из `observeAll().first()`).
  - feature/dashboard/.../components/AuroraBalanceCard.kt + CurrencyBalanceCardList.kt — прокинуть `labels` в `BalanceTrendChart` (G13); число дней — числовые, месяцы — локализованные (без хардкода строк), часы — числовые.
  - feature/dashboard/.../DashboardViewModelTest.kt — обновить дефолтные ассерты `trendPoints` под авто-путь (H3/G14); добавить кейсы Week/Month/Year/Day/Range + «раздельно».
  - feature/dashboard/.../ChartConfigMappingTest.kt — кейс маппинга `chartAutoMode`.
TEST_TYPES: unit, instrumented
CONSTRAINTS:
  - Делит `ChartConfig`/`ChartConfigMapping` с SPEC 05 — последовательно (04 раньше 05).
  - `BalanceTrendChart`/геометрия должны пережить серию из <2 точек (1 точка или `∅`) — не падать, рисовать точку/пусто (G13).
  - mp-runner ПРОПУСКАЕТ тесты `:feature:*` — `DashboardViewModelTest`/`ChartConfigMappingTest` проверять прямо (`:feature:dashboard:testDebugUnitTest`); ktlint-гейт → `:feature:dashboard:ktlintFormat`; chart UI — `:app` androidTest на устройстве (G15).
  - Нет хардкода user-facing строк (подписи месяцев — через локаль) — правило проекта.
  - Ручной режим — поведение байт-в-байт как сейчас (G14 тесты `buildWindow` остаются зелёными).
=== END SPEC ===

## Acceptance (Gherkin, UI-agnostic)
```gherkin
Feature: График следует выбранному периоду (авто-режим)
  Покрывает D1, D5.

  @wiring
  Scenario: Смена периода перестраивает график под выбранный период
    Given свежая установка (авто-режим по умолчанию)
    And выбран период «Месяц»
    When пользователь переключает период на «Неделя»
    Then график перестраивается на 7 точек (по дням недели)

  @wiring @separate
  Scenario: В режиме «раздельно» мини-графики тоже следуют выбранному периоду
    Given выбраны «Все счета» в режиме «раздельно»
    And выбран период «Год»
    When график строится в авто-режиме
    Then каждый per-currency мини-график показывает 12 точек по месяцам в своей валюте

  @wiring @manual
  Scenario: Ручной режим сохраняет старое поведение
    Given включён ручной режим с типом периода «Месяц» и числом точек 5
    When график строится
    Then график показывает 5 точек (текущий месяц + 4 предыдущих)

  @wiring @default
  Scenario: По умолчанию включён авто-режим
    Given свежая установка без сохранённых настроек графика
    When открыт dashboard
    Then график построен за выбранный период (авто-режим)

  @wiring @boundary
  Scenario: Период без записей не роняет график
    Given выбран период без единой записи
    When график строится в авто-режиме
    Then график пуст и не падает на серии из <2 точек
```

## Gap / context
Закрывает разрыв между доменными калькуляторами (01/02/03) и UI: VM начинает использовать авто-путь по умолчанию для «Авроры» и per-currency карточек, сохраняя ручной режим как override (D1). `chartAutoMode` персистится в DataStore (без миграции).

## Implementation links
- commit: 5e457d2e (impl) + 360bf5d3 (androidTest compile reconcile) + 2253609f (tests)
- files:
  - core/datastore: AppSettings.kt, AppSettingsKeys.kt, AppSettingsRepositoryImpl.kt (+chartAutoMode default true)
  - feature/dashboard: DashboardState.kt, ChartConfigMapping.kt, DashboardViewModel.kt, DashboardScreen.kt, components/AuroraBalanceCard.kt, components/ChartLabels.kt (new)
  - tests: ChartLabelsTest.kt (new 23), ChartConfigMappingTest.kt, DashboardViewModelTest.kt, app androidTest AuroraAutoModeTrendLabelUiTest.kt (new, device 1/1)
