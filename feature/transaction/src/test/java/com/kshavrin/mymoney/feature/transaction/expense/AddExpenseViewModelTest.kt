package com.kshavrin.mymoney.feature.transaction.expense

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import app.cash.turbine.test
import com.kshavrin.mymoney.core.designsystem.keypad.Operator
import com.kshavrin.mymoney.core.domain.model.Account
import com.kshavrin.mymoney.core.domain.model.AccountType
import com.kshavrin.mymoney.core.domain.model.Category
import com.kshavrin.mymoney.core.domain.model.CategoryKind
import com.kshavrin.mymoney.core.domain.model.Currency
import com.kshavrin.mymoney.core.domain.model.Transaction
import com.kshavrin.mymoney.core.domain.model.TransactionKind
import com.kshavrin.mymoney.core.domain.repository.CategoryRepository
import com.kshavrin.mymoney.core.domain.repository.TransactionRepository
import com.kshavrin.mymoney.feature.transaction.R
import com.kshavrin.mymoney.feature.transaction.fake.FakeAccountRepository
import com.kshavrin.mymoney.core.testing.fake.FakeAppSettingsRepository
import com.kshavrin.mymoney.feature.transaction.fake.FakeCategoryRepository
import com.kshavrin.mymoney.core.testing.fake.FakeCurrencyRepository
import com.kshavrin.mymoney.feature.transaction.fake.FakeTransactionRepository
import com.kshavrin.mymoney.feature.transaction.util.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.TimeZone

class AddExpenseViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var originalTimeZone: TimeZone

    private val now: Instant = Instant.parse("2026-05-20T10:00:00Z")

    private lateinit var transactionRepo: FakeTransactionRepository
    private lateinit var accountRepo: FakeAccountRepository
    private lateinit var currencyRepo: FakeCurrencyRepository
    private lateinit var categoryRepo: FakeCategoryRepository
    private lateinit var settingsRepo: FakeAppSettingsRepository
    private lateinit var savedStateHandle: SavedStateHandle

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

    private fun expenseCategory(
        id: Long,
        name: String,
        sortOrder: Int = 0,
        isArchived: Boolean = false,
    ) = Category(
        id = id,
        name = name,
        kind = CategoryKind.Expense,
        iconKey = "ic_cat_${name.lowercase()}",
        colorHex = "#FF8888",
        textColor = "#FFFFFF",
        sortOrder = sortOrder,
        isDefault = false,
        isArchived = isArchived,
        createdAt = now,
    )

    private fun incomeCategory(
        id: Long,
        name: String,
    ) =
        Category(
            id = id,
            name = name,
            kind = CategoryKind.Income,
            iconKey = "ic_cat_${name.lowercase()}",
            colorHex = "#88FF88",
            textColor = "#FFFFFF",
            sortOrder = 0,
            isDefault = false,
            isArchived = false,
            createdAt = now,
        )

    @Before
    fun setUp() {
        originalTimeZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone(TEST_TIME_ZONE_ID))
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

    @After
    fun tearDown() {
        TimeZone.setDefault(originalTimeZone)
    }

    private fun buildViewModel(
        transactionRepository: TransactionRepository = transactionRepo,
        categoryRepository: CategoryRepository = categoryRepo,
    ): AddExpenseViewModel =
        AddExpenseViewModel(
            transactionRepository = transactionRepository,
            accountRepository = accountRepo,
            currencyRepository = currencyRepo,
            categoryRepository = categoryRepository,
            appSettingsRepository = settingsRepo,
            savedStateHandle = savedStateHandle,
        )

    private fun localMidnight(date: LocalDate): Instant =
        date.atStartOfDay(ZoneId.systemDefault()).toInstant()

    private fun AddExpenseViewModel.setStateForExplicitSave(
        amount: BigDecimal,
        category: Category,
    ) {
        val field = AddExpenseViewModel::class.java.getDeclaredField("_state")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val stateFlow = field.get(this) as MutableStateFlow<AddExpenseState>
        stateFlow.value =
            stateFlow.value.copy(
                amount = amount,
                amountInput = amount.toPlainString(),
                category = category,
                categoryStep = true,
                errorBannerRes = null,
            )
    }

    @Test
    fun `initial state has amountInput 0 and zero amount with no pending operator`() =
        runTest {
            val viewModel = buildViewModel()

            val state = viewModel.state.value
            assertEquals("0", state.amountInput)
            assertEquals(0, BigDecimal.ZERO.compareTo(state.amount))
            assertNull(state.pendingOperator)
            assertEquals("", state.expression)
        }

    @Test
    fun `state categories include only unarchived expense categories sorted by sortOrder`() =
        runTest {
            categoryRepo.seed(
                expenseCategory(id = 12L, name = "Cafe", sortOrder = -1),
                expenseCategory(id = 13L, name = "Old", sortOrder = -2, isArchived = true),
                incomeCategory(id = 22L, name = "Bonus"),
            )

            val viewModel = buildViewModel()

            val categories = viewModel.state.value.categories
            assertEquals(listOf(12L, 10L, 11L), categories.map { it.id })
            assertTrue(categories.all { it.kind == CategoryKind.Expense && !it.isArchived })
        }

    @Test
    fun `KeypadDigit 5 sets amountInput to 5 and amount to 5`() =
        runTest {
            val viewModel = buildViewModel()

            viewModel.onEvent(AddExpenseEvent.KeypadDigit(5))

            val state = viewModel.state.value
            assertEquals("5", state.amountInput)
            assertEquals(0, BigDecimal("5").compareTo(state.amount))
        }

    @Test
    fun `BR-8 chain 1 2 plus 3 equals yields amountInput 15`() =
        runTest {
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
    fun `KeypadDot after Digit 1 sets amountInput to 1 dot`() =
        runTest {
            val viewModel = buildViewModel()

            viewModel.onEvent(AddExpenseEvent.KeypadDigit(1))
            viewModel.onEvent(AddExpenseEvent.KeypadDot)

            assertEquals("1.", viewModel.state.value.amountInput)
        }

    @Test
    fun `two operators in a row replace the pending operator`() =
        runTest {
            val viewModel = buildViewModel()

            viewModel.onEvent(AddExpenseEvent.KeypadDigit(5))
            viewModel.onEvent(AddExpenseEvent.KeypadOperator(Operator.Plus))
            viewModel.onEvent(AddExpenseEvent.KeypadOperator(Operator.Minus))

            assertEquals(Operator.Minus, viewModel.state.value.pendingOperator)
        }

    @Test
    fun `KeypadBackspace on initial zero state leaves state unchanged`() =
        runTest {
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
    fun `SelectCategoryClicked with positive amount opens category step`() =
        runTest {
            val viewModel = buildViewModel()

            viewModel.onEvent(AddExpenseEvent.KeypadDigit(5))
            viewModel.onEvent(AddExpenseEvent.SelectCategoryClicked)

            assertTrue(viewModel.state.value.categoryStep)
            assertNull(viewModel.state.value.errorBannerRes)
        }

    @Test
    fun `SelectCategoryClicked with zero amount keeps amount step and shows amount error`() =
        runTest {
            val viewModel = buildViewModel()

            viewModel.onEvent(AddExpenseEvent.SelectCategoryClicked)

            assertFalse(viewModel.state.value.categoryStep)
            assertEquals(R.string.error_enter_amount_first, viewModel.state.value.errorBannerRes)
            assertEquals(0, transactionRepo.upserted.size)
        }

    @Test
    fun `BackToAmount leaves category step and clears error banner`() =
        runTest {
            val viewModel = buildViewModel()
            viewModel.onEvent(AddExpenseEvent.KeypadDigit(5))
            viewModel.onEvent(AddExpenseEvent.SelectCategoryClicked)
            viewModel.onEvent(AddExpenseEvent.SaveClicked)

            assertTrue(viewModel.state.value.categoryStep)
            assertEquals(R.string.error_choose_category_first, viewModel.state.value.errorBannerRes)

            viewModel.onEvent(AddExpenseEvent.BackToAmount)

            assertFalse(viewModel.state.value.categoryStep)
            assertNull(viewModel.state.value.errorBannerRes)
        }

    @Test
    fun `AddCategoryClicked emits NavigateToCreateCategory`() =
        runTest {
            val viewModel = buildViewModel()

            viewModel.actions.test {
                viewModel.onEvent(AddExpenseEvent.AddCategoryClicked)

                assertEquals(AddExpenseAction.NavigateToCreateCategory, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `CategoryPicked with zero amount returns to amount step and does not save`() =
        runTest {
            val viewModel = buildViewModel()

            viewModel.onEvent(AddExpenseEvent.CategoryPicked(10L))

            assertEquals(false, viewModel.state.value.categoryStep)
            assertEquals(R.string.error_enter_amount_first, viewModel.state.value.errorBannerRes)
            assertEquals(0, transactionRepo.upserted.size)
        }

    @Test
    fun `CategoryPicked event saves expense transaction with picked categoryId and matching amount`() =
        runTest {
            val viewModel = buildViewModel()
            val saveDate = LocalDate.parse("2026-06-10")
            viewModel.onEvent(AddExpenseEvent.KeypadDigit(7))
            viewModel.onEvent(AddExpenseEvent.DateChanged(saveDate))

            viewModel.onEvent(AddExpenseEvent.CategoryPicked(10L))

            assertEquals(1, transactionRepo.upserted.size)
            val saved = transactionRepo.upserted.single()
            assertEquals(TransactionKind.Expense, saved.kind)
            assertEquals(0, BigDecimal("7").compareTo(saved.amount))
            assertEquals(10L, saved.categoryId)
            assertEquals(cashAccount.id, saved.accountId)
            assertEquals(usd.id, saved.currencyId)
            assertEquals(localMidnight(saveDate), saved.occurredAt)
            assertNotNull(viewModel.state.value.category)
            assertEquals(
                10L,
                viewModel.state.value.category
                    ?.id,
            )
        }

    @Test
    fun `CategoryPicked emits NavigateBack and increments saved signal`() =
        runTest {
            val viewModel = buildViewModel()
            viewModel.onEvent(AddExpenseEvent.KeypadDigit(7))
            viewModel.onEvent(AddExpenseEvent.SelectCategoryClicked)

            viewModel.actions.test {
                viewModel.onEvent(AddExpenseEvent.CategoryPicked(10L))

                assertEquals(AddExpenseAction.NavigateBack, awaitItem())
                assertEquals(1L, viewModel.state.value.savedSignal)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `double SaveClicked performs one upsert and emits one NavigateBack`() =
        runTest {
            val blockingRepo = BlockingTransactionRepository()
            val viewModel = buildViewModel(transactionRepository = blockingRepo)
            advanceUntilIdle()
            viewModel.setStateForExplicitSave(amount = BigDecimal("7"), category = expenseCategory(10L, "Food"))

            viewModel.actions.test {
                viewModel.onEvent(AddExpenseEvent.SaveClicked)
                assertTrue(viewModel.state.value.isSaving)
                viewModel.onEvent(AddExpenseEvent.SaveClicked)

                assertEquals(1, blockingRepo.startedUpserts.size)

                blockingRepo.release()
                advanceUntilIdle()

                assertEquals(1, blockingRepo.persistedUpserts.size)
                assertEquals(AddExpenseAction.NavigateBack, awaitItem())
                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `double CategoryPicked performs one upsert and emits one NavigateBack`() =
        runTest {
            val blockingRepo = BlockingTransactionRepository()
            val viewModel = buildViewModel(transactionRepository = blockingRepo)
            advanceUntilIdle()
            viewModel.onEvent(AddExpenseEvent.KeypadDigit(7))
            viewModel.onEvent(AddExpenseEvent.SelectCategoryClicked)

            viewModel.actions.test {
                viewModel.onEvent(AddExpenseEvent.CategoryPicked(10L))
                assertTrue(viewModel.state.value.isSaving)
                viewModel.onEvent(AddExpenseEvent.CategoryPicked(10L))

                assertEquals(1, blockingRepo.startedUpserts.size)

                blockingRepo.release()
                advanceUntilIdle()

                assertEquals(1, blockingRepo.persistedUpserts.size)
                assertEquals(AddExpenseAction.NavigateBack, awaitItem())
                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `cancelling explicit save does not show error banner or emit navigation`() =
        runTest {
            val blockingRepo = BlockingTransactionRepository()
            val viewModel = buildViewModel(transactionRepository = blockingRepo)
            advanceUntilIdle()
            viewModel.setStateForExplicitSave(amount = BigDecimal("7"), category = expenseCategory(10L, "Food"))

            viewModel.actions.test {
                viewModel.onEvent(AddExpenseEvent.SaveClicked)
                assertTrue(viewModel.state.value.isSaving)
                assertEquals(1, blockingRepo.startedUpserts.size)

                viewModel.viewModelScope.cancel()
                advanceUntilIdle()

                assertTrue(blockingRepo.persistedUpserts.isEmpty())
                assertNull(viewModel.state.value.errorBannerRes)
                assertEquals(0L, viewModel.state.value.savedSignal)
                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `category lookup failure resets isSaving and allows a second save attempt`() =
        runTest {
            val failOnceCategoryRepo = FailOnceCategoryRepository(categoryRepo)
            val viewModel = buildViewModel(categoryRepository = failOnceCategoryRepo)
            advanceUntilIdle()
            viewModel.onEvent(AddExpenseEvent.KeypadDigit(7))

            viewModel.onEvent(AddExpenseEvent.CategoryPicked(10L))

            assertFalse(viewModel.state.value.isSaving)
            assertEquals(R.string.error_save_failed, viewModel.state.value.errorBannerRes)
            assertEquals(0, transactionRepo.upserted.size)

            viewModel.actions.test {
                viewModel.onEvent(AddExpenseEvent.CategoryPicked(10L))

                assertEquals(AddExpenseAction.NavigateBack, awaitItem())
                assertEquals(1, transactionRepo.upserted.size)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `SwapMode emits NavigateToIncomeForm`() =
        runTest {
            val viewModel = buildViewModel()

            viewModel.actions.test {
                viewModel.onEvent(AddExpenseEvent.SwapMode)

                assertEquals(AddExpenseAction.NavigateToIncomeForm, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `NoteChanged sets note text in state`() =
        runTest {
            val viewModel = buildViewModel()

            viewModel.onEvent(AddExpenseEvent.NoteChanged("test"))

            assertEquals("test", viewModel.state.value.note)
        }

    @Test
    fun `DismissError clears errorBannerRes`() =
        runTest {
            val viewModel = buildViewModel()
            viewModel.onEvent(AddExpenseEvent.SaveClicked)
            assertEquals(R.string.error_enter_amount_first, viewModel.state.value.errorBannerRes)

            viewModel.onEvent(AddExpenseEvent.DismissError)

            assertNull(viewModel.state.value.errorBannerRes)
        }

    companion object {
        private const val TEST_TIME_ZONE_ID = "America/New_York"
    }

    private class BlockingTransactionRepository(
        private val delegate: FakeTransactionRepository = FakeTransactionRepository(),
    ) : TransactionRepository by delegate {
        val startedUpserts: MutableList<Transaction> = mutableListOf()
        val persistedUpserts: List<Transaction>
            get() = delegate.upserted
        private val gate = CompletableDeferred<Unit>()

        override suspend fun upsert(transaction: Transaction): Long {
            startedUpserts += transaction
            gate.await()
            return delegate.upsert(transaction)
        }

        fun release() {
            if (!gate.isCompleted) {
                gate.complete(Unit)
            }
        }
    }

    private class FailOnceCategoryRepository(
        private val delegate: FakeCategoryRepository,
    ) : CategoryRepository by delegate {
        private var shouldFail = true

        override suspend fun findById(id: Long): Category? {
            if (shouldFail) {
                shouldFail = false
                throw IllegalStateException("boom")
            }
            return delegate.findById(id)
        }
    }
}
