# Таблица журнала + миграция глобальной идентичности
Epic: operations-journal-sync
Order: 02 of 07
Status: done
Depends-on: 01
Date: 2026-06-25

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: Фундамент данных журнала в `:core:database`. Новая таблица `op_journal` (`OperationEntity` + `OperationDao`). Добавляем глобальную идентичность к синхронизируемым сущностям v1: колонки `uuid`(unique) + `deviceId` к `transactions`/`categories`/`accounts`, плюс недостающий `updatedAt` к `categories` (G16). `MIGRATION_7_8` создаёт таблицу, добавляет колонки и **backfill'ит `uuid`** для существующих строк; schema version 7→8; инструментальный тест миграции. Это единственный SPEC, правящий схему БД.
LAYERS: data
CHANGED_HINT:
  - core/database/src/main/java/com/kshavrin/mymoney/core/database/entity/OperationEntity.kt — новый: `@Entity(tableName="op_journal")` с `@PrimaryKey opId: String`, `deviceId: String`, `entityKind: String`, `@ColumnInfo(index=true) entityUuid: String`, `opType: String`, `payload: String?`, `updatedAt: Long`(epoch-ms, G21), `syncedToRemote: Boolean=false`, `appliedFromRemote: Boolean=false`. Зеркалит домен `Operation` из 01; форма по образцу `SyncLogEntity` (G19). (assumption)
  - core/database/.../dao/OperationDao.kt — новый: `@Insert insert(op)`/`insertAll(ops)`; `@Query unsyncedLocal(): List<OperationEntity>` (`syncedToRemote=0 AND appliedFromRemote=0` — локальные ops для push); `markSynced(opIds: List<String>)`; `knownOpIds(): List<String>` / `existsByOpId(opId): Boolean` (дедуп); `opsForEntity(entityUuid): List<OperationEntity>`. (assumption)
  - core/database/.../entity/TransactionEntity.kt:47-62 — добавить `uuid: String`, `deviceId: String` (есть `updatedAt`, G16). (G16)
  - core/database/.../entity/CategoryEntity.kt:12-23 — добавить `uuid: String`, `deviceId: String` И `updatedAt: Long` (ОТСУТСТВУЕТ, H5/G16). (G16)
  - core/database/.../entity/AccountEntity.kt:21-34 — добавить `uuid: String`, `deviceId: String` (есть `updatedAt`, G16). (G16)
  - core/database/.../MoneyDatabase.kt:28-40,67 — добавить `OperationEntity` в `@Database entities`; bump `version = 7 → 8` (G13–G14). (G13)
  - core/database/.../migration/Migrations.kt — новый `val MIGRATION_7_8 = object: Migration(7,8){...}`: `CREATE TABLE op_journal(...)`; на каждой из 3 таблиц `ALTER TABLE … ADD COLUMN uuid TEXT NOT NULL DEFAULT ''` + `… deviceId TEXT NOT NULL DEFAULT ''` (+ `categories … updatedAt INTEGER NOT NULL DEFAULT 0`); backfill `UPDATE <t> SET uuid = lower(hex(randomblob(16))) WHERE uuid = ''`; затем `CREATE UNIQUE INDEX index_<t>_uuid ON <t>(uuid)`. `const val` ВНУТРИ анонимной `Migration` нельзя — `private val` (G24). (G13, G24)
  - core/database/.../di/DatabaseModule.kt — зарегистрировать `MIGRATION_7_8` в `.addMigrations(...)` (G24). (G24)
  - core/database/src/androidTest/java/com/kshavrin/mymoney/core/database/MoneyDatabaseMigration7To8Test.kt — новый: `MigrationTestHelper`, `createDatabase(name,7)` + seed строк tx/cat/acc, `runMigrationsAndValidate(name,8,true,MIGRATION_7_8)`, raw-`db.query` ассерты: `op_journal` существует; `uuid` непустые и уникальные; `categories.updatedAt` присутствует (G23). (G23)
TEST_TYPES: dao, instrumented
CONSTRAINTS:
  - Миграция тестируется ИНСТРУМЕНТАЛЬНО на поднятом устройстве (`:core:database:connectedDebugAndroidTest`, `MigrationTestHelper` + `runMigrationsAndValidate`, схема экспортируется в `schemas/8.json`); устройство обязательно (G23, G29).
  - `const val` в анонимной `Migration` запрещён — instance `private val`; bump версии — в `MoneyDatabase.kt`, регистрация — в `DatabaseModule` (G24).
  - `deviceId` остаётся `''` ПОСЛЕ миграции (runtime неизвестен на миграции); заполняется `JournalBootstrap` на первом запуске (SPEC 06, D11). `uuid` backfill'ится сразу — это 32-симв. hex, не RFC-UUID, но глобально уникален (опаковый id).
  - UNIQUE-индекс по `uuid` требует, чтобы код ВСЕГДА проставлял `uuid` при вставке новых строк (иначе 2-я вставка с `''` нарушит индекс) — это обеспечивает SPEC 03; зафиксировать связь.
  - Контракт-тесты с захардкоженными счётчиками/схемой могут потребовать обновления при смене версии (G25).
  - Новый `OperationDao` и новые поля сущностей рябят на модуль-локальные фейки соответствующих интерфейсов (G26) — обновить фейки в потребляющих модулях.
=== END SPEC ===

## Acceptance
```gherkin
Feature: Журнал операций и глобальная идентичность в Room
  Покрывает MIGRATION_7_8 и таблицу op_journal.

  Scenario: Миграция 7→8 создаёт журнал и колонки идентичности
    Given база схемы версии 7 с существующими транзакциями, категориями и счетами
    When применяется MIGRATION_7_8
    Then существует таблица op_journal
    And у transactions, categories, accounts есть колонки uuid и deviceId
    And у categories появляется колонка updatedAt

  Scenario: Backfill uuid для существующих строк
    Given несколько строк каждой из трёх таблиц до миграции
    When применяется MIGRATION_7_8
    Then каждая существующая строка получает непустой uuid
    And все uuid в пределах таблицы уникальны

  Scenario: deviceId после миграции пуст
    Given строки версии 7
    When применяется MIGRATION_7_8
    Then deviceId существующих строк пуст (заполнится bootstrap'ом на первом запуске)

  Scenario: Запись и чтение операции журнала
    Given пустая таблица op_journal
    When вставляется операция и запрашиваются неотправленные локальные операции
    Then операция возвращается как неотправленная
    And повторная вставка с тем же opId не создаёт дубля при дедупе
```

## Gap / context
В проекте нет ни таблицы журнала, ни глобальных идентификаторов сущностей (всё на `Long` autoincrement, G15), у категорий нет `updatedAt` (G16). Без этого нельзя ни писать, ни сливать, ни переносить ops между устройствами. Этот SPEC закрывает разрыв на уровне схемы.

## Implementation links
- commit: (pending)
- files:  (pending)
