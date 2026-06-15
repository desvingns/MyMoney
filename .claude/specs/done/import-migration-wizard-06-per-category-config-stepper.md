# Импорт-визард: «настроить сейчас или потом» + степпер по категориям
Epic: import-migration-wizard
Order: 06 of 06
Status: done
Depends-on: 04
Date: 2026-06-14

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: Финальный шаг визарда (D7).
(1) После выбора стратегии категорий — гейт «Настроить каждую категорию сейчас или потом?».
(2) «Потом» → импорт завершается (коммит уже выполнен на завершении SPEC 04), показывается подтверждение.
(3) «Сейчас» → пошаговая настройка по ВСЕМ результирующим категориям (после коммита) по одной: на каждом шаге
редактируются имя/иконка/цвет (G13); кнопки «Назад»/«Далее», на последней категории «Далее» → «Готово».
(4) Каждая правка сохраняется через существующий путь обновления категории (CategoryRepository/`CategoryDao.upsert`),
не через import-commit.
LAYERS: presentation
CHANGED_HINT:
  - feature/settings/src/main/java/com/kshavrin/mymoney/feature/settings/import/CategoryConfigStepScreen.kt — НОВЫЙ:
    шаг настройки одной категории (имя + пикер иконки + пикер цвета из `:core:designsystem`) + навигация Назад/Далее/Готово;
    mirror `CategoryEditScreen`/`CategoryEditViewModel` (G15), но встроенный шаг визарда, не отдельный экран (assumption O4)
  - feature/settings/src/main/java/com/kshavrin/mymoney/feature/settings/import/ImportWizardViewModel.kt — РАСШИРИТЬ
    (SPEC 04): гейт сейчас/потом, очередь категорий (все результирующие), индекс шага, save через CategoryRepository.upsert,
    `Action` на завершение (G15/G17)
  - app/src/main/res/values/strings.xml + values-ru/strings.xml — строки гейта и шага настройки (G23)
TEST_TYPES: compose-ui, unit
CONSTRAINTS:
  - **CLASH:** расширяет `ImportWizardViewModel`/`State` из SPEC 04 → строго ПОСЛЕ 04; не параллельно с 04/05.
  - **Модули (O4):** шаг компонует пикер иконки и пикер цвета из `:core:designsystem` (registry уже там —
    redesign-monefy-fidelity / icon-library-expansion) + поле имени — НЕ зависеть от `:feature:dictionaries`
    (`:feature→:feature` запрещено). Если готового пикера цвета в designsystem нет — вынести его туда (отметить в реализации).
  - Очередь = ВСЕ результирующие категории (выбор пользователя D7), не только новые/объединённые; показывать прогресс «k / N».
  - На последнем шаге кнопка «Далее» меняется на «Готово»; «Готово»/«Потом» завершают визард (запись `CsvImportFocus`
    уже сделана на коммите SPEC 04 — здесь только правки категорий).
  - O3 (assumption): верхняя кнопка закрытия позволяет выйти досрочно, сохранив уже внесённые правки.
  - Сохранение правок — через существующий путь обновления категории (upsert), НЕ через import-commit (коммит завершён).
  - Без хардкод-строк (G23, RU+EN). `:feature:settings:ktlintFormat` перед коммитом (G19); UI-тест модуля верифицировать
    напрямую (runner пропускает :feature:*, G20). Иконки НЕ добавляем → `CategoryIconsTest` (67/66, G21) не трогаем.
=== END SPEC ===

## Acceptance (Gherkin)
```gherkin
Feature: Пошаговая настройка категорий после импорта

  Scenario: "Потом" завершает импорт
    Given коммит импорта выполнен
    When пользователь выбирает "настроить потом"
    Then визард закрывается с подтверждением, настройка не показывается

  Scenario: "Сейчас" проходит по всем категориям
    Given после импорта есть 3 результирующие категории
    When пользователь выбирает "настроить сейчас"
    Then он по очереди настраивает каждую (имя/иконка/цвет)
    And может вернуться назад к предыдущей

  Scenario: На последней категории кнопка становится "Готово"
    Given пользователь на последней из 3 категорий
    Then кнопка "Далее" заменена на "Готово"
    When он нажимает "Готово"
    Then изменения сохранены и визард завершается

  Scenario: Правка имени категории сохраняется
    Given пользователь на шаге настройки категории "Еда"
    When он меняет имя на "Питание" и идёт дальше
    Then категория сохранена с именем "Питание"
```

## Gap / context
Закрывает последнее требование пользователя: после выбора категорий — спросить «настроить сейчас/потом», и при
«сейчас» дать пошагово навести порядок (имя/иконка/цвет) по всем категориям с навигацией вперёд/назад и «Готово»
в конце (D7). Переиспользует паттерн формы категории (G15) и пикеры из designsystem (O4).

## Implementation links
- commit: c72dc6dd (ui tokens) + e149669e (feat) + 29793d11 (tests)
- files:
  - feature/settings/.../importwizard/CategoryConfigStepScreen.kt (new)
  - feature/settings/.../importwizard/ImportWizardViewModel.kt (extended: now/later gate, config queue=ALL resulting categories, k/N progress, Back/Next/Done, per-category save via CategoryRepository.upsert, Finished action, early-exit)
  - feature/settings/.../importwizard/ImportWizardScreen.kt (gate + step routing)
  - feature/settings/.../res/values/strings.xml + values-ru/strings.xml (EN+RU gate/step strings)
  - core/designsystem/.../picker/ColorPickerGrid.kt + IconPickerGrid.kt + CategoryIconKeys.kt (extracted pickers, no :feature→:feature dep)
  - core/ui/.../theme/Color.kt + Spacing.kt (wizard step/picker tokens)
  - tests: ImportWizardViewModelTest.kt, ImportWizardContentTest.kt, CategoryConfigStepContentTest.kt (274 settings tests green; ktlint+tests verified on :feature:settings + :core:designsystem)
