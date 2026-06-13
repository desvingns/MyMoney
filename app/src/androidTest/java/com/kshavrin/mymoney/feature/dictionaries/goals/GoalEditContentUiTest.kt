package com.kshavrin.mymoney.feature.dictionaries.goals

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kshavrin.mymoney.core.ui.theme.MyMoneyTheme
import com.kshavrin.mymoney.feature.dictionaries.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GoalEditContentUiTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private fun defaultState(canSave: Boolean = true) = GoalEditState(canSave = canSave)

    @Test
    fun `save button is displayed at bottom and emits SaveClicked when canSave is true`() {
        val events = mutableListOf<GoalEditEvent>()
        composeTestRule.setContent {
            MyMoneyTheme {
                GoalEditContent(
                    state = defaultState(canSave = true),
                    onEvent = { events += it },
                )
            }
        }

        composeTestRule
            .onNodeWithText(targetString(R.string.goal_save))
            .assertIsDisplayed()
            .assertIsEnabled()
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(listOf(GoalEditEvent.SaveClicked), events)
        }
    }

    @Test
    fun `save button is displayed but disabled when canSave is false`() {
        composeTestRule.setContent {
            MyMoneyTheme {
                GoalEditContent(
                    state = defaultState(canSave = false),
                    onEvent = {},
                )
            }
        }

        composeTestRule
            .onNodeWithText(targetString(R.string.goal_save))
            .assertIsDisplayed()
            .assertIsNotEnabled()
    }

    @Test
    fun `save button becomes enabled when canSave transitions from false to true`() {
        val events = mutableListOf<GoalEditEvent>()
        composeTestRule.setContent {
            MyMoneyTheme {
                GoalEditContent(
                    state = defaultState(canSave = true),
                    onEvent = { events += it },
                )
            }
        }

        composeTestRule
            .onNodeWithText(targetString(R.string.goal_save))
            .assertIsEnabled()
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(listOf(GoalEditEvent.SaveClicked), events)
        }
    }

    private fun targetString(resourceId: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(resourceId)
}
