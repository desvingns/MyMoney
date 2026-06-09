# Dashboard donut — устранение наезжания иконок категорий
Epic: dashboard-donut-icon-overlap
Order: 00 of 03 (overview)
Status: done
Date: 2026-06-09

## Goal
На дашборде (S01) иконки категорий вокруг пончика наезжают друг на друга, когда несколько
секторов с долей 3–8% идут подряд на одной стороне. Причина: радиальная раскладка на окружности
(`MonefyDonutChart.iconCenter`) ставит иконки близко к пончику, а высокий вертикальный блок «иконка
→ % (24sp) → название» у соседних мелких секторов перекрывается. Эпик чинит это тремя мерами:
освободить пространство вокруг пончика, ужать блок подписи и разнести иконки на периметр
прямоугольной области пончика (края экрана по ширине, от «Баланс» до FAB по высоте) с тонкими
линиями-выносками к секторам.

## Ordered SPECs
1. **01-reclaim-space** — `:core:ui` токены: `dashboardBalancePanelMaxWidth` 272→245dp (−10%),
   `dashboardFabSize` 100→90dp (−10%). Освобождает место у пончика по горизонтали и снизу.
   Тривиально, без зависимостей. Обновить `DashboardContentUiTest`, где захардкожен `100.dp`.
2. **02-callout-block-inline** — `:core:designsystem` `MonefyDonutChart`: блок подписи [иконка][%]
   в одну строку (% уменьшить ~24sp→~16sp), название под блоком, ширина названия = ширине блока,
   центр по блоку; длинное имя — шрифт↓ до пола (~10sp) → многоточие, всегда 1 строка. Работает на
   текущей радиальной раскладке (проверяется независимо).
3. **03-square-perimeter-placement** — `:core:designsystem` `MonefyDonutChart`/`DonutGeometry`:
   заменить радиальную позицию иконки проекцией mid-луча сектора на инсет-прямоугольник canvas
   (использовать существующий `framePoint`/`midAngleRadians`); подключить линии-выноски сектор→иконка
   (цвет сектора); клемпинг блока в пределах canvas. **Зависит от 02.**

## Locked decisions
- Обрезка длинного названия: плавно уменьшать шрифт до пола (~10sp) → затем `TextOverflow.Ellipsis`,
  всегда одна строка (выбор пользователя).
- Рисовать линии-выноски от каждой вынесенной иконки к её сектору, цветом сектора (выбор пользователя).
- «Квадрат» = bounds canvas внутри `Box(weight(1f))` — отдельной перестройки layout НЕ делаем,
  проецируем на этот прямоугольник с небольшим инсетом.
- % инлайн с иконкой; название под блоком «иконка+%», центрировано по блоку, ширина = ширине блока.

## Cross-cutting notes / flags
- **Visual task → device-gate** (booted Pixel_5_API_34) перед runner для всех трёх ТЗ.
- **API-реворк → androidTest в том же проходе** (runner-androidtest-gate): 01 трогает токены,
  на которые ссылается `DashboardContentUiTest` (захардкожен `100.dp`, L1050–1056); 02/03 трогают
  `MonefyDonutChart` рендер — проверить `MonefyDonutChartUiTest` / `DonutGeometryTest`, обновить
  в том же ТЗ.
- **03 depends-on 02 (жёстко):** клемпинг использует bounding-box блока из 02.
- 03 soft-benefits от 01 (больше места) — сверять 03 на устройстве после 01.
- Геометрию проекции вынести/использовать чистой функцией в `DonutGeometry.kt`
  (`framePoint` + `midAngleRadians` уже есть) → покрыть unit-тестом (кардинальные + диагональные
  углы) — это даёт `TEST_TYPES: unit` для 03.
- Граница: меры снижают, но не гарантируют полное отсутствие наездов при экстремальной кучности на
  одной грани (клемпинг держит на экране, не раздвигает соседей). Полное collision-spreading по
  грани — вне scope; подстраховка — существующее сворачивание <2% в «Other».
- Empty-state иконки (круговая раскладка `emptyIconSlot`, MonefyDonutChart L576–587) НЕ трогаем —
  только populated state.
- Параметры `leaderLineColor`/`leaderLineThickness` уже есть в сигнатуре `MonefyDonutChart` (dormant)
  и токены `dashboardDonutLeaderLine`(color)/`dashboardDonutLeaderLineThickness` уже определены —
  03 их подключает (толщина — из токена, цвет — сектора).

## Implementation links
**EPIC COMPLETE 2026-06-09 — all 3 SPECs in done/, pushed to main.**
- 01-reclaim-space: bb62ea4c + 8ace7ae8 + 5c937aa8 (37/37 device-green)
- 02-callout-block-inline: 3e808692 + 7b9928b2 + db6c4bbd (26/26 device-green)
- 03-square-perimeter-placement: 6727b1e3 + 81d5697b + c6f34b50 (29/29 device-green)
