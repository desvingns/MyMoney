package com.kshavrin.mymoney.feature.transaction.income

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.kshavrin.mymoney.core.domain.model.Account
import com.kshavrin.mymoney.core.domain.model.AccountType
import com.kshavrin.mymoney.core.domain.model.Category
import com.kshavrin.mymoney.core.domain.model.CategoryKind
import com.kshavrin.mymoney.core.domain.model.Currency
import com.kshavrin.mymoney.core.domain.model.TransactionKind
import com.kshavrin.mymoney.feature.transaction.fake.FakeAccountRepository
import com.kshavrin.mymoney.feature.transaction.fake.FakeAppSettingsRepository
import com.kshavrin.mymoney.feature.transaction.fake.FakeCategoryRepository
import com.kshavrin.mymoney.feature.transaction.fake.FakeCurrencyRepository
import com.kshavrin.mymoney.feature.transaction.fake.FakeTransactionRepository
import com.kshavrin.mymoney.feature.transaction.util.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

class AddIncomeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val now: Instant = Instant.parse("2026-05-20T10:00:00Z")

    private lateinit var transactionRepo: FakeTransactionRepository
    private lateinit var accountRepo: FakeAccountRepository
    private lateinit var currencyRepo: FakeCurrencyRepository
    private lateinit var categoryRepo: FakeCategoryRepository
    private lateinit var settingsRepo: FakeAppSettingsRepository
    private lateinit var savedStateHandle: SavedStateHandle

    private val usd = Currency(
        id = 1L,
        code = "USD",
        symbol = "$",
        name = "US Dollar",
        decimalDigits = 2,
        isActive = true,
        sortOrder = 0,
    )

    private val cashAccount = Account(
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

    private val salaryCategory = Category(
        id = 20L,
        name = "Salary",
        kind = CategoryKind.Income,
        iconKey = "ic_cat_salary",
        colorHex = "#88FF88",
        sortOrder = 0,
        isDefault = false,
        isArchived = false,
        createdAt = now,
    )

    @Before
    fun setUp() {
        transactionRepo = FakeTransactionRepository()
        accountRepo = FakeAccountRepository()
        currencyRepo = FakeCurrencyRepository()
        categoryRepo = FakeCategoryRepository()
        settingsRepo = FakeAppSettingsRepository()
        savedStateHandle = SavedStateHandle()

        currencyRepo.seed(usd)
        accountRepo.seed(cashAccount)
        categoryRepo.seed(salaryCategory)
    }

    private fun buildViewModel(): AddIncomeViewModel = AddIncomeViewModel(
        transactionRepository = transactionRepo,
        accountRepository = accountRepo,
        currencyRepository = currencyRepo,
        categoryRepository = categoryRepo,
        appSettingsRepository = settingsRepo,
        savedStateHandle = savedStateHandle,
    )

    @Test
    fun `CategoryPicked event saves an income transaction with picked category and matching amount`() = runTest {
        val viewModel = buildViewModel()
        viewModel.onEvent(AddIncomeEvent.KeypadDigit(9))

        viewModel.onEvent(AddIncomeEvent.CategoryPicked(salaryCategory.id))

        assertEquals(1, transactionRepo.upserted.size)
        val saved = transactionRepo.upserted.single()
        assertEquals(TransactionKind.Income, saved.kind)
        assertEquals(0, BigDecimal("9").compareTo(saved.amount))
        assertEquals(salaryCategory.id, saved.categoryId)
        assertEquals(cashAccount.id, saved.accountId)
        assertEquals(usd.id, saved.currencyId)
    }

    @Test
    fun `SwapMode emits NavigateToExpenseForm`() = runTest {
        val viewModel = buildViewModel()

        viewModel.actions.test {
            viewModel.onEvent(AddIncomeEvent.SwapMode)

            assertEquals(AddIncomeAction.NavigateToExpenseForm, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
