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
}
