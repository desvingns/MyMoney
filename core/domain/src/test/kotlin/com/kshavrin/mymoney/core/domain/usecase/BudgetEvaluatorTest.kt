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
