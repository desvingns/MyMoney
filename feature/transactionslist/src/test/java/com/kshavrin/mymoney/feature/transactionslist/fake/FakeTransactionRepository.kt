package com.kshavrin.mymoney.feature.transactionslist.fake

import androidx.paging.PagingData
import com.kshavrin.mymoney.core.domain.model.Period
import com.kshavrin.mymoney.core.domain.model.Transaction
import com.kshavrin.mymoney.core.domain.model.TransactionKind
import com.kshavrin.mymoney.core.domain.repository.CategoryGroup
import com.kshavrin.mymoney.core.domain.repository.CategorySummary
import com.kshavrin.mymoney.core.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import java.time.Instant

/**
 * Fake at the repository boundary for the S12 category-grouped records ViewModel tests.
 *
 * - `seedCategoryGroups(...)` feeds the authoritative per-category header rows (total + count +
 *   kind) that [getCategoryGroups] returns — the SQL-side figures the use case must NOT recompute.
 * - `seedPeriodTransactions(...)` feeds the leaf rows that [findByPeriod] returns; the use case
 *   buckets them under their category group (occurredAt desc).
 * - `seed(...)` is the legacy seam used by the detail/search tests; it also backs [findByPeriod]
 *   when no explicit period transactions are seeded.
 * - [softDelete] / [restore] record their ids so tests can assert the swipe + undo wiring
 *   without touching a real database.
 */
class FakeTransactionRepository : TransactionRepository {

    data class PagedCall(
        val accountId: Long,
        val categoryId: Long?,
        val from: Instant,
        val to: Instant,
    )

    val pagedCalls: MutableList<PagedCall> = mutableListOf()
    val softDeletedIds: MutableList<Long> = mutableListOf()
    val restoredIds: MutableList<Long> = mutableListOf()

    /** Every (query, limit) pair the ViewModel forwarded to [searchByNote]. */
    val searchCalls: MutableList<Pair<String, Int>> = mutableListOf()

    private val page = MutableStateFlow<List<Transaction>>(emptyList())

    private var categoryGroups: List<CategoryGroup> = emptyList()
    private var periodTransactions: List<Transaction>? = null

    /** Seeds the authoritative header rows [getCategoryGroups] returns (already total-desc ordered). */
    fun seedCategoryGroups(vararg groups: CategoryGroup) {
        categoryGroups = groups.toList()
    }

    /** Seeds the leaf rows [findByPeriod] returns for the records use case to bucket. */
    fun seedPeriodTransactions(vararg items: Transaction) {
        periodTransactions = items.toList()
    }

    /** Controllable result set returned by [searchByNote]; independent of the paged [page]. */
    private var searchResults: List<Transaction> = emptyList()

    /** When set, [searchByNote] throws this instead of returning, to drive the Error state. */
    private var searchError: Throwable? = null

    fun seed(vararg items: Transaction) {
        page.value = items.toList()
    }

    /** Seeds the rows [searchByNote] returns (S08 result / empty-result cases). */
    fun seedSearchResults(vararg items: Transaction) {
        searchResults = items.toList()
    }

    /** Makes the next (and subsequent) [searchByNote] calls throw, to drive the Error state. */
    fun failSearchWith(error: Throwable = RuntimeException("search boom")) {
        searchError = error
    }

    override fun paged(
        accountId: Long,
        categoryId: Long?,
        from: Instant,
        to: Instant,
    ): Flow<PagingData<Transaction>> {
        pagedCalls.add(PagedCall(accountId, categoryId, from, to))
        return flowOf(PagingData.from(page.value))
    }

    override fun observeRecent(limit: Int): Flow<List<Transaction>> = page.asStateFlow()
    override fun observeAll(): Flow<List<Transaction>> = page.asStateFlow()
    override suspend fun findById(id: Long): Transaction? = page.value.firstOrNull { it.id == id }
    override suspend fun findByPeriod(accountId: Long, period: Period): List<Transaction> =
        periodTransactions ?: page.value.filterNot { it.isDeleted }
    override suspend fun getCategorySummary(
        accountId: Long,
        period: Period,
        kind: TransactionKind,
    ): List<CategorySummary> = categoryGroups
        .filter { group ->
            when (kind) {
                TransactionKind.Expense -> group.kind == com.kshavrin.mymoney.core.domain.model.CategoryKind.Expense
                TransactionKind.Income -> group.kind == com.kshavrin.mymoney.core.domain.model.CategoryKind.Income
                TransactionKind.Transfer -> false
            }
        }
        .map { group ->
            CategorySummary(
                categoryId = group.categoryId,
                categoryName = group.name,
                colorHex = group.colorHex,
                total = group.total,
                iconKey = group.iconKey,
            )
        }
    override suspend fun getCategoryGroups(accountId: Long, period: Period): List<CategoryGroup> = categoryGroups
    override suspend fun searchByNote(query: String, limit: Int): List<Transaction> {
        searchCalls.add(query to limit)
        searchError?.let { throw it }
        return searchResults
    }

    override suspend fun upsert(transaction: Transaction): Long {
        val id = if (transaction.id == 0L) (page.value.maxOfOrNull { it.id } ?: 0L) + 1L else transaction.id
        page.value = page.value.filterNot { it.id == id } + transaction.copy(id = id)
        return id
    }

    override suspend fun softDelete(id: Long, now: Instant) {
        softDeletedIds.add(id)
        page.value = page.value.map { if (it.id == id) it.copy(isDeleted = true, updatedAt = now) else it }
    }

    override suspend fun restore(id: Long, now: Instant) {
        restoredIds.add(id)
        page.value = page.value.map { if (it.id == id) it.copy(isDeleted = false, updatedAt = now) else it }
    }

    override suspend fun pruneDeleted(before: Instant) {
        page.value = page.value.filterNot { it.isDeleted && it.updatedAt.isBefore(before) }
    }

    override suspend fun countByAccount(id: Long): Int =
        page.value.count { !it.isDeleted && (it.accountId == id || it.toAccountId == id) }

    override suspend fun countByCategory(id: Long): Int =
        page.value.count { !it.isDeleted && it.categoryId == id }

    override suspend fun countByCurrency(id: Long): Int =
        page.value.count { !it.isDeleted && it.currencyId == id }
}
