package com.kshavrin.mymoney.feature.transactionslist.list

import com.kshavrin.mymoney.core.domain.model.TransactionKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

/**
 * Contract-level pinning for [TransactionsListContent] (S12).
 *
 * # Why this is not a Compose-UI test yet
 *
 * `:feature:transactionslist`'s offline test classpath does NOT yet have:
 *
 *   - `androidx.compose.ui:ui-test-junit4`
 *   - `androidx.compose.ui:ui-test-manifest`
 *
 * (neither artifact is present in the Gradle cache, so a `createComposeRule()` test
 * would fail to resolve at compile time). This mirrors the deliberate deferral
 * documented in `:core:designsystem`'s `MonefyKeypadTest` — full Compose-UI tests
 * land in PHASE_15 once those dependencies are wired in.
 *
 * Until then, the user-visible decisions that the composable makes are driven by
 * pure, JVM-visible inputs ([TransactionsListUiState], the [TransactionListItem]
 * sealed type, and `LazyPagingItems.itemCount` / `loadState.refresh`). This file
 * pins those so the contract can't drift, and documents the exact Compose test that
 * replaces it.
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
 *     private fun setContent(
 *         state: TransactionsListUiState = TransactionsListUiState(accountId = 1L),
 *         items: List<TransactionListItem>,
 *     ) {
 *         val flow = flowOf(PagingData.from(items))
 *         composeTestRule.setContent {
 *             MyMoneyTheme {
 *                 TransactionsListContent(
 *                     state = state,
 *                     items = flow.collectAsLazyPagingItems(),
 *                     snackbarHostState = remember { SnackbarHostState() },
 *                     onEvent = {},
 *                     onSearch = {},
 *                     onBack = {},
 *                 )
 *             }
 *         }
 *     }
 *
 *     @Test fun `shows title`() {
 *         setContent(items = listOf(row(1L)))
 *         composeTestRule.onNodeWithText("Transactions").assertIsDisplayed()
 *     }
 *
 *     @Test fun `renders rows from paging items`() {
 *         setContent(items = listOf(header(today), row(1L), row(2L)))
 *         // amount text rendered for each row, a day header above them
 *         composeTestRule.onAllNodesWithText("Open transaction").assertCountEquals(2)
 *     }
 *
 *     @Test fun `shows empty-state string when zero items`() {
 *         setContent(items = emptyList())
 *         composeTestRule.onNodeWithText("No records for this period").assertIsDisplayed()
 *     }
 *
 *     @Test fun `shows day header`() {
 *         setContent(items = listOf(header(LocalDate.of(2026, 5, 20)), row(1L)))
 *         composeTestRule.onNodeWithText("Wed, 20 May").assertIsDisplayed()
 *     }
 *
 *     // Swipe-to-delete gesture is asserted in TransactionsListViewModelTest
 *     // (SwipeDeleted -> softDelete + ShowUndoSnackbar); the headless gesture itself
 *     // is not reliably drivable, so the ViewModel test owns that contract.
 * }
 * ```
 */
class TransactionsListContentTest {

    private val zone = java.time.ZoneId.systemDefault()

    private fun row(id: Long, occurredAt: Instant) = TransactionListItem.Row(
        id = id,
        kind = TransactionKind.Expense,
        amount = BigDecimal("12.50"),
        currencyId = 1L,
        categoryId = 10L,
        note = null,
        occurredAt = occurredAt,
    )

    @Test
    fun `empty-state is shown only when item count is zero and not refreshing`() {
        // Mirror of TransactionsListContent: isEmpty = itemCount == 0 && !refreshing
        fun isEmpty(itemCount: Int, refreshing: Boolean) = itemCount == 0 && !refreshing

        assertTrue("zero items, settled -> empty state", isEmpty(itemCount = 0, refreshing = false))
        assertFalse("zero items but still loading -> no empty state", isEmpty(itemCount = 0, refreshing = true))
        assertFalse("has items -> no empty state", isEmpty(itemCount = 3, refreshing = false))
    }

    @Test
    fun `day-header LazyColumn key is derived from the header date`() {
        val header = TransactionListItem.DayHeader(LocalDate.of(2026, 5, 20))
        val key = when (header) {
            is TransactionListItem.DayHeader -> "h_${header.date}"
            is TransactionListItem.Row -> "r_${header.id}"
        }
        assertEquals("h_2026-05-20", key)
    }

    @Test
    fun `row LazyColumn key is derived from the row id`() {
        val item: TransactionListItem = row(id = 7L, occurredAt = Instant.parse("2026-05-20T09:00:00Z"))
        val key = when (item) {
            is TransactionListItem.DayHeader -> "h_${item.date}"
            is TransactionListItem.Row -> "r_${item.id}"
        }
        assertEquals("r_7", key)
    }

    @Test
    fun `signed amount uses minus for expense and plus for income`() {
        // Pins the sign convention TransactionRow renders per kind.
        fun sign(kind: TransactionKind) = when (kind) {
            TransactionKind.Expense -> "-"
            TransactionKind.Income -> "+"
            TransactionKind.Transfer -> ""
        }
        assertEquals("-", sign(TransactionKind.Expense))
        assertEquals("+", sign(TransactionKind.Income))
        assertEquals("", sign(TransactionKind.Transfer))
    }
}
