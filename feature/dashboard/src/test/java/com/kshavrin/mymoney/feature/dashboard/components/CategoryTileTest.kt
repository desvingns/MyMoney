package com.kshavrin.mymoney.feature.dashboard.components

import android.content.Context
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.kshavrin.mymoney.core.domain.model.Currency
import com.kshavrin.mymoney.core.domain.model.Money
import com.kshavrin.mymoney.core.ui.theme.MyMoneyTheme
import com.kshavrin.mymoney.feature.dashboard.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
class CategoryTileTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `renders label and exposes a clickable category tile`() {
        val clickedIds = mutableListOf<Long>()
        setContent(onTileClick = { clickedIds += it })

        composeTestRule.onNodeWithText("Food").assertIsDisplayed()
        composeTestRule
            .onNodeWithTag("category_tile_7")
            .assertIsDisplayed()
            .assertHasClickAction()
            .performClick()

        assertEquals(listOf(7L), clickedIds)
    }

    @Test
    fun `clamps progress semantics to a valid percentage`() {
        setContent(tile = tile(fraction = 1.4f))
        val expectedDescription =
            ApplicationProvider
                .getApplicationContext<Context>()
                .getString(R.string.category_tile_spend_share, 100)

        composeTestRule
            .onNodeWithTag("category_tile_progress_7", useUnmergedTree = true)
            .assertExists()
            .assertContentDescriptionEquals(expectedDescription)
    }

    @Test
    fun `uses the category color as the default text color and budget alert is off`() {
        val tile = tile()

        assertEquals(tile.colorHex, tile.textColorHex)
        assertFalse(tile.hasBudgetAlert)
    }

    private fun setContent(
        tile: CategoryTileItem = tile(),
        onTileClick: (Long) -> Unit = {},
    ) {
        composeTestRule.setContent {
            MyMoneyTheme {
                CategoryTile(tile = tile, onTileClick = onTileClick)
            }
        }
    }

    private fun tile(fraction: Float = 0.75f): CategoryTileItem =
        CategoryTileItem(
            categoryId = 7L,
            label = "Food",
            amount = Money(BigDecimal("42.50"), usd),
            fraction = fraction,
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
