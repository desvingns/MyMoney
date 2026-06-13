package com.kshavrin.mymoney.core.sync.worker

import androidx.hilt.work.HiltWorkerFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.kshavrin.mymoney.core.datastore.AppSettingsRepository
import com.kshavrin.mymoney.core.domain.model.RecurringTemplate
import com.kshavrin.mymoney.core.domain.model.Transaction
import com.kshavrin.mymoney.core.domain.model.TransactionKind
import com.kshavrin.mymoney.core.domain.repository.AccountRepository
import com.kshavrin.mymoney.core.domain.repository.CategoryRepository
import com.kshavrin.mymoney.core.domain.repository.RecurringTemplateRepository
import com.kshavrin.mymoney.core.domain.repository.TransactionRepository
import com.kshavrin.mymoney.core.domain.seed.InitialDataSeeder
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/**
 * On-device WorkManager instrumentation for the four daily workers (TDD §11). Each worker is built
 * with its real Hilt dependencies via [HiltWorkerFactory] + [TestListenableWorkerBuilder] against a
 * fresh in-memory database, then driven through doWork() and asserted on Result + Room side effect.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class WorkerInstrumentationTest {
    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject lateinit var workerFactory: HiltWorkerFactory

    @Inject lateinit var seeder: InitialDataSeeder

    @Inject lateinit var transactionRepository: TransactionRepository

    @Inject lateinit var recurringTemplateRepository: RecurringTemplateRepository

    @Inject lateinit var accountRepository: AccountRepository

    @Inject lateinit var categoryRepository: CategoryRepository

    @Inject lateinit var appSettingsRepository: AppSettingsRepository

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @Test
    fun recurringWorkerGeneratesDueOccurrenceSilently() =
        runBlocking {
            seeder.seedIfNeeded(Instant.now())
            val cash = accountRepository.observeActive().first().first()
            val category = categoryRepository.observeAll().first().first { it.kind.name == "Expense" }
            val now = Instant.now()
            recurringTemplateRepository.upsert(
                RecurringTemplate(
                    id = 0L,
                    baseKind = TransactionKind.Expense,
                    amount = BigDecimal("5"),
                    currencyId = cash.currencyId,
                    accountId = cash.id,
                    categoryId = category.id,
                    toAccountId = null,
                    note = null,
                    recurrenceKind = "monthly",
                    interval = 1,
                    byDay = null,
                    startsAt = now.minus(2, ChronoUnit.DAYS),
                    endsAt = now,
                    nextRunAt = now.minus(1, ChronoUnit.DAYS),
                    isActive = true,
                ),
            )
            assertEquals(0, transactionRepository.observeAll().first().size)

            val worker =
                TestListenableWorkerBuilder<RecurringWorker>(context)
                    .setWorkerFactory(workerFactory)
                    .build()
            assertEquals(ListenableWorker.Result.success(), worker.doWork())

            val txns = transactionRepository.observeAll().first()
            assertEquals(1, txns.size)
            assertEquals(category.id, txns.single().categoryId)
        }

    @Test
    fun pruneDeletedWorkerRemovesOldDeletedButKeepsRecent() =
        runBlocking {
            seeder.seedIfNeeded(Instant.now())
            val cash = accountRepository.observeActive().first().first()
            val category = categoryRepository.observeAll().first().first { it.kind.name == "Expense" }
            val now = Instant.now()
            val oldId =
                transactionRepository.upsert(
                    deletedTransaction(cash.id, category.id, cash.currencyId, updatedAt = now.minus(40, ChronoUnit.DAYS)),
                )
            val recentId =
                transactionRepository.upsert(
                    deletedTransaction(cash.id, category.id, cash.currencyId, updatedAt = now),
                )

            val worker =
                TestListenableWorkerBuilder<PruneDeletedWorker>(context)
                    .setWorkerFactory(workerFactory)
                    .build()
            assertEquals(ListenableWorker.Result.success(), worker.doWork())

            assertNull("old soft-deleted row should be pruned", transactionRepository.findById(oldId))
            assertNotNull("recent soft-deleted row should survive", transactionRepository.findById(recentId))
        }

    @Test
    fun syncWorkerIsNoOpSuccessWhenAutoSyncDisabled() =
        runBlocking {
            appSettingsRepository.update { it.copy(autoSyncEnabled = false) }

            val worker =
                TestListenableWorkerBuilder<SyncWorker>(context)
                    .setWorkerFactory(workerFactory)
                    .build()

            assertEquals(ListenableWorker.Result.success(), worker.doWork())
        }

    @Test
    fun backupRotationWorkerFailsWhenTreeUriMissing() =
        runBlocking {
            val worker =
                TestListenableWorkerBuilder<BackupRotationWorker>(context)
                    .setWorkerFactory(workerFactory)
                    .build()

            assertEquals(ListenableWorker.Result.failure(), worker.doWork())
        }

    private fun deletedTransaction(
        accountId: Long,
        categoryId: Long,
        currencyId: Long,
        updatedAt: Instant,
    ): Transaction =
        Transaction(
            id = 0L,
            kind = TransactionKind.Expense,
            amount = BigDecimal("3"),
            currencyId = currencyId,
            accountId = accountId,
            categoryId = categoryId,
            note = null,
            occurredAt = updatedAt,
            createdAt = updatedAt,
            updatedAt = updatedAt,
            isDeleted = true,
            toAccountId = null,
            toAmount = null,
            exchangeRate = null,
        )
}
