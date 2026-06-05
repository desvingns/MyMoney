package com.kshavrin.mymoney.core.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

class CapitalBalanceDeltaTest {

    @Test
    fun `returns positive delta when balance exceeds starting capital`() {
        val result = capitalVsBalanceDelta(
            currentBalance = BigDecimal("1500.00"),
            startingCapital = BigDecimal("1000.00"),
        )
        assertEquals(0, BigDecimal("500.00").compareTo(result))
    }

    @Test
    fun `returns negative delta when balance is below starting capital`() {
        val result = capitalVsBalanceDelta(
            currentBalance = BigDecimal("800.00"),
            startingCapital = BigDecimal("1000.00"),
        )
        assertEquals(0, BigDecimal("-200.00").compareTo(result))
    }

    @Test
    fun `returns zero when balance equals starting capital`() {
        val result = capitalVsBalanceDelta(
            currentBalance = BigDecimal("1000.00"),
            startingCapital = BigDecimal("1000.00"),
        )
        assertEquals(0, BigDecimal.ZERO.compareTo(result))
    }

    @Test
    fun `returns zero when both values are zero`() {
        val result = capitalVsBalanceDelta(
            currentBalance = BigDecimal.ZERO,
            startingCapital = BigDecimal.ZERO,
        )
        assertEquals(0, BigDecimal.ZERO.compareTo(result))
    }

    @Test
    fun `preserves scale of subtraction result`() {
        val result = capitalVsBalanceDelta(
            currentBalance = BigDecimal("100.333"),
            startingCapital = BigDecimal("50.111"),
        )
        assertEquals(0, BigDecimal("50.222").compareTo(result))
    }
}
