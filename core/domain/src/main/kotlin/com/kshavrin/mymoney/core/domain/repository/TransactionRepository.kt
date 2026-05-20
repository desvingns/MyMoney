package com.kshavrin.mymoney.core.domain.repository

import com.kshavrin.mymoney.core.domain.model.Period
import com.kshavrin.mymoney.core.domain.model.Transaction
import com.kshavrin.mymoney.core.domain.model.TransactionKind
import kotlinx.coroutines.flow.Flow
import java.time.Instant

interface TransactionRepository {
    fun observeRecent(limit: Int): Flow<List<Transaction>>
    suspend fun findById(id: Long): Transaction?
    suspend fun findByPeriod(accountId: Long, period: Period): List<Transaction>
    suspend fun getCategorySummary(accountId: Long, period: Period, kind: TransactionKind): List<CategorySummary>
    suspend fun searchByNote(query: String, limit: Int = 200): List<Transaction>
    suspend fun upsert(transaction: Transaction): Long
    suspend fun softDelete(id: Long, now: Instant)
    suspend fun pruneDeleted(before: Instant)
}

data class CategorySummary(
    val categoryId: Long,
    val categoryName: String,
    val colorHex: String,
    val total: java.math.BigDecimal,
)
