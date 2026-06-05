# Dashboard left drawer: split "Pick a date" into "Date range" + a single-date picker
Epic: —
Order: —
Status: done
Depends-on: —
Date: 2026-06-05

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: In the dashboard LEFT drawer (LeftDrawerContent), the single date button currently labelled "Pick a date" opens a TWO-date DateRangePicker (Period.CustomRange). (1) RENAME that button to "Date range" (behaviour unchanged — still opens DateRangePicker -> Period.CustomRange). (2) ADD a NEW button directly BELOW it, labelled "Pick a date", that opens a Material3 SINGLE-date DatePicker and emits DashboardEvent.PeriodChanged(Period.Day(pickedDate)). Reuse the existing Period.Day mode — DO NOT add a new Period subtype (Period.Day already formats as "EEEE, d MMMM" in PeriodLabel and supports prev/next day navigation).
LAYERS: presentation
CHANGED_HINT:
  - feature/dashboard/src/main/res/values/strings.xml — ADD <string name="period_date_range">Date range</string>. KEEP period_pick_a_date ("Pick a date") — it now labels the NEW single-date button.
  - feature/dashboard/src/main/res/values-ru/strings.xml — ADD <string name="period_date_range">Диапазон дат</string>. KEEP period_pick_a_date ("Выбор даты").
  - feature/dashboard/src/main/java/com/kshavrin/mymoney/feature/dashboard/components/LeftDrawerContent.kt:
      • L125-130 (the existing range PeriodButton): change `label = stringResource(R.string.period_pick_a_date)` -> `R.string.period_date_range`. Keep `selected = state.period is Period.CustomRange`, keep `leadingIcon = Icons.Outlined.CalendarToday`, keep `onClick = { showRangePicker = true }`.
      • Immediately AFTER it (still inside the Column, before line 131), ADD a NEW PeriodButton: `label = stringResource(R.string.period_pick_a_date)`, `selected = state.period is Period.Day && (state.period as Period.Day).date != java.time.LocalDate.now()`, `leadingIcon = Icons.Outlined.Event` (distinct from the range button's CalendarToday), `onClick = { showSingleDatePicker = true }`.
      • L102 (the "Day" quick button): tighten `selected = state.period is Period.Day` -> `selected = state.period is Period.Day && (state.period as Period.Day).date == java.time.LocalDate.now()` so the "Day" pill and the new "Pick a date" pill don't both highlight.
      • ADD `var showSingleDatePicker by remember { mutableStateOf(false) }` next to the existing `showRangePicker` (L74).
      • ADD a single-date dialog block mirroring the existing range block (L153-181): `if (showSingleDatePicker) { val st = rememberDatePickerState(); DatePickerDialog(onDismissRequest = { showSingleDatePicker = false }, confirmButton = { TextButton(onClick = { st.selectedDateMillis?.let { changePeriod(Period.Day(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate())) }; showSingleDatePicker = false }) { Text(stringResource(R.string.period_apply)) } }, dismissButton = { TextButton(onClick = { showSingleDatePicker = false }) { Text(stringResource(R.string.period_cancel)) } }) { DatePicker(state = st) } }`. Add imports: androidx.compose.material3.DatePicker, androidx.compose.material3.rememberDatePickerState, androidx.compose.material.icons.outlined.Event. (DatePickerDialog/TextButton already imported.) Reuse the existing `changePeriod(period)` helper (it plays sound+haptic then emits PeriodChanged).
  - app/src/androidTest/java/com/kshavrin/mymoney/feature/dashboard/components/LeftDrawerPeriodSelectorUiTest.kt — update assertions: the period_date_range label is now present (was period_pick_a_date for the range button); a NEW period_pick_a_date button exists; clicking it + confirming a date drives state to Period.Day. Keep the existing 5-period assertions. (runner compiles + runs androidTest in the same pass.)
TEST_TYPES: compose-ui instrumented
CONSTRAINTS:
  - Reuse Period.Day for the single-date selection — DO NOT add a new Period subtype, DO NOT touch core/domain/.../model/Period.kt. The range button's behaviour (DateRangePicker -> Period.CustomRange) is unchanged.
  - Scope is the LEFT drawer ONLY. DO NOT modify PeriodStrip.kt (the alternate top-bar period strip with its own range picker) — out of scope for this SPEC.
  - The new "Pick a date" button sits directly BELOW "Date range" (last two rows of the period Column).
  - No hardcoded user-facing strings (use R.string.*, English default + Russian translation). English identifiers. No comments unless WHY is non-obvious. No domain/data/navigation changes.
=== END SPEC ===

## Gap / context
AS-12 made "Pick a date" open a two-date range picker. The label "Pick a date" misleads (users expect to pick ONE day). User wants the range action relabelled "Date range" and a genuine single-day picker added beneath it. Period.Day already exists and is fully wired (label + day-step navigation), so the single-date button needs no domain change — only a Material3 single DatePicker in the drawer. The single-date picker pattern (rememberDatePickerState + DatePicker) already exists in feature/transaction/.../transfer/TransferScreen.kt:29,47 as a working reference.

## Implementation links
- commit: be41d9b, 21f54b7
- files:  feature/dashboard/src/main/java/com/kshavrin/mymoney/feature/dashboard/components/LeftDrawerContent.kt; feature/dashboard/src/main/res/values/strings.xml; feature/dashboard/src/main/res/values-ru/strings.xml; app/src/androidTest/java/com/kshavrin/mymoney/feature/dashboard/components/LeftDrawerPeriodSelectorUiTest.kt
