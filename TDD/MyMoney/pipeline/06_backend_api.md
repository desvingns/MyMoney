# Backend API & Third-Party SDK Integration — MyMoney (Monefy re-implementation)

> Derived from: `02_business.md`, `04_navigation.md`, `05_data_model.md`, `07_apk.md`,
> `user_answers_qD.yaml`, `user_answers_qE.yaml`
> Scope: local-first clone, no proprietary REST backend, no IAP, no ads.
> Network stack: OkHttp 4.12+ / Retrofit 2.11+ / kotlinx.serialization 1.7+

---

## 9.1 — Backend "API"

### Architecture statement

**MyMoney has no proprietary REST backend.** All persistence is local (Room SQLite, version 1,
9 tables). All CRUD operates on the local database. The three external surfaces are:

1. **Dropbox snapshot sync** — full SQLite DB file upload/download via Dropbox SDK.
2. **Google Drive snapshot sync** — full SQLite DB file upload/download via Drive REST v3.
3. **Firebase Remote Config** — feature flags and kill-switches, no data sync.
4. **Sentry** — crash and error reporting only.

There is **no user account** on our backend. Cloud provider account (Dropbox or Google) belongs
to the user and is authenticated per-target. The app never creates or stores its own server-side
user record.

---

### 9.1.1 — Dropbox Snapshot Sync

#### Auth

- Flow: **Dropbox OAuth 2 with PKCE** (Dropbox SDK v5+ built-in PKCE support).
- Entry point: S17 Cloud Sync screen → "Connect Dropbox" button.
- Dropbox SDK launches a browser-based OAuth flow; on completion the SDK sends a callback
  to `dropbox.core.android.AuthActivity` via the scheme `db-wxbzuly0x7v23t8://` (carried
  over from APK manifest; our re-impl uses the same App Key `wxbzuly0x7v23t8` during
  development, replaced with our own registered App Key for production).
- On successful auth, the SDK returns a `DbxCredential` containing an access token and a
  long-lived refresh token. The refresh token is stored in **EncryptedSharedPreferences**
  (`AppSettings.dropboxToken`). The Dropbox SDK handles refresh internally.
- Scope requested: `files.content.write`, `files.content.read`, `files.metadata.read`,
  `account_info.read`.
- Revocation: user taps "Disconnect" in S17 → SDK call `DbxClientV2.auth().tokenRevoke()`,
  then clear token from EncryptedSharedPreferences.

#### SDK Methods Used (not raw HTTP)

| SDK Method | Dropbox API Endpoint (underlying) | Screen | Description |
|---|---|---|---|
| `DbxUserUsersRequests.getCurrentAccount()` | `users/get_current_account` | S17 | Verify identity immediately after OAuth; display linked account name |
| `DbxUserFilesRequests.uploadBuilder(path).withMode(WriteMode.OVERWRITE).uploadAndFinish(stream)` | `files/upload` | S17, S18, WorkManager | Upload full `monefy_backup.db` to `/Apps/MyMoney/monefy_backup.db` |
| `DbxUserFilesRequests.download(path)` | `files/download` | S17, S18 | Download latest backup snapshot |
| `DbxUserFilesRequests.listFolder(path)` | `files/list_folder` | S17 | List existing backups (show last sync timestamp to user) |
| `DbxUserFilesRequests.deleteV2(path)` | `files/delete_v2` | S18 | Remove old backup before rotating (keep last N=3 snapshots) |
| `DbxUserFilesRequests.getMetadata(path)` | `files/get_metadata` | WorkManager | Check `server_modified` timestamp for conflict detection |

#### Sync Flow (Push)

```
1. WorkManager SyncWorker fires (periodic: every 6 h, constraints: unmeteredNetwork + batteryNotLow).
2. SyncWorker calls DropboxSyncRepository.push():
   a. Export Room DB file: close all connections, copy monefy.db to a temp file.
   b. Compute SHA-256 of temp file (store in SyncLog.payloadHash).
   c. Upload via uploadBuilder with WriteMode.OVERWRITE.
   d. On success: record SyncLog(target=dropbox, event=push, status=success, performedAt=now).
   e. Update AppSettings.lastSyncAt.
3. On failure: record SyncLog(status=failure, errorMessage=...), report to Sentry, schedule retry.
```

#### Sync Flow (Pull / Conflict Resolution)

```
1. User taps "Sync now" in S17, or WorkManager triggers pull check.
2. DropboxSyncRepository.pull():
   a. Fetch metadata: getMetadata("/Apps/MyMoney/monefy_backup.db") → server_modified timestamp.
   b. Compare server_modified vs AppSettings.lastSyncAt.
   c. If server_modified <= lastSyncAt → local is current; skip pull.
   d. If server_modified > lastSyncAt → CONFLICT or remote is newer:
      - If local has no writes since lastSyncAt → pull silently (last-write-wins).
      - If local has writes after lastSyncAt → prompt user in S17:
          "Remote backup is newer. Overwrite local data?" [Keep Remote] [Keep Local]
   e. On "Keep Remote": download file, close Room, replace db file, reopen Room.
   f. Record SyncLog(event=conflict or pull, status=success/failure).
```

**Conflict strategy: last-write-wins by `server_modified` vs `lastSyncAt` timestamp.
Full-DB snapshot (not delta). User is prompted only when both sides have diverged.**

#### Error Handling

| Condition | SDK Exception | App Behavior | Sentry Log |
|---|---|---|---|
| Token revoked | `DbxException` with `AuthError.INVALID_ACCESS_TOKEN` | Show re-auth dialog in S17; clear EncryptedSharedPreferences token | Yes — warning |
| Storage quota exceeded | `DbxException` with `UploadSessionFinishError.TOO_MANY_WRITE_OPERATIONS` or `SpaceError` | Show user notice: "Dropbox storage is full"; do not retry automatically | Yes — warning |
| Network unavailable | `IOException` | Suppress silently in background; show last sync time; WorkManager retries with exponential backoff | No (expected) |
| Rate limited | `RateLimitException` | Respect `retryAfter` header; WorkManager back-off | Yes — info |
| Server error | `DbxException` (5xx wrapping) | Retry up to 3 times with exponential backoff (1 s, 2 s, 4 s) | Yes — error |
| File conflict on upload | `UploadError.PATH(UploadPathError.CONFLICT)` | Should not occur with `OVERWRITE` mode; log as unexpected | Yes — error |

**Rate-limit posture:** Dropbox enforces ~25 req/sec per user. Our sync issues ≤ 5 SDK
calls per full sync cycle (metadata check + upload/download + list + delete). Well within limits.

#### DropboxSyncRepository (interface contract)

```kotlin
interface DropboxSyncRepository {
    suspend fun isConnected(): Boolean
    suspend fun getConnectedAccountEmail(): String?
    suspend fun startOAuth(activity: Activity)  // launches DbxOAuth2PKCE flow
    suspend fun handleOAuthResult(): Boolean    // called after AuthActivity returns
    suspend fun disconnect()
    suspend fun push(): SyncResult
    suspend fun pull(): SyncResult
    suspend fun listBackups(): List<DropboxBackupMeta>
    suspend fun deleteBackup(path: String): Boolean
}

data class SyncResult(
    val success: Boolean,
    val conflictDetected: Boolean,
    val errorMessage: String?,
    val performedAt: Long,
)

data class DropboxBackupMeta(
    val path: String,
    val serverModifiedMs: Long,
    val sizeBytes: Long,
)
```

---

### 9.1.2 — Google Drive Snapshot Sync

#### Auth

- Flow: **Google Sign-In** (`GoogleSignInOptions` with `requestScopes(Drive.SCOPE_APPFOLDER)`).
  Uses `drive.appdata` scope — files are stored in the user's **appDataFolder** (hidden from
  Drive UI, accessible only by our app). This is the same pattern as the original Monefy APK
  (confirmed: `DRIVE_OPEN` intent filter + `play-services-auth` detected).
- Entry point: S17 Cloud Sync screen → "Connect Google Drive" button.
- The `GoogleSignInAccount` returned contains an `idToken` and `serverAuthCode`. We use the
  Drive API client initialized with `GoogleAccountCredential.usingOAuth2(context, scopes)`.
- The Google Sign-In token is **not** stored directly; `GoogleSignInAccount` is refreshed via
  `GoogleSignIn.getLastSignedInAccount(context)` on each app start. The account email is
  stored in EncryptedSharedPreferences for UI display only.
- Disconnect: `GoogleSignIn.getClient(context, gso).signOut()` + clear EncryptedSharedPrefs.

#### Endpoints Used (via Drive REST v3 client)

| Method | Endpoint | Auth Scope | Screen | Description |
|---|---|---|---|---|
| GET | `/drive/v3/files?spaces=appDataFolder&fields=files(id,name,modifiedTime,size)` | drive.appdata | S17, WorkManager | List backup files in appDataFolder |
| POST | `/upload/drive/v3/files?uploadType=multipart` | drive.appdata | S17, S18, WorkManager | Upload `monefy_backup.db` (multipart: metadata JSON + binary file) |
| GET | `/drive/v3/files/{fileId}?alt=media` | drive.appdata | S17, S18 | Download backup file content |
| PATCH | `/drive/v3/files/{fileId}` (update metadata) | drive.appdata | WorkManager | Update `modifiedTime` after overwrite (if using resumable update) |
| DELETE | `/drive/v3/files/{fileId}` | drive.appdata | S18 | Remove old backup snapshot |

**Note:** Files are uploaded with `name = "monefy_backup.db"`, stored in `appDataFolder` (not
user-visible). The `appDataFolder` is a hidden, app-scoped space; the user never sees these
files in their Drive UI. Quota is counted against the user's Drive storage.

#### Sync Flow (identical pattern to Dropbox)

```
1. WorkManager SyncWorker fires.
2. GDriveSyncRepository.push():
   a. List files in appDataFolder to get existing backup metadata (modifiedTime).
   b. Export Room DB file to temp path.
   c. If backup file exists: upload via multipart update (PATCH with uploadType=multipart).
      If not: create new file (POST).
   d. Delete old snapshots beyond last N=3.
   e. Record SyncLog, update AppSettings.lastSyncAt.
3. Pull + conflict resolution: identical logic to Dropbox (compare modifiedTime vs lastSyncAt).
```

#### Error Handling

| Condition | HTTP Status / Exception | App Behavior | Sentry Log |
|---|---|---|---|
| Token expired | 401 Unauthorized | Re-trigger GoogleSignIn silently; if fails, show re-auth dialog in S17 | Yes — warning |
| Insufficient scope | 403 Forbidden | Show "Grant Drive access" prompt; direct user to S17 | Yes — warning |
| Quota exceeded | 403 `storageQuotaExceeded` | User notice: "Google Drive storage is full"; no auto-retry | Yes — warning |
| File not found | 404 | Treat as fresh upload (no prior backup); proceed with push | No |
| Rate limited | 429 / 403 `rateLimitExceeded` | Exponential backoff up to 3 retries | Yes — info |
| Network error | IOException | Suppress in background; WorkManager retries | No |
| Server error | 5xx | Retry 3 times with exponential backoff | Yes — error |

**Quota posture:** Drive API allows 1 billion requests/day per project, per-user daily quota
is generous for our pattern (≤ 10 requests per sync cycle). Use batch requests if adding
metadata + content in one call (reduces quota usage).

#### GDriveSyncRepository (interface contract)

```kotlin
interface GDriveSyncRepository {
    suspend fun isConnected(): Boolean
    suspend fun getConnectedAccountEmail(): String?
    suspend fun connect(activity: Activity)  // triggers GoogleSignIn
    suspend fun disconnect()
    suspend fun push(): SyncResult
    suspend fun pull(): SyncResult
    suspend fun listBackups(): List<DriveBackupMeta>
    suspend fun deleteBackup(fileId: String): Boolean
}

data class DriveBackupMeta(
    val fileId: String,
    val name: String,
    val modifiedTimeMs: Long,
    val sizeBytes: Long,
)
```

---

### 9.1.3 — Firebase Remote Config

**Endpoint (SDK-managed):** `firebaseremoteconfig.googleapis.com` — fully managed by the
Firebase SDK; no direct HTTP calls from application code.

#### Fetch strategy

- **Fetch interval:** 12 hours in production (default interval), 0 s in debug builds
  (`FirebaseRemoteConfigSettings.Builder().setMinimumFetchIntervalInSeconds(0)`).
- **On fetch failure:** fall back to cached values (Firebase SDK default behavior). If no
  cache exists, fall back to in-app defaults defined at initialization.
- **Activation:** `fetchAndActivate()` called at app startup in the Application class
  (before any screen renders). Values are immediately available after activation.
- Remote Config is initialized in the Hilt `SingletonComponent` as a singleton.

#### Parameters defined

| Parameter Key | Type | Default (in-app) | Purpose |
|---|---|---|---|
| `feature_recurring_templates_enabled` | Boolean | `true` | Kill-switch for the recurring transaction WorkManager job. If `false`, skip scheduling `RecurringTemplateWorker`. |
| `feature_budget_mode_enabled` | Boolean | `true` | Kill-switch for budget tracking. If `false`, hide budget progress from dashboard and suppress BudgetEvaluator use-case. |
| `gdrive_sync_enabled` | Boolean | `true` | Kill-switch for Google Drive sync. If `false`, hide GDrive option in S17, disable GDriveWorker. Use if Drive API quota is breached project-wide. |
| `dropbox_sync_enabled` | Boolean | `true` | Kill-switch for Dropbox sync. |
| `min_supported_version_code` | Long | `1` | Minimum supported versionCode. If installed versionCode < this value, show a soft update dialog on app start. Does not force-close. |
| `aesthetic_sound_pack` | String | `"default"` | Sound pack identifier for gamification audio effects. Values: `"default"`, `"coins"`, `"chiptune"`. Future use — currently only `"default"` is bundled. |

#### Client usage pattern

```kotlin
// RemoteConfigRepository (Hilt singleton)
class RemoteConfigRepository @Inject constructor(
    private val remoteConfig: FirebaseRemoteConfig,
) {
    suspend fun fetchAndActivate() {
        remoteConfig.fetchAndActivate().await()
    }

    val isRecurringEnabled: Boolean
        get() = remoteConfig.getBoolean("feature_recurring_templates_enabled")

    val isBudgetModeEnabled: Boolean
        get() = remoteConfig.getBoolean("feature_budget_mode_enabled")

    val isGDriveSyncEnabled: Boolean
        get() = remoteConfig.getBoolean("gdrive_sync_enabled")

    val isDropboxSyncEnabled: Boolean
        get() = remoteConfig.getBoolean("dropbox_sync_enabled")

    val minSupportedVersionCode: Long
        get() = remoteConfig.getLong("min_supported_version_code")

    val soundPack: String
        get() = remoteConfig.getString("aesthetic_sound_pack")
}
```

---

### 9.1.4 — Sentry

**DSN (original APK):**
`https://8798feb6bdfcd4ab961ecd986fc80d90@o155653.ingest.us.sentry.io/4508238736916480`

> **IMPORTANT:** The DSN above is the **original Monefy app's** Sentry project. For our
> re-implementation, register a new Sentry project and replace the DSN with our own.
> Placeholder: `YOUR_SENTRY_DSN_HERE` — must be replaced before first beta build.

#### Initialization

Sentry is initialized via the `AndroidManifest.xml` meta-data key `io.sentry.dsn` (Sentry
Android SDK auto-init on app start via `ContentProvider`). No explicit `Sentry.init()` call
needed.

```xml
<!-- AndroidManifest.xml -->
<meta-data android:name="io.sentry.dsn"
           android:value="YOUR_SENTRY_DSN_HERE" />
<meta-data android:name="io.sentry.sample-rate" android:value="1.0" />
<meta-data android:name="io.sentry.traces-sample-rate" android:value="0.2" />
<meta-data android:name="io.sentry.send-default-pii" android:value="false" />
```

#### Events captured

| Event type | Captured automatically? | Manual breadcrumb? | Notes |
|---|---|---|---|
| Uncaught exceptions | Yes (SDK) | — | All unhandled JVM exceptions |
| ANRs | Yes (SDK) | — | Detected via main-thread watchdog |
| Dropbox sync failure | No | Yes | Breadcrumb added in `DropboxSyncRepository` on every `status=failure` SyncLog entry |
| GDrive sync failure | No | Yes | Same pattern as Dropbox |
| Remote Config fetch failure | No | Yes | Only logged at warning level |
| WorkManager job failure | No | Yes | Added in `SyncWorker.onFailure()` |
| DB migration failure | No | Yes | Added in `MoneyDatabase` `RoomDatabase.Callback.onCreate/onDestructiveMigration` |
| Biometric auth error | No | Yes | Non-fatal; informational only |

#### User consent

- Default state: **ON** for crash-only events (uncaught exceptions + ANRs).
- Settings → Privacy section (sub-item in S14 Settings Root): toggle "Send crash reports".
- When user opts out: call `Sentry.close()` and set `io.sentry.enable = false` (restart
  required, or store the flag in DataStore and conditionally initialize on next cold start).
- PII: `send-default-pii = false`; no email/username attached to events. Device model and
  OS version are attached (non-personal).

---

## 9.2 — Obligatory Third-Party SDKs

### Complete SDK Manifest

| ID | Artifact | Version | Required | Purpose |
|---|---|---|---|---|
| dropbox-sdk | `com.dropbox.core:dropbox-core-sdk` | `7.0.0` | yes (if Dropbox sync enabled) | Dropbox OAuth 2 PKCE + file upload/download (S17, S18) |
| google-api-client-android | `com.google.api-client:google-api-client-android` | `2.6.0` | yes (if GDrive sync enabled) | Google HTTP client for Drive REST v3 |
| google-api-services-drive | `com.google.apis:google-api-services-drive` | `v3-rev20240914-2.0.0` | yes (if GDrive sync enabled) | Drive REST v3 generated client (files CRUD) |
| play-services-auth | `com.google.android.gms:play-services-auth` | `21.2.0` | yes | Google Sign-In for Drive scope; confirmed in APK |
| firebase-bom | `com.google.firebase:firebase-bom` | `33.5.1` | yes | Firebase BoM — version-aligns all Firebase dependencies |
| firebase-config-ktx | `com.google.firebase:firebase-config-ktx` | *(BoM-managed)* | yes | Remote Config feature flags |
| firebase-analytics-ktx | `com.google.firebase:firebase-analytics-ktx` | *(BoM-managed)* | optional | Product analytics; confirmed in APK; default disabled until user consents |
| firebase-crashlytics-ktx | `com.google.firebase:firebase-crashlytics-ktx` | *(BoM-managed)* | no | Secondary crash reporter; optional alongside Sentry. Recommend SKIP — Sentry covers crash reporting and avoids dual-SDK overhead |
| sentry-android | `io.sentry:sentry-android` | `7.18.0` | yes | Crash reporting, ANR detection, manual breadcrumbs (confirmed in APK) |
| biometric | `androidx.biometric:biometric` | `1.2.0-alpha07` | yes | Biometric prompt (S16); confirmed `USE_BIOMETRIC` in APK manifest |
| security-crypto | `androidx.security:security-crypto` | `1.1.0-alpha07` | yes | EncryptedSharedPreferences — stores Dropbox refresh token, GDrive email |
| datastore-preferences | `androidx.datastore:datastore-preferences` | `1.1.1` | yes | AppSettings (language, theme, sync flags) — confirmed in APK |
| room-runtime | `androidx.room:room-runtime` | `2.6.1` | yes | Room ORM core — all 9 entities; confirmed in APK |
| room-ktx | `androidx.room:room-ktx` | `2.6.1` | yes | Coroutine/Flow extensions for Room DAOs |
| room-paging | `androidx.room:room-paging` | `2.6.1` | yes | Paging 3 integration for TransactionDao (S12 list) |
| room-compiler | `androidx.room:room-compiler` | `2.6.1` | yes | KSP annotation processor — `ksp("androidx.room:room-compiler:2.6.1")` |
| navigation-compose | `androidx.navigation:navigation-compose` | `2.8.4` | yes | Compose Navigation for single-activity NavHost |
| hilt-android | `com.google.dagger:hilt-android` | `2.52` | yes | DI framework (replaces original Dagger + AndroidAnnotations) |
| hilt-compiler | `com.google.dagger:hilt-android-compiler` | `2.52` | yes | KSP annotation processor — `ksp("com.google.dagger:hilt-android-compiler:2.52")` |
| hilt-navigation-compose | `androidx.hilt:hilt-navigation-compose` | `1.2.0` | yes | `hiltViewModel()` factory for Compose screens |
| hilt-work | `androidx.hilt:hilt-work` | `1.2.0` | yes | HiltWorkerFactory for WorkManager + Hilt integration |
| work-runtime-ktx | `androidx.work:work-runtime-ktx` | `2.10.0` | yes | WorkManager — RecurringTemplateWorker + SyncWorker (periodic, constraints-based) |
| kotlinx-serialization-json | `org.jetbrains.kotlinx:kotlinx-serialization-json` | `1.7.3` | yes | JSON serialization for Retrofit converter + metadata JSON in sync snapshots |
| retrofit | `com.squareup.retrofit2:retrofit` | `2.11.0` | yes | HTTP client wrapper — used for any future REST calls; OkHttp transport |
| retrofit-kotlinx-serialization | `com.jakewharton.retrofit:retrofit2-kotlinx-serialization-converter` | `1.0.0` | yes | Retrofit converter for kotlinx.serialization |
| okhttp | `com.squareup.okhttp3:okhttp` | `4.12.0` | yes | HTTP client used by Retrofit + bundled with GDrive SDK; confirmed in APK |
| okhttp-logging-interceptor | `com.squareup.okhttp3:logging-interceptor` | `4.12.0` | debug-only | HTTP request/response logging; `debugImplementation` only |
| kotlinx-coroutines-android | `org.jetbrains.kotlinx:kotlinx-coroutines-android` | `1.9.0` | yes | Coroutines Android dispatcher; confirmed in APK |
| kotlinx-coroutines-play-services | `org.jetbrains.kotlinx:kotlinx-coroutines-play-services` | `1.9.0` | yes | `await()` for Google Tasks API (GDrive, Sign-In) |
| coil-compose | `io.coil-kt.coil3:coil-compose` | `3.0.0-rc02` | optional | Image loading for custom category icons (S09/S10/S21/S22). Needed only if icons are raster assets rather than bundled vector drawables. Recommend: use bundled SVG/vector drawables instead and skip Coil. Mark as `optional`. |
| splashscreen | `androidx.core:core-splashscreen` | `1.0.1` | yes | SplashScreen API for S00; mandatory since minSdk=31 has native splash support |
| paging-runtime | `androidx.paging:paging-runtime-ktx` | `3.3.2` | yes | Paging 3 runtime for TransactionDao paged queries (S12) |
| paging-compose | `androidx.paging:paging-compose` | `3.3.2` | yes | Paging 3 Compose integration (`collectAsLazyPagingItems`) for S12 |

**Chart library decision: no third-party chart SDK.**
The donut chart (S01, S05) is implemented with **pure Compose Canvas** (`DrawScope.drawArc`
with animated sweep angles via `animateFloatAsState`). Rationale: MPAndroidChart is a
View-based library incompatible with Compose without interop overhead; Vico requires a
separate dependency with its own animation API; the Monefy donut is simple enough (arcs +
labels) to implement with ~200 lines of Canvas code, giving full control over the gamification
fill animation. No third-party chart SDK is added to the dependency list.

### Gradle Plugin Requirements

```kotlin
// build.gradle.kts (project-level) — plugins to apply
plugins {
    id("com.android.application") version "8.7.0" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21" apply false
    id("com.google.devtools.ksp") version "2.0.21-1.0.28" apply false
    id("com.google.dagger.hilt.android") version "2.52" apply false
    id("com.google.gms.google-services") version "4.4.2" apply false  // Firebase config
    // Optional (if Crashlytics added later):
    // id("com.google.firebase.crashlytics") version "3.0.2" apply false
    id("com.google.android.gms.oss-licenses-plugin") version "0.10.6" apply false  // OSS licenses
}
```

**Note:** `kotlin.plugin.compose` is required separately from `kotlin.android` in Kotlin 2.0+
(Compose compiler plugin decoupled from Kotlin compiler).

### google-services.json

Place `google-services.json` (downloaded from Firebase Console for our project) in
`app/`. This file is required for Firebase Remote Config + Analytics initialization.
Do **not** commit this file to public repositories; add to `.gitignore` and supply via
CI secret injection.

---

## 9.3 — Web Views / External Links

### About / Help (S20)

**Rendering strategy: bundled offline HTML.**

The About/Help screen loads a static HTML file bundled in `assets/help/index.html`.
A `WebView` with `webViewClient.shouldOverrideUrlLoading = false` and
`settings.allowFileAccessFromFileURLs = true` loads the asset via `file:///android_asset/`.

Rationale: offline-first app; user may open S20 without network. Bundled HTML is
versioned with the APK. No external HTTP fetch needed for help content.

Alternative (if help content must be updated independently of APK releases): host a static
page and load via `CustomTabsIntent` — but this breaks offline access. Prefer bundled for MVP.

```kotlin
// S20 About/Help composable
AndroidView(factory = { ctx ->
    WebView(ctx).apply {
        loadUrl("file:///android_asset/help/index.html")
        settings.javaScriptEnabled = false    // static HTML, no JS needed
        settings.allowFileAccessFromFileURLs = true
        webViewClient = WebViewClient()
    }
})
```

### Open-Source Licenses (sub-screen of S20)

Use the **Google Play OSS Licenses plugin** (`com.google.android.gms.oss-licenses-plugin`)
to auto-generate the license list at build time.

```kotlin
// In S20 sub-screen navigation:
startActivity(OssLicensesMenuActivity.Intent(context).apply {
    OssLicensesMenuActivity.setActivityTitle("Open-source licenses")
})
```

Apply the plugin in `app/build.gradle.kts`:
```kotlin
plugins { id("com.google.android.gms.oss-licenses-plugin") }
```

This auto-generates `res/raw/third_party_license_metadata` + `third_party_licenses`
at build time. No manual maintenance needed.

### Privacy Policy / Terms of Service

If a Privacy Policy URL is required (Play Store listing requires one for apps with
network access): use `CustomTabsIntent` for the external URL. Do NOT use WebView for
legal-document external links (CustomTabs inherits the user's browser history/cookies
and is considered more trustworthy per Play policy).

```kotlin
CustomTabsIntent.Builder().build()
    .launchUrl(context, Uri.parse("https://YOUR_PRIVACY_POLICY_URL"))
```

No privacy policy URL is defined yet — add to "Open questions" list.

### External links in app surface

| Location | Target | Rendering |
|---|---|---|
| S20 About | Bundled `assets/help/index.html` | WebView (offline asset) |
| S20 About → Licenses | Auto-generated OSS list | `OssLicensesMenuActivity` |
| S20 About → Privacy Policy | External URL (TBD) | `CustomTabsIntent` |
| S20 About → Rate the app | Play Store listing | `Intent(ACTION_VIEW, market://details?id=...)` |
| S17 Cloud Sync → Dropbox auth | Dropbox OAuth browser page | Dropbox SDK launches browser automatically |
| S17 Cloud Sync → Google auth | Google Sign-In bottom sheet | Google Sign-In SDK — system UI |

No in-app `WebView` that loads arbitrary external URLs (avoids WebView security surface).

---

## 9.4 — Permissions vs SDK Mapping

### Final permissions manifest (re-implementation)

```xml
<!-- Required -->
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.USE_BIOMETRIC" />
<uses-permission android:name="android.permission.WAKE_LOCK" />

<!-- REMOVED vs original APK — see rationale below -->
<!--
<uses-permission android:name="com.android.vending.BILLING" />
<uses-permission android:name="android.permission.USE_FINGERPRINT" />
<uses-permission android:name="android.permission.READ_PHONE_STATE" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="com.google.android.finsky.permission.BIND_GET_INSTALL_REFERRER_SERVICE" />
<uses-permission android:name="com.google.android.gms.permission.AD_ID" />
-->
```

### Permissions cross-reference table

| Permission | Status | SDK / Feature | Screen evidence | Rationale |
|---|---|---|---|---|
| `INTERNET` | **KEEP** | Dropbox SDK, GDrive REST v3, Firebase Remote Config, Sentry | All app — sync + feature flags + crash reporting | Mandatory for all network SDKs |
| `ACCESS_NETWORK_STATE` | **KEEP** | WorkManager network constraint (`requiresUnmeteredNetwork`), Dropbox SDK, Firebase | S17, WorkManager | WorkManager checks `NetworkType.UNMETERED` before sync; SDK pre-check |
| `USE_BIOMETRIC` | **KEEP** | `androidx.biometric:biometric` | S16 Biometric Lock Setup | Required for BiometricPrompt on Android 9+; confirmed in APK |
| `WAKE_LOCK` | **KEEP** | WorkManager — holds wakelock during `SyncWorker` execution | Background WorkManager | WorkManager uses `WAKE_LOCK` internally via `SystemAlarmService`; without it, periodic sync may miss triggers on DOZE devices |
| `USE_FINGERPRINT` | **REMOVED** | Deprecated since API 28; superseded by `USE_BIOMETRIC` | — | Original APK has both; we declare only `USE_BIOMETRIC`. biometric library handles backwards compat. |
| `POST_NOTIFICATIONS` | **REMOVED** | Not needed — qD3 = no push notifications | — | No notification channels in re-impl. RecurringTemplate creates transactions silently. Budget alerts are in-app UI badges only. |
| `RECEIVE_BOOT_COMPLETED` | **REMOVED** | WorkManager uses JobScheduler (API 23+) which survives reboot natively via `BOOT_COMPLETED` within its own service — no app-level declaration needed for API 31+ | — | minSdk=31; JobScheduler handles reboot persistence. Explicit `RECEIVE_BOOT_COMPLETED` is legacy pattern from pre-API 23 WorkManager. |
| `FOREGROUND_SERVICE` | **REMOVED** | No long-running foreground sync service; WorkManager periodic job is sufficient | — | Original APK has legacy `SyncServicePreSDK26` foreground service (pre-Android 8 workaround). minSdk=31 — not needed. |
| `READ_PHONE_STATE` | **REMOVED** | Original used for telephony-aware analytics (device ID for analytics fingerprinting) | — | No analytics fingerprinting in re-impl. Firebase Analytics uses installation ID, not phone state. |
| `com.android.vending.BILLING` | **REMOVED** | Google Play Billing — original has `BuyMonefyActivity_` | — | No IAP in re-impl; all features unlocked. |
| `com.google.android.gms.permission.AD_ID` | **REMOVED** | AdMob / ad attribution | — | No ads in re-impl. Play policy: declare only if actually used. |
| `BIND_GET_INSTALL_REFERRER_SERVICE` | **REMOVED** | Install attribution (Play referrer API for ad campaigns) | — | No advertising/attribution in re-impl. |
| `RECORD_AUDIO` | **NOT ADDED** | Voice search (S08) uses `RecognizerIntent` (system speech activity) | S08 | `RecognizerIntent` starts the system speech recognizer activity — **the app itself does not capture audio**; `RECORD_AUDIO` is declared by the system speech app, not ours. |

### Biometric permission note

On Android 12+ (minSdk=31), `USE_BIOMETRIC` also covers fingerprint. The deprecated
`USE_FINGERPRINT` is only needed for compatibility down to API 23, which we have removed.
`BiometricManager.canAuthenticate(BIOMETRIC_STRONG)` is the correct runtime check.

---

## 9.5 — Error Handling Contract

### Sync error taxonomy (tabular)

| Error Source | Error Condition | HTTP / SDK Code | App Behavior | User-Visible Message | Sentry |
|---|---|---|---|---|---|
| **Dropbox** | Token revoked | `AuthError.INVALID_ACCESS_TOKEN` | Show re-auth dialog in S17; clear stored token | "Dropbox connection lost. Please reconnect." | warn |
| **Dropbox** | Quota exceeded | `SpaceError` / quota variant | Disable auto-sync; show banner in S17 | "Dropbox storage is full." | warn |
| **Dropbox** | Rate limited | `RateLimitException` | Respect `retryAfter`; backoff silently | None (background) | info |
| **Dropbox** | Network unavailable | `IOException` | Suppress; WorkManager retries on connectivity restored | "Last sync: [timestamp]" (S17 status) | no |
| **Dropbox** | Server error 5xx | `DbxException` wrapping 5xx | Retry 3× exponential (1s, 2s, 4s); fail if exhausted | "Sync failed. Try again later." (S17) | error |
| **Dropbox** | Conflict (remote newer) | — | Show conflict dialog in S17 | "Remote backup is newer. Overwrite local data? [Keep Remote] [Keep Local]" | info |
| **GDrive** | Token expired | 401 Unauthorized | Silent re-auth via `GoogleSignIn.silentSignIn()`; if fails → dialog | "Google Drive connection lost. Please reconnect." | warn |
| **GDrive** | Scope insufficient | 403 Forbidden | Show "Grant Drive access" button in S17 | "Drive access denied. Grant permission." | warn |
| **GDrive** | Quota exceeded | 403 `storageQuotaExceeded` | Disable auto-sync; banner in S17 | "Google Drive storage is full." | warn |
| **GDrive** | File not found | 404 | Treat as first sync; proceed with push | None | no |
| **GDrive** | Rate limited | 429 / 403 `rateLimitExceeded` | Exponential backoff 3× | None (background) | info |
| **GDrive** | Network error | `IOException` | WorkManager retries | "Last sync: [timestamp]" | no |
| **GDrive** | Server error | 5xx | Retry 3× exponential | "Sync failed. Try again later." | error |
| **GDrive** | Conflict (remote newer) | — | Same as Dropbox conflict dialog | Same as Dropbox conflict message | info |
| **Firebase RC** | Fetch timeout | `FirebaseRemoteConfigFetchThrottledException` | Use cached values; no user impact | None | info |
| **Firebase RC** | No network | `IOException` | Use cached / in-app defaults | None | no |
| **Sentry** | DSN invalid | `SentryInitException` | Swallow silently; app continues without crash reporting | None | n/a |
| **Room** | DB locked (WAL timeout) | `SQLiteException` | Log + surface to ViewModel as `DbError` state | "Data error. Restart the app." | error |
| **Room** | Migration failure | `RoomMigrationException` | Destructive migration on dev; hard crash + Sentry event on prod | "App data error. Contact support." | fatal |
| **WorkManager** | Worker failure | `ListenableWorker.Result.failure()` | `SyncWorker` records failure in SyncLog; max 3 retries via `Result.retry()` | Reflected as failed sync timestamp in S17 | warn |
| **RecognizerIntent** | No speech app | `ActivityNotFoundException` | Show snackbar; fallback to text-only search | "Voice search unavailable on this device." | no |
| **BiometricPrompt** | HW unavailable | `BIOMETRIC_ERROR_HW_NOT_PRESENT` | Disable biometric option in S16; show PIN fallback | "Biometric hardware not available." | no |
| **BiometricPrompt** | Auth failed | `ERROR_LOCKOUT` | Show PIN fallback; log breadcrumb | "Too many attempts. Use your PIN." | info (breadcrumb only) |

### WorkManager retry policy

```kotlin
// SyncWorker retry specification (inside Worker class)
override suspend fun doWork(): Result {
    return try {
        syncRepository.push()
        Result.success()
    } catch (e: RateLimitException) {
        // Respect retryAfter
        Result.retry()
    } catch (e: IOException) {
        if (runAttemptCount < 3) Result.retry() else {
            Sentry.captureException(e)
            Result.failure()
        }
    } catch (e: Exception) {
        Sentry.captureException(e)
        Result.failure()
    }
}
```

Backoff policy: `setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)`
(WorkManager default minimum is 10 s; exponential capped at 5 min).

### SyncLog as audit trail

Every sync attempt (success or failure) is recorded in the `SyncLog` Room table:
- Success: `status=success`, `payloadHash=SHA256(dbFile)`, `performedAt=now`.
- Failure: `status=failure`, `errorMessage=exception.message`.
- Conflict: `event=conflict`, `status=partial`, `errorMessage=resolution`.

The SyncLog is pruned to the last 100 rows per target (enforced in `SyncLogDao.pruneOldEntries()`
called after every insert in `SyncLogRepository`).

---

## 9.6 — Retrofit + OkHttp Configuration (Q-E2: Retrofit)

While Dropbox and GDrive use their own SDK/REST clients, the Retrofit + OkHttp stack
(selected in qE2) serves as the generic HTTP infrastructure. This is relevant for:
- Any future lightweight REST calls (e.g., a currency rate fetch endpoint if we add
  live exchange rates — not in MVP but anticipated).
- The GDrive REST client can optionally be backed by the same OkHttpClient instance.

```kotlin
// Hilt Module (SingletonComponent)
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(
        @ApplicationContext context: Context,
    ): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)  // longer for DB file uploads
        .addInterceptor(
            if (BuildConfig.DEBUG)
                HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }
            else
                Interceptor { it.proceed(it.request()) }  // no-op in release
        )
        .build()

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient, json: Json): Retrofit =
        Retrofit.Builder()
            .baseUrl("https://placeholder.mymoney.app/")  // replace with real base URL if REST added
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
}
```

The Dropbox SDK client is initialized separately using its own `OkHttpRequestor` backed by
the same `OkHttpClient` singleton to share the connection pool:

```kotlin
@Provides
@Singleton
fun provideDropboxClient(
    okHttpClient: OkHttpClient,
    @DropboxAppKey appKey: String,
    encryptedPrefs: EncryptedSharedPreferences,
): DbxClientV2? {
    val token = encryptedPrefs.getString("dropbox_token", null) ?: return null
    val requestor = OkHttp3Requestor(okHttpClient)
    val config = DbxRequestConfig.newBuilder("MyMoney/1.0")
        .withHttpRequestor(requestor)
        .build()
    return DbxClientV2(config, DbxCredential.Reader.readFully(token))
}
```

---

## 9.7 — Manifest Additions Required

```xml
<!-- AndroidManifest.xml additions -->

<!-- Permissions (final set — see §9.4) -->
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.USE_BIOMETRIC" />
<uses-permission android:name="android.permission.WAKE_LOCK" />

<!-- Dropbox OAuth callback activity (from Dropbox SDK) -->
<activity
    android:name="com.dropbox.core.android.AuthActivity"
    android:exported="true"
    android:launchMode="singleTask">
    <intent-filter>
        <data android:scheme="db-wxbzuly0x7v23t8" />  <!-- replace with production App Key -->
        <action android:name="android.intent.action.VIEW" />
        <category android:name="android.intent.category.BROWSABLE" />
        <category android:name="android.intent.category.DEFAULT" />
    </intent-filter>
</activity>

<!-- Google Drive OPEN intent (S18 Backup & Restore — open backup from Drive) -->
<intent-filter>  <!-- add to MainActivity -->
    <action android:name="com.google.android.apps.drive.DRIVE_OPEN" />
    <data android:mimeType="application/octet-stream" />
</intent-filter>

<!-- App Shortcuts deep-links (add NavDeepLink to MainActivity intent-filter) -->
<intent-filter>
    <action android:name="android.intent.action.VIEW" />
    <category android:name="android.intent.category.DEFAULT" />
    <data android:scheme="monefy" android:host="add-expense" />
</intent-filter>
<intent-filter>
    <action android:name="android.intent.action.VIEW" />
    <category android:name="android.intent.category.DEFAULT" />
    <data android:scheme="monefy" android:host="add-income" />
</intent-filter>
<intent-filter>
    <action android:name="android.intent.action.VIEW" />
    <category android:name="android.intent.category.DEFAULT" />
    <data android:scheme="monefy" android:host="add-transfer" />
</intent-filter>

<!-- Sentry meta-data (auto-init) -->
<meta-data android:name="io.sentry.dsn" android:value="YOUR_SENTRY_DSN_HERE" />
<meta-data android:name="io.sentry.sample-rate" android:value="1.0" />
<meta-data android:name="io.sentry.traces-sample-rate" android:value="0.2" />
<meta-data android:name="io.sentry.send-default-pii" android:value="false" />

<!-- Firebase (auto-added by google-services plugin via google-services.json) -->
<!-- WorkManager (auto-added by work-runtime-ktx via Startup library) -->

<!-- FileProvider (for sharing exported CSV, future feature) -->
<provider
    android:name="androidx.core.content.FileProvider"
    android:authorities="${applicationId}.provider"
    android:exported="false"
    android:grantUriPermissions="true">
    <meta-data
        android:name="android.support.FILE_PROVIDER_PATHS"
        android:resource="@xml/file_provider_paths" />
</provider>
```

---

## 9.8 — Open Questions for Product / Engineering

| ID | Question | Impact |
|---|---|---|
| OQ-1 | Sentry DSN for re-impl project? Replace `YOUR_SENTRY_DSN_HERE` before first beta build. | Crash reporting will be silently disabled until replaced. |
| OQ-2 | Dropbox production App Key? `wxbzuly0x7v23t8` is the original Monefy key — cannot use for re-impl. Register new Dropbox app at dropbox.com/developers. | S17 OAuth flow blocked without valid App Key. |
| OQ-3 | Google Cloud project for Drive API? OAuth consent screen + Drive API must be enabled for re-impl app's SHA-1 fingerprint. | S17 GDrive connect will fail without valid project setup. |
| OQ-4 | Privacy Policy URL? Required for Play Store submission (apps with `INTERNET` permission). | Play Store listing rejected without it. |
| OQ-5 | `min_supported_version_code` Remote Config threshold — what versionCode triggers soft-update prompt? | Logic in RemoteConfigRepository is implemented; just needs a value. |
| OQ-6 | Currency exchange rate source — any live rate API (e.g., fixer.io, exchangerate.host) or manual-only? | Not in MVP scope (qD2=local_first); but CurrencyRate entity + S27 form are already modeled. If live rates added in v1.1, Retrofit base URL and a rate API endpoint need definition. |
| OQ-7 | Auto-sync schedule — 6 hours interval appropriate, or user-configurable (hourly/daily/manual-only)? | WorkManager `PeriodicWorkRequest` interval set in SyncScheduler; easy to expose in S17. |
| OQ-8 | Backup rotation count — keep last N=3 snapshots on cloud, or user-configurable? | Currently hardcoded N=3 in sync repositories. |
| OQ-9 | `google-services.json` CI delivery — how is it injected in build pipeline? | Must not be committed to VCS; needs CI secret + build script injection. |
| OQ-10 | firebase-crashlytics — add as secondary crash reporter alongside Sentry, or skip? | Marked as `no` (optional) above; product decision needed. Dual crash reporters increase overhead. |

---

## Summary

- **Backend type:** none (local-first, no proprietary REST)
- **External integrations:** 4 (Dropbox sync, GDrive sync, Firebase Remote Config, Sentry)
- **Auth methods:** Dropbox OAuth 2 PKCE, Google Sign-In (drive.appdata scope), no app-side user account
- **Sync model:** full DB snapshot, last-write-wins, WorkManager periodic (6 h) + manual trigger
- **Permissions (final):** 4 (INTERNET, ACCESS_NETWORK_STATE, USE_BIOMETRIC, WAKE_LOCK)
- **Permissions removed:** 8 (BILLING, USE_FINGERPRINT, READ_PHONE_STATE, POST_NOTIFICATIONS, RECEIVE_BOOT_COMPLETED, FOREGROUND_SERVICE, AD_ID, BIND_GET_INSTALL_REFERRER_SERVICE)
- **Total SDK dependencies (required):** 26 (see §9.2)
- **Chart implementation:** pure Compose Canvas (no third-party chart library)
