package com.kshavrin.mymoney.core.domain.usecase

import com.kshavrin.mymoney.core.domain.model.Transaction
import com.kshavrin.mymoney.core.domain.model.TransactionKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class IntradayTrendCalculatorTest {
    private val calculator = IntradayTrendCalculator()
    private val day = LocalDate.of(2026, 5, 10)
    private val utc = ZoneId.of("UTC")

    // ── helpers ──────────────────────────────────────────────────────────────

    private fun tx(
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

    private fun transfer(
        amount: String,
        occurredAt: String,
    ) =
        tx(TransactionKind.Transfer, amount, occurredAt)

    private fun income(
        amount: String,
        occurredAt: String,
    ) =
        tx(TransactionKind.Income, amount, occurredAt)

    private fun expense(
        amount: String,
        occurredAt: String,
    ) =
        tx(TransactionKind.Expense, amount, occurredAt)

    private fun bd(value: String): BigDecimal = BigDecimal(value)

    // Every result value must carry exactly 2 decimal places (MONEY_SCALE = 2).
    private fun assertScaleIs2(result: List<BigDecimal>) {
        result.forEachIndexed { idx, v ->
            assertEquals("slot $idx must have scale 2", 2, v.scale())
        }
    }

    // Value equality ignoring BigDecimal scale (compareTo == 0).
    private fun assertSeriesEquals(
        expected: List<String>,
        actual: List<BigDecimal>,
    ) {
        assertEquals("list size", expected.size, actual.size)
        expected.forEachIndexed { idx, exp ->
            assertEquals(
                "slot $idx: expected $exp got ${actual[idx]}",
                0,
                bd(exp).compareTo(actual[idx]),
            )
        }
    }

    // ── SPEC required fixtures ────────────────────────────────────────────────

    @Test
    fun `Ex1 income then expense trims to last active slot`() {
        // +100 @ 09:30 → slot4, −40 @ 13:00 → slot6
        // slots 0–3 = 0, slot4 = 100, slot5 = 100 (stagnation), slot6 = 60 → 7 points
        val result =
            calculator.buildIntradaySeries(
                listOf(
                    income("100", "2026-05-10T09:30:00Z"),
                    expense("40", "2026-05-10T13:00:00Z"),
                ),
                day,
                utc,
            )
        assertSeriesEquals(listOf("0", "0", "0", "0", "100", "100", "60"), result)
        assertScaleIs2(result)
    }

    @Test
    fun `Ex2 single income at 00-10 yields exactly one point`() {
        // +50 @ 00:10 → slot0; lastActiveSlot = 0 → list of 1 element
        val result =
            calculator.buildIntradaySeries(
                listOf(income("50", "2026-05-10T00:10:00Z")),
                day,
                utc,
            )
        assertSeriesEquals(listOf("50"), result)
        assertScaleIs2(result)
    }

    @Test
    fun `Ex3 boundary at 02-00 falls into slot1 and 23-59 fills all 12 slots`() {
        // 02:00:00 → hour=2 → slot1 (NOT slot0); 23:59 → slot11
        // slots: [0, 20, 20, 20, 20, 20, 20, 20, 20, 20, 20, 50] = 12 points
        val result =
            calculator.buildIntradaySeries(
                listOf(
                    income("20", "2026-05-10T02:00:00Z"),
                    income("30", "2026-05-10T23:59:00Z"),
                ),
                day,
                utc,
            )
        assertSeriesEquals(
            listOf("0", "20", "20", "20", "20", "20", "20", "20", "20", "20", "20", "50"),
            result,
        )
        assertEquals("must return all 12 slots", 12, result.size)
        assertScaleIs2(result)
    }

    @Test
    fun `Ex4 no transactions yields empty list`() {
        val result = calculator.buildIntradaySeries(emptyList(), day, utc)
        assertTrue(result.isEmpty())
    }

    // ── SPEC additional cases ─────────────────────────────────────────────────

    @Test
    fun `transfer is excluded from net even on an otherwise active day`() {
        // income 100 @ slot0, transfer 999 @ slot0 → transfer ignored → [100]
        val result =
            calculator.buildIntradaySeries(
                listOf(
                    income("100", "2026-05-10T01:00:00Z"),
                    transfer("999", "2026-05-10T01:30:00Z"),
                ),
                day,
                utc,
            )
        assertSeriesEquals(listOf("100"), result)
    }

    @Test
    fun `transfer-only day yields empty list`() {
        // no income/expense → lastActiveSlot = -1 → emptyList
        val result =
            calculator.buildIntradaySeries(
                listOf(transfer("500", "2026-05-10T10:00:00Z")),
                day,
                utc,
            )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `leading empty slots before first activity stay flat at zero`() {
        // income 75 @ slot5 (10:00); slots 0–4 must be 0, slot5 = 75
        val result =
            calculator.buildIntradaySeries(
                listOf(income("75", "2026-05-10T10:00:00Z")),
                day,
                utc,
            )
        assertSeriesEquals(listOf("0", "0", "0", "0", "0", "75"), result)
    }

    @Test
    fun `internal empty slot between two active slots carries running cumulative`() {
        // income 40 @ slot1 (03:00), income 10 @ slot3 (06:00)
        // slot2 has no transactions → cumulative stays at 40
        val result =
            calculator.buildIntradaySeries(
                listOf(
                    income("40", "2026-05-10T03:00:00Z"),
                    income("10", "2026-05-10T06:00:00Z"),
                ),
                day,
                utc,
            )
        assertSeriesEquals(listOf("0", "40", "40", "50"), result)
    }

    @Test
    fun `transaction exactly at 23-59 lands in slot 11`() {
        val result =
            calculator.buildIntradaySeries(
                listOf(income("7", "2026-05-10T23:59:59Z")),
                day,
                utc,
            )
        assertEquals("must return 12 slots (lastActive = slot11)", 12, result.size)
        assertSeriesEquals(
            listOf("0", "0", "0", "0", "0", "0", "0", "0", "0", "0", "0", "7"),
            result,
        )
    }

    @Test
    fun `expense before any income produces negative cumulative`() {
        // expense 30 @ slot0 → cumulative goes negative; not clamped to 0
        val result =
            calculator.buildIntradaySeries(
                listOf(expense("30", "2026-05-10T00:30:00Z")),
                day,
                utc,
            )
        assertSeriesEquals(listOf("-30"), result)
        assertEquals("negative value must not be clamped", -1, result[0].signum())
    }

    @Test
    fun `expense first then income can cross zero midway`() {
        // expense 50 @ slot0 → −50; income 80 @ slot2 → +30
        val result =
            calculator.buildIntradaySeries(
                listOf(
                    expense("50", "2026-05-10T01:00:00Z"),
                    income("80", "2026-05-10T04:00:00Z"),
                ),
                day,
                utc,
            )
        assertSeriesEquals(listOf("-50", "-50", "30"), result)
    }

    @Test
    fun `multiple transactions in the same slot accumulate before being added to cumulative`() {
        // slot0 (00–02): +10, +20, −5 → net slot0 = 25; no other slots
        val result =
            calculator.buildIntradaySeries(
                listOf(
                    income("10", "2026-05-10T00:00:00Z"),
                    income("20", "2026-05-10T00:45:00Z"),
                    expense("5", "2026-05-10T01:30:00Z"),
                ),
                day,
                utc,
            )
        assertSeriesEquals(listOf("25"), result)
    }

    @Test
    fun `transactions from a different day are ignored`() {
        // Only the transaction for `day` (2026-05-10) must be counted.
        // The other two are one day earlier and one day later.
        val result =
            calculator.buildIntradaySeries(
                listOf(
                    income("100", "2026-05-09T12:00:00Z"), // different day — must be ignored
                    income("50", "2026-05-10T08:00:00Z"), // slot4 → counted
                    income("200", "2026-05-11T08:00:00Z"), // different day — must be ignored
                ),
                day,
                utc,
            )
        assertSeriesEquals(listOf("0", "0", "0", "0", "50"), result)
    }

    @Test
    fun `all result values carry exactly 2 decimal places`() {
        // Verify that setScale(2, HALF_UP) is applied to every emitted point,
        // including zero-valued leading slots and stagnation slots.
        val result =
            calculator.buildIntradaySeries(
                listOf(
                    income("100", "2026-05-10T09:30:00Z"),
                    expense("40", "2026-05-10T13:00:00Z"),
                ),
                day,
                utc,
            )
        assertScaleIs2(result)
    }

    @Test
    fun `fractional amounts are rounded to 2dp with HALF_UP`() {
        // 0.005 rounds to 0.01 under HALF_UP; 0.004 rounds to 0.00
        val result =
            calculator.buildIntradaySeries(
                listOf(
                    income("0.005", "2026-05-10T00:30:00Z"),
                ),
                day,
                utc,
            )
        assertEquals("scale must be 2", 2, result[0].scale())
        assertEquals(0, bd("0.01").compareTo(result[0]))
    }

    @Test
    fun `timezone parameter governs which slot a transaction falls into`() {
        // 2026-05-10T01:00:00Z = 2026-05-10T04:00:00 in UTC+3
        // UTC+3: hour=4 → slot2; UTC: hour=1 → slot0
        val utcPlus3 = ZoneId.of("UTC+3")
        val resultUtc =
            calculator.buildIntradaySeries(
                listOf(income("100", "2026-05-10T01:00:00Z")),
                day,
                utc,
            )
        val resultUtcPlus3 =
            calculator.buildIntradaySeries(
                listOf(income("100", "2026-05-10T01:00:00Z")),
                day,
                utcPlus3,
            )
        // UTC: slot0 → 1 point
        assertEquals(1, resultUtc.size)
        // UTC+3: slot2 → 3 points (slots 0, 1 = 0, slot2 = 100)
        assertEquals(3, resultUtcPlus3.size)
        assertEquals(0, bd("100").compareTo(resultUtcPlus3.last()))
    }
}
