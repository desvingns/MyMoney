package com.kshavrin.mymoney.core.domain.usecase

import com.kshavrin.mymoney.core.domain.model.LoanGoalInput
import com.kshavrin.mymoney.core.domain.model.LoanProjection
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import javax.inject.Inject

class GoalLoanCalculator @Inject constructor() {

    operator fun invoke(input: LoanGoalInput): LoanProjection {
        require(input.termMonths >= 1) { "termMonths must be >= 1" }

        val mc = MathContext.DECIMAL64
        val principal = input.targetAmount.subtract(input.startingCapital, mc).max(BigDecimal.ZERO)
        val n = input.termMonths

        if (principal.signum() == 0) {
            val zero = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
            return LoanProjection(
                principal = zero,
                baseMonthlyPayment = zero,
                finalMonthlyPayment = zero,
                totalInterest = zero,
                totalPaid = zero,
                interestSavedVsBaseline = zero,
                monthsToPayoff = 0,
                underfunded = false,
                overpaymentApplied = false,
            )
        }

        val i = input.annualRatePercent
            .divide(BigDecimal(100), mc)
            .divide(BigDecimal(12), mc)
        val nBig = BigDecimal(n)

        val baseAnnuity = annuity(principal, i, n, mc)
        val baselineTotalPaid = baseAnnuity.multiply(nBig, mc)
        val baselineTotalInterest = baselineTotalPaid.subtract(principal, mc)

        if (input.monthlyContribution < baseAnnuity) {
            return LoanProjection(
                principal = principal.setScale(2, RoundingMode.HALF_UP),
                baseMonthlyPayment = baseAnnuity.setScale(2, RoundingMode.HALF_UP),
                finalMonthlyPayment = baseAnnuity.setScale(2, RoundingMode.HALF_UP),
                totalInterest = baselineTotalInterest.setScale(2, RoundingMode.HALF_UP),
                totalPaid = baselineTotalPaid.setScale(2, RoundingMode.HALF_UP),
                interestSavedVsBaseline = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                monthsToPayoff = n,
                underfunded = true,
                overpaymentApplied = false,
            )
        }

        if (input.monthlyContribution.compareTo(baseAnnuity) == 0) {
            return LoanProjection(
                principal = principal.setScale(2, RoundingMode.HALF_UP),
                baseMonthlyPayment = baseAnnuity.setScale(2, RoundingMode.HALF_UP),
                finalMonthlyPayment = baseAnnuity.setScale(2, RoundingMode.HALF_UP),
                totalInterest = baselineTotalInterest.setScale(2, RoundingMode.HALF_UP),
                totalPaid = baselineTotalPaid.setScale(2, RoundingMode.HALF_UP),
                interestSavedVsBaseline = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                monthsToPayoff = n,
                underfunded = false,
                overpaymentApplied = false,
            )
        }

        // Keep the horizon fixed so overpayment lowers the contractual payment, not the payoff date.
        var balance = principal
        var paidInterest = BigDecimal.ZERO
        var paidTotal = BigDecimal.ZERO
        var lastAnnuity = baseAnnuity
        val surplus = input.monthlyContribution.subtract(baseAnnuity, mc)

        for (m in 0 until n) {
            val remainingTerm = n - m
            if (balance.signum() == 0) {
                lastAnnuity = BigDecimal.ZERO
            } else {
                val recomputedAnnuity = annuity(balance, i, remainingTerm, mc)
                val interest = balance.multiply(i, mc)
                val principalPart = recomputedAnnuity.subtract(interest, mc)
                    .add(surplus, mc)
                    .min(balance)

                paidInterest = paidInterest.add(interest, mc)
                paidTotal = paidTotal.add(interest.add(principalPart, mc), mc)
                balance = balance.subtract(principalPart, mc)
                lastAnnuity = recomputedAnnuity
            }
        }
        val interestSaved = baselineTotalInterest.subtract(paidInterest, mc).max(BigDecimal.ZERO)

        return LoanProjection(
            principal = principal.setScale(2, RoundingMode.HALF_UP),
            baseMonthlyPayment = baseAnnuity.setScale(2, RoundingMode.HALF_UP),
            finalMonthlyPayment = lastAnnuity.setScale(2, RoundingMode.HALF_UP),
            totalInterest = paidInterest.setScale(2, RoundingMode.HALF_UP),
            totalPaid = paidTotal.setScale(2, RoundingMode.HALF_UP),
            interestSavedVsBaseline = interestSaved.setScale(2, RoundingMode.HALF_UP),
            monthsToPayoff = n,
            underfunded = false,
            overpaymentApplied = true,
        )
    }

    private fun annuity(
        balance: BigDecimal,
        monthlyRate: BigDecimal,
        termMonths: Int,
        mc: MathContext,
    ): BigDecimal {
        if (balance.signum() == 0) return BigDecimal.ZERO
        if (monthlyRate.signum() == 0) return balance.divide(BigDecimal(termMonths), mc)

        val onePlusRate = BigDecimal.ONE.add(monthlyRate, mc)
        val pow = onePlusRate.pow(termMonths, mc)
        return balance.multiply(monthlyRate, mc).multiply(pow, mc)
            .divide(pow.subtract(BigDecimal.ONE, mc), mc)
    }
}
