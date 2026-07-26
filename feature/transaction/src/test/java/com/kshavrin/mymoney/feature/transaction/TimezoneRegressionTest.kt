package com.kshavrin.mymoney.feature.transaction

import androidx.lifecycle.SavedStateHandle
import com.kshavrin.mymoney.core.datastore.model.AppSettings
import com.kshavrin.mymoney.core.domain.model.Account
import com.kshavrin.mymoney.core.domain.model.AccountType
import com.kshavrin.mymoney.core.domain.model.Category
import com.kshavrin.mymoney.core.domain.model.CategoryKind
import com.kshavrin.mymoney.core.domain.model.Currency
import com.kshavrin.mymoney.core.domain.model.Period
import com.kshavrin.mymoney.core.domain.time.PeriodArithmetic
import com.kshavrin.mymoney.core.testing.FixedTimeZoneRule
import com.kshavrin.mymoney.core.testing.fake.FakeAppSettingsRepository
import com.kshavrin.mymoney.core.testing.fake.FakeCurrencyRepository
import com.kshavrin.mymoney.feature.transaction.expense.AddExpenseEvent
import com.kshavrin.mymoney.feature.transaction.expense.AddExpenseViewModel
import com.kshavrin.mymoney.feature.transaction.fake.FakeAccountRepository
import com.kshavrin.mymoney.feature.transaction.fake.FakeCategoryRepository
import com.kshavrin.mymoney.feature.transaction.fake.FakeTransactionRepository
import com.kshavrin.mymoney.feature.transaction.util.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class TimezoneRegressionTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val timeZoneRule = FixedTimeZoneRule(ZoneId.of("America/New_York"))

    private val now = Instant.parse("2026-05-20T10:00:00Z")

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

    private val cashAccount =
        Account(
            id = 1L,
            name = "Cash",
            currencyId = usd.id,
            initialBalance = BigDecimal.ZERO,
            type = AccountType.Cash,
            colorHex = "#7AC794",
            iconKey = "ic_acc_cash",
            isDefault = true,
            sortOrder = 0,
            createdAt = now,
            updatedAt = now,
            isArchived = false,
        )

    private val foodCategory =
        Category(
            id = 10L,
            name = "Food",
            kind = CategoryKind.Expense,
            iconKey = "ic_cat_food",
            colorHex = "#FF8888",
            textColor = "#FFFFFF",
            sortOrder = 0,
            isDefault = false,
            isArchived = false,
            createdAt = now,
        )

    private fun buildViewModel(
        transactionRepository: FakeTransactionRepository,
        accountRepository: FakeAccountRepository,
        currencyRepository: FakeCurrencyRepository,
        categoryRepository: FakeCategoryRepository,
        settingsRepository: FakeAppSettingsRepository,
    ): AddExpenseViewModel =
        AddExpenseViewModel(
            transactionRepository = transactionRepository,
            accountRepository = accountRepository,
            currencyRepository = currencyRepository,
            categoryRepository = categoryRepository,
            appSettingsRepository = settingsRepository,
            savedStateHandle = SavedStateHandle(),
        )

    private fun systemZone(): ZoneId = ZoneId.systemDefault()

    @Test
    fun `saving June 10 expense in New York stores local midnight and displays the same local date`() =
        runTest {
            val transactionRepository = FakeTransactionRepository()
            val viewModel =
                buildViewModel(
                    transactionRepository = transactionRepository,
                    accountRepository = FakeAccountRepository().apply { seed(cashAccount) },
                    currencyRepository = FakeCurrencyRepository().apply { seed(usd) },
                    categoryRepository = FakeCategoryRepository().apply { seed(foodCategory) },
                    settingsRepository =
                        FakeAppSettingsRepository(
                            AppSettings(defaultAccountId = cashAccount.id),
                        ),
                )
            val date = LocalDate.of(2026, 6, 10)

            viewModel.onEvent(AddExpenseEvent.KeypadDigit(7))
            viewModel.onEvent(AddExpenseEvent.DateChanged(date))
            viewModel.onEvent(AddExpenseEvent.CategoryPicked(foodCategory.id))

            val saved = transactionRepository.upserted.single()
            val range = PeriodArithmetic.toEpochMillisRange(Period.Day(date))

            assertEquals(date.atStartOfDay(systemZone()).toInstant(), saved.occurredAt)
            assertTrue(saved.occurredAt.toEpochMilli() in range)
            assertEquals(date, saved.occurredAt.atZone(systemZone()).toLocalDate())
        }

    @Test
    fun `saving June 1 expense in New York lands in June month instead of May`() =
        runTest {
            val transactionRepository = FakeTransactionRepository()
            val viewModel =
                buildViewModel(
                    transactionRepository = transactionRepository,
                    accountRepository = FakeAccountRepository().apply { seed(cashAccount) },
                    currencyRepository = FakeCurrencyRepository().apply { seed(usd) },
                    categoryRepository = FakeCategoryRepository().apply { seed(foodCategory) },
                    settingsRepository =
                        FakeAppSettingsRepository(
                            AppSettings(defaultAccountId = cashAccount.id),
                        ),
                )
            val date = LocalDate.of(2026, 6, 1)

            viewModel.onEvent(AddExpenseEvent.KeypadDigit(9))
            viewModel.onEvent(AddExpenseEvent.DateChanged(date))
            viewModel.onEvent(AddExpenseEvent.CategoryPicked(foodCategory.id))

            val savedEpochMillis =
                transactionRepository.upserted
                    .single()
                    .occurredAt
                    .toEpochMilli()
            val juneRange = PeriodArithmetic.toEpochMillisRange(Period.Month(YearMonth.of(2026, 6)))
            val mayRange = PeriodArithmetic.toEpochMillisRange(Period.Month(YearMonth.of(2026, 5)))

            assertTrue(savedEpochMillis in juneRange)
            assertFalse(savedEpochMillis in mayRange)
        }
}
