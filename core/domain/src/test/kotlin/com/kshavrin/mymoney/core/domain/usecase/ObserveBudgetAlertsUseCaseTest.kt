package com.kshavrin.mymoney.core.domain.usecase

import app.cash.turbine.test
import com.kshavrin.mymoney.core.domain.fake.FakeAccountRepository
import com.kshavrin.mymoney.core.domain.fake.FakeBudgetRepository
import com.kshavrin.mymoney.core.domain.fake.FakeCurrencyRepository
import com.kshavrin.mymoney.core.domain.fake.FakeTransactionRepository
import com.kshavrin.mymoney.core.domain.model.Account
import com.kshavrin.mymoney.core.domain.model.AccountType
import com.kshavrin.mymoney.core.domain.model.Budget
import com.kshavrin.mymoney.core.domain.model.Currency
import com.kshavrin.mymoney.core.domain.model.DomainEvent
import com.kshavrin.mymoney.core.domain.model.Period
import com.kshavrin.mymoney.core.domain.model.Transaction
import com.kshavrin.mymoney.core.domain.model.TransactionKind
import com.kshavrin.mymoney.core.domain.repository.CategorySummary
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.YearMonth

class ObserveBudgetAlertsUseCaseTest {
    private val period = Period.Month(YearMonth.of(2026, 5))
    private val accountId = 10L

    private val currency =
        Currency(
            id = 1L,
            code = "USD",
            symbol = "$",
            name = "US Dollar",
            decimalDigits = 2,
            isActive = true,
            sortOrder = 0,
        )

    private val account =
        Account(
            id = accountId,
            name = "Cash",
            currencyId = 1L,
            initialBalance = BigDecimal.ZERO,
            type = AccountType.Cash,
            colorHex = "#7AC794",
            iconKey = "ic_cash",
            isDefault = true,
            sortOrder = 0,
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
            isArchived = false,
        )

    private val accountRepo = FakeAccountRepository().apply { seed(account) }
    private val currencyRepo = FakeCurrencyRepository().apply { seed(currency) }
    private val transactionRepo = FakeTransactionRepository()
    private val budgetRepo = FakeBudgetRepository()

    // Both injected @DefaultDispatcher args must share runTest's TestCoroutineScheduler. The use case
    // drives BalanceCalculator.withContext + a combine().flowOn(defaultDispatcher); if either dispatcher
    // carries its own scheduler, kotlinx-coroutines-test throws "Detected use of different schedulers".
    private fun createUseCase(scheduler: TestCoroutineScheduler): ObserveBudgetAlertsUseCase {
        val dispatcher = UnconfinedTestDispatcher(scheduler)
        return ObserveBudgetAlertsUseCase(
            transactionRepository = transactionRepo,
            budgetRepository = budgetRepo,
            balanceCalculator = BalanceCalculator(accountRepo, currencyRepo, transactionRepo, dispatcher),
            budgetEvaluator = BudgetEvaluator(),
            defaultDispatcher = dispatcher,
        )
    }

    private fun categoryBudget(
        id: Long = 1L,
        categoryId: Long = 100L,
        amount: String = "100.00",
        alertThresholdPct: Int = 80,
    ) = Budget(
        id = id,
        categoryId = categoryId,
        periodKind = "month",
        periodStart = Instant.EPOCH,
        amount = BigDecimal(amount),
        currencyId = 1L,
        alertThresholdPct = alertThresholdPct,
        isActive = true,
    )

    private fun totalBudget(
        id: Long = 2L,
        amount: String = "100.00",
        alertThresholdPct: Int = 80,
    ) = Budget(
        id = id,
        categoryId = null,
        periodKind = "month",
        periodStart = Instant.EPOCH,
        amount = BigDecimal(amount),
        currencyId = 1L,
        alertThresholdPct = alertThresholdPct,
        isActive = true,
    )

    private fun expense(
        categoryId: Long,
        total: String,
    ) =
        CategorySummary(categoryId = categoryId, categoryName = "cat$categoryId", colorHex = "#000000", total = BigDecimal(total))

    private fun transaction(
        id: Long,
        amount: String,
    ) =
        Transaction(
            id = id,
            kind = TransactionKind.Expense,
            amount = BigDecimal(amount),
            currencyId = 1L,
            accountId = accountId,
            categoryId = 100L,
            note = null,
            occurredAt = Instant.parse("2026-05-15T10:00:00Z"),
            createdAt = Instant.parse("2026-05-15T10:00:00Z"),
            updatedAt = Instant.parse("2026-05-15T10:00:00Z"),
            isDeleted = false,
            toAccountId = null,
            toAmount = null,
            exchangeRate = null,
        )

    @Test
    fun `emits empty list when spending is well under the alert threshold`() =
        runTest {
            transactionRepo.seedExpenseSummary(expense(categoryId = 100L, total = "30.00"))
            budgetRepo.seed(categoryBudget(alertThresholdPct = 80))

            val useCase = createUseCase(testScheduler)
            useCase(accountId, currentMonth = { period.yearMonth }).test {
                assertEquals(emptyList<DomainEvent.BudgetAlert>(), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `emits a non-over alert when spending crosses the threshold but stays below the limit`() =
        runTest {
            transactionRepo.seedExpenseSummary(expense(categoryId = 100L, total = "85.00"))
            budgetRepo.seed(categoryBudget(id = 1L, categoryId = 100L, amount = "100.00", alertThresholdPct = 80))

            val useCase = createUseCase(testScheduler)
            useCase(accountId, currentMonth = { period.yearMonth }).test {
                val alert = awaitItem().single()
                assertEquals(DomainEvent.BudgetAlert(budgetId = 1L, categoryId = 100L, over = false), alert)
                assertNull(alert.overage)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `emits a zero overage when spending reaches the limit`() =
        runTest {
            transactionRepo.seedExpenseSummary(expense(categoryId = 100L, total = "100.00"))
            budgetRepo.seed(categoryBudget(id = 1L, categoryId = 100L, amount = "100.00", alertThresholdPct = 80))

            val useCase = createUseCase(testScheduler)
            useCase(accountId, currentMonth = { period.yearMonth }).test {
                val alert = awaitItem().single()
                assertTrue(alert.over)
                assertNotNull(alert.overage)
                assertEquals(currency, alert.overage!!.currency)
                assertEquals(0, BigDecimal.ZERO.compareTo(alert.overage!!.amount))
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `emits spent minus limit as overage in the account currency when spending exceeds the limit`() =
        runTest {
            transactionRepo.seedExpenseSummary(expense(categoryId = 100L, total = "125.50"))
            budgetRepo.seed(categoryBudget(id = 1L, categoryId = 100L, amount = "100.00", alertThresholdPct = 80))

            val useCase = createUseCase(testScheduler)
            useCase(accountId, currentMonth = { period.yearMonth }).test {
                val alert = awaitItem().single()
                assertTrue(alert.over)
                assertNotNull(alert.overage)
                assertEquals(currency, alert.overage!!.currency)
                assertEquals(0, BigDecimal("25.50").compareTo(alert.overage!!.amount))
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `evaluates a category budget against its own category spend not the whole expense`() =
        runTest {
            // Category 100 spends 30 (under its 100 budget); category 101 spends 90 (over threshold of its 100 budget).
            // A naive whole-expense evaluation would flag category 100 too — it must not.
            transactionRepo.seedExpenseSummary(
                expense(categoryId = 100L, total = "30.00"),
                expense(categoryId = 101L, total = "90.00"),
            )
            budgetRepo.seed(
                categoryBudget(id = 1L, categoryId = 100L, amount = "100.00", alertThresholdPct = 80),
                categoryBudget(id = 2L, categoryId = 101L, amount = "100.00", alertThresholdPct = 80),
            )

            val useCase = createUseCase(testScheduler)
            useCase(accountId, currentMonth = { period.yearMonth }).test {
                val alerts = awaitItem()
                assertEquals(listOf(DomainEvent.BudgetAlert(budgetId = 2L, categoryId = 101L, over = false)), alerts)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `evaluates a whole-expense budget against total expense across categories`() =
        runTest {
            // No single category is over threshold (30 + 60 = 90 of a 100 whole-expense budget at 80 pct).
            transactionRepo.seedExpenseSummary(
                expense(categoryId = 100L, total = "30.00"),
                expense(categoryId = 101L, total = "60.00"),
            )
            budgetRepo.seed(totalBudget(id = 5L, amount = "100.00", alertThresholdPct = 80))

            val useCase = createUseCase(testScheduler)
            useCase(accountId, currentMonth = { period.yearMonth }).test {
                val alerts = awaitItem()
                assertEquals(listOf(DomainEvent.BudgetAlert(budgetId = 5L, categoryId = null, over = false)), alerts)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `re-emits updated alerts when new spending pushes a budget over after a transaction change`() =
        runTest {
            transactionRepo.seedExpenseSummary(expense(categoryId = 100L, total = "85.00"))
            budgetRepo.seed(categoryBudget(id = 1L, categoryId = 100L, amount = "100.00", alertThresholdPct = 80))

            val useCase = createUseCase(testScheduler)
            useCase(accountId, currentMonth = { period.yearMonth }).test {
                assertEquals(
                    listOf(DomainEvent.BudgetAlert(budgetId = 1L, categoryId = 100L, over = false)),
                    awaitItem(),
                )

                // Spending climbs to the limit; the snapshot is recomputed from the summary on the next trigger.
                transactionRepo.seedExpenseSummary(expense(categoryId = 100L, total = "120.00"))
                transactionRepo.upsert(transaction(id = 0L, amount = "35.00"))

                val alert = awaitItem().single()
                assertTrue(alert.over)
                assertNotNull(alert.overage)
                assertEquals(currency, alert.overage!!.currency)
                assertEquals(0, BigDecimal("20.00").compareTo(alert.overage!!.amount))
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `does not emit a duplicate when a transaction change leaves the alert set unchanged`() =
        runTest {
            transactionRepo.seedExpenseSummary(expense(categoryId = 100L, total = "85.00"))
            budgetRepo.seed(categoryBudget(id = 1L, categoryId = 100L, amount = "100.00", alertThresholdPct = 80))

            val useCase = createUseCase(testScheduler)
            useCase(accountId, currentMonth = { period.yearMonth }).test {
                assertEquals(
                    listOf(DomainEvent.BudgetAlert(budgetId = 1L, categoryId = 100L, over = false)),
                    awaitItem(),
                )

                // A transaction lands but the summary (and thus the snapshot and alert set) is identical.
                transactionRepo.upsert(transaction(id = 0L, amount = "5.00"))

                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
        }
}
