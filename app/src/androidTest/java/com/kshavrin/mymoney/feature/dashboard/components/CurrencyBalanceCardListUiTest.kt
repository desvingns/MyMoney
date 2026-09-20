package com.kshavrin.mymoney.feature.dashboard.components

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
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
import com.kshavrin.mymoney.core.designsystem.chart.ChartColorRule
import com.kshavrin.mymoney.core.designsystem.chart.ChartStyle
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    private val matrixPoints = listOf(10, 20, 5)

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

    private data class CapturedChart(
        val pixels: IntArray,
        val width: Int,
        val height: Int,
    )

    // -----------------------------------------------------------------------
    // Empty list
    // -----------------------------------------------------------------------

    @Test
    fun emptyCardListRendersTheCurrencyCardsContainerTagWithoutAnyChildren() {
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
    fun singleCurrencyCardShowsTheCurrencyCode() {
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
    fun singleCurrencyCardHasAPerCardContainerTagDerivedFromTheCurrencyCode() {
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
    fun singleCurrencyCardKeepsTheWideCenteredAuroraContainer() {
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
    fun singleCurrencyCardShowsTheFormattedNetBalanceAmount() {
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
    fun singleCurrencyCardBalanceUsesTheCompact26spTypographyToken() {
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
    fun singleCurrencyCardIncomePillContainsTheFormattedIncomeAmount() {
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
    fun singleCurrencyCardExpensePillContainsTheFormattedExpenseAmount() {
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
    fun singleCurrencyCardIncomePillTextStartsWithTheUpArrowPrefix() {
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
    fun singleCurrencyCardExpensePillTextStartsWithTheDownArrowPrefix() {
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
    fun currencyCardFiguresAreInTheCardCurrencyWithNoConversion() {
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
    fun negativeNetBalanceIsTruncatedTowardZeroInsteadOfRoundedAway() {
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
    fun twoCurrencyCardsRenderBothCurrencyCodes() {
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
    fun twoCurrencyCardsEachHaveTheirOwnPerCardContainerTag() {
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
    fun twoCurrencyCardsRenderTwoIncomePillsAndTwoExpensePills() {
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
    fun miniTrendChartIsShownWhenChartIsVisibleAndTheCardHasTrendPoints() {
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
    fun miniTrendChartIsHiddenWhenTheChartIsDisabledInSettings() {
        composeTestRule.setContent {
            MyMoneyTheme {
                CurrencyBalanceCardList(
                    cards = listOf(usdCard(income = "100.99", expense = "30.49", withTrend = true)),
                    chartConfig = ChartConfig(visible = false, showProjection = true),
                )
            }
        }

        composeTestRule
            .onNodeWithTag(DASHBOARD_CURRENCY_CARD_MINI_CHART_TAG)
            .assertDoesNotExist()
    }

    @Test
    fun miniTrendChartIsAbsentWhenTheCardCarriesNoTrendPoints() {
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
    fun miniTrendChartAppearsOnlyForTheCardThatHasTrendPointsWhenTwoCardsArePresent() {
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
    fun twoCardsWithTrendPointsBothRenderAMiniChart() {
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

    @Test
    fun currencyBalanceCardListRendersTheFullMiniChartMatrix() {
        val capture = startMiniChartMatrixCapture()

        ChartStyle.entries.forEach { style ->
            val withoutProjection =
                ChartColorRule.entries.associateWith { colorRule ->
                    capture(style, colorRule, false)
                }
            assertDistinctColorRules(withoutProjection, style)

            ChartColorRule.entries.forEach { colorRule ->
                val withProjection = capture(style, colorRule, true)
                val without = withoutProjection.getValue(colorRule)

                assertEquals(without.width, withProjection.width)
                assertEquals(without.height, withProjection.height)
                if (style == ChartStyle.Bars) {
                    assertTrue(
                        "Bars must ignore projection for ${colorRule.id} in currency mini-chart",
                        without.pixels.contentEquals(withProjection.pixels),
                    )
                } else {
                    assertFalse(
                        "${style.name} must receive showProjection for ${colorRule.id} in currency mini-chart",
                        without.pixels.contentEquals(withProjection.pixels),
                    )
                }
            }
        }
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

    private fun startMiniChartMatrixCapture(): (ChartStyle, ChartColorRule, Boolean) -> CapturedChart {
        var chartConfig by mutableStateOf(ChartConfig(visible = true))
        composeTestRule.setContent {
            MyMoneyTheme {
                CurrencyBalanceCardList(
                    cards = listOf(usdCardWithMatrixTrend()),
                    chartConfig = chartConfig,
                )
            }
        }

        return { style, colorRule, showProjection ->
            composeTestRule.runOnIdle {
                chartConfig =
                    chartConfig.copy(
                        style = style,
                        colorRule = colorRule,
                        showProjection = showProjection,
                    )
            }
            composeTestRule.waitForIdle()
            val image =
                composeTestRule
                    .onNodeWithTag(DASHBOARD_CURRENCY_CARD_MINI_CHART_TAG)
                    .assertIsDisplayed()
                    .captureToImage()
            val pixels = IntArray(image.width * image.height)
            image.readPixels(pixels)
            CapturedChart(pixels = pixels, width = image.width, height = image.height)
        }
    }

    private fun usdCardWithMatrixTrend(): CurrencyBalanceCard =
        usdCard(income = "100.99", expense = "30.49").copy(
            trendPoints =
                matrixPoints.mapIndexed { index, value ->
                    TrendPoint(
                        index = index,
                        period = Period.Month(YearMonth.of(2026, index + 1)),
                        value = Money(BigDecimal(value), usd),
                    )
                },
        )

    private fun assertDistinctColorRules(
        charts: Map<ChartColorRule, CapturedChart>,
        style: ChartStyle,
    ) {
        ChartColorRule.entries.forEachIndexed { firstIndex, firstRule ->
            ChartColorRule.entries.drop(firstIndex + 1).forEach { secondRule ->
                assertFalse(
                    "Currency mini-chart ${style.name} must render ${firstRule.id} differently from ${secondRule.id}",
                    charts.getValue(firstRule).pixels.contentEquals(charts.getValue(secondRule).pixels),
                )
            }
        }
        assertFalse(
            "Currency mini-chart ${style.name} ByDirection must not collapse to AlwaysGreen when the final point is below the first",
            charts
                .getValue(ChartColorRule.ByDirection)
                .pixels
                .contentEquals(charts.getValue(ChartColorRule.AlwaysGreen).pixels),
        )
        assertFalse(
            "Currency mini-chart ${style.name} ByDirection must not collapse to AlwaysRed when the second point is above the first",
            charts
                .getValue(ChartColorRule.ByDirection)
                .pixels
                .contentEquals(charts.getValue(ChartColorRule.AlwaysRed).pixels),
        )
    }
}
