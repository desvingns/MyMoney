package com.kshavrin.mymoney.core.database.repository

import androidx.paging.PagingSource
import com.kshavrin.mymoney.core.database.dao.AccountDao
import com.kshavrin.mymoney.core.database.dao.CategoryDao
import com.kshavrin.mymoney.core.database.dao.CurrencyDao
import com.kshavrin.mymoney.core.database.dao.OperationDao
import com.kshavrin.mymoney.core.database.dao.TransactionDao
import com.kshavrin.mymoney.core.database.entity.AccountEntity
import com.kshavrin.mymoney.core.database.entity.CategoryEntity
import com.kshavrin.mymoney.core.database.entity.CurrencyEntity
import com.kshavrin.mymoney.core.database.entity.OperationEntity
import com.kshavrin.mymoney.core.database.entity.TransactionEntity
import com.kshavrin.mymoney.core.database.journal.OperationPayloadCodec
import com.kshavrin.mymoney.core.domain.sync.DeviceIdProvider
import com.kshavrin.mymoney.core.domain.transaction.TransactionRunner
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Clock
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

        override fun pagedByAccount(
            accountId: Long,
            from: Long,
            to: Long,
        ): PagingSource<Int, TransactionEntity> =
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

        override suspend fun listByPeriod(
            accountId: Long,
            from: Long,
            to: Long,
        ): List<TransactionEntity> = emptyList()

        var getTransfersCalls: MutableList<Triple<Long?, Long, Long>> = mutableListOf()
        var transferRows: List<com.kshavrin.mymoney.core.database.projection.TransferRow> = emptyList()

        override suspend fun getTransfers(
            accountId: Long?,
            from: Long,
            to: Long,
        ): List<com.kshavrin.mymoney.core.database.projection.TransferRow> {
            getTransfersCalls.add(Triple(accountId, from, to))
            return transferRows
        }

        override suspend fun searchByNote(
            q: String,
            limit: Int,
        ): List<TransactionEntity> = emptyList()

        override suspend fun listDedupRows(): List<com.kshavrin.mymoney.core.database.projection.TransactionDedupRow> = emptyList()

        override suspend fun deleteAll() = Unit

        override suspend fun reassignCategory(
            oldId: Long,
            newId: Long,
        ) = Unit

        override suspend fun deleteByCategory(categoryId: Long) = Unit

        override suspend fun findById(id: Long): TransactionEntity? = normalizationRows.firstOrNull { it.id == id }

        override suspend fun listForTimezoneNormalization(): List<TransactionEntity> = normalizationRows

        override suspend fun listAll(): List<TransactionEntity> = normalizationRows

        override suspend fun stampMissingDeviceId(deviceId: String) = Unit

        override suspend fun upsert(transaction: TransactionEntity): Long = transaction.id

        override suspend fun updateOccurredAt(
            id: Long,
            occurredAt: Long,
            updatedAt: Long,
        ) {
            updateCalls += UpdateCall(id = id, occurredAt = occurredAt, updatedAt = updatedAt)
        }

        override suspend fun findByUuid(uuid: String): TransactionEntity? =
            normalizationRows.firstOrNull { it.uuid == uuid }

        override suspend fun softDeleteByUuid(
            uuid: String,
            now: Long,
        ) = Unit

        override suspend fun softDelete(
            id: Long,
            now: Long,
        ) = Unit

        override suspend fun restore(
            id: Long,
            now: Long,
        ) = Unit

        override suspend fun pruneDeleted(before: Long) = Unit

        override suspend fun countByAccount(id: Long): Int = 0

        override suspend fun countByCategory(id: Long): Int = 0

        override suspend fun countByCurrency(id: Long): Int = 0
    }

    private val dispatcher = StandardTestDispatcher()
    private val accountDao =
        object : AccountDao() {
            override fun observeActive(): Flow<List<AccountEntity>> = flowOf(emptyList())

            override suspend fun findById(id: Long): AccountEntity? = null

            override suspend fun findByUuid(uuid: String): AccountEntity? = null

            override suspend fun listAll(): List<AccountEntity> = emptyList()

            override suspend fun stampMissingDeviceId(deviceId: String) = Unit

            override suspend fun archiveByUuid(uuid: String) = Unit

            override suspend fun findDefault(): AccountEntity? = null

            override suspend fun listDefaults(): List<AccountEntity> = emptyList()

            override suspend fun computeBalance(id: Long): Double = 0.0

            override suspend fun upsert(account: AccountEntity): Long = account.id

            override suspend fun archive(id: Long) = Unit

            override suspend fun countByCurrency(id: Long): Int = 0

            override suspend fun deleteAll() = Unit

            override suspend fun clearDefaults() = Unit

            override suspend fun markDefault(id: Long) = Unit
        }
    private val categoryDao =
        object : CategoryDao {
            override fun observeByKind(kind: String): Flow<List<CategoryEntity>> = flowOf(emptyList())

            override fun observeAll(): Flow<List<CategoryEntity>> = flowOf(emptyList())

            override suspend fun findById(id: Long): CategoryEntity? = null

            override suspend fun findByUuid(uuid: String): CategoryEntity? = null

            override suspend fun listAll(): List<CategoryEntity> = emptyList()

            override suspend fun stampMissingDeviceId(deviceId: String) = Unit

            override suspend fun archiveByUuid(uuid: String) = Unit

            override suspend fun upsert(category: CategoryEntity): Long = category.id

            override suspend fun upsertAll(categories: List<CategoryEntity>) = Unit

            override suspend fun archive(id: Long) = Unit

            override suspend fun rename(
                id: Long,
                name: String,
            ) = Unit

            override suspend fun deleteAll() = Unit

            override suspend fun deleteByIds(ids: List<Long>) = Unit
        }
    private val currencyDao =
        object : CurrencyDao {
            private val fixed = CurrencyEntity(id = 8L, code = "USD", symbol = "$", name = "US Dollar", decimalDigits = 2, isActive = true, sortOrder = 1)

            override fun observeActive(): Flow<List<CurrencyEntity>> = flowOf(listOf(fixed))

            override fun observeAll(): Flow<List<CurrencyEntity>> = flowOf(listOf(fixed))

            override suspend fun findById(id: Long): CurrencyEntity? = fixed.copy(id = id)

            override suspend fun findByCode(code: String): CurrencyEntity? = fixed.takeIf { it.code.equals(code, ignoreCase = true) }

            override suspend fun upsert(item: CurrencyEntity): Long = item.id

            override suspend fun upsertAll(items: List<CurrencyEntity>) = Unit

            override suspend fun setActive(
                id: Long,
                active: Boolean,
            ) = Unit

            override suspend fun activateCurrenciesWithLiveTransactions(): Int = 0
        }
    private val operationDao =
        object : OperationDao {
            override suspend fun insert(op: OperationEntity) = Unit

            override suspend fun insertAll(ops: List<OperationEntity>) = Unit

            override suspend fun insertApplied(ops: List<OperationEntity>) = Unit

            override suspend fun unsyncedLocal(): List<OperationEntity> = emptyList()

            override suspend fun localOps(): List<OperationEntity> = emptyList()

            override suspend fun markSynced(opIds: List<String>) = Unit

            override suspend fun knownOpIds(): List<String> = emptyList()

            override suspend fun knownEntityUuids(): List<String> = emptyList()

            override suspend fun existsByOpId(opId: String): Boolean = false

            override suspend fun opsForEntity(entityUuid: String): List<OperationEntity> = emptyList()

            override suspend fun deleteAll() = Unit
        }
    private val deviceIdProvider =
        object : DeviceIdProvider {
            override suspend fun deviceId(): String = "device-id"
        }
    private val transactionRunner =
        object : TransactionRunner {
            override suspend fun <T> runInTransaction(block: suspend () -> T): T = block()
        }
    private lateinit var fakeDao: FakeTransactionDao
    private lateinit var repository: TransactionRepositoryImpl

    @Before
    fun setUp() {
        fakeDao = FakeTransactionDao()
        repository =
            TransactionRepositoryImpl(
                dao = fakeDao,
                accountDao = accountDao,
                categoryDao = categoryDao,
                operationDao = operationDao,
                payloadCodec = OperationPayloadCodec(currencyDao),
                deviceIdProvider = deviceIdProvider,
                clock = Clock.systemUTC(),
                transactionRunner = transactionRunner,
                ioDispatcher = dispatcher,
            )
    }

    @Test
    fun `listForTimezoneNormalization maps dao entities into domain transactions`() =
        runTest(dispatcher) {
            val occurredAt = Instant.parse("2026-06-10T00:00:00Z")
            val createdAt = Instant.parse("2026-06-09T10:00:00Z")
            val updatedAt = Instant.parse("2026-06-09T11:00:00Z")
            fakeDao.normalizationRows =
                listOf(
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
    fun `updateOccurredAts forwards each update as epoch millis with a shared updatedAt`() =
        runTest(dispatcher) {
            val sharedUpdatedAt = Instant.parse("2026-06-11T12:34:56Z")
            val updates =
                linkedMapOf(
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

    @Test
    fun `getTransfers maps DbTransferRow to DomainTransferRow and forwards period as epoch millis`() =
        runTest(dispatcher) {
            val occurredAt = Instant.parse("2026-06-10T12:00:00Z")
            fakeDao.transferRows =
                listOf(
                    com.kshavrin.mymoney.core.database.projection.TransferRow(
                        id = 77L,
                        fromAccountName = "Наличные",
                        toAccountName = "Карта",
                        amount = 500.0,
                        toAmount = 450.0,
                        currencyId = 8L,
                        occurredAt = occurredAt.toEpochMilli(),
                        note = null,
                    ),
                )

            val period =
                com.kshavrin.mymoney.core.domain.model.Period.Month(
                    java.time.YearMonth.of(2026, 6),
                )
            val rows = repository.getTransfers(accountId = 3L, period = period)

            assertEquals(1, rows.size)
            val row = rows.single()
            assertEquals(77L, row.id)
            assertEquals("Наличные", row.fromAccountName)
            assertEquals("Карта", row.toAccountName)
            assertEquals(0, java.math.BigDecimal("500.0").compareTo(row.amount))
            assertEquals(0, java.math.BigDecimal("450.0").compareTo(row.toAmount))
            assertEquals(8L, row.currencyId)
            assertEquals(occurredAt, row.occurredAt)

            val (calledAccountId, from, to) = fakeDao.getTransfersCalls.single()
            assertEquals(3L, calledAccountId)
            val range =
                com.kshavrin.mymoney.core.domain.time.PeriodArithmetic
                    .toEpochMillisRange(period)
            assertEquals(range.first, from)
            assertEquals(range.last, to)
        }

    @Test
    fun `getTransfers with null accountId passes null through to the DAO`() =
        runTest(dispatcher) {
            val period =
                com.kshavrin.mymoney.core.domain.model.Period.Month(
                    java.time.YearMonth.of(2026, 6),
                )

            repository.getTransfers(accountId = null, period = period)

            val (calledAccountId, _, _) = fakeDao.getTransfersCalls.single()
            assertEquals(null, calledAccountId)
        }

    @Test
    fun `getTransfers maps note field from TransferRow to DomainTransferRow`() =
        runTest(dispatcher) {
            val occurredAt = Instant.parse("2026-06-10T12:00:00Z")
            fakeDao.transferRows =
                listOf(
                    com.kshavrin.mymoney.core.database.projection.TransferRow(
                        id = 10L,
                        fromAccountName = "Наличные",
                        toAccountName = "Карта",
                        amount = 200.0,
                        toAmount = null,
                        currencyId = 8L,
                        occurredAt = occurredAt.toEpochMilli(),
                        note = "ежемесячный перевод",
                    ),
                )
            val period =
                com.kshavrin.mymoney.core.domain.model.Period.Month(
                    java.time.YearMonth.of(2026, 6),
                )

            val rows = repository.getTransfers(accountId = null, period = period)

            assertEquals("ежемесячный перевод", rows.single().note)
        }

    @Test
    fun `getTransfers maps null note from TransferRow to null in DomainTransferRow`() =
        runTest(dispatcher) {
            val occurredAt = Instant.parse("2026-06-10T12:00:00Z")
            fakeDao.transferRows =
                listOf(
                    com.kshavrin.mymoney.core.database.projection.TransferRow(
                        id = 11L,
                        fromAccountName = "A",
                        toAccountName = "B",
                        amount = 100.0,
                        toAmount = null,
                        currencyId = 8L,
                        occurredAt = occurredAt.toEpochMilli(),
                        note = null,
                    ),
                )
            val period =
                com.kshavrin.mymoney.core.domain.model.Period.Month(
                    java.time.YearMonth.of(2026, 6),
                )

            val rows = repository.getTransfers(accountId = null, period = period)

            assertEquals(null, rows.single().note)
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
