# Идемпотентная генерация повторяющихся транзакций
Epic: audit9-sync-hardening
Order: 01 of 04
Status: active
Depends-on: —
Date: 2026-06-10

## SPEC
=== SPEC ===
TASK: bugfix
PLATFORM: android
WHAT: Ретрай RecurringWorker после частичного прогона не должен дублировать платежи. Генерация по каждому шаблону выполняется атомарно: вставка occurrence-транзакций и updateNextRun — в ОДНОЙ DB-транзакции; убитый посреди цикла процесс при повторе продолжает с непрооцессированных шаблонов, уже обработанные не генерируются повторно (их nextRunAt уже сдвинут).
LAYERS: domain, data
CHANGED_HINT:
  - core/domain/.../repository/TransactionRunner.kt — НОВЫЙ интерфейс `suspend fun <T> inTransaction(block: suspend () -> T): T` (если не создан в audit7-forms-hardening-04 — G7; иначе переиспользовать)
  - core/database — impl на database.withTransaction + Hilt-binding (паттерн Decision 3)
  - core/domain/.../usecase/GenerateDueRecurringUseCase.kt:26-37 — на каждый шаблон: `runner.inTransaction { insert(occurrences); updateNextRun(...) }` (G1)
  - тесты: fake-runner — падение после первого шаблона → его occurrence+nextRun консистентны; повторный запуск не дублирует
TEST_TYPES: unit, dao
CONSTRAINTS:
  - Семантику расчёта occurrence (AS-11 silent) не менять — только границы транзакции.
  - Worker-слой не трогать (retry-логика остаётся; CancellationException — уже в audit2-04).
  - Гранулярность — на ШАБЛОН (не на весь прогон): частичный прогресс сохраняется при сбое.
=== END SPEC ===

## Acceptance (Gherkin)
```gherkin
Feature: Рекурренты без дублей

  Scenario: Сбой посреди прогона
    Given due-шаблоны A и B
    And процесс убит после обработки A
    When воркер ретраит прогон
    Then occurrence шаблона A существует ровно один раз
    And шаблон B обработан

  Scenario: Атомарность одного шаблона
    Given вставка occurrence шаблона A упала
    Then nextRunAt шаблона A не сдвинут
    And occurrence не записан
```

## Gap / context
Баг H7 аудита (G1): insert и updateNextRun разорваны — kill/ретрай между ними задваивает балансы.
Спящий (UI шаблонов нет — G6), но обязан закрыться до его появления.

## Implementation links
- commit: ab71e60d (fix), 653b75c8 (tests)
- files:
  - core/domain/.../usecase/GenerateDueRecurringUseCase.kt — each template's generate() body wrapped in TransactionRunner.runInTransaction (per-template atomicity; reused existing RoomTransactionRunner binding from audit7-04)
  - core/domain/.../usecase/GenerateDueRecurringUseCaseAtomicityTest.kt (new, 9 tests) — fake throwing runner proves no occurrence + no nextRun advance on failure; partial run-progress survives
  - core/domain/.../usecase/GenerateDueRecurringUseCaseTest.kt (updated, 12 tests) — pass-through fake runner threaded into ctor; semantics unchanged
- verified: :core:domain:test green (26 recurring-related tests, 0 failures); pushed 653b75c8→main
