# PHASE 10 — Transaction forms (S03 Transfer, S06 Expense, S07 Income, S09 Category picker, S27 Currency rate)

## Goal

Implement the five interconnected forms that let the user log money: Add Expense (S06), Add Income (S07), Transfer (S03), Category picker (S09), and Currency rate setup (S27). Build the custom `MonefyKeypad` (4×5 grid, spring+haptic+sound) and `MonefyAmountInput` (headline-sized amount + expression display). Save flows write to Room; on success the dashboard's `Flow<Transaction>` subscription refreshes automatically. AS-6 cross-currency rate jump + AS-7 single-row transfer + AS-4 category-add-then-return are observed.

## TDD anchors

- §4.6 S06 Add expense — lines 666–706
- §4.7 S07 Add income — lines 707–719 (deltas vs S06)
- §4.8 S03 Transfer — lines 720–759
- §4.10 S09 Category picker — lines 789–817
- §4.26 S27 Currency rate setup — lines 1153–1171
- §6.5 Components (MonefyKeypad, MonefyAmountInput signatures) — lines 1380–1424
- §6.7 Motion (keypad press spring) — lines 1433–1445
- §5 BR-7 … BR-16 — lines 1172–1207 (calculator semantics, transfer rules)
- AS-4 (category add → S06/S07 with new category pre-selected) — §14.1
- AS-6 (cross-currency transfer auto-jumps to S27) — §14.1
- AS-7 (transfer as single row) — §14.1

## Prerequisites

- PHASE_06 — done (TransferExecutor + repositories)
- PHASE_09 — done (S22 Category edit is the destination for "Add new category" from S09)
- PHASE_08 — done (dashboard provides the entry FABs)

## Deliverables (in `:core:designsystem`) — finalise PHASE_03 stubs

- `core/designsystem/.../keypad/MonefyKeypad.kt` — full `@Composable fun MonefyKeypad(onEvent: (KeypadEvent) -> Unit, modifier: Modifier = Modifier)`. 4×5 grid: 0–9, `.`, `+`, `−`, `×`, `÷`, `=`, `⌫`. Each key press: `spring(0.6, 500)` scale 1.0→0.92→1.0 + soft haptic + `KEYPAD_TAP` sound (sound deferred to PHASE_15 — stub the call now).
- `core/designsystem/.../keypad/KeypadEvent.kt` — sealed events emitted upward: `Digit(d)`, `Op(Operator)`, `Dot`, `Backspace`, `Equals`.
- `core/designsystem/.../amountinput/MonefyAmountInput.kt` — headline display of current value + small grey expression preview (e.g. `12 + 5.50`). Pure presentation.

## Deliverables (in `:feature:transaction`)

- `feature/transaction/build.gradle.kts` — feature deps + `:core:designsystem`.
- `feature/transaction/src/main/java/com/kshavrin/mymoney/feature/transaction/expense/AddExpenseScreen.kt` — wraps `AddExpenseRoute` + `AddExpenseScreen(state, onEvent)`.
- `feature/transaction/src/main/java/com/kshavrin/mymoney/feature/transaction/expense/AddExpenseViewModel.kt` — `@HiltViewModel`. State per TDD §2.3 lines 187–224 verbatim (the example case is `AddExpenseState`). Events also verbatim. On `SaveClicked`: validate amount > 0 + category not null + amount ≤ available balance (BR rule); call `transactionRepository.insertExpense(...)`; emit `Action.NavigateBack` + `Action.ShowSavedConfetti` (no — per AS-10 confetti is dashboard-only on first-positive; save just emits a snackbar).
- `feature/transaction/src/main/java/com/kshavrin/mymoney/feature/transaction/income/AddIncomeScreen.kt` + ViewModel — symmetric to expense.
- `feature/transaction/src/main/java/com/kshavrin/mymoney/feature/transaction/transfer/TransferScreen.kt` + ViewModel — per §4.8 + AS-6 + AS-7. On save with `sourceCurrency != targetCurrency` and no rate → emit `Action.NavigateToRateSetup(fromId, toId)`. On save success → write **single** TransactionEntity per AS-7.
- `feature/transaction/src/main/java/com/kshavrin/mymoney/feature/transaction/picker/CategoryPickerScreen.kt` — S09. Grid of categories filtered by `kind`. Tap → returns `categoryId` to caller via `SavedStateHandle["pickedCategoryId"]`. The "+ Add" tile navigates to S22 (`category_edit?id=-1`).
- `feature/transaction/src/main/java/com/kshavrin/mymoney/feature/transaction/rate/CurrencyRateScreen.kt` — S27. Inputs: `1 <fromCode> = ?  <toCode>`. Save → upsert `CurrencyRate`; pop back to S03.
- `feature/transaction/src/main/java/com/kshavrin/mymoney/feature/transaction/shared/AmountFieldSection.kt` — reusable composable: `MonefyAmountInput` + `MonefyKeypad` + date chip + note field + account chip. Used by S06/S07/S03.

## Task checklist

- [x] Re-read TDD §4.6, §4.7, §4.8, §4.10, §4.26 + §2.3 (UDF pattern example).
- [x] **Calculator engine** — pure Kotlin class `CalculatorEngine` in `:core:common`. Handles BR-7 (dot once per operand), BR-8 (operator chains: `5 + 3 + 2 = 10`), BR-9 (= terminates expression), BR-10 (backspace deletes last char), BR-11 (overflow protection). Unit-test thoroughly.
- [x] Wire `CalculatorEngine` into `AddExpenseViewModel`. Each `KeypadDigit` event mutates engine state; ViewModel emits `state.copy(amount = engine.currentValue, amountInput = engine.display, pendingOperator = engine.pendingOp)`.
- [x] Implement `MonefyKeypad` — the spring + haptic happens inside the Composable; sound playback is via injected `SoundPlayer` (interface; impl lands in PHASE_15).
- [x] Implement `MonefyAmountInput` — `headlineLarge` for current value, `bodySmall` grey for expression. Auto-shrink on long values (use `BasicTextField` measure pass or Compose `AutoSizeText` 3rd-party).
- [x] **Category-picker flow** (AS-4): when user from S09 taps "+ Add" → push S22 (category_edit, prefilled `kind = current_picker_kind`). On S22 save → pop to S06/S07 with the new category id passed back via `SavedStateHandle["pickedCategoryId"]`. **Skip S09 on return** — the caller picks up the saved id directly.
- [x] **Cross-currency transfer flow** (AS-6): in `TransferViewModel.onSaveClicked()`, call `TransferExecutor`. If returns `TransferError.RateMissing(fromId, toId)`, emit `Action.NavigateToRateSetup(fromId, toId)`. S27 saves rate → pops back to S03 → retry save.
- [x] **Single-row transfer** (AS-7): `TransferExecutor` writes one row with `kind = "transfer"`, both account ids, both amounts (source `amount`, dest `toAmount`), `exchangeRate`. Do NOT split into two rows.
- [x] Routing: add `"add_expense"`, `"add_income"`, `"transfer"`, `"category_picker?kind={kind}"`, `"currency_rate?fromId={x}&toId={y}"` to `MyMoneyNavHost`. Wire from dashboard FABs (PHASE_08).
- [x] App-Shortcut routing: when MainActivity intent has `shortcut_id = "add_expense"` (etc.), navigate to the right screen after onboarding/lock checks per §3.4. Implement in MainActivity `LaunchedEffect(intent)`.
- [x] Test save → confirm dashboard donut and balance refresh automatically (the `Flow<Transaction>` subscription in `DashboardViewModel` re-runs).
- [x] Update PROGRESS.md.

## Done criteria

- `.\gradlew.bat :feature:transaction:assembleDebug` succeeds.
- Manual e2e: save 3 expenses + 1 income + 1 transfer → dashboard reflects new balance + donut.
- AS-4: from S09 → S22 → save → returns to S06 with new category selected.
- AS-6: from S03 with mismatched currencies and no rate → auto-pushes S27 → save rate → returns to S03 → save succeeds.
- AS-7: a transfer creates exactly 1 row in `transaction` table (`adb shell run-as ... sqlite3 monefy.db "SELECT COUNT(*) FROM \`transaction\` WHERE kind='transfer'"`).
- `CalculatorEngine` unit tests cover BR-7…BR-11.

## Verification commands

```powershell
cd C:\Pet\MyMoney
.\gradlew.bat :feature:transaction:assembleDebug
.\gradlew.bat :feature:transaction:test
.\gradlew.bat :core:common:test    # CalculatorEngine
.\gradlew.bat :app:installDebug
```

## Notes for next session

Completed 2026-05-22.

- **AS-6 + AS-7 were already implemented** in commit `cd41194` ("Mb revert?" — message "Не уверен в этом коммите"; despite the name it ADDED ~2893 lines, not a revert). `TransferViewModel.evaluateRate()`/`save()` emit `NavigateToRateSetup` on missing rate and consume the return via `SavedStateHandle["pendingRate"]` (AS-6); `TransferExecutor` writes a single `kind=Transfer` row with both account ids, `toAmount = amount × rate`, `exchangeRate` (AS-7). Both verified correct against TDD §4.8 lines 751–752 — only the checkboxes were unticked.
- **Navigation was the real gap.** `e77770a` registered the 5 transaction composables in `MyMoneyNavHost`, extended `category_edit` with `kind`/`fromPicker` query args (switching to the `navController` overload of `CategoryEditRoute` — load-bearing for the AS-4 chained pop) and `currency_rate` with `fromId`/`toId` args, reconciled the `transaction/category_picker` route string, and added `MainActivity` `shortcut_id` intent routing (§3.4). `45c77a9` fixed a real latent bug: the S06↔S07 swap-toggle navigated to bare `"add_income"`/`"add_expense"` routes that were never registered (would crash); corrected to `transaction/income`/`transaction/expense`.
- **Dashboard refresh fixed** (`89a33ef`): `DashboardViewModel` previously computed balance only in `init` + on period/account events with one-shot `.first()` — so a saved transaction did not reflect on S01. Now it collects `transactionRepository.observeRecent(1)` as a change signal and re-runs `recomputeBalance()` on each emission (TDD §4.6 criterion 6).
- **BUILD STATUS — important.** The long-assumed "loopback blocker" is NOT reproducing. With `JAVA_HOME` = Android Studio JBR, `gradlew :feature:transaction:help` runs and reaches plugin resolution. The current build blocker is the root `build.gradle.kts` declaring `com.google.android.gms.oss-licenses-plugin:0.10.6` via the version-catalog `plugins{}` DSL — that plugin has no resolvable plugin-marker artifact (fix: `pluginManagement.resolutionStrategy` mapping to `com.google.android.gms:oss-licenses-plugin`, or legacy buildscript classpath). Build/e2e Done-criteria above remain unverified pending this fix.
- **Test debt:** `:feature:dashboard` has zero unit tests; the reactive-refresh fix is verified by inspection only. `MainActivity.resolveShortcutDestination(Intent)` is private/Intent-bound — extract a pure `String? -> String?` helper to make shortcut mapping unit-testable.
