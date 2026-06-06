# Financial-goal calculator domain — savings projection + annuity loan with overpayment
Epic: financial-goals
Order: 01 of 06
Status: done
Depends-on: —
Date: 2026-06-05

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: Add the pure-JVM calculator that powers both goal variants, as `:core:domain` use-cases with
immutable input/output models. NO persistence, NO UI, NO I/O — just deterministic math so it is fully
unit-testable (mirrors why `CalculatorEngine`/`TransferExecutor` are plain classes). Two use-cases:
(1) `GoalSavingsProjector` — given target/startingCapital/monthlyContribution + `today`, compute the
achievement date (BR-FG-1..3); (2) `GoalLoanCalculator` — given target/startingCapital/annualRate/
termMonths/monthlyContribution, compute the annuity payment and the reduce-payment overpayment outcome
(BR-FG-5..7). Also a tiny `capitalVsBalanceDelta(currentBalance, startingCapital)` helper (BR-FG-4).
LAYERS: domain
CHANGED_HINT:
  - NEW core/domain/src/main/kotlin/com/kshavrin/mymoney/core/domain/model/GoalCalculation.kt — input/output
    data classes (all money `BigDecimal`):
      `data class SavingsGoalInput(val targetAmount: BigDecimal, val startingCapital: BigDecimal, val monthlyContribution: BigDecimal)`
      `data class SavingsProjection(val monthsToGoal: Int?, val achievementDate: LocalDate?, val status: GoalStatus)`
      `enum class GoalStatus { ON_TRACK, ALREADY_ACHIEVED, UNREACHABLE }`
      `data class LoanGoalInput(val targetAmount: BigDecimal, val startingCapital: BigDecimal, val annualRatePercent: BigDecimal, val termMonths: Int, val monthlyContribution: BigDecimal)`
      `data class LoanProjection(val principal: BigDecimal, val baseMonthlyPayment: BigDecimal, val totalInterest: BigDecimal, val totalPaid: BigDecimal, val monthsToPayoff: Int, val underfunded: Boolean, val overpaymentApplied: Boolean)`
  - NEW core/domain/src/main/kotlin/com/kshavrin/mymoney/core/domain/usecase/GoalSavingsProjector.kt —
    `class GoalSavingsProjector @Inject constructor() { operator fun invoke(input: SavingsGoalInput, today: LocalDate): SavingsProjection }`.
    Logic: `remaining = target − startingCapital`. `remaining.signum() <= 0` → `(0, today, ALREADY_ACHIEVED)`.
    `monthlyContribution.signum() <= 0` → `(null, null, UNREACHABLE)`. Else
    `months = remaining.divide(monthly, 0, RoundingMode.CEILING).toInt()`, `date = today.plusMonths(months.toLong())`,
    status `ON_TRACK`. Pure — `today` is a parameter (no Clock injection), mirroring `TransferExecutor(now)`.
  - NEW core/domain/src/main/kotlin/com/kshavrin/mymoney/core/domain/usecase/GoalLoanCalculator.kt —
    `class GoalLoanCalculator @Inject constructor() { operator fun invoke(input: LoanGoalInput): LoanProjection }`.
    `MathContext.DECIMAL64`. `P = (target − startingCapital).max(ZERO)`. `i = annualRatePercent / 100 / 12`.
    Base annuity `A0`: if `i == 0` → `P / n`; else `A0 = P·i·(1+i)^n / ((1+i)^n − 1)` (compute `(1+i)^n` by
    `BigDecimal.pow(n)`). If `monthlyContribution < A0` → `underfunded=true, overpaymentApplied=false`,
    `totalInterest = A0·n − P`, `totalPaid = A0·n`, `monthsToPayoff = n`. Else **reduce-payment overpayment
    simulation** (term fixed = n): iterate months; each month interest `= balance·i`, the constant surplus
    `s = monthlyContribution − A0` is applied as extra principal, the contractual annuity is recomputed on the
    reduced balance over the remaining term so payoff still lands at month n; accumulate paid interest; stop
    when `balance <= 0` (clamp last payment). Return `totalInterest`, `totalPaid`, actual `monthsToPayoff`
    (≤ n), `overpaymentApplied=true`. Round money to 2 dp (`HALF_UP`) only at the boundary, keep DECIMAL64 internally.
  - NEW core/domain/src/main/kotlin/com/kshavrin/mymoney/core/domain/usecase/CapitalBalanceDelta.kt —
    small pure fun `fun capitalVsBalanceDelta(currentBalance: BigDecimal, startingCapital: BigDecimal): BigDecimal = currentBalance.subtract(startingCapital)`
    (the UI maps sign → "останется"/"не хватает"/exact; keep the *logic* here, the *strings* in the screen).
  - NEW core/domain/src/test/kotlin/com/kshavrin/mymoney/core/domain/usecase/GoalSavingsProjectorTest.kt
  - NEW core/domain/src/test/kotlin/com/kshavrin/mymoney/core/domain/usecase/GoalLoanCalculatorTest.kt
TEST_TYPES: unit
CONSTRAINTS:
  - Pure functions: NO repository, NO dispatcher, NO Android types. `LocalDate`/`BigDecimal` only. `@Inject`
    constructors so they are Hilt-injectable into ViewModels later, but they touch nothing injectable.
  - `BigDecimal` + `MathContext.DECIMAL64` throughout; never `Double`. Ceiling for savings months
    (you must fully fund the last partial month). Money rounded HALF_UP 2 dp only on returned figures.
  - `GoalLoanCalculatorTest` MUST include ≥3 worked examples committed as fixtures with hand-checked
    expectations: (a) zero-rate (`i==0`: A0 = P/n exactly; totalInterest = 0); (b) normal annuity, no
    overpayment (monthly == A0: totalInterest = A0·n − P; monthsToPayoff == n); (c) overpayment
    (monthly > A0: monthsToPayoff < n OR totalInterest < normal-case interest — overpayment must strictly
    reduce interest). Plus edge cases: `P<=0` (down payment ≥ target → principal 0, payment 0); `termMonths<=0`
    guarded (require n>=1). `GoalSavingsProjectorTest`: ON_TRACK ceiling rounding, ALREADY_ACHIEVED
    (startingCapital ≥ target → today), UNREACHABLE (monthly ≤ 0 → null date), exact-division boundary.
  - English ids; no hardcoded user-facing strings (this layer has none); no comments unless WHY
    (e.g. the one-line WHY for the reduce-payment recompute invariant is allowed).
=== END SPEC ===

## Gap / context
No financial math exists in the codebase (`CalculatorEngine` is arithmetic-input only). Both goal
variants need deterministic, unit-tested projections — the savings achievement date (FG-3) and the
annuity loan payment + reduce-payment overpayment outcome (FG-4). Keeping this as pure `:core:domain`
use-cases lets the ViewModels (SPEC 05/06) stay thin and lets the riskiest logic be covered by JVM tests.

## Implementation links
- commit: f372a33 (impl), 42175a2 (tests)
- files: core/domain/src/main/kotlin/com/kshavrin/mymoney/core/domain/model/GoalCalculation.kt, core/domain/src/main/kotlin/com/kshavrin/mymoney/core/domain/usecase/GoalSavingsProjector.kt, core/domain/src/main/kotlin/com/kshavrin/mymoney/core/domain/usecase/GoalLoanCalculator.kt, core/domain/src/main/kotlin/com/kshavrin/mymoney/core/domain/usecase/CapitalBalanceDelta.kt, core/domain/src/test/kotlin/com/kshavrin/mymoney/core/domain/usecase/GoalSavingsProjectorTest.kt, core/domain/src/test/kotlin/com/kshavrin/mymoney/core/domain/usecase/GoalLoanCalculatorTest.kt, core/domain/src/test/kotlin/com/kshavrin/mymoney/core/domain/usecase/CapitalBalanceDeltaTest.kt
