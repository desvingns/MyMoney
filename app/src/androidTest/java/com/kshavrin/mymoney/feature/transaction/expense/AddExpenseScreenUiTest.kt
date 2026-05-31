package com.kshavrin.mymoney.feature.transaction.expense

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kshavrin.mymoney.core.designsystem.R as DesignSystemR
import com.kshavrin.mymoney.core.designsystem.keypad.Operator
import com.kshavrin.mymoney.core.domain.model.Category
import com.kshavrin.mymoney.core.domain.model.CategoryKind
import com.kshavrin.mymoney.core.ui.theme.MyMoneyTheme
import com.kshavrin.mymoney.feature.transaction.R
import com.kshavrin.mymoney.feature.transaction.categorygrid.CATEGORY_GRID_ADD_CELL_TAG
import com.kshavrin.mymoney.feature.transaction.categorygrid.CATEGORY_GRID_TAG
import java.time.Instant
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
    fun `default form embeds category grid and keeps keypad hidden`() {
        composeTestRule.setContent {
            MyMoneyTheme {
                AddExpenseScreen(
                    state = AddExpenseState(categories = listOf(category(id = 10L, name = "Food"))),
                    onEvent = {},
                )
            }
        }

        composeTestRule
            .onNodeWithTag(CATEGORY_GRID_TAG)
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithContentDescription("Food")
            .assertIsDisplayed()
        composeTestRule
            .onAllNodes(hasText("7") and hasClickAction())
            .assertCountEquals(0)
    }

    @Test
    fun `amount field emits amount clicked event`() {
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
            .onNode(hasText("0") and hasClickAction())
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(listOf(AddExpenseEvent.AmountClicked), capturedEvents)
        }
    }

    @Test
    fun `keypad calculation buttons emit expense input events in order`() {
        val capturedEvents = mutableListOf<AddExpenseEvent>()

        composeTestRule.setContent {
            MyMoneyTheme {
                AddExpenseScreen(
                    state = AddExpenseState(keypadVisible = true),
                    onEvent = { event -> capturedEvents += event },
                )
            }
        }

        listOf("1", "2", "+", "3", "=").forEach { label ->
            composeTestRule
                .onNode(hasText(label) and hasClickAction())
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
    fun `keypad operator buttons emit the full expense keypad contract`() {
        val capturedEvents = mutableListOf<AddExpenseEvent>()

        composeTestRule.setContent {
            MyMoneyTheme {
                AddExpenseScreen(
                    state = AddExpenseState(keypadVisible = true),
                    onEvent = { event -> capturedEvents += event },
                )
            }
        }

        listOf(".", "\u2212", "\u00D7", "\u00F7").forEach { label ->
            composeTestRule
                .onNode(hasText(label) and hasClickAction())
                .performClick()
        }

        composeTestRule.runOnIdle {
            assertEquals(
                listOf(
                    AddExpenseEvent.KeypadDot,
                    AddExpenseEvent.KeypadOperator(Operator.Minus),
                    AddExpenseEvent.KeypadOperator(Operator.Multiply),
                    AddExpenseEvent.KeypadOperator(Operator.Divide),
                ),
                capturedEvents,
            )
        }
    }

    @Test
    fun `keypad backspace emits expense backspace event`() {
        val capturedEvents = mutableListOf<AddExpenseEvent>()

        composeTestRule.setContent {
            MyMoneyTheme {
                AddExpenseScreen(
                    state = AddExpenseState(keypadVisible = true),
                    onEvent = { event -> capturedEvents += event },
                )
            }
        }

        composeTestRule
            .onNodeWithContentDescription(targetString(DesignSystemR.string.keypad_backspace_cd))
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(listOf(AddExpenseEvent.KeypadBackspace), capturedEvents)
        }
    }

    @Test
    fun `dismissing keypad sheet emits expense keypad dismissed event`() {
        val capturedEvents = mutableListOf<AddExpenseEvent>()

        composeTestRule.setContent {
            MyMoneyTheme {
                AddExpenseScreen(
                    state = AddExpenseState(keypadVisible = true),
                    onEvent = { event -> capturedEvents += event },
                )
            }
        }

        composeTestRule
            .onNode(hasText("1") and hasClickAction())
            .assertIsDisplayed()
        pressBack()

        composeTestRule.runOnIdle {
            assertEquals(listOf(AddExpenseEvent.KeypadDismissed), capturedEvents)
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
    fun `entering a note emits expense note changed event`() {
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
            .onNodeWithText(targetString(DesignSystemR.string.amountfield_note_hint))
            .performTextInput("Dinner")

        composeTestRule.runOnIdle {
            assertEquals(listOf(AddExpenseEvent.NoteChanged("Dinner")), capturedEvents)
        }
    }

    @Test
    fun `category cell emits picked event`() {
        val capturedEvents = mutableListOf<AddExpenseEvent>()

        composeTestRule.setContent {
            MyMoneyTheme {
                AddExpenseScreen(
                    state = AddExpenseState(categories = listOf(category(id = 10L, name = "Food"))),
                    onEvent = { event -> capturedEvents += event },
                )
            }
        }

        composeTestRule
            .onNodeWithContentDescription("Food")
            .assertIsDisplayed()
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(listOf(AddExpenseEvent.CategoryPicked(10L)), capturedEvents)
        }
    }

    @Test
    fun `add category cell emits add event`() {
        val capturedEvents = mutableListOf<AddExpenseEvent>()

        composeTestRule.setContent {
            MyMoneyTheme {
                AddExpenseScreen(
                    state = AddExpenseState(categories = listOf(category(id = 10L, name = "Food"))),
                    onEvent = { event -> capturedEvents += event },
                )
            }
        }

        composeTestRule
            .onNodeWithTag(CATEGORY_GRID_ADD_CELL_TAG)
            .assertIsDisplayed()
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(listOf(AddExpenseEvent.AddCategoryClicked), capturedEvents)
        }
    }

    private fun category(id: Long, name: String): Category = Category(
        id = id,
        name = name,
        kind = CategoryKind.Expense,
        iconKey = "ic_cat_food",
        colorHex = "#7AC794",
        sortOrder = 0,
        isDefault = false,
        isArchived = false,
        createdAt = Instant.parse("2026-05-27T00:00:00Z"),
    )

    private fun targetString(resourceId: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(resourceId)

    private fun dateLabel(date: LocalDate): String {
        val locale = InstrumentationRegistry.getInstrumentation()
            .targetContext.resources.configuration.locales[0]
        return date.format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", locale))
    }
}
