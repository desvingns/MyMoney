# Records data layer: category-grouped query + use case (S01 -> records)
Epic: monefy-behavioral-fidelity
Order: 08a of 09
Status: active
Depends-on: —
Date: 2026-06-01

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: Add the domain+data for the category-grouped records screen: a single query returning, per category for an account+period, its id/name/icon/colour/kind/total/transaction-count — both income and expense, ordered by total descending, excluding soft-deleted and transfers — plus an eager per-period transaction list and a use case that buckets transactions under their category group (12.jpg/13.jpg).
LAYERS: domain data
CHANGED_HINT: core/database/.../dao/TransactionDao.kt (+ getCategoryGroups(accountId, from, to): List<CategoryGroupRow> — SUM(amount) AS total, COUNT(id) AS txCount, c.kind, INNER JOIN category c ON c.id = t.category_id, t.kind IN ('expense','income'), t.is_deleted = 0, GROUP BY c.id ORDER BY total DESC; + listByPeriod(accountId, from, to): List<TransactionEntity> ORDER BY occurred_at DESC, created_at DESC); core/database/.../projection/CategoryGroupRow.kt (NEW); core/database/.../Mappers.kt (+ CategoryGroupRow.toDomain()); core/domain/.../repository/TransactionRepository.kt (+ getCategoryGroups(accountId, period): List<CategoryGroup> + data class CategoryGroup; implement the previously-stubbed findByPeriod); core/database/.../repository/TransactionRepositoryImpl.kt (impl, reuse PeriodArithmetic.toEpochMillisRange); core/domain/.../model/CategoryRecordGroup.kt (NEW) + usecase/GetCategoryRecordsUseCase.kt (NEW); + getCategoryGroups override in ALL 6 TransactionRepository implementors; screenshots 12.jpg / 13.jpg
TEST_TYPES: unit dao
CONSTRAINTS:
  - Single grouped SQL for headers (total + count + kind, ORDER BY total DESC); transfers excluded (INNER JOIN category + the kind filter); soft-deleted excluded. No Room migration (read-only queries). Header totals/counts come from SQL (authoritative; must match the dashboard/donut figures) — NEVER recomputed from the in-memory list.
  - GetCategoryRecordsUseCase(accountId, period): resolve account -> currency (mirror BalanceCalculator), return List<CategoryRecordGroup> (categoryId, name, iconKey, colorHex, kind, total: Money, count, transactions: List<Transaction> bucketed from listByPeriod, occurredAt desc), preserving the SQL total-desc order; run on @DefaultDispatcher.
  - CRITICAL: add the getCategoryGroups override to ALL SIX TransactionRepository implementors or modules fail to compile: (1) core/database .../repository/TransactionRepositoryImpl.kt; (2) core/domain src/test .../fake/FakeRepositories.kt; (3) feature/transactionslist src/test .../fake/FakeTransactionRepository.kt; (4) feature/dashboard src/test .../DashboardViewModelTest.kt (private FakeDashboardTransactionRepository); (5) feature/dictionaries src/test .../currencies/fake/FakeTransactionRepository.kt; (6) feature/transaction src/test .../fake/FakeTransactionRepository.kt. Fakes return seeded/empty data.
  - Add a DAO androidTest (grouped SUM+COUNT, both kinds present, kind correct, ORDER BY total desc, excludes soft-deleted + transfers, empty period -> empty) + a GetCategoryRecordsUseCase unit test (groups total-desc, transactions bucketed + occurredAt-desc, currency -> Money, empty). English ids; no comments unless WHY.
=== END SPEC ===

## Gap / context
User note #7 (part c), data foundation. TransactionDao.getCategorySummary is one-kind and has no
count; TransactionRepositoryImpl.findByPeriod is a stub returning emptyList() (nothing depends on it
staying empty). The records screen (12/13) needs per-category totals + counts for BOTH kinds, plus
the underlying transactions to reveal on expand.

## Implementation links
(pending — fill commit + changed files after `/cmp --feature --next`)
