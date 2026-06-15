# Импорт-визард: экран ручного объединения категорий (список-резолвер)
Epic: import-migration-wizard
Order: 05 of 06
Status: done
Depends-on: 03, 04
Date: 2026-06-14

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: Шаг 3 визарда, показывается ТОЛЬКО когда на шаге 2 выбрана стратегия `AppendManualMerge` (D6).
Макет — «список-резолвер по импорту»:
(1) Импортные категории, точно совпавшие по имени с существующими, уже объединены автоматически (G5) — их не показываем.
(2) Для каждой НЕсовпавшей импортной категории — строка: название импортной категории + выпадающий список действия:
«Создать новую» (по умолчанию) ИЛИ объединить с одной из существующих категорий (того же kind).
(3) При выборе «объединить» появляется поле «итоговое имя» (✏), по умолчанию = имя выбранной существующей категории.
(4) Из выборов формируется `List<CategoryMergeMapping>` (SPEC 01), который кладётся в `ImportPlan` и уходит в
`commitImport` (SPEC 03): MergeInto → записи импортной категории идут в target (id реюз), target переименовывается;
CreateNew → обычное создание.
LAYERS: presentation
CHANGED_HINT:
  - feature/settings/src/main/java/com/kshavrin/mymoney/feature/settings/import/MergeResolverScreen.kt — НОВЫЙ:
    LazyColumn строк-резолверов; на строку — DropdownMenu действий + опц. поле имени (assumption — новый composable)
  - feature/settings/src/main/java/com/kshavrin/mymoney/feature/settings/import/ImportWizardViewModel.kt — РАСШИРИТЬ
    (создан в SPEC 04): держать список несовпавших импортных категорий + существующих категорий (того же kind, из
    read-метода SPEC 03), события выбора действия/имени, сборка `List<CategoryMergeMapping>` в state (G17)
  - app/src/main/res/values/strings.xml + values-ru/strings.xml — строки экрана объединения (G23)
TEST_TYPES: compose-ui, unit
CONSTRAINTS:
  - **CLASH:** расширяет `ImportWizardViewModel`/`State` из SPEC 04 → строго ПОСЛЕ 04; не параллельно с 04/06.
  - Показывать в выпадающем списке только существующие категории ТОГО ЖЕ kind (Expense/Income) — matching kind-aware (G5).
  - Дефолт каждой строки = «Создать новую» (безопасно: ничего не объединяется без явного действия пользователя, D6).
  - «Точно совпавшие по имени» в список НЕ попадают (они авто-merge, G5) — резолвер только для расхождений имён.
  - Экран — чистый сбор `CategoryMergeMapping`; никакой записи в БД (коммит — SPEC 03 на завершении визарда).
  - Без хардкод-строк (G23, RU+EN). `:feature:settings:ktlintFormat` перед коммитом (G19); Compose-UI тест модуля
    верифицировать напрямую (runner пропускает :feature:*, G20).
=== END SPEC ===

## Acceptance (Gherkin)
```gherkin
Feature: Ручное объединение похожих категорий при импорте

  Background:
    Given выбрана стратегия категорий "объединить вручную"
    And импортная категория "Еда" не совпала по имени ни с одной существующей

  Scenario: Объединение разных по имени категорий с новым именем
    When пользователь выбирает для "Еда" действие "объединить с Продукты"
    And задаёт итоговое имя "Продукты"
    And нажимает Далее
    Then в план уходит маппинг "Еда" -> объединить в "Продукты" с именем "Продукты"

  Scenario: По умолчанию создаётся новая категория
    When пользователь не меняет действие для "Кафе"
    And нажимает Далее
    Then в план уходит маппинг "Кафе" -> создать новую

  Scenario: В выпадающем списке только категории того же типа
    Given "Еда" — категория расхода
    Then в списке для объединения только существующие категории расхода

  Scenario: Точные совпадения по имени не показываются
    Given импортная категория "Зарплата" точно совпала с существующей "Зарплата"
    Then строка "Зарплата" в резолвере отсутствует
```

## Gap / context
Реализует ключевое требование пользователя: уметь отметить «Еда» (импорт) и «Продукты» (приложение) как одну
категорию, задать новое имя и слить записи (D6). Без этого экрана ManualMerge-стратегия (SPEC 03) не имеет UI.

## Implementation links
- commit: 5938d7b6 (feat), test commit follows (test), pushed to main 176769e9
- files:
  - feature/settings/.../importwizard/MergeResolverScreen.kt (new — ManualMergeStep resolver list)
  - feature/settings/.../importwizard/ImportWizardViewModel.kt (ManualMerge step + events + toPlan mappings)
  - feature/settings/.../importwizard/ImportWizardScreen.kt (wire ManualMerge into step `when`)
  - app/src/main/res/values/strings.xml + values-ru/strings.xml (import_wizard_merge_* EN+RU)
  - feature/settings/.../importwizard/ImportWizardViewModelTest.kt (+10 ManualMerge VM tests, 49 total)
  - feature/settings/.../importwizard/ImportWizardContentTest.kt (+9 ManualMerge content tests, 43 total)
