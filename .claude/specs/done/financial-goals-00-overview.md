# Financial Goals — epic overview
Epic: financial-goals
Order: 00 of 06
Status: done
Depends-on: —
Date: 2026-06-05

## Goal
Add a new **"Финансовые цели" (Financial Goals)** area to MyMoney: a savings + mortgage/credit
**planner**. The user opens it from the dashboard's right (⋮) drawer via a new button placed
**between "Счета" and "Валюты"**, sees a list of their goals, and can create a goal in one of two
variants:

- **Without credit (savings):** name, icon, account, read-only "сколько денег на счету сейчас",
  starting capital (+ a "останется/не хватает" diff vs. the account balance), monthly contribution,
  target amount → the app **computes the achievement date**.
- **With credit/mortgage:** the same fields plus an interest rate and a **read-only monthly payment**;
  it is a loan calculator. When the monthly set-aside exceeds the loan's monthly payment, the surplus
  is an overpayment that **lowers the future monthly payment** (annuity, term fixed at the entered date).

The feature is **projection-only**: a goal never moves real money and is not recomputed from
transactions. The selected account contributes only a live, read-only current-balance figure.

This epic lives entirely on the SPEC board (ad-hoc `--feature` epics, see `.claude/specs/README.md`);
it is outside the 15-phase TDD roadmap. It introduces **S28 (goals list)** and **S29 (goal create/edit)**
and the business rules below — this overview is the authoritative source for them until merged into the TDD.

## Locked decisions
- **FG-1** Delivered as this backlog epic (not a `~/AppSpecs` mp-spec bundle).
- **FG-2** A **target-amount** field ("сумма цели") exists — it was implicit in the request but is
  structurally required (defines "done" for savings and the loan principal for credit).
- **FG-3** **Savings:** date is **computed**: `months = ceil((target − startingCapital) / monthlyContribution)`,
  `achievementDate = today.plusMonths(months)`.
- **FG-4** **Credit:** **annuity** schedule; overpayment (`monthlyContribution > monthlyPayment`) uses
  **"reduce payment, keep term"** — the surplus reduces principal and the contractual payment is
  recomputed downward over the remaining term; the payoff date stays at the entered term.
- **FG-5** **Projection-only.** No transaction tracking, no auto-recompute. The selected account yields
  only a read-only current balance via `AccountRepository.computeBalance(accountId)`.
- **FG-6** **(Plan deviation, justified)** The goals UI is hosted **inside `:feature:dictionaries`**
  (a new `…/dictionaries/goals/` package), **not** a new `:feature:goals` module. Reason: the goal
  create/edit form must reuse `IconPickerSheet` + the icon catalog, both of which live in
  `:feature:dictionaries`; `:feature:* → :feature:*` is forbidden, and `IconPickerSheet.kt` is being
  edited right now by the active `icon-library-expansion-02`. Accounts/Currencies/Categories already
  live in `:feature:dictionaries`, so goals is a natural sibling. The calculator + persistence stay in
  `:core:domain`/`:core:database`; only the screens/ViewModels live in `:feature:dictionaries`.
- **AS-FG-credit-date** (assumption) For the **credit** variant the date is an **INPUT** (the loan term
  end) — an annuity needs 3 of {principal, rate, term, payment} and payment is the read-only output. So
  the single date field is *computed* for savings (FG-3) and *entered* for credit. Overpayment keeps
  this date (FG-4); it does not produce an earlier second date.

## New screens & business rules (interim authority — not yet in the TDD)
- **S28** Financial Goals list — empty state + goal rows (icon, name, target, projected date), FAB → create.
- **S29** Goal create/edit — variant toggle (savings ⇄ credit) + the field set above.
- **BR-FG-1** Savings achievement date = `today.plusMonths(ceil((target − startingCapital)/monthly))`.
- **BR-FG-2** `target − startingCapital ≤ 0` → goal already achieved (no future date).
- **BR-FG-3** `monthlyContribution ≤ 0` (savings) → unreachable (no date; surfaced in UI).
- **BR-FG-4** Capital-vs-balance text: `diff = currentBalance − startingCapital`; `>0` → "останется {diff}",
  `<0` → "не хватает {|diff|}", `0` → exact.
- **BR-FG-5** Credit annuity `A0 = P·i·(1+i)^n / ((1+i)^n − 1)` (or `P/n` if `i==0`),
  `P = target − startingCapital`, `i = annualRate/100/12`, `n = months(today→targetDate)`.
- **BR-FG-6** Credit, `monthly < A0` → underfunded (no overpayment; payment = A0, flagged).
- **BR-FG-7** Credit, `monthly ≥ A0` → reduce-payment overpayment (FG-4): report total interest,
  total paid, and the declining payment vs. the no-overpayment baseline.

## SPECs (run via `/mp --feature --next` in Order)
| Order | File | Depends-on | Layers | Summary |
|---|---|---|---|---|
| 01 | `financial-goals-01-calculator-domain.md` | — | domain | Pure `:core:domain` use-cases + I/O models: savings projection (BR-FG-1..3) + annuity loan with reduce-payment overpayment (BR-FG-5..7). Heavy unit tests with worked examples. **DONE** (`f372a33`/`42175a2`). |
| 01b | `financial-goals-01b-loan-overpayment-semantics.md` | 01 | domain | **Corrective:** realigned `GoalLoanCalculator` to FG-4/BR-FG-7 "reduce payment, keep term" (`monthsToPayoff == n`, declining payment), added `finalMonthlyPayment`/`interestSavedVsBaseline`, preserved the last non-zero declining payment when surplus zeroes the balance early, and removed the dead annuity recompute. **DONE** (`fc4923f`/`84e1763`/`153d3ca`). |
| 02 | `financial-goals-02-goal-persistence.md` | — | data, domain | `Goal` model, `GoalRepository`, `GoalEntity`/`GoalDao`/`GoalRepositoryImpl`, **DB v1→v2 + `Migration(1,2)`** + exported schema, Hilt wiring. Unit + Room instrumented. **DONE** (`38471b9`/`bf33cbd`; instrumented tests compiled, on-device run pending `/mp --device`). |
| 03 | `financial-goals-03-goal-icons.md` | — | presentation | `GoalIcons.kt` (`goalIcon(iconKey): ImageVector`, fallback `Flag`) + `GOAL_ICON_KEYS` + `GoalIconsTest`. 12 themed + `ic_goal_other`. (Visible in the picker only once `icon-library-expansion-02` lands.) **DONE** (`6d2ec1a`; GoalIconsTest 21/21). |
| 04 | `financial-goals-04-menu-nav-and-list.md` | 02, 03 | presentation | Menu item between Accounts/Currencies; `FinancialGoalsClicked`/`NavigateFinancialGoals`; Destinations + NavHost; goals package in `:feature:dictionaries` with **S28 list** (empty + rows + FAB); EN/RU strings; update `DashboardContentUiTest`. **DONE** (`362047e`/`c6b39e3`/`a8c20a1`). |
| 05 | `financial-goals-05-create-edit-savings.md` | 01, 02, 03, 04, `icon-library-expansion-02` | presentation | **S29** savings branch: name, icon picker (`iconFor = goalIcon`), account dropdown, read-only balance + capital diff, starting capital, monthly contribution, target, computed achievement date; save. **DONE** (`e999c53`/`0de37ed`; 51 tests). |
| 06 | `financial-goals-06-create-edit-credit.md` | 05 | presentation | **S29** credit branch of the toggle: interest-rate field, read-only monthly payment, overpayment recalculation display, date = entered loan term (date picker). **DONE** (`fad024e`/`01122a4`/`91a0758`/`8415b41`; module suite green). |

## Why this ordering
01 (pure calculator) and 02 (persistence) are independent foundations. 03 (icon registry) is standalone
like `icon-library-expansion-01`. 04 wires the entry point + list (needs the repo to observe goals and
`goalIcon` for row icons). 05 builds the form's savings variant on all of the above (and on the active
`icon-library-expansion-02` so the picker renders real glyphs). 06 adds the credit variant on top of 05.

## Key facts (verified)
- Right drawer menu: `feature/dashboard/.../components/RightDrawerContent.kt` — items at L43-72
  (Categories, Accounts L49-54, Currencies L55-60, Settings, About), tags at L111-115. Uses
  `feature.dashboard.R`. Insert between Accounts and Currencies.
- Dashboard UDF: `DashboardEvent` in `…/DashboardState.kt:42-63`; `DashboardAction.kt`
  (`NavigateAccounts` L10); VM mapping `DashboardViewModel.kt:284-291` (`closeDrawers()` + `emit(...)`).
- Nav: `app/.../navigation/Destinations.kt` (`ACCOUNTS_LIST`/`ACCOUNT_EDIT` L24-25,
  `CURRENCIES_LIST` L26); `MyMoneyNavHost.kt` — dashboard `onAction` when-block L59-92
  (`NavigateAccounts` L72-73), Accounts composables L187-201, Currencies L202-208.
- Balance is **derived**: `AccountRepository.computeBalance(accountId): BigDecimal` (suspend);
  `observeActive(): Flow<List<Account>>`, `findById`, `upsert(account): Long`
  (`core/domain/.../repository/AccountRepository.kt`). `Account` fields in `…/model/Account.kt`.
- Room: `core/database/.../MoneyDatabase.kt` — **version = 1**, 9 entities, `exportSchema = true`;
  `DatabaseModule.kt` builder `.fallbackToDestructiveMigrationFrom(99)` (no real migrations yet);
  `RepositoryBindingsModule.kt` binds impls. Money = `Double`, time = `Long` (`MoneyTypeConverters`).
- Icon registry to mirror: `core/designsystem/.../icon/AccountIcons.kt` (`accountIcon(iconKey): ImageVector`,
  when-expression, fallback `AccountBalanceWallet`); catalog `feature/dictionaries/.../common/IconCatalog.kt`;
  picker `…/common/IconPickerSheet.kt`; contract test `…/icon/AccountIconsTest.kt`.
- `:feature:dictionaries` deps (build.gradle.kts): `:core:{ui,designsystem,domain,datastore,common}`
  + compose/hilt — so `accountIcon`/`goalIcon`/`AccountRepository`/calculator use-cases are all reachable.
- No financial math exists: `CalculatorEngine` (`core/common/.../calculator/`) is arithmetic input only.

## Cross-cutting notes
- **Layer boundary:** keep `:feature:dictionaries → :feature:*` at zero (FG-6). Calculator + Goal
  domain/data go in `:core:*`. The Reviewer checks this.
- **androidTest compiles in the runner:** SPEC 04 changes the right drawer, so it MUST update
  `app/src/androidTest/.../dashboard/DashboardContentUiTest.kt` in the same pass; preserve existing
  `RIGHT_DRAWER_*` tags and add `RIGHT_DRAWER_FINANCIAL_GOALS_TAG`.
- **Strings:** EN default + RU (`feature/dashboard` res for the menu label; `feature/dictionaries` res
  for the goals screens). No hardcoded user-facing strings.
- **Money/time:** `BigDecimal`/`LocalDate` in domain; `Double`/`Long` in Room. Inject `today: LocalDate`
  into the savings projector for testability (mirror `TransferExecutor`'s `now: Instant`).

## Implementation links
- commit: <hash>
- files:  <changed files>
