package com.kshavrin.mymoney.core.common.calculator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal

class CalculatorEngineTest {

    private lateinit var engine: CalculatorEngine

    @Before
    fun setUp() {
        engine = CalculatorEngine()
    }

    @Test
    fun `BR-7 second dot is rejected after digits and a dot already typed`() {
        engine.inputDigit(1)
        engine.inputDot()
        engine.inputDigit(5)
        engine.inputDot()

        assertEquals("1.5", engine.display)
    }

    @Test
    fun `BR-8 chain 5 plus 3 plus 2 equals produces currentValue 10 and display 10`() {
        engine.inputDigit(5)
        engine.inputOperator(CalculatorOperator.Plus)
        engine.inputDigit(3)
        engine.inputOperator(CalculatorOperator.Plus)
        engine.inputDigit(2)
        engine.equals()

        assertEquals(0, engine.currentValue.compareTo(BigDecimal("10")))
        assertEquals("10", engine.display)
    }

    @Test
    fun `BR-9 equals terminates expression so next digit starts a new operand discarding previous result`() {
        engine.inputDigit(5)
        engine.inputOperator(CalculatorOperator.Plus)
        engine.inputDigit(3)
        engine.equals()
        assertEquals("8", engine.display)

        engine.inputDigit(7)

        assertEquals("7", engine.display)
        assertNull(engine.pendingOp)
    }

    @Test
    fun `BR-10 backspace removes last digit so 1 2 3 backspace shows 12`() {
        engine.inputDigit(1)
        engine.inputDigit(2)
        engine.inputDigit(3)
        engine.backspace()

        assertEquals("12", engine.display)
    }

    @Test
    fun `BR-10 backspace on initial zero is a no-op`() {
        engine.backspace()

        assertEquals("0", engine.display)
    }

    @Test
    fun `S06 AC3 operator-replace 12 plus minus keeps display at 12 and pendingOp becomes Minus`() {
        engine.inputDigit(1)
        engine.inputDigit(2)
        engine.inputOperator(CalculatorOperator.Plus)
        engine.inputOperator(CalculatorOperator.Minus)

        assertEquals(CalculatorOperator.Minus, engine.pendingOp)
        assertEquals("12", engine.display)
    }

    @Test
    fun `mixed operators use left-to-right evaluation so 5 plus 3 times 2 yields 16 not 11`() {
        engine.inputDigit(5)
        engine.inputOperator(CalculatorOperator.Plus)
        engine.inputDigit(3)
        engine.inputOperator(CalculatorOperator.Multiply)
        engine.inputDigit(2)
        engine.equals()

        assertEquals("16", engine.display)
        assertEquals(0, engine.currentValue.compareTo(BigDecimal("16")))
    }

    @Test
    fun `digit cap allows 16 digits but rejects the 17th leaving display unchanged at cap`() {
        repeat(16) { engine.inputDigit(1) }
        val displayAt16 = engine.display
        assertEquals("1111111111111111", displayAt16)

        engine.inputDigit(2)

        assertEquals(displayAt16, engine.display)
    }

    @Test
    fun `multiplication 4 times 5 equals 20`() {
        engine.inputDigit(4)
        engine.inputOperator(CalculatorOperator.Multiply)
        engine.inputDigit(5)
        engine.equals()

        assertEquals("20", engine.display)
    }

    @Test
    fun `division 10 divide 4 equals 2 point 5`() {
        engine.inputDigit(1)
        engine.inputDigit(0)
        engine.inputOperator(CalculatorOperator.Divide)
        engine.inputDigit(4)
        engine.equals()

        assertEquals("2.5", engine.display)
    }

    @Test
    fun `division by zero is handled gracefully and yields display 0 without crashing`() {
        engine.inputDigit(1)
        engine.inputDigit(0)
        engine.inputOperator(CalculatorOperator.Divide)
        engine.inputDigit(0)
        engine.equals()

        assertEquals("0", engine.display)
    }

    @Test
    fun `dot first turns initial zero into 0 dot and subsequent digit 5 yields 0 dot 5`() {
        engine.inputDot()
        assertEquals("0.", engine.display)

        engine.inputDigit(5)

        assertEquals("0.5", engine.display)
    }

    @Test
    fun `equals without operator leaves display at the entered operand`() {
        engine.inputDigit(5)
        engine.equals()

        assertEquals("5", engine.display)
    }

    @Test
    fun `expression preview shows left operand and operator symbol after 5 plus`() {
        engine.inputDigit(5)
        engine.inputOperator(CalculatorOperator.Plus)

        assertEquals("5 +", engine.expression)
    }

    @Test
    fun `expression preview extends with right operand once second number typed`() {
        engine.inputDigit(5)
        engine.inputOperator(CalculatorOperator.Plus)
        engine.inputDigit(3)

        assertEquals("5 + 3", engine.expression)
    }

    @Test
    fun `expression preview is empty before any operator is set`() {
        engine.inputDigit(5)

        assertEquals("", engine.expression)
    }

    @Test
    fun `clear after a chain restores display to 0 and clears pendingOp`() {
        engine.inputDigit(7)
        engine.inputOperator(CalculatorOperator.Plus)
        engine.inputDigit(3)
        engine.equals()
        assertNotEquals("0", engine.display)

        engine.clear()

        assertEquals("0", engine.display)
        assertNull(engine.pendingOp)
    }

    @Test
    fun `clear from mid-expression with operator set restores display to 0 and clears pendingOp`() {
        engine.inputDigit(4)
        engine.inputOperator(CalculatorOperator.Multiply)
        engine.inputDigit(2)
        assertEquals(CalculatorOperator.Multiply, engine.pendingOp)

        engine.clear()

        assertEquals("0", engine.display)
        assertNull(engine.pendingOp)
        assertEquals("", engine.expression)
    }

    @Test
    fun `inputDigit rejects digits outside 0 to 9 range`() {
        var thrown = false
        try {
            engine.inputDigit(10)
        } catch (e: IllegalArgumentException) {
            thrown = true
        }
        assertTrue(thrown)
    }

    @Test
    fun `pendingOp is null before any operator is pressed`() {
        engine.inputDigit(4)

        assertNull(engine.pendingOp)
    }

    @Test
    fun `pendingOp reflects last pressed operator while building second operand`() {
        engine.inputDigit(4)
        engine.inputOperator(CalculatorOperator.Plus)

        assertEquals(CalculatorOperator.Plus, engine.pendingOp)

        engine.inputDigit(2)

        assertEquals(CalculatorOperator.Plus, engine.pendingOp)
    }

    @Test
    fun `pendingOp is cleared after equals`() {
        engine.inputDigit(4)
        engine.inputOperator(CalculatorOperator.Plus)
        engine.inputDigit(2)
        engine.equals()

        assertNull(engine.pendingOp)
    }
}
