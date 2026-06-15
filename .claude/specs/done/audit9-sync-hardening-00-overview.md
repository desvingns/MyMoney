# Эпик: audit9-sync-hardening — рекурренты, restore-safety, авто-sync
Epic: audit9-sync-hardening
Order: 00 of 04 (overview)
Status: done
Completed: 2026-06-14 (all SPECs shipped to main; epic closed during backlog housekeeping)
Depends-on: —
Date: 2026-06-10

## Цель

Спящие, но реальные дефекты sync/worker-слоя из аудита (`docs/audit/2026-06-10-project-audit.md`):
(H7) генерация повторяющихся транзакций неидемпотентна — ретрай после частичного прогона дублирует
платежи; (M15) месячные повторы дрейфуют (Jan 31 → Feb 28 навсегда), weekly игнорирует interval;
(H4) cloud-pull подменяет файл БД под живым приложением без рестарта и удаляет safety-снапшот даже
при провале; (M11) restore файла с более новой схемой окирпичивает приложение; (M8) периодический
авто-sync никогда не планируется без ручного передёргивания тумблера.

Код синка DevOps-гейтнут OFF (OQ-2/3) — эпик идёт ПОСЛЕДНИМ, но 01/02 обязаны выйти раньше любого
UI создания шаблонов рекуррентов.

## Заблокированные решения (из grill)

- D1: эпик в бэклоге сейчас, исполняется последним.
- Идемпотентность — через общую DB-транзакцию на шаблон (insert occurrence + updateNextRun).
- keepRemote идёт тем же restart-after-restore путём, что и локальный restore.

## SPEC'и (собираются через `/mp --feature --next` в порядке)

| Order | File | Depends-on | Layers | Summary |
|---|---|---|---|---|
| 01 | `audit9-sync-hardening-01-recurring-idempotency.md` | — | domain+data | withTransaction(insert+nextRun) на шаблон |
| 02 | `audit9-sync-hardening-02-scheduler-correctness.md` | — | domain | якорный день месяца + weekly interval |
| 03 | `audit9-sync-hardening-03-restore-safety.md` | audit7-forms-hardening-01 | data | keepRemote→рестарт; снапшот при провале; user_version |
| 04 | `audit9-sync-hardening-04-autosync-scheduling.md` | — | data | периодический sync на старте при autoSync |

## Почему такой порядок

01/02 — чистый домен, безопасны в любой момент. 03 ждёт audit7-01 (общий `BackupRepositoryImpl.kt`
— третья правка файла в волне). 04 трогает `MyMoneyApp.kt` после audit5-donut-perf-02.

## Ключевые факты (verified, из grounding)

- G1: `GenerateDueRecurringUseCase` — цикл upsert по одному, `updateNextRun` после цикла, БЕЗ общей транзакции — `core/domain/.../usecase/GenerateDueRecurringUseCase.kt:26-37`; `RecurringWorker` — `Result.retry()` при runAttemptCount<3 — `core/sync/.../worker/RecurringWorker.kt:19-22`.
- G2: дрейф месячных повторов (`current.plusMonths(interval)` без якоря) + weekly сканирует с current+1d и игнорирует interval — `core/domain/.../usecase/RecurringScheduler.kt:17,24-37`.
- G3: keepRemote закрывает @Singleton Room и подменяет файл БЕЗ рестарта процесса — `core/sync/.../SnapshotSyncRepository.kt:72-92` → `BackupRepositoryImpl.kt:368-378`; safety-снапшот удаляется в finally даже при провале — :87-90; эталон рестарта — `RestartAfterRestore` в `BackupRestoreScreen.kt:269-275`.
- G4: restore валидирует только «файл открывается», PRAGMA user_version не сверяется — `BackupRepositoryImpl.kt:81,371`.
- G5: `WorkSchedulerImpl.scheduleDailyJobs` ставит только recurring+prune — :22-44; `enablePeriodicSync` зовётся ТОЛЬКО из тумблера — `CloudSyncViewModel.kt:129`; default autoSyncEnabled=true.
- G6: UI создания RecurringTemplate ещё не существует (ни один feature-модуль не ссылается на RecurringTemplateRepository) — дефекты 01/02 спящие, но обязаны быть закрыты до такого UI.
- G7: транзакционный раннер: при выполнении audit7-forms-hardening-04 интерфейс TransactionRunner уже может существовать — переиспользовать (паттерн Decision 3: интерфейс в :core:domain, impl в :core:database).

## Implementation links
- (заполняется по мере выполнения)
