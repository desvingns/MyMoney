package com.kshavrin.mymoney.core.domain.usecase

import com.kshavrin.mymoney.core.domain.model.GoalStatus
import com.kshavrin.mymoney.core.domain.model.SavingsGoalInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class GoalSavingsProjectorTest {

    private val projector = GoalSavingsProjector()
    private val today = LocalDate.of(2026, 6, 5)

    @Test
    fun on_track_with_ceiling_rounding() {
        val input = SavingsGoalInput(
            targetAmount = BigDecimal("1000"),
            startingCapital = BigDecimal("100"),
            monthlyContribution = BigDecimal("300"),
        )

        val result = projector(input, today)

        assertEquals(GoalStatus.ON_TRACK, result.status)
        assertEquals(3, result.monthsToGoal)
        assertEquals(today.plusMonths(3L), result.achievementDate)
    }

    @Test
    fun on_track_partial_last_month_rounds_up() {
        val input = SavingsGoalInput(
            targetAmount = BigDecimal("1000"),
            startingCapital = BigDecimal("0"),
            monthlyContribution = BigDecimal("300"),
        )

        val result = projector(input, today)

        assertEquals(GoalStatus.ON_TRACK, result.status)
        assertEquals(4, result.monthsToGoal)
        assertEquals(today.plusMonths(4L), result.achievementDate)
    }

    @Test
    fun exact_division_boundary_does_not_overshoot() {
        val input = SavingsGoalInput(
            targetAmount = BigDecimal("1200"),
            startingCapital = BigDecimal("0"),
            monthlyContribution = BigDecimal("100"),
        )

        val result = projector(input, today)

        assertEquals(GoalStatus.ON_TRACK, result.status)
        assertEquals(12, result.monthsToGoal)
        assertEquals(today.plusMonths(12L), result.achievementDate)
    }

    @Test
    fun already_achieved_when_capital_equals_target() {
        val input = SavingsGoalInput(
            targetAmount = BigDecimal("500"),
            startingCapital = BigDecimal("500"),
            monthlyContribution = BigDecimal("100"),
        )

        val result = projector(input, today)

        assertEquals(GoalStatus.ALREADY_ACHIEVED, result.status)
        assertEquals(0, result.monthsToGoal)
        assertEquals(today, result.achievementDate)
    }

    @Test
    fun already_achieved_when_capital_exceeds_target() {
        val input = SavingsGoalInput(
            targetAmount = BigDecimal("500"),
            startingCapital = BigDecimal("700"),
            monthlyContribution = BigDecimal("0"),
        )

        val result = projector(input, today)

        assertEquals(GoalStatus.ALREADY_ACHIEVED, result.status)
        assertEquals(0, result.monthsToGoal)
        assertEquals(today, result.achievementDate)
    }

    @Test
    fun unreachable_when_monthly_contribution_zero() {
        val input = SavingsGoalInput(
            targetAmount = BigDecimal("1000"),
            startingCapital = BigDecimal("100"),
            monthlyContribution = BigDecimal.ZERO,
        )

        val result = projector(input, today)

        assertEquals(GoalStatus.UNREACHABLE, result.status)
        assertNull(result.monthsToGoal)
        assertNull(result.achievementDate)
    }

    @Test
    fun unreachable_when_monthly_contribution_negative() {
        val input = SavingsGoalInput(
            targetAmount = BigDecimal("1000"),
            startingCapital = BigDecimal("100"),
            monthlyContribution = BigDecimal("-50"),
        )

        val result = projector(input, today)

        assertEquals(GoalStatus.UNREACHABLE, result.status)
        assertNull(result.monthsToGoal)
        assertNull(result.achievementDate)
    }
}
