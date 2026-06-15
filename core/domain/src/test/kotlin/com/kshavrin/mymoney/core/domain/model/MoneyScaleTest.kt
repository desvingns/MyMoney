package com.kshavrin.mymoney.core.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

class MoneyScaleTest {
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

    private val bhd =
        Currency(
            id = 2L,
            code = "BHD",
            symbol = "BD",
            name = "Bahraini Dinar",
            decimalDigits = 3,
            isActive = true,
            sortOrder = 1,
        )

    private val jpy =
        Currency(
            id = 3L,
            code = "JPY",
            symbol = "Y",
            name = "Yen",
            decimalDigits = 0,
            isActive = true,
            sortOrder = 2,
        )

    @Test
    fun `toMoneyScale rounds floating point tails for two-decimal currencies`() {
        assertScaledValue(
            actual = BigDecimal("-1182337.0799999996").toMoneyScale(usd),
            expectedValue = "-1182337.08",
            expectedScale = 2,
        )
    }

    @Test
    fun `toMoneyScale rounds half up for two-decimal currencies`() {
        assertScaledValue(
            actual = BigDecimal("10.005").toMoneyScale(usd),
            expectedValue = "10.01",
            expectedScale = 2,
        )
    }

    @Test
    fun `toMoneyScale caps three-decimal currencies at two fractional digits`() {
        assertScaledValue(
            actual = BigDecimal("12.3456").toMoneyScale(bhd),
            expectedValue = "12.35",
            expectedScale = 2,
        )
    }

    @Test
    fun `toMoneyScale rounds zero-decimal currencies to whole units`() {
        assertScaledValue(
            actual = BigDecimal("10.5").toMoneyScale(jpy),
            expectedValue = "11",
            expectedScale = 0,
        )
    }

    @Test
    fun `toMoneyScale preserves zero with the target scale`() {
        assertScaledValue(
            actual = BigDecimal.ZERO.toMoneyScale(usd),
            expectedValue = "0.00",
            expectedScale = 2,
        )
    }

    private fun assertScaledValue(
        actual: BigDecimal,
        expectedValue: String,
        expectedScale: Int,
    ) {
        assertEquals(0, BigDecimal(expectedValue).compareTo(actual))
        assertEquals(expectedScale, actual.scale())
    }
}
