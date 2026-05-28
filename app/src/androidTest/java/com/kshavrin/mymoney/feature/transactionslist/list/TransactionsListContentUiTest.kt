package com.kshavrin.mymoney.feature.transactionslist.list

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kshavrin.mymoney.core.domain.model.Category
import com.kshavrin.mymoney.core.domain.model.CategoryKind
import com.kshavrin.mymoney.core.domain.model.Currency
import com.kshavrin.mymoney.core.domain.model.TransactionKind
import com.kshavrin.mymoney.core.ui.theme.MyMoneyTheme
import com.kshavrin.mymoney.feature.transactionslist.R
import java.math.BigDecimal
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TransactionsListContentUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `back button invokes back callback`() {
        var backCalls = 0

        setContent(onBack = { backCalls += 1 })

        composeTestRule
            .onNodeWithContentDescription(targetString(R.string.transactions_list_back))
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(1, backCalls)
        }
    }

    @Test
    fun `search button invokes search callback`() {
        var searchCalls = 0

        setContent(onSearch = { searchCalls += 1 })

        composeTestRule
            .onNodeWithContentDescription(targetString(R.string.transactions_list_search))
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(1, searchCalls)
        }
    }

    @Test
    fun `empty state shows no records message`() {
        val emptyMessage = targetString(R.string.transactions_list_empty)

        setContent(items = emptyList())

        composeTestRule.waitUntil(timeoutMillis = 2_000) {
            composeTestRule.onAllNodesWithText(emptyMessage).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule
            .onNodeWithText(emptyMessage)
            .assertIsDisplayed()
    }

    @Test
    fun `category filter chip is visible above rows`() {
        setContent(
            state = TransactionsListUiState(
                categoryFilter = category(id = 10L, name = "Food"),
                currency = currency(),
            ),
            items = listOf(row(id = 7L, note = "Coffee")),
        )

        composeTestRule
            .onNodeWithText(targetString(R.string.transactions_list_filter_chip, "Food"))
            .assertIsDisplayed()
    }

    @Test
    fun `transaction row keeps note and emits row clicked event`() {
        val capturedEvents = mutableListOf<TransactionsListEvent>()

        setContent(
            state = TransactionsListUiState(currency = currency()),
            items = listOf(row(id = 42L, note = "Coffee")),
            onEvent = { event -> capturedEvents += event },
        )

        composeTestRule
            .onNodeWithText("Coffee")
            .assertIsDisplayed()
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(listOf(TransactionsListEvent.RowClicked(42L)), capturedEvents)
        }
    }

    private fun setContent(
        state: TransactionsListUiState = TransactionsListUiState(currency = currency()),
        items: List<TransactionListItem> = emptyList(),
        onEvent: (TransactionsListEvent) -> Unit = {},
        onSearch: () -> Unit = {},
        onBack: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            val pagingItems = remember(items) {
                Pager(PagingConfig(pageSize = 20)) { StaticPagingSource(items) }.flow
            }.collectAsLazyPagingItems()
            MyMoneyTheme {
                TransactionsListContent(
                    state = state,
                    items = pagingItems,
                    snackbarHostState = remember { SnackbarHostState() },
                    onEvent = onEvent,
                    onSearch = onSearch,
                    onBack = onBack,
                )
            }
        }
    }

    private fun row(id: Long, note: String): TransactionListItem.Row = TransactionListItem.Row(
        id = id,
        kind = TransactionKind.Expense,
        amount = BigDecimal("12.34"),
        currencyId = 1L,
        categoryId = 10L,
        note = note,
        occurredAt = Instant.parse("2026-05-28T09:00:00Z"),
    )

    private fun category(id: Long, name: String): Category = Category(
        id = id,
        name = name,
        kind = CategoryKind.Expense,
        iconKey = "ic_cat_food",
        colorHex = "#7AC794",
        sortOrder = 0,
        isDefault = false,
        isArchived = false,
        createdAt = Instant.parse("2026-05-28T00:00:00Z"),
    )

    private fun currency(): Currency = Currency(
        id = 1L,
        code = "USD",
        symbol = "$",
        name = "US Dollar",
        decimalDigits = 2,
        isActive = true,
        sortOrder = 0,
    )

    private fun targetString(resourceId: Int, vararg formatArgs: Any): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(resourceId, *formatArgs)

    private class StaticPagingSource(
        private val items: List<TransactionListItem>,
    ) : PagingSource<Int, TransactionListItem>() {
        override fun getRefreshKey(state: PagingState<Int, TransactionListItem>): Int? = null

        override suspend fun load(params: LoadParams<Int>): LoadResult<Int, TransactionListItem> =
            LoadResult.Page(
                data = items,
                prevKey = null,
                nextKey = null,
            )
    }
}
