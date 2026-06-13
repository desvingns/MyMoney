package com.kshavrin.mymoney.feature.dictionaries.accounts

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kshavrin.mymoney.core.domain.model.AccountType
import com.kshavrin.mymoney.core.domain.model.Currency
import com.kshavrin.mymoney.core.ui.theme.MyMoneyTheme
import com.kshavrin.mymoney.feature.dictionaries.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AccountEditContentUiTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun allFieldsEmitTheirEventsInOrder() {
        val events = mutableListOf<AccountEditEvent>()
        composeTestRule.setContent {
            MyMoneyTheme {
                var state by remember {
                    mutableStateOf(
                        AccountEditState(
                            currencyId = 10L,
                            availableCurrencies =
                                listOf(
                                    currency(10L, "EUR", "€"),
                                    currency(20L, "GBP", "£"),
                                ),
                        ),
                    )
                }
                AccountEditContent(
                    state = state,
                    onEvent = { event ->
                        events += event
                        state =
                            when (event) {
                                is AccountEditEvent.NameChanged -> state.copy(name = event.value)
                                is AccountEditEvent.CurrencyChanged -> state.copy(currencyId = event.id)
                                is AccountEditEvent.InitialBalanceChanged -> state.copy(initialBalanceText = event.value)
                                is AccountEditEvent.TypeChanged -> state.copy(type = event.value)
                                is AccountEditEvent.IsDefaultChanged -> state.copy(isDefault = event.value)
                                else -> state
                            }
                    },
                )
            }
        }

        // Currency dropdown first, while the soft keyboard is still down (avoids IME vs popup races).
        composeTestRule.onNodeWithText("EUR (€)").performClick()
        composeTestRule.onNodeWithText("GBP (£)").performClick()
        // Then top-down: text inputs raise the keyboard, scroll-to reaches Column children below it
        // (proven in S22). The colour picker is never touched, so no LazyVerticalGrid scroll.
        composeTestRule
            .onNodeWithText(targetString(R.string.dictionaries_field_name))
            .performTextInput("Savings Acc")
        // Balance field carries the default value "0" (unique on screen); match its editable text
        // rather than the floated label, then replace it wholesale.
        composeTestRule
            .onNodeWithText("0")
            .performScrollTo()
            .performTextReplacement("150")
        composeTestRule
            .onNodeWithText(targetString(R.string.dictionaries_type_bank))
            .performScrollTo()
            .performClick()
        composeTestRule.onNode(isToggleable()).performScrollTo().performClick()
        composeTestRule
            .onNodeWithText(targetString(R.string.dictionaries_save))
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(
                listOf(
                    AccountEditEvent.CurrencyChanged(20L),
                    AccountEditEvent.NameChanged("Savings Acc"),
                    AccountEditEvent.InitialBalanceChanged("150"),
                    AccountEditEvent.TypeChanged(AccountType.Bank),
                    AccountEditEvent.IsDefaultChanged(true),
                    AccountEditEvent.SaveClicked,
                ),
                events,
            )
        }
    }

    @Test
    fun blockedDeleteDialogDismissesWithoutDeleteRefireAndInlineErrorShows() {
        val events = mutableListOf<AccountEditEvent>()
        composeTestRule.setContent {
            MyMoneyTheme {
                AccountEditContent(
                    state =
                        AccountEditState(
                            isCreateMode = false,
                            name = "Wallet",
                            errorMessage = "currency_required",
                            blockedDeleteCount = 2,
                        ),
                    onEvent = { events += it },
                )
            }
        }

        composeTestRule
            .onNodeWithText(targetString(R.string.dictionaries_error_currency_required))
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(targetString(R.string.dictionaries_blocked_delete_title))
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(targetString(R.string.dictionaries_ok)).performClick()

        composeTestRule.runOnIdle {
            assertEquals(listOf(AccountEditEvent.BlockedDeleteDismissed), events)
        }
    }

    @Test
    fun `account icon picker opens and emits selected icon key`() {
        val events = mutableListOf<AccountEditEvent>()
        composeTestRule.setContent {
            MyMoneyTheme {
                AccountEditContent(
                    state = AccountEditState(iconKey = "ic_account_cash"),
                    onEvent = { events += it },
                )
            }
        }

        composeTestRule.onNodeWithText("ic_account_cash").performScrollTo().performClick()
        composeTestRule.onNodeWithText(targetString(R.string.dictionaries_choose_icon)).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("ic_account_bank").performClick()

        composeTestRule.runOnIdle {
            assertEquals(listOf(AccountEditEvent.IconChanged("ic_account_bank")), events)
        }
    }

    @Test
    fun `save button is displayed at bottom and emits SaveClicked in create mode`() {
        val events = mutableListOf<AccountEditEvent>()
        composeTestRule.setContent {
            MyMoneyTheme {
                AccountEditContent(
                    state = AccountEditState(isCreateMode = true),
                    onEvent = { events += it },
                )
            }
        }

        composeTestRule
            .onNodeWithText(targetString(R.string.dictionaries_save))
            .assertIsDisplayed()
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(listOf(AccountEditEvent.SaveClicked), events)
        }
    }

    @Test
    fun `delete button is present in body in edit mode and save is also displayed`() {
        val events = mutableListOf<AccountEditEvent>()
        composeTestRule.setContent {
            MyMoneyTheme {
                AccountEditContent(
                    state = AccountEditState(isCreateMode = false, name = "Wallet"),
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
            assertEquals(listOf(AccountEditEvent.DeleteClicked), events)
        }
    }

    private fun currency(
        id: Long,
        code: String,
        symbol: String,
    ): Currency =
        Currency(
            id = id,
            code = code,
            symbol = symbol,
            name = code,
            decimalDigits = 2,
            isActive = true,
            sortOrder = 0,
        )

    private fun targetString(resourceId: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(resourceId)
}
