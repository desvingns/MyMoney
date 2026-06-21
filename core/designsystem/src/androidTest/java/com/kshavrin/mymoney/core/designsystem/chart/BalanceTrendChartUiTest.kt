package com.kshavrin.mymoney.core.designsystem.chart

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kshavrin.mymoney.core.ui.theme.MyMoneyTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BalanceTrendChartUiTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private fun setContent(
        points: List<Float>,
        showGridlines: Boolean = true,
        showLabels: Boolean = false,
        labels: List<String> = emptyList(),
        colorRule: ChartColorRule = ChartColorRule.Default,
    ) {
        composeTestRule.setContent {
            MyMoneyTheme {
                BalanceTrendChart(
                    points = points,
                    modifier = Modifier.fillMaxWidth(),
                    labels = labels,
                    showGridlines = showGridlines,
                    showLabels = showLabels,
                    colorRule = colorRule,
                )
            }
        }
    }

    // ---- chart node is always present ----

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
    fun `empty series renders a chart node without crashing`() {
        setContent(emptyList())
        composeTestRule
            .onNodeWithTag(BALANCE_TREND_CHART_TAG)
            .assertExists()
    }

    @Test
    fun `single-point series renders a chart node without crashing`() {
        setContent(listOf(42f))
        composeTestRule
            .onNodeWithTag(BALANCE_TREND_CHART_TAG)
            .assertExists()
    }

    // ---- default height contract ----

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
        setContent(listOf(1f, 2f, 3f), showLabels = false)
        val heightWithout =
            composeTestRule
                .onNodeWithTag(BALANCE_TREND_CHART_TAG)
                .fetchSemanticsNode()
                .boundsInRoot.height

        setContent(
            listOf(1f, 2f, 3f),
            showLabels = true,
            labels = listOf("Jan", "Feb", "Mar"),
        )
        val heightWith =
            composeTestRule
                .onNodeWithTag(BALANCE_TREND_CHART_TAG)
                .fetchSemanticsNode()
                .boundsInRoot.height

        assertTrue(
            "chart with labels (height=$heightWith) must be taller than without ($heightWithout)",
            heightWith > heightWithout,
        )
    }

    // ---- by_sign color rule renders without crash ----

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

    // ---- gridlines toggle ----

    @Test
    fun `showGridlines false still renders the chart node`() {
        setContent(listOf(1f, 2f, 3f), showGridlines = false)
        composeTestRule
            .onNodeWithTag(BALANCE_TREND_CHART_TAG)
            .assertExists()
    }

    // ---- all-positive series (SPEC acceptance) ----

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

    // ---- chart is at least as wide as it is tall ----

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
}
