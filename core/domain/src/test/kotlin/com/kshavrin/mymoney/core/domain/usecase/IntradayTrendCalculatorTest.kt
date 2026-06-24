package com.kshavrin.mymoney.core.domain.usecase

import com.kshavrin.mymoney.core.domain.model.Transaction
import com.kshavrin.mymoney.core.domain.model.TransactionKind
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class IntradayTrendCalculatorTest {
    private val calculator = IntradayTrendCalculator()
    private val day = LocalDate.of(2026, 5, 10)
    private val utc = ZoneId.of("UTC")

    private fun transaction(
        kind: TransactionKind,
        amount: String,
        occurredAt: String,
    ) = Transaction(
        id = 0L,
        kind = kind,
        amount = BigDecimal(amount),
        currencyId = 1L,
        accountId = 1L,
        categoryId = 1L,
        note = null,
        occurredAt = Instant.parse(occurredAt),
        createdAt = Instant.parse(occurredAt),
        updatedAt = Instant.parse(occurredAt),
        isDeleted = false,
        toAccountId = null,
        toAmount = null,
        exchangeRate = null,
    )

    @Test
    fun `Ex1 income then expense trims to last active slot`() {
        val result =
            calculator.buildIntradaySeries(
                listOf(
                    transaction(TransactionKind.Income, "100", "2026-05-10T09:30:00Z"),
                    transaction(TransactionKind.Expense, "40", "2026-05-10T13:00:00Z"),
                ),
                day,
                utc,
            )
        assertEquals(
            listOf("0.00", "0.00", "0.00", "0.00", "100.00", "100.00", "60.00").map(::BigDecimal),
            result,
        )
    }

    @Test
    fun `Ex2 single early income yields one point`() {
        val result =
            calculator.buildIntradaySeries(
                listOf(transaction(TransactionKind.Income, "50", "2026-05-10T00:10:00Z")),
                day,
                utc,
            )
        assertEquals(listOf(BigDecimal("50.00")), result)
    }

    @Test
    fun `Ex3 boundary at 02-00 falls into later slot and 23-59 fills 12`() {
        val result =
            calculator.buildIntradaySeries(
                listOf(
                    transaction(TransactionKind.Income, "20", "2026-05-10T02:00:00Z"),
                    transaction(TransactionKind.Income, "30", "2026-05-10T23:59:00Z"),
                ),
                day,
                utc,
            )
        assertEquals(
            listOf("0.00", "20.00", "20.00", "20.00", "20.00", "20.00", "20.00", "20.00", "20.00", "20.00", "20.00", "50.00")
                .map(::BigDecimal),
            result,
        )
    }

    @Test
    fun `Ex4 no transactions yields empty`() {
        assertEquals(emptyList<BigDecimal>(), calculator.buildIntradaySeries(emptyList(), day, utc))
    }

    @Test
    fun `transfers are excluded from the net`() {
        val result =
            calculator.buildIntradaySeries(
                listOf(
                    transaction(TransactionKind.Income, "100", "2026-05-10T01:00:00Z"),
                    transaction(TransactionKind.Transfer, "999", "2026-05-10T01:30:00Z"),
                ),
                day,
                utc,
            )
        assertEquals(listOf(BigDecimal("100.00")), result)
    }
}
