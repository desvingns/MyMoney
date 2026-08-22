# Реворк настроек графика — epic overview
Epic: chart-settings-rework
Order: 00 of 06
Status: done
Depends-on: —
Date: 2026-08-20
Completed: 2026-08-22

## Goal

Упростить и переориентировать настройки графика тренда баланса (шторка «Настройки графика» из
правого drawer, G2): вместо 20 визуальных стилей — 3 (столбики / прямые / изогнутые); вместо
правил цвета {BySign, Income, Expense} — {однотонный, всегда зелёный, всегда красный,
по направлению от стартовой точки}; новый независимый тумблер «Проекции» — двухцветная
заливка площади между линией и осью времени. Существующие тумблеры «скрыть график» и
«скрыть подписи (дни)» сохраняются без изменений. Out of scope: forecast-прогноз в будущее,
color-picker, настройки периода/метрики/gridlines/auto-mode.

## Locked decisions (из grill.md)

- D1: «Проекции» = заливка площади между линией графика и нулевой осью (ось времени), НЕ
  forecast. Тумблер вкл/выкл, default = off. Заливка ВСЕГДА двухцветная: выше оси — зелёная,
  ниже — красная, независимо от режима цвета линии. На столбики не влияет.
- D2: Стили 20 → 3: bars / line / smooth. Legacy id в DataStore маппятся на ближайшее
  семейство (O1 assumption).
- D3: Режимы цвета = { Solid (акцент темы), AlwaysGreen, AlwaysRed, ByDirection };
  заменяют { BySign, Income, Expense }. Дефолт = ByDirection (O3 assumption — наследует
  семантику старого дефолта by_sign).
- D4: ByDirection красит пер-сегментно относительно горизонтальной линии ПЕРВОЙ точки:
  выше — зелёный, ниже — красный; сегмент делится в точке пересечения (интерполяция).
- D5: Тумблер проекций default = выключен.
- O2 (assumption): period type / point count / metric / gridlines / labels / visible /
  auto-mode не трогаем.
- O4 (assumption): все значения одного знака → заливка одноцветная.

## SPECs (run via /mp --feature --next in Order)

| Order | File | Depends-on | Layers | Summary |
|---|---|---|---|---|
| 01 | `chart-settings-rework-01-style-reduction.md` | — | presentation | ChartStyle 20→3 + рендер свёрнут + legacy style-id mapping |
| 02 | `chart-settings-rework-02-projection-persistence.md` | — | data | `chartShowProjection` в AppSettings/DataStore + тесты |
| 03 | `chart-settings-rework-03-renderer-color-and-fill.md` | 01 | presentation | 4 режима цвета, per-segment by-direction, заливка-проекция |
| 04 | `chart-settings-rework-04-dashboard-config-wiring.md` | 02, 03 | presentation | ChartConfig/mapping/VM: showProjection + color-id mapping |
| 05 | `chart-settings-rework-05-settings-sheet-rework.md` | 03, 04 | presentation | Шторка: 3 тумбы, 4 режима цвета, тумблер проекций, EN+RU |
| 06 | `chart-settings-rework-06-integration-and-snapshots.md` | 03, 04, 05 | presentation | Мини-чарты, Roborazzi re-record, чистка мёртвых строк |

## Why this ordering

01 и 02 независимы (разные модули: core:designsystem vs core:datastore). 03 расширяет рендер
поверх свёрнутых 3 стилей — same-file clash по `BalanceTrendChart.kt` с 01 → строго после.
04 зависит от persistence (02) и новых enum-ов (03); same-file clash по `ChartConfigMapping.kt`
с 01 (там style-id mapping, тут color-id mapping) → после 01. 05 — UI поверх 03+04. 06 —
интеграционная подметалка последней.

## Key facts (verified)

- G1: шторка `ChartSettingsSheet` — `feature/dashboard/.../components/ChartSettingsSheet.kt:55`.
- G2: вход — правый drawer → `DashboardEvent.ChartSettingsClicked` — `RightDrawerContent.kt:85`.
- G4/D2: `enum ChartStyle` (20) + `enum ChartColorRule {BySign, Income, Expense}` —
  `core/designsystem/.../chart/ChartStyle.kt:3-43`.
- G5/D8: `BalanceTrendChart(points, labels, metricLabel, showGridlines, showLabels, colorRule,
  style, chartHeight)`; цвет сейчас — по знаку последней точки — `BalanceTrendChart.kt:219,233-243`.
- D1: chart-поля `AppSettings` — `core/datastore/.../model/AppSettings.kt:32-40`; полей
  projection/direction нет.
- D3/D9: добавление поля = `AppSettingsKeys` + `toAppSettings()`/`writeTo()` +
  `AppSettingsRepositoryTest` round-trip — `AppSettingsKeys.kt:34-42`, тест :130-163.
- D6: mapping id↔enum с fallback на default — `feature/dashboard/.../ChartConfigMapping.kt:12-101`.
- D7: запись настроек только через `updateChartSettings` → `appSettingsRepository.update {}` —
  `DashboardViewModel.kt:1155-1192`.
- G10/H3: мини-чарты separate-mode потребляют тот же ChartConfig — `CurrencyBalanceCardList.kt:31,89`.
- G11: аннотации/дни = `labels`, тумблер уже есть — `ChartLabels.kt:16`, `DashboardState.kt:133`.
- G12: основной чарт в `AuroraBalanceCard` — `AuroraBalanceCard.kt:97-121`.
- F6-F8, F13: тесты под ударом — `BalanceTrendChartUiTest` (семантика + 20 стилей),
  `ChartSettingsSheetUiTest` (25 device), Roborazzi `balance_trend_*`; actions SharedFlow replay=0.
- F9/F10: l10n-gate (EN+RU парой), a11y-gate (touch ≥48dp).
- F11: visual gate — instrumented через `scripts/run_connected_test_on_host_avd.ps1` (Pixel_5 API 34).

## Implementation links
- commit: 0a2c4882, 0d4552b1, 3e47ce22, d0e754da, a2158dc5, 8ddacb88e7394d837afc6cf398978f3fbfc0f81a, 470be398, 75483b78, 393e21c1, 10b9806a, 66f2028c, c2093c5682b10dc0876e28e3b6731c5bc32e4676, 9055e990, 8a3e4a72, 882726fc, efd6d023, d2bff540
- files: chart settings persistence, renderer/style/color/projection integration, dashboard mapping and settings sheet, AuroraBalanceCard and CurrencyBalanceCardList wiring, Roborazzi baselines, and consumer matrix instrumented tests (see child SPEC implementation links)
