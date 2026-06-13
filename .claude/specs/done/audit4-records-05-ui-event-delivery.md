# Надёжная доставка one-shot событий и устранение UI-гонок
Epic: audit4-records
Order: 05 of 05
Status: done
Depends-on: audit4-records-03
Date: 2026-06-10

## SPEC
=== SPEC ===
TASK: bugfix
PLATFORM: android
WHAT: (1) Undo-снэкбар списка больше не блокирует очередь действий: showSnackbar уходит в дочернюю корутину — тапы по строкам срабатывают мгновенно. (2) recomputeBalance дашборда отменяет предыдущий расчёт (Job-поле) — при быстрых свайпах периода не побеждает устаревший результат, конфетти не дублируется. (3) Сбор one-shot действий переводится на repeatOnLifecycle(STARTED) через общий хелпер в :core:ui — события при повороте не теряются; применить на Dashboard и BackupRestore (худшие по последствиям: RestartAfterRestore).
LAYERS: presentation
CHANGED_HINT:
  - feature/transactionslist/.../list/TransactionsListScreen.kt:97-115 — `scope.launch { showSnackbar(...) }` вместо await в collect-цикле; результат Undo обрабатывается в той же дочерней корутине (G7)
  - feature/dashboard/.../DashboardViewModel.kt:245-271 — `private var recomputeJob: Job?`; cancel() перед новым запуском (G8)
  - core/ui — НОВЫЙ хелпер `CollectActions(flow) { }` на repeatOnLifecycle(Lifecycle.State.STARTED) (assumption: имя/пакет по месту)
  - feature/dashboard/.../DashboardScreen.kt:87-89 и feature/settings/.../BackupRestoreScreen.kt:96-117 — перейти на хелпер (G9)
  - тесты: VM-тест отмены устаревшего recompute (Turbine); снэкбар-кейс compose-ui — тап по строке при видимом снэкбаре открывает деталку сразу
TEST_TYPES: unit, compose-ui
CONSTRAINTS:
  - SharedFlow(replay=0) у VM сохраняется — меняется только сторона сбора; недоставленные при полной паузе события остаются известным ограничением (фиксация в Gap).
  - `DashboardViewModel.kt`/`DashboardScreen.kt` — общие файлы со SPEC 03: выполняется ПОСЛЕ него.
  - Остальные экраны мигрируют на хелпер опортунистически (вне scope этого SPEC).
=== END SPEC ===

## Acceptance (Gherkin)
```gherkin
Feature: События UI доставляются надёжно

  Scenario: Тап по строке при видимом undo-снэкбаре
    Given пользователь удалил запись и снэкбар Undo виден
    When он немедленно тапает другую строку
    Then деталка открывается без многосекундной задержки

  Scenario: Быстрые свайпы периода
    Given пользователь быстро листает три периода подряд
    Then показанные баланс и донат соответствуют последнему периоду

  Scenario: Поворот не теряет навигацию
    Given сохранение завершилось во время поворота экрана
    Then NavigateBack доставляется после пересоздания и экран закрывается
```

## Gap / context
Баги M6/M7/M5-события аудита (G7, G8, G9). Полная гарантия доставки требует Channel/replay —
осознанно остаёмся на SharedFlow + repeatOnLifecycle (минимальный дифф, закрывает окно поворота).

## Implementation links
- commit: dd654997 (feat), d8ba8a79 (tests) — pushed to main 2026-06-13
- files:
  - core/ui/.../flow/CollectActions.kt (NEW) — repeatOnLifecycle(STARTED) one-shot action collector
  - feature/dashboard/.../DashboardViewModel.kt — recomputeJob?.cancel() before relaunch
  - feature/dashboard/.../DashboardScreen.kt + feature/settings/.../backup/BackupRestoreScreen.kt — migrated to CollectActions
  - feature/transactionslist/.../list/TransactionsListScreen.kt — undo snackbar in child coroutine (unblocks action loop)
  - DashboardViewModelTest.kt (recompute-cancel) + app/.../TransactionsListContentUiTest.kt (snackbar non-blocking, 22/22 device-green)
