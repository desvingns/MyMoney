package com.kshavrin.mymoney.feature.transaction.expense

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kshavrin.mymoney.core.designsystem.keypad.Operator
import com.kshavrin.mymoney.core.ui.theme.MyMoneyTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AddExpenseScreenUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `keypad calculation buttons emit expense input events in order`() {
        val capturedEvents = mutableListOf<AddExpenseEvent>()

        composeTestRule.setContent {
            MyMoneyTheme {
                AddExpenseScreen(
                    state = AddExpenseState(),
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
                    AddExpenseEvent.KeypadDigit(1),
                    AddExpenseEvent.KeypadDigit(2),
                    AddExpenseEvent.KeypadOperator(Operator.Plus),
                    AddExpenseEvent.KeypadDigit(3),
                    AddExpenseEvent.KeypadEquals,
                ),
                capturedEvents,
            )
        }
    }
}
