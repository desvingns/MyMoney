package com.kshavrin.mymoney.core.domain.usecase

import androidx.paging.PagingData
import com.kshavrin.mymoney.core.domain.model.Period
import com.kshavrin.mymoney.core.domain.model.Transaction
import com.kshavrin.mymoney.core.domain.model.TransactionKind
import com.kshavrin.mymoney.core.domain.repository.CategoryGroup
import com.kshavrin.mymoney.core.domain.repository.CategorySummary
import com.kshavrin.mymoney.core.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class NormalizeLegacyUtcMidnightUseCaseTest {

    private val fixedNow = Instant.parse("2026-06-11T12:34:56Z")
    private val fixedClock = Clock.fixed(fixedNow, ZoneOffset.UTC)

    @Test
    fun `utc midnight in America_New_York shifts to local midnight of the same utc day`() = runTest {
        val repository = RecordingTransactionRepository(
            listOf(
                transaction(id = 1L, occurredAt = "2026-06-10T00:00:00Z"),
                transaction(id = 2L, occurredAt = "2026-06-10T13:37:00Z"),
            ),
        )
        val useCase = NormalizeLegacyUtcMidnightUseCase(repository)

        val shifted = useCase(zone = ZoneId.of("America/New_York"), clock = fixedClock)

        assertEquals(1, shifted)
        assertEquals(1, repository.updateCalls.size)
        assertEquals(
            Instant.parse("2026-06-10T04:00:00Z"),
            repository.updateCalls.single().updates[1L],
        )
        assertEquals(fixedNow, repository.updateCalls.single().updatedAt)
        assertTrue(2L !in repository.updateCalls.single().updates)
    }

    @Test
    fun `utc midnight in Europe_Moscow shifts backward to local midnight of the same utc day`() = runTest {
        val repository = RecordingTransactionRepository(
            listOf(transaction(id = 7L, occurredAt = "2026-06-10T00:00:00Z")),
        )
        val useCase = NormalizeLegacyUtcMidnightUseCase(repository)

        val shifted = useCase(zone = ZoneId.of("Europe/Moscow"), clock = fixedClock)

        assertEquals(1, shifted)
        assertEquals(
            Instant.parse("2026-06-09T21:00:00Z"),
            repository.updateCalls.single().updates[7L],
        )
    }

    @Test
    fun `utc zone is a no-op for utc midnight candidates`() = runTest {
        val repository = RecordingTransactionRepository(
            listOf(transaction(id = 3L, occurredAt = "2026-06-10T00:00:00Z")),
        )
        val useCase = NormalizeLegacyUtcMidnightUseCase(repository)

        val shifted = useCase(zone = ZoneOffset.UTC, clock = fixedClock)

        assertEquals(0, shifted)
        assertTrue(repository.updateCalls.isEmpty())
    }

    @Test
    fun `already local and non-midnight instants are never shifted a second time`() = runTest {
        val repository = RecordingTransactionRepository(
            listOf(
                transaction(id = 4L, occurredAt = "2026-06-10T04:00:00Z"),
                transaction(id = 5L, occurredAt = "2026-06-10T13:37:00Z"),
            ),
        )
        val useCase = NormalizeLegacyUtcMidnightUseCase(repository)

        val shifted = useCase(zone = ZoneId.of("America/New_York"), clock = fixedClock)

        assertEquals(0, shifted)
        assertTrue(repository.updateCalls.isEmpty())
    }

    private fun transaction(
        id: Long,
        occurredAt: String,
    ) = Transaction(
        id = id,
        kind = TransactionKind.Expense,
        amount = BigDecimal("12.50"),
        currencyId = 1L,
        accountId = 2L,
        categoryId = 3L,
        note = "coffee",
        occurredAt = Instant.parse(occurredAt),
        createdAt = Instant.parse("2026-06-01T08:00:00Z"),
        updatedAt = Instant.parse("2026-06-01T08:00:00Z"),
        isDeleted = false,
        toAccountId = null,
        toAmount = null,
        exchangeRate = null,
    )

    private data class UpdateCall(
        val updates: Map<Long, Instant>,
        val updatedAt: Instant,
    )

    private class RecordingTransactionRepository(
        private val normalizationCandidates: List<Transaction>,
    ) : TransactionRepository {
        val updateCalls = mutableListOf<UpdateCall>()

        override fun observeRecent(limit: Int): Flow<List<Transaction>> = flowOf(emptyList())

        override fun observeAll(): Flow<List<Transaction>> = flowOf(emptyList())

        override fun paged(
            accountId: Long,
            categoryId: Long?,
            from: Instant,
            to: Instant,
        ): Flow<PagingData<Transaction>> = flowOf(PagingData.empty())

        override suspend fun findById(id: Long): Transaction? = normalizationCandidates.firstOrNull { it.id == id }

        override suspend fun findByPeriod(accountId: Long, period: Period): List<Transaction> = emptyList()

        override suspend fun getCategorySummary(
            accountId: Long,
            period: Period,
            kind: TransactionKind,
        ): List<CategorySummary> = emptyList()

        override suspend fun getCategoryGroups(accountId: Long, period: Period): List<CategoryGroup> = emptyList()

        override suspend fun searchByNote(query: String, limit: Int): List<Transaction> = emptyList()

        override suspend fun upsert(transaction: Transaction): Long = transaction.id

        override suspend fun softDelete(id: Long, now: Instant) = Unit

        override suspend fun restore(id: Long, now: Instant) = Unit

        override suspend fun pruneDeleted(before: Instant) = Unit

        override suspend fun countByAccount(id: Long): Int = 0

        override suspend fun countByCategory(id: Long): Int = 0

        override suspend fun countByCurrency(id: Long): Int = 0

        override suspend fun listForTimezoneNormalization(): List<Transaction> = normalizationCandidates

        override suspend fun updateOccurredAts(updates: Map<Long, Instant>, updatedAt: Instant) {
            updateCalls += UpdateCall(updates = updates.toMap(), updatedAt = updatedAt)
        }
    }
}
