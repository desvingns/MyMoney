package com.kshavrin.mymoney.core.domain.usecase

import com.kshavrin.mymoney.core.domain.model.BalanceSnapshot
import com.kshavrin.mymoney.core.domain.model.Budget
import com.kshavrin.mymoney.core.domain.model.CategoryBalance
import com.kshavrin.mymoney.core.domain.model.Currency
import com.kshavrin.mymoney.core.domain.model.Money
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

class BudgetEvaluatorTest {
    private val usd = Currency(1L, "USD", "$", "US Dollar", 2, true, 0)

    @Test
    fun under_threshold_returns_under_state() {
        val snapshot = snapshot(expense = "30.00", byCategory = mapOf(100L to "30.00"))
        val budgets =
            listOf(
                Budget(1L, 100L, "month", Instant.EPOCH, BigDecimal("100.00"), 1L, alertThresholdPct = 80, isActive = true),
            )
        val result = BudgetEvaluator().evaluate(snapshot, budgets)
        assertEquals(BudgetState.Under, result[0].state)
    }

    @Test
    fun threshold_hit_returns_threshold_state() {
        val snapshot = snapshot(expense = "85.00", byCategory = mapOf(100L to "85.00"))
        val budgets =
            listOf(
                Budget(1L, 100L, "month", Instant.EPOCH, BigDecimal("100.00"), 1L, alertThresholdPct = 80, isActive = true),
            )
        val result = BudgetEvaluator().evaluate(snapshot, budgets)
        assertEquals(BudgetState.ThresholdHit, result[0].state)
    }

    @Test
    fun over_budget_returns_over_state() {
        val snapshot = snapshot(expense = "150.00", byCategory = mapOf(100L to "150.00"))
        val budgets =
            listOf(
                Budget(1L, 100L, "month", Instant.EPOCH, BigDecimal("100.00"), 1L, alertThresholdPct = 80, isActive = true),
            )
        val result = BudgetEvaluator().evaluate(snapshot, budgets)
        assertEquals(BudgetState.Over, result[0].state)
    }

    @Test
    fun total_budget_uses_total_expense() {
        val snapshot = snapshot(expense = "70.00", byCategory = mapOf(100L to "30.00", 101L to "40.00"))
        val budgets =
            listOf(
                Budget(1L, null, "month", Instant.EPOCH, BigDecimal("100.00"), 1L, alertThresholdPct = 80, isActive = true),
            )
        val result = BudgetEvaluator().evaluate(snapshot, budgets)
        assertEquals(BudgetState.Under, result[0].state)
    }

    @Test
    fun large_budget_just_below_limit_is_threshold_not_over() {
        val snapshot = snapshot(expense = "9999999.50", byCategory = mapOf(100L to "9999999.50"))
        val budgets =
            listOf(
                Budget(1L, 100L, "month", Instant.EPOCH, BigDecimal("10000000.00"), 1L, alertThresholdPct = 80, isActive = true),
            )
        val result = BudgetEvaluator().evaluate(snapshot, budgets)
        assertEquals(BudgetState.ThresholdHit, result[0].state)
    }

    @Test
    fun large_budget_at_limit_is_over() {
        val snapshot = snapshot(expense = "10000000.00", byCategory = mapOf(100L to "10000000.00"))
        val budgets =
            listOf(
                Budget(1L, 100L, "month", Instant.EPOCH, BigDecimal("10000000.00"), 1L, alertThresholdPct = 80, isActive = true),
            )
        val result = BudgetEvaluator().evaluate(snapshot, budgets)
        assertEquals(BudgetState.Over, result[0].state)
    }

    @Test
    fun large_budget_just_below_threshold_is_under() {
        val snapshot = snapshot(expense = "7999999.99", byCategory = mapOf(100L to "7999999.99"))
        val budgets =
            listOf(
                Budget(1L, 100L, "month", Instant.EPOCH, BigDecimal("10000000.00"), 1L, alertThresholdPct = 80, isActive = true),
            )
        val result = BudgetEvaluator().evaluate(snapshot, budgets)
        assertEquals(BudgetState.Under, result[0].state)
    }

    @Test
    fun large_budget_at_threshold_is_threshold() {
        val snapshot = snapshot(expense = "8000000.00", byCategory = mapOf(100L to "8000000.00"))
        val budgets =
            listOf(
                Budget(1L, 100L, "month", Instant.EPOCH, BigDecimal("10000000.00"), 1L, alertThresholdPct = 80, isActive = true),
            )
        val result = BudgetEvaluator().evaluate(snapshot, budgets)
        assertEquals(BudgetState.ThresholdHit, result[0].state)
    }

    @Test
    fun zero_spent_is_under() {
        val snapshot = snapshot(expense = "0.00", byCategory = mapOf(100L to "0.00"))
        val budgets =
            listOf(
                Budget(1L, 100L, "month", Instant.EPOCH, BigDecimal("10000000.00"), 1L, alertThresholdPct = 80, isActive = true),
            )
        val result = BudgetEvaluator().evaluate(snapshot, budgets)
        assertEquals(BudgetState.Under, result[0].state)
    }

    @Test
    fun non_positive_limit_is_not_evaluated() {
        val snapshot = snapshot(expense = "50.00", byCategory = mapOf(100L to "50.00"))
        val budgets =
            listOf(
                Budget(1L, 100L, "month", Instant.EPOCH, BigDecimal.ZERO, 1L, alertThresholdPct = 80, isActive = true),
                Budget(2L, 100L, "month", Instant.EPOCH, BigDecimal("-10.00"), 1L, alertThresholdPct = 80, isActive = true),
            )
        val result = BudgetEvaluator().evaluate(snapshot, budgets)
        assertEquals(emptyList<BudgetStatus>(), result)
    }

    private fun snapshot(
        expense: String,
        byCategory: Map<Long, String>,
    ) =
        BalanceSnapshot(
            income = Money(BigDecimal.ZERO.setScale(2), usd),
            expense = Money(BigDecimal(expense), usd),
            net = Money(BigDecimal(expense).negate(), usd),
            byCategory =
                byCategory.map { (id, amount) ->
                    CategoryBalance(id, "cat$id", "#000000", Money(BigDecimal(amount), usd), 0f)
                },
        )
}
