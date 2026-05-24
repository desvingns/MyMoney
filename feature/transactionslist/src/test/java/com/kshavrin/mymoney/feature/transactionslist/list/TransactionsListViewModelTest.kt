package com.kshavrin.mymoney.feature.transactionslist.list

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.kshavrin.mymoney.core.domain.model.Account
import com.kshavrin.mymoney.core.domain.model.AccountType
import com.kshavrin.mymoney.core.domain.model.Category
import com.kshavrin.mymoney.core.domain.model.CategoryKind
import com.kshavrin.mymoney.core.domain.model.Currency
import com.kshavrin.mymoney.core.domain.model.Period
import com.kshavrin.mymoney.core.domain.model.Transaction
import com.kshavrin.mymoney.core.domain.model.TransactionKind
import com.kshavrin.mymoney.core.domain.time.PeriodArithmetic
import com.kshavrin.mymoney.feature.transactionslist.fake.FakeAccountRepository
import com.kshavrin.mymoney.feature.transactionslist.fake.FakeCategoryRepository
import com.kshavrin.mymoney.feature.transactionslist.fake.FakeCurrencyRepository
import com.kshavrin.mymoney.feature.transactionslist.fake.FakeTransactionRepository
import com.kshavrin.mymoney.feature.transactionslist.util.MainDispatcherRule
import com.kshavrin.mymoney.feature.transactionslist.util.firstSnapshot
import kotlinx.coroutines.flow.first
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
import java.time.YearMonth

class TransactionsListViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val now: Instant = Instant.parse("2026-05-20T10:00:00Z")

    private lateinit var transactionRepo: FakeTransactionRepository
    private lateinit var accountRepo: FakeAccountRepository
    private lateinit var currencyRepo: FakeCurrencyRepository
    private lateinit var categoryRepo: FakeCategoryRepository

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
        id = 7L,
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

    private val foodCategory = Category(
        id = 10L,
        name = "Food",
        kind = CategoryKind.Expense,
        iconKey = "ic_cat_food",
        colorHex = "#FF8888",
        sortOrder = 0,
        isDefault = false,
        isArchived = false,
        createdAt = now,
    )

    private fun expense(
        id: Long,
        occurredAt: Instant,
        categoryId: Long? = foodCategory.id,
        amount: BigDecimal = BigDecimal("12.50"),
    ) = Transaction(
        id = id,
        kind = TransactionKind.Expense,
        amount = amount,
        currencyId = usd.id,
        accountId = cashAccount.id,
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

    @Before
    fun setUp() {
        transactionRepo = FakeTransactionRepository()
        accountRepo = FakeAccountRepository()
        currencyRepo = FakeCurrencyRepository()
        categoryRepo = FakeCategoryRepository()

        currencyRepo.seed(usd)
        accountRepo.seed(cashAccount)
        categoryRepo.seed(foodCategory)
    }

    private fun handleOf(
        accountId: Long = cashAccount.id,
        categoryId: Long? = null,
        from: Long? = null,
        to: Long? = null,
    ): SavedStateHandle {
        val map = mutableMapOf<String, Any?>(TransactionsListViewModel.KEY_ACCOUNT_ID to accountId)
        if (categoryId != null) map[TransactionsListViewModel.KEY_CATEGORY_ID] = categoryId
        if (from != null) map[TransactionsListViewModel.KEY_FROM] = from
        if (to != null) map[TransactionsListViewModel.KEY_TO] = to
        return SavedStateHandle(map)
    }

    private fun buildViewModel(savedStateHandle: SavedStateHandle = handleOf()): TransactionsListViewModel =
        TransactionsListViewModel(
            transactionRepository = transactionRepo,
            categoryRepository = categoryRepo,
            currencyRepository = currencyRepo,
            accountRepository = accountRepo,
            savedStateHandle = savedStateHandle,
        )

    @Test
    fun `accountId from savedState is forwarded to repository paged`() = runTest {
        // pagingData is cold; collecting the first emission triggers the repository call.
        buildViewModel(handleOf(accountId = 7L)).pagingData.first()

        val call = transactionRepo.pagedCalls.last()
        assertEquals(7L, call.accountId)
    }

    @Test
    fun `categoryId from savedState is forwarded to repository paged`() = runTest {
        buildViewModel(handleOf(categoryId = 10L)).pagingData.first()

        val call = transactionRepo.pagedCalls.last()
        assertEquals(10L, call.categoryId)
    }

    @Test
    fun `missing categoryId yields null forwarded to repository paged`() = runTest {
        buildViewModel(handleOf(categoryId = null)).pagingData.first()

        val call = transactionRepo.pagedCalls.last()
        assertNull(call.categoryId)
    }

    @Test
    fun `explicit from and to from savedState are forwarded to repository paged`() = runTest {
        val from = Instant.parse("2026-03-01T00:00:00Z").toEpochMilli()
        val to = Instant.parse("2026-03-31T23:59:59Z").toEpochMilli()

        buildViewModel(handleOf(from = from, to = to)).pagingData.first()

        val call = transactionRepo.pagedCalls.last()
        assertEquals(from, call.from.toEpochMilli())
        assertEquals(to, call.to.toEpochMilli())
    }

    @Test
    fun `default period is current month when no from or to supplied`() = runTest {
        buildViewModel(handleOf()).pagingData.first()

        val expected = PeriodArithmetic.toEpochMillisRange(Period.Month(YearMonth.now()))
        val call = transactionRepo.pagedCalls.last()
        assertEquals(expected.first, call.from.toEpochMilli())
        assertEquals(expected.last, call.to.toEpochMilli())
    }

    @Test
    fun `pagingData maps a non-empty page into Row items`() = runTest {
        transactionRepo.seed(
            expense(id = 1L, occurredAt = Instant.parse("2026-05-20T09:00:00Z")),
            expense(id = 2L, occurredAt = Instant.parse("2026-05-20T08:00:00Z")),
        )

        val items = buildViewModel().pagingData.firstSnapshot(this)

        val rows = items.filterIsInstance<TransactionListItem.Row>()
        assertEquals(listOf(1L, 2L), rows.map { it.id })
        assertEquals(0, BigDecimal("12.50").compareTo(rows.first().amount))
    }

    @Test
    fun `pagingData inserts a day header before the first row`() = runTest {
        transactionRepo.seed(
            expense(id = 1L, occurredAt = Instant.parse("2026-05-20T09:00:00Z")),
        )

        val items = buildViewModel().pagingData.firstSnapshot(this)

        assertTrue(
            "expected at least one DayHeader but got $items",
            items.any { it is TransactionListItem.DayHeader },
        )
        assertTrue("first item should be a DayHeader", items.first() is TransactionListItem.DayHeader)
    }

    @Test
    fun `empty page maps to no list items`() = runTest {
        val items = buildViewModel().pagingData.firstSnapshot(this)

        assertTrue("expected empty list for empty page but got $items", items.isEmpty())
    }

    @Test
    fun `SwipeDeleted soft-deletes the transaction and emits ShowUndoSnackbar with the id`() = runTest {
        val viewModel = buildViewModel()

        viewModel.actions.test {
            viewModel.onEvent(TransactionsListEvent.SwipeDeleted(id = 42L))

            val action = awaitItem()
            assertTrue(
                "expected ShowUndoSnackbar(42) but was $action",
                action is TransactionsListAction.ShowUndoSnackbar && action.transactionId == 42L,
            )
            assertEquals(listOf(42L), transactionRepo.softDeletedIds)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `UndoDeleteClicked restores the transaction`() = runTest {
        val viewModel = buildViewModel()

        viewModel.onEvent(TransactionsListEvent.UndoDeleteClicked(id = 42L))

        assertEquals(listOf(42L), transactionRepo.restoredIds)
    }

    @Test
    fun `RowClicked emits OpenDetail with the clicked id`() = runTest {
        val viewModel = buildViewModel()

        viewModel.actions.test {
            viewModel.onEvent(TransactionsListEvent.RowClicked(id = 5L))

            val action = awaitItem()
            assertTrue(
                "expected OpenDetail(5) but was $action",
                action is TransactionsListAction.OpenDetail && action.transactionId == 5L,
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `category filter context is loaded into state when categoryId is present`() = runTest {
        val viewModel = buildViewModel(handleOf(categoryId = foodCategory.id))

        viewModel.state.test {
            val state = expectMostRecentItem()
            assertEquals(usd.id, state.currency?.id)
            assertNotNull(state.categoryFilter)
            assertEquals(foodCategory.id, state.categoryFilter?.id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `no category filter in state when categoryId is absent`() = runTest {
        val viewModel = buildViewModel(handleOf(categoryId = null))

        viewModel.state.test {
            val state = expectMostRecentItem()
            assertNull(state.categoryFilter)
            assertEquals(usd.id, state.currency?.id)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
