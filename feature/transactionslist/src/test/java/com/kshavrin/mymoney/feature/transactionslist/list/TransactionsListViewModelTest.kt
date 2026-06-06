package com.kshavrin.mymoney.feature.transactionslist.list

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.kshavrin.mymoney.core.domain.model.Account
import com.kshavrin.mymoney.core.domain.model.AccountType
import com.kshavrin.mymoney.core.domain.model.CategoryKind
import com.kshavrin.mymoney.core.domain.model.Currency
import com.kshavrin.mymoney.core.domain.model.Transaction
import com.kshavrin.mymoney.core.domain.model.TransactionKind
import com.kshavrin.mymoney.core.domain.repository.CategoryGroup
import com.kshavrin.mymoney.core.domain.usecase.BalanceCalculator
import com.kshavrin.mymoney.core.domain.usecase.GetCategoryRecordsUseCase
import com.kshavrin.mymoney.feature.transactionslist.fake.FakeAccountRepository
import com.kshavrin.mymoney.feature.transactionslist.fake.FakeCurrencyRepository
import com.kshavrin.mymoney.feature.transactionslist.fake.FakeTransactionRepository
import com.kshavrin.mymoney.feature.transactionslist.util.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

class TransactionsListViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val now: Instant = Instant.parse("2026-05-20T10:00:00Z")

    private lateinit var transactionRepo: FakeTransactionRepository
    private lateinit var accountRepo: FakeAccountRepository
    private lateinit var currencyRepo: FakeCurrencyRepository

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
    private val cardAccount = Account(
        id = 8L,
        name = "Card",
        currencyId = usd.id,
        initialBalance = BigDecimal.ZERO,
        type = AccountType.Cash,
        colorHex = "#7AC794",
        iconKey = "ic_acc_card",
        isDefault = false,
        sortOrder = 1,
        createdAt = now,
        updatedAt = now,
        isArchived = false,
    )
    private val archivedUsdAccount = Account(
        id = 9L,
        name = "Archived",
        currencyId = usd.id,
        initialBalance = BigDecimal.ZERO,
        type = AccountType.Cash,
        colorHex = "#7AC794",
        iconKey = "ic_acc_archived",
        isDefault = false,
        sortOrder = 2,
        createdAt = now,
        updatedAt = now,
        isArchived = true,
    )

    private val foodGroup = CategoryGroup(
        categoryId = 10L,
        name = "Food",
        iconKey = "ic_cat_food",
        colorHex = "#FF8888",
        kind = CategoryKind.Expense,
        total = BigDecimal("30.00"),
        count = 2,
    )

    private val salaryGroup = CategoryGroup(
        categoryId = 20L,
        name = "Salary",
        iconKey = "ic_cat_salary",
        colorHex = "#7AC794",
        kind = CategoryKind.Income,
        total = BigDecimal("100.00"),
        count = 1,
    )

    private fun tx(
        id: Long,
        categoryId: Long,
        kind: TransactionKind,
        amount: BigDecimal,
        occurredAt: Instant,
        accountId: Long = cashAccount.id,
    ) = Transaction(
        id = id,
        kind = kind,
        amount = amount,
        currencyId = usd.id,
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

    @Before
    fun setUp() {
        transactionRepo = FakeTransactionRepository()
        accountRepo = FakeAccountRepository()
        currencyRepo = FakeCurrencyRepository()

        currencyRepo.seed(usd)
        accountRepo.seed(cashAccount, cardAccount, archivedUsdAccount)
    }

    private fun handleOf(
        accountId: Long? = cashAccount.id,
        currencyId: Long? = null,
        categoryId: Long? = null,
        from: Long? = null,
        to: Long? = null,
    ): SavedStateHandle {
        val map = mutableMapOf<String, Any?>()
        if (accountId != null) map[TransactionsListViewModel.KEY_ACCOUNT_ID] = accountId
        if (currencyId != null) map[TransactionsListViewModel.KEY_CURRENCY_ID] = currencyId
        if (categoryId != null) map[TransactionsListViewModel.KEY_CATEGORY_ID] = categoryId
        if (from != null) map[TransactionsListViewModel.KEY_FROM] = from
        if (to != null) map[TransactionsListViewModel.KEY_TO] = to
        return SavedStateHandle(map)
    }

    private fun buildViewModel(savedStateHandle: SavedStateHandle = handleOf()): TransactionsListViewModel {
        val records = GetCategoryRecordsUseCase(
            accountRepository = accountRepo,
            currencyRepository = currencyRepo,
            transactionRepository = transactionRepo,
            defaultDispatcher = mainDispatcherRule.testDispatcher,
        )
        val balance = BalanceCalculator(
            accountRepository = accountRepo,
            currencyRepository = currencyRepo,
            transactionRepository = transactionRepo,
            defaultDispatcher = mainDispatcherRule.testDispatcher,
        )
        return TransactionsListViewModel(
            getCategoryRecords = records,
            balanceCalculator = balance,
            accountRepository = accountRepo,
            currencyRepository = currencyRepo,
            transactionRepository = transactionRepo,
            savedStateHandle = savedStateHandle,
        )
    }

    @Test
    fun `loads category groups and clears loading`() = runTest {
        transactionRepo.seedCategoryGroups(foodGroup, salaryGroup)

        buildViewModel().state.test {
            val state = expectMostRecentItem()
            assertFalse(state.isLoading)
            assertEquals(setOf(10L, 20L), state.groups.map { it.categoryId }.toSet())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `groups are ordered by total descending`() = runTest {
        transactionRepo.seedCategoryGroups(foodGroup, salaryGroup)

        buildViewModel().state.test {
            val state = expectMostRecentItem()
            assertEquals(listOf(20L, 10L), state.sortedGroups.map { it.categoryId })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `SortClicked toggles between total descending and ascending`() = runTest {
        transactionRepo.seedCategoryGroups(foodGroup, salaryGroup)
        val viewModel = buildViewModel()

        viewModel.state.test {
            assertEquals(RecordSort.TotalDesc, expectMostRecentItem().sort)

            viewModel.onEvent(TransactionsListEvent.SortClicked)
            assertEquals(RecordSort.TotalAsc, awaitItem().sort)
            assertEquals(listOf(10L, 20L), viewModel.state.value.sortedGroups.map { it.categoryId })

            viewModel.onEvent(TransactionsListEvent.SortClicked)
            assertEquals(RecordSort.TotalDesc, awaitItem().sort)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `net header is the BalanceCalculator net for the period`() = runTest {
        transactionRepo.seedCategoryGroups(foodGroup, salaryGroup)

        buildViewModel().state.test {
            val state = expectMostRecentItem()
            // income 100 - expense 30 = 70
            assertEquals(0, BigDecimal("70.00").compareTo(state.net?.amount))
            assertEquals(usd.id, state.currency?.id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `expanded transactions are bucketed under their category ordered occurredAt descending`() = runTest {
        transactionRepo.seedCategoryGroups(foodGroup)
        transactionRepo.seedPeriodTransactions(
            tx(1L, 10L, TransactionKind.Expense, BigDecimal("10.00"), Instant.parse("2026-05-18T09:00:00Z")),
            tx(2L, 10L, TransactionKind.Expense, BigDecimal("20.00"), Instant.parse("2026-05-19T09:00:00Z")),
        )

        buildViewModel().state.test {
            val food = expectMostRecentItem().groups.first { it.categoryId == 10L }
            assertEquals(listOf(2L, 1L), food.transactions.map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `no categoryId leaves all categories collapsed`() = runTest {
        transactionRepo.seedCategoryGroups(foodGroup, salaryGroup)

        buildViewModel(handleOf(categoryId = null)).state.test {
            assertTrue(expectMostRecentItem().expandedCategoryIds.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `categoryId from savedState starts that category expanded`() = runTest {
        transactionRepo.seedCategoryGroups(foodGroup, salaryGroup)

        buildViewModel(handleOf(categoryId = 10L)).state.test {
            assertEquals(setOf(10L), expectMostRecentItem().expandedCategoryIds)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `CategoryClicked toggles a category in and out of the expanded set`() = runTest {
        transactionRepo.seedCategoryGroups(foodGroup, salaryGroup)
        val viewModel = buildViewModel()

        viewModel.state.test {
            assertTrue(expectMostRecentItem().expandedCategoryIds.isEmpty())

            viewModel.onEvent(TransactionsListEvent.CategoryClicked(10L))
            assertEquals(setOf(10L), awaitItem().expandedCategoryIds)

            viewModel.onEvent(TransactionsListEvent.CategoryClicked(10L))
            assertTrue(awaitItem().expandedCategoryIds.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
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
    fun `empty period yields the empty state`() = runTest {
        buildViewModel().state.test {
            val state = expectMostRecentItem()
            assertFalse(state.isLoading)
            assertTrue(state.isEmpty)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `currency filter without account id loads aggregate records for active same-currency accounts`() = runTest {
        transactionRepo.seedCategoryGroups(
            cashAccount.id,
            foodGroup,
            salaryGroup,
        )
        transactionRepo.seedCategoryGroups(
            cardAccount.id,
            CategoryGroup(
                categoryId = 10L,
                name = "Food",
                iconKey = "ic_cat_food",
                colorHex = "#FF8888",
                kind = CategoryKind.Expense,
                total = BigDecimal("5.00"),
                count = 1,
            ),
        )
        transactionRepo.seedCategoryGroups(
            archivedUsdAccount.id,
            CategoryGroup(
                categoryId = 10L,
                name = "Food",
                iconKey = "ic_cat_food",
                colorHex = "#FF8888",
                kind = CategoryKind.Expense,
                total = BigDecimal("999.00"),
                count = 1,
            ),
        )
        transactionRepo.seedPeriodTransactions(
            cashAccount.id,
            tx(1L, 10L, TransactionKind.Expense, BigDecimal("10.00"), Instant.parse("2026-05-18T09:00:00Z")),
            tx(2L, 20L, TransactionKind.Income, BigDecimal("100.00"), Instant.parse("2026-05-19T09:00:00Z")),
        )
        transactionRepo.seedPeriodTransactions(
            cardAccount.id,
            tx(3L, 10L, TransactionKind.Expense, BigDecimal("5.00"), Instant.parse("2026-05-20T09:00:00Z"), accountId = cardAccount.id),
        )
        transactionRepo.seedPeriodTransactions(
            archivedUsdAccount.id,
            tx(4L, 10L, TransactionKind.Expense, BigDecimal("999.00"), Instant.parse("2026-05-21T09:00:00Z"), accountId = archivedUsdAccount.id),
        )

        buildViewModel(handleOf(accountId = null, currencyId = usd.id)).state.test {
            val state = expectMostRecentItem()
            assertFalse(state.isLoading)
            assertEquals(null, state.accountId)
            assertEquals(usd.id, state.currencyId)
            assertEquals(setOf(10L, 20L), state.groups.map { it.categoryId }.toSet())
            assertEquals(0, BigDecimal("35.00").compareTo(state.groups.single { it.categoryId == 10L }.total.amount))
            assertEquals(listOf(3L, 1L), state.groups.single { it.categoryId == 10L }.transactions.map { it.id })
            assertEquals(0, BigDecimal("65.00").compareTo(state.net?.amount))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `currency filter with category id starts that aggregate category expanded`() = runTest {
        transactionRepo.seedCategoryGroups(
            cashAccount.id,
            foodGroup,
        )

        buildViewModel(handleOf(accountId = null, currencyId = usd.id, categoryId = 10L)).state.test {
            val state = expectMostRecentItem()
            assertEquals(setOf(10L), state.expandedCategoryIds)
            assertEquals(null, state.accountId)
            assertEquals(usd.id, state.currencyId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `category filter limits groups and exposes the category chip label`() = runTest {
        transactionRepo.seedCategoryGroups(
            cashAccount.id,
            foodGroup,
            salaryGroup,
        )
        transactionRepo.seedPeriodTransactions(
            cashAccount.id,
            tx(1L, 10L, TransactionKind.Expense, BigDecimal("10.00"), Instant.parse("2026-05-18T09:00:00Z")),
            tx(2L, 20L, TransactionKind.Income, BigDecimal("100.00"), Instant.parse("2026-05-19T09:00:00Z")),
        )

        buildViewModel(handleOf(categoryId = 10L)).state.test {
            val state = expectMostRecentItem()
            assertEquals(listOf(10L), state.groups.map { it.categoryId })
            assertEquals(10L, state.categoryId)
            assertEquals("Food", state.categoryName)
            assertTrue(state.hasCategoryFilter)
            assertEquals(setOf(10L), state.expandedCategoryIds)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `clearing category filter reloads the unfiltered list`() = runTest {
        transactionRepo.seedCategoryGroups(
            cashAccount.id,
            foodGroup,
            salaryGroup,
        )
        transactionRepo.seedPeriodTransactions(
            cashAccount.id,
            tx(1L, 10L, TransactionKind.Expense, BigDecimal("10.00"), Instant.parse("2026-05-18T09:00:00Z")),
            tx(2L, 20L, TransactionKind.Income, BigDecimal("100.00"), Instant.parse("2026-05-19T09:00:00Z")),
        )
        val viewModel = buildViewModel(handleOf(categoryId = 10L))

        viewModel.state.test {
            assertEquals(listOf(10L), expectMostRecentItem().groups.map { it.categoryId })

            viewModel.onEvent(TransactionsListEvent.CategoryFilterCleared)

            assertTrue(awaitItem().isLoading)
            val reloaded = awaitItem()
            assertEquals(setOf(10L, 20L), reloaded.groups.map { it.categoryId }.toSet())
            assertEquals(null, reloaded.categoryId)
            assertEquals(null, reloaded.categoryName)
            assertTrue(reloaded.expandedCategoryIds.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `missing category filter falls back to the unfiltered list`() = runTest {
        transactionRepo.seedCategoryGroups(
            cashAccount.id,
            foodGroup,
            salaryGroup,
        )

        buildViewModel(handleOf(categoryId = 999L)).state.test {
            val state = expectMostRecentItem()
            assertEquals(setOf(10L, 20L), state.groups.map { it.categoryId }.toSet())
            assertEquals(null, state.categoryId)
            assertEquals(null, state.categoryName)
            assertFalse(state.hasCategoryFilter)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
