package com.kshavrin.mymoney.feature.transactionslist.detail

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.kshavrin.mymoney.core.domain.model.Account
import com.kshavrin.mymoney.core.domain.model.AccountType
import com.kshavrin.mymoney.core.domain.model.Category
import com.kshavrin.mymoney.core.domain.model.CategoryKind
import com.kshavrin.mymoney.core.domain.model.Currency
import com.kshavrin.mymoney.core.domain.model.CurrencyRate
import com.kshavrin.mymoney.core.domain.model.Transaction
import com.kshavrin.mymoney.core.domain.model.TransactionKind
import com.kshavrin.mymoney.core.domain.repository.TransactionRepository
import com.kshavrin.mymoney.feature.transactionslist.fake.FakeAccountRepository
import com.kshavrin.mymoney.feature.transactionslist.fake.FakeCategoryRepository
import com.kshavrin.mymoney.feature.transactionslist.fake.FakeCurrencyRateRepository
import com.kshavrin.mymoney.feature.transactionslist.fake.FakeCurrencyRepository
import com.kshavrin.mymoney.feature.transactionslist.fake.FakeTransactionRepository
import com.kshavrin.mymoney.feature.transactionslist.util.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
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
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.TimeZone

class TransactionDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var originalTimeZone: TimeZone

    private val createdAt: Instant = Instant.parse("2026-05-01T08:00:00Z")
    private val originalDate: LocalDate = LocalDate.parse("2026-06-10")

    private lateinit var transactionRepo: FakeTransactionRepository
    private lateinit var accountRepo: FakeAccountRepository
    private lateinit var currencyRepo: FakeCurrencyRepository
    private lateinit var categoryRepo: FakeCategoryRepository
    private lateinit var rateRepo: FakeCurrencyRateRepository

    private val usd = Currency(
        id = 1L,
        code = "USD",
        symbol = "$",
        name = "US Dollar",
        decimalDigits = 2,
        isActive = true,
        sortOrder = 0,
    )

    private val eur = Currency(
        id = 2L,
        code = "EUR",
        symbol = "€",
        name = "Euro",
        decimalDigits = 2,
        isActive = true,
        sortOrder = 1,
    )

    private val cashUsd = account(id = 7L, name = "Cash", currencyId = usd.id, isDefault = true)
    private val bankUsd = account(id = 8L, name = "Bank", currencyId = usd.id)
    private val walletEur = account(id = 9L, name = "Wallet EUR", currencyId = eur.id)

    private val foodCategory = Category(
        id = 10L,
        name = "Food",
        kind = CategoryKind.Expense,
        iconKey = "ic_cat_food",
        colorHex = "#FF8888",
        sortOrder = 0,
        isDefault = false,
        isArchived = false,
        createdAt = createdAt,
    )

    private val salaryCategory = Category(
        id = 11L,
        name = "Salary",
        kind = CategoryKind.Income,
        iconKey = "ic_cat_salary",
        colorHex = "#88FF88",
        sortOrder = 0,
        isDefault = false,
        isArchived = false,
        createdAt = createdAt,
    )

    private fun account(
        id: Long,
        name: String,
        currencyId: Long,
        isDefault: Boolean = false,
    ) = Account(
        id = id,
        name = name,
        currencyId = currencyId,
        initialBalance = BigDecimal.ZERO,
        type = AccountType.Cash,
        colorHex = "#7AC794",
        iconKey = "ic_acc_cash",
        isDefault = isDefault,
        sortOrder = 0,
        createdAt = createdAt,
        updatedAt = createdAt,
        isArchived = false,
    )

    private fun expense(id: Long = 100L) = Transaction(
        id = id,
        kind = TransactionKind.Expense,
        amount = BigDecimal("12.50"),
        currencyId = usd.id,
        accountId = cashUsd.id,
        categoryId = foodCategory.id,
        note = "Lunch",
        occurredAt = localMidnight(originalDate),
        createdAt = createdAt,
        updatedAt = createdAt,
        isDeleted = false,
        toAccountId = null,
        toAmount = null,
        exchangeRate = null,
    )

    private fun income(id: Long = 200L) = Transaction(
        id = id,
        kind = TransactionKind.Income,
        amount = BigDecimal("3000"),
        currencyId = usd.id,
        accountId = bankUsd.id,
        categoryId = salaryCategory.id,
        note = "Paycheck",
        occurredAt = localMidnight(originalDate),
        createdAt = createdAt,
        updatedAt = createdAt,
        isDeleted = false,
        toAccountId = null,
        toAmount = null,
        exchangeRate = null,
    )

    /** Cross-currency transfer USD -> EUR with an existing rate of 0.9. */
    private fun crossTransfer(id: Long = 300L) = Transaction(
        id = id,
        kind = TransactionKind.Transfer,
        amount = BigDecimal("100"),
        currencyId = usd.id,
        accountId = cashUsd.id,
        categoryId = null,
        note = null,
        occurredAt = localMidnight(originalDate),
        createdAt = createdAt,
        updatedAt = createdAt,
        isDeleted = false,
        toAccountId = walletEur.id,
        toAmount = BigDecimal("90"),
        exchangeRate = 0.9,
    )

    /** Same-currency transfer USD -> USD (Cash -> Bank), no rate. */
    private fun sameCurrencyTransfer(id: Long = 400L) = Transaction(
        id = id,
        kind = TransactionKind.Transfer,
        amount = BigDecimal("50"),
        currencyId = usd.id,
        accountId = cashUsd.id,
        categoryId = null,
        note = null,
        occurredAt = localMidnight(originalDate),
        createdAt = createdAt,
        updatedAt = createdAt,
        isDeleted = false,
        toAccountId = bankUsd.id,
        toAmount = BigDecimal("50"),
        exchangeRate = null,
    )

    @Before
    fun setUp() {
        originalTimeZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone(TEST_TIME_ZONE_ID))
        transactionRepo = FakeTransactionRepository()
        accountRepo = FakeAccountRepository()
        currencyRepo = FakeCurrencyRepository()
        categoryRepo = FakeCategoryRepository()
        rateRepo = FakeCurrencyRateRepository()

        currencyRepo.seed(usd, eur)
        accountRepo.seed(cashUsd, bankUsd, walletEur)
        categoryRepo.seed(foodCategory, salaryCategory)
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(originalTimeZone)
    }

    private fun buildViewModel(
        transactionId: Long,
        transactionRepository: TransactionRepository = transactionRepo,
    ): TransactionDetailViewModel =
        TransactionDetailViewModel(
            transactionRepository = transactionRepository,
            accountRepository = accountRepo,
            currencyRepository = currencyRepo,
            categoryRepository = categoryRepo,
            currencyRateRepository = rateRepo,
            savedStateHandle = SavedStateHandle(
                mapOf(TransactionDetailViewModel.KEY_TRANSACTION_ID to transactionId),
            ),
        )

    private fun localMidnight(date: LocalDate): Instant =
        date.atStartOfDay(ZoneId.systemDefault()).toInstant()

    // ---- AC1: load / pre-populate ------------------------------------------------------------

    @Test
    fun `init pre-populates state from the loaded expense`() = runTest {
        val tx = expense()
        transactionRepo.seed(tx)

        val viewModel = buildViewModel(tx.id)

        viewModel.state.test {
            val state = expectMostRecentItem()
            assertTrue("expected isLoaded after init", state.isLoaded)
            assertEquals(TransactionKind.Expense, state.kind)
            assertEquals(0, BigDecimal("12.50").compareTo(state.amount))
            assertEquals("Lunch", state.note)
            assertEquals(originalDate, state.occurredAt)
            assertEquals(cashUsd.id, state.account?.id)
            assertEquals(usd.id, state.currency?.id)
            assertEquals(foodCategory.id, state.category?.id)
            assertFalse("freshly loaded state must not be dirty", state.isDirty)
            assertFalse("not dirty -> cannot save", state.canSave)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `init pre-populates state from the loaded income`() = runTest {
        val tx = income()
        transactionRepo.seed(tx)

        val viewModel = buildViewModel(tx.id)

        viewModel.state.test {
            val state = expectMostRecentItem()
            assertTrue(state.isLoaded)
            assertEquals(TransactionKind.Income, state.kind)
            assertEquals(0, BigDecimal("3000").compareTo(state.amount))
            assertEquals(bankUsd.id, state.account?.id)
            assertEquals(salaryCategory.id, state.category?.id)
            assertFalse("income is not a transfer", state.isTransfer)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `init pre-populates a cross-currency transfer with target account, rate and derived flags`() =
        runTest {
            val tx = crossTransfer()
            transactionRepo.seed(tx)

            val viewModel = buildViewModel(tx.id)

            viewModel.state.test {
                val state = expectMostRecentItem()
                assertTrue(state.isLoaded)
                assertEquals(TransactionKind.Transfer, state.kind)
                assertEquals(cashUsd.id, state.account?.id)
                assertEquals(walletEur.id, state.targetAccount?.id)
                assertEquals(usd.id, state.currency?.id)
                assertEquals(eur.id, state.targetCurrency?.id)
                assertEquals(0.9, state.exchangeRate!!, 0.0001)
                assertTrue("transfer kind -> isTransfer true", state.isTransfer)
                assertTrue("USD->EUR -> isCrossCurrency true", state.isCrossCurrency)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `same-currency transfer derives isCrossCurrency false`() = runTest {
        val tx = sameCurrencyTransfer()
        transactionRepo.seed(tx)

        val viewModel = buildViewModel(tx.id)

        viewModel.state.test {
            val state = expectMostRecentItem()
            assertTrue("transfer kind -> isTransfer true", state.isTransfer)
            assertFalse("USD->USD -> isCrossCurrency false", state.isCrossCurrency)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ---- AC2: isDirty / canSave --------------------------------------------------------------

    @Test
    fun `editing the note flips dirty and canSave true`() = runTest {
        val tx = expense()
        transactionRepo.seed(tx)
        val viewModel = buildViewModel(tx.id)

        viewModel.onEvent(TransactionDetailEvent.NoteChanged("Dinner"))

        viewModel.state.test {
            val state = expectMostRecentItem()
            assertTrue("note edit -> dirty", state.isDirty)
            assertTrue("dirty + amount>0 -> canSave", state.canSave)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `changing the date flips dirty true`() = runTest {
        val tx = expense()
        transactionRepo.seed(tx)
        val viewModel = buildViewModel(tx.id)

        viewModel.onEvent(TransactionDetailEvent.DateChanged(originalDate.plusDays(1)))

        viewModel.state.test {
            assertTrue("date edit -> dirty", expectMostRecentItem().isDirty)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a keypad digit that changes the amount flips dirty true`() = runTest {
        val tx = expense()
        transactionRepo.seed(tx)
        val viewModel = buildViewModel(tx.id)

        // loaded amount is 12.5; appending 9 -> 12.59, a real numeric change
        viewModel.onEvent(TransactionDetailEvent.KeypadDigit(9))

        viewModel.state.test {
            val state = expectMostRecentItem()
            assertTrue("amount edit -> dirty", state.isDirty)
            assertTrue(state.canSave)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `reverting the note back to the original value clears dirty and canSave`() = runTest {
        val tx = expense()
        transactionRepo.seed(tx)
        val viewModel = buildViewModel(tx.id)

        viewModel.onEvent(TransactionDetailEvent.NoteChanged("Dinner"))
        viewModel.onEvent(TransactionDetailEvent.NoteChanged("Lunch"))

        viewModel.state.test {
            val state = expectMostRecentItem()
            assertFalse("reverted to original -> not dirty", state.isDirty)
            assertFalse("not dirty -> cannot save", state.canSave)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `no edit keeps canSave false`() = runTest {
        val tx = expense()
        transactionRepo.seed(tx)
        val viewModel = buildViewModel(tx.id)

        viewModel.state.test {
            assertFalse("untouched form cannot save", expectMostRecentItem().canSave)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `canSave is false when amount is cleared to zero even if otherwise dirty`() = runTest {
        val tx = expense()
        transactionRepo.seed(tx)
        val viewModel = buildViewModel(tx.id)

        // make the form dirty via the note...
        viewModel.onEvent(TransactionDetailEvent.NoteChanged("Dinner"))
        // ...then drive the amount down to zero through the keypad
        repeat(8) { viewModel.onEvent(TransactionDetailEvent.KeypadBackspace) }

        viewModel.state.test {
            val state = expectMostRecentItem()
            assertEquals(0, BigDecimal.ZERO.compareTo(state.amount))
            assertFalse("amount must be > 0 to save", state.canSave)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ---- AC3: save ---------------------------------------------------------------------------

    @Test
    fun `SaveClicked upserts the same id with createdAt preserved and updatedAt advanced`() =
        runTest {
            val tx = expense()
            transactionRepo.seed(tx)
            val viewModel = buildViewModel(tx.id)

            viewModel.onEvent(TransactionDetailEvent.NoteChanged("Dinner"))
            viewModel.onEvent(TransactionDetailEvent.SaveClicked)

            val saved = transactionRepo.findById(tx.id)
            assertNotNull("a row must be upserted under the same id", saved)
            assertEquals(tx.id, saved!!.id)
            assertTrue("must not insert a new row (id 0)", saved.id != 0L)
            assertEquals("createdAt must be preserved", createdAt, saved.createdAt)
            assertTrue(
                "updatedAt must advance past the original",
                saved.updatedAt.isAfter(createdAt),
            )
            assertEquals("Dinner", saved.note)
            assertEquals(localMidnight(originalDate), saved.occurredAt)
        }

    @Test
    fun `SaveClicked emits NavigateBack`() = runTest {
        val tx = expense()
        transactionRepo.seed(tx)
        val viewModel = buildViewModel(tx.id)
        viewModel.onEvent(TransactionDetailEvent.NoteChanged("Dinner"))

        viewModel.actions.test {
            viewModel.onEvent(TransactionDetailEvent.SaveClicked)

            assertEquals(TransactionDetailAction.NavigateBack, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `double SaveClicked performs one upsert and emits one NavigateBack`() = runTest {
        val tx = expense()
        val blockingRepo = BlockingTransactionRepository().apply { seed(tx) }
        val viewModel = buildViewModel(tx.id, transactionRepository = blockingRepo)

        advanceUntilIdle()
        viewModel.onEvent(TransactionDetailEvent.NoteChanged("Dinner"))

        viewModel.actions.test {
            viewModel.onEvent(TransactionDetailEvent.SaveClicked)
            assertTrue(viewModel.state.value.isSaving)
            viewModel.onEvent(TransactionDetailEvent.SaveClicked)

            assertEquals(1, blockingRepo.startedUpserts.size)

            blockingRepo.releaseUpsert()
            advanceUntilIdle()

            assertEquals(1, blockingRepo.persistedUpserts.size)
            assertEquals(TransactionDetailAction.NavigateBack, awaitItem())
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `editing a local-midnight imported row preserves its local date on save`() = runTest {
        val importedDate = LocalDate.parse("2026-06-01")
        val tx = expense(id = 101L).copy(occurredAt = localMidnight(importedDate))
        transactionRepo.seed(tx)
        val viewModel = buildViewModel(tx.id)

        viewModel.state.test {
            assertEquals(importedDate, expectMostRecentItem().occurredAt)
            cancelAndIgnoreRemainingEvents()
        }

        viewModel.onEvent(TransactionDetailEvent.NoteChanged("Imported lunch"))
        viewModel.onEvent(TransactionDetailEvent.SaveClicked)

        val saved = transactionRepo.findById(tx.id)
        assertNotNull(saved)
        assertEquals(localMidnight(importedDate), saved!!.occurredAt)
    }

    // ---- AC4: delete + undo ------------------------------------------------------------------

    @Test
    fun `ConfirmDelete soft-deletes by id and emits ShowUndoSnackbar`() = runTest {
        val tx = expense()
        transactionRepo.seed(tx)
        val viewModel = buildViewModel(tx.id)

        viewModel.actions.test {
            viewModel.onEvent(TransactionDetailEvent.DeleteClicked)
            viewModel.onEvent(TransactionDetailEvent.ConfirmDelete)

            val action = awaitItem()
            assertTrue(
                "expected ShowUndoSnackbar(${tx.id}) but was $action",
                action is TransactionDetailAction.ShowUndoSnackbar &&
                    action.transactionId == tx.id,
            )
            assertEquals(listOf(tx.id), transactionRepo.softDeletedIds)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `double ConfirmDelete performs one softDelete and emits one undo action`() = runTest {
        val tx = expense()
        val blockingRepo = BlockingTransactionRepository().apply { seed(tx) }
        val viewModel = buildViewModel(tx.id, transactionRepository = blockingRepo)

        advanceUntilIdle()

        viewModel.actions.test {
            viewModel.onEvent(TransactionDetailEvent.DeleteClicked)
            viewModel.onEvent(TransactionDetailEvent.ConfirmDelete)
            assertTrue(viewModel.state.value.isSaving)
            viewModel.onEvent(TransactionDetailEvent.ConfirmDelete)

            assertEquals(1, blockingRepo.startedSoftDeletes.size)

            blockingRepo.releaseSoftDelete()
            advanceUntilIdle()

            val action = awaitItem()
            assertTrue(action is TransactionDetailAction.ShowUndoSnackbar)
            assertEquals(1, blockingRepo.persistedSoftDeletes.size)
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `UndoDeleteClicked restores the transaction by id`() = runTest {
        val tx = expense()
        transactionRepo.seed(tx)
        val viewModel = buildViewModel(tx.id)

        viewModel.onEvent(TransactionDetailEvent.UndoDeleteClicked(tx.id))

        assertEquals(listOf(tx.id), transactionRepo.restoredIds)
    }

    // ---- AC5: transfer rate ------------------------------------------------------------------

    @Test
    fun `editing the rate on a cross-currency transfer saves a single transfer row with toAmount = amount times rate`() =
        runTest {
            val tx = crossTransfer()
            transactionRepo.seed(tx)
            rateRepo.seed(
                CurrencyRate(
                    id = 5L,
                    fromCurrencyId = usd.id,
                    toCurrencyId = eur.id,
                    rate = 0.9,
                    updatedAt = createdAt,
                ),
            )
            val viewModel = buildViewModel(tx.id)

            // change the rate from 0.9 to 0.8 and save
            viewModel.onEvent(TransactionDetailEvent.RateChanged("0.8"))
            viewModel.onEvent(TransactionDetailEvent.SaveClicked)

            val saved = transactionRepo.findById(tx.id)
            assertNotNull(saved)
            assertEquals("transfer stays a single row under the same id", tx.id, saved!!.id)
            assertEquals(TransactionKind.Transfer, saved.kind)
            assertEquals(0.8, saved.exchangeRate!!, 0.0001)
            assertEquals(localMidnight(originalDate), saved.occurredAt)
            // AS-7: toAmount = amount (100) * rate (0.8) = 80
            assertEquals(0, BigDecimal("80").compareTo(saved.toAmount))
        }

    @Test
    fun `changing the rate on a cross-currency transfer writes a CurrencyRate via upsert`() =
        runTest {
            val tx = crossTransfer()
            transactionRepo.seed(tx)
            rateRepo.seed(
                CurrencyRate(
                    id = 5L,
                    fromCurrencyId = usd.id,
                    toCurrencyId = eur.id,
                    rate = 0.9,
                    updatedAt = createdAt,
                ),
            )
            val viewModel = buildViewModel(tx.id)

            viewModel.onEvent(TransactionDetailEvent.RateChanged("0.8"))
            viewModel.onEvent(TransactionDetailEvent.SaveClicked)

            assertEquals("exactly one rate row written", 1, rateRepo.upserts.size)
            val written = rateRepo.upserts.single()
            assertEquals("reuses the existing rate id (update in place)", 5L, written.id)
            assertEquals(usd.id, written.fromCurrencyId)
            assertEquals(eur.id, written.toCurrencyId)
            assertEquals(0.8, written.rate, 0.0001)
        }

    @Test
    fun `saving a same-currency transfer writes no rate row`() = runTest {
        val tx = sameCurrencyTransfer()
        transactionRepo.seed(tx)
        val viewModel = buildViewModel(tx.id)

        // make the form dirty without touching currencies (note edit), then save
        viewModel.onEvent(TransactionDetailEvent.NoteChanged("Move funds"))
        viewModel.onEvent(TransactionDetailEvent.SaveClicked)

        val saved = transactionRepo.findById(tx.id)
        assertNotNull(saved)
        assertNull("same-currency transfer carries no exchange rate", saved!!.exchangeRate)
        assertEquals(localMidnight(originalDate), saved.occurredAt)
        assertTrue("no CurrencyRate row may be written", rateRepo.upserts.isEmpty())
    }

    companion object {
        private const val TEST_TIME_ZONE_ID = "America/New_York"
    }

    private class BlockingTransactionRepository(
        private val delegate: FakeTransactionRepository = FakeTransactionRepository(),
    ) : TransactionRepository by delegate {
        val startedUpserts: MutableList<Transaction> = mutableListOf()
        val persistedUpserts: MutableList<Transaction> = mutableListOf()
        val startedSoftDeletes: MutableList<Long> = mutableListOf()
        val persistedSoftDeletes: List<Long>
            get() = delegate.softDeletedIds
        private val upsertGate = CompletableDeferred<Unit>()
        private val softDeleteGate = CompletableDeferred<Unit>()

        fun seed(vararg items: Transaction) {
            delegate.seed(*items)
        }

        override suspend fun upsert(transaction: Transaction): Long {
            startedUpserts += transaction
            upsertGate.await()
            val id = delegate.upsert(transaction)
            persistedUpserts += transaction.copy(id = id)
            return id
        }

        override suspend fun softDelete(id: Long, now: Instant) {
            startedSoftDeletes += id
            softDeleteGate.await()
            delegate.softDelete(id, now)
        }

        fun releaseUpsert() {
            if (!upsertGate.isCompleted) {
                upsertGate.complete(Unit)
            }
        }

        fun releaseSoftDelete() {
            if (!softDeleteGate.isCompleted) {
                softDeleteGate.complete(Unit)
            }
        }
    }
}
