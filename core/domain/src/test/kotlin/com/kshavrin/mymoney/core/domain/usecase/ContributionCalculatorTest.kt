package com.kshavrin.mymoney.core.domain.usecase

import com.kshavrin.mymoney.core.domain.model.ContributionBreakdown
import com.kshavrin.mymoney.core.domain.model.ContributionItem
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

class ContributionCalculatorTest {

    private val calculator = ContributionCalculator()

    // Fixture 1 — SPEC table row 1: empty lists → 0
    @Test
    fun `empty breakdown totals zero`() {
        val result = calculator(ContributionBreakdown())
        assertEquals(0, BigDecimal.ZERO.compareTo(result))
    }

    // Fixture 2 — SPEC table row 2: (50000 + 10000) − (20000 + 5000) = 35000.
    // The SPEC table printed "40000" which is a typo; hand check: 60000 − 25000 = 35000.
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

    // Fixture 3 — SPEC table row 3: 30000 − 40000 = −10000
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

    // SPEC constraint: a row with amount = ZERO contributes 0
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

    // SPEC constraint: row name never affects the total — blank vs filled names produce the same sum
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
