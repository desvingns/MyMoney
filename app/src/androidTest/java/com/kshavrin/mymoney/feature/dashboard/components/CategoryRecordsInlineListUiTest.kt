package com.kshavrin.mymoney.feature.dashboard.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kshavrin.mymoney.core.domain.model.Currency
import com.kshavrin.mymoney.core.domain.model.Transaction
import com.kshavrin.mymoney.core.domain.model.TransactionKind
import com.kshavrin.mymoney.core.ui.theme.MyMoneyTheme
import com.kshavrin.mymoney.feature.dashboard.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class CategoryRecordsInlineListUiTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `record row shows note amount and date when note is present`() {
        val record =
            makeTransaction(
                id = 1L,
                note = "Coffee",
                amount = BigDecimal("350"),
                currencyId = usd.id,
                occurredAt = Instant.parse("2026-06-15T09:00:00Z"),
            )

        composeTestRule.setContent {
            MyMoneyTheme {
                Box(modifier = Modifier.width(400.dp)) {
                    CategoryRecordsInlineList(
                        records = listOf(record),
                        loading = false,
                        currencies = listOf(usd),
                        onRowClick = {},
                    )
                }
            }
        }

        composeTestRule
            .onNodeWithTag(DASHBOARD_INLINE_RECORDS_TAG)
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText("Coffee")
            .assertIsDisplayed()
    }

    @Test
    fun `record row shows neutral placeholder when note is null`() {
        val record =
            makeTransaction(
                id = 2L,
                note = null,
                amount = BigDecimal("1200"),
                currencyId = usd.id,
                occurredAt = Instant.parse("2026-06-10T10:00:00Z"),
            )

        composeTestRule.setContent {
            MyMoneyTheme {
                Box(modifier = Modifier.width(400.dp)) {
                    CategoryRecordsInlineList(
                        records = listOf(record),
                        loading = false,
                        currencies = listOf(usd),
                        onRowClick = {},
                    )
                }
            }
        }

        composeTestRule
            .onNodeWithText(targetString(R.string.dashboard_inline_records_no_note))
            .assertIsDisplayed()
    }

    @Test
    fun `record row shows neutral placeholder when note is blank`() {
        val record =
            makeTransaction(
                id = 3L,
                note = "   ",
                amount = BigDecimal("800"),
                currencyId = usd.id,
                occurredAt = Instant.parse("2026-06-11T10:00:00Z"),
            )

        composeTestRule.setContent {
            MyMoneyTheme {
                Box(modifier = Modifier.width(400.dp)) {
                    CategoryRecordsInlineList(
                        records = listOf(record),
                        loading = false,
                        currencies = listOf(usd),
                        onRowClick = {},
                    )
                }
            }
        }

        composeTestRule
            .onNodeWithText(targetString(R.string.dashboard_inline_records_no_note))
            .assertIsDisplayed()
    }

    @Test
    fun `loading state shows loading indicator instead of records`() {
        val record =
            makeTransaction(
                id = 4L,
                note = "Should not appear",
                amount = BigDecimal("500"),
                currencyId = usd.id,
                occurredAt = Instant.parse("2026-06-12T10:00:00Z"),
            )

        composeTestRule.setContent {
            MyMoneyTheme {
                Box(modifier = Modifier.width(400.dp)) {
                    CategoryRecordsInlineList(
                        records = listOf(record),
                        loading = true,
                        currencies = listOf(usd),
                        onRowClick = {},
                    )
                }
            }
        }

        composeTestRule
            .onNodeWithTag(DASHBOARD_INLINE_RECORDS_LOADING_TAG)
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(targetString(R.string.dashboard_inline_records_loading))
            .assertIsDisplayed()
        composeTestRule
            .onAllNodes(hasText("Should not appear"))
            .assertCountEquals(0)
    }

    @Test
    fun `empty records list renders the container without crashing`() {
        composeTestRule.setContent {
            MyMoneyTheme {
                Box(modifier = Modifier.width(400.dp)) {
                    CategoryRecordsInlineList(
                        records = emptyList(),
                        loading = false,
                        currencies = listOf(usd),
                        onRowClick = {},
                    )
                }
            }
        }

        composeTestRule
            .onNodeWithTag(DASHBOARD_INLINE_RECORDS_TAG)
            .assertExists()
    }

    @Test
    fun `multiple records each show their own notes`() {
        val records =
            listOf(
                makeTransaction(id = 10L, note = "Lunch", amount = BigDecimal("400"), currencyId = usd.id),
                makeTransaction(id = 11L, note = "Dinner", amount = BigDecimal("700"), currencyId = usd.id),
                makeTransaction(id = 12L, note = "Snack", amount = BigDecimal("150"), currencyId = usd.id),
            )

        composeTestRule.setContent {
            MyMoneyTheme {
                Box(modifier = Modifier.width(400.dp)) {
                    CategoryRecordsInlineList(
                        records = records,
                        loading = false,
                        currencies = listOf(usd),
                        onRowClick = {},
                    )
                }
            }
        }

        composeTestRule.onNodeWithText("Lunch").assertIsDisplayed()
        composeTestRule.onNodeWithText("Dinner").assertIsDisplayed()
        composeTestRule.onNodeWithText("Snack").assertIsDisplayed()
    }

    private fun targetString(resourceId: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(resourceId)

    private fun makeTransaction(
        id: Long,
        note: String?,
        amount: BigDecimal,
        currencyId: Long,
        occurredAt: Instant = Instant.parse("2026-06-01T12:00:00Z"),
    ) = Transaction(
        id = id,
        kind = TransactionKind.Expense,
        amount = amount,
        currencyId = currencyId,
        accountId = 1L,
        categoryId = 1L,
        note = note,
        occurredAt = occurredAt,
        createdAt = occurredAt,
        updatedAt = occurredAt,
        isDeleted = false,
        toAccountId = null,
        toAmount = null,
        exchangeRate = null,
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
