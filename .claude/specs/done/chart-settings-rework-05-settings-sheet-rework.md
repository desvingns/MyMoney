# Реворк настроек графика — шторка настроек
Epic: chart-settings-rework
Order: 05 of 06
Status: done
Depends-on: 03, 04
Date: 2026-08-20

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: Обновить шторку «Настройки графика» под новый контракт: ряд стилей показывает 3 тумбы
(автоматически по `ChartStyle.entries`, D10); сегментированный ряд правила цвета — 4 новых
режима (Однотонный / Всегда зелёный / Всегда красный / По направлению) вместо
BySign/Income/Expense; новый `ToggleRow` «Проекции» → `DashboardEvent.ChartProjectionToggled`.
Новые строки EN+RU с нуля (заодно уходит рассинхрон «По тренду», G13/H4). Секции периода,
метрики, gridlines, labels, visible — без изменений (O2).
LAYERS: presentation
CHANGED_HINT:
  - feature/dashboard/src/main/java/com/kshavrin/mymoney/feature/dashboard/components/ChartSettingsSheet.kt:55 — секция цвета: 4 `SegmentOption` с новыми строками (D10: :327-344); новый ToggleRow проекций рядом с labels/visible (G1, :131-142).
  - feature/dashboard/src/main/res/values/strings.xml + values-ru/strings.xml — `chart_settings_color_solid` / `_always_green` / `_always_red` / `_by_direction`, `chart_settings_projection` (+CD-строки) парой EN+RU (F9, G9).
  - app/src/androidTest/java/com/kshavrin/mymoney/feature/dashboard/ChartSettingsSheetUiTest.kt — тумбы 20→3, сегменты цвета 3→4, новый тумблер; тесты эмиссии событий (F7).
  - app/src/androidTest/.../DashboardContentUiTest.kt — если ассерты завязаны на старые ярлыки/контролы (F7).
TEST_TYPES: unit compose-ui instrumented
Acceptance-matrix: control=style,color_rule,projection,display; locale=en,ru
Risk-signals: visual/device work
CONSTRAINTS:
  - Depends 03 (enum-ы) и 04 (событие + конфиг).
  - F9: lint MissingTranslation/ExtraTranslation = error — строки только парой EN+RU; `L10nParityTest` зелёный.
  - F10: a11y-gate — touch-target ≥48dp, ATF включён в UiTest; новые контролы под теми же правилами; CD-строки обязательны.
  - F7: раннер компилирует androidTest — 25 device-тестов шторки обновить синхронно.
  - F13: actions SharedFlow replay=0 — не пред-потреблять awaitItem в тестах.
  - Device gate: визуальные изменения верифицировать на Pixel_5 API 34 через `scripts/run_connected_test_on_host_avd.ps1` (F11).
=== END SPEC ===

## Acceptance
```gherkin
Scenario: Три тумбы стилей
  Given открыта шторка настроек графика
  Then ряд стилей содержит ровно 3 превью: столбики, прямые, изогнутые

Scenario: Четыре режима цвета
  Given открыта шторка
  When пользователь выбирает «По направлению»
  Then эмитится ChartColorRuleChanged(ByDirection) и выбор сохраняется

Scenario: Тумблер проекций
  Given открыта шторка
  When пользователь включает «Проекции»
  Then эмитится ChartProjectionToggled(true)

Scenario: Скрытие графика и аннотаций работают как раньше
  Given открыта шторка
  Then тумблеры «показывать график» и «подписи» присутствуют и функционируют
```

## Gap / context
Пользовательская поверхность реворка: простая шторка с 3 стилями, 4 режимами цвета и
тумблером проекций поверх существующих тумблеров скрытия графика/подписей.

## Implementation links
- commit: c2093c5682b10dc0876e28e3b6731c5bc32e4676
- files: feature/dashboard/src/main/java/com/kshavrin/mymoney/feature/dashboard/components/ChartSettingsSheet.kt; app/src/androidTest/java/com/kshavrin/mymoney/feature/dashboard/ChartSettingsSheetUiTest.kt
