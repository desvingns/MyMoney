# Monefy behavioural fidelity — epic overview
Epic: monefy-behavioral-fidelity
Order: 00 (index)
Status: done
Date: 2026-06-01

## Goal
Bring the app's BEHAVIOUR to the Monefy v1.0 reference (screenshots at
`D:\Pet\TDD_creater\MyMoney\input\screenshots`). This epic is the behavioural follow-up to
`done/redesign-monefy-fidelity` — that epic did the pure VISUAL restyle and explicitly DEFERRED the
behavioural divergences (see `done/redesign-monefy-fidelity-00-overview.md` lines 39–47: divergence
#1 left-drawer content, #2 period strip). The 9 SPECs below close the user's 7 review notes.

Executor: **GPT-5.5 (Codex)**. Review is based on **divergence from the reference**. Every SPEC
therefore names exact files, enumerates impacted call-sites, cites the exact reference screenshot,
and forbids the listed regressions.

## Ordered SPECs
| # | File | Slice (user note) | Layers | Depends-on |
|---|------|-------------------|--------|-----------|
| 01 | `-01-tx-keypad-first.md` | Tx entry: keypad-first → grid (#5) | presentation | — |
| 02 | `-02-donut-expense-only.md` | Donut expenses-only + center totals (#7a) | domain, presentation | — |
| 03 | `-03-donut-empty-state.md` | Empty-state donut ring + icons (#3) | domain, presentation | 02 |
| 04 | `-04-balance-bar.md` | Balance bar above ± (#7b) | presentation | — |
| 05 | `-05-drawers-partial-width.md` | Both drawers ~62% width (#1, #6) | presentation | — |
| 06 | `-06-left-drawer-period.md` | Left drawer: account dropdown + period (#1, #2) | presentation | — |
| 07 | `-07-period-swipe-nav.md` | Swipe prev/next period (#4) | domain, presentation | 06 |
| 08a | `-08a-records-data.md` | Category-grouped query + use case (#7c) | domain, data | — |
| 08b | `-08b-records-screen.md` | Rework list → category-grouped + expandable (#7c) | presentation | 08a, 04 |

Recommended implementation order: **01 → 02 → 03 → 04 → 05 → 06 → 07 → 08a → 08b** (one per `/cmp --feature --next`).

## Locked decisions (2026-06-01)
1. **Left drawer** = a tappable currency header that expands an account dropdown ("Все счета" +
   accounts, screenshot 14.jpg) + period buttons below (02.jpg). The persistent accounts list +
   "Manage accounts" row is dropped (account management stays in the right-drawer "Счета").
2. **Records (#7c)** = REWORK the existing `feature:transactionslist` into category-grouped +
   expandable (so the donut slice-tap path also becomes category-grouped) — NOT a new screen.
3. **Donut center** shows the currency symbol ("… ₽", per 05.jpg/11.jpg).
4. **Empty-state icons** = full category colour on a gray ring (11.jpg), not muted.
5. **Donut expense-only in PRESENTATION** + one additive `CategoryBalance.isExpense` field —
   `BalanceCalculator.byCategory` keeps BOTH kinds (BudgetEvaluator + locked tests depend on it).
6. **Tx flow (#5)** modifies SPEC-06 (`done/redesign-monefy-fidelity-06-embed-grid.md`): keypad
   INLINE first + "ВЫБОР КАТЕГОРИИ" button → embedded grid (06.jpg→10.jpg), not keypad-in-modal-sheet.

## User review notes → SPEC map
- #1 left menu covers full window → **05** (width) + **06** (content)
- #2 period selector should live in the left menu → **06**
- #3 empty dashboard should still show donut + category icons → **03**
- #4 swipes should change the period (not open the menu) → **07**
- #5 enter amount first, then pick category → **01**
- #6 right menu covers full window → **05**
- #7 income leaks into donut / balance plate placement / records drill-down → **02** (donut expenses-only + center) + **04** (balance bar) + **08a/08b** (category-grouped records)

## Reference
Screenshots 02, 04, 05, 06, 10, 11, 12, 13, 14 (Monefy v1.0) at
`D:\Pet\TDD_creater\MyMoney\input\screenshots`. Prior epic: `.claude/specs/done/redesign-monefy-fidelity-*`.
