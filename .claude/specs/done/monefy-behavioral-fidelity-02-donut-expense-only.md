# Donut shows expenses only + income/expense totals in the center (S01/S05)
Epic: monefy-behavioral-fidelity
Order: 02 of 09
Status: done
Depends-on: —
Date: 2026-06-01

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: Make the dashboard donut show ONLY expense categories (slices sum to 100% over total expense) and draw the income/expense totals in the donut center — income on top in green (primary), expense below in red (tertiary), each with the account currency symbol (05.jpg, e.g. "2 442 740,80 ₽" over "1 699 483,00 ₽"). Currently the donut mixes income+expense and the center is empty.
LAYERS: domain presentation
CHANGED_HINT: core/domain/.../model/BalanceSnapshot.kt (add `isExpense: Boolean = true` as the LAST field of CategoryBalance); core/domain/.../usecase/BalanceCalculator.kt L36-47 (tag expense rows isExpense=true / income rows false; KEEP emitting BOTH kinds in byCategory); feature/dashboard/.../DashboardViewModel.kt snapshotToSlices() L151-162 (filter isExpense; fraction = total / snapshot.expense.amount, guard signum()==0 -> 0f); core/designsystem/.../donut/MonefyDonutChart.kt (draw two center text lines; add params `currencySymbol: String = ""` + `decimalDigits: Int = 2`); feature/dashboard/.../DashboardScreen.kt L230 (pass currencySymbol/decimalDigits from state.currentCurrency); screenshot 05.jpg; builds on done/redesign-monefy-fidelity-02-donut-ring.md
TEST_TYPES: unit compose-ui
CONSTRAINTS:
  - Do NOT make BalanceCalculator.byCategory expense-only and do NOT touch BudgetEvaluator / BudgetEvaluatorTest / BalanceCalculatorTest — byCategory MUST keep BOTH kinds (BudgetEvaluator.kt:15 looks up by categoryId; BalanceCalculatorTest asserts byCategory.size == 3). Only ADD `isExpense` (last field, default true) so all existing positional/named constructors stay valid.
  - Donut fraction denominator = snapshot.expense.amount (expense slices sum to ~1.0). AS-14: % labels still only on slices >= 3%.
  - Center: capture MaterialTheme.colorScheme.primary (income, green) + .tertiary (expense, red) into local vals in the @Composable body — MaterialTheme is NOT accessible inside the Canvas DrawScope. Income line above the expense line; both horizontally centered in the hole via the existing textMeasurer/drawText. Format with MoneyFormatter (grouped; locale = LocalConfiguration.locales[0]; decimalDigits param; currencySymbol param; SymbolPosition.AFTER) -> "… ₽" matching 05.jpg.
  - Do NOT modify the donut_chart_cd string or add center-text semantics (avoid duplicate screen-reader output); keep the existing merged-semantics block.
  - No Roborazzi/screenshot baselines exist. Add a compose-ui test for the center text + a unit test asserting expense-only fraction (sums ~1.0) and isExpense tagging. English ids; no comments unless WHY.
=== END SPEC ===

## Gap / context
User note #7 (part a). BalanceCalculator concatenates expense+income into byCategory and computes
each fraction over (totalIncome + totalExpense) (BalanceCalculator.kt:36-38), so income categories
appear as donut slices and the percentages are wrong; MonefyDonutChart's center is currently empty
(it only draws arcs + perimeter icons). The reference (05.jpg) is an expense-only ring with income
(green) over expense (red) in the center.

## Implementation links
- Implemented: `1d569ec` feat: show expense-only donut with income/expense totals in center
- Tests: `aa4f9f7` test: cover donut expense-only + center totals
- Pushed: origin/main (`86ae077..aa4f9f7`) 2026-06-01
- Changed files:
  - `core/domain/.../model/BalanceSnapshot.kt` (add `CategoryBalance.isExpense`, last field, default true)
  - `core/domain/.../usecase/BalanceCalculator.kt` (tag expense/income; byCategory keeps both kinds)
  - `feature/dashboard/.../DashboardViewModel.kt` (`snapshotToSlices()` expense-only filter, denominator = `snapshot.expense.amount`)
  - `core/designsystem/.../donut/MonefyDonutChart.kt` (center income/expense totals; `currencySymbol`/`decimalDigits` params)
  - `feature/dashboard/.../DashboardScreen.kt` (thread currencySymbol/decimalDigits from state)
- Tests added: `DashboardViewModelTest.kt` (expense-only fraction ~1.0 + isExpense tagging), `MonefyDonutChartUiTest.kt` (compose-ui center totals — instrumented)
- Verification: 155 JVM unit tests passing / 0 failed; lint ok; Reviewer + Verifier `pass=true`
