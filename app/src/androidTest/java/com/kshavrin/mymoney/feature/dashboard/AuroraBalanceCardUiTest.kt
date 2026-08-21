package com.kshavrin.mymoney.feature.dashboard

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kshavrin.mymoney.core.designsystem.chart.BALANCE_TREND_CHART_TAG
import com.kshavrin.mymoney.core.designsystem.chart.ChartStyle
import com.kshavrin.mymoney.core.ui.theme.MyMoneyTheme
import com.kshavrin.mymoney.core.ui.theme.Spacing
import com.kshavrin.mymoney.feature.dashboard.components.AuroraBalanceCard
import com.kshavrin.mymoney.feature.dashboard.components.DASHBOARD_AURORA_BALANCE_TAG
import com.kshavrin.mymoney.feature.dashboard.components.DASHBOARD_AURORA_CARD_TAG
import com.kshavrin.mymoney.feature.dashboard.components.DASHBOARD_AURORA_EXPENSE_PILL_TAG
import com.kshavrin.mymoney.feature.dashboard.components.DASHBOARD_AURORA_INCOME_PILL_TAG
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AuroraBalanceCardUiTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val defaultPoints = listOf(100f, 200f, 150f, 300f, 250f)

    private fun defaultConfig(visible: Boolean = true) = ChartConfig(visible = visible)

    private fun setCard(
        balance: String = "12 345 $",
        income: String = "20 000 $",
        expense: String = "7 654 $",
        points: List<Float> = defaultPoints,
        chartConfig: ChartConfig = defaultConfig(),
        onChartClick: () -> Unit = {},
        netPositive: Boolean = true,
    ) {
        composeTestRule.setContent {
            MyMoneyTheme {
                AuroraBalanceCard(
                    balance = balance,
                    income = income,
                    expense = expense,
                    points = points,
                    chartConfig = chartConfig,
                    onChartClick = onChartClick,
                    netPositive = netPositive,
                )
            }
        }
    }

    @Test
    fun `aurora card renders without crash when net is positive`() {
        setCard(balance = "12 345 $", netPositive = true)
        composeTestRule
            .onNodeWithTag(DASHBOARD_AURORA_CARD_TAG)
            .assertExists()
    }

    @Test
    fun `aurora card renders without crash when net is negative`() {
        setCard(balance = "-3 210 $", netPositive = false)
        composeTestRule
            .onNodeWithTag(DASHBOARD_AURORA_CARD_TAG)
            .assertExists()
    }

    @Test
    fun `aurora card renders without crash when net is zero treated as positive`() {
        setCard(balance = "0 $", netPositive = true)
        composeTestRule
            .onNodeWithTag(DASHBOARD_AURORA_CARD_TAG)
            .assertExists()
        composeTestRule
            .onNodeWithTag(DASHBOARD_AURORA_BALANCE_TAG)
            .assertIsDisplayed()
    }

    @Test
    fun `aurora card displays balance text when net is positive`() {
        setCard(balance = "5 000 $", netPositive = true)
        composeTestRule
            .onNodeWithTag(DASHBOARD_AURORA_BALANCE_TAG)
            .assertIsDisplayed()
    }

    @Test
    fun `aurora card displays balance text when net is negative`() {
        setCard(balance = "-5 000 $", netPositive = false)
        composeTestRule
            .onNodeWithTag(DASHBOARD_AURORA_BALANCE_TAG)
            .assertIsDisplayed()
    }

    @Test
    fun `aurora card root tag exists`() {
        setCard()
        composeTestRule
            .onNodeWithTag(DASHBOARD_AURORA_CARD_TAG)
            .assertExists()
    }

    @Test
    fun `aurora card omits the legacy balance for period label`() {
        setCard()
        composeTestRule
            .onNodeWithText("BALANCE FOR JUNE")
            .assertDoesNotExist()
    }

    @Test
    fun `aurora card spans the host width with equal side insets`() {
        setCard()

        val rootBounds = composeTestRule.onRoot().fetchSemanticsNode().boundsInRoot
        val cardBounds = composeTestRule.onNodeWithTag(DASHBOARD_AURORA_CARD_TAG).fetchSemanticsNode().boundsInRoot
        val expectedInsetPx = with(composeTestRule.density) { Spacing.dashboardAuroraHostHorizontalPaddingWide.toPx() }

        assertCloseTo(expectedInsetPx, cardBounds.left, "left inset")
        assertCloseTo(rootBounds.right - expectedInsetPx, cardBounds.right, "right inset")
    }

    @Test
    fun `aurora balance value uses the compact 26sp typography token`() {
        setCard(balance = "98 765 $")

        val fontSize =
            composeTestRule
                .onNodeWithTag(DASHBOARD_AURORA_BALANCE_TAG)
                .textLayout()
                .layoutInput.style.fontSize
        assertEquals(26.sp, fontSize)
    }

    @Test
    fun `aurora balance value displays the provided integer amount with currency after`() {
        val balance = "12 345 $"
        setCard(balance = balance)

        composeTestRule
            .onNodeWithText(balance)
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithTag(DASHBOARD_AURORA_BALANCE_TAG)
            .assertIsDisplayed()
    }

    @Test
    fun `free balance label is displayed above the balance value`() {
        setCard(balance = "12 345 $")
        // The label is rendered uppercase by the composable; getString returns the raw resource.
        val rawLabel = targetString(R.string.dashboard_aurora_free_balance_label)
        composeTestRule
            .onNodeWithText(rawLabel.uppercase())
            .assertIsDisplayed()
    }

    @Test
    fun `income pill tag is displayed when chart config is visible`() {
        setCard(income = "20 000", chartConfig = defaultConfig(visible = true))
        composeTestRule
            .onNodeWithTag(DASHBOARD_AURORA_INCOME_PILL_TAG)
            .assertIsDisplayed()
        // Pill now reads "\u2191 <Income word> <plain amount>" \u2014 no currency symbol.
        val incomeLabel = targetString(R.string.dashboard_aurora_income_label)
        composeTestRule
            .onNodeWithText("\u2191 $incomeLabel 20 000")
            .assertIsDisplayed()
    }

    @Test
    fun `expense pill tag is displayed when chart config is visible`() {
        setCard(expense = "7 654", chartConfig = defaultConfig(visible = true))
        composeTestRule
            .onNodeWithTag(DASHBOARD_AURORA_EXPENSE_PILL_TAG)
            .assertIsDisplayed()
        // Pill now reads "\u2193 <Expenses word> <plain amount>" \u2014 no currency symbol.
        val expenseLabel = targetString(R.string.dashboard_aurora_expense_label)
        composeTestRule
            .onNodeWithText("\u2193 $expenseLabel 7 654")
            .assertIsDisplayed()
    }

    @Test
    fun `income pill does not show old symbol-only format without word label`() {
        // The previous contract was "\u2191 20 000 $"; now it must NOT match that pattern.
        setCard(income = "20 000", chartConfig = defaultConfig(visible = true))
        composeTestRule
            .onNodeWithText("\u2191 20 000 $")
            .assertDoesNotExist()
    }

    @Test
    fun `expense pill does not show old symbol-only format without word label`() {
        // The previous contract was "\u2193 7 654 $"; now it must NOT match that pattern.
        setCard(expense = "7 654", chartConfig = defaultConfig(visible = true))
        composeTestRule
            .onNodeWithText("\u2193 7 654 $")
            .assertDoesNotExist()
    }

    @Test
    fun `income pill tag is displayed when chart config is hidden`() {
        setCard(chartConfig = defaultConfig(visible = false))
        composeTestRule
            .onNodeWithTag(DASHBOARD_AURORA_INCOME_PILL_TAG)
            .assertIsDisplayed()
    }

    @Test
    fun `expense pill tag is displayed when chart config is hidden`() {
        setCard(chartConfig = defaultConfig(visible = false))
        composeTestRule
            .onNodeWithTag(DASHBOARD_AURORA_EXPENSE_PILL_TAG)
            .assertIsDisplayed()
    }

    @Test
    fun `embedded chart tag exists when chartConfig visible is true`() {
        setCard(chartConfig = defaultConfig(visible = true))
        composeTestRule
            .onNodeWithTag(DASHBOARD_TREND_CHART_TAG)
            .assertExists()
            .assertHeightIsEqualTo(Spacing.dashboardAuroraChartHeightCompact + Spacing.trendChartLabelHeight)
    }

    @Test
    fun `chart tag is absent and hidden hint tag is shown when chartConfig visible is false`() {
        setCard(chartConfig = defaultConfig(visible = false))
        composeTestRule
            .onNodeWithTag(DASHBOARD_CHART_HIDDEN_HINT_TAG)
            .assertExists()
            .assertContentDescriptionEquals(targetString(R.string.chart_hidden_hint))
        composeTestRule
            .onNodeWithTag(DASHBOARD_TREND_CHART_TAG)
            .assertDoesNotExist()
    }

    @Test
    fun `tapping chart area fires the chart click callback`() {
        var clicked = false
        setCard(
            chartConfig = defaultConfig(visible = true),
            onChartClick = { clicked = true },
        )

        composeTestRule
            .onNodeWithTag(DASHBOARD_TREND_CHART_TAG)
            .assertExists()
            .performClick()

        composeTestRule.runOnIdle {
            assertTrue("expected onChartClick to be invoked when chart area is tapped", clicked)
        }
    }

    @Test
    fun `tapping hidden hint strip fires the chart click callback`() {
        var clicked = false
        setCard(
            chartConfig = defaultConfig(visible = false),
            onChartClick = { clicked = true },
        )

        composeTestRule
            .onNodeWithTag(DASHBOARD_CHART_HIDDEN_HINT_TAG)
            .assertExists()
            .performClick()

        composeTestRule.runOnIdle {
            assertTrue("expected onChartClick to be invoked when hidden hint is tapped", clicked)
        }
    }

    // ---- inset layout tests (feathered-backdrop SPEC — full-bleed reverted) ----

    @Test
    fun `chart box is inset within card bounds after full-bleed revert`() {
        setCard(chartConfig = defaultConfig(visible = true))

        val cardBounds =
            composeTestRule
                .onNodeWithTag(DASHBOARD_AURORA_CARD_TAG)
                .fetchSemanticsNode()
                .boundsInRoot
        val chartBounds =
            composeTestRule
                .onNodeWithTag(DASHBOARD_TREND_CHART_TAG)
                .fetchSemanticsNode()
                .boundsInRoot

        assertTrue(
            "chart left (${chartBounds.left}) must be >= card left (${cardBounds.left}) — chart is inset, not full-bleed",
            chartBounds.left >= cardBounds.left - 1.5f,
        )
        assertTrue(
            "chart right (${chartBounds.right}) must be <= card right (${cardBounds.right}) — chart is inset, not full-bleed",
            chartBounds.right <= cardBounds.right + 1.5f,
        )
    }

    @Test
    fun `aurora card uses the borderless plain horizontal padding after substrate removal`() {
        // "Без подложки" (reference isV1): the framed substrate is gone, so the content is inset by
        // the tighter plain padding, not the old bordered 18dp. Locks the substrate stays removed.
        setCard(chartConfig = defaultConfig(visible = true))

        val cardBounds =
            composeTestRule
                .onNodeWithTag(DASHBOARD_AURORA_CARD_TAG)
                .fetchSemanticsNode()
                .boundsInRoot
        val chartBounds =
            composeTestRule
                .onNodeWithTag(DASHBOARD_TREND_CHART_TAG)
                .fetchSemanticsNode()
                .boundsInRoot
        val expectedInsetPx =
            with(composeTestRule.density) { Spacing.dashboardAuroraPlainPaddingHorizontal.toPx() }

        assertCloseTo(expectedInsetPx, chartBounds.left - cardBounds.left, "chart left plain inset")
        assertCloseTo(expectedInsetPx, cardBounds.right - chartBounds.right, "chart right plain inset")
    }

    @Test
    fun `embedded BalanceTrendChart node exists inside aurora card when chart is visible`() {
        setCard(chartConfig = defaultConfig(visible = true))
        composeTestRule
            .onNodeWithTag(BALANCE_TREND_CHART_TAG, useUnmergedTree = true)
            .assertExists()
    }

    @Test
    fun `Smooth style renders without crash inside aurora card`() {
        setCard(
            chartConfig = defaultConfig(visible = true).copy(style = ChartStyle.Smooth),
            points = listOf(100f, 200f, 150f, 300f, 250f),
        )
        composeTestRule
            .onNodeWithTag(DASHBOARD_TREND_CHART_TAG)
            .assertExists()
        composeTestRule
            .onNodeWithTag(BALANCE_TREND_CHART_TAG, useUnmergedTree = true)
            .assertExists()
    }

    @Test
    fun `Smooth style with empty points renders without crash inside aurora card`() {
        setCard(
            chartConfig = defaultConfig(visible = true).copy(style = ChartStyle.Smooth),
            points = emptyList(),
        )
        composeTestRule
            .onNodeWithTag(DASHBOARD_TREND_CHART_TAG)
            .assertExists()
    }

    @Test
    fun `Smooth style with single point renders without crash inside aurora card`() {
        setCard(
            chartConfig = defaultConfig(visible = true).copy(style = ChartStyle.Smooth),
            points = listOf(42f),
        )
        composeTestRule
            .onNodeWithTag(DASHBOARD_TREND_CHART_TAG)
            .assertExists()
    }

    @Test
    fun `chart height is unchanged and matches dashboardAuroraChartHeightCompact`() {
        setCard(chartConfig = defaultConfig(visible = true))
        composeTestRule
            .onNodeWithTag(DASHBOARD_TREND_CHART_TAG)
            .assertExists()
            .assertHeightIsEqualTo(Spacing.dashboardAuroraChartHeightCompact + Spacing.trendChartLabelHeight)
    }

    @Test
    fun `balance and pills are unaffected by inset chart when chart is visible`() {
        setCard(
            balance = "9 999 $",
            income = "15 000 $",
            expense = "5 001 $",
            chartConfig = defaultConfig(visible = true),
        )
        composeTestRule.onNodeWithTag(DASHBOARD_AURORA_BALANCE_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(DASHBOARD_AURORA_INCOME_PILL_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(DASHBOARD_AURORA_EXPENSE_PILL_TAG).assertIsDisplayed()
    }

    @Test
    fun `balance and pills are unaffected by hidden-hint path when chart is hidden`() {
        setCard(
            balance = "9 999 $",
            income = "15 000 $",
            expense = "5 001 $",
            chartConfig = defaultConfig(visible = false),
        )
        composeTestRule.onNodeWithTag(DASHBOARD_AURORA_BALANCE_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(DASHBOARD_AURORA_INCOME_PILL_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(DASHBOARD_AURORA_EXPENSE_PILL_TAG).assertIsDisplayed()
    }

    private fun assertCloseTo(
        expected: Float,
        actual: Float,
        label: String,
    ) {
        assertTrue("$label expected=$expected actual=$actual", kotlin.math.abs(expected - actual) <= 1.5f)
    }

    private fun SemanticsNodeInteraction.textLayout(): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        return results.first()
    }

    private fun targetString(resourceId: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(resourceId)
}
