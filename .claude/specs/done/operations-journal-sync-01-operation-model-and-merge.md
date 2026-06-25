# Доменная модель операции + LWW-merge
Epic: operations-journal-sync
Order: 01 of 07
Status: done
Depends-on: —
Date: 2026-06-25

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: Чистая доменная основа журнала в `:core:domain`. Модель `Operation` (одна мутация одной сущности), перечисления `EntityKind`/`OpType`, чистая функция `OperationMerger.resolve(...)` (last-write-wins по записи с детерминированным тайбрейком по `deviceId` и tombstone на удалении) и интерфейс `DeviceIdProvider`. Никакого I/O, Room, Drive — только модель и алгоритм слияния, полностью покрываемый JVM-юнит-тестами. Это сердце эпика: остальные SPEC-и опираются на эту модель и merge.
LAYERS: domain
CHANGED_HINT:
  - core/domain/src/main/kotlin/com/kshavrin/mymoney/core/domain/sync/Operation.kt — новый: `data class Operation(opId: String, deviceId: String, entityKind: EntityKind, entityUuid: String, opType: OpType, payload: String?, updatedAt: Instant)`; `enum class EntityKind { Transaction, Category, Account }` (объём v1, D3); `enum class OpType { Upsert, Delete }`. `opId`/`entityUuid` — UUID-строки; `updatedAt` — `Instant` (домен, G21); `payload` — сериализованный снимок записи для `Upsert`, `null` для `Delete`. (assumption — новый пакет `sync` в `:core:domain`)
  - core/domain/.../sync/OperationMerger.kt — новый: `object`/класс с чистой `fun resolve(ops: List<Operation>): MergeResult` для ops ОДНОЙ `entityUuid`: дедуп по `opId`, выбор победителя `argmax(updatedAt, затем deviceId)`, `Delete`-победитель → `MergeResult.Tombstone(entityUuid)`, иначе `MergeResult.Resolved(winner)`. `sealed interface MergeResult { Resolved(op), Tombstone(uuid), None }`. Чистая, без `now()`/I/O. (assumption)
  - core/domain/.../sync/DeviceIdProvider.kt — новый: `interface DeviceIdProvider { fun deviceId(): String }` — стабильный install-UUID (D9); реализация на DataStore — в SPEC 03. (assumption)
TEST_TYPES: unit
CONSTRAINTS:
  - `:core:domain` тест-таска — `test` (JVM), НЕ `testDebugUnitTest` (G27); проверять локально, runner-скрипт пропускает `:core:*` (G27). `:core:domain` под Kover-гейтом ~80% (G28) — покрыть merge кейсами из Calculation.
  - Чисто и детерминированно: НИКАКОГО `Instant.now()`/`Clock` внутри — `updatedAt` приходит в самой op; один и тот же вход даёт один и тот же результат на любом устройстве (тайбрейк по `deviceId` это гарантирует, H3).
  - Время `Instant` в домене (G21); сравнение по epoch-ms (Long), без `Double`.
  - Фейк-репозиторий не нужен — функция чистая; не вводить mock-фреймворк (G26).
  - `Delete` НЕ «всегда выигрывает»: разрешается тем же LWW (поздняя правка после удаления = восстановление-как-правка — допустимая LWW-семантика, D13); зафиксировать это в тестах.

### Calculation: LWW-разрешение набора операций одной записи
- Formula: для набора `O` операций с одинаковым `entityUuid`:
  `D = dedupById(O)`; `winner = argmax_{op∈D} ( op.updatedAt , op.deviceId )`
  (сравнение лексикографическое: сначала `updatedAt` как epoch-ms, при равенстве — строковое сравнение `deviceId`).
  `resolve(O) = None` если `D = ∅`; `Tombstone(entityUuid)` если `winner.opType = Delete`; иначе `Resolved(winner)`.
- Symbols: `op.updatedAt` = момент мутации (`Instant`, ключ порядка); `op.deviceId` = стабильный id установки (`String`, тайбрейк); `op.opType` ∈ {Upsert, Delete}; `op.opId` = глоб. id операции (`String`, для дедупа); `op.payload` = снимок записи (`String?`, для Upsert).
- Precision: порядок — по `updatedAt.toEpochMilli()` (Long), целочисленно, без float; тайбрейк делает порядок ПОЛНЫМ и детерминированным на всех устройствах.
- Edge: пустой набор → `None`; одна op → она и победитель; полностью дублирующиеся `opId` → дедуп до одной (идемпотентность); `Upsert` vs `Delete` при равном `updatedAt` → решает `deviceId` (не «delete wins»); `Delete`(ранее) затем `Upsert`(позже) → `Resolved` (восстановление); `Upsert`(ранее) затем `Delete`(позже) → `Tombstone`.
- Worked examples (fixtures):
  | # | ops (opType, updatedAt ms, deviceId)                          | expected            |
  |---|--------------------------------------------------------------|---------------------|
  | 1 | ∅                                                            | None (trivial)      |
  | 2 | [(Upsert,1000,"X"),(Upsert,2000,"Y")]                        | Resolved(op@2000,Y) |
  | 3 | [(Upsert,5000,"aaa"),(Upsert,5000,"bbb")]                    | Resolved(op,"bbb")  (tiebreak: "bbb">"aaa") |
  | 4 | [(Upsert,1000,"X"),(Delete,3000,"Y")]                        | Tombstone           |
  | 5 | [(Delete,1000,"X"),(Upsert,2000,"Y")]                        | Resolved(op@2000,Y) (recreate) |
  | 6 | [op1,(дубль op1)]                                            | Resolved(op1) (dedup) |
=== END SPEC ===

## Acceptance
```gherkin
Feature: Слияние операций по правилу last-write-wins (по записи)
  Покрывает доменную логику OperationMerger (объём v1: транзакции, категории, счета).

  Scenario: Поздняя правка побеждает раннюю
    Given две операции изменения одной записи с разным временем
    When набор разрешается merge'ом
    Then побеждает операция с более поздним updatedAt
    And её снимок становится разрешённым состоянием записи

  Scenario: Детерминированный тайбрейк при равном времени
    Given две операции одной записи с ОДИНАКОВЫМ updatedAt и разными deviceId
    When набор разрешается merge'ом
    Then побеждает операция с лексикографически большим deviceId
    And результат одинаков при любом порядке входных операций

  Scenario: Удаление позже правки даёт tombstone
    Given операция правки и более поздняя операция удаления одной записи
    When набор разрешается merge'ом
    Then результат — tombstone (запись считается удалённой)

  Scenario: Пересоздание после удаления
    Given операция удаления и более поздняя операция создания одной записи
    When набор разрешается merge'ом
    Then результат — разрешённое состояние из операции создания

  Scenario: Идемпотентность по opId
    Given одна и та же операция, продублированная в наборе
    When набор разрешается merge'ом
    Then дубли отбрасываются и результат равен результату для одной операции

  Scenario: Пустой набор
    Given пустой набор операций
    When набор разрешается merge'ом
    Then результат — None
```

## Gap / context
Сейчас нет ни модели операции, ни алгоритма слияния — журнал/применение/транспорт строить не на чем. Этот SPEC даёт чистую, протестированную основу, на которую опираются 02–07.

## Implementation links
- commit: b34075c8, a4f2fea6
- files:
  - core/domain/src/main/kotlin/com/kshavrin/mymoney/core/domain/sync/Operation.kt
  - core/domain/src/main/kotlin/com/kshavrin/mymoney/core/domain/sync/OperationMerger.kt
  - core/domain/src/main/kotlin/com/kshavrin/mymoney/core/domain/sync/DeviceIdProvider.kt
  - core/domain/src/test/kotlin/com/kshavrin/mymoney/core/domain/sync/OperationMergerTest.kt
