# Transaction & transfer form chrome (S03/S06/S07)
Epic: redesign-monefy-fidelity
Order: 03 of 05
Status: done
Depends-on: — (SPEC 01 icons help account/category rows)
Date: 2026-05-30
Amended: 2026-05-31 — constraint #4 DROPPED (see note below). SPEC 06 already retired the
  "choose category" button and embedded CategoryGrid into the add forms (locked divergence #3);
  user decision 2026-05-31 = "drop #4, restyle chrome only". The embedded grid is left untouched.

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: Restyle add-expense/income/transfer form chrome to the Monefy reference — green amount box with currency prefix + white number + ✕ clear, light calculator keypad, centered date header, flat "ВЫБОР КАТЕГОРИИ" bar, and transfer from/to account cards with a down-arrow + dialpad FAB.
LAYERS: presentation
CHANGED_HINT: core/designsystem/.../amountinput/MonefyAmountInput.kt; .../amountfield/AmountFieldSection.kt; .../keypad/MonefyKeypad.kt; feature/transaction/.../expense/AddExpenseScreen.kt; .../income/AddIncomeScreen.kt; .../transfer/TransferScreen.kt; screenshots 03/06/07; TDD §03_style L124-136
TEST_TYPES: unit compose-ui
CONSTRAINTS:
  - Amount box: green container (colorScheme.primary), white number (onPrimary), currency symbol/code on the LEFT inside the box, trailing ✕ clear affordance wired to KeypadEvent.Backspace (long-press clear optional). Keep computeDisplayFontSize autoscale + the expression line.
  - Keypad: light keys (surface/surfaceVariant bg, onSurface glyphs, hairline outline), distinct operator look; align to reference 4-col layout (rows: 1 2 3 +, 4 5 6 −, 7 8 9 ×, . 0 = ÷). Keep KeypadEvent contract + press-scale animation + haptics/sound. Backspace lives in the amount box (reference) — keep its event wired.
  - Date: centered header "EEEE, d MMMM" + calendar icon below the top bar (reference), instead of the inline AssistChip; keep the DateChipClicked -> DatePicker behaviour.
  - "ВЫБОР КАТЕГОРИИ" bar: DROPPED (amended 2026-05-31). SPEC 06 already embedded CategoryGrid into the add forms and retired the choose-category button (no ChooseCategoryClicked event exists). Do NOT re-introduce a category bar; leave the embedded CategoryGrid untouched.
  - Form TopAppBar: green container + white content (mirror dashboard's explicit topAppBarColors). Keep back + swap-mode.
  - Transfer (S03): green amount box, note, FROM account card -> down-arrow -> TO account card, dialpad FAB. Keep AS-6/AS-7 rate flow + all events.
  - No VM/behaviour changes beyond wiring; no hardcoded colours/strings (EN+RU); English ids; no comments unless WHY.
=== END SPEC ===

## Gap / context
MonefyAmountInput is a plain Card with a dark number (not the green box + white number + currency prefix + ✕); MonefyKeypad uses primary-filled buttons (not light keys); date is a chip not a centered header; "choose category" is a filled Button not the flat "ВЫБОР КАТЕГОРИИ" bar.

## Implementation links
- commit: `90ad3eb` (style: restyle transaction form chrome to Monefy fidelity) + `08db651` (test: cover transaction form chrome restyle). Pushed to origin/main 2026-05-31.
- constraint #4 dropped per user decision 2026-05-31 (SPEC 06 already embedded CategoryGrid — divergence #3); embedded grid left untouched.
- files (production, 90ad3eb):
  - core/designsystem/.../amountinput/MonefyAmountInput.kt (green box, currency-left, white number, ✕ clear)
  - core/designsystem/.../amountfield/AmountFieldSection.kt (onClear→Backspace, showAccountDateRow gate)
  - core/designsystem/.../keypad/MonefyKeypad.kt (light 4-col keys, ⌫ key removed; KeypadEvent.Backspace kept)
  - feature/transaction/.../DateHeader.kt (NEW — centered "EEEE, d MMMM" + calendar icon)
  - feature/transaction/.../expense/AddExpenseScreen.kt, income/AddIncomeScreen.kt, transfer/TransferScreen.kt (green TopAppBar; transfer FROM→down-arrow→TO cards + dialpad FAB)
  - feature/transaction/.../res/values/strings.xml + values-ru/strings.xml (transfer_direction_cd, transfer_open_keypad_cd; EN+RU parity)
- files (tests, 08db651): MonefyKeypadContractTest.kt, MonefyKeypadTest.kt, AmountFieldClearContractTest.kt, DateHeaderContractTest.kt, AddExpenseScreenContractTest.kt, AddIncomeScreenContractTest.kt, TransferScreenContractTest.kt (executable Compose-UI deferred to PHASE_15; JVM contract pinning + documented templates). Runner: 124 passed / 0 failed, lint ok.
