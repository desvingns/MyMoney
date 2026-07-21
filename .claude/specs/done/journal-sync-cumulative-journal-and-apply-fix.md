# Fix journal sync: cumulative per-device journal + honest apply marking
Epic: —
Order: standalone
Status: done
Depends-on: —
Date: 2026-07-20
Completed: 2026-07-21

## SPEC
=== SPEC ===
TASK: bugfix
WHAT: Fix two data-integrity bugs in the shipped Google Drive / Dropbox journal-sync protocol, found during on-device cross-account testing (Pixel 9 API 37 + OnePlus 11). Symptoms observed by the user: (1) sync worked only ONE way (data reached Pixel 9 but new Pixel 9 edits never appeared on OnePlus, even after app restart), and (2) after a factory reset on Pixel 9 sync stopped working entirely on Pixel 9 too. Root causes are in the sync LOGIC, not the (already-fixed) auth layer.

  BUG 1 (primary — non-cumulative journal): `JournalSyncImpl.push()` encodes only `operationDao.unsyncedLocal()` (the newest un-pushed delta) and `GoogleDriveJournalBackend.uploadJournal()` does `files.update(existingId, …, content)` which REPLACES the whole Drive file with that delta. Because `push()` then calls `markSynced(...)`, the next push never re-includes older ops. So each device's `ops-<deviceId>.jsonl` only ever holds its most recent push batch — never full history. Consequences: a reset/reinstalled/late-joining device can never reconstruct full state (it filters out its own file on pull and peers' files hold only their last delta), AND a later delta that references an account/category created in an earlier (now-overwritten) delta arrives at a peer with an unresolvable FK.

  BUG 2 (secondary — dishonest apply marking): `JournalApplier.apply()` calls `operationDao.insertApplied(fresh.map { it.toAppliedEntity() })` for ALL fresh ops, but `applyTransaction/applyAccount/applyCategory` silently `return` when a referenced account/category uuid or payload is missing. A skipped op is therefore recorded as applied_from_remote=1 forever and the per-file high-water advances, so it is never re-pulled — permanent silent op loss (the "data flows one way and disappears" symptom).

  FIX 1: Make each device upload its FULL local-origin journal, not just the unsynced delta. Add `OperationDao.localOps()` = `SELECT * FROM op_journal WHERE applied_from_remote = 0 ORDER BY updated_at ASC`. In `push()`, keep `unsyncedLocal()` ONLY as the "is there anything new to push?" guard (skip upload when empty to avoid redundant writes), but encode+upload `localOps()` so the file is the complete cumulative journal. Every peer (including a reset device) then rebuilds full state and all intra-device FK references resolve within a single pull. `markSynced` still marks the newly-unsynced ids.

  FIX 2: In `JournalApplier.apply()`, mark as applied ONLY the ops that actually resolved. Make the three apply* helpers report success/skip; `insertApplied` receives just the succeeded ops. Skipped ops (unresolved cross-device FK) must remain re-pullable — since the per-file high-water otherwise blocks re-pull, do NOT advance `peerHighWaterMs` for a peer file whose batch still contains skipped ops, so the next pull retries them once their dependency (another device's file) has been applied. Keep it deterministic and idempotent (knownOpIds dedup still applies).

MIGRATION NOTE (not code — for the human): existing folder files were written by the buggy delta-only code. After this ships, EACH device must Sync now once to rewrite its file as a full cumulative journal. Data already lost on the Pixel 9 factory reset is NOT recoverable; forward sync becomes correct and bidirectional.

LAYERS: [data] [domain]
CHANGED_HINT: core/sync/.../JournalSyncImpl.kt (push uses localOps), core/database/.../dao/OperationDao.kt (new localOps query), core/database/.../journal/JournalApplier.kt (honest apply marking + skip tracking), core/sync/.../JournalSyncImpl.kt pull (conditional high-water advance), tests in core/sync + core/database
TEST_TYPES: unit [dao]
CONSTRAINTS: fakes-only (no MockK/Mockito); deterministic — fixed clocks/ids, no sleeps; DO NOT weaken/@Ignore the existing JournalSyncImplTest / JournalApplierTest / JournalBootstrapTest — extend them; Clean Architecture layering preserved (domain→data direction); DriveScopes stays DRIVE_FILE-only (no protocol/scope change); on-device cross-account re-verification (Pixel 9 API 37 + OnePlus) is the human acceptance gate after JVM gates pass; no secrets touched
=== END SPEC ===

## Gap / context
Discovered 2026-07-20 during manual cross-account on-device testing after the Google Drive
Picker OAuth fix landed (AuthorizationClientDriveAuthorizer + account-picker-first flow — that
part is verified working). The auth layer is fine; these two bugs are in the journal merge/transport
logic and cause silent, directional data loss + unrecoverable state after a reset.

Evidence (file:line at diagnosis time):
- push encodes only the delta: core/sync/.../JournalSyncImpl.kt:43-49
- uploadJournal replaces the file: core/sync/.../gdrive/GoogleDriveJournalBackend.kt:69-74
- pull filters out own file: core/sync/.../JournalSyncImpl.kt:63
- apply marks ALL fresh applied regardless of skip: core/database/.../journal/JournalApplier.kt:50
- applyTransaction silent FK return: core/database/.../journal/JournalApplier.kt:95-97
- factory reset keeps deviceId, clears journal+config: core/sync/.../FactoryResetGatewayImpl.kt:39-43,
  core/datastore/.../AppSettingsRepositoryImpl.kt:37-41

Related backlog SPEC review-2026-07-25-two-device-merge-e2e (two-device convergence integration
test) is the natural companion — once these fixes land, that E2E test should assert cumulative
journals + no silent op loss.

## Post-ship on-device re-verification (2026-07-21)
Re-verifying FIX 1 + FIX 2 on-device (Pixel 9 emulator, real Google account, real Drive folder
with 3 stale peer files) surfaced a THIRD, previously unknown bug in the same protocol:
`AccountSnapshot`/`TransactionSnapshot.currencyId` carried the encoding device's raw local
Room `currency` row id, which has no cross-device meaning — applying a peer's op threw an
uncaught `SQLiteConstraintException` (FK on `currency_id`) that aborted the whole `pull()` (and
therefore the following `push()`), matching "sync does nothing, same on both devices". Fixed in
a separate commit (currency now travels as its portable ISO code, resolved locally via the
existing `CurrencyDao.findByCode`, with the same skip-and-retry mechanism FIX 2 introduced).
Full account: `journal-sync-noncumulative-bug.md` in Claude's project memory.

Human acceptance (2026-07-21): same-Google-account cross-device sync now converges
bidirectionally (Pixel 9 emulator <-> OnePlus physical). Two DIFFERENT Google accounts
connected to the same shared Drive folder do NOT converge ("separate memory") — accepted as a
known, currently out-of-scope limitation; tracked as a new backlog SPEC:
`multi-account-shared-folder-sync.md`.

## Implementation links
- commit: 708c0570 (cumulative journal + honest apply marking), 84d6c4e6 (currency-id portability
  follow-up fix found during this SPEC's on-device acceptance test)
- files: core/sync/src/main/java/com/kshavrin/mymoney/core/sync/JournalSyncImpl.kt,
  core/database/src/main/java/com/kshavrin/mymoney/core/database/dao/OperationDao.kt,
  core/database/src/main/java/com/kshavrin/mymoney/core/database/journal/JournalApplier.kt,
  core/database/src/main/java/com/kshavrin/mymoney/core/database/journal/OperationPayloadCodec.kt,
  core/database/src/main/java/com/kshavrin/mymoney/core/database/journal/JournalBootstrap.kt,
  core/database/src/main/java/com/kshavrin/mymoney/core/database/repository/BackupRepositoryImpl.kt,
  plus test files in core/sync + core/database (see both commits for the full list)
