package com.kshavrin.mymoney.feature.dashboard.components

import com.kshavrin.mymoney.core.domain.model.Period
import com.kshavrin.mymoney.core.domain.model.TrendPoint
import com.kshavrin.mymoney.feature.dashboard.ChartTrendAxis
import java.time.format.TextStyle
import java.util.Locale

private const val INTRADAY_SLOT_HOURS = 2

// Build the trend chart's x-axis point labels for the auto-mode window (G13). Day windows show the
// day-of-month number, year windows show the locale's short month name, intra-day shows the slot's
// start hour, and split ranges show the bucket-start day. All text is derived from java.time with
// the given locale — no hardcoded month strings (project rule). Manual mode (axis == None) yields
// no labels so the legacy chart keeps its own behaviour.
fun trendPointLabels(
    points: List<TrendPoint>,
    axis: ChartTrendAxis,
    locale: Locale,
): List<String> =
    when (axis) {
        ChartTrendAxis.None -> emptyList()
        ChartTrendAxis.Hours -> points.map { point -> (point.index * INTRADAY_SLOT_HOURS).toString() }
        ChartTrendAxis.Days ->
            points.map { point ->
                when (val period = point.period) {
                    is Period.Day -> period.date.dayOfMonth.toString()
                    else -> ""
                }
            }
        ChartTrendAxis.Months ->
            points.map { point ->
                when (val period = point.period) {
                    is Period.Month -> period.yearMonth.month.getDisplayName(TextStyle.SHORT, locale)
                    else -> ""
                }
            }
        ChartTrendAxis.RangeBuckets ->
            points.map { point ->
                when (val period = point.period) {
                    is Period.CustomRange -> period.start.dayOfMonth.toString()
                    is Period.Day -> period.date.dayOfMonth.toString()
                    else -> ""
                }
            }
    }
