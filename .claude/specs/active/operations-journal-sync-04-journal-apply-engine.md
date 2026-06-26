# Движок применения удалённых операций
Epic: operations-journal-sync
Order: 04 of 07
Status: backlog
Depends-on: 01, 02, 03
Date: 2026-06-25

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: `JournalApplier` в `:core:database` принимает батч удалённых операций, дедупит по `opId`, группирует по `entityUuid`, через `OperationMerger` (01) — с учётом локального состояния — определяет победителя LWW и применяет его к локальным таблицам по `uuid` (upsert / soft-delete), резолвя FK по `uuid`. Идемпотентен; не ре-эмитит локальные ops (loop-guard). Это «приёмная» часть синка: транспорт (05) и оркестратор (06) скармливают сюда чужие ops.
LAYERS: data
CHANGED_HINT:
  - core/database/src/main/java/com/kshavrin/mymoney/core/database/journal/JournalApplier.kt — новый: `suspend fun apply(remoteOps: List<Operation>)`: отбросить уже известные по `opId` (`OperationDao.knownOpIds`, 02); группировать по `entityUuid`; для группы собрать набор {удалённые ops + локальное состояние записи как op} и вызвать `OperationMerger.resolve` (01); применить победителя через uuid-DAO (upsert по uuid / soft-delete по uuid); записать применённые ops с `appliedFromRemote=true` (известны для дедупа, не пушатся). Декодирование payload — `OperationPayloadCodec` (03). (01, 02, 03; assumption — новый файл)
  - core/database/.../dao/OperationDao.kt — добавить (к созданному в 02): `insertApplied(ops)` / уточнить `knownOpIds()` для дедупа. КЛЭШ с 02 — 02 раньше (создаёт DAO), 04 дополняет. (G18; clash 02)
  - core/database/.../dao/TransactionDao.kt — добавить `findByUuid(uuid)`, upsert-по-uuid (сохраняя локальный Long id), `softDeleteByUuid(uuid, now)` (G18). (G18)
  - core/database/.../dao/CategoryDao.kt — добавить `findByUuid`, upsert-по-uuid, `archiveByUuid(uuid)` (G18). (G18)
  - core/database/.../dao/AccountDao.kt — добавить `findByUuid`, upsert-по-uuid, `archiveByUuid(uuid)` (G18). (G18)
TEST_TYPES: dao, unit
CONSTRAINTS:
  - ИДЕМПОТЕНТНОСТЬ: повторное применение того же батча — no-op (дедуп по `opId`); тест на реальном in-memory Room.
  - LOOP-GUARD (D12): применять ТОЛЬКО через DAO напрямую, НЕ через публичные `RepositoryImpl.upsert/archive` (03) — иначе бесконечная ре-эмиссия. Координируется с seam из 03.
  - LWW и ПРОТИВ ЛОКАЛЬНОГО: в набор для `resolve` включать локальное текущее состояние записи (его `updatedAt`), чтобы старая удалённая op не затёрла более свежую локальную правку.
  - FK ПО UUID: payload ссылается на категорию/счёт по `uuid` (не по локальному Long id — он у каждого устройства свой); applier резолвит `uuid → локальный id`. Порядок применения в батче: сперва `Account` и `Category`, затем `Transaction` (цель FK должна существовать); неразрешимый FK → отложить/пропустить запись до появления цели. (зависит от формата payload в 03)
  - `OperationDao` — общий файл с 02 (02 создаёт, 04 дополняет): строго последовательно, без параллельной правки.
  - Новые методы DAO/`OperationDao` рябят на модуль-локальные фейки (G26).
=== END SPEC ===

## Acceptance
```gherkin
Feature: Применение удалённых операций к локальному состоянию
  Покрывает JournalApplier (merge + запись по uuid + идемпотентность).

  Scenario: Применение удалённого создания
    Given локально нет записи с данным uuid
    When применяется удалённая операция Upsert с этим uuid
    Then в локальной таблице появляется запись с этим uuid

  Scenario: Удалённая правка побеждает по LWW
    Given локальная запись с updatedAt = T
    When применяется удалённая операция Upsert той же записи с updatedAt > T
    Then локальная запись обновляется значениями из удалённой операции

  Scenario: Старая удалённая правка не затирает свежую локальную
    Given локальная запись с updatedAt = T2
    When применяется удалённая операция той же записи с updatedAt = T1 < T2
    Then локальная запись НЕ меняется

  Scenario: Идемпотентное повторное применение
    Given батч удалённых операций уже применён
    When тот же батч применяется снова
    Then локальное состояние не меняется и дубли операций не появляются

  Scenario: Применение без повторной эмиссии
    Given применяется удалённая операция Upsert
    When она записана в локальную таблицу
    Then новая локальная операция в журнал НЕ пишется (loop-guard)

  Scenario: Удалённое удаление как tombstone
    Given локальная запись с данным uuid существует
    When применяется удалённая операция Delete (победитель по LWW)
    Then локальная запись помечается удалённой
```

## Gap / context
После 03 локальные ops пишутся и (в 05) выгружаются, но чужие ops применять нечем — состояния устройств не сходятся. Этот SPEC реализует слияние входящих ops в локальное состояние с LWW, идемпотентностью и защитой от петли.

## Implementation links
- commit: (pending)
- files:  (pending)
