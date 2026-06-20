package com.kshavrin.mymoney.core.domain.usecase

import com.kshavrin.mymoney.core.domain.model.Currency
import com.kshavrin.mymoney.core.domain.model.Money
import com.kshavrin.mymoney.core.domain.model.toMoneyScale
import java.math.BigDecimal
import java.math.MathContext
import javax.inject.Inject

private const val BASE_CURRENCY_CODE = "EUR"

fun interface RatesLookup {
    fun rateFromEur(currency: Currency): BigDecimal?
}

sealed class ConversionResult {
    data class Converted(
        val money: Money,
        val crossRate: BigDecimal,
    ) : ConversionResult()

    data class RateMissing(
        val currencyCode: String,
    ) : ConversionResult()
}

class ConvertMoneyUseCase
    @Inject
    constructor() {
        operator fun invoke(
            amount: BigDecimal,
            from: Currency,
            to: Currency,
            rates: RatesLookup,
        ): ConversionResult {
            if (from.id == to.id) {
                return ConversionResult.Converted(
                    money = Money(amount.toMoneyScale(to), to),
                    crossRate = BigDecimal.ONE,
                )
            }

            val rateFrom =
                rateFromEur(from, rates)
                    ?: return ConversionResult.RateMissing(from.code)
            val rateTo =
                rateFromEur(to, rates)
                    ?: return ConversionResult.RateMissing(to.code)

            val crossRate = rateTo.divide(rateFrom, MathContext.DECIMAL64)
            val converted = amount.multiply(crossRate, MathContext.DECIMAL64).toMoneyScale(to)

            return ConversionResult.Converted(
                money = Money(converted, to),
                crossRate = crossRate,
            )
        }

        private fun rateFromEur(
            currency: Currency,
            rates: RatesLookup,
        ): BigDecimal? =
            if (currency.code == BASE_CURRENCY_CODE) {
                BigDecimal.ONE
            } else {
                rates.rateFromEur(currency)
            }
    }
