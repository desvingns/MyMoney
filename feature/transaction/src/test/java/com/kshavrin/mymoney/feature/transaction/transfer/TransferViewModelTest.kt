package com.kshavrin.mymoney.feature.transaction.transfer

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import app.cash.turbine.test
import com.kshavrin.mymoney.core.domain.model.Account
import com.kshavrin.mymoney.core.domain.model.AccountType
import com.kshavrin.mymoney.core.domain.model.Currency
import com.kshavrin.mymoney.core.domain.model.CurrencyRate
import com.kshavrin.mymoney.core.domain.model.Transaction
import com.kshavrin.mymoney.core.domain.model.TransactionKind
import com.kshavrin.mymoney.core.domain.repository.CurrencyRateRepository
import com.kshavrin.mymoney.core.domain.repository.TransactionRepository
import com.kshavrin.mymoney.core.domain.usecase.ConvertMoneyUseCase
import com.kshavrin.mymoney.core.domain.usecase.ResolveRateUseCase
import com.kshavrin.mymoney.core.domain.usecase.TransferExecutor
import com.kshavrin.mymoney.feature.transaction.fake.FakeAccountRepository
import com.kshavrin.mymoney.feature.transaction.fake.FakeAppSettingsRepository
import com.kshavrin.mymoney.feature.transaction.fake.FakeCurrencyRepository
import com.kshavrin.mymoney.feature.transaction.fake.FakeTransactionRepository
import com.kshavrin.mymoney.feature.transaction.util.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
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
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.TimeZone

class TransferViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var originalTimeZone: TimeZone

    private val createdAt: Instant = Instant.parse("2026-05-20T10:00:00Z")

    private lateinit var transactionRepo: FakeTransactionRepository
    private lateinit var accountRepo: FakeAccountRepository
    private lateinit var currencyRepo: FakeCurrencyRepository
    private lateinit var rateRepo: FakeCurrencyRateRepository
    private lateinit var settingsRepo: FakeAppSettingsRepository

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

    private val eur =
        Currency(
            id = 2L,
            code = "EUR",
            symbol = "EUR",
            name = "Euro",
            decimalDigits = 2,
            isActive = true,
            sortOrder = 1,
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
            createdAt = createdAt,
            updatedAt = createdAt,
            isArchived = false,
        )

    private val bankAccount =
        Account(
            id = 2L,
            name = "Bank",
            currencyId = usd.id,
            initialBalance = BigDecimal.ZERO,
            type = AccountType.Card,
            colorHex = "#4E92DF",
            iconKey = "ic_acc_card",
            isDefault = false,
            sortOrder = 1,
            createdAt = createdAt,
            updatedAt = createdAt,
            isArchived = false,
        )

    private val euroAccount =
        Account(
            id = 3L,
            name = "Euro wallet",
            currencyId = eur.id,
            initialBalance = BigDecimal.ZERO,
            type = AccountType.Cash,
            colorHex = "#E1B12C",
            iconKey = "ic_acc_wallet",
            isDefault = false,
            sortOrder = 2,
            createdAt = createdAt,
            updatedAt = createdAt,
            isArchived = false,
        )

    @Before
    fun setUp() {
        originalTimeZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone(TEST_TIME_ZONE_ID))
        transactionRepo = FakeTransactionRepository()
        accountRepo = FakeAccountRepository()
        currencyRepo = FakeCurrencyRepository()
        rateRepo = FakeCurrencyRateRepository()
        settingsRepo = FakeAppSettingsRepository()

        currencyRepo.seed(usd)
        accountRepo.seed(cashAccount, bankAccount)
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(originalTimeZone)
    }

    private fun buildViewModel(
        transactionRepository: TransactionRepository = transactionRepo,
        clock: Clock = Clock.fixed(createdAt, ZoneId.of("UTC")),
    ): TransferViewModel =
        TransferViewModel(
            transactionRepository = transactionRepository,
            accountRepository = accountRepo,
            currencyRepository = currencyRepo,
            currencyRateRepository = rateRepo,
            transferExecutor =
                TransferExecutor(
                    accountRepository = accountRepo,
                    currencyRateRepository = rateRepo,
                    transactionRepository = transactionRepository,
                    defaultDispatcher = mainDispatcherRule.testDispatcher,
                ),
            resolveRate =
                ResolveRateUseCase(
                    currencyRateRepository = rateRepo,
                    currencyRepository = currencyRepo,
                    convertMoney = ConvertMoneyUseCase(),
                    clock = clock,
                    zoneId = ZoneId.systemDefault(),
                ),
            appSettingsRepository = settingsRepo,
            savedStateHandle = SavedStateHandle(),
        )

    private fun localMidnight(date: LocalDate): Instant =
        date.atStartOfDay(ZoneId.systemDefault()).toInstant()

    @Test
    fun `SaveClicked stores transfer occurredAt at local midnight in the system timezone`() =
        runTest {
            val viewModel = buildViewModel()
            val saveDate = LocalDate.parse("2026-06-10")

            advanceUntilIdle()
            viewModel.onEvent(TransferEvent.KeypadDigit(7))
            viewModel.onEvent(TransferEvent.DateChanged(saveDate))
            viewModel.onEvent(TransferEvent.TargetAccountChanged(bankAccount.id))
            viewModel.onEvent(TransferEvent.SaveClicked)
            advanceUntilIdle()

            val saved = transactionRepo.upserted.single()
            assertEquals(TransactionKind.Transfer, saved.kind)
            assertEquals(cashAccount.id, saved.accountId)
            assertEquals(bankAccount.id, saved.toAccountId)
            assertEquals(localMidnight(saveDate), saved.occurredAt)
            assertEquals(0, BigDecimal("7").compareTo(saved.amount))
            assertEquals(0, BigDecimal("7").compareTo(saved.toAmount))
        }

    @Test
    fun `double SaveClicked performs one transfer upsert and emits one NavigateBack`() =
        runTest {
            val blockingRepo = BlockingTransactionRepository()
            val viewModel = buildViewModel(transactionRepository = blockingRepo)

            advanceUntilIdle()
            viewModel.onEvent(TransferEvent.KeypadDigit(7))
            viewModel.onEvent(TransferEvent.TargetAccountChanged(bankAccount.id))

            viewModel.actions.test {
                viewModel.onEvent(TransferEvent.SaveClicked)
                assertEquals(true, viewModel.state.value.isSaving)
                viewModel.onEvent(TransferEvent.SaveClicked)

                assertEquals(1, blockingRepo.startedUpserts.size)

                blockingRepo.release()
                advanceUntilIdle()

                assertEquals(1, blockingRepo.persistedUpserts.size)
                assertEquals(TransferAction.NavigateBack, awaitItem())
                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `cancelling save does not show error banner or emit navigation`() =
        runTest {
            val blockingRepo = BlockingTransactionRepository()
            val viewModel = buildViewModel(transactionRepository = blockingRepo)

            advanceUntilIdle()
            viewModel.onEvent(TransferEvent.KeypadDigit(7))
            viewModel.onEvent(TransferEvent.TargetAccountChanged(bankAccount.id))

            viewModel.actions.test {
                viewModel.onEvent(TransferEvent.SaveClicked)
                assertEquals(true, viewModel.state.value.isSaving)
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
    fun `save failure keeps existing transfer error banner mapping`() =
        runTest {
            val viewModel = buildViewModel(transactionRepository = FailingTransactionRepository())

            advanceUntilIdle()
            viewModel.onEvent(TransferEvent.KeypadDigit(7))
            viewModel.onEvent(TransferEvent.TargetAccountChanged(bankAccount.id))
            viewModel.onEvent(TransferEvent.SaveClicked)
            advanceUntilIdle()

            assertEquals(false, viewModel.state.value.isSaving)
            assertEquals(com.kshavrin.mymoney.feature.transaction.R.string.error_save_failed, viewModel.state.value.errorBannerRes)
            assertEquals(0L, viewModel.state.value.savedSignal)
        }

    @Test
    fun `SaveClicked with zero amount shows enter amount error and skips persistence`() =
        runTest {
            val viewModel = buildViewModel()

            advanceUntilIdle()
            viewModel.onEvent(TransferEvent.TargetAccountChanged(bankAccount.id))
            viewModel.onEvent(TransferEvent.SaveClicked)
            advanceUntilIdle()

            assertEquals(
                com.kshavrin.mymoney.feature.transaction.R.string.error_enter_amount_first,
                viewModel.state.value.errorBannerRes,
            )
            assertTrue(transactionRepo.upserted.isEmpty())
        }

    @Test
    fun `cross currency target without stored rate navigates to rate setup`() =
        runTest {
            currencyRepo.seed(eur)
            accountRepo.seed(euroAccount)
            val viewModel = buildViewModel()

            advanceUntilIdle()
            viewModel.actions.test {
                viewModel.onEvent(TransferEvent.TargetAccountChanged(euroAccount.id))
                advanceUntilIdle()

                assertEquals(TransferAction.NavigateToRateSetup(usd.id, eur.id), awaitItem())
                assertNull(viewModel.state.value.currentRate)
                assertEquals("", viewModel.state.value.ratePreviewText)
                cancelAndIgnoreRemainingEvents()
            }
        }

    private suspend fun seedEurToUsdRate(rate: Double) {
        rateRepo.upsert(
            CurrencyRate(
                id = 0L,
                fromCurrencyId = eur.id,
                toCurrencyId = usd.id,
                rate = rate,
                updatedAt = createdAt,
            ),
        )
    }

    @Test
    fun `cross currency SaveClicked shows rate dialog instead of executing immediately`() =
        runTest {
            currencyRepo.seed(eur)
            accountRepo.seed(euroAccount)
            seedEurToUsdRate(1.10)
            val viewModel = buildViewModel()

            advanceUntilIdle()
            viewModel.actions.test {
                viewModel.onEvent(TransferEvent.SourceAccountChanged(euroAccount.id))
                viewModel.onEvent(TransferEvent.TargetAccountChanged(bankAccount.id))
                viewModel.onEvent(TransferEvent.KeypadDigit(1))
                viewModel.onEvent(TransferEvent.KeypadDigit(0))
                viewModel.onEvent(TransferEvent.KeypadDigit(0))
                advanceUntilIdle()

                viewModel.onEvent(TransferEvent.SaveClicked)
                advanceUntilIdle()

                assertEquals(TransferAction.ShowRateDialog, awaitItem())
                val row = viewModel.state.value.rateDialogRow
                assertEquals("EUR", row?.fromCode)
                assertEquals("USD", row?.toCode)
                assertEquals(0, BigDecimal("1.10").compareTo(row?.displayRate))
                assertTrue(viewModel.state.value.isSaving)
                assertTrue(transactionRepo.upserted.isEmpty())
                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `same currency SaveClicked executes without showing rate dialog`() =
        runTest {
            val viewModel = buildViewModel()

            advanceUntilIdle()
            viewModel.actions.test {
                viewModel.onEvent(TransferEvent.KeypadDigit(5))
                viewModel.onEvent(TransferEvent.TargetAccountChanged(bankAccount.id))
                viewModel.onEvent(TransferEvent.SaveClicked)
                advanceUntilIdle()

                assertNull(viewModel.state.value.rateDialogRow)
                val saved = transactionRepo.upserted.single()
                assertEquals(0, BigDecimal("5").compareTo(saved.amount))
                assertEquals(0, BigDecimal("5").compareTo(saved.toAmount))
                assertNull(saved.exchangeRate)
                assertEquals(TransferAction.NavigateBack, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `rate dialog confirm without edit converts toAmount by resolved rate and emits NavigateBack`() =
        runTest {
            currencyRepo.seed(eur)
            accountRepo.seed(euroAccount)
            seedEurToUsdRate(1.10)
            val viewModel = buildViewModel()

            advanceUntilIdle()
            viewModel.actions.test {
                viewModel.onEvent(TransferEvent.SourceAccountChanged(euroAccount.id))
                viewModel.onEvent(TransferEvent.TargetAccountChanged(bankAccount.id))
                viewModel.onEvent(TransferEvent.KeypadDigit(1))
                viewModel.onEvent(TransferEvent.KeypadDigit(0))
                viewModel.onEvent(TransferEvent.KeypadDigit(0))
                advanceUntilIdle()

                viewModel.onEvent(TransferEvent.SaveClicked)
                advanceUntilIdle()
                assertEquals(TransferAction.ShowRateDialog, awaitItem())

                val displayed =
                    viewModel.state.value.rateDialogRow
                        ?.displayRate!!
                viewModel.onEvent(TransferEvent.RateDialogConfirmed(displayed))
                advanceUntilIdle()

                val saved = transactionRepo.upserted.single()
                assertEquals(0, BigDecimal("100").compareTo(saved.amount))
                assertEquals(0, BigDecimal("110").compareTo(saved.toAmount))
                assertEquals(1.10, saved.exchangeRate)
                assertEquals(bankAccount.id, saved.toAccountId)
                assertEquals(euroAccount.id, saved.accountId)
                assertNull(viewModel.state.value.rateDialogRow)
                assertFalse(viewModel.state.value.isSaving)
                assertEquals(TransferAction.NavigateBack, awaitItem())
                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `rate dialog manual edit uses edited rate for toAmount and never writes to rate repo`() =
        runTest {
            currencyRepo.seed(eur)
            accountRepo.seed(euroAccount)
            seedEurToUsdRate(1.10)
            val viewModel = buildViewModel()

            advanceUntilIdle()
            viewModel.onEvent(TransferEvent.SourceAccountChanged(euroAccount.id))
            viewModel.onEvent(TransferEvent.TargetAccountChanged(bankAccount.id))
            viewModel.onEvent(TransferEvent.KeypadDigit(1))
            viewModel.onEvent(TransferEvent.KeypadDigit(0))
            viewModel.onEvent(TransferEvent.KeypadDigit(0))
            viewModel.onEvent(TransferEvent.SaveClicked)
            advanceUntilIdle()

            val ratesBefore = rateRepo.snapshot()
            viewModel.onEvent(TransferEvent.RateDialogConfirmed(BigDecimal("1.20")))
            advanceUntilIdle()

            val saved = transactionRepo.upserted.single()
            assertEquals(0, BigDecimal("120").compareTo(saved.toAmount))
            assertEquals(1.20, saved.exchangeRate)
            assertEquals(ratesBefore, rateRepo.snapshot())
        }

    @Test
    fun `rate dialog dismiss cancels execution and clears saving guard`() =
        runTest {
            currencyRepo.seed(eur)
            accountRepo.seed(euroAccount)
            seedEurToUsdRate(1.10)
            val viewModel = buildViewModel()

            advanceUntilIdle()
            viewModel.onEvent(TransferEvent.SourceAccountChanged(euroAccount.id))
            viewModel.onEvent(TransferEvent.TargetAccountChanged(bankAccount.id))
            viewModel.onEvent(TransferEvent.KeypadDigit(1))
            viewModel.onEvent(TransferEvent.KeypadDigit(0))
            viewModel.onEvent(TransferEvent.KeypadDigit(0))
            viewModel.onEvent(TransferEvent.SaveClicked)
            advanceUntilIdle()

            viewModel.onEvent(TransferEvent.RateDialogDismissed)
            advanceUntilIdle()

            assertTrue(transactionRepo.upserted.isEmpty())
            assertNull(viewModel.state.value.rateDialogRow)
            assertFalse(viewModel.state.value.isSaving)
        }

    @Test
    fun `confirm without edit uses full unrounded cross-rate so toAmount has full precision`() =
        runTest {
            // EUR->USD rate stored as 1.10555 (not a round number). The dialog displays it rounded
            // to 2 dp (1.11). Confirming the displayed value must STILL use the unrounded fullRate
            // so toAmount = 100 × 1.10555 = 110.56 (HALF_UP, 2 dp) — not 100 × 1.11 = 111.00.
            currencyRepo.seed(eur)
            accountRepo.seed(euroAccount)
            // Store an unrounded rate — display will be 1.11 (HALF_UP), full rate is 1.10555
            rateRepo.upsert(
                CurrencyRate(
                    id = 0L,
                    fromCurrencyId = eur.id,
                    toCurrencyId = usd.id,
                    rate = 1.10555,
                    updatedAt = createdAt,
                ),
            )
            val viewModel = buildViewModel()

            advanceUntilIdle()
            viewModel.actions.test {
                viewModel.onEvent(TransferEvent.SourceAccountChanged(euroAccount.id))
                viewModel.onEvent(TransferEvent.TargetAccountChanged(bankAccount.id))
                // amount = 100
                viewModel.onEvent(TransferEvent.KeypadDigit(1))
                viewModel.onEvent(TransferEvent.KeypadDigit(0))
                viewModel.onEvent(TransferEvent.KeypadDigit(0))
                advanceUntilIdle()

                viewModel.onEvent(TransferEvent.SaveClicked)
                advanceUntilIdle()
                assertEquals(TransferAction.ShowRateDialog, awaitItem())

                // Confirm with the display-rounded rate (what the user sees: 1.11)
                val displayRate =
                    viewModel.state.value.rateDialogRow!!
                        .displayRate!!
                viewModel.onEvent(TransferEvent.RateDialogConfirmed(displayRate))
                advanceUntilIdle()

                val saved = transactionRepo.upserted.single()
                // toAmount = 100 × 1.10555 (full rate) rounded HALF_UP to 2 dp = 110.56
                // NOT 100 × 1.11 = 111.00
                assertEquals(0, BigDecimal("110.56").compareTo(saved.toAmount))
                assertEquals(TransferAction.NavigateBack, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `double SaveClicked during rate dialog flow emits one ShowRateDialog and keeps dialog open`() =
        runTest {
            currencyRepo.seed(eur)
            accountRepo.seed(euroAccount)
            seedEurToUsdRate(1.10)
            val viewModel = buildViewModel()

            advanceUntilIdle()
            viewModel.onEvent(TransferEvent.SourceAccountChanged(euroAccount.id))
            viewModel.onEvent(TransferEvent.TargetAccountChanged(bankAccount.id))
            viewModel.onEvent(TransferEvent.KeypadDigit(5))
            advanceUntilIdle()

            viewModel.actions.test {
                viewModel.onEvent(TransferEvent.SaveClicked)
                advanceUntilIdle()
                assertEquals(TransferAction.ShowRateDialog, awaitItem())

                // Second SaveClicked while isSaving=true must be ignored
                assertTrue(viewModel.state.value.isSaving)
                viewModel.onEvent(TransferEvent.SaveClicked)
                advanceUntilIdle()

                // Dialog row stays open, no second ShowRateDialog, no transaction saved
                assertNotNull(viewModel.state.value.rateDialogRow)
                assertTrue(transactionRepo.upserted.isEmpty())
                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `resolve rate failure shows error banner and releases isSaving`() =
        runTest {
            currencyRepo.seed(eur)
            accountRepo.seed(euroAccount)
            // Use a CurrencyRateRepository that throws from findRate so that ResolveRateUseCase
            // propagates the exception to the VM's showRateDialog catch block.
            val throwingRateRepo = ThrowingCurrencyRateRepository()
            val viewModel =
                TransferViewModel(
                    transactionRepository = transactionRepo,
                    accountRepository = accountRepo,
                    currencyRepository = currencyRepo,
                    currencyRateRepository = throwingRateRepo,
                    transferExecutor =
                        TransferExecutor(
                            accountRepository = accountRepo,
                            currencyRateRepository = throwingRateRepo,
                            transactionRepository = transactionRepo,
                            defaultDispatcher = mainDispatcherRule.testDispatcher,
                        ),
                    resolveRate =
                        ResolveRateUseCase(
                            currencyRateRepository = throwingRateRepo,
                            currencyRepository = currencyRepo,
                            convertMoney = ConvertMoneyUseCase(),
                            clock = Clock.fixed(createdAt, ZoneId.of("UTC")),
                            zoneId = ZoneId.systemDefault(),
                        ),
                    appSettingsRepository = settingsRepo,
                    savedStateHandle = SavedStateHandle(),
                )

            advanceUntilIdle()
            viewModel.onEvent(TransferEvent.SourceAccountChanged(euroAccount.id))
            viewModel.onEvent(TransferEvent.TargetAccountChanged(bankAccount.id))
            viewModel.onEvent(TransferEvent.KeypadDigit(5))
            viewModel.onEvent(TransferEvent.SaveClicked)
            advanceUntilIdle()

            assertFalse(viewModel.state.value.isSaving)
            assertNull(viewModel.state.value.rateDialogRow)
            assertEquals(
                com.kshavrin.mymoney.feature.transaction.R.string.error_save_failed,
                viewModel.state.value.errorBannerRes,
            )
            assertTrue(transactionRepo.upserted.isEmpty())
        }

    @Test
    fun `after dismiss a fresh SaveClicked re-opens the rate dialog`() =
        runTest {
            currencyRepo.seed(eur)
            accountRepo.seed(euroAccount)
            seedEurToUsdRate(1.10)
            val viewModel = buildViewModel()

            advanceUntilIdle()
            viewModel.actions.test {
                viewModel.onEvent(TransferEvent.SourceAccountChanged(euroAccount.id))
                viewModel.onEvent(TransferEvent.TargetAccountChanged(bankAccount.id))
                viewModel.onEvent(TransferEvent.KeypadDigit(5))
                advanceUntilIdle()

                // First save → dialog
                viewModel.onEvent(TransferEvent.SaveClicked)
                advanceUntilIdle()
                assertEquals(TransferAction.ShowRateDialog, awaitItem())

                // User dismisses
                viewModel.onEvent(TransferEvent.RateDialogDismissed)
                advanceUntilIdle()
                assertFalse(viewModel.state.value.isSaving)

                // Second save must open the dialog again
                viewModel.onEvent(TransferEvent.SaveClicked)
                advanceUntilIdle()
                assertEquals(TransferAction.ShowRateDialog, awaitItem())
                assertNotNull(viewModel.state.value.rateDialogRow)
                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `RateDialogConfirmed with user-edited rate does not use fullRate even if edited matches display`() =
        runTest {
            // When the user types a rate that DIFFERS from the stored display rate, the VM must use
            // exactly the edited value regardless of rateDialogFullRate.
            currencyRepo.seed(eur)
            accountRepo.seed(euroAccount)
            seedEurToUsdRate(1.10)
            val viewModel = buildViewModel()

            advanceUntilIdle()
            viewModel.actions.test {
                viewModel.onEvent(TransferEvent.SourceAccountChanged(euroAccount.id))
                viewModel.onEvent(TransferEvent.TargetAccountChanged(bankAccount.id))
                viewModel.onEvent(TransferEvent.KeypadDigit(2))
                viewModel.onEvent(TransferEvent.KeypadDigit(0))
                advanceUntilIdle()

                viewModel.onEvent(TransferEvent.SaveClicked)
                advanceUntilIdle()
                assertEquals(TransferAction.ShowRateDialog, awaitItem())

                // User manually overrides with 1.50 (different from stored 1.10)
                viewModel.onEvent(TransferEvent.RateDialogConfirmed(BigDecimal("1.50")))
                advanceUntilIdle()

                val saved = transactionRepo.upserted.single()
                // toAmount = 20 × 1.50 = 30.00
                assertEquals(0, BigDecimal("30.00").compareTo(saved.toAmount))
                assertEquals(1.50, saved.exchangeRate)
                assertEquals(TransferAction.NavigateBack, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    /**
     * CurrencyRateRepository that returns null from findRate (so the use case marks the rate as
     * stale and triggers a refresh) but THROWS from refreshRatesFromNetwork — simulating an
     * infrastructure failure that propagates through ResolveRateUseCase into the VM's
     * showRateDialog catch block.
     */
    private class ThrowingCurrencyRateRepository : CurrencyRateRepository {
        override suspend fun findRate(
            fromCurrencyId: Long,
            toCurrencyId: Long,
        ): CurrencyRate? = null

        override fun observeAll(): Flow<List<CurrencyRate>> = MutableStateFlow(emptyList<CurrencyRate>()).asStateFlow()

        override suspend fun upsert(rate: CurrencyRate): Long = throw UnsupportedOperationException()

        override suspend fun deleteById(id: Long) {}

        override suspend fun refreshRatesFromNetwork(): Result<Int> = throw IllegalStateException("rate service unavailable")
    }

    private class FakeCurrencyRateRepository : CurrencyRateRepository {
        private val rates = MutableStateFlow<List<CurrencyRate>>(emptyList())

        fun snapshot(): List<CurrencyRate> = rates.value

        override suspend fun findRate(
            fromCurrencyId: Long,
            toCurrencyId: Long,
        ): CurrencyRate? =
            rates.value.firstOrNull {
                it.fromCurrencyId == fromCurrencyId && it.toCurrencyId == toCurrencyId
            }

        override fun observeAll(): Flow<List<CurrencyRate>> = rates.asStateFlow()

        override suspend fun upsert(rate: CurrencyRate): Long {
            val id = if (rate.id == 0L) (rates.value.maxOfOrNull { it.id } ?: 0L) + 1L else rate.id
            rates.value = rates.value.filterNot { it.id == id } + rate.copy(id = id)
            return id
        }

        override suspend fun deleteById(id: Long) {
            rates.value = rates.value.filterNot { it.id == id }
        }

        override suspend fun refreshRatesFromNetwork(): Result<Int> = Result.success(0)
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

    companion object {
        private const val TEST_TIME_ZONE_ID = "America/New_York"
    }
}
