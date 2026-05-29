package com.kshavrin.mymoney.feature.transaction.expense

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.kshavrin.mymoney.core.designsystem.keypad.Operator
import com.kshavrin.mymoney.core.domain.model.Account
import com.kshavrin.mymoney.core.domain.model.AccountType
import com.kshavrin.mymoney.core.domain.model.Category
import com.kshavrin.mymoney.core.domain.model.CategoryKind
import com.kshavrin.mymoney.core.domain.model.Currency
import com.kshavrin.mymoney.core.domain.model.TransactionKind
import com.kshavrin.mymoney.feature.transaction.R
import com.kshavrin.mymoney.feature.transaction.fake.FakeAccountRepository
import com.kshavrin.mymoney.feature.transaction.fake.FakeAppSettingsRepository
import com.kshavrin.mymoney.feature.transaction.fake.FakeCategoryRepository
import com.kshavrin.mymoney.feature.transaction.fake.FakeCurrencyRepository
import com.kshavrin.mymoney.feature.transaction.fake.FakeTransactionRepository
import com.kshavrin.mymoney.feature.transaction.util.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

class AddExpenseViewModelTest {

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

    private fun expenseCategory(id: Long, name: String) = Category(
        id = id,
        name = name,
        kind = CategoryKind.Expense,
        iconKey = "ic_cat_${name.lowercase()}",
        colorHex = "#FF8888",
        sortOrder = 0,
        isDefault = false,
        isArchived = false,
        createdAt = now,
    )

    private fun incomeCategory(id: Long, name: String) = Category(
        id = id,
        name = name,
        kind = CategoryKind.Income,
        iconKey = "ic_cat_${name.lowercase()}",
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
        categoryRepo.seed(
            expenseCategory(id = 10L, name = "Food"),
            expenseCategory(id = 11L, name = "Bills"),
            incomeCategory(id = 20L, name = "Salary"),
            incomeCategory(id = 21L, name = "Gifts"),
        )
    }

    private fun buildViewModel(): AddExpenseViewModel = AddExpenseViewModel(
        transactionRepository = transactionRepo,
        accountRepository = accountRepo,
        currencyRepository = currencyRepo,
        categoryRepository = categoryRepo,
        appSettingsRepository = settingsRepo,
        savedStateHandle = savedStateHandle,
    )

    @Test
    fun `initial state has amountInput 0 and zero amount with no pending operator`() = runTest {
        val viewModel = buildViewModel()

        val state = viewModel.state.value
        assertEquals("0", state.amountInput)
        assertEquals(0, BigDecimal.ZERO.compareTo(state.amount))
        assertNull(state.pendingOperator)
        assertEquals("", state.expression)
    }

    @Test
    fun `KeypadDigit 5 sets amountInput to 5 and amount to 5`() = runTest {
        val viewModel = buildViewModel()

        viewModel.onEvent(AddExpenseEvent.KeypadDigit(5))

        val state = viewModel.state.value
        assertEquals("5", state.amountInput)
        assertEquals(0, BigDecimal("5").compareTo(state.amount))
    }

    @Test
    fun `BR-8 chain 1 2 plus 3 equals yields amountInput 15`() = runTest {
        val viewModel = buildViewModel()

        viewModel.onEvent(AddExpenseEvent.KeypadDigit(1))
        viewModel.onEvent(AddExpenseEvent.KeypadDigit(2))
        viewModel.onEvent(AddExpenseEvent.KeypadOperator(Operator.Plus))
        viewModel.onEvent(AddExpenseEvent.KeypadDigit(3))
        viewModel.onEvent(AddExpenseEvent.KeypadEquals)

        val state = viewModel.state.value
        assertTrue(
            "expected amountInput to contain 15 but was ${state.amountInput}",
            state.amountInput.contains("15"),
        )
        assertEquals(0, BigDecimal("15").compareTo(state.amount))
    }

    @Test
    fun `KeypadDot after Digit 1 sets amountInput to 1 dot`() = runTest {
        val viewModel = buildViewModel()

        viewModel.onEvent(AddExpenseEvent.KeypadDigit(1))
        viewModel.onEvent(AddExpenseEvent.KeypadDot)

        assertEquals("1.", viewModel.state.value.amountInput)
    }

    @Test
    fun `two operators in a row replace the pending operator`() = runTest {
        val viewModel = buildViewModel()

        viewModel.onEvent(AddExpenseEvent.KeypadDigit(5))
        viewModel.onEvent(AddExpenseEvent.KeypadOperator(Operator.Plus))
        viewModel.onEvent(AddExpenseEvent.KeypadOperator(Operator.Minus))

        assertEquals(Operator.Minus, viewModel.state.value.pendingOperator)
    }

    @Test
    fun `KeypadBackspace on initial zero state leaves state unchanged`() = runTest {
        val viewModel = buildViewModel()
        val before = viewModel.state.value

        viewModel.onEvent(AddExpenseEvent.KeypadBackspace)

        val after = viewModel.state.value
        assertEquals(before.amountInput, after.amountInput)
        assertEquals(0, before.amount.compareTo(after.amount))
        assertEquals(before.pendingOperator, after.pendingOperator)
        assertEquals(before.expression, after.expression)
    }

    @Test
    fun `ChooseCategoryClicked with zero amount sets error banner and emits no navigation`() = runTest {
        val viewModel = buildViewModel()

        viewModel.actions.test {
            viewModel.onEvent(AddExpenseEvent.ChooseCategoryClicked)

            val state = viewModel.state.value
            assertEquals(R.string.error_enter_amount_first, state.errorBannerRes)
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `ChooseCategoryClicked with positive amount emits NavigateToCategoryPicker EXPENSE and no error banner`() = runTest {
        val viewModel = buildViewModel()
        viewModel.onEvent(AddExpenseEvent.KeypadDigit(5))

        viewModel.actions.test {
            viewModel.onEvent(AddExpenseEvent.ChooseCategoryClicked)

            val action = awaitItem()
            assertTrue(
                "expected NavigateToCategoryPicker(Expense) but was $action",
                action is AddExpenseAction.NavigateToCategoryPicker &&
                    action.kind == TransactionKind.Expense,
            )
            assertNull(viewModel.state.value.errorBannerRes)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `CategoryPicked event saves expense transaction with picked categoryId and matching amount`() = runTest {
        val viewModel = buildViewModel()
        viewModel.onEvent(AddExpenseEvent.KeypadDigit(7))

        viewModel.onEvent(AddExpenseEvent.CategoryPicked(10L))

        assertEquals(1, transactionRepo.upserted.size)
        val saved = transactionRepo.upserted.single()
        assertEquals(TransactionKind.Expense, saved.kind)
        assertEquals(0, BigDecimal("7").compareTo(saved.amount))
        assertEquals(10L, saved.categoryId)
        assertEquals(cashAccount.id, saved.accountId)
        assertEquals(usd.id, saved.currencyId)
        assertNotNull(viewModel.state.value.category)
        assertEquals(10L, viewModel.state.value.category?.id)
    }

    @Test
    fun `SwapMode emits NavigateToIncomeForm`() = runTest {
        val viewModel = buildViewModel()

        viewModel.actions.test {
            viewModel.onEvent(AddExpenseEvent.SwapMode)

            assertEquals(AddExpenseAction.NavigateToIncomeForm, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `NoteChanged sets note text in state`() = runTest {
        val viewModel = buildViewModel()

        viewModel.onEvent(AddExpenseEvent.NoteChanged("test"))

        assertEquals("test", viewModel.state.value.note)
    }

    @Test
    fun `DismissError clears errorBannerRes`() = runTest {
        val viewModel = buildViewModel()
        viewModel.onEvent(AddExpenseEvent.ChooseCategoryClicked)
        assertEquals(R.string.error_enter_amount_first, viewModel.state.value.errorBannerRes)

        viewModel.onEvent(AddExpenseEvent.DismissError)

        assertNull(viewModel.state.value.errorBannerRes)
    }
}
