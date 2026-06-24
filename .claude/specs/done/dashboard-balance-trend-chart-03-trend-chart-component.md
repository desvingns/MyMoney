# График тренда — базовый компонент в :core:designsystem
Epic: dashboard-balance-trend-chart
Order: 03 of 07
Status: done
Depends-on: —
Date: 2026-06-21

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: Базовый composable `BalanceTrendChart` в `:core:designsystem`, рисующий ряд точек: ломаную линию, 3 декоративные равномерные вертикальные линии, горизонтальную линию нуля (только когда ряд пересекает 0), светящуюся точку на последней точке, цвет линии по правилу (по умолчанию зелёный/красный по знаку последней точки), опциональные подписи периодов под точками. Вертикальный масштаб — авто min–max по точкам. Параметризуется списком значений, подписями и флагами (showGridlines, showLabels, colorRule, style — со стилем по умолчанию).
LAYERS: presentation
CHANGED_HINT:
  - core/designsystem/src/main/java/com/kshavrin/mymoney/core/designsystem/chart/BalanceTrendChart.kt (new) — Canvas-рендер; зеркалит подход `NeonRingChart.kt` (G12). Вход: `points: List<Float>` (или нейтральные value+label), `labels: List<String>`, `showGridlines`/`showLabels`/`colorRule`/`style`.
  - core/designsystem/src/main/java/com/kshavrin/mymoney/core/designsystem/chart/ChartStyle.kt (new) — enum со ЗНАЧЕНИЕМ по умолчанию (расширяется в 04) + enum `ChartColorRule`.
  - core/ui/theme — переиспользовать неон-токены (`neonRingGradientStart/End*`, G10) для линии/свечения; при нехватке добавить токен линии нуля. (assumption)
TEST_TYPES: unit compose-ui
CONSTRAINTS:
  - Компонент в `:core:designsystem` — `:feature:*` не может зависеть от `:feature:*` (G12).
  - Текст, нарисованный на Canvas (drawText), НЕ проверяется через semantics (G14, memory donut) — числовые значения проверять в доменном тесте (01); UI-тест проверяет факт рендера по testTag и геометрию (кол-во точек/линий).
  - Авто min–max; линия нуля рисуется ТОЛЬКО когда `min < 0 < max` (D8). 3 декоративные линии — равномерно по ширине, не привязаны к точкам (D6). Точка-маркер на последней точке (D9).
  - Цвет по `ChartColorRule` (default by_sign — по знаку последней точки) (D9); правило приходит параметром.
  - Без хардкод-строк: подписи приходят готовыми (локализация на стороне фичи, G18). Геометрию выносить в чистую функцию для юнит-теста.
  - Перф: `Paint`/`Path` хойстить в `remember` (memory: donut draw-allocations) — без аллокаций в onDraw.
  - English-идентификаторы; zero comments кроме неочевидного WHY.
=== END SPEC ===

## Acceptance
```gherkin
Scenario: Рендер ломаной по 5 точкам
  Given 5 значений [10, 6, 12, 12, 15] и showGridlines=true
  When отрисован BalanceTrendChart
  Then видна линия, 3 вертикальные линии и маркер на последней точке (по testTag)

Scenario: Линия нуля только при пересечении
  Given значения [4, 3, 1, -2, -3]
  When отрисован график
  Then рисуется горизонтальная линия нуля

Scenario: Нет линии нуля для одного знака
  Given значения [10, 6, 12, 12, 15] (все > 0)
  When отрисован график
  Then линия нуля не рисуется

Scenario: Цвет по знаку последней точки
  Given значения с последней точкой < 0 и colorRule=by_sign
  When отрисован график
  Then линия красная (иначе зелёная)
```

## Gap / context
Кольцо (`NeonRingChart`) рисует дугу-индикатор; для тренда нужен принципиально другой примитив — ломаная по N точкам.
Делаем переиспользуемый компонент в дизайн-системе (его же используют дашборд и валютные карточки).

## Implementation links
- commit: a6269891 (tokens), df29e705 (component), 07fc8369 (tests)
- files: core/designsystem/.../chart/BalanceTrendChart.kt, ChartStyle.kt; core/ui/.../theme/Color.kt, Spacing.kt; +Geometry/UI tests
