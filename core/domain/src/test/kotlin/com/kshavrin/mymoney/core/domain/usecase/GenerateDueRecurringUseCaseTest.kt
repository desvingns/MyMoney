package com.kshavrin.mymoney.core.domain.usecase

import com.kshavrin.mymoney.core.domain.fake.FakeRecurringTemplateRepository
import com.kshavrin.mymoney.core.domain.fake.FakeTransactionRepository
import com.kshavrin.mymoney.core.domain.model.RecurringTemplate
import com.kshavrin.mymoney.core.domain.model.TransactionKind
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.temporal.ChronoUnit

class GenerateDueRecurringUseCaseTest {
    private val now = Instant.parse("2026-05-20T10:00:00Z")

    private val templateRepo = FakeRecurringTemplateRepository()
    private val transactionRepo = FakeTransactionRepository()
    private val scheduler = RecurringScheduler()
    private val useCase =
        GenerateDueRecurringUseCase(
            recurringTemplateRepository = templateRepo,
            transactionRepository = transactionRepo,
            recurringScheduler = scheduler,
            defaultDispatcher = UnconfinedTestDispatcher(),
        )

    private fun dailyTemplate(
        id: Long = 1L,
        nextRunAt: Instant,
        endsAt: Instant? = null,
        isActive: Boolean = true,
        interval: Int = 1,
    ) = RecurringTemplate(
        id = id,
        baseKind = TransactionKind.Expense,
        amount = BigDecimal("12.50"),
        currencyId = 7L,
        accountId = 3L,
        categoryId = 100L,
        toAccountId = null,
        note = "rent",
        recurrenceKind = "daily",
        interval = interval,
        byDay = null,
        startsAt = Instant.parse("2026-05-01T00:00:00Z"),
        endsAt = endsAt,
        nextRunAt = nextRunAt,
        isActive = isActive,
    )

    // Replays the production catch-up walk through the already-tested scheduler so expected
    // run instants/counts stay correct regardless of the test machine's default time zone.
    private fun expectedRuns(template: RecurringTemplate): List<Instant> {
        val runs = mutableListOf<Instant>()
        var current = template.nextRunAt
        while (!current.isAfter(now) && (template.endsAt == null || !current.isAfter(template.endsAt))) {
            runs += current
            current = scheduler.computeNextRun(template.copy(nextRunAt = current), now)
            if (template.endsAt != null && current.isAfter(template.endsAt)) break
        }
        return runs
    }

    @Test
    fun `daily template due one hour ago generates exactly one new transaction`() =
        runTest {
            val dueInstant = now.minus(1, ChronoUnit.HOURS)
            templateRepo.seed(dailyTemplate(nextRunAt = dueInstant))

            useCase(now)

            val generated = transactionRepo.upserted()
            assertEquals(1, generated.size)
        }

    @Test
    fun `generated occurrence is a brand new row at the original due instant`() =
        runTest {
            val dueInstant = now.minus(1, ChronoUnit.HOURS)
            templateRepo.seed(dailyTemplate(nextRunAt = dueInstant))

            useCase(now)

            val tx = transactionRepo.upserted().single()
            assertTrue("expected a freshly assigned id, was ${tx.id}", tx.id > 0L)
            assertEquals(dueInstant, tx.occurredAt)
        }

    @Test
    fun `generated occurrence copies the template fields`() =
        runTest {
            val template = dailyTemplate(nextRunAt = now.minus(1, ChronoUnit.HOURS))
            templateRepo.seed(template)

            useCase(now)

            val tx = transactionRepo.upserted().single()
            assertEquals(template.baseKind, tx.kind)
            assertEquals(0, template.amount.compareTo(tx.amount))
            assertEquals(template.currencyId, tx.currencyId)
            assertEquals(template.accountId, tx.accountId)
            assertEquals(template.categoryId, tx.categoryId)
            assertEquals(template.toAccountId, tx.toAccountId)
            assertEquals(template.note, tx.note)
        }

    @Test
    fun `single due daily template advances nextRunAt past now`() =
        runTest {
            val template = dailyTemplate(nextRunAt = now.minus(1, ChronoUnit.HOURS))
            templateRepo.seed(template)

            useCase(now)

            val advanced = templateRepo.stored(template.id)?.nextRunAt
            assertNotNull(advanced)
            assertTrue("nextRunAt should be after now, was $advanced", advanced!!.isAfter(now))
            assertEquals(listOf(template.id to advanced), templateRepo.updateNextRunCalls)
        }

    @Test
    fun `single due daily template advances nextRunAt to the scheduler value`() =
        runTest {
            val template = dailyTemplate(nextRunAt = now.minus(1, ChronoUnit.HOURS))
            templateRepo.seed(template)
            val expectedNext = scheduler.computeNextRun(template, now)

            useCase(now)

            assertEquals(expectedNext, templateRepo.stored(template.id)?.nextRunAt)
        }

    @Test
    fun `template behind by several periods catches up one occurrence per missed run`() =
        runTest {
            val template = dailyTemplate(nextRunAt = now.minus(3, ChronoUnit.DAYS))
            templateRepo.seed(template)
            val expectedRuns = expectedRuns(template)

            useCase(now)

            val occurredAts = transactionRepo.upserted().map { it.occurredAt }.sorted()
            assertEquals(expectedRuns.sorted(), occurredAts)
            assertTrue("catch-up should generate more than one row", occurredAts.size > 1)
        }

    @Test
    fun `catch-up leaves nextRunAt strictly after now`() =
        runTest {
            val template = dailyTemplate(nextRunAt = now.minus(3, ChronoUnit.DAYS))
            templateRepo.seed(template)

            useCase(now)

            val advanced = templateRepo.stored(template.id)?.nextRunAt
            assertNotNull(advanced)
            assertTrue("nextRunAt should be after now, was $advanced", advanced!!.isAfter(now))
        }

    @Test
    fun `template is deactivated when the next run would pass endsAt`() =
        runTest {
            val template =
                dailyTemplate(
                    nextRunAt = now.minus(1, ChronoUnit.DAYS),
                    endsAt = now.minus(1, ChronoUnit.HOURS),
                )
            templateRepo.seed(template)

            useCase(now)

            assertEquals(listOf(template.id), templateRepo.deactivateCalls)
            assertEquals(false, templateRepo.stored(template.id)?.isActive)
        }

    @Test
    fun `deactivation generates the in-range occurrence and skips updateNextRun`() =
        runTest {
            val template =
                dailyTemplate(
                    nextRunAt = now.minus(1, ChronoUnit.DAYS),
                    endsAt = now.minus(1, ChronoUnit.HOURS),
                )
            templateRepo.seed(template)

            useCase(now)

            assertEquals(1, transactionRepo.upserted().size)
            assertTrue("a deactivated template must not also advance nextRunAt", templateRepo.updateNextRunCalls.isEmpty())
        }

    @Test
    fun `no due templates produces no writes`() =
        runTest {
            templateRepo.seed(dailyTemplate(nextRunAt = now.plus(1, ChronoUnit.DAYS)))

            useCase(now)

            assertTrue(transactionRepo.upserted().isEmpty())
            assertTrue(templateRepo.updateNextRunCalls.isEmpty())
            assertTrue(templateRepo.deactivateCalls.isEmpty())
        }

    @Test
    fun `inactive due template is skipped`() =
        runTest {
            templateRepo.seed(dailyTemplate(nextRunAt = now.minus(1, ChronoUnit.HOURS), isActive = false))

            useCase(now)

            assertTrue(transactionRepo.upserted().isEmpty())
            assertTrue(templateRepo.updateNextRunCalls.isEmpty())
            assertTrue(templateRepo.deactivateCalls.isEmpty())
        }

    @Test
    fun `processing a due template touches only the template and transaction repositories`() =
        runTest {
            val template = dailyTemplate(nextRunAt = now.minus(1, ChronoUnit.HOURS))
            templateRepo.seed(template)

            useCase(now)

            // AS-11: generation is a pure repository operation — upsert + updateNextRun only,
            // with no settings/preferences seam in the use case to interact with.
            assertEquals(1, transactionRepo.upserted().size)
            assertEquals(listOf(template.id), templateRepo.updateNextRunCalls.map { it.first })
            assertTrue(templateRepo.deactivateCalls.isEmpty())
        }
}
