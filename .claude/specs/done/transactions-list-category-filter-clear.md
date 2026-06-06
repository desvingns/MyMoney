# Transactions list category filter clear
Epic: -
Order: -
Status: done
Depends-on: -
Date: 2026-06-06

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: When the records screen opens with a `categoryId` route argument, it must behave as a real active category filter, not just an initially expanded section. Show a visible removable filter chip (`Category: <name>`) above the grouped list, limit the rendered groups/transactions to that category, and let the user clear the chip in place so the same account/currency/period view reloads without the category filter.
LAYERS: [domain] [presentation]
CHANGED_HINT: core/domain/src/main/kotlin/com/kshavrin/mymoney/core/domain/usecase/GetCategoryRecordsUseCase.kt; feature/transactionslist/src/main/java/com/kshavrin/mymoney/feature/transactionslist/list/TransactionsListViewModel.kt; feature/transactionslist/src/main/java/com/kshavrin/mymoney/feature/transactionslist/list/TransactionsListUiState.kt; feature/transactionslist/src/main/java/com/kshavrin/mymoney/feature/transactionslist/list/TransactionsListEvent.kt; feature/transactionslist/src/main/java/com/kshavrin/mymoney/feature/transactionslist/list/TransactionsListScreen.kt; feature/transactionslist/src/test/java/com/kshavrin/mymoney/feature/transactionslist/list/TransactionsListViewModelTest.kt; feature/transactionslist/src/test/java/com/kshavrin/mymoney/feature/transactionslist/list/TransactionsListContentTest.kt; app/src/androidTest/java/com/kshavrin/mymoney/feature/transactionslist/list/TransactionsListContentUiTest.kt
TEST_TYPES: unit [compose-ui] [instrumented-compose-ui]
CONSTRAINTS: Reuse existing `transactions_list_filter_chip` and `transactions_list_remove_filter` strings. Keep account/currency/period route arguments intact; clearing the chip removes only the category filter. Do not change dashboard navigation contracts. Preserve existing grouped-list sorting, row clicks, swipe delete, and undo behavior. If the filtered category id is absent from loaded groups, omit the chip and fall back to the unfiltered list.
=== END SPEC ===

## Gap / context
Dashboard/category navigation already passes `categoryId`, but the records screen currently treats it only as an initial expanded-group hint. The user gets no visible filter state and no way to clear it even though the route and strings already imply a category-filter feature.

## Implementation links
- commit: local-uncommitted
- files: core/domain/src/main/kotlin/com/kshavrin/mymoney/core/domain/usecase/GetCategoryRecordsUseCase.kt; feature/transactionslist/src/main/java/com/kshavrin/mymoney/feature/transactionslist/list/TransactionsListViewModel.kt; feature/transactionslist/src/main/java/com/kshavrin/mymoney/feature/transactionslist/list/TransactionsListScreen.kt; feature/transactionslist/src/main/java/com/kshavrin/mymoney/feature/transactionslist/list/TransactionsListUiState.kt; feature/transactionslist/src/main/java/com/kshavrin/mymoney/feature/transactionslist/list/TransactionsListEvent.kt; feature/transactionslist/src/main/java/com/kshavrin/mymoney/feature/transactionslist/list/TransactionListItem.kt; core/domain/src/test/kotlin/com/kshavrin/mymoney/core/domain/usecase/GetCategoryRecordsUseCaseTest.kt; feature/transactionslist/src/test/java/com/kshavrin/mymoney/feature/transactionslist/list/TransactionsListViewModelTest.kt; feature/transactionslist/src/test/java/com/kshavrin/mymoney/feature/transactionslist/list/TransactionsListContentTest.kt; app/src/androidTest/java/com/kshavrin/mymoney/feature/transactionslist/list/TransactionsListContentUiTest.kt
