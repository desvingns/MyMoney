# Dashboard donut 02 — компактный блок подписи (иконка+% в строку, имя под блоком)
Epic: dashboard-donut-icon-overlap
Order: 02 of 03
Status: done
Depends-on: —
Date: 2026-06-09

## SPEC
=== SPEC ===
TASK: feature
WHAT: Сделать блок подписи сектора компактным: [иконка][%] в одну строку (% уменьшить ~24sp→~16sp), название категории под блоком «иконка+%», центрировано по блоку, ширина названия = ширине блока; длинное имя плавно уменьшает шрифт до пола (~10sp), затем TextOverflow.Ellipsis — всегда одна строка.
LAYERS: presentation
CHANGED_HINT: core/designsystem/src/main/java/com/kshavrin/mymoney/core/designsystem/donut/MonefyDonutChart.kt (drawCalloutText L528–561: сейчас % сверху отдельной строкой ~24sp, имя ниже maxLines=2, ширина = iconSize*2.6f); calloutPercentageStyle/dashboardCalloutPercentage typography token (~24sp → ~16sp); core/designsystem/src/androidTest/.../donut/MonefyDonutChartUiTest.kt (callout-label тесты)
TEST_TYPES: compose-ui
CONSTRAINTS: Работает на ТЕКУЩЕЙ радиальной раскладке (проверяется независимо от 03). Блок «иконка+%» — одна строка (иконка слева, % справа от неё). Название — ровно одна строка под блоком, центр по блоку, ширина названия = ширине блока «иконка+%»; реализовать понижение шрифта до пола ~10sp перед эллипсисом (maxLines=1, TextOverflow.Ellipsis). НЕ трогать empty-state раскладку (emptyIconSlot). Обновить donut UI-тесты в этом же проходе. Visual task → device-gate (booted Pixel_5_API_34) перед runner. Conventional commit `feat:`.
=== END SPEC ===

## Gap / context
Высокий вертикальный блок «иконка → % (24sp) → название (до 2 строк)» у соседних малых секторов
(3–8%) перекрывается по вертикали. Ужатие в [иконка+%]-строку + одно-строчное имя резко снижает
высоту блока, давая 03 компактный bounding-box для клемпинга/выноса на периметр.

## Implementation links
- commit: 3e808692 (token 24→16sp), 7b9928b2 (compact callout layout), db6c4bbd (tests) — pushed to main
- files:  core/ui/.../theme/Typography.kt; core/designsystem/.../donut/MonefyDonutChart.kt; core/designsystem/src/androidTest/.../donut/MonefyDonutChartUiTest.kt
- verified: 26/26 MonefyDonutChartUiTest instrumented tests green on Pixel_5_API_34; CALLOUT_LABEL_MIN_SP=10f floor → ellipsis; icon+% inline row, name 1 line below
