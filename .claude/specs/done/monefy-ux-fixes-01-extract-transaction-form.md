# Ð’Ñ‹Ð½ÐµÑÑ‚Ð¸ Ð¾Ð±Ñ‰ÑƒÑŽ TransactionFormContent Ð² :core:designsystem (behavior-preserving)
Epic: monefy-ux-fixes
Order: 01 of 07
Status: done
Depends-on: â€”
Date: 2026-06-06

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: Ð˜Ð·Ð²Ð»ÐµÑ‡ÑŒ Ñ€Ð°ÑÐºÐ»Ð°Ð´ÐºÑƒ Ñ„Ð¾Ñ€Ð¼Ñ‹ Ñ‚Ñ€Ð°Ð½Ð·Ð°ÐºÑ†Ð¸Ð¸ (DateHeader + MonefyAmountInput + Ð¿Ð¾Ð»Ðµ Â«Ð—Ð°Ð¼ÐµÑ‚ÐºÐ°Â» + Ð´Ð²ÑƒÑ…ÑˆÐ°Ð³Ð¾Ð²Ñ‹Ð¹ Ð¿Ð¾Ñ‚Ð¾Ðº: MonefyKeypad + ÐºÐ½Ð¾Ð¿ÐºÐ° Â«Ð’Ð«Ð‘ÐžÐ  ÐšÐÐ¢Ð•Ð“ÐžÐ Ð˜Ð˜Â» â†’ embedded CategoryGrid) Ð² Ð½Ð¾Ð²Ñ‹Ð¹ domain-free ÐºÐ¾Ð¼Ð¿Ð¾Ð½ÐµÐ½Ñ‚ TransactionFormContent Ð² :core:designsystem, Ð¿Ð»ÑŽÑ UI-Ð½ÐµÐ¹Ñ‚Ñ€Ð°Ð»ÑŒÐ½ÑƒÑŽ Ð¼Ð¾Ð´ÐµÐ»ÑŒ ÑÐ¾ÑÑ‚Ð¾ÑÐ½Ð¸Ñ (TransactionFormState + ÐºÐ¾Ð»Ð±ÑÐºÐ¸/ÑÐ¾Ð±Ñ‹Ñ‚Ð¸Ñ Ñ„Ð¾Ñ€Ð¼Ñ‹). ÐŸÐµÑ€ÐµÐ²ÐµÑÑ‚Ð¸ AddIncomeScreen Ð¸ AddExpenseScreen Ð½Ð° Ñ€ÐµÐ½Ð´ÐµÑ€ ÑÑ‚Ð¾Ð³Ð¾ ÐºÐ¾Ð¼Ð¿Ð¾Ð½ÐµÐ½Ñ‚Ð°, Ð¼Ð°Ð¿Ð¿Ñ ÑÐ²Ð¾Ð¹ AddIncomeState/AddExpenseState â†’ Ñ„Ð¾Ñ€Ð¼-Ð¼Ð¾Ð´ÐµÐ»ÑŒ. Ð’Ð¸Ð·ÑƒÐ°Ð»ÑŒÐ½Ð¾ Ð¸ Ð¿Ð¾Ð²ÐµÐ´ÐµÐ½Ñ‡ÐµÑÐºÐ¸ â€” Ð‘Ð•Ð— Ð¸Ð·Ð¼ÐµÐ½ÐµÐ½Ð¸Ð¹; ÑÑ‚Ð¾ Ñ‡Ð¸ÑÑ‚Ñ‹Ð¹ Ñ€ÐµÑ„Ð°ÐºÑ‚Ð¾Ñ€-Ñ„ÑƒÐ½Ð´Ð°Ð¼ÐµÐ½Ñ‚, Ð½Ð° ÐºÐ¾Ñ‚Ð¾Ñ€Ð¾Ð¼ ÑÐ¾Ð±Ð¸Ñ€Ð°ÑŽÑ‚ÑÑ SPEC 02 (Ñ€Ð°ÑÐºÐ»Ð°Ð´ÐºÐ°), 03 (Ð´Ð°Ñ‚Ð°), 04 (ÑƒÐ½Ð¸Ñ„Ð¸ÐºÐ°Ñ†Ð¸Ñ edit).
LAYERS: presentation
CHANGED_HINT: new core/designsystem/src/main/java/com/kshavrin/mymoney/core/designsystem/form/TransactionFormContent.kt (+ TransactionFormState.kt + ÑÐ¾Ð±Ñ‹Ñ‚Ð¸Ñ/ÐºÐ¾Ð»Ð±ÑÐºÐ¸); promote feature/transaction/.../categorygrid/CategoryGrid.kt â†’ core/designsystem/.../form/ (git mv, ÑÐ´ÐµÐ»Ð°Ñ‚ÑŒ domain-free Ñ‡ÐµÑ€ÐµÐ· UI-Ð¼Ð¾Ð´ÐµÐ»ÑŒ ÐºÐ°Ñ‚ÐµÐ³Ð¾Ñ€Ð¸Ð¸); promote feature/transaction/.../DateHeader.kt â†’ :core:designsystem (ÐµÑÐ»Ð¸ Ð½Ðµ domain-free â€” ÑÐ´ÐµÐ»Ð°Ñ‚ÑŒ Ñ‚Ð°ÐºÐ¾Ð²Ñ‹Ð¼); rewire feature/transaction/.../income/AddIncomeScreen.kt + expense/AddExpenseScreen.kt (Ñ‚ÐµÐ»Ð¾ Column Ð½Ð° TransactionFormContent + Ð¼Ð°Ð¿Ð¿Ð¸Ð½Ð³ stateâ†’Ñ„Ð¾Ñ€Ð¼-Ð¼Ð¾Ð´ÐµÐ»ÑŒ); ÑÑ‚Ñ€Ð¾ÐºÐ¸ ÐºÐ½Ð¾Ð¿ÐºÐ¸/Ð·Ð°Ð¼ÐµÑ‚ÐºÐ¸ â€” Ð¿Ñ€Ð¾Ð±Ñ€Ð¾ÑÐ¸Ñ‚ÑŒ Ð¿Ð°Ñ€Ð°Ð¼ÐµÑ‚Ñ€Ð°Ð¼Ð¸ Ð¸Ð· :feature:transaction Ð˜Ð›Ð˜ Ð¿Ñ€Ð¾Ð´ÑƒÐ±Ð»Ð¸Ñ€Ð¾Ð²Ð°Ñ‚ÑŒ Ð² :core:designsystem (ÐºÐ°Ðº Ð´Ð»Ñ AmountFieldSection, PHASE_11 Decision 1)
TEST_TYPES: unit compose-ui
CONSTRAINTS:
  - :core:designsystem ÐžÐ¡Ð¢ÐÐÐ¢Ð¡Ð¯ domain-free â€” ÐÐ• Ð´Ð¾Ð±Ð°Ð²Ð»ÑÑ‚ÑŒ Ð·Ð°Ð²Ð¸ÑÐ¸Ð¼Ð¾ÑÑ‚ÑŒ Ð½Ð° :core:domain. ÐšÐ°Ñ‚ÐµÐ³Ð¾Ñ€Ð¸Ð¸ Ð¿ÐµÑ€ÐµÐ´Ð°Ð²Ð°Ñ‚ÑŒ UI-Ð½ÐµÐ¹Ñ‚Ñ€Ð°Ð»ÑŒÐ½Ð¾Ð¹ Ð¼Ð¾Ð´ÐµÐ»ÑŒÑŽ (Ð¿Ð¾ Ð¾Ð±Ñ€Ð°Ð·Ñ†Ñƒ CategorySlice Ð² donut/: id + name + colorHex + iconKey), Ð½Ðµ Ð´Ð¾Ð¼ÐµÐ½Ð½Ñ‹Ð¼ Category. ÐšÐ°Ð¶Ð´Ñ‹Ð¹ ÑÐºÑ€Ð°Ð½ Ð¼Ð°Ð¿Ð¿Ð¸Ñ‚ Ð´Ð¾Ð¼ÐµÐ½ â†’ Ñ„Ð¾Ñ€Ð¼-Ð¼Ð¾Ð´ÐµÐ»ÑŒ.
  - Ð¡Ð¾Ð±Ð»ÑŽÑÑ‚Ð¸ :feature:* âŠ¥ :feature:*. ÐŸÐµÑ€ÐµÐ¸ÑÐ¿Ð¾Ð»ÑŒÐ·Ð¾Ð²Ð°Ñ‚ÑŒ ÑƒÐ¶Ðµ Ð¾Ð±Ñ‰Ð¸Ðµ MonefyAmountInput, MonefyKeypad, Ð¿Ð¾Ð»Ð½Ñ‹Ð¹ ÐºÐ¾Ð½Ñ‚Ñ€Ð°ÐºÑ‚ KeypadEvent + CalculatorEngine (BR-7: Ð¾Ð´Ð½Ð° Ñ‚Ð¾Ñ‡ÐºÐ° Ð½Ð° Ð¾Ð¿ÐµÑ€Ð°Ð½Ð´), press-scale/haptic/sound.
  - Ð¡Ð¾Ñ…Ñ€Ð°Ð½Ð¸Ñ‚ÑŒ keypad-first Ð´Ð²ÑƒÑ…ÑˆÐ°Ð³Ð¾Ð²Ñ‹Ð¹ Ð¿Ð¾Ñ‚Ð¾Ðº (ÑˆÐ°Ð³ 1: ÐºÐ°Ñ€Ñ‚Ð¾Ñ‡ÐºÐ° ÑÑƒÐ¼Ð¼Ñ‹ + Ð·Ð°Ð¼ÐµÑ‚ÐºÐ° + inline-ÐºÐ»Ð°Ð²Ð¸Ð°Ñ‚ÑƒÑ€Ð° + full-width ÐºÐ½Ð¾Ð¿ÐºÐ° Â«Ð’Ð«Ð‘ÐžÐ  ÐšÐÐ¢Ð•Ð“ÐžÐ Ð˜Ð˜Â», Ð°ÐºÑ‚Ð¸Ð²Ð½Ð° Ð¿Ñ€Ð¸ amount > 0; ÑˆÐ°Ð³ 2: ÐºÐ°Ñ€Ñ‚Ð¾Ñ‡ÐºÐ° ÑÑƒÐ¼Ð¼Ñ‹ Ð¾ÑÑ‚Ð°Ñ‘Ñ‚ÑÑ + embedded CategoryGrid; Ñ‚Ð°Ð¿ Ð¿Ð¾ ÐºÐ°Ñ€Ñ‚Ð¾Ñ‡ÐºÐµ ÑÑƒÐ¼Ð¼Ñ‹ â†’ BackToAmount). ÐÐ• Ñ€ÐµÐ³Ñ€ÐµÑÑÐ¸Ñ€Ð¾Ð²Ð°Ñ‚ÑŒ monefy-behavioral-fidelity-01.
  - Ð¡Ð¾Ñ…Ñ€Ð°Ð½Ð¸Ñ‚ÑŒ AS-4 Â«+ Ð”ÐžÐ‘ÐÐ’Ð˜Ð¢Ð¬Â» round-trip: Ñ„Ð¾Ñ€Ð¼Ð° Ð¾Ñ‚Ð´Ð°Ñ‘Ñ‚ onAddCategory-ÐºÐ¾Ð»Ð±ÑÐº; Ð½Ð°Ð²Ð¸Ð³Ð°Ñ†Ð¸ÑŽ Ð½Ð° ÑÐ¾Ð·Ð´Ð°Ð½Ð¸Ðµ ÐºÐ°Ñ‚ÐµÐ³Ð¾Ñ€Ð¸Ð¸ Ð¸ Ð¿Ñ€Ð¸Ñ‘Ð¼ Ñ€ÐµÐ·ÑƒÐ»ÑŒÑ‚Ð°Ñ‚Ð° Ñ‡ÐµÑ€ÐµÐ· savedStateHandle Ð¿Ð¾-Ð¿Ñ€ÐµÐ¶Ð½ÐµÐ¼Ñƒ Ð´ÐµÐ»Ð°ÐµÑ‚ Route-Ð¾Ð±Ñ‘Ñ€Ñ‚ÐºÐ° ÑÐºÑ€Ð°Ð½Ð° (KEY_CREATED_CATEGORY_ID).
  - Ð—ÐµÑ€ÐºÐ°Ð»Ð¸Ñ‚ÑŒ expense Ð˜ income Ð´Ð¾ Ð¿Ð¾Ð»Ð½Ð¾Ð³Ð¾ Ð¿Ð°Ñ€Ð¸Ñ‚ÐµÑ‚Ð°. Ð‘ÐµÐ· Ð´Ð¾Ð¼ÐµÐ½Ð½Ñ‹Ñ…/data-Ð¸Ð·Ð¼ÐµÐ½ÐµÐ½Ð¸Ð¹; Ð±ÐµÐ· Ñ…Ð°Ñ€Ð´ÐºÐ¾Ð´Ð° ÑÑ‚Ñ€Ð¾Ðº/Ñ†Ð²ÐµÑ‚Ð¾Ð² (EN+RU); Ð°Ð½Ð³Ð»Ð¸Ð¹ÑÐºÐ¸Ðµ Ð¸Ð´ÐµÐ½Ñ‚Ð¸Ñ„Ð¸ÐºÐ°Ñ‚Ð¾Ñ€Ñ‹; ÐºÐ¾Ð¼Ð¼ÐµÐ½Ñ‚Ð°Ñ€Ð¸Ð¸ Ñ‚Ð¾Ð»ÑŒÐºÐ¾ Ð¿Ñ€Ð¸ Ð½ÐµÐ¾Ñ‡ÐµÐ²Ð¸Ð´Ð½Ð¾Ð¼ WHY.
  - ÐÑ€Ñ…Ð¸Ð²Ð¸Ñ€Ð¾Ð²Ð°Ñ‚ÑŒ, Ð½Ðµ ÑƒÐ´Ð°Ð»ÑÑ‚ÑŒ: Ð¿Ñ€Ð¸ Ð¿Ñ€Ð¾Ð¼Ð¾ÑƒÑ‚Ðµ â€” git mv; ÐµÑÐ»Ð¸ Ð¾ÑÑ‚Ð°Ñ‘Ñ‚ÑÑ Ð´ÑƒÐ±Ð»Ð¸ÐºÐ°Ñ‚ ÑÑ‚Ð°Ñ€Ð¾Ð³Ð¾ Ñ„Ð°Ð¹Ð»Ð°, Ð¿ÐµÑ€ÐµÐ¼ÐµÑÑ‚Ð¸Ñ‚ÑŒ ÐµÐ³Ð¾ Ð² archive/ Ñ€ÐµÐ¿Ð¾-ÐºÐ¾Ñ€Ð½Ñ Ð¸ ÑÐ¾Ð¾Ð±Ñ‰Ð¸Ñ‚ÑŒ Ð¿Ð¾Ð»ÑŒÐ·Ð¾Ð²Ð°Ñ‚ÐµÐ»ÑŽ.
=== END SPEC ===

## Gap / context
Ð¡ÐµÐ¹Ñ‡Ð°Ñ Ñ€Ð°ÑÐºÐ»Ð°Ð´ÐºÐ° Ñ„Ð¾Ñ€Ð¼Ñ‹ Ð¶Ð¸Ð²Ñ‘Ñ‚ Ð¿Ñ€ÑÐ¼Ð¾ Ð² AddIncomeScreen.kt:158-222 Ð¸ Ð·ÐµÑ€ÐºÐ°Ð»ÑŒÐ½Ð¾Ð¼ AddExpenseScreen, Ð°
CategoryGrid/DateHeader Ð»ÐµÐ¶Ð°Ñ‚ Ð² :feature:transaction. Ð”Ð»Ñ Ð¿ÑƒÐ½ÐºÑ‚Ð° 3 (edit = new, ÑÐºÑ€Ð°Ð½ Ñ€ÐµÐ´Ð°ÐºÑ‚Ð¸Ñ€Ð¾Ð²Ð°Ð½Ð¸Ñ â€”
Ð² :feature:transactionslist) Ð½ÑƒÐ¶ÐµÐ½ Ð¾Ð±Ñ‰Ð¸Ð¹ ÐºÐ¾Ð¼Ð¿Ð¾Ð½ÐµÐ½Ñ‚ Ð² :core:designsystem, Ð¸Ð½Ð°Ñ‡Ðµ Ð½Ð°Ñ€ÑƒÑˆÐ¸Ñ‚ÑÑ Ð¿Ñ€Ð°Ð²Ð¸Ð»Ð¾
:feature:* âŠ¥ :feature:*. Ð­Ñ‚Ð¾Ñ‚ SPEC ÑÐ¾Ð·Ð´Ð°Ñ‘Ñ‚ Ñ„ÑƒÐ½Ð´Ð°Ð¼ÐµÐ½Ñ‚ Ð±ÐµÐ· Ð²Ð¸Ð´Ð¸Ð¼Ñ‹Ñ… Ð¸Ð·Ð¼ÐµÐ½ÐµÐ½Ð¸Ð¹, Ñ‡Ñ‚Ð¾Ð±Ñ‹ 02â€“04 Ð¿Ñ€Ð°Ð²Ð¸Ð»Ð¸ Ð¾Ð´Ð½Ñƒ Ñ„Ð¾Ñ€Ð¼Ñƒ.

## Implementation links
- commit: 50f7f0e, 27ebe13, 082a587, 13b624d, 500472e
- files: core/designsystem/src/main/java/com/kshavrin/mymoney/core/designsystem/form/{CategoryGrid.kt,DateHeader.kt,TransactionFormContent.kt,TransactionFormState.kt}; core/designsystem/src/main/res/values*/strings.xml; feature/transaction/src/main/java/com/kshavrin/mymoney/feature/transaction/{expense/AddExpenseScreen.kt,income/AddIncomeScreen.kt,transfer/TransferScreen.kt}; app/src/androidTest/java/com/kshavrin/mymoney/{MainActivityAddExpenseJourneyTest.kt,MainActivityCreateCategoryJourneyTest.kt,feature/transaction/categorygrid/CategoryGridUiTest.kt,feature/transaction/expense/AddExpenseScreenUiTest.kt,feature/transaction/income/AddIncomeScreenUiTest.kt}; core/designsystem/src/androidTest/java/com/kshavrin/mymoney/core/designsystem/form/{DateHeaderUiTest.kt,TransactionFormContentUiTest.kt}; core/designsystem/src/test/java/com/kshavrin/mymoney/core/designsystem/form/CategoryGridColorParsingTest.kt

