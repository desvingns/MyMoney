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
- [x] **A3. Runtime-permission APIs vs manifest.** Only `Vibrator` (→ `VIBRATE`, declared `6767a58`)
  and `BiometricPrompt` (→ `USE_BIOMETRIC`, already declared) are permission-gated; no camera/record.
  Clean 2026-05-29.
- [x] **A4. Unsafe `!!` / `.first()` on possibly-empty flows.** `grep '!!'` across all `**/src/main`
  → 0 matches; `.first()` usages are `Flow.first()` (return the emitted list, safe on empty DB).
  No crash-risk found 2026-05-29.

## Phase B — Dictionaries CRUD device coverage (S21–S26): the biggest untested block

Pattern B on each public `*Content` (createComposeRule). Each row = one device-green test (a screen
may need 2–3 tests for happy/empty/error — split as needed and add sub-checkboxes). Fix every defect
found inline.

### S21 Categories list — `CategoriesListContentUiTest`
- [x] B-S21-happy: FAB `dictionaries_add` → `AddClicked`; a category row → `ItemClicked(id)`; back → `BackClicked`; both Expense+Income sections render (no tabs — sections stacked). Green 2/2.
- [x] B-S21-empty: `expense=[],income=[]` renders, FAB enabled. Green.
- [x] B-S21-skip: SKIP+log — `CategoriesListEvent` has no archive/unarchive (archive lives on S22 edit); drag-`Reordered` stays JVM-covered (PHASE_09 reorder tests).

### S22 Category edit — `CategoryEditContentUiTest`
- [x] B-S22-fields: name → `NameChanged`; kind chip → `KindChanged`; colour swatch → `ColorChanged`; Save → `SaveClicked`. Green 3/3.
- [x] B-S22-icon: open icon button → `IconPickerSheet` renders (no Lazy-in-scroll crash confirmed) → pick → `IconChanged`. Green.
- [x] B-S22-error: `errorMessage` set → inline error visible; `blockedDeleteCount=n` (edit mode) → blocked dialog + `BlockedDeleteDismissed`. Green.

### S23 Accounts list — `AccountsListContentUiTest`
- [x] B-S23-happy: FAB → `AddClicked`; row → `ItemClicked(id)`; back → `BackClicked`; populated row shows name+balance+default badge. Green. (Row tap: click the name via `useUnmergedTree` — merged-row centre lands on the trailing AssistChip.)
- [x] B-S23-empty: `rows=[]` renders, FAB enabled. Green. (Default-account chip / delete are not on this list → SKIP + log.)

### S24 Account edit — `AccountEditContentUiTest` (AS-13)
- [x] B-S24-fields: name → `NameChanged`; currency dropdown → `CurrencyChanged(id)`; initial balance → `InitialBalanceChanged`; type → `TypeChanged`; default toggle → `IsDefaultChanged`; Save → `SaveClicked`. Green. (Currency dropdown driven first with keyboard down; Switch found via `isToggleable()`; colour picker untouched so no lazy-grid scroll.)
- [x] B-S24-error: **AS-13** `blockedDeleteCount=2` → dialog, OK → `BlockedDeleteDismissed`, no `DeleteClicked` re-fire (asserted events == [BlockedDeleteDismissed]); `currency_required` `errorMessage` inline. Green.

### S25 Currencies list — `CurrenciesListContentUiTest`
- [x] B-S25-happy: FAB → `AddClicked`; row → `ItemClicked(id)`; active switch → `ActiveToggled(id,active)`; back → `BackClicked`; seeded list shows code/symbol/name. Green. (Switch via `isToggleable()`; row tap via `useUnmergedTree` code text.)
- [x] B-S25-empty: `currencies=[]` renders, FAB enabled. Green. (In-use-cannot-disable is VM logic with no Content seam → SKIP at Content level; covered by VM/JVM tests.)

### S26 Currency edit — `CurrencyEditContentUiTest`
- [x] B-S26-fields: code → `CodeChanged`; symbol → `SymbolChanged`; name → `NameChanged`; decimals → `DecimalDigitsChanged`; active toggle → `IsActiveChanged`; Save → `SaveClicked`. Green. (Stateful holder; decimals matched by editable text "2"; Switch via `isToggleable()`.)
- [x] B-S26-locked: `isCodeLocked=true` → code field `assertIsNotEnabled` (PHASE_09 code-lock). Green.
- [x] B-S26-error: invalid-code `errorMessage="code_format"` inline; `blockedDeleteCount` → blocked dialog + `BlockedDeleteDismissed` (no DeleteClicked re-fire). Green.

## Phase C — close remaining seam gaps where a production seam exists

- [x] C1. **S00 Splash content** — `SplashContentUiTest.splashRendersLogo`: `SplashContent()` renders the logo Image (asserted via `splash_logo_content_description`). Green. (No progress indicator in production — logo only.)
- [x] C2. **S12 loading/error states** — SKIP + log: `TransactionsListUiState` = {accountId, categoryId, categoryFilter, currency} — NO `isLoading`/`errorMessage`. Loading/empty are `LazyPagingItems` load-states, not UiState → no Content seam to assert. (Paging load-states would need a hand-built `PagingData`; out of scope for a Content event test.)
- [x] C3. **S12 filter-removal** — SKIP + log: the active-filter `FilterChip` has `onClick = {}` and there is no `FilterRemoved`/`ClearFilter` event. No seam. (See §Re-escalations.)
- [x] C4. **S09 long-press Edit/Archive** — SKIP + log: `CategoryPickerEvent` = {CategoryClicked, AddCategoryClicked, BackClicked} — no long-press/edit/archive seam (see §Re-escalations). NOTE: picker happy-path is ALREADY covered by the pre-existing `CategoryPickerContentUiTest` (back/add/category-cell+amountPreview), so no new test needed here.
- [x] C5. **S06/S07 account chip** — SKIP + log (escalate): `AmountFieldEvent.AccountChipClicked -> Unit` (no-op). The `AddExpenseEvent.AccountChanged(accountId)` event EXISTS but is never dispatched from the chip. No seam → missing feature, not a bug (see §Re-escalations).

## Phase D — re-escalations (genuinely need external resources; NOT fixable inline here)

Record each with the reason it cannot be device-tested without external setup. These are NOT counted
as open bugs.

- [x] D1. S16 real `BiometricPrompt` launch/callbacks + runtime `LockOverlay` unlock/back-blocking — needs enrolled biometric + runtime overlay harness. **DEFERRED — external resource; not a bug (recorded).**
- [x] D2. S17 provider OAuth + live Dropbox/Drive round-trips — needs OQ-2/OQ-3 credentials. **DEFERRED — external resource; not a bug (recorded).**
- [x] D3. BackupRotation success/rotation + S18 SAF picker + real DB/CSV IO — needs a real SAF tree. **DEFERRED — external resource; not a bug (recorded).**
- [x] D4. S20 AS-15 bundled privacy/help WebView route + asset load — Pattern A route test (optional follow-up). **DEFERRED — optional follow-up; not a bug (recorded).**
- [x] D5. S13/S12 undo snackbar routing — Pattern A route/snackbar-host test (optional follow-up). **DEFERRED — optional follow-up; not a bug (recorded).**

## Phase E — FINAL FULL RUN (do last; everything must be green)

- [x] E1. All JVM unit tests, every module: `gradlew testDebugUnitTest` (+ pure-JVM `:core:domain:test :core:common:test`). Parse every report; record total `tests/failures`. **Green: 67 reports, 655 tests, 0 failures, 0 errors, 0 skipped** (re-confirmed at E4 if any prod code changes during E2/E3).
- [x] E2. All instrumented suites on `Pixel_5_API_34`, every module with androidTest: `:app`, `:core:designsystem`, `:core:database`, `:core:datastore`, `:core:sync`. Parse every report. **Green: designsystem 3 + datastore 5 + sync 4 + database 20 + app 122 = 154 instrumented, 0 fail/err/skip** (on a health-checked 60-FPS emulator after Cold Boot; `:app` run as fresh-JVM batches/per-class to survive emulator instability — a single monolithic run is too heavy and crashed the AVD).
- [x] E3. Explicitly re-run ALL E2E journeys + gate green together: `MainActivityLaunchTest`, `MainActivityAddExpenseJourneyTest` (J1), `MainActivityTransferJourneyTest` (J2), `MainActivityCreateCategoryJourneyTest` (J3), `WorkerInstrumentationTest`. **Green together: Launch 1 + J1 1 + J2 1 + J3 1 (gC, one `:app` run) + Worker 4 = 8, 0 fail.**
- [x] E4. Record the final totals in §Final Full Run; update `DEVICE_VERIFICATION_PROGRESS.md` + `PHASE_15`; write the end summary. **Done 2026-05-30.**

---

## Defects fixed (append one line each)

- (from prior session) VIBRATE permission `6767a58`; picker nav-result `5388264`; transfer rate nav-result `beb64c0`; ColorPicker crash `566e8c4`; edit back-arrow `1a534c2`.
- 2026-05-30 — Phase E: **0 new product defects.** The two device failures (J3 `ComposeTimeoutException`, `SwipeToDeleteUiTest`) were emulator-FPS flakes, both green after Cold Boot + health-check; no code changed.

## Re-escalations (append one line each, with reason)

- **C5 (S06/S07 account chip is a dead control).** `AmountFieldEvent.AccountChipClicked -> Unit` in
  `AddExpenseScreen.dispatchAmountEvent` (and the income twin): the account chip renders a label but
  tapping it does nothing, even though `AddExpenseEvent.AccountChanged(accountId)` + `state.accounts`
  already exist. Not device-testable (no event); fixing it = building an account-picker sheet/dropdown
  (a feature + UX decision), so it's escalated, not patched inline. Borderline UX bug (tappable-looking
  no-op) — recommend wiring `AccountChipClicked` to an account picker that emits `AccountChanged`.
- **C3 (S12 cannot clear an active filter).** The active-filter `FilterChip` has `onClick = {}` and no
  `FilterRemoved`/`ClearFilter` event exists — once filtered by category/account the user has no in-UI
  way to clear it. Missing feature, not a bug; needs a clear-filter event + chip close affordance.
- **C4 (S09 no long-press Edit/Archive on a category).** `CategoryPickerEvent` exposes no
  edit/archive/long-press seam. Category edit/archive lives on S22 (reachable via dictionaries), so this
  is a convenience gap, not a blocker. Needs a `CategoryLongPressed`/context-menu event if desired.

## Final Full Run (filled in at E4)

**2026-05-30 — ALL GREEN: 809 autotests, 0 failures / 0 errors / 0 skipped.** Run on a health-checked
`Pixel_5_API_34` (NVIDIA RTX 5060 hardware GL, 60 FPS after Cold Boot).

| Layer | Suites | tests | fail | err | skip |
|---|---|---:|---:|---:|---:|
| E1 — JVM unit | all modules (67 reports) | 655 | 0 | 0 | 0 |
| E2 — instrumented | designsystem 3, datastore 5, sync 4, database 20, **app 122** | 154 | 0 | 0 | 0 |
| **TOTAL** | | **809** | **0** | **0** | **0** |

E3 — E2E journeys gated green together on the healthy device: `MainActivityLaunchTest` 1, J1
`MainActivityAddExpenseJourneyTest` 1, J2 `MainActivityTransferJourneyTest` 1, J3
`MainActivityCreateCategoryJourneyTest` 1, `WorkerInstrumentationTest` 4 — all 0-fail.

**Real product defects found this phase: 0.** Two device "failures" surfaced and were both proven to be
emulator-GPU flakes (not bugs): J3 `ComposeTimeoutException` and `SwipeToDeleteUiTest` — both passed on
the health-checked 60-FPS emulator after a Cold Boot, with no code change. The earlier chaos (a 64-min
`:app` "hang", an `INSTRUMENTATION_ABORTED: System has crashed`, `Failed to install split APK`, `Unable
to resolve activity` for 26 settings tests) was entirely a **~2-FPS emulator GPU/snapshot regression**,
not product or test defects — every test that ran on a healthy device went green.

**New tooling:** `scripts/preflight_device_health.ps1` — a pre-flight gate (adb present + boot complete +
hardware GL, not SwiftShader + a timed `MainActivityLaunchTest` smoke). Run it BEFORE any full
instrumented suite so a 2-FPS / unhealthy AVD is caught in ~40 s instead of wrecking a multi-hour run.
**Runner lesson:** never run all 122 `:app` tests in one `connectedDebugAndroidTest` invocation — split
into fresh-JVM batches (`--no-daemon`, per the helper) with a per-batch watchdog; one heavy monolithic
run exhausts/crashes the AVD.

## Progress log (append dated one-liners)

- 2026-05-29 — plan created; Phase A audit A1/A2 verified clean. Starting A3/A4 then Phase B.
- 2026-05-29 — **Phase C complete.** C1 Splash render green (`SplashContentUiTest` 1/1). C2/C3/C4/C5
  resolved as SKIP+log (no Content seam) — see §Re-escalations for the 3 genuine feature gaps
  (S06/S07 account chip is a dead no-op control; S12 has no clear-filter; S09 has no long-press menu).
  S09 picker happy-path already covered by the pre-existing `CategoryPickerContentUiTest`. Next: Phase D
  review (all external-resource items) then Phase E final full run.
- 2026-05-29 — **Phase B complete (S21–S26).** S23 accounts 2/2, S24 account-edit 2/2 (AS-13), S25
  currencies 2/2, S26 currency-edit 3/3 — all green on `Pixel_5_API_34`. Reusable seams discovered:
  rows with a trailing control (AssistChip/Switch) need `useUnmergedTree` name-text taps (merged-row
  centre hits the control); the lone `Switch` is found via `isToggleable()`; fields with a default
  value (balance "0", decimals "2") are matched by editable text + `performTextReplacement`; edit
  screens use a stateful state-holder so controlled fields settle. Next: Phase C seam gaps.
- 2026-05-29 — B-S22 green 3/3 (`CategoryEditContentUiTest`: fields/icon/error). Test-authoring lesson:
  `performScrollTo()` on a `LazyVerticalGrid` item (ColorPicker swatch) nested in `Column(verticalScroll)`
  deadlocks `waitForIdle` (nested-scroll measure loop) → click the always-visible first swatch directly.
  Applies to S24/S26 (same `ColorPicker`). Also gave the fields test a stateful state-holder so the
  controlled `OutlinedTextField` settles. No production defect — the UI scrolls fine for real users.
- 2026-05-30 — **Phase E FINAL FULL RUN complete — 809 tests, 0 failures** (E1 655 unit + E2 154
  instrumented; E3 E2E gate green together). **0 real product defects.** A first monolithic `:app`
  connected run hung 64 min and crashed the emulator (`INSTRUMENTATION_ABORTED`); diagnosed the root
  cause as a **~2-FPS emulator GPU/snapshot regression** (NVIDIA RTX 5060 laptop), NOT product/test
  bugs — proven by re-running batched/per-class on a Cold-Booted 60-FPS device, where every test (incl.
  the J3 `ComposeTimeout` + `SwipeToDelete` "failures") went green. Switched runner strategy to fresh-JVM
  batches with watchdogs; added `scripts/preflight_device_health.ps1` health gate (run before every full
  suite). 0 production changes this phase; new files (health script + doc updates) are LOCAL, not pushed.
