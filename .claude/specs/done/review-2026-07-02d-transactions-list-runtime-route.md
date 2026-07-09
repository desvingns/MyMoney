# Restore S12 transactions-list runtime route
Epic: review-2026-07
Order: 02d of 35
Status: done
Depends-on: review-2026-07-02-slice5-release-qa
Date: 2026-07-08

## SPEC
=== SPEC ===
TASK: bugfix
WHAT: Restore a production runtime path to TDD S12 Transactions list. The Slice 5 signed-release walkthrough could cover S08 Search, S13 detail/edit, and the dashboard operations summary sheet, but could not open the full S12 transactions-list destination because `Destinations.TRANSACTIONS_LIST` is declared/tested while `MyMoneyNavHost` has no matching `composable(...)` and no current release UI action navigates to it.
LAYERS: [presentation]
CHANGED_HINT: `app/src/main/java/com/kshavrin/mymoney/navigation/MyMoneyNavHost.kt`, `feature/transactionslist`, dashboard/search/detail navigation tests as needed
TEST_TYPES: [compose-ui, unit]
CONSTRAINTS: preserve the dashboard operations-summary sheet behavior; do not introduce `:feature:* -> :feature:*` dependencies; keep strings localized; cover the restored route on `Pixel_5_API_34`.
=== END SPEC ===

## Gap / context

- TDD screen inventory includes S12 Transactions list as a required destination.
- `app/src/main/java/com/kshavrin/mymoney/navigation/Destinations.kt` still declares `TRANSACTIONS_LIST = "transactions"`.
- `app/src/main/java/com/kshavrin/mymoney/navigation/MyMoneyNavHost.kt` wires S08 `SearchRoute`, S13 `TransactionDetailRoute`, and S27 `CurrencyRateRoute`, but no `composable(Destinations.TRANSACTIONS_LIST)` exists.
- `feature/transactionslist/src/main` currently contains search/detail runtime screens, while the transactions-list content coverage appears only in tests/legacy tracker entries.
- Slice 5 signed-release evidence under `build/visual-check/release-walk/` includes dashboard summary sheet `48-dashboard-food-summary-release.*` and S13 detail `50-s13-transaction-detail-release.*`, but no full S12 release screen because the route is unreachable.

## Acceptance criteria

- A user-visible release UI path opens the full S12 transactions list for the relevant dashboard/search/filter context.
- The restored route preserves existing dashboard operations-summary behavior and row-to-S13 navigation.
- Navigation/unit coverage asserts `Destinations.TRANSACTIONS_LIST` is actually registered in the app graph.
- Connected Compose coverage on `Pixel_5_API_34` proves the runtime route can open and return.
- `docs/DEVICE_VERIFICATION_PROGRESS.md` is updated with the new S12 runtime-route evidence.

## Implementation links

- commit: `20588d82` (`fix: restore transactions list route`)
- closeout: mixed-currency `ConvertTo` summaries that cannot be faithfully represented by the S12 route hide/ignore the Transactions CTA; covered by dashboard ViewModel, sheet UI, route-contract, and S12 default-period regression tests.
- files: `app/src/main/java/com/kshavrin/mymoney/navigation/MyMoneyNavHost.kt`, `feature/dashboard/src/main/java/com/kshavrin/mymoney/feature/dashboard/DashboardAction.kt`, `feature/dashboard/src/main/java/com/kshavrin/mymoney/feature/dashboard/DashboardScreen.kt`, `feature/dashboard/src/main/java/com/kshavrin/mymoney/feature/dashboard/DashboardState.kt`, `feature/dashboard/src/main/java/com/kshavrin/mymoney/feature/dashboard/DashboardViewModel.kt`, `feature/dashboard/src/main/java/com/kshavrin/mymoney/feature/dashboard/components/OperationsSummarySheet.kt`, `feature/transactionslist/src/main/java/com/kshavrin/mymoney/feature/transactionslist/list/`, `app/src/test/java/com/kshavrin/mymoney/navigation/DestinationsTest.kt`, `app/src/androidTest/java/com/kshavrin/mymoney/TransactionsListRuntimeRouteTest.kt`, `docs/DEVICE_VERIFICATION_PROGRESS.md`, `docs/implementation_plan/PROGRESS.md`
