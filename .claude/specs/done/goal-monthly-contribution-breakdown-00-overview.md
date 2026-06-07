# Advanced monthly-contribution settings (income/expense breakdown) — epic overview
Epic: goal-monthly-contribution-breakdown
Order: 00 of 03
Status: done
Depends-on: financial-goals (shipped, in done/)
Date: 2026-06-07

## Goal
On the goal create/edit form (`GoalEditScreen`, savings + credit variants), add a checkbox **«Расширенные
настройки ежемесячного пополнения»** under the "Monthly contribution" field. When enabled, that field
becomes **read-only** and two repeatable lists appear: **«Ежемесячный доход»** and **«Ежемесячные
расходы»** — each row has an optional name + an amount, with a **«+»** to add more rows and a delete
affordance. The monthly contribution is then **computed**: `sum(incomes) − sum(expenses)`. The breakdown
(rows + the enabled flag) is **persisted with the goal** and restored when the goal is reopened.
Scope boundary: manual flat monthly sums only — NOT tied to real transactions/categories, no
frequencies/dates/recurrence, single currency (the goal's account currency).

## Locked decisions
- D1: The breakdown **persists** with the goal (reopening restores rows + enabled state). → Room v2→3.
- D2: Available in **both** goal variants (savings & credit) — `monthlyContribution` feeds both calculators.
- D3: Negative total (expenses ≥ incomes) → show the **real number** (can be ≤ 0); the existing
  `GoalSavingsProjector` already maps `monthlyContribution ≤ 0` → `UNREACHABLE`. Save is **not** blocked.
- D4: On enable, show **one empty income row + one empty expense row**. Row name **optional** (placeholder
  when blank); amount parsed like other money fields (blank/invalid → 0).
- D5: Toggling the checkbox **off** keeps the last computed value (field editable again) and **retains**
  the rows (state + DB) — re-enabling restores them.
- (assumption) H3: storage shape = a **serialized TEXT column on `goal`** (not a child table), since the
  breakdown is always loaded/saved whole with the goal and never queried independently; mirrors the
  goals' "no @ForeignKey" stance (G7). Finalized in SPEC-02.
- (assumption) O1: read-only total display = the computed number in the (disabled) field + the existing
  projector status below it; no new error string.

## SPECs (run via `/mp --feature --next` in Order)
| Order | File | Depends-on | Layers | Summary |
|---|---|---|---|---|
| 01 | `goal-monthly-contribution-breakdown-01-breakdown-domain-calc.md` | — | domain | `ContributionItem`/`ContributionBreakdown` models + `ContributionCalculator` (sum incomes − sum expenses); add `contributionBreakdown` + `advancedEnabled` to `Goal`. |
| 02 | `goal-monthly-contribution-breakdown-02-breakdown-persistence.md` | 01 | data | Room v2→3: serialized column on `goal`, `MIGRATION_2_3`, `3.json`, entity + mapper + converter, instrumented migration test. |
| 03 | `goal-monthly-contribution-breakdown-03-breakdown-form-ui.md` | 01, 02 | presentation | Checkbox + read-only monthly + income/expense row lists + live recompute + toggle-off behaviour; strings EN+RU; extend savings VM/Content tests. |

## Why this ordering
Foundation-first (mirrors the `financial-goals` epic: 01 calculator-domain → 02 persistence → 05 form):
the pure aggregation (01) is unit-testable with no Android; persistence (02) carries the instrumented
migration test and depends on the domain model from 01; the UI (03) sits on top of a finished domain +
DB. **No same-file clashes** — each SPEC owns a distinct layer (`Goal.kt` edited only in 01; `GoalEntity`/
`Mappers`/`Migrations` only in 02; `GoalEditViewModel`/`GoalEditScreen` only in 03).

## Key facts (verified — see pipeline/grounding.md)
- G1: monthly field = `OutlinedTextField` at `feature/dictionaries/.../goals/GoalEditScreen.kt:221-228`; new UI goes between it and "Target amount" (`:230`).
- G2: `GoalEditState`/`GoalEditEvent`/`recompute()` — `GoalEditViewModel.kt:233-272`, `:138-194`; `save()` `:196-222`.
- G3: `GoalSavingsProjector` maps `monthlyContribution ≤ 0` → `UNREACHABLE` — `core/domain/.../usecase/GoalSavingsProjector.kt:21-27`.
- G4: money parse at VM boundary `String.parseMoney()` (`GoalEditViewModel.kt:225-226`); domain money = `BigDecimal`.
- G5: `monthlyContribution` also feeds `LoanGoalInput` (`GoalEditViewModel.kt:158-170`) — credit branch must keep working.
- G7: `Goal` domain ↔ `GoalEntity` (`monthly_contribution: Double`), no @ForeignKey on goals; mapper `core/database/.../mapper/Mappers.kt`.
- G8: `MoneyDatabase version = 2`, `exportSchema = true`, only `MIGRATION_1_2`; new persisted field ⇒ v3 + `MIGRATION_2_3` + `3.json` + matching instrumented migration test.
- G11: savings form covered by `GoalEditSavingsViewModelTest` (19) + `GoalEditSavingsContentTest` (32); fakes under `…/goals/fake/`; runner compiles the affected module's androidTest — extend tests in the same pass.

## Implementation links
- 01 domain calc — done (commit 0dc1e02d + decde445)
- 02 persistence  — done (commit 8f5d27d0 + c3d16d62)
- 03 form UI + VM — done (commit ad795e3b + ecc8c3ef + 768bb891)
- Epic COMPLETE 2026-06-07. All 3 SPECs in done/.
