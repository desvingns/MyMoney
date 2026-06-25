# Append-only журнал операций через Google Drive — epic overview
Epic: operations-journal-sync
Order: 00 of 07
Status: backlog
Depends-on: —
Date: 2026-06-25

## Goal
Заменяем облачный снапшот-синк (сериализация всей БД → один файл на Drive → скачать → заменить) на **append-only журнал операций**, синхронизируемый через ту же интеграцию Google Drive. Каждая мутация (добавил транзакцию, удалил категорию, отредактировал счёт) пишется как op с глобальным `uuid` + `updatedAt` + `deviceId`. Каждое устройство пишет свой файл в **общей видимой папке Drive** (для разных Google-аккаунтов); merge = объединение всех ops по времени; правка одной записи двумя людьми разрешается **last-write-wins по записи** (max `updatedAt`, tiebreak `deviceId`). Near-real-time: WorkManager-периодика + триггер при открытии приложения + pull-to-refresh. **Архитектура — state-primary + журнал-sidecar:** Room остаётся источником истины, журнал — параллельная запись; удалённые ops применяются к локальным таблицам по `uuid`. **Объём v1 — транзакции, категории, счета.** Вне зоны: event-sourcing; валюты/курсы/цели/бюджеты/шаблоны; живой Google-вход + DRIVE_FILE consent + picker/шаринг папки (отложенный OQ); компакция журнала; замена локального ручного бэкапа.

## Locked decisions
- Топология — общая видимая папка Drive для **разных аккаунтов**; per-device файлы; нужен `DRIVE_FILE` scope (текущий `DRIVE_APPDATA` не годится). [confirmed]
- Движок (журнал + merge + транспорт) строится поверх абстракции `CloudSyncBackend`/нового `JournalBackend` и тестируется **fake-бекендом**; живой Google sign-in + `DRIVE_FILE` consent + получение/шаринг папки — **отложенный OQ** (OQ-2/OQ-3 + новый folder-OQ). [confirmed]
- Объём журнала v1 — **транзакции + категории + счета**; остальные сущности позже. [confirmed]
- Архитектура — **state-primary + журнал-sidecar** (dual-write на границе репозитория; НЕ event-sourcing). [assumption — следует из «добавить журнал в Room» + минимальной инвазивности]
- Конфликт правок одной записи — **LWW по записи** (max `updatedAt`, tiebreak `deviceId`), запись заменяется целиком. [confirmed]
- Облачный снапшот-синк (`SnapshotSync`) **заменяется** журнальным; локальный ручной export/import `.db` (`BackupRepository`) **остаётся**. [confirmed]
- Триггеры near-real-time — периодика (WorkManager, есть) + open-app + pull-to-refresh. [confirmed]
- Идентичность — сохраняем `Long` PK; добавляем `uuid`(unique)+`deviceId` к Tx/Cat/Acc, +`updatedAt` к Category; `MIGRATION_7_8` backfill `uuid` (SQL `randomblob`). [assumption]
- `deviceId` — стабильный install-UUID в DataStore (идентификатор, не секрет). [assumption]
- `folderId` общей папки берётся из конфигурации (DataStore); получение/создание/шаринг папки — часть отложенного OQ. [assumption]
- Bootstrap — первый запуск после миграции штампует `deviceId` на существующих строках и эмитит initial create-ops для всех неопубликованных. [assumption]
- Применение удалённых ops НЕ ре-эмитит локальные ops (loop-guard); delete = op `opType=Delete` → soft-delete/archive по `uuid`. [assumption]

## SPECs (run via `/mp --feature --next` in Order)
| Order | File | Depends-on | Layers | Summary |
|---|---|---|---|---|
| 01 | `operations-journal-sync-01-operation-model-and-merge.md` | — | domain | `Operation`/`EntityKind`/`OpType` + `OperationMerger` (LWW-по-записи, тайбрейк deviceId, tombstones) + `DeviceIdProvider` интерфейс — чистый домен |
| 02 | `operations-journal-sync-02-journal-table-and-identity-migration.md` | 01 | data | `OperationEntity`/`OperationDao`; +`uuid`+`deviceId` к Tx/Cat/Acc; +`updatedAt` к Category; `MIGRATION_7_8` (backfill uuid); schema→8 + тест миграции |
| 03 | `operations-journal-sync-03-op-emission-dual-write.md` | 01, 02 | data | Dual-write: `upsert`/`delete`/`archive` в 3 репозиториях дополнительно пишут op в журнал в той же транзакции; `DeviceIdProvider` impl на DataStore |
| 04 | `operations-journal-sync-04-journal-apply-engine.md` | 01, 02, 03 | data | `JournalApplier`: дедуп по `opId`, группировка по `uuid`, merge через 01, применение к локальным таблицам (upsert/soft-delete по `uuid`), идемпотентность + loop-guard |
| 05 | `operations-journal-sync-05-drive-shared-folder-transport.md` | 01, 02 | data | `JournalBackend` + `GoogleDriveJournalBackend`: общий `folderId` (DRIVE_FILE), файл `ops-<deviceId>.jsonl`, push=upload, pull=list+download peer-файлов; fake-бекенд для тестов |
| 06 | `operations-journal-sync-06-journal-sync-orchestrator-and-rewire.md` | 03, 04, 05 | data/sync | `JournalSync` (push/pull) заменяет `SnapshotSync` в `SyncWorker`/`SyncScheduler`/`WorkScheduler`; rebind DI; app-open триггер; `JournalBootstrap` (deviceId + initial ops) |
| 07 | `operations-journal-sync-07-pull-to-refresh-and-sync-status-ui.md` | 06 | presentation | Pull-to-refresh на dashboard → sync; статус/last-sync + поле `folderId` в `:feature:cloudsync` |

## Why this ordering
Foundation-first: чистая доменная логика (01) → фундамент данных (02) → запись ops (03) → применение (04) → транспорт (05, нужны лишь 01/02 — может идти параллельно 03/04) → оркестрация, сшивающая всё и перепроводящая планировщик (06) → UI-триггеры (07). Клэши по файлам **строго последовательны**: `OperationDao` правят 02 (создаёт) и 04 (добавляет apply-запросы) → 02 раньше; `SyncModule` правят 05 (binding `JournalBackend`) и 06 (rebind `SnapshotSync`→`JournalSync`) → 05 раньше; `CloudSyncViewModel` правят 06 (свап зависимости) и 07 (status UI) → 06 раньше. `MyMoneyApp.kt` правит только 06 (риск dex-crash при старте — clean-assemble + smoke перед «done», см. CONSTRAINTS 06). Параллельная правка любого общего файла запрещена.

## Key facts (verified) — из grounding.md
- G1–G2: текущий `SnapshotSync`/`SnapshotSyncRepository` (push/autoSyncConnected, детект конфликта по окну) — `core/sync/.../SnapshotSync.kt:3-26`, `SnapshotSyncRepository.kt:54-122`.
- G3: `CloudSyncBackend` — `upload/listSnapshots/downloadNewest/prune` (multibinding Dropbox+GDrive) — `core/sync/.../CloudSyncBackend.kt:11-32`.
- G4: `GoogleDriveRepository` — `DriveScopes.DRIVE_APPDATA`, appData-папка, `application/x-sqlite3` — `core/sync/.../gdrive/GoogleDriveRepository.kt:30-133`.
- G5 (БЛОКЕР): live `LaunchGoogleSignIn` gated `gdriveSyncEnabled()` (OQ-2/OQ-3 не закрыты); `DRIVE_APPDATA` НЕ поддерживает общие папки между аккаунтами — `GoogleDriveRepository.kt:55-57`.
- G6–G9: `SyncWorker` (`push`/`autoSyncConnected`), `SyncSchedulerImpl` (periodic 6ч), `WorkSchedulerImpl` (из `MyMoneyApp.onCreate`), `SyncModule` (DI binding) — `core/sync/.../worker/SyncWorker.kt:24-40`, `SyncSchedulerImpl.kt:26-45`, `WorkSchedulerImpl.kt:28-59`, `app/.../MyMoneyApp.kt:52-56`, `core/sync/.../di/SyncModule.kt:21-47`.
- G10–G12: `:feature:cloudsync` VM/экран, вход из настроек; ошибки → `SyncException(SyncError)` → string-res; app-open/pull-to-refresh синка СЕЙЧАС НЕТ — `feature/cloudsync/.../CloudSyncViewModel.kt:26-219`.
- G13–G18: Room **version=7** (last `MIGRATION_6_7`) → новая `MIGRATION_7_8`; ВСЕ сущности `@PrimaryKey(autoGenerate=true) Long`, UUID/deviceId нет; `Transaction/Account/Goal` имеют `updatedAt`, **`Category` — нет** (только `createdAt`); `TransactionRepository` уже имеет `softDelete/restore/pruneDeleted`, у Cat/Acc только `archive` — `core/database/.../MoneyDatabase.kt:67`, entity/*.kt, `core/domain/.../repository/*.kt`.
- G19: `SyncLogEntity` (`sync_log`) — лог событий по target, без device-идентичности (не журнал мутаций) — `entity/SyncLogEntity.kt:1-22`.
- G20: `BackupRepository` — локальный ручной export/import `.db` через SAF, отдельно от облачного синка (остаётся) — `core/domain/.../repository/BackupRepository.kt:18-52`.
- G21: деньги BigDecimal/Double, время Instant·LocalDate/Long через `MoneyTypeConverters`; `@IoDispatcher` везде — `core/database/.../converter/MoneyTypeConverters.kt`.
- G23–G29: миграц.-тесты `core/database/src/androidTest/.../MoneyDatabaseMigrationXToYTest.kt` (`MigrationTestHelper`); `const val` в анон. Migration нельзя; ktlintFormat перед коммитом; `:core:domain` таска `test`; runner false-negative по `:core:*`; устройство обязательно для instrumented.

## Deferred (OQ — не в этом эпике)
- O3/OQ: live Google OAuth + `DRIVE_FILE` consent screen + picker/создание/шаринг общей папки (нужен живой аккаунт).
- O1: HLC/монотонные часы вместо wall-clock (риск скоса часов между людьми).
- O2: компакция/снапшоттинг растущего журнала.

## Implementation links
- commit: (pending)
- files:  (pending)
