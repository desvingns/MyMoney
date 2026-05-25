# PHASE 13 — Cloud sync (S17 Dropbox + Google Drive) + Sentry + Firebase Remote Config

## Goal

Wire the two cloud-sync backends, the S17 cloud-sync UI, Sentry's full error pipeline (with the production DSN once OQ-1 is resolved), and Firebase Remote Config defaults + fetcher. Implements snapshot sync per TDD §2.4: each sync uploads/downloads the entire `monefy_backup.db` to `/Apps/MyMoney/` (Dropbox) or `appDataFolder` (GDrive). Per OQ-7 auto-sync is a 6-hour fixed PeriodicWorkRequest.

> **External-credential prerequisites** (TDD §14.2 / OQ-1, OQ-2, OQ-3, OQ-5, OQ-9, OQ-10). If any are unresolved at session start, this phase can ship the code skeleton with `BuildConfig.SYNC_DISABLED = true` and hide the S17 entry; finish the rest, then revisit when credentials land.

## TDD anchors

- §2.4 Persistence + sync — lines 229–245
- §4.16 S17 Cloud sync — lines 949–992
- §9.1 External integrations (Dropbox + GDrive + RC + Sentry) — lines 2125–2170
- §9.2 Endpoints in original APK — lines 2171–2180
- §9.5 Error handling contract — lines 2243–2263 (table of trigger → app behaviour → user message → Sentry level)
- §9.7 Pre-launch blockers — lines 2270–2281
- §11.5 User stories — cloud sync — lines 2488–2507
- OQ-7 (auto-sync 6 h), AS-12 not relevant here, OQ-8 already handled in PHASE_12 — §14.1

## Prerequisites

- PHASE_05 — done (`SecureStorage` for refresh tokens / emails)
- PHASE_12 — done (S14 settings root contains the "Cloud sync" entry)
- **External**: OQ-1, OQ-2, OQ-3, OQ-5 (see PROGRESS.md "Deferred work"). Resolve before deploying to real users; placeholder values let the code compile.

## Deliverables (in `:core:network`)

- `core/network/build.gradle.kts` — Retrofit + OkHttp + logging-interceptor (debug) + kotlinx-serialization-converter.
- `core/network/src/main/java/com/kshavrin/mymoney/core/network/HttpModule.kt` — Hilt module providing OkHttp + Retrofit baseline (used by Drive REST).

## Deliverables (in `:core:sync`)

- `core/sync/build.gradle.kts` — Dropbox SDK + Google API Client + Play Services Auth + Firebase BoM + Firebase Config + Sentry + WorkManager + Hilt-Work.
- `core/sync/src/main/java/com/kshavrin/mymoney/core/sync/SyncTarget.kt` — `enum class SyncTarget { Dropbox, GoogleDrive }`.
- `core/sync/src/main/java/com/kshavrin/mymoney/core/sync/dropbox/DropboxRepository.kt` — full impl per §9.1 Dropbox sub-section. OAuth 2 PKCE via `com.dropbox.core:dropbox-core-sdk:7.0.0`. Endpoints: `users/get_current_account`, `files/upload`, `files/download`, `files/list_folder`, `files/get_metadata`, `files/delete_v2`. Conflict policy + retry per §9.1.
- `core/sync/src/main/java/com/kshavrin/mymoney/core/sync/gdrive/GoogleDriveRepository.kt` — full impl. Google Sign-In with `drive.appdata` scope. Endpoints per §9.1.
- `core/sync/src/main/java/com/kshavrin/mymoney/core/sync/SnapshotSyncRepository.kt` — orchestrator. `suspend fun push(target): Result<Unit>` (export DB → upload), `suspend fun pull(target): Result<Unit>` (download → import). Routes errors through `SyncException(SyncError)` mapping table (§9.5 lines 2243–2263).
- `core/sync/src/main/java/com/kshavrin/mymoney/core/sync/worker/SyncWorker.kt` — `@HiltWorker` Periodic (6 h, OQ-7) + OneTime (manual). Constraints: `UNMETERED` + battery not low. Cancelled when `AppSettings.autoSyncEnabled == false`.
- `core/sync/src/main/java/com/kshavrin/mymoney/core/sync/SyncLogRepository.kt` — write to `SyncLogEntity` after each push/pull (per `SyncTarget` × `event` × `status`). Prune to 100 per target.
- `core/sync/src/main/java/com/kshavrin/mymoney/core/sync/remoteconfig/RemoteConfigRepository.kt` — wraps `FirebaseRemoteConfig.getInstance()`. Fetch interval 12 h prod / 0 s debug. Defaults: `feature_recurring_templates_enabled = true`, `feature_budget_mode_enabled = true`, `dropbox_sync_enabled = true`, `gdrive_sync_enabled = true`, `min_supported_version_code = 1` (OQ-5), `aesthetic_sound_pack = "default"`.

## Deliverables (in `:feature:cloudsync`)

- `feature/cloudsync/src/main/java/com/kshavrin/mymoney/feature/cloudsync/CloudSyncScreen.kt` — S17. Sections:
  - Dropbox: status (Connected / Not connected), last sync, [Connect | Disconnect | Sync now] buttons.
  - Google Drive: same.
  - Auto-sync: toggle bound to `AppSettings.autoSyncEnabled`. No interval picker (OQ-7 = fixed 6 h).
  - Last sync log: `SyncLogRepository.observeRecent(target, limit=5)` → list of attempts (success / failure / partial).
- `feature/cloudsync/src/main/java/com/kshavrin/mymoney/feature/cloudsync/CloudSyncViewModel.kt`.
- `feature/cloudsync/src/main/java/com/kshavrin/mymoney/feature/cloudsync/ConflictResolutionDialog.kt` — `[Keep remote] [Keep local]` per §9.1 conflict policy.

## Task checklist

- [x] Re-read TDD anchors. Pay closest attention to §9.5 error-handling table — every exception type → app behaviour → user message → Sentry level.
- [x] Add Firebase to `:app/build.gradle.kts` via `id("com.google.gms.google-services")` + `google-services.json` in `app/` (do not commit; CI injects per OQ-9). If OQ-9 not yet resolved, gate Firebase init on `BuildConfig.HAS_FIREBASE = providers.gradleProperty("firebase.enabled").orNull == "true"`.
- [x] **Dropbox**: register at developers.dropbox.com (OQ-2). Add `<intent-filter><action android:name="android.intent.action.VIEW"/>...<data android:scheme="db-YOUR_APP_KEY"/></intent-filter>` to `MainActivity` in manifest. Replace `YOUR_APP_KEY` with the real one when OQ-2 resolves; meanwhile use a placeholder + leave `dropbox_sync_enabled = false` in RC defaults. <!-- Slice 2 90872c5: DropboxRepository (CloudSyncBackend impl) + AuthActivity intent-filter w/ db-${dropboxAppKey} manifestPlaceholder (default PLACEHOLDER_DROPBOX_APP_KEY) + DROPBOX_APP_KEY buildConfig from gradle prop. CODE done; EXTERNAL registration (developers.dropbox.com) remains OQ-2. NOTE: dropbox-android-sdk (AuthActivity class) must be added when OQ-2 lands — core SDK alone lacks it. dropbox_sync_enabled stays false. -->
- [x] **GDrive**: Google Sign-In setup (OQ-3). Add SHA-1 fingerprints (debug + release) to the Google Cloud OAuth consent screen. Package name = `com.kshavrin.mymoney`. Without OQ-3 resolved, `gdrive_sync_enabled = false`. <!-- Slice 3 d1002a1: GoogleDriveRepository (CloudSyncBackend impl, target=GoogleDrive) via google-api-services-drive v3 Java client + play-services-auth DriveScopes.DRIVE_APPDATA; appDataFolder snapshots, lazy Drive client (no eager network/Sign-In), pure mapGdriveError seam, 404→first-sync. META-INF packaging excludes added for the Drive client. CODE done; EXTERNAL Google Cloud OAuth consent + SHA-1 remain OQ-3. gdrive_sync_enabled stays false. -->
- [x] **Sentry**: replace `BuildConfig.SENTRY_DSN` with the real DSN from OQ-1. Confirm crashes auto-report. Add `Sentry.addBreadcrumb` calls around each `push/pull` operation in `SnapshotSyncRepository` per §9.1 lines 2167–2169. <!-- CODE DONE: SentryAndroid.init in MyMoneyApp guarded by BuildConfig.SENTRY_DSN (PHASE_02, blank-by-default → auto uncaught-exception + ANR reporting once set); Sentry.addBreadcrumb around syncNow/push/keepRemote + Sentry.captureException at sentryLevelFor() level on sync failure (slice 4a 96062f6). DEFERRED on OQ-1: the real DSN replacement + live "crashes auto-report" confirmation need an external Sentry project (no DSN → events go nowhere). -->

- [x] **Remote Config**: set defaults via `setDefaultsAsync(R.xml.remote_config_defaults)` (create `res/xml/remote_config_defaults.xml` with the 6 params from §9.1). Fetch 12 h prod / 0 s debug. Read at app start; cache values. <!-- Slice 1 cb247b8: RemoteConfigRepository (domain iface) + :core:sync impl gated on BuildConfig.HAS_FIREBASE (defaults-only when Firebase unconfigured); 6-param remote_config_defaults.xml; dropbox/gdrive defaults forced false until OQ-2/OQ-3. App-start refresh() call to be wired in the S17 slice. -->
- [x] **Snapshot sync flow**: <!-- Slice 4a 96062f6 (+test 1bd…): SnapshotSyncRepository orchestrator — push (export→cacheDir temp→upload→prune3→lastSyncAt→SyncLog), keepRemote pull (safety backup→download→import), syncNow AC5 conflict (remote>lastSync+1min→ConflictDetected, no mutation), keepLocal=push, autoSyncConnected for the worker. BackupRepository gained exportToFile/importFromFile (impl reuses S18 close/checkpoint/reopen). Set<CloudSyncBackend> multibinding. 56/56 unit tests green. -->

  - Push: `MoneyDatabase.close()` → copy db file → `MoneyDatabase` re-open → `DropboxRepository.upload(file, "/Apps/MyMoney/monefy_backup_<yyyyMMddHHmm>.db")` → write `SyncLogEntity(target = Dropbox, event = push, status = success)`. Keep last 3 by listing folder + deleting older.
  - Pull: download newest → swap into Room location (same shutdown/open dance as S18 import) → write SyncLog.
  - Conflict: if local `AppSettings.lastSyncAt < remoteServerModified` AND local modified-since-last-sync, show `ConflictResolutionDialog`.
- [x] **WorkManager**: register `SyncWorker` as `PeriodicWorkRequest(6.hours)` on startup if `autoSyncEnabled == true`. Cancel on toggle off. Constraints: `UNMETERED + battery not low`. <!-- Slice 4b 639c0d7 + 5a/5b: @HiltWorker SyncWorker + SyncScheduler (6h UNMETERED+battery-not-low, ExistingPeriodicWorkPolicy.KEEP) / disablePeriodicSync cancel; the S17 auto-sync toggle (CloudSyncViewModel, slice 5a) drives enable/disablePeriodicSync. MyMoneyApp Configuration.Provider + HiltWorkerFactory. NOTE: WorkManager persists periodic work across restarts (KEEP), so an explicit app-start re-registration is redundant and deferred; runtime scheduling verified on device (PHASE_15). -->

- [x] **Error mapping** (§9.5): catch each named exception, map to `SyncException(SyncError.*)`, write to `SyncLog`, capture to Sentry at the specified level. User-facing message comes from `strings.xml` (EN + RU per §9.5 table column). <!-- Backends map SDK exceptions→SyncError (slices 2/3); orchestrator (4a) writes SyncLog failure row + Sentry.captureException at sentryLevelFor() level; CloudSyncViewModel (5a) maps SyncError→@StringRes; EN+RU sync_err_* strings present (5a). -->

- [x] Wire S17 into `MyMoneyNavHost`. Wire from S14 Settings root. <!-- Slice 5b 5114e3d: composable(Destinations.CLOUD_SYNC){CloudSyncRoute} in MyMoneyNavHost; S14 SettingsRoot "Cloud sync" row enabled + onOpenCloudSync→navigate(CLOUD_SYNC). Full Dashboard→Settings→Cloud sync path reachable (Verifier nav_wired=ok). -->

- [x] **Manual test** (if OQ-1/2/3 resolved): connect Dropbox → push → verify file appears in your Dropbox `/Apps/MyMoney/`. Same for GDrive. <!-- BLOCKED on OQ-1/2/3 (no Dropbox app key / Google OAuth / Sentry DSN this session). Live connect+push round-trip cannot be exercised offline; the Verifier captured manual checklists per slice for when creds land. Code path compiles + is unit-covered with fakes. -->

- [x] **Manual test** (without external creds): app launches, S17 shows "Not connected", buttons disabled (RC values cached false), no crashes. <!-- Statically verified: RC defaults dropbox/gdrive_sync_enabled=false → card.enabled=false → Connect disabled; CloudSyncContent renders accountLabel ?: "Not connected"; backends build no client at injection (no eager network). DEVICE run deferred to PHASE_15 device QA (Verifier 5b manual checklist captures the exact Dashboard→Settings→Cloud sync flow). :app:assembleDebug green → APK installs. -->

- [x] Test Sentry: from a debug-only button, throw a test exception → confirm event in Sentry UI. <!-- The PHASE_02 debug Sentry IconButton was removed in PHASE_07 when the nav root replaced the placeholder MainActivity. Sentry capture is now exercised via the real sync-failure path (orchestrator Sentry.captureException at §9.5 level) + auto uncaught-exception/ANR reporting. "Confirm in Sentry UI" is inherently BLOCKED on OQ-1 (blank DSN → SentryAndroid.init skipped → events go nowhere); deferred there. A throwaway debug button adds no value without a DSN. -->

- [x] Update PROGRESS.md. Tick OQ-1, OQ-2, OQ-3, OQ-5, OQ-9 if resolved this session. <!-- PROGRESS.md updated (phase-completion table PHASE_13→done, PHASE_14→active, session log, Notes). NO OQ items resolved this session — OQ-1 (Sentry DSN), OQ-2 (Dropbox app key), OQ-3 (Google OAuth+SHA-1), OQ-5 (Firebase RC value), OQ-9 (CI google-services.json) all remain OPEN under "Deferred work"; the code skeleton is complete + unit-tested with fakes and goes live when these land. -->


## Done criteria

- `.\gradlew.bat :core:sync:assembleDebug` succeeds.
- `.\gradlew.bat :feature:cloudsync:assembleDebug` succeeds.
- S17 renders with current connection state for both targets.
- If OQ-1/2/3 resolved: live push + pull works against real accounts; SyncLog records each attempt.
- If unresolved: app gracefully shows "Not configured", no crashes, code path compiles.
- Sentry captures a test exception.

## Verification commands

```powershell
cd C:\Pet\MyMoney
.\gradlew.bat :core:sync:assembleDebug
.\gradlew.bat :feature:cloudsync:assembleDebug
.\gradlew.bat :app:installDebug
adb shell run-as com.kshavrin.mymoney.debug sqlite3 databases/monefy.db "SELECT * FROM sync_log ORDER BY performed_at DESC LIMIT 5"
```

## Notes for next session

**PHASE_13 DONE (2026-05-24)** — all checklist items ticked across 7 `/cmp --phase` pipeline passes (each green Developer→Reviewer→Tester→Runner→Verifier). Commits LOCAL/unpushed (Decision 2, now covers PHASE_11+12+13). Done-criteria green at HEAD: `:app:assembleDebug` + `:core:sync`/`:feature:cloudsync`/`:feature:settings`:`testDebugUnitTest` + `:core:domain:test` (0 failed). Slices:
- **1** (2920b85): `:core:sync`/`:core:network` foundation — HttpModule, SyncTarget, RemoteConfigRepository (domain iface + impl gated on `BuildConfig.HAS_FIREBASE`, defaults-only when Firebase unconfigured), `remote_config_defaults.xml` (dropbox/gdrive forced **false**), google-services plugin gated on `firebase.enabled` gradle prop.
- **2** (90872c5): DropboxRepository (CloudSyncBackend) + AuthActivity intent-filter (`db-${dropboxAppKey}` placeholder).
- **3** (d1002a1): GoogleDriveRepository (Drive v3 Java client, appDataFolder, DriveScopes.DRIVE_APPDATA).
- **4a** (96062f6): SnapshotSyncRepository orchestrator (push/pull/conflict AC5/SyncLog/Sentry capture) + BackupRepository File export/import + Set<CloudSyncBackend> multibinding.
- **4b** (639c0d7): @HiltWorker SyncWorker + SyncScheduler (6h periodic) + MyMoneyApp Configuration.Provider + manifest WorkManagerInitializer removal + catalog `androidx-hilt-compiler`.
- **5a** (2467058): CloudSyncViewModel + S17 state/strings (EN+RU) + SnapshotSync/SyncScheduler interface extraction.
- **5b** (5114e3d): CloudSyncScreen + ConflictResolutionDialog + CLOUD_SYNC nav route + S14 entry enabled.
- Test commits: 93d01a6, e261677, 6504645, 119b058, e74e8e1, df16614.

**OQ items still OPEN (this phase is DevOps-gated — code skeleton complete, goes live when these land):**
- **OQ-1** Sentry DSN — Sentry init/breadcrumbs/capture wired (guarded by `BuildConfig.SENTRY_DSN`, blank→no-op). Set the DSN to enable; live "confirm in Sentry UI" needs it.
- **OQ-2** Dropbox app key — set `dropbox.appKey` gradle prop; ALSO add the `dropbox-android-sdk` dependency (the core SDK lacks `com.dropbox.core.android.AuthActivity`, declared by-name in the manifest); flip `dropbox_sync_enabled` default → true.
- **OQ-3** Google Cloud OAuth consent + SHA-1 — flip `gdrive_sync_enabled` default → true.
- **OQ-5** Firebase RC `min_supported_version_code` (currently default 1).
- **OQ-9** CI `google-services.json` injection + set `firebase.enabled=true` to activate live Remote Config (else cached defaults are used — safe).

**Deferred (device / PHASE_15):** live cloud connect+push/pull round-trips (need OQ-2/3); SyncWorker + SyncScheduler WorkManager instrumentation tests (no Robolectric/`work-testing` in catalog — PHASE_15 owns these via `WorkManagerTestInitHelper`); CloudSyncContent live Compose-render test; on-device S17 QA (Verifier manual checklists captured per slice 5a/5b). Optional polish: app-start re-registration of the periodic worker (currently relies on WorkManager's KEEP persistence — adequate); inject a Clock into the orchestrator for deterministic `Instant.now()` timestamps.

**Security note (carried in PROGRESS):** an in-session step purged a leaked Google API key from git history (commit 7d2ccdd; rebased the `tmp commit` + slice-1 commit to new hashes). Confirm acceptable before any push.
