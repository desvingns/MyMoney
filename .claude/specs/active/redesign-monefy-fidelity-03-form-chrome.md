# Transaction & transfer form chrome (S03/S06/S07)
Epic: redesign-monefy-fidelity
Order: 03 of 05
Status: backlog
Depends-on: — (SPEC 01 icons help account/category rows)
Date: 2026-05-30

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
  - "ВЫБОР КАТЕГОРИИ": full-width flat/subtle bar (uppercase) instead of the filled Button; keep enabled rule (amount>0 && !isSaving) + ChooseCategoryClicked. Shows the picked category name when set.
  - Form TopAppBar: green container + white content (mirror dashboard's explicit topAppBarColors). Keep back + swap-mode.
  - Transfer (S03): green amount box, note, FROM account card -> down-arrow -> TO account card, dialpad FAB. Keep AS-6/AS-7 rate flow + all events.
  - No VM/behaviour changes beyond wiring; no hardcoded colours/strings (EN+RU); English ids; no comments unless WHY.
=== END SPEC ===

## Gap / context
MonefyAmountInput is a plain Card with a dark number (not the green box + white number + currency prefix + ✕); MonefyKeypad uses primary-filled buttons (not light keys); date is a chip not a centered header; "choose category" is a filled Button not the flat "ВЫБОР КАТЕГОРИИ" bar.

## Implementation links
- commit: (pending)
- files:  (pending)
