package com.kshavrin.mymoney.feature.transaction.income

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kshavrin.mymoney.core.designsystem.keypad.Operator
import com.kshavrin.mymoney.core.ui.theme.MyMoneyTheme
import com.kshavrin.mymoney.feature.transaction.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AddIncomeScreenUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `keypad calculation buttons emit income input events in order`() {
        val capturedEvents = mutableListOf<AddIncomeEvent>()

        composeTestRule.setContent {
            MyMoneyTheme {
                AddIncomeScreen(
                    state = AddIncomeState(),
                    onEvent = { event -> capturedEvents += event },
                )
            }
        }

        listOf("1", "2", "+", "3", "=").forEach { label ->
            composeTestRule
                .onNodeWithText(label)
                .performScrollTo()
                .performClick()
        }

        composeTestRule.runOnIdle {
            assertEquals(
                listOf(
                    AddIncomeEvent.KeypadDigit(1),
                    AddIncomeEvent.KeypadDigit(2),
                    AddIncomeEvent.KeypadOperator(Operator.Plus),
                    AddIncomeEvent.KeypadDigit(3),
                    AddIncomeEvent.KeypadEquals,
                ),
                capturedEvents,
            )
        }
    }

    @Test
    fun `income choose category stays disabled while amount is zero`() {
        composeTestRule.setContent {
            MyMoneyTheme {
                AddIncomeScreen(
                    state = AddIncomeState(),
                    onEvent = {},
                )
            }
        }

        composeTestRule
            .onNodeWithText(targetString(R.string.new_income_title))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(targetString(R.string.choose_category_cta))
            .performScrollTo()
            .assertIsNotEnabled()
    }

    private fun targetString(resourceId: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(resourceId)
}
