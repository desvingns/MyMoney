package com.kshavrin.mymoney.feature.dashboard.components

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.kshavrin.mymoney.core.domain.model.Currency
import com.kshavrin.mymoney.core.domain.model.Money
import com.kshavrin.mymoney.core.ui.theme.MyMoneyTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.math.BigDecimal

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CategoryTilesListTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `empty list shows the empty-state message`() {
        setContent(emptyList())

        composeTestRule.onNodeWithText("No expenses this period").assertIsDisplayed()
        composeTestRule.onNodeWithTag("category_tile_1").assertDoesNotExist()
    }

    @Test
    fun `renders every tile and forwards the clicked category id`() {
        val clickedIds = mutableListOf<Long>()
        setContent(listOf(tile(1L, "Food"), tile(2L, "Transport"))) { clickedIds += it }

        composeTestRule.onAllNodesWithTag("category_tile_1").assertCountEquals(1)
        composeTestRule.onAllNodesWithTag("category_tile_2").assertCountEquals(1)
        composeTestRule.onNodeWithTag("category_tile_2").performClick()

        assertEquals(listOf(2L), clickedIds)
    }

    private fun setContent(
        tiles: List<CategoryTileItem>,
        onTileClick: (Long) -> Unit = {},
    ) {
        composeTestRule.setContent {
            MyMoneyTheme {
                CategoryTilesList(expenseTiles = tiles, onTileClick = onTileClick)
            }
        }
    }

    private fun tile(
        categoryId: Long,
        label: String,
    ): CategoryTileItem =
        CategoryTileItem(
            categoryId = categoryId,
            label = label,
            amount = Money(BigDecimal("10.00"), usd),
            fraction = 0.5f,
            colorHex = "#E07AAE",
            iconKey = "ic_cat_food",
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
