package com.kshavrin.mymoney.feature.transaction.expense

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kshavrin.mymoney.core.designsystem.form.CATEGORY_GRID_ADD_CELL_TAG
import com.kshavrin.mymoney.core.designsystem.form.CATEGORY_GRID_TAG
import com.kshavrin.mymoney.core.designsystem.keypad.Operator
import com.kshavrin.mymoney.core.domain.model.Category
import com.kshavrin.mymoney.core.domain.model.CategoryKind
import com.kshavrin.mymoney.core.ui.theme.MyMoneyTheme
import com.kshavrin.mymoney.feature.transaction.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import com.kshavrin.mymoney.core.designsystem.R as DesignSystemR

@RunWith(AndroidJUnit4::class)
class AddExpenseScreenUiTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `default amount step shows keypad and hides category grid`() {
        composeTestRule.setContent {
            MyMoneyTheme {
                AddExpenseScreen(
                    state = AddExpenseState(categories = listOf(category(id = 10L, name = "Food"))),
                    onEvent = {},
                )
            }
        }

        composeTestRule
            .onAllNodes(hasText("7") and hasClickAction())
            .assertCountEquals(1)
        composeTestRule
            .onNodeWithText(targetString(DesignSystemR.string.amountfield_note_hint))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(targetString(DesignSystemR.string.transaction_form_choose_category_button))
            .assertIsDisplayed()
            .assertIsNotEnabled()
        composeTestRule
            .onNodeWithTag(CATEGORY_GRID_ADD_CELL_TAG)
            .assertDoesNotExist()
    }

    @Test
    fun `choose category emits select category event`() {
        val capturedEvents = mutableListOf<AddExpenseEvent>()

        composeTestRule.setContent {
            MyMoneyTheme {
                AddExpenseScreen(
                    state = AddExpenseState(amount = BigDecimal.ONE, amountInput = "1"),
                    onEvent = { event -> capturedEvents += event },
                )
            }
        }

        composeTestRule
            .onNodeWithText(targetString(DesignSystemR.string.transaction_form_choose_category_button))
            .assertIsEnabled()
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(listOf(AddExpenseEvent.SelectCategoryClicked), capturedEvents)
        }
    }

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
                    state = AddExpenseState(),
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
                    state = AddExpenseState(),
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
    fun `category step amount field emits back to amount event`() {
        val capturedEvents = mutableListOf<AddExpenseEvent>()

        composeTestRule.setContent {
            MyMoneyTheme {
                AddExpenseScreen(
                    state =
                        AddExpenseState(
                            categories = listOf(category(id = 10L, name = "Food")),
                            categoryStep = true,
                        ),
                    onEvent = { event -> capturedEvents += event },
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
            .onNodeWithTag(CATEGORY_GRID_ADD_CELL_TAG)
            .assertIsDisplayed()
        composeTestRule
            .onNode(hasText("0") and hasClickAction())
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(listOf(AddExpenseEvent.BackToAmount), capturedEvents)
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
    fun `applying a picked date emits the selected expense date event`() {
        val capturedEvents = mutableListOf<AddExpenseEvent>()
        val initialDate = LocalDate.of(2026, 5, 17)
        val selectedDate = initialDate.plusDays(1)

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
        composeTestRule.onNodeWithText(dateLabel(selectedDate)).performClick()
        composeTestRule.onNodeWithText(targetString(R.string.apply)).performClick()

        composeTestRule.runOnIdle {
            assertEquals(listOf(AddExpenseEvent.DateChanged(selectedDate)), capturedEvents)
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
                    state =
                        AddExpenseState(
                            categories = listOf(category(id = 10L, name = "Food")),
                            categoryStep = true,
                        ),
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

    // ---- Regression: New mode must NOT show the in-form delete button (Edit mode only) --------

    @Test
    fun `New mode expense form does not show the in-form delete button`() {
        composeTestRule.setContent {
            MyMoneyTheme {
                AddExpenseScreen(
                    state = AddExpenseState(),
                    onEvent = {},
                )
            }
        }

        composeTestRule
            .onNodeWithText(targetString(DesignSystemR.string.transaction_form_delete_button))
            .assertDoesNotExist()
    }

    @Test
    fun `add category cell emits add event`() {
        val capturedEvents = mutableListOf<AddExpenseEvent>()

        composeTestRule.setContent {
            MyMoneyTheme {
                AddExpenseScreen(
                    state =
                        AddExpenseState(
                            categories = listOf(category(id = 10L, name = "Food")),
                            categoryStep = true,
                        ),
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

    private fun category(
        id: Long,
        name: String,
    ): Category =
        Category(
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
        val locale =
            InstrumentationRegistry
                .getInstrumentation()
                .targetContext.resources.configuration.locales[0]
        return date.format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", locale))
    }
}
