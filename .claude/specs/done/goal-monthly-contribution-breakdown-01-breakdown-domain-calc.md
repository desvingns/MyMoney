# Contribution breakdown — domain model + calculator
Epic: goal-monthly-contribution-breakdown
Order: 01 of 03
Status: done
Depends-on: —
Date: 2026-06-07

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: Add the pure-JVM domain for the income/expense breakdown that derives the monthly contribution.
NO persistence, NO UI, NO I/O — deterministic, fully unit-testable (mirrors the `GoalSavingsProjector` /
`CapitalBalanceDelta` pure-use-case style in `:core:domain`). Two immutable models + one tiny calculator:
(1) `ContributionItem(name, amount)` — one income or expense line; (2) `ContributionBreakdown(enabled,
incomes, expenses)` — the whole advanced state; (3) `ContributionCalculator.invoke(breakdown): BigDecimal`
= `sum(incomes.amount) − sum(expenses.amount)` (BigDecimal). Also extend the `Goal` aggregate so the
breakdown travels with it (consumed by SPEC-02 persistence and SPEC-03 save()).
LAYERS: domain
CHANGED_HINT:
  - NEW core/domain/src/main/kotlin/com/kshavrin/mymoney/core/domain/model/ContributionBreakdown.kt — (G3, G4)
      `data class ContributionItem(val name: String, val amount: BigDecimal)`
      `data class ContributionBreakdown(val enabled: Boolean = false, val incomes: List<ContributionItem> = emptyList(), val expenses: List<ContributionItem> = emptyList())`
      with a `companion`/`val EMPTY` default. Money is `BigDecimal`. `name` may be blank (D4).
  - NEW core/domain/src/main/kotlin/com/kshavrin/mymoney/core/domain/usecase/ContributionCalculator.kt — (G4, G12)
      `class ContributionCalculator @Inject constructor() { operator fun invoke(breakdown: ContributionBreakdown): BigDecimal }`.
      Logic: `incomes.sumOf { it.amount } − expenses.sumOf { it.amount }` using `BigDecimal` folds
      (`fold(BigDecimal.ZERO) { acc, i -> acc.add(i.amount) }`). Result may be negative or zero (D3). Pure —
      no rounding mode needed (only add/subtract); no Clock, no repository, no Android types.
  - core/domain/src/main/kotlin/com/kshavrin/mymoney/core/domain/model/Goal.kt — add a field
      `val contributionBreakdown: ContributionBreakdown = ContributionBreakdown()` (default keeps existing
      construction sites compiling; SPEC-02 maps it, SPEC-03 save() populates it). (G7) Keep the data class
      otherwise unchanged.
  - NEW core/domain/src/test/kotlin/com/kshavrin/mymoney/core/domain/usecase/ContributionCalculatorTest.kt — (unit)
TEST_TYPES: unit
CONSTRAINTS:
  - Pure function: NO repository, NO dispatcher, NO Android types — `BigDecimal` only. `@Inject` constructor
    so it is Hilt-injectable into the ViewModel later (SPEC-03), but it touches nothing injectable.
  - Money is `BigDecimal`, never `Double`. No rounding mode required (addition/subtraction only); do NOT
    introduce a scale change — let `add`/`subtract` keep natural scale (display formatting is the UI's job, G4).
  - `ContributionCalculatorTest` MUST include ≥3 worked examples committed as fixtures with hand-checked
    expectations:
      | incomes (amount)                | expenses (amount)        | expected total |
      |---------------------------------|--------------------------|----------------|
      | (empty)                         | (empty)                  | 0              |
      | 50000 ("зарплата"), 10000       | 20000 ("аренда"), 5000   | 40000          |
      | 30000                           | 40000                    | -10000 (D3)    |
    Plus: a row with `amount = ZERO` (blank-parsed by the VM, D4) contributes 0; name value never affects
    the total (blank vs filled names give the same sum).
  - Adding the `Goal.contributionBreakdown` field with a default must NOT break existing `Goal(...)`
    construction in `GoalEditViewModel.save()` (G2 `:202-218`) or `Mappers` (G7) — they compile via the
    default until SPEC-02/03 wire the real value.
  - English ids; this layer has no user-facing strings; no comments unless WHY.
=== END SPEC ===

## Acceptance (UI-agnostic)
```gherkin
Feature: Monthly contribution derived from an income/expense breakdown
  Covers the breakdown calculator domain (epic goal-monthly-contribution-breakdown).

  Scenario: Incomes minus expenses
    Given a breakdown with incomes 50000 and 10000
    And expenses 20000 and 5000
    When the monthly contribution is computed
    Then the result is 40000

  Scenario: Expenses exceed incomes
    Given a breakdown with income 30000 and expense 40000
    When the monthly contribution is computed
    Then the result is -10000

  Scenario: Empty breakdown
    Given a breakdown with no incomes and no expenses
    When the monthly contribution is computed
    Then the result is 0

  Scenario: Names do not affect the total
    Given two breakdowns with identical amounts but different (or blank) row names
    When each monthly contribution is computed
    Then both results are equal
```

## Gap / context
There is no aggregation that turns a list of income/expense lines into a single monthly figure — today
`monthlyContribution` is a single hand-typed number. This SPEC adds the deterministic, unit-tested domain
(`sum incomes − sum expenses`) and threads the breakdown onto the `Goal` aggregate so it can be persisted
(SPEC-02) and edited (SPEC-03). Keeping it pure `:core:domain` lets the riskiest logic be covered by JVM tests.

## Implementation links
- commit: 0dc1e02d (feat: domain + calculator) · decde445 (test: SPEC cross-check comments)
- files:
  - core/domain/src/main/kotlin/com/kshavrin/mymoney/core/domain/model/ContributionBreakdown.kt (new)
  - core/domain/src/main/kotlin/com/kshavrin/mymoney/core/domain/usecase/ContributionCalculator.kt (new)
  - core/domain/src/main/kotlin/com/kshavrin/mymoney/core/domain/model/Goal.kt (+contributionBreakdown field, default)
  - core/domain/src/test/kotlin/com/kshavrin/mymoney/core/domain/usecase/ContributionCalculatorTest.kt (new; 5 tests green via :core:domain:test)
- Note: SPEC fixture table row 2 printed 40000; correct hand-check is 35000 (60000−25000). Production + test follow the math.
