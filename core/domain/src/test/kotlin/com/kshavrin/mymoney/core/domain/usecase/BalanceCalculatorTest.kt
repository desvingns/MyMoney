package com.kshavrin.mymoney.core.domain.usecase

import com.kshavrin.mymoney.core.domain.fake.FakeAccountRepository
import com.kshavrin.mymoney.core.domain.fake.FakeCurrencyRepository
import com.kshavrin.mymoney.core.domain.fake.FakeTransactionRepository
import com.kshavrin.mymoney.core.domain.model.Account
import com.kshavrin.mymoney.core.domain.model.AccountType
import com.kshavrin.mymoney.core.domain.model.Currency
import com.kshavrin.mymoney.core.domain.model.Period
import com.kshavrin.mymoney.core.domain.repository.CategorySummary
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.YearMonth

class BalanceCalculatorTest {
    private val may2026 = Period.Month(YearMonth.of(2026, 5))
    private val usd = currency(id = 1L, code = "USD", symbol = "$", name = "US Dollar")
    private val eur = currency(id = 2L, code = "EUR", symbol = "EUR", name = "Euro")

    @Test
    fun computes_per_category_aggregates() =
        runTest {
            val account = account(id = 10L, name = "Cash", currencyId = usd.id)

            val accountRepo = FakeAccountRepository().apply { seed(account) }
            val currencyRepo = FakeCurrencyRepository().apply { seed(usd) }
            val transactionRepo =
                FakeTransactionRepository().apply {
                    seedExpenseSummary(
                        CategorySummary(categoryId = 100L, categoryName = "Food", colorHex = "#E07AAE", total = BigDecimal("30.00")),
                        CategorySummary(categoryId = 101L, categoryName = "Bills", colorHex = "#C9A227", total = BigDecimal("70.00")),
                    )
                    seedIncomeSummary(
                        CategorySummary(categoryId = 200L, categoryName = "Salary", colorHex = "#7AC29A", total = BigDecimal("500.00")),
                    )
                }

            val calculator = BalanceCalculator(accountRepo, currencyRepo, transactionRepo, UnconfinedTestDispatcher())
            val snapshot = calculator(10L, may2026)

            assertEquals(BigDecimal("100.00"), snapshot.expense.amount)
            assertEquals(BigDecimal("500.00"), snapshot.income.amount)
            assertEquals(BigDecimal("400.00"), snapshot.net.amount)
            assertEquals(3, snapshot.byCategory.size)
        }

    @Test
    fun `carries iconKey from each category summary into the emitted CategoryBalance`() =
        runTest {
            val account = account(id = 10L, name = "Cash", currencyId = usd.id)

            val accountRepo = FakeAccountRepository().apply { seed(account) }
            val currencyRepo = FakeCurrencyRepository().apply { seed(usd) }
            val transactionRepo =
                FakeTransactionRepository().apply {
                    seedExpenseSummary(
                        CategorySummary(categoryId = 100L, categoryName = "Food", colorHex = "#E07AAE", total = BigDecimal("30.00"), iconKey = "food"),
                        CategorySummary(categoryId = 101L, categoryName = "Bills", colorHex = "#C9A227", total = BigDecimal("70.00"), iconKey = "bills"),
                    )
                    seedIncomeSummary(
                        CategorySummary(categoryId = 200L, categoryName = "Salary", colorHex = "#7AC29A", total = BigDecimal("500.00"), iconKey = "salary"),
                    )
                }

            val calculator = BalanceCalculator(accountRepo, currencyRepo, transactionRepo, UnconfinedTestDispatcher())
            val snapshot = calculator(10L, may2026)

            assertEquals("food", snapshot.byCategory.single { it.categoryId == 100L }.iconKey)
            assertEquals("bills", snapshot.byCategory.single { it.categoryId == 101L }.iconKey)
            assertEquals("salary", snapshot.byCategory.single { it.categoryId == 200L }.iconKey)
        }

    @Test
    fun `forAccounts aggregates only active accounts in the selected currency`() =
        runTest {
            val cash = account(id = 10L, name = "Cash", currencyId = usd.id)
            val card = account(id = 11L, name = "Card", currencyId = usd.id)
            val archived = account(id = 12L, name = "Archived", currencyId = usd.id, isArchived = true)
            val euro = account(id = 13L, name = "Euro", currencyId = eur.id)
            val accountRepo = FakeAccountRepository().apply { seed(cash, card, archived, euro) }
            val currencyRepo = FakeCurrencyRepository().apply { seed(usd, eur) }
            val transactionRepo =
                FakeTransactionRepository().apply {
                    seedExpenseSummary(cash.id, may2026, CategorySummary(100L, "Food", "#E07AAE", BigDecimal("30.00")))
                    seedIncomeSummary(cash.id, may2026, CategorySummary(200L, "Salary", "#7AC29A", BigDecimal("120.00")))
                    seedExpenseSummary(card.id, may2026, CategorySummary(100L, "Food", "#E07AAE", BigDecimal("10.00")))
                    seedIncomeSummary(card.id, may2026, CategorySummary(300L, "Bonus", "#7AC794", BigDecimal("20.00")))
                    seedExpenseSummary(archived.id, may2026, CategorySummary(100L, "Food", "#E07AAE", BigDecimal("999.00")))
                    seedIncomeSummary(euro.id, may2026, CategorySummary(400L, "FX", "#00AAFF", BigDecimal("999.00")))
                }

            val snapshot =
                BalanceCalculator(accountRepo, currencyRepo, transactionRepo, UnconfinedTestDispatcher())
                    .forAccounts(listOf(cash, card, archived), usd, may2026)

            assertEquals(0, BigDecimal("40.00").compareTo(snapshot.expense.amount))
            assertEquals(0, BigDecimal("140.00").compareTo(snapshot.income.amount))
            assertEquals(0, BigDecimal("100.00").compareTo(snapshot.net.amount))
            assertEquals(
                0,
                BigDecimal("40.00").compareTo(
                    snapshot.byCategory
                        .single { it.categoryId == 100L }
                        .total.amount,
                ),
            )
            assertEquals(setOf(100L, 200L, 300L), snapshot.byCategory.map { it.categoryId }.toSet())
        }

    @Test
    fun `forAccounts rejects mixed-currency selections`() =
        runTest {
            val cash = account(id = 10L, name = "Cash", currencyId = usd.id)
            val euro = account(id = 11L, name = "Euro", currencyId = eur.id)
            val calculator =
                BalanceCalculator(
                    accountRepository = FakeAccountRepository().apply { seed(cash, euro) },
                    currencyRepository = FakeCurrencyRepository().apply { seed(usd, eur) },
                    transactionRepository = FakeTransactionRepository(),
                    defaultDispatcher = UnconfinedTestDispatcher(),
                )

            assertThrows(IllegalArgumentException::class.java) {
                kotlinx.coroutines.runBlocking {
                    calculator.forAccounts(listOf(cash, euro), usd, may2026)
                }
            }
        }

    private fun currency(
        id: Long,
        code: String,
        symbol: String,
        name: String,
    ) = Currency(
        id = id,
        code = code,
        symbol = symbol,
        name = name,
        decimalDigits = 2,
        isActive = true,
        sortOrder = 0,
    )

    private fun account(
        id: Long,
        name: String,
        currencyId: Long,
        isArchived: Boolean = false,
    ) = Account(
        id = id,
        name = name,
        currencyId = currencyId,
        initialBalance = BigDecimal.ZERO,
        type = AccountType.Cash,
        colorHex = "#7AC794",
        iconKey = "ic_cash",
        isDefault = id == 10L,
        sortOrder = 0,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
        isArchived = isArchived,
    )
}
