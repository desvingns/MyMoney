# PHASE 05 — DataStore + secure storage (`:core:datastore`)

## Goal

Implement `AppSettings` persistence (DataStore Preferences) and `SecureSettings` (EncryptedSharedPreferences) per TDD §7.3 + §8.3. After this phase a singleton `AppSettingsRepository` exposes `Flow<AppSettings>` + `suspend fun update(transform: (AppSettings) -> AppSettings)`, and a singleton `SecureStorage` reads/writes the Dropbox refresh token, the GDrive email, and the PIN hash via the EncryptedSharedPreferences API. Round-trip tests cover both.

## TDD anchors

- §7.3 AppSettings (DataStore Preferences) — lines 1662–1690
- §8.3 Storage layout on disk — lines 2042–2057
- §2.4 Persistence + sync (where DataStore fits in the layer cake) — lines 229–245
- §9.4 Permission-vs-SDK mapping (note `USE_BIOMETRIC` will be added in PHASE_14) — lines 2234–2242

## Prerequisites

- PHASE_02 — done
- PHASE_04 — done (so Hilt graph is wired with `@SingletonComponent` and the dispatcher qualifiers exist)

## Deliverables (in `:core:datastore`)

- `core/datastore/build.gradle.kts` — `androidx.datastore:datastore-preferences` + `androidx.security:security-crypto` + Hilt KSP.
- `core/datastore/src/main/java/com/kshavrin/mymoney/core/datastore/model/AppSettings.kt` — `data class AppSettings(...)` with every field from §7.3 lines 1665–1681. Default values exactly as TDD specifies (note `autoSyncEnabled = true` per OQ-7, `biometricIdleTimeoutSec = 60`, `firstPositiveSeen = false`).
- `core/datastore/src/main/java/com/kshavrin/mymoney/core/datastore/model/SecureSettings.kt` — `data class SecureSettings(val dropboxRefreshToken: String? = null, val gdriveAccountEmail: String? = null, val pinHash: String? = null)`.
- `core/datastore/src/main/java/com/kshavrin/mymoney/core/datastore/AppSettingsKeys.kt` — `object AppSettingsKeys` containing one `stringPreferencesKey("language")`, etc., per field.
- `core/datastore/src/main/java/com/kshavrin/mymoney/core/datastore/AppSettingsRepository.kt` — `interface AppSettingsRepository` + `class AppSettingsRepositoryImpl @Inject constructor(...)`. Exposes `val settings: Flow<AppSettings>` (via `dataStore.data.map { it.toAppSettings() }.distinctUntilChanged()`) and `suspend fun update(transform: (AppSettings) -> AppSettings)`. Mapping helpers `Preferences.toAppSettings()` and `AppSettings.toPreferences()` in the same file.
- `core/datastore/src/main/java/com/kshavrin/mymoney/core/datastore/SecureStorage.kt` — `interface SecureStorage` + `class SecureStorageImpl @Inject constructor(@ApplicationContext ctx)`. Uses `EncryptedSharedPreferences.create(...)` with `MasterKey.Builder(ctx).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()`. File name: `com.kshavrin.mymoney_secure` (matches §8.3 line 2055).
- `core/datastore/src/main/java/com/kshavrin/mymoney/core/datastore/di/DataStoreModule.kt` — Hilt module providing the singleton `DataStore<Preferences>` (file name `app_settings.preferences_pb` — matches §8.3 line 2051), bound `AppSettingsRepository = AppSettingsRepositoryImpl`, bound `SecureStorage = SecureStorageImpl`.
- `core/datastore/src/test/java/com/kshavrin/mymoney/core/datastore/AppSettingsRepositoryTest.kt` — unit test using `PreferenceDataStoreFactory.create` with a temporary file. Round-trip: write each field, read back, verify equality.
- `core/datastore/src/androidTest/java/com/kshavrin/mymoney/core/datastore/SecureStorageTest.kt` — instrumentation test (EncryptedSharedPreferences needs Keystore). Round-trip Dropbox token.

## Task checklist

- [ ] Re-read TDD §7.3. Write down the 15 `AppSettings` fields and their types — especially the deliberately-monotonic `firstPositiveSeen` (false → true, never back; validate this rule in `update()`).
- [ ] Write `AppSettings.kt` exactly per TDD. Defaults matter — see lines 1665–1681.
- [ ] Write `AppSettingsKeys.kt`. Use one key per field, naming convention `LANGUAGE`, `THEME_MODE`, …
- [ ] Write `AppSettingsRepositoryImpl`. Mapping functions `toAppSettings()` / `toPreferences()` handle nullable `Long?` (for `defaultAccountId = -1L` sentinel and `onboardingCompletedAt: Long?` proper null).
- [ ] In `update(transform)`, after applying `transform`, validate `firstPositiveSeen` cannot flip true→false. If a caller tries to set it false when the current value is true, throw `IllegalStateException` (the test should assert this).
- [ ] Write `SecureStorage`. Three fields. Don't expose the underlying `SharedPreferences` — only typed getters/setters.
- [ ] Write Hilt module. Confirm `@Singleton` on both impls.
- [ ] Write unit test for `AppSettingsRepository` (uses `TestScope` + `PreferenceDataStoreFactory.create`).
- [ ] Write instrumentation test for `SecureStorage` (needs Android Keystore — must run on emulator).
- [ ] Run `:core:datastore:test` and `:core:datastore:connectedAndroidTest`.
- [ ] Smoke-check: install app, open with debug `IconButton` that calls `settings.update { it.copy(themeMode = "dark") }` — observe theme actually flips after relaunch. (We can't observe live yet — PHASE_12 wires the theme observer.)
- [ ] Update PROGRESS.md.

## Done criteria

- `.\gradlew.bat :core:datastore:test` passes. Round-trip covers all 15 `AppSettings` fields.
- `.\gradlew.bat :core:datastore:connectedAndroidTest` passes — `SecureStorage` round-trips Dropbox token, GDrive email, PIN hash.
- Hilt-injecting `AppSettingsRepository` into a test `@AndroidEntryPoint` works.
- Storage layout matches §8.3: `app_settings.preferences_pb` at `/data/data/com.kshavrin.mymoney/files/datastore/`, `com.kshavrin.mymoney_secure.xml` at `shared_prefs/`. Verify via `adb shell run-as com.kshavrin.mymoney ls -R files shared_prefs`.

## Verification commands

```powershell
cd D:\Pet\TDD_creater\MyMoney_app
.\gradlew.bat :core:datastore:test
.\gradlew.bat :core:datastore:connectedAndroidTest
adb shell run-as com.kshavrin.mymoney.debug ls -R files shared_prefs
```

## Notes for next session

(empty — fill at end of session)
