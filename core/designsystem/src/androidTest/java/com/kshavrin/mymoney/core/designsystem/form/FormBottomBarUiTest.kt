package com.kshavrin.mymoney.core.designsystem.form

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kshavrin.mymoney.core.ui.theme.MyMoneyTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FormBottomBarUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `shows save text and invokes onSave when enabled`() {
        var saves = 0

        composeTestRule.setContent {
            MyMoneyTheme {
                FormBottomBar(
                    text = SAVE_TEXT,
                    onSave = { saves += 1 },
                )
            }
        }

        composeTestRule
            .onNodeWithText(SAVE_TEXT)
            .assertIsDisplayed()
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(1, saves)
        }
    }

    @Test
    fun `disabled save button does not invoke onSave`() {
        var saves = 0

        composeTestRule.setContent {
            MyMoneyTheme {
                FormBottomBar(
                    text = SAVE_TEXT,
                    enabled = false,
                    onSave = { saves += 1 },
                )
            }
        }

        composeTestRule
            .onNodeWithText(SAVE_TEXT)
            .assertIsDisplayed()
            .assertIsNotEnabled()

        composeTestRule.runOnIdle {
            assertEquals(0, saves)
        }
    }

    private companion object {
        const val SAVE_TEXT = "Save"
    }
}
