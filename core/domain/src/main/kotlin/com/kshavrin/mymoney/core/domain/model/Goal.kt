package com.kshavrin.mymoney.core.domain.model

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

data class Goal(
    val id: Long,
    val name: String,
    val iconKey: String,
    val colorHex: String,
    val accountId: Long,
    val variant: GoalVariant,
    val targetAmount: BigDecimal,
    val startingCapital: BigDecimal,
    val monthlyContribution: BigDecimal,
    val annualRatePercent: BigDecimal?,
    val termDate: LocalDate?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val isArchived: Boolean,
    val contributionBreakdown: ContributionBreakdown = ContributionBreakdown(),
)

enum class GoalVariant { SAVINGS, CREDIT }
