package com.kshavrin.mymoney.core.domain.csv

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

class MyMoneyCsvTextEncodingTest {
    @Test
    fun `formula prefixes delimiters whitespace and literal apostrophes round trip as text`() {
        val samples = listOf("=1+1", "+SUM(A1)", "-1+1", "@SUM(A1)", "\t=1+1", "\r=1+1", "\n=1+1", "  =1+1", "＝1+1", "'literal", "a,\"b\"\n=1+1", "", "Кошелёк")
        for (sample in samples) {
            val row = MutableList(11) { "1" }
            MyMoneyCsvTextEncoding.TEXT_COLUMNS.forEach { row[it] = MyMoneyCsvTextEncoding.encode(sample) }
            if (sample.isNotEmpty()) assertEquals('\'', row[6].first())
            val decoded = MyMoneyCsvTextEncoding.decodeRow(row + MyMoneyCsvTextEncoding.VERSION)
            MyMoneyCsvTextEncoding.TEXT_COLUMNS.forEach { assertEquals(sample, decoded[it]) }
            assertEquals("1", decoded[2])
        }
        assertEquals(CsvImportFormat.MyMoney, MonefyCsvImportParser.detectFormat(MonefyCsvImportParser.MYMONEY_SAFE_HEADER))
    }

    @Test(expected = IOException::class)
    fun `unknown encoding is rejected instead of guessing and corrupting text`() {
        MyMoneyCsvTextEncoding.decodeRow(List(11) { "" } + "unknown-v2")
    }
}
