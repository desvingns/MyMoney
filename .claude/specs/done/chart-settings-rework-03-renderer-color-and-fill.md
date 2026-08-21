# Реворк настроек графика — рендер: режимы цвета + заливка-проекция
Epic: chart-settings-rework
Order: 03 of 06
Status: done
Depends-on: 01
Date: 2026-08-20
Acceptance-matrix: color_rule=solid,always_green,always_red,by_direction; projection=off,on; style=bars,line,smooth
Risk-signals: visual/device work, performance, cross-module data flow

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: Заменить `ChartColorRule` на 4 режима: `Solid` (однотонный — акцент темы
dashboardAuroraAccent), `AlwaysGreen` (incomeAccent), `AlwaysRed` (expenseAccent),
`ByDirection` (пер-сегментно относительно горизонтальной линии ПЕРВОЙ точки: выше — зелёный,
ниже — красный, сегмент делится в точке пересечения). Default = ByDirection (O3). Добавить в
`BalanceTrendChart` параметр `showProjection: Boolean = false`: при true под линией рисуется
заливка площади к нулевой оси (ось времени), ВСЕГДА двухцветная — выше оси зелёная, ниже
красная, независимо от colorRule (D1); на стиль Bars тумблер не влияет (D1). TalkBack-контракт
(F6) сохранить; при ByDirection summary дополняется направлением относительно старта.
LAYERS: presentation
CHANGED_HINT:
  - core/designsystem/src/main/java/com/kshavrin/mymoney/core/designsystem/chart/ChartStyle.kt:34 — `ChartColorRule { Solid, AlwaysGreen, AlwaysRed, ByDirection }`, Default=ByDirection; id: "solid"/"always_green"/"always_red"/"by_direction" (D3, D4, O3).
  - core/designsystem/src/main/java/com/kshavrin/mymoney/core/designsystem/chart/BalanceTrendChart.kt:219-256 — сигнатура + `showProjection: Boolean = false`; цвет линии: Solid→auroraAccent, AlwaysGreen→income, AlwaysRed→expense (G5, D8); ByDirection→пер-сегментная раскраска от `points.first()` (D4).
  - core/designsystem/.../chart/BalanceTrendChart.kt — новая чистая функция геометрии (internal, тестируемая unit-тестами): сплит сегментов на пересечениях с заданной горизонталью (используется и для ByDirection от стартового значения, и для заливки от нуля) (D4, D1).
  - core/designsystem/.../chart/BalanceTrendChart.kt:599-790 — заливка-проекция: area-path между линией и нулевой осью, два fill (зелёный выше/красный ниже), сплит на пересечениях с нулём; рисуется ПОД линией; при Bars — игнорируется (D1).
  - core/designsystem/src/androidTest/.../BalanceTrendChartUiTest.kt — контрактные проверки: 4 режима цвета рендерятся, заливка вкл/выкл, semantics summary (F6).
  - core/designsystem/src/test/.../ — unit-тесты функции сплита (пересечение, касание, все-выше/все-ниже, пусто/1 точка) (assumption).
TEST_TYPES: unit instrumented
CONSTRAINTS:
  - Same-file clash с 01 по `BalanceTrendChart.kt` и `ChartStyle.kt` — строго после 01.
  - Calculation: пересечение сегмента (x0,y0)-(x1,y1) с горизонталью y=h: t=(h-y0)/(y1-y0),
    точка (x0+t*(x1-x0), h); делить только при знакоразности (y0-h)*(y1-h)<0; равенство h →
    точка лежит на линии, относить к зоне «выше/равно» (зелёная) (assumption); y0==y1==h →
    сегмент нейтральный, красится зелёным (assumption). Rounding: нет (пиксели Float).
    Фикстуры: (а) [1000→1200→800] h=1000 → пересечение между 1200 и 800; (б) [-5→5] h=0 →
    t=0.5; (в) [3→7] h=0 → без пересечений, вся заливка зелёная; (г) [0→0] h=0 → нейтрально.
  - Заливка двухцветная ВСЕГДА, даже при Solid/AlwaysGreen/AlwaysRed (D1) — линия при этом
    красится своим режимом.
  - Все значения одного знака → заливка одноцветная, нулевая ось за пределами/на краю (O4).
  - ByDirection применим и к столбикам: цвет КАЖДОГО столбика относительно стартового
    значения; тумблер проекций на столбики не влияет (D4, grill D6).
  - Performance: аллокаций в DrawScope не добавлять (урок audit5-donut-perf) — path/brush
    кэшировать в существующий BalanceTrendChartDrawCache.
  - F10: новых интерактивных контролов здесь нет, но semantics summary не деградирует.
=== END SPEC ===

## Acceptance
```gherkin
Scenario: ByDirection красит относительно стартовой точки
  Given график со значениями [1000, 1200, 800] и режимом ByDirection
  Then часть линии выше y=1000 зелёная, ниже — красная, сегмент разделён в точке пересечения

Scenario: Однотонный режим
  Given режим Solid
  Then вся линия рисуется акцентом темы независимо от знака значений

Scenario: Проекция выключена по умолчанию
  Given showProjection=false
  Then заливка под линией отсутствует

Scenario: Проекция двухцветная при любом режиме цвета
  Given showProjection=true и режим AlwaysRed
  And значения переходят через ноль
  Then заливка выше нулевой оси зелёная, ниже — красная, линия — красная

Scenario: Столбики игнорируют проекцию
  Given стиль Bars и showProjection=true
  Then рисуются только столбики, дополнительной заливки нет
```

## Gap / context
Сердце эпика: D3+D4 (4 режима цвета, пер-сегментный by-direction) и D1 (заливка-проекция как
независимая ось, всегда двухцветная от нуля).

## Implementation links
- commit: 8ddacb88e7394d837afc6cf398978f3fbfc0f81a
- files: core/designsystem/src/main/java/com/kshavrin/mymoney/core/designsystem/chart/BalanceTrendChart.kt; core/designsystem/src/androidTest/java/com/kshavrin/mymoney/core/designsystem/chart/BalanceTrendChartUiTest.kt; core/designsystem/src/test/java/com/kshavrin/mymoney/core/designsystem/chart/BalanceTrendChartGeometryTest.kt
