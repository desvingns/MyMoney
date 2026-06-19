package com.kshavrin.mymoney.core.domain.usecase

import com.kshavrin.mymoney.core.domain.model.Currency
import com.kshavrin.mymoney.core.domain.model.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class RingGaugeCalculatorTest {
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

    private fun money(amount: String) = Money(BigDecimal(amount), usd)

    @Test
    fun `income present expense below income — green fill fraction equals net divided by income`() {
        val gauge =
            RingGaugeCalculator(
                income = money("100.00"),
                expense = money("10.00"),
                previousExpense = null,
            )

        assertFalse(gauge.isExpense)
        assertEquals(0.9f, gauge.fraction, 0.001f)
    }

    @Test
    fun `income present expense zero — green fill fraction is 1 point 0 all income retained`() {
        val gauge =
            RingGaugeCalculator(
                income = money("100.00"),
                expense = money("0.00"),
                previousExpense = null,
            )

        assertFalse(gauge.isExpense)
        assertEquals(1.0f, gauge.fraction, 0.001f)
    }

    @Test
    fun `income present expense equals income — break even green fill fraction is 0`() {
        val gauge =
            RingGaugeCalculator(
                income = money("100.00"),
                expense = money("100.00"),
                previousExpense = null,
            )

        assertFalse(gauge.isExpense)
        assertEquals(0.0f, gauge.fraction, 0.001f)
    }

    @Test
    fun `income present expense mildly exceeds income — red fill fraction equals overspend divided by income`() {
        val gauge =
            RingGaugeCalculator(
                income = money("100.00"),
                expense = money("120.00"),
                previousExpense = null,
            )

        assertTrue(gauge.isExpense)
        assertEquals(0.2f, gauge.fraction, 0.001f)
    }

    @Test
    fun `income present expense severely exceeds income — red fill fraction clamped to 1 point 0`() {
        val gauge =
            RingGaugeCalculator(
                income = money("100.00"),
                expense = money("220.00"),
                previousExpense = null,
            )

        assertTrue(gauge.isExpense)
        assertEquals(1.0f, gauge.fraction, 0.001f)
    }

    @Test
    fun `income zero expense present current less than previous — red fill fraction equals expense divided by previous`() {
        val gauge =
            RingGaugeCalculator(
                income = money("0.00"),
                expense = money("50.00"),
                previousExpense = money("100.00"),
            )

        assertTrue(gauge.isExpense)
        assertEquals(0.5f, gauge.fraction, 0.001f)
    }

    @Test
    fun `income zero expense present current greater than or equal to previous — red fill fraction clamped to 1 point 0`() {
        val gauge =
            RingGaugeCalculator(
                income = money("0.00"),
                expense = money("200.00"),
                previousExpense = money("100.00"),
            )

        assertTrue(gauge.isExpense)
        assertEquals(1.0f, gauge.fraction, 0.001f)
    }

    @Test
    fun `income zero expense present previous expense is zero — red fill fraction is 1 point 0 full ring`() {
        val gauge =
            RingGaugeCalculator(
                income = money("0.00"),
                expense = money("50.00"),
                previousExpense = money("0.00"),
            )

        assertTrue(gauge.isExpense)
        assertEquals(1.0f, gauge.fraction, 0.001f)
    }

    @Test
    fun `income zero expense present previous expense is null — red fill fraction is 1 point 0`() {
        val gauge =
            RingGaugeCalculator(
                income = money("0.00"),
                expense = money("50.00"),
                previousExpense = null,
            )

        assertTrue(gauge.isExpense)
        assertEquals(1.0f, gauge.fraction, 0.001f)
    }

    @Test
    fun `income zero expense zero no records — fraction is 0 point 0 empty ring`() {
        val gauge =
            RingGaugeCalculator(
                income = money("0.00"),
                expense = money("0.00"),
                previousExpense = null,
            )

        assertFalse(gauge.isExpense)
        assertEquals(0.0f, gauge.fraction, 0.001f)
    }
}
