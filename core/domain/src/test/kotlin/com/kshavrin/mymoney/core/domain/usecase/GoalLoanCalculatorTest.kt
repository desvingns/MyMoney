package com.kshavrin.mymoney.core.domain.usecase

import com.kshavrin.mymoney.core.domain.model.LoanGoalInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class GoalLoanCalculatorTest {

    private val calc = GoalLoanCalculator()

    @Test
    fun `zero-rate projection keeps the original payment contract`() {
        val input = LoanGoalInput(
            targetAmount = BigDecimal("12000"),
            startingCapital = BigDecimal.ZERO,
            annualRatePercent = BigDecimal.ZERO,
            termMonths = 12,
            monthlyContribution = BigDecimal("1000"),
        )

        val result = calc(input)

        assertMoneyEquals("12000.00", result.principal)
        assertMoneyEquals("1000.00", result.baseMonthlyPayment)
        assertMoneyEquals("1000.00", result.finalMonthlyPayment)
        assertMoneyEquals("0.00", result.totalInterest)
        assertMoneyEquals("12000.00", result.totalPaid)
        assertMoneyEquals("0.00", result.interestSavedVsBaseline)
        assertEquals(12, result.monthsToPayoff)
        assertFalse(result.underfunded)
        assertFalse(result.overpaymentApplied)
    }

    @Test
    fun `zero-rate exact monthly contribution stays on the baseline path`() {
        val input = LoanGoalInput(
            targetAmount = BigDecimal("6000"),
            startingCapital = BigDecimal.ZERO,
            annualRatePercent = BigDecimal.ZERO,
            termMonths = 6,
            monthlyContribution = BigDecimal("1000"),
        )

        val result = calc(input)

        assertMoneyEquals("6000.00", result.principal)
        assertMoneyEquals("1000.00", result.baseMonthlyPayment)
        assertMoneyEquals("1000.00", result.finalMonthlyPayment)
        assertEquals(6, result.monthsToPayoff)
        assertFalse(result.underfunded)
        assertFalse(result.overpaymentApplied)
        assertMoneyEquals("0.00", result.totalInterest)
        assertMoneyEquals("6000.00", result.totalPaid)
        assertMoneyEquals("0.00", result.interestSavedVsBaseline)
    }

    @Test
    fun `overpayment keeps the term fixed and lowers the contractual payment`() {
        val baseInput = LoanGoalInput(
            targetAmount = BigDecimal("10000"),
            startingCapital = BigDecimal.ZERO,
            annualRatePercent = BigDecimal("12"),
            termMonths = 12,
            monthlyContribution = BigDecimal("500"),
        )
        val overInput = baseInput.copy(monthlyContribution = BigDecimal("1000"))

        val base = calc(baseInput)
        val over = calc(overInput)

        assertTrue(base.underfunded)
        assertMoneyEquals("661.85", base.totalInterest)
        assertFalse(over.underfunded)
        assertTrue(over.overpaymentApplied)
        // Hand-checked: 1000/month keeps the term while the baseline-anchored surplus lowers the annuity floor.
        assertEquals(baseInput.termMonths, over.monthsToPayoff)
        assertMoneyEquals("888.49", over.baseMonthlyPayment)
        assertMoneyEquals("543.86", over.finalMonthlyPayment)
        assertMoneyEquals("618.46", over.totalInterest)
        assertMoneyEquals("10618.46", over.totalPaid)
        assertMoneyEquals("43.39", over.interestSavedVsBaseline)
        assertTrue(over.finalMonthlyPayment < over.baseMonthlyPayment)
        assertTrue(over.totalInterest < base.totalInterest)
        assertTrue(over.totalPaid < base.totalPaid)
        assertEquals(base.totalInterest.subtract(over.totalInterest), over.interestSavedVsBaseline)
    }

    @Test
    fun `surplus is anchored to the original annuity instead of a recomputed payment`() {
        val input = LoanGoalInput(
            targetAmount = BigDecimal("8000"),
            startingCapital = BigDecimal.ZERO,
            annualRatePercent = BigDecimal("9"),
            termMonths = 18,
            monthlyContribution = BigDecimal("500"),
        )

        val result = calc(input)

        assertFalse(result.underfunded)
        assertTrue(result.overpaymentApplied)
        assertEquals(18, result.monthsToPayoff)
        assertMoneyEquals("476.78", result.baseMonthlyPayment)
        assertMoneyEquals("395.12", result.finalMonthlyPayment)
        assertMoneyEquals("567.07", result.totalInterest)
        assertMoneyEquals("8567.07", result.totalPaid)
        assertMoneyEquals("14.99", result.interestSavedVsBaseline)
        assertTrue(result.finalMonthlyPayment < result.baseMonthlyPayment)
    }

    @Test
    fun `early zero balance keeps the last non-zero declining payment`() {
        val input = LoanGoalInput(
            targetAmount = BigDecimal("10000"),
            startingCapital = BigDecimal.ZERO,
            annualRatePercent = BigDecimal("12"),
            termMonths = 12,
            monthlyContribution = BigDecimal("2000"),
        )

        val result = calc(input)

        assertFalse(result.underfunded)
        assertTrue(result.overpaymentApplied)
        assertEquals(input.termMonths, result.monthsToPayoff)
        assertMoneyEquals("888.49", result.baseMonthlyPayment)
        assertMoneyEquals("31.91", result.finalMonthlyPayment)
        assertMoneyEquals("336.30", result.totalInterest)
        assertMoneyEquals("10336.30", result.totalPaid)
        assertMoneyEquals("325.56", result.interestSavedVsBaseline)
        assertTrue(result.finalMonthlyPayment > BigDecimal.ZERO)
        assertTrue(result.finalMonthlyPayment < result.baseMonthlyPayment)
        assertTrue(result.interestSavedVsBaseline > BigDecimal.ZERO)
    }

    @Test
    fun `underfunded projection keeps the baseline totals and reports no savings`() {
        val input = LoanGoalInput(
            targetAmount = BigDecimal("10000"),
            startingCapital = BigDecimal.ZERO,
            annualRatePercent = BigDecimal("12"),
            termMonths = 12,
            monthlyContribution = BigDecimal("500"),
        )

        val result = calc(input)

        assertTrue(result.underfunded)
        assertFalse(result.overpaymentApplied)
        assertEquals(12, result.monthsToPayoff)
        assertMoneyEquals("888.49", result.baseMonthlyPayment)
        assertMoneyEquals("888.49", result.finalMonthlyPayment)
        assertMoneyEquals("10661.85", result.totalPaid)
        assertMoneyEquals("661.85", result.totalInterest)
        assertMoneyEquals("0.00", result.interestSavedVsBaseline)
    }

    @Test
    fun `starting capital equal to target returns a zero loan projection`() {
        val input = LoanGoalInput(
            targetAmount = BigDecimal("5000"),
            startingCapital = BigDecimal("5000"),
            annualRatePercent = BigDecimal("10"),
            termMonths = 24,
            monthlyContribution = BigDecimal("300"),
        )

        val result = calc(input)

        assertMoneyEquals("0.00", result.principal)
        assertMoneyEquals("0.00", result.baseMonthlyPayment)
        assertMoneyEquals("0.00", result.finalMonthlyPayment)
        assertMoneyEquals("0.00", result.totalInterest)
        assertMoneyEquals("0.00", result.totalPaid)
        assertMoneyEquals("0.00", result.interestSavedVsBaseline)
        assertEquals(0, result.monthsToPayoff)
        assertFalse(result.underfunded)
        assertFalse(result.overpaymentApplied)
    }

    @Test
    fun `starting capital above target also returns a zero loan projection`() {
        val input = LoanGoalInput(
            targetAmount = BigDecimal("3000"),
            startingCapital = BigDecimal("5000"),
            annualRatePercent = BigDecimal("10"),
            termMonths = 24,
            monthlyContribution = BigDecimal("300"),
        )

        val result = calc(input)

        assertMoneyEquals("0.00", result.principal)
        assertMoneyEquals("0.00", result.baseMonthlyPayment)
        assertMoneyEquals("0.00", result.finalMonthlyPayment)
        assertMoneyEquals("0.00", result.totalInterest)
        assertMoneyEquals("0.00", result.totalPaid)
        assertMoneyEquals("0.00", result.interestSavedVsBaseline)
        assertEquals(0, result.monthsToPayoff)
    }

    @Test
    fun `term months must be at least one`() {
        val input = LoanGoalInput(
            targetAmount = BigDecimal("1000"),
            startingCapital = BigDecimal.ZERO,
            annualRatePercent = BigDecimal("5"),
            termMonths = 0,
            monthlyContribution = BigDecimal("100"),
        )

        try {
            calc(input)
            org.junit.Assert.fail("expected IllegalArgumentException for termMonths=0")
        } catch (_: IllegalArgumentException) {
        }
    }

    @Test
    fun `negative term months are rejected`() {
        val input = LoanGoalInput(
            targetAmount = BigDecimal("1000"),
            startingCapital = BigDecimal.ZERO,
            annualRatePercent = BigDecimal("5"),
            termMonths = -3,
            monthlyContribution = BigDecimal("100"),
        )

        try {
            calc(input)
            org.junit.Assert.fail("expected IllegalArgumentException for negative termMonths")
        } catch (_: IllegalArgumentException) {
        }
    }

    private fun assertMoneyEquals(expected: String, actual: BigDecimal) {
        assertEquals(0, actual.compareTo(BigDecimal(expected)))
    }
}
