# Dashboard donut 03 — вынос иконок на периметр квадрата + линии-выноски
Epic: dashboard-donut-icon-overlap
Order: 03 of 03
Status: draft
Depends-on: dashboard-donut-icon-overlap-02
Date: 2026-06-09

## SPEC
=== SPEC ===
TASK: feature
WHAT: Заменить радиальную позицию иконки (iconCenter на окружности) проекцией mid-луча сектора на инсет-прямоугольник canvas; подключить линии-выноски сектор→иконка цветом сектора; клемпить блок подписи в пределах canvas.
LAYERS: presentation
CHANGED_HINT: core/designsystem/src/main/java/com/kshavrin/mymoney/core/designsystem/donut/MonefyDonutChart.kt (iconCenter/radialPoint L727–762 → проекция на прямоугольник; подключить dormant leaderLineColor/leaderLineThickness в drawScope для populated-секторов); core/designsystem/src/main/java/com/kshavrin/mymoney/core/designsystem/donut/DonutGeometry.kt (уже есть framePoint(t, hw, hhTop, hhBot) + midAngleRadians — использовать/доработать как чистую функцию проекции mid-угла на инсет-прямоугольник); core/designsystem/src/test/.../donut/DonutGeometryTest.kt (unit на проекцию); core/designsystem/src/androidTest/.../donut/MonefyDonutChartUiTest.kt (снять «leader line dormant», добавить активную проверку)
TEST_TYPES: unit, compose-ui
CONSTRAINTS: ЗАВИСИТ ОТ 02 — клемпинг использует bounding-box компактного блока из 02. Проекцию вынести/реализовать чистой функцией в DonutGeometry.kt и покрыть unit-тестом на кардинальные (0/90/180/270°) И диагональные углы. Линии-выноски: толщина из токена `dashboardDonutLeaderLineThickness`, цвет = цвет сектора (НЕ статичный `dashboardDonutLeaderLine` color token для populated — он для dormant/empty). Клемпить блок так, чтобы не выходил за границы canvas. НЕ трогать empty-state (emptyIconSlot — круговая раскладка остаётся). Полное collision-spreading по грани — вне scope (клемпинг держит на экране, не раздвигает соседей). Обновить donut unit+UI тесты в этом же проходе. Visual task → device-gate (booted Pixel_5_API_34) перед runner; сверять после 01 (больше места). Conventional commit `feat:`.
=== END SPEC ===

## Gap / context
Радиальная раскладка (`outerRadius + iconMargin`) ставит иконки на окружность вплотную к пончику —
соседние малые секторы на одной грани наезжают. Проекция mid-луча на инсет-прямоугольник canvas
разносит иконки к краям кадра (ширина — края экрана, высота — от «Баланс» до FAB), линии-выноски
сохраняют связь иконка↔сектор. Финальная мера эпика; даёт unit-покрытие геометрии проекции.

## Implementation links
- commit: <hash>
- files:  <changed files>
