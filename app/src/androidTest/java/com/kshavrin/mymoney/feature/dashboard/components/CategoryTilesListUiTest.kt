package com.kshavrin.mymoney.feature.dashboard.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kshavrin.mymoney.core.common.money.MoneyFormatter
import com.kshavrin.mymoney.core.domain.model.Currency
import com.kshavrin.mymoney.core.domain.model.Money
import com.kshavrin.mymoney.core.domain.model.Transaction
import com.kshavrin.mymoney.core.domain.model.TransactionKind
import com.kshavrin.mymoney.core.ui.theme.MyMoneyTheme
import com.kshavrin.mymoney.feature.dashboard.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class CategoryTilesListUiTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `tile shows label whole amount progress and emits tap`() {
        val tappedCategoryIds = mutableListOf<Long>()
        val tile =
            CategoryTileItem(
                categoryId = 7L,
                label = "Groceries",
                amount = Money(BigDecimal("15200.00"), usd),
                fraction = 0.32f,
                colorHex = "#7AC794",
                iconKey = "ic_cat_food",
            )

        composeTestRule.setContent {
            MyMoneyTheme {
                Box(modifier = Modifier.width(320.dp)) {
                    CategoryTilesList(
                        expenseTiles = listOf(tile),
                        expandedCategoryId = null,
                        expandedRecords = emptyList(),
                        expandedRecordsLoading = false,
                        currencies = listOf(usd),
                        onTileClick = { tappedCategoryIds += it },
                        onRecordRowClick = {},
                    )
                }
            }
        }

        val expectedAmount =
            MoneyFormatter.format(
                amount = tile.amount.amount,
                currencySymbol = tile.amount.currency.symbol,
                decimalDigits = 0,
                locale = targetLocale(),
                symbolPosition = MoneyFormatter.SymbolPosition.AFTER,
            )

        composeTestRule.onNodeWithText(tile.label).assertIsDisplayed()
        composeTestRule.onNodeWithText(expectedAmount).assertIsDisplayed()

        val tileBounds =
            composeTestRule
                .onNodeWithTag(tileTag(tile.categoryId))
                .assertIsDisplayed()
                .assertHasClickAction()
                .fetchSemanticsNode()
                .boundsInRoot
        val progressBounds =
            composeTestRule
                .onNodeWithTag(progressTag(tile.categoryId), useUnmergedTree = true)
                .assertIsDisplayed()
                .fetchSemanticsNode()
                .boundsInRoot

        assertEquals(tile.fraction, progressBounds.width / tileBounds.width, 0.02f)

        composeTestRule.onNodeWithTag(tileTag(tile.categoryId)).performClick()

        composeTestRule.runOnIdle {
            assertEquals(listOf(tile.categoryId), tappedCategoryIds)
        }
    }

    @Test
    fun `empty list shows localized empty state copy`() {
        composeTestRule.setContent {
            MyMoneyTheme {
                CategoryTilesList(
                    expenseTiles = emptyList(),
                    expandedCategoryId = null,
                    expandedRecords = emptyList(),
                    expandedRecordsLoading = false,
                    currencies = emptyList(),
                    onTileClick = {},
                    onRecordRowClick = {},
                )
            }
        }

        composeTestRule
            .onNodeWithText(targetString(R.string.dashboard_category_tiles_empty))
            .assertIsDisplayed()
    }

    @Test
    fun `inline records list appears under the expanded tile and is absent for others`() {
        val tile1 = makeTile(categoryId = 10L, label = "Food")
        val tile2 = makeTile(categoryId = 20L, label = "Transport")
        val record = makeTransaction(categoryId = 10L, note = "Lunch", amount = BigDecimal("500"))

        composeTestRule.setContent {
            MyMoneyTheme {
                Box(modifier = Modifier.width(400.dp)) {
                    CategoryTilesList(
                        expenseTiles = listOf(tile1, tile2),
                        expandedCategoryId = 10L,
                        expandedRecords = listOf(record),
                        expandedRecordsLoading = false,
                        currencies = listOf(usd),
                        onTileClick = {},
                        onRecordRowClick = {},
                    )
                }
            }
        }

        composeTestRule
            .onNodeWithTag(DASHBOARD_INLINE_RECORDS_TAG)
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText("Lunch")
            .assertIsDisplayed()
    }

    @Test
    fun `inline records list is absent when no category is expanded`() {
        val tile = makeTile(categoryId = 10L, label = "Food")

        composeTestRule.setContent {
            MyMoneyTheme {
                Box(modifier = Modifier.width(400.dp)) {
                    CategoryTilesList(
                        expenseTiles = listOf(tile),
                        expandedCategoryId = null,
                        expandedRecords = emptyList(),
                        expandedRecordsLoading = false,
                        currencies = listOf(usd),
                        onTileClick = {},
                        onRecordRowClick = {},
                    )
                }
            }
        }

        composeTestRule
            .onAllNodesWithTag(DASHBOARD_INLINE_RECORDS_TAG)
            .assertCountEquals(0)
    }

    @Test
    fun `loading indicator appears while expandedRecordsLoading is true`() {
        val tile = makeTile(categoryId = 10L, label = "Food")

        composeTestRule.setContent {
            MyMoneyTheme {
                Box(modifier = Modifier.width(400.dp)) {
                    CategoryTilesList(
                        expenseTiles = listOf(tile),
                        expandedCategoryId = 10L,
                        expandedRecords = emptyList(),
                        expandedRecordsLoading = true,
                        currencies = listOf(usd),
                        onTileClick = {},
                        onRecordRowClick = {},
                    )
                }
            }
        }

        composeTestRule
            .onNodeWithTag(DASHBOARD_INLINE_RECORDS_LOADING_TAG)
            .assertIsDisplayed()
    }

    private fun targetString(resourceId: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(resourceId)

    private fun targetLocale() =
        InstrumentationRegistry
            .getInstrumentation()
            .targetContext.resources.configuration.locales[0]

    private fun tileTag(categoryId: Long) = "category_tile_$categoryId"

    private fun progressTag(categoryId: Long) = "category_tile_progress_$categoryId"

    private fun makeTile(
        categoryId: Long,
        label: String,
    ) =
        CategoryTileItem(
            categoryId = categoryId,
            label = label,
            amount = Money(BigDecimal("1000.00"), usd),
            fraction = 0.20f,
            colorHex = "#4CAF50",
            iconKey = "ic_cat_food",
        )

    private fun makeTransaction(
        categoryId: Long,
        note: String?,
        amount: BigDecimal,
    ) =
        Transaction(
            id = 1L,
            kind = TransactionKind.Expense,
            amount = amount,
            currencyId = usd.id,
            accountId = 1L,
            categoryId = categoryId,
            note = note,
            occurredAt = Instant.parse("2026-06-01T12:00:00Z"),
            createdAt = Instant.parse("2026-06-01T12:00:00Z"),
            updatedAt = Instant.parse("2026-06-01T12:00:00Z"),
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
