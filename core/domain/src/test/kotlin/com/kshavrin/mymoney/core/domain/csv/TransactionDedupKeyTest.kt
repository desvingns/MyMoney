package com.kshavrin.mymoney.core.domain.csv

import com.kshavrin.mymoney.core.domain.model.TransactionKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

class TransactionDedupKeyTest {
    private val base = Instant.parse("2024-03-15T10:00:00Z")

    // -------------------------------------------------------------------------
    // TransactionDedupKey.of — factory normalisation
    // -------------------------------------------------------------------------

    @Test
    fun `of normalizes account name to lowercase trimmed NFC`() {
        val key =
            TransactionDedupKey.of(
                accountName = "  Наличные  ",
                occurredAt = base,
                amount = BigDecimal("50"),
                categoryName = "еда",
                kind = TransactionKind.Expense,
                note = null,
            )
        assertEquals("наличные", key.accountKey)
    }

    @Test
    fun `of normalizes category name to lowercase trimmed NFC`() {
        val key =
            TransactionDedupKey.of(
                accountName = "cash",
                occurredAt = base,
                amount = BigDecimal("50"),
                categoryName = "  Кафе  ",
                kind = TransactionKind.Expense,
                note = null,
            )
        assertEquals("кафе", key.categoryKey)
    }

    @Test
    fun `amount 100 and 100 point 00 produce equal keys`() {
        val key1 =
            TransactionDedupKey.of(
                accountName = "cash",
                occurredAt = base,
                amount = BigDecimal("100"),
                categoryName = "food",
                kind = TransactionKind.Expense,
                note = null,
            )
        val key2 =
            TransactionDedupKey.of(
                accountName = "cash",
                occurredAt = base,
                amount = BigDecimal("100.00"),
                categoryName = "food",
                kind = TransactionKind.Expense,
                note = null,
            )
        assertEquals(key1, key2)
        assertEquals(key1.hashCode(), key2.hashCode())
    }

    @Test
    fun `amount zero variants all collapse to the same key`() {
        val key0 = TransactionDedupKey.of("a", base, BigDecimal.ZERO, "c", TransactionKind.Expense, null)
        val key00 = TransactionDedupKey.of("a", base, BigDecimal("0.00"), "c", TransactionKind.Expense, null)
        assertEquals(key0, key00)
    }

    @Test
    fun `keys with different note are not equal`() {
        val key1 = TransactionDedupKey.of("cash", base, BigDecimal("50"), "food", TransactionKind.Expense, null)
        val key2 = TransactionDedupKey.of("cash", base, BigDecimal("50"), "food", TransactionKind.Expense, "dinner")
        assertNotEquals(key1, key2)
    }

    @Test
    fun `keys differing only by category are not equal`() {
        val key1 = TransactionDedupKey.of("cash", base, BigDecimal("50"), "food", TransactionKind.Expense, null)
        val key2 = TransactionDedupKey.of("cash", base, BigDecimal("50"), "transport", TransactionKind.Expense, null)
        assertNotEquals(key1, key2)
    }

    @Test
    fun `keys differing only by kind are not equal`() {
        val key1 = TransactionDedupKey.of("cash", base, BigDecimal("50"), "food", TransactionKind.Expense, null)
        val key2 = TransactionDedupKey.of("cash", base, BigDecimal("50"), "food", TransactionKind.Income, null)
        assertNotEquals(key1, key2)
    }

    @Test
    fun `keys differing only by timestamp are not equal`() {
        val other = Instant.parse("2024-03-16T10:00:00Z")
        val key1 = TransactionDedupKey.of("cash", base, BigDecimal("50"), "food", TransactionKind.Expense, null)
        val key2 = TransactionDedupKey.of("cash", other, BigDecimal("50"), "food", TransactionKind.Expense, null)
        assertNotEquals(key1, key2)
    }

    @Test
    fun `keys differing only by account are not equal`() {
        val key1 = TransactionDedupKey.of("cash", base, BigDecimal("50"), "food", TransactionKind.Expense, null)
        val key2 = TransactionDedupKey.of("card", base, BigDecimal("50"), "food", TransactionKind.Expense, null)
        assertNotEquals(key1, key2)
    }
}
