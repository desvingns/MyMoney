package com.kshavrin.mymoney.core.database.repository

import androidx.paging.PagingSource
import com.kshavrin.mymoney.core.database.dao.TransactionDao
import com.kshavrin.mymoney.core.database.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

class TransactionRepositoryImplTest {

    private data class UpdateCall(
        val id: Long,
        val occurredAt: Long,
        val updatedAt: Long,
    )

    private inner class FakeTransactionDao : TransactionDao {
        var normalizationRows: List<TransactionEntity> = emptyList()
        val updateCalls = mutableListOf<UpdateCall>()

        override fun pagedByAccount(accountId: Long, from: Long, to: Long): PagingSource<Int, TransactionEntity> =
            error("unused in test")

        override fun pagedByPeriod(
            accountId: Long,
            categoryId: Long?,
            from: Long,
            to: Long,
        ): PagingSource<Int, TransactionEntity> = error("unused in test")

        override fun observeRecent(limit: Int): Flow<List<TransactionEntity>> = flowOf(emptyList())

        override fun observeAll(): Flow<List<TransactionEntity>> = flowOf(emptyList())

        override suspend fun getCategorySummary(
            accountId: Long,
            from: Long,
            to: Long,
            kind: String,
        ) = emptyList<com.kshavrin.mymoney.core.database.projection.CategorySummaryRow>()

        override suspend fun getCategoryGroups(
            accountId: Long,
            from: Long,
            to: Long,
        ) = emptyList<com.kshavrin.mymoney.core.database.projection.CategoryGroupRow>()

        override suspend fun listByPeriod(accountId: Long, from: Long, to: Long): List<TransactionEntity> = emptyList()

        override suspend fun getTransfers(
            accountId: Long?,
            from: Long,
            to: Long,
        ) = emptyList<com.kshavrin.mymoney.core.database.projection.TransferRow>()

        override suspend fun searchByNote(q: String, limit: Int): List<TransactionEntity> = emptyList()

        override suspend fun findById(id: Long): TransactionEntity? = normalizationRows.firstOrNull { it.id == id }

        override suspend fun listForTimezoneNormalization(): List<TransactionEntity> = normalizationRows

        override suspend fun upsert(transaction: TransactionEntity): Long = transaction.id

        override suspend fun updateOccurredAt(id: Long, occurredAt: Long, updatedAt: Long) {
            updateCalls += UpdateCall(id = id, occurredAt = occurredAt, updatedAt = updatedAt)
        }

        override suspend fun softDelete(id: Long, now: Long) = Unit

        override suspend fun restore(id: Long, now: Long) = Unit

        override suspend fun pruneDeleted(before: Long) = Unit

        override suspend fun countByAccount(id: Long): Int = 0

        override suspend fun countByCategory(id: Long): Int = 0

        override suspend fun countByCurrency(id: Long): Int = 0
    }

    private val dispatcher = StandardTestDispatcher()
    private lateinit var fakeDao: FakeTransactionDao
    private lateinit var repository: TransactionRepositoryImpl

    @Before
    fun setUp() {
        fakeDao = FakeTransactionDao()
        repository = TransactionRepositoryImpl(fakeDao, dispatcher)
    }

    @Test
    fun `listForTimezoneNormalization maps dao entities into domain transactions`() = runTest(dispatcher) {
        val occurredAt = Instant.parse("2026-06-10T00:00:00Z")
        val createdAt = Instant.parse("2026-06-09T10:00:00Z")
        val updatedAt = Instant.parse("2026-06-09T11:00:00Z")
        fakeDao.normalizationRows = listOf(
            transactionEntity(
                id = 11L,
                occurredAt = occurredAt.toEpochMilli(),
                createdAt = createdAt.toEpochMilli(),
                updatedAt = updatedAt.toEpochMilli(),
            ),
        )

        val transactions = repository.listForTimezoneNormalization()

        assertEquals(1, transactions.size)
        assertEquals(11L, transactions.single().id)
        assertEquals(0, transactions.single().amount.compareTo(java.math.BigDecimal("12.5")))
        assertEquals(occurredAt, transactions.single().occurredAt)
        assertEquals(createdAt, transactions.single().createdAt)
        assertEquals(updatedAt, transactions.single().updatedAt)
        assertEquals(21L, transactions.single().toAccountId)
        assertEquals(0, transactions.single().toAmount!!.compareTo(java.math.BigDecimal("13.5")))
        assertNull(transactions.single().exchangeRate)
    }

    @Test
    fun `updateOccurredAts forwards each update as epoch millis with a shared updatedAt`() = runTest(dispatcher) {
        val sharedUpdatedAt = Instant.parse("2026-06-11T12:34:56Z")
        val updates = linkedMapOf(
            5L to Instant.parse("2026-06-10T04:00:00Z"),
            9L to Instant.parse("2026-06-09T21:00:00Z"),
        )

        repository.updateOccurredAts(updates, sharedUpdatedAt)

        assertEquals(
            listOf(
                UpdateCall(
                    id = 5L,
                    occurredAt = Instant.parse("2026-06-10T04:00:00Z").toEpochMilli(),
                    updatedAt = sharedUpdatedAt.toEpochMilli(),
                ),
                UpdateCall(
                    id = 9L,
                    occurredAt = Instant.parse("2026-06-09T21:00:00Z").toEpochMilli(),
                    updatedAt = sharedUpdatedAt.toEpochMilli(),
                ),
            ),
            fakeDao.updateCalls,
        )
        assertTrue(fakeDao.updateCalls.all { it.updatedAt == sharedUpdatedAt.toEpochMilli() })
    }

    private fun transactionEntity(
        id: Long,
        occurredAt: Long,
        createdAt: Long,
        updatedAt: Long,
    ) = TransactionEntity(
        id = id,
        kind = "expense",
        amount = 12.5,
        currencyId = 8L,
        accountId = 3L,
        categoryId = 4L,
        note = "coffee",
        occurredAt = occurredAt,
        createdAt = createdAt,
        updatedAt = updatedAt,
        isDeleted = false,
        toAccountId = 21L,
        toAmount = 13.5,
        exchangeRate = null,
    )
}
