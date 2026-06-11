package com.kshavrin.mymoney.feature.transaction.transfer

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.kshavrin.mymoney.core.domain.model.Account
import com.kshavrin.mymoney.core.domain.model.AccountType
import com.kshavrin.mymoney.core.domain.model.Currency
import com.kshavrin.mymoney.core.domain.model.CurrencyRate
import com.kshavrin.mymoney.core.domain.model.Transaction
import com.kshavrin.mymoney.core.domain.model.TransactionKind
import com.kshavrin.mymoney.core.domain.repository.CurrencyRateRepository
import com.kshavrin.mymoney.core.domain.repository.TransactionRepository
import com.kshavrin.mymoney.core.domain.usecase.TransferExecutor
import com.kshavrin.mymoney.feature.transaction.fake.FakeAccountRepository
import com.kshavrin.mymoney.feature.transaction.fake.FakeAppSettingsRepository
import com.kshavrin.mymoney.feature.transaction.fake.FakeCurrencyRepository
import com.kshavrin.mymoney.feature.transaction.fake.FakeTransactionRepository
import com.kshavrin.mymoney.feature.transaction.util.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal
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

    private val bankAccount = Account(
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
    ): TransferViewModel = TransferViewModel(
        transactionRepository = transactionRepository,
        accountRepository = accountRepo,
        currencyRepository = currencyRepo,
        currencyRateRepository = rateRepo,
        transferExecutor = TransferExecutor(
            accountRepository = accountRepo,
            currencyRateRepository = rateRepo,
            transactionRepository = transactionRepository,
            defaultDispatcher = mainDispatcherRule.testDispatcher,
        ),
        appSettingsRepository = settingsRepo,
        savedStateHandle = SavedStateHandle(),
    )

    private fun localMidnight(date: LocalDate): Instant =
        date.atStartOfDay(ZoneId.systemDefault()).toInstant()

    @Test
    fun `SaveClicked stores transfer occurredAt at local midnight in the system timezone`() = runTest {
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
    fun `double SaveClicked performs one transfer upsert and emits one NavigateBack`() = runTest {
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

    private class FakeCurrencyRateRepository : CurrencyRateRepository {
        private val rates = MutableStateFlow<List<CurrencyRate>>(emptyList())

        override suspend fun findRate(fromCurrencyId: Long, toCurrencyId: Long): CurrencyRate? =
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

    companion object {
        private const val TEST_TIME_ZONE_ID = "America/New_York"
    }
}
