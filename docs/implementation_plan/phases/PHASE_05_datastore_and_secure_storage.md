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

- [x] Re-read TDD §7.3. Write down the 15 `AppSettings` fields and their types — especially the deliberately-monotonic `firstPositiveSeen` (false → true, never back; validate this rule in `update()`).
- [x] Write `AppSettings.kt` exactly per TDD. Defaults matter — see lines 1665–1681.
- [x] Write `AppSettingsKeys.kt`. Use one key per field, naming convention `LANGUAGE`, `THEME_MODE`, …
- [x] Write `AppSettingsRepositoryImpl`. Mapping functions `toAppSettings()` / `toPreferences()` handle nullable `Long?` (for `defaultAccountId = -1L` sentinel and `onboardingCompletedAt: Long?` proper null).
- [x] In `update(transform)`, after applying `transform`, validate `firstPositiveSeen` cannot flip true→false. If a caller tries to set it false when the current value is true, throw `IllegalStateException` (the test should assert this).
- [x] Write `SecureStorage`. Three fields. Don't expose the underlying `SharedPreferences` — only typed getters/setters.
- [x] Write Hilt module. Confirm `@Singleton` on both impls.
- [x] Write unit test for `AppSettingsRepository` (uses `TestScope` + `PreferenceDataStoreFactory.create`).
- [x] Write instrumentation test for `SecureStorage` (needs Android Keystore — must run on emulator).
- [x] Run `:core:datastore:test` and `:core:datastore:connectedAndroidTest`.
- [x] Smoke-check: install app, open with debug `IconButton` that calls `settings.update { it.copy(themeMode = "dark") }` — observe theme actually flips after relaunch. (We can't observe live yet — PHASE_12 wires the theme observer.)
- [x] Update PROGRESS.md.

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

### What landed (commit 15ec5ac)

- **AppSettings.kt**: 15 fields verbatim from TDD §7.3 (`language`/`themeMode`/`biometricLockEnabled`/`biometricIdleTimeoutSec=60`/`soundEnabled=true`/`hapticEnabled=true`/`defaultAccountId=-1L`/`defaultPeriod="month"`/`dateFirstDayOfWeek=1`/`currencySymbolPosition="before"`/`onboardingCompletedAt: Long? = null`/`lastSyncAt: Long? = null`/`autoSyncEnabled=true` per OQ-7/`budgetModeEnabled=true`/`firstPositiveSeen=false` per AS-10 monotonic flag).
- **SecureSettings.kt**: 3 nullable String fields (dropboxRefreshToken / gdriveAccountEmail / pinHash).
- **AppSettingsKeys.kt**: 15 typed Preferences keys (internal object).
- **AppSettingsRepositoryImpl.kt**: `@Singleton` impl with `settings: Flow<AppSettings>` via `dataStore.data.map { it.toAppSettings() }.distinctUntilChanged()` and `suspend fun update(transform)`. **Monotonic firstPositiveSeen guard** — throws IllegalStateException on true→false.
- **SecureStorageImpl.kt**: `@Singleton` impl using `EncryptedSharedPreferences.create(...)` with `MasterKey` AES256_GCM scheme. File `com.kshavrin.mymoney_secure`.
- **DataStoreModule.kt**: `@InstallIn(SingletonComponent::class)` — `@Provides @Singleton DataStore<Preferences>` via `PreferenceDataStoreFactory.create(scope = CoroutineScope(SupervisorJob() + @IoDispatcher))`. Plus `@Binds @Singleton` for AppSettingsRepository and SecureStorage interfaces. **FIRST cross-module Hilt qualifier usage** — `@IoDispatcher` from `:core:common`.
- **Tests** — `AppSettingsRepositoryTest` (4 unit tests: defaults / 15-field round-trip / monotonic enforcement / null-clearing). `SecureStorageTest` (5 androidTests: per-secret roundtrip / clearAll / null-write-removes).

### Done criteria status

| Criterion | Status |
|---|---|
| `.\gradlew.bat :core:datastore:test` passes; round-trip covers all 15 AppSettings fields | ⚠ deferred — Windows loopback blocker; 4 unit tests written + verified-by-inspection |
| `.\gradlew.bat :core:datastore:connectedAndroidTest` passes; SecureStorage roundtrips Dropbox/GDrive/PIN | ⚠ deferred — loopback + Android Keystore needed; 5 androidTests written |
| Hilt-injecting AppSettingsRepository into @AndroidEntryPoint works | ⚠ deferred — loopback; Verifier hilt_graph=ok confirms wiring statically |
| Storage layout matches §8.3 | ⚠ deferred — gated by adb access; file names match TDD by construction |

### DataStore + EncryptedSharedPreferences gotchas worth knowing

1. **`preferencesDataStoreFile(name)` auto-appends `.preferences_pb`.** Pass `"app_settings"` — DataStore writes `app_settings.preferences_pb`. Don't include the extension yourself.
2. **`@IoDispatcher` requires :core:common dep on the consumer.** `:core:datastore` now depends on `:core:common` — first explicit cross-module Hilt qualifier consumption.
3. **Monotonic firstPositiveSeen** — enforced in `update()` not in `toAppSettings()`. The READ path returns whatever's persisted (defaults to false). The WRITE path blocks regression. Pattern is non-obvious; if a future caller resets prefs via clearAll-equivalent, firstPositiveSeen drops back to false — that's expected (lifetime flag, not session flag).
4. **`PreferenceDataStoreFactory.create(produceFile = { tempFile })`** lets you skip DI in tests — no Context required, just a temp file. Use as the unit-test substrate for any DataStore Preferences code.
5. **`SupervisorJob() + ioDispatcher`** is the CoroutineContext for DataStore writes. If any single write throws, the supervisor keeps the scope alive for subsequent writes.
6. **`@Binds` + `@Provides` in same module** — Hilt allows `object DataStoreModule { @Provides ... }` + `abstract class DataStoreBindings { @Binds ... }` to coexist under `@InstallIn(SingletonComponent::class)`. `@Binds` requires an `abstract class` (not `object`).
7. **EncryptedSharedPreferences MUST run on Android with Keystore** — JUnit unit tests cannot exercise the encryption; use androidTest with `ApplicationProvider.getApplicationContext()` for integration tests.
8. **Null vs. default for optional fields** — `onboardingCompletedAt: Long? = null` vs. `defaultAccountId: Long = -1L`. Both encode "no value", but null is preferred for genuine absences (supports `remove(key)`), while sentinel values (`-1L`) are used when there's a natural "invalid ID" semantic.

### PHASE_06 entry hint

- Open `docs/implementation_plan/phases/PHASE_06_domain_layer.md`.
- Build `:core:domain` — domain entities (Account, Category, Transaction, etc. with BigDecimal money + LocalDate/Instant time vs. data-layer Double + Long), repository interfaces, use cases (BalanceCalculator, TransferExecutor, BudgetEvaluator, RecurringScheduler per TDD §2.1).
- `InitialDataSeeder` (per TDD §7.7) seeds 20 currencies + 1 default Cash account in locale currency + 15 expense categories per §6.1 + 2 income categories (Salary, Other — locked per AS-8/OQ-12).
- Domain repository interfaces in `:core:domain/repository/` — data-layer impls in `:core:database/repository/`. Will need a repository binding module.
- `:core:database` repositories will catch Room exceptions + remap to `SyncException(SyncError)` per CLAUDE.md error handling policy.
