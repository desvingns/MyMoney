package com.kshavrin.mymoney.core.designsystem.keypad

import com.kshavrin.mymoney.core.common.calculator.CalculatorOperator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OperatorMapperTest {
    @Test
    fun `Operator Plus maps to CalculatorOperator Plus`() {
        assertEquals(CalculatorOperator.Plus, Operator.Plus.toCalculator())
    }

    @Test
    fun `Operator Minus maps to CalculatorOperator Minus`() {
        assertEquals(CalculatorOperator.Minus, Operator.Minus.toCalculator())
    }

    @Test
    fun `Operator Multiply maps to CalculatorOperator Multiply`() {
        assertEquals(CalculatorOperator.Multiply, Operator.Multiply.toCalculator())
    }

    @Test
    fun `Operator Divide maps to CalculatorOperator Divide`() {
        assertEquals(CalculatorOperator.Divide, Operator.Divide.toCalculator())
    }

    @Test
    fun `CalculatorOperator Plus maps back to Operator Plus`() {
        val source: CalculatorOperator? = CalculatorOperator.Plus
        assertEquals(Operator.Plus, source.toDesignsystem())
    }

    @Test
    fun `CalculatorOperator Minus maps back to Operator Minus`() {
        val source: CalculatorOperator? = CalculatorOperator.Minus
        assertEquals(Operator.Minus, source.toDesignsystem())
    }

    @Test
    fun `CalculatorOperator Multiply maps back to Operator Multiply`() {
        val source: CalculatorOperator? = CalculatorOperator.Multiply
        assertEquals(Operator.Multiply, source.toDesignsystem())
    }

    @Test
    fun `CalculatorOperator Divide maps back to Operator Divide`() {
        val source: CalculatorOperator? = CalculatorOperator.Divide
        assertEquals(Operator.Divide, source.toDesignsystem())
    }

    @Test
    fun `null CalculatorOperator maps back to null Operator`() {
        val source: CalculatorOperator? = null
        assertNull(source.toDesignsystem())
    }
}
