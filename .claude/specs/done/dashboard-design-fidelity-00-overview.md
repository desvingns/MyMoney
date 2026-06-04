# Dashboard — попиксельное выравнивание под Claude Design handoff
Epic: dashboard-design-fidelity
Order: 00 of 02 (overview)
Status: done
Date: 2026-06-04

## Goal
Выровнять экран Dashboard (S01) **попиксельно** под дизайн из Claude Design handoff
(`docs/design/dashboard-redesign/phone.jsx` — авторитетный источник; `render-current.png`,
`render-standalone-final.png`, `monefy-reference-05.jpg` — визуальная сверка). Только визуал;
«Редактор дизайна» (`editor.jsx`) — инструмент времени дизайна, в приложение НЕ переносится.

Контекст и решения зафиксированы в одобренном плане
`~/.claude/plans/sparkling-mixing-bachman.md`. Приложение уже Monefy-fidelity клон; реальная дельта —
3D-«объёмный» донат + уточнения геометрии/цвета/типографики.

## Ordered SPECs
1. **01-donut-3d** — `:core:designsystem` `MonefyDonutChart`/`DonutGeometry`: extrude-3D кольцо,
   толщина ≈0.39, зазор 5°, иконки на прямоугольной рамке (1.7×, диск-маска, выноска+% цветом
   категории), центр income=`secondary` без копеек. (самый объёмный)
2. **02-chrome** — `:feature:dashboard` + `:core:designsystem` balancebar: PeriodLabel (крупный
   зелёный current / бледный prev / скрытый next), MonefyBalanceBar (зелёная плашка + «гамбургеры» +
   белый текст), TwoFabLayout (100dp/6dp), income-акцент `secondary`, проброс параметров доната,
   отступы. Depends-on: 01.

После 01+02 — визуальная сверка на эмуляторе (built screenshot vs `render-*` + `05.jpg`),
итеративный допил геометрии рамки/толщины/отступов; затем verifier-чеклист и гейтед-пуш.

## Cross-cutting notes / flags (дефолты из одобренного плана)
- **AS-14 (порог % меток)**: дизайн показывает % на ВСЕХ секторах; проект залочен на ≥3%
  (`LABEL_THRESHOLD`). Дефолт здесь — **показывать все** (per design), переопределяя AS-14 для S01.
  Обратимо одним числом.
- **income-green акцент** = `colorScheme.secondary` (#50AB6F), НЕ `primary` (#7AC794): центр income,
  +FAB, «плюс»-баланс. Топбар остаётся `primary`.
- **Лого**: оставить `FontFamily.Cursive` (Pacifico — отдельно, по запросу).
- **Суммы**: оставить sans-типографику приложения (в дизайне «Times New Roman» — артефакт).
- **balance-pill**: всегда зелёная (per design); over-budget — отдельная красная плашка (как есть).
- Donut-анимация роста — сохранить; 3D накладывается на анимируемый sweep.
- Visual task → device-gate (booted Pixel_5_API_34) перед developer/tester/runner.

## Implementation links
- commit: 4c00a88 3812a7b 3d4ddcc 4302287 161caa1 21135fb (+ parallel donut d252a0c ee4db96 9fc7c66 2e4ac61)
- files: MonefyDonutChart.kt, DonutGeometry.kt, MonefyBalanceBar.kt, DashboardScreen.kt, PeriodLabel.kt, TwoFabLayout.kt + UI/unit tests

## Round 2 (R1–R5, commit 21135fb)
FAB разнос+краевой паддинг · balance fit-width + краевые гамбургеры · иконки по периметру зоны + full-width canvas · пончик 0.60 · центр авто-fit под внутренний круг. Скриншот сверен с референсом, одобрено пользователем.
