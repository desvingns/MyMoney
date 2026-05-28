package com.kshavrin.mymoney.feature.transaction.picker

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
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
class CategoryPickerContentUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `back button emits picker back event`() {
        val capturedEvents = mutableListOf<CategoryPickerEvent>()

        composeTestRule.setContent {
            MyMoneyTheme {
                CategoryPickerContent(
                    state = CategoryPickerState(),
                    onEvent = { event -> capturedEvents += event },
                )
            }
        }

        composeTestRule
            .onNodeWithContentDescription(targetString(R.string.back))
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(listOf(CategoryPickerEvent.BackClicked), capturedEvents)
        }
    }

    @Test
    fun `add button emits picker add category event`() {
        val capturedEvents = mutableListOf<CategoryPickerEvent>()

        composeTestRule.setContent {
            MyMoneyTheme {
                CategoryPickerContent(
                    state = CategoryPickerState(),
                    onEvent = { event -> capturedEvents += event },
                )
            }
        }

        composeTestRule
            .onNodeWithContentDescription(targetString(R.string.add_category_cta))
            .assertIsDisplayed()
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(listOf(CategoryPickerEvent.AddCategoryClicked), capturedEvents)
        }
    }

    @Test
    fun `category cell keeps amount context and emits selection event`() {
        val capturedEvents = mutableListOf<CategoryPickerEvent>()

        composeTestRule.setContent {
            MyMoneyTheme {
                CategoryPickerContent(
                    state = CategoryPickerState(
                        categories = listOf(category(id = 10L, name = "Food")),
                        amountPreview = "8",
                    ),
                    onEvent = { event -> capturedEvents += event },
                )
            }
        }

        composeTestRule.onNodeWithText("8").assertIsDisplayed()
        composeTestRule.onNodeWithText("Food").assertIsDisplayed().performClick()

        composeTestRule.runOnIdle {
            assertEquals(listOf(CategoryPickerEvent.CategoryClicked(10L)), capturedEvents)
        }
    }

    private fun category(id: Long, name: String): Category = Category(
        id = id,
        name = name,
        kind = CategoryKind.Expense,
        iconKey = "ic_cat_food",
        colorHex = "#7AC794",
        sortOrder = 0,
        isDefault = false,
        isArchived = false,
        createdAt = Instant.parse("2026-05-27T00:00:00Z"),
    )

    private fun targetString(resourceId: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(resourceId)
}
