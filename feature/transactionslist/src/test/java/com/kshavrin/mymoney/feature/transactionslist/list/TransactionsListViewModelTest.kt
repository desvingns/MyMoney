package com.kshavrin.mymoney.feature.transactionslist.list

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.kshavrin.mymoney.core.domain.model.Account
import com.kshavrin.mymoney.core.domain.model.AccountType
import com.kshavrin.mymoney.core.domain.model.Category
import com.kshavrin.mymoney.core.domain.model.CategoryKind
import com.kshavrin.mymoney.core.domain.model.Currency
import com.kshavrin.mymoney.core.domain.model.Period
import com.kshavrin.mymoney.core.domain.model.SummaryRecord
import com.kshavrin.mymoney.core.domain.model.Transaction
import com.kshavrin.mymoney.core.domain.model.TransactionKind
import com.kshavrin.mymoney.core.domain.repository.CategoryGroup
import com.kshavrin.mymoney.core.domain.repository.TransferRow
import com.kshavrin.mymoney.core.domain.usecase.GetCategoryRecordsUseCase
import com.kshavrin.mymoney.core.domain.usecase.GetOperationsSummaryUseCase
import com.kshavrin.mymoney.core.domain.usecase.GetTransferRecordsUseCase
import com.kshavrin.mymoney.core.testing.fake.FakeCurrencyRepository
import com.kshavrin.mymoney.feature.transactionslist.fake.FakeAccountRepository
import com.kshavrin.mymoney.feature.transactionslist.fake.FakeCategoryRepository
import com.kshavrin.mymoney.feature.transactionslist.fake.FakeTransactionRepository
import com.kshavrin.mymoney.feature.transactionslist.util.MainDispatcherRule
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class TransactionsListViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(StandardTestDispatcher())

    @Test
    fun `route defaults fall back to current month instead of epoch custom range`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val fixture = Fixtures(mainDispatcherRule.testDispatcher)
            fixture.seedAccountData()

            val viewModel = buildViewModel(defaultArgs(), fixture)
            advanceUntilIdle()

            assertTrue(
                "missing from/to route args must not become an epoch CustomRange",
                viewModel.state.value.period is Period.Month,
            )
        }

    @Test
    fun `all-period route is decoded as Period All`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val fixture = Fixtures(mainDispatcherRule.testDispatcher)
            fixture.seedAccountData()

            val viewModel = buildViewModel(defaultArgs(from = 0L, to = Long.MAX_VALUE), fixture)
            advanceUntilIdle()

            assertEquals(Period.All, viewModel.state.value.period)
        }

    @Test
    fun `custom route timestamps are decoded into a local date range`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val fixture = Fixtures(mainDispatcherRule.testDispatcher)
            fixture.seedAccountData()
            val from = Instant.parse("2026-07-01T00:00:00Z").toEpochMilli()
            val to = Instant.parse("2026-07-07T23:59:59Z").toEpochMilli()

            val viewModel = buildViewModel(defaultArgs(from = from, to = to), fixture)
            advanceUntilIdle()

            assertEquals(
                Period.CustomRange(
                    Instant.ofEpochMilli(from).atZone(ZoneId.systemDefault()).toLocalDate(),
                    Instant.ofEpochMilli(to).atZone(ZoneId.systemDefault()).toLocalDate(),
                ),
                viewModel.state.value.period,
            )
        }

    @Test
    fun `load maps operation and transfer records to immutable resolved rows`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val fixture = Fixtures(mainDispatcherRule.testDispatcher)
            fixture.seedAccountData()
            fixture.categoryRepository.seed(fixture.food)
            fixture.transactionRepository.seedCategoryGroups(
                fixture.cash.id,
                CategoryGroup(
                    categoryId = fixture.food.id,
                    name = fixture.food.name,
                    iconKey = fixture.food.iconKey,
                    colorHex = fixture.food.colorHex,
                    kind = fixture.food.kind,
                    total = BigDecimal("12.50"),
                    count = 1,
                    textColorHex = fixture.food.textColor,
                ),
            )
            fixture.transactionRepository.seedPeriodTransactions(
                fixture.cash.id,
                fixture.operation(categoryId = fixture.food.id, note = "Lunch"),
            )
            fixture.transactionRepository.seedTransfers(
                fixture.cash.id,
                TransferRow(
                    id = 22L,
                    fromAccountName = "Cash",
                    toAccountName = "Card",
                    amount = BigDecimal("5.00"),
                    toAmount = null,
                    currencyId = fixture.usd.id,
                    occurredAt = Instant.parse("2026-07-01T13:00:00Z"),
                    note = null,
                ),
            )

            val viewModel = buildViewModel(defaultArgs(accountId = fixture.cash.id), fixture)
            advanceUntilIdle()

            val state = viewModel.state.value
            assertFalse(state.isLoading)
            assertEquals(listOf(22L, 11L), state.records.map { it.record.id })
            assertTrue(state.records is ImmutableList<*>)
            val operation = state.records.single { it.record.id == 11L }
            assertEquals(fixture.usd, operation.currency)
            assertEquals(
                TransactionCategoryDisplay(fixture.food.name, fixture.food.iconKey),
                operation.categoryDisplay,
            )
            assertNull(state.records.single { it.record.id == 22L }.categoryDisplay)
        }

    @Test
    fun `currency route loads all active accounts in that currency`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val fixture = Fixtures(mainDispatcherRule.testDispatcher)
            fixture.currencyRepository.seed(fixture.usd)
            fixture.accountRepository.seed(fixture.cash, fixture.card)
            fixture.categoryRepository.seed(fixture.food, fixture.salary)
            fixture.transactionRepository.seedCategoryGroups(
                fixture.cash.id,
                fixture.group(fixture.food),
            )
            fixture.transactionRepository.seedCategoryGroups(
                fixture.card.id,
                fixture.group(fixture.salary),
            )
            fixture.transactionRepository.seedPeriodTransactions(
                fixture.cash.id,
                fixture.operation(id = 31L, categoryId = fixture.food.id),
            )
            fixture.transactionRepository.seedPeriodTransactions(
                fixture.card.id,
                fixture.operation(id = 32L, categoryId = fixture.salary.id, accountId = fixture.card.id),
            )

            val viewModel =
                buildViewModel(
                    defaultArgs(currencyId = fixture.usd.id),
                    fixture,
                )
            advanceUntilIdle()

            assertEquals(fixture.usd.id, viewModel.state.value.currencyId)
            assertEquals(
                listOf(32L, 31L),
                viewModel.state.value.records
                    .map { it.record.id },
            )
        }

    @Test
    fun `clearing a category filter reloads the unfiltered list`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val fixture = Fixtures(mainDispatcherRule.testDispatcher)
            fixture.seedAccountData()
            fixture.categoryRepository.seed(fixture.food)
            fixture.transactionRepository.seedCategoryGroups(
                fixture.cash.id,
                fixture.group(fixture.food),
            )
            fixture.transactionRepository.seedPeriodTransactions(
                fixture.cash.id,
                fixture.operation(categoryId = fixture.food.id),
            )
            fixture.transactionRepository.seedTransfers(
                fixture.cash.id,
                TransferRow(
                    id = 44L,
                    fromAccountName = "Cash",
                    toAccountName = "Card",
                    amount = BigDecimal("3.00"),
                    toAmount = null,
                    currencyId = fixture.usd.id,
                    occurredAt = Instant.parse("2026-07-01T13:00:00Z"),
                    note = null,
                ),
            )

            val viewModel =
                buildViewModel(
                    defaultArgs(accountId = fixture.cash.id, categoryId = fixture.food.id),
                    fixture,
                )
            advanceUntilIdle()
            assertTrue(viewModel.state.value.hasCategoryFilter)
            assertEquals(
                listOf(11L),
                viewModel.state.value.records
                    .map { it.record.id },
            )

            viewModel.onEvent(TransactionsListEvent.CategoryFilterCleared)
            advanceUntilIdle()

            assertNull(viewModel.state.value.categoryId)
            assertNull(viewModel.state.value.categoryName)
            assertFalse(viewModel.state.value.hasCategoryFilter)
            assertEquals(
                listOf(44L, 11L),
                viewModel.state.value.records
                    .map { it.record.id },
            )
        }

    @Test
    fun `row click emits the detail action with the selected id`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val fixture = Fixtures(mainDispatcherRule.testDispatcher)
            fixture.seedAccountData()
            val viewModel = buildViewModel(defaultArgs(accountId = fixture.cash.id), fixture)
            advanceUntilIdle()

            viewModel.actions.test {
                viewModel.onEvent(TransactionsListEvent.RowClicked(91L))
                assertEquals(TransactionsListAction.OpenDetail(91L), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `transaction changes trigger a fresh immutable projection`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val fixture = Fixtures(mainDispatcherRule.testDispatcher)
            fixture.seedAccountData()
            fixture.categoryRepository.seed(fixture.food)
            fixture.transactionRepository.seedCategoryGroups(
                fixture.cash.id,
                fixture.group(fixture.food),
            )
            fixture.transactionRepository.seedPeriodTransactions(
                fixture.cash.id,
                fixture.operation(categoryId = fixture.food.id, note = "Before"),
            )
            fixture.transactionRepository.seed(fixture.operation(categoryId = fixture.food.id, note = "Before"))

            val viewModel = buildViewModel(defaultArgs(accountId = fixture.cash.id), fixture)
            advanceUntilIdle()
            val initial =
                viewModel.state.value.records
                    .single()
                    .record as SummaryRecord.Operation
            assertEquals("Before", initial.note)

            fixture.transactionRepository.seedPeriodTransactions(
                fixture.cash.id,
                fixture.operation(categoryId = fixture.food.id, note = "After"),
            )
            fixture.transactionRepository.triggerRecentChange()
            advanceUntilIdle()

            val refreshed =
                viewModel.state.value.records
                    .single()
                    .record as SummaryRecord.Operation
            assertEquals("After", refreshed.note)
            assertTrue(viewModel.state.value.records is ImmutableList<*>)
        }

    private fun buildViewModel(
        savedStateHandle: SavedStateHandle,
        fixture: Fixtures,
    ): TransactionsListViewModel =
        TransactionsListViewModel(
            getOperationsSummary = fixture.getOperationsSummary,
            accountRepository = fixture.accountRepository,
            currencyRepository = fixture.currencyRepository,
            categoryRepository = fixture.categoryRepository,
            transactionRepository = fixture.transactionRepository,
            savedStateHandle = savedStateHandle,
        )

    private fun defaultArgs(
        accountId: Long = -1L,
        currencyId: Long = -1L,
        categoryId: Long = -1L,
        from: Long = -1L,
        to: Long = -1L,
    ): SavedStateHandle =
        SavedStateHandle(
            mapOf(
                TransactionsListViewModel.KEY_ACCOUNT_ID to accountId,
                TransactionsListViewModel.KEY_CURRENCY_ID to currencyId,
                TransactionsListViewModel.KEY_CATEGORY_ID to categoryId,
                TransactionsListViewModel.KEY_FROM to from,
                TransactionsListViewModel.KEY_TO to to,
            ),
        )

    private class Fixtures(
        dispatcher: kotlinx.coroutines.CoroutineDispatcher,
    ) {
        val accountRepository = FakeAccountRepository()
        val currencyRepository = FakeCurrencyRepository()
        val categoryRepository = FakeCategoryRepository()
        val transactionRepository = FakeTransactionRepository()
        val getOperationsSummary =
            GetOperationsSummaryUseCase(
                getCategoryRecords =
                    GetCategoryRecordsUseCase(
                        accountRepository = accountRepository,
                        currencyRepository = currencyRepository,
                        transactionRepository = transactionRepository,
                        defaultDispatcher = dispatcher,
                    ),
                getTransferRecords =
                    GetTransferRecordsUseCase(
                        currencyRepository = currencyRepository,
                        transactionRepository = transactionRepository,
                        defaultDispatcher = dispatcher,
                    ),
            )

        val usd =
            Currency(
                id = 1L,
                code = "USD",
                symbol = "$",
                name = "US Dollar",
                decimalDigits = 2,
                isActive = true,
                sortOrder = 0,
            )
        val cash = account(1L, "Cash", isDefault = true)
        val card = account(2L, "Card")
        val food = category(10L, "Food", CategoryKind.Expense, "ic_cat_food")
        val salary = category(20L, "Salary", CategoryKind.Income, "ic_cat_salary")

        fun seedAccountData() {
            currencyRepository.seed(usd)
            accountRepository.seed(cash)
        }

        fun group(category: Category): CategoryGroup =
            CategoryGroup(
                categoryId = category.id,
                name = category.name,
                iconKey = category.iconKey,
                colorHex = category.colorHex,
                kind = category.kind,
                total = BigDecimal("12.50"),
                count = 1,
                textColorHex = category.textColor,
            )

        fun operation(
            id: Long = 11L,
            categoryId: Long?,
            note: String? = null,
            accountId: Long = cash.id,
        ): Transaction {
            val timestamp = Instant.parse("2026-07-01T12:00:00Z")
            return Transaction(
                id = id,
                kind = TransactionKind.Expense,
                amount = BigDecimal("12.50"),
                currencyId = usd.id,
                accountId = accountId,
                categoryId = categoryId,
                note = note,
                occurredAt = timestamp,
                createdAt = timestamp,
                updatedAt = timestamp,
                isDeleted = false,
                toAccountId = null,
                toAmount = null,
                exchangeRate = null,
            )
        }

        private fun account(
            id: Long,
            name: String,
            isDefault: Boolean = false,
        ) =
            Account(
                id = id,
                name = name,
                currencyId = usd.id,
                initialBalance = BigDecimal.ZERO,
                type = AccountType.Cash,
                colorHex = "#7AC794",
                iconKey = "ic_acc_cash",
                isDefault = isDefault,
                sortOrder = id.toInt(),
                createdAt = Instant.parse("2026-01-01T00:00:00Z"),
                updatedAt = Instant.parse("2026-01-01T00:00:00Z"),
                isArchived = false,
            )

        private fun category(
            id: Long,
            name: String,
            kind: CategoryKind,
            iconKey: String,
        ) =
            Category(
                id = id,
                name = name,
                kind = kind,
                iconKey = iconKey,
                colorHex = "#E07AAE",
                textColor = "#FFFFFF",
                sortOrder = id.toInt(),
                isDefault = false,
                isArchived = false,
                createdAt = Instant.parse("2026-01-01T00:00:00Z"),
            )
    }
}
