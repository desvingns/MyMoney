# Реворк настроек графика — проводка конфига в дашборд
Epic: chart-settings-rework
Order: 04 of 06
Status: done
Depends-on: 02, 03
Date: 2026-08-20
Acceptance-matrix: color_id=legacy,modern,unknown; projection=off,on; update_path=config_read,event_toggle
Risk-signals: persistence, cross-module-data-flow

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: Провести новые настройки через feature:dashboard: `ChartConfig` получает
`showProjection: Boolean`; `ChartConfigMapping` маппит новые color-id и legacy-значения
(by_sign→by_direction, income→always_green, expense→always_red — O1) и читает
`chartShowProjection` из AppSettings; `DashboardViewModel` получает событие
`ChartProjectionToggled(Boolean)` через существующий `updateChartSettings` (без recompute
тренда — визуальная настройка, D7). События смены стиля/цвета сигнатурно не меняются — новые
enum-значения текут по старым путям.
LAYERS: presentation
CHANGED_HINT:
  - feature/dashboard/src/main/java/com/kshavrin/mymoney/feature/dashboard/DashboardState.kt:145-171 — `ChartConfig` + `showProjection: Boolean = false` (G6, D5).
  - feature/dashboard/src/main/java/com/kshavrin/mymoney/feature/dashboard/ChartConfigMapping.kt:12-101 — `chartColorRuleFromId`: 4 новых id + legacy-маппинг O1; неизвестный id → Default; `toChartConfig()` читает chartShowProjection (D6, D9). Same-file clash с 01.
  - feature/dashboard/src/main/java/com/kshavrin/mymoney/feature/dashboard/DashboardState.kt:299-339 — `DashboardEvent.ChartProjectionToggled(Boolean)` (G7).
  - feature/dashboard/src/main/java/com/kshavrin/mymoney/feature/dashboard/DashboardViewModel.kt:1149-1197 — обработчик через `updateChartSettings(recomputeTrend = false) { copy(chartShowProjection = it) }` (D7).
  - feature/dashboard/src/test/.../ChartConfigMappingTest.kt — новые id, legacy-маппинг, showProjection round-trip (D6).
  - feature/dashboard/src/test/.../DashboardViewModelTest.kt — событие ChartProjectionToggled пишет в settings (F13: не пред-потреблять awaitItem).
TEST_TYPES: unit
CONSTRAINTS:
  - Depends 02 (поле в AppSettings) и 03 (новые enum) — не стартовать раньше.
  - Same-file clash `ChartConfigMapping.kt` с 01 — строго после 01.
  - Запись настроек ТОЛЬКО через `appSettingsRepository.update {}` (D4, F13).
  - recomputeTrend=false: проекция/цвет/стиль не влияют на точки тренда (D7).
  - VM unit-тесты с `savedStateHandle.toRoute` — @RunWith(RobolectricTestRunner) (memory).
=== END SPEC ===

## Acceptance
```gherkin
Scenario: Legacy color-id маппится на новый режим
  Given в DataStore chartColorRule="by_sign"
  When настройки читаются в ChartConfig
  Then colorRule = ByDirection

Scenario: Тумблер проекций сохраняется
  Given пользователь переключил «Проекции» в on
  Then AppSettings.chartShowProjection = true и ChartConfig.showProjection = true

Scenario: Смена проекции не пересчитывает тренд
  When приходит ChartProjectionToggled(true)
  Then точки тренда не пересчитываются (нет recomputeTrend)
```

## Gap / context
Связка data↔UI: новое persisted-поле (02) и новые enum (03) становятся доступны шторке (05).

## Implementation links
- commit: 470be398, 75483b78, 393e21c1, 10b9806a, 66f2028c
- files:
  - core/datastore/src/main/java/com/kshavrin/mymoney/core/datastore/usecase/DashboardDataUseCase.kt
  - core/datastore/src/test/java/com/kshavrin/mymoney/core/datastore/usecase/DashboardDataUseCaseTest.kt
  - feature/dashboard/src/main/java/com/kshavrin/mymoney/feature/dashboard/DashboardState.kt
  - feature/dashboard/src/main/java/com/kshavrin/mymoney/feature/dashboard/ChartConfigMapping.kt
  - feature/dashboard/src/main/java/com/kshavrin/mymoney/feature/dashboard/DashboardViewModel.kt
  - feature/dashboard/src/test/java/com/kshavrin/mymoney/feature/dashboard/ChartConfigMappingTest.kt
  - feature/dashboard/src/test/java/com/kshavrin/mymoney/feature/dashboard/DashboardViewModelTest.kt
  - app/src/androidTest/java/com/kshavrin/mymoney/ImportFocusColdStartRegressionTest.kt
