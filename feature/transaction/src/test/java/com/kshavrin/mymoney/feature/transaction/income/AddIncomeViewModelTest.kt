package com.kshavrin.mymoney.feature.transaction.income

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import app.cash.turbine.test
import com.kshavrin.mymoney.core.domain.model.Account
import com.kshavrin.mymoney.core.domain.model.AccountType
import com.kshavrin.mymoney.core.domain.model.Category
import com.kshavrin.mymoney.core.domain.model.CategoryKind
import com.kshavrin.mymoney.core.domain.model.Currency
import com.kshavrin.mymoney.core.domain.model.Transaction
import com.kshavrin.mymoney.core.domain.model.TransactionKind
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

class AddIncomeViewModelTest {
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

    private val salaryCategory =
        incomeCategory(
            id = 20L,
            name = "Salary",
        )

    private fun incomeCategory(
        id: Long,
        name: String,
        sortOrder: Int = 0,
        isArchived: Boolean = false,
    ) = Category(
        id = id,
        name = name,
        kind = CategoryKind.Income,
        iconKey = "ic_cat_${name.lowercase()}",
        colorHex = "#88FF88",
        textColor = "#FFFFFF",
        sortOrder = sortOrder,
        isDefault = false,
        isArchived = isArchived,
        createdAt = now,
    )

    private fun expenseCategory(
        id: Long,
        name: String,
    ) =
        Category(
            id = id,
            name = name,
            kind = CategoryKind.Expense,
            iconKey = "ic_cat_${name.lowercase()}",
            colorHex = "#FF8888",
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
        categoryRepo.seed(salaryCategory)
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(originalTimeZone)
    }

    private fun buildViewModel(
        transactionRepository: TransactionRepository = transactionRepo,
    ): AddIncomeViewModel =
        AddIncomeViewModel(
            transactionRepository = transactionRepository,
            accountRepository = accountRepo,
            currencyRepository = currencyRepo,
            categoryRepository = categoryRepo,
            appSettingsRepository = settingsRepo,
            savedStateHandle = savedStateHandle,
        )

    private fun localMidnight(date: LocalDate): Instant =
        date.atStartOfDay(ZoneId.systemDefault()).toInstant()

    private fun AddIncomeViewModel.setStateForExplicitSave(
        amount: BigDecimal,
        category: Category,
    ) {
        val field = AddIncomeViewModel::class.java.getDeclaredField("_state")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val stateFlow = field.get(this) as MutableStateFlow<AddIncomeState>
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
    fun `state categories include only unarchived income categories sorted by sortOrder`() =
        runTest {
            categoryRepo.seed(
                incomeCategory(id = 21L, name = "Gifts", sortOrder = 2),
                incomeCategory(id = 22L, name = "Bonus", sortOrder = -1),
                incomeCategory(id = 23L, name = "Old", sortOrder = -2, isArchived = true),
                expenseCategory(id = 10L, name = "Food"),
            )

            val viewModel = buildViewModel()

            val categories = viewModel.state.value.categories
            assertEquals(listOf(22L, 20L, 21L), categories.map { it.id })
            assertTrue(categories.all { it.kind == CategoryKind.Income && !it.isArchived })
        }

    @Test
    fun `SelectCategoryClicked with positive amount opens income category step`() =
        runTest {
            val viewModel = buildViewModel()

            viewModel.onEvent(AddIncomeEvent.KeypadDigit(6))
            viewModel.onEvent(AddIncomeEvent.SelectCategoryClicked)

            assertTrue(viewModel.state.value.categoryStep)
        }

    @Test
    fun `SelectCategoryClicked with zero amount keeps income in amount step and shows amount error`() =
        runTest {
            val viewModel = buildViewModel()

            viewModel.onEvent(AddIncomeEvent.SelectCategoryClicked)

            assertFalse(viewModel.state.value.categoryStep)
            assertEquals(R.string.error_enter_amount_first, viewModel.state.value.errorBannerRes)
            assertEquals(0, transactionRepo.upserted.size)
        }

    @Test
    fun `BackToAmount leaves income category step and clears error banner`() =
        runTest {
            val viewModel = buildViewModel()
            viewModel.onEvent(AddIncomeEvent.KeypadDigit(6))
            viewModel.onEvent(AddIncomeEvent.SelectCategoryClicked)
            viewModel.onEvent(AddIncomeEvent.SaveClicked)

            assertTrue(viewModel.state.value.categoryStep)
            assertEquals(R.string.error_choose_category_first, viewModel.state.value.errorBannerRes)

            viewModel.onEvent(AddIncomeEvent.BackToAmount)

            assertFalse(viewModel.state.value.categoryStep)
            assertNull(viewModel.state.value.errorBannerRes)
        }

    @Test
    fun `AddCategoryClicked emits NavigateToCreateCategory`() =
        runTest {
            val viewModel = buildViewModel()

            viewModel.actions.test {
                viewModel.onEvent(AddIncomeEvent.AddCategoryClicked)

                assertEquals(AddIncomeAction.NavigateToCreateCategory, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `CategoryPicked with zero amount returns to amount step and does not save`() =
        runTest {
            val viewModel = buildViewModel()

            viewModel.onEvent(AddIncomeEvent.CategoryPicked(salaryCategory.id))

            assertEquals(false, viewModel.state.value.categoryStep)
            assertEquals(R.string.error_enter_amount_first, viewModel.state.value.errorBannerRes)
            assertEquals(0, transactionRepo.upserted.size)
        }

    @Test
    fun `CategoryPicked event saves an income transaction with picked category and matching amount`() =
        runTest {
            val viewModel = buildViewModel()
            val saveDate = LocalDate.parse("2026-06-10")
            viewModel.onEvent(AddIncomeEvent.KeypadDigit(9))
            viewModel.onEvent(AddIncomeEvent.DateChanged(saveDate))

            viewModel.onEvent(AddIncomeEvent.CategoryPicked(salaryCategory.id))

            assertEquals(1, transactionRepo.upserted.size)
            val saved = transactionRepo.upserted.single()
            assertEquals(TransactionKind.Income, saved.kind)
            assertEquals(0, BigDecimal("9").compareTo(saved.amount))
            assertEquals(salaryCategory.id, saved.categoryId)
            assertEquals(cashAccount.id, saved.accountId)
            assertEquals(usd.id, saved.currencyId)
            assertEquals(localMidnight(saveDate), saved.occurredAt)
        }

    @Test
    fun `CategoryPicked emits NavigateBack and increments income saved signal`() =
        runTest {
            val viewModel = buildViewModel()
            viewModel.onEvent(AddIncomeEvent.KeypadDigit(9))
            viewModel.onEvent(AddIncomeEvent.SelectCategoryClicked)

            viewModel.actions.test {
                viewModel.onEvent(AddIncomeEvent.CategoryPicked(salaryCategory.id))

                assertEquals(AddIncomeAction.NavigateBack, awaitItem())
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
            viewModel.setStateForExplicitSave(amount = BigDecimal("9"), category = salaryCategory)

            viewModel.actions.test {
                viewModel.onEvent(AddIncomeEvent.SaveClicked)
                assertTrue(viewModel.state.value.isSaving)
                viewModel.onEvent(AddIncomeEvent.SaveClicked)

                assertEquals(1, blockingRepo.startedUpserts.size)

                blockingRepo.release()
                advanceUntilIdle()

                assertEquals(1, blockingRepo.persistedUpserts.size)
                assertEquals(AddIncomeAction.NavigateBack, awaitItem())
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
            viewModel.onEvent(AddIncomeEvent.KeypadDigit(9))
            viewModel.onEvent(AddIncomeEvent.SelectCategoryClicked)

            viewModel.actions.test {
                viewModel.onEvent(AddIncomeEvent.CategoryPicked(salaryCategory.id))
                assertTrue(viewModel.state.value.isSaving)
                viewModel.onEvent(AddIncomeEvent.CategoryPicked(salaryCategory.id))

                assertEquals(1, blockingRepo.startedUpserts.size)

                blockingRepo.release()
                advanceUntilIdle()

                assertEquals(1, blockingRepo.persistedUpserts.size)
                assertEquals(AddIncomeAction.NavigateBack, awaitItem())
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
            viewModel.setStateForExplicitSave(amount = BigDecimal("9"), category = salaryCategory)

            viewModel.actions.test {
                viewModel.onEvent(AddIncomeEvent.SaveClicked)
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
    fun `save failure keeps existing income error banner mapping`() =
        runTest {
            val viewModel = buildViewModel(transactionRepository = FailingTransactionRepository())
            advanceUntilIdle()
            viewModel.setStateForExplicitSave(amount = BigDecimal("9"), category = salaryCategory)

            viewModel.onEvent(AddIncomeEvent.SaveClicked)
            advanceUntilIdle()

            assertFalse(viewModel.state.value.isSaving)
            assertEquals(R.string.error_save_failed, viewModel.state.value.errorBannerRes)
            assertEquals(0L, viewModel.state.value.savedSignal)
        }

    @Test
    fun `SwapMode emits NavigateToExpenseForm`() =
        runTest {
            val viewModel = buildViewModel()

            viewModel.actions.test {
                viewModel.onEvent(AddIncomeEvent.SwapMode)

                assertEquals(AddIncomeAction.NavigateToExpenseForm, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
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

    private class FailingTransactionRepository(
        private val delegate: FakeTransactionRepository = FakeTransactionRepository(),
    ) : TransactionRepository by delegate {
        override suspend fun upsert(transaction: Transaction): Long = throw IllegalStateException("boom")
    }
}
