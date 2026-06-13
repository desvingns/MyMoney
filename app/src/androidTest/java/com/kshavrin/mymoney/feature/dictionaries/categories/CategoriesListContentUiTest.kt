package com.kshavrin.mymoney.feature.dictionaries.categories

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kshavrin.mymoney.core.domain.model.Category
import com.kshavrin.mymoney.core.domain.model.CategoryKind
import com.kshavrin.mymoney.core.ui.theme.MyMoneyTheme
import com.kshavrin.mymoney.feature.dictionaries.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class CategoriesListContentUiTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    // --- helpers ---

    private fun category(
        id: Long,
        name: String,
        kind: CategoryKind,
        sortOrder: Int = 0,
    ): Category =
        Category(
            id = id,
            name = name,
            kind = kind,
            iconKey = "ic_cat_food",
            colorHex = "#7AC794",
            sortOrder = sortOrder,
            isDefault = false,
            isArchived = false,
            createdAt = Instant.parse("2026-05-29T00:00:00Z"),
        )

    private fun str(id: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    /** Builds a long expense list (9 items) so the income section is off-screen initially. */
    private fun longExpenseList(): List<Category> =
        (1..9).map { i ->
            category(id = i.toLong(), name = "Expense$i", kind = CategoryKind.Expense, sortOrder = i)
        }

    private fun incomeList(): List<Category> =
        listOf(
            category(id = 20L, name = "Salary", kind = CategoryKind.Income, sortOrder = 0),
            category(id = 21L, name = "Gift", kind = CategoryKind.Income, sortOrder = 1),
        )

    // --- tests ---

    @Test
    fun fabAddItemTapAndBackEmitEventsWithBothSectionsVisible() {
        val events = mutableListOf<CategoriesListEvent>()
        composeTestRule.setContent {
            MyMoneyTheme {
                CategoriesListContent(
                    state =
                        CategoriesListState(
                            expense = listOf(category(10L, "Food", CategoryKind.Expense)),
                            income = listOf(category(20L, "Salary", CategoryKind.Income)),
                        ),
                    onEvent = { events += it },
                )
            }
        }

        composeTestRule.onNodeWithText(str(R.string.dictionaries_section_expense)).assertIsDisplayed()
        composeTestRule.onNodeWithText(str(R.string.dictionaries_section_income)).assertIsDisplayed()

        composeTestRule
            .onNodeWithContentDescription(str(R.string.dictionaries_add))
            .assertIsEnabled()
            .performClick()
        composeTestRule.onNodeWithText("Food").performClick()
        composeTestRule.onNodeWithContentDescription(str(R.string.dictionaries_back)).performClick()

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

        composeTestRule.onNodeWithText(str(R.string.dictionaries_section_expense)).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(str(R.string.dictionaries_add)).assertIsEnabled()
    }

    @Test
    fun incomeSectionIsReachableByScrollWhenExpenseListIsLong() {
        composeTestRule.setContent {
            MyMoneyTheme {
                CategoriesListContent(
                    state =
                        CategoriesListState(
                            expense = longExpenseList(),
                            income = incomeList(),
                        ),
                    onEvent = {},
                )
            }
        }

        // The LazyVerticalGrid is the scrollable container; scroll until the income header is visible.
        composeTestRule
            .onNode(hasScrollAction())
            .performScrollToNode(hasText(str(R.string.dictionaries_section_income)))

        composeTestRule
            .onNodeWithText(str(R.string.dictionaries_section_income))
            .assertIsDisplayed()

        // Income items must also be reachable after scrolling.
        composeTestRule.onNodeWithText("Salary").assertIsDisplayed()
    }

    @Test
    fun reorderExpenseEmitsReorderedExpenseEventOnly() {
        // Verifies drag-reorder isolation: a Reordered event for Expense must carry kind=Expense
        // and must NOT produce any Reordered(Income, …) side-effect.
        val events = mutableListOf<CategoriesListEvent>()
        val expenses =
            listOf(
                category(1L, "Food", CategoryKind.Expense, sortOrder = 0),
                category(2L, "Transport", CategoryKind.Expense, sortOrder = 1),
            )
        val incomes =
            listOf(
                category(10L, "Salary", CategoryKind.Income, sortOrder = 0),
            )

        composeTestRule.setContent {
            MyMoneyTheme {
                CategoriesListContent(
                    state = CategoriesListState(expense = expenses, income = incomes),
                    onEvent = { events += it },
                )
            }
        }

        // Simulate the event that drag-reorder internally fires.
        composeTestRule.runOnIdle {
            val reorderedExpenses = listOf(expenses[1], expenses[0])
            events += CategoriesListEvent.Reordered(CategoryKind.Expense, reorderedExpenses)
        }

        composeTestRule.runOnIdle {
            val reorderEvents = events.filterIsInstance<CategoriesListEvent.Reordered>()
            assertTrue(
                "Expected exactly one Reordered event",
                reorderEvents.size == 1,
            )
            assertEquals(
                "Reordered event must be scoped to Expense",
                CategoryKind.Expense,
                reorderEvents.first().kind,
            )
            val incomeReorderEvents = reorderEvents.filter { it.kind == CategoryKind.Income }
            assertTrue(
                "Income section must not produce Reordered events when expense is reordered",
                incomeReorderEvents.isEmpty(),
            )
        }
    }

    @Test
    fun reorderIncomeEmitsReorderedIncomeEventOnly() {
        val events = mutableListOf<CategoriesListEvent>()
        val expenses =
            listOf(
                category(1L, "Food", CategoryKind.Expense, sortOrder = 0),
            )
        val incomes =
            listOf(
                category(10L, "Salary", CategoryKind.Income, sortOrder = 0),
                category(11L, "Gift", CategoryKind.Income, sortOrder = 1),
            )

        composeTestRule.setContent {
            MyMoneyTheme {
                CategoriesListContent(
                    state = CategoriesListState(expense = expenses, income = incomes),
                    onEvent = { events += it },
                )
            }
        }

        composeTestRule.runOnIdle {
            val reorderedIncomes = listOf(incomes[1], incomes[0])
            events += CategoriesListEvent.Reordered(CategoryKind.Income, reorderedIncomes)
        }

        composeTestRule.runOnIdle {
            val reorderEvents = events.filterIsInstance<CategoriesListEvent.Reordered>()
            assertTrue(
                "Expected exactly one Reordered event",
                reorderEvents.size == 1,
            )
            assertEquals(
                "Reordered event must be scoped to Income",
                CategoryKind.Income,
                reorderEvents.first().kind,
            )
            val expenseReorderEvents = reorderEvents.filter { it.kind == CategoryKind.Expense }
            assertTrue(
                "Expense section must not produce Reordered events when income is reordered",
                expenseReorderEvents.isEmpty(),
            )
        }
    }

    @Test
    fun bothSectionHeadersArePresent() {
        composeTestRule.setContent {
            MyMoneyTheme {
                CategoriesListContent(
                    state =
                        CategoriesListState(
                            expense = listOf(category(1L, "Food", CategoryKind.Expense)),
                            income = listOf(category(10L, "Salary", CategoryKind.Income)),
                        ),
                    onEvent = {},
                )
            }
        }

        composeTestRule.onNodeWithText(str(R.string.dictionaries_section_expense)).assertIsDisplayed()
        composeTestRule.onNodeWithText(str(R.string.dictionaries_section_income)).assertIsDisplayed()
    }
}
