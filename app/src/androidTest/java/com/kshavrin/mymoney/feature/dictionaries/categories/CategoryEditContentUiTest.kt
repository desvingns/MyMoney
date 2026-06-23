package com.kshavrin.mymoney.feature.dictionaries.categories

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kshavrin.mymoney.core.domain.model.CategoryKind
import com.kshavrin.mymoney.core.ui.theme.MyMoneyTheme
import com.kshavrin.mymoney.feature.dictionaries.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CategoryEditContentUiTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun nameKindAndSaveEmitTheirEvents() {
        val events = mutableListOf<CategoryEditEvent>()
        composeTestRule.setContent {
            MyMoneyTheme {
                var state by remember { mutableStateOf(CategoryEditState()) }
                CategoryEditContent(
                    state = state,
                    onEvent = { event ->
                        events += event
                        state =
                            when (event) {
                                is CategoryEditEvent.NameChanged -> state.copy(name = event.value)
                                is CategoryEditEvent.KindChanged -> state.copy(kind = event.value)
                                else -> state
                            }
                    },
                )
            }
        }

        composeTestRule
            .onNodeWithText(targetString(R.string.dictionaries_field_name))
            .performTextInput("Coffee")
        composeTestRule
            .onNodeWithText(targetString(R.string.dictionaries_kind_income))
            .performScrollTo()
            .performClick()
        composeTestRule
            .onNodeWithText(targetString(R.string.dictionaries_save))
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(
                listOf(
                    CategoryEditEvent.NameChanged("Coffee"),
                    CategoryEditEvent.KindChanged(CategoryKind.Income),
                    CategoryEditEvent.SaveClicked,
                ),
                events,
            )
        }
    }

    @Test
    fun `color picker is not present on the screen`() {
        composeTestRule.setContent {
            MyMoneyTheme {
                CategoryEditContent(
                    state = CategoryEditState(),
                    onEvent = {},
                )
            }
        }

        composeTestRule
            .onNodeWithText(targetString(R.string.dictionaries_field_color))
            .assertDoesNotExist()
        composeTestRule
            .onNodeWithText(targetString(R.string.dictionaries_choose_color))
            .assertDoesNotExist()
    }

    @Test
    fun `changing icon updates preview name text color`() {
        val events = mutableListOf<CategoryEditEvent>()
        composeTestRule.setContent {
            MyMoneyTheme {
                var state by remember { mutableStateOf(CategoryEditState(iconKey = "ic_cat_food", name = "Preview")) }
                CategoryEditContent(
                    state = state,
                    onEvent = { event ->
                        events += event
                        state =
                            when (event) {
                                is CategoryEditEvent.IconChanged -> state.copy(iconKey = event.value)
                                else -> state
                            }
                    },
                )
            }
        }

        // Two nodes contain "Preview": index 0 = name TextField, index 1 = icon preview button.
        composeTestRule.onAllNodesWithText("Preview")[1].assertIsDisplayed()

        composeTestRule
            .onNodeWithText(targetString(R.string.dictionaries_field_icon))
            .performScrollTo()
        composeTestRule.onAllNodesWithText("Preview")[1].performScrollTo().performClick()
        composeTestRule.onNodeWithTag("ic_cat_car").assertIsDisplayed().performClick()

        composeTestRule.runOnIdle {
            assertEquals(listOf(CategoryEditEvent.IconChanged("ic_cat_car")), events)
            assertEquals("ic_cat_car", events.filterIsInstance<CategoryEditEvent.IconChanged>().last().value)
        }
    }

    @Test
    fun iconPickerOpensRendersAndEmitsIconChanged() {
        val events = mutableListOf<CategoryEditEvent>()
        composeTestRule.setContent {
            MyMoneyTheme {
                CategoryEditContent(
                    state = CategoryEditState(iconKey = "ic_cat_food"),
                    onEvent = { events += it },
                )
            }
        }

        composeTestRule.onNodeWithText(targetString(R.string.dictionaries_choose_icon)).performScrollTo().performClick()
        composeTestRule.onNodeWithTag("ic_cat_bills").assertIsDisplayed().performClick()

        composeTestRule.runOnIdle {
            assertEquals(listOf(CategoryEditEvent.IconChanged("ic_cat_bills")), events)
        }
    }

    @Test
    fun inlineErrorAndBlockedDeleteDialogRenderAndDismiss() {
        val events = mutableListOf<CategoryEditEvent>()
        composeTestRule.setContent {
            MyMoneyTheme {
                CategoryEditContent(
                    state =
                        CategoryEditState(
                            isCreateMode = false,
                            name = "Food",
                            errorMessage = "name_required_or_too_long",
                            blockedDeleteCount = 3,
                        ),
                    onEvent = { events += it },
                )
            }
        }

        composeTestRule.onNodeWithText(targetString(R.string.dictionaries_error_name_required)).assertIsDisplayed()
        composeTestRule.onNodeWithText(targetString(R.string.dictionaries_blocked_delete_title)).assertIsDisplayed()
        composeTestRule.onNodeWithText(targetString(R.string.dictionaries_ok)).performClick()

        composeTestRule.runOnIdle {
            assertEquals(listOf(CategoryEditEvent.BlockedDeleteDismissed), events)
        }
    }

    @Test
    fun `save button is displayed at bottom and emits SaveClicked in create mode`() {
        val events = mutableListOf<CategoryEditEvent>()
        composeTestRule.setContent {
            MyMoneyTheme {
                CategoryEditContent(
                    state = CategoryEditState(isCreateMode = true),
                    onEvent = { events += it },
                )
            }
        }

        composeTestRule
            .onNodeWithText(targetString(R.string.dictionaries_save))
            .assertIsDisplayed()
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(listOf(CategoryEditEvent.SaveClicked), events)
        }
    }

    @Test
    fun `delete button is present in body in edit mode and save is also displayed`() {
        val events = mutableListOf<CategoryEditEvent>()
        composeTestRule.setContent {
            MyMoneyTheme {
                CategoryEditContent(
                    state = CategoryEditState(isCreateMode = false, name = "Food"),
                    onEvent = { events += it },
                )
            }
        }

        composeTestRule
            .onNodeWithText(targetString(R.string.dictionaries_save))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(targetString(R.string.dictionaries_delete))
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(listOf(CategoryEditEvent.DeleteClicked), events)
        }
    }

    private fun targetString(resourceId: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(resourceId)
}
