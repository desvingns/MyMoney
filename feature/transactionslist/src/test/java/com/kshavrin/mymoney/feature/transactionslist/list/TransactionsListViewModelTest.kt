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
import com.kshavrin.mymoney.core.domain.repository.TransferRow
import com.kshavrin.mymoney.core.domain.usecase.BalanceCalculator
import com.kshavrin.mymoney.core.domain.usecase.GetCategoryRecordsUseCase
import com.kshavrin.mymoney.core.domain.usecase.GetTransferRecordsUseCase
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
    private val cardAccount =
        Account(
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
    private val archivedUsdAccount =
        Account(
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

    private val foodGroup =
        CategoryGroup(
            categoryId = 10L,
            name = "Food",
            iconKey = "ic_cat_food",
            colorHex = "#FF8888",
            kind = CategoryKind.Expense,
            total = BigDecimal("30.00"),
            count = 2,
        )

    private val salaryGroup =
        CategoryGroup(
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
        val records =
            GetCategoryRecordsUseCase(
                accountRepository = accountRepo,
                currencyRepository = currencyRepo,
                transactionRepository = transactionRepo,
                defaultDispatcher = mainDispatcherRule.testDispatcher,
            )
        val balance =
            BalanceCalculator(
                accountRepository = accountRepo,
                currencyRepository = currencyRepo,
                transactionRepository = transactionRepo,
                defaultDispatcher = mainDispatcherRule.testDispatcher,
            )
        val transfers =
            GetTransferRecordsUseCase(
                currencyRepository = currencyRepo,
                transactionRepository = transactionRepo,
                defaultDispatcher = mainDispatcherRule.testDispatcher,
            )
        return TransactionsListViewModel(
            getCategoryRecords = records,
            getTransferRecords = transfers,
            balanceCalculator = balance,
            accountRepository = accountRepo,
            currencyRepository = currencyRepo,
            transactionRepository = transactionRepo,
            savedStateHandle = savedStateHandle,
        )
    }

    @Test
    fun `loads category groups and clears loading`() =
        runTest {
            transactionRepo.seedCategoryGroups(foodGroup, salaryGroup)

            buildViewModel().state.test {
                val state = expectMostRecentItem()
                assertFalse(state.isLoading)
                assertEquals(setOf(10L, 20L), state.groups.map { it.categoryId }.toSet())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `groups are ordered by total descending`() =
        runTest {
            transactionRepo.seedCategoryGroups(foodGroup, salaryGroup)

            buildViewModel().state.test {
                val state = expectMostRecentItem()
                assertEquals(listOf(20L, 10L), state.sortedGroups.map { it.categoryId })
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `SortClicked toggles between total descending and ascending`() =
        runTest {
            transactionRepo.seedCategoryGroups(foodGroup, salaryGroup)
            val viewModel = buildViewModel()

            viewModel.state.test {
                assertEquals(RecordSort.TotalDesc, expectMostRecentItem().sort)

                viewModel.onEvent(TransactionsListEvent.SortClicked)
                assertEquals(RecordSort.TotalAsc, awaitItem().sort)
                assertEquals(
                    listOf(10L, 20L),
                    viewModel.state.value.sortedGroups
                        .map { it.categoryId },
                )

                viewModel.onEvent(TransactionsListEvent.SortClicked)
                assertEquals(RecordSort.TotalDesc, awaitItem().sort)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `net header is the BalanceCalculator net for the period`() =
        runTest {
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
    fun `expanded transactions are bucketed under their category ordered occurredAt descending`() =
        runTest {
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
    fun `no categoryId leaves all categories collapsed`() =
        runTest {
            transactionRepo.seedCategoryGroups(foodGroup, salaryGroup)

            buildViewModel(handleOf(categoryId = null)).state.test {
                assertTrue(expectMostRecentItem().expandedCategoryIds.isEmpty())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `categoryId from savedState starts that category expanded`() =
        runTest {
            transactionRepo.seedCategoryGroups(foodGroup, salaryGroup)

            buildViewModel(handleOf(categoryId = 10L)).state.test {
                assertEquals(setOf(10L), expectMostRecentItem().expandedCategoryIds)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `CategoryClicked toggles a category in and out of the expanded set`() =
        runTest {
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
    fun `RowClicked emits OpenDetail with the clicked id`() =
        runTest {
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
    fun `SwipeDeleted soft-deletes the transaction and emits ShowUndoSnackbar with the id`() =
        runTest {
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
    fun `UndoDeleteClicked restores the transaction`() =
        runTest {
            val viewModel = buildViewModel()

            viewModel.onEvent(TransactionsListEvent.UndoDeleteClicked(id = 42L))

            assertEquals(listOf(42L), transactionRepo.restoredIds)
        }

    @Test
    fun `empty period yields the empty state`() =
        runTest {
            buildViewModel().state.test {
                val state = expectMostRecentItem()
                assertFalse(state.isLoading)
                assertTrue(state.isEmpty)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `currency filter without account id loads aggregate records for active same-currency accounts`() =
        runTest {
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
                assertEquals(
                    0,
                    BigDecimal("35.00").compareTo(
                        state.groups
                            .single { it.categoryId == 10L }
                            .total.amount,
                    ),
                )
                assertEquals(
                    listOf(3L, 1L),
                    state.groups
                        .single { it.categoryId == 10L }
                        .transactions
                        .map { it.id },
                )
                assertEquals(0, BigDecimal("65.00").compareTo(state.net?.amount))
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `currency filter with category id starts that aggregate category expanded`() =
        runTest {
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
    fun `category filter limits groups and exposes the category chip label`() =
        runTest {
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
    fun `clearing category filter reloads the unfiltered list`() =
        runTest {
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
    fun `missing category filter falls back to the unfiltered list`() =
        runTest {
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

    // ----- Transfers tab tests -----

    @Test
    fun `transfers are loaded and exposed in state after init`() =
        runTest {
            val transferRow =
                TransferRow(
                    id = 55L,
                    fromAccountName = "Наличные",
                    toAccountName = "Карта",
                    amount = BigDecimal("500.00"),
                    toAmount = null,
                    currencyId = usd.id,
                    occurredAt = now,
                    note = null,
                )
            transactionRepo.seedTransfers(transferRow)

            buildViewModel().state.test {
                val state = expectMostRecentItem()
                assertFalse(state.isLoading)
                assertEquals(1, state.transfers.size)
                val record = state.transfers.single()
                assertEquals(55L, record.id)
                assertEquals("Наличные", record.fromAccountName)
                assertEquals("Карта", record.toAccountName)
                assertEquals(0, BigDecimal("500.00").compareTo(record.amount.amount))
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `isTransfersEmpty is true when there are no transfers and loading is complete`() =
        runTest {
            buildViewModel().state.test {
                val state = expectMostRecentItem()
                assertFalse(state.isLoading)
                assertTrue(state.isTransfersEmpty)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `isTransfersEmpty is false when transfers are present`() =
        runTest {
            val transferRow =
                TransferRow(
                    id = 1L,
                    fromAccountName = "A",
                    toAccountName = "B",
                    amount = BigDecimal("100.00"),
                    toAmount = null,
                    currencyId = usd.id,
                    occurredAt = now,
                    note = null,
                )
            transactionRepo.seedTransfers(transferRow)

            buildViewModel().state.test {
                val state = expectMostRecentItem()
                assertFalse(state.isTransfersEmpty)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `TabSelected Operations keeps Operations tab active`() =
        runTest {
            val viewModel = buildViewModel()

            viewModel.state.test {
                expectMostRecentItem()

                viewModel.onEvent(TransactionsListEvent.TabSelected(RecordsTab.Operations))

                assertEquals(RecordsTab.Operations, viewModel.state.value.activeTab)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `TabSelected Transfers switches active tab to Transfers`() =
        runTest {
            val viewModel = buildViewModel()

            viewModel.state.test {
                expectMostRecentItem()

                viewModel.onEvent(TransactionsListEvent.TabSelected(RecordsTab.Transfers))

                val state = awaitItem()
                assertEquals(RecordsTab.Transfers, state.activeTab)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `TabSelected same tab twice does not emit duplicate state`() =
        runTest {
            val viewModel = buildViewModel()

            viewModel.state.test {
                expectMostRecentItem()

                viewModel.onEvent(TransactionsListEvent.TabSelected(RecordsTab.Transfers))
                awaitItem()

                viewModel.onEvent(TransactionsListEvent.TabSelected(RecordsTab.Transfers))
                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `RowClicked on a transfer id emits OpenDetail with that id`() =
        runTest {
            val viewModel = buildViewModel()

            viewModel.actions.test {
                viewModel.onEvent(TransactionsListEvent.RowClicked(id = 55L))

                val action = awaitItem()
                assertTrue(
                    "expected OpenDetail(55) but was $action",
                    action is TransactionsListAction.OpenDetail && action.transactionId == 55L,
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `Operations tab groups do not include transfers`() =
        runTest {
            transactionRepo.seedCategoryGroups(cashAccount.id, foodGroup)
            val transferRow =
                TransferRow(
                    id = 99L,
                    fromAccountName = "Cash",
                    toAccountName = "Card",
                    amount = BigDecimal("200.00"),
                    toAmount = null,
                    currencyId = usd.id,
                    occurredAt = now,
                    note = null,
                )
            transactionRepo.seedTransfers(transferRow)

            buildViewModel().state.test {
                val state = expectMostRecentItem()
                assertEquals(listOf(10L), state.groups.map { it.categoryId })
                assertEquals(1, state.transfers.size)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `category filter on Operations tab does not affect transfers tab`() =
        runTest {
            transactionRepo.seedCategoryGroups(cashAccount.id, foodGroup, salaryGroup)
            transactionRepo.seedPeriodTransactions(cashAccount.id, tx(1L, 10L, TransactionKind.Expense, BigDecimal("10.00"), now))
            val transferRow =
                TransferRow(
                    id = 77L,
                    fromAccountName = "A",
                    toAccountName = "B",
                    amount = BigDecimal("50.00"),
                    toAmount = null,
                    currencyId = usd.id,
                    occurredAt = now,
                    note = null,
                )
            transactionRepo.seedTransfers(transferRow)

            buildViewModel(handleOf(categoryId = 10L)).state.test {
                val state = expectMostRecentItem()
                assertEquals(listOf(10L), state.groups.map { it.categoryId })
                assertEquals(1, state.transfers.size)
                assertEquals(77L, state.transfers.single().id)
                cancelAndIgnoreRemainingEvents()
            }
        }

    // ----- Reactivity / observeRecent tests -----

    @Test
    fun `table change triggers reload and updated group total is reflected in state`() =
        runTest {
            val initialFood = foodGroup.copy(total = BigDecimal("100.00"))
            transactionRepo.seedCategoryGroups(initialFood)
            val viewModel = buildViewModel()

            viewModel.state.test {
                val before = expectMostRecentItem()
                assertEquals(
                    0,
                    BigDecimal("100.00").compareTo(
                        before.groups
                            .single { it.categoryId == 10L }
                            .total.amount,
                    ),
                )

                val updatedFood = foodGroup.copy(total = BigDecimal("250.00"))
                transactionRepo.updateCategoryGroups(updatedFood)
                transactionRepo.triggerRecentChange()

                val after = awaitItem()
                assertEquals(
                    0,
                    BigDecimal("250.00").compareTo(
                        after.groups
                            .single { it.categoryId == 10L }
                            .total.amount,
                    ),
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `drop(1) contract — initial observeRecent emission does not produce a duplicate loaded state`() =
        runTest {
            transactionRepo.seedCategoryGroups(foodGroup)
            val viewModel = buildViewModel()

            viewModel.state.test {
                val loaded = expectMostRecentItem()
                assertFalse(loaded.isLoading)
                assertEquals(listOf(10L), loaded.groups.map { it.categoryId })
                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `filter and expanded groups survive a table-change reload`() =
        runTest {
            transactionRepo.seedCategoryGroups(cashAccount.id, foodGroup, salaryGroup)
            transactionRepo.seedPeriodTransactions(
                cashAccount.id,
                tx(1L, 10L, TransactionKind.Expense, BigDecimal("10.00"), now),
            )
            val viewModel = buildViewModel(handleOf(categoryId = 10L))

            viewModel.state.test {
                val before = expectMostRecentItem()
                assertEquals(10L, before.categoryId)
                assertEquals(setOf(10L), before.expandedCategoryIds)
                assertEquals(
                    0,
                    BigDecimal("30.00").compareTo(
                        before.groups
                            .single { it.categoryId == 10L }
                            .total.amount,
                    ),
                )

                // Mutate the account-keyed category groups so the reload produces a structurally
                // different state; a purely equal reload would be swallowed by the conflating StateFlow.
                val updatedFood = foodGroup.copy(total = BigDecimal("75.00"))
                transactionRepo.updateCategoryGroups(cashAccount.id, updatedFood, salaryGroup)
                transactionRepo.triggerRecentChange()

                val after = awaitItem()
                // Sub-state preserved: category filter and expanded set intact.
                assertEquals(10L, after.categoryId)
                assertEquals(setOf(10L), after.expandedCategoryIds)
                // Data actually refreshed: the new food total is visible.
                assertEquals(
                    0,
                    BigDecimal("75.00").compareTo(
                        after.groups
                            .single { it.categoryId == 10L }
                            .total.amount,
                    ),
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `active Transfers tab survives a table-change reload`() =
        runTest {
            transactionRepo.seedCategoryGroups(foodGroup)
            val viewModel = buildViewModel()

            viewModel.state.test {
                expectMostRecentItem()

                viewModel.onEvent(TransactionsListEvent.TabSelected(RecordsTab.Transfers))
                assertEquals(RecordsTab.Transfers, awaitItem().activeTab)

                // Mutate the transfers list so the reload produces a structurally different state;
                // a purely equal reload would be swallowed by the conflating StateFlow.
                val newTransfer =
                    TransferRow(
                        id = 11L,
                        fromAccountName = "Cash",
                        toAccountName = "Card",
                        amount = BigDecimal("150.00"),
                        toAmount = null,
                        currencyId = usd.id,
                        occurredAt = now,
                        note = null,
                    )
                transactionRepo.updateTransfers(newTransfer)
                transactionRepo.triggerRecentChange()

                val afterReload = awaitItem()
                // Sub-state preserved: active tab is still Transfers.
                assertEquals(RecordsTab.Transfers, afterReload.activeTab)
                // Data actually refreshed: the new transfer is visible.
                assertEquals(1, afterReload.transfers.size)
                assertEquals(11L, afterReload.transfers.single().id)
                cancelAndIgnoreRemainingEvents()
            }
        }
}
