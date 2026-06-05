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
                totalInterest = zero,
                totalPaid = zero,
                monthsToPayoff = 0,
                underfunded = false,
                overpaymentApplied = false,
            )
        }

        val i = input.annualRatePercent
            .divide(BigDecimal(100), mc)
            .divide(BigDecimal(12), mc)
        val nBig = BigDecimal(n)

        val baseAnnuity = if (i.signum() == 0) {
            principal.divide(nBig, mc)
        } else {
            val onePlusI = BigDecimal.ONE.add(i, mc)
            val pow = onePlusI.pow(n, mc)
            principal.multiply(i, mc).multiply(pow, mc)
                .divide(pow.subtract(BigDecimal.ONE, mc), mc)
        }

        if (input.monthlyContribution < baseAnnuity) {
            val totalPaid = baseAnnuity.multiply(nBig, mc)
            val totalInterest = totalPaid.subtract(principal, mc)
            return LoanProjection(
                principal = principal.setScale(2, RoundingMode.HALF_UP),
                baseMonthlyPayment = baseAnnuity.setScale(2, RoundingMode.HALF_UP),
                totalInterest = totalInterest.setScale(2, RoundingMode.HALF_UP),
                totalPaid = totalPaid.setScale(2, RoundingMode.HALF_UP),
                monthsToPayoff = n,
                underfunded = true,
                overpaymentApplied = false,
            )
        }

        if (input.monthlyContribution.compareTo(baseAnnuity) == 0) {
            val totalPaid = baseAnnuity.multiply(nBig, mc)
            val totalInterest = totalPaid.subtract(principal, mc)
            return LoanProjection(
                principal = principal.setScale(2, RoundingMode.HALF_UP),
                baseMonthlyPayment = baseAnnuity.setScale(2, RoundingMode.HALF_UP),
                totalInterest = totalInterest.setScale(2, RoundingMode.HALF_UP),
                totalPaid = totalPaid.setScale(2, RoundingMode.HALF_UP),
                monthsToPayoff = n,
                underfunded = false,
                overpaymentApplied = false,
            )
        }

        // Reduce-payment overpayment: term fixed at n, each month the constant surplus is
        // applied as extra principal and the contractual annuity is recomputed on the reduced
        // balance for the remaining term, so the schedule still terminates by month n.
        var balance = principal
        var paidInterest = BigDecimal.ZERO
        var paidTotal = BigDecimal.ZERO
        var monthsUsed = 0

        for (m in 0 until n) {
            val remainingTerm = n - m
            val annuity = if (i.signum() == 0) {
                balance.divide(BigDecimal(remainingTerm), mc)
            } else {
                val onePlusI = BigDecimal.ONE.add(i, mc)
                val pow = onePlusI.pow(remainingTerm, mc)
                balance.multiply(i, mc).multiply(pow, mc)
                    .divide(pow.subtract(BigDecimal.ONE, mc), mc)
            }
            val interest = balance.multiply(i, mc)
            val surplus = input.monthlyContribution.subtract(annuity, mc)
            val plannedPayment = annuity.add(surplus, mc)
            val principalPart = plannedPayment.subtract(interest, mc)

            monthsUsed = m + 1
            if (principalPart >= balance) {
                val finalPayment = balance.add(interest, mc)
                paidInterest = paidInterest.add(interest, mc)
                paidTotal = paidTotal.add(finalPayment, mc)
                balance = BigDecimal.ZERO
                break
            } else {
                paidInterest = paidInterest.add(interest, mc)
                paidTotal = paidTotal.add(plannedPayment, mc)
                balance = balance.subtract(principalPart, mc)
            }
        }

        return LoanProjection(
            principal = principal.setScale(2, RoundingMode.HALF_UP),
            baseMonthlyPayment = baseAnnuity.setScale(2, RoundingMode.HALF_UP),
            totalInterest = paidInterest.setScale(2, RoundingMode.HALF_UP),
            totalPaid = paidTotal.setScale(2, RoundingMode.HALF_UP),
            monthsToPayoff = monthsUsed,
            underfunded = false,
            overpaymentApplied = true,
        )
    }
}
