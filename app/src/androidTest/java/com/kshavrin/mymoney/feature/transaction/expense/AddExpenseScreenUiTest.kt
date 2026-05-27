package com.kshavrin.mymoney.feature.transaction.expense

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
import java.time.LocalDate
import java.time.format.DateTimeFormatter
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

    @Test
    fun `top bar controls emit back then swap events in order`() {
        val capturedEvents = mutableListOf<AddExpenseEvent>()

        composeTestRule.setContent {
            MyMoneyTheme {
                AddExpenseScreen(
                    state = AddExpenseState(),
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
                listOf(AddExpenseEvent.BackClicked, AddExpenseEvent.SwapMode),
                capturedEvents,
            )
        }
    }

    @Test
    fun `picking a different date emits only the changed date event`() {
        val capturedEvents = mutableListOf<AddExpenseEvent>()
        val initialDate = LocalDate.of(2026, 5, 17)
        val chosenDate = initialDate.plusDays(1)

        composeTestRule.setContent {
            MyMoneyTheme {
                AddExpenseScreen(
                    state = AddExpenseState(occurredAt = initialDate),
                    onEvent = { event -> capturedEvents += event },
                )
            }
        }

        composeTestRule
            .onNodeWithContentDescription(targetString(DesignSystemR.string.amountfield_pick_date_cd))
            .performClick()
        composeTestRule.onNodeWithText(dateLabel(chosenDate)).performClick()
        composeTestRule.onNodeWithText(targetString(R.string.pick_date)).performClick()

        composeTestRule.runOnIdle {
            assertEquals(listOf(AddExpenseEvent.DateChanged(chosenDate)), capturedEvents)
        }
    }

    @Test
    fun `choose category stays disabled while amount is zero`() {
        composeTestRule.setContent {
            MyMoneyTheme {
                AddExpenseScreen(
                    state = AddExpenseState(),
                    onEvent = {},
                )
            }
        }

        composeTestRule
            .onNodeWithText(targetString(R.string.choose_category_cta))
            .performScrollTo()
            .assertIsNotEnabled()
    }

    @Test
    fun `choose category emits event when amount is positive`() {
        val capturedEvents = mutableListOf<AddExpenseEvent>()

        composeTestRule.setContent {
            MyMoneyTheme {
                AddExpenseScreen(
                    state = AddExpenseState(amount = BigDecimal("1"), amountInput = "1"),
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
            assertEquals(listOf(AddExpenseEvent.ChooseCategoryClicked), capturedEvents)
        }
    }

    private fun targetString(resourceId: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(resourceId)

    private fun dateLabel(date: LocalDate): String {
        val locale = InstrumentationRegistry.getInstrumentation()
            .targetContext.resources.configuration.locales[0]
        return date.format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", locale))
    }
}
