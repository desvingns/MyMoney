package com.kshavrin.mymoney.feature.transactionslist.list

import com.kshavrin.mymoney.core.domain.model.Currency
import com.kshavrin.mymoney.core.domain.model.Money
import com.kshavrin.mymoney.core.domain.model.SummaryRecord
import com.kshavrin.mymoney.core.domain.model.TransferRecord
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class TransactionsListUiStateTest {
    private val usd =
        Currency(
            id = 1L,
            code = "USD",
            symbol = "$",
            name = "US Dollar",
            decimalDigits = 2,
            isActive = true,
            sortOrder = 0,
        )

    @Test
    fun `loading state with no records is not reported as empty`() {
        assertFalse(TransactionsListUiState().isEmpty)
    }

    @Test
    fun `loaded state with no records is reported as empty`() {
        assertTrue(TransactionsListUiState(isLoading = false).isEmpty)
    }

    @Test
    fun `category filter requires both an id and a non-blank name`() {
        assertFalse(TransactionsListUiState(categoryId = 4L).hasCategoryFilter)
        assertFalse(TransactionsListUiState(categoryId = 4L, categoryName = " ").hasCategoryFilter)
        assertTrue(TransactionsListUiState(categoryId = 4L, categoryName = "Food").hasCategoryFilter)
    }

    @Test
    fun `state keeps immutable records with their resolved display data`() {
        val record =
            SummaryRecord.Transfer(
                TransferRecord(
                    id = 8L,
                    fromAccountName = "Cash",
                    toAccountName = "Card",
                    amount = Money(java.math.BigDecimal("5.00"), usd),
                    toAmount = null,
                    occurredAt = Instant.parse("2026-07-01T12:00:00Z"),
                    note = "Top up",
                ),
            )
        val row =
            TransactionsListRecord(
                record = record,
                currency = null,
                categoryDisplay = TransactionCategoryDisplay("Food", "ic_cat_food"),
            )
        val state = TransactionsListUiState(records = persistentListOf(row), isLoading = false)

        assertEquals(row, state.records.single())
        assertEquals(
            "Food",
            state.records
                .single()
                .categoryDisplay
                ?.name,
        )
        assertEquals(1, state.records.size)
        assertFalse(state.isEmpty)
    }
}
