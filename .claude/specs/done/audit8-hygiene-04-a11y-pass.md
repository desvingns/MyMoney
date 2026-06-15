# A11y-проход: balance bar, клавиатура, секторы доната
Epic: audit8-hygiene
Order: 04 of 04
Status: done
Depends-on: audit5-donut-perf-01
Date: 2026-06-10

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: Закрыть три худших a11y-пробела из аудита: (1) кликабельный Row нижней балансовой панели получает осмысленное действие для TalkBack (onClickLabel/contentDescription «Открыть записи», иконки помечены декоративными); (2) операторные клавиши калькулятора (− × ÷ =) получают явные semantics-метки из строковых ресурсов вместо надежды на чтение глифов; (3) секторы доната становятся доступными фокусу по отдельности (semantics-узлы на слайс: «Еда, 45 процентов», действие — открыть категорию).
LAYERS: presentation
CHANGED_HINT:
  - core/designsystem/.../balancebar/MonefyBalanceBar.kt:36-42 — clickable(onClickLabel=...), у Menu-иконок contentDescription=null остаётся (декор), Row получает contentDescription/stateDescription (G7)
  - core/designsystem/.../keypad/MonefyKeypad.kt — semantics { contentDescription } для операторных клавиш из ресурсов designsystem (EN+RU) (G7)
  - core/designsystem/.../donut/MonefyDonutChart.kt — per-slice semantics-узлы (Modifier.semantics в overlay-слое поверх Canvas), действие = существующий onSliceClick (G7)
  - androidTest: semantics-проверки (onNodeWithContentDescription) для всех трёх компонентов
TEST_TYPES: compose-ui, instrumented
CONSTRAINTS:
  - `MonefyDonutChart.kt` — после audit5-donut-perf-01 (общий файл); merged-описание доната (уже есть) сохранить, добавить пер-слайсовые узлы.
  - Видимый рендер не меняется вообще — только семантическое дерево.
  - Новые строки EN+RU, без хардкода (designsystem-owned ресурсы — паттерн AmountFieldSection).
  - TalkBack-проход по чеклисту Verifier на устройстве (manual-гейт).
=== END SPEC ===

## Acceptance (Gherkin)
```gherkin
Feature: Ключевые элементы доступны TalkBack

  Scenario: Балансовая панель озвучивается
    When фокус доступности попадает на нижнюю балансовую панель
    Then озвучивается её назначение и действие открытия записей

  Scenario: Операторы калькулятора читаемы
    When фокус попадает на клавишу «÷»
    Then озвучивается «разделить» (локализованно)

  Scenario: Секторы доната перечислимы
    When пользователь свайпает фокус по донату
    Then каждый сектор озвучивается именем категории и долей
    And активация сектора открывает его категорию
```

## Gap / context
Аудит P3.15 (G7): кликабельные элементы без описаний и Canvas-чарт без пер-элементной семантики —
основные блокеры TalkBack-навигации по главному экрану.

## Implementation links
- commit: 7ea0c003 (prod: balance bar / keypad operators / donut slices semantics + EN/RU strings), 89a43c46 (tests: 61 instrumented a11y tests, green on Pixel_5_API_34)
- files:
  - core/designsystem/src/main/java/com/kshavrin/mymoney/core/designsystem/balancebar/MonefyBalanceBar.kt
  - core/designsystem/src/main/java/com/kshavrin/mymoney/core/designsystem/donut/MonefyDonutChart.kt
  - core/designsystem/src/main/java/com/kshavrin/mymoney/core/designsystem/keypad/MonefyKeypad.kt
  - core/designsystem/src/main/res/values/strings.xml
  - core/designsystem/src/main/res/values-ru/strings.xml
  - core/designsystem/src/androidTest/.../balancebar/MonefyBalanceBarUiTest.kt
  - core/designsystem/src/androidTest/.../donut/MonefyDonutChartUiTest.kt
  - core/designsystem/src/androidTest/.../keypad/MonefyKeypadA11yUiTest.kt (new)
