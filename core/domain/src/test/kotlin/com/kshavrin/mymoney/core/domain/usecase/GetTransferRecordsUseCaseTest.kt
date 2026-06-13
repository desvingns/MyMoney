package com.kshavrin.mymoney.core.domain.usecase

import androidx.paging.PagingData
import com.kshavrin.mymoney.core.domain.fake.FakeCurrencyRepository
import com.kshavrin.mymoney.core.domain.model.Currency
import com.kshavrin.mymoney.core.domain.model.Period
import com.kshavrin.mymoney.core.domain.model.Transaction
import com.kshavrin.mymoney.core.domain.model.TransactionKind
import com.kshavrin.mymoney.core.domain.repository.CategoryGroup
import com.kshavrin.mymoney.core.domain.repository.CategorySummary
import com.kshavrin.mymoney.core.domain.repository.TransactionRepository
import com.kshavrin.mymoney.core.domain.repository.TransferRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.YearMonth

class GetTransferRecordsUseCaseTest {
    private inner class FakeTransactionRepoWithTransfers : TransactionRepository {
        private var transfers: List<TransferRow> = emptyList()
        private val transfersByAccount = mutableMapOf<Long, List<TransferRow>>()

        var lastAccountId: Long? = -999L
        var callCount: Int = 0

        fun seedTransfers(vararg rows: TransferRow) {
            transfers = rows.toList()
        }

        fun seedTransfers(
            accountId: Long,
            vararg rows: TransferRow,
        ) {
            transfersByAccount[accountId] = rows.toList()
        }

        override suspend fun getTransfers(
            accountId: Long?,
            period: Period,
        ): List<TransferRow> {
            lastAccountId = accountId
            callCount++
            return accountId?.let { transfersByAccount[it] } ?: transfers
        }

        override fun observeRecent(limit: Int): Flow<List<Transaction>> = flowOf(emptyList())

        override fun observeAll(): Flow<List<Transaction>> = flowOf(emptyList())

        override fun paged(
            accountId: Long,
            categoryId: Long?,
            from: Instant,
            to: Instant,
        ): Flow<PagingData<Transaction>> =
            flowOf(PagingData.empty())

        override suspend fun findById(id: Long): Transaction? = null

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

        override suspend fun upsert(transaction: Transaction): Long = transaction.id

        override suspend fun softDelete(
            id: Long,
            now: Instant,
        ) = Unit

        override suspend fun restore(
            id: Long,
            now: Instant,
        ) = Unit

        override suspend fun pruneDeleted(before: Instant) = Unit

        override suspend fun countByAccount(id: Long): Int = 0

        override suspend fun countByCategory(id: Long): Int = 0

        override suspend fun countByCurrency(id: Long): Int = 0
    }

    private val usd =
        Currency(
            id = 1L,
            code = "USD",
            symbol = "$",
            name = "US Dollar",
            decimalDigits = 2,
            isActive = true,
            sortOrder = 0,
        )

    private val period = Period.Month(YearMonth.of(2026, 6))

    private lateinit var fakeTransactionRepo: FakeTransactionRepoWithTransfers
    private lateinit var fakeCurrencyRepo: FakeCurrencyRepository
    private lateinit var useCase: GetTransferRecordsUseCase

    @Before
    fun setUp() {
        fakeTransactionRepo = FakeTransactionRepoWithTransfers()
        fakeCurrencyRepo = FakeCurrencyRepository()
        useCase =
            GetTransferRecordsUseCase(
                currencyRepository = fakeCurrencyRepo,
                transactionRepository = fakeTransactionRepo,
                defaultDispatcher = Dispatchers.Unconfined,
            )
    }

    @Test
    fun `returns empty list when repository has no transfers`() =
        runTest {
            fakeCurrencyRepo.seed(usd)

            val result = useCase(accountId = null, period = period)

            assertTrue(result.isEmpty())
        }

    @Test
    fun `maps transfer row to TransferRecord with correct account names and amount`() =
        runTest {
            val occurredAt = Instant.parse("2026-06-10T12:00:00Z")
            fakeCurrencyRepo.seed(usd)
            fakeTransactionRepo.seedTransfers(
                TransferRow(
                    id = 42L,
                    fromAccountName = "Наличные",
                    toAccountName = "Карта",
                    amount = BigDecimal("500.00"),
                    toAmount = null,
                    currencyId = usd.id,
                    occurredAt = occurredAt,
                ),
            )

            val result = useCase(accountId = null, period = period)

            assertEquals(1, result.size)
            val record = result.single()
            assertEquals(42L, record.id)
            assertEquals("Наличные", record.fromAccountName)
            assertEquals("Карта", record.toAccountName)
            assertEquals(0, BigDecimal("500.00").compareTo(record.amount.amount))
            assertEquals(usd, record.amount.currency)
            assertNull(record.toAmount)
            assertEquals(occurredAt, record.occurredAt)
        }

    @Test
    fun `maps toAmount when present`() =
        runTest {
            fakeCurrencyRepo.seed(usd)
            fakeTransactionRepo.seedTransfers(
                TransferRow(
                    id = 7L,
                    fromAccountName = "Cash",
                    toAccountName = "Card",
                    amount = BigDecimal("100.00"),
                    toAmount = BigDecimal("92.50"),
                    currencyId = usd.id,
                    occurredAt = Instant.parse("2026-06-05T09:00:00Z"),
                ),
            )

            val result = useCase(accountId = null, period = period)

            val record = result.single()
            assertEquals(0, BigDecimal("100.00").compareTo(record.amount.amount))
            assertEquals(0, BigDecimal("92.50").compareTo(record.toAmount!!.amount))
            assertEquals(usd, record.toAmount!!.currency)
        }

    @Test
    fun `returns multiple transfers preserving repository order`() =
        runTest {
            fakeCurrencyRepo.seed(usd)
            fakeTransactionRepo.seedTransfers(
                TransferRow(
                    id = 1L,
                    fromAccountName = "A",
                    toAccountName = "B",
                    amount = BigDecimal("10.00"),
                    toAmount = null,
                    currencyId = usd.id,
                    occurredAt = Instant.parse("2026-06-10T10:00:00Z"),
                ),
                TransferRow(
                    id = 2L,
                    fromAccountName = "B",
                    toAccountName = "C",
                    amount = BigDecimal("20.00"),
                    toAmount = null,
                    currencyId = usd.id,
                    occurredAt = Instant.parse("2026-06-09T10:00:00Z"),
                ),
            )

            val result = useCase(accountId = null, period = period)

            assertEquals(listOf(1L, 2L), result.map { it.id })
        }

    @Test
    fun `forwards accountId filter to repository`() =
        runTest {
            fakeCurrencyRepo.seed(usd)
            fakeTransactionRepo.seedTransfers(
                accountId = 7L,
                TransferRow(
                    id = 10L,
                    fromAccountName = "Cash",
                    toAccountName = "Card",
                    amount = BigDecimal("300.00"),
                    toAmount = null,
                    currencyId = usd.id,
                    occurredAt = Instant.parse("2026-06-08T08:00:00Z"),
                ),
            )

            val resultFiltered = useCase(accountId = 7L, period = period)
            assertEquals(7L, fakeTransactionRepo.lastAccountId)

            val resultUnfiltered = useCase(accountId = null, period = period)

            assertEquals(listOf(10L), resultFiltered.map { it.id })
            assertTrue(resultUnfiltered.isEmpty())
        }

    @Test
    fun `throws IllegalStateException when currency referenced by transfer row is not found`() =
        runTest {
            fakeTransactionRepo.seedTransfers(
                TransferRow(
                    id = 99L,
                    fromAccountName = "A",
                    toAccountName = "B",
                    amount = BigDecimal("50.00"),
                    toAmount = null,
                    currencyId = 999L,
                    occurredAt = Instant.parse("2026-06-01T00:00:00Z"),
                ),
            )

            var caught: IllegalStateException? = null
            try {
                useCase(accountId = null, period = period)
            } catch (e: IllegalStateException) {
                caught = e
            }
            assertTrue("expected IllegalStateException for missing currency", caught != null)
        }
}
