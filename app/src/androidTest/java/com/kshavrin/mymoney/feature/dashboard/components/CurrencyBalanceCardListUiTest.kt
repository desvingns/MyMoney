package com.kshavrin.mymoney.feature.dashboard.components

import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kshavrin.mymoney.core.common.money.MoneyFormatter
import com.kshavrin.mymoney.core.domain.model.BalanceSnapshot
import com.kshavrin.mymoney.core.domain.model.Currency
import com.kshavrin.mymoney.core.domain.model.Money
import com.kshavrin.mymoney.core.ui.theme.MyMoneyTheme
import com.kshavrin.mymoney.core.ui.theme.Spacing
import com.kshavrin.mymoney.feature.dashboard.CurrencyBalanceCard
import com.kshavrin.mymoney.feature.dashboard.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class CurrencyBalanceCardListUiTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val usd =
        Currency(
            id = 1L,
            code = "USD",
            symbol = "$",
            name = "US Dollar",
            decimalDigits = 2,
            isActive = true,
            sortOrder = 0,
        )

    private val eur =
        Currency(
            id = 2L,
            code = "EUR",
            symbol = "EUR",
            name = "Euro",
            decimalDigits = 2,
            isActive = true,
            sortOrder = 1,
        )

    @Test
    fun `empty card list renders the currency cards container tag without any children`() {
        composeTestRule.setContent {
            MyMoneyTheme {
                CurrencyBalanceCardList(
                    cards = emptyList(),
                    modifier = Modifier.padding(Spacing.l),
                )
            }
        }

        composeTestRule
            .onNodeWithTag(DASHBOARD_CURRENCY_CARDS_TAG)
            .assertExists()
    }

    @Test
    fun `single currency card shows the currency code`() {
        composeTestRule.setContent {
            MyMoneyTheme {
                CurrencyBalanceCardList(
                    cards = listOf(usdCard(income = "100.00", expense = "30.00")),
                )
            }
        }

        composeTestRule
            .onNodeWithText("USD")
            .assertIsDisplayed()
    }

    @Test
    fun `single currency card shows income expense and balance labels`() {
        composeTestRule.setContent {
            MyMoneyTheme {
                CurrencyBalanceCardList(
                    cards = listOf(usdCard(income = "100.00", expense = "30.00")),
                )
            }
        }

        composeTestRule
            .onNodeWithText(targetString(R.string.dashboard_currency_card_income))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(targetString(R.string.dashboard_currency_card_expense))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(targetString(R.string.dashboard_currency_card_balance))
            .assertIsDisplayed()
    }

    @Test
    fun `single currency card shows formatted income amount`() {
        val income = BigDecimal("100.00")

        composeTestRule.setContent {
            MyMoneyTheme {
                CurrencyBalanceCardList(
                    cards = listOf(usdCard(income = "100.00", expense = "30.00")),
                )
            }
        }

        val expected =
            MoneyFormatter.format(
                amount = income,
                currencySymbol = usd.symbol,
                decimalDigits = usd.decimalDigits,
                locale = targetLocale(),
                symbolPosition = MoneyFormatter.SymbolPosition.AFTER,
            )
        composeTestRule.onNodeWithText(expected).assertIsDisplayed()
    }

    @Test
    fun `single currency card shows formatted net balance amount`() {
        val net = BigDecimal("70.00")

        composeTestRule.setContent {
            MyMoneyTheme {
                CurrencyBalanceCardList(
                    cards = listOf(usdCard(income = "100.00", expense = "30.00")),
                )
            }
        }

        val expected =
            MoneyFormatter.format(
                amount = net,
                currencySymbol = usd.symbol,
                decimalDigits = usd.decimalDigits,
                locale = targetLocale(),
                symbolPosition = MoneyFormatter.SymbolPosition.AFTER,
            )
        composeTestRule.onNodeWithText(expected).assertIsDisplayed()
    }

    @Test
    fun `two currency cards render both currency codes`() {
        composeTestRule.setContent {
            MyMoneyTheme {
                CurrencyBalanceCardList(
                    cards =
                        listOf(
                            usdCard(income = "100.00", expense = "30.00"),
                            eurCard(income = "50.00", expense = "20.00"),
                        ),
                )
            }
        }

        composeTestRule.onNodeWithText("USD").assertIsDisplayed()
        composeTestRule.onNodeWithText("EUR").assertIsDisplayed()
    }

    @Test
    fun `two currency cards render both income label instances`() {
        composeTestRule.setContent {
            MyMoneyTheme {
                CurrencyBalanceCardList(
                    cards =
                        listOf(
                            usdCard(income = "100.00", expense = "30.00"),
                            eurCard(income = "50.00", expense = "20.00"),
                        ),
                )
            }
        }

        val incomeLabel = targetString(R.string.dashboard_currency_card_income)
        val incomeNodes = composeTestRule.onAllNodesWithText(incomeLabel)
        // One Income label per card — two cards → two occurrences
        assertEquals(2, incomeNodes.fetchSemanticsNodes().size)
    }

    @Test
    fun `currency card figures are in the card currency with no conversion`() {
        // EUR card: income 50 EUR, expense 20 EUR, net 30 EUR.
        // If ConvertMoneyUseCase were called, figures would differ.
        val income = BigDecimal("50.00")
        val expense = BigDecimal("20.00")
        val net = BigDecimal("30.00")

        composeTestRule.setContent {
            MyMoneyTheme {
                CurrencyBalanceCardList(
                    cards = listOf(eurCard(income = "50.00", expense = "20.00")),
                )
            }
        }

        listOf(income, expense, net).forEach { amount ->
            val formatted =
                MoneyFormatter.format(
                    amount = amount,
                    currencySymbol = eur.symbol,
                    decimalDigits = eur.decimalDigits,
                    locale = targetLocale(),
                    symbolPosition = MoneyFormatter.SymbolPosition.AFTER,
                )
            composeTestRule.onNodeWithText(formatted).assertIsDisplayed()
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private fun usdCard(
        income: String,
        expense: String,
    ): CurrencyBalanceCard {
        val incomeAmount = BigDecimal(income)
        val expenseAmount = BigDecimal(expense)
        return CurrencyBalanceCard(
            currency = usd,
            snapshot =
                BalanceSnapshot(
                    income = Money(incomeAmount, usd),
                    expense = Money(expenseAmount, usd),
                    net = Money(incomeAmount.subtract(expenseAmount), usd),
                    byCategory = emptyList(),
                ),
        )
    }

    private fun eurCard(
        income: String,
        expense: String,
    ): CurrencyBalanceCard {
        val incomeAmount = BigDecimal(income)
        val expenseAmount = BigDecimal(expense)
        return CurrencyBalanceCard(
            currency = eur,
            snapshot =
                BalanceSnapshot(
                    income = Money(incomeAmount, eur),
                    expense = Money(expenseAmount, eur),
                    net = Money(incomeAmount.subtract(expenseAmount), eur),
                    byCategory = emptyList(),
                ),
        )
    }

    private fun targetString(resourceId: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(resourceId)

    private fun targetLocale(): Locale =
        InstrumentationRegistry
            .getInstrumentation()
            .targetContext.resources.configuration.locales[0]
}
