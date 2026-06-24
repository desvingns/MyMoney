package com.kshavrin.mymoney.feature.dashboard

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kshavrin.mymoney.core.designsystem.chart.ChartColorRule
import com.kshavrin.mymoney.core.designsystem.chart.ChartStyle
import com.kshavrin.mymoney.core.domain.model.ChartMetric
import com.kshavrin.mymoney.core.domain.model.Currency
import com.kshavrin.mymoney.core.domain.model.Money
import com.kshavrin.mymoney.core.domain.model.Period
import com.kshavrin.mymoney.core.domain.model.TrendPoint
import com.kshavrin.mymoney.core.ui.theme.MyMoneyTheme
import com.kshavrin.mymoney.feature.dashboard.components.AuroraBalanceCard
import com.kshavrin.mymoney.feature.dashboard.components.DASHBOARD_AURORA_CARD_TAG
import com.kshavrin.mymoney.feature.dashboard.components.trendPointLabels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.YearMonth
import java.util.Locale

// Instrumented Compose-UI test: verifies that the Aurora card renders a BalanceTrendChart
// with the label row present when an auto-mode series (Day labels) is supplied. One focused
// test per the "instrumented" constraint — do not batch unrelated screens here.
@RunWith(AndroidJUnit4::class)
class AuroraAutoModeTrendLabelUiTest {
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

    // Build a realistic auto-mode Month series: 5 Day-period points for days 1..5 of June 2026.
    // The label list is what the ViewModel passes after calling trendPointLabels — day-of-month
    // numbers ("1", "2", "3", "4", "5").
    private val june2026 = YearMonth.of(2026, 6)

    private fun dayPoint(day: Int): TrendPoint =
        TrendPoint(
            index = day - 1,
            period = Period.Day(june2026.atDay(day)),
            value = Money(BigDecimal("${day * 100}.00"), usd),
        )

    private val autoModePoints = (1..5).map { dayPoint(it) }

    // Reproduce the production path: ViewModel calls trendPointLabels with axis=Days.
    private val autoModeLabels: List<String> =
        trendPointLabels(autoModePoints, ChartTrendAxis.Days, Locale.getDefault())

    private val chartConfig =
        ChartConfig(
            visible = true,
            style = ChartStyle.Default,
            periodType = ChartPeriodType.Follow,
            pointCount = 5,
            metric = ChartMetric.CUMULATIVE,
            showGridlines = true,
            showLabels = true,
            colorRule = ChartColorRule.Default,
            autoMode = true,
        )

    // Float points (y-values) extracted from TrendPoint.value.amount, matching the production
    // DashboardScreen projection.
    private val floatPoints = autoModePoints.map { it.value.amount.toFloat() }

    @Test
    fun `aurora card renders with auto-mode day labels — chart tag exists and labels are day numbers`() {
        composeTestRule.setContent {
            MyMoneyTheme {
                AuroraBalanceCard(
                    balance = "500 $",
                    income = "1 500 $",
                    expense = "1 000 $",
                    points = floatPoints,
                    labels = autoModeLabels,
                    chartConfig = chartConfig,
                    onChartClick = {},
                    netPositive = true,
                )
            }
        }

        composeTestRule.runOnIdle {
            // Labels must be day-of-month numbers ("1".."5") for a Day-axis auto-mode series.
            assertEquals(5, autoModeLabels.size)
            assertEquals("1", autoModeLabels[0])
            assertEquals("5", autoModeLabels[4])
            autoModeLabels.forEach { label ->
                assertTrue("label must be a non-empty day number; got '$label'", label.isNotEmpty())
            }
        }

        // The chart container node must exist — confirms the composable rendered without crashing
        // and that chartConfig.visible=true caused the chart branch to execute.
        composeTestRule
            .onNodeWithTag(DASHBOARD_TREND_CHART_TAG)
            .assertExists()

        // The Aurora card root tag must exist — confirms the outer card composable rendered.
        composeTestRule
            .onNodeWithTag(DASHBOARD_AURORA_CARD_TAG)
            .assertExists()
    }
}
