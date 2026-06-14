package com.kshavrin.mymoney.core.domain.usecase

import com.kshavrin.mymoney.core.domain.fake.FakeRecurringTemplateRepository
import com.kshavrin.mymoney.core.domain.fake.FakeTransactionRepository
import com.kshavrin.mymoney.core.domain.model.RecurringTemplate
import com.kshavrin.mymoney.core.domain.model.TransactionKind
import com.kshavrin.mymoney.core.domain.transaction.TransactionRunner
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Covers per-template transaction atomicity:
 *  - A pass-through runner lets all existing semantics survive unchanged.
 *  - A throwing runner verifies that a failed template's writes are rolled back while
 *    already-committed templates retain their progress.
 */
class GenerateDueRecurringUseCaseAtomicityTest {
    private val now = Instant.parse("2026-06-14T08:00:00Z")

    // -------------------------------------------------------------------------
    // Fake runners
    // -------------------------------------------------------------------------

    /** Executes the block inline — models a successful transaction. */
    private val passThroughRunner =
        object : TransactionRunner {
            override suspend fun <T> runInTransaction(block: suspend () -> T): T = block()
        }

    /**
     * Throws [SimulatedCrash] on the Nth invocation (1-based).
     * Invocations before N complete normally via the pass-through path,
     * so earlier templates' writes are already durable.
     */
    private class FailOnNthCallRunner(
        private val failOnCall: Int,
    ) : TransactionRunner {
        private var callCount = 0

        override suspend fun <T> runInTransaction(block: suspend () -> T): T {
            callCount++
            if (callCount == failOnCall) throw SimulatedCrash("transaction $callCount failed")
            return block()
        }
    }

    private class SimulatedCrash(
        msg: String,
    ) : RuntimeException(msg)

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun templateRepo() = FakeRecurringTemplateRepository()

    private fun transactionRepo() = FakeTransactionRepository()

    private fun buildUseCase(
        templateRepo: FakeRecurringTemplateRepository,
        transactionRepo: FakeTransactionRepository,
        runner: TransactionRunner,
    ) = GenerateDueRecurringUseCase(
        recurringTemplateRepository = templateRepo,
        transactionRepository = transactionRepo,
        recurringScheduler = RecurringScheduler(),
        transactionRunner = runner,
        defaultDispatcher = UnconfinedTestDispatcher(),
    )

    private fun dailyTemplate(
        id: Long,
        nextRunAt: Instant,
        endsAt: Instant? = null,
        isActive: Boolean = true,
    ) = RecurringTemplate(
        id = id,
        baseKind = TransactionKind.Expense,
        amount = BigDecimal("10.00"),
        currencyId = 1L,
        accountId = 1L,
        categoryId = 10L,
        toAccountId = null,
        note = "auto",
        recurrenceKind = "daily",
        interval = 1,
        byDay = null,
        startsAt = Instant.parse("2026-01-01T00:00:00Z"),
        endsAt = endsAt,
        nextRunAt = nextRunAt,
        isActive = isActive,
    )

    // -------------------------------------------------------------------------
    // Pass-through runner — normal semantics still hold
    // -------------------------------------------------------------------------

    @Test
    fun `pass-through runner generates occurrence for single due template`() =
        runTest {
            val tRepo = templateRepo()
            val txRepo = transactionRepo()
            val template = dailyTemplate(id = 1L, nextRunAt = now.minus(1, ChronoUnit.HOURS))
            tRepo.seed(template)

            buildUseCase(tRepo, txRepo, passThroughRunner)(now)

            assertEquals(1, txRepo.upserted().size)
        }

    @Test
    fun `pass-through runner advances nextRunAt past now`() =
        runTest {
            val tRepo = templateRepo()
            val txRepo = transactionRepo()
            val template = dailyTemplate(id = 1L, nextRunAt = now.minus(1, ChronoUnit.HOURS))
            tRepo.seed(template)

            buildUseCase(tRepo, txRepo, passThroughRunner)(now)

            val advanced = tRepo.stored(1L)?.nextRunAt
            assertTrue("nextRunAt must be after now, was $advanced", advanced != null && advanced.isAfter(now))
        }

    @Test
    fun `pass-through runner processes both templates when two are due`() =
        runTest {
            val tRepo = templateRepo()
            val txRepo = transactionRepo()
            tRepo.seed(dailyTemplate(id = 1L, nextRunAt = now.minus(2, ChronoUnit.HOURS)))
            tRepo.seed(dailyTemplate(id = 2L, nextRunAt = now.minus(1, ChronoUnit.HOURS)))

            buildUseCase(tRepo, txRepo, passThroughRunner)(now)

            assertEquals(2, txRepo.upserted().size)
            assertEquals(2, tRepo.updateNextRunCalls.size)
        }

    // -------------------------------------------------------------------------
    // Single-template atomicity
    // Gherkin: A's occurrence insert fails → A's nextRunAt NOT advanced AND
    //          occurrence not written.
    // -------------------------------------------------------------------------

    @Test
    fun `when template transaction throws occurrence is not written`() =
        runTest {
            val tRepo = templateRepo()
            val txRepo = transactionRepo()
            val template = dailyTemplate(id = 1L, nextRunAt = now.minus(1, ChronoUnit.HOURS))
            tRepo.seed(template)

            try {
                buildUseCase(tRepo, txRepo, FailOnNthCallRunner(failOnCall = 1))(now)
            } catch (_: SimulatedCrash) {
                // expected — the test verifies post-crash state
            }

            assertTrue(
                "occurrence must not be written when transaction fails",
                txRepo.upserted().isEmpty(),
            )
        }

    @Test
    fun `when template transaction throws nextRunAt is not advanced`() =
        runTest {
            val tRepo = templateRepo()
            val txRepo = transactionRepo()
            val template = dailyTemplate(id = 1L, nextRunAt = now.minus(1, ChronoUnit.HOURS))
            tRepo.seed(template)

            try {
                buildUseCase(tRepo, txRepo, FailOnNthCallRunner(failOnCall = 1))(now)
            } catch (_: SimulatedCrash) {
                // expected
            }

            assertTrue(
                "updateNextRun must not be called when transaction fails",
                tRepo.updateNextRunCalls.isEmpty(),
            )
            assertEquals(
                "nextRunAt in store must remain unchanged",
                template.nextRunAt,
                tRepo.stored(1L)?.nextRunAt,
            )
        }

    // -------------------------------------------------------------------------
    // Crash mid-run (two templates)
    // Gherkin: templates A and B both due; process killed after A.
    //   → A's occurrence exists exactly once, B gets processed on retry.
    // We model "killed after A" as: runner succeeds for call 1 (A), throws for call 2 (B).
    // On retry the use case re-fetches due templates from the repo; because A's nextRunAt
    // is already advanced it won't appear as due again, so only B is processed.
    // -------------------------------------------------------------------------

    @Test
    fun `first template occurrence committed when second template transaction throws`() =
        runTest {
            val tRepo = templateRepo()
            val txRepo = transactionRepo()
            tRepo.seed(dailyTemplate(id = 1L, nextRunAt = now.minus(2, ChronoUnit.HOURS)))
            tRepo.seed(dailyTemplate(id = 2L, nextRunAt = now.minus(1, ChronoUnit.HOURS)))

            try {
                buildUseCase(tRepo, txRepo, FailOnNthCallRunner(failOnCall = 2))(now)
            } catch (_: SimulatedCrash) {
                // expected
            }

            val occurrences = txRepo.upserted()
            assertEquals(
                "template A occurrence must be committed exactly once",
                1,
                occurrences.size,
            )
        }

    @Test
    fun `first template nextRunAt advanced when second template transaction throws`() =
        runTest {
            val tRepo = templateRepo()
            val txRepo = transactionRepo()
            tRepo.seed(dailyTemplate(id = 1L, nextRunAt = now.minus(2, ChronoUnit.HOURS)))
            tRepo.seed(dailyTemplate(id = 2L, nextRunAt = now.minus(1, ChronoUnit.HOURS)))

            try {
                buildUseCase(tRepo, txRepo, FailOnNthCallRunner(failOnCall = 2))(now)
            } catch (_: SimulatedCrash) {
                // expected
            }

            val advancedA = tRepo.stored(1L)?.nextRunAt
            assertTrue(
                "template A nextRunAt must be advanced past now after its committed transaction",
                advancedA != null && advancedA.isAfter(now),
            )
        }

    @Test
    fun `second template not advanced when its transaction throws`() =
        runTest {
            val tRepo = templateRepo()
            val txRepo = transactionRepo()
            val templateB = dailyTemplate(id = 2L, nextRunAt = now.minus(1, ChronoUnit.HOURS))
            tRepo.seed(dailyTemplate(id = 1L, nextRunAt = now.minus(2, ChronoUnit.HOURS)))
            tRepo.seed(templateB)

            try {
                buildUseCase(tRepo, txRepo, FailOnNthCallRunner(failOnCall = 2))(now)
            } catch (_: SimulatedCrash) {
                // expected
            }

            assertEquals(
                "template B nextRunAt must remain at its original value",
                templateB.nextRunAt,
                tRepo.stored(2L)?.nextRunAt,
            )
        }

    @Test
    fun `retry after crash processes only uncommitted template`() =
        runTest {
            val tRepo = templateRepo()
            val txRepo = transactionRepo()
            tRepo.seed(dailyTemplate(id = 1L, nextRunAt = now.minus(2, ChronoUnit.HOURS)))
            tRepo.seed(dailyTemplate(id = 2L, nextRunAt = now.minus(1, ChronoUnit.HOURS)))

            // First run — crashes on template B (call 2)
            try {
                buildUseCase(tRepo, txRepo, FailOnNthCallRunner(failOnCall = 2))(now)
            } catch (_: SimulatedCrash) {
                // expected
            }

            val occurrencesAfterCrash = txRepo.upserted().size

            // Retry run — pass-through, simulates worker retry
            buildUseCase(tRepo, txRepo, passThroughRunner)(now)

            val totalOccurrences = txRepo.upserted().size
            assertEquals(
                "retry must add exactly one occurrence for the remaining template B",
                occurrencesAfterCrash + 1,
                totalOccurrences,
            )
            // Template A must NOT be duplicated: only one occurrence at its original due instant
            val templateAInstant = now.minus(2, ChronoUnit.HOURS)
            val aOccurrences = txRepo.upserted().filter { it.occurredAt == templateAInstant }
            assertEquals(
                "template A occurrence must exist exactly once after retry",
                1,
                aOccurrences.size,
            )
        }
}
