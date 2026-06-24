# Убрать пикер цвета из настроек категории + превью имени в textColor
Epic: category-icon-text-color
Order: 04 of 04
Status: done
Depends-on: 03
Date: 2026-06-22

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: Удалить из экрана редактирования категории ручной выбор цвета (запрос фичи «убрать из настроек
категории менять цвет текста»). Цвет больше не задаётся пользователем — он производный от иконки
(SPEC 01/02).
  1) Убрать composable `ColorPicker` из `CategoryEditContent` (CategoryEditScreen.kt:186-193) и любой
     связанный заголовок/секцию «цвет».
  2) Убрать событие `CategoryEditEvent.ColorChanged` (CategoryEditViewModel.kt:168-170) и обработку,
     мутирующую `state.colorHex` (:75-76). `state.colorHex` больше не редактируется пользователем.
  3) Превью имени/иконки на экране: цвет ИМЕНИ — из производного `textColor` (categoryTextColorHex(
     state.iconKey) через :core:common), цвет иконки в превью — из categoryIconDominantHex(state.iconKey)
     (вместо parseHexColor(state.colorHex), CategoryEditScreen.kt:176-180). Превью обновляется при смене
     иконки.
  4) Сохранение остаётся через путь SPEC 02 (репозиторий/use-case сам выставит colorHex+textColor из
     iconKey) — VM не должна слать пользовательский цвет.
  5) Обновить UI-тесты редактирования, которые кликают по образцу цвета
     (CategoryEditContentUiTest.kt — клик свотча, ~:62): убрать этот сценарий, добавить проверку, что
     пикера цвета на экране НЕТ и что смена иконки меняет цвет превью имени.
LAYERS: presentation
CHANGED_HINT:
  - feature/dictionaries/src/main/.../categories/CategoryEditScreen.kt:186-193 — удалить ColorPicker;
    :176-180 — превью имени/иконки из textColor/dominantHex по iconKey (G6, G8)
  - feature/dictionaries/src/main/.../categories/CategoryEditViewModel.kt:75-76,168-170 — удалить
    ColorChanged + мутацию colorHex (G7, G8)
  - app/src/androidTest/.../feature/dictionaries/categories/CategoryEditContentUiTest.kt — убрать клик
    по свотчу цвета (~:62), добавить «пикера цвета нет» + «смена иконки → меняется цвет имени»
  - возможно feature/dictionaries .../common/ColorPicker.kt — если больше нигде не используется,
    отметить как мёртвый (НЕ удалять файл — архивировать по правилу проекта, либо оставить)
TEST_TYPES: instrumented, unit
CONSTRAINTS:
  - НЕ удалять файлы (правило проекта «archive, never delete»): если `ColorPicker`/палитра становятся
    мёртвыми — сообщить пользователю пути для ручного переноса в `archive/`, файл не трогать.
  - `CategoryEditContentUiTest.kt` — в `:app` androidTest; ktlintFormat перед коммитом (G22); имена
    тест-функций в backtick без `[ ]`; импорты Compose-тестов без квалификации (память tester gotchas).
  - Сохранение НЕ должно ломаться: VM перестаёт управлять цветом, значение проставляется на слое
    данных (SPEC 02) — проверить, что после удаления пикера сохранение по-прежнему даёт корректные
    colorHex/textColor.
  - Файлы НЕ пересекаются со SPEC 03.
=== END SPEC ===

## Acceptance (Gherkin)
```gherkin
Feature: Настройки категории без пикера цвета

  Scenario: Пикера цвета больше нет
    Given открыт экран создания/редактирования категории
    When экран отрисован
    Then на нём нет выбора цвета (пикера/палитры)

  Scenario: Смена иконки меняет цвет имени в превью
    Given открыт экран категории
    When пользователь выбирает иконку такси (жёлтую)
    Then цвет имени в превью становится жёлтым (производным от иконки)

  Scenario: Сохранение проставляет цвета из иконки
    Given выбрана иконка "car", имя введено
    When категория сохраняется
    Then сохранённые colorHex и textColor вычислены из "car", без участия пользователя
```

## Gap / context
Экран редактирования категории даёт пользователю выбирать цвет (пикер на :186-193), который пишется
в `colorHex`. По запросу фичи этот контрол убирается; цвет полностью производный от иконки (SPEC 01/02).
Этот SPEC — последний, закрывает эпик.

## Implementation links
- commit: e827f8ec (prod), b2028049 (tests)
- files:
  - feature/dictionaries/src/main/java/com/kshavrin/mymoney/feature/dictionaries/categories/CategoryEditScreen.kt (ColorPicker removed; preview name/icon colored via categoryTextColorHex / categoryIconDominantHex by iconKey)
  - feature/dictionaries/src/main/java/com/kshavrin/mymoney/feature/dictionaries/categories/CategoryEditViewModel.kt (ColorChanged event + user colorHex mutation removed)
  - feature/dictionaries/src/test/kotlin/.../categories/CategoryEditViewModelTest.kt (stale ColorChanged test → derived-colorHex-on-save)
  - app/src/androidTest/java/.../categories/CategoryEditContentUiTest.kt (no-picker + icon-changes-preview-color; 7/7 green on emulator-5554)
  - ColorPicker.kt kept (still used by AccountEdit + import wizard)
