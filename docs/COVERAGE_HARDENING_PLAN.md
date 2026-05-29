# Coverage Hardening Plan — drive remaining bugs to ~0% with autotests

Owner: Opus (cmp). Goal: **maximum autotest coverage and near-zero possible bugs.** Every bug is
fixed with an **inline minimal correct change + device verification**. The plan ends with a **full
run of every autotest (all JVM unit + all instrumented/E2E)**.

This file is the resumable source of truth for this effort. `docs/DEVICE_VERIFICATION_PROGRESS.md`
remains the per-screen matrix; this file is the ordered execution checklist.

---

## How to resume (read this first every session)

1. Confirm the device: `adb devices` → `emulator-5554 device`; AVD `Pixel_5_API_34`, boot complete.
   If absent → STOP, ask the user, update memo `mymoney-device-connection`.
2. Open this file, find the **first unchecked `[ ]` task** in the lowest unfinished Phase, do exactly
   that one, then come back and tick it.
3. Run vehicle (ALWAYS): `powershell -NoProfile -ExecutionPolicy Bypass -File
   .\scripts\run_connected_test_on_host_avd.ps1 -TestClass '<FQN>'` (add `-Tasks ':core:sync:connectedDebugAndroidTest'`
   for `:core:sync`). Trust the parsed JUnit XML (`tests=N failures=0 errors=0 skipped=0`), not
   "BUILD SUCCESSFUL". Reports:
   `app/build/outputs/androidTest-results/connected/debug/TEST-*.xml` (and the `core/sync` equivalent).
4. Env for any direct gradle call: `JAVA_HOME=D:\For_work\AS\jbr`, `TMP=TEMP=D:\gradletmp`,
   `GRADLE_OPTS=-Djdk.net.unixdomain.tmpdir=D:\gradletmp`, and pass
   `-Porg.gradle.java.installations.paths=C:\Users\k.shavrin\AppData\Local\Programs\Microsoft\jdk-17.0.10.7-hotspot
   -Porg.gradle.java.installations.auto-download=false` (the helper already does all of this).

## Iron rules (always)

- One test → run on `Pixel_5_API_34` → green → commit (`test:`/`fix:`/`feat:`) → tick here. Never batch blind.
- **Never weaken a test** (no `@Ignore`, no deleted assertions, no `assertTrue(true)`).
- **Missing-seam policy:** to make a control testable you may add ONLY one of: a `Modifier.testTag`,
  a `contentDescription`, or change a `*Content` to `public`. Never invent UI/events/features. If a
  control genuinely has no production seam, SKIP + log it in §Re-escalations with a reason.
- Real defect → fix the **production** code with the smallest correct change, device-verify, commit
  as its own `fix:`, and record it in §Defects fixed. Keep Sentry/Firebase/sync OFF.
- Do NOT push. Summarize at the end; the user pushes.
- Strings via `targetString(R.string.…)`, never literals (EN+RU). Determinism: `MyMoneyTheme { }`,
  assert on `runOnIdle`/`waitUntil`, seed dates relative to `now()`.

## Definition of done

All Phase A–E tasks checked (or SKIP-logged with a reason), §Final Full Run recorded green, this file
+ `DEVICE_VERIFICATION_PROGRESS.md` + `PHASE_15` updated, end-of-run summary written.

---

## Phase A — proactive defect-class sweep (find the bug *classes* before they bite)

The 5 device defects already fixed clustered into classes. Eradicate each class everywhere before
adding more tests.

- [x] **A1. nav-result via ViewModel SavedStateHandle.** `grep savedStateHandle.getStateFlow` in
  `**/src/main` → must be 0 (all moved to route + `NavBackStackEntry`). Verified 0 matches 2026-05-29.
- [x] **A2. Lazy-in-verticalScroll crash.** Cross-checked the 9 Lazy files vs 11 `verticalScroll`
  files: only `ColorPicker` overlapped (fixed `566e8c4`); list screens use a root `LazyColumn`;
  `IconPickerSheet` is sheet-bounded (confirm render in B-S22-icon).
- [ ] **A3. Runtime-permission APIs vs manifest.** Audit `getSystemService`/`Vibrator`/`BiometricPrompt`/
  camera/record usages and confirm each needed permission is declared (VIBRATE done `6767a58`).
  Fix any missing declaration inline. Record result here.
- [ ] **A4. Unsafe `!!` / `.first()` on possibly-empty flows in UI/VM init paths** that could crash on a
  fresh DB. List suspects; fix only genuine crash risks with a minimal guard. Record result.

## Phase B — Dictionaries CRUD device coverage (S21–S26): the biggest untested block

Pattern B on each public `*Content` (createComposeRule). Each row = one device-green test (a screen
may need 2–3 tests for happy/empty/error — split as needed and add sub-checkboxes). Fix every defect
found inline.

### S21 Categories list — `CategoriesListContentUiTest`
- [ ] B-S21-happy: FAB `dictionaries_add` → `AddClicked`; a category row → `ItemClicked(id)`; back → `BackClicked`; expense/income tab switch shows the right list.
- [ ] B-S21-empty: `expense=[],income=[]` renders, FAB enabled.
- [ ] B-S21-skip: archive/unarchive + drag-`Reordered` — confirm seam; if none, SKIP + log (drag stays JVM-covered).

### S22 Category edit — `CategoryEditContentUiTest`
- [ ] B-S22-fields: name → `NameChanged`; kind chip → `KindChanged`; colour swatch → `ColorChanged`; Save → `SaveClicked`.
- [ ] B-S22-icon: open icon button → `IconPickerSheet` renders (verify no Lazy-in-scroll crash) → pick → `IconChanged`.
- [ ] B-S22-error: `errorMessage` set → inline error visible; `blockedDeleteCount=n` (edit mode) → blocked dialog + `BlockedDeleteDismissed`.

### S23 Accounts list — `AccountsListContentUiTest`
- [ ] B-S23-happy: FAB → `AddClicked`; row → `ItemClicked(id)`; back → `BackClicked`; populated row shows name+balance+currency.
- [ ] B-S23-empty: `rows=[]` renders, FAB enabled. (Default-account chip / delete are not on this list → SKIP + log.)

### S24 Account edit — `AccountEditContentUiTest` (AS-13)
- [ ] B-S24-fields: name → `NameChanged`; currency dropdown → `CurrencyChanged(id)`; initial balance → `InitialBalanceChanged`; type → `TypeChanged`; default toggle → `IsDefaultChanged`; Save → `SaveClicked`.
- [ ] B-S24-error: **AS-13** `blockedDeleteCount=2` → dialog with count, OK → `BlockedDeleteDismissed`, no `DeleteClicked` re-fire; currency-locked `errorMessage` inline.

### S25 Currencies list — `CurrenciesListContentUiTest`
- [ ] B-S25-happy: FAB → `AddClicked`; row → `ItemClicked(id)`; active switch → `ActiveToggled(id,active)`; back → `BackClicked`; seeded list shows code/symbol/name.
- [ ] B-S25-empty: `currencies=[]` renders. (In-use-cannot-disable is VM logic; if no Content seam → SKIP + log.)

### S26 Currency edit — `CurrencyEditContentUiTest`
- [ ] B-S26-fields: code → `CodeChanged`; symbol → `SymbolChanged`; name → `NameChanged`; decimals → `DecimalDigitsChanged`; active toggle → `IsActiveChanged`; Save → `SaveClicked`.
- [ ] B-S26-locked: `isCodeLocked=true` → code field disabled/read-only (PHASE_09 code-lock).
- [ ] B-S26-error: invalid `^[A-Z]{3}$` `errorMessage` inline; `blockedDeleteCount` → blocked dialog + `BlockedDeleteDismissed`.

## Phase C — close remaining seam gaps where a production seam exists

- [ ] C1. **S00 Splash content** — `SplashContentUiTest`: `SplashContent()` renders logo/progress (routing is covered by J1).
- [ ] C2. **S12 loading/error states** — if `TransactionsListState` exposes loading/error, cover them in `TransactionsListContentUiTest`; else SKIP + log.
- [ ] C3. **S12 filter-removal** — if a remove-filter control/event exists, cover it; else SKIP + log.
- [ ] C4. **S09 long-press Edit/Archive** — confirm a `CategoryPickerEvent` seam; if none, SKIP + log (escalate).
- [ ] C5. **S06/S07 account chip** — currently maps to `Unit` (no event). No seam → SKIP + log (escalate as a missing feature, not a bug).

## Phase D — re-escalations (genuinely need external resources; NOT fixable inline here)

Record each with the reason it cannot be device-tested without external setup. These are NOT counted
as open bugs.

- [ ] D1. S16 real `BiometricPrompt` launch/callbacks + runtime `LockOverlay` unlock/back-blocking — needs enrolled biometric + runtime overlay harness.
- [ ] D2. S17 provider OAuth + live Dropbox/Drive round-trips — needs OQ-2/OQ-3 credentials.
- [ ] D3. BackupRotation success/rotation + S18 SAF picker + real DB/CSV IO — needs a real SAF tree.
- [ ] D4. S20 AS-15 bundled privacy/help WebView route + asset load — Pattern A route test (optional follow-up).
- [ ] D5. S13/S12 undo snackbar routing — Pattern A route/snackbar-host test (optional follow-up).

## Phase E — FINAL FULL RUN (do last; everything must be green)

- [ ] E1. All JVM unit tests, every module: `gradlew testDebugUnitTest` (+ pure-JVM `:core:domain:test :core:common:test`). Parse every report; record total `tests/failures`.
- [ ] E2. All instrumented suites on `Pixel_5_API_34`, every module with androidTest: `:app`, `:core:designsystem`, `:core:database`, `:core:datastore`, `:core:sync`. Parse every report.
- [ ] E3. Explicitly re-run ALL E2E journeys + gate green together: `MainActivityLaunchTest`, `MainActivityAddExpenseJourneyTest` (J1), `MainActivityTransferJourneyTest` (J2), `MainActivityCreateCategoryJourneyTest` (J3), `WorkerInstrumentationTest`.
- [ ] E4. Record the final totals in §Final Full Run; update `DEVICE_VERIFICATION_PROGRESS.md` + `PHASE_15`; write the end summary.

---

## Defects fixed (append one line each)

- (from prior session) VIBRATE permission `6767a58`; picker nav-result `5388264`; transfer rate nav-result `beb64c0`; ColorPicker crash `566e8c4`; edit back-arrow `1a534c2`.

## Re-escalations (append one line each, with reason)

- (none yet)

## Final Full Run (filled in at E4)

- (pending)

## Progress log (append dated one-liners)

- 2026-05-29 — plan created; Phase A audit A1/A2 verified clean. Starting A3/A4 then Phase B.
