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
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.YearMonth

class BalanceCalculatorTest {

    @Test
    fun computes_per_category_aggregates() = runTest {
        val currency = Currency(
            id = 1L,
            code = "USD",
            symbol = "$",
            name = "US Dollar",
            decimalDigits = 2,
            isActive = true,
            sortOrder = 0,
        )
        val account = Account(
            id = 10L,
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

        val accountRepo = FakeAccountRepository().apply { seed(account) }
        val currencyRepo = FakeCurrencyRepository().apply { seed(currency) }
        val transactionRepo = FakeTransactionRepository().apply {
            seedExpenseSummary(
                CategorySummary(categoryId = 100L, categoryName = "Food", colorHex = "#E07AAE", total = BigDecimal("30.00")),
                CategorySummary(categoryId = 101L, categoryName = "Bills", colorHex = "#C9A227", total = BigDecimal("70.00")),
            )
            seedIncomeSummary(
                CategorySummary(categoryId = 200L, categoryName = "Salary", colorHex = "#7AC29A", total = BigDecimal("500.00")),
            )
        }

        val calculator = BalanceCalculator(accountRepo, currencyRepo, transactionRepo, UnconfinedTestDispatcher())
        val snapshot = calculator(10L, Period.Month(YearMonth.of(2026, 5)))

        assertEquals(BigDecimal("100.00"), snapshot.expense.amount)
        assertEquals(BigDecimal("500.00"), snapshot.income.amount)
        assertEquals(BigDecimal("400.00"), snapshot.net.amount)
        assertEquals(3, snapshot.byCategory.size)
    }
}
