# Кольцо меньше (−20%) и выше
Epic: dashboard-neon-fidelity
Order: 02 of 04
Status: backlog
Depends-on: dashboard-neon-fidelity-01
Date: 2026-06-19

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: Уменьшить неоновое кольцо с диаметра 248dp до ≈200dp (−20%, пропорция мокапа), с пропорциональным уменьшением толщины обводки и сохранением запаса под свечение. Поднять кольцо выше (за счёт тоньшего тулбара из 01 и отсутствия отдельной строки периода + меньший верхний отступ), чтобы освободить больше вертикального места под список плиток категорий.
LAYERS: presentation
CHANGED_HINT:
  - core/ui/.../theme/Spacing.kt (G8, :31-34) — `neonRingDiameter` 248→200dp; `neonRingStrokeWidth` 20→~16dp (пропорц. −20%); `neonRingGlowRadius`/`neonRingGlowSpread` оставить достаточными, чтобы glow не обрезался (G13)
  - core/designsystem/.../donut/NeonRingChart.kt (G7, :62) — убедиться, что `calculateNeonRingChartLayout()` пересчитывает `containerSize`/`innerDiameter` от новых токенов; БЕЗ `clipRect` (G13)
  - feature/dashboard/.../DashboardScreen.kt (G2, :200-246) — уменьшить верхние отступы вокруг кольца, чтобы оно поднялось; больше высоты отдать `CategoryTilesList`
TEST_TYPES: compose-ui instrumented
CONSTRAINTS:
  - Свечение НЕ обрезать (G13): внешний контейнер кольца ≥ `diameter + 2*glowRadius`, без обрезающего `clipRect`.
  - Кольцо остаётся НЕ кликабельным (`assertHasNoClickAction` на DONUT_TAG, G17) — не менять интерактивность.
  - Внутренний диаметр меняется → влияет на 03 (плашка вписывается в НОВЫЙ `innerDiameter`): `03 Depends-on 02`.
  - Same-file clash: `Spacing.kt` + `DashboardScreen.kt` правятся также в 01/03/04 — этот SPEC идёт ПОСЛЕ 01.
  - Точное финальное значение dp (после округления −20%) визуально подтвердить на устройстве через `/mp --fit` (O2, assumption).
  - ktlintFormat перед коммитом; прогон на устройстве `./gradlew :app:connectedDebugAndroidTest` (G18).
=== END SPEC ===

## Acceptance (Gherkin)
```gherkin
Feature: Размер и положение неонового кольца

  Scenario: Кольцо уменьшено
    Given открыт главный экран S01
    Then диаметр неонового кольца ≈200dp (меньше прежних 248dp)
    And толщина обводки уменьшена пропорционально

  Scenario: Свечение не обрезано
    Given кольцо с заполнением
    Then свечение по краям дуги не срезается (контейнер шире диаметра на запас под glow)

  Scenario: Под кольцом больше места для плиток
    Given на экране несколько категорий
    Then кольцо расположено выше, а список плиток занимает больше вертикального места, чем до правки
```

## Gap / context
В сборке кольцо крупное и занимает много места по вертикали; мокап показывает заметно меньшее кольцо,
поднятое выше, чтобы под него поместилось больше плиток категорий.

## Implementation links
- commit: —
- files:  —
