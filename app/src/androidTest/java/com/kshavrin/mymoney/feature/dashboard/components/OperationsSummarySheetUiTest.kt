package com.kshavrin.mymoney.feature.dashboard.components

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kshavrin.mymoney.core.domain.model.Currency
import com.kshavrin.mymoney.core.domain.model.Money
import com.kshavrin.mymoney.core.domain.model.SummaryRecord
import com.kshavrin.mymoney.core.domain.model.Transaction
import com.kshavrin.mymoney.core.domain.model.TransactionKind
import com.kshavrin.mymoney.core.domain.model.TransferRecord
import com.kshavrin.mymoney.core.ui.theme.MyMoneyTheme
import com.kshavrin.mymoney.feature.dashboard.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class OperationsSummarySheetUiTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `mixed records show all-operations title and preserve supplied row order`() {
        val records =
            listOf(
                operationRecord(
                    id = 301L,
                    occurredAt = Instant.parse("2026-06-25T12:00:00Z"),
                    kind = TransactionKind.Expense,
                    note = null,
                    categoryId = 11L,
                ),
                transferRecord(
                    id = 302L,
                    occurredAt = Instant.parse("2026-06-25T11:00:00Z"),
                    fromAccountName = "Cash",
                    toAccountName = "Savings",
                    note = null,
                ),
                operationRecord(
                    id = 303L,
                    occurredAt = Instant.parse("2026-06-25T10:00:00Z"),
                    kind = TransactionKind.Income,
                    note = null,
                    categoryId = 12L,
                ),
            )

        composeTestRule.setContent {
            MyMoneyTheme {
                OperationsSummarySheet(
                    records = records,
                    loading = false,
                    title = targetString(R.string.operations_summary_title_all_operations),
                    onRowClick = {},
                    onDismiss = {},
                    currencies = listOf(usd),
                    categoryDisplays =
                        mapOf(
                            11L to SummaryRecordCategoryDisplay(
                                name = targetString(R.string.category_other),
                                iconKey = "ic_cat_other",
                            ),
                            12L to SummaryRecordCategoryDisplay(
                                name = targetString(R.string.category_other),
                                iconKey = "ic_cat_other",
                            ),
                        ),
                )
            }
        }

        composeTestRule
            .onNodeWithTag(OPERATIONS_SUMMARY_SHEET_TAG)
            .assertExists()
        composeTestRule
            .onNodeWithText(targetString(R.string.operations_summary_title_all_operations))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithTag(operationsSummaryRowTag(301L))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithTag(operationsSummaryRowTag(302L))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithTag(operationsSummaryRowTag(303L))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(
                targetString(
                    R.string.operations_summary_transfer_route,
                    "Cash",
                    "Savings",
                ),
            ).assertIsDisplayed()

        assertRowsAreTopToBottom(301L, 302L, 303L)
    }

    @Test
    fun `category title mode shows supplied title and no transfer row route`() {
        val categoryTitle = targetString(R.string.dashboard_currency_card_expense)

        composeTestRule.setContent {
            MyMoneyTheme {
                OperationsSummarySheet(
                    records =
                        listOf(
                            operationRecord(
                                id = 401L,
                                occurredAt = Instant.parse("2026-06-24T12:00:00Z"),
                                note = null,
                                categoryId = 77L,
                            ),
                            operationRecord(
                                id = 402L,
                                occurredAt = Instant.parse("2026-06-23T12:00:00Z"),
                                note = null,
                                categoryId = 77L,
                            ),
                        ),
                    loading = false,
                    title = categoryTitle,
                    onRowClick = {},
                    onDismiss = {},
                    currencies = listOf(usd),
                    categoryDisplays =
                        mapOf(
                            77L to SummaryRecordCategoryDisplay(
                                name = targetString(R.string.category_other),
                                iconKey = "ic_cat_other",
                            ),
                        ),
                )
            }
        }

        composeTestRule
            .onNodeWithText(categoryTitle)
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithTag(operationsSummaryRowTag(401L))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithTag(operationsSummaryRowTag(402L))
            .assertIsDisplayed()
        composeTestRule
            .onAllNodesWithText(
                targetString(
                    R.string.operations_summary_transfer_route,
                    "Cash",
                    "Savings",
                ),
            ).assertCountEquals(0)
    }

    @Test
    fun `empty state exposes tagged placeholder container and empty-state copy`() {
        composeTestRule.setContent {
            MyMoneyTheme {
                OperationsSummarySheet(
                    records = emptyList(),
                    loading = false,
                    title = targetString(R.string.operations_summary_title_all_operations),
                    onRowClick = {},
                    onDismiss = {},
                )
            }
        }

        composeTestRule
            .onNodeWithTag(OPERATIONS_SUMMARY_EMPTY_TAG)
            .assertExists()
        composeTestRule
            .onNodeWithText(targetString(R.string.operations_summary_empty))
            .assertExists()
    }

    @Test
    fun `tapping an offscreen row emits its id after scrolling into view`() {
        val clickedIds = mutableListOf<Long>()
        val records =
            (1L..24L).map { index ->
                operationRecord(
                    id = 500L + index,
                    occurredAt = Instant.parse("2026-06-25T12:00:00Z").minusSeconds(index * 60),
                    note = null,
                    categoryId = 90L,
                )
            }
        val targetId = 524L

        composeTestRule.setContent {
            MyMoneyTheme {
                OperationsSummarySheet(
                    records = records,
                    loading = false,
                    title = targetString(R.string.operations_summary_title_all_operations),
                    onRowClick = { clickedIds += it },
                    onDismiss = {},
                    currencies = listOf(usd),
                    categoryDisplays =
                        mapOf(
                            90L to SummaryRecordCategoryDisplay(
                                name = targetString(R.string.category_other),
                                iconKey = "ic_cat_other",
                            ),
                        ),
                )
            }
        }

        composeTestRule
            .onNodeWithTag(operationsSummaryRowTag(targetId))
            .performScrollTo()
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(listOf(targetId), clickedIds)
        }
    }

    @Test
    fun `loading state exposes a progress indicator`() {
        composeTestRule.setContent {
            MyMoneyTheme {
                OperationsSummarySheet(
                    records = listOf(operationRecord(id = 601L, occurredAt = Instant.parse("2026-06-25T12:00:00Z"))),
                    loading = true,
                    title = targetString(R.string.operations_summary_title_all_operations),
                    onRowClick = {},
                    onDismiss = {},
                )
            }
        }

        composeTestRule
            .onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate))
            .assertExists()
    }

    private fun assertRowsAreTopToBottom(
        firstId: Long,
        secondId: Long,
        thirdId: Long,
    ) {
        val firstTop = composeTestRule.onNodeWithTag(operationsSummaryRowTag(firstId)).fetchSemanticsNode().boundsInRoot.top
        val secondTop = composeTestRule.onNodeWithTag(operationsSummaryRowTag(secondId)).fetchSemanticsNode().boundsInRoot.top
        val thirdTop = composeTestRule.onNodeWithTag(operationsSummaryRowTag(thirdId)).fetchSemanticsNode().boundsInRoot.top

        assertTrue(
            "expected rows to stay in supplied order, got tops: $firstTop, $secondTop, $thirdTop",
            firstTop < secondTop && secondTop < thirdTop,
        )
    }

    private fun targetString(
        resourceId: Int,
        vararg formatArgs: Any,
    ): String = InstrumentationRegistry.getInstrumentation().targetContext.getString(resourceId, *formatArgs)

    private fun operationRecord(
        id: Long,
        occurredAt: Instant,
        kind: TransactionKind = TransactionKind.Expense,
        note: String? = null,
        categoryId: Long? = 1L,
        amount: BigDecimal = BigDecimal("12.34"),
    ) = SummaryRecord.Operation(
        transaction =
            Transaction(
                id = id,
                kind = kind,
                amount = amount,
                currencyId = usd.id,
                accountId = 1L,
                categoryId = categoryId,
                note = note,
                occurredAt = occurredAt,
                createdAt = occurredAt,
                updatedAt = occurredAt,
                isDeleted = false,
                toAccountId = null,
                toAmount = null,
                exchangeRate = null,
            ),
    )

    private fun transferRecord(
        id: Long,
        occurredAt: Instant,
        fromAccountName: String,
        toAccountName: String,
        note: String? = null,
    ) = SummaryRecord.Transfer(
        transfer =
            TransferRecord(
                id = id,
                fromAccountName = fromAccountName,
                toAccountName = toAccountName,
                amount = Money(amount = BigDecimal("50.00"), currency = usd),
                toAmount = null,
                occurredAt = occurredAt,
                note = note,
            ),
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
