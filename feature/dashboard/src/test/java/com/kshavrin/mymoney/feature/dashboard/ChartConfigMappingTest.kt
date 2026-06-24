package com.kshavrin.mymoney.feature.dashboard

import com.kshavrin.mymoney.core.datastore.model.AppSettings
import com.kshavrin.mymoney.core.designsystem.chart.ChartColorRule
import com.kshavrin.mymoney.core.designsystem.chart.ChartStyle
import com.kshavrin.mymoney.core.domain.model.ChartMetric
import org.junit.Assert.assertEquals
import org.junit.Test

class ChartConfigMappingTest {
    // -------------------------------------------------------------------------
    // ChartStyle round-trip
    // -------------------------------------------------------------------------

    @Test
    fun `every ChartStyle survives a round-trip through its snake_case id`() {
        ChartStyle.entries.forEach { style ->
            val id = style.toId()
            val recovered = chartStyleFromId(id)
            assertEquals("round-trip failed for $style", style, recovered)
        }
    }

    @Test
    fun `unknown chart style id falls back to Default`() {
        assertEquals(ChartStyle.Default, chartStyleFromId("__not_a_real_id__"))
    }

    @Test
    fun `neon_line maps to ChartStyle NeonLine`() {
        assertEquals(ChartStyle.NeonLine, chartStyleFromId("neon_line"))
    }

    @Test
    fun `ribbon maps to ChartStyle Ribbon`() {
        assertEquals(ChartStyle.Ribbon, chartStyleFromId("ribbon"))
    }

    @Test
    fun `ChartStyle NeonLine toId returns neon_line`() {
        assertEquals("neon_line", ChartStyle.NeonLine.toId())
    }

    @Test
    fun `ChartStyle Ribbon toId returns ribbon`() {
        assertEquals("ribbon", ChartStyle.Ribbon.toId())
    }

    // -------------------------------------------------------------------------
    // ChartPeriodType round-trip
    // -------------------------------------------------------------------------

    @Test
    fun `every ChartPeriodType survives a round-trip through its snake_case id`() {
        ChartPeriodType.entries.forEach { type ->
            val id = type.toId()
            val recovered = chartPeriodTypeFromId(id)
            assertEquals("round-trip failed for $type", type, recovered)
        }
    }

    @Test
    fun `unknown period type id falls back to Follow`() {
        assertEquals(ChartPeriodType.Follow, chartPeriodTypeFromId("__bad__"))
    }

    @Test
    fun `follow id maps to Follow`() {
        assertEquals(ChartPeriodType.Follow, chartPeriodTypeFromId("follow"))
    }

    @Test
    fun `year id maps to Year`() {
        assertEquals(ChartPeriodType.Year, chartPeriodTypeFromId("year"))
    }

    @Test
    fun `ChartPeriodType Follow toId returns follow`() {
        assertEquals("follow", ChartPeriodType.Follow.toId())
    }

    @Test
    fun `ChartPeriodType Day toId returns day`() {
        assertEquals("day", ChartPeriodType.Day.toId())
    }

    // -------------------------------------------------------------------------
    // ChartMetric round-trip
    // -------------------------------------------------------------------------

    @Test
    fun `every ChartMetric survives a round-trip through its snake_case id`() {
        ChartMetric.entries.forEach { metric ->
            val id = metric.toId()
            val recovered = chartMetricFromId(id)
            assertEquals("round-trip failed for $metric", metric, recovered)
        }
    }

    @Test
    fun `unknown metric id falls back to CUMULATIVE`() {
        assertEquals(ChartMetric.CUMULATIVE, chartMetricFromId("__bad__"))
    }

    @Test
    fun `cumulative id maps to CUMULATIVE`() {
        assertEquals(ChartMetric.CUMULATIVE, chartMetricFromId("cumulative"))
    }

    @Test
    fun `income_expense id maps to INCOME_EXPENSE`() {
        assertEquals(ChartMetric.INCOME_EXPENSE, chartMetricFromId("income_expense"))
    }

    @Test
    fun `ChartMetric PERIOD_NET toId returns period_net`() {
        assertEquals("period_net", ChartMetric.PERIOD_NET.toId())
    }

    // -------------------------------------------------------------------------
    // ChartColorRule round-trip
    // -------------------------------------------------------------------------

    @Test
    fun `every ChartColorRule survives a round-trip through its snake_case id`() {
        listOf(ChartColorRule.BySign, ChartColorRule.Income, ChartColorRule.Expense).forEach { rule ->
            val id = rule.toId()
            val recovered = chartColorRuleFromId(id)
            assertEquals("round-trip failed for $rule", rule, recovered)
        }
    }

    @Test
    fun `unknown color rule id falls back to Default`() {
        assertEquals(ChartColorRule.Default, chartColorRuleFromId("__bad__"))
    }

    @Test
    fun `by_sign id maps to BySign`() {
        assertEquals(ChartColorRule.BySign, chartColorRuleFromId("by_sign"))
    }

    @Test
    fun `income id maps to Income`() {
        assertEquals(ChartColorRule.Income, chartColorRuleFromId("income"))
    }

    @Test
    fun `ChartColorRule Expense toId returns expense`() {
        assertEquals("expense", ChartColorRule.Expense.toId())
    }

    // -------------------------------------------------------------------------
    // AppSettings.toChartConfig() projection
    // -------------------------------------------------------------------------

    @Test
    fun `toChartConfig projects all fields from AppSettings into ChartConfig`() {
        val settings =
            AppSettings(
                chartVisible = false,
                chartStyle = "bars",
                chartPeriodType = "week",
                chartPointCount = 8,
                chartMetric = "period_net",
                chartShowGridlines = false,
                chartShowLabels = false,
                chartColorRule = "expense",
            )

        val config = settings.toChartConfig()

        assertEquals(false, config.visible)
        assertEquals(ChartStyle.Bars, config.style)
        assertEquals(ChartPeriodType.Week, config.periodType)
        assertEquals(8, config.pointCount)
        assertEquals(ChartMetric.PERIOD_NET, config.metric)
        assertEquals(false, config.showGridlines)
        assertEquals(false, config.showLabels)
        assertEquals(ChartColorRule.Expense, config.colorRule)
    }

    @Test
    fun `toChartConfig uses defaults when AppSettings has default values`() {
        val config = AppSettings().toChartConfig()

        assertEquals(true, config.visible)
        assertEquals(ChartStyle.SmoothArea, config.style)
        assertEquals(ChartPeriodType.Follow, config.periodType)
        assertEquals(DEFAULT_CHART_POINT_COUNT, config.pointCount)
        assertEquals(ChartMetric.CUMULATIVE, config.metric)
        assertEquals(true, config.showGridlines)
        assertEquals(true, config.showLabels)
        assertEquals(ChartColorRule.BySign, config.colorRule)
    }

    @Test
    fun `ChartStyle Default is SmoothArea`() {
        assertEquals(ChartStyle.SmoothArea, ChartStyle.Default)
    }

    @Test
    fun `toChartConfig clamps point count below minimum to minimum`() {
        val settings = AppSettings(chartPointCount = 1)
        val config = settings.toChartConfig()
        assertEquals(CHART_POINT_COUNT_RANGE.first, config.pointCount)
    }

    @Test
    fun `toChartConfig clamps point count above maximum to maximum`() {
        val settings = AppSettings(chartPointCount = 99)
        val config = settings.toChartConfig()
        assertEquals(CHART_POINT_COUNT_RANGE.last, config.pointCount)
    }

    @Test
    fun `toChartConfig unknown style id falls back to Default which is SmoothArea`() {
        val settings = AppSettings(chartStyle = "does_not_exist")
        val config = settings.toChartConfig()
        assertEquals(ChartStyle.Default, config.style)
        assertEquals(ChartStyle.SmoothArea, config.style)
    }

    @Test
    fun `toChartConfig unknown metric id falls back to CUMULATIVE`() {
        val settings = AppSettings(chartMetric = "bad_value")
        val config = settings.toChartConfig()
        assertEquals(ChartMetric.CUMULATIVE, config.metric)
    }

    @Test
    fun `toChartConfig unknown color rule id falls back to Default`() {
        val settings = AppSettings(chartColorRule = "not_real")
        val config = settings.toChartConfig()
        assertEquals(ChartColorRule.Default, config.colorRule)
    }

    @Test
    fun `toChartConfig unknown period type id falls back to Follow`() {
        val settings = AppSettings(chartPeriodType = "garbage")
        val config = settings.toChartConfig()
        assertEquals(ChartPeriodType.Follow, config.periodType)
    }

    // -------------------------------------------------------------------------
    // chartAutoMode mapping (SPEC dashboard-trend-selected-period)
    // -------------------------------------------------------------------------

    @Test
    fun `toChartConfig maps chartAutoMode true to autoMode true`() {
        val settings = AppSettings(chartAutoMode = true)
        val config = settings.toChartConfig()
        assertEquals(true, config.autoMode)
    }

    @Test
    fun `toChartConfig maps chartAutoMode false to autoMode false`() {
        val settings = AppSettings(chartAutoMode = false)
        val config = settings.toChartConfig()
        assertEquals(false, config.autoMode)
    }

    @Test
    fun `AppSettings default chartAutoMode is true so default ChartConfig autoMode is true`() {
        val config = AppSettings().toChartConfig()
        assertEquals(true, config.autoMode)
    }

    @Test
    fun `ChartConfig default autoMode is true`() {
        assertEquals(true, ChartConfig().autoMode)
    }
}
