package com.kshavrin.mymoney.feature.dictionaries.accounts

import app.cash.turbine.test
import com.kshavrin.mymoney.core.domain.model.Account
import com.kshavrin.mymoney.core.domain.model.AccountType
import com.kshavrin.mymoney.core.domain.model.Currency
import com.kshavrin.mymoney.core.domain.usecase.BalanceCalculator
import com.kshavrin.mymoney.feature.dictionaries.currencies.fake.FakeAccountRepository
import com.kshavrin.mymoney.feature.dictionaries.currencies.fake.FakeCurrencyRepository
import com.kshavrin.mymoney.feature.dictionaries.currencies.fake.FakeTransactionRepository
import com.kshavrin.mymoney.feature.dictionaries.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class AccountsListViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var accountRepo: FakeAccountRepository
    private lateinit var currencyRepo: FakeCurrencyRepository
    private lateinit var transactionRepo: FakeTransactionRepository
    private lateinit var balanceCalculator: BalanceCalculator
    private lateinit var viewModel: AccountsListViewModel

    private val now: Instant = Instant.parse("2026-05-20T10:00:00Z")

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

    @Before
    fun setUp() {
        accountRepo = FakeAccountRepository()
        currencyRepo = FakeCurrencyRepository()
        transactionRepo = FakeTransactionRepository()
        currencyRepo.seed(usd)
        balanceCalculator = BalanceCalculator(accountRepo, currencyRepo, transactionRepo, UnconfinedTestDispatcher())
        viewModel =
            AccountsListViewModel(
                accountRepository = accountRepo,
                currencyRepository = currencyRepo,
                balanceCalculator = balanceCalculator,
            )
    }

    private fun account(
        id: Long,
        name: String = "Account $id",
        sortOrder: Int = id.toInt(),
        isArchived: Boolean = false,
    ): Account =
        Account(
            id = id,
            name = name,
            currencyId = 1L,
            initialBalance = BigDecimal.ZERO,
            type = AccountType.Cash,
            colorHex = "#7AC794",
            iconKey = "ic_account_cash",
            isDefault = false,
            sortOrder = sortOrder,
            createdAt = now,
            updatedAt = now,
            isArchived = isArchived,
        )

    // --- initial state ---

    @Test
    fun `initial state has empty rows`() =
        runTest {
            viewModel.state.test {
                val state = awaitItem()
                assertTrue(state.rows.isEmpty())
                cancelAndIgnoreRemainingEvents()
            }
        }

    // --- loading: N accounts become N rows ---

    @Test
    fun `three active accounts produce three rows`() =
        runTest {
            accountRepo.seed(account(1L), account(2L), account(3L))

            viewModel.state.test {
                val state = expectMostRecentItem()
                assertEquals(3, state.rows.size)
                cancelAndIgnoreRemainingEvents()
            }
        }

    // --- sorting by sortOrder ---

    @Test
    fun `rows are sorted by account sortOrder ascending`() =
        runTest {
            accountRepo.seed(
                account(id = 10L, sortOrder = 2),
                account(id = 20L, sortOrder = 0),
                account(id = 30L, sortOrder = 1),
            )

            viewModel.state.test {
                val state = expectMostRecentItem()
                assertEquals(listOf(20L, 30L, 10L), state.rows.map { it.account.id })
                cancelAndIgnoreRemainingEvents()
            }
        }

    // --- archived accounts are excluded ---

    @Test
    fun `archived accounts are excluded from rows`() =
        runTest {
            accountRepo.seed(
                account(id = 1L, isArchived = false),
                account(id = 2L, isArchived = true),
                account(id = 3L, isArchived = false),
            )

            viewModel.state.test {
                val state = expectMostRecentItem()
                assertEquals(listOf(1L, 3L), state.rows.map { it.account.id })
                cancelAndIgnoreRemainingEvents()
            }
        }

    // --- currency is resolved for each row ---

    @Test
    fun `row currency matches the account currencyId`() =
        runTest {
            accountRepo.seed(account(id = 5L))

            viewModel.state.test {
                val state = expectMostRecentItem()
                assertEquals(1, state.rows.size)
                assertEquals(
                    1L,
                    state.rows
                        .first()
                        .currency
                        ?.id,
                )
                assertEquals(
                    "USD",
                    state.rows
                        .first()
                        .currency
                        ?.code,
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `row currency is null when currency not found`() =
        runTest {
            accountRepo.seed(
                account(id = 8L).copy(currencyId = 999L),
            )

            viewModel.state.test {
                val state = expectMostRecentItem()
                assertEquals(1, state.rows.size)
                assertEquals(null, state.rows.first().currency)
                cancelAndIgnoreRemainingEvents()
            }
        }

    // --- reactive: state updates when repository emits ---

    @Test
    fun `rows update when account repository emits new list`() =
        runTest {
            viewModel.state.test {
                awaitItem()

                accountRepo.seed(account(id = 1L, name = "Wallet"))
                val after = awaitItem()
                assertEquals(1, after.rows.size)
                assertEquals(
                    "Wallet",
                    after.rows
                        .first()
                        .account.name,
                )

                cancelAndIgnoreRemainingEvents()
            }
        }

    // --- navigation actions ---

    @Test
    fun `AddClicked emits NavigateAdd action`() =
        runTest {
            viewModel.actions.test {
                viewModel.onEvent(AccountsListEvent.AddClicked)

                assertEquals(AccountsListAction.NavigateAdd, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `ItemClicked emits NavigateEdit with correct id`() =
        runTest {
            accountRepo.seed(account(id = 42L))

            viewModel.actions.test {
                viewModel.onEvent(AccountsListEvent.ItemClicked(42L))

                val action = awaitItem()
                assertTrue("expected NavigateEdit but was $action", action is AccountsListAction.NavigateEdit)
                assertEquals(42L, (action as AccountsListAction.NavigateEdit).id)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `BackClicked emits NavigateBack action`() =
        runTest {
            viewModel.actions.test {
                viewModel.onEvent(AccountsListEvent.BackClicked)

                assertEquals(AccountsListAction.NavigateBack, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    // --- balance defaults to zero when calculator throws (account not in currency repo) ---

    @Test
    fun `balance is zero for account whose currency is absent from currency repository`() =
        runTest {
            accountRepo.seed(account(id = 7L).copy(currencyId = 99L))

            viewModel.state.test {
                val state = expectMostRecentItem()
                assertEquals(1, state.rows.size)
                assertEquals(BigDecimal.ZERO, state.rows.first().balance)
                cancelAndIgnoreRemainingEvents()
            }
        }

    // --- empty repository keeps state empty ---

    @Test
    fun `empty repository keeps rows empty after advanceUntilIdle`() =
        runTest {
            advanceUntilIdle()

            viewModel.state.test {
                val state = expectMostRecentItem()
                assertTrue(state.rows.isEmpty())
                cancelAndIgnoreRemainingEvents()
            }
        }
}
