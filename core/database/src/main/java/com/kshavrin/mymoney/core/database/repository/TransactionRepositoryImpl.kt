package com.kshavrin.mymoney.core.database.repository

import com.kshavrin.mymoney.core.common.di.IoDispatcher
import com.kshavrin.mymoney.core.database.dao.TransactionDao
import com.kshavrin.mymoney.core.database.mapper.toDomain
import com.kshavrin.mymoney.core.database.mapper.toEntity
import com.kshavrin.mymoney.core.domain.model.Period
import com.kshavrin.mymoney.core.domain.model.Transaction
import com.kshavrin.mymoney.core.domain.model.TransactionKind
import com.kshavrin.mymoney.core.domain.repository.CategorySummary
import com.kshavrin.mymoney.core.domain.repository.TransactionRepository
import com.kshavrin.mymoney.core.domain.time.PeriodArithmetic
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransactionRepositoryImpl @Inject constructor(
    private val dao: TransactionDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : TransactionRepository {

    override fun observeRecent(limit: Int): Flow<List<Transaction>> =
        dao.observeRecent(limit).map { list -> list.map { it.toDomain() } }

    override suspend fun findById(id: Long): Transaction? = withContext(ioDispatcher) {
        dao.findById(id)?.toDomain()
    }

    // Placeholder: TransactionDao only exposes PagingSource for the by-account+period query and
    // observeRecent for the global feed. A dedicated non-paged period query lands in PHASE_11
    // (List + search). Until then BalanceCalculator collects observeRecent(limit=10_000).first()
    // and filters by accountId + period in memory.
    override suspend fun findByPeriod(accountId: Long, period: Period): List<Transaction> = withContext(ioDispatcher) {
        emptyList()
    }

    override suspend fun getCategorySummary(accountId: Long, period: Period, kind: TransactionKind): List<CategorySummary> = withContext(ioDispatcher) {
        val range = PeriodArithmetic.toEpochMillisRange(period)
        dao.getCategorySummary(accountId, range.first, range.last, kind.name.lowercase()).map { it.toDomain() }
    }

    override suspend fun searchByNote(query: String, limit: Int): List<Transaction> = withContext(ioDispatcher) {
        dao.searchByNote(query, limit).map { it.toDomain() }
    }

    override suspend fun upsert(transaction: Transaction): Long = withContext(ioDispatcher) {
        require(transaction.amount.signum() > 0) { "amount must be > 0; got ${transaction.amount}" }
        if (transaction.kind == TransactionKind.Transfer) {
            require(transaction.toAccountId != null) { "transfer requires toAccountId" }
            require(transaction.toAccountId != transaction.accountId) { "transfer toAccountId must differ from accountId" }
        } else {
            require(transaction.categoryId != null) { "non-transfer requires categoryId" }
        }
        require((transaction.note?.length ?: 0) <= 256) { "note must be <= 256 chars" }
        dao.upsert(transaction.toEntity())
    }

    override suspend fun softDelete(id: Long, now: Instant) = withContext(ioDispatcher) {
        dao.softDelete(id, now.toEpochMilli())
    }

    override suspend fun pruneDeleted(before: Instant) = withContext(ioDispatcher) {
        dao.pruneDeleted(before.toEpochMilli())
    }

    override suspend fun countByAccount(id: Long): Int = withContext(ioDispatcher) {
        dao.countByAccount(id)
    }

    override suspend fun countByCategory(id: Long): Int = withContext(ioDispatcher) {
        dao.countByCategory(id)
    }

    override suspend fun countByCurrency(id: Long): Int = withContext(ioDispatcher) {
        dao.countByCurrency(id)
    }
}
