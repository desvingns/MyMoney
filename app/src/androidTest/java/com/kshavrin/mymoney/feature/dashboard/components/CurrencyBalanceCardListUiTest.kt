package com.kshavrin.mymoney.feature.dashboard.components

import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kshavrin.mymoney.core.common.money.MoneyFormatter
import com.kshavrin.mymoney.core.domain.model.BalanceSnapshot
import com.kshavrin.mymoney.core.domain.model.Currency
import com.kshavrin.mymoney.core.domain.model.Money
import com.kshavrin.mymoney.core.domain.model.Period
import com.kshavrin.mymoney.core.domain.model.TrendPoint
import com.kshavrin.mymoney.core.ui.theme.MyMoneyTheme
import com.kshavrin.mymoney.core.ui.theme.Spacing
import com.kshavrin.mymoney.feature.dashboard.ChartConfig
import com.kshavrin.mymoney.feature.dashboard.CurrencyBalanceCard
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.YearMonth

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

    // -----------------------------------------------------------------------
    // Empty list
    // -----------------------------------------------------------------------

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

    // -----------------------------------------------------------------------
    // Single card — currency code
    // -----------------------------------------------------------------------

    @Test
    fun `single currency card shows the currency code`() {
        composeTestRule.setContent {
            MyMoneyTheme {
                CurrencyBalanceCardList(
                    cards = listOf(usdCard(income = "100.99", expense = "30.49")),
                )
            }
        }

        composeTestRule
            .onNodeWithText("USD")
            .assertIsDisplayed()
    }

    // -----------------------------------------------------------------------
    // Single card — per-card container tag
    // -----------------------------------------------------------------------

    @Test
    fun `single currency card has a per-card container tag derived from the currency code`() {
        composeTestRule.setContent {
            MyMoneyTheme {
                CurrencyBalanceCardList(
                    cards = listOf(usdCard(income = "100.99", expense = "30.49")),
                )
            }
        }

        // Tag format: "${DASHBOARD_CURRENCY_CARDS_TAG}_<CODE>"
        composeTestRule
            .onNodeWithTag("${DASHBOARD_CURRENCY_CARDS_TAG}_USD")
            .assertExists()
    }

    @Test
    fun `single currency card keeps the wide centered aurora container`() {
        composeTestRule.setContent {
            MyMoneyTheme {
                CurrencyBalanceCardList(
                    cards = listOf(usdCard(income = "100.99", expense = "30.49")),
                )
            }
        }

        val rootBounds = composeTestRule.onRoot().fetchSemanticsNode().boundsInRoot
        val cardBounds =
            composeTestRule
                .onNodeWithTag("${DASHBOARD_CURRENCY_CARDS_TAG}_USD")
                .fetchSemanticsNode()
                .boundsInRoot
        val expectedInsetPx = with(composeTestRule.density) { Spacing.dashboardAuroraHostHorizontalPaddingWide.toPx() }

        assertCloseTo(expectedInsetPx, cardBounds.left, "left inset")
        assertCloseTo(rootBounds.right - expectedInsetPx, cardBounds.right, "right inset")
    }

    // -----------------------------------------------------------------------
    // Single card — balance value (big number, no "Balance" label in new design)
    // -----------------------------------------------------------------------

    @Test
    fun `single currency card shows the formatted net balance amount`() {
        val net = BigDecimal("70.50")

        composeTestRule.setContent {
            MyMoneyTheme {
                CurrencyBalanceCardList(
                    cards = listOf(usdCard(income = "100.99", expense = "30.49")),
                )
            }
        }

        val expected = formatAmount(net, usd)
        composeTestRule.onNodeWithText(expected).assertIsDisplayed()
    }

    @Test
    fun `single currency card balance uses the compact 26sp typography token`() {
        val expected = formatAmount(BigDecimal("70.50"), usd)

        composeTestRule.setContent {
            MyMoneyTheme {
                CurrencyBalanceCardList(
                    cards = listOf(usdCard(income = "100.99", expense = "30.49")),
                )
            }
        }

        val fontSize =
            composeTestRule
                .onNodeWithText(expected)
                .textLayout()
                .layoutInput.style.fontSize
        assertEquals(26.sp, fontSize)
    }

    // -----------------------------------------------------------------------
    // Single card — income/expense pills (arrows prefix — use substring match)
    // -----------------------------------------------------------------------

    @Test
    fun `single currency card income pill contains the formatted income amount`() {
        val income = BigDecimal("100.99")

        composeTestRule.setContent {
            MyMoneyTheme {
                CurrencyBalanceCardList(
                    cards = listOf(usdCard(income = "100.99", expense = "30.49")),
                )
            }
        }

        // The pill text is "↑ <formattedAmount>"; substring=true finds the amount inside the pill.
        val formatted = formatAmount(income, usd)
        composeTestRule
            .onNodeWithText(formatted, substring = true)
            .assertIsDisplayed()
    }

    @Test
    fun `single currency card expense pill contains the formatted expense amount`() {
        val expense = BigDecimal("30.49")

        composeTestRule.setContent {
            MyMoneyTheme {
                CurrencyBalanceCardList(
                    cards = listOf(usdCard(income = "100.99", expense = "30.49")),
                )
            }
        }

        // The pill text is "↓ <formattedAmount>".
        val formatted = formatAmount(expense, usd)
        composeTestRule
            .onNodeWithText(formatted, substring = true)
            .assertIsDisplayed()
    }

    @Test
    fun `single currency card income pill text starts with the up-arrow prefix`() {
        val income = BigDecimal("100.99")

        composeTestRule.setContent {
            MyMoneyTheme {
                CurrencyBalanceCardList(
                    cards = listOf(usdCard(income = "100.99", expense = "30.49")),
                )
            }
        }

        val formatted = formatAmount(income, usd)
        // Full pill text "↑ <amount>" must exist as a node.
        composeTestRule
            .onNodeWithText("↑ $formatted")
            .assertIsDisplayed()
    }

    @Test
    fun `single currency card expense pill text starts with the down-arrow prefix`() {
        val expense = BigDecimal("30.49")

        composeTestRule.setContent {
            MyMoneyTheme {
                CurrencyBalanceCardList(
                    cards = listOf(usdCard(income = "100.99", expense = "30.49")),
                )
            }
        }

        val formatted = formatAmount(expense, usd)
        composeTestRule
            .onNodeWithText("↓ $formatted")
            .assertIsDisplayed()
    }

    // -----------------------------------------------------------------------
    // Single card — currency figures are in own currency (no conversion)
    // -----------------------------------------------------------------------

    @Test
    fun `currency card figures are in the card currency with no conversion`() {
        // EUR card: income 50 EUR, expense 20 EUR, net 30 EUR.
        // If ConvertMoneyUseCase were called, figures would differ.
        val income = BigDecimal("50.75")
        val expense = BigDecimal("20.25")
        val net = BigDecimal("30.50")

        composeTestRule.setContent {
            MyMoneyTheme {
                CurrencyBalanceCardList(
                    cards = listOf(eurCard(income = "50.75", expense = "20.25")),
                )
            }
        }

        // Net balance is displayed as a standalone value (exact match).
        val formattedNet = formatAmount(net, eur)
        composeTestRule.onNodeWithText(formattedNet).assertIsDisplayed()

        // Income and expense appear inside arrow pills — substring match.
        listOf(income, expense).forEach { amount ->
            val formatted = formatAmount(amount, eur)
            composeTestRule
                .onNodeWithText(formatted, substring = true)
                .assertIsDisplayed()
        }
    }

    @Test
    fun `negative net balance is truncated toward zero instead of rounded away`() {
        val expectedNet = formatAmount(BigDecimal("-1234.56"), usd)
        val expectedExpense = formatAmount(BigDecimal("1334.56"), usd)

        composeTestRule.setContent {
            MyMoneyTheme {
                CurrencyBalanceCardList(
                    cards = listOf(usdCard(income = "100.00", expense = "1334.56")),
                )
            }
        }

        composeTestRule.onNodeWithText(expectedNet).assertIsDisplayed()
        composeTestRule
            .onNodeWithText(expectedExpense, substring = true)
            .assertIsDisplayed()
    }

    // -----------------------------------------------------------------------
    // Two cards — currency codes and per-card tags
    // -----------------------------------------------------------------------

    @Test
    fun `two currency cards render both currency codes`() {
        composeTestRule.setContent {
            MyMoneyTheme {
                CurrencyBalanceCardList(
                    cards =
                        listOf(
                            usdCard(income = "100.99", expense = "30.49"),
                            eurCard(income = "50.75", expense = "20.25"),
                        ),
                )
            }
        }

        composeTestRule.onNodeWithText("USD").assertIsDisplayed()
        composeTestRule.onNodeWithText("EUR").assertIsDisplayed()
    }

    @Test
    fun `two currency cards each have their own per-card container tag`() {
        composeTestRule.setContent {
            MyMoneyTheme {
                CurrencyBalanceCardList(
                    cards =
                        listOf(
                            usdCard(income = "100.99", expense = "30.49"),
                            eurCard(income = "50.75", expense = "20.25"),
                        ),
                )
            }
        }

        composeTestRule
            .onNodeWithTag("${DASHBOARD_CURRENCY_CARDS_TAG}_USD")
            .assertExists()
        composeTestRule
            .onNodeWithTag("${DASHBOARD_CURRENCY_CARDS_TAG}_EUR")
            .assertExists()
    }

    // -----------------------------------------------------------------------
    // Two cards — pill count (one income + one expense pill per card → 2 each)
    // -----------------------------------------------------------------------

    @Test
    fun `two currency cards render two income pills and two expense pills`() {
        composeTestRule.setContent {
            MyMoneyTheme {
                CurrencyBalanceCardList(
                    cards =
                        listOf(
                            usdCard(income = "100.99", expense = "30.49"),
                            eurCard(income = "50.75", expense = "20.25"),
                        ),
                )
            }
        }

        // Each card has one "↑ …" pill and one "↓ …" pill. Two cards → two nodes each.
        val upArrowNodes =
            composeTestRule.onAllNodes(
                androidx.compose.ui.test
                    .hasText("↑", substring = true),
            )
        assertEquals(2, upArrowNodes.fetchSemanticsNodes().size)

        val downArrowNodes =
            composeTestRule.onAllNodes(
                androidx.compose.ui.test
                    .hasText("↓", substring = true),
            )
        assertEquals(2, downArrowNodes.fetchSemanticsNodes().size)
    }

    // -----------------------------------------------------------------------
    // Mini trend chart
    // -----------------------------------------------------------------------

    @Test
    fun `mini trend chart is shown when chart is visible and the card has trend points`() {
        composeTestRule.setContent {
            MyMoneyTheme {
                CurrencyBalanceCardList(
                    cards = listOf(usdCard(income = "100.99", expense = "30.49", withTrend = true)),
                    chartConfig = ChartConfig(visible = true),
                )
            }
        }

        composeTestRule
            .onNodeWithTag(DASHBOARD_CURRENCY_CARD_MINI_CHART_TAG)
            .assertExists()
    }

    @Test
    fun `mini trend chart is hidden when the chart is disabled in settings`() {
        composeTestRule.setContent {
            MyMoneyTheme {
                CurrencyBalanceCardList(
                    cards = listOf(usdCard(income = "100.99", expense = "30.49", withTrend = true)),
                    chartConfig = ChartConfig(visible = false),
                )
            }
        }

        composeTestRule
            .onNodeWithTag(DASHBOARD_CURRENCY_CARD_MINI_CHART_TAG)
            .assertDoesNotExist()
    }

    @Test
    fun `mini trend chart is absent when the card carries no trend points`() {
        composeTestRule.setContent {
            MyMoneyTheme {
                CurrencyBalanceCardList(
                    cards = listOf(usdCard(income = "100.99", expense = "30.49", withTrend = false)),
                    chartConfig = ChartConfig(visible = true),
                )
            }
        }

        composeTestRule
            .onNodeWithTag(DASHBOARD_CURRENCY_CARD_MINI_CHART_TAG)
            .assertDoesNotExist()
    }

    @Test
    fun `mini trend chart appears only for the card that has trend points when two cards are present`() {
        composeTestRule.setContent {
            MyMoneyTheme {
                CurrencyBalanceCardList(
                    cards =
                        listOf(
                            usdCard(income = "100.99", expense = "30.49", withTrend = true),
                            eurCard(income = "50.75", expense = "20.25"),
                        ),
                    chartConfig = ChartConfig(visible = true),
                )
            }
        }

        // Only the USD card has trend points → exactly one mini-chart node.
        val chartNodes =
            composeTestRule.onAllNodes(
                hasTestTag(DASHBOARD_CURRENCY_CARD_MINI_CHART_TAG),
            )
        assertEquals(1, chartNodes.fetchSemanticsNodes().size)
    }

    @Test
    fun `two cards with trend points both render a mini chart`() {
        composeTestRule.setContent {
            MyMoneyTheme {
                CurrencyBalanceCardList(
                    cards =
                        listOf(
                            usdCard(income = "100.99", expense = "30.49", withTrend = true),
                            eurCard(income = "50.75", expense = "20.25", withTrend = true),
                        ),
                    chartConfig = ChartConfig(visible = true),
                )
            }
        }

        composeTestRule
            .onAllNodesWithTag(DASHBOARD_CURRENCY_CARD_MINI_CHART_TAG)
            .assertCountEquals(2)
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private fun usdCard(
        income: String,
        expense: String,
        withTrend: Boolean = false,
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
            trendPoints = if (withTrend) usdTrend() else emptyList(),
        )
    }

    private fun usdTrend(): List<TrendPoint> =
        (0 until 5).map { index ->
            TrendPoint(
                index = index,
                period = Period.Month(YearMonth.of(2026, index + 1)),
                value = Money(BigDecimal((index + 1) * 10), usd),
            )
        }

    private fun eurCard(
        income: String,
        expense: String,
        withTrend: Boolean = false,
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
            trendPoints = if (withTrend) eurTrend() else emptyList(),
        )
    }

    private fun eurTrend(): List<TrendPoint> =
        (0 until 5).map { index ->
            TrendPoint(
                index = index,
                period = Period.Month(YearMonth.of(2026, index + 1)),
                value = Money(BigDecimal((index + 1) * 5), eur),
            )
        }

    private fun formatAmount(
        amount: BigDecimal,
        currency: Currency,
    ): String {
        val locale =
            InstrumentationRegistry
                .getInstrumentation()
                .targetContext.resources.configuration.locales[0]
        return MoneyFormatter.format(
            amount = amount.setScale(0, RoundingMode.DOWN),
            currencySymbol = currency.symbol,
            decimalDigits = 0,
            locale = locale,
            symbolPosition = MoneyFormatter.SymbolPosition.AFTER,
        )
    }

    private fun assertCloseTo(
        expected: Float,
        actual: Float,
        label: String,
    ) {
        assertEquals("$label expected=$expected actual=$actual", expected, actual, 1.5f)
    }

    private fun SemanticsNodeInteraction.textLayout(): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        return results.first()
    }
}
