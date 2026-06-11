package com.kshavrin.mymoney.feature.dictionaries.accounts

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.kshavrin.mymoney.core.domain.model.Account
import com.kshavrin.mymoney.core.domain.model.AccountType
import com.kshavrin.mymoney.core.domain.model.Currency
import com.kshavrin.mymoney.core.domain.repository.AccountRepository
import com.kshavrin.mymoney.feature.dictionaries.currencies.fake.FakeAccountRepository
import com.kshavrin.mymoney.feature.dictionaries.currencies.fake.FakeCurrencyRepository
import com.kshavrin.mymoney.feature.dictionaries.currencies.fake.FakeTransactionRepository
import com.kshavrin.mymoney.feature.dictionaries.util.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal

class AccountEditViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var accountRepo: FakeAccountRepository
    private lateinit var currencyRepo: FakeCurrencyRepository
    private lateinit var transactionRepo: FakeTransactionRepository

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
    ): AccountEditViewModel = AccountEditViewModel(
        accountRepository = accountRepository,
        currencyRepository = currencyRepo,
        transactionRepository = transactionRepo,
        savedStateHandle = SavedStateHandle(),
    )

    @Test
    fun `double SaveClicked performs one upsert and emits one NavigateBack`() = runTest {
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
