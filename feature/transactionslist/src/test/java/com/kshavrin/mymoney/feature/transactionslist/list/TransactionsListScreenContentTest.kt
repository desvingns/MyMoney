package com.kshavrin.mymoney.feature.transactionslist.list

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.kshavrin.mymoney.core.domain.model.Currency
import com.kshavrin.mymoney.core.domain.model.Money
import com.kshavrin.mymoney.core.domain.model.SummaryRecord
import com.kshavrin.mymoney.core.domain.model.Transaction
import com.kshavrin.mymoney.core.domain.model.TransactionKind
import com.kshavrin.mymoney.core.domain.model.TransferRecord
import com.kshavrin.mymoney.core.ui.theme.MyMoneyTheme
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.math.BigDecimal
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TransactionsListScreenContentTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `empty loaded state shows title and empty message`() {
        setContent()

        composeTestRule.onNodeWithText("Transactions").assertIsDisplayed()
        composeTestRule.onNodeWithText("No records for this period").assertIsDisplayed()
    }

    @Test
    fun `back and search controls invoke their callbacks`() {
        var backClicks = 0
        var searchClicks = 0
        setContent(
            onBack = { backClicks += 1 },
            onSearch = { searchClicks += 1 },
        )

        composeTestRule.onNodeWithContentDescription("Back").performClick()
        composeTestRule.onNodeWithContentDescription("Search").performClick()

        assertEquals(1, backClicks)
        assertEquals(1, searchClicks)
    }

    @Test
    fun `category filter chip is visible and clearing it emits the filter event`() {
        val events = mutableListOf<TransactionsListEvent>()
        setContent(
            state =
                TransactionsListUiState(
                    categoryId = 10L,
                    categoryName = "Food",
                    isLoading = false,
                ),
            onEvent = { events += it },
        )

        composeTestRule.onNodeWithText("Category: Food").assertIsDisplayed()
        composeTestRule.onNodeWithTag(RecordsTestTags.FILTER).performClick()

        assertEquals(listOf(TransactionsListEvent.CategoryFilterCleared), events)
    }

    @Test
    fun `operation row renders resolved category and note and emits its id`() {
        val events = mutableListOf<TransactionsListEvent>()
        setContent(
            state =
                loadedState(
                    TransactionsListRecord(
                        record = operation(id = 21L, categoryId = 10L, note = "Lunch"),
                        currency = usd,
                        categoryDisplay = TransactionCategoryDisplay("Food", "ic_cat_food"),
                    ),
                ),
            onEvent = { events += it },
        )

        composeTestRule.onNodeWithText("Food").assertIsDisplayed()
        composeTestRule.onNodeWithText("Lunch").assertIsDisplayed()
        composeTestRule.onNodeWithTag(RecordsTestTags.transaction(21L)).performClick()

        assertEquals(listOf(TransactionsListEvent.RowClicked(21L)), events)
    }

    @Test
    fun `transfer row renders its account route and emits its id`() {
        val events = mutableListOf<TransactionsListEvent>()
        setContent(
            state = loadedState(TransactionsListRecord(transferRecord(31L).record, null, null)),
            onEvent = { events += it },
        )

        composeTestRule.onNodeWithText("Cash → Card").assertIsDisplayed()
        composeTestRule.onNodeWithTag(RecordsTestTags.transfer(31L)).performClick()

        assertEquals(listOf(TransactionsListEvent.RowClicked(31L)), events)
    }

    @Test
    fun `operation row falls back to other category and no-comment text`() {
        setContent(
            state =
                loadedState(
                    TransactionsListRecord(
                        record = operation(id = 41L, categoryId = 99L, note = null),
                        currency = null,
                        categoryDisplay = null,
                    ),
                ),
        )

        composeTestRule.onNodeWithText("Other").assertIsDisplayed()
        composeTestRule.onNodeWithText("No comment").assertIsDisplayed()
    }

    private fun setContent(
        state: TransactionsListUiState = TransactionsListUiState(isLoading = false),
        onEvent: (TransactionsListEvent) -> Unit = {},
        onSearch: () -> Unit = {},
        onBack: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            MyMoneyTheme {
                TransactionsListContent(
                    state = state,
                    onEvent = onEvent,
                    onSearch = onSearch,
                    onBack = onBack,
                )
            }
        }
    }

    private fun loadedState(vararg rows: TransactionsListRecord): TransactionsListUiState =
        TransactionsListUiState(
            records = persistentListOf(*rows),
            isLoading = false,
        )

    private fun operation(
        id: Long,
        categoryId: Long?,
        note: String?,
    ): SummaryRecord.Operation =
        SummaryRecord.Operation(
            Transaction(
                id = id,
                kind = TransactionKind.Expense,
                amount = BigDecimal("12.50"),
                currencyId = usd.id,
                accountId = 1L,
                categoryId = categoryId,
                note = note,
                occurredAt = Instant.parse("2026-07-01T12:00:00Z"),
                createdAt = Instant.parse("2026-07-01T12:00:00Z"),
                updatedAt = Instant.parse("2026-07-01T12:00:00Z"),
                isDeleted = false,
                toAccountId = null,
                toAmount = null,
                exchangeRate = null,
            ),
        )

    private fun transferRecord(id: Long): TransactionsListRecord =
        TransactionsListRecord(
            record =
                SummaryRecord.Transfer(
                    TransferRecord(
                        id = id,
                        fromAccountName = "Cash",
                        toAccountName = "Card",
                        amount = Money(BigDecimal("5.00"), usd),
                        toAmount = null,
                        occurredAt = Instant.parse("2026-07-01T12:00:00Z"),
                        note = null,
                    ),
                ),
            currency = null,
            categoryDisplay = null,
        )

    private companion object {
        val usd =
            Currency(
                id = 1L,
                code = "USD",
                symbol = "$",
                name = "US Dollar",
                decimalDigits = 2,
                isActive = true,
                sortOrder = 0,
            )
    }
}
