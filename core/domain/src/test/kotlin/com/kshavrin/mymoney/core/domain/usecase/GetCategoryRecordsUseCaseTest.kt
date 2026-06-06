package com.kshavrin.mymoney.core.domain.usecase

import com.kshavrin.mymoney.core.domain.fake.FakeAccountRepository
import com.kshavrin.mymoney.core.domain.fake.FakeCurrencyRepository
import com.kshavrin.mymoney.core.domain.fake.FakeTransactionRepository
import com.kshavrin.mymoney.core.domain.model.Account
import com.kshavrin.mymoney.core.domain.model.AccountType
import com.kshavrin.mymoney.core.domain.model.CategoryKind
import com.kshavrin.mymoney.core.domain.model.Currency
import com.kshavrin.mymoney.core.domain.model.Period
import com.kshavrin.mymoney.core.domain.model.Transaction
import com.kshavrin.mymoney.core.domain.model.TransactionKind
import com.kshavrin.mymoney.core.domain.repository.CategoryGroup
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

class GetCategoryRecordsUseCaseTest {

    private val accountId = 1L
    private val currencyId = 9L
    private val period = Period.Day(LocalDate.parse("2026-05-20"))

    private val accountRepo = FakeAccountRepository()
    private val currencyRepo = FakeCurrencyRepository()
    private val transactionRepo = FakeTransactionRepository()
    private val useCase = GetCategoryRecordsUseCase(
        accountRepository = accountRepo,
        currencyRepository = currencyRepo,
        transactionRepository = transactionRepo,
        defaultDispatcher = UnconfinedTestDispatcher(),
    )

    private val usd = Currency(
        id = currencyId,
        code = "USD",
        symbol = "$",
        name = "US Dollar",
        decimalDigits = 2,
        isActive = true,
        sortOrder = 0,
    )

    private fun group(
        categoryId: Long,
        name: String,
        kind: CategoryKind = CategoryKind.Expense,
        total: BigDecimal,
        count: Int,
    ) = CategoryGroup(
        categoryId = categoryId,
        name = name,
        iconKey = "ic_$name",
        colorHex = "#E07AAE",
        kind = kind,
        total = total,
        count = count,
    )

    private fun transaction(
        id: Long,
        categoryId: Long,
        occurredAt: Instant,
        kind: TransactionKind = TransactionKind.Expense,
        accountId: Long = this.accountId,
    ) = Transaction(
        id = id,
        kind = kind,
        amount = BigDecimal("1.00"),
        currencyId = currencyId,
        accountId = accountId,
        categoryId = categoryId,
        note = null,
        occurredAt = occurredAt,
        createdAt = occurredAt,
        updatedAt = occurredAt,
        isDeleted = false,
        toAccountId = null,
        toAmount = null,
        exchangeRate = null,
    )

    private fun seedAccountAndCurrency() {
        accountRepo.seed(account())
        currencyRepo.seed(usd)
    }

    @Test
    fun `preserves SQL total-desc order from the grouped query`() = runTest {
        seedAccountAndCurrency()
        transactionRepo.seedCategoryGroups(
            group(categoryId = 10L, name = "Bills", total = BigDecimal("50.00"), count = 1),
            group(categoryId = 11L, name = "Salary", kind = CategoryKind.Income, total = BigDecimal("30.00"), count = 1),
            group(categoryId = 12L, name = "Food", total = BigDecimal("10.00"), count = 2),
        )

        val result = useCase(accountId, period)

        assertEquals(listOf(10L, 11L, 12L), result.map { it.categoryId })
    }

    @Test
    fun `maps header fields straight from the grouped query`() = runTest {
        seedAccountAndCurrency()
        transactionRepo.seedCategoryGroups(
            group(categoryId = 11L, name = "Salary", kind = CategoryKind.Income, total = BigDecimal("30.00"), count = 4),
        )

        val salary = useCase(accountId, period).single()

        assertEquals(11L, salary.categoryId)
        assertEquals("Salary", salary.name)
        assertEquals("ic_Salary", salary.iconKey)
        assertEquals("#E07AAE", salary.colorHex)
        assertEquals(CategoryKind.Income, salary.kind)
        assertEquals(4, salary.count)
    }

    @Test
    fun `wraps the SQL total in Money with the account currency`() = runTest {
        seedAccountAndCurrency()
        transactionRepo.seedCategoryGroups(
            group(categoryId = 10L, name = "Bills", total = BigDecimal("50.00"), count = 1),
        )

        val bills = useCase(accountId, period).single()

        assertEquals(usd, bills.total.currency)
        assertEquals(0, BigDecimal("50.00").compareTo(bills.total.amount))
    }

    @Test
    fun `buckets period transactions under their category group`() = runTest {
        seedAccountAndCurrency()
        transactionRepo.seedCategoryGroups(
            group(categoryId = 10L, name = "Bills", total = BigDecimal("50.00"), count = 1),
            group(categoryId = 12L, name = "Food", total = BigDecimal("10.00"), count = 2),
        )
        transactionRepo.seedPeriodTransactions(
            transaction(id = 1L, categoryId = 12L, occurredAt = Instant.parse("2026-05-20T08:00:00Z")),
            transaction(id = 2L, categoryId = 10L, occurredAt = Instant.parse("2026-05-20T09:00:00Z")),
            transaction(id = 3L, categoryId = 12L, occurredAt = Instant.parse("2026-05-20T10:00:00Z")),
        )

        val result = useCase(accountId, period)

        val bills = result.first { it.categoryId == 10L }
        val food = result.first { it.categoryId == 12L }
        assertEquals(listOf(2L), bills.transactions.map { it.id })
        assertEquals(setOf(1L, 3L), food.transactions.map { it.id }.toSet())
    }

    @Test
    fun `orders bucketed transactions by occurredAt descending`() = runTest {
        seedAccountAndCurrency()
        transactionRepo.seedCategoryGroups(
            group(categoryId = 12L, name = "Food", total = BigDecimal("10.00"), count = 3),
        )
        transactionRepo.seedPeriodTransactions(
            transaction(id = 1L, categoryId = 12L, occurredAt = Instant.parse("2026-05-20T08:00:00Z")),
            transaction(id = 2L, categoryId = 12L, occurredAt = Instant.parse("2026-05-20T12:00:00Z")),
            transaction(id = 3L, categoryId = 12L, occurredAt = Instant.parse("2026-05-20T10:00:00Z")),
        )

        val food = useCase(accountId, period).single()

        assertEquals(listOf(2L, 3L, 1L), food.transactions.map { it.id })
    }

    @Test
    fun `returns empty list when the grouped query is empty`() = runTest {
        seedAccountAndCurrency()
        transactionRepo.seedCategoryGroups()
        transactionRepo.seedPeriodTransactions(
            transaction(id = 1L, categoryId = 12L, occurredAt = Instant.parse("2026-05-20T08:00:00Z")),
        )

        val result = useCase(accountId, period)

        assertTrue("expected empty result but got $result", result.isEmpty())
    }

    @Test
    fun `category filter returns only the matching category group and transactions`() = runTest {
        seedAccountAndCurrency()
        transactionRepo.seedCategoryGroups(
            group(categoryId = 10L, name = "Bills", total = BigDecimal("50.00"), count = 1),
            group(categoryId = 12L, name = "Food", total = BigDecimal("10.00"), count = 2),
        )
        transactionRepo.seedPeriodTransactions(
            transaction(id = 1L, categoryId = 10L, occurredAt = Instant.parse("2026-05-20T08:00:00Z")),
            transaction(id = 2L, categoryId = 12L, occurredAt = Instant.parse("2026-05-20T10:00:00Z")),
            transaction(id = 3L, categoryId = 12L, occurredAt = Instant.parse("2026-05-20T11:00:00Z")),
        )

        val result = useCase(accountId, period, categoryId = 12L)

        assertEquals(listOf(12L), result.map { it.categoryId })
        assertEquals(listOf(3L, 2L), result.single().transactions.map { it.id })
    }

    @Test
    fun `groups with no matching transactions get an empty bucket`() = runTest {
        seedAccountAndCurrency()
        transactionRepo.seedCategoryGroups(
            group(categoryId = 10L, name = "Bills", total = BigDecimal("50.00"), count = 1),
        )
        transactionRepo.seedPeriodTransactions()

        val bills = useCase(accountId, period).single()

        assertTrue(bills.transactions.isEmpty())
    }

    @Test
    fun `forAccounts aggregates same-currency groups and excludes archived accounts`() = runTest {
        val cardAccount = account(id = 2L, name = "Card", currencyId = currencyId)
        val archivedAccount = account(id = 3L, name = "Archived", currencyId = currencyId, isArchived = true)
        accountRepo.seed(account(), cardAccount, archivedAccount)
        currencyRepo.seed(usd)
        transactionRepo.seedCategoryGroups(
            accountId,
            period,
            group(categoryId = 10L, name = "Bills", total = BigDecimal("50.00"), count = 1),
            group(categoryId = 12L, name = "Food", total = BigDecimal("10.00"), count = 2),
        )
        transactionRepo.seedCategoryGroups(
            cardAccount.id,
            period,
            group(categoryId = 10L, name = "Bills", total = BigDecimal("20.00"), count = 1),
            group(categoryId = 13L, name = "Travel", total = BigDecimal("5.00"), count = 1),
        )
        transactionRepo.seedCategoryGroups(
            archivedAccount.id,
            period,
            group(categoryId = 10L, name = "Bills", total = BigDecimal("999.00"), count = 1),
        )
        transactionRepo.seedPeriodTransactions(
            accountId,
            period,
            transaction(id = 1L, categoryId = 10L, occurredAt = Instant.parse("2026-05-20T08:00:00Z")),
            transaction(id = 2L, categoryId = 12L, occurredAt = Instant.parse("2026-05-20T10:00:00Z")),
        )
        transactionRepo.seedPeriodTransactions(
            cardAccount.id,
            period,
            transaction(id = 3L, categoryId = 10L, occurredAt = Instant.parse("2026-05-20T09:00:00Z"), accountId = cardAccount.id),
            transaction(id = 4L, categoryId = 13L, occurredAt = Instant.parse("2026-05-20T11:00:00Z"), accountId = cardAccount.id),
        )
        transactionRepo.seedPeriodTransactions(
            archivedAccount.id,
            period,
            transaction(id = 5L, categoryId = 10L, occurredAt = Instant.parse("2026-05-20T12:00:00Z"), accountId = archivedAccount.id),
        )

        val result = useCase.forAccounts(listOf(account(), cardAccount, archivedAccount), usd, period)

        assertEquals(setOf(10L, 12L, 13L), result.map { it.categoryId }.toSet())
        assertEquals(0, BigDecimal("70.00").compareTo(result.single { it.categoryId == 10L }.total.amount))
        assertEquals(listOf(3L, 1L), result.single { it.categoryId == 10L }.transactions.map { it.id })
        assertEquals(usd, result.single { it.categoryId == 10L }.total.currency)
    }

    @Test
    fun `forAccounts rejects mixed-currency selections`() = runTest {
        val eur = Currency(
            id = 10L,
            code = "EUR",
            symbol = "EUR",
            name = "Euro",
            decimalDigits = 2,
            isActive = true,
            sortOrder = 1,
        )
        val euroAccount = account(id = 2L, name = "Euro", currencyId = eur.id)
        accountRepo.seed(account(), euroAccount)
        currencyRepo.seed(usd, eur)

        assertThrows(IllegalArgumentException::class.java) {
            kotlinx.coroutines.runBlocking {
                useCase.forAccounts(listOf(account(), euroAccount), usd, period)
            }
        }
    }

    @Test
    fun `forAccounts category filter keeps only the requested aggregate category`() = runTest {
        val cardAccount = account(id = 2L, name = "Card", currencyId = currencyId)
        accountRepo.seed(account(), cardAccount)
        currencyRepo.seed(usd)
        transactionRepo.seedCategoryGroups(
            accountId,
            period,
            group(categoryId = 10L, name = "Bills", total = BigDecimal("50.00"), count = 1),
            group(categoryId = 12L, name = "Food", total = BigDecimal("10.00"), count = 2),
        )
        transactionRepo.seedCategoryGroups(
            cardAccount.id,
            period,
            group(categoryId = 12L, name = "Food", total = BigDecimal("5.00"), count = 1),
        )
        transactionRepo.seedPeriodTransactions(
            accountId,
            period,
            transaction(id = 1L, categoryId = 10L, occurredAt = Instant.parse("2026-05-20T08:00:00Z")),
            transaction(id = 2L, categoryId = 12L, occurredAt = Instant.parse("2026-05-20T10:00:00Z")),
        )
        transactionRepo.seedPeriodTransactions(
            cardAccount.id,
            period,
            transaction(id = 3L, categoryId = 12L, occurredAt = Instant.parse("2026-05-20T11:00:00Z"), accountId = cardAccount.id),
        )

        val result = useCase.forAccounts(listOf(account(), cardAccount), usd, period, categoryId = 12L)

        assertEquals(listOf(12L), result.map { it.categoryId })
        assertEquals(0, BigDecimal("15.00").compareTo(result.single().total.amount))
        assertEquals(listOf(3L, 2L), result.single().transactions.map { it.id })
    }

    private fun account(
        id: Long = accountId,
        name: String = "Cash",
        currencyId: Long = this.currencyId,
        isArchived: Boolean = false,
    ) = Account(
        id = id,
        name = name,
        currencyId = currencyId,
        initialBalance = BigDecimal.ZERO,
        type = AccountType.Cash,
        colorHex = "#7AC794",
        iconKey = "ic_cash",
        isDefault = id == accountId,
        sortOrder = 0,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
        isArchived = isArchived,
    )
}
