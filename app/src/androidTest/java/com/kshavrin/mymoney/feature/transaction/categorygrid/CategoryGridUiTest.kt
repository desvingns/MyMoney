package com.kshavrin.mymoney.feature.transaction.categorygrid

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kshavrin.mymoney.core.domain.model.Category
import com.kshavrin.mymoney.core.domain.model.CategoryKind
import com.kshavrin.mymoney.core.ui.theme.MyMoneyTheme
import com.kshavrin.mymoney.feature.transaction.R
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CategoryGridUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `category cell emits clicked id`() {
        val captured = mutableListOf<Long>()

        setContent(
            categories = listOf(
                category(id = 1L, name = "Food", sortOrder = 0),
                category(id = 2L, name = "Bills", sortOrder = 1),
            ),
            onCategoryClick = { captured += it },
        )

        composeTestRule
            .onNodeWithContentDescription("Bills")
            .assertIsDisplayed()
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(listOf(2L), captured)
        }
    }

    @Test
    fun `add cell emits add click`() {
        var clicks = 0

        setContent(
            categories = listOf(category(id = 1L, name = "Food")),
            onAddClick = { clicks += 1 },
        )

        composeTestRule
            .onNodeWithTag(CATEGORY_GRID_ADD_CELL_TAG)
            .assertIsDisplayed()
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(1, clicks)
        }
    }

    @Test
    fun `add cell shows localized label`() {
        setContent(categories = listOf(category(id = 1L, name = "Food")))

        composeTestRule
            .onNodeWithText(targetString(R.string.add_category_cta), useUnmergedTree = true)
            .assertIsDisplayed()
    }

    @Test
    fun `empty grid still shows the add cell`() {
        var clicks = 0

        setContent(
            categories = emptyList(),
            onAddClick = { clicks += 1 },
        )

        composeTestRule
            .onNodeWithTag(CATEGORY_GRID_ADD_CELL_TAG)
            .assertIsDisplayed()
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(1, clicks)
        }
    }

    private fun setContent(
        categories: List<Category>,
        onCategoryClick: (Long) -> Unit = {},
        onAddClick: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            MyMoneyTheme {
                CategoryGrid(
                    categories = categories,
                    onCategoryClick = onCategoryClick,
                    onAddClick = onAddClick,
                )
            }
        }
    }

    private fun category(
        id: Long,
        name: String,
        sortOrder: Int = 0,
    ): Category = Category(
        id = id,
        name = name,
        kind = CategoryKind.Expense,
        iconKey = "ic_cat_food",
        colorHex = "#7AC794",
        sortOrder = sortOrder,
        isDefault = false,
        isArchived = false,
        createdAt = Instant.parse("2026-05-20T10:00:00Z"),
    )

    private fun targetString(resourceId: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(resourceId)
}
