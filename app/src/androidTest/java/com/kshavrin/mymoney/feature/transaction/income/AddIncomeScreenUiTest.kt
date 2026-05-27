package com.kshavrin.mymoney.feature.transaction.income

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
import com.kshavrin.mymoney.core.designsystem.R as DesignSystemR
import com.kshavrin.mymoney.core.designsystem.keypad.Operator
import com.kshavrin.mymoney.core.ui.theme.MyMoneyTheme
import com.kshavrin.mymoney.feature.transaction.R
import java.math.BigDecimal
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
    fun `top bar controls emit back then swap events in order`() {
        val capturedEvents = mutableListOf<AddIncomeEvent>()

        composeTestRule.setContent {
            MyMoneyTheme {
                AddIncomeScreen(
                    state = AddIncomeState(),
                    onEvent = { event -> capturedEvents += event },
                )
            }
        }

        composeTestRule
            .onNodeWithContentDescription(targetString(R.string.back))
            .assertIsEnabled()
            .performClick()
        composeTestRule
            .onNodeWithContentDescription(targetString(R.string.swap_mode))
            .assertIsEnabled()
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(
                listOf(AddIncomeEvent.BackClicked, AddIncomeEvent.SwapMode),
                capturedEvents,
            )
        }
    }

    @Test
    fun `keypad backspace emits income backspace event`() {
        val capturedEvents = mutableListOf<AddIncomeEvent>()

        composeTestRule.setContent {
            MyMoneyTheme {
                AddIncomeScreen(
                    state = AddIncomeState(),
                    onEvent = { event -> capturedEvents += event },
                )
            }
        }

        composeTestRule
            .onNodeWithContentDescription(targetString(DesignSystemR.string.keypad_backspace_cd))
            .performScrollTo()
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(listOf(AddIncomeEvent.KeypadBackspace), capturedEvents)
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

    @Test
    fun `income choose category emits event when amount is positive`() {
        val capturedEvents = mutableListOf<AddIncomeEvent>()

        composeTestRule.setContent {
            MyMoneyTheme {
                AddIncomeScreen(
                    state = AddIncomeState(amount = BigDecimal("1"), amountInput = "1"),
                    onEvent = { event -> capturedEvents += event },
                )
            }
        }

        composeTestRule
            .onNodeWithText(targetString(R.string.choose_category_cta))
            .performScrollTo()
            .assertIsEnabled()
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(listOf(AddIncomeEvent.ChooseCategoryClicked), capturedEvents)
        }
    }

    private fun targetString(resourceId: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(resourceId)
}
