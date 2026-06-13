package com.kshavrin.mymoney.core.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class MoneyTest {
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

    // Same decimalDigits as USD but a different id: the currency guard keys on id.
    private val eur =
        Currency(
            id = 2L,
            code = "EUR",
            symbol = "€",
            name = "Euro",
            decimalDigits = 2,
            isActive = true,
            sortOrder = 1,
        )

    private val jpy =
        Currency(
            id = 3L,
            code = "JPY",
            symbol = "¥",
            name = "Yen",
            decimalDigits = 0,
            isActive = true,
            sortOrder = 2,
        )

    private fun usd(value: String) = Money(BigDecimal(value), usd)

    @Test
    fun `plus adds amounts of the same currency`() {
        val result = usd("10.00") + usd("5.50")
        assertEquals(0, BigDecimal("15.50").compareTo(result.amount))
        assertEquals(usd, result.currency)
    }

    @Test
    fun `plus enforces currency scale via HALF_UP rounding`() {
        val result = usd("10.005") + usd("0.001")
        // 10.006 rounded HALF_UP to 2 decimals -> 10.01
        assertEquals(0, BigDecimal("10.01").compareTo(result.amount))
        assertEquals(2, result.amount.scale())
    }

    @Test
    fun `plus rejects different currencies`() {
        val ex =
            assertThrows(IllegalArgumentException::class.java) {
                Money(BigDecimal("1.00"), usd) + Money(BigDecimal("1.00"), eur)
            }
        assertTrue(ex.message!!.contains("USD"))
        assertTrue(ex.message!!.contains("EUR"))
    }

    @Test
    fun `minus subtracts amounts of the same currency`() {
        val result = usd("10.00") - usd("3.25")
        assertEquals(0, BigDecimal("6.75").compareTo(result.amount))
        assertEquals(2, result.amount.scale())
    }

    @Test
    fun `minus can produce a negative amount`() {
        val result = usd("1.00") - usd("3.00")
        assertEquals(0, BigDecimal("-2.00").compareTo(result.amount))
        assertTrue(result.isNegative())
    }

    @Test
    fun `minus rejects different currencies`() {
        assertThrows(IllegalArgumentException::class.java) {
            Money(BigDecimal("5.00"), usd) - Money(BigDecimal("1.00"), eur)
        }
    }

    @Test
    fun `times multiplies and rounds to currency scale`() {
        val result = usd("2.50") * BigDecimal("3")
        assertEquals(0, BigDecimal("7.50").compareTo(result.amount))
        assertEquals(2, result.amount.scale())
    }

    @Test
    fun `times rounds HALF_UP to currency scale`() {
        val result = usd("0.333") * BigDecimal("3")
        // 0.999 rounded to 2 decimals -> 1.00
        assertEquals(0, BigDecimal("1.00").compareTo(result.amount))
        assertEquals(2, result.amount.scale())
    }

    @Test
    fun `times honours a zero-decimal currency scale`() {
        val result = Money(BigDecimal("100"), jpy) * BigDecimal("1.5")
        // 150.0 rounded to 0 decimals -> 150
        assertEquals(0, BigDecimal("150").compareTo(result.amount))
        assertEquals(0, result.amount.scale())
    }

    @Test
    fun `sign predicates classify positive negative and zero`() {
        assertTrue(usd("5.00").isPositive())
        assertFalse(usd("5.00").isNegative())
        assertFalse(usd("5.00").isZero())

        assertTrue(usd("-5.00").isNegative())
        assertFalse(usd("-5.00").isPositive())

        assertTrue(usd("0.00").isZero())
        assertFalse(usd("0.00").isPositive())
        assertFalse(usd("0.00").isNegative())
    }

    @Test
    fun `zero factory builds zero amount scaled to the currency`() {
        val zero = Money.zero(usd)
        assertTrue(zero.isZero())
        assertEquals(usd, zero.currency)
        assertEquals(2, zero.amount.scale())
    }

    @Test
    fun `zero factory respects a zero-decimal currency`() {
        val zero = Money.zero(jpy)
        assertTrue(zero.isZero())
        assertEquals(0, zero.amount.scale())
    }
}
