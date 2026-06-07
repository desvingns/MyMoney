package com.kshavrin.mymoney.core.domain.usecase

import com.kshavrin.mymoney.core.domain.model.ContributionBreakdown
import com.kshavrin.mymoney.core.domain.model.ContributionItem
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

class ContributionCalculatorTest {

    private val calculator = ContributionCalculator()

    @Test
    fun `empty breakdown totals zero`() {
        val result = calculator(ContributionBreakdown())
        assertEquals(0, BigDecimal.ZERO.compareTo(result))
    }

    @Test
    fun `sums incomes minus expenses`() {
        val breakdown = ContributionBreakdown(
            enabled = true,
            incomes = listOf(
                ContributionItem("зарплата", BigDecimal("50000")),
                ContributionItem("", BigDecimal("10000")),
            ),
            expenses = listOf(
                ContributionItem("аренда", BigDecimal("20000")),
                ContributionItem("", BigDecimal("5000")),
            ),
        )
        val result = calculator(breakdown)
        assertEquals(0, BigDecimal("35000").compareTo(result))
    }

    @Test
    fun `result may be negative when expenses exceed incomes`() {
        val breakdown = ContributionBreakdown(
            enabled = true,
            incomes = listOf(ContributionItem("", BigDecimal("30000"))),
            expenses = listOf(ContributionItem("", BigDecimal("40000"))),
        )
        val result = calculator(breakdown)
        assertEquals(0, BigDecimal("-10000").compareTo(result))
    }

    @Test
    fun `zero amount row contributes nothing`() {
        val breakdown = ContributionBreakdown(
            enabled = true,
            incomes = listOf(
                ContributionItem("зарплата", BigDecimal("50000")),
                ContributionItem("", BigDecimal.ZERO),
            ),
            expenses = listOf(ContributionItem("", BigDecimal.ZERO)),
        )
        val result = calculator(breakdown)
        assertEquals(0, BigDecimal("50000").compareTo(result))
    }

    @Test
    fun `name value never affects the total`() {
        val named = ContributionBreakdown(
            incomes = listOf(ContributionItem("зарплата", BigDecimal("50000"))),
            expenses = listOf(ContributionItem("аренда", BigDecimal("20000"))),
        )
        val blank = ContributionBreakdown(
            incomes = listOf(ContributionItem("", BigDecimal("50000"))),
            expenses = listOf(ContributionItem("", BigDecimal("20000"))),
        )
        assertEquals(0, calculator(named).compareTo(calculator(blank)))
    }
}
