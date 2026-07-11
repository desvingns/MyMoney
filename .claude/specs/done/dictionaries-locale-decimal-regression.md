# Fix pre-existing locale decimal-separator red in :feature:dictionaries
Epic: —
Status: done
Depends-on: —
Date: 2026-07-10

## SPEC
=== SPEC ===
TASK: bugfix
WHAT: `:feature:dictionaries:testDebugUnitTest` has 5 failing tests on main (present at least since 2c1dc359). They assert money/rate values render with a locale decimal separator but the edit-mode ViewModel state carries the wrong form — e.g. `expected:<250.50> but was:<250,5>` and `expected:<8.5> but was:<8,5>`. Reconcile the account/goal edit ViewModels with the locale-aware display formatting introduced in `review-2026-07-06-locale-formatting-audit` (commits 0f46565f/16520338) so edit-mode pre-fill and display agree, and make the 5 tests green without weakening assertions.
LAYERS: [presentation]
CHANGED_HINT: feature/dictionaries/.../accounts/AccountEditViewModel*, feature/dictionaries/.../goals/GoalEditViewModel*, GoalEditCreditViewModel*, GoalEditSavingsViewModel*; the shared locale display-formatting helper from review-2026-07-06
TEST_TYPES: unit
CONSTRAINTS: regression is pre-existing (confirmed at parent 2c1dc359 — NOT introduced by review-2026-07-07); do not weaken or @Ignore any of the 5 tests; Room/JSON/CSV formats stay invariant; fix the ViewModel/formatter, not the test expectations unless a test itself encodes the wrong contract (justify if so)
=== END SPEC ===

## Failing tests
- AccountEditViewModelTest > edit mode displays an existing balance with the Russian decimal separator
- AccountEditViewModelTest > edit mode loads account fields into state
- GoalEditViewModelTest > edit mode displays existing goal amounts with Russian decimal separators
- GoalEditCreditViewModelTest > editing an existing CREDIT goal pre-fills rate downPayment and termYears into state
- GoalEditSavingsViewModelTest > advanced contribution rows round derived monthly contribution and persisted breakdown amounts to currency scale

## Gap / context
Surfaced by the full-suite run during `review-2026-07-07-cold-start-budget`. The MP runner
scopes to `:app`/changed modules, so this red is invisible to a normal run and rode onto main.
Likely fallout of the locale-formatting audit not covering the dictionaries edit ViewModels.

## Implementation links
- commit: c02b15cd (prod fix: preserve money-value prefill scale), 7c8a7b1d (revert out-of-scope version bump), 2123a6b3 (import order), + test-locale-pin commit below
- files (prod): core/common/.../money/MoneyFormatter.kt (formatInput fractionDigits overload); feature/dictionaries/.../accounts/AccountEditViewModel.kt; feature/dictionaries/.../goals/GoalEditViewModel.kt
- files (test): AccountEditViewModelTest.kt, GoalEditCreditViewModelTest.kt, GoalEditSavingsViewModelTest.kt — 3 locale-fragile tests pinned to Locale.US (no assertion weakening)
- verification: :feature:dictionaries:testDebugUnitTest 283/0; full testDebugUnitTest 1668/0; ktlint main+test green
- root cause: review-2026-07-06 locale audit prefill dropped trailing zeros under ru-RU default; money-value prefills now keep BigDecimal scale, rate/downPayment stay raw
