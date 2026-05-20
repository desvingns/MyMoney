package com.kshavrin.mymoney.core.domain.usecase

import com.kshavrin.mymoney.core.domain.fake.FakeAccountRepository
import com.kshavrin.mymoney.core.domain.fake.FakeCurrencyRateRepository
import com.kshavrin.mymoney.core.domain.fake.FakeTransactionRepository
import com.kshavrin.mymoney.core.domain.model.Account
import com.kshavrin.mymoney.core.domain.model.AccountType
import com.kshavrin.mymoney.core.domain.model.CurrencyRate
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

class TransferExecutorTest {

    private val now = Instant.parse("2026-05-20T10:00:00Z")

    private fun account(id: Long, currencyId: Long) = Account(
        id = id,
        name = "A$id",
        currencyId = currencyId,
        initialBalance = BigDecimal.ZERO,
        type = AccountType.Cash,
        colorHex = "#7AC794",
        iconKey = "ic_cash",
        isDefault = false,
        sortOrder = 0,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
        isArchived = false,
    )

    @Test
    fun same_currency_transfer_succeeds() = runTest {
        val accountRepo = FakeAccountRepository().apply { seed(account(1L, 1L), account(2L, 1L)) }
        val rateRepo = FakeCurrencyRateRepository()
        val txRepo = FakeTransactionRepository()
        val executor = TransferExecutor(accountRepo, rateRepo, txRepo, UnconfinedTestDispatcher())

        val result = executor.execute(
            sourceAccountId = 1L,
            targetAccountId = 2L,
            amount = BigDecimal("100.00"),
            note = null,
            occurredAt = now,
            now = now,
        )

        assertTrue(result is TransferResult.Success)
        val success = result as TransferResult.Success
        assertEquals(BigDecimal("100.00"), success.transaction.amount)
        assertEquals(BigDecimal("100.00"), success.transaction.toAmount)
        assertEquals(null, success.transaction.exchangeRate)
    }

    @Test
    fun cross_currency_with_rate_succeeds() = runTest {
        val accountRepo = FakeAccountRepository().apply { seed(account(1L, 1L), account(2L, 2L)) }
        val rateRepo = FakeCurrencyRateRepository().apply {
            seed(CurrencyRate(id = 0L, fromCurrencyId = 1L, toCurrencyId = 2L, rate = 0.92, updatedAt = now))
        }
        val txRepo = FakeTransactionRepository()
        val executor = TransferExecutor(accountRepo, rateRepo, txRepo, UnconfinedTestDispatcher())

        val result = executor.execute(
            sourceAccountId = 1L,
            targetAccountId = 2L,
            amount = BigDecimal("100.00"),
            note = "rent",
            occurredAt = now,
            now = now,
        )

        assertTrue(result is TransferResult.Success)
        val success = result as TransferResult.Success
        assertEquals(BigDecimal("92.00"), success.transaction.toAmount?.setScale(2))
        assertEquals(0.92, success.transaction.exchangeRate)
    }

    @Test
    fun cross_currency_without_rate_returns_rate_missing() = runTest {
        val accountRepo = FakeAccountRepository().apply { seed(account(1L, 1L), account(2L, 2L)) }
        val rateRepo = FakeCurrencyRateRepository()
        val txRepo = FakeTransactionRepository()
        val executor = TransferExecutor(accountRepo, rateRepo, txRepo, UnconfinedTestDispatcher())

        val result = executor.execute(
            sourceAccountId = 1L,
            targetAccountId = 2L,
            amount = BigDecimal("100.00"),
            note = null,
            occurredAt = now,
            now = now,
        )

        assertTrue(result is TransferResult.Failure.RateMissing)
        val failure = result as TransferResult.Failure.RateMissing
        assertEquals(1L, failure.fromCurrencyId)
        assertEquals(2L, failure.toCurrencyId)
    }
}
