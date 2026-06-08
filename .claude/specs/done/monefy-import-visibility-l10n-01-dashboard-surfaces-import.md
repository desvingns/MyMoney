# Dashboard surfaces imported transactions (reactive accounts + post-import period focus)
Epic: monefy-import-visibility-l10n
Order: 01 of 03
Status: done
Depends-on: —
Date: 2026-06-08

## SPEC
=== SPEC ===
TASK: bugfix
WHAT: After a Monefy/MyMoney CSV import, the dashboard immediately shows the imported transactions — newly-created accounts enter the balance computation and the period jumps to the month of the latest imported transaction.
LAYERS: presentation data
CHANGED_HINT:
  - feature/dashboard/src/main/java/com/kshavrin/mymoney/feature/dashboard/DashboardViewModel.kt (observeAccountsAndCurrencies reads accounts ONCE via .first() at init — make account/currency state reactive; recomputeBalance for AllAccounts filters the stale state.accounts list)
  - feature/dashboard/src/main/java/com/kshavrin/mymoney/feature/dashboard/DashboardState.kt
  - core/datastore/src/main/java/com/kshavrin/mymoney/core/datastore/model/AppSettings.kt + AppSettingsKeys.kt + AppSettingsRepositoryImpl.kt (add a one-shot import-focus signal)
  - core/database/src/main/java/com/kshavrin/mymoney/core/database/repository/BackupRepositoryImpl.kt (importMonefyCsv + importMyMoneyCsv: record the latest imported occurredAt + currencyId so the dashboard can focus on it)
  - core/domain/src/main/kotlin/com/kshavrin/mymoney/core/domain/repository/BackupRepository.kt
  - feature/settings/src/main/java/com/kshavrin/mymoney/feature/settings/backup/BackupRestoreViewModel.kt (persist the import-focus signal on import success)
TEST_TYPES: unit
CONSTRAINTS:
  - Reactive accounts/currencies: replace the one-shot `accountRepository.observeActive().first()` / `currencyRepository.observeActive().first()` in observeAccountsAndCurrencies with a continuous collection so an imported account appears in `state.accounts`. PRESERVE the user's current selection when it is still valid (its account still exists); only fall back to the default/first account when the current selection's account vanished or no selection exists yet. Do NOT reset the user's chosen account on every transaction add.
  - This fixes the latent "All accounts" bug: `recomputeBalance()` for `DashboardSelection.AllAccounts` filters `state.accounts` by currency — with a stale list, imported accounts are silently excluded. After this change, AllAccounts must include the imported account.
  - Post-import period focus: add a one-shot "import focus" signal to DataStore AppSettings — fields `importFocusEpochMs: Long` (0 = none) and `importFocusCurrencyId: Long` (0/-1 = none). On a successful CSV import, `importTransactionsCsv` returns the latest imported `occurredAt` epoch-millis + the imported `currencyId` (pick the most-recent row; if multiple currencies, the currency of that most-recent row), and BackupRestoreViewModel writes them into AppSettings.
  - DashboardViewModel observes AppSettings; when `importFocusEpochMs > 0` it: (a) sets `period = Period.Month` of that epoch in the system zone, (b) sets `dashboardSelection = AllAccounts(currency of importFocusCurrencyId)` so every imported account of that currency is summed, (c) recomputes balance, then (d) clears the signal (`importFocusEpochMs = 0`, `importFocusCurrencyId = -1`) so it fires exactly once and does not re-apply on later launches.
  - If the focus currency has no account or is unresolved, fall back gracefully to the existing default-selection logic (no crash, no throw).
  - Keep the existing `observeTransactionChanges()` change-signal behaviour intact; the imported rows must trigger a recompute as before.
  - Domain ops return kotlin.Result; map failures to SyncException as the repo already does. No hard-coded user-facing strings. Money BigDecimal in domain. occurredAt is Instant in domain / epoch-millis in Room (existing TypeConverters) — no Room schema change (the new fields live in DataStore, not Room).
  - Unit tests (Turbine + kotlinx-coroutines-test, fakes at repository boundary, no mocks): (1) an imported account newly appearing in AccountRepository enters `state.accounts` and, in AllAccounts mode, its transactions are included in the snapshot; (2) a non-zero import-focus signal moves `state.period` to the focused month AND selects AllAccounts of the focus currency, then the signal is cleared; (3) a still-valid manual account selection is preserved across an unrelated account-list emission (not reset to default).
=== END SPEC ===

## Gap / context
The "no history after import" bug. Two independent root causes both fixed here so the user
sees their data immediately: the dashboard never refreshed its account snapshot after import
(new accounts excluded even from "All accounts"), and the month-period default hid every
2018–2026 imported row. Ships first; restores visibility on its own regardless of SPECs 02/03.

## Implementation links
- commit: 2aa0d7c3, 68980012
- files:
  - core/domain/src/main/kotlin/com/kshavrin/mymoney/core/domain/repository/BackupRepository.kt
  - core/database/src/main/java/com/kshavrin/mymoney/core/database/repository/BackupRepositoryImpl.kt
  - core/database/src/androidTest/java/com/kshavrin/mymoney/core/database/MonefyCsvImportE2ETest.kt
  - core/datastore/src/main/java/com/kshavrin/mymoney/core/datastore/model/AppSettings.kt
  - core/datastore/src/main/java/com/kshavrin/mymoney/core/datastore/AppSettingsKeys.kt
  - core/datastore/src/main/java/com/kshavrin/mymoney/core/datastore/AppSettingsRepositoryImpl.kt
  - core/datastore/src/test/java/com/kshavrin/mymoney/core/datastore/AppSettingsRepositoryTest.kt
  - feature/dashboard/src/main/java/com/kshavrin/mymoney/feature/dashboard/DashboardViewModel.kt
  - feature/dashboard/src/test/java/com/kshavrin/mymoney/feature/dashboard/DashboardViewModelTest.kt
  - feature/settings/src/main/java/com/kshavrin/mymoney/feature/settings/backup/BackupRestoreViewModel.kt
  - feature/settings/src/test/java/com/kshavrin/mymoney/feature/settings/backup/BackupRestoreViewModelTest.kt
  - feature/settings/src/test/java/com/kshavrin/mymoney/feature/settings/fake/FakeBackupRepository.kt
