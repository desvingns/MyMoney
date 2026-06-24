# MyMoney project audit — 2026-06-10

Method: 4 parallel read-only auditors (money/date correctness, state/concurrency, hygiene/conventions, test-coverage inventory) + manual cross-verification of coverage claims. Every finding below was verified against source (file:line cited). The coverage agent's raw "untested classes" list was partially wrong (it missed `src/test/kotlin` roots) — the coverage section here is the corrected version.

---

## 1. Bugs

### CRITICAL

**C1. Timezone split-brain: writes use UTC-midnight, reads/aggregation use system zone.**
- Writes (UTC): `feature/transaction/.../expense/AddExpenseViewModel.kt:195`, `income/AddIncomeViewModel.kt:195`, `transfer/TransferViewModel.kt:223`, `feature/transactionslist/.../detail/TransactionDetailViewModel.kt:403-404` — all `occurredAt = date.atStartOfDay(ZoneOffset.UTC)`.
- Reads (systemDefault): `core/domain/.../time/PeriodArithmetic.kt:10,22-23` (ALL period boundaries), `TransactionsListScreen.kt:504` (row dates), `core/database/.../BackupRepositoryImpl.kt:322` (Monefy import writes **local** midnight — a third convention).
- Failure: any UTC-negative zone (all of the Americas) — a transaction saved for "June 10" lands in June 9 buckets; donut/balance/period grouping all wrong; saving on the 1st of a month books it into the previous month. Invisible in UTC+ dev/test zones — that's why 809 tests are green.
- Secondary: Monefy-imported rows (local midnight) vs manual rows (UTC midnight) differ; in UTC+3 opening an imported row in the edit form (`TransactionDetailViewModel.kt:108` reads via UTC) shows **yesterday's** date → open+save silently shifts the date back a day (data corruption).
- Fix: pick ONE convention. Recommended: local zone (`ZoneId.systemDefault()`) at all 4 write sites + the detail read site — matches PeriodArithmetic and Monefy import. Add regression tests that run with `TimeZone.setDefault(America/New_York)`.

**C2. Lock screen permanent lockout.**
- `feature/lockscreen/.../overlay/LockOverlay.kt:171-173` handles only `ERROR_LOCKOUT/ERROR_LOCKOUT_PERMANENT`. `ERROR_NEGATIVE_BUTTON` (user taps Cancel), `ERROR_NO_BIOMETRICS` (fingerprints deleted in system settings), `ERROR_HW_UNAVAILABLE` → static "Locked" screen, `BackHandler {}` (line 71) swallows back, prompt never re-shown (LaunchedEffect(Unit)).
- `BiometricSetupViewModel.kt:74-81` enables the lock BEFORE a PIN exists, and the PIN dialog is dismissible (`PinSetupDismissed`, :58-59) → lock enabled with `pinHash=null`; the LOCKOUT→PIN branch is then dead (`verifyPin` always false, LockOverlay.kt:145-149). User's financial data permanently inaccessible.
- Fix: handle all biometric error codes (negative button → PIN fallback / retry affordance); refuse to enable lock until a PIN is stored.

**C3. Auto Backup restores EncryptedSharedPreferences → crash-loop on a new device.**
- `app/src/main/AndroidManifest.xml:13` `allowBackup="true"`; `backup_rules.xml` / `data_extraction_rules.xml` are untouched AS templates (one still contains a literal `TODO`). Everything is backed up: `monefy.db`, DataStore, `com.kshavrin.mymoney_secure.xml` (PIN hash, Dropbox token).
- After device-to-device restore the Keystore master key doesn't transfer → `SecureStorageImpl.kt:17-28` (`EncryptedSharedPreferences.create` in a @Singleton constructor, no error handling) throws on first touch. With biometric lock enabled, `LockOverlayEntryPoint` touches it on every lock render → crash-loop on the lock screen.
- Fix: exclude secure prefs from backup rules; on create failure wipe + recreate the prefs file.

### HIGH

**H1. Currency-rate edit silently does not save.** (found independently by two auditors; verified against generated `CurrencyRateDao_Impl.java`)
- `feature/transaction/.../rate/CurrencyRateViewModel.kt:96-105` always saves `CurrencyRate(id = 0L, …)`; `CurrencyRateEntity.kt:25` has `unique(from,to)`; DAO is `@Upsert` → INSERT hits the unique index, fallback UPDATE runs `WHERE id = 0` → 0 rows, silent no-op; screen emits success and navigates back. First save of a pair works; every subsequent edit is lost → transfers keep converting at the stale rate.
- Contrast: `TransactionDetailViewModel.upsertRate` (:345-356) does it right (`existing?.id ?: 0L`).
- Fix: look up the existing pair and reuse its id (or `ON CONFLICT(from,to) DO UPDATE`).

**H2. DataStore read-modify-write race → lost updates / import-focus resurrection.**
- `core/datastore/.../AppSettingsRepositoryImpl.kt:23-32` — `settings.first()` OUTSIDE `dataStore.edit`, then `writeTo` rewrites ALL keys.
- Concrete: `DashboardViewModel.kt:334-351` (`AccountSelected`) runs `clearImportFocus()` (:131-137) and the `defaultAccountId` update concurrently; both read one snapshot; the second write resurrects `importFocusEpochMs` → the dashboard re-forces the import month/currency, silently rolling back the user's account selection. This is a direct regression vector for the `26dc71ac` import-focus fix. Same race: `SyncWorker` `lastSyncAt` vs UI settings; import-focus write vs `firstPositiveSeen`.
- Fix: move the transform inside `dataStore.edit { prefs -> ... }` (single atomic transform).

**H3. Double-tap Save creates two transactions, then double popBackStack → empty screen.**
- `AddExpenseViewModel.kt:170-217` — `save()` doesn't check `isSaving` (set only inside the coroutine, :184); keypad-first flow saves via category tap, `onCategoryPicked` (:155-168) also unguarded, grid not disabled (`TransactionFormContent.kt:62`). Two upserts(id=0) = duplicate transactions + two `NavigateBack` → second `popBackStack()` pops Dashboard → empty NavHost.
- Same pattern: `AddIncomeViewModel.kt:184`, `TransferViewModel.kt:203-262`, `TransactionDetailViewModel.kt:253-343`, `CategoryEditViewModel.kt:81-111`, AccountEdit/CurrencyEdit/GoalEdit.
- Fix: `if (state.isSaving) return` set synchronously at the top of every save/pick path.

**H4. Cloud pull (`keepRemote`) swaps the DB file under a running app without process restart.**
- `core/sync/.../SnapshotSyncRepository.kt:72-92` → `BackupRepositoryImpl.kt:368-378`: `database.close()` on a @Singleton Room + file copy while Flow subscriptions are live; collectors (`DashboardViewModel.kt:238-242`) can crash; UI keeps showing pre-pull data. Local restore does it correctly (`relaunchApplication`, `BackupRestoreScreen.kt:269-275`); sync path doesn't (`CloudSyncViewModel.kt:118-121`). Bonus: safety snapshot deleted in `finally` even when import failed (:87-90).
- Fix: route keepRemote through the same restart-after-restore; keep the safety snapshot on failure. (Sync is DevOps-gated OFF → not user-visible yet.)

**H5. Lock bypass windows: content flash on cold start + no FLAG_SECURE.**
- `LockController.kt:33-46` — `_shouldShowLock` starts `false`, flips async after first DataStore emission; `MainActivity.kt:45-58` composes NavHost immediately, splash released on first frame. Real balances visible/clickable for several frames on every cold start with lock enabled. No `FLAG_SECURE` anywhere → task-switcher preview shows financial data "under" the lock.
- Fix: `setKeepOnScreenCondition` until lock state is known; set FLAG_SECURE when lock enabled.

**H6. Donut chart allocates per animation frame — prime suspect for the 399 ms dashboard frame p50.**
- `core/designsystem/.../MonefyDonutChart.kt:784-838` (`drawExtrudedRing`, every frame): new `Paint` + `BlurMaskFilter(7dp)` per frame (:790-800); depth loop `for (k in depth downTo 1)` with depth up to 22 × N arcs (:802-807) ≈ 200 drawArc/frame at 8 slices + per-frame list allocs. Plus `LaunchedEffect(animationKey)` (:114-117) restarts the whole animation on every balance recompute (any transaction-table write).
- Fix: cache Paint/MaskFilter; draw the wall as one layer/Path; don't restart animation when the slice set is unchanged. Re-measure with :macrobenchmark on release.

**H7. Recurring generation is non-idempotent (dormant — no UI creates templates yet).**
- `core/domain/.../GenerateDueRecurringUseCase.kt:26-37` — per-row `upsert(occurrence)` loop, `updateNextRun()` only after the loop, no enclosing DB transaction, no dedup key; `RecurringWorker.kt:19-22` returns `Result.retry()` on any failure (and `runCatching` also catches cancellation). Kill mid-loop → next run regenerates everything → duplicated scheduled payments, doubled balances.
- Fix: wrap insert+nextRun per template in `database.withTransaction`, or unique index (templateId, occurredAt).

### MEDIUM

| # | Finding | Where | Failure |
|---|---|---|---|
| M1 | Transfers invisible in ALL lists (records groups filter `kind IN (expense,income)`, transfers have categoryId=NULL); search finds them only by note. **CSV export throws if even one transfer exists** | `TransactionDao.kt:62-75`, `BackupRepositoryImpl.kt:100-102` | Transfers can't be edited/deleted from UI; users with transfers lose CSV export entirely |
| M2 | Dashboard drill-down drops the period — nav to TRANSACTIONS_LIST never passes `from`/`to` | `MyMoneyNavHost.kt:84-96` (route :116) | Year/custom range selected → list silently shows current month; sums mismatch dashboard |
| M3 | Transactions list never observes the table; one-shot `load()` in init | `TransactionsListViewModel.kt:65-67` | Edit/delete in detail → back → stale sums/groups |
| M4 | `catch(Throwable)`/`runCatching` swallow `CancellationException` project-wide | `AddExpenseViewModel.kt:209`, `SnapshotSyncRepository.kt:51,67,74`, `DropboxRepository.kt:124-138`, `SearchViewModel.kt:77-80`, workers | Cancelled searches → Sentry errors; cancellation → error banners; broken structured concurrency |
| M5 | One-shot actions lost on config change — collected via `LaunchedEffect(viewModel)`, no `repeatOnLifecycle`, replay=0 | e.g. `DashboardScreen.kt:87-89`, `BackupRestoreScreen.kt:96-117` | Rotation during emit loses `NavigateBack`/`RestartAfterRestore` (DB-swap restart skipped!) |
| M6 | Undo snackbar awaited inside the action collect loop (buffer 4, DROP_OLDEST) | `TransactionsListScreen.kt:97-115` | Row taps delayed seconds / dropped while snackbar shows |
| M7 | `recomputeBalance` races itself — new launch per call, no cancel of previous | `DashboardViewModel.kt:245-271` | Fast period swipes → stale period's numbers under new header; double confetti |
| M8 | Periodic auto-sync NEVER scheduled unless user toggles the switch (default `autoSyncEnabled=true` but startup schedules only recurring+prune) | `CloudSyncViewModel.kt:129`, `WorkSchedulerImpl.kt:22-44` | Connected Dropbox → zero background sync |
| M9 | `SHOW_ONBOARDING=false` (all build types, incl. release) → `onboardingCompletedAt` never set → app-shortcuts dead, every cold start re-routes through Splash | `app/build.gradle.kts:61`, `DecisionRouterViewModel.kt:25`, `MyMoneyNavHost.kt:313-320` | Long-press shortcuts just open dashboard; onboarding lost in release |
| M10 | PIN: unlimited attempts, no throttle; PBKDF2 10k iterations (weak; OWASP ≥600k for SHA256) | `LockOverlay.kt:108-129`, `PinHasher.kt:46` | 4-digit space trivially brute-forceable |
| M11 | Restore of a backup with a NEWER schema version bricks the app (only "file opens" validated, file already swapped → Room downgrade crash-loop) | `BackupRepositoryImpl.kt:81,371` | Check `PRAGMA user_version` before swap |
| M12 | Monefy import matches accounts by name ignoring currency | `BackupRepositoryImpl.kt:276-294` | "Наличные"(RUB) absorbs USD rows → mixed-currency balance garbage (MyMoney path checks currency :184-186; Monefy path doesn't) |
| M13 | BudgetEvaluator compares threshold via Float (~7 significant digits) | `BudgetEvaluator.kt:23` | 10M ₽ budgets misclassified at the boundary |
| M14 | Goal form: non-numeric input silently becomes 0 (`toBigDecimalOrNull() ?: ZERO`) — RU comma input "10000,50" → goal saved with 0 | `GoalEditViewModel.kt:316-317` | AccountEditViewModel:102-106 shows an error — correct pattern to copy |
| M15 | Recurring scheduler: monthly drift (Jan 31 → Feb 28 → Mar 28 forever); weekly `byDay` ignores `interval` (every-2-weeks fires weekly) | `RecurringScheduler.kt:17,24-37` | Anchor day-of-month from `startsAt`; jump interval weeks before byDay scan |

### LOW

- L1 `MoneyFormatter.kt:19-23` — DecimalFormat default HALF_EVEN vs domain HALF_UP → display/domain mismatch on .005 boundaries.
- L2 `CalculatorEngine.kt:155-158` — division by zero silently yields 0.
- L3 `BalanceCalculator.kt:65,77` — `fraction` computed from income+expense (dead value today; dashboard recomputes from expense-only — trap for future consumers).
- L4 `GoalEditViewModel.kt:305`, `AccountEditViewModel.kt:121` — every edit overwrites `createdAt = now`.
- L5 `PeriodArithmetic.kt:15` — `Period.All = 0..MAX` excludes pre-1970 dates.
- L6 `InitialDataSeeder.kt:29-88` — seed check-then-act, no transaction; crash between currencies and categories → categories never seeded; `SplashViewModel.kt:24-27` has no try/catch around it.
- L7 `BackupRotationWorker` never enqueued (dead; rotation runs inline in exportDb).
- L8 CloudSync "Connect" is a no-op (`CloudSyncScreen.kt:58-59`); `monefy://` + `DRIVE_OPEN` intent-filters (`AndroidManifest.xml:37-47`) accepted but unhandled.
- L9 `SecureStorageImpl` does disk I/O + Keystore in constructor on the injection thread (possible main) — also see P1.
- L10 Rotation replays confetti sound/haptics (`DashboardScreen.kt:109-114`); `pinFallback` `remember` not saveable → rotation kicks user out of PIN entry back to biometric.
- L11 `DashboardViewModel.kt:176-181` import-focus month derived via systemDefault from UTC instants (same family as C1).
- L12 `CurrencyRateViewModel.kt:75` — `toDoubleOrNull` rejects RU comma input (error shown; no corruption).
- L13 BigDecimal scale-sensitive `equals` in data classes → spurious re-emissions through `distinctUntilChanged` (`ObserveBudgetAlertsUseCase.kt:57`); no wrong values found.
- L14 `CategoryEditViewModel.kt:113-124` TOCTOU count→archive; `save()` catches only IllegalArgumentException.
- L15 `ObserveBudgetAlertsUseCase.kt:36` uses `observeAll()` as a ticker — full table read+map on every change.

### Verified clean
TypeConverters/mappers (no `BigDecimal(double)` anywhere, `valueOf` everywhere); DonutGeometry guards (no div-by-zero; sentinel −1L can't collide with autoincrement ids); TransferExecutor (rate>0 enforced at write, multiply-only); goal calculators (DECIMAL64, 0% branch, no infinite loops); Monefy CSV tokenizer (RFC-4180, real U+00A0, atomic withTransaction); CalculatorEngine BR-7; Room migrations 1→4 registered, schemas committed; `AccountDao.computeBalance` (transfers + initial balance correct); no `runBlocking`/`GlobalScope`/direct `Dispatchers.*` outside DispatchersModule; the single `stateIn` uses `WhileSubscribed(5000)`; WorkManager Hilt wiring correct.

---

## 2. Improvement points

### P1 (high)
1. **Backup rules** — see bug C3 (manifest + both rule files are templates).
2. **`SHOW_ONBOARDING=false` baked into release** (`app/build.gradle.kts:61`, "Temporary") — move to debug build type or revert before release; see bug M9.
3. **SecureStorage constructed blocking on main at inject time**; `androidx.security-crypto 1.1.0-alpha07` is deprecated/archived upstream — lazy/background init now, plan a migration watch-item.
4. **Exported `com.dropbox.core.android.AuthActivity` with missing class** (`feature/cloudsync/src/main/AndroidManifest.xml`, `tools:ignore="MissingClass"`, scheme `db-PLACEHOLDER_DROPBOX_APP_KEY`) — browser hit on that scheme = crash. Gate/remove until OQ-2.

### P2 (medium)
5. Dead module `:core:network` (zero consumers; drags okhttp/retrofit into APK via `app/build.gradle.kts:126`) — unlink (archive per project rule) or use.
6. Dead `SYNC_DISABLED` BuildConfig flag (defined twice — `app/build.gradle.kts:55-59`, `core/sync/build.gradle.kts:21-25` — read by nothing).
7. **No static analysis at all**: no detekt/ktlint/spotless; no `lint {}` block (CI runs lintDebug on defaults, no baseline). Minimal gate: detekt+ktlint root config, `lint { abortOnError = true }` + baseline in :app, Kover for coverage.
8. CI (`.github/workflows/ci.yml` — it EXISTS): connected job runs monolithic `connectedDebugAndroidTest` (locally known to hang at 122 tests in one shot — batch it per module); publishes an unsigned release APK artifact (sign or mark explicitly).
9. Startup work in `MyMoneyApp.onCreate` on main: `workScheduler.scheduleDailyJobs()` (:27) + Sentry init + `SoundPoolImpl` (SoundPool + 6× `getIdentifier` in constructor, `core/ui/.../SoundPlayer.kt:44-54`) + SecureStorage (p.3) — main visible cold-start contributors (measured 5.5 s median is emulator/debug; re-measure via :macrobenchmark on release).
10. `:core:testing` is an empty shell (only `package.kt`) while fakes are duplicated per feature module (known PHASE_15 debt — java-test-fixtures).

### P3 (hygiene)
11. Unused catalog entries in `gradle/libs.versions.toml`: `coilCompose 3.0.0-rc02` (an RC!), `firebase-analytics-ktx`, `sentry-android`.
12. No convention plugins — compileSdk/minSdk/compileOptions/packaging duplicated across ~18 modules (NIA build-logic pattern).
13. `gradle.properties`: `-Xmx2048m` low for 18 modules; no configuration-cache; no `android.nonTransitiveRClass`.
14. Literal version in `settings.gradle.kts` (foojay-resolver 1.0.0) — the single exception to the toml rule.
15. A11y worst gaps: `MonefyBalanceBar.kt:36-42` clickable row with null contentDescriptions; keypad operator keys rely on TalkBack reading glyphs; donut slices not individually focusable.
16. Dead deprecation branch in `HapticPlayer.kt:87-94` (minSdk 31 → `SDK_INT >= S` always true).
17. `monefy://` / `DRIVE_OPEN` intent filters accepted but unhandled (`MainActivity.kt:64-70` reads only shortcut extras).
18. `app/src/test/.../ExampleUnitTest.kt` — template leftover.
19. ProGuard: `-keep class io.sentry.** { *; }` overly broad.

### Verified clean (hygiene)
i18n parity 100% (326 EN keys across 10 modules, 0 missing RU); zero hardcoded user-facing strings; zero TODO/FIXME in production Kotlin; zero Log/println in production; zero feature→feature deps; toml matches CLAUDE.md versions; Sentry debug button removed; baseline profile committed; PIN uses PBKDF2WithHmacSHA256+salt; plurals present where needed (PluralsTest exists).

---

## 3. Test coverage

Stats: ~283 production Kotlin files; ~129 unit-test + ~63 androidTest files; ~1700 `@Test` methods. CI runs lintDebug + all unit tests + assembleRelease + connected tests (API 34 emulator). **No coverage tooling** (no JaCoCo/Kover) → no numbers, only structural analysis.

Correction vs the raw inventory: `:core:domain` calculators ARE tested (BalanceCalculatorTest, GoalLoanCalculatorTest, ContributionCalculatorTest, GoalSavingsProjectorTest, CapitalBalanceDeltaTest, BudgetEvaluatorTest, GenerateDueRecurringUseCaseTest, ObserveBudgetAlertsUseCaseTest, InitialDataSeederTest, MonefyCsvImportParserTest — 17 test files in `src/test/kotlin`). `SecureStorageTest` exists (androidTest).

**Real gaps (verified by Glob):**
| Gap | Detail |
|---|---|
| `:feature:onboarding` — ZERO tests | `OnboardingViewModel`, `SplashViewModel` (runs InitialDataSeeder; no try/catch — see L6) |
| `:feature:transaction` | No `TransferViewModelTest` (only screen-contract test), no `CurrencyRateViewModelTest` — **exactly where bug H1 lives** |
| `:core:database` unit level | Only `GoalRepositoryImplTest` + `GoalMapperTest`; TransactionRepositoryImpl, BackupRepositoryImpl (import/export/restore monster), other 9 repos — androidTest/E2E only (10 files), JVM-untested |
| `:core:datastore` | 1 unit test (`AppSettingsRepositoryTest`) — does NOT catch the H2 race |
| `:app` | `DecisionRouterViewModel`, MainActivity shortcut/deeplink routing — no unit tests; `ExampleUnitTest` stub still present |
| `:feature:dictionaries` | Missing: AccountEditViewModelTest, AccountsListViewModelTest, CurrenciesListViewModelTest, CategoryEditViewModelTest (partial via CategoryEditFromPickerTest) |
| `:feature:dashboard` | 1 unit-test file for 11 prod files (DashboardViewModel is the app's most complex VM; UI covered by 36 androidTests) |
| `:core:sync` workers | Worker classes themselves untested at JVM level (logic seams are) |
| Uncommitted | `core/designsystem/src/androidTest/.../appbar/MoneyHeroAppBarUiTest.kt` (13 tests) — untracked in git |

**Systemic gap:** all 809+ tests are green WITH bugs C1/H1/H2/H3 present, because (a) no test varies the JVM timezone (everything runs in the dev's UTC+3), (b) no test edits an existing currency-rate pair, (c) no concurrency tests on DataStore updates, (d) no double-tap tests. Tests pin the happy path of the current implementation; boundary/adversarial coverage is the missing dimension — add a fixed `TimeZone America/New_York` run to the test discipline.

---

## 4. Proposed plan

### Stage 0 — quick wins (hours)
1. Commit untracked `MoneyHeroAppBarUiTest.kt`.
2. H1 currency-rate fix (reuse existing id, copy detail-VM pattern) + regression test.
3. C3/P1.1 backup rules: exclude secure prefs (+ wipe-recreate recovery in SecureStorageImpl).
4. M9/P1.2 `SHOW_ONBOARDING` → debug-only.
5. PeriodStrip UTC conversion fix (dashboard custom-range off-by-one; part of C1 family).
6. L1 MoneyFormatter roundingMode = HALF_UP; toml cleanup (P3.11); archive ExampleUnitTest.

### Stage 1 — epic `timezone-unification` (CRITICAL, the big one)
Pick local-zone convention; change 4 write sites + detail read site + import-focus derivation; migration decision for existing UTC-midnight rows (one-time normalizer or read-side tolerance); regression suite with `TimeZone.setDefault(UTC-4)` covering save→period-bucket→display round-trip + Monefy-import → edit round-trip. ~2–3 SPECs.

### Stage 2 — epic `save-integrity`
Double-tap guards on all 8 save/pick paths (H3) + DataStore atomic `edit` transform (H2) + CancellationException rethrow sweep (M4). ~3 SPECs.

### Stage 3 — epic `lock-security`
Biometric error-code handling + PIN-before-lock enforcement (C2); brute-force throttle + PBKDF2 iterations (M10); FLAG_SECURE + splash-hold-until-lock-known (H5); pinFallback saveable (L10). ~3 SPECs.

### Stage 4 — epic `records-completeness`
Transfers visible in records list + CSV export skips instead of throwing (M1); drill-down passes period (M2); list observes table / reloads on return (M3); undo snackbar in child coroutine (M6); recomputeBalance cancel-previous (M7). ~4 SPECs.

### Stage 5 — epic `donut-performance`
Cache Paint/BlurMaskFilter, single-pass extruded wall, stable animationKey (H6); then re-run :macrobenchmark on release and reconcile the TDD perf budgets (deferred PHASE_15 gate). ~2 SPECs.

### Stage 6 — epic `quality-gates` (parallel, continuous)
Kover + per-module thresholds; detekt+ktlint+lint baseline; CI connected job batched per module, mark unsigned artifact; missing VM tests (onboarding, Transfer, CurrencyRate, 4 dictionaries VMs, DecisionRouter); fixed-timezone test lane. ~3–4 SPECs.

### Stage 7 — epic `import-and-forms-hardening`
Monefy import currency-aware account match (M12); GoalEdit parseMoney validation + comma normalization (M14, copy AccountEdit pattern); BudgetEvaluator BigDecimal compare (M13); createdAt preservation (L4); seeder transaction + splash error handling (L6).

### Stage 8 — `sync-hardening` (when OQ-2/3 unblock; code is DevOps-gated OFF)
keepRemote restart-after-restore + keep safety snapshot on failure (H4); PRAGMA user_version check before swap (M11); auto-sync scheduling on startup (M8); recurring idempotency transaction (H7) + scheduler drift (M15) before any recurring UI ships.

### Backlog / opportunistic
Dead code unlink (:core:network, SYNC_DISABLED, BackupRotationWorker, intent filters, HapticPlayer branch); convention plugins + gradle.properties; a11y trio (balance bar, keypad operators, donut slices); :core:testing java-test-fixtures consolidation.

Priority rationale: data correctness (Stages 1–2) > security (3) > user-visible completeness (4) > perf (5) > infrastructure (6, runs in parallel) > dormant sync code (8).
