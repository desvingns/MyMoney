package com.kshavrin.mymoney.core.domain.csv

import com.kshavrin.mymoney.core.domain.model.TransactionKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.math.BigDecimal
import java.time.LocalDate

class MonefyCsvImportParserTest {
    private val monefyHeader =
        "date,account,category,amount,currency,converted amount,currency,description"
    private val myMoneyHeader =
        "id,kind,amount,currency,account,category,note,occurredAt,createdAt"

    // U+00A0 is the non-breaking space Monefy uses as the thousands grouping separator.
    private val nbsp = ' '

    @Test
    fun `detects Monefy header`() {
        val result = MonefyCsvImportParser.parseText("$monefyHeader\r\n")
        assertEquals(CsvImportFormat.Monefy, result.format)
    }

    @Test
    fun `detects MyMoney header and parses no Monefy rows`() {
        val result = MonefyCsvImportParser.parseText("$myMoneyHeader\r\n")
        assertEquals(CsvImportFormat.MyMoney, result.format)
        assertTrue(result.rows.isEmpty())
    }

    @Test
    fun `detects 11-column MyMoney transfer header as MyMoney format`() {
        val transferHeader = "$myMoneyHeader,to_account,to_amount"
        val result = MonefyCsvImportParser.detectFormat(transferHeader.split(","))
        assertEquals(CsvImportFormat.MyMoney, result)
    }

    @Test
    fun `detectFormat maps 9-col MYMONEY_HEADER to MyMoney`() {
        val result = MonefyCsvImportParser.detectFormat(MonefyCsvImportParser.MYMONEY_HEADER)
        assertEquals(CsvImportFormat.MyMoney, result)
    }

    @Test
    fun `detectFormat maps 11-col MYMONEY_TRANSFER_HEADER to MyMoney`() {
        val result = MonefyCsvImportParser.detectFormat(MonefyCsvImportParser.MYMONEY_TRANSFER_HEADER)
        assertEquals(CsvImportFormat.MyMoney, result)
    }

    @Test
    fun `detectFormat maps MONEFY_HEADER to Monefy`() {
        val result = MonefyCsvImportParser.detectFormat(MonefyCsvImportParser.MONEFY_HEADER)
        assertEquals(CsvImportFormat.Monefy, result)
    }

    @Test
    fun `detectFormat distinguishes Monefy header from MyMoney headers`() {
        assertNotEquals(
            MonefyCsvImportParser.detectFormat(MonefyCsvImportParser.MONEFY_HEADER),
            MonefyCsvImportParser.detectFormat(MonefyCsvImportParser.MYMONEY_HEADER),
        )
        assertNotEquals(
            MonefyCsvImportParser.detectFormat(MonefyCsvImportParser.MONEFY_HEADER),
            MonefyCsvImportParser.detectFormat(MonefyCsvImportParser.MYMONEY_TRANSFER_HEADER),
        )
    }

    @Test
    fun `detectFormat with null header returns Unknown`() {
        assertEquals(CsvImportFormat.Unknown, MonefyCsvImportParser.detectFormat(null))
    }

    @Test
    fun `MYMONEY_TRANSFER_HEADER is an additive extension of MYMONEY_HEADER`() {
        val transferHeader = MonefyCsvImportParser.MYMONEY_TRANSFER_HEADER
        val legacyHeader = MonefyCsvImportParser.MYMONEY_HEADER
        assertEquals(legacyHeader.size + 2, transferHeader.size)
        assertEquals(legacyHeader, transferHeader.take(legacyHeader.size))
        assertEquals(listOf("to_account", "to_amount"), transferHeader.takeLast(2))
    }

    @Test
    fun `unknown header is rejected as Unknown`() {
        val result = MonefyCsvImportParser.parseText("foo,bar,baz\r\n")
        assertEquals(CsvImportFormat.Unknown, result.format)
    }

    @Test
    fun `negative amount with nbsp grouping and comma decimal parses to expense abs`() {
        val csv = "$monefyHeader\r\n29/09/2018,Наличные,Продукты,\"-1${nbsp}234,56\",RUB,\"-1${nbsp}234,56\",RUB,хлеб\r\n"
        val row = MonefyCsvImportParser.parseText(csv).rows.single()
        assertEquals(TransactionKind.Expense, row.kind)
        assertEquals(0, BigDecimal("1234.56").compareTo(row.amount))
        assertEquals(LocalDate.of(2018, 9, 29), row.date)
        assertEquals("Наличные", row.accountName)
        assertEquals("Продукты", row.categoryName)
        assertEquals("RUB", row.currencyCode)
        assertEquals("хлеб", row.note)
    }

    @Test
    fun `positive amount parses to income`() {
        val csv = "$monefyHeader\r\n01/01/2020,Динары,Зарплата,50${nbsp}000,RUB,50${nbsp}000,RUB,\r\n"
        // 50<nbsp>000 has no decimal comma so it is a single unquoted CSV field
        val row = MonefyCsvImportParser.parseText(csv).rows.single()
        assertEquals(TransactionKind.Income, row.kind)
        assertEquals(0, BigDecimal("50000").compareTo(row.amount))
        assertNull(row.note)
    }

    @Test
    fun `quoted note with embedded comma is preserved`() {
        val csv =
            "$monefyHeader\r\n29/09/2018,Наличные,Продукты,-100,RUB,-100,RUB,\"хлеб, молоко\"\r\n"
        val row = MonefyCsvImportParser.parseText(csv).rows.single()
        assertEquals("хлеб, молоко", row.note)
    }

    @Test
    fun `rejects a row with too few fields with its exact row message`() {
        val csv = "$monefyHeader\n01/01/2020,Cash,Food,-1,USD"

        val exception =
            assertThrows(IOException::class.java) {
                MonefyCsvImportParser.parseText(csv)
            }

        assertEquals("Invalid Monefy CSV row 2: expected 8 fields", exception.message)
    }

    @Test
    fun `rejects a nonnumeric amount with its exact row message`() {
        val csv = "$monefyHeader\n01/01/2020,Cash,Food,not-a-number,USD,not-a-number,USD,"

        val exception =
            assertThrows(IOException::class.java) {
                MonefyCsvImportParser.parseText(csv)
            }

        assertEquals("Invalid Monefy CSV row 2: amount must be a number", exception.message)
    }

    @Test
    fun `parses LF rows and maps escaped quotes in a description to a quote in the note`() {
        val quotedDescription = "\"He said \"\"hello\"\"\""
        val csv =
            "$monefyHeader\n" +
                "01/01/2020,Cash,Food,-1,USD,-1,USD,$quotedDescription\n" +
                "02/01/2020,Cash,Food,2,USD,2,USD,plain"

        val rows = MonefyCsvImportParser.parseText(csv).rows

        assertEquals(2, rows.size)
        assertEquals("He said \"hello\"", rows[0].note)
        assertEquals("plain", rows[1].note)
    }

    @Test
    fun `rejects an unexpected character after a closing quote`() {
        val csv = "$monefyHeader\n01/01/2020,Cash,Food,-1,USD,-1,USD,\"note\"x"

        val exception =
            assertThrows(IOException::class.java) {
                MonefyCsvImportParser.parseText(csv)
            }

        assertEquals("Unexpected character after quoted CSV field", exception.message)
    }

    @Test
    fun `rejects an unexpected quote in an unquoted field`() {
        val csv = "$monefyHeader\n01/01/2020,Cash,Food,-1,USD,-1,USD,plain\"note"

        val exception =
            assertThrows(IOException::class.java) {
                MonefyCsvImportParser.parseText(csv)
            }

        assertEquals("Unexpected quote in CSV field", exception.message)
    }

    @Test
    fun `zero amount is rejected`() {
        val csv = "$monefyHeader\r\n29/09/2018,Наличные,Продукты,0,RUB,0,RUB,\r\n"
        assertThrows(IOException::class.java) {
            MonefyCsvImportParser.parseText(csv)
        }
    }

    @Test
    fun `invalid date is rejected`() {
        val csv = "$monefyHeader\r\n2018-09-29,Наличные,Продукты,-100,RUB,-100,RUB,\r\n"
        assertThrows(IOException::class.java) {
            MonefyCsvImportParser.parseText(csv)
        }
    }

    @Test
    fun `normalizeName trims, lowercases and collapses inner whitespace`() {
        assertEquals(
            MonefyCsvImportParser.normalizeName("Кафе и рестораны"),
            MonefyCsvImportParser.normalizeName("  Кафе и  рестораны "),
        )
    }

    @Test
    fun `normalizeName collapses non-breaking space runs`() {
        assertEquals(
            MonefyCsvImportParser.normalizeName("Кафе и рестораны"),
            MonefyCsvImportParser.normalizeName("Кафе$nbsp и${nbsp}рестораны"),
        )
    }

    @Test
    fun `normalizeName is case-insensitive`() {
        assertEquals(
            MonefyCsvImportParser.normalizeName("Наличные"),
            MonefyCsvImportParser.normalizeName("наличные"),
        )
    }

    @Test
    fun `normalizeName unifies NFC and NFD forms`() {
        // "Кафе" with a precomposed 'е' vs a base 'е' + combining diaeresis decompose to
        // distinct code-point sequences but must normalize to the same NFC key.
        val precomposed = "Cafeé"
        val decomposed = "Cafeé"
        assertNotEquals(
            "test setup: the two literals must be distinct code-point sequences",
            precomposed,
            decomposed,
        )
        assertEquals(
            MonefyCsvImportParser.normalizeName(precomposed),
            MonefyCsvImportParser.normalizeName(decomposed),
        )
    }

    @Test
    fun `normalizeName distinguishes genuinely different names`() {
        assertNotEquals(
            MonefyCsvImportParser.normalizeName("Продукты"),
            MonefyCsvImportParser.normalizeName("My custom"),
        )
    }

    @Test
    fun `normalizeName on blank string returns empty string`() {
        assertEquals("", MonefyCsvImportParser.normalizeName("   "))
    }

    @Test
    fun `normalizeName on empty string returns empty string`() {
        assertEquals("", MonefyCsvImportParser.normalizeName(""))
    }

    @Test
    fun `normalizeName is idempotent`() {
        val name = "  Кафе и  рестораны "
        assertEquals(
            MonefyCsvImportParser.normalizeName(name),
            MonefyCsvImportParser.normalizeName(MonefyCsvImportParser.normalizeName(name)),
        )
    }

    @Test
    fun `normalizeName collapses mixed ASCII-space and non-breaking-space run`() {
        // Monefy may emit a mix of U+0020 and U+00A0 between words
        val mixed = "Кафе$nbsp и ${nbsp}рестораны"
        assertEquals(
            MonefyCsvImportParser.normalizeName("Кафе и рестораны"),
            MonefyCsvImportParser.normalizeName(mixed),
        )
    }

    @Test
    fun `normalizeName uses Locale ROOT so Turkish dotless-i does not interfere`() {
        // "I" lowercased with Locale.ROOT → "i", not dotless-ı (Turkish locale pitfall)
        val upper = "INCOME"
        val lower = "income"
        assertEquals(
            MonefyCsvImportParser.normalizeName(upper),
            MonefyCsvImportParser.normalizeName(lower),
        )
    }
}
