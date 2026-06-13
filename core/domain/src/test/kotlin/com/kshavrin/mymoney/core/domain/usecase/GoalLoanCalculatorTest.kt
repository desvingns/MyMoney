package com.kshavrin.mymoney.core.domain.usecase

import com.kshavrin.mymoney.core.domain.model.GoalStatus
import com.kshavrin.mymoney.core.domain.model.LoanGoalInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test
import java.math.BigDecimal

class GoalLoanCalculatorTest {
    private val calc = GoalLoanCalculator()

    // -----------------------------------------------------------------------
    // Worked fixtures (authoritative, verbatim from SPEC)
    // -----------------------------------------------------------------------

    @Test
    fun `fixture 1 accumulation 10 months then zero-rate annuity`() {
        // target=2M, capital=0, down=500k, monthly=50k, rate=0%, term=120mo
        // → accumulate 10 months to reach down-payment, then 120 months loan
        val input =
            LoanGoalInput(
                targetAmount = BigDecimal("2000000"),
                startingCapital = BigDecimal.ZERO,
                downPayment = BigDecimal("500000"),
                annualRatePercent = BigDecimal.ZERO,
                termMonths = 120,
                monthlyContribution = BigDecimal("50000"),
            )

        val result = calc(input)

        assertEquals(10, result.accumulationMonths)
        assertMoneyEquals("1500000.00", result.principal)
        assertMoneyEquals("12500.00", result.baseMonthlyPayment)
        assertMoneyEquals("0.00", result.totalInterest)
        assertMoneyEquals("1500000.00", result.totalPaid)
        assertEquals(130, result.totalMonthsToPayoff)
        assertEquals(GoalStatus.ON_TRACK, result.status)
    }

    @Test
    fun `fixture 2 starting capital exceeds down-payment surplus reduces principal`() {
        // target=3M, capital=1.2M (> down=1M), monthly=30k, rate=0%, term=60mo
        // equity = max(capital, down) = 1.2M → principal = 3M - 1.2M = 1.8M
        // needed = max(down - capital, 0) = 0 → accumulationMonths = 0
        val input =
            LoanGoalInput(
                targetAmount = BigDecimal("3000000"),
                startingCapital = BigDecimal("1200000"),
                downPayment = BigDecimal("1000000"),
                annualRatePercent = BigDecimal.ZERO,
                termMonths = 60,
                monthlyContribution = BigDecimal("30000"),
            )

        val result = calc(input)

        assertEquals(0, result.accumulationMonths)
        assertMoneyEquals("1800000.00", result.principal)
        assertMoneyEquals("30000.00", result.baseMonthlyPayment)
        assertMoneyEquals("0.00", result.totalInterest)
        assertMoneyEquals("1800000.00", result.totalPaid)
        assertEquals(60, result.totalMonthsToPayoff)
        assertEquals(GoalStatus.ON_TRACK, result.status)
    }

    @Test
    fun `fixture 3 monthly zero with gap to down-payment returns UNREACHABLE but computes principal`() {
        // target=4M, capital=100k, down=800k, monthly=0, rate=10%, term=180mo
        // needed = 700k but monthly=0 → accumulationMonths=null → UNREACHABLE
        // principal still computed = 4M - max(800k,100k) = 3.2M (no exception)
        val input =
            LoanGoalInput(
                targetAmount = BigDecimal("4000000"),
                startingCapital = BigDecimal("100000"),
                downPayment = BigDecimal("800000"),
                annualRatePercent = BigDecimal("10"),
                termMonths = 180,
                monthlyContribution = BigDecimal.ZERO,
            )

        val result = calc(input)

        assertEquals(GoalStatus.UNREACHABLE, result.status)
        assertNull(result.accumulationMonths)
        assertNull(result.totalMonthsToPayoff)
        assertMoneyEquals("3200000.00", result.principal)
    }

    @Test
    fun `fixture 4 canonical annuity 1M at 12 percent per year for 12 months`() {
        // target=1M, capital=0, down=0, monthly=100k, rate=12%, term=12mo
        // equity = max(0,0) = 0 → principal = 1M
        // monthly rate = 1% → annuity = 88848.79, total = 1066185.48, interest = 66185.48
        val input =
            LoanGoalInput(
                targetAmount = BigDecimal("1000000"),
                startingCapital = BigDecimal.ZERO,
                downPayment = BigDecimal.ZERO,
                annualRatePercent = BigDecimal("12"),
                termMonths = 12,
                monthlyContribution = BigDecimal("100000"),
            )

        val result = calc(input)

        assertEquals(0, result.accumulationMonths)
        assertMoneyEquals("1000000.00", result.principal)
        assertMoneyEquals("88848.79", result.baseMonthlyPayment)
        assertMoneyEquals("66185.48", result.totalInterest)
        assertMoneyEquals("1066185.48", result.totalPaid)
        assertEquals(12, result.totalMonthsToPayoff)
        assertEquals(GoalStatus.ON_TRACK, result.status)
    }

    // -----------------------------------------------------------------------
    // Edge cases
    // -----------------------------------------------------------------------

    @Test
    fun `principal zero when starting capital meets or exceeds target`() {
        val input =
            LoanGoalInput(
                targetAmount = BigDecimal("500000"),
                startingCapital = BigDecimal("600000"),
                downPayment = BigDecimal("200000"),
                annualRatePercent = BigDecimal("10"),
                termMonths = 24,
                monthlyContribution = BigDecimal("30000"),
            )

        val result = calc(input)

        assertMoneyEquals("0.00", result.principal)
        assertMoneyEquals("0.00", result.baseMonthlyPayment)
        assertMoneyEquals("0.00", result.totalPaid)
        assertMoneyEquals("0.00", result.totalInterest)
    }

    @Test
    fun `term months zero throws IllegalArgumentException`() {
        val input =
            LoanGoalInput(
                targetAmount = BigDecimal("1000000"),
                startingCapital = BigDecimal.ZERO,
                downPayment = BigDecimal.ZERO,
                annualRatePercent = BigDecimal("5"),
                termMonths = 0,
                monthlyContribution = BigDecimal("100000"),
            )

        try {
            calc(input)
            fail("expected IllegalArgumentException for termMonths=0")
        } catch (_: IllegalArgumentException) {
        }
    }

    // -----------------------------------------------------------------------
    // Helper
    // -----------------------------------------------------------------------

    private fun assertMoneyEquals(
        expected: String,
        actual: BigDecimal,
    ) {
        assertEquals(
            "expected $expected but was ${actual.toPlainString()}",
            0,
            actual.compareTo(BigDecimal(expected)),
        )
    }
}
