# Device Verification Coverage Progress

This is the resumable tracker for the on-device remediation defined in
`docs/DEVICE_VERIFICATION_PLAN_FOR_CODEX.md`. `docs/implementation_plan/PROGRESS.md`
continues to own phase status; this file tracks the fine-grained UI/control and
worker test backlog across sessions.

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
| 2026-05-27 | UTP-safe device runner established | Direct remote serial causes AGP 8.7.3 UTP profile-path failure; `scripts/run_connected_test_on_host_avd.ps1` proxies host ADB so Gradle uses `emulator-5554` and waits 60 seconds after each run. |

## Delivery Order

| Slice | Scope | Status | Device run/report |
|---|---|---|---|
| 0 | Pattern A infrastructure: Hilt runner, isolated database/settings, `MainActivity` launch gate | Pending | - |
| 1 | S00/S11/S01/S06 critical flow: onboarding -> dashboard -> add expense -> updated balance | In progress | S11 5/5; S01/S04 + AS-2 7/7; S02 2/2; S06 stable controls 7/7 green 2026-05-27; account/error seams and Pattern A pending |
| 2 | Transaction forms S07/S03/S09/S27, including AS-4 and AS-6 paths | In progress | S07 stable controls 7/7 green 2026-05-27; S03/S09/S27, AS-4/AS-6, account/error seams pending |
| 3 | Dictionaries S21-S26 CRUD and validation controls | Pending | - |
| 4 | List/detail/search/settings/lock/sync/backup plus worker instrumentation | Pending | - |
| 5 | Manual QA, minified release walk, macrobenchmark/Baseline Profile | Pending | - |

## Screen Matrix

`Green` means a passing connected test on `Pixel_5_API_34`. A dated `Existing`
entry identifies coverage already recorded before this tracker was created.

| Screen / surface | Primary controls to exercise | Happy | Empty | Error | E2E / notes |
|---|---|:---:|:---:|:---:|---|
| S00 Splash | startup routing | Pending | n/a | Pending | Slice 0/1 |
| S11 Onboarding | Skip, Next, Get Started, pager | Green: 5/5 | n/a | n/a | `OnboardingContentUiTest`, 5/5 green 2026-05-27; indicator semantic state added in `c3f74b1`, regression in `a0a53ea` |
| S01/S05 Dashboard | expense/income FABs, search, transfer, balance, donut slice, drawers | Partial green: controls + balance 7/7 | Empty-state controls green | Pending | `DashboardContentUiTest` 7/7 green 2026-05-27; AS-2 balance pill covered; donut semantics existing green 2026-05-26 in `:core:designsystem` |
| S02 Period drawer | period choices, Pick a date, apply range | Green: 2/2 suite | n/a | Pending | `PeriodStripUiTest` covers ordinary chips plus AS-12 custom range on 2026-05-27 |
| S04 Right drawer | Categories, Accounts, Currencies, Settings/About tiles | Green: five rows covered | n/a | n/a | `DashboardContentUiTest` right-drawer group green 2026-05-27 |
| S06 Add expense | back, swap, date, note, keypad keys/backspace, choose category | Partial green: stable controls 7/7 | Zero-amount category disabled green | Blocked by missing retry UI | `AddExpenseScreenUiTest` 7/7 green 2026-05-27; account-chip event seam missing; critical E2E pending |
| S07 Add income | back, swap, date, note, keypad/backspace, choose category | Partial green: stable controls 7/7 | Zero-amount category disabled green | Blocked by missing retry UI | `AddIncomeScreenUiTest` 7/7 green 2026-05-27; account-chip event seam missing; AS-4 E2E pending |
| S03 Transfer | account pickers, keypad, rate/change, save | Pending | Pending | Pending | AS-6/AS-7 E2E pending |
| S09 Category picker | category cell, add, back, context actions | Pending | Pending | n/a | AS-4 pending |
| S27 Currency rate | amount input, save, back | Pending | n/a | Pending | Slice 2 |
| S08 Search | back, query/clear, voice affordance, result row, chips | Pending | Pending | Pending | Slice 4 |
| S12 Transactions list | search, filters, row, swipe/undo | Existing partial green 2026-05-26 | Pending | Pending | `SwipeToDeleteUiTest` covers delete callback only |
| S13 Detail/edit | back, delete/confirm/undo, edit/save, rate | Pending | n/a | Pending | Slice 4 |
| S14 Settings root | all destination rows, sound/haptic toggles | Pending | n/a | Pending | Slice 4 |
| S15 Theme | System, Light, Dark rows | Pending | n/a | n/a | Slice 4 |
| S16 Lock setup/overlay | enable, timeout, PIN fallback, back blocking | Pending | Pending | Pending | AS-5 pending |
| S17 Cloud sync | connect/disconnect, sync, auto-sync, conflict actions | Pending | Pending | Pending | Keep providers gated off |
| S18 Backup/Restore | export/import DB, export/import CSV, reset confirm | Pending | Pending | Pending | Slice 4 |
| S19 Language | System, English, Russian rows | Pending | n/a | n/a | Slice 4 |
| S20 About/Help | privacy, help, licences, back | Pending | n/a | Pending | AS-15 pending |
| S21 Categories | tabs, row/edit/archive, add, drag | Pending | Pending | Pending | Slice 3 |
| S22 Category edit | name, icon, colour, kind, save | Pending | Pending | Pending | Slice 3 |
| S23 Accounts | row/edit/archive/delete/default, add | Pending | Pending | Pending | AS-13 pending |
| S24 Account edit | fields, currency, default, save | Pending | Pending | Pending | Slice 3 |
| S25 Currencies | toggle, edit, manage rate, add | Pending | Pending | Pending | Slice 3 |
| S26 Currency edit | code/symbol/digits/active/save | Pending | Pending | Pending | Slice 3 |
| Workers | recurring, prune, rotation, sync no-op | Pending | n/a | Pending | WorkManager instrumentation pending |

## Session Log

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
