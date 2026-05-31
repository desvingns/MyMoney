# Embed category grid into the add-form; drop the separate picker route (S06/S07)
Epic: redesign-monefy-fidelity
Order: 06 of 06
Status: done
Depends-on: 01 (categoryIcon registry + flat-card grid)
Date: 2026-05-31

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: Bring the add-expense/income flow to the Monefy reference by embedding the category grid directly into the form (S06/S07) — the grid replaces the inline keypad, the keypad moves into a modal bottom sheet opened from the amount field, and the separate CategoryPicker route is removed.
LAYERS: presentation
CHANGED_HINT: feature/transaction/.../categorygrid/CategoryGrid.kt (NEW — reusable 3-col flat-card grid, reuses categoryIcon registry + parseHexColor, "+ ADD" cell CATEGORY_GRID_ADD_CELL_TAG); feature/transaction/.../expense/AddExpenseScreen.kt + income/AddIncomeScreen.kt (embed CategoryGrid; keypad -> ModalBottomSheet); AddExpense/AddIncome State (+keypadVisible), Event (+KeypadDismissed), Action, ViewModel; feature/transaction/.../picker/CategoryPickerScreen.kt (gutted/removed); app/.../navigation/MyMoneyNavHost.kt (picker route removed); screenshots 06.jpg/07.jpg; TDD §03_style L124-136 + epic-overview divergence #3
TEST_TYPES: unit compose-ui
CONSTRAINTS:
  - Reuse SPEC-01's `categoryIcon(iconKey)` registry and the flat outlined-card style; do NOT re-implement icons. CategoryGrid lives in :feature:transaction (presentation), pulls the registry from :core:designsystem.
  - Keypad becomes a ModalBottomSheet (state `keypadVisible`, dismiss event `KeypadDismissed`); keep the full KeypadEvent contract + AmountFieldEvent.Keypad dispatch + press-scale/haptics/sound. Amount field opens the sheet.
  - AS-4 add-category round-trip (AddCategoryClicked + savedStateHandle) must keep working from the embedded "+ ADD" cell — the create-category route is still separate, only the *pick* step is embedded.
  - Removing the CategoryPicker route must drop its nav entry cleanly (MyMoneyNavHost) and keep deep-link / back-stack behaviour sane; J3 "create-category-from-picker preserves amount" E2E must be re-pointed at the embedded flow, not deleted.
  - No domain/data changes; no hardcoded colours/strings (EN+RU); English ids; no comments unless WHY.
=== END SPEC ===

## Gap / context
The Monefy reference embeds the category grid into the add-transaction screen (it replaces the keypad);
MyMoney shipped SPEC 01 deliberately KEEPING the grid on a separate route (TopAppBar back-arrow +
amount-preview). This SPEC is the epic-overview **divergence #3**, originally flagged "optional, later" —
now actively in flight. As of 2026-05-31 the work is **uncommitted** in the working tree: new
`categorygrid/CategoryGrid.kt`; `CategoryPickerScreen.kt` gutted (−130); picker route removed from
`MyMoneyNavHost.kt` (−8); both add-forms embed the grid + move the keypad into a sheet
(`keypadVisible`/`KeypadDismissed`). The income side lags the expense side (e.g. `AddIncomeEvent.kt` not
yet touched) — the refactor is mid-flight, not finished.

## Implementation links
- commit: b94abb7
- files:  app/navigation/Destinations.kt; app androidTest add-form/E2E/category-grid tests; app navigation tests; feature/transaction categorygrid + expense/income form state/event/viewmodel/screen; removed feature/transaction picker route/screen/viewmodel/tests; transaction EN/RU strings
