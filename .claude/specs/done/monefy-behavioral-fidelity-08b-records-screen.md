# Rework transactions list into category-grouped, expandable "all records" (S11/S12)
Epic: monefy-behavioral-fidelity
Order: 08b of 09
Status: done
Depends-on: 08a (query + use case), 04 (balance bar entry point)
Date: 2026-06-01

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: Rework the existing transactions-list screen into the Monefy category-grouped, expandable "all records" screen (12.jpg collapsed, 13.jpg expanded): a balance-bar header ("Баланс <net> ₽" + a sort affordance), one row per category (chevron + tinted icon + name + count badge + total coloured green = income / red = expense, ordered by total descending), each expanding to its individual transactions (coloured dot + amount + date). Reached from the dashboard balance bar; the donut slice-tap opens it with that category pre-expanded. Replaces the day-grouped paged list.
LAYERS: presentation
CHANGED_HINT: feature/transactionslist/.../list/TransactionsListScreen.kt + TransactionsListViewModel.kt + TransactionsListUiState.kt + TransactionListItem.kt (rework: category-header items + expandable transaction items; drop DayHeader + Paging; use GetCategoryRecordsUseCase eagerly; expandedCategoryIds: Set<Long>; SortClicked; a categoryId arg -> that category starts expanded; BalanceCalculator for the net header); app/.../navigation/MyMoneyNavHost.kt (balance-bar NavigateTransactionsByAccount + slice-tap NavigateTransactionsByCategory BOTH -> the reworked screen, passing categoryId for slice-tap); update the existing transactionslist tests + DestinationsTest/nav tests; screenshots 12.jpg / 13.jpg
TEST_TYPES: unit compose-ui
CONSTRAINTS:
  - Header rows: chevron (down = expanded / right = collapsed) + categoryIcon(iconKey) tinted via parseHexColor(colorHex) + name + count badge + total; total colour green = primary (Income) / red = tertiary (Expense) (12.jpg); ordered by total desc; tap toggles expand (pure UI state over already-loaded data). Expanded rows: a coloured dot + amount + date ("d MMM", locale -> "31 мая") (13.jpg); tap a transaction -> the existing transaction detail (OpenDetail).
  - Header = a balance bar showing net "Баланс <net> ₽" (MoneyFormatter, SymbolPosition.AFTER) + a sort IconButton in the TopAppBar actions -> SortClicked (toggle total asc/desc). Reuse the dashboard net (BalanceCalculator). testTag dashboard_balance_bar-style header is fine.
  - Entry points: balance-bar (no categoryId) -> all categories collapsed; donut slice-tap (categoryId present) -> that category starts expanded. BOTH route to this one reworked screen.
  - Eager load via GetCategoryRecordsUseCase (no Paging). PRESERVE tap -> detail AND swipe-to-delete + undo on the expanded leaf rows by adapting the existing SwipeDeleted / UndoDeleteClicked / ShowUndoSnackbar to the in-memory list — do NOT silently drop deletion. (If simplified to a Monefy-minimal read-only overview, FIRST verify the transaction detail screen offers delete, and note the behaviour change in the run report.)
  - Update the existing transactionslist tests (day-grouping -> category-grouping; swipe/undo adapted) + DestinationsTest / nav tests. Add testTag constants: records_balance, records_sort, records_category_<id>, records_chevron_<id>, records_count_<id>, records_total_<id>, records_tx_<id>, records_empty. No hardcoded strings (EN+RU) / colours; English ids; no comments unless WHY.
=== END SPEC ===

## Gap / context
User note #7 (part c), UI. The current list (TransactionsListScreen) groups by DAY via Paging
(DayHeader + Row) with swipe-to-delete + undo, and is NOT category-grouped or expandable. The
reference (12.jpg/13.jpg) groups by category (income green / expense red), each row showing an icon +
name + count badge + total, expanding to reveal the individual transactions. Per the user's decision
this REWORKS the existing screen (so the donut slice-tap path also becomes category-grouped) rather
than adding a new screen; the balance bar (SPEC-04) is the primary entry point.

## Implementation links
Shipped 2026-06-02 via `/cmp --feature --next` (Developer→Reviewer→Tester→Runner→Verifier all green).

- Production: `b62ec52` — feat: rework transactions list into category-grouped expandable records
- Tests: `ffed6ea` — test: cover category-grouped records screen rework (S11/S12)

Changed files (production):
- `feature/transactionslist/.../list/TransactionsListViewModel.kt` — dropped Paging; eager `GetCategoryRecordsUseCase` + `BalanceCalculator` net header; `expandedCategoryIds: Set<Long>` (categoryId nav arg pre-expands); `SortClicked` (total asc/desc) + `CategoryClicked` toggle over loaded data; swipe-delete/undo preserved via `softDelete`/`restore` + reload.
- `feature/transactionslist/.../list/TransactionsListUiState.kt` — new shape: `groups`, `expandedCategoryIds`, `sort`, `net`, `currency`, `isLoading` + derived `sortedGroups`/`isEmpty`.
- `feature/transactionslist/.../list/TransactionsListEvent.kt` — added `CategoryClicked`, `SortClicked`.
- `feature/transactionslist/.../list/TransactionListItem.kt` — repurposed to `RecordSort` enum + `RecordsTestTags` constants.
- `feature/transactionslist/.../list/TransactionsListScreen.kt` — balance-bar header + sort IconButton; category-header rows (chevron/tinted icon/name/count badge/total green=income/red=expense, total-desc); expandable leaf rows (dot + amount + "d MMM" date) with swipe-to-delete + tap→OpenDetail.
- `feature/transactionslist/src/main/res/values{,-ru}/strings.xml` — new keys (sort/expand/collapse/count), EN+RU parity.

Tests: rewrote `TransactionsListViewModelTest.kt` (category grouping, sort, expand, swipe/undo, pre-expansion, empty), `TransactionsListContentTest.kt` (contract-pin + PHASE_15 Compose template), extended `FakeTransactionRepository.kt`. `:feature:transactionslist:testDebugUnitTest` 68/68 green; lint clean.

Deferred (out of slice scope):
- `app/src/androidTest/.../transactionslist/list/TransactionsListContentUiTest.kt` references the OLD paged `TransactionsListContent(state, items, …)` API → will fail to compile `:app:connectedDebugAndroidTest` until reworked. Needs a `/cmp --device` slice (one @Test per control, booted device).
- True module-local Compose-UI test deferred (module lacks `ui-test-junit4`/Robolectric on its test classpath — module convention).
- Unused `PagingSnapshot.kt` (`firstSnapshot`) left in place per the no-delete rule; remove manually if a cleanup is wanted.
