package com.kshavrin.mymoney.core.domain.usecase

import com.kshavrin.mymoney.core.domain.model.Money

data class RingGauge(
    val fraction: Float,
    val isExpense: Boolean,
)

object RingGaugeCalculator {
    operator fun invoke(
        income: Money,
        expense: Money,
        previousExpense: Money?,
    ): RingGauge {
        val incomeAmount = income.amount
        val expenseAmount = expense.amount

        return when {
            incomeAmount.signum() > 0 && expenseAmount.compareTo(incomeAmount) <= 0 -> {
                val net = incomeAmount.subtract(expenseAmount)
                RingGauge(
                    fraction = (net.toFloat() / incomeAmount.toFloat()).coerceIn(0f, 1f),
                    isExpense = false,
                )
            }
            incomeAmount.signum() > 0 -> {
                val overspend = expenseAmount.subtract(incomeAmount)
                RingGauge(
                    fraction = (overspend.toFloat() / incomeAmount.toFloat()).coerceIn(0f, 1f),
                    isExpense = true,
                )
            }
            expenseAmount.signum() > 0 -> {
                val previous = previousExpense?.amount
                val fraction =
                    if (previous == null || previous.signum() <= 0) {
                        1f
                    } else {
                        (expenseAmount.toFloat() / previous.toFloat()).coerceIn(0f, 1f)
                    }
                RingGauge(fraction = fraction, isExpense = true)
            }
            else -> RingGauge(fraction = 0f, isExpense = false)
        }
    }
}
