# Режим «раздельно» — мини-графики в валютных карточках
Epic: dashboard-balance-trend-chart
Order: 07 of 07
Status: done
Depends-on: 01, 03, 05
Date: 2026-06-21

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: В режиме «Все счета → раздельно» добавить мини-`BalanceTrendChart` накопленного баланса в каждую валютную карточку. ViewModel считает тренд на каждую валюту (через `BalanceTrendCalculator` поверх `balanceCalculator.forAccounts(group, currency, period)`) и кладёт в `CurrencyBalanceCard`. Мини-график учитывает глобальный конфиг (стиль/метрика/цвет), но с урезанным оформлением (без подписей/линий при нехватке места). Если график скрыт в настройках — мини-графиков нет.
LAYERS: presentation
CHANGED_HINT:
  - feature/dashboard/src/main/java/com/kshavrin/mymoney/feature/dashboard/DashboardState.kt:60 — `CurrencyBalanceCard` + поле `trendPoints: List<TrendPoint>` (G17).
  - feature/dashboard/src/main/java/com/kshavrin/mymoney/feature/dashboard/DashboardViewModel.kt:579 `computeCurrencyCards()` — на каждую валютную группу посчитать тренд provider'ом поверх `balanceCalculator.forAccounts(group, currency, period)` (G7, G17).
  - feature/dashboard/src/main/java/com/kshavrin/mymoney/feature/dashboard/components/CurrencyBalanceCardList.kt:57 `CurrencyBalanceCardItem` — добавить мини-график под суммами; стиль/видимость из конфига (G17, O4).
TEST_TYPES: unit compose-ui
CONSTRAINTS:
  - Зависит от 01 (калькулятор), 03 (компонент), 05 (интеграция + конфиг в state). Same-file clash по `DashboardState.kt`/`DashboardViewModel.kt` с 05/06 — последовательно.
  - Каждая валюта — свой ряд В СВОЕЙ валюте, без конвертации (G17, G11 — `forAccounts` требует одну валюту на вызов).
  - Мини-график урезанный: при нехватке места без подписей/декоративных линий (O4); уважает `chartVisible=false` (скрыт → мини-графиков нет).
  - Раннер компилирует androidTest (memory) — обновить тест(ы) `CurrencyBalanceCardList`/separate-mode.
  - Без хардкод-строк; zero comments кроме неочевидного WHY.
=== END SPEC ===

## Acceptance
```gherkin
Scenario: Мини-график на валютную карточку
  Given выбрано «Все счета → раздельно», есть счета в RUB и USD
  When дашборд отрисован
  Then каждая валютная карточка (RUB, USD) содержит свой мини-график тренда

Scenario: Уважение скрытия графика
  Given в настройках график скрыт (chartVisible=false)
  When показан режим «раздельно»
  Then мини-графики не отображаются

Scenario: Ряд считается в валюте карточки без конвертации
  Given карточка USD
  Then точки тренда посчитаны только по USD-счетам, без кросс-курса
```

## Gap / context
Закрывает решение D14: в мультивалютном «раздельно» один общий график не имеет смысла, поэтому тренд показывается
на каждой валютной карточке отдельно, в её собственной валюте.

## Implementation links
- commit: 23d92a19 (token), 51b5bfe1 (feat), dbd9d0f0 (test import fix), 146c810f (viewmodel tests)
- files:
  - core/ui/src/main/java/com/kshavrin/mymoney/core/ui/theme/Spacing.kt (trendChartMiniHeight)
  - core/designsystem/src/main/java/com/kshavrin/mymoney/core/designsystem/chart/BalanceTrendChart.kt (compact rendering)
  - feature/dashboard/src/main/java/com/kshavrin/mymoney/feature/dashboard/DashboardState.kt (CurrencyBalanceCard.trendPoints)
  - feature/dashboard/src/main/java/com/kshavrin/mymoney/feature/dashboard/DashboardViewModel.kt (computeCurrencyCards per-currency trend)
  - feature/dashboard/src/main/java/com/kshavrin/mymoney/feature/dashboard/components/CurrencyBalanceCardList.kt (mini-chart in card)
  - feature/dashboard/src/main/java/com/kshavrin/mymoney/feature/dashboard/DashboardScreen.kt (chartConfig wiring)
  - feature/dashboard/src/test/java/com/kshavrin/mymoney/feature/dashboard/DashboardViewModelTest.kt
  - app/src/androidTest/java/com/kshavrin/mymoney/feature/dashboard/components/CurrencyBalanceCardListUiTest.kt (device 12/12)
