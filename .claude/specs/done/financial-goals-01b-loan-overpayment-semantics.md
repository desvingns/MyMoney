# Financial-goal loan calculator — realign overpayment to "reduce payment, keep term" (FG-4)
Epic: financial-goals
Order: 01b of 06 (corrective follow-up to 01; run before 06)
Status: done
Depends-on: 01 (financial-goals-01-calculator-domain — already done/merged)
Date: 2026-06-05

## SPEC
=== SPEC ===
TASK: bugfix
PLATFORM: android
WHAT: Fix `GoalLoanCalculator` so the overpayment branch implements the LOCKED epic decision
**FG-4 / BR-FG-7 / AS-FG-credit-date = "reduce payment, keep term"** instead of the currently-merged
"reduce term, keep payment". Today the loan calc pays a constant `monthlyContribution` and stops when
`balance ≤ 0`, yielding `monthsToPayoff ≤ n` (the TERM shortens). The epic overview
(`financial-goals-00-overview.md`) locks the opposite: the surplus reduces principal, the contractual
annuity is recomputed downward over the remaining term, and **the payoff date stays at the entered term
(`monthsToPayoff == n` always)**; the projection must surface the **declining payment** vs. the
no-overpayment baseline (BR-FG-7). Also remove the dead recompute (see CONSTRAINTS) — in the current
code `plannedPayment = annuity + (monthlyContribution − annuity)` collapses to `monthlyContribution`, so
the per-iteration `pow()` annuity recompute is computed and discarded.
LAYERS: domain
CHANGED_HINT:
  - EDIT core/domain/src/main/kotlin/com/kshavrin/mymoney/core/domain/model/GoalCalculation.kt —
    extend `LoanProjection` to carry the reduce-payment outcome. Add (keep existing fields,
    additive so 06/forms aren't broken):
      `val finalMonthlyPayment: BigDecimal`   // the recomputed contractual annuity in the LAST term-month (the "declining payment" floor; == baseMonthlyPayment when no overpayment)
      `val interestSavedVsBaseline: BigDecimal` // baselineTotalInterest − totalInterest (≥ 0)
    `monthsToPayoff` stays an Int but its CONTRACT changes (see below): for overpayment it MUST equal
    `termMonths` now, not an early payoff month.
  - EDIT core/domain/src/main/kotlin/com/kshavrin/mymoney/core/domain/usecase/GoalLoanCalculator.kt —
    Replace the reduce-TERM loop (lines ~74–119) with a **reduce-payment, keep-term** simulation over a
    FIXED horizon of exactly `n` months:
      * `s = monthlyContribution − A0` is the constant monthly surplus (based on the ORIGINAL `A0`,
        NOT a recomputed annuity — this is the value the dead code was supposed to use).
      * Each month `m` in `0 until n`:
          `interest = balance · i`
          `recomputedAnnuity = annuity(balance, i, remainingTerm = n − m)`  // declining contractual payment over the remaining FIXED term
          `principalPart = (recomputedAnnuity − interest) + s`               // pay the recomputed annuity + the constant surplus
          `balance = (balance − principalPart).max(ZERO)`
          accumulate `paidInterest += interest`, `paidTotal += interest + principalPart`
          track `lastAnnuity = recomputedAnnuity`
      * The horizon is FIXED at `n`: **do NOT early-exit when balance hits 0**. If balance reaches 0
        before month n (it can, because the surplus accelerates payoff), clamp that month's
        `principalPart`/`interest` to the residual and let the remaining months contribute 0 — but
        `monthsToPayoff` is still reported as `n` (the entered term / payoff DATE is unchanged: FG-4 +
        AS-FG-credit-date "it does not produce an earlier second date").
      * Return: `monthsToPayoff = n`, `overpaymentApplied = true`, `underfunded = false`,
        `baseMonthlyPayment = A0` (unchanged), `finalMonthlyPayment = lastAnnuity` (the declined floor),
        `totalInterest = paidInterest`, `totalPaid = paidTotal`,
        `interestSavedVsBaseline = (A0·n − P) − paidInterest`.
    Keep `MathContext.DECIMAL64` internally, round all returned money HALF_UP 2 dp at the boundary.
    Keep the `principal == 0` early return and the `monthlyContribution < A0` underfunded branch exactly
    as they are (they already match BR-FG-6) — but set their new fields:
    `underfunded`/`principal==0` → `finalMonthlyPayment = baseMonthlyPayment`, `interestSavedVsBaseline = 0.00`.
  - EDIT core/domain/src/test/kotlin/com/kshavrin/mymoney/core/domain/usecase/GoalLoanCalculatorTest.kt —
    Update the overpayment expectations to the NEW contract and ADD worked fixtures (see CONSTRAINTS).
    The existing `overpayment_strictly_reduces_interest_vs_normal` test currently asserts
    `monthsToPayoff <= termMonths`; change it to assert `monthsToPayoff == termMonths` AND
    `over.finalMonthlyPayment < over.baseMonthlyPayment` AND `over.interestSavedVsBaseline > 0`.
TEST_TYPES: unit
CONSTRAINTS:
  - This CHANGES the merged behavior + tests from SPEC 01 deliberately; it is the correction. Do not try
    to keep the old `monthsToPayoff ≤ n` assertions — they encoded the wrong (reduce-term) semantics.
  - `BigDecimal` + `MathContext.DECIMAL64` throughout; never `Double`. Money HALF_UP 2 dp only on
    returned figures. Pure function, `@Inject constructor()`, no Android/repo/dispatcher deps (unchanged).
  - Remove the dead annuity recompute pattern: the OLD code computed `recomputedAnnuity` then cancelled
    it via `annuity + (monthly − annuity)`. The NEW code makes the recompute LOAD-BEARING (it is the
    declining payment) and adds the constant surplus `s` on top — verify by inspection that
    `finalMonthlyPayment` actually differs from `baseMonthlyPayment` in the overpayment fixture.
  - `GoalLoanCalculatorTest` MUST keep the green non-overpayment fixtures (zero-rate, underfunded
    888.49/661.85) and ADD ≥2 reduce-payment worked examples with hand-checked expectations:
      (a) **keep-term invariant**: monthly > A0 at a non-zero rate → `monthsToPayoff == n`,
          `finalMonthlyPayment < baseMonthlyPayment`, `totalInterest < (A0·n − P)`,
          `interestSavedVsBaseline == (A0·n − P) − totalInterest` (exact, 2 dp).
      (b) **surplus-from-A0, not recompute**: a case where the wrong (cancelling) formula and the right
          (A0-based `s`) formula produce DIFFERENT `totalInterest`, so the test would have failed under
          the old code — this pins that `s` is anchored to the original `A0`.
    Document the one hand-worked example inline (allowed WHY comment for the keep-term invariant).
  - English ids; no user-facing strings in this layer; no comments except the single allowed WHY for the
    keep-term recompute invariant.
=== END SPEC ===

## Why this SPEC exists (review finding, 2026-06-05)
Final review of the merged SPEC-01 (`f372a33` + `42175a2`) found two issues in
`GoalLoanCalculator.kt`:

1. **Semantic divergence from a locked decision (the real bug).** The overpayment branch pays a constant
   `monthlyContribution` and exits when `balance ≤ 0`, so `monthsToPayoff ≤ n` — i.e. it shortens the
   **term**. The epic overview locks the opposite in three places:
   - **FG-4**: "overpayment uses *reduce payment, keep term* … the payoff date stays at the entered term."
   - **BR-FG-7**: report "the **declining payment** vs. the no-overpayment baseline."
   - **AS-FG-credit-date**: "Overpayment keeps this date … it does not produce an earlier second date."
   SPEC-01's own prose was self-contradictory ("payoff still lands at month n" vs. "monthsToPayoff (≤ n)
   … stop when balance ≤ 0"), and the implementation + tests followed the wrong half. `LoanProjection`
   also has no field for the declining payment that BR-FG-7 requires.

2. **Dead / misleading code.** In the loop, `recomputedAnnuity` is computed via `pow()` each month but
   cancels: `surplus = monthly − recomputedAnnuity`, `plannedPayment = recomputedAnnuity + surplus =
   monthly`. Wasted compute + it visually implies reduce-payment semantics that aren't realized. The fix
   above makes that recompute load-bearing (it becomes the declining payment), resolving both findings at
   once.

What was VERIFIED correct in SPEC-01 and must stay: the savings projector (all four BR-FG-1..3 cases),
the annuity formula `A0` (hand-checked 888.49 / 661.85 at 12%, n=12, P=10000), `capitalVsBalanceDelta`,
the `principal ≤ 0` and `termMonths < 1` guards, BigDecimal/DECIMAL64/HALF_UP discipline, and Hilt
`@Inject` purity.

## Decision required at implement time (one question)
Before running this, confirm with the user that the **projection should keep the term fixed at the
entered loan date and show a declining payment (FG-4)**, vs. the as-merged "fixed payment, earlier
payoff" reading. FG-4/BR-FG-7/AS-FG-credit-date are the recommended (and currently authoritative)
default; this SPEC is written to that default. If the user instead wants the as-merged reduce-term
behavior, the fix collapses to just deleting the dead recompute and updating the overview's FG-4 — do
NOT silently pick that without confirmation.

## Implementation links
- commit: fc4923f (impl), 84e1763 (edge fix), 153d3ca (tests)
- files: core/domain/src/main/kotlin/com/kshavrin/mymoney/core/domain/model/GoalCalculation.kt, core/domain/src/main/kotlin/com/kshavrin/mymoney/core/domain/usecase/GoalLoanCalculator.kt, core/domain/src/test/kotlin/com/kshavrin/mymoney/core/domain/usecase/GoalLoanCalculatorTest.kt
