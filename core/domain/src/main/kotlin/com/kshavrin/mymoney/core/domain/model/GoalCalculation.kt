package com.kshavrin.mymoney.core.domain.model

import java.math.BigDecimal
import java.time.LocalDate

data class SavingsGoalInput(
    val targetAmount: BigDecimal,
    val startingCapital: BigDecimal,
    val monthlyContribution: BigDecimal,
)

data class SavingsProjection(
    val monthsToGoal: Int?,
    val achievementDate: LocalDate?,
    val status: GoalStatus,
)

enum class GoalStatus { ON_TRACK, ALREADY_ACHIEVED, UNREACHABLE }

data class LoanGoalInput(
    val targetAmount: BigDecimal,
    val startingCapital: BigDecimal,
    val annualRatePercent: BigDecimal,
    val termMonths: Int,
    val monthlyContribution: BigDecimal,
)

data class LoanProjection(
    val principal: BigDecimal,
    val baseMonthlyPayment: BigDecimal,
    val totalInterest: BigDecimal,
    val totalPaid: BigDecimal,
    val monthsToPayoff: Int,
    val underfunded: Boolean,
    val overpaymentApplied: Boolean,
)
