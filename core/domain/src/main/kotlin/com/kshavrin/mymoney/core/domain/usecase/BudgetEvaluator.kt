package com.kshavrin.mymoney.core.domain.usecase

import com.kshavrin.mymoney.core.domain.model.BalanceSnapshot
import com.kshavrin.mymoney.core.domain.model.Budget
import com.kshavrin.mymoney.core.domain.model.Money
import java.math.BigDecimal
import javax.inject.Inject

class BudgetEvaluator @Inject constructor() {

    fun evaluate(snapshot: BalanceSnapshot, budgets: List<Budget>): List<BudgetStatus> = budgets.mapNotNull { budget ->
        val spent: BigDecimal = if (budget.categoryId == null) {
            snapshot.expense.amount
        } else {
            snapshot.byCategory
                .firstOrNull { it.categoryId == budget.categoryId }
                ?.total
                ?.amount
                ?: BigDecimal.ZERO
        }
        val limit = budget.amount
        if (limit.signum() == 0) return@mapNotNull null
        val pct = spent.toFloat() / limit.toFloat()
        val state = when {
            pct >= 1f -> BudgetState.Over
            pct >= budget.alertThresholdPct / 100f -> BudgetState.ThresholdHit
            else -> BudgetState.Under
        }
        BudgetStatus(
            budgetId = budget.id,
            categoryId = budget.categoryId,
            spent = Money(spent, snapshot.expense.currency),
            limit = Money(limit, snapshot.expense.currency),
            fraction = pct,
            state = state,
        )
    }
}

data class BudgetStatus(
    val budgetId: Long,
    val categoryId: Long?,
    val spent: Money,
    val limit: Money,
    val fraction: Float,
    val state: BudgetState,
)

enum class BudgetState { Under, ThresholdHit, Over }
