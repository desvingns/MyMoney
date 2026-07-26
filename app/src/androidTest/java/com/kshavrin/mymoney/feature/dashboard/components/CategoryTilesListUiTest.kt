package com.kshavrin.mymoney.feature.dashboard.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kshavrin.mymoney.core.common.money.MoneyFormatter
import com.kshavrin.mymoney.core.domain.model.Currency
import com.kshavrin.mymoney.core.domain.model.Money
import com.kshavrin.mymoney.core.ui.theme.MyMoneyTheme
import com.kshavrin.mymoney.feature.dashboard.R
import com.kshavrin.mymoney.test.assertTouchHeightIsAtLeast
import com.kshavrin.mymoney.test.assertTouchWidthIsAtLeast
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal

@RunWith(AndroidJUnit4::class)
class CategoryTilesListUiTest {
    @get:Rule
    val composeTestRule = createComposeRule().apply { enableAccessibilityChecks() }

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
        val secondTile =
            tile.copy(
                categoryId = 8L,
                label = "Transport",
                amount = Money(BigDecimal("9800.00"), usd),
            )

        composeTestRule.setContent {
            MyMoneyTheme {
                Box(modifier = Modifier.width(320.dp)) {
                    CategoryTilesList(
                        expenseTiles = listOf(tile, secondTile),
                        onTileClick = { tappedCategoryIds += it },
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

        listOf(tile, secondTile).forEach { item ->
            composeTestRule
                .onNodeWithTag(tileTag(item.categoryId))
                .assertIsDisplayed()
                .assertHasClickAction()
                .assertTouchWidthIsAtLeast(48.dp)
                .assertTouchHeightIsAtLeast(48.dp)
        }

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
                    onTileClick = {},
                )
            }
        }

        composeTestRule
            .onNodeWithText(targetString(R.string.dashboard_category_tiles_empty))
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
