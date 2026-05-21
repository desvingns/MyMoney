package com.kshavrin.mymoney.feature.transaction

import com.kshavrin.mymoney.core.common.calculator.CalculatorOperator
import com.kshavrin.mymoney.core.designsystem.keypad.Operator

internal fun Operator.toCalculator(): CalculatorOperator = when (this) {
    Operator.Plus -> CalculatorOperator.Plus
    Operator.Minus -> CalculatorOperator.Minus
    Operator.Multiply -> CalculatorOperator.Multiply
    Operator.Divide -> CalculatorOperator.Divide
}

internal fun CalculatorOperator?.toDesignsystem(): Operator? = when (this) {
    null -> null
    CalculatorOperator.Plus -> Operator.Plus
    CalculatorOperator.Minus -> Operator.Minus
    CalculatorOperator.Multiply -> Operator.Multiply
    CalculatorOperator.Divide -> Operator.Divide
}
