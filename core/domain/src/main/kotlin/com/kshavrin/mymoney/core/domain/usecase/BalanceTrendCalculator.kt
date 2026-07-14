package com.kshavrin.mymoney.core.domain.usecase

import com.kshavrin.mymoney.core.domain.model.BalanceSnapshot
import com.kshavrin.mymoney.core.domain.model.ChartMetric
import com.kshavrin.mymoney.core.domain.model.Money
import com.kshavrin.mymoney.core.domain.model.Period
import com.kshavrin.mymoney.core.domain.model.TrendPoint
import com.kshavrin.mymoney.core.domain.model.toMoneyScale
import com.kshavrin.mymoney.core.domain.time.PeriodArithmetic
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject

const val DEFAULT_TREND_POINTS = 5

class BalanceTrendCalculator
    @Inject
    constructor() {
        fun buildWindow(
            anchor: Period,
            count: Int = DEFAULT_TREND_POINTS,
            zone: ZoneId = ZoneId.systemDefault(),
        ): List<Period> {
            require(count > 0) { "count must be positive" }
            return when (anchor) {
                is Period.All, is Period.Interval, is Period.CustomRange -> splitRange(anchor, count, zone)
                else ->
                    generateSequence(anchor) { it.previous() }
                        .take(count)
                        .toList()
                        .reversed()
            }
        }

        fun buildAutoWindow(
            anchor: Period,
            earliestDate: LocalDate? = null,
            today: LocalDate,
            zone: ZoneId = ZoneId.systemDefault(),
        ): List<Period> =
            when (anchor) {
                is Period.Day -> throw IllegalArgumentException("Period.Day uses the intra-day trend path")
                is Period.Week ->
                    (0L..6L).map { offset ->
                        Period.Day(anchor.weekStart.plusDays(offset))
                    }
                is Period.Month ->
                    (1..anchor.yearMonth.lengthOfMonth()).map { day ->
                        Period.Day(anchor.yearMonth.atDay(day))
                    }
                is Period.Year ->
                    (1..12).map { month ->
                        Period.Month(YearMonth.of(anchor.year, month))
                    }
                is Period.All ->
                    earliestDate?.let { start ->
                        splitRange(Period.CustomRange(start, today), count = 30, zone = zone)
                    } ?: emptyList()
                is Period.Interval ->
                    buildAutoRangeWindow(anchor.start, anchor.end, anchor, zone)
                is Period.CustomRange ->
                    buildAutoRangeWindow(anchor.start, anchor.end, anchor, zone)
            }

        suspend fun buildAutoSeries(
            window: List<Period>,
            metric: ChartMetric,
            snapshotProvider: suspend (Period) -> BalanceSnapshot,
        ): List<TrendPoint> {
            val snapshots = window.map { period -> snapshotProvider(period) }
            val lastActiveIndex =
                snapshots.indexOfLast { snapshot ->
                    snapshot.income.amount.signum() > 0 || snapshot.expense.amount.signum() > 0
                }
            if (lastActiveIndex < 0) return emptyList()

            var cumulative: BigDecimal? = null
            return window.take(lastActiveIndex + 1).mapIndexed { index, period ->
                val snapshot = snapshots[index]
                val currency = snapshot.net.currency
                val net = snapshot.net.amount
                when (metric) {
                    ChartMetric.CUMULATIVE -> {
                        val running = (cumulative ?: BigDecimal.ZERO).add(net).toMoneyScale(currency)
                        cumulative = running
                        TrendPoint(
                            index = index,
                            period = period,
                            value = Money(running, currency),
                        )
                    }
                    ChartMetric.PERIOD_NET ->
                        TrendPoint(
                            index = index,
                            period = period,
                            value = Money(net.toMoneyScale(currency), currency),
                        )
                    ChartMetric.INCOME_EXPENSE ->
                        TrendPoint(
                            index = index,
                            period = period,
                            value = Money(net.toMoneyScale(currency), currency),
                            income = snapshot.income,
                            expense = snapshot.expense,
                        )
                }
            }
        }

        suspend operator fun invoke(
            window: List<Period>,
            metric: ChartMetric,
            snapshotProvider: suspend (Period) -> BalanceSnapshot,
        ): List<TrendPoint> {
            var cumulative: BigDecimal? = null
            return window.mapIndexed { index, period ->
                val snapshot = snapshotProvider(period)
                val currency = snapshot.net.currency
                val net = snapshot.net.amount
                when (metric) {
                    ChartMetric.CUMULATIVE -> {
                        val running = (cumulative ?: BigDecimal.ZERO).add(net).toMoneyScale(currency)
                        cumulative = running
                        TrendPoint(
                            index = index,
                            period = period,
                            value = Money(running, currency),
                        )
                    }
                    ChartMetric.PERIOD_NET ->
                        TrendPoint(
                            index = index,
                            period = period,
                            value = Money(net.toMoneyScale(currency), currency),
                        )
                    ChartMetric.INCOME_EXPENSE ->
                        TrendPoint(
                            index = index,
                            period = period,
                            value = Money(net.toMoneyScale(currency), currency),
                            income = snapshot.income,
                            expense = snapshot.expense,
                        )
                }
            }
        }

        private fun splitRange(
            anchor: Period,
            count: Int,
            zone: ZoneId,
        ): List<Period> {
            val range = PeriodArithmetic.toEpochMillisRange(anchor, zone)
            val endMillis = if (anchor is Period.All) Instant.now().toEpochMilli() else range.last
            val safeStart = minOf(range.first, endMillis)
            val startDate = Instant.ofEpochMilli(safeStart).atZone(zone).toLocalDate()
            val endDate = Instant.ofEpochMilli(endMillis).atZone(zone).toLocalDate()
            val totalDays = ChronoUnit.DAYS.between(startDate, endDate) + 1
            return (0 until count).map { i ->
                val subStartOffset = totalDays * i / count
                val subEndOffset =
                    if (i == count - 1) totalDays - 1 else (totalDays * (i + 1) / count) - 1
                val subStart = startDate.plusDays(subStartOffset)
                val subEnd =
                    if (i == count - 1) endDate else startDate.plusDays(subEndOffset)
                when (anchor) {
                    is Period.Interval -> Period.Interval(subStart, subEnd)
                    else -> Period.CustomRange(subStart, subEnd)
                }
            }
        }

        private fun buildAutoRangeWindow(
            start: LocalDate,
            end: LocalDate,
            anchor: Period,
            zone: ZoneId,
        ): List<Period> {
            val days = ChronoUnit.DAYS.between(start, end) + 1
            return if (days <= 30) {
                (0L until days).map { offset -> Period.Day(start.plusDays(offset)) }
            } else {
                splitRange(anchor, count = 30, zone = zone)
            }
        }
    }
