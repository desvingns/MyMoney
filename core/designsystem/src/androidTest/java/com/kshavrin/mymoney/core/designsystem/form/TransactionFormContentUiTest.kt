package com.kshavrin.mymoney.core.designsystem.form

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kshavrin.mymoney.core.designsystem.R
import com.kshavrin.mymoney.core.designsystem.keypad.KeypadEvent
import com.kshavrin.mymoney.core.ui.theme.MyMoneyTheme
import com.kshavrin.mymoney.core.ui.theme.Spacing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class TransactionFormContentUiTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `amount step shows note keypad and disabled choose category button`() {
        composeTestRule.setContent {
            MyMoneyTheme {
                TransactionFormContent(
                    state =
                        defaultState(
                            chooseCategoryEnabled = false,
                            categories = listOf(category(id = 10L, name = "Food")),
                        ),
                    onEvent = {},
                )
            }
        }

        composeTestRule
            .onNodeWithText(targetString(R.string.amountfield_note_hint))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(targetString(R.string.transaction_form_choose_category_button))
            .assertIsDisplayed()
            .assertIsNotEnabled()
        composeTestRule
            .onAllNodes(hasText("7") and hasClickAction())
            .assertCountEquals(1)
        composeTestRule
            .onNodeWithTag(CATEGORY_GRID_TAG)
            .assertDoesNotExist()
    }

    @Test
    fun `amount step keeps keypad directly under note and lets choose category fill the freed space`() {
        composeTestRule.setContent {
            MyMoneyTheme {
                Box(
                    modifier = Modifier.requiredSize(width = 360.dp, height = 700.dp),
                ) {
                    TransactionFormContent(
                        state =
                            defaultState(
                                amountInput = "12",
                                chooseCategoryEnabled = true,
                            ),
                        onEvent = {},
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        val noteBounds =
            composeTestRule
                .onNode(hasSetTextAction())
                .fetchSemanticsNode()
                .boundsInRoot
        val keypadTop =
            composeTestRule
                .onNode(hasText("1") and hasClickAction())
                .fetchSemanticsNode()
                .boundsInRoot
                .top
        val buttonBounds =
            composeTestRule
                .onNodeWithText(targetString(R.string.transaction_form_choose_category_button))
                .assertIsDisplayed()
                .assertIsEnabled()
                .fetchSemanticsNode()
                .boundsInRoot
        val minButtonHeight =
            with(composeTestRule.density) {
                Spacing.transactionFormChooseCategoryMinHeight.toPx()
            }

        assertTrue(
            "keypad must start immediately under the note field",
            keypadTop - noteBounds.bottom <= 1f,
        )
        assertTrue(
            "choose category button must grow beyond its minimum height when space is available",
            buttonBounds.height > minButtonHeight + 1f,
        )
    }

    @Test
    fun `amount step forwards date note keypad and choose category events`() {
        val capturedEvents = mutableListOf<TransactionFormEvent>()
        val occurredAt = LocalDate.of(2026, 6, 6)

        composeTestRule.setContent {
            MyMoneyTheme {
                TransactionFormContent(
                    state =
                        defaultState(
                            amountInput = "12",
                            chooseCategoryEnabled = true,
                            occurredAt = occurredAt,
                        ),
                    onEvent = { event -> capturedEvents += event },
                )
            }
        }

        composeTestRule
            .onNodeWithText(dateLabel(occurredAt))
            .performClick()
        composeTestRule
            .onNodeWithText(targetString(R.string.amountfield_note_hint))
            .performTextInput("Dinner")
        composeTestRule
            .onNode(hasText("1") and hasClickAction())
            .performClick()
        composeTestRule
            .onNodeWithText(targetString(R.string.transaction_form_choose_category_button))
            .assertIsEnabled()
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(
                listOf(
                    TransactionFormEvent.DateHeaderClicked,
                    TransactionFormEvent.NoteChanged("Dinner"),
                    TransactionFormEvent.Keypad(KeypadEvent.Digit(1)),
                    TransactionFormEvent.SelectCategoryClicked,
                ),
                capturedEvents,
            )
        }
    }

    @Test
    fun `amount step keeps keypad usable and button within a short container`() {
        composeTestRule.setContent {
            MyMoneyTheme {
                Box(
                    modifier =
                        Modifier
                            .requiredSize(width = 320.dp, height = 620.dp)
                            .testTag(FORM_CONTAINER_TAG),
                ) {
                    TransactionFormContent(
                        state =
                            defaultState(
                                amountInput = "12",
                                chooseCategoryEnabled = true,
                            ),
                        onEvent = {},
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        val containerBottom =
            composeTestRule
                .onNodeWithTag(FORM_CONTAINER_TAG)
                .fetchSemanticsNode()
                .boundsInRoot
                .bottom
        val buttonBounds =
            composeTestRule
                .onNodeWithText(targetString(R.string.transaction_form_choose_category_button))
                .assertIsDisplayed()
                .assertHeightIsAtLeast(Spacing.transactionFormChooseCategoryMinHeight)
                .fetchSemanticsNode()
                .boundsInRoot

        composeTestRule
            .onNode(hasText("1") and hasClickAction())
            .assertIsDisplayed()
            .assertHeightIsAtLeast(56.dp)

        assertTrue(
            "choose category button must stay within the short form container",
            buttonBounds.bottom <= containerBottom + 1f,
        )
    }

    @Test
    fun `category step shows grid and hides note keypad and choose category button`() {
        composeTestRule.setContent {
            MyMoneyTheme {
                TransactionFormContent(
                    state =
                        defaultState(
                            amountInput = "12",
                            categories = listOf(category(id = 10L, name = "Food")),
                            categoryStep = true,
                        ),
                    onEvent = {},
                )
            }
        }

        composeTestRule
            .onNodeWithTag(CATEGORY_GRID_TAG)
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithContentDescription(
                targetString(R.string.transaction_form_category_cd, "Food"),
            ).assertIsDisplayed()
        composeTestRule
            .onNodeWithTag(CATEGORY_GRID_ADD_CELL_TAG)
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(targetString(R.string.amountfield_note_hint))
            .assertDoesNotExist()
        composeTestRule
            .onNodeWithText(targetString(R.string.transaction_form_choose_category_button))
            .assertDoesNotExist()
        composeTestRule
            .onAllNodes(hasText("7") and hasClickAction())
            .assertCountEquals(0)
    }

    @Test
    fun `category step amount category and add affordances emit shared form events`() {
        val capturedEvents = mutableListOf<TransactionFormEvent>()

        composeTestRule.setContent {
            MyMoneyTheme {
                TransactionFormContent(
                    state =
                        defaultState(
                            amountInput = "12",
                            categories = listOf(category(id = 10L, name = "Food")),
                            categoryStep = true,
                        ),
                    onEvent = { event -> capturedEvents += event },
                )
            }
        }

        composeTestRule
            .onNode(hasText("12") and hasClickAction())
            .performClick()
        composeTestRule
            .onNodeWithContentDescription(
                targetString(R.string.transaction_form_category_cd, "Food"),
            ).performClick()
        composeTestRule
            .onNodeWithTag(CATEGORY_GRID_ADD_CELL_TAG)
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(
                listOf(
                    TransactionFormEvent.BackToAmount,
                    TransactionFormEvent.CategoryPicked(10L),
                    TransactionFormEvent.AddCategoryClicked,
                ),
                capturedEvents,
            )
        }
    }

    private fun defaultState(
        amountInput: String = "0",
        occurredAt: LocalDate = LocalDate.of(2026, 6, 6),
        categories: List<TransactionFormCategory> = emptyList(),
        categoryStep: Boolean = false,
        chooseCategoryEnabled: Boolean = false,
    ): TransactionFormState =
        TransactionFormState(
            amountInput = amountInput,
            expression = "",
            currencyCode = "USD",
            currencySymbol = "$",
            note = "",
            occurredAt = occurredAt,
            categories = categories,
            categoryStep = categoryStep,
            chooseCategoryEnabled = chooseCategoryEnabled,
        )

    private fun category(
        id: Long,
        name: String,
    ): TransactionFormCategory =
        TransactionFormCategory(
            id = id,
            name = name,
            colorHex = "#7AC794",
            iconKey = "ic_cat_food",
        )

    private fun targetString(
        resourceId: Int,
        vararg formatArgs: Any,
    ): String =
        InstrumentationRegistry
            .getInstrumentation()
            .targetContext
            .getString(resourceId, *formatArgs)

    private fun dateLabel(date: LocalDate): String {
        val locale =
            InstrumentationRegistry
                .getInstrumentation()
                .targetContext.resources.configuration.locales[0]
        return date.format(
            java.time.format.DateTimeFormatter
                .ofPattern("EEEE, d MMMM", locale),
        )
    }

    private companion object {
        const val FORM_CONTAINER_TAG = "transaction_form_container"
    }
}
