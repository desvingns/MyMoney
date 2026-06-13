package com.kshavrin.mymoney.core.domain.usecase

import com.kshavrin.mymoney.core.domain.model.GoalStatus
import com.kshavrin.mymoney.core.domain.model.LoanGoalInput
import com.kshavrin.mymoney.core.domain.model.LoanProjection
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import javax.inject.Inject

class GoalLoanCalculator
    @Inject
    constructor() {
        operator fun invoke(input: LoanGoalInput): LoanProjection {
            require(input.termMonths >= 1) { "termMonths must be >= 1" }

            val mc = MathContext.DECIMAL64
            val n = input.termMonths

            val needed = input.downPayment.subtract(input.startingCapital, mc).max(BigDecimal.ZERO)
            val accumulationMonths: Int? =
                when {
                    needed.signum() == 0 -> 0
                    input.monthlyContribution.signum() <= 0 -> null
                    else -> needed.divide(input.monthlyContribution, 0, RoundingMode.CEILING).toInt()
                }

            val equity = input.downPayment.max(input.startingCapital)
            val principal = input.targetAmount.subtract(equity, mc).max(BigDecimal.ZERO)

            val annuity = annuity(principal, input.annualRatePercent, n, mc)
            val monthlyPayment = annuity.setScale(2, RoundingMode.HALF_UP)
            val totalPaid = monthlyPayment.multiply(BigDecimal(n))
            val totalInterest = totalPaid.subtract(principal).max(BigDecimal.ZERO)

            val totalMonthsToPayoff = accumulationMonths?.let { it + n }
            val status = if (accumulationMonths == null) GoalStatus.UNREACHABLE else GoalStatus.ON_TRACK
            val underfunded = annuity > input.monthlyContribution

            return LoanProjection(
                principal = principal.setScale(2, RoundingMode.HALF_UP),
                baseMonthlyPayment = monthlyPayment,
                totalInterest = totalInterest.setScale(2, RoundingMode.HALF_UP),
                totalPaid = totalPaid.setScale(2, RoundingMode.HALF_UP),
                accumulationMonths = accumulationMonths,
                totalMonthsToPayoff = totalMonthsToPayoff,
                underfunded = underfunded,
                status = status,
            )
        }

        private fun annuity(
            principal: BigDecimal,
            annualRatePercent: BigDecimal,
            termMonths: Int,
            mc: MathContext,
        ): BigDecimal {
            if (principal.signum() == 0) return BigDecimal.ZERO
            val i =
                annualRatePercent
                    .divide(BigDecimal(100), mc)
                    .divide(BigDecimal(12), mc)
            if (i.signum() == 0) return principal.divide(BigDecimal(termMonths), mc)

            val onePlusRate = BigDecimal.ONE.add(i, mc)
            val pow = onePlusRate.pow(termMonths, mc)
            return principal
                .multiply(i, mc)
                .multiply(pow, mc)
                .divide(pow.subtract(BigDecimal.ONE, mc), mc)
        }
    }
