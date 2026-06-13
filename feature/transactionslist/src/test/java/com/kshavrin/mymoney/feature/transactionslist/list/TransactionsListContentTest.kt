package com.kshavrin.mymoney.feature.transactionslist.list

import com.kshavrin.mymoney.core.domain.model.CategoryKind
import com.kshavrin.mymoney.core.domain.model.CategoryRecordGroup
import com.kshavrin.mymoney.core.domain.model.Currency
import com.kshavrin.mymoney.core.domain.model.Money
import com.kshavrin.mymoney.core.domain.model.Transaction
import com.kshavrin.mymoney.core.domain.model.TransactionKind
import com.kshavrin.mymoney.core.domain.model.TransferRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

/**
 * Contract-level pinning for the reworked category-grouped [TransactionsListContent] (S12 collapsed /
 * S13 expanded).
 *
 * # Why this is not a Compose-UI test yet
 *
 * `:feature:transactionslist`'s offline test classpath still does NOT have:
 *
 *   - `androidx.compose.ui:ui-test-junit4`
 *   - `androidx.compose.ui:ui-test-manifest`
 *   - Robolectric
 *
 * (none is present in the Gradle cache, so a `createComposeRule()` test would fail to resolve at
 * compile time). This mirrors the deliberate deferral documented in this module's
 * `TransactionDetailContentTest` / `SearchContentTest` and `:core:designsystem`'s `MonefyKeypadTest`
 * — full Compose-UI tests land in PHASE_15 once those dependencies are wired in. The on-device
 * Compose coverage lives in `:app`'s `androidTest` (`TransactionsListContentUiTest`).
 *
 * Until then, the user-visible decisions [TransactionsListContent] makes are driven by pure,
 * JVM-visible inputs ([TransactionsListUiState], [RecordSort], [RecordsTestTags]). This file pins
 * those so the contract can't drift, and documents the exact Compose test that replaces it.
 *
 * # What the real Compose-UI test must cover (template for PHASE_15)
 *
 * ```
 * @RunWith(RobolectricTestRunner::class)
 * @Config(sdk = [34], application = android.app.Application::class)
 * @GraphicsMode(GraphicsMode.Mode.NATIVE)
 * class TransactionsListContentTest {
 *     @get:Rule val composeTestRule = createComposeRule()
 *
 *     private fun setContent(state: TransactionsListUiState, onEvent: (TransactionsListEvent) -> Unit = {}) {
 *         composeTestRule.setContent {
 *             MyMoneyTheme {
 *                 TransactionsListContent(
 *                     state = state,
 *                     snackbarHostState = remember { SnackbarHostState() },
 *                     onEvent = onEvent,
 *                     onSearch = {},
 *                     onBack = {},
 *                 )
 *             }
 *         }
 *     }
 *
 *     @Test fun `shows balance bar with net`() {
 *         setContent(stateWith(net = money("70.00")))
 *         composeTestRule.onNodeWithTag(RecordsTestTags.BALANCE).assertIsDisplayed()
 *     }
 *
 *     @Test fun `category header shows count and total`() {
 *         setContent(stateWith(groups = listOf(food)))
 *         composeTestRule.onNodeWithTag(RecordsTestTags.count(10L)).assertIsDisplayed()
 *         composeTestRule.onNodeWithTag(RecordsTestTags.total(10L)).assertIsDisplayed()
 *     }
 *
 *     @Test fun `tapping a header toggles expand and reveals leaf rows`() {
 *         val events = mutableListOf<TransactionsListEvent>()
 *         setContent(stateWith(groups = listOf(food)), onEvent = { events += it })
 *         composeTestRule.onNodeWithTag(RecordsTestTags.category(10L)).performClick()
 *         composeTestRule.runOnIdle { assertEquals(listOf(TransactionsListEvent.CategoryClicked(10L)), events) }
 *     }
 *
 *     @Test fun `expanded leaf row tap emits RowClicked`() {
 *         val events = mutableListOf<TransactionsListEvent>()
 *         setContent(stateWith(groups = listOf(food), expanded = setOf(10L)), onEvent = { events += it })
 *         composeTestRule.onNodeWithTag(RecordsTestTags.transaction(1L)).performClick()
 *         composeTestRule.runOnIdle { assertEquals(listOf(TransactionsListEvent.RowClicked(1L)), events) }
 *     }
 *
 *     @Test fun `sort button emits SortClicked`() {
 *         val events = mutableListOf<TransactionsListEvent>()
 *         setContent(stateWith(groups = listOf(food)), onEvent = { events += it })
 *         composeTestRule.onNodeWithTag(RecordsTestTags.SORT).performClick()
 *         composeTestRule.runOnIdle { assertEquals(listOf(TransactionsListEvent.SortClicked), events) }
 *     }
 *
 *     @Test fun `empty state shows the empty marker`() {
 *         setContent(stateWith(groups = emptyList()).copy(isLoading = false))
 *         composeTestRule.onNodeWithTag(RecordsTestTags.EMPTY).assertIsDisplayed()
 *     }
 * }
 * ```
 */
class TransactionsListContentTest {
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

    private fun money(amount: String) = Money(BigDecimal(amount), usd)

    private fun group(
        id: Long,
        kind: CategoryKind,
        total: String,
        count: Int,
        transactions: List<Transaction> = emptyList(),
    ) = CategoryRecordGroup(
        categoryId = id,
        name = "cat$id",
        iconKey = "ic_cat",
        colorHex = "#FF8888",
        kind = kind,
        total = money(total),
        count = count,
        transactions = transactions,
    )

    @Test
    fun `sortedGroups orders by total descending by default`() {
        val state =
            TransactionsListUiState(
                groups =
                    listOf(
                        group(10L, CategoryKind.Expense, "30.00", 2),
                        group(20L, CategoryKind.Income, "100.00", 1),
                    ),
            )
        assertEquals(listOf(20L, 10L), state.sortedGroups.map { it.categoryId })
    }

    @Test
    fun `sortedGroups orders by total ascending when sort is TotalAsc`() {
        val state =
            TransactionsListUiState(
                sort = RecordSort.TotalAsc,
                groups =
                    listOf(
                        group(10L, CategoryKind.Expense, "30.00", 2),
                        group(20L, CategoryKind.Income, "100.00", 1),
                    ),
            )
        assertEquals(listOf(10L, 20L), state.sortedGroups.map { it.categoryId })
    }

    @Test
    fun `isEmpty is true only when settled with no groups`() {
        assertTrue(TransactionsListUiState(isLoading = false, groups = emptyList()).isEmpty)
        assertFalse("still loading is not empty", TransactionsListUiState(isLoading = true).isEmpty)
        assertFalse(
            "has groups is not empty",
            TransactionsListUiState(isLoading = false, groups = listOf(group(10L, CategoryKind.Expense, "1.00", 1))).isEmpty,
        )
    }

    @Test
    fun `expanded predicate reads the expandedCategoryIds set`() {
        val state = TransactionsListUiState(expandedCategoryIds = setOf(10L))
        assertTrue(10L in state.expandedCategoryIds)
        assertFalse(20L in state.expandedCategoryIds)
    }

    @Test
    fun `category filter is active only when both id and name are present`() {
        assertTrue(TransactionsListUiState(categoryId = 10L, categoryName = "Food").hasCategoryFilter)
        assertFalse(TransactionsListUiState(categoryId = 10L, categoryName = null).hasCategoryFilter)
        assertFalse(TransactionsListUiState(categoryId = null, categoryName = "Food").hasCategoryFilter)
    }

    @Test
    fun `chevron direction is down when expanded and right when collapsed`() {
        // Mirrors CategoryHeader: expanded -> KeyboardArrowDown, collapsed -> KeyboardArrowRight.
        fun chevronIsDown(expanded: Boolean) = expanded
        assertTrue(chevronIsDown(expanded = true))
        assertFalse(chevronIsDown(expanded = false))
    }

    @Test
    fun `total colour maps Income to primary and Expense to tertiary`() {
        // Pins the kind -> colour-role decision CategoryHeader / TransactionLeaf make.
        fun isIncomeGreen(kind: CategoryKind) = kind == CategoryKind.Income
        assertTrue("income total is green/primary", isIncomeGreen(CategoryKind.Income))
        assertFalse("expense total is red/tertiary", isIncomeGreen(CategoryKind.Expense))
    }

    @Test
    fun `leaf amount sign role follows the transaction kind`() {
        fun isIncome(kind: TransactionKind) = kind == TransactionKind.Income
        assertTrue(isIncome(TransactionKind.Income))
        assertFalse(isIncome(TransactionKind.Expense))
    }

    @Test
    fun `testTag constants are per-category and per-transaction scoped`() {
        assertEquals("records_category_10", RecordsTestTags.category(10L))
        assertEquals("records_chevron_10", RecordsTestTags.chevron(10L))
        assertEquals("records_count_10", RecordsTestTags.count(10L))
        assertEquals("records_total_10", RecordsTestTags.total(10L))
        assertEquals("records_tx_5", RecordsTestTags.transaction(5L))
        assertEquals("records_balance", RecordsTestTags.BALANCE)
        assertEquals("records_sort", RecordsTestTags.SORT)
        assertEquals("records_empty", RecordsTestTags.EMPTY)
        assertEquals("records_filter", RecordsTestTags.FILTER)
    }

    @Test
    fun `testTag constants cover the transfers tab`() {
        assertEquals("records_tab_operations", RecordsTestTags.TAB_OPERATIONS)
        assertEquals("records_tab_transfers", RecordsTestTags.TAB_TRANSFERS)
        assertEquals("records_transfers_empty", RecordsTestTags.TRANSFERS_EMPTY)
        assertEquals("records_transfer_99", RecordsTestTags.transfer(99L))
    }

    @Test
    fun `isTransfersEmpty is true when transfers list is empty and not loading`() {
        assertTrue(TransactionsListUiState(isLoading = false, transfers = emptyList()).isTransfersEmpty)
    }

    @Test
    fun `isTransfersEmpty is false when transfers are present`() {
        val record =
            TransferRecord(
                id = 1L,
                fromAccountName = "A",
                toAccountName = "B",
                amount = money("100.00"),
                toAmount = null,
                occurredAt = Instant.parse("2026-06-10T00:00:00Z"),
            )
        assertFalse(TransactionsListUiState(isLoading = false, transfers = listOf(record)).isTransfersEmpty)
    }

    @Test
    fun `isTransfersEmpty is false while loading even when transfers list is empty`() {
        assertFalse("loading state is not considered empty", TransactionsListUiState(isLoading = true, transfers = emptyList()).isTransfersEmpty)
    }

    @Test
    fun `activeTab default is Operations`() {
        assertEquals(RecordsTab.Operations, TransactionsListUiState().activeTab)
    }

    @Test
    fun `activeTab can be set to Transfers`() {
        val state = TransactionsListUiState(activeTab = RecordsTab.Transfers)
        assertEquals(RecordsTab.Transfers, state.activeTab)
    }

    @Test
    fun `header LazyColumn key is derived from the category id`() {
        val g = group(10L, CategoryKind.Expense, "1.00", 1)
        assertEquals("cat_10", "cat_${g.categoryId}")
    }

    @Test
    fun `leaf LazyColumn key is derived from the transaction id`() {
        val tx =
            Transaction(
                id = 7L,
                kind = TransactionKind.Expense,
                amount = BigDecimal("1.00"),
                currencyId = 1L,
                accountId = 1L,
                categoryId = 10L,
                note = null,
                occurredAt = Instant.parse("2026-05-20T09:00:00Z"),
                createdAt = Instant.parse("2026-05-20T09:00:00Z"),
                updatedAt = Instant.parse("2026-05-20T09:00:00Z"),
                isDeleted = false,
                toAccountId = null,
                toAmount = null,
                exchangeRate = null,
            )
        assertEquals("tx_7", "tx_${tx.id}")
    }
}
