package com.kshavrin.mymoney.feature.dashboard

import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kshavrin.mymoney.core.designsystem.chart.ChartColorRule
import com.kshavrin.mymoney.core.designsystem.chart.ChartStyle
import com.kshavrin.mymoney.core.domain.model.ChartMetric
import com.kshavrin.mymoney.core.ui.theme.MyMoneyTheme
import com.kshavrin.mymoney.feature.dashboard.components.CHART_SETTINGS_GRIDLINES_TAG
import com.kshavrin.mymoney.feature.dashboard.components.CHART_SETTINGS_LABELS_TAG
import com.kshavrin.mymoney.feature.dashboard.components.CHART_SETTINGS_POINTS_DECREASE_TAG
import com.kshavrin.mymoney.feature.dashboard.components.CHART_SETTINGS_POINTS_INCREASE_TAG
import com.kshavrin.mymoney.feature.dashboard.components.CHART_SETTINGS_POINTS_VALUE_TAG
import com.kshavrin.mymoney.feature.dashboard.components.CHART_SETTINGS_SHEET_TAG
import com.kshavrin.mymoney.feature.dashboard.components.CHART_SETTINGS_VISIBLE_TAG
import com.kshavrin.mymoney.feature.dashboard.components.ChartSettingsSheet
import com.kshavrin.mymoney.feature.dashboard.components.chartColorTag
import com.kshavrin.mymoney.feature.dashboard.components.chartMetricTag
import com.kshavrin.mymoney.feature.dashboard.components.chartPeriodTag
import com.kshavrin.mymoney.feature.dashboard.components.chartStyleThumbTag
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChartSettingsSheetUiTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private fun defaultConfig() =
        ChartConfig(
            visible = true,
            style = ChartStyle.NeonLine,
            periodType = ChartPeriodType.Follow,
            pointCount = DEFAULT_CHART_POINT_COUNT,
            metric = ChartMetric.CUMULATIVE,
            showGridlines = true,
            showLabels = true,
            colorRule = ChartColorRule.BySign,
        )

    private fun setSheet(
        config: ChartConfig = defaultConfig(),
        onEvent: (DashboardEvent) -> Unit = {},
        onDismiss: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            MyMoneyTheme {
                ChartSettingsSheet(config = config, onEvent = onEvent, onDismiss = onDismiss)
            }
        }
    }

    @Test
    fun `sheet renders with its test tag`() {
        setSheet()
        composeTestRule.onNodeWithTag(CHART_SETTINGS_SHEET_TAG).assertExists()
    }

    @Test
    fun `style thumb for each ChartStyle entry exists in the sheet`() {
        setSheet()
        ChartStyle.entries.forEach { style ->
            composeTestRule
                .onNodeWithTag(chartStyleThumbTag(style))
                .assertExists()
        }
    }

    @Test
    fun `tapping a style thumb emits ChartStyleChanged with the selected style`() {
        val captured = mutableListOf<DashboardEvent>()
        setSheet(onEvent = { captured += it })

        composeTestRule
            .onNodeWithTag(chartStyleThumbTag(ChartStyle.Bars))
            .performClick()

        composeTestRule.runOnIdle {
            assertTrue(
                "expected ChartStyleChanged(Bars); got $captured",
                captured.any { it == DashboardEvent.ChartStyleChanged(ChartStyle.Bars) },
            )
        }
    }

    @Test
    fun `period type segmented button Follow exists and is rendered`() {
        setSheet()
        composeTestRule
            .onNodeWithTag(chartPeriodTag(ChartPeriodType.Follow))
            .assertExists()
    }

    @Test
    fun `tapping period type Month emits ChartPeriodTypeChanged Month`() {
        val captured = mutableListOf<DashboardEvent>()
        setSheet(onEvent = { captured += it })

        composeTestRule
            .onNodeWithTag(chartPeriodTag(ChartPeriodType.Month))
            .performClick()

        composeTestRule.runOnIdle {
            assertTrue(
                "expected ChartPeriodTypeChanged(Month); got $captured",
                captured.any { it == DashboardEvent.ChartPeriodTypeChanged(ChartPeriodType.Month) },
            )
        }
    }

    @Test
    fun `point count stepper shows the current count value`() {
        setSheet(config = defaultConfig().copy(pointCount = 7))
        composeTestRule
            .onNodeWithTag(CHART_SETTINGS_POINTS_VALUE_TAG)
            .assertIsDisplayed()
    }

    @Test
    fun `tapping increase point count emits ChartPointCountChanged with count plus one`() {
        val captured = mutableListOf<DashboardEvent>()
        setSheet(
            config = defaultConfig().copy(pointCount = 5),
            onEvent = { captured += it },
        )

        composeTestRule
            .onNodeWithTag(CHART_SETTINGS_POINTS_INCREASE_TAG)
            .performClick()

        composeTestRule.runOnIdle {
            assertTrue(
                "expected ChartPointCountChanged(6); got $captured",
                captured.any { it == DashboardEvent.ChartPointCountChanged(6) },
            )
        }
    }

    @Test
    fun `tapping decrease point count emits ChartPointCountChanged with count minus one`() {
        val captured = mutableListOf<DashboardEvent>()
        setSheet(
            config = defaultConfig().copy(pointCount = 5),
            onEvent = { captured += it },
        )

        composeTestRule
            .onNodeWithTag(CHART_SETTINGS_POINTS_DECREASE_TAG)
            .performClick()

        composeTestRule.runOnIdle {
            assertTrue(
                "expected ChartPointCountChanged(4); got $captured",
                captured.any { it == DashboardEvent.ChartPointCountChanged(4) },
            )
        }
    }

    @Test
    fun `decrease button is disabled when point count is at minimum`() {
        setSheet(config = defaultConfig().copy(pointCount = CHART_POINT_COUNT_RANGE.first))
        composeTestRule
            .onNodeWithTag(CHART_SETTINGS_POINTS_DECREASE_TAG)
            .assertIsNotEnabled()
    }

    @Test
    fun `increase button is disabled when point count is at maximum`() {
        setSheet(config = defaultConfig().copy(pointCount = CHART_POINT_COUNT_RANGE.last))
        composeTestRule
            .onNodeWithTag(CHART_SETTINGS_POINTS_INCREASE_TAG)
            .assertIsNotEnabled()
    }

    @Test
    fun `increase and decrease buttons are both enabled for a mid-range point count`() {
        setSheet(config = defaultConfig().copy(pointCount = 6))
        composeTestRule
            .onNodeWithTag(CHART_SETTINGS_POINTS_INCREASE_TAG)
            .assertIsEnabled()
        composeTestRule
            .onNodeWithTag(CHART_SETTINGS_POINTS_DECREASE_TAG)
            .assertIsEnabled()
    }

    @Test
    fun `metric segmented buttons for all three metrics exist`() {
        setSheet()
        ChartMetric.entries.forEach { metric ->
            composeTestRule
                .onNodeWithTag(chartMetricTag(metric))
                .assertExists()
        }
    }

    @Test
    fun `tapping metric PERIOD_NET emits ChartMetricChanged PERIOD_NET`() {
        val captured = mutableListOf<DashboardEvent>()
        setSheet(onEvent = { captured += it })

        composeTestRule
            .onNodeWithTag(chartMetricTag(ChartMetric.PERIOD_NET))
            .performClick()

        composeTestRule.runOnIdle {
            assertTrue(
                "expected ChartMetricChanged(PERIOD_NET); got $captured",
                captured.any { it == DashboardEvent.ChartMetricChanged(ChartMetric.PERIOD_NET) },
            )
        }
    }

    @Test
    fun `color rule buttons for all three rules exist`() {
        setSheet()
        listOf(ChartColorRule.BySign, ChartColorRule.Income, ChartColorRule.Expense).forEach { rule ->
            composeTestRule
                .onNodeWithTag(chartColorTag(rule))
                .assertExists()
        }
    }

    @Test
    fun `tapping color rule Expense emits ChartColorRuleChanged Expense`() {
        val captured = mutableListOf<DashboardEvent>()
        setSheet(onEvent = { captured += it })

        composeTestRule
            .onNodeWithTag(chartColorTag(ChartColorRule.Expense))
            .performClick()

        composeTestRule.runOnIdle {
            assertTrue(
                "expected ChartColorRuleChanged(Expense); got $captured",
                captured.any { it == DashboardEvent.ChartColorRuleChanged(ChartColorRule.Expense) },
            )
        }
    }

    @Test
    fun `gridlines toggle is on when showGridlines is true`() {
        setSheet(config = defaultConfig().copy(showGridlines = true))
        composeTestRule
            .onNodeWithTag(CHART_SETTINGS_GRIDLINES_TAG)
            .assertIsOn()
    }

    @Test
    fun `gridlines toggle is off when showGridlines is false`() {
        setSheet(config = defaultConfig().copy(showGridlines = false))
        composeTestRule
            .onNodeWithTag(CHART_SETTINGS_GRIDLINES_TAG)
            .assertIsOff()
    }

    @Test
    fun `tapping gridlines toggle emits ChartGridlinesToggled with the new state`() {
        val captured = mutableListOf<DashboardEvent>()
        setSheet(
            config = defaultConfig().copy(showGridlines = true),
            onEvent = { captured += it },
        )

        composeTestRule
            .onNodeWithTag(CHART_SETTINGS_GRIDLINES_TAG)
            .performClick()

        composeTestRule.runOnIdle {
            assertTrue(
                "expected ChartGridlinesToggled(false); got $captured",
                captured.any { it == DashboardEvent.ChartGridlinesToggled(false) },
            )
        }
    }

    @Test
    fun `labels toggle is on when showLabels is true`() {
        setSheet(config = defaultConfig().copy(showLabels = true))
        composeTestRule
            .onNodeWithTag(CHART_SETTINGS_LABELS_TAG)
            .assertIsOn()
    }

    @Test
    fun `labels toggle is off when showLabels is false`() {
        setSheet(config = defaultConfig().copy(showLabels = false))
        composeTestRule
            .onNodeWithTag(CHART_SETTINGS_LABELS_TAG)
            .assertIsOff()
    }

    @Test
    fun `tapping labels toggle emits ChartLabelsToggled with the new state`() {
        val captured = mutableListOf<DashboardEvent>()
        setSheet(
            config = defaultConfig().copy(showLabels = false),
            onEvent = { captured += it },
        )

        composeTestRule
            .onNodeWithTag(CHART_SETTINGS_LABELS_TAG)
            .performClick()

        composeTestRule.runOnIdle {
            assertTrue(
                "expected ChartLabelsToggled(true); got $captured",
                captured.any { it == DashboardEvent.ChartLabelsToggled(true) },
            )
        }
    }

    @Test
    fun `visible toggle is on when visible is true`() {
        setSheet(config = defaultConfig().copy(visible = true))
        composeTestRule
            .onNodeWithTag(CHART_SETTINGS_VISIBLE_TAG)
            .assertIsOn()
    }

    @Test
    fun `visible toggle is off when visible is false`() {
        setSheet(config = defaultConfig().copy(visible = false))
        composeTestRule
            .onNodeWithTag(CHART_SETTINGS_VISIBLE_TAG)
            .assertIsOff()
    }

    @Test
    fun `tapping visible toggle to hide emits ChartVisibilityChanged false`() {
        val captured = mutableListOf<DashboardEvent>()
        setSheet(
            config = defaultConfig().copy(visible = true),
            onEvent = { captured += it },
        )

        composeTestRule
            .onNodeWithTag(CHART_SETTINGS_VISIBLE_TAG)
            .performClick()

        composeTestRule.runOnIdle {
            assertTrue(
                "expected ChartVisibilityChanged(false); got $captured",
                captured.any { it == DashboardEvent.ChartVisibilityChanged(false) },
            )
        }
    }

    @Test
    fun `tapping visible toggle to show emits ChartVisibilityChanged true`() {
        val captured = mutableListOf<DashboardEvent>()
        setSheet(
            config = defaultConfig().copy(visible = false),
            onEvent = { captured += it },
        )

        composeTestRule
            .onNodeWithTag(CHART_SETTINGS_VISIBLE_TAG)
            .performClick()

        composeTestRule.runOnIdle {
            assertTrue(
                "expected ChartVisibilityChanged(true); got $captured",
                captured.any { it == DashboardEvent.ChartVisibilityChanged(true) },
            )
        }
    }
}
