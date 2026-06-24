# Dashboard top bar: tap the period label to open a single-date picker
Epic: —
Order: —
Status: done
Depends-on: —
Date: 2026-06-22

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: In the S01 dashboard TOP BAR, the period label sitting BETWEEN the ‹ prev / next › arrows (the `PeriodSwitcher` composable) is currently NOT clickable — only the two arrow icons dispatch period changes. Make the period label tappable: tapping it opens a Material3 SINGLE-date picker dialog titled "Выбор даты" (reuse existing string `period_pick_a_date`), pre-selected to TODAY. Confirming a date emits `DashboardEvent.PeriodChanged(Period.Day(pickedDate))`, switching the dashboard to that single day. Reuse the existing single-date `DatePickerDialog` pattern already working in `LeftDrawerContent` — DO NOT add a new Period subtype.
LAYERS: presentation
CHANGED_HINT:
  - feature/dashboard/src/main/java/com/kshavrin/mymoney/feature/dashboard/components/PeriodLabel.kt:
      • `PeriodSwitcher` (L122-173, signature L123-128 `fun PeriodSwitcher(period, onPreviousClick, onNextClick, modifier)`): ADD a param `onPeriodLabelClick: () -> Unit`. Wrap the central period column (the period-title Text + mint underline Box) in `Modifier.clickable(onClick = onPeriodLabelClick)` so the label between the arrows is the tap target. Add `contentDescription = stringResource(R.string.period_pick_a_date)` to that clickable element (semantics) so the instrumented test can locate it. Keep the ‹/› arrow callbacks and the existing auto-shrink/single-line period-title logic unchanged. Visuals otherwise unchanged (only add clickable + ripple + semantics).
  - feature/dashboard/src/main/java/com/kshavrin/mymoney/feature/dashboard/DashboardScreen.kt:
      • At the `PeriodSwitcher` call site in the top bar (wired around L378-386, top bar block L382-454): ADD local `var showDatePicker by remember { mutableStateOf(false) }`; pass `onPeriodLabelClick = { showDatePicker = true }`.
      • ADD a dialog block mirroring the single-date pattern from LeftDrawerContent.kt:196-219: `if (showDatePicker) { val st = rememberDatePickerState(initialSelectedDateMillis = localDateToMaterialPickerUtcMillis(java.time.LocalDate.now())); DatePickerDialog(onDismissRequest = { showDatePicker = false }, confirmButton = { TextButton(onClick = { st.selectedDateMillis?.let { onEvent(DashboardEvent.PeriodChanged(Period.Day(materialPickerUtcMillisToLocalDate(it)))) }; showDatePicker = false }) { Text(stringResource(R.string.period_apply)) } }, dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.period_cancel)) } }) { DatePicker(state = st) } }`. Add imports: androidx.compose.material3.DatePicker, androidx.compose.material3.DatePickerDialog, androidx.compose.material3.rememberDatePickerState, androidx.compose.material3.TextButton (whichever are not already present).
  - feature/dashboard/src/main/java/com/kshavrin/mymoney/feature/dashboard/components/MaterialPickerDateConverters.kt (L7-8): ADD the inverse helper `fun localDateToMaterialPickerUtcMillis(date: LocalDate): Long = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()` so the picker can be seeded to TODAY consistently with the existing UTC-anchored `materialPickerUtcMillisToLocalDate`. (If a seeding helper already exists, reuse it instead of adding one.)
  - app/src/androidTest/java/com/kshavrin/mymoney/feature/dashboard/DashboardContentUiTest.kt — ADD a test: tapping the period label (locate by contentDescription = getString(R.string.period_pick_a_date)) shows the date picker; confirming a date drives the captured DashboardEvent to `PeriodChanged(Period.Day(...))`. Use InstrumentationRegistry targetContext getString for all strings (EN+RU shipped). createComposeRule() Pattern B, capture events in a list, assert with runOnIdle.
TEST_TYPES: compose-ui instrumented
CONSTRAINTS:
  - Single-date ONLY → Period.Day. DO NOT add a new Period subtype, DO NOT touch core/domain/.../model/Period.kt, DO NOT open a DateRangePicker / Period.CustomRange from the top-bar label.
  - Picker pre-selects TODAY (LocalDate.now()) regardless of the current period mode.
  - Use the canonical UTC-anchored converters in MaterialPickerDateConverters.kt for BOTH directions (Material3 pickers return/consume UTC-anchored millis) — NOT ZoneId.systemDefault(). This matches the audit1-timezone convention.
  - The label tap works in EVERY period mode (Day/Week/Month/Year/All/CustomRange); the result is always Period.Day(pickedDate). The existing ‹/› arrow prev/next navigation is unchanged.
  - Scope is the TOP BAR PeriodSwitcher only. DO NOT modify LeftDrawerContent's own date buttons or PeriodStrip.kt.
  - No hardcoded user-facing strings — reuse existing R.string.period_pick_a_date / period_apply / period_cancel (no new strings). English identifiers. No comments unless WHY is non-obvious. Presentation layer only — no domain/data/navigation/migration changes.
=== END SPEC ===

## Gap / context
The top-bar period label between the ‹ › arrows is dead to taps today — users can only step the period
one unit at a time via the arrows. Per AS-12 the LEFT-drawer "Pick a date" path opens a two-date range
picker, but the user wants the TOP-BAR label tap to open a genuine single-date picker ("Выбор даты") that
jumps the dashboard to one specific day (Period.Day). Period.Day is already fully wired (label format +
day-step prev/next), and LeftDrawerContent.kt:196-219 already runs the exact Material3 single-date
`DatePickerDialog` → `materialPickerUtcMillisToLocalDate` → `PeriodChanged(Period.Day(date))` pattern, so
this is a presentation-only change: make the label clickable and reuse that dialog. The only new code is a
one-line inverse converter to seed the picker to today consistently with the existing UTC convention.

## Implementation links
- Commit (prod): 1efebf97 — feat: open single-date picker from dashboard top-bar period label
- Commit (test): e8100982 — test: cover dashboard period-label tap opening single-date picker
- Pushed to main: 5a5506be..e8100982
- Files:
  - feature/dashboard/src/main/java/com/kshavrin/mymoney/feature/dashboard/components/PeriodLabel.kt (PeriodSwitcher gained onPeriodLabelClick + clickable + semantics)
  - feature/dashboard/src/main/java/com/kshavrin/mymoney/feature/dashboard/DashboardScreen.kt (showDatePicker state + Material3 DatePickerDialog at PeriodSwitcher call site)
  - feature/dashboard/src/main/java/com/kshavrin/mymoney/feature/dashboard/components/MaterialPickerDateConverters.kt (added localDateToMaterialPickerUtcMillis inverse converter)
  - app/src/androidTest/java/com/kshavrin/mymoney/feature/dashboard/DashboardContentUiTest.kt (2 new instrumented tests, 2/2 green on emulator-5554)
- Verification: instrumented 2/2 passed on emulator-5554; reviewer pass; verifier pass.
- Note: pre-existing stale androidTest compile errors (CategoryRecordsInlineListUiTest committed; CategoryTilesListUiTest +125 uncommitted parallel-session lines) were repaired in the working tree (missing onRowClick/onRecordRowClick lambdas) to unblock the instrumented gate — these repairs were left UNCOMMITTED per user instruction, alongside the broader uncommitted parallel-session work.
