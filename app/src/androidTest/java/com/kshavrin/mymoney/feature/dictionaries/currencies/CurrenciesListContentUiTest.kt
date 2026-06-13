package com.kshavrin.mymoney.feature.dictionaries.currencies

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kshavrin.mymoney.core.domain.model.Currency
import com.kshavrin.mymoney.core.ui.theme.MyMoneyTheme
import com.kshavrin.mymoney.feature.dictionaries.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CurrenciesListContentUiTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun fabSwitchRowAndBackEmitEventsWithSeededRow() {
        val events = mutableListOf<CurrenciesListEvent>()
        composeTestRule.setContent {
            MyMoneyTheme {
                CurrenciesListContent(
                    state =
                        CurrenciesListState(
                            currencies = listOf(currency(1L, "EUR", "€", "Euro", isActive = true)),
                        ),
                    onEvent = { events += it },
                )
            }
        }

        composeTestRule.onNodeWithText("EUR €").assertIsDisplayed()
        composeTestRule.onNodeWithText("Euro").assertIsDisplayed()

        composeTestRule
            .onNodeWithContentDescription(targetString(R.string.dictionaries_add))
            .assertIsEnabled()
            .performClick()
        // The row's only toggleable is the active Switch; toggling a checked row emits active=false.
        composeTestRule.onNode(isToggleable()).performClick()
        // Tap the code/symbol text (unmerged) for the row click: the merged-row centre lands on the Switch.
        composeTestRule.onNodeWithText("EUR €", useUnmergedTree = true).performClick()
        composeTestRule.onNodeWithContentDescription(targetString(R.string.dictionaries_back)).performClick()

        composeTestRule.runOnIdle {
            assertEquals(
                listOf(
                    CurrenciesListEvent.AddClicked,
                    CurrenciesListEvent.ActiveToggled(1L, false),
                    CurrenciesListEvent.ItemClicked(1L),
                    CurrenciesListEvent.BackClicked,
                ),
                events,
            )
        }
    }

    @Test
    fun emptyCurrenciesListRendersWithEnabledAddFab() {
        composeTestRule.setContent {
            MyMoneyTheme {
                CurrenciesListContent(state = CurrenciesListState(), onEvent = {})
            }
        }

        composeTestRule.onNodeWithText(targetString(R.string.dictionaries_currencies_title)).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(targetString(R.string.dictionaries_add)).assertIsEnabled()
    }

    private fun currency(
        id: Long,
        code: String,
        symbol: String,
        name: String,
        isActive: Boolean,
    ): Currency =
        Currency(
            id = id,
            code = code,
            symbol = symbol,
            name = name,
            decimalDigits = 2,
            isActive = isActive,
            sortOrder = 0,
        )

    private fun targetString(resourceId: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(resourceId)
}
