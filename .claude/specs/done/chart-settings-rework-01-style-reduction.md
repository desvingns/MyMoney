# Реворк настроек графика — сокращение стилей 20→3
Epic: chart-settings-rework
Order: 01 of 06
Status: done
Depends-on: —
Date: 2026-08-20
Acceptance-matrix: style=bars,line,smooth; persisted_id=canonical,legacy,unknown; render=bars,segment,smooth
Risk-signals: visual/device work, cross-module data flow

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: Сократить `ChartStyle` с 20 значений до 3: `Bars` (столбики), `Line` (прямые линии),
`Smooth` (изогнутые линии); Default = Smooth (наследует старый дефолт SmoothArea). Рендер
`BalanceTrendChart` сворачивается к трём веткам, переиспользуя существующие draw-хелперы
(bars / прямые сегменты + точки / сглаженный path + точки). Неиспользуемые хелперы 17
удалённых стилей удаляются. Legacy snake_case id в DataStore маппятся на ближайшее семейство
(O1): bars/rounded_bars→bars; smooth_line/smooth_area/stepped_area/neon_area/
vertical_gradient_area/mountain/baseline_fill→smooth; все остальные→line. Режимы цвета и
проекции в этом SPECе НЕ трогаем (SPEC 03/04).
LAYERS: presentation
CHANGED_HINT:
  - core/designsystem/src/main/java/com/kshavrin/mymoney/core/designsystem/chart/ChartStyle.kt:3 — enum сократить до {Bars, Line, Smooth}, Default=Smooth; `toId()` → "bars"/"line"/"smooth" (G4, D2).
  - core/designsystem/src/main/java/com/kshavrin/mymoney/core/designsystem/chart/BalanceTrendChart.kt:443-790 — `when (style)` свернуть к 3 веткам: Bars→drawCachedBars(rounded=false); Line→drawSegmentLine+drawCachedDots+marker; Smooth→drawCachedPath(smooth)+wave-dots+marker; удалить осиротевшие хелперы (G4, D2). Same-file clash с 03.
  - feature/dashboard/src/main/java/com/kshavrin/mymoney/feature/dashboard/ChartConfigMapping.kt — `chartStyleFromId`: legacy 20 id → 3 семейства по таблице O1; неизвестный id → Default (D6). Same-file clash с 04.
  - core/designsystem/src/androidTest/java/com/kshavrin/mymoney/core/designsystem/chart/BalanceTrendChartUiTest.kt — assertAllStylesRender и прочие хардкоды 20 стилей → 3 (F6).
  - feature/dashboard src/main/res/values*/strings.xml — строки имён 3 стилей (EN+RU парой, F9); старые строки стилей удалить в 06.
TEST_TYPES: unit instrumented
CONSTRAINTS:
  - Same-file clash: `BalanceTrendChart.kt` с SPEC 03, `ChartConfigMapping.kt` с SPEC 04 — строгая последовательность 01 → 03/04.
  - Enum-редукция ломает компиляцию во ВСЕХ `when (ChartStyle)` и прямых ссылках на удалённые значения — найти все call-sites (шторка итерирует `ChartStyle.entries` — выживет сама, D10).
  - Раннер компилирует androidTest (F11) — `BalanceTrendChartUiTest` обязателен к обновлению в этом же SPECе.
  - Толщина/плавность 3 стилей = текущий вид Bars / NeonLine-геометрия (прямые сегменты+точки) / SmoothArea-геометрия без заливки (заливка теперь ось проекций, D1) (assumption).
  - Zero comments кроме неочевидного WHY; без хардкод-строк.
=== END SPEC ===

## Acceptance
```gherkin
Scenario: Enum содержит ровно 3 стиля
  Given собранный core:designsystem
  Then ChartStyle.entries = [Bars, Line, Smooth] и Default = Smooth

Scenario: Legacy id маппится на ближайшее семейство
  Given в DataStore сохранён chartStyle="smooth_area"
  When настройки читаются в ChartConfig
  Then стиль графика = Smooth

Scenario: Неизвестный id откатывается на дефолт
  Given в DataStore сохранён chartStyle="no_such_style"
  When настройки читаются в ChartConfig
  Then стиль графика = Smooth

Scenario: Три стиля рендерятся без крэша
  Given чарт с ненулевыми точками
  Then каждый из стилей Bars/Line/Smooth рисуется (instrumented)
```

## Gap / context
Закрывает D2: 20 стилей → 3 семейства. Почва для SPEC 03 (цвет/заливка поверх 3 веток).

## Implementation links
- commit: 0a2c4882, 0d4552b1, 3e47ce22
- files:
  - core/designsystem/src/main/java/com/kshavrin/mymoney/core/designsystem/chart/BalanceTrendChart.kt
  - core/designsystem/src/main/java/com/kshavrin/mymoney/core/designsystem/chart/ChartStyle.kt
  - feature/dashboard/src/main/java/com/kshavrin/mymoney/feature/dashboard/ChartConfigMapping.kt
  - feature/dashboard/src/main/java/com/kshavrin/mymoney/feature/dashboard/components/ChartSettingsSheet.kt
  - feature/dashboard/src/main/res/values/strings.xml
  - feature/dashboard/src/main/res/values-ru/strings.xml
  - app/src/androidTest/java/com/kshavrin/mymoney/feature/dashboard/AuroraBalanceCardUiTest.kt
  - app/src/androidTest/java/com/kshavrin/mymoney/feature/dashboard/ChartSettingsSheetUiTest.kt
  - core/designsystem/src/androidTest/java/com/kshavrin/mymoney/core/designsystem/chart/BalanceTrendChartUiTest.kt
  - feature/dashboard/src/test/java/com/kshavrin/mymoney/feature/dashboard/ChartConfigMappingTest.kt
  - feature/dashboard/src/test/java/com/kshavrin/mymoney/feature/dashboard/DashboardViewModelTest.kt
