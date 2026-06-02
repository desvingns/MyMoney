# Left drawer selectable all-accounts mode (S02/S14)
Epic: monefy-fidelity-audit
Order: 02 of 04
Status: done
Depends-on: -
Date: 2026-06-02

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: Make the left-drawer `All accounts` row shown in `14.jpg` selectable and backed by a real
dashboard aggregate mode for the selected/current currency. Affected surfaces: S02 left drawer,
S01 dashboard balance/donut, and any dashboard drill-down route opened while all-accounts is active.
LAYERS: domain data presentation
CHANGED_HINT: feature/dashboard/.../components/LeftDrawerContent.kt (`All accounts` row at lines
178-186 becomes enabled, selectable, selected-state aware, and collapses the dropdown);
feature/dashboard/.../DashboardState.kt (replace account-only selection with an explicit
SpecificAccount/AllAccounts dashboard selection); feature/dashboard/.../DashboardEvent.kt or the
sealed event in `DashboardState.kt` (declare an AllAccounts selection event instead of overloading
`AccountChanged(Long)`); feature/dashboard/.../DashboardViewModel.kt (aggregate balance/donut for
all active accounts in the current currency, persist/restore selection without a magic account id);
core/domain and core/data transaction/balance APIs if an unfiltered-by-account query is required;
app navigation/transactionslist filter handling if all-account dashboard taps open records; update
unit tests for aggregate selection and Compose UI tests for selecting the row; screenshot evidence
must use `02.jpg` and `14.jpg`.
TEST_TYPES: unit compose-ui screenshot-manual
CONSTRAINTS:
  - Aggregate only active accounts in the selected/current currency; do not silently sum across
    currencies or invent conversion rules.
  - Per-account selection, default-account restore, period selection, drawer width, and AS-12 range
    picker behavior must remain intact.
  - `All accounts` drill-down must not route as a fake concrete account; records should receive an
    explicit all-accounts filter or omit the account filter.
  - Do not use sentinel account IDs in UI state if a typed selection model is practical.
  - Preserve localized strings (`All accounts` / `Все счета`) and accessibility labels.
=== END SPEC ===

## Evidence
- Reference screenshot IDs: `02.jpg`, `14.jpg`.
- Affected surfaces: S02 left drawer account dropdown, S01 dashboard aggregate state, records route
  entry from dashboard aggregate taps.
- Current evidence source: `feature\dashboard\src\main\java\com\kshavrin\mymoney\feature\dashboard\components\LeftDrawerContent.kt:178`
  renders `left_drawer_all_accounts`; line 186 sets `enabled = false`. `DashboardEvent.AccountChanged`
  currently accepts only `Long` account IDs (`DashboardState.kt:32`), and `DashboardViewModel.kt:219`
  handles only concrete-account changes.
- Prior shipped SPEC check: `monefy-behavioral-fidelity-06-left-drawer-period` explicitly rendered
  the row but constrained selection as follow-up when aggregate mode did not exist. This SPEC closes
  that residual gap without replaying the shipped drawer/period work.

## Implementation links
- commit: 0b3df22, 7840305, 9ebc6e8, 34a8510
- files:
  - app/src/main/java/com/kshavrin/mymoney/navigation/MyMoneyNavHost.kt
  - core/datastore/src/main/java/com/kshavrin/mymoney/core/datastore/AppSettingsKeys.kt
  - core/datastore/src/main/java/com/kshavrin/mymoney/core/datastore/AppSettingsRepositoryImpl.kt
  - core/datastore/src/main/java/com/kshavrin/mymoney/core/datastore/model/AppSettings.kt
  - core/domain/src/main/kotlin/com/kshavrin/mymoney/core/domain/usecase/BalanceCalculator.kt
  - core/domain/src/main/kotlin/com/kshavrin/mymoney/core/domain/usecase/GetCategoryRecordsUseCase.kt
  - feature/dashboard/src/main/java/com/kshavrin/mymoney/feature/dashboard/DashboardAction.kt
  - feature/dashboard/src/main/java/com/kshavrin/mymoney/feature/dashboard/DashboardState.kt
  - feature/dashboard/src/main/java/com/kshavrin/mymoney/feature/dashboard/DashboardViewModel.kt
  - feature/dashboard/src/main/java/com/kshavrin/mymoney/feature/dashboard/components/LeftDrawerContent.kt
  - feature/transactionslist/src/main/java/com/kshavrin/mymoney/feature/transactionslist/list/TransactionsListUiState.kt
  - feature/transactionslist/src/main/java/com/kshavrin/mymoney/feature/transactionslist/list/TransactionsListViewModel.kt
  - app/src/androidTest/java/com/kshavrin/mymoney/feature/dashboard/DashboardContentUiTest.kt
  - app/src/androidTest/java/com/kshavrin/mymoney/feature/dashboard/components/DashboardDrawerContentUiTest.kt
  - core/datastore/src/test/java/com/kshavrin/mymoney/core/datastore/AppSettingsRepositoryTest.kt
  - core/domain/src/test/kotlin/com/kshavrin/mymoney/core/domain/fake/FakeRepositories.kt
  - core/domain/src/test/kotlin/com/kshavrin/mymoney/core/domain/usecase/BalanceCalculatorTest.kt
  - core/domain/src/test/kotlin/com/kshavrin/mymoney/core/domain/usecase/GetCategoryRecordsUseCaseTest.kt
  - feature/dashboard/src/test/java/com/kshavrin/mymoney/feature/dashboard/DashboardViewModelTest.kt
  - feature/transactionslist/src/test/java/com/kshavrin/mymoney/feature/transactionslist/fake/FakeTransactionRepository.kt
  - feature/transactionslist/src/test/java/com/kshavrin/mymoney/feature/transactionslist/list/TransactionsListViewModelTest.kt
