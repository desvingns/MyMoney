package com.kshavrin.mymoney.feature.dictionaries.currencies

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kshavrin.mymoney.core.ui.theme.MyMoneyTheme
import com.kshavrin.mymoney.feature.dictionaries.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CurrencyEditContentUiTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun allFieldsEmitTheirEventsInOrder() {
        val events = mutableListOf<CurrencyEditEvent>()
        composeTestRule.setContent {
            MyMoneyTheme {
                var state by remember { mutableStateOf(CurrencyEditState()) }
                CurrencyEditContent(
                    state = state,
                    onEvent = { event ->
                        events += event
                        state =
                            when (event) {
                                is CurrencyEditEvent.CodeChanged -> state.copy(code = event.value)
                                is CurrencyEditEvent.SymbolChanged -> state.copy(symbol = event.value)
                                is CurrencyEditEvent.NameChanged -> state.copy(name = event.value)
                                is CurrencyEditEvent.DecimalDigitsChanged -> state.copy(decimalDigitsText = event.value)
                                is CurrencyEditEvent.IsActiveChanged -> state.copy(isActive = event.value)
                                else -> state
                            }
                    },
                )
            }
        }

        composeTestRule
            .onNodeWithText(targetString(R.string.dictionaries_field_code))
            .performTextInput("USD")
        composeTestRule
            .onNodeWithText(targetString(R.string.dictionaries_field_symbol))
            .performScrollTo()
            .performTextInput("kr")
        composeTestRule
            .onNodeWithText(targetString(R.string.dictionaries_field_name))
            .performScrollTo()
            .performTextInput("Krona")
        // The decimal-digits field carries the default "2" (unique on screen); match its editable text.
        composeTestRule.onNodeWithText("2").performScrollTo().performTextReplacement("3")
        composeTestRule.onNode(isToggleable()).performScrollTo().performClick()
        composeTestRule
            .onNodeWithText(targetString(R.string.dictionaries_save))
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(
                listOf(
                    CurrencyEditEvent.CodeChanged("USD"),
                    CurrencyEditEvent.SymbolChanged("kr"),
                    CurrencyEditEvent.NameChanged("Krona"),
                    CurrencyEditEvent.DecimalDigitsChanged("3"),
                    CurrencyEditEvent.IsActiveChanged(false),
                    CurrencyEditEvent.SaveClicked,
                ),
                events,
            )
        }
    }

    @Test
    fun lockedCodeFieldIsDisabled() {
        composeTestRule.setContent {
            MyMoneyTheme {
                CurrencyEditContent(
                    state =
                        CurrencyEditState(
                            isCreateMode = false,
                            code = "USD",
                            symbol = "kr",
                            name = "US Dollar",
                            isCodeLocked = true,
                            dependentAccountCount = 2,
                        ),
                    onEvent = {},
                )
            }
        }

        composeTestRule.onNodeWithText("USD").assertIsNotEnabled()
    }

    @Test
    fun invalidCodeErrorAndBlockedDeleteDialogDismiss() {
        val events = mutableListOf<CurrencyEditEvent>()
        composeTestRule.setContent {
            MyMoneyTheme {
                CurrencyEditContent(
                    state =
                        CurrencyEditState(
                            isCreateMode = false,
                            code = "US",
                            errorMessage = "code_format",
                            blockedDeleteCount = 4,
                        ),
                    onEvent = { events += it },
                )
            }
        }

        composeTestRule.onNodeWithText(targetString(R.string.dictionaries_error_code_format)).assertIsDisplayed()
        composeTestRule.onNodeWithText(targetString(R.string.dictionaries_blocked_delete_title)).assertIsDisplayed()
        composeTestRule.onNodeWithText(targetString(R.string.dictionaries_ok)).performClick()

        composeTestRule.runOnIdle {
            assertEquals(listOf(CurrencyEditEvent.BlockedDeleteDismissed), events)
        }
    }

    @Test
    fun `save button is displayed at bottom and emits SaveClicked in create mode`() {
        val events = mutableListOf<CurrencyEditEvent>()
        composeTestRule.setContent {
            MyMoneyTheme {
                CurrencyEditContent(
                    state = CurrencyEditState(isCreateMode = true),
                    onEvent = { events += it },
                )
            }
        }

        composeTestRule
            .onNodeWithText(targetString(R.string.dictionaries_save))
            .assertIsDisplayed()
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(listOf(CurrencyEditEvent.SaveClicked), events)
        }
    }

    @Test
    fun `delete button is present in body in edit mode and save is also displayed`() {
        val events = mutableListOf<CurrencyEditEvent>()
        composeTestRule.setContent {
            MyMoneyTheme {
                CurrencyEditContent(
                    state = CurrencyEditState(isCreateMode = false, code = "EUR"),
                    onEvent = { events += it },
                )
            }
        }

        composeTestRule
            .onNodeWithText(targetString(R.string.dictionaries_save))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(targetString(R.string.dictionaries_delete))
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(listOf(CurrencyEditEvent.DeleteClicked), events)
        }
    }

    private fun targetString(resourceId: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(resourceId)
}
