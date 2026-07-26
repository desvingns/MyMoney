package com.kshavrin.mymoney.feature.transactionslist.detail

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.kshavrin.mymoney.core.domain.model.Account
import com.kshavrin.mymoney.core.domain.model.AccountType
import com.kshavrin.mymoney.core.domain.model.Category
import com.kshavrin.mymoney.core.domain.model.CategoryKind
import com.kshavrin.mymoney.core.domain.model.Currency
import com.kshavrin.mymoney.core.domain.model.Transaction
import com.kshavrin.mymoney.core.domain.model.TransactionKind
import com.kshavrin.mymoney.core.testing.FixedTimeZoneRule
import com.kshavrin.mymoney.core.testing.fake.FakeCurrencyRepository
import com.kshavrin.mymoney.feature.transactionslist.fake.FakeAccountRepository
import com.kshavrin.mymoney.feature.transactionslist.fake.FakeCategoryRepository
import com.kshavrin.mymoney.feature.transactionslist.fake.FakeCurrencyRateRepository
import com.kshavrin.mymoney.feature.transactionslist.fake.FakeTransactionRepository
import com.kshavrin.mymoney.feature.transactionslist.util.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

// Robolectric supplies a real android.os.Bundle so savedStateHandle.toRoute<Destinations.TransactionDetail>()
// can decode its route args; the android.jar stub throws "not mocked" at VM construction (SPEC-19 type-safe nav).
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class TimezoneDetailRoundTripTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val timeZoneRule = FixedTimeZoneRule(ZoneId.of("Europe/Moscow"))

    private val createdAt = Instant.parse("2026-05-01T08:00:00Z")

    private val rub =
        Currency(
            id = 1L,
            code = "RUB",
            symbol = "₽",
            name = "Russian Ruble",
            decimalDigits = 2,
            isActive = true,
            sortOrder = 0,
        )

    private val cashAccount =
        Account(
            id = 7L,
            name = "Cash",
            currencyId = rub.id,
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

    private val foodCategory =
        Category(
            id = 10L,
            name = "Food",
            kind = CategoryKind.Expense,
            iconKey = "ic_cat_food",
            colorHex = "#FF8888",
            textColor = "#FFFFFF",
            sortOrder = 0,
            isDefault = false,
            isArchived = false,
            createdAt = createdAt,
        )

    private fun systemZone(): ZoneId = ZoneId.systemDefault()

    private fun localMidnight(date: LocalDate): Instant =
        date.atStartOfDay(systemZone()).toInstant()

    private fun importedExpense(date: LocalDate) =
        Transaction(
            id = 100L,
            kind = TransactionKind.Expense,
            amount = BigDecimal("12.50"),
            currencyId = rub.id,
            accountId = cashAccount.id,
            categoryId = foodCategory.id,
            note = "Imported lunch",
            occurredAt = localMidnight(date),
            createdAt = createdAt,
            updatedAt = createdAt,
            isDeleted = false,
            toAccountId = null,
            toAmount = null,
            exchangeRate = null,
        )

    private fun buildViewModel(
        transactionRepository: FakeTransactionRepository,
    ): TransactionDetailViewModel =
        TransactionDetailViewModel(
            transactionRepository = transactionRepository,
            accountRepository = FakeAccountRepository().apply { seed(cashAccount) },
            currencyRepository = FakeCurrencyRepository().apply { seed(rub) },
            categoryRepository = FakeCategoryRepository().apply { seed(foodCategory) },
            currencyRateRepository = FakeCurrencyRateRepository(),
            savedStateHandle =
                SavedStateHandle(
                    mapOf(TransactionDetailViewModel.KEY_TRANSACTION_ID to 100L),
                ),
        )

    @Test
    fun `imported local-midnight row opens on the same local date in Moscow`() =
        runTest {
            val importedDate = LocalDate.of(2026, 6, 1)
            val transactionRepository =
                FakeTransactionRepository().apply {
                    seed(importedExpense(importedDate))
                }
            val viewModel = buildViewModel(transactionRepository)

            viewModel.state.test {
                val state = expectMostRecentItem()
                assertEquals(importedDate, state.occurredAt)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `saving an imported local-midnight row preserves occurredAt instant and local date`() =
        runTest {
            val importedDate = LocalDate.of(2026, 6, 1)
            val originalOccurredAt = localMidnight(importedDate)
            val transactionRepository =
                FakeTransactionRepository().apply {
                    seed(importedExpense(importedDate))
                }
            val viewModel = buildViewModel(transactionRepository)

            viewModel.state.test {
                assertEquals(importedDate, expectMostRecentItem().occurredAt)
                cancelAndIgnoreRemainingEvents()
            }

            viewModel.onEvent(TransactionDetailEvent.NoteChanged("Imported lunch updated"))
            viewModel.onEvent(TransactionDetailEvent.SaveClicked)

            val saved = transactionRepository.findById(100L)
            assertNotNull(saved)
            assertEquals(originalOccurredAt, saved!!.occurredAt)
            assertEquals(importedDate, saved.occurredAt.atZone(systemZone()).toLocalDate())
        }
}
