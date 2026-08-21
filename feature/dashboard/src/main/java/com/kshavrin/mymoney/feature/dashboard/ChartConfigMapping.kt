package com.kshavrin.mymoney.feature.dashboard

import com.kshavrin.mymoney.core.datastore.model.AppSettings
import com.kshavrin.mymoney.core.designsystem.chart.ChartColorRule
import com.kshavrin.mymoney.core.designsystem.chart.ChartStyle
import com.kshavrin.mymoney.core.domain.model.ChartMetric

fun chartStyleFromId(id: String): ChartStyle =
    when (id) {
        "bars", "rounded_bars" -> ChartStyle.Bars
        "smooth",
        "smooth_line",
        "smooth_area",
        "stepped_area",
        "neon_area",
        "vertical_gradient_area",
        "mountain",
        "baseline_fill",
        -> ChartStyle.Smooth

        "line",
        "neon_line",
        "stepped_line",
        "dots_line",
        "dots_only",
        "gradient_stroke",
        "dual_glow",
        "dashed_line",
        "thin_minimal",
        "thick_bold",
        "candy_segments",
        "ribbon",
        -> ChartStyle.Line

        else -> ChartStyle.Default
    }

fun ChartStyle.toId(): String =
    when (this) {
        ChartStyle.Bars -> "bars"
        ChartStyle.Line -> "line"
        ChartStyle.Smooth -> "smooth"
    }

internal fun chartStyleLabelRes(style: ChartStyle): Int =
    when (style) {
        ChartStyle.Bars -> R.string.chart_settings_style_bars
        ChartStyle.Line -> R.string.chart_settings_style_line
        ChartStyle.Smooth -> R.string.chart_settings_style_smooth
    }

private val periodTypeIds: Map<String, ChartPeriodType> =
    mapOf(
        "follow" to ChartPeriodType.Follow,
        "day" to ChartPeriodType.Day,
        "week" to ChartPeriodType.Week,
        "month" to ChartPeriodType.Month,
        "year" to ChartPeriodType.Year,
    )

private val periodTypeToId: Map<ChartPeriodType, String> = periodTypeIds.entries.associate { it.value to it.key }

fun chartPeriodTypeFromId(id: String): ChartPeriodType = periodTypeIds[id] ?: ChartPeriodType.Follow

fun ChartPeriodType.toId(): String = periodTypeToId.getValue(this)

private val metricIds: Map<String, ChartMetric> =
    mapOf(
        "cumulative" to ChartMetric.CUMULATIVE,
        "period_net" to ChartMetric.PERIOD_NET,
        "income_expense" to ChartMetric.INCOME_EXPENSE,
    )

private val metricToId: Map<ChartMetric, String> = metricIds.entries.associate { it.value to it.key }

fun chartMetricFromId(id: String): ChartMetric = metricIds[id] ?: ChartMetric.CUMULATIVE

fun ChartMetric.toId(): String = metricToId.getValue(this)

internal fun chartMetricLabelRes(metric: ChartMetric): Int =
    when (metric) {
        ChartMetric.CUMULATIVE -> R.string.chart_settings_metric_cumulative
        ChartMetric.PERIOD_NET -> R.string.chart_settings_metric_period_net
        ChartMetric.INCOME_EXPENSE -> R.string.chart_settings_metric_income_expense
    }

private val colorRuleIds: Map<String, ChartColorRule> =
    mapOf(
        "by_sign" to ChartColorRule.BySign,
        "income" to ChartColorRule.Income,
        "expense" to ChartColorRule.Expense,
    )

private val colorRuleToId: Map<ChartColorRule, String> = colorRuleIds.entries.associate { it.value to it.key }

fun chartColorRuleFromId(id: String): ChartColorRule = colorRuleIds[id] ?: ChartColorRule.Default

fun ChartColorRule.toId(): String = colorRuleToId.getValue(this)

fun AppSettings.toChartConfig(): ChartConfig =
    ChartConfig(
        visible = chartVisible,
        style = chartStyleFromId(chartStyle),
        periodType = chartPeriodTypeFromId(chartPeriodType),
        pointCount = chartPointCount.coerceIn(CHART_POINT_COUNT_RANGE),
        metric = chartMetricFromId(chartMetric),
        showGridlines = chartShowGridlines,
        showLabels = chartShowLabels,
        colorRule = chartColorRuleFromId(chartColorRule),
        autoMode = chartAutoMode,
    )
