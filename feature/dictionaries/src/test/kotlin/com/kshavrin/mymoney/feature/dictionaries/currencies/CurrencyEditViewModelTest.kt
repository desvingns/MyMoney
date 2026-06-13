package com.kshavrin.mymoney.feature.dictionaries.currencies

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.kshavrin.mymoney.core.domain.model.Account
import com.kshavrin.mymoney.core.domain.model.AccountType
import com.kshavrin.mymoney.core.domain.model.Currency
import com.kshavrin.mymoney.core.domain.repository.CurrencyRepository
import com.kshavrin.mymoney.feature.dictionaries.currencies.fake.FakeAccountRepository
import com.kshavrin.mymoney.feature.dictionaries.currencies.fake.FakeCurrencyRepository
import com.kshavrin.mymoney.feature.dictionaries.currencies.fake.FakeTransactionRepository
import com.kshavrin.mymoney.feature.dictionaries.util.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

class CurrencyEditViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var currencyRepo: FakeCurrencyRepository
    private lateinit var transactionRepo: FakeTransactionRepository
    private lateinit var accountRepo: FakeAccountRepository

    private val now: Instant = Instant.parse("2026-05-20T10:00:00Z")

    @Before
    fun setUp() {
        currencyRepo = FakeCurrencyRepository()
        transactionRepo = FakeTransactionRepository()
        accountRepo = FakeAccountRepository()
    }

    private fun usdCurrency(id: Long = 1L) =
        Currency(
            id = id,
            code = "USD",
            symbol = "$",
            name = "US Dollar",
            decimalDigits = 2,
            isActive = true,
            sortOrder = 0,
        )

    private fun account(
        id: Long,
        currencyId: Long,
        name: String = "Account $id",
    ) =
        Account(
            id = id,
            name = name,
            currencyId = currencyId,
            initialBalance = BigDecimal.ZERO,
            type = AccountType.Cash,
            colorHex = "#7AC794",
            iconKey = "ic_acc",
            isDefault = false,
            sortOrder = 0,
            createdAt = now,
            updatedAt = now,
            isArchived = false,
        )

    private fun buildViewModel(
        currencyId: Long,
        currencyRepository: CurrencyRepository = currencyRepo,
    ): CurrencyEditViewModel =
        CurrencyEditViewModel(
            currencyRepository = currencyRepository,
            transactionRepository = transactionRepo,
            accountRepository = accountRepo,
            savedStateHandle = SavedStateHandle(mapOf("id" to currencyId)),
        )

    @Test
    fun `create mode keeps isCodeLocked false and dependentAccountCount zero`() =
        runTest {
            val viewModel = buildViewModel(currencyId = -1L)

            viewModel.state.test {
                val state = expectMostRecentItem()
                assertTrue(state.isCreateMode)
                assertFalse(state.isCodeLocked)
                assertEquals(0, state.dependentAccountCount)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `create mode allows CodeChanged to update code`() =
        runTest {
            val viewModel = buildViewModel(currencyId = -1L)

            viewModel.onEvent(CurrencyEditEvent.CodeChanged("eur"))

            viewModel.state.test {
                val state = expectMostRecentItem()
                assertEquals("EUR", state.code)
                assertFalse(state.isCodeLocked)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `edit mode with zero dependent accounts leaves isCodeLocked false`() =
        runTest {
            currencyRepo.seed(usdCurrency(id = 1L))

            val viewModel = buildViewModel(currencyId = 1L)

            viewModel.state.test {
                val state = expectMostRecentItem()
                assertFalse(state.isCreateMode)
                assertFalse(state.isCodeLocked)
                assertEquals(0, state.dependentAccountCount)
                assertEquals("USD", state.code)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `edit mode with zero dependent accounts allows CodeChanged to update code`() =
        runTest {
            currencyRepo.seed(usdCurrency(id = 1L))

            val viewModel = buildViewModel(currencyId = 1L)

            viewModel.onEvent(CurrencyEditEvent.CodeChanged("gbp"))

            viewModel.state.test {
                val state = expectMostRecentItem()
                assertEquals("GBP", state.code)
                assertFalse(state.isCodeLocked)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `edit mode with two dependent accounts sets isCodeLocked true and counts two`() =
        runTest {
            currencyRepo.seed(usdCurrency(id = 1L))
            accountRepo.seed(
                account(id = 10L, currencyId = 1L),
                account(id = 11L, currencyId = 1L),
            )

            val viewModel = buildViewModel(currencyId = 1L)

            viewModel.state.test {
                val state = expectMostRecentItem()
                assertFalse(state.isCreateMode)
                assertTrue(state.isCodeLocked)
                assertEquals(2, state.dependentAccountCount)
                assertEquals("USD", state.code)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `edit mode locked ignores CodeChanged and keeps loaded code`() =
        runTest {
            currencyRepo.seed(usdCurrency(id = 1L))
            accountRepo.seed(
                account(id = 10L, currencyId = 1L),
                account(id = 11L, currencyId = 1L),
            )

            val viewModel = buildViewModel(currencyId = 1L)

            viewModel.onEvent(CurrencyEditEvent.CodeChanged("eur"))

            viewModel.state.test {
                val state = expectMostRecentItem()
                assertEquals("USD", state.code)
                assertTrue(state.isCodeLocked)
                assertEquals(2, state.dependentAccountCount)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `save validates 3-letter regex even when code is unlocked`() =
        runTest {
            val viewModel = buildViewModel(currencyId = -1L)

            viewModel.onEvent(CurrencyEditEvent.CodeChanged("us"))
            viewModel.onEvent(CurrencyEditEvent.SymbolChanged("$"))
            viewModel.onEvent(CurrencyEditEvent.NameChanged("US Dollar"))
            viewModel.onEvent(CurrencyEditEvent.SaveClicked)

            viewModel.state.test {
                val state = expectMostRecentItem()
                assertEquals("code_format", state.errorMessage)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `save validates 3-letter regex even when code is locked`() =
        runTest {
            currencyRepo.seed(
                Currency(
                    id = 1L,
                    code = "US",
                    symbol = "$",
                    name = "Bad",
                    decimalDigits = 2,
                    isActive = true,
                    sortOrder = 0,
                ),
            )
            accountRepo.seed(
                account(id = 10L, currencyId = 1L),
                account(id = 11L, currencyId = 1L),
            )

            val viewModel = buildViewModel(currencyId = 1L)

            viewModel.onEvent(CurrencyEditEvent.SaveClicked)

            viewModel.state.test {
                val state = expectMostRecentItem()
                assertTrue(state.isCodeLocked)
                assertEquals("code_format", state.errorMessage)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `archived accounts are not counted as dependents`() =
        runTest {
            currencyRepo.seed(usdCurrency(id = 1L))
            accountRepo.seed(
                account(id = 10L, currencyId = 1L),
                account(id = 11L, currencyId = 1L).copy(isArchived = true),
            )

            val viewModel = buildViewModel(currencyId = 1L)

            viewModel.state.test {
                val state = expectMostRecentItem()
                assertEquals(1, state.dependentAccountCount)
                assertTrue(state.isCodeLocked)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `accounts of other currencies do not lock the code`() =
        runTest {
            currencyRepo.seed(usdCurrency(id = 1L))
            accountRepo.seed(
                account(id = 10L, currencyId = 2L),
                account(id = 11L, currencyId = 3L),
            )

            val viewModel = buildViewModel(currencyId = 1L)

            viewModel.state.test {
                val state = expectMostRecentItem()
                assertEquals(0, state.dependentAccountCount)
                assertFalse(state.isCodeLocked)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `double SaveClicked performs one upsert and emits one NavigateBack`() =
        runTest {
            val blockingRepo = BlockingCurrencyRepository()
            val viewModel = buildViewModel(currencyId = -1L, currencyRepository = blockingRepo)

            viewModel.onEvent(CurrencyEditEvent.CodeChanged("usd"))
            viewModel.onEvent(CurrencyEditEvent.SymbolChanged("$"))
            viewModel.onEvent(CurrencyEditEvent.NameChanged("US Dollar"))

            viewModel.actions.test {
                viewModel.onEvent(CurrencyEditEvent.SaveClicked)
                assertTrue(viewModel.state.value.isSaving)
                viewModel.onEvent(CurrencyEditEvent.SaveClicked)

                assertEquals(1, blockingRepo.startedUpserts.size)

                blockingRepo.release()
                advanceUntilIdle()

                assertEquals(1, blockingRepo.persistedUpserts.size)
                assertEquals(CurrencyEditAction.NavigateBack, awaitItem())
                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
        }

    private class BlockingCurrencyRepository(
        private val delegate: FakeCurrencyRepository = FakeCurrencyRepository(),
    ) : CurrencyRepository by delegate {
        val startedUpserts: MutableList<Currency> = mutableListOf()
        val persistedUpserts: MutableList<Currency> = mutableListOf()
        private val gate = CompletableDeferred<Unit>()

        override suspend fun upsert(currency: Currency): Long {
            startedUpserts += currency
            gate.await()
            val id = delegate.upsert(currency)
            persistedUpserts += currency.copy(id = id)
            return id
        }

        fun release() {
            if (!gate.isCompleted) {
                gate.complete(Unit)
            }
        }
    }
}
