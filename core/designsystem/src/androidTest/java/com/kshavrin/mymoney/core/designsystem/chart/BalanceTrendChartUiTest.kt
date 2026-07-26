package com.kshavrin.mymoney.core.designsystem.chart

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kshavrin.mymoney.core.designsystem.R
import com.kshavrin.mymoney.core.ui.theme.MyMoneyTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.text.NumberFormat

@RunWith(AndroidJUnit4::class)
class BalanceTrendChartUiTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private fun styleWrapperTag(style: ChartStyle) = "balance_trend_chart_style_${style.name}"

    private fun chartWrapperTag(name: String) = "balance_trend_chart_wrapper_$name"

    private fun setContent(
        points: List<Float>,
        showGridlines: Boolean = true,
        showLabels: Boolean = false,
        labels: List<String> = emptyList(),
        metricLabel: String? = null,
        colorRule: ChartColorRule = ChartColorRule.Default,
        style: ChartStyle = ChartStyle.Default,
    ) {
        composeTestRule.setContent {
            MyMoneyTheme {
                BalanceTrendChart(
                    points = points,
                    modifier = Modifier.fillMaxWidth(),
                    labels = labels,
                    metricLabel = metricLabel,
                    showGridlines = showGridlines,
                    showLabels = showLabels,
                    colorRule = colorRule,
                    style = style,
                )
            }
        }
    }

    private fun assertAllStylesRender(points: List<Float>) {
        composeTestRule.setContent {
            MyMoneyTheme {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                ) {
                    ChartStyle.entries.forEach { style ->
                        Box(modifier = Modifier.testTag(styleWrapperTag(style))) {
                            BalanceTrendChart(
                                points = points,
                                modifier = Modifier.fillMaxWidth(),
                                style = style,
                            )
                        }
                    }
                }
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule
            .onAllNodesWithTag(BALANCE_TREND_CHART_TAG)
            .assertCountEquals(ChartStyle.entries.size)
        ChartStyle.entries.forEach { style ->
            composeTestRule
                .onNodeWithTag(styleWrapperTag(style))
                .assertExists()
        }
    }

    @Test
    fun `five-point series renders a chart node with the expected testTag`() {
        setContent(listOf(10f, 6f, 12f, 12f, 15f))
        composeTestRule
            .onNodeWithTag(BALANCE_TREND_CHART_TAG)
            .assertExists()
    }

    @Test
    fun `crossing series renders a chart node`() {
        setContent(listOf(4f, 3f, 1f, -2f, -3f))
        composeTestRule
            .onNodeWithTag(BALANCE_TREND_CHART_TAG)
            .assertExists()
    }

    @Test
    fun `every chart style renders without crashing for a five-point series`() {
        assertAllStylesRender(listOf(10f, 6f, 12f, 12f, 15f))
    }

    @Test
    fun `every chart style renders without crashing for a single-point series`() {
        assertAllStylesRender(listOf(42f))
    }

    @Test
    fun `every chart style renders without crashing for an all-zero series`() {
        assertAllStylesRender(listOf(0f, 0f, 0f, 0f, 0f))
    }

    @Test
    fun `every chart style renders without crashing for a negative-only series`() {
        assertAllStylesRender(listOf(-8f, -3f, -11f, -5f, -2f))
    }

    @Test
    fun `every chart style renders without crashing for a zero-crossing series`() {
        assertAllStylesRender(listOf(4f, 3f, 1f, -2f, -3f))
    }

    @Test
    fun `empty series renders a chart node without crashing`() {
        setContent(emptyList())
        composeTestRule
            .onNodeWithTag(BALANCE_TREND_CHART_TAG)
            .assertExists()
    }

    @Test
    fun summaryUsesSuppliedMetricIntradayPeriodLocalizedValuesAndIncreasingDirection() {
        val labels = listOf("0", "2", "4")
        val metricLabel = "Income + expense"
        val points = listOf(1234.5f, 1800f, 2500.75f)
        setContent(
            points = points,
            labels = labels,
            metricLabel = metricLabel,
            showLabels = true,
        )

        val expectedPeriod = targetString(R.string.balance_trend_chart_period_range, labels.first(), labels.last())
        val expectedDescription =
            targetString(
                R.string.balance_trend_chart_cd,
                metricLabel,
                expectedPeriod,
                formatChartValue(points.first()),
                formatChartValue(points.last()),
                targetString(R.string.balance_trend_chart_direction_increasing),
            )

        composeTestRule.onNodeWithContentDescription(expectedDescription).assertExists()
    }

    @Test
    fun summaryReportsDecreasingDirection() {
        val points = listOf(9f, 4f)
        setContent(points = points, labels = listOf("09:00", "11:00"))

        val expectedPeriod = targetString(R.string.balance_trend_chart_period_range, "09:00", "11:00")
        val expectedDescription =
            targetString(
                R.string.balance_trend_chart_cd,
                targetString(R.string.balance_trend_chart_metric),
                expectedPeriod,
                formatChartValue(points.first()),
                formatChartValue(points.last()),
                targetString(R.string.balance_trend_chart_direction_decreasing),
            )

        composeTestRule.onNodeWithContentDescription(expectedDescription).assertExists()
    }

    @Test
    fun emptySummaryReportsSelectedPeriodNoDataAndUnchangedDirection() {
        setContent(emptyList())

        val expectedDescription =
            targetString(
                R.string.balance_trend_chart_cd,
                targetString(R.string.balance_trend_chart_metric),
                targetString(R.string.balance_trend_chart_period_selected),
                targetString(R.string.balance_trend_chart_no_data),
                targetString(R.string.balance_trend_chart_no_data),
                targetString(R.string.balance_trend_chart_direction_unchanged),
            )

        composeTestRule.onNodeWithContentDescription(expectedDescription).assertExists()
    }

    @Test
    fun `single-point series renders a chart node without crashing`() {
        setContent(listOf(42f))
        composeTestRule
            .onNodeWithTag(BALANCE_TREND_CHART_TAG)
            .assertExists()
    }

    @Test
    fun `chart without labels has height matching trendChartDefaultHeight`() {
        setContent(listOf(1f, 2f, 3f), showLabels = false)
        val bounds =
            composeTestRule
                .onNodeWithTag(BALANCE_TREND_CHART_TAG)
                .assertExists()
                .fetchSemanticsNode()
                .boundsInRoot
        val expectedHeightPx = with(composeTestRule.density) { 96.dp.toPx() }
        assertEquals(expectedHeightPx, bounds.height, 2f)
    }

    @Test
    fun `chart with labels is taller than chart without labels`() {
        composeTestRule.setContent {
            MyMoneyTheme {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Box(modifier = Modifier.testTag(chartWrapperTag("without_labels"))) {
                        BalanceTrendChart(
                            points = listOf(1f, 2f, 3f),
                            modifier = Modifier.fillMaxWidth(),
                            showLabels = false,
                        )
                    }
                    Box(modifier = Modifier.testTag(chartWrapperTag("with_labels"))) {
                        BalanceTrendChart(
                            points = listOf(1f, 2f, 3f),
                            modifier = Modifier.fillMaxWidth(),
                            showLabels = true,
                            labels = listOf("Jan", "Feb", "Mar"),
                        )
                    }
                }
            }
        }
        val heightWithout =
            composeTestRule
                .onNodeWithTag(chartWrapperTag("without_labels"))
                .fetchSemanticsNode()
                .boundsInRoot.height

        val heightWith =
            composeTestRule
                .onNodeWithTag(chartWrapperTag("with_labels"))
                .fetchSemanticsNode()
                .boundsInRoot.height

        assertTrue(
            "chart with labels (height=$heightWith) must be taller than without ($heightWithout)",
            heightWith > heightWithout,
        )
    }

    @Test
    fun `BySign rule on positive-last series renders the chart node`() {
        setContent(listOf(10f, 6f, 12f, 12f, 15f), colorRule = ChartColorRule.BySign)
        composeTestRule
            .onNodeWithTag(BALANCE_TREND_CHART_TAG)
            .assertExists()
    }

    @Test
    fun `BySign rule on negative-last series renders the chart node`() {
        setContent(listOf(4f, 3f, 1f, -2f, -3f), colorRule = ChartColorRule.BySign)
        composeTestRule
            .onNodeWithTag(BALANCE_TREND_CHART_TAG)
            .assertExists()
    }

    @Test
    fun `Income rule renders the chart node`() {
        setContent(listOf(1f, 2f, 3f), colorRule = ChartColorRule.Income)
        composeTestRule
            .onNodeWithTag(BALANCE_TREND_CHART_TAG)
            .assertExists()
    }

    @Test
    fun `Expense rule renders the chart node`() {
        setContent(listOf(1f, 2f, 3f), colorRule = ChartColorRule.Expense)
        composeTestRule
            .onNodeWithTag(BALANCE_TREND_CHART_TAG)
            .assertExists()
    }

    @Test
    fun `showGridlines false still renders the chart node`() {
        setContent(listOf(1f, 2f, 3f), showGridlines = false)
        composeTestRule
            .onNodeWithTag(BALANCE_TREND_CHART_TAG)
            .assertExists()
    }

    @Test
    fun `all-positive series 10 6 12 12 15 renders chart with correct default height`() {
        setContent(listOf(10f, 6f, 12f, 12f, 15f))
        val bounds =
            composeTestRule
                .onNodeWithTag(BALANCE_TREND_CHART_TAG)
                .assertExists()
                .fetchSemanticsNode()
                .boundsInRoot
        val expectedHeightPx = with(composeTestRule.density) { 96.dp.toPx() }
        assertEquals(expectedHeightPx, bounds.height, 2f)
    }

    @Test
    fun `chart fills the available width so it is wider than its default height`() {
        setContent(listOf(1f, 5f, 3f, 7f, 2f))
        val bounds =
            composeTestRule
                .onNodeWithTag(BALANCE_TREND_CHART_TAG)
                .assertExists()
                .fetchSemanticsNode()
                .boundsInRoot
        assertTrue(
            "chart width (${bounds.width}) must exceed chart height (${bounds.height})",
            bounds.width > bounds.height,
        )
    }

    // ---- SmoothArea / wave edge-fade (wave blend SPEC) ----

    @Test
    fun `SmoothArea style with five-point positive series renders chart node without crash`() {
        setContent(listOf(10f, 6f, 12f, 12f, 15f), style = ChartStyle.SmoothArea)
        composeTestRule
            .onNodeWithTag(BALANCE_TREND_CHART_TAG)
            .assertExists()
    }

    @Test
    fun `SmoothArea style with zero-crossing series renders chart node without crash`() {
        setContent(listOf(4f, 3f, 1f, -2f, -3f), style = ChartStyle.SmoothArea)
        composeTestRule
            .onNodeWithTag(BALANCE_TREND_CHART_TAG)
            .assertExists()
    }

    @Test
    fun `SmoothArea style with all-zero series renders chart node without crash`() {
        setContent(listOf(0f, 0f, 0f, 0f, 0f), style = ChartStyle.SmoothArea)
        composeTestRule
            .onNodeWithTag(BALANCE_TREND_CHART_TAG)
            .assertExists()
    }

    @Test
    fun `SmoothArea style with single-point series renders chart node without crash`() {
        setContent(listOf(42f), style = ChartStyle.SmoothArea)
        composeTestRule
            .onNodeWithTag(BALANCE_TREND_CHART_TAG)
            .assertExists()
    }

    @Test
    fun `SmoothArea style with empty series renders chart node without crash`() {
        setContent(emptyList(), style = ChartStyle.SmoothArea)
        composeTestRule
            .onNodeWithTag(BALANCE_TREND_CHART_TAG)
            .assertExists()
    }

    @Test
    fun `SmoothArea style with BySign color rule renders chart node without crash`() {
        setContent(listOf(10f, 6f, 12f, 12f, 15f), colorRule = ChartColorRule.BySign, style = ChartStyle.SmoothArea)
        composeTestRule
            .onNodeWithTag(BALANCE_TREND_CHART_TAG)
            .assertExists()
    }

    @Test
    fun `SmoothArea style with BySign negative series renders chart node without crash`() {
        setContent(listOf(-5f, -3f, -8f, -1f), colorRule = ChartColorRule.BySign, style = ChartStyle.SmoothArea)
        composeTestRule
            .onNodeWithTag(BALANCE_TREND_CHART_TAG)
            .assertExists()
    }

    @Test
    fun `SmoothArea style height is unchanged and matches trendChartDefaultHeight`() {
        setContent(listOf(1f, 2f, 3f, 4f, 5f), style = ChartStyle.SmoothArea, showLabels = false)
        val bounds =
            composeTestRule
                .onNodeWithTag(BALANCE_TREND_CHART_TAG)
                .assertExists()
                .fetchSemanticsNode()
                .boundsInRoot
        val expectedHeightPx = with(composeTestRule.density) { 96.dp.toPx() }
        assertEquals(expectedHeightPx, bounds.height, 2f)
    }

    private fun targetString(
        resourceId: Int,
        vararg formatArgs: Any,
    ): String =
        InstrumentationRegistry
            .getInstrumentation()
            .targetContext
            .getString(resourceId, *formatArgs)

    private fun formatChartValue(value: Float): String {
        val locale =
            InstrumentationRegistry
                .getInstrumentation()
                .targetContext.resources.configuration.locales[0]
        return NumberFormat
            .getNumberInstance(locale)
            .apply {
                minimumFractionDigits = 0
                maximumFractionDigits = 6
            }.format(value.toDouble())
    }
}
