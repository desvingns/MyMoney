# Contribution breakdown — form UI + ViewModel
Epic: goal-monthly-contribution-breakdown
Order: 03 of 03
Status: done
Depends-on: 01, 02
Date: 2026-06-07

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: Add the advanced-settings UI to the goal create/edit form. Under the "Monthly contribution" field
add a checkbox **«Расширенные настройки ежемесячного пополнения»**. When checked: the monthly field goes
**read-only** and shows the computed total; two repeatable sections appear — **«Ежемесячный доход»** and
**«Ежемесячные расходы»** — each a list of rows (optional name + amount), each section with a **«+»** to
add a row and a delete affordance per row. The monthly contribution recomputes live as
`sum(incomes) − sum(expenses)` via `ContributionCalculator` (SPEC-01). Available in BOTH variants (D2).
On enable, seed one empty income + one empty expense row (D4). Toggling off keeps the last computed value
(field editable again) and retains the rows (D5). The breakdown is saved on the `Goal` (persisted by SPEC-02).
LAYERS: presentation
CHANGED_HINT:
  - feature/dictionaries/.../goals/GoalEditViewModel.kt — (G2)
      • inject `ContributionCalculator` (SPEC-01) alongside the existing projector/loanCalculator (`:35-42`).
      • State (`GoalEditState`, `:233-258`): add
          `advancedContribution: Boolean = false`,
          `incomeRows: List<ContributionRowUi> = emptyList()`,
          `expenseRows: List<ContributionRowUi> = emptyList()`
        and a presentation-local `data class ContributionRowUi(val name: String = "", val amount: String = "")`.
      • Events (`GoalEditEvent`, `:260-272`): add
          `AdvancedToggled(enabled: Boolean)`, `IncomeAdded`, `ExpenseAdded`,
          `IncomeRemoved(index: Int)`, `ExpenseRemoved(index: Int)`,
          `IncomeNameChanged(index, value)`, `IncomeAmountChanged(index, value)`,
          `ExpenseNameChanged(index, value)`, `ExpenseAmountChanged(index, value)`.
      • `onEvent` (`:78-122`): handle them. `AdvancedToggled(true)` → set flag, seed one empty income + one
        empty expense row IF both lists empty (D4), then `recompute()`. `AdvancedToggled(false)` → clear flag,
        KEEP rows, KEEP current `monthlyContribution` value (it stays the last computed number, now editable) (D5).
        Row add/remove/name/amount → update the list immutably, then `recompute()`.
      • `recompute()` (`:138-194`): when `advancedContribution`, build a `ContributionBreakdown` from the rows
        (name as-is, `amount = row.amount.parseMoney()` — blank/invalid → 0, G4) and set
        `monthlyContribution = ContributionCalculator(breakdown).toPlainString()` BEFORE the existing projector/
        loan recompute (so both the savings projection and the loan calc consume the derived number, G3/G5).
        Result may be ≤ 0 → projector already yields UNREACHABLE (D3) — no extra handling.
      • init load (`:52-76`): read `existing.contributionBreakdown`; if `enabled`, set `advancedContribution=true`
        and map its items → `ContributionRowUi` (amount via `BigDecimal.toPlainString()`); then `recompute()`.
      • `save()` (`:196-222`): set `contributionBreakdown = ContributionBreakdown(enabled = advancedContribution,
        incomes = incomeRows.map { ContributionItem(it.name, it.amount.parseMoney()) }, expenses = …)` on the
        built `Goal`. Persist as today.
  - feature/dictionaries/.../goals/GoalEditScreen.kt — (G1, G6)
      • directly under the monthly-contribution `OutlinedTextField` (`:221-228`) and BEFORE the target-amount
        field (`:230`), add a `Row { Checkbox(checked = state.advancedContribution, onCheckedChange = { onEvent(AdvancedToggled(it)) }); Text(stringResource(R.string.goal_advanced_contribution)) }`.
      • make the monthly field `readOnly = state.advancedContribution` (reuse the read-only pattern at `:172-183`/`:298-314`, G6); keep its value bound to `state.monthlyContribution`.
      • when `state.advancedContribution`, render two sections (a small private `@Composable BreakdownSection`):
        header `Text` (income/expense), the rows (each a `Row` with a name `OutlinedTextField` (optional, hint)
        + an amount `OutlinedTextField` (decimal keyboard) + a delete `IconButton(Icons.Filled.Delete)`), and a
        trailing `TextButton`/`IconButton` "+ добавить" → `IncomeAdded`/`ExpenseAdded`. Use `itemsIndexed`-style
        index for the *Changed/*Removed events. Place the sections between the checkbox and the target field.
      • keep the credit branch (`CreditFields`, `:251-253`) unchanged — it just consumes the (now possibly
        derived) `monthlyContribution` (D2/G5).
  - feature/dictionaries/src/main/res/values/strings.xml + values-ru/strings.xml — add (no hardcoded text, G10):
      `goal_advanced_contribution` (EN "Advanced monthly-contribution settings" / RU "Расширенные настройки
      ежемесячного пополнения"), `goal_monthly_income` ("Monthly income" / "Ежемесячный доход"),
      `goal_monthly_expense` ("Monthly expenses" / "Ежемесячные расходы"),
      `goal_contribution_row_name` ("Name" / "Название"),
      `goal_contribution_add_income` ("Add income" / "Добавить доход"),
      `goal_contribution_add_expense` ("Add expense" / "Добавить расход"),
      `goal_contribution_remove` (content description "Remove row" / "Удалить строку").
  - feature/dictionaries/src/test/.../goals/GoalEditSavingsViewModelTest.kt — extend (unit, Turbine; inject the
    REAL `ContributionCalculator`, no fake needed). (G11)
  - feature/dictionaries/src/test/.../goals/GoalEditSavingsContentTest.kt — extend (compose-ui/Robolectric). (G11)
TEST_TYPES: compose-ui unit
CONSTRAINTS:
  - ViewModel-test cases: toggling advanced ON seeds one empty income + one expense row (D4) and makes the
    monthly field value derived; entering incomes/expenses recomputes monthly = sum−sum (assert 50000+10000
    − 20000−5000 = 40000); expenses > incomes → negative monthly → projection UNREACHABLE (D3); toggling OFF
    keeps the last computed monthly value AND retains the rows (D5); add/remove row updates the total; blank
    amount parses to 0; SaveClicked persists a `Goal` whose `contributionBreakdown` has `enabled` + the rows;
    loading an existing goal with an enabled breakdown pre-fills the checkbox + rows.
  - Content-test cases: the checkbox renders under the monthly field; checking it makes the monthly field
    read-only and reveals the income/expense sections with one empty row each; "+ добавить" adds a row; the
    delete icon removes one; the computed total shows in the (read-only) monthly field.
  - Reuse existing form patterns — `OutlinedTextField`, the read-only field idiom (G6), `IconButton`. Do NOT
    add a new shared component or pull in another `:feature:*` (G12). Keep the screen scrollable (it already
    is, `verticalScroll` at `GoalEditScreen.kt:118`).
  - The monthly field is the ONLY place the derived number surfaces (O1): show the computed value in the
    disabled field + the existing projector status text below; no new error/label string for ≤ 0.
  - Runner compiles the dictionaries module's androidTest (G11) — keep any touched test code compiling;
    extend the existing fakes under `…/goals/fake/` only if a new collaborator needs faking (the calculator
    is a real pure class — use it directly).
  - English ids; EN default + RU strings; no comments unless WHY.
=== END SPEC ===

## Acceptance (UI-agnostic)
```gherkin
Feature: Advanced monthly-contribution settings on the goal form
  Covers the breakdown form UI (epic goal-monthly-contribution-breakdown).

  Background:
    Given the user is editing a goal with the monthly-contribution field visible

  Scenario: Enable advanced settings
    When the user enables "advanced monthly-contribution settings"
    Then the monthly-contribution field becomes read-only
    And one empty income row and one empty expense row appear

  Scenario: Monthly contribution is derived
    Given advanced settings are enabled
    When the user enters incomes 50000 and 10000 and expenses 20000 and 5000
    Then the monthly contribution shows 40000

  Scenario: Add and remove rows
    Given advanced settings are enabled
    When the user adds another income row and removes an expense row
    Then the monthly contribution recomputes from the remaining rows

  Scenario: Negative total is allowed
    Given advanced settings are enabled
    When total expenses exceed total incomes
    Then the monthly contribution shows the negative number
    And the goal projection reports it as unreachable

  Scenario: Disable advanced settings keeps the value and rows
    Given advanced settings are enabled with a computed contribution of 40000
    When the user disables the advanced settings
    Then the monthly-contribution field shows 40000 and becomes editable again
    And re-enabling the advanced settings restores the previous rows

  Scenario: Reopen a saved goal
    Given a saved goal whose advanced breakdown was enabled with rows
    When the user reopens that goal for editing
    Then the checkbox is enabled and the income/expense rows are restored
```

## Gap / context
SPEC-01/02 give a calculated, persisted breakdown but no way to enter it. This SPEC adds the actual form:
the checkbox, the read-only computed field, the add/remove income & expense rows, the live recompute, and
the save wiring — completing the user-visible feature on top of the finished domain + DB.

## Implementation links
- commit: ad795e3b (form UI + VM), ecc8c3ef (ui-designer tokens), 768bb891 (test-expectation fix)
- files:
  - core/ui/src/main/java/com/kshavrin/mymoney/core/ui/theme/Color.kt
  - core/ui/src/main/java/com/kshavrin/mymoney/core/ui/theme/Typography.kt
  - feature/dictionaries/src/main/java/com/kshavrin/mymoney/feature/dictionaries/goals/GoalEditViewModel.kt
  - feature/dictionaries/src/main/java/com/kshavrin/mymoney/feature/dictionaries/goals/GoalEditScreen.kt
  - feature/dictionaries/src/main/res/values/strings.xml
  - feature/dictionaries/src/main/res/values-ru/strings.xml
  - feature/dictionaries/src/test/.../goals/GoalEditSavingsViewModelTest.kt (+11 VM tests)
  - feature/dictionaries/src/test/.../goals/GoalEditSavingsContentTest.kt (+22 state-contract tests)
  - feature/dictionaries/src/test/.../goals/GoalEditCreditViewModelTest.kt (ctor compile-fix)
- verification: :feature:dictionaries:testDebugUnitTest 178 passed; androidTest NO-SOURCE (compiles); :core:ui compiled. Runner script pass:false was the known false-negative (no :app:detekt/:app:jacoco tasks; :app:-only). Verifier pass:true.
- note: SPEC arithmetic typo — multi-row example sum−sum = 35000 (not 40000); test corrected.
