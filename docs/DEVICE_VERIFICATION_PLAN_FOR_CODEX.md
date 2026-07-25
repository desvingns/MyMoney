# MyMoney — On-Device Test Coverage Remediation — Execution Plan for Codex (GPT-5.6)

> **SUPERSEDED for execution (2026-05-28).** Active execution now follows
> `docs/DEVICE_VERIFICATION_PLAN_FOR_SONNET.md` (a step-by-step runbook for Claude Sonnet 4.6). The
> live, authoritative progress tracker is `docs/DEVICE_VERIFICATION_PROGRESS.md` — the coverage
> numbers in *this* document are stale (it predates the ~18 screens already covered). Kept for
> historical context and the original rationale only.

> **Why this document exists.** `MyMoney_TDD.md` was implemented here (27 screens, multi-module
> Kotlin/Compose/Hilt/Room). Because of a process mistake, features were built **without on-device
> verification**: there are **655 green JVM unit tests** but **only ~8 instrumented (`androidTest`)
> tests**, and **24 of 27 screens have zero on-device UI coverage**. TDD **§12.4 + §12.7** required
> happy/empty/error Compose UI tests for every screen, run on an emulator via
> `connectedDebugAndroidTest`; **§12.6** required a manual TalkBack/contrast/font pass. This plan closes
> that gap. **You (Codex, GPT-5.6) run the `Pixel_5_API_34` emulator yourself** and do the full
> write→run→green loop.

You are completing the **device-verification** work that PHASE_15 left open. The app is feature-complete
and unit-tested on the JVM, but almost nothing has been verified on a real device. Your job: give every
screen and every interactive control an **instrumented test that runs green on the `Pixel_5_API_34`
emulator**, and author a **manual QA checklist** for the sensory/accessibility/performance items that
cannot be automated.

Read this whole document before touching code. Then read `MyMoney_TDD.md` §4 (screen specs &
acceptance criteria), §12 (testing strategy), §14.1 (locked decisions AS-1…AS-15) and
`docs/implementation_plan/PROGRESS.md` (PHASE_14/15 "Notes for next session").

## 0. Iron rules (do not break these — they are the whole point)

1. **Write one test → run it on the AVD → see it green → only then write the next.** Never write a batch
   of tests "blind". The original mistake was building without running on a device; do not repeat it.
2. **Never weaken a test to make it pass.** No `@Ignore`, no deleting failing tests, no commenting out
   assertions, no `assertTrue(true)`, no catching-and-swallowing. If a test exposes a real defect, **fix
   the production bug** with the smallest correct change and record it in your phase report. If you
   cannot fix it safely, leave the test failing and escalate it in the report — do **not** hide it.
3. **Follow the existing conventions exactly** (see §3). Look at `PeriodStripUiTest.kt` and
   `SwipeToDeleteUiTest.kt` first — match their style: `@RunWith(AndroidJUnit4::class)`,
   `createComposeRule()`, `MyMoneyTheme { … }` wrapper, string lookup via
   `InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.…)` (never hard-code
   user-facing latin literals — the app ships EN + RU).
4. **Do not refactor production code** beyond the minimum a test legitimately needs (adding a
   `Modifier.testTag(...)`, making a `*Content` composable `public`, exposing a stable semantics label).
   Any production touch must be justified in the report.
5. **Keep tests deterministic.** Disable animations, control the clock, seed fixed data. See §3.4.
6. **Do not enable Sentry/Firebase/cloud sync.** They are gated OFF by default (`sentry.dsn` blank,
   `firebase.enabled`≠true, `sync.enabled`≠true). PHASE_15 notes that Sentry provider auto-init had to
   be disabled for device startup to be green — keep all of this off in tests.
7. **Update `docs/implementation_plan/PROGRESS.md`** at the end of every phase with what ran, the
   pass/fail counts, the report path, and any production bug you fixed.

## 1. Project facts you need

- Package / namespace: `com.kshavrin.mymoney`. Repo root: `D:\Pet\TDD_creater\MyMoney_app`.
- Application: `MyMoneyApp` (`@HiltAndroidApp`). Entry Activity: `MainActivity`
  (`@AndroidEntryPoint`, extends `AppCompatActivity`). It hosts `MyMoneyNavHost(...)` inside a `Box`,
  with a `LockOverlay` shown only when `LockController.shouldShowLock` is true (AS-5). It injects
  `LockController`, `SoundPlayer`, `HapticPlayer` and provides `LocalSoundPlayer` / `LocalHapticPlayer`.
- Navigation start route is `Destinations.DECISION` → `SPLASH` → (`ONBOARDING` if
  `AppSettings.onboardingCompletedAt == null`, else `DASHBOARD`). `InitialDataSeeder` runs from
  `SplashViewModel` and seeds 20 currencies + 1 `Cash` account + 17 categories.
- All routes are constants in `app/.../navigation/Destinations.kt` (DASHBOARD, ADD_EXPENSE=
  `transaction/expense`, ADD_INCOME, TRANSFER, CURRENCY_RATE, CATEGORY_PICKER, TRANSACTIONS_LIST,
  TRANSACTION_DETAIL, SEARCH, SETTINGS, SETTINGS_THEME, SETTINGS_LANGUAGE, SETTINGS_ABOUT(+_PRIVACY/_HELP),
  SETTINGS_BACKUP, CATEGORIES_LIST, CATEGORY_EDIT, ACCOUNTS_LIST, ACCOUNT_EDIT, CURRENCIES_LIST,
  CURRENCY_EDIT, CLOUD_SYNC, LOCK_SCREEN).
- Feature screens live in `:feature:*` modules; `*Content` composables are public and visible from
  `:app` (because `:app` depends on every feature) — that is why the existing UI tests sit in
  `app/src/androidTest`. **Put all new screen UI tests in `app/src/androidTest`** unless a test only
  touches one `:core:*` module (then put it in that module's `androidTest`).
- Existing instrumented test modules: `:app`, `:core:database`, `:core:datastore`, `:core:designsystem`.
  Their `connected*` runs are already green (`:app` 3/3, `:core:designsystem` 3/3, `:core:database`
  20/20, `:core:datastore` 5/5).

## 2. How to run on the emulator (Windows / PowerShell)

```powershell
# 1. Use the Android Studio JBR (the same JDK 17 that builds the project). Verify:
$env:JAVA_HOME = "<path to Android Studio>\jbr"      # do NOT hardcode if already configured
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
java -version                                         # must report 17

# 2. Boot `Pixel_5_API_34` in Android Studio on the primary Windows host.
#    This direct remote attachment is suitable for manual ADB commands.
$adb = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
$device = '10.0.2.2:5555'
& $adb kill-server
& $adb start-server
& $adb connect $device
& $adb devices -l
& $adb -s $device shell getprop ro.boot.qemu.avd_name  # Pixel_5_API_34
# poll until boot completed:
while ((& $adb -s $device shell getprop sys.boot_completed 2>$null).Trim() -ne "1") { Start-Sleep -Seconds 2 }
& $adb -s $device shell input keyevent 82               # dismiss keyguard

# If the NAT attach fails or hangs, discover a local host-side device before stopping.
& $adb devices -l
$device = $null
foreach ($serial in ((& $adb devices | Select-String "`tdevice$").Line | ForEach-Object { ($_ -split "`t")[0] })) {
  $avd = (& $adb -s $serial shell getprop ro.boot.qemu.avd_name).Trim()
  $sdk = (& $adb -s $serial shell getprop ro.build.version.sdk).Trim()
  $boot = (& $adb -s $serial shell getprop sys.boot_completed).Trim()
  if ($avd -eq 'Pixel_5_API_34' -and $sdk -eq '34' -and $boot -eq '1') { $device = $serial; break }
}
if (-not $device) { throw 'Pixel_5_API_34 not connected or not boot-complete' }

# 3. Run instrumented tests through the host-AVD helper. AGP 8.7.3 UTP cannot
#    profile a Windows remote serial containing ":" (`10.0.2.2:5555`); the helper
#    exposes the host ADB server locally so UTP uses serial `emulator-5554`.
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\run_connected_test_on_host_avd.ps1 -Tasks ':app:connectedDebugAndroidTest'
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\run_connected_test_on_host_avd.ps1 -Tasks ':app:connectedDebugAndroidTest' -TestClass 'com.kshavrin.mymoney.<Pkg>.<TestClass>'
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\run_connected_test_on_host_avd.ps1 -Tasks ':core:database:connectedDebugAndroidTest'
```

Reports: `app/build/reports/androidTests/connected/debug/index.html` and
`app/build/outputs/androidTest-results/connected/`. Read the report on every run; do not trust "BUILD
SUCCESSFUL" alone — confirm the test count and that 0 failed/0 skipped.

After a host or guest reboot, repeat `adb connect 10.0.2.2:5555` only for
manual ADB interaction. For Gradle tests run the helper; setting
`ADB_SERVER_SOCKET` alone does not redirect AGP's DDMLib provider. Use the
one-process `-ExecutionPolicy Bypass` invocation shown above because this guest
blocks local `.ps1` execution by default. The helper also waits 60 real seconds
after every instrumented-test invocation for this remediation run.

If the emulator is flaky: cold-boot it, `& $adb -s $device logcat -c` before a run, and capture
`& $adb -s $device logcat` on a crash. Never make a test pass by retrying a broken emulator — fix
the root cause.

## 3. Test architecture

Two patterns. Use the cheapest one that proves the behavior.

### Pattern B — per-screen Compose UI test (the workhorse; ~80% of the work)
Render the screen's public `*Content` composable directly with controlled state + callback capture.
No Hilt, no Room, no navigation — fast and deterministic. This is what `PeriodStripUiTest` already does.

```kotlin
@RunWith(AndroidJUnit4::class)
class AddExpenseContentUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun choose_category_disabled_when_amount_is_zero() {
        compose.setContent {
            MyMoneyTheme {
                AddExpenseContent(state = AddExpenseState(/* amount = 0 */), onEvent = {})
            }
        }
        compose.onNodeWithText(str(R.string.choose_category_cta)).assertIsNotEnabled()
    }

    @Test fun keypad_1_plus_2_equals_shows_3() {
        var captured: AddExpenseEvent? = null
        compose.setContent { MyMoneyTheme { AddExpenseContent(state = …, onEvent = { captured = it }) } }
        compose.onNodeWithText("1").performClick()      // assert onEvent(KeypadDigit(1)) etc.
        …
    }
    private fun str(id: Int) =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id)
}
```

For each screen produce, per TDD §12.4: a **happy-path** test (primary gesture → expected state/callback),
an **empty-state** test (no data → empty semantics visible, per the screen's `Empty` state in TDD §4.x),
and an **error-state** test (failure injected via state → banner/retry visible). Skip a variant only if
the screen genuinely has no such state (note it in the report).

### Pattern A — `MainActivity` end-to-end (the critical journeys; a handful)
Drive the real app through the real Hilt graph + Room. Use for cross-screen flows where the *wiring* is
the risk (nav, SavedStateHandle hand-offs, reactive dashboard refresh).

```kotlin
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class AddExpenseE2ETest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

    @Test fun onboarding_to_dashboard_add_expense_updates_balance() {
        // fresh in-memory DB + cleared DataStore ⇒ app starts at Splash → Onboarding
        compose.onNodeWithText(str(R.string.onboarding_get_started)).performClick() // through pager
        // Dashboard: tap "−" FAB → keypad → Choose Category → pick → Save
        // assert the balance pill text reflects the new expense
    }
}
```

### 3.1 One-time infrastructure to scaffold (PHASE 0, before any Pattern A test)
1. **Version catalog** (`gradle/libs.versions.toml`) — add libraries:
   `hilt-android-testing` (`com.google.dagger:hilt-android-testing:2.52`),
   `androidx-navigation-testing` (`androidx.navigation:navigation-testing:2.8.4`). `room-testing`,
   `androidx-test-runner`, `compose-ui-test-junit4`, `compose-ui-test-manifest`, `turbine`,
   `kotlinx-coroutines-test` already exist.
2. **`app/build.gradle.kts`** — change
   `testInstrumentationRunner = "com.kshavrin.mymoney.HiltTestRunner"`; add
   `androidTestImplementation(libs.hilt.android.testing)`,
   `kspAndroidTest(libs.hilt.compiler)`,
   `androidTestImplementation(libs.androidx.navigation.testing)`,
   `androidTestImplementation(project(":core:domain"))` /`:core:database`/`:core:datastore` as needed.
3. **`HiltTestRunner`** in `app/src/androidTest/.../HiltTestRunner.kt`:
   ```kotlin
   class HiltTestRunner : AndroidJUnitRunner() {
       override fun newApplication(cl: ClassLoader?, name: String?, ctx: Context?) =
           super.newApplication(cl, HiltTestApplication::class.java.name, ctx)
   }
   ```
4. **`TestDatabaseModule`** (`@TestInstallIn(components=[SingletonComponent::class], replaces=[DatabaseModule::class])`)
   providing an in-memory `MoneyDatabase` (`Room.inMemoryDatabaseBuilder(...).allowMainThreadQueries()`)
   and the same DAO accessors the real `DatabaseModule` exposes. Mirror the real module's `@Provides`.
5. **`TestDataStoreModule`** replacing `DataStoreModule` with a DataStore backed by a unique temp file per
   test run, so `onboardingCompletedAt` and settings don't leak between tests. Alternatively clear the
   preferences file in `@Before`.
6. **Test helpers** in `app/src/androidTest`: `str(resId)`, an `awaitIdle`/`waitUntilExists` helper, a
   `seed(...)` helper that inserts known accounts/categories/transactions through the repositories, and a
   `disableAnimations()` step (see §3.4). Reuse the unit-test fakes in `:core:testing` / module
   `test-fixtures` where they help build state objects.

### 3.4 Determinism checklist (apply to every test)
- Wrap content in `MyMoneyTheme { … }` (matches production theming).
- Disable animations: prefer `compose.mainClock.autoAdvance = false` where you step frames, or assert on
  the post-animation state via `compose.waitUntil { … }`. The donut grow-in, confetti, spring keypad and
  drawer transitions are all animated — never assert mid-animation.
- Control "today": screens use `LocalDate.now(...)`. Seed transactions relative to `LocalDate.now()` in
  the test (don't assert on absolute calendar dates).
- Ensure the lock overlay is inactive: default state has no PIN, so `shouldShowLock` is false. If a flow
  ever sets a PIN, reset it in `@After`.
- For Pattern A, each test gets a fresh in-memory DB; for Pattern B there is no shared state.

## 4. Phased work (priority order). Each phase ends with a green `connectedDebugAndroidTest` + a report.

### PHASE 0 — Instrumented infrastructure (blocking prerequisite)
Scaffold everything in §3.1. **Gate:** one trivial `@HiltAndroidTest` that launches `MainActivity` and
asserts the Splash/Onboarding root renders, green on the AVD. Do not proceed until this is green.

### PHASE 1 — Critical path + primary screens
- **Pattern A E2E:** Splash→Onboarding→Get Started→Dashboard (S00, S11); then on Dashboard the full
  **add-expense journey**: `−` FAB → keypad `1 2 + 3 =` → Choose Category → pick a seeded category →
  Save → pop to Dashboard → **assert the balance pill text reflects the new expense** (TDD §4.6 AC6,
  AS-4). This single flow is the highest-value test in the project.
- **Pattern B per screen:** S00 Splash, S11 Onboarding (pager advance, Skip==Get Started, dots, persists
  `onboardingCompletedAt`), S01/S05 Dashboard (empty state shows `empty_view_title`; negative balance pill
  `#f66561` vs positive `#7ac794`; balance-card tap → list AS-2; slice tap → filtered list AS-3; toolbar ↔
  → transfer AS-1; both FABs enabled even when empty), S06 Add Expense (happy/empty/error + ACs 1–8).

### PHASE 2 — Transaction forms
S07 Add Income (kind=INCOME, income categories only, ↔ swap preserves amount, AS-4 return path),
S03 Transfer (save disabled until `from≠to` & `amount>0` & rate exists; **AS-6 auto-nav to S27** when
currencies differ and no rate; AS-7 single-row storage reflected on dashboard), S09 Category Picker
(filter by kind; `+ ADD` → S22 → save → returns past S09 with new category preselected, AS-4),
S27 Currency Rate (pre-filled pair, save returns to S03 with rate applied). Mix Pattern B for each screen
+ one Pattern A for the AS-6 transfer-with-rate journey.

### PHASE 3 — Dictionaries CRUD
S21 Categories list (drag-reorder within section, add/edit/delete), S22 Category edit/create,
S23 Accounts list, S24 Account edit (initial balance), S25 Currencies list (+FAB custom currency),
S26 Currency edit (code field locked when accounts depend on it — `countByCurrency`). Pattern B per screen,
asserting the create/edit/delete/reorder controls fire the right events and validation blocks bad input.

### PHASE 4 — Remaining screens + integration + manual + perf
- **Screens (Pattern B):** S12 Transactions list (paging, swipe-to-delete already covered — extend),
  S13 Transaction detail/edit (edit & delete), S08 Search (200 ms debounce behavior via state, empty vs
  results vs no-match, voice mic visibility), S14 Settings root + S15 Theme (live switch), S19 Language,
  S18 Backup/Restore (export/import buttons, keep-newest-3), S20 About/Help (WebView screens),
  S17 Cloud Sync (shows "Not connected", Connect disabled while gated off), S16 Biometric setup.
- **Worker instrumentation (Pattern A-style):** use `WorkManagerTestInitHelper` +
  `TestListenableWorkerBuilder` for `RecurringWorker` (silent, AS-11), `PruneDeletedWorker` (30-day),
  `BackupRotationWorker`, and `SyncWorker` (gated off ⇒ no-op path). Assert `Result.success()` and the
  observable DB effect.
- **Manual QA checklist** — author `docs/MANUAL_QA_CHECKLIST.md` (see §6). This is a written deliverable,
  not automated.
- **R8 release walk** — install the minified `assembleRelease` APK on a clean emulator and walk all 27
  screens looking for `NoSuchMethodError`/missing-keep crashes. Record results; add ProGuard keep rules if
  needed.
- **(Optional) Macrobenchmark** — run the existing `:macrobenchmark` `StartupBenchmark` + generate a
  Baseline Profile on `Pixel_5_API_34`. Functional coverage is the priority; treat perf as stretch.
- **(Optional) CI** — wire the GitHub Actions `ui-instrumented` job (emulator API 31) from TDD §12.7 so
  this never regresses.

## 5. Coverage matrix (fill in as you go; mirror into PROGRESS.md)

| Screen | TDD §  | Pattern | happy | empty | error | e2e |
|--------|--------|---------|:-----:|:-----:|:-----:|:---:|
| S00 Splash | 4.0 | B | ☐ | – | ☐ | ✓(P1) |
| S11 Onboarding | 4.1 | B | ☐ | – | – | ✓(P1) |
| S01/S05 Dashboard | 4.2/4.3 | B | ☐ | ☐ | ☐ | ✓(P1) |
| S06 Add Expense | 4.6 | B+A | ☐ | ☐ | ☐ | ✓(P1) |
| S07 Add Income | 4.7 | B | ☐ | ☐ | ☐ | – |
| S03 Transfer | 4.8 | B+A | ☐ | ☐ | ☐ | ✓(P2) |
| S09 Category Picker | 4.10 | B | ☐ | ☐ | – | – |
| S27 Currency Rate | 4.x | B | ☐ | – | ☐ | – |
| S21–S26 Dictionaries | 4.21–4.26 | B | ☐ | ☐ | ☐ | – |
| S12 List / S13 Detail / S08 Search | 4.11–4.13/4.9 | B | ☐ | ☐ | ☐ | – |
| S14–S20 Settings | 4.14–4.20 | B | ☐ | ☐ | ☐ | – |
| S16 Lock / S17 Sync | 4.15/4.17 | B | ☐ | ☐ | ☐ | – |
| Workers (recurring/prune/rotation/sync) | §11 | A | ☐ | – | ☐ | – |

(Read each screen's exact acceptance criteria in TDD §4.x — the table is a tracker, not the spec.)

## 6. Manual QA checklist scope (`docs/MANUAL_QA_CHECKLIST.md`)
One row per check: action → expected → pass/fail/notes. Cover what instrumentation can't assert:
haptic on every keypad press (API 31–32 vs 33+ path), keypad/save sounds when `soundEnabled`, confetti on
first positive-balance milestone, BiometricPrompt enroll/unlock + PIN fallback, **TalkBack full walk of
all 27 screens** (focus order, donut & keypad announcements), dark-mode visual + WCAG-AA contrast,
font-scale 1.3×/1.5× reflow, backup→restore round-trip across reinstall, CSV export/import + factory
reset, live language switch (EN↔RU) and live theme switch without restart, App-Shortcut launch
(`monefy://add-expense/income/transfer`), R8 release-build walkthrough.

## 7. Definition of Done
- **Per phase:** every targeted screen has its happy/empty/error tests; the critical Pattern A flows for
  that phase exist; `:app:connectedDebugAndroidTest` (+ any touched `:core` module) is **green on
  `Pixel_5_API_34`** with 0 failed / 0 ignored; coverage matrix + PROGRESS.md updated.
- **Overall:** all 27 screens covered by instrumented tests; critical journeys (add expense, transfer
  with rate, onboarding→dashboard) pass E2E; Workers instrumented; `MANUAL_QA_CHECKLIST.md` authored and
  executed once; R8 release walk done. No assertion was weakened to get green.

## 8. Report format (output after each phase)
```
PHASE <n> — <name>
Tests added: <list of test classes + #test fns>
Ran: <gradle command>  → <X passed / 0 failed / 0 ignored> on Pixel_5_API_34
Report: app/build/reports/androidTests/connected/debug/index.html
Production code touched: <files + 1-line why, or "none">
Bugs found & fixed: <id/description, or "none">
Still open / escalations: <…>
```
