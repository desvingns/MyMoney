package com.kshavrin.mymoney.core.common.money

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.util.Locale

class MoneyFormatterTest {
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
                currencySymbol = "₽",
                decimalDigits = 2,
                locale = ruLocale,
                symbolPosition = MoneyFormatter.SymbolPosition.AFTER,
            )
        // RU locale uses non-breaking space for grouping; verify suffix + prefix instead of full string.
        assertTrue(result.endsWith(",56 ₽"))
        assertTrue(result.startsWith("1"))
    }

    @Test
    fun formats_zero_decimal_currency() {
        val result =
            MoneyFormatter.format(
                amount = BigDecimal("1234"),
                currencySymbol = "¥",
                decimalDigits = 0,
                locale = Locale.JAPAN,
                symbolPosition = MoneyFormatter.SymbolPosition.BEFORE,
            )
        assertEquals("¥1,234", result)
    }
}
