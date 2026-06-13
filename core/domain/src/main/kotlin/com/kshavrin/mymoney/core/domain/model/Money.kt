package com.kshavrin.mymoney.core.domain.model

import java.math.BigDecimal
import java.math.RoundingMode

data class Money(
    val amount: BigDecimal,
    val currency: Currency,
) {
    operator fun plus(other: Money): Money {
        require(currency.id == other.currency.id) { "Cannot add Money with different currencies (${currency.code} + ${other.currency.code})" }
        return copy(amount = amount.add(other.amount).setScale(currency.decimalDigits, RoundingMode.HALF_UP))
    }

    operator fun minus(other: Money): Money {
        require(currency.id == other.currency.id) { "Cannot subtract Money with different currencies (${currency.code} - ${other.currency.code})" }
        return copy(amount = amount.subtract(other.amount).setScale(currency.decimalDigits, RoundingMode.HALF_UP))
    }

    operator fun times(factor: BigDecimal): Money =
        copy(amount = amount.multiply(factor).setScale(currency.decimalDigits, RoundingMode.HALF_UP))

    fun isPositive(): Boolean = amount.signum() > 0

    fun isNegative(): Boolean = amount.signum() < 0

    fun isZero(): Boolean = amount.signum() == 0

    companion object {
        fun zero(currency: Currency): Money = Money(BigDecimal.ZERO.setScale(currency.decimalDigits), currency)
    }
}
