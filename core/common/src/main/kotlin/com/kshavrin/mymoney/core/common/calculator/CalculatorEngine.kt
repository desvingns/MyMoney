package com.kshavrin.mymoney.core.common.calculator

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

class CalculatorEngine {

    private var currentOperand: String = "0"
    private var leftOperand: BigDecimal? = null
    private var op: CalculatorOperator? = null
    private var mode: Mode = Mode.TYPING_FIRST

    val pendingOp: CalculatorOperator?
        get() = op

    val display: String
        get() = currentOperand

    val currentValue: BigDecimal
        get() = currentOperand.toBigDecimalOrZero()

    val expression: String
        get() = buildExpression()

    fun inputDigit(d: Int) {
        require(d in 0..9) { "digit must be 0..9, was $d" }
        when (mode) {
            Mode.TYPING_FIRST -> appendDigitToCurrent(d)
            Mode.OPERATOR_SET -> {
                currentOperand = d.toString()
                mode = Mode.TYPING_SECOND
            }
            Mode.TYPING_SECOND -> appendDigitToCurrent(d)
            Mode.EVALUATED -> {
                resetSoft()
                currentOperand = d.toString()
                mode = Mode.TYPING_FIRST
            }
        }
    }

    fun inputDot() {
        if (mode == Mode.OPERATOR_SET) {
            currentOperand = "0."
            mode = Mode.TYPING_SECOND
            return
        }
        if (mode == Mode.EVALUATED) {
            resetSoft()
            currentOperand = "0."
            mode = Mode.TYPING_FIRST
            return
        }
        if (currentOperand.contains('.')) return
        if (digitCount(currentOperand) >= MAX_DIGITS) return
        currentOperand = "$currentOperand."
    }

    fun inputOperator(op: CalculatorOperator) {
        when (mode) {
            Mode.TYPING_FIRST -> {
                leftOperand = currentValue
                this.op = op
                mode = Mode.OPERATOR_SET
            }
            Mode.OPERATOR_SET -> {
                this.op = op
            }
            Mode.TYPING_SECOND -> {
                val result = applyPending()
                leftOperand = result
                currentOperand = result.toDisplayString()
                this.op = op
                mode = Mode.OPERATOR_SET
            }
            Mode.EVALUATED -> {
                leftOperand = currentValue
                this.op = op
                mode = Mode.OPERATOR_SET
            }
        }
    }

    fun backspace() {
        if (mode == Mode.OPERATOR_SET) {
            op = null
            currentOperand = leftOperand?.toDisplayString() ?: "0"
            leftOperand = null
            mode = Mode.TYPING_FIRST
            return
        }
        if (mode == Mode.EVALUATED) {
            return
        }
        if (currentOperand == "0") return
        if (currentOperand.length == 1) {
            currentOperand = "0"
            return
        }
        currentOperand = currentOperand.dropLast(1)
        if (currentOperand == "-") currentOperand = "0"
    }

    fun equals() {
        when (mode) {
            Mode.TYPING_FIRST -> {
                currentOperand = currentValue.toDisplayString()
                mode = Mode.EVALUATED
            }
            Mode.OPERATOR_SET -> {
                val result = applyPending()
                currentOperand = result.toDisplayString()
                leftOperand = null
                op = null
                mode = Mode.EVALUATED
            }
            Mode.TYPING_SECOND -> {
                val result = applyPending()
                currentOperand = result.toDisplayString()
                leftOperand = null
                op = null
                mode = Mode.EVALUATED
            }
            Mode.EVALUATED -> Unit
        }
    }

    fun clear() {
        currentOperand = "0"
        leftOperand = null
        op = null
        mode = Mode.TYPING_FIRST
    }

    private fun appendDigitToCurrent(d: Int) {
        if (digitCount(currentOperand) >= MAX_DIGITS) return
        currentOperand = if (currentOperand == "0") d.toString() else "$currentOperand$d"
    }

    private fun resetSoft() {
        leftOperand = null
        op = null
        currentOperand = "0"
    }

    private fun applyPending(): BigDecimal {
        val left = leftOperand ?: currentValue
        val right = currentValue
        val pending = op ?: return left
        val result = when (pending) {
            CalculatorOperator.Plus -> left.add(right, MATH_CONTEXT)
            CalculatorOperator.Minus -> left.subtract(right, MATH_CONTEXT)
            CalculatorOperator.Multiply -> left.multiply(right, MATH_CONTEXT)
            CalculatorOperator.Divide -> if (right.signum() == 0) {
                BigDecimal.ZERO
            } else {
                left.divide(right, MATH_CONTEXT)
            }
        }
        return result.normalize()
    }

    private fun buildExpression(): String {
        val left = leftOperand ?: return ""
        val pending = op ?: return ""
        val symbol = pending.symbol
        return when (mode) {
            Mode.TYPING_SECOND -> "${left.toDisplayString()} $symbol $currentOperand"
            else -> "${left.toDisplayString()} $symbol"
        }
    }

    private enum class Mode {
        TYPING_FIRST,
        OPERATOR_SET,
        TYPING_SECOND,
        EVALUATED,
    }

    companion object {
        private const val MAX_DIGITS = 16
        private val MATH_CONTEXT = MathContext.DECIMAL64

        private val CalculatorOperator.symbol: String
            get() = when (this) {
                CalculatorOperator.Plus -> "+"
                CalculatorOperator.Minus -> "−"
                CalculatorOperator.Multiply -> "×"
                CalculatorOperator.Divide -> "÷"
            }

        private fun digitCount(operand: String): Int =
            operand.count { it.isDigit() }

        private fun String.toBigDecimalOrZero(): BigDecimal = when {
            isEmpty() || this == "-" || this == "." || this == "-." -> BigDecimal.ZERO
            endsWith(".") -> dropLast(1).toBigDecimal()
            else -> toBigDecimal()
        }

        private fun BigDecimal.normalize(): BigDecimal {
            val stripped = stripTrailingZeros()
            return if (stripped.scale() < 0) stripped.setScale(0) else stripped
        }

        private fun BigDecimal.toDisplayString(): String {
            val normalized = normalize()
            return if (normalized.scale() <= 0) {
                normalized.toPlainString()
            } else {
                normalized.setScale(
                    minOf(normalized.scale(), MAX_DIGITS),
                    RoundingMode.HALF_UP,
                ).stripTrailingZeros().let { stripped ->
                    if (stripped.scale() <= 0) stripped.setScale(0).toPlainString()
                    else stripped.toPlainString()
                }
            }
        }
    }
}
