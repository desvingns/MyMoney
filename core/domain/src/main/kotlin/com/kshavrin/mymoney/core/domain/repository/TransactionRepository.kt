package com.kshavrin.mymoney.core.domain.repository

import androidx.paging.PagingData
import com.kshavrin.mymoney.core.domain.model.CategoryKind
import com.kshavrin.mymoney.core.domain.model.Period
import com.kshavrin.mymoney.core.domain.model.Transaction
import com.kshavrin.mymoney.core.domain.model.TransactionKind
import kotlinx.coroutines.flow.Flow
import java.time.Instant

interface TransactionRepository {
    fun observeRecent(limit: Int): Flow<List<Transaction>>
    fun observeAll(): Flow<List<Transaction>>
    fun paged(accountId: Long, categoryId: Long?, from: Instant, to: Instant): Flow<PagingData<Transaction>>
    suspend fun findById(id: Long): Transaction?
    suspend fun findByPeriod(accountId: Long, period: Period): List<Transaction>
    suspend fun getCategorySummary(accountId: Long, period: Period, kind: TransactionKind): List<CategorySummary>
    suspend fun getCategoryGroups(accountId: Long, period: Period): List<CategoryGroup>
    suspend fun getTransfers(accountId: Long?, period: Period): List<TransferRow> = emptyList()
    suspend fun searchByNote(query: String, limit: Int = 200): List<Transaction>
    suspend fun upsert(transaction: Transaction): Long
    suspend fun softDelete(id: Long, now: Instant)
    suspend fun restore(id: Long, now: Instant)
    suspend fun pruneDeleted(before: Instant)
    suspend fun countByAccount(id: Long): Int
    suspend fun countByCategory(id: Long): Int
    suspend fun countByCurrency(id: Long): Int
    suspend fun listForTimezoneNormalization(): List<Transaction> =
        error("Timezone normalization requires a repository implementation")

    suspend fun updateOccurredAts(updates: Map<Long, Instant>, updatedAt: Instant) {
        error("Timezone normalization requires a repository implementation")
    }
}

data class CategorySummary(
    val categoryId: Long,
    val categoryName: String,
    val colorHex: String,
    val total: java.math.BigDecimal,
    val iconKey: String = "",
)

data class CategoryGroup(
    val categoryId: Long,
    val name: String,
    val iconKey: String,
    val colorHex: String,
    val kind: CategoryKind,
    val total: java.math.BigDecimal,
    val count: Int,
)

data class TransferRow(
    val id: Long,
    val fromAccountName: String,
    val toAccountName: String,
    val amount: java.math.BigDecimal,
    val toAmount: java.math.BigDecimal?,
    val currencyId: Long,
    val occurredAt: java.time.Instant,
)
