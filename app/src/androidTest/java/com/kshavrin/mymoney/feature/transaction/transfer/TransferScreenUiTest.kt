package com.kshavrin.mymoney.feature.transaction.transfer

import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kshavrin.mymoney.core.ui.theme.MyMoneyTheme
import com.kshavrin.mymoney.feature.transaction.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TransferScreenUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `back button emits transfer back event`() {
        val capturedEvents = mutableListOf<TransferEvent>()

        composeTestRule.setContent {
            MyMoneyTheme {
                TransferScreen(
                    state = TransferState(),
                    onEvent = { event -> capturedEvents += event },
                )
            }
        }

        composeTestRule.onNodeWithText(targetString(R.string.new_transfer_title)).assertIsDisplayed()
        composeTestRule
            .onNodeWithContentDescription(targetString(R.string.back))
            .assertIsEnabled()
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(listOf(TransferEvent.BackClicked), capturedEvents)
        }
    }

    @Test
    fun `save button stays disabled until transfer is valid`() {
        composeTestRule.setContent {
            MyMoneyTheme {
                TransferScreen(
                    state = TransferState(),
                    onEvent = {},
                )
            }
        }

        composeTestRule
            .onNodeWithContentDescription(targetString(R.string.currency_rate_save))
            .assertIsNotEnabled()
    }

    @Test
    fun `amount press reveals keypad and digit emits transfer event`() {
        val capturedEvents = mutableListOf<TransferEvent>()

        composeTestRule.setContent {
            MyMoneyTheme {
                TransferScreen(
                    state = TransferState(),
                    onEvent = { event -> capturedEvents += event },
                )
            }
        }

        composeTestRule.onNodeWithText("1").assertDoesNotExist()
        composeTestRule.onNodeWithText("0").performClick()
        composeTestRule.onNodeWithText("1").performScrollTo().performClick()

        composeTestRule.runOnIdle {
            assertEquals(listOf(TransferEvent.KeypadDigit(1)), capturedEvents)
        }
    }

    private fun targetString(resourceId: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(resourceId)
}
