# PHASE 09 — Dictionaries CRUD (S21–S26)

## Goal

Build full CRUD UI for the three reference data types: Categories (S21 list + S22 edit/create), Accounts (S23 list + S24 edit/create), Currencies (S25 list + S26 edit/create). All flows enforce the validation rules from §7.8 + the AS-13 "blocked delete" dialog when a parent has child transactions. After this phase, the right-drawer entries from PHASE_08 lead to working CRUD screens.

## TDD anchors

- §4.20 S21 Categories list — lines 1058–1078
- §4.21 S22 Category edit — lines 1079–1100
- §4.22 S23 Accounts list — lines 1101–1121
- §4.23 S24 Account edit — lines 1122–1131
- §4.24 S25 Currencies list — lines 1132–1142
- §4.25 S26 Currency edit — lines 1143–1152
- §7.8 Validation rules — lines 1971–1983
- AS-13 (block delete with children) — §14.1 lines 2727–2750 (the AS-13 row), also referenced in §4.22

## Prerequisites

- PHASE_06 — done (repositories + validation)
- PHASE_07 — done (nav routes available)

## Deliverables (in `:feature:dictionaries`)

- `feature/dictionaries/build.gradle.kts` — standard feature deps.
- `feature/dictionaries/src/main/java/com/kshavrin/mymoney/feature/dictionaries/categories/CategoriesListScreen.kt` — S21. Grid (3 col by default) of category cards. Sections: Expense / Income. Tap card → edit (S22). FAB `+` → create new (S22 with `id = null`). Long-press row → reorder mode (drag handles).
- `feature/dictionaries/src/main/java/com/kshavrin/mymoney/feature/dictionaries/categories/CategoryEditScreen.kt` — S22. Form: name, kind toggle (expense / income), icon picker (modal sheet with the 15 default icons + custom upload deferred), colour picker (M3 color picker dialog), sort-order. Save → upsert. Delete → AS-13 block check.
- `feature/dictionaries/src/main/java/com/kshavrin/mymoney/feature/dictionaries/categories/CategoryEditViewModel.kt` — @HiltViewModel.
- `feature/dictionaries/src/main/java/com/kshavrin/mymoney/feature/dictionaries/accounts/AccountsListScreen.kt` — S23. List with balance per account (calls `BalanceCalculator(account, Period.All)`). Default-account chip. FAB `+`.
- `feature/dictionaries/src/main/java/com/kshavrin/mymoney/feature/dictionaries/accounts/AccountEditScreen.kt` — S24. Form: name, currency (picker from active currencies), initial balance, type (cash/card/bank/savings), colour, icon, set-default toggle.
- `feature/dictionaries/src/main/java/com/kshavrin/mymoney/feature/dictionaries/accounts/AccountEditViewModel.kt`.
- `feature/dictionaries/src/main/java/com/kshavrin/mymoney/feature/dictionaries/currencies/CurrenciesListScreen.kt` — S25. List of all 20 seeded currencies + any user-added. Toggle `isActive` from the row. Tap → edit (S26).
- `feature/dictionaries/src/main/java/com/kshavrin/mymoney/feature/dictionaries/currencies/CurrencyEditScreen.kt` — S26. Form: code (3-letter, validated `^[A-Z]{3}$`), symbol, name, decimal digits (0–8), is-active toggle.
- `feature/dictionaries/src/main/java/com/kshavrin/mymoney/feature/dictionaries/currencies/CurrencyEditViewModel.kt`.
- `feature/dictionaries/src/main/java/com/kshavrin/mymoney/feature/dictionaries/common/BlockedDeleteDialog.kt` — shared `AlertDialog` rendered per AS-13. Text: `"Cannot delete <X>: it has N existing transactions. Move or delete them first."` (EN; RU in PHASE_15).
- `feature/dictionaries/src/main/java/com/kshavrin/mymoney/feature/dictionaries/common/IconPickerSheet.kt` — modal bottom sheet listing available `iconKey`s (15 expense + 2 income icons from the seeder).
- `feature/dictionaries/src/main/java/com/kshavrin/mymoney/feature/dictionaries/common/ColorPicker.kt` — wraps M3 color picker or custom palette grid (15 pastel hexes from §6.1 category palette).
- Nav additions in `:app/navigation/Destinations.kt`: `CATEGORIES_LIST`, `CATEGORY_EDIT/{id}`, `ACCOUNTS_LIST`, `ACCOUNT_EDIT/{id}`, `CURRENCIES_LIST`, `CURRENCY_EDIT/{id}`. Each route accepts `id = -1` for create.

## Task checklist

- [x] Read TDD §4.20–§4.25. Note that S25 currencies list does NOT have a "+ create" FAB — only existing currencies can be edited or activated (§4.24 lines 1132–1142). However if Q-D6 chose to allow custom currency, double-check before disabling the FAB. (TDD currently shows currency CRUD; keep create.)
- [x] AS-13 dialog: before deleting an account, `transactionRepository.countByAccount(id)` — if `> 0`, show dialog. Block, don't cascade. Same for category (`countByCategory`) and currency (`countByCurrency`).
- [ ] Categories — drag-reorder. Use `androidx.compose.foundation.lazy.grid.LazyVerticalGrid` + a long-press detection that switches to a reorderable state. Use the `reorderable` library or hand-roll with `Modifier.detectDragGesturesAfterLongPress`. Persist new `sortOrder` on drop.
- [x] Accounts list — show running balance via `BalanceCalculator(account, Period.All)`. Format with `MoneyFormatter` from `:core:common`.
- [ ] Currency edit — validate `code` regex. Disallow editing `code` for a currency that has dependents (UX hint, not data integrity — FK is by id, not by code).
- [x] Icon picker — use the 17 seeded icon keys (`ic_cat_clothing`, `ic_cat_bills`, … `ic_cat_salary`, `ic_cat_other`). Custom icon upload is deferred to v1.1 (deferred work, OQ-x not assigned — log in PROGRESS).
- [ ] Add the 6 destinations to `MyMoneyNavHost`. Wire the right-drawer entries from PHASE_08 to navigate here.
- [ ] Test each CRUD flow end-to-end on the emulator:
  - Create new expense category → appears on dashboard donut after first matching transaction.
  - Edit account → dashboard balance updates live.
  - Toggle currency active → S26/S24 picker only shows active currencies.
  - Delete category with no transactions → succeeds.
  - Try to delete category with transactions → AS-13 dialog blocks.
- [ ] Update PROGRESS.md.

## Done criteria

- `.\gradlew.bat :feature:dictionaries:assembleDebug` succeeds.
- All 6 screens render and persist edits to Room.
- AS-13 dialog appears and blocks delete when applicable.
- Validation rules from §7.8 are enforced; invalid input shows inline error (e.g., 4-letter currency code → "Code must be 3 uppercase letters").
- Unit tests for repository validation (per §7.8) pass.

## Verification commands

```powershell
cd D:\Pet\TDD_creater\MyMoney_app
.\gradlew.bat :feature:dictionaries:assembleDebug
.\gradlew.bat :feature:dictionaries:test
.\gradlew.bat :core:domain:test
```

## Notes for next session

(empty — fill at end of session. Track decision about custom-currency creation if user pushes back.)
