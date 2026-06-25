# Оркестратор JournalSync + перепроводка планировщика + bootstrap
Epic: operations-journal-sync
Order: 06 of 07
Status: backlog
Depends-on: 03, 04, 05
Date: 2026-06-25

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: Сшиваем журнал в работающий синк. `JournalSync` (push = выгрузить неотправленные локальные ops через `JournalBackend`; pull = скачать файлы соседей → `JournalApplier`) ЗАМЕНЯЕТ `SnapshotSync` в `SyncWorker`/`SyncScheduler`/`WorkScheduler` и в `CloudSyncViewModel`; DI перепривязывается. Триггер при открытии приложения. `JournalBootstrap` на первом запуске после миграции штампует `deviceId` и эмитит initial ops. Локальный ручной бэкап (`BackupRepository`) не трогаем (D6).
LAYERS: data, sync
CHANGED_HINT:
  - core/sync/src/main/java/com/kshavrin/mymoney/core/sync/JournalSync.kt — новый: `interface JournalSync { suspend fun push(); suspend fun pull(); suspend fun syncNow() }` + impl: `push` — `folderId` из конфига (D10) + `deviceId`; `OperationDao.unsyncedLocal()` (02) → `JournalSerializer` (05) → `JournalBackend.uploadJournal` → `markSynced`; `pull` — `listPeerJournals(folderId)` → новые/изменённые → `downloadJournal` → parse → `JournalApplier.apply` (04) → продвинуть high-water по `modifiedAt`. Занимает роль `SnapshotSync` (G1). (G1, 04, 05)
  - core/sync/.../worker/SyncWorker.kt:24-40 — вызывать `JournalSync` вместо `SnapshotSync.push/autoSyncConnected` (G6). (G6)
  - core/sync/.../SyncSchedulerImpl.kt:26-45 — планировать периодический `JournalSync` (WorkManager остаётся; минимальный период WorkManager = 15 мин) (G7). (G7)
  - core/sync/.../WorkSchedulerImpl.kt:28-59 — включать периодику при настроенной общей папке/`connectedTargets` (G8). (G8)
  - core/sync/.../di/SyncModule.kt — rebind: использование `SnapshotSync` → `JournalSync`; снапшот-облако-путь выводится из autosync (D6). КЛЭШ с 05 (там `@Binds JournalBackend`): 05 раньше. (G9; clash 05)
  - app/src/main/java/com/kshavrin/mymoney/MyMoneyApp.kt:52-56 — триггер синка ПРИ ОТКРЫТИИ приложения (one-shot `JournalSync` на старте/возврате в foreground, D7). (G8)
  - feature/cloudsync/.../CloudSyncViewModel.kt:26-138 — заменить инжект `SnapshotSync` → `JournalSync` для `syncNow`/autosync (минимальная перепроводка; статус-UI — в 07). КЛЭШ с 07: 06 раньше. (G10)
  - core/sync/.../journal/JournalBootstrap.kt — новый: one-shot после миграции — проставить `deviceId` строкам где `deviceId=''` (uuid уже backfill'ен в 02) и эмитировать initial `Upsert`-ops для строк, которых ещё нет в журнале (peers получат существующий реестр); флаг «выполнено» в DataStore (D11). (D11, 02, 03)
TEST_TYPES: unit, dao
CONSTRAINTS:
  - `MyMoneyApp.onCreate` — чувствительная зона: правка стартового/DI-кода вне gradle-гейтов даёт NoClassDefFound/dex-crash при старте (см. инцидент). ПЕРЕД «done»: чистый `:app:assembleDebug` + запуск + logcat-smoke (НЕ только тесты).
  - Near-real-time: минимальный период WorkManager = 15 мин — «таймер» не даёт секунд; реальная свежесть — от open-app + pull-to-refresh (07) (D7).
  - ЗАМЕНА, не дублирование (D6): убрать `SnapshotSync` из autosync-пути; локальный ручной export/import `.db` (`BackupRepository`, G20) ОСТАЁТСЯ. Старую снапшот-облако-проводку АРХИВИРОВАТЬ (`archive/`), не удалять (правило проекта).
  - У журнального синка НЕТ диалога конфликта (LWW авто, D5) — старый conflict-prompt путь (`resolveConflict`) выводится; связанный UI чистит 07.
  - `folderId` из конфига (D10); если не задан — синк no-op с понятным статусом (UI в 07).
  - `JournalBootstrap` идемпотентен и выполняется один раз (флаг DataStore).
  - `SyncModule` общий с 05, `CloudSyncViewModel` общий с 07 — строго последовательно. Изменения интерфейсов рябят на фейки (G26). `:core:*` тесты проверять вручную (runner false-neg, G27).
=== END SPEC ===

## Acceptance
```gherkin
Feature: Оркестрация журнального синка и перевод планировщика
  Покрывает JournalSync, перепроводку Worker/Scheduler, bootstrap.

  Scenario: Push выгружает неотправленные локальные операции
    Given в журнале есть неотправленные локальные операции и задан folderId
    When выполняется push
    Then операции выгружены в общую папку и помечены отправленными

  Scenario: Pull применяет операции соседей
    Given сосед выгрузил новые операции в общую папку
    When выполняется pull
    Then эти операции применяются к локальному состоянию (через applier)

  Scenario: Синк при открытии приложения
    Given autosync включён и folderId задан
    When приложение открывается
    Then запускается одноразовый JournalSync

  Scenario: Bootstrap публикует существующий реестр один раз
    Given первое открытие после миграции с существующими транзакциями/категориями/счетами
    When выполняется bootstrap
    Then существующим строкам проставлен deviceId
    And для них в журнал записаны initial Upsert-операции
    And повторный запуск bootstrap ничего не делает

  Scenario: Folder не задан — синк не падает
    Given folderId не сконфигурирован
    When срабатывает триггер синка
    Then синк завершается без ошибки и сообщает статус «папка не настроена»
```

## Gap / context
После 03–05 есть запись, применение и транспорт, но они не связаны и планировщик всё ещё гоняет снапшот-синк (G6–G8). Этот SPEC даёт оркестратор, переводит расписание и стартовый триггер на журнал и публикует существующие данные через bootstrap.

## Implementation links
- commit: (pending)
- files:  (pending)
