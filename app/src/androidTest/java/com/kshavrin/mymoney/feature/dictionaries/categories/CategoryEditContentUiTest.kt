package com.kshavrin.mymoney.feature.dictionaries.categories

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
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
    fun nameKindColorAndSaveEmitTheirEvents() {
        val events = mutableListOf<CategoryEditEvent>()
        composeTestRule.setContent {
            MyMoneyTheme {
                var state by remember { mutableStateOf(CategoryEditState()) }
                CategoryEditContent(
                    state = state,
                    onEvent = { event ->
                        events += event
                        state = when (event) {
                            is CategoryEditEvent.NameChanged -> state.copy(name = event.value)
                            is CategoryEditEvent.KindChanged -> state.copy(kind = event.value)
                            is CategoryEditEvent.ColorChanged -> state.copy(colorHex = event.value)
                            else -> state
                        }
                    },
                )
            }
        }

        composeTestRule.onNodeWithText(targetString(R.string.dictionaries_field_name))
            .performTextInput("Coffee")
        composeTestRule.onNodeWithText(targetString(R.string.dictionaries_kind_income))
            .performScrollTo().performClick()
        // #9C5BB8 is the first palette swatch (always visible). performScrollTo on a
        // LazyVerticalGrid item deadlocks the nested scroll inside Column(verticalScroll)
        // (waitForIdle never settles), so click it directly.
        composeTestRule.onNodeWithContentDescription("#9C5BB8").performClick()
        composeTestRule.onNodeWithText(targetString(R.string.dictionaries_save))
            .performScrollTo().performClick()

        composeTestRule.runOnIdle {
            assertEquals(
                listOf(
                    CategoryEditEvent.NameChanged("Coffee"),
                    CategoryEditEvent.KindChanged(CategoryKind.Income),
                    CategoryEditEvent.ColorChanged("#9C5BB8"),
                    CategoryEditEvent.SaveClicked,
                ),
                events,
            )
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

        composeTestRule.onNodeWithText("ic_cat_food").performScrollTo().performClick()
        composeTestRule.onNodeWithText(targetString(R.string.dictionaries_choose_icon)).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("ic_cat_bills").performClick()

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
                    state = CategoryEditState(
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

    private fun targetString(resourceId: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(resourceId)
}
