package com.kshavrin.mymoney.feature.dictionaries.accounts

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.kshavrin.mymoney.core.domain.model.Account
import com.kshavrin.mymoney.core.domain.model.AccountType
import com.kshavrin.mymoney.core.domain.model.Currency
import com.kshavrin.mymoney.core.domain.model.Transaction
import com.kshavrin.mymoney.core.domain.model.TransactionKind
import com.kshavrin.mymoney.core.domain.repository.AccountRepository
import com.kshavrin.mymoney.feature.dictionaries.currencies.fake.FakeAccountRepository
import com.kshavrin.mymoney.feature.dictionaries.currencies.fake.FakeCurrencyRepository
import com.kshavrin.mymoney.feature.dictionaries.currencies.fake.FakeTransactionRepository
import com.kshavrin.mymoney.feature.dictionaries.util.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
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
import java.util.Locale

class AccountEditViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var accountRepo: FakeAccountRepository
    private lateinit var currencyRepo: FakeCurrencyRepository
    private lateinit var transactionRepo: FakeTransactionRepository

    private val now: Instant = Instant.parse("2026-05-20T10:00:00Z")

    @Before
    fun setUp() {
        accountRepo = FakeAccountRepository()
        currencyRepo = FakeCurrencyRepository()
        transactionRepo = FakeTransactionRepository()
        currencyRepo.seed(
            Currency(
                id = 1L,
                code = "USD",
                symbol = "$",
                name = "US Dollar",
                decimalDigits = 2,
                isActive = true,
                sortOrder = 0,
            ),
        )
    }

    private fun buildViewModel(
        accountRepository: AccountRepository = accountRepo,
        accountId: Long = -1L,
    ): AccountEditViewModel =
        AccountEditViewModel(
            accountRepository = accountRepository,
            currencyRepository = currencyRepo,
            transactionRepository = transactionRepo,
            savedStateHandle = if (accountId == -1L) SavedStateHandle() else SavedStateHandle(mapOf("id" to accountId)),
        )

    private fun savedAccount(
        id: Long,
        name: String = "Cash",
        initialBalance: BigDecimal = BigDecimal("100.00"),
    ): Account =
        Account(
            id = id,
            name = name,
            currencyId = 1L,
            initialBalance = initialBalance,
            type = AccountType.Cash,
            colorHex = "#7AC794",
            iconKey = "ic_account_cash",
            isDefault = false,
            sortOrder = 0,
            createdAt = now,
            updatedAt = now,
            isArchived = false,
        )

    private fun transaction(
        id: Long,
        accountId: Long,
    ): Transaction =
        Transaction(
            id = id,
            accountId = accountId,
            toAccountId = null,
            toAmount = null,
            exchangeRate = null,
            categoryId = 1L,
            currencyId = 1L,
            amount = BigDecimal("10.00"),
            kind = TransactionKind.Expense,
            note = "",
            occurredAt = now,
            createdAt = now,
            updatedAt = now,
            isDeleted = false,
        )

    // --- create mode: initial state ---

    @Test
    fun `create mode initial state has isCreateMode true`() =
        runTest {
            val viewModel = buildViewModel()
            advanceUntilIdle()

            assertTrue(viewModel.state.value.isCreateMode)
        }

    @Test
    fun `create mode pre-fills currencyId from first active currency`() =
        runTest {
            val viewModel = buildViewModel()
            advanceUntilIdle()

            assertEquals(1L, viewModel.state.value.currencyId)
        }

    @Test
    fun `create mode initial state has availableCurrencies populated`() =
        runTest {
            val viewModel = buildViewModel()
            advanceUntilIdle()

            assertEquals(1, viewModel.state.value.availableCurrencies.size)
            assertEquals(
                "USD",
                viewModel.state.value.availableCurrencies
                    .first()
                    .code,
            )
        }

    // --- edit mode: loads existing account ---

    @Test
    fun `edit mode loads account fields into state`() =
        runTest {
            val originalLocale = Locale.getDefault()
            try {
                Locale.setDefault(Locale.US)
                accountRepo.seed(savedAccount(id = 7L, name = "Savings", initialBalance = BigDecimal("250.50")))
                val viewModel = buildViewModel(accountId = 7L)
                advanceUntilIdle()

                val state = viewModel.state.value
                assertEquals(false, state.isCreateMode)
                assertEquals("Savings", state.name)
                assertEquals("250.50", state.initialBalanceText)
                assertEquals(1L, state.currencyId)
            } finally {
                Locale.setDefault(originalLocale)
            }
        }

    @Test
    fun `edit mode displays an existing balance with the Russian decimal separator`() =
        runTest {
            val originalLocale = Locale.getDefault()
            try {
                Locale.setDefault(Locale.forLanguageTag("ru-RU"))
                accountRepo.seed(savedAccount(id = 7L, initialBalance = BigDecimal("250.50")))

                val viewModel = buildViewModel(accountId = 7L)
                advanceUntilIdle()

                assertEquals("250,50", viewModel.state.value.initialBalanceText)
            } finally {
                Locale.setDefault(originalLocale)
            }
        }

    // --- validation: empty name ---

    @Test
    fun `save with blank name sets error and does not upsert`() =
        runTest {
            val viewModel = buildViewModel()
            advanceUntilIdle()

            viewModel.onEvent(AccountEditEvent.InitialBalanceChanged("50"))
            viewModel.onEvent(AccountEditEvent.SaveClicked)
            advanceUntilIdle()

            assertEquals("name_required", viewModel.state.value.errorMessage)
            assertTrue(accountRepo.observeActive().first().isEmpty())
        }

    @Test
    fun `save with name longer than 32 characters sets error and does not upsert`() =
        runTest {
            val viewModel = buildViewModel()
            advanceUntilIdle()

            viewModel.onEvent(AccountEditEvent.NameChanged("A".repeat(33)))
            viewModel.onEvent(AccountEditEvent.InitialBalanceChanged("50"))
            viewModel.onEvent(AccountEditEvent.SaveClicked)
            advanceUntilIdle()

            assertEquals("name_required", viewModel.state.value.errorMessage)
            assertTrue(accountRepo.observeActive().first().isEmpty())
        }

    // --- validation: money "error instead of zero" path ---

    @Test
    fun `save with non-numeric balance sets balance_format error`() =
        runTest {
            val viewModel = buildViewModel()
            advanceUntilIdle()

            viewModel.onEvent(AccountEditEvent.NameChanged("Wallet"))
            viewModel.onEvent(AccountEditEvent.InitialBalanceChanged("abc"))
            viewModel.onEvent(AccountEditEvent.SaveClicked)
            advanceUntilIdle()

            assertEquals("balance_format", viewModel.state.value.errorMessage)
        }

    @Test
    fun `save with empty balance sets balance_format error`() =
        runTest {
            val viewModel = buildViewModel()
            advanceUntilIdle()

            viewModel.onEvent(AccountEditEvent.NameChanged("Wallet"))
            viewModel.onEvent(AccountEditEvent.InitialBalanceChanged(""))
            viewModel.onEvent(AccountEditEvent.SaveClicked)
            advanceUntilIdle()

            assertEquals("balance_format", viewModel.state.value.errorMessage)
        }

    @Test
    fun `save with non-numeric balance does not call upsert`() =
        runTest {
            val viewModel = buildViewModel()
            advanceUntilIdle()

            viewModel.onEvent(AccountEditEvent.NameChanged("Wallet"))
            viewModel.onEvent(AccountEditEvent.InitialBalanceChanged("not-a-number"))
            viewModel.onEvent(AccountEditEvent.SaveClicked)
            advanceUntilIdle()

            assertTrue(accountRepo.observeActive().first().isEmpty())
        }

    @Test
    fun `save with non-numeric balance does not emit NavigateBack`() =
        runTest {
            val viewModel = buildViewModel()
            advanceUntilIdle()

            viewModel.onEvent(AccountEditEvent.NameChanged("Wallet"))
            viewModel.onEvent(AccountEditEvent.InitialBalanceChanged("??"))

            viewModel.actions.test {
                viewModel.onEvent(AccountEditEvent.SaveClicked)
                advanceUntilIdle()
                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
        }

    // --- field change clears error ---

    @Test
    fun `NameChanged clears existing errorMessage`() =
        runTest {
            val viewModel = buildViewModel()
            advanceUntilIdle()

            viewModel.onEvent(AccountEditEvent.SaveClicked)
            advanceUntilIdle()
            assertNotNull(viewModel.state.value.errorMessage)

            viewModel.onEvent(AccountEditEvent.NameChanged("Fixed"))
            assertNull(viewModel.state.value.errorMessage)
        }

    @Test
    fun `InitialBalanceChanged clears existing errorMessage`() =
        runTest {
            val viewModel = buildViewModel()
            advanceUntilIdle()

            viewModel.onEvent(AccountEditEvent.NameChanged("Wallet"))
            viewModel.onEvent(AccountEditEvent.InitialBalanceChanged("bad"))
            viewModel.onEvent(AccountEditEvent.SaveClicked)
            advanceUntilIdle()
            assertNotNull(viewModel.state.value.errorMessage)

            viewModel.onEvent(AccountEditEvent.InitialBalanceChanged("0"))
            assertNull(viewModel.state.value.errorMessage)
        }

    // --- successful save (create) ---

    @Test
    fun `valid name and balance emits NavigateBack and persists account`() =
        runTest {
            val viewModel = buildViewModel()
            advanceUntilIdle()

            viewModel.onEvent(AccountEditEvent.NameChanged("Wallet"))
            viewModel.onEvent(AccountEditEvent.InitialBalanceChanged("99.99"))

            viewModel.actions.test {
                viewModel.onEvent(AccountEditEvent.SaveClicked)
                advanceUntilIdle()

                assertEquals(AccountEditAction.NavigateBack, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }

            val persisted = accountRepo.observeActive().first()
            assertEquals(1, persisted.size)
            assertEquals("Wallet", persisted.first().name)
        }

    // --- setDefault called when isDefault true ---

    @Test
    fun `save with isDefault true marks account as default in repository`() =
        runTest {
            val viewModel = buildViewModel()
            advanceUntilIdle()

            viewModel.onEvent(AccountEditEvent.NameChanged("Primary"))
            viewModel.onEvent(AccountEditEvent.InitialBalanceChanged("0"))
            viewModel.onEvent(AccountEditEvent.IsDefaultChanged(true))

            viewModel.actions.test {
                viewModel.onEvent(AccountEditEvent.SaveClicked)
                advanceUntilIdle()
                awaitItem()
                cancelAndIgnoreRemainingEvents()
            }

            val savedAccounts = accountRepo.observeActive().first()
            assertEquals(1, savedAccounts.size)
            assertTrue(savedAccounts.first().isDefault)
        }

    // --- archive / delete ---

    @Test
    fun `DeleteClicked on account without transactions archives and emits NavigateBack`() =
        runTest {
            accountRepo.seed(savedAccount(id = 3L))
            val viewModel = buildViewModel(accountId = 3L)
            advanceUntilIdle()

            viewModel.actions.test {
                viewModel.onEvent(AccountEditEvent.DeleteClicked)
                advanceUntilIdle()

                assertEquals(AccountEditAction.NavigateBack, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }

            val account = accountRepo.observeActive().first().first { it.id == 3L }
            assertTrue(account.isArchived)
        }

    @Test
    fun `DeleteClicked on account with transactions sets blockedDeleteCount`() =
        runTest {
            accountRepo.seed(savedAccount(id = 4L))
            transactionRepo.seed(transaction(id = 1L, accountId = 4L), transaction(id = 2L, accountId = 4L))
            val viewModel = buildViewModel(accountId = 4L)
            advanceUntilIdle()

            viewModel.onEvent(AccountEditEvent.DeleteClicked)
            advanceUntilIdle()

            assertEquals(2, viewModel.state.value.blockedDeleteCount)
        }

    @Test
    fun `DeleteClicked on account with transactions does not archive the account`() =
        runTest {
            accountRepo.seed(savedAccount(id = 5L))
            transactionRepo.seed(transaction(id = 10L, accountId = 5L))
            val viewModel = buildViewModel(accountId = 5L)
            advanceUntilIdle()

            viewModel.onEvent(AccountEditEvent.DeleteClicked)
            advanceUntilIdle()

            val account = accountRepo.observeActive().first().first { it.id == 5L }
            assertTrue(!account.isArchived)
        }

    @Test
    fun `BlockedDeleteDismissed clears blockedDeleteCount`() =
        runTest {
            accountRepo.seed(savedAccount(id = 6L))
            transactionRepo.seed(transaction(id = 11L, accountId = 6L))
            val viewModel = buildViewModel(accountId = 6L)
            advanceUntilIdle()

            viewModel.onEvent(AccountEditEvent.DeleteClicked)
            advanceUntilIdle()
            assertNotNull(viewModel.state.value.blockedDeleteCount)

            viewModel.onEvent(AccountEditEvent.BlockedDeleteDismissed)
            assertNull(viewModel.state.value.blockedDeleteCount)
        }

    @Test
    fun `DeleteClicked in create mode emits no actions`() =
        runTest {
            val viewModel = buildViewModel()
            advanceUntilIdle()

            viewModel.actions.test {
                viewModel.onEvent(AccountEditEvent.DeleteClicked)
                advanceUntilIdle()
                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
        }

    // --- BackClicked ---

    @Test
    fun `BackClicked emits NavigateBack`() =
        runTest {
            val viewModel = buildViewModel()

            viewModel.actions.test {
                viewModel.onEvent(AccountEditEvent.BackClicked)

                assertEquals(AccountEditAction.NavigateBack, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    // --- comma as decimal separator in initial balance ---

    @Test
    fun `initial balance with comma separator is accepted and saved correctly`() =
        runTest {
            val viewModel = buildViewModel()
            advanceUntilIdle()

            viewModel.onEvent(AccountEditEvent.NameChanged("Wallet"))
            viewModel.onEvent(AccountEditEvent.InitialBalanceChanged("1500,75"))

            viewModel.actions.test {
                viewModel.onEvent(AccountEditEvent.SaveClicked)
                advanceUntilIdle()
                assertEquals(AccountEditAction.NavigateBack, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }

            val saved = accountRepo.observeActive().first().single()
            assertEquals(0, BigDecimal("1500.75").compareTo(saved.initialBalance))
        }

    @Test
    fun `initial balance with comma does not trigger balance_format error`() =
        runTest {
            val viewModel = buildViewModel()
            advanceUntilIdle()

            viewModel.onEvent(AccountEditEvent.NameChanged("Wallet"))
            viewModel.onEvent(AccountEditEvent.InitialBalanceChanged("200,00"))
            viewModel.onEvent(AccountEditEvent.SaveClicked)
            advanceUntilIdle()

            assertNull(viewModel.state.value.errorMessage)
        }

    @Test
    fun `initial balance rounds half up to the account currency scale on save`() =
        runTest {
            val viewModel = buildViewModel()
            advanceUntilIdle()

            viewModel.onEvent(AccountEditEvent.NameChanged("Wallet"))
            viewModel.onEvent(AccountEditEvent.InitialBalanceChanged("10.005"))

            viewModel.actions.test {
                viewModel.onEvent(AccountEditEvent.SaveClicked)
                advanceUntilIdle()
                assertEquals(AccountEditAction.NavigateBack, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }

            val saved = accountRepo.observeActive().first().single()
            assertEquals(0, BigDecimal("10.01").compareTo(saved.initialBalance))
        }

    @Test
    fun `initial balance trims extra fractional digits to the account currency scale on save`() =
        runTest {
            val viewModel = buildViewModel()
            advanceUntilIdle()

            viewModel.onEvent(AccountEditEvent.NameChanged("Wallet"))
            viewModel.onEvent(AccountEditEvent.InitialBalanceChanged("12.3456"))

            viewModel.actions.test {
                viewModel.onEvent(AccountEditEvent.SaveClicked)
                advanceUntilIdle()
                assertEquals(AccountEditAction.NavigateBack, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }

            val saved = accountRepo.observeActive().first().single()
            assertEquals(0, BigDecimal("12.35").compareTo(saved.initialBalance))
        }

    // --- createdAt stability on edit ---

    @Test
    fun `editing account preserves original createdAt on upsert`() =
        runTest {
            val originalCreatedAt = Instant.parse("2026-06-01T08:00:00Z")
            val originalAccount =
                savedAccount(id = 20L, name = "Old").copy(createdAt = originalCreatedAt)
            accountRepo.seed(originalAccount)
            val viewModel = buildViewModel(accountId = 20L)
            advanceUntilIdle()

            viewModel.onEvent(AccountEditEvent.NameChanged("Updated"))

            viewModel.actions.test {
                viewModel.onEvent(AccountEditEvent.SaveClicked)
                advanceUntilIdle()
                assertEquals(AccountEditAction.NavigateBack, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }

            val saved = accountRepo.observeActive().first().single { it.id == 20L }
            assertEquals(originalCreatedAt, saved.createdAt)
        }

    @Test
    fun `creating new account sets a non-null createdAt`() =
        runTest {
            val viewModel = buildViewModel()
            advanceUntilIdle()

            viewModel.onEvent(AccountEditEvent.NameChanged("Fresh"))
            viewModel.onEvent(AccountEditEvent.InitialBalanceChanged("0"))

            viewModel.actions.test {
                viewModel.onEvent(AccountEditEvent.SaveClicked)
                advanceUntilIdle()
                awaitItem()
                cancelAndIgnoreRemainingEvents()
            }

            val saved = accountRepo.observeActive().first().single()
            assertNotNull(saved.createdAt)
        }

    // --- double-save guard (pre-existing test preserved) ---

    @Test
    fun `double SaveClicked performs one upsert and emits one NavigateBack`() =
        runTest {
            val blockingRepo = BlockingAccountRepository()
            val viewModel = buildViewModel(accountRepository = blockingRepo)

            advanceUntilIdle()
            viewModel.onEvent(AccountEditEvent.NameChanged("Wallet"))
            viewModel.onEvent(AccountEditEvent.InitialBalanceChanged("25"))
            viewModel.onEvent(AccountEditEvent.TypeChanged(AccountType.Cash))

            viewModel.actions.test {
                viewModel.onEvent(AccountEditEvent.SaveClicked)
                assertTrue(viewModel.state.value.isSaving)
                viewModel.onEvent(AccountEditEvent.SaveClicked)

                assertEquals(1, blockingRepo.startedUpserts.size)

                blockingRepo.release()
                advanceUntilIdle()

                assertEquals(1, blockingRepo.persistedUpserts.size)
                assertEquals(AccountEditAction.NavigateBack, awaitItem())
                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
        }

    private class BlockingAccountRepository(
        private val delegate: FakeAccountRepository = FakeAccountRepository(),
    ) : AccountRepository by delegate {
        val startedUpserts: MutableList<Account> = mutableListOf()
        val persistedUpserts: MutableList<Account> = mutableListOf()
        private val gate = CompletableDeferred<Unit>()

        override suspend fun upsert(account: Account): Long {
            startedUpserts += account
            gate.await()
            val id = delegate.upsert(account)
            persistedUpserts += account.copy(id = id)
            return id
        }

        fun release() {
            if (!gate.isCompleted) {
                gate.complete(Unit)
            }
        }
    }
}
