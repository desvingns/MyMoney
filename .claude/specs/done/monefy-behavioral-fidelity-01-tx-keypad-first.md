# Transaction entry: keypad first, then category grid (S06/S07)
Epic: monefy-behavioral-fidelity
Order: 01 of 09
Status: done
Depends-on: â€”
Date: 2026-06-01

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: Rework add-expense/add-income to the Monefy two-step order (06.jpg -> 10.jpg): on open show the calculator keypad INLINE (default step) with the amount card + note above it and a full-width "Ð’Ð«Ð‘ÐžÐ  ÐšÐÐ¢Ð•Ð“ÐžÐ Ð˜Ð˜" button at the bottom; tapping that button (enabled only when amount > 0) swaps the lower region for the embedded category grid (the amount card stays). Picking a category saves. Replaces SPEC-06's "grid always-visible + keypad in a modal sheet".
LAYERS: presentation
CHANGED_HINT: feature/transaction/.../expense/AddExpenseScreen.kt (drop the ModalBottomSheet keypad -> inline MonefyKeypad as the default lower region; add a full-width "Ð’Ð«Ð‘ÐžÐ  ÐšÐÐ¢Ð•Ð“ÐžÐ Ð˜Ð˜" button; show CategoryGrid ONLY in the category step); income/AddIncomeScreen.kt (mirror); expense/AddExpenseState.kt + income/AddIncomeState.kt (replace keypadVisible with categoryStep: Boolean = false); AddExpenseEvent/AddIncomeEvent (+ SelectCategoryClicked, BackToAmount; remove KeypadDismissed + the AmountClicked-as-sheet-opener); AddExpenseViewModel/AddIncomeViewModel (step transitions; require amount > 0); reuse core/designsystem/.../keypad/MonefyKeypad.kt + feature/transaction/.../categorygrid/CategoryGrid.kt; screenshots 06.jpg (keypad step) / 10.jpg (grid step); modifies done/redesign-monefy-fidelity-06-embed-grid.md
TEST_TYPES: unit compose-ui
CONSTRAINTS:
  - ONE screen/route, TWO steps (do NOT reintroduce a separate picker route). Step 1 = amount card + note + inline keypad + full-width "Ð’Ð«Ð‘ÐžÐ  ÐšÐÐ¢Ð•Ð“ÐžÐ Ð˜Ð˜" button (R.string, EN+RU). Step 2 = amount card (persists) + embedded CategoryGrid. 06.jpg = keypad step, 10.jpg = grid step.
  - Keypad INLINE, not ModalBottomSheet. Reuse MonefyKeypad + the full KeypadEvent contract + CalculatorEngine (BR-7: one dot per operand). Keep press-scale / haptics / sound.
  - "Ð’Ð«Ð‘ÐžÐ  ÐšÐÐ¢Ð•Ð“ÐžÐ Ð˜Ð˜" enabled only when amount > 0; amount == 0 -> the existing amount-required error (reuse current validation in onCategoryPicked).
  - In the grid step, tapping the amount card returns to the keypad step (BackToAmount).
  - Picking a category saves via the existing flow (savedSignal + NavigateBack). AS-4 "+ Ð”ÐžÐ‘ÐÐ’Ð˜Ð¢Ð¬" add-category round-trip (AddCategoryClicked + savedStateHandle) must keep working from the grid.
  - Mirror expense AND income to full parity (SPEC-06 left income lagging). No domain/data changes; no hardcoded colours/strings (EN+RU); English ids; no comments unless WHY.
=== END SPEC ===

## Gap / context
User note #5. Post-SPEC-06 the add-transaction screen shows the CategoryGrid always-visible with the
keypad in a ModalBottomSheet opened by tapping the amount field (AddExpenseScreen.kt:175-220). The
Monefy reference is the opposite ordering: a full inline keypad first (06.jpg) with a "Ð’Ð«Ð‘ÐžÐ 
ÐšÐÐ¢Ð•Ð“ÐžÐ Ð˜Ð˜" button, then the category grid (10.jpg). This SPEC re-sequences the SAME embedded grid +
keypad components (no new route) and brings the income side to parity.

## Implementation links
- commit: 0b0f0e6 (feature), f536840 (tests)
- files:
  - .claude/specs/done/redesign-monefy-fidelity-06-embed-grid.md
  - feature/transaction/src/main/java/com/kshavrin/mymoney/feature/transaction/expense/AddExpenseEvent.kt
  - feature/transaction/src/main/java/com/kshavrin/mymoney/feature/transaction/expense/AddExpenseScreen.kt
  - feature/transaction/src/main/java/com/kshavrin/mymoney/feature/transaction/expense/AddExpenseState.kt
  - feature/transaction/src/main/java/com/kshavrin/mymoney/feature/transaction/expense/AddExpenseViewModel.kt
  - feature/transaction/src/main/java/com/kshavrin/mymoney/feature/transaction/income/AddIncomeEvent.kt
  - feature/transaction/src/main/java/com/kshavrin/mymoney/feature/transaction/income/AddIncomeScreen.kt
  - feature/transaction/src/main/java/com/kshavrin/mymoney/feature/transaction/income/AddIncomeState.kt
  - feature/transaction/src/main/java/com/kshavrin/mymoney/feature/transaction/income/AddIncomeViewModel.kt
  - feature/transaction/src/main/res/values/strings.xml
  - feature/transaction/src/main/res/values-ru/strings.xml
  - app/src/androidTest/java/com/kshavrin/mymoney/MainActivityAddExpenseJourneyTest.kt
  - app/src/androidTest/java/com/kshavrin/mymoney/MainActivityCreateCategoryJourneyTest.kt
  - app/src/androidTest/java/com/kshavrin/mymoney/feature/transaction/expense/AddExpenseScreenUiTest.kt
  - app/src/androidTest/java/com/kshavrin/mymoney/feature/transaction/income/AddIncomeScreenUiTest.kt
  - feature/transaction/src/test/java/com/kshavrin/mymoney/feature/transaction/expense/AddExpenseViewModelTest.kt
  - feature/transaction/src/test/java/com/kshavrin/mymoney/feature/transaction/income/AddIncomeViewModelTest.kt
