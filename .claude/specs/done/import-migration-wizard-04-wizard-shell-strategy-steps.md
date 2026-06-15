# Импорт-визард: каркас + шаги выбора стратегий + диалог сирот
Epic: import-migration-wizard
Order: 04 of 06
Status: done
Depends-on: 02, 03
Date: 2026-06-14

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: Экран-визард миграции импорта (UDF, mirror Onboarding-пейджера G14) и его запуск.
(1) При выборе CSV-файла `BackupRestoreScreen` больше НЕ импортирует сразу — навигирует на маршрут визарда,
передавая `uri` (+ опц. формат) как nav-параметр (G16, `SavedStateHandle`).
(2) `ImportWizardViewModel` в стартовом шаге вызывает `parseImport(uri)` (SPEC 02) → показывает `ImportPreview`
(сколько записей, какие категории/счета, диапазон дат).
(3) Шаг 1 — выбор `ImportDataStrategy` (ReplaceAll/Append/AppendDedup) с пояснениями.
(4) Шаг 2 — выбор `ImportCategoryStrategy` (ReplaceCurrent/Append/AppendManualMerge). Если в шаге 1 выбран
ReplaceAll — шаг 2 пропускается (O1). На пути ReplaceCurrent для каждой существующей категории с транзакциями —
предупреждающий диалог «В категории "X" — N транзакций» → [Оставить категорию] / [Удалить транзакции] (D5),
по умолчанию «Оплавить»; решения копятся в `ImportPlan`.
(5) Деструктивные стратегии (ReplaceAll, удаление транзакций) подтверждаются диалогом (D8).
(6) По завершении (для путей Append/ReplaceCurrent, не требующих экрана объединения и без «настроить сейчас»)
вызывается `commitImport(staged, plan)` (SPEC 02/03), затем — переход к шагу настройки (SPEC 06) или завершение
с записью `CsvImportFocus` в AppSettings (прыжок периода на дашборде, как сейчас G2) + snackbar.
LAYERS: presentation
CHANGED_HINT:
  - feature/settings/src/main/java/com/kshavrin/mymoney/feature/settings/import/ImportWizardViewModel.kt — НОВЫЙ:
    `StateFlow<ImportWizardState>` + `SharedFlow<ImportWizardAction>` (replay=0), `onEvent`, `SavedStateHandle("uri")` (G16/G17)
  - feature/settings/src/main/java/com/kshavrin/mymoney/feature/settings/import/ImportWizardScreen.kt — НОВЫЙ:
    HorizontalPager/степпер вперёд-назад (mirror `OnboardingScreen.kt:49-140`, G14) + `ImportWizardRoute(navController)`
  - feature/settings/src/main/java/com/kshavrin/mymoney/feature/settings/backup/BackupRestoreScreen.kt:93-96 — на
    `ImportCsvFilePicked` навигировать на визард, не импортировать сразу (G1)
  - feature/settings/src/main/java/com/kshavrin/mymoney/feature/settings/backup/BackupRestoreViewModel.kt:95-113 — убрать
    прямой `importTransactionsCsv`; эмитить навигационный Action на визард с uri (G2)
  - app/src/main/java/com/kshavrin/mymoney/navigation/Destinations.kt:21 + MyMoneyNavHost.kt:353 — НОВЫЙ маршрут
    `IMPORT_WIZARD` (с аргументом uri) + `composable(...) { ImportWizardRoute(navController) }` (G16)
  - app/src/main/res/values/strings.xml + values-ru/strings.xml — строки шагов/опций/диалога (G23, без хардкода)
TEST_TYPES: compose-ui, unit
CONSTRAINTS:
  - **CLASH:** создаёт `ImportWizardViewModel`/`ImportWizardState`, которые расширяют SPEC 05 и 06 → 05/06 строго ПОСЛЕ 04.
  - UDF (G17): state — immutable data class; one-shot (навигация/коммит/snackbar) — через `SharedFlow` Action (replay=0).
  - Передавать через nav только `uri` (строка) — не распарсенные данные (G16); парс — внутри VM (`parseImport`, SPEC 02).
    Визард переживает поворот: повторный `parseImport` идемпотентен (файл не меняется).
  - ReplaceAll → шаг 2 пропускается (O1); диалог сирот — только на ReplaceCurrent с непустыми категориями (D5).
  - Прыжок периода (`CsvImportFocus`) переносится с момента старого импорта на завершение коммита визарда (G2) — не потерять.
  - Без хардкод-строк: `stringResource(R.string…)`, RU+EN (G23). `:feature:settings:ktlintFormat` перед коммитом (G19);
    Compose-UI тесты в `:feature:settings` runner пропускает (G20) — верифицировать модуль напрямую.
  - Не ломать существующие `BackupRestoreViewModelTest` (есть тесты на `ImportCsvFilePicked`, `BackupRestoreViewModelTest.kt:282-319`):
    обновить их под новую навигацию-вместо-импорта (G20 — рефакторы API обязаны чинить свои тесты в том же проходе).
=== END SPEC ===

## Acceptance (Gherkin)
```gherkin
Feature: Визард миграции импорта — каркас и выбор стратегий

  Scenario: Выбор файла открывает визард с превью
    Given пользователь на экране Backup & Restore
    When он выбирает CSV-файл
    Then открывается визард и показывает превью (число записей, категории, счета, период)
    And данные ещё не записаны в базу

  Scenario: Путь Append без объединения завершает импорт
    Given визард открыт
    When выбрано "добавить" для данных и "оставить и добавить" для категорий и "настроить потом"
    Then выполняется коммит и показывается подтверждение
    And период на дашборде переходит к импортированным данным

  Scenario: ReplaceAll пропускает вопрос о категориях
    Given выбран "чистый лист" на шаге данных
    When пользователь идёт дальше
    Then шаг выбора категорий не показывается
    And перед коммитом запрашивается подтверждение удаления

  Scenario: Диалог сирот при удалении категорий
    Given выбрано "удалить текущие категории"
    And у существующей категории есть транзакции
    Then показывается диалог с выбором "оставить категорию" или "удалить транзакции"

  Scenario: Назад между шагами сохраняет выбор
    Given пользователь на шаге 2
    When он возвращается на шаг 1 и снова идёт вперёд
    Then ранее выбранные стратегии сохранены
```

## Gap / context
Сейчас выбор файла сразу импортирует append'ом со snackbar (G2). Этот SPEC превращает импорт в управляемый
визард (превью → стратегии → коммит), подключая backend из 02/03 к пользователю; экран объединения (05) и
степпер настройки (06) добавляются поверх этого каркаса.

## Implementation links
- commit: 69989748 (ui tokens) + f58975d0 (wizard shell + nav + BackupRestore rewire) + 91519a23 (tests)
- files: ImportWizardViewModel.kt, ImportWizardScreen.kt (NEW, feature/settings/.../importwizard/), BackupRestoreViewModel.kt, BackupRestoreScreen.kt, Destinations.kt, MyMoneyNavHost.kt, strings.xml (EN+RU), core/ui Color.kt+Shape.kt; tests ImportWizardViewModelTest(38)+ImportWizardContentTest(25)+BackupRestoreViewModelTest(26 updated)
