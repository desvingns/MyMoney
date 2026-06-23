package com.kshavrin.mymoney.feature.dashboard

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
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
                )
            }
        }
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
    fun `aurora balance value uses the compact 34sp typography token`() {
        setCard(balance = "98 765 $")

        val fontSize =
            composeTestRule
                .onNodeWithTag(DASHBOARD_AURORA_BALANCE_TAG)
                .textLayout()
                .layoutInput.style.fontSize
        assertEquals(34.sp, fontSize)
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
    fun `income pill tag is displayed when chart config is visible`() {
        setCard(chartConfig = defaultConfig(visible = true))
        composeTestRule
            .onNodeWithTag(DASHBOARD_AURORA_INCOME_PILL_TAG)
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText("\u2191 20 000 $")
            .assertIsDisplayed()
    }

    @Test
    fun `expense pill tag is displayed when chart config is visible`() {
        setCard(chartConfig = defaultConfig(visible = true))
        composeTestRule
            .onNodeWithTag(DASHBOARD_AURORA_EXPENSE_PILL_TAG)
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText("\u2193 7 654 $")
            .assertIsDisplayed()
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
}
