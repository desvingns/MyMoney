package com.kshavrin.mymoney.core.domain.csv

import com.kshavrin.mymoney.core.domain.model.CategoryKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/**
 * Light construction/equality tests for [ImportPreview] and its nested data holders.
 * These are pure data classes — one scenario for construction and one for equality is sufficient.
 */
class ImportPreviewTest {
    @Test
    fun `ImportPreview with null dateRange constructs correctly`() {
        val preview =
            ImportPreview(
                rowCount = 0,
                categories = emptySet(),
                accounts = emptySet(),
                dateRange = null,
            )
        assertEquals(0, preview.rowCount)
        assertNull(preview.dateRange)
    }

    @Test
    fun `ImportPreview with populated fields equals another with the same fields`() {
        val cat = PreviewCategory(name = "Food", kind = CategoryKind.Expense)
        val acc = PreviewAccount(name = "Cash", currencyCode = "RUB")
        val range =
            PreviewDateRange(
                start = LocalDate.of(2024, 1, 1),
                end = LocalDate.of(2024, 3, 31),
            )
        val p1 = ImportPreview(rowCount = 42, categories = setOf(cat), accounts = setOf(acc), dateRange = range)
        val p2 = ImportPreview(rowCount = 42, categories = setOf(cat), accounts = setOf(acc), dateRange = range)
        assertEquals(p1, p2)
    }

    @Test
    fun `PreviewCategory equality is field-based`() {
        val a = PreviewCategory("Транспорт", CategoryKind.Expense)
        val b = PreviewCategory("Транспорт", CategoryKind.Expense)
        assertEquals(a, b)
    }

    @Test
    fun `PreviewAccount equality is field-based`() {
        val a = PreviewAccount(name = "Карта", currencyCode = "RUB")
        val b = PreviewAccount(name = "Карта", currencyCode = "RUB")
        assertEquals(a, b)
    }

    @Test
    fun `PreviewDateRange start and end are preserved`() {
        val start = LocalDate.of(2024, 1, 15)
        val end = LocalDate.of(2024, 6, 30)
        val range = PreviewDateRange(start, end)
        assertEquals(start, range.start)
        assertEquals(end, range.end)
    }
}
