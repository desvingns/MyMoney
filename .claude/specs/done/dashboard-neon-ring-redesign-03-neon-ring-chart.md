# Компонент NeonRingChart: градиентная дуга + неклиппируемое свечение
Epic: dashboard-neon-ring-redesign
Order: 03 of 06
Status: done
Depends-on: dashboard-neon-ring-redesign-01
Date: 2026-06-15

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: Новый компонент `NeonRingChart` — плоская градиентная дуга (мятный `#5BE3B0` → циан `#46B6E6`) с неоновым свечением на тёмном треке `#1A2236`; длина заполненной дуги пропорц. `fraction` (= расход÷доход из 02), `StrokeCap.Round`. Свечение НЕ обрезается по краям (D1). Компонент предоставляет слот центра `centerContent` (его заполняет 04).
LAYERS: presentation
CHANGED_HINT:
  - core/designsystem/.../donut/NeonRingChart.kt (new) — Canvas: `drawArc` трека (neonRingTrack, полный круг) + градиентная дуга (`Brush.sweepGradient`/`linearGradient` start→end) толщиной `strokeWidth`, sweep = `fraction * maxSweepDeg`, cap Round; слой свечения через BlurMaskFilter/`drawArc` с увеличенным blur БЕЗ clipRect (G6, D1)
  - core/designsystem/.../donut/NeonRingChart.kt — сигнатура `NeonRingChart(fraction: Float, modifier, centerContent: @Composable BoxScope.() -> Unit)`; внутренний бокс центра = диаметр × innerFactor (передать в слот для авто-ужатия в 04)
  - core/ui/theme tokens (из 01) — diameter/strokeWidth/glowRadius/glowSpread + градиентные стопы (G9, G10)
TEST_TYPES: unit compose-ui
CONSTRAINTS:
  - Свечение требует запаса: внешний Box/Canvas ≥ `diameter + 2*glowRadius`, без `clipRect`, срезающего верх/низ (D1). Это ПРЯМОЙ контраст с `MonefyDonutChart.drawExtrudedRing`, где clipRect намеренно срезает верх свечения (G6) — НЕ копировать ту логику.
  - Дуга/свечение рисуются Canvas → не семантизируются; compose-ui тест проверяет контейнер по testTag + что размер с запасом под glow; точный пиксель — captureToImage / ручной `--fit` (G15).
  - НЕ удалять и не менять `MonefyDonutChart.kt` (его юнит-тесты DonutGeometryTest/DonutAnimationKeyTest и возможные иные потребители) — это НОВЫЙ компонент (G15, G16).
  - `fraction` приходит готовым (clamp сделан в 02); компонент чистый, без обращения к VM/состоянию.
  - ktlintFormat перед коммитом; `:core:designsystem` тесты прогнать вручную/на устройстве (G16).
=== END SPEC ===

## Acceptance (Gherkin)
```gherkin
Feature: Неоновое кольцо

  Scenario: Заполнение пропорционально доле
    Given fraction = 0.5
    When отрисовано NeonRingChart
    Then градиентная дуга занимает половину максимального угла, остальное — трек

  Scenario: Пустое кольцо при доле 0
    Given fraction = 0.0
    Then видим только тёмный трек, градиентной дуги нет

  Scenario: Свечение не обрезается
    Given кольцо с заполнением
    Then контейнер компонента шире/выше диаметра на запас под glow (нет обрезающего clipRect)

  Scenario: Слот центра доступен
    Then centerContent рендерится внутри кольца и получает внутренний бокс
```

## Gap / context
Текущий S01 использует `MonefyDonutChart` (3D-«extrude» донат с иконками категорий и clipRect, срезающим верх
свечения). Новый макет — плоское неоновое кольцо без сегментов; нужен отдельный компонент с неклиппируемым glow
и слотом под центр.

## Implementation links
- commit: ffe9b6c3, dc2cd5d7, 877e5666
- files: core/designsystem/src/main/java/com/kshavrin/mymoney/core/designsystem/donut/NeonRingChart.kt, core/designsystem/src/test/java/com/kshavrin/mymoney/core/designsystem/donut/NeonRingChartTest.kt, core/designsystem/src/androidTest/java/com/kshavrin/mymoney/core/designsystem/donut/NeonRingChartUiTest.kt
