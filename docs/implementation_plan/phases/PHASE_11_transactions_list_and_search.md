# PHASE 11 — Transactions list (S12), Detail (S13), Search (S08)

## Goal

Build the history surfaces: paginated transactions list (S12) with swipe-to-delete + 5 s UNDO snackbar (AS-9), transaction detail/edit (S13), and full-screen search (S08) with text + category + date filters. After this phase the user can browse their entire history, edit any transaction, soft-delete with undo, and search by note / amount / category.

## TDD anchors

- §4.9 S08 Search records — lines 760–788
- §4.11 S12 Transactions list — lines 818–855
- §4.12 S13 Transaction detail/edit — lines 856–876
- §11.4 User stories — browsing history — lines 2464–2487
- §5 BR-24 (soft-delete 5 s undo window) — lines 1172–1207
- AS-9 (Snackbar UNDO 5 s) — §14.1 lines 2727–2750
- §7.4 DAOs `pagedByAccount`, `pagedByPeriod`, `searchByQuery` — lines 1691–1906

## Prerequisites

- PHASE_08 — done (balance-card and slice taps emit nav events that land here)
- PHASE_10 — done (S13 edit uses the same `AmountFieldSection` as S06/S07)

## Deliverables (in `:feature:transactionslist`)

- `feature/transactionslist/build.gradle.kts` — feature deps + Paging Compose + voice-recognition (`androidx.activity:activity-compose` Result API; no extra deps).
- `feature/transactionslist/src/main/java/com/kshavrin/mymoney/feature/transactionslist/list/TransactionsListScreen.kt` — S12. Accepts route args `accountId`, `categoryId?`, `from?`, `to?`. Shows `LazyColumn` backed by `Pager(PagingConfig(pageSize = 50))`. Sticky day headers (`stickyHeader { DayHeader(date) }`).
- `feature/transactionslist/src/main/java/com/kshavrin/mymoney/feature/transactionslist/list/TransactionsListViewModel.kt` — `@HiltViewModel`. State: `pagingData: Flow<PagingData<TransactionUi>>`. Events: `RowClicked(id)`, `SwipeDeleted(id)`, `UndoDeleteClicked(id)`.
- `feature/transactionslist/src/main/java/com/kshavrin/mymoney/feature/transactionslist/list/TransactionUi.kt` — paging item: category icon + colour, amount (signed + currency), date, note preview.
- `feature/transactionslist/src/main/java/com/kshavrin/mymoney/feature/transactionslist/list/SwipeToDelete.kt` — wraps `SwipeToDismissBox(Material 3)` per row. On `Settled at EndToStart` → emit `SwipeDeleted(id)`.
- `feature/transactionslist/src/main/java/com/kshavrin/mymoney/feature/transactionslist/detail/TransactionDetailScreen.kt` — S13. Edit mode mirrors S06/S07/S03 forms (delegate to `:feature:transaction` shared components). Buttons: Save, Delete (with confirm).
- `feature/transactionslist/src/main/java/com/kshavrin/mymoney/feature/transactionslist/detail/TransactionDetailViewModel.kt` — load by id; mutate; save → upsert; delete → soft-delete (`is_deleted = 1`).
- `feature/transactionslist/src/main/java/com/kshavrin/mymoney/feature/transactionslist/search/SearchScreen.kt` — S08. `SearchBar` (M3) + filter chips (category, date range, amount range). Suggestions from `SearchHistoryEntity` (top 10). Voice search via `RecognizerIntent` (`ActivityResultContracts.StartActivityForResult`). On result selected → navigate to S13.
- `feature/transactionslist/src/main/java/com/kshavrin/mymoney/feature/transactionslist/search/SearchViewModel.kt` — debounced (`flow { ... }.debounce(300.ms)`) query updates. On submit → write `SearchHistoryEntity` (pruned to 20 distinct).

## Task checklist

- [ ] Re-read §4.9, §4.11, §4.12 + BR-24 + AS-9.
- [ ] **Paging**: write `TransactionDao.pagedByPeriod(accountId, categoryId?, from, to)` (already in PHASE_04 PagingSource). Wire into a `Pager` in the ViewModel.
- [ ] **Day grouping**: in the Paging flow, use `insertSeparators` to inject `DayHeader(date)` between rows with different `occurredAt.toLocalDate()` values. Or use `LazyColumn.stickyHeader` with a `derivedStateOf` for the visible date.
- [ ] **Swipe-to-delete + UNDO** (AS-9 / BR-24):
  - On swipe → ViewModel marks `is_deleted = 1` AND emits a snackbar action `Action.ShowUndoSnackbar(transactionId)`.
  - Screen `LaunchedEffect(actions)` displays a `SnackbarHostState.showSnackbar(message = "Deleted", actionLabel = "UNDO", duration = 5_000.ms)`.
  - If user taps UNDO → `transactionRepository.restore(id)` flips `is_deleted = 0`.
  - If snackbar dismisses normally (timeout) → the soft-delete is final. PHASE_14's `PruneDeletedWorker` may physically remove it after 30 days.
- [ ] **Detail edit (S13)**: reuse `AmountFieldSection` from `:feature:transaction`. Delete-button click → AlertDialog confirm → soft-delete + pop.
- [ ] **Search (S08)**:
  - Free-text query searches `note` + amount (parse as `BigDecimal`) + category name. `transactionRepository.search(query, filters)` returns `Flow<PagingData<TransactionUi>>`.
  - Debounce 300 ms.
  - Voice search button → `Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(EXTRA_LANGUAGE_MODEL, LANGUAGE_MODEL_FREE_FORM)`. On result → set query.
  - Suggestions: top 10 entries from `SearchHistoryEntity` ordered by `usedAt DESC`. Tap → fills query.
  - On submit → write to `SearchHistoryEntity` + prune to 20.
- [ ] Wire from dashboard:
  - Balance pill tap (AS-2) → `transactions?accountId={id}` (no category filter).
  - Donut slice tap (AS-3) → `transactions?accountId={id}&categoryId={categoryId}`.
  - Search icon top-right → `search`.
- [ ] Test e2e:
  - Scroll list with 200+ rows seeded → smooth at 90 Hz.
  - Swipe → snackbar → UNDO → row reappears.
  - Swipe → wait 6 s → snackbar dismisses → row stays hidden (soft-deleted).
  - Edit row → save → list updates.
  - Search "lunch" → matching note rows appear.
  - Voice-search → speak "groceries" → query field fills.
- [ ] Update PROGRESS.md.

## Done criteria

- `.\gradlew.bat :feature:transactionslist:assembleDebug` succeeds.
- 200-row scroll smooth (visual QA).
- AS-9 / BR-24 observable: 5 s UNDO window.
- Edit + delete flows persist correctly.
- Search filters by text / category / date.

## Verification commands

```powershell
cd D:\Pet\TDD_creater\MyMoney_app
.\gradlew.bat :feature:transactionslist:assembleDebug
.\gradlew.bat :feature:transactionslist:test
.\gradlew.bat :app:installDebug
```

## Notes for next session

(empty — fill at end of session)
