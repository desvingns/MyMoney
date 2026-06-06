# Goal create/edit screen — credit/mortgage variant (S29)
Epic: financial-goals
Order: 06 of 06
Status: done
Depends-on: 05
Date: 2026-06-05

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: Complete the **S29** form's CREDIT branch — the mortgage/credit calculator. When the variant toggle
is "С кредитом", show: an interest-rate field, a term/payoff **date input** (the loan term end), a
read-only **monthly payment** (annuity, via `GoalLoanCalculator`), and an overpayment summary that
reflects FG-4 (when the monthly set-aside exceeds the payment, the surplus reduces the payment; show total
interest / total paid and an underfunded warning). Save persists a CREDIT `Goal`.
LAYERS: presentation
CHANGED_HINT:
  - feature/dictionaries/.../goals/GoalEditScreen.kt — fill the CREDIT-only region (shown when
    `variant == CREDIT`): `OutlinedTextField` for annual interest-rate % → `RateChanged`; a term-date
    picker (Material3 `DatePickerDialog`; the chosen date is the loan term end) → `TermDateChanged`,
    displayed read-only as the "дата погашения"; read-only `Text` monthly payment from
    `state.loanProjection?.baseMonthlyPayment` (the read-only "ежемесячный платёж"); an overpayment summary
    block — total interest, total paid, and a note that the monthly set-aside above the payment lowers
    future payments (FG-4); an underfunded warning when `loanProjection.underfunded` (monthly < payment).
    The shared fields (name/icon/account/balance/starting-capital/monthly/target) from SPEC 05 are reused as-is.
    NOTE: the date field is an INPUT here (AS-FG-credit-date) — unlike the savings variant where it is computed.
  - feature/dictionaries/.../goals/GoalEditViewModel.kt — also inject `GoalLoanCalculator`. Add state:
    `annualRatePercent: String`, `termDate: LocalDate?`, `loanProjection: LoanProjection?`. When
    `variant == CREDIT` and rate/termDate/target/startingCapital/monthly change, recompute the projection:
    `termMonths = ChronoUnit.MONTHS.between(YearMonth.now()/today, termDate)` (require ≥ 1; clamp/disable
    Save otherwise), then `GoalLoanCalculator(LoanGoalInput(target, startingCapital, ratePercent, termMonths,
    monthly))`. `SaveClicked` with `variant == CREDIT` → build `Goal(variant = CREDIT, annualRatePercent =
    parsed, termDate = chosen, …)` → `upsert` → `NavigateBack`. Switching the toggle back to SAVINGS clears
    rate/termDate from the saved entity (null).
  - feature/dictionaries/src/main/res/values/strings.xml + values-ru — add: `goal_interest_rate`,
    `goal_term_date` ("Дата погашения"), `goal_monthly_payment` ("Ежемесячный платёж"), `goal_total_interest`,
    `goal_total_paid`, `goal_overpayment_note`, `goal_underfunded`.
  - feature/dictionaries/src/test/.../goals/GoalEditCreditViewModelTest.kt (NEW, unit, Turbine) — selecting
    CREDIT + rate + termDate + target computes a monthly payment (matches `GoalLoanCalculator`); monthly <
    payment → underfunded flag; monthly > payment → overpayment reduces total interest vs. the no-overpayment
    baseline; termDate ≤ today disables Save; SaveClicked upserts a CREDIT Goal with rate + termDate.
  - feature/dictionaries/src/test/.../goals/GoalEditCreditContentTest.kt (NEW, compose-ui / Robolectric) —
    toggling to CREDIT reveals rate / term-date / monthly-payment fields; toggling back hides them.
TEST_TYPES: compose-ui unit
CONSTRAINTS:
  - Annuity math + reduce-payment overpayment come ENTIRELY from `GoalLoanCalculator` (SPEC 01) — the screen
    only collects inputs and renders `LoanProjection`. Do NOT reimplement the math in the ViewModel/UI.
  - `termMonths` is derived from the entered term date (AS-FG-credit-date): the credit date is an INPUT
    (loan term end), whereas the savings achievement date is COMPUTED (SPEC 05). Guard `termMonths ≥ 1`.
  - Interest rate parsed as `BigDecimal` percent (e.g. "12.5" → 12.5 %). Money fields `BigDecimal`.
  - Reuse SPEC-05's shared form + ViewModel; this SPEC only adds the credit branch. Keep
    `:feature:dictionaries → :feature:*` at zero. English ids; no hardcoded strings (EN + RU); no comments unless WHY.
=== END SPEC ===

## Gap / context
SPEC 05 builds the savings branch and leaves the CREDIT-only fields stubbed. This SPEC turns the form into
the full credit/mortgage calculator the user asked for — rate + term date in, read-only monthly payment +
overpayment effect out — consuming `GoalLoanCalculator` from SPEC 01.

## Implementation links
- commits: `fad024e` (M3 design tokens) · `01122a4` (credit form branch) · `91a0758` (fix: clear credit fields on toggle→savings) · `8415b41` (credit-form tests)
- files:
  - core/ui/src/main/java/com/kshavrin/mymoney/core/ui/theme/Color.kt
  - core/ui/src/main/java/com/kshavrin/mymoney/core/ui/theme/Typography.kt
  - feature/dictionaries/src/main/java/com/kshavrin/mymoney/feature/dictionaries/goals/GoalEditViewModel.kt
  - feature/dictionaries/src/main/java/com/kshavrin/mymoney/feature/dictionaries/goals/GoalEditScreen.kt
  - feature/dictionaries/src/main/res/values/strings.xml
  - feature/dictionaries/src/main/res/values-ru/strings.xml
  - feature/dictionaries/src/test/kotlin/com/kshavrin/mymoney/feature/dictionaries/goals/GoalEditCreditViewModelTest.kt (new)
  - feature/dictionaries/src/test/kotlin/com/kshavrin/mymoney/feature/dictionaries/goals/GoalEditCreditContentTest.kt (new)
  - feature/dictionaries/src/test/kotlin/com/kshavrin/mymoney/feature/dictionaries/goals/GoalEditSavingsViewModelTest.kt (shared harness)
- verification: `:feature:dictionaries:testDebugUnitTest` BUILD SUCCESSFUL (full module green); Reviewer ✅ no layer violations; Verifier ✅ pass (nav/Hilt/strings/tests).
- notes: pushed to origin/main (`8415b41`). Robolectric/compose-ui-test artifacts absent from the offline Gradle cache → `GoalEditCreditContentTest` pins the contract via state-logic assertions (full Compose-UI test deferred to PHASE_15 per the tester's coverage exception). Built/tested on JDK 17 (no JDK 21 / Android Studio JBR present on this host).
