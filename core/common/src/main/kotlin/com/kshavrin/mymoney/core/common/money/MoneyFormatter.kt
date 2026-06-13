package com.kshavrin.mymoney.core.common.money

import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

object MoneyFormatter {
    fun format(
        amount: BigDecimal,
        currencySymbol: String,
        decimalDigits: Int,
        locale: Locale,
        symbolPosition: SymbolPosition = SymbolPosition.BEFORE,
    ): String {
        val symbols = DecimalFormatSymbols.getInstance(locale)
        val pattern = if (decimalDigits > 0) "#,##0.${"0".repeat(decimalDigits)}" else "#,##0"
        val formatter =
            DecimalFormat(pattern, symbols).apply {
                isGroupingUsed = true
                maximumFractionDigits = decimalDigits
                minimumFractionDigits = decimalDigits
            }
        val number = formatter.format(amount)
        return when (symbolPosition) {
            SymbolPosition.BEFORE -> "$currencySymbol$number"
            SymbolPosition.AFTER -> "$number $currencySymbol"
        }
    }

    enum class SymbolPosition { BEFORE, AFTER }
}
