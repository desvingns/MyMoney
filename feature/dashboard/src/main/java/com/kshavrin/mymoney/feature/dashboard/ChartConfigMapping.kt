package com.kshavrin.mymoney.feature.dashboard

import com.kshavrin.mymoney.core.datastore.model.AppSettings
import com.kshavrin.mymoney.core.designsystem.chart.ChartColorRule
import com.kshavrin.mymoney.core.designsystem.chart.ChartStyle
import com.kshavrin.mymoney.core.domain.model.ChartMetric

// Stable snake_case ids for each persisted chart enum. These are the contract with AppSettings
// (SPEC 02) — the persisted default is "neon_line"/"follow"/"cumulative"/"by_sign". Unknown ids
// fall back to the enum's default so a future/older config never crashes the dashboard.

private val chartStyleIds: Map<String, ChartStyle> =
    mapOf(
        "neon_line" to ChartStyle.NeonLine,
        "neon_area" to ChartStyle.NeonArea,
        "smooth_line" to ChartStyle.SmoothLine,
        "smooth_area" to ChartStyle.SmoothArea,
        "stepped_line" to ChartStyle.SteppedLine,
        "stepped_area" to ChartStyle.SteppedArea,
        "bars" to ChartStyle.Bars,
        "rounded_bars" to ChartStyle.RoundedBars,
        "dots_line" to ChartStyle.DotsLine,
        "dots_only" to ChartStyle.DotsOnly,
        "gradient_stroke" to ChartStyle.GradientStroke,
        "dual_glow" to ChartStyle.DualGlow,
        "dashed_line" to ChartStyle.DashedLine,
        "thin_minimal" to ChartStyle.ThinMinimal,
        "thick_bold" to ChartStyle.ThickBold,
        "baseline_fill" to ChartStyle.BaselineFill,
        "vertical_gradient_area" to ChartStyle.VerticalGradientArea,
        "candy_segments" to ChartStyle.CandySegments,
        "mountain" to ChartStyle.Mountain,
        "ribbon" to ChartStyle.Ribbon,
    )

private val chartStyleToId: Map<ChartStyle, String> = chartStyleIds.entries.associate { it.value to it.key }

fun chartStyleFromId(id: String): ChartStyle = chartStyleIds[id] ?: ChartStyle.Default

fun ChartStyle.toId(): String = chartStyleToId[this] ?: chartStyleToId.getValue(ChartStyle.Default)

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
