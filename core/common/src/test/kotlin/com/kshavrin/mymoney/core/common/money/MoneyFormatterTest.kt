package com.kshavrin.mymoney.core.common.money

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.util.Locale

class MoneyFormatterTest {
    @Test
    fun `formats input without grouping using the requested Russian decimal separator`() {
        val result =
            MoneyFormatter.formatInput(
                amount = BigDecimal("1234.56"),
                locale = Locale.forLanguageTag("ru-RU"),
            )

        assertEquals("1234,56", result)
    }

    @Test
    fun formats_en_us_with_symbol_before() {
        val result =
            MoneyFormatter.format(
                amount = BigDecimal("1234.56"),
                currencySymbol = "$",
                decimalDigits = 2,
                locale = Locale.US,
                symbolPosition = MoneyFormatter.SymbolPosition.BEFORE,
            )
        assertEquals("\$1,234.56", result)
    }

    @Test
    fun formats_ru_with_symbol_after() {
        val ruLocale = Locale.forLanguageTag("ru-RU")
        val result =
            MoneyFormatter.format(
                amount = BigDecimal("1234.56"),
                currencySymbol = "\u20BD",
                decimalDigits = 2,
                locale = ruLocale,
                symbolPosition = MoneyFormatter.SymbolPosition.AFTER,
            )
        assertTrue(result.endsWith(",56 \u20BD"))
        assertTrue(result.startsWith("1"))
    }

    @Test
    fun formats_zero_decimal_currency() {
        val result =
            MoneyFormatter.format(
                amount = BigDecimal("1234"),
                currencySymbol = "\u00A5",
                decimalDigits = 0,
                locale = Locale.JAPAN,
                symbolPosition = MoneyFormatter.SymbolPosition.BEFORE,
            )
        assertEquals("\u00A51,234", result)
    }

    @Test
    fun rounds_half_up_on_boundary_values() {
        val result =
            MoneyFormatter.format(
                amount = BigDecimal("0.125"),
                currencySymbol = "",
                decimalDigits = 2,
                locale = Locale.US,
                symbolPosition = MoneyFormatter.SymbolPosition.BEFORE,
            )

        assertEquals("0.13", result)
    }

    @Test
    fun `rounds half up 0 point 135 to 0 point 14 at 2 fraction digits`() {
        val result =
            MoneyFormatter.format(
                amount = BigDecimal("0.135"),
                currencySymbol = "",
                decimalDigits = 2,
                locale = Locale.US,
                symbolPosition = MoneyFormatter.SymbolPosition.BEFORE,
            )

        assertEquals("0.14", result)
    }
}
