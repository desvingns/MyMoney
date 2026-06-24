# Тумблер «авто/вручную» в настройках графика
Epic: dashboard-trend-selected-period
Order: 05 of 05
Status: done
Depends-on: 04
Date: 2026-06-22

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: Добавить в `ChartSettingsSheet` переключатель «Авто / Вручную», привязанный к `chartAutoMode` (из SPEC 04). В авто-режиме контролы «тип периода» (`periodType`) и «число точек» (`pointCount`) СКРЫТЫ (график следует выбранному периоду); в ручном — показаны как сейчас. Переключение персистится через `appSettingsRepository.update`. Стиль/метрика/цвет/видимость доступны в обоих режимах.
LAYERS: presentation
CHANGED_HINT:
  - feature/dashboard/.../components/ChartSettingsSheet.kt — добавить верхний тумблер «Авто/Вручную» (bound к `chartConfig.autoMode`); контролы period-type + point-count рендерить только при `!autoMode` (G11/G12).
  - feature/dashboard/.../ChartConfigMapping.kt — если событие/сеттер `chartAutoMode` добавляется здесь (клэш с SPEC 04 → последовательно после 04) (G12).
  - feature/dashboard/.../DashboardViewModel.kt — обработчик события переключения авто/вручную (через существующий паттерн обновления `ChartConfig`/AppSettings; reuse, не дублировать) (G8).
  - app/src/androidTest/java/com/kshavrin/mymoney/feature/dashboard/ChartSettingsSheetUiTest.kt — расширить: тумблер виден; в авто period-type+point-count скрыты; в ручном — видны (G15).
  - Новые user-facing строки («Авто», «Вручную», подпись режима) — в `res/values/strings.xml` + `res/values-ru/strings.xml` (без хардкода, правило проекта).
TEST_TYPES: compose-ui, instrumented
CONSTRAINTS:
  - Делит `ChartConfig`/`ChartConfigMapping`/`ChartSettingsSheet` с SPEC 04 — строго после 04 (нет параллельной правки).
  - UI-тесты графика/листа — `:app` androidTest (instrumented), НЕ Robolectric; запускать на устройстве (G15). Off-screen контролы тапать после `.performScrollTo()`.
  - Никаких новых строк в коде хардкодом; и EN, и RU значения (правило проекта).
  - Семантические тесты не ловят перекрытие отрисовкой — проверить видимость/скрытие визуально на устройстве (как отмечалось по другим dashboard-правкам).
=== END SPEC ===

## Acceptance (Gherkin, UI-agnostic)
```gherkin
Feature: Переключатель режима графика
  Покрывает D1.

  @settings
  Scenario: В авто-режиме ручные контролы скрыты
    Given открыт лист настроек графика
    And включён режим «Авто»
    Then выбор типа периода и слайдер числа точек не отображаются

  @settings
  Scenario: Переключение в ручной режим открывает контролы
    Given открыт лист настроек графика в режиме «Авто»
    When пользователь переключает режим на «Вручную»
    Then появляются выбор типа периода и слайдер числа точек
    And график переключается на ручное поведение (текущий + предыдущие)

  @settings
  Scenario: Выбор режима сохраняется
    Given пользователь переключил режим на «Вручную»
    When лист настроек закрыт и открыт снова
    Then режим остаётся «Вручную»
```

## Gap / context
Закрывает разрыв: после SPEC 04 авто-режим — дефолт, но без UI его нельзя переключить на ручной. Этот SPEC даёт пользователю тумблер и прячет ставшие неактуальными в авто контролы (D1: ручной override сохраняется).

## Implementation links
- commit: 92634d0b (feat) + 35a18129 (test)
- files:
  - feature/dashboard/src/main/java/com/kshavrin/mymoney/feature/dashboard/components/ChartSettingsSheet.kt
  - feature/dashboard/src/main/java/com/kshavrin/mymoney/feature/dashboard/DashboardState.kt (ChartConfig.autoMode, DashboardEvent.ChartAutoModeChanged)
  - feature/dashboard/src/main/java/com/kshavrin/mymoney/feature/dashboard/DashboardViewModel.kt (ChartAutoModeChanged handler → updateChartSettings)
  - feature/dashboard/src/main/res/values/strings.xml + values-ru/strings.xml (chart_settings_mode_auto/manual, chart_settings_section_mode)
  - app/src/androidTest/java/com/kshavrin/mymoney/feature/dashboard/ChartSettingsSheetUiTest.kt (44/44 on emulator-5554)
