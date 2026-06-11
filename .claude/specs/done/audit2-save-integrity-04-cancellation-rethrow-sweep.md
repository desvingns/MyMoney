# CancellationException не глотается: sweep по catch-блокам
Epic: audit2-save-integrity
Order: 04 of 04
Status: done
Depends-on: —
Date: 2026-06-10

## SPEC
=== SPEC ===
TASK: bugfix
PLATFORM: android
WHAT: Отмена корутины не должна превращаться в баннер ошибки, событие Sentry или retry-попытку воркера. Во всех catch(Throwable)/runCatching по списку аудита первой строкой выполняется re-throw CancellationException (или `coroutineContext.ensureActive()`), затем существующий маппинг ошибок.
LAYERS: cross
CHANGED_HINT:
  - feature/transaction/.../expense/AddExpenseViewModel.kt:209 — re-throw до маппинга в errorBanner (G9); аналогично в save-путях AddIncome/Transfer/Detail
  - core/sync/.../SnapshotSyncRepository.kt:51,67,74 — re-throw до remap в SyncError.Unknown/Sentry (G9)
  - core/sync/.../DropboxRepository.kt:124-138 (runOnIo) — re-throw (G9)
  - feature/transactionslist/.../search/SearchViewModel.kt:77-80 — отменённый поиск (быстрый ввод) не летит в Sentry (G9)
  - core/sync/.../worker/RecurringWorker.kt:19-22 и остальные воркеры (Prune/BackupRotation/Sync) — отмена воркера не считается retry-попыткой: re-throw из runCatching (G9)
TEST_TYPES: unit
CONSTRAINTS:
  - Шаблон единый: `catch (t: Throwable) { if (t is CancellationException) throw t; … }` — без введения новых утилит, если в :core:common нет готовой (assumption: допустимо добавить inline-хелпер в :core:common при ≥5 использованиях).
  - Поведение НЕуспеха (не-отмена) не меняется: тот же маппинг, те же баннеры.
  - Тест: отмена scope во время save/поиска → нет errorBanner/Sentry-вызова; обычное исключение → прежний маппинг.
=== END SPEC ===

## Acceptance (Gherkin)
```gherkin
Feature: Отмена — не ошибка

  Scenario: Быстрый ввод в поиске
    Given пользователь быстро печатает запрос (каждый ввод отменяет предыдущий поиск)
    Then отменённые поиски не порождают ошибок и событий мониторинга

  Scenario: Уход с формы во время сохранения
    Given сохранение запущено
    When scope ViewModel отменяется
    Then баннер ошибки не показывается

  Scenario: Реальная ошибка по-прежнему видна
    Given репозиторий бросает IOException при сохранении
    Then пользователь видит существующий баннер ошибки
```

## Gap / context
Баг M4 аудита (G9): сквозной анти-паттерн ломает structured concurrency — отмена маппится в
ошибки UI/Sentry, у воркеров сжигает retry-попытки.

## Implementation links
- commit: 71e6185c, eaffe213, 7316d500
- files: core/sync/src/main/java/com/kshavrin/mymoney/core/sync/SnapshotSync.kt; core/sync/src/main/java/com/kshavrin/mymoney/core/sync/SnapshotSyncRepository.kt; core/sync/src/main/java/com/kshavrin/mymoney/core/sync/dropbox/DropboxRepository.kt; core/sync/src/main/java/com/kshavrin/mymoney/core/sync/worker/BackupRotationWorker.kt; core/sync/src/main/java/com/kshavrin/mymoney/core/sync/worker/PruneDeletedWorker.kt; core/sync/src/main/java/com/kshavrin/mymoney/core/sync/worker/RecurringWorker.kt; core/sync/src/main/java/com/kshavrin/mymoney/core/sync/worker/SyncWorker.kt; feature/transaction/src/main/java/com/kshavrin/mymoney/feature/transaction/expense/AddExpenseViewModel.kt; feature/transaction/src/main/java/com/kshavrin/mymoney/feature/transaction/income/AddIncomeViewModel.kt; feature/transaction/src/main/java/com/kshavrin/mymoney/feature/transaction/transfer/TransferViewModel.kt; feature/transactionslist/src/main/java/com/kshavrin/mymoney/feature/transactionslist/detail/TransactionDetailViewModel.kt; feature/transactionslist/src/main/java/com/kshavrin/mymoney/feature/transactionslist/search/SearchViewModel.kt; core/sync/src/test/java/com/kshavrin/mymoney/core/sync/SnapshotSyncRepositoryTest.kt; core/sync/src/test/java/com/kshavrin/mymoney/core/sync/dropbox/DropboxRepositoryTest.kt; core/sync/src/test/java/com/kshavrin/mymoney/core/sync/worker/WorkerCancellationBehaviorTest.kt; feature/transaction/src/test/java/com/kshavrin/mymoney/feature/transaction/expense/AddExpenseViewModelTest.kt; feature/transaction/src/test/java/com/kshavrin/mymoney/feature/transaction/income/AddIncomeViewModelTest.kt; feature/transaction/src/test/java/com/kshavrin/mymoney/feature/transaction/transfer/TransferViewModelTest.kt; feature/transactionslist/src/test/java/com/kshavrin/mymoney/feature/transactionslist/detail/TransactionDetailViewModelTest.kt; feature/transactionslist/src/test/java/com/kshavrin/mymoney/feature/transactionslist/search/SearchViewModelTest.kt
