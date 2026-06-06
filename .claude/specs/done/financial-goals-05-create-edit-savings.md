# Goal create/edit screen — savings variant (S29)
Epic: financial-goals
Order: 05 of 06
Status: done
Depends-on: 01, 02, 03, 04, icon-library-expansion-02
Date: 2026-06-05

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: Build the **S29 goal create/edit** screen, savings (no-credit) variant. Replace the SPEC-04
placeholder `GoalEditScreen.kt` body with the full form: name, icon (via the shared `IconPickerSheet`,
`GOAL_ICON_KEYS`), a variant toggle (Без кредита / С кредитом), account dropdown, a read-only "сколько
денег на счету сейчас", starting capital with a live "останется/не хватает" diff vs. the account balance,
monthly contribution, target amount, and a read-only **computed achievement date** (via
`GoalSavingsProjector`). Save persists via `GoalRepository.upsert`. The variant toggle is present and the
SAVINGS branch is fully wired; the CREDIT-only fields are added in SPEC 06.
LAYERS: presentation
CHANGED_HINT:
  - feature/dictionaries/.../goals/GoalEditScreen.kt — implement `GoalEditContent(state, onEvent)`:
      • name `OutlinedTextField`;
      • icon selector: a tappable circle showing `Icon(goalIcon(state.iconKey))`; tap opens
        `IconPickerSheet(iconKeys = GOAL_ICON_KEYS, selectedIconKey = state.iconKey, onIconSelected = { onEvent(IconSelected(it)) }, onDismiss = …, iconFor = { com.kshavrin.mymoney.core.designsystem.icon.goalIcon(it) })`
        (the `iconFor` param exists after `icon-library-expansion-02`);
      • variant toggle: Material3 SingleChoiceSegmentedButtonRow with SAVINGS/CREDIT → `VariantChanged`;
      • account dropdown: `ExposedDropdownMenuBox` over `state.accounts` (names) → `AccountSelected(id)`;
      • read-only current balance `Text(state.currentBalanceFormatted)` (with the account's currency);
      • starting capital `OutlinedTextField` (decimal keyboard) → `StartingCapitalChanged`; below it
        `Text(state.capitalDeltaText)` (BR-FG-4: "На счету останется X" / "Не хватает X" / exact);
      • monthly contribution + target amount `OutlinedTextField`s → `MonthlyChanged` / `TargetChanged`;
      • read-only achievement date `Text` from `state.savingsProjection` (status text for ALREADY_ACHIEVED /
        UNREACHABLE per BR-FG-2/3);
      • Save button → `SaveClicked`; back arrow → `BackClicked`.
    Show the CREDIT-only fields region only when `variant == CREDIT` — leave it as a stub here (SPEC 06).
  - NEW feature/dictionaries/.../goals/GoalEditViewModel.kt — `@HiltViewModel`, inject `GoalRepository`,
    `AccountRepository`, `CurrencyRepository` (for the currency symbol), `GoalSavingsProjector`, and
    `SavedStateHandle` (the `{id}` arg). State `GoalEditState(id, name, iconKey = "ic_goal_other",
    variant = SAVINGS, accountId, accounts: List<Account>, currentBalance: BigDecimal?, currencySymbol,
    startingCapital: String, monthlyContribution: String, targetAmount: String, savingsProjection:
    SavingsProjection?, capitalDeltaText, …)`. On init: load `accountRepository.observeActive()`; if id != -1
    load the goal (`findById`) into the form. On `AccountSelected`: `currentBalance = computeBalance(id)`,
    resolve currency. Recompute `savingsProjection` (via `GoalSavingsProjector(input, LocalDate.now())`) and
    `capitalDeltaText` (via `capitalVsBalanceDelta`) whenever target/startingCapital/monthly/account change.
    Parse the money text fields to `BigDecimal` (blank/invalid → ZERO). `SaveClicked` → build a `Goal`
    (`variant = SAVINGS`, `annualRatePercent = null`, `termDate = null`, timestamps) → `goalRepository.upsert`
    → emit `NavigateBack`. Events/actions mirror the Account edit screen.
  - feature/dictionaries/src/main/res/values/strings.xml + values-ru — add: `goal_name`, `goal_icon`,
    `goal_variant_savings` ("Без кредита"), `goal_variant_credit` ("С кредитом"), `goal_account`,
    `goal_current_balance`, `goal_starting_capital`, `goal_remaining_on_account` ("На счету останется %1$s"),
    `goal_short_on_account` ("Не хватает %1$s"), `goal_monthly_contribution`, `goal_target_amount`,
    `goal_achievement_date`, `goal_already_achieved`, `goal_unreachable`, `goal_save`.
  - feature/dictionaries/src/test/.../goals/fake/FakeGoalRepository.kt — extend (capture the upserted Goal
    so the test can assert variant/fields). Reuse the dictionaries module's existing account/currency test
    fakes (used by the accounts/currencies edit tests); if none is reachable for this package, add a local
    `FakeAccountRepository`/`FakeCurrencyRepository` under `…/goals/fake/` (module-local fakes convention).
  - NEW feature/dictionaries/src/test/.../goals/GoalEditSavingsViewModelTest.kt (unit, Turbine) — selecting
    an account loads its balance + currency; capital-delta text flips on sign (remaining / short / exact);
    achievement date recomputes (ON_TRACK / ALREADY_ACHIEVED / UNREACHABLE); SaveClicked upserts a SAVINGS
    Goal with the parsed BigDecimal fields and null rate/termDate, then emits NavigateBack; editing an
    existing goal (id != -1) pre-fills the form.
  - NEW feature/dictionaries/src/test/.../goals/GoalEditSavingsContentTest.kt (compose-ui / Robolectric) —
    fields render; the icon circle opens the picker; the diff text and achievement date appear; Save fires.
TEST_TYPES: compose-ui unit
CONSTRAINTS:
  - Money is `BigDecimal` in the domain; parse the text fields at the ViewModel boundary; display formatting
    uses the SELECTED account's currency (resolve via `account.currencyId` → currency symbol). Computed date
    is read-only (never an input in the savings variant). The date math itself is covered by SPEC-01 tests;
    the ViewModel may call `LocalDate.now()` directly.
  - Depends on `icon-library-expansion-02` for `IconPickerSheet`'s `iconFor` param. If that SPEC has not
    landed, the picker still works (renders abbreviations) — but author against the `iconFor` signature.
  - SAVINGS branch only: build the variant toggle, but the CREDIT-only fields (rate / monthly payment /
    term date) are SPEC 06. Selecting CREDIT here shows the shared fields + a placeholder region; the
    Save path for CREDIT is enabled in SPEC 06.
  - Reuse the existing Account edit-screen patterns (`ExposedDropdownMenuBox`, `IconPickerSheet` call site)
    — do NOT introduce a new picker. Keep `:feature:dictionaries → :feature:*` at zero.
  - English ids; no hardcoded user-facing strings (EN default + RU); no comments unless WHY.
=== END SPEC ===

## Gap / context
SPEC 04 leaves `GoalEditRoute` a placeholder. This SPEC builds the actual create/edit form for the
savings variant — the core of the feature: pick an account, see its live balance, enter capital/monthly/
target, and read off the computed achievement date, then save. It consumes the SPEC-01 projector,
SPEC-02 repository, SPEC-03 icons, and the SPEC-04 route.

## Implementation links
- commit: e999c53 (feat), 0de37ed (test) — pushed to origin/main 2026-06-06
- files:
  - feature/dictionaries/src/main/java/com/kshavrin/mymoney/feature/dictionaries/goals/GoalEditScreen.kt
  - feature/dictionaries/src/main/java/com/kshavrin/mymoney/feature/dictionaries/goals/GoalEditViewModel.kt
  - feature/dictionaries/src/main/res/values/strings.xml
  - feature/dictionaries/src/main/res/values-ru/strings.xml
  - feature/dictionaries/src/test/kotlin/com/kshavrin/mymoney/feature/dictionaries/goals/GoalEditSavingsViewModelTest.kt (19 tests)
  - feature/dictionaries/src/test/kotlin/com/kshavrin/mymoney/feature/dictionaries/goals/GoalEditSavingsContentTest.kt (32 tests)
  - feature/dictionaries/src/test/kotlin/com/kshavrin/mymoney/feature/dictionaries/goals/fake/{FakeGoalRepository,FakeAccountRepository,FakeCurrencyRepository}.kt
- verification: Reviewer pass · 51 unit/Robolectric tests green (--no-build-cache) · Verifier pass (nav/Hilt/strings ok)
