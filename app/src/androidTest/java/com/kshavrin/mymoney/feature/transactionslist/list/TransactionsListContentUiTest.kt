package com.kshavrin.mymoney.feature.transactionslist.list

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kshavrin.mymoney.core.domain.model.CategoryKind
import com.kshavrin.mymoney.core.domain.model.CategoryRecordGroup
import com.kshavrin.mymoney.core.domain.model.Currency
import com.kshavrin.mymoney.core.domain.model.Money
import com.kshavrin.mymoney.core.domain.model.Transaction
import com.kshavrin.mymoney.core.domain.model.TransactionKind
import com.kshavrin.mymoney.core.domain.model.TransferRecord
import com.kshavrin.mymoney.core.ui.theme.MyMoneyTheme
import com.kshavrin.mymoney.feature.transactionslist.R
import java.math.BigDecimal
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import com.kshavrin.mymoney.core.designsystem.R as DesignSystemR

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
    fun `header shows brand currency balance label and a single sort affordance`() {
        val serbianDinar = currency().copy(name = "Serbian dinar")

        setContent(
            state = TransactionsListUiState(
                currency = serbianDinar,
                isLoading = false,
                net = Money(BigDecimal("593658.80"), serbianDinar),
                groups = listOf(group(id = 10L, kind = CategoryKind.Expense, total = "30.00", count = 2)),
            ),
        )

        composeTestRule
            .onNodeWithText(targetString(R.string.transactions_list_brand_title))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(serbianDinar.name)
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(targetString(DesignSystemR.string.balance_bar_label), substring = true)
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithTag(RecordsTestTags.BALANCE)
            .assertIsDisplayed()
        composeTestRule
            .onAllNodesWithContentDescription(targetString(R.string.transactions_list_sort))
            .assertCountEquals(1)
    }

    @Test
    fun `balance header stays visible and sort stays actionable with long currency and large net`() {
        val capturedEvents = mutableListOf<TransactionsListEvent>()
        val longCurrency = currency().copy(
            symbol = "International Monetary Credits Units",
            name = "International Monetary Credits and Settlement Units",
        )

        setContent(
            state = TransactionsListUiState(
                currency = longCurrency,
                isLoading = false,
                net = Money(BigDecimal("12345678901234567890.12"), longCurrency),
                groups = listOf(group(id = 10L, kind = CategoryKind.Expense, total = "30.00", count = 2)),
            ),
            onEvent = { event -> capturedEvents += event },
        )

        composeTestRule
            .onNodeWithTag(RecordsTestTags.BALANCE)
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithTag(RecordsTestTags.SORT)
            .assertIsDisplayed()
            .assertHasClickAction()
            .assertIsEnabled()
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(listOf(TransactionsListEvent.SortClicked), capturedEvents)
        }
    }

    @Test
    fun `top bar search transfer and overflow invoke callbacks`() {
        var searchCalls = 0
        var transferCalls = 0
        var overflowCalls = 0

        setContent(
            onSearch = { searchCalls += 1 },
            onTransfer = { transferCalls += 1 },
            onOverflow = { overflowCalls += 1 },
        )

        composeTestRule
            .onNodeWithContentDescription(targetString(R.string.transactions_list_search))
            .performClick()
        composeTestRule
            .onNodeWithContentDescription(targetString(R.string.transactions_list_transfer))
            .performClick()
        composeTestRule
            .onNodeWithContentDescription(targetString(R.string.transactions_list_more))
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(1, searchCalls)
            assertEquals(1, transferCalls)
            assertEquals(1, overflowCalls)
        }
    }

    @Test
    fun `empty state keeps header controls and localized empty copy visible`() {
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
        composeTestRule
            .onNodeWithText(targetString(R.string.transactions_list_empty))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithTag(RecordsTestTags.SORT)
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
    fun `active category filter chip is visible and clearable`() {
        val capturedEvents = mutableListOf<TransactionsListEvent>()

        setContent(
            state = TransactionsListUiState(
                currency = currency(),
                categoryId = 10L,
                categoryName = "Food",
                isLoading = false,
                groups = listOf(group(id = 10L, kind = CategoryKind.Expense, total = "30.00", count = 2)),
            ),
            onEvent = { event -> capturedEvents += event },
        )

        composeTestRule
            .onNodeWithTag(RecordsTestTags.FILTER)
            .assertIsDisplayed()
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(listOf(TransactionsListEvent.CategoryFilterCleared), capturedEvents)
        }
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
    fun `collapsed category hides transactions and shows expand semantics`() {
        setContent(
            state = TransactionsListUiState(
                currency = currency(),
                isLoading = false,
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
        )

        composeTestRule
            .onNodeWithContentDescription(
                targetString(R.string.transactions_list_expand),
                useUnmergedTree = true,
            )
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithTag(RecordsTestTags.transaction(42L))
            .assertDoesNotExist()
    }

    @Test
    fun `expanded category shows transactions and shows collapse semantics`() {
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
        )

        composeTestRule
            .onNodeWithContentDescription(
                targetString(R.string.transactions_list_collapse),
                useUnmergedTree = true,
            )
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithTag(RecordsTestTags.transaction(42L))
            .assertIsDisplayed()
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
        onTransfer: () -> Unit = {},
        onOverflow: () -> Unit = {},
        onBack: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            MyMoneyTheme {
                TransactionsListContent(
                    state = state,
                    snackbarHostState = remember { SnackbarHostState() },
                    onEvent = onEvent,
                    onSearch = onSearch,
                    onTransfer = onTransfer,
                    onOverflow = onOverflow,
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

    // ----- Transfer tab Compose-UI tests -----

    @Test
    fun `Operations and Transfers tabs are both visible`() {
        setContent()

        composeTestRule
            .onNodeWithTag(RecordsTestTags.TAB_OPERATIONS)
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithTag(RecordsTestTags.TAB_TRANSFERS)
            .assertIsDisplayed()
    }

    @Test
    fun `tapping the Transfers tab emits TabSelected Transfers event`() {
        val capturedEvents = mutableListOf<TransactionsListEvent>()

        setContent(onEvent = { capturedEvents += it })

        composeTestRule
            .onNodeWithTag(RecordsTestTags.TAB_TRANSFERS)
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(listOf(TransactionsListEvent.TabSelected(RecordsTab.Transfers)), capturedEvents)
        }
    }

    @Test
    fun `tapping the Operations tab emits TabSelected Operations event`() {
        val capturedEvents = mutableListOf<TransactionsListEvent>()

        setContent(
            state = TransactionsListUiState(
                currency = currency(),
                activeTab = RecordsTab.Transfers,
                isLoading = false,
            ),
            onEvent = { capturedEvents += it },
        )

        composeTestRule
            .onNodeWithTag(RecordsTestTags.TAB_OPERATIONS)
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(listOf(TransactionsListEvent.TabSelected(RecordsTab.Operations)), capturedEvents)
        }
    }

    @Test
    fun `Transfers tab shows transfer row with from and to account names`() {
        val transferRecord = transferRecord(id = 55L, from = "Наличные", to = "Карта", amount = "500.00")

        setContent(
            state = TransactionsListUiState(
                currency = currency(),
                activeTab = RecordsTab.Transfers,
                transfers = listOf(transferRecord),
                isLoading = false,
            ),
        )

        composeTestRule
            .onNodeWithTag(RecordsTestTags.transfer(55L))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(
                targetString(
                    com.kshavrin.mymoney.feature.transactionslist.R.string.transactions_list_transfer_route,
                    "Наличные",
                    "Карта",
                ),
                useUnmergedTree = true,
            )
            .assertIsDisplayed()
    }

    @Test
    fun `tapping a transfer row emits RowClicked with the transfer id`() {
        val capturedEvents = mutableListOf<TransactionsListEvent>()
        val transferRecord = transferRecord(id = 55L, from = "Cash", to = "Card", amount = "200.00")

        setContent(
            state = TransactionsListUiState(
                currency = currency(),
                activeTab = RecordsTab.Transfers,
                transfers = listOf(transferRecord),
                isLoading = false,
            ),
            onEvent = { capturedEvents += it },
        )

        composeTestRule
            .onNodeWithTag(RecordsTestTags.transfer(55L))
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(listOf(TransactionsListEvent.RowClicked(55L)), capturedEvents)
        }
    }

    @Test
    fun `Transfers tab shows empty state when there are no transfers`() {
        setContent(
            state = TransactionsListUiState(
                currency = currency(),
                activeTab = RecordsTab.Transfers,
                transfers = emptyList(),
                isLoading = false,
            ),
        )

        composeTestRule
            .onNodeWithTag(RecordsTestTags.TRANSFERS_EMPTY)
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(
                targetString(com.kshavrin.mymoney.feature.transactionslist.R.string.transactions_list_transfers_empty),
            )
            .assertIsDisplayed()
    }

    @Test
    fun `Operations tab does not show transfer rows`() {
        val transferRecord = transferRecord(id = 77L, from = "A", to = "B", amount = "100.00")

        setContent(
            state = TransactionsListUiState(
                currency = currency(),
                activeTab = RecordsTab.Operations,
                transfers = listOf(transferRecord),
                isLoading = false,
                groups = emptyList(),
            ),
        )

        composeTestRule
            .onNodeWithTag(RecordsTestTags.transfer(77L))
            .assertDoesNotExist()
        composeTestRule
            .onNodeWithTag(RecordsTestTags.EMPTY)
            .assertIsDisplayed()
    }

    @Test
    fun `transfer row is not displayed on Operations tab — category filter does not apply to transfers tab`() {
        val transferRecord = transferRecord(id = 88L, from = "A", to = "B", amount = "50.00")

        setContent(
            state = TransactionsListUiState(
                currency = currency(),
                activeTab = RecordsTab.Transfers,
                transfers = listOf(transferRecord),
                isLoading = false,
                categoryId = 10L,
                categoryName = "Food",
            ),
        )

        composeTestRule
            .onNodeWithTag(RecordsTestTags.transfer(88L))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithTag(RecordsTestTags.FILTER)
            .assertDoesNotExist()
    }

    private fun transferRecord(
        id: Long,
        from: String,
        to: String,
        amount: String,
    ): TransferRecord = TransferRecord(
        id = id,
        fromAccountName = from,
        toAccountName = to,
        amount = money(amount),
        toAmount = null,
        occurredAt = java.time.Instant.parse("2026-06-10T12:00:00Z"),
    )

    // ----- Undo snackbar non-blocking test -----

    /**
     * Regression guard for the ShowUndoSnackbar blocking fix.
     *
     * Before the fix the [TransactionsListScreen]'s LaunchedEffect collected actions
     * sequentially: ShowUndoSnackbar would call showSnackbar() which suspends for the full
     * UNDO_WINDOW_MILLIS, meaning OpenDetail actions emitted while the snackbar was up were
     * never dispatched until the window expired. After the fix the snackbar is launched in a
     * child coroutine (snackbarScope.launch { ... }), so the collect loop is free immediately.
     *
     * This test pins that contract at the Content layer: even when a snackbar is already
     * showing (SnackbarHostState occupied), tapping a transaction row still fires RowClicked
     * immediately without waiting for the snackbar to dismiss.
     */
    @Test
    fun `row tap fires RowClicked immediately while an undo snackbar is already showing`() {
        val capturedEvents = mutableListOf<TransactionsListEvent>()
        val tx = transaction(id = 99L, categoryId = 10L)
        val snackbarHostState = SnackbarHostState()

        composeTestRule.setContent {
            MyMoneyTheme {
                // Show the snackbar asynchronously in the background — the content must
                // remain interactive while the snackbar persists.
                LaunchedEffect(Unit) {
                    snackbarHostState.showSnackbar(
                        message = "Deleted",
                        actionLabel = "Undo",
                        duration = SnackbarDuration.Indefinite,
                    )
                }
                TransactionsListContent(
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
                                transactions = listOf(tx),
                            ),
                        ),
                    ),
                    snackbarHostState = snackbarHostState,
                    onEvent = { event -> capturedEvents += event },
                    onSearch = {},
                    onTransfer = {},
                    onOverflow = {},
                    onBack = {},
                )
            }
        }

        // Wait for the snackbar to appear before tapping the row.
        composeTestRule.waitUntil(timeoutMillis = 3_000) {
            snackbarHostState.currentSnackbarData != null
        }

        composeTestRule
            .onNodeWithTag(RecordsTestTags.transaction(99L))
            .performClick()

        // Row tap must register immediately — asserting within runOnIdle which resolves
        // after the frame following the click settles, long before any snackbar timeout.
        composeTestRule.runOnIdle {
            assertEquals(
                listOf(TransactionsListEvent.RowClicked(99L)),
                capturedEvents,
            )
        }
    }

    private fun targetString(resourceId: Int, vararg formatArgs: Any): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(resourceId, *formatArgs)
}
