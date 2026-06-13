# Донат: нулевые аллокации в draw-пути и стабильный animationKey
Epic: audit5-donut-perf
Order: 01 of 02
Status: done
Depends-on: —
Date: 2026-06-10

## SPEC
=== SPEC ===
TASK: refactor
PLATFORM: android
WHAT: Кадр анимации доната перестаёт аллоцировать и перерисовывать лишнее при ИДЕНТИЧНОМ визуальном результате: (1) Paint/BlurMaskFilter создаются один раз на composition (remember, ключи — density/толщина) и переиспользуются; (2) экструдированная «стенка» рисуется одним слоем/Path вместо цикла из 7–22 арок на слайс (или кешируется между кадрами, если одно-проходный вариант визуально отличим); (3) animationKey стабилен: пересчёт баланса с тем же набором слайсов (id+доли) НЕ рестартует анимацию.
LAYERS: presentation
CHANGED_HINT:
  - core/designsystem/.../donut/MonefyDonutChart.kt:791-800 — Paint+BlurMaskFilter → remember с ключами (G1)
  - core/designsystem/.../donut/MonefyDonutChart.kt:802-808 — depth-цикл → один Path/слой на слайс или кеш промежуточного результата (G2)
  - core/designsystem/.../donut/MonefyDonutChart.kt:107,114-116 — animationKey = производная от списка (categoryId, fraction) — неизменный набор не перезапускает LaunchedEffect (G3)
  - JVM-тест на animationKey-производную; существующие гео-тесты не трогать
TEST_TYPES: unit, instrumented
CONSTRAINTS:
  - Визуальный паритет обязателен: MonefyDonutChartUiTest 29/29 зелёные (G6) + ручное сравнение скриншота до/после на Pixel_5_API_34 (device-гейт, preflight health-check перед прогоном).
  - Публичный API композабла не меняется (вызовы из :feature:dashboard нетронуты).
  - `MonefyDonutChart.kt` затем правится в audit8-hygiene-04 (a11y) — этот SPEC первый.
  - (assumption) если одно-проходная стенка визуально отличима — оставить цикл, но кешировать в слой между кадрами (graphicsLayer/Bitmap), цель — нулевые аллокации в onDraw.
=== END SPEC ===

## Acceptance (Gherkin)
```gherkin
Feature: Донат не аллоцирует на кадр

  Scenario: Кадровый путь чист
    Given дашборд с 8 категориями расходов
    When анимация доната проигрывается
    Then в draw-пути не создаются новые Paint/MaskFilter/списки на кадр

  Scenario: Пересчёт без изменения данных не дёргает анимацию
    Given донат показывает категории A/B/C
    When баланс пересчитывается с теми же долями
    Then анимация не перезапускается с нуля

  Scenario: Изменение данных анимируется как раньше
    When появляется новая категория в расходах периода
    Then донат анимирует переход (текущее поведение)
```

## Gap / context
Баг H6 аудита: главный подозреваемый в frame CPU p50 ~399 ms (G4) — аллокации на кадр (G1),
~200 drawArc/кадр (G2) и рестарт анимации на каждую запись в БД (G3).

## Implementation links
- commit: b6bb1c4d (perf: hoist donut shadow Paint and stabilize animation key)
- files: core/designsystem/.../donut/MonefyDonutChart.kt, core/designsystem/src/test/.../donut/DonutAnimationKeyTest.kt
- verify: DonutAnimationKeyTest JVM green; MonefyDonutChartUiTest 36/36 on Pixel_5_API_34 (visual parity); reviewer pass; pushed to main
