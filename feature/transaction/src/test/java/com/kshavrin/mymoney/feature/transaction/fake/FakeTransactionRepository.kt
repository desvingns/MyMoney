package com.kshavrin.mymoney.feature.transaction.fake

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

class FakeTransactionRepository : TransactionRepository {
    val upserted: MutableList<Transaction> = mutableListOf()

    private val transactions = MutableStateFlow<List<Transaction>>(emptyList())

    override fun observeRecent(limit: Int): Flow<List<Transaction>> = transactions.asStateFlow()

    override fun observeAll(): Flow<List<Transaction>> = transactions.asStateFlow()

    override fun paged(
        accountId: Long,
        categoryId: Long?,
        from: Instant,
        to: Instant,
    ): Flow<PagingData<Transaction>> = flowOf(PagingData.empty())

    override suspend fun findById(id: Long): Transaction? = transactions.value.firstOrNull { it.id == id }

    override suspend fun findByPeriod(
        accountId: Long,
        period: Period,
    ): List<Transaction> = emptyList()

    override suspend fun getCategorySummary(
        accountId: Long,
        period: Period,
        kind: TransactionKind,
    ): List<CategorySummary> = emptyList()

    override suspend fun getCategoryGroups(
        accountId: Long,
        period: Period,
    ): List<CategoryGroup> = emptyList()

    override suspend fun searchByNote(
        query: String,
        limit: Int,
    ): List<Transaction> = emptyList()

    override suspend fun upsert(transaction: Transaction): Long {
        val id = if (transaction.id == 0L) (transactions.value.maxOfOrNull { it.id } ?: 0L) + 1L else transaction.id
        val stored = transaction.copy(id = id)
        upserted.add(stored)
        transactions.value = transactions.value.filterNot { it.id == id } + stored
        return id
    }

    override suspend fun uuidForId(id: Long): String? = null

    override suspend fun applySharedUpsert(
        transaction: Transaction,
        uuid: String,
        deviceId: String,
    ) = Unit

    override suspend fun applySharedDelete(
        uuid: String,
        now: Instant,
    ) = Unit

    override suspend fun softDelete(
        id: Long,
        now: Instant,
    ) {
        transactions.value =
            transactions.value.map {
                if (it.id == id) it.copy(isDeleted = true, updatedAt = now) else it
            }
    }

    override suspend fun restore(
        id: Long,
        now: Instant,
    ) {
        transactions.value =
            transactions.value.map {
                if (it.id == id) it.copy(isDeleted = false, updatedAt = now) else it
            }
    }

    override suspend fun pruneDeleted(before: Instant) {
        transactions.value = transactions.value.filterNot { it.isDeleted && it.updatedAt.isBefore(before) }
    }

    override suspend fun countByAccount(id: Long): Int =
        transactions.value.count { !it.isDeleted && (it.accountId == id || it.toAccountId == id) }

    override suspend fun countByCategory(id: Long): Int =
        transactions.value.count { !it.isDeleted && it.categoryId == id }

    override suspend fun countByCurrency(id: Long): Int =
        transactions.value.count { !it.isDeleted && it.currencyId == id }
}
