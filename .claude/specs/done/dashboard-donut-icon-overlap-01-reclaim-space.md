# Dashboard donut 01 — освободить место у пончика (−10% панель баланса и FAB)
Epic: dashboard-donut-icon-overlap
Order: 01 of 03
Status: done
Depends-on: —
Date: 2026-06-09

## SPEC
=== SPEC ===
TASK: feature
WHAT: Уменьшить токены `dashboardBalancePanelMaxWidth` 272→245dp и `dashboardFabSize` 100→90dp (−10%), чтобы освободить место вокруг пончика по горизонтали и снизу.
LAYERS: presentation
CHANGED_HINT: core/ui/src/main/java/com/kshavrin/mymoney/core/ui/theme/Spacing.kt (L20 `dashboardBalancePanelMaxWidth = 272.dp`, L22 `dashboardFabSize = 100.dp`); app/src/androidTest/java/com/kshavrin/mymoney/feature/dashboard/DashboardContentUiTest.kt (L1050–1056 хардкодят `100.dp` в assertWidthIsAtLeast/assertHeightIsAtLeast для FAB)
TEST_TYPES: compose-ui
CONSTRAINTS: Менять ТОЛЬКО два значения токенов (245.dp и 90.dp); не трогать остальные токены и компоновку. Обновить захардкоженные `100.dp` ассерты в DashboardContentUiTest на `90.dp` (или на `Spacing.dashboardFabSize`) в этом же проходе — иначе FAB-size тест сломается (runner-androidtest-gate). Visual task → device-gate (booted Pixel_5_API_34) перед runner. Conventional commit `feat:`.
=== END SPEC ===

## Gap / context
Радиальные иконки малых секторов жмутся к краям, потому что панель «Баланс» (272dp) и две FAB (100dp)
занимают периметр. −10% по обоим освобождает горизонтальный коридор у пончика и низ кадра под нижние
иконки. Без зависимостей; фундамент для 03 (больше места под вынос на периметр).

## Implementation links
- commit: bb62ea4c (feat: tokens), 8ace7ae8 + 5c937aa8 (test: assertions) — pushed to main
- files:  core/ui/src/main/java/com/kshavrin/mymoney/core/ui/theme/Spacing.kt; app/src/androidTest/java/com/kshavrin/mymoney/feature/dashboard/DashboardContentUiTest.kt
- verified: 37/37 DashboardContentUiTest instrumented tests green on Pixel_5_API_34 (FAB 90dp + balance panel ≤245dp)
