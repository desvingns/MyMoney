# График тренда — ~20 визуальных стилей
Epic: dashboard-balance-trend-chart
Order: 04 of 07
Status: done
Depends-on: 03
Date: 2026-06-21

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: Реализовать ≈20 визуальных стилей графика как значения `ChartStyle`, диспетчеризуемые в `BalanceTrendChart`. Цель — исследовательская: пользователь посмотрит варианты на устройстве и потом отберёт/улучшит. Предлагаемый каталог (можно корректировать): 1 neon_line (default), 2 neon_area, 3 smooth_line (Безье), 4 smooth_area, 5 stepped_line, 6 stepped_area, 7 bars, 8 rounded_bars, 9 dots_line, 10 dots_only, 11 gradient_stroke, 12 dual_glow, 13 dashed_line, 14 thin_minimal, 15 thick_bold, 16 baseline_fill, 17 vertical_gradient_area, 18 candy_segments (цвет по знаку посегментно), 19 mountain, 20 ribbon.
LAYERS: presentation
CHANGED_HINT:
  - core/designsystem/src/main/java/com/kshavrin/mymoney/core/designsystem/chart/ChartStyle.kt — расширить enum до ~20 значений (G12).
  - core/designsystem/src/main/java/com/kshavrin/mymoney/core/designsystem/chart/BalanceTrendChart.kt — диспетчер `when(style)` → приватные рисовалки на каждый стиль (G12).
  - core/designsystem/src/main/java/com/kshavrin/mymoney/core/designsystem/chart/ChartStyleGallery.kt (new, опц.) — preview/Roborazzi-галерея всех стилей для отбора на устройстве. (assumption)
TEST_TYPES: compose-ui
CONSTRAINTS:
  - Один файл графика с SPEC 03 (same-file clash) — делать СТРОГО после 03 (Depends-on 03).
  - Все стили обязаны рендериться без краша на: 5 нормальных точках, 1 точке, всех нулях, отрицательных значениях.
  - Перф: переиспользовать хойстнутые `Paint`/`Path` (memory: donut draw-allocations); не плодить аллокации в onDraw.
  - Стиль — чистый параметр рендера; домен/данные не трогаем. Цвет/правило знака берутся из общих параметров (03).
  - Smoke-тест на каждый стиль (рендерится) и/или Roborazzi-галерея. English-идентификаторы; zero comments.
=== END SPEC ===

## Acceptance
```gherkin
Scenario: Все стили рендерятся
  Given список из ~20 значений ChartStyle и 5 точек данных
  When каждый стиль отрисован в BalanceTrendChart
  Then рендер проходит без исключений для каждого стиля

Scenario: Вырожденные данные не ломают стиль
  Given значения = все нули (или одна точка)
  When отрисован любой стиль
  Then рендер не падает
```

## Gap / context
Пользователь явно попросил «сделай 20 визуальных стилей, я посмотрю как они смотрятся на телефоне и отберём».
Это широта вариантов для последующего culling — отдельный исследовательский SPEC поверх базового компонента.

## Implementation links
- commit: dd9deb42, 511051db, e02e0d84
- files:
  - core/designsystem/src/main/java/com/kshavrin/mymoney/core/designsystem/chart/ChartStyle.kt
  - core/designsystem/src/main/java/com/kshavrin/mymoney/core/designsystem/chart/BalanceTrendChart.kt
  - core/designsystem/src/androidTest/java/com/kshavrin/mymoney/core/designsystem/chart/BalanceTrendChartUiTest.kt
