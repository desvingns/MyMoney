package com.kshavrin.mymoney.core.domain.csv

import com.kshavrin.mymoney.core.domain.model.TransactionKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

/**
 * Behavioural tests for [dedupTransactions].
 * All six key fields are exercised per the SPEC constraint: accountKey, occurredAt, amount,
 * categoryKey, kind, note.
 */
class DedupTransactionsTest {
    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private val t0 = Instant.parse("2024-03-15T10:00:00Z")
    private val t1 = Instant.parse("2024-03-16T10:00:00Z")

    private data class Row(
        val account: String,
        val at: Instant,
        val amount: BigDecimal,
        val category: String,
        val kind: TransactionKind,
        val note: String?,
    )

    private fun Row.key() = TransactionDedupKey.of(account, at, amount, category, kind, note)

    private fun row(
        account: String = "cash",
        at: Instant = t0,
        amount: BigDecimal = BigDecimal("100"),
        category: String = "food",
        kind: TransactionKind = TransactionKind.Expense,
        note: String? = null,
    ) = Row(account, at, amount, category, kind, note)

    private fun dedup(
        items: List<Row>,
        existing: Set<TransactionDedupKey> = emptySet(),
    ) = dedupTransactions(items, existing) { it.key() }

    // -------------------------------------------------------------------------
    // (a) Two identical rows within the import — only one survives
    // -------------------------------------------------------------------------

    @Test
    fun `two identical rows in import — exactly one is unique`() {
        val r = row()
        val result = dedup(listOf(r, r))

        assertEquals(1, result.unique.size)
        assertEquals(1, result.duplicates.size)
    }

    @Test
    fun `first occurrence wins when two identical rows are present`() {
        val r1 = row(note = null)
        val r2 = row(note = null)
        val result = dedup(listOf(r1, r2))

        assertEquals(r1, result.unique.single())
        assertEquals(r2, result.duplicates.single())
    }

    @Test
    fun `three identical rows produce one unique and two duplicates`() {
        val r = row()
        val result = dedup(listOf(r, r, r))

        assertEquals(1, result.unique.size)
        assertEquals(2, result.duplicates.size)
    }

    // -------------------------------------------------------------------------
    // (b) Row key matches an existing key — excluded as duplicate
    // -------------------------------------------------------------------------

    @Test
    fun `row matching existing key is placed in duplicates`() {
        val r = row()
        val existing = setOf(r.key())
        val result = dedup(listOf(r), existing = existing)

        assertTrue(result.unique.isEmpty())
        assertEquals(listOf(r), result.duplicates)
    }

    @Test
    fun `row not matching existing key is unique`() {
        val r = row(category = "transport")
        val existing = setOf(row(category = "food").key())
        val result = dedup(listOf(r), existing = existing)

        assertEquals(listOf(r), result.unique)
        assertTrue(result.duplicates.isEmpty())
    }

    @Test
    fun `mix of new and existing-key rows splits correctly`() {
        val existing = row(account = "card")
        val fresh = row(account = "cash")
        val existingKeys = setOf(existing.key())

        val result = dedup(listOf(existing, fresh), existing = existingKeys)

        assertEquals(listOf(fresh), result.unique)
        assertEquals(listOf(existing), result.duplicates)
    }

    // -------------------------------------------------------------------------
    // (c) Rows differing only by note — NOT duplicates
    // -------------------------------------------------------------------------

    @Test
    fun `rows differing only by note are both unique`() {
        val r1 = row(note = null)
        val r2 = row(note = "dinner")
        val result = dedup(listOf(r1, r2))

        assertEquals(2, result.unique.size)
        assertTrue(result.duplicates.isEmpty())
    }

    @Test
    fun `rows differing only by non-null note values are both unique`() {
        val r1 = row(note = "lunch")
        val r2 = row(note = "dinner")
        val result = dedup(listOf(r1, r2))

        assertEquals(2, result.unique.size)
        assertTrue(result.duplicates.isEmpty())
    }

    // -------------------------------------------------------------------------
    // (c) Rows differing only by category — NOT duplicates
    // -------------------------------------------------------------------------

    @Test
    fun `rows differing only by category are both unique`() {
        val r1 = row(category = "food")
        val r2 = row(category = "transport")
        val result = dedup(listOf(r1, r2))

        assertEquals(2, result.unique.size)
        assertTrue(result.duplicates.isEmpty())
    }

    // -------------------------------------------------------------------------
    // (d) Amounts "100" vs "100.00" — treated as equal (scale-insensitive)
    // -------------------------------------------------------------------------

    @Test
    fun `100 and 100 point 00 are treated as the same key`() {
        val r1 = row(amount = BigDecimal("100"))
        val r2 = row(amount = BigDecimal("100.00"))
        val result = dedup(listOf(r1, r2))

        assertEquals(1, result.unique.size)
        assertEquals(1, result.duplicates.size)
    }

    @Test
    fun `existing key with scale 100 matches incoming row with scale 100 point 00`() {
        val incomingRow = row(amount = BigDecimal("100.00"))
        val existingKey = row(amount = BigDecimal("100")).key()

        val result = dedup(listOf(incomingRow), existing = setOf(existingKey))

        assertTrue(result.unique.isEmpty())
        assertEquals(listOf(incomingRow), result.duplicates)
    }

    // -------------------------------------------------------------------------
    // Edge cases
    // -------------------------------------------------------------------------

    @Test
    fun `empty import list produces empty outcome`() {
        val result = dedup(emptyList())

        assertTrue(result.unique.isEmpty())
        assertTrue(result.duplicates.isEmpty())
    }

    @Test
    fun `single unique row survives when no existing keys`() {
        val r = row()
        val result = dedup(listOf(r))

        assertEquals(listOf(r), result.unique)
        assertTrue(result.duplicates.isEmpty())
    }

    @Test
    fun `rows differing only by kind are both unique`() {
        val r1 = row(kind = TransactionKind.Expense)
        val r2 = row(kind = TransactionKind.Income)
        val result = dedup(listOf(r1, r2))

        assertEquals(2, result.unique.size)
        assertTrue(result.duplicates.isEmpty())
    }

    @Test
    fun `rows differing only by timestamp are both unique`() {
        val r1 = row(at = t0)
        val r2 = row(at = t1)
        val result = dedup(listOf(r1, r2))

        assertEquals(2, result.unique.size)
        assertTrue(result.duplicates.isEmpty())
    }

    @Test
    fun `rows differing only by account are both unique`() {
        val r1 = row(account = "cash")
        val r2 = row(account = "card")
        val result = dedup(listOf(r1, r2))

        assertEquals(2, result.unique.size)
        assertTrue(result.duplicates.isEmpty())
    }

    @Test
    fun `all existing-key rows are excluded and all fresh rows are kept`() {
        val existingRow = row(at = t0)
        val freshRow1 = row(at = t1, category = "transport")
        val freshRow2 = row(at = t1, category = "food")
        val existingKeys = setOf(existingRow.key())

        val result = dedup(listOf(existingRow, freshRow1, freshRow2), existing = existingKeys)

        assertEquals(listOf(freshRow1, freshRow2), result.unique)
        assertEquals(listOf(existingRow), result.duplicates)
    }

    @Test
    fun `intra-import dupe after existing-key dupe still goes to duplicates`() {
        val r = row()
        val existingKeys = setOf(r.key())

        val result = dedup(listOf(r, r), existing = existingKeys)

        assertTrue(result.unique.isEmpty())
        assertEquals(2, result.duplicates.size)
    }
}
