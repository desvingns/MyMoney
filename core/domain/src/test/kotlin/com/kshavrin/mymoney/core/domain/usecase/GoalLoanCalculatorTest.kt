package com.kshavrin.mymoney.core.domain.usecase

import com.kshavrin.mymoney.core.domain.model.LoanGoalInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class GoalLoanCalculatorTest {

    private val calc = GoalLoanCalculator()

    @Test
    fun zero_rate_no_overpayment() {
        val input = LoanGoalInput(
            targetAmount = BigDecimal("12000"),
            startingCapital = BigDecimal.ZERO,
            annualRatePercent = BigDecimal.ZERO,
            termMonths = 12,
            monthlyContribution = BigDecimal("1000"),
        )

        val result = calc(input)

        assertEquals(BigDecimal("12000.00"), result.principal)
        assertEquals(BigDecimal("1000.00"), result.baseMonthlyPayment)
        assertEquals(BigDecimal("0.00"), result.totalInterest)
        assertEquals(BigDecimal("12000.00"), result.totalPaid)
        assertEquals(12, result.monthsToPayoff)
        assertFalse(result.underfunded)
        assertFalse(result.overpaymentApplied)
    }

    @Test
    fun normal_annuity_no_overpayment_zero_rate_exact_equality() {
        val input = LoanGoalInput(
            targetAmount = BigDecimal("6000"),
            startingCapital = BigDecimal.ZERO,
            annualRatePercent = BigDecimal.ZERO,
            termMonths = 6,
            monthlyContribution = BigDecimal("1000"),
        )

        val result = calc(input)

        assertEquals(BigDecimal("6000.00"), result.principal)
        assertEquals(BigDecimal("1000.00"), result.baseMonthlyPayment)
        assertEquals(6, result.monthsToPayoff)
        assertFalse(result.underfunded)
        assertFalse(result.overpaymentApplied)
        assertEquals(BigDecimal("0.00"), result.totalInterest)
        assertEquals(BigDecimal("6000.00"), result.totalPaid)
    }

    @Test
    fun overpayment_strictly_reduces_interest_vs_normal() {
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
        assertFalse(over.underfunded)
        assertTrue(over.overpaymentApplied)
        assertTrue(over.monthsToPayoff <= baseInput.termMonths)
        assertTrue(
            "overpayment must strictly reduce interest (base=${base.totalInterest}, over=${over.totalInterest})",
            over.totalInterest < base.totalInterest,
        )
        assertTrue(over.totalPaid < base.totalPaid)
        assertNotEquals(BigDecimal.ZERO.setScale(2), over.totalInterest)
    }

    @Test
    fun underfunded_when_monthly_below_base_annuity() {
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
        assertEquals(BigDecimal("888.49"), result.baseMonthlyPayment)
        assertEquals(BigDecimal("10661.85"), result.totalPaid)
        assertEquals(BigDecimal("661.85"), result.totalInterest)
    }

    @Test
    fun zero_principal_when_starting_capital_covers_target() {
        val input = LoanGoalInput(
            targetAmount = BigDecimal("5000"),
            startingCapital = BigDecimal("5000"),
            annualRatePercent = BigDecimal("10"),
            termMonths = 24,
            monthlyContribution = BigDecimal("300"),
        )

        val result = calc(input)

        assertEquals(BigDecimal("0.00"), result.principal)
        assertEquals(BigDecimal("0.00"), result.baseMonthlyPayment)
        assertEquals(BigDecimal("0.00"), result.totalInterest)
        assertEquals(BigDecimal("0.00"), result.totalPaid)
        assertEquals(0, result.monthsToPayoff)
        assertFalse(result.underfunded)
        assertFalse(result.overpaymentApplied)
    }

    @Test
    fun zero_principal_when_starting_capital_exceeds_target() {
        val input = LoanGoalInput(
            targetAmount = BigDecimal("3000"),
            startingCapital = BigDecimal("5000"),
            annualRatePercent = BigDecimal("10"),
            termMonths = 24,
            monthlyContribution = BigDecimal("300"),
        )

        val result = calc(input)

        assertEquals(BigDecimal("0.00"), result.principal)
        assertEquals(0, result.monthsToPayoff)
    }

    @Test
    fun term_months_must_be_at_least_one() {
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
    fun term_months_negative_rejected() {
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
}
