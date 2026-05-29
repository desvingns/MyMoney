package com.kshavrin.mymoney.feature.dictionaries.categories

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kshavrin.mymoney.core.domain.model.Category
import com.kshavrin.mymoney.core.domain.model.CategoryKind
import com.kshavrin.mymoney.core.ui.theme.MyMoneyTheme
import com.kshavrin.mymoney.feature.dictionaries.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class CategoriesListContentUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun fabAddRowTapAndBackEmitEventsWithBothSectionsVisible() {
        val events = mutableListOf<CategoriesListEvent>()
        composeTestRule.setContent {
            MyMoneyTheme {
                CategoriesListContent(
                    state = CategoriesListState(
                        expense = listOf(category(10L, "Food", CategoryKind.Expense)),
                        income = listOf(category(20L, "Salary", CategoryKind.Income)),
                    ),
                    onEvent = { events += it },
                )
            }
        }

        composeTestRule.onNodeWithText(targetString(R.string.dictionaries_section_expense)).assertIsDisplayed()
        composeTestRule.onNodeWithText(targetString(R.string.dictionaries_section_income)).assertIsDisplayed()

        composeTestRule.onNodeWithContentDescription(targetString(R.string.dictionaries_add))
            .assertIsEnabled().performClick()
        composeTestRule.onNodeWithText("Food").performClick()
        composeTestRule.onNodeWithContentDescription(targetString(R.string.dictionaries_back)).performClick()

        composeTestRule.runOnIdle {
            assertEquals(
                listOf(
                    CategoriesListEvent.AddClicked,
                    CategoriesListEvent.ItemClicked(10L),
                    CategoriesListEvent.BackClicked,
                ),
                events,
            )
        }
    }

    @Test
    fun emptyCategoryListsRenderWithEnabledAddFab() {
        composeTestRule.setContent {
            MyMoneyTheme {
                CategoriesListContent(state = CategoriesListState(), onEvent = {})
            }
        }

        composeTestRule.onNodeWithText(targetString(R.string.dictionaries_section_expense)).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(targetString(R.string.dictionaries_add)).assertIsEnabled()
    }

    private fun category(id: Long, name: String, kind: CategoryKind): Category = Category(
        id = id,
        name = name,
        kind = kind,
        iconKey = "ic_cat_food",
        colorHex = "#7AC794",
        sortOrder = 0,
        isDefault = false,
        isArchived = false,
        createdAt = Instant.parse("2026-05-29T00:00:00Z"),
    )

    private fun targetString(resourceId: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(resourceId)
}
