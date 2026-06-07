# Закрепить «Сохранить» снизу на экране CurrencyRate
Epic: form-sticky-save-bar
Order: 03 of 03
Status: done
Depends-on: 01
Date: 2026-06-07

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: На экране ввода курса валюты (CurrencyRate) вынести кнопку «Сохранить» из прокручиваемого тела в слот `Scaffold.bottomBar` через общий `FormBottomBar` (SPEC-01). Тело формы остаётся в `verticalScroll` и получает `innerPadding`.
LAYERS: presentation
CHANGED_HINT:
  - feature/transaction/.../rate/CurrencyRateScreen.kt — в `Scaffold(...)` добавить `bottomBar = { FormBottomBar(text = stringResource(R.string.currency_rate_save), onSave = { onEvent(CurrencyRateEvent.SaveClicked) }) }`; из тела убрать Save `Button` `:135-136` (G5); тело-Column применяет `innerPadding` (scroll на `:106`, G5/G8).
  - feature/transaction/src/androidTest/.../rate/CurrencyRate*UiTest.kt — обновить/добавить: «Сохранить» видна без скролла; клик → CurrencyRateEvent.SaveClicked.
TEST_TYPES: compose-ui
CONSTRAINTS:
  - Тело-Column применяет `innerPadding` от Scaffold (H2).
  - Поведение Save без изменений (`CurrencyRateEvent.SaveClicked`); back-arrow → BackClicked без изменений.
  - Раннер компилирует androidTest и тестит `:feature:transaction` — обновить UI-тест в этом же проходе ([[mymoney-runner-androidtest-gate]]).
  - Строка `currency_rate_save` EN+RU без изменений; токены spacing, не литералы.
  - Маленькая форма — закреплённая панель = просто всегда видимая кнопка, регрессий нет (H3).
=== END SPEC ===

## Acceptance (Gherkin)
```gherkin
Feature: Закреплённая кнопка «Сохранить» на экране курса валюты

  Scenario: Сохранить видна снизу
    Given открыт экран ввода курса валюты
    Then кнопка "Сохранить" закреплена внизу экрана
    When пользователь нажимает "Сохранить"
    Then отправляется событие SaveClicked
```

## Gap / context
G5: на CurrencyRate Save лежит последним элементом скролл-Column'а. Закрепляем снизу через
FormBottomBar (SPEC-01) для консистентности с остальными формами.

## Implementation links
- commit: ba5e7140, 1fedd37b
- files:
  - feature/transaction/src/main/java/com/kshavrin/mymoney/feature/transaction/rate/CurrencyRateScreen.kt
  - app/src/androidTest/java/com/kshavrin/mymoney/feature/transaction/rate/CurrencyRateScreenUiTest.kt
