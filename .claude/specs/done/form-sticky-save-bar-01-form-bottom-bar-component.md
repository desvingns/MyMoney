# Общий FormBottomBar в :core:designsystem (фундамент)
Epic: form-sticky-save-bar
Order: 01 of 03
Status: done
Depends-on: —
Date: 2026-06-07

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: Добавить в :core:designsystem domain-free composable `FormBottomBar`, предназначенный для слота `Scaffold.bottomBar`: закреплённая снизу панель с единственной кнопкой «Сохранить» во всю ширину. Параметры: `text: String`, `enabled: Boolean = true`, `onSave: () -> Unit`, опц. `modifier`. Сам по себе экран не меняет — это переиспользуемый фундамент для SPEC-02 и SPEC-03.
LAYERS: presentation
CHANGED_HINT:
  - core/designsystem/src/main/java/com/kshavrin/mymoney/core/designsystem/form/FormBottomBar.kt — НОВЫЙ файл рядом с TransactionFormContent.kt/CategoryGrid.kt (G9: образец domain-free компонентов в form/). Обернуть кнопку в `Surface`/`BottomAppBar`-подобный контейнер с фоном `MaterialTheme.colorScheme.surface`, тональной/теневой elevation для отделения от прокручиваемого тела, и внутренним padding (spacing-токены из :core:ui/theme/Spacing.kt, не хардкод). Кнопка: `Button(onClick = onSave, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text(text) }`.
  - core/designsystem/src/androidTest/java/com/kshavrin/mymoney/core/designsystem/form/FormBottomBarUiTest.kt — НОВЫЙ: рендер кнопки с текстом; клик → `onSave`; `enabled=false` → кнопка disabled и `onSave` не зовётся.
TEST_TYPES: compose-ui
CONSTRAINTS:
  - :core:designsystem остаётся domain-free — НЕ принимать доменные типы; только String/Boolean/лямбды (G9).
  - Текст кнопки приходит снаружи (`text`) — никаких хардкод-строк внутри компонента; экраны передают свои R.string (G9).
  - Это только компонент — НЕ подключать ни к одному экрану в этом SPEC (foundation-first). Подключение — SPEC-02/03.
  - Высота/padding — разумный минимум; на узком экране кнопка не выходит за границы.
=== END SPEC ===

## Acceptance (Gherkin)
```gherkin
Feature: FormBottomBar — закреплённая кнопка сохранения

  Scenario: Рендер и сохранение
    Given FormBottomBar с текстом "Сохранить" и enabled = true
    When пользователь нажимает кнопку
    Then вызывается onSave ровно один раз

  Scenario: Отключённое состояние
    Given FormBottomBar с enabled = false
    Then кнопка "Сохранить" неактивна
    And тап по ней не вызывает onSave
```

## Gap / context
Сейчас каждая форма верстает свою кнопку Save внутри скролла. Этот SPEC даёт единый закрепляемый
компонент, чтобы 5 форм (SPEC-02/03) переиспользовали его в слоте `Scaffold.bottomBar` (D2).

## Implementation links
- commit: 89d1a8f2, ec2f7ca5
- files:
  - core/designsystem/src/main/java/com/kshavrin/mymoney/core/designsystem/form/FormBottomBar.kt
  - core/designsystem/src/androidTest/java/com/kshavrin/mymoney/core/designsystem/form/FormBottomBarUiTest.kt
