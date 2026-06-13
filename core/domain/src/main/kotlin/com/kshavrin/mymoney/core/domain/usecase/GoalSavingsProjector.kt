package com.kshavrin.mymoney.core.domain.usecase

import com.kshavrin.mymoney.core.domain.model.GoalStatus
import com.kshavrin.mymoney.core.domain.model.SavingsGoalInput
import com.kshavrin.mymoney.core.domain.model.SavingsProjection
import java.math.RoundingMode
import java.time.LocalDate
import javax.inject.Inject

class GoalSavingsProjector
    @Inject
    constructor() {
        operator fun invoke(
            input: SavingsGoalInput,
            today: LocalDate,
        ): SavingsProjection {
            val remaining = input.targetAmount.subtract(input.startingCapital)
            if (remaining.signum() <= 0) {
                return SavingsProjection(
                    monthsToGoal = 0,
                    achievementDate = today,
                    status = GoalStatus.ALREADY_ACHIEVED,
                )
            }
            if (input.monthlyContribution.signum() <= 0) {
                return SavingsProjection(
                    monthsToGoal = null,
                    achievementDate = null,
                    status = GoalStatus.UNREACHABLE,
                )
            }
            val months = remaining.divide(input.monthlyContribution, 0, RoundingMode.CEILING).toInt()
            return SavingsProjection(
                monthsToGoal = months,
                achievementDate = today.plusMonths(months.toLong()),
                status = GoalStatus.ON_TRACK,
            )
        }
    }
