package com.kshavrin.mymoney.feature.dashboard

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kshavrin.mymoney.core.ui.theme.MyMoneyTheme
import com.kshavrin.mymoney.feature.dashboard.components.AuroraBalanceCard
import com.kshavrin.mymoney.feature.dashboard.components.DASHBOARD_AURORA_BALANCE_TAG
import com.kshavrin.mymoney.feature.dashboard.components.DASHBOARD_AURORA_CARD_TAG
import com.kshavrin.mymoney.feature.dashboard.components.DASHBOARD_AURORA_EXPENSE_PILL_TAG
import com.kshavrin.mymoney.feature.dashboard.components.DASHBOARD_AURORA_INCOME_PILL_TAG
import com.kshavrin.mymoney.feature.dashboard.components.DASHBOARD_AURORA_LABEL_TAG
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
        label: String = "BALANCE FOR JUNE",
        balance: String = "12 345.67",
        income: String = "20 000.00",
        expense: String = "7 654.33",
        points: List<Float> = defaultPoints,
        chartConfig: ChartConfig = defaultConfig(),
        onChartClick: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            MyMoneyTheme {
                AuroraBalanceCard(
                    label = label,
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
    fun `aurora label tag is displayed`() {
        setCard(label = "BALANCE FOR JUNE")
        composeTestRule
            .onNodeWithTag(DASHBOARD_AURORA_LABEL_TAG)
            .assertIsDisplayed()
    }

    @Test
    fun `aurora balance value tag is displayed`() {
        setCard(balance = "12 345.67")
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
    }

    @Test
    fun `expense pill tag is displayed when chart config is visible`() {
        setCard(chartConfig = defaultConfig(visible = true))
        composeTestRule
            .onNodeWithTag(DASHBOARD_AURORA_EXPENSE_PILL_TAG)
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
}
