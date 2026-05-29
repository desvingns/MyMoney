# Device Verification Coverage Progress

This is the resumable tracker for the on-device remediation. The active execution
runbook is `docs/DEVICE_VERIFICATION_PLAN_FOR_SONNET.md` (step-by-step, for Claude
Sonnet 4.6); the original `docs/DEVICE_VERIFICATION_PLAN_FOR_CODEX.md` is kept for
historical rationale only. `docs/implementation_plan/PROGRESS.md` continues to own
phase status; this file tracks the fine-grained UI/control and worker test backlog
across sessions, and is updated one green test at a time (`/cmp --device <Sxx>` or by hand).

## Rules

- Add one instrumented test at a time, run it on `Pixel_5_API_34`, inspect the
  connected-test result, then mark it green here.
- Do not mark a row complete from assembly, JVM tests, or static inspection.
- Record any minimal production changes needed to expose stable semantics or fix
  a defect discovered by a device test.
- Keep Sentry, Firebase, and cloud sync disabled in instrumented runs unless a
  future test explicitly supplies controlled test doubles.

## Current Baseline

| Date | Result | Evidence |
|---|---|---|
| 2026-05-26 | `:app` connected tests green, 3/3 | Recorded in `PROGRESS.md`; `PeriodStripUiTest`, `SwipeToDeleteUiTest`, and `ExampleInstrumentedTest` present. |
| 2026-05-26 | `:core:designsystem` connected tests green, 3/3 | Recorded in `PROGRESS.md`; `MonefyDonutChartUiTest` present. |
| 2026-05-26 | `:core:database` connected tests green, 20/20 | Recorded in `PROGRESS.md`. |
| 2026-05-26 | `:core:datastore` connected tests green, 5/5 | Recorded in `PROGRESS.md`. |
| 2026-05-27 | New remediation session started | `adb` server starts, but no `emulator-5554` is currently listed; new test rows remain pending a real AVD run. |
| 2026-05-27 | Device access recovered | Verified `adb connect 10.0.2.2:5555` from the NAT-only guest; serial `10.0.2.2:5555` reports `Pixel_5_API_34`, API 34, boot complete. |
| 2026-05-27 | S11 interaction coverage green, 5/5 | `OnboardingContentUiTest` covers `Skip`, `Next`, `Get Started`, pager swipe, and indicator state on `Pixel_5_API_34`; report: `app/build/reports/androidTests/connected/debug/index.html`. |
| 2026-05-27 | S01/S04 empty-state controls green, 6/6 | `DashboardContentUiTest` confirms enabled Add Expense, Add Income, both Transfer affordances, Search, all five right-drawer destinations, and left-drawer Manage accounts on `Pixel_5_API_34`; report: `app/build/reports/androidTests/connected/debug/index.html`. |
| 2026-05-27 | S01 AS-2 balance card green, 7/7 suite | `DashboardContentUiTest` now confirms a populated balance pill emits `BalanceCardClicked`; full dashboard suite passed `7/7` on `Pixel_5_API_34`. |
| 2026-05-27 | S02 period controls green, 2/2 suite | `PeriodStripUiTest` confirms `Today`, `Week`, `Month`, `Year`, `All`, plus AS-12 custom range selection on `Pixel_5_API_34`. |
| 2026-05-27 | S06 stable direct controls green, 7/7 | `AddExpenseScreenUiTest` confirms keypad taps, backspace, category CTA disabled/enabled behavior, Back/Swap events, date selection, and note input on `Pixel_5_API_34`. |
| 2026-05-27 | S07 stable direct controls green, 7/7 | `AddIncomeScreenUiTest` confirms keypad taps, backspace, category CTA disabled/enabled behavior, Back/Swap events, date selection, and note input on `Pixel_5_API_34`. |
| 2026-05-27 | S03 direct-form controls green, 10/10 | `TransferScreenUiTest` confirms Back, initial disabled Save, keypad reveal/digit/backspace, note-focus dismissal/input, date selection, both account dropdowns, and visible rate `Change`, with keypad dismissal on alternative controls on `Pixel_5_API_34`; the complete BR-23 fix is in `9dea4d7`. |
| 2026-05-28 | S03 direct-form controls green, 12/12 | `TransferScreenUiTest` additionally covers enabled Save and tapping disabled Save to dismiss the keypad without emitting a transfer event; BR-23/save fixes are in `9dea4d7`, `4875891`, and `f831477`; native reviewer pass. |
| 2026-05-28 | S09 direct controls green, 3/3 | `CategoryPickerContentUiTest` covers Back, accessible `+ ADD`, and category selection with amount preview; FAB accessibility/layout and top-centre amount preview fixed in `14c683c`; native reviewer pass. |
| 2026-05-28 | S27 direct controls green, 5/5 | `CurrencyRateScreenUiTest` covers Back, disabled/enabled Save, rate input, valid preview/Save, invalid inline error, From/To rows, and localized preview; fixes are in `b269a67`; native reviewer pass. |
| 2026-05-28 | S08 direct controls green, 8/8 | `SearchContentUiTest` covers Back, query input, Clear, deterministic Voice launch, history chip, result row tap, empty-results, and error states; first run exposed a `FocusRequester` crash fixed in `SearchContent`; result row tap fixed to match TDD S08 AC4. |
| 2026-05-28 | S12 direct controls green, 5/5 | `TransactionsListContentUiTest` covers Back, Search, empty-state copy, category filter chip, and whole-row tap; row tap fixed to match TDD S12, and `:app` androidTest now has direct `paging-compose` for Pattern B tests. |
| 2026-05-28 | S13 direct controls green, 11/11 | `TransactionDetailContentUiTest` covers Back, hidden/visible Save, Delete, delete confirm/cancel, keypad/backspace/note edits, date selection, account selection, cross-currency target/rate edits, and snackbar error dismissal on `Pixel_5_API_34`. |
| 2026-05-28 | S14 direct controls green, 3/3 | `SettingsRootContentUiTest` covers Back, all seven destination rows, current Theme/Language labels, and Sound/Haptic switches on `Pixel_5_API_34`. |
| 2026-05-28 | S15 direct controls green, 2/2 | `ThemeSettingsContentUiTest` covers Back plus System, Light, and Dark selectable rows with selected-state and event assertions on `Pixel_5_API_34`. |
| 2026-05-28 | S18 direct controls green, 5/5 | `BackupRestoreContentUiTest` covers Back, DB/CSV export-import buttons, reset request, disabled in-progress state, reset confirm/cancel dialog, size label, and error banner on `Pixel_5_API_34`. |
| 2026-05-28 | S19 direct controls green, 2/2 | `LanguageContentUiTest` covers Back plus System, English, and Russian selectable rows with selected-state and event assertions on `Pixel_5_API_34`. |
| 2026-05-28 | S20 direct controls green, 2/2 | `AboutHelpContentUiTest` covers Back, visible version/attribution copy, and Privacy/Help/Licences callbacks on `Pixel_5_API_34`. |
| 2026-05-28 | S17 direct controls green, 6/6 | `CloudSyncContentUiTest` covers Back, provider Connect/Disconnect/Sync now states, auto-sync switch, error dismissal, and conflict timestamps plus Keep remote/Keep local buttons on `Pixel_5_API_34`; providers stay credential-gated. |
| 2026-05-28 | S16 setup direct controls green, 6/6 | `BiometricSetupContentUiTest` covers Back, enable switch, unavailable/not-enrolled states, idle-timeout menu, PIN setup keypad confirmation, and PIN backspace on `Pixel_5_API_34`; BiometricPrompt and runtime overlay remain route/runtime gaps. |
| 2026-05-27 | UTP-safe device runner established | Direct remote serial causes AGP 8.7.3 UTP profile-path failure; `scripts/run_connected_test_on_host_avd.ps1` proxies host ADB so Gradle uses `emulator-5554` and waits 60 seconds after each run. |

## Delivery Order

| Slice | Scope | Status | Device run/report |
|---|---|---|---|
| 0 | Pattern A infrastructure: Hilt runner, isolated database/settings, `MainActivity` launch gate | **Done** | `MainActivityLaunchTest` 1/1 green on `Pixel_5_API_34` 2026-05-29 (`e94d985`) |
| 1 | S00/S11/S01/S06 critical flow: onboarding -> dashboard -> add expense -> updated balance | **Done (core E2E)** | J1 Pattern A E2E green 1/1 2026-05-29 (`5e42f8d`); S11 5/5; S01/S04 + AS-2 7/7; S02 2/2; S06 stable controls 7/7. Two real defects fixed (`6767a58`, `5388264`). Error-banner seams remain Pattern B gaps. |
| 2 | Transaction forms S07/S03/S09/S27, including AS-4 and AS-6 paths | **Done (E2E)** | J2 cross-currency transfer (AS-6/AS-7) and J3 create-category (AS-4) E2E green 2026-05-29 (`cc8c30f`, `210c1f4`); S07 7/7, S03 12/12, S09 3/3, S27 5/5. Two real defects fixed (`beb64c0`, `566e8c4`). S09 long-press context actions + error seams remain Pattern B gaps. |
| 3 | Dictionaries S21-S26 CRUD and validation controls | Pending | - |
| 4 | List/detail/search/settings/lock/sync/backup plus worker instrumentation | In progress | S08 direct controls 8/8, S12 direct controls 5/5, S13 direct controls 11/11, S14 direct controls 3/3, S15 direct controls 2/2, S16 setup direct controls 6/6, S17 direct controls 6/6, S18 direct controls 5/5, S19 direct controls 2/2, and S20 direct controls 2/2 green 2026-05-28; S12 loading/error/filter-removal/undo, S16 BiometricPrompt/overlay runtime, provider/OAuth E2E pending. **Worker instrumentation done 4/4 (`155a5b2`).** |
| 5 | Manual QA, minified release walk, macrobenchmark/Baseline Profile | Pending | - |

## Screen Matrix

`Green` means a passing connected test on `Pixel_5_API_34`. A dated `Existing`
entry identifies coverage already recorded before this tracker was created.

| Screen / surface | Primary controls to exercise | Happy | Empty | Error | E2E / notes |
|---|---|:---:|:---:|:---:|---|
| S00 Splash | startup routing | Green via gate | n/a | n/a | `MainActivityLaunchTest` (Slice 0 gate) launches on fresh in-memory DB and routes Splash→Onboarding 1/1 green 2026-05-29; full J1 routing pending |
| S11 Onboarding | Skip, Next, Get Started, pager | Green: 5/5 | n/a | n/a | `OnboardingContentUiTest`, 5/5 green 2026-05-27; indicator semantic state added in `c3f74b1`, regression in `a0a53ea` |
| S01/S05 Dashboard | expense/income FABs, search, transfer, balance, donut slice, drawers | Partial green: controls + balance 7/7 | Empty-state controls green | Pending | `DashboardContentUiTest` 7/7 green 2026-05-27; AS-2 balance pill covered; donut semantics existing green 2026-05-26 in `:core:designsystem` |
| S02 Period drawer | period choices, Pick a date, apply range | Green: 2/2 suite | n/a | Pending | `PeriodStripUiTest` covers ordinary chips plus AS-12 custom range on 2026-05-27 |
| S04 Right drawer | Categories, Accounts, Currencies, Settings/About tiles | Green: five rows covered | n/a | n/a | `DashboardContentUiTest` right-drawer group green 2026-05-27 |
| S06 Add expense | back, swap, date, note, keypad keys/backspace, choose category | Partial green: stable controls 7/7 | Zero-amount category disabled green | Blocked by missing retry UI | `AddExpenseScreenUiTest` 7/7 green 2026-05-27; account-chip event seam missing; critical E2E pending |
| S07 Add income | back, swap, date, note, keypad/backspace, choose category | Partial green: stable controls 7/7 | Zero-amount category disabled green | Blocked by missing retry UI | `AddIncomeScreenUiTest` 7/7 green 2026-05-27; account-chip event seam missing; AS-4 E2E pending |
| S03 Transfer | back, account pickers, keypad, rate/change, save | Green: stable direct controls 12/12 | Disabled Save green | Pending | `TransferScreenUiTest` 12/12 green 2026-05-28; BR-23/save behavior fixed in `9dea4d7`, `4875891`, and `f831477` and re-reviewed green; AS-6/AS-7 E2E pending |
| S09 Category picker | category cell, add, back, context actions | Green: Back/+ADD/category cell 3/3 | Add visible in empty state green | n/a | `CategoryPickerContentUiTest` 3/3 green 2026-05-28; long-press Edit/Archive and AS-4 E2E pending |
| S27 Currency rate | amount input, save, back | Green: rate input/save/back 5/5 | n/a | Inline invalid-rate error green | `CurrencyRateScreenUiTest` 5/5 green 2026-05-28; localized preview and read-only From/To rows green; AS-6 return/inverse-rate E2E pending |
| S08 Search | back, query/clear, voice affordance, result row, chips | Green: back/query/clear/voice/chip/result row 8/8 | Empty-results green | Error message green | `SearchContentUiTest` 8/8 green 2026-05-28; focus crash and row-click TDD AC4 defect fixed; debounce remains JVM-covered |
| S12 Transactions list | search, filters, row, swipe/undo | Green: Back/Search/category chip/row tap 5/5 plus swipe 1/1 | Empty-state copy green | Pending | `TransactionsListContentUiTest` 5/5 green 2026-05-28; `SwipeToDeleteUiTest` existing green; whole-row tap fixed; loading/error/filter-removal/undo pending |
| S13 Detail/edit | back, delete/confirm/undo, edit/save, rate | Green: direct controls 11/11 | n/a | Snackbar error green | `TransactionDetailContentUiTest` 11/11 green 2026-05-28; covers pre-populated edit controls, delete dialog, inline transfer rate, and error dismissal; S13/S12 undo snackbar routing remains Pattern A |
| S14 Settings root | all destination rows, sound/haptic toggles | Green: direct controls 3/3 | n/a | n/a | `SettingsRootContentUiTest` 3/3 green 2026-05-28; covers Back, seven destination rows, current labels, Sound/Haptic switches |
| S15 Theme | System, Light, Dark rows | Green: direct controls 2/2 | n/a | n/a | `ThemeSettingsContentUiTest` 2/2 green 2026-05-28; covers Back, selected Dark row semantics, and System/Light/Dark selection events |
| S16 Lock setup/overlay | enable, timeout, PIN fallback, back blocking | Green: setup direct controls 6/6 | Unavailable/not-enrolled setup states green | Pending | `BiometricSetupContentUiTest` 6/6 green 2026-05-28; covers Back, enable switch, system-settings CTA, idle-timeout dropdown, PIN confirmation and backspace; BiometricPrompt launch/callbacks and runtime `LockOverlay` remain route/runtime instrumentation gaps |
| S17 Cloud sync | connect/disconnect, sync, auto-sync, conflict actions | Green: direct controls 6/6 | Provider-gated disconnected state green | Error dismissal green | `CloudSyncContentUiTest` 6/6 green 2026-05-28; covers Back, Connect enabled/disabled, disconnected Sync-now disabled, connected Disconnect/Sync-now, auto-sync switch, error dismiss, and conflict remote/local timestamps plus Keep remote/Keep local; OAuth/live cloud round-trips remain OQ-2/OQ-3 and worker sync-no-op remains integration |
| S18 Backup/Restore | export/import DB, export/import CSV, reset confirm | Green: direct controls 5/5 | n/a | Error banner green | `BackupRestoreContentUiTest` 5/5 green 2026-05-28; covers Back, DB/CSV export-import callbacks, reset request, disabled in-progress state, reset confirm/cancel, size label, and error banner; SAF picker and real file IO remain route/integration |
| S19 Language | System, English, Russian rows | Green: direct controls 2/2 | n/a | n/a | `LanguageContentUiTest` 2/2 green 2026-05-28; covers Back, selected Russian row semantics, and System/English/Russian selection events |
| S20 About/Help | privacy, help, licences, back | Green: direct controls 2/2 | n/a | Pending | `AboutHelpContentUiTest` 2/2 green 2026-05-28; covers Back, version/attribution copy, and Privacy/Help/Licences callbacks; AS-15 bundled WebView route remains Pattern A/E2E |
| S21 Categories | tabs, row/edit/archive, add, drag | Pending | Pending | Pending | Slice 3 |
| S22 Category edit | name, icon, colour, kind, save | Back control green | Pending | Pending | back-arrow defect fixed + tested (`90879bd`); ColorPicker render crash fixed (`566e8c4`); J3 creates a category here via S22; remaining field/save controls are Slice 3 Pattern B |
| S23 Accounts | row/edit/archive/delete/default, add | Pending | Pending | Pending | AS-13 pending |
| S24 Account edit | fields, currency, default, save | Back control green | Pending | Pending | back-arrow defect fixed + tested (`90879bd`); remaining controls + AS-13 are Slice 3 Pattern B |
| S25 Currencies | toggle, edit, manage rate, add | Pending | Pending | Pending | Slice 3 |
| S26 Currency edit | code/symbol/digits/active/save | Back control green | Pending | Pending | back-arrow defect fixed + tested (`90879bd`); remaining controls are Slice 3 Pattern B |
| Workers | recurring, prune, rotation, sync no-op | Green: 4/4 | n/a | Rotation guard green | `core:sync` `WorkerInstrumentationTest` 4/4 green 2026-05-29 (`155a5b2`): Recurring Room effect, Prune 30-day Room effect, Sync gated no-op, BackupRotation missing-URI failure; rotation success path needs real SAF |

## Session Log

### 2026-05-29 - Worker instrumentation + back-arrow escalation resolved (Opus)

- **All four daily workers green 4/4** (`core:sync` `WorkerInstrumentationTest`, `155a5b2`) via
  `HiltWorkerFactory` + `TestListenableWorkerBuilder` on a fresh in-memory DB (new `:core:sync`
  androidTest infra): RecurringWorker (a due active template generates one occurrence in Room,
  silent AS-11), PruneDeletedWorker (>30-day soft-deleted pruned, recent kept), SyncWorker
  (gated off ⇒ success no-op), BackupRotationWorker (missing tree URI ⇒ failure guard). The
  BackupRotation success/rotation path needs a real SAF tree and remains an integration gap.
- **Escalation resolved — back-arrow defect on S22/S24/S26 (`1a534c2`, test `90879bd`).** The edit
  screens' TopAppBar back-arrow emitted `SaveClicked` (silently persisting edits / tripping
  validation). Added a dedicated `BackClicked` event that emits `NavigateBack` without saving;
  `EditScreenBackControlUiTest` green 3/3 on `Pixel_5_API_34`. Memo `mymoney-edit-screen-backarrow-quirk`
  marked resolved.

### 2026-05-29 - J2 + J3 E2E green + two more real defects fixed (Opus)

- **J2 (cross-currency transfer) green 1/1** (`MainActivityTransferJourneyTest`,
  `cc8c30f`): with a second account in a different currency (inserted via repo after seeding),
  selecting the target auto-navigates to S27, the rate is captured, the form returns to S03, and
  saving stores the transfer as exactly ONE row carrying both legs (source/target accounts, amount,
  converted `toAmount`, `exchangeRate`) — AS-6 + AS-7, TDD §4.8.
- **J3 (create-category-from-picker, AS-4) green 1/1** (`MainActivityCreateCategoryJourneyTest`,
  `210c1f4`): from the expense form with amount 9 entered → Choose category → S09 → + ADD → S22
  create → Save returns past S09 with the new category pre-selected and the amount preserved → the
  saved expense carries the new category and amount 9.
- **Real defect #3 — transfer rate result not delivered (`beb64c0`).** Same root as the picker
  defect: S27 writes the rate signal into `previousBackStackEntry.savedStateHandle` but
  `TransferViewModel` observed its Hilt-injected handle, so the S03 rate preview never refreshed.
  Fixed by observing `pendingRate` in `TransferRoute` on the entry and forwarding a new
  `TransferEvent.PendingRateResolved`.
- **Real defect #4 — CategoryEdit (S22) crashed on render (`566e8c4`).** `ColorPicker` is a
  `LazyVerticalGrid` inside `Column(verticalScroll)`; an unbounded lazy grid in a vertical scroll is
  measured with infinite height → `IllegalStateException`, crashing S22/S24/S26 on open. Capped the
  grid's max height. Found by J3.

### 2026-05-29 - J1 critical E2E green + two real product defects fixed (Opus)

- **J1 (highest-value E2E) green 1/1 on `Pixel_5_API_34`** (`MainActivityAddExpenseJourneyTest`,
  `5e42f8d`): onboarding Skip → dashboard → `−` expense FAB → calculator `1 2 + 3 =` (⇒15) →
  Choose category → seeded "Food" → auto-save → pops to dashboard → balance pill shows the 15
  expense (AS-2, AS-4, TDD §4.6 AC6). Pill matched via a new `dashboard_balance_pill` testTag seam.
- **Real defect #1 — missing VIBRATE permission (`6767a58`).** `HapticPlayer.vibrate()` runs with
  `hapticEnabled=true` by default, but the manifest never declared `android.permission.VIBRATE`, so
  the first haptic (expense FAB tap) crashed with `SecurityException` on device. Declared the normal
  permission. This would crash the real app on first FAB tap.
- **Real defect #2 — category-picker result never delivered (`5388264`).** S09 writes the picked
  (and AS-4 created) category id into `previousBackStackEntry.savedStateHandle`, but `AddExpense`/
  `AddIncome` observed their **ViewModel-injected** `SavedStateHandle` — a different instance from the
  `NavBackStackEntry`'s — so the id never arrived and **choosing a category never saved the
  transaction** on a real device (core flow + AS-4 broken; never caught because it was only ever
  built/JVM-tested). Fixed by observing the result in the route on the destination
  `NavBackStackEntry` and forwarding the existing `CategoryPicked` event; VM unit tests now drive that
  event path. This unblocks J3 (AS-4) too.

### 2026-05-29 - Single-machine migration, loopback blocker resolved, Slice 0 gate green (Opus)

- **Run vehicle migrated to single machine.** VirtualBox is retired (user confirmed). The
  `Pixel_5_API_34` AVD now runs locally in Android Studio and appears directly as `emulator-5554`;
  the old `10.0.2.2:5037` NAT proxy is dead. `scripts/run_connected_test_on_host_avd.ps1` was
  rewritten to run Gradle directly against `emulator-5554` (no proxy; the serial has no `:` so the
  AGP UTP path bug does not apply). `mymoney-device-connection` memo updated.
- **The "Windows loopback blocker" is RESOLVED — it was never corporate AV.** Root cause: the JDK
  NIO selector self-pipe binds an AF_UNIX socket under `java.io.tmpdir`; the default `%TEMP%`
  resolves to the 8.3 short name `C:\Users\K020B~1.SHA\...` (the dot in username `k.shavrin` forces a
  short name), and AF_UNIX `connect()` to a socket file there fails with WSAEINVAL → Gradle "Unable
  to establish loopback connection". Proven in pure JDK with a 4-line `Selector.open()` program on
  JBR 21 and JDK 17. Fix: point `TMP`/`TEMP`/`jdk.net.unixdomain.tmpdir` at a clean short dir
  (`D:\gradletmp`); baked into the helper. `gradle help` then `BUILD SUCCESSFUL`. Local builds/tests
  now run for real on this host (PHASE_01–09 inspection-only ticks can be reconfirmed when touched).
  Also pinned the local Microsoft JDK 17 toolchain path for the `jvmToolchain(17)` JVM modules
  (foojay auto-download fails on this host). Env facts: JBR 21 `D:\For_work\AS\jbr`, Studio
  `D:\For_work\AS`, SDK `%LOCALAPPDATA%\Android\Sdk`.
- **Slice 0 Pattern A infrastructure built and gate is green (`e94d985`).** Added
  `hilt-android-testing`/`navigation-testing`/`work-testing` to the version catalog;
  `testInstrumentationRunner = HiltTestRunner`; androidTest deps (hilt-android-testing + kspAndroidTest
  hilt-compiler, navigation/work-testing, coroutines-test, turbine, datastore-preferences,
  room-runtime, projects core:{database,datastore,domain,common}). `HiltTestRunner` swaps in
  `HiltTestApplication`; `TestDatabaseModule` (`@TestInstallIn` replacing `DatabaseModule`) provides an
  in-memory `MoneyDatabase` mirroring every real DAO `@Provides`; `TestDataStoreModule` replaces the
  DataStore provider with a UUID-named per-run file (fresh `onboardingCompletedAt` per test).
  `MainActivityLaunchTest` (`@HiltAndroidTest` + `createAndroidComposeRule<MainActivity>()`) launches
  the app on a fresh in-memory DB and asserts the onboarding root (`Skip`) renders. JUnit XML:
  `tests=1 failures=0 errors=0 skipped=0` on `Pixel_5_API_34`. WorkManager is not touched by the gate
  (manifest removes the default initializer; `MyMoneyApp.onCreate` does not run under
  `HiltTestApplication`) — Worker instrumentation will init test WorkManager separately.

### 2026-05-27 - Setup and audit

- Read the active PHASE_15 state, device-verification plan, TDD section 4,
  section 12, and section 14.1 decisions.
- Confirmed current instrumented UI coverage is limited to
  `PeriodStripUiTest`, `SwipeToDeleteUiTest`, and
  `MonefyDonutChartUiTest`, plus the package smoke test.
- Found that Pattern A critical-path infrastructure has not yet been scaffolded:
  `:app` still uses `AndroidJUnitRunner` and has no Hilt Android testing setup.
- Identified the first concrete interaction defect to cover: TDD section 4.1
  AC2 requires a `Skip` button on onboarding, but `OnboardingContent` currently
  exposes only `Next` / `Get started` and has no `onboarding_skip` resource.
  The first CMP bugfix slice should add the control plus a device-run
  regression test once the AVD is visible.
- Device execution was unblocked later in the session by connecting through
  VirtualBox NAT: `adb connect 10.0.2.2:5555`. The guest reports the host
  `Pixel_5_API_34` AVD under serial `10.0.2.2:5555`.
- Fixed the S11 AC2 product defect in `09be388` by adding a right-aligned
  resource-backed `Skip` button that invokes the existing completion callback;
  EN/RU resources were added with it. Regression test commit: `9f5a632`.
- Direct Gradle execution against guest serial `10.0.2.2:5555` failed before
  test execution because AGP 8.7.3 UTP cannot write its profile filename on
  Windows when the serial contains `:`. `ADB_SERVER_SOCKET` alone did not
  redirect UTP/DDMLib. A localhost TCP proxy to the host ADB server exposed
  safe serial `emulator-5554`; with that transport the single new test ran
  green: `1 passed / 0 failed / 0 skipped`. A 60-second pause followed every
  attempted automated-test invocation.
- Added interaction coverage commits `a8ebcdf` (`Next` advances to slide 2
  without completing), `df13b6c` (`Get Started` on slide 4 completes), and
  `58fe817` (swipe advances without completing). Re-running
  `OnboardingContentUiTest` through the helper passed `4/4` with `0` failed
  and `0` skipped on `Pixel_5_API_34`; the helper's post-run 60-second wait
  was observed on each execution. The pager-dot visual state is not
  semantically addressable yet; cover it when the accessibility/testability
  contract is defined.
- Added an accessible semantic contract for the pager indicator in `c3f74b1`
  (localized current-page state, visuals unchanged) and a single AC3
  instrumentation regression in `a0a53ea`. Scoped connected execution through
  the host-AVD helper passed `5/5` with `0` failed/skipped on
  `Pixel_5_API_34`; the required 60-second post-run pause completed. S11 is
  green for its listed interactive/indicator contract.
- Started S01/S05 Pattern B coverage in `78f6646`, `8e57180`, `29917c9`,
  `34806c0`, `8898f69`, `484205a`, and `ba67c2e`
  with direct-render empty-dashboard tests for enabled Add Expense, Add
  Income, both Transfer affordances, Search, and all five right-drawer rows.
  An initial right-drawer assertion collided with the non-clickable left
  `Accounts` heading; the fixed matcher filters click actions. Scoped device
  execution passed `6/6`, including the left-drawer `Manage accounts` action in
  `b972ef2`,
  with `0` failed/skipped on `Pixel_5_API_34`, after the helper completed its
  required 60-second pause. Data-driven controls are pending.
- Added populated-dashboard AS-2 coverage in `49868ca`: tapping the visible
  balance pill emits `DashboardEvent.BalanceCardClicked`. The scoped
  `DashboardContentUiTest` suite passed `7/7` with `0` failed/skipped on
  `Pixel_5_API_34`, after the helper completed its required 60-second pause.
- Added the five ordinary S02 period-chip callbacks in `4f35410` alongside the
  existing AS-12 `Pick a date` range selection test. Scoped `PeriodStripUiTest`
  execution passed `2/2` with `0` failed/skipped on `Pixel_5_API_34`, after
  the helper completed its required 60-second pause.
- Started S06 form coverage in `b7b77d3`, `4f378d6`, `fcd0222`, `38b8d10`,
  `f1742c2`, `9b79e63`, and `4172c42`: direct keypad taps for
  `1`, `2`, `+`, `3`, and `=` plus Backspace dispatch the expected
  `AddExpenseEvent` sequence; the `Choose category` CTA is disabled at zero
  and dispatches its event when the amount is positive; Back/Swap, date
  selection, and note input emit their events. Scoped `AddExpenseScreenUiTest` passed `7/7` with `0`
  failed/skipped on `Pixel_5_API_34`, after the helper completed its required
  60-second pause. The shared account chip currently maps to `Unit`, and the
  specified error retry action is not present in the S06 UI/event contract;
  those require production decisions before they can be marked green.
- Added S07 form coverage in `0f7512f`, `71525f7`, `0d4528b`, `64ca338`,
  `9550631`, `bcb127f`, and `e06289a`: direct keypad taps and backspace,
  disabled/enabled `Choose category`, Back/Swap, date selection, and note
  input emit the expected `AddIncomeEvent` contracts. Scoped
  `AddIncomeScreenUiTest` passed `7/7` with `0` failed/skipped on
  `Pixel_5_API_34`, after the helper completed its required 60-second pauses.
  The shared account chip currently maps to `Unit`, and the specified error
  retry action is not present in the S07 UI/event contract; AS-4 navigation
  remains an E2E slice.
- Continued S03 form coverage in `7d70c4d`, `35fa5df`, `b8924e2`,
  `17b2117`, `9e624bf`, `9dea4d7`, `8525ac2`, `7e26544`, `62e9c85`,
  `7c93b50`, `3e4adf1`, and `c82a4b5`: Back dispatches
  `TransferEvent.BackClicked`, Save is disabled for the empty initial state,
  amount focus reveals keypad digit/backspace controls, and moving focus to
  the note field hides the keypad and typing there emits
  `TransferEvent.NoteChanged`; selecting a date hides a revealed keypad and
  emits `TransferEvent.DateChanged`; selecting either account hides the
  keypad and emits `TransferEvent.SourceAccountChanged` or
  `TransferEvent.TargetAccountChanged`; the visible cross-currency rate
  `Change` button hides the keypad and emits `TransferEvent.ChangeRateClicked`.
  Native review caught that the first
  visibility fix was a one-way latch against BR-23; `9dea4d7` replaces it
  with amount-focus state plus dismissal for alternative form interactions,
  and the reviewer rechecked it without findings. Scoped
  `TransferScreenUiTest` passed `10/10`, and S06/S07 shared-component
  regression reruns passed `7/7` each, all with `0` failed/skipped on
  `Pixel_5_API_34`. An earlier S03 compile attempt failed due to an invalid
  assertion imports; `17b2117` and `7c93b50` corrected them. The first
  8-test device execution hit an emulator `HardwareRenderer` teardown
  watchdog in an unchanged previously green test; an unchanged rerun passed
  `8/8`. Every attempt still completed the helper's required 60-second pause.

### 2026-05-28 - Transaction-form direct controls

- Closed remaining S03 direct-control gaps in `4875891` and `f831477`:
  enabled Save now emits `TransferEvent.SaveClicked`, and tapping disabled Save
  dismisses a revealed keypad without emitting a transfer event. Scoped
  `TransferScreenUiTest` passed `12/12` with `0` failed/skipped on
  `Pixel_5_API_34`; the helper completed its required 60-second pause.
- Added S09 direct controls in `14c683c`: Back emits its event, the
  accessible `+ ADD` action emits `AddCategoryClicked`, and a category cell
  preserves the top-centre amount preview while emitting `CategoryClicked`.
  Native review caught the amount-preview alignment after moving the FAB into
  the `Scaffold` slot; the follow-up fix was re-reviewed without findings.
  Scoped `CategoryPickerContentUiTest` passed `3/3` with `0` failed/skipped on
  `Pixel_5_API_34`; the helper completed its required 60-second pause.
- Added S27 direct controls in `b269a67`: Back, disabled Save, rate input,
  valid preview/Save, invalid inline error, read-only From/To rows, and the
  localized rate pattern are covered. Native review caught the missing
  From/To rows and hardcoded preview pattern; both were fixed and re-reviewed
  without findings. Scoped `CurrencyRateScreenUiTest` passed `5/5` with `0`
  failed/skipped on `Pixel_5_API_34`; one earlier invalid-import compile
  attempt also completed the mandatory 60-second pause.
- Remaining transaction-form gaps: AS-4 navigation E2E, AS-6/AS-7 transfer/rate
  E2E, S09 long-press Edit/Archive context actions, and transaction error
  retry seams that are not exposed in the current screen contracts.

### 2026-05-28 - S08 search direct controls

- Added `SearchContentUiTest` for S08 Pattern B coverage: Back emits
  `SearchEvent.BackClicked`, typing emits `QueryChanged`, Clear emits
  `QueryCleared`, the deterministic voice affordance launches the supplied
  voice callback, a history chip emits `SuggestionClicked`, tapping a result
  row emits `ResultClicked`, and empty-results/error copy is visible.
- The first scoped device run failed `8/8` with `FocusRequester is not
  initialized`; `SearchContent` now requests focus after the first frame, which
  preserves BR-18 while avoiding the launch-time crash.
- The slice also fixed TDD S08 AC4: the whole result row is clickable, not only
  the trailing chevron. The final scoped `SearchContentUiTest` run passed `8/8`
  with `0` failed/skipped on `Pixel_5_API_34`; every run used the host-AVD
  helper and completed its required 60-second pause.

### 2026-05-28 - S12 transactions-list direct controls

- Added `TransactionsListContentUiTest` for S12 Pattern B coverage: Back invokes
  the route callback, Search invokes the search callback, empty-state copy is
  visible, a category filter chip is rendered above rows, and tapping the row
  content emits `TransactionsListEvent.RowClicked`.
- Fixed a real TDD S12 row-tap gap by making the whole transaction row
  clickable, not only the trailing chevron. `:app` androidTest now declares
  direct `paging-compose` access so Pattern B tests can collect
  `LazyPagingItems`.
- Early attempts exposed two test-harness issues: missing androidTest
  `paging-compose` on the app classpath, and an empty `PagingData.from` source
  that never settled in instrumentation. The final helper uses a small
  `Pager`/`PagingSource` and the scoped run passed `5/5` with `0`
  failed/skipped on `Pixel_5_API_34`.
- S12 remaining gaps: visible loading/error states, filter-removal behavior,
  and AS-9 undo snackbar routing still need production/testability seams or a
  Pattern A test.

### 2026-05-28 - S13 transaction-detail direct controls

- Added `TransactionDetailContentUiTest` for S13 Pattern B coverage: Back,
  hidden Save for a clean detail, visible Save for a dirty detail, Delete,
  delete confirm/cancel, keypad digit/backspace, note input, date selection,
  account selection, cross-currency transfer target selection, inline rate
  input, and snackbar error dismissal are covered.
- Early scoped attempts exposed harness-only issues: two invalid Compose test
  imports, a double `setContent` call in one test, a target-account dropdown
  matcher that opened the wrong row, and a snackbar assertion that could hold
  Compose idleness until timeout. The final harness captures and dismisses the
  snackbar while asserting both the message and `DismissError`.
- Scoped `TransactionDetailContentUiTest` passed `11/11` with `0`
  failed/skipped on `Pixel_5_API_34` through the host-AVD helper; the helper
  completed its required 60-second pause. Native reviewer recheck passed
  without findings.
- Remaining S13 gap: the S13 delete undo snackbar is shared with S12 behavior
  and still belongs to a Pattern A route/snackbar-host test.

### 2026-05-28 - S14 settings-root direct controls

- Added `SettingsRootContentUiTest` for S14 Pattern B coverage: Back invokes
  the settings back callback; Theme, App lock, Sync, Backup & Restore,
  Language, About & Help, and Open-source licences rows invoke their
  destination callbacks; current Dark/Russian trailing labels render; and the
  Sound/Haptic switches emit `SettingsEvent.SoundToggled(false)` and
  `SettingsEvent.HapticToggled(true)` from a controlled state.
- The scoped run passed `3/3` with `0` failed/skipped on `Pixel_5_API_34`
  through the host-AVD helper; the XML report is
  `app/build/outputs/androidTest-results/connected/debug/TEST-emulator-5554 - 14-_app-.xml`.
- Native reviewer subagents were unavailable for the final switch addition due
  quota, so the final review was local; the earlier S14 back and row tests had
  native reviewer pass before the quota error.

### 2026-05-28 - S15 theme direct controls

- Added `ThemeSettingsContentUiTest` for S15 Pattern B coverage: Back invokes
  the theme-settings back callback; the Dark row is selected from a controlled
  state while Light/System are unselected; and tapping System, Light, and Dark
  emits `ThemeSettingsEvent.ModeSelected` with the expected `ThemeMode`.
- The first scoped run passed `1/1`; the second scoped attempt caught a bad
  androidTest import before execution, then the corrected scoped run passed
  `2/2` with `0` failed/skipped on `Pixel_5_API_34` through the host-AVD
  helper. Native reviewer passed with no violations.

### 2026-05-28 - S18 backup/restore direct controls

- Added `BackupRestoreContentUiTest` for S18 Pattern B coverage: Back invokes
  the backup/restore back callback; idle DB export/import, CSV export/import,
  and reset-request buttons are enabled and invoke the expected callbacks; all
  five actions are disabled while `inProgress` is true; the reset confirmation
  dialog renders and invokes cancel/confirm callbacks; the database-size label
  uses Android's `Formatter`; and the error banner renders from state.
- Scoped runs passed `1/1`, `2/2`, `3/3`, `4/4`, then `5/5` with `0`
  failed/skipped on `Pixel_5_API_34` through the host-AVD helper. Native
  reviewer passed with no violations.
- Remaining S18 gaps: SAF picker launch/results and real DB/CSV file IO remain
  route/integration-level tests.

### 2026-05-28 - S19 language direct controls

- Added `LanguageContentUiTest` for S19 Pattern B coverage: Back invokes the
  language-settings back callback; the Russian row is selected from a controlled
  state while English/System are unselected; and tapping System, English, and
  Russian emits `LanguageEvent.LanguageSelected` with the expected
  `AppLanguage`.
- The first scoped run passed `1/1`; after adding the row-selection test, the
  scoped run passed `2/2` with `0` failed/skipped on `Pixel_5_API_34` through
  the host-AVD helper. Native reviewer passed with no violations.

### 2026-05-28 - S20 about/help direct controls

- Added `AboutHelpContentUiTest` for S20 Pattern B coverage: Back invokes the
  about/help back callback; formatted version and attribution copy render; and
  tapping Privacy policy, Help, and Open-source licences invokes the expected
  callbacks in order.
- The first scoped run passed `1/1`; the second scoped attempt caught a bad
  androidTest import before execution, then the corrected scoped run passed
  `2/2` with `0` failed/skipped on `Pixel_5_API_34` through the host-AVD
  helper. Native reviewer passed with no violations.
- Remaining S20 gap: AS-15 bundled privacy/help WebView routing and asset
  loading still needs a route-level Pattern A/E2E test.

### 2026-05-28 - S17 cloud sync direct controls

- Added `CloudSyncContentUiTest` for S17 Pattern B coverage: Back emits
  `CloudSyncEvent.BackClicked`; disconnected provider cards cover enabled and
  disabled Connect plus disabled Sync now; connected provider cards cover
  Disconnect and Sync now for Dropbox and Google Drive; the auto-sync switch
  emits `AutoSyncToggled`; the error banner close button emits `DismissError`;
  and the conflict dialog renders remote/local timestamps and emits Keep remote
  / Keep local.
- The first scoped run passed `1/1`; the expanded run first caught a bad
  androidTest import before execution, then the corrected scoped run passed
  `6/6` with `0` failed/skipped on `Pixel_5_API_34` through the host-AVD
  helper. Native reviewer caught the missing US-17 AC1 timestamps; after the
  production/test fix, the scoped suite passed `6/6` again and
  `:feature:cloudsync:testDebugUnitTest :feature:cloudsync:assembleDebug`
  passed locally. Reviewer-noted selector fragility was removed with
  provider-specific test tags for duplicate Connect/Disconnect/Sync-now labels
  and the auto-sync switch. Re-review subagent was unavailable due usage limits,
  so the corrected slice was locally reviewed.
- Remaining S17 gaps: provider credentials/OAuth and live cloud round-trips
  remain OQ-2/OQ-3; sync-worker no-op behavior remains a WorkManager
  integration test.

### 2026-05-28 - S16 lock setup direct controls

- Added `BiometricSetupContentUiTest` for S16 setup Pattern B coverage: Back
  invokes the setup callback; the available enable switch emits
  `ToggleChanged(true)`; no-hardware and not-enrolled states disable the switch;
  the not-enrolled text opens system settings; the idle-timeout row opens the
  menu and emits `IdleTimeoutSelected(120)`; and the PIN setup dialog confirms a
  four-digit PIN while the backspace key participates in the entered value.
- Added stable test tags for the setup switch and timeout row, plus an
  accessible content description for the PIN keypad backspace control. The
  first scoped run passed `1/1`; after expanding coverage, the scoped suite
  passed `6/6` with `0` failed/skipped on `Pixel_5_API_34` through the
  host-AVD helper. `:feature:lockscreen:testDebugUnitTest
  :feature:lockscreen:assembleDebug` also passed locally; unit reports show
  46 tests, 0 failures.
- Remaining S16 gaps: real `BiometricPrompt` launch/callback behavior and
  runtime `LockOverlay` unlock/back-blocking need route/runtime instrumentation.
