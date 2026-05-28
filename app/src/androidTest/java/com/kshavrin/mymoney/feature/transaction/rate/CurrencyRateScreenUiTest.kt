package com.kshavrin.mymoney.feature.transaction.rate

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kshavrin.mymoney.core.domain.model.Currency
import com.kshavrin.mymoney.core.ui.theme.MyMoneyTheme
import com.kshavrin.mymoney.feature.transaction.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CurrencyRateScreenUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `back button emits currency rate back event`() {
        val capturedEvents = mutableListOf<CurrencyRateEvent>()

        composeTestRule.setContent {
            MyMoneyTheme {
                CurrencyRateScreen(
                    state = CurrencyRateState(),
                    onEvent = { event -> capturedEvents += event },
                )
            }
        }

        composeTestRule
            .onNodeWithContentDescription(targetString(R.string.back))
            .assertIsEnabled()
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(listOf(CurrencyRateEvent.BackClicked), capturedEvents)
        }
    }

    @Test
    fun `save button stays disabled until rate is valid`() {
        composeTestRule.setContent {
            MyMoneyTheme {
                CurrencyRateScreen(
                    state = CurrencyRateState(),
                    onEvent = {},
                )
            }
        }

        composeTestRule
            .onNodeWithText(targetString(R.string.currency_rate_save))
            .assertIsNotEnabled()
    }

    @Test
    fun `rate input emits text change event`() {
        val capturedEvents = mutableListOf<CurrencyRateEvent>()

        composeTestRule.setContent {
            MyMoneyTheme {
                CurrencyRateScreen(
                    state = CurrencyRateState(),
                    onEvent = { event -> capturedEvents += event },
                )
            }
        }

        composeTestRule
            .onNode(hasSetTextAction())
            .performTextInput("0.92")

        composeTestRule.runOnIdle {
            assertTrue(capturedEvents.contains(CurrencyRateEvent.RateInputChanged("0.92")))
        }
    }

    @Test
    fun `invalid rate shows inline error and keeps save disabled`() {
        composeTestRule.setContent {
            MyMoneyTheme {
                CurrencyRateScreen(
                    state = CurrencyRateState(
                        rateInput = "0",
                        rate = 0.0,
                        isValid = false,
                    ),
                    onEvent = {},
                )
            }
        }

        composeTestRule
            .onNodeWithText(targetString(R.string.currency_rate_invalid))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(targetString(R.string.currency_rate_save))
            .assertIsNotEnabled()
    }

    @Test
    fun `valid rate preview and save emit currency rate event`() {
        val capturedEvents = mutableListOf<CurrencyRateEvent>()

        composeTestRule.setContent {
            MyMoneyTheme {
                CurrencyRateScreen(
                    state = CurrencyRateState(
                        fromCurrency = currency(id = 1L, code = "USD"),
                        toCurrency = currency(id = 2L, code = "EUR"),
                        rateInput = "0.92",
                        rate = 0.92,
                        isValid = true,
                    ),
                    onEvent = { event -> capturedEvents += event },
                )
            }
        }

        composeTestRule.onNodeWithText(targetString(R.string.source_label)).assertIsDisplayed()
        composeTestRule.onNodeWithText("USD").assertIsDisplayed()
        composeTestRule.onNodeWithText(targetString(R.string.target_label)).assertIsDisplayed()
        composeTestRule.onNodeWithText("EUR").assertIsDisplayed()
        composeTestRule
            .onNodeWithText(targetString(R.string.currency_rate_pattern, "USD", "0.92", "EUR"))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(targetString(R.string.currency_rate_save))
            .assertIsEnabled()
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(listOf(CurrencyRateEvent.SaveClicked), capturedEvents)
        }
    }

    private fun currency(id: Long, code: String): Currency = Currency(
        id = id,
        code = code,
        symbol = code,
        name = code,
        decimalDigits = 2,
        isActive = true,
        sortOrder = 0,
    )

    private fun targetString(resourceId: Int, vararg formatArgs: Any): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(resourceId, *formatArgs)
}
