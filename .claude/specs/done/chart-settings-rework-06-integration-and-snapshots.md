# Реворк настроек графика — интеграция, мини-чарты, снапшоты
Epic: chart-settings-rework
Order: 06 of 06
Status: done
Depends-on: 03, 04, 05
Date: 2026-08-20
Completed: 2026-08-22

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: Завершить интеграцию: мини-чарты separate-mode и основной чарт в `AuroraBalanceCard`
получают `showProjection` из ChartConfig; проверить, что новые режимы цвета/стили корректно
работают в мини-чартах (H3); перезаписать Roborazzi baseline (`balance_trend_light/dark`,
дашборд) под новый рендер; удалить мёртвые строки 17 старых стилей и 3 старых правил цвета
из values/values-ru; финальный визуальный прогон на устройстве.
LAYERS: presentation
CHANGED_HINT:
  - feature/dashboard/src/main/java/com/kshavrin/mymoney/feature/dashboard/components/AuroraBalanceCard.kt:97-121 — прокинуть `showProjection = chartConfig.showProjection` в BalanceTrendChart (G12).
  - feature/dashboard/src/main/java/com/kshavrin/mymoney/feature/dashboard/components/CurrencyBalanceCardList.kt:31,89 — мини-чарты: прокинуть showProjection; визуально проверить by-direction и заливку на мини-размере (G10, H3).
  - core/designsystem/src/test/.../screenshot/DesignSystemScreenshotTest.kt:58-67 + feature/dashboard/src/test/.../DashboardScreenshotTest.kt:124-134 — `recordRoborazziDebug` re-record baseline под новый рендер (F8).
  - feature/dashboard src/main/res/values*/strings.xml — удалить строки 17 удалённых стилей и старых color-rule (G9, F9) — файлы НЕ удалять, только ключи.
  - Ручной визуальный прогон на Pixel_5 API 34: 3 стиля × 4 цвета × проекции вкл/выкл (F11).
TEST_TYPES: unit instrumented
CONSTRAINTS:
  - Последний SPEC эпика: depends 03, 04, 05.
  - F8: re-record baseline — осознанное решение (новый рендер), зафиксировать в коммите.
  - F11 + visual gate: connected run через `scripts/run_connected_test_on_host_avd.ps1`; без устройства не верифицировать.
  - F12: pre-existing red на main (AuroraBalanceCardUiTest «compact», MainActivity journey timeouts) — не регрессии, не чинить в этом эпике.
  - НЕ удалять файлы (policy) — только строковые ключи и код.
  - Мини-чарты следуют тому же ChartConfig целиком, включая заливку (assumption H3); если визуально шумно — отдельный follow-up SPEC, не блокировать эпик.
Acceptance-matrix: style=bars,line,smooth; color=solid,always_green,always_red,by_direction; projection=on,off
Risk-signals: cross-module data flow, visual/device work
DESIGN_TOKENS: colorScheme.trendChartProjectionAbove, colorScheme.trendChartProjectionBelow
=== END SPEC ===

## Acceptance
```gherkin
Scenario: Мини-чарты используют новый конфиг
  Given separate-mode и режим ByDirection
  Then мини-чарты карточек валют красятся относительно своей стартовой точки

Scenario: Скрытый график скрывает и проекцию
  Given chartVisible=false
  Then ни основной график, ни заливка не рисуются; видна hidden-hint плашка

Scenario: Baseline обновлён
  Given новый рендер графика
  Then verifyRoborazziDebug зелёный на обновлённых baseline
```

## Gap / context
Интеграционная подметалка: все потребители ChartConfig на новом контракте, снапшоты и
строки соответствуют 3 стилям / 4 режимам.

## Handoff
- 2026-08-22: User approved proceeding without splitting this SPEC despite the size-gate
  recommendation; use `size_override=1` for this run.
- 2026-08-22: Semantic repair cycle 1 fixed themed projection-token wiring; consumer matrix
  tests and fresh Pixel 5/API 34 XML cover all 24 acceptance cells. Full Aurora class still
  reports only the SPEC-exempt F12 compact/typography baseline failures.

## Implementation links
- commit: 9055e990, 8a3e4a72, 882726fc, efd6d023, d2bff540
- files: core/ui/src/main/java/com/kshavrin/mymoney/core/ui/theme/Color.kt; core/designsystem/src/main/java/com/kshavrin/mymoney/core/designsystem/chart/BalanceTrendChart.kt; core/designsystem/src/test/screenshots/balance_trend_light.png; core/designsystem/src/test/screenshots/balance_trend_dark.png; feature/dashboard/src/main/java/com/kshavrin/mymoney/feature/dashboard/components/AuroraBalanceCard.kt; feature/dashboard/src/main/java/com/kshavrin/mymoney/feature/dashboard/components/CurrencyBalanceCardList.kt; feature/dashboard/src/test/screenshots/dashboard_day_light.png; feature/dashboard/src/test/screenshots/dashboard_day_dark.png; app/src/androidTest/java/com/kshavrin/mymoney/feature/dashboard/AuroraBalanceCardUiTest.kt; app/src/androidTest/java/com/kshavrin/mymoney/feature/dashboard/components/CurrencyBalanceCardListUiTest.kt
