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

- [ ] Re-read TDD anchors. Pay closest attention to §9.5 error-handling table — every exception type → app behaviour → user message → Sentry level.
- [ ] Add Firebase to `:app/build.gradle.kts` via `id("com.google.gms.google-services")` + `google-services.json` in `app/` (do not commit; CI injects per OQ-9). If OQ-9 not yet resolved, gate Firebase init on `BuildConfig.HAS_FIREBASE = providers.gradleProperty("firebase.enabled").orNull == "true"`.
- [ ] **Dropbox**: register at developers.dropbox.com (OQ-2). Add `<intent-filter><action android:name="android.intent.action.VIEW"/>...<data android:scheme="db-YOUR_APP_KEY"/></intent-filter>` to `MainActivity` in manifest. Replace `YOUR_APP_KEY` with the real one when OQ-2 resolves; meanwhile use a placeholder + leave `dropbox_sync_enabled = false` in RC defaults.
- [ ] **GDrive**: Google Sign-In setup (OQ-3). Add SHA-1 fingerprints (debug + release) to the Google Cloud OAuth consent screen. Package name = `com.kshavrin.mymoney`. Without OQ-3 resolved, `gdrive_sync_enabled = false`.
- [ ] **Sentry**: replace `BuildConfig.SENTRY_DSN` with the real DSN from OQ-1. Confirm crashes auto-report. Add `Sentry.addBreadcrumb` calls around each `push/pull` operation in `SnapshotSyncRepository` per §9.1 lines 2167–2169.
- [ ] **Remote Config**: set defaults via `setDefaultsAsync(R.xml.remote_config_defaults)` (create `res/xml/remote_config_defaults.xml` with the 6 params from §9.1). Fetch 12 h prod / 0 s debug. Read at app start; cache values.
- [ ] **Snapshot sync flow**:
  - Push: `MoneyDatabase.close()` → copy db file → `MoneyDatabase` re-open → `DropboxRepository.upload(file, "/Apps/MyMoney/monefy_backup_<yyyyMMddHHmm>.db")` → write `SyncLogEntity(target = Dropbox, event = push, status = success)`. Keep last 3 by listing folder + deleting older.
  - Pull: download newest → swap into Room location (same shutdown/open dance as S18 import) → write SyncLog.
  - Conflict: if local `AppSettings.lastSyncAt < remoteServerModified` AND local modified-since-last-sync, show `ConflictResolutionDialog`.
- [ ] **WorkManager**: register `SyncWorker` as `PeriodicWorkRequest(6.hours)` on startup if `autoSyncEnabled == true`. Cancel on toggle off. Constraints: `UNMETERED + battery not low`.
- [ ] **Error mapping** (§9.5): catch each named exception, map to `SyncException(SyncError.*)`, write to `SyncLog`, capture to Sentry at the specified level. User-facing message comes from `strings.xml` (EN + RU per §9.5 table column).
- [ ] Wire S17 into `MyMoneyNavHost`. Wire from S14 Settings root.
- [ ] **Manual test** (if OQ-1/2/3 resolved): connect Dropbox → push → verify file appears in your Dropbox `/Apps/MyMoney/`. Same for GDrive.
- [ ] **Manual test** (without external creds): app launches, S17 shows "Not connected", buttons disabled (RC values cached false), no crashes.
- [ ] Test Sentry: from a debug-only button, throw a test exception → confirm event in Sentry UI.
- [ ] Update PROGRESS.md. Tick OQ-1, OQ-2, OQ-3, OQ-5, OQ-9 if resolved this session.

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

(empty — fill at end of session. Mention which OQ items are still open.)
