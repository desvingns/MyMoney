package com.kshavrin.mymoney.feature.transactionslist.list

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kshavrin.mymoney.core.domain.model.CategoryKind
import com.kshavrin.mymoney.core.domain.model.CategoryRecordGroup
import com.kshavrin.mymoney.core.domain.model.Currency
import com.kshavrin.mymoney.core.domain.model.Money
import com.kshavrin.mymoney.core.domain.model.Transaction
import com.kshavrin.mymoney.core.domain.model.TransactionKind
import com.kshavrin.mymoney.core.ui.theme.MyMoneyTheme
import com.kshavrin.mymoney.feature.transactionslist.R
import java.math.BigDecimal
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * On-device Compose-UI coverage for the reworked category-grouped records screen (S12 collapsed /
 * S13 expanded). Renders the public [TransactionsListContent] directly inside [MyMoneyTheme] with
 * [createComposeRule], captures events into a list, and asserts via `runOnIdle`.
 *
 * Mirrors the PHASE_15 template documented in the module-level `TransactionsListContentTest` KDoc
 * and the Pattern B conventions from the green `DashboardContentUiTest`.
 */
@RunWith(AndroidJUnit4::class)
class TransactionsListContentUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `back navigation icon invokes back callback`() {
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
    fun `sort action emits SortClicked event`() {
        val capturedEvents = mutableListOf<TransactionsListEvent>()

        setContent(onEvent = { event -> capturedEvents += event })

        composeTestRule
            .onNodeWithTag(RecordsTestTags.SORT)
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(listOf(TransactionsListEvent.SortClicked), capturedEvents)
        }
    }

    @Test
    fun `empty state shows the empty marker`() {
        setContent(
            state = TransactionsListUiState(
                currency = currency(),
                isLoading = false,
                groups = emptyList(),
            ),
        )

        composeTestRule
            .onNodeWithTag(RecordsTestTags.EMPTY)
            .assertIsDisplayed()
    }

    @Test
    fun `category header shows count and total`() {
        setContent(
            state = TransactionsListUiState(
                currency = currency(),
                isLoading = false,
                groups = listOf(group(id = 10L, kind = CategoryKind.Expense, total = "30.00", count = 2)),
            ),
        )

        composeTestRule
            .onNodeWithTag(RecordsTestTags.count(10L), useUnmergedTree = true)
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithTag(RecordsTestTags.total(10L), useUnmergedTree = true)
            .assertIsDisplayed()
    }

    @Test
    fun `tapping a collapsed header emits CategoryClicked`() {
        val capturedEvents = mutableListOf<TransactionsListEvent>()

        setContent(
            state = TransactionsListUiState(
                currency = currency(),
                isLoading = false,
                groups = listOf(group(id = 10L, kind = CategoryKind.Expense, total = "30.00", count = 2)),
            ),
            onEvent = { event -> capturedEvents += event },
        )

        composeTestRule
            .onNodeWithTag(RecordsTestTags.category(10L))
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(listOf(TransactionsListEvent.CategoryClicked(10L)), capturedEvents)
        }
    }

    @Test
    fun `tapping an expanded leaf row emits RowClicked`() {
        val capturedEvents = mutableListOf<TransactionsListEvent>()

        setContent(
            state = TransactionsListUiState(
                currency = currency(),
                isLoading = false,
                expandedCategoryIds = setOf(10L),
                groups = listOf(
                    group(
                        id = 10L,
                        kind = CategoryKind.Expense,
                        total = "12.34",
                        count = 1,
                        transactions = listOf(transaction(id = 42L, categoryId = 10L)),
                    ),
                ),
            ),
            onEvent = { event -> capturedEvents += event },
        )

        composeTestRule
            .onNodeWithTag(RecordsTestTags.transaction(42L))
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(listOf(TransactionsListEvent.RowClicked(42L)), capturedEvents)
        }
    }

    @Test
    fun `balance bar is shown when net is present`() {
        setContent(
            state = TransactionsListUiState(
                currency = currency(),
                isLoading = false,
                net = money("70.00"),
                groups = listOf(group(id = 10L, kind = CategoryKind.Expense, total = "30.00", count = 2)),
            ),
        )

        composeTestRule
            .onNodeWithTag(RecordsTestTags.BALANCE)
            .assertIsDisplayed()
    }

    private fun setContent(
        state: TransactionsListUiState = TransactionsListUiState(currency = currency(), isLoading = false),
        onEvent: (TransactionsListEvent) -> Unit = {},
        onSearch: () -> Unit = {},
        onBack: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            MyMoneyTheme {
                TransactionsListContent(
                    state = state,
                    snackbarHostState = remember { SnackbarHostState() },
                    onEvent = onEvent,
                    onSearch = onSearch,
                    onBack = onBack,
                )
            }
        }
    }

    private fun currency(): Currency = Currency(
        id = 1L,
        code = "USD",
        symbol = "$",
        name = "US Dollar",
        decimalDigits = 2,
        isActive = true,
        sortOrder = 0,
    )

    private fun money(amount: String): Money = Money(BigDecimal(amount), currency())

    private fun group(
        id: Long,
        kind: CategoryKind,
        total: String,
        count: Int,
        transactions: List<Transaction> = emptyList(),
    ): CategoryRecordGroup = CategoryRecordGroup(
        categoryId = id,
        name = "cat$id",
        iconKey = "ic_cat_food",
        colorHex = "#7AC794",
        kind = kind,
        total = money(total),
        count = count,
        transactions = transactions,
    )

    private fun transaction(id: Long, categoryId: Long): Transaction = Transaction(
        id = id,
        kind = TransactionKind.Expense,
        amount = BigDecimal("12.34"),
        currencyId = 1L,
        accountId = 1L,
        categoryId = categoryId,
        note = "Coffee",
        occurredAt = Instant.parse("2026-05-28T09:00:00Z"),
        createdAt = Instant.parse("2026-05-28T09:00:00Z"),
        updatedAt = Instant.parse("2026-05-28T09:00:00Z"),
        isDeleted = false,
        toAccountId = null,
        toAmount = null,
        exchangeRate = null,
    )

    private fun targetString(resourceId: Int, vararg formatArgs: Any): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(resourceId, *formatArgs)
}
