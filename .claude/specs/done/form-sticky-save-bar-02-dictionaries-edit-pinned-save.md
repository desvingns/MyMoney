# Закрепить «Сохранить» снизу на 4 экранах dictionaries-edit
Epic: form-sticky-save-bar
Order: 02 of 03
Status: done
Depends-on: 01
Date: 2026-06-07

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: На экранах Category / Account / Currency / Goal edit вынести кнопку «Сохранить» из прокручиваемого тела в слот `Scaffold.bottomBar` через общий `FormBottomBar` (SPEC-01). Тело формы остаётся в `verticalScroll` над панелью и получает `innerPadding`. На Category/Account/Currency кнопка «Удалить» ОСТАЁТСЯ последним элементом тела формы (в скролле) — закрепляем только Save (D3).
LAYERS: presentation
CHANGED_HINT:
  - feature/dictionaries/.../categories/CategoryEditScreen.kt — в `Scaffold(...)` добавить `bottomBar = { FormBottomBar(text = stringResource(R.string.dictionaries_save), onSave = { onEvent(CategoryEditEvent.SaveClicked) }) }`; из Row на `:175-195` (G1) убрать кнопку Save, Delete оставить (для !isCreateMode) последним элементом скролл-Column'а; тело-Column применяет `innerPadding`.
  - feature/dictionaries/.../accounts/AccountEditScreen.kt — то же: bottomBar c `AccountEditEvent.SaveClicked`; из Row `:220-236` (G2) убрать Save, Delete оставить в скролле; `innerPadding` на тело.
  - feature/dictionaries/.../currencies/CurrencyEditScreen.kt — то же: bottomBar c `CurrencyEditEvent.SaveClicked`; из Row `:169-178` (G3) убрать Save, Delete оставить; `innerPadding`.
  - feature/dictionaries/.../goals/GoalEditScreen.kt — bottomBar c `GoalEditEvent.SaveClicked` и `enabled = state.canSave` (G4, H1); из тела убрать одиночную Save `:255-256`; `innerPadding`. (Delete на Goal нет.)
  - feature/dictionaries/src/androidTest/.../{Category,Account,Currency,Goal}Edit*UiTest.kt — обновить/добавить: «Сохранить» видна без скролла; клик → SaveClicked; на Category/Account/Currency «Удалить» по-прежнему в теле (для edit-режима); Goal: при `canSave=false` Save disabled (G10).
TEST_TYPES: compose-ui
CONSTRAINTS:
  - Тело-Column ДОЛЖНО применять `innerPadding` от Scaffold, иначе контент уедет под закреплённую панель (H2).
  - Поведение Save без изменений: те же события `*Event.SaveClicked`; Goal сохраняет `enabled = state.canSave`.
  - НЕ ломать back-arrow → `BackClicked` (NavigateBack без сохранения) — поведение зафиксировано ([[mymoney-edit-screen-backarrow-quirk]]).
  - Раннер компилирует androidTest и тестит `:feature:dictionaries` — обновить UI-тесты в этом же проходе ([[mymoney-runner-androidtest-gate]]).
  - Строки EN+RU без хардкода; токены spacing, не литералы; идентификаторы английские.
  - НЕ путать с `monefy-ux-fixes-07 categories-scroll` — тот правит CategoriesListContent (список), здесь — *Edit-формы (H4).
=== END SPEC ===

## Acceptance (Gherkin)
```gherkin
Feature: Закреплённая кнопка «Сохранить» на формах справочников

  Scenario: Сохранить видна без скролла на длинной форме
    Given открыт экран «Изменить категорию» с длинным телом формы
    Then кнопка "Сохранить" видна внизу экрана без прокрутки
    When пользователь нажимает "Сохранить"
    Then отправляется событие SaveClicked

  Scenario: Удалить остаётся в теле формы (edit-режим)
    Given открыт экран «Изменить счёт» (не создание)
    Then кнопка "Сохранить" закреплена снизу
    And кнопка "Удалить" находится в прокручиваемом теле формы

  Scenario: Goal — Сохранить отключена при невалидной форме
    Given открыт экран цели с canSave = false
    Then закреплённая кнопка "Сохранить" неактивна
```

## Gap / context
G1–G4: на этих 4 формах Save лежит последним элементом скролл-Column'а ⇒ до неё надо доскроллить.
Этот SPEC закрепляет Save снизу через FormBottomBar (SPEC-01), оставляя Delete в скролле (D3).

## Implementation links
- commit: b360dc29 (feat: pin save button to bottom bar on dictionary edit forms) + 5f59ad15 (test: cover pinned save bar on dictionary edit forms)
- files:
  - feature/dictionaries/.../categories/CategoryEditScreen.kt
  - feature/dictionaries/.../accounts/AccountEditScreen.kt
  - feature/dictionaries/.../currencies/CurrencyEditScreen.kt
  - feature/dictionaries/.../goals/GoalEditScreen.kt
  - app/src/androidTest/.../{categories/CategoryEditContentUiTest, accounts/AccountEditContentUiTest, currencies/CurrencyEditContentUiTest, goals/GoalEditContentUiTest}.kt
- device: 18 instrumented Compose-UI tests green on Pixel_5_API_34 (API 34). Pushed to main.
- note: AccountEditContentUiTest inline-error assert needed performScrollTo() — the pinned FormBottomBar shrinks the scroll viewport, pushing the bottom-of-body error below the fold (UX follow-up flagged).
